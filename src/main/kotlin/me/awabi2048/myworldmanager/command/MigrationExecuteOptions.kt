package me.awabi2048.myworldmanager.command

/** `/mwm migration execute` の許可済みフラグだけを厳格に解釈します。 */
internal data class MigrationExecuteOptions(
    val force: Boolean,
    val confirmed: Boolean,
) {
    companion object {
        fun parse(arguments: List<String>): MigrationExecuteOptions? {
            val normalized = arguments.map(String::lowercase)
            if (normalized.any { it != FORCE && it != CONFIRM }) return null
            if (normalized.count { it == FORCE } > 1 || normalized.count { it == CONFIRM } > 1) return null
            return MigrationExecuteOptions(FORCE in normalized, CONFIRM in normalized)
        }

        private const val FORCE = "--force"
        private const val CONFIRM = "confirm"
    }
}
