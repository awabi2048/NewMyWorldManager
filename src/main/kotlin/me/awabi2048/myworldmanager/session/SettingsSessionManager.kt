package me.awabi2048.myworldmanager.session

import org.bukkit.entity.Player
import java.util.UUID

enum class SettingsAction {
    VIEW_SETTINGS,
    RENAME_WORLD,
    CHANGE_DESCRIPTION,
    SET_SPAWN_GUEST,
    SET_SPAWN_MEMBER,
    SELECT_ICON,
    INVITE_PLAYER,
    ARCHIVE_WORLD,
    EXPAND_SELECT_METHOD,
    EXPAND_DIRECTION_WAIT,
    EXPAND_CONFIRM,
    MANAGE_MEMBERS,
    MEMBER_INVITE,
    MEMBER_REMOVE_CONFIRM,
    MANAGE_TAGS,
    MANAGE_VISITORS,
    VISITOR_KICK_CONFIRM,
    CRITICAL_SETTINGS,
    RESET_EXPANSION_CONFIRM,
    DELETE_WORLD_CONFIRM,
    DELETE_WORLD_CONFIRM_FINAL,
    SET_ANNOUNCEMENT,
    MANAGE_PORTALS,
    ADMIN_PLAYER_FILTER,
    VIEW_ENVIRONMENT_SETTINGS,
    ENV_CONFIRM,
    UNARCHIVE_CONFIRM,
    MEMBER_TRANSFER_CONFIRM,
    
    // Admin Actions
    ADMIN_MENU,
    ADMIN_CONVERT_NORMAL_CONFIRM,
    ADMIN_CONVERT_ADMIN_CONFIRM,
    ADMIN_EXPORT_CONFIRM,
    ADMIN_ARCHIVE_ALL_CONFIRM,
    ADMIN_UPDATE_DATA_CONFIRM,
    ADMIN_REPAIR_TEMPLATES_CONFIRM,
    ADMIN_PORTAL_GUI,
    ADMIN_WORLD_GUI
}


class SettingsSessionManager {
    private val sessions = mutableMapOf<UUID, SettingsSession>()

    fun startSession(player: Player, worldUuid: UUID, action: SettingsAction) {
        sessions[player.uniqueId] = SettingsSession(player.uniqueId, worldUuid, action)
    }

    fun getSession(player: Player): SettingsSession? {
        return sessions[player.uniqueId]
    }

    fun endSession(player: Player) {
        sessions.remove(player.uniqueId)
    }
    
    fun hasSession(player: Player): Boolean {
        return sessions.containsKey(player.uniqueId)
    }

    fun updateSessionAction(player: Player, worldUuid: UUID, action: SettingsAction, isGui: Boolean = false) {
        val currentSession = sessions[player.uniqueId]
        if (currentSession != null && currentSession.worldUuid == worldUuid) {
            currentSession.action = action
            if (isGui) currentSession.isGuiTransition = true
        } else {
            startSession(player, worldUuid, action)
            if (isGui) sessions[player.uniqueId]?.isGuiTransition = true
        }
    }
}
