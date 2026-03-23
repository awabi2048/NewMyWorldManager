package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.service.PendingDecisionManager
import me.awabi2048.myworldmanager.util.GuiItemFactory
import me.awabi2048.myworldmanager.util.ItemTag
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

class PendingInteractionGui(private val plugin: MyWorldManager) {

    private val itemsPerPage = 28

    fun open(
        player: Player,
        page: Int = 0,
        returnPage: Int = 0,
        showBackButton: Boolean = false,
        fromBedrockMenu: Boolean = false
    ) {
        val entries = plugin.pendingDecisionManager.getPendingEntries(player.uniqueId)
        val maxPage = if (entries.isEmpty()) 1 else ((entries.size - 1) / itemsPerPage) + 1
        val currentPage = page.coerceIn(0, maxPage - 1)
        val start = currentPage * itemsPerPage
        val pageEntries = entries.drop(start).take(itemsPerPage)

        val contentRows = if (pageEntries.isEmpty()) 1 else ((pageEntries.size - 1) / 7) + 1
        val rowCount = (contentRows + 2).coerceIn(3, 6)
        val holder = PendingInteractionHolder(currentPage, returnPage, showBackButton, fromBedrockMenu)
        val inventory = Bukkit.createInventory(holder, rowCount * 9, me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(plugin.languageManager.getComponent(player, "gui.pending_list.title")))
        holder.inv = inventory

        val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
        val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)
        val footerStart = (rowCount - 1) * 9

        for (slot in 0..8) inventory.setItem(slot, blackPane)
        for (slot in footerStart until footerStart + 9) inventory.setItem(slot, blackPane)
        for (row in 1 until rowCount - 1) {
            val rowStart = row * 9
            inventory.setItem(rowStart, grayPane)
            inventory.setItem(rowStart + 8, grayPane)
            for (col in 1..7) {
                inventory.setItem(rowStart + col, grayPane)
            }
        }

        pageEntries.forEachIndexed { index, entry ->
            val row = index / 7
            val col = index % 7
            val slot = (row + 1) * 9 + 1 + col
            inventory.setItem(slot, createEntryItem(player, entry))
        }

        if (pageEntries.isEmpty()) {
            inventory.setItem(22, createEmptyItem(player))
        }

        if (currentPage > 0) {
            inventory.setItem(
                footerStart + 1,
                me.awabi2048.myworldmanager.util.GuiHelper.createPrevPageItem(
                    plugin,
                    player,
                    "pending_list",
                    currentPage - 1
                )
            )
        }

        if (start + pageEntries.size < entries.size) {
            inventory.setItem(
                footerStart + 8,
                me.awabi2048.myworldmanager.util.GuiHelper.createNextPageItem(
                    plugin,
                    player,
                    "pending_list",
                    currentPage + 1
                )
            )
        }

        inventory.setItem(
            footerStart,
            me.awabi2048.myworldmanager.util.GuiHelper.createReturnItem(plugin, player, "pending_list")
        )

        player.openInventory(inventory)
    }

    fun handleInventoryClick(player: Player, event: InventoryClickEvent): Boolean {
        val holder = event.view.topInventory.holder as? PendingInteractionHolder ?: return false
        event.isCancelled = true

        if (event.clickedInventory != event.view.topInventory) {
            return true
        }

        val item = event.currentItem ?: return true
        val type = ItemTag.getType(item) ?: return true

        if (type == ItemTag.TYPE_GUI_NAV_PREV || type == ItemTag.TYPE_GUI_NAV_NEXT) {
            val targetPage = ItemTag.getTargetPage(item) ?: return true
            open(player, targetPage, holder.returnPage, holder.showBackButton, holder.fromBedrockMenu)
            return true
        }

        if (type == ItemTag.TYPE_GUI_RETURN) {
            if (holder.fromBedrockMenu) {
                plugin.menuEntryRouter.openPlayerWorld(player, holder.returnPage, holder.showBackButton)
            } else {
                plugin.playerWorldGui.open(player, holder.returnPage, holder.showBackButton)
            }
            return true
        }

        if (type != ItemTag.TYPE_GUI_PENDING_ENTRY) {
            return true
        }

        val idStr = ItemTag.getString(item, "pending_decision_id") ?: return true
        val decisionId = runCatching { UUID.fromString(idStr) }.getOrNull() ?: return true

        if (plugin.playerPlatformResolver.isBedrock(player)) {
            openBedrockDecisionForm(player, decisionId)
        } else {
            openJavaDecisionDialog(player, decisionId, holder.page)
        }
        return true
    }

    fun handleDialogResponse(player: Player, identifier: Key): Boolean {
        return false
    }

    fun openDecision(player: Player, decisionId: UUID, page: Int = 0, intendedAction: Boolean? = null) {
        if (plugin.playerPlatformResolver.isBedrock(player)) {
            openBedrockDecisionForm(player, decisionId, intendedAction)
        } else {
            openJavaDecisionDialog(player, decisionId, page, intendedAction)
        }
    }

    private fun openJavaDecisionDialog(player: Player, decisionId: UUID, page: Int, intendedAction: Boolean? = null) {
        val entry = plugin.pendingDecisionManager.getPendingEntry(player.uniqueId, decisionId)
            ?: run {
                player.sendMessage(plugin.languageManager.getMessage(player, "messages.myworld_pending_none"))
                return
            }

        val worldName = entry.worldUuid?.let { plugin.worldConfigRepository.findByUuid(it)?.name }
            ?: plugin.languageManager.getMessage(player, "general.unknown")
        val actorName = PlayerNameUtil.getNameOrDefault(entry.actorUuid, plugin.languageManager.getMessage(player, "general.unknown"))
        val typeLabel = typeLabel(player, entry.type)

        val title = plugin.languageManager.getComponent(player, "gui.pending_list.confirm.title")
        val center = PendingInteractionItemFactory.createItem(
            plugin = plugin,
            viewer = player,
            subjectUuid = entry.actorUuid,
            type = entry.type,
            worldName = worldName,
            createdAt = entry.createdAt,
            decisionId = decisionId,
            actionMode = PendingInteractionActionMode.REVIEW,
            itemTagType = ItemTag.TYPE_GUI_INFO
        )

        val confirmLabel = plugin.languageManager.getMessage(
            player,
            if (intendedAction == false) "gui.pending_list.confirm.deny" else "gui.pending_list.confirm.accept"
        )
        val cancelLabel = plugin.languageManager.getMessage(
            player,
            if (intendedAction == null) "gui.pending_list.confirm.deny" else "gui.common.cancel"
        )
        val confirmAction = intendedAction ?: true

        val confirmItem = GuiItemFactory.item(
            org.bukkit.Material.LIME_CONCRETE,
            confirmLabel,
            emptyList(),
            ItemTag.TYPE_GUI_CONFIRM
        )
        val cancelItem = GuiItemFactory.item(
            org.bukkit.Material.RED_CONCRETE,
            cancelLabel,
            emptyList(),
            ItemTag.TYPE_GUI_CANCEL
        )

        plugin.confirmationMenuGui.open(
            player = player,
            menuId = "pending_list",
            title = title,
            centerItem = center,
            confirmItem = confirmItem,
            cancelItem = cancelItem,
            onConfirm = { plugin.pendingDecisionManager.resolveById(player, decisionId, confirmAction) },
            onCancel = {
                if (intendedAction == null) {
                    plugin.pendingDecisionManager.resolveById(player, decisionId, false)
                }
            }
        )
    }

    private fun openBedrockDecisionForm(
        player: Player,
        decisionId: UUID,
        intendedAction: Boolean? = null
    ) {
        val entry = plugin.pendingDecisionManager.getPendingEntry(player.uniqueId, decisionId)
            ?: run {
                player.sendMessage(plugin.languageManager.getMessage(player, "messages.myworld_pending_none"))
                return
            }

        val worldName = entry.worldUuid?.let { plugin.worldConfigRepository.findByUuid(it)?.name }
            ?: plugin.languageManager.getMessage(player, "general.unknown")
        val actorName = PlayerNameUtil.getNameOrDefault(entry.actorUuid, plugin.languageManager.getMessage(player, "general.unknown"))
        val typeLabel = typeLabel(player, entry.type)

        val title = plugin.languageManager.getComponent(player, "gui.pending_list.confirm.title")
        val center = PendingInteractionItemFactory.createItem(
            plugin = plugin,
            viewer = player,
            subjectUuid = entry.actorUuid,
            type = entry.type,
            worldName = worldName,
            createdAt = entry.createdAt,
            decisionId = decisionId,
            actionMode = PendingInteractionActionMode.REVIEW,
            itemTagType = ItemTag.TYPE_GUI_INFO
        )

        val confirmLabel = plugin.languageManager.getMessage(
            player,
            if (intendedAction == false) "gui.pending_list.confirm.deny" else "gui.pending_list.confirm.accept"
        )
        val cancelLabel = plugin.languageManager.getMessage(
            player,
            if (intendedAction == null) "gui.pending_list.confirm.deny" else "gui.common.cancel"
        )
        val confirmAction = intendedAction ?: true

        val confirmItem = GuiItemFactory.item(
            org.bukkit.Material.LIME_CONCRETE,
            confirmLabel,
            emptyList(),
            ItemTag.TYPE_GUI_CONFIRM
        )
        val cancelItem = GuiItemFactory.item(
            org.bukkit.Material.RED_CONCRETE,
            cancelLabel,
            emptyList(),
            ItemTag.TYPE_GUI_CANCEL
        )

        plugin.confirmationMenuGui.open(
            player = player,
            menuId = "pending_list",
            title = title,
            centerItem = center,
            confirmItem = confirmItem,
            cancelItem = cancelItem,
            onConfirm = { plugin.pendingDecisionManager.resolveById(player, decisionId, confirmAction) },
            onCancel = {
                if (intendedAction == null) {
                    plugin.pendingDecisionManager.resolveById(player, decisionId, false)
                }
            }
        )
    }

    private fun createEntryItem(player: Player, entry: PendingDecisionManager.PendingEntryView): ItemStack {
        val worldName = entry.worldUuid?.let { plugin.worldConfigRepository.findByUuid(it)?.name }
            ?: plugin.languageManager.getMessage(player, "general.unknown")
        return PendingInteractionItemFactory.createItem(
            plugin = plugin,
            viewer = player,
            subjectUuid = entry.actorUuid,
            type = entry.type,
            worldName = worldName,
            createdAt = entry.createdAt,
            decisionId = entry.id,
            actionMode = PendingInteractionActionMode.REVIEW,
            itemTagType = ItemTag.TYPE_GUI_PENDING_ENTRY
        )
    }

    private fun createEmptyItem(player: Player): ItemStack {
        val item = ItemStack(Material.BARRIER)
        val meta = item.itemMeta ?: return item
        meta.displayName(plugin.languageManager.getComponent(player, "gui.pending_list.empty.name"))
        meta.lore(plugin.languageManager.getComponentList(player, "gui.pending_list.empty.lore"))
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_INFO)
        return item
    }

    private fun typeLabel(player: Player, type: PendingDecisionManager.PendingType): String {
        return when (type) {
            PendingDecisionManager.PendingType.WORLD_INVITE -> plugin.languageManager.getMessage(player, "gui.pending_list.type.world_invite")
            PendingDecisionManager.PendingType.MEMBER_INVITE -> plugin.languageManager.getMessage(player, "gui.pending_list.type.member_invite")
            PendingDecisionManager.PendingType.MEMBER_REQUEST -> plugin.languageManager.getMessage(player, "gui.pending_list.type.member_request")
            PendingDecisionManager.PendingType.MEET_REQUEST -> plugin.languageManager.getMessage(player, "gui.pending_list.type.meet_request")
        }
    }

    private fun formatDateTime(timestamp: Long): String {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(timestamp))
    }

    private fun createDecorationItem(material: Material): ItemStack {
        return GuiItemFactory.decoration(material)
    }

    class PendingInteractionHolder(
        val page: Int,
        val returnPage: Int,
        val showBackButton: Boolean,
        val fromBedrockMenu: Boolean
    ) : InventoryHolder {
        lateinit var inv: Inventory
        override fun getInventory(): Inventory = inv
    }
}
