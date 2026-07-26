package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuActionContext
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuRuntimeActions
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.TemplateData
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.GuiItemFactory
import me.awabi2048.myworldmanager.util.GuiLoreBuilder
import me.awabi2048.myworldmanager.util.ItemTag
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class TemplateWizardGui(private val plugin: MyWorldManager) {
    private val runtime = CCSystem.getAPI().getMenuRuntimeService()
    private val sessions = ConcurrentHashMap<UUID, WizardSession>()

    data class WizardSession(
        val sourceWorldName: String,
        val sourceWorldKey: String,
        var id: String = "",
        var name: String = "",
        var description: List<String> = emptyList(),
        var icon: Material = Material.GRASS_BLOCK,
        var originLocation: org.bukkit.Location? = null,
        var inputState: InputState = InputState.NONE,
    )

    enum class InputState { NONE, NAME, DESCRIPTION }

    init {
        runtime.register(
            InventoryMenuDefinition(
                owner = OWNER,
                id = ROUTE_ID,
                renderer = { context -> render(context.player) },
                actions = mapOf(
                    ACTION_NAME to MenuActionHandler(::name),
                    ACTION_DESCRIPTION to MenuActionHandler(::description),
                    ACTION_ICON to MenuActionHandler(::icon),
                    ACTION_ORIGIN to MenuActionHandler(::origin),
                    ACTION_SAVE to MenuActionHandler(::save),
                    ACTION_VALIDATE to MenuActionHandler(::validate),
                    ACTION_CANCEL to MenuActionHandler(::cancel),
                    MenuRuntimeActions.PLAYER_INVENTORY_CLICK to
                        MenuActionHandler(::selectPlayerInventoryItem),
                ),
            ),
        )
    }

    fun open(player: Player) {
        sessions.getOrPut(player.uniqueId) {
            WizardSession(player.world.name, player.world.key.toString())
        }
        runtime.navigate(player, MenuRoute(OWNER, ROUTE_ID))
    }

    private fun render(player: Player): InventoryMenuView {
        val session = sessions[player.uniqueId] ?: error("テンプレートウィザードのセッションがありません")
        val lang = plugin.languageManager
        val layout = GuiHelper.settingsLayout()
        val choices = GuiHelper.threeChoiceLayout()
        val elements = mutableListOf<MenuElement>()

        elements += MenuElement(
            13,
            setting(
                Material.FILLED_MAP,
                lang.getMessage(player, "gui.template_wizard.status.display"),
                GuiLoreBuilder(lang, player)
                    .data(lang.getMessage(player, "gui.template_wizard.status.source_world"), session.sourceWorldKey)
                    .data(
                        lang.getMessage(player, "gui.template_wizard.status.template_id"),
                        session.id.ifEmpty { lang.getMessage(player, "general.unknown") },
                    )
                    .data(
                        lang.getMessage(player, "gui.template_wizard.status.spawn"),
                        session.originLocation?.let { "(${it.blockX}, ${it.blockY}, ${it.blockZ})" }
                            ?: lang.getMessage(player, "general.unknown"),
                    )
                    .buildSpec(),
                ItemTag.TYPE_GUI_INFO,
            ),
            GuiElementRole.CONTENT,
        )
        elements += MenuElement(
            choices.leftSlot,
            setting(
                plugin.menuConfigManager.getIconMaterial(MENU_ID, "name_input", Material.NAME_TAG),
                lang.getMessage(player, "gui.template_wizard.name_input.display"),
                GuiLoreBuilder(lang, player)
                    .block(lang.getMessageList(
                        player,
                        "gui.template_wizard.name_input.description",
                        mapOf(
                            "name" to session.name.ifEmpty { "未設定" },
                            "id" to session.id.ifEmpty { "未設定" },
                        ),
                    ).map(GuiLoreLine::Text))
                    .actions(lang.getMessage(player, "gui.template_wizard.name_input.action"))
                    .buildSpec(),
                "name_input",
            ),
            GuiElementRole.ACTION,
            ACTION_NAME,
        )
        elements += MenuElement(
            choices.centerSlot,
            setting(
                plugin.menuConfigManager.getIconMaterial(MENU_ID, "desc_input", Material.WRITABLE_BOOK),
                lang.getMessage(player, "gui.template_wizard.desc_input.display"),
                GuiLoreBuilder(lang, player)
                    .block(lang.getMessageList(
                        player,
                        "gui.template_wizard.desc_input.description",
                        mapOf("desc" to session.description.joinToString("\n") { "§f  - $it" }),
                    ).map(GuiLoreLine::Text))
                    .actions(lang.getMessage(player, "gui.template_wizard.desc_input.action"))
                    .buildSpec(),
                "desc_input",
            ),
            GuiElementRole.ACTION,
            ACTION_DESCRIPTION,
        )
        elements += MenuElement(
            choices.rightSlot,
            setting(
                session.icon,
                lang.getMessage(player, "gui.template_wizard.icon_select.display"),
                GuiLoreBuilder(lang, player)
                    .block(lang.getMessageList(
                        player,
                        "gui.template_wizard.icon_select.description",
                        mapOf("icon" to session.icon.name),
                    ).map(GuiLoreLine::Text))
                    .actions(lang.getMessage(player, "gui.template_wizard.icon_select.action"))
                    .buildSpec(),
                "icon_select",
            ),
            GuiElementRole.ACTION,
            ACTION_ICON,
        )
        elements += MenuElement(
            31,
            setting(
                plugin.menuConfigManager.getIconMaterial(MENU_ID, "origin_set", Material.COMPASS),
                lang.getMessage(player, "gui.template_wizard.origin_set.display"),
                GuiLoreBuilder(lang, player)
                    .block(lang.getMessageList(
                        player,
                        "gui.template_wizard.origin_set.description",
                        mapOf(
                            "origin" to (session.originLocation?.let {
                                "${it.blockX}, ${it.blockY}, ${it.blockZ}"
                            } ?: "未設定"),
                        ),
                    ).map(GuiLoreLine::Text))
                    .actions(lang.getMessage(player, "gui.template_wizard.origin_set.action"))
                    .buildSpec(),
                "origin_set",
            ),
            GuiElementRole.ACTION,
            ACTION_ORIGIN,
        )

        val missing = buildList {
            if (session.id.isEmpty() || session.name.isEmpty()) {
                add(lang.getMessage(player, "gui.template_wizard.requirement.name"))
            }
            if (session.originLocation == null) {
                add(lang.getMessage(player, "gui.template_wizard.requirement.spawn"))
            }
            if (session.originLocation?.world?.name != session.sourceWorldName) {
                add(lang.getMessage(player, "gui.template_wizard.requirement.source_world"))
            }
        }
        elements += if (missing.isEmpty()) {
            MenuElement(
                40,
                setting(
                    plugin.menuConfigManager.getIconMaterial(MENU_ID, "save_confirm", Material.NETHER_STAR),
                    lang.getMessage(player, "gui.template_wizard.save_confirm.display"),
                    GuiLoreBuilder(lang, player)
                        .block(lang.getMessageList(
                            player,
                            "gui.template_wizard.save_confirm.description",
                        ).map(GuiLoreLine::Text))
                        .actions(lang.getMessage(player, "gui.template_wizard.save_confirm.action"))
                        .buildSpec(),
                    "save_confirm",
                ),
                GuiElementRole.CONFIRM,
                ACTION_SAVE,
            )
        } else {
            MenuElement(
                40,
                setting(
                    Material.BARRIER,
                    lang.getMessage(player, "gui.template_wizard.requirement.display"),
                    GuiLoreBuilder(lang, player).block(missing.map(GuiLoreLine::Warning)).buildSpec(),
                    ItemTag.TYPE_GUI_INFO,
                ),
                GuiElementRole.CONTENT,
            )
        }
        elements += MenuElement(
            39,
            setting(
                Material.SPYGLASS,
                lang.getMessage(player, "gui.template_wizard.validate.display"),
                GuiLoreBuilder(lang, player)
                    .actions(lang.getMessage(player, "gui.template_wizard.validate.action"))
                    .buildSpec(),
                "wizard_validate",
            ),
            GuiElementRole.ACTION,
            ACTION_VALIDATE,
        )
        elements += MenuElement(
            49,
            setting(
                Material.RED_CONCRETE,
                lang.getMessage(player, "gui.template_wizard.cancel.display"),
                GuiLoreBuilder(lang, player)
                    .actions(lang.getMessage(player, "gui.template_wizard.cancel.action"))
                    .buildSpec(),
                "wizard_cancel",
            ),
            GuiElementRole.CANCEL,
            ACTION_CANCEL,
        )
        return InventoryMenuView(
            layout.size,
            GuiHelper.inventoryTitle(lang.getMessage(player, "gui.template_wizard.title")),
            elements,
            allowPlayerInventoryInteraction = true,
        )
    }

    private fun selectPlayerInventoryItem(context: MenuActionContext): MenuActionResult {
        val session = session(context) ?: return MenuActionResult.Rejected()
        if (context.item.type == Material.AIR) return MenuActionResult.Ignored
        session.icon = context.item.type
        return MenuActionResult.Success(MenuUpdate.Refresh)
    }

    private fun name(context: MenuActionContext): MenuActionResult {
        val session = session(context) ?: return MenuActionResult.Rejected()
        plugin.templateWizardListener.openTemplateNameInput(plugin, context.player, session)
        return MenuActionResult.Success(MenuUpdate.None)
    }

    private fun description(context: MenuActionContext): MenuActionResult {
        val session = session(context) ?: return MenuActionResult.Rejected()
        plugin.templateWizardListener.openTemplateDescriptionInput(plugin, context.player, session)
        return MenuActionResult.Success(MenuUpdate.None)
    }

    private fun icon(context: MenuActionContext): MenuActionResult {
        val session = session(context) ?: return MenuActionResult.Rejected()
        if (context.cursor.type == Material.AIR) {
            context.player.sendMessage(
                plugin.languageManager.getMessage(context.player, "messages.template_wizard_icon_help"),
            )
            return MenuActionResult.Success(MenuUpdate.None)
        }
        session.icon = context.cursor.type
        return MenuActionResult.Success(MenuUpdate.Refresh)
    }

    private fun origin(context: MenuActionContext): MenuActionResult {
        val session = session(context) ?: return MenuActionResult.Rejected()
        if (context.player.world.name != session.sourceWorldName) {
            context.player.sendMessage(
                plugin.languageManager.getMessage(context.player, "messages.template_wizard_source_changed"),
            )
            return MenuActionResult.Rejected()
        }
        session.originLocation = context.player.location.clone()
        context.player.sendMessage(
            plugin.languageManager.getMessage(context.player, "messages.template_wizard_spawn_set"),
        )
        return MenuActionResult.Success(MenuUpdate.Refresh)
    }

    private fun save(context: MenuActionContext): MenuActionResult {
        val player = context.player
        val session = session(context) ?: return MenuActionResult.Rejected()
        val lang = plugin.languageManager
        if (session.id.isEmpty()) {
            player.sendMessage(lang.getMessage(player, "messages.template_wizard_id_missing"))
            return MenuActionResult.Rejected()
        }
        val origin = session.originLocation
        if (origin == null || origin.world?.name != session.sourceWorldName) {
            player.sendMessage(lang.getMessage(player, "messages.template_wizard_source_changed"))
            return MenuActionResult.Rejected()
        }
        if (plugin.templateRepository.findById(session.id) != null) {
            player.sendMessage(lang.getMessage(player, "messages.template_wizard_id_exists"))
            return MenuActionResult.Rejected()
        }
        plugin.templateRepository.saveTemplate(
            TemplateData(
                session.id,
                session.sourceWorldName,
                session.name,
                session.description,
                session.icon,
                origin.clone(),
            ),
        )
        player.sendMessage(
            lang.getMessage(player, "messages.wizard_registered", mapOf("template" to session.id)),
        )
        sessions.remove(player.uniqueId)
        plugin.templateRepository.loadTemplates()
        return MenuActionResult.Success(MenuUpdate.Close)
    }

    private fun validate(context: MenuActionContext): MenuActionResult {
        val session = session(context) ?: return MenuActionResult.Rejected()
        val valid = session.id.isNotEmpty() &&
            session.name.isNotEmpty() &&
            session.originLocation?.world?.name == session.sourceWorldName
        context.player.sendMessage(
            plugin.languageManager.getMessage(
                context.player,
                if (valid) {
                    "messages.template_wizard_validation_success"
                } else {
                    "messages.template_wizard_validation_failed"
                },
            ),
        )
        return MenuActionResult.Success(MenuUpdate.Refresh)
    }

    private fun cancel(context: MenuActionContext): MenuActionResult {
        sessions.remove(context.player.uniqueId)
        context.player.sendMessage(
            plugin.languageManager.getMessage(context.player, "messages.operation_cancelled"),
        )
        return MenuActionResult.Success(MenuUpdate.Back)
    }

    private fun session(context: MenuActionContext): WizardSession? = sessions[context.player.uniqueId]

    private fun setting(material: Material, display: String, lore: GuiLoreSpec, id: String): ItemStack =
        GuiItemFactory.item(material, display, lore, id)

    fun getSession(uuid: UUID) = sessions[uuid]
    fun removeSession(uuid: UUID) = sessions.remove(uuid)
    fun clearAll() = sessions.clear()

    companion object {
        private const val OWNER = "myworldmanager"
        private const val ROUTE_ID = "template-wizard"
        private const val MENU_ID = "template_wizard"
        private const val ACTION_NAME = "name"
        private const val ACTION_DESCRIPTION = "description"
        private const val ACTION_ICON = "icon"
        private const val ACTION_ORIGIN = "origin"
        private const val ACTION_SAVE = "save"
        private const val ACTION_VALIDATE = "validate"
        private const val ACTION_CANCEL = "cancel"
    }
}
