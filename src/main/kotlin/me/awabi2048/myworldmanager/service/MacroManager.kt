package me.awabi2048.myworldmanager.service

import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/**
 * 外部設定ファイルに基づき、特定イベント発生時にコンソールコマンドを実行するマネージャー
 */
class MacroManager(private val plugin: JavaPlugin) {
    private val file = File(plugin.dataFolder, "macro.yml")
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
     * 指定されたトリガーのマクロを実行する
     */
    fun execute(trigger: String, params: Map<String, String>) {
        val macros = config.getStringList("macros.$trigger")
        if (macros.isEmpty()) return

        for (macro in macros) {
            var command = macro
            // プレースホルダーの置換
            params.forEach { (key, value) ->
                command = command.replace("%$key%", value)
            }
            
            // コンソールチャットから実行（非同期の場合はメインスレッドへ）
            if (Bukkit.isPrimaryThread()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
            } else {
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
                })
            }
        }
    }
}
