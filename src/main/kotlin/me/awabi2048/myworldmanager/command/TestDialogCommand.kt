package me.awabi2048.myworldmanager.command

import me.awabi2048.myworldmanager.MyWorldManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.conversations.*
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.function.Consumer

@Suppress("UnstableApiUsage")
class TestDialogCommand : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Players only!")
            return true
        }

        sender.sendMessage("§eTrying to open Paper Dialog API (Reflection)...")

        try {
            // Check if API classes exist
            val dialogClass = Class.forName("io.papermc.paper.dialog.Dialog")
            val dialogTypeClass = Class.forName("io.papermc.paper.dialog.DialogType")
            val dialogBaseClass = Class.forName("io.papermc.paper.dialog.component.DialogBase")
            val actionButtonClass = Class.forName("io.papermc.paper.dialog.component.ActionButton")
            val dialogActionClass = Class.forName("io.papermc.paper.dialog.event.DialogAction")

            // Create Base
            val baseBuilderMethod = dialogBaseClass.getMethod("builder", Component::class.java)
            val baseBuilder = baseBuilderMethod.invoke(null, Component.text("Test Dialog", NamedTextColor.AQUA))
            
            val baseBodyMethod = baseBuilder.javaClass.getMethod("body", Component::class.java)
            baseBodyMethod.invoke(baseBuilder, Component.text("Paper Dialog API is working!\n(Sample Confirmation)", NamedTextColor.GRAY))
            
            val baseBuildMethod = baseBuilder.javaClass.getMethod("build")
            val dialogBase = baseBuildMethod.invoke(baseBuilder)

            // Create ActionButton (Yes)
            val btnBuilderMethod = actionButtonClass.getMethod("builder", Component::class.java)
            val yesBuilder = btnBuilderMethod.invoke(null, Component.text("Yes", NamedTextColor.GREEN))
            
            // Callback for Yes
            val callbackMethod = dialogActionClass.getMethod("callback", Consumer::class.java)
            val yesAction = callbackMethod.invoke(null, Consumer<Player> { p ->
                p.sendMessage(Component.text("Yes selected!", NamedTextColor.GREEN))
                // Try to close? p.closeDialog() might exist
            })
            
            val btnActionMethod = yesBuilder.javaClass.getMethod("action", dialogActionClass)
            btnActionMethod.invoke(yesBuilder, yesAction)
            
            val btnBuildMethod = yesBuilder.javaClass.getMethod("build")
            val yesButton = btnBuildMethod.invoke(yesBuilder)

            // Create Type (Confirmation)
            // Assuming DialogType.confirmation(ActionButton...)
            // Since it's varargs or list, we need to check method signature.
            // Usually reflection handling varargs is tricky.
            // Let's assume it takes an array of ActionButton
            val confirmMethod = dialogTypeClass.methods.find { it.name == "confirmation" }
            val dialogType = if (confirmMethod != null) {
                if (confirmMethod.parameterCount == 1 && confirmMethod.parameterTypes[0].isArray) {
                     val arr = java.lang.reflect.Array.newInstance(actionButtonClass, 1)
                     java.lang.reflect.Array.set(arr, 0, yesButton)
                     confirmMethod.invoke(null, arr)
                } else {
                     // Maybe multiple args?
                     confirmMethod.invoke(null, yesButton) // risky if more args required
                }
            } else {
                throw NoSuchMethodException("DialogType.confirmation not found")
            }

            // Create Dialog
            val createMethod = dialogClass.getMethod("create", Consumer::class.java)
            val builderConsumer = Consumer<Any> { builder ->
                try {
                    val bClass = builder.javaClass
                    bClass.getMethod("base", dialogBaseClass).invoke(builder, dialogBase)
                    bClass.getMethod("type", dialogTypeClass).invoke(builder, dialogType)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            val dialog = createMethod.invoke(null, builderConsumer)

            // Show Dialog
            // player.showDialog(dialog)
            val showMethod = sender.javaClass.getMethod("showDialog", dialogClass)
            showMethod.invoke(sender, dialog)
            
            sender.sendMessage("§aDialog opened successfully via Reflection.")

        } catch (e: ClassNotFoundException) {
            sender.sendMessage("§cPaper Dialog API classes not found.")
            sender.sendMessage("§eFalling back to Conversation API...")
            openConversation(sender)
        } catch (e: Exception) {
            sender.sendMessage("§cReflection Error: ${e.message}")
            e.printStackTrace()
            sender.sendMessage("§eFalling back to Conversation API...")
            openConversation(sender)
        }

        return true
    }

    private fun openConversation(player: Player) {
        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
        val factory = ConversationFactory(plugin)
            .withModality(true)
            .withFirstPrompt(NamePrompt())
            .withLocalEcho(false)
            .withEscapeSequence("cancel")
            .buildConversation(player)
        factory.begin()
    }

    private class NamePrompt : StringPrompt() {
        override fun getPromptText(context: ConversationContext): String {
            return "§a[Fallback] §eあなたの名前を入力してください (cancelで中止):"
        }

        override fun acceptInput(context: ConversationContext, input: String?): Prompt? {
            context.forWhom.sendRawMessage("§a入力されたテキスト: $input")
            return Prompt.END_OF_CONVERSATION
        }
    }
}
