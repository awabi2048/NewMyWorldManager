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

        var accumulatedDelay = 0L
        val delayRegex = Regex("""^\[delay:\s*(\d+)([ts]?)\]\s*(.*)$""")

        for (macro in macros) {
            var rawCommand = macro
            var delayTicks = 0L

            // 遅延の解析
            val match = delayRegex.find(rawCommand)
            if (match != null) {
                val value = match.groupValues[1].toLong()
                val unit = match.groupValues[2]
                val commandPart = match.groupValues[3]

                delayTicks = if (unit == "s") value * 20 else value
                rawCommand = commandPart
            }

            accumulatedDelay += delayTicks

            var command = rawCommand
            // プレースホルダーの置換
            params.forEach { (key, value) ->
                command = command.replace("%$key%", value)
            }

            if (accumulatedDelay == 0L) {
                // 即時実行
                dispatch(command)
            } else {
                // 遅延実行
                Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                    dispatch(command)
                }, accumulatedDelay)
            }
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
