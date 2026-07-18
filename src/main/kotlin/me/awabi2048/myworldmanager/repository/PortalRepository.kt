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
            val targetWorldKey = section.getString("target_world_key")
            
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

            val worldKey = section.getString("location.world_key") ?: continue
            val lx = section.getInt("location.x")
            val ly = section.getInt("location.y")
            val lz = section.getInt("location.z")

            val areaSection = section.getConfigurationSection("area")
            val areaMinX = if (areaSection?.contains("min_x") == true) areaSection.getInt("min_x") else null
            val areaMinY = if (areaSection?.contains("min_y") == true) areaSection.getInt("min_y") else null
            val areaMinZ = if (areaSection?.contains("min_z") == true) areaSection.getInt("min_z") else null
            val areaMaxX = if (areaSection?.contains("max_x") == true) areaSection.getInt("max_x") else null
            val areaMaxY = if (areaSection?.contains("max_y") == true) areaSection.getInt("max_y") else null
            val areaMaxZ = if (areaSection?.contains("max_z") == true) areaSection.getInt("max_z") else null

            // 読み込み時は参照先のWorldDataが未初期化・一時欠損でもレコードを破棄しない。
            // 不正なキーだけを読み飛ばし、ファイル全体の自動再保存は行わない。
            val parsedWorldKey = org.bukkit.NamespacedKey.fromString(worldKey)
            if (parsedWorldKey == null) {
                plugin.logger.warning("[Portal] 不正なworld_keyを持つポータルを保持したまま読み飛ばしました: id=$id, worldKey=$worldKey")
                continue
            }
            val runtimeName = parsedWorldKey.key
            if (runtimeName.startsWith("my_world.")) {
                val worldUuidStr = runtimeName.removePrefix("my_world.")
                try {
                    val worldUuid = UUID.fromString(worldUuidStr)
                    if (plugin.worldConfigRepository.findByUuid(worldUuid) == null) {
                        plugin.logger.warning("[Portal] WorldDataを確認できない設置元参照を保持します: portal=$id, worldKey=$worldKey")
                    }
                } catch (e: Exception) {
                    plugin.logger.warning("[Portal] MyWorld形式のworld_keyを解釈できません。レコードは保持します: portal=$id, worldKey=$worldKey")
                }
            }

            // 行き先参照も読み込み時には解除しない。削除は明示的なワールド削除処理だけが担当する。
            if (worldUuid != null && plugin.worldConfigRepository.findByUuid(worldUuid) == null) {
                plugin.logger.warning("[Portal] WorldDataを確認できない行き先参照を保持します: portal=$id, worldUuid=$worldUuid")
            }

             val textDisplayUuidStr = section.getString("text_display_uuid")
             val textDisplayUuid = if (textDisplayUuidStr != null && textDisplayUuidStr != "null") UUID.fromString(textDisplayUuidStr) else null
             
             portals[id] = PortalData(
                id = id,
                worldKey = worldKey,
                x = lx,
                y = ly,
                z = lz,
                worldUuid = worldUuid,
                targetWorldKey = targetWorldKey,
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
        
        // 読み込みだけで永続ファイルを書き換えない。明示的な追加・削除・更新時のみ保存する。
    }

     fun saveAll() {
         try {
             val config = YamlConfiguration()
             val section = config.createSection("portals")
             for ((id, data) in portals) {
                 val s = section.createSection(id.toString())
                 s.set("type", data.type.key)
                 s.set("location", mapOf(
                     "world_key" to data.worldKey,
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
                 s.set("target_world_key", data.targetWorldKey)
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
            it.worldKey == location.world?.key?.toString() &&
            it.x == location.blockX &&
            it.y == location.blockY &&
            it.z == location.blockZ
        }
    }

    fun findByContainingLocation(location: org.bukkit.Location): PortalData? {
        val worldKey = location.world?.key?.toString() ?: return null
        val exact = findByLocation(location)
        if (exact != null) return exact

        return portals.values.find {
            it.worldKey == worldKey && it.containsBlock(location.blockX, location.blockY, location.blockZ)
        }
    }

    fun findById(id: UUID): PortalData? = portals[id]
}
