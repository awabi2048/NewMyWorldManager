package me.awabi2048.myworldmanager.model

import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import java.util.UUID

enum class PortalType(val key: String) {
    PORTAL("portal"),
    GATE("gate");

    companion object {
        fun fromKey(key: String?): PortalType {
            return values().firstOrNull { it.key.equals(key, ignoreCase = true) } ?: PORTAL
        }
    }
}

/**
 * ポータルの設置データ
 */
data class PortalData(
    val id: UUID = UUID.randomUUID(),
    val worldName: String,
    val x: Int,
    val y: Int,
    val z: Int,
    var worldUuid: UUID?, // 行き先のマイワールドUUID (外部ワールドの場合はnull)
    var targetWorldName: String? = null, // 行き先の外部ワールド名
    var showText: Boolean = true,
    var particleColor: Color = Color.AQUA,
    val ownerUuid: UUID, // 設置者
    val createdAt: String = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), // 設置日時
    var textDisplayUuid: UUID? = null, // テキストディスプレイエンティティのUUID
    var type: PortalType = PortalType.PORTAL,
    var minX: Int? = null,
    var minY: Int? = null,
    var minZ: Int? = null,
    var maxX: Int? = null,
    var maxY: Int? = null,
    var maxZ: Int? = null
) {
    fun isGate(): Boolean = type == PortalType.GATE

    fun getMinX(): Int = minOf(minX ?: x, maxX ?: x)
    fun getMinY(): Int = minOf(minY ?: y, maxY ?: y)
    fun getMinZ(): Int = minOf(minZ ?: z, maxZ ?: z)
    fun getMaxX(): Int = maxOf(minX ?: x, maxX ?: x)
    fun getMaxY(): Int = maxOf(minY ?: y, maxY ?: y)
    fun getMaxZ(): Int = maxOf(minZ ?: z, maxZ ?: z)

    fun containsBlock(blockX: Int, blockY: Int, blockZ: Int): Boolean {
        return blockX in getMinX()..getMaxX() &&
                blockY in getMinY()..getMaxY() &&
                blockZ in getMinZ()..getMaxZ()
    }

    fun containsLocation(location: Location): Boolean {
        if (location.world?.name != worldName) return false
        return containsBlock(location.blockX, location.blockY, location.blockZ)
    }

    /**
     * ブロックの中心座標(X+0.5, Z+0.5)をLocationとして取得する
     */
    fun getCenterLocation(): Location {
        val world = Bukkit.getWorld(worldName)
        return if (isGate()) {
            Location(
                world,
                (getMinX() + getMaxX() + 1) / 2.0,
                (getMinY() + getMaxY()) / 2.0,
                (getMinZ() + getMaxZ() + 1) / 2.0
            )
        } else {
            Location(world, x + 0.5, y.toDouble(), z + 0.5)
        }
    }
}
