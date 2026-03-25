package me.awabi2048.myworldmanager.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta

private val guiLegacySerializer = LegacyComponentSerializer.legacySection()

data class GuiLoreAction(val operation: String? = null, val action: String)

private data class GuiLoreSection(
    val lines: List<Component>,
    val showClosingSeparator: Boolean = true,
)

class GuiLoreBuilder(
    private val languageManager: LanguageManager,
    private val player: Player,
) {
    private val sections = mutableListOf<GuiLoreSection>()

    private fun addSection(lines: List<Component>, showClosingSeparator: Boolean = true) {
        val normalized = lines.filter { it != Component.empty() }
        if (normalized.isNotEmpty()) {
            sections += GuiLoreSection(normalized, showClosingSeparator)
        }
    }

    fun line(line: String): GuiLoreBuilder {
        val normalized = line.trim()
        if (normalized.isNotEmpty()) {
            addSection(listOf(legacy(normalized)))
        }
        return this
    }

    fun block(lines: List<String>): GuiLoreBuilder {
        val normalized = lines
            .flatMap { it.split('\n') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { raw -> if (raw.startsWith("§")) legacy(raw) else legacy("§7$raw") }
        addSection(normalized)
        return this
    }

    fun componentBlock(lines: List<Component>): GuiLoreBuilder {
        addSection(lines.map { it.decoration(TextDecoration.ITALIC, false) })
        return this
    }

    fun data(name: String, value: String): GuiLoreBuilder {
        return data(listOf(name to value))
    }

    fun data(entries: List<Pair<String, String>>): GuiLoreBuilder {
        if (entries.isNotEmpty()) {
            addSection(entries.map { (name, value) -> legacy("§f§l| §7$name $value") })
        }
        return this
    }

    fun singleAction(action: String): GuiLoreBuilder {
        return rawAction(
            languageManager.getMessage(
                player,
                "gui.common.action_single",
                mapOf("action" to action)
            ),
            false
        )
    }

    fun multiActions(actions: List<GuiLoreAction>): GuiLoreBuilder {
        if (actions.isNotEmpty()) {
            addSection(
                actions.map {
                    legacy(
                        languageManager.getMessage(
                            player,
                            "gui.common.action_multi",
                            mapOf(
                                "operation" to (it.operation ?: ""),
                                "action" to it.action
                            )
                        )
                    )
                }
            )
        }
        return this
    }

    fun action(action: String): GuiLoreBuilder {
        return rawAction(action, false)
    }

    fun rawAction(text: String, showClosingSeparator: Boolean = false): GuiLoreBuilder {
        val normalized = text.trim()
        if (normalized.isNotEmpty()) {
            addSection(listOf(legacy(normalized)), showClosingSeparator)
        }
        return this
    }

    fun warning(text: String): GuiLoreBuilder {
        val normalized = text.trim()
        if (normalized.isNotEmpty()) {
            addSection(
                listOf(
                    legacy(
                        languageManager.getMessage(
                            player,
                            "gui.common.warning",
                            mapOf("content" to normalized)
                        )
                    )
                )
            )
        }
        return this
    }

    fun separator(): Component {
        return languageManager.getComponent(player, "gui.common.separator")
            .decoration(TextDecoration.ITALIC, false)
    }

    fun build(): List<Component> {
        if (sections.isEmpty()) {
            return emptyList()
        }

        val separator = separator()
        val lore = buildList {
            sections.forEach { section ->
                add(separator)
                addAll(section.lines)
            }
            if (sections.last().showClosingSeparator) {
                add(separator)
            }
        }
        return GuiHelper.cleanupLore(lore, separator)
    }
}

object GuiItemFactory {
    fun decoration(material: Material, tag: String = ItemTag.TYPE_GUI_DECORATION): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.displayName(Component.empty())
        meta.isHideTooltip = true
        item.itemMeta = meta
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
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.displayName(name.decoration(TextDecoration.ITALIC, false))
        meta.lore(loreComponents.map { it.decoration(TextDecoration.ITALIC, false) })
        item.itemMeta = meta
        if (tag != null) {
            ItemTag.tagItem(item, tag)
        }
        return item
    }

    fun playerHead(owner: OfflinePlayer, name: String, loreComponents: List<Component>, tag: String? = null): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as? SkullMeta ?: return item
        meta.owningPlayer = owner
        meta.displayName(legacy(name))
        meta.lore(loreComponents.map { it.decoration(TextDecoration.ITALIC, false) })
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
        meta.displayName(name.decoration(TextDecoration.ITALIC, false))
        meta.lore(loreComponents.map { it.decoration(TextDecoration.ITALIC, false) })
        item.itemMeta = meta
        if (tag != null) {
            ItemTag.tagItem(item, tag)
        }
        return item
    }

    fun legacy(raw: String): Component {
        return guiLegacySerializer.deserialize(raw)
            .decoration(TextDecoration.ITALIC, false)
    }
}

private fun legacy(raw: String): Component {
    return guiLegacySerializer.deserialize(raw)
        .decoration(TextDecoration.ITALIC, false)
}
