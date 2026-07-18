package me.awabi2048.myworldmanager.service

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.migration.WorldDirectoryResolver
import me.awabi2048.myworldmanager.migration.WorldDirectoryState
import me.awabi2048.myworldmanager.repository.TemplateRepository
import me.awabi2048.myworldmanager.repository.WorldConfigRepository
import java.io.File

class DirectoryManager(
    private val plugin: MyWorldManager,
    private val worldConfigRepository: WorldConfigRepository,
    private val templateRepository: TemplateRepository
) {
    /**
     * 各マイワールドおよびテンプレートのフォルダが実際に存在するかチェックする
     */
    fun checkDirectories() {
        val allWorlds = worldConfigRepository.findAll()
        val missingWorlds = mutableListOf<String>()
        val conflictingWorlds = mutableListOf<String>()
        val archiveFolder = File(plugin.dataFolder.parentFile.parentFile, "archived_worlds")
        
        for (world in allWorlds) {
            val folderName = world.customWorldName ?: "my_world.${world.uuid}"
            val resolution = if (world.isArchived) null else plugin.worldDirectoryResolver.inspect(folderName)
            if (resolution?.state == WorldDirectoryState.CONFLICT) {
                conflictingWorlds.add(
                    "「${world.name}」 (UUID: ${world.uuid}, Legacy: ${resolution.legacyPath}, Current: ${resolution.currentPath})"
                )
            }
            val folder = if (world.isArchived) File(archiveFolder, folderName) else resolution?.existingPath?.toFile()

            if (resolution?.state != WorldDirectoryState.CONFLICT &&
                (folder == null || !folder.exists() || !folder.isDirectory)
            ) {
                missingWorlds.add("「${world.name}」 (UUID: ${world.uuid}, Archived: ${world.isArchived})")
            }
        }
        
        val missingTemplates = templateRepository.missingTemplates
        
        if (missingWorlds.isNotEmpty() || conflictingWorlds.isNotEmpty() || missingTemplates.isNotEmpty()) {
            plugin.logger.severe("====================================================")
            plugin.logger.severe("【MyWorldManager 警告】")
            
            if (missingWorlds.isNotEmpty()) {
                plugin.logger.severe("以下のマイワールドに対応するディレクトリが見つかりませんでした:")
                missingWorlds.forEach { plugin.logger.severe(" - $it") }
            }

            if (conflictingWorlds.isNotEmpty()) {
                plugin.logger.severe("以下のマイワールドは旧構造と現行構造が衝突しています（自動選択しません）:")
                conflictingWorlds.forEach { plugin.logger.severe(" - $it") }
            }
            
            if (missingTemplates.isNotEmpty()) {
                plugin.logger.severe("templates.ymlに記述された次のワールドが見つかりませんでした:")
                missingTemplates.forEach { plugin.logger.severe(" - $it") }
                plugin.logger.severe("")
                plugin.logger.severe("mwm complete_templatesを実行して、該当するテンプレートに対応するように")
                plugin.logger.severe("ランダムなワールドを生成することができます。")
            }
            
            plugin.logger.severe("====================================================")
        }
    }

}
