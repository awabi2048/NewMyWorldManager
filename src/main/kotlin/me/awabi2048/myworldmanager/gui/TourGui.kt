package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.TourData
import me.awabi2048.myworldmanager.model.TourSignData
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

class TourGui(private val plugin: MyWorldManager) {
    private val pageSlots = listOf(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43)

    fun openVisitorMenu(player: Player, worldData: WorldData, page: Int = 0) {
        openPagedTourMenu(player, worldData, GuiHelper.inventoryTitle(Component.text("ツアー一覧")), VisitorTourHolder(worldData.uuid, page), page, true, null)
    }

    fun openStartSelectionMenu(player: Player, worldData: WorldData, signUuid: java.util.UUID) {
        openPagedTourMenu(player, worldData, GuiHelper.inventoryTitle(Component.text("起点ツアー選択")), StartSelectionHolder(worldData.uuid, signUuid), 0, false, signUuid)
    }

    private fun openPagedTourMenu(player: Player, worldData: WorldData, title: Component, holder: BaseHolder, page: Int, showWorldIcon: Boolean, filterSignUuid: java.util.UUID?) {
        val tours = (if (filterSignUuid == null) plugin.tourManager.validTours(worldData) else plugin.tourManager.findToursBySign(worldData, filterSignUuid).filter { it.signUuids.size >= 2 })
        val safePage = page.coerceAtLeast(0)
        val pageCount = tours.drop(safePage * pageSlots.size).take(pageSlots.size).size
        val contentRows = maxOf(1, ((maxOf(pageCount, 7) + 6) / 7))
        val rows = (contentRows + 2).coerceIn(3, 6)
        val inventory = Bukkit.createInventory(holder, rows * 9, title)
        holder.inv = inventory
        fillBase(inventory)
        tours.drop(safePage * pageSlots.size).take((rows - 2) * 7).forEachIndexed { index, tour ->
            val row = index / 7 + 1
            val col = index % 7 + 1
            inventory.setItem(row * 9 + col, createTourItem(player, worldData, tour, false))
        }
        val footerStart = inventory.size - 9
        if (showWorldIcon) inventory.setItem(footerStart + 4, createCurrentWorldItem(player, worldData))
        if (safePage > 0) inventory.setItem(footerStart, GuiHelper.createPrevPageItem(plugin, player, "tour", safePage - 1))
        if ((safePage + 1) * pageSlots.size < tours.size) inventory.setItem(footerStart + 8, GuiHelper.createNextPageItem(plugin, player, "tour", safePage + 1))
        player.openInventory(inventory)
    }

    fun openEditMenu(player: Player, worldData: WorldData, page: Int = 0) {
        val tours = worldData.tours.sortedBy { it.createdAt }
        val rows = (((minOf(pageSlots.size, maxOf(7, tours.size)) + 6) / 7) + 2).coerceIn(3, 6)
        val holder = EditTourHolder(worldData.uuid, page)
        val inventory = Bukkit.createInventory(holder, rows * 9, GuiHelper.inventoryTitle(Component.text("ツアー編集")))
        holder.inv = inventory
        fillBase(inventory)
        val safePage = page.coerceAtLeast(0)
        tours.drop(safePage * pageSlots.size).take((rows - 2) * 7).forEachIndexed { index, tour ->
            val row = index / 7 + 1
            val col = index % 7 + 1
            inventory.setItem(row * 9 + col, createEditTourItem(player, worldData, tour))
        }
        inventory.setItem(inventory.size - 9, createSimpleItem(Material.REDSTONE, "§7戻る", emptyList(), ItemTag.TYPE_GUI_TOUR_BACK))
        if (worldData.tours.size < plugin.tourManager.getTourLimit(player, worldData)) inventory.setItem(inventory.size - 7, createSimpleItem(Material.NETHER_STAR, "§bツアーを新規作成", listOf(separator(player), "§7新しいツアーを作成します", separator(player), "§e§nクリックで作成"), ItemTag.TYPE_GUI_TOUR_CREATE))
        inventory.setItem(inventory.size - 5, createCurrentWorldItem(player, worldData))
        inventory.setItem(inventory.size - 3, createSimpleItem(Material.REDSTONE_TORCH, "§eInfo", listOf(separator(player), "§7Tour看板を登録して", "§7ワールド観光ルートを作成します。", "§7起点は右クリック、編集はShift+右クリックです。", separator(player)), ItemTag.TYPE_GUI_TOUR_INFO))
        player.openInventory(inventory)
    }

    fun openSingleEditMenu(player: Player, worldData: WorldData, tour: TourData, isNew: Boolean = false) {
        val signRows = ((tour.signUuids.size + 1 + 6) / 7).coerceAtLeast(1).coerceAtMost(4)
        val rows = (signRows + 2).coerceAtMost(6)
        val holder = SingleTourHolder(worldData.uuid, tour.uuid, isNew)
        val inventory = Bukkit.createInventory(holder, rows * 9, GuiHelper.inventoryTitle(Component.text("ツアー編集: ${tour.name}")))
        holder.inv = inventory
        fillBase(inventory)
        val slots = mutableListOf<Int>()
        repeat(signRows) { row -> slots.addAll((1..7).map { (row + 1) * 9 + it }) }
        tour.signUuids.take(28).forEachIndexed { index, signUuid -> plugin.tourManager.getSign(worldData, signUuid)?.let { inventory.setItem(slots[index], createSignItem(player, it, "§e§nクリックで削除")) } }
        if (tour.signUuids.size < 28) inventory.setItem(slots[tour.signUuids.size], createSimpleItem(Material.YELLOW_STAINED_GLASS_PANE, "§e§nクリックで経由地点を追加", emptyList(), ItemTag.TYPE_GUI_TOUR_ADD_SIGN))
        val bottom = inventory.size - 9
        inventory.setItem(bottom, createSimpleItem(Material.REDSTONE, "§7戻る", emptyList(), ItemTag.TYPE_GUI_TOUR_BACK))
        inventory.setItem(bottom + 2, createSimpleItem(Material.NAME_TAG, "§e表示情報を変更", listOf(separator(player), "§e§l| §e左クリック §7名前と説明を変更", "§e§l| §e右クリック §7アイコンを変更", separator(player)), ItemTag.TYPE_GUI_TOUR_EDIT_TEXT))
        inventory.setItem(bottom + 4, createSimpleItem(Material.LIME_WOOL, "§a保存", listOf("§e§nクリックで保存"), ItemTag.TYPE_GUI_TOUR_SAVE))
        inventory.setItem(bottom + 6, createSimpleItem(Material.LAVA_BUCKET, "§cツアーを削除", listOf("§e§nクリックで実行"), ItemTag.TYPE_GUI_TOUR_DELETE))
        player.openInventory(inventory)
    }

    fun openAddSignMenu(player: Player, worldData: WorldData, tour: TourData, isNew: Boolean = false) {
        val holder = AddSignHolder(worldData.uuid, tour.uuid, isNew)
        val signCount = worldData.tourSigns.size.coerceAtMost(28)
        val rows = (((maxOf(signCount, 7) + 6) / 7) + 2).coerceIn(3, 6)
        val inventory = Bukkit.createInventory(holder, rows * 9, GuiHelper.inventoryTitle(Component.text("看板を追加")))
        holder.inv = inventory
        fillBase(inventory)
        worldData.tourSigns.sortedBy { it.createdAt }.take((rows - 2) * 7).forEachIndexed { index, sign ->
            val row = index / 7 + 1
            val col = index % 7 + 1
            inventory.setItem(row * 9 + col, createSignItem(player, sign, "§e§nクリックで追加", Material.PALE_OAK_SIGN))
        }
        inventory.setItem(inventory.size - 9, createSimpleItem(Material.REDSTONE, "§7戻る", emptyList(), ItemTag.TYPE_GUI_TOUR_BACK))
        player.openInventory(inventory)
    }

    fun openDeleteConfirm(player: Player, worldData: WorldData, tour: TourData, isNew: Boolean = false) {
        val holder = DeleteTourHolder(worldData.uuid, tour.uuid, isNew)
        val inventory = Bukkit.createInventory(holder, 27, GuiHelper.inventoryTitle(Component.text("ツアー削除確認")))
        holder.inv = inventory
        fillBase(inventory)
        inventory.setItem(11, createSimpleItem(Material.LIME_WOOL, "§a削除する", listOf("§c元に戻せません"), ItemTag.TYPE_GUI_CONFIRM))
        inventory.setItem(15, createSimpleItem(Material.RED_WOOL, "§cキャンセル", listOf("§eクリックで戻る"), ItemTag.TYPE_GUI_CANCEL))
        player.openInventory(inventory)
    }

    private fun fillBase(inventory: Inventory) {
        val black = decoration(Material.BLACK_STAINED_GLASS_PANE)
        val gray = decoration(Material.GRAY_STAINED_GLASS_PANE)
        for (slot in 0 until inventory.size) inventory.setItem(slot, gray)
        for (i in 0..8) inventory.setItem(i, black)
        for (i in inventory.size - 9 until inventory.size) inventory.setItem(i, black)
    }

    private fun createCurrentWorldItem(player: Player, worldData: WorldData): ItemStack {
        val lang = plugin.languageManager
        val item = ItemStack(worldData.icon)
        val meta = item.itemMeta ?: return item
        val owner = Bukkit.getOfflinePlayer(worldData.owner)
        val formattedDesc = if (worldData.description.isNotEmpty()) lang.getMessage(player, "gui.common.world_desc", mapOf("description" to worldData.description)) else ""
        val onlineColor = lang.getMessage(player, "publish_level.color.online")
        val offlineColor = lang.getMessage(player, "publish_level.color.offline")
        val statusColor = if (owner.isOnline) onlineColor else offlineColor
        val ownerLine = lang.getMessage(player, "gui.favorite.world_item.owner", mapOf("owner" to (owner.name ?: lang.getMessage(player, "general.unknown")), "status_color" to statusColor))
        val favoriteLine = lang.getMessage(player, "gui.favorite.world_item.favorite", mapOf("count" to worldData.favorite))
        val visitorLine = lang.getMessage(player, "gui.favorite.world_item.recent_visitors", mapOf("count" to worldData.recentVisitors.sum()))
        val tagLine = if (worldData.tags.isNotEmpty()) lang.getMessage(player, "gui.favorite.world_item.tag", mapOf("tags" to worldData.tags.joinToString(", ") { plugin.worldTagManager.getDisplayName(player, it) })) else ""
        val separator = lang.getComponent(player, "gui.common.separator")
        meta.displayName(LegacyComponentSerializer.legacySection().deserialize(lang.getMessage(player, "gui.favorite.current_world.name")).decoration(TextDecoration.ITALIC, false))
        meta.lore(GuiHelper.cleanupLore(lang.getComponentList(player, "gui.favorite.current_world.lore", mapOf("world_line" to lang.getMessage(player, "gui.favorite.current_world.world_name", mapOf("world" to worldData.name)), "description" to formattedDesc, "owner_line" to ownerLine, "favorite_line" to favoriteLine, "visitor_line" to visitorLine, "tag_line" to tagLine)), separator))
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_TOUR_CURRENT_WORLD)
        return item
    }

    private fun createTourItem(player: Player, worldData: WorldData, tour: TourData, editing: Boolean): ItemStack {
        val current = plugin.tourSessionManager.get(player.uniqueId)?.let { it.tourUuid == tour.uuid && it.worldUuid == worldData.uuid } == true
        val countLine = if (tour.completedCount == 0) "§f§l| §7訪問者 §7まだいません" else "§f§l| §7訪問者 §a${tour.completedCount}人"
        val action = if (editing) "§e§nクリックでこのツアーを編集" else if (current) "§bツアー中！" else "§e§nクリックでこのツアーを開始"
        val item = createSimpleItem(Material.PALE_OAK_BOAT, tour.name, listOf(separator(player), normalizeDescription(tour.description), countLine, separator(player), action), ItemTag.TYPE_GUI_TOUR_ITEM)
        ItemTag.setString(item, "tour_uuid", tour.uuid.toString())
        return item
    }

    private fun createEditTourItem(player: Player, worldData: WorldData, tour: TourData): ItemStack = createTourItem(player, worldData, tour, true)

    private fun createSignItem(player: Player, sign: TourSignData, actionLine: String, material: Material = Material.OAK_SIGN): ItemStack {
        val item = createSimpleItem(material, sign.title, listOf(separator(player), normalizeDescription(sign.description), separator(player), actionLine), ItemTag.TYPE_GUI_TOUR_SIGN_ITEM)
        ItemTag.setString(item, "tour_sign_uuid", sign.uuid.toString())
        return item
    }

    private fun createSimpleItem(material: Material, name: String, lore: List<String>, type: String): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.displayName(LegacyComponentSerializer.legacySection().deserialize(name).decoration(TextDecoration.ITALIC, false))
        meta.lore(lore.map { LegacyComponentSerializer.legacySection().deserialize(it).decoration(TextDecoration.ITALIC, false) })
        item.itemMeta = meta
        ItemTag.tagItem(item, type)
        return item
    }

    private fun decoration(material: Material): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.displayName(Component.empty())
        meta.isHideTooltip = true
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_DECORATION)
        return item
    }

    private fun separator(player: Player): String = plugin.languageManager.getMessage(player, "gui.common.separator")
    private fun normalizeDescription(text: String): String = if (text.startsWith("§")) text else "§7$text"

    abstract class BaseHolder : InventoryHolder { lateinit var inv: Inventory; override fun getInventory(): Inventory = inv }
    class VisitorTourHolder(val worldUuid: java.util.UUID, val page: Int) : BaseHolder()
    class EditTourHolder(val worldUuid: java.util.UUID, val page: Int) : BaseHolder()
    class SingleTourHolder(val worldUuid: java.util.UUID, val tourUuid: java.util.UUID, val isNew: Boolean) : BaseHolder()
    class AddSignHolder(val worldUuid: java.util.UUID, val tourUuid: java.util.UUID, val isNew: Boolean) : BaseHolder()
    class DeleteTourHolder(val worldUuid: java.util.UUID, val tourUuid: java.util.UUID, val isNew: Boolean) : BaseHolder()
    class StartSelectionHolder(val worldUuid: java.util.UUID, val signUuid: java.util.UUID) : BaseHolder()
}
