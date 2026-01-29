package me.awabi2048.myworldmanager.service

import me.awabi2048.myworldmanager.MyWorldManager
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
        val archiveFolder = File(plugin.dataFolder.parentFile.parentFile, "archived_worlds")
        
        for (world in allWorlds) {
            val folderName = world.customWorldName ?: "my_world.${world.uuid}"
            val folder = if (world.isArchived) {
                File(archiveFolder, folderName)
            } else {
                File(plugin.server.worldContainer, folderName)
            }
            
            if (!folder.exists() || !folder.isDirectory) {
                missingWorlds.add("「${world.name}」 (UUID: ${world.uuid}, Archived: ${world.isArchived})")
            }
        }
        
        val missingTemplates = templateRepository.missingTemplates
        
        if (missingWorlds.isNotEmpty() || missingTemplates.isNotEmpty()) {
            plugin.logger.severe("====================================================")
            plugin.logger.severe("【MyWorldManager 警告】")
            
            if (missingWorlds.isNotEmpty()) {
                plugin.logger.severe("以下のマイワールドに対応するディレクトリが見つかりませんでした:")
                missingWorlds.forEach { plugin.logger.severe(" - $it") }
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
