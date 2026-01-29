package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class FavoriteConfirmGui(private val plugin: MyWorldManager) {

    fun open(player: Player, worldData: WorldData) {
        val lang = plugin.languageManager
        val title = lang.getMessage(player, "gui.favorite.remove_confirm.title")
        me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(plugin, player, "favorite_menu", Component.text(title))

        val inventory = Bukkit.createInventory(null, 27, Component.text(title))

        // Decoration
        val grayPane = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        val grayMeta = grayPane.itemMeta
        grayMeta?.displayName(Component.empty())
        grayMeta?.isHideTooltip = true
        grayPane.itemMeta = grayMeta
        ItemTag.tagItem(grayPane, ItemTag.TYPE_GUI_DECORATION)

        for (i in 0 until 27) inventory.setItem(i, grayPane)

        // Center: World Info (Context)
        val worldItem = ItemStack(worldData.icon)
        val worldMeta = worldItem.itemMeta
        worldMeta.displayName(lang.getComponent(player, "gui.common.world_item_name", mapOf("name" to worldData.name)).decoration(TextDecoration.ITALIC, false))
        val lore = mutableListOf<Component>()
        lore.addAll(lang.getComponentList(player, "gui.favorite.remove_confirm.lore", mapOf("world" to worldData.name)))
        worldMeta.lore(lore)
        worldItem.itemMeta = worldMeta
        ItemTag.tagItem(worldItem, ItemTag.TYPE_GUI_INFO)
        inventory.setItem(13, worldItem)

        // Confirm Button (Right side - 15)
        val confirmItem = ItemStack(Material.RED_CONCRETE)
        val confirmMeta = confirmItem.itemMeta
        confirmMeta?.displayName(Component.text(lang.getMessage(player, "gui.common.confirm")))
        confirmItem.itemMeta = confirmMeta
        ItemTag.tagItem(confirmItem, ItemTag.TYPE_GUI_CONFIRM)
        ItemTag.setWorldUuid(confirmItem, worldData.uuid)
        inventory.setItem(15, confirmItem)

        // Cancel Button (Left side - 11)
        val cancelItem = ItemStack(Material.LIME_CONCRETE)
        val cancelMeta = cancelItem.itemMeta
        cancelMeta?.displayName(Component.text(lang.getMessage(player, "gui.common.cancel")))
        cancelItem.itemMeta = cancelMeta
        ItemTag.tagItem(cancelItem, ItemTag.TYPE_GUI_CANCEL)
        ItemTag.setWorldUuid(cancelItem, worldData.uuid)
        inventory.setItem(11, cancelItem)

        player.openInventory(inventory)
    }
}
