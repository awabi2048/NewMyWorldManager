package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.service.PendingDecisionManager
import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryAction
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryData
import com.awabi2048.ccsystem.api.gui.GuiMenuEntrySpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiValueTone
import com.awabi2048.ccsystem.api.gui.MenuAcceptedClicks
import com.awabi2048.ccsystem.api.gui.MenuElement
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
    fun createElement(
        plugin: MyWorldManager,
        viewer: Player,
        slot: Int,
        subjectUuid: UUID,
        type: PendingDecisionManager.PendingType,
        worldName: String,
        createdAt: Long,
        actionMode: PendingInteractionActionMode,
        actionId: String,
        actionPayload: Map<String, String>,
    ): MenuElement = CCSystem.getAPI().getGuiElementService().menuEntry(
        viewer,
        createSpec(
            plugin = plugin,
            viewer = viewer,
            slot = slot,
            subjectUuid = subjectUuid,
            type = type,
            worldName = worldName,
            createdAt = createdAt,
            actionMode = actionMode,
            actionId = actionId,
            actionPayload = actionPayload,
            showAction = true,
        ),
    )

    fun createItem(
        plugin: MyWorldManager,
        viewer: Player,
        subjectUuid: UUID,
        type: PendingDecisionManager.PendingType,
        worldName: String,
        createdAt: Long,
        decisionId: UUID,
        actionMode: PendingInteractionActionMode,
        itemTagType: String?,
        showAction: Boolean = true,
    ): ItemStack {
        val item = CCSystem.getAPI().getGuiElementService().menuEntry(
            viewer,
            createSpec(
                plugin = plugin,
                viewer = viewer,
                slot = 0,
                subjectUuid = subjectUuid,
                type = type,
                worldName = worldName,
                createdAt = createdAt,
                actionMode = actionMode,
                actionId = "pending_interaction",
                showAction = showAction,
            ),
        ).item
        if (itemTagType != null) {
            ItemTag.tagItem(item, itemTagType)
            ItemTag.setString(item, "pending_decision_id", decisionId.toString())
        }
        return item
    }

    private fun createSpec(
        plugin: MyWorldManager,
        viewer: Player,
        slot: Int,
        subjectUuid: UUID,
        type: PendingDecisionManager.PendingType,
        worldName: String,
        createdAt: Long,
        actionMode: PendingInteractionActionMode,
        actionId: String,
        actionPayload: Map<String, String> = emptyMap(),
        showAction: Boolean,
    ): GuiMenuEntrySpec {
        val lang = plugin.languageManager
        val subject = Bukkit.getOfflinePlayer(subjectUuid)
        val subjectName = PlayerNameUtil.getNameOrDefault(subjectUuid, lang.getMessage(viewer, "general.unknown"))
        return GuiMenuEntrySpec(
            slot = slot,
            material = org.bukkit.Material.PLAYER_HEAD,
            name = GuiNameSpec.Component(
                lang.getComponent(
                    viewer,
                    "gui.pending_list.item.name",
                    mapOf("player" to subjectName, "type" to typeLabel(plugin, viewer, type)),
                ),
            ),
            role = if (showAction) GuiElementRole.ACTION else GuiElementRole.CONTENT,
            data = listOf(
                GuiMenuEntryData(lang.getMessage(viewer, "gui.pending_list.item.type_label"), typeLabel(plugin, viewer, type), GuiValueTone.PRIMARY),
                GuiMenuEntryData(lang.getMessage(viewer, "gui.pending_list.item.world_label"), worldName, GuiValueTone.SUCCESS),
                GuiMenuEntryData(
                    lang.getMessage(viewer, "gui.pending_list.item.status_label"),
                    lang.getMessage(viewer, if (subject.isOnline) "gui.pending_list.item.status_online" else "gui.pending_list.item.status_offline"),
                ),
                GuiMenuEntryData(lang.getMessage(viewer, "gui.pending_list.item.received_label"), formatDateTime(plugin, viewer, createdAt)),
            ),
            actions = if (showAction) {
                listOf(
                    GuiMenuEntryAction(
                        actionId,
                        MenuAcceptedClicks.LEFT_RIGHT,
                        lang.getMessage(viewer, actionLineKey(actionMode, type), mapOf("type" to typeLabel(plugin, viewer, type))),
                        actionPayload,
                    ),
                )
            } else {
                emptyList()
            },
            glint = true,
            playerHeadOwner = subjectUuid,
        )
    }

    private fun actionLineKey(
        actionMode: PendingInteractionActionMode,
        type: PendingDecisionManager.PendingType
    ): String {
        return when (actionMode) {
            PendingInteractionActionMode.REVIEW -> "gui.pending_list.item.action_review"
            PendingInteractionActionMode.CANCEL -> {
                if (type == PendingDecisionManager.PendingType.MEMBER_REQUEST) {
                    "gui.pending_list.item.action_review_request"
                } else {
                    "gui.pending_list.item.action_cancel"
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
            PendingDecisionManager.PendingType.VISIT_REQUEST -> plugin.languageManager.getMessage(viewer, "gui.pending_list.type.visit_request")
        }
    }

    private fun formatDateTime(plugin: MyWorldManager, player: Player, timestamp: Long): String {
        val language = plugin.languageManager.resolveLocale(player).lowercase(Locale.ROOT)
        val formatter = if (language == "ja_jp") {
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
        } else {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        }
        return formatter.withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(timestamp))
    }
}
