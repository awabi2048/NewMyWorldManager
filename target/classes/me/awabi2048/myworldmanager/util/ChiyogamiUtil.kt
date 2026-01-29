package me.awabi2048.myworldmanager.util

import org.bukkit.Bukkit
import org.bukkit.World

object ChiyogamiUtil {
    private val chiyogamiApiClass: Class<*>? by lazy {
        try {
            Class.forName("me.chiyogaruby.chiyogami.ChiyogamiAPI")
        } catch (e: ClassNotFoundException) {
            null
        }
    }

    /**
     * ChiyogamiAPIが利用可能かどうかを返します。
     */
    fun isChiyogamiActive(): Boolean {
        return chiyogamiApiClass != null
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
