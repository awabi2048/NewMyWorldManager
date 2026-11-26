package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.event.MwmSpawnEggUseEvent
import me.awabi2048.myworldmanager.api.event.MwmSpawnEggScopeEvent
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.repository.WorldConfigRepository
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockDispenseEvent
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import org.bukkit.plugin.PluginManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class SpawnEggRestrictionListenerTest {
    private val plugin = mock(MyWorldManager::class.java)
    private val repository = mock(WorldConfigRepository::class.java)
    private val server = mock(Server::class.java)
    private val manager = mock(PluginManager::class.java)
    private val player = mock(Player::class.java)
    private val world = mock(World::class.java)
    private val item = mock(ItemStack::class.java)
    private val listener = SpawnEggRestrictionListener(plugin)

    init {
        `when`(plugin.worldConfigRepository).thenReturn(repository)
        `when`(plugin.server).thenReturn(server)
        `when`(server.pluginManager).thenReturn(manager)
        `when`(player.world).thenReturn(world)
        `when`(world.name).thenReturn("custom-managed-world")
        `when`(world.key).thenReturn(NamespacedKey.minecraft("unmanaged"))
        `when`(repository.findByWorldName("custom-managed-world")).thenReturn(mock(WorldData::class.java))
        `when`(item.type).thenReturn(Material.WANDERING_TRADER_SPAWN_EGG)
        `when`(item.clone()).thenReturn(item)
    }

    private fun interact(hand: EquipmentSlot = EquipmentSlot.HAND): PlayerInteractEvent {
        val event = mock(PlayerInteractEvent::class.java)
        `when`(event.player).thenReturn(player)
        `when`(event.item).thenReturn(item)
        `when`(event.hand).thenReturn(hand)
        `when`(event.action).thenReturn(Action.RIGHT_CLICK_BLOCK)
        return event
    }

    @Test fun `ordinary egg denied in both hands without mutating inventory`() {
        for (hand in listOf(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND)) {
            val event = interact(hand)
            listener.onInteract(event)
            verify(event).setUseItemInHand(Event.Result.DENY)
            verify(event).setUseInteractedBlock(Event.Result.DENY)
        }
        verify(player, never()).setOp(anyBoolean())
        verify(player, never()).getInventory()
        verify(item, never()).setAmount(anyInt())
    }

    @Test fun `operator and explicit permission allow ordinary eggs`() {
        `when`(player.isOp).thenReturn(true)
        val op = interact()
        listener.onInteract(op)
        verify(op, never()).setUseItemInHand(any())
        `when`(player.isOp).thenReturn(false)
        `when`(player.hasPermission(SpawnEggRestrictionListener.PERMISSION)).thenReturn(true)
        val permission = interact()
        listener.onInteract(permission)
        verify(permission, never()).setUseItemInHand(any())
    }

    @Test fun `extension denial also applies to operators`() {
        `when`(player.isOp).thenReturn(true)
        doAnswer { invocation -> (invocation.arguments[0] as MwmSpawnEggUseEvent).deny("blocked mob"); null }
            .`when`(manager).callEvent(any(MwmSpawnEggUseEvent::class.java))
        val event = interact()
        listener.onInteract(event)
        verify(event).setUseItemInHand(Event.Result.DENY)
        verify(player).sendMessage("blocked mob")
    }

    @Test fun `exemption never resets protection decisions and cannot leak to next use`() {
        doAnswer { invocation -> (invocation.arguments[0] as MwmSpawnEggUseEvent).markPluginHandled(); null }
            .`when`(manager).callEvent(any(MwmSpawnEggUseEvent::class.java))
        val first = interact()
        listener.onInteract(first)
        verify(first, never()).setUseItemInHand(any())
        verify(first, never()).setCancelled(anyBoolean())
        reset(manager)
        val next = interact()
        listener.onInteract(next)
        verify(next).setUseItemInHand(Event.Result.DENY)
    }

    @Test fun `outside managed metadata no restriction including matching prefix`() {
        `when`(world.name).thenReturn("my_world.unmanaged")
        val event = interact()
        listener.onInteract(event)
        verify(manager).callEvent(any(MwmSpawnEggScopeEvent::class.java))
        verify(event, never()).setUseItemInHand(any())
    }

    @Test fun `world key identifies managed world when folder name differs`() {
        `when`(world.name).thenReturn("main-world-folder")
        `when`(repository.findByWorldKey("minecraft:unmanaged")).thenReturn(mock(WorldData::class.java))
        val event = interact()
        listener.onInteract(event)
        verify(event).setUseItemInHand(Event.Result.DENY)
    }

    @Test fun `ordinary spawner edit allowed to operator without clearing protection`() {
        `when`(player.isOp).thenReturn(true)
        val event = interact()
        val block = mock(Block::class.java)
        `when`(block.type).thenReturn(Material.SPAWNER)
        `when`(event.clickedBlock).thenReturn(block)
        listener.onInteract(event)
        verify(event, never()).setUseInteractedBlock(any())
        verify(event, never()).setUseItemInHand(any())
        verify(event, never()).setCancelled(anyBoolean())
    }

    @Test fun `egg permission alone does not allow spawner edits`() {
        `when`(player.hasPermission(SpawnEggRestrictionListener.PERMISSION)).thenReturn(true)
        val event = interact()
        val block = mock(Block::class.java)
        `when`(block.type).thenReturn(Material.SPAWNER)
        `when`(event.clickedBlock).thenReturn(block)
        listener.onInteract(event)
        verify(event).setUseItemInHand(Event.Result.DENY)
    }

    @Test fun `integration can still deny a net spawner edit by operator`() {
        `when`(player.isOp).thenReturn(true)
        doAnswer { invocation -> (invocation.arguments[0] as MwmSpawnEggUseEvent).deny("net"); null }
            .`when`(manager).callEvent(any(MwmSpawnEggUseEvent::class.java))
        val event = interact()
        val block = mock(Block::class.java)
        `when`(block.type).thenReturn(Material.SPAWNER)
        `when`(event.clickedBlock).thenReturn(block)
        listener.onInteract(event)
        verify(event).setUseItemInHand(Event.Result.DENY)
    }

    @Test fun `additional world scope gates eggs but leaves natural spawning alone`() {
        `when`(world.name).thenReturn("extra-world")
        doAnswer { invocation ->
            (invocation.arguments[0] as? MwmSpawnEggScopeEvent)?.requireCheck()
            null
        }.`when`(manager).callEvent(any(Event::class.java))
        val event = interact()
        listener.onInteract(event)
        verify(event).setUseItemInHand(Event.Result.DENY)
        val spawn = mock(CreatureSpawnEvent::class.java)
        `when`(spawn.spawnReason).thenReturn(CreatureSpawnEvent.SpawnReason.NATURAL)
        `when`(spawn.location).thenReturn(Location(world, 0.0, 64.0, 0.0))
        listener.onNaturalSpawn(spawn)
        verify(spawn, never()).setCancelled(anyBoolean())
    }

    @Test fun `dispenser egg cancels before consumption`() {
        val event = mock(BlockDispenseEvent::class.java)
        val block = mock(Block::class.java)
        `when`(event.block).thenReturn(block)
        `when`(block.world).thenReturn(world)
        `when`(event.item).thenReturn(item)
        listener.onDispense(event)
        verify(event).setCancelled(true)
        verify(event, never()).setItem(any())
    }

    @Test fun `entity targeted eggs denied from off hand`() {
        val event = mock(PlayerInteractEntityEvent::class.java)
        val inventory = mock(PlayerInventory::class.java)
        `when`(player.inventory).thenReturn(inventory)
        `when`(inventory.getItem(EquipmentSlot.OFF_HAND)).thenReturn(item)
        `when`(event.player).thenReturn(player)
        `when`(event.hand).thenReturn(EquipmentSlot.OFF_HAND)
        listener.onEntityInteract(event)
        verify(event).setCancelled(true)
    }

    @Test fun `natural spawns blocked but commands plugins eggs and spawners untouched`() {
        for (reason in listOf(CreatureSpawnEvent.SpawnReason.NATURAL, CreatureSpawnEvent.SpawnReason.CHUNK_GEN,
            CreatureSpawnEvent.SpawnReason.COMMAND, CreatureSpawnEvent.SpawnReason.CUSTOM,
            CreatureSpawnEvent.SpawnReason.SPAWNER_EGG, CreatureSpawnEvent.SpawnReason.SPAWNER)) {
            val event = mock(CreatureSpawnEvent::class.java)
            `when`(event.spawnReason).thenReturn(reason)
            `when`(event.location).thenReturn(Location(world, 0.0, 64.0, 0.0))
            listener.onNaturalSpawn(event)
            if (reason == CreatureSpawnEvent.SpawnReason.NATURAL || reason == CreatureSpawnEvent.SpawnReason.CHUNK_GEN)
                verify(event).setCancelled(true)
            else verify(event, never()).setCancelled(anyBoolean())
        }
    }

    @Test fun `deny on extension query is sticky`() {
        val event = MwmSpawnEggUseEvent(interact(), world, player, EquipmentSlot.HAND,
            item, MwmSpawnEggUseEvent.Operation.PLAYER_USE)
        event.deny("deny")
        event.markPluginHandled()
        assertEquals(MwmSpawnEggUseEvent.Decision.DENY, event.decision)
    }
}
