package me.awabi2048.myworldmanager.api.extension

import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuRoute
import java.util.UUID
import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack

data class PlayerWorldCapabilityContext(
    val player: Player,
    val targetPlayerUuid: UUID,
    val targetPlayerName: String?,
)

data class PlayerWorldCreationView(
    val item: ItemStack,
    val actionable: Boolean,
)

interface PlayerWorldCapability {
    fun getId(): String

    fun renderWorldItem(
        context: PlayerWorldCapabilityContext,
        worldData: WorldData,
    ): ItemStack?

    fun renderSummaryItem(context: PlayerWorldCapabilityContext): ItemStack?

    fun renderCreationItem(context: PlayerWorldCapabilityContext): PlayerWorldCreationView?

    fun handleCreate(
        context: PlayerWorldCapabilityContext,
        click: ClickType,
        returnRoute: MenuRoute,
    ): MenuActionResult?

    fun handleWorld(
        context: PlayerWorldCapabilityContext,
        click: ClickType,
        worldData: WorldData,
    ): MenuActionResult?
}
