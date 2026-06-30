package me.awabi2048.myworldmanager.repository

import me.awabi2048.myworldmanager.MyWorldManager
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

/**
 * 実行時に変化するステート（データ的性質のもの）を config.yml から分離して保持するリポジトリ。
 *
 * 従来 config.yml に同居していた動的ステートは、`/mwm reload` で意図せずリセットされるリスクがあった。
 * 読み取り専用の設定ファイル（config.yml）と動的ステート（runtime_state.yml）を明確に分離する。
 *
 * 現在の管轄:
 * - `creation.enabled`: ワールド作成の受付可否（運営トグル）
 */
class RuntimeStateRepository(private val plugin: MyWorldManager) {
    private val file = File(plugin.dataFolder, "runtime_state.yml")

    private var worldCreationEnabled: Boolean = true

    init {
        load()
    }

    /**
     * runtime_state.yml からデータを読み込む。
     * ファイルが存在しない場合はデフォルト値（作成有効）で新規作成する。
     */
    fun load() {
        if (!file.exists()) {
            file.createNewFile()
            val config = YamlConfiguration()
            config.set("creation.enabled", true)
            config.save(file)
        }
        val config = YamlConfiguration.loadConfiguration(file)
        // 旧 config.yml の creation.enabled からの移行: runtime_state.yml 側に値が無ければ
        // 初期値 true を前提とする（実サーバーの live 値と整合）。
        worldCreationEnabled = config.getBoolean("creation.enabled", true)
    }

    private fun save() {
        val config = YamlConfiguration()
        config.set("creation.enabled", worldCreationEnabled)
        config.save(file)
    }

    fun isWorldCreationEnabled(): Boolean = worldCreationEnabled

    fun setWorldCreationEnabled(enabled: Boolean) {
        if (worldCreationEnabled == enabled) return
        worldCreationEnabled = enabled
        save()
    }
}
