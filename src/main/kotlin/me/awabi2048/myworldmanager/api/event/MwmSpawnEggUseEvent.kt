package me.awabi2048.myworldmanager.api.event

import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack

/**
 * A synchronous, single-operation extension point. This event never uncancels a
 * Bukkit event. An integration may take responsibility only after validating its
 * item and installing a consumption-safe veto in its own release pipeline.
 * DENY is sticky; a later listener cannot replace it with an exemption.
 */
class MwmSpawnEggUseEvent(
    val source: Event,
    val world: World,
    val player: Player?,
    val hand: EquipmentSlot?,
    item: ItemStack,
    val operation: Operation,
) : Event() {
    val item: ItemStack = item.clone()
    enum class Operation { PLAYER_USE, DISPENSE, CHANGE_SPAWNER }
    enum class Decision { DEFAULT, PLUGIN_HANDLED, DENY }
    var decision = Decision.DEFAULT
        private set
    var denialReason: String? = null
        private set

    fun deny(reason: String) {
        decision = Decision.DENY
        denialReason = reason
    }

    /** This exempts only MWM's egg permission check for [source]. */
    fun markPluginHandled() {
        if (decision != Decision.DENY) decision = Decision.PLUGIN_HANDLED
    }

    override fun getHandlers(): HandlerList = HANDLERS
    companion object {
        private val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
}
