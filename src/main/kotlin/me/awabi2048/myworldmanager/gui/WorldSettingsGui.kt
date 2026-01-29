package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.model.WorldTag
import me.awabi2048.myworldmanager.model.PortalData
import me.awabi2048.myworldmanager.session.SettingsAction
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import java.util.UUID

class WorldSettingsGui(private val plugin: MyWorldManager) {

    fun open(player: Player, worldData: WorldData) {
        val lang = plugin.languageManager
        val titleKey = "gui.settings.title"
        if (!lang.hasKey(player, titleKey)) {
            player.sendMessage("§c[MyWorldManager] Error: Missing translation key: $titleKey")
            return
        }

        val title = lang.getMessage(player, titleKey, worldData.name)
        val currentTitle = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(player.openInventory.title())
        
        if (currentTitle != title) {
            plugin.soundManager.playMenuOpenSound(player, "world_settings")
        }
        
        plugin.settingsSessionManager.updateSessionAction(player, worldData.uuid, SettingsAction.VIEW_SETTINGS, isGui = true)
        scheduleGuiTransitionReset(player)
        
        // インベントリの作成または再利用
        val inventory = if (player.openInventory.topInventory.size == 54 && currentTitle == title) {
            player.openInventory.topInventory
        } else {
            Bukkit.createInventory(null, 54, Component.text(title))
        }

        // 背景 (黒の板ガラス)
        val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
        for (i in 0..8) inventory.setItem(i, blackPane)
        for (i in 45..53) inventory.setItem(i, blackPane)

        // 権限判定
        val isOwner = worldData.owner == player.uniqueId
        val isModerator = worldData.moderators.contains(player.uniqueId)
        val hasManagePermission = isOwner || isModerator

        // スロット19: ワールド名・説明変更
        if (hasManagePermission) {
            inventory.setItem(19, createItem(
                Material.NAME_TAG,
                lang.getMessage(player, "gui.settings.info.display"),
                lang.getMessageList(player, "gui.settings.info.lore"),
                ItemTag.TYPE_GUI_SETTING_INFO
            ))
        }

        // Check if player is in the world for restricted settings
        val targetWorldName = worldData.customWorldName ?: "my_world.${worldData.uuid}"
        val isInWorld = player.world.name == targetWorldName
        val warningLore = if (!isInWorld) lang.getMessage(player, "gui.settings.error.must_be_in_world_lore") else null


        // スロット20: アイコン変更
        if (hasManagePermission) {
            inventory.setItem(20, createItem(
                Material.ANVIL,
                lang.getMessage(player, "gui.settings.icon.display"),
                lang.getMessageList(player, "gui.settings.icon.lore"),
                ItemTag.TYPE_GUI_SETTING_ICON
            ))
        }

        // スロット21: スポーン位置変更
        if (hasManagePermission) {
            val lore = lang.getMessageList(player, "gui.settings.spawn.lore").toMutableList()
            if (!isInWorld && warningLore != null) {
                lore.add("")
                lore.add(warningLore)
            }
            
            inventory.setItem(21, createItem(
                Material.COMPASS,
                lang.getMessage(player, "gui.settings.spawn.display"),
                lore,
                ItemTag.TYPE_GUI_SETTING_SPAWN
            ))
        }

        // スロット23: ワールド拡張 (オーナーのみ)
        if (isOwner) {
            val config = plugin.config
            val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
            val costsSection = config.getConfigurationSection("expansion.costs")
            val maxLevel = costsSection?.getKeys(false)?.size ?: 3
            val baseCost = config.getInt("expansion.base_cost", 100)
            val multiplier = config.getDouble("expansion.cost_multiplier", 2.0)
            
            val currentLevel = worldData.borderExpansionLevel
            
            val expansionLore = mutableListOf<String>()
            val separator = lang.getMessage(player, "gui.common.separator")
            
            expansionLore.add(separator)
            expansionLore.add(lang.getMessage(player, "gui.settings.expand.lore_desc"))
            expansionLore.add(separator)
            
            if (currentLevel == WorldData.EXPANSION_LEVEL_SPECIAL) {
                expansionLore.add("§7Level: §dSpecial")
                expansionLore.add("§7Border: §aUnlimited")
                expansionLore.add(separator)
            } else {
                val targetLevel = currentLevel + 1
                val cost = if (config.contains("expansion.costs.$targetLevel")) {
                    config.getInt("expansion.costs.$targetLevel")
                } else {
                    (baseCost * Math.pow(multiplier, currentLevel.toDouble())).toInt()
                }

                expansionLore.add(lang.getMessage(player, "gui.settings.expand.level", currentLevel, maxLevel))
                
            if (currentLevel < maxLevel) {
                if (stats.worldPoint < cost) {
                    val insufficient = cost - stats.worldPoint
                    expansionLore.add(lang.getMessage(player, "gui.settings.expand.cost_insufficient", cost, currentLevel, targetLevel, insufficient))
                    expansionLore.add(lang.getMessage(player, "gui.settings.expand.points", stats.worldPoint))
                } else {
                    expansionLore.add(lang.getMessage(player, "gui.settings.expand.cost", cost, currentLevel, targetLevel))
                    expansionLore.add(lang.getMessage(player, "gui.settings.expand.points", stats.worldPoint))
                }
            } else {
                expansionLore.add(lang.getMessage(player, "gui.settings.expand.max_level"))
            }

            if (!isInWorld && warningLore != null) {
                expansionLore.add("")
                expansionLore.add(warningLore)
            }
            expansionLore.add(separator)
            }
            
            inventory.setItem(23, createItem(
                Material.FILLED_MAP,
                lang.getMessage(player, "gui.settings.expand.display"),
                expansionLore,
                ItemTag.TYPE_GUI_SETTING_EXPAND
            ))
        }

        // スロット24: 公開レベル変更 (オーナーのみ)
        if (isOwner) {
            val levels = listOf(
                Triple(PublishLevel.PUBLIC, lang.getMessage(player, "publish_level.public"), lang.getMessage(player, "publish_level.color.public")),
                Triple(PublishLevel.FRIEND, lang.getMessage(player, "publish_level.friend"), lang.getMessage(player, "publish_level.color.friend")),
                Triple(PublishLevel.PRIVATE, lang.getMessage(player, "publish_level.private"), lang.getMessage(player, "publish_level.color.private")),
                Triple(PublishLevel.LOCKED, lang.getMessage(player, "publish_level.locked"), lang.getMessage(player, "publish_level.color.locked"))
            )
            
            val current = levels.find { it.first == worldData.publishLevel }
            val currentLevelName = current?.second ?: lang.getMessage(player, "general.unknown")
            val currentColor = current?.third ?: "§f"
            val inactiveColor = lang.getMessage(player, "publish_level.color.inactive")
            
            val publishLore = mutableListOf<String>()
            publishLore.add(lang.getMessage(player, "gui.settings.publish.current", currentColor, currentLevelName))
            publishLore.add("") 
            
            levels.forEach { (level, name, color) ->
                val isActive = worldData.publishLevel == level
                val prefix = if (isActive) lang.getMessage(player, "gui.settings.publish.active_prefix") else lang.getMessage(player, "gui.settings.publish.inactive_prefix")
                val style = if (isActive) color else inactiveColor
                publishLore.add("$prefix$style$name")
            }
            
            publishLore.add("")
            publishLore.add(lang.getMessage(player, "gui.settings.publish.desc_header"))
            val configKey = worldData.publishLevel.name.lowercase()
            val description = lang.getMessage(player, "publish_level.description.$configKey")
            publishLore.add(description)
            
            publishLore.add("")
            publishLore.add(lang.getMessage(player, "gui.settings.publish.click_right"))
            publishLore.add(lang.getMessage(player, "gui.settings.publish.click_left"))
            
            inventory.setItem(24, createItem(
                Material.OAK_DOOR,
                lang.getMessage(player, "gui.settings.publish.display"),
                publishLore,
                ItemTag.TYPE_GUI_SETTING_PUBLISH
            ))
        }

        // スロット25: メンバー管理 (オーナーのみ)
        if (isOwner) {
            val memberLore = mutableListOf<String>()
            val totalCount = worldData.members.size + worldData.moderators.size + 1
            memberLore.add(lang.getMessage(player, "gui.settings.member.count", totalCount))
            memberLore.add("")
            memberLore.add(lang.getMessage(player, "gui.settings.member.list_header"))

            // メンバーリストの作成 (Owner > Moderator > Member)
            val allMemberData = mutableListOf<Triple<UUID, String, String>>()
            val ownerRoleColor = lang.getMessage(player, "publish_level.color.owner")
            val moderatorRoleColor = lang.getMessage(player, "publish_level.color.moderator")
            val memberRoleColor = lang.getMessage(player, "publish_level.color.member")
            
            allMemberData.add(Triple(worldData.owner, lang.getMessage(player, "gui.settings.member.role_owner"), ownerRoleColor))
            worldData.moderators.forEach { allMemberData.add(Triple(it, lang.getMessage(player, "gui.settings.member.role_moderator"), moderatorRoleColor)) }
            worldData.members.forEach { allMemberData.add(Triple(it, lang.getMessage(player, "gui.settings.member.role_member"), memberRoleColor)) }

            val displayLimit = 7
            val displayMembers: List<Triple<UUID, String, String>> = allMemberData.take(displayLimit)
            
            displayMembers.forEach { data: Triple<UUID, String, String> ->
                val uuid: UUID = data.first
                val role: String = data.second
                val roleColor: String = data.third
                
                val offlinePlayer = Bukkit.getOfflinePlayer(uuid)
                val isOnline = offlinePlayer.isOnline
                val onlineColor = lang.getMessage(player, "publish_level.color.online")
                val offlineColor = lang.getMessage(player, "publish_level.color.offline")
                val debugColor = lang.getMessage(player, "publish_level.color.debug")
                val nameColor = if (isOnline) onlineColor else offlineColor
                val name = offlinePlayer.name ?: lang.getMessage(player, "general.unknown")
                
                if (role == lang.getMessage(player, "gui.settings.member.role_member")) {
                    memberLore.add(lang.getMessage(player, "gui.settings.member.list_item_member", nameColor, name))
                } else {
                    memberLore.add(lang.getMessage(player, "gui.settings.member.list_item", debugColor, roleColor, role, nameColor, name))
                }
            }

            if (allMemberData.size > displayLimit) {
                val remaining = allMemberData.size - displayLimit
                val onlineCount = allMemberData.count { d: Triple<UUID, String, String> ->
                    Bukkit.getOfflinePlayer(d.first).isOnline 
                }
                memberLore.add(lang.getMessage(player, "gui.settings.member.more_members", remaining, onlineCount))
            }

            memberLore.add("")
            memberLore.add(lang.getMessage(player, "gui.settings.member.click"))

            inventory.setItem(25, createItem(
                Material.PLAYER_HEAD,
                lang.getMessage(player, "gui.settings.member.display"),
                memberLore,
                ItemTag.TYPE_GUI_SETTING_MEMBER
            ))
        }

        // スロット28: タグ設定
        if (hasManagePermission) {
            val tagLore = mutableListOf<String>()
            tagLore.add(lang.getMessage(player, "gui.settings.tags.lore_header"))
            if (worldData.tags.isEmpty()) {
                tagLore.add(lang.getMessage(player, "gui.settings.tags.lore_empty"))
            } else {
                worldData.tags.forEach { tagLore.add(lang.getMessage(player, "gui.settings.tags.lore_item", lang.getMessage(player, "gui.discovery.tag_names.${it.name.lowercase()}"))) }
            }
            tagLore.add("")
            tagLore.add(lang.getMessage(player, "gui.settings.tags.lore_footer"))
            
            inventory.setItem(28, createItem(
                Material.BOOK,
                lang.getMessage(player, "gui.settings.tags.display"),
                tagLore,
                ItemTag.TYPE_GUI_SETTING_TAGS
            ))
        }

        // スロット29: 案内設定
        if (hasManagePermission) {
            val adLore = mutableListOf<String>()
            adLore.addAll(lang.getMessageList(player, "gui.settings.announcement.lore"))
            
            if (worldData.announcementMessages.isNotEmpty()) {
                adLore.add("")
                adLore.add(lang.getMessage(player, "gui.settings.announcement.preview_header"))
                worldData.announcementMessages.forEach { msg ->
                    adLore.add("  $msg")
                }
            }

            inventory.setItem(29, createItem(
                Material.OAK_SIGN,
                lang.getMessage(player, "gui.settings.announcement.display"),
                adLore,
                ItemTag.TYPE_GUI_SETTING_ANNOUNCEMENT
            ))
        }

        // スロット30: 通知設定
        if (hasManagePermission) {
            val onlineColor = lang.getMessage(player, "publish_level.color.online")
            val offlineColor = lang.getMessage(player, "publish_level.color.offline")
            val statusColor = if (worldData.notificationEnabled) onlineColor else offlineColor
            val statusText = if (worldData.notificationEnabled) lang.getMessage(player, "gui.settings.notification.on") else lang.getMessage(player, "gui.settings.notification.off")
            
            inventory.setItem(30, createItem(
                if (worldData.notificationEnabled) Material.BELL else Material.OAK_DOOR,
                lang.getMessage(player, "gui.settings.notification.display"),
                listOf(
                    lang.getMessage(player, "gui.settings.notification.current", statusColor, statusText),
                    ""
                ) + lang.getMessageList(player, "gui.settings.notification.desc"),
                ItemTag.TYPE_GUI_SETTING_NOTIFICATION
            ))
        }

        // スロット32: 環境設定 (オーナーのみ)
        if (isOwner) {
            val lore = lang.getMessageList(player, "gui.settings.environment.lore").toMutableList()
            if (!isInWorld && warningLore != null) {
                lore.add("")
                lore.add(warningLore)
            }
            inventory.setItem(32, createItem(
                Material.GRASS_BLOCK,
                lang.getMessage(player, "gui.settings.environment.display"),
                lore,
                ItemTag.TYPE_GUI_SETTING_ENVIRONMENT
            ))
        }

        // スロット33: 重大な設定 (オーナーのみ)
        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        if (isOwner && stats.criticalSettingsEnabled) {
            val lore = lang.getMessageList(player, "gui.settings.critical.lore").toMutableList()
            if (!isInWorld && warningLore != null) {
                lore.add("")
                lore.add(warningLore)
            }
            inventory.setItem(33, createItem(
                Material.TNT,
                lang.getMessage(player, "gui.settings.critical.display"),
                lore,
                ItemTag.TYPE_GUI_SETTING_CRITICAL
            ))
        }

        // スロット49: ワールド情報 (中央下)
        val currentLevel = worldData.borderExpansionLevel
        val costsSection = plugin.config.getConfigurationSection("expansion.costs")
        val maxLevel = costsSection?.getKeys(false)?.size ?: 3
        val totalCount = worldData.members.size + worldData.moderators.size + 1
        val onlineCount = (worldData.members + worldData.moderators + worldData.owner).count { Bukkit.getOfflinePlayer(it).isOnline }
        
        // 公開レベル表示用
        val publishLevelColor = when (worldData.publishLevel) {
            PublishLevel.PUBLIC -> lang.getMessage(player, "publish_level.color.public")
            PublishLevel.FRIEND -> lang.getMessage(player, "publish_level.color.friend")
            PublishLevel.PRIVATE -> lang.getMessage(player, "publish_level.color.private")
            PublishLevel.LOCKED -> lang.getMessage(player, "publish_level.color.locked")
        }
        val publishLevelName = lang.getMessage(player, "publish_level.${worldData.publishLevel.name.lowercase()}")
        
        // 有効期限の計算
        val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val expireDate = try { 
            java.time.LocalDate.parse(worldData.expireDate, dateFormatter) 
        } catch (e: Exception) { 
            java.time.LocalDate.now().plusDays(7) 
        }
        val today = java.time.LocalDate.now()
        val daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(today, expireDate)
        val displayFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日")
        val dateStr = displayFormatter.format(expireDate)

        // 作成日の計算
        val createdAtDate = try {
             val dateTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
             java.time.LocalDateTime.parse(worldData.createdAt, dateTimeFormatter).toLocalDate()
        } catch (e: Exception) {
             java.time.LocalDate.now()
        }
        val daysSinceCreation = java.time.temporal.ChronoUnit.DAYS.between(createdAtDate, java.time.LocalDate.now())
        val createdInfo = if (daysSinceCreation == 0L) {
             lang.getMessage(player, "gui.admin.world_item.created_info_today")
        } else {
             lang.getMessage(player, "gui.admin.world_item.created_info_days", daysSinceCreation)
        }

        val infoLore = mutableListOf<String>()
        
        lang.getMessageList(player, "gui.settings.main_info.lore").forEach { line ->
            if (currentLevel == WorldData.EXPANSION_LEVEL_SPECIAL) {
                if (line.contains("{2}") || line.contains("{4}") || line.contains("{5}")) {
                    return@forEach
                }
            }
            val processedLine = line
                .replace("{0}", worldData.name)
                .replace("{description}", worldData.description)
                .replace("{1}", Bukkit.getOfflinePlayer(worldData.owner).name ?: lang.getMessage(player, "general.unknown"))
                .replace("{2}", if (currentLevel == WorldData.EXPANSION_LEVEL_SPECIAL) "Special" else currentLevel.toString())
                .replace("{3}", maxLevel.toString())
                .replace("{4}", daysRemaining.toString())
                .replace("{5}", dateStr)
                .replace("{6}", totalCount.toString())
                .replace("{7}", onlineCount.toString())
                .replace("{8}", publishLevelColor)
                .replace("{9}", publishLevelName)
                .replace("{10}", worldData.favorite.toString())
                .replace("{11}", worldData.recentVisitors.sum().toString())
                .replace("{12}", displayFormatter.format(createdAtDate))
                .replace("{13}", createdInfo)
            
            infoLore.add(processedLine)
        }
        infoLore.add("${lang.getMessage(player, "publish_level.color.uuid")}UUID: ${worldData.uuid}")

        val infoItem = createItem(
            worldData.icon,
            lang.getMessage(player, "gui.settings.main_info.name", worldData.name),
            infoLore,
            ItemTag.TYPE_GUI_INFO
        )
        ItemTag.setWorldUuid(infoItem, worldData.uuid)
        inventory.setItem(49, infoItem)


        // スロット51: 訪問中のプレイヤー管理
        if (hasManagePermission) {
            val visitors = Bukkit.getWorld("my_world.${worldData.uuid}")?.players?.filter { 
                it.uniqueId != worldData.owner && !worldData.moderators.contains(it.uniqueId) && !worldData.members.contains(it.uniqueId) 
            } ?: emptyList()
            inventory.setItem(51, createItem(
                Material.SPYGLASS,
                lang.getMessage(player, "gui.settings.visitors.display"),
                listOf(
                    lang.getMessage(player, "gui.settings.visitors.count", visitors.size),
                    "",
                    lang.getMessage(player, "gui.settings.visitors.click")
                ),
                ItemTag.TYPE_GUI_SETTING_VISITOR
            ))
        }

        // スロット52: 設置済みポータルの管理
        if (hasManagePermission) {
            inventory.setItem(52, createItem(
                Material.END_PORTAL_FRAME,
                lang.getMessage(player, "gui.settings.portals.display"),
                lang.getMessageList(player, "gui.settings.portals.lore"),
                ItemTag.TYPE_GUI_SETTING_PORTALS
            ))
        }

        // 空きスロットを灰色板ガラスで埋める
        val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
        for (i in 0 until inventory.size) {
            val item = inventory.getItem(i)
            if (item == null || item.type == Material.AIR) {
                inventory.setItem(i, grayPane)
            }
        }

        player.openInventory(inventory)
    }

    fun openArchiveConfirmation(player: Player, worldData: WorldData) {
        val lang = plugin.languageManager
        plugin.soundManager.playMenuOpenSound(player, "world_settings")
        plugin.settingsSessionManager.updateSessionAction(player, worldData.uuid, SettingsAction.ARCHIVE_WORLD, isGui = true)
        scheduleGuiTransitionReset(player)
        val title = lang.getMessage(player, "gui.archive.title", worldData.name)
        val inventory = Bukkit.createInventory(null, 27, Component.text(title))
        
        val infoItem = createItem(
            Material.PAPER,
            lang.getMessage(player, "gui.archive.question"),
            lang.getMessageList(player, "gui.archive.warning"),
            ItemTag.TYPE_GUI_INFO
        )
        ItemTag.setWorldUuid(infoItem, worldData.uuid)
        inventory.setItem(13, infoItem)
        
        inventory.setItem(11, createItem(Material.LIME_WOOL, lang.getMessage(player, "gui.archive.cancel"), listOf(lang.getMessage(player, "gui.archive.cancel_desc")), ItemTag.TYPE_GUI_CANCEL))
        inventory.setItem(15, createItem(Material.RED_WOOL, lang.getMessage(player, "gui.archive.confirm"), listOf(lang.getMessage(player, "gui.archive.confirm_desc")), ItemTag.TYPE_GUI_CONFIRM))
        
        val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
        for (i in 0 until inventory.size) {
            if (inventory.getItem(i) == null) inventory.setItem(i, grayPane)
        }
        
        player.openInventory(inventory)
    }

    fun openUnarchiveConfirmation(player: Player, worldData: WorldData) {
        val lang = plugin.languageManager
        plugin.soundManager.playMenuOpenSound(player, "world_settings")
        plugin.settingsSessionManager.updateSessionAction(player, worldData.uuid, SettingsAction.UNARCHIVE_CONFIRM, isGui = true)
        scheduleGuiTransitionReset(player)
        
        val title = lang.getMessage(player, "gui.unarchive_confirm.title")
        val inventory = Bukkit.createInventory(null, 27, Component.text(title))

        // 背景
        val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
        for (i in 0..8) inventory.setItem(i, blackPane)
        for (i in 18..26) inventory.setItem(i, blackPane)

        // 情報
        val infoItem = createItem(
            Material.PAPER,
            lang.getMessage(player, "gui.unarchive_confirm.title"),
            lang.getMessageList(player, "gui.unarchive_confirm.lore"),
            ItemTag.TYPE_GUI_INFO
        )
        ItemTag.setWorldUuid(infoItem, worldData.uuid)
        inventory.setItem(13, infoItem)

        // 実行 (15)
        inventory.setItem(15, createItem(
            Material.LIME_CONCRETE,
            lang.getMessage(player, "gui.common.confirm"),
            listOf(),
            ItemTag.TYPE_GUI_CONFIRM
        ))

        // キャンセル (11)
        inventory.setItem(11, createItem(
            Material.RED_CONCRETE,
            lang.getMessage(player, "gui.common.cancel"),
            listOf(),
            ItemTag.TYPE_GUI_CANCEL
        ))

        val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
        for (i in 9..17) {
            if (inventory.getItem(i) == null) inventory.setItem(i, grayPane)
        }

        player.openInventory(inventory)
    }

    fun openExpansionMethodSelection(player: Player, @Suppress("UNUSED_PARAMETER") worldData: WorldData) {
        val lang = plugin.languageManager
        val title = lang.getMessage(player, "gui.expansion.method_title")
        val currentTitle = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(player.openInventory.title())
        
        plugin.soundManager.playMenuOpenSound(player, "world_settings")
        plugin.settingsSessionManager.updateSessionAction(player, worldData.uuid, SettingsAction.EXPAND_SELECT_METHOD, isGui = true)
        scheduleGuiTransitionReset(player)
        
        val inventory = if (player.openInventory.topInventory.size == 45 && currentTitle == title) {
            player.openInventory.topInventory
        } else {
            Bukkit.createInventory(null, 45, Component.text(title))
        }
        
        // ヘッダー・フッター
        val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
        for (i in 0..8) inventory.setItem(i, blackPane)
        for (i in 36..44) inventory.setItem(i, blackPane)

        inventory.setItem(20, createItem(
            Material.MAP,
            lang.getMessage(player, "gui.expansion.center_expand.name"),
            lang.getMessageList(player, "gui.expansion.center_expand.lore"),
            ItemTag.TYPE_GUI_SETTING_EXPAND
        ))
        
        inventory.setItem(24, createItem(
            Material.COMPASS,
            lang.getMessage(player, "gui.expansion.direction_expand.name"),
            lang.getMessageList(player, "gui.expansion.direction_expand.lore"),
            ItemTag.TYPE_GUI_SETTING_SPAWN
        ))

        // 戻るボタン
        inventory.setItem(40, createItem(Material.ARROW, lang.getMessage(player, "gui.common.back"), listOf(), ItemTag.TYPE_GUI_CANCEL))
        
        val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
        for (i in 0 until inventory.size) {
            if (inventory.getItem(i) == null) inventory.setItem(i, grayPane)
        }
        
        player.openInventory(inventory)
    }
    
    fun openExpansionConfirmation(player: Player, worldUuid: UUID, direction: org.bukkit.block.BlockFace?, cost: Int) {
        val lang = plugin.languageManager
        plugin.soundManager.playMenuOpenSound(player, "world_settings")
        plugin.settingsSessionManager.updateSessionAction(player, worldUuid, SettingsAction.EXPAND_CONFIRM, isGui = true)
        scheduleGuiTransitionReset(player)
        val inventory = Bukkit.createInventory(null, 45, Component.text(lang.getMessage(player, "gui.expansion.confirm_title")))
        
        // ヘッダー・フッター
        val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
        for (i in 0..8) inventory.setItem(i, blackPane)
        for (i in 36..44) inventory.setItem(i, blackPane)

        val directionKey = when (direction) {
            org.bukkit.block.BlockFace.NORTH_WEST -> "general.direction.north_west"
            org.bukkit.block.BlockFace.NORTH_EAST -> "general.direction.north_east"
            org.bukkit.block.BlockFace.SOUTH_WEST -> "general.direction.south_west"
            org.bukkit.block.BlockFace.SOUTH_EAST -> "general.direction.south_east"
            else -> "general.direction.unknown"
        }
        val directionName = lang.getMessage(player, directionKey)
        val methodText = if (direction == null) lang.getMessage(player, "gui.expansion.method_center") else lang.getMessage(player, "gui.expansion.method_direction", directionName)
        
        inventory.setItem(22, createItem(
            Material.BOOK,
            lang.getMessage(player, "gui.expansion.confirm_info"),
            listOf(
                lang.getMessage(player, "gui.expansion.method", methodText),
                lang.getMessage(player, "gui.expansion.cost", cost),
                "",
                lang.getMessage(player, "gui.expansion.warning")
            ),
            ItemTag.TYPE_GUI_INFO
        ))
        
        inventory.setItem(20, createItem(Material.LIME_WOOL, lang.getMessage(player, "gui.expansion.execute"), listOf(lang.getMessage(player, "gui.expansion.execute_desc")), ItemTag.TYPE_GUI_CONFIRM))
        inventory.setItem(24, createItem(Material.RED_WOOL, lang.getMessage(player, "gui.expansion.cancel"), listOf(lang.getMessage(player, "gui.expansion.cancel_desc")), ItemTag.TYPE_GUI_CANCEL))
        
        val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
        for (i in 0 until inventory.size) {
            if (inventory.getItem(i) == null) inventory.setItem(i, grayPane)
        }
        
        player.openInventory(inventory)
    }

    fun openMemberManagement(player: Player, worldData: WorldData, page: Int = 0) {
        val lang = plugin.languageManager
        val title = lang.getMessage(player, "gui.member_management.title")
        val currentTitle = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(player.openInventory.title())
        
        plugin.soundManager.playMenuOpenSound(player, "world_settings")
        plugin.settingsSessionManager.updateSessionAction(player, worldData.uuid, SettingsAction.MANAGE_MEMBERS, isGui = true)
        scheduleGuiTransitionReset(player)
        val allMembers = mutableListOf<Pair<java.util.UUID, String>>()
        allMembers.add(worldData.owner to lang.getMessage(player, "role.owner"))
        worldData.moderators.forEach { allMembers.add(it to lang.getMessage(player, "role.moderator")) }
        worldData.members.forEach { allMembers.add(it to lang.getMessage(player, "role.member")) }

        val itemsPerPage = 28
        val startIndex = page * itemsPerPage
        val currentPageMembers = allMembers.drop(startIndex).take(itemsPerPage)

        // 行数を計算 (ヘッダー+中身+フッター)
        val contentRows = if (currentPageMembers.isEmpty()) 1 else (currentPageMembers.size - 1) / 7 + 1
        val rowCount = (contentRows + 2).coerceIn(3, 6)
        
        val inventory = if (player.openInventory.topInventory.size == rowCount * 9 && currentTitle == title) {
            player.openInventory.topInventory
        } else {
            Bukkit.createInventory(null, rowCount * 9, Component.text(title))
        }
        
        val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
        for (i in 0..8) inventory.setItem(i, blackPane)
        
        val footerStart = (rowCount - 1) * 9
        for (i in 0..8) inventory.setItem(footerStart + i, blackPane)

        // メンバーリストの描画
        @Suppress("UNUSED_VARIABLE")
        val isOwner = worldData.owner == player.uniqueId
        currentPageMembers.forEachIndexed { index, pair ->
            val row = index / 7
            val col = index % 7
            val slot = (row + 1) * 9 + (col + 1)
            inventory.setItem(slot, createMemberItem(player, pair.first, pair.second, worldData.owner == player.uniqueId))
        }

        // ナビゲーション
        if (page > 0) {
            inventory.setItem(footerStart + 1, createItem(Material.ARROW, lang.getMessage(player, "gui.common.prev_page"), listOf("PAGE_TARGET: ${page - 1}"), ItemTag.TYPE_GUI_NAV_PREV))
        }
        if (startIndex + itemsPerPage < allMembers.size) {
            inventory.setItem(footerStart + 8, createItem(Material.ARROW, lang.getMessage(player, "gui.common.next_page"), listOf("PAGE_TARGET: ${page + 1}"), ItemTag.TYPE_GUI_NAV_NEXT))
        }

        // 戻るボタン (最終行1スロット目)
        inventory.setItem(footerStart, createItem(Material.REDSTONE, lang.getMessage(player, "gui.common.back"), listOf(), ItemTag.TYPE_GUI_CANCEL))
        
        // メンバー招待ボタン
        inventory.setItem(footerStart + 4, createItem(
            Material.PAPER, 
            lang.getMessage(player, "gui.member_management.invite.name"), 
            listOf(lang.getMessage(player, "gui.member_management.invite.desc")), 
            ItemTag.TYPE_GUI_MEMBER_INVITE
        ))
        
        // 背景埋め
        val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
        for (i in 9 until footerStart) {
            if (inventory.getItem(i) == null) inventory.setItem(i, grayPane)
        }

        player.openInventory(inventory)
    }

    fun openMemberRemoveConfirmation(player: Player, worldData: WorldData, targetUuid: java.util.UUID) {
        val lang = plugin.languageManager
        plugin.soundManager.playMenuOpenSound(player, "world_settings")
        plugin.settingsSessionManager.updateSessionAction(player, worldData.uuid, SettingsAction.MEMBER_REMOVE_CONFIRM, isGui = true)
        scheduleGuiTransitionReset(player)
        val targetName = Bukkit.getOfflinePlayer(targetUuid).name ?: "不明"
        val inventory = Bukkit.createInventory(null, 27, Component.text(lang.getMessage(player, "gui.member_management.remove_confirm.title", targetName)))
        
         val infoItem = createItem(
            Material.PLAYER_HEAD,
            lang.getMessage(player, "gui.member_management.remove_confirm.title", Bukkit.getOfflinePlayer(targetUuid).name ?: lang.getMessage(player, "general.unknown")),
            listOf(
                lang.getMessage(player, "gui.member_management.remove_confirm.question"),
                lang.getMessage(player, "gui.member_management.remove_confirm.player", Bukkit.getOfflinePlayer(targetUuid).name ?: lang.getMessage(player, "general.unknown")),
                lang.getMessage(player, "gui.member_management.remove_confirm.world", worldData.name),
                "",
                lang.getMessage(player, "gui.member_management.remove_confirm.warning")
            ),
            ItemTag.TYPE_GUI_INFO
        )
        ItemTag.setWorldUuid(infoItem, targetUuid) // 削除対象のUUIDを保存
        inventory.setItem(13, infoItem)
        
        inventory.setItem(11, createItem(Material.LIME_WOOL, lang.getMessage(player, "gui.member_management.remove_confirm.cancel"), listOf(lang.getMessage(player, "gui.member_management.remove_confirm.cancel_desc")), ItemTag.TYPE_GUI_CANCEL))
        inventory.setItem(15, createItem(Material.RED_WOOL, lang.getMessage(player, "gui.member_management.remove_confirm.confirm"), listOf(lang.getMessage(player, "gui.member_management.remove_confirm.confirm_desc")), ItemTag.TYPE_GUI_CONFIRM))
        
        val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
        for (i in 0 until inventory.size) {
            if (inventory.getItem(i) == null) inventory.setItem(i, grayPane)
        }
        
        player.openInventory(inventory)
    }

    fun openMemberTransferConfirmation(player: Player, worldData: WorldData, targetUuid: java.util.UUID) {
        val lang = plugin.languageManager
        plugin.soundManager.playMenuOpenSound(player, "world_settings")
        plugin.settingsSessionManager.updateSessionAction(player, worldData.uuid, SettingsAction.MEMBER_TRANSFER_CONFIRM, isGui = true)
        scheduleGuiTransitionReset(player)
        val targetName = Bukkit.getOfflinePlayer(targetUuid).name ?: "不明"
        val inventory = Bukkit.createInventory(null, 27, Component.text(lang.getMessage(player, "gui.member_management.transfer_confirm.title", targetName)))
        
        val infoItem = createItem(
            Material.PLAYER_HEAD,
            lang.getMessage(player, "gui.member_management.transfer_confirm.title", targetName),
            listOf(
                lang.getMessage(player, "gui.member_management.transfer_confirm.question"),
                lang.getMessage(player, "gui.member_management.transfer_confirm.player", targetName),
                lang.getMessage(player, "gui.member_management.transfer_confirm.world", worldData.name),
                "",
                lang.getMessage(player, "gui.member_management.transfer_confirm.warning")
            ),
            ItemTag.TYPE_GUI_INFO
        )
        ItemTag.setWorldUuid(infoItem, targetUuid)
        inventory.setItem(13, infoItem)
        
        inventory.setItem(11, createItem(Material.LIME_WOOL, lang.getMessage(player, "gui.member_management.transfer_confirm.cancel"), listOf(lang.getMessage(player, "gui.member_management.transfer_confirm.cancel_desc")), ItemTag.TYPE_GUI_CANCEL))
        inventory.setItem(15, createItem(Material.RED_WOOL, lang.getMessage(player, "gui.member_management.transfer_confirm.confirm"), listOf(lang.getMessage(player, "gui.member_management.transfer_confirm.confirm_desc")), ItemTag.TYPE_GUI_CONFIRM))
        
        val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
        for (i in 0 until inventory.size) {
            if (inventory.getItem(i) == null) inventory.setItem(i, grayPane)
        }
        
        player.openInventory(inventory)
    }

    private fun createMemberItem(viewer: Player, uuid: java.util.UUID, role: String, isOwner: Boolean): ItemStack {
        val lang = plugin.languageManager
        val player = Bukkit.getOfflinePlayer(uuid)
        val isOnline = player.isOnline
        val color = if (isOnline) "§a" else "§c"
        
        var displayName = player.name
        if (displayName == null) {
            val stats = plugin.playerStatsRepository.findByUuid(uuid)
            displayName = stats.lastName ?: lang.getMessage(viewer, "general.unknown")
        }

        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as? org.bukkit.inventory.meta.SkullMeta ?: return item
        meta.owningPlayer = player
        
        meta.displayName(Component.text("$color$displayName").decoration(TextDecoration.ITALIC, false))
        
        val lore = mutableListOf<String>()
        val separator = lang.getMessage(viewer, "gui.common.separator")
        
        lore.add(separator)
        if (!isOnline) {
             @Suppress("DEPRECATION")
             val lastPlayed = java.time.Instant.ofEpochMilli(player.lastPlayed)
                 .atZone(java.time.ZoneId.systemDefault())
                 .format(java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm"))
             lore.add(lang.getMessage(viewer, "gui.member_management.item.last_online", lastPlayed))
        }
        lore.add(lang.getMessage(viewer, "gui.member_management.item.role", role))
        lore.add(separator)
        
        if (isOwner && role != lang.getMessage(viewer, "role.owner")) {
            lore.add(separator)
            if (role == lang.getMessage(null as Player?, "role.member")) {
                lore.add(lang.getMessage(viewer, "gui.member_management.item.promote"))
            } else if (role == lang.getMessage(null as Player?, "role.moderator")) {
                lore.add(lang.getMessage(viewer, "gui.member_management.item.demote"))
            }
            lore.add(lang.getMessage(viewer, "gui.member_management.item.remove"))
            lore.add(lang.getMessage(viewer, "gui.settings.member.transfer"))
            lore.add(separator)
        }
        
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_MEMBER_ITEM)
        ItemTag.setWorldUuid(item, uuid)
        return item
    }

    fun openVisitorManagement(player: Player, worldData: WorldData, page: Int = 0) {
        val lang = plugin.languageManager
        plugin.soundManager.playMenuOpenSound(player, "world_settings")
        plugin.settingsSessionManager.updateSessionAction(player, worldData.uuid, SettingsAction.MANAGE_VISITORS, isGui = true)
        scheduleGuiTransitionReset(player)
        val world = Bukkit.getWorld("my_world.${worldData.uuid}") ?: return
        val visitorPlayers = world.players.filter { 
            it.uniqueId != worldData.owner && !worldData.moderators.contains(it.uniqueId) && !worldData.members.contains(it.uniqueId) 
        }

        val itemsPerPage = 28
        val startIndex = page * itemsPerPage
        val currentPageVisitors = visitorPlayers.drop(startIndex).take(itemsPerPage)

        // 行数を計算
        val contentRows = if (currentPageVisitors.isEmpty()) 1 else (currentPageVisitors.size - 1) / 7 + 1
        val rowCount = (contentRows + 2).coerceIn(3, 6)

        val inventory = Bukkit.createInventory(null, rowCount * 9, Component.text(lang.getMessage(player, "gui.visitor_management.title")))
        
        val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
        for (i in 0..8) inventory.setItem(i, blackPane)
        
        val footerStart = (rowCount - 1) * 9
        for (i in 0..8) inventory.setItem(footerStart + i, blackPane)

        // プレイヤーリストの描画
        val canKick = worldData.owner == player.uniqueId || worldData.moderators.contains(player.uniqueId)
        
        currentPageVisitors.forEachIndexed { index, visitor ->
            val row = index / 7
            val col = index % 7
            val slot = (row + 1) * 9 + (col + 1)
            inventory.setItem(slot, createVisitorItem(player, visitor.uniqueId, canKick))
        }

        // ナビゲーション
        if (page > 0) {
            inventory.setItem(footerStart + 1, createItem(Material.ARROW, lang.getMessage(player, "gui.common.prev_page"), listOf("PAGE_TARGET: ${page - 1}"), ItemTag.TYPE_GUI_NAV_PREV))
        }
        if (startIndex + itemsPerPage < visitorPlayers.size) {
            inventory.setItem(footerStart + 8, createItem(Material.ARROW, lang.getMessage(player, "gui.common.next_page"), listOf("PAGE_TARGET: ${page + 1}"), ItemTag.TYPE_GUI_NAV_NEXT))
        }

        // 戻るボタン
        inventory.setItem(footerStart, createItem(Material.REDSTONE, lang.getMessage(player, "gui.common.back"), listOf(), ItemTag.TYPE_GUI_CANCEL))

        // 背景埋め
        val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
        for (i in 9 until footerStart) {
            if (inventory.getItem(i) == null) inventory.setItem(i, grayPane)
        }

        player.openInventory(inventory)
    }

    fun openVisitorKickConfirmation(player: Player, @Suppress("UNUSED_PARAMETER") worldData: WorldData, targetUuid: java.util.UUID) {
        val lang = plugin.languageManager
        plugin.soundManager.playMenuOpenSound(player, "world_settings")
        plugin.settingsSessionManager.updateSessionAction(player, worldData.uuid, SettingsAction.VISITOR_KICK_CONFIRM, isGui = true)
        scheduleGuiTransitionReset(player)
        val targetName = Bukkit.getOfflinePlayer(targetUuid).name ?: lang.getMessage(player, "general.unknown")
        val inventory = Bukkit.createInventory(null, 27, Component.text(lang.getMessage(player, "gui.visitor_management.kick_confirm.title", targetName)))
        
        inventory.setItem(13, createItem(
            Material.PAPER,
            lang.getMessage(player, "gui.visitor_management.kick_confirm.question"),
            listOf(
                lang.getMessage(player, "gui.visitor_management.kick_confirm.player", targetName),
                "",
                "${lang.getMessage(player, "publish_level.color.uuid")}UUID: $targetUuid"
            ),
            ItemTag.TYPE_GUI_INFO
        ))
        
        inventory.setItem(11, createItem(Material.LIME_WOOL, lang.getMessage(player, "gui.visitor_management.kick_confirm.cancel"), listOf(lang.getMessage(player, "gui.visitor_management.kick_confirm.cancel_desc")), ItemTag.TYPE_GUI_CANCEL))
        inventory.setItem(15, createItem(Material.RED_WOOL, lang.getMessage(player, "gui.visitor_management.kick_confirm.confirm"), listOf(lang.getMessage(player, "gui.visitor_management.kick_confirm.confirm_desc")), ItemTag.TYPE_GUI_CONFIRM))
        
        val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
        for (i in 0 until inventory.size) {
            if (inventory.getItem(i) == null) inventory.setItem(i, grayPane)
        }
        
        player.openInventory(inventory)
    }

    private fun createVisitorItem(viewer: Player, uuid: java.util.UUID, canKick: Boolean): ItemStack {
        val lang = plugin.languageManager
        val player = Bukkit.getOfflinePlayer(uuid)
        val isOnline = player.isOnline
        val onlineColor = lang.getMessage(viewer, "publish_level.color.online")
        val offlineColor = lang.getMessage(viewer, "publish_level.color.offline")
        val color = if (isOnline) onlineColor else offlineColor
        
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as? org.bukkit.inventory.meta.SkullMeta ?: return item
        meta.owningPlayer = player
        
        meta.displayName(LegacyComponentSerializer.legacySection().deserialize("$color${player.name ?: lang.getMessage(viewer, "general.unknown")}").decoration(TextDecoration.ITALIC, false))
        
        val lore = mutableListOf<String>()
        val separator = lang.getMessage(viewer, "gui.common.separator")
        
        lore.add(separator)
        val statusText = if (isOnline) lang.getMessage(viewer, "status.online") else lang.getMessage(viewer, "status.offline")
        val statusColor = if (isOnline) onlineColor else offlineColor
        lore.add(lang.getMessage(viewer, "gui.common.status_display", statusColor, statusText))
        lore.add(separator)
        
        if (canKick) {
            lore.add(lang.getMessage(viewer, "gui.visitor_management.item.kick"))
            lore.add(separator)
        }
        
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_VISITOR_ITEM)
        ItemTag.setWorldUuid(item, uuid)
        return item
    }

    private fun createItem(material: Material, name: String, loreLines: List<String>, tag: String): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        
        // 装飾を指定しない (LegacyComponentSerializerにおまかせする)
        meta.displayName(LegacyComponentSerializer.legacySection().deserialize(name).decoration(TextDecoration.ITALIC, false))
        
        val loreComponents = loreLines.map { 
            LegacyComponentSerializer.legacySection().deserialize(it).decoration(TextDecoration.ITALIC, false)
        }
        meta.lore(loreComponents)
        
        item.itemMeta = meta
        ItemTag.tagItem(item, tag)
        return item
    }

    fun openTagEditor(player: Player, worldData: WorldData) {
        val lang = plugin.languageManager
        val title = lang.getMessage(player, "gui.tag_editor.title", worldData.name)
        val currentTitle = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(player.openInventory.title())
        
        plugin.soundManager.playMenuOpenSound(player, "world_settings")
        plugin.settingsSessionManager.updateSessionAction(player, worldData.uuid, SettingsAction.MANAGE_TAGS, isGui = true)
        scheduleGuiTransitionReset(player)
        
        val inventory = if (player.openInventory.topInventory.size == 27 && currentTitle == title) {
            player.openInventory.topInventory
        } else {
            Bukkit.createInventory(null, 27, Component.text(title))
        }

        // 背景
        val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
        for (i in 0..8) inventory.setItem(i, blackPane)
        for (i in 18..26) inventory.setItem(i, blackPane)

        // タグの配置
        val tags = WorldTag.values()
        tags.forEachIndexed { index, tag ->
            val slot = 10 + index
            val hasTag = worldData.tags.contains(tag)
            
            val activeColor = lang.getMessage(player, "publish_level.color.active")
            val inactiveColor = lang.getMessage(player, "publish_level.color.inactive")
            
            val item = ItemStack(if (hasTag) Material.ENCHANTED_BOOK else Material.BOOK)
            val meta = item.itemMeta ?: return@forEachIndexed
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize((if (hasTag) activeColor else inactiveColor) + lang.getMessage(player, "gui.discovery.tag_names.${tag.name.lowercase()}")).decoration(TextDecoration.ITALIC, false))
            
            val lore = mutableListOf<String>()
            val statusText = if (hasTag) lang.getMessage(player, "gui.tag_editor.status_active") else lang.getMessage(player, "gui.tag_editor.status_inactive")
            val statusColor = if (hasTag) activeColor else inactiveColor
            lore.add(lang.getMessage(player, "gui.common.status_display", statusColor, statusText))
            lore.add("")
            lore.add(lang.getMessage(player, "gui.tag_editor.click_toggle"))
            
            meta.lore(lore.map { LegacyComponentSerializer.legacySection().deserialize(it).decoration(TextDecoration.ITALIC, false) })
            item.itemMeta = meta
            ItemTag.tagItem(item, "tag_" + tag.name)
            inventory.setItem(slot, item)
        }

        // 戻るボタン
        inventory.setItem(22, createItem(Material.ARROW, lang.getMessage(player, "gui.common.back"), listOf(), ItemTag.TYPE_GUI_CANCEL))

        player.openInventory(inventory)
    }


    fun openCriticalSettings(player: Player, worldData: WorldData) {
        val lang = plugin.languageManager
        val title = lang.getMessage(player, "gui.critical.title")
        val currentTitle = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(player.openInventory.title())
        
        plugin.soundManager.playMenuOpenSound(player, "world_settings")
        plugin.settingsSessionManager.updateSessionAction(player, worldData.uuid, SettingsAction.CRITICAL_SETTINGS, isGui = true)
        scheduleGuiTransitionReset(player)
        
        val inventory = if (player.openInventory.topInventory.size == 45 && currentTitle == title) {
            player.openInventory.topInventory
        } else {
            Bukkit.createInventory(null, 45, Component.text(title))
        }

        val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
        for (i in 0..8) inventory.setItem(i, blackPane)
        for (i in 36..44) inventory.setItem(i, blackPane)

        // 拡張段階の初期化
        if (worldData.borderExpansionLevel != WorldData.EXPANSION_LEVEL_SPECIAL) {
            val resetLore = mutableListOf<String>()
            val currentLevel = worldData.borderExpansionLevel
            resetLore.add(lang.getMessage(player, "gui.critical.reset_expansion.current", currentLevel))
            resetLore.add("")
            if (currentLevel > 0) {
                val refundRate = plugin.config.getDouble("critical_settings.refund_percentage", 0.5)
                val refund = (calculateTotalExpansionCost(currentLevel) * refundRate).toInt()
                resetLore.add(lang.getMessage(player, "gui.critical.reset_expansion.refund", refund))
                resetLore.add("")
                resetLore.add(lang.getMessage(player, "gui.critical.reset_expansion.click"))
            } else {
                resetLore.add(lang.getMessage(player, "gui.critical.reset_expansion.unavailable"))
            }
            
            inventory.setItem(20, createItem(
                Material.BARRIER,
                lang.getMessage(player, "gui.critical.reset_expansion.display"),
                resetLore,
                ItemTag.TYPE_GUI_SETTING_RESET_EXPANSION
            ))
        }

        // ワールドの削除
        val deleteLore = mutableListOf<String>()
        
        // 払い戻し額の計算
        val refundRate = plugin.config.getDouble("critical_settings.refund_percentage", 0.5)
        val refund = (worldData.cumulativePoints * refundRate).toInt()
        
        lang.getMessageList(player, "gui.critical.delete_world.lore").forEach { line ->
            val processedLine = line
                .replace("{0}", refund.toString())
                .replace("{1}", (refundRate * 100).toInt().toString())
            deleteLore.add(processedLine)
        }

        val deleteSlot = if (worldData.borderExpansionLevel == WorldData.EXPANSION_LEVEL_SPECIAL) 22 else 24
        inventory.setItem(deleteSlot, createItem(
            Material.LAVA_BUCKET,
            lang.getMessage(player, "gui.critical.delete_world.display"),
            deleteLore,
            ItemTag.TYPE_GUI_SETTING_DELETE_WORLD
        ))

        // 戻るボタン (スロット36から40へ移動)
        inventory.setItem(40, createItem(Material.ARROW, lang.getMessage(player, "gui.common.back"), listOf(), ItemTag.TYPE_GUI_CANCEL))

        val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
        for (i in 0 until inventory.size) {
            if (inventory.getItem(i) == null) inventory.setItem(i, grayPane)
        }

        player.openInventory(inventory)
    }

    private fun calculateTotalExpansionCost(level: Int): Int {
        var total = 0
        val config = plugin.config
        val baseCost = config.getInt("expansion.base_cost", 100)
        val multiplier = config.getDouble("expansion.cost_multiplier", 2.0)
        
        for (i in 1..level) {
            total += if (config.contains("expansion.costs.$i")) {
                config.getInt("expansion.costs.$i")
            } else {
                (baseCost * Math.pow(multiplier, (i - 1).toDouble())).toInt()
            }
        }
        return total
    }


    fun openResetExpansionConfirmation(player: Player, worldData: WorldData) {
        val lang = plugin.languageManager
        plugin.soundManager.playMenuOpenSound(player, "world_settings")
        plugin.settingsSessionManager.updateSessionAction(player, worldData.uuid, SettingsAction.RESET_EXPANSION_CONFIRM, isGui = true)
        scheduleGuiTransitionReset(player)
        val inventory = Bukkit.createInventory(null, 27, Component.text(lang.getMessage(player, "gui.confirm.reset_expansion.title")))

        val infoItem = createItem(
            Material.PAPER,
            lang.getMessageList(player, "gui.confirm.reset_expansion.lore")[0],
            lang.getMessageList(player, "gui.confirm.reset_expansion.lore").drop(1),
            ItemTag.TYPE_GUI_INFO
        )
        ItemTag.setWorldUuid(infoItem, worldData.uuid)
        inventory.setItem(13, infoItem)

        inventory.setItem(11, createItem(Material.LIME_WOOL, lang.getMessage(player, "gui.common.cancel"), listOf(lang.getMessage(player, "gui.common.back")), ItemTag.TYPE_GUI_CANCEL))
        inventory.setItem(15, createItem(Material.RED_WOOL, lang.getMessage(player, "gui.common.confirm"), listOf(), ItemTag.TYPE_GUI_CONFIRM))

        val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
        for (i in 0 until inventory.size) {
            if (inventory.getItem(i) == null) inventory.setItem(i, grayPane)
        }
        player.openInventory(inventory)
    }

    fun openDeleteWorldConfirmation1(player: Player, worldData: WorldData) {
        val lang = plugin.languageManager
        plugin.soundManager.playMenuOpenSound(player, "world_settings")
        plugin.settingsSessionManager.updateSessionAction(player, worldData.uuid, SettingsAction.DELETE_WORLD_CONFIRM, isGui = true)
        scheduleGuiTransitionReset(player)
        val inventory = Bukkit.createInventory(null, 27, Component.text(lang.getMessage(player, "gui.confirm.delete_1.title")))

        val infoItem = createItem(
            Material.PAPER,
            lang.getMessageList(player, "gui.confirm.delete_1.lore")[0],
            lang.getMessageList(player, "gui.confirm.delete_1.lore").drop(1),
            ItemTag.TYPE_GUI_INFO
        )
        ItemTag.setWorldUuid(infoItem, worldData.uuid)
        inventory.setItem(13, infoItem)

        inventory.setItem(11, createItem(Material.LIME_WOOL, lang.getMessage(player, "gui.common.cancel"), listOf(), ItemTag.TYPE_GUI_CANCEL))
        inventory.setItem(15, createItem(Material.RED_WOOL, lang.getMessage(player, "gui.confirm.delete_1.next"), listOf(), ItemTag.TYPE_GUI_SETTING_DELETE_WORLD))

        val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
        for (i in 0 until inventory.size) {
            if (inventory.getItem(i) == null) inventory.setItem(i, grayPane)
        }
        player.openInventory(inventory)
    }

    fun openDeleteWorldConfirmation2(player: Player, worldData: WorldData) {
        val lang = plugin.languageManager
        plugin.soundManager.playMenuOpenSound(player, "world_settings")
        plugin.settingsSessionManager.updateSessionAction(player, worldData.uuid, SettingsAction.DELETE_WORLD_CONFIRM_FINAL, isGui = true)
        scheduleGuiTransitionReset(player)
        val inventory = Bukkit.createInventory(null, 27, Component.text(lang.getMessage(player, "gui.confirm.delete_2.title")))

        val infoItem = createItem(
            Material.LAVA_BUCKET,
            lang.getMessageList(player, "gui.confirm.delete_2.lore")[0],
            lang.getMessageList(player, "gui.confirm.delete_2.lore").drop(1),
            ItemTag.TYPE_GUI_INFO
        )
        ItemTag.setWorldUuid(infoItem, worldData.uuid)
        inventory.setItem(13, infoItem)

        inventory.setItem(11, createItem(Material.LIME_WOOL, lang.getMessage(player, "gui.common.cancel"), listOf(), ItemTag.TYPE_GUI_CANCEL))
        inventory.setItem(15, createItem(Material.RED_WOOL, lang.getMessage(player, "gui.confirm.delete_2.confirm_btn"), listOf(), ItemTag.TYPE_GUI_CONFIRM))

        val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
        for (i in 0 until inventory.size) {
            if (inventory.getItem(i) == null) inventory.setItem(i, grayPane)
        }
        player.openInventory(inventory)
    }

    fun openPortalManagement(player: Player, worldData: WorldData, page: Int = 0) {
        val lang = plugin.languageManager
        val title = lang.getMessage(player, "gui.settings.portals.display")
        val currentTitle = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(player.openInventory.title())
        
        plugin.soundManager.playMenuOpenSound(player, "portal_manage")
        plugin.settingsSessionManager.updateSessionAction(player, worldData.uuid, SettingsAction.MANAGE_PORTALS, isGui = true)
        scheduleGuiTransitionReset(player)
        
        val worldName = "my_world.${worldData.uuid}"
        val allPortals = plugin.portalRepository.findAll().filter { it.worldName == worldName }

        val itemsPerPage = 21
        val startIndex = page * itemsPerPage
        val currentPagePortals = allPortals.drop(startIndex).take(itemsPerPage)
        
        val inventory = if (player.openInventory.topInventory.size == 45 && currentTitle == title) {
            player.openInventory.topInventory
        } else {
            Bukkit.createInventory(null, 45, Component.text(title))
        }

        // 背景
        val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
        for (i in 0..8) inventory.setItem(i, blackPane)
        for (i in 36..44) inventory.setItem(i, blackPane)

        currentPagePortals.forEachIndexed { index, portal ->
            val slot = 9 + index
            inventory.setItem(slot, createPortalManagementItem(player, portal))
        }

        // ナビゲーション
        if (page > 0) {
            inventory.setItem(40, createItem(Material.ARROW, lang.getMessage(player, "gui.common.prev_page"), listOf("PAGE_TARGET: ${page - 1}"), ItemTag.TYPE_GUI_NAV_PREV))
        }
        if (startIndex + itemsPerPage < allPortals.size) {
            inventory.setItem(44, createItem(Material.ARROW, lang.getMessage(player, "gui.common.next_page"), listOf("PAGE_TARGET: ${page + 1}"), ItemTag.TYPE_GUI_NAV_NEXT))
        }

        // 戻るボタン
        inventory.setItem(36, createItem(Material.REDSTONE, lang.getMessage(player, "gui.common.back"), listOf(), ItemTag.TYPE_GUI_CANCEL))

        val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
        for (i in 9..35) {
            if (inventory.getItem(i) == null) inventory.setItem(i, grayPane)
        }

        player.openInventory(inventory)
    }

    private fun createPortalManagementItem(player: Player, portal: PortalData): ItemStack {
        val lang = plugin.languageManager
        val item = ItemStack(Material.END_PORTAL_FRAME)
        val meta = item.itemMeta ?: return item
        
        val destName = if (portal.worldUuid != null) {
            val worldData = plugin.worldConfigRepository.findByUuid(portal.worldUuid!!)
            worldData?.name ?: "Unknown World"
        } else {
            val configName = plugin.config.getString("portal_targets.${portal.targetWorldName}")
            configName ?: portal.targetWorldName ?: "Unknown"
        }

        val displayTitle = lang.getMessage(player, "gui.admin_portals.portal_item.name").replace("{0}", destName)
        meta.displayName(LegacyComponentSerializer.legacySection().deserialize(displayTitle).decoration(TextDecoration.ITALIC, false))
        
        val lore = mutableListOf<Component>()
        lore.add(lang.getComponent(player, "gui.admin_portals.portal_item.lore_location", portal.x, portal.y, portal.z))
        lore.add(Component.empty())
        lore.add(lang.getComponent(player, "gui.admin_portals.portal_item.lore_teleport"))
        lore.add(lang.getComponent(player, "gui.admin_portals.portal_item.lore_remove"))
        
        meta.lore(lore)
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_PORTAL)
        ItemTag.setPortalUuid(item, portal.id)
        
        return item
    }

    private fun createDecorationItem(material: Material): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.displayName(Component.empty())
        meta.isHideTooltip = true
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_DECORATION)
        return item
    }

    private fun scheduleGuiTransitionReset(player: Player) {
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val session = plugin.settingsSessionManager.getSession(player)
            if (session != null && session.isGuiTransition) {
                session.isGuiTransition = false
            }
        }, 5L)
    }
}
