package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.event.MwmSpawnEggUseEvent
import me.awabi2048.myworldmanager.api.event.MwmSpawnEggScopeEvent
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockDispenseEvent
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack

/** No item mutation, spawn replacement, or global permission grants. */
class SpawnEggRestrictionListener(private val plugin: MyWorldManager) : Listener {
    private fun managed(world: World) = plugin.worldConfigRepository.findByWorldName(world.name) != null ||
        plugin.worldConfigRepository.findByWorldKey(world.key.toString()) != null
    private fun egg(item: ItemStack?) = item?.type?.name?.endsWith("_SPAWN_EGG") == true
    private fun checksEggs(world: World): Boolean {
        if (managed(world)) return true
        val scope = MwmSpawnEggScopeEvent(world)
        plugin.server.pluginManager.callEvent(scope)
        return scope.checkRequired
    }

    private fun reason(source: Event, world: World, player: Player?, hand: EquipmentSlot?,
                       item: ItemStack, operation: MwmSpawnEggUseEvent.Operation): String? {
        val query = MwmSpawnEggUseEvent(source, world, player, hand, item, operation)
        plugin.server.pluginManager.callEvent(query)
        if (query.decision == MwmSpawnEggUseEvent.Decision.DENY)
            return query.denialReason ?: "このアイテムは使用できません。"
        if (operation == MwmSpawnEggUseEvent.Operation.DISPENSE)
            return "このワールドでは卵のディスペンサー使用はできません。"
        // The integration's DENY above still blocks nets, including those held by OPs.
        if (operation == MwmSpawnEggUseEvent.Operation.CHANGE_SPAWNER)
            return if (player?.isOp == true) null else "スポーンエッグでスポナーを変更できるのはOPのみです。"
        if (query.decision == MwmSpawnEggUseEvent.Decision.PLUGIN_HANDLED) return null
        if (player != null && (player.isOp || player.hasPermission(PERMISSION))) return null
        return "このワールドで通常のスポーンエッグを使う権限がありません。"
    }

    // LOWEST ensures unrecognised eggs are denied before item plugins run.
    // Do not use ignoreCancelled: Bukkit pre-cancels right clicks in air, and
    // useInteractedBlock/useItemInHand carry independent protection decisions.
    @EventHandler(priority = EventPriority.LOWEST)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        val item = event.item ?: return
        if (!egg(item) || !checksEggs(event.player.world)) return
        val operation = if (event.clickedBlock?.type == Material.SPAWNER ||
            event.clickedBlock?.type == Material.TRIAL_SPAWNER)
            MwmSpawnEggUseEvent.Operation.CHANGE_SPAWNER else MwmSpawnEggUseEvent.Operation.PLAYER_USE
        val denial = reason(event, event.player.world, event.player, event.hand, item, operation) ?: return
        event.setUseItemInHand(Event.Result.DENY)
        event.setUseInteractedBlock(Event.Result.DENY)
        event.player.sendMessage(denial)
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onEntityInteract(event: PlayerInteractEntityEvent) {
        val player = event.player
        if (!checksEggs(player.world)) return
        val item = player.inventory.getItem(event.hand)
        if (!egg(item)) return
        val denial = reason(event, player.world, player, event.hand, item,
            MwmSpawnEggUseEvent.Operation.PLAYER_USE) ?: return
        event.isCancelled = true
        player.sendMessage(denial)
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onEntityInteractAt(event: PlayerInteractAtEntityEvent) = onEntityInteract(event)

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onDispense(event: BlockDispenseEvent) {
        if (!egg(event.item) || !checksEggs(event.block.world)) return
        reason(event, event.block.world, null, null, event.item, MwmSpawnEggUseEvent.Operation.DISPENSE)
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onNaturalSpawn(event: CreatureSpawnEvent) {
        if (event.spawnReason !in NATURAL_REASONS || !managed(event.location.world)) return
        event.isCancelled = true
    }

    companion object {
        const val PERMISSION = "myworldmanager.spawn-egg.use"
        private val NATURAL_REASONS = setOf(CreatureSpawnEvent.SpawnReason.NATURAL,
            CreatureSpawnEvent.SpawnReason.CHUNK_GEN, CreatureSpawnEvent.SpawnReason.PATROL)
    }
}
