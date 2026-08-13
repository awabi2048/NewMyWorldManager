package me.awabi2048.myworldmanager.api.service

import java.util.UUID

/**
 * MyWorldManager が管理する保存データの移行処理を、/mwm migration に参加させるための境界です。
 *
 * 参加プラグインは onEnable 中に保存データを書き換えず、status で未移行を報告し、
 * migrate が呼ばれた時だけ永続データを更新します。
 */
interface ApiMigrationParticipant {
    fun getId(): String

    /**
     * プレイヤー・ワールドなど対象単位で状態を持つ参加者だけtrueを返します。
     * グローバル設定や集約ファイルはfalseのままとし、健全な対象への操作を
     * 不要に全体停止させないための分類です。
     */
    fun targetKind(): ApiMigrationTargetKind? = null

    fun isTargetScoped(): Boolean = targetKind() != null

    fun status(): ApiMigrationParticipantStatus

    /**
     * 対象単位の状態を返します。対象外の参加者はnullを返します。
     * 既存の外部参加者が再コンパイルなしで動作できるよう、既定実装を持たせます。
     */
    fun statusFor(targetUuid: UUID): ApiMigrationParticipantStatus? = null

}

enum class ApiMigrationTargetKind {
    WORLD,
    PLAYER,
}

enum class ApiMigrationParticipantState {
    CURRENT,
    PENDING,
    FAILED,
}

data class ApiMigrationParticipantStatus(
    val id: String,
    val state: ApiMigrationParticipantState,
    val message: String? = null,
)

/** 移行判定がどの種類のデータをブロックしたかを呼出側へ返します。 */
data class ApiMigrationBlock(
    val participantId: String,
    val state: ApiMigrationParticipantState,
    val targetUuid: UUID? = null,
    val message: String,
)

data class ApiMigrationPreflight(
    val blocks: List<ApiMigrationBlock>,
) {
    val allowed: Boolean get() = blocks.isEmpty()
}

enum class ApiMigrationParticipantResultState {
    MIGRATED,
    ALREADY_CURRENT,
    UNRESOLVED,
    /** 26.812系参加者とのバイナリ互換用。新規実装はUNRESOLVEDを返します。 */
    @Deprecated("Use UNRESOLVED")
    NEEDS_INPUT,
    FAILED,
}

data class ApiMigrationParticipantResult(
    val state: ApiMigrationParticipantResultState,
    val message: String,
)
