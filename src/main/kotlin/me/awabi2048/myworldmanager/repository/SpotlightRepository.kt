package me.awabi2048.myworldmanager.repository

import me.awabi2048.myworldmanager.MyWorldManager
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.*

/**
 * SPOTLIGHT（おすすめワールド）のUUIDを管理するリポジトリ
 */
class SpotlightRepository(private val plugin: MyWorldManager) {
    private val file = File(plugin.dataFolder, "spotlight.yml")
    private var spotlightUuids = mutableListOf<UUID>()
    private val limit = 10

    /**
     * spotlight.ymlからデータを読み込む
     */
    fun load() {
        if (!file.exists()) {
            // plugin.saveResource("spotlight.yml", false) // resourcesにないので手動で作成
            file.createNewFile()
            val config = YamlConfiguration.loadConfiguration(file)
            config.set("worlds", listOf<String>())
            config.save(file)
        }
        val config = YamlConfiguration.loadConfiguration(file)
        val list = config.getStringList("worlds")
        val loadedUuids = list.mapNotNull {
            try { UUID.fromString(it) } catch (e: Exception) { null }
        }

        // 存在しないワールドのUUIDを除去する（自動クリーンアップ）
        val existingUuids = loadedUuids.filter { uuid ->
            plugin.worldConfigRepository.findByUuid(uuid) != null
        }

        spotlightUuids = existingUuids.toMutableList()

        // 変更があった場合は保存する
        if (loadedUuids.size != existingUuids.size) {
            plugin.logger.info("[Spotlight] 存在しないワールドのUUIDを ${loadedUuids.size - existingUuids.size} 件削除しました。")
            save()
        }
    }

    /**
     * spotlight.ymlにデータを保存する
     */
    private fun save() {
        val config = YamlConfiguration()
        config.set("worlds", spotlightUuids.map { it.toString() })
        config.save(file)
    }

    /**
     * 全ての登録済みUUIDを取得する
     */
    fun findAll(): List<UUID> = spotlightUuids

    /**
     * 指定されたUUIDが登録されているかチェックする
     */
    fun isSpotlight(uuid: UUID): Boolean = spotlightUuids.contains(uuid)

    /**
     * 新しいUUIDを登録する
     * @return 登録に成功したか（上限に達している場合はfalse）
     */
    fun add(uuid: UUID): Boolean {
        if (spotlightUuids.size >= limit) return false
        if (!spotlightUuids.contains(uuid)) {
            spotlightUuids.add(uuid)
            save()
        }
        return true
    }

    /**
     * 登録解除する
     */
    fun remove(uuid: UUID) {
        if (spotlightUuids.remove(uuid)) {
            save()
        }
    }
}
