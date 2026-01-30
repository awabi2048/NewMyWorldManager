package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.gui.PlayerWorldGui
import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
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

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        
        // GUI遷移中のクリックを無視
        val session = plugin.settingsSessionManager.getSession(player)
        if (session != null && session.isGuiTransition) {
            event.isCancelled = true
            return
        }

        val view = event.view
        val title = PlainTextComponentSerializer.plainText().serialize(view.title())
        val lang = plugin.languageManager

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
                
                val worldUuid = worldData.uuid
                
                if (worldUuid != null) {
                    if (worldData.publishLevel == PublishLevel.LOCKED) {
                        player.sendMessage(lang.getMessage(player, "messages.invite_locked_error"))
                        player.closeInventory()
                        return
                    }
                    
                    plugin.inviteSessionManager.startSession(player.uniqueId, worldUuid)
                    player.closeInventory()
                    val cancelWord = plugin.config.getString("creation.cancel_word", "cancel") ?: "cancel"
                    val cancelInfo = lang.getMessage(player, "messages.chat_input_cancel_hint", mapOf("word" to cancelWord))
                    player.sendMessage(lang.getMessage(player, "messages.member_invite_input") + " " + cancelInfo)
                }
                return
            }

            if (type == ItemTag.TYPE_GUI_CREATION_BUTTON) {
                plugin.soundManager.playClickSound(player, currentItem, "player_world")
                
                // セッションの開始
                plugin.creationSessionManager.startSession(player.uniqueId)
                player.closeInventory()
                player.sendMessage(lang.getMessage(player, "messages.wizard_start"))
                
                val cancelWord = plugin.config.getString("creation.cancel_word", "cancel") ?: "cancel"
                val cancelInfo = lang.getMessage(player, "messages.chat_input_cancel_hint", mapOf("word" to cancelWord))
                player.sendMessage(lang.getMessage(player, "messages.wizard_name_prompt") + " " + cancelInfo)
                return
            }
            val uuid = ItemTag.getWorldUuid(currentItem) ?: return
            val worldData = plugin.worldConfigRepository.findByUuid(uuid) ?: return

            if (event.isLeftClick) {
                // 通常の左クリック：ワープ処理
                val folderName = worldData.customWorldName ?: "my_world.$uuid"
                if (Bukkit.getWorld(folderName) == null) {
                    player.sendMessage(lang.getMessage(player, "messages.world_loading"))
                    if (!plugin.worldService.loadWorld(uuid)) {
                        player.sendMessage(lang.getMessage(player, "messages.load_failed"))
                        return
                    }
                }

                val targetWorld = Bukkit.getWorld(folderName)
                if (targetWorld != null || worldData.isArchived) {
                    if (worldData.isArchived) {
                        if (worldData.owner == player.uniqueId) {
                            plugin.worldSettingsGui.openUnarchiveConfirmation(player, worldData)
                        } else {
                            player.sendMessage(lang.getMessage(player, "messages.archive_access_denied"))
                        }
                        return
                    }

                    if (targetWorld == null) {
                        player.sendMessage(lang.getMessage(player, "general.world_not_found"))
                        return
                    }

                    val isMember = worldData.owner == player.uniqueId ||
                            worldData.moderators.contains(player.uniqueId) ||
                            worldData.members.contains(player.uniqueId)

                    val spawnLocation = if (isMember) {
                        worldData.spawnPosMember ?: targetWorld.spawnLocation
                    } else {
                        worldData.spawnPosGuest ?: targetWorld.spawnLocation
                    }

                    plugin.worldService.teleportToWorld(player, uuid, spawnLocation)
                    player.sendMessage(lang.getMessage(player, "messages.warp_success", mapOf("world" to worldData.name)))
                    plugin.worldService.sendAnnouncementMessage(player, worldData)
                    plugin.soundManager.playClickSound(player, currentItem, "player_world")

                    if (!isMember) {
                        worldData.recentVisitors[0]++
                        plugin.worldConfigRepository.save(worldData)
                    }
                    player.closeInventory()
                } else {
                    player.sendMessage(lang.getMessage(player, "general.world_not_found"))
                }
            } else if (event.isRightClick) {
                // 右クリック：設定を開く
                val isMember = worldData.owner == player.uniqueId ||
                        worldData.moderators.contains(player.uniqueId) ||
                        worldData.members.contains(player.uniqueId)

                if (isMember) {
                    plugin.worldSettingsGui.open(player, worldData)
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
                    val supported = lang.getSupportedLanguages()
                    val currentIndex = supported.indexOf(stats.language)
                    val nextIndex = (currentIndex + 1) % supported.size
                    stats.language = supported[nextIndex]
                    plugin.playerStatsRepository.save(stats)
                    plugin.userSettingsGui.open(player)
                }
                ItemTag.TYPE_GUI_USER_SETTING_CRITICAL_VISIBILITY -> {
                    plugin.soundManager.playClickSound(player, currentItem)
                    stats.criticalSettingsEnabled = !stats.criticalSettingsEnabled
                    plugin.playerStatsRepository.save(stats)
                    plugin.userSettingsGui.open(player)
                }

                ItemTag.TYPE_GUI_RETURN -> {
                    plugin.soundManager.playClickSound(player, currentItem, "player_world")
                    plugin.playerWorldGui.open(player)
                }
            }
            return
        }
    }
}
