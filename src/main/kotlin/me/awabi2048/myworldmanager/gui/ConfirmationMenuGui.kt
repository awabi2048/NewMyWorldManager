package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.util.GuiHelper
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

class ConfirmationMenuGui(private val plugin: MyWorldManager) {

    fun open(
        player: Player,
        menuId: String,
        title: Component,
        centerItem: ItemStack,
        confirmItem: ItemStack,
        cancelItem: ItemStack,
        onConfirm: () -> Unit,
        onCancel: () -> Unit = {}
    ) {
        val holder = ConfirmationMenuHolder(onConfirm, onCancel)
        val inventory = Bukkit.createInventory(holder, 45, GuiHelper.inventoryTitle(title))
        holder.inv = inventory

        GuiHelper.applyConfirmationFrame(inventory)
        inventory.setItem(22, centerItem)
        inventory.setItem(20, confirmItem)
        inventory.setItem(24, cancelItem)

        GuiHelper.playMenuSoundIfTitleChanged(plugin, player, menuId, title, ConfirmationMenuHolder::class.java)
        player.openInventory(inventory)
    }

    class ConfirmationMenuHolder(
        val onConfirm: () -> Unit,
        val onCancel: () -> Unit,
    ) : InventoryHolder {
        lateinit var inv: Inventory
        var resolved: Boolean = false
        override fun getInventory(): Inventory = inv
    }
}
