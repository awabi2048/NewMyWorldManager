package me.awabi2048.myworldmanager.api.service

import me.awabi2048.myworldmanager.model.WorldData
import java.util.UUID

enum class ApiWorldDataState {
    AVAILABLE,
    QUARANTINED,
    NOT_FOUND,
}

interface ApiWorldRepository {

    fun findByUuid(uuid: UUID): WorldData?
    fun findByOwner(ownerUuid: UUID): List<WorldData>
    fun findByOwnerAndDisplayName(
        ownerUuid: UUID,
        worldName: String,
        excludingUuid: UUID? = null,
    ): WorldData?
    fun findByWorldName(worldName: String): WorldData?
    fun findByWorldKey(worldKey: String): WorldData?
    fun findAll(): List<WorldData>
    fun save(worldData: WorldData)
    fun delete(uuid: UUID)

    /** 有効データ・隔離データ・不存在を区別するための読み取り専用状態です。 */
    fun stateOf(uuid: UUID): ApiWorldDataState = when {
        findByUuid(uuid) != null -> ApiWorldDataState.AVAILABLE
        else -> ApiWorldDataState.NOT_FOUND
    }

    /** 実装が隔離データを追跡する場合に、その件数を公開します。 */
    fun quarantinedCount(): Int = 0

    /** 隔離データを含む表示名の予約状態を返します。 */
    fun hasDisplayNameConflict(
        ownerUuid: UUID,
        worldName: String,
        excludingUuid: UUID? = null,
    ): Boolean = findByOwnerAndDisplayName(ownerUuid, worldName, excludingUuid) != null
}
