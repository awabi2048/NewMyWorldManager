package me.awabi2048.myworldmanager.service

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.repository.WorldConfigRepository
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.WorldCreator
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import net.kyori.adventure.text.Component
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * ワールドの作成、読み込み、削除、アーカイブ化を制御するサービスクラス
 */
class WorldService(
    private val plugin: MyWorldManager,
    private val repository: WorldConfigRepository,
    private val playerStatsRepository: me.awabi2048.myworldmanager.repository.PlayerStatsRepository
) {

    // アーカイブ用フォルダの場所（サーバーのルート/archived_worlds を想定）
    private val archiveFolder = File(plugin.dataFolder.parentFile.parentFile, "archived_worlds")
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    init {
        if (!archiveFolder.exists()) {
            archiveFolder.mkdirs()
        }
    }

    /**
     * テンプレートから新しいワールドを作成する
     * フォルダ名は my_world.{uuid} 形式
     */
    fun createWorld(templateName: String, ownerUuid: UUID, displayWorldName: String, initialPoints: Int = 0): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()
        
        val source = File(Bukkit.getWorldContainer(), templateName)
        if (!source.exists() || !source.isDirectory) {
            plugin.logger.warning("テンプレートワールド $templateName が見つかりませんでした。")
            future.complete(false)
            return future
        }

        val newWorldUuid = UUID.randomUUID()
        val targetName = "my_world.$newWorldUuid" // 命名規則：my_world.{uuid}
        val target = File(Bukkit.getWorldContainer(), targetName)

        // 非同期でフォルダをコピー
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                copyDirectory(source, target)
                
                // UIDをリセットしてワールドの重複を回避
                val uidDat = File(target, "uid.dat")
                if (uidDat.exists()) {
                    uidDat.delete()
                }

                // 期限切れ日時の設定
                val initialDays = plugin.config.getInt("default_expiration.initial_days", 7)
                val expireDate = LocalDate.now().plusDays(initialDays.toLong()).format(dateFormatter)

                // ワールドデータの作成
                val worldData = WorldData(
                    uuid = newWorldUuid,
                    name = displayWorldName,
                    description = "新規ワールド",
                    icon = Material.GRASS_BLOCK,
                    sourceWorld = templateName,
                    expireDate = expireDate,
                    owner = ownerUuid,
                    cumulativePoints = initialPoints
                )
                
                // メインスレッドでワールドをロード
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    val creator = WorldCreator(targetName)
                    val world = Bukkit.createWorld(creator)
                    if (world != null) {
                        setupNewWorld(world, worldData, templateName)
                        future.complete(true)
                        
                        // マクロ実行
                        plugin.macroManager.execute("on_world_create", mapOf(
                            "owner" to Bukkit.getOfflinePlayer(ownerUuid).name.toString(),
                            "world_uuid" to newWorldUuid.toString(),
                            "world_name" to displayWorldName,
                            "template_name" to templateName
                        ))
                    } else {
                        future.complete(false)
                    }
                })

            } catch (e: Exception) {
                plugin.logger.severe("ワールドの作成中にエラーが発生しました: ${e.message}")
                e.printStackTrace()
                future.complete(false)
            }
        })

        return future
    }

    /**
     * 新しく生成されたワールドの初期設定を行う
     */
    private fun setupNewWorld(world: org.bukkit.World, worldData: WorldData, templateName: String? = null) {
        // ボーダー初期設定
        val initialSize = plugin.config.getDouble("expansion.initial_size", 100.0)
        
        // ボーダー中心の決定
        val mwm = plugin as? me.awabi2048.myworldmanager.MyWorldManager
        val template = if (templateName != null) mwm?.templateRepository?.findAll()?.find { it.path == templateName } else null
        
        val center = if (template?.originLocation != null) {
            template.originLocation.clone().apply { 
                this.world = world
                this.x = this.blockX + 0.5
                this.z = this.blockZ + 0.5
            }
        } else {
            org.bukkit.Location(world, 0.5, 64.0, 0.5) // デフォルト中央
        }
        
        world.worldBorder.center = center
        world.worldBorder.center = center
        
        if (worldData.borderExpansionLevel == WorldData.EXPANSION_LEVEL_SPECIAL) {
            world.worldBorder.size = 60000000.0 // Max size
        } else {
            world.worldBorder.size = initialSize
        }
        
        // ゲームルールの設定 (モブのスポーンを停止)
        world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false)
        world.setGameRule(org.bukkit.GameRule.DO_PATROL_SPAWNING, false)
        world.setGameRule(org.bukkit.GameRule.DO_TRADER_SPAWNING, false)
        try {
            // バージョンによって存在しない場合があるためtry-catch
            @Suppress("UNCHECKED_CAST")
            val wardenRule = org.bukkit.GameRule::class.java.getField("DO_WARDEN_SPAWNING").get(null) as org.bukkit.GameRule<Boolean>
            world.setGameRule(wardenRule, false)
        } catch (e: Exception) {}
        
        // デフォルトのスポーン地点を設定
        val spawnLoc = if (template?.originLocation != null) {
            template.originLocation.clone().apply {
                this.world = world
                this.yaw = 180f // 北向き
                this.pitch = 0f
            }
        } else {
            val defaultSpawn = world.spawnLocation
            val highestY = world.getHighestBlockYAt(defaultSpawn.blockX, defaultSpawn.blockZ)
            val targetY = if (highestY < world.seaLevel) world.seaLevel.toDouble() else (highestY + 1).toDouble()
            org.bukkit.Location(world, defaultSpawn.x, targetY, defaultSpawn.z, 180f, 0f)
        }

        world.setSpawnLocation(spawnLoc)
        
        // WorldDataの更新
        worldData.borderCenterPos = center
        
        // データ保存
        repository.save(worldData)
        
        // プレイヤーをテレポート
        val player = Bukkit.getPlayer(worldData.owner)
        if (player != null && player.isOnline) {
            val lang = (plugin as me.awabi2048.myworldmanager.MyWorldManager).languageManager
            player.teleport(world.spawnLocation)
            plugin.soundManager.playTeleportSound(player)
            player.sendMessage(lang.getMessage(player, "messages.world_created_warp"))
        }
    }

    /**
     * シード値を指定して新規ワールドを生成する
     */
    fun generateWorld(ownerUuid: UUID, displayWorldName: String, seed: Long?, initialPoints: Int = 0): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()
        val newWorldUuid = UUID.randomUUID()
        val targetName = "my_world.$newWorldUuid"

        // 期限切れ日時の設定
        val initialDays = plugin.config.getInt("default_expiration.initial_days", 7)
        val expireDate = LocalDate.now().plusDays(initialDays.toLong()).format(dateFormatter)

        // ワールドデータの作成
        val worldData = WorldData(
            uuid = newWorldUuid,
            name = displayWorldName,
            description = "新規生成ワールド",
            icon = Material.GRASS_BLOCK,
            sourceWorld = "GENERATED",
            expireDate = expireDate,
            owner = ownerUuid,
            cumulativePoints = initialPoints
        )

        // メインスレッドで生成
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val creator = WorldCreator(targetName)
            if (seed != null) {
                creator.seed(seed)
            }
            
            val world = Bukkit.createWorld(creator)
            if (world != null) {
                setupNewWorld(world, worldData)
                future.complete(true)

                // マクロ実行
                plugin.macroManager.execute("on_world_create", mapOf(
                    "owner" to Bukkit.getOfflinePlayer(ownerUuid).name.toString(),
                    "world_uuid" to newWorldUuid.toString(),
                    "world_name" to displayWorldName,
                    "template_name" to "GENERATED"
                ))
            } else {
                future.complete(false)
            }
        })

        return future
    }

    /**
     * 指定されたUUIDのワールドをロードする
     */
    fun loadWorld(uuid: UUID): Boolean {
        // ワールドデータの存在確認
        val worldData = repository.findByUuid(uuid) ?: return false
        val folderName = getWorldFolderName(worldData)
        
        val world = if (Bukkit.getWorld(folderName) != null) {
            Bukkit.getWorld(folderName)
        } else {
            val creator = WorldCreator(folderName)
            Bukkit.createWorld(creator)
        }

        if (world != null) {
            // ロード後にWordDataの設定を反映
            if (worldData.borderExpansionLevel == WorldData.EXPANSION_LEVEL_SPECIAL) {
                world.worldBorder.size = 60000000.0
            } else {
                val initialSize = plugin.config.getDouble("expansion.initial_size", 100.0)
                val borderSize = initialSize * Math.pow(2.0, worldData.borderExpansionLevel.toDouble())
                world.worldBorder.size = borderSize
            }
            
            // 座標情報の反映（未ロード時に不完全だったLocationを完成させる）
            worldData.borderCenterPos?.let { 
                val loc = it.clone()
                loc.world = world
                world.worldBorder.center = loc
                worldData.borderCenterPos = loc
            }
            
            worldData.spawnPosGuest?.let { 
                val loc = it.clone()
                loc.world = world
                worldData.spawnPosGuest = loc
                // バニラのスポーン地点も更新
                world.setSpawnLocation(loc)
            }
            
            worldData.spawnPosMember?.let { 
                val loc = it.clone()
                loc.world = world
                worldData.spawnPosMember = loc
            }
            
            return true
        }
        return false
    }

    /**
     * 指定されたUUIDのワールドをアンロードする
     */
    fun unloadWorld(uuid: UUID): Boolean {
        val worldData = repository.findByUuid(uuid) ?: return false
        val folderName = getWorldFolderName(worldData)
        val world = Bukkit.getWorld(folderName) ?: return false
        
        val evacLoc = getEvacuationLocation()
        
        // アンロード前にプレイヤーを移動させる
        for (player in world.players) {
            val lang = (plugin as me.awabi2048.myworldmanager.MyWorldManager).languageManager
            player.teleport(evacLoc)
            player.sendMessage(lang.getMessage(player, "messages.evacuated"))
        }
        
        return Bukkit.unloadWorld(world, true)
    }

    fun getEvacuationLocation(): Location {
        val config = plugin.config
        val worldName = config.getString("evacuation_location.world", "world")
        val evacWorld = Bukkit.getWorld(worldName!!) ?: Bukkit.getWorlds()[0]
        val x = config.getDouble("evacuation_location.x", evacWorld.spawnLocation.x)
        val y = config.getDouble("evacuation_location.y", evacWorld.spawnLocation.y)
        val z = config.getDouble("evacuation_location.z", evacWorld.spawnLocation.z)
        val yaw = config.getDouble("evacuation_location.yaw", evacWorld.spawnLocation.yaw.toDouble()).toFloat()
        val pitch = config.getDouble("evacuation_location.pitch", evacWorld.spawnLocation.pitch.toDouble()).toFloat()
        
        return Location(evacWorld, x, y, z, yaw, pitch)
    }

    /**
     * プレイヤーをマイワールドにテレポートさせ、マクロを実行する
     */
    fun teleportToWorld(player: Player, worldUuid: UUID, location: Location? = null, runMacro: Boolean = true) {
        val worldData = repository.findByUuid(worldUuid) ?: return
        val folderName = getWorldFolderName(worldData)
        
        // ワールドロード前にインベントリを閉じる (ロード中の操作防止)
        if (Bukkit.getWorld(folderName) == null) {
            player.closeInventory()
            if (!loadWorld(worldUuid)) return
        }
        
        val world = Bukkit.getWorld(folderName) ?: return
        // locationが指定されている場合はそれを優先、なければspawnPosGuest、それもなければバニラのspawnLocation
        val targetLoc = location?.clone() ?: worldData.spawnPosGuest?.clone() ?: world.spawnLocation.clone()
        
        // ワールド情報の補完（LocationにWorldが入っていない場合がある）
        if (targetLoc.world == null) {
            targetLoc.world = world
        }

        player.teleport(targetLoc)
        plugin.soundManager.playTeleportSound(player)
        
        // マクロ実行
        if (runMacro) {
            plugin.macroManager.execute("on_world_warp", mapOf(
                "player" to player.name,
                "world_uuid" to worldUuid.toString()
            ))
        }
    }

    /**
     * 指定されたUUIDのワールドをアーカイブフォルダーへ移動する
     */
    fun archiveWorld(uuid: UUID): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()
        val data = repository.findByUuid(uuid) ?: run {
             future.complete(false)
             return future
        }

        // ロードされている場合のみアンロードを試行
        val folderName = getWorldFolderName(data)
        if (Bukkit.getWorld(folderName) != null) {
            if (!unloadWorld(uuid)) {
                future.complete(false)
                return future
            }
        }

        val source = File(Bukkit.getWorldContainer(), folderName)
        
        // 非同期でファイルを移動
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                if (source.exists()) {
                    val target = File(archiveFolder, folderName)
                    if (target.exists()) {
                        target.deleteRecursively()
                    }
                    Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    
                    data.isArchived = true
                    // 保存処理は同期で行う
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        repository.save(data)
                        future.complete(true)
                    })
                } else {
                    future.complete(false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                future.complete(false)
            }
        })
        
        return future
    }

    /**
     * 指定されたUUIDのワールドをアーカイブフォルダーから戻す
     */
    fun unarchiveWorld(uuid: UUID): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()
        val data = repository.findByUuid(uuid) ?: run {
             future.complete(false)
             return future
        }

        val folderName = getWorldFolderName(data)
        val source = File(archiveFolder, folderName)
        val target = File(Bukkit.getWorldContainer(), folderName)

        // 非同期でファイルを移動
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                if (source.exists()) {
                    if (target.exists()) {
                        target.deleteRecursively()
                    }
                    Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    
                    data.isArchived = false
                    // 保存処理は同期で行う
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        repository.save(data)
                        future.complete(true)
                    })
                } else {
                    future.complete(false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                future.complete(false)
            }
        })
        
        return future
    }

    /**
     * 指定されたUUIDのワールドを完全に削除する
     */
    fun deleteWorld(uuid: UUID): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()
        @Suppress("UNUSED_VARIABLE")
        val data = repository.findByUuid(uuid) ?: run {
            future.complete(false)
            return future
        }

        val folderName = getWorldFolderName(data)
        
        // メインスレッドでアンロード
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (Bukkit.getWorld(folderName) != null) {
                if (!unloadWorld(uuid)) {
                    future.complete(false)
                    return@Runnable
                }
            }

            // 非同期でファイル削除
            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                try {
                    val target = File(Bukkit.getWorldContainer(), folderName)
                    if (target.exists()) {
                        deleteDirectory(target)
                    }
                    
                    // 設定ファイルの削除
                    repository.delete(uuid)

                    // 拡張スロットの消費（設定が有効な場合）
                    if (plugin.config.getBoolean("deletion.reduce_owner_slot", false)) {
                        val stats = playerStatsRepository.findByUuid(data.owner)
                        if (stats.unlockedWorldSlot > 0) {
                            stats.unlockedWorldSlot--
                            playerStatsRepository.save(stats)
                            plugin.logger.info("ワールド削除に伴い、${Bukkit.getOfflinePlayer(data.owner).name} の拡張スロットを1つ消費しました。")
                        }
                    }
                    
                    future.complete(true)

                    // マクロ実行
                    plugin.macroManager.execute("on_world_delete", mapOf(
                        "world_uuid" to uuid.toString()
                    ))
                } catch (e: Exception) {
                    e.printStackTrace()
                    future.complete(false)
                }
            })
        })

        return future
    }

    /**
     * ディレクトリを再帰的に削除する内部メソッド
     */
    private fun deleteDirectory(directory: File) {
        val files = directory.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                deleteDirectory(file)
            } else {
                file.delete()
            }
        }
        directory.delete()
    }

    /**
     * ディレクトリを再帰的にコピーする内部メソッド
     */
    private fun copyDirectory(source: File, target: File) {
        if (!target.exists()) {
            target.mkdirs()
        }
        val files = source.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                if (file.name == "uid.dat" || file.name == "session.lock") continue
                copyDirectory(file, File(target, file.name))
            } else {
                if (file.name == "uid.dat" || file.name == "session.lock") continue
                Files.copy(file.toPath(), File(target, file.name).toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    /**
     * 指定されたUUIDのワールドとメタデータをパッケージ化してエクスポートする
     */
    fun exportWorld(uuid: UUID): CompletableFuture<File?> {
        val future = CompletableFuture<File?>()
        val data = repository.findByUuid(uuid) ?: run {
            future.complete(null)
            return future
        }

        val folderName = getWorldFolderName(data)
        val worldDir = File(Bukkit.getWorldContainer(), folderName)
        if (!worldDir.exists()) {
            future.complete(null)
            return future
        }

        val exportDir = File(plugin.dataFolder, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val zipFile = File(exportDir, "${data.name}_${uuid}.zip")

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                    // 1. ワールドフォルダの追加
                    zipDirectory(worldDir, folderName, zos)

                    // 2. メタデータの追加 (元のYAMLファイルを取得して書き込む方式に変更)
                    val originalMetaFile = File(plugin.dataFolder, "my_worlds/${data.uuid}.yml")
                    if (originalMetaFile.exists()) {
                        zos.putNextEntry(ZipEntry("${data.uuid}.yml"))
                        Files.copy(originalMetaFile.toPath(), zos)
                        zos.closeEntry()
                    }
                }
                future.complete(zipFile)
            } catch (e: Exception) {
                e.printStackTrace()
                future.complete(null)
            }
        })

        return future
    }

    enum class ConversionMode {
        NORMAL, // リネームあり・標準管理
        ADMIN   // リネームなし・管理用（不変）
    }

    /**
     * 既存のワールドをマイワールドとして変換する（管理下に置く）
     */
    fun convertWorld(world: org.bukkit.World, ownerUuid: UUID, mode: ConversionMode): CompletableFuture<UUID?> {
        val future = CompletableFuture<UUID?>()
        val oldName = world.name
        val newUuid = UUID.randomUUID()
        
        val lang = (plugin as me.awabi2048.myworldmanager.MyWorldManager).languageManager

        if (mode == ConversionMode.NORMAL) {
            // NORMAL: ディレクトリをリネームして標準化
            val newFolderName = "my_world.$newUuid"
            val evacLoc = getEvacuationLocation()

            for (player in world.players) {
                player.teleport(evacLoc)
                player.sendMessage(lang.getMessage(player, "messages.evacuated"))
            }

            if (!Bukkit.unloadWorld(world, true)) {
                future.complete(null)
                return future
            }

            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                try {
                    val oldDir = File(Bukkit.getWorldContainer(), oldName)
                    val newDir = File(Bukkit.getWorldContainer(), newFolderName)

                    if (oldDir.exists()) {
                        Files.move(oldDir.toPath(), newDir.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                        
                        val initialDays = plugin.config.getInt("default_expiration.initial_days", 7)
                        val expireDate = LocalDate.now().plusDays(initialDays.toLong()).format(dateFormatter)

                        val worldData = WorldData(
                            uuid = newUuid,
                            name = oldName,
                            description = "変換されたワールド",
                            icon = Material.GRASS_BLOCK,
                            sourceWorld = "CONVERT",
                            expireDate = expireDate,
                            owner = ownerUuid,
                            borderExpansionLevel = 0
                        )

                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            repository.save(worldData)
                            if (loadWorld(newUuid)) future.complete(newUuid) else future.complete(null)
                        })
                    } else {
                        future.complete(null)
                    }
                } catch (e: Exception) {
                    plugin.logger.severe("ワールド変換中にエラーが発生しました: ${e.message}")
                    future.complete(null)
                }
            })
        } else {
            // ADMIN: リネームなし、設定のみ生成
            val worldData = WorldData(
                uuid = newUuid,
                name = oldName,
                description = "",
                icon = Material.GRASS_BLOCK,
                sourceWorld = "CONVERT",
                expireDate = "2999-12-31", // 無期限
                owner = ownerUuid,
                borderExpansionLevel = WorldData.EXPANSION_LEVEL_SPECIAL, // 拡張不可
                customWorldName = oldName // リポジトリ検索用
            )

            repository.save(worldData)
            future.complete(newUuid)
        }

        return future
    }

    private fun zipDirectory(source: File, baseName: String, zos: ZipOutputStream) {
        val files = source.listFiles() ?: return
        for (file in files) {
            if (file.name == "session.lock") continue
            val entryName = "$baseName/${file.name}"
            if (file.isDirectory) {
                zipDirectory(file, entryName, zos)
            } else {
                zos.putNextEntry(ZipEntry(entryName))
                Files.copy(file.toPath(), zos)
                zos.closeEntry()
            }
        }
    }

    /**
     * すべてのワールドの訪問者統計を更新する（1日1回呼び出し想定）
     */
    fun updateDailyData() {
        val allWorlds = repository.findAll()
        for (worldData in allWorlds) {
            val list = worldData.recentVisitors
            if (list.size >= 7) {
                list.removeAt(6) // 一番古い要素を削除
            }
            list.add(0, 0) // 先頭に新しい日のカウンター(0)を追加
            
            while (list.size < 7) list.add(0)
            while (list.size > 7) list.removeAt(7)

            repository.save(worldData)
        }

        plugin.logger.info("ワールドの訪問者統計を更新しました。")

        // 自動アーカイブの実行
        if (plugin.config.getBoolean("default_expiration.automatic_archive", true)) {
            val today = LocalDate.now()
            val archiveTargets = repository.findAll().filter { worldData ->
                !worldData.isArchived && try {
                    val expire = LocalDate.parse(worldData.expireDate, dateFormatter)
                    expire.isBefore(today)
                } catch (e: Exception) { false }
            }

            if (archiveTargets.isNotEmpty()) {
                plugin.logger.info("${archiveTargets.size}件のワールドが期限切れのため、順次アーカイブを開始します。")
                
                fun processNext(index: Int) {
                    if (index >= archiveTargets.size) return
                    val worldData = archiveTargets[index]
                    archiveWorld(worldData.uuid).thenAccept { success ->
                        if (success) {
                            plugin.logger.info("自動アーカイブ成功: ${worldData.name} (${worldData.uuid})")
                        } else {
                            plugin.logger.warning("自動アーカイブ失敗: ${worldData.name} (${worldData.uuid})")
                        }
                        processNext(index + 1)
                    }
                }
                processNext(0)
            }
        }
    }
    fun sendAnnouncementMessage(player: Player, worldData: WorldData) {
        // メンバー、モデレーター、オーナーには表示しない
        if (worldData.owner == player.uniqueId || worldData.members.contains(player.uniqueId) || worldData.moderators.contains(player.uniqueId)) {
            return
        }

        if (worldData.announcementMessages.isNotEmpty()) {
            val lang = (plugin as me.awabi2048.myworldmanager.MyWorldManager).languageManager
            // ヘッダーなどは言語ファイルで定義されていないが、シンプルにメッセージリストを送る
            // 必要であれば装飾を加える
            // player.sendMessage("§8§m-----------------------")
            worldData.announcementMessages.forEach { msg ->
                player.sendMessage(msg)
            }
            // player.sendMessage("§8§m-----------------------")
        }
    }

    private fun getWorldFolderName(worldData: WorldData): String {
        return worldData.customWorldName ?: "my_world.${worldData.uuid}"
    }

    /**
     * 各テンプレートのスポーン周辺チャンクを事前に読み込む
     */
    fun preloadTemplateChunks() {
        val templates = plugin.templateRepository.findAll()
        for (template in templates) {
            val dir = File(Bukkit.getWorldContainer(), template.path)
            if (dir.exists() && dir.isDirectory) {
                // メインスレッドでロードとチャンクの読み込みを行う
                val creator = WorldCreator(template.path)
                val world = Bukkit.createWorld(creator)
                if (world != null) {
                    val loc = template.originLocation?.clone()?.apply { this.world = world } ?: world.spawnLocation
                    val chunk = loc.chunk
                    if (!chunk.isLoaded) {
                        chunk.load(true)
                        plugin.logger.info("テンプレートワールド '${template.name}' の初期チャンクを事前読み込みしました。")
                    }
                }
            }
        }
    }
}
