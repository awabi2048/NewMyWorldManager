package me.awabi2048.myworldmanager.session

import com.awabi2048.ccsystem.api.localization.generated.CommonKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldMessagesKeys

import com.awabi2048.ccsystem.CCSystem

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.service.WorldLoadFailure
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
        data class Template(val templateId: String) : PreviewTarget()
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
    fun startPreview(
        player: Player,
        target: PreviewTarget,
        source: PreviewSource,
        onReturn: (() -> Unit)? = null
    ): Boolean {
        if (isInPreview(player)) return false

        // プレビュー開始音（グローバルクリック音）
        plugin.soundManager.playGlobalClickSound(player)

        val world: org.bukkit.World
        val templateName: String
        val originLoc: Location
        val folderName: String

        when (target) {
            is PreviewTarget.Template -> {
                val template = plugin.templateRepository.findById(target.templateId)
                if (template == null) {
                    player.sendMessage(plugin.languageManager.getMessage(player, CommonKeys.ERROR_PREVIEW_TEMPLATE_NOT_FOUND))
                    return false
                }
                if (!plugin.templateRepository.isUsable(template)) {
                    player.sendMessage(plugin.languageManager.getMessage(player, CommonKeys.ERROR_PREVIEW_TEMPLATE_INVALID))
                    return false
                }

                // プレビューも通常ワープと同じ診断を通し、旧形式を暗黙にロードしません。
                val loadResult = plugin.worldService.loadWorldByKey(template.path)
                if (!loadResult.isSuccess) {
                    val failure = loadResult.failure ?: WorldLoadFailure.BUKKIT_LOAD_FAILED
                    player.sendMessage(failure.message(plugin, player))
                    return false
                }

                world = requireNotNull(loadResult.world)
                folderName = template.path
                templateName = template.name
                originLoc = template.originLocation!!.clone()

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
                    player.sendMessage(plugin.languageManager.getMessage(player, MyworldMessagesKeys.MESSAGES_PREVIEW_ARCHIVED))
                    plugin.soundManager.playActionSound(player, "discovery", "access_denied")
                    return false
                }

                // ワールドがロードされていない場合はロード
                val worldKey = org.bukkit.NamespacedKey.fromString(worldData.worldKey)
                if (worldKey == null) {
                    player.sendMessage(WorldLoadFailure.INVALID_KEY.message(plugin, player))
                    return false
                }
                folderName = worldKey.toString()
                val loadedWorld = Bukkit.getWorld(worldKey)
                if (loadedWorld == null) {
                    val loadResult = plugin.worldService.loadWorldDetailed(worldData.uuid)
                    if (!loadResult.isSuccess) {
                        val failure = loadResult.failure ?: WorldLoadFailure.BUKKIT_LOAD_FAILED
                        player.sendMessage(failure.message(plugin, player))
                        return false
                    }
                    world = requireNotNull(loadResult.world)
                } else {
                    world = loadedWorld
                }
                templateName = worldData.name
                originLoc = worldData.spawnPosGuest?.clone() ?: world.spawnLocation.clone()
            }
        }

        if (originLoc.world == null) originLoc.world = world
        val runtimeSuspended =
            source != PreviewSource.EXTERNAL &&
                CCSystem.getAPI().getMenuRuntimeService().suspendForExternal(player)

        // 既存セッションの保存
        val session = PreviewSession(
            playerUuid = player.uniqueId,
            originalLocation = player.location.clone(),
            originalGameMode = player.gameMode,
            templatePath = folderName,
            source = source,
            onReturn = onReturn,
            runtimeSuspended = runtimeSuspended,
        )
        sessions[player.uniqueId] = session

        // config設定を取得
        val config = plugin.config
        val durationSeconds = config.getDouble("template_preview.duration_seconds", 6.0)

        // --- プレビュー視点の計算 ---
        val worldData = (target as? PreviewTarget.World)?.worldData
        val isGuestSpawnSet = worldData?.spawnPosGuest != null

        val initialYaw = if (isGuestSpawnSet) originLoc.yaw else 0f
        val initialPitch = if (isGuestSpawnSet) originLoc.pitch else config.getDouble("template_preview.pitch", -30.0).toFloat()
        val heightOffset = if (isGuestSpawnSet) 0.0 else config.getDouble("template_preview.height", 10.0)

        // 指定位置（または少し上空）からの視点
        val viewLocation = originLoc.clone().add(0.0, heightOffset, 0.0)
        viewLocation.pitch = initialPitch
        viewLocation.yaw = initialYaw
        session.previewLocation = viewLocation

        // 即座にスペクテイターへ切り替えてからテレポートします。テレポートイベント中に
        // ワールド状態ポリシーが動いても、プレビュー中のプレイヤーを通常モードへ戻さないためです。
        viewLocation.chunk.load()
        player.gameMode = GameMode.SPECTATOR
        if (!player.teleport(viewLocation)) {
            // テレポートがキャンセルされた場合は、作成済みセッションと外部メニュー停止状態を残さず復元します。
            sessions.remove(player.uniqueId)
            player.gameMode = session.originalGameMode
            player.teleport(session.originalLocation)
            if (runtimeSuspended) {
                CCSystem.getAPI().getMenuRuntimeService().finishExternal(player)
            }
            player.sendMessage(plugin.languageManager.getMessage(player, CommonKeys.ERROR_PREVIEW_WORLD_LOAD_FAILED))
            return false
        }

        // テレポート先のイベントや連携プラグインがモードを変更しても、プレビュー開始時点で
        // 必ずスペクテイターへ戻します。
        player.gameMode = GameMode.SPECTATOR

        // メッセージ送信
        player.sendMessage(plugin.languageManager.getMessage(player, MyworldMessagesKeys.MESSAGES_PREVIEW_START, mapOf("template" to templateName)))

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
        val session = sessions[player.uniqueId] ?: return
        val initialYaw = session.previewLocation?.yaw ?: 0f

        val ticksTotal = (durationSeconds * 20).toInt()
        val yawPerTick = 360f / ticksTotal
        var ticksElapsed = 0

        session.rotationTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (ticksElapsed >= ticksTotal) {
                endPreview(player, false)
                return@Runnable
            }

            val currentSession = sessions[player.uniqueId] ?: return@Runnable
            val viewLocation = currentSession.previewLocation
            if (viewLocation != null) {
                val newYaw = (initialYaw + ticksElapsed * yawPerTick) % 360f
                val loc = viewLocation.clone()
                loc.yaw = newYaw

                // プレビュー中に他プラグインがゲームモードを変更しても、回転処理の各tickで
                // 一時表示状態を再保証します。
                if (player.gameMode != GameMode.SPECTATOR) {
                    player.gameMode = GameMode.SPECTATOR
                }

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

        // CraftPlayer.setSpectatorTargetはスペクテイター中以外では例外になるため、
        // 外部要因でモードが変わったセッション終了時はターゲット解除を行いません。
        if (player.gameMode == GameMode.SPECTATOR) {
            player.spectatorTarget = null
        }

        // 元の位置・ゲームモードに復元
        player.gameMode = session.originalGameMode
        player.teleport(session.originalLocation)

        // メッセージ送信
        val lang = plugin.languageManager
        if (cancelled) {
            player.sendMessage(lang.getMessage(player, MyworldMessagesKeys.MESSAGES_PREVIEW_CANCEL))
        } else {
            player.sendMessage(lang.getMessage(player, MyworldMessagesKeys.MESSAGES_PREVIEW_END))
        }

        // テンプレート選択画面を再表示（少し遅延させる）
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (!player.isOnline) return@Runnable
            if (session.runtimeSuspended &&
                CCSystem.getAPI().getMenuRuntimeService().finishExternal(player)
            ) {
                return@Runnable
            }

            when (session.source) {
                PreviewSource.TEMPLATE_SELECTION -> {
                    val creationSession = plugin.creationSessionManager.getSession(player.uniqueId)
                    if (creationSession != null) {
                        plugin.creationGui.openTemplateSelection(player)
                    }
                }
                PreviewSource.FAVORITE_MENU -> {
                    plugin.menuEntryRouter.openFavoriteList(player)
                }
                PreviewSource.TEMPLATE_DETAIL -> {
                    val creationSession = plugin.creationSessionManager.getSession(player.uniqueId)
                    if (creationSession != null) {
                        creationSession.phase = WorldCreationPhase.TEMPLATE_DETAIL
                        plugin.creationGui.openTemplateDetail(player, creationSession)
                    }
                }
                PreviewSource.CREATION_CONFIRM -> {
                    val creationSession = plugin.creationSessionManager.getSession(player.uniqueId)
                    if (creationSession != null) {
                        creationSession.phase = WorldCreationPhase.CONFIRM
                        plugin.creationGui.openConfirmation(player, creationSession)
                    }
                }
                PreviewSource.EXTERNAL -> {
                    session.onReturn?.invoke()
                }
                PreviewSource.DISCOVERY_MENU -> {
                    plugin.menuEntryRouter.openDiscovery(player)
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
        val worldKey = section.getString("world_key") ?: return
        val x = section.getDouble("x")
        val y = section.getDouble("y")
        val z = section.getDouble("z")
        val yaw = section.getDouble("yaw").toFloat()
        val pitch = section.getDouble("pitch").toFloat()
        val gameModeStr = section.getString("gameMode") ?: "SURVIVAL"

        val key = org.bukkit.NamespacedKey.fromString(worldKey)
        val world = key?.let(Bukkit::getWorld)
        if (world == null) {
            // ワールドが存在しない場合はスポーンに送る
            player.teleport(plugin.worldService.getEvacuationLocation())
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

        player.sendMessage(plugin.languageManager.getMessage(player, MyworldMessagesKeys.MESSAGES_PREVIEW_RESTORED))
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
        config.set("$uuidStr.world_key", location.world?.key?.toString() ?: "minecraft:world")
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
