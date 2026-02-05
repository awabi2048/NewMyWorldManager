package me.awabi2048.myworldmanager.util

import org.bukkit.Bukkit
import org.bukkit.World
import world.chiyogami.chiyogamilib.ChiyogamiLib
import world.chiyogami.chiyogamilib.ServerType
import world.chiyogami.chiyogamilib.monitor.PerformanceMonitor

object ChiyogamiUtil {
    /** ChiyogamiAPIが利用可能かどうかを返します。 ChiyogamiLibを使用して判定します。 */
    fun isChiyogamiActive(): Boolean {
        return try {
            ChiyogamiLib.getServerType() == ServerType.CHIYOGAMI
        } catch (e: NoClassDefFoundError) {
            // ChiyogamiLib が存在しない場合は Bukkit.getVersion() でフォールバック
            Bukkit.getVersion().contains("Chiyogami", ignoreCase = true)
        } catch (e: Exception) {
            Bukkit.getVersion().contains("Chiyogami", ignoreCase = true)
        }
    }

    /** 指定されたワールドのMSPTを取得します。 取得に失敗した場合は null を返します。 */
    fun getWorldMspt(world: World): Double? {
        if (!isChiyogamiActive()) return null

        return try {
            // 直接呼び出しを試みる
            directGetWorldMspt(world.name)
        } catch (e: Throwable) {
            // リフレクションによる取得
            reflectionGetWorldMspt(world)
        }
    }

    /**
     * 指定されたワールド名のMSPTを取得します。 ワールドがロードされていなくても、ChiyogamiLibがデータを保持していれば取得可能です。
     * リフレクションによる取得はサポートしていません。
     */
    fun getWorldMspt(worldName: String): Double? {
        if (!isChiyogamiActive()) return null

        return try {
            directGetWorldMspt(worldName)
        } catch (e: Throwable) {
            null
        }
    }

    private fun directGetWorldMspt(worldName: String): Double? {
        return try {
            val nanoTimeMap = PerformanceMonitor.getWorldTickNanoTimeMap()
            // Map<String, Long> と想定
            val tickTimeNs = nanoTimeMap[worldName] ?: return null
            // 10^-6倍する (ナノ秒 -> ミリ秒)
            tickTimeNs * 0.000001
        } catch (e: Exception) {
            null
        }
    }

    /** MSPT値に基づいた装飾色コードを返します。 */
    fun getMsptColorCode(mspt: Double): String {
        return when {
            mspt <= 10.0 -> "§a"
            mspt <= 30.0 -> "§e"
            mspt <= 50.0 -> "§c"
            else -> "§4"
        }
    }

    /** MSPT値に基づいたAdventure Componentの色（装飾付き文字列）を返します。 */
    fun getMsptColoredString(mspt: Double): String {
        val color = getMsptColorCode(mspt)
        return String.format("%s%.1f", color, mspt)
    }

    /** サーバー全体のMSPTを取得します。Chiyogamiが利用可能な場合はPerformanceMonitorから取得し、そうでない場合はTPSから概算します。 */
    fun getServerMspt(): Double {
        return if (isChiyogamiActive()) {
            try {
                PerformanceMonitor.getFullServerTickNanoTime() * 0.000001
            } catch (e: Exception) {
                // フォールバック: TPSから計算
                getServerMsptFromTps()
            }
        } else {
            getServerMsptFromTps()
        }
    }

    private fun getServerMsptFromTps(): Double {
        val tps = Bukkit.getTPS()[0] // 1分平均TPS
        return if (tps > 0) 50.0 / tps else 50.0 // TPSが0以下の場合は50msとする
    }

    private fun reflectionGetWorldMspt(world: World): Double? {
        val possibleClassNames =
                listOf(
                        "io.github.bea4dev.chiyogami.ChiyogamiAPI",
                        "me.chiyogaruby.chiyogami.ChiyogamiAPI"
                )

        for (className in possibleClassNames) {
            try {
                val clazz = Class.forName(className)
                val method = clazz.getMethod("getTickTime", World::class.java)
                val tickTimeNs = method.invoke(null, world) as Long
                return tickTimeNs / 1_000_000.0
            } catch (e: Exception) {
                continue
            }
        }
        return null
    }
}
