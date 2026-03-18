package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.gui.DialogConfirmManager
import me.awabi2048.myworldmanager.gui.PortalGui
import me.awabi2048.myworldmanager.model.PortalData
import me.awabi2048.myworldmanager.model.PortalType
import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.util.ItemTag
import me.awabi2048.myworldmanager.util.PortalItemUtil
import me.awabi2048.myworldmanager.util.WorldGateItemUtil
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import io.papermc.paper.event.player.PlayerCustomClickEvent
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import java.util.*

class PortalListener(private val plugin: MyWorldManager) : Listener {
    private data class GateSelectionSession(
        val first: Location,
        var second: Location? = null,
        var previewTaskId: Int? = null
    )

    private data class PendingGatePlacement(
        val first: Location,
        val second: Location,
        val boundWorldUuid: UUID?,
        val boundTargetWorldName: String?,
        val hand: EquipmentSlot?
    )

    private class WorldGateConfirmHolder : InventoryHolder {
        lateinit var inv: Inventory
        override fun getInventory(): Inventory = inv
    }

    private val gateSelections = mutableMapOf<UUID, GateSelectionSession>()
    private val pendingGatePlacements = mutableMapOf<UUID, PendingGatePlacement>()

    companion object {
        private const val WORLD_GATE_CONFIRM_ACTION = "mwm:confirm/world_gate_place"
        private const val WORLD_GATE_CANCEL_ACTION = "mwm:confirm/world_gate_place_cancel"
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = event.item ?: ItemStack(Material.AIR)
        
        // 1. アイテム状態での操作（紐づけ・解除）
        if (ItemTag.isType(item, ItemTag.TYPE_PORTAL)) {
            val lang = plugin.languageManager
            if (event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK) {
                if (player.isSneaking && (PortalItemUtil.getBoundWorldUuid(item) != null || PortalItemUtil.getBoundTargetWorldName(item) != null)) {
                    // 解除
                    PortalItemUtil.unbindWorld(item, lang, player)
                    player.sendMessage(lang.getMessage(player, "messages.portal_unbind_success"))
                    event.isCancelled = true
                    return
                } else {
                    // 紐づけ
                    if (player.isSneaking) return 
                    if (PortalItemUtil.getBoundWorldUuid(item) != null || PortalItemUtil.getBoundTargetWorldName(item) != null) return // 既に紐づけ済み

                    val currentWorld = player.world
                    // check if managed
                    val managedWorld = plugin.worldConfigRepository.findByWorldName(currentWorld.name)
                    if (managedWorld == null) {
                         player.sendMessage(lang.getMessage(player, "error.portal_bind_myworld_only"))
                         return
                    }

                    val worldUuid = managedWorld.uuid
                    val worldData = managedWorld

                    val isMember = worldData.owner == player.uniqueId || worldData.moderators.contains(player.uniqueId)
                    if (!isMember && worldData.publishLevel != PublishLevel.PUBLIC && worldData.publishLevel != PublishLevel.FRIEND) {
                        player.sendMessage(lang.getMessage(player, "error.portal_bind_invalid_publish"))
                        return
                    }

                    PortalItemUtil.bindWorld(item, worldUuid, worldData.name, lang, player)
                    player.sendMessage(lang.getMessage(player, "messages.portal_bind_success", mapOf("destination" to worldData.name)))
                    event.isCancelled = true
                    return
                }
            }
        }

        // 1-2. ワールドゲートアイテムの操作
        if (ItemTag.isType(item, ItemTag.TYPE_WORLD_GATE) && (event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK)) {
            val lang = plugin.languageManager
            if (player.isSneaking) {
                val boundWorldUuid = WorldGateItemUtil.getBoundWorldUuid(item)
                val boundTargetWorldName = WorldGateItemUtil.getBoundTargetWorldName(item)
                
                if (boundWorldUuid != null || boundTargetWorldName != null) {
                    WorldGateItemUtil.unbindWorld(item, lang, player)
                    player.sendMessage(lang.getMessage(player, "messages.world_gate_unbind_success"))
                    clearGateSession(player.uniqueId)
                    pendingGatePlacements.remove(player.uniqueId)
                    event.isCancelled = true
                    return
                }
                
                val managedWorld = plugin.worldConfigRepository.findByWorldName(player.world.name)
                if (managedWorld == null) {
                    player.sendMessage(lang.getMessage(player, "error.portal_bind_myworld_only"))
                    event.isCancelled = true
                    return
                }

                val worldUuid = managedWorld.uuid
                val worldData = managedWorld
                val isMember = worldData.owner == player.uniqueId || worldData.moderators.contains(player.uniqueId)
                if (!isMember && worldData.publishLevel != PublishLevel.PUBLIC && worldData.publishLevel != PublishLevel.FRIEND) {
                    player.sendMessage(lang.getMessage(player, "error.portal_bind_invalid_publish"))
                    event.isCancelled = true
                    return
                }

                WorldGateItemUtil.bindWorld(item, worldUuid, worldData.name, lang, player)
                player.sendMessage(lang.getMessage(player, "messages.world_gate_bind_success", mapOf("destination" to worldData.name)))
                clearGateSession(player.uniqueId)
                pendingGatePlacements.remove(player.uniqueId)
                event.isCancelled = true
                return
            }

            val boundWorldUuid = WorldGateItemUtil.getBoundWorldUuid(item)
            val boundTargetWorldName = WorldGateItemUtil.getBoundTargetWorldName(item)
            if (boundWorldUuid == null && boundTargetWorldName == null) {
                player.sendMessage(lang.getMessage(player, "error.world_gate_bind_required"))
                event.isCancelled = true
                return
            }

            val currentSession = gateSelections[player.uniqueId]
            if (currentSession == null) {
                val clicked = event.clickedBlock
                if (clicked == null) {
                    player.sendMessage(lang.getMessage(player, "messages.world_gate_select_first"))
                    event.isCancelled = true
                    return
                }

                gateSelections[player.uniqueId] = GateSelectionSession(clicked.location)
                startOrRefreshSelectionPreview(player, gateSelections[player.uniqueId]!!)
                player.sendMessage(
                    lang.getMessage(
                        player,
                        "messages.world_gate_point_selected",
                        mapOf("point" to 1, "x" to clicked.x, "y" to clicked.y, "z" to clicked.z)
                    )
                )
                player.sendMessage(lang.getMessage(player, "messages.world_gate_select_second"))
                event.isCancelled = true
                return
            }

            if (currentSession.second == null) {
                val clicked = event.clickedBlock
                if (clicked == null) {
                    player.sendMessage(lang.getMessage(player, "messages.world_gate_select_second"))
                    event.isCancelled = true
                    return
                }

                if (currentSession.first.world?.name != clicked.world.name) {
                    clearGateSession(player.uniqueId)
                    gateSelections[player.uniqueId] = GateSelectionSession(clicked.location)
                    startOrRefreshSelectionPreview(player, gateSelections[player.uniqueId]!!)
                    player.sendMessage(lang.getMessage(player, "messages.world_gate_world_mismatch"))
                    player.sendMessage(
                        lang.getMessage(
                            player,
                            "messages.world_gate_point_selected",
                            mapOf("point" to 1, "x" to clicked.x, "y" to clicked.y, "z" to clicked.z)
                        )
                    )
                    player.sendMessage(lang.getMessage(player, "messages.world_gate_select_second"))
                    event.isCancelled = true
                    return
                }

                currentSession.second = clicked.location
                startOrRefreshSelectionPreview(player, currentSession)
                player.sendMessage(
                    lang.getMessage(
                        player,
                        "messages.world_gate_point_selected",
                        mapOf("point" to 2, "x" to clicked.x, "y" to clicked.y, "z" to clicked.z)
                    )
                )
                player.sendMessage(lang.getMessage(player, "messages.world_gate_confirm_place"))
                event.isCancelled = true
                return
            }

            val second = currentSession.second ?: run {
                player.sendMessage(lang.getMessage(player, "messages.world_gate_select_second"))
                event.isCancelled = true
                return
            }

            pendingGatePlacements[player.uniqueId] = PendingGatePlacement(
                first = currentSession.first,
                second = second,
                boundWorldUuid = boundWorldUuid,
                boundTargetWorldName = boundTargetWorldName,
                hand = event.hand
            )

            showWorldGateConfirm(player)
            event.isCancelled = true
            return
        }

    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onAnyRightClickForPortalMenu(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return

        val player = event.player
        if (player.isSneaking) return
        if (event.hand != EquipmentSlot.HAND) return

        val clickedBlock = event.clickedBlock ?: return
        val handItem = player.inventory.itemInMainHand

        if (ItemTag.isType(handItem, ItemTag.TYPE_WORLD_GATE) || ItemTag.isType(handItem, ItemTag.TYPE_PORTAL)) {
            return
        }

        val portal = plugin.portalRepository.findByLocation(clickedBlock.location) ?: return
        if (portal.isGate()) return
        if (!canOpenPortalMenu(player, portal)) return

        event.isCancelled = true
        PortalGui(plugin).open(player, portal)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onAnyRightClickForGateMenu(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return

        val player = event.player
        if (player.isSneaking) return
        if (event.hand != EquipmentSlot.HAND) return

        val clickedBlock = event.clickedBlock ?: return
        val handItem = player.inventory.itemInMainHand

        if (ItemTag.isType(handItem, ItemTag.TYPE_WORLD_GATE) || ItemTag.isType(handItem, ItemTag.TYPE_PORTAL)) {
            return
        }

        val portal = plugin.portalRepository.findByContainingLocation(clickedBlock.location) ?: return
        if (!portal.isGate()) return
        if (!canOpenPortalMenu(player, portal)) return

        event.isCancelled = true
        PortalGui(plugin).open(player, portal)
    }

    private fun canOpenPortalMenu(player: org.bukkit.entity.Player, portal: PortalData): Boolean {
        return portal.ownerUuid == player.uniqueId || player.hasPermission("myworldmanager.admin")
    }

    private fun showWorldGateConfirm(player: org.bukkit.entity.Player) {
        val pending = pendingGatePlacements[player.uniqueId] ?: return
        val lang = plugin.languageManager

        val first = pending.first
        val second = pending.second
        val minX = minOf(first.blockX, second.blockX)
        val minY = minOf(first.blockY, second.blockY)
        val minZ = minOf(first.blockZ, second.blockZ)
        val maxX = maxOf(first.blockX, second.blockX)
        val maxY = maxOf(first.blockY, second.blockY)
        val maxZ = maxOf(first.blockZ, second.blockZ)
        val requiredPoints = plugin.portalManager.calculateWorldGatePlacementCost(minX, minY, minZ, maxX, maxY, maxZ)
        val currentPoints = plugin.playerStatsRepository.findByUuid(player.uniqueId).worldPoint
        val remainingPoints = (currentPoints - requiredPoints).coerceAtLeast(0)

        val title = LegacyComponentSerializer.legacySection().deserialize(
            lang.getMessage(player, "messages.world_gate_confirm_title")
        )
        val bodyLines = lang.getMessageList(
            player,
            "messages.world_gate_confirm_body",
            mapOf(
                "min_x" to minX,
                "min_y" to minY,
                "min_z" to minZ,
                "max_x" to maxX,
                "max_y" to maxY,
                "max_z" to maxZ,
                "required" to requiredPoints,
                "current" to currentPoints,
                "remaining" to remainingPoints
            )
        ).map { LegacyComponentSerializer.legacySection().deserialize(it) }

        DialogConfirmManager.showConfirmationByPreference(
            player,
            plugin,
            title,
            bodyLines,
            WORLD_GATE_CONFIRM_ACTION,
            WORLD_GATE_CANCEL_ACTION,
            lang.getMessage(player, "gui.common.confirm"),
            lang.getMessage(player, "gui.common.cancel")
        ) {
            openWorldGateConfirmGui(player)
        }
    }

    private fun openWorldGateConfirmGui(player: org.bukkit.entity.Player) {
        val lang = plugin.languageManager
        val pending = pendingGatePlacements[player.uniqueId] ?: return
        val minX = minOf(pending.first.blockX, pending.second.blockX)
        val minY = minOf(pending.first.blockY, pending.second.blockY)
        val minZ = minOf(pending.first.blockZ, pending.second.blockZ)
        val maxX = maxOf(pending.first.blockX, pending.second.blockX)
        val maxY = maxOf(pending.first.blockY, pending.second.blockY)
        val maxZ = maxOf(pending.first.blockZ, pending.second.blockZ)
        val requiredPoints = plugin.portalManager.calculateWorldGatePlacementCost(minX, minY, minZ, maxX, maxY, maxZ)
        val currentPoints = plugin.playerStatsRepository.findByUuid(player.uniqueId).worldPoint
        val remainingPoints = (currentPoints - requiredPoints).coerceAtLeast(0)

        val holder = WorldGateConfirmHolder()
        val inventory = Bukkit.createInventory(holder, 27, me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(lang.getMessage(player, "messages.world_gate_confirm_title")))
        holder.inv = inventory

        val pane = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        val paneMeta = pane.itemMeta
        paneMeta?.displayName(Component.empty())
        paneMeta?.isHideTooltip = true
        pane.itemMeta = paneMeta
        ItemTag.tagItem(pane, ItemTag.TYPE_GUI_DECORATION)
        for (i in 0 until inventory.size) {
            inventory.setItem(i, pane)
        }

        val infoItem = ItemStack(Material.BOOK)
        val infoMeta = infoItem.itemMeta
        infoMeta?.displayName(Component.text(lang.getMessage(player, "messages.world_gate_confirm_title")))
        infoMeta?.lore(
            lang.getComponentList(
                player,
                "messages.world_gate_confirm_body",
                mapOf(
                    "min_x" to minX,
                    "min_y" to minY,
                    "min_z" to minZ,
                    "max_x" to maxX,
                    "max_y" to maxY,
                    "max_z" to maxZ,
                    "required" to requiredPoints,
                    "current" to currentPoints,
                    "remaining" to remainingPoints
                )
            )
        )
        infoItem.itemMeta = infoMeta
        ItemTag.tagItem(infoItem, ItemTag.TYPE_GUI_INFO)
        inventory.setItem(13, infoItem)

        val confirmItem = ItemStack(Material.LIME_CONCRETE)
        val confirmMeta = confirmItem.itemMeta
        confirmMeta?.displayName(Component.text(lang.getMessage(player, "gui.common.confirm")))
        confirmItem.itemMeta = confirmMeta
        ItemTag.tagItem(confirmItem, ItemTag.TYPE_GUI_CONFIRM)
        inventory.setItem(11, confirmItem)

        val cancelItem = ItemStack(Material.RED_CONCRETE)
        val cancelMeta = cancelItem.itemMeta
        cancelMeta?.displayName(Component.text(lang.getMessage(player, "gui.common.cancel")))
        cancelItem.itemMeta = cancelMeta
        ItemTag.tagItem(cancelItem, ItemTag.TYPE_GUI_CANCEL)
        inventory.setItem(15, cancelItem)

        player.openInventory(inventory)
    }

    private fun confirmGatePlacement(player: org.bukkit.entity.Player) {
        val lang = plugin.languageManager
        val pending = pendingGatePlacements[player.uniqueId] ?: return

        val minX = minOf(pending.first.blockX, pending.second.blockX)
        val minY = minOf(pending.first.blockY, pending.second.blockY)
        val minZ = minOf(pending.first.blockZ, pending.second.blockZ)
        val maxX = maxOf(pending.first.blockX, pending.second.blockX)
        val maxY = maxOf(pending.first.blockY, pending.second.blockY)
        val maxZ = maxOf(pending.first.blockZ, pending.second.blockZ)

        val requiredPoints = plugin.portalManager.calculateWorldGatePlacementCost(minX, minY, minZ, maxX, maxY, maxZ).toLong()
        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)

        if (requiredPoints > stats.worldPoint.toLong()) {
            player.sendMessage(
                lang.getMessage(
                    player,
                    "error.world_gate_insufficient_points",
                    mapOf(
                        "required" to requiredPoints,
                        "current" to stats.worldPoint,
                        "shortage" to (requiredPoints - stats.worldPoint.toLong())
                    )
                )
            )
            return
        }

        if (!consumeWorldGateItem(player, pending.hand)) {
            player.sendMessage(lang.getMessage(player, "error.world_gate_item_required"))
            return
        }

        val firstWorld = pending.first.world ?: run {
            clearGateSession(player.uniqueId)
            pendingGatePlacements.remove(player.uniqueId)
            return
        }

        stats.worldPoint -= requiredPoints.toInt()
        plugin.playerStatsRepository.save(stats)

        val gate = PortalData(
            worldName = firstWorld.name,
            x = pending.first.blockX,
            y = pending.first.blockY,
            z = pending.first.blockZ,
            worldUuid = pending.boundWorldUuid,
            targetWorldName = pending.boundTargetWorldName,
            ownerUuid = player.uniqueId,
            type = PortalType.GATE,
            minX = minX,
            minY = minY,
            minZ = minZ,
            maxX = maxX,
            maxY = maxY,
            maxZ = maxZ
        )
        plugin.portalRepository.addPortal(gate)
        clearGateSession(player.uniqueId)
        pendingGatePlacements.remove(player.uniqueId)
        player.sendMessage(lang.getMessage(player, "messages.world_gate_place_success"))
    }

    private fun cancelGatePlacement(player: org.bukkit.entity.Player) {
        player.sendMessage(plugin.languageManager.getMessage(player, "messages.world_gate_place_cancelled"))
    }

    private fun consumeWorldGateItem(player: org.bukkit.entity.Player, hand: EquipmentSlot?): Boolean {
        val preferred = hand ?: EquipmentSlot.HAND
        if (consumeOneWorldGateFromHand(player, preferred)) return true
        val fallback = if (preferred == EquipmentSlot.OFF_HAND) EquipmentSlot.HAND else EquipmentSlot.OFF_HAND
        return consumeOneWorldGateFromHand(player, fallback)
    }

    private fun consumeOneWorldGateFromHand(player: org.bukkit.entity.Player, hand: EquipmentSlot): Boolean {
        val current = when (hand) {
            EquipmentSlot.OFF_HAND -> player.inventory.itemInOffHand
            else -> player.inventory.itemInMainHand
        }

        if (current.type == Material.AIR) return false
        if (!ItemTag.isType(current, ItemTag.TYPE_WORLD_GATE)) return false

        if (current.amount <= 1) {
            when (hand) {
                EquipmentSlot.OFF_HAND -> player.inventory.setItemInOffHand(null)
                else -> player.inventory.setItemInMainHand(null)
            }
        } else {
            current.amount = current.amount - 1
            when (hand) {
                EquipmentSlot.OFF_HAND -> player.inventory.setItemInOffHand(current)
                else -> player.inventory.setItemInMainHand(current)
            }
        }
        return true
    }

    private fun startOrRefreshSelectionPreview(player: org.bukkit.entity.Player, session: GateSelectionSession) {
        renderSelectionPreview(player, session)
        if (session.previewTaskId != null) return

        val task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            val current = gateSelections[player.uniqueId] ?: return@Runnable
            if (!player.isOnline) {
                clearGateSession(player.uniqueId)
                return@Runnable
            }
            renderSelectionPreview(player, current)
        }, 10L, 10L)
        session.previewTaskId = task.taskId
    }

    private fun clearGateSession(playerUuid: UUID) {
        val session = gateSelections.remove(playerUuid) ?: return
        val taskId = session.previewTaskId ?: return
        Bukkit.getScheduler().cancelTask(taskId)
    }

    private fun renderSelectionPreview(player: org.bukkit.entity.Player, session: GateSelectionSession) {
        val firstWorld = session.first.world ?: return
        if (firstWorld.name != player.world.name) return

        val pointColor = Particle.DustOptions(Color.fromRGB(64, 255, 160), 1.0f)
        val regionColor = Particle.DustOptions(Color.fromRGB(120, 200, 255), 1.0f)

        spawnBlockOutline(firstWorld, session.first.blockX, session.first.blockY, session.first.blockZ, pointColor)

        val second = session.second ?: return
        spawnBlockOutline(firstWorld, second.blockX, second.blockY, second.blockZ, pointColor)
        spawnCuboidFrameAndFaces(
            firstWorld,
            minOf(session.first.blockX, second.blockX),
            minOf(session.first.blockY, second.blockY),
            minOf(session.first.blockZ, second.blockZ),
            maxOf(session.first.blockX, second.blockX),
            maxOf(session.first.blockY, second.blockY),
            maxOf(session.first.blockZ, second.blockZ),
            regionColor
        )
    }

    private fun spawnBlockOutline(
        world: org.bukkit.World,
        blockX: Int,
        blockY: Int,
        blockZ: Int,
        dust: Particle.DustOptions
    ) {
        val minX = blockX.toDouble()
        val minY = blockY.toDouble()
        val minZ = blockZ.toDouble()
        val maxX = blockX + 1.0
        val maxY = blockY + 1.0
        val maxZ = blockZ + 1.0

        spawnLine(world, minX, minY, minZ, maxX, minY, minZ, 0.2, dust)
        spawnLine(world, minX, minY, maxZ, maxX, minY, maxZ, 0.2, dust)
        spawnLine(world, minX, maxY, minZ, maxX, maxY, minZ, 0.2, dust)
        spawnLine(world, minX, maxY, maxZ, maxX, maxY, maxZ, 0.2, dust)

        spawnLine(world, minX, minY, minZ, minX, maxY, minZ, 0.2, dust)
        spawnLine(world, maxX, minY, minZ, maxX, maxY, minZ, 0.2, dust)
        spawnLine(world, minX, minY, maxZ, minX, maxY, maxZ, 0.2, dust)
        spawnLine(world, maxX, minY, maxZ, maxX, maxY, maxZ, 0.2, dust)

        spawnLine(world, minX, minY, minZ, minX, minY, maxZ, 0.2, dust)
        spawnLine(world, maxX, minY, minZ, maxX, minY, maxZ, 0.2, dust)
        spawnLine(world, minX, maxY, minZ, minX, maxY, maxZ, 0.2, dust)
        spawnLine(world, maxX, maxY, minZ, maxX, maxY, maxZ, 0.2, dust)
    }

    private fun spawnCuboidFrameAndFaces(
        world: org.bukkit.World,
        minBlockX: Int,
        minBlockY: Int,
        minBlockZ: Int,
        maxBlockX: Int,
        maxBlockY: Int,
        maxBlockZ: Int,
        dust: Particle.DustOptions
    ) {
        val minX = minBlockX.toDouble()
        val minY = minBlockY.toDouble()
        val minZ = minBlockZ.toDouble()
        val maxX = maxBlockX + 1.0
        val maxY = maxBlockY + 1.0
        val maxZ = maxBlockZ + 1.0

        spawnLine(world, minX, minY, minZ, maxX, minY, minZ, 0.25, dust)
        spawnLine(world, minX, minY, maxZ, maxX, minY, maxZ, 0.25, dust)
        spawnLine(world, minX, maxY, minZ, maxX, maxY, minZ, 0.25, dust)
        spawnLine(world, minX, maxY, maxZ, maxX, maxY, maxZ, 0.25, dust)

        spawnLine(world, minX, minY, minZ, minX, maxY, minZ, 0.25, dust)
        spawnLine(world, maxX, minY, minZ, maxX, maxY, minZ, 0.25, dust)
        spawnLine(world, minX, minY, maxZ, minX, maxY, maxZ, 0.25, dust)
        spawnLine(world, maxX, minY, maxZ, maxX, maxY, maxZ, 0.25, dust)

        spawnLine(world, minX, minY, minZ, minX, minY, maxZ, 0.25, dust)
        spawnLine(world, maxX, minY, minZ, maxX, minY, maxZ, 0.25, dust)
        spawnLine(world, minX, maxY, minZ, minX, maxY, maxZ, 0.25, dust)
        spawnLine(world, maxX, maxY, minZ, maxX, maxY, maxZ, 0.25, dust)

        val spacing = 0.75
        var x = minX
        while (x <= maxX + 1e-6) {
            var y = minY
            while (y <= maxY + 1e-6) {
                world.spawnParticle(Particle.DUST, x, y, minZ, 1, 0.0, 0.0, 0.0, 0.0, dust)
                world.spawnParticle(Particle.DUST, x, y, maxZ, 1, 0.0, 0.0, 0.0, 0.0, dust)
                y += spacing
            }
            x += spacing
        }

        var z = minZ
        while (z <= maxZ + 1e-6) {
            var y = minY
            while (y <= maxY + 1e-6) {
                world.spawnParticle(Particle.DUST, minX, y, z, 1, 0.0, 0.0, 0.0, 0.0, dust)
                world.spawnParticle(Particle.DUST, maxX, y, z, 1, 0.0, 0.0, 0.0, 0.0, dust)
                y += spacing
            }
            z += spacing
        }

        x = minX
        while (x <= maxX + 1e-6) {
            z = minZ
            while (z <= maxZ + 1e-6) {
                world.spawnParticle(Particle.DUST, x, minY, z, 1, 0.0, 0.0, 0.0, 0.0, dust)
                world.spawnParticle(Particle.DUST, x, maxY, z, 1, 0.0, 0.0, 0.0, 0.0, dust)
                z += spacing
            }
            x += spacing
        }
    }

    private fun spawnLine(
        world: org.bukkit.World,
        startX: Double,
        startY: Double,
        startZ: Double,
        endX: Double,
        endY: Double,
        endZ: Double,
        step: Double,
        dust: Particle.DustOptions
    ) {
        val dx = endX - startX
        val dy = endY - startY
        val dz = endZ - startZ
        val distance = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
        if (distance <= 0.0) {
            world.spawnParticle(Particle.DUST, startX, startY, startZ, 1, 0.0, 0.0, 0.0, 0.0, dust)
            return
        }

        val count = maxOf(1, kotlin.math.ceil(distance / step).toInt())
        for (i in 0..count) {
            val t = i.toDouble() / count.toDouble()
            val x = startX + dx * t
            val y = startY + dy * t
            val z = startZ + dz * t
            world.spawnParticle(Particle.DUST, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, dust)
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onPlace(event: BlockPlaceEvent) {
        val item = event.itemInHand
        if (ItemTag.isType(item, ItemTag.TYPE_WORLD_GATE)) {
            event.isCancelled = true
            return
        }
        if (!ItemTag.isType(item, ItemTag.TYPE_PORTAL)) return

        val existingPortal = plugin.portalRepository.findByContainingLocation(event.block.location)
        if (existingPortal?.isGate() == true) {
            event.isCancelled = true
            event.player.sendMessage(
                plugin.languageManager.getMessage(event.player, "error.portal_place_in_world_gate")
            )
            return
        }
        
        val lang = plugin.languageManager
        val worldUuid = PortalItemUtil.getBoundWorldUuid(item)
        val targetWorldName = PortalItemUtil.getBoundTargetWorldName(item)

        if (worldUuid == null && targetWorldName == null) {
            event.isCancelled = true
            event.player.sendMessage(lang.getMessage(event.player, "error.portal_not_bound"))
            return
        }
        
        val portal = PortalData(
            worldName = event.block.world.name,
            x = event.block.x,
            y = event.block.y,
            z = event.block.z,
            worldUuid = worldUuid,
            targetWorldName = targetWorldName,
            ownerUuid = event.player.uniqueId
        )
        plugin.portalRepository.addPortal(portal)
        event.player.sendMessage(lang.getMessage(event.player, "messages.portal_place_success"))
    }

     @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreak(event: org.bukkit.event.block.BlockBreakEvent) {
         val block = event.block
          if (block.type == Material.END_PORTAL_FRAME) {
              val portal = plugin.portalRepository.findByLocation(block.location) ?: return
              if (portal.isGate()) return

              // 撤去処理（正確な順序で実行）
              // 1. ビジュアル要素を削除（TextDisplay など）
              plugin.portalManager.removePortalVisuals(portal.id)
             
             // 2. ポータルデータをリポジトリから削除
             plugin.portalRepository.removePortal(portal.id)
             
             // 3. プレイヤーにメッセージを送信
             event.player.sendMessage(plugin.languageManager.getMessage(event.player, "messages.portal_broken"))
         }
     }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        val from = event.from
        val to = event.to
        
        // ブロックごとの移動のみ検知（軽量化）
        if (from.blockX == to.blockX && from.blockY == to.blockY && from.blockZ == to.blockZ) return

        if (event.player.isSneaking) return

        plugin.portalManager.handlePlayerMove(event.player)
    }

    @EventHandler
    fun onCustomClick(event: PlayerCustomClickEvent) {
        val conn = event.commonConnection as? io.papermc.paper.connection.PlayerGameConnection ?: return
        val player = conn.player
        val identifier = event.identifier.asString()

        when (identifier) {
            WORLD_GATE_CANCEL_ACTION -> {
                DialogConfirmManager.safeCloseDialog(player)
                cancelGatePlacement(player)
            }
            WORLD_GATE_CONFIRM_ACTION -> {
                DialogConfirmManager.safeCloseDialog(player)
                confirmGatePlacement(player)
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.view.topInventory.holder !is WorldGateConfirmHolder) return
        event.isCancelled = true

        val player = event.whoClicked as? org.bukkit.entity.Player ?: return
        val item = event.currentItem ?: return
        when (ItemTag.getType(item)) {
            ItemTag.TYPE_GUI_CONFIRM -> {
                confirmGatePlacement(player)
                player.closeInventory()
            }
            ItemTag.TYPE_GUI_CANCEL -> {
                cancelGatePlacement(player)
                player.closeInventory()
            }
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        clearGateSession(event.player.uniqueId)
        pendingGatePlacements.remove(event.player.uniqueId)
    }
}
