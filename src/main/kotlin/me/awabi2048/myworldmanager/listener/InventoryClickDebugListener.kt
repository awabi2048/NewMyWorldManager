package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import java.util.UUID

class InventoryClickDebugListener(private val plugin: MyWorldManager) : Listener {
    private val lastLogAt = mutableMapOf<UUID, Long>()

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    fun onInventoryClick(event: InventoryClickEvent) {
        if (!event.isCancelled) return
        val player = event.whoClicked as? Player ?: return
        val clickedInventory = event.clickedInventory
        if (clickedInventory != null && clickedInventory != player.inventory) return

        val now = System.currentTimeMillis()
        val last = lastLogAt[player.uniqueId] ?: 0L
        if (now - last < 500L) return
        lastLogAt[player.uniqueId] = now

        val title = PlainTextComponentSerializer.plainText().serialize(event.view.title())
        val top = event.view.topInventory
        val bottom = event.view.bottomInventory
        plugin.logger.info(
            "[InventoryClickDebug] cancelled player inventory click: " +
                "player=${player.name}, title=$title, rawSlot=${event.rawSlot}, slot=${event.slot}, " +
                "click=${event.click}, action=${event.action}, current=${event.currentItem?.type}, " +
                "cursor=${event.cursor.type}, topType=${top.type}, bottomType=${bottom.type}, " +
                "topHolder=${top.holder?.javaClass?.name}, clickedHolder=${clickedInventory?.holder?.javaClass?.name}"
        )
    }
}
