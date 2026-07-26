package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.GuiItemFactory
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class SpotlightRemoveConfirmGui(private val plugin: MyWorldManager) {
    fun open(player: Player, worldData: WorldData) {
        val lang = plugin.languageManager
        val info = ItemStack(Material.PAPER).apply {
            editMeta { meta ->
                meta.displayName(lang.getComponent(player, "gui.discovery.spotlight_remove_confirm.title"))
                meta.lore(
                    GuiItemFactory.menuLore(
                        lang.getMessageList(
                            player,
                            "gui.discovery.spotlight_remove_confirm.lore",
                            mapOf("world" to worldData.name),
                        ).map(GuiLoreLine::Text),
                    ),
                )
            }
        }
        plugin.confirmationMenuGui.open(
            player,
            "spotlight_remove_confirm",
            GuiHelper.inventoryTitle(lang.getComponent(player, "gui.discovery.spotlight_remove_confirm.title")),
            info,
            button(player, Material.LIME_CONCRETE, "gui.common.confirm"),
            button(player, Material.RED_CONCRETE, "gui.common.cancel"),
            onConfirm = {
                plugin.spotlightRepository.remove(worldData.uuid)
                player.sendMessage(lang.getMessage(player, "messages.spotlight_removed", mapOf("world" to worldData.name)))
                plugin.menuEntryRouter.openDiscovery(player)
            },
            onCancel = { plugin.menuEntryRouter.openDiscovery(player) },
        )
    }

    private fun button(player: Player, material: Material, key: String) =
        ItemStack(material).apply { editMeta { it.displayName(plugin.languageManager.getComponent(player, key)) } }
}
