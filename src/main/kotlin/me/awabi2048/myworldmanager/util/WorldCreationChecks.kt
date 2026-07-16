package me.awabi2048.myworldmanager.util

import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.api.extension.WorldCreationOperation
import me.awabi2048.myworldmanager.api.extension.WorldCreationRequest
import me.awabi2048.myworldmanager.api.extension.WorldCreationType as ApiWorldCreationType
import me.awabi2048.myworldmanager.session.WorldCreationType
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import me.awabi2048.myworldmanager.MyWorldManager
import java.util.UUID

object WorldCreationChecks {
    fun checkLimits(plugin: MyWorldManager, actor: CommandSender, targetOwner: UUID, notify: Boolean = true): Boolean {
        val stats = plugin.playerStatsRepository.findByUuid(targetOwner)
        val decision = WorldCreationLimitPolicy.evaluate(
            actor = actor,
            totalCount = plugin.worldConfigRepository.findAll().size,
            ownerCount = plugin.worldConfigRepository.findAll().count { it.owner == targetOwner },
            totalLimit = plugin.config.getInt("creation.max_total_world_count", 50),
            ownerLimit = WorldRuntimePolicies.maxCreateCountDefault(plugin.config) + stats.unlockedWorldSlot
        )
        if (decision.allowed) return true
        if (notify) {
            val key = if (decision.reason == WorldCreationLimitDecision.Reason.TOTAL) {
                "gui.creation.limit_reached_total"
            } else {
                "gui.creation.limit_reached"
            }
            val placeholders = if (decision.reason == WorldCreationLimitDecision.Reason.TOTAL) {
                mapOf("max" to plugin.config.getInt("creation.max_total_world_count", 50))
            } else {
                val max = WorldRuntimePolicies.maxCreateCountDefault(plugin.config) + stats.unlockedWorldSlot
                mapOf("current" to plugin.worldConfigRepository.findAll().count { it.owner == targetOwner }, "max" to max)
            }
            actor.sendMessage(plugin.languageManager.getMessage(actor as? Player, key, placeholders))
        }
        return false
    }

    fun check(
        actor: CommandSender,
        player: Player?,
        operation: WorldCreationOperation,
        type: WorldCreationType?,
        notify: Boolean = true
    ): Boolean {
        val decision = MyWorldManagerApi.checkWorldCreation(
            WorldCreationRequest(actor, player, operation, type?.toApiType())
        )
        if (decision.allowed) return true
        if (notify) decision.denialMessage?.let(actor::sendMessage)
        return false
    }

    fun check(
        player: Player,
        operation: WorldCreationOperation = WorldCreationOperation.NORMAL,
        type: WorldCreationType? = null,
        notify: Boolean = true
    ): Boolean = check(player, player, operation, type, notify)

    private fun WorldCreationType.toApiType(): ApiWorldCreationType = when (this) {
        WorldCreationType.TEMPLATE -> ApiWorldCreationType.TEMPLATE
        WorldCreationType.SEED -> ApiWorldCreationType.SEED
        WorldCreationType.RANDOM -> ApiWorldCreationType.RANDOM
    }
}
