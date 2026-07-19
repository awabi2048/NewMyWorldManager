package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.model.*
import me.awabi2048.myworldmanager.repository.*
import me.awabi2048.myworldmanager.session.*
import me.awabi2048.myworldmanager.util.GuiLoreActions
import me.awabi2048.myworldmanager.util.GuiItemFactory
import me.awabi2048.myworldmanager.util.GuiLoreAction
import me.awabi2048.myworldmanager.util.GuiLoreBuilder
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

class CreationGui(private val plugin: MyWorldManager) {

    fun openTypeSelection(player: Player) {
        val config = plugin.config
        val lang = plugin.languageManager
        if (!WorldCreationChecks.checkLimits(plugin, player, player.uniqueId) || !WorldCreationChecks.check(player)) {
            plugin.soundManager.playClickSound(player, ItemStack(Material.BARRIER))
            return
        }

        val titleKey = "gui.creation.title_type"
        if (!lang.hasKey(player, titleKey)) {
             player.sendMessage("§c[MyWorldManager] Error: Missing translation key: $titleKey")
             return
        }
        val title = me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(lang.getMessage(player, titleKey))
        me.awabi2048.myworldmanager.util.GuiHelper.playMenuOpen(player, "creation")
        clearSettingsGuiTransition(player)
        val holder = CreationGuiHolder(CreationMenuType.TYPE_SELECT)
        val layout = me.awabi2048.myworldmanager.util.GuiHelper.threeChoiceLayout()
        val inventory = Bukkit.createInventory(holder, layout.size, title)
        holder.inv = inventory

        setupHeaderFooter(inventory, 5)

        val templateItem = createCreationTypeItem(player, plugin.menuConfigManager.getIconMaterial("creation", "template", Material.MAP), lang.getMessage("gui.creation.type.template.name"), "gui.creation.type.template.lore", WorldCreationType.TEMPLATE, ItemTag.TYPE_GUI_CREATION_TYPE_TEMPLATE)
        val randomItem = createCreationTypeItem(player, plugin.menuConfigManager.getIconMaterial("creation", "random", Material.ENDER_EYE), lang.getMessage("gui.creation.type.random.name"), "gui.creation.type.random.lore", WorldCreationType.RANDOM, ItemTag.TYPE_GUI_CREATION_TYPE_RANDOM)
        if (MyWorldManagerApi.isWorldSlotSystemEnabled()) {
            me.awabi2048.myworldmanager.util.GuiHelper.setThreeChoiceItems(
                inventory,
                templateItem,
                createCreationTypeItem(player, plugin.menuConfigManager.getIconMaterial("creation", "seed", Material.NAME_TAG), lang.getMessage("gui.creation.type.seed.name"), "gui.creation.type.seed.lore", WorldCreationType.SEED, ItemTag.TYPE_GUI_CREATION_TYPE_SEED),
                randomItem
            )
        } else {
            inventory.setItem(layout.leftSlot, templateItem)
            inventory.setItem(layout.rightSlot, randomItem)
        }

        me.awabi2048.myworldmanager.util.GuiHelper.setThreeChoiceBack(inventory, createBackButton(player))

        fillBackground(inventory)
        player.openInventory(inventory)
    }

    private fun createCreationTypeItem(
        player: Player,
        material: Material,
        name: String,
        baseLoreKey: String,
        creationType: WorldCreationType,
        tag: String
    ): ItemStack {
        val lang = plugin.languageManager
        val lore = lang.getMessageList(player, baseLoreKey)
            .map<String, GuiLoreLine>(GuiLoreLine::Text)
            .toMutableList()

        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        if (MyWorldManagerApi.isWorldPointEconomyEnabled()) {
            val cost = WorldRuntimePolicies.creationCost(plugin.config, creationType)
            lore.add(GuiLoreLine.Spacer)
            lore.add(
                GuiLoreLine.Data(
                    lang.getMessage(player, "gui.creation.type.cost_label"),
                    "§6🛖 §e$cost",
                    ""
                )
            )
            lore.add(
                GuiLoreLine.Data(
                    lang.getMessage(player, "gui.creation.type.current_points_label"),
                    "§6🛖 §e${stats.worldPoint}",
                    ""
                )
            )
            lore.add(
                GuiLoreLine.Data(
                    lang.getMessage(player, "gui.creation.type.remaining_points_label"),
                    "§6🛖 §e${(stats.worldPoint - cost).coerceAtLeast(0)}",
                    ""
                )
            )
            if (stats.worldPoint < cost) {
                lore.add(GuiLoreLine.Warning(
                    lang.getMessage(
                        player,
                        "gui.creation.type.insufficient",
                        mapOf("shortage" to (cost - stats.worldPoint))
                    ).removePrefix("§c")
                ))
            }
        }
        val defaultMax = WorldRuntimePolicies.maxCreateCountDefault(plugin.config)
        val maxCounts = defaultMax + stats.unlockedWorldSlot
        val currentCounts = plugin.worldConfigRepository.findAll().count { it.owner == player.uniqueId }
        val bypassLimits = PermissionManager.canBypassWorldLimits(player)

        if (!bypassLimits && currentCounts >= maxCounts) {
            lore.add(GuiLoreLine.Spacer)
            lore.add(GuiLoreLine.Warning(lang.getMessage(player, "gui.creation.limit_reached", mapOf("current" to currentCounts, "max" to maxCounts)).removePrefix("§c")))
        }

        return createItem(material, name, tag, GuiLoreSpec.Rich(lore, GuiLoreFrame.NONE))
    }

    fun openTemplateSelection(player: Player) {
        val lang = plugin.languageManager
        val titleKey = "gui.creation.title_template"
        if (!lang.hasKey(player, titleKey)) {
            player.sendMessage("§c[MyWorldManager] Error: Missing translation key: $titleKey")
            return
        }
        val title = me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(lang.getMessage(player, titleKey))
        me.awabi2048.myworldmanager.util.GuiHelper.playMenuOpen(player, "creation")

        val templates = plugin.templateRepository.findAll()
            .filter(plugin.templateRepository::isUsable)
        if (templates.isEmpty()) {
            player.sendMessage(lang.getMessage(player, "error.preview_template_not_found"))
            return
        }
        val worldsPerRow = 7
        val neededDataRows = (templates.size + worldsPerRow - 1) / worldsPerRow
        val rowCount = (neededDataRows + 2).coerceIn(3, 6)

        clearSettingsGuiTransition(player)
        val holder = CreationGuiHolder(CreationMenuType.TEMPLATE_SELECT)
        val inventory = Bukkit.createInventory(holder, rowCount * 9, title)
        holder.inv = inventory

        setupHeaderFooter(inventory, rowCount)

        // Fill data rows
        templates.take((rowCount - 2) * worldsPerRow).forEachIndexed { index, template ->
            val row = index / worldsPerRow
            val col = index % worldsPerRow
            val slot = (row + 1) * 9 + 1 + col
            val issue = plugin.templateRepository.validationIssue(template)
            val loreBuilder = GuiLoreBuilder(lang, player)
                .block(template.description.map(GuiLoreLine::Text))
                .data(
                    lang.getMessage(player, "gui.creation.template_detail.status_label"),
                    lang.getMessage(
                        player,
                        if (issue == null) {
                            "gui.creation.template_detail.status_available"
                        } else {
                            "gui.creation.template_detail.status_unavailable"
                        }
                    )
                )
            if (issue != null) {
                loreBuilder.warning(templateValidationMessage(player, issue))
            }
            loreBuilder.actions(lang.getMessage(player, "gui.creation.template_item.action.details"))

            val item = createItem(
                template.icon,
                template.name,
                ItemTag.TYPE_GUI_CREATION_TEMPLATE_ITEM,
                loreBuilder.buildSpec()
            )
            ItemTag.setTemplateId(item, template.id)
            inventory.setItem(slot, item)
        }

        inventory.setItem((rowCount - 1) * 9 + 4, createBackButton(player))

        fillBackground(inventory)
        player.openInventory(inventory)
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

        val title = me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(
            lang.getMessage(player, "gui.creation.template_detail.title")
        )
        val holder = CreationGuiHolder(CreationMenuType.TEMPLATE_DETAIL)
        val layout = me.awabi2048.myworldmanager.util.GuiHelper.threeChoiceLayout()
        val inventory = Bukkit.createInventory(holder, layout.size, title)
        holder.inv = inventory
        GuiItemFactory.applyStandardFrame(inventory)

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
        val detailItem = createItem(
            template.icon,
            template.name,
            ItemTag.TYPE_GUI_INFO,
            GuiLoreSpec.Rich(detailLines, GuiLoreFrame.BOTH)
        )
        inventory.setItem(layout.leftSlot, detailItem)

        if (issue == null) {
            inventory.setItem(
                layout.centerSlot,
                createItem(
                    Material.LIME_CONCRETE,
                    lang.getMessage(player, "gui.creation.template_detail.use"),
                    ItemTag.TYPE_GUI_CREATION_TEMPLATE_USE,
                    GuiLoreBuilder(lang, player)
                        .actions(lang.getMessage(player, "gui.creation.template_detail.use_action"))
                        .buildSpec()
                )
            )
            inventory.setItem(
                layout.rightSlot,
                createItem(
                    Material.ENDER_EYE,
                    lang.getMessage(player, "gui.creation.template_detail.preview"),
                    ItemTag.TYPE_GUI_CREATION_TEMPLATE_PREVIEW,
                    GuiLoreBuilder(lang, player)
                        .actions(lang.getMessage(player, "gui.creation.template_detail.preview_action"))
                        .buildSpec()
                )
            )
        }
        me.awabi2048.myworldmanager.util.GuiHelper.setThreeChoiceBack(inventory, createBackButton(player))
        fillBackground(inventory)
        player.openInventory(inventory)
    }

    fun openConfirmation(player: Player, session: WorldCreationSession) {
        val lang = plugin.languageManager
        val titleKey = "gui.creation.title_confirm"
        if (!lang.hasKey(player, titleKey)) {
            player.sendMessage("§c[MyWorldManager] Error: Missing translation key: $titleKey")
            return
        }
        if (MyWorldManagerApi.openCreationConfirmationMenuOverride(player, session)) {
            return
        }
        val title = me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(lang.getMessage(player, titleKey))
        me.awabi2048.myworldmanager.util.GuiHelper.playMenuOpen(player, "creation")

        clearSettingsGuiTransition(player)
        val holder = CreationGuiHolder(CreationMenuType.CONFIRM)
        val inventory = me.awabi2048.myworldmanager.util.GuiHelper.createConfirmationInventory(holder, title)
        holder.inv = inventory

        me.awabi2048.myworldmanager.util.GuiHelper.applyConfirmationFrame(inventory)

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

        me.awabi2048.myworldmanager.util.GuiHelper.setConfirmationItems(
            inventory,
            createItem(Material.PAPER, lang.getMessage(player, "gui.creation.confirm.name"), ItemTag.TYPE_GUI_INFO, infoLore),
            createItem(
                Material.LIME_CONCRETE,
                lang.getMessage(player, "gui.common.confirm"),
                ItemTag.TYPE_GUI_CONFIRM,
                GuiLoreSpec.None
            ),
            createItem(
                Material.RED_CONCRETE,
                lang.getMessage(player, "gui.common.cancel"),
                ItemTag.TYPE_GUI_CANCEL,
                GuiLoreSpec.None
            )
        )

        if (session.creationType == WorldCreationType.SEED) {
            inventory.setItem(
                SEED_DIMENSION_SLOT,
                createItem(
                    seedEnvironmentMaterial(session.seedEnvironment),
                    lang.getMessage(player, "gui.creation.confirm.dimension.display"),
                    ItemTag.TYPE_GUI_CREATION_DIMENSION,
                    seedEnvironmentLore(player, session.seedEnvironment)
                )
            )

            val coordinates = session.spawnCoordinates?.let {
                "§6(${it.x}, ${it.y}, ${it.z})"
            } ?: "§b${lang.getMessage(player, "gui.creation.confirm.spawn_location.default")}"
            val spawnLore = GuiLoreSpec.Blocks(
                listOf(
                    GuiLoreBlock(
                        listOf(
                            GuiLoreLine.Text(lang.getMessage(player, "gui.creation.confirm.spawn_location.description")),
                            GuiLoreLine.Text(lang.getMessage(player, "gui.creation.confirm.spawn_location.default_help")),
                            GuiLoreLine.Spacer,
                            GuiLoreLine.Data(lang.getMessage(player, "gui.creation.confirm.spawn_location.current_label"), coordinates, ""),
                            GuiLoreActions.singleClick(
                                lang,
                                player,
                                lang.getMessage(player, "gui.creation.confirm.spawn_location.action")
                            )
                        )
                    )
                )
            )
            inventory.setItem(
                SEED_SPAWN_LOCATION_SLOT,
                createItem(
                    Material.COMPASS,
                    lang.getMessage(player, "gui.creation.confirm.spawn_location.display"),
                    ItemTag.TYPE_GUI_CREATION_SPAWN_LOCATION,
                    spawnLore
                )
            )
        } else if (session.creationType == WorldCreationType.TEMPLATE) {
            inventory.setItem(
                39,
                createItem(
                    Material.ENDER_EYE,
                    lang.getMessage(player, "gui.creation.template_detail.preview"),
                    ItemTag.TYPE_GUI_CREATION_TEMPLATE_PREVIEW,
                    GuiLoreSpec.None
                )
            )
            inventory.setItem(
                40,
                createItem(
                    Material.NAME_TAG,
                    lang.getMessage(player, "gui.creation.confirm.change_name"),
                    ItemTag.TYPE_GUI_BACK,
                    GuiLoreSpec.None
                )
            )
            inventory.setItem(
                41,
                createItem(
                    Material.MAP,
                    lang.getMessage(player, "gui.creation.confirm.change_template"),
                    ItemTag.TYPE_GUI_CREATION_TEMPLATE_CHANGE,
                    GuiLoreSpec.None
                )
            )
        }

        player.openInventory(inventory)
    }

    private fun setupHeaderFooter(inventory: org.bukkit.inventory.Inventory, rowCount: Int) {
        val greyPane = GuiItemFactory.decoration(Material.GRAY_STAINED_GLASS_PANE)

        GuiItemFactory.applyStandardFrame(inventory, emptyMaterial = null)

        for (i in 1 until rowCount - 1) {
            inventory.setItem(i * 9, greyPane)
            inventory.setItem(i * 9 + 8, greyPane)
        }
    }

    private fun createBackButton(player: Player): ItemStack {
        val lang = plugin.languageManager
        return createItem(
            plugin.menuConfigManager.getIconMaterial("creation", "back", Material.REDSTONE),
            lang.getMessage(player, "gui.common.return"),
            ItemTag.TYPE_GUI_BACK,
            GuiLoreSpec.None
        )
    }

    private fun fillBackground(inventory: org.bukkit.inventory.Inventory) {
        GuiItemFactory.fillEmpty(inventory)
    }

    private fun createItem(material: Material, name: String, tag: String, lore: GuiLoreSpec): ItemStack {
        return GuiItemFactory.item(material, name, lore, tag)
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
        plugin.settingsSessionManager.getSession(player)?.isGuiTransition = false
    }

    enum class CreationMenuType {
        TYPE_SELECT,
        TEMPLATE_SELECT,
        TEMPLATE_DETAIL,
        CONFIRM
    }

    companion object {
        const val SEED_DIMENSION_SLOT = 39
        const val SEED_SPAWN_LOCATION_SLOT = 40
    }

    class CreationGuiHolder(val menuType: CreationMenuType) : InventoryHolder {
        lateinit var inv: Inventory

        override fun getInventory(): Inventory = inv
    }
}
