package me.awabi2048.myworldmanager.service

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.event.MwmWarpReason
import me.awabi2048.myworldmanager.model.PortalData
import me.awabi2048.myworldmanager.model.PublishLevel
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PortalManager(private val plugin: MyWorldManager) {
    // PortalUUID -> TextDisplay の紐付け
    private val textDisplays = ConcurrentHashMap<UUID, TextDisplay>()
    // TextDisplayUUID -> PortalUUID の逆引きマップ
    private val displayToPortal = ConcurrentHashMap<UUID, UUID>()
    private val warpCooldowns = ConcurrentHashMap<UUID, Long>()
    private val entityWarpCooldowns = ConcurrentHashMap<UUID, Long>()
    private val ignorePlayers = ConcurrentHashMap.newKeySet<UUID>()
    private val ignoreEntities = ConcurrentHashMap.newKeySet<UUID>()
    private val portalGracePeriods = ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, Long>>() // PlayerUUID -> (PortalUUID -> Expiry)
    private val entityGracePeriods = ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, Long>>() // EntityUUID -> (PortalUUID -> Expiry)
    private val portalCache = ConcurrentHashMap<String, ConcurrentHashMap<Long, PortalData>>() // WorldName -> BlockKey -> PortalData
    private val gateCache = ConcurrentHashMap<String, MutableList<PortalData>>() // WorldName -> Gate portals

    private fun getBlockKey(x: Int, y: Int, z: Int): Long {
        return (x.toLong() and 0x7FFFFFF shl 38) or (z.toLong() and 0x7FFFFFF shl 12) or (y.toLong() and 0xFFF)
    }

    fun startTasks() {
        // TextDisplayの存在確認を初期化時に実行
        initializeTextDisplays()
        
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            updatePortals()
        }, 0L, 20L) // 20tick間隔 (1秒)
    }
    
    /**
     * サーバー起動時に既存のTextDisplayをメモリマップに復元
     */
    private fun initializeTextDisplays() {
        val portals = plugin.portalRepository.findAll()
        for (portal in portals) {
            if (!portal.showText || portal.textDisplayUuid == null) continue
            
            val world = Bukkit.getWorld(portal.worldName) ?: continue
            val entity = world.getEntity(portal.textDisplayUuid!!)
            
            if (entity is TextDisplay && entity.isValid) {
                // 既存のTextDisplayをメモリマップに復元
                textDisplays[portal.id] = entity
                displayToPortal[entity.uniqueId] = portal.id
            } else {
                // 無効な場合はUUIDをクリア
                portal.textDisplayUuid = null
            }
        }
        plugin.portalRepository.saveAll()
    }

    /**
     * 指定したプレイヤーの一時的なワープ判定を無効化する
     */
    fun addIgnorePlayer(player: Player) {
        ignorePlayers.add(player.uniqueId)
    }

    /**
     * 指定したエンティティの一時的なワープ判定を無効化する
     */
    private fun addIgnoreEntity(entity: Entity) {
        ignoreEntities.add(entity.uniqueId)
    }

    /**
     * 指定したポータルに対して、プレイヤーのテレポート猶予期間を設定する
     */
    fun addPortalGrace(player: Player, portalUuid: UUID, seconds: Long) {
        val playerGrace = portalGracePeriods.computeIfAbsent(player.uniqueId) { ConcurrentHashMap() }
        playerGrace[portalUuid] = System.currentTimeMillis() + (seconds * 1000)
    }

    /**
     * 指定したポータルに対して、エンティティのテレポート猶予期間を設定する
     */
    private fun addEntityGrace(entity: Entity, portalUuid: UUID, seconds: Long) {
        val entityGrace = entityGracePeriods.computeIfAbsent(entity.uniqueId) { ConcurrentHashMap() }
        entityGrace[portalUuid] = System.currentTimeMillis() + (seconds * 1000)
    }

    private fun updatePortals() {
        val portals = plugin.portalRepository.findAll()
        portalCache.clear()
        gateCache.clear()

        // 孤立したTextDisplayをクリーンアップ（ポータルデータが存在しないが、紐付けマップに残っている）
        val validPortalIds = portals.map { it.id }.toSet()
        val displayIterator = displayToPortal.iterator()
        while (displayIterator.hasNext()) {
            val entry = displayIterator.next()
            if (!validPortalIds.contains(entry.value)) {
                val orphanDisplay = textDisplays[entry.value]
                orphanDisplay?.remove()
                textDisplays.remove(entry.value)
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

                for (i in 0..30) {
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
            updateTextDisplay(portal)

            if (portal.isGate()) {
                gateCache.computeIfAbsent(portal.worldName) { mutableListOf() }.add(portal)
            } else {
                val key = getBlockKey(portal.x, portal.y, portal.z)
                portalCache.computeIfAbsent(portal.worldName) { ConcurrentHashMap() }[key] = portal
            }
        }

        warpEntitiesInGates()

        // 4. portalGracePeriodsの清掃 (期限切れのものを削除)
        val now = System.currentTimeMillis()
        portalGracePeriods.forEach { (_, graces) ->
            graces.entries.removeIf { it.value < now }
        }
        entityWarpCooldowns.entries.removeIf { now - it.value > 60_000L }

        // エンティティの猶予期間をクリア
        entityGracePeriods.forEach { (_, graces) ->
            graces.entries.removeIf { it.value < now }
        }

        // 無効化エンティティのクリーンアップ
        val entityIterator = ignoreEntities.iterator()
        while (entityIterator.hasNext()) {
            val entityUuid = entityIterator.next()
            val entity = Bukkit.getEntity(entityUuid)
            if (entity == null || !entity.isValid) {
                entityIterator.remove()
                entityGracePeriods.remove(entityUuid)
            }
        }

        // 5. ignorePlayersの解除判定 (ポータル判定範囲外に出たら解除)
        val playerIterator = ignorePlayers.iterator()
        while (playerIterator.hasNext()) {
            val playerUuid = playerIterator.next()
            val player = Bukkit.getPlayer(playerUuid)

            if (player == null) {
                playerIterator.remove()
                continue
            }

            var isInAnyPortal = false
            for (portal in portals) {
                if (player.world.name != portal.worldName) continue

                if (portal.isGate()) {
                    if (portal.containsLocation(player.location)) {
                        isInAnyPortal = true
                        break
                    }
                } else {
                    val loc = portal.getCenterLocation()
                    val checkCenter = loc.clone().add(0.0, 1.5, 0.0)
                    if (player.location.distanceSquared(checkCenter) <= 0.25) {
                        isInAnyPortal = true
                        break
                    }
                }
            }

            if (!isInAnyPortal) {
                playerIterator.remove()
            }
        }
    }

     private fun updateTextDisplay(portal: PortalData) {
         val lang = plugin.languageManager
         if (!portal.showText) {
             // テキスト表示が無効の場合は削除
             removeTextDisplayForPortal(portal.id)
             return
         }

         val loc = portal.getCenterLocation()
         val world = loc.world ?: return
         val displayLoc = loc.clone().add(0.0, 3.0, 0.0)
         
         // メモリマップから取得
         var display = textDisplays[portal.id]
         
         // 既存のTextDisplayが有効か確認
         if (display != null && display.isValid) {
             // 有効な場合はテキストを更新して終了
             updateDisplayText(display, portal, lang)
             return
         }
         
         // メモリマップにない場合、保存されているUUIDから取得を試みる
         if (portal.textDisplayUuid != null) {
             val savedUuid = portal.textDisplayUuid!!
             val entity = world.getEntity(savedUuid)
             if (entity is TextDisplay && entity.isValid) {
                 // 保存されたUUIDが有効なら使用
                 display = entity
                 textDisplays[portal.id] = display
                 displayToPortal[display.uniqueId] = portal.id
                 updateDisplayText(display, portal, lang)
                 return
             } else {
                 // 無効ならUUIDをクリア（リポジトリ内のオブジェクトも更新）
                 val repoPortal = plugin.portalRepository.findById(portal.id)
                 if (repoPortal != null) {
                     repoPortal.textDisplayUuid = null
                 }
             }
         }
         
         // 位置ベースでも検索してみる（UUIDが変わっていても検出できるように）
         val nearbyDisplays = world.getNearbyEntities(displayLoc, 0.5, 0.5, 0.5)
             .filterIsInstance<TextDisplay>()
             .filter { it.isValid }
         
         if (nearbyDisplays.isNotEmpty()) {
             // 既存のTextDisplayを再利用
             display = nearbyDisplays.first()
             // リポジトリ内のオブジェクトも更新
             val repoPortal = plugin.portalRepository.findById(portal.id)
             if (repoPortal != null) {
                 repoPortal.textDisplayUuid = display.uniqueId
                 plugin.portalRepository.saveAll()
             }
             textDisplays[portal.id] = display
             displayToPortal[display.uniqueId] = portal.id
             updateDisplayText(display, portal, lang)
             return
         }
         
         // 新規作成が必要な場合
         display = world.spawn(displayLoc, TextDisplay::class.java) {
             it.billboard = org.bukkit.entity.Display.Billboard.CENTER
         }
         
         // 紐付け情報を保存
         // 重要: リポジトリ内のオブジェクトを更新する必要がある
         val repoPortal = plugin.portalRepository.findById(portal.id)
         if (repoPortal != null) {
             repoPortal.textDisplayUuid = display.uniqueId
             plugin.portalRepository.saveAll()
         }
         
         textDisplays[portal.id] = display
         displayToPortal[display.uniqueId] = portal.id
         
         // 新規作成した場合もテキストを更新
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

            for (entity in world.entities) {
                if (!entity.isValid || entity.isDead) continue

                var isInAnyGate = false
                for (gate in gates) {
                    if (!gate.containsLocation(entity.location)) continue
                    if (entity is TextDisplay && entity.uniqueId == gate.textDisplayUuid) continue

                    isInAnyGate = true
                    if (!ignoreEntities.contains(entity.uniqueId)) {
                        if (entity is Player) {
                            executeWarp(entity, gate)
                        } else {
                            executeEntityWarp(entity, gate)
                        }
                    }
                    break
                }

                if (!isInAnyGate) {
                    ignoreEntities.remove(entity.uniqueId)
                }
            }
        }
    }

     fun handlePlayerMove(player: Player) {
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
            val targetWorld = Bukkit.getWorld(portal.targetWorldName!!)
            if (targetWorld == null) return

            player.teleport(targetWorld.spawnLocation)
            warpCooldowns[player.uniqueId] = System.currentTimeMillis()
            
            val displayName = plugin.config.getString("portal_targets.${portal.targetWorldName}") ?: portal.targetWorldName!!
            player.sendMessage(lang.getMessage(player, "messages.portal_warped", mapOf("destination" to displayName)))
         }
     }

    private fun executeEntityWarp(entity: Entity, portal: PortalData) {
        val now = System.currentTimeMillis()
        val lastWarp = entityWarpCooldowns[entity.uniqueId] ?: 0L
        if (now - lastWarp < 1000L) return

        if (ignoreEntities.contains(entity.uniqueId)) return

        val entityGrace = entityGracePeriods[entity.uniqueId]
        if (entityGrace != null) {
            val expiry = entityGrace[portal.id]
            if (expiry != null && expiry > now) return
        }

        if (portal.worldUuid != null) {
            val destData = plugin.worldConfigRepository.findByUuid(portal.worldUuid!!) ?: return
            if (destData.publishLevel == PublishLevel.LOCKED) return

            if (!plugin.worldService.loadWorld(destData.uuid)) return
            val targetWorld = Bukkit.getWorld(plugin.worldService.getWorldFolderName(destData)) ?: return
            val targetLoc = destData.spawnPosGuest ?: targetWorld.spawnLocation
            if (targetLoc.world == null) {
                targetLoc.world = targetWorld
            }

            entity.teleport(targetLoc)
            entityWarpCooldowns[entity.uniqueId] = now
            addIgnoreEntity(entity)
            addEntityGrace(entity, portal.id, 5L)
        } else if (portal.targetWorldName != null) {
            val targetWorld = Bukkit.getWorld(portal.targetWorldName!!) ?: return
            entity.teleport(targetWorld.spawnLocation)
            entityWarpCooldowns[entity.uniqueId] = now
            addIgnoreEntity(entity)
            addEntityGrace(entity, portal.id, 5L)
        }
    }

      /**
       * ポータルの TextDisplay を削除
       */
     private fun removeTextDisplayForPortal(portalId: UUID) {
         // メモリから削除
         val display = textDisplays[portalId]
         textDisplays.remove(portalId)
         
         // TextDisplay をエンティティから削除
         if (display != null && display.isValid) {
             displayToPortal.remove(display.uniqueId)
             display.remove()
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
          val portalIdsToRemove = mutableListOf<UUID>()
          val displayUuidsToRemove = mutableListOf<UUID>()
          
          for ((portalId, display) in textDisplays) {
              if (display.world?.name == worldName) {
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
