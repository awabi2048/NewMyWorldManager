package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.*
import me.awabi2048.myworldmanager.repository.*
import me.awabi2048.myworldmanager.session.AdminGuiSession
import me.awabi2048.myworldmanager.session.PortalSortType
import me.awabi2048.myworldmanager.session.SettingsAction
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.*

class AdminPortalGui(private val plugin: MyWorldManager) {

    private val menuId = "admin_portals"
    private val itemsPerPage = 36

    fun open(player: Player, page: Int? = null, fromAdminMenu: Boolean? = null) {
        plugin.soundManager.playMenuOpenSound(player, "admin_portals")
        
        val session = plugin.adminGuiSessionManager.getSession(player.uniqueId)
        if (fromAdminMenu != null) {
            session.fromAdminMenu = fromAdminMenu
        }
        val currentPage = page ?: session.portalPage
        session.portalPage = currentPage
        plugin.settingsSessionManager.updateSessionAction(player, player.uniqueId, SettingsAction.ADMIN_PORTAL_GUI, isGui = true)

        // ソートを適用してポータルリストを取得
        val portals = getSortedPortals(session)
        val totalPages = if (portals.isEmpty()) 1 else (portals.size + itemsPerPage - 1) / itemsPerPage
        val safePage = currentPage.coerceIn(0, totalPages - 1)
        session.portalPage = safePage

        val lang = plugin.languageManager
        val title = lang.getMessage(player, "gui.admin_portals.title")
        val inventory = Bukkit.createInventory(null, 54, Component.text(title, NamedTextColor.DARK_GRAY, TextDecoration.BOLD))

        // 1行目を黒の板ガラスで敷き詰める
        val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
        for (i in 0..8) inventory.setItem(i, blackPane)

        // ポータルアイテムの配置
        val startIndex = safePage * itemsPerPage
        val pagePortals = portals.drop(startIndex).take(itemsPerPage)

        pagePortals.forEachIndexed { index, portal ->
            inventory.setItem(index + 9, createPortalItem(player, portal))
        }

        // 下部ボタンレイアウト:
        // [<前] [ ] [ ] [情報] [ ] [ソート] [ ] [ ] [次>]
        //  46   47  48  49   50   51   52  53
        
        // 最終行を黒の板ガラスで敷き詰める（RAYOUT_RULES.md準拠）
        for (i in 45..53) inventory.setItem(i, blackPane)

        // ページネーション
        if (safePage > 0) {
            inventory.setItem(46, createNavButton(lang.getMessage("gui.common.prev_page"), Material.ARROW, safePage - 1, true))
        }
        
        if (safePage < totalPages - 1) {
            inventory.setItem(53, createNavButton(lang.getMessage("gui.common.next_page"), Material.ARROW, safePage + 1, false))
        }

        // 情報
        inventory.setItem(49, createInfoButton(portals.size, safePage + 1, totalPages))
        
        // ソート
        inventory.setItem(51, createSortButton(player, session))

        // 戻るボタン
        if (session.fromAdminMenu) {
            inventory.setItem(52, createNavButton(lang.getMessage("gui.common.back"), Material.ARROW, 0, false)) // ページ0はダミー、タイプでタグ付け
            val backItem = inventory.getItem(52)!!
            ItemTag.tagItem(backItem, ItemTag.TYPE_GUI_RETURN)
        }

        // 背景
        val background = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
        for (slot in 0 until inventory.size) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, background)
            }
        }

        player.openInventory(inventory)
    }

    private fun getSortedPortals(session: AdminGuiSession): List<PortalData> {
        val all = plugin.portalRepository.findAll().toList()
        return when (session.portalSortBy) {
            PortalSortType.CREATED_DESC -> all.sortedByDescending { it.createdAt }
            PortalSortType.CREATED_ASC -> all.sortedBy { it.createdAt }
        }
    }

    private fun createPortalItem(player: Player, portal: PortalData): ItemStack {
        val lang = plugin.languageManager
        val item = ItemStack(Material.END_PORTAL_FRAME)
        val meta = item.itemMeta ?: return item
        
        // 行き先の判定
        val destName = if (portal.worldUuid != null) {
            val worldData = plugin.worldConfigRepository.findByUuid(portal.worldUuid!!)
            worldData?.name ?: "Unknown World"
        } else {
            val configName = plugin.config.getString("portal_targets.${portal.targetWorldName}")
            configName ?: portal.targetWorldName ?: "Unknown"
        }

        val displayTitle = lang.getMessage(player, "gui.admin_portals.portal_item.name").replace("{0}", destName)
        meta.displayName(LegacyComponentSerializer.legacySection().deserialize(displayTitle).decoration(TextDecoration.ITALIC, false))
        
        val lore = mutableListOf<Component>()
        val ownerName = Bukkit.getOfflinePlayer(portal.ownerUuid).name ?: "Unknown"
        
        lore.add(lang.getComponent(player, "gui.admin_portals.portal_item.lore_owner", ownerName))
        lore.add(lang.getComponent(player, "gui.admin_portals.portal_item.lore_world", portal.worldName))
        lore.add(lang.getComponent(player, "gui.admin_portals.portal_item.lore_location", portal.x, portal.y, portal.z))
        
        lore.add(Component.empty())
        lore.add(lang.getComponent(player, "gui.admin_portals.portal_item.lore_teleport"))
        lore.add(lang.getComponent(player, "gui.admin_portals.portal_item.lore_remove"))
        
        meta.lore(lore)
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_PORTAL)
        ItemTag.setPortalUuid(item, portal.id)
        
        return item
    }

    private fun createNavButton(label: String, material: Material, targetPage: Int, isPrev: Boolean): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.displayName(LegacyComponentSerializer.legacySection().deserialize(label).decoration(TextDecoration.ITALIC, false))
        item.itemMeta = meta
        ItemTag.tagItem(item, if (isPrev) ItemTag.TYPE_GUI_NAV_PREV else ItemTag.TYPE_GUI_NAV_NEXT)
        ItemTag.setTargetPage(item, targetPage)
        return item
    }

    private fun createInfoButton(totalCount: Int, current: Int, total: Int): ItemStack {
        val item = ItemStack(Material.PAPER)
        val meta = item.itemMeta ?: return item
        val lang = plugin.languageManager
        meta.displayName(lang.getComponent(null, "gui.admin.info.display"))
        meta.lore(listOf(
            lang.getComponent(null, "gui.admin.info.total_count", totalCount),
            lang.getComponent(null, "gui.admin.info.page", current, total)
        ))
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_INFO)
        return item
    }

    private fun createSortButton(player: Player, session: AdminGuiSession): ItemStack {
        val item = ItemStack(Material.HOPPER)
        val meta = item.itemMeta ?: return item
        val lang = plugin.languageManager
        
        meta.displayName(lang.getComponent(player, "gui.admin_portals.sort.display"))
        
        val lore = mutableListOf<Component>()
        PortalSortType.values().forEach { sortType ->
            val prefix = if (sortType == session.portalSortBy) "§a» " else "§8- "
            lore.add(LegacyComponentSerializer.legacySection().deserialize("$prefix${lang.getMessage(player, sortType.displayKey)}"))
        }
        lore.add(Component.empty())
        lore.add(lang.getComponent(player, "gui.admin.filter.click_cycle"))
        
        meta.lore(lore)
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_ADMIN_PORTAL_SORT)
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
