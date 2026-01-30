package me.awabi2048.myworldmanager.session

import org.bukkit.block.BlockFace
import java.util.UUID

data class SettingsSession(
    val playerUuid: UUID,
    val worldUuid: UUID,
    var action: SettingsAction,
    var expansionDirection: org.bukkit.block.BlockFace? = null,
    var tempWeather: String? = null,
    var confirmItem: org.bukkit.inventory.ItemStack? = null,
    var isGuiTransition: Boolean = false,
    var showBackButton: Boolean = false,
    var isAdminFlow: Boolean = false
)
