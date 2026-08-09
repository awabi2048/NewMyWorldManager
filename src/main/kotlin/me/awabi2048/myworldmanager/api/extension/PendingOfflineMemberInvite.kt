package me.awabi2048.myworldmanager.api.extension

import java.util.UUID

/**
 * 対象がオフライン中に作成され、まだ処理されていないメンバー招待の公開API表現です。
 * 招待元や操作コードなど、ログイン通知の判定に不要な内部情報は公開しません。
 */
data class PendingOfflineMemberInvite(
    val id: UUID,
    val worldUuid: UUID,
    val createdAt: Long,
)
