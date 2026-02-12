package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.service.UnloadedWorldRegistry
import org.bukkit.Difficulty
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.WorldLoadEvent
import org.bukkit.event.world.WorldUnloadEvent

class WorldStatusListener(private val plugin: MyWorldManager) : Listener {

    @EventHandler
    fun onWorldUnload(event: WorldUnloadEvent) {
        // アンロードされたワールドを登録
        // キャンセルされる可能性もあるので、MONITOR優先度が望ましいが、通常のEventHandlerで処理
        // WorldServiceでの手動アンロードと重複する可能性があるが、Setなので問題ない
        UnloadedWorldRegistry.register(event.world.name)
        
        // そのワールドのTextDisplayをメモリマップからクリア
        plugin.portalManager.cleanupWorld(event.world.name)
    }

    @EventHandler
    fun onWorldLoad(event: WorldLoadEvent) {
        // ロードされたワールドは登録解除
        UnloadedWorldRegistry.unregister(event.world.name)

        // マイワールドがロードされたときは難易度をピースフルに統一
        if (plugin.worldConfigRepository.findByWorldName(event.world.name) != null) {
            event.world.difficulty = Difficulty.PEACEFUL
        }
        
        // マイワールドのLikeSignホログラムを生成
        val worldName = event.world.name
        if (worldName.startsWith("my_world.")) {
            val worldUuidStr = worldName.removePrefix("my_world.")
            val worldUuid = try { 
                java.util.UUID.fromString(worldUuidStr) 
            } catch (e: Exception) { 
                return 
            }
            val worldData = plugin.worldConfigRepository.findByUuid(worldUuid)
            if (worldData != null) {
                plugin.likeSignManager.spawnHologramsForWorld(worldData)
            }
        }
    }
}
