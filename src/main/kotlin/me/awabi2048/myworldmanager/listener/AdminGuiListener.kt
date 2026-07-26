@file:Suppress("REDUNDANT_CALL_OF_CONVERSION_METHOD")

package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.ui.ManagedMenuPresenter

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiCycle
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuDialogButton
import com.awabi2048.ccsystem.api.gui.MenuDialogHandler
import com.awabi2048.ccsystem.api.gui.MenuDialogInput
import com.awabi2048.ccsystem.api.gui.MenuDialogRequest
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.session.MenuExternalInput
import me.awabi2048.myworldmanager.session.PlayerFilterType
import me.awabi2048.myworldmanager.session.SettingsAction
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.ItemTag
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import me.awabi2048.myworldmanager.util.cancelWithDebug
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

class AdminGuiListener : Listener {

    @EventHandler(ignoreCancelled = false)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
        // Debug: リスナー呼び出し確認

        // GUI遷移中のクリックを無視
        val session = plugin.settingsSessionManager.getSession(player)
        if (session != null && session.isGuiTransition) {
            if (GuiHelper.isPluginGuiInventory(event.view.topInventory)) {
                session.isGuiTransition = false
            } else {
                session.isGuiTransition = false
                return
            }
        }

        val view = event.view
        val title = PlainTextComponentSerializer.plainText().serialize(view.title())
        val lang = plugin.languageManager

        // 管理者用ワールド管理
        if (lang.isKeyMatch(title, "gui.admin.title")) {
            event.cancelWithDebug("AdminGuiListener.onInventoryClick: admin world list GUI click")
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
                plugin.adminGuiSessionManager.cycleArchiveFilter(
                    player.uniqueId,
                    GuiCycle.direction(event.click) ?: return
                )
                plugin.worldGui.open(player)
                return
            }

            // 公開レベルフィルターボタン
            if (type == ItemTag.TYPE_GUI_ADMIN_FILTER_PUBLISH) {
                plugin.soundManager.playClickSound(player, currentItem)
                plugin.adminGuiSessionManager.cyclePublishFilter(
                    player.uniqueId,
                    GuiCycle.direction(event.click) ?: return
                )
                plugin.worldGui.open(player)
                return
            }

            // プレイヤーフィルターボタン
            if (type == ItemTag.TYPE_GUI_ADMIN_FILTER_PLAYER) {
                plugin.soundManager.playClickSound(player, currentItem)
                if (event.isLeftClick) {
                    plugin.adminGuiSessionManager.cyclePlayerFilterType(
                        player.uniqueId,
                        GuiCycle.direction(event.click) ?: return
                    )
                    plugin.worldGui.open(player)
                } else if (event.isRightClick) {
                    if (session.playerFilterType != me.awabi2048.myworldmanager.session.PlayerFilterType.NONE) {
                        plugin.settingsSessionManager.startSession(player, java.util.UUID(0, 0), me.awabi2048.myworldmanager.session.SettingsAction.ADMIN_PLAYER_FILTER)
                        plugin.settingsSessionManager.getSession(player)?.beginExternalInput(MenuExternalInput.ADMIN_PLAYER_FILTER)
                        ManagedMenuPresenter.close(player)
                        openAdminPlayerFilterInput(plugin, player)
                    }
                }
                return
            }

            // ソートボタン
            if (type == ItemTag.TYPE_GUI_ADMIN_SORT) {
                plugin.soundManager.playAdminClickSound(player)
                plugin.adminGuiSessionManager.cycleSortType(
                    player.uniqueId,
                    GuiCycle.direction(event.click) ?: return
                )
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
        CCSystem.getAPI().getMenuDialogService().show(
            player,
            MenuDialogRequest(
                owner = "myworldmanager",
                id = "admin-player-filter",
                title = Component.text(prompt, NamedTextColor.YELLOW),
                body = listOf(Component.text(prompt)),
                inputs = listOf(
                    MenuDialogInput.Text(
                        "admin_player_name",
                        Component.text(lang.getMessage(player, "gui.bedrock.input.admin_player_filter.label")),
                    ),
                ),
                confirm = MenuDialogButton(
                    Component.text(lang.getMessage(player, "gui.common.confirm"), NamedTextColor.GREEN),
                    MenuDialogHandler { target, response ->
                        applyAdminPlayerFilter(plugin, target, response.textValue("admin_player_name"))
                        MenuActionResult.Success(MenuUpdate.Close)
                    },
                ),
                cancel = MenuDialogButton(
                    Component.text(lang.getMessage(player, "gui.common.cancel"), NamedTextColor.RED),
                    MenuDialogHandler { target, _ ->
                        plugin.settingsSessionManager.endSession(target)
                        target.sendMessage(
                            plugin.languageManager.getMessage(target, "messages.operation_cancelled"),
                        )
                        plugin.worldGui.open(target)
                        MenuActionResult.Success(MenuUpdate.Close)
                    },
                ),
            ),
        )
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
            ManagedMenuPresenter.close(player)
            player.sendMessage(lang.getMessage(player, "messages.world_loading"))
            plugin.worldService.teleportToWorld(player, worldData.uuid, runMacro = false) {
                player.sendMessage(lang.getMessage(player, "messages.admin_warp_success", mapOf("world" to worldData.name)))
            }
            return
        }

        ManagedMenuPresenter.close(player)
        plugin.worldService.teleportToWorld(player, worldData.uuid, runMacro = false) {
            player.sendMessage(lang.getMessage(player, "messages.admin_warp_success", mapOf("world" to worldData.name)))
        }
    }

    private fun openWorldSettingsFromAdmin(plugin: MyWorldManager, player: Player, worldData: me.awabi2048.myworldmanager.model.WorldData) {
        val lang = plugin.languageManager
        val folderName = worldData.customWorldName ?: "my_world.${worldData.uuid}"
        if (!worldData.isArchived && Bukkit.getWorld(folderName) == null) {
            ManagedMenuPresenter.close(player)
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
