package me.awabi2048.myworldmanager.ui

import me.awabi2048.myworldmanager.MyWorldManager
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PlayerPlatformResolver(private val plugin: MyWorldManager) {

    private val cache = ConcurrentHashMap<UUID, PlayerPlatform>()

    @Volatile
    private var floodgateLookupCompleted = false

    @Volatile
    private var floodgateApiClass: Class<*>? = null

    fun resolve(player: Player): PlayerPlatform {
        return cache.compute(player.uniqueId) { _, _ -> detect(player) } ?: PlayerPlatform.JAVA
    }

    fun isBedrock(player: Player): Boolean {
        return resolve(player) == PlayerPlatform.BEDROCK
    }

    fun invalidate(player: Player) {
        cache.remove(player.uniqueId)
    }

    fun clearCache() {
        cache.clear()
    }

    private fun detect(player: Player): PlayerPlatform {
        if (isFloodgateBedrockPlayer(player.uniqueId)) {
            return PlayerPlatform.BEDROCK
        }

        val configuredPrefix =
            plugin.config.getString("bedrock.player_name_prefix", "")?.trim().orEmpty()
        if (configuredPrefix.isNotEmpty() && player.name.startsWith(configuredPrefix)) {
            return PlayerPlatform.BEDROCK
        }

        return PlayerPlatform.JAVA
    }

    private fun isFloodgateBedrockPlayer(playerUuid: UUID): Boolean {
        val apiInstance = resolveFloodgateApiInstance() ?: return false
        val method = apiInstance.javaClass.methods.firstOrNull {
            it.name == "isFloodgatePlayer" &&
                it.parameterCount == 1 &&
                it.parameterTypes[0] == UUID::class.java
        } ?: return false

        return runCatching {
            method.invoke(apiInstance, playerUuid) as? Boolean ?: false
        }.getOrDefault(false)
    }

    private fun resolveFloodgateApiInstance(): Any? {
        val apiClass = resolveFloodgateApiClass() ?: return null
        val getInstanceMethod = apiClass.methods.firstOrNull {
            it.name == "getInstance" && it.parameterCount == 0
        } ?: return null

        return runCatching {
            getInstanceMethod.invoke(null)
        }.getOrNull()
    }

    private fun resolveFloodgateApiClass(): Class<*>? {
        if (floodgateLookupCompleted) {
            return floodgateApiClass
        }

        synchronized(this) {
            if (!floodgateLookupCompleted) {
                floodgateApiClass = runCatching {
                    Class.forName("org.geysermc.floodgate.api.FloodgateApi")
                }.getOrNull()
                floodgateLookupCompleted = true
            }
        }

        return floodgateApiClass
    }
}
