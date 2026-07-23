package me.awabi2048.myworldmanager.service

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.TourData
import me.awabi2048.myworldmanager.model.TourNavigationMode
import me.awabi2048.myworldmanager.model.TourSignData
import me.awabi2048.myworldmanager.model.TourWaypointData
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
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Sign
import org.bukkit.block.data.Directional
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2
import kotlin.math.ceil

object TourParticipationPolicy {
    fun cannotStartTour(worldData: WorldData, playerUuid: UUID): Boolean {
        return worldData.owner == playerUuid || worldData.moderators.contains(playerUuid)
    }
}

class TourManager(private val plugin: MyWorldManager) {
    companion object {
        const val DEFAULT_TOUR_LIMIT = 7
        const val MAX_WAYPOINTS_PER_TOUR = 28
        const val MAX_START_SIGNS_PER_WORLD = 64
        const val MAX_TITLE_LENGTH = 15
        const val MAX_DESCRIPTION_LENGTH = 30
        private const val WAYPOINT_REACH_DISTANCE = 4.0
        private const val START_SIGN_NOTICE_RADIUS = 10.0
        private const val START_SIGN_NOTICE_COOLDOWN_MILLIS = 30_000L
        private const val TOUR_PROGRESS_CHECK_INTERVAL_TICKS = 20L
        private const val START_SIGN_NOTICE_CHECK_INTERVAL_TICKS = 20L
        private const val ARROW_INTERVAL_TICKS = 2L
        private const val PARTICLE_INTERVAL_TICKS = 10L
        val TOUR_SIGN_KEY = org.bukkit.NamespacedKey("myworldmanager", "tour_sign_uuid")
    }

    enum class StartTourResult {
        STARTED,
        WORLD_MEMBER,
        INVALID_TOUR,
        WRONG_WORLD
    }

    private data class StartSignNoticeState(
        var insideSignUuids: Set<UUID> = emptySet(),
        var lastNotifiedAt: Long = 0L
    )

    private val bossBars = ConcurrentHashMap<UUID, BossBar>()
    private val arrowTasks = ConcurrentHashMap<UUID, BukkitTask>()
    private val particleTasks = ConcurrentHashMap<UUID, BukkitTask>()
    private val startSignNoticeStates = ConcurrentHashMap<UUID, StartSignNoticeState>()
    private var progressCheckTask: BukkitTask? = null
    private var startSignNoticeTask: BukkitTask? = null

    init {
        startProgressCheckTask()
        startStartSignNoticeTask()
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            restoreActiveSessions()
        }, 40L)
    }

    fun shutdown() {
        progressCheckTask?.cancel()
        progressCheckTask = null
        startSignNoticeTask?.cancel()
        startSignNoticeTask = null
        arrowTasks.values.forEach { it.cancel() }
        arrowTasks.clear()
        particleTasks.values.forEach { it.cancel() }
        particleTasks.clear()
        bossBars.forEach { (uuid, bar) -> Bukkit.getPlayer(uuid)?.hideBossBar(bar) }
        bossBars.clear()
        startSignNoticeStates.clear()
    }

    fun isWorldMember(worldData: WorldData, playerUuid: UUID): Boolean {
        return worldData.owner == playerUuid ||
            worldData.moderators.contains(playerUuid) ||
            worldData.members.contains(playerUuid)
    }

    fun cannotStartTour(worldData: WorldData, playerUuid: UUID): Boolean {
        return TourParticipationPolicy.cannotStartTour(worldData, playerUuid)
    }

    fun canManage(worldData: WorldData, playerUuid: UUID): Boolean {
        return worldData.owner == playerUuid || worldData.moderators.contains(playerUuid)
    }

    fun getTourLimit(player: Player, worldData: WorldData): Int {
        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        return stats.tourSlotsByWorld[worldData.uuid] ?: DEFAULT_TOUR_LIMIT
    }

    fun canPlaceSign(worldData: WorldData): Boolean = worldData.tourSigns.size < MAX_START_SIGNS_PER_WORLD

    fun validTours(worldData: WorldData): List<TourData> =
        worldData.tours.filter { it.startSignUuid != null && it.waypoints.size >= 2 }

    fun hasValidTour(worldData: WorldData): Boolean = validTours(worldData).isNotEmpty()

    fun getSign(worldData: WorldData, signUuid: UUID): TourSignData? = worldData.tourSigns.find { it.uuid == signUuid }

    fun getTour(worldData: WorldData, tourUuid: UUID): TourData? = worldData.tours.find { it.uuid == tourUuid }

    fun findToursBySign(worldData: WorldData, signUuid: UUID): List<TourData> =
        worldData.tours.filter { it.startSignUuid == signUuid && it.waypoints.size >= 2 }

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
            if (draft.createdBy == null) {
                draft.createdBy = player.uniqueId
            }
            worldData.tours.add(draft)
            draft
        } else {
            target.name = draft.name
            target.description = draft.description
            target.icon = draft.icon
            target.startSignUuid = draft.startSignUuid
            target.waypoints.clear()
            target.waypoints.addAll(
                draft.waypoints.map {
                    TourWaypointData(
                        uuid = it.uuid,
                        name = it.name,
                        blockX = it.blockX,
                        blockY = it.blockY,
                        blockZ = it.blockZ,
                        createdAt = it.createdAt
                    )
                }
            )
            target.completedCount = draft.completedCount
            target.startedPlayerUuids.clear()
            target.startedPlayerUuids.addAll(draft.startedPlayerUuids)
            if (target.createdBy == null) {
                target.createdBy = draft.createdBy
            }
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
        return createTourSignAt(worldData, player, signBlock, blockFace, title, description)
    }

    fun createTourSignAt(worldData: WorldData, player: Player, signBlock: Block, blockFace: BlockFace, title: String, description: String): TourSignData {
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
        placeSignBlockAt(signData, signBlock, blockFace, player)
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
        placeSignBlockAt(signData, signBlock, blockFace, player)
    }

    private fun placeSignBlockAt(signData: TourSignData, signBlock: Block, blockFace: BlockFace, player: Player) {
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
        val boundTour = worldData.tours.find { it.startSignUuid == signData.uuid }
        val tourName = boundTour?.name ?: ""
        sign.getSide(org.bukkit.block.sign.Side.FRONT).line(0, Component.text("§3［Tour］"))
        sign.getSide(org.bukkit.block.sign.Side.FRONT).line(1, Component.text("§8${signVisitCount(worldData, signData.uuid)} Visitors"))
        if (tourName.isNotEmpty()) {
            sign.getSide(org.bukkit.block.sign.Side.FRONT).line(2, Component.text("§f【$tourName】"))
        } else {
            sign.getSide(org.bukkit.block.sign.Side.FRONT).line(2, Component.empty())
        }
        sign.getSide(org.bukkit.block.sign.Side.FRONT).line(3, Component.text("§e§nクリック！"))
        sign.update()
    }

    fun removeTourSign(worldData: WorldData, signUuid: UUID, returnItemTo: Player? = null) {
        val signData = getSign(worldData, signUuid) ?: return
        val affectedTourUuids = worldData.tours.filter { it.startSignUuid == signUuid }.map { it.uuid }
        val world = Bukkit.getWorld(plugin.worldService.getWorldFolderName(worldData))
        val block = world?.getBlockAt(signData.blockX, signData.blockY, signData.blockZ)
        if (block != null && (block.type == Material.PALE_OAK_SIGN || block.type == Material.PALE_OAK_WALL_SIGN)) {
            block.type = Material.AIR
        }
        worldData.tours.forEach {
            if (it.startSignUuid == signUuid) {
                it.startSignUuid = null
            }
        }
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

    fun startTour(player: Player, worldData: WorldData, tour: TourData): StartTourResult {
        if (cannotStartTour(worldData, player.uniqueId)) return StartTourResult.WORLD_MEMBER
        if (tour.startSignUuid == null || tour.waypoints.size < 2) return StartTourResult.INVALID_TOUR
        val world = Bukkit.getWorld(plugin.worldService.getWorldFolderName(worldData)) ?: return StartTourResult.WRONG_WORLD
        if (player.world.uid != world.uid) return StartTourResult.WRONG_WORLD
        stopTour(player, silent = true)
        plugin.tourSessionManager.start(player.uniqueId, worldData.uuid, tour.uuid)
        tour.startedPlayerUuids.add(player.uniqueId)
        tour.activePlayerProgress[player.uniqueId] = 0
        plugin.worldConfigRepository.save(worldData)
        player.sendMessage(plugin.languageManager.getMessage(player, "messages.tour.started", mapOf("tour" to tour.name)))
        player.playSound(player.location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.25f)
        showNextNavigation(player, worldData, tour, 0, sendMessage = true)
        return StartTourResult.STARTED
    }

    fun stopTour(player: Player, silent: Boolean = false) {
        val session = plugin.tourSessionManager.get(player.uniqueId)
        plugin.tourSessionManager.end(player.uniqueId)
        bossBars.remove(player.uniqueId)?.let { player.hideBossBar(it) }
        arrowTasks.remove(player.uniqueId)?.cancel()
        particleTasks.remove(player.uniqueId)?.cancel()
        if (session != null) {
            val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid)
            val tour = worldData?.let { getTour(it, session.tourUuid) }
            tour?.activePlayerProgress?.remove(player.uniqueId)
            if (worldData != null && tour != null) {
                plugin.worldConfigRepository.save(worldData)
            }
        }
        if (!silent) {
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.tour.stopped"))
        }
    }

    fun advanceByWaypoint(player: Player, worldData: WorldData) {
        val session = plugin.tourSessionManager.get(player.uniqueId) ?: return
        if (session.worldUuid != worldData.uuid) return
        val tour = getTour(worldData, session.tourUuid) ?: return
        val waypoint = tour.waypoints.getOrNull(session.nextIndex) ?: return
        if (player.world.uid != Bukkit.getWorld(plugin.worldService.getWorldFolderName(worldData))?.uid) return

        val target = waypointCenter(player.world, waypoint)
        if (player.location.distance(target) > WAYPOINT_REACH_DISTANCE) return

        session.nextIndex++
        tour.activePlayerProgress[player.uniqueId] = session.nextIndex
        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.55f, 1.55f)
        if (session.nextIndex >= tour.waypoints.size) {
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
        val waypoint = tour.waypoints.getOrNull(index) ?: return
        val mode = plugin.playerStatsRepository.findByUuid(player.uniqueId).tourNavigationMode
        if (sendMessage) {
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.tour.next_marker", mapOf("marker" to waypoint.name, "tour" to tour.name)))
        }
        if (mode == TourNavigationMode.NONE) {
            bossBars.remove(player.uniqueId)?.let { player.hideBossBar(it) }
            arrowTasks.remove(player.uniqueId)?.cancel()
            particleTasks.remove(player.uniqueId)?.cancel()
            return
        }
        val bossBar = bossBars.computeIfAbsent(player.uniqueId) {
            BossBar.bossBar(Component.empty(), 1f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS)
        }
        updateNavigationDisplay(player, worldData, tour, index, bossBar)
        player.showBossBar(bossBar)
        startVisualTask(player, worldData, tour)
    }

    private fun startVisualTask(player: Player, worldData: WorldData, tour: TourData) {
        arrowTasks.remove(player.uniqueId)?.cancel()
        particleTasks.remove(player.uniqueId)?.cancel()

        val arrowTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (!player.isOnline) {
                stopTour(player, silent = true)
                return@Runnable
            }
            val session = plugin.tourSessionManager.get(player.uniqueId) ?: return@Runnable
            if (session.worldUuid != worldData.uuid || session.tourUuid != tour.uuid) return@Runnable
            val mode = plugin.playerStatsRepository.findByUuid(player.uniqueId).tourNavigationMode
            if (mode == TourNavigationMode.NONE) return@Runnable
            val currentWorldData = plugin.worldConfigRepository.findByUuid(worldData.uuid) ?: return@Runnable
            val currentTour = getTour(currentWorldData, tour.uuid) ?: return@Runnable
            val bossBar = bossBars.computeIfAbsent(player.uniqueId) {
                BossBar.bossBar(Component.empty(), 1f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS)
            }
            updateNavigationDisplay(player, currentWorldData, currentTour, session.nextIndex, bossBar)
            player.showBossBar(bossBar)
            if (mode == TourNavigationMode.ALL) {
                spawnDirectionArrow(player, currentTour, session.nextIndex)
            }
        }, 0L, ARROW_INTERVAL_TICKS)
        arrowTasks[player.uniqueId] = arrowTask

        val particleTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (!player.isOnline) {
                stopTour(player, silent = true)
                return@Runnable
            }
            val session = plugin.tourSessionManager.get(player.uniqueId) ?: return@Runnable
            if (session.worldUuid != worldData.uuid || session.tourUuid != tour.uuid) return@Runnable
            val currentWorldData = plugin.worldConfigRepository.findByUuid(worldData.uuid) ?: return@Runnable
            val currentTour = getTour(currentWorldData, tour.uuid) ?: return@Runnable
            spawnWaypointParticles(player, currentTour, session.nextIndex)
        }, 0L, PARTICLE_INTERVAL_TICKS)
        particleTasks[player.uniqueId] = particleTask
    }

    private fun updateNavigationDisplay(player: Player, worldData: WorldData, tour: TourData, nextIndex: Int, bossBar: BossBar) {
        val waypoint = tour.waypoints.getOrNull(nextIndex) ?: return
        val world = Bukkit.getWorld(plugin.worldService.getWorldFolderName(worldData)) ?: return
        if (player.world.uid != world.uid) return
        val target = waypointCenter(world, waypoint)
        val distance = player.location.distance(target)
        val rounded = formatDistance(distance)
        val progress = if (tour.waypoints.isEmpty()) 0f else nextIndex.toFloat() / tour.waypoints.size.toFloat()
        val arrow = relativeArrow(player, target)
        bossBar.name(LegacyComponentSerializer.legacySection().deserialize("§7【§a${waypoint.name}§7】 §b$arrow §7($rounded ブロック )"))
        bossBar.progress(progress.coerceIn(0f, 1f))
        player.sendActionBar(
            LegacyComponentSerializer.legacySection().deserialize(
                "§7ワールドツアー§b【${tour.name}】 §7(次の経由地まで $rounded ブロック )"
            )
        )
    }

    private fun relativeArrow(player: Player, target: Location): String {
        val yawRad = Math.toRadians(player.location.yaw.toDouble())
        val forward = Vector(-kotlin.math.sin(yawRad), 0.0, kotlin.math.cos(yawRad)).normalize()
        val right = forward.clone().crossProduct(Vector(0.0, 1.0, 0.0)).normalize()
        val toTarget = target.toVector().subtract(player.location.toVector()).setY(0)
        if (toTarget.lengthSquared() < 1.0e-6) return "↑"
        val normalized = toTarget.normalize()
        val x = normalized.dot(right)
        val y = normalized.dot(forward)
        val angle = Math.toDegrees(atan2(x, y))
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

    private fun spawnDirectionArrow(player: Player, tour: TourData, nextIndex: Int) {
        val waypoint = tour.waypoints.getOrNull(nextIndex) ?: return
        val target = waypointCenter(player.world, waypoint)
        val base = player.location.clone().add(0.0, 0.75, 0.0)
        val yawRad = Math.toRadians(player.location.yaw.toDouble())
        val front = Vector(-kotlin.math.sin(yawRad), 0.0, kotlin.math.cos(yawRad)).normalize()
        val center = base.clone().add(front.clone().multiply(1.5))
        val toTarget = target.toVector().subtract(center.toVector())
        if (toTarget.lengthSquared() < 1.0e-6) return
        val direction = toTarget.normalize()
        var side = Vector(0.0, 1.0, 0.0).crossProduct(direction)
        if (side.lengthSquared() < 1.0e-6) {
            side = Vector(1.0, 0.0, 0.0)
        }
        side.normalize()

        val distance = player.location.distance(target)
        var size = (0.5 + ((distance - 5.0) / 45.0).coerceIn(0.0, 1.0) * 0.5).toFloat()
        size *= 0.5f
        val dust = Particle.DustOptions(Color.fromRGB(0x00, 0xff, 0xff), size)
        val shaftHalf = size * 0.6
        val tail = center.clone().add(direction.clone().multiply(-shaftHalf))
        val tip = center.clone().add(direction.clone().multiply(shaftHalf))
        val wingBack = direction.clone().multiply(-shaftHalf * 0.6)
        val wingSide = side.clone().multiply(shaftHalf * 0.5)
        val leftWingEnd = tip.clone().add(wingBack).add(wingSide)
        val rightWingEnd = tip.clone().add(wingBack).subtract(wingSide)

        spawnLine(player, tail, tip, 0.02, dust)
        spawnLine(player, tip, leftWingEnd, 0.02, dust)
        spawnLine(player, tip, rightWingEnd, 0.02, dust)
    }

    private fun spawnWaypointParticles(player: Player, tour: TourData, nextIndex: Int) {
        val world = player.world
        val completedColor = Color.fromRGB(0xbb, 0xbb, 0xff)
        val currentColor = Color.fromRGB(0xbb, 0xff, 0xbb)
        val futureColor = Color.fromRGB(0xbb, 0xff, 0xff)
        val white = Color.fromRGB(0xff, 0xff, 0xff)

        tour.waypoints.forEachIndexed { index, waypoint ->
            val center = waypointCenter(world, waypoint)
            val startColor = when {
                index < nextIndex -> completedColor
                index == nextIndex -> currentColor
                else -> futureColor
            }
            val count = if (index == nextIndex) 90 else 30
            val transition = Particle.DustTransition(startColor, white, 0.5f)
            player.spawnParticle(
                Particle.DUST_COLOR_TRANSITION,
                center,
                count,
                1.5,
                1.5,
                1.5,
                0.0,
                transition
            )
        }
    }

    private fun spawnLine(player: Player, from: Location, to: Location, spacing: Double, dust: Particle.DustOptions) {
        val delta = to.toVector().subtract(from.toVector())
        val length = delta.length()
        if (length <= 0.0) {
            player.spawnParticle(Particle.DUST, from, 1, 0.0, 0.0, 0.0, 0.0, dust)
            return
        }
        val step = delta.normalize().multiply(spacing)
        val points = kotlin.math.max(1, ceil(length / spacing).toInt())
        repeat(points + 1) { index ->
            val point = from.clone().add(step.clone().multiply(index.toDouble()))
            player.spawnParticle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0, dust)
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

    private fun startProgressCheckTask() {
        progressCheckTask?.cancel()
        progressCheckTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            val sessions = plugin.tourSessionManager.getAllSessions()
            sessions.forEach { session ->
                val player = Bukkit.getPlayer(session.playerUuid) ?: return@forEach
                if (!player.isOnline) {
                    plugin.tourSessionManager.end(session.playerUuid)
                    return@forEach
                }
                val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid) ?: return@forEach
                advanceByWaypoint(player, worldData)
            }
        }, 20L, TOUR_PROGRESS_CHECK_INTERVAL_TICKS)
    }

    private fun startStartSignNoticeTask() {
        startSignNoticeTask?.cancel()
        startSignNoticeTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            Bukkit.getOnlinePlayers().forEach { player ->
                val worldData = plugin.worldConfigRepository.findByWorldName(player.world.name) ?: return@forEach
                if (cannotStartTour(worldData, player.uniqueId)) return@forEach
                val startSigns = worldData.tours
                    .filter { it.startSignUuid != null && it.waypoints.size >= 2 }
                    .mapNotNull { tour ->
                        val sign = getSign(worldData, tour.startSignUuid ?: return@mapNotNull null) ?: return@mapNotNull null
                        Triple(tour, sign, signLocation(player.world, sign))
                    }

                val insideNow = startSigns
                    .filter { (_, _, location) -> player.location.distance(location) <= START_SIGN_NOTICE_RADIUS }
                    .map { (_, sign, _) -> sign.uuid }
                    .toSet()

                val state = startSignNoticeStates.computeIfAbsent(player.uniqueId) { StartSignNoticeState() }
                val entered = insideNow.firstOrNull { it !in state.insideSignUuids }
                val now = System.currentTimeMillis()
                if (entered != null && now - state.lastNotifiedAt >= START_SIGN_NOTICE_COOLDOWN_MILLIS) {
                    val target = startSigns.firstOrNull { (_, sign, _) -> sign.uuid == entered }
                    if (target != null) {
                        val (tour, _, location) = target
                        val distance = formatDistance(player.location.distance(location))
                        val arrow = relativeArrow(player, location)
                        player.sendMessage("§e近くにワールドツアー §b【${tour.name}】 のスタート看板があります！")
                        player.sendMessage("§7看板をクリックして、ツアーを開始することができます §7($arrow $distance ブロック )")
                        state.lastNotifiedAt = now
                    }
                }
                state.insideSignUuids = insideNow
            }
        }, 20L, START_SIGN_NOTICE_CHECK_INTERVAL_TICKS)
    }

    private fun signLocation(world: org.bukkit.World, signData: TourSignData): Location {
        return Location(world, signData.blockX + 0.5, signData.blockY + 0.5, signData.blockZ + 0.5)
    }

    private fun waypointCenter(world: org.bukkit.World, waypoint: TourWaypointData): Location {
        return Location(world, waypoint.blockX + 0.5, waypoint.blockY + 0.5, waypoint.blockZ + 0.5)
    }

    private fun formatDistance(distance: Double): String {
        return if (distance < 10) {
            String.format("%.1f", distance)
        } else {
            distance.toInt().toString()
        }
    }

    fun addWaypoint(session: TourEditSession, location: Location): TourWaypointData {
        val createdAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val waypoint = TourWaypointData(
            uuid = UUID.randomUUID(),
            name = "中継点${session.draft.waypoints.size + 1}",
            blockX = location.blockX,
            blockY = location.blockY + 1,
            blockZ = location.blockZ,
            createdAt = createdAt
        )
        session.draft.waypoints.add(waypoint)
        return waypoint
    }

    fun createTour(name: String, description: String, createdBy: UUID, worldData: WorldData): TourData {
        val tour = TourData(
            uuid = UUID.randomUUID(),
            name = name,
            description = description,
            icon = Material.OAK_BOAT,
            createdBy = createdBy
        )
        worldData.tours.add(tour)
        plugin.worldConfigRepository.save(worldData)
        return tour
    }

    private fun cancelActiveTourSessions(worldUuid: UUID, tourUuid: UUID, messageKey: String) {
        val worldData = plugin.worldConfigRepository.findByUuid(worldUuid)
        val tour = worldData?.let { getTour(it, tourUuid) }
        val progressUuids = tour?.activePlayerProgress?.keys?.toSet() ?: emptySet()
        plugin.tourSessionManager.findActiveByTour(worldUuid, tourUuid).forEach { session ->
            val player = Bukkit.getPlayer(session.playerUuid)
            if (player != null && player.isOnline) {
                stopTour(player, silent = true)
                player.sendMessage(plugin.languageManager.getMessage(player, messageKey))
            } else {
                plugin.tourSessionManager.end(session.playerUuid)
            }
        }
        progressUuids.forEach { tour?.activePlayerProgress?.remove(it) }
        if (worldData != null && tour != null) {
            plugin.worldConfigRepository.save(worldData)
        }
    }

    private fun restoreActiveSessions() {
        plugin.worldConfigRepository.loadAll()
        val allWorldData = plugin.worldConfigRepository.findAll()
        for (worldData in allWorldData) {
            for (tour in worldData.tours) {
                for ((playerUuid, nextIndex) in tour.activePlayerProgress.toMap()) {
                    val player = Bukkit.getPlayer(playerUuid) ?: continue
                    if (!player.isOnline) continue
                    val safeIndex = nextIndex.coerceIn(0, (tour.waypoints.size - 1).coerceAtLeast(0))
                    if (tour.waypoints.isEmpty()) {
                        tour.activePlayerProgress.remove(playerUuid)
                        continue
                    }
                    plugin.tourSessionManager.start(playerUuid, worldData.uuid, tour.uuid).apply {
                        this.nextIndex = safeIndex
                    }
                    showNextNavigation(player, worldData, tour, safeIndex, sendMessage = true)
                }
            }
        }
    }

    fun sendArrivalMessage(player: Player, worldData: WorldData) {
        val message = Component.text(plugin.languageManager.getMessage(player, "messages.tour.available", mapOf("world" to worldData.name)))
            .clickEvent(ClickEvent.runCommand("/tour"))
        player.sendMessage(message)
    }
}
