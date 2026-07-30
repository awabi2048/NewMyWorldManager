package me.awabi2048.myworldmanager.api.extension

data class WorldSettingsNavigationRequest(
    val showBackButton: Boolean = true,
    val isAdminFlow: Boolean = false,
    val isPlayerWorldFlow: Boolean? = null,
    val parentShowBackButton: Boolean? = null,
)
