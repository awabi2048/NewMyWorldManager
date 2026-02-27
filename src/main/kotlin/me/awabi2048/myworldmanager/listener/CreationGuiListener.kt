package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.*
import me.awabi2048.myworldmanager.repository.*
import me.awabi2048.myworldmanager.session.*
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent

class CreationGuiListener(private val plugin: MyWorldManager) : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val view = event.view
        val title = PlainTextComponentSerializer.plainText().serialize(view.title())

        val lang = plugin.languageManager

        // 作成GUIのタイトルかどうかを判定（多言語対応）
        val isCreationGui =
                lang.isKeyMatch(title, "gui.creation.title_type") ||
                        lang.isKeyMatch(title, "gui.creation.title_template") ||
                        lang.isKeyMatch(title, "gui.creation.title_confirm")

        if (!isCreationGui) return
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        val currentItem = event.currentItem ?: return
        val tag = ItemTag.getType(currentItem)
        if (currentItem.type == Material.AIR || tag == ItemTag.TYPE_GUI_DECORATION) return

        // GUI遷移中のクリックを無視
        val settingsSession = plugin.settingsSessionManager.getSession(player)
        if (settingsSession != null && settingsSession.isGuiTransition) {
            // player.sendMessage("§7[Debug] Click cancelled (GuiTransition: true)")
            event.isCancelled = true
            return
        }

        val session = plugin.creationSessionManager.getSession(player.uniqueId) ?: return

        if (tag == ItemTag.TYPE_GUI_BACK) {
            plugin.soundManager.playClickSound(player, currentItem)
            when (session.phase) {
                WorldCreationPhase.TYPE_SELECT -> {
                    player.closeInventory()
                    plugin.creationSessionManager.endSession(player.uniqueId)
                    player.sendMessage(lang.getMessage(player, "messages.creation_cancelled"))
                }
                WorldCreationPhase.TEMPLATE_SELECT -> {
                    session.phase = WorldCreationPhase.TYPE_SELECT
                    plugin.creationGui.openTypeSelection(player)
                }
                WorldCreationPhase.CONFIRM -> {
                    session.phase = WorldCreationPhase.NAME_INPUT
                    player.closeInventory()
                    val cancelWord =
                            plugin.config.getString("creation.cancel_word", "cancel") ?: "cancel"
                    val cancelInfo =
                            lang.getMessage(
                                    player,
                                    "messages.chat_input_cancel_hint",
                                    mapOf("word" to cancelWord)
                            )
                    player.sendMessage(
                            lang.getMessage(player, "messages.wizard_name_prompt") +
                                    " " +
                                    cancelInfo
                    )
                }
                else -> {}
            }
            return
        }

        when (session.phase) {
            WorldCreationPhase.TYPE_SELECT -> {
                val config = plugin.config
                val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)

                // 上限チェック (WorldCommandへ移動済み)

                // コスト判定
                val cost =
                        when (tag) {
                            ItemTag.TYPE_GUI_CREATION_TYPE_TEMPLATE ->
                                    config.getInt("creation_cost.template", 0)
                            ItemTag.TYPE_GUI_CREATION_TYPE_SEED ->
                                    config.getInt("creation_cost.seed", 100)
                            ItemTag.TYPE_GUI_CREATION_TYPE_RANDOM ->
                                    config.getInt("creation_cost.random", 50)
                            else -> 0
                        }

                if (stats.worldPoint < cost) {
                    player.sendMessage(
                            lang.getMessage(player, "messages.creation_insufficient_points")
                    )
                    plugin.soundManager.playActionSound(player, "creation", "insufficient_points")
                    return
                }

                when (tag) {
                    ItemTag.TYPE_GUI_CREATION_TYPE_TEMPLATE -> {
                        plugin.soundManager.playClickSound(player, currentItem)
                        session.creationType = WorldCreationType.TEMPLATE
                        session.phase = WorldCreationPhase.TEMPLATE_SELECT
                        plugin.creationGui.openTemplateSelection(player)
                    }
                    ItemTag.TYPE_GUI_CREATION_TYPE_SEED -> {
                        plugin.soundManager.playClickSound(player, currentItem)
                        session.creationType = WorldCreationType.SEED
                        session.phase = WorldCreationPhase.SEED_INPUT
                        
                        if (session.isDialogMode) {
                            me.awabi2048.myworldmanager.gui.CreationDialogManager.showSeedInputDialog(player, session)
                            return
                        }
                        
                        player.closeInventory()
                        val cancelWord =
                                plugin.config.getString("creation.cancel_word", "cancel")
                                        ?: "cancel"
                        val cancelInfo =
                                lang.getMessage(
                                        player,
                                        "messages.chat_input_cancel_hint",
                                        mapOf("word" to cancelWord)
                                )
                        player.sendMessage("§a生成に使用するシード値をチャットに入力してください。 " + cancelInfo)
                    }
                    ItemTag.TYPE_GUI_CREATION_TYPE_RANDOM -> {
                        plugin.soundManager.playClickSound(player, currentItem)
                        session.creationType = WorldCreationType.RANDOM
                        
                        if (session.isDialogMode) {
                            session.phase = WorldCreationPhase.NAME_INPUT
                            me.awabi2048.myworldmanager.gui.CreationDialogManager.showNameInputDialog(player, session)
                            return
                        }
                        
                        session.phase = WorldCreationPhase.NAME_INPUT
                        player.closeInventory()
                        val cancelWord = plugin.config.getString("creation.cancel_word", "cancel") ?: "cancel"
                        val cancelInfo =
                                lang.getMessage(
                                        player,
                                        "messages.chat_input_cancel_hint",
                                        mapOf("word" to cancelWord)
                                )
                        player.sendMessage(lang.getMessage(player, "messages.wizard_name_prompt") + " " + cancelInfo)
                    }
                    else -> {}
                }
            }
            WorldCreationPhase.TEMPLATE_SELECT -> {
                if (tag != ItemTag.TYPE_GUI_CREATION_TEMPLATE_ITEM) return
                val displayName =
                        PlainTextComponentSerializer.plainText()
                                .serialize(currentItem.itemMeta.displayName()!!)
                val template = plugin.templateRepository.findAll().find { it.name == displayName }

                if (template != null) {
                    // 右クリックでプレビュー
                    if (event.isRightClick) {
                        player.closeInventory()
                        val target = PreviewSessionManager.PreviewTarget.Template(template.path)
                        plugin.previewSessionManager.startPreview(
                                player,
                                target,
                                me.awabi2048.myworldmanager.session.PreviewSource.TEMPLATE_SELECTION
                        )
                        return
                    }

                    // 左クリックで選択確定
                    plugin.soundManager.playClickSound(player, currentItem)
                    session.templateName = template.path
                    
                    if (session.isDialogMode) {
                        session.phase = WorldCreationPhase.NAME_INPUT
                        me.awabi2048.myworldmanager.gui.CreationDialogManager.showNameInputDialog(player, session)
                        return
                    }
                    
                    session.phase = WorldCreationPhase.NAME_INPUT
                    player.closeInventory()
                    val cancelWord = plugin.config.getString("creation.cancel_word", "cancel") ?: "cancel"
                    val cancelInfo =
                            lang.getMessage(
                                    player,
                                    "messages.chat_input_cancel_hint",
                                    mapOf("word" to cancelWord)
                            )
                    player.sendMessage(lang.getMessage(player, "messages.wizard_name_prompt") + " " + cancelInfo)
                }
            }
            WorldCreationPhase.CONFIRM -> {
                if (tag == ItemTag.TYPE_GUI_CONFIRM) {
                    player.closeInventory()

                    // ポイント消費
                    val config = plugin.config
                    val cost =
                            when (session.creationType) {
                                WorldCreationType.TEMPLATE ->
                                        config.getInt("creation_cost.template", 0)
                                WorldCreationType.SEED -> config.getInt("creation_cost.seed", 100)
                                WorldCreationType.RANDOM ->
                                        config.getInt("creation_cost.random", 50)
                                else -> 0
                            }
                    val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)

                    if (stats.worldPoint < cost) {
                        player.sendMessage(
                                lang.getMessage(player, "messages.creation_insufficient_points")
                        )
                        plugin.creationSessionManager.endSession(player.uniqueId)
                        return
                    }

                    stats.worldPoint -= cost
                    plugin.playerStatsRepository.save(stats)
                    if (cost > 0) {
                        player.sendMessage(
                                "§e§6🛖 §e${cost} §eを消費しました。(残り: §6🛖 §e${stats.worldPoint}§e)"
                        )
                    }

                    player.sendMessage("§aワールドを作成しています...")

                    when (session.creationType) {
                        WorldCreationType.TEMPLATE -> {
                            plugin.worldService.createWorld(
                                            session.templateName!!,
                                            player.uniqueId,
                                            session.worldName!!,
                                            cost
                                    )
                                    .thenAccept { success: Boolean ->
                                        if (success) player.sendMessage("§aワールド作成完了！")
                                        else player.sendMessage("§c作成に失敗しました。")
                                    }
                        }
                        WorldCreationType.SEED -> {
                            plugin.worldService.generateWorld(
                                            player.uniqueId,
                                            session.worldName!!,
                                            session.inputSeedString,
                                            cost
                                    )
                                    .thenAccept { success: Boolean ->
                                        if (success) player.sendMessage("§aワールド作成完了！")
                                        else player.sendMessage("§c作成に失敗しました。")
                                    }
                        }
                        WorldCreationType.RANDOM -> {
                            plugin.worldService.generateWorld(
                                            player.uniqueId,
                                            session.worldName!!,
                                            null,
                                            cost
                                    )
                                    .thenAccept { success: Boolean ->
                                        if (success) player.sendMessage("§aワールド作成完了！")
                                        else player.sendMessage("§c作成に失敗しました。")
                                    }
                        }
                        null -> {}
                    }
                    plugin.soundManager.playClickSound(player, currentItem)
                    plugin.creationSessionManager.endSession(player.uniqueId)
                } else if (tag == ItemTag.TYPE_GUI_CANCEL) {
                    plugin.soundManager.playActionSound(player, "creation", "cancel")
                    player.closeInventory()
                    player.sendMessage(
                            plugin.languageManager.getMessage(player, "messages.creation_cancelled")
                    )
                    plugin.creationSessionManager.endSession(player.uniqueId)
                }
            }
            else -> {}
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val view = event.view
        val title = PlainTextComponentSerializer.plainText().serialize(view.title())

        val lang = plugin.languageManager

        // 作成GUIのタイトルかどうかを判定（多言語対応）
        val isCreationGui =
                lang.isKeyMatch(title, "gui.creation.title_type") ||
                        lang.isKeyMatch(title, "gui.creation.title_template") ||
                        lang.isKeyMatch(title, "gui.creation.title_confirm")

        if (!isCreationGui) return

        val player = event.player as? Player ?: return

        val session = plugin.creationSessionManager.getSession(player.uniqueId) ?: return

        if (session.phase == WorldCreationPhase.SEED_INPUT ||
                        session.phase == WorldCreationPhase.NAME_INPUT
        ) {
            return
        }

        // 遅延を2tickに増やしてGUI遷移の時間を確保
        Bukkit.getScheduler()
                .runTaskLater(
                        plugin,
                        Runnable {
                            if (!player.isOnline) return@Runnable

                            // プレビュー中はセッションをキャンセルしない
                            if (plugin.previewSessionManager.isInPreview(player)) {
                                return@Runnable
                            }

                            val currentTitle =
                                    PlainTextComponentSerializer.plainText()
                                            .serialize(player.openInventory.title())
                            val isStillInCreationGui =
                                    lang.isKeyMatch(currentTitle, "gui.creation.title_type") ||
                                            lang.isKeyMatch(
                                                    currentTitle,
                                                    "gui.creation.title_template"
                                            ) ||
                                            lang.isKeyMatch(
                                                    currentTitle,
                                                    "gui.creation.title_confirm"
                                            )
                            if (isStillInCreationGui) {
                                return@Runnable
                            }

                            val currentSession =
                                    plugin.creationSessionManager.getSession(player.uniqueId)
                            if (currentSession != null &&
                                            currentSession.phase != WorldCreationPhase.SEED_INPUT &&
                                            currentSession.phase != WorldCreationPhase.NAME_INPUT
                            ) {
                                // セッションがまだ残っている（＝他で終了されていない）場合のみ処理
                                plugin.creationSessionManager.endSession(player.uniqueId)
                                player.sendMessage(
                                        lang.getMessage(player, "messages.creation_cancelled")
                                )
                            }
                        },
                        2L
                )
    }
}
