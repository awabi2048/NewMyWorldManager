package me.awabi2048.myworldmanager.listener

import com.awabi2048.ccsystem.api.localization.generated.CommonKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldMessagesKeys

import com.awabi2048.ccsystem.CCSystem
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.gui.TourDialogManager
import me.awabi2048.myworldmanager.gui.TourGui
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Tag
import org.bukkit.block.Sign
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.SignChangeEvent
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import me.awabi2048.myworldmanager.util.PlayerBlockTargetResolver

class TourListener(private val plugin: MyWorldManager) : Listener {
    private val waypointPreviewTasks = ConcurrentHashMap<UUID, BukkitTask>()

    @EventHandler(ignoreCancelled = true)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = event.item

        val editSession = plugin.tourSessionManager.getEdit(player.uniqueId)
        if (editSession != null && editSession.awaitingWaypointPick && event.action == Action.RIGHT_CLICK_BLOCK && event.hand == EquipmentSlot.HAND) {
            event.isCancelled = true
            // PlayerInteractEventのclickedBlockは、草や雪などの通過可能ブロックを
            // 最初の衝突対象として返すことがあります。位置設定ではそれらを貫通し、
            // ワールドスポーン設定と同じく、実際に足場となる候補ブロックを選びます。
            val targetBlock = PlayerBlockTargetResolver.find(player) ?: event.clickedBlock ?: return
            val spawnLocation = targetBlock.location.clone().add(0.5, 1.0, 0.5)
            if (!plugin.worldSettingsSpawnPreviewService.isSpawnAreaPlaceable(spawnLocation)) {
                player.sendMessage(plugin.languageManager.getMessage(player, MyworldMessagesKeys.MESSAGES_TOUR_WAYPOINT_INVALID_LOCATION))
                return
            }
            stopWaypointPreview(player)
            editSession.awaitingWaypointPick = false
            val editingWaypointUuid = editSession.editingWaypointUuid
            editSession.editingWaypointUuid = null
            plugin.soundManager.playGlobalClickSound(player)
            if (editingWaypointUuid == null) {
                plugin.tourManager.addWaypoint(editSession, targetBlock.location)
            } else {
                plugin.tourManager.updateWaypointLocation(editSession, editingWaypointUuid, targetBlock.location)
            }
            plugin.worldConfigRepository.findByUuid(editSession.worldUuid)?.let { worldData ->
                plugin.tourManager.saveEditSession(player, worldData, closeSession = false)
            }
            CCSystem.getAPI().getMenuRuntimeService().finishExternal(player)
            return
        }

        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        val signBlock = event.clickedBlock ?: return
        if (!Tag.ALL_SIGNS.isTagged(signBlock.type)) return
        if (signBlock.state !is Sign) return
        val worldData = plugin.worldConfigRepository.findByWorldName(player.world.name) ?: return
        val signData = plugin.tourManager.findSignFromBlock(worldData, signBlock) ?: return
        event.isCancelled = true

        if (player.isSneaking && plugin.tourManager.canManage(worldData, player.uniqueId)) {
            plugin.soundManager.playGlobalClickSound(player)
            TourDialogManager.startSignTextEdit(player, plugin, worldData.uuid, signData.uuid, signData.title, signData.description)
            return
        }

        val startTours = plugin.tourManager.findToursBySign(worldData, signData.uuid)

        val activeSession = plugin.tourSessionManager.get(player.uniqueId)
        if (activeSession != null) {
            plugin.tourManager.advanceByWaypoint(player, worldData)
            return
        }

        when {
            startTours.size > 1 -> {
                plugin.soundManager.playGlobalClickSound(player)
                plugin.tourGui.openStartSelectionMenu(player, worldData, signData.uuid)
            }
            startTours.size == 1 -> {
                plugin.soundManager.playGlobalClickSound(player)
                val tour = startTours.first()
                plugin.tourGui.openStartConfirm(player, worldData, tour)
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    fun onTourSignChange(event: SignChangeEvent) {
        val plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
        val player = event.player
        val worldData = plugin.worldConfigRepository.findByWorldName(event.block.world.name) ?: return
        val existing = plugin.tourManager.findSignFromBlock(worldData, event.block)
        if (existing != null && !plugin.tourManager.canManage(worldData, player.uniqueId)) {
            event.isCancelled = true
            player.sendMessage(plugin.languageManager.getMessage(player, CommonKeys.ERROR_TOUR_NO_PERMISSION))
            return
        }
        val isTourMarker = plain.serialize(event.line(0) ?: Component.empty()) == "[Tour]"
        if (!isTourMarker) {
            if (existing != null) {
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    plugin.tourManager.unregisterTourSign(worldData, existing, event.block)
                })
            }
            return
        }
        if (!plugin.tourManager.canManage(worldData, player.uniqueId)) return
        if (existing != null) {
            event.line(0, Component.text("[Tour]", NamedTextColor.DARK_AQUA))
        }
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (existing != null) {
                plugin.tourManager.refreshTourSignText(worldData, existing, event.block)
                return@Runnable
            }
            TourDialogManager.startExistingSignBinding(player, plugin, event.block)
        })
    }

    private fun startWaypointPreview(player: Player) {
        stopWaypointPreview(player)
        waypointPreviewTasks[player.uniqueId] = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (!player.isOnline) {
                stopWaypointPreview(player)
                return@Runnable
            }
            val session = plugin.tourSessionManager.getEdit(player.uniqueId)
            if (session == null || !session.awaitingWaypointPick) {
                stopWaypointPreview(player)
                return@Runnable
            }
            val targetBlock = PlayerBlockTargetResolver.find(player) ?: return@Runnable
            val spawnLocation = targetBlock.location.clone().add(0.5, 1.0, 0.5)
            val placeable = plugin.worldSettingsSpawnPreviewService.isSpawnAreaPlaceable(spawnLocation)
            val frameDust = Particle.DustOptions(
                if (placeable) Color.fromRGB(64, 255, 120) else Color.fromRGB(255, 80, 80),
                0.5f,
            )
            val x = targetBlock.x
            val y = targetBlock.y + 1
            val z = targetBlock.z
            spawnBlockOutline(player, x, y, z, frameDust)
        }, 0L, 2L)
    }

    fun beginWaypointPick(player: Player, waypointUuid: UUID? = null) {
        val session = plugin.tourSessionManager.getEdit(player.uniqueId) ?: return
        if (waypointUuid == null && session.draft.waypoints.size >= 28) {
            player.sendMessage(plugin.languageManager.getMessage(player, CommonKeys.ERROR_TOUR_WAYPOINT_LIMIT))
            return
        }
        session.awaitingWaypointPick = true
        session.editingWaypointUuid = waypointUuid
        player.sendMessage(plugin.languageManager.getMessage(player, MyworldMessagesKeys.MESSAGES_TOUR_WAYPOINT_PICK))
        startWaypointPreview(player)
    }

    private fun stopWaypointPreview(player: Player) {
        waypointPreviewTasks.remove(player.uniqueId)?.cancel()
    }

    private fun spawnBlockOutline(player: Player, blockX: Int, blockY: Int, blockZ: Int, dust: Particle.DustOptions) {
        val minX = blockX.toDouble()
        val minY = blockY.toDouble()
        val minZ = blockZ.toDouble()
        val maxX = blockX + 1.0
        val maxY = blockY + 1.0
        val maxZ = blockZ + 1.0

        spawnLine(player, minX, minY, minZ, maxX, minY, minZ, dust)
        spawnLine(player, minX, minY, maxZ, maxX, minY, maxZ, dust)
        spawnLine(player, minX, minY, minZ, minX, minY, maxZ, dust)
        spawnLine(player, maxX, minY, minZ, maxX, minY, maxZ, dust)

        spawnLine(player, minX, maxY, minZ, maxX, maxY, minZ, dust)
        spawnLine(player, minX, maxY, maxZ, maxX, maxY, maxZ, dust)
        spawnLine(player, minX, maxY, minZ, minX, maxY, maxZ, dust)
        spawnLine(player, maxX, maxY, minZ, maxX, maxY, maxZ, dust)

        spawnLine(player, minX, minY, minZ, minX, maxY, minZ, dust)
        spawnLine(player, maxX, minY, minZ, maxX, maxY, minZ, dust)
        spawnLine(player, minX, minY, maxZ, minX, maxY, maxZ, dust)
        spawnLine(player, maxX, minY, maxZ, maxX, maxY, maxZ, dust)
    }

    private fun spawnLine(player: Player, startX: Double, startY: Double, startZ: Double, endX: Double, endY: Double, endZ: Double, dust: Particle.DustOptions) {
        for (i in 0..9) {
            val t = i.toDouble() / 9.0
            val x = startX + (endX - startX) * t
            val y = startY + (endY - startY) * t
            val z = startZ + (endZ - startZ) * t
            player.spawnParticle(Particle.DUST, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, dust)
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val worldData = plugin.worldConfigRepository.findByWorldName(event.block.world.name) ?: return
        val signData = plugin.tourManager.findSignFromBlock(worldData, event.block) ?: return
        if (!plugin.tourManager.isWorldMember(worldData, event.player.uniqueId)) {
            event.isCancelled = true
            event.player.sendMessage(plugin.languageManager.getMessage(event.player, CommonKeys.ERROR_TOUR_NO_PERMISSION))
            return
        }
        event.isDropItems = false
        plugin.tourManager.breakTourSign(worldData, signData.uuid, event.block.location)
    }

    @EventHandler
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        if (MyWorldManagerApi.isLogoutRelocation(event.player)) return
        val editSession = plugin.tourSessionManager.getEdit(event.player.uniqueId)
        if (editSession != null) {
            editSession.awaitingIconPick = false
            editSession.awaitingWaypointPick = false
            editSession.awaitingWaypointIconPick = null
            editSession.editingWaypointUuid = null
            stopWaypointPreview(event.player)
        }
        if (plugin.tourSessionManager.get(event.player.uniqueId) != null) {
            plugin.tourManager.stopTour(event.player, silent = true)
            event.player.sendMessage(plugin.languageManager.getMessage(event.player, MyworldMessagesKeys.MESSAGES_TOUR_STOPPED_WORLD_CHANGE))
        }
    }
}
