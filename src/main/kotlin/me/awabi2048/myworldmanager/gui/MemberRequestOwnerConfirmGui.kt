package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.MenuSoundPolicy
import com.awabi2048.ccsystem.CCSystem
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.service.MemberRequestInfo
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.GuiItemFactory
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class MemberRequestOwnerConfirmGui(private val plugin: MyWorldManager) {
    fun open(player: Player, info: MemberRequestInfo, key: String) {
        val decisionId = runCatching { java.util.UUID.fromString(key) }.getOrNull() ?: return
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
        plugin.confirmationMenuGui.open(
            player = player,
            menuId = "member_request_owner_confirm",
            title = GuiHelper.inventoryTitle(lang.getComponent(player, "gui.member_request_owner_confirm.title")),
            centerItem = infoItem,
            confirmItem = approveItem,
            cancelItem = rejectItem,
            onConfirm = { resolve(player, decisionId, true, reopen = false) },
            onCancel = { resolve(player, decisionId, false, reopen = false) },
            returnOnConfirm = true,
            cancelSound = MenuSoundPolicy.Silent,
        )
    }

    private fun resolve(player: Player, decisionId: java.util.UUID, accepted: Boolean, reopen: Boolean = true) {
        val worldUuid = plugin.pendingDecisionManager.getPendingEntry(player.uniqueId, decisionId)?.worldUuid
        plugin.pendingDecisionManager.resolvePersistentById(player, decisionId, accepted)
        if (!accepted) plugin.soundManager.playActionSound(player, "member_request", "rejected")
        if (reopen) reopenMemberManagement(player, worldUuid)
    }

    private fun reopenMemberManagement(player: Player, worldUuid: java.util.UUID?) {
        val session = plugin.settingsSessionManager.getSession(player)
        val worldData = worldUuid?.let(plugin.worldConfigRepository::findByUuid)
        if (session == null || worldData == null) {
            CCSystem.getAPI().getMenuRuntimeService().close(player)
            return
        }
        val page = (session.getMetadata("member_management_page") as? Int)?.coerceAtLeast(0) ?: 0
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (player.isOnline) plugin.worldSettingsGui.openMemberManagement(player, worldData, page, false)
        })
    }
}
