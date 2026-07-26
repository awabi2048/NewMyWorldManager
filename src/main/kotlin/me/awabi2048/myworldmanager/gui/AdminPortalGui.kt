package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiCycle
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
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
import me.awabi2048.myworldmanager.model.PortalData
import me.awabi2048.myworldmanager.session.AdminGuiSession
import me.awabi2048.myworldmanager.session.PortalSortType
import me.awabi2048.myworldmanager.session.SettingsAction
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.GuiItemFactory
import me.awabi2048.myworldmanager.util.GuiLoreActions
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
        val session = plugin.adminGuiSessionManager.getSession(player.uniqueId)
        if (fromAdminMenu != null) session.fromAdminMenu = fromAdminMenu
        val currentPage = page ?: session.portalPage
        plugin.settingsSessionManager.updateSessionAction(
            player,
            player.uniqueId,
            SettingsAction.ADMIN_PORTAL_GUI,
            isGui = true,
        )
        runtime.navigate(player, route(currentPage))
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
            elements += MenuElement(
                layout.itemSlots[index],
                createPortalItem(player, portal),
                GuiElementRole.ACTION,
                ACTION_PORTAL,
                mapOf(PORTAL_ID to portal.id.toString()),
            )
        }
        if (safePage > 0) {
            elements += MenuElement(
                46,
                createNavButton(player, false),
                GuiElementRole.NAVIGATION,
                ACTION_PAGE,
                mapOf(PAGE to (safePage - 1).toString()),
            )
        }
        elements += MenuElement(49, createInfoButton(player, portals.size, safePage + 1, totalPages), GuiElementRole.CONTENT)
        elements += MenuElement(51, createSortButton(player, session), GuiElementRole.ACTION, ACTION_SORT)
        if (session.fromAdminMenu) {
            elements += MenuElement(52, createBackButton(player), GuiElementRole.BACK, ACTION_BACK)
        }
        if (safePage < totalPages - 1) {
            elements += MenuElement(
                53,
                createNavButton(player, true),
                GuiElementRole.NAVIGATION,
                ACTION_PAGE,
                mapOf(PAGE to (safePage + 1).toString()),
            )
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

    private fun createPortalItem(player: Player, portal: PortalData): ItemStack {
        val lang = plugin.languageManager
        val destination = portal.worldUuid
            ?.let(plugin.worldConfigRepository::findByUuid)
            ?.name
            ?: plugin.config.getString("portal_targets.${portal.targetRuntimeName}")
            ?: portal.targetRuntimeName
            ?: "Unknown"
        val item = ItemStack(Material.END_PORTAL_FRAME)
        item.editMeta { meta ->
            meta.displayName(
                GuiItemFactory.legacy(
                    lang.getMessage(player, "gui.admin_portals.portal_item.name", mapOf("id" to destination)),
                ),
            )
            meta.lore(
                CCSystem.getAPI().getLoreService().render(
                    GuiLoreSpec.Blocks(
                        listOf(
                            GuiLoreBlock(
                                listOf(
                                    GuiLoreLine.Data(
                                        lang.getMessage(player, "gui.admin_portals.portal_item.owner"),
                                        PlayerNameUtil.getNameOrDefault(portal.ownerUuid, "Unknown"),
                                        "§f",
                                    ),
                                    GuiLoreLine.Data(lang.getMessage(player, "gui.admin_portals.portal_item.world"), portal.worldKey, "§f"),
                                    GuiLoreLine.Data(
                                        lang.getMessage(player, "gui.admin_portals.portal_item.coordinates"),
                                        "${portal.x}, ${portal.y}, ${portal.z}",
                                        "§f",
                                    ),
                                ),
                            ),
                            GuiLoreBlock(
                                listOf(
                                    GuiLoreLine.Action(
                                        lang.getMessage(player, "gui.settings.click.left"),
                                        lang.getMessage(player, "gui.admin_portals.portal_item.action.teleport"),
                                    ),
                                    GuiLoreLine.Action(
                                        lang.getMessage(player, "gui.settings.click.right"),
                                        lang.getMessage(player, "gui.admin_portals.portal_item.action.remove"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }
        return item
    }

    private fun createNavButton(player: Player, next: Boolean): ItemStack =
        ItemStack(Material.ARROW).apply {
            editMeta { it.displayName(GuiItemFactory.legacy(plugin.languageManager.getMessage(player, if (next) "gui.common.next_page" else "gui.common.prev_page"))) }
        }

    private fun createBackButton(player: Player): ItemStack =
        ItemStack(Material.REDSTONE).apply {
            editMeta { it.displayName(GuiItemFactory.legacy(plugin.languageManager.getMessage(player, "gui.common.back"))) }
        }

    private fun createInfoButton(player: Player, totalCount: Int, current: Int, total: Int): ItemStack =
        ItemStack(Material.PAPER).apply {
            editMeta { meta ->
                meta.displayName(plugin.languageManager.getComponent(player, "gui.admin.info.display"))
                meta.lore(
                    GuiItemFactory.menuLore(
                        listOf(
                            GuiLoreLine.Data(plugin.languageManager.getMessage(player, "gui.admin.info.total_count_label"), totalCount, "§b"),
                            GuiLoreLine.Data(plugin.languageManager.getMessage(player, "gui.admin.info.page_label"), "$current/$total", "§a"),
                        ),
                    ),
                )
            }
        }

    private fun createSortButton(player: Player, session: AdminGuiSession): ItemStack =
        ItemStack(Material.HOPPER).apply {
            editMeta { meta ->
                val lang = plugin.languageManager
                val options = PortalSortType.entries.map { it to lang.getMessage(player, it.displayKey) }
                meta.displayName(lang.getComponent(player, "gui.admin_portals.sort.display"))
                meta.lore(
                    CCSystem.getAPI().getLoreService().render(
                        GuiLoreSpec.Rich(
                            buildList {
                                add(
                                    GuiLoreLine.Data(
                                        lang.getMessage(player, "gui.admin_portals.sort.label"),
                                        options.first { it.first == session.portalSortBy }.second,
                                        "§e",
                                    ),
                                )
                                add(GuiLoreLine.Spacer)
                                options.forEach { (type, name) ->
                                    add(GuiLoreLine.Option(name, type == session.portalSortBy, "§e", "§7"))
                                }
                                add(GuiLoreLine.Spacer)
                                addAll(GuiLoreActions.cyclePreviousNext(lang, player))
                            },
                            GuiLoreFrame.BOTH,
                        ),
                    ),
                )
            }
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
