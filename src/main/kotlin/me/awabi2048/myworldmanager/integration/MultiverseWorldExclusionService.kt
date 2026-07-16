package me.awabi2048.myworldmanager.integration

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.repository.WorldConfigRepository
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginEnableEvent
import org.bukkit.event.world.WorldLoadEvent
import java.util.logging.Level

/** Multiverseの任意連携APIからMyWorldの登録情報だけを除去する。 */
class MultiverseWorldExclusionService(
    private val plugin: MyWorldManager,
    private val repository: WorldConfigRepository
) : Listener {
    private var cleanupScheduled = false

    fun start() {
        plugin.server.pluginManager.registerEvents(this, plugin)
        scheduleCleanup()
    }

    @EventHandler
    fun onPluginEnable(event: PluginEnableEvent) {
        if (event.plugin.name == MULTIVERSE_PLUGIN_NAME) {
            scheduleCleanup()
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onWorldLoad(event: WorldLoadEvent) {
        if (repository.findByWorldName(event.world.name) != null || isDefaultMyWorldName(event.world.name)) {
            scheduleCleanup()
        }
    }

    private fun scheduleCleanup() {
        if (cleanupScheduled) return
        cleanupScheduled = true
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            cleanupScheduled = false
            removeManagedWorldRegistrations()
        }, 1L)
    }

    private fun removeManagedWorldRegistrations() {
        val multiverse = plugin.server.pluginManager.getPlugin(MULTIVERSE_PLUGIN_NAME)
            ?.takeIf { it.isEnabled }
            ?: return

        try {
            val loader = multiverse.javaClass.classLoader
            val apiClass = Class.forName(MULTIVERSE_API_CLASS, true, loader)
            val api = apiClass.getMethod("get").invoke(null)
            val worldManager = api.javaClass.getMethod("getWorldManager").invoke(api)
            val registeredWorlds = worldManager.javaClass.getMethod("getWorlds").invoke(worldManager) as? Collection<*>
                ?: return
            val configuredNames = repository.findAll()
                .mapTo(mutableSetOf()) { it.customWorldName ?: "my_world.${it.uuid}" }

            var removed = 0
            registeredWorlds.filterNotNull().forEach { multiverseWorld ->
                val name = multiverseWorld.javaClass.getMethod("getName").invoke(multiverseWorld) as? String
                    ?: return@forEach
                if (name !in configuredNames && !isDefaultMyWorldName(name)) return@forEach

                val optionsClass = Class.forName(REMOVE_OPTIONS_CLASS, true, loader)
                val options = optionsClass.methods
                    .first { it.name == "world" && it.parameterCount == 1 }
                    .invoke(null, multiverseWorld)
                optionsClass.getMethod("saveBukkitWorld", Boolean::class.javaPrimitiveType).invoke(options, false)
                optionsClass.getMethod("unloadBukkitWorld", Boolean::class.javaPrimitiveType).invoke(options, false)
                val attempt = worldManager.javaClass.methods
                    .first { it.name == "removeWorld" && it.parameterTypes.contentEquals(arrayOf(optionsClass)) }
                    .invoke(worldManager, options)
                val success = attempt.javaClass.getMethod("isSuccess").invoke(attempt) as Boolean
                if (success) {
                    removed++
                } else {
                    val reason = attempt.javaClass.getMethod("getFailureReason").invoke(attempt)
                    plugin.logger.warning("MultiverseからMyWorld登録を削除できませんでした: world=$name reason=$reason")
                }
            }

            if (removed > 0) {
                worldManager.javaClass.getMethod("saveWorldsConfig").invoke(worldManager)
                plugin.logger.info("MultiverseのMyWorld登録を${removed}件削除しました")
            }
        } catch (error: ReflectiveOperationException) {
            plugin.logger.log(Level.WARNING, "Multiverse APIによるMyWorld登録削除に失敗しました", error)
        }
    }

    companion object {
        private const val MULTIVERSE_PLUGIN_NAME = "Multiverse-Core"
        private const val MULTIVERSE_API_CLASS = "org.mvplugins.multiverse.core.MultiverseCoreApi"
        private const val REMOVE_OPTIONS_CLASS = "org.mvplugins.multiverse.core.world.options.RemoveWorldOptions"
        private val DEFAULT_MY_WORLD_PATTERN = Regex("^my_world\\.[0-9a-fA-F-]{36}$")

        internal fun isDefaultMyWorldName(name: String): Boolean = DEFAULT_MY_WORLD_PATTERN.matches(name)
    }
}
