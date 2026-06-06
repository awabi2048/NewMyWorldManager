package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import org.bukkit.Location
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockBurnEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockFadeEvent
import org.bukkit.event.block.BlockFromToEvent
import org.bukkit.event.block.BlockGrowEvent
import org.bukkit.event.block.BlockMultiPlaceEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.block.BlockSpreadEvent
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.world.StructureGrowEvent
import java.util.UUID

class BorderExpansionChangeListener(private val plugin: MyWorldManager) : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        markChanged(event.block.location)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockMultiPlace(event: BlockMultiPlaceEvent) {
        markChanged(event.replacedBlockStates.map { it.block.location })
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        markChanged(event.block.location)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBurn(event: BlockBurnEvent) {
        markChanged(event.block.location)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockFade(event: BlockFadeEvent) {
        markChanged(event.block.location)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockGrow(event: BlockGrowEvent) {
        markChanged(event.block.location)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockSpread(event: BlockSpreadEvent) {
        markChanged(event.block.location)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockFromTo(event: BlockFromToEvent) {
        markChanged(event.toBlock.location)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityChangeBlock(event: EntityChangeBlockEvent) {
        markChanged(event.block.location)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) {
        markChanged(event.blockList().map { it.location })
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        markChanged(event.blockList().map { it.location })
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPistonExtend(event: BlockPistonExtendEvent) {
        markChanged(
            event.blocks.flatMap { block ->
                listOf(block.location, block.getRelative(event.direction).location)
            }
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPistonRetract(event: BlockPistonRetractEvent) {
        markChanged(
            event.blocks.flatMap { block ->
                listOf(block.location, block.getRelative(event.direction).location)
            }
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onStructureGrow(event: StructureGrowEvent) {
        markChanged(event.blocks.map { it.location })
    }

    private fun markChanged(location: Location) {
        markChanged(listOf(location))
    }

    private fun markChanged(locations: Iterable<Location>) {
        val changedWorlds = mutableSetOf<UUID>()
        for (location in locations) {
            val worldName = location.world?.name ?: continue
            val worldData = plugin.worldConfigRepository.findByWorldName(worldName) ?: continue
            val x = location.blockX + 0.5
            val z = location.blockZ + 0.5
            val record = worldData.borderExpansionHistory
                .asReversed()
                .firstOrNull { !it.modified && it.containsAddedArea(x, z) }
                ?: continue
            record.modified = true
            changedWorlds.add(worldData.uuid)
        }

        changedWorlds.forEach { uuid ->
            plugin.worldConfigRepository.findByUuid(uuid)?.let { plugin.worldConfigRepository.save(it) }
        }
    }
}
