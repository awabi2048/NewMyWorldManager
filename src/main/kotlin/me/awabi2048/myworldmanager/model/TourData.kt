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
    var createdBy: UUID? = null,
    var startSignUuid: UUID? = null,
    val waypoints: MutableList<TourWaypointData> = mutableListOf(),
    var completedCount: Int = 0,
    val startedPlayerUuids: MutableSet<UUID> = mutableSetOf(),
    val activePlayerProgress: MutableMap<UUID, Int> = mutableMapOf(),
    val createdAt: String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
) : ConfigurationSerializable {
    override fun serialize(): Map<String, Any?> = mapOf(
        "uuid" to uuid.toString(),
        "name" to name,
        "description" to description,
        "icon" to icon.name,
        "created_by" to createdBy?.toString(),
        "start_sign_uuid" to startSignUuid?.toString(),
        "waypoints" to waypoints.map {
            mapOf(
                "uuid" to it.uuid.toString(),
                "name" to it.name,
                "block_x" to it.blockX,
                "block_y" to it.blockY,
                "block_z" to it.blockZ,
                "created_at" to it.createdAt
            )
        },
        "completed_count" to completedCount,
        "started_player_uuids" to startedPlayerUuids.map { it.toString() },
        "active_player_progress" to activePlayerProgress.map { (k, v) -> mapOf("uuid" to k.toString(), "index" to v) },
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
                createdBy = (args["created_by"] as? String)?.let {
                    runCatching { UUID.fromString(it) }.getOrNull()
                },
                startSignUuid = (args["start_sign_uuid"] as? String)?.let {
                    runCatching { UUID.fromString(it) }.getOrNull()
                },
                waypoints = (args["waypoints"] as? List<*>)
                    ?.mapNotNull {
                        val map = it as? Map<*, *> ?: return@mapNotNull null
                        val uuid = (map["uuid"] as? String)?.let { raw -> runCatching { UUID.fromString(raw) }.getOrNull() }
                            ?: return@mapNotNull null
                        val name = map["name"] as? String ?: return@mapNotNull null
                        val x = (map["block_x"] as? Number)?.toInt() ?: return@mapNotNull null
                        val y = (map["block_y"] as? Number)?.toInt() ?: return@mapNotNull null
                        val z = (map["block_z"] as? Number)?.toInt() ?: return@mapNotNull null
                        val createdAt = map["created_at"] as? String
                            ?: LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        TourWaypointData(
                            uuid = uuid,
                            name = name,
                            blockX = x,
                            blockY = y,
                            blockZ = z,
                            createdAt = createdAt
                        )
                    }
                    ?.toMutableList()
                    ?: mutableListOf(),
                completedCount = (args["completed_count"] as? Number)?.toInt() ?: 0,
                startedPlayerUuids = (args["started_player_uuids"] as? List<*>)
                    ?.mapNotNull { (it as? String)?.let(UUID::fromString) }
                    ?.toMutableSet()
                    ?: mutableSetOf(),
                activePlayerProgress = (args["active_player_progress"] as? List<*>)
                    ?.mapNotNull {
                        val map = it as? Map<*, *> ?: return@mapNotNull null
                        val uuid = (map["uuid"] as? String)?.let { raw -> runCatching { UUID.fromString(raw) }.getOrNull() } ?: return@mapNotNull null
                        val index = (map["index"] as? Number)?.toInt() ?: return@mapNotNull null
                        uuid to index
                    }
                    ?.toMap()
                    ?.toMutableMap()
                    ?: mutableMapOf(),
                createdAt = args["created_at"] as? String
                    ?: LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            )
        }
    }
}
