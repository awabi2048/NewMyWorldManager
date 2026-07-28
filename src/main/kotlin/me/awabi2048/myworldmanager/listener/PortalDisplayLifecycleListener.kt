package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class PortalDisplayLifecycleListener(private val plugin: MyWorldManager) : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) {
        plugin.portalManager.refreshWorldDisplayLifecycle(event.player.world.name)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        if (MyWorldManagerApi.isLogoutRelocation(event.player)) return
        plugin.portalManager.refreshWorldDisplayLifecycle(event.from.name, event.player.world.name)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        val worldName = MyWorldManagerApi.getLogoutRelocationOrigin(event.player)
            ?: event.player.world.name
        Bukkit.getScheduler().runTask(plugin, Runnable {
            plugin.portalManager.refreshWorldDisplayLifecycle(worldName)
        })
    }
}
