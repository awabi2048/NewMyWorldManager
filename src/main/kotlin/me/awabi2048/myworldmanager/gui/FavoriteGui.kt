package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class FavoriteGui(private val plugin: MyWorldManager) {

        private val itemsPerPage = 27 // 2行目から4行目までの3行分

        fun open(
                player: Player,
                page: Int = 0,
                returnToWorld: WorldData? = null,
                returnToFavoriteMenu: Boolean = false,
                showBackButton: Boolean? = null
        ) {
                val lang = plugin.languageManager
                val session = plugin.favoriteSessionManager.getSession(player.uniqueId)

                if (showBackButton != null) {
                        session.showBackButton = showBackButton
                }
                session.returnToFavoriteMenu = returnToFavoriteMenu
                val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                val favWorldUuids = stats.favoriteWorlds.keys
                val selectedTag = session.selectedTag

                val resolvedWorlds =
                        favWorldUuids.mapNotNull { uuid ->
                                val data = plugin.worldConfigRepository.findByUuid(uuid)
                                if (data == null) {
                                        stats.favoriteWorlds.remove(uuid)
                                        null
                                } else {
                                        data
                                }
                        }
                val allWorlds = resolvedWorlds.filter { worldData ->
                                selectedTag == null || worldData.tags.contains(selectedTag)
                         }
                if (allWorlds.size != favWorldUuids.size) {
                        plugin.playerStatsRepository.save(stats)
                }

                val totalPages =
                        if (allWorlds.isEmpty()) 1
                        else (allWorlds.size + itemsPerPage - 1) / itemsPerPage
                val currentPage = page.coerceIn(0, totalPages - 1)

                val titleKey = "gui.favorite.title"
                if (!lang.hasKey(player, titleKey)) {
                        player.sendMessage(
                                "§c[MyWorldManager] Error: Missing translation key: $titleKey"
                        )
                        return
                }
                val title = me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(lang.getMessage(player, titleKey))
                me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(
                        plugin,
                        player,
                        "favorite",
                        title,
                        FavoriteGuiHolder::class.java
                )

                val inventorySize = 45
                val footerStart = 36

                val holder = FavoriteGuiHolder()
                val inventory = Bukkit.createInventory(holder, inventorySize, title)
                holder.inv = inventory

                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        player.uniqueId,
                        me.awabi2048.myworldmanager.session.SettingsAction.FAVORITE_GUI,
                        isGui = true
                )

                val blackPane =
                        createDecorationItem(Material.BLACK_STAINED_GLASS_PANE, returnToWorld)
                for (i in 0..8) inventory.setItem(i, blackPane)
                for (i in footerStart until inventorySize) inventory.setItem(i, blackPane)

                val startIndex = currentPage * itemsPerPage
                val pageWorlds = allWorlds.drop(startIndex).take(itemsPerPage)

                for ((index, data) in pageWorlds.withIndex()) {
                        inventory.setItem(index + 9, createWorldItem(player, data, returnToWorld))
                }

                // お気に入りがない場合の特殊表示
                if (allWorlds.isEmpty()) {
                        val emptyItem = ItemStack(Material.QUARTZ)
                        val emptyMeta = emptyItem.itemMeta
                        val emptyMessageKey =
                                if (resolvedWorlds.isEmpty()) {
                                        "gui.favorite.empty_message_no_favorites"
                                } else {
                                        "gui.favorite.empty_message"
                                }
                        emptyMeta?.displayName(
                                lang.getComponent(player, emptyMessageKey)
                                        .decoration(TextDecoration.ITALIC, false)
                        )
                        emptyItem.itemMeta = emptyMeta
                        ItemTag.tagItem(emptyItem, ItemTag.TYPE_GUI_INFO)
                        inventory.setItem(22, emptyItem)
                }

                if (currentPage > 0) {
                        val item =
                                me.awabi2048.myworldmanager.util.GuiHelper.createPrevPageItem(
                                        plugin,
                                        player,
                                        "favorite",
                                        currentPage - 1
                                )
                        if (returnToWorld != null)
                                me.awabi2048.myworldmanager.util.ItemTag.setWorldUuid(
                                        item,
                                        returnToWorld.uuid
                                )
                        else if (session.returnToFavoriteMenu)
                                ItemTag.setString(item, "favorite_return_target", "favorite_menu")
                        inventory.setItem(footerStart + 1, item)
                }

                inventory.setItem(footerStart + 4, createPlayerHead(player, allWorlds.size))

                if (currentPage < totalPages - 1) {
                        val item =
                                me.awabi2048.myworldmanager.util.GuiHelper.createNextPageItem(
                                        plugin,
                                        player,
                                        "favorite",
                                        currentPage + 1
                                )
                        if (returnToWorld != null)
                                me.awabi2048.myworldmanager.util.ItemTag.setWorldUuid(
                                        item,
                                        returnToWorld.uuid
                                )
                        else if (session.returnToFavoriteMenu)
                                ItemTag.setString(item, "favorite_return_target", "favorite_menu")
                        inventory.setItem(footerStart + 8, item)
                }

                // タグフィルターボタン
                val tagFilterButton = createTagFilterButton(player, session.selectedTag)
                if (returnToWorld != null) {
                        ItemTag.setWorldUuid(tagFilterButton, returnToWorld.uuid)
                } else if (session.returnToFavoriteMenu) {
                        ItemTag.setString(tagFilterButton, "favorite_return_target", "favorite_menu")
                }
                inventory.setItem(footerStart + 7, tagFilterButton)

                // 戻るボタン
                val returnItem =
                        me.awabi2048.myworldmanager.util.GuiHelper.createReturnItem(
                                plugin,
                                player,
                                "favorite"
                        )
                if (returnToWorld != null) {
                        me.awabi2048.myworldmanager.util.ItemTag.setWorldUuid(
                                returnItem,
                                returnToWorld.uuid
                        )
                } else if (session.returnToFavoriteMenu) {
                        ItemTag.setString(returnItem, "favorite_return_target", "favorite_menu")
                }
                inventory.setItem(footerStart, returnItem)

                val whiteBackground =
                        createDecorationItem(Material.WHITE_STAINED_GLASS_PANE, returnToWorld)
                for (row in 1..3) {
                        for (col in 0..8) {
                                val slot = row * 9 + col
                                if (inventory.getItem(slot) == null) {
                                        inventory.setItem(slot, whiteBackground)
                                }
                        }
                }

                val borderBackground =
                        createDecorationItem(Material.GRAY_STAINED_GLASS_PANE, returnToWorld)
                for (slot in 0 until inventory.size) {
                        if (inventory.getItem(slot) == null) {
                                inventory.setItem(slot, borderBackground)
                        }
                }

                player.openInventory(inventory)
                me.awabi2048.myworldmanager.util.GuiHelper.scheduleGuiTransitionReset(plugin, player)
        }

        private fun createWorldItem(
                player: Player,
                data: WorldData,
                returnToWorld: WorldData?
        ): ItemStack {
                val item = ItemStack(data.icon)
                val meta = item.itemMeta ?: return item
                val lang = plugin.languageManager
                val isBedrock = plugin.playerPlatformResolver.isBedrock(player)
                val worldName = lang.getMessageStrict(player, data.name) ?: data.name
                meta.displayName(
                        lang.getComponent(
                                player,
                                "gui.common.world_item_name",
                                mapOf("world" to worldName)
                        ).decoration(TextDecoration.ITALIC, false)
                )

                val formattedDesc = if (data.description.isNotEmpty()) {
                        lang.getMessage(player, "gui.common.world_desc", mapOf("description" to data.description))
                } else ""

                val onlineColor = lang.getMessage(player, "publish_level.color.online")
                val offlineColor = lang.getMessage(player, "publish_level.color.offline")
                val statusColor = if (Bukkit.getOfflinePlayer(data.owner).isOnline) onlineColor else offlineColor
                val ownerLine = lang.getMessage(player, "gui.favorite.world_item.owner", mapOf("owner" to PlayerNameUtil.getNameOrDefault(data.owner, lang.getMessage(player, "general.unknown")), "status_color" to statusColor))


                val favoriteLine = lang.getMessage(player, "gui.favorite.world_item.favorite", mapOf("count" to data.favorite))
                val visitorLine = lang.getMessage(player, "gui.favorite.world_item.recent_visitors", mapOf("count" to data.recentVisitors.sum()))

                val tagLine = if (data.tags.isNotEmpty()) {
                        val tagNames = data.tags.joinToString(", ") {
                                plugin.worldTagManager.getDisplayName(player, it)
                        }
                        lang.getMessage(player, "gui.favorite.world_item.tag", mapOf("tags" to tagNames))
                } else ""

                val warpLine = if (!data.isArchived && (data.publishLevel == PublishLevel.PUBLIC || data.publishLevel == PublishLevel.FRIEND)) {
                        if (isBedrock) {
                                lang.getMessage(player, "gui.favorite.world_item.warp_bedrock")
                        } else {
                                lang.getMessage(player, "gui.favorite.world_item.warp")
                        }
                } else ""

                val viewerUuid = player.uniqueId
                val isMember = data.owner == viewerUuid || data.moderators.contains(viewerUuid) || data.members.contains(viewerUuid)

                val archivedLine = if (data.isArchived) lang.getMessage(player, "gui.favorite.world_item.archived_label") else ""
                val unfavoriteLine = if (isBedrock) {
                        ""
                } else if (data.isArchived) {
                        lang.getMessage(player, "gui.favorite.world_item.unfavorite_archived")
                } else if (!isMember) {
                        lang.getMessage(player, "gui.favorite.world_item.unfavorite")
                } else ""

                val separator = lang.getComponent(player, "gui.common.separator")

                meta.lore(
                        me.awabi2048.myworldmanager.util.GuiHelper.cleanupLore(
                                lang.getComponentList(
                                        player,
                                        "gui.favorite.world_item.lore",
                                        mapOf(
                                                "description" to formattedDesc,
                                                "owner_line" to ownerLine,
                                                "favorite_line" to favoriteLine,
                                                "visitor_line" to visitorLine,
                                                "tag_line" to tagLine,
                                                "warp_line" to warpLine,
                                                "archived_line" to archivedLine,
                                                "unfavorite_line" to unfavoriteLine
                               )
                                ),
                                separator
                        )
                )

                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_WORLD_ITEM)
                ItemTag.setWorldUuid(item, data.uuid)
                return item
        }

        private fun createBackButton(player: Player, returnToWorld: WorldData?): ItemStack {
                val lang = plugin.languageManager
                val item = ItemStack(Material.REDSTONE)
                val meta = item.itemMeta ?: return item
                meta.displayName(
                        lang.getComponent(player, "gui.common.return")
                                .decorate(TextDecoration.BOLD)
                )
                if (returnToWorld != null) {
                        meta.lore(listOf(lang.getComponent(player, "gui.common.return_desc")))
                }
                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_RETURN)
                if (returnToWorld != null) ItemTag.setWorldUuid(item, returnToWorld.uuid)
                return item
        }

        private fun createNavButton(
                player: Player,
                label: String,
                material: Material,
                targetPage: Int,
                returnToWorld: WorldData?
        ): ItemStack {
                val item = ItemStack(material)
                val meta = item.itemMeta ?: return item

                // 装飾を指定しない (LegacyComponentSerializerにおまかせする)
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
                if (returnToWorld != null) ItemTag.setWorldUuid(item, returnToWorld.uuid)
                return item
        }

        private fun createPlayerHead(player: Player, totalCount: Int): ItemStack {
                val lang = plugin.languageManager
                val item = ItemStack(Material.PLAYER_HEAD)
                val meta = item.itemMeta as? org.bukkit.inventory.meta.SkullMeta ?: return item
                meta.owningPlayer = player

                meta.displayName(
                        lang.getComponent(
                                player,
                                "gui.favorite.player_icon.name",
                                mapOf("player" to PlayerNameUtil.getNameOrDefault(player.uniqueId, lang.getMessage(player, "general.unknown")))
                        ).decoration(TextDecoration.ITALIC, false)
                )

                val lore = mutableListOf<Component>()
                lore.add(lang.getComponent(player, "gui.common.separator"))
                lore.add(
                        lang.getComponent(
                                player,
                                "gui.favorite.player_icon.lore_count",
                                mapOf("count" to totalCount)
                        )
                )
                lore.add(lang.getComponent(player, "gui.common.separator"))

                meta.lore(lore)
                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_INFO)
                return item
        }

        private fun createDecorationItem(
                material: Material,
                returnToWorld: WorldData? = null
        ): ItemStack {
                val item = ItemStack(material)
                val meta = item.itemMeta ?: return item
                meta.displayName(Component.empty())
                meta.isHideTooltip = true
                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_DECORATION)
                if (returnToWorld != null) ItemTag.setWorldUuid(item, returnToWorld.uuid)
                return item
        }

        private fun createTagFilterButton(player: Player, selectedTag: String?): ItemStack {
                val lang = plugin.languageManager
                val item = ItemStack(plugin.menuConfigManager.getIconMaterial("favorite", "tag_filter", Material.NAME_TAG))
                val meta = item.itemMeta ?: return item
                val isBedrock = plugin.playerPlatformResolver.isBedrock(player)

                val tagName = if (selectedTag != null)
                        plugin.worldTagManager.getDisplayName(player, selectedTag)
                else
                        lang.getMessage(player, "gui.discovery.tag_filter.no_selection")

                val prefix = if (selectedTag != null)
                        lang.getMessage(player, "gui.discovery.tag_filter.current_prefix")
                else ""

                val clickLeft = if (isBedrock) lang.getMessage(player, "gui.discovery.tag_filter.click_bedrock") else lang.getMessage(player, "gui.discovery.tag_filter.click_left")
                val clickRight = if (isBedrock) "" else lang.getMessage(player, "gui.discovery.tag_filter.click_right")
                val loreKey = if (isBedrock) "gui.discovery.tag_filter.lore_bedrock" else "gui.discovery.tag_filter.lore"

                val tagList = plugin.worldTagManager.getEnabledTagIds().joinToString("\n") { tagId ->
                        val tagPrefix = if (tagId == selectedTag)
                                lang.getMessage(player, "gui.discovery.tag_filter.active")
                        else
                                lang.getMessage(player, "gui.discovery.tag_filter.inactive")
                        val tagColor = if (tagId == selectedTag) "§e" else "§7"
                        val name = plugin.worldTagManager.getDisplayName(player, tagId)
                        "$tagPrefix$tagColor$name"
                }

                meta.displayName(lang.getComponent(player, "gui.discovery.tag_filter.name").decoration(TextDecoration.ITALIC, false))
                meta.lore(
                        lang.getComponentList(
                                player,
                                loreKey,
                                mapOf(
                                        "prefix" to prefix,
                                        "tag" to tagName,
                                        "tag_list" to tagList,
                                        "click_left" to clickLeft,
                                        "click_right" to clickRight
                                )
                        )
                )

                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_FAVORITE_TAG)
                return item
        }

        class FavoriteGuiHolder : org.bukkit.inventory.InventoryHolder {
                lateinit var inv: org.bukkit.inventory.Inventory
                override fun getInventory(): org.bukkit.inventory.Inventory = inv
        }
}
