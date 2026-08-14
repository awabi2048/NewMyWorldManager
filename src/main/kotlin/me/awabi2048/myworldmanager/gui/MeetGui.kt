package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.api.localization.generated.MyworldMessagesKeys

import com.awabi2048.ccsystem.api.localization.generated.CommonKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldGuiMeetKeys

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiInteractionGuidance
import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiMenuDisplaySpec
import com.awabi2048.ccsystem.api.gui.GuiMenuActionIntent
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryData
import com.awabi2048.ccsystem.api.gui.GuiMenuEntrySpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiNameStyle
import com.awabi2048.ccsystem.api.gui.GuiValueTone
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuActionContext
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuActionSafety
import com.awabi2048.ccsystem.api.gui.MenuGesture
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import java.util.UUID
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.api.extension.MeetTargetAction
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.WorldAccessMessageResolver
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player

class MeetGui(private val plugin: MyWorldManager) {
    private val runtime = CCSystem.getAPI().getMenuRuntimeService()

    // 1行7アイコン中央寄せのレイアウト (スロット 10~16, 19~25, ...)
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
        val pageLayout = CCSystem.getAPI().getGuiLayoutService()
            .sevenColumnPage(targets.size, route.payload[PAGE]?.toIntOrNull() ?: session.currentPage)
        val currentPage = pageLayout.page
        val layout = pageLayout.layout
        session.currentPage = currentPage
        val title = GuiHelper.inventoryTitle(lang.getMessage(player, MyworldGuiMeetKeys.GUI_MEET_TITLE_LIST))
        val elements = mutableListOf<MenuElement>()
        val pageTargets = targets.drop(pageLayout.startIndex).take(pageLayout.itemCount)
        pageTargets.forEachIndexed { index, target ->
            val action = resolveTargetAction(player, target)
            elements += createTargetEntry(target, player, layout.itemSlots[index], action)
        }
        if (pageTargets.isEmpty()) {
            elements += createEmptyEntry(player)
        }

        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        val currentStatus = stats.meetStatus
        val statusNameKey = "general.status.${currentStatus.lowercase()}"
        val statusName = if (lang.hasKey(player, statusNameKey)) lang.getMessage(player, statusNameKey) else currentStatus
        elements += CCSystem.getAPI().getGuiElementService().menuEntry(
            player,
            GuiMenuEntrySpec(
                slot = layout.actionSlot,
                material = Material.PLAYER_HEAD,
                name = GuiNameSpec.FixedLabel(lang.getComponent(player, MyworldGuiMeetKeys.GUI_MEET_STATUS_BUTTON_DISPLAY, mapOf("player" to player.name))),
                role = GuiElementRole.ACTION,
                description = listOf(lang.getMessage(player, "general.status.description.${currentStatus.lowercase()}")),
                data = listOf(GuiMenuEntryData(lang.getMessage(player, MyworldGuiMeetKeys.GUI_MEET_STATUS_BUTTON_CURRENT), statusName, GuiValueTone.PRIMARY)),
                actions = listOf(menuGestureAction(ACTION_STATUS, MenuGesture.ANY, lang.getMessage(player, MyworldGuiMeetKeys.GUI_MEET_STATUS_BUTTON_ACTION), safety = MenuActionSafety.REVERSIBLE, reversibleContract = MwmMenuActionSemantics.contract("meet-status"))),
                playerHeadOwner = player.uniqueId,
            ),
        )

        if (currentPage > 0) {
            elements += navigationEntry(player, layout.previousPageSlot, false, currentPage - 1)
        }
        if (currentPage < pageLayout.totalPages - 1) {
            elements += navigationEntry(player, layout.nextPageSlot, true, currentPage + 1)
        }
        if (GuiHelper.canGoBack(player)) {
            elements += backEntry(player, layout.backSlot)
        }
        return InventoryMenuView(layout.size, title, elements)
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
                player.sendMessage(plugin.languageManager.getMessage(player, CommonKeys.ERROR_TARGET_OFFLINE))
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
                            MyworldMessagesKeys.MESSAGES_WARP_SUCCESS,
                            mapOf("world" to worldData.name),
                        ),
                    )
                    if (worldData.notificationEnabled && plugin.playerVisibilityService.isVisibleTo(target, player)) {
                        target.sendMessage(
                            plugin.languageManager.getMessage(
                                target,
                                MyworldMessagesKeys.MESSAGES_VISITOR_NOTIFIED,
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
                    player.sendMessage(plugin.languageManager.getMessage(player, CommonKeys.ERROR_TARGET_NOT_IN_MYWORLD))
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
                CommonKeys.GENERAL_MEET_REQUEST_SENT,
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

    private fun createEmptyEntry(viewer: Player): MenuElement =
        CCSystem.getAPI().getGuiElementService().menuDisplay(
            GuiMenuDisplaySpec(
                slot = 22,
                item = GuiItemSpec(
                    material = Material.QUARTZ,
                    name = GuiNameSpec.FixedLabel(plugin.languageManager.getComponent(viewer, MyworldGuiMeetKeys.GUI_MEET_EMPTY_MESSAGE)),
                    lore = GuiLoreSpec.None,
                    role = GuiElementRole.CONTENT,
                    amount = 1,
                ),
            ),
        )

    private fun navigationEntry(viewer: Player, slot: Int, next: Boolean, page: Int): MenuElement {
        val key = if (next) "gui.common.next_page" else "gui.common.prev_page"
        val iconId = if (next) "next_page" else "prev_page"
        return CCSystem.getAPI().getGuiElementService().menuEntry(
            viewer,
            GuiMenuEntrySpec(
                slot = slot,
                material = plugin.menuConfigManager.getIconMaterial("meet", iconId, Material.ARROW),
                name = GuiNameSpec.FixedLabel(plugin.languageManager.getComponent(viewer, key)),
                role = GuiElementRole.NAVIGATION,
                actions = listOf(
                    menuGestureAction(
                        ACTION_PAGE,
                        MenuGesture.LEFT_RIGHT,
                        plugin.languageManager.getMessage(viewer, key),
                        mapOf(PAGE to page.toString()),
                        safety = MenuActionSafety.NAVIGATION_ONLY,
                    ),
                ),
                interactionGuidance = GuiInteractionGuidance.SINGLE_ACTION_CLICK,
            ),
        )
    }

    private fun backEntry(viewer: Player, slot: Int): MenuElement =
        CCSystem.getAPI().getGuiElementService().backEntry(
            viewer,
            slot,
            plugin.menuConfigManager.getIconMaterial("world_settings", "back", Material.REDSTONE),
        )

    private fun createTargetEntry(
        target: Player,
        viewer: Player,
        slot: Int,
        targetAction: TargetAction?,
    ): MenuElement {
        val lang = plugin.languageManager
        val stats = plugin.playerStatsRepository.findByUuid(target.uniqueId)
        val statusKey = "general.status.${stats.meetStatus.lowercase()}"
        val statusName = if (lang.hasKey(viewer, statusKey)) lang.getMessage(viewer, statusKey) else stats.meetStatus
        val world = target.world
        val worldName = world.name
        val worldData = plugin.worldConfigRepository.findByWorldName(worldName)
        val isSameWorld = target.world.uid == viewer.world.uid
        val displayWorldName = worldData?.name ?: run {
            val configMap = plugin.config.getConfigurationSection("world_display_names")
            configMap?.getString(worldName) ?: "???"
        }

        val worldValue = if (isSameWorld) {
            "$displayWorldName (${lang.getMessage(viewer, MyworldGuiMeetKeys.GUI_MEET_WORLD_ITEM_SAME_WORLD)})"
        } else {
            displayWorldName
        }
        val actionLabel = when (targetAction) {
            TargetAction.DIRECT -> lang.getMessage(viewer, MyworldGuiMeetKeys.GUI_MEET_WORLD_ITEM_CLICK_VISIT)
            TargetAction.REQUEST -> lang.getMessage(viewer, MyworldGuiMeetKeys.GUI_MEET_WORLD_ITEM_CLICK_REQUEST)
            TargetAction.DENY, null -> null
        }
        return CCSystem.getAPI().getGuiElementService().menuEntry(
            viewer,
            GuiMenuEntrySpec(
                slot = slot,
                material = Material.PLAYER_HEAD,
                name = me.awabi2048.myworldmanager.util.targetIdentityName(target.name, GuiNameStyle.DEFAULT),
                role = if (actionLabel == null) GuiElementRole.CONTENT else GuiElementRole.ACTION,
                data = listOf(
                    GuiMenuEntryData(
                        lang.getMessage(viewer, MyworldGuiMeetKeys.GUI_MEET_WORLD_ITEM_STATUS),
                        statusName,
                        GuiValueTone.PRIMARY,
                    ),
                    GuiMenuEntryData(
                        lang.getMessage(viewer, MyworldGuiMeetKeys.GUI_MEET_WORLD_ITEM_ONLINE_STATE),
                        lang.getMessage(viewer, if (target.isOnline) MyworldGuiMeetKeys.GUI_MEET_WORLD_ITEM_ONLINE else MyworldGuiMeetKeys.GUI_MEET_WORLD_ITEM_OFFLINE),
                        if (target.isOnline) GuiValueTone.SUCCESS else GuiValueTone.MUTED,
                    ),
                    GuiMenuEntryData(
                        lang.getMessage(viewer, MyworldGuiMeetKeys.GUI_MEET_WORLD_ITEM_CURRENT_WORLD),
                        worldValue,
                        if (isSameWorld) GuiValueTone.WARNING else GuiValueTone.DEFAULT,
                    ),
                ),
                actions = actionLabel?.let {
                    listOf(
                        menuGestureAction(
                            ACTION_TARGET,
                            MenuGesture.ANY,
                            it,
                            mapOf(TARGET_UUID to target.uniqueId.toString()),
                            safety = MenuActionSafety.EXTERNAL_SIDE_EFFECT,
                        ),
                    )
                }.orEmpty(),
                playerHeadOwner = target.uniqueId,
            ),
        )
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
