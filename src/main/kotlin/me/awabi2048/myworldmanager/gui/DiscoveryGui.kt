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

    private val itemsPerPage = 10

    fun open(player: Player, page: Int = 0, showBackButton: Boolean? = null) {
        val lang = plugin.languageManager
        val session = plugin.discoverySessionManager.getSession(player.uniqueId)
        
        if (showBackButton != null) {
            session.showBackButton = showBackButton
        }
        
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
        val inventory = if (player.openInventory.topInventory.holder is DiscoveryGuiHolder) {
            player.openInventory.topInventory
        } else {
            val holder = DiscoveryGuiHolder()
            val inv = Bukkit.createInventory(holder, 54, title)
            holder.inv = inv
            inv
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

        // ワールド表示エリアの背景 (21-23, 28-34)
        val worldItemSlots = listOf(21, 22, 23, 28, 29, 30, 31, 32, 33, 34)
        worldItemSlots.forEach { inventory.setItem(it, whitePane) }

        // ページ内アイテムの配置 (1ページのみ, 上位10件)
        val pageWorlds = sortedWorlds.take(itemsPerPage)

        if (sortedWorlds.isEmpty()) {
            val noResultItem = ItemStack(Material.GRAY_DYE)
            val noResultMeta = noResultItem.itemMeta
            noResultMeta.displayName(lang.getComponent(player, "gui.discovery.no_result").decoration(TextDecoration.ITALIC, false))
            noResultItem.itemMeta = noResultMeta
            ItemTag.tagItem(noResultItem, ItemTag.TYPE_GUI_DECORATION)
            inventory.setItem(31, noResultItem)
        } else {
            pageWorlds.forEachIndexed { index, worldData ->
                inventory.setItem(worldItemSlots[index], createWorldItem(player, worldData))
            }

            // SPOTLIGHT ソート時の空枠埋め
            if (session.sort == DiscoverySort.SPOTLIGHT) {
                for (i in pageWorlds.size until worldItemSlots.size) {
                    inventory.setItem(worldItemSlots[i], createSpotlightEmptyItem(player))
                }
            }
        }

        // ソート & フィルタ
        inventory.setItem(48, createSortButton(player, session.sort))
        inventory.setItem(49, createStatsItem(player, session.sort, session.selectedTag, sortedWorlds.size))
        inventory.setItem(50, createTagFilterButton(player, session.selectedTag))

        if (session.showBackButton) {
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

        val sortName = lang.getMessage(player, "gui.discovery.sort.type.${currentSort.name.lowercase()}")
        lore.add(LegacyComponentSerializer.legacySection().deserialize("§f§l| §7${lang.getMessage(player, "gui.discovery.tag_filter.current_prefix")} §f$sortName").decoration(TextDecoration.ITALIC, false))

        val sortDesc = lang.getMessage(player, "gui.discovery.sort_info.${currentSort.name.lowercase()}")
        lore.add(LegacyComponentSerializer.legacySection().deserialize("  §8$sortDesc").decoration(TextDecoration.ITALIC, false))

        lore.add(lang.getComponent(player, "gui.common.separator"))
        DiscoverySort.values().forEach { sort ->
            val prefix = if (sort == currentSort) "§6» " else "§7  "
            val color = if (sort == currentSort) "§e" else "§7"
            val key = "gui.discovery.sort.type.${sort.name.lowercase()}"
            lore.add(LegacyComponentSerializer.legacySection().deserialize("$prefix$color${lang.getMessage(player, key)}").decoration(TextDecoration.ITALIC, false))
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

        val currentTagName = selectedTag?.let { lang.getMessage(player, "world_tag.${it.name.lowercase()}") } ?: lang.getMessage(player, "gui.discovery.tag_filter.no_selection")
        lore.add(LegacyComponentSerializer.legacySection().deserialize("§f§l| §7${lang.getMessage(player, "gui.discovery.tag_filter.current_prefix")} §f$currentTagName").decoration(TextDecoration.ITALIC, false))

        lore.add(lang.getComponent(player, "gui.common.separator"))

        WorldTag.values().forEach { tag ->
             val isSelected = selectedTag == tag
             val prefix = if (isSelected) "§6» " else "§7  "
             val color = if (isSelected) "§e" else "§7"
             val tagName = lang.getMessage(player, "world_tag.${tag.name.lowercase()}")
             lore.add(LegacyComponentSerializer.legacySection().deserialize("$prefix$color$tagName").decoration(TextDecoration.ITALIC, false))
        }

        lore.add(lang.getComponent(player, "gui.common.separator"))
        lore.add(lang.getComponent(player, "gui.discovery.tag_filter.click_left"))
        lore.add(lang.getComponent(player, "gui.discovery.tag_filter.click_right"))

        meta.lore(lore)
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_DISCOVERY_TAG)
        return item
    }

    private fun createSpotlightEmptyItem(player: Player): ItemStack {
        val lang = plugin.languageManager
        val item = ItemStack(Material.GLASS_PANE)
        val meta = item.itemMeta ?: return item

        meta.displayName(lang.getComponent(player, "gui.discovery.spotlight_empty.name").decoration(TextDecoration.ITALIC, false))

        val loreLines = if (player.hasPermission("myworldmanager.admin")) {
            lang.getComponentList(player, "gui.discovery.spotlight_empty.lore_admin")
        } else {
            lang.getComponentList(player, "gui.discovery.spotlight_empty.lore_visitor")
        }
        meta.lore(loreLines.map { it.decoration(TextDecoration.ITALIC, false) })

        item.itemMeta = meta
        ItemTag.tagItem(item, "discovery_spotlight_empty")
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

    class DiscoveryGuiHolder : org.bukkit.inventory.InventoryHolder {
        lateinit var inv: org.bukkit.inventory.Inventory
        override fun getInventory(): org.bukkit.inventory.Inventory = inv
    }
}
