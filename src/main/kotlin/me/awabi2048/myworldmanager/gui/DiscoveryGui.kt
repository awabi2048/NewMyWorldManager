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

    fun open(player: Player, page: Int = 0, showBackButton: Boolean = false) {
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
        val plainTitle = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(title)
        val currentTitle = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(player.openInventory.title())
        
        val inventory = if (player.openInventory.topInventory.size == 54 && currentTitle == plainTitle) {
            player.openInventory.topInventory
        } else {
            Bukkit.createInventory(null, 54, title)
        }

        // 背景
        val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
        val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
        val whitePane = createDecorationItem(Material.WHITE_STAINED_GLASS_PANE)

        // Clear content area if reusing
        if (player.openInventory.topInventory == inventory) {
            for (i in 0 until 54) inventory.setItem(i, null)
        }

        for (i in 0 until 54) inventory.setItem(i, grayPane)
        for (i in 0..8) inventory.setItem(i, blackPane)
        for (i in 45..53) inventory.setItem(i, blackPane)

        // ページ内アイテムの配置
        val startIndex = currentPage * itemsPerPage
        val pageWorlds = sortedWorlds.drop(startIndex).take(itemsPerPage)
        
        // レイアウト定義: 上位10枠 (21-23, 28-34) と 通常枠 (それ以外)
        val topSlots = listOf(21, 22, 23, 28, 29, 30, 31, 32, 33, 34)
        // 9-44の中でtopSlotsに含まれないものを抽出
        val normalSlots = (9..44).filter { !topSlots.contains(it) }

        pageWorlds.forEachIndexed { index, worldData ->
            if (index < topSlots.size) {
                 inventory.setItem(topSlots[index], createWorldItem(player, worldData))
            } else {
                 val normalIndex = index - topSlots.size
                 if (normalIndex < normalSlots.size) {
                     inventory.setItem(normalSlots[normalIndex], createWorldItem(player, worldData))
                 }
            }
        }

        // コントロールボタン
        if (currentPage > 0) {
            inventory.setItem(46, me.awabi2048.myworldmanager.util.GuiHelper.createPrevPageItem(plugin, player, "discovery", currentPage - 1))
        }
        if (currentPage < totalPages - 1) {
            inventory.setItem(53, me.awabi2048.myworldmanager.util.GuiHelper.createNextPageItem(plugin, player, "discovery", currentPage + 1))
        }

        // ソート & フィルタ
        inventory.setItem(48, createSortButton(player, session.sort))
        inventory.setItem(49, createStatsItem(player, session.sort, session.selectedTag, sortedWorlds.size))
        inventory.setItem(50, createTagFilterButton(player, session.selectedTag))
        
        if (showBackButton) {
            inventory.setItem(45, me.awabi2048.myworldmanager.util.GuiHelper.createReturnItem(plugin, player, "discovery"))
        }

        me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(plugin, player, "discovery", title)
        
        if (player.openInventory.topInventory != inventory) {
            player.openInventory(inventory)
        }
    }

    private fun createWorldItem(player: Player, data: WorldData): ItemStack {
        val item = ItemStack(data.icon)
        val meta = item.itemMeta ?: return item
        val lang = plugin.languageManager
        
        meta.displayName(lang.getComponent(player, "gui.common.world_item_name", mapOf("world" to data.name)).decoration(TextDecoration.ITALIC, false))
        
        val lore = mutableListOf<Component>()
        lore.add(lang.getComponent(player, "gui.common.separator").decoration(TextDecoration.ITALIC, false))
        
        if (data.description.isNotEmpty()) {
            lore.add(lang.getComponent(player, "gui.common.world_desc", mapOf("description" to data.description)).decoration(TextDecoration.ITALIC, false))
            lore.add(lang.getComponent(player, "gui.common.separator").decoration(TextDecoration.ITALIC, false))
        }

        val owner = Bukkit.getOfflinePlayer(data.owner)
        val onlineColor = lang.getMessage(player, "publish_level.color.online")
        val offlineColor = lang.getMessage(player, "publish_level.color.offline")
        val ownerColor = if (owner.isOnline) onlineColor else offlineColor
        lore.add(lang.getComponent(player, "gui.discovery.world_item.owner", mapOf("status_color" to ownerColor, "owner" to (owner.name ?: lang.getMessage(player, "general.unknown")))).decoration(TextDecoration.ITALIC, false))
        
        lore.add(lang.getComponent(player, "gui.discovery.world_item.favorite", mapOf("count" to data.favorite)).decoration(TextDecoration.ITALIC, false))
        lore.add(lang.getComponent(player, "gui.discovery.world_item.recent_visitors", mapOf("count" to data.recentVisitors.sum())).decoration(TextDecoration.ITALIC, false))
        
        lore.add(lang.getComponent(player, "gui.common.separator").decoration(TextDecoration.ITALIC, false))
        lore.add(lang.getComponent(player, "gui.discovery.world_item.warp").decoration(TextDecoration.ITALIC, false))
        lore.add(lang.getComponent(player, "gui.discovery.world_item.preview").decoration(TextDecoration.ITALIC, false))
        lore.add(lang.getComponent(player, "gui.discovery.world_item.favorite_toggle").decoration(TextDecoration.ITALIC, false))
        lore.add(lang.getComponent(player, "gui.common.separator").decoration(TextDecoration.ITALIC, false))

        meta.lore(lore)
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_WORLD_ITEM)
        ItemTag.setWorldUuid(item, data.uuid)
        return item
    }

    private fun createSortButton(player: Player, currentSort: DiscoverySort): ItemStack {
        val lang = plugin.languageManager
        val item = ItemStack(plugin.menuConfigManager.getIconMaterial("discovery", "sort", Material.HOPPER))
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
        val item = ItemStack(plugin.menuConfigManager.getIconMaterial("discovery", "tag_filter", Material.NAME_TAG))
        val meta = item.itemMeta ?: return item
        
        meta.displayName(lang.getComponent(player, "gui.discovery.tag_filter.name").color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD))
        
        val lore = mutableListOf<Component>()
        lore.add(lang.getComponent(player, "gui.common.separator"))
        
        val currentTagName = selectedTag?.let { lang.getMessage(player, "world_tag.${it.name.lowercase()}") } ?: lang.getMessage(player, "gui.discovery.tag_filter.all")
        lore.add(LegacyComponentSerializer.legacySection().deserialize("§f§l| §7${lang.getMessage(player, "gui.discovery.tag_filter.current_prefix")} $currentTagName").decoration(TextDecoration.ITALIC, false))
        
        lore.add(lang.getComponent(player, "gui.common.separator"))
        
        // "All" option
        val allSelected = selectedTag == null
        val allColor = if (allSelected) "§e" else "§7"
        lore.add(LegacyComponentSerializer.legacySection().deserialize("$allColor- ${lang.getMessage(player, "gui.discovery.tag_filter.all")}").decoration(TextDecoration.ITALIC, false))

        WorldTag.values().forEach { tag ->
             val isSelected = selectedTag == tag
             val color = if (isSelected) "§e" else "§7"
             val tagName = lang.getMessage(player, "world_tag.${tag.name.lowercase()}")
             lore.add(LegacyComponentSerializer.legacySection().deserialize("$color- $tagName").decoration(TextDecoration.ITALIC, false))
        }

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
        lore.add(lang.getComponent(player, "gui.discovery.stats.sort", mapOf("sort" to sortName)))
        
        val tagName = tag?.let { lang.getMessage(player, "world_tag.${it.name.lowercase()}") } ?: lang.getMessage(player, "gui.discovery.tag_filter.all")
        lore.add(lang.getComponent(player, "gui.discovery.stats.tag", mapOf("tag" to tagName)))
        
        lore.add(lang.getComponent(player, "gui.discovery.stats.count", mapOf("count" to count)))
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
