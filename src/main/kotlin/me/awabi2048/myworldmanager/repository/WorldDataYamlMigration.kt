package me.awabi2048.myworldmanager.repository

import me.awabi2048.myworldmanager.model.ManagedDimension
import java.util.Locale
import java.util.UUID

/**
 * WorldData の生YAMLを、厳格なデシリアライズ前に現行形式へ整える変換器です。
 * このクラスはファイルへ書き込まず、書き込みは /mwm migration の実行経路だけが担当します。
 */
object WorldDataYamlMigration {
    const val CURRENT_SCHEMA_VERSION = 1

    /**
     * Bukkit の ConfigurationSerialization は不正な保存データを読むと、呼び出し側で捕捉しても
     * ERROR とスタックトレースを出力します。通常ロードでは先に生YAMLを検査し、移行待ちの
     * データをデシリアライザへ渡さず隔離することで、想定内の旧形式を起動障害と区別します。
     */
    fun currentSchemaViolation(lines: List<String>, expectedUuid: UUID): String? {
        if (worldDataBounds(lines) == null) return "world_data is missing"

        val rawDimension = readField(lines, "dimension")
            ?: return "dimension is missing"
        val normalizedDimension = normalizeDimension(rawDimension)
            ?: return "dimension is invalid: $rawDimension"
        if (rawDimension.uppercase(Locale.ROOT) != normalizedDimension) {
            return "dimension is not canonical: $rawDimension"
        }

        val rawSchemaVersion = readField(lines, "schema_version")
            ?: return "schema_version is missing"
        val schemaVersion = rawSchemaVersion.toIntOrNull()
            ?: return "schema_version is invalid: $rawSchemaVersion"
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            return "unsupported schema_version: $schemaVersion"
        }

        val serializedUuid = readField(lines, "uuid")
            ?.runCatching { UUID.fromString(this) }
            ?.getOrNull()
            ?: return "uuid is missing or invalid"
        if (serializedUuid != expectedUuid) return "uuid does not match file name"

        val worldKey = readField(lines, "world_key")
            ?: return "world_key is missing"
        if (!worldKey.contains(':')) return "world_key is invalid: $worldKey"
        return null
    }

    fun readField(lines: List<String>, field: String): String? {
        val bounds = worldDataBounds(lines) ?: return null
        val indent = childIndent(lines, bounds)
        return (bounds.first + 1 until bounds.second)
            .asSequence()
            .map { lines[it] }
            .firstOrNull { line -> line.startsWith("$indent$field:") }
            ?.substringAfter(':')
            ?.trim()
            ?.trim('"', '\'')
            ?.takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
    }

    /** 旧実装が保存した代表的な次元名を、現行のManagedDimension名へ変換します。 */
    fun normalizeDimension(raw: String?): String? {
        val value = raw?.trim()?.uppercase(Locale.ROOT) ?: return null
        return when (value) {
            "NORMAL", "OVERWORLD" -> ManagedDimension.OVERWORLD.name
            "NETHER" -> ManagedDimension.NETHER.name
            "THE_END", "END" -> ManagedDimension.END.name
            else -> null
        }
    }

    /**
     * world_key と dimension の欠落・旧表記を補正します。
     * dimensionが不明な場合、明示入力なしでは推測せず、変更せずにnullを返します。
     */
    fun migrate(
        lines: List<String>,
        uuid: UUID,
        dimension: String? = null,
    ): List<String>? {
        if (worldDataBounds(lines) == null) return null
        val currentDimension = readField(lines, "dimension")
        val normalizedDimension = normalizeDimension(currentDimension)
        if (dimension == null && normalizedDimension == null) return null
        val rawSchemaVersion = readField(lines, "schema_version")
        val schemaVersion = rawSchemaVersion?.toIntOrNull()
        if (rawSchemaVersion != null && schemaVersion == null) return null
        if (schemaVersion != null && schemaVersion > CURRENT_SCHEMA_VERSION) return null
        val migrated = lines.toMutableList()
        var changed = false

        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            val currentBounds = worldDataBounds(migrated) ?: return null
            val existingIndex = (currentBounds.first + 1 until currentBounds.second)
                .firstOrNull { migrated[it].trimStart().startsWith("schema_version:") }
            if (existingIndex != null) {
                val indent = migrated[existingIndex].takeWhile(Char::isWhitespace)
                migrated[existingIndex] = "${indent}schema_version: $CURRENT_SCHEMA_VERSION"
            } else {
                migrated.add(
                    currentBounds.first + 1,
                    "${childIndent(migrated, currentBounds)}schema_version: $CURRENT_SCHEMA_VERSION",
                )
            }
            changed = true
        }

        if (readField(migrated, "world_key") == null) {
            val customWorldName = readField(migrated, "custom_world_name") ?: "my_world.$uuid"
            val currentBounds = worldDataBounds(migrated) ?: return null
            val insertAfter = (currentBounds.first + 1 until currentBounds.second)
                .lastOrNull { migrated[it].trimStart().startsWith("custom_world_name:") }
                ?: currentBounds.first
            migrated.add(
                insertAfter + 1,
                "${childIndent(migrated, currentBounds)}world_key: minecraft:$customWorldName",
            )
            changed = true
        }

        val targetDimension = dimension ?: normalizedDimension
        if (targetDimension != null && currentDimension != targetDimension) {
            val currentBounds = worldDataBounds(migrated) ?: return null
            val existingIndex = (currentBounds.first + 1 until currentBounds.second)
                .firstOrNull { migrated[it].trimStart().startsWith("dimension:") }
            if (existingIndex != null) {
                val indent = migrated[existingIndex].takeWhile(Char::isWhitespace)
                migrated[existingIndex] = "${indent}dimension: $targetDimension"
            } else {
                val insertAfter = (currentBounds.first + 1 until currentBounds.second)
                    .firstOrNull { migrated[it].trimStart().startsWith("uuid:") }
                    ?: currentBounds.first
                migrated.add(
                    insertAfter + 1,
                    "${childIndent(migrated, currentBounds)}dimension: $targetDimension",
                )
            }
            changed = true
        }

        return migrated.takeIf { changed }
    }

    private fun childIndent(lines: List<String>, bounds: Pair<Int, Int>): String =
        lines.drop(bounds.first + 1)
            .take(bounds.second - bounds.first - 1)
            .firstOrNull { it.isNotBlank() }
            ?.takeWhile(Char::isWhitespace)
            ?: "  "

    private fun worldDataBounds(lines: List<String>): Pair<Int, Int>? {
        val start = lines.indexOfFirst { it.trimEnd() == "world_data:" }
        if (start < 0) return null
        val end = (start + 1 until lines.size)
            .firstOrNull { lines[it].isNotBlank() && !lines[it].startsWith(" ") }
            ?: lines.size
        return start to end
    }
}
