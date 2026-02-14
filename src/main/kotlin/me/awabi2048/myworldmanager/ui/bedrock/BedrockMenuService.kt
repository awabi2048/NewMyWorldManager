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

    fun openPlayerWorld(player: Player, page: Int = 0, showBackButton: Boolean = false) {
        val safePage = page.coerceAtLeast(0)

        if (routingService.shouldUseForm(player)) {
            val opened = openPlayerWorldForm(player, safePage, showBackButton)
            if (opened) {
                return
            }
            routingService.markFormFailure(player, "openPlayerWorldForm returned false")
        }

        openPlayerWorldInventory(player, safePage, showBackButton)
    }

    fun openCurrentWorldMenu(player: Player, worldData: WorldData, showBackButton: Boolean = false) {
        openWorldActionMenu(player, worldData, 0, showBackButton)
    }

    fun openSettings(player: Player, showBackButton: Boolean = false, returnPage: Int = 0) {
        if (routingService.shouldUseForm(player)) {
            val opened = openSettingsForm(player, showBackButton, returnPage)
            if (opened) {
                return
            }
            routingService.markFormFailure(player, "openSettingsForm returned false")
        }

        openSettingsInventory(player, showBackButton, returnPage)
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

        val actions = mutableListOf<FormAction>()
        pageWorlds.forEach { world ->
            val archivedMarker = if (world.isArchived) "[A] " else ""
            actions += FormAction("$archivedMarker${world.name}") {
                val latestWorld = plugin.worldConfigRepository.findByUuid(world.uuid) ?: return@FormAction
                openWorldActionMenu(player, latestWorld, page, showBackButton)
            }
        }

        if (page > 0) {
            actions += FormAction("< Prev") {
                openPlayerWorld(player, page - 1, showBackButton)
            }
        }
        if (start + pageWorlds.size < worlds.size) {
            actions += FormAction("Next >") {
                openPlayerWorld(player, page + 1, showBackButton)
            }
        }

        actions += FormAction("Settings") {
            openSettings(player, showBackButton, page)
        }

        val currentManagedWorld = getCurrentManagedWorld(player)
        if (currentManagedWorld != null && canAccessWorldSettings(player, currentManagedWorld)) {
            actions += FormAction("Current World") {
                openCurrentWorldMenu(player, currentManagedWorld, showBackButton)
            }
        }

        if (showBackButton) {
            actions += FormAction("Return") {
                performConfiguredReturn(player)
            }
        }

        actions += FormAction("Close") {
            player.closeInventory()
        }

        val title = "My Worlds [BE]"
        val content = "Page ${page + 1}/$totalPages"
        return sendActionForm(player, title, content, actions)
    }

    private fun openWorldActionsForm(
        player: Player,
        worldData: WorldData,
        returnPage: Int,
        showBackButton: Boolean
    ): Boolean {
        val actions = mutableListOf<FormAction>()

        actions += FormAction("Warp") {
            val latest = plugin.worldConfigRepository.findByUuid(worldData.uuid) ?: return@FormAction
            warpToWorld(player, latest)
        }

        if (canManagePublish(player, worldData)) {
            actions += FormAction("Cycle Publish (${worldData.publishLevel.name})") {
                val latest = plugin.worldConfigRepository.findByUuid(worldData.uuid) ?: return@FormAction
                cyclePublishLevel(player, latest)
                openWorldActionMenu(player, latest, returnPage, showBackButton)
            }
        }

        if (canManageArchive(player, worldData)) {
            val label = if (worldData.isArchived) "Unarchive" else "Archive"
            actions += FormAction(label) {
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
            actions += FormAction("Advanced Settings GUI") {
                val latest = plugin.worldConfigRepository.findByUuid(worldData.uuid) ?: return@FormAction
                plugin.worldSettingsGui.open(player, latest, showBackButton)
            }
        }

        actions += FormAction("Back to Worlds") {
            openPlayerWorld(player, returnPage, showBackButton)
        }

        actions += FormAction("Settings") {
            openSettings(player, showBackButton, returnPage)
        }

        if (showBackButton) {
            actions += FormAction("Return") {
                performConfiguredReturn(player)
            }
        }

        actions += FormAction("Close") {
            player.closeInventory()
        }

        val content =
            "Owner: ${worldData.owner}\nStatus: ${if (worldData.isArchived) "ARCHIVED" else "ACTIVE"}\nPublish: ${worldData.publishLevel.name}"
        return sendActionForm(player, worldData.name, content, actions)
    }

    private fun openSettingsForm(player: Player, showBackButton: Boolean, returnPage: Int): Boolean {
        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        val actions = mutableListOf<FormAction>()

        actions += FormAction("Visitor Notification: ${statusText(stats.visitorNotificationEnabled)}") {
            stats.visitorNotificationEnabled = !stats.visitorNotificationEnabled
            plugin.playerStatsRepository.save(stats)
            openSettings(player, showBackButton, returnPage)
        }

        actions += FormAction("Language: ${languageDisplay(player, stats.language)}") {
            cycleLanguage(stats)
            plugin.playerStatsRepository.save(stats)
            openSettings(player, showBackButton, returnPage)
        }

        actions += FormAction("Critical Menu: ${statusText(stats.criticalSettingsEnabled)}") {
            stats.criticalSettingsEnabled = !stats.criticalSettingsEnabled
            plugin.playerStatsRepository.save(stats)
            openSettings(player, showBackButton, returnPage)
        }

        actions += FormAction("Beta Features: ${statusText(stats.betaFeaturesEnabled)}") {
            stats.betaFeaturesEnabled = !stats.betaFeaturesEnabled
            plugin.playerStatsRepository.save(stats)
            openSettings(player, showBackButton, returnPage)
        }

        actions += FormAction("Back to Worlds") {
            openPlayerWorld(player, returnPage, showBackButton)
        }

        if (showBackButton) {
            actions += FormAction("Return") {
                performConfiguredReturn(player)
            }
        }

        actions += FormAction("Close") {
            player.closeInventory()
        }

        return sendActionForm(player, "User Settings [BE]", "Single-tap menu for Bedrock", actions)
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

        val labels = actions.map { it.label }
        return formBridge.sendSimpleForm(
            player = player,
            title = title,
            content = content,
            buttons = labels,
            onSelect = { index ->
                val action = actions.getOrNull(index) ?: return@sendSimpleForm
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
        val inventory = Bukkit.createInventory(holder, 54, Component.text("My Worlds [BE]"))
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
            inventory.setItem(slot, createWorldListItem(worldData))
        }

        if (page > 0) {
            inventory.setItem(
                45,
                createActionItem(Material.ARROW, "Prev", "open_prev_page")
            )
        }
        if (start + pageWorlds.size < worlds.size) {
            inventory.setItem(
                53,
                createActionItem(Material.ARROW, "Next", "open_next_page")
            )
        }

        inventory.setItem(
            47,
            createActionItem(Material.WRITABLE_BOOK, "Settings", "open_settings")
        )

        val currentManagedWorld = getCurrentManagedWorld(player)
        if (currentManagedWorld != null && canAccessWorldSettings(player, currentManagedWorld)) {
            inventory.setItem(
                49,
                createActionItem(Material.COMPASS, "Current World", "open_current_world_menu")
            )
        }

        if (showBackButton) {
            inventory.setItem(
                51,
                createActionItem(Material.BARRIER, "Return", "return_command")
            )
        }

        inventory.setItem(
            52,
            createActionItem(Material.REDSTONE, "Close", "close_menu")
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
        val inventory = Bukkit.createInventory(holder, 27, Component.text("World: ${worldData.name}"))
        holder.inv = inventory

        val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
        for (slot in 0 until 27) {
            inventory.setItem(slot, blackPane)
        }

        inventory.setItem(
            10,
            createActionItem(Material.ENDER_PEARL, "Warp", "warp_world", worldData.uuid)
        )

        if (canManagePublish(player, worldData)) {
            inventory.setItem(
                11,
                createActionItem(
                    Material.ENDER_EYE,
                    "Cycle Publish (${worldData.publishLevel.name})",
                    "cycle_publish",
                    worldData.uuid
                )
            )
        }

        if (canManageArchive(player, worldData)) {
            val label = if (worldData.isArchived) "Unarchive" else "Archive"
            inventory.setItem(
                12,
                createActionItem(Material.CHEST, label, "toggle_archive", worldData.uuid)
            )
        }

        if (canAccessWorldSettings(player, worldData)) {
            inventory.setItem(
                14,
                createActionItem(Material.COMPARATOR, "Advanced Settings GUI", "open_advanced_settings", worldData.uuid)
            )
        }

        inventory.setItem(
            15,
            createActionItem(Material.WRITABLE_BOOK, "Settings", "open_settings")
        )

        inventory.setItem(
            16,
            createActionItem(Material.ARROW, "Back to Worlds", "back_to_worlds")
        )

        if (showBackButton) {
            inventory.setItem(
                22,
                createActionItem(Material.BARRIER, "Return", "return_command")
            )
        } else {
            inventory.setItem(
                22,
                createActionItem(Material.REDSTONE, "Close", "close_menu")
            )
        }

        player.openInventory(inventory)
    }

    private fun openSettingsInventory(player: Player, showBackButton: Boolean, returnPage: Int) {
        val holder = BedrockSettingsHolder(showBackButton, returnPage)
        val inventory = Bukkit.createInventory(holder, 27, Component.text("Settings [BE]"))
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
                "Visitor Notification: ${statusText(stats.visitorNotificationEnabled)}",
                "toggle_notification"
            )
        )
        inventory.setItem(
            11,
            createActionItem(
                Material.WRITABLE_BOOK,
                "Language: ${languageDisplay(player, stats.language)}",
                "cycle_language"
            )
        )
        inventory.setItem(
            12,
            createActionItem(
                Material.REPEATER,
                "Critical Menu: ${statusText(stats.criticalSettingsEnabled)}",
                "toggle_critical"
            )
        )
        inventory.setItem(
            13,
            createActionItem(
                Material.EXPERIENCE_BOTTLE,
                "Beta Features: ${statusText(stats.betaFeaturesEnabled)}",
                "toggle_beta"
            )
        )
        inventory.setItem(
            15,
            createActionItem(Material.ARROW, "Back to Worlds", "back_to_worlds")
        )

        if (showBackButton) {
            inventory.setItem(
                22,
                createActionItem(Material.BARRIER, "Return", "return_command")
            )
        }

        inventory.setItem(
            23,
            createActionItem(Material.REDSTONE, "Close", "close_menu")
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

            "toggle_beta" -> {
                stats.betaFeaturesEnabled = !stats.betaFeaturesEnabled
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
        if (routingService.shouldUseForm(player)) {
            val opened = openWorldActionsForm(player, worldData, returnPage, showBackButton)
            if (opened) {
                return
            }
            routingService.markFormFailure(player, "openWorldActionsForm returned false")
        }

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
            player.sendMessage("§cThis world is archived.")
            return
        }

        val folderName = worldData.customWorldName ?: "my_world.${worldData.uuid}"
        if (Bukkit.getWorld(folderName) == null && !plugin.worldService.loadWorld(worldData.uuid)) {
            player.sendMessage("§cFailed to load the world.")
            return
        }

        val targetWorld = Bukkit.getWorld(folderName)
        if (targetWorld == null) {
            player.sendMessage("§cWorld not found.")
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
        player.sendMessage("§aWarped to ${worldData.name}.")
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
        player.sendMessage("§aPublish updated: ${worldData.publishLevel.name}")
    }

    private fun toggleArchiveState(player: Player, worldData: WorldData, onComplete: () -> Unit) {
        if (worldData.isArchived) {
            player.sendMessage("§eUnarchiving world...")
            plugin.worldService.unarchiveWorld(worldData.uuid).thenAccept { success ->
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (success) {
                        player.sendMessage("§aWorld unarchived.")
                    } else {
                        player.sendMessage("§cFailed to unarchive world.")
                    }
                    onComplete()
                })
            }
        } else {
            player.sendMessage("§eArchiving world...")
            plugin.worldService.archiveWorld(worldData.uuid).thenAccept { success ->
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (success) {
                        player.sendMessage("§aWorld archived.")
                    } else {
                        player.sendMessage("§cFailed to archive world.")
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

    private fun statusText(enabled: Boolean): String {
        return if (enabled) "ON" else "OFF"
    }

    private fun performConfiguredReturn(player: Player) {
        player.closeInventory()
        val command = plugin.config.getString("menu_command", "mwm")?.removePrefix("/") ?: "mwm"
        player.performCommand(command)
    }

    private fun createWorldListItem(worldData: WorldData): ItemStack {
        val title = if (worldData.isArchived) "[A] ${worldData.name}" else worldData.name
        val lore =
            listOf(
                "Publish: ${worldData.publishLevel.name}",
                "Favorite: ${worldData.favorite}",
                "Tap to open actions"
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
