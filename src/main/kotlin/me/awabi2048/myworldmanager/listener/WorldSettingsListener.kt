package me.awabi2048.myworldmanager.listener

import io.papermc.paper.event.player.AsyncChatEvent
import java.util.UUID
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.model.WorldTag
import me.awabi2048.myworldmanager.session.SettingsAction
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.block.BlockFace
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

class WorldSettingsListener : Listener {

        private val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
        private val pendingExpansions = mutableMapOf<UUID, PendingExpansion>()

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
                                                                .openMemberManagement(
                                                                        player,
                                                                        worldData,
                                                                        targetPage
                                                                )
                                                }
                                        }
                                        return
                                }

                                if (type == ItemTag.TYPE_GUI_MEMBER_INVITE) {
                                        plugin.soundManager.playClickSound(
                                                player,
                                                item,
                                                "world_settings"
                                        )
                                        session.action = SettingsAction.MEMBER_INVITE
                                        player.closeInventory()
                                        val cancelWord =
                                                plugin.config.getString(
                                                        "creation.cancel_word",
                                                        "cancel"
                                                )
                                                        ?: "cancel"
                                        val cancelInfo =
                                                lang.getMessage(
                                                        player,
                                                        "messages.chat_input_cancel_hint",
                                                        mapOf("word" to cancelWord)
                                                )
                                        player.sendMessage(
                                                plugin.languageManager.getMessage(
                                                        "messages.member_invite_input"
                                                ) + " " + cancelInfo
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

                                if (type == ItemTag.TYPE_GUI_MEMBER_ITEM) {
                                        val memberId = ItemTag.getWorldUuid(item)
                                        if (memberId != null && memberId != player.uniqueId) {
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

                                                                if (currentCounts >= maxCounts) {
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

                                                                plugin.worldSettingsGui
                                                                        .openMemberTransferConfirmation(
                                                                                player,
                                                                                worldData,
                                                                                memberId
                                                                        )
                                                        } else if (event.isRightClick) {
                                                                plugin.soundManager.playClickSound(
                                                                        player,
                                                                        item,
                                                                        "world_settings"
                                                                )
                                                                // メンバー削除
                                                                plugin.worldSettingsGui
                                                                        .openMemberRemoveConfirmation(
                                                                                player,
                                                                                worldData,
                                                                                memberId
                                                                        )
                                                        }
                                                } else if (event.isLeftClick) {
                                                        plugin.soundManager.playClickSound(
                                                                player,
                                                                item,
                                                                "world_settings"
                                                        )
                                                        // 権限変更
                                                        val isModerator = worldData.moderators.contains(memberId)
                                                        if (isModerator) {
                                                                // モデレーター -> メンバー
                                                                worldData.moderators.remove(memberId)
                                                                if (!worldData.members.contains(memberId)) {
                                                                        worldData.members.add(memberId)
                                                                }
                                                        } else {
                                                                // メンバー -> モデレーター
                                                                worldData.members.remove(memberId)
                                                                if (!worldData.moderators.contains(memberId)) {
                                                                        worldData.moderators.add(memberId)
                                                                }
                                                        }
                                                        plugin.worldConfigRepository.save(worldData)

                                                        // GUI再描画
                                                        plugin.worldSettingsGui.openMemberManagement(player, worldData, 0, false)
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
                                        plugin.worldSettingsGui.openMemberManagement(
                                                player,
                                                worldData
                                        )
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
                                        plugin.worldSettingsGui.openMemberManagement(
                                                player,
                                                worldData
                                        )
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
                                        plugin.worldSettingsGui.openMemberManagement(
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

                                        plugin.worldSettingsGui.openMemberManagement(
                                                player,
                                                worldData
                                        )
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
                                        if (event.isRightClick) {
                                                val visitorUuid =
                                                        ItemTag.getWorldUuid(item) ?: return
                                                plugin.soundManager.playClickSound(
                                                        player,
                                                        item,
                                                        "world_settings"
                                                )
                                                plugin.worldSettingsGui.openVisitorKickConfirmation(
                                                        player,
                                                        worldData,
                                                        visitorUuid
                                                )
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
                                        // Note: openExpansionConfirmation updates session action to
                                        // EXPAND_CONFIRM
                                        plugin.worldSettingsGui.openExpansionConfirmation(
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
                                        player.sendMessage(
                                                plugin.languageManager.getMessage(
                                                        player,
                                                        "messages.expand_direction_prompt"
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
                                                
                                                // Beta Features Check
                                                val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                                                val useDialog = stats.betaFeaturesEnabled
                                                
                                                if (useDialog) {
                                                    // Dialog Flow
                                                    if (event.isRightClick) {
                                                        plugin.settingsSessionManager.updateSessionAction(player, worldData.uuid, SettingsAction.CHANGE_DESCRIPTION)
                                                    } else {
                                                        plugin.settingsSessionManager.updateSessionAction(player, worldData.uuid, SettingsAction.RENAME_WORLD)
                                                    }
                                                    
                                                    player.closeInventory()
                                                    Bukkit.getScheduler().runTask(plugin, Runnable {
                                                        if (event.isRightClick) {
                                                            showDescriptionDialog(player, worldData)
                                                        } else {
                                                            showRenameDialog(player, worldData)
                                                        }
                                                    })
                                                } else {
                                                    // Chat Flow
                                                    val lang = plugin.languageManager
                                                    val cancelWord = plugin.config.getString("creation.cancel_word", "cancel") ?: "cancel"
                                                    val cancelInfo = lang.getMessage(player, "messages.chat_input_cancel_hint", mapOf("word" to cancelWord))
                                                    
                                                    if (event.isRightClick) {
                                                        plugin.settingsSessionManager.updateSessionAction(player, worldData.uuid, SettingsAction.CHANGE_DESCRIPTION)
                                                        // Fallback hardcoded or valid key
                                                        player.sendMessage("§e新しい説明文を入力してください。 $cancelInfo")
                                                    } else {
                                                        plugin.settingsSessionManager.updateSessionAction(player, worldData.uuid, SettingsAction.RENAME_WORLD)
                                                        player.sendMessage("§e新しいワールド名を入力してください。 $cancelInfo")
                                                    }
                                                    player.closeInventory()
                                                }

                                        }
                                        ItemTag.TYPE_GUI_SETTING_SPAWN -> {
                                                plugin.soundManager.playClickSound(
                                                        player,
                                                        clickedItem,
                                                        "world_settings"
                                                )
                                                val isGuest = event.isLeftClick
                                                val action =
                                                        if (isGuest) SettingsAction.SET_SPAWN_GUEST
                                                        else SettingsAction.SET_SPAWN_MEMBER
                                                val typeKey =
                                                        if (isGuest) "gui.settings.spawn.type.guest"
                                                        else "gui.settings.spawn.type.member"
                                                val typeName =
                                                        plugin.languageManager.getMessage(
                                                                player,
                                                                typeKey
                                                        )

                                                player.sendMessage(
                                                        plugin.languageManager.getMessage(
                                                                player,
                                                                "messages.spawn_set_start",
                                                                mapOf("type" to typeName)
                                                        )
                                                )
                                                plugin.settingsSessionManager.updateSessionAction(
                                                        player,
                                                        worldData.uuid,
                                                        action
                                                )
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
                                                plugin.soundManager.playClickSound(
                                                        player,
                                                        clickedItem,
                                                        "world_settings"
                                                )

                                                val config = plugin.config
                                                val stats =
                                                        plugin.playerStatsRepository.findByUuid(
                                                                player.uniqueId
                                                        )
                                                val costsSection =
                                                        config.getConfigurationSection(
                                                                "expansion.costs"
                                                        )
                                                val maxLevel =
                                                        costsSection?.getKeys(false)?.size ?: 3
                                                val baseCost =
                                                        config.getInt("expansion.base_cost", 100)
                                                val multiplier =
                                                        config.getDouble(
                                                                "expansion.cost_multiplier",
                                                                2.0
                                                        )

                                                val currentLevel = worldData.borderExpansionLevel
                                                val targetLevel = currentLevel + 1

                                                if (currentLevel >= maxLevel) {
                                                        player.sendMessage(
                                                                plugin.languageManager.getMessage(
                                                                        "error.max_expansion_reached"
                                                                )
                                                        )
                                                        return
                                                }
                                                val cost =
                                                        if (config.contains(
                                                                        "expansion.costs.$targetLevel"
                                                                )
                                                        ) {
                                                                config.getInt(
                                                                        "expansion.costs.$targetLevel"
                                                                )
                                                        } else {
                                                                (baseCost *
                                                                                Math.pow(
                                                                                        multiplier,
                                                                                        currentLevel
                                                                                                .toDouble()
                                                                                ))
                                                                        .toInt()
                                                        }

                                                if (stats.worldPoint < cost) {
                                                        player.sendMessage(
                                                                plugin.languageManager.getMessage(
                                                                        player,
                                                                        "error.expand_insufficient_points",
                                                                        mapOf("cost" to cost)
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
                                                val nextLevel =
                                                        GuiHelper.getNextValue(
                                                                worldData.publishLevel,
                                                                PublishLevel.values(),
                                                                event.isRightClick
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
                                                plugin.worldSettingsGui.openArchiveConfirmation(
                                                        player,
                                                        worldData
                                                )
                                        }
                                        ItemTag.TYPE_GUI_SETTING_TAGS -> {
                                                plugin.soundManager.playClickSound(
                                                        player,
                                                        clickedItem,
                                                        "world_settings"
                                                )
                                                // Check Beta Features
                                                val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                                                if (stats.betaFeaturesEnabled) {
                                                    // Dialog Flow
                                                    // Set session action to MANAGE_TAGS (so we know context if needed, though Dialog is separate)
                                                    // plugin.settingsSessionManager.updateSessionAction(player, worldData.uuid, SettingsAction.MANAGE_TAGS) 
                                                    // Actually we might not need session action for Dialog as it's self-contained, 
                                                    // but keeping it consistent is good.
                                                    plugin.settingsSessionManager.updateSessionAction(player, worldData.uuid, SettingsAction.MANAGE_TAGS, isGui = false)

                                                    player.closeInventory()
                                                    Bukkit.getScheduler().runTask(plugin, Runnable {
                                                        showTagEditorDialog(player, worldData)
                                                    })
                                                } else {
                                                    // Legacy GUI Flow
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
                                                        val cancelWord =
                                                                plugin.config.getString(
                                                                        "creation.cancel_word",
                                                                        "cancel"
                                                                )
                                                                        ?: "cancel"
                                                        val cancelInfo =
                                                                plugin.languageManager.getMessage(
                                                                        player,
                                                                        "messages.chat_input_cancel_hint",
                                                                        mapOf("word" to cancelWord)
                                                                )
                                                        player.sendMessage(
                                                                plugin.languageManager.getMessage(
                                                                        "messages.announcement_prompt"
                                                                ) + " " + cancelInfo
                                                        )
                                                        plugin.settingsSessionManager
                                                                .updateSessionAction(
                                                                        player,
                                                                        worldData.uuid,
                                                                        SettingsAction
                                                                                .SET_ANNOUNCEMENT
                                                                )
                                                        player.closeInventory()
                                                }
                                        }
                                        ItemTag.TYPE_GUI_SETTING_PORTALS -> {
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
                                                        // 繝昴・繧ｿ繝ｫ縺後↑縺・ｴ蜷医・髢九°縺ｪ縺・(繧ｯ繝ｪ繝・け髻ｳ縺縺鷹ｳｴ繧九√∪縺溘・菴輔ｂ縺励↑縺・
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
                                                if (player.hasPermission("myworldmanager.admin")) {
                                                        plugin.environmentGui.open(player, worldData)
                                                }
                                        }
                                }
                        }
                        SettingsAction.VIEW_ENVIRONMENT_SETTINGS -> {
                                event.isCancelled = true

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
                                                plugin.environmentConfirmGui.open(
                                                        player,
                                                        worldData,
                                                        clickedItem,
                                                        cost
                                                )
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
                                                plugin.environmentConfirmGui.open(
                                                        player,
                                                        worldData,
                                                        clickedItem,
                                                        cost
                                                )
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
                                                        handleWeatherConfirm(player, worldData)
                                                }
                                        }
                                        ItemTag.TYPE_GUI_ENV_GRAVITY,
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
                                        val tagName = type.substringAfter("tag_")
                                        val tag = WorldTag.valueOf(tagName)

                                        if (worldData.tags.contains(tag)) {
                                                worldData.tags.remove(tag)
                                        } else {
                                                val maxTags =
                                                        plugin.config.getInt("tags.max_count", 4)
                                                if (worldData.tags.size >= maxTags) {
                                                        player.sendMessage(
                                                                plugin.languageManager.getMessage(
                                                                        player,
                                                                        "messages.tag_max_reached",
                                                                        mapOf("limit" to maxTags)
                                                                )
                                                        )
                                                        return
                                                }
                                                worldData.tags.add(tag)
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
                                        if (event.isRightClick) {
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
                                                        plugin.portalManager.removePortalVisuals(
                                                                portal.id
                                                        )
                                                        plugin.portalRepository.removePortal(
                                                                portal.id
                                                        )

                                                        val returnItem =
                                                                me.awabi2048.myworldmanager.util
                                                                        .PortalItemUtil
                                                                        .createBasePortalItem(
                                                                                lang,
                                                                                player
                                                                        )
                                                        if (portal.worldUuid != null) {
                                                                val destData =
                                                                        plugin.worldConfigRepository
                                                                                .findByUuid(
                                                                                        portal.worldUuid!!
                                                                                )
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
                                                        } else if (portal.targetWorldName != null) {
                                                                val displayName =
                                                                        plugin.config.getString(
                                                                                "portal_targets.${portal.targetWorldName}"
                                                                        )
                                                                                ?: portal.targetWorldName!!
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
                                                        player.inventory.addItem(returnItem)

                                                        player.sendMessage(
                                                                lang.getMessage(
                                                                        player,
                                                                        "messages.portal_removed"
                                                                )
                                                        )
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
                                                plugin.worldSettingsGui
                                                        .openResetExpansionConfirmation(
                                                                player,
                                                                worldData
                                                        )
                                        }
                                        ItemTag.TYPE_GUI_SETTING_DELETE_WORLD -> {
                                                plugin.soundManager.playClickSound(
                                                        player,
                                                        item,
                                                        "world_settings"
                                                )
                                                plugin.worldSettingsGui
                                                        .openDeleteWorldConfirmation1(
                                                                player,
                                                                worldData
                                                        )
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

        @EventHandler(priority = EventPriority.LOWEST)
        fun onChat(event: AsyncChatEvent) {
                val player = event.player
                val session = plugin.settingsSessionManager.getSession(player) ?: return
                val worldData =
                        if (session.action == SettingsAction.ADMIN_PLAYER_FILTER) null
                        else plugin.worldConfigRepository.findByUuid(session.worldUuid)

                if (worldData == null && session.action != SettingsAction.ADMIN_PLAYER_FILTER)
                        return
                val lang = plugin.languageManager
                val messageText =
                        PlainTextComponentSerializer.plainText().serialize(event.originalMessage())

                event.isCancelled = true
                event.viewers().clear()

                Bukkit.getScheduler()
                        .runTask(
                                plugin,
                                Runnable {
                                        val cancelWord =
                                                plugin.config.getString(
                                                        "creation.cancel_word",
                                                        "cancel"
                                                )
                                                        ?: "cancel"
                                        if (messageText.equals(cancelWord, ignoreCase = true)) {
                                                if (worldData != null) {
                                                        plugin.worldSettingsGui.open(
                                                                player,
                                                                worldData
                                                        )
                                                } else {
                                                        plugin.worldGui.open(player)
                                                }
                                                player.sendMessage(
                                                        lang.getMessage(
                                                                player,
                                                                "messages.operation_cancelled"
                                                        )
                                                )
                                                return@Runnable
                                        }

                                        when (session.action) {
                                                SettingsAction.ADMIN_PLAYER_FILTER -> {
                                                        val targetName = messageText
                                                        val offlinePlayer =
                                                                Bukkit.getOfflinePlayer(targetName)

                                                        val adminSession =
                                                                plugin.adminGuiSessionManager
                                                                        .getSession(player.uniqueId)
                                                        adminSession.playerFilter =
                                                                offlinePlayer.uniqueId
                                                        if (adminSession.playerFilterType ==
                                                                        me.awabi2048.myworldmanager
                                                                                .session
                                                                                .PlayerFilterType
                                                                                .NONE
                                                        ) {
                                                                adminSession.playerFilterType =
                                                                        me.awabi2048.myworldmanager
                                                                                .session
                                                                                .PlayerFilterType
                                                                                .OWNER
                                                        }

                                                        player.sendMessage(
                                                                lang.getMessage(
                                                                        player,
                                                                        "messages.admin_player_filter_set",
                                                                        mapOf(
                                                                                "player" to
                                                                                        (offlinePlayer
                                                                                                .name
                                                                                                ?: targetName)
                                                                        )
                                                                )
                                                        )
                                                        plugin.settingsSessionManager.endSession(
                                                                player
                                                        )
                                                        plugin.worldGui.open(player)
                                                        return@Runnable
                                                }
                                                SettingsAction.RENAME_WORLD -> {
                                                        if (worldData == null) return@Runnable
                                                        val newName = messageText
                                                        if (newName.length < 3 ||
                                                                        newName.length > 16
                                                        ) {
                                                                player.sendMessage(
                                                                        lang.getMessage(
                                                                                player,
                                                                                "messages.world_name_invalid"
                                                                        )
                                                                )
                                                        } else {
                                                                worldData.name = newName
                                                                plugin.worldConfigRepository.save(
                                                                        worldData
                                                                )
                                                                player.sendMessage(
                                                                        lang.getMessage(
                                                                                player,
                                                                                "messages.world_name_change"
                                                                        )
                                                                )
                                                        }
                                                        plugin.settingsSessionManager.endSession(
                                                                player
                                                        )
                                                        plugin.worldSettingsGui.open(
                                                                player,
                                                                worldData
                                                        )
                                                        return@Runnable
                                                }
                                                SettingsAction.CHANGE_DESCRIPTION -> {
                                                        if (worldData == null) return@Runnable
                                                        val newDesc = messageText
                                                        worldData.description = newDesc
                                                        plugin.worldConfigRepository.save(worldData)
                                                        player.sendMessage(
                                                                lang.getMessage(
                                                                        player,
                                                                        "messages.world_desc_change"
                                                                )
                                                        )

                                                        plugin.settingsSessionManager.endSession(
                                                                player
                                                        )
                                                        plugin.worldSettingsGui.open(
                                                                player,
                                                                worldData
                                                        )
                                                        return@Runnable
                                                }
                                                SettingsAction.MEMBER_INVITE -> {
                                                        if (worldData == null) return@Runnable
                                                        val targetName = messageText
                                                        val target =
                                                                Bukkit.getPlayerExact(targetName)
                                                        if (target == null) {
                                                                player.sendMessage(
                                                                        lang.getMessage(
                                                                                player,
                                                                                "general.player_not_found"
                                                                        )
                                                                )
                                                                player.playSound(
                                                                        player.location,
                                                                        org.bukkit.Sound
                                                                                .ENTITY_VILLAGER_NO,
                                                                        1.0f,
                                                                        1.0f
                                                                )
                                                                plugin.settingsSessionManager
                                                                        .endSession(player)
                                                                plugin.worldSettingsGui
                                                                        .openMemberManagement(
                                                                                player,
                                                                                worldData,
                                                                                playSound = false
                                                                        )
                                                                return@Runnable
                                                        } else if (target == player) {
                                                                player.sendMessage(
                                                                        lang.getMessage(
                                                                                player,
                                                                                "messages.invite_self_error"
                                                                        )
                                                                )
                                                                player.playSound(
                                                                        player.location,
                                                                        org.bukkit.Sound
                                                                                .ENTITY_VILLAGER_NO,
                                                                        1.0f,
                                                                        1.0f
                                                                )
                                                                plugin.settingsSessionManager
                                                                        .endSession(player)
                                                                plugin.worldSettingsGui
                                                                        .openMemberManagement(
                                                                                player,
                                                                                worldData,
                                                                                playSound = false
                                                                        )
                                                                return@Runnable
                                                        } else {
                                                                val inviteText =
                                                                        lang.getMessage(
                                                                                target,
                                                                                "messages.member_invite_text",
                                                                                mapOf(
                                                                                        "player" to
                                                                                                player.name,
                                                                                        "world" to
                                                                                                worldData
                                                                                                        .name
                                                                                )
                                                                        )
                                                                val clickText =
                                                                        lang.getMessage(
                                                                                target,
                                                                                "messages.member_invite_click"
                                                                        )
                                                                val hoverText =
                                                                        lang.getMessage(
                                                                                target,
                                                                                "messages.member_invite_hover"
                                                                        )

                                                                val message =
                                                                        net.kyori.adventure.text
                                                                                .Component.text()
                                                                                .append(
                                                                                        net.kyori
                                                                                                .adventure
                                                                                                .text
                                                                                                .Component
                                                                                                .text(
                                                                                                        "-----------------------",
                                                                                                        net.kyori
                                                                                                                .adventure
                                                                                                                .text
                                                                                                                .format
                                                                                                                .NamedTextColor
                                                                                                                .GRAY
                                                                                                )
                                                                                )
                                                                                .append(
                                                                                        net.kyori
                                                                                                .adventure
                                                                                                .text
                                                                                                .Component
                                                                                                .newline()
                                                                                )
                                                                                .append(
                                                                                        net.kyori
                                                                                                .adventure
                                                                                                .text
                                                                                                .Component
                                                                                                .text(
                                                                                                        inviteText
                                                                                                )
                                                                                )
                                                                                .append(
                                                                                        net.kyori
                                                                                                .adventure
                                                                                                .text
                                                                                                .Component
                                                                                                .newline()
                                                                                )
                                                                                .append(
                                                                                        net.kyori
                                                                                                .adventure
                                                                                                .text
                                                                                                .Component
                                                                                                .text(
                                                                                                        clickText,
                                                                                                        net.kyori
                                                                                                                .adventure
                                                                                                                .text
                                                                                                                .format
                                                                                                                .NamedTextColor
                                                                                                                .AQUA
                                                                                                )
                                                                                                .decoration(
                                                                                                        net.kyori
                                                                                                                .adventure
                                                                                                                .text
                                                                                                                .format
                                                                                                                .TextDecoration
                                                                                                                .UNDERLINED,
                                                                                                        true
                                                                                                )
                                                                                                .clickEvent(
                                                                                                        net.kyori
                                                                                                                .adventure
                                                                                                                .text
                                                                                                                .event
                                                                                                                .ClickEvent
                                                                                                                .runCommand(
                                                                                                                        "/memberinviteaccept_internal"
                                                                                                                )
                                                                                                )
                                                                                                .hoverEvent(
                                                                                                        net.kyori
                                                                                                                .adventure
                                                                                                                .text
                                                                                                                .event
                                                                                                                .HoverEvent
                                                                                                                .showText(
                                                                                                                        net.kyori
                                                                                                                                .adventure
                                                                                                                                .text
                                                                                                                                .Component
                                                                                                                                .text(
                                                                                                                                        hoverText
                                                                                                                                )
                                                                                                                )
                                                                                                )
                                                                                )
                                                                                .append(
                                                                                        net.kyori
                                                                                                .adventure
                                                                                                .text
                                                                                                .Component
                                                                                                .newline()
                                                                                )
                                                                                .append(
                                                                                        net.kyori
                                                                                                .adventure
                                                                                                .text
                                                                                                .Component
                                                                                                .text(
                                                                                                        "-----------------------",
                                                                                                        net.kyori
                                                                                                                .adventure
                                                                                                                .text
                                                                                                                .format
                                                                                                                .NamedTextColor
                                                                                                                .GRAY
                                                                                                )
                                                                                )
                                                                                .build()

                                                                target.sendMessage(message)
                                                                plugin.memberInviteManager
                                                                        .addInvite(
                                                                                target.uniqueId,
                                                                                worldData.uuid,
                                                                                player.uniqueId,
                                                                                plugin.config
                                                                                        .getLong(
                                                                                                "invite.timeout_seconds",
                                                                                                60
                                                                                        )
                                                                        )
                                                                player.sendMessage(
                                                                        lang.getMessage(
                                                                                player,
                                                                                "messages.invite_sent_success",
                                                                                mapOf(
                                                                                        "player" to
                                                                                                target.name,
                                                                                        "world" to
                                                                                                worldData
                                                                                                        .name
                                                                                )
                                                                        )
                                                                )
                                                        }
                                                        session.action =
                                                                SettingsAction.MANAGE_MEMBERS
                                                        plugin.worldSettingsGui
                                                                .openMemberManagement(
                                                                        player,
                                                                        worldData
                                                                )
                                                        return@Runnable
                                                }
                                                SettingsAction.SET_ANNOUNCEMENT -> {
                                                        if (worldData == null) return@Runnable
                                                        val input = messageText

                                                        // 繝舌Μ繝・・繧ｷ繝ｧ繝ｳ
                                                        val maxLines =
                                                                plugin.config.getInt(
                                                                        "announcement.max_lines",
                                                                        5
                                                                )
                                                        val maxLength =
                                                                plugin.config.getInt(
                                                                        "announcement.max_line_length",
                                                                        100
                                                                )
                                                        val blockedStrings =
                                                                plugin.config.getStringList(
                                                                        "announcement.blocked_strings"
                                                                )

                                                        blockedStrings.forEach { blocked ->
                                                                if (input.contains(
                                                                                blocked,
                                                                                ignoreCase = true
                                                                        )
                                                                ) {
                                                                        player.sendMessage(
                                                                                lang.getMessage(
                                                                                        player,
                                                                                        "messages.announcement_blocked_string",
                                                                                        mapOf(
                                                                                                "string" to
                                                                                                        blocked
                                                                                        )
                                                                                )
                                                                        )
                                                                        return@Runnable
                                                                }
                                                        }

                                                        if (input.length > maxLength) {
                                                                player.sendMessage(
                                                                        lang.getMessage(
                                                                                player,
                                                                                "messages.announcement_invalid_length",
                                                                                mapOf(
                                                                                        "max_lines" to
                                                                                                maxLines,
                                                                                        "max_length" to
                                                                                                maxLength
                                                                                )
                                                                        )
                                                                )
                                                                return@Runnable
                                                        }

                                                        // 隍・焚陦悟・蜉帙・邁｡譏灘ｮ溯｣・ｼ・陦後★縺､霑ｽ蜉縺励※縺・￥繧ｹ繧ｿ繧､繝ｫ縺ｫ縺吶ｋ縺九∽ｸ蠎ｦ縺ｫ險ｭ螳壹☆繧九°縲・
                                                        // 繝励Ο繝ｳ繝励ヨ縺ｧ "exit" 縺ｧ邨ゆｺ・→縺ゅｋ縺ｮ縺ｧ縲∬ｿｽ險伜梛縺ｫ縺吶ｋ
                                                        if (input.equals("exit", ignoreCase = true)
                                                        ) {
                                                                plugin.worldConfigRepository.save(
                                                                        worldData
                                                                )
                                                                player.sendMessage(
                                                                        lang.getMessage(
                                                                                player,
                                                                                "messages.announcement_set"
                                                                        )
                                                                )
                                                                plugin.settingsSessionManager
                                                                        .endSession(player)
                                                                plugin.worldSettingsGui.open(
                                                                        player,
                                                                        worldData
                                                                )
                                                                return@Runnable
                                                        }

                                                        if (worldData.announcementMessages.size >=
                                                                        maxLines
                                                        ) {
                                                                player.sendMessage(
                                                                        lang.getMessage(
                                                                                player,
                                                                                "messages.announcement_invalid_length",
                                                                                mapOf(
                                                                                        "max_lines" to
                                                                                                maxLines,
                                                                                        "max_length" to
                                                                                                maxLength
                                                                                )
                                                                        )
                                                                )
                                                                // 繝｡繝・そ繝ｼ繧ｸ縺後＞縺｣縺ｱ縺・〒縺吶‘xit縺励※螳御ｺ・＠縺ｦ縺上□縺輔＞逧・↑繝｡繝・そ繝ｼ繧ｸ縺瑚憶縺・′縲・
                                                                // 縺ｨ繧翫≠縺医★辟｡險縺ｧ霑ｽ蜉縺励↑縺・°縲√お繝ｩ繝ｼ繧貞・縺・
                                                                return@Runnable
                                                        }

                                                        // 色コード変換 (初期色を白にする)
                                                        val formatted =
                                                                "§f" + input.replace("&", "§")
                                                        worldData.announcementMessages.add(
                                                                formatted
                                                        )
                                                        player.sendMessage(
                                                                lang.getMessage(
                                                                        player,
                                                                        "messages.announcement_preview",
                                                                        mapOf(
                                                                                "message" to
                                                                                        formatted
                                                                        )
                                                                )
                                                        )
                                                        // 繧ｻ繝・す繝ｧ繝ｳ縺ｯ邯ｭ謖・ｼ・xit縺輔ｌ繧九∪縺ｧ・・
                                                        return@Runnable
                                                }
                                                SettingsAction.EXPAND_DIRECTION_CONFIRM -> {
                                                        // Handled by onCommandPreprocess
                                                        return@Runnable
                                                }
                                                else -> {}
                                        }
                                        if (worldData != null) {
                                                plugin.worldSettingsGui.open(player, worldData)
                                        }
                                }
                        )
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

                // 繝√Ε繝・ヨ蜈･蜉帛ｾ・ｩ滉ｸｭ繧・ヶ繝ｭ繝・け蜈･蜉帛ｾ・ｩ滉ｸｭ縺ｮ繧｢繧ｯ繧ｷ繝ｧ繝ｳ縺ｯ縲√う繝ｳ繝吶Φ繝医Μ繧帝哩縺倥※繧ゅそ繝・す繝ｧ繝ｳ繧堤ｶｭ謖√☆繧・
                val chatInputActions =
                        setOf(
                                SettingsAction.RENAME_WORLD,
                                SettingsAction.CHANGE_DESCRIPTION,
                                SettingsAction.MEMBER_INVITE,
                                SettingsAction.SET_ANNOUNCEMENT,
                                SettingsAction.ADMIN_PLAYER_FILTER
                        )
                val blockInputActions =
                        setOf(
                                SettingsAction.SET_SPAWN_GUEST,
                                SettingsAction.SET_SPAWN_MEMBER,
                                SettingsAction.EXPAND_DIRECTION_WAIT,
                                SettingsAction.EXPAND_DIRECTION_CONFIRM
                        )

                if (session.action in chatInputActions || session.action in blockInputActions) {
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
                clearBorderPreview(event.player)
                processImmediateExpansion(event.player)
        }

        @EventHandler
        fun onQuit(event: PlayerQuitEvent) {
                clearBorderPreview(event.player)
                processImmediateExpansion(event.player)
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
                val player = event.player
                val settingsSession = plugin.settingsSessionManager.getSession(player) ?: return
                val currentAction = settingsSession.action

                if (currentAction == SettingsAction.EXPAND_DIRECTION_WAIT) {
                        if (event.action == Action.LEFT_CLICK_AIR ||
                                        event.action == Action.LEFT_CLICK_BLOCK
                        ) {
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

                        var yaw = player.location.yaw
                        while (yaw < 0) yaw += 360
                        while (yaw >= 360) yaw -= 360

                        val normalizedYaw =
                                when {
                                        yaw >= 45 && yaw < 135 -> 90.0f
                                        yaw >= 135 && yaw < 225 -> 180.0f
                                        yaw >= 225 && yaw < 315 -> 270.0f
                                        else -> 0.0f
                                }

                        loc.yaw = normalizedYaw
                        loc.pitch = 0.0f

                        val worldData =
                                plugin.worldConfigRepository.findByUuid(settingsSession.worldUuid)
                        if (worldData != null) {
                                if (currentAction == SettingsAction.SET_SPAWN_GUEST) {
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

        private fun handleWeatherConfirm(player: Player, worldData: WorldData) {
                val session = plugin.settingsSessionManager.getSession(player) ?: return
                val nextWeather = session.tempWeather ?: return

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
                        plugin.soundManager.playActionSound(
                                player,
                                "environment",
                                "insufficient_points"
                        )
                        return
                }

                stats.worldPoint -= cost
                worldData.fixedWeather = if (nextWeather == "DEFAULT") null else nextWeather
                worldData.cumulativePoints += cost
                session.tempWeather = null // Reset temp

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

                val session = plugin.settingsSessionManager.getSession(player) ?: return
                val lang = plugin.languageManager

                if (args[0] == "expand_confirm") {
                        if (session.action == SettingsAction.EXPAND_DIRECTION_CONFIRM) {
                                event.isCancelled = true
                                val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid) ?: return
                                val direction = session.expansionDirection
                                val cost = session.getMetadata("expand_cost") as? Int ?: 0
                                plugin.worldSettingsGui.openExpansionConfirmation(
                                        player,
                                        worldData.uuid,
                                        direction,
                                        cost
                                )
                        }
                } else if (args[0] == "expand_retry") {
                        if (session.action == SettingsAction.EXPAND_DIRECTION_CONFIRM) {
                                event.isCancelled = true
                                clearBorderPreview(player)
                                session.action = SettingsAction.EXPAND_DIRECTION_WAIT
                                player.sendMessage(lang.getMessage(player, "messages.expand_direction_prompt"))
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
                
                val session = plugin.settingsSessionManager.getSession(player) ?: return
                val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid) ?: return
                
                worldData.name = newName
                plugin.worldConfigRepository.save(worldData)
                player.sendMessage(
                    plugin.languageManager.getMessage(
                        player,
                        "messages.world_name_change"
                    )
                )
                plugin.worldSettingsGui.open(player, worldData)
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
                
                worldData.description = newDesc
                plugin.worldConfigRepository.save(worldData)
                 player.sendMessage(
                    plugin.languageManager.getMessage(
                        player,
                        "messages.world_desc_changed" // Assuming this key exists, or use generic
                    )
                )
                plugin.worldSettingsGui.open(player, worldData)
                return
            }
            
            // Cancel
            if (identifier == Key.key("mwm:settings/cancel")) {
                val session = plugin.settingsSessionManager.getSession(player) ?: return
                val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid) ?: return
                plugin.worldSettingsGui.open(player, worldData)
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

        private fun showTagEditorDialog(player: Player, worldData: WorldData) {
             val lang = plugin.languageManager
             val currentTags = worldData.tags
             val allTags = WorldTag.values()
             
             // Build Options
             // Format: [x] TagName  or [ ] TagName
             val options = allTags.map { tag ->
                 val isSelected = currentTags.contains(tag)
                 val checkMark = if (isSelected) "[✔]" else "[ ]"
                 val textColor = if (isSelected) NamedTextColor.GREEN else NamedTextColor.GRAY
                 
                 val tagName = lang.getMessage(player, "world_tag.${tag.name.lowercase()}")
                 val displayText = Component.text("$checkMark $tagName", textColor)
                 
                 SingleOptionDialogInput.OptionEntry.create("tag:${tag.name}", displayText, false)
             }
             
             // Add Done/Back options
             val navOptions = mutableListOf<SingleOptionDialogInput.OptionEntry>()
             navOptions.addAll(options)
             navOptions.add(SingleOptionDialogInput.OptionEntry.create("action:close", Component.text("完了 / 戻る", NamedTextColor.YELLOW), false))

             val dialog = Dialog.create { builder ->
                builder.empty()
                    .base(DialogBase.builder(Component.text("タグ設定", NamedTextColor.YELLOW))
                        .body(listOf(
                            DialogBody.plainMessage(Component.text("ワールドのタグを設定します。\n項目を選択してオン/オフを切り替えてください。")),
                        ))
                        .inputs(listOf(
                            DialogInput.singleOption("tag_select", Component.text("Select Tag"), navOptions)
                                .build()
                        ))
                        .build()
                    )
                    .type(DialogType.confirmation(
                        ActionButton.create(Component.text("Select", NamedTextColor.GREEN), null, 100, DialogAction.customClick(Key.key("mwm:settings/tags/select"), null)),
                        ActionButton.create(Component.text("Close", NamedTextColor.GRAY), null, 200, DialogAction.customClick(Key.key("mwm:settings/tags/close"), null))
                    ))
            }
            player.showDialog(dialog)
        }
        
    @EventHandler
    fun onTagDialogInteraction(event: PlayerCustomClickEvent) {
        val conn = event.commonConnection as? PlayerGameConnection ?: return
        val player = conn.player
        val identifier = event.identifier
        
        if (identifier == Key.key("mwm:settings/tags/select")) {
            val view = event.getDialogResponseView() ?: return
            val inputAny = view.getText("tag_select") ?: return
            val input = inputAny.toString()
            
            val session = plugin.settingsSessionManager.getSession(player) ?: return
            val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid) ?: return
            
            if (input.startsWith("tag:")) {
                // Toggle Tag
                val tagName = input.removePrefix("tag:")
                val tag = try { WorldTag.valueOf(tagName) } catch (e: Exception) { null }
                
                if (tag != null) {
                    if (worldData.tags.contains(tag)) {
                        worldData.tags.remove(tag)
                    } else {
                        val maxTags = plugin.config.getInt("tags.max_count", 4)
                        if (worldData.tags.size >= maxTags) {
                            player.sendMessage(plugin.languageManager.getMessage(player, "messages.tag_max_reached", mapOf("limit" to maxTags)))
                        } else {
                            worldData.tags.add(tag)
                        }
                    }
                    plugin.worldConfigRepository.save(worldData)
                    
                    // Re-open dialog (Loop)
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        showTagEditorDialog(player, worldData)
                    })
                }
            } else if (input == "action:close") {
                // Return to settings
                plugin.worldSettingsGui.open(player, worldData)
            }
            return
        }
        
        if (identifier == Key.key("mwm:settings/tags/close")) {
             val session = plugin.settingsSessionManager.getSession(player) ?: return
             val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid) ?: return
             plugin.worldSettingsGui.open(player, worldData)
             return
        }
    }
}
