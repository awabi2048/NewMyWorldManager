package me.awabi2048.myworldmanager.ui.bedrock

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.ui.PlayerPlatformResolver
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BedrockUiRoutingService(
    private val plugin: MyWorldManager,
    private val platformResolver: PlayerPlatformResolver,
    private val formBridge: FloodgateFormBridge
) {

    private val formFallbackUntil = ConcurrentHashMap<UUID, Long>()

    fun shouldUseForm(player: Player): Boolean {
        if (!platformResolver.isBedrock(player)) {
            return false
        }

        val mode = BedrockUiMode.fromRaw(plugin.config.getString("bedrock.ui.mode", "auto"))
        return when (mode) {
            BedrockUiMode.INVENTORY -> false
            BedrockUiMode.FORM -> formBridge.isAvailable(player)
            BedrockUiMode.AUTO -> {
                val now = System.currentTimeMillis()
                val fallbackDeadline = formFallbackUntil[player.uniqueId] ?: 0L
                if (fallbackDeadline > now) {
                    false
                } else {
                    formBridge.isAvailable(player)
                }
            }
        }
    }

    fun markFormFailure(player: Player, reason: String? = null) {
        val cooldownMinutes =
            plugin.config.getLong("bedrock.ui.form_failure_cooldown_minutes", 10L)
                .coerceAtLeast(1L)
        val until = System.currentTimeMillis() + cooldownMinutes * 60_000L
        formFallbackUntil[player.uniqueId] = until

        if (!reason.isNullOrBlank()) {
            plugin.logger.warning(
                "[BedrockUI] Form disabled for ${player.name} for ${cooldownMinutes}m. reason=$reason"
            )
        }
    }

    fun clearFormFailure(player: Player) {
        formFallbackUntil.remove(player.uniqueId)
    }
}
