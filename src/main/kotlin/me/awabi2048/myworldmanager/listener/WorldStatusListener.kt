package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.service.UnloadedWorldRegistry
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.world.WorldLoadEvent
import org.bukkit.event.world.WorldUnloadEvent

class WorldStatusListener(private val plugin: MyWorldManager) : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onWorldUnload(event: WorldUnloadEvent) {
        UnloadedWorldRegistry.register(event.world.name)
        plugin.portalManager.cleanupWorld(event.world.name)
    }

    @EventHandler
    fun onWorldLoad(event: WorldLoadEvent) {
        UnloadedWorldRegistry.unregister(event.world.name)
        // ロード済み復元や手動ロードでも、保存済みの環境設定を同じ経路で再適用する。
        plugin.worldEnvironmentService.applyAll(event.world)

        val worldName = event.world.name
        if (worldName.startsWith("my_world.")) {
            val worldUuid = runCatching {
                java.util.UUID.fromString(worldName.removePrefix("my_world."))
            }.getOrNull() ?: return
            plugin.worldConfigRepository.findByUuid(worldUuid)?.let { worldData ->
                plugin.likeSignManager.spawnHologramsForWorld(worldData)
            }
        }
    }
}
