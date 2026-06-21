package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.gui.DialogConfirmManager
import me.awabi2048.myworldmanager.gui.TourDialogManager
import me.awabi2048.myworldmanager.gui.TourGui
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.BlockFace
import org.bukkit.block.Sign
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import me.awabi2048.myworldmanager.util.cancelWithDebug
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class TourListener(private val plugin: MyWorldManager) : Listener {
    private val waypointPreviewTasks = ConcurrentHashMap<UUID, BukkitTask>()

    @EventHandler(ignoreCancelled = true)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = event.item

        val editSession = plugin.tourSessionManager.getEdit(player.uniqueId)
        if (editSession != null && editSession.awaitingWaypointPick && event.action == Action.RIGHT_CLICK_BLOCK && event.hand == EquipmentSlot.HAND) {
            event.isCancelled = true
            val targetBlock = event.clickedBlock ?: return
            stopWaypointPreview(player)
            editSession.awaitingWaypointPick = false
            plugin.soundManager.playGlobalClickSound(player)
            plugin.tourManager.addWaypoint(editSession, targetBlock.location)
            val worldData = plugin.worldConfigRepository.findByUuid(editSession.worldUuid) ?: return
            plugin.tourGui.openSingleEditMenu(player, worldData, editSession.draft, editSession.isNew)
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

        val startTours = plugin.tourManager.findToursBySign(worldData, signData.uuid)

        val activeSession = plugin.tourSessionManager.get(player.uniqueId)
        if (activeSession != null) {
            plugin.tourManager.advanceByWaypoint(player, worldData)
            return
        }

        when {
            startTours.size > 1 -> {
                plugin.soundManager.playGlobalClickSound(player)
                plugin.tourGui.openStartSelectionMenu(player, worldData, signData.uuid)
            }
            startTours.size == 1 -> {
                plugin.soundManager.playGlobalClickSound(player)
                val tour = startTours.first()
                plugin.tourGui.openStartConfirm(player, worldData, tour)
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    fun onTourSignPlace(event: BlockPlaceEvent) {
        val item = event.itemInHand
        if (!ItemTag.isType(item, ItemTag.TYPE_TOUR_SIGN)) return
        val player = event.player
        val blockFace = event.blockAgainst.getFace(event.blockPlaced) ?: return
        if (blockFace != BlockFace.UP && blockFace != BlockFace.NORTH && blockFace != BlockFace.SOUTH && blockFace != BlockFace.EAST && blockFace != BlockFace.WEST) {
            return
        }
        event.isCancelled = true
        TourDialogManager.startPlacement(player, plugin, event.blockPlaced, blockFace, event.hand)
    }

    @EventHandler(ignoreCancelled = false)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val top = event.view.topInventory.holder
        if (event.clickedInventory != event.view.topInventory) {
            if (plugin.tourSessionManager.getEdit(player.uniqueId)?.awaitingIconPick == true) {
                val picked = event.currentItem?.type ?: return
                if (picked.isAir) return
                event.cancelWithDebug("TourListener.onInventoryClick: tour icon pick click", force = true)
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
        event.cancelWithDebug("TourListener.onInventoryClick: tour GUI click")
        val item = event.currentItem ?: return
        val type = ItemTag.getType(item) ?: return
        if (type == ItemTag.TYPE_GUI_DECORATION) return
        val worldUuid = when (top) {
            is TourGui.VisitorTourHolder -> top.worldUuid
            is TourGui.EditTourHolder -> top.worldUuid
            is TourGui.SingleTourHolder -> top.worldUuid
            is TourGui.DeleteTourHolder -> top.worldUuid
            is TourGui.StartSelectionHolder -> top.worldUuid
            is TourGui.StartConfirmHolder -> top.worldUuid
            is TourGui.BindSignHolder -> top.worldUuid
            else -> return
        }
        val worldData = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return
        when (top) {
            is TourGui.VisitorTourHolder, is TourGui.StartSelectionHolder -> handleVisitorClick(player, worldData, type, item, top)
            is TourGui.StartConfirmHolder -> handleStartConfirmClick(player, worldData, type, item, top)
            is TourGui.EditTourHolder -> handleEditMenuClick(player, worldData, type, item)
            is TourGui.SingleTourHolder -> handleSingleEditClick(player, worldData, type, item, top, event.click)
            is TourGui.DeleteTourHolder -> handleDeleteConfirmClick(player, worldData, type, item, top)
            is TourGui.BindSignHolder -> handleBindSignClick(player, worldData, type, item, top)
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
        plugin.tourManager.getTour(worldData, tourUuid)?.let {
            plugin.tourGui.openStartConfirm(player, worldData, it)
        }
    }

    private fun handleStartConfirmClick(player: Player, worldData: me.awabi2048.myworldmanager.model.WorldData, type: String, item: org.bukkit.inventory.ItemStack, holder: TourGui.StartConfirmHolder) {
        if (type != ItemTag.TYPE_GUI_CONFIRM) return
        plugin.soundManager.playClickSound(player, item, "confirm")
        val tour = plugin.tourManager.getTour(worldData, holder.tourUuid) ?: return
        when (plugin.tourManager.startTour(player, worldData, tour)) {
            me.awabi2048.myworldmanager.service.TourManager.StartTourResult.STARTED -> player.closeInventory()
            me.awabi2048.myworldmanager.service.TourManager.StartTourResult.WORLD_MEMBER ->
                player.sendMessage(plugin.languageManager.getMessage(player, "messages.invite_already_member"))
            me.awabi2048.myworldmanager.service.TourManager.StartTourResult.INVALID_TOUR ->
                player.sendMessage(plugin.languageManager.getMessage(player, "messages.tour.none_available"))
            me.awabi2048.myworldmanager.service.TourManager.StartTourResult.WRONG_WORLD ->
                player.sendMessage(plugin.languageManager.getMessage(player, "messages.no_in_myworld"))
        }
    }

    private fun handleEditMenuClick(player: Player, worldData: me.awabi2048.myworldmanager.model.WorldData, type: String, item: org.bukkit.inventory.ItemStack) {
        when (type) {
            ItemTag.TYPE_GUI_TOUR_BACK -> {
                plugin.soundManager.playClickSound(player, item, "tour")
                if (!plugin.menuRouteHistory.openPrevious(player)) {
                    plugin.menuEntryRouter.openWorldSettings(player, worldData, false)
                }
            }
            ItemTag.TYPE_GUI_TOUR_CREATE -> {
                plugin.soundManager.playClickSound(player, item, "tour")
                player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.4f)
                TourDialogManager.startTourCreation(player, plugin, worldData.uuid)
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
                    val lang = plugin.languageManager
                    DialogConfirmManager.showConfirmationByPreference(
                        player = player,
                        plugin = plugin,
                        title = Component.text(lang.getMessage(player, "gui.tour.menu.discard_new.title")),
                        bodyLines = listOf(
                            Component.text(lang.getMessage(player, "gui.tour.menu.discard_new.body_line1")),
                            Component.text(lang.getMessage(player, "gui.tour.menu.discard_new.body_line2"))
                        ),
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
                val lang = plugin.languageManager
                DialogConfirmManager.showConfirmationByPreference(
                    player = player,
                    plugin = plugin,
                    title = Component.text(lang.getMessage(player, "gui.tour.menu.delete_confirm.title")),
                    bodyLines = listOf(
                        Component.text(lang.getMessage(player, "gui.tour.menu.delete_confirm.body_line1")),
                        Component.text(lang.getMessage(player, "gui.tour.menu.delete_confirm.body_line2"))
                    ),
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
                        plugin.tourGui.openDeleteConfirm(player, worldData, session.draft, holder.isNew)
                    }
                )
            }
            ItemTag.TYPE_GUI_TOUR_ADD_WAYPOINT -> {
                plugin.soundManager.playClickSound(player, item, "tour")
                if (session.draft.waypoints.size >= 28) {
                    player.sendMessage(plugin.languageManager.getMessage(player, "error.tour.waypoint_limit"))
                    return
                }
                session.awaitingWaypointPick = true
                player.closeInventory()
                player.sendMessage(plugin.languageManager.getMessage(player, "messages.tour.waypoint_pick"))
                startWaypointPreview(player)
            }
            ItemTag.TYPE_GUI_TOUR_WAYPOINT_ITEM -> {
                plugin.soundManager.playClickSound(player, item, "tour")
                val waypointUuid = ItemTag.getString(item, "tour_waypoint_uuid")?.let(UUID::fromString) ?: return
                session.draft.waypoints.removeIf { it.uuid == waypointUuid }
                plugin.tourGui.openSingleEditMenu(player, worldData, session.draft, holder.isNew)
            }
        }
    }

    private fun handleDeleteConfirmClick(player: Player, worldData: me.awabi2048.myworldmanager.model.WorldData, type: String, item: org.bukkit.inventory.ItemStack, holder: TourGui.DeleteTourHolder) {
        val session = plugin.tourSessionManager.getEdit(player.uniqueId) ?: return
        when (type) {
            ItemTag.TYPE_GUI_CONFIRM -> {
                plugin.soundManager.playClickSound(player, item, "tour")
                if (!holder.isNew) {
                    val tourUuid = session.originalTourUuid ?: holder.tourUuid
                    plugin.tourManager.deleteTour(worldData, tourUuid)
                }
                plugin.tourSessionManager.clearEdit(player.uniqueId)
                plugin.tourGui.openEditMenu(player, worldData)
            }
            ItemTag.TYPE_GUI_CANCEL -> {
                plugin.soundManager.playClickSound(player, item, "tour")
                plugin.tourGui.openSingleEditMenu(player, worldData, session.draft, holder.isNew)
            }
        }
    }

    private fun handleBindSignClick(player: Player, worldData: me.awabi2048.myworldmanager.model.WorldData, type: String, item: org.bukkit.inventory.ItemStack, holder: TourGui.BindSignHolder) {
        if (type != ItemTag.TYPE_GUI_TOUR_ITEM) return
        plugin.soundManager.playClickSound(player, item, "tour")
        val tourUuid = ItemTag.getString(item, "tour_uuid")?.let(UUID::fromString) ?: return
        val tour = plugin.tourManager.getTour(worldData, tourUuid) ?: return
        val placement = TourDialogManager.consumePlacement(player.uniqueId) ?: return
        val placementItem = if (placement.hand == EquipmentSlot.HAND) player.inventory.itemInMainHand else player.inventory.itemInOffHand
        if (!ItemTag.isType(placementItem, ItemTag.TYPE_TOUR_SIGN) || placementItem.amount <= 0) return
        val signBlock = player.world.getBlockAt(placement.x, placement.y, placement.z)
        val blockFace = runCatching { BlockFace.valueOf(placement.blockFace) }.getOrDefault(BlockFace.UP)
        val signData = plugin.tourManager.createTourSignAt(worldData, player, signBlock, blockFace, "", "")
        placementItem.amount -= 1
        tour.startSignUuid = signData.uuid
        plugin.worldConfigRepository.save(worldData)
        plugin.tourManager.updateTourSign(
            signData,
            worldData
        )
        player.closeInventory()
        player.sendMessage(plugin.languageManager.getMessage(player, "messages.tour_sign.bound"))
    }

    private fun canSaveTour(player: Player, tour: me.awabi2048.myworldmanager.model.TourData): Boolean {
        if (tour.waypoints.size >= 2) return true
        player.sendMessage(plugin.languageManager.getMessage(player, "error.tour.not_enough_signs"))
        return false
    }

    private fun startWaypointPreview(player: Player) {
        stopWaypointPreview(player)
        waypointPreviewTasks[player.uniqueId] = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (!player.isOnline) {
                stopWaypointPreview(player)
                return@Runnable
            }
            val session = plugin.tourSessionManager.getEdit(player.uniqueId)
            if (session == null || !session.awaitingWaypointPick) {
                stopWaypointPreview(player)
                return@Runnable
            }
            val targetBlock = player.getTargetBlockExact(6) ?: return@Runnable
            val frameDust = Particle.DustOptions(Color.fromRGB(64, 255, 120), 0.5f)
            val x = targetBlock.x
            val y = targetBlock.y + 1
            val z = targetBlock.z
            spawnBlockOutline(player, x, y, z, frameDust)
        }, 0L, 2L)
    }

    private fun stopWaypointPreview(player: Player) {
        waypointPreviewTasks.remove(player.uniqueId)?.cancel()
    }

    private fun spawnBlockOutline(player: Player, blockX: Int, blockY: Int, blockZ: Int, dust: Particle.DustOptions) {
        val minX = blockX.toDouble()
        val minY = blockY.toDouble()
        val minZ = blockZ.toDouble()
        val maxX = blockX + 1.0
        val maxY = blockY + 1.0
        val maxZ = blockZ + 1.0

        spawnLine(player, minX, minY, minZ, maxX, minY, minZ, dust)
        spawnLine(player, minX, minY, maxZ, maxX, minY, maxZ, dust)
        spawnLine(player, minX, minY, minZ, minX, minY, maxZ, dust)
        spawnLine(player, maxX, minY, minZ, maxX, minY, maxZ, dust)

        spawnLine(player, minX, maxY, minZ, maxX, maxY, minZ, dust)
        spawnLine(player, minX, maxY, maxZ, maxX, maxY, maxZ, dust)
        spawnLine(player, minX, maxY, minZ, minX, maxY, maxZ, dust)
        spawnLine(player, maxX, maxY, minZ, maxX, maxY, maxZ, dust)

        spawnLine(player, minX, minY, minZ, minX, maxY, minZ, dust)
        spawnLine(player, maxX, minY, minZ, maxX, maxY, minZ, dust)
        spawnLine(player, minX, minY, maxZ, minX, maxY, maxZ, dust)
        spawnLine(player, maxX, minY, maxZ, maxX, maxY, maxZ, dust)
    }

    private fun spawnLine(player: Player, startX: Double, startY: Double, startZ: Double, endX: Double, endY: Double, endZ: Double, dust: Particle.DustOptions) {
        for (i in 0..9) {
            val t = i.toDouble() / 9.0
            val x = startX + (endX - startX) * t
            val y = startY + (endY - startY) * t
            val z = startZ + (endZ - startZ) * t
            player.spawnParticle(Particle.DUST, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, dust)
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val worldData = plugin.worldConfigRepository.findByWorldName(event.block.world.name) ?: return
        val signData = plugin.tourManager.findSignFromBlock(worldData, event.block) ?: return
        if (!plugin.tourManager.isWorldMember(worldData, event.player.uniqueId)) {
            event.isCancelled = true
            event.player.sendMessage(plugin.languageManager.getMessage(event.player, "error.tour.no_permission"))
            return
        }
        event.isDropItems = false
        plugin.tourManager.breakTourSign(worldData, signData.uuid, event.block.location)
    }

    @EventHandler
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        val editSession = plugin.tourSessionManager.getEdit(event.player.uniqueId)
        if (editSession != null) {
            editSession.awaitingIconPick = false
            editSession.awaitingWaypointPick = false
            stopWaypointPreview(event.player)
        }
        if (plugin.tourSessionManager.get(event.player.uniqueId) != null) {
            plugin.tourManager.stopTour(event.player, silent = true)
            event.player.sendMessage(plugin.languageManager.getMessage(event.player, "messages.tour.stopped_world_change"))
        }
    }
}
