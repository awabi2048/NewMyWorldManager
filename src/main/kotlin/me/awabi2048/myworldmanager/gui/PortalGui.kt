package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.api.localization.generated.CommonKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldGuiPortalKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldMessagesKeys
import com.awabi2048.ccsystem.api.localization.LocalizationKey
import com.awabi2048.ccsystem.api.localization.generated.MyworldColorsKeys

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiCycle
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiInteractionGuidance
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
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.PortalData
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
        return runtime.openEphemeral(player, route(portal.id))
    }

    private fun render(player: Player, portal: PortalData): InventoryMenuView {
        val lang = plugin.languageManager
        val textStatus = if (portal.showText) {
            lang.getMessage(player, MyworldMessagesKeys.MESSAGES_STATUS_ON)
        } else {
            lang.getMessage(player, MyworldMessagesKeys.MESSAGES_STATUS_OFF)
        }
        val currentIndex = colors.indexOf(portal.particleColor).coerceAtLeast(0)
        val nextColor = colors[(currentIndex + 1) % colors.size]
        val previousColor = colors[(currentIndex + colors.size - 1) % colors.size]

        return InventoryMenuView(
            size = 27,
            title = me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(
                lang.getMessage(player, MyworldGuiPortalKeys.GUI_PORTAL_TITLE),
            ),
            elements = listOf(
                menuEntry(
                    player,
                    11,
                    Material.OAK_SIGN,
                    MyworldGuiPortalKeys.GUI_PORTAL_TOGGLE_TEXT_NAME,
                    MyworldGuiPortalKeys.GUI_PORTAL_TOGGLE_TEXT_ACTION,
                    data = listOf(GuiMenuEntryData(
                        lang.getMessage(player, MyworldGuiPortalKeys.GUI_PORTAL_TOGGLE_TEXT_CURRENT_LABEL),
                        textStatus,
                        GuiValueTone.PRIMARY,
                    )),
                    actionId = ACTION_TOGGLE_TEXT,
                ),
                menuEntry(
                    player,
                    13,
                    getWoolColor(portal.particleColor),
                    MyworldGuiPortalKeys.GUI_PORTAL_COLOR_NAME,
                    MyworldGuiPortalKeys.GUI_PORTAL_COLOR_ACTION,
                    description = listOf(
                        lang.getMessage(player, MyworldGuiPortalKeys.GUI_PORTAL_COLOR_PREVIOUS, mapOf("color" to lang.getMessage(player, getColorKey(previousColor)))),
                        lang.getMessage(player, MyworldGuiPortalKeys.GUI_PORTAL_COLOR_NEXT, mapOf("color" to lang.getMessage(player, getColorKey(nextColor)))),
                    ),
                    data = listOf(GuiMenuEntryData(
                        lang.getMessage(player, MyworldGuiPortalKeys.GUI_PORTAL_COLOR_CURRENT_LABEL),
                        lang.getMessage(player, getColorKey(portal.particleColor)),
                        GuiValueTone.PRIMARY,
                    )),
                    actionId = ACTION_CYCLE_COLOR,
                    gesture = MenuGesture.LEFT_RIGHT,
                    interactionGuidance = GuiInteractionGuidance.LIST_SETTING,
                ),
                menuEntry(
                    player,
                    15,
                    Material.LAVA_BUCKET,
                    MyworldGuiPortalKeys.GUI_PORTAL_REMOVE_NAME,
                    MyworldGuiPortalKeys.GUI_PORTAL_REMOVE_ACTION,
                    dangers = listOf(lang.getMessage(player, MyworldGuiPortalKeys.GUI_PORTAL_REMOVE_DESCRIPTION)),
                    actionId = ACTION_REMOVE,
                ),
            ),
        )
    }

    private fun toggleText(context: MenuActionContext): MenuActionResult {
        val portal = portalOrNull(context.route) ?: return MenuActionResult.Rejected()
        if (plugin.portalManager.updateAppearance(portal.id, !portal.showText, portal.particleColor) !=
            me.awabi2048.myworldmanager.service.PortalManager.AppearanceUpdateResult.UPDATED
        ) return MenuActionResult.Rejected()
        return MenuActionResult.Success(MenuUpdate.Refresh)
    }

    private fun cycleColor(context: MenuActionContext): MenuActionResult {
        val direction = GuiCycle.direction(context.click) ?: return MenuActionResult.Ignored
        val portal = portalOrNull(context.route) ?: return MenuActionResult.Rejected()
        val color = GuiCycle.select(portal.particleColor, colors, direction)
        if (plugin.portalManager.updateAppearance(portal.id, portal.showText, color) !=
            me.awabi2048.myworldmanager.service.PortalManager.AppearanceUpdateResult.UPDATED
        ) return MenuActionResult.Rejected()
        return MenuActionResult.Success(MenuUpdate.Refresh)
    }

    private fun remove(context: MenuActionContext): MenuActionResult {
        val portal = portalOrNull(context.route) ?: return MenuActionResult.Rejected()
        val player = context.player
        val lang = plugin.languageManager
        val refundResult = runCatching {
            plugin.portalManager.removePortalAndRefund(portal)
        }.getOrElse { error ->
            plugin.logger.warning("Portal removal was rejected for ${portal.id}: ${error.message}")
            player.sendMessage(lang.getMessage(player, MyworldMessagesKeys.MESSAGES_MIGRATION_REQUIRED))
            return MenuActionResult.Rejected()
        }
        plugin.portalManager.removePortalVisuals(portal.id)
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
                    MyworldMessagesKeys.MESSAGES_WORLD_GATE_REMOVED_REFUND,
                    mapOf(
                        "points" to (refundResult?.points ?: 0),
                        "percent" to (refundResult?.percent ?: 0),
                        "owner" to ownerName,
                    ),
                ),
            )
        } else {
            player.sendMessage(lang.getMessage(player, MyworldMessagesKeys.MESSAGES_PORTAL_REMOVED))
        }
        return MenuActionResult.Success(MenuUpdate.Close)
    }

    private fun bindReturnItem(item: ItemStack, portal: PortalData, player: Player) {
        val lang = plugin.languageManager
        portal.worldUuid?.let { worldUuid ->
            val worldName = plugin.worldConfigRepository.findByUuid(worldUuid)?.name
                ?: lang.getMessage(player, CommonKeys.GENERAL_UNKNOWN)
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

    private fun menuEntry(
        player: Player,
        slot: Int,
        material: Material,
        nameKey: LocalizationKey<String>,
        actionKey: LocalizationKey<String>,
        description: List<String> = emptyList(),
        data: List<GuiMenuEntryData> = emptyList(),
        dangers: List<String> = emptyList(),
        actionId: String,
        gesture: MenuGesture = MenuGesture.ANY,
        interactionGuidance: GuiInteractionGuidance = GuiInteractionGuidance.DEFAULT,
    ): MenuElement {
        val lang = plugin.languageManager
        return CCSystem.getAPI().getGuiElementService().menuEntry(
            player,
            GuiMenuEntrySpec(
                slot = slot,
                material = material,
                name = GuiNameSpec.FixedLabel(lang.getComponent(player, nameKey)),
                role = GuiElementRole.ACTION,
                description = description,
                data = data,
                dangers = dangers,
                actions = listOf(
                    menuGestureAction(
                        actionId,
                        gesture,
                        lang.getMessage(player, actionKey),
                        safety = portalActionSafety(actionId),
                        reversibleContract = when (actionId) {
                            ACTION_TOGGLE_TEXT -> MwmMenuActionSemantics.contract("portal-text")
                            ACTION_CYCLE_COLOR -> MwmMenuActionSemantics.contract("portal-color")
                            else -> null
                        },
                    ),
                ),
                interactionGuidance = interactionGuidance,
            ),
        )
    }

    private fun portalActionSafety(actionId: String): MenuActionSafety = when (actionId) {
        ACTION_TOGGLE_TEXT,
        ACTION_CYCLE_COLOR -> MenuActionSafety.REVERSIBLE
        ACTION_REMOVE -> MenuActionSafety.IRREVERSIBLE
        else -> error("Unknown portal action safety: $actionId")
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

    private fun getColorKey(color: Color): LocalizationKey<String> = when (color) {
        Color.WHITE -> MyworldColorsKeys.COLORS_WHITE
        Color.SILVER -> MyworldColorsKeys.COLORS_SILVER
        Color.GRAY -> MyworldColorsKeys.COLORS_GRAY
        Color.BLACK -> MyworldColorsKeys.COLORS_BLACK
        Color.RED -> MyworldColorsKeys.COLORS_RED
        Color.MAROON -> MyworldColorsKeys.COLORS_MAROON
        Color.YELLOW -> MyworldColorsKeys.COLORS_YELLOW
        Color.OLIVE -> MyworldColorsKeys.COLORS_OLIVE
        Color.LIME -> MyworldColorsKeys.COLORS_LIME
        Color.GREEN -> MyworldColorsKeys.COLORS_GREEN
        Color.AQUA -> MyworldColorsKeys.COLORS_AQUA
        Color.TEAL -> MyworldColorsKeys.COLORS_TEAL
        Color.BLUE -> MyworldColorsKeys.COLORS_BLUE
        Color.NAVY -> MyworldColorsKeys.COLORS_NAVY
        Color.FUCHSIA -> MyworldColorsKeys.COLORS_FUCHSIA
        Color.PURPLE -> MyworldColorsKeys.COLORS_PURPLE
        Color.ORANGE -> MyworldColorsKeys.COLORS_ORANGE
        else -> MyworldColorsKeys.COLORS_WHITE
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
