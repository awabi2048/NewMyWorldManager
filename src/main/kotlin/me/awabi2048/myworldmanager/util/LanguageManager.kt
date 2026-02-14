package me.awabi2048.myworldmanager.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import me.awabi2048.myworldmanager.MyWorldManager

class LanguageManager(private val plugin: MyWorldManager) {
    private val langConfigs = mutableMapOf<String, YamlConfiguration>()
    private val defaultLangs = listOf("ja_jp", "en_us")

    init {
        loadAllLanguages()
    }

    /**
     * すべての言語ファイルを読み込む
     */
    fun loadAllLanguages() {
        langConfigs.clear()
        
        // デフォルトファイルを保存
        val langDir = File(plugin.dataFolder, "lang")
        if (!langDir.exists()) langDir.mkdirs()
        
        defaultLangs.forEach { lang ->
            val langFile = File(langDir, "$lang.yml")
            if (!langFile.exists()) {
                plugin.saveResource("lang/$lang.yml", false)
            }
        }
        
        // langフォルダ内のすべてのymlファイルを読み込む
        langDir.listFiles { f -> f.extension == "yml" }?.forEach { file ->
            val langName = file.nameWithoutExtension
            val config = YamlConfiguration.loadConfiguration(file)
            
            // リソース内ファイルからデフォルト値を補完
            val defaultStream = plugin.getResource("lang/$langName.yml")
            if (defaultStream != null) {
                val defaultConfig = YamlConfiguration.loadConfiguration(InputStreamReader(defaultStream, StandardCharsets.UTF_8))
                config.setDefaults(defaultConfig)
            }
            langConfigs[langName] = config
        }
    }

    private fun getLangName(player: Player?): String {
        return if (player != null) {
            // CC-Systemが有効で、use_cc_system設定が有効な場合、CC-Systemから言語設定を取得
            val useCCSystem = plugin.config.getBoolean("use_cc_system", false)
            val isCCSystemAvailable = CCSystemUtil.isCCSystemAvailable()

            if (useCCSystem && isCCSystemAvailable) {
                val lang = CCSystemUtil.getPlayerLanguageFromCCSystem(player)
                (lang ?: plugin.playerStatsRepository.findByUuid(player.uniqueId).language).lowercase()
            } else {
                val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                stats.language.lowercase()
            }
        } else {
            (plugin.config.getString("language", "ja_jp") ?: "ja_jp").lowercase()
        }
    }

    private fun getFallbackLangName(): String {
        return (plugin.config.getString("fallback_language", "ja_jp") ?: "ja_jp").lowercase()
    }

    private fun getLanguageCandidates(lang: String): List<String> {
        return linkedSetOf(lang.lowercase(), getFallbackLangName(), "ja_jp").toList()
    }

    private fun hasAnyLanguageConfig(lang: String): Boolean {
        return getLanguageCandidates(lang).any { langConfigs.containsKey(it) }
    }

    private fun findMessage(lang: String, key: String): String? {
        for (candidate in getLanguageCandidates(lang)) {
            val config = langConfigs[candidate] ?: continue
            val message = config.getString(key) ?: config.getDefaults()?.getString(key)
            if (message != null) return message
        }
        return null
    }

    private fun findMessageList(lang: String, key: String): List<String>? {
        for (candidate in getLanguageCandidates(lang)) {
            val config = langConfigs[candidate] ?: continue
            val list = config.getStringList(key)
            if (list.isNotEmpty()) return list

            val defaultList = config.getDefaults()?.getStringList(key)
            if (defaultList != null && defaultList.isNotEmpty()) return defaultList

            val single = config.getString(key) ?: config.getDefaults()?.getString(key)
            if (single != null) return listOf(single)
        }
        return null
    }

    /**
     * メッセージを取得する
     */
    fun getMessage(player: Player?, key: String): String {
        val lang = getLangName(player)
        if (!hasAnyLanguageConfig(lang)) return "§cMISSING_LANG_FILE: $lang"
        return findMessage(lang, key) ?: "§cMISSING_LANG: $key"
    }

    fun getMessage(key: String): String {
        return getMessage(null as Player?, key)
    }

    /**
     * Map形式のプレースホルダーを使用してメッセージを取得する
     */
    fun getMessage(player: Player?, key: String, placeholders: Map<String, Any>): String {
        val lang = getLangName(player)
        if (!hasAnyLanguageConfig(lang)) return "§cMISSING_LANG_FILE: $lang"
        var message = findMessage(lang, key) ?: return "§cMISSING_LANG: $key"

        placeholders.forEach { (placeholder, value) ->
            message = message.replace("{$placeholder}", value.toString())
        }

        return message
    }

    fun getMessage(key: String, placeholders: Map<String, Any>): String {
        return getMessage(null as Player?, key, placeholders)
    }

    fun getComponent(player: Player?, key: String, placeholders: Map<String, Any>): Component {
        val message = getMessage(player, key, placeholders)
        return LegacyComponentSerializer.legacySection().deserialize(message).decoration(TextDecoration.ITALIC, false)
    }

    fun getComponent(key: String, placeholders: Map<String, Any>): Component {
        return getComponent(null as Player?, key, placeholders)
    }

    fun getComponent(player: Player?, key: String): Component {
        val message = getMessage(player, key)
        return LegacyComponentSerializer.legacySection().deserialize(message).decoration(TextDecoration.ITALIC, false)
    }

    fun getComponentList(player: Player?, key: String): List<Component> {
        return getMessageList(player, key).map {
            LegacyComponentSerializer.legacySection().deserialize(it).decoration(TextDecoration.ITALIC, false)
        }
    }

    /**
     * キーが存在するか確認する
     */
    fun hasKey(player: Player?, key: String): Boolean {
        return hasKey(getLangName(player), key)
    }

    fun hasKey(lang: String, key: String): Boolean {
        for (candidate in getLanguageCandidates(lang)) {
            val config = langConfigs[candidate] ?: continue
            if (config.contains(key) || (config.getDefaults()?.contains(key) ?: false)) {
                return true
            }
        }
        return false
    }

    /**
     * メッセージを厳格に取得する（存在しない場合はnullを返す）
     */
    fun getMessageStrict(player: Player?, key: String): String? {
        val lang = getLangName(player)
        if (!hasAnyLanguageConfig(lang)) return null
        return findMessage(lang, key)
    }

    /**
     * リスト形式のメッセージを厳格に取得する（存在しない場合はnullを返す）
     */
    fun getMessageListStrict(player: Player?, key: String): List<String>? {
        val lang = getLangName(player)
        if (!hasAnyLanguageConfig(lang)) return null
        return findMessageList(lang, key)
    }

    /**
     * リスト形式のメッセージを取得する
     */
    fun getMessageList(player: Player?, key: String): List<String> {
        return getMessageListDraft(getLangName(player), key)
    }

    fun getMessageList(key: String): List<String> {
        return getMessageList(null as Player?, key)
    }

    fun getMessageListDraft(lang: String, key: String): List<String> {
        if (!hasAnyLanguageConfig(lang)) return listOf("§cMISSING_LANG_FILE: $lang")
        return findMessageList(lang, key) ?: listOf("§cMISSING_LANG: $key")
    }

    /**
     * Map形式のプレースホルダーを使用してリスト形式のメッセージを取得する
     */
    fun getMessageList(player: Player?, key: String, placeholders: Map<String, Any>): List<String> {
        val lang = getLangName(player)
        val resultList = getMessageListDraft(lang, key)
        return resultList.map {
            var line = it
            placeholders.forEach { (placeholder, value) ->
                line = line.replace("{$placeholder}", value.toString())
            }
            line
        }
    }

    fun getMessageList(key: String, placeholders: Map<String, Any>): List<String> {
        return getMessageList(null as Player?, key, placeholders)
    }


    fun getComponentList(player: Player?, key: String, placeholders: Map<String, Any>): List<Component> {
        return getMessageList(player, key, placeholders).flatMap { it.split("\n") }.map {
            LegacyComponentSerializer.legacySection().deserialize(it).decoration(TextDecoration.ITALIC, false)
        }
    }

    fun getComponentList(key: String, placeholders: Map<String, Any>): List<Component> {
        return getComponentList(null as Player?, key, placeholders)
    }

    fun getSupportedLanguages(): List<String> {
        return langConfigs.keys.toList().sorted()
    }

    /**
     * 指定されたタイトルが、いずれかの言語の翻訳結果と一致するか確認する
     */
    fun isKeyMatch(title: String, key: String): Boolean {
        val plainSerializer = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
        val legacySerializer = LegacyComponentSerializer.legacySection()

        for (lang in getSupportedLanguages()) {
            val config = langConfigs[lang] ?: continue
            val templateRaw = config.getString(key) ?: config.getDefaults()?.getString(key) ?: continue
            val template = plainSerializer.serialize(legacySerializer.deserialize(templateRaw))

            // プレースホルダー {...} を .* に置換して正規表現を作成
            val regexString = "^" + Regex.escape(template).replace(Regex("\\\\\\{[^}]+\\\\\\}"), ".*") + "$"
            if (title.matches(Regex(regexString))) return true
        }
        return false
    }

    /**
     * 指定されたタイトルが、いずれかの言語の翻訳結果で始まるか確認する
     */
    fun isKeyStartWith(title: String, key: String): Boolean {
        val plainSerializer = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
        val legacySerializer = LegacyComponentSerializer.legacySection()

        for (lang in getSupportedLanguages()) {
            val config = langConfigs[lang] ?: continue
            val templateRaw = config.getString(key) ?: config.getDefaults()?.getString(key) ?: continue
            val template = plainSerializer.serialize(legacySerializer.deserialize(templateRaw))

            // プレースホルダー {...} を .* に置換して正規表現を作成
            val regexString = "^" + Regex.escape(template).replace(Regex("\\\\\\{[^}]+\\\\\\}"), ".*")
            if (title.contains(Regex(regexString))) return true
        }
        return false
    }
}
