package me.awabi2048.myworldmanager.listener

import io.papermc.paper.connection.PlayerGameConnection
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.event.player.PlayerCustomClickEvent
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.session.PlayerFilterType
import me.awabi2048.myworldmanager.session.SettingsAction
import me.awabi2048.myworldmanager.util.ItemTag
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

class AdminGuiListener : Listener {

    @EventHandler(ignoreCancelled = false)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
        // Debug: リスナー呼び出し確認
        // player.sendMessage("§d[Debug-System] AdminGuiListener called. Cancelled: ${event.isCancelled}, Title: ${event.view.title}")
        
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
        
        // ポータル管理GUIの判定
        if (lang.isKeyMatch(title, "gui.admin_portals.title")) {
            event.isCancelled = true
            if (event.clickedInventory != view.topInventory) return
            val currentItem = event.currentItem ?: return
            val type = ItemTag.getType(currentItem) ?: return

            when (type) {
                ItemTag.TYPE_GUI_RETURN -> {
                    plugin.soundManager.playAdminClickSound(player)
                    plugin.adminCommandGui.open(player)
                }
                ItemTag.TYPE_GUI_NAV_PREV, ItemTag.TYPE_GUI_NAV_NEXT -> {
                    val page = ItemTag.getTargetPage(currentItem) ?: 0
                    plugin.soundManager.playAdminClickSound(player)
                    plugin.adminPortalGui.open(player, page)
                }
                ItemTag.TYPE_GUI_ADMIN_PORTAL_SORT -> {
                    plugin.soundManager.playAdminClickSound(player)
                    plugin.adminGuiSessionManager.cyclePortalSortType(player.uniqueId)
                    plugin.adminPortalGui.open(player)
                }
                ItemTag.TYPE_PORTAL -> {
                    val portalUuid = ItemTag.getPortalUuid(currentItem) ?: return
                    val portal = plugin.portalRepository.findAll().find { it.id == portalUuid } ?: return

                    if (event.isLeftClick) {
                        // テレポート
                        plugin.soundManager.playClickSound(player, currentItem)
                        player.closeInventory()
                        if (portal.worldUuid != null) {
                            plugin.portalManager.addIgnorePlayer(player)
                            plugin.portalManager.addPortalGrace(player, portalUuid, 15)
                            val destData = plugin.worldConfigRepository.findByUuid(portal.worldUuid!!)
                            if (destData != null && Bukkit.getWorld(plugin.worldService.getWorldFolderName(destData)) == null) {
                                player.sendMessage(lang.getMessage(player, "messages.world_loading"))
                            }
                            plugin.worldService.teleportToWorld(player, portal.worldUuid!!, portal.getCenterLocation(), runMacro = false) {
                                player.sendMessage(lang.getMessage(player, "messages.admin_portal_teleport"))
                            }
                        } else if (portal.targetWorldName != null) {
                            plugin.portalManager.addIgnorePlayer(player)
                            plugin.portalManager.addPortalGrace(player, portalUuid, 15)
                            val teleported = plugin.portalManager.teleportPlayerToWorldSpawn(
                                player,
                                portal.targetWorldName!!
                            ) {
                                player.sendMessage(lang.getMessage(player, "messages.admin_portal_teleport"))
                            }
                            if (!teleported) {
                                player.sendMessage(lang.getMessage(player, "general.world_not_found"))
                                return
                            }
                        }
                    } else if (event.isRightClick) {
                        // 撤去
                        val refundResult = if (portal.isGate()) plugin.portalManager.refundPointsForRemovedGate(portal) else null
                        plugin.portalManager.removePortalVisuals(portalUuid)
                        plugin.portalRepository.removePortal(portalUuid)
                        
                        if (!portal.isGate()) {
                            val world = Bukkit.getWorld(portal.worldName)
                            val block = world?.getBlockAt(portal.x, portal.y, portal.z)
                            if (block != null && block.type == org.bukkit.Material.END_PORTAL_FRAME) {
                                block.type = org.bukkit.Material.AIR
                            }
                        }
                        
                        plugin.soundManager.playAdminClickSound(player)
                        if (portal.isGate()) {
                            val ownerName = Bukkit.getOfflinePlayer(portal.ownerUuid).name ?: portal.ownerUuid.toString()
                            player.sendMessage(
                                lang.getMessage(
                                    player,
                                    "messages.world_gate_removed_refund",
                                    mapOf(
                                        "points" to (refundResult?.points ?: 0),
                                        "percent" to (refundResult?.percent ?: 0),
                                        "owner" to ownerName
                                    )
                                )
                            )
                        }
                        player.sendMessage(lang.getMessage(player, "messages.admin_portal_removed"))
                        plugin.adminPortalGui.open(player)
                    }
                }
            }
            return
        }

        // 管理者用ワールド管理
        if (lang.isKeyMatch(title, "gui.admin.title")) {
            event.isCancelled = true
            if (event.clickedInventory != view.topInventory) return
            val currentItem = event.currentItem ?: return
            if (currentItem.type == Material.AIR) return

            val type = ItemTag.getType(currentItem)
            val session = plugin.adminGuiSessionManager.getSession(player.uniqueId)

            // ページナビゲーション
            if (type == ItemTag.TYPE_GUI_NAV_NEXT || type == ItemTag.TYPE_GUI_NAV_PREV) {
                val direction = if (type == ItemTag.TYPE_GUI_NAV_NEXT) 1 else -1
                val step = if (event.isShiftClick) 5 else 1
                val targetPage = (session.currentPage + (direction * step)).coerceAtLeast(0)
                plugin.soundManager.playClickSound(player, currentItem)
                plugin.worldGui.open(player, targetPage)
                return
            }
            
            // アーカイブフィルターボタン
            if (type == ItemTag.TYPE_GUI_ADMIN_FILTER_ARCHIVE) {
                plugin.soundManager.playClickSound(player, currentItem)
                plugin.adminGuiSessionManager.cycleArchiveFilter(player.uniqueId)
                plugin.worldGui.open(player)
                return
            }
            
            // 公開レベルフィルターボタン
            if (type == ItemTag.TYPE_GUI_ADMIN_FILTER_PUBLISH) {
                plugin.soundManager.playClickSound(player, currentItem)
                plugin.adminGuiSessionManager.cyclePublishFilter(player.uniqueId, event.isRightClick)
                plugin.worldGui.open(player)
                return
            }
            
            // プレイヤーフィルターボタン
            if (type == ItemTag.TYPE_GUI_ADMIN_FILTER_PLAYER) {
                plugin.soundManager.playClickSound(player, currentItem)
                if (event.isLeftClick) {
                    plugin.adminGuiSessionManager.cyclePlayerFilterType(player.uniqueId)
                    plugin.worldGui.open(player)
                } else if (event.isRightClick) {
                    if (session.playerFilterType != me.awabi2048.myworldmanager.session.PlayerFilterType.NONE) {
                        plugin.settingsSessionManager.startSession(player, java.util.UUID(0, 0), me.awabi2048.myworldmanager.session.SettingsAction.ADMIN_PLAYER_FILTER)
                        player.closeInventory()
                        openAdminPlayerFilterInput(plugin, player)
                    }
                }
                return
            }
            
            // ソートボタン
            if (type == ItemTag.TYPE_GUI_ADMIN_SORT) {
                plugin.soundManager.playAdminClickSound(player)
                plugin.adminGuiSessionManager.cycleSortType(player.uniqueId, event.isRightClick)
                plugin.worldGui.open(player)
                return
            }

            if (type == ItemTag.TYPE_GUI_ADMIN_CURRENT_WORLD_INFO) {
                val uuid = ItemTag.getWorldUuid(currentItem) ?: return
                val worldData = plugin.worldConfigRepository.findByUuid(uuid) ?: return

                if (event.click == org.bukkit.event.inventory.ClickType.MIDDLE) {
                    sendWorldDirectoryCopyMessage(player, worldData)
                    return
                }

                if (!event.isRightClick) {
                    return
                }

                plugin.soundManager.playClickSound(player, currentItem)
                if (event.isRightClick && event.isShiftClick) {
                    if (worldData.isArchived) {
                        plugin.adminCommandGui.openUnarchiveWorldConfirmation(player, worldData.name, uuid)
                    } else {
                        plugin.adminCommandGui.openArchiveWorldConfirmation(player, worldData.name, uuid)
                    }
                    return
                }

                plugin.settingsSessionManager.updateSessionAction(
                    player,
                    uuid,
                    SettingsAction.VIEW_SETTINGS,
                    isGui = true,
                    isAdminFlow = true
                )
                openWorldSettingsFromAdmin(plugin, player, worldData)
                return
            }
            
            if (type == ItemTag.TYPE_GUI_DECORATION || type == ItemTag.TYPE_GUI_INFO) return

            // ワールドアイコンの処理
            val uuid = ItemTag.getWorldUuid(currentItem) ?: return
            val worldData = plugin.worldConfigRepository.findByUuid(uuid) ?: return

            if (event.isLeftClick) {
                if (worldData.isArchived) {
                    player.sendMessage(lang.getMessage(player, "messages.admin_warp_archived_error"))
                    return
                }
                plugin.soundManager.playClickSound(player, currentItem)
                warpFromAdminList(plugin, player, worldData)
            } else if (event.isRightClick) {
                plugin.soundManager.playClickSound(player, currentItem)
                if (event.isShiftClick) {
                    // Shift + 右クリック: アーカイブ操作
                    if (worldData.isArchived) {
                        plugin.adminCommandGui.openUnarchiveWorldConfirmation(player, worldData.name, uuid)
                    } else {
                        plugin.adminCommandGui.openArchiveWorldConfirmation(player, worldData.name, uuid)
                    }
                } else {
                    // 右クリック: ワールド設定メニューを開く
                    plugin.settingsSessionManager.updateSessionAction(player, uuid, SettingsAction.VIEW_SETTINGS, isGui = true, isAdminFlow = true)
                    openWorldSettingsFromAdmin(plugin, player, worldData)
                }
            } else if (event.click == org.bukkit.event.inventory.ClickType.MIDDLE) {
                // ホイールクリック：UUIDコピーメッセージを送信（クリエイティブモードのみ）
                if (player.gameMode == org.bukkit.GameMode.CREATIVE) {
                    sendWorldDirectoryCopyMessage(player, worldData)
                }
            }
            return
        }
    }

    @EventHandler
    fun onAdminFilterDialog(event: PlayerCustomClickEvent) {
        val identifier = event.identifier
        if (
            identifier != Key.key("mwm:admin/player_filter_submit") &&
                identifier != Key.key("mwm:admin/player_filter_cancel")
        ) {
            return
        }

        val conn = event.commonConnection as? PlayerGameConnection ?: return
        val player = conn.player
        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)

        if (identifier == Key.key("mwm:admin/player_filter_cancel")) {
            plugin.settingsSessionManager.endSession(player)
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.operation_cancelled"))
            plugin.worldGui.open(player)
            return
        }

        val view = event.getDialogResponseView() ?: return
        val input = view.getText("admin_player_name")?.toString().orEmpty()
        applyAdminPlayerFilter(plugin, player, input)
    }

    private fun openAdminPlayerFilterInput(plugin: MyWorldManager, player: Player) {
        val lang = plugin.languageManager

        if (plugin.playerPlatformResolver.isBedrock(player)) {
            if (!plugin.floodgateFormBridge.isAvailable(player)) {
                plugin.floodgateFormBridge.notifyFallbackCancelled(player)
                plugin.settingsSessionManager.endSession(player)
                plugin.worldGui.open(player)
                return
            }

            val opened =
                plugin.floodgateFormBridge.sendCustomInputForm(
                    player = player,
                    title = lang.getMessage(player, "gui.bedrock.input.admin_player_filter.title"),
                    label = lang.getMessage(player, "gui.bedrock.input.admin_player_filter.label"),
                    placeholder =
                        lang.getMessage(player, "gui.bedrock.input.admin_player_filter.placeholder"),
                    defaultValue = "",
                    onSubmit = { value ->
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            applyAdminPlayerFilter(plugin, player, value)
                        })
                    },
                    onClosed = {
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            plugin.settingsSessionManager.endSession(player)
                            if (player.isOnline) {
                                plugin.worldGui.open(player)
                            }
                        })
                    }
                )
            if (!opened) {
                plugin.floodgateFormBridge.notifyFallbackCancelled(player)
                plugin.settingsSessionManager.endSession(player)
                plugin.worldGui.open(player)
            }
            return
        }

        val prompt = lang.getMessage(player, "messages.admin_player_filter_prompt")
        val dialog = Dialog.create { builder ->
            builder.empty()
                .base(
                    DialogBase.builder(Component.text(prompt, NamedTextColor.YELLOW))
                        .body(listOf(DialogBody.plainMessage(Component.text(prompt))))
                        .inputs(
                            listOf(
                                DialogInput.text(
                                    "admin_player_name",
                                    Component.text(lang.getMessage(player, "gui.bedrock.input.admin_player_filter.label"))
                                ).build()
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
                            DialogAction.customClick(Key.key("mwm:admin/player_filter_submit"), null)
                        ),
                        ActionButton.create(
                            Component.text(lang.getMessage(player, "gui.common.cancel"), NamedTextColor.RED),
                            null,
                            200,
                            DialogAction.customClick(Key.key("mwm:admin/player_filter_cancel"), null)
                        )
                    )
                )
        }
        player.showDialog(dialog)
    }

    private fun applyAdminPlayerFilter(plugin: MyWorldManager, player: Player, targetNameRaw: String) {
        val targetName = targetNameRaw.trim()
        val offlinePlayer = PlayerNameUtil.resolveOfflinePlayer(plugin, targetName)
        if (offlinePlayer == null) {
            player.sendMessage(plugin.languageManager.getMessage(player, "general.player_not_found"))
            plugin.settingsSessionManager.endSession(player)
            plugin.worldGui.open(player)
            return
        }

        val adminSession = plugin.adminGuiSessionManager.getSession(player.uniqueId)
        adminSession.playerFilter = offlinePlayer.uniqueId
        if (adminSession.playerFilterType == PlayerFilterType.NONE) {
            adminSession.playerFilterType = PlayerFilterType.OWNER
        }

        player.sendMessage(
            plugin.languageManager.getMessage(
                player,
                "messages.admin_player_filter_set",
                mapOf("player" to (offlinePlayer.name ?: targetName))
            )
        )
        plugin.settingsSessionManager.endSession(player)
        plugin.worldGui.open(player)
    }

    private fun sendWorldDirectoryCopyMessage(player: Player, worldData: me.awabi2048.myworldmanager.model.WorldData) {
        if (player.gameMode != org.bukkit.GameMode.CREATIVE) {
            return
        }

        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
        val lang = plugin.languageManager
        val worldDirectory = worldData.customWorldName ?: "my_world.${worldData.uuid}"
        val bar = net.kyori.adventure.text.Component.text("§8§m－－－－－－－－－－－－－－－－－－")
        val header = net.kyori.adventure.text.Component.text(
            lang.getMessage(player, "messages.internal_data_extracted", mapOf("world" to worldData.name))
        )

        val worldDirectoryText = net.kyori.adventure.text.Component.text(lang.getMessage(player, "messages.copy_world_uuid"))
            .hoverEvent(
                net.kyori.adventure.text.event.HoverEvent.showText(
                    net.kyori.adventure.text.Component.text(lang.getMessage(player, "messages.copy_world_uuid_hover"))
                )
            )
            .clickEvent(net.kyori.adventure.text.event.ClickEvent.copyToClipboard(worldDirectory))

        val ownerUuidText = net.kyori.adventure.text.Component.text(lang.getMessage(player, "messages.copy_owner_uuid"))
            .hoverEvent(
                net.kyori.adventure.text.event.HoverEvent.showText(
                    net.kyori.adventure.text.Component.text(lang.getMessage(player, "messages.copy_owner_uuid_hover"))
                )
            )
            .clickEvent(net.kyori.adventure.text.event.ClickEvent.copyToClipboard(worldData.owner.toString()))

        player.sendMessage(bar)
        player.sendMessage(header)
        player.sendMessage(net.kyori.adventure.text.Component.empty())
        player.sendMessage(worldDirectoryText)
        player.sendMessage(ownerUuidText)
        player.sendMessage(bar)

        plugin.soundManager.playCopySound(player)
    }

    private fun warpFromAdminList(plugin: MyWorldManager, player: Player, worldData: me.awabi2048.myworldmanager.model.WorldData) {
        val lang = plugin.languageManager
        val folderName = worldData.customWorldName ?: "my_world.${worldData.uuid}"
        if (Bukkit.getWorld(folderName) == null) {
            player.closeInventory()
            player.sendMessage(lang.getMessage(player, "messages.world_loading"))
            plugin.worldService.teleportToWorld(player, worldData.uuid, runMacro = false) {
                player.sendMessage(lang.getMessage(player, "messages.admin_warp_success", mapOf("world" to worldData.name)))
            }
            return
        }

        player.closeInventory()
        plugin.worldService.teleportToWorld(player, worldData.uuid, runMacro = false) {
            player.sendMessage(lang.getMessage(player, "messages.admin_warp_success", mapOf("world" to worldData.name)))
        }
    }

    private fun openWorldSettingsFromAdmin(plugin: MyWorldManager, player: Player, worldData: me.awabi2048.myworldmanager.model.WorldData) {
        val lang = plugin.languageManager
        val folderName = worldData.customWorldName ?: "my_world.${worldData.uuid}"
        if (!worldData.isArchived && Bukkit.getWorld(folderName) == null) {
            player.closeInventory()
            player.sendMessage(lang.getMessage(player, "messages.world_loading"))
            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (!player.isOnline) {
                    return@Runnable
                }
                if (!plugin.worldService.loadWorld(worldData.uuid)) {
                    player.sendMessage(lang.getMessage(player, "error.load_failed"))
                    return@Runnable
                }
                plugin.worldSettingsGui.open(player, worldData, showBackButton = true)
            })
            return
        }

        plugin.worldSettingsGui.open(player, worldData, showBackButton = true)
    }
}
