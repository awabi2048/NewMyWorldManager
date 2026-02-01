package me.awabi2048.myworldmanager.model

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.configuration.serialization.ConfigurationSerializable
import java.util.UUID

/**
 * ワールドのメタデータを保持するデータクラス
 */
data class WorldData(
    val uuid: UUID,
    var name: String,
    var description: String,
    var icon: Material,
    val sourceWorld: String,
    var expireDate: String,
    var owner: UUID,
    val members: MutableList<UUID> = mutableListOf(),
    val moderators: MutableList<UUID> = mutableListOf(),
    var publishLevel: PublishLevel = PublishLevel.PRIVATE,
    var spawnPosGuest: Location? = null,
    var spawnPosMember: Location? = null,
    var borderCenterPos: Location? = null,
    var borderExpansionLevel: Int = 0,
    var isArchived: Boolean = false,
    var favorite: Int = 0,
    var recentVisitors: MutableList<Int> = mutableListOf(0, 0, 0, 0, 0, 0, 0),
    var publicAt: String? = null,
    val tags: MutableList<WorldTag> = mutableListOf(),
    var cumulativePoints: Int = 0,
    var notificationEnabled: Boolean = true,
    val announcementMessages: MutableList<String> = mutableListOf(),
    val createdAt: String = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
    val customWorldName: String? = null,
    var gravityMultiplier: Double = 1.0,
    var fixedWeather: String? = null,
    var fixedBiome: String? = null,
    val partialBiomes: MutableList<PartialBiomeData> = mutableListOf()
) : ConfigurationSerializable {

    override fun serialize(): Map<String, Any?> {
        return mutableMapOf(
            "uuid" to uuid.toString(),
            "name" to name,
            "description" to description,
            "icon" to icon.name,
            "source_world" to sourceWorld,
            "expire_date" to expireDate,
            "owner" to owner.toString(),
            "members" to members.map { it.toString() },
            "moderators" to moderators.map { it.toString() },
            "publish_level" to publishLevel.name,
            "spawn_pos_guest" to spawnPosGuest?.let { serializeLocation(it) },
            "spawn_pos_member" to spawnPosMember?.let { serializeLocation(it) },
            "border_center_pos" to borderCenterPos?.let { serializeLocation(it) },
            "border_expansion_level" to borderExpansionLevel,
            "is_archived" to isArchived,
            "favorite" to favorite,
            "recent_visitors" to recentVisitors,
            "public_at" to publicAt,
            "tags" to tags.map { it.name },
            "cumulative_points" to cumulativePoints,
            "notification_enabled" to notificationEnabled,
            "announcement_messages" to announcementMessages,
            "created_at" to createdAt,
            "custom_world_name" to customWorldName,
            "gravity_multiplier" to gravityMultiplier,
            "fixed_weather" to fixedWeather,
            "fixed_biome" to fixedBiome,
            "partial_biomes" to partialBiomes.map { mapOf("x" to it.x, "z" to it.z, "radius" to it.radius, "biome" to it.biome) }
        )
    }

    companion object {
        const val EXPANSION_LEVEL_SPECIAL = -1

        @JvmStatic
        fun deserialize(args: Map<String, Any>): WorldData {
            val createdAtRaw = args["created_at"]
            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            val createdAtVal = if (createdAtRaw is String) {
                if (createdAtRaw.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                    "$createdAtRaw 00:00:00"
                } else if (!createdAtRaw.contains(" ")) {
                    // 他の不完全な形式の場合のフォールバック
                    java.time.LocalDateTime.now().format(formatter)
                } else {
                    createdAtRaw
                }
            } else if (createdAtRaw is Number) {
                // 旧データ(Long)のマイグレーション
                try {
                    val instant = java.time.Instant.ofEpochMilli(createdAtRaw.toLong())
                    val zone = java.time.ZoneId.systemDefault()
                    java.time.LocalDateTime.ofInstant(instant, zone).format(formatter)
                } catch (e: Exception) {
                    java.time.LocalDateTime.now().format(formatter)
                }
            } else {
                java.time.LocalDateTime.now().format(formatter)
            }

            return WorldData(
                uuid = UUID.fromString(args["uuid"] as String),
                name = args["name"] as String,
                description = args["description"] as String,
                icon = Material.valueOf(args["icon"] as String),
                sourceWorld = args["source_world"] as String,
                expireDate = args["expire_date"] as String,
                owner = UUID.fromString(args["owner"] as String),
                members = (args["members"] as? List<*>)?.mapNotNull { try { UUID.fromString(it as String) } catch (e: Exception) { null } }?.toMutableList() ?: mutableListOf(),
                moderators = (args["moderators"] as? List<*>)?.mapNotNull { try { UUID.fromString(it as String) } catch (e: Exception) { null } }?.toMutableList() ?: mutableListOf(),
                publishLevel = PublishLevel.valueOf(args["publish_level"] as String),
                spawnPosGuest = deserializeLocation(args["spawn_pos_guest"]),
                spawnPosMember = deserializeLocation(args["spawn_pos_member"]),
                borderCenterPos = deserializeLocation(args["border_center_pos"]),
                borderExpansionLevel = args["border_expansion_level"] as Int,
                isArchived = args["is_archived"] as Boolean,
                favorite = args["favorite"] as Int,
                recentVisitors = (args["recent_visitors"] as? List<*>)?.filterIsInstance<Int>()?.toMutableList() ?: mutableListOf(0, 0, 0, 0, 0, 0, 0),
                publicAt = args["public_at"] as? String,
                tags = (args["tags"] as? List<*>)?.mapNotNull { try { WorldTag.valueOf(it as String) } catch (e: Exception) { null } }?.toMutableList() ?: mutableListOf(),
                cumulativePoints = args["cumulative_points"] as Int,
                notificationEnabled = args["notification_enabled"] as? Boolean ?: true,
                announcementMessages = (args["announcement_messages"] as? List<*>)?.filterIsInstance<String>()?.toMutableList() ?: mutableListOf(),
                createdAt = createdAtVal,
                customWorldName = args["custom_world_name"] as? String,
                gravityMultiplier = (args["gravity_multiplier"] as? Number)?.toDouble() ?: 1.0,
                fixedWeather = args["fixed_weather"] as? String,
                fixedBiome = args["fixed_biome"] as? String,
                partialBiomes = (args["partial_biomes"] as? List<*>)?.mapNotNull {
                    val map = it as? Map<*, *> ?: return@mapNotNull null
                    val x = (map["x"] as? Number)?.toInt() ?: return@mapNotNull null
                    val z = (map["z"] as? Number)?.toInt() ?: return@mapNotNull null
                    val radius = (map["radius"] as? Number)?.toInt() ?: return@mapNotNull null
                    val biome = map["biome"] as? String ?: return@mapNotNull null
                    PartialBiomeData(x, z, radius, biome)
                }?.toMutableList() ?: mutableListOf()
            )
        }

        private fun serializeLocation(loc: Location): Map<String, Any?> {
            return mapOf(
                "world" to loc.world?.name,
                "x" to loc.blockX,
                "y" to loc.blockY,
                "z" to loc.blockZ,
                "yaw" to loc.yaw,
                "pitch" to loc.pitch
            )
        }

        private fun deserializeLocation(obj: Any?): Location? {
            if (obj == null) return null
            if (obj is Location) return obj
            
            if (obj is Map<*, *>) {
                try {
                    val worldName = obj["world"] as? String
                    val x = (obj["x"] as Number).toInt()
                    val y = (obj["y"] as Number).toInt()
                    val z = (obj["z"] as Number).toInt()
                    val yaw = (obj["yaw"] as? Number)?.toFloat() ?: 0.0f
                    val pitch = (obj["pitch"] as? Number)?.toFloat() ?: 0.0f
                    
                    val world = worldName?.let { org.bukkit.Bukkit.getWorld(it) }
                    return Location(world, x + 0.5, y.toDouble(), z + 0.5, yaw, pitch)
                } catch (e: Exception) {
                    return null
                }
            }
            return null
        }
    }
}

data class PartialBiomeData(
    val x: Int,
    val z: Int,
    val radius: Int,
    val biome: String
)
