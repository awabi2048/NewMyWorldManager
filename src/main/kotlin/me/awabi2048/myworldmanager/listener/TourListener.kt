package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.gui.DialogConfirmManager
import me.awabi2048.myworldmanager.gui.TourDialogManager
import me.awabi2048.myworldmanager.gui.TourGui
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.block.Sign
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import java.util.UUID

class TourListener(private val plugin: MyWorldManager) : Listener {
    @EventHandler(ignoreCancelled = true)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = event.item
        if (event.action == Action.RIGHT_CLICK_BLOCK && event.hand == EquipmentSlot.HAND && item != null && ItemTag.isType(item, ItemTag.TYPE_TOUR_SIGN)) {
            event.isCancelled = true
            val block = event.clickedBlock ?: return
            val blockFace = event.blockFace
            if (blockFace == BlockFace.UP || blockFace == BlockFace.NORTH || blockFace == BlockFace.SOUTH || blockFace == BlockFace.EAST || blockFace == BlockFace.WEST) {
                TourDialogManager.startPlacement(player, plugin, block, blockFace, event.hand ?: EquipmentSlot.HAND)
            }
            return
        }
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        val signBlock = event.clickedBlock ?: return
        if (signBlock.type != Material.PALE_OAK_SIGN && signBlock.type != Material.PALE_OAK_WALL_SIGN) return
        if (signBlock.state !is Sign) return
        val worldData = plugin.worldConfigRepository.findByWorldName(player.world.name) ?: return
        val signData = plugin.tourManager.findSignFromBlock(worldData, signBlock) ?: return
        event.isCancelled = true
        if (player.isSneaking && plugin.tourManager.canManage(worldData, player.uniqueId)) {
            plugin.soundManager.playGlobalClickSound(player)
            TourDialogManager.startSignTextEdit(player, plugin, worldData.uuid, signData.uuid, signData.title, signData.description)
            return
        }
        val startTours = plugin.tourManager.findToursBySign(worldData, signData.uuid).filter { it.signUuids.firstOrNull() == signData.uuid && it.signUuids.size >= 2 }
        when {
            startTours.size > 1 && plugin.tourSessionManager.get(player.uniqueId) == null -> {
                plugin.soundManager.playGlobalClickSound(player)
                plugin.tourGui.openStartSelectionMenu(player, worldData, signData.uuid)
            }
            startTours.size == 1 && plugin.tourSessionManager.get(player.uniqueId) == null -> {
                plugin.soundManager.playGlobalClickSound(player)
                plugin.tourManager.startTour(player, worldData, startTours.first())
            }
            else -> plugin.tourManager.advanceBySign(player, worldData, signData.uuid)
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val top = event.view.topInventory.holder
        if (event.clickedInventory != event.view.topInventory) {
            if (plugin.tourSessionManager.getEdit(player.uniqueId)?.awaitingIconPick == true) {
                val picked = event.currentItem?.type ?: return
                if (picked.isAir) return
                event.isCancelled = true
                val session = plugin.tourSessionManager.getEdit(player.uniqueId) ?: return
                session.draft.icon = picked
                session.awaitingIconPick = false
                plugin.soundManager.playGlobalClickSound(player)
                val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid) ?: return
                plugin.tourGui.openSingleEditMenu(player, worldData, session.draft, session.isNew)
            }
            return
        }
        if (top !is TourGui.BaseHolder) return
        event.isCancelled = true
        val item = event.currentItem ?: return
        val type = ItemTag.getType(item) ?: return
        if (type == ItemTag.TYPE_GUI_DECORATION) return
        val worldUuid = when (top) {
            is TourGui.VisitorTourHolder -> top.worldUuid
            is TourGui.EditTourHolder -> top.worldUuid
            is TourGui.SingleTourHolder -> top.worldUuid
            is TourGui.AddSignHolder -> top.worldUuid
            is TourGui.StartSelectionHolder -> top.worldUuid
            else -> return
        }
        val worldData = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return
        when (top) {
            is TourGui.VisitorTourHolder, is TourGui.StartSelectionHolder -> handleVisitorClick(player, worldData, type, item, top)
            is TourGui.EditTourHolder -> handleEditMenuClick(player, worldData, type, item)
            is TourGui.SingleTourHolder -> handleSingleEditClick(player, worldData, type, item, top, event.click)
            is TourGui.AddSignHolder -> handleAddSignClick(player, worldData, type, item, top)
        }
    }

    private fun handleVisitorClick(player: Player, worldData: me.awabi2048.myworldmanager.model.WorldData, type: String, item: org.bukkit.inventory.ItemStack, holder: Any) {
        if (type == ItemTag.TYPE_GUI_NAV_NEXT || type == ItemTag.TYPE_GUI_NAV_PREV) {
            plugin.soundManager.playClickSound(player, item, "tour")
            val page = ItemTag.getTargetPage(item) ?: 0
            if (holder is TourGui.StartSelectionHolder) plugin.tourGui.openStartSelectionMenu(player, worldData, holder.signUuid) else plugin.tourGui.openVisitorMenu(player, worldData, page)
            return
        }
        if (type != ItemTag.TYPE_GUI_TOUR_ITEM) return
        plugin.soundManager.playClickSound(player, item, "tour")
        val tourUuid = ItemTag.getString(item, "tour_uuid")?.let(UUID::fromString) ?: return
        plugin.tourManager.getTour(worldData, tourUuid)?.let { plugin.tourManager.startTour(player, worldData, it) }
        player.closeInventory()
    }

    private fun handleEditMenuClick(player: Player, worldData: me.awabi2048.myworldmanager.model.WorldData, type: String, item: org.bukkit.inventory.ItemStack) {
        when (type) {
            ItemTag.TYPE_GUI_TOUR_BACK -> {
                plugin.soundManager.playClickSound(player, item, "tour")
                plugin.menuEntryRouter.openWorldSettings(player, worldData, false)
            }
            ItemTag.TYPE_GUI_TOUR_CREATE -> {
                plugin.soundManager.playClickSound(player, item, "tour")
                player.playSound(player.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.4f)
                val session = plugin.tourManager.createDraftTour(player, worldData)
                plugin.tourGui.openSingleEditMenu(player, worldData, session.draft, true)
            }
            ItemTag.TYPE_GUI_TOUR_ITEM -> {
                plugin.soundManager.playClickSound(player, item, "tour")
                val tourUuid = ItemTag.getString(item, "tour_uuid")?.let(UUID::fromString) ?: return
                plugin.tourManager.getTour(worldData, tourUuid)?.let {
                    val session = plugin.tourManager.openEditSession(player, worldData, it)
                    plugin.tourGui.openSingleEditMenu(player, worldData, session.draft, false)
                }
            }
        }
    }

    private fun handleSingleEditClick(player: Player, worldData: me.awabi2048.myworldmanager.model.WorldData, type: String, item: org.bukkit.inventory.ItemStack, holder: TourGui.SingleTourHolder, click: ClickType) {
        val session = plugin.tourSessionManager.getEdit(player.uniqueId) ?: return
        when (type) {
            ItemTag.TYPE_GUI_TOUR_BACK -> {
                plugin.soundManager.playClickSound(player, item, "tour")
                if (holder.isNew) {
                    DialogConfirmManager.showConfirmationByPreference(
                        player = player,
                        plugin = plugin,
                        title = Component.text("未保存ツアー"),
                        bodyLines = listOf(Component.text("§cこの新規ツアーは保存されていません。"), Component.text("§7保存せずに戻りますか？")),
                        confirmActionId = "mwm:tour/discard_new",
                        cancelActionId = "mwm:tour/discard_new_cancel",
                        onBedrockConfirm = {
                            plugin.tourSessionManager.clearEdit(player.uniqueId)
                            plugin.tourGui.openEditMenu(player, worldData)
                        },
                        onBedrockCancel = {
                            plugin.tourGui.openSingleEditMenu(player, worldData, session.draft, true)
                        },
                        onGuiFallback = {
                            plugin.tourGui.openSingleEditMenu(player, worldData, session.draft, true)
                        }
                    )
                }
                else {
                    if (!canSaveTour(player, session.draft)) return
                    plugin.tourManager.saveEditSession(player, worldData)
                    plugin.tourGui.openEditMenu(player, worldData)
                }
            }
            ItemTag.TYPE_GUI_TOUR_EDIT_TEXT -> {
                plugin.soundManager.playClickSound(player, item, "tour")
                if (click.isRightClick) {
                    session.awaitingIconPick = true
                    player.sendMessage(plugin.languageManager.getMessage(player, "messages.icon_prompt"))
                } else {
                    TourDialogManager.startTourTextEdit(player, plugin, worldData.uuid, session.draft.uuid, session.draft.name, session.draft.description)
                }
            }
            ItemTag.TYPE_GUI_TOUR_SAVE -> {
                plugin.soundManager.playClickSound(player, item, "tour")
                if (!canSaveTour(player, session.draft)) return
                plugin.tourManager.saveEditSession(player, worldData)
                plugin.tourGui.openEditMenu(player, worldData)
            }
            ItemTag.TYPE_GUI_TOUR_DELETE -> {
                plugin.soundManager.playClickSound(player, item, "tour")
                DialogConfirmManager.showConfirmationByPreference(
                    player = player,
                    plugin = plugin,
                    title = Component.text("ツアー削除確認"),
                    bodyLines = listOf(Component.text("§cこのツアーを削除します。"), Component.text("§7この操作は元に戻せません。")),
                    confirmActionId = "mwm:tour/delete_confirm",
                    cancelActionId = "mwm:tour/delete_cancel",
                    onBedrockConfirm = {
                        if (!holder.isNew) {
                            plugin.tourManager.deleteTour(worldData, session.originalTourUuid ?: return@showConfirmationByPreference)
                        }
                        plugin.tourSessionManager.clearEdit(player.uniqueId)
                        plugin.tourGui.openEditMenu(player, worldData)
                    },
                    onBedrockCancel = {
                        plugin.tourGui.openSingleEditMenu(player, worldData, session.draft, holder.isNew)
                    },
                    onGuiFallback = {
                        plugin.tourGui.openSingleEditMenu(player, worldData, session.draft, holder.isNew)
                    }
                )
            }
            ItemTag.TYPE_GUI_TOUR_ADD_SIGN -> {
                plugin.soundManager.playClickSound(player, item, "tour")
                plugin.tourGui.openAddSignMenu(player, worldData, session.draft, holder.isNew)
            }
            ItemTag.TYPE_GUI_TOUR_SIGN_ITEM -> {
                plugin.soundManager.playClickSound(player, item, "tour")
                val signUuid = ItemTag.getString(item, "tour_sign_uuid")?.let(UUID::fromString) ?: return
                session.draft.signUuids.remove(signUuid)
                plugin.tourGui.openSingleEditMenu(player, worldData, session.draft, holder.isNew)
            }
        }
    }

    private fun canSaveTour(player: Player, tour: me.awabi2048.myworldmanager.model.TourData): Boolean {
        if (tour.signUuids.size >= 2) return true
        player.sendMessage(plugin.languageManager.getMessage(player, "error.tour.not_enough_signs"))
        return false
    }

    private fun handleAddSignClick(player: Player, worldData: me.awabi2048.myworldmanager.model.WorldData, type: String, item: org.bukkit.inventory.ItemStack, holder: TourGui.AddSignHolder) {
        val session = plugin.tourSessionManager.getEdit(player.uniqueId) ?: return
        when (type) {
            ItemTag.TYPE_GUI_TOUR_BACK -> {
                plugin.soundManager.playClickSound(player, item, "tour")
                plugin.tourGui.openSingleEditMenu(player, worldData, session.draft, holder.isNew)
            }
            ItemTag.TYPE_GUI_TOUR_SIGN_ITEM -> {
                plugin.soundManager.playClickSound(player, item, "tour")
                val signUuid = ItemTag.getString(item, "tour_sign_uuid")?.let(UUID::fromString) ?: return
                if (session.draft.signUuids.size < 28) session.draft.signUuids.add(signUuid)
                plugin.tourGui.openSingleEditMenu(player, worldData, session.draft, holder.isNew)
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val worldData = plugin.worldConfigRepository.findByWorldName(event.block.world.name) ?: return
        val signData = plugin.tourManager.findSignFromBlock(worldData, event.block) ?: return
        event.isDropItems = false
        plugin.tourManager.breakTourSign(worldData, signData.uuid, event.block.location)
    }

    @EventHandler
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        plugin.tourSessionManager.getEdit(event.player.uniqueId)?.awaitingIconPick = false
        if (plugin.tourSessionManager.get(event.player.uniqueId) != null) {
            plugin.tourManager.stopTour(event.player, silent = true)
            event.player.sendMessage(plugin.languageManager.getMessage(event.player, "messages.tour.stopped_world_change"))
        }
    }
}
