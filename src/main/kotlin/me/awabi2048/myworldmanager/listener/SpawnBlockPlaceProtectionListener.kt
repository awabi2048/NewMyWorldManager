package me.awabi2048.myworldmanager.listener

import com.awabi2048.ccsystem.api.localization.generated.CommonKeys
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent

/**
 * ワールドのスポーン位置（足・頭の2ブロック）へ貫通不可ブロックを新規配置できないようにします。
 *
 * スポーン地点に立つプレイヤーがブロックの中に埋まって窒息するのを防ぐためのガードです。
 * スラブやガラスなどの透過ブロックは窒息しないため対象外とし、不透明（isOccluding）ブロックだけを拒否します。
 */
class SpawnBlockPlaceProtectionListener(private val plugin: MyWorldManager) : Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        // 透過ブロック（スラブ・ガラス・液体等）はプレイヤーを埋めないため対象外です。
        if (!event.block.type.isOccluding) return
        val worldData = plugin.worldConfigRepository.findByWorldName(event.block.world.name) ?: return
        val placed = event.block
        val protected = spawnProtectedBlocks(worldData)
        if (protected.any { it.x == placed.x && it.y == placed.y && it.z == placed.z }) {
            event.isCancelled = true
            event.player.sendMessage(
                plugin.languageManager.getMessage(event.player, CommonKeys.ERROR_SPAWN_BLOCK_PLACEMENT_BLOCKED),
            )
        }
    }

    private fun spawnProtectedBlocks(worldData: WorldData): List<ProtectedBlock> = buildList {
        listOfNotNull(worldData.spawnPosMember, worldData.spawnPosGuest).forEach { spawn ->
            // スポーン位置はプレイヤーの足の位置のため、足ブロック（floor(y)）と頭ブロック（floor(y)+1）を保護します。
            val feetY = spawn.blockY
            add(ProtectedBlock(spawn.blockX, feetY, spawn.blockZ))
            add(ProtectedBlock(spawn.blockX, feetY + 1, spawn.blockZ))
        }
    }

    private data class ProtectedBlock(val x: Int, val y: Int, val z: Int)
}