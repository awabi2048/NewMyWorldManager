package me.awabi2048.myworldmanager.listener

import io.papermc.paper.connection.PlayerGameConnection
import io.papermc.paper.event.player.PlayerCustomClickEvent
import net.kyori.adventure.key.Key
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.type.DialogType
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.session.PreviewSource
import me.awabi2048.myworldmanager.session.WorldCreationPhase
import me.awabi2048.myworldmanager.session.WorldCreationType
import me.awabi2048.myworldmanager.session.WorldCreationSession
import java.util.UUID

class DialogTestListener: Listener {


    @EventHandler
    fun handleDialogTest(event: PlayerCustomClickEvent) {
        val identifier = event.identifier
        val conn = event.commonConnection as? PlayerGameConnection ?: return
        val player = conn.player

        
        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
        val session = plugin.creationSessionManager.getSession(player.uniqueId) ?: return

        // Cancel Action
        if (identifier == Key.key("mwm:dialog_test/cancel")) {
            plugin.creationSessionManager.endSession(player.uniqueId)
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.creation_cancelled"))
            safeCloseDialog(player)
            return
        }

        // Stage 1: Name Input -> Next
        if (identifier == Key.key("mwm:dialog_test/stage1_next")) {
            val view = event.getDialogResponseView()
            if (view == null) return
            
            // getText returns String or Any (depending on version), so safely convert to String
            val nameRawAny = view.getText("new_world_name")
            if (nameRawAny == null) return
            val nameRaw = nameRawAny.toString()
            // player.sendMessage("§7[Debug] Input Name: $nameRaw")
            
            // Validate & Clean Name
            val error = plugin.worldValidator.validateName(nameRaw)
            if (error != null) {
                player.sendMessage("§c$error")
                // Re-open Stage 1? For now just return to let them try again (needs back button or re-command)
                // Ideally we re-show the dialog.
                return
            }
            
            session.worldName = cleanWorldName(nameRaw)
            session.phase = WorldCreationPhase.TYPE_SELECT
            
            org.bukkit.Bukkit.getScheduler().runTask(plugin, Runnable {
                plugin.creationGui.openTypeSelection(player)
            })
            return
        }

        // Stage 2: Seed Input -> Next
        if (identifier == Key.key("mwm:dialog_test/seed_input_next")) {
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

        // Stage 3: Confirmation -> Proceed
        if (identifier == Key.key("mwm:dialog_test/stage3_proceed")) {
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
                        val target = me.awabi2048.myworldmanager.session.PreviewSessionManager.PreviewTarget.Template(session.templateName!!)
                        plugin.previewSessionManager.startPreview(player, target, PreviewSource.TEMPLATE_SELECTION)
                        safeCloseDialog(player)
                    }
                }
                "back" -> {
                    // Back logic
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
        
        // Back from Seed Input
        if (identifier == Key.key("mwm:dialog_test/seed_input_back")) {
            session.phase = WorldCreationPhase.TYPE_SELECT
            plugin.creationGui.openTypeSelection(player)
            return
        }
    }

    companion object {
        /**
         * シード値入力ダイアログを表示
         */
        fun showSeedInputDialog(player: Player, session: WorldCreationSession) {
            val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
            val lang = plugin.languageManager
            
            val dialog = Dialog.create { builder ->
                builder.empty()
                    .base(DialogBase.builder(Component.text("シード値入力", NamedTextColor.YELLOW))
                        .body(listOf(
                            DialogBody.plainMessage(Component.text("生成に使用するシード値または文字列を入力してください。")),
                        ))
                        .inputs(listOf(
                            DialogInput.text("seed_value", Component.text("Seed / Text"))
                                .initial(session.inputSeedString ?: "")
                                .build()
                        ))
                        .build()
                    )
                    .type(DialogType.confirmation(
                        ActionButton.create(Component.text("Next", NamedTextColor.GREEN), null, 100, DialogAction.customClick(Key.key("mwm:dialog_test/seed_input_next"), null)),
                        ActionButton.create(Component.text("Back", NamedTextColor.GRAY), null, 200, DialogAction.customClick(Key.key("mwm:dialog_test/seed_input_back"), null))
                    ))
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
            
            val typeName = when(session.creationType) {
                WorldCreationType.TEMPLATE -> lang.getMessage(player, "gui.creation.type.template.name")
                WorldCreationType.SEED -> lang.getMessage(player, "gui.creation.type.seed.name")
                WorldCreationType.RANDOM -> lang.getMessage(player, "gui.creation.type.random.name")
                else -> lang.getMessage(player, "general.unknown")
            }

            val cost = when(session.creationType) {
                WorldCreationType.TEMPLATE -> config.getInt("creation_cost.template", 0)
                WorldCreationType.SEED -> config.getInt("creation_cost.seed", 100)
                WorldCreationType.RANDOM -> config.getInt("creation_cost.random", 50)
                else -> 0
            }

            val cleanedName = session.worldName ?: lang.getMessage(player, "general.unknown")
            
            val templateLine = if (session.creationType == WorldCreationType.TEMPLATE) {
                val template = plugin.templateRepository.findAll().find { it.path == session.templateName }
                val displayName = template?.name ?: (session.templateName ?: lang.getMessage(player, "general.unknown"))
                lang.getMessage(player, "gui.creation.confirm.template_line", mapOf("template" to displayName))
            } else ""

            val seedLine = if (session.creationType == WorldCreationType.SEED) {
                lang.getMessage(player, "gui.creation.confirm.seed_line", mapOf("seed" to (session.inputSeedString ?: "")))
            } else ""

            val bodyLines = mutableListOf<Component>()
            bodyLines.add(Component.text("§8§m－－－－－－－－－－－－－－－－－－"))
            bodyLines.add(Component.text("§f§l| §7ワールド名 §a$cleanedName"))
            bodyLines.add(Component.text("§f§l| §7作成方法 §e$typeName"))
            
            if (templateLine.isNotEmpty()) bodyLines.add(Component.text("§f§l| §7$templateLine"))
            if (seedLine.isNotEmpty()) bodyLines.add(Component.text("§f§l| §7$seedLine"))
            
            bodyLines.add(Component.text("§8§m－－－－－－－－－－－－－－－－－－"))
            bodyLines.add(Component.text("§f§l| §7消費ポイント §6🛖 §e$cost"))
            bodyLines.add(Component.text("§8§m－－－－－－－－－－－－－－－－－－"))

            val actionOptions = mutableListOf(
                SingleOptionDialogInput.OptionEntry.create("create", Component.text("作成する", NamedTextColor.GREEN), true)
            )
            
            if (session.creationType == WorldCreationType.TEMPLATE) {
                actionOptions.add(SingleOptionDialogInput.OptionEntry.create("preview", Component.text("テンプレートをプレビュー", NamedTextColor.YELLOW), false))
            }
            
            actionOptions.add(SingleOptionDialogInput.OptionEntry.create("back", Component.text("一つ前へ戻る", NamedTextColor.GRAY), false))
            actionOptions.add(SingleOptionDialogInput.OptionEntry.create("cancel", Component.text("キャンセル", NamedTextColor.RED), false))

            val dialog = Dialog.create { builder ->
                builder.empty()
                    .base(DialogBase.builder(LegacyComponentSerializer.legacySection().deserialize(lang.getMessage(player, "gui.creation.title_confirm")))
                        .body(bodyLines.map { DialogBody.plainMessage(it) })
                        .inputs(listOf(
                            DialogInput.singleOption("action_choice", Component.text("アクションを選択"), actionOptions)
                                .build()
                        ))
                        .build()
                    )
                    .type(DialogType.confirmation(
                        ActionButton.create(Component.text("Proceed", NamedTextColor.GREEN), null, 100, DialogAction.customClick(Key.key("mwm:dialog_test/stage3_proceed"), null)),
                        ActionButton.create(Component.text("Close", NamedTextColor.RED), null, 200, DialogAction.customClick(Key.key("mwm:dialog_test/cancel"), null))
                    ))
            }
            
            player.showDialog(dialog)
        }

        private fun performWorldCreation(player: Player, session: WorldCreationSession, plugin: MyWorldManager) {
            val cost = when(session.creationType) {
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
            } catch (e: Exception) {}
        }
    }
}
