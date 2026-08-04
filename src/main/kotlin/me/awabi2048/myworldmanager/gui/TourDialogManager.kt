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
    data class EditWaypointSession(val worldUuid: UUID, val tourUuid: UUID, val waypointUuid: UUID)
    data class EditSignSession(val worldUuid: UUID, val signUuid: UUID)
    data class CreateTourSession(val worldUuid: UUID)

    companion object {
        private val placement = ConcurrentHashMap<UUID, PlacementSession>()
        private val textEdit = ConcurrentHashMap<UUID, EditTourSession>()
        private val waypointNameEdit = ConcurrentHashMap<UUID, EditWaypointSession>()
        private val waypointDescriptionEdit = ConcurrentHashMap<UUID, EditWaypointSession>()
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

        fun clear(playerUuid: UUID) {
            placement.remove(playerUuid)
            textEdit.remove(playerUuid)
            waypointNameEdit.remove(playerUuid)
            waypointDescriptionEdit.remove(playerUuid)
            signEdit.remove(playerUuid)
            createTour.remove(playerUuid)
        }

        fun clearAll() {
            placement.clear()
            textEdit.clear()
            waypointNameEdit.clear()
            waypointDescriptionEdit.clear()
            signEdit.clear()
            createTour.clear()
        }

        private fun showCreateTourDialog(
            player: Player,
            plugin: MyWorldManager,
            errorMessage: Component? = null,
        ) {
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
                    body = buildList {
                        errorMessage?.let(::add)
                        add(Component.text(lang.getMessage(player, "gui.tour.create_dialog.description")))
                    },
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
                            val name = response.textValue("name").trim()
                            if (name.isBlank()) {
                                createTour[target.uniqueId] = session
                                // Paper Dialogは確認後に閉じるため、検証エラーを本文へ付けた
                                // 同じDialogを再表示し、入力中の作成セッションを維持します。
                                showCreateTourDialog(
                                    target,
                                    plugin,
                                    lang.getComponent(target, "gui.tour.create_dialog.name_required"),
                                )
                                return@MenuDialogHandler MenuActionResult.Ignored
                            }
                            val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid)
                                ?: return@MenuDialogHandler MenuActionResult.Rejected()
                            plugin.tourManager.createTour(
                                name.take(15),
                                response.textValue("description").trim().take(30),
                                target.uniqueId,
                                worldData,
                            )
                            MenuActionResult.Success(MenuUpdate.Resume)
                        },
                    ),
                    cancel = MenuDialogButton(
                        Component.text(lang.getMessage(player, "gui.common.cancel"), NamedTextColor.RED),
                        MenuDialogHandler { target, _ ->
                            createTour.remove(target.uniqueId)
                            MenuActionResult.Success(MenuUpdate.Resume)
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

        fun startWaypointNameEdit(
            player: Player,
            plugin: MyWorldManager,
            worldUuid: UUID,
            tourUuid: UUID,
            waypointUuid: UUID,
            currentName: String,
        ) {
            waypointNameEdit[player.uniqueId] = EditWaypointSession(worldUuid, tourUuid, waypointUuid)
            if (plugin.playerPlatformResolver.isBedrock(player) &&
                plugin.floodgateFormBridge.isAvailable(player)
            ) {
                showBedrockWaypointNameForm(player, plugin, currentName)
                return
            }
            val lang = plugin.languageManager
            CCSystem.getAPI().getMenuDialogService().show(
                player,
                MenuDialogRequest(
                    owner = "myworldmanager",
                    id = "tour-waypoint-name-edit",
                    title = Component.text(lang.getMessage(player, "gui.tour.waypoint.name_dialog.title"), NamedTextColor.GOLD),
                    body = listOf(Component.text(lang.getMessage(player, "gui.tour.waypoint.name_dialog.description"))),
                    inputs = listOf(
                        MenuDialogInput.Text(
                            "name",
                            Component.text(lang.getMessage(player, "gui.tour.menu.waypoint.name")),
                            currentName,
                            maxLength = TourManager.MAX_TITLE_LENGTH,
                        ),
                    ),
                    confirm = MenuDialogButton(
                        Component.text(lang.getMessage(player, "gui.common.confirm"), NamedTextColor.GREEN),
                        MenuDialogHandler { target, response ->
                            val editSession = waypointNameEdit.remove(target.uniqueId)
                                ?: return@MenuDialogHandler MenuActionResult.Rejected()
                            val name = response.textValue("name").trim()
                            if (name.isBlank()) {
                                waypointNameEdit[target.uniqueId] = editSession
                                return@MenuDialogHandler MenuActionResult.Rejected()
                            }
                            val edit = plugin.tourSessionManager.getEdit(target.uniqueId)
                                ?: return@MenuDialogHandler MenuActionResult.Rejected()
                            if (edit.draft.uuid != editSession.tourUuid ||
                                !plugin.tourManager.updateWaypointName(edit, editSession.waypointUuid, name)
                            ) {
                                return@MenuDialogHandler MenuActionResult.Rejected()
                            }
                            val worldData = plugin.worldConfigRepository.findByUuid(editSession.worldUuid)
                                ?: return@MenuDialogHandler MenuActionResult.Rejected()
                            plugin.tourManager.saveEditSession(target, worldData, closeSession = false)
                            MenuActionResult.Success(MenuUpdate.Resume)
                        },
                    ),
                    cancel = MenuDialogButton(
                        Component.text(lang.getMessage(player, "gui.common.cancel"), NamedTextColor.RED),
                        MenuDialogHandler { target, _ ->
                            waypointNameEdit.remove(target.uniqueId)
                            MenuActionResult.Success(MenuUpdate.Resume)
                        },
                    ),
                ),
            )
        }

        fun startWaypointDescriptionEdit(
            player: Player,
            plugin: MyWorldManager,
            worldUuid: UUID,
            tourUuid: UUID,
            waypointUuid: UUID,
            currentDescription: List<String>,
        ) {
            waypointDescriptionEdit[player.uniqueId] = EditWaypointSession(worldUuid, tourUuid, waypointUuid)
            if (plugin.playerPlatformResolver.isBedrock(player) &&
                plugin.floodgateFormBridge.isAvailable(player)
            ) {
                showBedrockWaypointDescriptionForm(player, plugin, currentDescription)
                return
            }
            val lang = plugin.languageManager
            CCSystem.getAPI().getMenuDialogService().show(
                player,
                MenuDialogRequest(
                    owner = "myworldmanager",
                    id = "tour-waypoint-description-edit",
                    title = Component.text(lang.getMessage(player, "gui.tour.waypoint.description_dialog.title"), NamedTextColor.GOLD),
                    body = listOf(Component.text(lang.getMessage(player, "gui.tour.waypoint.description_dialog.description"))),
                    inputs = (0 until 3).map { index ->
                        MenuDialogInput.Text(
                            "line_$index",
                            Component.text(
                                lang.getMessage(
                                    player,
                                    "gui.tour.waypoint.description_line",
                                    mapOf("line" to index + 1),
                                ),
                            ),
                            currentDescription.getOrNull(index).orEmpty(),
                            maxLength = TourManager.MAX_DESCRIPTION_LENGTH,
                        )
                    },
                    confirm = MenuDialogButton(
                        Component.text(lang.getMessage(player, "gui.common.confirm"), NamedTextColor.GREEN),
                        MenuDialogHandler { target, response ->
                            val editSession = waypointDescriptionEdit.remove(target.uniqueId)
                                ?: return@MenuDialogHandler MenuActionResult.Rejected()
                            val edit = plugin.tourSessionManager.getEdit(target.uniqueId)
                                ?: return@MenuDialogHandler MenuActionResult.Rejected()
                            if (edit.draft.uuid != editSession.tourUuid ||
                                !plugin.tourManager.updateWaypointDescription(
                                    edit,
                                    editSession.waypointUuid,
                                    (0 until 3).map { index -> response.textValue("line_$index") },
                                )
                            ) {
                                return@MenuDialogHandler MenuActionResult.Rejected()
                            }
                            val worldData = plugin.worldConfigRepository.findByUuid(editSession.worldUuid)
                                ?: return@MenuDialogHandler MenuActionResult.Rejected()
                            plugin.tourManager.saveEditSession(target, worldData, closeSession = false)
                            MenuActionResult.Success(MenuUpdate.Resume)
                        },
                    ),
                    cancel = MenuDialogButton(
                        Component.text(lang.getMessage(player, "gui.common.cancel"), NamedTextColor.RED),
                        MenuDialogHandler { target, _ ->
                            waypointDescriptionEdit.remove(target.uniqueId)
                            MenuActionResult.Success(MenuUpdate.Resume)
                        },
                    ),
                ),
            )
        }

        /** Bedrockでは、ワールド設定の案内編集と同じ外部フォーム経路で入力を受け取ります。 */
        private fun showBedrockWaypointNameForm(player: Player, plugin: MyWorldManager, currentName: String) {
            val lang = plugin.languageManager
            val runtime = CCSystem.getAPI().getMenuRuntimeService()
            runtime.suspendForExternal(player)
            val sent = plugin.floodgateFormBridge.sendCustomInputForm(
                player = player,
                title = lang.getMessage(player, "gui.tour.waypoint.name_dialog.title"),
                label = lang.getMessage(player, "gui.tour.menu.waypoint.name"),
                placeholder = lang.getMessage(player, "gui.tour.waypoint.name_dialog.description"),
                defaultValue = currentName,
                onSubmit = { raw ->
                    val editSession = waypointNameEdit.remove(player.uniqueId) ?: return@sendCustomInputForm
                    val edit = plugin.tourSessionManager.getEdit(player.uniqueId)
                    val worldData = plugin.worldConfigRepository.findByUuid(editSession.worldUuid)
                    if (edit != null && edit.draft.uuid == editSession.tourUuid && worldData != null) {
                        val name = raw.trim().ifBlank { currentName }
                        if (plugin.tourManager.updateWaypointName(edit, editSession.waypointUuid, name)) {
                            plugin.tourManager.saveEditSession(player, worldData, closeSession = false)
                        }
                    }
                    runtime.finishExternal(player)
                },
                onClosed = {
                    waypointNameEdit.remove(player.uniqueId)
                    runtime.finishExternal(player)
                },
            )
            if (!sent) {
                waypointNameEdit.remove(player.uniqueId)
                runtime.finishExternal(player)
            }
        }

        /**
         * 3行を個別入力にすることで、JEのDialogとBEの案内編集フォームで空行の扱いを揃えます。
         */
        private fun showBedrockWaypointDescriptionForm(
            player: Player,
            plugin: MyWorldManager,
            currentDescription: List<String>,
        ) {
            val lang = plugin.languageManager
            val runtime = CCSystem.getAPI().getMenuRuntimeService()
            runtime.suspendForExternal(player)
            val inputs = (0 until 3).map { index ->
                me.awabi2048.myworldmanager.ui.bedrock.FloodgateFormBridge.CustomFormInput(
                    label = lang.getMessage(
                        player,
                        "gui.tour.waypoint.description_line",
                        mapOf("line" to index + 1),
                    ),
                    defaultValue = currentDescription.getOrNull(index).orEmpty(),
                )
            }
            val sent = plugin.floodgateFormBridge.sendCustomForm(
                player = player,
                title = lang.getMessage(player, "gui.tour.waypoint.description_dialog.title"),
                inputs = inputs,
                onSubmit = { values ->
                    val editSession = waypointDescriptionEdit.remove(player.uniqueId) ?: return@sendCustomForm
                    val edit = plugin.tourSessionManager.getEdit(player.uniqueId)
                    val worldData = plugin.worldConfigRepository.findByUuid(editSession.worldUuid)
                    if (edit != null && edit.draft.uuid == editSession.tourUuid && worldData != null &&
                        plugin.tourManager.updateWaypointDescription(edit, editSession.waypointUuid, values)
                    ) {
                        plugin.tourManager.saveEditSession(player, worldData, closeSession = false)
                    }
                    runtime.finishExternal(player)
                },
                onClosed = {
                    waypointDescriptionEdit.remove(player.uniqueId)
                    runtime.finishExternal(player)
                },
            )
            if (!sent) {
                waypointDescriptionEdit.remove(player.uniqueId)
                runtime.finishExternal(player)
            }
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
                                textEdit.remove(target.uniqueId)
                            } else {
                                signEdit.remove(target.uniqueId)
                            }
                            MenuActionResult.Success(if (tour) MenuUpdate.Resume else MenuUpdate.Close)
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
            val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid)
                ?: return MenuActionResult.Rejected()
            plugin.tourManager.saveEditSession(player, worldData, closeSession = false)
            return MenuActionResult.Success(MenuUpdate.Resume)
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
    }

}
