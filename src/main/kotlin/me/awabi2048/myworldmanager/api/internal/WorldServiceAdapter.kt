package me.awabi2048.myworldmanager.api.internal

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.service.ApiWorldService
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.CompletableFuture

internal class WorldServiceAdapter(private val plugin: MyWorldManager) : ApiWorldService {

    override fun createFromTemplate(
        templateName: String,
        ownerUuid: UUID,
        worldName: String,
        cost: Int
    ): CompletableFuture<Boolean> {
        return plugin.worldService.createWorld(templateName, ownerUuid, worldName, cost)
    }

    override fun teleportToWorld(player: Player, worldUuid: UUID) {
        plugin.worldService.teleportToWorld(player, worldUuid)
    }

    override fun loadWorld(worldUuid: UUID): Boolean {
        return plugin.worldService.loadWorld(worldUuid)
    }
}
