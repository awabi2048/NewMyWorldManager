package me.awabi2048.myworldmanager.api.extension

import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

interface MenuExtension {
    fun getId(): String

    fun onPrepare(context: MenuExtensionContext) {}

    fun onRender(inventory: Inventory, player: Player, context: MenuExtensionContext) {}

    fun onClick(click: ClickType, item: ItemStack, player: Player, context: MenuExtensionContext): Boolean = false
}
