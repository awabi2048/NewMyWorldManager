package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiCycle
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryAction
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryData
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryOption
import com.awabi2048.ccsystem.api.gui.GuiMenuEntrySpec
import com.awabi2048.ccsystem.api.gui.GuiMenuDisplaySpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiValueTone
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuActionContext
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuAcceptedClicks
import com.awabi2048.ccsystem.api.gui.MenuCloseContext
import com.awabi2048.ccsystem.api.gui.MenuCloseHandler
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuInteraction
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.service.UnloadedWorldRegistry
import me.awabi2048.myworldmanager.session.*
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.GuiItemFactory
import me.awabi2048.myworldmanager.util.StructuredLore
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
import org.bukkit.scheduler.BukkitTask

/** 管理者用ワールド一覧GUI（ページネーション対応） フィルター、ソート、プレイヤーフィルター機能付き */
class WorldGui(private val plugin: MyWorldManager) {

        private val repository = plugin.worldConfigRepository
        private val runtime = CCSystem.getAPI().getMenuRuntimeService()
        private val worldSizeCache = ConcurrentHashMap<String, WorldSizeCacheEntry>()
        private val worldSizeInFlight = ConcurrentHashMap.newKeySet<String>()
        private val refreshTasks = ConcurrentHashMap<java.util.UUID, BukkitTask>()

        init {
                runtime.register(
                        InventoryMenuDefinition(
                                owner = OWNER,
                                id = ROUTE_ID,
                                renderer = { context -> render(context.player, context.route) },
                                actions = mapOf(
                                        ACTION_PAGE to MenuActionHandler(::page),
                                        ACTION_ARCHIVE_FILTER to MenuActionHandler(::archiveFilter),
                                        ACTION_PUBLISH_FILTER to MenuActionHandler(::publishFilter),
                                        ACTION_PLAYER_FILTER to MenuActionHandler(::playerFilter),
                                        ACTION_SORT to MenuActionHandler(::sort),
                                        ACTION_CURRENT_WORLD to MenuActionHandler(::currentWorld),
                                        ACTION_WORLD to MenuActionHandler(::world),
                                        ACTION_BACK to MenuActionHandler(::back),
                                ),
                                onClose = MenuCloseHandler(::closed),
                        ),
                )
        }

        private data class WorldSizeCacheEntry(
                val sizeBytes: Long?,
                val updatedAtMillis: Long,
                val failed: Boolean
        )

        companion object {
                private const val OWNER = "myworldmanager"
                private const val ROUTE_ID = "admin-world-list"
                private const val PAGE = "page"
                private const val WORLD_UUID = "worldUuid"
                private const val ACTION_PAGE = "page"
                private const val ACTION_ARCHIVE_FILTER = "archiveFilter"
                private const val ACTION_PUBLISH_FILTER = "publishFilter"
                private const val ACTION_PLAYER_FILTER = "playerFilter"
                private const val ACTION_SORT = "sort"
                private const val ACTION_CURRENT_WORLD = "currentWorld"
                private const val ACTION_WORLD = "world"
                private const val ACTION_BACK = "back"
        }

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
                runtime.navigate(player, prepareOpen(player, page, fromAdminMenu))
        }

        fun prepareOpen(
                player: Player,
                page: Int? = null,
                fromAdminMenu: Boolean? = null,
        ): MenuRoute {
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
                startAutoRefresh(player)
                return route(currentPage)
        }

        private fun render(player: Player, route: MenuRoute): InventoryMenuView {
                val session = plugin.adminGuiSessionManager.getSession(player.uniqueId)
                val currentPage = route.payload[PAGE]?.toIntOrNull() ?: session.currentPage
                repository.loadAll()
                val currentWorldData = plugin.worldConfigRepository.findByWorldName(player.world.name)

                // フィルターとソートを適用してワールドリストを取得（現在地ワールドは一覧から除外）
                val filteredWorlds = getFilteredAndSortedWorlds(session, currentWorldData?.uuid)
                val layout = GuiHelper.pagedListLayout()
                val itemsPerPage = layout.itemSlots.size

                val totalPages =
                        if (filteredWorlds.isEmpty()) 1
                        else (filteredWorlds.size + itemsPerPage - 1) / itemsPerPage
                val safePage = currentPage.coerceIn(0, totalPages - 1)
                session.currentPage = safePage

                val lang = plugin.languageManager
                val titleKey = "gui.admin.title"
                val title = GuiHelper.inventoryTitle(lang.getComponent(player, titleKey))

                val inventory = MenuViewBuilder(layout.size, title)

                // 1行目を黒の板ガラスで敷き詰める
                for (i in 0..8) {
                        inventory.setEntry(decorationEntry(i, Material.BLACK_STAINED_GLASS_PANE))
                }

                if (currentWorldData != null) {
                        inventory.setEntry(createAdminWorldEntry(
                                player, currentWorldData, 4,
                                lang.getMessage(player, "gui.admin_menu.current_world.display"),
                                ACTION_CURRENT_WORLD,
                                includeWarpAction = false,
                                includeWorldName = true,
                        ))
                }

                // ワールドアイテムの配置 (スロット9から44まで)
                val startIndex = safePage * itemsPerPage
                val pageWorlds = filteredWorlds.drop(startIndex).take(itemsPerPage)

                pageWorlds.forEachIndexed { index, worldData ->
                        inventory.setEntry(createAdminWorldEntry(
                                player, worldData, layout.itemSlots[index],
                                lang.getMessage(player, "gui.common.world_item_name_simple", mapOf("world" to worldData.name)),
                                ACTION_WORLD,
                                includeWarpAction = true,
                                includeWorldName = false,
                        ))
                }

                // 6行目のレイアウト:
                // [<前] [フィルター:アーカイブ] [フィルター:公開] [フィルター:プレイヤー] [統計] [■] [ソート] [■] [次>]
                //  45         46                   47                  48               49   50
                // 51   52   53

                // ページ移動ボタン（最終行の右端2つ）
                if (safePage > 0) {
                        inventory.setEntry(createPageEntry(player, 52, safePage + 1, totalPages, false, safePage - 1))
                } else {
                        inventory.setEntry(decorationEntry(52, Material.BLACK_STAINED_GLASS_PANE))
                }

                if (safePage < totalPages - 1) {
                        inventory.setEntry(createPageEntry(player, 53, safePage + 1, totalPages, true, safePage + 1))
                } else {
                        inventory.setEntry(decorationEntry(53, Material.BLACK_STAINED_GLASS_PANE))
                }

                // フィルターボタン
                inventory.setEntry(createArchiveFilterButton(player, session, 46))
                inventory.setEntry(createPublishFilterButton(player, session, 47))
                inventory.setEntry(createPlayerFilterButton(player, session, 48))

                // 統計情報ボタン
                inventory.setEntry(createInfoEntry(player, layout.infoSlot, filteredWorlds.size, safePage + 1, totalPages))

                // 装飾
                inventory.setEntry(decorationEntry(50, Material.BLACK_STAINED_GLASS_PANE))

                // ソートボタン
                inventory.setEntry(createSortButton(player, session, 51))

                // 装飾
                if (GuiHelper.canGoBack(player)) {
                        inventory.setEntry(createBackEntry(player, layout.backSlot))
                } else {
                        inventory.setEntry(decorationEntry(layout.backSlot, Material.BLACK_STAINED_GLASS_PANE))
                }

                // 余ったスロットは灰色の板ガラスで埋める (背景)
                for (slot in 0 until inventory.size) {
                        if (!inventory.hasElement(slot)) {
                                inventory.setEntry(decorationEntry(slot, Material.GRAY_STAINED_GLASS_PANE))
                        }
                }

                return inventory.build()
        }

        private fun page(context: MenuActionContext): MenuActionResult {
                val selected = context.payload[PAGE]?.toIntOrNull() ?: return MenuActionResult.Rejected()
                val current = context.route.payload[PAGE]?.toIntOrNull() ?: 0
                val direction = if (selected >= current) 1 else -1
                val target = if (context.click.isShiftClick) {
                        (current + direction * 5).coerceAtLeast(0)
                } else {
                        selected
                }
                return MenuActionResult.Success(MenuUpdate.Replace(route(target)))
        }

        private fun archiveFilter(context: MenuActionContext): MenuActionResult {
                val direction = GuiCycle.direction(context.click) ?: return MenuActionResult.Ignored
                plugin.adminGuiSessionManager.cycleArchiveFilter(context.player.uniqueId, direction)
                return MenuActionResult.Success(MenuUpdate.Replace(route(0)))
        }

        private fun publishFilter(context: MenuActionContext): MenuActionResult {
                val direction = GuiCycle.direction(context.click) ?: return MenuActionResult.Ignored
                plugin.adminGuiSessionManager.cyclePublishFilter(context.player.uniqueId, direction)
                return MenuActionResult.Success(MenuUpdate.Replace(route(0)))
        }

        private fun playerFilter(context: MenuActionContext): MenuActionResult {
                val session = plugin.adminGuiSessionManager.getSession(context.player.uniqueId)
                if (context.click.isLeftClick) {
                        val direction = GuiCycle.direction(context.click) ?: return MenuActionResult.Ignored
                        plugin.adminGuiSessionManager.cyclePlayerFilterType(
                                context.player.uniqueId,
                                direction,
                        )
                        return MenuActionResult.Success(MenuUpdate.Replace(route(0)))
                }
                if (context.click.isRightClick && session.playerFilterType != PlayerFilterType.NONE) {
                        plugin.settingsSessionManager.startSession(
                                context.player,
                                java.util.UUID(0, 0),
                                SettingsAction.ADMIN_PLAYER_FILTER,
                        )
                        plugin.settingsSessionManager.getSession(context.player)
                                ?.beginExternalInput(MenuExternalInput.ADMIN_PLAYER_FILTER)
                        plugin.adminGuiListener.openAdminPlayerFilterInput(
                                plugin,
                                context.player,
                        )
                        return MenuActionResult.Success(MenuUpdate.None)
                }
                return MenuActionResult.Ignored
        }

        private fun sort(context: MenuActionContext): MenuActionResult {
                val direction = GuiCycle.direction(context.click) ?: return MenuActionResult.Ignored
                plugin.adminGuiSessionManager.cycleSortType(context.player.uniqueId, direction)
                return MenuActionResult.Success(MenuUpdate.Replace(route(0)))
        }

        private fun currentWorld(context: MenuActionContext): MenuActionResult =
                worldAction(context, current = true)

        private fun world(context: MenuActionContext): MenuActionResult =
                worldAction(context, current = false)

        private fun worldAction(context: MenuActionContext, current: Boolean): MenuActionResult {
                val uuid = context.payload[WORLD_UUID]
                        ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                        ?: return MenuActionResult.Rejected()
                val worldData = plugin.worldConfigRepository.findByUuid(uuid)
                        ?: return MenuActionResult.Rejected()
                return when {
                        context.click == org.bukkit.event.inventory.ClickType.MIDDLE -> {
                                plugin.adminGuiListener.sendWorldDirectoryCopyMessage(
                                        context.player,
                                        worldData,
                                )
                                MenuActionResult.Success(MenuUpdate.None)
                        }
                        current && !context.click.isRightClick -> MenuActionResult.Ignored
                        context.click.isShiftClick && context.click.isRightClick -> {
                                Bukkit.getScheduler().runTask(
                                        plugin,
                                        Runnable {
                                                if (worldData.isArchived) {
                                                        plugin.adminCommandGui.openUnarchiveWorldConfirmation(
                                                                context.player,
                                                                worldData.name,
                                                                uuid,
                                                        )
                                                } else {
                                                        plugin.adminCommandGui.openArchiveWorldConfirmation(
                                                                context.player,
                                                                worldData.name,
                                                                uuid,
                                                        )
                                                }
                                        },
                                )
                                MenuActionResult.Success(MenuUpdate.Close)
                        }
                        context.click.isRightClick -> openWorldSettings(context.player, worldData)
                        context.click.isLeftClick && worldData.isArchived -> {
                                context.player.sendMessage(
                                        plugin.languageManager.getMessage(
                                                context.player,
                                                "messages.admin_warp_archived_error",
                                        ),
                                )
                                MenuActionResult.Rejected()
                        }
                        context.click.isLeftClick -> warp(context.player, worldData)
                        else -> MenuActionResult.Ignored
                }
        }

        private fun openWorldSettings(player: Player, worldData: WorldData): MenuActionResult {
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.VIEW_SETTINGS,
                        isGui = true,
                        isAdminFlow = true,
                )
                Bukkit.getScheduler().runTask(
                        plugin,
                        Runnable {
                                val folderName = worldData.customWorldName ?: "my_world.${worldData.uuid}"
                                if (!worldData.isArchived && Bukkit.getWorld(folderName) == null) {
                                        player.sendMessage(
                                                plugin.languageManager.getMessage(player, "messages.world_loading"),
                                        )
                                        if (!plugin.worldService.loadWorld(worldData.uuid)) {
                                                player.sendMessage(
                                                        plugin.languageManager.getMessage(player, "error.load_failed"),
                                                )
                                                return@Runnable
                                        }
                                }
                                plugin.worldSettingsGui.open(player, worldData, showBackButton = true)
                        },
                )
                return MenuActionResult.Success(MenuUpdate.Close)
        }

        private fun warp(player: Player, worldData: WorldData): MenuActionResult {
                val folderName = worldData.customWorldName ?: "my_world.${worldData.uuid}"
                if (Bukkit.getWorld(folderName) == null) {
                        player.sendMessage(
                                plugin.languageManager.getMessage(player, "messages.world_loading"),
                        )
                }
                plugin.worldService.teleportToWorld(player, worldData.uuid, runMacro = false) {
                        player.sendMessage(
                                plugin.languageManager.getMessage(
                                        player,
                                        "messages.admin_warp_success",
                                        mapOf("world" to worldData.name),
                                ),
                        )
                }
                return MenuActionResult.Success(MenuUpdate.Close)
        }

        private fun startAutoRefresh(player: Player) {
                refreshTasks.remove(player.uniqueId)?.cancel()
                refreshTasks[player.uniqueId] = Bukkit.getScheduler().runTaskTimer(
                        plugin,
                        Runnable {
                                if (
                                        plugin.adminGuiSessionManager
                                                .getSession(player.uniqueId)
                                                .sortBy != AdminSortType.MSPT_DESC
                                ) {
                                        runtime.refresh(player)
                                }
                        },
                        20L,
                        20L,
                )
        }

        private fun closed(context: MenuCloseContext) {
                refreshTasks.remove(context.player.uniqueId)?.cancel()
        }

        private fun route(page: Int): MenuRoute =
                MenuRoute(OWNER, ROUTE_ID, mapOf(PAGE to page.toString()))

        private inner class MenuViewBuilder(
                val size: Int,
                private val title: Component,
        ) {
                private val elements = mutableMapOf<Int, MenuElement>()

                fun setEntry(element: MenuElement) {
                        elements[element.slot] = element
                }

                fun hasElement(slot: Int): Boolean = slot in elements

                fun build(): InventoryMenuView =
                        InventoryMenuView(
                                size,
                                title,
                                elements.values.sortedBy(MenuElement::slot),
                                standardFrame = false,
                        )
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
                                AdminSortType.WORLD_SIZE_DESC ->
                                        worlds.sortedByDescending { getWorldSizeForSort(it) }
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
        private fun decorationEntry(slot: Int, material: Material): MenuElement =
                CCSystem.getAPI().getGuiElementService().menuDisplay(
                        GuiMenuDisplaySpec(
                                slot,
                                GuiItemSpec(
                                        material,
                                        GuiNameSpec.Empty,
                                        GuiLoreSpec.None,
                                        GuiElementRole.DECORATION,
                                        1,
                                ),
                        ),
                )

        private fun createBackEntry(player: Player, slot: Int): MenuElement =
                CCSystem.getAPI().getGuiElementService().menuEntry(
                        player,
                        GuiMenuEntrySpec(
                                slot = slot,
                                material = Material.REDSTONE,
                                name = GuiNameSpec.Component(plugin.languageManager.getComponent(player, "gui.common.back")),
                                role = GuiElementRole.BACK,
                                actions = listOf(
                                        GuiMenuEntryAction(
                                                ACTION_BACK,
                                                MenuAcceptedClicks.LEFT_RIGHT,
                                                plugin.languageManager.getMessage(player, "gui.common.back"),
                                        ),
                                ),
                        ),
                )

        private fun back(context: MenuActionContext): MenuActionResult {
                return MenuActionResult.Success(MenuUpdate.Back)
        }

        private fun createInfoEntry(
                player: Player,
                slot: Int,
                totalCount: Int,
                current: Int,
                total: Int,
        ): MenuElement {
                val lang = plugin.languageManager
                val lore = buildList {
                        add(GuiLoreLine.Data(lang.getMessage(player, "gui.admin.info.total_count_label"), totalCount, "§b"))
                        add(GuiLoreLine.Data(lang.getMessage(player, "gui.admin.info.page_label"), "$current/$total", "§a"))
                        if (me.awabi2048.myworldmanager.util.ChiyogamiUtil.isChiyogamiActive()) {
                                val mspt = plugin.msptMonitorTask.currentServerMspt
                                add(
                                        GuiLoreLine.Data(
                                                lang.getMessage(player, "gui.admin.info.mspt_label"),
                                                "${me.awabi2048.myworldmanager.util.ChiyogamiUtil.getMsptColoredString(mspt)} ms",
                                                "",
                                        ),
                                )
                        }
                }
                return CCSystem.getAPI().getGuiElementService().menuDisplay(
                        GuiMenuDisplaySpec(
                                slot,
                                GuiItemSpec(
                                        Material.PAPER,
                                        GuiNameSpec.Component(lang.getComponent(player, "gui.admin.info.display")),
                                        GuiLoreSpec.Blocks(listOf(GuiLoreBlock(lore))),
                                        GuiElementRole.CONTENT,
                                        1,
                                ),
                        ),
                )
        }

        private fun createBlackPaneItem(): ItemStack {
                return GuiItemFactory.decoration(Material.BLACK_STAINED_GLASS_PANE)
        }

        /** 背景用の灰色の板ガラスを作成 */
        private fun createBackgroundItem(): ItemStack {
                return GuiItemFactory.decoration(Material.GRAY_STAINED_GLASS_PANE)
        }

        private fun createAdminWorldEntry(
                player: Player,
                data: WorldData,
                slot: Int,
                name: String,
                actionId: String,
                includeWarpAction: Boolean,
                includeWorldName: Boolean
        ): MenuElement {
                val lang = plugin.languageManager
                val worldDirectory = data.customWorldName ?: "my_world.${data.uuid}"
                val ownerName =
                        PlayerNameUtil.getNameOrDefault(
                                data.owner,
                                lang.getMessage(player, "general.unknown")
                        )
                val statusVal =
                        if (data.isArchived)
                                lang.getMessage(player, "gui.admin.world_item.status_archived")
                        else lang.getMessage(player, "gui.admin.world_item.status_active")
                val publishColor =
                        when (data.publishLevel.name) {
                                "PUBLIC" -> lang.getMessage(player, "publish_level.color.public")
                                "FRIEND" -> lang.getMessage(player, "publish_level.color.friend")
                                "PRIVATE" -> lang.getMessage(player, "publish_level.color.private")
                                else -> lang.getMessage(player, "publish_level.color.locked")
                        }
                val publishName =
                        lang.getMessage(player, "publish_level.${data.publishLevel.name.lowercase()}")
                val generationMethod = getGenerationMethodLabel(player, data.sourceWorld)

                var createdValue: String? = null
                try {
                        val dateTimeFormatter =
                                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        val createdAtDate =
                                java.time.LocalDateTime.parse(data.createdAt, dateTimeFormatter)
                                        .toLocalDate()
                        val createdAtDateStr = formatDateForPlayer(player, createdAtDate)
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
                        createdValue = lang.getMessage(
                                player,
                                "gui.admin.world_item.created_value",
                                mapOf("date" to createdAtDateStr, "days_ago" to createdInfoStr)
                        )
                } catch (_: Exception) {
                }

                var expireValue: String? = null
                var expireLabelKey = "gui.admin.world_item.expires_at"
                if (data.sourceWorld != "CONVERT") {
                        try {
                                val formatter =
                                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
                                val expireDate = java.time.LocalDate.parse(data.expireDate, formatter)
                                val today = java.time.LocalDate.now()
                                val localizedExpireDate = formatDateForPlayer(player, expireDate)
                                if (data.isArchived) {
                                        val archiveDate = parseArchiveDate(data.archivedAt) ?: expireDate
                                        val archiveMode = resolveArchiveMode(player, data, expireDate)
                                        expireLabelKey = "gui.admin.world_item.archived_at"
                                        expireValue = lang.getMessage(
                                                player,
                                                "gui.admin.world_item.archived_value",
                                                mapOf(
                                                        "date" to formatDateForPlayer(player, archiveDate),
                                                        "mode" to archiveMode
                                                )
                                        )
                                } else {
                                        val daysBetween =
                                                java.time.temporal.ChronoUnit.DAYS.between(
                                                        today,
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
                                                                                kotlin.math.abs(daysBetween)
                                                                )
                                                        )
                                                }
                                        expireValue = lang.getMessage(
                                                player,
                                                "gui.admin.world_item.expires_value",
                                                mapOf("date" to localizedExpireDate, "days_remain" to expireInfo)
                                        )
                                }
                        } catch (_: Exception) {
                                if (data.isArchived) {
                                        val archiveMode = resolveArchiveMode(player, data, null)
                                        expireLabelKey = "gui.admin.world_item.archived_at"
                                        expireValue = lang.getMessage(
                                                player,
                                                "gui.admin.world_item.archived_value",
                                                mapOf("date" to (data.archivedAt ?: data.expireDate), "mode" to archiveMode)
                                        )
                                } else {
                                        expireValue = data.expireDate
                                }
                        }
                }

                val msptValue = buildMsptValue(player, data)
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
                val worldSizeValue = buildWorldSizeValue(player, data)

                val payload = mapOf(WORLD_UUID to data.uuid.toString())
                val glint = if (data.sourceWorld == "CONVERT") {
                        false
                } else {
                        runCatching {
                                val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
                                java.time.LocalDate.now().isAfter(java.time.LocalDate.parse(data.expireDate, formatter))
                        }.getOrDefault(false)
                }
                return CCSystem.getAPI().getGuiElementService().menuEntry(
                        player,
                        GuiMenuEntrySpec(
                                slot = slot,
                                material = data.icon,
                                name = GuiNameSpec.Text(name, com.awabi2048.ccsystem.api.gui.GuiNameStyle.DEFAULT),
                                role = GuiElementRole.ACTION,
                                data = buildList {
                                        add(GuiMenuEntryData(lang.getMessage(player, "gui.admin.world_item.uuid"), worldDirectory))
                                        if (includeWorldName) add(GuiMenuEntryData(lang.getMessage(player, "gui.admin.world_item.world_name_line"), data.name, GuiValueTone.SUCCESS))
                                        add(GuiMenuEntryData(lang.getMessage(player, "gui.admin.world_item.owner"), ownerName))
                                        add(GuiMenuEntryData(lang.getMessage(player, "gui.admin.world_item.status"), statusVal, if (data.isArchived) GuiValueTone.DANGER else GuiValueTone.INFO))
                                        add(GuiMenuEntryData(lang.getMessage(player, "gui.admin.world_item.publish"), publishName, toneFor(publishColor)))
                                        add(GuiMenuEntryData(lang.getMessage(player, "gui.admin.world_item.generation"), generationMethod, GuiValueTone.PRIMARY))
                                        if (createdValue != null) add(GuiMenuEntryData(lang.getMessage(player, "gui.admin.world_item.created_at"), createdValue, GuiValueTone.PRIMARY))
                                        if (expireValue != null) add(GuiMenuEntryData(lang.getMessage(player, expireLabelKey), expireValue, GuiValueTone.PRIMARY))
                                        add(GuiMenuEntryData(lang.getMessage(player, "gui.admin.world_item.mspt"), msptValue.value, toneFor(msptValue.color)))
                                        add(GuiMenuEntryData(lang.getMessage(player, "gui.admin.world_item.world_size_line"), worldSizeValue.value, toneFor(worldSizeValue.color)))
                                },
                                actions = buildList {
                                        if (actionWarp.isNotBlank()) add(GuiMenuEntryAction(actionId, MenuAcceptedClicks.PLAIN_LEFT, actionWarp, payload))
                                        add(GuiMenuEntryAction(actionId, MenuAcceptedClicks.PLAIN_RIGHT, actionSettings, payload))
                                        add(GuiMenuEntryAction(actionId, MenuAcceptedClicks.SHIFT_RIGHT, actionArchive, payload))
                                        if (uuidCopyHint.isNotBlank()) add(GuiMenuEntryAction(actionId, MenuAcceptedClicks.MIDDLE, uuidCopyHint, payload))
                                },
                                glint = glint,
                        ),
                )
        }

        private fun toneFor(colorCode: String): GuiValueTone =
                GuiValueTone.entries.firstOrNull { it.colorCode == colorCode } ?: GuiValueTone.DEFAULT

        private fun getGenerationMethodLabel(player: Player, sourceWorld: String): String {
                val lang = plugin.languageManager
                val normalized = sourceWorld.uppercase()
                return when {
                        normalized == "CONVERT" ->
                                lang.getMessage(player, "gui.admin.world_item.generation_type.convert")
                        normalized.startsWith("TEMPLATE") -> {
                                val templateId = sourceWorld.substringAfter(':', "").trim()
                                if (templateId.isNotEmpty()) {
                                        lang.getMessage(
                                                player,
                                                "gui.admin.world_item.generation_type.template_with_id",
                                                mapOf("template_id" to templateId)
                                        )
                                } else {
                                        lang.getMessage(
                                                player,
                                                "gui.admin.world_item.generation_type.template"
                                        )
                                }
                        }
                        normalized == "SEED" ->
                                lang.getMessage(player, "gui.admin.world_item.generation_type.seed")
                        normalized == "RANDOM" || normalized == "DEFAULT" ->
                                lang.getMessage(player, "gui.admin.world_item.generation_type.random")
                        else ->
                                lang.getMessage(
                                        player,
                                        "gui.admin.world_item.generation_type.unknown",
                                        mapOf("source" to sourceWorld)
                                )
                }
        }

        private data class DisplayValue(val value: String, val color: String)

        private fun buildWorldSizeValue(player: Player, data: WorldData): DisplayValue {
                val lang = plugin.languageManager
                val cacheKey = worldSizeCacheKey(data)
                val now = System.currentTimeMillis()
                val ttlMillis = worldSizeCacheTtlMillis()
                val entry = worldSizeCache[cacheKey]
                val suffix =
                        if (data.isArchived)
                                lang.getMessage(player, "gui.admin.world_item.world_size_archived_suffix")
                        else ""

                if (entry != null) {
                        val isFresh = now - entry.updatedAtMillis <= ttlMillis
                        if (entry.sizeBytes != null) {
                                if (!isFresh) {
                                        scheduleWorldSizeRefresh(data)
                                }
                                return DisplayValue("${formatWorldSize(entry.sizeBytes)}$suffix", "§e")
                        }

                        if (isFresh && entry.failed) {
                                return DisplayValue(lang.getMessage(player, "gui.admin.world_item.world_size_unavailable"), "§c")
                        }
                }

                scheduleWorldSizeRefresh(data)
                return DisplayValue(lang.getMessage(player, "gui.admin.world_item.world_size_measuring"), "§8")
        }

        private fun worldSizeCacheTtlMillis(): Long {
                val cacheMinutes = plugin.config.getLong("world_size.cache_minutes", 10L)
                return cacheMinutes.coerceAtLeast(1L) * 60_000L
        }

        private fun getWorldSizeForSort(data: WorldData): Long {
                val cacheKey = worldSizeCacheKey(data)
                val entry = worldSizeCache[cacheKey]
                if (entry != null && entry.sizeBytes != null) {
                        if (System.currentTimeMillis() - entry.updatedAtMillis > worldSizeCacheTtlMillis()) {
                                scheduleWorldSizeRefresh(data)
                        }
                        return entry.sizeBytes
                }

                scheduleWorldSizeRefresh(data)
                return -1L
        }

        private fun worldSizeCacheKey(data: WorldData): String {
                val worldFolderName = data.customWorldName ?: "my_world.${data.uuid}"
                return if (data.isArchived) "archived:$worldFolderName" else "active:$worldFolderName"
        }

        private fun scheduleWorldSizeRefresh(data: WorldData) {
                val cacheKey = worldSizeCacheKey(data)
                if (!worldSizeInFlight.add(cacheKey)) {
                        return
                }

                Bukkit.getScheduler()
                        .runTaskAsynchronously(
                                plugin,
                                Runnable {
                                        try {
                                                val sizeBytes = calculateOverworldRegionSize(data)
                                                worldSizeCache[cacheKey] =
                                                        WorldSizeCacheEntry(
                                                                sizeBytes = sizeBytes,
                                                                updatedAtMillis =
                                                                        System.currentTimeMillis(),
                                                                failed = false
                                                        )
                                        } catch (_: Exception) {
                                                worldSizeCache[cacheKey] =
                                                        WorldSizeCacheEntry(
                                                                sizeBytes = null,
                                                                updatedAtMillis =
                                                                        System.currentTimeMillis(),
                                                                failed = true
                                                        )
                                        } finally {
                                                worldSizeInFlight.remove(cacheKey)
                                        }
                                }
                        )
        }

        private fun calculateOverworldRegionSize(data: WorldData): Long {
                val worldFolderName = data.customWorldName ?: "my_world.${data.uuid}"
                val worldFolder = resolveWorldDirectory(data, worldFolderName)
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

        private fun resolveWorldDirectory(data: WorldData, worldFolderName: String): File {
                if (!data.isArchived) {
                        return plugin.worldService.resolveWorldDirectory(worldFolderName)
                }

                val archiveRoot = File(plugin.dataFolder.parentFile.parentFile, "archived_worlds")
                val archivedFolder = File(archiveRoot, worldFolderName)
                if (archivedFolder.exists()) {
                        return archivedFolder
                }
                return plugin.worldService.resolveWorldDirectory(worldFolderName)
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

        private fun dateFormatterFor(player: Player): java.time.format.DateTimeFormatter {
                val language =
                        plugin.playerStatsRepository
                                .findByUuid(player.uniqueId)
                                .language
                                .lowercase(Locale.ROOT)
                return if (language == "ja_jp") {
                        java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日")
                } else {
                        java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
                }
        }

        private fun formatDateForPlayer(player: Player, date: java.time.LocalDate): String {
                return date.format(dateFormatterFor(player))
        }

        private fun parseArchiveDate(raw: String?): java.time.LocalDate? {
                if (raw.isNullOrBlank()) {
                        return null
                }
                return try {
                        java.time.LocalDate.parse(
                                raw,
                                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
                        )
                } catch (_: Exception) {
                        null
                }
        }

        private fun resolveArchiveMode(
                player: Player,
                data: WorldData,
                expireDate: java.time.LocalDate?
        ): String {
                val lang = plugin.languageManager
                val transition = data.archiveTransitionType?.uppercase(Locale.ROOT)
                if (transition == "AUTO") {
                        return lang.getMessage(player, "gui.admin.world_item.archive_mode_auto")
                }
                if (transition == "MANUAL") {
                        return lang.getMessage(player, "gui.admin.world_item.archive_mode_manual")
                }

                if (expireDate != null && expireDate.isBefore(java.time.LocalDate.now())) {
                        return lang.getMessage(player, "gui.admin.world_item.archive_mode_auto")
                }
                return lang.getMessage(player, "gui.admin.world_item.archive_mode_manual")
        }

        private fun createNavButton(
                player: Player,
                label: String,
                material: Material,
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

                item.itemMeta = meta
                return item
        }

        private fun createPageEntry(
                player: Player,
                slot: Int,
                currentPage: Int,
                totalPages: Int,
                isNext: Boolean,
                targetPage: Int,
        ): MenuElement {
                val lang = plugin.languageManager
                return CCSystem.getAPI().getGuiElementService().menuEntry(
                        player,
                        GuiMenuEntrySpec(
                                slot = slot,
                                material = Material.ARROW,
                                name = GuiNameSpec.Text(
                                        lang.getMessage(player, if (isNext) "gui.common.next_page" else "gui.common.prev_page"),
                                        com.awabi2048.ccsystem.api.gui.GuiNameStyle.DEFAULT,
                                ),
                                role = GuiElementRole.NAVIGATION,
                                data = listOf(GuiMenuEntryData(lang.getMessage(player, "gui.common.page_info_label"), "$currentPage/$totalPages", GuiValueTone.SUCCESS)),
                                actions = listOf(GuiMenuEntryAction(
                                        ACTION_PAGE,
                                        MenuAcceptedClicks.LEFT_RIGHT,
                                        lang.getMessage(player, if (isNext) "gui.common.page_shift_next" else "gui.common.page_shift_prev"),
                                        mapOf(PAGE to targetPage.toString()),
                                )),
                        ),
                )
        }

        private fun buildMsptValue(player: Player, data: WorldData): DisplayValue {
                val lang = plugin.languageManager
                val worldFolderName = data.customWorldName ?: "my_world.${data.uuid}"
                val world = Bukkit.getWorld(worldFolderName)
                val worldFolder = plugin.worldService.resolveWorldDirectory(worldFolderName)
                val isUnloaded = UnloadedWorldRegistry.isUnloaded(worldFolderName)

                if (!me.awabi2048.myworldmanager.util.ChiyogamiUtil.isChiyogamiActive()) {
                        return DisplayValue(
                                lang.getMessage(
                                        player,
                                        "gui.admin.world_item.mspt_error_with_reason",
                                        mapOf("reason" to lang.getMessage(player, "gui.admin.world_item.mspt_reason_chiyogami_inactive"))
                                ),
                                "§c"
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
                        val msptDisplay =
                                if (mspt < 0.1) {
                                        DisplayValue(lang.getMessage(player, "gui.admin.world_item.mspt_value_low"), "§a")
                                } else {
                                        DisplayValue(
                                                "${String.format("%.1f", mspt)} ms",
                                                me.awabi2048.myworldmanager.util.ChiyogamiUtil.getMsptColorCode(mspt)
                                        )
                                }

                        return if (status != null) {
                                DisplayValue(
                                        lang.getMessage(player, "gui.admin.world_item.mspt_with_status", mapOf("mspt" to msptDisplay.value, "status" to status)),
                                        msptDisplay.color
                                )
                        } else msptDisplay
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

                return DisplayValue(
                        lang.getMessage(player, "gui.admin.world_item.mspt_error_with_reason", mapOf("reason" to reason)),
                        "§c"
                )
        }

        private fun createInfoButton(totalCount: Int, current: Int, total: Int): ItemStack {
                val item = ItemStack(Material.PAPER)
                val meta = item.itemMeta ?: return item
                val lang = plugin.languageManager

                meta.displayName(lang.getComponent(null, "gui.admin.info.display"))
                val lore = mutableListOf<GuiLoreLine>(
                        GuiLoreLine.Data(lang.getMessage(null, "gui.admin.info.total_count_label"), totalCount, "§b"),
                        GuiLoreLine.Data(lang.getMessage(null, "gui.admin.info.page_label"), "$current/$total", "§a")
                )
                if (me.awabi2048.myworldmanager.util.ChiyogamiUtil.isChiyogamiActive()) {
                    val mspt = plugin.msptMonitorTask.currentServerMspt
                    lore.add(GuiLoreLine.Data(
                            lang.getMessage(null, "gui.admin.info.mspt_label"),
                            "${me.awabi2048.myworldmanager.util.ChiyogamiUtil.getMsptColoredString(mspt)} ms",
                            ""
                    ))
                }
                meta.lore(GuiItemFactory.menuLore(lore))

                item.itemMeta = meta
                return item
        }

        /** アーカイブフィルターボタン */
        private fun createArchiveFilterButton(player: Player, session: AdminGuiSession, slot: Int): MenuElement {
                val lang = plugin.languageManager
                val options = ArchiveFilter.values().map { filter ->
                        filter to lang.getMessage(player, filter.displayKey)
                }
                return CCSystem.getAPI().getGuiElementService().menuEntry(
                        player,
                        GuiMenuEntrySpec(
                                slot, Material.CHEST,
                                GuiNameSpec.Component(lang.getComponent(player, "gui.admin.filter.archive.display")),
                                GuiElementRole.ACTION,
                                data = listOf(GuiMenuEntryData(
                                        lang.getMessage(player, "gui.admin.filter.archive.label"),
                                        options.first { it.first == session.archiveFilter }.second,
                                        GuiValueTone.PRIMARY,
                                )),
                                options = options.map { (filter, name) ->
                                        GuiMenuEntryOption(name, filter == session.archiveFilter)
                                },
                                actions = listOf(GuiMenuEntryAction(
                                        ACTION_ARCHIVE_FILTER,
                                        MenuAcceptedClicks.LEFT_RIGHT,
                                        lang.getMessage(player, "gui.common.action.cycle"),
                                )),
                        ),
                )
        }

        /** 公開レベルフィルターボタン */
        private fun createPublishFilterButton(player: Player, session: AdminGuiSession, slot: Int): MenuElement {
                val lang = plugin.languageManager
                val options = PublishFilter.values().map { filter ->
                        filter to lang.getMessage(player, filter.displayKey)
                }
                return CCSystem.getAPI().getGuiElementService().menuEntry(
                        player,
                        GuiMenuEntrySpec(
                                slot, Material.ENDER_EYE,
                                GuiNameSpec.Component(lang.getComponent(player, "gui.admin.filter.publish.display")),
                                GuiElementRole.ACTION,
                                data = listOf(GuiMenuEntryData(
                                        lang.getMessage(player, "gui.admin.filter.publish.label"),
                                        options.first { it.first == session.publishFilter }.second,
                                        GuiValueTone.PRIMARY,
                                )),
                                options = options.map { (filter, name) ->
                                        GuiMenuEntryOption(name, filter == session.publishFilter)
                                },
                                actions = listOf(GuiMenuEntryAction(
                                        ACTION_PUBLISH_FILTER,
                                        MenuAcceptedClicks.LEFT_RIGHT,
                                        lang.getMessage(player, "gui.common.action.cycle"),
                                )),
                        ),
                )
        }

        /** プレイヤーフィルターボタン */
        private fun createPlayerFilterButton(player: Player, session: AdminGuiSession, slot: Int): MenuElement {
                val lang = plugin.languageManager
                val actions = buildList {
                        add(GuiMenuEntryAction(
                                ACTION_PLAYER_FILTER,
                                MenuAcceptedClicks.LEFT,
                                lang.getMessage(player, "gui.admin.filter.player.click_left"),
                        ))
                        if (session.playerFilterType != PlayerFilterType.NONE) {
                                add(GuiMenuEntryAction(
                                        ACTION_PLAYER_FILTER,
                                        MenuAcceptedClicks.RIGHT,
                                        lang.getMessage(player, "gui.admin.filter.player.click_right"),
                                ))
                        }
                }
                return CCSystem.getAPI().getGuiElementService().menuEntry(
                        player,
                        GuiMenuEntrySpec(
                                slot, Material.PLAYER_HEAD,
                                GuiNameSpec.Component(lang.getComponent(player, "gui.admin.filter.player.display")),
                                GuiElementRole.ACTION,
                                data = buildList {
                                        add(GuiMenuEntryData(
                                                lang.getMessage(player, "gui.admin.filter.player.current_type"),
                                                lang.getMessage(player, session.playerFilterType.displayKey),
                                        ))
                                        session.playerFilter?.let {
                                                add(GuiMenuEntryData(
                                                        lang.getMessage(player, "gui.admin.filter.player.current_player"),
                                                        PlayerNameUtil.getNameOrDefault(it, "Unknown"),
                                                        GuiValueTone.INFO,
                                                ))
                                        }
                                },
                                options = PlayerFilterType.values().map {
                                        GuiMenuEntryOption(
                                                lang.getMessage(player, it.displayKey),
                                                it == session.playerFilterType,
                                        )
                                },
                                actions = actions,
                        ),
                )
        }

        /** ソートボタン */
        private fun createSortButton(player: Player, session: AdminGuiSession, slot: Int): MenuElement {
                val lang = plugin.languageManager

                var sortTypes = AdminSortType.values()
                if (!me.awabi2048.myworldmanager.util.ChiyogamiUtil.isChiyogamiActive()) {
                        sortTypes =
                                sortTypes.filter { it != AdminSortType.MSPT_DESC }.toTypedArray()
                }

                val options = sortTypes.map { sortType ->
                        sortType to lang.getMessage(player, sortType.displayKey)
                }
                return CCSystem.getAPI().getGuiElementService().menuEntry(
                        player,
                        GuiMenuEntrySpec(
                                slot, Material.HOPPER,
                                GuiNameSpec.Component(lang.getComponent(player, "gui.admin.sort.display")),
                                GuiElementRole.ACTION,
                                data = listOf(GuiMenuEntryData(
                                        lang.getMessage(player, "gui.admin.sort.label"),
                                        options.first { it.first == session.sortBy }.second,
                                        GuiValueTone.PRIMARY,
                                )),
                                options = options.map { (type, name) ->
                                        GuiMenuEntryOption(name, type == session.sortBy)
                                },
                                actions = listOf(GuiMenuEntryAction(
                                        ACTION_SORT,
                                        MenuAcceptedClicks.LEFT_RIGHT,
                                        lang.getMessage(player, "gui.common.action.cycle"),
                                )),
                        ),
                )
        }

}
