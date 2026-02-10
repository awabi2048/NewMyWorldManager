package me.awabi2048.myworldmanager.service

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.event.MwmMemberAddSource
import me.awabi2048.myworldmanager.api.event.MwmMemberAddedEvent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class MemberRequestInfo(
    val requestorUuid: UUID,
    val worldUuid: UUID,
    val ownerUuid: UUID,
    val expiryTime: Long
)

class MemberRequestManager(private val plugin: MyWorldManager) {
    private val pendingRequests = ConcurrentHashMap<String, MemberRequestInfo>()
    private val requestTimeout = 60000L // 60 seconds
    private val languageManager = plugin.languageManager

    fun addRequest(requestorUuid: UUID, worldUuid: UUID, ownerUuid: UUID): String {
        val key = generateKey(requestorUuid, worldUuid)
        val expiryTime = System.currentTimeMillis() + requestTimeout
        val info = MemberRequestInfo(requestorUuid, worldUuid, ownerUuid, expiryTime)
        pendingRequests[key] = info
        return key
    }

    fun getRequest(key: String): MemberRequestInfo? {
        val info = pendingRequests[key] ?: return null
        if (System.currentTimeMillis() > info.expiryTime) {
            pendingRequests.remove(key)
            return null
        }
        return info
    }

    fun removeRequest(key: String) {
        pendingRequests.remove(key)
    }

    fun sendRequest(requestor: Player, worldUuid: UUID) {
        val worldData = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return
        val ownerUuid = worldData.owner
        
        // Check if already a member
        if (ownerUuid == requestor.uniqueId || worldData.moderators.contains(requestor.uniqueId) || worldData.members.contains(requestor.uniqueId)) {
            requestor.sendMessage(languageManager.getMessage(requestor, "messages.member_request_already_member"))
            return
        }

        val owner = Bukkit.getPlayer(ownerUuid)
        if (owner == null || !owner.isOnline) {
            requestor.sendMessage(languageManager.getMessage(requestor, "messages.member_request_owner_offline"))
            return
        }

        // Add request to pending
        val key = addRequest(requestor.uniqueId, worldUuid, ownerUuid)

        // Notify requestor
        requestor.sendMessage(languageManager.getMessage(requestor, "messages.member_request_sent"))

        // Notify owner with clickable components
        val message = languageManager.getComponent(owner, "messages.member_request_received", mapOf("player" to requestor.name))
        owner.sendMessage(message)

        val approveText = languageManager.getComponent(owner, "messages.member_request_click_approve")
            .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/mwm_internal memberrequest $key approve"))
            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(languageManager.getComponent(owner, "messages.member_request_hover_approve")))

        val rejectText = languageManager.getComponent(owner, "messages.member_request_click_reject")
            .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/mwm_internal memberrequest $key reject"))
            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(languageManager.getComponent(owner, "messages.member_request_hover_reject")))

        owner.sendMessage(net.kyori.adventure.text.Component.text("").append(approveText).append(net.kyori.adventure.text.Component.text(" ")).append(rejectText))
        plugin.soundManager.playActionSound(owner, "member_request", "received")
    }

    fun handleApproval(player: Player, requestorUuid: UUID, worldUuid: UUID) {
        val key = generateKey(requestorUuid, worldUuid)
        val request = getRequest(key)

        if (request == null || System.currentTimeMillis() > request.expiryTime) {
            player.sendMessage(languageManager.getMessage(player, "messages.member_request_expired"))
            removeRequest(key)
            return
        }

        val worldData = plugin.worldConfigRepository.findByUuid(worldUuid)
        if (worldData == null) {
            player.sendMessage(languageManager.getMessage(player, "error.invite_world_not_found"))
            removeRequest(key)
            return
        }

        // Check if requestor is already a member/moderator/owner
        if (worldData.members.contains(requestorUuid) ||
            worldData.moderators.contains(requestorUuid) ||
            worldData.owner == requestorUuid) {
            player.sendMessage(languageManager.getMessage(player, "error.invite_already_member"))
            removeRequest(key)
            return
        }

        // Add member
        worldData.members.add(requestorUuid)
        plugin.worldConfigRepository.save(worldData)
        Bukkit.getPluginManager().callEvent(
            MwmMemberAddedEvent(
                worldUuid = worldData.uuid,
                memberUuid = requestorUuid,
                memberName = Bukkit.getPlayer(requestorUuid)?.name ?: requestorUuid.toString(),
                addedByUuid = player.uniqueId,
                source = MwmMemberAddSource.REQUEST_APPROVE
            )
        )

        // Execute macro
        plugin.macroManager.execute("on_member_add", mapOf(
            "member" to (Bukkit.getPlayer(requestorUuid)?.name ?: requestorUuid.toString()),
            "world_name" to worldData.name,
            "world_uuid" to worldData.uuid.toString()
        ))

        // Notify requestor
        val requestor = Bukkit.getPlayer(requestorUuid)
        if (requestor != null && requestor.isOnline) {
            requestor.sendMessage(languageManager.getMessage(requestor, "messages.member_request_approved"))
            plugin.soundManager.playActionSound(requestor, "member_request", "approved")
        }

        // Notify owner
        player.sendMessage(languageManager.getMessage(player, "messages.invite_accepted_sender",
            mapOf("player" to (requestor?.name ?: requestorUuid.toString()), "world" to worldData.name)))
        plugin.soundManager.playActionSound(player, "member_request", "approved")

        removeRequest(key)
    }

    fun handleRejection(player: Player, requestorUuid: UUID, worldUuid: UUID) {
        val key = generateKey(requestorUuid, worldUuid)
        val request = getRequest(key)

        if (request == null || System.currentTimeMillis() > request.expiryTime) {
            player.sendMessage(languageManager.getMessage(player, "messages.member_request_expired"))
            removeRequest(key)
            return
        }

        // Notify requestor
        val requestor = Bukkit.getPlayer(requestorUuid)
        if (requestor != null && requestor.isOnline) {
            requestor.sendMessage(languageManager.getMessage(requestor, "messages.member_request_rejected"))
            plugin.soundManager.playActionSound(requestor, "member_request", "rejected")
        }

        // Notify owner
        plugin.soundManager.playActionSound(player, "member_request", "rejected")

        removeRequest(key)
    }

    fun handleInternalCommand(player: Player, key: String, type: String) {
        val request = getRequest(key)
        if (request == null) {
            player.sendMessage(languageManager.getMessage(player, "messages.member_request_expired"))
            return
        }

        if (type == "approve" || type == "reject") {
            // オーナーがアクションを起こした際、beta機能の設定に応じてDialogかGUIを選択
            val titleText = languageManager.getMessage(player, "gui.member_request_owner_confirm.title")
            val bodyLines = languageManager.getMessageList(
                player,
                "gui.member_request_owner_confirm.lore",
                mapOf("player" to (Bukkit.getPlayer(request.requestorUuid)?.name ?: "Unknown"))
            )

            me.awabi2048.myworldmanager.gui.DialogConfirmManager.showConfirmationByPreference(
                player,
                plugin,
                net.kyori.adventure.text.Component.text(titleText),
                bodyLines.map { net.kyori.adventure.text.Component.text(it) },
                "mwm:confirm/member_request_owner_approve/$key",
                "mwm:confirm/member_request_owner_reject/$key",
                confirmText = languageManager.getMessage(player, "gui.member_request_owner_confirm.confirm"),
                cancelText = languageManager.getMessage(player, "gui.member_request_owner_confirm.reject")
            ) {
                plugin.memberRequestOwnerConfirmGui.open(player, request, key)
            }
        }
    }

    private fun generateKey(requestorUuid: UUID, worldUuid: UUID): String {
        return "${requestorUuid}_${worldUuid}"
    }
}
