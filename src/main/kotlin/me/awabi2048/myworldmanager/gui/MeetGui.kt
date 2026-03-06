package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class MeetGui(private val plugin: MyWorldManager) {
    
    // 1行7アイコン中央寄せのレイアウト (スロット 10~16, 19~25, ...)
    private val playerSlots = listOf(
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
    )
    private val itemsPerPage = playerSlots.size

    fun open(player: Player, showBackButton: Boolean? = null) {
        val lang = plugin.languageManager
        val session = plugin.meetSessionManager.getSession(player.uniqueId)
        
        if (showBackButton != null) {
            session.showBackButton = showBackButton
        }
        
        val titleKey = "gui.meet.title_list"
        if (!lang.hasKey(player, titleKey)) {
            player.sendMessage("§c[MyWorldManager] Error: Missing translation key: $titleKey")
            return
        }
        
        // マイワールドに滞在中で、且つ meetStatus が BUSY でないプレイヤーを抽出（自分以外）
        val targets = Bukkit.getOnlinePlayers().filter { target ->
            if (target.uniqueId == player.uniqueId) return@filter false

            if (target.world.uid == player.world.uid) {
                val stats = plugin.playerStatsRepository.findByUuid(target.uniqueId)
                return@filter stats.meetStatus != "BUSY"
            }
            
            val worldName = target.world.name
            val worldData = plugin.worldConfigRepository.findByWorldName(worldName) ?: return@filter false
            
            // 公開/限定公開のワールドにいるかチェック
            if (worldData.publishLevel != PublishLevel.PUBLIC && worldData.publishLevel != PublishLevel.FRIEND) return@filter false
            
            val stats = plugin.playerStatsRepository.findByUuid(target.uniqueId)
            stats.meetStatus != "BUSY"
        }.sortedBy { it.name }

        // 対象プレイヤーがいない場合でも、設定ボタンを表示するためにメニューは開く
        val totalPages = if (targets.isEmpty()) 1 else (targets.size + itemsPerPage - 1) / itemsPerPage
        val currentPage = session.currentPage.coerceIn(0, totalPages - 1)
        session.currentPage = currentPage
        val title = me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(lang.getMessage(player, titleKey))
        me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(plugin, player, "meet", title, MeetGuiHolder::class.java)
        
        val holder = MeetGuiHolder()
        val inventory = Bukkit.createInventory(holder, 45, title)
        holder.inv = inventory

        val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
        val greyPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)

        for (i in 0..8) inventory.setItem(i, blackPane)
        for (i in 36 until 45) inventory.setItem(i, blackPane)
        for (i in 9 until 36) {
            inventory.setItem(i, greyPane)
        }

        // プレイヤーの配置
        val pageTargets = targets.drop(currentPage * itemsPerPage).take(itemsPerPage)
        pageTargets.forEachIndexed { index, target ->
            inventory.setItem(playerSlots[index], createTargetHead(target, player, plugin))
        }

        if (pageTargets.isEmpty()) {
            inventory.setItem(22, createEmptyItem(player))
        }
        
        val statusSlot = 40
        
        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        val currentStatus = stats.meetStatus
        val statusNameKey = "general.status.${currentStatus.lowercase()}"
        val statusName = if (lang.hasKey(player, statusNameKey)) lang.getMessage(player, statusNameKey) else currentStatus

        val statusLore = mutableListOf<String>()
        statusLore.add(lang.getMessage(player, "gui.common.separator"))
        statusLore.add(lang.getMessage(player, "gui.meet.status_button.current", mapOf("status" to statusName)))
        statusLore.add(lang.getMessage(player, "gui.meet.status_button.click"))
        statusLore.add(lang.getMessage(player, "gui.common.separator"))

        val statusItem = ItemStack(Material.PLAYER_HEAD)
        val statusMeta = statusItem.itemMeta as? org.bukkit.inventory.meta.SkullMeta
        if (statusMeta != null) {
            statusMeta.owningPlayer = player
            statusMeta.displayName(LegacyComponentSerializer.legacySection().deserialize(lang.getMessage(player, "gui.meet.status_button.display", mapOf("player" to player.name))).decoration(TextDecoration.ITALIC, false))
            statusMeta.lore(statusLore.map { LegacyComponentSerializer.legacySection().deserialize(it).decoration(TextDecoration.ITALIC, false) })
            statusItem.itemMeta = statusMeta
        }
        ItemTag.tagItem(statusItem, ItemTag.TYPE_GUI_MEET_STATUS_TOGGLE)
        
        inventory.setItem(statusSlot, statusItem)

        if (currentPage > 0) {
            inventory.setItem(37, me.awabi2048.myworldmanager.util.GuiHelper.createPrevPageItem(plugin, player, "meet", currentPage - 1))
        }
        if (currentPage < totalPages - 1) {
            inventory.setItem(43, me.awabi2048.myworldmanager.util.GuiHelper.createNextPageItem(plugin, player, "meet", currentPage + 1))
        }

        // 戻るボタン
        if (session.showBackButton) {
            inventory.setItem(36, me.awabi2048.myworldmanager.util.GuiHelper.createReturnItem(plugin, player, "meet"))
        }

        player.openInventory(inventory)
    }

    private fun createReturnButton(player: Player): ItemStack {
        val lang = plugin.languageManager
        val item = ItemStack(Material.REDSTONE)
        val meta = item.itemMeta ?: return item
        meta.displayName(lang.getComponent(player, "gui.common.return").decorate(TextDecoration.BOLD))
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_RETURN)
        return item
    }

    private fun createEmptyItem(viewer: Player): ItemStack {
        val item = ItemStack(Material.QUARTZ)
        val meta = item.itemMeta ?: return item
        meta.displayName(
            LegacyComponentSerializer.legacySection().deserialize(
                plugin.languageManager.getMessage(viewer, "gui.meet.empty_message")
            ).decoration(TextDecoration.ITALIC, false)
        )
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_INFO)
        return item
    }

    private fun createTargetHead(target: Player, viewer: Player, plugin: MyWorldManager): ItemStack {
        val lang = plugin.languageManager
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as? org.bukkit.inventory.meta.SkullMeta ?: return item
        meta.owningPlayer = target

        val isOnline = target.isOnline
        val colorCode = if (isOnline) lang.getMessage(viewer, "publish_level.color.online") else lang.getMessage(viewer, "publish_level.color.offline")
        // 名前を色分けしたComponentに変換
        meta.displayName(LegacyComponentSerializer.legacySection().deserialize("$colorCode${target.name}").decoration(TextDecoration.ITALIC, false))

        val lore = mutableListOf<Component>()
        lore.add(lang.getComponent(viewer, "gui.common.separator"))

        // Status
        val stats = plugin.playerStatsRepository.findByUuid(target.uniqueId)
        val statusKey = "general.status.${stats.meetStatus.lowercase()}"
        val statusName = if (lang.hasKey(viewer, statusKey)) lang.getMessage(viewer, statusKey) else stats.meetStatus
        lore.add(lang.getComponent(viewer, "gui.meet.world_item.status", mapOf("status" to statusName)))

        // 現在のワールド名取得
        val world = target.world
        val worldName = world.name
        val worldData = plugin.worldConfigRepository.findByWorldName(worldName)
        val isSameWorld = target.world.uid == viewer.world.uid
        val displayWorldName = worldData?.name ?: run {
            val configMap = plugin.config.getConfigurationSection("world_display_names")
            configMap?.getString(worldName) ?: "???"
        }

        val currentWorldKey = if (isSameWorld && lang.hasKey(viewer, "gui.meet.world_item.current_world_same")) {
            "gui.meet.world_item.current_world_same"
        } else {
            "gui.meet.world_item.current_world"
        }
        lore.add(lang.getComponent(viewer, currentWorldKey, mapOf("world" to displayWorldName)))

        // クリックしてワールドを訪れる/申請の表示判定
        if (worldData != null && !isSameWorld) {
            val isMember = worldData.owner == viewer.uniqueId || 
                           worldData.moderators.contains(viewer.uniqueId) || 
                           worldData.members.contains(viewer.uniqueId)
                           
            // Logic based on status
            if (stats.meetStatus == "JOIN_ME") {
                // Anyone can join if public
                if (worldData.publishLevel == PublishLevel.PUBLIC || isMember) {
                    lore.add(lang.getComponent(viewer, "gui.meet.world_item.click_visit"))
                }
            } else if (stats.meetStatus == "ASK_ME") {
                // Request needed
                lore.add(lang.getComponent(viewer, "gui.meet.world_item.click_request"))
            }
        }

        lore.add(lang.getComponent(viewer, "gui.common.separator"))

        meta.lore(lore)
        item.itemMeta = meta
        
        // タグ付け
        ItemTag.tagItem(item, "gui_meet_target_head")
        return item
    }

    private fun createItem(material: Material, name: String, loreLines: List<String>, tag: String): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        
        meta.displayName(LegacyComponentSerializer.legacySection().deserialize(name).decoration(TextDecoration.ITALIC, false))
        meta.lore(loreLines.map { LegacyComponentSerializer.legacySection().deserialize(it).decoration(TextDecoration.ITALIC, false) })
        
        item.itemMeta = meta
        ItemTag.tagItem(item, tag)
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

    class MeetGuiHolder : org.bukkit.inventory.InventoryHolder {
        lateinit var inv: org.bukkit.inventory.Inventory
        override fun getInventory(): org.bukkit.inventory.Inventory = inv
    }
}

