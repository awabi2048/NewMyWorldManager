package me.awabi2048.myworldmanager.listener

import io.papermc.paper.event.player.AsyncChatEvent
import me.awabi2048.myworldmanager.MyWorldManager
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

class TemplateWizardChatListener(private val plugin: MyWorldManager) : Listener {

    @EventHandler(priority = EventPriority.LOW)
    fun onChat(event: AsyncChatEvent) {
        val player = event.player
        val session = plugin.templateWizardGui.getSession(player.uniqueId) ?: return

        if (session.inputState == me.awabi2048.myworldmanager.gui.TemplateWizardGui.InputState.NONE) return

        event.isCancelled = true
        event.viewers().clear()
        
        val message = PlainTextComponentSerializer.plainText().serialize(event.originalMessage())

        val cancelWord = plugin.config.getString("creation.cancel_word", "__cancel__") ?: "__cancel__"
        if (message.equals(cancelWord, ignoreCase = true)) {
            session.inputState = me.awabi2048.myworldmanager.gui.TemplateWizardGui.InputState.NONE
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.wizard_input_mode_ended"))
            Bukkit.getScheduler().runTask(plugin, Runnable {
                plugin.templateWizardGui.open(player)
            })
            return
        }

        when (session.inputState) {
            me.awabi2048.myworldmanager.gui.TemplateWizardGui.InputState.NAME -> {
                session.name = message
                session.id = message.replace(Regex("[^a-zA-Z0-9_-]"), "").lowercase()
                if (session.id.isEmpty()) {
                    session.id = "tpl_" + java.util.UUID.randomUUID().toString().substring(0, 8)
                }
                
                player.sendMessage(plugin.languageManager.getMessage(player, "messages.wizard_name_set", mapOf("name" to session.name!!, "id" to session.id!!)))
                session.inputState = me.awabi2048.myworldmanager.gui.TemplateWizardGui.InputState.NONE
                
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    plugin.templateWizardGui.open(player)
                })
            }
            me.awabi2048.myworldmanager.gui.TemplateWizardGui.InputState.DESCRIPTION -> {
                session.description = listOf(message)
                player.sendMessage(plugin.languageManager.getMessage(player, "messages.wizard_desc_set"))
                session.inputState = me.awabi2048.myworldmanager.gui.TemplateWizardGui.InputState.NONE
                
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    plugin.templateWizardGui.open(player)
                })
            }
            else -> {}
        }
    }
}
