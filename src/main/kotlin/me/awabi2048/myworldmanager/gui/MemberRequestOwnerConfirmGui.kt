package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuSoundPolicy
import com.awabi2048.ccsystem.CCSystem
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.service.MemberRequestInfo
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.GuiItemFactory
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class MemberRequestOwnerConfirmGui(private val plugin: MyWorldManager) {
    fun open(player: Player, info: MemberRequestInfo, key: String) {
        val route = prepareOpen(player, info, key) ?: return
        CCSystem.getAPI().getMenuRuntimeService().navigate(player, route)
    }

    fun prepareOpen(player: Player, info: MemberRequestInfo, key: String): MenuRoute? {
        val decisionId = runCatching { java.util.UUID.fromString(key) }.getOrNull() ?: return null
        val lang = plugin.languageManager
        val infoItem = ItemStack(Material.PAPER).apply {
            editMeta { meta ->
                meta.displayName(lang.getComponent(player, "gui.member_request_owner_confirm.title"))
                meta.lore(
                    GuiItemFactory.menuLore(
                        lang.getMessageList(
                            player,
                            "gui.member_request_owner_confirm.lore",
                            mapOf(
                                "player" to PlayerNameUtil.getNameOrDefault(
                                    info.requestorUuid,
                                    lang.getMessage(player, "general.unknown"),
                                ),
                            ),
                        ).map(GuiLoreLine::Text),
                    ),
                )
            }
        }
        val approveItem = ItemStack(Material.LIME_CONCRETE).apply {
            editMeta { it.displayName(lang.getComponent(player, "gui.member_request_owner_confirm.confirm")) }
        }
        val rejectItem = ItemStack(Material.RED_CONCRETE).apply {
            editMeta { it.displayName(lang.getComponent(player, "gui.member_request_owner_confirm.reject")) }
        }
        return plugin.confirmationMenuGui.prepareOpen(
            player = player,
            menuId = "member_request_owner_confirm",
            title = GuiHelper.inventoryTitle(lang.getComponent(player, "gui.member_request_owner_confirm.title")),
            centerItem = infoItem,
            confirmItem = approveItem,
            cancelItem = rejectItem,
            onConfirm = { resolve(player, decisionId, true) },
            onCancel = { resolve(player, decisionId, false) },
            returnOnConfirm = true,
            cancelSound = MenuSoundPolicy.Silent,
        )
    }

    private fun resolve(player: Player, decisionId: java.util.UUID, accepted: Boolean) {
        plugin.pendingDecisionManager.resolvePersistentById(player, decisionId, accepted)
        if (!accepted) plugin.soundManager.playActionSound(player, "member_request", "rejected")
    }
}
