package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.MenuSoundPolicy
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.util.GuiHelper
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

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
        val confirmItem = ItemStack(Material.LIME_CONCRETE).apply {
            editMeta { it.displayName(lang.getComponent(player, "gui.member_request_confirm.confirm")) }
        }
        val cancelItem = ItemStack(Material.RED_CONCRETE).apply {
            editMeta { it.displayName(lang.getComponent(player, "gui.member_request_confirm.cancel")) }
        }
        plugin.confirmationMenuGui.open(
            player = player,
            menuId = "member_request",
            title = GuiHelper.inventoryTitle(lang.getMessage(player, "gui.member_request_confirm.title")),
            centerItem = worldItem,
            confirmItem = confirmItem,
            cancelItem = cancelItem,
            onConfirm = { plugin.memberRequestManager.sendRequest(player, worldData.uuid) },
            onCancel = { plugin.soundManager.playActionSound(player, "member_request", "cancel") },
            cancelSound = MenuSoundPolicy.Silent,
        )
    }
}
