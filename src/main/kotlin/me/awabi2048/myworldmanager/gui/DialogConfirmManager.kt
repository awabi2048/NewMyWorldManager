package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuDialogButton
import com.awabi2048.ccsystem.api.gui.MenuDialogHandler
import com.awabi2048.ccsystem.api.gui.MenuDialogRequest
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.myworldmanager.MyWorldManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.Player

/**
 * 汎用的な確認ダイアログを管理するクラス
 */
object DialogConfirmManager {

    fun isNativeDialogEnabled(player: Player, plugin: MyWorldManager): Boolean {
        return !plugin.playerPlatformResolver.isBedrock(player)
    }

    fun showConfirmationByPreference(
        player: Player,
        plugin: MyWorldManager,
        title: Component,
        bodyLines: List<Component>,
        confirmActionId: String,
        cancelActionId: String = "mwm:confirm/cancel",
        confirmText: String? = null,
        cancelText: String? = null,
        onBedrockConfirm: (() -> Unit)? = null,
        onBedrockCancel: (() -> Unit)? = null,
        onGuiFallback: () -> Unit
    ) {
        onGuiFallback()
    }

    /**
     * 確認ダイアログを表示する
     * @param player 対象プレイヤー
     * @param plugin プラグインインスタンス
     * @param title タイトル
     * @param bodyLines 本文（複数行）
     * @param confirmActionId 承諾時のカスタムクリック用ID (例: "mwm:confirm/env_change")
     * @param cancelActionId キャンセル時のカスタムクリック用ID (例: "mwm:confirm/cancel")
     * @param confirmText 承諾ボタンのテキスト (デフォルト: Confirm)
     * @param cancelText キャンセルボタンのテキスト (デフォルト: Cancel)
     */
    fun showSimpleConfirmationDialog(
        player: Player,
        plugin: MyWorldManager,
        title: Component,
        bodyLines: List<Component>,
        confirmText: String? = null,
        cancelText: String? = null,
        onConfirm: () -> Unit,
        onCancel: () -> Unit,
        onBedrockFallback: (() -> Unit)? = null
    ) {


        val lang = plugin.languageManager
        val confirmLabel = confirmText ?: lang.getMessage(player, "gui.common.confirm")
        val cancelLabel = cancelText ?: lang.getMessage(player, "gui.common.cancel")

        if (plugin.playerPlatformResolver.isBedrock(player) &&
            plugin.bedrockUiRoutingService.shouldUseForm(player)
        ) {
            val opened =
                plugin.floodgateFormBridge.sendSimpleForm(
                    player = player,
                    title = PlainTextComponentSerializer.plainText().serialize(title),
                    content =
                        bodyLines.joinToString("\n") {
                            PlainTextComponentSerializer.plainText().serialize(it)
                        },
                    buttons = listOf(confirmLabel, cancelLabel),
                    onSelect = { index ->
                        if (index == 0) {
                            onConfirm()
                        } else {
                            onCancel()
                        }
                    },
                    onClosed = {
                        onCancel()
                    }
                )

            if (opened) {
                plugin.bedrockUiRoutingService.clearFormFailure(player)
                return
            }

            plugin.bedrockUiRoutingService.markFormFailure(player, "dialog_simple_confirm")
            if (onBedrockFallback != null) {
                onBedrockFallback()
                return
            }
        }

        CCSystem.getAPI().getMenuDialogService().show(
            player,
            MenuDialogRequest(
                owner = "myworldmanager",
                id = "simple-confirmation",
                title = title,
                body = bodyLines,
                confirm = MenuDialogButton(
                    Component.text(confirmLabel, NamedTextColor.GREEN),
                    MenuDialogHandler { _, _ ->
                        onConfirm()
                        MenuActionResult.Success(MenuUpdate.Close)
                    },
                ),
                cancel = MenuDialogButton(
                    Component.text(cancelLabel, NamedTextColor.RED),
                    MenuDialogHandler { _, _ ->
                        onCancel()
                        MenuActionResult.Success(MenuUpdate.Close)
                    },
                ),
            ),
        )
    }

    /**
     * ダイアログを安全に閉じる
     */
    fun safeCloseDialog(player: Player) {
        try {
            val method = try {
                player.javaClass.getMethod("closeDialog")
            } catch (e: NoSuchMethodException) {
                Player::class.java.getMethod("closeDialog")
            }
            method.invoke(player)
        } catch (e: Exception) {
            // ignore
        }
    }
}
