package me.awabi2048.myworldmanager.gui

import io.papermc.paper.connection.PlayerGameConnection
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.event.player.PlayerCustomClickEvent
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.session.PreviewSource
import me.awabi2048.myworldmanager.session.WorldCreationPhase
import me.awabi2048.myworldmanager.session.WorldCreationSession
import me.awabi2048.myworldmanager.session.WorldCreationType
import me.awabi2048.myworldmanager.session.PreviewSessionManager
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin

/**
 * ワールド作成時のダイアログ入力を管理するクラス
 * ベータ機能として、チャット入力の代わりにダイアログを使用する
 */
class CreationDialogManager : Listener {

    @EventHandler
    fun handleCreationDialog(event: PlayerCustomClickEvent) {
        val identifier = event.identifier
        val conn = event.commonConnection as? PlayerGameConnection ?: return
        val player = conn.player

        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
        val session = plugin.creationSessionManager.getSession(player.uniqueId) ?: return

        // ダイアログモードでない場合は無視
        if (!session.isDialogMode) return

        // Cancel Action
        if (identifier == Key.key("mwm:creation/cancel")) {
            plugin.creationSessionManager.endSession(player.uniqueId)
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.creation_cancelled"))
            safeCloseDialog(player)
            return
        }

        // 名前入力 -> 次へ
        if (identifier == Key.key("mwm:creation/name_input_next")) {
            val view = event.getDialogResponseView() ?: return

            val nameRawAny = view.getText("world_name")
            if (nameRawAny == null) return
            val nameRaw = nameRawAny.toString()

            // 名前のバリデーション
            val error = plugin.worldValidator.validateName(nameRaw)
            if (error != null) {
                player.sendMessage("§c$error")
                // エラー時はダイアログを再表示
                showNameInputDialog(player, session)
                return
            }

            session.worldName = cleanWorldName(nameRaw)
            session.phase = WorldCreationPhase.TYPE_SELECT

            org.bukkit.Bukkit.getScheduler().runTask(plugin, Runnable {
                plugin.creationGui.openTypeSelection(player)
            })
            return
        }

        // シード値入力 -> 次へ
        if (identifier == Key.key("mwm:creation/seed_input_next")) {
            val view = event.getDialogResponseView() ?: return
            val seedInputAny = view.getText("seed_value") ?: ""
            val seedInput = seedInputAny.toString()

            val seed = seedInput.toLongOrNull() ?: seedInput.hashCode().toLong()
            session.seed = seed
            session.inputSeedString = seedInput
            session.phase = WorldCreationPhase.CONFIRM

            showConfirmationDialog(player, session)
            return
        }

        // シード値入力 -> 戻る
        if (identifier == Key.key("mwm:creation/seed_input_back")) {
            session.phase = WorldCreationPhase.TYPE_SELECT
            plugin.creationGui.openTypeSelection(player)
            return
        }

        // 最終確認 -> 実行
        if (identifier == Key.key("mwm:creation/confirm_proceed")) {
            val view = event.getDialogResponseView() ?: return
            val actionAny = view.getText("action_choice") ?: "cancel"
            val action = actionAny.toString()

            when (action) {
                "create" -> {
                    performWorldCreation(player, session, plugin)
                    safeCloseDialog(player)
                }
                "preview" -> {
                    if (session.creationType == WorldCreationType.TEMPLATE && session.templateName != null) {
                        val target = PreviewSessionManager.PreviewTarget.Template(session.templateName!!)
                        plugin.previewSessionManager.startPreview(player, target, PreviewSource.TEMPLATE_SELECTION)
                        safeCloseDialog(player)
                    }
                }
                "back" -> {
                    when (session.creationType) {
                        WorldCreationType.SEED -> showSeedInputDialog(player, session)
                        WorldCreationType.TEMPLATE -> {
                            session.phase = WorldCreationPhase.TEMPLATE_SELECT
                            plugin.creationGui.openTemplateSelection(player)
                        }
                        WorldCreationType.RANDOM -> {
                            session.phase = WorldCreationPhase.TYPE_SELECT
                            plugin.creationGui.openTypeSelection(player)
                        }
                        else -> safeCloseDialog(player)
                    }
                }
                "cancel" -> {
                    plugin.creationSessionManager.endSession(player.uniqueId)
                    player.sendMessage(plugin.languageManager.getMessage(player, "messages.creation_cancelled"))
                    safeCloseDialog(player)
                }
            }
            return
        }

        // 最終確認 -> 戻る (Esc or Cancel button)
        if (identifier == Key.key("mwm:creation/confirm_back")) {
            when (session.creationType) {
                WorldCreationType.SEED -> showSeedInputDialog(player, session)
                WorldCreationType.TEMPLATE -> {
                    session.phase = WorldCreationPhase.TEMPLATE_SELECT
                    plugin.creationGui.openTemplateSelection(player)
                }
                WorldCreationType.RANDOM -> {
                    session.phase = WorldCreationPhase.TYPE_SELECT
                    plugin.creationGui.openTypeSelection(player)
                }
                else -> {
                    session.phase = WorldCreationPhase.TYPE_SELECT
                    plugin.creationGui.openTypeSelection(player)
                }
            }
            return
        }
    }

    companion object {
        /**
         * ワールド名入力ダイアログを表示
         */
        fun showNameInputDialog(player: Player, session: WorldCreationSession) {
            val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
            val lang = plugin.languageManager

            val cancelWord = plugin.config.getString("creation.cancel_word", "cancel") ?: "cancel"

            val dialog = Dialog.create { builder ->
                builder.empty()
                    .base(
                        DialogBase.builder(
                            LegacyComponentSerializer.legacySection()
                                .deserialize(lang.getMessage(player, "gui.creation.dialog.name_title"))
                        )
                            .body(
                                listOf(
                                    DialogBody.plainMessage(
                                        Component.text(
                                            lang.getMessage(
                                                player,
                                                "messages.wizard_name_prompt"
                                            )
                                        )
                                    ),
                                    DialogBody.plainMessage(
                                        Component.text(
                                            "§7${lang.getMessage(player, "messages.chat_input_cancel_hint", mapOf("word" to cancelWord))}"
                                        )
                                    )
                                )
                            )
                            .inputs(
                                listOf(
                                    DialogInput.text("world_name", Component.text("World Name"))
                                        .maxLength(20)
                                        .initial(session.worldName ?: "")
                                        .build()
                                )
                            )
                            .build()
                    )
                    .type(
                        DialogType.confirmation(
                            ActionButton.create(
                                Component.text("Next", NamedTextColor.GREEN),
                                null,
                                100,
                                DialogAction.customClick(Key.key("mwm:creation/name_input_next"), null)
                            ),
                            ActionButton.create(
                                Component.text("Cancel", NamedTextColor.RED),
                                null,
                                200,
                                DialogAction.customClick(Key.key("mwm:creation/cancel"), null)
                            )
                        )
                    )
            }
            player.showDialog(dialog)
        }

        /**
         * シード値入力ダイアログを表示
         */
        fun showSeedInputDialog(player: Player, session: WorldCreationSession) {
            val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
            val lang = plugin.languageManager

            val dialog = Dialog.create { builder ->
                builder.empty()
                    .base(
                        DialogBase.builder(Component.text(lang.getMessage(player, "gui.creation.dialog.seed_title"), NamedTextColor.YELLOW))
                            .body(
                                listOf(
                                    DialogBody.plainMessage(
                                        Component.text(
                                            lang.getMessage(player, "messages.wizard_seed_prompt")
                                        )
                                    )
                                )
                            )
                            .inputs(
                                listOf(
                                    DialogInput.text("seed_value", Component.text("Seed / Text"))
                                        .initial(session.inputSeedString ?: "")
                                        .build()
                                )
                            )
                            .build()
                    )
                    .type(
                        DialogType.confirmation(
                            ActionButton.create(
                                Component.text("Next", NamedTextColor.GREEN),
                                null,
                                100,
                                DialogAction.customClick(Key.key("mwm:creation/seed_input_next"), null)
                            ),
                            ActionButton.create(
                                Component.text("Back", NamedTextColor.GRAY),
                                null,
                                200,
                                DialogAction.customClick(Key.key("mwm:creation/seed_input_back"), null)
                            )
                        )
                    )
            }
            player.showDialog(dialog)
        }

        /**
         * 最終確認ダイアログを表示
         */
        fun showConfirmationDialog(player: Player, session: WorldCreationSession) {
            val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
            val lang = plugin.languageManager
            val config = plugin.config

            val typeName = when (session.creationType) {
                WorldCreationType.TEMPLATE -> lang.getMessage(player, "gui.creation.type.template.name")
                WorldCreationType.SEED -> lang.getMessage(player, "gui.creation.type.seed.name")
                WorldCreationType.RANDOM -> lang.getMessage(player, "gui.creation.type.random.name")
                else -> lang.getMessage(player, "general.unknown")
            }

            val cost = when (session.creationType) {
                WorldCreationType.TEMPLATE -> config.getInt("creation_cost.template", 0)
                WorldCreationType.SEED -> config.getInt("creation_cost.seed", 100)
                WorldCreationType.RANDOM -> config.getInt("creation_cost.random", 50)
                else -> 0
            }

            val cleanedName = session.worldName ?: lang.getMessage(player, "general.unknown")

            val templateLine = if (session.creationType == WorldCreationType.TEMPLATE) {
                val template = plugin.templateRepository.findAll().find { it.path == session.templateName }
                val displayName = template?.name
                    ?: (session.templateName ?: lang.getMessage(player, "general.unknown"))
                lang.getMessage(player, "gui.creation.confirm.template_line", mapOf("template" to displayName))
            } else ""

            val seedLine = if (session.creationType == WorldCreationType.SEED) {
                lang.getMessage(
                    player,
                    "gui.creation.confirm.seed_line",
                    mapOf("seed" to (session.inputSeedString ?: ""))
                )
            } else ""

            val bodyLines = mutableListOf<Component>()
            bodyLines.add(Component.text("§8§m－－－－－－－－－－－－－－－－－－"))
            bodyLines.add(Component.text("§f§l| §7${lang.getMessage(player, "gui.creation.confirm.name_label")} §a$cleanedName"))
            bodyLines.add(Component.text("§f§l| §7${lang.getMessage(player, "gui.creation.confirm.type_label")} §e$typeName"))

            if (templateLine.isNotEmpty()) bodyLines.add(Component.text("§f§l| §7$templateLine"))
            if (seedLine.isNotEmpty()) bodyLines.add(Component.text("§f§l| §7$seedLine"))

            bodyLines.add(Component.text("§8§m－－－－－－－－－－－－－－－－－－"))
            bodyLines.add(Component.text("§f§l| §7${lang.getMessage(player, "gui.creation.confirm.cost_label")} §6🛖 §e$cost"))
            bodyLines.add(Component.text("§8§m－－－－－－－－－－－－－－－－－－"))

            val actionOptions = mutableListOf(
                SingleOptionDialogInput.OptionEntry.create(
                    "create",
                    Component.text(lang.getMessage(player, "gui.creation.confirm.action_create"), NamedTextColor.GREEN),
                    true
                )
            )

            if (session.creationType == WorldCreationType.TEMPLATE) {
                actionOptions.add(
                    SingleOptionDialogInput.OptionEntry.create(
                        "preview",
                        Component.text(lang.getMessage(player, "gui.creation.confirm.action_preview"), NamedTextColor.YELLOW),
                        false
                    )
                )
            }

            actionOptions.add(
                SingleOptionDialogInput.OptionEntry.create(
                    "back",
                    Component.text(lang.getMessage(player, "gui.creation.confirm.action_back"), NamedTextColor.GRAY),
                    false
                )
            )
            actionOptions.add(
                SingleOptionDialogInput.OptionEntry.create(
                    "cancel",
                    Component.text(lang.getMessage(player, "gui.creation.confirm.action_cancel"), NamedTextColor.RED),
                    false
                )
            )

            val dialog = Dialog.create { builder ->
                builder.empty()
                    .base(
                        DialogBase.builder(
                            LegacyComponentSerializer.legacySection()
                                .deserialize(lang.getMessage(player, "gui.creation.title_confirm"))
                        )
                            .body(bodyLines.map { DialogBody.plainMessage(it) })
                            .inputs(
                                listOf(
                                    DialogInput.singleOption(
                                        "action_choice",
                                        Component.text(lang.getMessage(player, "gui.creation.confirm.select_action")),
                                        actionOptions
                                    )
                                        .build()
                                )
                            )
                            .build()
                    )
                    .type(
                        DialogType.confirmation(
                            ActionButton.create(
                                Component.text(lang.getMessage(player, "gui.creation.confirm.proceed_button"), NamedTextColor.GREEN),
                                null,
                                100,
                                DialogAction.customClick(Key.key("mwm:creation/confirm_proceed"), null)
                            ),
                            ActionButton.create(
                                Component.text(lang.getMessage(player, "gui.creation.confirm.cancel_button"), NamedTextColor.RED),
                                null,
                                200,
                                DialogAction.customClick(Key.key("mwm:creation/confirm_back"), null)
                            )
                        )
                    )
            }

            player.showDialog(dialog)
        }

        private fun performWorldCreation(
            player: Player,
            session: WorldCreationSession,
            plugin: MyWorldManager
        ) {
            val cost = when (session.creationType) {
                WorldCreationType.TEMPLATE -> plugin.config.getInt("creation_cost.template", 0)
                WorldCreationType.SEED -> plugin.config.getInt("creation_cost.seed", 100)
                WorldCreationType.RANDOM -> plugin.config.getInt("creation_cost.random", 50)
                else -> 0
            }

            // ポイント消費
            val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
            stats.worldPoint -= cost
            plugin.playerStatsRepository.save(stats)

            val name = session.worldName ?: "New World"

            when (session.creationType) {
                WorldCreationType.TEMPLATE -> {
                    val templatePath = session.templateName ?: "skyblock"
                    plugin.worldService.createWorld(templatePath, player.uniqueId, name, cost)
                }
                WorldCreationType.SEED -> {
                    val seedStr = session.inputSeedString ?: ""
                    plugin.worldService.generateWorld(player.uniqueId, name, seedStr, cost)
                }
                WorldCreationType.RANDOM -> {
                    plugin.worldService.generateWorld(player.uniqueId, name, null, cost)
                }
                else -> {}
            }

            plugin.creationSessionManager.endSession(player.uniqueId)
            player.closeInventory()
        }

        fun cleanWorldName(name: String): String {
            val regex = Regex("\\s?\\(.*?\\)")
            return name.replace(regex, "").trim()
        }

        fun safeCloseDialog(player: Player) {
            try {
                val method = try {
                    player.javaClass.getMethod("closeDialog")
                } catch (e: NoSuchMethodException) {
                    Player::class.java.getMethod("closeDialog")
                }
                method.invoke(player)
            } catch (e: Exception) {
            }
        }
    }
}
