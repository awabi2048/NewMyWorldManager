package me.awabi2048.myworldmanager.service

import kotlin.random.Random

class PendingActionCodeAllocator @JvmOverloads constructor(
    private val random: Random = Random.Default,
    private val randomAttempts: Int = 64
) {
    fun allocate(usedCodes: Set<String>): String? {
        if (usedCodes.size >= CODE_SPACE_SIZE) {
            return null
        }
        repeat(randomAttempts) {
            val candidate = format(random.nextInt(CODE_SPACE_SIZE))
            if (candidate !in usedCodes) {
                return candidate
            }
        }
        return (0 until CODE_SPACE_SIZE)
            .asSequence()
            .map(::format)
            .firstOrNull { it !in usedCodes }
    }

    companion object {
        const val CODE_SPACE_SIZE: Int = 10_000
        val CODE_PATTERN: Regex = Regex("^[0-9]{4}$")

        fun format(value: Int): String = value.toString().padStart(4, '0')
    }
}
