package me.awabi2048.myworldmanager.command

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.PortalData
import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.service.WorldService
import me.awabi2048.myworldmanager.session.CreationSessionManager
import me.awabi2048.myworldmanager.util.CustomItem
import me.awabi2048.myworldmanager.util.ItemTag
import me.awabi2048.myworldmanager.util.PortalItemUtil
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import me.awabi2048.myworldmanager.util.PermissionManager
import java.util.UUID

/**
 * MyWorldManagerの主要なコマンドを受け付けるクラス
 */
class WorldCommand(
    private val worldService: WorldService,
    private val sessionManager: CreationSessionManager
) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!PermissionManager.checkPermission(sender, PermissionManager.ADMIN)) {
            PermissionManager.sendNoPermissionMessage(sender)
            return true
        }
        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)

        if (args.isEmpty()) {
             plugin.adminCommandGui.open(sender as Player)
             return true
        }

        when (args[0].lowercase()) {
            "create" -> {
                val lang = plugin.languageManager
                if (!PermissionManager.checkPermission(sender, PermissionManager.ADMIN)) {
                    PermissionManager.sendNoPermissionMessage(sender)
                    return true
                }
                if (args.size < 2) {
                    sender.sendMessage(lang.getMessage("messages.usage_create"))
                    return true
                }
                val targetPlayer = Bukkit.getPlayer(args[1])
                if (targetPlayer == null || !targetPlayer.isOnline) {
                    sender.sendMessage(lang.getMessage("general.player_not_found"))
                    return true
                }

                // テンプレートのディレクトリ存在チェック
                val missingTemplates = plugin.templateRepository.missingTemplates
                if (missingTemplates.isNotEmpty()) {
                    sender.sendMessage("§c[Error] 以下のテンプレートワールドのディレクトリが見つからないため、開始できません:")
                    missingTemplates.forEach { sender.sendMessage("§c - $it") }
                    return true
                }

                // テンプレートワールドのチャンクを事前読み込み
                plugin.worldService.preloadTemplateChunks()
                
                // ウィザード開始
                sessionManager.startSession(targetPlayer.uniqueId)
                val cancelWord = plugin.config.getString("creation.cancel_word", "cancel") ?: "cancel"
                val cancelInfo = lang.getMessage(targetPlayer, "messages.chat_input_cancel_hint", mapOf("word" to cancelWord))
                
                targetPlayer.sendMessage(lang.getMessage("messages.wizard_start"))
                targetPlayer.sendMessage(lang.getMessage("messages.wizard_name_prompt") + " " + cancelInfo)
                sender.sendMessage(lang.getMessage(sender as? Player, "messages.wizard_started_for", mapOf("player" to targetPlayer.name)))
                return true
            }

            "reload" -> {
                val lang = plugin.languageManager
                if (!PermissionManager.checkPermission(sender, PermissionManager.ADMIN)) {
                    PermissionManager.sendNoPermissionMessage(sender)
                    return true
                }
                plugin.reloadSystem()
                sender.sendMessage(lang.getMessage(sender as? Player, "messages.reload_success"))
                return true
            }
            "update-day" -> {
                val lang = plugin.languageManager
                if (sender !is org.bukkit.command.ConsoleCommandSender) {
                    sender.sendMessage(lang.getMessage("messages.console_only"))
                    return true
                }
                plugin.worldService.updateDailyData()
                sender.sendMessage(lang.getMessage(sender as? Player, "messages.daily_update_success"))
                return true
            }
            "stats" -> {
                val lang = plugin.languageManager
                if (sender !is org.bukkit.command.ConsoleCommandSender && !PermissionManager.checkPermission(sender, PermissionManager.ADMIN)) {
                    PermissionManager.sendNoPermissionMessage(sender)
                    return true
                }
                if (args.size < 4) {
                    sender.sendMessage(lang.getMessage(sender as? Player, "messages.usage_stats"))
                    sender.sendMessage(lang.getMessage(sender as? Player, "messages.usage_stats_fields"))
                    return true
                }

                val targetOffline = Bukkit.getOfflinePlayer(args[1])
                val field = args[2].lowercase()
                val action = args[3].lowercase()
                val value = if (args.size >= 5) args[4].toIntOrNull() else null

                val stats = plugin.playerStatsRepository.findByUuid(targetOffline.uniqueId)
                
                when (action) {
                    "get" -> {
                        val current = when (field) {
                            "points" -> stats.worldPoint
                            // warp-slots removed
                            "world-slots" -> stats.unlockedWorldSlot
                            else -> {
                                sender.sendMessage(lang.getMessage(sender as? Player, "messages.invalid_field"))
                                return true
                            }
                        }
                        val displayValue = if (field == "points") "§6🛖 §e$current" else "§f$current"
                        sender.sendMessage(lang.getMessage(sender as? Player, "messages.stats_get", mapOf("player" to (targetOffline.name ?: "不明"), "key" to field, "value" to displayValue)))
                    }
                    "set" -> {
                        if (value == null) { sender.sendMessage(lang.getMessage(sender as? Player, "messages.value_required")); return true }
                        if (value < 0) { sender.sendMessage(lang.getMessage(sender as? Player, "messages.value_negative")); return true }
                        when (field) {
                            "points" -> stats.worldPoint = value
                            // warp-slots removed
                            "world-slots" -> stats.unlockedWorldSlot = value
                            else -> { sender.sendMessage(lang.getMessage(sender as? Player, "messages.invalid_field")); return true }
                        }
                        plugin.playerStatsRepository.save(stats)
                        val displayValue = if (field == "points") "§6🛖 §e$value" else "§f$value"
                        sender.sendMessage(lang.getMessage(sender as? Player, "messages.stats_set", mapOf("player" to (targetOffline.name ?: "不明"), "key" to field, "value" to displayValue)))
                    }
                    "add" -> {
                        if (value == null) { sender.sendMessage(lang.getMessage(sender as? Player, "messages.value_required")); return true }
                        when (field) {
                            "points" -> stats.worldPoint += value
                            // warp-slots removed
                            "world-slots" -> stats.unlockedWorldSlot += value
                            else -> { sender.sendMessage(lang.getMessage(sender as? Player, "messages.invalid_field")); return true }
                        }
                        plugin.playerStatsRepository.save(stats)
                        val displayValue = if (field == "points") "§6🛖 §e$value" else "§f$value"
                        sender.sendMessage(lang.getMessage(sender as? Player, "messages.stats_add", mapOf("player" to (targetOffline.name ?: "不明"), "key" to field, "value" to displayValue)))
                    }
                    "remove" -> {
                        if (value == null) { sender.sendMessage(lang.getMessage(sender as? Player, "messages.value_required")); return true }
                        val current = when (field) {
                            "points" -> stats.worldPoint
                            // warp-slots removed
                            "world-slots" -> stats.unlockedWorldSlot
                            else -> { sender.sendMessage(lang.getMessage(sender as? Player, "messages.invalid_field")); return true }
                        }
                        if (current < value) {
                            sender.sendMessage(lang.getMessage(sender as? Player, "messages.stats_remove_error", mapOf("value" to current)))
                            return true
                        }
                        when (field) {
                            "points" -> stats.worldPoint -= value
                            // warp-slots removed
                            "world-slots" -> stats.unlockedWorldSlot -= value
                        }
                        plugin.playerStatsRepository.save(stats)
                        val displayValue = if (field == "points") "§6🛖 §e$value" else "§f$value"
                        sender.sendMessage(lang.getMessage(sender as? Player, "messages.stats_remove", mapOf("player" to (targetOffline.name ?: "不明"), "key" to field, "value" to displayValue)))
                    }
                    else -> sender.sendMessage(lang.getMessage(sender as? Player, "messages.invalid_action"))
                }
                return true
            }
            "give" -> {
                val lang = plugin.languageManager
                if (sender !is org.bukkit.command.ConsoleCommandSender && !PermissionManager.checkPermission(sender, PermissionManager.ADMIN)) {
                    PermissionManager.sendNoPermissionMessage(sender)
                    return true
                }
                if (args.size < 3) {
                    sender.sendMessage(lang.getMessage(sender as? Player, "messages.usage_give"))
                    return true
                }
                val targetPlayer = Bukkit.getPlayer(args[1])
                if (targetPlayer == null || !targetPlayer.isOnline) {
                    sender.sendMessage(lang.getMessage(sender as? Player, "general.player_not_found"))
                    return true
                }
                val customItemId = args[2]
                val customItem = CustomItem.fromId(customItemId)
                if (customItem == null) {
                    sender.sendMessage(lang.getMessage(sender as? Player, "messages.invalid_item_id"))
                    return true
                }
                val amount = if (args.size >= 4) args[3].toIntOrNull() ?: 1 else 1
                
                // Handle biome for bottled_biome_air
                val item = if (customItem == CustomItem.BOTTLED_BIOME_AIR && args.size >= 5) {
                    customItem.createWithBiome(lang, targetPlayer, args[4])
                } else {
                    customItem.create(lang, targetPlayer)
                }
                item.amount = amount
                
                targetPlayer.inventory.addItem(item)
                val displayName = item.itemMeta?.displayName ?: customItem.id
                sender.sendMessage(lang.getMessage(sender as? Player, "messages.give_success", mapOf("player" to targetPlayer.name, "item" to displayName, "amount" to amount)))
                return true
            }
            "migrate-worlds" -> {
                val lang = plugin.languageManager
                if (sender !is org.bukkit.command.ConsoleCommandSender) {
                    sender.sendMessage(lang.getMessage("messages.console_only"))
                    return true
                }
                if (!plugin.config.getBoolean("migration.enable_world_migration", false)) {
                    sender.sendMessage(lang.getMessage("messages.migration_disabled", mapOf("config_key" to "migration.enable_world_migration")))
                    return true
                }
                performMigration(sender)
                plugin.config.set("migration.enable_world_migration", false)
                plugin.saveConfig()
                return true
            }
            "migrate-players" -> {
                val lang = plugin.languageManager
                if (sender !is org.bukkit.command.ConsoleCommandSender) {
                    sender.sendMessage(lang.getMessage("messages.console_only"))
                    return true
                }
                if (!plugin.config.getBoolean("migration.enable_player_migration", false)) {
                    sender.sendMessage(lang.getMessage("messages.migration_disabled", mapOf("config_key" to "migration.enable_player_migration")))
                    return true
                }
                performPlayerMigration(sender)
                plugin.config.set("migration.enable_player_migration", false)
                plugin.saveConfig()
                return true
            }
            "migrate-portals" -> {
                val lang = plugin.languageManager
                if (sender !is org.bukkit.command.ConsoleCommandSender) {
                    sender.sendMessage(lang.getMessage("messages.console_only"))
                    return true
                }
                if (!plugin.config.getBoolean("migration.enable_portal_migration", false)) {
                    sender.sendMessage(lang.getMessage("messages.migration_disabled", mapOf("config_key" to "migration.enable_portal_migration")))
                    return true
                }
                performPortalMigration(sender)
                plugin.config.set("migration.enable_portal_migration", false)
                plugin.saveConfig()
                return true
            }
            else -> {
                sender.sendMessage(plugin.languageManager.getMessage("messages.unknown_subcommand"))
                return true
            }
        }
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        val list = mutableListOf<String>()
        if (!PermissionManager.checkPermission(sender, PermissionManager.ADMIN)) return emptyList()
        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)

        when (args.size) {
            1 -> {
                list.addAll(listOf("create", "reload", "stats", "give"))
                if (sender is org.bukkit.command.ConsoleCommandSender) {
                    list.add("update-day")
                    list.addAll(listOf("migrate-worlds", "migrate-players", "migrate-portals"))
                }
            }
            2 -> {
                val sub = args[0].lowercase()
                if (sub == "stats" || sub == "give" || sub == "create") {
                    list.addAll(Bukkit.getOnlinePlayers().map { it.name })
                } else if (sub == "portals") {
                    list.add("bind")
                } else if (sub == "export") {
                    val worlds: List<WorldData> = plugin.worldConfigRepository.findAll()
                    list.addAll(worlds.map { it.uuid.toString() })
                }
            }
            3 -> {
                val sub = args[0].lowercase()
                if (sub == "stats") {
                    list.addAll(listOf("points", "warp-slots", "world-slots"))
                } else if (sub == "give") {
                    list.addAll(CustomItem.values().map { it.id })
                } else if (sub == "portals" && args[1].lowercase() == "bind") {
                    val targets = plugin.config.getConfigurationSection("portal_targets")?.getKeys(false) ?: emptySet()
                    list.addAll(targets)
                }
            }
            4 -> {
                if (args[0].lowercase() == "stats") {
                    list.addAll(listOf("get", "set", "add", "remove"))
                }
            }
        }
        return list.filter { it.lowercase().startsWith(args.last().lowercase()) }
    }

    private fun performMigration(sender: CommandSender) {
        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
        val lang = plugin.languageManager
        val file = java.io.File(plugin.dataFolder, "world_data.yml")
        if (!file.exists()) {
            sender.sendMessage(lang.getMessage("messages.migration_file_not_found", mapOf("file" to "world_data.yml")))
            return
        }

        val config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file)
        val initialDays = plugin.config.getInt("default_expiration.initial_days", 7)
        val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")

        var count = 0
        for (key in config.getKeys(false)) {
            try {
                val uuid = UUID.fromString(key)
                val section = config.getConfigurationSection(key) ?: continue

                val name = section.getString("name", "Unknown")!!
                val description = section.getString("description", "")!!
                val iconStr = section.getString("icon", "GRASS_BLOCK")!!
                val icon = org.bukkit.Material.matchMaterial(iconStr) ?: org.bukkit.Material.GRASS_BLOCK
                val sourceWorld = section.getString("source_world", "default")!!
                
                val isArchived = section.getString("activity_state") == "ARCHIVED"
                val worldName = "my_world.$uuid"
                
                // フォルダ存在確認
                val archiveFolder = java.io.File(plugin.dataFolder.parentFile.parentFile, "archived_worlds")
                val worldFolder = if (isArchived) {
                    java.io.File(archiveFolder, worldName)
                } else {
                    java.io.File(Bukkit.getWorldContainer(), worldName)
                }

                if (!worldFolder.exists() || !worldFolder.isDirectory) {
                    val errorMsg = lang.getMessage("messages.migration_folder_not_found", mapOf("path" to worldName, "id" to key))
                    sender.sendMessage(errorMsg)
                    plugin.logger.severe(errorMsg)
                    continue
                }
                
                // 期限計算
                val lastUpdatedStr = section.getString("last_updated", "2026-01-12")!!
                val lastUpdated = try {
                    java.time.LocalDate.parse(lastUpdatedStr.replace("'", ""))
                } catch (e: Exception) {
                    java.time.LocalDate.now()
                }
                val expireDate = lastUpdated.plusDays(initialDays.toLong()).format(dateFormatter)

                val ownerStr = section.getString("owner") ?: continue
                val owner = UUID.fromString(ownerStr)
                
                val members = mutableListOf<UUID>()
                val moderators = mutableListOf<UUID>()
                val memberSection = section.getConfigurationSection("member")
                if (memberSection != null) {
                    for (mUuidStr in memberSection.getKeys(false)) {
                        try {
                            val mUuid = UUID.fromString(mUuidStr)
                            val role = memberSection.getString(mUuidStr)?.uppercase() ?: "MEMBER"
                            when (role) {
                                "OWNER", "MODERATOR" -> moderators.add(mUuid)
                                "MEMBER" -> {
                                    if (mUuid != owner) members.add(mUuid)
                                }
                            }
                        } catch (e: Exception) {}
                    }
                }

                val publishLevel = when (section.getString("publish_level", "CLOSE")?.uppercase()) {
                    "OPEN" -> PublishLevel.FRIEND
                    "PUBLIC" -> PublishLevel.PUBLIC
                    else -> PublishLevel.PRIVATE
                }

                
                fun parseLoc(str: String?): org.bukkit.Location? {
                    if (str == null) return null
                    val clean = str.replace("(", "").replace(")", "")
                    val coords = clean.split(",")
                    if (coords.size < 3) return null
                    val x = coords[0].trim().toDoubleOrNull() ?: return null
                    val y = coords[1].trim().toDoubleOrNull() ?: return null
                    val z = coords[2].trim().toDoubleOrNull() ?: return null
                    return org.bukkit.Location(Bukkit.getWorld(worldName), x, y, z)
                }

                val spawnPosGuest = parseLoc(section.getString("spawn_pos_guest"))
                val spawnPosMember = parseLoc(section.getString("spawn_pos_member"))
                val borderCenterPos = parseLoc(section.getString("border_center_pos"))
                
                val expansionLevel = section.getInt("border_expansion_level", 0)

                // 累積ポイント計算
                var points = plugin.config.getInt("creation_cost.template", 0)
                for (i in 1..expansionLevel) {
                    points += plugin.config.getInt("expansion.costs.$i", 100)
                }

                val createdAtRaw = section.getString("created_at")
                val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                val createdAt = if (createdAtRaw != null) {
                    if (createdAtRaw.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                        "$createdAtRaw 00:00:00"
                    } else {
                        createdAtRaw
                    }
                } else {
                    java.time.LocalDateTime.now().format(formatter)
                }

                val worldData = WorldData(
                    uuid = uuid,
                    name = name,
                    description = description,
                    icon = icon,
                    sourceWorld = sourceWorld,
                    expireDate = expireDate,
                    owner = owner,
                    members = members,
                    moderators = moderators,
                    publishLevel = publishLevel,
                    spawnPosGuest = spawnPosGuest,
                    spawnPosMember = spawnPosMember,
                    borderCenterPos = borderCenterPos,
                    borderExpansionLevel = expansionLevel,
                    isArchived = isArchived,
                    cumulativePoints = points,
                    createdAt = createdAt
                )

                plugin.worldConfigRepository.save(worldData)
                count++
            } catch (e: Exception) {
                sender.sendMessage(lang.getMessage("messages.migration_error", mapOf("error" to key)))
                e.printStackTrace()
            }
        }
        sender.sendMessage(lang.getMessage("messages.migration_success", mapOf("count" to count)))
        plugin.worldConfigRepository.loadAll()
    }
    
    private fun processArchiveQueue(sender: CommandSender, targets: List<WorldData>, index: Int) {
        if (index >= targets.size) {
            val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
            sender.sendMessage(plugin.languageManager.getMessage("messages.migration_archive_complete", mapOf("count" to targets.size)))
            return
        }
        
        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
        val lang = plugin.languageManager
        val worldData = targets[index]
        
        plugin.worldService.archiveWorld(worldData.uuid).thenAccept { success ->
            if (success) {
                sender.sendMessage(lang.getMessage("messages.migration_archive_progress", mapOf("current" to (index + 1), "total" to targets.size, "world" to worldData.name)))
            }
            // 次のワールドを処理
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                processArchiveQueue(sender, targets, index + 1)
            }, 20L) // 1秒後に次を処理
        }
    }

    private fun performPlayerMigration(sender: CommandSender) {
        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
        val lang = plugin.languageManager
        val file = java.io.File(plugin.dataFolder, "player_data.yml")
        if (!file.exists()) {
            sender.sendMessage(lang.getMessage("messages.migration_file_not_found", mapOf("file" to "player_data.yml")))
            return
        }

        val config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file)
        val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val nowStr = java.time.LocalDate.now().format(dateFormatter)

        var count = 0
        for (key in config.getKeys(false)) {
            try {
                val uuid = UUID.fromString(key)
                val section = config.getConfigurationSection(key) ?: continue

                val worldPoint = section.getInt("world_point")
                // unlockedWarpSlot removed
                val unlockedWorldSlot = section.getInt("unlocked_world_slot")
                val warpShortcuts: List<String> = section.getStringList("warp_shortcuts")

                val stats = plugin.playerStatsRepository.findByUuid(uuid)
                stats.worldPoint = worldPoint
                // unlockedWarpSlot removed
                stats.unlockedWorldSlot = unlockedWorldSlot
                
                warpShortcuts.forEach { wUuidStr: String ->
                    try {
                        val wUuid = UUID.fromString(wUuidStr)
                        if (!stats.favoriteWorlds.containsKey(wUuid)) {
                            stats.favoriteWorlds[wUuid] = nowStr
                        }
                    } catch (e: Exception) {}
                }

                plugin.playerStatsRepository.save(stats)
                count++
            } catch (e: Exception) {
                sender.sendMessage(lang.getMessage("messages.migration_error", mapOf("error" to key)))
                e.printStackTrace()
            }
        }
        sender.sendMessage(lang.getMessage("messages.migration_success", mapOf("count" to count)))
    }

    private fun performPortalMigration(sender: CommandSender) {
        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
        val lang = plugin.languageManager
        val file = java.io.File(plugin.dataFolder, "portal_data.yml")
        if (!file.exists()) {
            sender.sendMessage(lang.getMessage("messages.migration_file_not_found", mapOf("file" to "portal_data.yml")))
            return
        }

        val config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file)
        var count = 0

        for (key in config.getKeys(false)) {
            try {
                val section = config.getConfigurationSection(key) ?: continue
                val id = try { UUID.fromString(key) } catch (e: Exception) { continue }

                val locSection = section.getConfigurationSection("location") ?: continue
                val worldName = locSection.getString("world") ?: continue
                val world = Bukkit.getWorld(worldName)
                if (world == null) {
                    plugin.logger.warning("Migration warning: World '$worldName' not found for portal $id.")
                    continue
                }
                val x = locSection.getDouble("x")
                val y = locSection.getDouble("y")
                val z = locSection.getDouble("z")
                val location = org.bukkit.Location(world, x, y, z)

                val ownerStr = section.getString("owner") ?: continue
                val ownerUuid = UUID.fromString(ownerStr)

                val destStr = section.getString("destination") ?: continue
                val worldUuid = UUID.fromString(destStr)

                val showText = section.getBoolean("display_text", true)
                
                // 色のパース
                val colorStr = section.getString("color", "AQUA")!!
                var color: org.bukkit.Color = org.bukkit.Color.AQUA
                try {
                    // 名前からColor定数を取得を試みる
                    val field = org.bukkit.Color::class.java.getDeclaredField(colorStr.uppercase())
                    color = field.get(null) as org.bukkit.Color
                } catch (e: Exception) {
                    try {
                        // 失敗したらDyeColor経由で試す
                        color = org.bukkit.DyeColor.valueOf(colorStr.uppercase()).fireworkColor
                    } catch (e2: Exception) {
                        // それでもだめならデフォルト
                    }
                }

                val portalData = PortalData(
                    id = id,
                    worldName = worldName,
                    x = x.toInt(),
                    y = y.toInt(),
                    z = z.toInt(),
                    worldUuid = worldUuid,
                    showText = showText,
                    particleColor = color,
                    ownerUuid = ownerUuid
                )

                plugin.portalRepository.addPortal(portalData)
                count++
            } catch (e: Exception) {
                sender.sendMessage(lang.getMessage("messages.migration_error", mapOf("error" to key)))
                e.printStackTrace()
            }
        }
        sender.sendMessage(lang.getMessage("messages.migration_success", mapOf("count" to count)))
    }
}
