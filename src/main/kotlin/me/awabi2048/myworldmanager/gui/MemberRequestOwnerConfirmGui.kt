package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.api.localization.generated.CommonKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldGuiMeetKeys

import me.awabi2048.myworldmanager.util.descriptionLine
import me.awabi2048.myworldmanager.util.warningLine
import me.awabi2048.myworldmanager.util.dangerLine

import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuSoundPolicy
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import com.awabi2048.ccsystem.CCSystem
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.service.MemberRequestInfo
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.GuiSpecFactory
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import org.bukkit.Material
import org.bukkit.entity.Player

class MemberRequestOwnerConfirmGui(private val plugin: MyWorldManager) {
    fun open(player: Player, info: MemberRequestInfo, key: String) {
        val route = prepareOpen(player, info, key) ?: return
        CCSystem.getAPI().getMenuRuntimeService().navigate(player, route)
    }

    fun prepareOpen(player: Player, info: MemberRequestInfo, key: String): MenuRoute? {
        val decisionId = runCatching { java.util.UUID.fromString(key) }.getOrNull() ?: return null
        val lang = plugin.languageManager
        val approveLabel = lang.getMessage(player, MyworldGuiMeetKeys.GUI_MEMBER_REQUEST_OWNER_CONFIRM_CONFIRM)
        val rejectLabel = lang.getMessage(player, MyworldGuiMeetKeys.GUI_MEMBER_REQUEST_OWNER_CONFIRM_REJECT)
        val infoItem = GuiSpecFactory.spec(
            Material.PAPER,
            lang.getComponent(player, MyworldGuiMeetKeys.GUI_MEMBER_REQUEST_OWNER_CONFIRM_TITLE),
            me.awabi2048.myworldmanager.util.semanticLore(
                lang.getMessageList(
                    player,
                    MyworldGuiMeetKeys.GUI_MEMBER_REQUEST_OWNER_CONFIRM_LORE,
                    mapOf(
                        "player" to PlayerNameUtil.getNameOrDefault(
                            info.requestorUuid,
                            lang.getMessage(player, CommonKeys.GENERAL_UNKNOWN),
                        ),
                    ),
                ).map(::descriptionLine),
                GuiLoreFrame.BOTH,
            ),
        )
        val approveItem = GuiSpecFactory.spec(Material.LIME_CONCRETE, approveLabel, GuiLoreSpec.None, GuiElementRole.CONFIRM)
        val rejectItem = GuiSpecFactory.spec(Material.RED_CONCRETE, rejectLabel, GuiLoreSpec.None, GuiElementRole.CANCEL)
        return plugin.confirmationMenuGui.prepareOpen(
            player = player,
            menuId = "member_request_owner_confirm",
            title = GuiHelper.inventoryTitle(lang.getComponent(player, MyworldGuiMeetKeys.GUI_MEMBER_REQUEST_OWNER_CONFIRM_TITLE)),
            centerItem = infoItem,
            confirmItem = approveItem,
            cancelItem = rejectItem,
            confirmActionText = approveLabel,
            cancelActionText = rejectLabel,
            onConfirm = {
                resolve(player, decisionId, true)
                MenuActionResult.Success(MenuUpdate.Back)
            },
            onCancel = {
                resolve(player, decisionId, false)
                MenuActionResult.Success(MenuUpdate.Back)
            },
            cancelSound = MenuSoundPolicy.Silent,
        )
    }

    private fun resolve(player: Player, decisionId: java.util.UUID, accepted: Boolean) {
        plugin.pendingDecisionManager.resolvePersistentById(player, decisionId, accepted)
        if (!accepted) plugin.soundManager.playActionSound(player, "member_request", "rejected")
    }
}
