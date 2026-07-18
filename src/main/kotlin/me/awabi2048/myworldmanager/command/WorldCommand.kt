package me.awabi2048.myworldmanager.command

import java.util.UUID
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.model.PortalData
import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.service.WorldService
import me.awabi2048.myworldmanager.session.CreationSessionManager
import me.awabi2048.myworldmanager.session.WorldCreationType
import me.awabi2048.myworldmanager.util.PermissionManager
import org.bukkit.Bukkit
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import me.awabi2048.myworldmanager.util.WorldRuntimePolicies
import me.awabi2048.myworldmanager.util.WorldCreationChecks
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

/** MyWorldManagerの主要なコマンドを受け付けるクラス */
class WorldCommand(
        private val worldService: WorldService,
        private val sessionManager: CreationSessionManager
) : CommandExecutor, TabCompleter {

    override fun onCommand(
            sender: CommandSender,
            command: Command,
            label: String,
            args: Array<out String>
    ): Boolean {
        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
        val subCommand = args.firstOrNull()?.lowercase()
        val hasGlobalPermission = PermissionManager.checkPermission(sender, PermissionManager.COMMAND_MWM)

        if (args.isEmpty()) {
            if (!hasGlobalPermission) {
                PermissionManager.sendNoPermissionMessage(sender)
                return true
            }
            if (sender !is Player) {
                sender.sendMessage(plugin.languageManager.getMessage("error.player_only"))
                return true
            }
            plugin.adminCommandGui.open(sender)
            return true
        }

        if (subCommand != null && !isSubCommandEnabled(sender, subCommand, args.toList())) {
            sender.sendMessage(plugin.languageManager.getMessage(sender as? Player, "messages.command_disabled"))
            return true
        }

        if (subCommand == "list") {
            if (!hasGlobalPermission && !PermissionManager.checkAnyPermission(sender, PermissionManager.COMMAND_MWM_LIST, PermissionManager.ADMIN_WORLD_LIST)) {
                PermissionManager.sendNoPermissionMessage(sender)
                return true
            }
            if (sender !is Player) {
                sender.sendMessage(plugin.languageManager.getMessage("error.player_only"))
                return true
            }
            plugin.worldGui.open(sender, page = 0, fromAdminMenu = true)
            return true
        }

        if (!hasGlobalPermission && !hasSubcommandPermission(sender, subCommand)) {
            PermissionManager.sendNoPermissionMessage(sender)
            return true
        }

        when (args[0].lowercase()) {
            "migration" -> {
                val action = args.getOrNull(1)?.lowercase()
                if (action == "list") {
                    val pending = plugin.worldMigrationService.reportPending(sender)
                    if (pending.isEmpty()) sender.sendMessage(plugin.languageManager.getMessage("messages.migration.none_pending"))
                    return true
                }
                val uuid = args.getOrNull(2)?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                if (uuid == null || action !in setOf("approve", "reject")) {
                    sender.sendMessage(plugin.languageManager.getMessage("messages.migration.usage"))
                    return true
                }
                val handled = if (action == "approve") {
                    plugin.worldMigrationService.approveAndLoad(uuid, sender)
                } else {
                    plugin.worldMigrationService.reject(uuid)
                }
                if (!handled) sender.sendMessage(plugin.languageManager.getMessage("messages.migration.not_found"))
                return true
            }
            "create" -> {
                val lang = plugin.languageManager
                if (!hasGlobalPermission && !PermissionManager.checkPermission(sender, PermissionManager.COMMAND_MWM_CREATE)) {
                    PermissionManager.sendNoPermissionMessage(sender)
                    return true
                }
                val handler = MyWorldManagerApi.getCreateCommandHandler()
                if (handler != null) {
                    return handler.handleCreateCommand(sender, args.drop(1))
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

                if (!WorldCreationChecks.check(sender, targetPlayer, me.awabi2048.myworldmanager.api.extension.WorldCreationOperation.NORMAL, null)) {
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

                if (!WorldCreationChecks.checkLimits(plugin, sender, targetPlayer.uniqueId)) return true

                // ウィザード開始
                val session = sessionManager.startSession(targetPlayer.uniqueId)

                // JE は常にダイアログ入力、BE は FormUI 入力
                session.isDialogMode = !plugin.playerPlatformResolver.isBedrock(targetPlayer)
                plugin.creationGui.openTypeSelection(targetPlayer)
                sender.sendMessage(
                        lang.getMessage(
                                sender as? Player,
                                "messages.wizard_started_for",
                                mapOf("player" to targetPlayer.name)
                        )
                )
                return true
            }
            "reload" -> {
                val lang = plugin.languageManager
                if (!hasGlobalPermission && !PermissionManager.checkPermission(sender, PermissionManager.COMMAND_MWM_RELOAD)) {
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
                    sender.sendMessage(lang.getMessage("error.console_only"))
                    return true
                }
                val results = plugin.worldService.updateDailyData(); val updatedCount = results["updated"] ?: 0; val archivedCount = results["archived"] ?: 0
                sender.sendMessage(
                        lang.getMessage(sender as? Player, "messages.daily_update_success", mapOf("updated" to updatedCount, "archived" to archivedCount))
                )
                return true
            }
            "stats" -> {
                val lang = plugin.languageManager
                if (sender !is org.bukkit.command.ConsoleCommandSender &&
                                !hasGlobalPermission &&
                                !PermissionManager.checkPermission(sender, PermissionManager.COMMAND_MWM_STATS)
                ) {
                    PermissionManager.sendNoPermissionMessage(sender)
                    return true
                }
                if (args.size < 4) {
                    sender.sendMessage(lang.getMessage(sender as? Player, "messages.usage_stats"))
                    sender.sendMessage(
                            lang.getMessage(sender as? Player, "messages.usage_stats_fields")
                    )
                    return true
                }

                val targetOffline = PlayerNameUtil.resolveOfflinePlayer(plugin, args[1])
                if (targetOffline == null) {
                    sender.sendMessage(lang.getMessage(sender as? Player, "general.player_not_found"))
                    return true
                }
                val field = args[2].lowercase()
                val action = args[3].lowercase()
                val value = if (args.size >= 5) args[4].toIntOrNull() else null

                val stats = plugin.playerStatsRepository.findByUuid(targetOffline.uniqueId)

                when (action) {
                    "get" -> {
                        val current =
                                when (field) {
                                    "points" -> stats.worldPoint
                                    // warp-slots removed
                                    "world-slots" -> stats.unlockedWorldSlot
                                    else -> {
                                        sender.sendMessage(
                                                lang.getMessage(
                                                        sender as? Player,
                                                        "messages.invalid_field"
                                                )
                                        )
                                        return true
                                    }
                                }
                        val displayValue =
                                if (field == "points") "§6🛖 §e$current" else "§f$current"
                        sender.sendMessage(
                                lang.getMessage(
                                        sender as? Player,
                                        "messages.stats_get",
                                        mapOf(
                                                "player" to PlayerNameUtil.getNameOrDefault(targetOffline.uniqueId, "不明"),

                                                "key" to field,
                                                "value" to displayValue
                                        )
                                )
                        )
                    }
                    "set" -> {
                        if (value == null) {
                            sender.sendMessage(
                                    lang.getMessage(sender as? Player, "messages.value_required")
                            )
                            return true
                        }
                        if (value < 0) {
                            sender.sendMessage(
                                    lang.getMessage(sender as? Player, "messages.value_negative")
                            )
                            return true
                        }
                        when (field) {
                            "points" -> stats.worldPoint = value
                            // warp-slots removed
                            "world-slots" -> stats.unlockedWorldSlot = value
                            else -> {
                                sender.sendMessage(
                                        lang.getMessage(sender as? Player, "messages.invalid_field")
                                )
                                return true
                            }
                        }
                        plugin.playerStatsRepository.save(stats)
                        val displayValue = if (field == "points") "§6🛖 §e$value" else "§f$value"
                        sender.sendMessage(
                                lang.getMessage(
                                        sender as? Player,
                                        "messages.stats_set",
                                        mapOf(
                                                "player" to PlayerNameUtil.getNameOrDefault(targetOffline.uniqueId, "不明"),

                                                "key" to field,
                                                "value" to displayValue
                                        )
                                )
                        )
                    }
                    "add" -> {
                        if (value == null) {
                            sender.sendMessage(
                                    lang.getMessage(sender as? Player, "messages.value_required")
                            )
                            return true
                        }
                        when (field) {
                            "points" -> stats.worldPoint += value
                            // warp-slots removed
                            "world-slots" -> stats.unlockedWorldSlot += value
                            else -> {
                                sender.sendMessage(
                                        lang.getMessage(sender as? Player, "messages.invalid_field")
                                )
                                return true
                            }
                        }
                        plugin.playerStatsRepository.save(stats)
                        val displayValue = if (field == "points") "§6🛖 §e$value" else "§f$value"
                        sender.sendMessage(
                                lang.getMessage(
                                        sender as? Player,
                                        "messages.stats_add",
                                        mapOf(
                                                "player" to PlayerNameUtil.getNameOrDefault(targetOffline.uniqueId, "不明"),

                                                "key" to field,
                                                "value" to displayValue
                                        )
                                )
                        )
                    }
                    "remove" -> {
                        if (value == null) {
                            sender.sendMessage(
                                    lang.getMessage(sender as? Player, "messages.value_required")
                            )
                            return true
                        }
                        val current =
                                when (field) {
                                    "points" -> stats.worldPoint
                                    // warp-slots removed
                                    "world-slots" -> stats.unlockedWorldSlot
                                    else -> {
                                        sender.sendMessage(
                                                lang.getMessage(
                                                        sender as? Player,
                                                        "messages.invalid_field"
                                                )
                                        )
                                        return true
                                    }
                                }
                        if (current < value) {
                            sender.sendMessage(
                                    lang.getMessage(
                                            sender as? Player,
                                            "messages.stats_remove_error",
                                            mapOf("value" to current)
                                    )
                            )
                            return true
                        }
                        when (field) {
                            "points" -> stats.worldPoint -= value
                            // warp-slots removed
                            "world-slots" -> stats.unlockedWorldSlot -= value
                        }
                        plugin.playerStatsRepository.save(stats)
                        val displayValue = if (field == "points") "§6🛖 §e$value" else "§f$value"
                        sender.sendMessage(
                                lang.getMessage(
                                        sender as? Player,
                                        "messages.stats_remove",
                                        mapOf(
                                                "player" to PlayerNameUtil.getNameOrDefault(targetOffline.uniqueId, "不明"),

                                                "key" to field,
                                                "value" to displayValue
                                        )
                                )
                        )
                    }
                    else ->
                            sender.sendMessage(
                                    lang.getMessage(sender as? Player, "messages.invalid_action")
                            )
                }
                return true
            }
            else -> {
                sender.sendMessage(plugin.languageManager.getMessage("error.unknown_subcommand"))
                return true
            }
        }
    }

    override fun onTabComplete(
            sender: CommandSender,
            command: Command,
            alias: String,
            args: Array<out String>
    ): List<String> {
        val list = mutableListOf<String>()
        val hasGlobalPermission = PermissionManager.checkPermission(sender, PermissionManager.COMMAND_MWM)
        val hasCreatePermission = hasGlobalPermission || PermissionManager.checkPermission(sender, PermissionManager.COMMAND_MWM_CREATE)
        val hasReloadPermission = hasGlobalPermission || PermissionManager.checkPermission(sender, PermissionManager.COMMAND_MWM_RELOAD)
        val hasStatsPermission = hasGlobalPermission || PermissionManager.checkPermission(sender, PermissionManager.COMMAND_MWM_STATS)
        val hasWorldListPermission =
                hasGlobalPermission || PermissionManager.checkAnyPermission(sender, PermissionManager.COMMAND_MWM_LIST, PermissionManager.ADMIN_WORLD_LIST)
        if (!hasGlobalPermission && !hasCreatePermission && !hasReloadPermission && !hasStatsPermission && !hasWorldListPermission) return emptyList()
        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)

        when (args.size) {
            1 -> {
                if (hasCreatePermission && canSuggestSubCommand(sender, "create", args.toList())) {
                    list.add("create")
                }
                if (hasReloadPermission && canSuggestSubCommand(sender, "reload", args.toList())) {
                    list.add("reload")
                }
                if (hasStatsPermission && canSuggestSubCommand(sender, "stats", args.toList())) {
                    list.add("stats")
                }
                if (hasGlobalPermission && sender is org.bukkit.command.ConsoleCommandSender && canSuggestSubCommand(sender, "update-day", args.toList())) {
                    list.add("update-day")
                }
                if (hasWorldListPermission && canSuggestSubCommand(sender, "list", args.toList())) {
                    list.add("list")
                }
            }
            2 -> {
                val sub = args[0].lowercase()
                val createCompletion = if (sub == "create" && hasCreatePermission && canSuggestSubCommand(sender, sub, args.toList())) {
                    MyWorldManagerApi.getCreateCommandHandler()?.tabCompleteCreateCommand(sender, args.drop(1))
                } else {
                    null
                }
                if (createCompletion != null) {
                    list.addAll(createCompletion)
                } else if (((sub == "stats" && hasStatsPermission) ||
                    (sub == "create" && hasCreatePermission)) &&
                    canSuggestSubCommand(sender, sub, args.toList())
                ) {
                    list.addAll(Bukkit.getOnlinePlayers().map { it.name })
                }
            }
            3 -> {
                val sub = args[0].lowercase()
                if (sub == "stats" && hasStatsPermission) {
                    list.addAll(listOf("points", "warp-slots", "world-slots"))
                }
            }
            4 -> {
                if (args[0].lowercase() == "stats" && hasStatsPermission) {
                    list.addAll(listOf("get", "set", "add", "remove"))
                }
            }
        }
        return list.filter { it.lowercase().startsWith(args.last().lowercase()) }
    }

    private fun isSubCommandEnabled(sender: CommandSender, subCommand: String, args: List<String>): Boolean {
        return subCommand in enabledSubCommands
    }

    private fun hasSubcommandPermission(sender: CommandSender, subCommand: String?): Boolean {
        return when (subCommand) {
            "migration" -> PermissionManager.checkPermission(sender, PermissionManager.ADMIN)
            "create" -> PermissionManager.checkPermission(sender, PermissionManager.COMMAND_MWM_CREATE)
            "reload" -> PermissionManager.checkPermission(sender, PermissionManager.COMMAND_MWM_RELOAD)
            "stats" -> PermissionManager.checkPermission(sender, PermissionManager.COMMAND_MWM_STATS)
            "list" -> PermissionManager.checkAnyPermission(sender, PermissionManager.COMMAND_MWM_LIST, PermissionManager.ADMIN_WORLD_LIST)
            "update-day" -> sender is org.bukkit.command.ConsoleCommandSender
            else -> false
        }
    }

    private fun canSuggestSubCommand(sender: CommandSender, subCommand: String, args: List<String>): Boolean {
        return isSubCommandEnabled(sender, subCommand, args) && hasSubcommandPermission(sender, subCommand)
    }

    companion object {
        private val enabledSubCommands = setOf("create", "reload", "stats", "update-day", "list", "migration")
    }
}
