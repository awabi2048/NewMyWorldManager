package me.awabi2048.myworldmanager.listener

import java.util.UUID
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.gui.DialogConfirmManager
import me.awabi2048.myworldmanager.service.WorldService
import me.awabi2048.myworldmanager.session.SettingsAction
import me.awabi2048.myworldmanager.util.ItemTag
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import io.papermc.paper.event.player.PlayerCustomClickEvent
import org.bukkit.plugin.java.JavaPlugin

class AdminCommandListener : Listener {

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST, ignoreCancelled = false)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)

        // セッションチェック
        if (!plugin.settingsSessionManager.hasSession(player)) return
        val session = plugin.settingsSessionManager.getSession(player) ?: return

        // GUI遷移中のクリックを無視
        if (session.isGuiTransition) {
            // player.sendMessage("§7[Debug] Click cancelled (GuiTransition: true)")
            event.isCancelled = true
            return
        }

        // アクションに応じた処理
        if (session.action == SettingsAction.ADMIN_MENU ||
                        session.action == SettingsAction.ADMIN_PORTAL_GUI ||
                        session.action == SettingsAction.ADMIN_WORLD_GUI
        ) {
            handleAdminMenuClick(event, player, plugin, session.action)
        } else if (isAdminConfirmAction(session.action)) {
            handleAdminConfirmClick(event, player, plugin, session.action)
        }
    }

    @EventHandler
    fun onCustomClick(event: PlayerCustomClickEvent) {
        val conn = event.commonConnection as? io.papermc.paper.connection.PlayerGameConnection ?: return
        val player = conn.player
        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
        val session = plugin.settingsSessionManager.getSession(player) ?: return
        val identifier = event.identifier.asString()

        if (identifier == "mwm:confirm/cancel") {
            if (!isAdminConfirmAction(session.action)) return
            DialogConfirmManager.safeCloseDialog(player)
            plugin.soundManager.playAdminClickSound(player)
            plugin.adminCommandGui.open(player)
            return
        }

        if (!identifier.startsWith("mwm:confirm/admin/")) return
        if (!isAdminConfirmAction(session.action)) return

        DialogConfirmManager.safeCloseDialog(player)
        plugin.soundManager.playAdminClickSound(player)

        when (session.action) {
            SettingsAction.ADMIN_UPDATE_DATA_CONFIRM -> performUpdateData(player, plugin)
            SettingsAction.ADMIN_REPAIR_TEMPLATES_CONFIRM -> performRepairTemplates(player, plugin)
            SettingsAction.ADMIN_ARCHIVE_ALL_CONFIRM -> performArchiveAll(player, plugin)
            SettingsAction.ADMIN_CONVERT_NORMAL_CONFIRM -> performConvert(player, plugin, WorldService.ConversionMode.NORMAL)
            SettingsAction.ADMIN_CONVERT_ADMIN_CONFIRM -> performConvert(player, plugin, WorldService.ConversionMode.ADMIN)
            SettingsAction.ADMIN_EXPORT_CONFIRM -> performExport(player, plugin)
            SettingsAction.ADMIN_UNLINK_CONFIRM -> performUnlink(player, plugin)
            SettingsAction.ADMIN_ARCHIVE_WORLD_CONFIRM -> performArchiveWorld(player, plugin)
            SettingsAction.ADMIN_UNARCHIVE_WORLD_CONFIRM -> performUnarchiveWorld(player, plugin)
            else -> return
        }

        plugin.settingsSessionManager.endSession(player)
    }

    private fun isAdminConfirmAction(action: SettingsAction): Boolean {
        return when (action) {
            SettingsAction.ADMIN_CONVERT_NORMAL_CONFIRM,
            SettingsAction.ADMIN_CONVERT_ADMIN_CONFIRM,
            SettingsAction.ADMIN_EXPORT_CONFIRM,
            SettingsAction.ADMIN_ARCHIVE_ALL_CONFIRM,
            SettingsAction.ADMIN_UPDATE_DATA_CONFIRM,
            SettingsAction.ADMIN_UNLINK_CONFIRM,
            SettingsAction.ADMIN_REPAIR_TEMPLATES_CONFIRM,
            SettingsAction.ADMIN_ARCHIVE_WORLD_CONFIRM,
            SettingsAction.ADMIN_UNARCHIVE_WORLD_CONFIRM -> true
            else -> false
        }
    }

    private fun handleAdminMenuClick(
            event: InventoryClickEvent,
            player: Player,
            plugin: MyWorldManager,
            action: SettingsAction
    ) {
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
                    plugin.adminCommandGui.openConvertConfirmation(
                            player,
                            WorldService.ConversionMode.NORMAL
                    )
                } else if (event.isRightClick) {
                    plugin.adminCommandGui.openConvertConfirmation(
                            player,
                            WorldService.ConversionMode.ADMIN
                    )
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
                if (action == SettingsAction.ADMIN_PORTAL_GUI ||
                                action == SettingsAction.ADMIN_WORLD_GUI
                ) {
                    plugin.adminCommandGui.open(player)
                }
            }
        }
    }

    private fun handleAdminConfirmClick(
            event: InventoryClickEvent,
            player: Player,
            plugin: MyWorldManager,
            action: SettingsAction
    ) {
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
                SettingsAction.ADMIN_REPAIR_TEMPLATES_CONFIRM ->
                        performRepairTemplates(player, plugin)
                SettingsAction.ADMIN_ARCHIVE_ALL_CONFIRM -> performArchiveAll(player, plugin)
                SettingsAction.ADMIN_CONVERT_NORMAL_CONFIRM ->
                        performConvert(player, plugin, WorldService.ConversionMode.NORMAL)
                SettingsAction.ADMIN_CONVERT_ADMIN_CONFIRM ->
                        performConvert(player, plugin, WorldService.ConversionMode.ADMIN)
                SettingsAction.ADMIN_EXPORT_CONFIRM -> performExport(player, plugin)
                SettingsAction.ADMIN_UNLINK_CONFIRM -> performUnlink(player, plugin)
                SettingsAction.ADMIN_ARCHIVE_WORLD_CONFIRM -> performArchiveWorld(player, plugin)
                SettingsAction.ADMIN_UNARCHIVE_WORLD_CONFIRM ->
                        performUnarchiveWorld(player, plugin)
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
            player.sendMessage(
                    plugin.languageManager.getMessage(player, "error.unlink_not_myworld")
            )
            return
        }

        plugin.worldConfigRepository.delete(uuid)
        player.sendMessage(plugin.languageManager.getMessage(player, "messages.unlink_success"))
    }

    private fun performUpdateData(player: Player, plugin: MyWorldManager) {
        player.sendMessage("§eUpdating player data...")
        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        Runnable {
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
                                    var estimatedPoints =
                                            worldConfig.getInt("creation_cost.template", 0)
                                    for (i in 1..world.borderExpansionLevel) {
                                        estimatedPoints +=
                                                worldConfig.getInt("expansion.costs.$i", 100)
                                    }
                                    world.cumulativePoints = estimatedPoints
                                }
                                plugin.worldConfigRepository.save(world)
                            }

                            // プレイヤーデータの更新
                            val count = plugin.playerStatsRepository.updateAllData()
                            player.sendMessage(
                                    plugin.languageManager.getMessage(
                                            player,
                                            "messages.data_update_success",
                                            mapOf(
                                                    "world_count" to worlds.size,
                                                    "player_count" to count
                                            )
                                    )
                            )
                        }
                )
    }

    private fun performRepairTemplates(player: Player, plugin: MyWorldManager) {
        val repo = plugin.templateRepository
        val missing = repo.missingTemplates
        if (missing.isEmpty()) {
            player.sendMessage("§a欠損しているテンプレートディレクトリはありません。")
            return
        }

        player.sendMessage("§e欠損しているテンプレートの修復を開始します (${missing.size}件)...")
        val config =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                        java.io.File(plugin.dataFolder, "templates.yml")
                )

        missing.toList().forEach { key ->
            val path = config.getString("$key.path")
            if (path != null) {
                player.sendMessage("§7- $key を生成中... ($path)")
                val creator = org.bukkit.WorldCreator(path)
                val world = Bukkit.createWorld(creator)
                if (world != null) {
                    player.sendMessage("§a  -> $key の生成に成功しました。")
                } else {
                    player.sendMessage("§c  -> $key の生成に失敗しました。")
                }
            }
        }
        plugin.templateRepository.loadTemplates()
        player.sendMessage("§a修復処理が完了しました。")
    }

    // 再帰的にアーカイブ処理を行うヘルパー
    private fun processArchiveQueue(
            player: Player,
            plugin: MyWorldManager,
            targets: List<me.awabi2048.myworldmanager.model.WorldData>,
            index: Int
    ) {
        if (index >= targets.size) {
            player.sendMessage(
                    plugin.languageManager.getMessage(
                            "messages.migration_archive_complete",
                            mapOf("count" to targets.size)
                    )
            )
            return
        }

        val worldData = targets[index]
        plugin.worldService.archiveWorld(worldData.uuid).thenAccept { success: Boolean ->
            if (success) {
                player.sendMessage(
                        plugin.languageManager.getMessage(
                                "messages.migration_archive_progress",
                                mapOf(
                                        "current" to (index + 1),
                                        "total" to targets.size,
                                        "world" to worldData.name
                                )
                        )
                )
            }
            Bukkit.getScheduler()
                    .runTaskLater(
                            plugin,
                            Runnable { processArchiveQueue(player, plugin, targets, index + 1) },
                            20L
                    )
        }
    }

    private fun performArchiveAll(player: Player, plugin: MyWorldManager) {
        player.sendMessage("§eデイリーメンテナンス（期限切れアーカイブ処理）を開始します...")
        val results = plugin.worldService.updateDailyData()
        val updatedCount = results["updated"] ?: 0
        val archivedCount = results["archived"] ?: 0
        
        player.sendMessage(
                plugin.languageManager.getMessage(
                        player,
                        "messages.daily_update_success",
                        mapOf("updated" to updatedCount, "archived" to archivedCount)
                )
        )
    }

    private fun performConvert(
            player: Player,
            plugin: MyWorldManager,
            mode: WorldService.ConversionMode
    ) {
        val currentWorld = player.world
        val worldName = currentWorld.name
        val alreadyRegistered = plugin.worldConfigRepository.findByWorldName(worldName) != null

        if (alreadyRegistered) {
            player.sendMessage("§cこのワールドは既にMyWorldとして登録されています。")
            return
        }

        player.sendMessage("§eワールドの変換を開始します。しばらくお待ちください...")
        plugin.worldService.convertWorld(currentWorld, player.uniqueId, mode).thenAccept {
                uuid: java.util.UUID? ->
            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            Runnable {
                                if (uuid != null) {
                                    player.sendMessage(
                                            "§a現在のワールド '$worldName' をMyWorldとして登録しました。(UUID: $uuid)"
                                    )
                                    if (mode == WorldService.ConversionMode.NORMAL) {
                                        player.sendMessage(
                                                "§aディレクトリが標準形式にリネームされ、通常のマイワールド管理が適用されます。"
                                        )
                                    } else {
                                        player.sendMessage("§a設定ファイルのみ生成されました。管理用ワールドとして扱われます。")
                                    }
                                } else {
                                    player.sendMessage("§cワールドの変換に失敗しました。")
                                }
                            }
                    )
        }
    }

    private fun performExport(player: Player, plugin: MyWorldManager) {
        val currentWorld = player.world
        // 現在のワールドがMyWorldかチェック
        val worldData = plugin.worldConfigRepository.findByWorldName(currentWorld.name)
        val uuid = worldData?.uuid

        if (uuid == null) {
            player.sendMessage("§c現在のワールドはMyWorld管理下のワールドではありません。")
            return
        }

        player.sendMessage(plugin.languageManager.getMessage(player, "messages.export_started"))
        plugin.worldService.exportWorld(uuid).thenAccept { file: java.io.File? ->
            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            Runnable {
                                if (file != null) {
                                    player.sendMessage(
                                            plugin.languageManager.getMessage(
                                                    player,
                                                    "messages.export_success",
                                                    mapOf("file" to file.name)
                                            )
                                    )
                                } else {
                                    player.sendMessage(
                                            plugin.languageManager.getMessage(
                                                    player,
                                                    "messages.export_failed"
                                            )
                                    )
                                }
                            }
                    )
        }
    }

    private fun performArchiveWorld(player: Player, plugin: MyWorldManager) {
        val session = plugin.settingsSessionManager.getSession(player) ?: return
        val uuid = session.worldUuid
        val worldData = plugin.worldConfigRepository.findByUuid(uuid) ?: return

        player.sendMessage(plugin.languageManager.getMessage(player, "messages.archive_start"))
        plugin.worldService.archiveWorld(uuid).thenAccept { success: Boolean ->
            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            Runnable {
                                if (success) {
                                    player.sendMessage(
                                            plugin.languageManager.getMessage(
                                                    player,
                                                    "messages.archive_success",
                                                    mapOf("world" to worldData.name)
                                            )
                                    )
                                    plugin.worldGui.open(
                                            player,
                                            fromAdminMenu = true,
                                            suppressSound = true
                                    )
                                } else {
                                    player.sendMessage(
                                            plugin.languageManager.getMessage(
                                                    player,
                                                    "error.archive_failed"
                                            )
                                    )
                                }
                            }
                    )
        }
    }

    private fun performUnarchiveWorld(player: Player, plugin: MyWorldManager) {
        val session = plugin.settingsSessionManager.getSession(player) ?: return
        val uuid = session.worldUuid
        val worldData = plugin.worldConfigRepository.findByUuid(uuid) ?: return

        player.sendMessage(plugin.languageManager.getMessage(player, "messages.unarchive_start"))
        plugin.worldService.unarchiveWorld(uuid).thenAccept { success: Boolean ->
            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            Runnable {
                                if (success) {
                                    player.sendMessage(
                                            plugin.languageManager.getMessage(
                                                    player,
                                                    "messages.unarchive_success"
                                            )
                                    )
                                    plugin.worldGui.open(
                                            player,
                                            fromAdminMenu = true,
                                            suppressSound = true
                                    )
                                } else {
                                    player.sendMessage(
                                            plugin.languageManager.getMessage(
                                                    player,
                                                    "error.unarchive_failed"
                                            )
                                    )
                                }
                            }
                    )
        }
    }
}
