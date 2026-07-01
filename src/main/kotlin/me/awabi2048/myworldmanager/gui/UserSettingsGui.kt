package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.*
import me.awabi2048.myworldmanager.repository.*
import me.awabi2048.myworldmanager.util.GuiItemFactory
import me.awabi2048.myworldmanager.util.ItemTag
import me.awabi2048.myworldmanager.util.StructuredLore
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
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
        items.add(createItem(
            Material.BELL,
            lang.getMessage(player, "gui.user_settings.notification.display"),
            settingLore(player, "notification", mapOf("status" to notifyStatus)),
            ItemTag.TYPE_GUI_USER_SETTING_NOTIFICATION
        ))

        // 2. Language
        val currentLocale = lang.resolveLocale(player)
        val languageName = lang.getMessage(player, "general.language.$currentLocale")
        items.add(createItem(
            Material.WRITABLE_BOOK,
            lang.getMessage(player, "gui.user_settings.language.display"),
            settingLore(player, "language", mapOf("language" to languageName)),
            ItemTag.TYPE_GUI_USER_SETTING_LANGUAGE
        ))

        // 3. Critical Settings Visibility
        val criticalStatus = if (stats.criticalSettingsEnabled) {
            lang.getMessage(player, "messages.status_visible")
        } else {
            lang.getMessage(player, "messages.status_hidden")
        }
        items.add(createItem(
            Material.RECOVERY_COMPASS,
            lang.getMessage(player, "gui.user_settings.critical_settings_visibility.display"),
            settingLore(player, "critical_settings_visibility", mapOf("status" to criticalStatus)),
            ItemTag.TYPE_GUI_USER_SETTING_CRITICAL_VISIBILITY
        ))

        items.add(createItem(
            Material.COMPASS,
            lang.getMessage(player, "gui.user_settings.tour_navigation.display"),
            tourNavigationLore(player, stats.tourNavigationMode),
            ItemTag.TYPE_GUI_USER_SETTING_TOUR_NAVIGATION
        ))

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

        player.openInventory(inventory)
    }

    private fun settingLore(player: Player, setting: String, placeholders: Map<String, Any>): GuiLoreSpec.Blocks {
        val prefix = "gui.user_settings.$setting.blocks"
        return StructuredLore.setting(
            plugin.languageManager.getMessageList(player, "$prefix.description", placeholders),
            plugin.languageManager.getMessageList(player, "$prefix.current", placeholders),
            plugin.languageManager.getMessageList(player, "$prefix.action", placeholders)
        )
    }

    private fun tourNavigationLore(player: Player, currentMode: TourNavigationMode): GuiLoreSpec.Blocks {
        val lang = plugin.languageManager
        val description = lang.getMessageList(player, "gui.user_settings.tour_navigation.blocks.description")
        val options = TourNavigationMode.entries.map { mode ->
            StructuredLore.SelectionOption(
                label = lang.getMessage(player, "gui.user_settings.tour_navigation.mode.${mode.name.lowercase()}"),
                selected = mode == currentMode,
                selectedColor = "§b",
                inactiveColor = "§7"
            )
        }
        val action = lang.getMessageList(player, "gui.user_settings.tour_navigation.blocks.action")

        // 現在値は選択肢リスト内のマーカーで示し、説明ブロックに一覧をまとめる。
        return StructuredLore.selectionSetting(description, options, action)
    }

    private fun createItem(material: Material, name: String, lore: GuiLoreSpec, tag: String): ItemStack {
        return GuiItemFactory.item(material, name, lore, tag)
    }

    class UserSettingsGuiHolder : org.bukkit.inventory.InventoryHolder {
        lateinit var inv: org.bukkit.inventory.Inventory
        override fun getInventory(): org.bukkit.inventory.Inventory = inv
    }
}
