package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.api.localization.generated.CommonKeys

import me.awabi2048.myworldmanager.MyWorldManager
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

/**
 * 汎用的な確認ダイアログを管理するクラス
 */
object DialogConfirmManager {

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
        bodyLines: List<String>,
        confirmText: String? = null,
        cancelText: String? = null,
        onConfirm: () -> MenuActionResult,
        onCancel: () -> MenuActionResult,
    ) {
        val lang = plugin.languageManager
        val confirmLabel = confirmText ?: lang.getMessage(player, CommonKeys.GUI_COMMON_CONFIRM)
        val cancelLabel = cancelText ?: lang.getMessage(player, CommonKeys.GUI_COMMON_CANCEL)

        plugin.confirmationMenuGui.openSimple(
            player = player,
            menuId = "simple-confirmation",
            title = title,
            body = GuiLoreSpec.Rich(
                bodyLines.map(GuiLoreLine::Text),
                GuiLoreFrame.BOTH,
            ),
            confirmLabel = confirmLabel,
            cancelLabel = cancelLabel,
            onConfirm = onConfirm,
            onCancel = onCancel,
        )
    }

    /**
     * ダイアログを安全に閉じる
     */
}
