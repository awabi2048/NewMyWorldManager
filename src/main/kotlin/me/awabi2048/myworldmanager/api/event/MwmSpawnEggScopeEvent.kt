package me.awabi2048.myworldmanager.api.event

import org.bukkit.World
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/** Add-on worlds receive the egg gate only, never MWM environment/natural-spawn settings. */
class MwmSpawnEggScopeEvent(val world: World) : Event() {
    var checkRequired = false
        private set
    fun requireCheck() { checkRequired = true }
    override fun getHandlers(): HandlerList = HANDLERS
    companion object {
        private val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
}
