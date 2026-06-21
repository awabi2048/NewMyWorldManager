package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.api.event.MwmMemberAddedEvent
import me.awabi2048.myworldmanager.api.event.MwmMemberRemovedEvent
import me.awabi2048.myworldmanager.api.event.MwmOwnerTransferredEvent
import me.awabi2048.myworldmanager.api.event.MwmWorldCreatedEvent
import me.awabi2048.myworldmanager.api.event.MwmWorldDeletedEvent
import me.awabi2048.myworldmanager.integration.WorldPermissionPolicyService
import me.awabi2048.myworldmanager.repository.WorldConfigRepository
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.world.WorldLoadEvent

class WorldPermissionPolicyListener(
    private val repository: WorldConfigRepository,
    private val policy: WorldPermissionPolicyService
) : Listener {
    @EventHandler
    fun onWorldCreated(event: MwmWorldCreatedEvent) {
        repository.findByUuid(event.worldUuid)?.let(policy::initializeWorld)
    }

    @EventHandler
    fun onWorldLoaded(event: WorldLoadEvent) {
        repository.findByWorldName(event.world.name)?.let { worldData ->
            policy.initializeDefaultsOnce(worldData)
            policy.syncParticipants(worldData)
        }
    }

    @EventHandler
    fun onMemberAdded(event: MwmMemberAddedEvent) {
        repository.findByUuid(event.worldUuid)?.let(policy::syncParticipants)
    }

    @EventHandler
    fun onMemberRemoved(event: MwmMemberRemovedEvent) {
        repository.findByUuid(event.worldUuid)?.let { policy.removeMember(it, event.memberUuid) }
    }

    @EventHandler
    fun onOwnerTransferred(event: MwmOwnerTransferredEvent) {
        repository.findByUuid(event.worldUuid)?.let { policy.transferOwner(it, event.oldOwnerUuid, event.newOwnerUuid) }
    }

    @EventHandler
    fun onWorldDeleted(event: MwmWorldDeletedEvent) {
        policy.clearWorld(event.worldName, event.participantUuids)
    }

    @EventHandler
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        repository.findByWorldName(event.player.world.name)?.let { policy.handlePlayerEnteredWorld(event.player, it) }
    }
}
