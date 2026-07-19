@file:Suppress("REDUNDANT_CALL_OF_CONVERSION_METHOD")

package me.awabi2048.myworldmanager.listener

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
import me.awabi2048.myworldmanager.gui.TemplateWizardGui
import me.awabi2048.myworldmanager.model.TemplateData
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.key.Key
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
    fun onInventoryClick(event: InventoryClickEvent) {
        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
        val view = event.view
        val title = PlainTextComponentSerializer.plainText().serialize(view.title())
        val player = event.whoClicked as? Player ?: return
        val lang = plugin.languageManager

        if (!lang.isKeyMatch(title, "gui.template_wizard.title")) return

        event.cancelWithDebug("TemplateWizardListener.onInventoryClick: template wizard GUI click")
        if (event.clickedInventory != view.topInventory) return
        val currentItem = event.currentItem ?: return
        if (currentItem.type == Material.AIR) return

        val id = ItemTag.getType(currentItem) ?: return
        val session = plugin.templateWizardGui.getSession(player.uniqueId) ?: return

        when (id) {
            "name_input" -> {
                plugin.soundManager.playClickSound(player, currentItem)
                player.closeInventory()
                openTemplateNameInput(plugin, player, session)
            }

            "desc_input" -> {
                plugin.soundManager.playClickSound(player, currentItem)
                player.closeInventory()
                openTemplateDescriptionInput(plugin, player, session)
            }

            "icon_select" -> {
                plugin.soundManager.playClickSound(player, currentItem)
                val cursor = event.cursor
                if (cursor.type != Material.AIR) {
                    session.icon = cursor.type
                    plugin.soundManager.playClickSound(player, currentItem)
                    plugin.templateWizardGui.open(player)
                    return
                }
                player.sendMessage(lang.getMessage(player, "messages.template_wizard_icon_help"))
            }

            "origin_set" -> {
                plugin.soundManager.playClickSound(player, currentItem)
                if (player.world.name != session.sourceWorldName) {
                    player.sendMessage(lang.getMessage(player, "messages.template_wizard_source_changed"))
                    return
                }
                session.originLocation = player.location.clone()
                player.sendMessage(lang.getMessage(player, "messages.template_wizard_spawn_set"))
                plugin.templateWizardGui.open(player)
            }

            "save_confirm" -> {
                plugin.soundManager.playClickSound(player, currentItem)

                val templateId = session.id
                if (templateId.isEmpty()) {
                    player.sendMessage(lang.getMessage(player, "messages.template_wizard_id_missing"))
                    return
                }
                val origin = session.originLocation
                if (origin == null || origin.world?.name != session.sourceWorldName) {
                    player.sendMessage(lang.getMessage(player, "messages.template_wizard_source_changed"))
                    return
                }
                if (plugin.templateRepository.findById(templateId) != null) {
                    player.sendMessage(lang.getMessage(player, "messages.template_wizard_id_exists"))
                    return
                }

                plugin.templateRepository.saveTemplate(
                    TemplateData(
                        id = templateId,
                        path = session.sourceWorldName,
                        name = session.name,
                        description = session.description,
                        icon = session.icon,
                        originLocation = origin.clone()
                    )
                )

                player.sendMessage(
                    lang.getMessage(
                        player,
                        "messages.wizard_registered",
                        mapOf("template" to templateId)
                    )
                )
                player.closeInventory()
                plugin.templateWizardGui.removeSession(player.uniqueId)
                plugin.templateRepository.loadTemplates()
            }

            "wizard_cancel" -> {
                plugin.templateWizardGui.removeSession(player.uniqueId)
                player.closeInventory()
                player.sendMessage(lang.getMessage(player, "messages.operation_cancelled"))
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    plugin.adminCommandGui.open(player)
                })
            }

            "wizard_validate" -> {
                val valid =
                    session.id.isNotEmpty() &&
                        session.name.isNotEmpty() &&
                        session.originLocation?.world?.name == session.sourceWorldName
                player.sendMessage(
                    lang.getMessage(
                        player,
                        if (valid) {
                            "messages.template_wizard_validation_success"
                        } else {
                            "messages.template_wizard_validation_failed"
                        }
                    )
                )
                plugin.templateWizardGui.open(player)
            }
        }
    }

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

    @EventHandler
    fun onTemplateWizardDialog(event: PlayerCustomClickEvent) {
        val identifier = event.identifier
        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)

        if (
            identifier != Key.key("mwm:template_wizard/name_submit") &&
                identifier != Key.key("mwm:template_wizard/desc_submit") &&
                identifier != Key.key("mwm:template_wizard/input_cancel")
        ) {
            return
        }

        val conn = event.commonConnection as? PlayerGameConnection ?: return
        val player = conn.player
        val session = plugin.templateWizardGui.getSession(player.uniqueId) ?: return

        if (identifier == Key.key("mwm:template_wizard/input_cancel")) {
            plugin.templateWizardGui.open(player)
            return
        }

        val view = event.getDialogResponseView() ?: return
        if (identifier == Key.key("mwm:template_wizard/name_submit")) {
            val input = view.getText("template_name")?.toString().orEmpty().trim()
            applyTemplateName(plugin, player, session, input)
            return
        }

        val input = view.getText("template_desc")?.toString().orEmpty().trim()
        applyTemplateDescription(plugin, player, session, input)
    }

    private fun openTemplateNameInput(
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

        val dialog = Dialog.create { builder ->
            builder.empty()
                .base(
                    DialogBase.builder(Component.text(lang.getMessage(player, "gui.template_wizard.name_input.display"), NamedTextColor.YELLOW))
                        .body(
                            listOf(
                                DialogBody.plainMessage(
                                    Component.text(lang.getMessage(player, "gui.bedrock.input.template_wizard_name.label"))
                                )
                            )
                        )
                        .inputs(
                            listOf(
                                DialogInput.text(
                                    "template_name",
                                    Component.text(lang.getMessage(player, "gui.bedrock.input.template_wizard_name.label"))
                                ).initial(session.name).maxLength(30).build()
                            )
                        )
                        .build()
                )
                .type(
                    DialogType.confirmation(
                        ActionButton.create(
                            Component.text(lang.getMessage(player, "gui.common.confirm"), NamedTextColor.GREEN),
                            null,
                            100,
                            DialogAction.customClick(Key.key("mwm:template_wizard/name_submit"), null)
                        ),
                        ActionButton.create(
                            Component.text(lang.getMessage(player, "gui.common.cancel"), NamedTextColor.RED),
                            null,
                            200,
                            DialogAction.customClick(Key.key("mwm:template_wizard/input_cancel"), null)
                        )
                    )
                )
        }
        player.showDialog(dialog)
    }

    private fun openTemplateDescriptionInput(
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

        val dialog = Dialog.create { builder ->
            builder.empty()
                .base(
                    DialogBase.builder(Component.text(lang.getMessage(player, "gui.template_wizard.desc_input.display"), NamedTextColor.YELLOW))
                        .body(
                            listOf(
                                DialogBody.plainMessage(
                                    Component.text(lang.getMessage(player, "gui.bedrock.input.template_wizard_desc.label"))
                                )
                            )
                        )
                        .inputs(
                            listOf(
                                DialogInput.text(
                                    "template_desc",
                                    Component.text(lang.getMessage(player, "gui.bedrock.input.template_wizard_desc.label"))
                                ).initial(session.description.firstOrNull().orEmpty()).maxLength(120).build()
                            )
                        )
                        .build()
                )
                .type(
                    DialogType.confirmation(
                        ActionButton.create(
                            Component.text(lang.getMessage(player, "gui.common.confirm"), NamedTextColor.GREEN),
                            null,
                            100,
                            DialogAction.customClick(Key.key("mwm:template_wizard/desc_submit"), null)
                        ),
                        ActionButton.create(
                            Component.text(lang.getMessage(player, "gui.common.cancel"), NamedTextColor.RED),
                            null,
                            200,
                            DialogAction.customClick(Key.key("mwm:template_wizard/input_cancel"), null)
                        )
                    )
                )
        }
        player.showDialog(dialog)
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
