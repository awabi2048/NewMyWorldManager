package me.awabi2048.myworldmanager.repository

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.ManagedDimension
import me.awabi2048.myworldmanager.model.TemplateData
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant

class TemplateRepository(private val plugin: MyWorldManager) {
    enum class ValidationIssue {
        MISSING_DIRECTORY,
        MISSING_ORIGIN
    }

    private val templates = mutableMapOf<String, TemplateData>()
    val missingTemplates = mutableListOf<String>()
    private val quarantined = mutableMapOf<String, QuarantinedTemplateData>()
    private val configFile = File(plugin.dataFolder, "templates.yml")

    init {
        loadTemplates()
    }

    /**
     * テンプレートはセクション単位で読み込み、不正な1件が有効なテンプレートを止めないようにします。
     * ファイルの修正は行わず、問題のあるセクションを移行対象として隔離します。
     */
    fun loadTemplates() {
        if (!configFile.exists()) {
            plugin.saveResource("templates.yml", false)
        }

        templates.clear()
        missingTemplates.clear()
        quarantined.clear()
        val config = try {
            YamlConfiguration.loadConfiguration(configFile)
        } catch (e: Exception) {
            quarantined["<file>"] = QuarantinedTemplateData(
                id = "<file>",
                reason = e.message ?: e.javaClass.simpleName,
                detectedAt = Instant.now(),
                contentHash = MigrationFileFingerprint.sha256(configFile),
            )
            plugin.logger.warning("テンプレート定義全体を隔離しました: ${e.message}")
            return
        }

        config.getKeys(false).forEach { key ->
            val section = config.getConfigurationSection(key) ?: return@forEach
            try {
                val path = section.getString("path") ?: ""
                val dimension = ManagedDimension.parse(
                    section.getString("dimension")
                        ?: throw IllegalArgumentException("Template '$key' has no dimension")
                )
                val name = section.getString("name") ?: key
                val description = if (section.isList("description")) {
                    section.getStringList("description")
                } else {
                    section.getString("description")?.let(::listOf) ?: emptyList()
                }
                val icon = Material.matchMaterial(section.getString("icon") ?: "PAPER") ?: Material.PAPER

                val originLocation = section.getString("origin_location")?.let { raw ->
                    val parts = raw.split(",").map { it.trim() }
                    if (parts.size < 3) {
                        plugin.logger.warning("Template '$key' has an invalid origin_location format: $raw")
                        null
                    } else {
                        runCatching {
                            org.bukkit.Location(null, parts[0].toDouble() + 0.5, parts[1].toDouble(), parts[2].toDouble() + 0.5)
                        }.onFailure {
                            plugin.logger.warning("Template '$key' has an invalid origin_location format: $raw")
                        }.getOrNull()
                    }
                }

                val templateDir = plugin.worldDirectoryResolver.inspect(path)?.existingPath?.toFile()
                if (templateDir == null || !templateDir.exists() || !templateDir.isDirectory) {
                    missingTemplates.add(key)
                }

                templates[key] = TemplateData(
                    id = key,
                    dimension = dimension,
                    path = path,
                    name = name,
                    description = description,
                    icon = icon,
                    originLocation = originLocation,
                    previewTime = if (section.contains("preview_time")) section.getLong("preview_time") else null,
                    previewWeather = section.getString("preview_weather"),
                )
            } catch (e: Exception) {
                quarantined[key] = QuarantinedTemplateData(
                    id = key,
                    reason = e.message ?: e.javaClass.simpleName,
                    detectedAt = Instant.now(),
                    path = section.getString("path"),
                    contentHash = MigrationFileFingerprint.sha256(configFile),
                )
                plugin.logger.warning("テンプレート $key を隔離しました: ${e.message}")
            }
        }
    }

    fun findAll(): List<TemplateData> = templates.values.toList()

    fun quarantinedTemplates(): List<QuarantinedTemplateData> = quarantined.values.sortedBy { it.id }

    fun isQuarantined(id: String): Boolean = quarantined.containsKey(id)

    fun findById(id: String): TemplateData? = templates[id]

    fun findByWorldKey(key: org.bukkit.NamespacedKey): TemplateData? = templates.values.firstOrNull { template ->
        org.bukkit.NamespacedKey.fromString(template.path) == key
    }

    fun validationIssue(template: TemplateData): ValidationIssue? {
        val templateDir = plugin.worldDirectoryResolver.inspect(template.path)?.existingPath?.toFile()
        if (templateDir == null || !templateDir.exists() || !templateDir.isDirectory) {
            return ValidationIssue.MISSING_DIRECTORY
        }
        if (template.originLocation == null) {
            return ValidationIssue.MISSING_ORIGIN
        }
        return null
    }

    fun isUsable(template: TemplateData): Boolean = validationIssue(template) == null

    /** /mwm migration からだけ呼び出されるテンプレート定義のdimension移行です。 */
    @Synchronized
    internal fun migrateTemplate(
        id: String,
        dimension: ManagedDimension? = null,
        forceDimension: Boolean = false,
    ): MetadataMigrationResult {
        val config = YamlConfiguration.loadConfiguration(configFile)
        val section = config.getConfigurationSection(id)
            ?: return MetadataMigrationResult(MetadataMigrationStatus.FAILED, "template not found: $id")
        val currentRaw = section.getString("dimension")
        val quarantinedData = quarantined[id]
        if (forceDimension && quarantinedData == null) {
            return MetadataMigrationResult(
                MetadataMigrationStatus.FAILED,
                "template is not quarantined: $id"
            )
        }
        val currentHash = MigrationFileFingerprint.sha256(configFile)
        if (quarantinedData?.contentHash != null && quarantinedData.contentHash != currentHash) {
            return MetadataMigrationResult(
                MetadataMigrationStatus.FAILED,
                "template changed after quarantine: $id"
            )
        }
        val normalizedDimension = WorldDataYamlMigration.normalizeDimension(currentRaw)
        if (dimension == null && normalizedDimension == null) {
            return MetadataMigrationResult(MetadataMigrationStatus.NEEDS_INPUT, "dimension is required: $id")
        }
        val targetDimension = if (!forceDimension && normalizedDimension != null) {
            ManagedDimension.parse(normalizedDimension)
        } else {
            dimension ?: return MetadataMigrationResult(
                MetadataMigrationStatus.NEEDS_INPUT,
                "dimension is required: $id",
            )
        }
        if (currentRaw == targetDimension.name) {
            return MetadataMigrationResult(MetadataMigrationStatus.ALREADY_CURRENT, "already current: $id")
        }

        section.set("dimension", targetDimension.name)
        val backup = File(configFile.parentFile, "${configFile.name}.pre-migration-${System.currentTimeMillis()}.bak")
        val temporary = File(configFile.parentFile, "${configFile.name}.migration.tmp")
        try {
            Files.copy(configFile.toPath(), backup.toPath())
            config.save(temporary)
            moveAtomically(temporary, configFile)
            loadTemplates()
            check(!isQuarantined(id)) { "migrated template remains invalid: $id" }
            return MetadataMigrationResult(MetadataMigrationStatus.MIGRATED, "migrated: $id")
        } catch (e: Exception) {
            temporary.delete()
            runCatching { Files.copy(backup.toPath(), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            loadTemplates()
            return MetadataMigrationResult(
                MetadataMigrationStatus.FAILED,
                "template migration failed for $id: ${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    fun saveTemplate(template: TemplateData) {
        check(!isQuarantined(template.id)) {
            "Cannot save quarantined template: ${template.id}"
        }
        templates[template.id] = template
        val config = YamlConfiguration.loadConfiguration(configFile)
        val section = config.createSection(template.id)
        section.set("path", template.path)
        section.set("dimension", template.dimension.name)
        section.set("name", template.name)
        section.set("description", template.description)
        section.set("icon", template.icon.name)
        template.originLocation?.let { loc ->
            section.set("origin_location", "${loc.blockX}, ${loc.blockY}, ${loc.blockZ}")
        }
        section.set("preview_time", template.previewTime)
        section.set("preview_weather", template.previewWeather)
        config.save(configFile)
    }

    private fun moveAtomically(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
