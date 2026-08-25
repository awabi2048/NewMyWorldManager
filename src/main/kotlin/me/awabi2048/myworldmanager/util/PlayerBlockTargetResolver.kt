package me.awabi2048.myworldmanager.util

import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
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
        return ray(player, maxDistance)?.hitBlock
    }

    /**
     * 視線の当たった面の直上に立つ位置を返します。
     *
     * スラブなど半端な高さのブロックの上にも正確に立てるよう、当たった面の高さ
     * （hitPosition.y）を基準にします。上面に当たった場合は面の高さをそのまま使い、
     * 側面・下面に当たった場合はブロックの上面へ丸めます。x/z はブロック中央です。
     */
    fun findStandingLocation(player: Player, maxDistance: Double = DEFAULT_MAX_DISTANCE): Location? {
        val ray = ray(player, maxDistance) ?: return null
        val hitBlock = ray.hitBlock ?: return null
        val hitPosition = ray.hitPosition ?: return null
        val standY = if (ray.hitBlockFace == BlockFace.UP) {
            // スラブ・階段などの上面に当たった場合は、その面の高さに立ちます。
            hitPosition.y
        } else {
            hitBlock.y + 1.0
        }
        return Location(hitBlock.world, hitBlock.x + 0.5, standY, hitBlock.z + 0.5)
    }

    private fun ray(player: Player, maxDistance: Double) = player.world.rayTraceBlocks(
        player.eyeLocation,
        player.eyeLocation.direction,
        maxDistance,
        FluidCollisionMode.NEVER,
        true,
    )
}
