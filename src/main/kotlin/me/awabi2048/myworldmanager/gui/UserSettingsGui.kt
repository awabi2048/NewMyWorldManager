package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.ui.ManagedMenuPresenter

import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiMenuIconData
import com.awabi2048.ccsystem.api.gui.GuiMenuIconOption
import com.awabi2048.ccsystem.api.gui.GuiMenuIconSpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiNameStyle
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.*
import me.awabi2048.myworldmanager.repository.*
import me.awabi2048.myworldmanager.util.GuiItemFactory
import me.awabi2048.myworldmanager.util.GuiLoreActions
import me.awabi2048.myworldmanager.util.ItemTag
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class UserSettingsGui(private val plugin: MyWorldManager) {

    fun open(player: Player, showBackButton: Boolean? = null) {
        val lang = plugin.languageManager
        val session = plugin.playerWorldSessionManager.getSession(player.uniqueId)
        if (showBackButton != null) {
            session.showBackButton = showBackButton
        }

        val titleKey = "gui.user_settings.title"
        if (!lang.hasKey(player, titleKey)) {
            player.sendMessage("§c[MyWorldManager] Error: Missing translation key: $titleKey")
            return
        }
        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)


        val title = me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(lang.getMessage(player, titleKey))
        me.awabi2048.myworldmanager.util.GuiHelper.playMenuOpen(player, "user_settings")

        plugin.settingsSessionManager.updateSessionAction(player, java.util.UUID(0, 0), me.awabi2048.myworldmanager.session.SettingsAction.VIEW_SETTINGS, isGui = true)
        me.awabi2048.myworldmanager.util.GuiHelper.scheduleGuiTransitionReset(plugin, player)

        // Prepare Items
        val items = mutableListOf<ItemStack>()

        // 1. Notification
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

        // 2. Language
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

        // 3. Critical Settings Visibility
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

        val holder = UserSettingsGuiHolder()
        val inventory = Bukkit.createInventory(holder, totalRows * 9, title)
        holder.inv = inventory

        GuiItemFactory.applyStandardFrame(inventory)

        // 個人設定は5行レイアウトの中央行に集約し、他の設定系メニューと視線の位置を揃える。
        val centerRowStart = 2 * 9
        val firstSlot = centerRowStart + ((9 - items.size) / 2)
        items.forEachIndexed { index, item ->
            inventory.setItem(firstSlot + index, item)
        }

        if (session.showBackButton) {
            val backSlot = (totalRows - 1) * 9 + 4
            inventory.setItem(backSlot, me.awabi2048.myworldmanager.util.GuiHelper.createReturnItem(plugin, player, "user_settings"))
        }

        ManagedMenuPresenter.open(player, inventory)
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

    class UserSettingsGuiHolder : org.bukkit.inventory.InventoryHolder {
        lateinit var inv: org.bukkit.inventory.Inventory
        override fun getInventory(): org.bukkit.inventory.Inventory = inv
    }
}
