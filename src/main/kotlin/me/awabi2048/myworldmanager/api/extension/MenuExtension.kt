package me.awabi2048.myworldmanager.api.extension

import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory

interface MenuExtension {
    fun getId(): String

    fun onRender(inventory: Inventory, player: Player, context: MenuExtensionContext) {}

    fun onClick(event: InventoryClickEvent, player: Player, context: MenuExtensionContext): Boolean = false
}
