package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.ui.ManagedMenuPresenter

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.ItemTag
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class FavoriteConfirmGui(private val plugin: MyWorldManager) {

    fun open(player: Player, worldData: WorldData) {
        val lang = plugin.languageManager
        val title = lang.getMessage(player, "gui.favorite.remove_confirm.title")
        val titleComponent = me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(title)
        me.awabi2048.myworldmanager.util.GuiHelper.playMenuOpen(player, "favorite_menu")

        val holder = FavoriteConfirmGuiHolder()
        val inventory = GuiHelper.createConfirmationInventory(holder, titleComponent)
        holder.inv = inventory

        me.awabi2048.myworldmanager.util.GuiHelper.applyConfirmationFrame(inventory)

        // Center: World Info (Context)
        val worldName = lang.getMessageStrict(player, worldData.name) ?: worldData.name
        val worldItem = GuiHelper.createContextWorldIconItem(
            plugin,
            player,
            worldData,
            com.awabi2048.ccsystem.api.gui.GuiLoreSpec.Rich(
                lang.getMessageList(player, "gui.favorite.remove_confirm.lore", mapOf("world" to worldName))
                    .map(com.awabi2048.ccsystem.api.gui.GuiLoreLine::Warning),
                com.awabi2048.ccsystem.api.gui.GuiLoreFrame.BOTH
            )
        )

        // Confirm Button
        val confirmItem = ItemStack(Material.RED_CONCRETE)
        val confirmMeta = confirmItem.itemMeta
        confirmMeta?.displayName(lang.getComponent(player, "gui.common.confirm"))
        confirmItem.itemMeta = confirmMeta
        ItemTag.tagItem(confirmItem, ItemTag.TYPE_GUI_CONFIRM)
        ItemTag.setWorldUuid(confirmItem, worldData.uuid)

        // Cancel Button
        val cancelItem = ItemStack(Material.LIME_CONCRETE)
        val cancelMeta = cancelItem.itemMeta
        cancelMeta?.displayName(lang.getComponent(player, "gui.common.cancel"))
        cancelItem.itemMeta = cancelMeta
        ItemTag.tagItem(cancelItem, ItemTag.TYPE_GUI_CANCEL)
        ItemTag.setWorldUuid(cancelItem, worldData.uuid)
        GuiHelper.setConfirmationItems(inventory, worldItem, confirmItem, cancelItem)

        ManagedMenuPresenter.open(player, inventory)
    }

    class FavoriteConfirmGuiHolder : org.bukkit.inventory.InventoryHolder {
        lateinit var inv: org.bukkit.inventory.Inventory
        override fun getInventory(): org.bukkit.inventory.Inventory = inv
    }
}
