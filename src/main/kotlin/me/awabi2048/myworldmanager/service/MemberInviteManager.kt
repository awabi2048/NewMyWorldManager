package me.awabi2048.myworldmanager.service

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.repository.WorldConfigRepository
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class MemberInviteInfo(
    val worldUuid: UUID,
    val senderUuid: UUID,
    val expiry: Long
)

class MemberInviteManager(
    private val plugin: MyWorldManager,
    private val worldConfigRepository: WorldConfigRepository,
    private val macroManager: MacroManager
) {
    private val pendingInvites = ConcurrentHashMap<UUID, MemberInviteInfo>()
    private val languageManager = plugin.languageManager

    fun addInvite(targetUuid: UUID, worldUuid: UUID, senderUuid: UUID, timeoutSeconds: Long) {
        val expiry = System.currentTimeMillis() + (timeoutSeconds * 1000)
        pendingInvites[targetUuid] = MemberInviteInfo(worldUuid, senderUuid, expiry)
    }

    fun getInvite(targetUuid: UUID): MemberInviteInfo? {
        val info = pendingInvites[targetUuid] ?: return null
        if (System.currentTimeMillis() > info.expiry) {
            pendingInvites.remove(targetUuid)
            return null
        }
        return info
    }

    fun removeInvite(targetUuid: UUID) {
        pendingInvites.remove(targetUuid)
    }

    fun handleMemberInviteAccept(player: Player) {
        val lang = languageManager
        val info = getInvite(player.uniqueId)
        if (info == null) {
            player.sendMessage(lang.getMessage(player, "error.invite_expired"))
            return
        }
        removeInvite(player.uniqueId)

        val worldData = worldConfigRepository.findByUuid(info.worldUuid)
        if (worldData == null) {
            player.sendMessage(lang.getMessage(player, "error.invite_world_not_found"))
            return
        }

        if (worldData.members.contains(player.uniqueId) || worldData.moderators.contains(player.uniqueId) || worldData.owner == player.uniqueId) {
            player.sendMessage(lang.getMessage(player, "error.invite_already_member"))
            return
        }

        worldData.members.add(player.uniqueId)
        worldConfigRepository.save(worldData)
        player.sendMessage(lang.getMessage(player, "messages.invite_accepted_self", mapOf("world" to worldData.name)))
        
        val sender = org.bukkit.Bukkit.getPlayer(info.senderUuid)
        sender?.sendMessage(lang.getMessage(sender, "messages.invite_accepted_sender", mapOf("player" to player.name, "world" to worldData.name)))

        // マクロ実行
        macroManager.execute("on_member_add", mapOf(
            "world_uuid" to info.worldUuid.toString(),
            "member" to player.name
        ))
    }
}
