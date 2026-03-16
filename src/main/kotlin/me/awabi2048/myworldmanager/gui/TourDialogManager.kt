package me.awabi2048.myworldmanager.gui

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
import me.awabi2048.myworldmanager.service.TourManager
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class TourDialogManager : Listener {
    data class PlacementSession(val worldUuid: UUID, val x: Int, val y: Int, val z: Int, val blockFace: String, val hand: EquipmentSlot)
    data class EditTourSession(val worldUuid: UUID, val tourUuid: UUID)
    data class EditSignSession(val worldUuid: UUID, val signUuid: UUID)

    companion object {
        private val placement = ConcurrentHashMap<UUID, PlacementSession>()
        private val textEdit = ConcurrentHashMap<UUID, EditTourSession>()
        private val signEdit = ConcurrentHashMap<UUID, EditSignSession>()

        fun startPlacement(player: Player, plugin: MyWorldManager, block: Block, blockFace: BlockFace, hand: EquipmentSlot) {
            val worldData = plugin.worldConfigRepository.findByWorldName(player.world.name) ?: run {
                player.sendMessage(plugin.languageManager.getMessage(player, "error.tour.my_world_only"))
                return
            }
            if (!plugin.tourManager.canManage(worldData, player.uniqueId)) {
                player.sendMessage(plugin.languageManager.getMessage(player, "error.tour.no_permission"))
                return
            }
            if (!plugin.tourManager.canPlaceSign(worldData)) {
                player.sendMessage(plugin.languageManager.getMessage(player, "error.tour.limit_reached", mapOf("limit" to TourManager.MAX_SIGNS_PER_WORLD.toString())))
                return
            }
            placement[player.uniqueId] = PlacementSession(worldData.uuid, block.x, block.y, block.z, blockFace.name, hand)
            showPlacementDialog(player, plugin)
        }

        fun startTourTextEdit(player: Player, plugin: MyWorldManager, worldUuid: UUID, tourUuid: UUID, currentName: String, currentDescription: String) {
            textEdit[player.uniqueId] = EditTourSession(worldUuid, tourUuid)
            showTextDialog(player, plugin, currentName, currentDescription, true)
        }

        fun startSignTextEdit(player: Player, plugin: MyWorldManager, worldUuid: UUID, signUuid: UUID, currentName: String, currentDescription: String) {
            signEdit[player.uniqueId] = EditSignSession(worldUuid, signUuid)
            showTextDialog(player, plugin, currentName, currentDescription, false)
        }

        private fun showTextDialog(player: Player, plugin: MyWorldManager, currentName: String, currentDescription: String, tour: Boolean) {
            val lang = plugin.languageManager
            val titleKey = if (tour) "gui.tour.edit_text.title" else "gui.tour_sign.edit_text.title"
            val bodyKey = if (tour) "gui.tour.edit_text.description" else "gui.tour_sign.edit_text.description"
            val confirmKey = if (tour) "mwm:tour/edit_text" else "mwm:tour_sign/edit_text"
            val cancelKey = if (tour) "mwm:tour/edit_text_cancel" else "mwm:tour_sign/edit_text_cancel"
            val dialog = Dialog.create { builder ->
                builder.empty().base(
                    DialogBase.builder(Component.text(lang.getMessage(player, titleKey), NamedTextColor.GOLD))
                        .body(listOf(DialogBody.plainMessage(Component.text(lang.getMessage(player, bodyKey)))))
                        .inputs(listOf(
                            DialogInput.text("name", Component.text(lang.getMessage(player, if (tour) "gui.tour.input.name" else "gui.tour_sign.input.title"))).initial(currentName).maxLength(15).build(),
                            DialogInput.text("description", Component.text(lang.getMessage(player, if (tour) "gui.tour.input.description" else "gui.tour_sign.input.description"))).initial(currentDescription).maxLength(30).build()
                        )).build()
                ).type(DialogType.confirmation(
                    ActionButton.create(Component.text(lang.getMessage(player, "gui.common.confirm"), NamedTextColor.GREEN), null, 100, DialogAction.customClick(Key.key(confirmKey), null)),
                    ActionButton.create(Component.text(lang.getMessage(player, "gui.common.cancel"), NamedTextColor.RED), null, 200, DialogAction.customClick(Key.key(cancelKey), null))
                ))
            }
            player.showDialog(dialog)
        }

        private fun showPlacementDialog(player: Player, plugin: MyWorldManager) {
            val lang = plugin.languageManager
            val dialog = Dialog.create { builder ->
                builder.empty().base(
                    DialogBase.builder(Component.text(lang.getMessage(player, "gui.tour_sign.placement.title"), NamedTextColor.GOLD))
                        .body(listOf(DialogBody.plainMessage(Component.text(lang.getMessage(player, "gui.tour_sign.placement.description")))))
                        .inputs(listOf(
                            DialogInput.text("title", Component.text(lang.getMessage(player, "gui.tour_sign.input.title"))).maxLength(15).build(),
                            DialogInput.text("description", Component.text(lang.getMessage(player, "gui.tour_sign.input.description"))).maxLength(30).build()
                        )).build()
                ).type(DialogType.confirmation(
                    ActionButton.create(Component.text(lang.getMessage(player, "gui.tour_sign.button.place"), NamedTextColor.GREEN), null, 100, DialogAction.customClick(Key.key("mwm:tour_sign/place"), null)),
                    ActionButton.create(Component.text(lang.getMessage(player, "gui.tour_sign.button.cancel"), NamedTextColor.RED), null, 200, DialogAction.customClick(Key.key("mwm:tour_sign/cancel"), null))
                ))
            }
            player.showDialog(dialog)
        }
    }

    @EventHandler
    fun onCustomClick(event: PlayerCustomClickEvent) {
        val conn = event.commonConnection as? PlayerGameConnection ?: return
        val player = conn.player
        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
        when (event.identifier) {
            Key.key("mwm:tour_sign/cancel") -> placement.remove(player.uniqueId)
            Key.key("mwm:tour_sign/place") -> {
                val session = placement.remove(player.uniqueId) ?: return
                val view = event.getDialogResponseView() ?: return
                val title = view.getText("title")?.toString().orEmpty()
                if (title.isBlank()) {
                    player.sendMessage(plugin.languageManager.getMessage(player, "error.tour.title_required"))
                    return
                }
                val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid) ?: return
                if (!plugin.tourManager.canPlaceSign(worldData)) {
                    player.sendMessage(plugin.languageManager.getMessage(player, "error.tour.limit_reached", mapOf("limit" to TourManager.MAX_SIGNS_PER_WORLD.toString())))
                    return
                }
                plugin.tourManager.createTourSign(worldData, player, player.world.getBlockAt(session.x, session.y, session.z), runCatching { BlockFace.valueOf(session.blockFace) }.getOrDefault(BlockFace.NORTH), title, view.getText("description")?.toString().orEmpty())
                val item = if (session.hand == EquipmentSlot.HAND) player.inventory.itemInMainHand else player.inventory.itemInOffHand
                item.amount -= 1
                player.sendMessage(plugin.languageManager.getMessage(player, "messages.tour_sign.placed"))
            }
            Key.key("mwm:tour/edit_text_cancel") -> plugin.tourSessionManager.getEdit(player.uniqueId)?.let {
                val worldData = plugin.worldConfigRepository.findByUuid(it.worldUuid) ?: return
                plugin.tourGui.openSingleEditMenu(player, worldData, it.draft, it.isNew)
            }
            Key.key("mwm:tour/edit_text") -> {
                val session = textEdit.remove(player.uniqueId) ?: return
                val edit = plugin.tourSessionManager.getEdit(player.uniqueId) ?: return
                if (edit.draft.uuid != session.tourUuid) return
                val view = event.getDialogResponseView() ?: return
                edit.draft.name = view.getText("name")?.toString().orEmpty().ifBlank { edit.draft.name }.take(15)
                edit.draft.description = view.getText("description")?.toString().orEmpty().ifBlank { edit.draft.description }.take(30)
                val worldData = plugin.worldConfigRepository.findByUuid(edit.worldUuid) ?: return
                plugin.tourGui.openSingleEditMenu(player, worldData, edit.draft, edit.isNew)
            }
            Key.key("mwm:tour/discard_new") -> {
                val edit = plugin.tourSessionManager.getEdit(player.uniqueId) ?: return
                val worldData = plugin.worldConfigRepository.findByUuid(edit.worldUuid) ?: return
                plugin.tourSessionManager.clearEdit(player.uniqueId)
                plugin.tourGui.openEditMenu(player, worldData)
                DialogConfirmManager.safeCloseDialog(player)
            }
            Key.key("mwm:tour/discard_new_cancel") -> {
                val edit = plugin.tourSessionManager.getEdit(player.uniqueId) ?: return
                val worldData = plugin.worldConfigRepository.findByUuid(edit.worldUuid) ?: return
                plugin.tourGui.openSingleEditMenu(player, worldData, edit.draft, true)
                DialogConfirmManager.safeCloseDialog(player)
            }
            Key.key("mwm:tour/delete_confirm") -> {
                val edit = plugin.tourSessionManager.getEdit(player.uniqueId) ?: return
                val worldData = plugin.worldConfigRepository.findByUuid(edit.worldUuid) ?: return
                if (!edit.isNew) {
                    plugin.tourManager.deleteTour(worldData, edit.originalTourUuid ?: return)
                }
                plugin.tourSessionManager.clearEdit(player.uniqueId)
                plugin.tourGui.openEditMenu(player, worldData)
                DialogConfirmManager.safeCloseDialog(player)
            }
            Key.key("mwm:tour/delete_cancel") -> {
                val edit = plugin.tourSessionManager.getEdit(player.uniqueId) ?: return
                val worldData = plugin.worldConfigRepository.findByUuid(edit.worldUuid) ?: return
                plugin.tourGui.openSingleEditMenu(player, worldData, edit.draft, edit.isNew)
                DialogConfirmManager.safeCloseDialog(player)
            }
            Key.key("mwm:tour_sign/edit_text_cancel") -> signEdit.remove(player.uniqueId)
            Key.key("mwm:tour_sign/edit_text") -> {
                val session = signEdit.remove(player.uniqueId) ?: return
                val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid) ?: return
                val sign = plugin.tourManager.getSign(worldData, session.signUuid) ?: return
                val view = event.getDialogResponseView() ?: return
                sign.title = view.getText("name")?.toString().orEmpty().ifBlank { sign.title }.take(15)
                sign.description = view.getText("description")?.toString().orEmpty().ifBlank { sign.description }.take(30)
                plugin.worldConfigRepository.save(worldData)
                plugin.tourManager.updateTourSign(sign, worldData)
            }
            else -> return
        }
    }
}
