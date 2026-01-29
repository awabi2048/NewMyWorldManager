package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class FavoriteMenuGui(private val plugin: MyWorldManager) {

    fun open(player: Player, worldData: WorldData?) {
        val lang = plugin.languageManager
        val titleKey = "gui.favorite.favorite_menu.title"
        val title = lang.getComponent(player, titleKey).color(NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD)
        plugin.soundManager.playMenuOpenSound(player, "favorite_menu")
        val inventory = Bukkit.createInventory(null, 45, title)

        val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE, worldData)
        val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE, worldData)

        // 背景配置
        for (i in 0 until 45) {
            inventory.setItem(i, grayPane)
        }
        for (i in 0..8) inventory.setItem(i, blackPane)
        for (i in 36..44) inventory.setItem(i, blackPane)


        if (worldData != null && worldData.owner == player.uniqueId) {
            // オーナーの場合：お気に入りリストを中央に配置
            inventory.setItem(22, createFavoriteListItem(player, worldData))
        } else {
            // オーナー以外の場合：通常レイアウト
            // Slot 20: ほかのワールド
            if (worldData != null) {
                inventory.setItem(20, createOtherWorldsItem(player, worldData))
            }

            // Slot 22: お気に入り追加/削除
            inventory.setItem(22, createToggleFavoriteItem(player, worldData))

            // Slot 24: お気に入り一覧
            inventory.setItem(24, createFavoriteListItem(player, worldData))
        }

        // Slot 40: ワールドアイコン
        if (worldData != null) {
            inventory.setItem(40, createWorldInfoItem(player, worldData))
        }

        player.openInventory(inventory)
    }

    private fun createOtherWorldsItem(player: Player, worldData: WorldData): ItemStack {
        val lang = plugin.languageManager
        val item = ItemStack(Material.COMPASS)
        val meta = item.itemMeta ?: return item
        meta.displayName(lang.getComponent(player, "gui.favorite.favorite_menu.other_worlds.name").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD))
        
        val lore = lang.getComponentList(player, "gui.favorite.favorite_menu.other_worlds.lore")
        meta.lore(lore)
        
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
            meta.displayName(lang.getComponent(player, "gui.favorite.favorite_menu.toggle.name_restricted"))
            meta.lore(listOf(lang.getComponent(player, "gui.favorite.favorite_menu.toggle.lore_restricted_not_managed")))
            item.itemMeta = meta
            ItemTag.tagItem(item, ItemTag.TYPE_GUI_INFO)
            return item
        }
        
        if (worldData.owner == player.uniqueId) {
            val item = ItemStack(Material.BARRIER)
            val meta = item.itemMeta ?: return item
            meta.displayName(lang.getComponent(player, "gui.favorite.favorite_menu.toggle.name_restricted"))
            meta.lore(listOf(lang.getComponent(player, "gui.favorite.favorite_menu.toggle.lore_restricted_owner")))
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
        
        meta.displayName(lang.getComponent(player, nameKey))
        val lore = mutableListOf<Component>()
        lore.add(lang.getComponent(player, loreKey))
        lore.add(lang.getComponent(player, "gui.favorite.favorite_menu.toggle.click"))
        meta.lore(lore)
        
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_FAVORITE_TOGGLE)
        ItemTag.setWorldUuid(item, worldData.uuid)
        return item
    }

    private fun createFavoriteListItem(player: Player, worldData: WorldData?): ItemStack {
        val lang = plugin.languageManager
        val item = ItemStack(Material.BOOK)
        val meta = item.itemMeta ?: return item
        meta.displayName(lang.getComponent(player, "gui.favorite.favorite_menu.list.name").color(NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD))
        
        val lore = lang.getComponentList(player, "gui.favorite.favorite_menu.list.lore")
        meta.lore(lore)
        
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_FAVORITE_LIST)
        if (worldData != null) ItemTag.setWorldUuid(item, worldData.uuid)
        return item
    }

    private fun createWorldInfoItem(player: Player, worldData: WorldData): ItemStack {
        val item = ItemStack(worldData.icon)
        val meta = item.itemMeta ?: return item
        val lang = plugin.languageManager
        meta.displayName(lang.getComponent(player, "gui.common.world_item_name", worldData.name).decoration(TextDecoration.ITALIC, false))
        
        val lore = mutableListOf<Component>()
        lore.add(lang.getComponent(player, "gui.common.separator").decoration(TextDecoration.ITALIC, false))
        
        if (worldData.description.isNotEmpty()) {
            lore.add(lang.getComponent(player, "gui.common.world_desc", worldData.description).decoration(TextDecoration.ITALIC, false))
            lore.add(lang.getComponent(player, "gui.common.separator").decoration(TextDecoration.ITALIC, false))
        }

        val owner = Bukkit.getOfflinePlayer(worldData.owner)
        val onlineColor = lang.getMessage(player, "publish_level.color.online")
        val offlineColor = lang.getMessage(player, "publish_level.color.offline")
        val ownerColor = if (owner.isOnline) onlineColor else offlineColor
        lore.add(lang.getComponent(player, "gui.favorite.world_item.owner", ownerColor, owner.name ?: lang.getMessage(player, "general.unknown")).decoration(TextDecoration.ITALIC, false))
        
        lore.add(lang.getComponent(player, "gui.favorite.world_item.favorite", worldData.favorite).decoration(TextDecoration.ITALIC, false))
        val totalRecent = worldData.recentVisitors.sum()
        lore.add(lang.getComponent(player, "gui.favorite.world_item.recent_visitors", totalRecent).decoration(TextDecoration.ITALIC, false))

        meta.lore(lore)
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_INFO)
        return item
    }

    private fun createDecorationItem(material: Material, worldData: WorldData? = null): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.displayName(Component.empty())
        meta.isHideTooltip = true
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_DECORATION)
        if (worldData != null) ItemTag.setWorldUuid(item, worldData.uuid)
        return item
    }
}
