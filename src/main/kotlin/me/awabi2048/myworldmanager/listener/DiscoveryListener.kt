package me.awabi2048.myworldmanager.listener

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.event.player.PlayerCustomClickEvent
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.event.MwmFavoriteAddSource
import me.awabi2048.myworldmanager.api.event.MwmWorldFavoritedEvent
import me.awabi2048.myworldmanager.gui.DialogConfirmManager
import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.session.DiscoverySort
import me.awabi2048.myworldmanager.session.PreviewSessionManager
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import java.util.*

class DiscoveryListener(private val plugin: MyWorldManager) : Listener {

    companion object {
        private const val SPOTLIGHT_DESCRIPTION_MAX_LENGTH = 100
    }

    @EventHandler(ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val view = event.view
        val title = PlainTextComponentSerializer.plainText().serialize(view.title())

        val lang = plugin.languageManager
        if (view.topInventory.holder !is me.awabi2048.myworldmanager.gui.DiscoveryGui.DiscoveryGuiHolder) return
        event.isCancelled = true
        
        // GUI遷移中のクリックを無視
        val settingsSession = plugin.settingsSessionManager.getSession(player)
        if (settingsSession != null && settingsSession.isGuiTransition) {
            player.sendMessage("§7[Debug] Click cancelled (GuiTransition: true)")
            return
        }

        val item = event.currentItem ?: return
        val tag = ItemTag.getType(item) ?: return
        val session = plugin.discoverySessionManager.getSession(player.uniqueId)
        val isBedrock = plugin.playerPlatformResolver.isBedrock(player)

        when (tag) {
            "discovery_world_item", ItemTag.TYPE_GUI_WORLD_ITEM -> {
                val uuid = ItemTag.getWorldUuid(item) ?: return
                val worldData = plugin.worldConfigRepository.findByUuid(uuid) ?: return
                val currentWorldData = plugin.worldConfigRepository.findByWorldName(player.world.name)
                val isCurrentWorld = currentWorldData?.uuid == worldData.uuid

                val isMember = worldData.owner == player.uniqueId || 
                              worldData.moderators.contains(player.uniqueId) ||
                              worldData.members.contains(player.uniqueId)

                if (isBedrock) {
                    if (worldData.publishLevel != PublishLevel.PUBLIC && !isMember) {
                        player.sendMessage(lang.getMessage(player, "error.world_not_public"))
                        plugin.soundManager.playActionSound(player, "discovery", "access_denied")
                        player.closeInventory()
                        return
                    }

                    plugin.soundManager.playClickSound(player, item, "discovery")
                    player.closeInventory()
                    plugin.worldService.teleportToWorld(player, uuid) {
                        player.sendMessage(lang.getMessage(player, "messages.warp_success", mapOf("world" to worldData.name)))
                    }
                    return
                }

                if (event.isLeftClick) {
                    if (!event.isShiftClick && isCurrentWorld) {
                        return
                    }

                    // アクセス判定
                    if (worldData.publishLevel != PublishLevel.PUBLIC && !isMember) {
                        player.sendMessage(lang.getMessage(player, "error.world_not_public"))
                        plugin.soundManager.playActionSound(player, "discovery", "access_denied")
                        player.closeInventory()
                        return
                    }

                    if (event.isShiftClick) {
                        // Member Request (Shift + Left Click)
                        if (isMember) {
                            player.sendMessage(lang.getMessage(player, "messages.member_request_already_member"))
                            return
                        }

                        val title = net.kyori.adventure.text.Component.text(lang.getMessage(player, "gui.member_request_confirm.title"))
                        val bodyLines = lang.getMessageList(player, "gui.member_request_confirm.lore", mapOf("world" to worldData.name))
                            .map { net.kyori.adventure.text.Component.text(it) }

                        me.awabi2048.myworldmanager.gui.DialogConfirmManager.showConfirmationByPreference(
                            player,
                            plugin,
                            title,
                            bodyLines,
                            "mwm:confirm/member_request_send/" + worldData.uuid.toString(),
                            "mwm:confirm/cancel"
                        ) {
                            plugin.menuEntryRouter.openMemberRequestConfirm(
                                player,
                                worldData,
                                onBedrockConfirm = {
                                    plugin.memberRequestManager.sendRequest(player, worldData.uuid)
                                },
                                onBedrockCancel = {
                                    plugin.soundManager.playActionSound(player, "member_request", "cancel")
                                }
                            )
                        }
                        plugin.soundManager.playClickSound(player, item, "discovery")
                        return
                    }

                    // ワープ
                    plugin.soundManager.playClickSound(player, item, "discovery")
                    player.closeInventory()
                    plugin.worldService.teleportToWorld(player, uuid) {
                        player.sendMessage(lang.getMessage(player, "messages.warp_success", mapOf("world" to worldData.name)))
                    }
                } else if (event.isRightClick) {
                    if (!event.isShiftClick && isCurrentWorld) {
                        return
                    }

                    if (event.isShiftClick) {
                        // Spotlight削除 (Shift + 右クリック)
                        if (session.sort == DiscoverySort.SPOTLIGHT && player.hasPermission("myworldmanager.admin")) {
                            val title = net.kyori.adventure.text.Component.text(lang.getMessage(player, "gui.discovery.spotlight_remove_confirm.title"))
                            val bodyLines = lang.getMessageList(player, "gui.discovery.spotlight_remove_confirm.lore", mapOf("world" to worldData.name))
                                .map { net.kyori.adventure.text.Component.text(it) }

                            me.awabi2048.myworldmanager.gui.DialogConfirmManager.showConfirmationByPreference(
                                player,
                                plugin,
                                title,
                                bodyLines,
                                "mwm:confirm/spotlight_remove/" + worldData.uuid.toString(),
                                "mwm:confirm/cancel"
                            ) {
                                plugin.menuEntryRouter.openSpotlightRemoveConfirm(
                                    player,
                                    worldData,
                                    onBedrockConfirm = {
                                        plugin.spotlightRepository.remove(worldData.uuid)
                                        player.sendMessage(
                                            lang.getMessage(
                                                player,
                                                "messages.spotlight_removed",
                                                mapOf("world" to worldData.name)
                                            )
                                        )
                                        plugin.soundManager.playClickSound(player, null, "discovery")
                                        plugin.menuEntryRouter.openDiscovery(player)
                                    },
                                    onBedrockCancel = {
                                        plugin.soundManager.playClickSound(player, null, "discovery")
                                        plugin.menuEntryRouter.openDiscovery(player)
                                    }
                                )
                            }
                            plugin.soundManager.playClickSound(player, item, "discovery")
                            return
                        }

                        // お気に入り (Shift + 右クリック)
                        val isWorldMember = worldData.owner == player.uniqueId || 
                                      worldData.moderators.contains(player.uniqueId) ||
                                      worldData.members.contains(player.uniqueId)
                                      
                        if (isWorldMember) return // 所属ワールドはお気に入り不可

                        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                        var favoriteAdded = false
                        if (stats.favoriteWorlds.containsKey(uuid)) {
                            stats.favoriteWorlds.remove(uuid)
                            worldData.favorite = (worldData.favorite - 1).coerceAtLeast(0)
                            player.sendMessage(lang.getMessage(player, "messages.favorite_removed"))
                            plugin.soundManager.playActionSound(player, "discovery", "favorite_remove")
                        } else {
                            val maxFav = plugin.config.getInt("favorite.max_count", 1000)
                            if (stats.favoriteWorlds.size >= maxFav) {
                                player.sendMessage(lang.getMessage(player, "error.favorite_limit_reached", mapOf("max" to maxFav)))
                                return
                            }
                            val now = java.time.LocalDate.now()
                            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
                            stats.favoriteWorlds[uuid] = now.format(formatter)
                            worldData.favorite++
                            favoriteAdded = true
                            player.sendMessage(lang.getMessage(player, "messages.favorite_added"))
                            plugin.soundManager.playActionSound(player, "discovery", "favorite_add")
                        }
                        plugin.playerStatsRepository.save(stats)
                        plugin.worldConfigRepository.save(worldData)
                        if (favoriteAdded) {
                            Bukkit.getPluginManager().callEvent(
                                MwmWorldFavoritedEvent(
                                    worldUuid = worldData.uuid,
                                    worldName = worldData.name,
                                    playerUuid = player.uniqueId,
                                    playerName = player.name,
                                    source = MwmFavoriteAddSource.DISCOVERY_MENU
                                )
                            )
                        }
                        plugin.menuEntryRouter.openDiscovery(player)
                    } else {
                        // プレビュー (通常右クリック)
                        player.closeInventory()
                        val target = PreviewSessionManager.PreviewTarget.World(worldData)
                        plugin.previewSessionManager.startPreview(player, target, me.awabi2048.myworldmanager.session.PreviewSource.DISCOVERY_MENU)
                    }
                }
            }
            ItemTag.TYPE_GUI_DISCOVERY_TAG -> {
                val allTags = plugin.worldTagManager.getEnabledTagIds()
                if (isBedrock || event.isLeftClick) {
                    val currentIndex = if (session.selectedTag == null) -1 else allTags.indexOf(session.selectedTag)
                    val nextIndex = (currentIndex + 1) % (allTags.size + 1)

                    session.selectedTag = if (nextIndex == allTags.size) {
                        null
                    } else {
                        allTags[nextIndex]
                    }
                } else if (event.isRightClick) {
                    if (session.selectedTag == null) {
                        return
                    }
                    session.selectedTag = null
                }
                
                plugin.soundManager.playClickSound(player, item, "discovery")
                plugin.menuEntryRouter.openDiscovery(player)
            }
            ItemTag.TYPE_GUI_DISCOVERY_SORT -> {
                if (!isBedrock &&
                    event.isShiftClick &&
                    event.isLeftClick &&
                    session.sort == DiscoverySort.SPOTLIGHT &&
                    canManageSpotlight(player)
                ) {
                    plugin.soundManager.playClickSound(player, item, "discovery")
                    openSpotlightDescriptionDialog(player)
                    return
                }

                val forward = if (isBedrock) true else event.isLeftClick
                session.sort = GuiHelper.getNextValue(session.sort, DiscoverySort.values(), forward)
                plugin.soundManager.playClickSound(player, item, "discovery")
                plugin.menuEntryRouter.openDiscovery(player)
            }
            ItemTag.TYPE_GUI_RETURN -> {
                me.awabi2048.myworldmanager.util.GuiHelper.handleReturnClick(plugin, player, item)
            }
            "discovery_spotlight_empty" -> {
                if ((isBedrock || event.isLeftClick) && player.hasPermission("myworldmanager.admin")) {
                    val world = player.world
                    
                    // Try getting by UUID first (standard MyWorld folder name format: my_world.<UUID>)
                    var worldData: me.awabi2048.myworldmanager.model.WorldData? = null
                    if (world.name.startsWith("my_world.")) {
                        try {
                            val uuidStr = world.name.substring("my_world.".length)
                            val uuid = UUID.fromString(uuidStr)
                            worldData = plugin.worldConfigRepository.findByUuid(uuid)
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                    
                    // Fallback to name search if not found by UUID
                    if (worldData == null) {
                        worldData = plugin.worldConfigRepository.findByWorldName(world.name)
                    }

                    if (worldData == null) {
                        player.sendMessage(lang.getMessage(player, "error.spotlight_not_in_myworld"))
                        return
                    }
                    
                    // オーナーチェック (管理者なら他人のワールドも可とする)
                    val isAdmin = player.hasPermission("myworldmanager.admin")
                    if (!isAdmin && worldData.owner != player.uniqueId && !player.isOp) {
                        player.sendMessage(lang.getMessage(player, "error.spotlight_not_owner"))
                        return
                    }
                    
                    val title = net.kyori.adventure.text.Component.text(lang.getMessage(player, "gui.spotlight_confirm.title"))
                    val bodyLines = lang.getMessageList(player, "gui.spotlight_confirm.lore", mapOf("world" to worldData.name))
                        .map { net.kyori.adventure.text.Component.text(it) }

                    me.awabi2048.myworldmanager.gui.DialogConfirmManager.showConfirmationByPreference(
                        player,
                        plugin,
                        title,
                        bodyLines,
                        "mwm:confirm/spotlight_add/" + worldData.uuid.toString(),
                        "mwm:confirm/cancel"
                    ) {
                        plugin.menuEntryRouter.openSpotlightConfirm(
                            player,
                            worldData,
                            onBedrockConfirm = {
                                if (plugin.spotlightRepository.isSpotlight(worldData.uuid)) {
                                    player.sendMessage(lang.getMessage(player, "error.spotlight_already_registered"))
                                } else {
                                    val success = plugin.spotlightRepository.add(worldData.uuid)
                                    if (success) {
                                        player.sendMessage(
                                            lang.getMessage(
                                                player,
                                                "messages.spotlight_added",
                                                mapOf("world" to worldData.name)
                                            )
                                        )
                                        plugin.soundManager.playClickSound(player, null, "discovery")
                                    } else {
                                        player.sendMessage(lang.getMessage(player, "error.spotlight_limit_reached"))
                                    }
                                }
                                plugin.menuEntryRouter.openDiscovery(player)
                            },
                            onBedrockCancel = {
                                plugin.soundManager.playClickSound(player, null, "discovery")
                                plugin.menuEntryRouter.openDiscovery(player)
                            }
                        )
                    }
                    plugin.soundManager.playClickSound(player, item, "discovery")
                }
            }
        }
    }

    @Suppress("UnstableApiUsage")
    @EventHandler
    fun onSpotlightDescriptionDialog(event: PlayerCustomClickEvent) {
        val identifier = event.identifier
        if (identifier != Key.key("mwm:discovery/spotlight_description_submit") &&
            identifier != Key.key("mwm:discovery/spotlight_description_cancel")
        ) {
            return
        }

        val conn = event.commonConnection as? io.papermc.paper.connection.PlayerGameConnection ?: return
        val player = conn.player
        val lang = plugin.languageManager

        if (!canManageSpotlight(player)) {
            player.sendMessage(lang.getMessage(player, "general.no_permission"))
            return
        }

        if (identifier == Key.key("mwm:discovery/spotlight_description_cancel")) {
            DialogConfirmManager.safeCloseDialog(player)
            plugin.soundManager.playClickSound(player, null, "discovery")
            plugin.menuEntryRouter.openDiscovery(player)
            return
        }

        val view = event.getDialogResponseView() ?: return
        val input = view.getText("spotlight_description")?.toString().orEmpty().trim()
        if (input.length > SPOTLIGHT_DESCRIPTION_MAX_LENGTH) {
            player.sendMessage(
                lang.getMessage(
                    player,
                    "error.discovery_spotlight_description_too_long",
                    mapOf("max" to SPOTLIGHT_DESCRIPTION_MAX_LENGTH)
                )
            )
            openSpotlightDescriptionDialog(player, input)
            return
        }

        plugin.spotlightRepository.setDescription(input)
        val messageKey = if (input.isEmpty()) {
            "messages.discovery_spotlight_description_reset"
        } else {
            "messages.discovery_spotlight_description_updated"
        }
        player.sendMessage(lang.getMessage(player, messageKey))
        plugin.soundManager.playClickSound(player, null, "discovery")
        plugin.menuEntryRouter.openDiscovery(player)
    }

    private fun canManageSpotlight(player: Player): Boolean {
        return player.hasPermission("myworldmanager.admin")
    }

    @Suppress("UnstableApiUsage")
    private fun openSpotlightDescriptionDialog(player: Player, initialValue: String? = null) {
        val lang = plugin.languageManager
        val currentText = initialValue ?: plugin.spotlightRepository.getDescription().orEmpty()

        val dialog = Dialog.create { builder ->
            builder.empty()
                .base(
                    DialogBase.builder(
                        Component.text(
                            lang.getMessage(player, "gui.discovery.spotlight_description_dialog.title"),
                            NamedTextColor.YELLOW
                        )
                    )
                        .body(
                            listOf(
                                DialogBody.plainMessage(
                                    Component.text(
                                        lang.getMessage(
                                            player,
                                            "gui.discovery.spotlight_description_dialog.body",
                                            mapOf("max" to SPOTLIGHT_DESCRIPTION_MAX_LENGTH)
                                        )
                                    )
                                )
                            )
                        )
                        .inputs(
                            listOf(
                                DialogInput.text(
                                    "spotlight_description",
                                    Component.text(
                                        lang.getMessage(
                                            player,
                                            "gui.discovery.spotlight_description_dialog.input_label"
                                        )
                                    )
                                )
                                    .maxLength(SPOTLIGHT_DESCRIPTION_MAX_LENGTH)
                                    .initial(currentText)
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
                            DialogAction.customClick(Key.key("mwm:discovery/spotlight_description_submit"), null)
                        ),
                        ActionButton.create(
                            Component.text(lang.getMessage(player, "gui.common.cancel"), NamedTextColor.RED),
                            null,
                            200,
                            DialogAction.customClick(Key.key("mwm:discovery/spotlight_description_cancel"), null)
                        )
                    )
                )
        }
        player.showDialog(dialog)
    }
}
