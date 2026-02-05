package me.awabi2048.myworldmanager.model

import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import java.util.UUID

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
    var textDisplayUuid: UUID? = null // テキストディスプレイエンティティのUUID
) {
    /**
     * ブロックの中心座標(X+0.5, Z+0.5)をLocationとして取得する
     */
    fun getCenterLocation(): Location {
        val world = Bukkit.getWorld(worldName)
        return Location(world, x + 0.5, y.toDouble(), z + 0.5)
    }
}
