package me.awabi2048.myworldmanager.util

import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec

/**
 * Converts module-owned content and ordering into CC-System lore blocks.
 * CC-System draws the outer frame and ordinary boundary blank lines; explicit separators draw middle rules.
 */
object StructuredLore {
    data class SelectionOption(
        val label: String,
        val selected: Boolean,
        val selectedColor: String,
        val inactiveColor: String
    )

    fun blocks(vararg contentBlocks: List<GuiLoreLine>): GuiLoreSpec.Blocks {
        require(contentBlocks.isNotEmpty()) { "At least one lore block is required" }
        return GuiLoreSpec.Blocks(
            contentBlocks.map { lines ->
                require(lines.isNotEmpty()) { "Lore blocks must be omitted instead of left empty" }
                GuiLoreBlock(lines)
            }
        )
    }

    /**
     * Settings keep their explanation and current value in one visual block.
     * MWM supplies the spacer as layout data; CC-System draws the outer frame and ordinary boundary blank lines.
     */
    fun setting(description: List<GuiLoreLine>, current: List<GuiLoreLine>, action: List<GuiLoreLine>): GuiLoreSpec.Blocks {
        require(description.isNotEmpty() && current.isNotEmpty() && action.isNotEmpty()) {
            "Setting lore requires description, current value, and action content"
        }
        return GuiLoreSpec.Blocks(listOf(
            GuiLoreBlock(
                description +
                    GuiLoreLine.Spacer +
                    current +
                    action
            )
        ))
    }

    fun selectionLines(
        options: List<SelectionOption>,
        selectedMarker: String = "§a»",
        inactiveMarker: String = "§8・",
        prefix: String = ""
    ): List<GuiLoreLine.Option> {
        require(options.isNotEmpty()) { "Selection lore requires at least one option" }
        return options.map { option ->
            GuiLoreLine.Option(
                label = "$prefix${option.label}",
                selected = option.selected,
                selectedColor = option.selectedColor,
                inactiveColor = option.inactiveColor
            )
        }
    }

    /**
     * 選択中の値は一覧内のマーカーで示す。
     * 説明と選択肢を同じブロックに置き、現在値だけの行を重複させない。
     */
    fun selectionSetting(
        description: List<GuiLoreLine>,
        options: List<SelectionOption>,
        action: List<GuiLoreLine>
    ): GuiLoreSpec.Blocks {
        return selectionSettingWithActions(description, options, action)
    }

    fun selectionSettingWithActions(
        description: List<GuiLoreLine>,
        options: List<SelectionOption>,
        actionLines: List<GuiLoreLine>
    ): GuiLoreSpec.Blocks {
        require(description.isNotEmpty() && actionLines.isNotEmpty()) {
            "Selection setting lore requires description, options, and action content"
        }
        return GuiLoreSpec.Blocks(listOf(
            GuiLoreBlock(
                description +
                    GuiLoreLine.Spacer +
                    selectionLines(options)
            ),
            GuiLoreBlock(actionLines)
        ))
    }
}
