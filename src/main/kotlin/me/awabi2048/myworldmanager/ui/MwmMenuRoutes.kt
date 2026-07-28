package me.awabi2048.myworldmanager.ui

import com.awabi2048.ccsystem.CCSystem
import me.awabi2048.myworldmanager.MyWorldManager
import org.bukkit.entity.Player

class MwmMenuRoutes(private val plugin: MyWorldManager) {
    private val navigation = CCSystem.getAPI().getMenuNavigationService()

    init {
        navigation.registerMenuMatcher(OWNER) { inventory ->
            me.awabi2048.myworldmanager.util.GuiHelper.isPluginGuiInventory(inventory)
        }
    }

    fun clear(player: Player) {
        navigation.clear(player)
    }

    fun unregister() {
        navigation.unregisterOwner(OWNER)
    }

    fun closeOwnedMenus() {
        navigation.closeOwnedMenus(OWNER, plugin.server.onlinePlayers)
    }

    private companion object {
        private const val OWNER = "mwm"
    }
}
