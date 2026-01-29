package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.model.PlayerStats
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

class PlayerWorldGui(private val plugin: MyWorldManager) {

    private val repository = plugin.worldConfigRepository

    private val worldsPerRow = 7
    private val dataRowsPerPage = 4
    private val itemsPerPageNum = dataRowsPerPage * worldsPerRow // 28 items

    fun getPlayerWorlds(player: Player): List<WorldData> {
        val allWorlds = repository.findAll()
        return allWorlds.filter { 
            it.owner == player.uniqueId || 
            it.moderators.contains(player.uniqueId) || 
            it.members.contains(player.uniqueId) ||
            it.isArchived // アーカイブ済みも自分のなら表示
        }.filter { it.owner == player.uniqueId || !it.isArchived } // メンバーとして参加しているアーカイブ済みは非表示
    }

    fun open(player: Player, page: Int = 0, showBackButton: Boolean = false) {
        val playerWorlds = getPlayerWorlds(player)
        
        if (playerWorlds.isEmpty()) {
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.no_registered_worlds"))
            return
        }

        val startIndex = page * itemsPerPageNum
        val currentPageWorlds = playerWorlds.drop(startIndex).take(itemsPerPageNum)

        val neededDataRows = if (currentPageWorlds.isEmpty()) 1 else (currentPageWorlds.size + worldsPerRow - 1) / worldsPerRow
        val rowCount = (neededDataRows + 2).coerceIn(3, 6)
        
        val lang = plugin.languageManager
        val titleKey = "gui.player_world.title"
        if (!lang.hasKey(player, titleKey)) {
            player.sendMessage("§c[MyWorldManager] Error: Missing translation key: $titleKey")
            return
        }

        val titleStr = lang.getMessage(player, titleKey)
        val title = Component.text(titleStr)
        me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(plugin, player, "player_world", title)
        val inventory = Bukkit.createInventory(null, rowCount * 9, title)

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
                    inventory.setItem(rowStart + 1 + j, createWorldItem(player, currentPageWorlds[worldIndexInPage], player.uniqueId))
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
        val maxSlot = plugin.config.getInt("creation.max_create_count_default", 3) + stats.unlockedWorldSlot

        // マイワールド新規作成ボタン (Slot 2)
        if (currentCreateCount < maxSlot) {
            inventory.setItem(footerStart + 2, createCreationButton(player))
        }

        // プレイヤー統計ボタン (Slot 4)
        inventory.setItem(footerStart + 4, createStatsButton(player, currentCreateCount, maxSlot, stats))
        
        // 重大な設定の表示/非表示ボタン (Slot 6)
        inventory.setItem(footerStart + 6, createCriticalSettingsVisibilityButton(player, stats.criticalSettingsEnabled))

        if (page > 0) {
            inventory.setItem(footerStart + 1, createNavigationItem(player, Material.ARROW, lang.getMessage(player, "gui.common.prev_page"), page - 1))
        }

        if (showBackButton) {
            inventory.setItem(footerStart, createReturnButton(player))
        }
        if (startIndex + itemsPerPageNum < playerWorlds.size) {
            inventory.setItem(footerStart + 8, createNavigationItem(player, Material.ARROW, lang.getMessage(player, "gui.common.next_page"), page + 1))
        }

        player.openInventory(inventory)
    }

    private fun createWorldItem(player: Player, world: WorldData, @Suppress("UNUSED_PARAMETER") playerUuid: UUID): ItemStack {
        val item = ItemStack(world.icon)
        val meta = item.itemMeta ?: return item

        val lang = plugin.languageManager
        meta.displayName(lang.getComponent(player, "gui.common.world_item_name", world.name))

        val lore = mutableListOf<Component>()
        lore.add(lang.getComponent(player, "gui.common.separator"))
        
        if (world.description.isNotEmpty()) {
            lore.add(lang.getComponent(player, "gui.common.world_desc", world.description))
            lore.add(lang.getComponent(player, "gui.common.separator"))
        }
        
        val owner = Bukkit.getOfflinePlayer(world.owner)
        lore.add(lang.getComponent(player, "gui.player_world.world_item.owner", owner.name ?: lang.getMessage(player, "general.unknown")))
        
        // 公開レベル表示用
        val publishLevelColor = lang.getMessage(player, "publish_level.color.${world.publishLevel.name.lowercase()}")
        val publishLevelName = lang.getMessage(player, "publish_level.${world.publishLevel.name.lowercase()}")
        
        lore.add(lang.getComponent(player, "gui.player_world.world_item.publish", publishLevelColor, publishLevelName))

        lore.add(lang.getComponent(player, "gui.player_world.world_item.favorite", world.favorite))
        val totalRecent = world.recentVisitors.sum()
        lore.add(lang.getComponent(player, "gui.player_world.world_item.recent_visitors", totalRecent))
        
        lore.add(lang.getComponent(player, "gui.common.separator"))

        val now = LocalDate.now()
        val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val displayFormatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日")
        val expireDate = try { LocalDate.parse(world.expireDate, inputFormatter) } catch (e: Exception) { LocalDate.now().plusYears(1) }
        val daysRemaining = ChronoUnit.DAYS.between(now, expireDate)
        
        // 2900年以降は無期限として表示しない（ADMINモードなど）
        if (expireDate.year < 2900) {
            if (daysRemaining < 0) {
                meta.setEnchantmentGlintOverride(true)
            }
            val displayDate = displayFormatter.format(expireDate)
            
            lore.add(lang.getComponent(player, "gui.player_world.world_item.expires_at", daysRemaining, displayDate))
            lore.add(lang.getComponent(player, "gui.common.separator"))
        }
        
        if (!world.isArchived) {
            lore.add(lang.getComponent(player, "gui.player_world.world_item.warp"))
        }
        lore.add(lang.getComponent(player, "gui.player_world.world_item.settings"))
        if (world.isArchived) {
            lore.add(lang.getComponent(player, "gui.player_world.world_item.expired"))
            lore.add(lang.getComponent(player, "gui.common.separator"))
        }

        meta.lore(lore)
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_WORLD_ITEM)
        ItemTag.setWorldUuid(item, world.uuid)
        return item
    }

    private fun createCriticalSettingsVisibilityButton(player: Player, isEnabled: Boolean): ItemStack {
        val lang = plugin.languageManager
        val item = ItemStack(Material.RECOVERY_COMPASS)
        val meta = item.itemMeta ?: return item
        meta.displayName(lang.getComponent(player, "gui.user_settings.critical_settings_visibility.display"))
        
        val status = if (isEnabled) lang.getMessage(player, "messages.status_visible") else lang.getMessage(player, "messages.status_hidden")
        val lore = lang.getMessageList(player, "gui.user_settings.critical_settings_visibility.lore", mapOf("status" to status)).map { 
            net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(it).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
        }
        meta.lore(lore)
        
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_USER_SETTING_CRITICAL_VISIBILITY)
        return item
    }

    private fun createCreationButton(player: Player): ItemStack {
        val lang = plugin.languageManager
        val item = ItemStack(Material.NETHER_STAR)
        val meta = item.itemMeta ?: return item
        meta.displayName(lang.getComponent(player, "gui.player_world.creation_button.display"))
        meta.lore(lang.getComponentList(player, "gui.player_world.creation_button.lore"))
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_CREATION_BUTTON)
        return item
    }

    private fun createStatsButton(player: Player, currentCreateCount: Int, maxSlot: Int, stats: PlayerStats): ItemStack {
        val lang = plugin.languageManager
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as? org.bukkit.inventory.meta.SkullMeta ?: return item
        meta.owningPlayer = player
        
        meta.displayName(lang.getComponent(player, "gui.player_world.stats_button.display", player.name ?: lang.getMessage(player, "general.unknown")))
        
        val placeholders = mapOf(
            "point" to stats.worldPoint,
            "current_occupied" to currentCreateCount,
            "unlocked" to maxSlot,
            "icon" to "🛖" // 必要に応じて調整
        )
        
        meta.lore(lang.getComponentList(player, "gui.player_world.stats_button.lore", placeholders))
        
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

    private fun createNavigationItem(player: Player, material: Material, name: String, targetPage: Int): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        
        // 装飾を指定しない (LegacyComponentSerializerにおまかせする)
        meta.displayName(LegacyComponentSerializer.legacySection().deserialize(name).decoration(TextDecoration.ITALIC, false))
        
        item.itemMeta = meta
        ItemTag.setTargetPage(item, targetPage)
        val lang = plugin.languageManager
        val type = if (name == lang.getMessage(player, "gui.common.next_page")) ItemTag.TYPE_GUI_NAV_NEXT else ItemTag.TYPE_GUI_NAV_PREV
        ItemTag.tagItem(item, type)
        return item
    }

    private fun createReturnButton(player: Player): ItemStack {
        val lang = plugin.languageManager
        val item = ItemStack(Material.REDSTONE)
        val meta = item.itemMeta ?: return item
        meta.displayName(lang.getComponent(player, "gui.common.return").color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD))
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_RETURN)
        return item
    }
}
