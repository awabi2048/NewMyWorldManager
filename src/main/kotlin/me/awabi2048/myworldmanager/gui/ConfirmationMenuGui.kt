package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuActionContext
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuActionSoundPolicy
import com.awabi2048.ccsystem.api.gui.MenuCloseContext
import com.awabi2048.ccsystem.api.gui.MenuCloseHandler
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuSoundPolicy
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import me.awabi2048.myworldmanager.MyWorldManager
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class ConfirmationMenuGui(private val plugin: MyWorldManager) {
    private val runtime = CCSystem.getAPI().getMenuRuntimeService()
    private val sessions = ConcurrentHashMap<UUID, ConfirmationSession>()

    init {
        runtime.register(
            InventoryMenuDefinition(
                owner = OWNER,
                id = ROUTE_ID,
                renderer = { context -> render(context.route) },
                actions = mapOf(
                    ACTION_CONFIRM to MenuActionHandler(::confirm),
                    ACTION_CANCEL to MenuActionHandler(::cancel),
                ),
                onClose = MenuCloseHandler(::closed),
            ),
        )
    }

    fun open(
        player: Player,
        menuId: String,
        title: Component,
        centerItem: ItemStack,
        confirmItem: ItemStack,
        cancelItem: ItemStack,
        onConfirm: () -> Unit,
        onCancel: () -> Unit = {},
        onAbandon: () -> Unit = {},
        returnOnConfirm: Boolean = false,
        confirmSound: MenuSoundPolicy = MenuSoundPolicy.Default,
        cancelSound: MenuSoundPolicy = MenuSoundPolicy.Default,
    ) {
        val route = prepareOpen(
            player,
            menuId,
            title,
            centerItem,
            confirmItem,
            cancelItem,
            onConfirm,
            onCancel,
            onAbandon,
            returnOnConfirm,
            confirmSound,
            cancelSound,
        )
        if (!runtime.navigate(player, route)) {
            route.payload[TOKEN]?.let { runCatching { UUID.fromString(it) }.getOrNull() }?.let(sessions::remove)
        }
    }

    fun prepareOpen(
        player: Player,
        menuId: String,
        title: Component,
        centerItem: ItemStack,
        confirmItem: ItemStack,
        cancelItem: ItemStack,
        onConfirm: () -> Unit,
        onCancel: () -> Unit = {},
        onAbandon: () -> Unit = {},
        returnOnConfirm: Boolean = false,
        confirmSound: MenuSoundPolicy = MenuSoundPolicy.Default,
        cancelSound: MenuSoundPolicy = MenuSoundPolicy.Default,
    ): MenuRoute {
        val token = UUID.randomUUID()
        sessions[token] = ConfirmationSession(
            player.uniqueId,
            title,
            centerItem.clone(),
            confirmItem.clone(),
            cancelItem.clone(),
            onConfirm,
            onCancel,
            onAbandon,
            returnOnConfirm,
            confirmSound,
            cancelSound,
        )
        return MenuRoute(OWNER, ROUTE_ID, mapOf(TOKEN to token.toString(), MENU_ID to menuId))
    }

    fun openSimple(
        player: Player,
        menuId: String,
        title: Component,
        bodyLines: List<Component>,
        confirmLabel: Component,
        cancelLabel: Component,
        onConfirm: () -> Unit,
        onCancel: () -> Unit = {},
        onAbandon: () -> Unit = {},
        returnOnConfirm: Boolean = false,
    ) {
        val centerItem = ItemStack(Material.PAPER).apply {
            editMeta {
                it.displayName(title)
                it.lore(bodyLines)
            }
        }
        val confirmItem = ItemStack(Material.LIME_CONCRETE).apply {
            editMeta { it.displayName(confirmLabel) }
        }
        val cancelItem = ItemStack(Material.RED_CONCRETE).apply {
            editMeta { it.displayName(cancelLabel) }
        }
        open(
            player = player,
            menuId = menuId,
            title = title,
            centerItem = centerItem,
            confirmItem = confirmItem,
            cancelItem = cancelItem,
            onConfirm = onConfirm,
            onCancel = onCancel,
            onAbandon = onAbandon,
            returnOnConfirm = returnOnConfirm,
        )
    }

    private fun render(route: MenuRoute): InventoryMenuView {
        val session = session(route)
        val layout = CCSystem.getAPI().getGuiLayoutService().confirmation45()
        return InventoryMenuView(
            size = layout.size,
            title = session.title,
            elements = listOf(
                MenuElement(
                    layout.confirmSlot,
                    session.confirmItem.clone(),
                    GuiElementRole.CONFIRM,
                    ACTION_CONFIRM,
                    sounds = MenuActionSoundPolicy(success = session.confirmSound),
                ),
                MenuElement(layout.previewSlot, session.centerItem.clone(), GuiElementRole.CONTENT),
                MenuElement(
                    layout.cancelSlot,
                    session.cancelItem.clone(),
                    GuiElementRole.CANCEL,
                    ACTION_CANCEL,
                    sounds = MenuActionSoundPolicy(success = session.cancelSound),
                ),
            ),
        )
    }

    private fun confirm(context: MenuActionContext): MenuActionResult {
        val session = removeOwned(context.player, context.route) ?: return MenuActionResult.Rejected()
        session.onConfirm()
        return MenuActionResult.Success(if (session.returnOnConfirm) MenuUpdate.Back else MenuUpdate.Close)
    }

    private fun cancel(context: MenuActionContext): MenuActionResult {
        val session = removeOwned(context.player, context.route) ?: return MenuActionResult.Rejected()
        session.onCancel()
        return MenuActionResult.Success(MenuUpdate.Back)
    }

    private fun closed(context: MenuCloseContext) {
        val session = removeOwned(context.player, context.route) ?: return
        session.onAbandon()
    }

    private fun session(route: MenuRoute): ConfirmationSession =
        token(route)?.let(sessions::get) ?: error("確認画面セッションが見つかりません")

    private fun removeOwned(player: Player, route: MenuRoute): ConfirmationSession? {
        val token = token(route) ?: return null
        val session = sessions[token] ?: return null
        if (session.playerId != player.uniqueId) return null
        return sessions.remove(token)
    }

    private fun token(route: MenuRoute): UUID? =
        route.payload[TOKEN]?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    private data class ConfirmationSession(
        val playerId: UUID,
        val title: Component,
        val centerItem: ItemStack,
        val confirmItem: ItemStack,
        val cancelItem: ItemStack,
        val onConfirm: () -> Unit,
        val onCancel: () -> Unit,
        val onAbandon: () -> Unit,
        val returnOnConfirm: Boolean,
        val confirmSound: MenuSoundPolicy,
        val cancelSound: MenuSoundPolicy,
    )

    private companion object {
        const val OWNER = "mwm"
        const val ROUTE_ID = "confirmation"
        const val TOKEN = "token"
        const val MENU_ID = "menu_id"
        const val ACTION_CONFIRM = "confirm"
        const val ACTION_CANCEL = "cancel"
    }
}
