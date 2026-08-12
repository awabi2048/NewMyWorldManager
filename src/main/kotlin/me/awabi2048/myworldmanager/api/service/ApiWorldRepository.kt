package me.awabi2048.myworldmanager.api.service

import me.awabi2048.myworldmanager.model.WorldData
import java.io.File
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

    /**
     * 外部から持ち込まれる MWM メタデータを、現行スキーマかつ指定 UUID で検証します。
     * 復元処理はワールドディレクトリを変更する前にこの API を通す必要があります。
     */
    fun readCurrentDataFile(file: File, expectedUuid: UUID): WorldData =
        error("current world metadata validation is not supported by this repository")

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

    /** 隔離データを含む総数です。復旧後の作成上限超過を防ぐ予約枠として使用します。 */
    fun totalCountIncludingQuarantine(): Int = findAll().size + quarantinedCount()

    /** 所有者が判明している隔離データを含む所有数です。 */
    fun ownerCountIncludingQuarantine(ownerUuid: UUID): Int = findByOwner(ownerUuid).size
}
