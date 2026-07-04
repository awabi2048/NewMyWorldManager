package me.awabi2048.myworldmanager.util

import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec

/**
 * Converts module-owned content and ordering into CC-System lore blocks.
 * Separator rendering intentionally remains outside MyWorldManager.
 */
object StructuredLore {
    data class SelectionOption(
        val label: String,
        val selected: Boolean,
        val selectedColor: String,
        val inactiveColor: String
    )

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

    fun selectionLines(
        options: List<SelectionOption>,
        selectedMarker: String = "§a»",
        inactiveMarker: String = "§8・",
        prefix: String = ""
    ): List<GuiLoreLine.Raw> {
        require(options.isNotEmpty()) { "Selection lore requires at least one option" }
        return options.map { option ->
            val marker = if (option.selected) selectedMarker else inactiveMarker
            val color = if (option.selected) option.selectedColor else option.inactiveColor
            GuiLoreLine.Raw("$prefix$marker $color${option.label}")
        }
    }

    /**
     * 選択中の値は一覧内のマーカーで示す。
     * 説明と選択肢を同じブロックに置き、現在値だけの行を重複させない。
     */
    fun selectionSetting(
        description: List<String>,
        options: List<SelectionOption>,
        action: List<String>
    ): GuiLoreSpec.Blocks {
        return selectionSettingWithActions(description, options, action.map(GuiLoreLine::Raw))
    }

    fun selectionSettingWithActions(
        description: List<String>,
        options: List<SelectionOption>,
        actionLines: List<GuiLoreLine>
    ): GuiLoreSpec.Blocks {
        require(description.isNotEmpty() && actionLines.isNotEmpty()) {
            "Selection setting lore requires description, options, and action content"
        }
        require(description.none(String::isBlank)) {
            "Blank selection setting lore lines must be expressed explicitly"
        }
        return GuiLoreSpec.Blocks(listOf(
            GuiLoreBlock(
                description.map(GuiLoreLine::Raw) +
                    GuiLoreLine.Spacer +
                    selectionLines(options)
            ),
            GuiLoreBlock(actionLines)
        ))
    }
}
