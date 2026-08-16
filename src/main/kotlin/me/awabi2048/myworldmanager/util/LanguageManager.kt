package me.awabi2048.myworldmanager.util

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.localization.LocalizationKey
import me.awabi2048.myworldmanager.MyWorldManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.entity.Player
import java.util.Locale

/**
 * MyWorldManager の表示境界です。
 * 呼び出し側が生成済みキーを渡すことを型で必須にし、文字列キーの実行時解決は提供しません。
 */
class LanguageManager(private val plugin: MyWorldManager) {
    private val serializer = LegacyComponentSerializer.legacySection()

    fun getMessage(player: Player?, key: LocalizationKey<String>): String =
        CCSystem.getAPI().getLocalized(player, key).replace('&', '§')

    fun getMessage(key: LocalizationKey<String>): String = getMessage(null as Player?, key)

    fun getMessage(player: Player?, key: LocalizationKey<String>, placeholders: Map<String, Any>): String =
        CCSystem.getAPI().getLocalized(player, key, placeholders).replace('&', '§')

    fun getMessage(key: LocalizationKey<String>, placeholders: Map<String, Any>): String =
        getMessage(null as Player?, key, placeholders)

    fun getComponent(player: Player?, key: LocalizationKey<String>): Component =
        normalizeComponent(serializer.deserialize(getMessage(player, key)))

    fun getComponent(player: Player?, key: LocalizationKey<String>, placeholders: Map<String, Any>): Component =
        normalizeComponent(serializer.deserialize(getMessage(player, key, placeholders)))

    fun getMessageList(player: Player?, key: LocalizationKey<List<String>>): List<String> =
        CCSystem.getAPI().getLocalized(player, key).map { it.replace('&', '§') }

    fun getMessageList(key: LocalizationKey<List<String>>): List<String> = getMessageList(null as Player?, key)

    fun getMessageList(
        player: Player?,
        key: LocalizationKey<List<String>>,
        placeholders: Map<String, Any>,
    ): List<String> = CCSystem.getAPI().getLocalized(player, key, placeholders).map { it.replace('&', '§') }

    fun getComponentList(player: Player?, key: LocalizationKey<List<String>>): List<Component> =
        getMessageList(player, key).map { normalizeComponent(serializer.deserialize(it)) }

    fun getComponentList(
        player: Player?,
        key: LocalizationKey<List<String>>,
        placeholders: Map<String, Any>,
    ): List<Component> = getMessageList(player, key, placeholders).flatMap { it.split("\n") }.map {
        normalizeComponent(serializer.deserialize(it))
    }

    fun resolveLocale(player: Player?): String = if (player != null) {
        CCSystem.getAPI().getPlayerLanguage(player).lowercase(Locale.ROOT)
    } else {
        CCSystem.getAPI().getSupportedLanguages().firstOrNull()?.lowercase(Locale.ROOT) ?: "ja_jp"
    }

    fun getSupportedLanguages(): List<String> =
        CCSystem.getAPI().getSupportedLanguages().toList().sorted()

    private fun normalizeComponent(component: Component): Component = component
        .colorIfAbsent(NamedTextColor.WHITE)
        .decoration(TextDecoration.ITALIC, false)
}
