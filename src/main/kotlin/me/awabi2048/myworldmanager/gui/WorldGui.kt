package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.service.UnloadedWorldRegistry
import me.awabi2048.myworldmanager.session.*
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** 管理者用ワールド一覧GUI（ページネーション対応） フィルター、ソート、プレイヤーフィルター機能付き */
class WorldGui(private val plugin: MyWorldManager) {

        private val repository = plugin.worldConfigRepository
        private val itemsPerPage = 36 // 2行目から5行目までの4行分
        private val worldSizeCache = ConcurrentHashMap<String, WorldSizeCacheEntry>()
        private val worldSizeInFlight = ConcurrentHashMap.newKeySet<String>()

        private data class WorldSizeCacheEntry(
                val sizeBytes: Long?,
                val updatedAtMillis: Long,
                val failed: Boolean
        )

        /**
         * 指定されたページのGUIを開く
         * @param player プレイヤー
         * @param page 0から始まるページ番号（省略時はセッションから取得）
         * @param fromAdminMenu 管理者メニューから開かれたかどうか (省略時はそのまま)
         * @param suppressSound 開封時の音を抑制するかどうか
         */
        fun open(
                player: Player,
                page: Int? = null,
                fromAdminMenu: Boolean? = null,
                suppressSound: Boolean = false
        ) {
                val session = plugin.adminGuiSessionManager.getSession(player.uniqueId)
                if (fromAdminMenu != null) {
                        session.fromAdminMenu = fromAdminMenu
                }
                val currentPage = page ?: session.currentPage
                session.currentPage = currentPage
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        player.uniqueId,
                        SettingsAction.ADMIN_WORLD_GUI,
                        isGui = true
                )

                repository.loadAll()

                val currentWorldData = plugin.worldConfigRepository.findByWorldName(player.world.name)

                // フィルターとソートを適用してワールドリストを取得（現在地ワールドは一覧から除外）
                val filteredWorlds = getFilteredAndSortedWorlds(session, currentWorldData?.uuid)

                val totalPages =
                        if (filteredWorlds.isEmpty()) 1
                        else (filteredWorlds.size + itemsPerPage - 1) / itemsPerPage
                val safePage = currentPage.coerceIn(0, totalPages - 1)
                session.currentPage = safePage

                val lang = plugin.languageManager
                val titleKey = "gui.admin.title"
                if (!lang.hasKey(player, titleKey)) {
                        player.sendMessage(
                                "§c[MyWorldManager] Error: Missing translation key: $titleKey"
                        )
                        return
                }
                val title = lang.getComponent(player, titleKey).color(net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD)

                if (!suppressSound) {
                        me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(
                                plugin,
                                player,
                                "admin_world",
                                title,
                                null
                        )
                }
                val inventory = Bukkit.createInventory(null, 54, title)

                // 1行目を黒の板ガラスで敷き詰める
                val blackPane = createBlackPaneItem()
                for (i in 0..8) {
                        inventory.setItem(i, blackPane)
                }

                if (currentWorldData != null) {
                        inventory.setItem(4, createCurrentWorldInfoItem(player, currentWorldData))
                }

                // ワールドアイテムの配置 (スロット9から44まで)
                val startIndex = safePage * itemsPerPage
                val pageWorlds = filteredWorlds.drop(startIndex).take(itemsPerPage)

                pageWorlds.forEachIndexed { index, worldData ->
                        inventory.setItem(index + 9, createWorldItem(player, worldData))
                }

                // 6行目のレイアウト:
                // [<前] [フィルター:アーカイブ] [フィルター:公開] [フィルター:プレイヤー] [統計] [■] [ソート] [■] [次>]
                //  45         46                   47                  48               49   50
                // 51   52   53

                // ページ移動ボタン（最終行の右端2つ）
                if (safePage > 0) {
                        inventory.setItem(
                                52,
                                createNavButton(
                                        player,
                                        lang.getMessage("gui.common.prev_page"),
                                        Material.ARROW,
                                        safePage - 1,
                                        safePage + 1,
                                        totalPages,
                                        isNext = false
                                )
                        )
                } else {
                        inventory.setItem(52, blackPane)
                }

                if (safePage < totalPages - 1) {
                        inventory.setItem(
                                53,
                                createNavButton(
                                        player,
                                        lang.getMessage("gui.common.next_page"),
                                        Material.ARROW,
                                        safePage + 1,
                                        safePage + 1,
                                        totalPages,
                                        isNext = true
                                )
                        )
                } else {
                        inventory.setItem(53, blackPane)
                }

                // フィルターボタン
                inventory.setItem(46, createArchiveFilterButton(player, session))
                inventory.setItem(47, createPublishFilterButton(player, session))
                inventory.setItem(48, createPlayerFilterButton(player, session))

                // 統計情報ボタン
                inventory.setItem(
                        49,
                        createInfoButton(filteredWorlds.size, safePage + 1, totalPages)
                )

                // 装飾
                inventory.setItem(50, blackPane)

                // ソートボタン
                inventory.setItem(51, createSortButton(player, session))

                // 装飾
                if (session.fromAdminMenu) {
                        inventory.setItem(
                                45,
                                createNavButton(
                                        player,
                                        lang.getMessage("gui.common.back"),
                                        Material.REDSTONE,
                                        0,
                                        safePage + 1,
                                        totalPages,
                                        isNext = false
                                )
                        )
                        val backItem = inventory.getItem(45)!!
                        ItemTag.tagItem(backItem, ItemTag.TYPE_GUI_RETURN)
                } else {
                        inventory.setItem(45, blackPane)
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
                me.awabi2048.myworldmanager.util.GuiHelper.scheduleGuiTransitionReset(plugin, player)

                // 自動更新タスク
                object : org.bukkit.scheduler.BukkitRunnable() {
                                override fun run() {
                                        if (player.openInventory.topInventory != inventory) {
                                                this.cancel()
                                                return
                                        }

                                        // MSPTソート時は更新しない（順序がちらつくため）
                                        if (session.sortBy == AdminSortType.MSPT_DESC) {
                                                return
                                        }

                                        // セッションから最新情報を再取得して更新
                                        val currentFilteredWorlds =
                                                getFilteredAndSortedWorlds(
                                                        session,
                                                        plugin.worldConfigRepository
                                                                .findByWorldName(player.world.name)
                                                                ?.uuid
                                                )

                                        val currentTotalPages =
                                                if (currentFilteredWorlds.isEmpty()) 1
                                                else
                                                        (currentFilteredWorlds.size + itemsPerPage -
                                                                1) / itemsPerPage

                                        // 現在のページが範囲外にならないように調整（念のため）
                                        val currentSafePage =
                                                safePage.coerceIn(
                                                        0,
                                                        maxOf(0, currentTotalPages - 1)
                                                )

                                        val currentStartIndex = safePage * itemsPerPage
                                        val currentPageWorlds =
                                                currentFilteredWorlds
                                                        .drop(currentStartIndex)
                                                        .take(itemsPerPage)

                                        currentPageWorlds.forEachIndexed { index, worldData ->
                                                inventory.setItem(
                                                        index + 9,
                                                        createWorldItem(player, worldData)
                                                )
                                        }

                                        // アイテム数が減った場合、残りのスロットを背景にする
                                        for (i in currentPageWorlds.size until itemsPerPage) {
                                                inventory.setItem(i + 9, createBackgroundItem())
                                        }

                                        // 統計情報ボタンも更新
                                        inventory.setItem(
                                                49,
                                                createInfoButton(
                                                        currentFilteredWorlds.size,
                                                        safePage + 1,
                                                        currentTotalPages
                                                )
                                        )
                                }
                        }
                        .runTaskTimer(plugin, 20L, 20L)
        }

        /** セッションのフィルター・ソート条件を適用してワールドリストを取得 */
        private fun getFilteredAndSortedWorlds(
                session: AdminGuiSession,
                excludeWorldUuid: java.util.UUID? = null
        ): List<WorldData> {
                var worlds = repository.findAll()

                if (excludeWorldUuid != null) {
                        worlds = worlds.filter { it.uuid != excludeWorldUuid }
                }

                // アーカイブフィルター
                worlds =
                        when (session.archiveFilter) {
                                ArchiveFilter.ALL -> worlds
                                ArchiveFilter.ACTIVE_ONLY -> worlds.filter { !it.isArchived }
                                ArchiveFilter.ARCHIVED_ONLY -> worlds.filter { it.isArchived }
                        }

                // 公開レベルフィルター
                if (session.publishFilter.publishLevel != null) {
                        worlds =
                                worlds.filter {
                                        it.publishLevel == session.publishFilter.publishLevel
                                }
                }

                // プレイヤーフィルター
                val targetPlayer = session.playerFilter

                if (session.playerFilterType != PlayerFilterType.NONE) {
                        if (targetPlayer == null) {
                                // フィルター有効だがプレイヤー未選択の場合は何も表示しない（または全非表示）
                                worlds = emptyList()
                        } else {
                                worlds =
                                        when (session.playerFilterType) {
                                                PlayerFilterType.NONE -> worlds // ここには来ないはず
                                                PlayerFilterType.OWNER ->
                                                        worlds.filter { it.owner == targetPlayer }
                                                PlayerFilterType.MEMBER ->
                                                        worlds.filter {
                                                                it.owner == targetPlayer ||
                                                                        it.moderators.contains(
                                                                                targetPlayer
                                                                        ) ||
                                                                        it.members.contains(
                                                                                targetPlayer
                                                                        )
                                                        }
                                        }
                        }
                }

                // ソート
                worlds =
                        when (session.sortBy) {
                                AdminSortType.CREATED_DESC ->
                                        worlds.sortedByDescending { it.createdAt }
                                AdminSortType.CREATED_ASC -> worlds.sortedBy { it.createdAt }
                                AdminSortType.EXPIRE_ASC -> worlds.sortedBy { it.expireDate }
                                AdminSortType.EXPIRE_DESC ->
                                        worlds.sortedByDescending { it.expireDate }
                                AdminSortType.EXPANSION_DESC ->
                                        worlds.sortedByDescending { it.borderExpansionLevel }
                                AdminSortType.EXPANSION_ASC ->
                                        worlds.sortedBy { it.borderExpansionLevel }
                                AdminSortType.MSPT_DESC ->
                                        worlds.sortedByDescending {
                                                val worldFolderName =
                                                        it.customWorldName ?: "my_world.${it.uuid}"
                                                val world = Bukkit.getWorld(worldFolderName)
                                                val mspt =
                                                        if (world != null) {
                                                                me.awabi2048.myworldmanager.util
                                                                        .ChiyogamiUtil.getWorldMspt(
                                                                        world
                                                                )
                                                        } else if (me.awabi2048.myworldmanager
                                                                        .service
                                                                        .UnloadedWorldRegistry
                                                                        .isUnloaded(worldFolderName)
                                                        ) {
                                                                me.awabi2048.myworldmanager.util
                                                                        .ChiyogamiUtil.getWorldMspt(
                                                                        worldFolderName
                                                                )
                                                        } else {
                                                                null
                                                        }
                                                mspt ?: -1.0
                                        }
                        }

                return worlds
        }

        /** 黒の板ガラスを作成 (1行目・6行目用) */
        private fun createBlackPaneItem(): ItemStack {
                val item = ItemStack(Material.BLACK_STAINED_GLASS_PANE)
                val meta = item.itemMeta ?: return item
                meta.displayName(Component.empty())
                meta.isHideTooltip = true
                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_DECORATION)
                return item
        }

        /** 背景用の灰色の板ガラスを作成 */
        private fun createBackgroundItem(): ItemStack {
                val item = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
                val meta = item.itemMeta ?: return item
                meta.displayName(Component.empty())
                meta.isHideTooltip = true
                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_DECORATION)
                return item
        }

        private fun createCurrentWorldInfoItem(player: Player, worldData: WorldData): ItemStack {
                val lang = plugin.languageManager
                val worldDirectory = worldData.customWorldName ?: "my_world.${worldData.uuid}"
                val worldDirectoryLine =
                        lang.getMessage(
                                player,
                                "gui.admin.world_item.uuid",
                                mapOf("uuid" to worldDirectory)
                        )

                val item = ItemStack(worldData.icon)
                val meta = item.itemMeta ?: return item
                meta.displayName(
                        LegacyComponentSerializer.legacySection()
                                .deserialize(lang.getMessage(player, "gui.admin_menu.current_world.display"))
                                .decoration(TextDecoration.ITALIC, false)
                )
                meta.lore(
                        createAdminWorldLore(
                                player,
                                worldData,
                                worldDirectoryLine,
                                includeWarpAction = false,
                                includeWorldName = true
                        )
                )
                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_ADMIN_CURRENT_WORLD_INFO)
                ItemTag.setWorldUuid(item, worldData.uuid)
                return item
        }

        /** ワールド情報の表示用アイテム */
        private fun createWorldItem(player: Player, data: WorldData): ItemStack {
                val item = ItemStack(data.icon)
                val meta = item.itemMeta ?: return item
                val lang = plugin.languageManager

                meta.displayName(
                        lang.getComponent(
                                player,
                                "gui.common.world_item_name_simple",
                                mapOf("world" to data.name)
                        )
                )

                // Owner Line
                val worldDirectory = data.customWorldName ?: "my_world.${data.uuid}"
                val directoryLine =
                        lang.getMessage(
                                player,
                                "gui.admin.world_item.uuid",
                                mapOf("uuid" to worldDirectory)
                        )
                meta.lore(
                        createAdminWorldLore(
                                player,
                                data,
                                directoryLine,
                                includeWarpAction = true,
                                includeWorldName = false
                        )
                )
                if (data.sourceWorld != "CONVERT") {
                        try {
                                val formatter =
                                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
                                val expireDate = java.time.LocalDate.parse(data.expireDate, formatter)
                                if (java.time.LocalDate.now().isAfter(expireDate)) {
                                        meta.setEnchantmentGlintOverride(true)
                                }
                        } catch (_: Exception) {
                        }
                }

                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_WORLD_ITEM)
                ItemTag.setWorldUuid(item, data.uuid)
                return item
        }

        private fun createAdminWorldLore(
                player: Player,
                data: WorldData,
                firstLine: String,
                includeWarpAction: Boolean,
                includeWorldName: Boolean
        ): List<Component> {
                val lang = plugin.languageManager
                val ownerName =
                        PlayerNameUtil.getNameOrDefault(
                                data.owner,
                                lang.getMessage(player, "general.unknown")
                        )
                val ownerColor =
                        if (Bukkit.getOfflinePlayer(data.owner).isOnline)
                                lang.getMessage(player, "publish_level.color.online")
                        else lang.getMessage(player, "publish_level.color.offline")
                val ownerLine =
                        lang.getMessage(
                                player,
                                "gui.admin.world_item.owner",
                                mapOf("owner" to ownerName, "status_color" to ownerColor)
                        )

                val statusVal =
                        if (data.isArchived)
                                lang.getMessage(player, "gui.admin.world_item.status_archived")
                        else lang.getMessage(player, "gui.admin.world_item.status_active")
                val statusLine =
                        lang.getMessage(
                                player,
                                "gui.admin.world_item.status",
                                mapOf("status" to statusVal)
                        )

                val publishColor =
                        when (data.publishLevel.name) {
                                "PUBLIC" -> lang.getMessage(player, "publish_level.color.public")
                                "FRIEND" -> lang.getMessage(player, "publish_level.color.friend")
                                "PRIVATE" -> lang.getMessage(player, "publish_level.color.private")
                                else -> lang.getMessage(player, "publish_level.color.locked")
                        }
                val publishName =
                        lang.getMessage(player, "publish_level.${data.publishLevel.name.lowercase()}")
                val publishLine =
                        lang.getMessage(
                                player,
                                "gui.admin.world_item.publish",
                                mapOf("color" to publishColor, "level" to publishName)
                        )

                val generationLine =
                        lang.getMessage(
                                player,
                                "gui.admin.world_item.generation",
                                mapOf("method" to getGenerationMethodLabel(player, data.sourceWorld))
                        )

                val expansionLine =
                        if (data.sourceWorld != "CONVERT") {
                                val expansionDisplay =
                                        if (data.borderExpansionLevel == WorldData.EXPANSION_LEVEL_SPECIAL)
                                                "Special"
                                        else data.borderExpansionLevel.toString()
                                lang.getMessage(
                                        player,
                                        "gui.admin.world_item.expansion",
                                        mapOf("level" to expansionDisplay)
                                )
                        } else ""

                var createdLine = ""
                try {
                        val dateTimeFormatter =
                                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        val displayFormatter =
                                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
                        val createdAtDate =
                                java.time.LocalDateTime.parse(data.createdAt, dateTimeFormatter)
                                        .toLocalDate()
                        val createdAtDateStr = createdAtDate.format(displayFormatter)
                        val daysSince =
                                java.time.temporal.ChronoUnit.DAYS.between(
                                        createdAtDate,
                                        java.time.LocalDate.now()
                                )
                        val createdInfoStr =
                                if (daysSince == 0L)
                                        lang.getMessage(
                                                player,
                                                "gui.admin.world_item.created_info_today"
                                        )
                                else
                                        lang.getMessage(
                                                player,
                                                "gui.admin.world_item.created_info_days",
                                                mapOf("days" to daysSince)
                                        )
                        createdLine =
                                lang.getMessage(
                                        player,
                                        "gui.admin.world_item.created_at",
                                        mapOf("date" to createdAtDateStr, "days_ago" to createdInfoStr)
                                )
                } catch (_: Exception) {
                }

                var expireLine = ""
                if (data.sourceWorld != "CONVERT") {
                        try {
                                val formatter =
                                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
                                val expireDate = java.time.LocalDate.parse(data.expireDate, formatter)
                                val daysBetween =
                                        java.time.temporal.ChronoUnit.DAYS.between(
                                                java.time.LocalDate.now(),
                                                expireDate
                                        )
                                val expireInfo =
                                        if (daysBetween >= 0) {
                                                lang.getMessage(
                                                        player,
                                                        "gui.admin.world_item.expire_info_remaining",
                                                        mapOf("days" to daysBetween)
                                                )
                                        } else {
                                                lang.getMessage(
                                                        player,
                                                        "gui.admin.world_item.expire_info_overdue",
                                                        mapOf(
                                                                "days" to
                                                                        java.lang.Math.abs(daysBetween)
                                                        )
                                                )
                                        }
                                expireLine =
                                        lang.getMessage(
                                                player,
                                                "gui.admin.world_item.expires_at",
                                                mapOf("date" to data.expireDate, "days_remain" to expireInfo)
                                        )
                        } catch (_: Exception) {
                                expireLine =
                                        lang.getMessage(
                                                player,
                                                "gui.admin.world_item.expires_at",
                                                mapOf("date" to data.expireDate, "days_remain" to "")
                                        )
                        }
                }

                val msptLine = buildMsptLine(player, data)
                val actionWarp =
                        if (includeWarpAction)
                                lang.getMessage(player, "gui.admin.world_item.action_warp")
                        else ""
                val actionSettings = lang.getMessage(player, "gui.admin.world_item.action_settings")
                val actionArchive = lang.getMessage(player, "gui.admin.world_item.action_archive")
                val uuidCopyHint =
                        if (player.gameMode == org.bukkit.GameMode.CREATIVE)
                                lang.getMessage(player, "gui.admin.world_item.uuid_copy_hint")
                        else ""
                val worldNameLine =
                        if (includeWorldName)
                                lang.getMessage(
                                        player,
                                        "gui.admin.world_item.world_name_line",
                                        mapOf("world" to data.name)
                                )
                        else ""
                val worldSizeLine = buildWorldSizeLine(player, data)
                val separator = lang.getComponent(player, "gui.common.separator")

                return me.awabi2048.myworldmanager.util.GuiHelper.cleanupLore(
                        lang.getComponentList(
                                player,
                                "gui.admin.world_item.lore",
                                mapOf(
                                        "uuid_line" to firstLine,
                                        "world_name_line" to worldNameLine,
                                        "owner_line" to ownerLine,
                                        "status_line" to statusLine,
                                        "publish_line" to publishLine,
                                        "generation_line" to generationLine,
                                        "expansion_line" to expansionLine,
                                        "created_line" to createdLine,
                                        "expire_line" to expireLine,
                                        "mspt_line" to msptLine,
                                        "world_size_line" to worldSizeLine,
                                        "tag_line" to "",
                                        "action_warp" to actionWarp,
                                        "action_settings" to actionSettings,
                                        "action_archive" to actionArchive,
                                        "uuid_copy_hint" to uuidCopyHint
                                )
                        ),
                        separator
                )
        }

        private fun getGenerationMethodLabel(player: Player, sourceWorld: String): String {
                val lang = plugin.languageManager
                return when (sourceWorld.uppercase()) {
                        "CONVERT" -> lang.getMessage(player, "gui.admin.world_item.generation_type.convert")
                        "TEMPLATE" -> lang.getMessage(player, "gui.admin.world_item.generation_type.template")
                        "SEED" -> lang.getMessage(player, "gui.admin.world_item.generation_type.seed")
                        "RANDOM", "DEFAULT" -> lang.getMessage(player, "gui.admin.world_item.generation_type.random")
                        else -> lang.getMessage(player, "gui.admin.world_item.generation_type.unknown", mapOf("source" to sourceWorld))
                }
        }

        private fun buildWorldSizeLine(player: Player, data: WorldData): String {
                val lang = plugin.languageManager
                val worldFolderName = data.customWorldName ?: "my_world.${data.uuid}"
                val now = System.currentTimeMillis()
                val ttlMillis = worldSizeCacheTtlMillis()
                val entry = worldSizeCache[worldFolderName]

                if (entry != null) {
                        val isFresh = now - entry.updatedAtMillis <= ttlMillis
                        if (entry.sizeBytes != null) {
                                if (!isFresh) {
                                        scheduleWorldSizeRefresh(worldFolderName)
                                }
                                return lang.getMessage(
                                        player,
                                        "gui.admin.world_item.world_size_line",
                                        mapOf("size" to formatWorldSize(entry.sizeBytes))
                                )
                        }

                        if (isFresh && entry.failed) {
                                return lang.getMessage(
                                        player,
                                        "gui.admin.world_item.world_size_unavailable"
                                )
                        }
                }

                scheduleWorldSizeRefresh(worldFolderName)
                return lang.getMessage(player, "gui.admin.world_item.world_size_measuring")
        }

        private fun worldSizeCacheTtlMillis(): Long {
                val cacheMinutes = plugin.config.getLong("world_size.cache_minutes", 10L)
                return cacheMinutes.coerceAtLeast(1L) * 60_000L
        }

        private fun scheduleWorldSizeRefresh(worldFolderName: String) {
                if (!worldSizeInFlight.add(worldFolderName)) {
                        return
                }

                Bukkit.getScheduler()
                        .runTaskAsynchronously(
                                plugin,
                                Runnable {
                                        try {
                                                val sizeBytes = calculateOverworldRegionSize(worldFolderName)
                                                worldSizeCache[worldFolderName] =
                                                        WorldSizeCacheEntry(
                                                                sizeBytes = sizeBytes,
                                                                updatedAtMillis =
                                                                        System.currentTimeMillis(),
                                                                failed = false
                                                        )
                                        } catch (_: Exception) {
                                                worldSizeCache[worldFolderName] =
                                                        WorldSizeCacheEntry(
                                                                sizeBytes = null,
                                                                updatedAtMillis =
                                                                        System.currentTimeMillis(),
                                                                failed = true
                                                        )
                                        } finally {
                                                worldSizeInFlight.remove(worldFolderName)
                                        }
                                }
                        )
        }

        private fun calculateOverworldRegionSize(worldFolderName: String): Long {
                val worldFolder = File(Bukkit.getWorldContainer(), worldFolderName)
                if (!worldFolder.exists() || !worldFolder.isDirectory) {
                        throw IllegalStateException("world directory not found: $worldFolderName")
                }

                val targetDirs = arrayOf("region", "entities", "poi")
                var totalBytes = 0L
                for (dirName in targetDirs) {
                        totalBytes += sumMcaFileSize(File(worldFolder, dirName))
                }
                return totalBytes
        }

        private fun sumMcaFileSize(directory: File): Long {
                if (!directory.exists() || !directory.isDirectory) {
                        return 0L
                }

                val mcaFiles =
                        directory.listFiles { file ->
                                file.isFile && file.extension.equals("mca", ignoreCase = true)
                        } ?: return 0L

                var totalBytes = 0L
                for (mcaFile in mcaFiles) {
                        totalBytes += mcaFile.length().coerceAtLeast(0L)
                }
                return totalBytes
        }

        private fun formatWorldSize(bytes: Long): String {
                if (bytes < 1024L) {
                        return "$bytes B"
                }

                val units = arrayOf("KB", "MB", "GB", "TB")
                var value = bytes.toDouble()
                var index = -1

                while (value >= 1024.0 && index < units.lastIndex) {
                        value /= 1024.0
                        index++
                }

                return String.format(Locale.US, "%.1f %s", value, units[index])
        }

        private fun createNavButton(
                player: Player,
                label: String,
                material: Material,
                targetPage: Int,
                currentPage: Int,
                totalPages: Int,
                isNext: Boolean
        ): ItemStack {
                val item = ItemStack(material)
                val meta = item.itemMeta ?: return item
                val lang = plugin.languageManager

                meta.displayName(
                        LegacyComponentSerializer.legacySection()
                                .deserialize(label)
                                .decoration(TextDecoration.ITALIC, false)
                )

                if (material == Material.ARROW) {
                        val lore = mutableListOf<Component>()
                        lore.add(
                                lang.getComponent(
                                        player,
                                        "gui.common.page_info",
                                        mapOf("page" to currentPage, "total_pages" to totalPages)
                                )
                        )
                        lore.add(Component.empty().decoration(TextDecoration.ITALIC, false))
                        lore.add(
                                lang.getComponent(
                                        player,
                                        if (isNext) "gui.common.page_shift_next" else "gui.common.page_shift_prev"
                                )
                        )
                        meta.lore(lore.map { it.decoration(TextDecoration.ITALIC, false) })
                }

                item.itemMeta = meta
                ItemTag.setTargetPage(item, targetPage)
                val type =
                        if (label == lang.getMessage("gui.common.next_page"))
                                ItemTag.TYPE_GUI_NAV_NEXT
                        else ItemTag.TYPE_GUI_NAV_PREV
                ItemTag.tagItem(item, type)
                return item
        }

        private fun buildMsptLine(player: Player, data: WorldData): String {
                val lang = plugin.languageManager
                val worldFolderName = data.customWorldName ?: "my_world.${data.uuid}"
                val world = Bukkit.getWorld(worldFolderName)
                val worldFolder = java.io.File(Bukkit.getWorldContainer(), worldFolderName)
                val isUnloaded = UnloadedWorldRegistry.isUnloaded(worldFolderName)

                if (!me.awabi2048.myworldmanager.util.ChiyogamiUtil.isChiyogamiActive()) {
                        return lang.getMessage(
                                player,
                                "gui.admin.world_item.mspt_error_with_reason",
                                mapOf(
                                        "reason" to
                                                lang.getMessage(
                                                        player,
                                                        "gui.admin.world_item.mspt_reason_chiyogami_inactive"
                                                )
                                )
                        )
                }

                val mspt =
                        if (world != null) {
                                me.awabi2048.myworldmanager.util.ChiyogamiUtil.getWorldMspt(world)
                        } else if (isUnloaded || worldFolder.exists()) {
                                me.awabi2048.myworldmanager.util.ChiyogamiUtil.getWorldMspt(worldFolderName)
                        } else {
                                null
                        }

                val status =
                        when {
                                data.isArchived ->
                                        lang.getMessage(player, "gui.admin.world_item.status_archived")
                                world == null && worldFolder.exists() ->
                                        lang.getMessage(player, "gui.admin.world_item.mspt_status_unloaded")
                                else -> null
                        }

                if (mspt != null) {
                        val msptValue =
                                if (mspt < 0.1) {
                                        lang.getMessage(player, "gui.admin.world_item.mspt_value_low")
                                } else {
                                        "${me.awabi2048.myworldmanager.util.ChiyogamiUtil.getMsptColorCode(mspt)}${String.format("%.1f", mspt)} §7ms"
                                }

                        return if (status != null) {
                                lang.getMessage(
                                        player,
                                        "gui.admin.world_item.mspt_with_status",
                                        mapOf("mspt" to msptValue, "status" to status)
                                )
                        } else {
                                lang.getMessage(
                                        player,
                                        "gui.admin.world_item.mspt",
                                        mapOf("mspt" to msptValue)
                                )
                        }
                }

                val reason =
                        when {
                                world == null && !worldFolder.exists() ->
                                        lang.getMessage(
                                                player,
                                                "gui.admin.world_item.mspt_reason_directory_not_found"
                                        )
                                else ->
                                        lang.getMessage(
                                                player,
                                                "gui.admin.world_item.mspt_reason_metrics_not_found"
                                        )
                        }

                return lang.getMessage(
                        player,
                        "gui.admin.world_item.mspt_error_with_reason",
                        mapOf("reason" to reason)
                )
        }

        private fun createInfoButton(totalCount: Int, current: Int, total: Int): ItemStack {
                val item = ItemStack(Material.PAPER)
                val meta = item.itemMeta ?: return item
                val lang = plugin.languageManager

                meta.displayName(lang.getComponent(null, "gui.admin.info.display"))
                val lore = mutableListOf<Component>()
                lore.add(lang.getComponent(
                        null,
                        "gui.admin.info.total_count",
                        mapOf("count" to totalCount)
                ))
                lore.add(lang.getComponent(
                        null,
                        "gui.admin.info.page",
                        mapOf("page" to current, "total_pages" to total)
                ))
                if (me.awabi2048.myworldmanager.util.ChiyogamiUtil.isChiyogamiActive()) {
                    val mspt = plugin.msptMonitorTask.currentServerMspt
                     lore.add(Component.text(
                         String.format("§7Server MSPT: %s ms", me.awabi2048.myworldmanager.util.ChiyogamiUtil.getMsptColoredString(mspt))
                     ))
                }
                meta.lore(lore.map { it.decoration(TextDecoration.ITALIC, false) })

                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_INFO)
                return item
        }

        /** アーカイブフィルターボタン */
        private fun createArchiveFilterButton(player: Player, session: AdminGuiSession): ItemStack {
                val item = ItemStack(Material.CHEST)
                val meta = item.itemMeta ?: return item
                val lang = plugin.languageManager

                meta.displayName(lang.getComponent(player, "gui.admin.filter.archive.display"))

                val lore = mutableListOf<Component>()
                ArchiveFilter.values().forEach { filter ->
                        val prefix = if (filter == session.archiveFilter) "§a» " else "§8- "
                        lore.add(
                                LegacyComponentSerializer.legacySection()
                                        .deserialize(
                                                "$prefix${lang.getMessage(player, filter.displayKey)}"
                                        )
                                        .decoration(TextDecoration.ITALIC, false)
                        )
                }
                lore.add(Component.empty())
                lore.add(lang.getComponent(player, "gui.admin.filter.click_cycle"))

                meta.lore(lore.map { it.decoration(TextDecoration.ITALIC, false) })
                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_ADMIN_FILTER_ARCHIVE)
                return item
        }

        /** 公開レベルフィルターボタン */
        private fun createPublishFilterButton(player: Player, session: AdminGuiSession): ItemStack {
                val item = ItemStack(Material.ENDER_EYE)
                val meta = item.itemMeta ?: return item
                val lang = plugin.languageManager

                meta.displayName(lang.getComponent(player, "gui.admin.filter.publish.display"))

                val lore = mutableListOf<Component>()
                PublishFilter.values().forEach { filter ->
                        val prefix = if (filter == session.publishFilter) "§a» " else "§8- "
                        lore.add(
                                LegacyComponentSerializer.legacySection()
                                        .deserialize(
                                                "$prefix${lang.getMessage(player, filter.displayKey)}"
                                        )
                                        .decoration(TextDecoration.ITALIC, false)
                        )
                }
                lore.add(Component.empty())
                lore.add(lang.getComponent(player, "gui.admin.filter.click_lr"))

                meta.lore(lore.map { it.decoration(TextDecoration.ITALIC, false) })
                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_ADMIN_FILTER_PUBLISH)
                return item
        }

        /** プレイヤーフィルターボタン */
        private fun createPlayerFilterButton(player: Player, session: AdminGuiSession): ItemStack {
                val item = ItemStack(Material.PLAYER_HEAD)
                val meta = item.itemMeta ?: return item
                val lang = plugin.languageManager

                meta.displayName(lang.getComponent(player, "gui.admin.filter.player.display"))

                val lore = mutableListOf<Component>()

                // 現在の設定
                val filterTypeName = lang.getMessage(player, session.playerFilterType.displayKey)
                lore.add(
                        lang.getComponent(
                                player,
                                "gui.admin.filter.player.current_type",
                                mapOf("type" to filterTypeName)
                        )
                )

                if (session.playerFilter != null) {
                        val targetName = PlayerNameUtil.getNameOrDefault(session.playerFilter!!, "Unknown")

                        lore.add(
                                lang.getComponent(
                                        player,
                                        "gui.admin.filter.player.current_player",
                                        mapOf("player" to targetName)
                                )
                        )
                }

                lore.add(Component.empty())
                lore.add(lang.getComponent(player, "gui.admin.filter.player.click_left"))
                if (session.playerFilterType != PlayerFilterType.NONE) {
                        lore.add(lang.getComponent(player, "gui.admin.filter.player.click_right"))
                }

                meta.lore(lore.map { it.decoration(TextDecoration.ITALIC, false) })
                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_ADMIN_FILTER_PLAYER)
                return item
        }

        /** ソートボタン */
        private fun createSortButton(player: Player, session: AdminGuiSession): ItemStack {
                val item = ItemStack(Material.HOPPER)
                val meta = item.itemMeta ?: return item
                val lang = plugin.languageManager

                meta.displayName(lang.getComponent(player, "gui.admin.sort.display"))

                val lore = mutableListOf<Component>()
                var sortTypes = AdminSortType.values()
                if (!me.awabi2048.myworldmanager.util.ChiyogamiUtil.isChiyogamiActive()) {
                        sortTypes =
                                sortTypes.filter { it != AdminSortType.MSPT_DESC }.toTypedArray()
                }

                sortTypes.forEach { sortType ->
                        val prefix = if (sortType == session.sortBy) "§a» " else "§8- "
                        lore.add(
                                LegacyComponentSerializer.legacySection()
                                        .deserialize(
                                                "$prefix${lang.getMessage(player, sortType.displayKey)}"
                                        )
                                        .decoration(TextDecoration.ITALIC, false)
                        )
                }
                lore.add(Component.empty())
                lore.add(lang.getComponent(player, "gui.admin.filter.click_lr"))

                meta.lore(lore.map { it.decoration(TextDecoration.ITALIC, false) })
                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_ADMIN_SORT)
                return item
        }
}
