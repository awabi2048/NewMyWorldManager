package me.awabi2048.myworldmanager.service

import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import me.awabi2048.myworldmanager.api.service.ApiMacroService
import java.io.File

/**
 * 外部設定ファイルに基づき、特定イベント発生時にコンソールコマンドを実行するマネージャーです。
 *
 * 遅延はこのクラスで解釈せず、設定された `delay <時間> <コマンド>` を
 * CC-Systemへ渡します。遅延スケジューリングの責務をCC-Systemへ集約するためです。
 */
class MacroManager(
    private val plugin: JavaPlugin,
    private val file: File = File(plugin.dataFolder, "macro.yml"),
) : ApiMacroService {
    private var config: YamlConfiguration = YamlConfiguration()

    init {
        loadConfig()
    }

    /**
     * 設定ファイルを読み込む
     */
    fun loadConfig() {
        if (!file.exists()) {
            plugin.saveResource("macro.yml", false)
        }
        config = YamlConfiguration.loadConfiguration(file)
    }

    /**
     * 指定されたトリガーのマクロを実行します。
     * `macros.<trigger>` の文字列リストを読み込み、プレースホルダーを置換して
     * コンソールから実行します。設定が種類別セクションへ拡張されても、
     * 呼出側が渡すトリガー単位の引数だけを扱う境界は変えません。
     */
    override fun execute(trigger: String, params: Map<String, String>) {
        val macros = config.getStringList("macros.$trigger")
        if (macros.isEmpty()) return

        for (macro in macros) {
            var command = macro.trim().removePrefix("/")
            // プレースホルダーの置換
            params.forEach { (key, value) ->
                command = command.replace("%$key%", value)
            }
            if (command.isNotBlank()) dispatch(command)
        }
    }

    private fun dispatch(command: String) {
        if (Bukkit.isPrimaryThread()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
        } else {
            Bukkit.getScheduler().runTask(plugin, Runnable {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
            })
        }
    }
}
