package me.awabi2048.myworldmanager.util

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiNameStyle
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player

private data class GuiLoreSection(
    val lines: List<GuiLoreLine>,
    val isSpacer: Boolean = false,
)

class GuiLoreBuilder(
    private val languageManager: LanguageManager,
    private val player: Player,
) {
    private val sections = mutableListOf<GuiLoreSection>()

    private fun addSection(lines: List<GuiLoreLine>) {
        if (lines.isNotEmpty()) {
            sections += GuiLoreSection(lines)
        }
    }

    fun block(lines: List<GuiLoreLine>): GuiLoreBuilder {
        addSection(lines)
        return this
    }

    fun data(name: String, value: String): GuiLoreBuilder {
        return data(listOf(name to value))
    }

    fun data(entries: List<Pair<String, String>>): GuiLoreBuilder {
        if (entries.isNotEmpty()) {
            addSection(entries.map { (name, value) -> GuiLoreLine.Data(name, value, "§7") })
        }
        return this
    }

    fun warning(text: String): GuiLoreBuilder {
        val normalized = text.trim()
        if (normalized.isNotEmpty()) {
            addSection(listOf(GuiLoreLine.Warning(normalized)))
        }
        return this
    }

    fun spacer(): GuiLoreBuilder {
        sections += GuiLoreSection(emptyList(), isSpacer = true)
        return this
    }

    fun build(): List<Component> {
        if (sections.isEmpty()) {
            return emptyList()
        }
        return CCSystem.getAPI().getLoreService().render(buildSpec())
    }

    /** MWM supplies content blocks; CC-System draws the outer frame and ordinary boundary blank lines, while explicit separators draw middle rules. */
    fun buildSpec(): GuiLoreSpec.Blocks {
        val blocks = mutableListOf<GuiLoreBlock>()
        var current = mutableListOf<GuiLoreLine>()
        var joinNextSection = false

        sections.forEach { section ->
            if (section.isSpacer) {
                require(current.isNotEmpty()) { "A lore spacer requires preceding content" }
                current += GuiLoreLine.Spacer
                joinNextSection = true
                return@forEach
            }

            if (current.isNotEmpty() && !joinNextSection) {
                blocks += GuiLoreBlock(current.toList())
                current = mutableListOf()
            }
            current += section.lines
            joinNextSection = false
        }

        if (current.isNotEmpty()) blocks += GuiLoreBlock(current.toList())
        return GuiLoreSpec.Blocks(blocks)
    }
}

object GuiSpecFactory {

    fun spec(
        material: Material,
        name: String,
        lore: GuiLoreSpec,
        role: GuiElementRole = GuiElementRole.CONTENT,
    ): GuiItemSpec = GuiItemSpec(
        material,
        GuiNameSpec.Text(name, GuiNameStyle.DEFAULT),
        lore,
        role,
        1,
    )

    fun spec(
        material: Material,
        name: Component,
        lore: GuiLoreSpec,
        role: GuiElementRole = GuiElementRole.CONTENT,
    ): GuiItemSpec = GuiItemSpec(
        material,
        GuiNameSpec.Component(name),
        lore,
        role,
        1,
    )

}
