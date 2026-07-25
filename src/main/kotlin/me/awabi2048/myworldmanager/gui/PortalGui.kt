package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiCycle
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
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.PortalData
import me.awabi2048.myworldmanager.util.GuiItemFactory
import me.awabi2048.myworldmanager.util.ItemTag
import me.awabi2048.myworldmanager.util.PortalItemUtil
import me.awabi2048.myworldmanager.util.WorldGateItemUtil
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID

class PortalGui(private val plugin: MyWorldManager) {
    private val runtime = CCSystem.getAPI().getMenuRuntimeService()
    private val colors = listOf(
        Color.WHITE, Color.SILVER, Color.GRAY, Color.BLACK,
        Color.RED, Color.MAROON, Color.YELLOW, Color.OLIVE,
        Color.LIME, Color.GREEN, Color.AQUA, Color.TEAL,
        Color.BLUE, Color.NAVY, Color.FUCHSIA, Color.PURPLE,
        Color.ORANGE,
    )

    init {
        runtime.register(
            InventoryMenuDefinition(
                owner = OWNER,
                id = ROUTE_ID,
                renderer = { context -> render(context.player, portal(context.route)) },
                actions = mapOf(
                    ACTION_TOGGLE_TEXT to MenuActionHandler(::toggleText),
                    ACTION_CYCLE_COLOR to MenuActionHandler(::cycleColor),
                    ACTION_REMOVE to MenuActionHandler(::remove),
                ),
            ),
        )
    }

    fun open(player: Player, portal: PortalData): Boolean {
        val titleKey = "gui.portal.title"
        if (!plugin.languageManager.hasKey(player, titleKey)) {
            player.sendMessage("§c[MyWorldManager] Error: Missing translation key: $titleKey")
            return false
        }
        return runtime.openEphemeral(player, route(portal.id))
    }

    private fun render(player: Player, portal: PortalData): InventoryMenuView {
        val lang = plugin.languageManager
        val textStatus = if (portal.showText) {
            lang.getMessage(player, "messages.status_on")
        } else {
            lang.getMessage(player, "messages.status_off")
        }
        val currentIndex = colors.indexOf(portal.particleColor).coerceAtLeast(0)
        val nextColor = colors[(currentIndex + 1) % colors.size]
        val previousColor = colors[(currentIndex + colors.size - 1) % colors.size]

        return InventoryMenuView(
            size = 27,
            title = me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(
                lang.getMessage(player, "gui.portal.title"),
            ),
            elements = listOf(
                MenuElement(
                    slot = 11,
                    item = structuredItem(
                        player,
                        Material.OAK_SIGN,
                        "gui.portal.toggle_text",
                        listOf(
                            GuiLoreLine.Data(
                                lang.getMessage(player, "gui.portal.toggle_text.current_label"),
                                textStatus,
                                "§e",
                            ),
                        ),
                        ItemTag.TYPE_GUI_PORTAL_TOGGLE_TEXT,
                    ),
                    role = GuiElementRole.ACTION,
                    actionId = ACTION_TOGGLE_TEXT,
                ),
                MenuElement(
                    slot = 13,
                    item = structuredItem(
                        player,
                        getWoolColor(portal.particleColor),
                        "gui.portal.color",
                        listOf(
                            GuiLoreLine.Data(
                                lang.getMessage(player, "gui.portal.color.current_label"),
                                lang.getMessage(player, "colors.${getColorKey(portal.particleColor)}"),
                                "§e",
                            ),
                            GuiLoreLine.Text(
                                lang.getMessage(
                                    player,
                                    "gui.portal.color.previous",
                                    mapOf("color" to lang.getMessage(player, "colors.${getColorKey(previousColor)}")),
                                ),
                            ),
                            GuiLoreLine.Text(
                                lang.getMessage(
                                    player,
                                    "gui.portal.color.next",
                                    mapOf("color" to lang.getMessage(player, "colors.${getColorKey(nextColor)}")),
                                ),
                            ),
                        ),
                        ItemTag.TYPE_GUI_PORTAL_CYCLE_COLOR,
                    ),
                    role = GuiElementRole.ACTION,
                    actionId = ACTION_CYCLE_COLOR,
                ),
                MenuElement(
                    slot = 15,
                    item = structuredItem(
                        player,
                        Material.LAVA_BUCKET,
                        "gui.portal.remove",
                        listOf(GuiLoreLine.Danger(lang.getMessage(player, "gui.portal.remove.description"))),
                        ItemTag.TYPE_GUI_PORTAL_REMOVE,
                    ),
                    role = GuiElementRole.ACTION,
                    actionId = ACTION_REMOVE,
                ),
            ),
        )
    }

    private fun toggleText(context: MenuActionContext): MenuActionResult {
        val portal = portalOrNull(context.route) ?: return MenuActionResult.Rejected()
        portal.showText = !portal.showText
        plugin.portalRepository.saveAll()
        return MenuActionResult.Success(MenuUpdate.Refresh)
    }

    private fun cycleColor(context: MenuActionContext): MenuActionResult {
        val direction = GuiCycle.direction(context.click) ?: return MenuActionResult.Ignored
        val portal = portalOrNull(context.route) ?: return MenuActionResult.Rejected()
        portal.particleColor = GuiCycle.select(portal.particleColor, colors, direction)
        plugin.portalRepository.saveAll()
        return MenuActionResult.Success(MenuUpdate.Refresh)
    }

    private fun remove(context: MenuActionContext): MenuActionResult {
        val portal = portalOrNull(context.route) ?: return MenuActionResult.Rejected()
        val player = context.player
        val lang = plugin.languageManager
        val refundResult = if (portal.isGate()) plugin.portalManager.refundPointsForRemovedGate(portal) else null

        plugin.portalManager.removePortalVisuals(portal.id)
        plugin.portalRepository.removePortal(portal.id)
        if (!portal.isGate()) {
            portal.loadedWorld()?.getBlockAt(portal.x, portal.y, portal.z)
                ?.takeIf { it.type == Material.END_PORTAL_FRAME }
                ?.setType(Material.AIR)
        }

        val returnItem = if (portal.isGate()) {
            WorldGateItemUtil.createBaseWorldGateItem(lang, player)
        } else {
            PortalItemUtil.createBasePortalItem(lang, player)
        }
        bindReturnItem(returnItem, portal, player)
        player.inventory.addItem(returnItem)

        if (portal.isGate()) {
            val ownerName = Bukkit.getOfflinePlayer(portal.ownerUuid).name ?: portal.ownerUuid.toString()
            player.sendMessage(
                lang.getMessage(
                    player,
                    "messages.world_gate_removed_refund",
                    mapOf(
                        "points" to (refundResult?.points ?: 0),
                        "percent" to (refundResult?.percent ?: 0),
                        "owner" to ownerName,
                    ),
                ),
            )
        } else {
            player.sendMessage(lang.getMessage(player, "messages.portal_removed"))
        }
        return MenuActionResult.Success(MenuUpdate.Close)
    }

    private fun bindReturnItem(item: ItemStack, portal: PortalData, player: Player) {
        val lang = plugin.languageManager
        portal.worldUuid?.let { worldUuid ->
            val worldName = plugin.worldConfigRepository.findByUuid(worldUuid)?.name
                ?: lang.getMessage(player, "general.unknown")
            if (portal.isGate()) {
                WorldGateItemUtil.bindWorld(item, worldUuid, worldName = worldName, lang, player)
            } else {
                PortalItemUtil.bindWorld(item, worldUuid, worldName = worldName, lang, player)
            }
            return
        }
        val targetRuntimeName = portal.targetRuntimeName ?: return
        val displayName = plugin.config.getString("portal_targets.$targetRuntimeName") ?: targetRuntimeName
        if (portal.isGate()) {
            WorldGateItemUtil.bindExternalWorld(item, targetRuntimeName, displayName, lang, player)
        } else {
            PortalItemUtil.bindExternalWorld(item, targetRuntimeName, displayName, lang, player)
        }
    }

    private fun structuredItem(
        player: Player,
        material: Material,
        key: String,
        information: List<GuiLoreLine>,
        tag: String,
    ): ItemStack {
        val lang = plugin.languageManager
        return GuiItemFactory.item(
            material,
            lang.getMessage(player, "$key.name"),
            GuiLoreSpec.Blocks(
                listOf(
                    GuiLoreBlock(information),
                    GuiLoreBlock(
                        listOf(
                            me.awabi2048.myworldmanager.util.GuiLoreActions.singleClick(
                                lang,
                                player,
                                lang.getMessage(player, "$key.action"),
                            ),
                        ),
                    ),
                ),
            ),
            tag,
        )
    }

    private fun portal(route: MenuRoute): PortalData =
        portalOrNull(route) ?: error("ポータルが見つかりません: ${route.payload[PORTAL_ID]}")

    private fun portalOrNull(route: MenuRoute): PortalData? {
        val portalId = route.payload[PORTAL_ID]
            ?.let { value -> runCatching { UUID.fromString(value) }.getOrNull() }
            ?: return null
        return plugin.portalRepository.findAll().find { it.id == portalId }
    }

    private fun route(portalId: UUID) = MenuRoute(OWNER, ROUTE_ID, mapOf(PORTAL_ID to portalId.toString()))

    private fun getWoolColor(color: Color): Material = when (color) {
        Color.WHITE -> Material.WHITE_WOOL
        Color.SILVER -> Material.LIGHT_GRAY_WOOL
        Color.GRAY -> Material.GRAY_WOOL
        Color.BLACK -> Material.BLACK_WOOL
        Color.RED, Color.MAROON -> Material.RED_WOOL
        Color.YELLOW -> Material.YELLOW_WOOL
        Color.OLIVE, Color.GREEN -> Material.GREEN_WOOL
        Color.LIME -> Material.LIME_WOOL
        Color.AQUA -> Material.LIGHT_BLUE_WOOL
        Color.TEAL -> Material.CYAN_WOOL
        Color.BLUE, Color.NAVY -> Material.BLUE_WOOL
        Color.FUCHSIA -> Material.MAGENTA_WOOL
        Color.PURPLE -> Material.PURPLE_WOOL
        Color.ORANGE -> Material.ORANGE_WOOL
        else -> Material.WHITE_WOOL
    }

    private fun getColorKey(color: Color): String = when (color) {
        Color.WHITE -> "white"
        Color.SILVER -> "silver"
        Color.GRAY -> "gray"
        Color.BLACK -> "black"
        Color.RED -> "red"
        Color.MAROON -> "maroon"
        Color.YELLOW -> "yellow"
        Color.OLIVE -> "olive"
        Color.LIME -> "lime"
        Color.GREEN -> "green"
        Color.AQUA -> "aqua"
        Color.TEAL -> "teal"
        Color.BLUE -> "blue"
        Color.NAVY -> "navy"
        Color.FUCHSIA -> "fuchsia"
        Color.PURPLE -> "purple"
        Color.ORANGE -> "orange"
        else -> "white"
    }

    private companion object {
        const val OWNER = "mwm"
        const val ROUTE_ID = "portal_settings"
        const val PORTAL_ID = "portal_id"
        const val ACTION_TOGGLE_TEXT = "toggle_text"
        const val ACTION_CYCLE_COLOR = "cycle_color"
        const val ACTION_REMOVE = "remove"
    }
}
