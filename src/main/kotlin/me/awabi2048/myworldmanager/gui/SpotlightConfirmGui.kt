package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.GuiItemFactory
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class SpotlightConfirmGui(private val plugin: MyWorldManager) {
    fun open(player: Player, worldData: WorldData) {
        val lang = plugin.languageManager
        val info = ItemStack(Material.PAPER).apply {
            editMeta { meta ->
                meta.displayName(lang.getComponent(player, "gui.spotlight_confirm.title"))
                meta.lore(
                    GuiItemFactory.menuLore(
                        lang.getMessageList(
                            player,
                            "gui.spotlight_confirm.lore",
                            mapOf("world" to worldData.name),
                        ).map(GuiLoreLine::Text),
                    ),
                )
            }
        }
        plugin.confirmationMenuGui.open(
            player,
            "spotlight_confirm",
            GuiHelper.inventoryTitle(lang.getComponent(player, "gui.spotlight_confirm.title")),
            info,
            button(player, Material.LIME_CONCRETE, "gui.common.confirm"),
            button(player, Material.RED_CONCRETE, "gui.common.cancel"),
            onConfirm = {
                if (plugin.spotlightRepository.isSpotlight(worldData.uuid)) {
                    player.sendMessage(lang.getMessage(player, "error.spotlight_already_registered"))
                } else if (plugin.spotlightRepository.add(worldData.uuid)) {
                    player.sendMessage(lang.getMessage(player, "messages.spotlight_added", mapOf("world" to worldData.name)))
                } else {
                    player.sendMessage(lang.getMessage(player, "error.spotlight_limit_reached"))
                }
                plugin.menuEntryRouter.openDiscovery(player)
            },
            onCancel = { plugin.menuEntryRouter.openDiscovery(player) },
        )
    }

    private fun button(player: Player, material: Material, key: String) =
        ItemStack(material).apply { editMeta { it.displayName(plugin.languageManager.getComponent(player, key)) } }
}
