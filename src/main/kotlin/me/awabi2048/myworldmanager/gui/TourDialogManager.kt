@file:Suppress("REDUNDANT_CALL_OF_CONVERSION_METHOD")

package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuDialogButton
import com.awabi2048.ccsystem.api.gui.MenuDialogHandler
import com.awabi2048.ccsystem.api.gui.MenuDialogInput
import com.awabi2048.ccsystem.api.gui.MenuDialogRequest
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.service.TourManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.block.Block
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class TourDialogManager {
    data class PlacementSession(val worldUuid: UUID, val x: Int, val y: Int, val z: Int)
    data class EditTourSession(val worldUuid: UUID, val tourUuid: UUID)
    data class EditSignSession(val worldUuid: UUID, val signUuid: UUID)
    data class CreateTourSession(val worldUuid: UUID)

    companion object {
        private val placement = ConcurrentHashMap<UUID, PlacementSession>()
        private val textEdit = ConcurrentHashMap<UUID, EditTourSession>()
        private val signEdit = ConcurrentHashMap<UUID, EditSignSession>()
        private val createTour = ConcurrentHashMap<UUID, CreateTourSession>()

        fun startExistingSignBinding(player: Player, plugin: MyWorldManager, block: Block) {
            val worldData = plugin.worldConfigRepository.findByWorldName(player.world.name) ?: return
            if (!plugin.tourManager.canManage(worldData, player.uniqueId)) {
                player.sendMessage(plugin.languageManager.getMessage(player, "error.tour.no_permission"))
                return
            }
            if (!plugin.tourManager.canPlaceSign(worldData)) {
                player.sendMessage(
                    plugin.languageManager.getMessage(
                        player,
                        "error.tour.limit_reached",
                        mapOf("limit" to TourManager.MAX_START_SIGNS_PER_WORLD.toString()),
                    ),
                )
                return
            }
            if (worldData.tours.none { it.startSignUuid == null }) {
                player.sendMessage(plugin.languageManager.getMessage(player, "error.tour.sign_no_available_tour"))
                return
            }
            placement[player.uniqueId] = PlacementSession(worldData.uuid, block.x, block.y, block.z)
            plugin.tourGui.openBindSignToTourMenu(player, worldData)
        }

        fun startTourCreation(player: Player, plugin: MyWorldManager, worldUuid: UUID) {
            createTour[player.uniqueId] = CreateTourSession(worldUuid)
            showCreateTourDialog(player, plugin)
        }

        fun consumePlacement(playerUuid: UUID): PlacementSession? = placement.remove(playerUuid)

        private fun showCreateTourDialog(player: Player, plugin: MyWorldManager) {
            val lang = plugin.languageManager
            CCSystem.getAPI().getMenuDialogService().show(
                player,
                MenuDialogRequest(
                    owner = "myworldmanager",
                    id = "tour-create",
                    title = Component.text(
                        lang.getMessage(player, "gui.tour.create_dialog.title"),
                        NamedTextColor.GOLD,
                    ),
                    body = listOf(
                        Component.text(lang.getMessage(player, "gui.tour.create_dialog.description")),
                    ),
                    inputs = listOf(
                        MenuDialogInput.Text(
                            "name",
                            Component.text(lang.getMessage(player, "gui.tour.input.name")),
                            maxLength = 15,
                        ),
                        MenuDialogInput.Text(
                            "description",
                            Component.text(lang.getMessage(player, "gui.tour.input.description")),
                            maxLength = 30,
                        ),
                    ),
                    confirm = MenuDialogButton(
                        Component.text(lang.getMessage(player, "gui.common.confirm"), NamedTextColor.GREEN),
                        MenuDialogHandler { target, response ->
                            val session = createTour.remove(target.uniqueId)
                                ?: return@MenuDialogHandler MenuActionResult.Rejected()
                            val name = response.textValue("name").ifBlank {
                                createTour[target.uniqueId] = session
                                return@MenuDialogHandler MenuActionResult.Rejected()
                            }
                            val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid)
                                ?: return@MenuDialogHandler MenuActionResult.Rejected()
                            plugin.tourManager.createTour(
                                name.trim().take(15),
                                response.textValue("description").trim().take(30),
                                target.uniqueId,
                                worldData,
                            )
                            plugin.tourGui.openEditMenu(target, worldData)
                            MenuActionResult.Success(MenuUpdate.Close)
                        },
                    ),
                    cancel = MenuDialogButton(
                        Component.text(lang.getMessage(player, "gui.common.cancel"), NamedTextColor.RED),
                        MenuDialogHandler { target, _ ->
                            createTour.remove(target.uniqueId)
                            MenuActionResult.Success(MenuUpdate.Close)
                        },
                    ),
                ),
            )
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
            CCSystem.getAPI().getMenuDialogService().show(
                player,
                MenuDialogRequest(
                    owner = "myworldmanager",
                    id = if (tour) "tour-text-edit" else "tour-sign-text-edit",
                    title = Component.text(lang.getMessage(player, titleKey), NamedTextColor.GOLD),
                    body = listOf(Component.text(lang.getMessage(player, bodyKey))),
                    inputs = listOf(
                        MenuDialogInput.Text(
                            "name",
                            Component.text(
                                lang.getMessage(
                                    player,
                                    if (tour) "gui.tour.input.name" else "gui.tour_sign.input.title",
                                ),
                            ),
                            currentName,
                            maxLength = 15,
                        ),
                        MenuDialogInput.Text(
                            "description",
                            Component.text(
                                lang.getMessage(
                                    player,
                                    if (tour) "gui.tour.input.description" else "gui.tour_sign.input.description",
                                ),
                            ),
                            currentDescription,
                            maxLength = 30,
                        ),
                    ),
                    confirm = MenuDialogButton(
                        Component.text(lang.getMessage(player, "gui.common.confirm"), NamedTextColor.GREEN),
                        MenuDialogHandler { target, response ->
                            if (tour) {
                                saveTourText(target, plugin, response.textValue("name"), response.textValue("description"))
                            } else {
                                saveSignText(target, plugin, response.textValue("name"), response.textValue("description"))
                            }
                        },
                    ),
                    cancel = MenuDialogButton(
                        Component.text(lang.getMessage(player, "gui.common.cancel"), NamedTextColor.RED),
                        MenuDialogHandler { target, _ ->
                            if (tour) {
                                reopenTourEditor(target, plugin)
                            } else {
                                signEdit.remove(target.uniqueId)
                            }
                            MenuActionResult.Success(MenuUpdate.Close)
                        },
                    ),
                ),
            )
        }

        private fun saveTourText(
            player: Player,
            plugin: MyWorldManager,
            name: String,
            description: String,
        ): MenuActionResult {
            val session = textEdit.remove(player.uniqueId) ?: return MenuActionResult.Rejected()
            val edit = plugin.tourSessionManager.getEdit(player.uniqueId) ?: return MenuActionResult.Rejected()
            if (edit.draft.uuid != session.tourUuid) return MenuActionResult.Rejected()
            edit.draft.name = name.ifBlank { edit.draft.name }.take(15)
            edit.draft.description = description.ifBlank { edit.draft.description }.take(30)
            val worldData = plugin.worldConfigRepository.findByUuid(edit.worldUuid)
                ?: return MenuActionResult.Rejected()
            plugin.tourGui.openSingleEditMenu(player, worldData, edit.draft, edit.isNew)
            return MenuActionResult.Success(MenuUpdate.Close)
        }

        private fun saveSignText(
            player: Player,
            plugin: MyWorldManager,
            name: String,
            description: String,
        ): MenuActionResult {
            val session = signEdit.remove(player.uniqueId) ?: return MenuActionResult.Rejected()
            val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid)
                ?: return MenuActionResult.Rejected()
            val sign = plugin.tourManager.getSign(worldData, session.signUuid)
                ?: return MenuActionResult.Rejected()
            sign.title = name.ifBlank { sign.title }.take(15)
            sign.description = description.ifBlank { sign.description }.take(30)
            plugin.worldConfigRepository.save(worldData)
            plugin.tourManager.updateTourSign(sign, worldData)
            return MenuActionResult.Success(MenuUpdate.Close)
        }

        private fun reopenTourEditor(player: Player, plugin: MyWorldManager) {
            plugin.tourSessionManager.getEdit(player.uniqueId)?.let {
                val worldData = plugin.worldConfigRepository.findByUuid(it.worldUuid) ?: return
                plugin.tourGui.openSingleEditMenu(player, worldData, it.draft, it.isNew)
            }
        }

    }

}
