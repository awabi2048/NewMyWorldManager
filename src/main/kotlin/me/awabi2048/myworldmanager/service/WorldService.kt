package me.awabi2048.myworldmanager.service

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.*
import java.util.logging.Level
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.api.event.MwmDailyMaintenanceCompletedEvent
import me.awabi2048.myworldmanager.api.event.MwmWorldCreatedEvent
import me.awabi2048.myworldmanager.api.event.MwmWorldDeletedEvent
import me.awabi2048.myworldmanager.api.event.MwmWorldWarpedEvent
import me.awabi2048.myworldmanager.api.event.MwmWarpReason
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.migration.WorldDirectoryState
import me.awabi2048.myworldmanager.repository.PlayerStatsRepository
import me.awabi2048.myworldmanager.repository.WorldConfigRepository
import me.awabi2048.myworldmanager.session.WorldCreationType
import me.awabi2048.myworldmanager.session.WorldSpawnCoordinates
import me.awabi2048.myworldmanager.util.WorldNameValidation
import me.awabi2048.myworldmanager.util.WorldRuntimePolicies
import me.awabi2048.myworldmanager.util.WorldCreationChecks
import me.awabi2048.myworldmanager.util.SeedSpawnSafety
import me.awabi2048.myworldmanager.util.WorldWarpId
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.WorldCreator
import org.bukkit.WorldType
import org.bukkit.command.CommandSender
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture

class WorldService(
        private val plugin: MyWorldManager,
        private val repository: WorldConfigRepository,
        private val playerStatsRepository: PlayerStatsRepository
) {

    private val creatingWorlds = mutableSetOf<String>()
    private val expansionInitialSizeConfigKey = listOf("expansion", "initial_size").joinToString(".")

    private fun resolveSeed(seedInput: String?): Long? {
        val normalized = seedInput?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return normalized.toLongOrNull() ?: normalized.hashCode().toLong()
    }

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
            worldType: WorldType = WorldType.NORMAL,
            initialSpawn: WorldSpawnCoordinates? = null
    ): Boolean {

        if (!WorldCreationChecks.checkLimits(plugin, player, player.uniqueId) ||
                !WorldCreationChecks.check(player, type = WorldCreationType.SEED.takeIf { seed != null } ?: WorldCreationType.RANDOM)) {
            return false
        }

        plugin.worldValidator.validateName(worldName).let { result ->
            if (result is WorldNameValidation.Failure) {
                player.sendMessage(plugin.languageManager.getComponent(player, result.messageKey, result.placeholders))
                return false
            }
        }
        if (repository.findByOwnerAndDisplayName(player.uniqueId, worldName) != null) {
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.world_name_duplicate"))
            return false
        }

        val uuid = generateUniqueWorldUuid()
        val worldFolderName = "my_world.${uuid}"

        // Paper 26 ではカスタムワールド実体が world/dimensions/minecraft 配下に置かれるため、両方を確認する。
        if (Bukkit.getWorld(worldFolderName) != null ||
                        worldFolderExists(worldFolderName)
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
            val creator = WorldCreator(NamespacedKey.minecraft(worldFolderName))
            creator.environment(environment)
            creator.type(worldType)

            val resolvedSeed = resolveSeed(seed)
            if (resolvedSeed != null) {
                creator.seed(resolvedSeed)
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

            val effectiveInitialSpawn = if (environment == org.bukkit.World.Environment.THE_END) {
                prepareGeneratedEndWorld(world)
                initialSpawn ?: findRandomSafeEndSpawn(world)
            } else {
                initialSpawn
            }

            finalizeWorldCreation(player, uuid, worldName, worldFolderName, world, 0, "None", effectiveInitialSpawn, seed != null)
            return true
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Failed to create world: $worldName", e)
            player.sendMessage(plugin.languageManager.getMessage(player, "error.internal_error"))
            creatingWorlds.remove(player.uniqueId.toString())
            return false
        }
    }

    private fun prepareGeneratedEndWorld(world: org.bukkit.World) {
        removeEndBossEntities(world)
        hideEndDragonBossBar(world)
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            removeEndBossEntities(world)
            hideEndDragonBossBar(world)
        }, 20L)
    }

    private fun removeEndBossEntities(world: org.bukkit.World) {
        world.entities
                .filter { it.type == EntityType.ENDER_DRAGON || it.type == EntityType.END_CRYSTAL }
                .forEach { it.remove() }
    }

    private fun hideEndDragonBossBar(world: org.bukkit.World) {
        // End生成直後はドラゴン本体を消してもボスバーだけ残ることがあるため、BossBattle側も明示的に閉じる。
        world.enderDragonBattle?.bossBar?.let { bossBar ->
            bossBar.removeAll()
            bossBar.isVisible = false
        }
    }

    private fun findRandomSafeEndSpawn(world: org.bukkit.World): WorldSpawnCoordinates? {
        val random = java.util.concurrent.ThreadLocalRandom.current()
        repeat(160) {
            val radius = Math.sqrt(random.nextDouble()) * 15.0
            val angle = random.nextDouble(0.0, Math.PI * 2.0)
            val x = Math.round(Math.cos(angle) * radius).toInt()
            val z = Math.round(Math.sin(angle) * radius).toInt()
            val safe = findSafeSurfaceLocation(world, x, z)
            if (safe != null) {
                return WorldSpawnCoordinates(safe.blockX, safe.blockY, safe.blockZ)
            }
        }
        val spawn = world.spawnLocation
        return WorldSpawnCoordinates(spawn.blockX, spawn.blockY.coerceIn(1, 255), spawn.blockZ)
    }

    private fun findSafeSurfaceLocation(world: org.bukkit.World, x: Int, z: Int): Location? {
        world.getChunkAt(x shr 4, z shr 4).load()
        val y = world.getHighestBlockYAt(x, z) + 1
        if (!isSafeStandingLocation(world, x, y, z)) return null
        return Location(world, x + 0.5, y.toDouble(), z + 0.5)
    }

    private fun isSafeStandingLocation(world: org.bukkit.World, x: Int, y: Int, z: Int): Boolean {
        if (y <= world.minHeight + 1 || y >= world.maxHeight - 2) return false
        val floor = world.getBlockAt(x, y - 1, z)
        val feet = world.getBlockAt(x, y, z)
        val head = world.getBlockAt(x, y + 1, z)
        return !floor.type.isAir &&
                floor.type.isSolid &&
                feet.type.isAir &&
                head.type.isAir
    }

    private fun validateSeedSpawn(player: Player, worldData: WorldData, world: org.bukkit.World): Boolean {
        if (!worldData.seedSpecified || worldData.seedSpawnValidated) return true
        val requested = worldData.spawnPosMember ?: world.spawnLocation
        val requestedPosition = SeedSpawnSafety.Position(requested.blockX, requested.blockY, requested.blockZ)
        val radius = plugin.config.getInt("creation.seed_spawn_search_radius", 16).coerceAtLeast(0)
        val sameXzSafeY = (world.minHeight + 1 until world.maxHeight - 1)
            .filter { isSafeStandingLocation(world, requestedPosition.x, it, requestedPosition.z) }
        val surroundingSafe = (-radius..radius).flatMap { dx ->
            (-radius..radius).mapNotNull { dz ->
                if (dx == 0 && dz == 0) return@mapNotNull null
                findSafeSurfaceLocation(world, requestedPosition.x + dx, requestedPosition.z + dz)?.let {
                    SeedSpawnSafety.Position(it.blockX, it.blockY, it.blockZ)
                }
            }
        }
        val chosen = SeedSpawnSafety.choose(
            requestedPosition,
            isSafeStandingLocation(world, requestedPosition.x, requestedPosition.y, requestedPosition.z),
            sameXzSafeY,
            surroundingSafe
        ) ?: run {
            plugin.logger.warning("シード指定ワールド ${worldData.uuid} の安全な初回スポーン地点を見つけられませんでした")
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.seed_spawn.safe_location_not_found"))
            return false
        }

        if (chosen != requestedPosition) {
            plugin.logger.warning("シード指定ワールド ${worldData.uuid} の危険なスポーンを ${chosen.x},${chosen.y},${chosen.z} に補正しました")
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.seed_spawn.corrected", mapOf("x" to chosen.x, "y" to chosen.y, "z" to chosen.z)))
            val corrected = Location(world, chosen.x + 0.5, chosen.y.toDouble(), chosen.z + 0.5, requested.yaw, requested.pitch)
            worldData.spawnPosMember = corrected
            world.setSpawnLocation(chosen.x, chosen.y, chosen.z)
        }
        worldData.seedSpawnValidated = true
        repository.save(worldData)
        return true
    }

    private fun finalizeWorldCreation(
            player: Player,
            uuid: UUID,
            worldName: String,
            worldFolderName: String,
            world: org.bukkit.World,
            cost: Int,
            templateName: String,
            initialSpawn: WorldSpawnCoordinates? = null,
            seedSpecified: Boolean = false
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

        // World と永続データの基準地点を先に揃え、初回ワープやボーダー中心だけがずれないようにする。
        if (initialSpawn != null) {
            world.setSpawnLocation(initialSpawn.x, initialSpawn.y, initialSpawn.z)
        }

        // 初期ワールドボーダーの設定
        val initialSize = plugin.config.getDouble(expansionInitialSizeConfigKey, 100.0)
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
                        sourceWorld = "template:$templateName",
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
                        createdAt = now.format(formatter),
                        seedSpecified = seedSpecified,
                        seedSpawnValidated = !seedSpecified
                )

        repository.save(worldData)

        Bukkit.getPluginManager().callEvent(
                MwmWorldCreatedEvent(
                        worldUuid = worldData.uuid,
                        worldName = worldData.name,
                        ownerUuid = worldData.owner,
                        ownerName = player.name,
                        templateName = templateName,
                        createdAt = worldData.createdAt
                )
        )

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
            cost: Int,
            initialSpawn: WorldSpawnCoordinates? = null,
            environment: org.bukkit.World.Environment = org.bukkit.World.Environment.NORMAL
    ): java.util.concurrent.CompletableFuture<Boolean> {
        val future = java.util.concurrent.CompletableFuture<Boolean>()
        val player = Bukkit.getPlayer(ownerUuid)
        if (player == null) {
            future.complete(false)
            return future
        }
        val success = createWorld(
                player,
                worldName,
                seed,
                environment,
                initialSpawn = initialSpawn
        )
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

        if (!WorldCreationChecks.checkLimits(plugin, player, ownerUuid) ||
                !WorldCreationChecks.check(player, type = WorldCreationType.TEMPLATE)) {
            future.complete(false)
            return future
        }

        plugin.worldValidator.validateName(worldName).let { result ->
            if (result is WorldNameValidation.Failure) {
                player.sendMessage(plugin.languageManager.getComponent(player, result.messageKey, result.placeholders))
                future.complete(false)
                return future
            }
        }
        if (repository.findByOwnerAndDisplayName(ownerUuid, worldName) != null) {
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.world_name_duplicate"))
            future.complete(false)
            return future
        }

        val uuid = generateUniqueWorldUuid()
        val worldFolderName = "my_world.${uuid}"

        if (Bukkit.getWorld(worldFolderName) != null ||
                        worldFolderExists(worldFolderName)
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

        val templateFolder = plugin.worldDirectoryResolver.inspect(templateName)?.existingPath?.toFile()
        if (templateFolder == null || !templateFolder.exists() || !templateFolder.isDirectory) {
            player.sendMessage("§cテンプレートが見つかりません: $templateName")
            creatingWorlds.remove(player.uniqueId.toString())
            future.complete(false)
            return future
        }

        val targetFolder = preferredActiveWorldDirectory(worldFolderName)

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
                        val creator = WorldCreator(NamespacedKey.minecraft(worldFolderName))
                        val world = plugin.server.createWorld(creator)

                        if (world == null) {
                            player.sendMessage(plugin.languageManager.getMessage(player, "error.world_creation_failed"))
                            creatingWorlds.remove(player.uniqueId.toString())
                            future.complete(false)
                            return@Runnable
                        }

                        finalizeWorldCreation(player, uuid, worldName, worldFolderName, world, cost, templateName, null)
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

        when (plugin.worldDirectoryResolver.inspect(folderName)?.state) {
            WorldDirectoryState.CURRENT -> Unit
            WorldDirectoryState.LEGACY -> if (plugin.worldMigrationService.isPending(worldUuid)) {
                plugin.logger.warning("World load requires administrator approval: $folderName")
                return false
            }
            WorldDirectoryState.CONFLICT -> {
                plugin.logger.severe("World load rejected because both legacy and current directories exist: $folderName")
                return false
            }
            WorldDirectoryState.MISSING, null -> {
                plugin.logger.warning("World load rejected because the directory is missing or unsafe: $folderName")
                return false
            }
        }

        if (Bukkit.getWorld(folderName) != null) {
            plugin.worldEnvironmentService.applyAll(Bukkit.getWorld(folderName)!!, worldData)
            return true // すでにロードされている
        }

        return try {
            val creator = WorldCreator(NamespacedKey.minecraft(folderName))
            val world = plugin.server.createWorld(creator) ?: return false
            if (worldData.seedSpecified && world.environment == org.bukkit.World.Environment.THE_END) {
                prepareGeneratedEndWorld(world)
            }
            plugin.worldEnvironmentService.applyAll(world, worldData)
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
                    p.teleport(getEvacuationLocation())
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

    fun unloadWorldForMaintenance(worldUuid: UUID, save: Boolean): CompletableFuture<Boolean> {
        return unloadWorldAfterEvacuation(worldUuid, save)
    }

    private fun unloadWorldAfterEvacuation(worldUuid: UUID, save: Boolean): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()
        val worldData = repository.findByUuid(worldUuid)
        if (worldData == null) {
            future.complete(false)
            return future
        }

        val folderName = getWorldFolderName(worldData)
        val world = Bukkit.getWorld(folderName)
        if (world == null) {
            future.complete(true)
            return future
        }

        val players = world.players.toList()
        if (players.isEmpty()) {
            future.complete(plugin.server.unloadWorld(world, save))
            return future
        }

        val evacuationLocation = getEvacuationLocation()
        evacuationLocation.chunk.load()

        for (player in players) {
            if (!player.isOnline || player.world.name != folderName) continue
            player.teleport(evacuationLocation)
            player.sendMessage(
                    plugin.languageManager.getMessage(
                            player,
                            "messages.world_unloading_evacuation"
                    )
            )
        }

        val waitTicks =
                plugin.config.getLong("world_unload.evacuation_wait_ticks", 5L).coerceAtLeast(1L)
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val currentWorld = Bukkit.getWorld(folderName)
            if (currentWorld == null) {
                future.complete(true)
                return@Runnable
            }

            if (currentWorld.players.isNotEmpty()) {
                plugin.logger.warning(
                        "[MyWorldManager] Failed to unload $folderName: players remain after evacuation."
                )
                future.complete(false)
                return@Runnable
            }

            future.complete(plugin.server.unloadWorld(currentWorld, save))
        }, waitTicks)

        return future
    }

    /** ワールドフォルダ名を取得する（my_world.UUID または customWorldName） */
    fun getWorldFolderName(worldData: WorldData): String {
        return NamespacedKey.fromString(worldData.worldKey)?.key
            ?: throw IllegalStateException("Invalid world_key for ${worldData.uuid}: ${worldData.worldKey}")
    }

    fun getWorldDirectory(worldData: WorldData): File {
        val folderName = getWorldFolderName(worldData)
        if (worldData.isArchived) {
            return File(File(plugin.dataFolder.parentFile.parentFile, "archived_worlds"), folderName)
        }
        return resolveWorldDirectory(folderName)
    }

    fun getWorldCreationDirectory(worldData: WorldData): File =
            preferredActiveWorldDirectory(getWorldFolderName(worldData))

    fun getWorldCreationDirectory(folderName: String): File =
            preferredActiveWorldDirectory(folderName)

    fun resolveWorldDirectory(folderName: String): File =
            resolveExistingWorldDirectory(folderName)
                    ?: throw IllegalStateException("Existing world directory is not uniquely resolvable: $folderName")

    private fun resolveExistingWorldDirectory(folderName: String): File? =
            plugin.worldDirectoryResolver.inspect(folderName)?.existingPath?.toFile()

    private fun preferredActiveWorldDirectory(folderName: String): File {
        return com.awabi2048.ccsystem.CCSystem.getAPI()
                .getWorldDirectoryService()
                .creationDirectory(NamespacedKey.minecraft(folderName))
                .toFile()
    }

    private fun worldFolderExists(folderName: String): Boolean =
            plugin.worldDirectoryResolver.inspect(folderName)?.state
                    ?.let { it != WorldDirectoryState.MISSING } == true

    private fun generateUniqueWorldUuid(): UUID {
        val usedWarpIds = repository.findAll().mapTo(mutableSetOf()) { WorldWarpId.of(it.uuid) }
        repeat(100) {
            val uuid = UUID.randomUUID()
            val folderName = "my_world.$uuid"
            if (WorldWarpId.of(uuid) !in usedWarpIds &&
                    Bukkit.getWorld(folderName) == null &&
                    !worldFolderExists(folderName)
            ) {
                return uuid
            }
        }
        error("Unable to allocate unique MyWorld warp id after 100 attempts")
    }

    /** プレイヤーを指定されたワールドにテレポートさせる */
    fun teleportToWorld(
            player: Player,
            worldUuid: UUID,
            location: Location? = null,
            runMacro: Boolean = true,
            reason: MwmWarpReason = MwmWarpReason.DIRECT,
            closeInventoryOnLoad: Boolean = true,
            afterTeleported: (() -> Unit)? = null
    ) {
        val worldData = repository.findByUuid(worldUuid) ?: return
        val folderName = getWorldFolderName(worldData)
        val needsLoad = Bukkit.getWorld(folderName) == null
        val sessionAtStart = plugin.settingsSessionManager.getSession(player)
        plugin.logWorldSettingsDebug(
                "warp=start player=${player.name}/${player.uniqueId} world=$worldUuid folder=$folderName " +
                        "needsLoad=$needsLoad closeOnLoad=$closeInventoryOnLoad reason=$reason " +
                        "session=${sessionAtStart?.action ?: "none"}/${sessionAtStart?.worldUuid ?: "none"} " +
                        "holder=${player.openInventory.topInventory.holder?.javaClass?.name ?: "none"}"
        )

        if (needsLoad) {
            if (closeInventoryOnLoad) {
                player.closeInventory()
            }
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.world_loading"))
            if (!loadWorld(worldUuid)) {
                player.sendMessage(plugin.languageManager.getMessage(player, "error.world_load_failed"))
                return
            }
        }

        val world = Bukkit.getWorld(folderName)
        if (world == null) {
            player.sendMessage(plugin.languageManager.getMessage(player, "error.world_load_failed"))
            return
        }

        plugin.worldEnvironmentService.applyAll(world, worldData)

        if (!validateSeedSpawn(player, worldData, world)) return

        // スポーン地点の決定
        val selectedLoc =
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

        // 再ロード後の保存済みLocationは停止済みの古いWorldを保持している場合があるため、現在のWorldへ差し替える。
        // 保存済みLocationは古いWorld参照を持つことがあるため、現在ロード済みのWorldへ差し替える。
        val selectedTargetLoc = selectedLoc.clone()
        selectedTargetLoc.world = world

        // スポーン/ポータル/個別指定がボーダー外に出ている場合でも、保存値は変えずに中央の安全地点へ逃がす。
        val targetLoc = resolveBorderSafeWarpLocation(world, selectedTargetLoc)

        val executeTeleport = Runnable {
            if (!player.isOnline) {
                return@Runnable
            }

            player.teleport(targetLoc)
            val sessionAfterTeleport = plugin.settingsSessionManager.getSession(player)
            plugin.logWorldSettingsDebug(
                    "warp=teleported player=${player.name}/${player.uniqueId} world=$worldUuid " +
                            "actualWorld=${player.world.name} success=${player.world.uid == world.uid} " +
                            "session=${sessionAfterTeleport?.action ?: "none"}/${sessionAfterTeleport?.worldUuid ?: "none"} " +
                            "transition=${sessionAfterTeleport?.isGuiTransition ?: false} " +
                            "holder=${player.openInventory.topInventory.holder?.javaClass?.name ?: "none"}"
            )

            plugin.soundManager.playTeleportSound(player)

            Bukkit.getPluginManager().callEvent(
                    MwmWorldWarpedEvent(
                            playerUuid = player.uniqueId,
                            playerName = player.name,
                            worldUuid = worldUuid,
                            toLocation = targetLoc.clone(),
                            reason = reason
                    )
            )

            // マクロ実行
            if (runMacro) {
                plugin.macroManager.execute(
                        "on_join",
                        mapOf("player" to player.name, "world_uuid" to worldUuid.toString())
                )
            }

            afterTeleported?.invoke()
            val sessionAfterCallback = plugin.settingsSessionManager.getSession(player)
            plugin.logWorldSettingsDebug(
                    "warp=callback_complete player=${player.name}/${player.uniqueId} world=$worldUuid " +
                            "session=${sessionAfterCallback?.action ?: "none"}/${sessionAfterCallback?.worldUuid ?: "none"} " +
                            "holder=${player.openInventory.topInventory.holder?.javaClass?.name ?: "none"}"
            )
        }

        if (needsLoad) {
            val waitTicks = plugin.config.getLong("warp.load_wait_ticks", 10L).coerceAtLeast(0L)
            Bukkit.getScheduler().runTaskLater(plugin, executeTeleport, waitTicks)
            return
        }

        executeTeleport.run()

        // 最終アクセス日時の更新などはaccessControlListener等で行うのが良いかもしれないが、
        // 明示的にここで更新する手もある。
    }

    /** プレイヤーの既存のワールドデータをすべて削除してリセットする（デバッグ用・管理者用） */
    fun teleportToWorldKeepingInventory(
            player: Player,
            worldUuid: UUID,
            reason: MwmWarpReason,
            afterTeleported: Runnable?
    ) {
        // 外部アドオンからは Kotlin の Function0 をシグネチャに出さず、JAR間のクラスローダ衝突を避ける。
        teleportToWorld(
                player = player,
                worldUuid = worldUuid,
                location = null,
                runMacro = true,
                reason = reason,
                closeInventoryOnLoad = false,
                afterTeleported = { afterTeleported?.run() }
        )
    }

    private fun resolveBorderSafeWarpLocation(world: org.bukkit.World, target: Location): Location {
        if (world.worldBorder.isInside(target)) {
            return target
        }

        return findSafeBorderCenterLocation(world) ?: run {
            val center = world.worldBorder.center
            val x = Math.round(center.x).toInt()
            val z = Math.round(center.z).toInt()
            val y = (world.getHighestBlockYAt(x, z) + 1).coerceIn(world.minHeight + 1, world.maxHeight - 2)
            Location(world, x + 0.5, y.toDouble(), z + 0.5)
        }
    }

    private fun findSafeBorderCenterLocation(world: org.bukkit.World): Location? {
        val center = world.worldBorder.center
        val centerX = Math.round(center.x).toInt()
        val centerZ = Math.round(center.z).toInt()

        for (radius in 0..12) {
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    if (radius != 0 && kotlin.math.abs(dx) != radius && kotlin.math.abs(dz) != radius) continue
                    val x = centerX + dx
                    val z = centerZ + dz
                    val safe = findSafeSurfaceLocation(world, x, z) ?: continue
                    if (world.worldBorder.isInside(safe)) {
                        return safe
                    }
                }
            }
        }

        return null
    }

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
        val targetFile = preferredActiveWorldDirectory(folderName)
        val activeResolution = plugin.worldDirectoryResolver.inspect(folderName)
        if (activeResolution == null || activeResolution.state != WorldDirectoryState.MISSING) {
            plugin.logger.warning(
                "World restore rejected because the active directory state is " +
                        "${activeResolution?.state ?: "UNSAFE"}: $folderName"
            )
            future.complete(false)
            return future
        }

        if (!isSafeArchiveDirectory(archiveFolder, archivedFile)) {
            plugin.logger.warning("World restore rejected because the archived directory is missing or unsafe: $archivedFile")
            future.complete(false)
            return future
        }
        targetFile.parentFile?.mkdirs()
        if (!archivedFile.renameTo(targetFile)) {
            plugin.logger.severe("Failed to move world directory from archive: $folderName")
            future.complete(false)
            return future
        }

        worldData.isArchived = false
        // 期限をリセット
        val initialDays = plugin.config.getLong("default_expiration.initial_days", 90)
        worldData.expireDate = java.time.LocalDate.now().plusDays(initialDays).toString()
        
        repository.save(worldData)
        future.complete(true)
        return future
    }

    private fun isSafeArchiveDirectory(archiveFolder: File, archivedFile: File): Boolean {
        val archiveRoot = archiveFolder.toPath().toAbsolutePath().normalize()
        val archivedPath = archivedFile.toPath().toAbsolutePath().normalize()
        if (!archivedPath.startsWith(archiveRoot) ||
            !Files.isDirectory(archiveRoot, LinkOption.NOFOLLOW_LINKS) ||
            !Files.isDirectory(archivedPath, LinkOption.NOFOLLOW_LINKS)
        ) {
            return false
        }

        var current = archivedPath
        while (true) {
            if (Files.isSymbolicLink(current)) return false
            if (current == archiveRoot) return true
            current = current.parent ?: return false
        }
    }

    /** ワールドを完全に削除する */
    fun deleteWorld(worldUuid: UUID, caller: CommandSender? = null): java.util.concurrent.CompletableFuture<Boolean> {
        val future = java.util.concurrent.CompletableFuture<Boolean>()
        val guardTarget = repository.findByUuid(worldUuid)
        if (guardTarget == null) {
            future.complete(false)
            return future
        }
        val guardRejection =
                MyWorldManagerApi.getWorldDeleteGuards()
                        .firstNotNullOfOrNull { guard -> guard.canDelete(guardTarget, caller) }
        if (guardRejection != null) {
            plugin.logger.info(
                    "World deletion rejected by registered guard for ${guardTarget.uuid}: $guardRejection"
            )
            caller?.sendMessage(guardRejection)
            future.complete(false)
            return future
        }
        unloadWorldAfterEvacuation(worldUuid, false).thenAccept { unloaded ->
            if (!unloaded) {
                future.complete(false)
                return@thenAccept
            }

            val worldData = repository.findByUuid(worldUuid)
            if (worldData == null) {
                future.complete(false)
                return@thenAccept
            }

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
                resolveExistingWorldDirectory(folderName) ?: run {
                    future.complete(false)
                    return@thenAccept
                }
            }

            if (folder.exists() && !folder.deleteRecursively()) {
                future.complete(false)
                return@thenAccept
            }

            repository.delete(worldUuid)
            reduceOwnerSlotOnDeleteIfEnabled(worldData.owner)
            Bukkit.getPluginManager().callEvent(
                    MwmWorldDeletedEvent(
                            worldUuid = worldUuid,
                            worldName = folderName,
                            ownerUuid = worldData.owner,
                            participantUuids = (worldData.members + worldData.moderators + worldData.owner).toSet(),
                            refundPoints = refund,
                            wasArchived = worldData.isArchived
                    )
            )
            plugin.macroManager.execute(
                    "on_world_delete",
                    mapOf("world_uuid" to worldUuid.toString())
            )
            future.complete(true)
        }
        return future
    }

    fun deleteWorldForMaintenance(worldUuid: UUID): java.util.concurrent.CompletableFuture<Boolean> {
        val future = java.util.concurrent.CompletableFuture<Boolean>()
        unloadWorldAfterEvacuation(worldUuid, false).thenAccept { unloaded ->
            if (!unloaded) {
                future.complete(false)
                return@thenAccept
            }

            val worldData = repository.findByUuid(worldUuid)
            if (worldData == null) {
                future.complete(false)
                return@thenAccept
            }

            val folderName = getWorldFolderName(worldData)
            val archiveFolder = File(plugin.dataFolder.parentFile.parentFile, "archived_worlds")
            val folder = if (worldData.isArchived) {
                File(archiveFolder, folderName)
            } else {
                resolveExistingWorldDirectory(folderName) ?: run {
                    future.complete(false)
                    return@thenAccept
                }
            }

            if (folder.exists() && !folder.deleteRecursively()) {
                future.complete(false)
                return@thenAccept
            }

            repository.delete(worldUuid)
            reduceOwnerSlotOnDeleteIfEnabled(worldData.owner)
            Bukkit.getPluginManager().callEvent(
                    MwmWorldDeletedEvent(
                            worldUuid = worldUuid,
                            worldName = folderName,
                            ownerUuid = worldData.owner,
                            participantUuids = (worldData.members + worldData.moderators + worldData.owner).toSet(),
                            refundPoints = 0,
                            wasArchived = worldData.isArchived
                    )
            )
            plugin.macroManager.execute(
                    "on_world_delete",
                    mapOf("world_uuid" to worldUuid.toString())
            )
            future.complete(true)
        }
        return future
    }

    private fun reduceOwnerSlotOnDeleteIfEnabled(ownerUuid: UUID) {
        if (!WorldRuntimePolicies.reduceOwnerSlotOnDelete(plugin.config)) {
            return
        }

        val ownerStats = playerStatsRepository.findByUuid(ownerUuid)
        if (ownerStats.unlockedWorldSlot <= 0) {
            return
        }

        ownerStats.unlockedWorldSlot--
        playerStatsRepository.save(ownerStats)
    }

    /** ワールドをアーカイブする */
    fun archiveWorld(
            worldUuid: UUID,
            isAutomaticTransition: Boolean = false
    ): java.util.concurrent.CompletableFuture<Boolean> {
        val future = java.util.concurrent.CompletableFuture<Boolean>()
        val worldData = repository.findByUuid(worldUuid)
        if (worldData == null) {
            future.complete(false)
            return future
        }

        unloadWorldAfterEvacuation(worldUuid, true).thenAccept { unloaded ->
            if (!unloaded) {
                future.complete(false)
                return@thenAccept
            }

            val folderName = getWorldFolderName(worldData)
            val archiveFolder = File(plugin.dataFolder.parentFile.parentFile, "archived_worlds")
            if (!archiveFolder.exists()) archiveFolder.mkdirs()

            val sourceFile = resolveExistingWorldDirectory(folderName) ?: run {
                future.complete(false)
                return@thenAccept
            }
            val targetFile = File(archiveFolder, folderName)

            if (sourceFile.exists() && !sourceFile.renameTo(targetFile)) {
                plugin.logger.severe("Failed to move world directory to archive: $folderName")
                future.complete(false)
                return@thenAccept
            }

            worldData.isArchived = true
            worldData.archivedAt = java.time.LocalDate.now().toString()
            worldData.archiveTransitionType = if (isAutomaticTransition) "AUTO" else "MANUAL"
            repository.save(worldData)
            future.complete(true)
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
    fun updateDailyData(): Map<String, Int> {
        val worlds = repository.findAll()
        var updatedCount = 0
        var archivedCount = 0
        
        val today = java.time.LocalDate.now()
        val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")

        for (worldData in worlds) {
            // 訪問者統計の更新
            val visitors = worldData.recentVisitors
            // サイズ調整（足りない場合は0で埋める、多すぎる場合は切り詰める）
            while (visitors.size < 7) visitors.add(0, 0)
            if (visitors.size > 7) {
                val sub = visitors.subList(visitors.size - 7, visitors.size)
                visitors.clear()
                visitors.addAll(sub)
            }

            // 6 -> 破棄, 5 -> 6, ..., 0 -> 1
            for (i in 6 downTo 1) {
                visitors[i] = visitors[i - 1]
            }
            // 今日のカウントをリセット
            visitors[0] = 0
            
            repository.save(worldData)
            updatedCount++

            // 期限切れワールドのアーカイブ
            if (!worldData.isArchived) {
                try {
                    val expireDate = java.time.LocalDate.parse(worldData.expireDate, dateFormatter)
                    if (expireDate.isBefore(today)) {
                        archiveWorld(worldData.uuid, true)
                        archivedCount++
                    }
                } catch (e: Exception) {
                    plugin.logger.warning("Failed to parse expireDate for world ${worldData.uuid}: ${worldData.expireDate}")
                }
            }
        }

        // マクロ実行
        plugin.macroManager.execute("on_daily_maintenance", emptyMap())

        Bukkit.getPluginManager().callEvent(
                MwmDailyMaintenanceCompletedEvent(
                        executedDate = today,
                        updatedCount = updatedCount,
                        archivedCount = archivedCount,
                        totalWorlds = worlds.size
                )
        )

        return mapOf(
            "updated" to updatedCount,
            "archived" to archivedCount
        )
    }

    /** 避難先ロケーションを取得する ロビーワールドが見つからない場合はメインワールドのスポーン地点 */
    fun getEvacuationLocation(): Location {
        MyWorldManagerApi.getEvacuationLocationOverride()?.let { return it }

        val lobbyName = plugin.config.getString("lobby_world_name", "world")
        var lobby = Bukkit.getWorld(lobbyName!!)
        if (lobby == null) {
            lobby = Bukkit.getWorlds().firstOrNull()
        }
        return lobby?.spawnLocation ?: Bukkit.getWorlds()[0].spawnLocation
    }
}
