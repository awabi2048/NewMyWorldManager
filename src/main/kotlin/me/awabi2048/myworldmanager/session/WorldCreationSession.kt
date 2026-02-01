package me.awabi2048.myworldmanager.session

import java.util.UUID

enum class WorldCreationPhase {
    NAME_INPUT,
    TYPE_SELECT,
    TEMPLATE_SELECT,
    SEED_INPUT,
    CONFIRM
}

enum class WorldCreationType {
    TEMPLATE,
    SEED,
    RANDOM
}

data class WorldCreationSession(
    val playerId: UUID,
    var phase: WorldCreationPhase = WorldCreationPhase.NAME_INPUT,
    var worldName: String? = null,
    var creationType: WorldCreationType? = null,
    var templateName: String? = null,
    var seed: Long? = null,
    var inputSeedString: String? = null,
    var isDialogMode: Boolean = false,
    var lastActivity: Long = System.currentTimeMillis()
) {
    /**
     * 最終操作時間を更新する
     */
    fun touch() {
        lastActivity = System.currentTimeMillis()
    }
}
