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
import me.awabi2048.myworldmanager.util.GuiLoreActions
import me.awabi2048.myworldmanager.util.GuiItemFactory
import me.awabi2048.myworldmanager.util.ItemTag
import me.awabi2048.myworldmanager.util.PermissionManager
import me.awabi2048.myworldmanager.util.WorldCreationChecks
import me.awabi2048.myworldmanager.util.WorldRuntimePolicies
import org.bukkit.Bukkit
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiNameStyle

class PlayerWorldGui(private val plugin: MyWorldManager) {

        private val repository = plugin.worldConfigRepository

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

                // 共通レイアウトにページ帯域とフッター位置を委譲し、画面ごとの行計算を残さない。
                val pageLayout = CCSystem.getAPI().getGuiLayoutService().sevenColumnPage(playerWorlds.size, page)
                val currentPage = pageLayout.page
                session.currentPage = currentPage
                val currentPageWorlds = playerWorlds.drop(pageLayout.startIndex).take(pageLayout.itemCount)
                val layout = pageLayout.layout

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
                val inventory = Bukkit.createInventory(holder, layout.size, title)
                holder.inv = inventory
                val footerStart = inventory.size - 9

                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        player.uniqueId,
                        me.awabi2048.myworldmanager.session.SettingsAction.PLAYER_WORLD_GUI,
                        isGui = true
                )

                GuiItemFactory.applyStandardFrame(inventory, emptyMaterial = null)

                layout.itemSlots.forEachIndexed { index, slot ->
                        currentPageWorlds.getOrNull(index)?.let {
                                inventory.setItem(slot, createWorldItem(player, it, targetPlayerUuid))
                        }
                }

                // 統計情報の取得
                val currentCreateCount = playerWorlds.count { it.owner == targetPlayerUuid }
                val maxSlot =
                        WorldRuntimePolicies.maxCreateCountDefault(plugin.config) +
                                stats.unlockedWorldSlot
                val bypassLimits = PermissionManager.canBypassWorldLimits(player)
                // マイワールド新規作成ボタン (Slot 2)
                val creationBlockReason = creationBlockReason(player, currentCreateCount, maxSlot, bypassLimits)
                if (isOwnMenu && creationBlockReason == null) {
                        inventory.setItem(layout.actionSlot - 2, createCreationButton(player))
                } else if (isOwnMenu) {
                        creationBlockReason?.let {
                                inventory.setItem(layout.actionSlot - 2, createCreationUnavailableButton(player, it))
                        }
                }

                // プレイヤー統計ボタン (Slot 4)
                inventory.setItem(
                        layout.actionSlot,
                        createStatsButton(player, targetPlayerUuid, targetPlayerName, currentCreateCount, maxSlot, stats)
                )

                // 個人設定ボタン (Slot 6)
                if (isOwnMenu) {
                        inventory.setItem(
                                layout.actionSlot + 2,
                                createUserSettingsButton(player)
                        )
                        inventory.setItem(
                                footerStart + 7,
                                createPendingButton(player)
                        )
                }

                if (currentPage > 0) {
                        inventory.setItem(
                                layout.previousPageSlot,
                                me.awabi2048.myworldmanager.util.GuiHelper.createPrevPageItem(
                                        plugin,
                                        player,
                                        "player_world",
                                currentPage - 1
                                )
                        )
                }

                if (session.showBackButton) {
                        inventory.setItem(
                                layout.backSlot,
                                me.awabi2048.myworldmanager.util.GuiHelper.createReturnItem(
                                        plugin,
                                        player,
                                        "player_world"
                                )
                        )
                }
                if (currentPage < pageLayout.totalPages - 1) {
                        inventory.setItem(
                                footerStart + 8,
                                me.awabi2048.myworldmanager.util.GuiHelper.createNextPageItem(
                                        plugin,
                                        player,
                                        "player_world",
                                currentPage + 1
                                )
                        )
                }

                GuiItemFactory.fillEmpty(inventory)
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

                val ownerName = PlayerNameUtil.getNameOrDefault(world.owner, lang.getMessage(player, "general.unknown"))

                val publishLevelColor = lang.getMessage(player, "publish_level.color.${world.publishLevel.name.lowercase()}")
                val publishLevelName = lang.getMessage(player, "publish_level.${world.publishLevel.name.lowercase()}")

                val favorites = world.favorite

                val visitors = world.recentVisitors.sum()

                val tagNames = if (world.tags.isNotEmpty()) {
                        world.tags.joinToString(", ") {
                                plugin.worldTagManager.getDisplayName(player, it)
                        }
                } else null

                val now = LocalDate.now()
                val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                val displayFormatter = dateFormatterFor(player)
                val expireDate = try {
                        LocalDate.parse(world.expireDate, inputFormatter)
                } catch (e: Exception) {
                        LocalDate.now().plusYears(1)
                }
                val daysRemaining = ChronoUnit.DAYS.between(now, expireDate)

                val expiresAtValue = if (expireDate.year < 2900) {
                        if (daysRemaining < 0) meta.setEnchantmentGlintOverride(true)
                        lang.getMessage(player, "gui.player_world.world_item.expires_value", mapOf("days" to daysRemaining, "date" to displayFormatter.format(expireDate)))
                } else null

                val isArchived = if (world.isArchived) {
                    meta.setEnchantmentGlintOverride(true)
                    true
                } else false

                val warpAction = lang.getMessage(player, "gui.player_world.world_item.warp")
                val settingsAction = lang.getMessage(player, "gui.player_world.world_item.settings")
                val isCurrentWorld = isCurrentWorld(player, world)
                // MWM owns semantic block order; CC-System draws the outer frame and ordinary boundary blank lines, while explicit separators draw middle rules.
                meta.lore(CCSystem.getAPI().getLoreService().render(GuiLoreSpec.Blocks(buildList {
                        if (world.description.isNotBlank()) add(GuiLoreBlock(listOf(GuiLoreLine.UserText(world.description))))
                        add(GuiLoreBlock(buildList {
                                add(GuiLoreLine.Data(lang.getMessage(player, "gui.common.world_item.owner"), ownerName, "§f"))
                                add(GuiLoreLine.Data(lang.getMessage(player, "gui.common.world_item.publish"), publishLevelName, publishLevelColor))
                                add(GuiLoreLine.Data(lang.getMessage(player, "gui.common.world_item.favorite"), favorites, "§c"))
                                add(GuiLoreLine.Data(
                                        lang.getMessage(player, "gui.common.world_item.recent_visitors"),
                                        lang.getMessage(player, "gui.common.world_item.recent_visitors_value", mapOf("count" to visitors)),
                                        "§a"
                                ))
                                if (tagNames != null) add(GuiLoreLine.Data(lang.getMessage(player, "gui.common.world_item.tags"), tagNames, "§e"))
                        }))
                        val lifecycle = buildList {
                                if (expiresAtValue != null) add(GuiLoreLine.Data(lang.getMessage(player, "gui.player_world.world_item.expires_at"), expiresAtValue, "§f"))
                                if (isArchived) add(GuiLoreLine.Warning(lang.getMessage(player, "gui.player_world.world_item.expired")))
                        }
                        if (lifecycle.isNotEmpty()) add(GuiLoreBlock(lifecycle))
                        add(GuiLoreBlock(buildList {
                                if (isCurrentWorld) {
                                        add(GuiLoreActions.singleClick(lang, player, settingsAction))
                                } else {
                                        add(GuiLoreLine.Action(lang.getMessage(player, "gui.settings.click.left"), warpAction))
                                        add(GuiLoreLine.Action(lang.getMessage(player, "gui.settings.click.right"), settingsAction))
                                }
                        }))
                })))

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
                                lore = GuiLoreSpec.Blocks(listOf(GuiLoreBlock(listOf(GuiLoreActions.singleClick(
                                        lang,
                                        player,
                                        lang.getMessage(player, "gui.user_settings.button.action")
                                ))))),
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
                meta.lore(CCSystem.getAPI().getLoreService().render(GuiLoreSpec.Blocks(listOf(
                        GuiLoreBlock(lang.getMessageList(player, "gui.player_world.creation_button.description").map(GuiLoreLine::Text)),
                        GuiLoreBlock(listOf(GuiLoreActions.singleClick(
                                lang,
                                player,
                                lang.getMessage(player, "gui.player_world.creation_button.action")
                        )))
                ))))
                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_CREATION_BUTTON)
                return item
        }

        private fun createCreationUnavailableButton(player: Player, reason: CreationBlockReason): ItemStack {
                val lang = plugin.languageManager
                val item = ItemStack(Material.BARRIER)
                val meta = item.itemMeta ?: return item
                meta.displayName(lang.getComponent(player, reason.displayKey))
                meta.lore(GuiItemFactory.menuLore(lang.getMessageList(player, reason.loreKey).map(GuiLoreLine::Text)))
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
                if (!PermissionManager.checkPermission(player, PermissionManager.WORLD_CREATE)) {
                        return CreationBlockReason.NO_PERMISSION
                }
                if (!WorldCreationChecks.check(player, notify = false)) return CreationBlockReason.POLICY_DENIED
                if (bypassLimits) return null
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
                val isOwnMenu = targetPlayerUuid == player.uniqueId
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

                meta.lore(CCSystem.getAPI().getLoreService().render(GuiLoreSpec.Blocks(buildList {
                        val pointIcon = if (plugin.playerPlatformResolver.isBedrock(player)) "" else "🛖 "
                        add(GuiLoreBlock(listOf(
                                GuiLoreLine.Data(
                                        lang.getMessage(player, "gui.player_world.stats_button.points_label"),
                                        "$pointIcon${stats.worldPoint}",
                                        "§6"
                                ),
                                GuiLoreLine.Text(lang.getMessage(player, "gui.player_world.stats_button.points_description"))
                        )))
                        add(GuiLoreBlock(if (bypassLimits) {
                                listOf(
                                        GuiLoreLine.Data(
                                                lang.getMessage(player, "gui.player_world.stats_button.world_count_label"),
                                                currentCreateCount,
                                                "§a§l"
                                        ),
                                        GuiLoreLine.Text(lang.getMessage(player, "gui.player_world.stats_button.slots_bypass_description"))
                                )
                        } else {
                                listOf(
                                        GuiLoreLine.Data(
                                                lang.getMessage(player, "gui.player_world.stats_button.slots_label"),
                                                "$currentCreateCount/$maxSlot",
                                                "§a§l"
                                        ),
                                        GuiLoreLine.Text(lang.getMessage(player, "gui.player_world.stats_button.slots_description"))
                                )
                        }))
                })))

                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_PLAYER_STATS)
                return item
        }

        private fun createPendingButton(player: Player): ItemStack {
                val lang = plugin.languageManager
                val pendingCount = plugin.pendingDecisionManager.getPendingCount(player.uniqueId)
                val latestPendingText = plugin.pendingDecisionManager
                        .getLatestPendingCreatedAt(player.uniqueId)
                        ?.let {
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                        .withZone(ZoneId.systemDefault())
                                        .format(Instant.ofEpochMilli(it))
                        }
                        ?: lang.getMessage(player, "gui.player_world.pending_button.none")
                val item = ItemStack(Material.WRITABLE_BOOK)
                val meta = item.itemMeta ?: return item
                meta.displayName(lang.getComponent(player, "gui.player_world.pending_button.display"))
                meta.lore(CCSystem.getAPI().getLoreService().render(GuiLoreSpec.Blocks(listOf(
                        GuiLoreBlock(
                                lang.getMessageList(player, "gui.player_world.pending_button.description")
                                        .map(GuiLoreLine::Text)
                        ),
                        GuiLoreBlock(listOf(
                                GuiLoreLine.Data(
                                        lang.getMessage(player, "gui.player_world.pending_button.count_label"),
                                        pendingCount,
                                        if (pendingCount > 0) "§e" else "§7"
                                ),
                                GuiLoreLine.Data(
                                        lang.getMessage(player, "gui.player_world.pending_button.latest_label"),
                                        latestPendingText,
                                        "§b"
                                )
                        )),
                        GuiLoreBlock(listOf(
                                GuiLoreActions.singleClick(
                                        lang,
                                        player,
                                        lang.getMessage(player, "gui.player_world.pending_button.action")
                                )
                        ))
                ))))
                meta.setEnchantmentGlintOverride(pendingCount > 0)
                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_PENDING_BUTTON)
                return item
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
                POLICY_DENIED(
                        "gui.player_world.creation_unavailable.policy_denied.display",
                        "gui.player_world.creation_unavailable.policy_denied.lore"
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
