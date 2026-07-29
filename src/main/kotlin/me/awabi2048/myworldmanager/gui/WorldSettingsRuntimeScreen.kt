package me.awabi2048.myworldmanager.gui

import java.util.UUID

/**
 * Runtime route が表示している画面と、その画面を再描画するための最小コンテキストです。
 *
 * SettingsSession は Inventory 画面の識別には使用しません。外部入力やワールド上の操作中に
 * 必要な状態だけを保持します。
 */
enum class WorldSettingsRuntimeScreen {
    WORLD_SETTINGS,
    ICON_SELECTION,
    MEMBER_MANAGEMENT,
    MEMBER_PENDING_INVITE_CANCEL_CONFIRM,
    MEMBER_REMOVE_CONFIRM,
    MEMBER_TRANSFER_CONFIRM,
    VISITOR_MANAGEMENT,
    VISITOR_KICK_CONFIRM,
    EXPANSION_METHOD_SELECTION,
    EXPANSION_CONFIRM,
    EXPANSION_STEP_BACK_CONFIRM,
    CRITICAL_SETTINGS,
    RESET_EXPANSION_CONFIRM,
    RESET_EXPANSION_SPAWN_UNSAFE_CONFIRM,
    DELETE_WORLD_CONFIRM,
    DELETE_WORLD_FINAL_CONFIRM,
    ARCHIVE_CONFIRM,
    UNARCHIVE_CONFIRM,
    PORTAL_MANAGEMENT,
    ARCHIVE_FROM_CRITICAL_CONFIRM,
}

data class WorldSettingsRuntimeContext(
    val screen: WorldSettingsRuntimeScreen,
    val worldUuid: UUID? = null,
    val page: Int? = null,
    val targetUuid: UUID? = null,
    val decisionId: UUID? = null,
)
