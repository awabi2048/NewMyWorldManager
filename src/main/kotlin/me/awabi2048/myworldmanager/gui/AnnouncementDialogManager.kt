package me.awabi2048.myworldmanager.gui

import io.papermc.paper.connection.PlayerGameConnection
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.event.player.PlayerCustomClickEvent
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import me.awabi2048.myworldmanager.MyWorldManager
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin

/**
 * 案内メッセージ編集用ダイアログ管理クラス
 * ベータ機能として、configのmax_lines分のテキスト入力フィールドを提供する
 */
class AnnouncementDialogManager : Listener {

    companion object {
        private const val ACTION_PREFIX = "mwm:announcement"

        /**
         * 案内メッセージ編集ダイアログを表示
         */
        fun showAnnouncementEditDialog(player: Player, worldData: me.awabi2048.myworldmanager.model.WorldData) {
            val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
            val lang = plugin.languageManager
            val maxLines = plugin.config.getInt("announcement.max_lines", 5)
            val maxLength = plugin.config.getInt("announcement.max_line_length", 100)

            val title = Component.text(
                lang.getMessage(player, "gui.announcement_dialog.title"),
                NamedTextColor.YELLOW
            )

            // 本文（説明文）
            val bodyLines = mutableListOf<Component>()
            bodyLines.add(Component.text("§7" + lang.getMessage(player, "gui.announcement_dialog.help")))
            bodyLines.add(Component.text("§7" + lang.getMessage(player, "gui.announcement_dialog.max_lines", mapOf("max" to maxLines))))
            bodyLines.add(Component.text("§7" + lang.getMessage(player, "gui.announcement_dialog.max_length", mapOf("max" to maxLength))))

            // 現在のメッセージを取得（存在しない場合は空文字）
            val currentMessages = worldData.announcementMessages.map { it.replace("§f", "").replace("§", "&") }

            // テキスト入力フィールドを生成
            val inputs = mutableListOf<DialogInput>()
            for (i in 0 until maxLines) {
                val lineValue = if (i < currentMessages.size) currentMessages[i] else ""
                val lineLabel = lang.getMessage(player, "gui.announcement_dialog.line_label", mapOf("number" to (i + 1)))

                inputs.add(
                    DialogInput.text("announcement_line_$i", Component.text(lineLabel))
                        .maxLength(maxLength)
                        .initial(lineValue)
                        .build()
                )
            }

            // ボタンを作成
            val saveButton = ActionButton.create(
                Component.text(lang.getMessage(player, "gui.announcement_dialog.save"), NamedTextColor.GREEN),
                null,
                100,
                DialogAction.customClick(Key.key("$ACTION_PREFIX/save/${worldData.uuid}"), null)
            )

            val resetButton = ActionButton.create(
                Component.text(lang.getMessage(player, "gui.announcement_dialog.reset"), NamedTextColor.YELLOW),
                null,
                150,
                DialogAction.customClick(Key.key("$ACTION_PREFIX/reset/${worldData.uuid}"), null)
            )

            val cancelButton = ActionButton.create(
                Component.text(lang.getMessage(player, "gui.announcement_dialog.cancel"), NamedTextColor.RED),
                null,
                200,
                DialogAction.customClick(Key.key("$ACTION_PREFIX/cancel/${worldData.uuid}"), null)
            )

            // ダイアログを作成
            val dialog = Dialog.create { builder ->
                builder.empty()
                    .base(
                        DialogBase.builder(title)
                            .body(bodyLines.map { DialogBody.plainMessage(it) })
                            .inputs(inputs)
                            .build()
                    )
                    .type(
                        DialogType.confirmation(saveButton, cancelButton)
                    )
            }

            player.showDialog(dialog)
            plugin.soundManager.playMenuOpenSound(player, "world_settings")
        }
    }

    @EventHandler
    fun handleAnnouncementDialog(event: PlayerCustomClickEvent) {
        val identifier = event.identifier
        val conn = event.commonConnection as? PlayerGameConnection ?: return
        val player = conn.player
        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
        val lang = plugin.languageManager

        // 保存アクション
        if (identifier.toString().startsWith("$ACTION_PREFIX/save/")) {
            val worldUuidStr = identifier.toString().substringAfter("$ACTION_PREFIX/save/")
            val worldUuid = try {
                java.util.UUID.fromString(worldUuidStr)
            } catch (e: IllegalArgumentException) {
                return
            }

            val worldData = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return
            val view = event.getDialogResponseView() ?: return

            // configから設定値を取得
            val maxLines = plugin.config.getInt("announcement.max_lines", 5)
            val maxLength = plugin.config.getInt("announcement.max_line_length", 100)
            val blockedStrings = plugin.config.getStringList("announcement.blocked_strings")

            // 各行の入力を収集
            val newMessages = mutableListOf<String>()
            var hasError = false

            loop@ for (i in 0 until maxLines) {
                val lineKey = "announcement_line_$i"
                val lineInput = view.getText(lineKey) ?: ""

                if (lineInput.isNotBlank()) {
                    // 禁止文字列チェック
                    for (blocked in blockedStrings) {
                        if (lineInput.contains(blocked, ignoreCase = true)) {
                            player.sendMessage(
                                lang.getMessage(
                                    player,
                                    "messages.announcement_blocked_string",
                                    mapOf("string" to blocked)
                                )
                            )
                            hasError = true
                            break@loop
                        }
                    }

                    // 文字数チェック
                    if (lineInput.length > maxLength) {
                        player.sendMessage(
                            lang.getMessage(
                                player,
                                "messages.announcement_invalid_length",
                                mapOf("max_lines" to maxLines, "max_length" to maxLength)
                            )
                        )
                        hasError = true
                        break@loop
                    }

                    if (!hasError) {
                        // 色コード変換 (初期色を白にする)
                        val formatted = "§f" + lineInput.replace("&", "§")
                        newMessages.add(formatted)
                    }
                }
            }

            if (!hasError) {
                // メッセージを保存
                worldData.announcementMessages.clear()
                worldData.announcementMessages.addAll(newMessages)
                plugin.worldConfigRepository.save(worldData)

                player.sendMessage(lang.getMessage(player, "messages.announcement_set"))
                plugin.soundManager.playActionSound(player, "world_settings", "success")

                // 設定GUIに戻る
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    plugin.settingsSessionManager.endSession(player)
                    plugin.worldSettingsGui.open(player, worldData)
                })
            } else {
                // エラー時はダイアログを再表示
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    showAnnouncementEditDialog(player, worldData)
                })
            }

            return
        }

        // キャンセルアクション
        if (identifier.toString().startsWith("$ACTION_PREFIX/cancel/")) {
            val worldUuidStr = identifier.toString().substringAfter("$ACTION_PREFIX/cancel/")
            val worldUuid = try {
                java.util.UUID.fromString(worldUuidStr)
            } catch (e: IllegalArgumentException) {
                null
            }

            Bukkit.getScheduler().runTask(plugin, Runnable {
                plugin.settingsSessionManager.endSession(player)

                if (worldUuid != null) {
                    val worldData = plugin.worldConfigRepository.findByUuid(worldUuid)
                    if (worldData != null) {
                        plugin.worldSettingsGui.open(player, worldData)
                    }
                }
            })
            return
        }

        // リセットアクション
        if (identifier.toString().startsWith("$ACTION_PREFIX/reset/")) {
            val worldUuidStr = identifier.toString().substringAfter("$ACTION_PREFIX/reset/")
            val worldUuid = try {
                java.util.UUID.fromString(worldUuidStr)
            } catch (e: IllegalArgumentException) {
                return
            }

            val worldData = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return

            // メッセージをクリア
            worldData.announcementMessages.clear()
            plugin.worldConfigRepository.save(worldData)

            player.sendMessage(lang.getMessage(player, "messages.announcement_reset"))
            plugin.soundManager.playActionSound(player, "world_settings", "reset")

            // 設定GUIに戻る
            Bukkit.getScheduler().runTask(plugin, Runnable {
                plugin.settingsSessionManager.endSession(player)
                plugin.worldSettingsGui.open(player, worldData)
            })

            return
        }
    }
}
