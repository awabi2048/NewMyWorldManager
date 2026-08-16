package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.api.localization.generated.CommonKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldGuiDiscoveryKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldMessagesKeys

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
            lang.getComponent(player, MyworldGuiDiscoveryKeys.GUI_SPOTLIGHT_CONFIRM_TITLE),
            me.awabi2048.myworldmanager.util.semanticLore(
                lang.getMessageList(player, MyworldGuiDiscoveryKeys.GUI_SPOTLIGHT_CONFIRM_LORE, mapOf("world" to worldData.name))
                    .map(::warningLine),
                GuiLoreFrame.BOTH,
            ),
        )
        val confirmLabel = lang.getMessage(player, CommonKeys.GUI_COMMON_CONFIRM)
        val cancelLabel = lang.getMessage(player, CommonKeys.GUI_COMMON_CANCEL)
        plugin.confirmationMenuGui.open(
            player,
            "spotlight_confirm",
            GuiHelper.inventoryTitle(lang.getComponent(player, MyworldGuiDiscoveryKeys.GUI_SPOTLIGHT_CONFIRM_TITLE)),
            info,
            GuiSpecFactory.spec(Material.LIME_CONCRETE, confirmLabel, GuiLoreSpec.None, GuiElementRole.CONFIRM),
            GuiSpecFactory.spec(Material.RED_CONCRETE, cancelLabel, GuiLoreSpec.None, GuiElementRole.CANCEL),
            confirmLabel,
            cancelLabel,
            onConfirm = {
                if (plugin.spotlightRepository.isSpotlight(worldData.uuid)) {
                    player.sendMessage(lang.getMessage(player, CommonKeys.ERROR_SPOTLIGHT_ALREADY_REGISTERED))
                } else if (plugin.spotlightRepository.add(worldData.uuid)) {
                    player.sendMessage(lang.getMessage(player, MyworldMessagesKeys.MESSAGES_SPOTLIGHT_ADDED, mapOf("world" to worldData.name)))
                } else {
                    player.sendMessage(lang.getMessage(player, CommonKeys.ERROR_SPOTLIGHT_LIMIT_REACHED))
                }
                MenuActionResult.Success(MenuUpdate.Back)
            },
        )
    }

}
