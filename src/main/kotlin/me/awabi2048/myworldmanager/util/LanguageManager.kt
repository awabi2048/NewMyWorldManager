package me.awabi2048.myworldmanager.util

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.localization.LocalizationKey
import com.awabi2048.ccsystem.api.localization.LocalizationCatalogContract
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.entity.Player
import java.util.Locale
import me.awabi2048.myworldmanager.MyWorldManager

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

    fun hasKey(player: Player?, key: LocalizationKey<*>): Boolean = CCSystem.getAPI().hasI18nKey(key.id)

    @Deprecated("生成済みLocalizationKeyへ移行してください")
    fun hasKey(player: Player?, key: String): Boolean = CCSystem.getAPI().hasI18nKey(key)

    fun resolveLocale(player: Player?): String {
        return if (player != null) {
            CCSystem.getAPI().getPlayerLanguage(player).lowercase(Locale.ROOT)
        } else {
            CCSystem.getAPI().getSupportedLanguages().firstOrNull()?.lowercase(Locale.ROOT) ?: "ja_jp"
        }
    }

    // 移行中だけ残す厳密境界です。全呼出側を生成キーへ変更した後、この一群は削除します。
    @Deprecated("生成済みLocalizationKeyへ移行してください")
    fun getMessage(player: Player?, key: String): String = getMessage(player, LocalizationCatalogContract.resolveText(key))

    @Deprecated("生成済みLocalizationKeyへ移行してください")
    fun getMessage(key: String): String = getMessage(null as Player?, key)

    @Deprecated("生成済みLocalizationKeyへ移行してください")
    fun getMessage(player: Player?, key: String, placeholders: Map<String, Any>): String =
        getMessage(player, LocalizationCatalogContract.resolveText(key), placeholders)

    @Deprecated("生成済みLocalizationKeyへ移行してください")
    fun getMessage(key: String, placeholders: Map<String, Any>): String = getMessage(null as Player?, key, placeholders)

    @Deprecated("生成済みLocalizationKeyへ移行してください")
    fun getComponent(player: Player?, key: String, placeholders: Map<String, Any> = emptyMap()): Component =
        getComponent(player, LocalizationCatalogContract.resolveText(key), placeholders)

    @Deprecated("生成済みLocalizationKeyへ移行してください")
    fun getMessageList(player: Player?, key: String, placeholders: Map<String, Any> = emptyMap()): List<String> =
        getMessageList(player, LocalizationCatalogContract.resolveTextList(key), placeholders)

    @Deprecated("生成済みLocalizationKeyへ移行してください")
    fun getMessageList(key: String): List<String> = getMessageList(null as Player?, key)

    @Deprecated("生成済みLocalizationKeyへ移行してください")
    fun getMessageList(key: String, placeholders: Map<String, Any>): List<String> =
        getMessageList(null as Player?, key, placeholders)

    @Deprecated("生成済みLocalizationKeyへ移行してください")
    fun getComponentList(player: Player?, key: String, placeholders: Map<String, Any> = emptyMap()): List<Component> =
        getComponentList(player, LocalizationCatalogContract.resolveTextList(key), placeholders)

    @Deprecated("生成済みLocalizationKeyへ移行してください")
    fun getMessageStrict(player: Player?, key: String): String? = runCatching { getMessage(player, key) }.getOrNull()

    private fun normalizeComponent(component: Component): Component {
        return component
            .colorIfAbsent(NamedTextColor.WHITE)
            .decoration(TextDecoration.ITALIC, false)
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

}
