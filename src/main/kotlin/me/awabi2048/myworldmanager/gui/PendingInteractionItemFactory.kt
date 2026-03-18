package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.service.PendingDecisionManager
import me.awabi2048.myworldmanager.util.GuiLoreBuilder
import me.awabi2048.myworldmanager.util.ItemTag
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

enum class PendingInteractionActionMode {
    REVIEW,
    CANCEL
}

object PendingInteractionItemFactory {
    fun createItem(
        plugin: MyWorldManager,
        viewer: Player,
        subjectUuid: UUID,
        type: PendingDecisionManager.PendingType,
        worldName: String,
        createdAt: Long,
        decisionId: UUID,
        actionMode: PendingInteractionActionMode,
        itemTagType: String
    ): ItemStack {
        val lang = plugin.languageManager
        val subject = Bukkit.getOfflinePlayer(subjectUuid)
        val isOnline = subject.isOnline
        val subjectName = PlayerNameUtil.getNameOrDefault(subjectUuid, lang.getMessage(viewer, "general.unknown"))
        val item = ItemStack(org.bukkit.Material.PLAYER_HEAD)
        val meta = item.itemMeta as? SkullMeta ?: return item
        meta.owningPlayer = subject
        meta.displayName(
            lang.getComponent(
                viewer,
                "gui.pending_list.item.name",
                mapOf(
                    "player" to subjectName,
                    "type" to typeLabel(plugin, viewer, type)
                )
            ).decoration(TextDecoration.ITALIC, false)
        )

        val lore = GuiLoreBuilder(lang, viewer)
            .componentBlock(
                listOf(
                    lang.getComponent(
                        viewer,
                        "gui.pending_list.item.type_line",
                        mapOf("type" to typeLabel(plugin, viewer, type))
                    ),
                    lang.getComponent(
                        viewer,
                        "gui.pending_list.item.world_line",
                        mapOf("world" to worldName)
                    ),
                    lang.getComponent(
                        viewer,
                        "gui.pending_list.item.status_line",
                        mapOf(
                            "status" to lang.getMessage(
                                viewer,
                                if (isOnline) "gui.pending_list.item.status_online" else "gui.pending_list.item.status_offline"
                            )
                        )
                    ),
                    lang.getComponent(
                        viewer,
                        "gui.pending_list.item.received_line",
                        mapOf("datetime" to formatDateTime(plugin, viewer, createdAt))
                    ),
                )
            )
            .componentBlock(
                listOf(
                    lang.getComponent(
                        viewer,
                        actionLineKey(plugin, viewer, actionMode),
                        mapOf("type" to typeLabel(plugin, viewer, type))
                    )
                )
            )
            .build()
        meta.lore(lore)
        meta.setEnchantmentGlintOverride(true)

        item.itemMeta = meta
        ItemTag.tagItem(item, itemTagType)
        ItemTag.setString(item, "pending_decision_id", decisionId.toString())
        return item
    }

    private fun actionLineKey(
        plugin: MyWorldManager,
        viewer: Player,
        actionMode: PendingInteractionActionMode
    ): String {
        return when (actionMode) {
            PendingInteractionActionMode.REVIEW -> "gui.pending_list.item.action_review"
            PendingInteractionActionMode.CANCEL -> {
                if (plugin.playerPlatformResolver.isBedrock(viewer)) {
                    "gui.pending_list.item.action_cancel_bedrock"
                } else {
                    "gui.pending_list.item.action_cancel_java"
                }
            }
        }
    }

    private fun typeLabel(plugin: MyWorldManager, viewer: Player, type: PendingDecisionManager.PendingType): String {
        return when (type) {
            PendingDecisionManager.PendingType.WORLD_INVITE -> plugin.languageManager.getMessage(viewer, "gui.pending_list.type.world_invite")
            PendingDecisionManager.PendingType.MEMBER_INVITE -> plugin.languageManager.getMessage(viewer, "gui.pending_list.type.member_invite")
            PendingDecisionManager.PendingType.MEMBER_REQUEST -> plugin.languageManager.getMessage(viewer, "gui.pending_list.type.member_request")
            PendingDecisionManager.PendingType.MEET_REQUEST -> plugin.languageManager.getMessage(viewer, "gui.pending_list.type.meet_request")
        }
    }

    private fun formatDateTime(plugin: MyWorldManager, player: Player, timestamp: Long): String {
        val language = plugin.playerStatsRepository.findByUuid(player.uniqueId).language.lowercase(Locale.ROOT)
        val formatter = if (language == "ja_jp") {
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
        } else {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        }
        return formatter.withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(timestamp))
    }
}
