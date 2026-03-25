package me.awabi2048.myworldmanager.gui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.PendingInteractionType
import me.awabi2048.myworldmanager.model.PortalData
import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.session.SettingsAction
import me.awabi2048.myworldmanager.util.GuiHelper.scheduleGuiTransitionReset
import me.awabi2048.myworldmanager.util.GuiItemFactory
import me.awabi2048.myworldmanager.util.GuiLoreBuilder
import me.awabi2048.myworldmanager.util.ItemTag
import me.awabi2048.myworldmanager.util.PermissionManager
import net.kyori.adventure.text.Component
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta

class WorldSettingsGui(private val plugin: MyWorldManager) {

        private data class MemberManagementEntry(
                val playerUuid: UUID,
                val role: String? = null,
                val pendingDecisionId: UUID? = null,
                val pendingCreatedAt: Long? = null,
                val pendingType: PendingInteractionType? = null
        )

        fun open(player: Player, worldData: WorldData, showBackButton: Boolean? = null, isPlayerWorldFlow: Boolean? = null, parentShowBackButton: Boolean? = null) {
                val lang = plugin.languageManager
                val titleKey = "gui.settings.title"
                if (!lang.hasKey(player, titleKey)) {
                        player.sendMessage(
                                "§c[MyWorldManager] Error: Missing translation key: $titleKey"
                        )
                }

                // セッションの更新
                if (showBackButton != null || isPlayerWorldFlow != null || parentShowBackButton != null) {
                        plugin.settingsSessionManager.updateSessionAction(
                                player,
                                worldData.uuid,
                                SettingsAction.VIEW_SETTINGS,
                                isGui = true,
                                isPlayerWorldFlow = isPlayerWorldFlow,
                                parentShowBackButton = parentShowBackButton
                        )
                        if (showBackButton != null) {
                            plugin.settingsSessionManager.getSession(player)?.showBackButton =
                                showBackButton
                        }
                } else {
                        plugin.settingsSessionManager.updateSessionAction(
                                player,
                                worldData.uuid,
                                SettingsAction.VIEW_SETTINGS,
                                isGui = true
                        )
                }

                val currentShowBack =
                        plugin.settingsSessionManager.getSession(player)?.showBackButton ?: false

                me.awabi2048.myworldmanager.util.GuiHelper.scheduleGuiTransitionReset(
                        plugin,
                        player
                )

                val title =
                        me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(
                                lang.getComponent(
                                        player,
                                        "gui.settings.title",
                                        mapOf("world" to worldData.name)
                                )
                        )
                me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(
                        plugin,
                        player,
                        "world_settings",
                        title,
                        WorldSettingsGuiHolder::class.java
                )

                // 権限判定
                val currentSession = plugin.settingsSessionManager.getSession(player)
                val isOwner = worldData.owner == player.uniqueId || currentSession?.isAdminFlow == true
                val isModerator = worldData.moderators.contains(player.uniqueId)
                val isMember = worldData.members.contains(player.uniqueId)
                val hasManagePermission = isOwner || isModerator
                val isBedrock = plugin.playerPlatformResolver.isBedrock(player)

                val isMemberLayout = isMember && !hasManagePermission
                val useModeratorCenteredLayout = isModerator && !isOwner

                val inventorySize = if (isMemberLayout) 45 else 54
                val bottomRowStartSlot = inventorySize - 9
                val backButtonSlot = if (isMemberLayout) 36 else 45
                val worldInfoSlot = if (isMemberLayout) 22 else 49
                val tourSettingSlot = if (isMemberLayout) 42 else 47

                val infoSettingSlot = if (useModeratorCenteredLayout) 21 else 19
                val iconSettingSlot = if (useModeratorCenteredLayout) 22 else 20
                val spawnSettingSlot = if (useModeratorCenteredLayout) 23 else 21
                val tagsSettingSlot = if (useModeratorCenteredLayout) 30 else 28
                val announcementSettingSlot = if (useModeratorCenteredLayout) 31 else 29
                val notificationSettingSlot = if (useModeratorCenteredLayout) 32 else 30

                val holder = WorldSettingsGuiHolder()
                val inventory = Bukkit.createInventory(holder, inventorySize, title)
                holder.inv = inventory

                // 背景 (黒の板ガラス)
                val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
                for (i in 0..8) inventory.setItem(i, blackPane)
                for (i in bottomRowStartSlot until inventorySize) inventory.setItem(i, blackPane)

                // 戻るボタン
                if (currentShowBack) {
                        inventory.setItem(
                                backButtonSlot,
                                createItem(Material.REDSTONE, "§7戻る", listOf("§eクリックで戻る"), ItemTag.TYPE_GUI_RETURN)
                        )
                }

                if (hasManagePermission && !isMemberLayout) {
                        inventory.setItem(
                                tourSettingSlot,
                                createItem(
                                        Material.PALE_OAK_BOAT,
                                        lang.getMessage(player, "gui.tour.worldmenu.display"),
                                        lang.getMessageList(player, "gui.tour.worldmenu.lore"),
                                        ItemTag.TYPE_GUI_SETTING_TOUR
                                )
                        )
                }

                // ワールド名・説明変更
                if (hasManagePermission) {
                        inventory.setItem(
                                infoSettingSlot,
                                createItem(
                                        plugin.menuConfigManager.getIconMaterial(
                                                "world_settings",
                                                "info",
                                                Material.NAME_TAG
                                        ),
                                        lang.getMessage(player, "gui.settings.info.display"),
                                        lang.getMessageList(
                                                player,
                                                if (isBedrock) {
                                                        "gui.settings.info.lore_bedrock"
                                                } else {
                                                        "gui.settings.info.lore"
                                                }
                                        ),
                                        ItemTag.TYPE_GUI_SETTING_INFO
                                )
                        )
                }

                // Check if player is in the world for restricted settings
                val targetWorldName = worldData.customWorldName ?: "my_world.${worldData.uuid}"
                val isInWorld = player.world.name == targetWorldName
                val warningLore =
                        if (!isInWorld)
                                lang.getMessage(player, "gui.settings.error.must_be_in_world_lore")
                        else null

                // アイコン変更
                if (hasManagePermission) {
                        inventory.setItem(
                                iconSettingSlot,
                                createItem(
                                        plugin.menuConfigManager.getIconMaterial(
                                                "world_settings",
                                                "icon",
                                                Material.ANVIL
                                        ),
                                        lang.getMessage(player, "gui.settings.icon.display"),
                                        lang.getMessageList(player, "gui.settings.icon.lore"),
                                        ItemTag.TYPE_GUI_SETTING_ICON
                                )
                        )
                }

                // スポーン位置変更
                if (hasManagePermission) {
                        val lore =
                                lang.getMessageList(
                                                player,
                                                if (isBedrock) {
                                                        "gui.settings.spawn.lore_bedrock"
                                                } else {
                                                        "gui.settings.spawn.lore"
                                                }
                                        )
                                        .toMutableList()
                        if (!isInWorld && warningLore != null) {
                                lore.add("")
                                lore.add(warningLore)
                        }

                        inventory.setItem(
                                spawnSettingSlot,
                                createItem(
                                        plugin.menuConfigManager.getIconMaterial(
                                                "world_settings",
                                                "spawn",
                                                Material.COMPASS
                                        ),
                                        lang.getMessage(player, "gui.settings.spawn.display"),
                                        lore,
                                        ItemTag.TYPE_GUI_SETTING_SPAWN
                                )
                        )
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
                        val cost =
                                if (config.contains("expansion.costs.${currentLevel + 1}")) {
                                        config.getInt("expansion.costs.${currentLevel + 1}")
                                } else {
                                        (baseCost * Math.pow(multiplier, currentLevel.toDouble()))
                                                .toInt()
                                }
                        val expansionLore = mutableListOf<net.kyori.adventure.text.Component>()
                        val separator = lang.getMessage(player, "gui.common.separator")

                        if (currentLevel == WorldData.EXPANSION_LEVEL_SPECIAL) {
                                expansionLore.addAll(
                                        lang.getComponentList(
                                                player,
                                                "gui.settings.expand.lore_no_border",
                                                mapOf()
                                        )
                                )
                        } else {
                                val targetLevel = currentLevel + 1
                                if (currentLevel < maxLevel) {
                                        if (stats.worldPoint < cost) {
                                                // Insufficient points
                                                val insufficient = cost - stats.worldPoint
                                                expansionLore.addAll(
                                                        lang.getComponentList(
                                                                player,
                                                                "gui.settings.expand.lore_insufficient",
                                                                mapOf(
                                                                        "current" to currentLevel,
                                                                        "max" to maxLevel,
                                                                        "cost" to cost,
                                                                        "level_before" to currentLevel,
                                                                        "level_after" to targetLevel,
                                                                        "shortage" to insufficient,
                                                                        "points" to stats.worldPoint
                                                                )
                                                        )
                                                )
                                        } else {
                                                // Able to upgrade
                                                expansionLore.addAll(
                                                        lang.getComponentList(
                                                                player,
                                                                "gui.settings.expand.lore_upgrade",
                                                                mapOf(
                                                                        "current" to currentLevel,
                                                                        "max" to maxLevel,
                                                                        "cost" to cost,
                                                                        "level_before" to currentLevel,
                                                                        "level_after" to targetLevel,
                                                                        "points" to stats.worldPoint
                                                                )
                                                        )
                                                )
                                        }
                                } else {
                                        // Max level
                                        expansionLore.addAll(
                                                lang.getComponentList(
                                                        player,
                                                        "gui.settings.expand.lore_max",
                                                        mapOf("current" to currentLevel, "max" to maxLevel)
                                                )
                                        )
                                }

                                val showOpenMenuHint = isInWorld && currentLevel < maxLevel
                                if (showOpenMenuHint) {
                                        val openMenuHintKey =
                                                if (isBedrock) {
                                                        "gui.settings.expand.lore_open_menu_hint_be"
                                                } else {
                                                        "gui.settings.expand.lore_open_menu_hint_je"
                                                }
                                        expansionLore.addAll(
                                                2,
                                                lang.getComponentList(player, openMenuHintKey)
                                        )
                                }

                                val borderInfo = buildCurrentBorderInfo(worldData, currentLevel)
                                expansionLore.addAll(
                                        lang.getComponentList(
                                                player,
                                                "gui.settings.expand.lore_border_info",
                                                mapOf(
                                                        "x" to formatDecimal(borderInfo.centerX),
                                                        "z" to formatDecimal(borderInfo.centerZ),
                                                        "size" to formatDecimal(borderInfo.size)
                                                )
                                        )
                                )
                                if (!isBedrock && isInWorld) {
                                        expansionLore.addAll(
                                                lang.getComponentList(
                                                        player,
                                                        "gui.settings.expand.lore_teleport_hint"
                                                )
                                        )
                                }
                                expansionLore.add(
                                        LegacyComponentSerializer.legacySection()
                                                .deserialize(separator)
                                                .decoration(TextDecoration.ITALIC, false)
                                )

                                if (!isInWorld && warningLore != null) {
                                        expansionLore.add(
                                                LegacyComponentSerializer.legacySection()
                                                        .deserialize(warningLore)
                                                        .decoration(TextDecoration.ITALIC, false)
                                        )
                                }
                        }

                        inventory.setItem(
                                23,
                                createItemComponent(
                                        plugin.menuConfigManager.getIconMaterial(
                                                "world_settings",
                                                "expand",
                                                Material.FILLED_MAP
                                        ),
                                        lang.getMessage(player, "gui.settings.expand.display"),
                                        expansionLore,
                                        ItemTag.TYPE_GUI_SETTING_EXPAND
                                )
                        )
                }

                // スロット24: 公開レベル変更 (オーナーのみ)
                if (isOwner) {
                        val levels =
                                listOf(
                                        Triple(
                                                PublishLevel.PUBLIC,
                                                lang.getMessage(player, "publish_level.public"),
                                                lang.getMessage(
                                                        player,
                                                        "publish_level.color.public"
                                                )
                                        ),
                                        Triple(
                                                PublishLevel.FRIEND,
                                                lang.getMessage(player, "publish_level.friend"),
                                                lang.getMessage(
                                                        player,
                                                        "publish_level.color.friend"
                                                )
                                        ),
                                        Triple(
                                                PublishLevel.PRIVATE,
                                                lang.getMessage(player, "publish_level.private"),
                                                lang.getMessage(
                                                        player,
                                                        "publish_level.color.private"
                                                )
                                        ),
                                        Triple(
                                                PublishLevel.LOCKED,
                                                lang.getMessage(player, "publish_level.locked"),
                                                lang.getMessage(
                                                        player,
                                                        "publish_level.color.locked"
                                                )
                                        )
                                )

                        val current = levels.find { it.first == worldData.publishLevel }
                        val currentLevelName =
                                current?.second ?: lang.getMessage(player, "general.unknown")
                        val currentColor = current?.third ?: "§f"
                        val inactiveColor = lang.getMessage(player, "publish_level.color.inactive")

                        // Build level_list
                        val levelList = levels.joinToString("\n") { (level, name, color) ->
                                val isActive = worldData.publishLevel == level
                                val prefix = if (isActive) lang.getMessage(player, "gui.settings.publish.active_prefix") else lang.getMessage(player, "gui.settings.publish.inactive_prefix")
                                val style = if (isActive) color else inactiveColor
                                "$prefix$style$name"
                        }

                        // Description
                        val configKey = worldData.publishLevel.name.lowercase()
                        val description = lang.getMessage(player, "publish_level.description.$configKey")

                        val publishLore = lang.getComponentList(
                                player,
                                if (isBedrock) {
                                        "gui.settings.publish.lore_bedrock"
                                } else {
                                        "gui.settings.publish.lore"
                                },
                                mapOf(
                                        "current_color" to currentColor,
                                        "current_level" to currentLevelName,
                                        "level_list" to levelList,
                                        "description" to description
                                )
                        ).toMutableList()

                        inventory.setItem(
                                24,
                                createItemComponent(
                                        plugin.menuConfigManager.getIconMaterial(
                                                "world_settings",
                                                "publish",
                                                Material.OAK_DOOR
                                        ),
                                        lang.getMessage(player, "gui.settings.publish.display"),
                                        publishLore,
                                        ItemTag.TYPE_GUI_SETTING_PUBLISH
                                )
                        )
                }

                // スロット25: メンバー管理 (オーナーのみ)
                if (isOwner) {

                        val totalCount = worldData.members.size + worldData.moderators.size + 1

                        // メンバーリストの作成 (Owner > Moderator > Member)
                        val allMemberData = mutableListOf<Triple<UUID, String, String>>()
                        val ownerRoleColor = lang.getMessage(player, "publish_level.color.owner")
                        val moderatorRoleColor =
                                lang.getMessage(player, "publish_level.color.moderator")
                        val memberRoleColor = lang.getMessage(player, "publish_level.color.member")

                        allMemberData.add(
                                Triple(
                                        worldData.owner,
                                        lang.getMessage(player, "gui.settings.member.role_owner"),
                                        ownerRoleColor
                                )
                        )
                        worldData.moderators.forEach {
                                allMemberData.add(
                                        Triple(
                                                it,
                                                lang.getMessage(
                                                        player,
                                                        "gui.settings.member.role_moderator"
                                                ),
                                                moderatorRoleColor
                                        )
                                )
                        }
                        worldData.members.forEach {
                                allMemberData.add(
                                        Triple(
                                                it,
                                                lang.getMessage(
                                                        player,
                                                        "gui.settings.member.role_member"
                                                ),
                                                memberRoleColor
                                        )
                                )
                        }

                        val maxDisplay = 10
                        val displayList = allMemberData.take(maxDisplay).joinToString("\n") { (uuid, role, color) ->
                                val playerName = PlayerNameUtil.getNameOrDefault(uuid, lang.getMessage(player, "general.unknown"))

                                val isOnline = Bukkit.getOfflinePlayer(uuid).isOnline
                                val nameColor = if (isOnline) "§a" else "§7"
                                val debugColor = "§8"

                                if (role == lang.getMessage(player, "gui.settings.member.role_member")) {
                                    lang.getMessage(player, "gui.settings.member.list_item_member", mapOf("name_color" to nameColor, "player" to playerName))
                                } else {
                                    lang.getMessage(player, "gui.settings.member.list_item", mapOf("debug_color" to debugColor, "role_color" to color, "role" to role, "name_color" to nameColor, "player" to playerName))
                                }
                        }

                        val memberListString = if (allMemberData.size > maxDisplay) {
                                val remaining = allMemberData.size - maxDisplay
                                val onlineCount = allMemberData.drop(maxDisplay).count { Bukkit.getOfflinePlayer(it.first).isOnline }
                                displayList + "\n" + lang.getMessage(player, "gui.settings.member.more_members", mapOf("remaining" to remaining, "online" to onlineCount))
                        } else {
                                displayList
                        }

                        val memberLore = lang.getComponentList(
                                player,
                                "gui.settings.member.lore",
                                mapOf(
                                        "count" to totalCount,
                                        "member_list" to memberListString
                                )
                        )

                        inventory.setItem(
                                25,
                                createItemComponent(
                                        plugin.menuConfigManager.getIconMaterial(
                                                "world_settings",
                                                "members",
                                                Material.PLAYER_HEAD
                                        ),
                                        lang.getMessage(player, "gui.settings.member.display"),
                                        memberLore,
                                        ItemTag.TYPE_GUI_SETTING_MEMBER
                                )
                        )
                }

                // タグ設定
                if (hasManagePermission) {
                        val tagsList = if (worldData.tags.isEmpty()) {
                                lang.getMessage(player, "gui.settings.tags.lore_empty")
                        } else {
                                worldData.tags.joinToString("\n") {
                                        lang.getMessage(
                                                player,
                                                "gui.settings.tags.lore_item",
                                                mapOf(
                                                        "tag" to
                                                                plugin.worldTagManager.getDisplayName(player, it)
                                                )
                                        )
                                }
                        }

                        val tagLore = lang.getComponentList(
                                player,
                                "gui.settings.tags.lore",
                                mapOf("tags_list" to tagsList)
                        )

                        inventory.setItem(
                                tagsSettingSlot,
                                createItemComponent(
                                        plugin.menuConfigManager.getIconMaterial(
                                                "world_settings",
                                                "tags",
                                                Material.BOOK
                                        ),
                                        lang.getMessage(player, "gui.settings.tags.display"),
                                        tagLore,
                                        ItemTag.TYPE_GUI_SETTING_TAGS
                                )
                        )
                }

                // 案内設定
                if (hasManagePermission) {
                        val messagePreview = if (worldData.announcementMessages.isNotEmpty()) {
                                mutableListOf<String>().apply {
                                    add("")
                                    add(lang.getMessage(player, "gui.settings.announcement.preview_header"))
                                    addAll(worldData.announcementMessages.map { "  $it" })
                                    add(lang.getMessage(player, "gui.settings.announcement.preview_footer"))
                                }.joinToString("\n")
                        } else {
                                ""
                        }

                        val adLore = lang.getComponentList(
                                player,
                                if (isBedrock) {
                                        "gui.settings.announcement.lore_bedrock"
                                } else {
                                        "gui.settings.announcement.lore"
                                },
                                mapOf("message_preview" to messagePreview)
                        )

                        inventory.setItem(
                                announcementSettingSlot,
                                createItemComponent(
                                        plugin.menuConfigManager.getIconMaterial(
                                                "world_settings",
                                                "announcement",
                                                Material.OAK_SIGN
                                        ),
                                        lang.getMessage(
                                                player,
                                                "gui.settings.announcement.display"
                                        ),
                                        adLore,
                                        ItemTag.TYPE_GUI_SETTING_ANNOUNCEMENT
                                )
                        )
                }

                // 通知設定
                if (hasManagePermission) {
                        val onlineColor = lang.getMessage(player, "publish_level.color.online")
                        val offlineColor = lang.getMessage(player, "publish_level.color.offline")
                        val statusColor =
                                if (worldData.notificationEnabled) onlineColor else offlineColor
                        val statusText =
                                if (worldData.notificationEnabled)
                                        lang.getMessage(player, "gui.settings.notification.on")
                                else lang.getMessage(player, "gui.settings.notification.off")

                        val notificationLore = lang.getComponentList(
                                player,
                                "gui.settings.notification.lore",
                                mapOf(
                                        "color" to statusColor,
                                        "status" to statusText
                                )
                        )

                        inventory.setItem(
                                notificationSettingSlot,
                                createItemComponent(
                                        if (worldData.notificationEnabled)
                                                plugin.menuConfigManager.getIconMaterial(
                                                        "world_settings",
                                                        "notification_on",
                                                        Material.BELL
                                                )
                                        else
                                                plugin.menuConfigManager.getIconMaterial(
                                                        "world_settings",
                                                        "notification_off",
                                                        Material.OAK_DOOR
                                                ),
                                        lang.getMessage(
                                                player,
                                                "gui.settings.notification.display"
                                        ),
                                        notificationLore,
                                        ItemTag.TYPE_GUI_SETTING_NOTIFICATION
                                )
                        )
                }

                // スロット32: 環境設定 (オーナーのみ)
                if (isOwner && !isBedrock) {
                        val lore =
                                lang.getMessageList(player, "gui.settings.environment.lore")
                                        .toMutableList()
                        if (!isInWorld && warningLore != null) {
                                lore.add("")
                                lore.add(warningLore)
                        }
                        inventory.setItem(
                                32,
                                createItem(
                                        plugin.menuConfigManager.getIconMaterial(
                                                "world_settings",
                                                "environment",
                                                Material.GRASS_BLOCK
                                        ),
                                        lang.getMessage(player, "gui.settings.environment.display"),
                                        lore,
                                        ItemTag.TYPE_GUI_SETTING_ENVIRONMENT
                                )
                        )
                }

                // スロット33: 重大な設定 (オーナーのみ)
                // スロット33: 重大な設定 (オーナーのみ)
                val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                if (isOwner && stats.criticalSettingsEnabled) {
                        val lore =
                                lang.getComponentList(player, "gui.settings.critical.lore")
                                        .toMutableList()
                        if (!isInWorld && warningLore != null) {
                                lore.add(net.kyori.adventure.text.Component.empty())
                                lore.add(LegacyComponentSerializer.legacySection().deserialize(warningLore))
                        }
                        inventory.setItem(
                                33,
                                createItemComponent(
                                        plugin.menuConfigManager.getIconMaterial(
                                                "world_settings",
                                                "critical",
                                                Material.TNT
                                        ),
                                        lang.getMessage(player, "gui.settings.critical.display"),
                                        lore,
                                        ItemTag.TYPE_GUI_SETTING_CRITICAL
                                )
                        )
                }

                // ワールド情報
                val currentLevel = worldData.borderExpansionLevel
                val costsSection = plugin.config.getConfigurationSection("expansion.costs")
                val maxLevel = costsSection?.getKeys(false)?.size ?: 3
                val totalCount = worldData.members.size + worldData.moderators.size + 1
                val onlineCount =
                        (worldData.members + worldData.moderators + worldData.owner).count {
                                Bukkit.getOfflinePlayer(it).isOnline
                        }

                // 公開レベル表示用
                val publishLevelColor =
                        when (worldData.publishLevel) {
                                PublishLevel.PUBLIC ->
                                        lang.getMessage(player, "publish_level.color.public")
                                PublishLevel.FRIEND ->
                                        lang.getMessage(player, "publish_level.color.friend")
                                PublishLevel.PRIVATE ->
                                        lang.getMessage(player, "publish_level.color.private")
                                PublishLevel.LOCKED ->
                                        lang.getMessage(player, "publish_level.color.locked")
                        }
                val publishLevelName =
                        lang.getMessage(
                                player,
                                "publish_level.${worldData.publishLevel.name.lowercase()}"
                        )

                // 有効期限の計算
                val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
                val expireDate =
                        try {
                                java.time.LocalDate.parse(worldData.expireDate, dateFormatter)
                        } catch (e: Exception) {
                                java.time.LocalDate.now().plusDays(7)
                        }
                val today = java.time.LocalDate.now()
                val daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(today, expireDate)
                val displayFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日")
                val dateStr = displayFormatter.format(expireDate)

                // 作成日の計算
                val createdAtDate =
                        try {
                                val dateTimeFormatter =
                                        java.time.format.DateTimeFormatter.ofPattern(
                                                "yyyy-MM-dd HH:mm:ss"
                                        )
                                java.time.LocalDateTime.parse(
                                                worldData.createdAt,
                                                dateTimeFormatter
                                        )
                                        .toLocalDate()
                        } catch (e: Exception) {
                                java.time.LocalDate.now()
                        }
                val daysSinceCreation =
                        java.time.temporal.ChronoUnit.DAYS.between(
                                createdAtDate,
                                java.time.LocalDate.now()
                        )
                val createdInfo =
                        if (daysSinceCreation == 0L) {
                                lang.getMessage(player, "gui.admin.world_item.created_info_today")
                        } else {
                                lang.getMessage(
                                        player,
                                        "gui.admin.world_item.created_info_days",
                                        mapOf("days" to daysSinceCreation)
                                )
                        }

                val infoLoreKey =
                        if (currentLevel == WorldData.EXPANSION_LEVEL_SPECIAL)
                                "gui.settings.main_info.lore_special"
                        else "gui.settings.main_info.lore"
                val infoLore =
                        lang.getMessageList(
                                        player,
                                        infoLoreKey,
                                        mapOf(
                                                "world" to worldData.name,
                                                "description" to
                                                        (if (worldData.description.isEmpty())
                                                                "__REMOVE_IF_EMPTY__"
                                                        else worldData.description),
                                                "owner" to PlayerNameUtil.getNameOrDefault(worldData.owner, lang.getMessage(player, "general.unknown")),

                                                "level" to
                                                        (if (currentLevel ==
                                                                        WorldData
                                                                                .EXPANSION_LEVEL_SPECIAL
                                                        )
                                                                "Special"
                                                        else currentLevel.toString()),
                                                "max_level" to maxLevel.toString(),
                                                "days_until_archive" to daysRemaining.toString(),
                                                "archive_date" to dateStr,
                                                "member_count" to totalCount.toString(),
                                                "online_count" to onlineCount.toString(),
                                                "publish_color" to publishLevelColor,
                                                "publish_level" to publishLevelName,
                                                "favorites" to worldData.favorite.toString(),
                                                "visitors" to
                                                        worldData.recentVisitors.sum().toString(),
                                                "created_at_date_formatted" to
                                                        displayFormatter.format(createdAtDate),
                                                "created_days_ago" to createdInfo
                                        )
                                )
                                .filter { !it.contains("__REMOVE_IF_EMPTY__") }
                                .toMutableList()
                infoLore.add(
                        "${lang.getMessage(player, "publish_level.color.uuid")}UUID: ${worldData.uuid}"
                )

                val infoItem =
                        createItem(
                                worldData.icon,
                                lang.getMessage(
                                        player,
                                        "gui.settings.main_info.name",
                                        mapOf("world" to worldData.name)
                                ),
                                infoLore,
                                ItemTag.TYPE_GUI_INFO
                        )
                ItemTag.setWorldUuid(infoItem, worldData.uuid)
                inventory.setItem(worldInfoSlot, infoItem)

                // スロット51: 訪問中のプレイヤー管理
                val visitors =
                        Bukkit.getWorld(targetWorldName)?.players?.filter {
                                it.uniqueId != worldData.owner &&
                                        !worldData.moderators.contains(it.uniqueId) &&
                                        !worldData.members.contains(it.uniqueId)
                        }
                                ?: emptyList()
                if (hasManagePermission && visitors.isNotEmpty()) {
                        val visitorLore = lang.getComponentList(
                                player,
                                "gui.settings.visitors.lore",
                                mapOf("count" to visitors.size)
                        )

                        inventory.setItem(
                                51,
                                createItemComponent(
                                        plugin.menuConfigManager.getIconMaterial(
                                                "world_settings",
                                                "visitor",
                                                Material.SPYGLASS
                                        ),
                                        lang.getMessage(player, "gui.settings.visitors.display"),
                                        visitorLore,
                                        ItemTag.TYPE_GUI_SETTING_VISITOR
                                )
                        )
                }

                // スロット52: 設置済みポータルの管理
                // ワールドオーナーのみ表示
                val hasPortals =
                        isOwner &&
                                plugin.portalRepository.findAll().any {
                                        it.worldName == targetWorldName
                                }
                if (hasPortals) {
                        val portalLore = lang.getComponentList(player, "gui.settings.portals.lore")
                        inventory.setItem(
                                52,
                                createItemComponent(
                                        plugin.menuConfigManager.getIconMaterial(
                                                "world_settings",
                                                "portals",
                                                Material.END_PORTAL_FRAME
                                        ),
                                        lang.getMessage(player, "gui.settings.portals.display"),
                                        portalLore,
                                        ItemTag.TYPE_GUI_SETTING_PORTALS
                                )
                        )
                }

                // 空きスロットを灰色板ガラスで埋める
                val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
                for (i in 0 until inventory.size) {
                        val item = inventory.getItem(i)
                        if (item == null || item.type == Material.AIR) {
                                inventory.setItem(i, grayPane)
                        }
                }

                if (player.openInventory.topInventory != inventory) {
                        player.openInventory(inventory)
                }
        }

        fun openArchiveConfirmation(player: Player, worldData: WorldData) {
                val lang = plugin.languageManager
                val title = lang.getMessage(player, "gui.archive_confirm.title")
                val currentTitle =
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                .plainText()
                                .serialize(player.openInventory.title())
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.ARCHIVE_WORLD,
                        isGui = true
                )
                me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(
                        plugin,
                        player,
                        "world_settings",
                        me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(title),
                        WorldSettingsGuiHolder::class.java
                )

                val inventory =
                        if (player.openInventory.topInventory.size == 45 && currentTitle == title) {
                                player.openInventory.topInventory
                        } else {
                                val holder = WorldSettingsGuiHolder()
                                val inv = Bukkit.createInventory(holder, 45, me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(title))
                                holder.inv = inv
                                inv
                        }

                me.awabi2048.myworldmanager.util.GuiHelper.applyConfirmationFrame(inventory)

                val infoItem =
                        createItem(
                                Material.PAPER,
                                lang.getMessage(player, "gui.archive.question"),
                                lang.getMessageList(player, "gui.archive.warning"),
                                ItemTag.TYPE_GUI_INFO
                        )
                ItemTag.setWorldUuid(infoItem, worldData.uuid)
                inventory.setItem(22, infoItem)

                inventory.setItem(
                        20,
                        createItem(
                                Material.LIME_WOOL,
                                lang.getMessage(player, "gui.archive.confirm"),
                                listOf(lang.getMessage(player, "gui.archive.confirm_desc")),
                                ItemTag.TYPE_GUI_CONFIRM
                        )
                )
                inventory.setItem(
                        24,
                        createItem(
                                Material.RED_WOOL,
                                lang.getMessage(player, "gui.archive.cancel"),
                                listOf(lang.getMessage(player, "gui.archive.cancel_desc")),
                                ItemTag.TYPE_GUI_CANCEL
                        )
                )

                if (player.openInventory.topInventory != inventory) {
                        player.openInventory(inventory)
                        scheduleGuiTransitionReset(plugin, player)
                }
        }

        fun openUnarchiveConfirmation(player: Player, worldData: WorldData) {
                val lang = plugin.languageManager
                val title = lang.getMessage(player, "gui.unarchive_confirm.title")
                val currentTitle =
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                .plainText()
                                .serialize(player.openInventory.title())
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.UNARCHIVE_CONFIRM,
                        isGui = true
                )
                me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(
                        plugin,
                        player,
                        "world_settings",
                        me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(title),
                        WorldSettingsGuiHolder::class.java
                )

                val inventory =
                        if (player.openInventory.topInventory.size == 45 && currentTitle == title) {
                                player.openInventory.topInventory
                        } else {
                                val holder = WorldSettingsGuiHolder()
                                val inv = Bukkit.createInventory(holder, 45, me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(title))
                                holder.inv = inv
                                inv
                        }

                me.awabi2048.myworldmanager.util.GuiHelper.applyConfirmationFrame(inventory)

                val infoItem =
                        createItem(
                                Material.PAPER,
                                lang.getMessage(player, "gui.unarchive_confirm.title"),
                                lang.getMessageList(player, "gui.unarchive_confirm.lore"),
                                ItemTag.TYPE_GUI_INFO
                        )
                ItemTag.setWorldUuid(infoItem, worldData.uuid)
                inventory.setItem(22, infoItem)

                inventory.setItem(
                        20,
                        createItem(
                                Material.LIME_CONCRETE,
                                lang.getMessage(player, "gui.common.confirm"),
                                emptyList<String>(),
                                ItemTag.TYPE_GUI_CONFIRM
                        )
                )

                inventory.setItem(
                        24,
                        createItem(
                                Material.RED_CONCRETE,
                                lang.getMessage(player, "gui.common.cancel"),
                                emptyList<String>(),
                                ItemTag.TYPE_GUI_CANCEL
                        )
                )

                if (player.openInventory.topInventory != inventory) {
                        player.openInventory(inventory)
                }
        }

        fun openExpansionMethodSelection(
                player: Player,
                @Suppress("UNUSED_PARAMETER") worldData: WorldData
        ) {
                val lang = plugin.languageManager
                val title = lang.getMessage(player, "gui.expansion.method_title")
                val currentTitle =
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                .plainText()
                                .serialize(player.openInventory.title())

                me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(
                        plugin,
                        player,
                        "world_settings",
                        me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(title),
                        WorldSettingsGuiHolder::class.java
                )
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.EXPAND_SELECT_METHOD,
                        isGui = true
                )
                scheduleGuiTransitionReset(plugin, player)

                val inventory =
                        if (player.openInventory.topInventory.size == 45 && currentTitle == title) {
                                player.openInventory.topInventory
                        } else {
                                val holder = WorldSettingsGuiHolder()
                                val inventory =
                                        Bukkit.createInventory(holder, 45, me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(title))
                                holder.inv = inventory
                                inventory
                        }

                // ヘッダー・フッター
                val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
                for (i in 0..8) inventory.setItem(i, blackPane)
                for (i in 36..44) inventory.setItem(i, blackPane)

                inventory.setItem(
                        20,
                        createItem(
                                Material.MAP,
                                lang.getMessage(player, "gui.expansion.center_expand.name"),
                                lang.getMessageList(player, "gui.expansion.center_expand.lore"),
                                ItemTag.TYPE_GUI_SETTING_EXPAND
                        )
                )

                inventory.setItem(
                        24,
                        createItem(
                                Material.COMPASS,
                                lang.getMessage(player, "gui.expansion.direction_expand.name"),
                                lang.getMessageList(player, "gui.expansion.direction_expand.lore"),
                                ItemTag.TYPE_GUI_SETTING_EXPAND_DIRECTION
                        )
                )

                // 戻るボタン
                inventory.setItem(
                        40,
                        me.awabi2048.myworldmanager.util.GuiHelper.createReturnItem(
                                plugin,
                                player,
                                "world_settings"
                        )
                )

                val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
                for (i in 0 until inventory.size) {
                        if (inventory.getItem(i) == null) inventory.setItem(i, grayPane)
                }

                player.openInventory(inventory)
        }

        fun openExpansionConfirmation(
                player: Player,
                worldUuid: UUID,
                direction: org.bukkit.block.BlockFace?,
                cost: Int
        ) {
                val lang = plugin.languageManager
                val title = lang.getMessage(player, "gui.expansion.confirm_title")
                val currentTitle =
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                .plainText()
                                .serialize(player.openInventory.title())
                me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(
                        plugin,
                        player,
                        "world_settings",
                        me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(title),
                        WorldSettingsGuiHolder::class.java
                )
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldUuid,
                        SettingsAction.EXPAND_CONFIRM,
                        isGui = true
                )
                scheduleGuiTransitionReset(plugin, player)
                val inventory =
                        if (player.openInventory.topInventory.size == 45 && currentTitle == title) {
                                player.openInventory.topInventory
                        } else {
                                val holder = WorldSettingsGuiHolder()
                                val inventory =
                                        Bukkit.createInventory(holder, 45, me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(title))
                                holder.inv = inventory
                                inventory
                        }

                // ヘッダー・フッター
                val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
                for (i in 0..8) inventory.setItem(i, blackPane)
                for (i in 36..44) inventory.setItem(i, blackPane)

                val directionKey =
                        when (direction) {
                                org.bukkit.block.BlockFace.NORTH_WEST ->
                                        "general.direction.north_west"
                                org.bukkit.block.BlockFace.NORTH_EAST ->
                                        "general.direction.north_east"
                                org.bukkit.block.BlockFace.SOUTH_WEST ->
                                        "general.direction.south_west"
                                org.bukkit.block.BlockFace.SOUTH_EAST ->
                                        "general.direction.south_east"
                                else -> "general.direction.unknown"
                        }
                val directionName = lang.getMessage(player, directionKey)
                val methodText =
                        if (direction == null)
                                lang.getMessage(player, "gui.expansion.method_center")
                        else
                                lang.getMessage(
                                        player,
                                        "gui.expansion.method_direction",
                                        mapOf("direction" to directionName)
                                )

                inventory.setItem(
                        22,
                        createItem(
                                Material.BOOK,
                                lang.getMessage(player, "gui.expansion.confirm_info"),
                                listOf(
                                        lang.getMessage(
                                                player,
                                                "gui.expansion.method",
                                                mapOf("method" to methodText)
                                        ),
                                        lang.getMessage(
                                                player,
                                                "gui.expansion.cost",
                                                mapOf("cost" to cost)
                                        ),
                                        "",
                                        lang.getMessage(player, "gui.expansion.warning")
                                ),
                                ItemTag.TYPE_GUI_INFO
                        )
                )

                inventory.setItem(
                        20,
                        createItem(
                                Material.LIME_WOOL,
                                lang.getMessage(player, "gui.expansion.execute"),
                                listOf(lang.getMessage(player, "gui.expansion.execute_desc")),
                                ItemTag.TYPE_GUI_CONFIRM
                        )
                )
                inventory.setItem(
                        24,
                        createItem(
                                Material.RED_WOOL,
                                lang.getMessage(player, "gui.expansion.cancel"),
                                listOf(lang.getMessage(player, "gui.expansion.cancel_desc")),
                                ItemTag.TYPE_GUI_CANCEL
                        )
                )

                val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
                for (i in 0 until inventory.size) {
                        if (inventory.getItem(i) == null) inventory.setItem(i, grayPane)
                }

                player.openInventory(inventory)
        }

        fun openMemberManagement(
                player: Player,
                worldData: WorldData,
                page: Int = 0,
                playSound: Boolean = true
        ) {
                val lang = plugin.languageManager
                val title = lang.getMessage(player, "gui.member_management.title")
                val currentTitle =
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                .plainText()
                                .serialize(player.openInventory.title())

                if (playSound) {
                        me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(
                                plugin,
                                player,
                                "world_settings",
                                me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(title),
                                WorldSettingsGuiHolder::class.java
                        )
                }
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.MANAGE_MEMBERS,
                        isGui = true
                )
                scheduleGuiTransitionReset(plugin, player)

                val allEntries = mutableListOf<MemberManagementEntry>()
                allEntries.add(
                        MemberManagementEntry(
                                playerUuid = worldData.owner,
                                role = lang.getMessage(player, "role.owner")
                        )
                )
                worldData.moderators.forEach {
                        allEntries.add(
                                MemberManagementEntry(
                                        playerUuid = it,
                                        role = lang.getMessage(player, "role.moderator")
                                )
                        )
                }
                worldData.members.forEach {
                        allEntries.add(
                                MemberManagementEntry(
                                        playerUuid = it,
                                        role = lang.getMessage(player, "role.member")
                                )
                        )
                }

                val pendingInvites =
                        plugin.pendingInteractionRepository
                                .findByWorldAndType(worldData.uuid, PendingInteractionType.MEMBER_INVITE)
                pendingInvites.forEach { invite ->
                        allEntries.add(
                                MemberManagementEntry(
                                        playerUuid = invite.targetUuid,
                                        pendingDecisionId = invite.id,
                                        pendingCreatedAt = invite.createdAt,
                                        pendingType = PendingInteractionType.MEMBER_INVITE
                                )
                        )
                }

                val pendingRequests =
                        plugin.pendingInteractionRepository
                                .findByWorldAndType(worldData.uuid, PendingInteractionType.MEMBER_REQUEST)
                pendingRequests.forEach { request ->
                        allEntries.add(
                                MemberManagementEntry(
                                        playerUuid = request.actorUuid,
                                        pendingDecisionId = request.id,
                                        pendingCreatedAt = request.createdAt,
                                        pendingType = PendingInteractionType.MEMBER_REQUEST
                                )
                        )
                }

                val itemsPerPage = 28
                val maxPage = ((allEntries.size - 1).coerceAtLeast(0)) / itemsPerPage
                val currentPage = page.coerceIn(0, maxPage)
                plugin.settingsSessionManager
                        .getSession(player)
                        ?.setMetadata("member_management_page", currentPage)
                val startIndex = currentPage * itemsPerPage
                val currentPageMembers = allEntries.drop(startIndex).take(itemsPerPage)

                // 行数を計算 (ヘッダー+中身+フッター)
                val contentRows =
                        if (currentPageMembers.isEmpty()) 1
                        else (currentPageMembers.size - 1) / 7 + 1
                val rowCount = (contentRows + 2).coerceIn(3, 6)

                val inventory =
                        if (player.openInventory.topInventory.size == rowCount * 9 &&
                                        currentTitle == title
                        ) {
                                player.openInventory.topInventory
                        } else {
                                val holder = WorldSettingsGuiHolder()
                                val inventory =
                                        Bukkit.createInventory(
                                                holder,
                                                rowCount * 9,
                                                me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(title)
                                        )
                                holder.inv = inventory
                                inventory
                        }
                inventory.clear()

                val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
                for (i in 0..8) inventory.setItem(i, blackPane)

                val footerStart = (rowCount - 1) * 9
                for (i in 0..8) inventory.setItem(footerStart + i, blackPane)

                // メンバーリストの描画
                val isAdminFlow = plugin.settingsSessionManager.getSession(player)?.isAdminFlow == true
                val canManageRoles = worldData.owner == player.uniqueId || isAdminFlow
                currentPageMembers.forEachIndexed { index, entry ->
                        val row = index / 7
                        val col = index % 7
                        val slot = (row + 1) * 9 + (col + 1)
                                val memberItem =
                                        if (entry.pendingDecisionId != null) {
                                        createPendingItem(
                                                viewer = player,
                                                targetUuid = entry.playerUuid,
                                                decisionId = entry.pendingDecisionId,
                                                createdAt = entry.pendingCreatedAt ?: 0L,
                                                pendingType = entry.pendingType ?: PendingInteractionType.MEMBER_INVITE
                                        )
                                } else {
                                        createMemberItem(
                                                player,
                                                entry.playerUuid,
                                                entry.role ?: lang.getMessage(player, "role.member"),
                                                canManageRoles
                                        )
                                }
                        inventory.setItem(slot, memberItem)
                }

                // ナビゲーション
                if (currentPage > 0) {
                        inventory.setItem(
                                footerStart + 1,
                                me.awabi2048.myworldmanager.util.GuiHelper.createPrevPageItem(
                                        plugin,
                                        player,
                                        "world_settings",
                                        currentPage - 1
                                )
                        )
                }
                if (startIndex + itemsPerPage < allEntries.size) {
                        inventory.setItem(
                                footerStart + 8,
                                me.awabi2048.myworldmanager.util.GuiHelper.createNextPageItem(
                                        plugin,
                                        player,
                                        "world_settings",
                                        currentPage + 1
                                )
                        )
                }

                // 戻るボタン (最終行1スロット目)
                inventory.setItem(
                        footerStart,
                        me.awabi2048.myworldmanager.util.GuiHelper.createReturnItem(
                                plugin,
                                player,
                                "world_settings"
                        )
                )

                // メンバー招待ボタン
                val canForceAddMember = PermissionManager.canForceAddMember(player)
                val inviteLore =
                        if (canForceAddMember) {
                                listOf(
                                        lang.getMessage(player, "gui.common.separator"),
                                        lang.getMessage(player, "gui.member_management.invite.desc"),
                                        lang.getMessage(player, "gui.common.separator"),
                                        lang.getMessage(player, "gui.member_management.invite.click_normal"),
                                        lang.getMessage(player, "gui.member_management.invite.click_force"),
                                        lang.getMessage(player, "gui.common.separator")
                                )
                        } else {
                                listOf(lang.getMessage(player, "gui.member_management.invite.desc"))
                        }
                inventory.setItem(
                        footerStart + 4,
                        createItem(
                                Material.PAPER,
                                lang.getMessage(player, "gui.member_management.invite.name"),
                                inviteLore,
                                ItemTag.TYPE_GUI_MEMBER_INVITE
                        )
                )

                // 背景埋め
                val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
                for (i in 9 until footerStart) {
                        if (inventory.getItem(i) == null) inventory.setItem(i, grayPane)
                }

                player.openInventory(inventory)
        }

        fun openMemberPendingInviteCancelConfirmation(
                player: Player,
                worldData: WorldData,
                targetUuid: UUID,
                decisionId: UUID
        ) {
                val lang = plugin.languageManager
                val targetName = PlayerNameUtil.getNameOrDefault(targetUuid, lang.getMessage(player, "general.unknown"))
                val title = lang.getMessage(player, "gui.member_management.pending_cancel_confirm.title")
                val currentTitle =
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                .plainText()
                                .serialize(player.openInventory.title())
                me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(
                        plugin,
                        player,
                        "world_settings",
                        me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(title),
                        WorldSettingsGuiHolder::class.java
                )
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.MEMBER_PENDING_INVITE_CANCEL_CONFIRM,
                        isGui = true
                )
                scheduleGuiTransitionReset(plugin, player)

                val inventory =
                        if (player.openInventory.topInventory.size == 45 && currentTitle == title) {
                                player.openInventory.topInventory
                        } else {
                                val holder = WorldSettingsGuiHolder()
                                val inv = Bukkit.createInventory(holder, 45, me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(title))
                                holder.inv = inv
                                inv
                        }

                me.awabi2048.myworldmanager.util.GuiHelper.applyConfirmationFrame(inventory)

                val item = ItemStack(Material.PLAYER_HEAD)
                val meta = item.itemMeta as? SkullMeta ?: return
                meta.owningPlayer = Bukkit.getOfflinePlayer(targetUuid)
                meta.displayName(
                        LegacyComponentSerializer.legacySection()
                                .deserialize(
                                        lang.getMessage(
                                                player,
                                                "gui.member_management.pending_item.name",
                                                mapOf("player" to targetName)
                                        )
                                )
                                .decoration(TextDecoration.ITALIC, false)
                )
                meta.lore(
                        listOf(
                                lang.getComponent(
                                        player,
                                        "gui.member_management.pending_cancel_confirm.body",
                                        mapOf("player" to targetName)
                                )
                        )
                )
                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_INFO)
                ItemTag.setWorldUuid(item, targetUuid)
                ItemTag.setString(item, "member_pending_invite_id", decisionId.toString())
                inventory.setItem(22, item)

                inventory.setItem(
                        20,
                        createItem(
                                Material.LIME_WOOL,
                                lang.getMessage(player, "gui.member_management.pending_cancel_confirm.cancel"),
                                emptyList(),
                                ItemTag.TYPE_GUI_CANCEL
                        )
                )
                inventory.setItem(
                        24,
                        createItem(
                                Material.RED_WOOL,
                                lang.getMessage(player, "gui.member_management.pending_cancel_confirm.confirm"),
                                emptyList(),
                                ItemTag.TYPE_GUI_CONFIRM
                        )
                )

                player.openInventory(inventory)
        }

        fun openMemberRemoveConfirmation(
                player: Player,
                worldData: WorldData,
                targetUuid: java.util.UUID
        ) {
                val lang = plugin.languageManager
                val targetName = PlayerNameUtil.getNameOrDefault(targetUuid, lang.getMessage(player, "general.unknown"))

                val title =
                        lang.getMessage(
                                player,
                                "gui.member_management.remove_confirm.title",
                                mapOf("player" to targetName)
                        )
                val currentTitle =
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                .plainText()
                                .serialize(player.openInventory.title())
                me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(
                        plugin,
                        player,
                        "world_settings",
                        me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(title),
                        WorldSettingsGuiHolder::class.java
                )
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.MEMBER_REMOVE_CONFIRM,
                        isGui = true
                )
                scheduleGuiTransitionReset(plugin, player)
                val inventory =
                        if (player.openInventory.topInventory.size == 45 && currentTitle == title) {
                                player.openInventory.topInventory
                        } else {
                                val holder = WorldSettingsGuiHolder()
                                val inventory =
                                        Bukkit.createInventory(holder, 45, me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(title))
                                holder.inv = inventory
                                inventory
                        }

                me.awabi2048.myworldmanager.util.GuiHelper.applyConfirmationFrame(inventory)

                val lore = lang.getComponentList(
                        player,
                        "gui.member_management.remove_confirm.lore",
                        mapOf(
                                "player" to PlayerNameUtil.getNameOrDefault(targetUuid, lang.getMessage(player, "general.unknown")),

                                "world" to worldData.name
                        )
                )

                val infoItem =
                        createItemComponent(
                                Material.PLAYER_HEAD,
                                lang.getMessage(
                                        player,
                                        "gui.member_management.remove_confirm.title",
                                        mapOf(
                                                "player" to PlayerNameUtil.getNameOrDefault(targetUuid, lang.getMessage(player, "general.unknown"))

                                        )
                                ),
                                lore,
                                ItemTag.TYPE_GUI_INFO
                        )
                ItemTag.setWorldUuid(infoItem, targetUuid) // 削除対象のUUIDを保存
                inventory.setItem(22, infoItem)

                inventory.setItem(
                        20,
                        createItem(
                                Material.LIME_WOOL,
                                lang.getMessage(
                                        player,
                                        "gui.member_management.remove_confirm.cancel"
                                ),
                                listOf(
                                        lang.getMessage(
                                                player,
                                                "gui.member_management.remove_confirm.cancel_desc"
                                        )
                                ),
                                ItemTag.TYPE_GUI_CANCEL
                        )
                )
                inventory.setItem(
                        24,
                        createItem(
                                Material.RED_WOOL,
                                lang.getMessage(
                                        player,
                                        "gui.member_management.remove_confirm.confirm"
                                ),
                                listOf(
                                        lang.getMessage(
                                                player,
                                                "gui.member_management.remove_confirm.confirm_desc"
                                        )
                                ),
                                ItemTag.TYPE_GUI_CONFIRM
                        )
                )


                player.openInventory(inventory)
        }

        fun openMemberTransferConfirmation(
                player: Player,
                worldData: WorldData,
                targetUuid: java.util.UUID
        ) {
                val lang = plugin.languageManager
                val targetName = PlayerNameUtil.getNameOrDefault(targetUuid, lang.getMessage(player, "general.unknown"))

                val title =
                        lang.getMessage(
                                player,
                                "gui.member_management.transfer_confirm.title",
                                mapOf("player" to targetName)
                        )
                val currentTitle =
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                .plainText()
                                .serialize(player.openInventory.title())
                me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(
                        plugin,
                        player,
                        "world_settings",
                        me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(title),
                        WorldSettingsGuiHolder::class.java
                )
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.MEMBER_TRANSFER_CONFIRM,
                        isGui = true
                )
                scheduleGuiTransitionReset(plugin, player)
                val inventory =
                        if (player.openInventory.topInventory.size == 45 && currentTitle == title) {
                                player.openInventory.topInventory
                        } else {
                                val holder = WorldSettingsGuiHolder()
                                val inventory =
                                        Bukkit.createInventory(holder, 45, me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(title))
                                holder.inv = inventory
                                inventory
                        }

                me.awabi2048.myworldmanager.util.GuiHelper.applyConfirmationFrame(inventory)

                val lore = lang.getComponentList(
                        player,
                        "gui.member_management.transfer_confirm.lore",
                        mapOf(
                                "player" to targetName,
                                "world" to worldData.name
                        )
                )

                val infoItem =
                        createItemComponent(
                                Material.PLAYER_HEAD,
                                lang.getMessage(
                                        player,
                                        "gui.member_management.transfer_confirm.title",
                                        mapOf("player" to targetName)
                                ),
                                lore,
                                ItemTag.TYPE_GUI_INFO
                        )
                ItemTag.setWorldUuid(infoItem, targetUuid)
                inventory.setItem(22, infoItem)

                inventory.setItem(
                        20,
                        createItem(
                                Material.LIME_WOOL,
                                lang.getMessage(
                                        player,
                                        "gui.member_management.transfer_confirm.cancel"
                                ),
                                listOf(
                                        lang.getMessage(
                                                player,
                                                "gui.member_management.transfer_confirm.cancel_desc"
                                        )
                                ),
                                ItemTag.TYPE_GUI_CANCEL
                        )
                )
                inventory.setItem(
                        24,
                        createItem(
                                Material.RED_WOOL,
                                lang.getMessage(
                                        player,
                                        "gui.member_management.transfer_confirm.confirm"
                                ),
                                listOf(
                                        lang.getMessage(
                                                player,
                                                "gui.member_management.transfer_confirm.confirm_desc"
                                        )
                                ),
                                ItemTag.TYPE_GUI_CONFIRM
                        )
                )

                player.openInventory(inventory)
        }

        private fun createMemberItem(
                viewer: Player,
                uuid: java.util.UUID,
                role: String,
                isOwner: Boolean
        ): ItemStack {
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

                meta.displayName(
                        Component.text("$color$displayName")
                                .decoration(TextDecoration.ITALIC, false)
                )

                val itemLore = mutableListOf<Component>()
                
                // Info section
                val lastOnlineVal = if (!isOnline) {
                        @Suppress("DEPRECATION")
                        val date = java.time.Instant.ofEpochMilli(player.lastPlayed)
                                .atZone(java.time.ZoneId.systemDefault())
                                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        val onlineLabel = lang.getMessage(viewer, "gui.member_management.item.last_online_label")
                        "§f§l| §7$onlineLabel §f$date"
                } else {
                        ""
                }

                itemLore.addAll(
                    lang.getComponentList(
                        viewer, 
                        "gui.member_management.item.lore_info",
                        mapOf(
                            "last_online" to lastOnlineVal,
                            "role" to role
                        )
                    ).filter { 
                        // Filter out empty lines that result from empty last_online
                        val text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(it)
                        text.isNotBlank() 
                    }
                )

                if (isOwner && role != lang.getMessage(viewer, "role.owner")) {
                        val nextRole = if (role == lang.getMessage(null as Player?, "role.member")) {
                                lang.getMessage(null as Player?, "role.moderator")
                        } else {
                                lang.getMessage(null as Player?, "role.member")
                        }
                        val actionLoreKey =
                                if (plugin.playerPlatformResolver.isBedrock(viewer)) {
                                        "gui.member_management.item.lore_actions_bedrock"
                                } else {
                                        "gui.member_management.item.lore_actions"
                                }

                        itemLore.addAll(
                            lang.getComponentList(
                                viewer,
                                actionLoreKey,
                                mapOf("next_role" to nextRole)
                            )
                        )
                }

                meta.lore(itemLore)
                item.itemMeta = meta
                
                me.awabi2048.myworldmanager.util.ItemTag.tagItem(item, ItemTag.TYPE_GUI_MEMBER_ITEM)
                me.awabi2048.myworldmanager.util.ItemTag.setWorldUuid(item, uuid)
                
                return item
        }

        private fun createPendingItem(
                viewer: Player,
                targetUuid: UUID,
                decisionId: UUID,
                createdAt: Long,
                pendingType: PendingInteractionType
        ): ItemStack {
                val worldName =
                        plugin.worldConfigRepository
                                .findByUuid(
                                        plugin.settingsSessionManager.getSession(viewer)?.worldUuid
                                                ?: return ItemStack(Material.BARRIER)
                                )
                                ?.name
                                ?: plugin.languageManager.getMessage(viewer, "general.unknown")
                val item =
                        PendingInteractionItemFactory.createItem(
                                plugin = plugin,
                                viewer = viewer,
                                subjectUuid = targetUuid,
                                type =
                                        when (pendingType) {
                                                PendingInteractionType.MEMBER_INVITE -> me.awabi2048.myworldmanager.service.PendingDecisionManager.PendingType.MEMBER_INVITE
                                                PendingInteractionType.MEMBER_REQUEST -> me.awabi2048.myworldmanager.service.PendingDecisionManager.PendingType.MEMBER_REQUEST
                                        },
                                worldName = worldName,
                                createdAt = createdAt,
                                decisionId = decisionId,
                                actionMode = PendingInteractionActionMode.CANCEL,
                                itemTagType =
                                        if (pendingType == PendingInteractionType.MEMBER_INVITE) {
                                                ItemTag.TYPE_GUI_MEMBER_PENDING_INVITE
                                        } else {
                                                ItemTag.TYPE_GUI_MEMBER_PENDING_REQUEST
                                        }
                        )
                ItemTag.setWorldUuid(item, targetUuid)
                ItemTag.setString(item, "member_pending_invite_id", decisionId.toString())
                return item
        }

        private fun formatPendingInviteDateTimeForPlayer(player: Player, timestamp: Long): String {
                val dateTime = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime()
                return dateTime.format(pendingInviteDateTimeFormatterFor(player))
        }

        private fun pendingInviteDateTimeFormatterFor(player: Player): DateTimeFormatter {
                val language = plugin.playerStatsRepository.findByUuid(player.uniqueId).language.lowercase(Locale.ROOT)
                return if (language == "ja_jp") {
                        DateTimeFormatter.ofPattern("yyyy/M/d H:mm")
                } else {
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                }
        }

        fun openVisitorManagement(player: Player, worldData: WorldData, page: Int = 0) {
                val lang = plugin.languageManager
                val title = lang.getMessage(player, "gui.visitor_management.title")
                me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(
                        plugin,
                        player,
                        "world_settings",
                        me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(title)
                )
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.MANAGE_VISITORS,
                        isGui = true
                )
                scheduleGuiTransitionReset(plugin, player)
                val world = Bukkit.getWorld("my_world.${worldData.uuid}") ?: return
                val visitorPlayers =
                        world.players.filter {
                                it.uniqueId != worldData.owner &&
                                        !worldData.moderators.contains(it.uniqueId) &&
                                        !worldData.members.contains(it.uniqueId)
                        }

                val itemsPerPage = 28
                val startIndex = page * itemsPerPage
                val currentPageVisitors = visitorPlayers.drop(startIndex).take(itemsPerPage)

                // 行数を計算
                val contentRows =
                        if (currentPageVisitors.isEmpty()) 1
                        else (currentPageVisitors.size - 1) / 7 + 1
                val rowCount = (contentRows + 2).coerceIn(3, 6)

                val currentTitle =
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                .plainText()
                                .serialize(player.openInventory.title())
                val inventory =
                        if (player.openInventory.topInventory.size == rowCount * 9 &&
                                        currentTitle == title
                        ) {
                                player.openInventory.topInventory
                        } else {
                                val holder = WorldSettingsGuiHolder()
                                val inventory =
                                        Bukkit.createInventory(
                                                holder,
                                                rowCount * 9,
                                                me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(title)
                                        )
                                holder.inv = inventory
                                inventory
                        }

                val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
                for (i in 0..8) inventory.setItem(i, blackPane)

                val footerStart = (rowCount - 1) * 9
                for (i in 0..8) inventory.setItem(footerStart + i, blackPane)

                // プレイヤーリストの描画
                val isAdminFlow = plugin.settingsSessionManager.getSession(player)?.isAdminFlow == true
                val canKick =
                        worldData.owner == player.uniqueId ||
                                worldData.moderators.contains(player.uniqueId) ||
                                isAdminFlow

                currentPageVisitors.forEachIndexed { index, visitor ->
                        val row = index / 7
                        val col = index % 7
                        val slot = (row + 1) * 9 + (col + 1)
                        inventory.setItem(
                                slot,
                                createVisitorItem(player, visitor.uniqueId, canKick)
                        )
                }

                // ナビゲーション
                if (page > 0) {
                        inventory.setItem(
                                footerStart + 1,
                                createItem(
                                        Material.ARROW,
                                        lang.getMessage(player, "gui.common.prev_page"),
                                        listOf("PAGE_TARGET: ${page - 1}"),
                                        ItemTag.TYPE_GUI_NAV_PREV
                                )
                        )
                }
                if (startIndex + itemsPerPage < visitorPlayers.size) {
                        inventory.setItem(
                                footerStart + 8,
                                createItem(
                                        Material.ARROW,
                                        lang.getMessage(player, "gui.common.next_page"),
                                        listOf("PAGE_TARGET: ${page + 1}"),
                                        ItemTag.TYPE_GUI_NAV_NEXT
                                )
                        )
                }

                // 戻るボタン
                inventory.setItem(
                        footerStart,
                        createItem(
                                Material.REDSTONE,
                                lang.getMessage(player, "gui.common.back"),
                                emptyList<String>(),
                                ItemTag.TYPE_GUI_CANCEL
                        )
                )

                // 背景埋め
                val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
                for (i in 9 until footerStart) {
                        if (inventory.getItem(i) == null) inventory.setItem(i, grayPane)
                }

                player.openInventory(inventory)
        }

        fun openVisitorKickConfirmation(
                player: Player,
                @Suppress("UNUSED_PARAMETER") worldData: WorldData,
                targetUuid: java.util.UUID
        ) {
                val lang = plugin.languageManager
                val targetName = PlayerNameUtil.getNameOrDefault(targetUuid, lang.getMessage(player, "general.unknown"))

                val title =
                        lang.getMessage(
                                player,
                                "gui.visitor_management.kick_confirm.title",
                                mapOf("player" to targetName)
                        )
                val currentTitle =
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                .plainText()
                                .serialize(player.openInventory.title())
                me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(
                        plugin,
                        player,
                        "world_settings",
                        me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(title)
                )
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.VISITOR_KICK_CONFIRM,
                        isGui = true
                )
                scheduleGuiTransitionReset(plugin, player)
                val inventory =
                        if (player.openInventory.topInventory.size == 45 && currentTitle == title) {
                                player.openInventory.topInventory
                        } else {
                                val holder = WorldSettingsGuiHolder()
                                val inventory =
                                        Bukkit.createInventory(holder, 45, me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(title))
                                holder.inv = inventory
                                inventory
                        }

                me.awabi2048.myworldmanager.util.GuiHelper.applyConfirmationFrame(inventory)

                inventory.setItem(
                        22,
                        createItem(
                                Material.PAPER,
                                lang.getMessage(
                                        player,
                                        "gui.visitor_management.kick_confirm.question"
                                ),
                                listOf(
                                        lang.getMessage(
                                                player,
                                                "gui.visitor_management.kick_confirm.player",
                                                mapOf("player" to targetName)
                                        ),
                                        "",
                                        "${lang.getMessage(player, "publish_level.color.uuid")}UUID: $targetUuid"
                                ),
                                ItemTag.TYPE_GUI_INFO
                        )
                )

                inventory.setItem(
                        20,
                        createItem(
                                Material.LIME_WOOL,
                                lang.getMessage(
                                        player,
                                        "gui.visitor_management.kick_confirm.cancel"
                                ),
                                listOf(
                                        lang.getMessage(
                                                player,
                                                "gui.visitor_management.kick_confirm.cancel_desc"
                                        )
                                ),
                                ItemTag.TYPE_GUI_CANCEL
                        )
                )
                inventory.setItem(
                        24,
                        createItem(
                                Material.RED_WOOL,
                                lang.getMessage(
                                        player,
                                        "gui.visitor_management.kick_confirm.confirm"
                                ),
                                listOf(
                                        lang.getMessage(
                                                player,
                                                "gui.visitor_management.kick_confirm.confirm_desc"
                                        )
                                ),
                                ItemTag.TYPE_GUI_CONFIRM
                        )
                )

                player.openInventory(inventory)
        }

        private fun createVisitorItem(
                viewer: Player,
                uuid: java.util.UUID,
                canKick: Boolean
        ): ItemStack {
                val lang = plugin.languageManager
                val player = Bukkit.getOfflinePlayer(uuid)
                val isOnline = player.isOnline
                val onlineColor = lang.getMessage(viewer, "publish_level.color.online")
                val offlineColor = lang.getMessage(viewer, "publish_level.color.offline")
                val color = if (isOnline) onlineColor else offlineColor

                val item = ItemStack(Material.PLAYER_HEAD)
                val meta = item.itemMeta as? org.bukkit.inventory.meta.SkullMeta ?: return item
                meta.owningPlayer = player

                meta.displayName(
                        LegacyComponentSerializer.legacySection()
                                .deserialize(
                                        "$color${player.name ?: lang.getMessage(viewer, "general.unknown")}"
                                )
                                .decoration(TextDecoration.ITALIC, false)
                )

                val lore = mutableListOf<String>()
                val separator = lang.getMessage(viewer, "gui.common.separator")

                lore.add(separator)
                val statusText =
                        if (isOnline) lang.getMessage(viewer, "status.online")
                        else lang.getMessage(viewer, "status.offline")
                val statusColor = if (isOnline) onlineColor else offlineColor
                lore.add(
                        lang.getMessage(
                                viewer,
                                "gui.common.status_display",
                                mapOf("color" to statusColor, "status" to statusText)
                        )
                )
                lore.add(separator)

                if (canKick) {
                        val kickKey =
                                if (plugin.playerPlatformResolver.isBedrock(viewer)) {
                                        "gui.visitor_management.item.kick_bedrock"
                                } else {
                                        "gui.visitor_management.item.kick"
                                }
                        lore.add(lang.getMessage(viewer, kickKey))
                        lore.add(separator)
                }

                meta.lore(lore.map { LegacyComponentSerializer.legacySection().deserialize(it).decoration(TextDecoration.ITALIC, false) })
                item.itemMeta = meta

                ItemTag.tagItem(item, ItemTag.TYPE_GUI_VISITOR_ITEM)
                ItemTag.setWorldUuid(item, uuid)
                return item
        }

        private fun createItem(
                material: Material,
                name: String,
                loreLines: List<String>,
                tag: String
        ): ItemStack {
                return GuiItemFactory.textItem(material, name, loreLines, tag)
        }

        private data class BorderInfo(
                val centerX: Double,
                val centerZ: Double,
                val size: Double
        )

        private fun buildCurrentBorderInfo(worldData: WorldData, currentLevel: Int): BorderInfo {
                val worldName = worldData.customWorldName ?: "my_world.${worldData.uuid}"
                val world = Bukkit.getWorld(worldName)
                if (world != null) {
                        val border = world.worldBorder
                        return BorderInfo(
                                centerX = border.center.x,
                                centerZ = border.center.z,
                                size = border.size
                        )
                }

                val center =
                        worldData.borderCenterPos
                                ?: worldData.spawnPosMember
                                ?: worldData.spawnPosGuest
                                ?: plugin.server.worlds.firstOrNull()?.spawnLocation
                val initialSize = plugin.config.getDouble("expansion.initial_size", 100.0)
                val size = initialSize * Math.pow(2.0, currentLevel.toDouble())
                return BorderInfo(
                        centerX = center?.x ?: 0.0,
                        centerZ = center?.z ?: 0.0,
                        size = size
                )
        }

        private fun formatDecimal(value: Double): String {
                val rounded = Math.round(value)
                return if (kotlin.math.abs(value - rounded.toDouble()) < 0.000001) {
                        rounded.toString()
                } else {
                        String.format(Locale.US, "%.1f", value)
                }
        }

        private fun createItemComponent(
                material: Material,
                name: String,
                loreComponents: List<Component>,
                tag: String
        ): ItemStack {
                return GuiItemFactory.item(material, name, loreComponents, tag)
        }

        fun openTagEditor(player: Player, worldData: WorldData) {
                val lang = plugin.languageManager
                val title =
                        lang.getMessage(
                                player,
                                "gui.tag_editor.title",
                                mapOf("world" to worldData.name)
                        )
                val currentTitle =
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                .plainText()
                                .serialize(player.openInventory.title())

                me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(
                        plugin,
                        player,
                        "world_settings",
                        me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(title)
                )
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.MANAGE_TAGS,
                        isGui = true
                )
                scheduleGuiTransitionReset(plugin, player)

                val inventory =
                        if (player.openInventory.topInventory.size == 45 && currentTitle == title) {
                                player.openInventory.topInventory
                        } else {
                                val holder = WorldSettingsGuiHolder()
                                val inventory =
                                        Bukkit.createInventory(holder, 45, me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(title))
                                holder.inv = inventory
                                inventory
                        }

                me.awabi2048.myworldmanager.util.GuiHelper.applyConfirmationFrame(inventory)

                // 背景
                val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
                for (i in 0..8) inventory.setItem(i, blackPane)
                for (i in 18..26) inventory.setItem(i, blackPane)

                // タグの配置
                val tags = plugin.worldTagManager.getEditableTagIds(worldData.tags)
                val maxVisibleTags = 7
                val visibleTags = tags.take(maxVisibleTags)
                visibleTags.forEachIndexed { index, tagId ->
                        val slot = 10 + index
                        val hasTag = worldData.tags.contains(tagId)

                        val activeColor = lang.getMessage(player, "publish_level.color.active")
                        val inactiveColor = lang.getMessage(player, "publish_level.color.inactive")

                        val item = ItemStack(if (hasTag) Material.ENCHANTED_BOOK else Material.BOOK)
                        val meta = item.itemMeta ?: return@forEachIndexed
                        meta.displayName(
                                LegacyComponentSerializer.legacySection()
                                        .deserialize(
                                                (if (hasTag) activeColor else inactiveColor) +
                                                        plugin.worldTagManager.getDisplayName(player, tagId)
                                        )
                                        .decoration(TextDecoration.ITALIC, false)
                        )

                        val lore = mutableListOf<String>()
                        val statusText =
                                if (hasTag) lang.getMessage(player, "gui.tag_editor.status_active")
                                else lang.getMessage(player, "gui.tag_editor.status_inactive")
                        val statusColor = if (hasTag) activeColor else inactiveColor
                        lore.add(
                                lang.getMessage(
                                        player,
                                        "gui.common.status_display",
                                        mapOf("color" to statusColor, "status" to statusText)
                                )
                        )
                        lore.add("")
                        lore.add(lang.getMessage(player, "gui.tag_editor.click_toggle"))

                        meta.lore(
                                lore.map {
                                        LegacyComponentSerializer.legacySection()
                                                .deserialize(it)
                                                .decoration(TextDecoration.ITALIC, false)
                                }
                        )
                        item.itemMeta = meta
                        ItemTag.tagItem(item, "tag_$tagId")
                        inventory.setItem(slot, item)
                }

                if (tags.size > maxVisibleTags) {
                        inventory.setItem(
                                17,
                                createItem(
                                        Material.PAPER,
                                        "§e...",
                                        listOf("§7タグが多いため、ダイアログ編集を利用してください"),
                                        ItemTag.TYPE_GUI_INFO
                                )
                        )
                }

                // 戻るボタン
                inventory.setItem(
                        22,
                        createItem(
                                Material.ARROW,
                                lang.getMessage(player, "gui.common.back"),
                                emptyList<String>(),
                                ItemTag.TYPE_GUI_CANCEL
                        )
                )

                player.openInventory(inventory)
        }

        fun openCriticalSettings(player: Player, worldData: WorldData) {
                val lang = plugin.languageManager
                val title = lang.getMessage(player, "gui.critical.title")
                val currentTitle =
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                .plainText()
                                .serialize(player.openInventory.title())

                me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(
                        plugin,
                        player,
                        "world_settings",
                        me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(title)
                )
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.CRITICAL_SETTINGS,
                        isGui = true
                )
                scheduleGuiTransitionReset(plugin, player)

                val inventory =
                        if (player.openInventory.topInventory.size == 45 && currentTitle == title) {
                                player.openInventory.topInventory
                        } else {
                                val holder = WorldSettingsGuiHolder()
                                val inventory =
                                        Bukkit.createInventory(holder, 45, me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(title))
                                holder.inv = inventory
                                inventory
                        }

                val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
                for (i in 0..8) inventory.setItem(i, blackPane)
                for (i in 36..44) inventory.setItem(i, blackPane)

                val middleGrayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
                for (i in 9..35) inventory.setItem(i, middleGrayPane)

                // 払い戻し額の計算
                val refundRate = plugin.config.getDouble("critical_settings.refund_percentage", 0.5)
                val refund = (worldData.cumulativePoints * refundRate).toInt()
                val percent = (refundRate * 100).toInt()

                // プレイヤーごとのクールタイムチェック
                val cooldownHours = plugin.config.getLong("critical_settings.archive_cooldown_hours", 24L)
                val playerStats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                val lastActionAt = playerStats.lastArchiveActionAt
                val dtFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                val isOnCooldown = if (lastActionAt != null) {
                    try {
                        val lastAction = java.time.LocalDateTime.parse(lastActionAt, dtFormatter)
                        val elapsed = java.time.Duration.between(lastAction, java.time.LocalDateTime.now()).toHours()
                        elapsed < cooldownHours
                    } catch (e: Exception) { false }
                } else false
                val hoursRemaining = if (isOnCooldown && lastActionAt != null) {
                    try {
                        val lastAction = java.time.LocalDateTime.parse(lastActionAt, dtFormatter)
                        val elapsed = java.time.Duration.between(lastAction, java.time.LocalDateTime.now()).toHours()
                        (cooldownHours - elapsed).coerceAtLeast(0L)
                    } catch (e: Exception) { 0L }
                } else 0L

                val archiveLoreKey = if (isOnCooldown) "gui.critical.archive_world.lore_cooldown" else "gui.critical.archive_world.lore"
                val archiveLore = lang.getComponentList(
                    player,
                    archiveLoreKey,
                    mapOf(
                        "cooldown_hours" to cooldownHours,
                        "hours_remaining" to hoursRemaining
                    )
                )

                // 動的スロット配置: ボーダー拡張の有無に応じて決定
                // - 拡張あり (level > 0): スロット20=リセット, 22=アーカイブ, 24=削除
                // - 拡張なし (level == 0): スロット20=削除, 24=アーカイブ
                val isExpansionEnabled = worldData.borderExpansionLevel > 0
                val hasSpecialExpansion = worldData.borderExpansionLevel == WorldData.EXPANSION_LEVEL_SPECIAL

                if (isExpansionEnabled || hasSpecialExpansion) {
                    // 拡張リセットボタン (スロット20)
                    val currentLevel = worldData.borderExpansionLevel
                    val resetLore: List<Component>

                    if (currentLevel > 0) {
                        val resetRefund = (calculateTotalExpansionCost(currentLevel) * refundRate).toInt()
                        resetLore = lang.getComponentList(
                            player,
                            "gui.critical.reset_expansion.lore",
                            mapOf("level" to currentLevel, "points" to resetRefund)
                        )
                    } else {
                        resetLore = lang.getComponentList(player, "gui.critical.reset_expansion.lore_unavailable")
                    }

                    inventory.setItem(
                        20,
                        createItemComponent(
                            Material.BARRIER,
                            lang.getMessage(player, "gui.critical.reset_expansion.display"),
                            resetLore,
                            ItemTag.TYPE_GUI_SETTING_RESET_EXPANSION
                        )
                    )

                    // アーカイブボタン (スロット22)
                    inventory.setItem(
                        22,
                        createItemComponent(
                            plugin.menuConfigManager.getIconMaterial("world_settings", "critical", Material.CHEST),
                            lang.getMessage(player, "gui.critical.archive_world.display"),
                            archiveLore,
                            if (isOnCooldown) ItemTag.TYPE_GUI_INFO else ItemTag.TYPE_GUI_SETTING_ARCHIVE
                        )
                    )
                } else {
                    // 拡張なし: アーカイブは slot 24
                    inventory.setItem(
                        24,
                        createItemComponent(
                            plugin.menuConfigManager.getIconMaterial("world_settings", "critical", Material.CHEST),
                            lang.getMessage(player, "gui.critical.archive_world.display"),
                            archiveLore,
                            if (isOnCooldown) ItemTag.TYPE_GUI_INFO else ItemTag.TYPE_GUI_SETTING_ARCHIVE
                        )
                    )
                }

                // 削除ボタン
                val ownerStats = plugin.playerStatsRepository.findByUuid(worldData.owner)
                val canDeleteWorld = ownerStats.unlockedWorldSlot > 0
                val deleteLore = lang.getComponentList(
                    player,
                    if (canDeleteWorld) "gui.critical.delete_world.lore" else "gui.critical.delete_world.lore_unavailable_slot",
                    mapOf(
                        "points" to refund,
                        "percent" to percent,
                        "slots" to ownerStats.unlockedWorldSlot
                    )
                )
                val deleteDisplayName = if (canDeleteWorld) {
                        lang.getMessage(player, "gui.critical.delete_world.display")
                } else {
                        "§8§m${lang.getMessage(player, "gui.critical.delete_world.display")}"
                }

                val deleteSlot = if (isExpansionEnabled || hasSpecialExpansion) 24 else 20
                inventory.setItem(
                        deleteSlot,
                        createItemComponent(
                                Material.LAVA_BUCKET,
                                deleteDisplayName,
                                deleteLore,
                                if (canDeleteWorld) ItemTag.TYPE_GUI_SETTING_DELETE_WORLD else ItemTag.TYPE_GUI_INFO
                        )
                )

                // 戻るボタン (スロット36から40へ移動)
                inventory.setItem(
                        40,
                        createItem(
                                Material.ARROW,
                                lang.getMessage(player, "gui.common.back"),
                                emptyList<String>(),
                                ItemTag.TYPE_GUI_CANCEL
                        )
                )

                if (player.openInventory.topInventory != inventory) {
                        player.openInventory(inventory)
                }
        }

        private fun calculateTotalExpansionCost(level: Int): Int {
                var total = 0
                val config = plugin.config
                val baseCost = config.getInt("expansion.base_cost", 100)
                val multiplier = config.getDouble("expansion.cost_multiplier", 2.0)

                for (i in 1..level) {
                        total +=
                                if (config.contains("expansion.costs.$i")) {
                                        config.getInt("expansion.costs.$i")
                                } else {
                                        (baseCost * Math.pow(multiplier, (i - 1).toDouble()))
                                                .toInt()
                                }
                }
                return total
        }

        fun openResetExpansionConfirmation(player: Player, worldData: WorldData) {
                val lang = plugin.languageManager
                val title = lang.getMessage(player, "gui.confirm.reset_expansion.title")
                me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(
                        plugin,
                        player,
                        "world_settings",
                        me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(title)
                )
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.RESET_EXPANSION_CONFIRM,
                        isGui = true
                )
                scheduleGuiTransitionReset(plugin, player)
                val currentTitle =
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                .plainText()
                                .serialize(player.openInventory.title())
                val inventory =
                        if (player.openInventory.topInventory.size == 45 && currentTitle == title) {
                                player.openInventory.topInventory
                        } else {
                                val holder = WorldSettingsGuiHolder()
                                val inventory =
                                        Bukkit.createInventory(holder, 45, me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(title))
                                holder.inv = inventory
                                inventory
                        }

                me.awabi2048.myworldmanager.util.GuiHelper.applyConfirmationFrame(inventory)

                val lore = lang.getComponentList(player, "gui.confirm.reset_expansion.lore")
                val infoItem =
                        createItemComponent(
                                Material.PAPER,
                                LegacyComponentSerializer.legacySection().serialize(lore[0]),
                                lore.drop(1),
                                ItemTag.TYPE_GUI_INFO
                        )
                ItemTag.setWorldUuid(infoItem, worldData.uuid)
                inventory.setItem(22, infoItem)

                inventory.setItem(
                        20,
                        createItem(
                                Material.LIME_WOOL,
                                lang.getMessage(player, "gui.common.cancel"),
                                listOf(lang.getMessage(player, "gui.common.back")),
                                ItemTag.TYPE_GUI_CANCEL
                        )
                )
                inventory.setItem(
                        24,
                        createItem(
                                Material.RED_WOOL,
                                lang.getMessage(player, "gui.common.confirm"),
                                emptyList<String>(),
                                ItemTag.TYPE_GUI_CONFIRM
                        )
                )

                if (player.openInventory.topInventory != inventory) {
                        player.openInventory(inventory)
                }
        }

        fun openDeleteWorldConfirmation1(player: Player, worldData: WorldData) {
                val lang = plugin.languageManager
                val title = lang.getMessage(player, "gui.confirm.delete_1.title")
                me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(
                        plugin,
                        player,
                        "world_settings",
                        me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(title)
                )
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.DELETE_WORLD_CONFIRM,
                        isGui = true
                )
                scheduleGuiTransitionReset(plugin, player)
                val currentTitle =
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                .plainText()
                                .serialize(player.openInventory.title())
                val inventory =
                        if (player.openInventory.topInventory.size == 45 && currentTitle == title) {
                                player.openInventory.topInventory
                        } else {
                                val holder = WorldSettingsGuiHolder()
                                val inventory =
                                        Bukkit.createInventory(holder, 45, me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(title))
                                holder.inv = inventory
                                inventory
                        }

                me.awabi2048.myworldmanager.util.GuiHelper.applyConfirmationFrame(inventory)

                val lore = lang.getComponentList(player, "gui.confirm.delete_1.lore")
                val infoItem =
                        createItemComponent(
                                Material.PAPER,
                                LegacyComponentSerializer.legacySection().serialize(lore[0]),
                                lore.drop(1),
                                ItemTag.TYPE_GUI_INFO
                        )
                ItemTag.setWorldUuid(infoItem, worldData.uuid)
                inventory.setItem(22, infoItem)

                inventory.setItem(
                        20,
                        createItem(
                                Material.LIME_WOOL,
                                lang.getMessage(player, "gui.common.cancel"),
                                emptyList<String>(),
                                ItemTag.TYPE_GUI_CANCEL
                        )
                )
                inventory.setItem(
                        24,
                        createItem(
                                Material.RED_WOOL,
                                lang.getMessage(player, "gui.confirm.delete_1.next"),
                                emptyList<String>(),
                                ItemTag.TYPE_GUI_SETTING_DELETE_WORLD
                        )
                )

                if (player.openInventory.topInventory != inventory) {
                        player.openInventory(inventory)
                }
        }

        fun openDeleteWorldConfirmation2(player: Player, worldData: WorldData) {
                val lang = plugin.languageManager
                val title = lang.getMessage(player, "gui.confirm.delete_2.title")
                me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(
                        plugin,
                        player,
                        "world_settings",
                        me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(title)
                )
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.DELETE_WORLD_CONFIRM_FINAL,
                        isGui = true
                )
                scheduleGuiTransitionReset(plugin, player)
                val currentTitle =
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                .plainText()
                                .serialize(player.openInventory.title())
                val inventory =
                        if (player.openInventory.topInventory.size == 45 && currentTitle == title) {
                                player.openInventory.topInventory
                        } else {
                                val holder = WorldSettingsGuiHolder()
                                val inventory =
                                        Bukkit.createInventory(holder, 45, me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(title))
                                holder.inv = inventory
                                inventory
                        }

                me.awabi2048.myworldmanager.util.GuiHelper.applyConfirmationFrame(inventory)

                val lore = lang.getComponentList(player, "gui.confirm.delete_2.lore")
                val infoItem =
                        createItemComponent(
                                Material.LAVA_BUCKET,
                                LegacyComponentSerializer.legacySection().serialize(lore[0]),
                                lore.drop(1),
                                ItemTag.TYPE_GUI_INFO
                        )
                ItemTag.setWorldUuid(infoItem, worldData.uuid)
                inventory.setItem(22, infoItem)

                inventory.setItem(
                        20,
                        createItem(
                                Material.LIME_WOOL,
                                lang.getMessage(player, "gui.common.cancel"),
                                emptyList<String>(),
                                ItemTag.TYPE_GUI_CANCEL
                        )
                )
                inventory.setItem(
                        24,
                        createItem(
                                Material.RED_WOOL,
                                lang.getMessage(player, "gui.confirm.delete_2.confirm_btn"),
                                emptyList<String>(),
                                ItemTag.TYPE_GUI_CONFIRM
                        )
                )

                if (player.openInventory.topInventory != inventory) {
                        player.openInventory(inventory)
                }
        }

        fun openPortalManagement(player: Player, worldData: WorldData, page: Int = 0) {
                val lang = plugin.languageManager
                val title = lang.getMessage(player, "gui.settings.portals.display")
                val currentTitle =
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                .plainText()
                                .serialize(player.openInventory.title())

                me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(
                        plugin,
                        player,
                        "portal_manage",
                        me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(title)
                )
                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldData.uuid,
                        SettingsAction.MANAGE_PORTALS,
                        isGui = true
                )
                scheduleGuiTransitionReset(plugin, player)

                val worldName = "my_world.${worldData.uuid}"
                val allPortals =
                        plugin.portalRepository.findAll().filter { it.worldName == worldName }

                val itemsPerPage = 21
                val startIndex = page * itemsPerPage
                val currentPagePortals = allPortals.drop(startIndex).take(itemsPerPage)

                val inventory =
                        if (player.openInventory.topInventory.size == 45 && currentTitle == title) {
                                player.openInventory.topInventory
                        } else {
                                val holder = WorldSettingsGuiHolder()
                                val inventory =
                                        Bukkit.createInventory(holder, 45, me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(title))
                                holder.inv = inventory
                                inventory
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
                        inventory.setItem(
                                40,
                                createItem(
                                        Material.ARROW,
                                        lang.getMessage(player, "gui.common.prev_page"),
                                        listOf("PAGE_TARGET: ${page - 1}"),
                                        ItemTag.TYPE_GUI_NAV_PREV
                                )
                        )
                }
                if (startIndex + itemsPerPage < allPortals.size) {
                        inventory.setItem(
                                44,
                                createItem(
                                        Material.ARROW,
                                        lang.getMessage(player, "gui.common.next_page"),
                                        listOf("PAGE_TARGET: ${page + 1}"),
                                        ItemTag.TYPE_GUI_NAV_NEXT
                                )
                        )
                }

                // 戻るボタン
                inventory.setItem(
                        36,
                        createItem(
                                Material.REDSTONE,
                                lang.getMessage(player, "gui.common.back"),
                                emptyList<String>(),
                                ItemTag.TYPE_GUI_CANCEL
                        )
                )

                if (player.openInventory.topInventory != inventory) {
                        player.openInventory(inventory)
                }
        }

        private fun createPortalManagementItem(player: Player, portal: PortalData): ItemStack {
                val lang = plugin.languageManager
                val item = ItemStack(Material.END_PORTAL_FRAME)
                val meta = item.itemMeta ?: return item

                val destName =
                        if (portal.worldUuid != null) {
                                val worldData =
                                        plugin.worldConfigRepository.findByUuid(portal.worldUuid!!)
                                worldData?.name ?: "Unknown World"
                        } else {
                                val configName =
                                        plugin.config.getString(
                                                "portal_targets.${portal.targetWorldName}"
                                        )
                                configName ?: portal.targetWorldName ?: "Unknown"
                        }

                val displayTitle =
                        lang.getMessage(player, "gui.admin_portals.portal_item.name")
                                .replace("{0}", destName)
                meta.displayName(
                        LegacyComponentSerializer.legacySection()
                                .deserialize(displayTitle)
                                .decoration(TextDecoration.ITALIC, false)
                )

                val lore = lang.getComponentList(
                        player,
                        if (plugin.playerPlatformResolver.isBedrock(player)) {
                                "gui.admin_portals.portal_item.lore_management_bedrock"
                        } else {
                                "gui.admin_portals.portal_item.lore_management"
                        },
                        mapOf("x" to portal.x, "y" to portal.y, "z" to portal.z)
                )

                meta.lore(lore)
                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_PORTAL)
                ItemTag.setPortalUuid(item, portal.id)

                return item
        }

        private fun createDecorationItem(material: Material): ItemStack {
                return GuiItemFactory.decoration(material)
        }
}
