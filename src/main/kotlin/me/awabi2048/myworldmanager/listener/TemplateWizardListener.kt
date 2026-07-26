@file:Suppress("REDUNDANT_CALL_OF_CONVERSION_METHOD")

package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.ui.ManagedMenuPresenter

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuDialogButton
import com.awabi2048.ccsystem.api.gui.MenuDialogHandler
import com.awabi2048.ccsystem.api.gui.MenuDialogInput
import com.awabi2048.ccsystem.api.gui.MenuDialogRequest
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.gui.TemplateWizardGui
import me.awabi2048.myworldmanager.model.TemplateData
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import me.awabi2048.myworldmanager.util.cancelWithDebug
import org.bukkit.plugin.java.JavaPlugin

class TemplateWizardListener : Listener {

    @EventHandler(ignoreCancelled = false)
    fun onPlayerInventoryClick(event: InventoryClickEvent) {
        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
        val player = event.whoClicked as? Player ?: return
        val session = plugin.templateWizardGui.getSession(player.uniqueId) ?: return

        val view = event.view
        val title = PlainTextComponentSerializer.plainText().serialize(view.title())
        val lang = plugin.languageManager
        if (!lang.isKeyMatch(title, "gui.template_wizard.title")) return

        if (event.clickedInventory == player.inventory) {
            val item = event.currentItem
            if (item != null && item.type != Material.AIR) {
                session.icon = item.type
                plugin.soundManager.playClickSound(player, item)
                event.cancelWithDebug("TemplateWizardListener.onPlayerInventoryClick: template wizard icon pick click", force = true)
                plugin.templateWizardGui.open(player)
            }
        }
    }

    fun openTemplateNameInput(
        plugin: MyWorldManager,
        player: Player,
        session: TemplateWizardGui.WizardSession
    ) {
        val lang = plugin.languageManager

        if (plugin.playerPlatformResolver.isBedrock(player)) {
            if (!plugin.floodgateFormBridge.isAvailable(player)) {
                plugin.floodgateFormBridge.notifyFallbackCancelled(player)
                plugin.templateWizardGui.open(player)
                return
            }

            val opened =
                plugin.floodgateFormBridge.sendCustomInputForm(
                    player = player,
                    title = lang.getMessage(player, "gui.bedrock.input.template_wizard_name.title"),
                    label = lang.getMessage(player, "gui.bedrock.input.template_wizard_name.label"),
                    placeholder =
                        lang.getMessage(player, "gui.bedrock.input.template_wizard_name.placeholder"),
                    defaultValue = session.name,
                    onSubmit = { value ->
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            applyTemplateName(plugin, player, session, value.trim())
                        })
                    },
                    onClosed = {
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            if (player.isOnline) {
                                plugin.templateWizardGui.open(player)
                            }
                        })
                    }
                )
            if (!opened) {
                plugin.floodgateFormBridge.notifyFallbackCancelled(player)
                plugin.templateWizardGui.open(player)
            }
            return
        }

        showTextInputDialog(
            plugin = plugin,
            player = player,
            id = "template-wizard-name",
            title = lang.getMessage(player, "gui.template_wizard.name_input.display"),
            label = lang.getMessage(player, "gui.bedrock.input.template_wizard_name.label"),
            inputId = "template_name",
            initial = session.name,
            maxLength = 30,
            onSubmit = { value -> applyTemplateName(plugin, player, session, value.trim()) },
        )
    }

    fun openTemplateDescriptionInput(
        plugin: MyWorldManager,
        player: Player,
        session: TemplateWizardGui.WizardSession
    ) {
        val lang = plugin.languageManager

        if (plugin.playerPlatformResolver.isBedrock(player)) {
            if (!plugin.floodgateFormBridge.isAvailable(player)) {
                plugin.floodgateFormBridge.notifyFallbackCancelled(player)
                plugin.templateWizardGui.open(player)
                return
            }

            val opened =
                plugin.floodgateFormBridge.sendCustomInputForm(
                    player = player,
                    title = lang.getMessage(player, "gui.bedrock.input.template_wizard_desc.title"),
                    label = lang.getMessage(player, "gui.bedrock.input.template_wizard_desc.label"),
                    placeholder =
                        lang.getMessage(player, "gui.bedrock.input.template_wizard_desc.placeholder"),
                    defaultValue = session.description.firstOrNull().orEmpty(),
                    onSubmit = { value ->
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            applyTemplateDescription(plugin, player, session, value.trim())
                        })
                    },
                    onClosed = {
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            if (player.isOnline) {
                                plugin.templateWizardGui.open(player)
                            }
                        })
                    }
                )
            if (!opened) {
                plugin.floodgateFormBridge.notifyFallbackCancelled(player)
                plugin.templateWizardGui.open(player)
            }
            return
        }

        showTextInputDialog(
            plugin = plugin,
            player = player,
            id = "template-wizard-description",
            title = lang.getMessage(player, "gui.template_wizard.desc_input.display"),
            label = lang.getMessage(player, "gui.bedrock.input.template_wizard_desc.label"),
            inputId = "template_desc",
            initial = session.description.firstOrNull().orEmpty(),
            maxLength = 120,
            onSubmit = { value -> applyTemplateDescription(plugin, player, session, value.trim()) },
        )
    }

    private fun showTextInputDialog(
        plugin: MyWorldManager,
        player: Player,
        id: String,
        title: String,
        label: String,
        inputId: String,
        initial: String,
        maxLength: Int,
        onSubmit: (String) -> Unit,
    ) {
        val lang = plugin.languageManager
        CCSystem.getAPI().getMenuDialogService().show(
            player,
            MenuDialogRequest(
                owner = "myworldmanager",
                id = id,
                title = Component.text(title, NamedTextColor.YELLOW),
                body = listOf(Component.text(label)),
                inputs = listOf(
                    MenuDialogInput.Text(inputId, Component.text(label), initial, maxLength = maxLength),
                ),
                confirm = MenuDialogButton(
                    Component.text(lang.getMessage(player, "gui.common.confirm"), NamedTextColor.GREEN),
                    MenuDialogHandler { _, response ->
                        onSubmit(response.textValue(inputId))
                        MenuActionResult.Success(MenuUpdate.Close)
                    },
                ),
                cancel = MenuDialogButton(
                    Component.text(lang.getMessage(player, "gui.common.cancel"), NamedTextColor.RED),
                    MenuDialogHandler { target, _ ->
                        plugin.templateWizardGui.open(target)
                        MenuActionResult.Success(MenuUpdate.Close)
                    },
                ),
            ),
        )
    }

    private fun applyTemplateName(
        plugin: MyWorldManager,
        player: Player,
        session: TemplateWizardGui.WizardSession,
        input: String
    ) {
        if (input.isEmpty()) {
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.template_wizard_name_required"))
            openTemplateNameInput(plugin, player, session)
            return
        }

        session.name = input
        session.id = input.replace(Regex("[^a-zA-Z0-9_-]"), "").lowercase()
        if (session.id.isEmpty()) {
            session.id = "tpl_" + java.util.UUID.randomUUID().toString().substring(0, 8)
        }
        player.sendMessage(
            plugin.languageManager.getMessage(
                player,
                "messages.template_wizard_name_set",
                mapOf("name" to session.name, "id" to session.id)
            )
        )
        plugin.templateWizardGui.open(player)
    }

    private fun applyTemplateDescription(
        plugin: MyWorldManager,
        player: Player,
        session: TemplateWizardGui.WizardSession,
        input: String
    ) {
        session.description = if (input.isEmpty()) emptyList() else listOf(input)
        player.sendMessage(plugin.languageManager.getMessage(player, "messages.template_wizard_description_set"))
        plugin.templateWizardGui.open(player)
    }
}
