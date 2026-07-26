package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuActionContext
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.service.PendingDecisionManager
import me.awabi2048.myworldmanager.util.GuiItemFactory
import me.awabi2048.myworldmanager.util.ItemTag
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

class PendingInteractionGui(private val plugin: MyWorldManager) {

    private val itemsPerPage = 28
    private val runtime = CCSystem.getAPI().getMenuRuntimeService()

    init {
        runtime.register(
            InventoryMenuDefinition(
                owner = OWNER,
                id = ROUTE_ID,
                renderer = { context -> render(context.player, context.route) },
                actions = mapOf(
                    ACTION_PAGE to MenuActionHandler(::changePage),
                    ACTION_BACK to MenuActionHandler(::back),
                    ACTION_OPEN to MenuActionHandler(::openEntry),
                ),
            ),
        )
    }

    fun open(
        player: Player,
        page: Int = 0,
        returnPage: Int = 0,
        showBackButton: Boolean = false,
        fromBedrockMenu: Boolean = false
    ) {
        runtime.navigate(
            player,
            route(page, returnPage, showBackButton, fromBedrockMenu),
        )
    }

    private fun render(player: Player, route: MenuRoute): InventoryMenuView {
        val entries = plugin.pendingDecisionManager.getPendingEntries(player.uniqueId)
        val maxPage = if (entries.isEmpty()) 1 else ((entries.size - 1) / itemsPerPage) + 1
        val currentPage = page(route).coerceIn(0, maxPage - 1)
        val start = currentPage * itemsPerPage
        val pageEntries = entries.drop(start).take(itemsPerPage)
        val contentRows = if (pageEntries.isEmpty()) 1 else ((pageEntries.size - 1) / 7) + 1
        val rowCount = (contentRows + 2).coerceIn(3, 6)
        val footerStart = (rowCount - 1) * 9
        val elements = mutableListOf<MenuElement>()
        pageEntries.forEachIndexed { index, entry ->
            val row = index / 7
            val col = index % 7
            val slot = (row + 1) * 9 + 1 + col
            elements += MenuElement(
                slot,
                createEntryItem(player, entry),
                GuiElementRole.ACTION,
                ACTION_OPEN,
                mapOf(DECISION_ID to entry.id.toString()),
            )
        }
        if (pageEntries.isEmpty()) {
            elements += MenuElement(22, createEmptyItem(player), GuiElementRole.CONTENT)
        }
        if (currentPage > 0) {
            elements += MenuElement(
                footerStart + 1,
                me.awabi2048.myworldmanager.util.GuiHelper.createPrevPageItem(
                    plugin,
                    player,
                    "pending_list",
                    currentPage - 1
                ),
                GuiElementRole.NAVIGATION,
                ACTION_PAGE,
                mapOf(PAGE to (currentPage - 1).toString()),
            )
        }
        if (start + pageEntries.size < entries.size) {
            elements += MenuElement(
                footerStart + 8,
                me.awabi2048.myworldmanager.util.GuiHelper.createNextPageItem(
                    plugin,
                    player,
                    "pending_list",
                    currentPage + 1
                ),
                GuiElementRole.NAVIGATION,
                ACTION_PAGE,
                mapOf(PAGE to (currentPage + 1).toString()),
            )
        }
        elements += MenuElement(
            footerStart,
            me.awabi2048.myworldmanager.util.GuiHelper.createReturnItem(plugin, player, "pending_list"),
            GuiElementRole.BACK,
            ACTION_BACK,
        )
        return InventoryMenuView(
            rowCount * 9,
            me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(
                plugin.languageManager.getComponent(player, "gui.pending_list.title"),
            ),
            elements,
        )
    }

    private fun changePage(context: MenuActionContext): MenuActionResult {
        val target = context.payload[PAGE]?.toIntOrNull() ?: return MenuActionResult.Rejected()
        return MenuActionResult.Success(
            MenuUpdate.Replace(
                route(target, returnPage(context.route), showBack(context.route), fromBedrock(context.route)),
            ),
        )
    }

    private fun back(context: MenuActionContext): MenuActionResult {
        return MenuActionResult.Success(MenuUpdate.Back)
    }

    private fun openEntry(context: MenuActionContext): MenuActionResult {
        val decisionId = context.payload[DECISION_ID]
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: return MenuActionResult.Rejected()
        if (plugin.playerPlatformResolver.isBedrock(context.player)) {
            openBedrockDecisionForm(context.player, decisionId)
        } else {
            openJavaDecisionDialog(context.player, decisionId, page(context.route))
        }
        return MenuActionResult.Success(MenuUpdate.None)
    }

    fun openDecision(player: Player, decisionId: UUID, page: Int = 0, intendedAction: Boolean? = null) {
        if (plugin.playerPlatformResolver.isBedrock(player)) {
            openBedrockDecisionForm(player, decisionId, intendedAction)
        } else {
            openJavaDecisionDialog(player, decisionId, page, intendedAction)
        }
    }

    private fun openJavaDecisionDialog(player: Player, decisionId: UUID, page: Int, intendedAction: Boolean? = null) {
        val entry = plugin.pendingDecisionManager.getPendingEntry(player.uniqueId, decisionId)
            ?: run {
                player.sendMessage(plugin.languageManager.getMessage(player, "messages.myworld_pending_none"))
                return
            }

        val worldName = entry.worldUuid?.let { plugin.worldConfigRepository.findByUuid(it)?.name }
            ?: plugin.languageManager.getMessage(player, "general.unknown")
        val actorName = PlayerNameUtil.getNameOrDefault(entry.actorUuid, plugin.languageManager.getMessage(player, "general.unknown"))
        val typeLabel = typeLabel(player, entry.type)

        val title = plugin.languageManager.getComponent(player, "gui.pending_list.confirm.title")
        val center = PendingInteractionItemFactory.createItem(
            plugin = plugin,
            viewer = player,
            subjectUuid = entry.actorUuid,
            type = entry.type,
            worldName = worldName,
            createdAt = entry.createdAt,
            decisionId = decisionId,
            actionMode = PendingInteractionActionMode.REVIEW,
            itemTagType = ItemTag.TYPE_GUI_INFO
        )

        val confirmLabel = plugin.languageManager.getMessage(
            player,
            if (intendedAction == false) "gui.pending_list.confirm.deny" else "gui.pending_list.confirm.accept"
        )
        val cancelLabel = plugin.languageManager.getMessage(
            player,
            if (intendedAction == null) "gui.pending_list.confirm.deny" else "gui.common.cancel"
        )
        val confirmAction = intendedAction ?: true

        val confirmItem = GuiItemFactory.item(
            org.bukkit.Material.LIME_CONCRETE,
            confirmLabel,
            GuiLoreSpec.None,
            ItemTag.TYPE_GUI_CONFIRM
        )
        val cancelItem = GuiItemFactory.item(
            org.bukkit.Material.RED_CONCRETE,
            cancelLabel,
            GuiLoreSpec.None,
            ItemTag.TYPE_GUI_CANCEL
        )

        plugin.confirmationMenuGui.open(
            player = player,
            menuId = "pending_list",
            title = title,
            centerItem = center,
            confirmItem = confirmItem,
            cancelItem = cancelItem,
            onConfirm = { plugin.pendingDecisionManager.resolveById(player, decisionId, confirmAction) },
            onCancel = {
                if (intendedAction == null) {
                    plugin.pendingDecisionManager.resolveById(player, decisionId, false)
                }
            },
            onAbandon = { player.sendMessage(PENDING_MESSAGE) },
        )
    }

    private fun openBedrockDecisionForm(
        player: Player,
        decisionId: UUID,
        intendedAction: Boolean? = null
    ) {
        val entry = plugin.pendingDecisionManager.getPendingEntry(player.uniqueId, decisionId)
            ?: run {
                player.sendMessage(plugin.languageManager.getMessage(player, "messages.myworld_pending_none"))
                return
            }

        val worldName = entry.worldUuid?.let { plugin.worldConfigRepository.findByUuid(it)?.name }
            ?: plugin.languageManager.getMessage(player, "general.unknown")
        val actorName = PlayerNameUtil.getNameOrDefault(entry.actorUuid, plugin.languageManager.getMessage(player, "general.unknown"))
        val typeLabel = typeLabel(player, entry.type)

        val title = plugin.languageManager.getComponent(player, "gui.pending_list.confirm.title")
        val center = PendingInteractionItemFactory.createItem(
            plugin = plugin,
            viewer = player,
            subjectUuid = entry.actorUuid,
            type = entry.type,
            worldName = worldName,
            createdAt = entry.createdAt,
            decisionId = decisionId,
            actionMode = PendingInteractionActionMode.REVIEW,
            itemTagType = ItemTag.TYPE_GUI_INFO
        )

        val confirmLabel = plugin.languageManager.getMessage(
            player,
            if (intendedAction == false) "gui.pending_list.confirm.deny" else "gui.pending_list.confirm.accept"
        )
        val cancelLabel = plugin.languageManager.getMessage(
            player,
            if (intendedAction == null) "gui.pending_list.confirm.deny" else "gui.common.cancel"
        )
        val confirmAction = intendedAction ?: true

        val confirmItem = GuiItemFactory.item(
            org.bukkit.Material.LIME_CONCRETE,
            confirmLabel,
            GuiLoreSpec.None,
            ItemTag.TYPE_GUI_CONFIRM
        )
        val cancelItem = GuiItemFactory.item(
            org.bukkit.Material.RED_CONCRETE,
            cancelLabel,
            GuiLoreSpec.None,
            ItemTag.TYPE_GUI_CANCEL
        )

        plugin.confirmationMenuGui.open(
            player = player,
            menuId = "pending_list",
            title = title,
            centerItem = center,
            confirmItem = confirmItem,
            cancelItem = cancelItem,
            onConfirm = { plugin.pendingDecisionManager.resolveById(player, decisionId, confirmAction) },
            onCancel = {
                if (intendedAction == null) {
                    plugin.pendingDecisionManager.resolveById(player, decisionId, false)
                }
            },
            onAbandon = { player.sendMessage(PENDING_MESSAGE) },
        )
    }

    private fun createEntryItem(player: Player, entry: PendingDecisionManager.PendingEntryView): ItemStack {
        val worldName = entry.worldUuid?.let { plugin.worldConfigRepository.findByUuid(it)?.name }
            ?: plugin.languageManager.getMessage(player, "general.unknown")
        return PendingInteractionItemFactory.createItem(
            plugin = plugin,
            viewer = player,
            subjectUuid = entry.actorUuid,
            type = entry.type,
            worldName = worldName,
            createdAt = entry.createdAt,
            decisionId = entry.id,
            actionMode = PendingInteractionActionMode.REVIEW,
            itemTagType = ItemTag.TYPE_GUI_PENDING_ENTRY
        )
    }

    private fun createEmptyItem(player: Player): ItemStack {
        val item = ItemStack(Material.BARRIER)
        val meta = item.itemMeta ?: return item
        meta.displayName(plugin.languageManager.getComponent(player, "gui.pending_list.empty.name"))
        meta.lore(GuiItemFactory.menuLore(
            plugin.languageManager.getMessageList(player, "gui.pending_list.empty.lore").map(com.awabi2048.ccsystem.api.gui.GuiLoreLine::Text)
        ))
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_INFO)
        return item
    }

    private fun typeLabel(player: Player, type: PendingDecisionManager.PendingType): String {
        return when (type) {
            PendingDecisionManager.PendingType.WORLD_INVITE -> plugin.languageManager.getMessage(player, "gui.pending_list.type.world_invite")
            PendingDecisionManager.PendingType.MEMBER_INVITE -> plugin.languageManager.getMessage(player, "gui.pending_list.type.member_invite")
            PendingDecisionManager.PendingType.MEMBER_REQUEST -> plugin.languageManager.getMessage(player, "gui.pending_list.type.member_request")
            PendingDecisionManager.PendingType.MEET_REQUEST -> plugin.languageManager.getMessage(player, "gui.pending_list.type.meet_request")
            PendingDecisionManager.PendingType.VISIT_REQUEST -> plugin.languageManager.getMessage(player, "gui.pending_list.type.visit_request")
        }
    }

    private fun formatDateTime(timestamp: Long): String {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(timestamp))
    }

    private fun createDecorationItem(material: Material): ItemStack {
        return GuiItemFactory.decoration(material)
    }

    private fun route(page: Int, returnPage: Int, showBack: Boolean, fromBedrock: Boolean) =
        MenuRoute(
            OWNER,
            ROUTE_ID,
            mapOf(
                PAGE to page.toString(),
                RETURN_PAGE to returnPage.toString(),
                SHOW_BACK to showBack.toString(),
                FROM_BEDROCK to fromBedrock.toString(),
            ),
        )

    private fun page(route: MenuRoute) = route.payload[PAGE]?.toIntOrNull() ?: 0
    private fun returnPage(route: MenuRoute) = route.payload[RETURN_PAGE]?.toIntOrNull() ?: 0
    private fun showBack(route: MenuRoute) = route.payload[SHOW_BACK].toBoolean()
    private fun fromBedrock(route: MenuRoute) = route.payload[FROM_BEDROCK].toBoolean()

    private companion object {
        const val OWNER = "mwm"
        const val ROUTE_ID = "pending_interactions"
        const val PAGE = "page"
        const val RETURN_PAGE = "return_page"
        const val SHOW_BACK = "show_back"
        const val FROM_BEDROCK = "from_bedrock"
        const val DECISION_ID = "decision_id"
        const val ACTION_PAGE = "page"
        const val ACTION_BACK = "back"
        const val ACTION_OPEN = "open"
        const val PENDING_MESSAGE = "§7保留しました。/myworld メニューから、保留中の申請などを確認できます。"
    }
}
