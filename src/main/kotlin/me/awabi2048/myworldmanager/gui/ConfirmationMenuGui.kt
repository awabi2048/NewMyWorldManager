package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuConfirmationDraft
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuSoundPolicy
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.myworldmanager.MyWorldManager
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class ConfirmationMenuGui(
    @Suppress("UNUSED_PARAMETER") plugin: MyWorldManager,
) {
    private val confirmations = CCSystem.getAPI().getMenuConfirmationService()

    fun open(
        player: Player,
        menuId: String,
        title: Component,
        centerItem: ItemStack,
        confirmItem: ItemStack,
        cancelItem: ItemStack,
        onConfirm: () -> MenuActionResult,
        onCancel: () -> MenuActionResult = { MenuActionResult.Success(MenuUpdate.Back) },
        onAbandon: () -> Unit = {},
        confirmSound: MenuSoundPolicy = MenuSoundPolicy.Default,
        cancelSound: MenuSoundPolicy = MenuSoundPolicy.Default,
    ) {
        confirmations.open(
            player,
            draft(
                menuId,
                title,
                centerItem,
                confirmItem,
                cancelItem,
                onConfirm,
                onCancel,
                onAbandon,
                confirmSound,
                cancelSound,
            ),
        )
    }

    fun prepareOpen(
        player: Player,
        menuId: String,
        title: Component,
        centerItem: ItemStack,
        confirmItem: ItemStack,
        cancelItem: ItemStack,
        onConfirm: () -> MenuActionResult,
        onCancel: () -> MenuActionResult = { MenuActionResult.Success(MenuUpdate.Back) },
        onAbandon: () -> Unit = {},
        confirmSound: MenuSoundPolicy = MenuSoundPolicy.Default,
        cancelSound: MenuSoundPolicy = MenuSoundPolicy.Default,
    ): MenuRoute =
        confirmations.prepare(
            player,
            draft(
                menuId,
                title,
                centerItem,
                confirmItem,
                cancelItem,
                onConfirm,
                onCancel,
                onAbandon,
                confirmSound,
                cancelSound,
            ),
        )

    fun openSimple(
        player: Player,
        menuId: String,
        title: Component,
        bodyLines: List<Component>,
        confirmLabel: Component,
        cancelLabel: Component,
        onConfirm: () -> MenuActionResult,
        onCancel: () -> MenuActionResult = { MenuActionResult.Success(MenuUpdate.Back) },
        onAbandon: () -> Unit = {},
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
            player,
            menuId,
            title,
            centerItem,
            confirmItem,
            cancelItem,
            onConfirm,
            onCancel,
            onAbandon,
        )
    }

    private fun draft(
        menuId: String,
        title: Component,
        centerItem: ItemStack,
        confirmItem: ItemStack,
        cancelItem: ItemStack,
        onConfirm: () -> MenuActionResult,
        onCancel: () -> MenuActionResult,
        onAbandon: () -> Unit,
        confirmSound: MenuSoundPolicy,
        cancelSound: MenuSoundPolicy,
    ): MenuConfirmationDraft =
        MenuConfirmationDraft(
            owner = OWNER,
            menuId = menuId,
            title = title,
            previewItem = centerItem,
            confirmItem = confirmItem,
            cancelItem = cancelItem,
            onConfirm = onConfirm,
            onCancel = onCancel,
            onAbandon = onAbandon,
            confirmSound = confirmSound,
            cancelSound = cancelSound,
        )

    private companion object {
        const val OWNER = "mwm"
    }
}
