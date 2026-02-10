package me.awabi2048.myworldmanager.model

import org.bukkit.Location
import org.bukkit.configuration.serialization.ConfigurationSerializable
import java.util.UUID

data class LikeSignData(
    val uuid: UUID,
    val worldUuid: UUID,
    var title: String,
    var description: String,
    var displayType: LikeSignDisplayType,
    val likedPlayers: MutableSet<UUID> = mutableSetOf(),
    val placedBy: UUID,
    val createdAt: String = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
    var blockX: Int = 0,
    var blockY: Int = 0,
    var blockZ: Int = 0,
    var blockFace: String = "NORTH"
) : ConfigurationSerializable {

    fun likeCount(): Int = likedPlayers.size

    fun hasLiked(playerUuid: UUID): Boolean = likedPlayers.contains(playerUuid)

    fun addLike(playerUuid: UUID): Boolean = likedPlayers.add(playerUuid)

    fun removeLike(playerUuid: UUID): Boolean = likedPlayers.remove(playerUuid)

    override fun serialize(): Map<String, Any?> {
        return mutableMapOf(
            "uuid" to uuid.toString(),
            "world_uuid" to worldUuid.toString(),
            "title" to title,
            "description" to description,
            "display_type" to displayType.name,
            "liked_players" to likedPlayers.map { it.toString() },
            "placed_by" to placedBy.toString(),
            "created_at" to createdAt,
            "block_x" to blockX,
            "block_y" to blockY,
            "block_z" to blockZ,
            "block_face" to blockFace
        )
    }

    companion object {
        @JvmStatic
        fun deserialize(args: Map<String, Any>): LikeSignData {
            return LikeSignData(
                uuid = UUID.fromString(args["uuid"] as String),
                worldUuid = UUID.fromString(args["world_uuid"] as String),
                title = args["title"] as String,
                description = args["description"] as String,
                displayType = LikeSignDisplayType.valueOf(args["display_type"] as String),
                likedPlayers = (args["liked_players"] as? List<*>)?.mapNotNull { 
                    try { UUID.fromString(it as String) } catch (e: Exception) { null } 
                }?.toMutableSet() ?: mutableSetOf(),
                placedBy = UUID.fromString(args["placed_by"] as String),
                createdAt = args["created_at"] as? String ?: java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                blockX = (args["block_x"] as? Number)?.toInt() ?: 0,
                blockY = (args["block_y"] as? Number)?.toInt() ?: 0,
                blockZ = (args["block_z"] as? Number)?.toInt() ?: 0,
                blockFace = args["block_face"] as? String ?: "NORTH"
            )
        }
    }
}
