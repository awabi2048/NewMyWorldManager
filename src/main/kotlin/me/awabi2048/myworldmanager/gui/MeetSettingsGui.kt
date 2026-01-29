package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class MeetSettingsGui(private val plugin: MyWorldManager) {

    fun open(player: Player) {
        val lang = plugin.languageManager
        val titleKey = "gui.meet_settings.title"
        if (!lang.hasKey(player, titleKey)) {
            player.sendMessage("§c[MyWorldManager] Error: Missing translation key: $titleKey")
            return
        }
        

        
        val title = Component.text(lang.getMessage(player, titleKey))
        me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(plugin, player, "user_settings", title)
        val inventory = Bukkit.createInventory(null, 27, title)

        // 背景
        val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
        for (i in 0..26) inventory.setItem(i, blackPane)

        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        val currentStatus = stats.meetStatus

        // ステータスセレクター (Slot 13)
        // サイクル: JOIN_ME -> ASK_ME -> BUSY
        val statusList = listOf("JOIN_ME", "ASK_ME", "BUSY")
        
        val statusNameKey = "general.status.${currentStatus.lowercase()}"
        val statusName = if (lang.hasKey(player, statusNameKey)) lang.getMessage(player, statusNameKey) else currentStatus

        val lore = mutableListOf<String>()
        lore.add(lang.getMessage(player, "gui.meet_settings.status_selector.current", mapOf("status" to statusName)))
        lore.add("")
        
        for (status in statusList) {
            val sKey = "general.status.${status.lowercase()}"
            val sName = if (lang.hasKey(player, sKey)) lang.getMessage(player, sKey) else status
            val isActive = status == currentStatus
            val prefix = if (isActive) lang.getMessage(player, "gui.meet_settings.status_selector.active_prefix") else lang.getMessage(player, "gui.meet_settings.status_selector.inactive_prefix")
            val color = if (isActive) "§a" else "§7"
            lore.add("$prefix$color$sName")
        }
        
        lore.add("")
        lore.add(lang.getMessage(player, "gui.meet_settings.status_selector.desc_header"))
        val descKey = "general.status.description.${currentStatus.lowercase()}"
        if (lang.hasKey(player, descKey)) {
            lore.add(lang.getMessage(player, descKey))
        }
        
        lore.add("")
        lore.add(lang.getMessage(player, "gui.meet_settings.status_selector.click_cycle"))

        // Icon based on status
        val mat = when (currentStatus) {
            "JOIN_ME" -> Material.LIME_DYE
            "ASK_ME" -> Material.ORANGE_DYE
            "BUSY" -> Material.RED_DYE
            else -> Material.GRAY_DYE
        }

        inventory.setItem(13, createItem(
            mat,
            lang.getMessage(player, "gui.meet_settings.status_selector.display"),
            lore,
            ItemTag.TYPE_GUI_MEET_STATUS_SELECTOR
        ))

        // 戻るボタン (Slot 22) => actually 22 is fine for 27 size inv, but let's put it at bottom center.
        // wait, 27 size inv has indices 0-26. Center is 13.
        // Let's put back button at 26 (bottom right) or 18 (bottom left) or 22 (bottom center).
        
        // Using standard layout style if possible? Usually back button is center bottom.
        val backBtn = ItemStack(Material.ARROW)
        val backMeta = backBtn.itemMeta ?: return
        backMeta.displayName(lang.getComponent(player, "gui.common.return"))
        backMeta.lore(listOf(lang.getComponent(player, "gui.common.return_desc")))
        backBtn.itemMeta = backMeta
        ItemTag.tagItem(backBtn, ItemTag.TYPE_GUI_RETURN)
        inventory.setItem(22, backBtn)

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
}
