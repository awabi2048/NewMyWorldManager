package me.awabi2048.myworldmanager.api.service

import me.awabi2048.myworldmanager.model.WorldData
import java.util.UUID

interface ApiWorldRepository {

    fun findByUuid(uuid: UUID): WorldData?
    fun findByOwner(ownerUuid: UUID): List<WorldData>
    fun findByWorldName(worldName: String): WorldData?
    fun findAll(): List<WorldData>
}
