package me.awabi2048.myworldmanager.api.service

import org.bukkit.block.BlockFace

enum class ExpansionSequencePhase {
    METHOD_SELECT,
    DIRECTION_SELECT,
    PREVIEW,
    CONFIRM,
    EXECUTE,
    CLEANUP
}

enum class ExpansionExecutionMode {
    STANDARD,
    IMMEDIATE_NO_COST
}

data class ExpansionSequenceOptions(
    val startPhase: ExpansionSequencePhase,
    val direction: BlockFace?,
    val executionMode: ExpansionExecutionMode,
    val skipPhases: Set<ExpansionSequencePhase>
)
