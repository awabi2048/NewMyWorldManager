package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.service.WorldService
import me.awabi2048.myworldmanager.session.SettingsAction
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.UUID

class AdminCommandGui(private val plugin: MyWorldManager) {
    private val titleKey = "gui.admin_menu.title"

    fun open(player: Player) {
        val lang = plugin.languageManager
        val title = lang.getComponent(player, titleKey).color(NamedTextColor.DARK_GRAY).decorate(TextDecoration.BOLD)
        me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(plugin, player, "admin_manage", title)
        
        plugin.settingsSessionManager.updateSessionAction(player, player.uniqueId, SettingsAction.ADMIN_MENU, isGui = true)
        
        val inventory = Bukkit.createInventory(null, 45, title)

        // 背景 (黒の板ガラス)
        val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
        for (i in 0..8) inventory.setItem(i, blackPane)
        for (i in 36..44) inventory.setItem(i, blackPane)

        // 背景 (灰色の板ガラス)
        val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
        for (i in 0 until inventory.size) {
            if (inventory.getItem(i) == null) inventory.setItem(i, grayPane)
        }

        // Slot 19: データ更新 (update-data)
        inventory.setItem(19, createItem(
            Material.COMMAND_BLOCK,
            lang.getMessage(player, "gui.admin_menu.update_data.display"),
            lang.getMessageList(player, "gui.admin_menu.update_data.lore"),
            ItemTag.TYPE_GUI_ADMIN_UPDATE_DATA
        ))

        // Slot 20: テンプレート修復 (complete_templates)
        inventory.setItem(20, createItem(
            Material.ANVIL,
            lang.getMessage(player, "gui.admin_menu.repair_templates.display"),
            lang.getMessageList(player, "gui.admin_menu.repair_templates.lore"),
            ItemTag.TYPE_GUI_ADMIN_REPAIR_TEMPLATES
        ))

        // Slot 21: テンプレート作成 (create-template)
        inventory.setItem(21, createItem(
            Material.CRAFTING_TABLE,
            lang.getMessage(player, "gui.admin_menu.create_template.display"),
            lang.getMessageList(player, "gui.admin_menu.create_template.lore"),
            ItemTag.TYPE_GUI_ADMIN_CREATE_TEMPLATE
        ))

        // Slot 23: アーカイブ実行 (archive)
        inventory.setItem(23, createItem(
            Material.CHEST,
            lang.getMessage(player, "gui.admin_menu.archive.display"),
            lang.getMessageList(player, "gui.admin_menu.archive.lore"),
            ItemTag.TYPE_GUI_ADMIN_ARCHIVE_ALL
        ))

        // Check current world status
        val currentWorld = player.world
        val isMyWorld = currentWorld.name.startsWith("my_world.") || plugin.worldConfigRepository.findAll().any { it.customWorldName == currentWorld.name }

        // Slot 24: ワールド変換 (convert) / 紐づけ解除 (unlink)
        if (isMyWorld) {
            inventory.setItem(24, createItem(
                Material.CHAIN,
                lang.getMessage(player, "gui.admin_menu.unlink.display"),
                lang.getMessageList(player, "gui.admin_menu.unlink.lore"),
                ItemTag.TYPE_GUI_ADMIN_UNLINK
            ))
        } else {
            inventory.setItem(24, createItem(
                Material.WRITABLE_BOOK,
                lang.getMessage(player, "gui.admin_menu.convert.display"),
                lang.getMessageList(player, "gui.admin_menu.convert.lore"),
                ItemTag.TYPE_GUI_ADMIN_CONVERT
            ))
        }

        // Slot 25: ワールドエクスポート (export)
        if (isMyWorld) {
            inventory.setItem(25, createItem(
                Material.DISPENSER,
                lang.getMessage(player, "gui.admin_menu.export.display"),
                lang.getMessageList(player, "gui.admin_menu.export.lore"),
                ItemTag.TYPE_GUI_ADMIN_EXPORT
            ))
        } else {
            val lore = lang.getMessageList(player, "gui.admin_menu.export.lore").toMutableList()
            lore.add("")
            lore.add("§c※現在のワールドはマイワールドではないため")
            lore.add("§c  エクスポートできません。")
            inventory.setItem(25, createItem(
                Material.BARRIER,
                lang.getMessage(player, "gui.admin_menu.export.display"),
                lore,
                ItemTag.TYPE_GUI_INFO
            ))
        }

        // Slot 38: ワールド一覧 (info)
        inventory.setItem(38, createItem(
            Material.FILLED_MAP,
            lang.getMessage(player, "gui.admin_menu.info.display"),
            lang.getMessageList(player, "gui.admin_menu.info.lore"),
            ItemTag.TYPE_GUI_ADMIN_INFO
        ))

        // Slot 40: プラグイン情報
        inventory.setItem(40, createItem(
            Material.NETHER_STAR,
            "§bMyWorldManager",
            listOf("§7Version: " + plugin.description.version, "§7Author: awabi2048"),
            ItemTag.TYPE_GUI_INFO
        ))

        // Slot 42: ポータル管理 (portals)
        inventory.setItem(42, createItem(
            Material.END_PORTAL_FRAME,
            lang.getMessage(player, "gui.admin_menu.portals.display"),
            lang.getMessageList(player, "gui.admin_menu.portals.lore"),
            ItemTag.TYPE_GUI_ADMIN_PORTALS
        ))

        player.openInventory(inventory)
        me.awabi2048.myworldmanager.util.GuiHelper.scheduleGuiTransitionReset(plugin, player)
    }

    // --- 確認画面 ---

    fun openConvertConfirmation(player: Player, mode: WorldService.ConversionMode) {
        val lang = plugin.languageManager
        val action = if (mode == WorldService.ConversionMode.NORMAL) SettingsAction.ADMIN_CONVERT_NORMAL_CONFIRM else SettingsAction.ADMIN_CONVERT_ADMIN_CONFIRM
        val titleKey = if (mode == WorldService.ConversionMode.NORMAL) "gui.admin_menu.convert.confirm_normal" else "gui.admin_menu.convert.confirm_admin"
        val title = lang.getComponent(player, titleKey)
        val confirmId = if (mode == WorldService.ConversionMode.NORMAL) {
            "mwm:confirm/admin/convert_normal"
        } else {
            "mwm:confirm/admin/convert_admin"
        }
        showDialogOrGuiConfirmation(player, player.uniqueId, action, title, confirmId) {
            me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(plugin, player, "admin_manage", title)
            val inventory = Bukkit.createInventory(null, 27, title)
            setupConfirmationGui(inventory, player, mode == WorldService.ConversionMode.NORMAL)
            player.openInventory(inventory)
            me.awabi2048.myworldmanager.util.GuiHelper.scheduleGuiTransitionReset(plugin, player)
        }
    }

    fun openUnlinkConfirmation(player: Player) {
        val lang = plugin.languageManager
        val title = lang.getComponent(player, "gui.admin_menu.unlink.confirm_title")
        showDialogOrGuiConfirmation(
            player,
            player.uniqueId,
            SettingsAction.ADMIN_UNLINK_CONFIRM,
            title,
            "mwm:confirm/admin/unlink"
        ) {
            me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(plugin, player, "admin_manage", title)
            val inventory = Bukkit.createInventory(null, 27, title)
            setupConfirmationGui(inventory, player, true)
            player.openInventory(inventory)
            me.awabi2048.myworldmanager.util.GuiHelper.scheduleGuiTransitionReset(plugin, player)
        }
    }

    fun openExportConfirmation(player: Player, worldName: String) {
        val lang = plugin.languageManager
        val title = lang.getComponent(player, "gui.admin_menu.export.confirm_title")
        val extraInfo = listOf(lang.getMessage(player, "gui.admin_menu.export.target_world", mapOf("world" to worldName)))
        showDialogOrGuiConfirmation(
            player,
            player.uniqueId,
            SettingsAction.ADMIN_EXPORT_CONFIRM,
            title,
            "mwm:confirm/admin/export",
            extraInfo
        ) {
            me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(plugin, player, "admin_manage", title)
            val inventory = Bukkit.createInventory(null, 27, title)
            setupConfirmationGui(inventory, player, true, extraInfo = extraInfo)
            player.openInventory(inventory)
            me.awabi2048.myworldmanager.util.GuiHelper.scheduleGuiTransitionReset(plugin, player)
        }
    }

    fun openArchiveAllConfirmation(player: Player) {
        val lang = plugin.languageManager
        val title = lang.getComponent(player, "gui.admin_menu.archive.confirm_title")
        showDialogOrGuiConfirmation(
            player,
            player.uniqueId,
            SettingsAction.ADMIN_ARCHIVE_ALL_CONFIRM,
            title,
            "mwm:confirm/admin/archive_all"
        ) {
            me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(plugin, player, "admin_manage", title)
            val inventory = Bukkit.createInventory(null, 27, title)
            setupConfirmationGui(inventory, player, true)
            player.openInventory(inventory)
            me.awabi2048.myworldmanager.util.GuiHelper.scheduleGuiTransitionReset(plugin, player)
        }
    }
    
    fun openUpdateDataConfirmation(player: Player) {
        val lang = plugin.languageManager
        val title = lang.getComponent(player, "gui.admin_menu.update_data.confirm_title")
        showDialogOrGuiConfirmation(
            player,
            player.uniqueId,
            SettingsAction.ADMIN_UPDATE_DATA_CONFIRM,
            title,
            "mwm:confirm/admin/update_data"
        ) {
            me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(plugin, player, "admin_manage", title)
            val inventory = Bukkit.createInventory(null, 27, title)
            setupConfirmationGui(inventory, player, true)
            player.openInventory(inventory)
            me.awabi2048.myworldmanager.util.GuiHelper.scheduleGuiTransitionReset(plugin, player)
        }
    }

    fun openRepairTemplatesConfirmation(player: Player) {
        val lang = plugin.languageManager
        val title = lang.getComponent(player, "gui.admin_menu.repair_templates.confirm_title")
        showDialogOrGuiConfirmation(
            player,
            player.uniqueId,
            SettingsAction.ADMIN_REPAIR_TEMPLATES_CONFIRM,
            title,
            "mwm:confirm/admin/repair_templates"
        ) {
            me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(plugin, player, "admin_manage", title)
            val inventory = Bukkit.createInventory(null, 27, title)
            setupConfirmationGui(inventory, player, true)
            player.openInventory(inventory)
            me.awabi2048.myworldmanager.util.GuiHelper.scheduleGuiTransitionReset(plugin, player)
        }
    }

    fun openArchiveWorldConfirmation(player: Player, worldName: String, worldUuid: UUID) {
        val lang = plugin.languageManager
        val title = lang.getComponent(player, "gui.admin_menu.archive_world.confirm_title")
        val extraInfo = listOf(lang.getMessage(player, "gui.admin_menu.archive_world.target_world", mapOf("world" to worldName)))
        showDialogOrGuiConfirmation(
            player,
            worldUuid,
            SettingsAction.ADMIN_ARCHIVE_WORLD_CONFIRM,
            title,
            "mwm:confirm/admin/archive_world/$worldUuid",
            extraInfo
        ) {
            me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(plugin, player, "admin_manage", title)
            val inventory = Bukkit.createInventory(null, 27, title)
            setupConfirmationGui(inventory, player, true, extraInfo = extraInfo)
            player.openInventory(inventory)
            me.awabi2048.myworldmanager.util.GuiHelper.scheduleGuiTransitionReset(plugin, player)
        }
    }

    fun openUnarchiveWorldConfirmation(player: Player, worldName: String, worldUuid: UUID) {
        val lang = plugin.languageManager
        val title = lang.getComponent(player, "gui.admin_menu.unarchive_world.confirm_title")
        val extraInfo = listOf(lang.getMessage(player, "gui.admin_menu.unarchive_world.target_world", mapOf("world" to worldName)))
        showDialogOrGuiConfirmation(
            player,
            worldUuid,
            SettingsAction.ADMIN_UNARCHIVE_WORLD_CONFIRM,
            title,
            "mwm:confirm/admin/unarchive_world/$worldUuid",
            extraInfo
        ) {
            me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(plugin, player, "admin_manage", title)
            val inventory = Bukkit.createInventory(null, 27, title)
            setupConfirmationGui(inventory, player, true, extraInfo = extraInfo)
            player.openInventory(inventory)
            me.awabi2048.myworldmanager.util.GuiHelper.scheduleGuiTransitionReset(plugin, player)
        }
    }

    private fun showDialogOrGuiConfirmation(
        player: Player,
        worldUuid: UUID,
        action: SettingsAction,
        title: Component,
        confirmActionId: String,
        extraInfo: List<String> = emptyList(),
        onGuiFallback: () -> Unit
    ) {
        val lang = plugin.languageManager
        plugin.settingsSessionManager.updateSessionAction(player, worldUuid, action, isGui = true)
        val bodyLines = (extraInfo + "" + lang.getMessage(player, "gui.common.confirm_warning"))
            .map { LegacyComponentSerializer.legacySection().deserialize(it) }

        DialogConfirmManager.showConfirmationByPreference(
            player,
            plugin,
            title,
            bodyLines,
            confirmActionId,
            "mwm:confirm/cancel",
            lang.getMessage(player, "gui.common.confirm"),
            lang.getMessage(player, "gui.common.cancel")
        ) {
            onGuiFallback()
        }
    }

    private fun setupConfirmationGui(inventory: Inventory, player: Player, isDanger: Boolean, extraInfo: List<String> = emptyList()) {
        val lang = plugin.languageManager
        
        // 背景
        val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
        for (i in 0..8) inventory.setItem(i, blackPane)
        for (i in 18..26) inventory.setItem(i, blackPane)
        
        val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
        for (i in 9..17) {
             if (inventory.getItem(i) == null) inventory.setItem(i, grayPane)
        }

        // メイン情報
        val infoLore = extraInfo.toMutableList()
        infoLore.add("")
        infoLore.add(lang.getMessage(player, "gui.common.confirm_warning"))
        
        inventory.setItem(13, createItem(
            Material.PAPER,
            lang.getMessage(player, "gui.common.confirmation"),
            infoLore,
            ItemTag.TYPE_GUI_INFO
        ))

        // 実行ボタン
        inventory.setItem(15, createItem(
            if (isDanger) Material.RED_WOOL else Material.LIME_WOOL,
            lang.getMessage(player, "gui.common.confirm"),
            listOf(lang.getMessage(player, "gui.common.confirm_desc")),
            ItemTag.TYPE_GUI_CONFIRM
        ))

        // キャンセルボタン
        inventory.setItem(11, createItem(
            Material.GREEN_WOOL, // キャンセルは安全な色で
            lang.getMessage(player, "gui.common.cancel"),
            listOf(lang.getMessage(player, "gui.common.cancel_desc")),
            ItemTag.TYPE_GUI_CANCEL
        ))
    }

    private fun createItem(material: Material, name: String, lore: List<String>, tagType: String): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.displayName(LegacyComponentSerializer.legacySection().deserialize(name).decoration(TextDecoration.ITALIC, false))
        meta.lore(lore.map { LegacyComponentSerializer.legacySection().deserialize(it).decoration(TextDecoration.ITALIC, false) })
        item.itemMeta = meta
        ItemTag.tagItem(item, tagType)
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
