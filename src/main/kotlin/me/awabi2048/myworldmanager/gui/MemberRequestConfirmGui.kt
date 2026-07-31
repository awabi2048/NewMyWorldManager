package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.MenuSoundPolicy
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.util.GuiHelper
import org.bukkit.Material
import org.bukkit.entity.Player
import me.awabi2048.myworldmanager.util.GuiSpecFactory

class MemberRequestConfirmGui(private val plugin: MyWorldManager) {
    fun open(player: Player, worldData: WorldData) {
        val lang = plugin.languageManager
        val worldName = lang.getMessageStrict(player, worldData.name) ?: worldData.name
        val worldItem = GuiHelper.createContextWorldIconItem(
            plugin,
            player,
            worldData,
            GuiLoreSpec.Rich(
                lang.getMessageList(
                    player,
                    "gui.member_request_confirm.lore",
                    mapOf("world" to worldName),
                ).map(GuiLoreLine::Warning),
                GuiLoreFrame.BOTH,
            ),
        )
        val confirmLabel = lang.getMessage(player, "gui.member_request_confirm.confirm")
        val cancelLabel = lang.getMessage(player, "gui.member_request_confirm.cancel")
        val confirmItem = GuiSpecFactory.spec(Material.LIME_CONCRETE, confirmLabel, GuiLoreSpec.None, GuiElementRole.CONFIRM)
        val cancelItem = GuiSpecFactory.spec(Material.RED_CONCRETE, cancelLabel, GuiLoreSpec.None, GuiElementRole.CANCEL)
        plugin.confirmationMenuGui.open(
            player = player,
            menuId = "member_request",
            title = GuiHelper.inventoryTitle(lang.getMessage(player, "gui.member_request_confirm.title")),
            centerItem = worldItem,
            confirmItem = confirmItem,
            cancelItem = cancelItem,
            confirmActionText = confirmLabel,
            cancelActionText = cancelLabel,
            onConfirm = {
                plugin.memberRequestManager.sendRequest(player, worldData.uuid)
                MenuActionResult.Success(MenuUpdate.Close)
            },
            onCancel = {
                plugin.soundManager.playActionSound(player, "member_request", "cancel")
                MenuActionResult.Success(MenuUpdate.Back)
            },
            cancelSound = MenuSoundPolicy.Silent,
        )
    }
}
