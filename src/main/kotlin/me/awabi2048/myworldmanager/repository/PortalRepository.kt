package me.awabi2048.myworldmanager.repository

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.PortalData
import me.awabi2048.myworldmanager.model.PortalType
import org.bukkit.Color
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class PortalRepository(private val plugin: MyWorldManager) {
    private val file = File(plugin.dataFolder, "portals.yml")
    private val portals = ConcurrentHashMap<UUID, PortalData>()

    init {
        loadAll()
    }

    fun loadAll() {
        if (!file.exists()) return
        portals.clear()

        val config = YamlConfiguration.loadConfiguration(file)
        val sections = config.getConfigurationSection("portals") ?: return
        
        for (key in sections.getKeys(false)) {
            val section = sections.getConfigurationSection(key) ?: continue
            val id = try {
                UUID.fromString(key)
            } catch (_: Exception) {
                continue
            }
            
            val worldUuidStr = section.getString("world_uuid")
            val worldUuid = if (worldUuidStr != null && worldUuidStr != "null") UUID.fromString(worldUuidStr) else null
            val targetWorldName = section.getString("target_world_name")
            
            val createdAtRaw = section.get("created_at")
            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            val createdAt = if (createdAtRaw is String) {
                if (createdAtRaw.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                    "$createdAtRaw 00:00:00"
                } else {
                    createdAtRaw
                }
            } else if (createdAtRaw is Number) {
                val instant = java.time.Instant.ofEpochMilli(createdAtRaw.toLong())
                val zone = java.time.ZoneId.systemDefault()
                java.time.LocalDateTime.ofInstant(instant, zone).format(formatter)
            } else {
                java.time.LocalDateTime.now().format(formatter)
            }
            
            val showText = section.getBoolean("show_text", true)
            val colorInt = section.getInt("color", Color.AQUA.asRGB())
            val ownerUuid = UUID.fromString(section.getString("owner_uuid") ?: continue)
            val type = PortalType.fromKey(section.getString("type", "portal"))

            var worldName: String? = section.getString("location.world")
            var lx = section.getInt("location.x")
            var ly = section.getInt("location.y")
            var lz = section.getInt("location.z")

            // location.worldで取得できなかった場合、旧形式やオブジェクト形式からの取得を試みる
            if (worldName == null) {
                try {
                    val locObj = section.get("location")
                    if (locObj is org.bukkit.Location) {
                        worldName = locObj.world?.name
                        lx = locObj.blockX
                        ly = locObj.blockY
                        lz = locObj.blockZ
                    } else if (locObj is Map<*, *>) {
                        worldName = locObj["world"] as? String
                        lx = (locObj["x"] as? Number)?.toInt() ?: 0
                        ly = (locObj["y"] as? Number)?.toInt() ?: 0
                        lz = (locObj["z"] as? Number)?.toInt() ?: 0
                    } else if (locObj is org.bukkit.configuration.ConfigurationSection) {
                        worldName = locObj.getString("world")
                        lx = locObj.getInt("x")
                        ly = locObj.getInt("y")
                        lz = locObj.getInt("z")
                    }
                } catch (e: Exception) {
                    plugin.logger.warning("ポータル $key のロケーション情報の読み込みに失敗しました (無視されます): ${e.message}")
                }
            }

            if (worldName == null) continue

            val areaSection = section.getConfigurationSection("area")
            val areaMinX = if (areaSection?.contains("min_x") == true) areaSection.getInt("min_x") else null
            val areaMinY = if (areaSection?.contains("min_y") == true) areaSection.getInt("min_y") else null
            val areaMinZ = if (areaSection?.contains("min_z") == true) areaSection.getInt("min_z") else null
            val areaMaxX = if (areaSection?.contains("max_x") == true) areaSection.getInt("max_x") else null
            val areaMaxY = if (areaSection?.contains("max_y") == true) areaSection.getInt("max_y") else null
            val areaMaxZ = if (areaSection?.contains("max_z") == true) areaSection.getInt("max_z") else null

            // 設置場所がマイワールドの場合、そのワールドが存在するかチェック
            if (worldName.startsWith("my_world.")) {
                val worldUuidStr = worldName.removePrefix("my_world.")
                try {
                    val worldUuid = UUID.fromString(worldUuidStr)
                    if (plugin.worldConfigRepository.findByUuid(worldUuid) == null) {
                        plugin.logger.info("[Portal] 存在しないワールド($worldName)に設置されたポータルを削除しました。")
                        continue 
                    }
                } catch (e: Exception) {
                    // UUID形式でない場合は無視（通常ありえない）
                }
            }

            // 行き先のマイワールドが存在するかチェック
            var finalWorldUuid = worldUuid
            if (worldUuid != null && plugin.worldConfigRepository.findByUuid(worldUuid) == null) {
                plugin.logger.info("[Portal] 存在しないワールド(UUID: $worldUuid)への行き先設定を解除しました。")
                finalWorldUuid = null
            }

             val textDisplayUuidStr = section.getString("text_display_uuid")
             val textDisplayUuid = if (textDisplayUuidStr != null && textDisplayUuidStr != "null") UUID.fromString(textDisplayUuidStr) else null
             
             portals[id] = PortalData(
                id = id,
                worldName = worldName,
                x = lx,
                y = ly,
                z = lz,
                worldUuid = finalWorldUuid,
                targetWorldName = targetWorldName,
                showText = showText,
                particleColor = Color.fromRGB(colorInt),
                ownerUuid = ownerUuid,
                createdAt = createdAt,
                textDisplayUuid = textDisplayUuid,
                type = type,
                minX = if (type == PortalType.GATE) areaMinX ?: lx else null,
                minY = if (type == PortalType.GATE) areaMinY ?: ly else null,
                minZ = if (type == PortalType.GATE) areaMinZ ?: lz else null,
                maxX = if (type == PortalType.GATE) areaMaxX ?: lx else null,
                maxY = if (type == PortalType.GATE) areaMaxY ?: ly else null,
                maxZ = if (type == PortalType.GATE) areaMaxZ ?: lz else null
            )
        }
        
        // 読み込み後に保存してクリーンアップ結果を反映
        saveAll()
    }

     fun saveAll() {
         try {
             val config = YamlConfiguration()
             val section = config.createSection("portals")
             for ((id, data) in portals) {
                 val s = section.createSection(id.toString())
                 s.set("type", data.type.key)
                 s.set("location", mapOf(
                     "world" to data.worldName,
                     "x" to data.x,
                     "y" to data.y,
                     "z" to data.z
                 ))
                 if (data.isGate()) {
                     s.set(
                         "area",
                         mapOf(
                             "min_x" to data.getMinX(),
                             "min_y" to data.getMinY(),
                             "min_z" to data.getMinZ(),
                             "max_x" to data.getMaxX(),
                             "max_y" to data.getMaxY(),
                             "max_z" to data.getMaxZ()
                         )
                     )
                 }
                 s.set("world_uuid", data.worldUuid?.toString())
                 s.set("target_world_name", data.targetWorldName)
                 s.set("show_text", data.showText)
                 s.set("color", data.particleColor.asRGB())
                 s.set("owner_uuid", data.ownerUuid.toString())
                 s.set("created_at", data.createdAt)
                 s.set("text_display_uuid", data.textDisplayUuid?.toString())
             }
             config.save(file)
         } catch (e: Exception) {
             plugin.logger.warning("[PortalRepository] 保存中にエラーが発生しました: ${e.message}")
             e.printStackTrace()
         }
     }

    fun addPortal(portal: PortalData) {
        portals[portal.id] = portal
        saveAll()
    }

    fun removePortal(id: UUID) {
        portals.remove(id)
        saveAll()
    }

    fun findAll(): Collection<PortalData> = portals.values

    fun findByLocation(location: org.bukkit.Location): PortalData? {
        return portals.values.find {
            it.worldName == location.world?.name &&
            it.x == location.blockX &&
            it.y == location.blockY &&
            it.z == location.blockZ
        }
    }

    fun findByContainingLocation(location: org.bukkit.Location): PortalData? {
        val worldName = location.world?.name ?: return null
        val exact = findByLocation(location)
        if (exact != null) return exact

        return portals.values.find {
            it.worldName == worldName && it.containsBlock(location.blockX, location.blockY, location.blockZ)
        }
    }

    fun findById(id: UUID): PortalData? = portals[id]
}
