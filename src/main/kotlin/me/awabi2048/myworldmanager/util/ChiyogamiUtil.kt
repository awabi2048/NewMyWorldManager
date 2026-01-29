package me.awabi2048.myworldmanager.util

import org.bukkit.Bukkit
import org.bukkit.World

object ChiyogamiUtil {
    private val chiyogamiApiClass: Class<*>? by lazy {
        val possibleClassNames = listOf(
            "io.github.bea4dev.chiyogami.ChiyogamiAPI",
            "me.chiyogaruby.chiyogami.ChiyogamiAPI"
        )
        
        var foundClass: Class<*>? = null
        for (className in possibleClassNames) {
            try {
                // デバッグログ: クラス検索開始
                Bukkit.getLogger().info("[MyWorldManager-Debug] Attempting to find Chiyogami API class: $className")
                foundClass = Class.forName(className)
                Bukkit.getLogger().info("[MyWorldManager-Debug] Found Chiyogami API class: $className")
                break
            } catch (e: ClassNotFoundException) {
                continue
            }
        }
        foundClass
    }

    /**
     * ChiyogamiAPIが利用可能かどうかを返します。
     */
    fun isChiyogamiActive(): Boolean {
        val version = Bukkit.getVersion()
        val classFound = chiyogamiApiClass != null
        val versionMatch = version.contains("Chiyogami", ignoreCase = true)
        
        // デバッグログ: 判定条件の出力
        Bukkit.getLogger().info("[MyWorldManager-Debug] Chiyogami Detection - Class Found: $classFound, Version String: $version")
        Bukkit.getLogger().info("[MyWorldManager-Debug] Chiyogami Detection - Version Match: $versionMatch")
        
        // APIクラスが存在するか、サーバー名にChiyogamiが含まれている場合に有効とみなす
        return classFound || versionMatch
    }

    /**
     * 指定されたワールドのMSPTを取得します。
     * ChiyogamiAPIが利用できない場合は 0.0 を返します。
     */
    fun getWorldMspt(world: World): Double {
        val clazz = chiyogamiApiClass ?: return 0.0
        return try {
            val method = clazz.getMethod("getTickTime", World::class.java)
            val tickTimeNs = method.invoke(null, world) as Long
            tickTimeNs / 1_000_000.0 // ナノ秒をミリ秒に変換
        } catch (e: Exception) {
            0.0
        }
    }
}
