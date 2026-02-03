package me.awabi2048.myworldmanager.gui

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.type.DialogType
import me.awabi2048.myworldmanager.MyWorldManager
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

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
        bodyLines: List<Component>,
        confirmActionId: String,
        cancelActionId: String,
        confirmText: String? = null,
        cancelText: String? = null,
        confirmValue: String? = null,
        cancelValue: String? = null
    ) {


        val lang = plugin.languageManager
        val confirmLabel = confirmText ?: lang.getMessage(player, "gui.common.confirm")
        val cancelLabel = cancelText ?: lang.getMessage(player, "gui.common.cancel")

        val dialog = Dialog.create { builder ->
            builder.empty()
                .base(
                    DialogBase.builder(title)
                        .body(bodyLines.map { DialogBody.plainMessage(it) })
                        .build()
                )
                .type(
                    DialogType.confirmation(
                        ActionButton.create(
                            Component.text(confirmLabel, NamedTextColor.GREEN),
                            null,
                            100,
                            DialogAction.customClick(Key.key(confirmActionId), null)
                        ),
                        ActionButton.create(
                            Component.text(cancelLabel, NamedTextColor.RED),
                            null,
                            200,
                            DialogAction.customClick(Key.key(cancelActionId), null)
                        )
                    )
                )
        }

        player.showDialog(dialog)
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
