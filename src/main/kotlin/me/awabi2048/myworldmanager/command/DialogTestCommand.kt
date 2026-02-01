package me.awabi2048.myworldmanager.command

import io.papermc.paper.dialog.Dialog
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
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.*

@Suppress("UnstableApiUsage")
class DialogTestCommand(private val plugin: MyWorldManager) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Players only!")
            return true
        }

        sender.sendMessage("§e[DialogTest] Opening Dialog for World Clone...")

        try {
            val lang = plugin.languageManager
            
            // Start official session
            val session = plugin.creationSessionManager.startSession(sender.uniqueId)
            session.isDialogMode = true

            val dialog = Dialog.create { builder ->
                builder.empty()
                    .base(DialogBase.builder(LegacyComponentSerializer.legacySection().deserialize(lang.getMessage(sender, "messages.wizard_start")))
                        .body(listOf(
                            DialogBody.plainMessage(LegacyComponentSerializer.legacySection().deserialize(lang.getMessage(sender, "messages.wizard_name_prompt"))),
                        ))
                        .inputs(listOf(
                            DialogInput.text("new_world_name", Component.text("Name"))
                                .maxLength(16)
                                .initial("${sender.name}_world")
                                .build()
                        ))
                        .build()
                    )
                    .type(DialogType.confirmation(
                        ActionButton.create(Component.text("Next", NamedTextColor.GREEN), null, 100, DialogAction.customClick(Key.key("mwm:dialog_test/stage1_next"), null)),
                        ActionButton.create(Component.text("Cancel", NamedTextColor.RED), null, 200, DialogAction.customClick(Key.key("mwm:dialog_test/cancel"), null))
                    ))
            }

            sender.showDialog(dialog)

        } catch (e: Exception) {
            sender.sendMessage("§cError showing dialog: ${e.message}")
            e.printStackTrace()
        }
        
        return true
    }
}
