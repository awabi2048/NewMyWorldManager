package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.api.localization.generated.CommonKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldGuiCommonKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldGuiPortalKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldMessagesKeys

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiInteractionGuidance
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiMenuActionIntent
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryData
import com.awabi2048.ccsystem.api.gui.GuiMenuEntrySpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
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
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import me.awabi2048.myworldmanager.util.WorldAccessMessageResolver
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player

class VisitGui(private val plugin: MyWorldManager) {
        private val runtime = CCSystem.getAPI().getMenuRuntimeService()

        init {
                runtime.register(
                        InventoryMenuDefinition(
                                owner = OWNER,
                                id = ROUTE_ID,
                                renderer = { context -> render(context.player, context.route) },
                                actions = mapOf(
                                        ACTION_BACK to MenuActionHandler(::back),
                                        ACTION_PAGE to MenuActionHandler(::page),
                                        ACTION_WORLD to MenuActionHandler(::world),
                                ),
                        ),
                )
        }

        fun open(
                player: Player,
                targetPlayer: OfflinePlayer,
                page: Int = 0,
                returnToWorld: WorldData? = null,
                guestAccessibleOnly: Boolean = false,
        ) {
                val targetRoute = route(targetPlayer.uniqueId, page, returnToWorld?.uuid, guestAccessibleOnly)
                if (returnToWorld != null) {
                        runtime.navigate(player, targetRoute)
                } else {
                        runtime.open(player, targetRoute)
                }
        }

        private fun render(player: Player, route: MenuRoute): InventoryMenuView {
                val targetPlayerUuid = route.uuid(TARGET_PLAYER_UUID) ?: player.uniqueId
                val targetPlayer = Bukkit.getOfflinePlayer(targetPlayerUuid)
                val returnWorldUuid = route.uuid(RETURN_WORLD_UUID)
                val guestAccessibleOnly = route.payload[GUEST_ACCESSIBLE_ONLY].toBoolean()
                val requestedPage = route.payload[PAGE]?.toIntOrNull() ?: 0
                val allWorlds = plugin.worldConfigRepository.findAll()
                val targetWorlds =
                        allWorlds.filter { world ->
                                if (world.owner != targetPlayerUuid || world.isArchived || world.uuid == returnWorldUuid)
                                        return@filter false

                                if (guestAccessibleOnly) {
                                        // 自分の「他のワールド」を見る場合も、第三者が一覧へ到達できる公開条件で絞ります。
                                        return@filter MyWorldManagerApi.getWorldAccessPolicy()
                                                .canShowInGuestAccessibleWorldList(player, world)
                                }

                                val isMember =
                                        world.owner == player.uniqueId ||
                                                world.moderators.contains(player.uniqueId) ||
                                                world.members.contains(player.uniqueId)

                                MyWorldManagerApi.getWorldAccessPolicy().canUseVisitEntry(player, world, isMember)
                        }

                val worldCount = targetWorlds.size
                val lang = plugin.languageManager
                val pageLayout = CCSystem.getAPI().getGuiLayoutService().sevenColumnPage(worldCount, requestedPage)
                val currentPage = pageLayout.page
                val layout = pageLayout.layout
                val targetName = PlayerNameUtil.getNameOrDefault(targetPlayerUuid, "Unknown")
                val titleComp = me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(lang.getComponent(player, MyworldGuiPortalKeys.GUI_VISIT_TITLE, mapOf("player" to targetName)))
                val elements = mutableListOf<MenuElement>()
                targetWorlds.drop(pageLayout.startIndex).take(pageLayout.itemCount).forEachIndexed { index, worldData ->
                        elements += createWorldEntry(player, worldData, layout.itemSlots[index])
                }

                if (GuiHelper.canGoBack(player)) {
                        elements += backEntry(player, layout.backSlot)
                }
                if (currentPage > 0) {
                        elements += navigationEntry(player, layout.previousPageSlot, false, currentPage - 1)
                }
                if (currentPage < pageLayout.totalPages - 1) {
                        elements += navigationEntry(player, layout.nextPageSlot, true, currentPage + 1)
                }

                return InventoryMenuView(layout.size, titleComp, elements)
        }

        private fun back(context: MenuActionContext): MenuActionResult {
                return MenuActionResult.Success(MenuUpdate.Back)
        }

        private fun page(context: MenuActionContext): MenuActionResult {
                val target = context.payload[PAGE]?.toIntOrNull() ?: return MenuActionResult.Rejected()
                val targetPlayerUuid = context.route.uuid(TARGET_PLAYER_UUID)
                        ?: return MenuActionResult.Rejected()
                return MenuActionResult.Success(
                        MenuUpdate.Replace(
                                route(
                                        targetPlayerUuid,
                                        target,
                                        context.route.uuid(RETURN_WORLD_UUID),
                                        context.route.payload[GUEST_ACCESSIBLE_ONLY].toBoolean(),
                                ),
                        ),
                )
        }

        private fun world(context: MenuActionContext): MenuActionResult {
                val player = context.player
                val worldUuid = context.payload[WORLD_UUID]
                        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: return MenuActionResult.Rejected()
                val worldData = plugin.worldConfigRepository.findByUuid(worldUuid)
                val isMember = worldData != null && (
                        worldData.owner == player.uniqueId ||
                                worldData.moderators.contains(player.uniqueId) ||
                                worldData.members.contains(player.uniqueId)
                        )
                if (worldData == null || !MyWorldManagerApi.getWorldAccessPolicy().canUseVisitEntry(player, worldData, isMember)) {
                        player.sendMessage(
                                WorldAccessMessageResolver.visit(
                                        plugin.languageManager,
                                        player,
                                        worldData,
                                        isMember,
                                ),
                        )
                        return MenuActionResult.Rejected()
                }
                if (context.click.isLeftClick) {
                        plugin.worldService.teleportToWorld(player, worldUuid) {
                                player.sendMessage(
                                        plugin.languageManager.getMessage(
                                                player,
                                                MyworldMessagesKeys.MESSAGES_WARP_SUCCESS,
                                                mapOf("world" to worldData.name),
                                        ),
                                )
                        }
                        return MenuActionResult.Success(MenuUpdate.Close)
                }
                if (!context.click.isRightClick || isMember) return MenuActionResult.Ignored

                return when (plugin.favoriteStateService.toggle(player, worldData)) {
                        me.awabi2048.myworldmanager.service.FavoriteStateService.ToggleResult.Removed -> {
                        player.sendMessage(plugin.languageManager.getMessage(player, MyworldMessagesKeys.MESSAGES_FAVORITE_REMOVED))
                                MenuActionResult.Success(MenuUpdate.Refresh)
                        }
                        me.awabi2048.myworldmanager.service.FavoriteStateService.ToggleResult.Added -> {
                                player.sendMessage(plugin.languageManager.getMessage(player, MyworldMessagesKeys.MESSAGES_FAVORITE_ADDED))
                                MenuActionResult.Success(MenuUpdate.Refresh)
                        }
                        me.awabi2048.myworldmanager.service.FavoriteStateService.ToggleResult.LimitReached -> {
                        val maxFavoriteCount = plugin.config.getInt("favorite.max_count", 1000)
                                player.sendMessage(
                                        plugin.languageManager.getMessage(
                                                player,
                                                CommonKeys.ERROR_FAVORITE_LIMIT_REACHED,
                                                mapOf("limit" to maxFavoriteCount),
                                        ),
                                )
                                MenuActionResult.Rejected()
                        }
                }
        }

        private fun createWorldEntry(viewer: Player, world: WorldData, slot: Int): MenuElement {
                val lang = plugin.languageManager
                val ownerName = PlayerNameUtil.getNameOrDefault(world.owner, lang.getMessage(viewer, CommonKeys.GENERAL_UNKNOWN))
                val tagNames = if (world.tags.isNotEmpty()) {
                        world.tags.joinToString(", ") {
                                plugin.worldTagManager.getDisplayName(viewer, it)
                        }
                } else null

                val warpAction = lang.getMessage(viewer, MyworldGuiPortalKeys.GUI_VISIT_WORLD_ITEM_WARP)

                val stats = plugin.playerStatsRepository.findByUuid(viewer.uniqueId)
                val viewerPlayerUuid = viewer.uniqueId
                val isMember = world.owner == viewerPlayerUuid ||
                                world.moderators.contains(viewerPlayerUuid) ||
                                world.members.contains(viewerPlayerUuid)

                val favoriteAction = if (!isMember) {
                        if (stats.favoriteWorlds.containsKey(world.uuid)) {
                                lang.getMessage(viewer, MyworldGuiPortalKeys.GUI_VISIT_WORLD_ITEM_FAV_REMOVE)
                        } else {
                                lang.getMessage(viewer, MyworldGuiPortalKeys.GUI_VISIT_WORLD_ITEM_FAV_ADD)
                        }
                } else ""

                return CCSystem.getAPI().getGuiElementService().menuEntry(
                        viewer,
                        GuiMenuEntrySpec(
                                slot = slot,
                                material = world.icon,
                                name = GuiNameSpec.TargetIdentity(
                                        lang.getComponent(viewer, MyworldGuiCommonKeys.GUI_COMMON_WORLD_ITEM_NAME, mapOf("world" to world.name)),
                                ),
                                role = GuiElementRole.ACTION,
                                description = listOfNotNull(world.description.takeIf(String::isNotBlank)),
                                data = buildList {
                                        add(GuiMenuEntryData(lang.getMessage(viewer, MyworldGuiCommonKeys.GUI_COMMON_WORLD_ITEM_OWNER), ownerName, GuiValueTone.INFO))
                                        add(GuiMenuEntryData(lang.getMessage(viewer, MyworldGuiCommonKeys.GUI_COMMON_WORLD_ITEM_FAVORITE), world.favorite, GuiValueTone.DANGER))
                                        add(GuiMenuEntryData(
                                                lang.getMessage(viewer, MyworldGuiCommonKeys.GUI_COMMON_WORLD_ITEM_RECENT_VISITORS),
                                                lang.getMessage(viewer, MyworldGuiCommonKeys.GUI_COMMON_WORLD_ITEM_RECENT_VISITORS_VALUE, mapOf("count" to world.recentVisitors.sum())),
                                                GuiValueTone.SUCCESS,
                                        ))
                                        tagNames?.let { add(GuiMenuEntryData(lang.getMessage(viewer, MyworldGuiCommonKeys.GUI_COMMON_WORLD_ITEM_TAGS), it, GuiValueTone.PRIMARY)) }
                                },
                                actions = buildList {
                                        add(menuGestureAction(ACTION_WORLD, MenuGesture.LEFT, warpAction, mapOf(WORLD_UUID to world.uuid.toString()), safety = MenuActionSafety.EXTERNAL_SIDE_EFFECT))
                                        if (favoriteAction.isNotBlank()) {
                                                add(menuGestureAction(ACTION_WORLD, MenuGesture.RIGHT, favoriteAction, mapOf(WORLD_UUID to world.uuid.toString()), safety = MenuActionSafety.REVERSIBLE, reversibleContract = MwmMenuActionSemantics.contract("visit-favorite")))
                                        }
                                },
                        ),
                )
        }

        private fun backEntry(player: Player, slot: Int): MenuElement =
                CCSystem.getAPI().getGuiElementService().backEntry(
                        player,
                        slot,
                        plugin.menuConfigManager.getIconMaterial("world_settings", "back", Material.REDSTONE),
                )

        private fun navigationEntry(player: Player, slot: Int, next: Boolean, targetPage: Int): MenuElement {
                val key = if (next) "gui.common.next_page" else "gui.common.prev_page"
                val iconId = if (next) "next_page" else "prev_page"
                return CCSystem.getAPI().getGuiElementService().menuEntry(
                        player,
                        GuiMenuEntrySpec(
                                slot = slot,
                                material = plugin.menuConfigManager.getIconMaterial("visit", iconId, Material.ARROW),
                                name = GuiNameSpec.FixedLabel(plugin.languageManager.getComponent(player, key)),
                                role = GuiElementRole.NAVIGATION,
                                actions = listOf(
                                        menuGestureAction(
                                                ACTION_PAGE,
                                                MenuGesture.LEFT_RIGHT,
                                                plugin.languageManager.getMessage(player, key),
                                                mapOf(PAGE to targetPage.toString()),
                                                safety = MenuActionSafety.NAVIGATION_ONLY,
                                        ),
                                ),
                                interactionGuidance = GuiInteractionGuidance.SINGLE_ACTION_CLICK,
                        ),
                )
        }

        private fun route(
                targetPlayerUuid: UUID,
                page: Int,
                returnWorldUuid: UUID?,
                guestAccessibleOnly: Boolean,
        ) =
                MenuRoute(
                        OWNER,
                        ROUTE_ID,
                        buildMap {
                                put(TARGET_PLAYER_UUID, targetPlayerUuid.toString())
                                put(PAGE, page.toString())
                            returnWorldUuid?.let { put(RETURN_WORLD_UUID, it.toString()) }
                            put(GUEST_ACCESSIBLE_ONLY, guestAccessibleOnly.toString())
                        },
                )

        private fun MenuRoute.uuid(key: String): UUID? =
                payload[key]?.let { runCatching { UUID.fromString(it) }.getOrNull() }

        companion object {
                private const val OWNER = "myworldmanager"
                private const val ROUTE_ID = "visit"
                private const val TARGET_PLAYER_UUID = "target_player_uuid"
                private const val RETURN_WORLD_UUID = "return_world_uuid"
                private const val WORLD_UUID = "world_uuid"
                private const val PAGE = "page"
                private const val GUEST_ACCESSIBLE_ONLY = "guest_accessible_only"
                private const val ACTION_BACK = "back"
                private const val ACTION_PAGE = "page"
                private const val ACTION_WORLD = "world"
        }
}
