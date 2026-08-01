package me.awabi2048.myworldmanager.service

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.api.extension.ReversibleWorldPublishPolicy
import me.awabi2048.myworldmanager.api.extension.WorldPublishReversibleState
import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.entity.Player
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class WorldPublishMetadataSnapshot(
    val publishLevel: PublishLevel,
    val publicAt: String?,
)

/** capture 後の実Actionが埋める、標準公開状態の一回限りのCAS計画です。 */
class StandardWorldPublishCyclePlan internal constructor(
    val playerId: UUID,
    val worldUuid: UUID,
    val before: WorldPublishMetadataSnapshot,
) {
    internal var expectedAfter: WorldPublishMetadataSnapshot? = null
        private set

    internal fun complete(after: WorldPublishMetadataSnapshot) {
        check(expectedAfter == null) { "standard publish cycle plan is already completed" }
        expectedAfter = after
    }
}

/** policy所有のcycleは、実測した操作後状態を一度だけ復元へ渡します。 */
class PolicyWorldPublishCyclePlan internal constructor(
    val playerId: UUID,
    val worldUuid: UUID,
    val policy: ReversibleWorldPublishPolicy,
    val policyId: String,
    val before: WorldPublishReversibleState,
) {
    private var actualAfter: WorldPublishReversibleState? = null
    private var discarded = false
    private var consumed = false

    @Synchronized
    internal fun complete(after: WorldPublishReversibleState) {
        check(!discarded && !consumed && actualAfter == null) { "policy publish cycle plan is not pending" }
        actualAfter = after
    }

    @Synchronized
    internal fun discard() {
        if (actualAfter == null && !consumed) discarded = true
    }

    @Synchronized
    fun consumeActualAfter(): WorldPublishReversibleState? {
        if (discarded || consumed) return null
        val after = actualAfter ?: return null
        consumed = true
        return after
    }
}

enum class WorldPublishCycleSource {
    STANDARD,
    POLICY,
}

/**
 * 公開切替の唯一の標準更新経路です。
 *
 * 可逆captureがある時だけ、同じ実更新で生じた正確な after state を plan へ記録します。
 * 通常クリックはplanを持たず、従来どおりクリック時刻で publicAt を更新します。
 */
class WorldPublishService(private val plugin: MyWorldManager) {
    private val standardPlans = ConcurrentHashMap<Pair<UUID, UUID>, StandardWorldPublishCyclePlan>()
    private val policyPlans = ConcurrentHashMap<Pair<UUID, UUID>, PolicyWorldPublishCyclePlan>()

    fun requireReversibleCycleContract(worldData: WorldData) {
        val policy = MyWorldManagerApi.getWorldPublishPolicy()
        if (policy.handlesPublishCycle(worldData)) {
            require(policy is ReversibleWorldPublishPolicy) {
                "WorldPublishPolicy '${policy.getId()}' handles publish cycles but does not implement ReversibleWorldPublishPolicy"
            }
        }
    }

    fun captureStandardCycle(player: Player, worldData: WorldData): StandardWorldPublishCyclePlan {
        requireReversibleCycleContract(worldData)
        check(!MyWorldManagerApi.getWorldPublishPolicy().handlesPublishCycle(worldData)) {
            "captureStandardCycle must not be used for a policy-owned publish state"
        }
        val plan = StandardWorldPublishCyclePlan(player.uniqueId, worldData.uuid, worldData.publishSnapshot())
        val key = player.uniqueId to worldData.uuid
        policyPlans.remove(key)?.discard()
        // captureに続くclickが拒否された場合でもRuntimeからproviderへ破棄通知は来ないため、
        // 同じ操作を再captureした時点で古い監査計画を安全に置き換えます。
        standardPlans[key] = plan
        return plan
    }

    fun capturePolicyCycle(
        player: Player,
        worldData: WorldData,
        policy: ReversibleWorldPublishPolicy,
    ): PolicyWorldPublishCyclePlan {
        require(policy.handlesPublishCycle(worldData)) {
            "capturePolicyCycle requires a policy-owned publish state"
        }
        val key = player.uniqueId to worldData.uuid
        standardPlans.remove(key)
        policyPlans.remove(key)?.discard()
        val plan = PolicyWorldPublishCyclePlan(
            player.uniqueId,
            worldData.uuid,
            policy,
            policy.getId(),
            policy.capturePublishCycleState(player, worldData),
        )
        policyPlans.put(key, plan)?.discard()
        return plan
    }

    fun cycle(player: Player, worldData: WorldData): WorldPublishCycleSource {
        val policy = MyWorldManagerApi.getWorldPublishPolicy()
        if (policy.handlesPublishCycle(worldData)) {
            require(policy is ReversibleWorldPublishPolicy) {
                "WorldPublishPolicy '${policy.getId()}' handled a publish cycle without ReversibleWorldPublishPolicy"
            }
            completePolicyCycle(player, worldData, policy)
            return WorldPublishCycleSource.POLICY
        }
        policyPlans.remove(player.uniqueId to worldData.uuid)?.discard()
        check(!policy.cyclePublishLevel(player, worldData)) {
            "WorldPublishPolicy '${policy.getId()}' handled a publish cycle without declaring ownership"
        }

        val before = worldData.publishSnapshot()
        worldData.publishLevel = nextPublishLevel(before.publishLevel)
        if (worldData.publishLevel == PublishLevel.PUBLIC) {
            worldData.publicAt = nowText()
        }
        plugin.worldConfigRepository.save(worldData)
        val after = worldData.publishSnapshot()
        standardPlans.remove(player.uniqueId to worldData.uuid)?.let { plan ->
            if (plan.before == before) {
                plan.complete(after)
            }
        }
        return WorldPublishCycleSource.STANDARD
    }

    fun restoreStandardCycle(
        player: Player,
        worldUuid: UUID,
        before: WorldPublishMetadataSnapshot,
        expectedAfter: WorldPublishMetadataSnapshot,
    ): Boolean {
        val world = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return false
        if (world.publishSnapshot() != expectedAfter) return false
        world.publishLevel = before.publishLevel
        world.publicAt = before.publicAt
        plugin.worldConfigRepository.save(world)
        return true
    }

    fun isWorldPresent(worldUuid: UUID): Boolean = plugin.worldConfigRepository.findByUuid(worldUuid) != null

    private fun completePolicyCycle(
        player: Player,
        worldData: WorldData,
        policy: ReversibleWorldPublishPolicy,
    ) {
        val capturedPlan = policyPlans.remove(player.uniqueId to worldData.uuid)
        standardPlans.remove(player.uniqueId to worldData.uuid)
        try {
            val plan = capturedPlan?.takeIf {
                it.policy === policy &&
                    it.policyId == policy.getId() &&
                    policy.capturePublishCycleState(player, worldData) == it.before
            }
            if (plan == null) capturedPlan?.discard()
            check(policy.cyclePublishLevel(player, worldData)) {
                "WorldPublishPolicy '${policy.getId()}' declared publish-cycle ownership but did not handle it"
            }
            if (plan == null) return

            val latestWorld = plugin.worldConfigRepository.findByUuid(worldData.uuid)
                ?: run {
                    plan.discard()
                    return
                }
            val latestPolicy = MyWorldManagerApi.getWorldPublishPolicy() as? ReversibleWorldPublishPolicy
            if (latestPolicy !== plan.policy || latestPolicy.getId() != plan.policyId || !latestPolicy.handlesPublishCycle(latestWorld)) {
                plan.discard()
                return
            }
            plan.complete(latestPolicy.capturePublishCycleState(player, latestWorld))
        } catch (exception: RuntimeException) {
            capturedPlan?.discard()
            throw exception
        }
    }

    private fun WorldData.publishSnapshot() = WorldPublishMetadataSnapshot(publishLevel, publicAt)

    private fun nextPublishLevel(level: PublishLevel): PublishLevel =
        PublishLevel.entries[(PublishLevel.entries.indexOf(level) + 1) % PublishLevel.entries.size]

    private fun nowText(): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
}
