package me.awabi2048.myworldmanager.ui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.MenuRoute
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import me.awabi2048.myworldmanager.MyWorldManager
import org.bukkit.entity.Player

class MenuRouteHistory(private val plugin: MyWorldManager) {
    private val navigation = CCSystem.getAPI().getMenuNavigationService()
    private val customOpeners = ConcurrentHashMap<String, CustomMenuRouteOpener>()

    init {
        navigation.registerMenuMatcher(OWNER) { inventory ->
            me.awabi2048.myworldmanager.util.GuiHelper.isPluginGuiInventory(inventory)
        }
        navigation.registerOpener(OWNER, ROUTE_PLAYER_WORLD) { target, route ->
            val page = route.payload["page"]?.toIntOrNull() ?: 0
            val showBackButton = route.payload["showBackButton"].toBooleanValue()
            plugin.playerWorldGui.open(target, page, showBackButton)
            true
        }
        navigation.registerOpener(OWNER, ROUTE_WORLD_SETTINGS) { target, route ->
            val worldUuid = route.payload["worldUuid"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@registerOpener false
            val latest = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return@registerOpener false
            val showBackButton = route.payload["showBackButton"].toBooleanValue()
            val isPlayerWorldFlow = route.payload["isPlayerWorldFlow"]?.toBooleanStrictOrNull()
            val parentShowBackButton = route.payload["parentShowBackButton"]?.toBooleanStrictOrNull()
            plugin.worldSettingsGui.open(target, latest, showBackButton, isPlayerWorldFlow, parentShowBackButton)
            true
        }
        navigation.registerOpener(OWNER, ROUTE_CUSTOM) { target, route ->
            val key = route.payload["key"] ?: return@registerOpener false
            customOpeners[key]?.open(target) == true
        }
    }

    fun clear(player: Player) {
        navigation.clear(player)
    }

    fun unregister() {
        customOpeners.clear()
        navigation.unregisterOwner(OWNER)
    }

    fun closeOwnedMenus() {
        navigation.closeOwnedMenus(OWNER, plugin.server.onlinePlayers)
    }

    fun pushPlayerWorld(player: Player, page: Int, showBackButton: Boolean) {
        navigation.push(player, playerWorldRoute(page, showBackButton))
    }

    fun pushWorldSettings(
        player: Player,
        worldUuid: UUID,
        showBackButton: Boolean,
        isPlayerWorldFlow: Boolean? = null,
        parentShowBackButton: Boolean? = null
    ) {
        navigation.push(
            player,
            worldSettingsRoute(worldUuid, showBackButton, isPlayerWorldFlow, parentShowBackButton)
        )
    }

    fun pushCustom(player: Player, key: String, opener: CustomMenuRouteOpener) {
        customOpeners[key] = opener
        navigation.push(player, MenuRoute(OWNER, ROUTE_CUSTOM, mapOf("key" to key)))
    }

    fun openPrevious(player: Player): Boolean {
        return navigation.openPrevious(player)
    }

    fun interface CustomMenuRouteOpener {
        fun open(player: Player): Boolean
    }

    private fun playerWorldRoute(page: Int, showBackButton: Boolean): MenuRoute {
        return MenuRoute(
            OWNER,
            ROUTE_PLAYER_WORLD,
            mapOf(
                "page" to page.toString(),
                "showBackButton" to showBackButton.toString()
            )
        )
    }

    private fun worldSettingsRoute(
        worldUuid: UUID,
        showBackButton: Boolean,
        isPlayerWorldFlow: Boolean?,
        parentShowBackButton: Boolean?
    ): MenuRoute {
        val payload = mutableMapOf(
            "worldUuid" to worldUuid.toString(),
            "showBackButton" to showBackButton.toString()
        )
        isPlayerWorldFlow?.let { payload["isPlayerWorldFlow"] = it.toString() }
        parentShowBackButton?.let { payload["parentShowBackButton"] = it.toString() }
        return MenuRoute(OWNER, ROUTE_WORLD_SETTINGS, payload)
    }

    private fun String?.toBooleanValue(): Boolean {
        return this?.toBooleanStrictOrNull() ?: false
    }

    private companion object {
        private const val OWNER = "mwm"
        private const val ROUTE_PLAYER_WORLD = "player_world"
        private const val ROUTE_WORLD_SETTINGS = "world_settings"
        private const val ROUTE_CUSTOM = "custom"
    }
}
