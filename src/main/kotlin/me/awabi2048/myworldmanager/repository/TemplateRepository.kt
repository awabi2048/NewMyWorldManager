package me.awabi2048.myworldmanager.repository

import me.awabi2048.myworldmanager.model.TemplateData
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class TemplateRepository(private val plugin: JavaPlugin) {
    private val templates = mutableMapOf<String, TemplateData>()
    val missingTemplates = mutableListOf<String>()
    private val configFile = File(plugin.dataFolder, "templates.yml")

    init {
        loadTemplates()
    }

    fun loadTemplates() {
        if (!configFile.exists()) {
            plugin.saveResource("templates.yml", false)
        }

        val config = YamlConfiguration.loadConfiguration(configFile)
        templates.clear()
        missingTemplates.clear()

        config.getKeys(false).forEach { key ->
            val section = config.getConfigurationSection(key) ?: return@forEach
            val path = section.getString("path") ?: ""
            val name = section.getString("name") ?: key
            val description = if (section.isList("description")) {
                section.getStringList("description")
            } else {
                val descStr = section.getString("description")
                if (descStr != null) listOf(descStr) else emptyList()
            }
            val iconStr = section.getString("icon") ?: "PAPER"
            val icon = Material.matchMaterial(iconStr) ?: Material.PAPER
            
            // Validate path
            val templateDir = File(Bukkit.getWorldContainer(), path)
            if (!templateDir.exists() || !templateDir.isDirectory) {
                missingTemplates.add(key)
            } else {
                val originStr = section.getString("origin_location")
                var originLocation: org.bukkit.Location? = null
                if (originStr != null) {
                    val parts = originStr.split(",").map { it.trim() }
                    if (parts.size >= 3) {
                        try {
                            val x = parts[0].toDouble()
                            val y = parts[1].toDouble()
                            val z = parts[2].toDouble()
                            // Worldはロード時に設定されるため、ここではnullのままLocationを作成
                            // x, zはブロック中心(+0.5)に合わせる
                            originLocation = org.bukkit.Location(null, x + 0.5, y, z + 0.5)
                        } catch (e: Exception) {
                            plugin.logger.warning("Template '$key' has an invalid origin_location format: $originStr")
                        }
                    }
                }

                val previewTime = if (section.contains("preview_time")) section.getLong("preview_time") else null
                val previewWeather = section.getString("preview_weather")

                templates[key] = TemplateData(
                    id = key,
                    path = path,
                    name = name,
                    description = description,
                    icon = icon,
                    originLocation = originLocation,
                    previewTime = previewTime,
                    previewWeather = previewWeather
                )
            }
        }
    }

    fun findAll(): List<TemplateData> = templates.values.toList()

    fun findById(id: String): TemplateData? = templates[id]

    fun saveTemplate(template: TemplateData) {
        templates[template.id] = template
        val config = YamlConfiguration.loadConfiguration(configFile)
        val section = config.createSection(template.id)
        section.set("path", template.path)
        section.set("name", template.name)
        section.set("description", template.description)
        section.set("icon", template.icon.name)
        template.originLocation?.let { loc ->
            // 整数値として保存
            section.set("origin_location", "${loc.blockX}, ${loc.blockY}, ${loc.blockZ}")
        }
        section.set("preview_time", template.previewTime)
        section.set("preview_weather", template.previewWeather)
        config.save(configFile)
    }
}
