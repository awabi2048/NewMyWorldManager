package me.awabi2048.myworldmanager.session

import me.awabi2048.myworldmanager.MyWorldManager
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.EntityType
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import java.io.File
import java.util.UUID

/**
 * テンプレートワールドのプレビューセッションを管理するクラス
 */
class PreviewSessionManager(private val plugin: MyWorldManager) {

    private val sessions = mutableMapOf<UUID, PreviewSession>()
    private val pendingRestoreFile = File(plugin.dataFolder, "pending_preview_restore.yml")

    init {
        // 起動時に保存されていた復元情報を読み込む
        loadPendingRestores()
    }

    /**
     * プレビュー対象
     */
    sealed class PreviewTarget {
        data class Template(val path: String) : PreviewTarget()
        data class World(val worldData: me.awabi2048.myworldmanager.model.WorldData) : PreviewTarget()
    }

    /**
     * プレビュー中かどうかを確認
     */
    fun isInPreview(player: Player): Boolean = sessions.containsKey(player.uniqueId)

    /**
     * セッションを取得
     */
    fun getSession(player: Player): PreviewSession? = sessions[player.uniqueId]

    /**
     * プレビューを開始する（共通処理）
     */
    fun startPreview(player: Player, target: PreviewTarget, source: PreviewSource): Boolean {
        if (isInPreview(player)) return false

        // プレビュー開始音（グローバルクリック音）
        plugin.soundManager.playGlobalClickSound(player)

        val world: org.bukkit.World
        val templateName: String
        val originLoc: Location
        val folderName: String

        when (target) {
            is PreviewTarget.Template -> {
                val template = plugin.templateRepository.findAll().find { it.path == target.path }
                if (template == null) {
                    player.sendMessage(plugin.languageManager.getMessage(player, "messages.preview_template_not_found"))
                    return false
                }

                // テンプレートワールドのロード確認
                if (Bukkit.getWorld(target.path) == null) {
                    player.closeInventory() // ロード前に閉じる
                    val creator = org.bukkit.WorldCreator(target.path)
                    Bukkit.createWorld(creator) ?: run {
                        player.sendMessage(plugin.languageManager.getMessage(player, "messages.preview_world_load_failed"))
                        return false
                    }
                }

                world = Bukkit.getWorld(target.path) ?: return false
                folderName = target.path
                templateName = template.name
                originLoc = template.originLocation?.clone() ?: Location(world, 0.5, 64.0, 0.5)

                // プレビュー時の天気・時間を設定
                template.previewTime?.let { world.fullTime = it }
                template.previewWeather?.let { weather ->
                    setWorldWeather(world, weather)
                }
            }
            is PreviewTarget.World -> {
                val worldData = target.worldData
                // アーカイブ済みチェック
                if (worldData.isArchived) {
                    player.sendMessage(plugin.languageManager.getMessage(player, "messages.preview_archived"))
                    plugin.soundManager.playActionSound(player, "discovery", "access_denied")
                    return false
                }

                // ワールドがロードされていない場合はロード
                folderName = worldData.customWorldName ?: "my_world.${worldData.uuid}"
                if (Bukkit.getWorld(folderName) == null) {
                    player.closeInventory() // ロード前に閉じる
                    if (!plugin.worldService.loadWorld(worldData.uuid)) {
                        player.sendMessage(plugin.languageManager.getMessage(player, "messages.preview_world_load_failed"))
                        return false
                    }
                }

                world = Bukkit.getWorld(folderName) ?: return false
                templateName = worldData.name
                originLoc = worldData.spawnPosGuest?.clone() ?: world.spawnLocation.clone()
            }
        }

        if (originLoc.world == null) originLoc.world = world

        // 既存セッションの保存
        val session = PreviewSession(
            playerUuid = player.uniqueId,
            originalLocation = player.location.clone(),
            originalGameMode = player.gameMode,
            templatePath = folderName,
            source = source,
        )
        sessions[player.uniqueId] = session

        // config設定を取得
        val config = plugin.config
        val durationSeconds = config.getDouble("template_preview.duration_seconds", 6.0)
        val pitch = config.getDouble("template_preview.pitch", -30.0).toFloat()
        val height = config.getDouble("template_preview.height", 10.0)

        // 少し上空からの視点
        val viewLocation = originLoc.clone().add(0.0, height, 0.0)
        viewLocation.pitch = pitch
        viewLocation.yaw = 0f
        session.previewLocation = viewLocation

        // メッセージ送信
        player.sendMessage(plugin.languageManager.getMessage(player, "messages.preview_start", mapOf("template" to templateName)))

        // 即座にテレポートとスペクテイター設定
        viewLocation.chunk.load()
        player.teleport(viewLocation)
        player.gameMode = GameMode.SPECTATOR

        // 回転アニメーションの開始 (遅延なし)
        startRotationTask(player, durationSeconds)

        return true
    }

    private fun setWorldWeather(world: org.bukkit.World, weather: String) {
        when (weather.lowercase()) {
            "clear", "sun" -> {
                world.setStorm(false)
                world.isThundering = false
            }
            "rain" -> {
                world.setStorm(true)
                world.isThundering = false
            }
            "storm", "thunder" -> {
                world.setStorm(true)
                world.isThundering = true
            }
        }
    }

    private fun startRotationTask(player: Player, durationSeconds: Double) {
        val ticksTotal = (durationSeconds * 20).toInt()
        val yawPerTick = 360f / ticksTotal
        var ticksElapsed = 0

        sessions[player.uniqueId]?.rotationTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (ticksElapsed >= ticksTotal) {
                endPreview(player, false)
                return@Runnable
            }

            val currentSession = sessions[player.uniqueId] ?: return@Runnable
            val viewLocation = currentSession.previewLocation
            if (viewLocation != null) {
                val newYaw = (ticksElapsed * yawPerTick) % 360f
                val loc = viewLocation.clone()
                loc.yaw = newYaw
                
                // プレイヤーを直接テレポートさせて位置と視点を固定
                player.teleport(loc)
                currentSession.currentYaw = newYaw
            }

            ticksElapsed++
        }, 0L, 1L)
    }

    /**
     * プレイヤーがログアウトした時の処理（復元情報を保存）
     */
    fun handlePlayerQuit(playerUuid: UUID) {
        val session = sessions.remove(playerUuid) ?: return

        // 回転タスクをキャンセル
        session.rotationTask?.cancel()

        // 復元情報をファイルに保存（サーバー再起動対応）
        savePendingRestore(playerUuid, session.originalLocation, session.originalGameMode)
    }

    /**
     * プレビューを終了する
     */
    fun endPreview(player: Player, cancelled: Boolean) {
        val session = sessions.remove(player.uniqueId) ?: return

        // 回転タスクをキャンセル
        session.rotationTask?.cancel()

        // スペクテイターターゲットを解除
        player.spectatorTarget = null

        // 元の位置・ゲームモードに復元
        player.gameMode = session.originalGameMode
        player.teleport(session.originalLocation)

        // メッセージ送信
        val lang = plugin.languageManager
        if (cancelled) {
            player.sendMessage(lang.getMessage(player, "messages.preview_cancel"))
        } else {
            player.sendMessage(lang.getMessage(player, "messages.preview_end"))
        }

        // テンプレート選択画面を再表示（少し遅延させる）
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (!player.isOnline) return@Runnable

            when (session.source) {
                PreviewSource.TEMPLATE_SELECTION -> {
                    val creationSession = plugin.creationSessionManager.getSession(player.uniqueId)
                    if (creationSession != null) {
                        plugin.creationGui.openTemplateSelection(player)
                    }
                }
                PreviewSource.FAVORITE_MENU -> {
                    plugin.favoriteGui.open(player)
                }
                PreviewSource.DISCOVERY_MENU -> {
                    plugin.discoveryGui.open(player)
                }
            }
        }, 5L)
    }

    /**
     * プレイヤーがログインした時の復元処理
     */
    fun handlePlayerJoin(player: Player) {
        val config = YamlConfiguration()
        if (!pendingRestoreFile.exists()) return
        
        try {
            config.load(pendingRestoreFile)
        } catch (e: Exception) {
            return
        }
        
        val uuidStr = player.uniqueId.toString()
        if (!config.contains(uuidStr)) return

        val section = config.getConfigurationSection(uuidStr) ?: return
        
        // 復元データを取得
        val worldName = section.getString("world") ?: return
        val x = section.getDouble("x")
        val y = section.getDouble("y")
        val z = section.getDouble("z")
        val yaw = section.getDouble("yaw").toFloat()
        val pitch = section.getDouble("pitch").toFloat()
        val gameModeStr = section.getString("gameMode") ?: "SURVIVAL"
        
        val world = Bukkit.getWorld(worldName)
        if (world == null) {
            // ワールドが存在しない場合はスポーンに送る
            val evacuationWorld = plugin.config.getString("evacuation_location.world", "world") ?: "world"
            val evacWorld = Bukkit.getWorld(evacuationWorld)
            if (evacWorld != null) {
                val evacLoc = Location(
                    evacWorld,
                    plugin.config.getDouble("evacuation_location.x", 0.5),
                    plugin.config.getDouble("evacuation_location.y", 64.0),
                    plugin.config.getDouble("evacuation_location.z", 0.5)
                )
                player.teleport(evacLoc)
            }
        } else {
            val restoreLoc = Location(world, x, y, z, yaw, pitch)
            player.teleport(restoreLoc)
        }
        
        val gameMode = try {
            GameMode.valueOf(gameModeStr)
        } catch (e: Exception) {
            GameMode.SURVIVAL
        }
        player.gameMode = gameMode
        
        // 復元したデータを削除
        config.set(uuidStr, null)
        try {
            config.save(pendingRestoreFile)
        } catch (e: Exception) {
            plugin.logger.warning("復元データの削除に失敗しました: ${e.message}")
        }
        
        player.sendMessage(plugin.languageManager.getMessage(player, "messages.preview_restored"))
    }

    /**
     * 復元情報をファイルに保存
     */
    private fun savePendingRestore(playerUuid: UUID, location: Location, gameMode: GameMode) {
        val config = if (pendingRestoreFile.exists()) {
            try {
                YamlConfiguration.loadConfiguration(pendingRestoreFile)
            } catch (e: Exception) {
                YamlConfiguration()
            }
        } else {
            YamlConfiguration()
        }
        
        val uuidStr = playerUuid.toString()
        config.set("$uuidStr.world", location.world?.name ?: "world")
        config.set("$uuidStr.x", location.x)
        config.set("$uuidStr.y", location.y)
        config.set("$uuidStr.z", location.z)
        config.set("$uuidStr.yaw", location.yaw.toDouble())
        config.set("$uuidStr.pitch", location.pitch.toDouble())
        config.set("$uuidStr.gameMode", gameMode.name)
        
        try {
            config.save(pendingRestoreFile)
        } catch (e: Exception) {
            plugin.logger.warning("プレビュー復元情報の保存に失敗しました: ${e.message}")
        }
    }

    /**
     * 起動時に保存されていた復元情報を読み込む
     */
    private fun loadPendingRestores() {
        // 実際の復元はプレイヤーがログインした時に行う
        // ここでは何もしない（ファイルの存在確認のみ）
        if (pendingRestoreFile.exists()) {
            plugin.logger.info("プレビュー復元データが存在します。プレイヤーログイン時に復元されます。")
        }
    }

    /**
     * 全てのプレビューを強制終了（プラグイン無効化時用）
     */
    fun endAllPreviews() {
        val playerUuids = sessions.keys.toList()
        for (uuid in playerUuids) {
            val player = Bukkit.getPlayer(uuid)
            if (player != null && player.isOnline) {
                endPreview(player, true)
            } else {
                // オフラインの場合は復元情報を保存
                handlePlayerQuit(uuid)
            }
        }
    }
}
