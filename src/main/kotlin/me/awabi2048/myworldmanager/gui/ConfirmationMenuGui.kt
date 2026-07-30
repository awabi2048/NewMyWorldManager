package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiNameStyle
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuConfirmationDraft
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuSoundPolicy
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.myworldmanager.MyWorldManager
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import java.util.UUID

class ConfirmationMenuGui(
    @Suppress("UNUSED_PARAMETER") plugin: MyWorldManager,
) {
    private val confirmations = CCSystem.getAPI().getMenuConfirmationService()

    fun open(
        player: Player,
        menuId: String,
        title: Component,
        centerItem: GuiItemSpec,
        confirmItem: GuiItemSpec,
        cancelItem: GuiItemSpec,
        confirmActionText: String,
        cancelActionText: String,
        previewPlayerHeadOwner: UUID? = null,
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
                confirmActionText,
                cancelActionText,
                previewPlayerHeadOwner,
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
        centerItem: GuiItemSpec,
        confirmItem: GuiItemSpec,
        cancelItem: GuiItemSpec,
        confirmActionText: String,
        cancelActionText: String,
        previewPlayerHeadOwner: UUID? = null,
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
                confirmActionText,
                cancelActionText,
                previewPlayerHeadOwner,
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
        body: GuiLoreSpec,
        confirmLabel: String,
        cancelLabel: String,
        onConfirm: () -> MenuActionResult,
        onCancel: () -> MenuActionResult = { MenuActionResult.Success(MenuUpdate.Back) },
        onAbandon: () -> Unit = {},
    ) {
        val centerItem = GuiItemSpec(
            Material.PAPER,
            GuiNameSpec.Component(title),
            body,
            GuiElementRole.CONTENT,
            1,
        )
        val confirmItem = GuiItemSpec(
            Material.LIME_CONCRETE,
            GuiNameSpec.Text(confirmLabel, GuiNameStyle.DEFAULT),
            GuiLoreSpec.None,
            GuiElementRole.CONFIRM,
            1,
        )
        val cancelItem = GuiItemSpec(
            Material.RED_CONCRETE,
            GuiNameSpec.Text(cancelLabel, GuiNameStyle.DEFAULT),
            GuiLoreSpec.None,
            GuiElementRole.CANCEL,
            1,
        )
        open(
            player,
            menuId,
            title,
            centerItem,
            confirmItem,
            cancelItem,
            confirmLabel,
            cancelLabel,
            null,
            onConfirm,
            onCancel,
            onAbandon,
        )
    }

    private fun draft(
        menuId: String,
        title: Component,
        centerItem: GuiItemSpec,
        confirmItem: GuiItemSpec,
        cancelItem: GuiItemSpec,
        confirmActionText: String,
        cancelActionText: String,
        previewPlayerHeadOwner: UUID?,
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
            confirmActionText = confirmActionText,
            cancelActionText = cancelActionText,
            previewPlayerHeadOwner = previewPlayerHeadOwner,
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
