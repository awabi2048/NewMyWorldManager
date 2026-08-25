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
    MEMBER_ADD_MENU,
    TAG_EDITOR,
    MEMBER_PENDING_INVITE_CANCEL_CONFIRM,
    MEMBER_REMOVE_CONFIRM,
    MEMBER_TRANSFER_CONFIRM,
    VISITOR_MANAGEMENT,
    VISITOR_KICK_CONFIRM,
    VISITOR_INVITE_CONFIRM,
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

enum class WorldSettingsRuntimeOperation {
    BACK,
    TOUR,
    EDIT_INFO,
    SELECT_ICON,
    SET_SPAWN,
    EXPAND,
    CYCLE_PUBLISH,
    MANAGE_MEMBERS,
    EDIT_TAGS,
    EDIT_ANNOUNCEMENT,
    TOGGLE_NOTIFICATION,
    OPEN_ENVIRONMENT,
    OPEN_CRITICAL,
    WARP,
    MANAGE_VISITORS,
    MANAGE_PORTALS,
    CONFIRM,
    CANCEL,
    PAGE,
    VISITOR,
    VISITOR_INVITE,
    PORTAL,
    EXPAND_AUTOMATIC,
    EXPAND_DIRECTION,
    EXPANSION_STEP_BACK,
    RESET_EXPANSION,
    ARCHIVE,
    DELETE_WORLD,
    MEMBER_OWNER_RESET,
    MEMBER,
    PENDING_INVITE,
    PENDING_REQUEST,
    INVITE_MEMBER,
    OPEN_MEMBER_ADD,
    MEMBER_ADD_TARGET,
    MEMBER_ADD_ID_INPUT,
    TAG_TOGGLE,
}

data class WorldSettingsRuntimeContext(
    val screen: WorldSettingsRuntimeScreen,
    val worldUuid: UUID? = null,
    val page: Int? = null,
    val targetUuid: UUID? = null,
    val decisionId: UUID? = null,
    val operation: WorldSettingsRuntimeOperation? = null,
    val actionPayload: Map<String, String> = emptyMap(),
)
