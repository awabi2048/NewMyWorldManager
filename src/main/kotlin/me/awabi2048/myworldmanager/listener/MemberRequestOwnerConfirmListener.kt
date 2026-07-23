package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.ui.ManagedMenuPresenter

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.gui.MemberRequestOwnerConfirmGui
import me.awabi2048.myworldmanager.util.ItemTag
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import me.awabi2048.myworldmanager.util.cancelWithDebug

class MemberRequestOwnerConfirmListener(private val plugin: MyWorldManager) : Listener {

    @EventHandler(ignoreCancelled = false)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        event.view.topInventory.holder as? MemberRequestOwnerConfirmGui.MemberRequestOwnerConfirmHolder ?: return

        event.cancelWithDebug("MemberRequestOwnerConfirmListener.onInventoryClick: member request owner confirm GUI click")
        if (event.clickedInventory != event.view.topInventory) return

        val item = event.currentItem ?: return
        val tag = ItemTag.getType(item) ?: return

        when (tag) {
            "member_request_owner_yes" -> {
                val key = ItemTag.getString(item, "key") ?: return
                val decisionId = runCatching { java.util.UUID.fromString(key) }.getOrNull() ?: return
                val worldUuid = plugin.pendingDecisionManager.getPendingEntry(player.uniqueId, decisionId)?.worldUuid
                plugin.pendingDecisionManager.resolvePersistentById(player, decisionId, true)
                plugin.soundManager.playClickSound(player, item)
                reopenMemberManagement(player, worldUuid)
            }
            "member_request_owner_no" -> {
                val key = ItemTag.getString(item, "key") ?: return
                val decisionId = runCatching { java.util.UUID.fromString(key) }.getOrNull() ?: return
                val worldUuid = plugin.pendingDecisionManager.getPendingEntry(player.uniqueId, decisionId)?.worldUuid
                plugin.pendingDecisionManager.resolvePersistentById(player, decisionId, false)
                plugin.soundManager.playActionSound(player, "member_request", "rejected")
                reopenMemberManagement(player, worldUuid)
            }
        }
    }

    @EventHandler
    fun onDialogResponse(event: io.papermc.paper.event.player.PlayerCustomClickEvent) {
        val identifierStr = event.identifier.asString()
        val conn = event.commonConnection as? io.papermc.paper.connection.PlayerGameConnection ?: return
        val player = conn.player

        if (identifierStr.startsWith("mwm:confirm/member_request_owner_approve/")) {
            me.awabi2048.myworldmanager.gui.DialogConfirmManager.safeCloseDialog(player)
            val key = identifierStr.substringAfter("mwm:confirm/member_request_owner_approve/")
            val decisionId = runCatching { java.util.UUID.fromString(key) }.getOrNull() ?: return
            val worldUuid = plugin.pendingDecisionManager.getPendingEntry(player.uniqueId, decisionId)?.worldUuid
            plugin.pendingDecisionManager.resolvePersistentById(player, decisionId, true)
            plugin.soundManager.playClickSound(player, null)
            reopenMemberManagement(player, worldUuid)
        } else if (identifierStr.startsWith("mwm:confirm/member_request_owner_reject/")) {
            me.awabi2048.myworldmanager.gui.DialogConfirmManager.safeCloseDialog(player)
            val key = identifierStr.substringAfter("mwm:confirm/member_request_owner_reject/")
            val decisionId = runCatching { java.util.UUID.fromString(key) }.getOrNull() ?: return
            val worldUuid = plugin.pendingDecisionManager.getPendingEntry(player.uniqueId, decisionId)?.worldUuid
            plugin.pendingDecisionManager.resolvePersistentById(player, decisionId, false)
            plugin.soundManager.playActionSound(player, "member_request", "rejected")
            reopenMemberManagement(player, worldUuid)
        }
    }

    /**
     * メンバー管理メニューを再描画する。
     * 承認/拒否後もメンバー管理メニューに留まるため、最新状態を反映して開き直す。
     * セッション情報が無い場合は単にインベントリを閉じる。
     */
    private fun reopenMemberManagement(player: Player, worldUuid: java.util.UUID?) {
        val session = plugin.settingsSessionManager.getSession(player)
        if (worldUuid == null || session == null) {
            ManagedMenuPresenter.close(player)
            return
        }
        val worldData = plugin.worldConfigRepository.findByUuid(worldUuid) ?: run {
            ManagedMenuPresenter.close(player)
            return
        }
        val page = (session.getMetadata("member_management_page") as? Int)?.coerceAtLeast(0) ?: 0
        ManagedMenuPresenter.close(player)
        // 承認処理でメンバーリストが変化するため、次tickで開き直して反映する。
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (player.isOnline) {
                plugin.worldSettingsGui.openMemberManagement(player, worldData, page, false)
            }
        })
    }
}
