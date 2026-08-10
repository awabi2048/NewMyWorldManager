package me.awabi2048.myworldmanager.listener

import java.util.UUID
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.service.WorldService
import me.awabi2048.myworldmanager.session.SettingsAction
import me.awabi2048.myworldmanager.session.WorldCreationType
import me.awabi2048.myworldmanager.util.WorldRuntimePolicies
import me.awabi2048.myworldmanager.migration.WorldDirectoryState
import me.awabi2048.myworldmanager.service.WorldLoadFailure
import org.bukkit.Bukkit
import org.bukkit.entity.Player

class AdminCommandListener {

    fun executeConfirmation(
        player: Player,
        plugin: MyWorldManager,
        action: SettingsAction,
        targetWorldUuid: UUID?,
        targetWorldName: String?,
    ) {
        // 確認画面を開いた時点のルート情報を使用し、現在のセッションや
        // プレイヤーの移動先によって実行対象が変わらないようにします。
        when (action) {
            SettingsAction.ADMIN_UPDATE_DATA_CONFIRM -> performUpdateData(player, plugin)
            SettingsAction.ADMIN_REPAIR_TEMPLATES_CONFIRM -> performRepairTemplates(player, plugin)
            SettingsAction.ADMIN_ARCHIVE_ALL_CONFIRM -> performArchiveAll(player, plugin)
            SettingsAction.ADMIN_CONVERT_NORMAL_CONFIRM ->
                performConvert(player, plugin, WorldService.ConversionMode.NORMAL, targetWorldUuid)
            SettingsAction.ADMIN_CONVERT_ADMIN_CONFIRM ->
                performConvert(player, plugin, WorldService.ConversionMode.ADMIN, targetWorldUuid)
            SettingsAction.ADMIN_EXPORT_CONFIRM -> performExport(player, plugin, requireNotNull(targetWorldName))
            SettingsAction.ADMIN_UNLINK_CONFIRM -> performUnlink(player, plugin, requireNotNull(targetWorldName))
            SettingsAction.ADMIN_ARCHIVE_WORLD_CONFIRM -> performArchiveWorld(player, plugin, requireNotNull(targetWorldUuid))
            SettingsAction.ADMIN_UNARCHIVE_WORLD_CONFIRM -> performUnarchiveWorld(player, plugin, requireNotNull(targetWorldUuid))
            else -> return
        }
        // 管理メニューが作成した待機セッションだけを後始末します。
        // 別画面が同じプレイヤーのセッションを更新済みなら、その状態は保持します。
        if (plugin.settingsSessionManager.getSession(player)?.action == SettingsAction.ADMIN_MENU) {
            plugin.settingsSessionManager.endSession(player)
        }
    }

    private fun performUnlink(player: Player, plugin: MyWorldManager, targetWorldName: String) {
        val worldData = plugin.worldConfigRepository.findByWorldName(targetWorldName)
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
                                if (MyWorldManagerApi.isWorldPointEconomyEnabled() &&
                                    world.cumulativePoints <= 0
                                ) {
                                    val worldConfig = plugin.config
                                    var estimatedPoints =
                                            WorldRuntimePolicies.creationCost(worldConfig, WorldCreationType.TEMPLATE)
                                    estimatedPoints += WorldRuntimePolicies.totalExpansionCost(worldConfig, world.borderExpansionLevel)
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
                val worldKey = org.bukkit.NamespacedKey.fromString(path)
                if (worldKey == null) {
                    player.sendMessage(WorldLoadFailure.INVALID_KEY.message(plugin, player))
                    return@forEach
                }
                val resolution = plugin.worldDirectoryResolver.inspect(worldKey)
                val rejected = when (resolution.state) {
                    WorldDirectoryState.LEGACY -> WorldLoadFailure.MIGRATION_REQUIRED
                    WorldDirectoryState.CONFLICT -> WorldLoadFailure.DIRECTORY_CONFLICT
                    WorldDirectoryState.UNSAFE -> WorldLoadFailure.DIRECTORY_UNSAFE
                    WorldDirectoryState.CURRENT -> null
                    WorldDirectoryState.MISSING -> null
                }
                if (rejected != null) {
                    player.sendMessage(rejected.message(plugin, player))
                    return@forEach
                }

                // この管理操作だけは、診断でMISSINGと確定したテンプレートを明示的に新規生成します。
                val world = if (resolution.state == WorldDirectoryState.MISSING) {
                    Bukkit.createWorld(org.bukkit.WorldCreator(worldKey))
                } else {
                    Bukkit.getWorld(worldKey)
                }
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
            mode: WorldService.ConversionMode,
            targetWorldUuid: UUID?,
    ) {
        val currentWorld = targetWorldUuid?.let(Bukkit::getWorld)
        if (currentWorld == null) {
            player.sendMessage("§cワールドの変換に失敗しました。")
            return
        }
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

    private fun performExport(player: Player, plugin: MyWorldManager, targetWorldName: String) {
        val worldData = plugin.worldConfigRepository.findByWorldName(targetWorldName)
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

    private fun performArchiveWorld(player: Player, plugin: MyWorldManager, uuid: UUID) {
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
                                                    "messages.archive_failed"
                                            )
                                    )
                                }
                            }
                    )
        }
    }

    private fun performUnarchiveWorld(player: Player, plugin: MyWorldManager, uuid: UUID) {
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
