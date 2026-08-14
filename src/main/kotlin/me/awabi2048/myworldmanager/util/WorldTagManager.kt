package me.awabi2048.myworldmanager.util

import com.awabi2048.ccsystem.api.localization.LocalizationKey
import me.awabi2048.myworldmanager.MyWorldManager
import org.bukkit.entity.Player
import java.util.Locale

class WorldTagManager(private val plugin: MyWorldManager) {

    private val configuredTagIds = mutableListOf<String>()
    private val knownTagIds = linkedSetOf<String>()
    private val localizedTagKeys = linkedMapOf<String, LocalizationKey<String>>()

    fun reload() {
        configuredTagIds.clear()
        knownTagIds.clear()
        localizedTagKeys.clear()

        plugin.config.getStringList("world_tags")
            .mapNotNull { normalizeTagId(it) }
            .forEach { tagId ->
                if (knownTagIds.add(tagId)) {
                    // 運用設定由来のキーはreload時に解決し、表示処理へStringキーを持ち越しません。
                    localizedTagKeys[tagId] = CatalogKeyResolver.worldTag(tagId)
                    configuredTagIds.add(tagId)
                }
            }

        if (configuredTagIds.isEmpty()) {
            loadFallbackDefinitions()
        }
    }

    fun getEnabledTagIds(): List<String> {
        return configuredTagIds.toList()
    }

    fun getEditableTagIds(currentTagIds: List<String>): List<String> {
        val editable = linkedSetOf<String>()
        editable.addAll(configuredTagIds)

        currentTagIds
            .mapNotNull { normalizeTagId(it) }
            .forEach { tagId ->
                if (!knownTagIds.contains(tagId)) {
                    editable.add(tagId)
                }
            }

        return editable.toList()
    }

    fun isKnownTag(tagId: String): Boolean {
        val normalized = normalizeTagId(tagId) ?: return false
        return knownTagIds.contains(normalized)
    }

    fun normalizeTagId(raw: String?): String? {
        if (raw == null) return null
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val upper = trimmed.uppercase(Locale.ROOT)
        val legacy = legacyTagMap[upper]
        if (legacy != null) return legacy

        return trimmed.lowercase(Locale.ROOT)
    }

    fun getDisplayName(player: Player?, tagId: String): String {
        val normalized = normalizeTagId(tagId) ?: return tagId
        val lang = plugin.languageManager

        val key = localizedTagKeys[normalized]
        // 旧データにだけ残る任意タグは翻訳キーではなくユーザーデータとして表示します。
        return key?.let { lang.getMessage(player, it) } ?: normalized
    }

    private fun loadFallbackDefinitions() {
        listOf("shop", "minigame", "building", "facility", "streaming").forEach { id ->
            knownTagIds.add(id)
            localizedTagKeys[id] = CatalogKeyResolver.worldTag(id)
            configuredTagIds.add(id)
        }
    }

    private val legacyTagMap = mapOf(
        "SHOP" to "shop",
        "MINIGAME" to "minigame",
        "BUILDING" to "building",
        "FACILITY" to "facility",
        "STREAMING" to "streaming"
    )
}
