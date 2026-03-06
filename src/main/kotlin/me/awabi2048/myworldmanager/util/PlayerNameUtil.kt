package me.awabi2048.myworldmanager.util

import com.google.gson.JsonParser
import me.awabi2048.myworldmanager.MyWorldManager
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * UUIDからプレイヤー名を取得するためのユーティリティクラス。
 * サーバーのキャッシュに存在しないUUIDでも、外部APIを使用して名前を特定します。
 */
object PlayerNameUtil {
    private val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
        
    private val nameCache = ConcurrentHashMap<UUID, String>()

    /**
     * 指定されたUUIDのプレイヤー名を取得します。
     * 
     * キャッシュ -> Bukkit (既知のプレイヤー) -> 外部API (Mojang) の順で探索します。
     * 外部APIへのアクセスが必要な場合、nullを返しつつ非同期で取得を開始します。
     * 次回の呼び出し時にはキャッシュから取得可能になります。
     */
    fun getName(uuid: UUID): String? {
        // 1. メモリキャッシュ
        nameCache[uuid]?.let { return it }

        // 2. BukkitのOfflinePlayerキャッシュ
        val offlinePlayer = Bukkit.getOfflinePlayer(uuid)
        val name = offlinePlayer.name
        if (name != null) {
            nameCache[uuid] = name
            return name
        }

        // 3. 外部API (非同期)
        fetchNameAsync(uuid)
        
        return null
    }

    /**
     * 名前が取得できない場合に表示するデフォルト名を含めて取得します。
     */
    fun getNameOrDefault(uuid: UUID, default: String = "Unknown"): String {
        return getName(uuid) ?: default
    }

    fun buildSearchCandidates(plugin: MyWorldManager, inputName: String): LinkedHashSet<String> {
        val trimmed = inputName.trim()
        val configuredPrefix =
            plugin.config.getString("bedrock.player_name_prefix", "")?.trim().orEmpty()

        val candidates = linkedSetOf<String>()
        if (trimmed.isEmpty()) {
            return candidates
        }

        candidates += trimmed
        if (configuredPrefix.isNotEmpty()) {
            if (!trimmed.startsWith(configuredPrefix)) {
                candidates += "$configuredPrefix$trimmed"
            } else {
                val withoutPrefix = trimmed.removePrefix(configuredPrefix)
                if (withoutPrefix.isNotEmpty()) {
                    candidates += withoutPrefix
                }
            }
        }
        return candidates
    }

    fun resolveOnlinePlayer(plugin: MyWorldManager, inputName: String): Player? {
        val candidates = buildSearchCandidates(plugin, inputName)
        if (candidates.isEmpty()) {
            return null
        }

        for (candidate in candidates) {
            Bukkit.getPlayerExact(candidate)?.let { return it }
        }

        val lowerCandidates = candidates.map { it.lowercase(Locale.ROOT) }.toSet()
        return Bukkit.getOnlinePlayers().firstOrNull { online ->
            online.name.lowercase(Locale.ROOT) in lowerCandidates
        }
    }

    fun resolveOfflinePlayer(plugin: MyWorldManager, inputName: String): OfflinePlayer? {
        resolveOnlinePlayer(plugin, inputName)?.let { return it }

        val candidates = buildSearchCandidates(plugin, inputName)
        if (candidates.isEmpty()) {
            return null
        }

        val lowerCandidates = candidates.map { it.lowercase(Locale.ROOT) }.toSet()

        Bukkit.getOfflinePlayers().firstOrNull { offline ->
            val name = offline.name ?: return@firstOrNull false
            name.lowercase(Locale.ROOT) in lowerCandidates &&
                (offline.hasPlayedBefore() || offline.isOnline)
        }?.let { return it }

        return plugin.playerStatsRepository.findAllFiles().firstNotNullOfOrNull { file ->
            val uuid = runCatching { UUID.fromString(file.nameWithoutExtension) }.getOrNull()
                ?: return@firstNotNullOfOrNull null
            val lastName = plugin.playerStatsRepository.findByUuid(uuid).lastName ?: return@firstNotNullOfOrNull null
            if (lastName.lowercase(Locale.ROOT) !in lowerCandidates) {
                return@firstNotNullOfOrNull null
            }
            Bukkit.getPlayer(uuid) ?: Bukkit.getOfflinePlayer(uuid)
        }
    }

    /**
     * 外部APIからプレイヤー名を非同期で取得し、キャッシュを更新します。
     */
    private fun fetchNameAsync(uuid: UUID): CompletableFuture<String?> {
        val uuidStr = uuid.toString().replace("-", "")
        val url = "https://sessionserver.mojang.com/session/minecraft/profile/$uuidStr"
        
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", "MyWorldManager-Plugin")
            .build()

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply { response ->
                if (response.statusCode() == 200) {
                    try {
                        val json = JsonParser.parseString(response.body()).asJsonObject
                        val name = json.get("name").asString
                        if (name != null) {
                            nameCache[uuid] = name
                            name
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                } else null
            }
            .exceptionally { null }
    }
}
