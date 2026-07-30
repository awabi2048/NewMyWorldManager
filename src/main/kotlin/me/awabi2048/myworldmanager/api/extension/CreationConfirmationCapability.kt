package me.awabi2048.myworldmanager.api.extension

import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuCapabilityPresentation
import org.bukkit.entity.Player

interface WorldCreationDraft {
    val worldName: String?

    fun getBoolean(key: String): Boolean?

    fun setBoolean(key: String, value: Boolean)
}

data class CreationConfirmationCapabilityContext(
    val player: Player,
    val draft: WorldCreationDraft,
)

data class CreationConfirmationCapabilityView(
    val presentation: MenuCapabilityPresentation,
)

interface CreationConfirmationCapability {
    fun getId(): String

    fun resolve(
        context: CreationConfirmationCapabilityContext,
    ): CreationConfirmationCapabilityView?

    fun handlePrimaryAction(
        context: CreationConfirmationCapabilityContext,
    ): MenuActionResult
}
