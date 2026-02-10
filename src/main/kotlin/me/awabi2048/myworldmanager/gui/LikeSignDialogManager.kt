package me.awabi2048.myworldmanager.gui

import io.papermc.paper.connection.PlayerGameConnection
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.event.player.PlayerCustomClickEvent
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.LikeSignData
import me.awabi2048.myworldmanager.model.LikeSignDisplayType
import me.awabi2048.myworldmanager.service.LikeSignManager
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class LikeSignDialogManager : Listener {

    data class PlacementSession(
        val playerUuid: UUID,
        val worldUuid: UUID,
        val blockX: Int,
        val blockY: Int,
        val blockZ: Int,
        val blockFace: String,
        val hand: EquipmentSlot,
        var title: String = "",
        var description: String = "",
        var displayType: LikeSignDisplayType = LikeSignDisplayType.HOLOGRAM
    )

    data class EditSession(
        val playerUuid: UUID,
        val signUuid: UUID,
        val worldUuid: UUID
    )

    data class UnlikeSession(
        val playerUuid: UUID,
        val signUuid: UUID,
        val worldUuid: UUID
    )

    companion object {
        private val placementSessions = ConcurrentHashMap<UUID, PlacementSession>()
        private val editSessions = ConcurrentHashMap<UUID, EditSession>()
        private val unlikeSessions = ConcurrentHashMap<UUID, UnlikeSession>()

        fun startPlacementSession(
            player: Player,
            plugin: MyWorldManager,
            block: Block,
            blockFace: BlockFace,
            hand: EquipmentSlot
        ) {
            val worldName = player.world.name
            if (!worldName.startsWith("my_world.")) {
                player.sendMessage(plugin.languageManager.getMessage(player, "error.like_sign.my_world_only"))
                return
            }

            val worldUuidStr = worldName.removePrefix("my_world.")
            val worldUuid = try { UUID.fromString(worldUuidStr) } catch (e: Exception) { return }
            val worldData = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return

            if (!plugin.likeSignManager.isWorldMember(worldData, player.uniqueId)) {
                player.sendMessage(plugin.languageManager.getMessage(player, "error.like_sign.no_permission"))
                return
            }

            if (!plugin.likeSignManager.canPlaceSign(worldData)) {
                player.sendMessage(plugin.languageManager.getMessage(player, "error.like_sign.limit_reached"))
                return
            }

            val session = PlacementSession(
                playerUuid = player.uniqueId,
                worldUuid = worldUuid,
                blockX = block.x,
                blockY = block.y,
                blockZ = block.z,
                blockFace = blockFace.name,
                hand = hand
            )
            placementSessions[player.uniqueId] = session

            showPlacementDialog(player, plugin, session)
        }

        fun startEditSession(
            player: Player,
            plugin: MyWorldManager,
            signData: LikeSignData,
            worldUuid: UUID
        ) {
            val session = EditSession(
                playerUuid = player.uniqueId,
                signUuid = signData.uuid,
                worldUuid = worldUuid
            )
            editSessions[player.uniqueId] = session

            showEditDialog(player, plugin, signData)
        }

        private fun showPlacementDialog(player: Player, plugin: MyWorldManager, session: PlacementSession) {
            val lang = plugin.languageManager

            val displayTypeOptions = listOf(
                SingleOptionDialogInput.OptionEntry.create(
                    "sign",
                    Component.text(lang.getMessage(player, "gui.like_sign.display_type.sign"), NamedTextColor.YELLOW),
                    session.displayType == LikeSignDisplayType.SIGN
                ),
                SingleOptionDialogInput.OptionEntry.create(
                    "hologram",
                    Component.text(lang.getMessage(player, "gui.like_sign.display_type.hologram"), NamedTextColor.AQUA),
                    session.displayType == LikeSignDisplayType.HOLOGRAM
                )
            )

            val dialog = Dialog.create { builder ->
                builder.empty()
                    .base(
                        DialogBase.builder(Component.text(lang.getMessage(player, "gui.like_sign.placement.title"), NamedTextColor.GOLD))
                            .body(
                                listOf(
                                    DialogBody.plainMessage(Component.text(lang.getMessage(player, "gui.like_sign.placement.description")))
                                )
                            )
                            .inputs(
                                listOf(
                                    DialogInput.text("title", Component.text(lang.getMessage(player, "gui.like_sign.input.title")))
                                        .maxLength(LikeSignManager.MAX_TITLE_LENGTH)
                                        .initial(session.title)
                                        .build(),
                                    DialogInput.text("description", Component.text(lang.getMessage(player, "gui.like_sign.input.description")))
                                        .maxLength(LikeSignManager.MAX_DESCRIPTION_LENGTH)
                                        .initial(session.description)
                                        .build(),
                                    DialogInput.singleOption(
                                        "display_type",
                                        Component.text(lang.getMessage(player, "gui.like_sign.input.display_type")),
                                        displayTypeOptions
                                    ).build()
                                )
                            )
                            .build()
                    )
                    .type(
                        DialogType.confirmation(
                            ActionButton.create(
                                Component.text(lang.getMessage(player, "gui.like_sign.button.place"), NamedTextColor.GREEN),
                                null,
                                100,
                                DialogAction.customClick(Key.key("mwm:like_sign/place"), null)
                            ),
                            ActionButton.create(
                                Component.text(lang.getMessage(player, "gui.like_sign.button.cancel"), NamedTextColor.RED),
                                null,
                                200,
                                DialogAction.customClick(Key.key("mwm:like_sign/cancel"), null)
                            )
                        )
                    )
            }
            player.showDialog(dialog)
        }

        private fun showEditDialog(player: Player, plugin: MyWorldManager, signData: LikeSignData) {
            val lang = plugin.languageManager

            val bodyLines = mutableListOf<Component>()
            bodyLines.add(Component.text("§8§m－－－－－－－－－－－－－－－－－－"))
            bodyLines.add(Component.text("§f§l| §7${lang.getMessage(player, "gui.like_sign.edit.current_likes")} §c❤ ${signData.likeCount()}"))
            bodyLines.add(Component.text("§f§l| §7${lang.getMessage(player, "gui.like_sign.input.display_type")} §f${signData.displayType.name}"))
            bodyLines.add(Component.text("§8§m－－－－－－－－－－－－－－－－－－"))

            val dialog = Dialog.create { builder ->
                builder.empty()
                    .base(
                        DialogBase.builder(Component.text(lang.getMessage(player, "gui.like_sign.edit.title"), NamedTextColor.GOLD))
                            .body(bodyLines.map { DialogBody.plainMessage(it) })
                            .inputs(
                                listOf(
                                    DialogInput.text("title", Component.text(lang.getMessage(player, "gui.like_sign.input.title")))
                                        .maxLength(LikeSignManager.MAX_TITLE_LENGTH)
                                        .initial(signData.title)
                                        .build(),
                                    DialogInput.text("description", Component.text(lang.getMessage(player, "gui.like_sign.input.description")))
                                        .maxLength(LikeSignManager.MAX_DESCRIPTION_LENGTH)
                                        .initial(signData.description)
                                        .build()
                                )
                            )
                            .build()
                    )
                    .type(
                        DialogType.confirmation(
                            ActionButton.create(
                                Component.text(lang.getMessage(player, "gui.like_sign.button.save"), NamedTextColor.GREEN),
                                null,
                                100,
                                DialogAction.customClick(Key.key("mwm:like_sign/save"), null)
                            ),
                            ActionButton.create(
                                Component.text(lang.getMessage(player, "gui.like_sign.button.delete"), NamedTextColor.RED),
                                null,
                                150,
                                DialogAction.customClick(Key.key("mwm:like_sign/delete"), null)
                            )
                        )
                    )
            }
            player.showDialog(dialog)
        }

        fun showLikeConfirmDialog(player: Player, plugin: MyWorldManager, signData: LikeSignData, worldUuid: UUID) {
            val lang = plugin.languageManager

            val session = UnlikeSession(
                playerUuid = player.uniqueId,
                signUuid = signData.uuid,
                worldUuid = worldUuid
            )
            unlikeSessions[player.uniqueId] = session

            val title = Component.text(lang.getMessage(player, "gui.like_sign.unlike_confirm.title"), NamedTextColor.RED)
            val bodyLines = listOf(
                Component.text(lang.getMessage(player, "gui.like_sign.unlike_confirm.description"))
            )

            DialogConfirmManager.showConfirmationByPreference(
                player,
                plugin,
                title,
                bodyLines,
                "mwm:like_sign/unlike",
                "mwm:like_sign/unlike_cancel"
            ) {
            }
        }
    }

    @EventHandler
    fun handleLikeSignDialog(event: PlayerCustomClickEvent) {
        val identifier = event.identifier
        val conn = event.commonConnection as? PlayerGameConnection ?: return
        val player = conn.player

        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)

        if (identifier == Key.key("mwm:like_sign/cancel")) {
            placementSessions.remove(player.uniqueId)
            DialogConfirmManager.safeCloseDialog(player)
            return
        }

        if (identifier == Key.key("mwm:like_sign/place")) {
            val session = placementSessions.remove(player.uniqueId) ?: return
            val view = event.getDialogResponseView() ?: return

            val titleInput = view.getText("title")?.toString() ?: ""
            val descriptionInput = view.getText("description")?.toString() ?: ""
            val displayTypeInput = view.getText("display_type")?.toString() ?: "hologram"

            val displayType = when (displayTypeInput) {
                "sign" -> LikeSignDisplayType.SIGN
                else -> LikeSignDisplayType.HOLOGRAM
            }

            if (titleInput.isBlank()) {
                player.sendMessage(plugin.languageManager.getMessage(player, "error.like_sign.title_required"))
                return
            }

            val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid) ?: return
            val world = player.world
            val block = world.getBlockAt(session.blockX, session.blockY, session.blockZ)
            val blockFace = try { BlockFace.valueOf(session.blockFace) } catch (e: Exception) { BlockFace.NORTH }

            val signData = plugin.likeSignManager.createSign(
                worldData,
                player,
                block,
                blockFace,
                titleInput,
                descriptionInput,
                displayType
            )

            if (signData != null) {
                val item = if (session.hand == EquipmentSlot.HAND) {
                    player.inventory.itemInMainHand
                } else {
                    player.inventory.itemInOffHand
                }
                item.amount -= 1

                player.sendMessage(plugin.languageManager.getMessage(player, "messages.like_sign.placed"))
                player.playSound(player.location, org.bukkit.Sound.BLOCK_WOOD_PLACE, 1.0f, 1.0f)
            }

            DialogConfirmManager.safeCloseDialog(player)
            return
        }

        if (identifier == Key.key("mwm:like_sign/save")) {
            val session = editSessions.remove(player.uniqueId) ?: return
            val view = event.getDialogResponseView() ?: return

            val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid) ?: return
            val signData = plugin.likeSignManager.findSignByUuid(worldData, session.signUuid) ?: return

            val titleInput = view.getText("title")?.toString() ?: signData.title
            val descriptionInput = view.getText("description")?.toString() ?: signData.description

            signData.title = titleInput.take(LikeSignManager.MAX_TITLE_LENGTH)
            signData.description = descriptionInput.take(LikeSignManager.MAX_DESCRIPTION_LENGTH)

            plugin.worldConfigRepository.save(worldData)
            plugin.likeSignManager.refreshSignDisplay(signData, worldData)

            player.sendMessage(plugin.languageManager.getMessage(player, "messages.like_sign.saved"))
            player.playSound(player.location, org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f)

            DialogConfirmManager.safeCloseDialog(player)
            return
        }

        if (identifier == Key.key("mwm:like_sign/delete")) {
            val session = editSessions.remove(player.uniqueId) ?: return
            val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid) ?: return
            val signData = plugin.likeSignManager.findSignByUuid(worldData, session.signUuid) ?: return

            plugin.likeSignManager.removeSign(signData, worldData, player)

            player.sendMessage(plugin.languageManager.getMessage(player, "messages.like_sign.deleted"))
            player.playSound(player.location, org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f)

            DialogConfirmManager.safeCloseDialog(player)
            return
        }

        if (identifier == Key.key("mwm:like_sign/unlike")) {
            val session = unlikeSessions.remove(player.uniqueId) ?: return
            val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid) ?: return
            val signData = plugin.likeSignManager.findSignByUuid(worldData, session.signUuid) ?: return

            if (signData.hasLiked(player.uniqueId)) {
                signData.removeLike(player.uniqueId)
                plugin.worldConfigRepository.save(worldData)
                plugin.likeSignManager.refreshSignDisplay(signData, worldData)

                player.sendMessage(plugin.languageManager.getMessage(player, "messages.like_sign.unliked"))
                player.playSound(player.location, org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f)
            }

            DialogConfirmManager.safeCloseDialog(player)
            return
        }

        if (identifier == Key.key("mwm:like_sign/unlike_cancel")) {
            DialogConfirmManager.safeCloseDialog(player)
            return
        }
    }
}
