package me.awabi2048.myworldmanager.util

/** シード作成地点の補正順位を Bukkit API から分離した純粋ロジック。 */
object SeedSpawnSafety {
    data class Position(val x: Int, val y: Int, val z: Int)

    fun choose(
        requested: Position,
        requestedSafe: Boolean,
        sameXzSafeY: List<Int>,
        surroundingSafe: List<Position>
    ): Position? {
        if (requestedSafe) return requested
        sameXzSafeY
            .distinct()
            .sortedWith(compareBy<Int> { kotlin.math.abs(it - requested.y) }.thenBy { it })
            .firstOrNull()?.let { return requested.copy(y = it) }
        return surroundingSafe
            .distinct()
            .filter { it != requested }
            .sortedWith(
                compareBy<Position> { squaredDistance(it, requested) }
                    .thenBy { kotlin.math.abs(it.y - requested.y) }
                    .thenBy { it.y }
                    .thenBy { it.x }
                    .thenBy { it.z }
            )
            .firstOrNull()
    }

    private fun squaredDistance(a: Position, b: Position): Long {
        val dx = a.x.toLong() - b.x
        val dy = a.y.toLong() - b.y
        val dz = a.z.toLong() - b.z
        return dx * dx + dy * dy + dz * dz
    }
}
