package me.awabi2048.myworldmanager.service

import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.util.BorderResetBounds
import me.awabi2048.myworldmanager.util.BorderResetSpawnCalculator
import me.awabi2048.myworldmanager.util.BorderResetSpawnChanges
import me.awabi2048.myworldmanager.util.BorderResetSpawnPosition
import org.bukkit.Location
import org.bukkit.World
import kotlin.math.abs
import kotlin.math.floor

/**
 * ボーダー変更後の保存スポーン補正を一元化する。
 * 通常リセット、公開APIのstepBack、確認画面は同じ計算結果を参照する。
 */
class BorderResetSpawnService {
    fun apply(world: World, worldData: WorldData): BorderResetSpawnChanges {
        val changes = calculate(
            world = world,
            worldData = worldData,
            center = world.worldBorder.center,
            size = world.worldBorder.size
        )
        applyChange(world, worldData.spawnPosGuest, changes.guest) { worldData.spawnPosGuest = it }
        applyChange(world, worldData.spawnPosMember, changes.member) { worldData.spawnPosMember = it }
        return changes
    }

    fun preview(
        world: World,
        worldData: WorldData,
        center: Location,
        size: Double
    ): BorderResetSpawnChanges {
        return calculate(world, worldData, center, size)
    }

    private fun calculate(
        world: World,
        worldData: WorldData,
        center: Location,
        size: Double
    ): BorderResetSpawnChanges {
        val defaultPosition = world.spawnLocation.toPosition()
        val resultingBorder = BorderResetBounds.centeredAt(center.x, center.z, size)
        val target = resultingBorder.clampInside(defaultPosition.x, defaultPosition.z)
        val safeYCoordinates = target?.let { (x, z) ->
            findSafeYCoordinates(world, x, defaultPosition.y, z)
        } ?: emptyList()
        return BorderResetSpawnCalculator.calculate(
            guest = worldData.spawnPosGuest?.toPosition(),
            member = worldData.spawnPosMember?.toPosition(),
            defaultPosition = defaultPosition,
            resultingBorder = resultingBorder,
            safeYCoordinates = safeYCoordinates
        )
    }

    private fun findSafeYCoordinates(world: World, xPosition: Double, preferredYPosition: Double, zPosition: Double): List<Int> {
        // KotlinのtoIntは負数を0方向へ丸めるため、ブロック座標にはfloorを使う。
        val x = floor(xPosition).toInt()
        val z = floor(zPosition).toInt()
        if (!world.isChunkLoaded(x shr 4, z shr 4)) return emptyList()

        val minY = world.minHeight + 1
        val maxY = world.maxHeight - 2
        if (minY > maxY) return emptyList()

        // デフォルトYを優先し、同じX/Zで最も近い安全なYを決定する。
        val preferredY = preferredYPosition.toInt().coerceIn(minY, maxY)
        return (minY..maxY)
            .sortedBy { abs(it - preferredY) }
            .filter { y -> isSafeStandingLocation(world, x, y, z) }
    }

    private fun isSafeStandingLocation(world: World, x: Int, y: Int, z: Int): Boolean {
        val support = world.getBlockAt(x, y - 1, z)
        val feet = world.getBlockAt(x, y, z)
        val head = world.getBlockAt(x, y + 1, z)
        return support.type.isSolid && feet.type.isAir && head.type.isAir
    }

    private fun applyChange(
        world: World,
        current: Location?,
        change: me.awabi2048.myworldmanager.util.BorderResetSpawnChange?,
        setter: (Location) -> Unit
    ) {
        if (current == null || change == null) return
        val replacement = current.clone().apply {
            this.world = world
            x = change.replacement.x
            y = change.replacement.y
            z = change.replacement.z
        }
        setter(replacement)
    }

    private fun Location.toPosition(): BorderResetSpawnPosition {
        return BorderResetSpawnPosition(x, y, z)
    }
}
