package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.gui.DialogConfirmManager
import me.awabi2048.myworldmanager.gui.PlayerWorldGui
import io.papermc.paper.connection.PlayerGameConnection
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.event.player.PlayerCustomClickEvent
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.model.TourNavigationMode
import me.awabi2048.myworldmanager.session.SettingsAction
import me.awabi2048.myworldmanager.util.InviteTargetResolver
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

class PlayerWorldListener(private val plugin: MyWorldManager) : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        
        // GUI遷移中のクリックを無視
        val session = plugin.settingsSessionManager.getSession(player)
        if (session != null && session.isGuiTransition) {
            // player.sendMessage("§7[Debug] Click cancelled (GuiTransition: true)")
            event.isCancelled = true
            return
        }

        val view = event.view
        val title = PlainTextComponentSerializer.plainText().serialize(view.title())
        val lang = plugin.languageManager

        if (plugin.pendingInteractionGui.handleInventoryClick(player, event)) {
            return
        }

        // プレイヤー用ワールド一覧
        if (view.topInventory.holder is me.awabi2048.myworldmanager.gui.PlayerWorldGui.PlayerWorldGuiHolder) {
            event.isCancelled = true
            val currentItem = event.currentItem ?: return
            val type = ItemTag.getType(currentItem)
            

            
            val gui = PlayerWorldGui(plugin)

            if (type == ItemTag.TYPE_GUI_NAV_NEXT || type == ItemTag.TYPE_GUI_NAV_PREV) {
                val targetPage = ItemTag.getTargetPage(currentItem) ?: return
                plugin.soundManager.playClickSound(player, currentItem, "player_world")
                gui.open(player, targetPage)
                return
            }
            
            if (type == ItemTag.TYPE_GUI_RETURN) {
                me.awabi2048.myworldmanager.util.GuiHelper.handleReturnClick(plugin, player, currentItem)
                return
            }
            if (type == ItemTag.TYPE_GUI_USER_SETTING_CRITICAL_VISIBILITY) {
                plugin.soundManager.playClickSound(player, currentItem, "player_world")
                val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                stats.criticalSettingsEnabled = !stats.criticalSettingsEnabled
                plugin.playerStatsRepository.save(stats)
                gui.open(player)
                return
            }
            
            if (type == ItemTag.TYPE_GUI_INVITE) {
                plugin.soundManager.playClickSound(player, currentItem, "player_world")
                val currentWorld = player.world
                val worldData = plugin.worldConfigRepository.findByWorldName(currentWorld.name)
                
                if (worldData == null) {
                    player.sendMessage(lang.getMessage(player, "messages.no_in_myworld"))
                    player.closeInventory()
                    return
                }
                
                if (worldData.publishLevel == PublishLevel.LOCKED) {
                    player.sendMessage(lang.getMessage(player, "error.invite_locked_error"))
                    player.closeInventory()
                    return
                }

                player.closeInventory()

                plugin.inviteGui.open(player, showBackButton = true)
                return
            }

            if (type == ItemTag.TYPE_GUI_CREATION_BUTTON) {
                plugin.soundManager.playClickSound(player, currentItem, "player_world")
                
                // セッションの開始
                val session = plugin.creationSessionManager.startSession(player.uniqueId)
                
                // JE は常にダイアログ、BE は FormUI を使用
                session.isDialogMode = !plugin.playerPlatformResolver.isBedrock(player)

                player.closeInventory()
                player.sendMessage(lang.getMessage(player, "messages.wizard_start"))
                plugin.creationGui.openTypeSelection(player)
                return
            }

            if (type == ItemTag.TYPE_GUI_PLAYER_STATS) {
                val pendingCount = plugin.pendingDecisionManager.getPersistentPendingCount(player.uniqueId)
                if (pendingCount == 0) {
                    return
                }
                plugin.soundManager.playClickSound(player, currentItem, "player_world")
                val currentPage = plugin.playerWorldSessionManager.getSession(player.uniqueId).currentPage
                val showBack = plugin.playerWorldSessionManager.getSession(player.uniqueId).showBackButton
                plugin.pendingInteractionGui.open(
                    player = player,
                    page = 0,
                    returnPage = currentPage,
                    showBackButton = showBack,
                    fromBedrockMenu = false
                )
                return
            }
            if (type == ItemTag.TYPE_GUI_USER_SETTINGS_BUTTON) {
                plugin.soundManager.playClickSound(player, currentItem, "player_world")
                plugin.userSettingsGui.open(player, showBackButton = true)
                return
            }
            val uuid = ItemTag.getWorldUuid(currentItem) ?: return
            val worldData = plugin.worldConfigRepository.findByUuid(uuid) ?: return
            val isBedrock = plugin.playerPlatformResolver.isBedrock(player)

            if (!isBedrock && event.isShiftClick && event.isLeftClick) {
                // Shift+左クリック：ワールドを先頭に移動
                val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                stats.worldDisplayOrder.remove(uuid)
                stats.worldDisplayOrder.add(0, uuid)
                plugin.playerStatsRepository.save(stats)
                
                player.sendMessage("§a「${worldData.name}」を一番上に移動しました。")
                plugin.soundManager.playClickSound(player, currentItem, "player_world")
                
                // 現在のページ番号を取得してGUI再表示
                val session = plugin.playerWorldSessionManager.getSession(player.uniqueId)
                plugin.playerWorldGui.open(player, session.currentPage)
                return
            } else if (event.isLeftClick && !event.isShiftClick) {
                // Shiftなし左クリック：アーカイブ状態のチェック
                if (worldData.isArchived) {
                    if (worldData.owner == player.uniqueId) {
                        val title = LegacyComponentSerializer.legacySection().deserialize(
                            lang.getMessage(player, "gui.unarchive_confirm.title")
                        )
                        val bodyLines = lang.getMessageList(player, "gui.unarchive_confirm.lore")
                            .map { LegacyComponentSerializer.legacySection().deserialize(it) }

                        plugin.settingsSessionManager.updateSessionAction(
                            player,
                            worldData.uuid,
                            SettingsAction.UNARCHIVE_CONFIRM,
                            isGui = true
                        )
                        DialogConfirmManager.showConfirmationByPreference(
                            player,
                            plugin,
                            title,
                            bodyLines,
                            "mwm:confirm/unarchive_world",
                            "mwm:confirm/cancel",
                            lang.getMessage(player, "gui.common.confirm"),
                            lang.getMessage(player, "gui.common.cancel")
                        ) {
                            plugin.worldSettingsGui.openUnarchiveConfirmation(player, worldData)
                        }
                    } else {
                        player.sendMessage(lang.getMessage(player, "messages.archive_access_denied"))
                    }
                    return
                }

                // 通常の左クリック：ワープ処理
                val folderName = worldData.customWorldName ?: "my_world.$uuid"
                if (Bukkit.getWorld(folderName) == null) {
                    player.closeInventory()
                    player.sendMessage(lang.getMessage(player, "messages.world_loading"))
                    plugin.worldService.teleportToWorld(player, uuid) {
                        player.sendMessage(lang.getMessage(player, "messages.warp_success", mapOf("world" to worldData.name)))
                        plugin.soundManager.playClickSound(player, currentItem, "player_world")
                        player.closeInventory()
                    }
                    return
                }

                player.closeInventory()
                plugin.worldService.teleportToWorld(player, uuid) {
                    player.sendMessage(lang.getMessage(player, "messages.warp_success", mapOf("world" to worldData.name)))
                    plugin.soundManager.playClickSound(player, currentItem, "player_world")
                    player.closeInventory()
                }
            } else if (!isBedrock && event.isRightClick) {

                // 右クリック：設定を開く
                val isMember = worldData.owner == player.uniqueId ||
                        worldData.moderators.contains(player.uniqueId) ||
                        worldData.members.contains(player.uniqueId)

                if (isMember) {
                    val currentShowBack = plugin.playerWorldSessionManager.getSession(player.uniqueId).showBackButton
                    plugin.worldSettingsGui.open(player, worldData, showBackButton = true, isPlayerWorldFlow = true, parentShowBackButton = currentShowBack)
                }
            }
            return
        }

        // 個人設定
        if (view.topInventory.holder is me.awabi2048.myworldmanager.gui.UserSettingsGui.UserSettingsGuiHolder) {
            event.isCancelled = true
            val currentItem = event.currentItem ?: return
            val type = ItemTag.getType(currentItem) ?: return
            val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
            
            when (type) {
                ItemTag.TYPE_GUI_USER_SETTING_NOTIFICATION -> {
                    plugin.soundManager.playClickSound(player, currentItem)
                    stats.visitorNotificationEnabled = !stats.visitorNotificationEnabled
                    plugin.playerStatsRepository.save(stats)
                    plugin.userSettingsGui.open(player)
                }
                ItemTag.TYPE_GUI_USER_SETTING_LANGUAGE -> {
                    plugin.soundManager.playClickSound(player, currentItem)
                    plugin.userSettingsGui.open(player)
                }
                ItemTag.TYPE_GUI_USER_SETTING_CRITICAL_VISIBILITY -> {
                    plugin.soundManager.playClickSound(player, currentItem)
                    stats.criticalSettingsEnabled = !stats.criticalSettingsEnabled
                    plugin.playerStatsRepository.save(stats)
                    plugin.userSettingsGui.open(player)
                }
                ItemTag.TYPE_GUI_USER_SETTING_TOUR_NAVIGATION -> {
                    plugin.soundManager.playClickSound(player, currentItem)
                    stats.tourNavigationMode = when (stats.tourNavigationMode) {
                        TourNavigationMode.BOSSBAR_ONLY -> TourNavigationMode.ALL
                        TourNavigationMode.ALL -> TourNavigationMode.NONE
                        TourNavigationMode.NONE -> TourNavigationMode.BOSSBAR_ONLY
                    }
                    plugin.playerStatsRepository.save(stats)
                    plugin.tourManager.refreshNavigation(player)
                    plugin.userSettingsGui.open(player)
                }

                ItemTag.TYPE_GUI_RETURN -> {
                    plugin.soundManager.playClickSound(player, currentItem, "player_world")
                    val currentPage = plugin.playerWorldSessionManager.getSession(player.uniqueId).currentPage
                    plugin.playerWorldGui.open(player, currentPage)
                }
            }
            return
        }
    }

    @EventHandler
    fun onInviteDialogResponse(event: PlayerCustomClickEvent) {
        val identifier = event.identifier
        val conn = event.commonConnection as? PlayerGameConnection ?: return
        val player = conn.player

        if (plugin.pendingInteractionGui.handleDialogResponse(player, identifier)) {
            return
        }

        if (identifier != Key.key("mwm:invite/input_submit") && identifier != Key.key("mwm:invite/input_cancel")) {
            return
        }

        if (identifier == Key.key("mwm:invite/input_cancel")) {
            plugin.inviteSessionManager.endSession(player.uniqueId)
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.operation_cancelled"))
            plugin.playerWorldGui.open(player)
            return
        }

        val view = event.getDialogResponseView() ?: return
        val input = view.getText("invite_player")?.toString().orEmpty()
        processInviteInput(player, input)
    }

    private fun openBedrockInviteInputForm(player: Player): Boolean {
        if (!plugin.floodgateFormBridge.isAvailable(player)) {
            return false
        }

        val lang = plugin.languageManager
        return plugin.floodgateFormBridge.sendCustomInputForm(
            player = player,
            title = lang.getMessage(player, "gui.bedrock.input.member_invite.title"),
            label = lang.getMessage(player, "gui.bedrock.input.member_invite.label"),
            placeholder = lang.getMessage(player, "gui.bedrock.input.member_invite.placeholder"),
            defaultValue = "",
            onSubmit = { value ->
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    processInviteInput(player, value)
                })
            },
            onClosed = {
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    plugin.inviteSessionManager.endSession(player.uniqueId)
                    if (player.isOnline) {
                        plugin.playerWorldGui.open(player)
                    }
                })
            }
        )
    }

    private fun showInviteInputDialog(player: Player) {
        val lang = plugin.languageManager
        val dialog = Dialog.create { builder ->
            builder.empty()
                .base(
                    DialogBase.builder(Component.text(lang.getMessage(player, "gui.member_management.invite.name"), NamedTextColor.YELLOW))
                        .body(
                            listOf(
                                DialogBody.plainMessage(Component.text(lang.getMessage(player, "messages.member_invite_input")))
                            )
                        )
                        .inputs(
                            listOf(
                                DialogInput.text("invite_player", Component.text(lang.getMessage(player, "gui.bedrock.input.member_invite.label")))
                                    .maxLength(16)
                                    .build()
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
                            DialogAction.customClick(Key.key("mwm:invite/input_submit"), null)
                        ),
                        ActionButton.create(
                            Component.text(lang.getMessage(player, "gui.common.cancel"), NamedTextColor.RED),
                            null,
                            200,
                            DialogAction.customClick(Key.key("mwm:invite/input_cancel"), null)
                        )
                    )
                )
        }
        player.showDialog(dialog)
    }

    private fun processInviteInput(player: Player, rawInput: String) {
        val session = plugin.inviteSessionManager.getSession(player.uniqueId) ?: return
        val targetName = rawInput.trim()
        val lang = plugin.languageManager
        val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid)

        if (worldData == null) {
            plugin.inviteSessionManager.endSession(player.uniqueId)
            player.sendMessage(lang.getMessage(player, "general.world_not_found"))
            return
        }

        val target = PlayerNameUtil.resolveOnlinePlayer(plugin, targetName)
        if (target == null) {
            plugin.inviteSessionManager.endSession(player.uniqueId)
            player.sendMessage(lang.getMessage(player, "messages.invite_target_offline", mapOf("player" to targetName)))
            plugin.playerWorldGui.open(player)
            return
        }

        when (val reason = InviteTargetResolver.getRejectionReason(plugin, player, worldData, target)) {
            null -> Unit
            else -> {
                plugin.inviteSessionManager.endSession(player.uniqueId)
                val messageKey = InviteTargetResolver.getRejectionMessageKey(reason) ?: return
                player.sendMessage(lang.getMessage(player, messageKey, mapOf("player" to target.name)))
                plugin.playerWorldGui.open(player)
                return
            }
        }

        plugin.inviteSessionManager.endSession(player.uniqueId)
        player.performCommand("invite $targetName")
    }
}
