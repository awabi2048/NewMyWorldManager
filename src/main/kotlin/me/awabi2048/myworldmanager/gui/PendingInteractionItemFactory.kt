package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.api.localization.generated.CommonKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldGuiCommonKeys
import com.awabi2048.ccsystem.api.localization.LocalizationKey

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.service.PendingDecisionManager
import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiMenuActionIntent
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryData
import com.awabi2048.ccsystem.api.gui.GuiMenuEntrySpec
import com.awabi2048.ccsystem.api.gui.GuiMenuDisplaySpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiValueTone
import com.awabi2048.ccsystem.api.gui.MenuGesture
import com.awabi2048.ccsystem.api.gui.MenuActionSafety
import com.awabi2048.ccsystem.api.gui.MenuElement
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.entity.Player
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
        simplified: Boolean = false,
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
            simplified = simplified,
        ),
    )

    fun createDisplay(
        plugin: MyWorldManager,
        viewer: Player,
        subjectUuid: UUID,
        type: PendingDecisionManager.PendingType,
        worldName: String,
        createdAt: Long,
        actionMode: PendingInteractionActionMode,
    ): GuiMenuDisplaySpec {
        val entry = createSpec(
                plugin = plugin,
                viewer = viewer,
                slot = 0,
                subjectUuid = subjectUuid,
                type = type,
                worldName = worldName,
                createdAt = createdAt,
                actionMode = actionMode,
                actionId = "pending_interaction",
                showAction = false,
            )
        return GuiMenuDisplaySpec(
            slot = 0,
            item = GuiItemSpec(
                entry.material,
                entry.name,
                GuiLoreSpec.Blocks(
                    listOf(GuiLoreBlock(
                        entry.data.map { GuiLoreLine.Data(it.label, it.value, it.tone.colorCode) },
                    )),
                ),
                GuiElementRole.CONTENT,
                entry.amount,
            ),
            glint = entry.glint,
            playerHeadOwner = entry.playerHeadOwner,
        )
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
        simplified: Boolean = false,
    ): GuiMenuEntrySpec {
        val lang = plugin.languageManager
        val subject = Bukkit.getOfflinePlayer(subjectUuid)
        val subjectName = PlayerNameUtil.getNameOrDefault(subjectUuid, lang.getMessage(viewer, CommonKeys.GENERAL_UNKNOWN))
        val isOnline = subject.isOnline
        // PaperのlastSeenは環境依存のため、Bukkit標準のlastPlayed（epoch ms、不明時0）で最終オンラインを表します。
        val lastSeenMillis = subject.lastPlayed
        return GuiMenuEntrySpec(
            slot = slot,
            material = org.bukkit.Material.PLAYER_HEAD,
            name = if (simplified) {
                // メンバー管理の招待中エントリでは種別が文脈で自明なため、プレイヤー名のみを表示します。
                GuiNameSpec.TargetIdentity(
                    lang.getComponent(
                        viewer,
                        MyworldGuiCommonKeys.GUI_PENDING_LIST_ITEM_NAME_SIMPLE,
                        mapOf("player" to subjectName),
                    ),
                )
            } else {
                GuiNameSpec.TargetIdentity(
                    lang.getComponent(
                        viewer,
                        MyworldGuiCommonKeys.GUI_PENDING_LIST_ITEM_NAME,
                        mapOf("player" to subjectName, "type" to typeLabel(plugin, viewer, type)),
                    ),
                )
            },
            role = if (showAction) GuiElementRole.ACTION else GuiElementRole.CONTENT,
            data = buildList {
                if (!simplified) {
                    add(GuiMenuEntryData(lang.getMessage(viewer, MyworldGuiCommonKeys.GUI_PENDING_LIST_ITEM_TYPE_LABEL), typeLabel(plugin, viewer, type), GuiValueTone.PRIMARY))
                    add(GuiMenuEntryData(lang.getMessage(viewer, MyworldGuiCommonKeys.GUI_PENDING_LIST_ITEM_WORLD_LABEL), worldName, GuiValueTone.SUCCESS))
                }
                add(GuiMenuEntryData(
                    lang.getMessage(viewer, MyworldGuiCommonKeys.GUI_PENDING_LIST_ITEM_STATUS_LABEL),
                    lang.getMessage(viewer, if (isOnline) MyworldGuiCommonKeys.GUI_PENDING_LIST_ITEM_STATUS_ONLINE else MyworldGuiCommonKeys.GUI_PENDING_LIST_ITEM_STATUS_OFFLINE),
                ))
                // オフラインの相手には最終オンライン日時を併記します。
                if (!isOnline && lastSeenMillis > 0L) {
                    add(GuiMenuEntryData(
                        lang.getMessage(viewer, MyworldGuiCommonKeys.GUI_PENDING_LIST_ITEM_LAST_SEEN_LABEL),
                        formatDateTime(plugin, viewer, lastSeenMillis),
                        GuiValueTone.MUTED,
                    ))
                }
                add(GuiMenuEntryData(lang.getMessage(viewer, MyworldGuiCommonKeys.GUI_PENDING_LIST_ITEM_RECEIVED_LABEL), formatDateTime(plugin, viewer, createdAt)))
            },
            actions = if (showAction) {
                listOf(
                    menuGestureAction(
                        actionId,
                        // 左クリックのみだと統合版や操作体系によって反応しないことがあるため、左右クリックで受理します。
                        MenuGesture.PLAIN_LEFT_RIGHT,
                        lang.getMessage(viewer, actionLineKey(actionMode, type), mapOf("type" to typeLabel(plugin, viewer, type))),
                        actionPayload,
                        safety = MenuActionSafety.INPUT_OR_EXTERNAL_SURFACE,
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
    ): LocalizationKey<String> {
        return when (actionMode) {
            PendingInteractionActionMode.REVIEW -> MyworldGuiCommonKeys.GUI_PENDING_LIST_ITEM_ACTION_REVIEW
            PendingInteractionActionMode.CANCEL -> {
                if (type == PendingDecisionManager.PendingType.MEMBER_REQUEST) {
                    MyworldGuiCommonKeys.GUI_PENDING_LIST_ITEM_ACTION_REVIEW_REQUEST
                } else {
                    MyworldGuiCommonKeys.GUI_PENDING_LIST_ITEM_ACTION_CANCEL
                }
            }
        }
    }

    private fun typeLabel(plugin: MyWorldManager, viewer: Player, type: PendingDecisionManager.PendingType): String {
        return when (type) {
            PendingDecisionManager.PendingType.WORLD_INVITE -> plugin.languageManager.getMessage(viewer, MyworldGuiCommonKeys.GUI_PENDING_LIST_TYPE_WORLD_INVITE)
            PendingDecisionManager.PendingType.MEMBER_INVITE -> plugin.languageManager.getMessage(viewer, MyworldGuiCommonKeys.GUI_PENDING_LIST_TYPE_MEMBER_INVITE)
            PendingDecisionManager.PendingType.MEMBER_REQUEST -> plugin.languageManager.getMessage(viewer, MyworldGuiCommonKeys.GUI_PENDING_LIST_TYPE_MEMBER_REQUEST)
            PendingDecisionManager.PendingType.MEET_REQUEST -> plugin.languageManager.getMessage(viewer, MyworldGuiCommonKeys.GUI_PENDING_LIST_TYPE_MEET_REQUEST)
            PendingDecisionManager.PendingType.VISIT_REQUEST -> plugin.languageManager.getMessage(viewer, MyworldGuiCommonKeys.GUI_PENDING_LIST_TYPE_VISIT_REQUEST)
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
