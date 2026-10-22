package me.awabi2048.myworldmanager.api.service

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 安全な状態が保証できないワールドのロードを一時的に拒否する登録簿です。
 *
 * 復元処理の途中で失敗したワールドは、中途半端な世代のディレクトリのまま残ります。
 * そのままロード・編集を許すと、次回起動時のジャーナル復旧で編集内容が失われます。
 * アドオンは永続化された未完了記録（ジャーナル等）から登録し、復旧が完了した時点で
 * 解除します。
 *
 * 登録はプロセス内だけの状態です。未完了記録そのものは各アドオン側で永続化しており、
 * 再起動のたびに再登録される前提です。
 */
internal object WorldLoadBlocks {
    private val reasons = ConcurrentHashMap<UUID, String>()

    fun block(worldUuid: UUID, reason: String) {
        reasons[worldUuid] = reason
    }

    fun unblock(worldUuid: UUID) {
        reasons.remove(worldUuid)
    }

    fun reason(worldUuid: UUID): String? = reasons[worldUuid]

    fun blockedWorlds(): Set<UUID> = reasons.keys.toSet()

    fun clear() = reasons.clear()
}
