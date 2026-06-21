package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.api.extension.MenuExtensionContext
import me.awabi2048.myworldmanager.gui.CreationGui
import me.awabi2048.myworldmanager.model.*
import me.awabi2048.myworldmanager.repository.*
import me.awabi2048.myworldmanager.session.*
import me.awabi2048.myworldmanager.util.ItemTag
import me.awabi2048.myworldmanager.util.WorldRuntimePolicies
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import me.awabi2048.myworldmanager.util.cancelWithDebug
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.ItemStack

class CreationGuiListener(private val plugin: MyWorldManager) : Listener {

    @EventHandler(ignoreCancelled = false)
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.view.topInventory.holder !is CreationGui.CreationGuiHolder) return

        // 作成GUIのタイトルかどうかを判定（多言語対応）
        event.cancelWithDebug("CreationGuiListener.onInventoryClick: creation GUI click")
        if (event.clickedInventory != event.view.topInventory) return

        val player = event.whoClicked as? Player ?: return
        val currentItem = event.currentItem ?: return
        val tag = ItemTag.getType(currentItem)
        if (currentItem.type == Material.AIR || tag == ItemTag.TYPE_GUI_DECORATION) return

        // GUI遷移中のクリックを無視
        val lang = plugin.languageManager
            // player.sendMessage("§7[Debug] Click cancelled (GuiTransition: true)")
        val session = plugin.creationSessionManager.getSession(player.uniqueId) ?: return

        if (tag == ItemTag.TYPE_GUI_EXTENSION) {
            val extensionId = ItemTag.getExtensionId(currentItem) ?: return
            val extension =
                    MyWorldManagerApi.getMenuExtensions().firstOrNull { it.getId() == extensionId }
                            ?: return
            val context =
                    MenuExtensionContext(
                            "creation_confirm",
                            mutableMapOf(
                                    "session" to session,
                                    "worldName" to (session.worldName ?: ""),
                                    "creationType" to session.creationType
                            )
                    )
            if (extension.onClick(event, player, context)) {
                plugin.soundManager.playClickSound(player, currentItem)
            }
            return
        }

        if (tag == ItemTag.TYPE_GUI_BACK) {
            plugin.soundManager.playClickSound(player, currentItem)
            when (session.phase) {
                WorldCreationPhase.TYPE_SELECT -> {
                    player.closeInventory()
                    cancelAndReturnToMyWorld(player)
                }
                WorldCreationPhase.TEMPLATE_SELECT -> {
                    session.phase = WorldCreationPhase.TYPE_SELECT
                    plugin.creationGui.openTypeSelection(player)
                }
                WorldCreationPhase.CONFIRM -> {
                    session.phase = WorldCreationPhase.NAME_INPUT
                    player.closeInventory()
                    openNameInputByPlatform(player, session)
                }
                else -> {}
            }
            return
        }

        when (session.phase) {
            WorldCreationPhase.TYPE_SELECT -> {
                val config = plugin.config
                val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)

                // 上限チェック (WorldCommandへ移動済み)

                // コスト判定
                val cost =
                        when (tag) {
                            ItemTag.TYPE_GUI_CREATION_TYPE_TEMPLATE ->
                                    WorldRuntimePolicies.creationCost(config, WorldCreationType.TEMPLATE)
                            ItemTag.TYPE_GUI_CREATION_TYPE_SEED ->
                                    WorldRuntimePolicies.creationCost(config, WorldCreationType.SEED)
                            ItemTag.TYPE_GUI_CREATION_TYPE_RANDOM ->
                                    WorldRuntimePolicies.creationCost(config, WorldCreationType.RANDOM)
                            else -> 0
                        }

                if (stats.worldPoint < cost) {
                    player.sendMessage(
                            lang.getMessage(player, "messages.creation_insufficient_points")
                    )
                    plugin.soundManager.playActionSound(player, "creation", "insufficient_points")
                    return
                }

                when (tag) {
                    ItemTag.TYPE_GUI_CREATION_TYPE_TEMPLATE -> {
                        plugin.soundManager.playClickSound(player, currentItem)
                        if (plugin.templateRepository.findAll().isEmpty()) {
                            player.sendMessage(lang.getMessage(player, "error.preview_template_not_found"))
                            plugin.soundManager.playClickSound(player, ItemStack(Material.BARRIER))
                            return
                        }
                        session.creationType = WorldCreationType.TEMPLATE
                        session.phase = WorldCreationPhase.TEMPLATE_SELECT
                        plugin.creationGui.openTemplateSelection(player)
                    }
                    ItemTag.TYPE_GUI_CREATION_TYPE_SEED -> {
                        plugin.soundManager.playClickSound(player, currentItem)
                        session.creationType = WorldCreationType.SEED
                        session.phase = WorldCreationPhase.SEED_INPUT
                        
                        if (session.isDialogMode) {
                            player.closeInventory()
                            me.awabi2048.myworldmanager.gui.CreationDialogManager.showSeedInputDialog(player, session)
                            return
                        }

                        player.closeInventory()
                        openSeedInputByPlatform(player, session)
                    }
                    ItemTag.TYPE_GUI_CREATION_TYPE_RANDOM -> {
                        plugin.soundManager.playClickSound(player, currentItem)
                        session.creationType = WorldCreationType.RANDOM
                        
                        if (session.isDialogMode) {
                            session.phase = WorldCreationPhase.NAME_INPUT
                            player.closeInventory()
                            me.awabi2048.myworldmanager.gui.CreationDialogManager.showNameInputDialog(player, session)
                            return
                        }
                        
                        session.phase = WorldCreationPhase.NAME_INPUT
                        player.closeInventory()
                        openNameInputByPlatform(player, session)
                    }
                    else -> {}
                }
            }
            WorldCreationPhase.TEMPLATE_SELECT -> {
                if (tag != ItemTag.TYPE_GUI_CREATION_TEMPLATE_ITEM) return
                val displayName =
                        PlainTextComponentSerializer.plainText()
                                .serialize(currentItem.itemMeta.displayName()!!)
                val template = plugin.templateRepository.findAll().find { it.name == displayName }

                if (template != null) {
                    // 右クリックでプレビュー
                    if (event.isRightClick) {
                        player.closeInventory()
                        val target = PreviewSessionManager.PreviewTarget.Template(template.path)
                        plugin.previewSessionManager.startPreview(
                                player,
                                target,
                                me.awabi2048.myworldmanager.session.PreviewSource.TEMPLATE_SELECTION
                        )
                        return
                    }

                    // 左クリックで選択確定
                    plugin.soundManager.playClickSound(player, currentItem)
                    session.templateName = template.path
                    
                    if (session.isDialogMode) {
                        session.phase = WorldCreationPhase.NAME_INPUT
                        player.closeInventory()
                        me.awabi2048.myworldmanager.gui.CreationDialogManager.showNameInputDialog(player, session)
                        return
                    }
                    
                    session.phase = WorldCreationPhase.NAME_INPUT
                    player.closeInventory()
                    openNameInputByPlatform(player, session)
                }
            }
            WorldCreationPhase.CONFIRM -> {
                if (tag == ItemTag.TYPE_GUI_CREATION_SPAWN_LOCATION &&
                    session.creationType == WorldCreationType.SEED
                ) {
                    plugin.soundManager.playClickSound(player, currentItem)
                    session.phase = WorldCreationPhase.SPAWN_INPUT
                    player.closeInventory()
                    openSpawnInputByPlatform(player, session)
                } else if (tag == ItemTag.TYPE_GUI_CONFIRM) {
                    player.closeInventory()

                    // ポイント消費
                    val cost =
                            session.creationType?.let {
                                WorldRuntimePolicies.creationCost(plugin.config, it)
                            } ?: 0
                    val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)

                    if (stats.worldPoint < cost) {
                        player.sendMessage(
                                lang.getMessage(player, "messages.creation_insufficient_points")
                        )
                        plugin.creationSessionManager.endSession(player.uniqueId)
                        return
                    }

                    stats.worldPoint -= cost
                    plugin.playerStatsRepository.save(stats)
                    if (cost > 0) {
                        player.sendMessage(
                                "§e§6🛖 §e${cost} §eを消費しました。(残り: §6🛖 §e${stats.worldPoint}§e)"
                        )
                    }

                    player.sendMessage("§aワールドを作成しています...")

                    when (session.creationType) {
                        WorldCreationType.TEMPLATE -> {
                            plugin.worldService.createWorld(
                                            session.templateName!!,
                                            player.uniqueId,
                                            session.worldName!!,
                                            cost
                                    )
                                    .thenAccept { success: Boolean ->
                                        if (success) player.sendMessage("§aワールド作成完了！")
                                        else player.sendMessage("§c作成に失敗しました。")
                                    }
                        }
                        WorldCreationType.SEED -> {
                            plugin.worldService.generateWorld(
                                            player.uniqueId,
                                            session.worldName!!,
                                            session.inputSeedString,
                                            cost,
                                            session.spawnCoordinates
                                    )
                                    .thenAccept { success: Boolean ->
                                        if (success) player.sendMessage("§aワールド作成完了！")
                                        else player.sendMessage("§c作成に失敗しました。")
                                    }
                        }
                        WorldCreationType.RANDOM -> {
                            plugin.worldService.generateWorld(
                                            player.uniqueId,
                                            session.worldName!!,
                                            null,
                                            cost
                                    )
                                    .thenAccept { success: Boolean ->
                                        if (success) player.sendMessage("§aワールド作成完了！")
                                        else player.sendMessage("§c作成に失敗しました。")
                                    }
                        }
                        null -> {}
                    }
                    plugin.soundManager.playClickSound(player, currentItem)
                    plugin.creationSessionManager.endSession(player.uniqueId)
                } else if (tag == ItemTag.TYPE_GUI_CANCEL) {
                    plugin.soundManager.playActionSound(player, "creation", "cancel")
                    player.closeInventory()
                    cancelAndReturnToMyWorld(player)
                }
            }
            else -> {}
        }
    }

    private fun openSeedInputByPlatform(
        player: Player,
        session: WorldCreationSession,
        errorMessage: String? = null
    ) {
        if (!plugin.playerPlatformResolver.isBedrock(player)) {
            me.awabi2048.myworldmanager.gui.CreationDialogManager.showSeedInputDialog(player, session, errorMessage)
            return
        }

        val lang = plugin.languageManager
        if (!plugin.floodgateFormBridge.isAvailable(player)) {
            plugin.creationSessionManager.endSession(player.uniqueId)
            plugin.floodgateFormBridge.notifyFallbackCancelled(player)
            return
        }

        val opened = plugin.floodgateFormBridge.sendCustomInputForm(
            player = player,
            title = lang.getMessage(player, "gui.bedrock.input.creation_seed.title"),
            label = buildString {
                append(lang.getMessage(player, "gui.bedrock.input.creation_seed.label"))
                if (!errorMessage.isNullOrBlank()) append("\n$errorMessage")
            },
            placeholder = lang.getMessage(player, "gui.bedrock.input.creation_seed.placeholder"),
            defaultValue = session.inputSeedString ?: "",
            onSubmit = { value ->
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    val latest = plugin.creationSessionManager.getSession(player.uniqueId) ?: return@Runnable
                    if (value.isBlank()) {
                        openSeedInputByPlatform(
                            player,
                            latest,
                            lang.getMessage(player, "gui.creation.dialog.seed_required")
                        )
                        return@Runnable
                    }
                    latest.inputSeedString = value
                    latest.phase = WorldCreationPhase.NAME_INPUT
                    openNameInputByPlatform(player, latest)
                })
            },
            onClosed = {
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (plugin.creationSessionManager.getSession(player.uniqueId) != null) {
                        cancelAndReturnToMyWorld(player)
                    }
                })
            }
        )

        if (!opened) {
            plugin.creationSessionManager.endSession(player.uniqueId)
            plugin.floodgateFormBridge.notifyFallbackCancelled(player)
        }
    }

    private fun openSpawnInputByPlatform(
        player: Player,
        session: WorldCreationSession,
        errorMessage: String? = null
    ) {
        if (!plugin.playerPlatformResolver.isBedrock(player)) {
            me.awabi2048.myworldmanager.gui.CreationDialogManager.showSpawnLocationInputDialog(
                player,
                session,
                errorMessage
            )
            return
        }

        val lang = plugin.languageManager
        if (!plugin.floodgateFormBridge.isAvailable(player)) {
            session.phase = WorldCreationPhase.CONFIRM
            plugin.floodgateFormBridge.notifyFallbackCancelled(player)
            plugin.creationGui.openConfirmation(player, session)
            return
        }

        val coordinates = session.spawnCoordinates
        val labels = listOf("X", "Y", "Z")
        val values = listOf(coordinates?.x, coordinates?.y, coordinates?.z)
        val opened = plugin.floodgateFormBridge.sendCustomForm(
            player = player,
            title = lang.getMessage(player, "gui.bedrock.input.creation_spawn.title"),
            inputs = labels.mapIndexed { index, axis ->
                val label = buildString {
                    append(lang.getMessage(player, "gui.bedrock.input.creation_spawn.axis", mapOf("axis" to axis)))
                    if (index == 0 && !errorMessage.isNullOrBlank()) {
                        append("\n§c")
                        append(errorMessage)
                    }
                }
                me.awabi2048.myworldmanager.ui.bedrock.FloodgateFormBridge.CustomFormInput(
                    label = label,
                    placeholder = lang.getMessage(player, "gui.bedrock.input.creation_spawn.placeholder"),
                    defaultValue = values[index]?.toString() ?: ""
                )
            },
            onSubmit = { input ->
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    val latest = plugin.creationSessionManager.getSession(player.uniqueId) ?: return@Runnable
                    when (val result = WorldSpawnCoordinates.parse(
                        input.getOrElse(0) { "" },
                        input.getOrElse(1) { "" },
                        input.getOrElse(2) { "" }
                    )) {
                        is WorldSpawnCoordinates.ParseResult.Valid -> latest.spawnCoordinates = result.coordinates
                        WorldSpawnCoordinates.ParseResult.Unset -> latest.spawnCoordinates = null
                        WorldSpawnCoordinates.ParseResult.InvalidNumber -> {
                            openSpawnInputByPlatform(
                                player,
                                latest,
                                lang.getMessage(player, "gui.creation.confirm.spawn_location.error.number")
                            )
                            return@Runnable
                        }
                        WorldSpawnCoordinates.ParseResult.OutOfRange -> {
                            openSpawnInputByPlatform(
                                player,
                                latest,
                                lang.getMessage(player, "gui.creation.confirm.spawn_location.error.range")
                            )
                            return@Runnable
                        }
                    }
                    latest.phase = WorldCreationPhase.CONFIRM
                    plugin.creationGui.openConfirmation(player, latest)
                })
            },
            onClosed = {
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    val latest = plugin.creationSessionManager.getSession(player.uniqueId) ?: return@Runnable
                    latest.phase = WorldCreationPhase.CONFIRM
                    plugin.creationGui.openConfirmation(player, latest)
                })
            }
        )

        if (!opened) {
            session.phase = WorldCreationPhase.CONFIRM
            plugin.floodgateFormBridge.notifyFallbackCancelled(player)
            plugin.creationGui.openConfirmation(player, session)
        }
    }

    private fun openNameInputByPlatform(player: Player, session: WorldCreationSession, errorMessage: String? = null) {
        if (!plugin.playerPlatformResolver.isBedrock(player)) {
            me.awabi2048.myworldmanager.gui.CreationDialogManager.showNameInputDialog(player, session, errorMessage)
            return
        }

        val lang = plugin.languageManager
        val label = buildString {
            append(lang.getMessage(player, "gui.bedrock.input.creation_name.label"))
            if (!errorMessage.isNullOrBlank()) {
                append("\n")
                append(errorMessage)
            }
        }
        if (!plugin.floodgateFormBridge.isAvailable(player)) {
            plugin.creationSessionManager.endSession(player.uniqueId)
            plugin.floodgateFormBridge.notifyFallbackCancelled(player)
            return
        }

        val opened = plugin.floodgateFormBridge.sendCustomInputForm(
            player = player,
            title = lang.getMessage(player, "gui.bedrock.input.creation_name.title"),
            label = label,
            placeholder = lang.getMessage(player, "gui.bedrock.input.creation_name.placeholder"),
            defaultValue = session.worldName ?: "",
            onSubmit = { value ->
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    val latest = plugin.creationSessionManager.getSession(player.uniqueId) ?: return@Runnable
                    val error = plugin.worldValidator.validateName(value)
                    if (error != null) {
                        if (!plugin.playerPlatformResolver.isBedrock(player)) {
                            me.awabi2048.myworldmanager.gui.CreationDialogManager.showNameInputDialog(player, latest, error)
                        } else {
                            openNameInputByPlatform(player, latest, error)
                        }
                        return@Runnable
                    }

                    latest.worldName = me.awabi2048.myworldmanager.gui.CreationDialogManager.cleanWorldName(value)
                    latest.phase = WorldCreationPhase.CONFIRM
                    plugin.creationGui.openConfirmation(player, latest)
                })
            },
            onClosed = {
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (plugin.creationSessionManager.getSession(player.uniqueId) != null) {
                        cancelAndReturnToMyWorld(player)
                    }
                })
            }
        )

        if (!opened) {
            plugin.creationSessionManager.endSession(player.uniqueId)
            plugin.floodgateFormBridge.notifyFallbackCancelled(player)
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.view.topInventory.holder !is CreationGui.CreationGuiHolder) return

        // 作成GUIのタイトルかどうかを判定（多言語対応）
        val player = event.player as? Player ?: return

        val session = plugin.creationSessionManager.getSession(player.uniqueId) ?: return

        if (session.phase == WorldCreationPhase.SEED_INPUT ||
                        session.phase == WorldCreationPhase.NAME_INPUT ||
                        session.phase == WorldCreationPhase.SPAWN_INPUT
        ) {
            return
        }

        // 遅延を2tickに増やしてGUI遷移の時間を確保
        Bukkit.getScheduler()
                .runTaskLater(
                        plugin,
                        Runnable {
                            if (!player.isOnline) return@Runnable

                            // プレビュー中はセッションをキャンセルしない
                            if (plugin.previewSessionManager.isInPreview(player)) {
                                return@Runnable
                            }

                            if (player.openInventory.topInventory.holder is CreationGui.CreationGuiHolder) {
                                return@Runnable
                            }

                            val currentSession =
                                    plugin.creationSessionManager.getSession(player.uniqueId)
                            if (currentSession != null &&
                                            currentSession.phase != WorldCreationPhase.SEED_INPUT &&
                                            currentSession.phase != WorldCreationPhase.NAME_INPUT
                            ) {
                                // セッションがまだ残っている（＝他で終了されていない）場合のみ処理
                                cancelAndReturnToMyWorld(player)
                            }
                        },
                        2L
                )
    }

    private fun cancelAndReturnToMyWorld(player: Player) {
        plugin.creationSessionManager.endSession(player.uniqueId)
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (player.isOnline) plugin.menuEntryRouter.openPlayerWorld(player, 0, false)
        })
    }
}
