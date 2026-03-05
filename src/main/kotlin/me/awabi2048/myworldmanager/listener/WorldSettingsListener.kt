package me.awabi2048.myworldmanager.listener

import java.util.UUID
import java.util.Locale
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.event.MwmMemberRemoveSource
import me.awabi2048.myworldmanager.api.event.MwmMemberRemovedEvent
import me.awabi2048.myworldmanager.api.event.MwmMemberAddSource
import me.awabi2048.myworldmanager.api.event.MwmMemberAddedEvent
import me.awabi2048.myworldmanager.api.event.MwmOwnerTransferSource
import me.awabi2048.myworldmanager.api.event.MwmOwnerTransferredEvent
import me.awabi2048.myworldmanager.model.PendingInteractionType
import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.session.SettingsAction
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.ItemTag
import me.awabi2048.myworldmanager.util.PermissionManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Color
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.BlockFace
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import io.papermc.paper.event.player.PlayerCustomClickEvent
import net.kyori.adventure.key.Key
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput
import io.papermc.paper.connection.PlayerGameConnection
import net.kyori.adventure.text.format.NamedTextColor
import me.awabi2048.myworldmanager.gui.DialogConfirmManager

class WorldSettingsListener : Listener {

        private val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
        private val pendingExpansions = mutableMapOf<UUID, PendingExpansion>()
        private val spawnPreviewTasks = mutableMapOf<UUID, BukkitTask>()

        data class PendingExpansion(
                val worldData: WorldData,
                val cost: Int,
                val direction: BlockFace?,
                val task: BukkitTask
        )

        @EventHandler(ignoreCancelled = true)
        fun onInventoryClick(event: InventoryClickEvent) {
                val player = event.whoClicked as? Player ?: return
                val session = plugin.settingsSessionManager.getSession(player) ?: return


                // GUI遷移中のクリックを無視
                if (session.isGuiTransition) {

                        event.isCancelled = true

                        return
                }

                // セッションが有効な場合、基本キャンセル (各caseで解除も可能だが基本はGUI)
                // ただし、clickedInventoryチェックが必要か？
                // ここでは一旦キャンセルせずに各分岐に委ねる、あるいはトップインベントリならキャンセル？
                // 既存実装：各ifブロックで event.isCancelled = true していた。

                val item = event.currentItem ?: return
                val type = ItemTag.getType(item)

                val lang = plugin.languageManager
                val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid) ?: return

                // 蜈ｱ騾壹Γ繧ｽ繝・ラ: 繧ｭ繝｣繝ｳ繧ｻ繝ｫ縺ｨ繧ｯ繝ｪ繝・け髻ｳ
                fun handleCommandCancel() {
                        plugin.soundManager.playClickSound(player, item, "world_settings")
                        plugin.worldSettingsGui.open(player, worldData)
                }

                when (session.action) {
                        SettingsAction.MANAGE_MEMBERS -> {
                                event.isCancelled = true
                                if (event.clickedInventory != event.view.topInventory) return

                                if (type == ItemTag.TYPE_GUI_NAV_NEXT ||
                                                type == ItemTag.TYPE_GUI_NAV_PREV
                                ) {
                                        val targetPageFromTag = ItemTag.getTargetPage(item)
                                        val targetPage =
                                                if (targetPageFromTag != null) {
                                                        targetPageFromTag
                                                } else {
                                                        val lore = item.itemMeta?.lore().orEmpty()
                                                        lore.firstNotNullOfOrNull { line ->
                                                                val plainLine =
                                                                        PlainTextComponentSerializer
                                                                                .plainText()
                                                                                .serialize(line)
                                                                if (plainLine.startsWith("PAGE_TARGET: ")) {
                                                                        plainLine
                                                                                .removePrefix("PAGE_TARGET: ")
                                                                                .trim()
                                                                                .toIntOrNull()
                                                                } else {
                                                                        null
                                                                }
                                                        }
                                                } ?: return

                                        plugin.soundManager.playClickSound(
                                                player,
                                                item,
                                                "world_settings"
                                        )
                                        val latestWorld =
                                                plugin.worldConfigRepository.findByUuid(worldData.uuid)
                                                        ?: return
                                        plugin.worldSettingsGui.openMemberManagement(
                                                player,
                                                latestWorld,
                                                targetPage
                                        )
                                        return
                                }

                                if (type == ItemTag.TYPE_GUI_MEMBER_INVITE) {
                                        plugin.soundManager.playClickSound(
                                                player,
                                                item,
                                                "world_settings"
                                        )

                                        val forceAddMode =
                                                PermissionManager.canForceAddMember(player) &&
                                                        event.isShiftClick

                                        if (
                                                openBedrockMemberInviteInputForm(
                                                        player,
                                                        worldData,
                                                        forceAddMode
                                                )
                                        ) {
                                                return
                                        }

                                        if (plugin.playerPlatformResolver.isBedrock(player)) {
                                                plugin.floodgateFormBridge.notifyFallbackCancelled(player)
                                                reopenMemberManagementLatest(player, worldData.uuid)
                                                return
                                        }

                                        plugin.settingsSessionManager.updateSessionAction(
                                                player,
                                                worldData.uuid,
                                                SettingsAction.MEMBER_INVITE,
                                                isGui = true
                                        )
                                        plugin.settingsSessionManager
                                                .getSession(player)
                                                ?.setMetadata(
                                                        "member_invite_force_add_mode",
                                                        forceAddMode
                                                )
                                        player.closeInventory()
                                        Bukkit.getScheduler().runTask(
                                                plugin,
                                                Runnable {
                                                        showMemberInviteDialog(
                                                                player,
                                                                forceAddMode
                                                        )
                                                }
                                        )
                                        return
                                }

                                if (type == ItemTag.TYPE_GUI_CANCEL ||
                                                type == ItemTag.TYPE_GUI_BACK ||
                                                type == ItemTag.TYPE_GUI_RETURN
                                ) {
                                        handleCommandCancel()
                                        return
                                }

                                if (type == ItemTag.TYPE_GUI_MEMBER_PENDING_INVITE) {
                                        if (!event.isLeftClick) {
                                                return
                                        }
                                        if (!canCancelMemberInvite(player, worldData)) {
                                                player.sendMessage(lang.getMessage(player, "general.no_permission"))
                                                plugin.soundManager.playActionSound(
                                                        player,
                                                        "world_settings",
                                                        "error"
                                                )
                                                return
                                        }

                                        val decisionId =
                                                ItemTag.getString(item, "member_pending_invite_id")
                                                        ?.let { raw ->
                                                                runCatching { UUID.fromString(raw) }
                                                                        .getOrNull()
                                                        } ?: return
                                        val interaction = plugin.pendingInteractionRepository.findById(decisionId)
                                        if (
                                                interaction == null ||
                                                        interaction.type != PendingInteractionType.MEMBER_INVITE ||
                                                        interaction.worldUuid != worldData.uuid
                                        ) {
                                                player.sendMessage(
                                                        lang.getMessage(
                                                                player,
                                                                "messages.member_invite_cancel_not_found"
                                                        )
                                                )
                                                reopenMemberManagementLatest(player, worldData.uuid)
                                                return
                                        }

                                        val targetName =
                                                PlayerNameUtil.getNameOrDefault(
                                                        interaction.targetUuid,
                                                        lang.getMessage(player, "general.unknown")
                                                )
                                        plugin.soundManager.playClickSound(
                                                player,
                                                item,
                                                "world_settings"
                                        )
                                        plugin.settingsSessionManager.updateSessionAction(
                                                player,
                                                worldData.uuid,
                                                SettingsAction.MEMBER_PENDING_INVITE_CANCEL_CONFIRM,
                                                isGui = true
                                        )
                                        plugin.settingsSessionManager
                                                .getSession(player)
                                                ?.setMetadata(
                                                        "member_pending_invite_cancel_id",
                                                        decisionId.toString()
                                                )
                                        player.closeInventory()

                                        val title =
                                                LegacyComponentSerializer.legacySection().deserialize(
                                                        lang.getMessage(
                                                                player,
                                                                "gui.member_management.pending_cancel_confirm.title"
                                                        )
                                                )
                                        val bodyLines =
                                                listOf(
                                                        LegacyComponentSerializer.legacySection().deserialize(
                                                                lang.getMessage(
                                                                        player,
                                                                        "gui.member_management.pending_cancel_confirm.body",
                                                                        mapOf("player" to targetName)
                                                                )
                                                        )
                                                )
                                        val confirmAction =
                                                "mwm:confirm/member_pending_invite_cancel/$decisionId"
                                        DialogConfirmManager.showConfirmationByPreference(
                                                player,
                                                plugin,
                                                title,
                                                bodyLines,
                                                confirmAction,
                                                "mwm:confirm/cancel",
                                                lang.getMessage(
                                                        player,
                                                        "gui.member_management.pending_cancel_confirm.confirm"
                                                ),
                                                lang.getMessage(
                                                        player,
                                                        "gui.member_management.pending_cancel_confirm.cancel"
                                                ),
                                                onBedrockConfirm = {
                                                        handleBedrockDialogAction(
                                                                player,
                                                                worldData,
                                                                confirmAction
                                                        )
                                                },
                                                onBedrockCancel = {
                                                        handleBedrockDialogCancel(player, worldData)
                                                }
                                        ) {
                                                plugin.worldSettingsGui
                                                        .openMemberPendingInviteCancelConfirmation(
                                                                player,
                                                                worldData,
                                                                interaction.targetUuid,
                                                                decisionId
                                                        )
                                        }
                                        return
                                }

                                if (type == ItemTag.TYPE_GUI_MEMBER_ITEM) {
                                        val memberId = ItemTag.getWorldUuid(item)
                                        if (memberId != null && memberId != player.uniqueId) {
                                                val isBedrock =
                                                        plugin.playerPlatformResolver.isBedrock(player)
                                                if (isBedrock) {
                                                        if (event.isLeftClick && !event.isShiftClick) {
                                                                plugin.soundManager.playClickSound(
                                                                        player,
                                                                        item,
                                                                        "world_settings"
                                                                )
                                                                toggleMemberRole(
                                                                        player,
                                                                        worldData,
                                                                        memberId
                                                                )
                                                        }
                                                        return
                                                }

                                                if (event.isShiftClick) {
                                                        if (event.isLeftClick) {
                                                                plugin.soundManager.playClickSound(
                                                                        player,
                                                                        item,
                                                                        "world_settings"
                                                                )
                                                                // オーナー権限の移譲
                                                                val config = plugin.config
                                                                val stats =
                                                                        plugin.playerStatsRepository
                                                                                .findByUuid(memberId)
                                                                val defaultMax =
                                                                        config.getInt(
                                                                                "creation.max_create_count_default",
                                                                                3
                                                                        )
                                                                val maxCounts =
                                                                        defaultMax + stats.unlockedWorldSlot
                                                                val currentCounts =
                                                                        plugin.worldConfigRepository
                                                                                .findAll()
                                                                                .count {
                                                                                        it.owner == memberId
                                                                                }

                                                                if (!PermissionManager.canBypassWorldLimits(player) && currentCounts >= maxCounts) {
                                                                        player.sendMessage(
                                                                                plugin.languageManager
                                                                                        .getMessage(
                                                                                                "messages.owner_transfer_failed_limit"
                                                                                        )
                                                                        )
                                                                        plugin.soundManager.playActionSound(
                                                                                player,
                                                                                "creation",
                                                                                "limit_reached"
                                                                        )
                                                                        return
                                                                }

                                                                val memberName = PlayerNameUtil.getNameOrDefault(memberId, lang.getMessage(player, "general.unknown"))
                                                                val dialogTitle = LegacyComponentSerializer.legacySection().deserialize(
                                                                        lang.getMessage(player, "gui.member_management.transfer_confirm.title", mapOf("player" to memberName))
                                                                )
                                                                val bodyLines = lang.getMessageList(
                                                                        player,
                                                                        "gui.member_management.transfer_confirm.lore",
                                                                        mapOf("player" to memberName, "world" to worldData.name)
                                                                ).map { LegacyComponentSerializer.legacySection().deserialize(it) }

                                                                plugin.settingsSessionManager.updateSessionAction(
                                                                        player,
                                                                        worldData.uuid,
                                                                        SettingsAction.MEMBER_TRANSFER_CONFIRM,
                                                                        isGui = true
                                                                )
                                                                DialogConfirmManager.showConfirmationByPreference(
                                                                        player,
                                                                        plugin,
                                                                        dialogTitle,
                                                                        bodyLines,
                                                                        "mwm:confirm/member_transfer/$memberId",
                                                                        "mwm:confirm/cancel",
                                                                        lang.getMessage(player, "gui.member_management.transfer_confirm.confirm"),
                                                                        lang.getMessage(player, "gui.member_management.transfer_confirm.cancel"),
                                                                        onBedrockConfirm = {
                                                                                handleBedrockDialogAction(
                                                                                        player,
                                                                                        worldData,
                                                                                        "mwm:confirm/member_transfer/$memberId"
                                                                                )
                                                                        },
                                                                        onBedrockCancel = {
                                                                                handleBedrockDialogCancel(player, worldData)
                                                                        }
                                                                ) {
                                                                        plugin.worldSettingsGui.openMemberTransferConfirmation(
                                                                                player,
                                                                                worldData,
                                                                                memberId
                                                                        )
                                                                }
                                                        } else if (event.isRightClick) {
                                                                plugin.soundManager.playClickSound(
                                                                        player,
                                                                        item,
                                                                        "world_settings"
                                                                )
                                                                // メンバー削除
                                                                val memberName = PlayerNameUtil.getNameOrDefault(memberId, lang.getMessage(player, "general.unknown"))
                                                                val dialogTitle = LegacyComponentSerializer.legacySection().deserialize(
                                                                        lang.getMessage(player, "gui.member_management.remove_confirm.title", mapOf("player" to memberName))
                                                                )
                                                                val bodyLines = lang.getMessageList(
                                                                        player,
                                                                        "gui.member_management.remove_confirm.lore",
                                                                        mapOf("player" to memberName, "world" to worldData.name)
                                                                ).map { LegacyComponentSerializer.legacySection().deserialize(it) }

                                                                plugin.settingsSessionManager.updateSessionAction(
                                                                        player,
                                                                        worldData.uuid,
                                                                        SettingsAction.MEMBER_REMOVE_CONFIRM,
                                                                        isGui = true
                                                                )
                                                                DialogConfirmManager.showConfirmationByPreference(
                                                                        player,
                                                                        plugin,
                                                                        dialogTitle,
                                                                        bodyLines,
                                                                        "mwm:confirm/member_remove/$memberId",
                                                                        "mwm:confirm/cancel",
                                                                        lang.getMessage(player, "gui.member_management.remove_confirm.confirm"),
                                                                        lang.getMessage(player, "gui.member_management.remove_confirm.cancel"),
                                                                        onBedrockConfirm = {
                                                                                handleBedrockDialogAction(
                                                                                        player,
                                                                                        worldData,
                                                                                        "mwm:confirm/member_remove/$memberId"
                                                                                )
                                                                        },
                                                                        onBedrockCancel = {
                                                                                handleBedrockDialogCancel(player, worldData)
                                                                        }
                                                                ) {
                                                                        plugin.worldSettingsGui.openMemberRemoveConfirmation(
                                                                                player,
                                                                                worldData,
                                                                                memberId
                                                                        )
                                                                }
                                                        }
                                                } else if (event.isLeftClick) {
                                                        plugin.soundManager.playClickSound(
                                                                player,
                                                                item,
                                                                "world_settings"
                                                        )
                                                        toggleMemberRole(
                                                                player,
                                                                worldData,
                                                                memberId
                                                        )
                                                }
                                        }
                                }
                        }
                        SettingsAction.MEMBER_REMOVE_CONFIRM -> {
                                event.isCancelled = true
                                if (event.clickedInventory != event.view.topInventory) return

                                if (type == ItemTag.TYPE_GUI_CANCEL) {
                                        plugin.soundManager.playClickSound(
                                                player,
                                                item,
                                                "world_settings"
                                        )
                                        reopenMemberManagementLatest(player, worldData.uuid)
                                        // Updates session to MANAGE_MEMBERS
                                } else if (type == ItemTag.TYPE_GUI_CONFIRM) {
                                        plugin.soundManager.playClickSound(
                                                player,
                                                item,
                                                "world_settings"
                                        )
                                        val infoItem = event.inventory.getItem(13) ?: return
                                        val memberId = ItemTag.getWorldUuid(infoItem)

                                        if (memberId != null) {
                                                val memberName = PlayerNameUtil.getNameOrDefault(memberId, "Unknown")

                                                worldData.members.remove(memberId)
                                                worldData.moderators.remove(memberId)
                                                plugin.worldConfigRepository.save(worldData)
                                                Bukkit.getPluginManager().callEvent(
                                                        MwmMemberRemovedEvent(
                                                                worldUuid = worldData.uuid,
                                                                memberUuid = memberId,
                                                                memberName = memberName,
                                                                removedByUuid = player.uniqueId,
                                                                source = MwmMemberRemoveSource.MANUAL
                                                        )
                                                )
                                                player.sendMessage(
                                                        plugin.languageManager.getMessage(
                                                                "messages.member_deleted"
                                                        )
                                                )

                                                // 繝槭け繝ｭ螳溯｡・
                                                plugin.macroManager.execute(
                                                        "on_member_remove",
                                                        mapOf(
                                                                "world_uuid" to
                                                                        worldData.uuid.toString(),
                                                                "member" to memberName
                                                        )
                                                )
                                        }
                                        reopenMemberManagementLatest(player, worldData.uuid)
                                }
                        }
                        SettingsAction.MEMBER_TRANSFER_CONFIRM -> {
                                event.isCancelled = true
                                if (event.clickedInventory != event.view.topInventory) return

                                if (type == ItemTag.TYPE_GUI_CANCEL) {
                                        plugin.soundManager.playClickSound(
                                                player,
                                                item,
                                                "world_settings"
                                        )
                                        reopenMemberManagementLatest(player, worldData.uuid)
                                } else if (type == ItemTag.TYPE_GUI_CONFIRM) {
                                        plugin.soundManager.playClickSound(
                                                player,
                                                item,
                                                "world_settings"
                                        )
                                        val infoItem = event.inventory.getItem(13) ?: return
                                        val newOwnerId = ItemTag.getWorldUuid(infoItem) ?: return

                                        val oldOwnerId = worldData.owner
                                        val oldOwnerName = PlayerNameUtil.getNameOrDefault(oldOwnerId, "Unknown")
                                        val newOwnerName = PlayerNameUtil.getNameOrDefault(newOwnerId, "Unknown")


                                        // 繧ｪ繝ｼ繝翫・讓ｩ髯占ｭｲ貂｡縺ｮ蜃ｦ逅・
                                        worldData.owner = newOwnerId

                                        // 譌ｧ繧ｪ繝ｼ繝翫・繧偵Δ繝・Ξ繝ｼ繧ｿ繝ｼ縺ｸ
                                        if (!worldData.moderators.contains(oldOwnerId)) {
                                                worldData.moderators.add(oldOwnerId)
                                        }

                                        // 譁ｰ繧ｪ繝ｼ繝翫・繧偵Γ繝ｳ繝舌・/繝｢繝・Ξ繝ｼ繧ｿ繝ｼ繝ｪ繧ｹ繝医°繧牙炎髯､
                                        worldData.moderators.remove(newOwnerId)
                                        worldData.members.remove(newOwnerId)

                                        plugin.worldConfigRepository.save(worldData)
                                        Bukkit.getPluginManager().callEvent(
                                                MwmOwnerTransferredEvent(
                                                        worldUuid = worldData.uuid,
                                                        oldOwnerUuid = oldOwnerId,
                                                        oldOwnerName = oldOwnerName,
                                                        newOwnerUuid = newOwnerId,
                                                        newOwnerName = newOwnerName,
                                                        transferredByUuid = player.uniqueId,
                                                        source = MwmOwnerTransferSource.MANUAL
                                                )
                                        )
                                        player.sendMessage(
                                                plugin.languageManager.getMessage(
                                                        player,
                                                        "messages.owner_transferred",
                                                        mapOf("old_owner" to newOwnerName)
                                                )
                                        )

                                        // 繝槭け繝ｭ螳溯｡・
                                        plugin.macroManager.execute(
                                                "on_owner_transfer",
                                                mapOf(
                                                        "old_owner" to oldOwnerName,
                                                        "new_owner" to newOwnerName,
                                                        "world_uuid" to worldData.uuid.toString()
                                                )
                                        )

                                        reopenMemberManagementLatest(player, worldData.uuid)
                                }
                        }
                        SettingsAction.MEMBER_PENDING_INVITE_CANCEL_CONFIRM -> {
                                event.isCancelled = true
                                if (event.clickedInventory != event.view.topInventory) return

                                if (type == ItemTag.TYPE_GUI_CANCEL) {
                                        plugin.soundManager.playClickSound(
                                                player,
                                                item,
                                                "world_settings"
                                        )
                                        reopenMemberManagementLatest(player, worldData.uuid)
                                        return
                                }

                                if (type == ItemTag.TYPE_GUI_CONFIRM) {
                                        plugin.soundManager.playClickSound(
                                                player,
                                                item,
                                                "world_settings"
                                        )
                                        val infoItem = event.inventory.getItem(13) ?: return
                                        val decisionId =
                                                ItemTag.getString(infoItem, "member_pending_invite_id")
                                                        ?.let { raw ->
                                                                runCatching { UUID.fromString(raw) }
                                                                        .getOrNull()
                                                        } ?: return
                                        cancelMemberInviteByDecisionId(player, worldData.uuid, decisionId)
                                }
                        }
                        SettingsAction.MANAGE_VISITORS -> {
                                event.isCancelled = true
                                if (event.clickedInventory != event.view.topInventory) return

                                if (type == ItemTag.TYPE_GUI_NAV_NEXT ||
                                                type == ItemTag.TYPE_GUI_NAV_PREV
                                ) {
                                        val lore = item?.itemMeta?.lore() ?: return
                                        lore.forEach { line ->
                                                val plainLine =
                                                        PlainTextComponentSerializer.plainText()
                                                                .serialize(line)
                                                if (plainLine.startsWith("PAGE_TARGET: ")) {
                                                        val targetPage =
                                                                plainLine
                                                                        .removePrefix(
                                                                                "PAGE_TARGET: "
                                                                        )
                                                                        .trim()
                                                                        .toIntOrNull()
                                                                        ?: return@forEach
                                                        plugin.soundManager.playClickSound(
                                                                player,
                                                                item,
                                                                "world_settings"
                                                        )
                                                        plugin.worldSettingsGui
                                                                .openVisitorManagement(
                                                                        player,
                                                                        worldData,
                                                                        targetPage
                                                                )
                                                }
                                        }
                                        return
                                }

                                if (type == ItemTag.TYPE_GUI_VISITOR_ITEM) {
                                        val canKickByClick =
                                                if (plugin.playerPlatformResolver.isBedrock(player)) {
                                                        event.isLeftClick
                                                } else {
                                                        event.isRightClick
                                                }
                                        if (canKickByClick) {
                                                val visitorUuid =
                                                        ItemTag.getWorldUuid(item) ?: return
                                                plugin.soundManager.playClickSound(
                                                        player,
                                                        item,
                                                        "world_settings"
                                                )
                                                val targetName = PlayerNameUtil.getNameOrDefault(visitorUuid, lang.getMessage(player, "general.unknown"))
                                                val dialogTitle = LegacyComponentSerializer.legacySection().deserialize(
                                                        lang.getMessage(player, "gui.visitor_management.kick_confirm.title", mapOf("player" to targetName))
                                                )
                                                val bodyLines = listOf(
                                                        LegacyComponentSerializer.legacySection().deserialize(
                                                                lang.getMessage(player, "gui.visitor_management.kick_confirm.question")
                                                        ),
                                                        LegacyComponentSerializer.legacySection().deserialize(
                                                                lang.getMessage(player, "gui.visitor_management.kick_confirm.player", mapOf("player" to targetName))
                                                        )
                                                )

                                                plugin.settingsSessionManager.updateSessionAction(
                                                        player,
                                                        worldData.uuid,
                                                        SettingsAction.VISITOR_KICK_CONFIRM,
                                                        isGui = true
                                                )
                                                DialogConfirmManager.showConfirmationByPreference(
                                                        player,
                                                        plugin,
                                                        dialogTitle,
                                                        bodyLines,
                                                        "mwm:confirm/visitor_kick/$visitorUuid",
                                                        "mwm:confirm/cancel",
                                                        lang.getMessage(player, "gui.visitor_management.kick_confirm.confirm"),
                                                        lang.getMessage(player, "gui.visitor_management.kick_confirm.cancel"),
                                                        onBedrockConfirm = {
                                                                handleBedrockDialogAction(
                                                                        player,
                                                                        worldData,
                                                                        "mwm:confirm/visitor_kick/$visitorUuid"
                                                                )
                                                        },
                                                        onBedrockCancel = {
                                                                handleBedrockDialogCancel(player, worldData)
                                                        }
                                                ) {
                                                        plugin.worldSettingsGui.openVisitorKickConfirmation(
                                                                player,
                                                                worldData,
                                                                visitorUuid
                                                        )
                                                }
                                        }
                                } else if (type == ItemTag.TYPE_GUI_CANCEL) {
                                        handleCommandCancel()
                                }
                        }
                        SettingsAction.VISITOR_KICK_CONFIRM -> {
                                event.isCancelled = true
                                if (event.clickedInventory != event.view.topInventory) return

                                if (type == ItemTag.TYPE_GUI_CANCEL) {
                                        plugin.soundManager.playClickSound(
                                                player,
                                                item,
                                                "world_settings"
                                        )
                                        plugin.worldSettingsGui.openVisitorManagement(
                                                player,
                                                worldData
                                        )
                                } else if (type == ItemTag.TYPE_GUI_CONFIRM) {
                                        plugin.soundManager.playClickSound(
                                                player,
                                                item,
                                                "world_settings"
                                        )
                                        val infoItem = event.inventory.getItem(13) ?: return
                                        val visitorUuid = ItemTag.getWorldUuid(infoItem) ?: return
                                        val visitor = Bukkit.getPlayer(visitorUuid)

                                        val worldFolderName =
                                                worldData.customWorldName
                                                        ?: "my_world.${worldData.uuid}"
                                        if (visitor != null && visitor.world.name == worldFolderName
                                        ) {
                                                val config = plugin.config
                                                val worldName =
                                                        config.getString(
                                                                "evacuation_location.world",
                                                                "world"
                                                        )
                                                val evacWorld =
                                                        Bukkit.getWorld(worldName!!)
                                                                ?: Bukkit.getWorlds()[0]
                                                val x =
                                                        config.getDouble(
                                                                "evacuation_location.x",
                                                                evacWorld.spawnLocation.x
                                                        )
                                                val y =
                                                        config.getDouble(
                                                                "evacuation_location.y",
                                                                evacWorld.spawnLocation.y
                                                        )
                                                val z =
                                                        config.getDouble(
                                                                "evacuation_location.z",
                                                                evacWorld.spawnLocation.z
                                                        )
                                                val yaw =
                                                        config.getDouble(
                                                                        "evacuation_location.yaw",
                                                                        evacWorld.spawnLocation.yaw
                                                                                .toDouble()
                                                                )
                                                                .toFloat()
                                                val pitch =
                                                        config.getDouble(
                                                                        "evacuation_location.pitch",
                                                                        evacWorld.spawnLocation
                                                                                .pitch.toDouble()
                                                                )
                                                                .toFloat()

                                                val evacuationLoc =
                                                        org.bukkit.Location(
                                                                evacWorld,
                                                                x,
                                                                y,
                                                                z,
                                                                yaw,
                                                                pitch
                                                        )

                                                visitor.teleport(evacuationLoc)
                                                visitor.sendMessage(
                                                        plugin.languageManager.getMessage(
                                                                visitor,
                                                                "messages.kicked"
                                                        )
                                                )
                                                player.sendMessage(
                                                        plugin.languageManager.getMessage(
                                                                player,
                                                                "messages.kicked_success",
                                                                mapOf("player" to visitor.name)
                                                        )
                                                )
                                        }
                                        plugin.worldSettingsGui.openVisitorManagement(
                                                player,
                                                worldData
                                        )
                                }
                        }
                        SettingsAction.EXPAND_SELECT_METHOD -> {
                                event.isCancelled = true
                                if (event.clickedInventory != event.view.topInventory) return

                                if (type == ItemTag.TYPE_GUI_SETTING_EXPAND) {
                                        plugin.soundManager.playClickSound(
                                                player,
                                                item,
                                                "world_settings"
                                        )
                                        session.expansionDirection = null // Reset
                                        val cost =
                                                calculateExpansionCost(
                                                        worldData.borderExpansionLevel
                                                )
                                        openExpandConfirmationByPreference(
                                                player,
                                                worldData.uuid,
                                                null,
                                                cost
                                        )
                                } else if (type == ItemTag.TYPE_GUI_SETTING_EXPAND_DIRECTION) {
                                        plugin.soundManager.playClickSound(
                                                player,
                                                item,
                                                "world_settings"
                                        )
                                        session.action = SettingsAction.EXPAND_DIRECTION_WAIT
                                        player.closeInventory()
                                        val promptKey =
                                                if (plugin.playerPlatformResolver.isBedrock(player)) {
                                                        "messages.expand_direction_prompt_bedrock"
                                                } else {
                                                        "messages.expand_direction_prompt"
                                                }
                                        player.sendMessage(
                                                plugin.languageManager.getMessage(
                                                        player,
                                                        promptKey
                                                )
                                        )
                                } else if (type == ItemTag.TYPE_GUI_CANCEL ||
                                                type == ItemTag.TYPE_GUI_BACK ||
                                                type == ItemTag.TYPE_GUI_RETURN
                                ) {
                                        handleCommandCancel()
                                }
                        }
                        SettingsAction.EXPAND_CONFIRM -> {
                                event.isCancelled = true
                                if (event.clickedInventory != event.view.topInventory) return

                                if (type == ItemTag.TYPE_GUI_CANCEL ||
                                                (item?.type == Material.RED_WOOL && type == null) ||
                                                (item?.type == Material.RED_WOOL &&
                                                        type == ItemTag.TYPE_GUI_CANCEL)
                                ) {
                                        plugin.soundManager.playClickSound(
                                                player,
                                                item,
                                                "world_settings"
                                        )
                                        plugin.worldSettingsGui.openExpansionMethodSelection(
                                                player,
                                                worldData
                                        )
                                } else if (type == ItemTag.TYPE_GUI_CONFIRM ||
                                                (item?.type == Material.LIME_WOOL &&
                                                        type == null) ||
                                                (item?.type == Material.LIME_WOOL &&
                                                        type == ItemTag.TYPE_GUI_CONFIRM)
                                ) {
                                        val cost =
                                                calculateExpansionCost(
                                                        worldData.borderExpansionLevel
                                                )
                                        val stats =
                                                plugin.playerStatsRepository.findByUuid(
                                                        player.uniqueId
                                                )

                                        if (stats.worldPoint < cost) {
                                                 player.sendMessage("§cポイントが不足しています。")
                                                player.closeInventory()
                                                return
                                        }

                                        plugin.soundManager.playClickSound(
                                                player,
                                                item,
                                                "world_settings"
                                        )
                                        player.closeInventory()

                                        val messageList =
                                                plugin.languageManager.getMessageList(
                                                        player,
                                                        "messages.oage_ganbaru_messages"
                                                )
                                        val randomMessage =
                                                if (messageList.isNotEmpty()) messageList.random()
                                                else
                                                        plugin.languageManager.getMessage(
                                                                player,
                                                                "messages.oage_ganbaru_default"
                                                        )
                                        player.sendMessage(randomMessage)
                                        player.playSound(
                                                player.location,
                                                Sound.BLOCK_ANVIL_USE,
                                                0.5f,
                                                0.5f
                                        )

                                        val task =
                                                Bukkit.getScheduler()
                                                        .runTaskLater(
                                                                plugin,
                                                                Runnable {
                                                                        val pending =
                                                                                pendingExpansions
                                                                                        .remove(
                                                                                                player.uniqueId
                                                                                        )
                                                                                        ?: return@Runnable
                                                                        executeExpansionFinal(
                                                                                player,
                                                                                pending.worldData,
                                                                                pending.cost,
                                                                                pending.direction
                                                                        )
                                                                },
                                                                40L
                                                        )

                                        pendingExpansions[player.uniqueId] =
                                                PendingExpansion(
                                                        worldData,
                                                        cost,
                                                        session.expansionDirection,
                                                        task
                                                )
                                        plugin.settingsSessionManager.endSession(player)
                                }
                        }
                        SettingsAction.VIEW_SETTINGS, SettingsAction.SELECT_ICON -> {
                                event.isCancelled = true
                                val clickedItem = event.currentItem ?: return

                                // 繧｢繧､繧ｳ繝ｳ驕ｸ謚槭Δ繝ｼ繝・
                                if (session.action == SettingsAction.SELECT_ICON) {
                                        // 陬・｣ｾ繧｢繧､繝・Β莉･螟悶↑繧峨い繧､繧ｳ繝ｳ縺ｨ縺励※繧ｻ繝・ヨ
                                        if (clickedItem.type != Material.AIR &&
                                                        !ItemTag.isType(
                                                                clickedItem,
                                                                ItemTag.TYPE_GUI_DECORATION
                                                        )
                                        ) {
                                                worldData.icon = clickedItem.type
                                                plugin.worldConfigRepository.save(worldData)
                                                val itemName =
                                                        clickedItem
                                                                .displayName()
                                                                .decoration(
                                                                        TextDecoration.ITALIC,
                                                                        false
                                                                )
                                                player.sendMessage(
                                                        plugin.languageManager.getMessage(
                                                                player,
                                                                "messages.icon_changed",
                                                                mapOf(
                                                                        "item" to
                                                                                LegacyComponentSerializer
                                                                                        .legacySection()
                                                                                        .serialize(
                                                                                                itemName
                                                                                        )
                                                                )
                                                        )
                                                )
                                                plugin.soundManager.playClickSound(
                                                        player,
                                                        null,
                                                        "world_settings"
                                                )

                                                // メニュー再描画（これによりセッションはVIEW_SETTINGSに戻る）
                                                plugin.worldSettingsGui.open(player, worldData)
                                        }
                                        return
                                }

                                // 通常設定操作
                                val itemTag = ItemTag.getType(clickedItem) ?: return

                                // 制限対象のタグ
                                val restrictedTags =
                                        setOf(
                                                ItemTag.TYPE_GUI_SETTING_SPAWN,
                                                ItemTag.TYPE_GUI_SETTING_EXPAND_DIRECTION,
                                                ItemTag.TYPE_GUI_SETTING_EXPAND,
                                                ItemTag.TYPE_GUI_SETTING_ENVIRONMENT,
                                                ItemTag.TYPE_GUI_SETTING_CRITICAL
                                        )

                                if (restrictedTags.contains(itemTag)) {
                                        val targetWorldName =
                                                worldData.customWorldName
                                                        ?: "my_world.${worldData.uuid}"
                                        if (player.world.name != targetWorldName) {
                                                plugin.soundManager.playClickSound(
                                                        player,
                                                        null,
                                                        "world_settings"
                                                )
                                                player.playSound(
                                                        player.location,
                                                        Sound.ENTITY_VILLAGER_NO,
                                                        1.0f,
                                                        1.0f
                                                )
                                                return
                                        }
                                }

                                when (itemTag) {
                                        ItemTag.TYPE_GUI_RETURN -> {
                                                if (session.isAdminFlow) {
                                                        plugin.soundManager.playAdminClickSound(player)
                                                        plugin.worldGui.open(player, fromAdminMenu = true)
                                                } else if (session.isPlayerWorldFlow) {
                                                        plugin.soundManager.playClickSound(player, clickedItem, "world_settings")
                                                        plugin.playerWorldGui.open(player, 0, showBackButton = session.parentShowBackButton)
                                                } else {
                                                        me.awabi2048.myworldmanager.util.GuiHelper
                                                                .handleReturnClick(
                                                                        plugin,
                                                                        player,
                                                                        clickedItem
                                                                )
                                                }
                                        }
                                        ItemTag.TYPE_GUI_SETTING_INFO -> {
                                                plugin.soundManager.playClickSound(
                                                        player,
                                                        clickedItem,
                                                        "world_settings"
                                                )

                                                if (openBedrockWorldInfoInputForm(player, worldData)) {
                                                        return
                                                }

                                                val isDescriptionInput = event.isRightClick

                                                if (
                                                        openBedrockSettingTextInputForm(
                                                                player,
                                                                worldData,
                                                                isDescriptionInput
                                                        )
                                                ) {
                                                        return
                                                }
                                                
                                                if (plugin.playerPlatformResolver.isBedrock(player)) {
                                                    plugin.floodgateFormBridge.notifyFallbackCancelled(player)
                                                    plugin.worldSettingsGui.open(player, worldData)
                                                    return
                                                }

                                                if (isDescriptionInput) {
                                                    plugin.settingsSessionManager.updateSessionAction(player, worldData.uuid, SettingsAction.CHANGE_DESCRIPTION)
                                                } else {
                                                    plugin.settingsSessionManager.updateSessionAction(player, worldData.uuid, SettingsAction.RENAME_WORLD)
                                                }

                                                player.closeInventory()
                                                Bukkit.getScheduler().runTask(plugin, Runnable {
                                                    if (isDescriptionInput) {
                                                        showDescriptionDialog(player, worldData)
                                                    } else {
                                                        showRenameDialog(player, worldData)
                                                    }
                                                })

                                        }
                                        ItemTag.TYPE_GUI_SETTING_SPAWN -> {
                                                plugin.soundManager.playClickSound(
                                                        player,
                                                        clickedItem,
                                                        "world_settings"
                                                )
                                                val isBedrock =
                                                        plugin.playerPlatformResolver.isBedrock(player)
                                                val isGuest = isBedrock || event.isLeftClick
                                                val action =
                                                        if (isGuest) SettingsAction.SET_SPAWN_GUEST
                                                        else SettingsAction.SET_SPAWN_MEMBER
                                                val typeKey =
                                                        if (isBedrock) {
                                                                "gui.settings.spawn.type.both"
                                                        } else if (isGuest) {
                                                                "gui.settings.spawn.type.guest"
                                                        } else {
                                                                "gui.settings.spawn.type.member"
                                                        }
                                                val typeName =
                                                        plugin.languageManager.getMessage(
                                                                player,
                                                                typeKey
                                                        )

                                                player.sendMessage(
                                                        plugin.languageManager.getMessage(
                                                                player,
                                                                if (isBedrock) "messages.spawn_set_start_bedrock" else "messages.spawn_set_start",
                                                                mapOf("type" to typeName)
                                                        )
                                                )
                                                plugin.settingsSessionManager.updateSessionAction(
                                                        player,
                                                        worldData.uuid,
                                                        action
                                                )
                                                plugin.settingsSessionManager
                                                        .getSession(player)
                                                        ?.setMetadata("spawn_set_both", isBedrock)
                                                startSpawnPreview(player)
                                                player.closeInventory()
                                        }
                                        ItemTag.TYPE_GUI_SETTING_ICON -> {
                                                plugin.soundManager.playClickSound(
                                                        player,
                                                        clickedItem,
                                                        "world_settings"
                                                )
                                                plugin.settingsSessionManager.updateSessionAction(
                                                        player,
                                                        worldData.uuid,
                                                        SettingsAction.SELECT_ICON
                                                )
                                                player.sendMessage(
                                                        plugin.languageManager.getMessage(
                                                                "messages.icon_prompt"
                                                        )
                                                )
                                        }
                                        ItemTag.TYPE_GUI_SETTING_EXPAND -> {
                                                if (worldData.borderExpansionLevel ==
                                                                WorldData.EXPANSION_LEVEL_SPECIAL
                                                )
                                                        return
                                                val isBedrock =
                                                        plugin.playerPlatformResolver.isBedrock(player)
                                                if (!isBedrock && event.isRightClick) {
                                                        plugin.soundManager.playClickSound(
                                                                player,
                                                                clickedItem,
                                                                "world_settings"
                                                        )
                                                        if (!teleportToBorderCenterSurface(player, worldData)) {
                                                                player.sendMessage(
                                                                        plugin.languageManager.getMessage(
                                                                                player,
                                                                                "error.world_load_failed"
                                                                        )
                                                                )
                                                        }
                                                        return
                                                }
                                                plugin.soundManager.playClickSound(
                                                        player,
                                                        clickedItem,
                                                        "world_settings"
                                                )

                                                val config = plugin.config
                                                val costsSection =
                                                        config.getConfigurationSection(
                                                                "expansion.costs"
                                                        )
                                                val maxLevel =
                                                        costsSection?.getKeys(false)?.size ?: 3

                                                val currentLevel = worldData.borderExpansionLevel

                                                if (currentLevel >= maxLevel) {
                                                        player.sendMessage(
                                                                plugin.languageManager.getMessage(
                                                                        "error.max_expansion_reached"
                                                                )
                                                        )
                                                        return
                                                }

                                                plugin.worldSettingsGui
                                                        .openExpansionMethodSelection(
                                                                player,
                                                                worldData
                                                        )
                                        }
                                        ItemTag.TYPE_GUI_SETTING_PUBLISH -> {
                                                plugin.soundManager.playClickSound(
                                                        player,
                                                        clickedItem,
                                                        "world_settings"
                                                )
                                                val isBedrock =
                                                        plugin.playerPlatformResolver.isBedrock(player)
                                                val nextLevel =
                                                        GuiHelper.getNextValue(
                                                                worldData.publishLevel,
                                                                PublishLevel.values(),
                                                                if (isBedrock) false else event.isRightClick
                                                        )
                                                worldData.publishLevel = nextLevel
                                                if (worldData.publishLevel == PublishLevel.PUBLIC) {
                                                        worldData.publicAt =
                                                                java.time.format.DateTimeFormatter
                                                                        .ofPattern(
                                                                                "yyyy-MM-dd HH:mm:ss"
                                                                        )
                                                                        .format(
                                                                                java.time
                                                                                        .LocalDateTime
                                                                                        .now()
                                                                        )
                                                }
                                                plugin.worldConfigRepository.save(worldData)
                                                plugin.worldSettingsGui.open(player, worldData)
                                        }
                                        ItemTag.TYPE_GUI_SETTING_MEMBER -> {
                                                plugin.soundManager.playClickSound(
                                                        player,
                                                        clickedItem,
                                                        "world_settings"
                                                )
                                                plugin.worldSettingsGui.openMemberManagement(
                                                        player,
                                                        worldData
                                                )
                                        }
                                        ItemTag.TYPE_GUI_SETTING_ARCHIVE -> {
                                                plugin.soundManager.playClickSound(
                                                        player,
                                                        clickedItem,
                                                        "world_settings"
                                                )
                                                val title = LegacyComponentSerializer.legacySection().deserialize(
                                                        plugin.languageManager.getMessage(player, "gui.archive.confirm_title")
                                                )
                                                val bodyLines = listOf(
                                                        LegacyComponentSerializer.legacySection().deserialize(
                                                                plugin.languageManager.getMessage(player, "gui.common.confirm_warning")
                                                        )
                                                )
                                                plugin.settingsSessionManager.updateSessionAction(
                                                        player,
                                                        worldData.uuid,
                                                        SettingsAction.ARCHIVE_WORLD,
                                                        isGui = true
                                                )
                                                DialogConfirmManager.showConfirmationByPreference(
                                                        player,
                                                        plugin,
                                                        title,
                                                        bodyLines,
                                                        "mwm:confirm/archive_world",
                                                        "mwm:confirm/cancel",
                                                        plugin.languageManager.getMessage(player, "gui.archive.confirm"),
                                                        plugin.languageManager.getMessage(player, "gui.common.cancel"),
                                                        onBedrockConfirm = {
                                                                handleBedrockDialogAction(
                                                                        player,
                                                                        worldData,
                                                                        "mwm:confirm/archive_world"
                                                                )
                                                        },
                                                        onBedrockCancel = {
                                                                handleBedrockDialogCancel(player, worldData)
                                                        }
                                                ) {
                                                        plugin.worldSettingsGui.openArchiveConfirmation(
                                                                player,
                                                                worldData
                                                        )
                                                }
                                        }
                                        ItemTag.TYPE_GUI_SETTING_TAGS -> {
                                                plugin.logger.info("[MWM-Debug] Tag settings button clicked by ${player.name}")
                                                plugin.soundManager.playClickSound(
                                                        player,
                                                        clickedItem,
                                                        "world_settings"
                                                )
                                                // Check Beta Features
                                                val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                                                plugin.logger.info("[MWM-Debug] Beta features enabled: ${stats.betaFeaturesEnabled}")
                                                if (stats.betaFeaturesEnabled) {
                                                    // Dialog Flow
                                                    plugin.logger.info("[MWM-Debug] Opening Dialog flow for tag editing")
                                                    // Dialog uses custom GUI transition flag to prevent session cleanup
                                                    plugin.settingsSessionManager.updateSessionAction(player, worldData.uuid, SettingsAction.MANAGE_TAGS, isGui = true)
                                                    plugin.logger.info("[MWM-Debug] Session updated for world ${worldData.uuid}")

                                                    player.closeInventory()
                                                    Bukkit.getScheduler().runTask(plugin, Runnable {
                                                        showTagEditorDialog(player, worldData)
                                                    })
                                                } else {
                                                    // Legacy GUI Flow
                                                    plugin.logger.info("[MWM-Debug] Opening Legacy GUI flow for tag editing")
                                                    plugin.worldSettingsGui.openTagEditor(
                                                            player,
                                                            worldData
                                                    )
                                                }
                                        }
                                        ItemTag.TYPE_GUI_SETTING_VISITOR -> {
                                                plugin.soundManager.playClickSound(
                                                        player,
                                                        clickedItem,
                                                        "world_settings"
                                                )
                                                val worldFolderName =
                                                        worldData.customWorldName
                                                                ?: "my_world.${worldData.uuid}"
                                                val world = Bukkit.getWorld(worldFolderName)
                                                val visitorCount =
                                                        world?.players?.count {
                                                                it.uniqueId != worldData.owner &&
                                                                        !worldData.moderators
                                                                                .contains(
                                                                                        it.uniqueId
                                                                                ) &&
                                                                        !worldData.members.contains(
                                                                                it.uniqueId
                                                                        )
                                                        }
                                                                ?: 0

                                                if (visitorCount == 0) {
                                                        player.sendMessage(
                                                                plugin.languageManager.getMessage(
                                                                        player,
                                                                        "gui.visitor_management.no_visitors"
                                                                )
                                                        )
                                                        return
                                                }
                                                plugin.worldSettingsGui.openVisitorManagement(
                                                        player,
                                                        worldData
                                                )
                                        }
                                        ItemTag.TYPE_GUI_SETTING_NOTIFICATION -> {
                                                plugin.soundManager.playClickSound(
                                                        player,
                                                        clickedItem,
                                                        "world_settings"
                                                )
                                                worldData.notificationEnabled =
                                                        !worldData.notificationEnabled
                                                plugin.worldConfigRepository.save(worldData)
                                                plugin.worldSettingsGui.open(player, worldData)
                                        }
                                        ItemTag.TYPE_GUI_SETTING_CRITICAL -> {
                                                plugin.soundManager.playClickSound(
                                                        player,
                                                        clickedItem,
                                                        "world_settings"
                                                )
                                                plugin.worldSettingsGui.openCriticalSettings(
                                                        player,
                                                        worldData
                                                )
                                        }
						ItemTag.TYPE_GUI_SETTING_ANNOUNCEMENT -> {
								plugin.soundManager.playClickSound(
										player,
										clickedItem,
										"world_settings"
								)
								if (openBedrockAnnouncementActionForm(player, worldData)) {
										return
								}
								if (plugin.playerPlatformResolver.isBedrock(player)) {
										plugin.floodgateFormBridge.notifyFallbackCancelled(player)
										plugin.worldSettingsGui.open(player, worldData)
										return
								}
								if (event.isRightClick) {
										worldData.announcementMessages.clear()
										plugin.worldConfigRepository.save(worldData)
														player.sendMessage(
																plugin.languageManager.getMessage(
																		"messages.announcement_reset"
																)
														)
														plugin.worldSettingsGui.open(
																player,
																worldData
														)
												} else {
												player.closeInventory()
												me.awabi2048.myworldmanager.gui.AnnouncementDialogManager.showAnnouncementEditDialog(player, worldData)
												plugin.settingsSessionManager
														.updateSessionAction(
																player,
																worldData.uuid,
																SettingsAction.SET_ANNOUNCEMENT
														)
										}
										}
                                        ItemTag.TYPE_GUI_SETTING_PORTALS -> {
                                                // ワールドオーナーのみアクセス可能
                                                val isOwner = worldData.owner == player.uniqueId ||
                                                        plugin.settingsSessionManager.getSession(player)?.isAdminFlow == true
                                                if (!isOwner) {
                                                        player.sendMessage(
                                                                plugin.languageManager.getMessage(
                                                                        player,
                                                                        "error.no_permission"
                                                                )
                                                        )
                                                        plugin.soundManager.playClickSound(
                                                                player,
                                                                clickedItem,
                                                                "world_settings"
                                                        )
                                                        return
                                                }

                                                val worldName =
                                                        worldData.customWorldName
                                                                ?: "my_world.${worldData.uuid}"
                                                val hasPortals =
                                                        plugin.portalRepository.findAll().any {
                                                                it.worldName == worldName
                                                        }

                                                plugin.soundManager.playClickSound(
                                                        player,
                                                        clickedItem,
                                                        "world_settings"
                                                )
                                                if (hasPortals) {
                                                        plugin.worldSettingsGui
                                                                .openPortalManagement(
                                                                        player,
                                                                        worldData
                                                                )
                                                } else {
                                                        // ポータルがない場合の処理 (クリック音も鳴らさない)
                                                        player.sendMessage(
                                                                plugin.languageManager.getMessage(
                                                                        player,
                                                                        "error.no_portals_found"
                                                                )
                                                        )
                                                }
                                        }
                                        ItemTag.TYPE_GUI_SETTING_ENVIRONMENT -> {
                                                plugin.soundManager.playClickSound(
                                                        player,
                                                        clickedItem,
                                                        "world_settings"
                                                )
                                                if (plugin.playerPlatformResolver.isBedrock(player)) {
                                                        player.sendMessage(
                                                                plugin.languageManager.getMessage(
                                                                        player,
                                                                        "messages.bedrock_option_unavailable"
                                                                )
                                                        )
                                                        return
                                                }
                                                if (player.hasPermission("myworldmanager.admin")) {
                                                        plugin.environmentGui.open(player, worldData)
                                                }
                                        }
                                }
                        }
                        SettingsAction.VIEW_ENVIRONMENT_SETTINGS -> {
                                event.isCancelled = true
                                if (plugin.playerPlatformResolver.isBedrock(player)) {
                                        player.sendMessage(
                                                plugin.languageManager.getMessage(
                                                        player,
                                                        "messages.bedrock_option_unavailable"
                                                )
                                        )
                                        plugin.worldSettingsGui.open(player, worldData)
                                        return
                                }

                                // Handle player inventory clicks for biome bottle / Moon stone
                                if (event.clickedInventory != event.view.topInventory) {
                                        val clickedItem = event.currentItem ?: return
                                        if (ItemTag.isType(clickedItem, ItemTag.TYPE_MOON_STONE)) {
                                                plugin.soundManager.playClickSound(
                                                        player,
                                                        clickedItem,
                                                        "world_settings"
                                                )
                                                val cost =
                                                        plugin.config.getInt(
                                                                "environment.gravity.cost",
                                                                100
                                                        )
                                                plugin.logger.info("[MWM-Debug] Gravity click detected. Cost: $cost")
                                                showEnvConfirmDialog(player, "gravity", cost)
                                        } else if (ItemTag.isType(
                                                        clickedItem,
                                                        ItemTag.TYPE_BOTTLED_BIOME_AIR
                                                )
                                        ) {
                                                // Permission & Logic Check
                                                val isMember =
                                                        player.uniqueId == worldData.owner ||
                                                                worldData.moderators.contains(
                                                                        player.uniqueId
                                                                ) ||
                                                                worldData.members.contains(
                                                                        player.uniqueId
                                                                ) ||
                                                                session.isAdminFlow
                                                val isAdminWorld = worldData.customWorldName != null

                                                if (isAdminWorld) {
                                                        player.playSound(
                                                                player.location,
                                                                Sound.ENTITY_VILLAGER_NO,
                                                                1.0f,
                                                                1.0f
                                                        )
                                                        player.sendMessage(
                                                                lang.getMessage(
                                                                        player,
                                                                        "messages.custom_item.biome_bottle_disabled"
                                                                )
                                                        )
                                                        return
                                                }

                                                if (!isMember) {
                                                        player.playSound(
                                                                player.location,
                                                                Sound.ENTITY_VILLAGER_NO,
                                                                1.0f,
                                                                1.0f
                                                        )
                                                        player.sendMessage(
                                                                lang.getMessage(
                                                                        player,
                                                                        "error.custom_item.no_permission"
                                                                )
                                                        )
                                                        return
                                                }

                                                plugin.soundManager.playClickSound(
                                                        player,
                                                        clickedItem,
                                                        "world_settings"
                                                )
                                                val cost =
                                                        plugin.config.getInt(
                                                                "environment.biome.cost",
                                                                500
                                                        )

                                                val biomeId = ItemTag.getBiomeId(clickedItem)
                                                if (biomeId != null) {
                                                        session.setMetadata("temp_biome", biomeId)
                                                        plugin.logger.info("[MWM-Debug] Biome click detected. ID: $biomeId")
                                                        showEnvConfirmDialog(player, "biome", cost)
                                                }
                                        }
                                        return
                                }

                                when (type) {
                                        ItemTag.TYPE_GUI_CANCEL -> {
                                                handleCommandCancel()
                                        }
                                        ItemTag.TYPE_GUI_ENV_WEATHER -> {
                                                if (event.isLeftClick) {
                                                        handleWeatherClickCycle(player, worldData)
                                                } else if (event.isRightClick) {
                                                        val cost = plugin.config.getInt("environment.weather.cost", 50)
                                                        showEnvConfirmDialog(player, "weather", cost)
                                                }
                                        }
                                        ItemTag.TYPE_GUI_ENV_GRAVITY -> {
                                                val cost = plugin.config.getInt("environment.gravity.cost", 100)
                                                showEnvConfirmDialog(player, "gravity", cost)
                                        }
                                        ItemTag.TYPE_GUI_ENV_BIOME -> {
                                                player.sendMessage(
                                                        plugin.languageManager.getMessage(
                                                                player,
                                                                "gui.environment.biome.click_bottle_hint"
                                                        )
                                                )
                                        }
                                }
                        }
                        SettingsAction.ENV_CONFIRM -> {
                                event.isCancelled = true
                                if (event.clickedInventory != event.view.topInventory) return

                                when (type) {
                                        ItemTag.TYPE_GUI_CONFIRM -> {
                                                val confirmItem = session.confirmItem ?: return
                                                if (ItemTag.isType(
                                                                confirmItem,
                                                                ItemTag.TYPE_MOON_STONE
                                                        )
                                                ) {
                                                        executeGravityChange(
                                                                player,
                                                                worldData,
                                                                confirmItem
                                                        )
                                                } else if (ItemTag.isType(
                                                                confirmItem,
                                                                ItemTag.TYPE_BOTTLED_BIOME_AIR
                                                        )
                                                ) {
                                                        executeBiomeChange(
                                                                player,
                                                                worldData,
                                                                confirmItem
                                                        )
                                                }
                                        }
                                        ItemTag.TYPE_GUI_CANCEL -> {
                                                plugin.soundManager.playClickSound(
                                                        player,
                                                        null,
                                                        "world_settings"
                                                )
                                                plugin.environmentGui.open(player, worldData)
                                        }
                                }
                        }
                        SettingsAction.MANAGE_TAGS -> {
                                event.isCancelled = true
                                if (event.clickedInventory != event.view.topInventory) return

                                if (type != null && type.startsWith("tag_")) {
                                        plugin.soundManager.playClickSound(
                                                player,
                                                item,
                                                "world_settings"
                                        )
                                        val tagId = plugin.worldTagManager.normalizeTagId(type.substringAfter("tag_")) ?: return
                                        val editableTags = plugin.worldTagManager.getEditableTagIds(worldData.tags)
                                        if (!editableTags.contains(tagId)) {
                                                return
                                        }

                                        if (worldData.tags.contains(tagId)) {
                                                worldData.tags.remove(tagId)
                                        } else {
                                                worldData.tags.add(tagId)
                                        }
                                        plugin.worldConfigRepository.save(worldData)
                                        plugin.worldSettingsGui.openTagEditor(player, worldData)
                                } else if (type == ItemTag.TYPE_GUI_BACK ||
                                                type == ItemTag.TYPE_GUI_CANCEL
                                ) {
                                        plugin.soundManager.playGlobalClickSound(player)
                                        plugin.worldSettingsGui.open(player, worldData)
                                }
                        }
                        SettingsAction.MANAGE_PORTALS -> {
                                event.isCancelled = true
                                if (event.clickedInventory != event.view.topInventory) return

                                if (type == ItemTag.TYPE_GUI_NAV_NEXT ||
                                                type == ItemTag.TYPE_GUI_NAV_PREV
                                ) {
                                        val lore = item?.itemMeta?.lore() ?: return
                                        lore.forEach { line ->
                                                val plainLine =
                                                        PlainTextComponentSerializer.plainText()
                                                                .serialize(line)
                                                if (plainLine.startsWith("PAGE_TARGET: ")) {
                                                        val targetPage =
                                                                plainLine
                                                                        .removePrefix(
                                                                                "PAGE_TARGET: "
                                                                        )
                                                                        .trim()
                                                                        .toIntOrNull()
                                                                        ?: return@forEach
                                                        plugin.soundManager.playClickSound(
                                                                player,
                                                                item,
                                                                "world_settings"
                                                        )
                                                        plugin.worldSettingsGui
                                                                .openPortalManagement(
                                                                        player,
                                                                        worldData,
                                                                        targetPage
                                                                )
                                                }
                                        }
                                        return
                                }

                                if (type == ItemTag.TYPE_PORTAL) {
                                        val portalId = ItemTag.getPortalUuid(item) ?: return
                                        val isBedrock =
                                                plugin.playerPlatformResolver.isBedrock(player)
                                        if (!isBedrock && event.isRightClick) {
                                                plugin.soundManager.playClickSound(
                                                        player,
                                                        item,
                                                        "world_settings"
                                                )
                                                val portal =
                                                        plugin.portalRepository.findAll().find {
                                                                it.id == portalId
                                                        }
                                                if (portal != null) {
                                                        val refundResult =
                                                                if (portal.isGate()) {
                                                                        plugin.portalManager
                                                                                .refundPointsForRemovedGate(portal)
                                                                } else {
                                                                        null
                                                                }
                                                        if (!portal.isGate()) {
                                                                val world =
                                                                        Bukkit.getWorld(portal.worldName)
                                                                val block =
                                                                        world?.getBlockAt(
                                                                                portal.x,
                                                                                portal.y,
                                                                                portal.z
                                                                        )
                                                                if (block != null &&
                                                                                block.type ==
                                                                                        Material.END_PORTAL_FRAME
                                                                ) {
                                                                        block.type = Material.AIR
                                                                }
                                                        }
                                                        plugin.portalManager.removePortalVisuals(
                                                                portal.id
                                                        )
                                                        plugin.portalRepository.removePortal(
                                                                portal.id
                                                        )

                                                        val returnItem = if (portal.isGate()) {
                                                                me.awabi2048.myworldmanager.util.WorldGateItemUtil
                                                                        .createBaseWorldGateItem(
                                                                                lang,
                                                                                player
                                                                        )
                                                        } else {
                                                                me.awabi2048.myworldmanager.util
                                                                        .PortalItemUtil
                                                                        .createBasePortalItem(
                                                                                lang,
                                                                                player
                                                                        )
                                                        }
                                                        if (portal.worldUuid != null) {
                                                                val destData =
                                                                        plugin.worldConfigRepository
                                                                                .findByUuid(
                                                                                        portal.worldUuid!!
                                                                                )
                                                                if (portal.isGate()) {
                                                                        me.awabi2048.myworldmanager.util.WorldGateItemUtil
                                                                                .bindWorld(
                                                                                        returnItem,
                                                                                        portal.worldUuid!!,
                                                                                        worldName = destData?.name
                                                                                                ?: lang.getMessage(
                                                                                                        player,
                                                                                                        "general.unknown"
                                                                                                ),
                                                                                        lang,
                                                                                        player
                                                                                )
                                                                } else {
                                                                        me.awabi2048.myworldmanager.util
                                                                                .PortalItemUtil.bindWorld(
                                                                                returnItem,
                                                                                portal.worldUuid!!,
                                                                                worldName = destData?.name
                                                                                                ?: lang.getMessage(
                                                                                                        player,
                                                                                                        "general.unknown"
                                                                                                ),
                                                                                lang,
                                                                                player
                                                                        )
                                                                }
                                                        } else if (portal.targetWorldName != null) {
                                                                val displayName =
                                                                        plugin.config.getString(
                                                                                "portal_targets.${portal.targetWorldName}"
                                                                        )
                                                                                ?: portal.targetWorldName!!
                                                                if (portal.isGate()) {
                                                                        me.awabi2048.myworldmanager.util.WorldGateItemUtil
                                                                                .bindExternalWorld(
                                                                                        returnItem,
                                                                                        portal.targetWorldName!!,
                                                                                        displayName,
                                                                                        lang,
                                                                                        player
                                                                                )
                                                                } else {
                                                                        me.awabi2048.myworldmanager.util
                                                                                .PortalItemUtil
                                                                                .bindExternalWorld(
                                                                                        returnItem,
                                                                                        portal.targetWorldName!!,
                                                                                        displayName,
                                                                                        lang,
                                                                                        player
                                                                                )
                                                                }
                                                        }
                                                        player.inventory.addItem(returnItem)

                                                        if (portal.isGate()) {
                                                                val ownerName =
                                                                        Bukkit.getOfflinePlayer(
                                                                                        portal.ownerUuid
                                                                                ).name
                                                                                ?: portal.ownerUuid
                                                                                        .toString()
                                                                player.sendMessage(
                                                                        lang.getMessage(
                                                                                player,
                                                                                "messages.world_gate_removed_refund",
                                                                                mapOf(
                                                                                        "points" to
                                                                                                (refundResult
                                                                                                                ?.points
                                                                                                        ?: 0),
                                                                                        "percent" to
                                                                                                (refundResult
                                                                                                                ?.percent
                                                                                                        ?: 0),
                                                                                        "owner" to ownerName
                                                                                )
                                                                        )
                                                                )
                                                        } else {
                                                                player.sendMessage(
                                                                        lang.getMessage(
                                                                                player,
                                                                                "messages.portal_removed"
                                                                        )
                                                                )
                                                        }
                                                        plugin.worldSettingsGui
                                                                .openPortalManagement(
                                                                        player,
                                                                        worldData
                                                                )
                                                }
                                        } else if (event.isLeftClick) {
                                                val portal =
                                                        plugin.portalRepository.findAll().find {
                                                                it.id == portalId
                                                        }
                                                if (portal != null) {
                                                        plugin.soundManager.playClickSound(
                                                                player,
                                                                item,
                                                                "world_settings"
                                                        )
                                                        plugin.portalManager.addIgnorePlayer(player)
                                                        player.teleport(
                                                                portal.getCenterLocation()
                                                                        .add(0.0, 1.0, 0.0)
                                                        )
                                                        plugin.soundManager.playTeleportSound(
                                                                player
                                                        )
                                                        player.sendMessage(
                                                                lang.getMessage(
                                                                        player,
                                                                        "messages.warp_generic"
                                                                )
                                                        )
                                                        player.closeInventory()
                                                }
                                        }
                                } else if (type == ItemTag.TYPE_GUI_CANCEL) {
                                        handleCommandCancel()
                                }
                        }
                        SettingsAction.CRITICAL_SETTINGS -> {
                                event.isCancelled = true
                                if (event.clickedInventory != event.view.topInventory) return

                                when (type) {
                                        ItemTag.TYPE_GUI_CANCEL -> {
                                                handleCommandCancel()
                                        }
                                        ItemTag.TYPE_GUI_SETTING_RESET_EXPANSION -> {
                                                if (worldData.borderExpansionLevel <= 0) return
                                                plugin.soundManager.playClickSound(
                                                        player,
                                                        item,
                                                        "world_settings"
                                                )
                                                val title = LegacyComponentSerializer.legacySection().deserialize(
                                                        plugin.languageManager.getMessage(player, "gui.confirm.reset_expansion.title")
                                                )
                                                val bodyLines = plugin.languageManager
                                                        .getMessageList(player, "gui.confirm.reset_expansion.lore")
                                                        .map { LegacyComponentSerializer.legacySection().deserialize(it) }
                                                plugin.settingsSessionManager.updateSessionAction(
                                                        player,
                                                        worldData.uuid,
                                                        SettingsAction.RESET_EXPANSION_CONFIRM,
                                                        isGui = true
                                                )
                                                DialogConfirmManager.showConfirmationByPreference(
                                                        player,
                                                        plugin,
                                                        title,
                                                        bodyLines,
                                                        "mwm:confirm/reset_expansion",
                                                        "mwm:confirm/cancel",
                                                        plugin.languageManager.getMessage(player, "gui.common.confirm"),
                                                        plugin.languageManager.getMessage(player, "gui.common.cancel"),
                                                        onBedrockConfirm = {
                                                                handleBedrockDialogAction(
                                                                        player,
                                                                        worldData,
                                                                        "mwm:confirm/reset_expansion"
                                                                )
                                                        },
                                                        onBedrockCancel = {
                                                                handleBedrockDialogCancel(player, worldData)
                                                        }
                                                ) {
                                                        plugin.worldSettingsGui.openResetExpansionConfirmation(
                                                                player,
                                                                worldData
                                                        )
                                                }
                                        }
                                        ItemTag.TYPE_GUI_SETTING_ARCHIVE -> {
                                                // クールダウンチェック（GUI側でも再確認）- プレイヤーごとのクールタイム
                                                val cooldownHours = plugin.config.getLong("critical_settings.archive_cooldown_hours", 24L)
                                                val dtFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                                val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                                                val isOnCooldown = stats.lastArchiveActionAt?.let { lastAt ->
                                                    try {
                                                        val lastAction = java.time.LocalDateTime.parse(lastAt, dtFormatter)
                                                        java.time.Duration.between(lastAction, java.time.LocalDateTime.now()).toHours() < cooldownHours
                                                    } catch (e: Exception) { false }
                                                } ?: false
                                                if (isOnCooldown) {
                                                    val hoursRemaining = stats.lastArchiveActionAt?.let { lastAt ->
                                                        try {
                                                            val lastAction = java.time.LocalDateTime.parse(lastAt, dtFormatter)
                                                            val elapsed = java.time.Duration.between(lastAction, java.time.LocalDateTime.now()).toHours()
                                                            (cooldownHours - elapsed).coerceAtLeast(0L)
                                                        } catch (e: Exception) { 0L }
                                                    } ?: 0L
                                                    player.sendMessage(plugin.languageManager.getMessage(
                                                        player,
                                                        "messages.archive_cooldown",
                                                        mapOf("cooldown_hours" to cooldownHours, "hours_remaining" to hoursRemaining)
                                                    ))
                                                    return
                                                }
                                                plugin.soundManager.playClickSound(player, item, "world_settings")
                                                val title = LegacyComponentSerializer.legacySection().deserialize(
                                                    plugin.languageManager.getMessage(player, "gui.archive.confirm_title")
                                                )
                                                val bodyLines = listOf(
                                                    LegacyComponentSerializer.legacySection().deserialize(
                                                        plugin.languageManager.getMessage(player, "gui.common.confirm_warning")
                                                    )
                                                )
                                                plugin.settingsSessionManager.updateSessionAction(
                                                    player,
                                                    worldData.uuid,
                                                    SettingsAction.ARCHIVE_WORLD_FROM_CRITICAL,
                                                    isGui = true
                                                )
                                                DialogConfirmManager.showConfirmationByPreference(
                                                    player,
                                                    plugin,
                                                    title,
                                                    bodyLines,
                                                    "mwm:confirm/archive_world_critical",
                                                    "mwm:confirm/cancel",
                                                    plugin.languageManager.getMessage(player, "gui.archive.confirm"),
                                                    plugin.languageManager.getMessage(player, "gui.common.cancel"),
                                                    onBedrockConfirm = {
                                                            handleBedrockDialogAction(
                                                                    player,
                                                                    worldData,
                                                                    "mwm:confirm/archive_world_critical"
                                                            )
                                                    },
                                                    onBedrockCancel = {
                                                            handleBedrockDialogCancel(player, worldData)
                                                    }
                                                ) {
                                                    plugin.worldSettingsGui.openArchiveConfirmation(player, worldData)
                                                }
                                        }
                                        ItemTag.TYPE_GUI_SETTING_DELETE_WORLD -> {
                                                plugin.soundManager.playClickSound(
                                                        player,
                                                        item,
                                                        "world_settings"
                                                )
                                                val title = LegacyComponentSerializer.legacySection().deserialize(
                                                        plugin.languageManager.getMessage(player, "gui.confirm.delete_1.title")
                                                )
                                                val bodyLines = plugin.languageManager
                                                        .getMessageList(player, "gui.confirm.delete_1.lore")
                                                        .map { LegacyComponentSerializer.legacySection().deserialize(it) }
                                                plugin.settingsSessionManager.updateSessionAction(
                                                        player,
                                                        worldData.uuid,
                                                        SettingsAction.DELETE_WORLD_CONFIRM,
                                                        isGui = true
                                                )
                                                DialogConfirmManager.showConfirmationByPreference(
                                                        player,
                                                        plugin,
                                                        title,
                                                        bodyLines,
                                                        "mwm:confirm/delete_world_step1",
                                                        "mwm:confirm/cancel",
                                                        plugin.languageManager.getMessage(player, "gui.confirm.delete_1.next"),
                                                        plugin.languageManager.getMessage(player, "gui.common.cancel"),
                                                        onBedrockConfirm = {
                                                                handleBedrockDialogAction(
                                                                        player,
                                                                        worldData,
                                                                        "mwm:confirm/delete_world_step1"
                                                                )
                                                        },
                                                        onBedrockCancel = {
                                                                handleBedrockDialogCancel(player, worldData)
                                                        }
                                                ) {
                                                        plugin.worldSettingsGui.openDeleteWorldConfirmation1(
                                                                player,
                                                                worldData
                                                        )
                                                }
                                        }
                                }
                        }
                        SettingsAction.RESET_EXPANSION_CONFIRM -> {
                                event.isCancelled = true
                                if (event.clickedInventory != event.view.topInventory) return

                                if (type == ItemTag.TYPE_GUI_CANCEL) {
                                        plugin.soundManager.playClickSound(
                                                player,
                                                item,
                                                "world_settings"
                                        )
                                        plugin.worldSettingsGui.openCriticalSettings(
                                                player,
                                                worldData
                                        )
                                } else if (type == ItemTag.TYPE_GUI_CONFIRM) {
                                        plugin.soundManager.playClickSound(
                                                player,
                                                item,
                                                "world_settings"
                                        )
                                        val totalExpCost =
                                                calculateTotalExpansionCost(
                                                        worldData.borderExpansionLevel
                                                )
                                        val stats =
                                                plugin.playerStatsRepository.findByUuid(
                                                        player.uniqueId
                                                )

                                        val worldName =
                                                worldData.customWorldName
                                                        ?: "my_world.${worldData.uuid}"
                                        val world = Bukkit.getWorld(worldName)
                                        if (world != null) {
                                                val initialSize =
                                                        plugin.config.getDouble(
                                                                "expansion.initial_size",
                                                                100.0
                                                        )
                                                world.worldBorder.size = initialSize
                                        }

                                        val refundRate =
                                                plugin.config.getDouble(
                                                        "critical_settings.refund_percentage",
                                                        0.5
                                                )
                                        val refund = (totalExpCost * refundRate).toInt()

                                        stats.worldPoint += refund
                                        worldData.cumulativePoints -= totalExpCost
                                        worldData.borderExpansionLevel = 0

                                        plugin.playerStatsRepository.save(stats)
                                        plugin.worldConfigRepository.save(worldData)

                                        player.sendMessage(
                                                plugin.languageManager.getMessage(
                                                        player,
                                                        "messages.expansion_reset_success",
                                                        mapOf("points" to refund)
                                                )
                                        )
                                        player.closeInventory()
                                        plugin.settingsSessionManager.endSession(player)
                                }
                        }
                        SettingsAction.DELETE_WORLD_CONFIRM -> {
                                event.isCancelled = true
                                if (event.clickedInventory != event.view.topInventory) return

                                if (type == ItemTag.TYPE_GUI_CANCEL) {
                                        plugin.soundManager.playClickSound(
                                                player,
                                                item,
                                                "world_settings"
                                        )
                                        plugin.worldSettingsGui.openCriticalSettings(
                                                player,
                                                worldData
                                        )
                                } else if (type == ItemTag.TYPE_GUI_SETTING_DELETE_WORLD) {
                                        plugin.soundManager.playClickSound(
                                                player,
                                                item,
                                                "world_settings"
                                        )
                                        plugin.worldSettingsGui.openDeleteWorldConfirmation2(
                                                player,
                                                worldData
                                        )
                                }
                        }
                        SettingsAction.DELETE_WORLD_CONFIRM_FINAL -> {
                                event.isCancelled = true
                                if (event.clickedInventory != event.view.topInventory) return

                                if (type == ItemTag.TYPE_GUI_CANCEL) {
                                        plugin.soundManager.playClickSound(
                                                player,
                                                item,
                                                "world_settings"
                                        )
                                        plugin.worldSettingsGui.openCriticalSettings(
                                                player,
                                                worldData
                                        )
                                } else if (type == ItemTag.TYPE_GUI_CONFIRM) {
                                        plugin.soundManager.playClickSound(
                                                player,
                                                item,
                                                "world_settings"
                                        )
                                        val refundRate =
                                                plugin.config.getDouble(
                                                        "critical_settings.refund_percentage",
                                                        0.5
                                                )
                                        val refund =
                                                (worldData.cumulativePoints * refundRate).toInt()

                                        val stats =
                                                plugin.playerStatsRepository.findByUuid(
                                                        player.uniqueId
                                                )

                                        player.closeInventory()
                                        player.sendMessage(
                                                plugin.languageManager.getMessage(
                                                        player,
                                                        "messages.world_delete_start",
                                                        mapOf("world" to worldData.name)
                                                )
                                        )

                                        plugin.worldService.deleteWorld(worldData.uuid)
                                                .thenAccept { success: Boolean ->
                                                        Bukkit.getScheduler()
                                                                .runTask(
                                                                        plugin,
                                                                        Runnable {
                                                                                if (success) {
                                                                                         // 設定に応じてスロットを削減
                                                                                         if (plugin.config.getBoolean("deletion.reduce_owner_slot", false)) {
                                                                                                 if (stats.unlockedWorldSlot > 0) {
                                                                                                         stats.unlockedWorldSlot--
                                                                                                 }
                                                                                         }

                                                                                         plugin.playerStatsRepository
                                                                                                 .save(
                                                                                                         stats
                                                                                                 )
                                                                                         player.sendMessage(
                                                                                                 plugin.languageManager
                                                                                                         .getMessage(
                                                                                                                 player,
                                                                                                                 "messages.world_delete_success",
                                                                                                                 mapOf(
                                                                                                                         "points" to
                                                                                                                                 refund
                                                                                                                 )
                                                                                                         )
                                                                                         )
                                                                                } else {
                                                                                        player.sendMessage(
                                                                                                plugin.languageManager
                                                                                                        .getMessage(
                                                                                                                player,
                                                                                                                "messages.world_delete_fail"
                                                                                                        )
                                                                                        )
                                                                                }
                                                                        }
                                                                )
                                                }
                                        plugin.settingsSessionManager.endSession(player)
                                }
                        }
                        SettingsAction.ARCHIVE_WORLD -> {
                                event.isCancelled = true
                                if (event.clickedInventory != event.view.topInventory) return

                                if (type == ItemTag.TYPE_GUI_CANCEL) {
                                        handleCommandCancel()
                                } else if (type == ItemTag.TYPE_GUI_CONFIRM) {
                                        plugin.soundManager.playClickSound(
                                                player,
                                                item,
                                                "world_settings"
                                        )
                                        player.sendMessage(
                                                plugin.languageManager.getMessage(
                                                        player,
                                                        "messages.archive_success",
                                                        mapOf("world" to worldData.name)
                                                )
                                        )
                                        worldData.isArchived = true
                                        plugin.worldConfigRepository.save(worldData)
                                        plugin.settingsSessionManager.endSession(player)
                                        player.closeInventory()
                                }
                        }
                        SettingsAction.ARCHIVE_WORLD_FROM_CRITICAL -> {
                                event.isCancelled = true
                                if (event.clickedInventory != event.view.topInventory) return

                                if (type == ItemTag.TYPE_GUI_CANCEL) {
                                        plugin.soundManager.playClickSound(player, item, "world_settings")
                                        plugin.worldSettingsGui.openCriticalSettings(player, worldData)
                                } else if (type == ItemTag.TYPE_GUI_CONFIRM) {
                                        plugin.soundManager.playClickSound(player, item, "world_settings")
                                        player.closeInventory()
                                        player.sendMessage(plugin.languageManager.getMessage(player, "messages.archive_start"))
                                        plugin.worldService.archiveWorld(worldData.uuid)
                                            .thenAccept { success: Boolean ->
                                                Bukkit.getScheduler().runTask(plugin, Runnable {
                                                    if (success) {
                                                        // クールダウン記録（プレイヤーごと）
                                                        val now = java.time.LocalDateTime.now()
                                                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                                                        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                                                        stats.lastArchiveActionAt = now
                                                        plugin.playerStatsRepository.save(stats)
                                                        player.sendMessage(plugin.languageManager.getMessage(
                                                            player,
                                                            "messages.archive_success",
                                                            mapOf("world" to worldData.name)
                                                        ))
                                                    } else {
                                                        player.sendMessage(plugin.languageManager.getMessage(
                                                            player,
                                                            "messages.archive_failed"
                                                        ))
                                                    }
                                                })
                                            }
                                        plugin.settingsSessionManager.endSession(player)
                                }
                        }
                        SettingsAction.UNARCHIVE_CONFIRM -> {
                                event.isCancelled = true
                                if (event.clickedInventory != event.view.topInventory) return

                                if (type == ItemTag.TYPE_GUI_CANCEL) {
                                        plugin.soundManager.playClickSound(
                                                player,
                                                item,
                                                "world_settings"
                                        )
                                        plugin.settingsSessionManager.endSession(player)
                                        plugin.playerWorldGui.open(player)
                                } else if (type == ItemTag.TYPE_GUI_CONFIRM) {
                                        plugin.soundManager.playClickSound(
                                                player,
                                                item,
                                                "world_settings"
                                        )
                                        player.closeInventory()
                                        player.sendMessage(
                                                plugin.languageManager.getMessage(
                                                        player,
                                                        "messages.unarchive_start"
                                                )
                                        )

                                        plugin.worldService.unarchiveWorld(worldData.uuid)
                                                .thenAccept { success: Boolean ->
                                                        Bukkit.getScheduler()
                                                                .runTask(
                                                                        plugin,
                                                                        Runnable {
                                                                                if (success) {
                                                                                        // クールダウン記録（プレイヤーごと）
                                                                                        val now = java.time.LocalDateTime.now()
                                                                                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                                                                                        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                                                                                        stats.lastArchiveActionAt = now
                                                                                        plugin.playerStatsRepository.save(stats)
                                                                                        player.sendMessage(
                                                                                                plugin.languageManager
                                                                                                        .getMessage(
                                                                                                                player,
                                                                                                                "messages.unarchive_success"
                                                                                                        )
                                                                                        )
                                                                                        plugin.worldService
                                                                                                .teleportToWorld(
                                                                                                        player,
                                                                                                        worldData
                                                                                                                .uuid
                                                                                                 )
                                                                                } else {
                                                                                        player.sendMessage(
plugin.languageManager
                                                                                                        .getMessage(
                                                                                                                player,
                                                                                                                "error.unarchive_failed"
                                                                                                        )
                                                                                        )
                                                                                }
                                                                        }
                                                                )
                                                }
                                        plugin.settingsSessionManager.endSession(player)
                                }
                        }
                        else -> {}
                }
        }

        private fun openBedrockExpandDirectionConfirmForm(
                player: Player,
                worldUuid: UUID,
                direction: org.bukkit.block.BlockFace,
                cost: Int
        ): Boolean {
                if (!plugin.playerPlatformResolver.isBedrock(player)) {
                        return false
                }
                if (!plugin.floodgateFormBridge.isAvailable(player)) {
                        return false
                }

                val session = plugin.settingsSessionManager.getSession(player) ?: return false
                session.action = SettingsAction.EXPAND_CONFIRM
                session.expansionDirection = direction
                session.setMetadata("expand_cost", cost)

                val lang = plugin.languageManager
                val directionKey =
                        when (direction) {
                                org.bukkit.block.BlockFace.NORTH_WEST -> "general.direction.north_west"
                                org.bukkit.block.BlockFace.NORTH_EAST -> "general.direction.north_east"
                                org.bukkit.block.BlockFace.SOUTH_WEST -> "general.direction.south_west"
                                org.bukkit.block.BlockFace.SOUTH_EAST -> "general.direction.south_east"
                                else -> "general.direction.unknown"
                        }
                val directionName = lang.getMessage(player, directionKey)
                val methodText =
                        lang.getMessage(
                                player,
                                "gui.expansion.method_direction",
                                mapOf("direction" to directionName)
                        )
                val content =
                        listOf(
                                        lang.getMessage(
                                                player,
                                                "gui.expansion.method",
                                                mapOf("method" to methodText)
                                        ),
                                        lang.getMessage(
                                                player,
                                                "gui.expansion.cost",
                                                mapOf("cost" to cost)
                                        ),
                                        lang.getMessage(player, "gui.expansion.warning")
                                )
                                .joinToString("\n")

                return plugin.floodgateFormBridge.sendSimpleForm(
                        player = player,
                        title = lang.getMessage(player, "gui.expansion.confirm_title"),
                        content = content,
                        buttons =
                                listOf(
                                        lang.getMessage(player, "gui.common.confirm"),
                                        lang.getMessage(player, "messages.expand_retry_button")
                                ),
                        onSelect = { index ->
                                val latestSession = plugin.settingsSessionManager.getSession(player)
                                                ?: return@sendSimpleForm
                                if (latestSession.worldUuid != worldUuid) {
                                        return@sendSimpleForm
                                }

                                if (index == 0) {
                                        val latestWorld =
                                                plugin.worldConfigRepository.findByUuid(worldUuid)
                                                        ?: return@sendSimpleForm
                                        handleExpandConfirm(player, latestWorld)
                                        return@sendSimpleForm
                                }

                                latestSession.action = SettingsAction.EXPAND_DIRECTION_WAIT
                                player.sendMessage(
                                        lang.getMessage(player, "messages.expand_direction_prompt_bedrock")
                                )
                        }
                )
        }

        private fun openBedrockSettingTextInputForm(
                player: Player,
                worldData: WorldData,
                isDescriptionInput: Boolean
        ): Boolean {
                if (!plugin.playerPlatformResolver.isBedrock(player)) {
                        return false
                }
                if (!plugin.floodgateFormBridge.isAvailable(player)) {
                        return false
                }

                val lang = plugin.languageManager
                val titleKey =
                        if (isDescriptionInput) {
                                "gui.bedrock.input.description.title"
                        } else {
                                "gui.bedrock.input.rename.title"
                        }
                val labelKey =
                        if (isDescriptionInput) {
                                "gui.bedrock.input.description.label"
                        } else {
                                "gui.bedrock.input.rename.label"
                        }
                val placeholderKey =
                        if (isDescriptionInput) {
                                "gui.bedrock.input.description.placeholder"
                        } else {
                                "gui.bedrock.input.rename.placeholder"
                        }
                val initialValue = if (isDescriptionInput) worldData.description else worldData.name
                val worldUuid = worldData.uuid

                player.closeInventory()

                val opened =
                        plugin.floodgateFormBridge.sendCustomInputForm(
                                player = player,
                                title = lang.getMessage(player, titleKey),
                                label = lang.getMessage(player, labelKey),
                                placeholder = lang.getMessage(player, placeholderKey),
                                defaultValue = initialValue,
                                onSubmit = { value ->
                                        val latestWorld =
                                                plugin.worldConfigRepository.findByUuid(worldUuid)
                                                        ?: return@sendCustomInputForm
                                        if (isDescriptionInput) {
                                                applyWorldDescriptionUpdate(
                                                        player,
                                                        latestWorld,
                                                        value
                                                )
                                        } else {
                                                applyWorldNameUpdate(player, latestWorld, value)
                                        }
                                },
                                onClosed = {
                                        if (!player.isOnline) {
                                                return@sendCustomInputForm
                                        }
                                        val latestWorld =
                                                plugin.worldConfigRepository.findByUuid(worldUuid)
                                                        ?: return@sendCustomInputForm
                                        plugin.worldSettingsGui.open(player, latestWorld)
                                }
                        )
                return opened
        }

        private fun openBedrockWorldInfoInputForm(player: Player, worldData: WorldData): Boolean {
                if (!plugin.playerPlatformResolver.isBedrock(player)) {
                        return false
                }
                if (!plugin.floodgateFormBridge.isAvailable(player)) {
                        return false
                }

                val lang = plugin.languageManager
                val worldUuid = worldData.uuid
                player.closeInventory()

                return plugin.floodgateFormBridge.sendCustomForm(
                        player = player,
                        title = lang.getMessage(player, "gui.bedrock.input.info_form.title"),
                        inputs =
                                listOf(
                                        me.awabi2048.myworldmanager.ui.bedrock.FloodgateFormBridge
                                                .CustomFormInput(
                                                        label =
                                                                lang.getMessage(
                                                                        player,
                                                                        "gui.bedrock.input.rename.label"
                                                                ),
                                                        placeholder =
                                                                lang.getMessage(
                                                                        player,
                                                                        "gui.bedrock.input.rename.placeholder"
                                                                ),
                                                        defaultValue = worldData.name
                                                ),
                                        me.awabi2048.myworldmanager.ui.bedrock.FloodgateFormBridge
                                                .CustomFormInput(
                                                        label =
                                                                lang.getMessage(
                                                                        player,
                                                                        "gui.bedrock.input.description.label"
                                                                ),
                                                        placeholder =
                                                                lang.getMessage(
                                                                        player,
                                                                        "gui.bedrock.input.description.placeholder"
                                                                ),
                                                        defaultValue = worldData.description
                                                )
                                ),
                        onSubmit = { values ->
                                val latestWorld =
                                        plugin.worldConfigRepository.findByUuid(worldUuid)
                                                ?: return@sendCustomForm
                                val newName = values.getOrNull(0).orEmpty().trim()
                                val newDescription = values.getOrNull(1).orEmpty().trim()
                                applyWorldInfoUpdate(
                                        player,
                                        latestWorld,
                                        newName,
                                        newDescription
                                )
                        },
                        onClosed = {
                                if (!player.isOnline) {
                                        return@sendCustomForm
                                }
                                val latestWorld =
                                        plugin.worldConfigRepository.findByUuid(worldUuid)
                                                ?: return@sendCustomForm
                                plugin.worldSettingsGui.open(player, latestWorld)
                        }
                )
        }

        private fun openBedrockMemberInviteInputForm(
                player: Player,
                worldData: WorldData,
                forceAddMode: Boolean
        ): Boolean {
                if (!plugin.playerPlatformResolver.isBedrock(player)) {
                        return false
                }
                if (!plugin.floodgateFormBridge.isAvailable(player)) {
                        return false
                }

                val lang = plugin.languageManager
                val worldUuid = worldData.uuid
                player.closeInventory()

                return plugin.floodgateFormBridge.sendCustomInputForm(
                        player = player,
                        title = lang.getMessage(player, "gui.bedrock.input.member_invite.title"),
                        label = lang.getMessage(player, "gui.bedrock.input.member_invite.label"),
                        placeholder =
                                lang.getMessage(player, "gui.bedrock.input.member_invite.placeholder"),
                        defaultValue = "",
                        onSubmit = { value ->
                                val latestWorld =
                                        plugin.worldConfigRepository.findByUuid(worldUuid)
                                                ?: return@sendCustomInputForm
                                applyMemberInvite(player, latestWorld, value, forceAddMode)
                        },
                        onClosed = {
                                if (!player.isOnline) {
                                        return@sendCustomInputForm
                                }
                                reopenMemberManagementLatest(player, worldUuid, playSound = false)
                        }
                )
        }

        private fun openBedrockAnnouncementActionForm(player: Player, worldData: WorldData): Boolean {
                if (!plugin.playerPlatformResolver.isBedrock(player)) {
                        return false
                }
                if (!plugin.floodgateFormBridge.isAvailable(player)) {
                        return false
                }

                val lang = plugin.languageManager
                val worldUuid = worldData.uuid
                player.closeInventory()

                return plugin.floodgateFormBridge.sendSimpleForm(
                        player = player,
                        title =
                                lang.getMessage(
                                        player,
                                        "gui.bedrock.input.announcement_menu.title"
                                ),
                        content =
                                lang.getMessage(
                                        player,
                                        "gui.bedrock.input.announcement_menu.content"
                                ),
                        buttons =
                                listOf(
                                        lang.getMessage(
                                                player,
                                                "gui.bedrock.input.announcement_menu.edit"
                                        ),
                                        lang.getMessage(
                                                player,
                                                "gui.bedrock.input.announcement_menu.reset"
                                        )
                                ),
                        onSelect = { index ->
                                val latestWorld =
                                        plugin.worldConfigRepository.findByUuid(worldUuid)
                                                ?: return@sendSimpleForm
                                if (index == 1) {
                                        latestWorld.announcementMessages.clear()
                                        plugin.worldConfigRepository.save(latestWorld)
                                        player.sendMessage(
                                                lang.getMessage(player, "messages.announcement_reset")
                                        )
                                        plugin.worldSettingsGui.open(player, latestWorld)
                                        return@sendSimpleForm
                                }
                                if (!openBedrockAnnouncementEditForm(player, latestWorld)) {
                                        player.sendMessage(
                                                lang.getMessage(
                                                        player,
                                                        "messages.operation_cancelled"
                                                )
                                        )
                                        plugin.worldSettingsGui.open(player, latestWorld)
                                }
                        },
                        onClosed = {
                                if (!player.isOnline) {
                                        return@sendSimpleForm
                                }
                                val latestWorld =
                                        plugin.worldConfigRepository.findByUuid(worldUuid)
                                                ?: return@sendSimpleForm
                                plugin.worldSettingsGui.open(player, latestWorld)
                        }
                )
        }

        private fun openBedrockAnnouncementEditForm(player: Player, worldData: WorldData): Boolean {
                if (!plugin.playerPlatformResolver.isBedrock(player)) {
                        return false
                }
                if (!plugin.floodgateFormBridge.isAvailable(player)) {
                        return false
                }

                val lang = plugin.languageManager
                val maxLines = plugin.config.getInt("announcement.max_lines", 5)
                val maxLength = plugin.config.getInt("announcement.max_line_length", 100)
                val worldUuid = worldData.uuid
                val inputs =
                        (0 until maxLines).map { index ->
                                val current = worldData.announcementMessages.getOrNull(index).orEmpty()
                                me.awabi2048.myworldmanager.ui.bedrock.FloodgateFormBridge
                                        .CustomFormInput(
                                                label =
                                                        lang.getMessage(
                                                                player,
                                                                "gui.bedrock.input.announcement_edit.label",
                                                                mapOf("line" to index + 1)
                                                        ),
                                                placeholder =
                                                        lang.getMessage(
                                                                player,
                                                                "gui.bedrock.input.announcement_edit.placeholder",
                                                                mapOf("max" to maxLength)
                                                        ),
                                                defaultValue =
                                                        current
                                                                .removePrefix("§f")
                                                                .replace("§", "&")
                                        )
                        }

                player.closeInventory()

                return plugin.floodgateFormBridge.sendCustomForm(
                        player = player,
                        title =
                                lang.getMessage(
                                        player,
                                        "gui.bedrock.input.announcement_edit.title",
                                        mapOf("max_lines" to maxLines, "max_length" to maxLength)
                                ),
                        inputs = inputs,
                        onSubmit = { values ->
                                val latestWorld =
                                        plugin.worldConfigRepository.findByUuid(worldUuid)
                                                ?: return@sendCustomForm
                                applyAnnouncementUpdateFromForm(
                                        player,
                                        latestWorld,
                                        values
                                )
                        },
                        onClosed = {
                                if (!player.isOnline) {
                                        return@sendCustomForm
                                }
                                val latestWorld =
                                        plugin.worldConfigRepository.findByUuid(worldUuid)
                                                ?: return@sendCustomForm
                                plugin.worldSettingsGui.open(player, latestWorld)
                        }
                )
        }

        private fun applyAnnouncementUpdateFromForm(
                player: Player,
                worldData: WorldData,
                rawInputs: List<String>
        ) {
                val lang = plugin.languageManager
                val maxLines = plugin.config.getInt("announcement.max_lines", 5)
                val maxLength = plugin.config.getInt("announcement.max_line_length", 100)
                val blockedStrings = plugin.config.getStringList("announcement.blocked_strings")

                val trimmed = rawInputs.map { it.trim() }.filter { it.isNotEmpty() }
                if (trimmed.size > maxLines) {
                        player.sendMessage(
                                lang.getMessage(
                                        player,
                                        "messages.announcement_invalid_length",
                                        mapOf(
                                                "max_lines" to maxLines,
                                                "max_length" to maxLength
                                        )
                                )
                        )
                        plugin.worldSettingsGui.open(player, worldData)
                        return
                }

                for (line in trimmed) {
                        if (line.length > maxLength) {
                                player.sendMessage(
                                        lang.getMessage(
                                                player,
                                                "messages.announcement_invalid_length",
                                                mapOf(
                                                        "max_lines" to maxLines,
                                                        "max_length" to maxLength
                                                )
                                        )
                                )
                                plugin.worldSettingsGui.open(player, worldData)
                                return
                        }

                        val blocked =
                                blockedStrings.firstOrNull {
                                        line.contains(it, ignoreCase = true)
                                }
                        if (blocked != null) {
                                player.sendMessage(
                                        lang.getMessage(
                                                player,
                                                "messages.announcement_blocked_string",
                                                mapOf("string" to blocked)
                                        )
                                )
                                plugin.worldSettingsGui.open(player, worldData)
                                return
                        }
                }

                worldData.announcementMessages.clear()
                trimmed.forEach { line ->
                        worldData.announcementMessages.add("§f${line.replace("&", "§")}")
                }

                plugin.worldConfigRepository.save(worldData)
                player.sendMessage(lang.getMessage(player, "messages.announcement_set"))
                plugin.worldSettingsGui.open(player, worldData)
        }

        private fun canCancelMemberInvite(player: Player, worldData: WorldData): Boolean {
                return worldData.owner == player.uniqueId ||
                        worldData.moderators.contains(player.uniqueId)
        }

        private fun reopenMemberManagementLatest(
                player: Player,
                worldUuid: UUID,
                playSound: Boolean = false
        ) {
                val latestWorld = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return
                val page =
                        (plugin.settingsSessionManager.getSession(player)
                                ?.getMetadata("member_management_page") as? Int)
                                ?.coerceAtLeast(0)
                                ?: 0
                player.closeInventory()
                Bukkit.getScheduler().runTask(
                        plugin,
                        Runnable {
                                if (!player.isOnline) {
                                        return@Runnable
                                }
                                plugin.worldSettingsGui.openMemberManagement(
                                        player,
                                        latestWorld,
                                        page,
                                        playSound
                                )
                        }
                )
        }

        private fun cancelMemberInviteByDecisionId(
                player: Player,
                worldUuid: UUID,
                decisionId: UUID
        ) {
                val lang = plugin.languageManager
                val latestWorld = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return
                if (!canCancelMemberInvite(player, latestWorld)) {
                        player.sendMessage(lang.getMessage(player, "general.no_permission"))
                        reopenMemberManagementLatest(player, worldUuid)
                        return
                }

                val interaction = plugin.pendingInteractionRepository.findById(decisionId)
                if (
                        interaction == null ||
                                interaction.type != PendingInteractionType.MEMBER_INVITE ||
                                interaction.worldUuid != worldUuid
                ) {
                        player.sendMessage(
                                lang.getMessage(player, "messages.member_invite_cancel_not_found")
                        )
                        reopenMemberManagementLatest(player, worldUuid)
                        return
                }

                plugin.pendingInteractionRepository.remove(decisionId)
                val targetName =
                        PlayerNameUtil.getNameOrDefault(
                                interaction.targetUuid,
                                lang.getMessage(player, "general.unknown")
                        )
                player.sendMessage(
                        lang.getMessage(
                                player,
                                "messages.member_invite_cancelled",
                                mapOf("player" to targetName)
                        )
                )
                reopenMemberManagementLatest(player, worldUuid)
        }

        private fun toggleMemberRole(player: Player, worldData: WorldData, memberId: UUID) {
                val isModerator = worldData.moderators.contains(memberId)
                if (isModerator) {
                        worldData.moderators.remove(memberId)
                        if (!worldData.members.contains(memberId)) {
                                worldData.members.add(memberId)
                        }
                } else {
                        worldData.members.remove(memberId)
                        if (!worldData.moderators.contains(memberId)) {
                                worldData.moderators.add(memberId)
                        }
                }
                plugin.worldConfigRepository.save(worldData)
                reopenMemberManagementLatest(player, worldData.uuid, playSound = false)
        }

        private fun applyMemberInvite(
                player: Player,
                worldData: WorldData,
                targetNameRaw: String,
                forceAddMode: Boolean = false
        ) {
                val lang = plugin.languageManager
                val targetName = targetNameRaw.trim()
                val useForceAddMode =
                        forceAddMode && PermissionManager.canForceAddMember(player)

                if (targetName.isEmpty()) {
                        val inputMessageKey =
                                if (useForceAddMode) {
                                        "messages.member_force_add_input"
                                } else {
                                        "messages.member_invite_input"
                                }
                        player.sendMessage(lang.getMessage(player, inputMessageKey))
                        player.playSound(
                                player.location,
                                org.bukkit.Sound.ENTITY_VILLAGER_NO,
                                1.0f,
                                1.0f
                        )
                        reopenMemberManagementLatest(player, worldData.uuid, playSound = false)
                        return
                }

                val target = resolveInviteTarget(targetName)

                if (target == null) {
                        player.sendMessage(lang.getMessage(player, "general.player_not_found"))
                        player.playSound(
                                player.location,
                                org.bukkit.Sound.ENTITY_VILLAGER_NO,
                                1.0f,
                                1.0f
                        )
                        reopenMemberManagementLatest(player, worldData.uuid, playSound = false)
                        return
                }

                if (target.uniqueId == player.uniqueId) {
                        player.sendMessage(lang.getMessage(player, "messages.invite_self_error"))
                        player.playSound(
                                player.location,
                                org.bukkit.Sound.ENTITY_VILLAGER_NO,
                                1.0f,
                                1.0f
                        )
                        reopenMemberManagementLatest(player, worldData.uuid, playSound = false)
                        return
                }

                if (
                        worldData.owner == target.uniqueId ||
                                worldData.members.contains(target.uniqueId) ||
                                worldData.moderators.contains(target.uniqueId)
                ) {
                        player.sendMessage(lang.getMessage(player, "error.invite_already_member"))
                        player.playSound(
                                player.location,
                                org.bukkit.Sound.ENTITY_VILLAGER_NO,
                                1.0f,
                                1.0f
                        )
                        reopenMemberManagementLatest(player, worldData.uuid, playSound = false)
                        return
                }

                if (
                        plugin.pendingInteractionRepository.existsByTargetWorldAndType(
                                target.uniqueId,
                                worldData.uuid,
                                PendingInteractionType.MEMBER_INVITE
                        )
                ) {
                        player.sendMessage(
                                lang.getMessage(player, "messages.member_invite_already_sent")
                        )
                        player.playSound(
                                player.location,
                                org.bukkit.Sound.ENTITY_VILLAGER_NO,
                                1.0f,
                                1.0f
                        )
                        reopenMemberManagementLatest(player, worldData.uuid, playSound = false)
                        return
                }

                if (useForceAddMode) {
                        worldData.members.add(target.uniqueId)
                        plugin.worldConfigRepository.save(worldData)
                        Bukkit.getPluginManager().callEvent(
                                MwmMemberAddedEvent(
                                        worldUuid = worldData.uuid,
                                        memberUuid = target.uniqueId,
                                        memberName = target.name ?: targetName,
                                        addedByUuid = player.uniqueId,
                                        source = MwmMemberAddSource.FORCE_ADD
                                )
                        )

                        if (target is Player && target.isOnline) {
                                target.sendMessage(
                                        lang.getMessage(
                                                target,
                                                "messages.member_force_added_self",
                                                mapOf("world" to worldData.name)
                                        )
                                )
                        }

                        val targetDisplayName = target.name ?: targetName
                        player.sendMessage(
                                lang.getMessage(
                                        player,
                                        "messages.member_force_add_success",
                                        mapOf(
                                                "player" to targetDisplayName,
                                                "world" to worldData.name
                                        )
                                )
                        )

                        val recipients = linkedSetOf<UUID>()
                        recipients.add(worldData.owner)
                        recipients.addAll(worldData.moderators)
                        recipients.addAll(worldData.members)
                        recipients.remove(target.uniqueId)

                        recipients.forEach { memberUuid ->
                                val memberPlayer = Bukkit.getPlayer(memberUuid) ?: return@forEach
                                if (!memberPlayer.isOnline) {
                                        return@forEach
                                }
                                memberPlayer.sendMessage(
                                        lang.getMessage(
                                                memberPlayer,
                                                "messages.member_joined_notify",
                                                mapOf(
                                                        "player" to targetDisplayName,
                                                        "world" to worldData.name
                                                )
                                        )
                                )
                        }

                        plugin.macroManager.execute(
                                "on_member_add",
                                mapOf(
                                        "world_uuid" to worldData.uuid.toString(),
                                        "member" to targetDisplayName
                                )
                        )

                        reopenMemberManagementLatest(player, worldData.uuid)
                        return
                }

                val inviteText =
                        lang.getMessage(
                                if (target is Player) target else player,
                                "messages.member_invite_text",
                                mapOf(
                                        "player" to player.name,
                                        "world" to worldData.name
                                )
                        )
                val count = plugin.pendingDecisionManager.enqueueMemberInvite(
                        target.uniqueId,
                        worldData.uuid,
                        player.uniqueId
                )

                if (target is Player && target.isOnline) {
                        target.sendMessage(inviteText)
                        plugin.pendingDecisionManager.sendPendingHint(target, count)
                }
                player.sendMessage(
                        lang.getMessage(
                                player,
                                        "messages.invite_sent_success",
                                        mapOf(
                                                "player" to (target.name ?: targetName),
                                                "world" to worldData.name
                                        )
                                )
                )
                if (!(target is Player && target.isOnline)) {
                        player.sendMessage(
                                lang.getMessage(
                                        player,
                                        "messages.invite_queued_offline",
                                        mapOf("player" to (target.name ?: targetName))
                                )
                        )
                }

                reopenMemberManagementLatest(player, worldData.uuid)
        }

        private fun resolveInviteTarget(inputName: String): OfflinePlayer? {
                val trimmedInput = inputName.trim()
                if (trimmedInput.isEmpty()) {
                        return null
                }

                val candidates = buildInviteTargetCandidates(trimmedInput)
                val lowerCandidates = candidates.map { it.lowercase(Locale.ROOT) }.toSet()

                for (candidate in candidates) {
                        Bukkit.getPlayerExact(candidate)?.let { return it }
                }

                Bukkit.getOnlinePlayers().firstOrNull {
                        it.name.lowercase(Locale.ROOT) in lowerCandidates
                }?.let { return it }

                Bukkit.getOfflinePlayers().firstOrNull { offline ->
                        val name = offline.name ?: return@firstOrNull false
                        name.lowercase(Locale.ROOT) in lowerCandidates &&
                                (offline.hasPlayedBefore() || offline.isOnline)
                }?.let { return it }

                return plugin.playerStatsRepository.findAllFiles().firstNotNullOfOrNull { file ->
                        val uuid = runCatching { UUID.fromString(file.nameWithoutExtension) }.getOrNull()
                                ?: return@firstNotNullOfOrNull null
                        val lastName = plugin.playerStatsRepository.findByUuid(uuid).lastName ?: return@firstNotNullOfOrNull null
                        if (lastName.lowercase(Locale.ROOT) !in lowerCandidates) {
                                return@firstNotNullOfOrNull null
                        }
                        Bukkit.getPlayer(uuid) ?: Bukkit.getOfflinePlayer(uuid)
                }
        }

        private fun buildInviteTargetCandidates(inputName: String): LinkedHashSet<String> {
                val configuredPrefix =
                        plugin.config.getString("bedrock.player_name_prefix", "")?.trim().orEmpty()

                val candidates = linkedSetOf(inputName)
                if (configuredPrefix.isNotEmpty()) {
                        if (!inputName.startsWith(configuredPrefix)) {
                                candidates += "$configuredPrefix$inputName"
                        } else {
                                val withoutPrefix = inputName.removePrefix(configuredPrefix)
                                if (withoutPrefix.isNotEmpty()) {
                                        candidates += withoutPrefix
                                }
                        }
                }
                return candidates
        }

        private fun applyWorldInfoUpdate(
                player: Player,
                worldData: WorldData,
                newName: String,
                newDescription: String
        ) {
                val lang = plugin.languageManager
                var updated = false

                if (newName.length < 3 || newName.length > 16) {
                        player.sendMessage(lang.getMessage(player, "messages.world_name_invalid"))
                } else if (worldData.name != newName) {
                        worldData.name = newName
                        updated = true
                        player.sendMessage(lang.getMessage(player, "messages.world_name_change"))
                }

                if (worldData.description != newDescription) {
                        worldData.description = newDescription
                        updated = true
                        player.sendMessage(lang.getMessage(player, "messages.world_desc_change"))
                }

                if (updated) {
                        plugin.worldConfigRepository.save(worldData)
                }
                plugin.settingsSessionManager.endSession(player)
                plugin.worldSettingsGui.open(player, worldData)
        }

        private fun applyWorldNameUpdate(player: Player, worldData: WorldData, newName: String) {
                val lang = plugin.languageManager
                if (newName.length < 3 || newName.length > 16) {
                        player.sendMessage(lang.getMessage(player, "messages.world_name_invalid"))
                } else {
                        worldData.name = newName
                        plugin.worldConfigRepository.save(worldData)
                        player.sendMessage(lang.getMessage(player, "messages.world_name_change"))
                }

                plugin.settingsSessionManager.endSession(player)
                plugin.worldSettingsGui.open(player, worldData)
        }

        private fun applyWorldDescriptionUpdate(
                player: Player,
                worldData: WorldData,
                newDescription: String
        ) {
                val lang = plugin.languageManager
                worldData.description = newDescription
                plugin.worldConfigRepository.save(worldData)
                player.sendMessage(lang.getMessage(player, "messages.world_desc_change"))

                plugin.settingsSessionManager.endSession(player)
                plugin.worldSettingsGui.open(player, worldData)
        }

        @EventHandler
        fun onInventoryClose(event: InventoryCloseEvent) {
                val player = event.player as? Player ?: return
                val session = plugin.settingsSessionManager.getSession(player) ?: return
                val lang = plugin.languageManager

                // 繧｢繧､繧ｳ繝ｳ驕ｸ謚樔ｸｭ縺ｫ繧､繝ｳ繝吶Φ繝医Μ繧帝哩縺倥◆蝣ｴ蜷医・繧ｭ繝｣繝ｳ繧ｻ繝ｫ繝｡繝・そ繝ｼ繧ｸ繧定｡ｨ遉ｺ縺励※蜊ｳ邨ゆｺ・
                if (session.action == SettingsAction.SELECT_ICON) {
                        plugin.settingsSessionManager.endSession(player)
                        player.sendMessage(lang.getMessage(player, "messages.icon_cancelled"))
                        return
                }

                // ブロック選択入力中のアクションは、インベントリを閉じてもセッションを維持する
                val blockInputActions =
                        setOf(
                                SettingsAction.SET_SPAWN_GUEST,
                                SettingsAction.SET_SPAWN_MEMBER,
                                SettingsAction.EXPAND_DIRECTION_WAIT,
                                SettingsAction.EXPAND_DIRECTION_CONFIRM
                        )

                if (session.action in blockInputActions) {
                        return
                }

                // GUI驕ｷ遘ｻ・医し繝悶Γ繝九Η繝ｼ縺ｸ縺ｮ遘ｻ蜍輔ｄ逕ｻ髱｢譖ｴ譁ｰ・峨ｒ閠・・縺励・tick蠕後↓縺ｾ縺險ｭ螳夐未騾｣GUI繧帝幕縺・※縺・ｋ縺九メ繧ｧ繝・け縺吶ｋ
                Bukkit.getScheduler()
                        .runTaskLater(
                                plugin,
                                Runnable {
                                        if (session.action == SettingsAction.EXPAND_DIRECTION_CONFIRM) {
                                                // 諡｡蠑ｵ譁ｹ蜷醍｢ｺ隱堺ｸｭ縺ｮ蝣ｴ蜷医・繝懊・繝繝ｼ繝励Ξ繝薙Η繝ｼ繧呈ｶ医＆縺ｪ縺・
                                                return@Runnable
                                        }

                                        if (!player.isOnline) {
                                                clearBorderPreview(player)
                                                return@Runnable
                                        }
                                        
                                        if (session.isGuiTransition) {
                                                session.isGuiTransition = false
                                                return@Runnable
                                        }

                                        // 迴ｾ蝨ｨ髢九＞縺ｦ縺・ｋ繧､繝ｳ繝吶Φ繝医Μ縺瑚ｨｭ螳哦UI縺ｮ繝帙Ν繝繝ｼ繧呈戟縺｣縺ｦ縺・ｋ蝣ｴ蜷医・邨ゆｺ・＠縺ｪ縺・
                                        if (player.openInventory.topInventory.holder is
                                                        me.awabi2048.myworldmanager.gui.WorldSettingsGuiHolder
                                        ) {
                                                return@Runnable
                                        }

                                        // 險ｭ螳哦UI莉･螟悶・逕ｻ髱｢・医∪縺溘・繧､繝ｳ繝吶Φ繝医Μ縺ｪ縺暦ｼ峨↓縺ｪ縺｣縺溷ｴ蜷医√そ繝・す繝ｧ繝ｳ繧堤ｵゆｺ・☆繧・
                                        clearBorderPreview(player)
                                        plugin.settingsSessionManager.endSession(player)
                                },
                                2L
                        )
        }

        @EventHandler
        fun onWorldChange(event: PlayerChangedWorldEvent) {
                stopSpawnPreview(event.player)
                clearBorderPreview(event.player)
                processImmediateExpansion(event.player)
        }

        @EventHandler
        fun onQuit(event: PlayerQuitEvent) {
                stopSpawnPreview(event.player)
                clearBorderPreview(event.player)
                processImmediateExpansion(event.player)
        }

        private fun startSpawnPreview(player: Player) {
                stopSpawnPreview(player)
                spawnPreviewTasks[player.uniqueId] =
                        Bukkit.getScheduler()
                                .runTaskTimer(
                                        plugin,
                                        Runnable {
                                                if (!player.isOnline) {
                                                        stopSpawnPreview(player)
                                                        return@Runnable
                                                }
                                                val session =
                                                        plugin.settingsSessionManager.getSession(player)
                                                if (session == null ||
                                                                (session.action !=
                                                                        SettingsAction.SET_SPAWN_GUEST &&
                                                                        session.action !=
                                                                                SettingsAction
                                                                                        .SET_SPAWN_MEMBER)
                                                ) {
                                                        stopSpawnPreview(player)
                                                        return@Runnable
                                                }

                                                val targetBlock = player.getTargetBlockExact(6) ?: return@Runnable
                                                val spawnLoc =
                                                        targetBlock.location.clone().add(0.5, 1.0, 0.5)
                                                val yaw = normalizeToCardinalYaw(player.location.yaw)
                                                val placeable = isSpawnAreaPlaceable(spawnLoc)
                                                spawnSpawnPreview(player, spawnLoc, yaw, placeable)
                                        },
                                        0L,
                                        10L
                                )
        }

        private fun stopSpawnPreview(player: Player) {
                spawnPreviewTasks.remove(player.uniqueId)?.cancel()
        }

        private fun normalizeToCardinalYaw(yawRaw: Float): Float {
                var yaw = yawRaw
                while (yaw < 0) yaw += 360
                while (yaw >= 360) yaw -= 360

                return when {
                        yaw >= 45 && yaw < 135 -> 90.0f
                        yaw >= 135 && yaw < 225 -> 180.0f
                        yaw >= 225 && yaw < 315 -> 270.0f
                        else -> 0.0f
                }
        }

        private fun isSpawnAreaPlaceable(spawnLoc: Location): Boolean {
                val feetBlock = spawnLoc.block
                val headBlock = spawnLoc.clone().add(0.0, 1.0, 0.0).block
                return isAirOrWater(feetBlock.type) && isAirOrWater(headBlock.type)
        }

        private fun isAirOrWater(material: Material): Boolean {
                return material.isAir || material == Material.WATER
        }

        private fun spawnSpawnPreview(
                player: Player,
                spawnLoc: Location,
                yaw: Float,
                placeable: Boolean
        ) {
                val world = spawnLoc.world ?: return
                val frameColor = if (placeable) Color.fromRGB(64, 255, 120) else Color.fromRGB(255, 80, 80)
                val frameDust = Particle.DustOptions(frameColor, 0.5f)
                val arrowDust = Particle.DustOptions(Color.fromRGB(80, 160, 255), 0.5f)

                val feetBlockY = spawnLoc.blockY
                spawnSpawnBlockOutline(
                        player,
                        spawnLoc.blockX,
                        feetBlockY,
                        spawnLoc.blockZ,
                        frameDust,
                        drawBottomFace = true,
                        drawTopFace = false
                )
                spawnSpawnBlockOutline(
                        player,
                        spawnLoc.blockX,
                        feetBlockY + 1,
                        spawnLoc.blockZ,
                        frameDust,
                        drawBottomFace = false,
                        drawTopFace = true
                )

                val rad = Math.toRadians(yaw.toDouble())
                val forwardX = -kotlin.math.sin(rad)
                val forwardZ = kotlin.math.cos(rad)
                val arrowStart =
                        Location(
                                world,
                                spawnLoc.x - forwardX * 0.5,
                                spawnLoc.y + 0.15,
                                spawnLoc.z - forwardZ * 0.5
                        )
                spawnDirectionArrow(player, arrowStart, yaw, arrowDust)
        }

        private fun spawnSpawnBlockOutline(
                player: Player,
                blockX: Int,
                blockY: Int,
                blockZ: Int,
                dust: Particle.DustOptions,
                drawBottomFace: Boolean,
                drawTopFace: Boolean
        ) {
                val minX = blockX.toDouble()
                val minY = blockY.toDouble()
                val minZ = blockZ.toDouble()
                val maxX = blockX + 1.0
                val maxY = blockY + 1.0
                val maxZ = blockZ + 1.0

                if (drawBottomFace) {
                        spawnLineWithTenParticles(player, minX, minY, minZ, maxX, minY, minZ, dust)
                        spawnLineWithTenParticles(player, minX, minY, maxZ, maxX, minY, maxZ, dust)
                        spawnLineWithTenParticles(player, minX, minY, minZ, minX, minY, maxZ, dust)
                        spawnLineWithTenParticles(player, maxX, minY, minZ, maxX, minY, maxZ, dust)
                }
                if (drawTopFace) {
                        spawnLineWithTenParticles(player, minX, maxY, minZ, maxX, maxY, minZ, dust)
                        spawnLineWithTenParticles(player, minX, maxY, maxZ, maxX, maxY, maxZ, dust)
                        spawnLineWithTenParticles(player, minX, maxY, minZ, minX, maxY, maxZ, dust)
                        spawnLineWithTenParticles(player, maxX, maxY, minZ, maxX, maxY, maxZ, dust)
                }

                spawnLineWithTenParticles(player, minX, minY, minZ, minX, maxY, minZ, dust)
                spawnLineWithTenParticles(player, maxX, minY, minZ, maxX, maxY, minZ, dust)
                spawnLineWithTenParticles(player, minX, minY, maxZ, minX, maxY, maxZ, dust)
                spawnLineWithTenParticles(player, maxX, minY, maxZ, maxX, maxY, maxZ, dust)
        }

        private fun spawnDirectionArrow(
                player: Player,
                start: Location,
                yaw: Float,
                dust: Particle.DustOptions
        ) {
                val rad = Math.toRadians(yaw.toDouble())
                val forwardX = -kotlin.math.sin(rad)
                val forwardZ = kotlin.math.cos(rad)

                val tipX = start.x + forwardX * 1.0
                val tipZ = start.z + forwardZ * 1.0
                spawnLineWithTenParticles(player, start.x, start.y, start.z, tipX, start.y, tipZ, dust)

                val baseX = tipX - forwardX * 0.4
                val baseZ = tipZ - forwardZ * 0.4
                val sideX = -forwardZ * 0.2
                val sideZ = forwardX * 0.2

                spawnLineWithFiveParticles(
                        player,
                        tipX,
                        start.y,
                        tipZ,
                        baseX + sideX,
                        start.y,
                        baseZ + sideZ,
                        dust
                )
                spawnLineWithFiveParticles(
                        player,
                        tipX,
                        start.y,
                        tipZ,
                        baseX - sideX,
                        start.y,
                        baseZ - sideZ,
                        dust
                )
        }

        private fun spawnLineWithFiveParticles(
                player: Player,
                startX: Double,
                startY: Double,
                startZ: Double,
                endX: Double,
                endY: Double,
                endZ: Double,
                dust: Particle.DustOptions
        ) {
                for (i in 0..4) {
                        val t = i.toDouble() / 4.0
                        val x = startX + (endX - startX) * t
                        val y = startY + (endY - startY) * t
                        val z = startZ + (endZ - startZ) * t
                        player.spawnParticle(Particle.DUST, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, dust)
                }
        }

        private fun spawnLineWithTenParticles(
                player: Player,
                startX: Double,
                startY: Double,
                startZ: Double,
                endX: Double,
                endY: Double,
                endZ: Double,
                dust: Particle.DustOptions
        ) {
                for (i in 0..9) {
                        val t = i.toDouble() / 9.0
                        val x = startX + (endX - startX) * t
                        val y = startY + (endY - startY) * t
                        val z = startZ + (endZ - startZ) * t
                        player.spawnParticle(Particle.DUST, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, dust)
                }
        }

        private fun processImmediateExpansion(player: Player) {
                val pending = pendingExpansions.remove(player.uniqueId) ?: return
                pending.task.cancel()
                executeExpansionFinal(player, pending.worldData, pending.cost, pending.direction)
        }

        private fun executeExpansionFinal(
                player: Player,
                worldData: WorldData,
                cost: Int,
                direction: BlockFace?
        ) {
                val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                if (performExpansion(worldData, direction)) {
                        stats.worldPoint -= cost
                        worldData.cumulativePoints += cost
                        plugin.playerStatsRepository.save(stats)
                        plugin.worldConfigRepository.save(worldData)
                        player.sendMessage(
                                plugin.languageManager.getMessage(
                                        player,
                                        "messages.expand_complete",
                                        mapOf(
                                                "level_before" to
                                                        (worldData.borderExpansionLevel - 1),
                                                "level_after" to worldData.borderExpansionLevel,
                                                "remaining" to stats.worldPoint
                                        )
                                )
                        )
                } else {
player.sendMessage(
                                plugin.languageManager.getMessage("error.expand_failed")
                        )
                }
        }

        @EventHandler
        fun onInteract(event: PlayerInteractEvent) {
                if (event.hand != EquipmentSlot.HAND) return

                val player = event.player
                val settingsSession = plugin.settingsSessionManager.getSession(player) ?: return
                val currentAction = settingsSession.action

                if (currentAction == SettingsAction.EXPAND_DIRECTION_WAIT) {
                        val isBedrock = plugin.playerPlatformResolver.isBedrock(player)
                        val isDirectionClick =
                                if (isBedrock) {
                                        event.action == Action.LEFT_CLICK_AIR ||
                                                event.action == Action.LEFT_CLICK_BLOCK ||
                                                event.action == Action.RIGHT_CLICK_AIR ||
                                                event.action == Action.RIGHT_CLICK_BLOCK
                                } else {
                                        event.action == Action.LEFT_CLICK_AIR ||
                                                event.action == Action.LEFT_CLICK_BLOCK
                                }
                        if (isDirectionClick) {
                                event.isCancelled = true

                                var yaw = player.location.yaw
                                while (yaw < -180) yaw += 360
                                while (yaw >= 180) yaw -= 360

                                val direction =
                                        when {
                                                yaw >= 0 && yaw < 90 -> BlockFace.SOUTH_WEST
                                                yaw >= 90 && yaw < 180 -> BlockFace.NORTH_WEST
                                                yaw >= -90 && yaw < 0 -> BlockFace.SOUTH_EAST
                                                else -> BlockFace.NORTH_EAST
                                        }

                                settingsSession.expansionDirection = direction
                                settingsSession.action = SettingsAction.EXPAND_DIRECTION_CONFIRM

                                val worldData =
                                        plugin.worldConfigRepository.findByUuid(
                                                settingsSession.worldUuid
                                        ) ?: return
                                val cost =
                                        calculateExpansionCost(worldData.borderExpansionLevel)
                                
                                settingsSession.setMetadata("expand_cost", cost)

                                val directionKey =
                                        when (direction) {
                                                BlockFace.NORTH_WEST ->
                                                        "general.direction.north_west"
                                                BlockFace.NORTH_EAST ->
                                                        "general.direction.north_east"
                                                BlockFace.SOUTH_WEST ->
                                                        "general.direction.south_west"
                                                BlockFace.SOUTH_EAST ->
                                                        "general.direction.south_east"
                                                else -> "general.direction.unknown"
                                        }
                                val directionName =
                                        plugin.languageManager.getMessage(player, directionKey)
                                player.sendMessage(
                                        plugin.languageManager.getMessage(
                                                player,
                                                "messages.expand_direction_selected",
                                                mapOf("direction" to directionName)
                                        )
                                )

                                if (isBedrock &&
                                                openBedrockExpandDirectionConfirmForm(
                                                        player,
                                                        worldData.uuid,
                                                        direction,
                                                        cost
                                                )
                                ) {
                                        return
                                }

                                showBorderPreview(player, worldData, direction)
                                sendExpansionConfirmMessage(player)
                        }
                        return
                }

                if (currentAction == SettingsAction.SET_SPAWN_GUEST ||
                                currentAction == SettingsAction.SET_SPAWN_MEMBER
                ) {
                        if (event.action != Action.RIGHT_CLICK_BLOCK &&
                                        event.action != Action.LEFT_CLICK_BLOCK
                        )
                                return
                        val clickedBlock = event.clickedBlock ?: return

                        event.isCancelled = true
                        val loc = clickedBlock.location.clone().add(0.5, 1.0, 0.5)

                        val normalizedYaw = normalizeToCardinalYaw(player.location.yaw)

                        loc.yaw = normalizedYaw
                        loc.pitch = 0.0f

                        if (!isSpawnAreaPlaceable(loc)) {
                                player.sendMessage(
                                        plugin.languageManager.getMessage(
                                                player,
                                                "error.spawn_set_blocked"
                                        )
                                )
                                return
                        }

                        val worldData =
                                plugin.worldConfigRepository.findByUuid(settingsSession.worldUuid)
                        if (worldData != null) {
                                val setBoth =
                                        settingsSession.getMetadata("spawn_set_both") as? Boolean
                                                ?: false
                                if (setBoth) {
                                        worldData.spawnPosGuest = loc.clone()
                                        worldData.spawnPosMember = loc.clone()
                                        player.sendMessage(
                                                plugin.languageManager.getMessage(
                                                        player,
                                                        "messages.spawn_both_set"
                                                )
                                        )
                                } else if (currentAction == SettingsAction.SET_SPAWN_GUEST) {
                                        worldData.spawnPosGuest = loc
                                        player.sendMessage(
                                                plugin.languageManager.getMessage(
                                                        "messages.spawn_guest_set"
                                                )
                                        )
                                } else {
                                        worldData.spawnPosMember = loc
                                        player.sendMessage(
                                                plugin.languageManager.getMessage(
                                                        "messages.spawn_member_set"
                                                )
                                        )
                                }
                                plugin.worldConfigRepository.save(worldData)
                                stopSpawnPreview(player)
                                plugin.worldSettingsGui.open(player, worldData)
                        }
                        return
                }
        }

        private fun calculateExpansionCost(currentLevel: Int): Int {
                val config = plugin.config
                val targetLevel = currentLevel + 1
                return if (config.contains("expansion.costs.$targetLevel")) {
                        config.getInt("expansion.costs.$targetLevel")
                } else {
                        val baseCost = config.getInt("expansion.base_cost", 100)
                        val multiplier = config.getDouble("expansion.cost_multiplier", 2.0)
                        (baseCost * Math.pow(multiplier, currentLevel.toDouble())).toInt()
                }
        }

        private fun calculateTotalExpansionCost(level: Int): Int {
                var total = 0
                val config = plugin.config
                val baseCost = config.getInt("expansion.base_cost", 100)
                val multiplier = config.getDouble("expansion.cost_multiplier", 2.0)

                for (i in 1..level) {
                        total +=
                                if (config.contains("expansion.costs.$i")) {
                                        config.getInt("expansion.costs.$i")
                                } else {
                                        (baseCost * Math.pow(multiplier, (i - 1).toDouble()))
                                                .toInt()
                                }
                }
                return total
        }

        private fun performExpansion(worldData: WorldData, direction: BlockFace?): Boolean {
                val worldName = "my_world.${worldData.uuid}"
                val world = Bukkit.getWorld(worldName) ?: return false

                val border = world.worldBorder
                val oldSize = border.size
                val newSize = oldSize * 2

                if (direction != null) {
                        val oldCenter = border.center
                        val radius = oldSize / 2.0

                        var shiftX = 0.0
                        var shiftZ = 0.0

                        when (direction) {
                                BlockFace.NORTH_WEST -> {
                                        shiftX = -radius
                                        shiftZ = -radius
                                }
                                BlockFace.NORTH_EAST -> {
                                        shiftX = radius
                                        shiftZ = -radius
                                }
                                BlockFace.SOUTH_WEST -> {
                                        shiftX = -radius
                                        shiftZ = radius
                                }
                                BlockFace.SOUTH_EAST -> {
                                        shiftX = radius
                                        shiftZ = radius
                                }
                                else -> {}
                        }
                        val newCenter = oldCenter.clone().add(shiftX, 0.0, shiftZ)
                        newCenter.x = Math.round(newCenter.x).toDouble()
                        newCenter.z = Math.round(newCenter.z).toDouble()

                        border.setCenter(newCenter)
                        worldData.borderCenterPos = newCenter
                }

                border.setSize(newSize, 0)

                worldData.borderExpansionLevel += 1
                plugin.worldConfigRepository.save(worldData)
                return true
        }

        private fun teleportToBorderCenterSurface(player: Player, worldData: WorldData): Boolean {
                val worldName = worldData.customWorldName ?: "my_world.${worldData.uuid}"
                var world = Bukkit.getWorld(worldName)
                if (world == null) {
                        if (!plugin.worldService.loadWorld(worldData.uuid)) {
                                return false
                        }
                        world = Bukkit.getWorld(worldName) ?: return false
                }
                val targetWorld = world

                val center = worldData.borderCenterPos ?: targetWorld.worldBorder.center
                val centerX = Math.round(center.x).toInt()
                val centerZ = Math.round(center.z).toInt()
                val highestY = targetWorld.getHighestBlockYAt(centerX, centerZ)
                val y = (highestY + 1).coerceAtLeast(targetWorld.minHeight + 1)
                val target =
                        org.bukkit.Location(
                                targetWorld,
                                centerX + 0.5,
                                y.toDouble(),
                                centerZ + 0.5,
                                player.location.yaw,
                                player.location.pitch
                        )
                player.teleport(target)
                plugin.soundManager.playTeleportSound(player)
                return true
        }

        private fun handleWeatherClickCycle(player: Player, worldData: WorldData) {
                val session = plugin.settingsSessionManager.getSession(player) ?: return
                val config = plugin.config
                val options = config.getStringList("environment.weather.options")
                if (options.isEmpty()) return

                val currentTemp = session.tempWeather ?: worldData.fixedWeather ?: "DEFAULT"
                session.tempWeather =
                        GuiHelper.getNextValue(currentTemp, options, true) // Cycle next

                plugin.soundManager.playClickSound(player, null, "world_settings")
                plugin.environmentGui.open(player, worldData)
        }

        private fun executeGravityChange(
                player: Player,
                worldData: WorldData,
                confirmItem: org.bukkit.inventory.ItemStack
        ) {
                val config = plugin.config
                val cost = config.getInt("environment.gravity.cost", 100)
                val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)

                if (stats.worldPoint < cost) {
                        player.sendMessage(
                                plugin.languageManager.getMessage(
                                        player,
                                        "gui.creation.insufficient",
                                        mapOf("shortage" to (cost - stats.worldPoint))
                                )
                        )
                        plugin.soundManager.playActionSound(
                                player,
                                "environment",
                                "insufficient_points"
                        )
                        return
                }

                worldData.gravityMultiplier = 0.17
                stats.worldPoint -= cost
                worldData.cumulativePoints += cost

                plugin.playerStatsRepository.save(stats)
                plugin.worldConfigRepository.save(worldData)

                removeFromInventory(player, confirmItem)

                player.sendMessage(
                        plugin.languageManager.getMessage(
                                player,
                                "messages.env_gravity_changed",
                                mapOf("gravity" to "Moon", "multiplier" to "0.17")
                        )
                )
                player.sendMessage(
                        plugin.languageManager.getMessage(
                                player,
                                "messages.env_cost_paid",
                                mapOf("cost" to cost)
                        )
                )
                plugin.soundManager.playActionSound(player, "environment", "gravity_change")
                applyGravityToWorld(worldData)
                plugin.environmentGui.open(player, worldData)
        }

        private fun executeBiomeChange(
                player: Player,
                worldData: WorldData,
                confirmItem: org.bukkit.inventory.ItemStack
        ) {
                val biomeId = ItemTag.getBiomeId(confirmItem) ?: return
                val lang = plugin.languageManager
                val config = plugin.config
                val cost = config.getInt("environment.biome.cost", 500)
                val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)

                // Safety Check: Permission & Logic
                val session = plugin.settingsSessionManager.getSession(player)
                val isMember =
                        player.uniqueId == worldData.owner ||
                                worldData.moderators.contains(player.uniqueId) ||
                                worldData.members.contains(player.uniqueId) ||
                                session?.isAdminFlow == true
                val isAdmin = player.hasPermission("myworldmanager.admin")
                val worldFolderName = worldData.customWorldName ?: "my_world.${worldData.uuid}"
                val isAdminWorld = worldData.customWorldName != null

                if (isAdminWorld) {
                        player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                        player.sendMessage(lang.getMessage(player, "messages.custom_item.biome_bottle_disabled"))
                        return
                }

                if (!isMember) {
                        player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                        player.sendMessage(lang.getMessage(player, "error.custom_item.no_permission"))
                        return
                }

                if (stats.worldPoint < cost) {
                        player.sendMessage(
                                plugin.languageManager.getMessage(
                                        player,
                                        "gui.creation.insufficient",
                                        mapOf("shortage" to (cost - stats.worldPoint))
                                )
                        )
                        plugin.soundManager.playActionSound(
                                player,
                                "environment",
                                "insufficient_points"
                        )
                        return
                }

                try {
                        org.bukkit.block.Biome.valueOf(biomeId.uppercase())
                        worldData.fixedBiome = biomeId.uppercase()
                        worldData.partialBiomes.clear()
                        stats.worldPoint -= cost
                        worldData.cumulativePoints += cost

                        plugin.playerStatsRepository.save(stats)
                        plugin.worldConfigRepository.save(worldData)

                        removeFromInventory(player, confirmItem)

                        val biomeName = lang.getMessage(player, "biomes.${biomeId.lowercase()}")
                        player.sendMessage(
                                lang.getMessage(
                                        player,
                                        "messages.env_biome_changed",
                                        mapOf("biome" to biomeName)
                                )
                        )
                        player.sendMessage(
                                lang.getMessage(
                                        player,
                                        "messages.env_cost_paid",
                                        mapOf("cost" to cost)
                                )
                        )
                        plugin.soundManager.playActionSound(player, "environment", "biome_change")
                        applyBiomeToWorld(worldData)
                        plugin.environmentGui.open(player, worldData)
                } catch (e: Exception) {
                        player.sendMessage("ﾂｧcInvalid biome data.")
                }
        }

        private fun removeFromInventory(
                player: Player,
                templateItem: org.bukkit.inventory.ItemStack
        ) {
                val inv = player.inventory
                for (i in 0 until inv.size) {
                        val item = inv.getItem(i)
                        if (item != null && item.isSimilar(templateItem)) {
                                item.amount -= 1
                                return
                        }
                }
        }

        private fun applyGravityToWorld(worldData: WorldData) {
                val world = Bukkit.getWorld("my_world.${worldData.uuid}") ?: return
                val multiplier = worldData.gravityMultiplier
                val gravityValue = 0.08 * multiplier

                world.entities.forEach { entity ->
                        if (entity is org.bukkit.entity.LivingEntity) {
                                entity.getAttribute(org.bukkit.attribute.Attribute.GRAVITY)
                                        ?.baseValue = gravityValue
                        }
                }
        }

        private fun applyWeatherToWorld(worldData: WorldData) {
                val world = Bukkit.getWorld("my_world.${worldData.uuid}") ?: return
                val fixed = worldData.fixedWeather ?: return

                when (fixed) {
                        "CLEAR" -> {
                                world.setStorm(false)
                                world.setThundering(false)
                        }
                        "RAIN" -> {
                                world.setStorm(true)
                                world.setThundering(false)
                        }
                        "THUNDER" -> {
                                world.setStorm(true)
                                world.setThundering(true)
                        }
                }
        }

        private fun applyBiomeToWorld(worldData: WorldData) {
                val world = Bukkit.getWorld("my_world.${worldData.uuid}") ?: return
                val biomeStr = worldData.fixedBiome ?: return
                val biome =
                        try {
                                org.bukkit.block.Biome.valueOf(biomeStr)
                        } catch (e: Exception) {
                                return
                        }

                val center = worldData.borderCenterPos ?: world.spawnLocation
                val expansion = worldData.borderExpansionLevel
                val initialSize = plugin.config.getDouble("expansion.initial_size", 100.0)
                // Adjust for special level or calculate size
                val size =
                        if (expansion == WorldData.EXPANSION_LEVEL_SPECIAL) 60000000.0
                        else initialSize * Math.pow(2.0, expansion.toDouble())
                val radius = size / 2.0
                val applyRadius = radius + 160.0

                val minX = (center.x - applyRadius).toInt()
                val maxX = (center.x + applyRadius).toInt()
                val minZ = (center.z - applyRadius).toInt()
                val maxZ = (center.z + applyRadius).toInt()

                // Iterate loaded chunks instead of all blocks directly to avoid loading unloaded
                // chunks if
                // possible,
                // or iterate chunks in range.
                // Task says "Change ... to border + 160 blocks range".
                // Efficient way: iterate loaded chunks, and for each column check if in range.
                // If we want to ensure *all* blocks in range are changed (even unloaded), we should
                // iterate
                // strict range.
                // However, iterating 60million blocks is bad.
                // But for a typical MyWorld with expansion 0-3, it's small.
                // Let's assume we only update loaded chunks because unloaded ones will be handled
                // by
                // ChunkLoadEvent.

                world.loadedChunks.forEach { chunk ->
                        val chunkX = chunk.x * 16
                        val chunkZ = chunk.z * 16

                        // Optimization: check if chunk overlaps with range
                        if (chunkX + 15 < minX ||
                                        chunkX > maxX ||
                                        chunkZ + 15 < minZ ||
                                        chunkZ > maxZ
                        ) {
                                return@forEach
                        }

                        for (x in 0..15) {
                                val worldX = chunkX + x
                                for (z in 0..15) {
                                        val worldZ = chunkZ + z

                                        if (worldX in minX..maxX && worldZ in minZ..maxZ) {
                                                for (y in
                                                        world.minHeight until
                                                                world.maxHeight step
                                                                16) {
                                                        world.setBiome(worldX, y, worldZ, biome)
                                                }
                                        }
                                }
                        }
                        // Update chunk for clients
                        world.refreshChunk(chunk.x, chunk.z)
                }
        }

        private fun showBorderPreview(player: Player, worldData: WorldData, direction: BlockFace) {
                var world = Bukkit.getWorld(worldData.uuid)
                
                if (world == null) {
                    val folderName = worldData.customWorldName ?: "my_world.${worldData.uuid}"
                    world = Bukkit.getWorld(folderName)
                }

                if (world == null) {
                    return
                }

                val currentSize = world.worldBorder.size
                val nextSize = currentSize * 2 // performExpansionと同じロジック

                val currentCenter = world.worldBorder.center
                val radius = currentSize / 2.0 // performExpansionと同じロジック
                var shiftX = 0.0
                var shiftZ = 0.0

                when (direction) {
                        BlockFace.NORTH_WEST -> {
                                shiftX = -radius
                                shiftZ = -radius
                        }
                        BlockFace.NORTH_EAST -> {
                                shiftX = radius
                                shiftZ = -radius
                        }
                        BlockFace.SOUTH_WEST -> {
                                shiftX = -radius
                                shiftZ = radius
                        }
                        BlockFace.SOUTH_EAST -> {
                                shiftX = radius
                                shiftZ = radius
                        }
                        else -> {}
                }
                val newCenter = currentCenter.clone().add(shiftX, 0.0, shiftZ)
                newCenter.x = Math.round(newCenter.x).toDouble()
                newCenter.z = Math.round(newCenter.z).toDouble()

                val border = Bukkit.createWorldBorder()
                border.center = newCenter
                border.size = nextSize
                player.worldBorder = border

                player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f)
        }

        private fun clearBorderPreview(player: Player) {
                player.worldBorder = null
        }

        private fun sendExpansionConfirmMessage(player: Player) {
                val lang = plugin.languageManager
                val confirmText = lang.getMessage(player, "messages.expand_confirm_chat")
                val confirmBtn = lang.getMessage(player, "messages.expand_confirm_button")
                val confirmHover = lang.getMessage(player, "messages.expand_confirm_hover")
                val retryBtn = lang.getMessage(player, "messages.expand_retry_button")
                val retryHover = lang.getMessage(player, "messages.expand_retry_hover")

                val message = net.kyori.adventure.text.Component.text()
                        .append(net.kyori.adventure.text.Component.newline())
                        .append(net.kyori.adventure.text.Component.text(confirmText))
                        .append(net.kyori.adventure.text.Component.newline())
                        .append(net.kyori.adventure.text.Component.newline())
                        .append(net.kyori.adventure.text.Component.text(confirmBtn)
                                .hoverEvent(net.kyori.adventure.text.Component.text(confirmHover))
                                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/mwm_internal expand_confirm")))
                        .append(net.kyori.adventure.text.Component.text("   "))
                        .append(net.kyori.adventure.text.Component.text(retryBtn)
                                .hoverEvent(net.kyori.adventure.text.Component.text(retryHover))
                                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/mwm_internal expand_retry")))
                        .append(net.kyori.adventure.text.Component.newline())
                        .build()

                player.sendMessage(message)
        }

        @EventHandler
        fun onCommandPreprocess(event: PlayerCommandPreprocessEvent) {
                val player = event.player
                val message = event.message
                if (!message.startsWith("/mwm_internal ")) return

                val args = message.substring("/mwm_internal ".length).split(" ")
                if (args.isEmpty()) return

                event.isCancelled = true
                val lang = plugin.languageManager

                when (args[0]) {
                        "expand_confirm" -> {
                                val session = plugin.settingsSessionManager.getSession(player) ?: return
                                if (session.action == SettingsAction.EXPAND_DIRECTION_CONFIRM) {
                                        val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid) ?: return
                                        val direction = session.expansionDirection
                                        val cost = session.getMetadata("expand_cost") as? Int ?: 0
                                        openExpandConfirmationByPreference(
                                                player,
                                                worldData.uuid,
                                                direction,
                                                cost
                                        )
                                }
                        }
                        "expand_retry" -> {
                                val session = plugin.settingsSessionManager.getSession(player) ?: return
                                if (session.action == SettingsAction.EXPAND_DIRECTION_CONFIRM) {
                                        clearBorderPreview(player)
                                        session.action = SettingsAction.EXPAND_DIRECTION_WAIT
                                        val promptKey =
                                                if (plugin.playerPlatformResolver.isBedrock(player)) {
                                                        "messages.expand_direction_prompt_bedrock"
                                                } else {
                                                        "messages.expand_direction_prompt"
                                                }
                                        player.sendMessage(lang.getMessage(player, promptKey))
                                }
                        }
                        "mspt-sort" -> {
                                if (!player.isOp) return
                                val session = plugin.adminGuiSessionManager.getSession(player.uniqueId)
                                session.sortBy = me.awabi2048.myworldmanager.session.AdminSortType.MSPT_DESC
                                plugin.worldGui.open(player, 0, false, true)
                        }
                        "inviteaccept" -> {
                                plugin.inviteCommand.handleAccept(player)
                        }
                        "memberinviteaccept" -> {
                                val invite = plugin.memberInviteManager.getInvite(player.uniqueId)
                                if (invite == null) {
                                        player.sendMessage(lang.getMessage(player, "error.invite_expired"))
                                        return
                                }

                                val worldData = plugin.worldConfigRepository.findByUuid(invite.worldUuid)
                                if (worldData == null) {
                                        player.sendMessage(lang.getMessage(player, "error.invite_world_not_found"))
                                        plugin.memberInviteManager.removeInvite(invite.id)
                                        return
                                }

                                val senderName = PlayerNameUtil.getNameOrDefault(invite.senderUuid, "Unknown")
                                val title = Component.text(
                                        lang.getMessage(player, "gui.member_invite_accept_confirm.title")
                                )
                                val bodyLines = lang.getMessageList(
                                        player,
                                        "gui.member_invite_accept_confirm.lore",
                                        mapOf(
                                                "world" to worldData.name,
                                                "player" to senderName
                                        )
                                ).map { Component.text(it) }

                                DialogConfirmManager.showSimpleConfirmationDialog(
                                        player,
                                        plugin,
                                        title,
                                        bodyLines,
                                        "mwm:confirm/member_invite_accept",
                                        "mwm:confirm/member_invite_accept_cancel",
                                        lang.getMessage(player, "gui.member_invite_accept_confirm.confirm"),
                                        lang.getMessage(player, "gui.member_invite_accept_confirm.cancel")
                                )
                        }
                        "memberrequest" -> {
                                if (args.size < 3) return
                                val key = args[1]
                                val action = args[2] // "approve" or "reject"
                                plugin.memberRequestManager.handleInternalCommand(player, key, action)
                        }
                }
        }


        @EventHandler
        fun onDialogResponse(event: PlayerCustomClickEvent) {
            val identifier = event.identifier


            val conn = event.commonConnection as? io.papermc.paper.connection.PlayerGameConnection ?: return
            val player = conn.player
            
            // Rename World
            if (identifier == Key.key("mwm:settings/rename_submit")) {

                val view = event.getDialogResponseView() ?: return
                val newNameAny = view.getText("world_name") ?: return
                val newName = newNameAny.toString()

                
                // Validate Name
                // Logic copied from DialogTestListener/WorldValidator
                val error = plugin.worldValidator.validateName(newName)
                if (error != null) {
                    player.sendMessage("§c$error")
                    // Ideally re-open dialog, but for now close
                    return
                }
                
                val session = plugin.settingsSessionManager.getSession(player) ?: run {
                    plugin.logger.info("[MWM-Debug] Session is null")
                    return
                }
                plugin.logger.info("[MWM-Debug] Session found. Action: ${session.action}")

                val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid) ?: run {
                    plugin.logger.info("[MWM-Debug] WorldData not found")
                    return
                }

                applyWorldNameUpdate(player, worldData, newName)
                return
            }
            
            // Change Description
            if (identifier == Key.key("mwm:settings/desc_submit")) {
                val view = event.getDialogResponseView() ?: return
                val newDescAny = view.getText("world_desc") ?: ""
                val newDesc = newDescAny.toString()
                
                // Description validation? usually loosely allowed, max length maybe
                 if (newDesc.length > 100) {
                     player.sendMessage("§cDescription too long (max 100 chars)")
                     return
                 }

                val session = plugin.settingsSessionManager.getSession(player) ?: return
                val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid) ?: return

                applyWorldDescriptionUpdate(player, worldData, newDesc)
                return
            }

            if (identifier == Key.key("mwm:settings/member_invite_submit")) {
                val view = event.getDialogResponseView()
                val targetName = view?.getText("member_invite_target")?.toString().orEmpty().trim()

                val session = plugin.settingsSessionManager.getSession(player) ?: run {
                    player.sendMessage(plugin.languageManager.getMessage(player, "messages.member_invite_input"))
                    return
                }
                val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid) ?: run {
                    player.sendMessage(plugin.languageManager.getMessage(player, "general.world_not_found"))
                    plugin.settingsSessionManager.endSession(player)
                    return
                }
                val forceAddMode =
                        (session.getMetadata("member_invite_force_add_mode") as? Boolean) ==
                                true
                applyMemberInvite(player, worldData, targetName, forceAddMode)
                return
            }

            if (identifier == Key.key("mwm:settings/member_invite_cancel")) {
                val session = plugin.settingsSessionManager.getSession(player) ?: return
                reopenMemberManagementLatest(player, session.worldUuid)
                return
            }
            
            // Cancel
            if (identifier == Key.key("mwm:settings/cancel")) {
                val session = plugin.settingsSessionManager.getSession(player) ?: return
                val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid) ?: return
                plugin.worldSettingsGui.open(player, worldData)
            }

            if (identifier == Key.key("mwm:confirm/member_invite_accept")) {
                DialogConfirmManager.safeCloseDialog(player)
                plugin.memberInviteManager.handleMemberInviteAccept(player)
                return
            }

            if (identifier == Key.key("mwm:confirm/member_invite_accept_cancel")) {
                DialogConfirmManager.safeCloseDialog(player)
                player.sendMessage(plugin.languageManager.getMessage(player, "messages.operation_cancelled"))
                return
            }

            // Dialog Confirmation Handler (Environment & Visitor Kick)
            val keyVal = identifier.value()
            if (keyVal.startsWith("mwm:confirm/")) {
                
                // Cancel
                if (keyVal == "mwm:confirm/cancel") {
                   DialogConfirmManager.safeCloseDialog(player)
                   return 
                }

                val session = plugin.settingsSessionManager.getSession(player) ?: return
                val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid) ?: return

                if (keyVal.startsWith("mwm:confirm/env_change/")) {
                    val envType = keyVal.substringAfter("mwm:confirm/env_change/")
                    DialogConfirmManager.safeCloseDialog(player)
                    
                    when (envType) {
                        "gravity" -> handleEnvGravityConfirm(player, worldData)
                        "weather" -> handleWeatherConfirm(player, worldData)
                        "biome" -> {
                             val biomeId = session.getMetadata("temp_biome") as? String ?: return
                             handleEnvBiomeConfirm(player, worldData, biomeId)
                        }
                    }
                    return
                }
                
                if (keyVal.startsWith("mwm:confirm/visitor_kick/")) {
                     val targetUuidStr = keyVal.substringAfter("mwm:confirm/visitor_kick/")
                     DialogConfirmManager.safeCloseDialog(player)
                     try {
                         val targetUuid = java.util.UUID.fromString(targetUuidStr)
                         // Execute Kick
                         val targetPlayer = plugin.server.getPlayer(targetUuid)
                         if (targetPlayer != null && targetPlayer.world.uid == worldData.uuid) {
                             targetPlayer.teleport(plugin.server.worlds[0].spawnLocation)
                             targetPlayer.sendMessage(plugin.languageManager.getMessage(targetPlayer, "messages.visitor.kicked"))
                             player.sendMessage(plugin.languageManager.getMessage(player, "messages.visitor.kick_success", mapOf("player" to targetPlayer.name)))
                         } else {
                             player.sendMessage(plugin.languageManager.getMessage(player, "error.player_not_found"))
                         }
                     } catch (e: Exception) {
                         plugin.logger.warning("Invalid UUID in kick confirmation: $targetUuidStr")
                     }
                     return
                }
            }
        }
        

        
        private fun showRenameDialog(player: Player, worldData: WorldData) {
             val dialog = Dialog.create { builder ->
                builder.empty()
                    .base(DialogBase.builder(Component.text("ワールド名の変更", NamedTextColor.YELLOW))
                        .body(listOf(
                            DialogBody.plainMessage(Component.text("新しいワールド名を入力してください。")),
                        ))
                        .inputs(listOf(
                            DialogInput.text("world_name", Component.text("New World Name"))
                                .initial(worldData.name)
                                .build()
                        ))
                        .build()
                    )
                    .type(DialogType.confirmation(
                        ActionButton.create(Component.text("Submit", NamedTextColor.GREEN), null, 100, DialogAction.customClick(Key.key("mwm:settings/rename_submit"), null)),
                        ActionButton.create(Component.text("Cancel", NamedTextColor.GRAY), null, 200, DialogAction.customClick(Key.key("mwm:settings/cancel"), null))
                    ))
            }
            player.showDialog(dialog)
        }
        
        private fun showDescriptionDialog(player: Player, worldData: WorldData) {
             val dialog = Dialog.create { builder ->
                builder.empty()
                    .base(DialogBase.builder(Component.text("説明文の変更", NamedTextColor.YELLOW))
                        .body(listOf(
                            DialogBody.plainMessage(Component.text("新しい説明文を入力してください。")),
                        ))
                        .inputs(listOf(
                            DialogInput.text("world_desc", Component.text("New Description"))
                                .initial(worldData.description)
                                .build()
                        ))
                        .build()
                    )
                    .type(DialogType.confirmation(
                        ActionButton.create(Component.text("Submit", NamedTextColor.GREEN), null, 100, DialogAction.customClick(Key.key("mwm:settings/desc_submit"), null)),
                        ActionButton.create(Component.text("Cancel", NamedTextColor.GRAY), null, 200, DialogAction.customClick(Key.key("mwm:settings/cancel"), null))
                    ))
            }
            player.showDialog(dialog)
        }

        private fun showMemberInviteDialog(
                player: Player,
                forceAddMode: Boolean
        ) {
             val lang = plugin.languageManager
             val inviteInputMessageKey =
                     if (forceAddMode) {
                             "messages.member_force_add_input"
                     } else {
                             "messages.member_invite_input"
                     }
             val dialog = Dialog.create { builder ->
                builder.empty()
                    .base(
                        DialogBase.builder(Component.text(lang.getMessage(player, "gui.member_management.invite.name"), NamedTextColor.YELLOW))
                            .body(
                                listOf(
                                    DialogBody.plainMessage(Component.text(lang.getMessage(player, inviteInputMessageKey)))
                                )
                            )
                            .inputs(
                                listOf(
                                    DialogInput.text(
                                        "member_invite_target",
                                        Component.text(lang.getMessage(player, "gui.bedrock.input.member_invite.label"))
                                    ).maxLength(32).build()
                                )
                            )
                            .build()
                    )
                    .type(
                        DialogType.confirmation(
                            ActionButton.create(
                                Component.text(lang.getMessage(player, "gui.common.confirm"), NamedTextColor.GREEN),
                                null,
                                100,
                                DialogAction.customClick(Key.key("mwm:settings/member_invite_submit"), null)
                            ),
                            ActionButton.create(
                                Component.text(lang.getMessage(player, "gui.common.cancel"), NamedTextColor.RED),
                                null,
                                200,
                                DialogAction.customClick(Key.key("mwm:settings/member_invite_cancel"), null)
                            )
                        )
                    )
            }
            player.showDialog(dialog)
        }

        private fun showTagEditorDialog(player: Player, worldData: WorldData) {
                plugin.logger.info("[MWM-Debug] showTagEditorDialog called for player ${player.name}, world ${worldData.customWorldName}")
                
                val lang = plugin.languageManager
                val currentTags = worldData.tags
                val allTags = plugin.worldTagManager.getEditableTagIds(currentTags)
                
                plugin.logger.info("[MWM-Debug] Current tags: $currentTags")
                
                // Build Inputs
                // Create a boolean input for each tag
                val inputs = allTags.map { tagId ->
                        val tagName = plugin.worldTagManager.getDisplayName(player, tagId)
                        val isSelected = currentTags.contains(tagId)
                        
                        plugin.logger.info("[MWM-Debug] Creating input for tag $tagId: key=tag_$tagId, initial=$isSelected")
                        
                        DialogInput.bool("tag_$tagId", Component.text(tagName))
                                .initial(isSelected)
                                .build()
                }

                plugin.logger.info("[MWM-Debug] Creating dialog with ${inputs.size} inputs")
                
                val dialog = Dialog.create { builder ->
                        builder.empty()
                                .base(DialogBase.builder(Component.text("タグ設定", NamedTextColor.YELLOW))
                                        .body(listOf(
                                                DialogBody.plainMessage(Component.text("ワールドのタグを設定します。\n有効にするタグのスイッチをオンにしてください。")),
                                        ))
                                        .inputs(inputs)
                                        .build()
                                )
                                .type(DialogType.confirmation(
                                        ActionButton.create(Component.text("Submit", NamedTextColor.GREEN), null, 100, DialogAction.customClick(Key.key("mwm:settings/tags/submit"), null)),
                                        ActionButton.create(Component.text("Close", NamedTextColor.GRAY), null, 200, DialogAction.customClick(Key.key("mwm:settings/tags/close"), null))
                                ))
                }
                player.showDialog(dialog)
                plugin.logger.info("[MWM-Debug] Dialog shown to player ${player.name}")
        }
        
        private fun openExpandConfirmationByPreference(
                player: Player,
                worldUuid: UUID,
                direction: org.bukkit.block.BlockFace?,
                cost: Int
        ) {
                val worldData = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return
                val lang = plugin.languageManager
                val title = LegacyComponentSerializer.legacySection().deserialize(
                        lang.getMessage(player, "gui.expansion.confirm_title")
                )
                val directionKey = when (direction) {
                        org.bukkit.block.BlockFace.NORTH_WEST -> "general.direction.north_west"
                        org.bukkit.block.BlockFace.NORTH_EAST -> "general.direction.north_east"
                        org.bukkit.block.BlockFace.SOUTH_WEST -> "general.direction.south_west"
                        org.bukkit.block.BlockFace.SOUTH_EAST -> "general.direction.south_east"
                        else -> "general.direction.unknown"
                }
                val directionName = lang.getMessage(player, directionKey)
                val methodText = if (direction == null) {
                        lang.getMessage(player, "gui.expansion.method_center")
                } else {
                        lang.getMessage(player, "gui.expansion.method_direction", mapOf("direction" to directionName))
                }
                val bodyLines = listOf(
                        LegacyComponentSerializer.legacySection().deserialize(
                                lang.getMessage(player, "gui.expansion.method", mapOf("method" to methodText))
                        ),
                        LegacyComponentSerializer.legacySection().deserialize(
                                lang.getMessage(player, "gui.expansion.cost", mapOf("cost" to cost))
                        ),
                        LegacyComponentSerializer.legacySection().deserialize(
                                lang.getMessage(player, "gui.expansion.warning")
                        )
                )

                plugin.settingsSessionManager.updateSessionAction(
                        player,
                        worldUuid,
                        SettingsAction.EXPAND_CONFIRM,
                        isGui = true
                )
                plugin.settingsSessionManager.getSession(player)?.let {
                        it.expansionDirection = direction
                        it.setMetadata("expand_cost", cost)
                }

                DialogConfirmManager.showConfirmationByPreference(
                        player,
                        plugin,
                        title,
                        bodyLines,
                        "mwm:confirm/expand",
                        "mwm:confirm/cancel",
                        lang.getMessage(player, "gui.common.confirm"),
                        lang.getMessage(player, "gui.common.cancel"),
                        onBedrockConfirm = {
                                handleBedrockDialogAction(player, worldData, "mwm:confirm/expand")
                        },
                        onBedrockCancel = {
                                handleBedrockDialogCancel(player, worldData)
                        }
                ) {
                        plugin.worldSettingsGui.openExpansionConfirmation(player, worldData.uuid, direction, cost)
                }
        }

        // Helper to show generic simple confirmation
        private fun showEnvConfirmDialog(player: Player, type: String, cost: Int) {
            val lang = plugin.languageManager
            
            val titleKey = "gui.environment.$type.display" // e.g. gui.environment.gravity.display
            val title = Component.text(lang.getMessage(player, titleKey), NamedTextColor.YELLOW)
            
            val bodyLines = listOf(
                Component.text(lang.getMessage(player, "gui.common.confirm_action")),
                Component.text(lang.getMessage(player, "gui.settings.expand.cost", mapOf("cost" to cost)))
            )
            
            DialogConfirmManager.showSimpleConfirmationDialog(
                player,
                plugin,
                title,
                bodyLines,
                "mwm:confirm/env_change/$type",
                "mwm:confirm/cancel",
                lang.getMessage(player, "gui.common.confirm"),
                lang.getMessage(player, "gui.common.cancel"),
                onBedrockConfirm = {
                        val worldData = plugin.worldConfigRepository.findByUuid(plugin.settingsSessionManager.getSession(player)?.worldUuid ?: return@showSimpleConfirmationDialog)
                                ?: return@showSimpleConfirmationDialog
                        handleBedrockDialogAction(player, worldData, "mwm:confirm/env_change/$type")
                },
                onBedrockCancel = {
                        val worldData = plugin.worldConfigRepository.findByUuid(plugin.settingsSessionManager.getSession(player)?.worldUuid ?: return@showSimpleConfirmationDialog)
                                ?: return@showSimpleConfirmationDialog
                        handleBedrockDialogCancel(player, worldData)
                }
            )
        }
        
        private fun showVisitorKickConfirmDialog(player: Player, targetName: String, targetUuid: UUID) {
            val lang = plugin.languageManager
            val title = Component.text(lang.getMessage(player, "gui.visitor_management.kick"), NamedTextColor.RED)
            
            val bodyLines = listOf(
                Component.text(lang.getMessage(player, "gui.visitor_management.kick_confirm", mapOf("player" to targetName)))
            )
            
            DialogConfirmManager.showSimpleConfirmationDialog(
                player,
                plugin,
                title,
                bodyLines,
                "mwm:confirm/visitor_kick/$targetUuid",
                "mwm:confirm/cancel",
                "KICK",
                "Cancel",
                onBedrockConfirm = {
                        val worldData = plugin.worldConfigRepository.findByUuid(plugin.settingsSessionManager.getSession(player)?.worldUuid ?: return@showSimpleConfirmationDialog)
                                ?: return@showSimpleConfirmationDialog
                        handleBedrockDialogAction(player, worldData, "mwm:confirm/visitor_kick/$targetUuid")
                },
                onBedrockCancel = {
                        val worldData = plugin.worldConfigRepository.findByUuid(plugin.settingsSessionManager.getSession(player)?.worldUuid ?: return@showSimpleConfirmationDialog)
                                ?: return@showSimpleConfirmationDialog
                        handleBedrockDialogCancel(player, worldData)
                }
            )
        }
        
        @EventHandler
        fun onTagDialogInteraction(event: PlayerCustomClickEvent) {
                
                val identifier = event.identifier
                
                val conn = event.commonConnection as? PlayerGameConnection ?: return
                val player = conn.player
                
                if (identifier == Key.key("mwm:settings/tags/submit")) {
                        
                        val view = event.getDialogResponseView() ?: return
                        
                        val session = plugin.settingsSessionManager.getSession(player) ?: return
                        
                        val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid) ?: run {
                                plugin.logger.warning("[MWM-Debug] WorldData not found for uuid ${session.worldUuid}")
                                return
                        }
                        plugin.logger.info("[MWM-Debug] WorldData found: ${worldData.customWorldName}")
                        
                        // Collect all tags from input
                        val allTags = plugin.worldTagManager.getEditableTagIds(worldData.tags)
                        val newTags = mutableSetOf<String>()
                        plugin.logger.info("[MWM-Debug] Processing ${allTags.size} tags")
                        
                        for (tagId in allTags) {
                                val inputKey = "tag_$tagId"
                                val isSelected = view.getBoolean(inputKey) ?: false
                                plugin.logger.info("[MWM-Debug] Tag $tagId: inputKey=$inputKey, isSelected=$isSelected")
                                if (isSelected) {
                                        newTags.add(tagId)
                                }
                        }
                        
                        plugin.logger.info("[MWM-Debug] Selected tags: $newTags")
                        
                        // Save Changes
                        plugin.logger.info("[MWM-Debug] Clearing old tags: ${worldData.tags}")
                        worldData.tags.clear()
                        worldData.tags.addAll(newTags)
                        plugin.logger.info("[MWM-Debug] New tags set: ${worldData.tags}")
                        
                        plugin.worldConfigRepository.save(worldData)
                        plugin.logger.info("[MWM-Debug] WorldData saved successfully")
                        
                        // Return to settings
                        plugin.logger.info("[MWM-Debug] Opening settings GUI")
                        plugin.worldSettingsGui.open(player, worldData)
                        plugin.logger.info("[MWM-Debug] Done!")
                        return
                }
                
                if (identifier == Key.key("mwm:settings/tags/close")) {
                        plugin.logger.info("[MWM-Debug] Close button clicked")
                        val session = plugin.settingsSessionManager.getSession(player) ?: run {
                                plugin.logger.warning("[MWM-Debug] Session not found for close action")
                                return
                        }
                        val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid) ?: run {
                                plugin.logger.warning("[MWM-Debug] WorldData not found for close action")
                                return
                        }
                        plugin.worldSettingsGui.open(player, worldData)
                        plugin.logger.info("[MWM-Debug] Returned to settings after close")
                        return
                }
        }

        private fun handleEnvGravityConfirm(player: Player, worldData: WorldData) {
                val config = plugin.config
                val cost = config.getInt("environment.gravity.cost", 100)
                val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)

                if (stats.worldPoint < cost) {
                        player.sendMessage(
                                plugin.languageManager.getMessage(
                                        player,
                                        "gui.creation.insufficient",
                                        mapOf("shortage" to (cost - stats.worldPoint))
                                )
                        )
                        plugin.soundManager.playActionSound(player, "environment", "insufficient_points")
                        return
                }

                worldData.gravityMultiplier = 0.17
                stats.worldPoint -= cost
                worldData.cumulativePoints += cost

                plugin.playerStatsRepository.save(stats)
                plugin.worldConfigRepository.save(worldData)

                player.sendMessage(
                        plugin.languageManager.getMessage(
                                player,
                                "messages.env_gravity_changed",
                                mapOf("gravity" to "Moon", "multiplier" to "0.17")
                        )
                )
                player.sendMessage(
                        plugin.languageManager.getMessage(
                                player,
                                "messages.env_cost_paid",
                                mapOf("cost" to cost)
                        )
                )
                plugin.soundManager.playActionSound(player, "environment", "gravity_change")
                applyGravityToWorld(worldData)
                plugin.environmentGui.open(player, worldData)
        }

        private fun handleWeatherConfirm(player: Player, worldData: WorldData) {
                val session = plugin.settingsSessionManager.getSession(player) ?: return
                val nextWeather = session.getMetadata("temp_weather") as? String ?: return
                val config = plugin.config
                val cost = config.getInt("environment.weather.cost", 50)
                val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)

                if (stats.worldPoint < cost) {
                        player.sendMessage(
                                plugin.languageManager.getMessage(
                                        player,
                                        "gui.creation.insufficient",
                                        mapOf("shortage" to (cost - stats.worldPoint))
                                )
                        )
                        plugin.soundManager.playActionSound(player, "environment", "insufficient_points")
                        return
                }

                stats.worldPoint -= cost
                worldData.fixedWeather = if (nextWeather == "DEFAULT") null else nextWeather
                worldData.cumulativePoints += cost
                session.tempWeather = null

                plugin.playerStatsRepository.save(stats)
                plugin.worldConfigRepository.save(worldData)

                player.sendMessage(
                        plugin.languageManager.getMessage(
                                player,
                                "messages.env_cost_paid",
                                mapOf("cost" to cost)
                        )
                )
                applyWeatherToWorld(worldData)
                plugin.soundManager.playActionSound(player, "environment", "weather_change")
                plugin.environmentGui.open(player, worldData)
        }

        private fun handleEnvBiomeConfirm(player: Player, worldData: WorldData, biomeId: String) {
                val lang = plugin.languageManager
                val config = plugin.config
                val cost = config.getInt("environment.biome.cost", 500)
                val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)

                if (stats.worldPoint < cost) {
                        player.sendMessage(
                                plugin.languageManager.getMessage(
                                        player,
                                        "gui.creation.insufficient",
                                        mapOf("shortage" to (cost - stats.worldPoint))
                                )
                        )
                        plugin.soundManager.playActionSound(player, "environment", "insufficient_points")
                        return
                }

                try {
                        org.bukkit.block.Biome.valueOf(biomeId.uppercase())
                        worldData.fixedBiome = biomeId.uppercase()
                        worldData.partialBiomes.clear()
                        stats.worldPoint -= cost
                        worldData.cumulativePoints += cost

                        plugin.playerStatsRepository.save(stats)
                        plugin.worldConfigRepository.save(worldData)

                        val biomeName = lang.getMessage(player, "biomes.${biomeId.lowercase()}")
                        player.sendMessage(lang.getMessage(player, "messages.env_biome_changed", mapOf("biome" to biomeName)))
                        player.sendMessage(lang.getMessage(player, "messages.env_cost_paid", mapOf("cost" to cost)))
                        plugin.soundManager.playActionSound(player, "environment", "biome_change")
                        applyBiomeToWorld(worldData)
                        plugin.environmentGui.open(player, worldData)

                } catch (e: Exception) {
                        player.sendMessage("§cInvalid Biome: $biomeId")
                }
        }

        private fun handleExpandConfirm(player: Player, worldData: WorldData) {
                val session = plugin.settingsSessionManager.getSession(player) ?: return
                val cost = (session.getMetadata("expand_cost") as? Number)?.toInt() ?: return
                val direction = session.expansionDirection
                val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)

                if (stats.worldPoint < cost) {
                        player.sendMessage(
                                plugin.languageManager.getMessage(
                                        player,
                                        "gui.creation.insufficient",
                                        mapOf("shortage" to (cost - stats.worldPoint))
                                )
                        )
                        plugin.soundManager.playActionSound(player, "environment", "insufficient_points")
                        return
                }

                if (performExpansion(worldData, direction)) {
                        stats.worldPoint -= cost
                        worldData.cumulativePoints += cost
                        plugin.playerStatsRepository.save(stats)
                        plugin.worldConfigRepository.save(worldData)

                        player.sendMessage(
                                plugin.languageManager.getMessage(
                                        player,
                                        "messages.expand_complete",
                                        mapOf(
                                                "level_before" to (worldData.borderExpansionLevel - 1),
                                                "level_after" to worldData.borderExpansionLevel,
                                                "remaining" to stats.worldPoint
                                        )
                                )
                        )
                        plugin.soundManager.playActionSound(player, "creation", "wizard_next")
                } else {
                        player.sendMessage(plugin.languageManager.getMessage("error.expand_failed"))
                }
                plugin.worldSettingsGui.open(player, worldData)
        }

        private fun handleResetExpansionConfirm(player: Player, worldData: WorldData) {
                // TODO: resetExpansion機能は未実装
                player.sendMessage(plugin.languageManager.getMessage("messages.expansion_reset_complete"))
                plugin.soundManager.playActionSound(player, "environment", "gravity_change")
                plugin.worldSettingsGui.open(player, worldData)
        }

        private fun handleDeleteWorldConfirm(player: Player, worldData: WorldData) {
                val worldName = worldData.name
                if (plugin.worldService.deleteWorld(worldData.uuid).get()) {
                        player.sendMessage(
                                plugin.languageManager.getMessage(
                                        player,
                                        "messages.delete_success",
                                        mapOf("world" to worldName)
                                )
                        )
                        plugin.soundManager.playActionSound(player, "creation", "delete")
                        plugin.settingsSessionManager.endSession(player)
                        player.closeInventory()
                } else {
                        player.sendMessage(plugin.languageManager.getMessage("error.delete_failed"))
                        plugin.worldSettingsGui.open(player, worldData)
                }
        }

        private fun handleUnarchiveWorldConfirm(player: Player, worldData: WorldData) {
                player.closeInventory()
                player.sendMessage(
                        plugin.languageManager.getMessage(
                                player,
                                "messages.unarchive_start"
                        )
                )

                plugin.worldService.unarchiveWorld(worldData.uuid)
                        .thenAccept { success: Boolean ->
                                Bukkit.getScheduler().runTask(
                                        plugin,
                                        Runnable {
                                if (success) {
                                                        // クールダウン記録（プレイヤーごと）
                                                        val now = java.time.LocalDateTime.now()
                                                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                                                        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                                                        stats.lastArchiveActionAt = now
                                                        plugin.playerStatsRepository.save(stats)
                                                        player.sendMessage(
                                                                plugin.languageManager.getMessage(
                                                                        player,
                                                                        "messages.unarchive_success"
                                                                )
                                                        )
                                                        plugin.worldService.teleportToWorld(player, worldData.uuid)
                                                } else {
                                                        player.sendMessage(
                                                                plugin.languageManager.getMessage(
                                                                        player,
                                                                        "error.unarchive_failed"
                                                                )
                                                        )
                                                }
                                        }
                                )
                        }
                plugin.settingsSessionManager.endSession(player)
        }

        private fun handleVisitorKickConfirm(player: Player, worldData: WorldData, visitorUuid: UUID) {
                val visitor = Bukkit.getPlayer(visitorUuid)
                val worldFolderName = worldData.customWorldName ?: "my_world.${worldData.uuid}"
                if (visitor != null && visitor.world.name == worldFolderName) {
                        val config = plugin.config
                        val evacWorldName = config.getString("evacuation_location.world", "world") ?: "world"
                        val evacWorld = Bukkit.getWorld(evacWorldName) ?: Bukkit.getWorlds()[0]
                        val x = config.getDouble("evacuation_location.x", evacWorld.spawnLocation.x)
                        val y = config.getDouble("evacuation_location.y", evacWorld.spawnLocation.y)
                        val z = config.getDouble("evacuation_location.z", evacWorld.spawnLocation.z)
                        val yaw = config.getDouble("evacuation_location.yaw", evacWorld.spawnLocation.yaw.toDouble()).toFloat()
                        val pitch = config.getDouble("evacuation_location.pitch", evacWorld.spawnLocation.pitch.toDouble()).toFloat()

                        val evacuationLoc = org.bukkit.Location(evacWorld, x, y, z, yaw, pitch)
                        visitor.teleport(evacuationLoc)
                        visitor.sendMessage(plugin.languageManager.getMessage(visitor, "messages.kicked"))
                        player.sendMessage(
                                plugin.languageManager.getMessage(
                                        player,
                                        "messages.kicked_success",
                                        mapOf("player" to visitor.name)
                                )
                        )
                plugin.soundManager.playActionSound(player, "world_settings", "kick")
                }
                plugin.worldSettingsGui.openVisitorManagement(player, worldData)
        }

        private fun handleBedrockDialogCancel(player: Player, worldData: WorldData) {
                val session = plugin.settingsSessionManager.getSession(player) ?: return
                when (session.action) {
                        SettingsAction.ENV_CONFIRM -> plugin.environmentGui.open(player, worldData)
                        SettingsAction.EXPAND_CONFIRM -> plugin.worldSettingsGui.openExpansionMethodSelection(player, worldData)
                        SettingsAction.VISITOR_KICK_CONFIRM -> plugin.worldSettingsGui.openVisitorManagement(player, worldData)
                        SettingsAction.MEMBER_REMOVE_CONFIRM,
                        SettingsAction.MEMBER_TRANSFER_CONFIRM,
                        SettingsAction.MEMBER_PENDING_INVITE_CANCEL_CONFIRM ->
                                reopenMemberManagementLatest(player, worldData.uuid)
                        SettingsAction.RESET_EXPANSION_CONFIRM,
                        SettingsAction.DELETE_WORLD_CONFIRM,
                        SettingsAction.DELETE_WORLD_CONFIRM_FINAL,
                        SettingsAction.ARCHIVE_WORLD_FROM_CRITICAL -> plugin.worldSettingsGui.openCriticalSettings(player, worldData)
                        SettingsAction.UNARCHIVE_CONFIRM -> plugin.menuEntryRouter.openPlayerWorld(player)
                        else -> plugin.worldSettingsGui.open(player, worldData)
                }
        }

        private fun handleBedrockDialogAction(player: Player, worldData: WorldData, actionId: String) {
                val keyVal = if (actionId.startsWith("mwm:")) actionId.substringAfter("mwm:") else actionId

                if (keyVal == "confirm/cancel") {
                        handleBedrockDialogCancel(player, worldData)
                        return
                }

                val session = plugin.settingsSessionManager.getSession(player) ?: return

                if (keyVal.startsWith("confirm/env_change/")) {
                        val type = keyVal.substringAfter("confirm/env_change/")
                        when (type) {
                                "gravity" -> handleEnvGravityConfirm(player, worldData)
                                "weather" -> handleWeatherConfirm(player, worldData)
                                "biome" -> {
                                        val biomeId = session.getMetadata("temp_biome") as? String ?: return
                                        handleEnvBiomeConfirm(player, worldData, biomeId)
                                }
                        }
                        return
                }

                if (keyVal == "confirm/archive_world") {
                        player.sendMessage(
                                plugin.languageManager.getMessage(
                                        player,
                                        "messages.archive_success",
                                        mapOf("world" to worldData.name)
                                )
                        )
                        worldData.isArchived = true
                        plugin.worldConfigRepository.save(worldData)
                        plugin.settingsSessionManager.endSession(player)
                        player.closeInventory()
                        return
                }

                if (keyVal == "confirm/archive_world_critical") {
                        player.sendMessage(plugin.languageManager.getMessage(player, "messages.archive_start"))
                        plugin.worldService.archiveWorld(worldData.uuid)
                                .thenAccept { success: Boolean ->
                                        Bukkit.getScheduler().runTask(plugin, Runnable {
                                                if (success) {
                                                        val now = java.time.LocalDateTime.now()
                                                                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                                                        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                                                        stats.lastArchiveActionAt = now
                                                        plugin.playerStatsRepository.save(stats)
                                                        player.sendMessage(
                                                                plugin.languageManager.getMessage(
                                                                        player,
                                                                        "messages.archive_success",
                                                                        mapOf("world" to worldData.name)
                                                                )
                                                        )
                                                } else {
                                                        player.sendMessage(plugin.languageManager.getMessage(player, "messages.archive_failed"))
                                                }
                                        })
                                }
                        plugin.settingsSessionManager.endSession(player)
                        return
                }

                if (keyVal == "confirm/expand") {
                        handleExpandConfirm(player, worldData)
                        return
                }

                if (keyVal == "confirm/reset_expansion") {
                        handleResetExpansionConfirm(player, worldData)
                        return
                }

                if (keyVal == "confirm/delete_world_step1") {
                        val title = LegacyComponentSerializer.legacySection().deserialize(
                                plugin.languageManager.getMessage(player, "gui.confirm.delete_2.title")
                        )
                        val bodyLines = plugin.languageManager
                                .getMessageList(player, "gui.confirm.delete_2.lore")
                                .map { LegacyComponentSerializer.legacySection().deserialize(it) }
                        DialogConfirmManager.showSimpleConfirmationDialog(
                                player,
                                plugin,
                                title,
                                bodyLines,
                                "mwm:confirm/delete_world",
                                "mwm:confirm/cancel",
                                plugin.languageManager.getMessage(player, "gui.confirm.delete_2.confirm_btn"),
                                plugin.languageManager.getMessage(player, "gui.common.cancel"),
                                onBedrockConfirm = {
                                        handleBedrockDialogAction(player, worldData, "mwm:confirm/delete_world")
                                },
                                onBedrockCancel = {
                                        handleBedrockDialogCancel(player, worldData)
                                },
                                onBedrockFallback = {
                                        plugin.worldSettingsGui.openDeleteWorldConfirmation2(player, worldData)
                                }
                        )
                        return
                }

                if (keyVal == "confirm/delete_world") {
                        handleDeleteWorldConfirm(player, worldData)
                        return
                }

                if (keyVal == "confirm/unarchive_world") {
                        handleUnarchiveWorldConfirm(player, worldData)
                        return
                }

                if (keyVal.startsWith("confirm/member_remove/")) {
                        val targetUuidStr = keyVal.substringAfter("confirm/member_remove/")
                        val targetUuid = try {
                                UUID.fromString(targetUuidStr)
                        } catch (_: Exception) {
                                return
                        }
                        val memberName = PlayerNameUtil.getNameOrDefault(targetUuid, "Unknown")
                        worldData.members.remove(targetUuid)
                        worldData.moderators.remove(targetUuid)
                        plugin.worldConfigRepository.save(worldData)
                        Bukkit.getPluginManager().callEvent(
                                MwmMemberRemovedEvent(
                                        worldUuid = worldData.uuid,
                                        memberUuid = targetUuid,
                                        memberName = memberName,
                                        removedByUuid = player.uniqueId,
                                        source = MwmMemberRemoveSource.MANUAL
                                )
                        )
                        player.sendMessage(plugin.languageManager.getMessage("messages.member_deleted"))
                        plugin.macroManager.execute(
                                "on_member_remove",
                                mapOf(
                                        "world_uuid" to worldData.uuid.toString(),
                                        "member" to memberName
                                )
                        )
                        reopenMemberManagementLatest(player, worldData.uuid)
                        return
                }

                if (keyVal.startsWith("confirm/member_transfer/")) {
                        val targetUuidStr = keyVal.substringAfter("confirm/member_transfer/")
                        val newOwnerId = try {
                                UUID.fromString(targetUuidStr)
                        } catch (_: Exception) {
                                return
                        }
                        val oldOwnerId = worldData.owner
                        val oldOwnerName = PlayerNameUtil.getNameOrDefault(oldOwnerId, "Unknown")
                        val newOwnerName = PlayerNameUtil.getNameOrDefault(newOwnerId, "Unknown")
                        worldData.owner = newOwnerId
                        if (!worldData.moderators.contains(oldOwnerId)) {
                                worldData.moderators.add(oldOwnerId)
                        }
                        worldData.moderators.remove(newOwnerId)
                        worldData.members.remove(newOwnerId)
                        plugin.worldConfigRepository.save(worldData)
                        Bukkit.getPluginManager().callEvent(
                                MwmOwnerTransferredEvent(
                                        worldUuid = worldData.uuid,
                                        oldOwnerUuid = oldOwnerId,
                                        oldOwnerName = oldOwnerName,
                                        newOwnerUuid = newOwnerId,
                                        newOwnerName = newOwnerName,
                                        transferredByUuid = player.uniqueId,
                                        source = MwmOwnerTransferSource.MANUAL
                                )
                        )
                        plugin.macroManager.execute(
                                "on_owner_transfer",
                                mapOf(
                                        "world_uuid" to worldData.uuid.toString(),
                                        "old_owner" to oldOwnerName,
                                        "new_owner" to newOwnerName
                                )
                        )
                        player.sendMessage(
                                plugin.languageManager.getMessage(
                                        player,
                                        "messages.owner_transferred",
                                        mapOf("old_owner" to newOwnerName)
                                )
                        )
                        reopenMemberManagementLatest(player, worldData.uuid)
                        return
                }

                if (keyVal.startsWith("confirm/member_pending_invite_cancel/")) {
                        val decisionIdStr =
                                keyVal.substringAfter("confirm/member_pending_invite_cancel/")
                        val decisionId =
                                runCatching { UUID.fromString(decisionIdStr) }.getOrNull() ?: return
                        cancelMemberInviteByDecisionId(player, worldData.uuid, decisionId)
                        return
                }

                if (keyVal.startsWith("confirm/visitor_kick/")) {
                        val targetUuidStr = keyVal.substringAfter("confirm/visitor_kick/")
                        val targetUuid = try {
                                UUID.fromString(targetUuidStr)
                        } catch (_: Exception) {
                                return
                        }
                        handleVisitorKickConfirm(player, worldData, targetUuid)
                }
        }

        @EventHandler
        fun onCustomClick(event: PlayerCustomClickEvent) {
                val identifier = event.identifier
                val keyVal = identifier.value()
                val conn = event.commonConnection as? io.papermc.paper.connection.PlayerGameConnection ?: return
                val player = conn.player
                val session = plugin.settingsSessionManager.getSession(player) ?: return
                val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid) ?: return

                if (identifier == Key.key("mwm:confirm/cancel")) {
                        plugin.soundManager.playClickSound(player, null, "world_settings")
                        DialogConfirmManager.safeCloseDialog(player)

                        when (session.action) {
                                SettingsAction.ENV_CONFIRM -> plugin.environmentGui.open(player, worldData)
                                SettingsAction.EXPAND_CONFIRM -> plugin.worldSettingsGui.openExpansionMethodSelection(player, worldData)
                                SettingsAction.VISITOR_KICK_CONFIRM -> plugin.worldSettingsGui.openVisitorManagement(player, worldData)
                                SettingsAction.MEMBER_REMOVE_CONFIRM,
                                SettingsAction.MEMBER_TRANSFER_CONFIRM,
                                SettingsAction.MEMBER_PENDING_INVITE_CANCEL_CONFIRM ->
                                        reopenMemberManagementLatest(player, worldData.uuid)
                                SettingsAction.RESET_EXPANSION_CONFIRM,
                                SettingsAction.DELETE_WORLD_CONFIRM,
                                SettingsAction.DELETE_WORLD_CONFIRM_FINAL,
                                SettingsAction.ARCHIVE_WORLD_FROM_CRITICAL -> plugin.worldSettingsGui.openCriticalSettings(player, worldData)
                                SettingsAction.UNARCHIVE_CONFIRM -> plugin.playerWorldGui.open(player)
                                else -> plugin.worldSettingsGui.open(player, worldData)
                        }
                        return
                }

                if (keyVal.startsWith("confirm/env_change/")) {
                        DialogConfirmManager.safeCloseDialog(player)
                        val type = keyVal.substringAfter("confirm/env_change/")
                        when (type) {
                                "gravity" -> handleEnvGravityConfirm(player, worldData)
                                "weather" -> handleWeatherConfirm(player, worldData)
                                "biome" -> {
                                        val biomeId = session.getMetadata("temp_biome") as? String ?: return
                                        handleEnvBiomeConfirm(player, worldData, biomeId)
                                }
                        }
                        return
                }

                if (identifier == Key.key("mwm:confirm/archive_world")) {
                        DialogConfirmManager.safeCloseDialog(player)
                        player.sendMessage(
                                plugin.languageManager.getMessage(
                                        player,
                                        "messages.archive_success",
                                        mapOf("world" to worldData.name)
                                )
                        )
                        worldData.isArchived = true
                        plugin.worldConfigRepository.save(worldData)
                        plugin.settingsSessionManager.endSession(player)
                        player.closeInventory()
                        return
                }

                if (identifier == Key.key("mwm:confirm/archive_world_critical")) {
                        DialogConfirmManager.safeCloseDialog(player)
                        player.sendMessage(plugin.languageManager.getMessage(player, "messages.archive_start"))
                        plugin.worldService.archiveWorld(worldData.uuid)
                            .thenAccept { success: Boolean ->
                                Bukkit.getScheduler().runTask(plugin, Runnable {
                                    if (success) {
                                        val now = java.time.LocalDateTime.now()
                                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                                        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                                        stats.lastArchiveActionAt = now
                                        plugin.playerStatsRepository.save(stats)
                                        player.sendMessage(plugin.languageManager.getMessage(
                                            player,
                                            "messages.archive_success",
                                            mapOf("world" to worldData.name)
                                        ))
                                    } else {
                                        player.sendMessage(plugin.languageManager.getMessage(
                                            player,
                                            "messages.archive_failed"
                                        ))
                                    }
                                })
                            }
                        plugin.settingsSessionManager.endSession(player)
                        return
                }

                if (identifier == Key.key("mwm:confirm/expand")) {
                        DialogConfirmManager.safeCloseDialog(player)
                        handleExpandConfirm(player, worldData)
                        return
                }

                if (identifier == Key.key("mwm:confirm/reset_expansion")) {
                        DialogConfirmManager.safeCloseDialog(player)
                        handleResetExpansionConfirm(player, worldData)
                        return
                }

                if (identifier == Key.key("mwm:confirm/delete_world_step1")) {
                        DialogConfirmManager.safeCloseDialog(player)
                        val title = LegacyComponentSerializer.legacySection().deserialize(
                                plugin.languageManager.getMessage(player, "gui.confirm.delete_2.title")
                        )
                        val bodyLines = plugin.languageManager
                                .getMessageList(player, "gui.confirm.delete_2.lore")
                                .map { LegacyComponentSerializer.legacySection().deserialize(it) }
                        DialogConfirmManager.showSimpleConfirmationDialog(
                                player,
                                plugin,
                                title,
                                bodyLines,
                                "mwm:confirm/delete_world",
                                "mwm:confirm/cancel",
                                plugin.languageManager.getMessage(player, "gui.confirm.delete_2.confirm_btn"),
                                plugin.languageManager.getMessage(player, "gui.common.cancel")
                        )
                        return
                }

                if (identifier == Key.key("mwm:confirm/delete_world")) {
                        DialogConfirmManager.safeCloseDialog(player)
                        handleDeleteWorldConfirm(player, worldData)
                        return
                }

                if (identifier == Key.key("mwm:confirm/unarchive_world")) {
                        DialogConfirmManager.safeCloseDialog(player)
                        handleUnarchiveWorldConfirm(player, worldData)
                        return
                }

                if (keyVal.startsWith("confirm/member_remove/")) {
                        DialogConfirmManager.safeCloseDialog(player)
                        val targetUuidStr = keyVal.substringAfter("confirm/member_remove/")
                        val targetUuid = try { UUID.fromString(targetUuidStr) } catch(e: Exception) { return }
                        val memberName = PlayerNameUtil.getNameOrDefault(targetUuid, "Unknown")
                        worldData.members.remove(targetUuid)
                        worldData.moderators.remove(targetUuid)
                        plugin.worldConfigRepository.save(worldData)
                        Bukkit.getPluginManager().callEvent(
                                MwmMemberRemovedEvent(
                                        worldUuid = worldData.uuid,
                                        memberUuid = targetUuid,
                                        memberName = memberName,
                                        removedByUuid = player.uniqueId,
                                        source = MwmMemberRemoveSource.MANUAL
                                )
                        )
                        player.sendMessage(plugin.languageManager.getMessage("messages.member_deleted"))
                        plugin.macroManager.execute(
                                "on_member_remove",
                                mapOf(
                                        "world_uuid" to worldData.uuid.toString(),
                                        "member" to memberName
                                )
                        )
                        reopenMemberManagementLatest(player, worldData.uuid)
                        return
                }

                if (keyVal.startsWith("confirm/member_transfer/")) {
                        DialogConfirmManager.safeCloseDialog(player)
                        val targetUuidStr = keyVal.substringAfter("confirm/member_transfer/")
                        val newOwnerId = try { UUID.fromString(targetUuidStr) } catch(e: Exception) { return }
                        val oldOwnerId = worldData.owner
                        val oldOwnerName = PlayerNameUtil.getNameOrDefault(oldOwnerId, "Unknown")
                        val newOwnerName = PlayerNameUtil.getNameOrDefault(newOwnerId, "Unknown")
                        worldData.owner = newOwnerId
                        if (!worldData.moderators.contains(oldOwnerId)) {
                                worldData.moderators.add(oldOwnerId)
                        }
                        worldData.moderators.remove(newOwnerId)
                        worldData.members.remove(newOwnerId)
                        plugin.worldConfigRepository.save(worldData)
                        Bukkit.getPluginManager().callEvent(
                                MwmOwnerTransferredEvent(
                                        worldUuid = worldData.uuid,
                                        oldOwnerUuid = oldOwnerId,
                                        oldOwnerName = oldOwnerName,
                                        newOwnerUuid = newOwnerId,
                                        newOwnerName = newOwnerName,
                                        transferredByUuid = player.uniqueId,
                                        source = MwmOwnerTransferSource.MANUAL
                                )
                        )
                        plugin.macroManager.execute(
                                "on_owner_transfer",
                                mapOf(
                                        "world_uuid" to worldData.uuid.toString(),
                                        "old_owner" to oldOwnerName,
                                        "new_owner" to newOwnerName
                                )
                        )
                        player.sendMessage(
                                plugin.languageManager.getMessage(
                                        player,
                                        "messages.owner_transferred",
                                        mapOf("old_owner" to newOwnerName)
                                )
                        )
                        reopenMemberManagementLatest(player, worldData.uuid)
                        return
                }

                if (keyVal.startsWith("confirm/member_pending_invite_cancel/")) {
                        DialogConfirmManager.safeCloseDialog(player)
                        val decisionIdStr =
                                keyVal.substringAfter("confirm/member_pending_invite_cancel/")
                        val decisionId =
                                runCatching { UUID.fromString(decisionIdStr) }.getOrNull() ?: return
                        cancelMemberInviteByDecisionId(player, worldData.uuid, decisionId)
                        return
                }

                if (keyVal.startsWith("confirm/visitor_kick/")) {
                        DialogConfirmManager.safeCloseDialog(player)
                        val targetUuidStr = keyVal.substringAfter("confirm/visitor_kick/")
                        val targetUuid = try { UUID.fromString(targetUuidStr) } catch(e: Exception) { return }
                        handleVisitorKickConfirm(player, worldData, targetUuid)
                        return
                }
        }
}
