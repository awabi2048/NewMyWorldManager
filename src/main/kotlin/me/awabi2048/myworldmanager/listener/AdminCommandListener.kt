package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.service.WorldService
import me.awabi2048.myworldmanager.session.SettingsAction
import me.awabi2048.myworldmanager.util.ItemTag
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

class AdminCommandListener : Listener {

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
        
        // セッションチェック
        if (!plugin.settingsSessionManager.hasSession(player)) return
        val session = plugin.settingsSessionManager.getSession(player) ?: return

        // アクションに応じた処理
        if (session.action == SettingsAction.ADMIN_MENU || session.action == SettingsAction.ADMIN_PORTAL_GUI || session.action == SettingsAction.ADMIN_WORLD_GUI) {
            handleAdminMenuClick(event, player, plugin, session.action)
        } else if (isAdminConfirmAction(session.action)) {
            handleAdminConfirmClick(event, player, plugin, session.action)
        }
    }

    private fun isAdminConfirmAction(action: SettingsAction): Boolean {
        return when (action) {
            SettingsAction.ADMIN_CONVERT_NORMAL_CONFIRM,
            SettingsAction.ADMIN_CONVERT_ADMIN_CONFIRM,
            SettingsAction.ADMIN_EXPORT_CONFIRM,
            SettingsAction.ADMIN_ARCHIVE_ALL_CONFIRM,
            SettingsAction.ADMIN_UPDATE_DATA_CONFIRM,
            SettingsAction.ADMIN_UNLINK_CONFIRM,
            SettingsAction.ADMIN_REPAIR_TEMPLATES_CONFIRM -> true
            else -> false
        }
    }

    private fun handleAdminMenuClick(event: InventoryClickEvent, player: Player, plugin: MyWorldManager, action: SettingsAction) {
        event.isCancelled = true
        if (event.clickedInventory != event.view.topInventory) return
        val item = event.currentItem ?: return

        val tagType = ItemTag.getType(item) ?: return

        when (tagType) {
            ItemTag.TYPE_GUI_ADMIN_UPDATE_DATA -> {
                plugin.soundManager.playAdminClickSound(player)
                plugin.adminCommandGui.openUpdateDataConfirmation(player)
            }
            ItemTag.TYPE_GUI_ADMIN_REPAIR_TEMPLATES -> {
                plugin.soundManager.playAdminClickSound(player)
                plugin.adminCommandGui.openRepairTemplatesConfirmation(player)
            }
            ItemTag.TYPE_GUI_ADMIN_CREATE_TEMPLATE -> {
                plugin.soundManager.playAdminClickSound(player)
                plugin.templateWizardGui.open(player)
            }
            ItemTag.TYPE_GUI_ADMIN_ARCHIVE_ALL -> {
                plugin.soundManager.playAdminClickSound(player)
                plugin.adminCommandGui.openArchiveAllConfirmation(player)
            }
            ItemTag.TYPE_GUI_ADMIN_CONVERT -> {
                plugin.soundManager.playAdminClickSound(player)
                if (event.isLeftClick) {
                    plugin.adminCommandGui.openConvertConfirmation(player, WorldService.ConversionMode.NORMAL)
                } else if (event.isRightClick) {
                    plugin.adminCommandGui.openConvertConfirmation(player, WorldService.ConversionMode.ADMIN)
                }
            }
            ItemTag.TYPE_GUI_ADMIN_UNLINK -> {
                plugin.soundManager.playAdminClickSound(player)
                plugin.adminCommandGui.openUnlinkConfirmation(player)
            }
            ItemTag.TYPE_GUI_ADMIN_EXPORT -> {
                plugin.soundManager.playAdminClickSound(player)
                plugin.adminCommandGui.openExportConfirmation(player, player.world.name)
            }
            ItemTag.TYPE_GUI_ADMIN_INFO -> {
                plugin.soundManager.playAdminClickSound(player)
                plugin.worldGui.open(player, fromAdminMenu = true)
            }
            ItemTag.TYPE_GUI_ADMIN_PORTALS -> {
                plugin.soundManager.playAdminClickSound(player)
                plugin.adminPortalGui.open(player, fromAdminMenu = true)
            }
            ItemTag.TYPE_GUI_RETURN -> {
                plugin.soundManager.playAdminClickSound(player)
                // Return from sub-menus to the main admin menu
                if (action == SettingsAction.ADMIN_PORTAL_GUI || action == SettingsAction.ADMIN_WORLD_GUI) {
                    plugin.adminCommandGui.open(player)
                }
            }
        }
    }

    private fun handleAdminConfirmClick(event: InventoryClickEvent, player: Player, plugin: MyWorldManager, action: SettingsAction) {
        event.isCancelled = true
        if (event.clickedInventory != event.view.topInventory) return
        val item = event.currentItem ?: return
        val tagType = ItemTag.getType(item) ?: return

        if (tagType == ItemTag.TYPE_GUI_CANCEL) {
            plugin.soundManager.playAdminClickSound(player)
            plugin.adminCommandGui.open(player)
            return
        }

        if (tagType == ItemTag.TYPE_GUI_CONFIRM) {
            plugin.soundManager.playAdminClickSound(player)
            player.closeInventory()
            
            // アクション実行
            when (action) {
                SettingsAction.ADMIN_UPDATE_DATA_CONFIRM -> performUpdateData(player, plugin)
                SettingsAction.ADMIN_REPAIR_TEMPLATES_CONFIRM -> performRepairTemplates(player, plugin)
                SettingsAction.ADMIN_ARCHIVE_ALL_CONFIRM -> performArchiveAll(player, plugin)
                SettingsAction.ADMIN_CONVERT_NORMAL_CONFIRM -> performConvert(player, plugin, WorldService.ConversionMode.NORMAL)
                SettingsAction.ADMIN_CONVERT_ADMIN_CONFIRM -> performConvert(player, plugin, WorldService.ConversionMode.ADMIN)
                SettingsAction.ADMIN_EXPORT_CONFIRM -> performExport(player, plugin)
                SettingsAction.ADMIN_UNLINK_CONFIRM -> performUnlink(player, plugin)
                else -> {}
            }
            // セッション終了（必要なら）またはメニューに戻る?
            // 処理完了後にどうするかは各メソッド次第だが、基本はチャット通知して終了
            plugin.settingsSessionManager.endSession(player)
        }
    }

    private fun performUnlink(player: Player, plugin: MyWorldManager) {
        val currentWorld = player.world
        val worldData = plugin.worldConfigRepository.findByWorldName(currentWorld.name)
        val uuid = worldData?.uuid

        if (uuid == null) {
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.unlink_not_myworld"))
            return
        }

        plugin.worldConfigRepository.delete(uuid)
        player.sendMessage(plugin.languageManager.getMessage(player, "messages.unlink_success"))
    }

    private fun performUpdateData(player: Player, plugin: MyWorldManager) {
        player.sendMessage(plugin.languageManager.getMessage(player, "messages.admin_update_data_start"))
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            plugin.worldConfigRepository.loadAll()
            val worlds = plugin.worldConfigRepository.findAll()
            worlds.forEach { world ->
                // 権限データの重複削除などのクリーンアップ
                world.moderators.remove(world.owner)
                world.members.remove(world.owner)
                world.members.removeAll(world.moderators)
                
                val duplicateInModerators = world.moderators.distinct()
                world.moderators.clear()
                world.moderators.addAll(duplicateInModerators)

                val duplicateInMembers = world.members.distinct()
                world.members.clear()
                world.members.addAll(duplicateInMembers)
                
                // ポイント補完
                if (world.cumulativePoints <= 0) {
                     val worldConfig = plugin.config
                     var estimatedPoints = worldConfig.getInt("creation_cost.template", 0)
                     for (i in 1..world.borderExpansionLevel) {
                         estimatedPoints += worldConfig.getInt("expansion.costs.$i", 100)
                     }
                     world.cumulativePoints = estimatedPoints
                }
                plugin.worldConfigRepository.save(world)
            }
            
            // プレイヤーデータの更新
            val count = plugin.playerStatsRepository.updateAllData()
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.data_update_success", mapOf("world_count" to worlds.size, "player_count" to count)))
        })
    }

    private fun performRepairTemplates(player: Player, plugin: MyWorldManager) {
        val repo = plugin.templateRepository
        val missing = repo.missingTemplates
        if (missing.isEmpty()) {
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.admin_repair_template_none_missing"))
            return
        }

        player.sendMessage(plugin.languageManager.getMessage(player, "messages.admin_repair_template_start", mapOf("count" to missing.size)))
        val config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(java.io.File(plugin.dataFolder, "templates.yml"))
        
        missing.toList().forEach { key ->
            val path = config.getString("$key.path")
            if (path != null) {
                player.sendMessage(plugin.languageManager.getMessage(player, "messages.admin_repair_template_progress", mapOf("template" to key, "path" to path)))
                val creator = org.bukkit.WorldCreator(path)
                val world = Bukkit.createWorld(creator)
                if (world != null) {
                    player.sendMessage(plugin.languageManager.getMessage(player, "messages.admin_repair_template_success", mapOf("template" to key)))
                } else {
                    player.sendMessage(plugin.languageManager.getMessage(player, "messages.admin_repair_template_failed", mapOf("template" to key)))
                }
            }
        }
        plugin.templateRepository.loadTemplates()
        player.sendMessage(plugin.languageManager.getMessage(player, "messages.admin_repair_template_complete"))
    }
    
    // 再帰的にアーカイブ処理を行うヘルパー
    private fun processArchiveQueue(player: Player, plugin: MyWorldManager, targets: List<me.awabi2048.myworldmanager.model.WorldData>, index: Int) {
        if (index >= targets.size) {
            player.sendMessage(plugin.languageManager.getMessage("messages.migration_archive_complete", mapOf("count" to targets.size)))
            return
        }
        
        val worldData = targets[index]
        plugin.worldService.archiveWorld(worldData.uuid).thenAccept { success ->
            if (success) {
                player.sendMessage(plugin.languageManager.getMessage("messages.migration_archive_progress", mapOf("current" to (index + 1), "total" to targets.size, "world" to worldData.name)))
            }
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                processArchiveQueue(player, plugin, targets, index + 1)
            }, 20L)
        }
    }

    private fun performArchiveAll(player: Player, plugin: MyWorldManager) {
        val today = java.time.LocalDate.now()
        val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val archiveTargets = plugin.worldConfigRepository.findAll().filter { worldData ->
            !worldData.isArchived && try {
                java.time.LocalDate.parse(worldData.expireDate, dateFormatter) < today
            } catch (e: Exception) { false }
        }

        if (archiveTargets.isEmpty()) {
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.admin_archive_none_found"))
            return
        }

        val estSeconds = archiveTargets.size * 5
        player.sendMessage(plugin.languageManager.getMessage("messages.migration_archive_start", mapOf("count" to archiveTargets.size, "seconds" to estSeconds)))
        processArchiveQueue(player, plugin, archiveTargets, 0)
    }

    private fun performConvert(player: Player, plugin: MyWorldManager, mode: WorldService.ConversionMode) {
        val currentWorld = player.world
        val worldName = currentWorld.name
        val alreadyRegistered = plugin.worldConfigRepository.findByWorldName(worldName) != null

        if (alreadyRegistered) {
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.admin_convert_already_registered"))
            return
        }

        player.sendMessage(plugin.languageManager.getMessage(player, "messages.admin_convert_start"))
        plugin.worldService.convertWorld(currentWorld, player.uniqueId, mode).thenAccept { uuid ->
            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (uuid != null) {
                    player.sendMessage(plugin.languageManager.getMessage(player, "messages.admin_convert_success", mapOf("world" to worldName, "uuid" to uuid)))
                    if (mode == WorldService.ConversionMode.NORMAL) {
                        player.sendMessage(plugin.languageManager.getMessage(player, "messages.admin_convert_success_normal"))
                    } else {
                        player.sendMessage(plugin.languageManager.getMessage(player, "messages.admin_convert_success_admin"))
                    }
                } else {
                    player.sendMessage(plugin.languageManager.getMessage(player, "messages.admin_convert_failed"))
                }
            })
        }
    }

    private fun performExport(player: Player, plugin: MyWorldManager) {
        val currentWorld = player.world
        // 現在のワールドがMyWorldかチェック
        val worldData = plugin.worldConfigRepository.findByWorldName(currentWorld.name)
        val uuid = worldData?.uuid

        if (uuid == null) {
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.admin_export_not_myworld"))
            return
        }

        player.sendMessage(plugin.languageManager.getMessage(player, "messages.export_started"))
        plugin.worldService.exportWorld(uuid).thenAccept { file ->
            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (file != null) {
                    player.sendMessage(plugin.languageManager.getMessage(player, "messages.export_success", mapOf("file" to file.name)))
                } else {
                    player.sendMessage(plugin.languageManager.getMessage(player, "messages.export_failed"))
                }
            })
        }
    }
}
