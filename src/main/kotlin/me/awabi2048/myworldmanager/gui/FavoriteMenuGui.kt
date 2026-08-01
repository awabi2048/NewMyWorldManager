package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiMenuDisplaySpec
import com.awabi2048.ccsystem.api.gui.GuiMenuActionIntent
import com.awabi2048.ccsystem.api.gui.GuiMenuEntrySpec
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
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.util.GuiHelper
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import java.util.UUID

class FavoriteMenuGui(private val plugin: MyWorldManager) {
    private val runtime = CCSystem.getAPI().getMenuRuntimeService()

    init {
        runtime.register(
            InventoryMenuDefinition(
                owner = OWNER,
                id = ROUTE_ID,
                renderer = { context -> render(context.player, context.route) },
                actions = mapOf(
                    ACTION_OTHER_WORLDS to MenuActionHandler(::openOtherWorlds),
                    ACTION_TOGGLE to MenuActionHandler(::toggleFavorite),
                    ACTION_LIST to MenuActionHandler(::openFavoriteList),
                ),
            ),
        )
    }

    fun open(player: Player, worldData: WorldData?) {
        plugin.settingsSessionManager.updateSessionAction(
            player,
            worldData?.uuid ?: player.uniqueId,
            me.awabi2048.myworldmanager.session.SettingsAction.FAVORITE_MENU_GUI,
            isGui = true
        )
        runtime.open(
            player,
            MenuRoute(
                OWNER,
                ROUTE_ID,
                worldData?.let { mapOf(WORLD_UUID to it.uuid.toString()) }.orEmpty(),
            ),
        )
    }

    private fun render(player: Player, route: MenuRoute): InventoryMenuView {
        val worldData = route.worldUuid()?.let(plugin.worldConfigRepository::findByUuid)
        val layout = GuiHelper.threeChoiceLayout()
        val elements = mutableListOf<MenuElement>()
        if (worldData != null && worldData.owner == player.uniqueId) {
            elements += actionEntry(player, layout.centerSlot, Material.BOOK, "gui.favorite.favorite_menu.list", ACTION_LIST)
        } else {
            if (worldData != null) {
                elements += actionEntry(player, layout.leftSlot, Material.COMPASS, "gui.favorite.favorite_menu.other_worlds", ACTION_OTHER_WORLDS)
            }
            elements += createToggleFavoriteEntry(player, worldData, layout.centerSlot)
            elements += actionEntry(player, layout.rightSlot, Material.BOOK, "gui.favorite.favorite_menu.list", ACTION_LIST)
        }

        if (worldData != null) {
            elements += createWorldInfoEntry(player, worldData, layout.backSlot)
        }

        return InventoryMenuView(
            size = layout.size,
            title = GuiHelper.inventoryTitle(plugin.languageManager.getMessage(player, "gui.favorite.favorite_menu.title")),
            elements = elements,
        )
    }

    private fun openOtherWorlds(context: MenuActionContext): MenuActionResult {
        val worldData = context.route.worldUuid()?.let(plugin.worldConfigRepository::findByUuid)
            ?: return MenuActionResult.Rejected()
        plugin.menuEntryRouter.openVisitMenu(context.player, Bukkit.getOfflinePlayer(worldData.owner), 0, worldData)
        return MenuActionResult.Success(MenuUpdate.None)
    }

    private fun toggleFavorite(context: MenuActionContext): MenuActionResult {
        val player = context.player
        val worldData = context.route.worldUuid()?.let(plugin.worldConfigRepository::findByUuid)
            ?: return MenuActionResult.Rejected()
        if (worldData.owner == player.uniqueId) return MenuActionResult.Rejected()
        return when (plugin.favoriteStateService.toggle(
            player,
            worldData,
            me.awabi2048.myworldmanager.api.event.MwmFavoriteAddSource.FAVORITE_MENU,
        )) {
            me.awabi2048.myworldmanager.service.FavoriteStateService.ToggleResult.Removed -> {
                player.sendMessage(plugin.languageManager.getMessage(player, "messages.favorite_removed"))
                MenuActionResult.Success(MenuUpdate.Refresh)
            }
            me.awabi2048.myworldmanager.service.FavoriteStateService.ToggleResult.Added -> {
                player.sendMessage(plugin.languageManager.getMessage(player, "messages.favorite_added"))
                MenuActionResult.Success(MenuUpdate.Refresh)
            }
            me.awabi2048.myworldmanager.service.FavoriteStateService.ToggleResult.LimitReached -> {
                val maxFavoriteCount = plugin.config.getInt("favorite.max_count", 1000)
                player.sendMessage(
                    plugin.languageManager.getMessage(
                        player,
                        "error.favorite_limit_reached",
                        mapOf("limit" to maxFavoriteCount),
                    ),
                )
                MenuActionResult.Rejected()
            }
        }
    }

    private fun openFavoriteList(context: MenuActionContext): MenuActionResult {
        val worldData = context.route.worldUuid()?.let(plugin.worldConfigRepository::findByUuid)
        plugin.menuEntryRouter.openFavoriteList(context.player, 0, worldData, returnToFavoriteMenu = true)
        return MenuActionResult.Success(MenuUpdate.None)
    }

    private fun actionEntry(
        player: Player,
        slot: Int,
        material: Material,
        key: String,
        actionId: String,
    ): MenuElement {
        val lang = plugin.languageManager
        return CCSystem.getAPI().getGuiElementService().menuEntry(
            player,
            GuiMenuEntrySpec(
                slot = slot,
                material = material,
                name = GuiNameSpec.FixedLabel(lang.getComponent(player, "$key.name")),
                role = GuiElementRole.ACTION,
                description = lang.getMessageList(player, "$key.lore"),
                actions = listOf(
                    menuGestureAction(
                        actionId,
                        MenuGesture.ANY,
                        lang.getMessage(player, "$key.action"),
                        safety = favoriteMenuActionSafety(actionId),
                    ),
                ),
            ),
        )
    }

    private fun createToggleFavoriteEntry(player: Player, worldData: WorldData?, slot: Int): MenuElement {
        val lang = plugin.languageManager

        if (worldData == null) {
            return restrictedToggleEntry(player, slot, "gui.favorite.favorite_menu.toggle.lore_restricted_not_managed")
        }

        if (worldData.owner == player.uniqueId) {
            return restrictedToggleEntry(player, slot, "gui.favorite.favorite_menu.toggle.lore_restricted_owner")
        }

        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        val isFavorite = stats.favoriteWorlds.containsKey(worldData.uuid)

        val material = if (isFavorite) Material.RED_DYE else Material.GRAY_DYE
        val nameKey = if (isFavorite) "gui.favorite.favorite_menu.toggle.name_remove" else "gui.favorite.favorite_menu.toggle.name_add"
        val loreKey = if (isFavorite) "gui.favorite.favorite_menu.toggle.lore_remove" else "gui.favorite.favorite_menu.toggle.lore_add"
        return CCSystem.getAPI().getGuiElementService().menuEntry(
            player,
            GuiMenuEntrySpec(
                slot = slot,
                material = material,
                name = GuiNameSpec.FixedLabel(lang.getComponent(player, nameKey)),
                role = GuiElementRole.ACTION,
                description = listOf(lang.getMessage(player, loreKey)),
                actions = listOf(
                    menuGestureAction(
                        ACTION_TOGGLE,
                        MenuGesture.ANY,
                        lang.getMessage(player, "gui.favorite.favorite_menu.toggle.action"),
                        safety = MenuActionSafety.REVERSIBLE,
                        reversibleContract = MwmMenuActionSemantics.contract("favorite-toggle"),
                    ),
                ),
            ),
        )
    }

    private fun favoriteMenuActionSafety(actionId: String): MenuActionSafety = when (actionId) {
        ACTION_OTHER_WORLDS,
        ACTION_LIST -> MenuActionSafety.INPUT_OR_EXTERNAL_SURFACE
        else -> error("Unknown favorite menu action safety: $actionId")
    }

    private fun restrictedToggleEntry(player: Player, slot: Int, warningKey: String): MenuElement {
        val lang = plugin.languageManager
        return CCSystem.getAPI().getGuiElementService().menuEntry(
            player,
            GuiMenuEntrySpec(
                slot = slot,
                material = Material.BARRIER,
                name = GuiNameSpec.FixedLabel(lang.getComponent(player, "gui.favorite.favorite_menu.toggle.name_restricted")),
                role = GuiElementRole.CONTENT,
                warnings = listOf(lang.getMessage(player, warningKey)),
            ),
        )
    }

    private fun createWorldInfoEntry(player: Player, worldData: WorldData, slot: Int): MenuElement {
        val lang = plugin.languageManager

        val owner = Bukkit.getOfflinePlayer(worldData.owner)
        val ownerName = owner.name ?: lang.getMessage(player, "general.unknown")
        val tagNames = worldData.tags.takeIf(List<String>::isNotEmpty)?.joinToString(", ") {
            plugin.worldTagManager.getDisplayName(player, it)
        }
        val lore = GuiLoreSpec.Blocks(listOf(
            GuiLoreBlock(buildList {
                add(GuiLoreLine.Data(lang.getMessage(player, "gui.common.world_item.world_name"), worldData.name, "§a"))
                if (worldData.description.isNotBlank()) add(GuiLoreLine.UserText(worldData.description))
            }),
            GuiLoreBlock(buildList {
                add(GuiLoreLine.Data(lang.getMessage(player, "gui.common.world_item.owner"), ownerName, "§b"))
                add(GuiLoreLine.Data(lang.getMessage(player, "gui.common.world_item.favorite"), worldData.favorite, "§c"))
                add(GuiLoreLine.Data(
                    lang.getMessage(player, "gui.common.world_item.recent_visitors"),
                    lang.getMessage(player, "gui.common.world_item.recent_visitors_value", mapOf("count" to worldData.recentVisitors.sum())),
                    "§a"
                ))
                if (tagNames != null) add(GuiLoreLine.Data(lang.getMessage(player, "gui.common.world_item.tags"), tagNames, "§e"))
            })
        ))

        return CCSystem.getAPI().getGuiElementService().menuDisplay(
            GuiMenuDisplaySpec(
                slot = slot,
                item = GuiItemSpec(
                    material = worldData.icon,
                    name = GuiNameSpec.FixedLabel(lang.getComponent(player, "gui.favorite.current_world.name")),
                    lore = lore,
                    role = GuiElementRole.CONTENT,
                    amount = 1,
                ),
            ),
        )
    }

    private fun MenuRoute.worldUuid(): UUID? =
        payload[WORLD_UUID]?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    companion object {
        private const val OWNER = "myworldmanager"
        private const val ROUTE_ID = "favorite_menu"
        private const val WORLD_UUID = "world_uuid"
        private const val ACTION_OTHER_WORLDS = "other_worlds"
        private const val ACTION_TOGGLE = "toggle"
        private const val ACTION_LIST = "list"
    }
}
