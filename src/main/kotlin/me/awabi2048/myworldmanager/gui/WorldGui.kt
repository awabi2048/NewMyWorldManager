package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.session.*
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID

/**
 * 管理者用ワールド一覧GUI（ページネーション対応）
 * フィルター、ソート、プレイヤーフィルター機能付き
 */
class WorldGui(private val plugin: MyWorldManager) {

    private val repository = plugin.worldConfigRepository
    private val itemsPerPage = 36 // 2行目から5行目までの4行分

    /**
     * 指定されたページのGUIを開く
     * @param player プレイヤー
     * @param page 0から始まるページ番号（省略時はセッションから取得）
     */
    fun open(player: Player, page: Int? = null, fromAdminMenu: Boolean? = null) {

        
        val session = plugin.adminGuiSessionManager.getSession(player.uniqueId)
        if (fromAdminMenu != null) {
            session.fromAdminMenu = fromAdminMenu
        }
        val currentPage = page ?: session.currentPage
        session.currentPage = currentPage
        plugin.settingsSessionManager.updateSessionAction(player, player.uniqueId, SettingsAction.ADMIN_WORLD_GUI, isGui = true)
        
        // フィルターとソートを適用してワールドリストを取得
        val filteredWorlds = getFilteredAndSortedWorlds(session)
        
        val totalPages = if (filteredWorlds.isEmpty()) 1 else (filteredWorlds.size + itemsPerPage - 1) / itemsPerPage
        val safePage = currentPage.coerceIn(0, totalPages - 1)
        session.currentPage = safePage

        val lang = plugin.languageManager
        val titleKey = "gui.admin.title"
        if (!lang.hasKey(player, titleKey)) {
            player.sendMessage("§c[MyWorldManager] Error: Missing translation key: $titleKey")
            return
        }
        val title = Component.text(lang.getMessage(player, titleKey), NamedTextColor.DARK_GRAY, TextDecoration.BOLD)
        val plainTitle = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(title)
        val currentTitle = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(player.openInventory.title())
        
        me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(plugin, player, "admin_world", title)
        
        val inventory = if (player.openInventory.topInventory.size == 54 && currentTitle == plainTitle) {
            player.openInventory.topInventory
        } else {
            Bukkit.createInventory(null, 54, title)
        }

        // 1行目を黒の板ガラスで敷き詰める
        val blackPane = createBlackPaneItem()
        for (i in 0..8) {
            inventory.setItem(i, blackPane)
        }

        // ワールドアイテムの配置 (スロット9から44まで)
        val startIndex = safePage * itemsPerPage
        val pageWorlds = filteredWorlds.drop(startIndex).take(itemsPerPage)

        // Clear existing world items if reusing
        for (i in 9..44) {
            inventory.setItem(i, null)
        }

        pageWorlds.forEachIndexed { index, worldData ->
            inventory.setItem(index + 9, createWorldItem(player, worldData))
        }

        // 6行目のレイアウト:
        // [<前] [フィルター:アーカイブ] [フィルター:公開] [フィルター:プレイヤー] [統計] [■] [ソート] [■] [次>]
        //  45         46                   47                  48               49   50   51   52   53
        
        // ページ移動ボタン（最終行の両端）
        if (safePage > 0) {
            inventory.setItem(45, createNavButton(lang.getMessage("gui.common.prev_page"), Material.ARROW, safePage - 1))
        } else {
            inventory.setItem(45, blackPane)
        }
        
        if (safePage < totalPages - 1) {
            inventory.setItem(53, createNavButton(lang.getMessage("gui.common.next_page"), Material.ARROW, safePage + 1))
        } else {
            inventory.setItem(53, blackPane)
        }
        
        // フィルターボタン
        inventory.setItem(46, createArchiveFilterButton(player, session))
        inventory.setItem(47, createPublishFilterButton(player, session))
        inventory.setItem(48, createPlayerFilterButton(player, session))
        
        // 統計情報ボタン
        inventory.setItem(49, createInfoButton(filteredWorlds.size, safePage + 1, totalPages))
        
        // 装飾
        inventory.setItem(50, blackPane)
        
        // ソートボタン
        inventory.setItem(51, createSortButton(player, session))
        
        // 装飾
        if (session.fromAdminMenu) {
            inventory.setItem(52, createNavButton(lang.getMessage("gui.common.back"), Material.ARROW, 0)) // ページ0はダミー、タイプで判定
            val backItem = inventory.getItem(52)!!
            ItemTag.tagItem(backItem, ItemTag.TYPE_GUI_RETURN)
        } else {
            inventory.setItem(52, blackPane)
        }

        // 余ったスロットは灰色の板ガラスで埋める (背景)
        val background = createBackgroundItem()
        for (slot in 0 until inventory.size) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, background)
            }
        }

        if (player.openInventory.topInventory != inventory) {
            player.openInventory(inventory)
        }
    }

    /**
     * セッションのフィルター・ソート条件を適用してワールドリストを取得
     */
    private fun getFilteredAndSortedWorlds(session: AdminGuiSession): List<WorldData> {
        var worlds = repository.findAll()
        
        // アーカイブフィルター
        worlds = when (session.archiveFilter) {
            ArchiveFilter.ALL -> worlds
            ArchiveFilter.ACTIVE_ONLY -> worlds.filter { !it.isArchived }
            ArchiveFilter.ARCHIVED_ONLY -> worlds.filter { it.isArchived }
        }
        
        // 公開レベルフィルター
        if (session.publishFilter.publishLevel != null) {
            worlds = worlds.filter { it.publishLevel == session.publishFilter.publishLevel }
        }
        
        // プレイヤーフィルター
        val targetPlayer = session.playerFilter
        
        if (session.playerFilterType != PlayerFilterType.NONE) {
            if (targetPlayer == null) {
                // フィルター有効だがプレイヤー未選択の場合は何も表示しない（または全非表示）
                worlds = emptyList()
            } else {
                worlds = when (session.playerFilterType) {
                    PlayerFilterType.NONE -> worlds // ここには来ないはず
                    PlayerFilterType.OWNER -> worlds.filter { it.owner == targetPlayer }
                    PlayerFilterType.MEMBER -> worlds.filter { 
                        it.owner == targetPlayer || 
                        it.moderators.contains(targetPlayer) || 
                        it.members.contains(targetPlayer) 
                    }
                }
            }
        }
        
        // ソート
        worlds = when (session.sortBy) {
            AdminSortType.CREATED_DESC -> worlds.sortedByDescending { it.createdAt }
            AdminSortType.CREATED_ASC -> worlds.sortedBy { it.createdAt }
            AdminSortType.EXPIRE_ASC -> worlds.sortedBy { it.expireDate }
            AdminSortType.EXPIRE_DESC -> worlds.sortedByDescending { it.expireDate }
            AdminSortType.EXPANSION_DESC -> worlds.sortedByDescending { it.borderExpansionLevel }
            AdminSortType.EXPANSION_ASC -> worlds.sortedBy { it.borderExpansionLevel }
            AdminSortType.MSPT_DESC -> worlds.sortedByDescending { 
                val world = Bukkit.getWorld(it.customWorldName ?: "") ?: Bukkit.getWorld(it.uuid.toString())
                if (world != null) me.awabi2048.myworldmanager.util.ChiyogamiUtil.getWorldMspt(world) else 0.0
            }
        }
        
        return worlds
    }

    /**
     * 黒の板ガラスを作成 (1行目・6行目用)
     */
    private fun createBlackPaneItem(): ItemStack {
        val item = ItemStack(Material.BLACK_STAINED_GLASS_PANE)
        val meta = item.itemMeta ?: return item
        meta.displayName(Component.empty())
        meta.isHideTooltip = true
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_DECORATION)
        return item
    }

    /**
     * 背景用の灰色の板ガラスを作成
     */
    private fun createBackgroundItem(): ItemStack {
        val item = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        val meta = item.itemMeta ?: return item
        meta.displayName(Component.empty())
        meta.isHideTooltip = true
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_DECORATION)
        return item
    }

    /**
     * ワールド情報の表示用アイテム
     */
    private fun createWorldItem(player: Player, data: WorldData): ItemStack {
        val item = ItemStack(data.icon)
        val meta = item.itemMeta ?: return item
        val lang = plugin.languageManager

        meta.displayName(lang.getComponent(null, "gui.common.world_item_name_simple", mapOf("world" to data.name)))

        val lore = mutableListOf<Component>()
        lore.add(lang.getComponent(null, "gui.common.separator"))
        
        val owner = Bukkit.getOfflinePlayer(data.owner)
        val ownerName = owner.name ?: lang.getMessage("general.unknown")
        val ownerColor = if (owner.isOnline) lang.getMessage("publish_level.color.online") else lang.getMessage("publish_level.color.offline")
        lore.add(lang.getComponent(null, "gui.admin.world_item.owner", mapOf("owner" to ownerName, "status_color" to ownerColor)))
        
        val statusText = if (data.isArchived) lang.getMessage("gui.admin.world_item.status_archived") else lang.getMessage("gui.admin.world_item.status_active")
        lore.add(lang.getComponent(null, "gui.admin.world_item.status", mapOf("status" to statusText)))
        
        // 公開レベル表示
        val publishColor = when (data.publishLevel.name) {
            "PUBLIC" -> lang.getMessage("publish_level.color.public")
            "FRIEND" -> lang.getMessage("publish_level.color.friend")
            "PRIVATE" -> lang.getMessage("publish_level.color.private")
            else -> lang.getMessage("publish_level.color.locked")
        }
        val publishName = lang.getMessage("publish_level.${data.publishLevel.name.lowercase()}")
        lore.add(lang.getComponent(null, "gui.admin.world_item.publish", mapOf("color" to publishColor, "level" to publishName)))
        
        // 拡張レベル表示
        if (data.sourceWorld != "CONVERT") {
            val expansionDisplay = if (data.borderExpansionLevel == WorldData.EXPANSION_LEVEL_SPECIAL) "Special" else data.borderExpansionLevel.toString()
            lore.add(lang.getComponent(null, "gui.admin.world_item.expansion", mapOf("level" to expansionDisplay)))
        }
        

        // 作成日の表示
        try {
            val dateTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            val displayFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val createdAtDate = java.time.LocalDateTime.parse(data.createdAt, dateTimeFormatter).toLocalDate()
            val today = java.time.LocalDate.now()
            val daysSince = java.time.temporal.ChronoUnit.DAYS.between(createdAtDate, today)

            val createdInfo = if (daysSince == 0L) {
                lang.getMessage("gui.admin.world_item.created_info_today")
            } else {
                lang.getMessage("gui.admin.world_item.created_info_days", mapOf("days" to daysSince))
            }
            lore.add(lang.getComponent(null, "gui.admin.world_item.created_at", mapOf("date" to createdAtDate.format(displayFormatter), "days_ago" to createdInfo)))
        } catch (e: Exception) {
            // Ignore parsing errors
        }

        // 期限表示の計算
        if (data.sourceWorld != "CONVERT") {
            try {
                val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
                val expireDate = java.time.LocalDate.parse(data.expireDate, formatter)
                val today = java.time.LocalDate.now()
                val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(today, expireDate)
                
                val expireInfo = if (daysBetween >= 0) {
                    lang.getMessage("gui.admin.world_item.expire_info_remaining", mapOf("days" to daysBetween))
                } else {
                    meta.setEnchantmentGlintOverride(true)
                    lang.getMessage("gui.admin.world_item.expire_info_overdue", mapOf("days" to java.lang.Math.abs(daysBetween)))
                }
                lore.add(lang.getComponent(null, "gui.admin.world_item.expires_at", mapOf("date" to data.expireDate, "days_remain" to expireInfo)))
            } catch (e: Exception) {
                lore.add(lang.getComponent(null, "gui.admin.world_item.expires_at", mapOf("date" to data.expireDate, "days_remain" to "")))
            }
        }
        
        // MSPT表示 (Chiyogamiが有効な場合のみ)
        if (me.awabi2048.myworldmanager.util.ChiyogamiUtil.isChiyogamiActive()) {
            val world = Bukkit.getWorld(data.customWorldName ?: "") ?: Bukkit.getWorld(data.uuid.toString())
            if (world != null) {
                val mspt = me.awabi2048.myworldmanager.util.ChiyogamiUtil.getWorldMspt(world)
                val msptString = String.format("%.1f", mspt)
                lore.add(lang.getComponent(null, "gui.admin.world_item.mspt", mapOf("mspt" to msptString)))
            }
        }
        
        lore.add(lang.getComponent(null, "gui.common.separator"))
        lore.add(lang.getComponent(null, "gui.admin.world_item.action_archive"))
        lore.add(lang.getComponent(null, "gui.admin.world_item.action_warp"))

        if (player.gameMode == org.bukkit.GameMode.CREATIVE) {
            lore.add(lang.getComponent(player, "gui.admin.world_item.uuid_copy_hint"))
        }

        lore.add(lang.getComponent(null, "gui.common.separator"))

        meta.lore(lore)
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_WORLD_ITEM)
        ItemTag.setWorldUuid(item, data.uuid)
        return item
    }

    private fun createNavButton(label: String, material: Material, targetPage: Int): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        val lang = plugin.languageManager
        
        meta.displayName(LegacyComponentSerializer.legacySection().deserialize(label).decoration(TextDecoration.ITALIC, false))
        
        item.itemMeta = meta
        ItemTag.setTargetPage(item, targetPage)
        val type = if (label == lang.getMessage("gui.common.next_page")) ItemTag.TYPE_GUI_NAV_NEXT else ItemTag.TYPE_GUI_NAV_PREV
        ItemTag.tagItem(item, type)
        return item
    }
    
    private fun createInfoButton(totalCount: Int, current: Int, total: Int): ItemStack {
        val item = ItemStack(Material.PAPER)
        val meta = item.itemMeta ?: return item
        val lang = plugin.languageManager
        
        meta.displayName(lang.getComponent(null, "gui.admin.info.display"))
        meta.lore(listOf(
            lang.getComponent(null, "gui.admin.info.total_count", mapOf("count" to totalCount)),
            lang.getComponent(null, "gui.admin.info.page", mapOf("page" to current, "total_pages" to total))
        ))
        
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_INFO)
        return item
    }
    
    /**
     * アーカイブフィルターボタン
     */
    private fun createArchiveFilterButton(player: Player, session: AdminGuiSession): ItemStack {
        val item = ItemStack(Material.CHEST)
        val meta = item.itemMeta ?: return item
        val lang = plugin.languageManager
        
        meta.displayName(lang.getComponent(player, "gui.admin.filter.archive.display"))
        
        val lore = mutableListOf<Component>()
        ArchiveFilter.values().forEach { filter ->
            val prefix = if (filter == session.archiveFilter) "§a» " else "§8- "
            lore.add(LegacyComponentSerializer.legacySection().deserialize("$prefix${lang.getMessage(player, filter.displayKey)}"))
        }
        lore.add(Component.empty())
        lore.add(lang.getComponent(player, "gui.admin.filter.click_cycle"))
        
        meta.lore(lore)
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_ADMIN_FILTER_ARCHIVE)
        return item
    }
    
    /**
     * 公開レベルフィルターボタン
     */
    private fun createPublishFilterButton(player: Player, session: AdminGuiSession): ItemStack {
        val item = ItemStack(Material.ENDER_EYE)
        val meta = item.itemMeta ?: return item
        val lang = plugin.languageManager
        
        meta.displayName(lang.getComponent(player, "gui.admin.filter.publish.display"))
        
        val lore = mutableListOf<Component>()
        PublishFilter.values().forEach { filter ->
            val prefix = if (filter == session.publishFilter) "§a» " else "§8- "
            lore.add(LegacyComponentSerializer.legacySection().deserialize("$prefix${lang.getMessage(player, filter.displayKey)}"))
        }
        lore.add(Component.empty())
        lore.add(lang.getComponent(player, "gui.admin.filter.click_lr"))
        
        meta.lore(lore)
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_ADMIN_FILTER_PUBLISH)
        return item
    }
    
    /**
     * プレイヤーフィルターボタン
     */
    private fun createPlayerFilterButton(player: Player, session: AdminGuiSession): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta ?: return item
        val lang = plugin.languageManager
        
        meta.displayName(lang.getComponent(player, "gui.admin.filter.player.display"))
        
        val lore = mutableListOf<Component>()
        val separator = lang.getComponent(null, "gui.common.separator")
        
        lore.add(separator)

        // 現在の設定
        val filterTypeName = lang.getMessage(player, session.playerFilterType.displayKey)
        lore.add(lang.getComponent(player, "gui.admin.filter.player.current_type", mapOf("type" to filterTypeName)))
        
        if (session.playerFilterType != PlayerFilterType.NONE && session.playerFilter != null) {
            val targetName = Bukkit.getOfflinePlayer(session.playerFilter!!).name ?: "Unknown"
            lore.add(lang.getComponent(player, "gui.admin.filter.player.current_player", mapOf("player" to targetName)))
        }

        lore.add(separator)
        lore.add(lang.getComponent(player, "gui.admin.filter.player.click_left"))
        if (session.playerFilterType != PlayerFilterType.NONE) {
            lore.add(lang.getComponent(player, "gui.admin.filter.player.click_right"))
        }
        lore.add(separator)
        
        meta.lore(lore)
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_ADMIN_FILTER_PLAYER)
        return item
    }
    
    /**
     * ソートボタン
     */
    private fun createSortButton(player: Player, session: AdminGuiSession): ItemStack {
        val item = ItemStack(Material.HOPPER)
        val meta = item.itemMeta ?: return item
        val lang = plugin.languageManager
        
        meta.displayName(lang.getComponent(player, "gui.admin.sort.display"))
        
        val lore = mutableListOf<Component>()
        var sortTypes = AdminSortType.values()
        if (!me.awabi2048.myworldmanager.util.ChiyogamiUtil.isChiyogamiActive()) {
            sortTypes = sortTypes.filter { it != AdminSortType.MSPT_DESC }.toTypedArray()
        }

        sortTypes.forEach { sortType ->
            val prefix = if (sortType == session.sortBy) "§a» " else "§8- "
            lore.add(LegacyComponentSerializer.legacySection().deserialize("$prefix${lang.getMessage(player, sortType.displayKey)}"))
        }
        lore.add(Component.empty())
        lore.add(lang.getComponent(player, "gui.admin.filter.click_lr"))
        
        meta.lore(lore)
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_ADMIN_SORT)
        return item
    }
}
