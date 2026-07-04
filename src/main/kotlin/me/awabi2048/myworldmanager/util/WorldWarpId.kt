package me.awabi2048.myworldmanager.util

import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.UUID

object WorldWarpId {
    private val MOD = BigInteger.valueOf(1_000_000L)

    fun of(uuid: UUID): String {
        val bytes = ByteBuffer.allocate(16)
            .putLong(uuid.mostSignificantBits)
            .putLong(uuid.leastSignificantBits)
            .array()
        val value = BigInteger(1, bytes).mod(MOD).toInt()
        return value.toString().padStart(6, '0')
    }
}
