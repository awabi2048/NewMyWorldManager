package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.*
import me.awabi2048.myworldmanager.repository.*
import me.awabi2048.myworldmanager.util.CustomItem
import me.awabi2048.myworldmanager.util.GuiItemFactory
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
        val session = sessions.getOrPut(player.uniqueId) { WizardSession() }
        val lang = plugin.languageManager
        val title = lang.getMessage(player, "gui.template_wizard.title")
        val titleComponent = me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(title)
        me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(plugin, player, menuId, titleComponent, null)
        val inventory = Bukkit.createInventory(player, 54, titleComponent)

        // 装飾用
        // 装飾用
        val blackPane = GuiItemFactory.decoration(Material.BLACK_STAINED_GLASS_PANE)
        val grayPane = GuiItemFactory.decoration(Material.GRAY_STAINED_GLASS_PANE)
        
        for (i in 0..53) {
            if (i < 9 || i >= 45) {
                inventory.setItem(i, blackPane)
            } else {
                inventory.setItem(i, grayPane)
            }
        }

        // 設定項目
        // ID & Name (IDはNameの英字版などにする想定)
        val nameItem = createSettingItem(
            plugin.menuConfigManager.getIconMaterial(menuId, "name_input", Material.NAME_TAG),
            lang.getMessage(player, "gui.template_wizard.name_input.display"),
            lang.getComponentList(
                player,
                "gui.template_wizard.name_input.lore",
                mapOf(
                    "name" to (if (session.name.isEmpty()) "未設定" else session.name),
                    "id" to (if (session.id.isEmpty()) "未設定" else session.id)
                )
            ),
            "name_input"
        )
        inventory.setItem(20, nameItem)

        // Description
        val descItem = createSettingItem(
            plugin.menuConfigManager.getIconMaterial(menuId, "desc_input", Material.WRITABLE_BOOK),
            lang.getMessage(player, "gui.template_wizard.desc_input.display"),
            lang.getComponentList(
                player,
                "gui.template_wizard.desc_input.lore",
                mapOf("desc" to session.description.joinToString("\n") { "§f  - $it" })
            ),
            "desc_input"
        )
        inventory.setItem(22, descItem)

        // Icon
        val iconItem = createSettingItem(
            session.icon,
            lang.getMessage(player, "gui.template_wizard.icon_select.display"),
            lang.getComponentList(
                player,
                "gui.template_wizard.icon_select.lore",
                mapOf("icon" to session.icon.name)
            ),
            "icon_select"
        )
        inventory.setItem(24, iconItem)

        // Origin
        val originItem = createSettingItem(
            plugin.menuConfigManager.getIconMaterial(menuId, "origin_set", Material.COMPASS),
            lang.getMessage(player, "gui.template_wizard.origin_set.display"),
            lang.getComponentList(
                player,
                "gui.template_wizard.origin_set.lore",
                mapOf("origin" to (if (session.originLocation == null) "未設定" else "${session.originLocation!!.blockX}, ${session.originLocation!!.blockY}, ${session.originLocation!!.blockZ}"))
            ),
            "origin_set"
        )
        inventory.setItem(31, originItem)

        // Save
        if (session.id.isNotEmpty() && session.name.isNotEmpty() && session.originLocation != null) {
            val saveItem = createSettingItem(
                plugin.menuConfigManager.getIconMaterial(menuId, "save_confirm", Material.NETHER_STAR),
                lang.getMessage(player, "gui.template_wizard.save_confirm.display"),
                lang.getComponentList(player, "gui.template_wizard.save_confirm.lore"),
                "save_confirm"
            )
            inventory.setItem(40, saveItem)
        }

        player.openInventory(inventory)
        
        // サウンド

    }

    private fun createSettingItem(material: Material, display: String, lore: List<Component>, id: String): ItemStack {
        return GuiItemFactory.item(material, display, lore, id)
    }

    fun getSession(uuid: UUID) = sessions[uuid]
    fun removeSession(uuid: UUID) = sessions.remove(uuid)
}
