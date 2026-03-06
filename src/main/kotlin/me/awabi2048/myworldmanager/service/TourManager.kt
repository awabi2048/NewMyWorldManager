package me.awabi2048.myworldmanager.service

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.TourData
import me.awabi2048.myworldmanager.model.TourNavigationMode
import me.awabi2048.myworldmanager.model.TourSignData
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.session.TourEditSession
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.FluidCollisionMode
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Sign
import org.bukkit.block.data.Directional
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2

class TourManager(private val plugin: MyWorldManager) {
    companion object {
        const val DEFAULT_TOUR_LIMIT = 7
        const val MAX_SIGNS_PER_TOUR = 28
        const val MAX_SIGNS_PER_WORLD = 64
        const val MAX_TITLE_LENGTH = 15
        const val MAX_DESCRIPTION_LENGTH = 30
        val TOUR_SIGN_KEY = org.bukkit.NamespacedKey("myworldmanager", "tour_sign_uuid")
    }

    private val bossBars = ConcurrentHashMap<UUID, BossBar>()
    private val navigationTasks = ConcurrentHashMap<UUID, BukkitTask>()

    fun isWorldMember(worldData: WorldData, playerUuid: UUID): Boolean {
        return worldData.owner == playerUuid ||
            worldData.moderators.contains(playerUuid) ||
            worldData.members.contains(playerUuid)
    }

    fun canManage(worldData: WorldData, playerUuid: UUID): Boolean {
        return worldData.owner == playerUuid || worldData.moderators.contains(playerUuid)
    }

    fun getTourLimit(player: Player, worldData: WorldData): Int {
        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        return stats.tourSlotsByWorld[worldData.uuid] ?: DEFAULT_TOUR_LIMIT
    }

    fun validTours(worldData: WorldData): List<TourData> = worldData.tours.filter { it.signUuids.size >= 2 }

    fun hasValidTour(worldData: WorldData): Boolean = validTours(worldData).isNotEmpty()

    fun getSign(worldData: WorldData, signUuid: UUID): TourSignData? = worldData.tourSigns.find { it.uuid == signUuid }

    fun getTour(worldData: WorldData, tourUuid: UUID): TourData? = worldData.tours.find { it.uuid == tourUuid }

    fun findToursBySign(worldData: WorldData, signUuid: UUID): List<TourData> = worldData.tours.filter { signUuid in it.signUuids }

    fun signVisitCount(worldData: WorldData, signUuid: UUID): Int =
        findToursBySign(worldData, signUuid).sumOf { it.startedPlayerUuids.size }

    fun createDraftTour(player: Player, worldData: WorldData): TourEditSession {
        val index = worldData.tours.size + 1
        return plugin.tourSessionManager.openNewEdit(player.uniqueId, worldData.uuid, "ツアー$index", "新しいワールドツアー")
    }

    fun openEditSession(player: Player, worldData: WorldData, tour: TourData): TourEditSession {
        return plugin.tourSessionManager.openExistingEdit(player.uniqueId, worldData.uuid, tour)
    }

    fun saveEditSession(player: Player, worldData: WorldData): TourData? {
        val session = plugin.tourSessionManager.getEdit(player.uniqueId) ?: return null
        val draft = session.draft
        val target = session.originalTourUuid?.let { getTour(worldData, it) }
        val editedExisting = target != null
        val result = if (target == null) {
            worldData.tours.add(draft)
            draft
        } else {
            target.name = draft.name
            target.description = draft.description
            target.icon = draft.icon
            target.signUuids.clear()
            target.signUuids.addAll(draft.signUuids)
            target.completedCount = draft.completedCount
            target.startedPlayerUuids.clear()
            target.startedPlayerUuids.addAll(draft.startedPlayerUuids)
            target
        }
        worldData.tours.sortBy { it.createdAt }
        plugin.worldConfigRepository.save(worldData)
        plugin.tourSessionManager.clearEdit(player.uniqueId)
        if (editedExisting) {
            cancelActiveTourSessions(worldData.uuid, result.uuid, "messages.tour.cancelled_edited")
        }
        return result
    }

    fun deleteTour(worldData: WorldData, tourUuid: UUID) {
        worldData.tours.removeIf { it.uuid == tourUuid }
        plugin.worldConfigRepository.save(worldData)
        cancelActiveTourSessions(worldData.uuid, tourUuid, "messages.tour.cancelled_deleted")
    }

    fun createTourSign(worldData: WorldData, player: Player, block: Block, blockFace: BlockFace, title: String, description: String): TourSignData {
        val signBlock = if (blockFace == BlockFace.UP) block.getRelative(BlockFace.UP) else block
        val signData = TourSignData(
            uuid = UUID.randomUUID(),
            worldUuid = worldData.uuid,
            title = title.take(MAX_TITLE_LENGTH),
            description = description.take(MAX_DESCRIPTION_LENGTH),
            placedBy = player.uniqueId,
            blockX = signBlock.x,
            blockY = signBlock.y,
            blockZ = signBlock.z,
            blockFace = blockFace.name
        )
        placeSignBlock(signData, block, blockFace, player)
        worldData.tourSigns.add(signData)
        plugin.worldConfigRepository.save(worldData)
        return signData
    }

    fun updateTourSign(signData: TourSignData, worldData: WorldData) {
        val world = Bukkit.getWorld(plugin.worldService.getWorldFolderName(worldData)) ?: return
        val block = world.getBlockAt(signData.blockX, signData.blockY, signData.blockZ)
        val sign = block.state as? Sign ?: return
        updateSignText(sign, signData, worldData)
    }

    fun placeSignBlock(signData: TourSignData, clickedBlock: Block, blockFace: BlockFace, player: Player) {
        val signBlock = if (blockFace == BlockFace.UP) clickedBlock.getRelative(BlockFace.UP) else clickedBlock
        if (blockFace == BlockFace.UP) {
            signBlock.type = Material.PALE_OAK_SIGN
            val sign = signBlock.state as? Sign ?: return
            val rotatable = signBlock.blockData as? org.bukkit.block.data.Rotatable ?: return
            rotatable.rotation = yawToBlockFace(player.location.yaw)
            signBlock.blockData = rotatable
            sign.persistentDataContainer.set(TOUR_SIGN_KEY, PersistentDataType.STRING, signData.uuid.toString())
            updateSignText(sign, signData, plugin.worldConfigRepository.findByUuid(signData.worldUuid) ?: return)
        } else {
            signBlock.type = Material.PALE_OAK_WALL_SIGN
            val sign = signBlock.state as? Sign ?: return
            val directional = signBlock.blockData as? Directional ?: return
            directional.facing = blockFace
            signBlock.blockData = directional
            sign.persistentDataContainer.set(TOUR_SIGN_KEY, PersistentDataType.STRING, signData.uuid.toString())
            updateSignText(sign, signData, plugin.worldConfigRepository.findByUuid(signData.worldUuid) ?: return)
        }
    }

    private fun yawToBlockFace(yaw: Float): BlockFace {
        return when {
            yaw >= -22.5 && yaw < 22.5 -> BlockFace.SOUTH
            yaw >= 22.5 && yaw < 67.5 -> BlockFace.SOUTH_WEST
            yaw >= 67.5 && yaw < 112.5 -> BlockFace.WEST
            yaw >= 112.5 && yaw < 157.5 -> BlockFace.NORTH_WEST
            yaw >= 157.5 || yaw < -157.5 -> BlockFace.NORTH
            yaw >= -157.5 && yaw < -112.5 -> BlockFace.NORTH_EAST
            yaw >= -112.5 && yaw < -67.5 -> BlockFace.EAST
            else -> BlockFace.SOUTH_EAST
        }
    }

    fun updateSignText(sign: Sign, signData: TourSignData, worldData: WorldData) {
        sign.getSide(org.bukkit.block.sign.Side.FRONT).line(0, Component.text("§1[Tour]"))
        sign.getSide(org.bukkit.block.sign.Side.FRONT).line(1, Component.text(signData.title))
        sign.getSide(org.bukkit.block.sign.Side.FRONT).line(2, Component.text(signData.description))
        sign.getSide(org.bukkit.block.sign.Side.FRONT).line(3, Component.text("§b${signVisitCount(worldData, signData.uuid)} Visitors"))
        sign.update()
    }

    fun removeTourSign(worldData: WorldData, signUuid: UUID, returnItemTo: Player? = null) {
        val signData = getSign(worldData, signUuid) ?: return
        val affectedTourUuids = worldData.tours.filter { signUuid in it.signUuids }.map { it.uuid }
        val world = Bukkit.getWorld(plugin.worldService.getWorldFolderName(worldData))
        val block = world?.getBlockAt(signData.blockX, signData.blockY, signData.blockZ)
        if (block != null && (block.type == Material.PALE_OAK_SIGN || block.type == Material.PALE_OAK_WALL_SIGN)) {
            block.type = Material.AIR
        }
        worldData.tours.forEach { it.signUuids.remove(signUuid) }
        worldData.tourSigns.removeIf { it.uuid == signUuid }
        plugin.worldConfigRepository.save(worldData)
        affectedTourUuids.forEach { cancelActiveTourSessions(worldData.uuid, it, "messages.tour.cancelled_edited") }
        returnItemTo?.inventory?.addItem(me.awabi2048.myworldmanager.util.CustomItem.TOUR_SIGN.create(plugin.languageManager, returnItemTo))
    }

    fun breakTourSign(worldData: WorldData, signUuid: UUID, location: Location) {
        removeTourSign(worldData, signUuid, null)
        location.world?.dropItemNaturally(location, me.awabi2048.myworldmanager.util.CustomItem.TOUR_SIGN.create(plugin.languageManager, null))
    }

    fun findSignFromBlock(worldData: WorldData, signBlock: Block): TourSignData? {
        val sign = signBlock.state as? Sign ?: return null
        val uuid = sign.persistentDataContainer.get(TOUR_SIGN_KEY, PersistentDataType.STRING) ?: return null
        return runCatching { UUID.fromString(uuid) }.getOrNull()?.let { getSign(worldData, it) }
    }

    fun startTour(player: Player, worldData: WorldData, tour: TourData) {
        stopTour(player, silent = true)
        plugin.tourSessionManager.start(player.uniqueId, worldData.uuid, tour.uuid)
        if (!isWorldMember(worldData, player.uniqueId)) {
            tour.startedPlayerUuids.add(player.uniqueId)
        }
        plugin.worldConfigRepository.save(worldData)
        player.sendMessage(plugin.languageManager.getMessage(player, "messages.tour.started", mapOf("tour" to tour.name)))
        player.playSound(player.location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.25f)
        showNextNavigation(player, worldData, tour, 0, sendMessage = true)
    }

    fun stopTour(player: Player, silent: Boolean = false) {
        plugin.tourSessionManager.end(player.uniqueId)
        bossBars.remove(player.uniqueId)?.let { player.hideBossBar(it) }
        navigationTasks.remove(player.uniqueId)?.cancel()
        if (!silent) {
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.tour.stopped"))
        }
    }

    fun advanceBySign(player: Player, worldData: WorldData, signUuid: UUID) {
        val session = plugin.tourSessionManager.get(player.uniqueId) ?: return
        if (session.worldUuid != worldData.uuid) return
        val tour = getTour(worldData, session.tourUuid) ?: return
        val expectedSignUuid = tour.signUuids.getOrNull(session.nextIndex) ?: return
        if (expectedSignUuid != signUuid) return
        session.nextIndex++
        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.55f, 1.55f)
        if (session.nextIndex >= tour.signUuids.size) {
            tour.completedCount++
            plugin.worldConfigRepository.save(worldData)
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.tour.completed", mapOf("tour" to tour.name)))
            player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f)
            stopTour(player, silent = true)
            return
        }
        plugin.worldConfigRepository.save(worldData)
        showNextNavigation(player, worldData, tour, session.nextIndex, sendMessage = false)
    }

    private fun showNextNavigation(player: Player, worldData: WorldData, tour: TourData, index: Int, sendMessage: Boolean) {
        val signUuid = tour.signUuids.getOrNull(index) ?: return
        val signData = getSign(worldData, signUuid) ?: return
        val mode = plugin.playerStatsRepository.findByUuid(player.uniqueId).tourNavigationMode
        if (sendMessage) {
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.tour.next_marker", mapOf("marker" to signData.title, "tour" to tour.name)))
        }
        if (mode == TourNavigationMode.NONE) {
            bossBars.remove(player.uniqueId)?.let { player.hideBossBar(it) }
            navigationTasks.remove(player.uniqueId)?.cancel()
            return
        }
        val bossBar = bossBars.computeIfAbsent(player.uniqueId) {
            BossBar.bossBar(Component.empty(), 1f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS)
        }
        updateBossBar(player, bossBar, signData, tour, index)
        player.showBossBar(bossBar)
        startNavigationTask(player, signData, tour, index)
    }

    private fun startNavigationTask(player: Player, signData: TourSignData, tour: TourData, index: Int) {
        navigationTasks.remove(player.uniqueId)?.cancel()
        val task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (!player.isOnline) return@Runnable
            val world = player.world
            val target = Location(world, signData.blockX + 0.5, signData.blockY + 0.6, signData.blockZ + 0.5)
            if (world.uid != target.world?.uid && target.world != null) return@Runnable
            val mode = plugin.playerStatsRepository.findByUuid(player.uniqueId).tourNavigationMode
            if (mode == TourNavigationMode.NONE) {
                bossBars[player.uniqueId]?.let { player.hideBossBar(it) }
                return@Runnable
            }
            val bossBar = bossBars.computeIfAbsent(player.uniqueId) {
                BossBar.bossBar(Component.empty(), 1f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS)
            }
            updateBossBar(player, bossBar, signData, tour, index)
            player.showBossBar(bossBar)
            if (mode == TourNavigationMode.ALL) {
                spawnDirectionArrow(player, target)
            }
        }, 0L, 1L)
        navigationTasks[player.uniqueId] = task
    }

    private fun updateBossBar(player: Player, bossBar: BossBar, signData: TourSignData, tour: TourData, nextIndex: Int) {
        val progress = if (tour.signUuids.isEmpty()) 0f else nextIndex.toFloat() / tour.signUuids.size.toFloat()
        val arrow = relativeArrow(player, signData)
        bossBar.name(LegacyComponentSerializer.legacySection().deserialize("§7【§a${signData.title}§7】 §b$arrow"))
        bossBar.progress(progress.coerceIn(0f, 1f))
    }

    private fun relativeArrow(player: Player, signData: TourSignData): String {
        val forward = Vector(player.location.direction.x, 0.0, player.location.direction.z).let {
            if (it.lengthSquared() < 1.0e-6) Vector(0.0, 0.0, 1.0) else it.normalize()
        }
        val right = forward.clone().crossProduct(Vector(0.0, 1.0, 0.0)).normalize()
        val toTarget = Vector(signData.blockX + 0.5 - player.location.x, 0.0, signData.blockZ + 0.5 - player.location.z)
        if (toTarget.lengthSquared() < 1.0e-6) return "↑"
        val normalized = toTarget.normalize()
        val x = normalized.dot(right)
        val y = normalized.dot(forward)
        val angle = Math.toDegrees(kotlin.math.atan2(x, y))
        return when {
            angle >= -22.5 && angle < 22.5 -> "↑"
            angle >= 22.5 && angle < 67.5 -> "↗"
            angle >= 67.5 && angle < 112.5 -> "→"
            angle >= 112.5 && angle < 157.5 -> "↘"
            angle >= 157.5 || angle < -157.5 -> "↓"
            angle >= -157.5 && angle < -112.5 -> "↙"
            angle >= -112.5 && angle < -67.5 -> "←"
            else -> "↖"
        }
    }

    fun refreshNavigation(player: Player) {
        val session = plugin.tourSessionManager.get(player.uniqueId) ?: return
        val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid) ?: return
        val tour = getTour(worldData, session.tourUuid) ?: run {
            stopTour(player, silent = true)
            return
        }
        showNextNavigation(player, worldData, tour, session.nextIndex, sendMessage = false)
    }

    private fun spawnDirectionArrow(player: Player, target: Location) {
        val eye = player.eyeLocation
        val direction = eye.direction.normalize()
        val hit = player.world.rayTraceBlocks(eye, direction, 2.0, FluidCollisionMode.NEVER, true)
        val center = hit?.hitPosition?.toLocation(player.world)?.subtract(direction.clone().multiply(0.05)) ?: eye.clone().add(direction.multiply(2.0))
        center.add(0.0, -0.24, 0.0)
        val toTarget = target.toVector().subtract(center.toVector())
        if (toTarget.lengthSquared() < 1.0e-6) return
        val forward = toTarget.normalize()
        val viewDir = eye.direction.normalize()
        var side = viewDir.clone().crossProduct(forward)
        if (side.lengthSquared() < 1.0e-6) {
            side = Vector(0, 1, 0).crossProduct(forward)
        }
        if (side.lengthSquared() < 1.0e-6) {
            side = Vector(1, 0, 0).crossProduct(forward)
        }
        side.normalize()

        val dust = Particle.DustOptions(Color.fromRGB(0, 255, 0), 0.38f)
        val tailStart = center.clone().add(forward.clone().multiply(-0.30))
        val tip = center.clone().add(forward.clone().multiply(0.30))
        val wingBack = forward.clone().multiply(-0.30 * 0.70710678118)
        val wingSide = side.clone().multiply(0.30 * 0.70710678118)
        val leftWingEnd = tip.clone().add(wingBack).add(wingSide)
        val rightWingEnd = tip.clone().add(wingBack).subtract(wingSide)

        spawnLine(player, tailStart, tip, 0.045, dust)
        spawnLine(player, tip, leftWingEnd, 0.045, dust)
        spawnLine(player, tip, rightWingEnd, 0.045, dust)
    }

    private fun spawnLine(player: Player, from: Location, to: Location, spacing: Double, dust: Particle.DustOptions) {
        val delta = to.toVector().subtract(from.toVector())
        val length = delta.length()
        if (length <= 0.0) {
            player.spawnParticle(Particle.DUST, from, 1, 0.0, 0.0, 0.0, 0.0, dust)
            return
        }
        val step = delta.normalize().multiply(spacing)
        val points = kotlin.math.max(1, kotlin.math.ceil(length / spacing).toInt())
        repeat(points + 1) { index ->
            val point = from.clone().add(step.clone().multiply(index.toDouble()))
            player.spawnParticle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0, dust)
        }
    }

    private fun cancelActiveTourSessions(worldUuid: UUID, tourUuid: UUID, messageKey: String) {
        plugin.tourSessionManager.findActiveByTour(worldUuid, tourUuid).forEach { session ->
            val player = Bukkit.getPlayer(session.playerUuid)
            if (player != null && player.isOnline) {
                stopTour(player, silent = true)
                player.sendMessage(plugin.languageManager.getMessage(player, messageKey))
            } else {
                plugin.tourSessionManager.end(session.playerUuid)
            }
        }
    }

    fun sendArrivalMessage(player: Player, worldData: WorldData) {
        val message = Component.text(plugin.languageManager.getMessage(player, "messages.tour.available", mapOf("world" to worldData.name)))
            .clickEvent(ClickEvent.runCommand("/tour"))
        player.sendMessage(message)
    }
}
