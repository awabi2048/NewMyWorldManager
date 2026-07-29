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
}

data class WorldSettingsRuntimeContext(
    val screen: WorldSettingsRuntimeScreen,
    val worldUuid: UUID? = null,
    val page: Int? = null,
)
