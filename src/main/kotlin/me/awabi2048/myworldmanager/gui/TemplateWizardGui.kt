package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiMenuActionIntent
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryData
import com.awabi2048.ccsystem.api.gui.GuiMenuEntrySpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiNameStyle
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuActionContext
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuActionSafety
import com.awabi2048.ccsystem.api.gui.MenuGesture
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuRuntimeActions
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import com.awabi2048.ccsystem.api.gui.PlayerInventoryInteraction
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.service.MwmReversibleContracts
import me.awabi2048.myworldmanager.model.TemplateData
import me.awabi2048.myworldmanager.util.GuiHelper
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
                onClose = { context ->
                    Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                        val current = CCSystem.getAPI().getMenuNavigationService().currentRoute(context.player)
                        if (current?.owner != OWNER || current.id != ROUTE_ID) {
                            sessions.remove(context.player.uniqueId)
                        }
                    }, 2L)
                },
            ),
        )
    }

    fun open(player: Player) {
        runtime.navigate(player, prepareOpen(player))
    }

    fun prepareOpen(player: Player): MenuRoute {
        sessions.getOrPut(player.uniqueId) {
            WizardSession(player.world.name, player.world.key.toString())
        }
        return MenuRoute(OWNER, ROUTE_ID)
    }

    private fun render(player: Player): InventoryMenuView {
        val session = sessions[player.uniqueId] ?: error("テンプレートウィザードのセッションがありません")
        val lang = plugin.languageManager
        val layout = GuiHelper.settingsLayout()
        val choices = GuiHelper.threeChoiceLayout()
        val elements = mutableListOf<MenuElement>()

        elements += menuEntry(
            player, 13, Material.FILLED_MAP,
            lang.getMessage(player, "gui.template_wizard.status.display"),
            GuiElementRole.CONTENT,
            data = listOf(
                GuiMenuEntryData(lang.getMessage(player, "gui.template_wizard.status.source_world"), session.sourceWorldKey),
                GuiMenuEntryData(lang.getMessage(player, "gui.template_wizard.status.template_id"), session.id.ifEmpty { lang.getMessage(player, "general.unknown") }),
                GuiMenuEntryData(
                    lang.getMessage(player, "gui.template_wizard.status.spawn"),
                    session.originLocation?.let { "(${it.blockX}, ${it.blockY}, ${it.blockZ})" }
                        ?: lang.getMessage(player, "general.unknown"),
                ),
            )
        )
        elements += actionEntry(
            player, choices.leftSlot,
                plugin.menuConfigManager.getIconMaterial(MENU_ID, "name_input", Material.NAME_TAG),
                lang.getMessage(player, "gui.template_wizard.name_input.display"),
                lang.getMessageList(player, "gui.template_wizard.name_input.description", mapOf(
                    "name" to session.name.ifEmpty { "未設定" },
                    "id" to session.id.ifEmpty { "未設定" },
                )),
                ACTION_NAME,
                lang.getMessage(player, "gui.template_wizard.name_input.action"),
            )
        elements += actionEntry(
            player, choices.centerSlot,
                plugin.menuConfigManager.getIconMaterial(MENU_ID, "desc_input", Material.WRITABLE_BOOK),
                lang.getMessage(player, "gui.template_wizard.desc_input.display"),
                lang.getMessageList(player, "gui.template_wizard.desc_input.description", mapOf("desc" to session.description.joinToString("\n") { "§f  - $it" })),
                ACTION_DESCRIPTION,
                lang.getMessage(player, "gui.template_wizard.desc_input.action"),
            )
        elements += actionEntry(
            player, choices.rightSlot,
                session.icon,
                lang.getMessage(player, "gui.template_wizard.icon_select.display"),
                lang.getMessageList(player, "gui.template_wizard.icon_select.description", mapOf("icon" to session.icon.name)),
                ACTION_ICON,
                lang.getMessage(player, "gui.template_wizard.icon_select.action"),
            )
        elements += actionEntry(
            player, 31,
                plugin.menuConfigManager.getIconMaterial(MENU_ID, "origin_set", Material.COMPASS),
                lang.getMessage(player, "gui.template_wizard.origin_set.display"),
                lang.getMessageList(player, "gui.template_wizard.origin_set.description", mapOf(
                    "origin" to (session.originLocation?.let { "${it.blockX}, ${it.blockY}, ${it.blockZ}" } ?: "未設定"),
                )),
                ACTION_ORIGIN,
                lang.getMessage(player, "gui.template_wizard.origin_set.action"),
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
            actionEntry(
                player, 40,
                    plugin.menuConfigManager.getIconMaterial(MENU_ID, "save_confirm", Material.NETHER_STAR),
                    lang.getMessage(player, "gui.template_wizard.save_confirm.display"),
                    lang.getMessageList(player, "gui.template_wizard.save_confirm.description"),
                ACTION_SAVE,
                lang.getMessage(player, "gui.template_wizard.save_confirm.action"),
                GuiElementRole.CONFIRM,
            )
        } else {
            menuEntry(
                player, 40, Material.BARRIER,
                lang.getMessage(player, "gui.template_wizard.requirement.display"),
                GuiElementRole.CONTENT,
                warnings = missing,
            )
        }
        elements += actionEntry(
            player, 39, Material.SPYGLASS,
            lang.getMessage(player, "gui.template_wizard.validate.display"),
            emptyList(), ACTION_VALIDATE,
            lang.getMessage(player, "gui.template_wizard.validate.action"),
        )
        elements += actionEntry(
            player, 49, Material.RED_CONCRETE,
            lang.getMessage(player, "gui.template_wizard.cancel.display"),
            emptyList(), ACTION_CANCEL,
            lang.getMessage(player, "gui.template_wizard.cancel.action"),
            GuiElementRole.CANCEL,
        )
        return InventoryMenuView(
            layout.size,
            GuiHelper.inventoryTitle(lang.getMessage(player, "gui.template_wizard.title")),
            elements,
            playerInventoryInteraction = PlayerInventoryInteraction.SELECTION,
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

    private fun menuEntry(
        player: Player,
        slot: Int,
        material: Material,
        name: String,
        role: GuiElementRole,
        description: List<String> = emptyList(),
        data: List<GuiMenuEntryData> = emptyList(),
        warnings: List<String> = emptyList(),
        actions: List<GuiMenuActionIntent> = emptyList(),
    ): MenuElement = CCSystem.getAPI().getGuiElementService().menuEntry(
        player,
        GuiMenuEntrySpec(
            slot, material, GuiNameSpec.Text(name, GuiNameStyle.DEFAULT), role,
            description = description,
            data = data,
            warnings = warnings,
            actions = actions,
        ),
    )

    private fun actionEntry(
        player: Player,
        slot: Int,
        material: Material,
        name: String,
        description: List<String>,
        actionId: String,
        actionText: String,
        role: GuiElementRole = GuiElementRole.ACTION,
    ): MenuElement = menuEntry(
        player, slot, material, name, role, description = description,
        actions = listOf(menuGestureAction(
            actionId, MenuGesture.ANY, actionText,
            safety = wizardActionSafety(actionId),
            reversibleContract = when (actionId) {
                ACTION_CANCEL -> MwmReversibleContracts.draft("template_cancel")
                ACTION_ORIGIN -> MwmReversibleContracts.draft("template_origin")
                else -> null
            },
        )),
    )

    data class WizardLocationSnapshot(
        val worldUuid: UUID,
        val worldName: String,
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float,
        val pitch: Float,
    )

    data class WizardSessionSnapshot(
        val sourceWorldName: String,
        val sourceWorldKey: String,
        val id: String,
        val name: String,
        val description: List<String>,
        val icon: Material,
        val origin: WizardLocationSnapshot?,
        val inputState: InputState,
    )

    fun snapshot(playerId: UUID): WizardSessionSnapshot? = sessions[playerId]?.let { session ->
        WizardSessionSnapshot(
            session.sourceWorldName,
            session.sourceWorldKey,
            session.id,
            session.name,
            session.description.toList(),
            session.icon,
            session.originLocation?.let { location ->
                WizardLocationSnapshot(
                    requireNotNull(location.world).uid,
                    requireNotNull(location.world).name,
                    location.x, location.y, location.z, location.yaw, location.pitch,
                )
            },
            session.inputState,
        )
    }

    fun restore(playerId: UUID, snapshot: WizardSessionSnapshot?): Boolean {
        if (snapshot == null) sessions.remove(playerId)
        else {
            val origin = snapshot.origin?.let { saved ->
                val world = Bukkit.getWorld(saved.worldUuid) ?: Bukkit.getWorld(saved.worldName) ?: return false
                org.bukkit.Location(world, saved.x, saved.y, saved.z, saved.yaw, saved.pitch)
            }
            sessions[playerId] = WizardSession(
                snapshot.sourceWorldName, snapshot.sourceWorldKey, snapshot.id, snapshot.name,
                snapshot.description.toList(), snapshot.icon, origin, snapshot.inputState,
            )
        }
        return true
    }

    private fun wizardActionSafety(actionId: String): MenuActionSafety = when (actionId) {
        ACTION_NAME,
        ACTION_DESCRIPTION -> MenuActionSafety.INPUT_OR_EXTERNAL_SURFACE
        ACTION_ICON -> MenuActionSafety.INPUT_OR_EXTERNAL_SURFACE
        ACTION_ORIGIN,
        ACTION_CANCEL -> MenuActionSafety.REVERSIBLE
        ACTION_SAVE -> MenuActionSafety.IRREVERSIBLE
        ACTION_VALIDATE -> MenuActionSafety.EXTERNAL_SIDE_EFFECT
        else -> error("Unknown template wizard action safety: $actionId")
    }

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
