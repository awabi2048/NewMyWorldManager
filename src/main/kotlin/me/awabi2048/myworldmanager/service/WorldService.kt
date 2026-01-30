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

            // ワールドデータの保存
            val now = java.time.LocalDateTime.now()
            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            val expireDate =
                    java.time.LocalDate.now()
                            .plusDays(
                                    plugin.config.getLong(
                                            "world_settings.default_expiration_days",
                                            30
                                    )
                            )

            val worldData =
                    WorldData(
                            uuid = uuid,
                            name = worldName,
                            owner = player.uniqueId,
                            members = mutableListOf(),
                            moderators = mutableListOf(),
                            createdAt = now.format(formatter),
                            expireDate = expireDate.toString(), // 期限は後で設定（デフォルト値など）
                            lastLoaded = now.format(formatter),
                            borderSize = 1000.0, // 初期ボーダーサイズ
                            isArchived = false,
                            customWorldName = worldFolderName,
                            environment = environment,
                            seed = world.seed,
                            worldType = worldType,
                            difficulty = org.bukkit.Difficulty.NORMAL
                    )

            repository.save(worldData)

            // プレイヤーの統計情報を更新（作成ワールド数をインクリメントなど）
            // 実装予定

            player.sendMessage(
                    plugin.languageManager.getMessage(
                            player,
                            "messages.world_creation_success",
                            mapOf("world" to worldName)
                    )
            )

            // 作成完了後、テレポートするか尋ねる、あるいは自動でテレポート
            teleportToWorld(player, uuid)

            creatingWorlds.remove(player.uniqueId.toString())
            return true
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Failed to create world: $worldName", e)
            player.sendMessage(plugin.languageManager.getMessage(player, "error.internal_error"))
            creatingWorlds.remove(player.uniqueId.toString())
            return false
        }
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
            // 環境情報はWorldDataから復元する必要があるが、bukkitはlevel.datから読み取るので指定しなくても大抵はいける。
            // 明示的に指定したほうが安全。
            creator.environment(worldData.environment)
            plugin.server.createWorld(creator)

            // 最終ロード時刻の更新
            val now = java.time.LocalDateTime.now()
            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            worldData.lastLoaded = now.format(formatter)
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
    fun teleportToWorld(player: Player, worldUuid: UUID, runMacro: Boolean = true) {
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
        // WorldDataにspawnLocationがあればそれを使う（未実装）、なければworld.spawnLocation
        val targetLoc =
                if (worldData.spawnPosMember != null &&
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

        player.teleport(targetLoc)
        plugin.soundManager.playTeleportSound(player)

        // マクロ実行
        if (runMacro) {
            plugin.macroManager.execute(
                    player,
                    me.awabi2048.myworldmanager.model.MacroTriggerType.ON_JOIN
            )
        }

        // 最終アクセス日時の更新などはaccessControlListener等で行うのが良いかもしれないが、
        // 明示的にここで更新する手もある。
    }

    /** プレイヤーの既存のワールドデータをすべて削除してリセットする（デバッグ用・管理者用） */
    fun resetPlayerData(player: Player) {
        val worlds = repository.findByOwner(player.uniqueId)
        for (world in worlds) {
            deleteWorld(world.uuid)
        }
    }

    /** ワールドを完全に削除する */
    fun deleteWorld(worldUuid: UUID): Boolean {
        if (unloadWorld(worldUuid, false)) { // セーブせずにアンロード
            val worldData = repository.findByUuid(worldUuid) ?: return false
            val folderName = getWorldFolderName(worldData)
            val folder = File(Bukkit.getWorldContainer(), folderName)

            if (folder.exists()) {
                val deleted = folder.deleteRecursively()
                if (deleted) {
                    repository.delete(worldUuid)
                    return true
                }
            } else {
                // フォルダがない場合もデータだけ削除
                repository.delete(worldUuid)
                return true
            }
        }
        return false
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
