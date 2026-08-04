package me.awabi2048.myworldmanager.service

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.session.SettingsAction
import me.awabi2048.myworldmanager.util.PlayerBlockTargetResolver
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

/** スポーン設定中のプレビュー描画とライフサイクルを管理します。 */
internal class WorldSettingsSpawnPreviewService(private val plugin: MyWorldManager) {
    private val tasks = mutableMapOf<UUID, BukkitTask>()

    fun start(player: Player) {
        stop(player)
        tasks[player.uniqueId] = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (!player.isOnline) {
                stop(player)
                return@Runnable
            }
            val session = plugin.settingsSessionManager.getSession(player)
            if (session == null ||
                (session.action != SettingsAction.SET_SPAWN_GUEST && session.action != SettingsAction.SET_SPAWN_MEMBER)
            ) {
                stop(player)
                return@Runnable
            }
            val targetBlock = PlayerBlockTargetResolver.find(player) ?: return@Runnable
            val spawnLoc = targetBlock.location.clone().add(0.5, 1.0, 0.5)
            val yaw = normalizeToCardinalYaw(player.location.yaw)
            spawnSpawnPreview(player, spawnLoc, yaw, isSpawnAreaPlaceable(spawnLoc))
        }, 0L, 2L)
    }

    fun stop(player: Player) {
        tasks.remove(player.uniqueId)?.cancel()
    }

    fun isSpawnAreaPlaceable(spawnLoc: Location): Boolean {
        val feetBlock = spawnLoc.block
        val headBlock = spawnLoc.clone().add(0.0, 1.0, 0.0).block
        return feetBlock.isPassable && headBlock.isPassable
    }

    fun normalizeToCardinalYaw(yawRaw: Float): Float {
        var yaw = yawRaw
        while (yaw < 0) yaw += 360
        while (yaw >= 360) yaw -= 360
        return when {
            yaw >= 45 && yaw < 135 -> 90.0f
            yaw >= 135 && yaw < 225 -> 180.0f
            yaw >= 225 && yaw < 315 -> 270.0f
            else -> 0.0f
        }
    }

    fun showSpawnConfirmEffect(player: Player, spawnLoc: Location, isGuest: Boolean) {
        val waxParticle = if (isGuest) Particle.WAX_ON else Particle.WAX_OFF
        val blockY = spawnLoc.blockY
        spawnSpawnBlockOutlineWax(player, spawnLoc.blockX, blockY, spawnLoc.blockZ, waxParticle, true, false)
        spawnSpawnBlockOutlineWax(player, spawnLoc.blockX, blockY + 1, spawnLoc.blockZ, waxParticle, false, true)
    }

    fun spawnDirectionArrow(player: Player, start: Location, yaw: Float, dust: Particle.DustOptions) {
        val rad = Math.toRadians(yaw.toDouble())
        val forwardX = -kotlin.math.sin(rad)
        val forwardZ = kotlin.math.cos(rad)
        val tipX = start.x + forwardX
        val tipZ = start.z + forwardZ
        spawnLineWithTenParticles(player, start.x, start.y, start.z, tipX, start.y, tipZ, dust)
        val baseX = tipX - forwardX * 0.4
        val baseZ = tipZ - forwardZ * 0.4
        val sideX = -forwardZ * 0.2
        val sideZ = forwardX * 0.2
        spawnLineWithFiveParticles(player, tipX, start.y, tipZ, baseX + sideX, start.y, baseZ + sideZ, dust)
        spawnLineWithFiveParticles(player, tipX, start.y, tipZ, baseX - sideX, start.y, baseZ - sideZ, dust)
    }

    private fun spawnSpawnPreview(player: Player, spawnLoc: Location, yaw: Float, placeable: Boolean) {
        val world = spawnLoc.world ?: return
        val frameDust = Particle.DustOptions(if (placeable) Color.fromRGB(64, 255, 120) else Color.fromRGB(255, 80, 80), 0.5f)
        val arrowDust = Particle.DustOptions(Color.fromRGB(80, 160, 255), 0.5f)
        val feetBlockY = spawnLoc.blockY
        spawnSpawnBlockOutline(player, spawnLoc.blockX, feetBlockY, spawnLoc.blockZ, frameDust, true, false)
        spawnSpawnBlockOutline(player, spawnLoc.blockX, feetBlockY + 1, spawnLoc.blockZ, frameDust, false, true)
        val rad = Math.toRadians(yaw.toDouble())
        val arrowStart = Location(world, spawnLoc.x + kotlin.math.sin(rad) * 0.5, spawnLoc.y + 0.15, spawnLoc.z - kotlin.math.cos(rad) * 0.5)
        spawnDirectionArrow(player, arrowStart, yaw, arrowDust)
    }

    private fun spawnSpawnBlockOutline(player: Player, blockX: Int, blockY: Int, blockZ: Int, dust: Particle.DustOptions, drawBottomFace: Boolean, drawTopFace: Boolean) {
        val minX = blockX.toDouble(); val minY = blockY.toDouble(); val minZ = blockZ.toDouble()
        val maxX = blockX + 1.0; val maxY = blockY + 1.0; val maxZ = blockZ + 1.0
        if (drawBottomFace) {
            spawnLineWithTenParticles(player, minX, minY, minZ, maxX, minY, minZ, dust)
            spawnLineWithTenParticles(player, minX, minY, maxZ, maxX, minY, maxZ, dust)
            spawnLineWithTenParticles(player, minX, minY, minZ, minX, minY, maxZ, dust)
            spawnLineWithTenParticles(player, maxX, minY, minZ, maxX, minY, maxZ, dust)
        }
        if (drawTopFace) {
            spawnLineWithTenParticles(player, minX, maxY, minZ, maxX, maxY, minZ, dust)
            spawnLineWithTenParticles(player, minX, maxY, maxZ, maxX, maxY, maxZ, dust)
            spawnLineWithTenParticles(player, minX, maxY, minZ, minX, maxY, maxZ, dust)
            spawnLineWithTenParticles(player, maxX, maxY, minZ, maxX, maxY, maxZ, dust)
        }
        spawnLineWithTenParticles(player, minX, minY, minZ, minX, maxY, minZ, dust)
        spawnLineWithTenParticles(player, maxX, minY, minZ, maxX, maxY, minZ, dust)
        spawnLineWithTenParticles(player, minX, minY, maxZ, minX, maxY, maxZ, dust)
        spawnLineWithTenParticles(player, maxX, minY, maxZ, maxX, maxY, maxZ, dust)
    }

    private fun spawnLineWithFiveParticles(player: Player, startX: Double, startY: Double, startZ: Double, endX: Double, endY: Double, endZ: Double, dust: Particle.DustOptions) {
        for (i in 0..4) {
            val t = i.toDouble() / 4.0
            player.spawnParticle(Particle.DUST, startX + (endX - startX) * t, startY + (endY - startY) * t, startZ + (endZ - startZ) * t, 1, 0.0, 0.0, 0.0, 0.0, dust)
        }
    }

    private fun spawnLineWithTenParticles(player: Player, startX: Double, startY: Double, startZ: Double, endX: Double, endY: Double, endZ: Double, dust: Particle.DustOptions) {
        for (i in 0..9) {
            val t = i.toDouble() / 9.0
            player.spawnParticle(Particle.DUST, startX + (endX - startX) * t, startY + (endY - startY) * t, startZ + (endZ - startZ) * t, 1, 0.0, 0.0, 0.0, 0.0, dust)
        }
    }

    private fun spawnLineWithTenParticlesWax(player: Player, startX: Double, startY: Double, startZ: Double, endX: Double, endY: Double, endZ: Double, particle: Particle) {
        for (i in 0..9) {
            val t = i.toDouble() / 9.0
            player.spawnParticle(particle, startX + (endX - startX) * t, startY + (endY - startY) * t, startZ + (endZ - startZ) * t, 1, 0.0, 0.0, 0.0, 0.0)
        }
    }

    private fun spawnSpawnBlockOutlineWax(player: Player, blockX: Int, blockY: Int, blockZ: Int, particle: Particle, drawBottomFace: Boolean, drawTopFace: Boolean) {
        val minX = blockX.toDouble(); val minY = blockY.toDouble(); val minZ = blockZ.toDouble()
        val maxX = blockX + 1.0; val maxY = blockY + 1.0; val maxZ = blockZ + 1.0
        if (drawBottomFace) {
            spawnLineWithTenParticlesWax(player, minX, minY, minZ, maxX, minY, minZ, particle)
            spawnLineWithTenParticlesWax(player, minX, minY, maxZ, maxX, minY, maxZ, particle)
            spawnLineWithTenParticlesWax(player, minX, minY, minZ, minX, minY, maxZ, particle)
            spawnLineWithTenParticlesWax(player, maxX, minY, minZ, maxX, minY, maxZ, particle)
        }
        if (drawTopFace) {
            spawnLineWithTenParticlesWax(player, minX, maxY, minZ, maxX, maxY, minZ, particle)
            spawnLineWithTenParticlesWax(player, minX, maxY, maxZ, maxX, maxY, maxZ, particle)
            spawnLineWithTenParticlesWax(player, minX, maxY, minZ, minX, maxY, maxZ, particle)
            spawnLineWithTenParticlesWax(player, maxX, maxY, minZ, maxX, maxY, maxZ, particle)
        }
        spawnLineWithTenParticlesWax(player, minX, minY, minZ, minX, maxY, minZ, particle)
        spawnLineWithTenParticlesWax(player, maxX, minY, minZ, maxX, maxY, minZ, particle)
        spawnLineWithTenParticlesWax(player, minX, minY, maxZ, minX, maxY, maxZ, particle)
        spawnLineWithTenParticlesWax(player, maxX, minY, maxZ, maxX, maxY, maxZ, particle)
    }
}
