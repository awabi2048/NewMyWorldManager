package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.*
import me.awabi2048.myworldmanager.repository.*
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
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

        
        val title = Component.text(lang.getMessage(player, titleKey))
        me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(plugin, player, "user_settings", title, UserSettingsGuiHolder::class.java)

        plugin.settingsSessionManager.updateSessionAction(player, java.util.UUID(0, 0), me.awabi2048.myworldmanager.session.SettingsAction.VIEW_SETTINGS, isGui = true)
        me.awabi2048.myworldmanager.util.GuiHelper.scheduleGuiTransitionReset(plugin, player)
        
        // Prepare Items
        val items = mutableListOf<ItemStack>()

        // 1. Notification
        val notifyStatus = if (stats.visitorNotificationEnabled) lang.getMessage(player, "messages.status_on") else lang.getMessage(player, "messages.status_off")
        items.add(createItem(
            Material.BELL,
            lang.getMessage(player, "gui.user_settings.notification.display"),
            lang.getMessageList(player, "gui.user_settings.notification.lore", mapOf("status" to notifyStatus)),
            ItemTag.TYPE_GUI_USER_SETTING_NOTIFICATION
        ))

        // 2. Language
        val languageName = lang.getMessage(player, "general.language.${stats.language}")
        items.add(createItem(
            Material.WRITABLE_BOOK,
            lang.getMessage(player, "gui.user_settings.language.display"),
            lang.getMessageList(player, "gui.user_settings.language.lore", mapOf("language" to languageName)),
            ItemTag.TYPE_GUI_USER_SETTING_LANGUAGE
        ))
        
        // 3. Beta Features
        val betaStatus = if (stats.betaFeaturesEnabled) lang.getMessage(player, "messages.status_on") else lang.getMessage(player, "messages.status_off")
        val betaLore = lang.getMessageList(player, "gui.user_settings.beta_features.lore", mapOf("status" to betaStatus))
            .ifEmpty { listOf("§7实验的な機能の使用:", "§7・ダイアログ入力", "", "§7現在の状態: $betaStatus") } // Fallback if key missing

        items.add(createItem(
            Material.EXPERIENCE_BOTTLE,
            lang.getMessage(player, "gui.user_settings.beta_features.display").ifEmpty { "§eベータ機能" },
            betaLore,
            ItemTag.TYPE_GUI_USER_SETTING_BETA_FEATURES
        ))
        
        // Calculate Size
        val itemsPerRow = 7
        val contentRows = (items.size + itemsPerRow - 1) / itemsPerRow
        val totalRows = (contentRows + 2).coerceAtLeast(3) // Min 3 rows
        
        val holder = UserSettingsGuiHolder()
        val inventory = Bukkit.createInventory(holder, totalRows * 9, title)
        holder.inv = inventory

        // Fill Background
        val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
        val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
        
        // Top and Bottom Rows (Black)
        for (i in 0 until 9) inventory.setItem(i, blackPane)
        for (i in (totalRows - 1) * 9 until totalRows * 9) inventory.setItem(i, blackPane)
        
        // Middle Rows (Gray)
        for (row in 1 until totalRows - 1) {
             for (col in 0 until 9) {
                 inventory.setItem(row * 9 + col, grayPane)
             }
        }
        
        // Place Items
        items.forEachIndexed { index, item ->
            val rowOffset = index / itemsPerRow
            val colOffset = index % itemsPerRow
            // Start at 2nd slot (index 1) of the content row
            val slot = (rowOffset + 1) * 9 + 1 + colOffset
            inventory.setItem(slot, item)
        }

        // Back Button (Center of last row)
        if (session.showBackButton) {
            val backSlot = (totalRows - 1) * 9 + 4
            inventory.setItem(backSlot, me.awabi2048.myworldmanager.util.GuiHelper.createReturnItem(plugin, player, "user_settings"))
        }

        player.openInventory(inventory)
    }

    private fun createItem(material: Material, name: String, loreLines: List<String>, tag: String): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        
        meta.displayName(LegacyComponentSerializer.legacySection().deserialize(name).decoration(TextDecoration.ITALIC, false))
        meta.lore(loreLines.map { LegacyComponentSerializer.legacySection().deserialize(it).decoration(TextDecoration.ITALIC, false) })
        
        item.itemMeta = meta
        ItemTag.tagItem(item, tag)
        return item
    }

    private fun createDecorationItem(material: Material): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.displayName(Component.empty())
        meta.isHideTooltip = true
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_DECORATION)
        return item
    }

    class UserSettingsGuiHolder : org.bukkit.inventory.InventoryHolder {
        lateinit var inv: org.bukkit.inventory.Inventory
        override fun getInventory(): org.bukkit.inventory.Inventory = inv
    }
}
