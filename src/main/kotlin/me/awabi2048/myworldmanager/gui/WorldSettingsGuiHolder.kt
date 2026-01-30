package me.awabi2048.myworldmanager.gui

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

class WorldSettingsGuiHolder : InventoryHolder {
    lateinit var inv: Inventory
    override fun getInventory(): Inventory = inv
}
