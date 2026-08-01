package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiMenuDisplaySpec
import com.awabi2048.ccsystem.api.gui.GuiMenuActionIntent
import com.awabi2048.ccsystem.api.gui.GuiMenuEntrySpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiNameStyle
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuActionContext
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuActionSafety
import com.awabi2048.ccsystem.api.gui.MenuGesture
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.service.PendingDecisionManager
import me.awabi2048.myworldmanager.util.GuiSpecFactory
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

    private val runtime = CCSystem.getAPI().getMenuRuntimeService()
    private val guiElements = CCSystem.getAPI().getGuiElementService()

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
            prepareOpen(page, returnPage, showBackButton, fromBedrockMenu),
        )
    }

    fun prepareOpen(
        page: Int = 0,
        returnPage: Int = 0,
        showBackButton: Boolean = false,
        fromBedrockMenu: Boolean = false,
    ): MenuRoute = route(page, returnPage, showBackButton, fromBedrockMenu)

    private fun render(player: Player, route: MenuRoute): InventoryMenuView {
        val entries = plugin.pendingDecisionManager.getPendingEntries(player.uniqueId)
        val page = CCSystem.getAPI().getGuiLayoutService().sevenColumnPage(entries.size, page(route))
        val layout = page.layout
        val pageEntries = entries.drop(page.startIndex).take(page.itemCount)
        val elements = mutableListOf<MenuElement>()
        elements += createInfoEntry(player, entries.size, page.page + 1, page.totalPages)
        pageEntries.forEachIndexed { index, entry ->
            elements += createEntry(player, entry, layout.itemSlots[index])
        }
        if (pageEntries.isEmpty()) {
            elements += createEmptyEntry(player, layout.itemSlots[layout.itemSlots.size / 2])
        }
        if (page.page > 0) {
            elements += navigationEntry(player, layout.previousPageSlot, false, page.page - 1)
        }
        if (page.page < page.totalPages - 1) {
            elements += navigationEntry(player, layout.nextPageSlot, true, page.page + 1)
        }
        if (me.awabi2048.myworldmanager.util.GuiHelper.canGoBack(player)) {
            elements += backEntry(player, layout.actionSlot)
        }
        return InventoryMenuView(
            layout.size,
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
        val center = PendingInteractionItemFactory.createDisplay(
            plugin = plugin,
            viewer = player,
            subjectUuid = entry.actorUuid,
            type = entry.type,
            worldName = worldName,
            createdAt = entry.createdAt,
            actionMode = PendingInteractionActionMode.REVIEW,
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

        val confirmItem = GuiSpecFactory.spec(
            org.bukkit.Material.LIME_CONCRETE,
            confirmLabel,
            GuiLoreSpec.None,
            GuiElementRole.CONFIRM,
        )
        val cancelItem = GuiSpecFactory.spec(
            org.bukkit.Material.RED_CONCRETE,
            cancelLabel,
            GuiLoreSpec.None,
            GuiElementRole.CANCEL,
        )

        plugin.confirmationMenuGui.open(
            player = player,
            menuId = "pending_list",
            title = title,
            centerItem = center.item,
            confirmItem = confirmItem,
            cancelItem = cancelItem,
            confirmActionText = confirmLabel,
            cancelActionText = cancelLabel,
            previewPlayerHeadOwner = center.playerHeadOwner,
            onConfirm = {
                if (plugin.pendingDecisionManager.resolveById(player, decisionId, confirmAction)) {
                    MenuActionResult.Success(MenuUpdate.Back)
                } else {
                    MenuActionResult.Rejected()
                }
            },
            onCancel = {
                if (intendedAction == null) {
                    plugin.pendingDecisionManager.resolveById(player, decisionId, false)
                }
                MenuActionResult.Success(MenuUpdate.Back)
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
        val center = PendingInteractionItemFactory.createDisplay(
            plugin = plugin,
            viewer = player,
            subjectUuid = entry.actorUuid,
            type = entry.type,
            worldName = worldName,
            createdAt = entry.createdAt,
            actionMode = PendingInteractionActionMode.REVIEW,
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

        val confirmItem = GuiSpecFactory.spec(
            org.bukkit.Material.LIME_CONCRETE,
            confirmLabel,
            GuiLoreSpec.None,
            GuiElementRole.CONFIRM,
        )
        val cancelItem = GuiSpecFactory.spec(
            org.bukkit.Material.RED_CONCRETE,
            cancelLabel,
            GuiLoreSpec.None,
            GuiElementRole.CANCEL,
        )

        plugin.confirmationMenuGui.open(
            player = player,
            menuId = "pending_list",
            title = title,
            centerItem = center.item,
            confirmItem = confirmItem,
            cancelItem = cancelItem,
            confirmActionText = confirmLabel,
            cancelActionText = cancelLabel,
            previewPlayerHeadOwner = center.playerHeadOwner,
            onConfirm = {
                if (plugin.pendingDecisionManager.resolveById(player, decisionId, confirmAction)) {
                    MenuActionResult.Success(MenuUpdate.Back)
                } else {
                    MenuActionResult.Rejected()
                }
            },
            onCancel = {
                if (intendedAction == null) {
                    plugin.pendingDecisionManager.resolveById(player, decisionId, false)
                }
                MenuActionResult.Success(MenuUpdate.Back)
            },
            onAbandon = { player.sendMessage(PENDING_MESSAGE) },
        )
    }

    private fun createEntry(
        player: Player,
        entry: PendingDecisionManager.PendingEntryView,
        slot: Int,
    ): MenuElement {
        val worldName = entry.worldUuid?.let { plugin.worldConfigRepository.findByUuid(it)?.name }
            ?: plugin.languageManager.getMessage(player, "general.unknown")
        return PendingInteractionItemFactory.createElement(
            plugin = plugin,
            viewer = player,
            slot = slot,
            subjectUuid = entry.actorUuid,
            type = entry.type,
            worldName = worldName,
            createdAt = entry.createdAt,
            actionMode = PendingInteractionActionMode.REVIEW,
            actionId = ACTION_OPEN,
            actionPayload = mapOf(DECISION_ID to entry.id.toString()),
        )
    }

    private fun createEmptyEntry(player: Player, slot: Int): MenuElement =
        guiElements.menuDisplay(
            GuiMenuDisplaySpec(
                slot = slot,
                item = GuiItemSpec(
                    material = Material.BARRIER,
                    name = GuiNameSpec.Component(plugin.languageManager.getComponent(player, "gui.pending_list.empty.name")),
                    lore = GuiLoreSpec.Blocks(
                        listOf(
                            GuiLoreBlock(
                                plugin.languageManager.getMessageList(player, "gui.pending_list.empty.lore")
                                    .map(GuiLoreLine::Text),
                            ),
                        ),
                    ),
                    role = GuiElementRole.CONTENT,
                    amount = 1,
                ),
            ),
        )

    private fun createInfoEntry(player: Player, count: Int, page: Int, pages: Int): MenuElement =
        guiElements.menuDisplay(
            GuiMenuDisplaySpec(
                slot = 4,
                item = GuiItemSpec(
                    material = Material.BOOK,
                    name = GuiNameSpec.Text(
                        plugin.languageManager.getMessage(player, "gui.pending_list.info.name"),
                        GuiNameStyle.DEFAULT,
                    ),
                    lore = GuiLoreSpec.Blocks(
                        listOf(
                            GuiLoreBlock(
                                listOf(
                                    GuiLoreLine.Data(
                                        plugin.languageManager.getMessage(player, "gui.pending_list.info.count_label"),
                                        count,
                                        "§e",
                                    ),
                                    GuiLoreLine.Data(
                                        plugin.languageManager.getMessage(player, "gui.pending_list.info.page_label"),
                                        "$page/$pages",
                                        "§e",
                                    ),
                                ),
                            ),
                        ),
                    ),
                    role = GuiElementRole.CONTENT,
                    amount = 1,
                ),
            ),
        )

    private fun navigationEntry(player: Player, slot: Int, next: Boolean, targetPage: Int): MenuElement {
        val key = if (next) "gui.common.next_page" else "gui.common.prev_page"
        val iconId = if (next) "next_page" else "prev_page"
        return guiElements.menuEntry(
            player,
            GuiMenuEntrySpec(
                slot = slot,
                material = plugin.menuConfigManager.getIconMaterial("pending_list", iconId, Material.ARROW),
                name = GuiNameSpec.Component(plugin.languageManager.getComponent(player, key)),
                role = GuiElementRole.NAVIGATION,
                actions = listOf(
                    menuGestureAction(
                        actionId = ACTION_PAGE,
                        gesture = MenuGesture.ANY,
                        label = plugin.languageManager.getMessage(player, key),
                        payload = mapOf(PAGE to targetPage.toString()),
                        safety = MenuActionSafety.NAVIGATION_ONLY,
                    ),
                ),
            ),
        )
    }

    private fun backEntry(player: Player, slot: Int): MenuElement =
        guiElements.backEntry(
            player,
            slot,
            plugin.menuConfigManager.getIconMaterial("world_settings", "back", Material.REDSTONE),
        )

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
