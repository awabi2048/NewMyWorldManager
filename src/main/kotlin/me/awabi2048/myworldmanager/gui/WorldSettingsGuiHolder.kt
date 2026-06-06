package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.api.extension.WorldSettingsInventoryHolder
import org.bukkit.inventory.Inventory

class WorldSettingsGuiHolder : WorldSettingsInventoryHolder {
    lateinit var inv: Inventory
    override fun getInventory(): Inventory = inv
}
