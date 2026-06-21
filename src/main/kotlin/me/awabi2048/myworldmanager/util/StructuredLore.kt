package me.awabi2048.myworldmanager.util

import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec

/**
 * Converts module-owned content and ordering into CC-System lore blocks.
 * Separator rendering intentionally remains outside MyWorldManager.
 */
object StructuredLore {
    fun blocks(vararg contentBlocks: List<String>): GuiLoreSpec.Blocks {
        require(contentBlocks.isNotEmpty()) { "At least one lore block is required" }
        return GuiLoreSpec.Blocks(
            contentBlocks.map { lines ->
                require(lines.isNotEmpty()) { "Lore blocks must be omitted instead of left empty" }
                require(lines.none(String::isBlank)) { "Blank lore lines must be expressed explicitly" }
                GuiLoreBlock(lines.map(GuiLoreLine::Raw))
            }
        )
    }

    /**
     * Settings keep their explanation and current value in one visual block.
     * The spacer is layout data supplied by MWM; CC-System still owns every separator.
     */
    fun setting(description: List<String>, current: List<String>, action: List<String>): GuiLoreSpec.Blocks {
        require(description.isNotEmpty() && current.isNotEmpty() && action.isNotEmpty()) {
            "Setting lore requires description, current value, and action content"
        }
        require((description + current + action).none(String::isBlank)) {
            "Blank setting lore lines must be expressed explicitly"
        }
        return GuiLoreSpec.Blocks(listOf(
            GuiLoreBlock(
                description.map(GuiLoreLine::Raw) +
                    GuiLoreLine.Spacer +
                    current.map(GuiLoreLine::Raw) +
                    action.map(GuiLoreLine::Raw)
            )
        ))
    }
}
