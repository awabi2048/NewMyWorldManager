package me.awabi2048.myworldmanager.session

data class WorldSpawnCoordinates(
    val x: Int,
    val y: Int,
    val z: Int
) {
    fun display(): String = "$x, $y, $z"

    companion object {
        const val MIN_Y = -64
        const val MAX_Y = 319
        const val MAX_HORIZONTAL = 29_999_984

        fun parse(x: String, y: String, z: String): ParseResult {
            if (x.isBlank() && y.isBlank() && z.isBlank()) {
                return ParseResult.Unset
            }
            val parsedX = x.trim().toIntOrNull()
            val parsedY = y.trim().toIntOrNull()
            val parsedZ = z.trim().toIntOrNull()
            if (parsedX == null || parsedY == null || parsedZ == null) {
                return ParseResult.InvalidNumber
            }
            if (parsedY !in MIN_Y..MAX_Y ||
                parsedX !in -MAX_HORIZONTAL..MAX_HORIZONTAL ||
                parsedZ !in -MAX_HORIZONTAL..MAX_HORIZONTAL
            ) {
                return ParseResult.OutOfRange
            }
            return ParseResult.Valid(WorldSpawnCoordinates(parsedX, parsedY, parsedZ))
        }
    }

    sealed interface ParseResult {
        data class Valid(val coordinates: WorldSpawnCoordinates) : ParseResult
        data object Unset : ParseResult
        data object InvalidNumber : ParseResult
        data object OutOfRange : ParseResult
    }
}
