package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryAction
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryData
import com.awabi2048.ccsystem.api.gui.GuiMenuEntrySpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiValueTone
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuActionContext
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuAcceptedClicks
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
                createGravityEntry(player, worldData, layout.leftSlot),
                createWeatherEntry(player, worldData, layout.centerSlot),
                createBiomeEntry(player, worldData, layout.rightSlot),
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

    private fun createGravityEntry(player: Player, worldData: WorldData, slot: Int): MenuElement {
        val lang = plugin.languageManager
        val gravityKey = when (worldData.gravityValue ?: 0.08) {
            0.01 -> "moon"
            0.02 -> "mars"
            else -> "earth"
        }
        val currentName = lang.getMessage(player, "gui.environment.gravity.options.$gravityKey")
        val cost = WorldRuntimePolicies.environmentCost(plugin.config, "gravity")
        return menuEntry(
            player,
            GuiMenuEntrySpec(
                slot = slot,
                material = Material.FEATHER,
                name = GuiNameSpec.Component(lang.getComponent(player, "gui.environment.gravity.display")),
                role = GuiElementRole.ACTION,
                description = listOf(lang.getMessage(player, "gui.environment.gravity.requirement")),
                data = buildList {
                    add(GuiMenuEntryData(lang.getMessage(player, "gui.environment.gravity.current"), currentName, GuiValueTone.WARNING))
                    if (MyWorldManagerApi.isWorldPointEconomyEnabled()) {
                        add(GuiMenuEntryData(lang.getMessage(player, "gui.environment.gravity.cost"), cost, GuiValueTone.WARNING))
                    }
                },
                actions = listOf(
                    GuiMenuEntryAction(
                        ACTION_GRAVITY,
                        MenuAcceptedClicks.LEFT_RIGHT,
                        lang.getMessage(player, "gui.environment.gravity.action"),
                    ),
                ),
            ),
        )
    }

    private fun createWeatherEntry(player: Player, worldData: WorldData, slot: Int): MenuElement {
        val lang = plugin.languageManager
        val session = plugin.settingsSessionManager.getSession(player)
        val currentWeather = session?.tempWeather ?: worldData.fixedWeather ?: "DEFAULT"
        val cost = WorldRuntimePolicies.environmentCost(plugin.config, "weather")
        return menuEntry(
            player,
            GuiMenuEntrySpec(
                slot = slot,
                material = Material.WHITE_WOOL,
                name = GuiNameSpec.Component(lang.getComponent(player, "gui.environment.weather.display")),
                role = GuiElementRole.ACTION,
                description = listOf(lang.getMessage(player, "gui.environment.weather.desc")),
                data = buildList {
                    add(GuiMenuEntryData(lang.getMessage(player, "gui.environment.weather.current"), currentWeather, GuiValueTone.INFO))
                    if (MyWorldManagerApi.isWorldPointEconomyEnabled()) {
                        add(GuiMenuEntryData(lang.getMessage(player, "gui.environment.weather.cost"), cost, GuiValueTone.WARNING))
                    }
                },
                actions = listOf(
                    GuiMenuEntryAction(
                        ACTION_WEATHER,
                        MenuAcceptedClicks.LEFT,
                        lang.getMessage(player, "gui.environment.weather.action.cycle"),
                    ),
                    GuiMenuEntryAction(
                        ACTION_WEATHER,
                        MenuAcceptedClicks.RIGHT,
                        lang.getMessage(player, "gui.environment.weather.action.confirm"),
                    ),
                ),
            ),
        )
    }

    private fun createBiomeEntry(player: Player, worldData: WorldData, slot: Int): MenuElement {
        val lang = plugin.languageManager
        val currentBiome = worldData.fixedBiome ?: "DEFAULT"
        val cost = WorldRuntimePolicies.environmentCost(plugin.config, "biome")
        return menuEntry(
            player,
            GuiMenuEntrySpec(
                slot = slot,
                material = Material.GRASS_BLOCK,
                name = GuiNameSpec.Component(lang.getComponent(player, "gui.environment.biome.display")),
                role = GuiElementRole.ACTION,
                description = listOf(
                    lang.getMessage(player, "gui.environment.biome.desc"),
                    lang.getMessage(player, "gui.environment.biome.requirement"),
                ),
                data = buildList {
                    add(GuiMenuEntryData(lang.getMessage(player, "gui.environment.biome.current"), currentBiome, GuiValueTone.SUCCESS))
                    if (MyWorldManagerApi.isWorldPointEconomyEnabled()) {
                        add(GuiMenuEntryData(lang.getMessage(player, "gui.environment.biome.cost"), cost, GuiValueTone.WARNING))
                    }
                },
                actions = listOf(
                    GuiMenuEntryAction(
                        ACTION_BIOME,
                        MenuAcceptedClicks.LEFT_RIGHT,
                        lang.getMessage(player, "gui.environment.biome.action"),
                    ),
                ),
            ),
        )
    }

    private fun menuEntry(player: Player, spec: GuiMenuEntrySpec): MenuElement =
        CCSystem.getAPI().getGuiElementService().menuEntry(player, spec)

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
