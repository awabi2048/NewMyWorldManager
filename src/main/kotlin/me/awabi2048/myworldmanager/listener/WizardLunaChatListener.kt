package me.awabi2048.myworldmanager.listener

import com.github.ucchyocean.lc.event.LunaChatChannelChatEvent
import me.awabi2048.myworldmanager.MyWorldManager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin

class WizardLunaChatListener(private val plugin: MyWorldManager) : Listener {
    @EventHandler
    fun onLunaChat(event: LunaChatChannelChatEvent) {
        val player = event.player.player ?: return
        
        // Check if player is in Creation Wizard
        if (plugin.creationSessionManager.getSession(player.uniqueId) != null) {
            event.isCancelled = true
            return
        }

        // Check if player is in Settings Session
        if (plugin.settingsSessionManager.getSession(player) != null) {
            event.isCancelled = true
            return
        }

        // Check if player is in Invite Session
        if (plugin.inviteSessionManager.getSession(player.uniqueId) != null) {
            event.isCancelled = true
            return
        }

        // Check if player is in Template Wizard Session
        if (plugin.templateWizardGui.getSession(player.uniqueId) != null) {
            event.isCancelled = true
            return
        }
    }
}
