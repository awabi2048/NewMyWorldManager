package me.awabi2048.myworldmanager.ui.bedrock

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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

    private val inventoryWorldSlots =
        listOf(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        )

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
        plugin.playerWorldGui.open(player, safePage, showBackButton)
    }

    fun openCurrentWorldMenu(player: Player, worldData: WorldData, showBackButton: Boolean = false) {
        plugin.worldSettingsGui.open(player, worldData, showBackButton)
    }

    fun openSettings(player: Player, showBackButton: Boolean = false, returnPage: Int = 0) {
        plugin.userSettingsGui.open(player, showBackButton)
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
            openSettings(player, showBackButton, page)
        }

        val currentManagedWorld = getCurrentManagedWorld(player)
        if (currentManagedWorld != null && canAccessWorldSettings(player, currentManagedWorld)) {
            actions += FormAction(tr(player, "gui.bedrock.player_world.button.current_world"), Material.COMPASS) {
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
                        mapOf("level" to publishLevelText(player, worldData.publishLevel))
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
                        mapOf("publish" to publishLevelText(player, worldData.publishLevel))
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
                    mapOf("language" to languageDisplay(player, stats.language))
                ),
                Material.WRITABLE_BOOK
            ) {
            cycleLanguage(stats)
            plugin.playerStatsRepository.save(stats)
            openSettings(player, showBackButton, returnPage)
        }

        actions +=
            FormAction(
                tr(
                    player,
                    "gui.bedrock.settings.button.critical",
                    mapOf("status" to statusText(player, stats.criticalSettingsEnabled))
                ),
                Material.REPEATER
            ) {
            stats.criticalSettingsEnabled = !stats.criticalSettingsEnabled
            plugin.playerStatsRepository.save(stats)
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
        val pageSize = inventoryWorldSlots.size
        val totalPages = if (worlds.isEmpty()) 1 else (worlds.size + pageSize - 1) / pageSize
        val page = requestedPage.coerceIn(0, totalPages - 1)
        val start = page * pageSize
        val pageWorlds = worlds.drop(start).take(pageSize)

        val holder = BedrockPlayerWorldListHolder(page, showBackButton)
        val inventory =
            Bukkit.createInventory(
                holder,
                54,
                Component.text(tr(player, "gui.bedrock.player_world.title"))
            )
        holder.inv = inventory

        val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
        val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)

        for (slot in 0..8) {
            inventory.setItem(slot, blackPane)
        }
        for (slot in 9 until 45) {
            inventory.setItem(slot, grayPane)
        }
        for (slot in 45 until 54) {
            inventory.setItem(slot, blackPane)
        }

        pageWorlds.forEachIndexed { index, worldData ->
            val slot = inventoryWorldSlots[index]
            inventory.setItem(slot, createWorldListItem(player, worldData))
        }

        if (page > 0) {
            inventory.setItem(
                45,
                createActionItem(Material.ARROW, tr(player, "gui.bedrock.player_world.button.prev"), "open_prev_page")
            )
        }
        if (start + pageWorlds.size < worlds.size) {
            inventory.setItem(
                53,
                createActionItem(Material.ARROW, tr(player, "gui.bedrock.player_world.button.next"), "open_next_page")
            )
        }

        inventory.setItem(
            47,
            createActionItem(Material.WRITABLE_BOOK, tr(player, "gui.bedrock.player_world.button.settings"), "open_settings")
        )

        val currentManagedWorld = getCurrentManagedWorld(player)
        if (currentManagedWorld != null && canAccessWorldSettings(player, currentManagedWorld)) {
            inventory.setItem(
                49,
                createActionItem(Material.COMPASS, tr(player, "gui.bedrock.player_world.button.current_world"), "open_current_world_menu")
            )
        }

        if (showBackButton) {
            inventory.setItem(
                51,
                createActionItem(Material.BARRIER, tr(player, "gui.bedrock.player_world.button.return"), "return_command")
            )
        }

        inventory.setItem(
            52,
            createActionItem(Material.REDSTONE, tr(player, "gui.bedrock.player_world.button.close"), "close_menu")
        )

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
                Component.text(tr(player, "gui.bedrock.world_action.title", mapOf("world" to worldData.name)))
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
                        mapOf("level" to publishLevelText(player, worldData.publishLevel))
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
        val inventory = Bukkit.createInventory(holder, 27, Component.text(tr(player, "gui.bedrock.settings.title")))
        holder.inv = inventory

        val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
        for (slot in 0 until 27) {
            inventory.setItem(slot, blackPane)
        }

        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)

        inventory.setItem(
            10,
            createActionItem(
                Material.BELL,
                tr(
                    player,
                    "gui.bedrock.settings.button.notification",
                    mapOf("status" to statusText(player, stats.visitorNotificationEnabled))
                ),
                "toggle_notification"
            )
        )
        inventory.setItem(
            11,
            createActionItem(
                Material.WRITABLE_BOOK,
                tr(
                    player,
                    "gui.bedrock.settings.button.language",
                    mapOf("language" to languageDisplay(player, stats.language))
                ),
                "cycle_language"
            )
        )
        inventory.setItem(
            12,
            createActionItem(
                Material.REPEATER,
                tr(
                    player,
                    "gui.bedrock.settings.button.critical",
                    mapOf("status" to statusText(player, stats.criticalSettingsEnabled))
                ),
                "toggle_critical"
            )
        )
        inventory.setItem(
            15,
            createActionItem(Material.ARROW, tr(player, "gui.bedrock.settings.button.back_to_worlds"), "back_to_worlds")
        )

        if (showBackButton) {
            inventory.setItem(
                22,
                createActionItem(Material.BARRIER, tr(player, "gui.bedrock.settings.button.return"), "return_command")
            )
        }

        inventory.setItem(
            23,
            createActionItem(Material.REDSTONE, tr(player, "gui.bedrock.settings.button.close"), "close_menu")
        )

        player.openInventory(inventory)
    }

    private fun handlePlayerWorldInventoryClick(
        player: Player,
        holder: BedrockPlayerWorldListHolder,
        event: InventoryClickEvent
    ) {
        event.isCancelled = true
        if (event.clickedInventory != event.view.topInventory) {
            return
        }

        val item = event.currentItem ?: return
        val action = ItemTag.getString(item, "bedrock_action") ?: return

        when (action) {
            "open_world_actions" -> {
                val worldUuid = ItemTag.getWorldUuid(item) ?: return
                val worldData = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return
                openWorldActionMenu(player, worldData, holder.page, holder.showBackButton)
            }

            "open_prev_page" -> openPlayerWorld(player, holder.page - 1, holder.showBackButton)
            "open_next_page" -> openPlayerWorld(player, holder.page + 1, holder.showBackButton)
            "open_settings" -> openSettings(player, holder.showBackButton, holder.page)
            "open_current_world_menu" -> {
                val currentWorld = getCurrentManagedWorld(player) ?: return
                openCurrentWorldMenu(player, currentWorld, holder.showBackButton)
            }

            "return_command" -> performConfiguredReturn(player)
            "close_menu" -> player.closeInventory()
        }
    }

    private fun handleWorldActionInventoryClick(
        player: Player,
        holder: BedrockWorldActionHolder,
        event: InventoryClickEvent
    ) {
        event.isCancelled = true
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
                plugin.worldSettingsGui.open(player, worldData, holder.showBackButton)
            }

            "open_settings" -> {
                openSettings(player, holder.showBackButton, holder.returnPage)
            }

            "back_to_worlds" -> openPlayerWorld(player, holder.returnPage, holder.showBackButton)
            "return_command" -> performConfiguredReturn(player)
            "close_menu" -> player.closeInventory()
        }
    }

    private fun handleSettingsInventoryClick(
        player: Player,
        holder: BedrockSettingsHolder,
        event: InventoryClickEvent
    ) {
        event.isCancelled = true
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
                cycleLanguage(stats)
                plugin.playerStatsRepository.save(stats)
                openSettings(player, holder.showBackButton, holder.returnPage)
            }

            "toggle_critical" -> {
                stats.criticalSettingsEnabled = !stats.criticalSettingsEnabled
                plugin.playerStatsRepository.save(stats)
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
        openWorldActionsInventory(player, worldData, returnPage, showBackButton)
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
        if (Bukkit.getWorld(folderName) == null && !plugin.worldService.loadWorld(worldData.uuid)) {
            player.sendMessage(tr(player, "messages.world_load_failed"))
            return
        }

        val targetWorld = Bukkit.getWorld(folderName)
        if (targetWorld == null) {
            player.sendMessage(tr(player, "messages.world_not_found"))
            return
        }

        val isMember =
            worldData.owner == player.uniqueId ||
                worldData.moderators.contains(player.uniqueId) ||
                worldData.members.contains(player.uniqueId)

        val spawnLocation =
            if (isMember) {
                worldData.spawnPosMember ?: targetWorld.spawnLocation
            } else {
                worldData.spawnPosGuest ?: targetWorld.spawnLocation
            }

        plugin.worldService.teleportToWorld(player, worldData.uuid, spawnLocation)
        player.sendMessage(tr(player, "messages.warp_success", mapOf("world" to worldData.name)))
        player.closeInventory()
    }

    private fun cyclePublishLevel(player: Player, worldData: WorldData) {
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

    private fun cycleLanguage(stats: me.awabi2048.myworldmanager.model.PlayerStats) {
        val supported = plugin.languageManager.getSupportedLanguages()
        if (supported.isEmpty()) {
            return
        }

        val current = supported.indexOf(stats.language)
        val next = if (current == -1) 0 else (current + 1) % supported.size
        stats.language = supported[next]
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

    private fun materialToBedrockPath(material: Material?): String? {
        if (material == null || material.isAir) {
            return null
        }
        return materialPathOverrides[material] ?: "textures/items/${material.key.key}"
    }

    private fun performConfiguredReturn(player: Player) {
        player.closeInventory()
        val command = plugin.config.getString("menu_command", "mwm")?.removePrefix("/") ?: "mwm"
        player.performCommand(command)
    }

    private fun createWorldListItem(player: Player, worldData: WorldData): ItemStack {
        val archivedPrefix = tr(player, "gui.bedrock.player_world.archived_prefix")
        val title = if (worldData.isArchived) "$archivedPrefix${worldData.name}" else worldData.name
        val lore =
            listOf(
                tr(
                    player,
                    "gui.bedrock.world_item.publish",
                    mapOf("publish" to publishLevelText(player, worldData.publishLevel))
                ),
                tr(player, "gui.bedrock.world_item.favorite", mapOf("favorite" to worldData.favorite)),
                tr(player, "gui.bedrock.world_item.open_actions")
            )

        return createActionItem(
            material = worldData.icon,
            displayName = title,
            action = "open_world_actions",
            worldUuid = worldData.uuid,
            lore = lore
        )
    }

    private fun createActionItem(
        material: Material,
        displayName: String,
        action: String,
        worldUuid: UUID? = null,
        lore: List<String> = emptyList()
    ): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item

        meta.displayName(
            Component.text(displayName, NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false)
        )

        if (lore.isNotEmpty()) {
            meta.lore(
                lore.map {
                    Component.text(it, NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                }
            )
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
}
