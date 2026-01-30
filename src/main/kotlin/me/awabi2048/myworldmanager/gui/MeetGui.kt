package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

class MeetGui(private val plugin: MyWorldManager) {
    
    // 1行7アイコン中央寄せのレイアウト (スロット 10~16, 19~25, ...)
    private val playerSlots = listOf(
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    )

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
            
            val worldName = target.world.name
            val worldData = plugin.worldConfigRepository.findByWorldName(worldName) ?: return@filter false
            
            // 公開/限定公開のワールドにいるかチェック
            if (worldData.publishLevel != PublishLevel.PUBLIC && worldData.publishLevel != PublishLevel.FRIEND) return@filter false
            
            val stats = plugin.playerStatsRepository.findByUuid(target.uniqueId)
            stats.meetStatus != "BUSY"
        }.sortedBy { it.name }

        // 対象プレイヤーがいない場合でも、設定ボタンを表示するためにメニューは開く
        val userCount = targets.size
        val rowCount = if (userCount <= 7) 3 else if (userCount <= 14) 4 else if (userCount <= 21) 5 else 6
        val title = Component.text(lang.getMessage(player, titleKey))
        me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(plugin, player, "meet", title, MeetGuiHolder::class.java)
        
        val holder = MeetGuiHolder()
        val inventory = Bukkit.createInventory(holder, rowCount * 9, title)
        holder.inv = inventory

        // 背景
        val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
        val greyPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)

        // 1行目と最終行は黒色
        for (i in 0..8) inventory.setItem(i, blackPane)
        for (i in (rowCount - 1) * 9 until rowCount * 9) inventory.setItem(i, blackPane)
        
        // その他は灰色で埋める
        for (i in 9 until (rowCount - 1) * 9) {
            inventory.setItem(i, greyPane)
        }

        // プレイヤーの配置
        targets.forEachIndexed { index, target ->
            if (index < playerSlots.size) {
                val slot = playerSlots[index]
                if (slot < inventory.size) {
                    inventory.setItem(slot, createTargetHead(target, player, plugin))
                }
            }
        }
        
        // ステータス変更ボタン (最下段 5スロット目 -> index: (rowCount-1)*9 + 4)
        val statusSlot = (rowCount - 1) * 9 + 4
        
        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        val currentStatus = stats.meetStatus
        val statusNameKey = "general.status.${currentStatus.lowercase()}"
        val statusName = if (lang.hasKey(player, statusNameKey)) lang.getMessage(player, statusNameKey) else currentStatus
        
        // Next status for display
        val nextStatus = when (currentStatus) {
            "JOIN_ME" -> "ASK_ME"
            "ASK_ME" -> "BUSY"
            else -> "JOIN_ME"
        }
        val nextStatusNameKey = "general.status.${nextStatus.lowercase()}"
        val nextStatusName = if (lang.hasKey(player, nextStatusNameKey)) lang.getMessage(player, nextStatusNameKey) else nextStatus

        val statusLore = mutableListOf<String>()
        statusLore.add(lang.getMessage(player, "gui.meet.status_button.current", mapOf("status" to statusName)))
        statusLore.add(lang.getMessage(player, "gui.meet.status_button.next", mapOf("next_status" to nextStatusName)))
        statusLore.add("")
        statusLore.add(lang.getMessage(player, "gui.meet.status_button.click"))

        val statusItem = ItemStack(Material.PLAYER_HEAD)
        val statusMeta = statusItem.itemMeta as? org.bukkit.inventory.meta.SkullMeta
        if (statusMeta != null) {
            statusMeta.owningPlayer = player
            statusMeta.displayName(LegacyComponentSerializer.legacySection().deserialize(lang.getMessage(player, "gui.meet.status_button.display")).decoration(TextDecoration.ITALIC, false))
            statusMeta.lore(statusLore.map { LegacyComponentSerializer.legacySection().deserialize(it).decoration(TextDecoration.ITALIC, false) })
            statusItem.itemMeta = statusMeta
        }
        ItemTag.tagItem(statusItem, ItemTag.TYPE_GUI_MEET_STATUS_TOGGLE)
        
        inventory.setItem(statusSlot, statusItem)

        // 戻るボタン
        if (session.showBackButton) {
            val backButtonSlot = (rowCount - 1) * 9
            inventory.setItem(backButtonSlot, me.awabi2048.myworldmanager.util.GuiHelper.createReturnItem(plugin, player, "meet"))
        }

        player.openInventory(inventory)
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
        val displayWorldName = worldData?.name ?: run {
            val configMap = plugin.config.getConfigurationSection("world_display_names")
            configMap?.getString(worldName) ?: "???"
        }

        lore.add(lang.getComponent(viewer, "gui.meet.world_item.current_world", mapOf("world" to displayWorldName)))

        // クリックしてワールドを訪れる/申請の表示判定
        if (worldData != null) {
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

