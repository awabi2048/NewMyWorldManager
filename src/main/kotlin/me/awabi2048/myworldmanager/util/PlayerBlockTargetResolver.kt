package me.awabi2048.myworldmanager.util

import org.bukkit.FluidCollisionMode
import org.bukkit.block.Block
import org.bukkit.entity.Player

/**
 * プレイヤーの視線から、位置設定に使うブロックを解決します。
 *
 * 草や雪などの通過可能ブロックを透過し、実際に位置の基準とするブロックを
 * 取得します。ツアー経由地点とワールドスポーンで選択結果がずれないよう、
 * この判定は共通化しています。
 */
internal object PlayerBlockTargetResolver {
    private const val DEFAULT_MAX_DISTANCE = 6.0

    fun find(player: Player, maxDistance: Double = DEFAULT_MAX_DISTANCE): Block? {
        val eyeLocation = player.eyeLocation
        val ray = player.world.rayTraceBlocks(
            eyeLocation,
            eyeLocation.direction,
            maxDistance,
            FluidCollisionMode.NEVER,
            true,
        )
        return ray?.hitBlock
    }
}
