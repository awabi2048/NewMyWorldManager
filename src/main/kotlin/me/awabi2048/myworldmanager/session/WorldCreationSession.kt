package me.awabi2048.myworldmanager.session

import me.awabi2048.myworldmanager.api.service.WorldPointBillingMode
import org.bukkit.World
import java.util.UUID

enum class WorldCreationPhase {
    NAME_INPUT,
    TYPE_SELECT,
    TEMPLATE_SELECT,
    TEMPLATE_DETAIL,
    SEED_INPUT,
    SPAWN_INPUT,
    CONFIRM
}

enum class WorldCreationType {
    TEMPLATE,
    SEED,
    RANDOM
}

data class WorldCreationSession(
    val playerId: UUID,
    var phase: WorldCreationPhase = WorldCreationPhase.TYPE_SELECT,
    var worldName: String? = null,
    var creationType: WorldCreationType? = null,
    var templateId: String? = null,
    var inputSeedString: String? = null,
    var seedEnvironment: World.Environment = World.Environment.NORMAL,
    var spawnCoordinates: WorldSpawnCoordinates? = null,
    var isDialogMode: Boolean = false,
    var billingMode: WorldPointBillingMode = WorldPointBillingMode.STANDARD,
    val extras: MutableMap<String, Any?> = mutableMapOf(),
    var lastActivity: Long = System.currentTimeMillis()
) {
    fun immutableSnapshot(): WorldCreationSessionSnapshot {
        val copiedExtras = extras.mapValues { (key, value) ->
            when (value) {
                null, is String, is Boolean, is Int, is Long, is Double, is Float, is UUID -> value
                else -> throw IllegalArgumentException("unsupported creation session extra '$key': ${value.javaClass.name}")
            }
        }
        return WorldCreationSessionSnapshot(
            playerId, phase, worldName, creationType, templateId, inputSeedString, seedEnvironment,
            spawnCoordinates?.copy(), isDialogMode, billingMode, copiedExtras, lastActivity,
        )
    }

    /**
     * 最終操作時間を更新する
     */
    fun touch() {
        lastActivity = System.currentTimeMillis()
    }
}

data class WorldCreationSessionSnapshot(
    val playerId: UUID,
    val phase: WorldCreationPhase,
    val worldName: String?,
    val creationType: WorldCreationType?,
    val templateId: String?,
    val inputSeedString: String?,
    val seedEnvironment: World.Environment,
    val spawnCoordinates: WorldSpawnCoordinates?,
    val isDialogMode: Boolean,
    val billingMode: WorldPointBillingMode,
    val extras: Map<String, Any?>,
    val lastActivity: Long,
) {
    fun restore(): WorldCreationSession = WorldCreationSession(
        playerId, phase, worldName, creationType, templateId, inputSeedString, seedEnvironment,
        spawnCoordinates?.copy(), isDialogMode, billingMode, extras.toMutableMap(), lastActivity,
    )
}
