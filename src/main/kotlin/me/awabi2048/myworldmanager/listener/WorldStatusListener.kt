package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.service.UnloadedWorldRegistry
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.WorldLoadEvent
import org.bukkit.event.world.WorldUnloadEvent

class WorldStatusListener : Listener {

    @EventHandler
    fun onWorldUnload(event: WorldUnloadEvent) {
        // アンロードされたワールドを登録
        // キャンセルされる可能性もあるので、MONITOR優先度が望ましいが、通常のEventHandlerで処理
        // WorldServiceでの手動アンロードと重複する可能性があるが、Setなので問題ない
        UnloadedWorldRegistry.register(event.world.name)
    }

    @EventHandler
    fun onWorldLoad(event: WorldLoadEvent) {
        // ロードされたワールドは登録解除
        UnloadedWorldRegistry.unregister(event.world.name)
    }
}
