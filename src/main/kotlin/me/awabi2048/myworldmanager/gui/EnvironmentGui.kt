package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuActionContext
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuRuntimeActions
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import com.awabi2048.ccsystem.api.gui.PlayerInventoryInteraction
import java.util.UUID
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.session.SettingsAction
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.GuiItemFactory
import me.awabi2048.myworldmanager.util.ItemTag
import me.awabi2048.myworldmanager.util.WorldRuntimePolicies
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class EnvironmentGui(private val plugin: MyWorldManager) {
    private val runtime = CCSystem.getAPI().getMenuRuntimeService()

    init {
        runtime.register(
            InventoryMenuDefinition(
                owner = OWNER,
                id = ROUTE_ID,
                renderer = { context -> render(context.player, context.route) },
                actions = mapOf(
                    ACTION_GRAVITY to MenuActionHandler(::gravity),
                    ACTION_WEATHER to MenuActionHandler(::weather),
                    ACTION_BIOME to MenuActionHandler(::biome),
                    ACTION_BACK to MenuActionHandler(::back),
                    MenuRuntimeActions.PLAYER_INVENTORY_CLICK to
                        MenuActionHandler(::selectPlayerInventoryItem),
                ),
            ),
        )
    }

    fun open(player: Player, worldData: WorldData) {
        runtime.navigate(player, prepareOpen(player, worldData))
    }

    fun prepareOpen(player: Player, worldData: WorldData): MenuRoute {
        plugin.settingsSessionManager.updateSessionAction(
            player,
            worldData.uuid,
            SettingsAction.VIEW_ENVIRONMENT_SETTINGS,
            isGui = true,
        )
        return route(worldData.uuid)
    }

    private fun render(player: Player, route: MenuRoute): InventoryMenuView {
        val worldData = worldData(route)
        val layout = GuiHelper.threeChoiceLayout()
        return InventoryMenuView(
            size = layout.size,
            title = GuiHelper.inventoryTitle(
                plugin.languageManager.getMessage(player, "gui.environment.title"),
            ),
            elements = listOf(
                MenuElement(layout.leftSlot, createGravityItem(player, worldData), GuiElementRole.ACTION, ACTION_GRAVITY),
                MenuElement(layout.centerSlot, createWeatherItem(player, worldData), GuiElementRole.ACTION, ACTION_WEATHER),
                MenuElement(layout.rightSlot, createBiomeItem(player, worldData), GuiElementRole.ACTION, ACTION_BIOME),
                MenuElement(layout.backSlot, createBackItem(player), GuiElementRole.BACK, ACTION_BACK),
            ),
            playerInventoryInteraction = PlayerInventoryInteraction.SELECTION,
        )
    }

    private fun gravity(context: MenuActionContext): MenuActionResult {
        val worldData = worldData(context.route)
        val cost = WorldRuntimePolicies.environmentCost(plugin.config, "gravity")
        plugin.worldSettingsListener.showEnvironmentConfirmDialog(context.player, worldData, "gravity", cost)
        return MenuActionResult.Success(MenuUpdate.None)
    }

    private fun weather(context: MenuActionContext): MenuActionResult {
        val worldData = worldData(context.route)
        return when {
            context.click.isLeftClick -> {
                plugin.worldSettingsListener.cycleEnvironmentWeather(context.player, worldData)
                MenuActionResult.Success(MenuUpdate.Refresh)
            }
            context.click.isRightClick -> {
                val cost = WorldRuntimePolicies.environmentCost(plugin.config, "weather")
                plugin.worldSettingsListener.showEnvironmentConfirmDialog(context.player, worldData, "weather", cost)
                MenuActionResult.Success(MenuUpdate.None)
            }
            else -> MenuActionResult.Ignored
        }
    }

    private fun biome(context: MenuActionContext): MenuActionResult {
        context.player.sendMessage(
            plugin.languageManager.getMessage(context.player, "gui.environment.biome.click_bottle_hint"),
        )
        return MenuActionResult.Success(MenuUpdate.None)
    }

    private fun back(context: MenuActionContext): MenuActionResult {
        return MenuActionResult.Success(MenuUpdate.Back)
    }

    private fun selectPlayerInventoryItem(context: MenuActionContext): MenuActionResult {
        val player = context.player
        val session = plugin.settingsSessionManager.getSession(player)
            ?: return MenuActionResult.Ignored
        if (session.action != SettingsAction.VIEW_ENVIRONMENT_SETTINGS) {
            return MenuActionResult.Ignored
        }
        val clickedItem = context.item
        if (clickedItem.type == Material.AIR) return MenuActionResult.Ignored
        val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid)
            ?: return MenuActionResult.Ignored
        when {
            ItemTag.isType(clickedItem, ItemTag.TYPE_MOON_STONE) -> {
                session.confirmItem = clickedItem.clone()
                val cost = WorldRuntimePolicies.environmentCost(plugin.config, "gravity")
                showConfirmationNextTick(player, worldData, "gravity", cost)
                return MenuActionResult.Success(MenuUpdate.Close)
            }
            ItemTag.isType(clickedItem, ItemTag.TYPE_BOTTLED_BIOME_AIR) -> {
                validateBiomeBottleUse(player, worldData, session.isAdminFlow)?.let { message ->
                    return MenuActionResult.Rejected(message)
                }
                val biomeId = ItemTag.getBiomeId(clickedItem) ?: return MenuActionResult.Ignored
                session.confirmItem = clickedItem.clone()
                session.tempBiomeId = biomeId
                val cost = WorldRuntimePolicies.environmentCost(plugin.config, "biome")
                showConfirmationNextTick(player, worldData, "biome", cost)
                return MenuActionResult.Success(MenuUpdate.Close)
            }
        }
        return MenuActionResult.Ignored
    }

    private fun showConfirmationNextTick(player: Player, worldData: WorldData, type: String, cost: Int) {
        Bukkit.getScheduler().runTask(
            plugin,
            Runnable { plugin.worldSettingsListener.showEnvironmentConfirmDialog(player, worldData, type, cost) },
        )
    }

    private fun validateBiomeBottleUse(
        player: Player,
        worldData: WorldData,
        isAdminFlow: Boolean,
    ): net.kyori.adventure.text.Component? {
        if (worldData.customWorldName != null) {
            return plugin.languageManager.getComponent(
                player,
                "messages.custom_item.biome_bottle_disabled",
            )
        }
        val isMember = player.uniqueId == worldData.owner ||
            player.uniqueId in worldData.moderators ||
            player.uniqueId in worldData.members ||
            isAdminFlow
        if (!isMember) {
            return plugin.languageManager.getComponent(player, "error.custom_item.no_permission")
        }
        return null
    }

    private fun createGravityItem(player: Player, worldData: WorldData): ItemStack {
        val lang = plugin.languageManager
        val item = ItemStack(Material.FEATHER)
        val meta = item.itemMeta ?: return item
        val gravityKey = when (worldData.gravityValue ?: 0.08) {
            0.01 -> "moon"
            0.02 -> "mars"
            else -> "earth"
        }
        val currentName = lang.getMessage(player, "gui.environment.gravity.options.$gravityKey")
        val cost = WorldRuntimePolicies.environmentCost(plugin.config, "gravity")
        meta.displayName(lang.getComponent(player, "gui.environment.gravity.display"))
        meta.lore(GuiItemFactory.menuLore(buildList {
            add(GuiLoreLine.Data(lang.getMessage(player, "gui.environment.gravity.current"), currentName, "§6"))
            if (MyWorldManagerApi.isWorldPointEconomyEnabled()) {
                add(GuiLoreLine.Data(lang.getMessage(player, "gui.environment.gravity.cost"), cost, "§e"))
            }
            add(GuiLoreLine.Spacer)
            add(GuiLoreLine.Text(lang.getMessage(player, "gui.environment.gravity.requirement")))
            add(
                CCSystem.getAPI().getGuiActionService().singleClick(
                    player,
                    lang.getMessage(player, "gui.environment.gravity.action"),
                ),
            )
        }))
        item.itemMeta = meta
        return item
    }

    private fun createWeatherItem(player: Player, worldData: WorldData): ItemStack {
        val lang = plugin.languageManager
        val item = ItemStack(Material.WHITE_WOOL)
        val meta = item.itemMeta ?: return item
        val session = plugin.settingsSessionManager.getSession(player)
        val currentWeather = session?.tempWeather ?: worldData.fixedWeather ?: "DEFAULT"
        val cost = WorldRuntimePolicies.environmentCost(plugin.config, "weather")
        meta.displayName(lang.getComponent(player, "gui.environment.weather.display"))
        meta.lore(GuiItemFactory.menuLore(buildList {
            add(GuiLoreLine.Text(lang.getMessage(player, "gui.environment.weather.desc")))
            add(GuiLoreLine.Data(lang.getMessage(player, "gui.environment.weather.current"), currentWeather, "§b"))
            if (MyWorldManagerApi.isWorldPointEconomyEnabled()) {
                add(GuiLoreLine.Data(lang.getMessage(player, "gui.environment.weather.cost"), cost, "§e"))
            }
            add(GuiLoreLine.Spacer)
            add(GuiLoreLine.Action(lang.getMessage(player, "lore.click.left"), lang.getMessage(player, "gui.environment.weather.action.cycle")))
            add(GuiLoreLine.Action(lang.getMessage(player, "lore.click.right"), lang.getMessage(player, "gui.environment.weather.action.confirm")))
        }))
        item.itemMeta = meta
        return item
    }

    private fun createBiomeItem(player: Player, worldData: WorldData): ItemStack {
        val lang = plugin.languageManager
        val item = ItemStack(Material.GRASS_BLOCK)
        val meta = item.itemMeta ?: return item
        val currentBiome = worldData.fixedBiome ?: "DEFAULT"
        val cost = WorldRuntimePolicies.environmentCost(plugin.config, "biome")
        meta.displayName(lang.getComponent(player, "gui.environment.biome.display"))
        meta.lore(GuiItemFactory.menuLore(buildList {
            add(GuiLoreLine.Text(lang.getMessage(player, "gui.environment.biome.desc")))
            add(GuiLoreLine.Data(lang.getMessage(player, "gui.environment.biome.current"), currentBiome, "§a"))
            if (MyWorldManagerApi.isWorldPointEconomyEnabled()) {
                add(GuiLoreLine.Data(lang.getMessage(player, "gui.environment.biome.cost"), cost, "§e"))
            }
            add(GuiLoreLine.Spacer)
            add(GuiLoreLine.Text(lang.getMessage(player, "gui.environment.biome.requirement")))
            add(
                CCSystem.getAPI().getGuiActionService().singleClick(
                    player,
                    lang.getMessage(player, "gui.environment.biome.action"),
                ),
            )
        }))
        item.itemMeta = meta
        return item
    }

    private fun createBackItem(player: Player): ItemStack {
        val item = ItemStack(Material.REDSTONE)
        item.itemMeta = item.itemMeta?.also {
            it.displayName(plugin.languageManager.getComponent(player, "gui.common.back"))
        }
        return item
    }

    private fun worldData(route: MenuRoute): WorldData {
        val uuid = route.payload[WORLD_UUID]?.let(UUID::fromString)
            ?: error("環境設定のワールドUUIDがありません")
        return plugin.worldConfigRepository.findByUuid(uuid)
            ?: error("環境設定のワールドが見つかりません: $uuid")
    }

    private fun route(worldUuid: UUID) = MenuRoute(OWNER, ROUTE_ID, mapOf(WORLD_UUID to worldUuid.toString()))

    companion object {
        private const val OWNER = "myworldmanager"
        private const val ROUTE_ID = "environment"
        private const val WORLD_UUID = "worldUuid"
        private const val ACTION_GRAVITY = "gravity"
        private const val ACTION_WEATHER = "weather"
        private const val ACTION_BIOME = "biome"
        private const val ACTION_BACK = "back"
    }
}
