package me.awabi2048.myworldmanager.api.extension

import com.awabi2048.ccsystem.api.gui.MenuActionResult
import me.awabi2048.myworldmanager.session.WorldCreationSession
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

data class CreationConfirmationCapabilityContext(
    val player: Player,
    val session: WorldCreationSession,
)

data class CreationConfirmationCapabilityView(
    val item: ItemStack,
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
