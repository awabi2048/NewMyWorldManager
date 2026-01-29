package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.gui.CreationGui
import me.awabi2048.myworldmanager.model.*
import me.awabi2048.myworldmanager.repository.*
import me.awabi2048.myworldmanager.session.*
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import java.util.UUID

class CreationGuiListener(private val plugin: MyWorldManager) : Listener {

    
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val view = event.view
        val title = PlainTextComponentSerializer.plainText().serialize(view.title())
        
        val lang = plugin.languageManager
        
        // 作成GUIのタイトルかどうかを判定（多言語対応）
        val isCreationGui = lang.isKeyMatch(title, "gui.creation.title_type") ||
                            lang.isKeyMatch(title, "gui.creation.title_template") ||
                            lang.isKeyMatch(title, "gui.creation.title_confirm")
        
        if (!isCreationGui) return
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        val currentItem = event.currentItem ?: return
        val tag = ItemTag.getType(currentItem)
        if (currentItem.type == Material.AIR || tag == ItemTag.TYPE_GUI_DECORATION) return

        val session = plugin.creationSessionManager.getSession(player.uniqueId) ?: return

        if (tag == ItemTag.TYPE_GUI_BACK) {
            plugin.soundManager.playClickSound(player, currentItem)
            when (session.phase) {
                WorldCreationPhase.TYPE_SELECT -> {
                    player.closeInventory()
                    session.phase = WorldCreationPhase.NAME_INPUT
                    val cancelWord = plugin.config.getString("creation.cancel_word", "__cancel__") ?: "__cancel__"
                    val cancelInfo = lang.getMessage(player, "messages.wizard_cancel_word", mapOf("word" to cancelWord))
                    player.sendMessage(lang.getMessage(player, "messages.wizard_name_prompt") + "\n" + cancelInfo)
                }
                WorldCreationPhase.TEMPLATE_SELECT -> {
                    session.phase = WorldCreationPhase.TYPE_SELECT
                    plugin.creationGui.openTypeSelection(player)
                }
                WorldCreationPhase.CONFIRM -> {
                    if (session.creationType == WorldCreationType.TEMPLATE) {
                        session.phase = WorldCreationPhase.TEMPLATE_SELECT
                        plugin.creationGui.openTemplateSelection(player)
                    } else {
                        session.phase = WorldCreationPhase.TYPE_SELECT
                        plugin.creationGui.openTypeSelection(player)
                    }
                }
                else -> {}
            }
            return
        }

        when (session.phase) {
            WorldCreationPhase.TYPE_SELECT -> {
                val config = plugin.config
                val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                
                // 上限チェック
                val totalMax = config.getInt("creation.max_total_world_count", 50)
                val totalCurrent = plugin.worldConfigRepository.findAll().size
                if (totalCurrent >= totalMax) {
                    player.sendMessage(lang.getMessage(player, "gui.creation.type.limit_reached_total", mapOf("max" to totalMax)))
                    plugin.soundManager.playActionSound(player, "creation", "limit_reached")
                    return
                }

                val defaultMax = config.getInt("creation.max_create_count_default", 3)
                val maxCounts = defaultMax + stats.unlockedWorldSlot
                val currentCounts = plugin.worldConfigRepository.findAll().count { it.owner == player.uniqueId }

                if (currentCounts >= maxCounts) {
                    player.sendMessage(lang.getMessage(player, "gui.creation.type.limit_reached", mapOf("current" to currentCounts, "max" to maxCounts)))
                    plugin.soundManager.playActionSound(player, "creation", "limit_reached")
                    return
                }

                // コスト判定
                val cost = when (tag) {
                    ItemTag.TYPE_GUI_CREATION_TYPE_TEMPLATE -> config.getInt("creation_cost.template", 0)
                    ItemTag.TYPE_GUI_CREATION_TYPE_SEED -> config.getInt("creation_cost.seed", 100)
                    ItemTag.TYPE_GUI_CREATION_TYPE_RANDOM -> config.getInt("creation_cost.random", 50)
                    else -> 0
                }
                
                if (stats.worldPoint < cost) {
                    player.sendMessage(lang.getMessage(player, "messages.insufficient_points"))
                    plugin.soundManager.playActionSound(player, "creation", "insufficient_points")
                    return
                }

                when (tag) {
                    ItemTag.TYPE_GUI_CREATION_TYPE_TEMPLATE -> {
                        plugin.soundManager.playClickSound(player, currentItem)
                        session.creationType = WorldCreationType.TEMPLATE
                        session.phase = WorldCreationPhase.TEMPLATE_SELECT
                        plugin.creationGui.openTemplateSelection(player)
                    }
                    ItemTag.TYPE_GUI_CREATION_TYPE_SEED -> {
                        plugin.soundManager.playClickSound(player, currentItem)
                        session.creationType = WorldCreationType.SEED
                        session.phase = WorldCreationPhase.SEED_INPUT
                        player.closeInventory()
                        val cancelWord = plugin.config.getString("creation.cancel_word", "__cancel__") ?: "__cancel__"
                        val cancelInfo = lang.getMessage(player, "messages.wizard_cancel_word", mapOf("word" to cancelWord))
                        player.sendMessage(lang.getMessage(player, "messages.creation_seed_prompt") + " " + cancelInfo)
                    }
                    ItemTag.TYPE_GUI_CREATION_TYPE_RANDOM -> {
                        plugin.soundManager.playClickSound(player, currentItem)
                        session.creationType = WorldCreationType.RANDOM
                        session.phase = WorldCreationPhase.CONFIRM
                        plugin.creationGui.openConfirmation(player, session)
                    }
                    else -> {}
                }
            }
            WorldCreationPhase.TEMPLATE_SELECT -> {
                if (tag != ItemTag.TYPE_GUI_CREATION_TEMPLATE_ITEM) return
                val displayName = PlainTextComponentSerializer.plainText().serialize(currentItem.itemMeta.displayName()!!)
                val template = plugin.templateRepository.findAll().find { it.name == displayName }
                
                if (template != null) {
                    // 右クリックでプレビュー
                    if (event.isRightClick) {
                        player.closeInventory()
                        plugin.previewSessionManager.startPreview(player, template.path)
                        return
                    }
                    
                    // 左クリックで選択確定
                    plugin.soundManager.playClickSound(player, currentItem)
                    session.templateName = template.path
                    session.phase = WorldCreationPhase.CONFIRM
                    plugin.creationGui.openConfirmation(player, session)
                }
            }
            WorldCreationPhase.CONFIRM -> {
                if (tag == ItemTag.TYPE_GUI_CONFIRM) {
                    player.closeInventory()
                    
                    // ポイント消費
                    val config = plugin.config
                    val cost = when(session.creationType) {
                        WorldCreationType.TEMPLATE -> config.getInt("creation_cost.template", 0)
                        WorldCreationType.SEED -> config.getInt("creation_cost.seed", 100)
                        WorldCreationType.RANDOM -> config.getInt("creation_cost.random", 50)
                        else -> 0
                    }
                    val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                    
                    if (stats.worldPoint < cost) {
                         player.sendMessage(lang.getMessage(player, "messages.insufficient_points"))
                         plugin.creationSessionManager.endSession(player.uniqueId)
                         return
                    }
                    
                    stats.worldPoint -= cost
                    plugin.playerStatsRepository.save(stats)
                    if (cost > 0) {
                        val remainingInfo = lang.getMessage(player, "messages.env_cost_paid_remaining", mapOf("remaining" to stats.worldPoint))
                        player.sendMessage(lang.getMessage(player, "messages.env_cost_paid", mapOf("cost" to cost, "remaining_info" to remainingInfo)))
                    }

                    player.sendMessage(lang.getMessage(player, "messages.creation_start"))
                    
                    when(session.creationType) {
                        WorldCreationType.TEMPLATE -> {
                            plugin.worldService.createWorld(session.templateName!!, player.uniqueId, session.worldName!!, cost)
                                .thenAccept { success ->
                                    if (success) player.sendMessage(lang.getMessage(player, "messages.creation_success")) 
                                    else player.sendMessage(lang.getMessage(player, "messages.creation_failed"))
                                }
                        }
                        WorldCreationType.SEED -> {
                            plugin.worldService.generateWorld(player.uniqueId, session.worldName!!, session.seed, cost)
                                .thenAccept { success ->
                                    if (success) player.sendMessage(lang.getMessage(player, "messages.creation_success")) 
                                    else player.sendMessage(lang.getMessage(player, "messages.creation_failed"))
                                }
                        }
                        WorldCreationType.RANDOM -> {
                            plugin.worldService.generateWorld(player.uniqueId, session.worldName!!, null, cost)
                                .thenAccept { success ->
                                    if (success) player.sendMessage(lang.getMessage(player, "messages.creation_success")) 
                                    else player.sendMessage(lang.getMessage(player, "messages.creation_failed"))
                                }
                        }
                        null -> {}
                    }
                    plugin.soundManager.playClickSound(player, currentItem)
                    plugin.creationSessionManager.endSession(player.uniqueId)
                } else if (tag == ItemTag.TYPE_GUI_CANCEL) {
                    plugin.soundManager.playActionSound(player, "creation", "cancel")
                    player.closeInventory()
                    player.sendMessage(lang.getMessage(player, "messages.creation_cancelled"))
                    plugin.creationSessionManager.endSession(player.uniqueId)
                }
            }
            else -> {}
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val view = event.view
        val title = PlainTextComponentSerializer.plainText().serialize(view.title())

        val lang = plugin.languageManager
        
        // 作成GUIのタイトルかどうかを判定（多言語対応）
        val isCreationGui = lang.isKeyMatch(title, "gui.creation.title_type") ||
                            lang.isKeyMatch(title, "gui.creation.title_template") ||
                            lang.isKeyMatch(title, "gui.creation.title_confirm")
        
        if (!isCreationGui) return

        val player = event.player as? Player ?: return
        
        val session = plugin.creationSessionManager.getSession(player.uniqueId) ?: return
        
        if (session.phase == WorldCreationPhase.SEED_INPUT || session.phase == WorldCreationPhase.NAME_INPUT) {
            return
        }

        // 遅延を2tickに増やしてGUI遷移の時間を確保
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (!player.isOnline) return@Runnable
            
            // プレビュー中はセッションをキャンセルしない
            if (plugin.previewSessionManager.isInPreview(player)) {
                return@Runnable
            }
            
            val currentTitle = PlainTextComponentSerializer.plainText().serialize(player.openInventory.title())
            val isStillInCreationGui = lang.isKeyMatch(currentTitle, "gui.creation.title_type") ||
                                       lang.isKeyMatch(currentTitle, "gui.creation.title_template") ||
                                       lang.isKeyMatch(currentTitle, "gui.creation.title_confirm")
            if (isStillInCreationGui) {
                return@Runnable
            }
            
            val currentSession = plugin.creationSessionManager.getSession(player.uniqueId)
            if (currentSession != null && currentSession.phase != WorldCreationPhase.SEED_INPUT && currentSession.phase != WorldCreationPhase.NAME_INPUT) {
                plugin.creationSessionManager.endSession(player.uniqueId)
                player.sendMessage(lang.getMessage(player, "messages.creation_cancelled"))
            }
        }, 2L)
    }
}
