package me.awabi2048.myworldmanager.service

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.PortalData
import me.awabi2048.myworldmanager.model.PublishLevel
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Particle
import org.bukkit.entity.TextDisplay
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PortalManager(private val plugin: MyWorldManager) {
    private val textDisplays = ConcurrentHashMap<UUID, TextDisplay>()
    private val warpCooldowns = ConcurrentHashMap<UUID, Long>()
    private val ignorePlayers = ConcurrentHashMap.newKeySet<UUID>()
    private val portalGracePeriods = ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, Long>>() // PlayerUUID -> (PortalUUID -> Expiry)
    private val portalCache = ConcurrentHashMap<String, ConcurrentHashMap<Long, PortalData>>() // WorldName -> BlockKey -> PortalData

    private fun getBlockKey(x: Int, y: Int, z: Int): Long {
        return (x.toLong() and 0x7FFFFFF shl 38) or (z.toLong() and 0x7FFFFFF shl 12) or (y.toLong() and 0xFFF)
    }

    fun startTasks() {
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            updatePortals()
        }, 0L, 4L) // 4tick間隔 (0.2秒)
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

    private fun updatePortals() {
        val portals = plugin.portalRepository.findAll()
        portalCache.clear()
        
        for (portal in portals) {
            val world = Bukkit.getWorld(portal.worldName) ?: continue
            val block = world.getBlockAt(portal.x, portal.y, portal.z)

            // ブロックが消失（END_PORTAL_FRAMEでなくなった）している場合は自動撤去
            if (block.type != org.bukkit.Material.END_PORTAL_FRAME) {
                removePortalVisuals(portal.id)
                plugin.portalRepository.removePortal(portal.id)
                continue
            }

            val loc = portal.getCenterLocation()

            // 1. パーティクル表示
            val dustOptions = Particle.DustOptions(portal.particleColor, 1.0f)
            for (i in 0..10) {
                val offset = Math.random() * 2.0
                loc.world?.spawnParticle(
                    Particle.DUST,
                    loc.clone().add(0.0, 0.5 + offset, 0.0),
                    1,
                    0.3, 0.0, 0.3,
                    dustOptions
                )
            }

            // 2. テキスト表示の更新
            updateTextDisplay(portal)

            // Cache creation
            val key = getBlockKey(portal.x, portal.y, portal.z)
            portalCache.computeIfAbsent(portal.worldName) { ConcurrentHashMap() }[key] = portal
        }

        // 4. portalGracePeriodsの清掃 (期限切れのものを削除)
        val now = System.currentTimeMillis()
        portalGracePeriods.forEach { (playerUuid, graces) ->
            graces.entries.removeIf { it.value < now }
        }

        // 5. ignorePlayersの解除判定 (ポータル判定範囲外に出たら解除)
        val iterator = ignorePlayers.iterator()
        while (iterator.hasNext()) {
            val playerUuid = iterator.next()
            val player = Bukkit.getPlayer(playerUuid)
            
            if (player == null) {
                iterator.remove()
                continue
            }

            // 全てのポータルの判定エリア外にいるかチェック
            var isInAnyPortal = false
            for (portal in portals) {
                val loc = portal.getCenterLocation()
                val checkCenter = loc.clone().add(0.0, 1.5, 0.0)
                if (player.world.name == portal.worldName && player.location.distanceSquared(checkCenter) <= 0.25) { // 0.5 * 0.5
                    isInAnyPortal = true
                    break
                }
            }
            
            if (!isInAnyPortal) {
                iterator.remove()
            }
        }
    }

    private fun updateTextDisplay(portal: PortalData) {
        val lang = plugin.languageManager
        if (!portal.showText) {
            textDisplays[portal.id]?.remove()
            textDisplays.remove(portal.id)
            return
        }

        val loc = portal.getCenterLocation()
        val world = loc.world ?: return
        val displayLoc = loc.clone().add(0.0, 3.0, 0.0)
        
        var display = textDisplays[portal.id]
        if (display == null || !display.isValid) {
            // 周囲の既存textDisplayを掃除
            world.getNearbyEntities(displayLoc, 0.1, 0.1, 0.1) { it is TextDisplay }.forEach { it.remove() }
            
            display = world.spawn(displayLoc, TextDisplay::class.java) {
                it.billboard = org.bukkit.entity.Display.Billboard.CENTER
            }
            textDisplays[portal.id] = display
        }

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
            plugin.worldService.teleportToWorld(player, portal.worldUuid!!)
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

    fun removePortalVisuals(portalId: UUID) {
        textDisplays[portalId]?.remove()
        textDisplays.remove(portalId)
    }
}
