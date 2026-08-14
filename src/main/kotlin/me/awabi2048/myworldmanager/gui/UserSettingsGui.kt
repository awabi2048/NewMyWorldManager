package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.api.localization.generated.MyworldGuiSettingsKeys
import com.awabi2048.ccsystem.api.localization.generated.CommonKeys
import com.awabi2048.ccsystem.api.localization.LocalizationKey
import com.awabi2048.ccsystem.api.localization.generated.MyworldMessagesKeys

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiCycle
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiInteractionGuidance
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryData
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryOption
import com.awabi2048.ccsystem.api.gui.GuiMenuEntrySpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiNameStyle
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
import com.awabi2048.ccsystem.api.gui.GuiValueTone
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.*
import me.awabi2048.myworldmanager.session.SettingsAction
import me.awabi2048.myworldmanager.util.GuiHelper
import org.bukkit.Material
import org.bukkit.entity.Player
import java.util.UUID

class UserSettingsGui(private val plugin: MyWorldManager) {
    private val runtime = CCSystem.getAPI().getMenuRuntimeService()

    init {
        runtime.register(
            InventoryMenuDefinition(
                owner = OWNER,
                id = ROUTE_ID,
                renderer = { context -> render(context.player) },
                actions = mapOf(
                    ACTION_NOTIFICATION to MenuActionHandler(::toggleNotification),
                    ACTION_CRITICAL_VISIBILITY to MenuActionHandler(::toggleCriticalVisibility),
                    ACTION_FAVORITE_GROUP_INVITES to MenuActionHandler(::toggleFavoriteGroupInvites),
                    ACTION_TOUR_NAVIGATION to MenuActionHandler(::cycleTourNavigation),
                    ACTION_BACK to MenuActionHandler(::back),
                ),
            ),
        )
    }

    fun open(player: Player, showBackButton: Boolean? = null) {
        val route = prepareOpen(player, showBackButton) ?: return
        val session = plugin.playerWorldSessionManager.getSession(player.uniqueId)
        if (session.showBackButton) {
            runtime.navigate(player, route)
        } else {
            runtime.open(player, route)
        }
    }

    fun prepareOpen(player: Player, showBackButton: Boolean? = null): MenuRoute? {
        val lang = plugin.languageManager
        val session = plugin.playerWorldSessionManager.getSession(player.uniqueId)
        if (showBackButton != null) {
            session.showBackButton = showBackButton
        }

        val titleKey = "gui.user_settings.title"
        if (!lang.hasKey(player, titleKey)) {
            player.sendMessage("§c[MyWorldManager] Error: Missing translation key: $titleKey")
            return null
        }
        plugin.settingsSessionManager.updateSessionAction(player, UUID(0, 0), SettingsAction.VIEW_SETTINGS, isGui = true)
        return MenuRoute(OWNER, ROUTE_ID)
    }

    private fun render(player: Player): InventoryMenuView {
        val lang = plugin.languageManager
        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        val entries = mutableListOf<(Int) -> MenuElement>()
        val notifyStatus = if (stats.visitorNotificationEnabled) lang.getMessage(player, MyworldMessagesKeys.MESSAGES_STATUS_ON) else lang.getMessage(player, MyworldMessagesKeys.MESSAGES_STATUS_OFF)
        entries.add { slot -> settingEntry(
            player,
            slot,
            Material.BELL,
            MyworldGuiSettingsKeys.GUI_USER_SETTINGS_NOTIFICATION_DISPLAY,
            MyworldGuiSettingsKeys.GUI_USER_SETTINGS_NOTIFICATION_BLOCKS_DESCRIPTION,
            MyworldGuiSettingsKeys.GUI_USER_SETTINGS_NOTIFICATION_BLOCKS_CURRENT_LABEL,
            notifyStatus,
            if (stats.visitorNotificationEnabled) GuiValueTone.SUCCESS else GuiValueTone.DANGER,
            ACTION_NOTIFICATION,
            MyworldGuiSettingsKeys.GUI_USER_SETTINGS_CYCLE_ACTION_TOGGLE,
            glint = stats.visitorNotificationEnabled
        ) }

        val currentLocale = lang.resolveLocale(player)
        val languageName = lang.getMessage(player, when (currentLocale) {
            "en_us" -> CommonKeys.GENERAL_LANGUAGE_EN_US
            else -> CommonKeys.GENERAL_LANGUAGE_JA_JP
        })
        entries.add { slot -> settingEntry(
            player,
            slot,
            Material.WRITABLE_BOOK,
            MyworldGuiSettingsKeys.GUI_USER_SETTINGS_LANGUAGE_DISPLAY,
            MyworldGuiSettingsKeys.GUI_USER_SETTINGS_LANGUAGE_BLOCKS_DESCRIPTION,
            MyworldGuiSettingsKeys.GUI_USER_SETTINGS_LANGUAGE_BLOCKS_CURRENT_LABEL,
            languageName,
            GuiValueTone.DEFAULT,
            null,
            null,
        ) }

        val criticalStatus = if (stats.criticalSettingsEnabled) {
            lang.getMessage(player, MyworldMessagesKeys.MESSAGES_STATUS_VISIBLE)
        } else {
            lang.getMessage(player, MyworldMessagesKeys.MESSAGES_STATUS_HIDDEN)
        }
        entries.add { slot -> settingEntry(
            player,
            slot,
            Material.RECOVERY_COMPASS,
            MyworldGuiSettingsKeys.GUI_USER_SETTINGS_CRITICAL_SETTINGS_VISIBILITY_DISPLAY,
            MyworldGuiSettingsKeys.GUI_USER_SETTINGS_CRITICAL_SETTINGS_VISIBILITY_BLOCKS_DESCRIPTION,
            MyworldGuiSettingsKeys.GUI_USER_SETTINGS_CRITICAL_SETTINGS_VISIBILITY_BLOCKS_CURRENT_LABEL,
            criticalStatus,
            if (stats.criticalSettingsEnabled) GuiValueTone.SUCCESS else GuiValueTone.MUTED,
            ACTION_CRITICAL_VISIBILITY,
            MyworldGuiSettingsKeys.GUI_USER_SETTINGS_CYCLE_ACTION_TOGGLE
        ) }

        entries.add { slot -> tourNavigationEntry(player, stats.tourNavigationMode, slot) }
        val groupInviteStatus = if (stats.favoriteGroupInvitesEnabled) {
            lang.getMessage(player, MyworldMessagesKeys.MESSAGES_STATUS_ON)
        } else {
            lang.getMessage(player, MyworldMessagesKeys.MESSAGES_STATUS_OFF)
        }
        entries.add { slot -> settingEntry(
            player,
            slot,
            Material.GOAT_HORN,
            MyworldGuiSettingsKeys.GUI_USER_SETTINGS_FAVORITE_GROUP_INVITES_DISPLAY,
            MyworldGuiSettingsKeys.GUI_USER_SETTINGS_FAVORITE_GROUP_INVITES_BLOCKS_DESCRIPTION,
            MyworldGuiSettingsKeys.GUI_USER_SETTINGS_FAVORITE_GROUP_INVITES_BLOCKS_CURRENT_LABEL,
            groupInviteStatus,
            if (stats.favoriteGroupInvitesEnabled) GuiValueTone.SUCCESS else GuiValueTone.DANGER,
            ACTION_FAVORITE_GROUP_INVITES,
            MyworldGuiSettingsKeys.GUI_USER_SETTINGS_CYCLE_ACTION_TOGGLE,
            glint = stats.favoriteGroupInvitesEnabled,
        ) }
        val totalRows = 5
        val centerRowStart = 2 * 9
        val firstSlot = centerRowStart + ((9 - entries.size) / 2)
        val elements = entries.mapIndexed { index, entry ->
            entry(firstSlot + index)
        }.toMutableList()

        if (GuiHelper.canGoBack(player)) {
            elements += CCSystem.getAPI().getGuiElementService().backEntry(
                player,
                (totalRows - 1) * 9 + 4,
                Material.REDSTONE,
            )
        }

        return InventoryMenuView(
            size = totalRows * 9,
            title = me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(
                lang.getMessage(player, MyworldGuiSettingsKeys.GUI_USER_SETTINGS_TITLE),
            ),
            elements = elements,
        )
    }

    private fun toggleNotification(context: MenuActionContext): MenuActionResult {
        val stats = plugin.playerStatsRepository.findByUuid(context.player.uniqueId)
        stats.visitorNotificationEnabled = !stats.visitorNotificationEnabled
        plugin.playerStatsRepository.save(stats)
        return MenuActionResult.Success(MenuUpdate.Refresh)
    }

    private fun toggleCriticalVisibility(context: MenuActionContext): MenuActionResult {
        val stats = plugin.playerStatsRepository.findByUuid(context.player.uniqueId)
        stats.criticalSettingsEnabled = !stats.criticalSettingsEnabled
        plugin.playerStatsRepository.save(stats)
        return MenuActionResult.Success(MenuUpdate.Refresh)
    }

    private fun toggleFavoriteGroupInvites(context: MenuActionContext): MenuActionResult {
        val stats = plugin.playerStatsRepository.findByUuid(context.player.uniqueId)
        stats.favoriteGroupInvitesEnabled = !stats.favoriteGroupInvitesEnabled
        plugin.playerStatsRepository.save(stats)
        return MenuActionResult.Success(MenuUpdate.Refresh)
    }

    private fun cycleTourNavigation(context: MenuActionContext): MenuActionResult {
        val direction = GuiCycle.direction(context.click) ?: return MenuActionResult.Ignored
        val stats = plugin.playerStatsRepository.findByUuid(context.player.uniqueId)
        stats.tourNavigationMode = GuiCycle.select(
            stats.tourNavigationMode,
            TourNavigationMode.entries,
            direction,
        )
        plugin.playerStatsRepository.save(stats)
        plugin.tourManager.refreshNavigation(context.player)
        return MenuActionResult.Success(MenuUpdate.Refresh)
    }

    private fun back(context: MenuActionContext): MenuActionResult {
        return MenuActionResult.Success(MenuUpdate.Back)
    }

    private fun settingEntry(
        player: Player,
        slot: Int,
        material: Material,
        displayKey: LocalizationKey<String>,
        descriptionKey: LocalizationKey<List<String>>,
        currentLabelKey: LocalizationKey<String>,
        currentValue: String,
        currentValueTone: GuiValueTone,
        actionId: String?,
        actionKey: LocalizationKey<String>?,
        glint: Boolean? = null,
    ): MenuElement {
        return CCSystem.getAPI().getGuiElementService().menuEntry(
            player,
            GuiMenuEntrySpec(
                slot = slot,
                material = material,
                name = me.awabi2048.myworldmanager.util.fixedLabelName(
                    plugin.languageManager.getMessage(player, displayKey),
                    GuiNameStyle.DEFAULT
                ),
                role = GuiElementRole.CONTENT,
                amount = 1,
                description = plugin.languageManager.getMessageList(player, descriptionKey),
                data = listOf(
                    GuiMenuEntryData(
                        plugin.languageManager.getMessage(player, currentLabelKey),
                        currentValue,
                        currentValueTone,
                    )
                ),
                options = emptyList(),
                warnings = emptyList(),
                dangers = emptyList(),
                actions = if (actionId != null && actionKey != null) listOf(
                    menuGestureAction(
                        actionId,
                        MenuGesture.ANY,
                        plugin.languageManager.getMessage(player, actionKey),
                        safety = settingActionSafety(actionId),
                        reversibleContract = when (actionId) {
                            ACTION_NOTIFICATION -> MwmMenuActionSemantics.contract("user-notification")
                            ACTION_CRITICAL_VISIBILITY -> MwmMenuActionSemantics.contract("user-critical")
                            ACTION_FAVORITE_GROUP_INVITES -> MwmMenuActionSemantics.contract("user-favorite-group-invites")
                            else -> null
                        },
                    ),
                ) else emptyList(),
                glint = glint,
            ),
        )
    }

    private fun settingActionSafety(actionId: String): MenuActionSafety = when (actionId) {
        ACTION_NOTIFICATION,
        ACTION_CRITICAL_VISIBILITY -> MenuActionSafety.REVERSIBLE
        ACTION_FAVORITE_GROUP_INVITES -> MenuActionSafety.REVERSIBLE
        else -> error("Unknown user setting action safety: $actionId")
    }

    private fun tourNavigationEntry(player: Player, currentMode: TourNavigationMode, slot: Int): MenuElement {
        val lang = plugin.languageManager
        val options = TourNavigationMode.entries.map { mode ->
            GuiMenuEntryOption(
                label = lang.getMessage(player, "gui.user_settings.tour_navigation.mode.${mode.name.lowercase()}"),
                selected = mode == currentMode,
            )
        }
        return CCSystem.getAPI().getGuiElementService().menuEntry(
            player,
            GuiMenuEntrySpec(
                slot = slot,
                material = Material.COMPASS,
                name = me.awabi2048.myworldmanager.util.fixedLabelName(
                    lang.getMessage(player, MyworldGuiSettingsKeys.GUI_USER_SETTINGS_TOUR_NAVIGATION_DISPLAY),
                    GuiNameStyle.DEFAULT
                ),
                role = GuiElementRole.CONTENT,
                amount = 1,
                description = lang.getMessageList(player, MyworldGuiSettingsKeys.GUI_USER_SETTINGS_TOUR_NAVIGATION_BLOCKS_DESCRIPTION),
                data = emptyList(),
                options = options,
                warnings = emptyList(),
                dangers = emptyList(),
                actions = listOf(
                    menuGestureAction(
                        ACTION_TOUR_NAVIGATION,
                        MenuGesture.PLAIN_LEFT_RIGHT,
                        lang.getMessage(player, MyworldGuiSettingsKeys.GUI_USER_SETTINGS_CYCLE_ACTION_TOGGLE),
                        safety = MenuActionSafety.REVERSIBLE,
                        reversibleContract = MwmMenuActionSemantics.contract("user-tour"),
                    ),
                ),
                interactionGuidance = GuiInteractionGuidance.LIST_SETTING,
                glint = null
            ),
        )
    }

    companion object {
        private const val OWNER = "myworldmanager"
        private const val ROUTE_ID = "user_settings"
        private const val ACTION_NOTIFICATION = "notification"
        private const val ACTION_CRITICAL_VISIBILITY = "critical_visibility"
        private const val ACTION_TOUR_NAVIGATION = "tour_navigation"
        private const val ACTION_FAVORITE_GROUP_INVITES = "favorite_group_invites"
        private const val ACTION_BACK = "back"
    }
}
