
package me.awabi2048.myworldmanager.listener

import io.papermc.paper.event.player.AsyncChatEvent
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.gui.CreationGui
import me.awabi2048.myworldmanager.session.WorldCreationPhase
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import java.util.UUID

class CreationChatListener(private val plugin: MyWorldManager) : Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    fun onChat(event: AsyncChatEvent) {
        val player = event.player
        val session = plugin.creationSessionManager.getSession(player.uniqueId) ?: return

        val message = PlainTextComponentSerializer.plainText().serialize(event.originalMessage())
        val cancelWord = plugin.config.getString("creation.cancel_word", "cancel") ?: "cancel"

        if (message.equals(cancelWord, ignoreCase = true)) {
            event.isCancelled = true
            event.viewers().clear()
            
            Bukkit.getScheduler().runTask(plugin, Runnable {
                plugin.creationSessionManager.endSession(player.uniqueId)
                player.closeInventory()
                player.sendMessage(plugin.languageManager.getMessage(player, "messages.creation_cancelled"))
            })
            return
        }

        if (session.phase == WorldCreationPhase.NAME_INPUT) {
            event.isCancelled = true
            event.viewers().clear()
            
            val error = plugin.worldValidator.validateName(message)
            if (error != null) {
                player.sendMessage("§c$error")
                return
            }

            val cleanedName = cleanWorldName(message)
            session.worldName = cleanedName
            session.phase = WorldCreationPhase.TYPE_SELECT
            
            // GUI open must be sync
            Bukkit.getScheduler().runTask(plugin, Runnable {
                plugin.creationGui.openTypeSelection(player)
            })
        } else if (session.phase == WorldCreationPhase.SEED_INPUT) {
            event.isCancelled = true
            event.viewers().clear()
            
            // 入力文字列をシード値として処理
            val seed = message.toLongOrNull() ?: message.hashCode().toLong()
            session.seed = seed
            session.inputSeedString = message
            session.phase = WorldCreationPhase.CONFIRM
            
            Bukkit.getScheduler().runTask(plugin, Runnable {
                plugin.creationGui.openConfirmation(player, session)
            })
        }
    }

    private fun cleanWorldName(name: String): String {
        // 括弧内の文字列（ローマ字変換など）を削除
        val regex = Regex("\\s?\\(.*?\\)")
        return name.replace(regex, "").trim()
    }
}
