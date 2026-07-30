package me.awabi2048.myworldmanager.api.extension

import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import java.util.UUID
import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.entity.Player

data class MemberManagementCapabilityContext(
    val player: Player,
    val worldData: WorldData,
    val memberUuid: UUID,
)

data class MemberManagementCapabilityView(
    val detailLines: List<GuiLoreLine>,
    val actionLines: List<GuiLoreLine>,
)

interface MemberManagementCapability {
    fun getId(): String

    fun resolve(context: MemberManagementCapabilityContext): MemberManagementCapabilityView?

    fun handlePrimaryAction(context: MemberManagementCapabilityContext): MenuActionResult
}
