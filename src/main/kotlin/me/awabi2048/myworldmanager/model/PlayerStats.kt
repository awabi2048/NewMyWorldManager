package me.awabi2048.myworldmanager.model

import java.util.UUID

/**
 * プレイヤーの統計情報と個人設定
 */
data class PlayerStats(
    val playerUuid: UUID,
    var worldPoint: Int = 0,
    // unlockedWarpSlot removed
    var unlockedWorldSlot: Int = 0,
    val registeredWarp: MutableList<UUID> = mutableListOf(),
    val favoriteWorlds: MutableMap<UUID, String> = mutableMapOf(), // WorldUUID to RegistrationDate (YYYY-MM-DD)
    var lastOnline: String? = null,
    var lastName: String? = null,
    var language: String = "ja_jp",
    var visitorNotificationEnabled: Boolean = true,
    var criticalSettingsEnabled: Boolean = true,
    var meetStatus: String = "JOIN_ME"
)
