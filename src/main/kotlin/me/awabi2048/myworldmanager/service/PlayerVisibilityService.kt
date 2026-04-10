package me.awabi2048.myworldmanager.service

import de.myzelyam.api.vanish.VanishAPI
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.Locale

class PlayerVisibilityService(private val plugin: MyWorldManager) {

    @Volatile
    private var premiumVanishApiAvailable: Boolean? = null

    fun isVisibleTo(viewer: Player, target: Player): Boolean {
        if (viewer.uniqueId == target.uniqueId) {
            return true
        }
        if (!viewer.canSee(target)) {
            return false
        }
        if (!isPremiumVanishEnabled()) {
            return true
        }

        return runCatching {
            VanishAPI.canSee(viewer, target)
        }.getOrDefault(viewer.canSee(target))
    }

    fun getVisibleOnlinePlayers(viewer: Player): List<Player> {
        return Bukkit.getOnlinePlayers().filter { online ->
            isVisibleTo(viewer, online)
        }
    }

    fun resolveVisibleOnlinePlayer(viewer: Player, inputName: String): Player? {
        val candidates = PlayerNameUtil.buildSearchCandidates(plugin, inputName)
        if (candidates.isEmpty()) {
            return null
        }

        for (candidate in candidates) {
            Bukkit.getPlayerExact(candidate)?.let { target ->
                if (isVisibleTo(viewer, target)) {
                    return target
                }
            }
        }

        val lowerCandidates = candidates.map { it.lowercase(Locale.ROOT) }.toSet()
        return getVisibleOnlinePlayers(viewer).firstOrNull { online ->
            online.name.lowercase(Locale.ROOT) in lowerCandidates
        }
    }

    private fun isPremiumVanishEnabled(): Boolean {
        if (!plugin.server.pluginManager.isPluginEnabled("PremiumVanish")) {
            return false
        }
        return isPremiumVanishApiAvailable()
    }

    private fun isPremiumVanishApiAvailable(): Boolean {
        premiumVanishApiAvailable?.let { return it }

        val available = runCatching {
            Class.forName("de.myzelyam.api.vanish.VanishAPI")
            true
        }.getOrDefault(false)

        premiumVanishApiAvailable = available
        return available
    }
}
