package me.awabi2048.myworldmanager.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
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
            plugin.playerStatsRepository.findByUuid(player.uniqueId).language
        } else {
            plugin.config.getString("language", "ja_jp") ?: "ja_jp"
        }
    }

    /**
     * メッセージを取得する
     */
    fun getMessage(player: Player?, key: String): String {
        val lang = getLangName(player)
        val config = langConfigs[lang] ?: langConfigs["ja_jp"] ?: return "§cMISSING_LANG_FILE: $lang"
        return config.getString(key) ?: config.getDefaults()?.getString(key) ?: return "§cMISSING_LANG: $key"
    }

    fun getMessage(key: String): String {
        return getMessage(null as Player?, key)
    }

    /**
     * Map形式のプレースホルダーを使用してメッセージを取得する
     */
    fun getMessage(player: Player?, key: String, placeholders: Map<String, Any>): String {
        val lang = getLangName(player)
        val config = langConfigs[lang] ?: langConfigs["ja_jp"] ?: return "§cMISSING_LANG_FILE: $lang"
        var message = config.getString(key) ?: config.getDefaults()?.getString(key) ?: return "§cMISSING_LANG: $key"

        placeholders.forEach { (key, value) ->
            message = message.replace("{$key}", value.toString())
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
        val config = langConfigs[lang] ?: langConfigs["ja_jp"] ?: return false
        return config.contains(key) || (config.getDefaults()?.contains(key) ?: false)
    }

    /**
     * メッセージを厳格に取得する（存在しない場合はnullを返す）
     */
    fun getMessageStrict(player: Player?, key: String): String? {
        val lang = getLangName(player)
        if (!hasKey(lang, key)) return null
        val config = langConfigs[lang] ?: langConfigs["ja_jp"] ?: return null
        return config.getString(key) ?: config.getDefaults()?.getString(key)
    }

    /**
     * リスト形式のメッセージを厳格に取得する（存在しない場合はnullを返す）
     */
    fun getMessageListStrict(player: Player?, key: String): List<String>? {
        val lang = getLangName(player)
        if (!hasKey(lang, key)) return null
        return getMessageListDraft(lang, key)
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
        val config = langConfigs[lang] ?: langConfigs["ja_jp"] ?: return listOf("§cMISSING_LANG_FILE: $lang")
        val list = config.getStringList(key)
        val resultList = if (list.isNotEmpty()) list else {
            val defaultList = config.getDefaults()?.getStringList(key)
            if (defaultList != null && defaultList.isNotEmpty()) defaultList else listOf(config.getString(key) ?: "§cMISSING_LANG: $key")
        }

        return resultList
    }

    /**
     * Map形式のプレースホルダーを使用してリスト形式のメッセージを取得する
     */
    fun getMessageList(player: Player?, key: String, placeholders: Map<String, Any>): List<String> {
        val lang = getLangName(player)
        val config = langConfigs[lang] ?: langConfigs["ja_jp"] ?: return listOf("§cMISSING_LANG_FILE: $lang")
        val list = config.getStringList(key)
        val resultList = if (list.isNotEmpty()) list else {
            val defaultList = config.getDefaults()?.getStringList(key)
            if (defaultList != null && defaultList.isNotEmpty()) defaultList else listOf(config.getString(key) ?: "§cMISSING_LANG: $key")
        }

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
