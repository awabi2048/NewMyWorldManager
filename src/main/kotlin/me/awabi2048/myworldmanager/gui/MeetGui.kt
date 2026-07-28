package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuActionContext
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import java.util.UUID
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.api.extension.MeetTargetAction
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.GuiItemFactory
import me.awabi2048.myworldmanager.util.GuiLoreBuilder
import me.awabi2048.myworldmanager.util.ItemTag
import me.awabi2048.myworldmanager.util.WorldAccessMessageResolver
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class MeetGui(private val plugin: MyWorldManager) {
    private val runtime = CCSystem.getAPI().getMenuRuntimeService()

    // 1行7アイコン中央寄せのレイアウト (スロット 10~16, 19~25, ...)
    private val playerSlots = listOf(
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
    )
    private val itemsPerPage = playerSlots.size

    init {
        runtime.register(
            InventoryMenuDefinition(
                owner = OWNER,
                id = ROUTE_ID,
                renderer = { context -> render(context.player, context.route) },
                actions = mapOf(
                    ACTION_PAGE to MenuActionHandler(::page),
                    ACTION_STATUS to MenuActionHandler(::cycleStatus),
                    ACTION_BACK to MenuActionHandler(::back),
                    ACTION_TARGET to MenuActionHandler(::target),
                ),
            ),
        )
    }

    fun open(player: Player, showBackButton: Boolean? = null) {
        val lang = plugin.languageManager
        val session = plugin.meetSessionManager.getSession(player.uniqueId)

        if (showBackButton != null) {
            session.showBackButton = showBackButton
        }

        val titleKey = "gui.meet.title_list"
        if (!lang.hasKey(player, titleKey)) {
            player.sendMessage("§c[MyWorldManager] Error: Missing translation key: $titleKey")
            return
        }
        val route = MenuRoute(OWNER, ROUTE_ID, mapOf(PAGE to session.currentPage.toString()))
        if (GuiHelper.canGoBack(player)) {
            runtime.navigate(player, route)
        } else {
            runtime.open(player, route)
        }
    }

    private fun render(player: Player, route: MenuRoute): InventoryMenuView {
        val lang = plugin.languageManager
        val session = plugin.meetSessionManager.getSession(player.uniqueId)
        val targets = collectTargets(player)
        val totalPages = if (targets.isEmpty()) 1 else (targets.size + itemsPerPage - 1) / itemsPerPage
        val currentPage = (route.payload[PAGE]?.toIntOrNull() ?: session.currentPage).coerceIn(0, totalPages - 1)
        session.currentPage = currentPage
        val title = GuiHelper.inventoryTitle(lang.getMessage(player, "gui.meet.title_list"))
        val elements = mutableListOf<MenuElement>()
        val pageTargets = targets.drop(currentPage * itemsPerPage).take(itemsPerPage)
        pageTargets.forEachIndexed { index, target ->
            val action = resolveTargetAction(player, target)
            val actionable = action == TargetAction.DIRECT || action == TargetAction.REQUEST
            elements += MenuElement(
                playerSlots[index],
                createTargetHead(target, player, plugin),
                if (actionable) GuiElementRole.ACTION else GuiElementRole.CONTENT,
                if (actionable) ACTION_TARGET else null,
                if (actionable) mapOf(TARGET_UUID to target.uniqueId.toString()) else emptyMap(),
            )
        }
        if (pageTargets.isEmpty()) {
            elements += MenuElement(22, createEmptyItem(player), GuiElementRole.CONTENT)
        }

        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        val currentStatus = stats.meetStatus
        val statusNameKey = "general.status.${currentStatus.lowercase()}"
        val statusName = if (lang.hasKey(player, statusNameKey)) lang.getMessage(player, statusNameKey) else currentStatus
        val statusLore = GuiLoreBuilder(lang, player)
            .block(listOf(
                GuiLoreLine.Data(lang.getMessage(player, "gui.meet.status_button.current"), statusName, "§e"),
                GuiLoreLine.Text(lang.getMessage(player, "general.status.description.${currentStatus.lowercase()}"))
            ))
            .actions(lang.getMessage(player, "gui.meet.status_button.action"))
            .build()
        val statusItem = ItemStack(Material.PLAYER_HEAD)
        (statusItem.itemMeta as? org.bukkit.inventory.meta.SkullMeta)?.let { meta ->
            meta.owningPlayer = player
            meta.displayName(lang.getComponent(player, "gui.meet.status_button.display", mapOf("player" to player.name)))
            meta.lore(statusLore)
            statusItem.itemMeta = meta
        }
        ItemTag.tagItem(statusItem, ItemTag.TYPE_GUI_MEET_STATUS_TOGGLE)
        elements += MenuElement(40, statusItem, GuiElementRole.ACTION, ACTION_STATUS)

        if (currentPage > 0) {
            elements += MenuElement(
                37,
                GuiHelper.createPrevPageItem(plugin, player, "meet", currentPage - 1),
                GuiElementRole.NAVIGATION,
                ACTION_PAGE,
                mapOf(PAGE to (currentPage - 1).toString()),
            )
        }
        if (currentPage < totalPages - 1) {
            elements += MenuElement(
                43,
                GuiHelper.createNextPageItem(plugin, player, "meet", currentPage + 1),
                GuiElementRole.NAVIGATION,
                ACTION_PAGE,
                mapOf(PAGE to (currentPage + 1).toString()),
            )
        }
        if (GuiHelper.canGoBack(player)) {
            elements += MenuElement(
                36,
                GuiHelper.createReturnItem(plugin, player, "meet"),
                GuiElementRole.BACK,
                ACTION_BACK,
            )
        }
        return InventoryMenuView(GuiHelper.confirmationLayout().size, title, elements)
    }

    private fun collectTargets(player: Player): List<Player> =
        Bukkit.getOnlinePlayers().filter { target ->
            if (target.uniqueId == player.uniqueId) return@filter false
            if (!plugin.playerVisibilityService.isVisibleTo(player, target)) return@filter false

            if (target.world.uid == player.world.uid) {
                val stats = plugin.playerStatsRepository.findByUuid(target.uniqueId)
                return@filter stats.meetStatus != "BUSY"
            }

            val worldName = target.world.name
            val worldData = plugin.worldConfigRepository.findByWorldName(worldName) ?: return@filter false

            // 公開/限定公開のワールドにいるかチェック
            val isMember = worldData.owner == player.uniqueId ||
                worldData.members.contains(player.uniqueId) ||
                worldData.moderators.contains(player.uniqueId)
            if (!MyWorldManagerApi.getWorldAccessPolicy().canShowMeetTarget(player, target, worldData, isMember)) {
                return@filter false
            }

            val stats = plugin.playerStatsRepository.findByUuid(target.uniqueId)
            stats.meetStatus != "BUSY"
        }.sortedBy { it.name }

    private fun page(context: MenuActionContext): MenuActionResult {
        val targetPage = context.payload[PAGE]?.toIntOrNull() ?: return MenuActionResult.Rejected()
        plugin.meetSessionManager.getSession(context.player.uniqueId).currentPage = targetPage
        return MenuActionResult.Success(MenuUpdate.Replace(MenuRoute(OWNER, ROUTE_ID, mapOf(PAGE to targetPage.toString()))))
    }

    private fun cycleStatus(context: MenuActionContext): MenuActionResult {
        val stats = plugin.playerStatsRepository.findByUuid(context.player.uniqueId)
        stats.meetStatus = when (stats.meetStatus) {
            "JOIN_ME" -> "ASK_ME"
            "ASK_ME" -> "BUSY"
            else -> "JOIN_ME"
        }
        plugin.playerStatsRepository.save(stats)
        plugin.meetSessionManager.getSession(context.player.uniqueId).currentPage = 0
        return MenuActionResult.Success(MenuUpdate.Replace(MenuRoute(OWNER, ROUTE_ID, mapOf(PAGE to "0"))))
    }

    private fun back(context: MenuActionContext): MenuActionResult {
        return MenuActionResult.Success(MenuUpdate.Back)
    }

    private fun target(context: MenuActionContext): MenuActionResult {
        val player = context.player
        val target = context.payload[TARGET_UUID]
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?.let(Bukkit::getPlayer)
            ?.takeIf { it.isOnline && plugin.playerVisibilityService.isVisibleTo(player, it) }
            ?: run {
                player.sendMessage(plugin.languageManager.getMessage(player, "error.target_offline"))
                return MenuActionResult.Rejected()
            }
        return when (val action = resolveTargetAction(player, target)) {
            TargetAction.REQUEST -> {
                sendMeetRequest(player, target)
                MenuActionResult.Success(MenuUpdate.Close)
            }
            TargetAction.DIRECT -> {
                val worldData = plugin.worldConfigRepository.findByWorldName(target.world.name)
                    ?: return MenuActionResult.Rejected()
                plugin.worldService.teleportToWorld(player, worldData.uuid) {
                    player.sendMessage(
                        plugin.languageManager.getMessage(
                            player,
                            "messages.warp_success",
                            mapOf("world" to worldData.name),
                        ),
                    )
                    if (worldData.notificationEnabled && plugin.playerVisibilityService.isVisibleTo(target, player)) {
                        target.sendMessage(
                            plugin.languageManager.getMessage(
                                target,
                                "messages.visitor_notified",
                                mapOf("player" to player.name, "world" to worldData.name),
                            ),
                        )
                    }
                }
                MenuActionResult.Success(MenuUpdate.Close)
            }
            TargetAction.DENY, null -> {
                val worldData = plugin.worldConfigRepository.findByWorldName(target.world.name)
                if (worldData == null) {
                    player.sendMessage(plugin.languageManager.getMessage(player, "error.target_not_in_myworld"))
                    return MenuActionResult.Rejected()
                }
                val isMember = (
                    worldData.owner == player.uniqueId ||
                        worldData.moderators.contains(player.uniqueId) ||
                        worldData.members.contains(player.uniqueId)
                    )
                player.sendMessage(WorldAccessMessageResolver.meet(plugin.languageManager, player, target, worldData, isMember))
                MenuActionResult.Rejected()
            }
        }
    }

    private fun resolveTargetAction(viewer: Player, target: Player): TargetAction? {
        val stats = plugin.playerStatsRepository.findByUuid(target.uniqueId)
        if (stats.meetStatus == "BUSY" || target.world.uid == viewer.world.uid) return null
        if (stats.meetStatus == "ASK_ME") return TargetAction.REQUEST
        val worldData = plugin.worldConfigRepository.findByWorldName(target.world.name) ?: return TargetAction.DENY
        val isMember = worldData.owner == viewer.uniqueId ||
            worldData.moderators.contains(viewer.uniqueId) ||
            worldData.members.contains(viewer.uniqueId)
        return when (MyWorldManagerApi.getWorldAccessPolicy().getMeetTargetAction(viewer, target, worldData, isMember)) {
            MeetTargetAction.DIRECT -> TargetAction.DIRECT
            MeetTargetAction.REQUEST -> TargetAction.REQUEST
            MeetTargetAction.DENY -> TargetAction.DENY
        }
    }

    private fun sendMeetRequest(player: Player, target: Player) {
        val result = plugin.pendingDecisionManager.enqueueMeetRequest(target, player.uniqueId, target.world.uid, 60)
        player.sendMessage(
            plugin.languageManager.getMessage(
                player,
                "general.meet_request.sent",
                mapOf("player" to target.name),
            ),
        )
        plugin.pendingNotificationService.send(
            target,
            me.awabi2048.myworldmanager.service.PendingDecisionManager.PendingType.MEET_REQUEST,
            result.actionCode,
            player.uniqueId,
            null,
        )
    }

    private fun createEmptyItem(viewer: Player): ItemStack {
        val item = ItemStack(Material.QUARTZ)
        val meta = item.itemMeta ?: return item
        meta.displayName(
            plugin.languageManager.getComponent(viewer, "gui.meet.empty_message")
        )
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_INFO)
        return item
    }

    private fun createTargetHead(target: Player, viewer: Player, plugin: MyWorldManager): ItemStack {
        val lang = plugin.languageManager
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as? org.bukkit.inventory.meta.SkullMeta ?: return item
        meta.owningPlayer = target

        meta.displayName(GuiItemFactory.legacy("§f${target.name}"))

        val information = mutableListOf<GuiLoreLine>()

        // Status
        val stats = plugin.playerStatsRepository.findByUuid(target.uniqueId)
        val statusKey = "general.status.${stats.meetStatus.lowercase()}"
        val statusName = if (lang.hasKey(viewer, statusKey)) lang.getMessage(viewer, statusKey) else stats.meetStatus
        information.add(GuiLoreLine.Data(lang.getMessage(viewer, "gui.meet.world_item.status"), statusName, "§e"))
        information.add(GuiLoreLine.Data(
            lang.getMessage(viewer, "gui.meet.world_item.online_state"),
            lang.getMessage(viewer, if (target.isOnline) "gui.meet.world_item.online" else "gui.meet.world_item.offline"),
            if (target.isOnline) "§a" else "§8"
        ))

        // 現在のワールド名取得
        val world = target.world
        val worldName = world.name
        val worldData = plugin.worldConfigRepository.findByWorldName(worldName)
        val isSameWorld = target.world.uid == viewer.world.uid
        val displayWorldName = worldData?.name ?: run {
            val configMap = plugin.config.getConfigurationSection("world_display_names")
            configMap?.getString(worldName) ?: "???"
        }

        val worldValue = if (isSameWorld) {
            "$displayWorldName (${lang.getMessage(viewer, "gui.meet.world_item.same_world")})"
        } else {
            displayWorldName
        }
        information.add(GuiLoreLine.Data(
            lang.getMessage(viewer, "gui.meet.world_item.current_world"),
            worldValue,
            if (isSameWorld) "§6" else "§f"
        ))

        // クリックしてワールドを訪れる/申請の表示判定
        var action: String? = null
        if (worldData != null && !isSameWorld) {
            val isMember = worldData.owner == viewer.uniqueId ||
                           worldData.moderators.contains(viewer.uniqueId) ||
                           worldData.members.contains(viewer.uniqueId)

            // Logic based on status
            if (stats.meetStatus == "JOIN_ME") {
                when (MyWorldManagerApi.getWorldAccessPolicy().getMeetTargetAction(viewer, target, worldData, isMember)) {
                    MeetTargetAction.DIRECT -> action = lang.getMessage(viewer, "gui.meet.world_item.click_visit")
                    MeetTargetAction.REQUEST -> action = lang.getMessage(viewer, "gui.meet.world_item.click_request")
                    MeetTargetAction.DENY -> Unit
                }
            } else if (stats.meetStatus == "ASK_ME") {
                // Request needed
                action = lang.getMessage(viewer, "gui.meet.world_item.click_request")
            }
        }

        val lore = CCSystem.getAPI().getLoreService().render(GuiLoreSpec.Blocks(buildList {
            add(GuiLoreBlock(information))
            action?.let {
                add(GuiLoreBlock(listOf(GuiLoreLine.Action(lang.getMessage(viewer, "lore.click.any"), it))))
            }
        }))
        meta.lore(lore)
        item.itemMeta = meta

        // タグ付け
        ItemTag.tagItem(item, "gui_meet_target_head")
        return item
    }

    private enum class TargetAction {
        DIRECT,
        REQUEST,
        DENY,
    }

    companion object {
        private const val OWNER = "myworldmanager"
        private const val ROUTE_ID = "meet"
        private const val PAGE = "page"
        private const val TARGET_UUID = "target_uuid"
        private const val ACTION_PAGE = "page"
        private const val ACTION_STATUS = "status"
        private const val ACTION_BACK = "back"
        private const val ACTION_TARGET = "target"
    }
}
