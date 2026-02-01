package me.awabi2048.myworldmanager.gui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.PlayerStats
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class PlayerWorldGui(private val plugin: MyWorldManager) {

        private val repository = plugin.worldConfigRepository

        private val worldsPerRow = 7
        private val dataRowsPerPage = 4
        private val itemsPerPageNum = dataRowsPerPage * worldsPerRow // 28 items

        fun getPlayerWorlds(player: Player): List<WorldData> {
                val allWorlds = repository.findAll()
                return allWorlds
                        .filter {
                                it.owner == player.uniqueId ||
                                        it.moderators.contains(player.uniqueId) ||
                                        it.members.contains(player.uniqueId) ||
                                        it.isArchived // アーカイブ済みも自分のなら表示
                        }
                        .filter {
                                it.owner == player.uniqueId || !it.isArchived
                        } // メンバーとして参加しているアーカイブ済みは非表示
                        .sortedWith(compareBy<WorldData> { it.isArchived }.thenByDescending { it.createdAt })
        }

        fun open(player: Player, page: Int = 0, showBackButton: Boolean? = null) {
                val session = plugin.playerWorldSessionManager.getSession(player.uniqueId)
                if (showBackButton != null) {
                        session.showBackButton = showBackButton
                }

                repository.loadAll()
                val playerWorlds = getPlayerWorlds(player)

                if (playerWorlds.isEmpty() && !session.showBackButton) {
                        player.sendMessage(
                                plugin.languageManager.getMessage(
                                        player,
                                        "error.no_registered_worlds"
                                )
                        )
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
                val title = Component.text(titleStr)
                me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(
                        plugin,
                        player,
                        "player_world",
                        title,
                        PlayerWorldGuiHolder::class.java
                )

                val holder = PlayerWorldGuiHolder()
                val inventory = Bukkit.createInventory(holder, rowCount * 9, title)
                holder.inv = inventory

                val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
                val greyPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)

                for (i in 0..8) inventory.setItem(i, blackPane)

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
                                                        player.uniqueId
                                                )
                                        )
                                } else {
                                        inventory.setItem(rowStart + 1 + j, greyPane)
                                }
                        }
                }

                val footerStart = (rowCount - 1) * 9
                for (i in 0..8) inventory.setItem(footerStart + i, blackPane)

                // 統計情報の取得
                val currentCreateCount = playerWorlds.count { it.owner == player.uniqueId }
                val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                val maxSlot =
                        plugin.config.getInt("creation.max_create_count_default", 3) +
                                stats.unlockedWorldSlot

                // マイワールド新規作成ボタン (Slot 2)
                if (currentCreateCount < maxSlot) {
                        inventory.setItem(footerStart + 2, createCreationButton(player))
                }

                // プレイヤー統計ボタン (Slot 4)
                inventory.setItem(
                        footerStart + 4,
                        createStatsButton(player, currentCreateCount, maxSlot, stats)
                )

                // 重大な設定の表示/非表示ボタン (Slot 6)
                inventory.setItem(
                        footerStart + 6,
                        createCriticalSettingsVisibilityButton(
                                player,
                                stats.criticalSettingsEnabled
                        )
                )

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
                                lang.getMessage(player, "world_tag.${it.name.lowercase()}")
                        }
                        lang.getMessage(player, "gui.player_world.world_item.tag", mapOf("tags" to tagNames))
                } else ""

                val now = LocalDate.now()
                val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                val displayFormatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日")
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
                
                val warpAction = lang.getMessage(player, "gui.player_world.world_item.warp")
                val settingsAction = lang.getMessage(player, "gui.player_world.world_item.settings")

                val separator = lang.getComponent(player, "gui.common.separator")

                meta.lore(
                        me.awabi2048.myworldmanager.util.GuiHelper.cleanupLore(
                                lang.getComponentList(
                                        player,
                                        "gui.player_world.world_item.lore",
                                        mapOf(
                                                "description" to formattedDesc,
                                                "owner_line" to ownerLine,
                                                "publish_line" to publishLine,
                                                "favorite_line" to favoriteLine,
                                                "visitor_line" to visitorLine,
                                                "tag_line" to tagLine,
                                                "expires_at_line" to expiresAtLine,
                                                "expired_line" to expiredLine,
                                                "warp_action" to warpAction,
                                                "settings_action" to settingsAction
                                        )
                                ),
                                separator
                        )
                )

                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_WORLD_ITEM)
                ItemTag.setWorldUuid(item, world.uuid)
                return item
        }

        private fun createCriticalSettingsVisibilityButton(
                player: Player,
                isEnabled: Boolean
        ): ItemStack {
                val lang = plugin.languageManager
                val item = ItemStack(Material.RECOVERY_COMPASS)
                val meta = item.itemMeta ?: return item
                meta.displayName(
                        lang.getComponent(
                                player,
                                "gui.user_settings.critical_settings_visibility.display"
                        )
                )

                val status = if (isEnabled) lang.getMessage(player, "messages.status_visible")
                else lang.getMessage(player, "messages.status_hidden")

                meta.lore(
                        lang.getComponentList(
                                player,
                                "gui.user_settings.critical_settings_visibility.lore",
                                mapOf("status" to status)
                        )
                )

                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_USER_SETTING_CRITICAL_VISIBILITY)
                return item
        }

        private fun createCreationButton(player: Player): ItemStack {
                val lang = plugin.languageManager
                val item = ItemStack(Material.NETHER_STAR)
                val meta = item.itemMeta ?: return item
                meta.displayName(
                        lang.getComponent(player, "gui.player_world.creation_button.display")
                )
                meta.lore(lang.getComponentList(player, "gui.player_world.creation_button.lore"))
                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_CREATION_BUTTON)
                return item
        }

        private fun createStatsButton(
                player: Player,
                currentCreateCount: Int,
                maxSlot: Int,
                stats: PlayerStats
        ): ItemStack {
                val lang = plugin.languageManager
                val item = ItemStack(Material.PLAYER_HEAD)
                val meta = item.itemMeta as? org.bukkit.inventory.meta.SkullMeta ?: return item
                meta.owningPlayer = player

                meta.displayName(
                        lang.getComponent(
                                player,
                                "gui.player_world.stats_button.display",
                                mapOf("player" to PlayerNameUtil.getNameOrDefault(player.uniqueId, lang.getMessage(player, "general.unknown")))

                        )
                )

                meta.lore(
                        lang.getComponentList(
                                player,
                                "gui.player_world.stats_button.lore",
                                mapOf(
                                        "point" to stats.worldPoint,
                                        "current_occupied" to currentCreateCount,
                                        "unlocked" to maxSlot,
                                        "icon" to "🛖"
                                )
                        )
                )

                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_PLAYER_STATS)
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

        class PlayerWorldGuiHolder : org.bukkit.inventory.InventoryHolder {
                lateinit var inv: org.bukkit.inventory.Inventory
                override fun getInventory(): org.bukkit.inventory.Inventory = inv
        }
}
