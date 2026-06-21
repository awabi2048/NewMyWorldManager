package me.awabi2048.myworldmanager.api.extension

import java.util.UUID
import me.awabi2048.myworldmanager.model.WorldData

/** Allows addons to suspend the MWM-managed work group without taking ownership of LuckPerms nodes. */
interface WorldWorkPermissionPolicy {
    fun getId(): String

    fun canAssign(worldData: WorldData, playerUuid: UUID): Boolean
}
