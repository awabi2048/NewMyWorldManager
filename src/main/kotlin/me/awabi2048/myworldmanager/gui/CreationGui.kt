package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.api.extension.CreationConfirmationLayout
import me.awabi2048.myworldmanager.api.extension.MenuExtensionContext
import me.awabi2048.myworldmanager.model.*
import me.awabi2048.myworldmanager.repository.*
import me.awabi2048.myworldmanager.session.*
import me.awabi2048.myworldmanager.util.GuiItemFactory
import me.awabi2048.myworldmanager.util.ItemTag
import me.awabi2048.myworldmanager.util.PermissionManager
import me.awabi2048.myworldmanager.util.WorldRuntimePolicies
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
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
        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        val bypassLimits = PermissionManager.canBypassWorldLimits(player)
        if (!bypassLimits && !MyWorldManagerApi.isWorldCreationEnabled()) {
            player.sendMessage(lang.getMessage(player, "gui.creation.period_disabled"))
            plugin.soundManager.playClickSound(player, ItemStack(Material.BARRIER))
            return
        }

        // サーバー全体の上限チェック
        val totalMax = config.getInt("creation.max_total_world_count", 50)
        val totalCurrent = plugin.worldConfigRepository.findAll().size
        if (!bypassLimits && totalCurrent >= totalMax) {
            player.sendMessage(lang.getMessage(player, "gui.creation.limit_reached_total", mapOf("max" to totalMax)))
            plugin.soundManager.playClickSound(player, ItemStack(Material.BARRIER)) // エラー音の代わり
            return
        }

        // プレイヤー個別の上限チェック
        val defaultMax = WorldRuntimePolicies.maxCreateCountDefault(config)
        val playerMax = defaultMax + stats.unlockedWorldSlot
        val playerCurrent = plugin.worldConfigRepository.findAll().count { it.owner == player.uniqueId }
        if (!bypassLimits && playerCurrent >= playerMax) {
            player.sendMessage(lang.getMessage(player, "gui.creation.limit_reached", mapOf("current" to playerCurrent, "max" to playerMax)))
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

        me.awabi2048.myworldmanager.util.GuiHelper.setThreeChoiceItems(
            inventory,
            createCreationTypeItem(player, plugin.menuConfigManager.getIconMaterial("creation", "template", Material.MAP), lang.getMessage("gui.creation.type.template.name"), "gui.creation.type.template.lore", ItemTag.TYPE_GUI_CREATION_TYPE_TEMPLATE),
            createCreationTypeItem(player, plugin.menuConfigManager.getIconMaterial("creation", "seed", Material.NAME_TAG), lang.getMessage("gui.creation.type.seed.name"), "gui.creation.type.seed.lore", ItemTag.TYPE_GUI_CREATION_TYPE_SEED),
            createCreationTypeItem(player, plugin.menuConfigManager.getIconMaterial("creation", "random", Material.ENDER_EYE), lang.getMessage("gui.creation.type.random.name"), "gui.creation.type.random.lore", ItemTag.TYPE_GUI_CREATION_TYPE_RANDOM)
        )

        me.awabi2048.myworldmanager.util.GuiHelper.setThreeChoiceBack(inventory, createBackButton(player))

        fillBackground(inventory)
        player.openInventory(inventory)
    }

    private fun createCreationTypeItem(player: Player, material: Material, name: String, baseLoreKey: String, tag: String): ItemStack {
        val lang = plugin.languageManager
        val lore = lang.getMessageList(player, baseLoreKey)
            .map<String, GuiLoreLine> { GuiLoreLine.Text(it.replace(Regex("(?i)^[§&][0-9A-FK-ORX]"), "")) }
            .toMutableList()

        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
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
            val lore = mutableListOf<Component>()
            val desc = template.description // formatted in repo? assuming list of strings
            desc.forEach { line ->
                 lore.add(LegacyComponentSerializer.legacySection().deserialize(line).decoration(TextDecoration.ITALIC, false))
            }
            lore.add(Component.empty())
            lore.add(lang.getComponent(player, "gui.creation.template_item.preview_hint").decoration(TextDecoration.ITALIC, false))

            inventory.setItem(slot, createItem(template.icon, template.name, ItemTag.TYPE_GUI_CREATION_TEMPLATE_ITEM, lore))
        }

        inventory.setItem((rowCount - 1) * 9 + 4, createBackButton(player))

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
        val title = me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(lang.getMessage(player, titleKey))
        me.awabi2048.myworldmanager.util.GuiHelper.playMenuOpen(player, "creation")

        clearSettingsGuiTransition(player)
        val holder = CreationGuiHolder(CreationMenuType.CONFIRM)
        val inventory = me.awabi2048.myworldmanager.util.GuiHelper.createConfirmationInventory(holder, title)
        holder.inv = inventory

        me.awabi2048.myworldmanager.util.GuiHelper.applyConfirmationFrame(inventory)

        val layout = CreationConfirmationLayout()
        val context = MenuExtensionContext(
            "creation_confirm",
            mutableMapOf(
                "session" to session,
                "worldName" to cleanWorldName(session.worldName ?: lang.getMessage(player, "general.unknown")),
                "creationType" to session.creationType,
                "layout" to layout
            )
        )
        // アドオンには作成処理を渡さず、コア項目と競合する表示位置だけを事前調整させる。
        MyWorldManagerApi.getMenuExtensions().forEach { extension ->
            extension.onPrepare(context)
        }

        val cleanedName = cleanWorldName(session.worldName ?: lang.getMessage(player, "general.unknown"))
        val generationLine: GuiLoreLine = when (session.creationType) {
            WorldCreationType.TEMPLATE -> {
                val template = plugin.templateRepository.findAll().find { it.path == session.templateName }
                val displayName = template?.name ?: (session.templateName ?: lang.getMessage(player, "general.unknown"))
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
                listOf(
                    GuiLoreLine.Data(
                        lang.getMessage(player, "gui.creation.confirm.world_name_label"),
                        cleanedName,
                        "§e"
                    ),
                    generationLine
                ),
                GuiLoreFrame.BOTH
        )

        me.awabi2048.myworldmanager.util.GuiHelper.setConfirmationItems(
            inventory,
            createItem(Material.PAPER, lang.getMessage(player, "gui.creation.confirm.name"), ItemTag.TYPE_GUI_INFO, infoLore),
            createItem(
                Material.LIME_CONCRETE,
                lang.getMessage(player, "gui.common.confirm"),
                ItemTag.TYPE_GUI_CONFIRM,
                emptyList()
            ),
            createItem(
                Material.RED_CONCRETE,
                lang.getMessage(player, "gui.common.cancel"),
                ItemTag.TYPE_GUI_CANCEL,
                emptyList()
            )
        )

        if (session.creationType == WorldCreationType.SEED) {
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
                            GuiLoreLine.Raw("§f❙ §7${lang.getMessage(player, "gui.creation.confirm.spawn_location.current_label")} $coordinates"),
                            GuiLoreLine.SingleAction(lang.getMessage(player, "gui.creation.confirm.spawn_location.action"))
                        )
                    )
                )
            )
            inventory.setItem(
                layout.spawnLocationSlot,
                createItem(
                    Material.COMPASS,
                    lang.getMessage(player, "gui.creation.confirm.spawn_location.display"),
                    ItemTag.TYPE_GUI_CREATION_SPAWN_LOCATION,
                    spawnLore
                )
            )
        }

        MyWorldManagerApi.getMenuExtensions().forEach { extension ->
            extension.onRender(inventory, player, context)
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
            plugin.menuConfigManager.getIconMaterial("creation", "back", Material.ARROW),
            lang.getMessage(player, "gui.common.return"),
            ItemTag.TYPE_GUI_BACK,
            emptyList()
        )
    }

    private fun fillBackground(inventory: org.bukkit.inventory.Inventory) {
        GuiItemFactory.fillEmpty(inventory)
    }

    private fun createItem(material: Material, name: String, tag: String, lore: List<Component>): ItemStack {
        return GuiItemFactory.item(material, name, lore, tag)
    }

    private fun createItem(material: Material, name: String, tag: String, lore: GuiLoreSpec): ItemStack {
        return GuiItemFactory.item(material, name, lore, tag)
    }

    private fun cleanWorldName(name: String): String {
        // 括弧内の文字列（ローマ字変換など）を削除
        val regex = Regex("\\s?\\(.*?\\)")
        return name.replace(regex, "").trim()
    }

    private fun clearSettingsGuiTransition(player: Player) {
        plugin.settingsSessionManager.getSession(player)?.isGuiTransition = false
    }

    enum class CreationMenuType {
        TYPE_SELECT,
        TEMPLATE_SELECT,
        CONFIRM
    }

    class CreationGuiHolder(val menuType: CreationMenuType) : InventoryHolder {
        lateinit var inv: Inventory

        override fun getInventory(): Inventory = inv
    }
}
