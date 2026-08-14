package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.api.localization.generated.CommonKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldGuiAdminKeys

import me.awabi2048.myworldmanager.util.descriptionLine
import me.awabi2048.myworldmanager.util.warningLine
import me.awabi2048.myworldmanager.util.dangerLine

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
import com.awabi2048.ccsystem.api.gui.copyPreservingResolutionMetadata
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
import com.awabi2048.ccsystem.api.gui.MenuSoundPresets
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import com.awabi2048.ccsystem.api.gui.MenuViewCategory
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.extension.AdminMenuCapabilityPlacements
import me.awabi2048.myworldmanager.service.WorldService
import me.awabi2048.myworldmanager.session.SettingsAction
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.ItemTag
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
                            MenuUpdate.Navigate(prepareUpdateDataConfirmation()),
                        )
                    },
                    ACTION_REPAIR_TEMPLATES to MenuActionHandler { context ->
                        MenuActionResult.Success(
                            MenuUpdate.Navigate(prepareRepairTemplatesConfirmation()),
                        )
                    },
                    ACTION_CREATE_TEMPLATE to MenuActionHandler(::createTemplate),
                    ACTION_ARCHIVE_ALL to MenuActionHandler { context ->
                        MenuActionResult.Success(
                            MenuUpdate.Navigate(prepareArchiveAllConfirmation()),
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
                                prepareExportConfirmation(context.player.world.name),
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
                renderer = { context -> renderConfirmation(context.player, context.route) },
                actions = mapOf(
                    ACTION_CONFIRM to MenuActionHandler(::confirm),
                    ACTION_CANCEL to MenuActionHandler(::cancel),
                ),
                openSound = MenuSoundPresets.CONFIRMATION_OPEN,
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
            lang.getMessage(player, MyworldGuiAdminKeys.GUI_ADMIN_MENU_UPDATE_DATA_DISPLAY),
            textLore(player, "gui.admin_menu.update_data.lore"),
            lang.getMessage(player, MyworldGuiAdminKeys.GUI_ADMIN_MENU_UPDATE_DATA_ACTION),
            ItemTag.TYPE_GUI_ADMIN_UPDATE_DATA
        ), ACTION_UPDATE_DATA)

        elements += actionElement(20, createActionItem(player,
            Material.ANVIL,
            lang.getMessage(player, MyworldGuiAdminKeys.GUI_ADMIN_MENU_REPAIR_TEMPLATES_DISPLAY),
            textLore(player, "gui.admin_menu.repair_templates.lore"),
            lang.getMessage(player, MyworldGuiAdminKeys.GUI_ADMIN_MENU_REPAIR_TEMPLATES_ACTION),
            ItemTag.TYPE_GUI_ADMIN_REPAIR_TEMPLATES
        ), ACTION_REPAIR_TEMPLATES)

        elements += actionElement(21, createActionItem(player,
            Material.CRAFTING_TABLE,
            lang.getMessage(player, MyworldGuiAdminKeys.GUI_ADMIN_MENU_CREATE_TEMPLATE_DISPLAY),
            textLore(player, "gui.admin_menu.create_template.lore"),
            lang.getMessage(player, MyworldGuiAdminKeys.GUI_ADMIN_MENU_CREATE_TEMPLATE_ACTION),
            ItemTag.TYPE_GUI_ADMIN_CREATE_TEMPLATE
        ), ACTION_CREATE_TEMPLATE)

        elements += actionElement(23, createActionItem(player,
            Material.CHEST,
            lang.getMessage(player, MyworldGuiAdminKeys.GUI_ADMIN_MENU_ARCHIVE_DISPLAY),
            textLore(player, "gui.admin_menu.archive.lore"),
            lang.getMessage(player, MyworldGuiAdminKeys.GUI_ADMIN_MENU_ARCHIVE_ACTION),
            ItemTag.TYPE_GUI_ADMIN_ARCHIVE_ALL
        ), ACTION_ARCHIVE_ALL)

        val currentWorld = player.world
        val isMyWorld = currentWorld.name.startsWith("my_world.") || plugin.worldConfigRepository.findAll().any { it.customWorldName == currentWorld.name }

        if (isMyWorld) {
            elements += actionElement(24, createActionItem(player,
                Material.LEAD,
                lang.getMessage(player, MyworldGuiAdminKeys.GUI_ADMIN_MENU_UNLINK_DISPLAY),
                textLore(player, "gui.admin_menu.unlink.lore"),
                lang.getMessage(player, MyworldGuiAdminKeys.GUI_ADMIN_MENU_UNLINK_ACTION),
                ItemTag.TYPE_GUI_ADMIN_UNLINK
            ), ACTION_UNLINK)
        } else {
            elements += actionElement(24, createDualActionItem(player,
                Material.WRITABLE_BOOK,
                lang.getMessage(player, MyworldGuiAdminKeys.GUI_ADMIN_MENU_CONVERT_DISPLAY),
                textLore(player, "gui.admin_menu.convert.lore"),
                lang.getMessage(player, MyworldGuiAdminKeys.GUI_ADMIN_MENU_CONVERT_ACTION_NORMAL),
                lang.getMessage(player, MyworldGuiAdminKeys.GUI_ADMIN_MENU_CONVERT_ACTION_ADMIN),
                ItemTag.TYPE_GUI_ADMIN_CONVERT
            ), ACTION_CONVERT)
        }

        if (isMyWorld) {
            elements += actionElement(25, createActionItem(player,
                Material.DISPENSER,
                lang.getMessage(player, MyworldGuiAdminKeys.GUI_ADMIN_MENU_EXPORT_DISPLAY),
                textLore(player, "gui.admin_menu.export.lore"),
                lang.getMessage(player, MyworldGuiAdminKeys.GUI_ADMIN_MENU_EXPORT_ACTION),
                ItemTag.TYPE_GUI_ADMIN_EXPORT
            ), ACTION_EXPORT)
        } else {
            val loreLines = mutableListOf<GuiLoreLine>()
            lang.getMessageList(player, MyworldGuiAdminKeys.GUI_ADMIN_MENU_EXPORT_LORE).forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) loreLines.add(GuiLoreLine.Spacer) else loreLines.add(GuiLoreLine.Text(trimmed))
            }
            loreLines.add(GuiLoreLine.Spacer)
            loreLines.add(GuiLoreLine.Warning(lang.getMessage(player, MyworldGuiAdminKeys.GUI_ADMIN_MENU_EXPORT_UNAVAILABLE_WARNING)))
            elements += CCSystem.getAPI().getGuiElementService().menuDisplay(
                GuiMenuDisplaySpec(
                    25,
                    GuiItemSpec(
                        Material.BARRIER,
                        me.awabi2048.myworldmanager.util.fixedLabelName(
                            lang.getMessage(player, MyworldGuiAdminKeys.GUI_ADMIN_MENU_EXPORT_DISPLAY),
                            com.awabi2048.ccsystem.api.gui.GuiNameStyle.DEFAULT,
                        ),
                        GuiLoreSpec.Blocks(
                            listOf(
                                GuiLoreBlock(loreLines.filterIsInstance<GuiLoreLine.Text>()),
                                GuiLoreBlock(listOf(GuiLoreLine.Warning(lang.getMessage(player, MyworldGuiAdminKeys.GUI_ADMIN_MENU_EXPORT_UNAVAILABLE_WARNING)))),
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
                lang.getMessage(player, MyworldGuiAdminKeys.GUI_ADMIN_MENU_INFO_DISPLAY),
                textLore(player, "gui.admin_menu.info.lore"),
                lang.getMessage(player, MyworldGuiAdminKeys.GUI_ADMIN_MENU_INFO_ACTION),
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
                        me.awabi2048.myworldmanager.util.fixedLabelName(
                            lang.getMessage(player, MyworldGuiAdminKeys.GUI_ADMIN_MENU_PLUGIN_INFO_DISPLAY),
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
            lang.getMessage(player, MyworldGuiAdminKeys.GUI_ADMIN_MENU_PORTALS_DISPLAY),
            textLore(player, "gui.admin_menu.portals.lore"),
            lang.getMessage(player, MyworldGuiAdminKeys.GUI_ADMIN_MENU_PORTALS_ACTION),
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
                name = me.awabi2048.myworldmanager.util.fixedLabelName(item.name, com.awabi2048.ccsystem.api.gui.GuiNameStyle.DEFAULT),
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
                capability = capability.requireExplicitActionSafety().copyPreservingResolutionMetadata(
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
        val confirmation = confirmationFrom(context.route)
        plugin.adminCommandListener.executeConfirmation(
            context.player,
            plugin,
            confirmation.action,
            confirmation.targetWorldUuid,
            confirmation.targetWorldName,
        )
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
        val action = if (mode == WorldService.ConversionMode.NORMAL) SettingsAction.ADMIN_CONVERT_NORMAL_CONFIRM else SettingsAction.ADMIN_CONVERT_ADMIN_CONFIRM
        return prepareConfirmation(action, player.world.uid, player.world.name)
    }

    fun openUnlinkConfirmation(player: Player) {
        runtime.navigate(player, prepareUnlinkConfirmation(player))
    }

    private fun prepareUnlinkConfirmation(player: Player): MenuRoute {
        return prepareConfirmation(SettingsAction.ADMIN_UNLINK_CONFIRM, targetWorldName = player.world.name)
    }

    fun openExportConfirmation(player: Player, worldName: String) {
        runtime.navigate(player, prepareExportConfirmation(worldName))
    }

    private fun prepareExportConfirmation(worldName: String): MenuRoute {
        return prepareConfirmation(SettingsAction.ADMIN_EXPORT_CONFIRM, targetWorldName = worldName)
    }

    fun openArchiveAllConfirmation(player: Player) {
        runtime.navigate(player, prepareArchiveAllConfirmation())
    }

    private fun prepareArchiveAllConfirmation(): MenuRoute {
        return prepareConfirmation(SettingsAction.ADMIN_ARCHIVE_ALL_CONFIRM)
    }

    fun openUpdateDataConfirmation(player: Player) {
        runtime.navigate(player, prepareUpdateDataConfirmation())
    }

    private fun prepareUpdateDataConfirmation(): MenuRoute {
        return prepareConfirmation(SettingsAction.ADMIN_UPDATE_DATA_CONFIRM)
    }

    fun openRepairTemplatesConfirmation(player: Player) {
        runtime.navigate(player, prepareRepairTemplatesConfirmation())
    }

    private fun prepareRepairTemplatesConfirmation(): MenuRoute {
        return prepareConfirmation(SettingsAction.ADMIN_REPAIR_TEMPLATES_CONFIRM)
    }

    fun openArchiveWorldConfirmation(player: Player, worldName: String, worldUuid: UUID) {
        runtime.navigate(
            player,
            prepareConfirmation(SettingsAction.ADMIN_ARCHIVE_WORLD_CONFIRM, worldUuid, worldName),
        )
    }

    fun openUnarchiveWorldConfirmation(player: Player, worldName: String, worldUuid: UUID) {
        runtime.navigate(
            player,
            prepareConfirmation(SettingsAction.ADMIN_UNARCHIVE_WORLD_CONFIRM, worldUuid, worldName),
        )
    }

    private fun prepareConfirmation(
        action: SettingsAction,
        targetWorldUuid: UUID? = null,
        targetWorldName: String? = null,
    ): MenuRoute {
        // 確認画面の正データをルートへ固定し、別画面によるセッション更新や
        // プレイヤーの移動が描画対象・実行対象を変えないようにします。
        val payload = buildMap {
            put(CONFIRM_ACTION_PAYLOAD, action.name)
            targetWorldUuid?.let { put(CONFIRM_WORLD_UUID_PAYLOAD, it.toString()) }
            targetWorldName?.let { put(CONFIRM_WORLD_NAME_PAYLOAD, it) }
        }
        return MenuRoute(OWNER, CONFIRM_ROUTE_ID, payload)
    }

    private fun renderConfirmation(player: Player, route: MenuRoute): InventoryMenuView {
        val confirmation = confirmationFrom(route)
        val action = confirmation.action
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
                    plugin.languageManager.getMessage(player, MyworldGuiAdminKeys.GUI_ADMIN_MENU_TARGET_WORLD_LABEL),
                    requireNotNull(confirmation.targetWorldName),
                    "§b",
                ),
            )
            SettingsAction.ADMIN_ARCHIVE_WORLD_CONFIRM,
            SettingsAction.ADMIN_UNARCHIVE_WORLD_CONFIRM -> {
                val worldName = confirmation.targetWorldName
                    ?: confirmation.targetWorldUuid?.let(plugin.worldConfigRepository::findByUuid)?.name
                    ?: error("管理確認画面の対象ワールドがありません: $action")
                listOf(
                    GuiLoreLine.Data(
                        plugin.languageManager.getMessage(player, MyworldGuiAdminKeys.GUI_ADMIN_MENU_TARGET_WORLD_LABEL),
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
        infoLore.add(GuiLoreLine.Warning(lang.getMessage(player, CommonKeys.GUI_COMMON_CONFIRM_WARNING)))

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
                            me.awabi2048.myworldmanager.util.fixedLabelName(
                                lang.getMessage(player, CommonKeys.GUI_COMMON_CONFIRMATION),
                                com.awabi2048.ccsystem.api.gui.GuiNameStyle.DEFAULT,
                            ),
                            GuiLoreSpec.Blocks(
                                buildList {
                                    val dataLines = infoLore.filterNot {
                                        it == GuiLoreLine.Spacer || it is GuiLoreLine.Warning
                                    }
                                    if (dataLines.isNotEmpty()) add(GuiLoreBlock(dataLines))
                                    add(GuiLoreBlock(listOf(GuiLoreLine.Warning(lang.getMessage(player, CommonKeys.GUI_COMMON_CONFIRM_WARNING)))))
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
            category = MenuViewCategory.CONFIRMATION,
        )
    }

    private fun confirmationFrom(route: MenuRoute): AdminConfirmation {
        val actionName = route.payload[CONFIRM_ACTION_PAYLOAD]
            ?: error("管理確認画面のルートにアクションがありません")
        val action = runCatching { SettingsAction.valueOf(actionName) }
            .getOrElse { error("管理確認画面のアクションが不正です: $actionName") }
        require(action in CONFIRMATION_ACTIONS) { "管理確認画面ではないアクションです: $action" }

        val targetWorldUuid = route.payload[CONFIRM_WORLD_UUID_PAYLOAD]?.let { value ->
            runCatching { UUID.fromString(value) }
                .getOrElse { error("管理確認画面の対象UUIDが不正です: $value") }
        }
        val targetWorldName = route.payload[CONFIRM_WORLD_NAME_PAYLOAD]
        when (action) {
            SettingsAction.ADMIN_CONVERT_NORMAL_CONFIRM,
            SettingsAction.ADMIN_CONVERT_ADMIN_CONFIRM -> require(targetWorldUuid != null && targetWorldName != null) {
                "ワールド変換の確認対象が不足しています"
            }
            SettingsAction.ADMIN_EXPORT_CONFIRM,
            SettingsAction.ADMIN_UNLINK_CONFIRM -> require(targetWorldName != null) {
                "管理操作の確認対象ワールド名がありません: $action"
            }
            SettingsAction.ADMIN_ARCHIVE_WORLD_CONFIRM,
            SettingsAction.ADMIN_UNARCHIVE_WORLD_CONFIRM -> require(targetWorldUuid != null) {
                "管理操作の確認対象UUIDがありません: $action"
            }
            else -> Unit
        }
        return AdminConfirmation(action, targetWorldUuid, targetWorldName)
    }

    private data class AdminConfirmation(
        val action: SettingsAction,
        val targetWorldUuid: UUID?,
        val targetWorldName: String?,
    )

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
            name = me.awabi2048.myworldmanager.util.fixedLabelName(
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
        return plugin.languageManager.getMessageList(player, key, placeholders).map(::descriptionLine)
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
        private const val CONFIRM_ACTION_PAYLOAD = "action"
        private const val CONFIRM_WORLD_UUID_PAYLOAD = "target_world_uuid"
        private const val CONFIRM_WORLD_NAME_PAYLOAD = "target_world_name"
        private val CONFIRMATION_ACTIONS = setOf(
            SettingsAction.ADMIN_CONVERT_NORMAL_CONFIRM,
            SettingsAction.ADMIN_CONVERT_ADMIN_CONFIRM,
            SettingsAction.ADMIN_EXPORT_CONFIRM,
            SettingsAction.ADMIN_ARCHIVE_ALL_CONFIRM,
            SettingsAction.ADMIN_UPDATE_DATA_CONFIRM,
            SettingsAction.ADMIN_UNLINK_CONFIRM,
            SettingsAction.ADMIN_REPAIR_TEMPLATES_CONFIRM,
            SettingsAction.ADMIN_ARCHIVE_WORLD_CONFIRM,
            SettingsAction.ADMIN_UNARCHIVE_WORLD_CONFIRM,
        )
    }
}
