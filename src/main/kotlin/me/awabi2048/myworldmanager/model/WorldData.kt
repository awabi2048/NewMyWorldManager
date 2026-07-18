package me.awabi2048.myworldmanager.model

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.configuration.serialization.ConfigurationSerializable
import java.util.Locale
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
    val tags: MutableList<String> = mutableListOf(),
    var cumulativePoints: Int = 0,
    var notificationEnabled: Boolean = true,
    val announcementMessages: MutableList<String> = mutableListOf(),
    val createdAt: String = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
    var archivedAt: String? = null,
    var archiveTransitionType: String? = null,
    val customWorldName: String? = null,
    var worldKey: String = "minecraft:${customWorldName ?: "my_world.$uuid"}",
    var gravityValue: Double? = DEFAULT_GRAVITY,
    var fixedWeather: String? = null,
    var fixedBiome: String? = null,
    var fixedTime: Long? = null,
    var fixedScale: Double? = null,
    var allowFlight: Boolean = false,
    var seedSpecified: Boolean = false,
    var seedSpawnValidated: Boolean = true,
    val borderExpansionHistory: MutableList<BorderExpansionRecord> = mutableListOf(),
    val partialBiomes: MutableList<PartialBiomeData> = mutableListOf(),
    val tourSigns: MutableList<TourSignData> = mutableListOf(),
    val tours: MutableList<TourData> = mutableListOf()
) : ConfigurationSerializable {

    fun latestBorderExpansionRecord(): BorderExpansionRecord? {
        return borderExpansionHistory.lastOrNull { it.levelAfter == borderExpansionLevel }
    }

    fun hasModifiedBorderExpansion(): Boolean {
        return borderExpansionHistory.any { it.modified }
    }

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
            "tags" to tags,
            "cumulative_points" to cumulativePoints,
            "notification_enabled" to notificationEnabled,
            "announcement_messages" to announcementMessages,
            "created_at" to createdAt,
            "archived_at" to archivedAt,
            "archive_transition_type" to archiveTransitionType,
            "custom_world_name" to customWorldName,
            "world_key" to worldKey,
            "gravity_value" to gravityValue,
            "fixed_weather" to fixedWeather,
            "fixed_biome" to fixedBiome,
            "fixed_time" to fixedTime,
            "fixed_scale" to fixedScale,
            "allow_flight" to allowFlight,
            "seed_specified" to seedSpecified,
            "seed_spawn_validated" to seedSpawnValidated,
            "border_expansion_history" to borderExpansionHistory.map { it.serialize() },
            "partial_biomes" to partialBiomes.map { mapOf("x" to it.x, "z" to it.z, "radius" to it.radius, "biome" to it.biome) },
            "tour_signs" to tourSigns.map { it.serialize() },
            "tours" to tours.map { it.serialize() }
        )
    }

    companion object {
        const val EXPANSION_LEVEL_SPECIAL = -1
        const val DEFAULT_GRAVITY = 0.08

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

            val uuid = UUID.fromString(args["uuid"] as String)
            val customWorldName = args["custom_world_name"] as? String
            return WorldData(
                uuid = uuid,
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
                tags = (args["tags"] as? List<*>)
                    ?.mapNotNull { (it as? String)?.let { raw -> normalizeTagId(raw) } }
                    ?.distinct()
                    ?.toMutableList()
                    ?: mutableListOf(),
                cumulativePoints = args["cumulative_points"] as Int,
                notificationEnabled = args["notification_enabled"] as? Boolean ?: true,
                announcementMessages = (args["announcement_messages"] as? List<*>)?.filterIsInstance<String>()?.toMutableList() ?: mutableListOf(),
                createdAt = createdAtVal,
                archivedAt = args["archived_at"] as? String,
                archiveTransitionType = args["archive_transition_type"] as? String,
                customWorldName = customWorldName,
                worldKey = args["world_key"] as? String
                    ?: throw IllegalArgumentException("world_keyがありません: $uuid"),
                gravityValue = (args["gravity_value"] as? Number)?.toDouble() ?: DEFAULT_GRAVITY,
                fixedWeather = args["fixed_weather"] as? String,
                fixedBiome = args["fixed_biome"] as? String,
                fixedTime = (args["fixed_time"] as? Number)?.toLong(),
                fixedScale = (args["fixed_scale"] as? Number)?.toDouble(),
                allowFlight = args["allow_flight"] as? Boolean ?: false,
                seedSpecified = args["seed_specified"] as? Boolean ?: false,
                seedSpawnValidated = args["seed_spawn_validated"] as? Boolean ?: true,
                borderExpansionHistory = (args["border_expansion_history"] as? List<*>)
                    ?.mapNotNull { BorderExpansionRecord.deserialize(it as? Map<*, *>) }
                    ?.toMutableList()
                    ?: mutableListOf(),
                partialBiomes = (args["partial_biomes"] as? List<*>)?.mapNotNull {
                    val map = it as? Map<*, *> ?: return@mapNotNull null
                    val x = (map["x"] as? Number)?.toInt() ?: return@mapNotNull null
                    val z = (map["z"] as? Number)?.toInt() ?: return@mapNotNull null
                    val radius = (map["radius"] as? Number)?.toInt() ?: return@mapNotNull null
                    val biome = map["biome"] as? String ?: return@mapNotNull null
                    PartialBiomeData(x, z, radius, biome)
                }?.toMutableList() ?: mutableListOf(),
                tourSigns = (args["tour_signs"] as? List<*>)?.mapNotNull {
                    try {
                        @Suppress("UNCHECKED_CAST")
                        TourSignData.deserialize(it as Map<String, Any>)
                    } catch (e: Exception) {
                        null
                    }
                }?.toMutableList() ?: mutableListOf(),
                tours = (args["tours"] as? List<*>)?.mapNotNull {
                    try {
                        @Suppress("UNCHECKED_CAST")
                        TourData.deserialize(it as Map<String, Any>)
                    } catch (e: Exception) {
                        null
                    }
                }?.toMutableList() ?: mutableListOf()
            )
        }

        private fun serializeLocation(loc: Location): Map<String, Any?> {
            return mapOf(
                "world_key" to loc.world?.key?.toString(),
                "x" to loc.blockX,
                "y" to loc.blockY,
                "z" to loc.blockZ,
                "yaw" to loc.yaw,
                "pitch" to loc.pitch
            )
        }

        private fun deserializeLocation(obj: Any?): Location? {
            if (obj == null) return null
            if (obj is Map<*, *>) {
                try {
                    val worldKey = obj["world_key"] as? String
                    val x = (obj["x"] as Number).toInt()
                    val y = (obj["y"] as Number).toInt()
                    val z = (obj["z"] as Number).toInt()
                    val yaw = (obj["yaw"] as? Number)?.toFloat() ?: 0.0f
                    val pitch = (obj["pitch"] as? Number)?.toFloat() ?: 0.0f
                    
                    val world = worldKey
                        ?.let(org.bukkit.NamespacedKey::fromString)
                        ?.let(org.bukkit.Bukkit::getWorld)
                    return Location(world, x + 0.5, y.toDouble(), z + 0.5, yaw, pitch)
                } catch (e: Exception) {
                    return null
                }
            }
            return null
        }

        private fun normalizeTagId(raw: String): String? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null

            val legacy = legacyTagMap[trimmed.uppercase(Locale.ROOT)]
            if (legacy != null) return legacy

            return trimmed.lowercase(Locale.ROOT)
        }

        private val legacyTagMap = mapOf(
            "SHOP" to "shop",
            "MINIGAME" to "minigame",
            "BUILDING" to "building",
            "FACILITY" to "facility",
            "STREAMING" to "streaming"
        )
    }
}

data class BorderExpansionRecord(
    val levelBefore: Int,
    val levelAfter: Int,
    val direction: String?,
    val oldCenterX: Double,
    val oldCenterZ: Double,
    val oldSize: Double,
    val newCenterX: Double,
    val newCenterZ: Double,
    val newSize: Double,
    var modified: Boolean = false
) {
    fun serialize(): Map<String, Any?> = mapOf(
        "level_before" to levelBefore,
        "level_after" to levelAfter,
        "direction" to direction,
        "old_center_x" to oldCenterX,
        "old_center_z" to oldCenterZ,
        "old_size" to oldSize,
        "new_center_x" to newCenterX,
        "new_center_z" to newCenterZ,
        "new_size" to newSize,
        "modified" to modified
    )

    fun containsAddedArea(x: Double, z: Double): Boolean {
        return isInside(x, z, newCenterX, newCenterZ, newSize) &&
            !isInside(x, z, oldCenterX, oldCenterZ, oldSize)
    }

    private fun isInside(x: Double, z: Double, centerX: Double, centerZ: Double, size: Double): Boolean {
        val radius = size / 2.0
        return x >= centerX - radius &&
            x <= centerX + radius &&
            z >= centerZ - radius &&
            z <= centerZ + radius
    }

    companion object {
        fun deserialize(map: Map<*, *>?): BorderExpansionRecord? {
            if (map == null) return null
            val levelBefore = (map["level_before"] as? Number)?.toInt() ?: return null
            val levelAfter = (map["level_after"] as? Number)?.toInt() ?: return null
            val oldCenterX = (map["old_center_x"] as? Number)?.toDouble() ?: return null
            val oldCenterZ = (map["old_center_z"] as? Number)?.toDouble() ?: return null
            val oldSize = (map["old_size"] as? Number)?.toDouble() ?: return null
            val newCenterX = (map["new_center_x"] as? Number)?.toDouble() ?: return null
            val newCenterZ = (map["new_center_z"] as? Number)?.toDouble() ?: return null
            val newSize = (map["new_size"] as? Number)?.toDouble() ?: return null
            return BorderExpansionRecord(
                levelBefore = levelBefore,
                levelAfter = levelAfter,
                direction = map["direction"] as? String,
                oldCenterX = oldCenterX,
                oldCenterZ = oldCenterZ,
                oldSize = oldSize,
                newCenterX = newCenterX,
                newCenterZ = newCenterZ,
                newSize = newSize,
                modified = map["modified"] as? Boolean ?: false
            )
        }
    }
}

data class PartialBiomeData(
    val x: Int,
    val z: Int,
    val radius: Int,
    val biome: String
)
