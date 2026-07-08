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

data class GuiLoreAction(val operation: String, val action: String)

object GuiLoreActions {
    fun singleClick(languageManager: LanguageManager, player: Player, action: String): GuiLoreLine.SingleAction {
        return single(
            languageManager,
            player,
            languageManager.getMessage(player, "lore.click.any"),
            action
        )
    }

    fun single(
        languageManager: LanguageManager,
        player: Player,
        operation: String,
        action: String
    ): GuiLoreLine.SingleAction {
        require(operation.isNotBlank()) { "Single action operation must not be blank" }
        require(action.isNotBlank()) { "Single action content must not be blank" }
        val resolved = languageManager.getMessage(
            player,
            "lore.action_single_with_operation",
            mapOf(
                "operation" to operation,
                "action" to action
            )
        )
        // SingleActionは操作と内容を分けて保持し、表示文だけロケール別テンプレートで解決する。
        return GuiLoreLine.SingleAction(
            operation = operation,
            action = action,
            resolvedText = resolved
        )
    }
}

private data class GuiLoreSection(
    val lines: List<GuiLoreLine>,
    val showClosingSeparator: Boolean = true,
    val isSpacer: Boolean = false,
)

class GuiLoreBuilder(
    private val languageManager: LanguageManager,
    private val player: Player,
) {
    private val sections = mutableListOf<GuiLoreSection>()

    private fun addSection(lines: List<GuiLoreLine>, showClosingSeparator: Boolean = true) {
        if (lines.isNotEmpty()) {
            sections += GuiLoreSection(lines, showClosingSeparator)
        }
    }

    fun line(line: String): GuiLoreBuilder {
        val normalized = line.trim()
        if (normalized.isNotEmpty()) {
            addSection(listOf(GuiLoreLine.Raw(normalized)))
        }
        return this
    }

    fun block(lines: List<String>): GuiLoreBuilder {
        val normalized = lines
            .flatMap { it.split('\n') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { raw -> if (raw.startsWith("§")) GuiLoreLine.Raw(raw) else GuiLoreLine.Text(raw) }
        addSection(normalized)
        return this
    }

    fun componentBlock(lines: List<Component>): GuiLoreBuilder {
        addSection(lines.map { component ->
            if (component == Component.empty()) GuiLoreLine.Spacer
            else GuiLoreLine.Raw(guiLegacySerializer.serialize(component))
        })
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

    fun actions(action: String): GuiLoreBuilder {
        joinActionWithPreviousContent()
        addSection(listOf(GuiLoreActions.singleClick(languageManager, player, action)), false)
        return this
    }

    /**
     * 操作案内の件数を一箇所で判定し、単一操作と複数操作の表示規則を統一する。
     */
    fun actions(actions: List<GuiLoreAction>): GuiLoreBuilder {
        if (actions.isEmpty()) return this

        joinActionWithPreviousContent()
        if (actions.size == 1) {
            val action = actions.single()
            addSection(
                listOf(GuiLoreActions.single(languageManager, player, action.operation, action.action)),
                false
            )
        } else {
            addSection(actions.map { GuiLoreLine.Action(it.operation, it.action) })
        }
        return this
    }

    private fun joinActionWithPreviousContent() {
        if (sections.isNotEmpty() && !sections.last().isSpacer) {
            spacer()
        }
    }

    fun warning(text: String): GuiLoreBuilder {
        val normalized = text.trim()
        if (normalized.isNotEmpty()) {
            addSection(listOf(GuiLoreLine.Warning(normalized)))
        }
        return this
    }

    fun spacer(): GuiLoreBuilder {
        sections += GuiLoreSection(emptyList(), showClosingSeparator = false, isSpacer = true)
        return this
    }

    fun build(): List<Component> {
        if (sections.isEmpty()) {
            return emptyList()
        }
        return CCSystem.getAPI().getLoreService().render(buildSpec())
    }

    /** Returns module-defined content blocks; CC-System owns every separator between them. */
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

    fun textItem(material: Material, name: String, loreLines: List<String>, tag: String? = null): ItemStack {
        return item(material, legacy(name), loreLines.map { legacy(it) }, tag)
    }

    fun item(material: Material, name: String, loreComponents: List<Component>, tag: String? = null): ItemStack {
        return item(material, legacy(name), loreComponents, tag)
    }

    fun item(material: Material, name: Component, loreComponents: List<Component>, tag: String? = null): ItemStack {
        val item = CCSystem.getAPI().getGuiElementService().item(
            GuiItemSpec(
                material = material,
                name = GuiNameSpec.Text(guiLegacySerializer.serialize(name), GuiNameStyle.DEFAULT),
                lore = GuiLoreSpec.Rich(
                    loreComponents.map { component ->
                        if (component == Component.empty()) GuiLoreLine.Spacer
                        else GuiLoreLine.Raw(guiLegacySerializer.serialize(component))
                    },
                    GuiLoreFrame.BOTH
                ),
                role = GuiElementRole.CONTENT,
                amount = 1
            )
        )
        if (tag != null) {
            ItemTag.tagItem(item, tag)
        }
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

    fun playerHead(owner: OfflinePlayer, name: String, loreComponents: List<Component>, tag: String? = null): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as? SkullMeta ?: return item
        meta.owningPlayer = owner
        meta.displayName(legacy(name))
        val lore = componentMenuLore(loreComponents)
        if (lore.isNotEmpty()) meta.lore(lore)
        item.itemMeta = meta
        if (tag != null) {
            ItemTag.tagItem(item, tag)
        }
        return item
    }

    fun playerHead(owner: OfflinePlayer, name: Component, loreComponents: List<Component>, tag: String? = null): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as? SkullMeta ?: return item
        meta.owningPlayer = owner
        meta.displayName(normalizeName(name))
        val lore = componentMenuLore(loreComponents)
        if (lore.isNotEmpty()) meta.lore(lore)
        item.itemMeta = meta
        if (tag != null) {
            ItemTag.tagItem(item, tag)
        }
        return item
    }

    fun menuLore(lines: List<String>, closingSeparator: Boolean = true): List<Component> {
        return CCSystem.getAPI().getGuiElementService().autoLore(lines, closingSeparator)
    }

    fun componentMenuLore(lines: List<Component>, closingSeparator: Boolean = true, preserveBlanks: Boolean = true): List<Component> {
        val filtered = if (preserveBlanks) lines else lines.filter { it != Component.empty() }
        return CCSystem.getAPI().getLoreService().render(
            GuiLoreSpec.Rich(
                filtered.map { component ->
                    if (component == Component.empty()) {
                        com.awabi2048.ccsystem.api.gui.GuiLoreLine.Spacer
                    } else {
                        com.awabi2048.ccsystem.api.gui.GuiLoreLine.Raw(guiLegacySerializer.serialize(component))
                    }
                },
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
