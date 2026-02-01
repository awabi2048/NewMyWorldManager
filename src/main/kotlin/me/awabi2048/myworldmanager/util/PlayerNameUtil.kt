package me.awabi2048.myworldmanager.util

import com.google.gson.JsonParser
import org.bukkit.Bukkit
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
