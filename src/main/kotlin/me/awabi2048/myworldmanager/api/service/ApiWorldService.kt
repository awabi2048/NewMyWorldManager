package me.awabi2048.myworldmanager.api.service

import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.CompletableFuture

interface ApiWorldService {

    fun createFromTemplate(
        templateName: String,
        ownerUuid: UUID,
        worldName: String,
        cost: Int
    ): CompletableFuture<Boolean>

    fun teleportToWorld(
        player: Player,
        worldUuid: UUID
    )

    fun loadWorld(worldUuid: UUID): Boolean
}
