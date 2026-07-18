package me.awabi2048.myworldmanager.api.internal

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.service.ApiWorldRepository
import me.awabi2048.myworldmanager.model.WorldData
import java.util.UUID

internal class WorldRepositoryAdapter(private val plugin: MyWorldManager) : ApiWorldRepository {

    override fun findByUuid(uuid: UUID): WorldData? {
        return plugin.worldConfigRepository.findByUuid(uuid)
    }

    override fun findByOwner(ownerUuid: UUID): List<WorldData> {
        return plugin.worldConfigRepository.findByOwner(ownerUuid)
    }

    override fun findByWorldName(worldName: String): WorldData? {
        return plugin.worldConfigRepository.findByWorldName(worldName)
    }

    override fun findByWorldKey(worldKey: String): WorldData? {
        return plugin.worldConfigRepository.findByWorldKey(worldKey)
    }

    override fun findAll(): List<WorldData> {
        return plugin.worldConfigRepository.findAll()
    }

    override fun save(worldData: WorldData) {
        plugin.worldConfigRepository.save(worldData)
    }
}
