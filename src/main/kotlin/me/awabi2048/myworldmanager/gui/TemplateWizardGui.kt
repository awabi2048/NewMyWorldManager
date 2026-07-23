package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.ui.ManagedMenuPresenter

import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.*
import me.awabi2048.myworldmanager.repository.*
import me.awabi2048.myworldmanager.util.CustomItem
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.GuiItemFactory
import me.awabi2048.myworldmanager.util.GuiLoreBuilder
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class TemplateWizardGui(private val plugin: MyWorldManager) {

    private val menuId = "template_wizard"
    private val sessions = ConcurrentHashMap<UUID, WizardSession>()

    data class WizardSession(
        val sourceWorldName: String,
        val sourceWorldKey: String,
        var id: String = "",
        var name: String = "",
        var description: List<String> = emptyList(),
        var icon: Material = Material.GRASS_BLOCK,
        var originLocation: org.bukkit.Location? = null,
        var inputState: InputState = InputState.NONE
    )

    enum class InputState {
        NONE, NAME, DESCRIPTION
    }

    fun open(player: Player) {
        val session = sessions.getOrPut(player.uniqueId) {
            WizardSession(
                sourceWorldName = player.world.name,
                sourceWorldKey = player.world.key.toString()
            )
        }
        val lang = plugin.languageManager
        val title = lang.getMessage(player, "gui.template_wizard.title")
        val settingsLayout = GuiHelper.settingsLayout()
        val titleComponent = GuiHelper.inventoryTitle(title)
        GuiHelper.playMenuOpen(player, menuId)
        val inventory = Bukkit.createInventory(player, settingsLayout.size, titleComponent)

        // Template editing is a settings-style screen; use the shared frame before placing wizard controls.
        GuiItemFactory.applyStandardFrame(inventory)

        val statusItem = createSettingItem(
            Material.FILLED_MAP,
            lang.getMessage(player, "gui.template_wizard.status.display"),
            GuiLoreBuilder(lang, player)
                .data(
                    lang.getMessage(player, "gui.template_wizard.status.source_world"),
                    session.sourceWorldKey
                )
                .data(
                    lang.getMessage(player, "gui.template_wizard.status.template_id"),
                    session.id.ifEmpty { lang.getMessage(player, "general.unknown") }
                )
                .data(
                    lang.getMessage(player, "gui.template_wizard.status.spawn"),
                    session.originLocation?.let {
                        "(${it.blockX}, ${it.blockY}, ${it.blockZ})"
                    } ?: lang.getMessage(player, "general.unknown")
                )
                .buildSpec(),
            ItemTag.TYPE_GUI_INFO
        )
        inventory.setItem(13, statusItem)

        // ID & Name (IDはNameの英字版などにする想定)
        val nameItem = createSettingItem(
            plugin.menuConfigManager.getIconMaterial(menuId, "name_input", Material.NAME_TAG),
            lang.getMessage(player, "gui.template_wizard.name_input.display"),
            GuiLoreBuilder(lang, player).block(lang.getMessageList(
                player,
                "gui.template_wizard.name_input.description",
                mapOf(
                    "name" to (if (session.name.isEmpty()) "未設定" else session.name),
                    "id" to (if (session.id.isEmpty()) "未設定" else session.id)
                )
            ).map(GuiLoreLine::Text)).actions(lang.getMessage(player, "gui.template_wizard.name_input.action")).buildSpec(),
            "name_input"
        )
        // Description
        val descItem = createSettingItem(
            plugin.menuConfigManager.getIconMaterial(menuId, "desc_input", Material.WRITABLE_BOOK),
            lang.getMessage(player, "gui.template_wizard.desc_input.display"),
            GuiLoreBuilder(lang, player).block(lang.getMessageList(
                player,
                "gui.template_wizard.desc_input.description",
                mapOf("desc" to session.description.joinToString("\n") { "§f  - $it" })
            ).map(GuiLoreLine::Text)).actions(lang.getMessage(player, "gui.template_wizard.desc_input.action")).buildSpec(),
            "desc_input"
        )
        // Icon
        val iconItem = createSettingItem(
            session.icon,
            lang.getMessage(player, "gui.template_wizard.icon_select.display"),
            GuiLoreBuilder(lang, player).block(lang.getMessageList(
                player,
                "gui.template_wizard.icon_select.description",
                mapOf("icon" to session.icon.name)
            ).map(GuiLoreLine::Text)).actions(lang.getMessage(player, "gui.template_wizard.icon_select.action")).buildSpec(),
            "icon_select"
        )
        GuiHelper.setThreeChoiceItems(inventory, nameItem, descItem, iconItem)

        // Origin
        val originItem = createSettingItem(
            plugin.menuConfigManager.getIconMaterial(menuId, "origin_set", Material.COMPASS),
            lang.getMessage(player, "gui.template_wizard.origin_set.display"),
            GuiLoreBuilder(lang, player).block(lang.getMessageList(
                player,
                "gui.template_wizard.origin_set.description",
                mapOf("origin" to (if (session.originLocation == null) "未設定" else "${session.originLocation!!.blockX}, ${session.originLocation!!.blockY}, ${session.originLocation!!.blockZ}"))
            ).map(GuiLoreLine::Text)).actions(lang.getMessage(player, "gui.template_wizard.origin_set.action")).buildSpec(),
            "origin_set"
        )
        inventory.setItem(31, originItem)

        // Save
        val missing = buildList {
            if (session.id.isEmpty() || session.name.isEmpty()) {
                add(lang.getMessage(player, "gui.template_wizard.requirement.name"))
            }
            if (session.originLocation == null) {
                add(lang.getMessage(player, "gui.template_wizard.requirement.spawn"))
            }
            if (session.originLocation?.world?.name != session.sourceWorldName) {
                add(lang.getMessage(player, "gui.template_wizard.requirement.source_world"))
            }
        }
        if (missing.isEmpty()) {
            val saveItem = createSettingItem(
                plugin.menuConfigManager.getIconMaterial(menuId, "save_confirm", Material.NETHER_STAR),
                lang.getMessage(player, "gui.template_wizard.save_confirm.display"),
                GuiLoreBuilder(lang, player)
                    .block(lang.getMessageList(player, "gui.template_wizard.save_confirm.description").map(GuiLoreLine::Text))
                    .actions(lang.getMessage(player, "gui.template_wizard.save_confirm.action"))
                    .buildSpec(),
                "save_confirm"
            )
            inventory.setItem(40, saveItem)
        } else {
            val warningItem = createSettingItem(
                Material.BARRIER,
                lang.getMessage(player, "gui.template_wizard.requirement.display"),
                GuiLoreBuilder(lang, player)
                    .block(missing.map(GuiLoreLine::Warning))
                    .buildSpec(),
                ItemTag.TYPE_GUI_INFO
            )
            inventory.setItem(40, warningItem)
        }

        inventory.setItem(
            39,
            createSettingItem(
                Material.SPYGLASS,
                lang.getMessage(player, "gui.template_wizard.validate.display"),
                GuiLoreBuilder(lang, player)
                    .actions(lang.getMessage(player, "gui.template_wizard.validate.action"))
                    .buildSpec(),
                "wizard_validate"
            )
        )

        inventory.setItem(
            49,
            createSettingItem(
                Material.RED_CONCRETE,
                lang.getMessage(player, "gui.template_wizard.cancel.display"),
                GuiLoreBuilder(lang, player)
                    .actions(lang.getMessage(player, "gui.template_wizard.cancel.action"))
                    .buildSpec(),
                "wizard_cancel"
            )
        )

        ManagedMenuPresenter.open(player, inventory)

        // サウンド

    }

    private fun createSettingItem(material: Material, display: String, lore: GuiLoreSpec, id: String): ItemStack {
        return GuiItemFactory.item(material, display, lore, id)
    }

    fun getSession(uuid: UUID) = sessions[uuid]
    fun removeSession(uuid: UUID) = sessions.remove(uuid)
    fun clearAll() = sessions.clear()
}
