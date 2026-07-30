package me.awabi2048.myworldmanager.listener

import com.awabi2048.ccsystem.CCSystem

import com.awabi2048.ccsystem.api.gui.GuiCycle
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.api.service.WorldPointBillingMode
import me.awabi2048.myworldmanager.gui.CreationGui
import me.awabi2048.myworldmanager.model.*
import me.awabi2048.myworldmanager.repository.*
import me.awabi2048.myworldmanager.session.*
import me.awabi2048.myworldmanager.util.WorldNameValidation
import me.awabi2048.myworldmanager.util.WorldRuntimePolicies
import me.awabi2048.myworldmanager.util.WorldCreationChecks
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType

class CreationGuiListener(private val plugin: MyWorldManager) {

    fun handleConfirmationAction(
        player: Player,
        click: ClickType,
        action: CreationConfirmationAction,
    ): MenuActionResult {
        val session = plugin.creationSessionManager.getSession(player.uniqueId)
            ?: return MenuActionResult.Ignored
        if (session.phase != WorldCreationPhase.CONFIRM) return MenuActionResult.Ignored
        val lang = plugin.languageManager

        if (action == CreationConfirmationAction.BACK) {
            session.phase = WorldCreationPhase.NAME_INPUT
            CCSystem.getAPI().getMenuRuntimeService().close(player)
            openNameInputByPlatform(player, session)
            return MenuActionResult.Success(MenuUpdate.None)
        }
        if (action == CreationConfirmationAction.DIMENSION &&
            session.creationType == WorldCreationType.SEED
        ) {
            session.seedEnvironment = GuiCycle.select(
                session.seedEnvironment,
                listOf(
                    org.bukkit.World.Environment.NORMAL,
                    org.bukkit.World.Environment.NETHER,
                    org.bukkit.World.Environment.THE_END
                ),
                GuiCycle.direction(click) ?: return MenuActionResult.Ignored
            )
            return MenuActionResult.Success(MenuUpdate.Refresh)
        }
        if (action == CreationConfirmationAction.SPAWN_LOCATION &&
            session.creationType == WorldCreationType.SEED
        ) {
            session.phase = WorldCreationPhase.SPAWN_INPUT
            CCSystem.getAPI().getMenuRuntimeService().close(player)
            openSpawnInputByPlatform(player, session)
            return MenuActionResult.Success(MenuUpdate.None)
        }
        if (action == CreationConfirmationAction.TEMPLATE_PREVIEW &&
            session.creationType == WorldCreationType.TEMPLATE
        ) {
            val templateId = session.templateId ?: return MenuActionResult.Ignored
            CCSystem.getAPI().getMenuRuntimeService().close(player)
            plugin.previewSessionManager.startPreview(
                player,
                PreviewSessionManager.PreviewTarget.Template(templateId),
                PreviewSource.CREATION_CONFIRM
            )
            return MenuActionResult.Success(MenuUpdate.None)
        }
        if (action == CreationConfirmationAction.TEMPLATE_CHANGE &&
            session.creationType == WorldCreationType.TEMPLATE
        ) {
            session.phase = WorldCreationPhase.TEMPLATE_SELECT
            plugin.creationGui.openTemplateSelection(player)
            return MenuActionResult.Success(MenuUpdate.None)
        }
        if (action == CreationConfirmationAction.CANCEL) {
            CCSystem.getAPI().getMenuRuntimeService().close(player)
            cancelAndReturnToMyWorld(player)
            return MenuActionResult.Success(MenuUpdate.None)
        }
        if (action != CreationConfirmationAction.CONFIRM) return MenuActionResult.Ignored

        CCSystem.getAPI().getMenuRuntimeService().close(player)
        val adminCommandSession =
            session.extras[CreationGui.ADMIN_COMMAND_SESSION_KEY] == true
        if (!WorldCreationChecks.checkSelfCreatePermission(
                player,
                allowAdminCommandSession = adminCommandSession
            )
        ) {
            plugin.creationSessionManager.endSession(player.uniqueId)
            return MenuActionResult.Success(MenuUpdate.Close)
        }
        if (!WorldCreationChecks.check(player, type = session.creationType)) {
            plugin.creationSessionManager.endSession(player.uniqueId)
            return MenuActionResult.Success(MenuUpdate.Close)
        }

        val cost = session.creationType?.let {
            WorldRuntimePolicies.creationCost(plugin.config, it)
        } ?: 0
        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        if (session.billingMode == WorldPointBillingMode.STANDARD &&
            MyWorldManagerApi.isWorldPointEconomyEnabled() &&
            stats.worldPoint < cost
        ) {
            player.sendMessage(lang.getMessage(player, "messages.creation_insufficient_points"))
            plugin.creationSessionManager.endSession(player.uniqueId)
            return MenuActionResult.Success(MenuUpdate.Close)
        }

        player.sendMessage(lang.getMessage(player, "messages.world_creation_processing"))
        when (session.creationType) {
            WorldCreationType.TEMPLATE -> {
                plugin.worldService.createWorld(
                    session.templateId!!,
                    player.uniqueId,
                    session.worldName!!,
                    cost,
                    session.billingMode
                ).thenAccept { success ->
                    if (!success) {
                        player.sendMessage(lang.getMessage(player, "messages.creation_failed"))
                    }
                }
            }
            WorldCreationType.SEED -> {
                plugin.worldService.generateWorld(
                    player.uniqueId,
                    session.worldName!!,
                    session.inputSeedString,
                    cost,
                    session.spawnCoordinates,
                    session.seedEnvironment,
                    session.billingMode
                ).thenAccept { success ->
                    player.sendMessage(
                        lang.getMessage(
                            player,
                            if (success) "messages.creation_success" else "messages.creation_failed"
                        )
                    )
                }
            }
            WorldCreationType.RANDOM -> {
                plugin.worldService.generateWorld(
                    player.uniqueId,
                    session.worldName!!,
                    null,
                    cost,
                    billingMode = session.billingMode
                ).thenAccept { success ->
                    player.sendMessage(
                        lang.getMessage(
                            player,
                            if (success) "messages.creation_success" else "messages.creation_failed"
                        )
                    )
                }
            }
            null -> {}
        }
        plugin.creationSessionManager.endSession(player.uniqueId)
        return MenuActionResult.Success(MenuUpdate.Close)
    }

    fun openSeedInputByPlatform(
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

    fun openNameInputByPlatform(player: Player, session: WorldCreationSession, errorMessage: Component? = null) {
        if (!plugin.playerPlatformResolver.isBedrock(player)) {
            me.awabi2048.myworldmanager.gui.CreationDialogManager.showNameInputDialog(player, session, errorMessage)
            return
        }

        val lang = plugin.languageManager
        val plainSerializer = PlainTextComponentSerializer.plainText()
        val label = buildString {
            append(lang.getMessage(player, "gui.bedrock.input.creation_name.label"))
            errorMessage?.let {
                append("\n")
                append(plainSerializer.serialize(it))
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
                    val result = plugin.worldValidator.validateName(value)
                    if (result is WorldNameValidation.Failure) {
                        val errorComponent = plugin.languageManager.getComponent(player, result.messageKey, result.placeholders)
                        if (!plugin.playerPlatformResolver.isBedrock(player)) {
                            me.awabi2048.myworldmanager.gui.CreationDialogManager.showNameInputDialog(player, latest, errorComponent)
                        } else {
                            openNameInputByPlatform(player, latest, errorComponent)
                        }
                        return@Runnable
                    }

                    val cleanName = me.awabi2048.myworldmanager.gui.CreationDialogManager.cleanWorldName(value)
                    if (plugin.worldConfigRepository.findByOwnerAndDisplayName(player.uniqueId, cleanName) != null) {
                        val errorComponent = plugin.languageManager.getComponent(player, "messages.world_name_duplicate")
                        if (!plugin.playerPlatformResolver.isBedrock(player)) {
                            me.awabi2048.myworldmanager.gui.CreationDialogManager.showNameInputDialog(player, latest, errorComponent)
                        } else {
                            openNameInputByPlatform(player, latest, errorComponent)
                        }
                        return@Runnable
                    }

                    latest.worldName = cleanName
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

    fun cancelAndReturnToMyWorld(player: Player) {
        plugin.creationSessionManager.endSession(player.uniqueId)
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (player.isOnline) plugin.menuEntryRouter.openPlayerWorld(player, 0, false)
        })
    }
}

enum class CreationConfirmationAction {
    BACK,
    DIMENSION,
    SPAWN_LOCATION,
    TEMPLATE_PREVIEW,
    TEMPLATE_CHANGE,
    CANCEL,
    CONFIRM,
}
