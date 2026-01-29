package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.util.ItemTag
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

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val view = event.view
        val title = PlainTextComponentSerializer.plainText().serialize(view.title())
        val player = event.whoClicked as? Player ?: return
        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
        val lang = plugin.languageManager
        
        // ポータル管理GUIの判定
        if (lang.isKeyMatch(title, "gui.admin_portals.title")) {
            event.isCancelled = true
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
                        if (portal.worldUuid != null) {
                            plugin.portalManager.addIgnorePlayer(player)
                            plugin.portalManager.addPortalGrace(player, portalUuid, 15)
                            plugin.worldService.teleportToWorld(player, portal.worldUuid!!, portal.getCenterLocation(), runMacro = false)
                        } else if (portal.targetWorldName != null) {
                            val targetWorld = Bukkit.getWorld(portal.targetWorldName!!)
                            if (targetWorld != null) {
                                plugin.portalManager.addIgnorePlayer(player)
                                plugin.portalManager.addPortalGrace(player, portalUuid, 15)
                                player.teleport(targetWorld.spawnLocation)
                            } else {
                                player.sendMessage(lang.getMessage(player, "general.world_not_found"))
                                return
                            }
                        }
                        player.sendMessage(lang.getMessage(player, "messages.admin_portal_teleport"))
                        player.closeInventory()
                    } else if (event.isRightClick) {
                        // 撤去
                        plugin.portalRepository.removePortal(portalUuid)
                        plugin.portalManager.removePortalVisuals(portalUuid)
                        
                        // ブロックを空気に（もしエンドポータルフレームなら）
                        val world = Bukkit.getWorld(portal.worldName)
                        val block = world?.getBlockAt(portal.x, portal.y, portal.z)
                        if (block != null && block.type == org.bukkit.Material.END_PORTAL_FRAME) {
                            block.type = org.bukkit.Material.AIR
                        }
                        
                        plugin.soundManager.playAdminClickSound(player)
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
            val currentItem = event.currentItem ?: return
            if (currentItem.type == Material.AIR) return

            val type = ItemTag.getType(currentItem)
            val session = plugin.adminGuiSessionManager.getSession(player.uniqueId)
            
            // ページナビゲーション
            if (type == ItemTag.TYPE_GUI_NAV_NEXT || type == ItemTag.TYPE_GUI_NAV_PREV) {
                val targetPage = ItemTag.getTargetPage(currentItem) ?: return
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
                    // タイプを切り替え
                    plugin.adminGuiSessionManager.cyclePlayerFilterType(player.uniqueId)
                    plugin.worldGui.open(player)
                } else if (event.isRightClick) {
                    // タイプが「なし」以外の場合のみプレイヤー名の入力を開始
                    if (session.playerFilterType != me.awabi2048.myworldmanager.session.PlayerFilterType.NONE) {
                        player.closeInventory()
                        player.sendMessage(lang.getMessage(player, "messages.admin_player_filter_prompt"))
                        plugin.settingsSessionManager.startSession(player, java.util.UUID(0, 0), me.awabi2048.myworldmanager.session.SettingsAction.ADMIN_PLAYER_FILTER)
                        // 専用のセッション状態を設定（チャット入力待ち）
                        session.playerFilter = null // リセット
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
            
            if (type == ItemTag.TYPE_GUI_DECORATION || type == ItemTag.TYPE_GUI_INFO) return

            // ワールドアイコンの処理
            val uuid = ItemTag.getWorldUuid(currentItem) ?: return
            val worldData = plugin.worldConfigRepository.findByUuid(uuid) ?: return
            val currentPage = session.currentPage

            if (event.isLeftClick) {
                if (worldData.isArchived) {
                    player.sendMessage(lang.getMessage(player, "messages.admin_warp_archived_error"))
                    return
                }
                plugin.soundManager.playClickSound(player, currentItem)
                plugin.worldService.teleportToWorld(player, uuid, runMacro = false)
                player.sendMessage(lang.getMessage(player, "messages.admin_warp_success", worldData.name))
                plugin.worldService.sendAnnouncementMessage(player, worldData)
                player.closeInventory()
            } else if (event.isRightClick) {
                if (worldData.isArchived) {
                    plugin.soundManager.playClickSound(player, currentItem)
                    player.closeInventory()
                    player.sendMessage(lang.getMessage(player, "messages.unarchive_start"))
                    plugin.worldService.unarchiveWorld(uuid).thenAccept { success ->
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            if (success) {
                                player.sendMessage(lang.getMessage(player, "messages.unarchive_success"))
                                plugin.worldGui.open(player, currentPage) // 同じページに戻る
                            } else {
                                player.sendMessage(lang.getMessage(player, "messages.unarchive_failed"))
                            }
                        })
                    }
                } else {
                    plugin.soundManager.playClickSound(player, currentItem)
                    player.closeInventory()
                    player.sendMessage(lang.getMessage(player, "messages.archive_start"))
                    plugin.worldService.archiveWorld(uuid).thenAccept { success ->
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            if (success) {
                                player.sendMessage(lang.getMessage(player, "messages.archive_success", worldData.name))
                                plugin.worldGui.open(player, currentPage) // 同じページに戻る
                            } else {
                                player.sendMessage(lang.getMessage(player, "messages.archive_failed"))
                            }
                        })
                    }
                }
            } else if (event.click == org.bukkit.event.inventory.ClickType.MIDDLE) {
                // ホイールクリック：UUIDコピーメッセージを送信（クリエイティブモードのみ）
                if (player.gameMode == org.bukkit.GameMode.CREATIVE) {
                    val bar = net.kyori.adventure.text.Component.text("§8§m－－－－－－－－－－－－－－－－－－")
                    val header = net.kyori.adventure.text.Component.text(lang.getMessage(player, "messages.internal_data_extracted", worldData.name))
                    
                    val worldUuidText = net.kyori.adventure.text.Component.text(lang.getMessage(player, "messages.copy_world_uuid"))
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(net.kyori.adventure.text.Component.text(lang.getMessage(player, "messages.copy_world_uuid_hover"))))
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.copyToClipboard(worldData.uuid.toString()))
                    
                    val ownerUuidText = net.kyori.adventure.text.Component.text(lang.getMessage(player, "messages.copy_owner_uuid"))
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(net.kyori.adventure.text.Component.text(lang.getMessage(player, "messages.copy_owner_uuid_hover"))))
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.copyToClipboard(worldData.owner.toString()))

                    player.sendMessage(bar)
                    player.sendMessage(header)
                    player.sendMessage(net.kyori.adventure.text.Component.empty())
                    player.sendMessage(worldUuidText)
                    player.sendMessage(ownerUuidText)
                    player.sendMessage(bar)
                    
                    plugin.soundManager.playCopySound(player)
                }
            }
            return
        }
    }
}
