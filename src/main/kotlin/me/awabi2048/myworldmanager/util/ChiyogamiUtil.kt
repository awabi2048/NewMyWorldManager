package me.awabi2048.myworldmanager.util

import org.bukkit.Bukkit
import org.bukkit.World

object ChiyogamiUtil {
    private val chiyogamiApiClass: Class<*>? by lazy {
        val possibleClassNames = listOf(
            "io.github.bea4dev.chiyogami.ChiyogamiAPI",
            "me.chiyogaruby.chiyogami.ChiyogamiAPI",
            "me.chiyogaruby.chiyogami.api.ChiyogamiAPI",
            "io.github.bea4dev.chiyogami.api.ChiyogamiAPI",
            "world.chiyogami.ChiyogamiAPI",
            "world.chiyogami.api.ChiyogamiAPI",
            "chiyogami.ChiyogamiAPI",
            "chiyogami.api.ChiyogamiAPI",
            "world.chiyogami.chiyogamilib.monitor.TickMonitor",
            "world.chiyogami.chiyogamilib.monitor.PerformanceMonitor",
            "world.chiyogami.chiyogamilib.ChiyogamiLib"
        )
        
        var foundClass: Class<*>? = null
        for (className in possibleClassNames) {
            try {
                foundClass = Class.forName(className)
                Bukkit.getLogger().info("[MyWorldManager-Debug] Found Chiyogami API class: $className")
                break
            } catch (e: ClassNotFoundException) {
                continue
            }
        }
        if (foundClass == null) {
            Bukkit.getLogger().info("[MyWorldManager-Debug] Chiyogami API class not found in possible candidates.")
            
            // パッケージスキャンによるデバッグ情報出力
            try {
                Bukkit.getLogger().info("[MyWorldManager-Debug] Scanning packages for 'chiyogami'...")
                val packages = Package.getPackages()
                var foundPackage = false
                for (pkg in packages) {
                    if (pkg.name.contains("chiyogami", ignoreCase = true)) {
                        Bukkit.getLogger().info("[MyWorldManager-Debug] Found suspicious package: ${pkg.name}")
                        foundPackage = true
                    }
                }
                if (!foundPackage) {
                    Bukkit.getLogger().info("[MyWorldManager-Debug] No packages containing 'chiyogami' found.")
                }
            } catch (e: Exception) {
                Bukkit.getLogger().warning("[MyWorldManager-Debug] Failed to scan packages: ${e.message}")
            }
        }
        foundClass
    }

    /**
     * ChiyogamiAPIが利用可能かどうかを返します。
     */
    fun isChiyogamiActive(): Boolean {
        val serverVersion = Bukkit.getVersion()
        val serverName = Bukkit.getName() // Chiyogamiの場合、ここに名前が入る可能性がある
        val classFound = chiyogamiApiClass != null
        
        val versionMatch = serverVersion.contains("Chiyogami", ignoreCase = true)
        val nameMatch = serverName.contains("Chiyogami", ignoreCase = true)
        
        // デバッグログ: 判定条件の出力
        Bukkit.getLogger().info("[MyWorldManager-Debug] Chiyogami Detection - Class Found: $classFound")
        Bukkit.getLogger().info("[MyWorldManager-Debug] Chiyogami Detection - Server Name: $serverName (Match: $nameMatch)")
        Bukkit.getLogger().info("[MyWorldManager-Debug] Chiyogami Detection - Server Version: $serverVersion (Match: $versionMatch)")
        
        // いずれかの条件が一致すれば有効とみなす
        return classFound || versionMatch || nameMatch
    }

    /**
     * 指定されたワールドのMSPTを取得します。
     * ChiyogamiAPIが利用できない場合は 0.0 を返します。
     */
    fun getWorldMspt(world: World): Double {
        // マイワールドとして登録されているか確認
        try {
            val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(me.awabi2048.myworldmanager.MyWorldManager::class.java)
            if (plugin.worldConfigRepository.findByUuid(world.uid) == null) {
                return 0.0
            }
        } catch (e: Exception) {
            // プラグインインスタンス取得失敗時などはチェックをスキップして続行するか、安全に0.0を返す
            // ここでは続行させる（ChiyogamiUtilが単体テストされる場合などを考慮）
        }

        val clazz = chiyogamiApiClass
        if (clazz == null) {
            return 0.0
        }
        
        return try {
            // パターン1: PerformanceMonitor.getWorldTickNanoTimeMap() を試行
            if (clazz.simpleName == "PerformanceMonitor") {
                try {
                    val method = clazz.getMethod("getWorldTickNanoTimeMap")
                    val result = method.invoke(null)
                    
                    if (result is Map<*, *>) {
                        var nanoTime: Long? = null
                        val worldName = world.name
                        val worldUid = world.uid.toString()

                        // 優先順位をつけて検索
                        // 1. 完全一致 (名前 または UUID)
                        if (result.containsKey(worldName)) {
                            nanoTime = result[worldName] as? Long
                        } else if (result.containsKey(worldUid)) {
                            nanoTime = result[worldUid] as? Long
                        } else {
                            // 2. 柔軟な検索 (イテレーション)
                            for ((k, v) in result) {
                                val keyString = k.toString()
                                
                                // UUIDが含まれている場合 (例: "MyWorld.UUID")
                                if (keyString.contains(worldUid)) {
                                    nanoTime = v as? Long
                                    break
                                }
                                
                                // 名前 + "." で始まる場合 (例: "resource_nether.a.20260122")
                                if (keyString.startsWith("$worldName.")) {
                                    nanoTime = v as? Long
                                    break
                                }
                            }
                        }
                        
                        if (nanoTime != null) {
                            return nanoTime / 1_000_000.0
                        }
                    }
                } catch (e: NoSuchMethodException) {
                   // メソッドがない場合は下の処理へフォールバック
                }
            }

            // パターン2: 従来の getTickTime / getTickTimeNs
            // メソッド名の候補
            val methodNames = listOf("getTickTime", "getTickTimeNs")
            var method: java.lang.reflect.Method? = null
            
            for (name in methodNames) {
                try {
                    method = clazz.getMethod(name, World::class.java)
                    break
                } catch (e: NoSuchMethodException) {
                    continue
                }
            }
            
            if (method == null) {
                return 0.0
            }
            
            val result = method.invoke(null, world)
            val tickTimeNs = when (result) {
                is Long -> result
                is Double -> (result * 1_000_000.0).toLong()
                is Number -> result.toLong()
                else -> 0L
            }
            tickTimeNs / 1_000_000.0
        } catch (e: Exception) {
            // エラー時は静かに0.0を返す
            0.0
        }
    }
}
