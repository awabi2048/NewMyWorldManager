package me.awabi2048.myworldmanager.model

import org.bukkit.configuration.serialization.ConfigurationSerializable
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

data class TourSignData(
    val uuid: UUID,
    val worldUuid: UUID,
    var title: String,
    var description: String,
    val placedBy: UUID,
    val createdAt: String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
    var blockX: Int = 0,
    var blockY: Int = 0,
    var blockZ: Int = 0,
    var blockFace: String = "NORTH"
) : ConfigurationSerializable {
    override fun serialize(): Map<String, Any?> = mapOf(
        "uuid" to uuid.toString(),
        "world_uuid" to worldUuid.toString(),
        "title" to title,
        "description" to description,
        "placed_by" to placedBy.toString(),
        "created_at" to createdAt,
        "block_x" to blockX,
        "block_y" to blockY,
        "block_z" to blockZ,
        "block_face" to blockFace
    )

    companion object {
        @JvmStatic
        fun deserialize(args: Map<String, Any>): TourSignData {
            return TourSignData(
                uuid = UUID.fromString(args["uuid"] as String),
                worldUuid = UUID.fromString(args["world_uuid"] as String),
                title = args["title"] as? String ?: "",
                description = args["description"] as? String ?: "",
                placedBy = UUID.fromString(args["placed_by"] as String),
                createdAt = args["created_at"] as? String
                    ?: LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                blockX = (args["block_x"] as? Number)?.toInt() ?: 0,
                blockY = (args["block_y"] as? Number)?.toInt() ?: 0,
                blockZ = (args["block_z"] as? Number)?.toInt() ?: 0,
                blockFace = args["block_face"] as? String ?: "NORTH"
            )
        }
    }
}
