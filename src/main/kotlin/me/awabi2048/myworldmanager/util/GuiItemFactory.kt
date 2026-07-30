package me.awabi2048.myworldmanager.util

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiFrameSection
import com.awabi2048.ccsystem.api.gui.GuiFrameSpec
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiNameStyle
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.meta.SkullMeta

private val guiLegacySerializer = LegacyComponentSerializer.legacySection()

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

object GuiItemFactory {

    private fun decorationSpec(material: Material): GuiItemSpec = GuiItemSpec(
        material = material,
        name = GuiNameSpec.Empty,
        lore = GuiLoreSpec.None,
        role = GuiElementRole.DECORATION,
        amount = 1
    )

    fun applyStandardFrame(
        inventory: Inventory,
        frameMaterial: Material = Material.BLACK_STAINED_GLASS_PANE,
        emptyMaterial: Material? = Material.GRAY_STAINED_GLASS_PANE
    ) {
        val frame = decorationSpec(frameMaterial)
        CCSystem.getAPI().getGuiElementService().applyFrame(
            inventory,
            GuiFrameSpec(
                header = GuiFrameSection.Row(frame),
                footer = GuiFrameSection.Row(frame),
                emptySlot = emptyMaterial?.let(::decorationSpec)
            )
        )
    }

    fun fillEmpty(inventory: Inventory, material: Material = Material.GRAY_STAINED_GLASS_PANE) {
        CCSystem.getAPI().getGuiElementService().fillEmpty(inventory, decorationSpec(material))
    }

    fun decoration(material: Material, tag: String = ItemTag.TYPE_GUI_DECORATION): ItemStack {
        val item = CCSystem.getAPI().getGuiElementService().item(decorationSpec(material))
        ItemTag.tagItem(item, tag)
        return item
    }

    fun item(material: Material, name: Component, lore: GuiLoreSpec, tag: String? = null): ItemStack {
        val item = CCSystem.getAPI().getGuiElementService().item(
            GuiItemSpec(
                material = material,
                name = GuiNameSpec.Text(guiLegacySerializer.serialize(name), GuiNameStyle.DEFAULT),
                lore = lore,
                role = GuiElementRole.CONTENT,
                amount = 1
            )
        )
        if (tag != null) {
            ItemTag.tagItem(item, tag)
        }
        return item
    }

    fun item(material: Material, name: String, lore: GuiLoreSpec, tag: String? = null): ItemStack {
        return item(material, legacy(name), lore, tag)
    }

    fun playerHead(owner: OfflinePlayer, name: Component, lore: GuiLoreSpec, tag: String? = null): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as? SkullMeta ?: return item
        meta.owningPlayer = owner
        meta.displayName(normalizeName(name))
        val renderedLore = CCSystem.getAPI().getLoreService().render(lore)
        if (renderedLore.isNotEmpty()) meta.lore(renderedLore)
        item.itemMeta = meta
        if (tag != null) {
            ItemTag.tagItem(item, tag)
        }
        return item
    }

    fun menuLore(lines: List<GuiLoreLine>, closingSeparator: Boolean = true): List<Component> {
        return CCSystem.getAPI().getLoreService().render(
            GuiLoreSpec.Rich(
                lines,
                if (closingSeparator) GuiLoreFrame.BOTH else GuiLoreFrame.TOP
            )
        )
    }

    fun legacy(raw: String): Component {
        return guiLegacySerializer.deserialize(raw)
            .let(::normalizeName)
    }

    fun normalizeName(component: Component): Component {
        return component
            .colorIfAbsent(NamedTextColor.WHITE)
            .decoration(TextDecoration.ITALIC, false)
    }
}

private fun legacy(raw: String): Component {
    return guiLegacySerializer.deserialize(raw)
        .let(GuiItemFactory::normalizeName)
}
