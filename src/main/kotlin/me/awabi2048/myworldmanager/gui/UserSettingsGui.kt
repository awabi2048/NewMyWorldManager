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
        
        val holder = UserSettingsGuiHolder()
        val inventory = Bukkit.createInventory(holder, 45, title)
        holder.inv = inventory

        // 背景
        val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
        for (i in 0..8) inventory.setItem(i, blackPane)
        for (i in 36..44) inventory.setItem(i, blackPane)

        // 通知設定 (Slot 20)
        val notifyStatus = if (stats.visitorNotificationEnabled) lang.getMessage(player, "messages.status_on") else lang.getMessage(player, "messages.status_off")
        inventory.setItem(20, createItem(
            Material.BELL,
            lang.getMessage(player, "gui.user_settings.notification.display"),
            lang.getMessageList(player, "gui.user_settings.notification.lore", mapOf("status" to notifyStatus)),
            ItemTag.TYPE_GUI_USER_SETTING_NOTIFICATION
        ))

        // 言語設定 (Slot 24)
        val languageName = lang.getMessage(player, "general.language.${stats.language}")
        inventory.setItem(24, createItem(
            Material.WRITABLE_BOOK,
            lang.getMessage(player, "gui.user_settings.language.display"),
            lang.getMessageList(player, "gui.user_settings.language.lore", mapOf("language" to languageName)),
            ItemTag.TYPE_GUI_USER_SETTING_LANGUAGE
        ))



        // 戻るボタン (Slot 40)
        if (session.showBackButton) {
            inventory.setItem(40, me.awabi2048.myworldmanager.util.GuiHelper.createReturnItem(plugin, player, "user_settings"))
        }

        // 空きスロットを灰色板ガラスで埋める
        val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
        for (i in 0 until 45) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, grayPane)
            }
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
