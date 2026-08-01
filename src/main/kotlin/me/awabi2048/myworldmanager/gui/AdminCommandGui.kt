package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiMenuActionIntent
import com.awabi2048.ccsystem.api.gui.GuiMenuEntrySpec
import com.awabi2048.ccsystem.api.gui.GuiMenuDisplaySpec
import com.awabi2048.ccsystem.api.gui.GuiMenuCapabilityInvocationSpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuActionContext
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuActionSafety
import com.awabi2048.ccsystem.api.gui.MenuGesture
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.extension.AdminMenuCapabilityPlacements
import me.awabi2048.myworldmanager.service.WorldService
import me.awabi2048.myworldmanager.session.SettingsAction
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import java.util.UUID

class AdminCommandGui(private val plugin: MyWorldManager) {
    private val titleKey = "gui.admin_menu.title"
    private val runtime = CCSystem.getAPI().getMenuRuntimeService()

    init {
        runtime.register(
            InventoryMenuDefinition(
                owner = OWNER,
                id = ROUTE_ID,
                renderer = { context -> render(context.player) },
                actions = mapOf(
                    ACTION_UPDATE_DATA to MenuActionHandler { context ->
                        MenuActionResult.Success(
                            MenuUpdate.Navigate(prepareUpdateDataConfirmation(context.player)),
                        )
                    },
                    ACTION_REPAIR_TEMPLATES to MenuActionHandler { context ->
                        MenuActionResult.Success(
                            MenuUpdate.Navigate(prepareRepairTemplatesConfirmation(context.player)),
                        )
                    },
                    ACTION_CREATE_TEMPLATE to MenuActionHandler(::createTemplate),
                    ACTION_ARCHIVE_ALL to MenuActionHandler { context ->
                        MenuActionResult.Success(
                            MenuUpdate.Navigate(prepareArchiveAllConfirmation(context.player)),
                        )
                    },
                    ACTION_CONVERT to MenuActionHandler(::convert),
                    ACTION_UNLINK to MenuActionHandler { context ->
                        MenuActionResult.Success(
                            MenuUpdate.Navigate(prepareUnlinkConfirmation(context.player)),
                        )
                    },
                    ACTION_EXPORT to MenuActionHandler { context ->
                        MenuActionResult.Success(
                            MenuUpdate.Navigate(
                                prepareExportConfirmation(context.player, context.player.world.name),
                            ),
                        )
                    },
                    ACTION_INFO to MenuActionHandler(::openWorldList),
                    ACTION_PORTALS to MenuActionHandler(::openPortals),
                ),
            ),
        )
        runtime.register(
            InventoryMenuDefinition(
                owner = OWNER,
                id = CONFIRM_ROUTE_ID,
                renderer = { context -> renderConfirmation(context.player) },
                actions = mapOf(
                    ACTION_CONFIRM to MenuActionHandler(::confirm),
                    ACTION_CANCEL to MenuActionHandler(::cancel),
                ),
            ),
        )
    }

    fun open(player: Player) {
        plugin.settingsSessionManager.updateSessionAction(player, player.uniqueId, SettingsAction.ADMIN_MENU, isGui = true)
        runtime.open(player, route())
    }

    internal fun route(): MenuRoute = MenuRoute(OWNER, ROUTE_ID)

    private fun render(player: Player): InventoryMenuView {
        val lang = plugin.languageManager
        val elements = mutableListOf<MenuElement>()
        elements += actionElement(19, createActionItem(player,
            Material.COMMAND_BLOCK,
            lang.getMessage(player, "gui.admin_menu.update_data.display"),
            textLore(player, "gui.admin_menu.update_data.lore"),
            lang.getMessage(player, "gui.admin_menu.update_data.action"),
            ItemTag.TYPE_GUI_ADMIN_UPDATE_DATA
        ), ACTION_UPDATE_DATA)

        elements += actionElement(20, createActionItem(player,
            Material.ANVIL,
            lang.getMessage(player, "gui.admin_menu.repair_templates.display"),
            textLore(player, "gui.admin_menu.repair_templates.lore"),
            lang.getMessage(player, "gui.admin_menu.repair_templates.action"),
            ItemTag.TYPE_GUI_ADMIN_REPAIR_TEMPLATES
        ), ACTION_REPAIR_TEMPLATES)

        elements += actionElement(21, createActionItem(player,
            Material.CRAFTING_TABLE,
            lang.getMessage(player, "gui.admin_menu.create_template.display"),
            textLore(player, "gui.admin_menu.create_template.lore"),
            lang.getMessage(player, "gui.admin_menu.create_template.action"),
            ItemTag.TYPE_GUI_ADMIN_CREATE_TEMPLATE
        ), ACTION_CREATE_TEMPLATE)

        elements += actionElement(23, createActionItem(player,
            Material.CHEST,
            lang.getMessage(player, "gui.admin_menu.archive.display"),
            textLore(player, "gui.admin_menu.archive.lore"),
            lang.getMessage(player, "gui.admin_menu.archive.action"),
            ItemTag.TYPE_GUI_ADMIN_ARCHIVE_ALL
        ), ACTION_ARCHIVE_ALL)

        val currentWorld = player.world
        val isMyWorld = currentWorld.name.startsWith("my_world.") || plugin.worldConfigRepository.findAll().any { it.customWorldName == currentWorld.name }

        if (isMyWorld) {
            elements += actionElement(24, createActionItem(player,
                Material.LEAD,
                lang.getMessage(player, "gui.admin_menu.unlink.display"),
                textLore(player, "gui.admin_menu.unlink.lore"),
                lang.getMessage(player, "gui.admin_menu.unlink.action"),
                ItemTag.TYPE_GUI_ADMIN_UNLINK
            ), ACTION_UNLINK)
        } else {
            elements += actionElement(24, createDualActionItem(player,
                Material.WRITABLE_BOOK,
                lang.getMessage(player, "gui.admin_menu.convert.display"),
                textLore(player, "gui.admin_menu.convert.lore"),
                lang.getMessage(player, "gui.admin_menu.convert.action.normal"),
                lang.getMessage(player, "gui.admin_menu.convert.action.admin"),
                ItemTag.TYPE_GUI_ADMIN_CONVERT
            ), ACTION_CONVERT)
        }

        if (isMyWorld) {
            elements += actionElement(25, createActionItem(player,
                Material.DISPENSER,
                lang.getMessage(player, "gui.admin_menu.export.display"),
                textLore(player, "gui.admin_menu.export.lore"),
                lang.getMessage(player, "gui.admin_menu.export.action"),
                ItemTag.TYPE_GUI_ADMIN_EXPORT
            ), ACTION_EXPORT)
        } else {
            val loreLines = mutableListOf<GuiLoreLine>()
            lang.getMessageList(player, "gui.admin_menu.export.lore").forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) loreLines.add(GuiLoreLine.Spacer) else loreLines.add(GuiLoreLine.Text(trimmed))
            }
            loreLines.add(GuiLoreLine.Spacer)
            loreLines.add(GuiLoreLine.Warning(lang.getMessage(player, "gui.admin_menu.export.unavailable_warning")))
            elements += CCSystem.getAPI().getGuiElementService().menuDisplay(
                GuiMenuDisplaySpec(
                    25,
                    GuiItemSpec(
                        Material.BARRIER,
                        GuiNameSpec.Text(
                            lang.getMessage(player, "gui.admin_menu.export.display"),
                            com.awabi2048.ccsystem.api.gui.GuiNameStyle.DEFAULT,
                        ),
                        GuiLoreSpec.Blocks(
                            listOf(
                                GuiLoreBlock(loreLines.filterIsInstance<GuiLoreLine.Text>()),
                                GuiLoreBlock(listOf(GuiLoreLine.Warning(lang.getMessage(player, "gui.admin_menu.export.unavailable_warning")))),
                            ),
                        ),
                        GuiElementRole.CONTENT,
                        1,
                    ),
                ),
            )
        }

        val worldListAction = resolveCapability(
            player,
            AdminMenuCapabilityPlacements.WORLD_LIST_ACTION,
        )
        elements += if (worldListAction != null) {
            capabilityElement(player, 38, worldListAction)
        } else {
            actionElement(38, createActionItem(player,
                Material.FILLED_MAP,
                lang.getMessage(player, "gui.admin_menu.info.display"),
                textLore(player, "gui.admin_menu.info.lore"),
                lang.getMessage(player, "gui.admin_menu.info.action"),
                ItemTag.TYPE_GUI_ADMIN_INFO
            ), ACTION_INFO)
        }

        val menuSwitch = resolveCapability(
            player,
            AdminMenuCapabilityPlacements.MENU_SWITCH,
        )
        if (menuSwitch != null) {
            elements += capabilityElement(player, 40, menuSwitch, GuiElementRole.NAVIGATION)
        } else {
            elements += CCSystem.getAPI().getGuiElementService().menuDisplay(
                GuiMenuDisplaySpec(
                    40,
                    GuiItemSpec(
                        Material.NETHER_STAR,
                        GuiNameSpec.Text(
                            lang.getMessage(player, "gui.admin_menu.plugin_info.display"),
                            com.awabi2048.ccsystem.api.gui.GuiNameStyle.DEFAULT,
                        ),
                        GuiLoreSpec.Blocks(
                            listOf(
                                GuiLoreBlock(
                                    textLore(
                                        player,
                                        "gui.admin_menu.plugin_info.lore",
                                        mapOf("version" to plugin.pluginMeta.version, "author" to "awabi2048"),
                                    ),
                                ),
                            ),
                        ),
                        GuiElementRole.CONTENT,
                        1,
                    ),
                ),
            )
        }

        elements += actionElement(42, createActionItem(player,
            Material.END_PORTAL_FRAME,
            lang.getMessage(player, "gui.admin_menu.portals.display"),
            textLore(player, "gui.admin_menu.portals.lore"),
            lang.getMessage(player, "gui.admin_menu.portals.action"),
            ItemTag.TYPE_GUI_ADMIN_PORTALS
        ), ACTION_PORTALS)

        return InventoryMenuView(
            size = 45,
            title = GuiHelper.inventoryTitle(lang.getComponent(player, titleKey)),
            elements = elements,
        )
    }

    private fun actionElement(slot: Int, item: AdminActionItemSpec, actionId: String): MenuElement =
        CCSystem.getAPI().getGuiElementService().menuEntry(
            item.player,
            GuiMenuEntrySpec(
                slot = slot,
                material = item.material,
                name = GuiNameSpec.Text(item.name, com.awabi2048.ccsystem.api.gui.GuiNameStyle.DEFAULT),
                role = GuiElementRole.ACTION,
                description = item.description,
                actions = if (item.rightAction == null) {
                    listOf(menuGestureAction(actionId, MenuGesture.ANY, item.leftAction, safety = actionSafety(actionId)))
                } else {
                    listOf(
                        menuGestureAction(actionId, MenuGesture.PLAIN_LEFT, item.leftAction, safety = actionSafety(actionId)),
                        menuGestureAction(actionId, MenuGesture.PLAIN_RIGHT, item.rightAction, safety = actionSafety(actionId)),
                    )
                },
            ),
        )

    private fun resolveCapability(
        player: Player,
        placement: String,
    ) = CCSystem.getAPI().getMenuCapabilityService()
        .definitions(placement)
        .asSequence()
        .mapNotNull { definition ->
            CCSystem.getAPI().getMenuCapabilityService()
                .resolve(definition.capabilityId, player)
        }
        .firstOrNull()
        ?.requireExplicitActionSafety()

    private fun capabilityElement(
        player: Player,
        slot: Int,
        capability: com.awabi2048.ccsystem.api.gui.ResolvedMenuCapability,
        role: GuiElementRole = GuiElementRole.ACTION,
    ): MenuElement {
        val presentation = capability.presentation
        return CCSystem.getAPI().getGuiElementService().menuCapabilityEntry(
            player,
            GuiMenuCapabilityInvocationSpec(
                slot = slot,
                capability = capability.requireExplicitActionSafety().copy(
                    presentation = presentation.copy(
                        item = presentation.item.copy(role = role),
                    ),
                ),
            ),
        )
    }

    private fun createTemplate(context: MenuActionContext): MenuActionResult {
        return MenuActionResult.Success(
            MenuUpdate.Navigate(plugin.templateWizardGui.prepareOpen(context.player)),
        )
    }

    private fun convert(context: MenuActionContext): MenuActionResult {
        val mode = when (context.click) {
            ClickType.LEFT, ClickType.SHIFT_LEFT -> WorldService.ConversionMode.NORMAL
            ClickType.RIGHT, ClickType.SHIFT_RIGHT -> WorldService.ConversionMode.ADMIN
            else -> return MenuActionResult.Ignored
        }
        return MenuActionResult.Success(
            MenuUpdate.Navigate(prepareConvertConfirmation(context.player, mode)),
        )
    }

    private fun openWorldList(context: MenuActionContext): MenuActionResult {
        return MenuActionResult.Success(
            MenuUpdate.Navigate(plugin.worldGui.prepareOpen(context.player, fromAdminMenu = true)),
        )
    }

    private fun openPortals(context: MenuActionContext): MenuActionResult {
        return MenuActionResult.Success(
            MenuUpdate.Navigate(
                plugin.adminPortalGui.prepareOpen(context.player, fromAdminMenu = true),
            ),
        )
    }

    private fun confirm(context: MenuActionContext): MenuActionResult {
        plugin.adminCommandListener.executeCurrentConfirmation(context.player, plugin)
        return MenuActionResult.Success(MenuUpdate.Close)
    }

    private fun cancel(context: MenuActionContext): MenuActionResult {
        plugin.settingsSessionManager.updateSessionAction(
            context.player,
            context.player.uniqueId,
            SettingsAction.ADMIN_MENU,
            isGui = true,
        )
        return MenuActionResult.Success(MenuUpdate.Replace(MenuRoute(OWNER, ROUTE_ID)))
    }

    // --- 確認画面 ---

    fun openConvertConfirmation(player: Player, mode: WorldService.ConversionMode) {
        runtime.navigate(player, prepareConvertConfirmation(player, mode))
    }

    private fun prepareConvertConfirmation(player: Player, mode: WorldService.ConversionMode): MenuRoute {
        val lang = plugin.languageManager
        val action = if (mode == WorldService.ConversionMode.NORMAL) SettingsAction.ADMIN_CONVERT_NORMAL_CONFIRM else SettingsAction.ADMIN_CONVERT_ADMIN_CONFIRM
        val titleKey = if (mode == WorldService.ConversionMode.NORMAL) "gui.admin_menu.convert.confirm_normal" else "gui.admin_menu.convert.confirm_admin"
        val title = lang.getComponent(player, titleKey)
        val confirmId = if (mode == WorldService.ConversionMode.NORMAL) {
            "mwm:confirm/admin/convert_normal"
        } else {
            "mwm:confirm/admin/convert_admin"
        }
        return prepareConfirmation(player, player.uniqueId, action, title, confirmId)
    }

    fun openUnlinkConfirmation(player: Player) {
        runtime.navigate(player, prepareUnlinkConfirmation(player))
    }

    private fun prepareUnlinkConfirmation(player: Player): MenuRoute {
        val lang = plugin.languageManager
        val title = lang.getComponent(player, "gui.admin_menu.unlink.confirm_title")
        return prepareConfirmation(
            player,
            player.uniqueId,
            SettingsAction.ADMIN_UNLINK_CONFIRM,
            title,
            "mwm:confirm/admin/unlink"
        )
    }

    fun openExportConfirmation(player: Player, worldName: String) {
        runtime.navigate(player, prepareExportConfirmation(player, worldName))
    }

    private fun prepareExportConfirmation(player: Player, worldName: String): MenuRoute {
        val lang = plugin.languageManager
        val title = lang.getComponent(player, "gui.admin_menu.export.confirm_title")
        val extraInfo = listOf(GuiLoreLine.Data(lang.getMessage(player, "gui.admin_menu.target_world_label"), worldName, "§b"))
        return prepareConfirmation(
            player,
            player.uniqueId,
            SettingsAction.ADMIN_EXPORT_CONFIRM,
            title,
            "mwm:confirm/admin/export",
            extraInfo
        )
    }

    fun openArchiveAllConfirmation(player: Player) {
        runtime.navigate(player, prepareArchiveAllConfirmation(player))
    }

    private fun prepareArchiveAllConfirmation(player: Player): MenuRoute {
        val lang = plugin.languageManager
        val title = lang.getComponent(player, "gui.admin_menu.archive.confirm_title")
        return prepareConfirmation(
            player,
            player.uniqueId,
            SettingsAction.ADMIN_ARCHIVE_ALL_CONFIRM,
            title,
            "mwm:confirm/admin/archive_all"
        )
    }

    fun openUpdateDataConfirmation(player: Player) {
        runtime.navigate(player, prepareUpdateDataConfirmation(player))
    }

    private fun prepareUpdateDataConfirmation(player: Player): MenuRoute {
        val lang = plugin.languageManager
        val title = lang.getComponent(player, "gui.admin_menu.update_data.confirm_title")
        return prepareConfirmation(
            player,
            player.uniqueId,
            SettingsAction.ADMIN_UPDATE_DATA_CONFIRM,
            title,
            "mwm:confirm/admin/update_data"
        )
    }

    fun openRepairTemplatesConfirmation(player: Player) {
        runtime.navigate(player, prepareRepairTemplatesConfirmation(player))
    }

    private fun prepareRepairTemplatesConfirmation(player: Player): MenuRoute {
        val lang = plugin.languageManager
        val title = lang.getComponent(player, "gui.admin_menu.repair_templates.confirm_title")
        return prepareConfirmation(
            player,
            player.uniqueId,
            SettingsAction.ADMIN_REPAIR_TEMPLATES_CONFIRM,
            title,
            "mwm:confirm/admin/repair_templates"
        )
    }

    fun openArchiveWorldConfirmation(player: Player, worldName: String, worldUuid: UUID) {
        val lang = plugin.languageManager
        val title = lang.getComponent(player, "gui.admin_menu.archive_world.confirm_title")
        val extraInfo = listOf(GuiLoreLine.Data(lang.getMessage(player, "gui.admin_menu.target_world_label"), worldName, "§b"))
        runtime.navigate(player, prepareConfirmation(
            player,
            worldUuid,
            SettingsAction.ADMIN_ARCHIVE_WORLD_CONFIRM,
            title,
            "mwm:confirm/admin/archive_world/$worldUuid",
            extraInfo
        ))
    }

    fun openUnarchiveWorldConfirmation(player: Player, worldName: String, worldUuid: UUID) {
        val lang = plugin.languageManager
        val title = lang.getComponent(player, "gui.admin_menu.unarchive_world.confirm_title")
        val extraInfo = listOf(GuiLoreLine.Data(lang.getMessage(player, "gui.admin_menu.target_world_label"), worldName, "§b"))
        runtime.navigate(player, prepareConfirmation(
            player,
            worldUuid,
            SettingsAction.ADMIN_UNARCHIVE_WORLD_CONFIRM,
            title,
            "mwm:confirm/admin/unarchive_world/$worldUuid",
            extraInfo
        ))
    }

    private fun prepareConfirmation(
        player: Player,
        worldUuid: UUID,
        action: SettingsAction,
        @Suppress("UNUSED_PARAMETER")
        title: Component,
        @Suppress("UNUSED_PARAMETER")
        confirmActionId: String,
        @Suppress("UNUSED_PARAMETER")
        extraInfo: List<GuiLoreLine> = emptyList(),
    ): MenuRoute {
        plugin.settingsSessionManager.updateSessionAction(player, worldUuid, action, isGui = true)
        return MenuRoute(OWNER, CONFIRM_ROUTE_ID)
    }

    private fun renderConfirmation(player: Player): InventoryMenuView {
        val session = plugin.settingsSessionManager.getSession(player)
            ?: error("管理確認画面のセッションがありません")
        val action = session.action
        val titleKey = when (action) {
            SettingsAction.ADMIN_CONVERT_NORMAL_CONFIRM -> "gui.admin_menu.convert.confirm_normal"
            SettingsAction.ADMIN_CONVERT_ADMIN_CONFIRM -> "gui.admin_menu.convert.confirm_admin"
            SettingsAction.ADMIN_EXPORT_CONFIRM -> "gui.admin_menu.export.confirm_title"
            SettingsAction.ADMIN_ARCHIVE_ALL_CONFIRM -> "gui.admin_menu.archive.confirm_title"
            SettingsAction.ADMIN_UPDATE_DATA_CONFIRM -> "gui.admin_menu.update_data.confirm_title"
            SettingsAction.ADMIN_UNLINK_CONFIRM -> "gui.admin_menu.unlink.confirm_title"
            SettingsAction.ADMIN_REPAIR_TEMPLATES_CONFIRM -> "gui.admin_menu.repair_templates.confirm_title"
            SettingsAction.ADMIN_ARCHIVE_WORLD_CONFIRM -> "gui.admin_menu.archive_world.confirm_title"
            SettingsAction.ADMIN_UNARCHIVE_WORLD_CONFIRM -> "gui.admin_menu.unarchive_world.confirm_title"
            else -> error("管理確認画面ではないアクションです: $action")
        }
        val extraInfo = when (action) {
            SettingsAction.ADMIN_EXPORT_CONFIRM -> listOf(
                GuiLoreLine.Data(
                    plugin.languageManager.getMessage(player, "gui.admin_menu.target_world_label"),
                    player.world.name,
                    "§b",
                ),
            )
            SettingsAction.ADMIN_ARCHIVE_WORLD_CONFIRM,
            SettingsAction.ADMIN_UNARCHIVE_WORLD_CONFIRM -> {
                val worldName = plugin.worldConfigRepository.findByUuid(session.worldUuid)?.name
                    ?: session.worldUuid.toString()
                listOf(
                    GuiLoreLine.Data(
                        plugin.languageManager.getMessage(player, "gui.admin_menu.target_world_label"),
                        worldName,
                        "§b",
                    ),
                )
            }
            else -> emptyList()
        }
        val isDanger = action != SettingsAction.ADMIN_CONVERT_ADMIN_CONFIRM
        val lang = plugin.languageManager
        val infoLore: MutableList<GuiLoreLine> = extraInfo.toMutableList()
        infoLore.add(GuiLoreLine.Spacer)
        infoLore.add(GuiLoreLine.Warning(lang.getMessage(player, "gui.common.confirm_warning")))

        val layout = GuiHelper.confirmationLayout()
        return InventoryMenuView(
            size = layout.size,
            title = GuiHelper.inventoryTitle(lang.getComponent(player, titleKey)),
            elements = listOf(
                CCSystem.getAPI().getGuiElementService().menuDisplay(
                    GuiMenuDisplaySpec(
                        layout.previewSlot,
                        GuiItemSpec(
                            Material.PAPER,
                            GuiNameSpec.Text(
                                lang.getMessage(player, "gui.common.confirmation"),
                                com.awabi2048.ccsystem.api.gui.GuiNameStyle.DEFAULT,
                            ),
                            GuiLoreSpec.Blocks(
                                buildList {
                                    val dataLines = infoLore.filterNot {
                                        it == GuiLoreLine.Spacer || it is GuiLoreLine.Warning
                                    }
                                    if (dataLines.isNotEmpty()) add(GuiLoreBlock(dataLines))
                                    add(GuiLoreBlock(listOf(GuiLoreLine.Warning(lang.getMessage(player, "gui.common.confirm_warning")))))
                                },
                            ),
                            GuiElementRole.CONTENT,
                            1,
                        ),
                    ),
                ),
                confirmationAction(
                    player,
                    layout.confirmSlot,
                    if (isDanger) Material.RED_WOOL else Material.LIME_WOOL,
                    "gui.common.confirm",
                    "gui.common.confirm_desc",
                    "gui.common.confirm_action",
                    GuiElementRole.CONFIRM,
                    ACTION_CONFIRM,
                ),
                confirmationAction(
                    player,
                    layout.cancelSlot,
                    Material.GREEN_WOOL,
                    "gui.common.cancel",
                    "gui.common.cancel_desc",
                    "gui.common.cancel_action",
                    GuiElementRole.CANCEL,
                    ACTION_CANCEL,
                ),
            ),
        )
    }

    private fun confirmationAction(
        player: Player,
        slot: Int,
        material: Material,
        nameKey: String,
        descriptionKey: String,
        actionKey: String,
        role: GuiElementRole,
        actionId: String,
    ): MenuElement = CCSystem.getAPI().getGuiElementService().menuEntry(
        player,
        GuiMenuEntrySpec(
            slot = slot,
            material = material,
            name = GuiNameSpec.Text(
                plugin.languageManager.getMessage(player, nameKey),
                com.awabi2048.ccsystem.api.gui.GuiNameStyle.DEFAULT,
            ),
            role = role,
            description = listOf(plugin.languageManager.getMessage(player, descriptionKey)),
            actions = listOf(
                menuGestureAction(
                    actionId,
                    MenuGesture.ANY,
                    plugin.languageManager.getMessage(player, actionKey),
                    safety = actionSafety(actionId),
                ),
            ),
        ),
    )

    private fun actionSafety(actionId: String): MenuActionSafety = when (actionId) {
        ACTION_UPDATE_DATA,
        ACTION_REPAIR_TEMPLATES,
        ACTION_ARCHIVE_ALL,
        ACTION_CONVERT,
        ACTION_UNLINK,
        ACTION_EXPORT -> MenuActionSafety.CONFIRM_ENTRY
        ACTION_CREATE_TEMPLATE,
        ACTION_INFO,
        ACTION_PORTALS -> MenuActionSafety.NAVIGATION_ONLY
        ACTION_CONFIRM -> MenuActionSafety.IRREVERSIBLE
        ACTION_CANCEL -> MenuActionSafety.NAVIGATION_ONLY
        else -> error("Unknown admin GUI action safety: $actionId")
    }

    private fun createActionItem(
        player: Player,
        material: Material,
        name: String,
        lore: List<GuiLoreLine>,
        action: String,
        @Suppress("UNUSED_PARAMETER") tagType: String,
    ): AdminActionItemSpec {
        return AdminActionItemSpec(
            player,
            material,
            name,
            lore.mapNotNull { (it as? GuiLoreLine.Text)?.text },
            action,
        )
    }

    private fun createDualActionItem(
        player: Player,
        material: Material,
        name: String,
        lore: List<GuiLoreLine>,
        leftAction: String,
        rightAction: String,
        tagType: String
    ): AdminActionItemSpec {
        return AdminActionItemSpec(
            player,
            material,
            name,
            lore.mapNotNull { (it as? GuiLoreLine.Text)?.text },
            leftAction,
            rightAction,
        )
    }

    private data class AdminActionItemSpec(
        val player: Player,
        val material: Material,
        val name: String,
        val description: List<String>,
        val leftAction: String,
        val rightAction: String? = null,
    )

    private fun textLore(player: Player, key: String, placeholders: Map<String, Any> = emptyMap()): List<GuiLoreLine> {
        return plugin.languageManager.getMessageList(player, key, placeholders).map(GuiLoreLine::Text)
    }

    companion object {
        private const val OWNER = "myworldmanager"
        private const val ROUTE_ID = "admin_command"
        private const val CONFIRM_ROUTE_ID = "admin_command_confirmation"
        private const val ACTION_CONFIRM = "confirm"
        private const val ACTION_CANCEL = "cancel"
        private const val ACTION_UPDATE_DATA = "update_data"
        private const val ACTION_REPAIR_TEMPLATES = "repair_templates"
        private const val ACTION_CREATE_TEMPLATE = "create_template"
        private const val ACTION_ARCHIVE_ALL = "archive_all"
        private const val ACTION_CONVERT = "convert"
        private const val ACTION_UNLINK = "unlink"
        private const val ACTION_EXPORT = "export"
        private const val ACTION_INFO = "info"
        private const val ACTION_PORTALS = "portals"
    }
}
