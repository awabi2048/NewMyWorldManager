package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.gui.PortalGui
import me.awabi2048.myworldmanager.model.PortalData
import me.awabi2048.myworldmanager.model.PortalType
import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.util.ItemTag
import me.awabi2048.myworldmanager.util.PortalItemUtil
import me.awabi2048.myworldmanager.util.WorldGateItemUtil
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import java.util.*

class PortalListener(private val plugin: MyWorldManager) : Listener {
    private data class GateSelectionSession(val first: Location, var second: Location? = null)

    private val gateSelections = mutableMapOf<UUID, GateSelectionSession>()

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
                gateSelections.remove(player.uniqueId)
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
                    gateSelections[player.uniqueId] = GateSelectionSession(clicked.location)
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

            val first = currentSession.first
            val second = currentSession.second ?: return
            val firstWorld = first.world ?: run {
                gateSelections.remove(player.uniqueId)
                event.isCancelled = true
                return
            }

            val worldName = firstWorld.name
            val minX = minOf(first.blockX, second.blockX)
            val minY = minOf(first.blockY, second.blockY)
            val minZ = minOf(first.blockZ, second.blockZ)
            val maxX = maxOf(first.blockX, second.blockX)
            val maxY = maxOf(first.blockY, second.blockY)
            val maxZ = maxOf(first.blockZ, second.blockZ)

            val gate = PortalData(
                worldName = worldName,
                x = first.blockX,
                y = first.blockY,
                z = first.blockZ,
                worldUuid = boundWorldUuid,
                targetWorldName = boundTargetWorldName,
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
            consumeOneItem(player, event.hand)
            gateSelections.remove(player.uniqueId)
            player.sendMessage(lang.getMessage(player, "messages.world_gate_place_success"))
            event.isCancelled = true
            return
        }

        // 2. 設置済みポータルの操作（メニュー）
        if (event.action == Action.RIGHT_CLICK_BLOCK) {
            val block = event.clickedBlock ?: return
            val portal = plugin.portalRepository.findByContainingLocation(block.location) ?: return

            // 権限チェック (設置者または管理者)
            if (portal.ownerUuid == player.uniqueId || player.hasPermission("myworldmanager.admin")) {
                event.isCancelled = true
                PortalGui(plugin).open(player, portal)
            }
        }
    }

    private fun consumeOneItem(player: org.bukkit.entity.Player, hand: EquipmentSlot?) {
        val targetHand = hand ?: EquipmentSlot.HAND
        val current = when (targetHand) {
            EquipmentSlot.OFF_HAND -> player.inventory.itemInOffHand
            else -> player.inventory.itemInMainHand
        }

        if (current.type == Material.AIR) return
        if (current.amount <= 1) {
            when (targetHand) {
                EquipmentSlot.OFF_HAND -> player.inventory.setItemInOffHand(null)
                else -> player.inventory.setItemInMainHand(null)
            }
        } else {
            current.amount = current.amount - 1
            when (targetHand) {
                EquipmentSlot.OFF_HAND -> player.inventory.setItemInOffHand(current)
                else -> player.inventory.setItemInMainHand(current)
            }
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
        
        plugin.portalManager.handlePlayerMove(event.player)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        gateSelections.remove(event.player.uniqueId)
    }
}
