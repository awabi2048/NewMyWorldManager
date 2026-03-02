package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.PendingInteractionType
import me.awabi2048.myworldmanager.util.ItemTag
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
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
        val entries = plugin.pendingDecisionManager.getPersistentPending(player.uniqueId)
        val maxPage = if (entries.isEmpty()) 1 else ((entries.size - 1) / itemsPerPage) + 1
        val currentPage = page.coerceIn(0, maxPage - 1)
        val start = currentPage * itemsPerPage
        val pageEntries = entries.drop(start).take(itemsPerPage)

        val holder = PendingInteractionHolder(currentPage, returnPage, showBackButton, fromBedrockMenu)
        val inventory = Bukkit.createInventory(holder, 54, plugin.languageManager.getComponent(player, "gui.pending_list.title"))
        holder.inv = inventory

        val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
        val grayPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)

        for (slot in 0..8) inventory.setItem(slot, blackPane)
        for (slot in 45..53) inventory.setItem(slot, blackPane)
        for (row in 1..4) {
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
                46,
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
                52,
                me.awabi2048.myworldmanager.util.GuiHelper.createNextPageItem(
                    plugin,
                    player,
                    "pending_list",
                    currentPage + 1
                )
            )
        }

        inventory.setItem(
            49,
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
            openBedrockDecisionForm(player, decisionId, holder)
        } else {
            openJavaDecisionDialog(player, decisionId, holder.page)
        }
        return true
    }

    fun handleDialogResponse(player: Player, identifier: Key): Boolean {
        val value = identifier.value()
        if (!value.startsWith("mwm:pending/")) {
            return false
        }

        val payload = value.removePrefix("mwm:pending/")
        val parts = payload.split("/")
        if (parts.size < 3) {
            return true
        }

        val action = parts[0]
        val id = runCatching { UUID.fromString(parts[1]) }.getOrNull() ?: return true
        val page = parts[2].toIntOrNull() ?: 0
        val accept = action == "accept"
        plugin.pendingDecisionManager.resolvePersistentById(player, id, accept)
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (player.isOnline) {
                open(player, page)
            }
        })
        return true
    }

    private fun openJavaDecisionDialog(player: Player, decisionId: UUID, page: Int) {
        val entry = plugin.pendingDecisionManager.getPersistentPending(player.uniqueId).firstOrNull { it.id == decisionId }
            ?: run {
                player.sendMessage(plugin.languageManager.getMessage(player, "messages.myworld_pending_none"))
                return
            }

        val worldName = plugin.worldConfigRepository.findByUuid(entry.worldUuid)?.name
            ?: plugin.languageManager.getMessage(player, "general.unknown")
        val actorName = PlayerNameUtil.getNameOrDefault(entry.actorUuid, plugin.languageManager.getMessage(player, "general.unknown"))
        val typeLabel = typeLabel(player, entry.type)

        val title = Component.text(plugin.languageManager.getMessage(player, "gui.pending_list.confirm.title"))
        val body = listOf(
            Component.text(
                plugin.languageManager.getMessage(
                    player,
                    "gui.pending_list.confirm.body",
                    mapOf(
                        "type" to typeLabel,
                        "player" to actorName,
                        "world" to worldName,
                        "datetime" to formatDateTime(entry.createdAt)
                    )
                )
            )
        )

        DialogConfirmManager.showSimpleConfirmationDialog(
            player,
            plugin,
            title,
            body,
            "mwm:pending/accept/$decisionId/$page",
            "mwm:pending/deny/$decisionId/$page",
            plugin.languageManager.getMessage(player, "gui.pending_list.confirm.accept"),
            plugin.languageManager.getMessage(player, "gui.pending_list.confirm.deny")
        )
    }

    private fun openBedrockDecisionForm(player: Player, decisionId: UUID, holder: PendingInteractionHolder) {
        val entry = plugin.pendingDecisionManager.getPersistentPending(player.uniqueId).firstOrNull { it.id == decisionId }
            ?: run {
                player.sendMessage(plugin.languageManager.getMessage(player, "messages.myworld_pending_none"))
                return
            }

        val worldName = plugin.worldConfigRepository.findByUuid(entry.worldUuid)?.name
            ?: plugin.languageManager.getMessage(player, "general.unknown")
        val actorName = PlayerNameUtil.getNameOrDefault(entry.actorUuid, plugin.languageManager.getMessage(player, "general.unknown"))
        val typeLabel = typeLabel(player, entry.type)

        val opened = plugin.floodgateFormBridge.sendSimpleForm(
            player = player,
            title = plugin.languageManager.getMessage(player, "gui.pending_list.confirm.title"),
            content = plugin.languageManager.getMessage(
                player,
                "gui.pending_list.confirm.body",
                mapOf(
                    "type" to typeLabel,
                    "player" to actorName,
                    "world" to worldName,
                    "datetime" to formatDateTime(entry.createdAt)
                )
            ),
            buttons = listOf(
                plugin.languageManager.getMessage(player, "gui.pending_list.confirm.accept"),
                plugin.languageManager.getMessage(player, "gui.pending_list.confirm.deny"),
                plugin.languageManager.getMessage(player, "gui.common.cancel")
            ),
            onSelect = { index ->
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    when (index) {
                        0 -> plugin.pendingDecisionManager.resolvePersistentById(player, decisionId, true)
                        1 -> plugin.pendingDecisionManager.resolvePersistentById(player, decisionId, false)
                    }
                    if (player.isOnline) {
                        open(player, holder.page, holder.returnPage, holder.showBackButton, holder.fromBedrockMenu)
                    }
                })
            },
            onClosed = {
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (player.isOnline) {
                        open(player, holder.page, holder.returnPage, holder.showBackButton, holder.fromBedrockMenu)
                    }
                })
            }
        )

        if (!opened) {
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.operation_cancelled"))
        }
    }

    private fun createEntryItem(player: Player, entry: me.awabi2048.myworldmanager.service.PendingDecisionManager.PersistentPendingView): ItemStack {
        val material = when (entry.type) {
            PendingInteractionType.MEMBER_INVITE -> Material.PAPER
            PendingInteractionType.MEMBER_REQUEST -> Material.WRITABLE_BOOK
        }
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item

        val worldName = plugin.worldConfigRepository.findByUuid(entry.worldUuid)?.name
            ?: plugin.languageManager.getMessage(player, "general.unknown")
        val actorName = PlayerNameUtil.getNameOrDefault(entry.actorUuid, plugin.languageManager.getMessage(player, "general.unknown"))

        meta.displayName(
            plugin.languageManager.getComponent(
                player,
                "gui.pending_list.item.name",
                mapOf("type" to typeLabel(player, entry.type))
            )
        )
        meta.lore(
            plugin.languageManager.getComponentList(
                player,
                "gui.pending_list.item.lore",
                mapOf(
                    "player" to actorName,
                    "world" to worldName,
                    "datetime" to formatDateTime(entry.createdAt)
                )
            )
        )

        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_PENDING_ENTRY)
        ItemTag.setString(item, "pending_decision_id", entry.id.toString())
        return item
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

    private fun createDecorationItem(material: Material): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.displayName(Component.empty())
        meta.isHideTooltip = true
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_DECORATION)
        return item
    }

    private fun typeLabel(player: Player, type: PendingInteractionType): String {
        return when (type) {
            PendingInteractionType.MEMBER_INVITE -> plugin.languageManager.getMessage(player, "gui.pending_list.type.member_invite")
            PendingInteractionType.MEMBER_REQUEST -> plugin.languageManager.getMessage(player, "gui.pending_list.type.member_request")
        }
    }

    private fun formatDateTime(timestamp: Long): String {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(timestamp))
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
