package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuActionContext
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuInteraction
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.extension.AdminMenuCapabilityPlacements
import me.awabi2048.myworldmanager.service.WorldService
import me.awabi2048.myworldmanager.session.SettingsAction
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.GuiItemFactory
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
                        openUpdateDataConfirmation(context.player)
                        MenuActionResult.Success(MenuUpdate.None)
                    },
                    ACTION_REPAIR_TEMPLATES to MenuActionHandler { context ->
                        openRepairTemplatesConfirmation(context.player)
                        MenuActionResult.Success(MenuUpdate.None)
                    },
                    ACTION_CREATE_TEMPLATE to MenuActionHandler(::createTemplate),
                    ACTION_ARCHIVE_ALL to MenuActionHandler { context ->
                        openArchiveAllConfirmation(context.player)
                        MenuActionResult.Success(MenuUpdate.None)
                    },
                    ACTION_CONVERT to MenuActionHandler(::convert),
                    ACTION_UNLINK to MenuActionHandler { context ->
                        openUnlinkConfirmation(context.player)
                        MenuActionResult.Success(MenuUpdate.None)
                    },
                    ACTION_EXPORT to MenuActionHandler { context ->
                        openExportConfirmation(context.player, context.player.world.name)
                        MenuActionResult.Success(MenuUpdate.None)
                    },
                    ACTION_INFO to MenuActionHandler(::openWorldList),
                    ACTION_CAPABILITY to MenuActionHandler(::executeCapability),
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
        runtime.open(player, MenuRoute(OWNER, ROUTE_ID))
    }

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
            elements += MenuElement(
                25,
                GuiItemFactory.item(
                    Material.BARRIER,
                    lang.getMessage(player, "gui.admin_menu.export.display"),
                    GuiLoreSpec.Rich(loreLines, GuiLoreFrame.BOTH),
                    ItemTag.TYPE_GUI_INFO
                ),
                GuiElementRole.CONTENT,
            )
        }

        val worldListAction = resolveCapability(
            player,
            AdminMenuCapabilityPlacements.WORLD_LIST_ACTION,
        )
        elements += if (worldListAction != null) {
            capabilityElement(38, worldListAction)
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
            elements += capabilityElement(40, menuSwitch, GuiElementRole.NAVIGATION)
        } else {
            elements += MenuElement(
                40,
                createItem(
                    Material.NETHER_STAR,
                    lang.getMessage(player, "gui.admin_menu.plugin_info.display"),
                    textLore(player, "gui.admin_menu.plugin_info.lore", mapOf("version" to plugin.pluginMeta.version, "author" to "awabi2048")),
                    ItemTag.TYPE_GUI_INFO
                ),
                GuiElementRole.CONTENT,
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

    private fun actionElement(slot: Int, item: ItemStack, actionId: String) =
        MenuElement(slot, item, GuiElementRole.ACTION, actionId)

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

    private fun capabilityElement(
        slot: Int,
        capability: com.awabi2048.ccsystem.api.gui.ResolvedMenuCapability,
        role: GuiElementRole = GuiElementRole.ACTION,
    ): MenuElement {
        val presentation = capability.presentation
        val item = CCSystem.getAPI().getGuiElementService()
            .item(presentation.item.copy(role = role))
        presentation.glint?.let { enabled ->
            item.editMeta { meta -> meta.setEnchantmentGlintOverride(enabled) }
        }
        return MenuElement(
            slot = slot,
            item = item,
            role = role,
            interaction = if (capability.actionable) {
                MenuInteraction.Action(
                    actionId = ACTION_CAPABILITY,
                    acceptedClicks = capability.acceptedClicks,
                    payload = mapOf(CAPABILITY_ID to capability.capabilityId),
                )
            } else {
                MenuInteraction.DisplayOnly
            },
        )
    }

    private fun createTemplate(context: MenuActionContext): MenuActionResult {
        plugin.templateWizardGui.open(context.player)
        return MenuActionResult.Success(MenuUpdate.None)
    }

    private fun convert(context: MenuActionContext): MenuActionResult {
        val mode = when (context.click) {
            ClickType.LEFT, ClickType.SHIFT_LEFT -> WorldService.ConversionMode.NORMAL
            ClickType.RIGHT, ClickType.SHIFT_RIGHT -> WorldService.ConversionMode.ADMIN
            else -> return MenuActionResult.Ignored
        }
        openConvertConfirmation(context.player, mode)
        return MenuActionResult.Success(MenuUpdate.None)
    }

    private fun openWorldList(context: MenuActionContext): MenuActionResult {
        plugin.worldGui.open(context.player, fromAdminMenu = true)
        return MenuActionResult.Success(MenuUpdate.None)
    }

    private fun executeCapability(context: MenuActionContext): MenuActionResult =
        context.payload[CAPABILITY_ID]
            ?.let {
                CCSystem.getAPI().getMenuCapabilityService()
                    .execute(it, context.player, context.click)
            }
            ?: MenuActionResult.Ignored

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
        val lang = plugin.languageManager
        val action = if (mode == WorldService.ConversionMode.NORMAL) SettingsAction.ADMIN_CONVERT_NORMAL_CONFIRM else SettingsAction.ADMIN_CONVERT_ADMIN_CONFIRM
        val titleKey = if (mode == WorldService.ConversionMode.NORMAL) "gui.admin_menu.convert.confirm_normal" else "gui.admin_menu.convert.confirm_admin"
        val title = lang.getComponent(player, titleKey)
        val confirmId = if (mode == WorldService.ConversionMode.NORMAL) {
            "mwm:confirm/admin/convert_normal"
        } else {
            "mwm:confirm/admin/convert_admin"
        }
        showDialogOrGuiConfirmation(player, player.uniqueId, action, title, confirmId) {}
    }

    fun openUnlinkConfirmation(player: Player) {
        val lang = plugin.languageManager
        val title = lang.getComponent(player, "gui.admin_menu.unlink.confirm_title")
        showDialogOrGuiConfirmation(
            player,
            player.uniqueId,
            SettingsAction.ADMIN_UNLINK_CONFIRM,
            title,
            "mwm:confirm/admin/unlink"
        ) {}
    }

    fun openExportConfirmation(player: Player, worldName: String) {
        val lang = plugin.languageManager
        val title = lang.getComponent(player, "gui.admin_menu.export.confirm_title")
        val extraInfo = listOf(GuiLoreLine.Data(lang.getMessage(player, "gui.admin_menu.target_world_label"), worldName, "§b"))
        showDialogOrGuiConfirmation(
            player,
            player.uniqueId,
            SettingsAction.ADMIN_EXPORT_CONFIRM,
            title,
            "mwm:confirm/admin/export",
            extraInfo
        ) {}
    }

    fun openArchiveAllConfirmation(player: Player) {
        val lang = plugin.languageManager
        val title = lang.getComponent(player, "gui.admin_menu.archive.confirm_title")
        showDialogOrGuiConfirmation(
            player,
            player.uniqueId,
            SettingsAction.ADMIN_ARCHIVE_ALL_CONFIRM,
            title,
            "mwm:confirm/admin/archive_all"
        ) {}
    }

    fun openUpdateDataConfirmation(player: Player) {
        val lang = plugin.languageManager
        val title = lang.getComponent(player, "gui.admin_menu.update_data.confirm_title")
        showDialogOrGuiConfirmation(
            player,
            player.uniqueId,
            SettingsAction.ADMIN_UPDATE_DATA_CONFIRM,
            title,
            "mwm:confirm/admin/update_data"
        ) {}
    }

    fun openRepairTemplatesConfirmation(player: Player) {
        val lang = plugin.languageManager
        val title = lang.getComponent(player, "gui.admin_menu.repair_templates.confirm_title")
        showDialogOrGuiConfirmation(
            player,
            player.uniqueId,
            SettingsAction.ADMIN_REPAIR_TEMPLATES_CONFIRM,
            title,
            "mwm:confirm/admin/repair_templates"
        ) {}
    }

    fun openArchiveWorldConfirmation(player: Player, worldName: String, worldUuid: UUID) {
        val lang = plugin.languageManager
        val title = lang.getComponent(player, "gui.admin_menu.archive_world.confirm_title")
        val extraInfo = listOf(GuiLoreLine.Data(lang.getMessage(player, "gui.admin_menu.target_world_label"), worldName, "§b"))
        showDialogOrGuiConfirmation(
            player,
            worldUuid,
            SettingsAction.ADMIN_ARCHIVE_WORLD_CONFIRM,
            title,
            "mwm:confirm/admin/archive_world/$worldUuid",
            extraInfo
        ) {}
    }

    fun openUnarchiveWorldConfirmation(player: Player, worldName: String, worldUuid: UUID) {
        val lang = plugin.languageManager
        val title = lang.getComponent(player, "gui.admin_menu.unarchive_world.confirm_title")
        val extraInfo = listOf(GuiLoreLine.Data(lang.getMessage(player, "gui.admin_menu.target_world_label"), worldName, "§b"))
        showDialogOrGuiConfirmation(
            player,
            worldUuid,
            SettingsAction.ADMIN_UNARCHIVE_WORLD_CONFIRM,
            title,
            "mwm:confirm/admin/unarchive_world/$worldUuid",
            extraInfo
        ) {}
    }

    private fun showDialogOrGuiConfirmation(
        player: Player,
        worldUuid: UUID,
        action: SettingsAction,
        title: Component,
        confirmActionId: String,
        extraInfo: List<GuiLoreLine> = emptyList(),
        @Suppress("UNUSED_PARAMETER") onGuiFallback: () -> Unit
    ) {
        plugin.settingsSessionManager.updateSessionAction(player, worldUuid, action, isGui = true)
        runtime.navigate(player, MenuRoute(OWNER, CONFIRM_ROUTE_ID))
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

        val infoItem = createItem(
            Material.PAPER,
            lang.getMessage(player, "gui.common.confirmation"),
            infoLore,
            ItemTag.TYPE_GUI_INFO
        )

        // 実行ボタン
        val confirmItem = createItem(
            if (isDanger) Material.RED_WOOL else Material.LIME_WOOL,
            lang.getMessage(player, "gui.common.confirm"),
            listOf(GuiLoreLine.Text(lang.getMessage(player, "gui.common.confirm_desc"))),
            ItemTag.TYPE_GUI_CONFIRM
        )

        // キャンセルボタン
        val cancelItem = createItem(
            Material.GREEN_WOOL, // キャンセルは安全な色で
            lang.getMessage(player, "gui.common.cancel"),
            listOf(GuiLoreLine.Text(lang.getMessage(player, "gui.common.cancel_desc"))),
            ItemTag.TYPE_GUI_CANCEL
        )
        val layout = GuiHelper.confirmationLayout()
        return InventoryMenuView(
            size = layout.size,
            title = GuiHelper.inventoryTitle(lang.getComponent(player, titleKey)),
            elements = listOf(
                MenuElement(layout.previewSlot, infoItem, GuiElementRole.CONTENT),
                MenuElement(layout.confirmSlot, confirmItem, GuiElementRole.CONFIRM, ACTION_CONFIRM),
                MenuElement(layout.cancelSlot, cancelItem, GuiElementRole.CANCEL, ACTION_CANCEL),
            ),
        )
    }

    private fun createItem(material: Material, name: String, lore: List<GuiLoreLine>, tagType: String): ItemStack {
        return GuiItemFactory.item(
            material,
            name,
            GuiLoreSpec.Rich(lore, GuiLoreFrame.BOTH),
            tagType
        )
    }

    private fun createActionItem(player: Player, material: Material, name: String, lore: List<GuiLoreLine>, action: String, tagType: String): ItemStack {
        return GuiItemFactory.item(
            material,
            name,
            GuiLoreSpec.Blocks(listOf(
                com.awabi2048.ccsystem.api.gui.GuiLoreBlock(lore),
                com.awabi2048.ccsystem.api.gui.GuiLoreBlock(listOf(me.awabi2048.myworldmanager.util.GuiLoreActions.singleClick(plugin.languageManager, player, action)))
            )),
            tagType
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
    ): ItemStack {
        return GuiItemFactory.item(
            material,
            name,
            GuiLoreSpec.Blocks(listOf(
                com.awabi2048.ccsystem.api.gui.GuiLoreBlock(lore),
                com.awabi2048.ccsystem.api.gui.GuiLoreBlock(listOf(
                    GuiLoreLine.Action(plugin.languageManager.getMessage(player, "lore.click.left"), leftAction),
                    GuiLoreLine.Action(plugin.languageManager.getMessage(player, "lore.click.right"), rightAction)
                ))
            )),
            tagType
        )
    }

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
        private const val ACTION_CAPABILITY = "capability"
        private const val CAPABILITY_ID = "capability_id"
        private const val ACTION_PORTALS = "portals"
    }
}
