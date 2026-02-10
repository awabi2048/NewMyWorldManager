package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.gui.LikeSignDialogManager
import me.awabi2048.myworldmanager.model.LikeSignDisplayType
import me.awabi2048.myworldmanager.util.CustomItem
import me.awabi2048.myworldmanager.util.ItemTag
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Sign
import org.bukkit.entity.Interaction
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.inventory.EquipmentSlot
import java.util.UUID

class LikeSignListener(private val plugin: MyWorldManager) : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = event.item

        if (event.action == Action.RIGHT_CLICK_BLOCK && event.hand == EquipmentSlot.HAND) {
            if (item != null && ItemTag.isType(item, ItemTag.TYPE_LIKE_SIGN)) {
                event.isCancelled = true

                val block = event.clickedBlock ?: return
                val blockFace = event.blockFace
                val hand = event.hand ?: EquipmentSlot.HAND

                if (blockFace == BlockFace.UP) {
                    LikeSignDialogManager.startPlacementSession(player, plugin, block, blockFace, hand)
                } else if (blockFace == BlockFace.NORTH || blockFace == BlockFace.SOUTH || 
                           blockFace == BlockFace.EAST || blockFace == BlockFace.WEST) {
                    LikeSignDialogManager.startPlacementSession(player, plugin, block, blockFace, hand)
                }
                return
            }
        }

        if (event.action == Action.RIGHT_CLICK_BLOCK && event.clickedBlock?.type == Material.PALE_OAK_WALL_SIGN) {
            val signBlock = event.clickedBlock ?: return
            val sign = signBlock.state as? Sign ?: return

            val worldName = player.world.name
            if (!worldName.startsWith("my_world.")) return

            val worldUuidStr = worldName.removePrefix("my_world.")
            val worldUuid = try { UUID.fromString(worldUuidStr) } catch (e: Exception) { return }
            val worldData = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return

            val signData = plugin.likeSignManager.findSignFromSignBlock(worldData, signBlock) ?: return

            event.isCancelled = true

            if (plugin.likeSignManager.isWorldMember(worldData, player.uniqueId)) {
                LikeSignDialogManager.startEditSession(player, plugin, signData, worldUuid)
                return
            }

            if (plugin.likeSignManager.isOnCooldown(player.uniqueId)) {
                player.sendMessage(plugin.languageManager.getMessage(player, "error.like_sign.cooldown"))
                return
            }

            if (signData.hasLiked(player.uniqueId)) {
                LikeSignDialogManager.showLikeConfirmDialog(player, plugin, signData, worldUuid)
            } else {
                signData.addLike(player.uniqueId)
                plugin.worldConfigRepository.save(worldData)
                plugin.likeSignManager.refreshSignDisplay(signData, worldData)
                plugin.likeSignManager.setCooldown(player.uniqueId)

                player.sendMessage(plugin.languageManager.getMessage(player, "messages.like_sign.liked"))
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f)
                player.world.spawnParticle(
                    org.bukkit.Particle.HEART,
                    signBlock.location.add(0.5, 1.0, 0.5),
                    5, 0.3, 0.3, 0.3, 0.0
                )
            }
        }
    }

    @EventHandler
    fun onPlayerInteractAtEntity(event: PlayerInteractAtEntityEvent) {
        val player = event.player
        val entity = event.rightClicked

        if (entity !is Interaction) return

        val worldName = player.world.name
        if (!worldName.startsWith("my_world.")) return

        val worldUuidStr = worldName.removePrefix("my_world.")
        val worldUuid = try { UUID.fromString(worldUuidStr) } catch (e: Exception) { return }
        val worldData = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return

        val signData = plugin.likeSignManager.findSignFromEntity(worldData, entity) ?: return

        event.isCancelled = true

        if (plugin.likeSignManager.isWorldMember(worldData, player.uniqueId)) {
            LikeSignDialogManager.startEditSession(player, plugin, signData, worldUuid)
            return
        }

        if (plugin.likeSignManager.isOnCooldown(player.uniqueId)) {
            player.sendMessage(plugin.languageManager.getMessage(player, "error.like_sign.cooldown"))
            return
        }

        if (signData.hasLiked(player.uniqueId)) {
            LikeSignDialogManager.showLikeConfirmDialog(player, plugin, signData, worldUuid)
        } else {
            signData.addLike(player.uniqueId)
            plugin.worldConfigRepository.save(worldData)
            plugin.likeSignManager.refreshSignDisplay(signData, worldData)
            plugin.likeSignManager.setCooldown(player.uniqueId)

            player.sendMessage(plugin.languageManager.getMessage(player, "messages.like_sign.liked"))
            player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f)
            player.world.spawnParticle(
                org.bukkit.Particle.HEART,
                entity.location.add(0.0, 0.5, 0.0),
                5, 0.3, 0.3, 0.3, 0.0
            )
        }
    }

    @EventHandler
    fun onChunkLoad(event: ChunkLoadEvent) {
        val world = event.world
        val worldName = world.name

        if (!worldName.startsWith("my_world.")) return

        val worldUuidStr = worldName.removePrefix("my_world.")
        val worldUuid = try { UUID.fromString(worldUuidStr) } catch (e: Exception) { return }
        val worldData = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return

        plugin.likeSignManager.spawnHologramsForWorld(worldData)
    }
}
