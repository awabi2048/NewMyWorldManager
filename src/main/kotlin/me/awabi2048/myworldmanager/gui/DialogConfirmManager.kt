package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.MyWorldManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player

/**
 * 汎用的な確認ダイアログを管理するクラス
 */
object DialogConfirmManager {

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
    ) {
        val lang = plugin.languageManager
        val confirmLabel = confirmText ?: lang.getMessage(player, "gui.common.confirm")
        val cancelLabel = cancelText ?: lang.getMessage(player, "gui.common.cancel")

        plugin.confirmationMenuGui.openSimple(
            player = player,
            menuId = "simple-confirmation",
            title = title,
            bodyLines = bodyLines,
            confirmLabel = Component.text(confirmLabel, NamedTextColor.GREEN),
            cancelLabel = Component.text(cancelLabel, NamedTextColor.RED),
            onConfirm = onConfirm,
            onCancel = onCancel,
        )
    }

    /**
     * ダイアログを安全に閉じる
     */
}
