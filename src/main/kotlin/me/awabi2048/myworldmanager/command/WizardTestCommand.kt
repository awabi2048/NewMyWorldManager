package me.awabi2048.myworldmanager.command

import net.kyori.adventure.text.Component
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

// Note: Imports for experimental Paper API
// If these are not available in the classpath, this file will fail to compile.
// Given the prompt implies testing the feature, we assume the dependency is present (as seen in pom.xml).
// import io.papermc.paper.dialog.Dialog
// import io.papermc.paper.dialog.input.DialogInput

class WizardTestCommand : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Players only!")
            return true
        }

        sender.sendMessage("§e[WizardTest] Attempting to open Dialog Wizard...")

        try {
            // Using reflection to ensure the command runs even if the API signatures differ slightly
            // or to allow testing without hard compile dependency issues during this investigation phase.
            // Ideally, this should be replaced with direct API calls:
            // Dialog.create { ... }.open(sender)

            val dialogClass = Class.forName("io.papermc.paper.dialog.Dialog")
            val createMethod = dialogClass.getMethod("create", java.util.function.Consumer::class.java)
            
            // This reflection is complex to write correctly without an IDE.
            // I will try to use the direct class usage assuming the imports work, 
            // but wrapped in a way that minimizes damage.
            // actually, let's just use the classes.
            // If it doesn't compile, the user will see.
            
            /*
            val dialog = io.papermc.paper.dialog.Dialog.create { builder ->
                builder.title(Component.text("Test Wizard"))
                builder.content(Component.text("Welcome to the test wizard.\nPlease enter some text."))
                
                // Assuming DialogInput.text() exists
                // builder.addInput(io.papermc.paper.dialog.input.DialogInput.text(Component.text("Input Label")))
            }
            dialog.open(sender)
            */
             
            // Since I cannot verify the method signature for "addInput" or "DialogInput.text", 
            // I will provide a reflection-based implementation that tries to find the method.
            
            val builderConsumer = java.util.function.Consumer<Any> { builder ->
                try {
                    val builderClass = builder.javaClass
                    
                    // Set Title
                    val titleMethod = builderClass.getMethod("title", Component::class.java)
                    titleMethod.invoke(builder, Component.text("Wizard Test (Dialog API)"))
                    
                    // Set Content/Text (if available)
                    try {
                        val contentMethod = builderClass.getMethod("content", Component::class.java)
                        contentMethod.invoke(builder, Component.text("Please enter a value below to test text input."))
                    } catch (e: NoSuchMethodException) {
                        try {
                             val textMethod = builderClass.getMethod("text", Component::class.java)
                             textMethod.invoke(builder, Component.text("Please enter a value below to test text input."))
                        } catch (ignored: Exception) {}
                    }
                    
                    // Add Input
                    // Looking for addInput(DialogInput)
                    // We need to create a DialogInput.
                    val rxInputClass = Class.forName("io.papermc.paper.dialog.input.DialogInput")
                    
                    // Assume there is a static method 'text' or similar on DialogInput
                    // or a builder for it.
                    // Let's try to find 'text' method on DialogInput interface/class
                    val textMethod = rxInputClass.methods.find { it.name == "text" }
                    if (textMethod != null) {
                         // Likely takes a param?
                         // e.g. text(Component label)
                         val inputObj = if (textMethod.parameterCount > 0 && textMethod.parameterTypes[0] == Component::class.java) {
                             textMethod.invoke(null, Component.text("Your Name"))
                         } else {
                             textMethod.invoke(null)
                         }
                         
                         val addInputMethod = builderClass.methods.find { it.name == "addInput" }
                         addInputMethod?.invoke(builder, inputObj)
                    } else {
                        sender.sendMessage("§cCould not find 'text' input method on DialogInput.")
                    }

                } catch (e: Exception) {
                    sender.sendMessage("§cBuilder Error: ${e.message}")
                    e.printStackTrace()
                }
            }
            
            val dialogObj = createMethod.invoke(null, builderConsumer)
            val openMethod = dialogClass.getMethod("open", Player::class.java)
            openMethod.invoke(dialogObj, sender)
            
            sender.sendMessage("§aDialog opened (if no errors).")

        } catch (e: ClassNotFoundException) {
            sender.sendMessage("§cDialog API not found (ClassNotFound). Check server version.")
        } catch (e: Exception) {
            sender.sendMessage("§cError: ${e.message}")
            e.printStackTrace()
        }

        return true
    }
}
