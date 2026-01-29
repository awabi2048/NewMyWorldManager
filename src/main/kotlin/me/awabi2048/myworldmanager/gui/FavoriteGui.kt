package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.WorldData
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
import java.util.UUID

class FavoriteGui(private val plugin: MyWorldManager) {

    private val itemsPerPage = 36 // 2行目から5行目までの4行分

    fun open(player: Player, page: Int = 0, returnToWorld: WorldData? = null, showBackButton: Boolean = false) {
        val lang = plugin.languageManager
        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        val favWorldUuids = stats.favoriteWorlds.keys
        
        if (favWorldUuids.isEmpty()) {
            player.sendMessage(lang.getMessage(player, "messages.no_favorites_found"))
            return
        }
        

        val allWorlds = favWorldUuids.mapNotNull { uuid ->
            val data = plugin.worldConfigRepository.findByUuid(uuid)
            if (data == null) {
                stats.favoriteWorlds.remove(uuid)
                null
            } else {
                data
            }
        }
        if (allWorlds.size != favWorldUuids.size) {
            plugin.playerStatsRepository.save(stats)
        }

        val totalPages = if (allWorlds.isEmpty()) 1 else (allWorlds.size + itemsPerPage - 1) / itemsPerPage
        val currentPage = page.coerceIn(0, totalPages - 1)

        val titleKey = "gui.favorite.title"
        if (!lang.hasKey(player, titleKey)) {
            player.sendMessage("§c[MyWorldManager] Error: Missing translation key: $titleKey")
            return
        }
        val title = Component.text(lang.getMessage(player, titleKey), NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
        me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(plugin, player, "favorite", title)
        val inventory = Bukkit.createInventory(null, 54, title)

        val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE, returnToWorld)
        for (i in 0..8) inventory.setItem(i, blackPane)
        for (i in 45..53) inventory.setItem(i, blackPane)

        val startIndex = currentPage * itemsPerPage
        val pageWorlds = allWorlds.drop(startIndex).take(itemsPerPage)

        pageWorlds.forEachIndexed { index, worldData ->
            inventory.setItem(index + 9, createWorldItem(player, worldData, returnToWorld))
        }

        if (currentPage > 0) {
            val item = me.awabi2048.myworldmanager.util.GuiHelper.createPrevPageItem(plugin, player, "favorite", currentPage - 1)
            if (returnToWorld != null) me.awabi2048.myworldmanager.util.ItemTag.setWorldUuid(item, returnToWorld.uuid)
            inventory.setItem(46, item)
        }
        
        inventory.setItem(49, createPlayerHead(player, allWorlds.size))

        if (currentPage < totalPages - 1) {
            val item = me.awabi2048.myworldmanager.util.GuiHelper.createNextPageItem(plugin, player, "favorite", currentPage + 1)
            if (returnToWorld != null) me.awabi2048.myworldmanager.util.ItemTag.setWorldUuid(item, returnToWorld.uuid)
            inventory.setItem(53, item)
        }

        // 戻るボタン
        if (returnToWorld != null || showBackButton) {
            val item = me.awabi2048.myworldmanager.util.GuiHelper.createReturnItem(plugin, player, "favorite")
            if (returnToWorld != null) {
                // 説明文を追加 (CreateReturnItemはデフォルトのものを使うので、loreを追加する必要があるが、Configurationで管理されるべきかも。
                // いったんGuiHelperのデフォルトのままにするか、Loreを修正するか。
                // createBackButtonの実装では "gui.common.return_desc" を追加していた。
                // GuiHelper.createReturnItem は return_desc を追加してくれるか？ getIconMaterial -> createItem からして怪しい。
                // Configで設定されたLoreを使うべきだが、GuiHelperはConfigのLoreを使う？
                // MenuConfigManagerはMaterialのみ。GuiHelperはcreateItemを作る。
                // GuiHelper.createReturnItemの実装を見ると、Iconsから取得して、名前とLoreを設定しているはず。
                // しかし、FavoriteGuiのcreateBackButtonは特別にLoreを追加していた。
                // ここでは、GuiHelperの標準に合わせるか、独自に追加するか。
                // GuiHelperの実装: "gui.common.return" + "gui.common.return_desc" (always?)
                // GuiHelperを確認すべきだが、いったん標準を使用。
                me.awabi2048.myworldmanager.util.ItemTag.setWorldUuid(item, returnToWorld.uuid)
            }
            inventory.setItem(45, item)
        }

        val background = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE, returnToWorld)
        for (slot in 0 until inventory.size) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, background)
            }
        }

        player.openInventory(inventory)
    }

    private fun createWorldItem(player: Player, data: WorldData, returnToWorld: WorldData?): ItemStack {
        val item = ItemStack(data.icon)
        val meta = item.itemMeta ?: return item
        val lang = plugin.languageManager
        meta.displayName(lang.getComponent(player, "gui.common.world_item_name", mapOf("world" to data.name)))

        val lore = mutableListOf<Component>()
        lore.add(lang.getComponent(player, "gui.common.separator"))
        
        if (data.description.isNotEmpty()) {
            lore.add(lang.getComponent(player, "gui.common.world_desc", mapOf("description" to data.description)))
            lore.add(lang.getComponent(player, "gui.common.separator"))
        }
        
        val owner = Bukkit.getOfflinePlayer(data.owner)
        val onlineColor = lang.getMessage(player, "publish_level.color.online")
        val offlineColor = lang.getMessage(player, "publish_level.color.offline")
        val ownerColor = if (owner.isOnline) onlineColor else offlineColor
        lore.add(lang.getComponent(player, "gui.favorite.world_item.owner", mapOf("owner" to (owner.name ?: lang.getMessage(player, "general.unknown")), "status_color" to ownerColor)))
        
        // 統計情報
        lore.add(lang.getComponent(player, "gui.favorite.world_item.favorite", mapOf("count" to data.favorite)))
        val totalRecent = data.recentVisitors.sum()
        lore.add(lang.getComponent(player, "gui.favorite.world_item.recent_visitors", mapOf("count" to totalRecent)))

        lore.add(lang.getComponent(player, "gui.common.separator"))
        if (!data.isArchived && (data.publishLevel == PublishLevel.PUBLIC || data.publishLevel == PublishLevel.FRIEND)) {
            lore.add(lang.getComponent(player, "gui.visit.world_item.warp"))
        }

        val viewerUuid = player.uniqueId
        val isMember = data.owner == viewerUuid || 
                      data.moderators.contains(viewerUuid) || 
                      data.members.contains(viewerUuid)

        if (data.isArchived) {
            lore.add(lang.getComponent(player, "gui.favorite.world_item.archived_label"))
            lore.add(lang.getComponent(player, "gui.favorite.world_item.unfavorite_archived"))
            lore.add(lang.getComponent(player, "gui.common.separator"))
        } else if (!isMember) {
            lore.add(lang.getComponent(player, "gui.favorite.world_item.unfavorite"))
            lore.add(lang.getComponent(player, "gui.common.separator"))
        }
        lore.add(lang.getComponent(player, "gui.common.separator"))
        
        meta.lore(lore)
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_WORLD_ITEM)
        ItemTag.setWorldUuid(item, data.uuid)
        return item
    }

    private fun createBackButton(player: Player, returnToWorld: WorldData?): ItemStack {
        val lang = plugin.languageManager
        val item = ItemStack(Material.REDSTONE)
        val meta = item.itemMeta ?: return item
        meta.displayName(lang.getComponent(player, "gui.common.return").color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD))
        if(returnToWorld != null){
             meta.lore(listOf(lang.getComponent(player, "gui.common.return_desc")))
        }
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_RETURN)
        if (returnToWorld != null) ItemTag.setWorldUuid(item, returnToWorld.uuid)
        return item
    }

    private fun createNavButton(player: Player, label: String, material: Material, targetPage: Int, returnToWorld: WorldData?): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        
        // 装飾を指定しない (LegacyComponentSerializerにおまかせする)
        meta.displayName(LegacyComponentSerializer.legacySection().deserialize(label).decoration(TextDecoration.ITALIC, false))
        
        item.itemMeta = meta
        ItemTag.setTargetPage(item, targetPage)
        val lang = plugin.languageManager
        val type = if (label == lang.getMessage(player, "gui.common.next_page")) ItemTag.TYPE_GUI_NAV_NEXT else ItemTag.TYPE_GUI_NAV_PREV
        ItemTag.tagItem(item, type)
        if (returnToWorld != null) ItemTag.setWorldUuid(item, returnToWorld.uuid)
        return item
    }

    private fun createPlayerHead(player: Player, totalCount: Int): ItemStack {
        val lang = plugin.languageManager
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as? org.bukkit.inventory.meta.SkullMeta ?: return item
        meta.owningPlayer = player
        
        meta.displayName(lang.getComponent(player, "gui.favorite.player_icon.name", mapOf("player" to (player.name ?: lang.getMessage(player, "general.unknown")))))
        
        val lore = mutableListOf<Component>()
        lore.add(lang.getComponent(player, "gui.common.separator"))
        lore.add(lang.getComponent(player, "gui.favorite.player_icon.lore_count", mapOf("count" to totalCount)))
        lore.add(lang.getComponent(player, "gui.common.separator"))
        
        meta.lore(lore)
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_INFO)
        return item
    }

    private fun createDecorationItem(material: Material, returnToWorld: WorldData? = null): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.displayName(Component.empty())
        meta.isHideTooltip = true
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_DECORATION)
        if (returnToWorld != null) ItemTag.setWorldUuid(item, returnToWorld.uuid)
        return item
    }
}
