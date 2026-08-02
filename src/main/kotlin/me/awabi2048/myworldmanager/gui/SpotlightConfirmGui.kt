package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.util.descriptionLine
import me.awabi2048.myworldmanager.util.warningLine
import me.awabi2048.myworldmanager.util.dangerLine

import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.GuiSpecFactory
import org.bukkit.Material
import org.bukkit.entity.Player

class SpotlightConfirmGui(private val plugin: MyWorldManager) {
    fun open(player: Player, worldData: WorldData) {
        val lang = plugin.languageManager
        val info = GuiSpecFactory.spec(
            Material.PAPER,
            lang.getComponent(player, "gui.spotlight_confirm.title"),
            me.awabi2048.myworldmanager.util.semanticLore(
                lang.getMessageList(player, "gui.spotlight_confirm.lore", mapOf("world" to worldData.name))
                    .map(::warningLine),
                GuiLoreFrame.BOTH,
            ),
        )
        val confirmLabel = lang.getMessage(player, "gui.common.confirm")
        val cancelLabel = lang.getMessage(player, "gui.common.cancel")
        plugin.confirmationMenuGui.open(
            player,
            "spotlight_confirm",
            GuiHelper.inventoryTitle(lang.getComponent(player, "gui.spotlight_confirm.title")),
            info,
            GuiSpecFactory.spec(Material.LIME_CONCRETE, confirmLabel, GuiLoreSpec.None, GuiElementRole.CONFIRM),
            GuiSpecFactory.spec(Material.RED_CONCRETE, cancelLabel, GuiLoreSpec.None, GuiElementRole.CANCEL),
            confirmLabel,
            cancelLabel,
            onConfirm = {
                if (plugin.spotlightRepository.isSpotlight(worldData.uuid)) {
                    player.sendMessage(lang.getMessage(player, "error.spotlight_already_registered"))
                } else if (plugin.spotlightRepository.add(worldData.uuid)) {
                    player.sendMessage(lang.getMessage(player, "messages.spotlight_added", mapOf("world" to worldData.name)))
                } else {
                    player.sendMessage(lang.getMessage(player, "error.spotlight_limit_reached"))
                }
                MenuActionResult.Success(MenuUpdate.Back)
            },
        )
    }

}
