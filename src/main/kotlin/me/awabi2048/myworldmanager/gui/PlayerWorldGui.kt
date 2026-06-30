package me.awabi2048.myworldmanager.gui

import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.api.extension.PlayerWorldMenuRequest
import me.awabi2048.myworldmanager.model.PlayerStats
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.util.GuiItemFactory
import me.awabi2048.myworldmanager.util.ItemTag
import me.awabi2048.myworldmanager.util.PermissionManager
import me.awabi2048.myworldmanager.util.WorldRuntimePolicies
import me.awabi2048.myworldmanager.util.StructuredLore
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiNameStyle

class PlayerWorldGui(private val plugin: MyWorldManager) {

        private val repository = plugin.worldConfigRepository

        private val worldsPerRow = 7
        private val dataRowsPerPage = 4
        private val itemsPerPageNum = dataRowsPerPage * worldsPerRow // 28 items

        fun getPlayerWorlds(player: Player): List<WorldData> {
                return getPlayerWorlds(player.uniqueId)
        }

        fun getPlayerWorlds(targetPlayerUuid: UUID): List<WorldData> {
                val stats = plugin.playerStatsRepository.findByUuid(targetPlayerUuid)
                val allWorlds = repository.findAll()

                // プレイヤーがアクセス可能なワールドをフィルタリング
                val accessibleWorlds = allWorlds
                        .filter {
                                it.owner == targetPlayerUuid ||
                                        it.moderators.contains(targetPlayerUuid) ||
                                        it.members.contains(targetPlayerUuid) ||
                                        it.isArchived // アーカイブ済みも自分のなら表示
                        }
                        .filter {
                                it.owner == targetPlayerUuid || !it.isArchived
                        } // メンバーとして参加しているアーカイブ済みは非表示

                // worldDisplayOrder に含まれるワールド（順序リスト順）
                val orderedWorlds = stats.worldDisplayOrder
                        .mapNotNull { uuid -> accessibleWorlds.find { it.uuid == uuid } }

                // worldDisplayOrder に含まれていないワールド（作成日時降順）
                val unorderedWorlds = accessibleWorlds
                        .filter { !stats.worldDisplayOrder.contains(it.uuid) }
                        .sortedWith(compareBy<WorldData> { it.isArchived }.thenByDescending { it.createdAt })

                // 完全な順序リスト（orderedWorlds + unorderedWorlds）
                return orderedWorlds + unorderedWorlds
        }

        fun open(
                player: Player,
                page: Int = 0,
                showBackButton: Boolean? = null,
                targetPlayerUuid: UUID = player.uniqueId,
                targetPlayerName: String? = player.name
        ) {
                val session = plugin.playerWorldSessionManager.getSession(player.uniqueId)
                if (showBackButton != null) {
                        session.showBackButton = showBackButton
                }

                // worldDisplayOrder のバリデーション
                val isOwnMenu = targetPlayerUuid == player.uniqueId
                val stats = plugin.playerStatsRepository.findByUuid(targetPlayerUuid)
                val beforeCount = stats.worldDisplayOrder.size
                stats.worldDisplayOrder.removeAll { uuid ->
                        plugin.worldConfigRepository.findByUuid(uuid) == null
                }
                val afterCount = stats.worldDisplayOrder.size

                // 変更があれば保存＆ログ出力
                if (beforeCount != afterCount) {
                        plugin.playerStatsRepository.save(stats)
                        plugin.logger.info("[PlayerWorldGui] ${player.name} の worldDisplayOrder から削除されたワールド ${beforeCount - afterCount} 件を削除しました。")
                }

                repository.loadAll()
                val playerWorlds = getPlayerWorlds(targetPlayerUuid)

                // worldDisplayOrder に含まれていないワールドを自動追加
                val currentUuids = playerWorlds.map { it.uuid }
                val missingUuids = currentUuids.filter { !stats.worldDisplayOrder.contains(it) }
                if (missingUuids.isNotEmpty()) {
                        stats.worldDisplayOrder.addAll(missingUuids)
                        plugin.playerStatsRepository.save(stats)
                        plugin.logger.info("[PlayerWorldGui] ${player.name} の worldDisplayOrder に新規ワールド ${missingUuids.size} 件を追加しました。")
                }

                // 現在のページ番号を保存
                session.currentPage = page

                if (
                        MyWorldManagerApi.openPlayerWorldMenuOverride(
                                player,
                                PlayerWorldMenuRequest(
                                        page = page,
                                        showBackButton = session.showBackButton,
                                        targetPlayerUuid = targetPlayerUuid,
                                        targetPlayerName = targetPlayerName
                                )
                        )
                ) {
                        return
                }

                val startIndex = page * itemsPerPageNum
                val currentPageWorlds = playerWorlds.drop(startIndex).take(itemsPerPageNum)

                val neededDataRows =
                        if (currentPageWorlds.isEmpty()) 1
                        else (currentPageWorlds.size + worldsPerRow - 1) / worldsPerRow
                val rowCount = (neededDataRows + 2).coerceIn(3, 6)

                val lang = plugin.languageManager
                val titleKey = "gui.player_world.title"
                if (!lang.hasKey(player, titleKey)) {
                        player.sendMessage(
                                "§c[MyWorldManager] Error: Missing translation key: $titleKey"
                        )
                        return
                }

                val titleStr = lang.getMessage(player, titleKey)
                val title = me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(titleStr)
                me.awabi2048.myworldmanager.util.GuiHelper.playMenuOpen(player, "player_world")

                val holder = PlayerWorldGuiHolder(targetPlayerUuid, targetPlayerName)
                val inventory = Bukkit.createInventory(holder, rowCount * 9, title)
                holder.inv = inventory

                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        player.uniqueId,
                        me.awabi2048.myworldmanager.session.SettingsAction.PLAYER_WORLD_GUI,
                        isGui = true
                )

                val greyPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)

                GuiItemFactory.applyStandardFrame(inventory, emptyMaterial = null)

                for (i in 0 until neededDataRows) {
                        val rowStart = (i + 1) * 9
                        inventory.setItem(rowStart, greyPane)
                        inventory.setItem(rowStart + 8, greyPane)

                        for (j in 0 until 7) {
                                val worldIndexInPage = i * 7 + j
                                if (worldIndexInPage < currentPageWorlds.size) {
                                        inventory.setItem(
                                                rowStart + 1 + j,
                                                createWorldItem(
                                                        player,
                                                        currentPageWorlds[worldIndexInPage],
                                                        targetPlayerUuid
                                                )
                                        )
                                } else {
                                        inventory.setItem(rowStart + 1 + j, greyPane)
                                }
                        }
                }

                val footerStart = (rowCount - 1) * 9
                // 統計情報の取得
                val currentCreateCount = playerWorlds.count { it.owner == targetPlayerUuid }
                val maxSlot =
                        WorldRuntimePolicies.maxCreateCountDefault(plugin.config) +
                                stats.unlockedWorldSlot
                val bypassLimits = PermissionManager.canBypassWorldLimits(player)
                val pendingCount = plugin.pendingDecisionManager.getPersistentPendingCount(targetPlayerUuid)
                val latestPendingAt = plugin.pendingDecisionManager.getLatestPersistentCreatedAt(targetPlayerUuid)
                val latestPendingText =
                        latestPendingAt?.let {
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                        .withZone(ZoneId.systemDefault())
                                        .format(Instant.ofEpochMilli(it))
                        } ?: lang.getMessage(player, "general.unknown")

                // マイワールド新規作成ボタン (Slot 2)
                val creationBlockReason = creationBlockReason(player, currentCreateCount, maxSlot, bypassLimits)
                if (isOwnMenu && creationBlockReason == null) {
                        inventory.setItem(footerStart + 2, createCreationButton(player))
                } else if (isOwnMenu) {
                        creationBlockReason?.let {
                                inventory.setItem(footerStart + 2, createCreationUnavailableButton(player, it))
                        }
                }

                // プレイヤー統計ボタン (Slot 4)
                inventory.setItem(
                        footerStart + 4,
                        createStatsButton(player, targetPlayerUuid, targetPlayerName, currentCreateCount, maxSlot, stats)
                )

                // 個人設定ボタン (Slot 6)
                if (isOwnMenu) {
                        inventory.setItem(
                                footerStart + 6,
                                createUserSettingsButton(player)
                        )
                }

                if (page > 0) {
                        inventory.setItem(
                                footerStart + 1,
                                me.awabi2048.myworldmanager.util.GuiHelper.createPrevPageItem(
                                        plugin,
                                        player,
                                        "player_world",
                                        page - 1
                                )
                        )
                }

                if (session.showBackButton) {
                        inventory.setItem(
                                footerStart,
                                me.awabi2048.myworldmanager.util.GuiHelper.createReturnItem(
                                        plugin,
                                        player,
                                        "player_world"
                                )
                        )
                }
                if (startIndex + itemsPerPageNum < playerWorlds.size) {
                        inventory.setItem(
                                footerStart + 8,
                                me.awabi2048.myworldmanager.util.GuiHelper.createNextPageItem(
                                        plugin,
                                        player,
                                        "player_world",
                                        page + 1
                                )
                        )
                }

                player.openInventory(inventory)
                me.awabi2048.myworldmanager.util.GuiHelper.scheduleGuiTransitionReset(plugin, player)
        }

        private fun createWorldItem(
                player: Player,
                world: WorldData,
                @Suppress("UNUSED_PARAMETER") playerUuid: UUID
        ): ItemStack {
                val item = ItemStack(world.icon)
                val meta = item.itemMeta ?: return item
                val lang = plugin.languageManager

                meta.displayName(
                        lang.getComponent(
                                player,
                                "gui.common.world_item_name",
                                mapOf("world" to world.name)
                        )
                )

                val formattedDesc = if (world.description.isNotEmpty()) {
                        lang.getMessage(player, "gui.common.world_desc", mapOf("description" to world.description))
                } else ""

                val ownerName = PlayerNameUtil.getNameOrDefault(world.owner, lang.getMessage(player, "general.unknown"))

                val ownerLine = lang.getMessage(player, "gui.player_world.world_item.owner", mapOf("owner" to ownerName))

                val publishLevelColor = lang.getMessage(player, "publish_level.color.${world.publishLevel.name.lowercase()}")
                val publishLevelName = lang.getMessage(player, "publish_level.${world.publishLevel.name.lowercase()}")
                val publishLine = lang.getMessage(player, "gui.player_world.world_item.publish", mapOf("level" to publishLevelName, "status_color" to publishLevelColor))

                val favorites = world.favorite
                val favoriteLine = lang.getMessage(player, "gui.player_world.world_item.favorite", mapOf("count" to favorites))

                val visitors = world.recentVisitors.sum()
                val visitorLine = lang.getMessage(player, "gui.player_world.world_item.recent_visitors", mapOf("count" to visitors))

                val tagLine = if (world.tags.isNotEmpty()) {
                        val tagNames = world.tags.joinToString(", ") {
                                plugin.worldTagManager.getDisplayName(player, it)
                        }
                        lang.getMessage(player, "gui.player_world.world_item.tag", mapOf("tags" to tagNames))
                } else ""

                val now = LocalDate.now()
                val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                val displayFormatter = dateFormatterFor(player)
                val expireDate = try {
                        LocalDate.parse(world.expireDate, inputFormatter)
                } catch (e: Exception) {
                        LocalDate.now().plusYears(1)
                }
                val daysRemaining = ChronoUnit.DAYS.between(now, expireDate)

                val expiresAtLine = if (expireDate.year < 2900) {
                        if (daysRemaining < 0) meta.setEnchantmentGlintOverride(true)
                        lang.getMessage(player, "gui.player_world.world_item.expires_at", mapOf("days" to daysRemaining, "date" to displayFormatter.format(expireDate)))
                } else ""

                val expiredLine = if (world.isArchived) {
                    meta.setEnchantmentGlintOverride(true)
                    lang.getMessage(player, "gui.player_world.world_item.expired")
                } else ""

                val warpAction = if (isCurrentWorld(player, world)) {
                        ""
                } else {
                        lang.getMessage(player, "gui.player_world.world_item.warp")
                }
                val settingsAction =
                        if (plugin.playerPlatformResolver.isBedrock(player)) {
                                lang.getMessage(player, "gui.player_world.world_item.settings_bedrock")
                        } else {
                                lang.getMessage(player, "gui.player_world.world_item.settings")
                        }

                // MWM owns the semantic block order; CC-System owns all surrounding separators.
                meta.lore(CCSystem.getAPI().getLoreService().render(StructuredLore.blocks(
                        *buildList {
                                if (formattedDesc.isNotBlank()) add(listOf(formattedDesc))
                                add(listOf(ownerLine, publishLine, favoriteLine, visitorLine) + listOfNotNull(tagLine.takeIf(String::isNotBlank)))
                                val lifecycle = listOf(expiresAtLine, expiredLine).filter(String::isNotBlank)
                                if (lifecycle.isNotEmpty()) add(lifecycle)
                                add(listOf(warpAction, settingsAction).filter(String::isNotBlank))
                        }.toTypedArray()
                )))

                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_WORLD_ITEM)
                ItemTag.setWorldUuid(item, world.uuid)
                return item
        }

        private fun isCurrentWorld(player: Player, world: WorldData): Boolean {
                return plugin.worldConfigRepository.findByWorldName(player.world.name)?.uuid == world.uuid
        }

        private fun createUserSettingsButton(player: Player): ItemStack {
                val lang = plugin.languageManager
                val item = CCSystem.getAPI().getGuiElementService().item(
                        GuiItemSpec(
                                material = Material.WRITABLE_BOOK,
                                name = GuiNameSpec.Text(lang.getMessage(player, "gui.user_settings.button.display"), GuiNameStyle.PRIMARY),
                                lore = GuiLoreSpec.Simple(
                                        listOf(lang.getMessage(player, "gui.user_settings.button.action"))
                                ),
                                role = GuiElementRole.ACTION,
                                amount = 1
                        )
                )
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_USER_SETTINGS_BUTTON)
                return item
        }

        private fun createCreationButton(player: Player): ItemStack {
                val lang = plugin.languageManager
                val item = ItemStack(Material.NETHER_STAR)
                val meta = item.itemMeta ?: return item
                meta.displayName(
                        lang.getComponent(player, "gui.player_world.creation_button.display")
                )
                meta.lore(GuiItemFactory.menuLore(lang.getMessageList(player, "gui.player_world.creation_button.lore")))
                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_CREATION_BUTTON)
                return item
        }

        private fun createCreationUnavailableButton(player: Player, reason: CreationBlockReason): ItemStack {
                val lang = plugin.languageManager
                val item = ItemStack(Material.BARRIER)
                val meta = item.itemMeta ?: return item
                meta.displayName(lang.getComponent(player, reason.displayKey))
                meta.lore(GuiItemFactory.menuLore(lang.getMessageList(player, reason.loreKey)))
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES)
                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_INFO)
                return item
        }

        private fun creationBlockReason(
                player: Player,
                currentCreateCount: Int,
                maxSlot: Int,
                bypassLimits: Boolean
        ): CreationBlockReason? {
                // 作成権限は最優先。bypassLimits や運用フラグよりも先に判定する。
                if (!PermissionManager.checkPermission(player, PermissionManager.CREATE)) {
                        return CreationBlockReason.NO_PERMISSION
                }
                if (bypassLimits) return null
                // 作成期間の停止は枠不足より優先して、運営側の意図をそのまま表示する。
                if (!MyWorldManagerApi.isWorldCreationEnabled()) return CreationBlockReason.PERIOD_DISABLED
                if (currentCreateCount >= maxSlot) return CreationBlockReason.NO_SLOT
                return null
        }

        private fun createStatsButton(
                player: Player,
                targetPlayerUuid: UUID,
                targetPlayerName: String?,
                currentCreateCount: Int,
                maxSlot: Int,
                stats: PlayerStats
        ): ItemStack {
                val lang = plugin.languageManager
                val item = ItemStack(Material.PLAYER_HEAD)
                val meta = item.itemMeta as? org.bukkit.inventory.meta.SkullMeta ?: return item
                meta.owningPlayer = Bukkit.getOfflinePlayer(targetPlayerUuid)
                val bypassLimits = PermissionManager.canBypassWorldLimits(player)
                val pendingCount = plugin.pendingDecisionManager.getPersistentPendingCount(targetPlayerUuid)
                val latestPendingAt = plugin.pendingDecisionManager.getLatestPersistentCreatedAt(targetPlayerUuid)
                val latestPendingText =
                        latestPendingAt?.let {
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                        .withZone(ZoneId.systemDefault())
                                        .format(Instant.ofEpochMilli(it))
                        } ?: lang.getMessage(player, "general.unknown")

                meta.displayName(
                        lang.getComponent(
                                player,
                                "gui.player_world.stats_button.display",
                                mapOf(
                                        "player" to (
                                                targetPlayerName
                                                        ?: PlayerNameUtil.getNameOrDefault(targetPlayerUuid, lang.getMessage(player, "general.unknown"))
                                                )
                                )

                        )
                )

                meta.lore(
                        GuiItemFactory.menuLore(lang.getMessageList(
                                player,
                                if (bypassLimits) "gui.player_world.stats_button.lore_bypass" else "gui.player_world.stats_button.lore",
                                mapOf(
                                        "point" to stats.worldPoint,
                                        "current_occupied" to currentCreateCount,
                                        "unlocked" to maxSlot,
                                        "icon" to if (plugin.playerPlatformResolver.isBedrock(player)) "" else "🛖",
                                        "pending_count" to pendingCount,
                                        "latest_pending_at" to latestPendingText
                                )
                        ))
                )

                if (pendingCount == 0) {
                        val lore = meta.lore()
                        if (!lore.isNullOrEmpty() && lore.size >= 5) {
                                val pendingSectionStart = lore.size - 5
                                val pendingSectionEnd = lore.size - 1
                                meta.lore(
                                        lore.filterIndexed { index, _ ->
                                                index !in pendingSectionStart until pendingSectionEnd
                                        }
                                )
                        }
                }

                if (pendingCount > 0) {
                        meta.setEnchantmentGlintOverride(true)
                }

                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_PLAYER_STATS)
                return item
        }

        private fun createDecorationItem(material: Material): ItemStack {
                return GuiItemFactory.decoration(material)
        }

        private fun dateFormatterFor(player: Player): DateTimeFormatter {
                val language = plugin.languageManager.resolveLocale(player).lowercase(Locale.ROOT)
                return if (language == "ja_jp") {
                        DateTimeFormatter.ofPattern("yyyy年MM月dd日")
                } else {
                        DateTimeFormatter.ofPattern("yyyy-MM-dd")
                }
        }

        class PlayerWorldGuiHolder(
                val targetPlayerUuid: UUID,
                val targetPlayerName: String?
        ) : org.bukkit.inventory.InventoryHolder {
                lateinit var inv: org.bukkit.inventory.Inventory
                override fun getInventory(): org.bukkit.inventory.Inventory = inv
        }

        private enum class CreationBlockReason(val displayKey: String, val loreKey: String) {
                PERIOD_DISABLED(
                        "gui.player_world.creation_unavailable.period_disabled.display",
                        "gui.player_world.creation_unavailable.period_disabled.lore"
                ),
                NO_SLOT(
                        "gui.player_world.creation_unavailable.no_slot.display",
                        "gui.player_world.creation_unavailable.no_slot.lore"
                ),
                NO_PERMISSION(
                        "gui.player_world.creation_unavailable.no_permission.display",
                        "gui.player_world.creation_unavailable.no_permission.lore"
                )
        }
}
