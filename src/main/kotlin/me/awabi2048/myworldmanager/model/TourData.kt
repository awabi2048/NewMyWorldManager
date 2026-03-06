package me.awabi2048.myworldmanager.model

import org.bukkit.configuration.serialization.ConfigurationSerializable
import org.bukkit.Material
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

data class TourData(
    val uuid: UUID,
    var name: String,
    var description: String,
    var icon: Material = Material.OAK_BOAT,
    val signUuids: MutableList<UUID> = mutableListOf(),
    var completedCount: Int = 0,
    val startedPlayerUuids: MutableSet<UUID> = mutableSetOf(),
    val createdAt: String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
) : ConfigurationSerializable {
    override fun serialize(): Map<String, Any?> = mapOf(
        "uuid" to uuid.toString(),
        "name" to name,
        "description" to description,
        "icon" to icon.name,
        "sign_uuids" to signUuids.map { it.toString() },
        "completed_count" to completedCount,
        "started_player_uuids" to startedPlayerUuids.map { it.toString() },
        "created_at" to createdAt
    )

    companion object {
        @JvmStatic
        fun deserialize(args: Map<String, Any>): TourData {
            return TourData(
                uuid = UUID.fromString(args["uuid"] as String),
                name = args["name"] as? String ?: "",
                description = args["description"] as? String ?: "",
                icon = (args["icon"] as? String)?.let { runCatching { Material.valueOf(it) }.getOrNull() } ?: Material.OAK_BOAT,
                signUuids = (args["sign_uuids"] as? List<*>)
                    ?.mapNotNull { (it as? String)?.let(UUID::fromString) }
                    ?.toMutableList()
                    ?: mutableListOf(),
                completedCount = (args["completed_count"] as? Number)?.toInt() ?: 0,
                startedPlayerUuids = (args["started_player_uuids"] as? List<*>)
                    ?.mapNotNull { (it as? String)?.let(UUID::fromString) }
                    ?.toMutableSet()
                    ?: mutableSetOf(),
                createdAt = args["created_at"] as? String
                    ?: LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            )
        }
    }
}
