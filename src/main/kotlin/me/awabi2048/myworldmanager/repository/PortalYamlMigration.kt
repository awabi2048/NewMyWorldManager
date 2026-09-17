package me.awabi2048.myworldmanager.repository

import org.bukkit.NamespacedKey
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

/**
 * portals.yml の旧形式（`location.world` / `target_world_name`）を現行形式
 * （`location.world_key` / `target_world_key`）へ変換します。
 *
 * 背景：Paper 26 移行（6a56911）で保存形式だけが world名から world_keyへ切り替わり、
 * 既存ファイルの変換処理と版数更新が行われませんでした。その後の隔離強化（f2ea8df）で
 * `config_version: 1` のまま旧レコードが隔離対象となり、`/mwm migration` の版数判定
 * （`< CURRENT` のときだけ移行）を永久に通過できなくなりました。
 * この変換器はファイルへ書き込まず、書き込みは `/mwm migration` の実行経路だけが担当します。
 */
object PortalYamlMigration {
    /**
     * バニラのフォルダ名と Paper のワールドキーは一致しません。
     * 例：フォルダ `world` のキーは `minecraft:overworld` です。
     * 添付の移行データでも `world` 配置の新形式が `minecraft:overworld` で保存されていました。
     */
    private val VANILLA_FOLDER_TO_KEY = mapOf(
        "world" to "minecraft:overworld",
        "world_nether" to "minecraft:the_nether",
        "world_the_end" to "minecraft:the_end",
    )

    private const val MY_WORLD_PREFIX = "my_world."

    /**
     * 旧キーの残存を確認します。版数が現行でも旧キーが残っていれば移行対象です。
     * 版数だけでは新旧を区別できない（旧形式も `config_version: 1` を持つ）ため、
     * 実データの検査を併用します。
     */
    fun needsMigration(config: YamlConfiguration): Boolean {
        val section = config.getConfigurationSection("portals") ?: return false
        return section.getKeys(false).any { rawId ->
            val child = section.getConfigurationSection(rawId) ?: return@any true
            isLegacySection(child)
        }
    }

    fun isLegacySection(section: ConfigurationSection): Boolean {
        val location = section.getConfigurationSection("location")
        if (location != null) {
            // 新形式は world_key を持ちます。旧形式は world だけを持ちます。
            val hasWorldKey = location.contains("world_key")
            val hasWorld = location.contains("world")
            if (!hasWorldKey && hasWorld) return true
            if (hasWorldKey && hasWorld) return true
        } else if (section.contains("location")) {
            // Location オブジェクト等の非セクション形式は現行パーサで扱えないため移行対象とします。
            return true
        }
        // 旧行き先名が残っていれば移行対象です。
        if (section.contains("target_world_name") && !section.contains("target_world_key")) return true
        if (section.contains("target_world_name") && section.contains("target_world_key")) return true
        return false
    }

    /**
     * 1レコードを現行形式へ整えます。
     * 成功時は true を返し、セクション内の `location.world_key` / `target_world_key` が確定します。
     * 解決できない旧値は推測で上書きせず false を返し、呼び出し側が隔離に残せるようにします。
     *
     * @param findWorldKeyByUuid MyWorld UUID に対応する現行 worldKey（customWorldName 考慮済み）
     * @param findWorldKeyByFolder フォルダ名・旧ワールド名に対応する現行 worldKey
     */
    fun migrateSection(
        section: ConfigurationSection,
        findWorldKeyByUuid: (UUID) -> String?,
        findWorldKeyByFolder: (String) -> String?,
    ): Boolean {
        var changed = false

        val location = section.getConfigurationSection("location")
        if (location == null) {
            // 非セクション形式（旧 Location オブジェクト等）はここでは変換しません。
            return false
        }
        val existingWorldKey = location.getString("world_key")
        val legacyWorld = location.getString("world")
        if (existingWorldKey == null && legacyWorld != null) {
            val resolved = resolveLocationWorldKey(legacyWorld, findWorldKeyByUuid, findWorldKeyByFolder)
                ?: return false
            location.set("world_key", resolved)
            // 旧キーを残すと新旧混在の原因になるため除去します。原本は移行前のバックアップに残ります。
            location.set("world", null)
            changed = true
        } else if (existingWorldKey != null && legacyWorld != null) {
            // 混在ファイルでは新形式を優先し、旧キーを除去します。
            location.set("world", null)
            changed = true
        }

        val existingTargetKey = section.getString("target_world_key")
        val legacyTargetName = section.getString("target_world_name")
        if (existingTargetKey == null && legacyTargetName != null) {
            val resolved = resolveTargetWorldKey(legacyTargetName, findWorldKeyByFolder)
                ?: return false
            section.set("target_world_key", resolved)
            section.set("target_world_name", null)
            changed = true
        } else if (existingTargetKey != null && legacyTargetName != null) {
            section.set("target_world_name", null)
            changed = true
        }

        // 既に新形式のレコードは変換不要として成功扱いにし、移行を冪等にします。
        if (!changed) {
            val worldKey = location.getString("world_key") ?: return false
            return NamespacedKey.fromString(worldKey) != null
        }
        val worldKey = location.getString("world_key") ?: return false
        return NamespacedKey.fromString(worldKey) != null
    }

    /**
     * 設置側 `location.world` の旧値を world_key へ解決します。
     * 存在しないワールドでも形式さえ正しければフォールバック値を返し、
     * 読み込み時の存在確認（警告のみ）に委ねてデータを失いません。
     */
    fun resolveLocationWorldKey(
        legacy: String,
        findWorldKeyByUuid: (UUID) -> String?,
        findWorldKeyByFolder: (String) -> String?,
    ): String? {
        val trimmed = legacy.trim()
        if (trimmed.isEmpty()) return null
        // 既にキー形式の値は検証のみ行います。
        if (':' in trimmed) {
            return NamespacedKey.fromString(trimmed)?.toString()
        }
        // MyWorld は UUID またはフォルダ名から現行 worldKey を優先します。
        // customWorldName を持つワールドは `my_world.<UUID>` と worldKey が一致しないため、
        // リポジトリ参照を先に行い、見つからない場合のみ既定形式へフォールバックします。
        if (trimmed.startsWith(MY_WORLD_PREFIX)) {
            val rawUuid = trimmed.removePrefix(MY_WORLD_PREFIX)
            val uuid = runCatching { UUID.fromString(rawUuid) }.getOrNull()
            if (uuid != null) {
                findWorldKeyByUuid(uuid)?.let { return it }
            }
            findWorldKeyByFolder(trimmed)?.let { return it }
            // フォールバックもキー形式として正しいことを確認します。
            return NamespacedKey.fromString("minecraft:$trimmed")?.toString()
        }
        VANILLA_FOLDER_TO_KEY[trimmed]?.let { return it }
        findWorldKeyByFolder(trimmed)?.let { return it }
        // 外部ワールド（spawn / end_b_2 / resource_* 等）は minecraft 名前空間へ畳みます。
        // これは設置時の `normalizeWorldKey` と同じ規則です。
        return try {
            NamespacedKey.minecraft(trimmed).toString()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 行き先側 `target_world_name` の旧値を target_world_key へ解決します。
     */
    fun resolveTargetWorldKey(
        legacy: String,
        findWorldKeyByFolder: (String) -> String?,
    ): String? {
        val trimmed = legacy.trim()
        if (trimmed.isEmpty()) return null
        if (':' in trimmed) {
            return NamespacedKey.fromString(trimmed)?.toString()
        }
        VANILLA_FOLDER_TO_KEY[trimmed]?.let { return it }
        findWorldKeyByFolder(trimmed)?.let { return it }
        return try {
            NamespacedKey.minecraft(trimmed).toString()
        } catch (_: Exception) {
            null
        }
    }
}
