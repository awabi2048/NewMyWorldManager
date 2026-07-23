package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.ui.ManagedMenuPresenter

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.*
import me.awabi2048.myworldmanager.repository.*
import me.awabi2048.myworldmanager.session.AdminGuiSession
import me.awabi2048.myworldmanager.session.PortalSortType
import me.awabi2048.myworldmanager.session.SettingsAction
import me.awabi2048.myworldmanager.util.GuiItemFactory
import me.awabi2048.myworldmanager.util.GuiLoreActions
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.*

class AdminPortalGui(private val plugin: MyWorldManager) {

    private val menuId = "admin_portals"
    fun open(player: Player, page: Int? = null, fromAdminMenu: Boolean? = null) {


        val session = plugin.adminGuiSessionManager.getSession(player.uniqueId)
        if (fromAdminMenu != null) {
            session.fromAdminMenu = fromAdminMenu
        }
        val currentPage = page ?: session.portalPage
        session.portalPage = currentPage
        plugin.settingsSessionManager.updateSessionAction(player, player.uniqueId, SettingsAction.ADMIN_PORTAL_GUI, isGui = true)

        // ソートを適用してポータルリストを取得
        val portals = getSortedPortals(session)
        val layout = GuiHelper.pagedListLayout()
        val itemsPerPage = layout.itemSlots.size
        val totalPages = if (portals.isEmpty()) 1 else (portals.size + itemsPerPage - 1) / itemsPerPage
        val safePage = currentPage.coerceIn(0, totalPages - 1)
        session.portalPage = safePage

        val lang = plugin.languageManager
        val titleComp = me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(lang.getComponent(player, "gui.admin_portals.title"))
        me.awabi2048.myworldmanager.util.GuiHelper.playMenuOpen(player, "admin_portals")
        val inventory = Bukkit.createInventory(null, layout.size, titleComp)

        GuiItemFactory.applyStandardFrame(inventory)

        // ポータルアイテムの配置
        val startIndex = safePage * itemsPerPage
        val pagePortals = portals.drop(startIndex).take(itemsPerPage)

        pagePortals.forEachIndexed { index, portal ->
            inventory.setItem(layout.itemSlots[index], createPortalItem(player, portal))
        }

        // 下部ボタンレイアウト:
        // [<前] [ ] [ ] [情報] [ ] [ソート] [ ] [ ] [次>]
        //  46   47  48  49   50   51   52  53


        // ページネーション
        if (safePage > 0) {
            inventory.setItem(46, createNavButton(lang.getMessage("gui.common.prev_page"), Material.ARROW, safePage - 1, true))
        }

        if (safePage < totalPages - 1) {
            inventory.setItem(53, createNavButton(lang.getMessage("gui.common.next_page"), Material.ARROW, safePage + 1, false))
        }

        // 情報
        GuiHelper.setSettingsFooter(inventory, null, createInfoButton(portals.size, safePage + 1, totalPages))

        // ソート
        inventory.setItem(51, createSortButton(player, session))

        // 戻るボタン
        if (session.fromAdminMenu) {
            inventory.setItem(52, createNavButton(lang.getMessage("gui.common.back"), Material.REDSTONE, 0, false)) // ページ0はダミー、タイプでタグ付け
            val backItem = inventory.getItem(52)!!
            ItemTag.tagItem(backItem, ItemTag.TYPE_GUI_RETURN)
        }

        ManagedMenuPresenter.open(player, inventory)
        me.awabi2048.myworldmanager.util.GuiHelper.scheduleGuiTransitionReset(plugin, player)
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
            val configName = plugin.config.getString("portal_targets.${portal.targetRuntimeName}")
            configName ?: portal.targetRuntimeName ?: "Unknown"
        }

        val displayTitle = lang.getMessage(player, "gui.admin_portals.portal_item.name", mapOf("id" to destName))
        meta.displayName(me.awabi2048.myworldmanager.util.GuiItemFactory.legacy(displayTitle))

        val ownerName = PlayerNameUtil.getNameOrDefault(portal.ownerUuid, "Unknown")


        meta.lore(CCSystem.getAPI().getLoreService().render(GuiLoreSpec.Blocks(listOf(
            GuiLoreBlock(listOf(
                GuiLoreLine.Data(lang.getMessage(player, "gui.admin_portals.portal_item.owner"), ownerName, "§f"),
                GuiLoreLine.Data(lang.getMessage(player, "gui.admin_portals.portal_item.world"), portal.worldKey, "§f"),
                GuiLoreLine.Data(
                    lang.getMessage(player, "gui.admin_portals.portal_item.coordinates"),
                    "${portal.x}, ${portal.y}, ${portal.z}",
                    "§f"
                )
            )),
            GuiLoreBlock(listOf(
                GuiLoreLine.Action(lang.getMessage(player, "gui.settings.click.left"), lang.getMessage(player, "gui.admin_portals.portal_item.action.teleport")),
                GuiLoreLine.Action(lang.getMessage(player, "gui.settings.click.right"), lang.getMessage(player, "gui.admin_portals.portal_item.action.remove"))
            ))
        ))))
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_PORTAL)
        ItemTag.setPortalUuid(item, portal.id)

        return item
    }

    private fun createNavButton(label: String, material: Material, targetPage: Int, isPrev: Boolean): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.displayName(me.awabi2048.myworldmanager.util.GuiItemFactory.legacy(label))
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
        meta.lore(GuiItemFactory.menuLore(listOf(
            GuiLoreLine.Data(lang.getMessage(null, "gui.admin.info.total_count_label"), totalCount, "§b"),
            GuiLoreLine.Data(lang.getMessage(null, "gui.admin.info.page_label"), "$current/$total", "§a")
        )))
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_INFO)
        return item
    }

    private fun createSortButton(player: Player, session: AdminGuiSession): ItemStack {
        val item = ItemStack(Material.HOPPER)
        val meta = item.itemMeta ?: return item
        val lang = plugin.languageManager

        meta.displayName(lang.getComponent(player, "gui.admin_portals.sort.display"))

        val options = PortalSortType.values().map { sortType ->
            sortType to lang.getMessage(player, sortType.displayKey)
        }
        meta.lore(
            CCSystem.getAPI().getLoreService().render(
                GuiLoreSpec.Rich(buildList {
                    add(GuiLoreLine.Data(
                        lang.getMessage(player, "gui.admin_portals.sort.label"),
                        options.first { it.first == session.portalSortBy }.second,
                        "\u00A7e"
                    ))
                    add(GuiLoreLine.Spacer)
                    options.forEach { (sortType, displayName) ->
                        val selected = sortType == session.portalSortBy
                        add(GuiLoreLine.Option(displayName, selected, "§e", "§7"))
                    }
                    add(GuiLoreLine.Spacer)
                    addAll(GuiLoreActions.cyclePreviousNext(lang, player))
                }, GuiLoreFrame.BOTH)
            )
        )
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_ADMIN_PORTAL_SORT)
        return item
    }

}
