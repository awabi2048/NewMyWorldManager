package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiCycle
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryAction
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
import com.awabi2048.ccsystem.api.gui.MenuAcceptedClicks
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
                    ACTION_LANGUAGE to MenuActionHandler { MenuActionResult.Success(MenuUpdate.Refresh) },
                    ACTION_CRITICAL_VISIBILITY to MenuActionHandler(::toggleCriticalVisibility),
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
        val notifyStatus = if (stats.visitorNotificationEnabled) lang.getMessage(player, "messages.status_on") else lang.getMessage(player, "messages.status_off")
        entries.add { slot -> settingEntry(
            player,
            slot,
            Material.BELL,
            "gui.user_settings.notification.display",
            "notification",
            notifyStatus,
            if (stats.visitorNotificationEnabled) GuiValueTone.SUCCESS else GuiValueTone.DANGER,
            ACTION_NOTIFICATION,
            "gui.user_settings.cycle_action.toggle",
            glint = stats.visitorNotificationEnabled
        ) }

        val currentLocale = lang.resolveLocale(player)
        val languageName = lang.getMessage(player, "general.language.$currentLocale")
        entries.add { slot -> settingEntry(
            player,
            slot,
            Material.WRITABLE_BOOK,
            "gui.user_settings.language.display",
            "language",
            languageName,
            GuiValueTone.DEFAULT,
            ACTION_LANGUAGE,
            "gui.user_settings.cycle_action.next"
        ) }

        val criticalStatus = if (stats.criticalSettingsEnabled) {
            lang.getMessage(player, "messages.status_visible")
        } else {
            lang.getMessage(player, "messages.status_hidden")
        }
        entries.add { slot -> settingEntry(
            player,
            slot,
            Material.RECOVERY_COMPASS,
            "gui.user_settings.critical_settings_visibility.display",
            "critical_settings_visibility",
            criticalStatus,
            if (stats.criticalSettingsEnabled) GuiValueTone.SUCCESS else GuiValueTone.MUTED,
            ACTION_CRITICAL_VISIBILITY,
            "gui.user_settings.cycle_action.toggle"
        ) }

        entries.add { slot -> tourNavigationEntry(player, stats.tourNavigationMode, slot) }
        val totalRows = 5
        val centerRowStart = 2 * 9
        val firstSlot = centerRowStart + ((9 - entries.size) / 2)
        val elements = entries.mapIndexed { index, entry ->
            entry(firstSlot + index)
        }.toMutableList()

        if (GuiHelper.canGoBack(player)) {
            elements += MenuElement(
                (totalRows - 1) * 9 + 4,
                me.awabi2048.myworldmanager.util.GuiHelper.createReturnItem(plugin, player, "user_settings"),
                GuiElementRole.BACK,
                ACTION_BACK,
            )
        }

        return InventoryMenuView(
            size = totalRows * 9,
            title = me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(
                lang.getMessage(player, "gui.user_settings.title"),
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
        displayKey: String,
        setting: String,
        currentValue: String,
        currentValueTone: GuiValueTone,
        actionId: String,
        actionKey: String,
        glint: Boolean? = null,
    ): MenuElement {
        val prefix = "gui.user_settings.$setting.blocks"
        return CCSystem.getAPI().getGuiElementService().menuEntry(
            player,
            GuiMenuEntrySpec(
                slot = slot,
                material = material,
                name = GuiNameSpec.Text(
                    plugin.languageManager.getMessage(player, displayKey),
                    GuiNameStyle.DEFAULT
                ),
                role = GuiElementRole.CONTENT,
                amount = 1,
                description = plugin.languageManager.getMessageList(player, "$prefix.description"),
                data = listOf(
                    GuiMenuEntryData(
                        plugin.languageManager.getMessage(player, "$prefix.current_label"),
                        currentValue,
                        currentValueTone,
                    )
                ),
                options = emptyList(),
                warnings = emptyList(),
                dangers = emptyList(),
                actions = listOf(
                    GuiMenuEntryAction(
                        actionId,
                        MenuAcceptedClicks.LEFT_RIGHT,
                        plugin.languageManager.getMessage(player, actionKey),
                    ),
                ),
                glint = glint,
            ),
        )
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
                name = GuiNameSpec.Text(
                    lang.getMessage(player, "gui.user_settings.tour_navigation.display"),
                    GuiNameStyle.DEFAULT
                ),
                role = GuiElementRole.CONTENT,
                amount = 1,
                description = lang.getMessageList(player, "gui.user_settings.tour_navigation.blocks.description"),
                data = emptyList(),
                options = options,
                warnings = emptyList(),
                dangers = emptyList(),
                actions = listOf(
                    GuiMenuEntryAction(
                        ACTION_TOUR_NAVIGATION,
                        MenuAcceptedClicks.LEFT_RIGHT,
                        lang.getMessage(player, "gui.user_settings.cycle_action.toggle"),
                    ),
                ),
                glint = null
            ),
        )
    }

    companion object {
        private const val OWNER = "myworldmanager"
        private const val ROUTE_ID = "user_settings"
        private const val ACTION_NOTIFICATION = "notification"
        private const val ACTION_LANGUAGE = "language"
        private const val ACTION_CRITICAL_VISIBILITY = "critical_visibility"
        private const val ACTION_TOUR_NAVIGATION = "tour_navigation"
        private const val ACTION_BACK = "back"
    }
}
