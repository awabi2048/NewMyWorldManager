package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.CCSystem
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.api.extension.MenuExtensionContext
import me.awabi2048.myworldmanager.model.*
import me.awabi2048.myworldmanager.repository.*
import me.awabi2048.myworldmanager.session.*
import me.awabi2048.myworldmanager.util.GuiItemFactory
import me.awabi2048.myworldmanager.util.ItemTag
import me.awabi2048.myworldmanager.util.PermissionManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID

class CreationGui(private val plugin: MyWorldManager) {
    
    fun openTypeSelection(player: Player) {
        val config = plugin.config
        val lang = plugin.languageManager
        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        val currentPoints = stats.worldPoint
        val bypassLimits = PermissionManager.canBypassWorldLimits(player)

        // サーバー全体の上限チェック
        val totalMax = config.getInt("creation.max_total_world_count", 50)
        val totalCurrent = plugin.worldConfigRepository.findAll().size
        if (!bypassLimits && totalCurrent >= totalMax) {
            player.sendMessage(lang.getMessage(player, "gui.creation.limit_reached_total", mapOf("max" to totalMax)))
            plugin.soundManager.playClickSound(player, ItemStack(Material.BARRIER)) // エラー音の代わり
            return
        }

        // プレイヤー個別の上限チェック
        val defaultMax = config.getInt("creation.max_create_count_default", 3)
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
        me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(plugin, player, "creation", title)
        val inventory = Bukkit.createInventory(null, 45, title)

        setupHeaderFooter(inventory, 5)

        inventory.setItem(20, createItemWithCost(player, plugin.menuConfigManager.getIconMaterial("creation", "template", Material.MAP), lang.getMessage("gui.creation.type.template.name"), "gui.creation.type.template.lore", config.getInt("creation_cost.template", 0), currentPoints, ItemTag.TYPE_GUI_CREATION_TYPE_TEMPLATE))
        inventory.setItem(22, createItemWithCost(player, plugin.menuConfigManager.getIconMaterial("creation", "seed", Material.NAME_TAG), lang.getMessage("gui.creation.type.seed.name"), "gui.creation.type.seed.lore", config.getInt("creation_cost.seed", 100), currentPoints, ItemTag.TYPE_GUI_CREATION_TYPE_SEED))
        inventory.setItem(24, createItemWithCost(player, plugin.menuConfigManager.getIconMaterial("creation", "random", Material.ENDER_EYE), lang.getMessage("gui.creation.type.random.name"), "gui.creation.type.random.lore", config.getInt("creation_cost.random", 50), currentPoints, ItemTag.TYPE_GUI_CREATION_TYPE_RANDOM))

        inventory.setItem(40, createBackButton(player))

        fillBackground(inventory)
        player.openInventory(inventory)
    }

    private fun createItemWithCost(player: Player, material: Material, name: String, baseLoreKey: String, cost: Int, currentPoints: Int, tag: String): ItemStack {
        val lang = plugin.languageManager
        val lore = lang.getComponentList(player, baseLoreKey).toMutableList()
        lore.add(Component.empty())
        lore.add(lang.getComponent(player, "gui.creation.type.cost", mapOf("cost" to cost)).decoration(TextDecoration.ITALIC, false))
        lore.add(lang.getComponent(player, "gui.creation.type.points", mapOf("points" to currentPoints)).decoration(TextDecoration.ITALIC, false))
        
        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        val defaultMax = plugin.config.getInt("creation.max_create_count_default", 3)
        val maxCounts = defaultMax + stats.unlockedWorldSlot
        val currentCounts = plugin.worldConfigRepository.findAll().count { it.owner == player.uniqueId }
        val bypassLimits = PermissionManager.canBypassWorldLimits(player)

        if (!bypassLimits && currentCounts >= maxCounts) {
            lore.add(lang.getComponent(player, "gui.creation.limit_reached", mapOf("current" to currentCounts, "max" to maxCounts)).decoration(TextDecoration.ITALIC, false))
        } else if (currentPoints >= cost) {
            lore.add(lang.getComponent(player, "gui.creation.type.available").decoration(TextDecoration.ITALIC, false))
        } else {
            lore.add(lang.getComponent(player, "gui.creation.type.insufficient", mapOf("shortage" to (cost - currentPoints))).decoration(TextDecoration.ITALIC, false))
        }
        
        return createItem(material, name, tag, lore)
    }

    fun openTemplateSelection(player: Player) {
        val lang = plugin.languageManager
        val titleKey = "gui.creation.title_template"
        if (!lang.hasKey(player, titleKey)) {
            player.sendMessage("§c[MyWorldManager] Error: Missing translation key: $titleKey")
            return
        }
        val title = me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(lang.getMessage(player, titleKey))
        me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(plugin, player, "creation", title)
        
        val templates = plugin.templateRepository.findAll()
        if (templates.isEmpty()) {
            player.sendMessage(lang.getMessage(player, "error.preview_template_not_found"))
            return
        }
        val worldsPerRow = 7
        val neededDataRows = (templates.size + worldsPerRow - 1) / worldsPerRow
        val rowCount = (neededDataRows + 2).coerceIn(3, 6)
        
        val inventory = Bukkit.createInventory(null, rowCount * 9, title)

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
        me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(plugin, player, "creation", title)
        
        val inventory = Bukkit.createInventory(null, 45, title)
        
        me.awabi2048.myworldmanager.util.GuiHelper.applyConfirmationFrame(inventory)

        val cleanedName = cleanWorldName(session.worldName ?: lang.getMessage(player, "general.unknown"))
        val generationLine = when (session.creationType) {
            WorldCreationType.TEMPLATE -> {
                val template = plugin.templateRepository.findAll().find { it.path == session.templateName }
                val displayName = template?.name ?: (session.templateName ?: lang.getMessage(player, "general.unknown"))
                CCSystem.getAPI().createLoreSubDataLine("テンプレート", displayName)
            }
            WorldCreationType.SEED -> CCSystem.getAPI().createLoreSubDataLine("シード値", session.inputSeedString ?: "")
            WorldCreationType.RANDOM -> CCSystem.getAPI().createLoreSubDataLine("生成方法", "ランダム")
            null -> CCSystem.getAPI().createLoreSubDataLine("生成方法", lang.getMessage(player, "general.unknown"))
        }

        val infoLore = CCSystem.getAPI().buildLore(
            listOf(
                CCSystem.getAPI().createLoreDataLine("ワールド名", cleanedName, "§e"),
                generationLine
            )
        )

        inventory.setItem(22, createItem(Material.PAPER, lang.getMessage(player, "gui.creation.confirm.name"), ItemTag.TYPE_GUI_INFO, infoLore))

        inventory.setItem(
            20,
            createItem(
                Material.LIME_CONCRETE,
                lang.getMessage(player, "gui.common.confirm"),
                ItemTag.TYPE_GUI_CONFIRM,
                listOf(lang.getComponent(player, "gui.common.confirm_desc").decoration(TextDecoration.ITALIC, false))
            )
        )

        inventory.setItem(
            24,
            createItem(
                Material.RED_CONCRETE,
                lang.getMessage(player, "gui.common.cancel"),
                ItemTag.TYPE_GUI_CANCEL,
                listOf(lang.getComponent(player, "gui.common.cancel_desc").decoration(TextDecoration.ITALIC, false))
            )
        )

        val context = MenuExtensionContext(
            "creation_confirm",
            mutableMapOf(
                "session" to session,
                "worldName" to cleanedName,
                "creationType" to session.creationType
            )
        )
        MyWorldManagerApi.getMenuExtensions().forEach { extension ->
            extension.onRender(inventory, player, context)
        }

        player.openInventory(inventory)
    }

    private fun setupHeaderFooter(inventory: org.bukkit.inventory.Inventory, rowCount: Int) {
        val blackPane = GuiItemFactory.decoration(Material.BLACK_STAINED_GLASS_PANE)
        val greyPane = GuiItemFactory.decoration(Material.GRAY_STAINED_GLASS_PANE)

        for (i in 0..8) inventory.setItem(i, blackPane)
        for (i in (rowCount - 1) * 9 until rowCount * 9) inventory.setItem(i, blackPane)
        
        for (i in 1 until rowCount - 1) {
            inventory.setItem(i * 9, greyPane)
            inventory.setItem(i * 9 + 8, greyPane)
        }
    }

    private fun createBackButton(player: Player): ItemStack {
        val lang = plugin.languageManager
        val lore = listOf(LegacyComponentSerializer.legacySection().deserialize(lang.getMessage(player, "gui.common.return_desc")).decoration(TextDecoration.ITALIC, false))
        return createItem(plugin.menuConfigManager.getIconMaterial("creation", "back", Material.ARROW), lang.getMessage(player, "gui.common.return"), ItemTag.TYPE_GUI_BACK, lore)
    }

    private fun fillBackground(inventory: org.bukkit.inventory.Inventory) {
        val item = GuiItemFactory.decoration(Material.GRAY_STAINED_GLASS_PANE)

        for (i in 0 until inventory.size) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, item)
            }
        }
    }

    private fun createItem(material: Material, name: String, tag: String, lore: List<Component>): ItemStack {
        return GuiItemFactory.item(material, name, lore, tag)
    }

    private fun cleanWorldName(name: String): String {
        // 括弧内の文字列（ローマ字変換など）を削除
        val regex = Regex("\\s?\\(.*?\\)")
        return name.replace(regex, "").trim()
    }
}
