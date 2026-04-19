package me.awabi2048.myworldmanager.util

import com.awabi2048.ccsystem.CCSystem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.entity.Player
import java.util.Locale
import me.awabi2048.myworldmanager.MyWorldManager

class LanguageManager(private val plugin: MyWorldManager) {
    private val serializer = LegacyComponentSerializer.legacySection()

    init {
        loadAllLanguages()
    }

    fun loadAllLanguages() {
        val api = CCSystem.getAPI()
        val validation = api.validateI18nSource(plugin, featureByFile())
        if (validation.hasErrors) {
            val detail = validation.errors.joinToString("\n") { "- $it" }
            throw IllegalStateException("MyWorldManager の言語ファイル検証に失敗しました:\n$detail")
        }

        api.unregisterI18nSource(plugin.name)
        api.registerI18nSource(plugin.name, plugin, featureByFile().keys)
    }

    fun resolveLocale(player: Player?): String {
        return if (player != null) {
            CCSystem.getAPI().getPlayerLanguage(player).lowercase(Locale.ROOT)
        } else {
            CCSystem.getAPI().getSupportedLanguages().firstOrNull()?.lowercase(Locale.ROOT) ?: "ja_jp"
        }
    }

    fun getMessage(player: Player?, key: String): String {
        return CCSystem.getAPI().getI18nString(player, key).replace('&', '§')
    }

    fun getMessage(key: String): String {
        return getMessage(null as Player?, key)
    }

    fun getMessage(player: Player?, key: String, placeholders: Map<String, Any>): String {
        return CCSystem.getAPI().getI18nString(player, key, placeholders).replace('&', '§')
    }

    fun getMessage(key: String, placeholders: Map<String, Any>): String {
        return getMessage(null as Player?, key, placeholders)
    }

    fun getComponent(player: Player?, key: String, placeholders: Map<String, Any>): Component {
        return serializer.deserialize(getMessage(player, key, placeholders)).decoration(TextDecoration.ITALIC, false)
    }

    fun getComponent(key: String, placeholders: Map<String, Any>): Component {
        return getComponent(null as Player?, key, placeholders)
    }

    fun getComponent(player: Player?, key: String): Component {
        return serializer.deserialize(getMessage(player, key)).decoration(TextDecoration.ITALIC, false)
    }

    fun getComponentList(player: Player?, key: String): List<Component> {
        return getMessageList(player, key).map {
            serializer.deserialize(it).decoration(TextDecoration.ITALIC, false)
        }
    }

    fun hasKey(player: Player?, key: String): Boolean {
        return CCSystem.getAPI().hasI18nKey(key)
    }

    fun hasKey(lang: String, key: String): Boolean {
        return CCSystem.getAPI().hasI18nKey(key)
    }

    fun getMessageStrict(player: Player?, key: String): String? {
        return runCatching { getMessage(player, key) }.getOrNull()
    }

    fun getMessageListStrict(player: Player?, key: String): List<String>? {
        return runCatching { getMessageList(player, key) }.getOrNull()
    }

    fun getMessageList(player: Player?, key: String): List<String> {
        return CCSystem.getAPI().getI18nStringList(player, key).map { it.replace('&', '§') }
    }

    fun getMessageList(key: String): List<String> {
        return getMessageList(null as Player?, key)
    }

    fun getMessageListDraft(lang: String, key: String): List<String> {
        return CCSystem.getAPI().getI18nStringList(lang, key).map { it.replace('&', '§') }
    }

    fun getMessageList(player: Player?, key: String, placeholders: Map<String, Any>): List<String> {
        return CCSystem.getAPI().getI18nStringList(player, key, placeholders).map { it.replace('&', '§') }
    }

    fun getMessageList(key: String, placeholders: Map<String, Any>): List<String> {
        return getMessageList(null as Player?, key, placeholders)
    }

    fun getComponentList(player: Player?, key: String, placeholders: Map<String, Any>): List<Component> {
        return getMessageList(player, key, placeholders).flatMap { it.split("\n") }.map {
            serializer.deserialize(it).decoration(TextDecoration.ITALIC, false)
        }
    }

    fun getComponentList(key: String, placeholders: Map<String, Any>): List<Component> {
        return getComponentList(null as Player?, key, placeholders)
    }

    fun getSupportedLanguages(): List<String> {
        return CCSystem.getAPI().getSupportedLanguages().toList().sorted()
    }

    fun isKeyMatch(title: String, key: String): Boolean {
        return CCSystem.getAPI().isI18nKeyMatch(title, key)
    }

    fun isKeyStartWith(title: String, key: String): Boolean {
        return CCSystem.getAPI().isI18nKeyStartWith(title, key)
    }

    private fun featureByFile(): Map<String, String> {
        return mapOf(
            "_common.yml" to plugin.name,
            "colors.yml" to plugin.name,
            "world_tag.yml" to plugin.name,
            "publish_level.yml" to plugin.name,
            "gui_common.yml" to plugin.name,
            "gui_creation.yml" to plugin.name,
            "gui_portal.yml" to plugin.name,
            "gui_settings.yml" to plugin.name,
            "gui_admin.yml" to plugin.name,
            "gui_discovery.yml" to plugin.name,
            "gui_favorite.yml" to plugin.name,
            "gui_meet.yml" to plugin.name,
            "gui_bedrock.yml" to plugin.name,
            "messages.yml" to plugin.name,
            "role.yml" to plugin.name,
            "custom_item.yml" to plugin.name,
            "biomes.yml" to plugin.name
        )
    }
}
