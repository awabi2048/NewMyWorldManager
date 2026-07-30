package me.awabi2048.myworldmanager.session

import org.bukkit.block.BlockFace
import java.util.UUID
import me.awabi2048.myworldmanager.api.service.ExpansionExecutionMode

enum class MenuExternalInput {
    NONE,
    SELECT_ICON,
    ADMIN_PLAYER_FILTER,
    RENAME_WORLD,
    CHANGE_DESCRIPTION,
    SET_ANNOUNCEMENT,
    MEMBER_INVITE,
    MANAGE_TAGS,
    SET_SPAWN,
    EXPAND_DIRECTION
}

data class SettingsSession(
    val playerUuid: UUID,
    val worldUuid: UUID,
    var action: SettingsAction,
    var expansionDirection: org.bukkit.block.BlockFace? = null,
    var tempWeather: String? = null,
    var confirmItem: org.bukkit.inventory.ItemStack? = null,
    var showBackButton: Boolean = false,
    var isAdminFlow: Boolean = false,
    var isPlayerWorldFlow: Boolean = false,
    var parentShowBackButton: Boolean = false,
    var externalInput: MenuExternalInput = MenuExternalInput.NONE,
    var externalInputExpiresAt: Long = 0L,
    var tempBiomeId: String? = null,
    var expansionCost: Int = 0,
    var expansionExecutionMode: ExpansionExecutionMode = ExpansionExecutionMode.STANDARD,
) {
    fun beginExternalInput(input: MenuExternalInput, timeoutMillis: Long = 120_000L) {
        externalInput = input
        externalInputExpiresAt = System.currentTimeMillis() + timeoutMillis
    }

    fun clearExternalInput() {
        externalInput = MenuExternalInput.NONE
        externalInputExpiresAt = 0L
    }

    fun isExternalInputActive(input: MenuExternalInput): Boolean {
        return externalInput == input &&
            (externalInputExpiresAt <= 0L || System.currentTimeMillis() <= externalInputExpiresAt)
    }

    fun isExternalInputExpired(): Boolean {
        return externalInput != MenuExternalInput.NONE &&
            externalInputExpiresAt > 0L &&
            System.currentTimeMillis() > externalInputExpiresAt
    }
}
