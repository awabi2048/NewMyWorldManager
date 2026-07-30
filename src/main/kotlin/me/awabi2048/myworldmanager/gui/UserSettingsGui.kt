package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiCycle
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiMenuIconData
import com.awabi2048.ccsystem.api.gui.GuiMenuIconOption
import com.awabi2048.ccsystem.api.gui.GuiMenuIconSpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiNameStyle
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuActionContext
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.*
import me.awabi2048.myworldmanager.session.SettingsAction
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.GuiItemFactory
import me.awabi2048.myworldmanager.util.GuiLoreActions
import me.awabi2048.myworldmanager.util.ItemTag
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
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
        val items = mutableListOf<ItemStack>()
        val notifyStatus = if (stats.visitorNotificationEnabled) lang.getMessage(player, "messages.status_on") else lang.getMessage(player, "messages.status_off")
        items.add(settingItem(
            player,
            Material.BELL,
            "gui.user_settings.notification.display",
            "notification",
            notifyStatus,
            if (stats.visitorNotificationEnabled) "§a" else "§c",
            ItemTag.TYPE_GUI_USER_SETTING_NOTIFICATION,
            "gui.user_settings.cycle_action.toggle",
            glint = stats.visitorNotificationEnabled
        ))

        val currentLocale = lang.resolveLocale(player)
        val languageName = lang.getMessage(player, "general.language.$currentLocale")
        items.add(settingItem(
            player,
            Material.WRITABLE_BOOK,
            "gui.user_settings.language.display",
            "language",
            languageName,
            "§f",
            ItemTag.TYPE_GUI_USER_SETTING_LANGUAGE,
            "gui.user_settings.cycle_action.next"
        ))

        val criticalStatus = if (stats.criticalSettingsEnabled) {
            lang.getMessage(player, "messages.status_visible")
        } else {
            lang.getMessage(player, "messages.status_hidden")
        }
        items.add(settingItem(
            player,
            Material.RECOVERY_COMPASS,
            "gui.user_settings.critical_settings_visibility.display",
            "critical_settings_visibility",
            criticalStatus,
            if (stats.criticalSettingsEnabled) "§a" else "§7",
            ItemTag.TYPE_GUI_USER_SETTING_CRITICAL_VISIBILITY,
            "gui.user_settings.cycle_action.toggle"
        ))

        items.add(tourNavigationItem(player, stats.tourNavigationMode))
        val totalRows = 5
        val centerRowStart = 2 * 9
        val firstSlot = centerRowStart + ((9 - items.size) / 2)
        val actionIds = listOf(
            ACTION_NOTIFICATION,
            ACTION_LANGUAGE,
            ACTION_CRITICAL_VISIBILITY,
            ACTION_TOUR_NAVIGATION,
        )
        val elements = items.mapIndexed { index, item ->
            MenuElement(firstSlot + index, item, GuiElementRole.ACTION, actionIds[index])
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

    private fun settingItem(
        player: Player,
        material: Material,
        displayKey: String,
        setting: String,
        currentValue: String,
        currentValueColor: String,
        tag: String,
        actionKey: String,
        glint: Boolean? = null,
    ): ItemStack {
        val prefix = "gui.user_settings.$setting.blocks"
        return GuiItemFactory.menuIcon(
            GuiMenuIconSpec(
                material = material,
                name = GuiNameSpec.Text(
                    plugin.languageManager.getMessage(player, displayKey),
                    GuiNameStyle.DEFAULT
                ),
                role = GuiElementRole.CONTENT,
                amount = 1,
                description = plugin.languageManager.getMessageList(player, "$prefix.description"),
                data = listOf(
                    GuiMenuIconData(
                        plugin.languageManager.getMessage(player, "$prefix.current_label"),
                        currentValue,
                        currentValueColor
                    )
                ),
                options = emptyList(),
                warnings = emptyList(),
                dangers = emptyList(),
                actions = listOf(
                    GuiLoreActions.menuSingleClick(
                        plugin.languageManager,
                        player,
                        plugin.languageManager.getMessage(player, actionKey)
                    )
                ),
                glint = glint,
            ),
            tag
        )
    }

    private fun tourNavigationItem(player: Player, currentMode: TourNavigationMode): ItemStack {
        val lang = plugin.languageManager
        val options = TourNavigationMode.entries.map { mode ->
            GuiMenuIconOption(
                label = lang.getMessage(player, "gui.user_settings.tour_navigation.mode.${mode.name.lowercase()}"),
                selected = mode == currentMode,
                selectedColor = "§b",
                inactiveColor = "§7"
            )
        }
        return GuiItemFactory.menuIcon(
            GuiMenuIconSpec(
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
                    GuiLoreActions.menuSingleClick(
                        lang,
                        player,
                        lang.getMessage(player, "gui.user_settings.cycle_action.toggle")
                    )
                ),
                glint = null
            ),
            ItemTag.TYPE_GUI_USER_SETTING_TOUR_NAVIGATION
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
