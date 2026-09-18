package me.awabi2048.myworldmanager.service

import java.io.File
import me.awabi2048.myworldmanager.api.service.ApiMacroService
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin

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
    data class Registration(
        val trigger: String,
        val placeholders: List<String>,
        val commands: List<String>,
    )

    private var config: YamlConfiguration = YamlConfiguration()

    init {
        loadConfig()
    }

    /** 設定ファイルを読み込みます。 */
    fun loadConfig() {
        if (!file.exists()) {
            plugin.saveResource("macro.yml", false)
        }
        config = YamlConfiguration.loadConfiguration(file)
    }

    /**
     * 指定されたトリガーのマクロを実行します。
     * `macros.<trigger>` の文字列リストを読み込み、プレースホルダーを置換して
     * コンソールから実行します。
     */
    override fun execute(trigger: String, params: Map<String, String>) {
        val macros = config.getStringList("macros.$trigger")
        if (macros.isEmpty()) return

        for (macro in macros) {
            var command = macro.trim().removePrefix("/")
            params.forEach { (key, value) ->
                command = command.replace("%$key%", value)
            }
            if (command.isNotBlank()) dispatch(command)
        }
    }

    /**
     * 管理コマンド向けに、MWMが保証するトリガー契約と現在の登録内容を返します。
     * プレースホルダー一覧は実際の呼出側が常に渡す値だけを定義します。
     */
    fun registrations(): List<Registration> =
        TRIGGER_PLACEHOLDERS.map { (trigger, placeholders) ->
            Registration(
                trigger = trigger,
                placeholders = placeholders,
                commands = config.getStringList("macros.$trigger"),
            )
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

    companion object {
        private val TRIGGER_PLACEHOLDERS = linkedMapOf(
            "on_world_create" to listOf("owner", "world_uuid", "world_name", "template_name"),
            "on_owner_transfer" to listOf("old_owner", "new_owner", "world_uuid"),
            "on_world_warp" to listOf("player", "world_uuid"),
            "on_member_add" to listOf("member", "world_uuid"),
            "on_member_remove" to listOf("member", "world_uuid"),
            // 現行の削除経路は owner をマクロへ渡していません。
            "on_world_delete" to listOf("world_uuid"),
        )
    }
}
