package me.awabi2048.myworldmanager.util

import kotlin.math.floor
import org.bukkit.Location

/** Border geometry is independent of the +0.5 offset used to land a player. */
object WorldBorderCoordinates {
    fun fromSpawn(spawn: Location): Location = spawn.clone().also {
        it.x = spawn.blockX.toDouble()
        it.z = spawn.blockZ.toDouble()
    }

    fun shift(center: Location, x: Double, z: Double): Location =
        center.clone().add(x, 0.0, z)

    /** Old spawn-based centers were at n+0.5; integer centers from expansion stay intact. */
    internal fun legacyHistoryCoordinate(value: Double): Double =
        if (value - floor(value) == 0.5) floor(value) else value
}
