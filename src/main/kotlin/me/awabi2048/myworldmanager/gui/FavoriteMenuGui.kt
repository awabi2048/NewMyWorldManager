package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.ui.ManagedMenuPresenter

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.GuiItemFactory
import me.awabi2048.myworldmanager.util.GuiLoreBuilder
import me.awabi2048.myworldmanager.util.ItemTag
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class FavoriteMenuGui(private val plugin: MyWorldManager) {

    fun open(player: Player, worldData: WorldData?) {
        val lang = plugin.languageManager
        val titleKey = "gui.favorite.favorite_menu.title"
        val layout = GuiHelper.threeChoiceLayout()
        val title = GuiHelper.inventoryTitle(lang.getMessage(player, titleKey))
        GuiHelper.playMenuOpen(player, "favorite_menu")

        val holder = FavoriteMenuGuiHolder()
        val inventory = Bukkit.createInventory(holder, layout.size, title)
        holder.inv = inventory

        plugin.settingsSessionManager.updateSessionAction(
            player,
            worldData?.uuid ?: player.uniqueId,
            me.awabi2048.myworldmanager.session.SettingsAction.FAVORITE_MENU_GUI,
            isGui = true
        )

        val blackPane = GuiItemFactory.decoration(Material.BLACK_STAINED_GLASS_PANE)
        val grayPane = GuiItemFactory.decoration(Material.GRAY_STAINED_GLASS_PANE)
        if (worldData != null) {
            ItemTag.setWorldUuid(blackPane, worldData.uuid)
            ItemTag.setWorldUuid(grayPane, worldData.uuid)
        }

        // 背景配置
        // Keep the favorite menu on the shared three-choice frame while preserving per-world context tags.
        for (i in 0 until layout.size) {
            inventory.setItem(i, grayPane)
        }
        for (i in 0..8) inventory.setItem(i, blackPane)
        for (i in layout.size - 9 until layout.size) inventory.setItem(i, blackPane)


        if (worldData != null && worldData.owner == player.uniqueId) {
            // オーナーの場合：お気に入りリストを中央に配置
            inventory.setItem(layout.centerSlot, createFavoriteListItem(player, worldData))
        } else {
            // オーナー以外の場合：通常レイアウト
            // Slot 20: ほかのワールド
            if (worldData != null) {
                inventory.setItem(layout.leftSlot, createOtherWorldsItem(player, worldData))
            }

            // Slot 22: お気に入り追加/削除
            inventory.setItem(layout.centerSlot, createToggleFavoriteItem(player, worldData))

            // Slot 24: お気に入り一覧
            inventory.setItem(layout.rightSlot, createFavoriteListItem(player, worldData))
        }

        // Slot 40: ワールドアイコン
        if (worldData != null) {
            inventory.setItem(layout.backSlot, createWorldInfoItem(player, worldData))
        }

        ManagedMenuPresenter.open(player, inventory)
        GuiHelper.scheduleGuiTransitionReset(plugin, player)
    }

    private fun createOtherWorldsItem(player: Player, worldData: WorldData): ItemStack {
        val lang = plugin.languageManager
        val item = ItemStack(Material.COMPASS)
        val meta = item.itemMeta ?: return item
        meta.displayName(lang.getComponent(player, "gui.favorite.favorite_menu.other_worlds.name"))

        meta.lore(actionLore(player, "gui.favorite.favorite_menu.other_worlds"))

        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_FAVORITE_OTHER_WORLDS)
        ItemTag.setWorldUuid(item, worldData.uuid)
        return item
    }

    private fun createToggleFavoriteItem(player: Player, worldData: WorldData?): ItemStack {
        val lang = plugin.languageManager

        if (worldData == null) {
            val item = ItemStack(Material.BARRIER)
            val meta = item.itemMeta ?: return item
            meta.displayName(lang.getComponent(player, "gui.favorite.favorite_menu.toggle.name_restricted").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false))
            meta.lore(GuiItemFactory.menuLore(listOf(GuiLoreLine.Warning(lang.getMessage(player, "gui.favorite.favorite_menu.toggle.lore_restricted_not_managed")))))
            item.itemMeta = meta
            ItemTag.tagItem(item, ItemTag.TYPE_GUI_INFO)
            return item
        }

        if (worldData.owner == player.uniqueId) {
            val item = ItemStack(Material.BARRIER)
            val meta = item.itemMeta ?: return item
            meta.displayName(lang.getComponent(player, "gui.favorite.favorite_menu.toggle.name_restricted").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false))
            meta.lore(GuiItemFactory.menuLore(listOf(GuiLoreLine.Warning(lang.getMessage(player, "gui.favorite.favorite_menu.toggle.lore_restricted_owner")))))
            item.itemMeta = meta
            ItemTag.tagItem(item, ItemTag.TYPE_GUI_INFO)
            return item
        }

        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        val isFavorite = stats.favoriteWorlds.containsKey(worldData.uuid)

        val material = if (isFavorite) Material.RED_DYE else Material.GRAY_DYE
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item

        val nameKey = if (isFavorite) "gui.favorite.favorite_menu.toggle.name_remove" else "gui.favorite.favorite_menu.toggle.name_add"
        val loreKey = if (isFavorite) "gui.favorite.favorite_menu.toggle.lore_remove" else "gui.favorite.favorite_menu.toggle.lore_add"

        meta.displayName(lang.getComponent(player, nameKey).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false))
        meta.lore(CCSystem.getAPI().getLoreService().render(GuiLoreSpec.Blocks(listOf(
            GuiLoreBlock(listOf(GuiLoreLine.Text(lang.getMessage(player, loreKey)))),
            GuiLoreBlock(listOf(me.awabi2048.myworldmanager.util.GuiLoreActions.singleClick(
                lang,
                player,
                lang.getMessage(player, "gui.favorite.favorite_menu.toggle.action")
            )))
        ))))

        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_FAVORITE_TOGGLE)
        ItemTag.setWorldUuid(item, worldData.uuid)
        return item
    }

    private fun createFavoriteListItem(player: Player, worldData: WorldData?): ItemStack {
        val lang = plugin.languageManager
        val item = ItemStack(Material.BOOK)
        val meta = item.itemMeta ?: return item
        meta.displayName(lang.getComponent(player, "gui.favorite.favorite_menu.list.name"))

        meta.lore(actionLore(player, "gui.favorite.favorite_menu.list"))

        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_FAVORITE_LIST)
        if (worldData != null) ItemTag.setWorldUuid(item, worldData.uuid)
        return item
    }

    private fun createWorldInfoItem(player: Player, worldData: WorldData): ItemStack {
        val lang = plugin.languageManager

        val item = ItemStack(worldData.icon)
        val meta = item.itemMeta ?: return item
        val owner = Bukkit.getOfflinePlayer(worldData.owner)
        val ownerName = owner.name ?: lang.getMessage(player, "general.unknown")
        val tagNames = worldData.tags.takeIf(List<String>::isNotEmpty)?.joinToString(", ") {
            plugin.worldTagManager.getDisplayName(player, it)
        }
        val lore = CCSystem.getAPI().getLoreService().render(GuiLoreSpec.Blocks(listOf(
            GuiLoreBlock(buildList {
                add(GuiLoreLine.Data(lang.getMessage(player, "gui.common.world_item.world_name"), worldData.name, "§a"))
                if (worldData.description.isNotBlank()) add(GuiLoreLine.UserText(worldData.description))
            }),
            GuiLoreBlock(buildList {
                add(GuiLoreLine.Data(lang.getMessage(player, "gui.common.world_item.owner"), ownerName, "§b"))
                add(GuiLoreLine.Data(lang.getMessage(player, "gui.common.world_item.favorite"), worldData.favorite, "§c"))
                add(GuiLoreLine.Data(
                    lang.getMessage(player, "gui.common.world_item.recent_visitors"),
                    lang.getMessage(player, "gui.common.world_item.recent_visitors_value", mapOf("count" to worldData.recentVisitors.sum())),
                    "§a"
                ))
                if (tagNames != null) add(GuiLoreLine.Data(lang.getMessage(player, "gui.common.world_item.tags"), tagNames, "§e"))
            })
        )))

        meta.displayName(lang.getComponent(player, "gui.favorite.current_world.name"))
        meta.lore(lore)
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_INFO)
        ItemTag.setWorldUuid(item, worldData.uuid)
        return item
    }

    private fun actionLore(player: Player, key: String) =
        CCSystem.getAPI().getLoreService().render(GuiLoreSpec.Blocks(listOf(
            GuiLoreBlock(plugin.languageManager.getMessageList(player, "$key.lore").map(GuiLoreLine::Text)),
            GuiLoreBlock(listOf(me.awabi2048.myworldmanager.util.GuiLoreActions.singleClick(
                plugin.languageManager,
                player,
                plugin.languageManager.getMessage(player, "$key.action")
            )))
        )))

    class FavoriteMenuGuiHolder : org.bukkit.inventory.InventoryHolder {
        lateinit var inv: org.bukkit.inventory.Inventory
        override fun getInventory(): org.bukkit.inventory.Inventory = inv
    }
}
