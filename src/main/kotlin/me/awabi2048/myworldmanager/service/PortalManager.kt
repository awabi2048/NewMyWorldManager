package me.awabi2048.myworldmanager.service

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.event.MwmWarpReason
import me.awabi2048.myworldmanager.model.PortalData
import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.util.TextDisplayEntityUtil
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.entity.Display
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

class PortalManager(private val plugin: MyWorldManager) {
    data class GateRefundResult(val points: Int, val percent: Int, val ownerUuid: UUID)

    // PortalUUID -> TextDisplay の紐付け
    private val textDisplays = ConcurrentHashMap<UUID, TextDisplay>()
    // TextDisplayUUID -> PortalUUID の逆引きマップ
    private val displayToPortal = ConcurrentHashMap<UUID, UUID>()
    private val activeDisplayWorlds = ConcurrentHashMap.newKeySet<String>()
    private val portalDisplayKey = NamespacedKey(plugin, "portal_display_id")
    private val warpCooldowns = ConcurrentHashMap<UUID, Long>()
    private val ignorePlayers = ConcurrentHashMap.newKeySet<UUID>()
    private val portalGracePeriods = ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, Long>>() // PlayerUUID -> (PortalUUID -> Expiry)
    private val portalCache = ConcurrentHashMap<String, ConcurrentHashMap<Long, PortalData>>() // WorldName -> BlockKey -> PortalData
    private val gateCache = ConcurrentHashMap<String, MutableList<PortalData>>() // WorldName -> Gate portals

    private fun getBlockKey(x: Int, y: Int, z: Int): Long {
        return (x.toLong() and 0x7FFFFFF shl 38) or (z.toLong() and 0x7FFFFFF shl 12) or (y.toLong() and 0xFFF)
    }

    fun calculateWorldGatePlacementCost(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Int {
        val volume = ((maxX - minX + 1).toLong().coerceAtLeast(1L) *
                (maxY - minY + 1).toLong().coerceAtLeast(1L) *
                (maxZ - minZ + 1).toLong().coerceAtLeast(1L))
        val pointCostPerBlock = plugin.config.getInt("portal.world_gate.point_cost_per_block", 1).coerceAtLeast(0)
        val totalCost = volume * pointCostPerBlock.toLong()
        return totalCost.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }

    fun calculateWorldGatePlacementCost(portal: PortalData): Int {
        if (!portal.isGate()) return 0
        return calculateWorldGatePlacementCost(
            portal.getMinX(),
            portal.getMinY(),
            portal.getMinZ(),
            portal.getMaxX(),
            portal.getMaxY(),
            portal.getMaxZ()
        )
    }

    fun refundPointsForRemovedGate(portal: PortalData): GateRefundResult? {
        if (!portal.isGate()) return null

        val rate = plugin.config.getDouble(
            "portal.world_gate.remove_refund_rate",
            plugin.config.getDouble("critical_settings.refund_percentage", 0.5)
        ).coerceIn(0.0, 1.0)
        val cost = calculateWorldGatePlacementCost(portal)
        val refund = kotlin.math.floor(cost * rate).toInt().coerceAtLeast(0)
        val percent = kotlin.math.floor(rate * 100.0).toInt().coerceIn(0, 100)

        if (refund > 0) {
            val ownerStats = plugin.playerStatsRepository.findByUuid(portal.ownerUuid)
            ownerStats.worldPoint += refund
            plugin.playerStatsRepository.save(ownerStats)
        }

        return GateRefundResult(refund, percent, portal.ownerUuid)
    }

    fun startTasks() {
        // 旧バージョンの永続TextDisplay UUIDを初期化
        clearLegacyTextDisplayUuids()

        Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            updatePortalVisualsAndCache()
        }, 0L, 10L) // 10tick間隔 (0.5秒)

        Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            processWarpCycle()
        }, 0L, 5L) // 5tick間隔 (0.25秒)
    }

    fun refreshWorldDisplayLifecycle(previousWorldName: String?, currentWorldName: String?) {
        refreshWorldDisplayLifecycle(previousWorldName)
        if (currentWorldName != previousWorldName) {
            refreshWorldDisplayLifecycle(currentWorldName)
        }
    }

    fun refreshWorldDisplayLifecycle(worldName: String?) {
        if (worldName.isNullOrBlank()) return

        val world = Bukkit.getWorld(worldName)
        if (world == null || world.players.isEmpty()) {
            activeDisplayWorlds.remove(worldName)
            removeTextDisplaysForWorld(worldName)
            return
        }

        val portals = plugin.portalRepository.findAll().filter { it.worldName == worldName }
        if (portals.isEmpty()) {
            activeDisplayWorlds.remove(worldName)
            return
        }

        if (!activeDisplayWorlds.add(worldName)) {
            return
        }

        for (portal in portals) {
            updateTextDisplay(portal, true)
        }
    }
    
    /**
     * 旧バージョンで保存されたTextDisplay UUIDをクリア
     */
    private fun clearLegacyTextDisplayUuids() {
        val portals = plugin.portalRepository.findAll()
        var changed = false
        for (portal in portals) {
            if (portal.textDisplayUuid != null) {
                val world = Bukkit.getWorld(portal.worldName)
                val legacyDisplay = world?.getEntity(portal.textDisplayUuid!!)
                if (legacyDisplay is TextDisplay && legacyDisplay.isValid) {
                    legacyDisplay.remove()
                }
                portal.textDisplayUuid = null
                changed = true
            }
        }
        if (changed) {
            plugin.portalRepository.saveAll()
        }
    }

    /**
     * 指定したプレイヤーの一時的なワープ判定を無効化する
     */
    fun addIgnorePlayer(player: Player) {
        ignorePlayers.add(player.uniqueId)
    }

    /**
     * 指定したポータルに対して、プレイヤーのテレポート猶予期間を設定する
     */
    fun addPortalGrace(player: Player, portalUuid: UUID, seconds: Long) {
        val playerGrace = portalGracePeriods.computeIfAbsent(player.uniqueId) { ConcurrentHashMap() }
        playerGrace[portalUuid] = System.currentTimeMillis() + (seconds * 1000)
    }

    private fun updatePortalVisualsAndCache() {
        val portals = plugin.portalRepository.findAll()
        portalCache.clear()
        gateCache.clear()
        val playersByWorld = Bukkit.getOnlinePlayers().groupBy { it.world.name }
        syncActiveDisplayWorlds(portals, playersByWorld)
        val portalById = portals.associateBy { it.id }

        // 孤立したTextDisplayをクリーンアップ（ポータルデータが存在しないが、紐付けマップに残っている）
        val displayIterator = displayToPortal.iterator()
        while (displayIterator.hasNext()) {
            val entry = displayIterator.next()
            val portalId = entry.value
            val mappedDisplay = textDisplays[portalId]
            val portal = portalById[portalId]
            if (portal == null || mappedDisplay == null || !mappedDisplay.isValid || mappedDisplay.world.name != portal.worldName) {
                TextDisplayEntityUtil.removeIfValid(mappedDisplay)
                textDisplays.remove(portalId)
                displayIterator.remove()
            }
        }

        for (portal in portals) {
            val world = Bukkit.getWorld(portal.worldName) ?: continue

            if (!portal.isGate()) {
                val block = world.getBlockAt(portal.x, portal.y, portal.z)
                if (block.type != org.bukkit.Material.END_PORTAL_FRAME) {
                    removePortalVisuals(portal.id)
                    plugin.portalRepository.removePortal(portal.id)
                    continue
                }
            }

            val loc = portal.getCenterLocation()

            // 1. パーティクル表示
            val dustOptions = Particle.DustOptions(portal.particleColor, 1.0f)
            if (portal.isGate()) {
                val minX = portal.getMinX().toDouble()
                val maxX = portal.getMaxX().toDouble()
                val minY = portal.getMinY().toDouble()
                val maxY = portal.getMaxY().toDouble()
                val minZ = portal.getMinZ().toDouble()
                val maxZ = portal.getMaxZ().toDouble()
                val particleCount = calculateGateParticleCount(portal)

                for (i in 0 until particleCount) {
                    val px = minX + Math.random() * (maxX - minX + 1.0)
                    val py = minY + Math.random() * (maxY - minY + 1.0)
                    val pz = minZ + Math.random() * (maxZ - minZ + 1.0)
                    world.spawnParticle(
                        Particle.DUST,
                        Location(world, px, py, pz),
                        1,
                        0.0, 0.0, 0.0,
                        dustOptions
                    )
                }
            } else {
                for (i in 0..10) {
                    val offset = Math.random() * 2.0
                    world.spawnParticle(
                        Particle.DUST,
                        loc.clone().add(0.0, 0.5 + offset, 0.0),
                        1,
                        0.3, 0.0, 0.3,
                        dustOptions
                    )
                }
            }

            // 2. テキスト表示の更新
            updateTextDisplay(portal, activeDisplayWorlds.contains(portal.worldName))

            if (portal.isGate()) {
                gateCache.computeIfAbsent(portal.worldName) { mutableListOf() }.add(portal)
            } else {
                val key = getBlockKey(portal.x, portal.y, portal.z)
                portalCache.computeIfAbsent(portal.worldName) { ConcurrentHashMap() }[key] = portal
            }
        }

    }

    private fun syncActiveDisplayWorlds(
        portals: Collection<PortalData>,
        playersByWorld: Map<String, List<Player>>
    ) {
        val worldsWithPortals = portals.map { it.worldName }.toSet()

        for (worldName in worldsWithPortals) {
            if (playersByWorld[worldName].isNullOrEmpty()) {
                if (activeDisplayWorlds.remove(worldName)) {
                    removeTextDisplaysForWorld(worldName)
                }
            } else {
                activeDisplayWorlds.add(worldName)
            }
        }

        val activeIterator = activeDisplayWorlds.iterator()
        while (activeIterator.hasNext()) {
            val worldName = activeIterator.next()
            if (!worldsWithPortals.contains(worldName)) {
                removeTextDisplaysForWorld(worldName)
                activeIterator.remove()
            }
        }
    }

    private fun processWarpCycle() {
        warpEntitiesInGates()

        val now = System.currentTimeMillis()

        // portalGracePeriodsの清掃 (期限切れのものを削除)
        portalGracePeriods.forEach { (_, graces) ->
            graces.entries.removeIf { it.value < now }
        }

        // ignorePlayersの解除判定 (ポータル判定範囲外に出たら解除)
        val playerIterator = ignorePlayers.iterator()
        while (playerIterator.hasNext()) {
            val playerUuid = playerIterator.next()
            val player = Bukkit.getPlayer(playerUuid)

            if (player == null) {
                playerIterator.remove()
                continue
            }

            if (!isPlayerInAnyPortal(player)) {
                playerIterator.remove()
            }
        }
    }

    fun teleportPlayerToPortalLocation(player: Player, portal: PortalData, afterTeleported: (() -> Unit)? = null): Boolean {
        return teleportPlayerWithLoadWait(player, portal.worldName, {
            buildPortalEntryLocation(player, portal, it)
        }, afterTeleported)
    }

    fun teleportPlayerToWorldSpawn(
        player: Player,
        targetWorldName: String,
        afterTeleported: (() -> Unit)? = null
    ): Boolean {
        return teleportPlayerWithLoadWait(player, targetWorldName, { it.spawnLocation }, afterTeleported)
    }

    private fun buildPortalEntryLocation(player: Player, portal: PortalData, world: World): Location {
        val entry = if (portal.isGate()) {
            Location(
                world,
                (portal.getMinX() + portal.getMaxX() + 1) / 2.0,
                (portal.getMinY() + portal.getMaxY()) / 2.0,
                (portal.getMinZ() + portal.getMaxZ() + 1) / 2.0
            )
        } else {
            Location(world, portal.x + 0.5, portal.y.toDouble(), portal.z + 0.5)
        }

        entry.add(0.0, 1.0, 0.0)
        entry.yaw = player.location.yaw
        entry.pitch = player.location.pitch
        return entry
    }

    private fun teleportPlayerWithLoadWait(
        player: Player,
        targetWorldName: String,
        locationProvider: (World) -> Location,
        afterTeleported: (() -> Unit)? = null
    ): Boolean {
        val loadedWorld = loadWorldByName(targetWorldName) ?: return false
        val (targetWorld, loadedNow) = loadedWorld
        val waitTicks = plugin.config.getLong("warp.load_wait_ticks", 10L).coerceAtLeast(0L)

        val doTeleport = Runnable {
            if (!player.isOnline) {
                return@Runnable
            }
            player.teleport(locationProvider(targetWorld))
            afterTeleported?.invoke()
        }

        if (loadedNow) {
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.world_loading"))
            Bukkit.getScheduler().runTaskLater(plugin, doTeleport, waitTicks)
        } else {
            doTeleport.run()
        }

        return true
    }

    private fun loadWorldByName(worldName: String): Pair<World, Boolean>? {
        val loaded = Bukkit.getWorld(worldName)
        if (loaded != null) {
            return loaded to false
        }

        val worldDirectory = File(plugin.server.worldContainer, worldName)
        if (!worldDirectory.exists()) {
            return null
        }

        return try {
            val created = plugin.server.createWorld(WorldCreator(worldName)) ?: return null
            created to true
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Failed to load world by name: $worldName", e)
            null
        }
    }

    private fun isPlayerInAnyPortal(player: Player): Boolean {
        val worldName = player.world.name

        val gates = gateCache[worldName]
        if (gates != null) {
            for (gate in gates) {
                if (gate.containsLocation(player.location)) return true
            }
        }

        val worldPortals = portalCache[worldName]
        if (worldPortals != null) {
            for (portal in worldPortals.values) {
                val loc = portal.getCenterLocation()
                val checkCenter = loc.clone().add(0.0, 1.5, 0.0)
                if (player.location.distanceSquared(checkCenter) <= 0.25) {
                    return true
                }
            }
        }

        return false
    }

     private fun updateTextDisplay(portal: PortalData, worldActive: Boolean) {
         val lang = plugin.languageManager
         if (!portal.showText || !worldActive) {
             removeTextDisplayForPortal(portal.id)
             return
         }

         val loc = portal.getCenterLocation()
         val world = loc.world ?: run {
             removeTextDisplayForPortal(portal.id)
             return
         }
         val displayLoc = loc.clone().add(0.0, 3.0, 0.0)
         val tagValue = portal.id.toString()

         var display = textDisplays[portal.id]
         if (display != null) {
             val wrongWorld = display.world.name != portal.worldName
             if (!display.isValid || wrongWorld) {
                 displayToPortal.remove(display.uniqueId)
                 TextDisplayEntityUtil.removeIfValid(display)
                 textDisplays.remove(portal.id)
                 display = null
             }
         }

         if (display == null) {
             val taggedDisplays = TextDisplayEntityUtil.findTaggedDisplays(world, portalDisplayKey, tagValue)
             if (taggedDisplays.isNotEmpty()) {
                 display = taggedDisplays.first()
                 for (duplicate in taggedDisplays.drop(1)) {
                     TextDisplayEntityUtil.removeIfValid(duplicate)
                     displayToPortal.remove(duplicate.uniqueId)
                 }
             }
         }

         if (display == null) {
             display = TextDisplayEntityUtil.spawnTaggedDisplay(
                 world,
                 displayLoc,
                 portalDisplayKey,
                 tagValue
             ) {
                 it.billboard = Display.Billboard.CENTER
             }
         } else {
             if (!TextDisplayEntityUtil.hasTag(display, portalDisplayKey, tagValue)) {
                 TextDisplayEntityUtil.setTag(display, portalDisplayKey, tagValue)
             }
             if (display.location.world != world || display.location.distanceSquared(displayLoc) > 0.01) {
                 display.teleport(displayLoc)
             }
         }

         textDisplays[portal.id] = display
         displayToPortal[display.uniqueId] = portal.id
         updateDisplayText(display, portal, lang)
     }
     
     /**
      * TextDisplayのテキストを更新
      */
     private fun updateDisplayText(display: TextDisplay, portal: PortalData, lang: me.awabi2048.myworldmanager.util.LanguageManager) {
         val destName = if (portal.worldUuid != null) {
             val destinationData = plugin.worldConfigRepository.findByUuid(portal.worldUuid!!)
             destinationData?.name ?: "未知のワールド"
         } else {
             val configName = plugin.config.getString("portal_targets.${portal.targetWorldName}")
             configName ?: portal.targetWorldName ?: "未知のワールド"
         }
         
         val color = TextColor.color(portal.particleColor.asRGB())
         display.text(
             Component.text()
                 .append(LegacyComponentSerializer.legacySection().deserialize(lang.getMessage(null as Player?, "gui.portal.destination_label")))
                 .append(Component.text(destName, color))
                 .build()
         )
     }

    private fun warpEntitiesInGates() {
        for ((worldName, gates) in gateCache) {
            if (gates.isEmpty()) continue
            val world = Bukkit.getWorld(worldName) ?: continue

            for (player in world.players) {
                if (!player.isOnline || player.isDead) continue
                if (player.isSneaking) continue

                for (gate in gates) {
                    if (!gate.containsLocation(player.location)) continue
                    executeWarp(player, gate)
                    break
                }
            }
        }
    }

     fun handlePlayerMove(player: Player) {
        if (player.isSneaking) return

        val worldName = player.world.name
        val worldPortals = portalCache[worldName] ?: return
        
        val x = player.location.blockX
        val y = player.location.blockY
        val z = player.location.blockZ
        
        // ポータルのフレームは足元のブロック (y-1)
        val key = getBlockKey(x, y - 1, z)
        val portal = worldPortals[key] ?: return

        executeWarp(player, portal)
    }

    private fun calculateGateParticleCount(portal: PortalData): Int {
        val volume = ((portal.getMaxX() - portal.getMinX() + 1).toLong() *
                (portal.getMaxY() - portal.getMinY() + 1).toLong() *
                (portal.getMaxZ() - portal.getMinZ() + 1).toLong()).coerceAtLeast(1L)

        val density = plugin.config.getDouble("portal.world_gate.particle_density_per_block", 0.8).coerceAtLeast(0.0)
        val minCount = plugin.config.getInt("portal.world_gate.particle_min_count", 16).coerceAtLeast(0)
        val maxCount = plugin.config.getInt("portal.world_gate.particle_max_count", 320).coerceAtLeast(minCount)

        val scaled = kotlin.math.ceil(volume.toDouble() * density).toInt()
        return scaled.coerceIn(minCount, maxCount)
    }

     private fun executeWarp(player: Player, portal: PortalData) {
         val lang = plugin.languageManager
            
        // クールタイムチェック (1秒)
        val lastWarp = warpCooldowns[player.uniqueId] ?: 0L
        if (System.currentTimeMillis() - lastWarp < 1000) return

        // ワープスキップチェック
        if (ignorePlayers.contains(player.uniqueId)) return
        
        // ポータルごとの猶予期間チェック
        val playerGraces = portalGracePeriods[player.uniqueId]
        if (playerGraces != null) {
            val expiry = playerGraces[portal.id]
            if (expiry != null && expiry > System.currentTimeMillis()) return
        }
        
        if (portal.worldUuid != null) {
            // マイワールドへのワープ
            val destData = plugin.worldConfigRepository.findByUuid(portal.worldUuid!!) ?: return
            
            if (destData.publishLevel == PublishLevel.LOCKED) {
                player.sendMessage(lang.getMessage(player, "error.portal_dest_locked"))
                return
            }

            val folderName = plugin.worldService.getWorldFolderName(destData)
            if (Bukkit.getWorld(folderName) == null) {
                player.sendMessage(lang.getMessage(player, "messages.world_loading"))
            }

            // ワープ実行
            plugin.worldService.teleportToWorld(
                player,
                portal.worldUuid!!,
                reason = MwmWarpReason.PORTAL
            )
            warpCooldowns[player.uniqueId] = System.currentTimeMillis()
            player.sendMessage(lang.getMessage(player, "messages.portal_warped", mapOf("destination" to destData.name)))

                // メンバー以外のみ統計加算 -> AccessControlListenerへ統合
                /*
                val isMember = destData.owner == player.uniqueId || 
                              destData.moderators.contains(player.uniqueId) ||
                              destData.members.contains(player.uniqueId)
                if (!isMember) {
                    destData.recentVisitors[0]++
                    plugin.worldConfigRepository.save(destData)
                }
                */
        } else if (portal.targetWorldName != null) {
            // 外部ワールドへのワープ
            val targetWorldName = portal.targetWorldName!!
            if (!teleportPlayerToWorldSpawn(player, targetWorldName)) {
                player.sendMessage(lang.getMessage(player, "general.world_not_found"))
                return
            }
            warpCooldowns[player.uniqueId] = System.currentTimeMillis()

            val displayName = plugin.config.getString("portal_targets.${portal.targetWorldName}") ?: portal.targetWorldName!!
            player.sendMessage(lang.getMessage(player, "messages.portal_warped", mapOf("destination" to displayName)))
         }
     }

      /**
       * ポータルの TextDisplay を削除
       */
     private fun removeTextDisplayForPortal(portalId: UUID) {
          // メモリから削除
          val display = textDisplays.remove(portalId)

          // TextDisplay をエンティティから削除
          if (display != null) {
              displayToPortal.remove(display.uniqueId)
              TextDisplayEntityUtil.removeIfValid(display)
          }
      }

      private fun removeTextDisplaysForWorld(worldName: String) {
          val portalIds = plugin.portalRepository.findAll()
              .asSequence()
              .filter { it.worldName == worldName }
              .map { it.id }
              .toList()
          for (portalId in portalIds) {
              removeTextDisplayForPortal(portalId)
          }
      }
     
      /**
       * ポータルのビジュアル要素をすべて削除
       */
       fun removePortalVisuals(portalId: UUID) {
           // TextDisplay を削除
           removeTextDisplayForPortal(portalId)
           
           // ポータルデータから紐付けUUIDを削除
           val portal = plugin.portalRepository.findById(portalId)
           if (portal != null) {
               portal.textDisplayUuid = null
               plugin.portalRepository.saveAll()
           }
      }

      /**
       * ワールドアンロード時にそのワールドのTextDisplayをメモリマップから削除
       */
      fun cleanupWorld(worldName: String) {
          activeDisplayWorlds.remove(worldName)
          val portalIdsToRemove = mutableListOf<UUID>()
          val displayUuidsToRemove = mutableListOf<UUID>()
          
          for ((portalId, display) in textDisplays) {
              val portalWorld = plugin.portalRepository.findById(portalId)?.worldName
              if (display.world.name == worldName || portalWorld == worldName) {
                  portalIdsToRemove.add(portalId)
                  displayUuidsToRemove.add(display.uniqueId)
              }
          }
          
            for (portalId in portalIdsToRemove) {
                textDisplays.remove(portalId)
            }
          
          for (displayUuid in displayUuidsToRemove) {
              displayToPortal.remove(displayUuid)
          }
      }
}
