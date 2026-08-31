package me.awabi2048.myworldmanager.service

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * プレイヤー単位で、まだ完了していないワープ要求を一つだけ保持します。
 *
 * ワールドのロード待ち中はプレイヤーが元のポータル上に残るため、ポータルの定期処理が
 * 同じワープを再要求できます。要求時点で予約し、テレポートの成否が確定した時点で解放
 * することで、遅延中の二重実行を共通の境界で防ぎます。
 */
internal class WarpRequestGate {
    private val pending = ConcurrentHashMap<UUID, Lease>()

    /** 同じプレイヤーの実行中ワープがなければ、無効化可能なLeaseを返します。 */
    fun tryAcquire(playerUuid: UUID, worldUuid: UUID): Lease? {
        val lease = Lease(playerUuid, worldUuid, UUID.randomUUID())
        return if (pending.putIfAbsent(playerUuid, lease) == null) lease else null
    }

    /** 古い処理の解放が後から到着しても、新しいLeaseを消さないよう値一致で解放します。 */
    fun release(lease: Lease) {
        pending.remove(lease.playerUuid, lease)
    }

    /** プラグイン停止時に、キャンセルされた遅延タスクの状態を破棄します。 */
    fun clear() {
        pending.clear()
    }

    internal data class Lease(
        val playerUuid: UUID,
        val worldUuid: UUID,
        val token: UUID,
    )
}
