package me.awabi2048.myworldmanager.ui.bedrock

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiMenuIconData
import com.awabi2048.ccsystem.api.gui.GuiMenuIconOption
import com.awabi2048.ccsystem.api.gui.GuiMenuIconSpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiNameStyle
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.model.TourNavigationMode
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.GuiItemFactory
import me.awabi2048.myworldmanager.util.GuiLoreActions
import me.awabi2048.myworldmanager.util.ItemTag
import me.awabi2048.myworldmanager.util.PermissionManager
import me.awabi2048.myworldmanager.util.WorldCreationChecks
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import me.awabi2048.myworldmanager.util.StructuredLore
import me.awabi2048.myworldmanager.util.WorldRuntimePolicies
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import me.awabi2048.myworldmanager.util.cancelWithDebug
import org.bukkit.inventory.ItemStack
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

class BedrockMenuService(
    private val plugin: MyWorldManager,
    private val routingService: BedrockUiRoutingService,
    private val formBridge: FloodgateFormBridge
) {

    private data class FormAction(
        val label: String,
        val iconMaterial: Material? = null,
        val onClick: () -> Unit
    )

    private val playerWorldPageSize = 28

    private val formPageSize = 8

    private val materialPathOverrides =
        mapOf(
            Material.WRITABLE_BOOK to "textures/items/book_writable",
            Material.COMPASS to "textures/items/compass_item",
            Material.REDSTONE to "textures/items/redstone_dust",
            Material.EXPERIENCE_BOTTLE to "textures/items/experience_bottle",
            Material.ENDER_PEARL to "textures/items/ender_pearl",
            Material.ENDER_EYE to "textures/items/ender_eye"
        )

    fun openPlayerWorld(player: Player, page: Int = 0, showBackButton: Boolean = false) {
        val safePage = page.coerceAtLeast(0)
        openPlayerWorldInventory(player, safePage, showBackButton)
    }

    fun openCurrentWorldMenu(player: Player, worldData: WorldData, showBackButton: Boolean = false) {
        plugin.worldSettingsGui.open(player, worldData, showBackButton)
    }

    fun openSettings(player: Player, showBackButton: Boolean = false, returnPage: Int = 0) {
        openSettingsInventory(player, showBackButton, returnPage)
    }

    fun openDiscovery(player: Player, page: Int = 0, showBackButton: Boolean = false) {
        plugin.discoveryGui.open(player, page, showBackButton)
    }

    fun openFavoriteList(
        player: Player,
        page: Int = 0,
        worldData: WorldData? = null,
        returnToFavoriteMenu: Boolean = false,
        showBackButton: Boolean = false
    ) {
        plugin.favoriteGui.open(player, page, worldData, returnToFavoriteMenu, showBackButton)
    }

    fun openFavoriteMenu(player: Player, worldData: WorldData?) {
        plugin.favoriteMenuGui.open(player, worldData)
    }

    fun openVisitMenu(
        player: Player,
        owner: org.bukkit.OfflinePlayer,
        page: Int = 0,
        worldData: WorldData? = null
    ) {
        plugin.visitGui.open(player, owner, page, worldData)
    }

    fun openMeet(player: Player, showBackButton: Boolean? = null) {
        plugin.meetGui.open(player, showBackButton)
    }

    fun openFavoriteRemoveConfirm(
        player: Player,
        worldData: WorldData,
        onConfirm: () -> Unit,
        onCancel: () -> Unit
    ): Boolean {
        plugin.favoriteConfirmGui.open(player, worldData)
        return true
    }

    fun openSpotlightConfirm(
        player: Player,
        worldData: WorldData,
        onConfirm: () -> Unit,
        onCancel: () -> Unit
    ): Boolean {
        plugin.spotlightConfirmGui.open(player, worldData)
        return true
    }

    fun openSpotlightRemoveConfirm(
        player: Player,
        worldData: WorldData,
        onConfirm: () -> Unit,
        onCancel: () -> Unit
    ): Boolean {
        plugin.spotlightRemoveConfirmGui.open(player, worldData)
        return true
    }

    fun openMemberRequestConfirm(
        player: Player,
        worldData: WorldData,
        onConfirm: () -> Unit,
        onCancel: () -> Unit
    ): Boolean {
        plugin.memberRequestConfirmGui.open(player, worldData)
        return true
    }

    fun openWorldSeedConfirm(
        player: Player,
        currentSlots: Int,
        nextSlots: Int,
        onConfirm: () -> Unit,
        onCancel: () -> Unit
    ): Boolean {
        plugin.worldSeedConfirmGui.open(player, currentSlots, nextSlots)
        return true
    }

    fun handleInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val holder = event.view.topInventory.holder

        when (holder) {
            is BedrockPlayerWorldListHolder -> {
                handlePlayerWorldInventoryClick(player, holder, event)
            }

            is BedrockWorldActionHolder -> {
                handleWorldActionInventoryClick(player, holder, event)
            }

            is BedrockSettingsHolder -> {
                handleSettingsInventoryClick(player, holder, event)
            }

            else -> return
        }
    }

    private fun openPlayerWorldForm(player: Player, requestedPage: Int, showBackButton: Boolean): Boolean {
        val worlds = getAccessibleWorlds(player)
        val totalPages = if (worlds.isEmpty()) 1 else (worlds.size + formPageSize - 1) / formPageSize
        val page = requestedPage.coerceIn(0, totalPages - 1)
        val start = page * formPageSize
        val pageWorlds = worlds.drop(start).take(formPageSize)
        val archivedPrefix = tr(player, "gui.bedrock.player_world.archived_prefix")

        val actions = mutableListOf<FormAction>()
        pageWorlds.forEach { world ->
            val archivedMarker = if (world.isArchived) archivedPrefix else ""
            actions += FormAction("$archivedMarker${world.name}", world.icon) {
                val latestWorld = plugin.worldConfigRepository.findByUuid(world.uuid) ?: return@FormAction
                openWorldActionMenu(player, latestWorld, page, showBackButton)
            }
        }

        if (page > 0) {
            actions += FormAction(tr(player, "gui.bedrock.player_world.button.prev"), Material.ARROW) {
                openPlayerWorld(player, page - 1, showBackButton)
            }
        }
        if (start + pageWorlds.size < worlds.size) {
            actions += FormAction(tr(player, "gui.bedrock.player_world.button.next"), Material.ARROW) {
                openPlayerWorld(player, page + 1, showBackButton)
            }
        }

        actions += FormAction(tr(player, "gui.bedrock.player_world.button.settings"), Material.WRITABLE_BOOK) {
            plugin.menuRouteHistory.pushPlayerWorld(player, page, showBackButton)
            openSettings(player, showBackButton, page)
        }

        val currentManagedWorld = getCurrentManagedWorld(player)
        if (currentManagedWorld != null && canAccessWorldSettings(player, currentManagedWorld)) {
            actions += FormAction(tr(player, "gui.bedrock.player_world.button.current_world"), Material.COMPASS) {
                plugin.menuRouteHistory.pushPlayerWorld(player, page, showBackButton)
                openCurrentWorldMenu(player, currentManagedWorld, showBackButton)
            }
        }

        if (showBackButton) {
            actions += FormAction(tr(player, "gui.bedrock.player_world.button.return"), Material.BARRIER) {
                performConfiguredReturn(player)
            }
        }

        actions += FormAction(tr(player, "gui.bedrock.player_world.button.close"), Material.REDSTONE) {
            player.closeInventory()
        }

        val title = tr(player, "gui.bedrock.player_world.title")
        val content =
            tr(
                player,
                "gui.bedrock.player_world.page_content",
                mapOf("current" to page + 1, "total" to totalPages)
            )
        return sendActionForm(player, title, content, actions)
    }

    private fun openWorldActionsForm(
        player: Player,
        worldData: WorldData,
        returnPage: Int,
        showBackButton: Boolean
    ): Boolean {
        val actions = mutableListOf<FormAction>()

        actions += FormAction(tr(player, "gui.bedrock.world_action.button.warp"), Material.ENDER_PEARL) {
            val latest = plugin.worldConfigRepository.findByUuid(worldData.uuid) ?: return@FormAction
            warpToWorld(player, latest)
        }

        if (canManagePublish(player, worldData)) {
            actions +=
                FormAction(
                    tr(
                        player,
                        "gui.bedrock.world_action.button.cycle_publish",
                        mapOf("level" to publishDisplayText(player, worldData))
                    ),
                    Material.ENDER_EYE
                ) {
                val latest = plugin.worldConfigRepository.findByUuid(worldData.uuid) ?: return@FormAction
                cyclePublishLevel(player, latest)
                openWorldActionMenu(player, latest, returnPage, showBackButton)
            }
        }

        if (canManageArchive(player, worldData)) {
            val label =
                if (worldData.isArchived) {
                    tr(player, "gui.bedrock.world_action.button.unarchive")
                } else {
                    tr(player, "gui.bedrock.world_action.button.archive")
                }
            actions += FormAction(label, Material.CHEST) {
                val latest = plugin.worldConfigRepository.findByUuid(worldData.uuid) ?: return@FormAction
                toggleArchiveState(player, latest) {
                    val refreshed = plugin.worldConfigRepository.findByUuid(worldData.uuid)
                    if (refreshed != null) {
                        openWorldActionMenu(player, refreshed, returnPage, showBackButton)
                    } else {
                        openPlayerWorld(player, returnPage, showBackButton)
                    }
                }
            }
        }

        if (canAccessWorldSettings(player, worldData)) {
            actions += FormAction(tr(player, "gui.bedrock.world_action.button.advanced_settings"), Material.COMPARATOR) {
                val latest = plugin.worldConfigRepository.findByUuid(worldData.uuid) ?: return@FormAction
                pushWorldActionRoute(player, latest, returnPage, showBackButton)
                plugin.worldSettingsGui.open(player, latest, showBackButton)
            }
        }

        actions += FormAction(tr(player, "gui.bedrock.world_action.button.back_to_worlds"), Material.ARROW) {
            openPlayerWorld(player, returnPage, showBackButton)
        }

        actions += FormAction(tr(player, "gui.bedrock.world_action.button.settings"), Material.WRITABLE_BOOK) {
            openSettings(player, showBackButton, returnPage)
        }

        if (showBackButton) {
            actions += FormAction(tr(player, "gui.bedrock.world_action.button.return"), Material.BARRIER) {
                performConfiguredReturn(player)
            }
        }

        actions += FormAction(tr(player, "gui.bedrock.world_action.button.close"), Material.REDSTONE) {
            player.closeInventory()
        }

        val content =
            listOf(
                    tr(
                        player,
                        "gui.bedrock.world_action.content.owner",
                        mapOf("owner" to worldData.owner)
                    ),
                    tr(
                        player,
                        "gui.bedrock.world_action.content.status",
                        mapOf("status" to worldStateText(player, worldData.isArchived))
                    ),
                    tr(
                        player,
                        "gui.bedrock.world_action.content.publish",
                        mapOf("publish" to publishDisplayText(player, worldData))
                    )
                )
                .joinToString("\n")
        return sendActionForm(
            player,
            tr(player, "gui.bedrock.world_action.title", mapOf("world" to worldData.name)),
            content,
            actions
        )
    }

    private fun openSettingsForm(player: Player, showBackButton: Boolean, returnPage: Int): Boolean {
        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        val actions = mutableListOf<FormAction>()

        actions +=
            FormAction(
                tr(
                    player,
                    "gui.bedrock.settings.button.notification",
                    mapOf("status" to statusText(player, stats.visitorNotificationEnabled))
                ),
                Material.BELL
            ) {
            stats.visitorNotificationEnabled = !stats.visitorNotificationEnabled
            plugin.playerStatsRepository.save(stats)
            openSettings(player, showBackButton, returnPage)
        }

        actions +=
            FormAction(
                tr(
                    player,
                    "gui.bedrock.settings.button.language",
                    mapOf("language" to languageDisplay(player, plugin.languageManager.resolveLocale(player)))
                ),
                Material.WRITABLE_BOOK
            ) {
            openSettings(player, showBackButton, returnPage)
        }

        actions += FormAction(tr(player, "gui.bedrock.settings.button.back_to_worlds"), Material.ARROW) {
            openPlayerWorld(player, returnPage, showBackButton)
        }

        if (showBackButton) {
            actions += FormAction(tr(player, "gui.bedrock.settings.button.return"), Material.BARRIER) {
                performConfiguredReturn(player)
            }
        }

        actions += FormAction(tr(player, "gui.bedrock.settings.button.close"), Material.REDSTONE) {
            player.closeInventory()
        }

        return sendActionForm(
            player,
            tr(player, "gui.bedrock.settings.title"),
            tr(player, "gui.bedrock.settings.form_content"),
            actions
        )
    }

    private fun sendActionForm(
        player: Player,
        title: String,
        content: String,
        actions: List<FormAction>
    ): Boolean {
        if (actions.isEmpty()) {
            return false
        }

        val buttons =
            actions.map { action ->
                FloodgateFormBridge.SimpleFormButton(
                    label = action.label,
                    imagePath = materialToBedrockPath(action.iconMaterial)
                )
            }
        return formBridge.sendSimpleFormWithImages(
            player = player,
            title = title,
            content = content,
            buttons = buttons,
            onSelect = { index ->
                val action = actions.getOrNull(index) ?: return@sendSimpleFormWithImages
                action.onClick()
            }
        )
    }

    private fun openPlayerWorldInventory(player: Player, requestedPage: Int, showBackButton: Boolean) {
        val worlds = getAccessibleWorlds(player)
        val pageSize = playerWorldPageSize
        val totalPages = if (worlds.isEmpty()) 1 else (worlds.size + pageSize - 1) / pageSize
        val page = requestedPage.coerceIn(0, totalPages - 1)
        val start = page * pageSize
        val pageWorlds = worlds.drop(start).take(pageSize)

        val neededDataRows = if (pageWorlds.isEmpty()) 1 else (pageWorlds.size + 6) / 7
        val rowCount = (neededDataRows + 2).coerceIn(3, 6)
        val footerStart = (rowCount - 1) * 9
        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        val currentCreateCount = worlds.count { it.owner == player.uniqueId }
        val maxSlot = WorldRuntimePolicies.maxCreateCountDefault(plugin.config) + stats.unlockedWorldSlot
        val bypassLimits = PermissionManager.canBypassWorldLimits(player)

        val holder = BedrockPlayerWorldListHolder(page, showBackButton)
        val inventory =
            Bukkit.createInventory(
                holder,
                rowCount * 9,
                me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(tr(player, "gui.player_world.title"))
            )
        holder.inv = inventory

        val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
        val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)

        for (slot in 0..8) {
            inventory.setItem(slot, blackPane)
        }
        for (row in 0 until neededDataRows) {
            val rowStart = (row + 1) * 9
            inventory.setItem(rowStart, grayPane)
            inventory.setItem(rowStart + 8, grayPane)
            for (col in 1..7) {
                inventory.setItem(rowStart + col, grayPane)
            }
        }
        for (slot in footerStart until footerStart + 9) {
            inventory.setItem(slot, blackPane)
        }

        pageWorlds.forEachIndexed { index, worldData ->
            val row = index / 7
            val col = index % 7
            val slot = (row + 1) * 9 + 1 + col
            inventory.setItem(slot, createWorldListItem(player, worldData))
        }

        if (page > 0) {
            inventory.setItem(
                footerStart + 1,
                createActionItem(Material.ARROW, tr(player, "gui.bedrock.player_world.button.prev"), "open_prev_page")
            )
        }
        if (start + pageWorlds.size < worlds.size) {
            inventory.setItem(
                footerStart + 8,
                createActionItem(Material.ARROW, tr(player, "gui.bedrock.player_world.button.next"), "open_next_page")
            )
        }

        val creationBlockReason = creationBlockReason(player, currentCreateCount, maxSlot, bypassLimits)
        if (creationBlockReason == null) {
            inventory.setItem(footerStart + 2, createCreationButtonItem(player))
        } else {
            inventory.setItem(footerStart + 2, createCreationUnavailableButtonItem(player, creationBlockReason))
        }
        inventory.setItem(footerStart + 4, createStatsButtonItem(player, currentCreateCount, maxSlot, stats.worldPoint))
        inventory.setItem(
            footerStart + 6,
            createActionItem(
                Material.WRITABLE_BOOK,
                tr(player, "gui.user_settings.button.display"),
                "open_settings",
                lore = GuiLoreSpec.Blocks(listOf(com.awabi2048.ccsystem.api.gui.GuiLoreBlock(listOf(
                    GuiLoreActions.singleClick(plugin.languageManager, player, tr(player, "gui.user_settings.button.action"))
                ))))
            )
        )

        if (showBackButton) {
            inventory.setItem(
                footerStart,
                createActionItem(Material.BARRIER, tr(player, "gui.bedrock.player_world.button.return"), "return_command")
            )
        }

        player.openInventory(inventory)
    }

    private fun openWorldActionsInventory(
        player: Player,
        worldData: WorldData,
        returnPage: Int,
        showBackButton: Boolean
    ) {
        val holder = BedrockWorldActionHolder(worldData.uuid, returnPage, showBackButton)
        val inventory =
            Bukkit.createInventory(
                holder,
                27,
                me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(tr(player, "gui.bedrock.world_action.title", mapOf("world" to worldData.name)))
            )
        holder.inv = inventory

        val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
        for (slot in 0 until 27) {
            inventory.setItem(slot, blackPane)
        }

        inventory.setItem(
            10,
            createActionItem(Material.ENDER_PEARL, tr(player, "gui.bedrock.world_action.button.warp"), "warp_world", worldData.uuid)
        )

        if (canManagePublish(player, worldData)) {
            inventory.setItem(
                11,
                createActionItem(
                    Material.ENDER_EYE,
                    tr(
                        player,
                        "gui.bedrock.world_action.button.cycle_publish",
                        mapOf("level" to publishDisplayText(player, worldData))
                    ),
                    "cycle_publish",
                    worldData.uuid
                )
            )
        }

        if (canManageArchive(player, worldData)) {
            val label =
                if (worldData.isArchived) {
                    tr(player, "gui.bedrock.world_action.button.unarchive")
                } else {
                    tr(player, "gui.bedrock.world_action.button.archive")
                }
            inventory.setItem(
                12,
                createActionItem(Material.CHEST, label, "toggle_archive", worldData.uuid)
            )
        }

        if (canAccessWorldSettings(player, worldData)) {
            inventory.setItem(
                14,
                createActionItem(
                    Material.COMPARATOR,
                    tr(player, "gui.bedrock.world_action.button.advanced_settings"),
                    "open_advanced_settings",
                    worldData.uuid
                )
            )
        }

        inventory.setItem(
            15,
            createActionItem(Material.WRITABLE_BOOK, tr(player, "gui.bedrock.world_action.button.settings"), "open_settings")
        )

        inventory.setItem(
            16,
            createActionItem(Material.ARROW, tr(player, "gui.bedrock.world_action.button.back_to_worlds"), "back_to_worlds")
        )

        if (showBackButton) {
            inventory.setItem(
                22,
                createActionItem(Material.BARRIER, tr(player, "gui.bedrock.world_action.button.return"), "return_command")
            )
        } else {
            inventory.setItem(
                22,
                createActionItem(Material.REDSTONE, tr(player, "gui.bedrock.world_action.button.close"), "close_menu")
            )
        }

        player.openInventory(inventory)
    }

    private fun openSettingsInventory(player: Player, showBackButton: Boolean, returnPage: Int) {
        val holder = BedrockSettingsHolder(showBackButton, returnPage)
        val title = me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(tr(player, "gui.user_settings.title"))
        val inventory = Bukkit.createInventory(holder, 27, title)
        holder.inv = inventory

        val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
        val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)

        for (slot in 0..8) {
            inventory.setItem(slot, blackPane)
        }
        for (slot in 9..17) {
            inventory.setItem(slot, grayPane)
        }
        for (slot in 18..26) {
            inventory.setItem(slot, blackPane)
        }

        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        val languageName = languageDisplay(player, plugin.languageManager.resolveLocale(player))
        val notifyStatus = statusText(player, stats.visitorNotificationEnabled)

        inventory.setItem(
            10,
            createSettingActionItem(
                player,
                Material.BELL,
                "gui.user_settings.notification.display",
                "notification",
                notifyStatus,
                if (stats.visitorNotificationEnabled) "§a" else "§c",
                "toggle_notification",
                "gui.user_settings.cycle_action.toggle",
                glint = stats.visitorNotificationEnabled
            )
        )
        inventory.setItem(
            11,
            createSettingActionItem(
                player,
                Material.WRITABLE_BOOK,
                "gui.user_settings.language.display",
                "language",
                languageName,
                "§f",
                "cycle_language",
                "gui.user_settings.cycle_action.next"
            )
        )
        inventory.setItem(12, createCriticalVisibilityItem(player, stats.criticalSettingsEnabled))
        inventory.setItem(
            13,
            createSettingActionItem(
                player,
                Material.COMPASS,
                "gui.user_settings.tour_navigation.display",
                "tour_navigation",
                null,
                "§f",
                "cycle_tour_navigation",
                "gui.user_settings.cycle_action.toggle",
                TourNavigationMode.entries.map { mode ->
                    GuiMenuIconOption(
                        tr(player, "gui.user_settings.tour_navigation.mode.${mode.name.lowercase(Locale.ROOT)}"),
                        mode == stats.tourNavigationMode,
                        "§b",
                        "§7"
                    )
                }
            )
        )
        if (showBackButton) {
            inventory.setItem(
                22,
                createActionItem(Material.REDSTONE, tr(player, "gui.common.return"), "return_command")
            )
        }

        player.openInventory(inventory)
    }

    private fun handlePlayerWorldInventoryClick(
        player: Player,
        holder: BedrockPlayerWorldListHolder,
        event: InventoryClickEvent
    ) {
        event.cancelWithDebug("BedrockMenuService.handlePlayerWorldInventoryClick: bedrock player world list click")
        if (event.clickedInventory != event.view.topInventory) {
            return
        }

        val item = event.currentItem ?: return
        val action = ItemTag.getString(item, "bedrock_action") ?: return

        when (action) {
            "warp_world" -> {
                val worldUuid = ItemTag.getWorldUuid(item) ?: return
                val worldData = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return
                warpToWorld(player, worldData)
            }

            "open_prev_page" -> openPlayerWorld(player, holder.page - 1, holder.showBackButton)
            "open_next_page" -> openPlayerWorld(player, holder.page + 1, holder.showBackButton)
            "start_creation" -> {
                val session = plugin.creationSessionManager.startSession(player.uniqueId)
                session.isDialogMode = false
                player.closeInventory()
                plugin.creationGui.openTypeSelection(player)
            }
            "open_settings" -> openSettings(player, holder.showBackButton, holder.page)

            "open_pending_interactions" -> {
                plugin.pendingInteractionGui.open(
                    player = player,
                    page = 0,
                    returnPage = holder.page,
                    showBackButton = holder.showBackButton,
                    fromBedrockMenu = true
                )
            }

            "return_command" -> performConfiguredReturn(player)
        }
    }

    private fun handleWorldActionInventoryClick(
        player: Player,
        holder: BedrockWorldActionHolder,
        event: InventoryClickEvent
    ) {
        event.cancelWithDebug("BedrockMenuService.handleWorldActionInventoryClick: bedrock world action click")
        if (event.clickedInventory != event.view.topInventory) {
            return
        }

        val item = event.currentItem ?: return
        val action = ItemTag.getString(item, "bedrock_action") ?: return
        val worldData = plugin.worldConfigRepository.findByUuid(holder.worldUuid) ?: run {
            openPlayerWorld(player, holder.returnPage, holder.showBackButton)
            return
        }

        when (action) {
            "warp_world" -> {
                warpToWorld(player, worldData)
            }

            "cycle_publish" -> {
                cyclePublishLevel(player, worldData)
                val refreshed = plugin.worldConfigRepository.findByUuid(holder.worldUuid) ?: return
                openWorldActionMenu(player, refreshed, holder.returnPage, holder.showBackButton)
            }

            "toggle_archive" -> {
                toggleArchiveState(player, worldData) {
                    val refreshed = plugin.worldConfigRepository.findByUuid(holder.worldUuid)
                    if (refreshed != null) {
                        openWorldActionMenu(player, refreshed, holder.returnPage, holder.showBackButton)
                    } else {
                        openPlayerWorld(player, holder.returnPage, holder.showBackButton)
                    }
                }
            }

            "open_advanced_settings" -> {
                pushWorldActionRoute(player, worldData, holder.returnPage, holder.showBackButton)
                plugin.worldSettingsGui.open(player, worldData, holder.showBackButton)
            }

            "open_settings" -> {
                openSettings(player, holder.showBackButton, holder.returnPage)
            }

            "return_command" -> performConfiguredReturn(player)
        }
    }

    private fun handleSettingsInventoryClick(
        player: Player,
        holder: BedrockSettingsHolder,
        event: InventoryClickEvent
    ) {
        event.cancelWithDebug("BedrockMenuService.handleSettingsInventoryClick: bedrock settings click")
        if (event.clickedInventory != event.view.topInventory) {
            return
        }

        val item = event.currentItem ?: return
        val action = ItemTag.getString(item, "bedrock_action") ?: return
        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)

        when (action) {
            "toggle_notification" -> {
                stats.visitorNotificationEnabled = !stats.visitorNotificationEnabled
                plugin.playerStatsRepository.save(stats)
                openSettings(player, holder.showBackButton, holder.returnPage)
            }

            "cycle_language" -> {
                openSettings(player, holder.showBackButton, holder.returnPage)
            }

            "toggle_critical" -> {
                stats.criticalSettingsEnabled = !stats.criticalSettingsEnabled
                plugin.playerStatsRepository.save(stats)
                openSettings(player, holder.showBackButton, holder.returnPage)
            }

            "cycle_tour_navigation" -> {
                stats.tourNavigationMode = nextTourNavigationMode(stats.tourNavigationMode)
                plugin.playerStatsRepository.save(stats)
                plugin.tourManager.refreshNavigation(player)
                openSettings(player, holder.showBackButton, holder.returnPage)
            }

            "back_to_worlds" -> openPlayerWorld(player, holder.returnPage, holder.showBackButton)
            "return_command" -> performConfiguredReturn(player)
            "close_menu" -> player.closeInventory()
        }
    }

    private fun openWorldActionMenu(
        player: Player,
        worldData: WorldData,
        returnPage: Int,
        showBackButton: Boolean
    ) {
        if (routingService.shouldUseForm(player)) {
            if (openWorldActionsForm(player, worldData, returnPage, showBackButton)) {
                routingService.clearFormFailure(player)
                return
            }
            routingService.markFormFailure(player, "world_action_form_open_failed")
        }

        openWorldActionsInventory(player, worldData, returnPage, showBackButton)
    }

    private fun openSimpleConfirmationForm(
        player: Player,
        title: String,
        bodyLines: List<String>,
        confirmLabel: String,
        cancelLabel: String,
        failureReason: String,
        onConfirm: () -> Unit,
        onCancel: () -> Unit
    ): Boolean {
        if (!routingService.shouldUseForm(player)) {
            return false
        }

        val content = bodyLines.joinToString("\n")
        val opened =
            formBridge.sendSimpleForm(
                player = player,
                title = title,
                content = content,
                buttons = listOf(confirmLabel, cancelLabel),
                onSelect = { index ->
                    if (index == 0) {
                        onConfirm()
                    } else {
                        onCancel()
                    }
                },
                onClosed = {
                    onCancel()
                }
            )

        if (!opened) {
            routingService.markFormFailure(player, failureReason)
            return false
        }

        routingService.clearFormFailure(player)
        return true
    }

    private fun getAccessibleWorlds(player: Player): List<WorldData> {
        plugin.worldConfigRepository.loadAll()

        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        val allWorlds = plugin.worldConfigRepository.findAll()
        val accessibleWorlds =
            allWorlds
                .filter {
                    it.owner == player.uniqueId ||
                        it.moderators.contains(player.uniqueId) ||
                        it.members.contains(player.uniqueId) ||
                        it.isArchived
                }
                .filter { it.owner == player.uniqueId || !it.isArchived }

        val orderedWorlds =
            stats.worldDisplayOrder.mapNotNull { uuid ->
                accessibleWorlds.find { it.uuid == uuid }
            }

        val unorderedWorlds =
            accessibleWorlds
                .filter { !stats.worldDisplayOrder.contains(it.uuid) }
                .sortedWith(compareBy<WorldData> { it.isArchived }.thenByDescending { it.createdAt })

        return orderedWorlds + unorderedWorlds
    }

    private fun getCurrentManagedWorld(player: Player): WorldData? {
        return plugin.worldConfigRepository.findByWorldName(player.world.name)
    }

    private fun canAccessWorldSettings(player: Player, worldData: WorldData): Boolean {
        return player.hasPermission("myworldmanager.admin") ||
            worldData.owner == player.uniqueId ||
            worldData.moderators.contains(player.uniqueId) ||
            worldData.members.contains(player.uniqueId)
    }

    private fun canManagePublish(player: Player, worldData: WorldData): Boolean {
        return player.hasPermission("myworldmanager.admin") ||
            worldData.owner == player.uniqueId ||
            worldData.moderators.contains(player.uniqueId)
    }

    private fun canManageArchive(player: Player, worldData: WorldData): Boolean {
        return player.hasPermission("myworldmanager.admin") || worldData.owner == player.uniqueId
    }

    private fun warpToWorld(player: Player, worldData: WorldData) {
        if (worldData.isArchived) {
            player.sendMessage(tr(player, "messages.archive_access_denied"))
            return
        }

        val folderName = worldData.customWorldName ?: "my_world.${worldData.uuid}"
        if (Bukkit.getWorld(folderName) == null) {
            player.closeInventory()
            player.sendMessage(tr(player, "messages.world_loading"))
            plugin.worldService.teleportToWorld(player, worldData.uuid) {
                completeWarpToWorld(player, worldData)
            }
            return
        }

        player.closeInventory()
        plugin.worldService.teleportToWorld(player, worldData.uuid) {
            completeWarpToWorld(player, worldData)
        }
    }

    private fun completeWarpToWorld(player: Player, worldData: WorldData) {
        player.sendMessage(tr(player, "messages.warp_success", mapOf("world" to worldData.name)))
        player.closeInventory()
    }

    private fun cyclePublishLevel(player: Player, worldData: WorldData) {
        if (MyWorldManagerApi.getWorldPublishPolicy().cyclePublishLevel(player, worldData)) {
            return
        }
        val levels = PublishLevel.values()
        val currentIndex = levels.indexOf(worldData.publishLevel).let { if (it < 0) 0 else it }
        worldData.publishLevel = levels[(currentIndex + 1) % levels.size]
        if (worldData.publishLevel == PublishLevel.PUBLIC) {
            worldData.publicAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        }
        plugin.worldConfigRepository.save(worldData)
        player.sendMessage(
            tr(
                player,
                "messages.publish_updated",
                mapOf("level" to publishLevelText(player, worldData.publishLevel))
            )
        )
    }

    private fun toggleArchiveState(player: Player, worldData: WorldData, onComplete: () -> Unit) {
        if (worldData.isArchived) {
            player.sendMessage(tr(player, "messages.unarchive_start"))
            plugin.worldService.unarchiveWorld(worldData.uuid).thenAccept { success ->
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (success) {
                        player.sendMessage(tr(player, "messages.unarchive_success"))
                    } else {
                        player.sendMessage(tr(player, "messages.unarchive_failed"))
                    }
                    onComplete()
                })
            }
        } else {
            player.sendMessage(tr(player, "messages.archive_start"))
            plugin.worldService.archiveWorld(worldData.uuid).thenAccept { success ->
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (success) {
                        player.sendMessage(tr(player, "messages.archive_success", mapOf("world" to worldData.name)))
                    } else {
                        player.sendMessage(tr(player, "messages.archive_failed"))
                    }
                    onComplete()
                })
            }
        }
    }

    private fun languageDisplay(player: Player, languageKey: String): String {
        val message = runCatching {
            plugin.languageManager.getMessage(player, "general.language.$languageKey")
        }.getOrNull()
        return if (message.isNullOrBlank()) languageKey else message
    }

    private fun statusText(player: Player, enabled: Boolean): String {
        return if (enabled) {
            tr(player, "messages.status_on")
        } else {
            tr(player, "messages.status_off")
        }
    }

    private fun dateFormatterFor(player: Player): DateTimeFormatter {
        val language = plugin.languageManager.resolveLocale(player).lowercase(Locale.ROOT)
        return if (language == "ja_jp") {
            DateTimeFormatter.ofPattern("yyyy年MM月dd日")
        } else {
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
        }
    }

    private fun materialToBedrockPath(material: Material?): String? {
        if (material == null || material.isAir) {
            return null
        }
        return materialPathOverrides[material] ?: "textures/items/${material.key.key}"
    }

    private fun performConfiguredReturn(player: Player) {
        if (!plugin.menuRouteHistory.openPrevious(player)) {
            player.closeInventory()
        }
    }

    private fun pushWorldActionRoute(
        player: Player,
        worldData: WorldData,
        returnPage: Int,
        showBackButton: Boolean
    ) {
        plugin.menuRouteHistory.pushCustom(
            player,
            "bedrock.world_action:${worldData.uuid}:$returnPage:$showBackButton"
        ) { target ->
            val latest = plugin.worldConfigRepository.findByUuid(worldData.uuid) ?: return@pushCustom false
            openWorldActionMenu(target, latest, returnPage, showBackButton)
            true
        }
    }

    private fun createWorldListItem(player: Player, worldData: WorldData): ItemStack {
        val item = ItemStack(worldData.icon)
        val meta = item.itemMeta ?: return item

        meta.displayName(
            plugin.languageManager.getComponent(
                player,
                "gui.common.world_item_name",
                mapOf("world" to worldData.name)
            )
        )

        val ownerName = PlayerNameUtil.getNameOrDefault(worldData.owner, tr(player, "general.unknown"))
        val publishLevelColor = tr(player, "publish_level.color.${worldData.publishLevel.name.lowercase()}")
        val publishLevelName = tr(player, "publish_level.${worldData.publishLevel.name.lowercase()}")
        val tagNames =
            if (worldData.tags.isNotEmpty()) {
                worldData.tags.joinToString(", ") { plugin.worldTagManager.getDisplayName(player, it) }
            } else {
                null
            }

        val now = LocalDate.now()
        val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val displayFormatter = dateFormatterFor(player)
        val expireDate =
            try {
                LocalDate.parse(worldData.expireDate, inputFormatter)
            } catch (_: Exception) {
                LocalDate.now().plusYears(1)
            }
        val daysRemaining = ChronoUnit.DAYS.between(now, expireDate)

        val expiresAtValue =
            if (expireDate.year < 2900) {
                if (daysRemaining < 0) {
                    meta.setEnchantmentGlintOverride(true)
                }
                tr(
                    player,
                    "gui.player_world.world_item.expires_value",
                    mapOf("days" to daysRemaining, "date" to displayFormatter.format(expireDate))
                )
            } else {
                null
            }

        if (worldData.isArchived) meta.setEnchantmentGlintOverride(true)

        val warpAction = tr(player, "gui.player_world.world_item.warp")
        // Bedrock uses the same semantic sections as the Java menu, with its own action text.
        meta.lore(CCSystem.getAPI().getLoreService().render(StructuredLore.blocks(
            *buildList {
                if (worldData.description.isNotBlank()) add(listOf(GuiLoreLine.UserText(worldData.description)))
                add(buildList {
                    add(GuiLoreLine.Data(tr(player, "gui.common.world_item.owner"), ownerName, "§f"))
                    add(GuiLoreLine.Data(tr(player, "gui.common.world_item.publish"), publishLevelName, publishLevelColor))
                    add(GuiLoreLine.Data(tr(player, "gui.common.world_item.favorite"), worldData.favorite, "§c"))
                    add(GuiLoreLine.Data(
                        tr(player, "gui.common.world_item.recent_visitors"),
                        tr(player, "gui.common.world_item.recent_visitors_value", mapOf("count" to worldData.recentVisitors.sum())),
                        "§a"
                    ))
                    if (tagNames != null) add(GuiLoreLine.Data(tr(player, "gui.common.world_item.tags"), tagNames, "§e"))
                })
                val lifecycle = buildList {
                    if (expiresAtValue != null) add(GuiLoreLine.Data(tr(player, "gui.player_world.world_item.expires_at"), expiresAtValue, "§f"))
                    if (worldData.isArchived) add(GuiLoreLine.Warning(tr(player, "gui.player_world.world_item.expired")))
                }
                if (lifecycle.isNotEmpty()) add(lifecycle)
                add(listOf(GuiLoreActions.singleClick(plugin.languageManager, player, warpAction)))
            }.toTypedArray()
        )))

        item.itemMeta = meta
        ItemTag.tagItem(item, "bedrock_menu_item")
        ItemTag.setString(item, "bedrock_action", "warp_world")
        ItemTag.setWorldUuid(item, worldData.uuid)
        return item
    }

    private fun createCreationButtonItem(player: Player): ItemStack {
        val item = ItemStack(Material.NETHER_STAR)
        val meta = item.itemMeta ?: return item
        meta.displayName(plugin.languageManager.getComponent(player, "gui.player_world.creation_button.display"))
        meta.lore(CCSystem.getAPI().getLoreService().render(GuiLoreSpec.Blocks(listOf(
            com.awabi2048.ccsystem.api.gui.GuiLoreBlock(
                plugin.languageManager.getMessageList(player, "gui.player_world.creation_button.description").map(GuiLoreLine::Text)
            ),
            com.awabi2048.ccsystem.api.gui.GuiLoreBlock(listOf(GuiLoreActions.singleClick(
                plugin.languageManager,
                player,
                tr(player, "gui.player_world.creation_button.action")
            )))
        ))))
        item.itemMeta = meta
        ItemTag.tagItem(item, "bedrock_menu_item")
        ItemTag.setString(item, "bedrock_action", "start_creation")
        return item
    }

    private fun createCreationUnavailableButtonItem(player: Player, reason: CreationBlockReason): ItemStack {
        val item = ItemStack(Material.BARRIER)
        val meta = item.itemMeta ?: return item
        meta.displayName(plugin.languageManager.getComponent(player, reason.displayKey))
        meta.lore(GuiItemFactory.menuLore(plugin.languageManager.getMessageList(player, reason.loreKey).map(GuiLoreLine::Text)))
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES)
        item.itemMeta = meta
        ItemTag.tagItem(item, "bedrock_menu_item")
        ItemTag.setString(item, "bedrock_action", "noop")
        return item
    }

    private fun creationBlockReason(
        player: Player,
        currentCreateCount: Int,
        maxSlot: Int,
        bypassLimits: Boolean
    ): CreationBlockReason? {
        // 作成権限は、運営トグルや枠不足より先に本人へ表示する基本要件として扱う。
        if (!PermissionManager.checkPermission(player, PermissionManager.COMMAND_MWM_CREATE)) {
            return CreationBlockReason.NO_PERMISSION
        }
        if (!WorldCreationChecks.check(player, notify = false)) return CreationBlockReason.POLICY_DENIED
        if (bypassLimits) return null
        if (currentCreateCount >= maxSlot) return CreationBlockReason.NO_SLOT
        return null
    }

    private fun createStatsButtonItem(
        player: Player,
        currentCreateCount: Int,
        maxSlot: Int,
        worldPoint: Int
    ): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as? org.bukkit.inventory.meta.SkullMeta ?: return item
        meta.owningPlayer = player
        val playerName = PlayerNameUtil.getNameOrDefault(player.uniqueId, tr(player, "general.unknown"))
        val bypassLimits = PermissionManager.canBypassWorldLimits(player)
        val pendingCount = plugin.pendingDecisionManager.getPendingCount(player.uniqueId)
        meta.displayName(
            plugin.languageManager.getComponent(
                player,
                "gui.player_world.stats_button.display",
                mapOf("player" to playerName)
            )
        )
        meta.lore(CCSystem.getAPI().getLoreService().render(GuiLoreSpec.Blocks(buildList {
            add(com.awabi2048.ccsystem.api.gui.GuiLoreBlock(listOf(
                GuiLoreLine.Data(tr(player, "gui.player_world.stats_button.points_label"), worldPoint, "§6")
            )))
            add(com.awabi2048.ccsystem.api.gui.GuiLoreBlock(listOf(
                if (bypassLimits) {
                    GuiLoreLine.Data(tr(player, "gui.player_world.stats_button.world_count_label"), currentCreateCount, "§a")
                } else {
                    GuiLoreLine.Data(tr(player, "gui.player_world.stats_button.slots_label"), "$currentCreateCount/$maxSlot", "§a")
                },
                GuiLoreLine.Text(tr(player, if (bypassLimits) "gui.player_world.stats_button.slots_bypass_description" else "gui.player_world.stats_button.slots_description"))
            )))
            if (pendingCount > 0) {
                val latest = plugin.pendingDecisionManager.getLatestPendingCreatedAt(player.uniqueId)
                    ?.let {
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                            .withZone(ZoneId.systemDefault())
                            .format(Instant.ofEpochMilli(it))
                    }
                    ?: tr(player, "gui.player_world.pending_button.none")
                add(com.awabi2048.ccsystem.api.gui.GuiLoreBlock(listOf(
                    GuiLoreLine.Data(
                        tr(player, "gui.player_world.pending_button.count_label"),
                        pendingCount,
                        "§e"
                    ),
                    GuiLoreLine.Data(
                        tr(player, "gui.player_world.pending_button.latest_label"),
                        latest,
                        "§b"
                    )
                )))
                add(com.awabi2048.ccsystem.api.gui.GuiLoreBlock(listOf(
                    GuiLoreActions.singleClick(
                        plugin.languageManager,
                        player,
                        tr(player, "gui.player_world.pending_button.action")
                    )
                )))
            }
        })))
        meta.setEnchantmentGlintOverride(pendingCount > 0)
        item.itemMeta = meta
        ItemTag.tagItem(item, "bedrock_menu_item")
        ItemTag.setString(
            item,
            "bedrock_action",
            if (pendingCount > 0) "open_pending_interactions" else "noop"
        )
        return item
    }

    private fun createCriticalVisibilityItem(player: Player, enabled: Boolean): ItemStack {
        val status = if (enabled) tr(player, "messages.status_visible") else tr(player, "messages.status_hidden")
        return createSettingActionItem(
            player,
            Material.RECOVERY_COMPASS,
            "gui.user_settings.critical_settings_visibility.display",
            "critical_settings_visibility",
            status,
            if (enabled) "§a" else "§7",
            "toggle_critical",
            "gui.user_settings.cycle_action.toggle"
        )
    }

    private fun createSettingActionItem(
        player: Player,
        material: Material,
        displayKey: String,
        setting: String,
        currentValue: String?,
        currentValueColor: String,
        actionId: String,
        actionKey: String,
        options: List<GuiMenuIconOption> = emptyList(),
        glint: Boolean? = null
    ): ItemStack {
        val prefix = "gui.user_settings.$setting.blocks"
        val item = GuiItemFactory.menuIcon(
            GuiMenuIconSpec(
                material,
                GuiNameSpec.Text(tr(player, displayKey), GuiNameStyle.DEFAULT),
                GuiElementRole.CONTENT,
                1,
                plugin.languageManager.getMessageList(player, "$prefix.description"),
                currentValue?.let {
                    listOf(GuiMenuIconData(tr(player, "$prefix.current_label"), it, currentValueColor))
                } ?: emptyList(),
                options,
                emptyList(),
                emptyList(),
                listOf(GuiLoreActions.menuSingleClick(plugin.languageManager, player, tr(player, actionKey))),
                glint
            ),
            "bedrock_menu_item"
        )
        ItemTag.setString(item, "bedrock_action", actionId)
        return item
    }

    private fun nextTourNavigationMode(mode: TourNavigationMode): TourNavigationMode {
        return when (mode) {
            TourNavigationMode.BOSSBAR_ONLY -> TourNavigationMode.ALL
            TourNavigationMode.ALL -> TourNavigationMode.NONE
            TourNavigationMode.NONE -> TourNavigationMode.BOSSBAR_ONLY
        }
    }

    private fun createActionItem(
        material: Material,
        displayName: String,
        action: String,
        worldUuid: UUID? = null,
        lore: GuiLoreSpec = GuiLoreSpec.None
    ): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item

        meta.displayName(CCSystem.getAPI().getGuiElementService().name(displayName))

        if (lore != GuiLoreSpec.None) {
            meta.lore(CCSystem.getAPI().getLoreService().render(lore))
        }

        item.itemMeta = meta
        ItemTag.tagItem(item, "bedrock_menu_item")
        ItemTag.setString(item, "bedrock_action", action)
        if (worldUuid != null) {
            ItemTag.setWorldUuid(item, worldUuid)
        }
        return item
    }

    private fun tr(player: Player, key: String, placeholders: Map<String, Any> = emptyMap()): String {
        return if (placeholders.isEmpty()) {
            plugin.languageManager.getMessage(player, key)
        } else {
            plugin.languageManager.getMessage(player, key, placeholders)
        }
    }

    private fun publishLevelText(player: Player, level: PublishLevel): String {
        return tr(player, "publish_level.${level.name.lowercase()}")
    }

    private fun publishDisplayText(player: Player, worldData: WorldData): String {
        return MyWorldManagerApi.getWorldPublishPolicy()
            .getPublishDisplayName(player, worldData, publishLevelText(player, worldData.publishLevel))
    }

    private fun worldStateText(player: Player, archived: Boolean): String {
        return if (archived) {
            tr(player, "gui.bedrock.world_action.status.archived")
        } else {
            tr(player, "gui.bedrock.world_action.status.active")
        }
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

    private enum class CreationBlockReason(val displayKey: String, val loreKey: String) {
        POLICY_DENIED(
            "gui.player_world.creation_unavailable.policy_denied.display",
            "gui.player_world.creation_unavailable.policy_denied.lore"
        ),
        NO_SLOT(
            "gui.player_world.creation_unavailable.no_slot.display",
            "gui.player_world.creation_unavailable.no_slot.lore"
        ),
        NO_PERMISSION(
            "gui.player_world.creation_unavailable.no_permission.display",
            "gui.player_world.creation_unavailable.no_permission.lore"
        )
    }
}
