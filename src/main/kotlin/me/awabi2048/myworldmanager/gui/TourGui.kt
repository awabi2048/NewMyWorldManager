package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.TourData
import me.awabi2048.myworldmanager.model.TourWaypointData
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.GuiItemFactory
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
        openPagedTourMenu(player, worldData, GuiHelper.inventoryTitle(Component.text(plugin.languageManager.getMessage(player, "gui.tour.menu.visitor_title"))), VisitorTourHolder(worldData.uuid, page), page, true, null)
    }

    fun openStartSelectionMenu(player: Player, worldData: WorldData, signUuid: java.util.UUID) {
        openPagedTourMenu(player, worldData, GuiHelper.inventoryTitle(Component.text(plugin.languageManager.getMessage(player, "gui.tour.menu.start_selection_title"))), StartSelectionHolder(worldData.uuid, signUuid), 0, false, signUuid)
    }

    fun openStartConfirm(player: Player, worldData: WorldData, tour: TourData) {
        val ownerName = Bukkit.getOfflinePlayer(tour.createdBy ?: worldData.owner).name
            ?: plugin.languageManager.getMessage(player, "general.unknown")
        val holder = StartConfirmHolder(worldData.uuid, tour.uuid)
        val inventory = Bukkit.createInventory(holder, 45, GuiHelper.inventoryTitle(Component.text("§b【${tour.name}】")))
        holder.inv = inventory
        fillBase(inventory)

        val description = if (tour.description.isBlank()) "§7" else normalizeDescription(tour.description)
        val byLine = "§7by $ownerName"
        inventory.setItem(
            22,
            createItem(
                Material.FILLED_MAP,
                "§b【${tour.name}】",
                framedLore(description, "", byLine),
                ItemTag.TYPE_GUI_INFO
            )
        )
        inventory.setItem(
            40,
            createLoreItem(
                Material.LIME_WOOL,
                "§eこのツアーをはじめる！",
                listOf("§7クリックしてこのワールドツアーを開始します"),
                ItemTag.TYPE_GUI_CONFIRM,
                LoreStyle.RAW
            )
        )
        player.openInventory(inventory)
    }

    private fun openPagedTourMenu(player: Player, worldData: WorldData, title: Component, holder: BaseHolder, page: Int, showWorldIcon: Boolean, filterSignUuid: java.util.UUID?) {
        val tours = (if (filterSignUuid == null) plugin.tourManager.validTours(worldData) else plugin.tourManager.findToursBySign(worldData, filterSignUuid))
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
        val lang = plugin.languageManager
        val tours = worldData.tours.sortedBy { it.createdAt }
        val rows = (((minOf(pageSlots.size, maxOf(7, tours.size)) + 6) / 7) + 2).coerceIn(3, 6)
        val holder = EditTourHolder(worldData.uuid, page)
        val inventory = Bukkit.createInventory(holder, rows * 9, GuiHelper.inventoryTitle(Component.text(lang.getMessage(player, "gui.tour.menu.edit_title"))))
        holder.inv = inventory
        fillBase(inventory)
        val safePage = page.coerceAtLeast(0)
        tours.drop(safePage * pageSlots.size).take((rows - 2) * 7).forEachIndexed { index, tour ->
            val row = index / 7 + 1
            val col = index % 7 + 1
            inventory.setItem(row * 9 + col, createEditTourItem(player, worldData, tour))
        }
        inventory.setItem(inventory.size - 9, createLoreItem(Material.REDSTONE, lang.getMessage(player, "gui.tour.menu.back"), emptyList(), ItemTag.TYPE_GUI_TOUR_BACK))
        if (worldData.tours.size < plugin.tourManager.getTourLimit(player, worldData)) {
            val createContent = listOf(lang.getMessage(player, "gui.tour.menu.create.description"), lang.getMessage(player, "gui.tour.menu.create.action"))
            inventory.setItem(inventory.size - 7, createLoreItem(Material.NETHER_STAR, lang.getMessage(player, "gui.tour.menu.create.display"), createContent, ItemTag.TYPE_GUI_TOUR_CREATE, LoreStyle.FRAMED))
        }
        inventory.setItem(inventory.size - 5, createCurrentWorldItem(player, worldData))
        val infoLines = lang.getMessageList(player, "gui.tour.menu.info.lore")
        inventory.setItem(inventory.size - 3, createLoreItem(Material.REDSTONE_TORCH, lang.getMessage(player, "gui.tour.menu.info.display"), infoLines, ItemTag.TYPE_GUI_TOUR_INFO, LoreStyle.FRAMED))
        player.openInventory(inventory)
    }

    fun openSingleEditMenu(player: Player, worldData: WorldData, tour: TourData, isNew: Boolean = false) {
        val lang = plugin.languageManager
        val waypointRows = ((tour.waypoints.size + 1 + 6) / 7).coerceAtLeast(1).coerceAtMost(4)
        val rows = (waypointRows + 2).coerceAtMost(6)
        val holder = SingleTourHolder(worldData.uuid, tour.uuid, isNew)
        val inventory = Bukkit.createInventory(holder, rows * 9, GuiHelper.inventoryTitle(Component.text(lang.getMessage(player, "gui.tour.menu.single_edit_title", mapOf("tour" to tour.name)))))
        holder.inv = inventory
        fillBase(inventory)
        val slots = mutableListOf<Int>()
        repeat(waypointRows) { row -> slots.addAll((1..7).map { (row + 1) * 9 + it }) }
        tour.waypoints.take(28).forEachIndexed { index, waypoint ->
            inventory.setItem(slots[index], createWaypointItem(player, waypoint, lang.getMessage(player, "gui.tour.menu.remove_waypoint_action")))
        }
        if (tour.waypoints.size < 28) inventory.setItem(slots[tour.waypoints.size], createLoreItem(Material.YELLOW_STAINED_GLASS_PANE, lang.getMessage(player, "gui.tour.menu.add_waypoint_button"), emptyList(), ItemTag.TYPE_GUI_TOUR_ADD_WAYPOINT))
        val bottom = inventory.size - 9
        inventory.setItem(bottom, createLoreItem(Material.REDSTONE, lang.getMessage(player, "gui.tour.menu.back"), emptyList(), ItemTag.TYPE_GUI_TOUR_BACK))
        val editTextLines = lang.getMessageList(player, "gui.tour.menu.edit_text.lore")
        inventory.setItem(bottom + 2, createLoreItem(Material.NAME_TAG, lang.getMessage(player, "gui.tour.menu.edit_text.display"), editTextLines, ItemTag.TYPE_GUI_TOUR_EDIT_TEXT, LoreStyle.FRAMED))
        inventory.setItem(bottom + 4, createLoreItem(Material.LIME_WOOL, lang.getMessage(player, "gui.tour.menu.save.display"), listOf(lang.getMessage(player, "gui.tour.menu.save.action")), ItemTag.TYPE_GUI_TOUR_SAVE))
        inventory.setItem(bottom + 6, createLoreItem(Material.LAVA_BUCKET, lang.getMessage(player, "gui.tour.menu.delete.display"), listOf(lang.getMessage(player, "gui.tour.menu.delete.action")), ItemTag.TYPE_GUI_TOUR_DELETE))
        player.openInventory(inventory)
    }

    fun openDeleteConfirm(player: Player, worldData: WorldData, tour: TourData, isNew: Boolean = false) {
        val lang = plugin.languageManager
        val holder = DeleteTourHolder(worldData.uuid, tour.uuid, isNew)
        val inventory = Bukkit.createInventory(holder, 45, GuiHelper.inventoryTitle(Component.text(lang.getMessage(player, "gui.tour.menu.delete_confirm.title"))))
        holder.inv = inventory
        fillBase(inventory)
        inventory.setItem(22, createLoreItem(Material.LAVA_BUCKET, lang.getMessage(player, "gui.tour.menu.delete_confirm.title"), listOf(lang.getMessage(player, "gui.tour.menu.delete_confirm.body_line1"), lang.getMessage(player, "gui.tour.menu.delete_confirm.body_line2"), lang.getMessage(player, "gui.tour.menu.delete_confirm.warning")), ItemTag.TYPE_GUI_INFO))
        inventory.setItem(20, createLoreItem(Material.LIME_WOOL, lang.getMessage(player, "gui.tour.menu.delete_confirm.confirm"), emptyList(), ItemTag.TYPE_GUI_CONFIRM))
        inventory.setItem(24, createLoreItem(Material.RED_WOOL, lang.getMessage(player, "gui.tour.menu.delete_confirm.cancel"), emptyList(), ItemTag.TYPE_GUI_CANCEL))
        player.openInventory(inventory)
    }

    private fun fillBase(inventory: Inventory) {
        GuiItemFactory.applyStandardFrame(inventory)
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
        meta.displayName(lang.getComponent(player, "gui.favorite.current_world.name"))
        val lines = lang.getMessageList(player, "gui.favorite.current_world.lore", mapOf(
                "world_line" to lang.getMessage(player, "gui.favorite.current_world.world_name", mapOf("world" to worldData.name)),
                "description" to formattedDesc,
                "owner_line" to ownerLine,
                "favorite_line" to favoriteLine,
                "visitor_line" to visitorLine,
                "tag_line" to tagLine
        ))
            .filter { line ->
                val stripped = line.replace(Regex("[§&][0-9A-FK-ORa-fk-or]"), "").trim()
                !(stripped.isNotEmpty() && stripped.all { it == '―' || it == '－' || it == '-' || it == '—' })
            }
            .filter { it.isNotBlank() }
        val lore = GuiItemFactory.menuLore(lines)
        meta.lore(lore)
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_TOUR_CURRENT_WORLD)
        return item
    }

    private fun createTourItem(player: Player, worldData: WorldData, tour: TourData, editing: Boolean): ItemStack {
        val lang = plugin.languageManager
        val current = plugin.tourSessionManager.get(player.uniqueId)?.let { it.tourUuid == tour.uuid && it.worldUuid == worldData.uuid } == true
        val countLine = if (tour.completedCount == 0) {
            lang.getMessage(player, "gui.tour.menu.tour_item.visitors_none")
        } else {
            lang.getMessage(player, "gui.tour.menu.tour_item.visitors_count", mapOf("count" to tour.completedCount.toString()))
        }
        val action = when {
            editing -> lang.getMessage(player, "gui.tour.menu.tour_item.action_edit")
            current -> lang.getMessage(player, "gui.tour.menu.tour_item.action_current")
            else -> lang.getMessage(player, "gui.tour.menu.tour_item.action_start")
        }
        val desc = normalizeDescription(tour.description)
        val item = createItem(tour.icon, tour.name, framedLore(desc, countLine, action), ItemTag.TYPE_GUI_TOUR_ITEM)
        ItemTag.setString(item, "tour_uuid", tour.uuid.toString())
        return item
    }

    private fun createEditTourItem(player: Player, worldData: WorldData, tour: TourData): ItemStack = createTourItem(player, worldData, tour, true)

    private fun createWaypointItem(player: Player, waypoint: TourWaypointData, actionLine: String): ItemStack {
        val lang = plugin.languageManager
        val coordLine = "§8${waypoint.blockX}, ${waypoint.blockY}, ${waypoint.blockZ}"
        val item = createItem(Material.OAK_BOAT, waypoint.name, framedLore(coordLine, actionLine), ItemTag.TYPE_GUI_TOUR_WAYPOINT_ITEM)
        ItemTag.setString(item, "tour_waypoint_uuid", waypoint.uuid.toString())
        return item
    }

    private enum class LoreStyle {
        RAW,
        AUTO_OPEN,
        FRAMED
    }

    private fun createLoreItem(
        material: Material,
        name: String,
        lore: List<String>,
        type: String,
        style: LoreStyle = LoreStyle.AUTO_OPEN
    ): ItemStack {
        val components = when (style) {
            LoreStyle.RAW -> legacyLore(lore)
            LoreStyle.AUTO_OPEN -> GuiItemFactory.menuLore(lore, closingSeparator = false)
            LoreStyle.FRAMED -> framedLore(lore)
        }
        return createItem(material, name, components, type)
    }

    private fun createItem(material: Material, name: String, lore: List<Component>, type: String): ItemStack {
        return GuiItemFactory.item(material, name, lore, type)
    }

    private fun framedLore(vararg lines: String): List<Component> = framedLore(lines.toList())

    private fun framedLore(lines: List<String>): List<Component> {
        if (lines.isEmpty()) return emptyList()
        return CCSystem.getAPI().getGuiElementService().autoLore(lines, true)
    }

    private fun legacyLore(vararg lines: String): List<Component> = legacyLore(lines.toList())

    private fun legacyLore(lines: List<String>): List<Component> {
        return CCSystem.getAPI().getLoreService().render(
            GuiLoreSpec.Rich(lines.map(GuiLoreLine::Raw), GuiLoreFrame.NONE)
        )
    }

    private fun decoration(material: Material): ItemStack {
        return GuiItemFactory.decoration(material)
    }

    fun openBindSignToTourMenu(player: Player, worldData: WorldData) {
        val lang = plugin.languageManager
        val unboundTours = worldData.tours.filter { it.startSignUuid == null }
        val rows = (((minOf(pageSlots.size, maxOf(7, unboundTours.size)) + 6) / 7) + 2).coerceIn(3, 6)
        val holder = BindSignHolder(worldData.uuid)
        val inventory = Bukkit.createInventory(holder, rows * 9, GuiHelper.inventoryTitle(Component.text(lang.getMessage(player, "gui.tour.bind_sign_title"))))
        holder.inv = inventory
        fillBase(inventory)
        unboundTours.sortedBy { it.createdAt }.take((rows - 2) * 7).forEachIndexed { index, tour ->
            val row = index / 7 + 1
            val col = index % 7 + 1
            val item = createItem(tour.icon, tour.name,
                framedLore(normalizeDescription(tour.description), lang.getMessage(player, "gui.tour.menu.tour_item.action_bind")),
                ItemTag.TYPE_GUI_TOUR_ITEM)
            ItemTag.setString(item, "tour_uuid", tour.uuid.toString())
            inventory.setItem(row * 9 + col, item)
        }
        player.openInventory(inventory)
    }

    private fun normalizeDescription(text: String): String = if (text.startsWith("§")) text else "§7$text"

    abstract class BaseHolder : InventoryHolder { lateinit var inv: Inventory; override fun getInventory(): Inventory = inv }
    class VisitorTourHolder(val worldUuid: java.util.UUID, val page: Int) : BaseHolder()
    class EditTourHolder(val worldUuid: java.util.UUID, val page: Int) : BaseHolder()
    class SingleTourHolder(val worldUuid: java.util.UUID, val tourUuid: java.util.UUID, val isNew: Boolean) : BaseHolder()
    class DeleteTourHolder(val worldUuid: java.util.UUID, val tourUuid: java.util.UUID, val isNew: Boolean) : BaseHolder()
    class StartSelectionHolder(val worldUuid: java.util.UUID, val signUuid: java.util.UUID) : BaseHolder()
    class StartConfirmHolder(val worldUuid: java.util.UUID, val tourUuid: java.util.UUID) : BaseHolder()
    class BindSignHolder(val worldUuid: java.util.UUID) : BaseHolder()
}
