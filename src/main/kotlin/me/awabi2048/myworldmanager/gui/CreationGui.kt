package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiMenuDisplaySpec
import com.awabi2048.ccsystem.api.gui.GuiMenuCapabilityInvocationSpec
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiMenuActionIntent
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryData
import com.awabi2048.ccsystem.api.gui.GuiMenuEntrySpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiValueTone
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuActionContext
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuActionSafety
import com.awabi2048.ccsystem.api.gui.MenuGesture
import com.awabi2048.ccsystem.api.gui.MenuCloseContext
import com.awabi2048.ccsystem.api.gui.MenuCloseHandler
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.api.extension.CreationConfirmationCapabilityContract
import me.awabi2048.myworldmanager.api.extension.WorldCreationDraft
import me.awabi2048.myworldmanager.model.*
import me.awabi2048.myworldmanager.listener.CreationConfirmationAction
import me.awabi2048.myworldmanager.repository.*
import me.awabi2048.myworldmanager.session.*
import me.awabi2048.myworldmanager.util.ItemTag
import me.awabi2048.myworldmanager.util.PermissionManager
import me.awabi2048.myworldmanager.util.WorldRuntimePolicies
import me.awabi2048.myworldmanager.util.WorldCreationChecks
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID

internal data class CreationTypeAvailability(val enabled: Boolean, val reasonKey: String? = null)

internal fun resolveCreationTypeAvailability(
    creationType: WorldCreationType,
    usableTemplateExists: Boolean,
    canAfford: Boolean,
): CreationTypeAvailability = when {
    !canAfford -> CreationTypeAvailability(false, "messages.creation_insufficient_points")
    creationType == WorldCreationType.TEMPLATE && !usableTemplateExists ->
        CreationTypeAvailability(false, "error.preview_template_not_found")
    else -> CreationTypeAvailability(true)
}

internal fun templateCreationTypeUpdate(route: MenuRoute): MenuUpdate = MenuUpdate.Navigate(route)

class CreationGui(private val plugin: MyWorldManager) {
    private val runtime = CCSystem.getAPI().getMenuRuntimeService()

    init {
        runtime.register(
            InventoryMenuDefinition(
                owner = OWNER,
                id = TYPE_ROUTE,
                renderer = { context -> renderTypeSelection(context.player) },
                actions = mapOf(
                    ACTION_SELECT_TYPE to MenuActionHandler(::selectCreationType),
                    ACTION_BACK to MenuActionHandler(::cancelCreation),
                ),
                onClose = MenuCloseHandler(::closed),
            ),
        )
        runtime.register(
            InventoryMenuDefinition(
                owner = OWNER,
                id = CONFIRM_ROUTE,
                renderer = { context -> renderConfirmation(context.player) },
                actions = mapOf(
                    ACTION_CONFIRM_INTERACTION to MenuActionHandler(::confirmationAction),
                ),
                onClose = MenuCloseHandler(::closed),
            ),
        )
        runtime.register(
            InventoryMenuDefinition(
                owner = OWNER,
                id = TEMPLATE_LIST_ROUTE,
                renderer = { context -> renderTemplateSelection(context.player, context.route) },
                actions = mapOf(
                    ACTION_SELECT_TEMPLATE to MenuActionHandler(::selectTemplate),
                    ACTION_TEMPLATE_LIST_PAGE to MenuActionHandler(::templateListPage),
                    ACTION_TEMPLATE_LIST_BACK to MenuActionHandler(::templateListBack),
                ),
                onClose = MenuCloseHandler(::closed),
            ),
        )
        runtime.register(
            InventoryMenuDefinition(
                owner = OWNER,
                id = TEMPLATE_DETAIL_ROUTE,
                renderer = { context -> renderTemplateDetail(context.player) },
                actions = mapOf(
                    ACTION_USE_TEMPLATE to MenuActionHandler(::useTemplate),
                    ACTION_PREVIEW_TEMPLATE to MenuActionHandler(::previewTemplate),
                    ACTION_TEMPLATE_DETAIL_BACK to MenuActionHandler(::templateDetailBack),
                ),
                onClose = MenuCloseHandler(::closed),
            ),
        )
    }

    fun openTypeSelection(player: Player) {
        val config = plugin.config
        val lang = plugin.languageManager
        val adminCommandSession = plugin.creationSessionManager.getSession(player.uniqueId)
            ?.extras
            ?.get(ADMIN_COMMAND_SESSION_KEY) == true
        if (!WorldCreationChecks.checkSelfCreatePermission(player, allowAdminCommandSession = adminCommandSession) ||
            !WorldCreationChecks.checkLimits(plugin, player, player.uniqueId) ||
            !WorldCreationChecks.check(player)
        ) {
            return
        }

        clearSettingsGuiTransition(player)
        runtime.navigate(player, typeSelectionRoute())
    }

    internal fun typeSelectionRoute(): MenuRoute = MenuRoute(OWNER, TYPE_ROUTE)

    internal fun templateSelectionRoute(): MenuRoute = MenuRoute(OWNER, TEMPLATE_LIST_ROUTE)

    internal fun templateCreationTypeUpdate(): MenuUpdate = templateCreationTypeUpdate(templateSelectionRoute())

    private fun renderTypeSelection(player: Player): InventoryMenuView {
        val lang = plugin.languageManager
        val layout = me.awabi2048.myworldmanager.util.GuiHelper.threeChoiceLayout()
        val session = plugin.creationSessionManager.getSession(player.uniqueId)
        val usableTemplateExists = plugin.templateRepository.findAll().any(plugin.templateRepository::isUsable)
        return InventoryMenuView(
            size = layout.size,
            title = me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(
                lang.getMessage(player, "gui.creation.title_type"),
            ),
            elements = listOf(
                createCreationTypeEntry(player, layout.leftSlot, plugin.menuConfigManager.getIconMaterial("creation", "template", Material.MAP), lang.getMessage("gui.creation.type.template.name"), "gui.creation.type.template.lore", WorldCreationType.TEMPLATE, creationTypeAvailability(player, session, WorldCreationType.TEMPLATE, usableTemplateExists)),
                createCreationTypeEntry(player, layout.centerSlot, plugin.menuConfigManager.getIconMaterial("creation", "seed", Material.NAME_TAG), lang.getMessage("gui.creation.type.seed.name"), "gui.creation.type.seed.lore", WorldCreationType.SEED, creationTypeAvailability(player, session, WorldCreationType.SEED, usableTemplateExists)),
                createCreationTypeEntry(player, layout.rightSlot, plugin.menuConfigManager.getIconMaterial("creation", "random", Material.ENDER_EYE), lang.getMessage("gui.creation.type.random.name"), "gui.creation.type.random.lore", WorldCreationType.RANDOM, creationTypeAvailability(player, session, WorldCreationType.RANDOM, usableTemplateExists)),
                backEntry(player, layout.backSlot, ACTION_BACK),
            ),
        )
    }

    private fun selectCreationType(context: MenuActionContext): MenuActionResult {
        val session = plugin.creationSessionManager.getSession(context.player.uniqueId)
            ?: return MenuActionResult.Rejected()
        val creationType = context.payload["type"]?.let {
            runCatching { WorldCreationType.valueOf(it) }.getOrNull()
        } ?: return MenuActionResult.Rejected()
        val availability = creationTypeAvailability(
            context.player,
            session,
            creationType,
            plugin.templateRepository.findAll().any(plugin.templateRepository::isUsable),
        )
        if (!availability.enabled) {
            context.player.sendMessage(plugin.languageManager.getMessage(context.player, availability.reasonKey!!))
            return MenuActionResult.Rejected()
        }

        session.creationType = creationType
        return when (creationType) {
            WorldCreationType.TEMPLATE -> {
                session.phase = WorldCreationPhase.TEMPLATE_SELECT
                MenuActionResult.Success(templateCreationTypeUpdate())
            }
            WorldCreationType.SEED -> {
                session.phase = WorldCreationPhase.SEED_INPUT
                if (session.isDialogMode) {
                    CreationDialogManager.showSeedInputDialog(context.player, session)
                } else {
                    plugin.creationGuiListener.openSeedInputByPlatform(context.player, session)
                }
                MenuActionResult.Success(MenuUpdate.None)
            }
            WorldCreationType.RANDOM -> {
                session.phase = WorldCreationPhase.NAME_INPUT
                if (session.isDialogMode) {
                    CreationDialogManager.showNameInputDialog(context.player, session)
                } else {
                    plugin.creationGuiListener.openNameInputByPlatform(context.player, session)
                }
                MenuActionResult.Success(MenuUpdate.None)
            }
        }
    }

    private fun cancelCreation(context: MenuActionContext): MenuActionResult {
        plugin.creationGuiListener.cancelAndReturnToMyWorld(context.player)
        return MenuActionResult.Success(MenuUpdate.None)
    }

    private fun selectTemplate(context: MenuActionContext): MenuActionResult {
        val session = plugin.creationSessionManager.getSession(context.player.uniqueId)
            ?: return MenuActionResult.Rejected()
        val templateId = context.payload["template"] ?: return MenuActionResult.Rejected()
        val template = plugin.templateRepository.findById(templateId)
            ?.takeIf(plugin.templateRepository::isUsable)
            ?: return MenuActionResult.Rejected()
        session.templateId = template.id
        session.phase = WorldCreationPhase.TEMPLATE_DETAIL
        return MenuActionResult.Success(MenuUpdate.Navigate(MenuRoute(OWNER, TEMPLATE_DETAIL_ROUTE)))
    }

    private fun templateListBack(context: MenuActionContext): MenuActionResult {
        val session = plugin.creationSessionManager.getSession(context.player.uniqueId)
            ?: return MenuActionResult.Rejected()
        session.phase = WorldCreationPhase.TYPE_SELECT
        return MenuActionResult.Success(MenuUpdate.Replace(MenuRoute(OWNER, TYPE_ROUTE)))
    }

    private fun templateListPage(context: MenuActionContext): MenuActionResult {
        val page = context.payload[PAGE]?.toIntOrNull() ?: return MenuActionResult.Rejected()
        return MenuActionResult.Success(
            MenuUpdate.Replace(MenuRoute(OWNER, TEMPLATE_LIST_ROUTE, mapOf(PAGE to page.toString()))),
        )
    }

    private fun templateDetailBack(context: MenuActionContext): MenuActionResult {
        val session = plugin.creationSessionManager.getSession(context.player.uniqueId)
            ?: return MenuActionResult.Rejected()
        session.phase = WorldCreationPhase.TEMPLATE_SELECT
        return MenuActionResult.Success(MenuUpdate.Back)
    }

    private fun useTemplate(context: MenuActionContext): MenuActionResult {
        val session = plugin.creationSessionManager.getSession(context.player.uniqueId)
            ?: return MenuActionResult.Rejected()
        val template = session.templateId?.let(plugin.templateRepository::findById)
            ?.takeIf(plugin.templateRepository::isUsable)
            ?: return MenuActionResult.Rejected()
        session.templateId = template.id
        session.phase = WorldCreationPhase.NAME_INPUT
        plugin.creationGuiListener.openNameInputByPlatform(context.player, session)
        return MenuActionResult.Success(MenuUpdate.None)
    }

    private fun previewTemplate(context: MenuActionContext): MenuActionResult {
        val session = plugin.creationSessionManager.getSession(context.player.uniqueId)
            ?: return MenuActionResult.Rejected()
        val templateId = session.templateId ?: return MenuActionResult.Rejected()
        plugin.previewSessionManager.startPreview(
            context.player,
            PreviewSessionManager.PreviewTarget.Template(templateId),
            PreviewSource.TEMPLATE_DETAIL,
        )
        return MenuActionResult.Success(MenuUpdate.None)
    }

    private fun closed(context: MenuCloseContext) {
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (!context.player.isOnline) return@Runnable
            val session = plugin.creationSessionManager.getSession(context.player.uniqueId)
                ?: return@Runnable
            if (
                session.phase == WorldCreationPhase.SEED_INPUT ||
                session.phase == WorldCreationPhase.NAME_INPUT ||
                session.phase == WorldCreationPhase.SPAWN_INPUT ||
                plugin.previewSessionManager.isInPreview(context.player)
            ) {
                return@Runnable
            }
            if (runtime.refresh(context.player)) return@Runnable
            plugin.creationGuiListener.cancelAndReturnToMyWorld(context.player)
        }, 2L)
    }

    private fun createCreationTypeEntry(
        player: Player,
        slot: Int,
        material: Material,
        name: String,
        baseLoreKey: String,
        creationType: WorldCreationType,
        availability: CreationTypeAvailability,
    ): MenuElement {
        val lang = plugin.languageManager
        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        val data = mutableListOf<GuiMenuEntryData>()
        val warnings = mutableListOf<String>()
        availability.reasonKey?.let { warnings += lang.getMessage(player, it).removePrefix("ﾂｧc") }
        if (MyWorldManagerApi.isWorldPointEconomyEnabled()) {
            val cost = WorldRuntimePolicies.creationCost(plugin.config, creationType)
            data += GuiMenuEntryData(
                lang.getMessage(player, "gui.creation.type.cost_label"),
                "🛖 $cost",
                GuiValueTone.WARNING,
            )
            data += GuiMenuEntryData(
                lang.getMessage(player, "gui.creation.type.current_points_label"),
                "🛖 ${stats.worldPoint}",
                GuiValueTone.WARNING,
            )
            data += GuiMenuEntryData(
                lang.getMessage(player, "gui.creation.type.remaining_points_label"),
                "🛖 ${(stats.worldPoint - cost).coerceAtLeast(0)}",
                GuiValueTone.WARNING,
            )
            if (stats.worldPoint < cost) {
                warnings +=
                    lang.getMessage(
                        player,
                        "gui.creation.type.insufficient",
                        mapOf("shortage" to (cost - stats.worldPoint))
                    ).removePrefix("§c")
            }
        }
        val defaultMax = WorldRuntimePolicies.maxCreateCountDefault(plugin.config)
        val maxCounts = defaultMax + stats.unlockedWorldSlot
        val currentCounts = plugin.worldConfigRepository.findAll().count { it.owner == player.uniqueId }
        val bypassLimits = PermissionManager.canBypassWorldLimits(player)

        if (MyWorldManagerApi.isWorldSlotSystemEnabled() &&
            !bypassLimits &&
            currentCounts >= maxCounts
        ) {
            warnings += lang.getMessage(
                player,
                "gui.creation.limit_reached",
                mapOf("current" to currentCounts, "max" to maxCounts),
            ).removePrefix("§c")
        }

        val element = CCSystem.getAPI().getGuiElementService().menuEntry(
            player,
            GuiMenuEntrySpec(
                slot = slot,
                material = material,
                name = GuiNameSpec.Text(name, com.awabi2048.ccsystem.api.gui.GuiNameStyle.DEFAULT),
                role = if (availability.enabled) GuiElementRole.ACTION else GuiElementRole.CONTENT,
                description = lang.getMessageList(player, baseLoreKey),
                data = data,
                warnings = warnings,
                actions = if (availability.enabled) listOf(
                    menuGestureAction(
                        ACTION_SELECT_TYPE,
                        MenuGesture.ANY,
                        name,
                        mapOf("type" to creationType.name),
                        safety = creationTypeSafety(creationType),
                    ),
                ) else emptyList(),
            ),
        )
        return if (availability.enabled) element else element.copy(enabled = false)
    }

    private fun creationTypeAvailability(
        player: Player,
        session: WorldCreationSession?,
        creationType: WorldCreationType,
        usableTemplateExists: Boolean,
    ): CreationTypeAvailability {
        val cost = WorldRuntimePolicies.creationCost(plugin.config, creationType)
        val canAfford = session?.billingMode != me.awabi2048.myworldmanager.api.service.WorldPointBillingMode.STANDARD ||
            !MyWorldManagerApi.isWorldPointEconomyEnabled() ||
            plugin.playerStatsRepository.findByUuid(player.uniqueId).worldPoint >= cost
        return resolveCreationTypeAvailability(creationType, usableTemplateExists, canAfford)
    }

    fun openTemplateSelection(player: Player) {
        val lang = plugin.languageManager
        val templates = plugin.templateRepository.findAll()
            .filter(plugin.templateRepository::isUsable)
        if (templates.isEmpty()) {
            player.sendMessage(lang.getMessage(player, "error.preview_template_not_found"))
            return
        }
        clearSettingsGuiTransition(player)
        runtime.navigate(player, MenuRoute(OWNER, TEMPLATE_LIST_ROUTE))
    }

    private fun renderTemplateSelection(player: Player, route: MenuRoute): InventoryMenuView {
        val lang = plugin.languageManager
        val templates = plugin.templateRepository.findAll()
            .filter(plugin.templateRepository::isUsable)
        val page = CCSystem.getAPI().getGuiLayoutService().sevenColumnPage(
            templates.size,
            route.payload[PAGE]?.toIntOrNull() ?: 0,
        )
        val layout = page.layout
        val elements = mutableListOf<MenuElement>()
        templates.drop(page.startIndex).take(page.itemCount).forEachIndexed { index, template ->
            val issue = plugin.templateRepository.validationIssue(template)
            elements += CCSystem.getAPI().getGuiElementService().menuEntry(
                player,
                GuiMenuEntrySpec(
                    slot = layout.itemSlots[index],
                    material = template.icon,
                    name = GuiNameSpec.Text(template.name, com.awabi2048.ccsystem.api.gui.GuiNameStyle.DEFAULT),
                    role = GuiElementRole.ACTION,
                    description = template.description,
                    data = listOf(GuiMenuEntryData(
                        lang.getMessage(player, "gui.creation.template_detail.status_label"),
                        lang.getMessage(player, if (issue == null) "gui.creation.template_detail.status_available" else "gui.creation.template_detail.status_unavailable"),
                        if (issue == null) GuiValueTone.SUCCESS else GuiValueTone.DANGER,
                    )),
                    warnings = issue?.let { listOf(templateValidationMessage(player, it)) }.orEmpty(),
                    actions = listOf(menuGestureAction(
                        ACTION_SELECT_TEMPLATE,
                        MenuGesture.LEFT_RIGHT,
                        lang.getMessage(player, "gui.creation.template_item.action.details"),
                        mapOf("template" to template.id),
                        safety = MenuActionSafety.NAVIGATION_ONLY,
                    )),
                ),
            )
        }
        if (page.page > 0) {
            elements += navigationEntry(player, layout.previousPageSlot, false, page.page - 1)
        }
        if (page.page < page.totalPages - 1) {
            elements += navigationEntry(player, layout.nextPageSlot, true, page.page + 1)
        }
        elements += backEntry(player, layout.actionSlot, ACTION_TEMPLATE_LIST_BACK)
        return InventoryMenuView(
            size = layout.size,
            title = me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(
                lang.getMessage(player, "gui.creation.title_template"),
            ),
            elements = elements,
        )
    }

    fun openTemplateDetail(player: Player, session: WorldCreationSession) {
        val lang = plugin.languageManager
        val template = session.templateId?.let(plugin.templateRepository::findById)
        if (template == null || !plugin.templateRepository.isUsable(template)) {
            player.sendMessage(lang.getMessage(player, "error.preview_template_not_found"))
            session.phase = WorldCreationPhase.TEMPLATE_SELECT
            openTemplateSelection(player)
            return
        }
        runtime.navigate(player, MenuRoute(OWNER, TEMPLATE_DETAIL_ROUTE))
    }

    private fun renderTemplateDetail(player: Player): InventoryMenuView {
        val lang = plugin.languageManager
        val session = plugin.creationSessionManager.getSession(player.uniqueId)
            ?: error("ワールド作成セッションがありません")
        val template = session.templateId?.let(plugin.templateRepository::findById)
            ?.takeIf(plugin.templateRepository::isUsable)
            ?: error("利用可能なテンプレートがありません")
        val layout = me.awabi2048.myworldmanager.util.GuiHelper.threeChoiceLayout()
        val issue = plugin.templateRepository.validationIssue(template)
        val origin = template.originLocation
        val cost = WorldRuntimePolicies.creationCost(plugin.config, WorldCreationType.TEMPLATE)
        val detailLines = buildList {
            addAll(template.description.map(GuiLoreLine::Text))
            add(GuiLoreLine.Spacer)
            add(GuiLoreLine.Data(
                lang.getMessage(player, "gui.creation.template_detail.spawn_label"),
                origin?.let { "(${it.blockX}, ${it.blockY}, ${it.blockZ})" }
                    ?: lang.getMessage(player, "general.unknown"),
                "§6"
            ))
            add(GuiLoreLine.Data(
                lang.getMessage(player, "gui.creation.confirm.cost_label"),
                "§6🛖 §e$cost",
                ""
            ))
            add(GuiLoreLine.Data(
                lang.getMessage(player, "gui.creation.template_detail.status_label"),
                lang.getMessage(
                    player,
                    if (issue == null) {
                        "gui.creation.template_detail.status_available"
                    } else {
                        "gui.creation.template_detail.status_unavailable"
                    }
                ),
                if (issue == null) "§a" else "§c"
            ))
            if (issue != null) add(GuiLoreLine.Warning(templateValidationMessage(player, issue)))
        }
        val elements = mutableListOf<MenuElement>()
        elements += displayEntry(
            layout.leftSlot,
            template.icon,
            template.name,
            GuiLoreSpec.Rich(detailLines, GuiLoreFrame.BOTH),
        )
        if (issue == null) {
            elements += actionEntry(
                player, layout.centerSlot, Material.LIME_CONCRETE,
                lang.getMessage(player, "gui.creation.template_detail.use"),
                ACTION_USE_TEMPLATE,
                lang.getMessage(player, "gui.creation.template_detail.use_action"),
            )
            elements += actionEntry(
                player, layout.rightSlot, Material.ENDER_EYE,
                lang.getMessage(player, "gui.creation.template_detail.preview"),
                ACTION_PREVIEW_TEMPLATE,
                lang.getMessage(player, "gui.creation.template_detail.preview_action"),
            )
        }
        elements += backEntry(player, layout.backSlot, ACTION_TEMPLATE_DETAIL_BACK)
        return InventoryMenuView(
            size = layout.size,
            title = me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(
                lang.getMessage(player, "gui.creation.template_detail.title"),
            ),
            elements = elements,
        )
    }

    fun openConfirmation(player: Player, session: WorldCreationSession) {
        clearSettingsGuiTransition(player)
        runtime.navigate(player, MenuRoute(OWNER, CONFIRM_ROUTE))
    }

    private fun renderConfirmation(player: Player): InventoryMenuView {
        val lang = plugin.languageManager
        val session = plugin.creationSessionManager.getSession(player.uniqueId)
            ?: error("ワールド作成セッションがありません")
        val layout = me.awabi2048.myworldmanager.util.GuiHelper.confirmationLayout()
        val elements = mutableListOf<MenuElement>()
        val capabilityService = CCSystem.getAPI().getMenuCapabilityService()
        val capabilityAttributes = mapOf<String, Any>(
            CreationConfirmationCapabilityContract.DRAFT_ATTRIBUTE to SessionCreationDraft(session),
        )
        val confirmationCapability = capabilityService
            .definitions(CreationConfirmationCapabilityContract.PLACEMENT)
            .firstNotNullOfOrNull { definition ->
                capabilityService.resolve(
                    definition.capabilityId,
                    player,
                    attributes = capabilityAttributes,
                )?.requireExplicitActionSafety()
            }
        val cleanedName = cleanWorldName(session.worldName ?: lang.getMessage(player, "general.unknown"))
        val generationLine: GuiLoreLine = when (session.creationType) {
            WorldCreationType.TEMPLATE -> {
                val template = session.templateId?.let(plugin.templateRepository::findById)
                val displayName = template?.name ?: (session.templateId ?: lang.getMessage(player, "general.unknown"))
                GuiLoreLine.SubData(
                    lang.getMessage(player, "gui.creation.confirm.template_label"),
                    displayName
                )
            }
            WorldCreationType.SEED -> GuiLoreLine.SubData(
                lang.getMessage(player, "gui.creation.confirm.seed_label"),
                session.inputSeedString ?: ""
            )
            WorldCreationType.RANDOM -> GuiLoreLine.SubData(
                lang.getMessage(player, "gui.creation.confirm.generation_label"),
                lang.getMessage(player, "gui.creation.confirm.random_generation")
            )
            null -> GuiLoreLine.SubData(
                lang.getMessage(player, "gui.creation.confirm.generation_label"),
                lang.getMessage(player, "general.unknown")
            )
        }

        val infoLore = GuiLoreSpec.Rich(
                buildList {
                    add(GuiLoreLine.Data(
                        lang.getMessage(player, "gui.creation.confirm.world_name_label"),
                        cleanedName,
                        "§e"
                    ))
                    add(generationLine)
                    if (MyWorldManagerApi.isWorldPointEconomyEnabled()) {
                        val cost = session.creationType?.let {
                            WorldRuntimePolicies.creationCost(plugin.config, it)
                        } ?: 0
                        val currentPoints = plugin.playerStatsRepository
                            .findByUuid(player.uniqueId)
                            .worldPoint
                        add(GuiLoreLine.SubData(
                            lang.getMessage(player, "gui.creation.confirm.cost_label"),
                            "§6🛖 §e$cost"
                        ))
                        add(GuiLoreLine.SubData(
                            lang.getMessage(player, "gui.creation.confirm.current_points_label"),
                            "§6🛖 §e$currentPoints"
                        ))
                        add(GuiLoreLine.SubData(
                            lang.getMessage(player, "gui.creation.confirm.remaining_points_label"),
                            "§6🛖 §e${(currentPoints - cost).coerceAtLeast(0)}"
                        ))
                    }
                    if (session.creationType == WorldCreationType.TEMPLATE) {
                        val template = session.templateId?.let(plugin.templateRepository::findById)
                        val origin = template?.originLocation
                        add(GuiLoreLine.SubData(
                            lang.getMessage(player, "gui.creation.confirm.template_spawn_label"),
                            origin?.let { "(${it.blockX}, ${it.blockY}, ${it.blockZ})" }
                                ?: lang.getMessage(player, "general.unknown")
                        ))
                    }
                    if (session.creationType == WorldCreationType.SEED) {
                        add(
                            GuiLoreLine.SubData(
                                lang.getMessage(player, "gui.creation.confirm.dimension_label"),
                                seedEnvironmentDisplay(player, session.seedEnvironment)
                            )
                        )
                    }
                },
                GuiLoreFrame.BOTH
        )

        elements += displayEntry(
            layout.previewSlot,
            Material.PAPER,
            lang.getMessage(player, "gui.creation.confirm.name"),
            infoLore,
        )
        elements += confirmationEntry(
            player,
            layout.confirmSlot,
            Material.LIME_CONCRETE,
            "gui.common.confirm",
            GuiElementRole.CONFIRM,
            CreationConfirmationAction.CONFIRM,
        )
        elements += confirmationEntry(
            player,
            layout.cancelSlot,
            Material.RED_CONCRETE,
            "gui.common.cancel",
            GuiElementRole.CANCEL,
            CreationConfirmationAction.CANCEL,
        )

        if (session.creationType == WorldCreationType.SEED) {
            elements += interactionEntry(
                player,
                SEED_DIMENSION_SLOT,
                seedEnvironmentMaterial(session.seedEnvironment),
                lang.getMessage(player, "gui.creation.confirm.dimension.display"),
                seedEnvironmentLore(player, session.seedEnvironment),
                GuiElementRole.ACTION,
                CreationConfirmationAction.DIMENSION,
                lang.getMessage(player, "gui.creation.confirm.dimension.action"),
            )

            val coordinates = session.spawnCoordinates?.let {
                "(${it.x}, ${it.y}, ${it.z})"
            } ?: lang.getMessage(player, "gui.creation.confirm.spawn_location.default")
            val spawnSlot = if (confirmationCapability == null) {
                    SEED_SPAWN_LOCATION_SLOT
                } else {
                    SEED_SPAWN_LOCATION_WITH_CAPABILITY_SLOT
                }
            elements += CCSystem.getAPI().getGuiElementService().menuEntry(
                player,
                GuiMenuEntrySpec(
                    slot = spawnSlot,
                    material = Material.COMPASS,
                    name = GuiNameSpec.Text(
                        lang.getMessage(player, "gui.creation.confirm.spawn_location.display"),
                        com.awabi2048.ccsystem.api.gui.GuiNameStyle.DEFAULT,
                    ),
                    role = GuiElementRole.ACTION,
                    description = listOf(
                        lang.getMessage(player, "gui.creation.confirm.spawn_location.description"),
                        lang.getMessage(player, "gui.creation.confirm.spawn_location.default_help"),
                    ),
                    data = listOf(
                        GuiMenuEntryData(
                            lang.getMessage(player, "gui.creation.confirm.spawn_location.current_label"),
                            coordinates,
                            if (session.spawnCoordinates == null) GuiValueTone.INFO else GuiValueTone.WARNING,
                        ),
                    ),
                    actions = listOf(
                        menuGestureAction(
                            ACTION_CONFIRM_INTERACTION,
                            MenuGesture.ANY,
                            lang.getMessage(player, "gui.creation.confirm.spawn_location.action"),
                            mapOf(CONFIRMATION_ACTION to CreationConfirmationAction.SPAWN_LOCATION.name),
                            safety = confirmationActionSafety(CreationConfirmationAction.SPAWN_LOCATION),
                        ),
                    ),
                ),
            )
            confirmationCapability?.let { resolved ->
                elements += confirmationCapabilityElement(player, resolved, capabilityAttributes)
            }
        } else if (session.creationType == WorldCreationType.TEMPLATE) {
            elements += interactionEntry(
                player,
                if (confirmationCapability == null) 39 else 38,
                Material.ENDER_EYE,
                lang.getMessage(player, "gui.creation.template_detail.preview"),
                GuiLoreSpec.None,
                GuiElementRole.ACTION,
                CreationConfirmationAction.TEMPLATE_PREVIEW,
                lang.getMessage(player, "gui.creation.template_detail.preview_action"),
            )
            elements += interactionEntry(
                player,
                39,
                Material.NAME_TAG,
                lang.getMessage(player, "gui.creation.confirm.change_name"),
                GuiLoreSpec.None,
                GuiElementRole.NAVIGATION,
                CreationConfirmationAction.BACK,
                lang.getMessage(player, "gui.creation.confirm.change_name"),
            )
            confirmationCapability?.let { resolved ->
                elements += confirmationCapabilityElement(player, resolved, capabilityAttributes)
            }
            elements += interactionEntry(
                player,
                41,
                Material.MAP,
                lang.getMessage(player, "gui.creation.confirm.change_template"),
                GuiLoreSpec.None,
                GuiElementRole.NAVIGATION,
                CreationConfirmationAction.TEMPLATE_CHANGE,
                lang.getMessage(player, "gui.creation.confirm.change_template"),
            )
        } else {
            confirmationCapability?.let { resolved ->
                elements += confirmationCapabilityElement(player, resolved, capabilityAttributes)
            }
        }
        return InventoryMenuView(
            size = layout.size,
            title = me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(
                lang.getMessage(player, "gui.creation.title_confirm"),
            ),
            elements = elements,
        )
    }

    private fun confirmationCapabilityElement(
        player: Player,
        resolved: com.awabi2048.ccsystem.api.gui.ResolvedMenuCapability,
        attributes: Map<String, Any>,
    ): MenuElement {
        return CCSystem.getAPI().getGuiElementService().menuCapabilityEntry(
            player,
            GuiMenuCapabilityInvocationSpec(
                slot = CONFIRM_CAPABILITY_SLOT,
                capability = resolved.requireExplicitActionSafety(),
                attributes = attributes,
            ),
        )
    }

    private fun confirmationAction(context: MenuActionContext): MenuActionResult {
        val action = context.payload[CONFIRMATION_ACTION]
            ?.let { runCatching { CreationConfirmationAction.valueOf(it) }.getOrNull() }
            ?: return MenuActionResult.Ignored
        return plugin.creationGuiListener.handleConfirmationAction(
            context.player,
            context.click,
            action,
        )
    }

    private fun displayEntry(
        slot: Int,
        material: Material,
        name: String,
        lore: GuiLoreSpec,
    ): MenuElement = CCSystem.getAPI().getGuiElementService().menuDisplay(
        GuiMenuDisplaySpec(
            slot,
            GuiItemSpec(
                material,
                GuiNameSpec.Text(name, com.awabi2048.ccsystem.api.gui.GuiNameStyle.DEFAULT),
                lore,
                GuiElementRole.CONTENT,
                1,
            ),
        ),
    )

    private fun backEntry(player: Player, slot: Int, actionId: String): MenuElement =
        CCSystem.getAPI().getGuiElementService().backEntry(
            player,
            slot,
            plugin.menuConfigManager.getIconMaterial("world_settings", "back", Material.REDSTONE),
        )

    private fun navigationEntry(player: Player, slot: Int, next: Boolean, targetPage: Int): MenuElement {
        val key = if (next) "gui.common.next_page" else "gui.common.prev_page"
        val iconId = if (next) "next_page" else "prev_page"
        return CCSystem.getAPI().getGuiElementService().menuEntry(
            player,
            GuiMenuEntrySpec(
                slot = slot,
                material = plugin.menuConfigManager.getIconMaterial("creation_template", iconId, Material.ARROW),
                name = GuiNameSpec.Component(plugin.languageManager.getComponent(player, key)),
                role = GuiElementRole.NAVIGATION,
                actions = listOf(
                    menuGestureAction(
                        ACTION_TEMPLATE_LIST_PAGE,
                        MenuGesture.ANY,
                        plugin.languageManager.getMessage(player, key),
                        mapOf(PAGE to targetPage.toString()),
                        safety = MenuActionSafety.NAVIGATION_ONLY,
                    ),
                ),
            ),
        )
    }

    private fun confirmationEntry(
        player: Player,
        slot: Int,
        material: Material,
        nameKey: String,
        role: GuiElementRole,
        action: CreationConfirmationAction,
    ): MenuElement = interactionEntry(
        player,
        slot,
        material,
        plugin.languageManager.getMessage(player, nameKey),
        GuiLoreSpec.None,
        role,
        action,
        plugin.languageManager.getMessage(player, nameKey),
    )

    private fun interactionEntry(
        player: Player,
        slot: Int,
        material: Material,
        name: String,
        lore: GuiLoreSpec,
        role: GuiElementRole,
        action: CreationConfirmationAction,
        actionText: String,
    ): MenuElement {
        val lines = when (lore) {
            is GuiLoreSpec.Rich -> lore.lines
            else -> emptyList()
        }
        return CCSystem.getAPI().getGuiElementService().menuEntry(
            player,
            GuiMenuEntrySpec(
                slot = slot,
                material = material,
                name = GuiNameSpec.Text(name, com.awabi2048.ccsystem.api.gui.GuiNameStyle.DEFAULT),
                role = role,
                description = lines.mapNotNull {
                    when (it) {
                        is GuiLoreLine.Text -> it.text
                        is GuiLoreLine.UserText -> it.text
                        else -> null
                    }
                },
                data = lines.mapNotNull {
                    when (it) {
                        is GuiLoreLine.Data -> GuiMenuEntryData(it.label, it.value)
                        is GuiLoreLine.SubData -> GuiMenuEntryData(it.label, it.value, GuiValueTone.MUTED)
                        else -> null
                    }
                },
                options = lines.mapNotNull {
                    (it as? GuiLoreLine.Option)?.let { option ->
                        com.awabi2048.ccsystem.api.gui.GuiMenuEntryOption(option.label, option.selected)
                    }
                },
                warnings = lines.filterIsInstance<GuiLoreLine.Warning>().map(GuiLoreLine.Warning::content),
                actions = listOf(
                    menuGestureAction(
                        ACTION_CONFIRM_INTERACTION,
                        MenuGesture.ANY,
                        actionText,
                        mapOf(CONFIRMATION_ACTION to action.name),
                            safety = confirmationActionSafety(action),
                            reversibleContract = when (action) {
                                CreationConfirmationAction.CANCEL -> MwmMenuActionSemantics.contract("creation-cancel")
                                CreationConfirmationAction.DIMENSION -> MwmMenuActionSemantics.contract("creation-dimension")
                                else -> null
                            },
                    ),
                ),
            ),
        )
    }

    private fun cleanWorldName(name: String): String {
        // 括弧内の文字列（ローマ字変換など）を削除
        val regex = Regex("\\s?\\(.*?\\)")
        return name.replace(regex, "").trim()
    }

    private fun seedEnvironmentDisplay(player: Player, environment: World.Environment): String {
        val key = when (environment) {
            World.Environment.NORMAL -> "normal"
            World.Environment.NETHER -> "nether"
            World.Environment.THE_END -> "the_end"
            else -> "normal"
        }
        return plugin.languageManager.getMessage(player, "gui.creation.confirm.dimension.options.$key")
    }

    private fun seedEnvironmentLore(player: Player, current: World.Environment): GuiLoreSpec {
        val lang = plugin.languageManager
        val options = listOf(
            World.Environment.NORMAL to "\u00A7a",
            World.Environment.NETHER to "\u00A7c",
            World.Environment.THE_END to "\u00A75"
        )
        return GuiLoreSpec.Rich(buildList {
            add(GuiLoreLine.Data(
                lang.getMessage(player, "gui.creation.confirm.dimension.current_label"),
                seedEnvironmentDisplay(player, current),
                options.first { it.first == current }.second
            ))
            add(GuiLoreLine.Text(lang.getMessage(player, "gui.creation.confirm.dimension.description")))
            add(GuiLoreLine.Spacer)
            options.forEach { (environment, selectedColor) ->
                val selected = environment == current
                add(GuiLoreLine.Option(seedEnvironmentDisplay(player, environment), selected, selectedColor, "\u00A77"))
            }
        }, GuiLoreFrame.BOTH)
    }

    private fun seedEnvironmentMaterial(environment: World.Environment): Material {
        return when (environment) {
            World.Environment.NORMAL -> Material.GRASS_BLOCK
            World.Environment.NETHER -> Material.NETHERRACK
            World.Environment.THE_END -> Material.END_STONE
            else -> Material.GRASS_BLOCK
        }
    }

    private fun templateValidationMessage(
        player: Player,
        issue: TemplateRepository.ValidationIssue
    ): String {
        val key = when (issue) {
            TemplateRepository.ValidationIssue.MISSING_DIRECTORY ->
                "gui.creation.template_detail.error.missing_directory"
            TemplateRepository.ValidationIssue.MISSING_ORIGIN ->
                "gui.creation.template_detail.error.missing_origin"
        }
        return plugin.languageManager.getMessage(player, key)
    }

    private fun clearSettingsGuiTransition(player: Player) {
    }

    private fun actionEntry(
        player: Player,
        slot: Int,
        material: Material,
        name: String,
        actionId: String,
        actionText: String,
    ): MenuElement = CCSystem.getAPI().getGuiElementService().menuEntry(
        player,
        GuiMenuEntrySpec(
            slot = slot,
            material = material,
            name = GuiNameSpec.Text(name, com.awabi2048.ccsystem.api.gui.GuiNameStyle.DEFAULT),
            role = GuiElementRole.ACTION,
            actions = listOf(menuGestureAction(actionId, MenuGesture.ANY, actionText, safety = templateActionSafety(actionId))),
        ),
    )

    private fun creationTypeSafety(type: WorldCreationType): MenuActionSafety = when (type) {
        WorldCreationType.TEMPLATE -> MenuActionSafety.NAVIGATION_ONLY
        WorldCreationType.SEED,
        WorldCreationType.RANDOM -> MenuActionSafety.INPUT_OR_EXTERNAL_SURFACE
    }

    private fun confirmationActionSafety(action: CreationConfirmationAction): MenuActionSafety = when (action) {
        CreationConfirmationAction.BACK,
        CreationConfirmationAction.SPAWN_LOCATION -> MenuActionSafety.INPUT_OR_EXTERNAL_SURFACE
        CreationConfirmationAction.DIMENSION,
        CreationConfirmationAction.CANCEL -> MenuActionSafety.REVERSIBLE
        CreationConfirmationAction.TEMPLATE_PREVIEW -> MenuActionSafety.EXTERNAL_SIDE_EFFECT
        CreationConfirmationAction.TEMPLATE_CHANGE -> MenuActionSafety.NAVIGATION_ONLY
        CreationConfirmationAction.CONFIRM -> MenuActionSafety.IRREVERSIBLE
    }

    private fun templateActionSafety(actionId: String): MenuActionSafety = when (actionId) {
        ACTION_USE_TEMPLATE -> MenuActionSafety.INPUT_OR_EXTERNAL_SURFACE
        ACTION_PREVIEW_TEMPLATE -> MenuActionSafety.EXTERNAL_SIDE_EFFECT
        else -> error("Unknown creation template action safety: $actionId")
    }

    private class SessionCreationDraft(
        private val session: WorldCreationSession,
    ) : WorldCreationDraft {
        override val worldName: String?
            get() = session.worldName

        override fun getBoolean(key: String): Boolean? =
            session.extras[key] as? Boolean

        override fun setBoolean(key: String, value: Boolean) {
            session.extras[key] = value
        }
    }

    companion object {
        const val ADMIN_COMMAND_SESSION_KEY = "mwm:admin_command_creation"
        const val SEED_DIMENSION_SLOT = 39
        const val SEED_SPAWN_LOCATION_SLOT = 40
        const val SEED_SPAWN_LOCATION_WITH_CAPABILITY_SLOT = 41
        private const val OWNER = "myworldmanager"
        private const val TYPE_ROUTE = "creation_type"
        private const val TEMPLATE_LIST_ROUTE = "creation_template_list"
        private const val TEMPLATE_DETAIL_ROUTE = "creation_template_detail"
        private const val CONFIRM_ROUTE = "creation_confirmation"
        private const val ACTION_SELECT_TYPE = "select_type"
        private const val ACTION_BACK = "back"
        private const val ACTION_SELECT_TEMPLATE = "select_template"
        private const val ACTION_TEMPLATE_LIST_PAGE = "template_list_page"
        private const val ACTION_TEMPLATE_LIST_BACK = "template_list_back"
        private const val ACTION_USE_TEMPLATE = "use_template"
        private const val ACTION_PREVIEW_TEMPLATE = "preview_template"
        private const val ACTION_TEMPLATE_DETAIL_BACK = "template_detail_back"
        private const val ACTION_CONFIRM_INTERACTION = "confirm_interaction"
        private const val CONFIRMATION_ACTION = "confirmation_action"
        private const val CONFIRM_CAPABILITY_SLOT = 40
        private const val PAGE = "page"
    }

}
