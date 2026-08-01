package me.awabi2048.myworldmanager.service

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.api.extension.ReversibleWorldPublishPolicy
import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.entity.Player
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

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
    private val standardPlans = mutableMapOf<Pair<UUID, UUID>, StandardWorldPublishCyclePlan>()

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
        // captureに続くclickが拒否された場合でもRuntimeからproviderへ破棄通知は来ないため、
        // 同じ操作を再captureした時点で古い監査計画を安全に置き換えます。
        standardPlans[key] = plan
        return plan
    }

    fun cycle(player: Player, worldData: WorldData): WorldPublishCycleSource {
        val policy = MyWorldManagerApi.getWorldPublishPolicy()
        if (policy.handlesPublishCycle(worldData)) {
            require(policy is ReversibleWorldPublishPolicy) {
                "WorldPublishPolicy '${policy.getId()}' handled a publish cycle without ReversibleWorldPublishPolicy"
            }
            check(policy.cyclePublishLevel(player, worldData)) {
                "WorldPublishPolicy '${policy.getId()}' declared publish-cycle ownership but did not handle it"
            }
            return WorldPublishCycleSource.POLICY
        }
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

    private fun WorldData.publishSnapshot() = WorldPublishMetadataSnapshot(publishLevel, publicAt)

    private fun nextPublishLevel(level: PublishLevel): PublishLevel =
        PublishLevel.entries[(PublishLevel.entries.indexOf(level) + 1) % PublishLevel.entries.size]

    private fun nowText(): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
}
