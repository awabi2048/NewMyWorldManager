package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.gui.PortalGui
import me.awabi2048.myworldmanager.model.PortalData
import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.util.PortalItemUtil
import me.awabi2048.myworldmanager.util.ItemTag
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import java.util.UUID

class PortalListener(private val plugin: MyWorldManager) : Listener {

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
                         player.sendMessage(lang.getMessage(player, "messages.portal_bind_myworld_only"))
                         return
                    }

                    val worldUuid = managedWorld.uuid ?: return
                    val worldData = managedWorld

                    val isMember = worldData.owner == player.uniqueId || worldData.moderators.contains(player.uniqueId)
                    if (!isMember && worldData.publishLevel != PublishLevel.PUBLIC && worldData.publishLevel != PublishLevel.FRIEND) {
                        player.sendMessage(lang.getMessage(player, "messages.portal_bind_invalid_publish"))
                        return
                    }

                    PortalItemUtil.bindWorld(item, worldUuid, worldData.name, lang, player)
                    player.sendMessage(lang.getMessage(player, "messages.portal_bind_success", worldData.name))
                    event.isCancelled = true
                    return
                }
            }
        }

        // 2. 設置済みポータルの操作（メニュー）
        if (event.action == Action.RIGHT_CLICK_BLOCK) {
            val block = event.clickedBlock ?: return
            if (block.type == Material.END_PORTAL_FRAME) {
                val portal = plugin.portalRepository.findByLocation(block.location) ?: return
                
                // 権限チェック (設置者または管理者)
                if (portal.ownerUuid == player.uniqueId || player.hasPermission("myworldmanager.admin")) {
                    event.isCancelled = true
                    PortalGui(plugin).open(player, portal)
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onPlace(event: BlockPlaceEvent) {
        val item = event.itemInHand
        if (!ItemTag.isType(item, ItemTag.TYPE_PORTAL)) return
        
        val lang = plugin.languageManager
        val worldUuid = PortalItemUtil.getBoundWorldUuid(item)
        val targetWorldName = PortalItemUtil.getBoundTargetWorldName(item)

        if (worldUuid == null && targetWorldName == null) {
            event.isCancelled = true
            event.player.sendMessage(lang.getMessage(event.player, "messages.portal_not_bound"))
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
            
            // 撤去処理
            plugin.portalManager.removePortalVisuals(portal.id)
            plugin.portalRepository.removePortal(portal.id)
            event.player.sendMessage(plugin.languageManager.getMessage(event.player, "messages.portal_broken"))
        }
    }
}
