package me.awabi2048.myworldmanager.command

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.*

@Suppress("UnstableApiUsage")
class TestWizardCommand : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Players only!")
            return true
        }

        val worldUuid = UUID.randomUUID()
        val worldName = "my_world.$worldUuid"

        sender.sendMessage("§e[TestWizard] Opening Dialog...")

        try {
            val dialog = Dialog.create { builder ->
                builder.empty()
                    .base(DialogBase.builder(Component.text("World Creation Wizard Test", NamedTextColor.GOLD))
                    .body(listOf(
                        DialogBody.plainMessage(Component.text("New world name will be:\n", NamedTextColor.WHITE)),
                        DialogBody.plainMessage(Component.text(worldName, NamedTextColor.AQUA)),
                        DialogBody.plainMessage(Component.text("\n\nDo you want to proceed?", NamedTextColor.WHITE))
                    ))
                    .build()
                )
                .type(DialogType.confirmation(
                    // YES
                    ActionButton.create(
                        Component.text("Proceed", NamedTextColor.GREEN),
                        null,
                        100,
                        DialogAction.customClick(net.kyori.adventure.key.Key.key("mwm:test_wizard/proceed"), null)
                    ),
                    
                    // NO
                    ActionButton.create(
                        Component.text("Cancel", NamedTextColor.RED),
                        null,
                        200,
                        DialogAction.customClick(net.kyori.adventure.key.Key.key("mwm:test_wizard/cancel"), null)
                    )
                ))
            }

            sender.showDialog(dialog)

        } catch (e: Exception) {
            sender.sendMessage("§cError: ${e.message}")
            e.printStackTrace()
        }
        
        return true
    }
}
