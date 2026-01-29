package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.model.WorldTag
import me.awabi2048.myworldmanager.session.DiscoverySort
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.*

class DiscoveryGui(private val plugin: MyWorldManager) {

    private val itemsPerPage = 36

    fun open(player: Player, page: Int = 0) {
        val lang = plugin.languageManager
        val session = plugin.discoverySessionManager.getSession(player.uniqueId)
        
        // ワールドの取得とフィルタリング
        val allWorlds = plugin.worldConfigRepository.findAll()
            .filter { it.publishLevel == PublishLevel.PUBLIC && !it.isArchived }
            .filter { session.selectedTag == null || it.tags.contains(session.selectedTag) }

        // ソート
        val sortedWorlds = when (session.sort) {
            DiscoverySort.HOT -> allWorlds.sortedByDescending { it.recentVisitors.sum() }
            DiscoverySort.NEW -> allWorlds.sortedByDescending { it.createdAt }
            DiscoverySort.FAVORITES -> allWorlds.sortedByDescending { it.favorite }
            DiscoverySort.SPOTLIGHT -> {
                val spotlightUuids = plugin.spotlightRepository.findAll()
                allWorlds.filter { spotlightUuids.contains(it.uuid) }
                    .sortedBy { spotlightUuids.indexOf(it.uuid) }
            }
            DiscoverySort.RANDOM -> allWorlds.shuffled()
        }

        val totalPages = if (sortedWorlds.isEmpty()) 1 else (sortedWorlds.size + itemsPerPage - 1) / itemsPerPage
        val currentPage = page.coerceIn(0, totalPages - 1)

        val titleKey = "gui.discovery.title"
        val title = lang.getComponent(player, titleKey).color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD)
        val inventory = Bukkit.createInventory(null, 54, title)

        // 背景
        val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
        val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
        val whitePane = createDecorationItem(Material.WHITE_STAINED_GLASS_PANE)

        for (i in 0 until 54) inventory.setItem(i, grayPane)
        for (i in 0..8) inventory.setItem(i, blackPane)
        for (i in 45..53) inventory.setItem(i, blackPane)

        // 特別なレイアウト (上位10位)
        val topSlots = listOf(21, 22, 23, 28, 29, 30, 31, 32, 33, 34)
        topSlots.forEachIndexed { index, slot ->
            if (index < sortedWorlds.size) {
                inventory.setItem(slot, createWorldItem(player, sortedWorlds[index]))
            } else {
                inventory.setItem(slot, whitePane)
            }
        }

        // 通常表示 (ページネーション)
        // ここの仕様（上位10位とページネーションの関係）が不明確だが、
        // 一般的なページ表示として実装する。
        val startIndex = currentPage * itemsPerPage
        val pageWorlds = sortedWorlds.drop(startIndex).take(itemsPerPage)
        
        // ページネーション用のスロットを決定する必要があるが、
        // ユーザー指定の「スロット21-23, 28-34」は中央部分。
        // FavoriteGuiを参考にしつつ、空いている場所に配置する。
        // もし「1ページ36アイテム」が全スロット（9-44）を指すなら、
        // 上位10位の指定スロット以外をページアイテムで埋める。
        var worldIdx = 0
        for (slot in 9..44) {
            if (topSlots.contains(slot)) continue
            if (worldIdx < pageWorlds.size) {
                inventory.setItem(slot, createWorldItem(player, pageWorlds[worldIdx]))
                worldIdx++
            }
        }

        // コントロールボタン
        if (currentPage > 0) {
            inventory.setItem(46, createNavButton(player, lang.getMessage(player, "gui.common.prev_page"), Material.ARROW, currentPage - 1))
        }
        if (currentPage < totalPages - 1) {
            inventory.setItem(53, createNavButton(player, lang.getMessage(player, "gui.common.next_page"), Material.ARROW, currentPage + 1))
        }

        // ソート & フィルタ
        inventory.setItem(48, createSortButton(player, session.sort))
        inventory.setItem(49, createStatsItem(player, session.sort, session.selectedTag, sortedWorlds.size))
        inventory.setItem(50, createTagFilterButton(player, session.selectedTag))
        
        // 戻るボタン (通常はメインメニューへ)
        inventory.setItem(45, createReturnButton(player))

        plugin.soundManager.playMenuOpenSound(player, "discovery")
        player.openInventory(inventory)
    }

    private fun createWorldItem(player: Player, data: WorldData): ItemStack {
        val item = ItemStack(data.icon)
        val meta = item.itemMeta ?: return item
        val lang = plugin.languageManager
        
        meta.displayName(lang.getComponent(player, "gui.common.world_item_name", data.name).decoration(TextDecoration.ITALIC, false))
        
        val lore = mutableListOf<Component>()
        lore.add(lang.getComponent(player, "gui.common.separator").decoration(TextDecoration.ITALIC, false))
        
        if (data.description.isNotEmpty()) {
            lore.add(lang.getComponent(player, "gui.common.world_desc", data.description).decoration(TextDecoration.ITALIC, false))
            lore.add(lang.getComponent(player, "gui.common.separator").decoration(TextDecoration.ITALIC, false))
        }

        val owner = Bukkit.getOfflinePlayer(data.owner)
        val onlineColor = lang.getMessage(player, "publish_level.color.online")
        val offlineColor = lang.getMessage(player, "publish_level.color.offline")
        val ownerColor = if (owner.isOnline) onlineColor else offlineColor
        lore.add(lang.getComponent(player, "gui.discovery.world_item.owner", ownerColor, owner.name ?: lang.getMessage(player, "general.unknown")).decoration(TextDecoration.ITALIC, false))
        
        lore.add(lang.getComponent(player, "gui.discovery.world_item.favorite", data.favorite).decoration(TextDecoration.ITALIC, false))
        lore.add(lang.getComponent(player, "gui.discovery.world_item.recent_visitors", data.recentVisitors.sum()).decoration(TextDecoration.ITALIC, false))
        
        lore.add(lang.getComponent(player, "gui.common.separator").decoration(TextDecoration.ITALIC, false))
        lore.add(lang.getComponent(player, "gui.discovery.world_item.warp").decoration(TextDecoration.ITALIC, false))
        lore.add(lang.getComponent(player, "gui.discovery.world_item.preview").decoration(TextDecoration.ITALIC, false))
        lore.add(lang.getComponent(player, "gui.discovery.world_item.favorite_toggle").decoration(TextDecoration.ITALIC, false))

        meta.lore(lore)
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_WORLD_ITEM)
        ItemTag.setWorldUuid(item, data.uuid)
        return item
    }

    private fun createSortButton(player: Player, currentSort: DiscoverySort): ItemStack {
        val lang = plugin.languageManager
        val item = ItemStack(Material.HOPPER)
        val meta = item.itemMeta ?: return item
        
        meta.displayName(lang.getComponent(player, "gui.discovery.sort.name").color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD))
        
        val lore = mutableListOf<Component>()
        lore.add(lang.getComponent(player, "gui.common.separator"))
        DiscoverySort.values().forEach { sort ->
            val prefix = if (sort == currentSort) "§a▶ " else "§7  "
            val key = "gui.discovery.sort.type.${sort.name.lowercase()}"
            lore.add(Component.text(prefix + lang.getMessage(player, key)).decoration(TextDecoration.ITALIC, false))
        }
        lore.add(lang.getComponent(player, "gui.common.separator"))
        lore.add(lang.getComponent(player, "gui.discovery.sort.click"))
        
        meta.lore(lore)
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_DISCOVERY_SORT)
        return item
    }

    private fun createTagFilterButton(player: Player, selectedTag: WorldTag?): ItemStack {
        val lang = plugin.languageManager
        val item = ItemStack(Material.NAME_TAG)
        val meta = item.itemMeta ?: return item
        
        meta.displayName(lang.getComponent(player, "gui.discovery.tag_filter.name").color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD))
        
        val lore = mutableListOf<Component>()
        lore.add(lang.getComponent(player, "gui.common.separator"))
        
        val currentTagName = selectedTag?.let { lang.getMessage(player, "world_tag.${it.name.lowercase()}") } ?: lang.getMessage(player, "gui.discovery.tag_filter.all")
        lore.add(lang.getComponent(player, "gui.discovery.tag_filter.current", currentTagName))
        
        lore.add(lang.getComponent(player, "gui.common.separator"))
        lore.add(lang.getComponent(player, "gui.discovery.tag_filter.click_left"))
        lore.add(lang.getComponent(player, "gui.discovery.tag_filter.click_right"))
        
        meta.lore(lore)
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_DISCOVERY_TAG)
        return item
    }

    private fun createStatsItem(player: Player, sort: DiscoverySort, tag: WorldTag?, count: Int): ItemStack {
        val lang = plugin.languageManager
        val item = ItemStack(Material.BOOK)
        val meta = item.itemMeta ?: return item
        
        meta.displayName(lang.getComponent(player, "gui.discovery.stats.name").color(NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD))
        
        val lore = mutableListOf<Component>()
        lore.add(lang.getComponent(player, "gui.common.separator"))
        
        val sortName = lang.getMessage(player, "gui.discovery.sort.type.${sort.name.lowercase()}")
        lore.add(lang.getComponent(player, "gui.discovery.stats.sort", sortName))
        
        val tagName = tag?.let { lang.getMessage(player, "world_tag.${it.name.lowercase()}") } ?: lang.getMessage(player, "gui.discovery.tag_filter.all")
        lore.add(lang.getComponent(player, "gui.discovery.stats.tag", tagName))
        
        lore.add(lang.getComponent(player, "gui.discovery.stats.count", count))
        lore.add(lang.getComponent(player, "gui.common.separator"))
        
        meta.lore(lore)
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_INFO)
        return item
    }

    private fun createNavButton(player: Player, label: String, material: Material, targetPage: Int): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.displayName(LegacyComponentSerializer.legacySection().deserialize(label).decoration(TextDecoration.ITALIC, false))
        item.itemMeta = meta
        ItemTag.setTargetPage(item, targetPage)
        val lang = plugin.languageManager
        val type = if (label == lang.getMessage(player, "gui.common.next_page")) ItemTag.TYPE_GUI_NAV_NEXT else ItemTag.TYPE_GUI_NAV_PREV
        ItemTag.tagItem(item, type)
        return item
    }

    private fun createReturnButton(player: Player): ItemStack {
        val lang = plugin.languageManager
        val item = ItemStack(Material.REDSTONE)
        val meta = item.itemMeta ?: return item
        meta.displayName(lang.getComponent(player, "gui.common.return").color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD))
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_RETURN)
        return item
    }

    private fun createDecorationItem(material: Material): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.displayName(Component.empty())
        meta.isHideTooltip = true
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_DECORATION)
        return item
    }
}
