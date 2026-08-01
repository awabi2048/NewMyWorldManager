package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiCycle
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiMenuDisplaySpec
import com.awabi2048.ccsystem.api.gui.GuiMenuActionIntent
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryData
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryOption
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
import me.awabi2048.myworldmanager.model.PortalData
import me.awabi2048.myworldmanager.session.AdminGuiSession
import me.awabi2048.myworldmanager.session.PortalSortType
import me.awabi2048.myworldmanager.session.SettingsAction
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.ItemTag
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack

class AdminPortalGui(private val plugin: MyWorldManager) {
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
                    ACTION_SORT to MenuActionHandler(::sort),
                    ACTION_PORTAL to MenuActionHandler(::portal),
                ),
            ),
        )
    }

    fun open(player: Player, page: Int? = null, fromAdminMenu: Boolean? = null) {
        runtime.navigate(player, prepareOpen(player, page, fromAdminMenu))
    }

    fun prepareOpen(player: Player, page: Int? = null, fromAdminMenu: Boolean? = null): MenuRoute {
        val session = plugin.adminGuiSessionManager.getSession(player.uniqueId)
        if (fromAdminMenu != null) session.fromAdminMenu = fromAdminMenu
        val currentPage = page ?: session.portalPage
        plugin.settingsSessionManager.updateSessionAction(
            player,
            player.uniqueId,
            SettingsAction.ADMIN_PORTAL_GUI,
            isGui = true,
        )
        return route(currentPage)
    }

    private fun render(player: Player, route: MenuRoute): InventoryMenuView {
        val session = plugin.adminGuiSessionManager.getSession(player.uniqueId)
        val portals = getSortedPortals(session)
        val layout = GuiHelper.pagedListLayout()
        val totalPages = ((portals.size + layout.itemSlots.size - 1) / layout.itemSlots.size).coerceAtLeast(1)
        val safePage = route.payload[PAGE]?.toIntOrNull()?.coerceIn(0, totalPages - 1) ?: 0
        session.portalPage = safePage
        val elements = mutableListOf<MenuElement>()
        portals.drop(safePage * layout.itemSlots.size).take(layout.itemSlots.size).forEachIndexed { index, portal ->
            elements += createPortalEntry(player, portal, layout.itemSlots[index])
        }
        if (safePage > 0) {
            elements += navigationEntry(player, 46, false, safePage - 1)
        }
        elements += createInfoEntry(player, portals.size, safePage + 1, totalPages)
        elements += createSortEntry(player, session, 51)
        if (GuiHelper.canGoBack(player)) {
            elements += backEntry(player, 52)
        }
        if (safePage < totalPages - 1) {
            elements += navigationEntry(player, 53, true, safePage + 1)
        }
        return InventoryMenuView(
            layout.size,
            GuiHelper.inventoryTitle(plugin.languageManager.getComponent(player, "gui.admin_portals.title")),
            elements,
        )
    }

    private fun back(context: MenuActionContext): MenuActionResult {
        return MenuActionResult.Success(MenuUpdate.Back)
    }

    private fun page(context: MenuActionContext): MenuActionResult {
        val target = context.payload[PAGE]?.toIntOrNull() ?: return MenuActionResult.Rejected()
        return MenuActionResult.Success(MenuUpdate.Replace(route(target)))
    }

    private fun sort(context: MenuActionContext): MenuActionResult {
        val direction = GuiCycle.direction(context.click) ?: return MenuActionResult.Ignored
        plugin.adminGuiSessionManager.cyclePortalSortType(context.player.uniqueId, direction)
        return MenuActionResult.Success(MenuUpdate.Refresh)
    }

    private fun portal(context: MenuActionContext): MenuActionResult {
        val portalId = context.payload[PORTAL_ID]
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: return MenuActionResult.Rejected()
        val portal = plugin.portalRepository.findAll().find { it.id == portalId }
            ?: return MenuActionResult.Rejected()
        return when {
            context.click.isLeftClick -> teleport(context.player, portal)
            context.click.isRightClick -> remove(context.player, portal)
            else -> MenuActionResult.Ignored
        }
    }

    private fun teleport(player: Player, portal: PortalData): MenuActionResult {
        val lang = plugin.languageManager
        plugin.portalManager.addIgnorePlayer(player)
        plugin.portalManager.addPortalGrace(player, portal.id, 15)
        if (portal.worldUuid != null) {
            val destination = plugin.worldConfigRepository.findByUuid(portal.worldUuid!!)
            if (destination != null && Bukkit.getWorld(plugin.worldService.getWorldFolderName(destination)) == null) {
                player.sendMessage(lang.getMessage(player, "messages.world_loading"))
            }
            plugin.worldService.teleportToWorld(
                player,
                portal.worldUuid!!,
                portal.getCenterLocation(),
                runMacro = false,
            ) {
                player.sendMessage(lang.getMessage(player, "messages.admin_portal_teleport"))
            }
        } else {
            val target = portal.targetRuntimeName ?: return MenuActionResult.Rejected()
            if (!plugin.portalManager.teleportPlayerToWorldSpawn(player, target) {
                    player.sendMessage(lang.getMessage(player, "messages.admin_portal_teleport"))
                }
            ) {
                player.sendMessage(lang.getMessage(player, "general.world_not_found"))
                return MenuActionResult.Rejected()
            }
        }
        return MenuActionResult.Success(MenuUpdate.Close)
    }

    private fun remove(player: Player, portal: PortalData): MenuActionResult {
        val refund = if (portal.isGate()) plugin.portalManager.refundPointsForRemovedGate(portal) else null
        plugin.portalManager.removePortalVisuals(portal.id)
        plugin.portalRepository.removePortal(portal.id)
        if (!portal.isGate()) {
            portal.loadedWorld()?.getBlockAt(portal.x, portal.y, portal.z)
                ?.takeIf { it.type == Material.END_PORTAL_FRAME }
                ?.setType(Material.AIR)
        }
        val lang = plugin.languageManager
        if (portal.isGate()) {
            player.sendMessage(
                lang.getMessage(
                    player,
                    "messages.world_gate_removed_refund",
                    mapOf(
                        "points" to (refund?.points ?: 0),
                        "percent" to (refund?.percent ?: 0),
                        "owner" to (Bukkit.getOfflinePlayer(portal.ownerUuid).name ?: portal.ownerUuid.toString()),
                    ),
                ),
            )
        }
        player.sendMessage(lang.getMessage(player, "messages.admin_portal_removed"))
        return MenuActionResult.Success(MenuUpdate.Refresh)
    }

    private fun getSortedPortals(session: AdminGuiSession): List<PortalData> =
        when (session.portalSortBy) {
            PortalSortType.CREATED_DESC -> plugin.portalRepository.findAll().sortedByDescending { it.createdAt }
            PortalSortType.CREATED_ASC -> plugin.portalRepository.findAll().sortedBy { it.createdAt }
        }

    private fun createPortalEntry(player: Player, portal: PortalData, slot: Int): MenuElement {
        val lang = plugin.languageManager
        val destination = portal.worldUuid
            ?.let(plugin.worldConfigRepository::findByUuid)
            ?.name
            ?: plugin.config.getString("portal_targets.${portal.targetRuntimeName}")
            ?: portal.targetRuntimeName
            ?: "Unknown"
        val payload = mapOf(PORTAL_ID to portal.id.toString())
        return CCSystem.getAPI().getGuiElementService().menuEntry(
            player,
            GuiMenuEntrySpec(
                slot = slot,
                material = Material.END_PORTAL_FRAME,
                name = GuiNameSpec.Text(
                    lang.getMessage(player, "gui.admin_portals.portal_item.name", mapOf("id" to destination)),
                    com.awabi2048.ccsystem.api.gui.GuiNameStyle.DEFAULT,
                ),
                role = GuiElementRole.ACTION,
                data = listOf(
                    GuiMenuEntryData(lang.getMessage(player, "gui.admin_portals.portal_item.owner"), PlayerNameUtil.getNameOrDefault(portal.ownerUuid, "Unknown")),
                    GuiMenuEntryData(lang.getMessage(player, "gui.admin_portals.portal_item.world"), portal.worldKey),
                    GuiMenuEntryData(lang.getMessage(player, "gui.admin_portals.portal_item.coordinates"), "${portal.x}, ${portal.y}, ${portal.z}"),
                ),
                actions = listOf(
                    menuGestureAction(ACTION_PORTAL, MenuGesture.PLAIN_LEFT, lang.getMessage(player, "gui.admin_portals.portal_item.action.teleport"), payload, safety = MenuActionSafety.EXTERNAL_SIDE_EFFECT),
                    menuGestureAction(ACTION_PORTAL, MenuGesture.PLAIN_RIGHT, lang.getMessage(player, "gui.admin_portals.portal_item.action.remove"), payload, safety = MenuActionSafety.IRREVERSIBLE),
                ),
            ),
        )
    }

    private fun navigationEntry(player: Player, slot: Int, next: Boolean, targetPage: Int): MenuElement {
        val key = if (next) "gui.common.next_page" else "gui.common.prev_page"
        return CCSystem.getAPI().getGuiElementService().menuEntry(
            player,
            GuiMenuEntrySpec(
                slot = slot,
                material = Material.ARROW,
                name = GuiNameSpec.Component(plugin.languageManager.getComponent(player, key)),
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
            ),
        )
    }

    private fun backEntry(player: Player, slot: Int): MenuElement =
        CCSystem.getAPI().getGuiElementService().backEntry(
            player,
            slot,
            plugin.menuConfigManager.getIconMaterial("world_settings", "back", Material.REDSTONE),
        )

    private fun createInfoEntry(player: Player, totalCount: Int, current: Int, total: Int): MenuElement =
        CCSystem.getAPI().getGuiElementService().menuDisplay(
            GuiMenuDisplaySpec(
                slot = 49,
                item = GuiItemSpec(
                    material = Material.PAPER,
                    name = GuiNameSpec.Component(plugin.languageManager.getComponent(player, "gui.admin.info.display")),
                    lore = GuiLoreSpec.Blocks(
                        listOf(
                            GuiLoreBlock(
                                listOf(
                                    GuiLoreLine.Data(plugin.languageManager.getMessage(player, "gui.admin.info.total_count_label"), totalCount, "§b"),
                                    GuiLoreLine.Data(plugin.languageManager.getMessage(player, "gui.admin.info.page_label"), "$current/$total", "§a"),
                                ),
                            ),
                        ),
                    ),
                    role = GuiElementRole.CONTENT,
                    amount = 1,
                ),
            ),
        )

    private fun createSortEntry(player: Player, session: AdminGuiSession, slot: Int): MenuElement {
        val lang = plugin.languageManager
        val options = PortalSortType.entries.map { it to lang.getMessage(player, it.displayKey) }
        return CCSystem.getAPI().getGuiElementService().menuEntry(
            player,
            GuiMenuEntrySpec(
                slot = slot,
                material = Material.HOPPER,
                name = GuiNameSpec.Component(lang.getComponent(player, "gui.admin_portals.sort.display")),
                role = GuiElementRole.ACTION,
                data = listOf(
                    GuiMenuEntryData(
                        lang.getMessage(player, "gui.admin_portals.sort.label"),
                        options.first { it.first == session.portalSortBy }.second,
                        GuiValueTone.PRIMARY,
                    ),
                ),
                options = options.map { (type, name) -> GuiMenuEntryOption(name, type == session.portalSortBy) },
                actions = listOf(
                    menuGestureAction(
                        ACTION_SORT,
                        MenuGesture.ANY,
                        lang.getMessage(player, "gui.common.action.cycle"),
                        safety = MenuActionSafety.REVERSIBLE,
                    ),
                ),
            ),
        )
    }

    private fun route(page: Int) = MenuRoute(OWNER, ROUTE_ID, mapOf(PAGE to page.toString()))

    private companion object {
        const val OWNER = "mwm"
        const val ROUTE_ID = "admin_portals"
        const val PAGE = "page"
        const val PORTAL_ID = "portal_id"
        const val ACTION_BACK = "back"
        const val ACTION_PAGE = "page"
        const val ACTION_SORT = "sort"
        const val ACTION_PORTAL = "portal"
    }
}
