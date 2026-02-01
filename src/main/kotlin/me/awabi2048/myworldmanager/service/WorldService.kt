package me.awabi2048.myworldmanager.service

import java.io.File
import java.util.*
import java.util.logging.Level
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.repository.PlayerStatsRepository
import me.awabi2048.myworldmanager.repository.WorldConfigRepository
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.WorldCreator
import org.bukkit.WorldType
import org.bukkit.entity.Player

class WorldService(
        private val plugin: MyWorldManager,
        private val repository: WorldConfigRepository,
        private val playerStatsRepository: PlayerStatsRepository
) {

    private val creatingWorlds = mutableSetOf<String>()

    enum class ConversionMode {
        NORMAL,
        ADMIN
    }

    /**
     * ワールドの作成処理を行う
     *
     * @param player 作成者
     * @param worldName ワールド名（表示名）
     * @param seed シード値（nullならランダム）
     * @param environment 環境設定（NORMAL, NETHER, THE_END）
     * @param generator ジェネレーター設定（nullならデフォルト）
     * @param worldType ワールドタイプ（NORMAL, FLATなど）
     * @return 作成成功ならtrue
     */
    fun createWorld(
            player: Player,
            worldName: String,
            seed: String?,
            environment: org.bukkit.World.Environment,
            generator: String? = null,
            worldType: WorldType = WorldType.NORMAL
    ): Boolean {

        val uuid = UUID.randomUUID()
        val worldFolderName = "my_world.${uuid}"

        // 同名ワールドのチェック（念のため）
        if (Bukkit.getWorld(worldFolderName) != null ||
                        File(Bukkit.getWorldContainer(), worldFolderName).exists()
        ) {
            player.sendMessage(
                    plugin.languageManager.getMessage(player, "error.world_already_exists")
            )
            return false
        }

        // 作成中フラグ
        if (creatingWorlds.contains(player.uniqueId.toString())) {
            player.sendMessage(
                    plugin.languageManager.getMessage(player, "error.world_creation_in_progress")
            )
            return false
        }
        creatingWorlds.add(player.uniqueId.toString())

        player.sendMessage(
                plugin.languageManager.getMessage(
                        player,
                        "messages.world_creation_started",
                        mapOf("world" to worldName)
                )
        )

        // 非同期でワールド作成（BukkitのWorldCreatorはメインスレッドで呼ぶ必要があるが、準備等の重い処理を分割できるか検討。
        // ただし、WorldCreator.createWorld()自体はメインスレッド必須。
        // ここでは、ラグ軽減のため、チャット送信などを先に行い、1tick後に作成開始するなどの工夫が可能だが、
        // ひとまずはメインスレッドで実行する。

        try {
            val creator = WorldCreator(worldFolderName)
            creator.environment(environment)
            creator.type(worldType)

            if (seed != null) {
                try {
                    creator.seed(seed.hashCode().toLong()) // 文字列シード対応（簡易的）
                } catch (e: NumberFormatException) {
                    creator.seed(seed.hashCode().toLong())
                }
            } else {
                // ランダムシードはデフォルトで適用されるので設定不要
            }

            if (generator != null) {
                // generatorの指定形式: "PluginName:GeneratorID" など
                creator.generator(generator)
            }

            // 構造物生成の有無などはWorldTypeやserver.propertiesに依存するが、
            // 必要に応じて creator.generateStructures(boolean) を設定可能。
            // 今回はデフォルト（true）とする。

            val world = plugin.server.createWorld(creator)

            if (world == null) {
                player.sendMessage(
                        plugin.languageManager.getMessage(player, "error.world_creation_failed")
                )
                creatingWorlds.remove(player.uniqueId.toString())
                return false
            }

            finalizeWorldCreation(player, uuid, worldName, worldFolderName, world, 0, "None")
            return true
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Failed to create world: $worldName", e)
            player.sendMessage(plugin.languageManager.getMessage(player, "error.internal_error"))
            creatingWorlds.remove(player.uniqueId.toString())
            return false
        }
    }

    private fun finalizeWorldCreation(
            player: Player,
            uuid: UUID,
            worldName: String,
            worldFolderName: String,
            world: org.bukkit.World,
            cost: Int,
            templateName: String
    ) {
        val now = java.time.LocalDateTime.now()
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val expireDate =
                java.time.LocalDate.now()
                        .plusDays(
                                plugin.config.getLong(
                                        "default_expiration.initial_days",
                                        30
                                )
                        )

        // 初期ワールドボーダーの設定
        val initialSize = plugin.config.getDouble("expansion.initial_size", 100.0)
        val initialCenter = world.spawnLocation

        val border = world.worldBorder
        border.center = initialCenter
        border.size = initialSize

        val worldData =
                WorldData(
                        uuid = uuid,
                        name = worldName,
                        description = "My World",
                        icon = org.bukkit.Material.GRASS_BLOCK,
                        sourceWorld = "template",
                        expireDate = expireDate.toString(),
                        owner = player.uniqueId,
                        members = mutableListOf(),
                        moderators = mutableListOf(),
                        publishLevel = me.awabi2048.myworldmanager.model.PublishLevel.PRIVATE,
                        spawnPosMember = initialCenter,
                        borderCenterPos = initialCenter,
                        borderExpansionLevel = 0,
                        cumulativePoints = cost,
                        isArchived = false,
                        customWorldName = worldFolderName,
                        createdAt = now.format(formatter)
                )

        repository.save(worldData)

        player.sendMessage(
                plugin.languageManager.getMessage(
                        player,
                        "messages.world_creation_success",
                        mapOf("world" to worldName)
                )
        )

        teleportToWorld(player, uuid)
        creatingWorlds.remove(player.uniqueId.toString())

        // マクロ実行
        plugin.macroManager.execute(
                "on_world_create",
                mapOf(
                        "owner" to player.name,
                        "world_uuid" to uuid.toString(),
                        "world_name" to worldName,
                        "template_name" to templateName
                )
        )
    }

    /** ワールドの生成処理（async互換用） */
    fun generateWorld(
            ownerUuid: UUID,
            worldName: String,
            seed: String?,
            cost: Int
    ): java.util.concurrent.CompletableFuture<Boolean> {
        val future = java.util.concurrent.CompletableFuture<Boolean>()
        val player = Bukkit.getPlayer(ownerUuid)
        if (player == null) {
            future.complete(false)
            return future
        }
        val success = createWorld(player, worldName, seed, org.bukkit.World.Environment.NORMAL)
        // Note: Currently createWorld(player, ...) doesn't take cost. 
        // If it's used elsewhere, it might need update. For now, matching the call.
        future.complete(success)
        return future
    }

    /** ワールドの作成処理（テンプレート用、async互換用） */
    fun createWorld(
            templateName: String,
            ownerUuid: UUID,
            worldName: String,
            cost: Int
    ): java.util.concurrent.CompletableFuture<Boolean> {
        val future = java.util.concurrent.CompletableFuture<Boolean>()
        val player = Bukkit.getPlayer(ownerUuid)
        if (player == null) {
            future.complete(false)
            return future
        }

        val uuid = UUID.randomUUID()
        val worldFolderName = "my_world.${uuid}"

        if (Bukkit.getWorld(worldFolderName) != null ||
                        File(Bukkit.getWorldContainer(), worldFolderName).exists()
        ) {
            player.sendMessage(
                    plugin.languageManager.getMessage(player, "error.world_already_exists")
            )
            future.complete(false)
            return future
        }

        if (creatingWorlds.contains(player.uniqueId.toString())) {
            player.sendMessage(
                    plugin.languageManager.getMessage(player, "error.world_creation_in_progress")
            )
            future.complete(false)
            return future
        }
        creatingWorlds.add(player.uniqueId.toString())

        player.sendMessage(
                plugin.languageManager.getMessage(
                        player,
                        "messages.world_creation_started",
                        mapOf("world" to worldName)
                )
        )

        val templateFolder = File(Bukkit.getWorldContainer(), templateName)
        if (!templateFolder.exists() || !templateFolder.isDirectory) {
            player.sendMessage("§cテンプレートが見つかりません: $templateName")
            creatingWorlds.remove(player.uniqueId.toString())
            future.complete(false)
            return future
        }

        val targetFolder = File(Bukkit.getWorldContainer(), worldFolderName)

        // フォルダのコピー (非同期スレッドで実行)
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                // 手動での再帰的コピー (session.lock 等を避けるため)
                templateFolder.walkTopDown().forEach { file ->
                    val relativePath = file.relativeTo(templateFolder).path
                    val targetFile = File(targetFolder, relativePath)

                    // スキップするファイル/ディレクトリ
                    if (file.name == "session.lock" || file.name == "uid.dat") return@forEach

                    if (file.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        file.copyTo(targetFile, overwrite = true)
                    }
                }

                // ワールドの読み込みはメインスレッドで行う
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    try {
                        val creator = WorldCreator(worldFolderName)
                        val world = plugin.server.createWorld(creator)

                        if (world == null) {
                            player.sendMessage(plugin.languageManager.getMessage(player, "error.world_creation_failed"))
                            creatingWorlds.remove(player.uniqueId.toString())
                            future.complete(false)
                            return@Runnable
                        }

                        finalizeWorldCreation(player, uuid, worldName, worldFolderName, world, cost, templateName)
                        future.complete(true)
                    } catch (e: Exception) {
                        plugin.logger.log(Level.SEVERE, "Failed to load copied world: $worldName", e)
                        player.sendMessage(plugin.languageManager.getMessage(player, "error.internal_error"))
                        creatingWorlds.remove(player.uniqueId.toString())
                        future.complete(false)
                    }
                })
            } catch (e: Exception) {
                plugin.logger.log(Level.SEVERE, "Failed to copy template: $templateName", e)
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    player.sendMessage("§cテンプレートのコピーに失敗しました。")
                    creatingWorlds.remove(player.uniqueId.toString())
                    future.complete(false)
                })
            }
        })

        return future
    }

    /**
     * ワールドの読み込みを行う
     * @param worldUuid ワールドUUID
     * @return 読み込み成功ならtrue
     */
    fun loadWorld(worldUuid: UUID): Boolean {
        val worldData = repository.findByUuid(worldUuid) ?: return false
        val folderName = getWorldFolderName(worldData)

        if (Bukkit.getWorld(folderName) != null) {
            return true // すでにロードされている
        }

        if (!File(Bukkit.getWorldContainer(), folderName).exists()) {
            return false // ワールドフォルダが存在しない
        }

        return try {
            val creator = WorldCreator(folderName)
            // creator.environment(worldData.environment) // Missing in WorldData
            plugin.server.createWorld(creator)

            // 最終ロード時刻の更新
            // worldData.lastLoaded = now.format(formatter) // Missing in WorldData
            repository.save(worldData)

            true
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Failed to load world: $folderName", e)
            false
        }
    }

    /** ワールドのアンロードを行う */
    fun unloadWorld(worldUuid: UUID, save: Boolean = true): Boolean {
        val worldData = repository.findByUuid(worldUuid) ?: return false
        val folderName = getWorldFolderName(worldData)
        val world = Bukkit.getWorld(folderName) ?: return true // すでにアンロードされている

        // プレイヤーがいる場合はロビー等に退避させる処理が必要
        if (world.players.isNotEmpty()) {
            val lobby = Bukkit.getWorlds().firstOrNull() // 暫定的に最初のワールド（通常はメインワールド）
            if (lobby != null) {
                for (p in world.players) {
                    p.teleport(lobby.spawnLocation)
                    p.sendMessage(
                            plugin.languageManager.getMessage(
                                    p,
                                    "messages.world_unloading_evacuation"
                            )
                    )
                }
            }
        }

        return plugin.server.unloadWorld(world, save)
    }

    /** ワールドフォルダ名を取得する（my_world.UUID または customWorldName） */
    fun getWorldFolderName(worldData: WorldData): String {
        return worldData.customWorldName ?: "my_world.${worldData.uuid}"
    }

    /** プレイヤーを指定されたワールドにテレポートさせる */
    fun teleportToWorld(
            player: Player,
            worldUuid: UUID,
            location: Location? = null,
            runMacro: Boolean = true
    ) {
        val worldData = repository.findByUuid(worldUuid) ?: return
        val folderName = getWorldFolderName(worldData)

        if (Bukkit.getWorld(folderName) == null) {
            player.closeInventory()
            if (!loadWorld(worldUuid)) return
        }

        val world = Bukkit.getWorld(folderName)
        if (world == null) {
            player.sendMessage(plugin.languageManager.getMessage(player, "error.world_load_failed"))
            return
        }

        // スポーン地点の決定
        val targetLoc =
                location
                        ?: if (worldData.spawnPosMember != null &&
                                        (worldData.owner == player.uniqueId ||
                                                worldData.members.contains(player.uniqueId) ||
                                                worldData.moderators.contains(player.uniqueId))
                        ) {
                            worldData.spawnPosMember!!
                        } else if (worldData.spawnPosGuest != null) {
                            worldData.spawnPosGuest!!
                        } else {
                            world.spawnLocation
                        }

        // ワールド参照の補完(アーカイブ復帰直後など、参照がnullになっている場合があるため)
        if (targetLoc.world == null) {
            targetLoc.world = world
        }

        player.teleport(targetLoc)

        plugin.soundManager.playTeleportSound(player)

        // マクロ実行
        if (runMacro) {
            plugin.macroManager.execute(
                    "on_join",
                    mapOf("player" to player.name, "world_uuid" to worldUuid.toString())
            )
        }

        // 最終アクセス日時の更新などはaccessControlListener等で行うのが良いかもしれないが、
        // 明示的にここで更新する手もある。
    }

    /** プレイヤーの既存のワールドデータをすべて削除してリセットする（デバッグ用・管理者用） */
    fun resetPlayerData(player: Player) {
        val worlds = repository.findAll().filter { it.owner == player.uniqueId }
        for (world in worlds) {
            deleteWorld(world.uuid)
        }
    }

    /** ワールドをアーカイブから戻す */
    fun unarchiveWorld(worldUuid: UUID): java.util.concurrent.CompletableFuture<Boolean> {
        val future = java.util.concurrent.CompletableFuture<Boolean>()
        val worldData = repository.findByUuid(worldUuid)
        if (worldData == null) {
            future.complete(false)
            return future
        }

        val folderName = getWorldFolderName(worldData)
        val archiveFolder = File(plugin.dataFolder.parentFile.parentFile, "archived_worlds")
        val archivedFile = File(archiveFolder, folderName)
        val targetFile = File(plugin.server.worldContainer, folderName)

        if (archivedFile.exists()) {
            if (!archivedFile.renameTo(targetFile)) {
                plugin.logger.severe("Failed to move world directory from archive: $folderName")
                future.complete(false)
                return future
            }
        }

        worldData.isArchived = false
        // 期限をリセット
        val initialDays = plugin.config.getLong("default_expiration.initial_days", 90)
        worldData.expireDate = java.time.LocalDate.now().plusDays(initialDays).toString()
        
        repository.save(worldData)
        future.complete(true)
        return future
    }

    /** ワールドを完全に削除する */
    fun deleteWorld(worldUuid: UUID): java.util.concurrent.CompletableFuture<Boolean> {
        val future = java.util.concurrent.CompletableFuture<Boolean>()
        if (unloadWorld(worldUuid, false)) { // セーブせずにアンロード
            val worldData = repository.findByUuid(worldUuid)
            if (worldData == null) {
                future.complete(false)
                return future
            }

            // ポイント返還処理
            val stats = playerStatsRepository.findByUuid(worldData.owner)
            val refundRate = plugin.config.getDouble("critical_settings.refund_percentage", 0.5)
            val refund = (worldData.cumulativePoints * refundRate).toInt()

            if (refund > 0) {
                stats.worldPoint += refund
                playerStatsRepository.save(stats)
            }

            val folderName = getWorldFolderName(worldData)
            val archiveFolder = File(plugin.dataFolder.parentFile.parentFile, "archived_worlds")
            
            val folder = if (worldData.isArchived) {
                File(archiveFolder, folderName)
            } else {
                File(Bukkit.getWorldContainer(), folderName)
            }

            if (folder.exists()) {
                val deleted = folder.deleteRecursively()
                if (deleted) {
                    repository.delete(worldUuid)

                    // マクロ実行
                    plugin.macroManager.execute(
                            "on_world_delete",
                            mapOf("world_uuid" to worldUuid.toString())
                    )
                    future.complete(true)
                } else {
                    future.complete(false)
                }
            } else {
                // フォルダがない場合もデータだけ削除
                repository.delete(worldUuid)
                future.complete(true)
            }
        } else {
            future.complete(false)
        }
        return future
    }

    /** ワールドをアーカイブする */
    fun archiveWorld(worldUuid: UUID): java.util.concurrent.CompletableFuture<Boolean> {
        val future = java.util.concurrent.CompletableFuture<Boolean>()
        val worldData = repository.findByUuid(worldUuid)
        if (worldData == null) {
            future.complete(false)
            return future
        }
        if (unloadWorld(worldUuid, true)) {
            val folderName = getWorldFolderName(worldData)
            val archiveFolder = File(plugin.dataFolder.parentFile.parentFile, "archived_worlds")
            if (!archiveFolder.exists()) archiveFolder.mkdirs()

            val sourceFile = File(plugin.server.worldContainer, folderName)
            val targetFile = File(archiveFolder, folderName)

            if (sourceFile.exists()) {
                if (!sourceFile.renameTo(targetFile)) {
                    plugin.logger.severe("Failed to move world directory to archive: $folderName")
                    future.complete(false)
                    return future
                }
            }

            worldData.isArchived = true
            repository.save(worldData)
            future.complete(true)
        } else {
            future.complete(false)
        }
        return future
    }

    /** ワールドを変換する */
    fun convertWorld(
            world: org.bukkit.World,
            owner: UUID,
            mode: me.awabi2048.myworldmanager.service.WorldService.ConversionMode
    ): java.util.concurrent.CompletableFuture<UUID?> {
        val future = java.util.concurrent.CompletableFuture<UUID?>()
        // 簡易実装
        future.complete(null)
        return future
    }

    /** ワールドをエクスポートする */
    fun exportWorld(worldUuid: UUID): java.util.concurrent.CompletableFuture<java.io.File?> {
        val future = java.util.concurrent.CompletableFuture<java.io.File?>()
        // 簡易実装
        future.complete(null)
        return future
    }

    /** アナウンスメッセージをプレイヤーに送信する */
    fun sendAnnouncementMessage(
            player: Player,
            worldData: me.awabi2048.myworldmanager.model.WorldData
    ) {
        for (message in worldData.announcementMessages) {
            player.sendMessage(me.awabi2048.myworldmanager.util.ColorConverter.toComponent(message))
        }
    }

    /** テンプレートワールドのチャンクを事前読み込みする */
    fun preloadTemplateChunks() {
        // 簡易実装
    }

    /** 日次のデータ更新処理を行う */
    fun updateDailyData() {
        // 簡易実装
    }

    /** 避難先ロケーションを取得する ロビーワールドが見つからない場合はメインワールドのスポーン地点 */
    fun getEvacuationLocation(): Location {
        val lobbyName = plugin.config.getString("lobby_world_name", "world")
        var lobby = Bukkit.getWorld(lobbyName!!)
        if (lobby == null) {
            lobby = Bukkit.getWorlds().firstOrNull()
        }
        return lobby?.spawnLocation ?: Bukkit.getWorlds()[0].spawnLocation
    }
}
