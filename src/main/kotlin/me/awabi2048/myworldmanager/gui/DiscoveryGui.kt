package me.awabi2048.myworldmanager.gui

import java.util.*
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.api.extension.DiscoveryMenuRequest
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.session.DiscoverySort
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.GuiItemFactory
import me.awabi2048.myworldmanager.util.StructuredLore
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import java.time.LocalDate

class DiscoveryGui(private val plugin: MyWorldManager) {

        private val itemsPerPage = 10

        fun open(player: Player, page: Int = 0, showBackButton: Boolean? = null) {
                val lang = plugin.languageManager
                val session = plugin.discoverySessionManager.getSession(player.uniqueId)

                if (showBackButton != null) {
                        session.showBackButton = showBackButton
                }

                if (
                        MyWorldManagerApi.openDiscoveryMenuOverride(
                                player,
                                DiscoveryMenuRequest(
                                        page = page,
                                        showBackButton = session.showBackButton
                                )
                        )
                ) {
                        return
                }

                // ワールドの取得とフィルタリング
                val selectedTag = session.selectedTag?.takeIf {
                        it in plugin.worldTagManager.getEnabledTagIds()
                }
                if (selectedTag != session.selectedTag) {
                        session.selectedTag = null
                }
                val allWorlds =
                        plugin.worldConfigRepository
                                .findAll()
                                .filter { MyWorldManagerApi.getWorldAccessPolicy().canShowInDiscovery(player, it) }
                                .filter {
                                        selectedTag == null || it.tags.contains(selectedTag)
                                }

                // ソート
                val sortedWorlds =
                        when (session.sort) {
                                DiscoverySort.HOT ->
                                        allWorlds.sortedByDescending { it.recentVisitors.sum() }
                                DiscoverySort.NEW -> allWorlds.sortedByDescending { it.createdAt }
                                DiscoverySort.FAVORITES ->
                                        allWorlds.sortedByDescending { it.favorite }
                                DiscoverySort.SPOTLIGHT -> {
                                        val spotlightUuids = plugin.spotlightRepository.findAll()
                                        // Spotlight登録ワールドは公開設定に関わらず表示する
                                        spotlightUuids.mapNotNull { uuid ->
                                                plugin.worldConfigRepository.findByUuid(uuid)
                                        }.filter { MyWorldManagerApi.getWorldAccessPolicy().canShowInDiscovery(player, it) }
                                }
                                DiscoverySort.RANDOM -> {
                                        val seed = LocalDate.now().toEpochDay()
                                        allWorlds
                                                .sortedBy { it.uuid.toString() }
                                                .shuffled(Random(seed))
                                }
                        }

                val totalPages =
                        if (sortedWorlds.isEmpty()) 1
                        else (sortedWorlds.size + itemsPerPage - 1) / itemsPerPage
                val currentPage = page.coerceIn(0, totalPages - 1)

                val titleKey = "gui.discovery.title"
                val title = GuiHelper.inventoryTitle(lang.getMessage(player, titleKey))
                val inventorySize = GuiHelper.settingsLayout().size
                val inventory =
                        if (player.openInventory.topInventory.holder is DiscoveryGuiHolder) {
                                player.openInventory.topInventory
                        } else {
                                val holder = DiscoveryGuiHolder()
                                val inv = Bukkit.createInventory(holder, inventorySize, title)
                                holder.inv = inv
                                inv
                        }

                // 背景
                val grayPane = GuiItemFactory.decoration(Material.GRAY_STAINED_GLASS_PANE)
                val blackPane = GuiItemFactory.decoration(Material.BLACK_STAINED_GLASS_PANE)
                val whitePane = GuiItemFactory.decoration(Material.WHITE_STAINED_GLASS_PANE)

                // Clear content area if reusing
                if (player.openInventory.topInventory == inventory) {
                        for (i in 0 until inventorySize) inventory.setItem(i, null)
                }

                GuiItemFactory.applyStandardFrame(inventory)

                // ワールド表示エリアの背景 (21-23, 28-34)
                val worldItemSlots = listOf(21, 22, 23, 28, 29, 30, 31, 32, 33, 34)
                worldItemSlots.forEach { inventory.setItem(it, whitePane) }

                // ページ内アイテムの配置 (1ページのみ, 上位10件)
                val pageWorlds = sortedWorlds.take(itemsPerPage)

                if (sortedWorlds.isEmpty()) {
                        // SPOTLIGHT ソート時は空枠を表示
                        if (session.sort == DiscoverySort.SPOTLIGHT) {
                                for (i in 0 until worldItemSlots.size) {
                                        inventory.setItem(
                                                worldItemSlots[i],
                                                createSpotlightEmptyItem(player)
                                        )
                                }
                        } else {
                                val noResultItem = ItemStack(Material.GRAY_DYE)
                                val noResultMeta = noResultItem.itemMeta
                                noResultMeta.displayName(
                                        lang.getComponent(player, "gui.discovery.no_result")
                                                .decoration(TextDecoration.ITALIC, false)
                                )
                                noResultItem.itemMeta = noResultMeta
                                ItemTag.tagItem(noResultItem, ItemTag.TYPE_GUI_DECORATION)
                                inventory.setItem(31, noResultItem)
                        }
                } else {
                        pageWorlds.forEachIndexed { index, worldData ->
                                inventory.setItem(
                                        worldItemSlots[index],
                                        createWorldItem(player, worldData)
                                )
                        }

                        // SPOTLIGHT ソート時の空枠埋め
                        if (session.sort == DiscoverySort.SPOTLIGHT) {
                                for (i in pageWorlds.size until worldItemSlots.size) {
                                        inventory.setItem(
                                                worldItemSlots[i],
                                                createSpotlightEmptyItem(player)
                                        )
                                }
                        }
                }

                // ソート & フィルタ
                inventory.setItem(48, createSortButton(player, session.sort))
                GuiHelper.setSettingsFooter(
                        inventory,
                        if (session.showBackButton) GuiHelper.createReturnItem(plugin, player, "discovery") else null,
                        createStatsItem(player, session.sort, session.selectedTag, sortedWorlds.size)
                )
                inventory.setItem(50, createTagFilterButton(player, session.selectedTag))

                GuiHelper.playMenuSoundIfTitleChanged(
                        plugin,
                        player,
                        "discovery",
                        title
                )

                if (player.openInventory.topInventory != inventory) {
                        player.openInventory(inventory)
                }
        }

        private fun createWorldItem(player: Player, data: WorldData): ItemStack {
                val item = ItemStack(data.icon)
                val meta = item.itemMeta ?: return item
                val lang = plugin.languageManager
                val isBedrock = plugin.playerPlatformResolver.isBedrock(player)

                meta.displayName(
                        lang.getComponent(
                                player,
                                "gui.common.world_item_name",
                                mapOf("world" to data.name)
                        ).decoration(TextDecoration.ITALIC, false)
                )

                val ownerRef = Bukkit.getOfflinePlayer(data.owner)
                val onlineColor = lang.getMessage(player, "publish_level.color.online")
                val offlineColor = lang.getMessage(player, "publish_level.color.offline")
                val ownerColor = if (ownerRef.isOnline) onlineColor else offlineColor

                val favorites = data.favorite
                val visitors = data.recentVisitors.sum()
                val currentWorldData = plugin.worldConfigRepository.findByWorldName(player.world.name)
                val isCurrentWorld = currentWorldData?.uuid == data.uuid
                val isFavoritedByViewer = plugin.playerStatsRepository.findByUuid(player.uniqueId).favoriteWorlds.containsKey(data.uuid)

                val formattedDesc = if (data.description.isNotEmpty()) {
                        lang.getMessage(player, "gui.common.world_desc", mapOf("description" to data.description))
                } else ""

                val tagNames = if (data.tags.isNotEmpty()) {
                        val tagsStr = data.tags.joinToString(", ") {
                                plugin.worldTagManager.getDisplayName(player, it)
                        }
                        lang.getMessage(player, "gui.discovery.world_item.tag", mapOf("tags" to tagsStr))
                } else {
                        ""
                }

                val warpHint = if (isBedrock) {
                        lang.getMessage(player, "gui.discovery.world_item.warp_hint_bedrock")
                } else if (isCurrentWorld) {
                        ""
                } else {
                        lang.getMessage(player, "gui.discovery.world_item.warp_hint")
                }
                val previewHint = if (isCurrentWorld || isBedrock) "" else lang.getMessage(player, "gui.discovery.world_item.preview_hint")
                val memberRequestHint = if (isBedrock) "" else lang.getMessage(player, "gui.discovery.world_item.member_request_hint")
                val favoriteHint = if (isBedrock) {
                        ""
                } else if (isFavoritedByViewer) {
                        lang.getMessage(player, "gui.discovery.world_item.favorite_hint_remove")
                } else {
                        lang.getMessage(player, "gui.discovery.world_item.favorite_hint_add")
                }
                val ownerLine = lang.getMessage(player, "gui.discovery.world_item.owner", mapOf(
                        "owner" to PlayerNameUtil.getNameOrDefault(data.owner, lang.getMessage(player, "general.unknown")),
                        "status_color" to ownerColor
                ))
                val favoriteLine = lang.getMessage(player, "gui.discovery.world_item.favorite", mapOf("count" to favorites))
                val visitorLine = lang.getMessage(player, "gui.discovery.world_item.recent_visitors", mapOf("count" to visitors))
                meta.lore(CCSystem.getAPI().getLoreService().render(StructuredLore.blocks(
                        *buildList {
                                if (formattedDesc.isNotBlank()) add(listOf(formattedDesc))
                                add(listOf(ownerLine, favoriteLine, visitorLine) + listOfNotNull(tagNames.takeIf(String::isNotBlank)))
                                add(listOf(warpHint, previewHint, memberRequestHint, favoriteHint).filter(String::isNotBlank))
                        }.toTypedArray()
                )))

                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_WORLD_ITEM)
                ItemTag.setWorldUuid(item, data.uuid)
                return item
        }

        private fun createSortButton(player: Player, currentSort: DiscoverySort): ItemStack {
                val lang = plugin.languageManager
                val item = ItemStack(plugin.menuConfigManager.getIconMaterial("discovery", "sort", Material.HOPPER))
                val meta = item.itemMeta ?: return item

                val sortDesc = getSortDescription(player, currentSort)

                meta.displayName(lang.getComponent(player, "gui.discovery.sort.display"))
                val canEditSpotlight = currentSort == DiscoverySort.SPOTLIGHT && canManageSpotlight(player)
                val options = DiscoverySort.values().map { sort ->
                        sort to lang.getMessage(player, "gui.discovery.sort.type.${sort.name.lowercase()}")
                }
                meta.lore(CCSystem.getAPI().getLoreService().render(
                        GuiLoreSpec.Rich(buildList {
                                add(GuiLoreLine.Data(
                                        lang.getMessage(player, "gui.discovery.sort.label"),
                                        options.first { it.first == currentSort }.second,
                                        "\u00A7e"
                                ))
                                add(GuiLoreLine.Spacer)
                                add(GuiLoreLine.Text(sortDesc))
                                add(GuiLoreLine.Spacer)
                                options.forEach { (sort, displayName) ->
                                        val selected = sort == currentSort
                                        val marker = if (selected) "\u00A7a\u00BB" else "\u00A78\u30FB"
                                        val color = if (selected) "\u00A7e" else "\u00A77"
                                        add(GuiLoreLine.Raw("\u00A7f\u2759 $marker $color$displayName"))
                                }
                                add(GuiLoreLine.Spacer)
                                add(GuiLoreLine.Action(
                                        lang.getMessage(player, "gui.settings.click.left"),
                                        lang.getMessage(player, "gui.discovery.sort.action.previous")
                                ))
                                add(GuiLoreLine.Action(
                                        lang.getMessage(player, "gui.settings.click.right"),
                                        lang.getMessage(player, "gui.discovery.sort.action.next")
                                ))
                                if (canEditSpotlight) {
                                        add(GuiLoreLine.Action(
                                                lang.getMessage(player, "gui.discovery.sort.action.edit_operation"),
                                                lang.getMessage(player, "gui.discovery.sort.action.edit_spotlight")
                                        ))
                                }
                        }, GuiLoreFrame.BOTH)
                ))

                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_DISCOVERY_SORT)
                return item
        }

        private fun createTagFilterButton(player: Player, selectedTag: String?): ItemStack {
                val lang = plugin.languageManager
                val item = ItemStack(plugin.menuConfigManager.getIconMaterial("discovery", "tag_filter", Material.NAME_TAG))
                val meta = item.itemMeta ?: return item
                val isBedrock = plugin.playerPlatformResolver.isBedrock(player)

                meta.displayName(lang.getComponent(player, "gui.discovery.tag_filter.name"))
                val options = listOf(
                        "" to lang.getMessage(player, "gui.discovery.tag_filter.no_selection")
                ) + plugin.worldTagManager.getEnabledTagIds().map { tagId ->
                        tagId to plugin.worldTagManager.getDisplayName(player, tagId)
                }
                val selectedId = selectedTag.orEmpty()
                val selectedOption = options.firstOrNull { it.first == selectedId } ?: options.first()
                meta.lore(CCSystem.getAPI().getLoreService().render(
                        GuiLoreSpec.Rich(buildList {
                                add(GuiLoreLine.Data(
                                        lang.getMessage(player, "gui.discovery.tag_filter.label"),
                                        selectedOption.second,
                                        "\u00A7e"
                                ))
                                add(GuiLoreLine.Spacer)
                                options.forEach { (tagId, displayName) ->
                                        val selected = tagId == selectedOption.first
                                        val marker = if (selected) "\u00A7a\u00BB" else "\u00A78\u30FB"
                                        val color = if (selected) "\u00A7e" else "\u00A77"
                                        add(GuiLoreLine.Raw("\u00A7f\u2759 $marker $color$displayName"))
                                }
                                add(GuiLoreLine.Spacer)
                                if (isBedrock) {
                                        add(GuiLoreLine.SingleAction(
                                                lang.getMessage(player, "gui.discovery.tag_filter.action.next")
                                        ))
                                } else {
                                        add(GuiLoreLine.Action(
                                                lang.getMessage(player, "gui.settings.click.left"),
                                                lang.getMessage(player, "gui.discovery.tag_filter.action.next")
                                        ))
                                        add(GuiLoreLine.Action(
                                                lang.getMessage(player, "gui.settings.click.right"),
                                                lang.getMessage(player, "gui.discovery.tag_filter.action.clear")
                                        ))
                                }
                        }, GuiLoreFrame.BOTH)
                ))

                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_DISCOVERY_TAG)
                return item
        }

        private fun canManageSpotlight(player: Player): Boolean {
                return player.hasPermission("myworldmanager.admin")
        }

        private fun getSortDescription(player: Player, sort: DiscoverySort): String {
                val lang = plugin.languageManager
                if (sort != DiscoverySort.SPOTLIGHT) {
                        return lang.getMessage(player, "gui.discovery.sort_info.${sort.name.lowercase()}")
                }

                return plugin.spotlightRepository.getDescription()
                        ?: lang.getMessage(player, "gui.discovery.sort_info.spotlight")
        }

        private fun createSpotlightEmptyItem(player: Player): ItemStack {
                val lang = plugin.languageManager
                val item = ItemStack(Material.GLASS_PANE)
                val meta = item.itemMeta ?: return item
                val isBedrock = plugin.playerPlatformResolver.isBedrock(player)

                meta.displayName(
                        lang.getComponent(player, "gui.discovery.spotlight_empty.name")
                                .decoration(TextDecoration.ITALIC, false)
                )

                val loreLines =
                        if (player.hasPermission("myworldmanager.admin")) {
                                lang.getMenuLore(
                                        player,
                                        if (isBedrock) "gui.discovery.spotlight_empty.lore_admin_bedrock" else "gui.discovery.spotlight_empty.lore_admin"
                                )
                        } else {
                                lang.getMenuLore(
                                        player,
                                        "gui.discovery.spotlight_empty.lore_visitor"
                                )
                        }

                meta.lore(loreLines)

                item.itemMeta = meta
                ItemTag.tagItem(item, "discovery_spotlight_empty")
                return item
        }

        private fun createStatsItem(
                player: Player,
                sort: DiscoverySort,
                tag: String?,
                count: Int
        ): ItemStack {
                val lang = plugin.languageManager
                val item = ItemStack(Material.BOOK)
                val meta = item.itemMeta ?: return item

                val sortName = lang.getMessage(player, "gui.discovery.sort.type.${sort.name.lowercase()}")
                val tagName = tag?.let { plugin.worldTagManager.getDisplayName(player, it) } ?: lang.getMessage(player, "gui.discovery.tag_filter.all")
                val desc = lang.getMessage(player, "gui.discovery.stats.desc")

                meta.displayName(lang.getComponent(player, "gui.discovery.stats.name"))
                meta.lore(
                        lang.getMenuLore(
                                player,
                                "gui.discovery.stats.lore",
                                mapOf(
                                        "sort" to sortName,
                                        "tag" to tagName,
                                        "count" to count,
                                        "desc" to desc
                                )
                        )
                )

                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_INFO)
                return item
        }

        private fun createNavButton(
                player: Player,
                label: String,
                material: Material,
                targetPage: Int
        ): ItemStack {
                val item = ItemStack(material)
                val meta = item.itemMeta ?: return item
                meta.displayName(
                        LegacyComponentSerializer.legacySection()
                                .deserialize(label)
                                .decoration(TextDecoration.ITALIC, false)
                )
                item.itemMeta = meta
                ItemTag.setTargetPage(item, targetPage)
                val lang = plugin.languageManager
                val type =
                        if (label == lang.getMessage(player, "gui.common.next_page"))
                                ItemTag.TYPE_GUI_NAV_NEXT
                        else ItemTag.TYPE_GUI_NAV_PREV
                ItemTag.tagItem(item, type)
                return item
        }

        private fun createReturnButton(player: Player): ItemStack {
                val lang = plugin.languageManager
                val item = ItemStack(Material.REDSTONE)
                val meta = item.itemMeta ?: return item
                meta.displayName(
                        lang.getComponent(player, "gui.common.return")
                                .color(NamedTextColor.YELLOW)
                                .decorate(TextDecoration.BOLD)
                )
                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_RETURN)
                return item
        }

        class DiscoveryGuiHolder : org.bukkit.inventory.InventoryHolder {
                lateinit var inv: org.bukkit.inventory.Inventory
                override fun getInventory(): org.bukkit.inventory.Inventory = inv
        }
}
