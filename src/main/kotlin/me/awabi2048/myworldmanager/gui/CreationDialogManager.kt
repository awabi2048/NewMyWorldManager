@file:Suppress("REDUNDANT_CALL_OF_CONVERSION_METHOD")

package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.api.localization.generated.CommonKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldGuiCreationKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldMessagesKeys


import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuDialogButton
import com.awabi2048.ccsystem.api.gui.MenuDialogHandler
import com.awabi2048.ccsystem.api.gui.MenuDialogInput
import com.awabi2048.ccsystem.api.gui.MenuDialogRequest
import com.awabi2048.ccsystem.api.gui.MenuDialogResponse
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.session.PreviewSource
import me.awabi2048.myworldmanager.session.WorldCreationPhase
import me.awabi2048.myworldmanager.session.WorldCreationSession
import me.awabi2048.myworldmanager.session.WorldCreationType
import me.awabi2048.myworldmanager.session.PreviewSessionManager
import me.awabi2048.myworldmanager.session.WorldSpawnCoordinates
import me.awabi2048.myworldmanager.util.WorldNameValidation
import me.awabi2048.myworldmanager.util.WorldRuntimePolicies
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

/**
 * ワールド作成時のダイアログ入力を管理するクラス
 * ベータ機能として、チャット入力の代わりにダイアログを使用する
 */
class CreationDialogManager {


    companion object {
        /**
         * ワールド名入力ダイアログを表示
         */
        fun showNameInputDialog(player: Player, session: WorldCreationSession, errorMessage: Component? = null) {
            val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
            val lang = plugin.languageManager
            // 入力指示は入力欄の枠タイトルに集約し、同じ文面を本文へ重複表示しない。
            CCSystem.getAPI().getMenuDialogService().show(
                player,
                MenuDialogRequest(
                    owner = "myworldmanager",
                    id = "creation-name-input",
                    title = LegacyComponentSerializer.legacySection()
                        .deserialize(lang.getMessage(player, MyworldGuiCreationKeys.GUI_CREATION_DIALOG_NAME_TITLE)),
                    body = listOfNotNull(errorMessage),
                    inputs = listOf(
                        MenuDialogInput.Text(
                            "world_name",
                            lang.getComponent(player, MyworldMessagesKeys.MESSAGES_WIZARD_NAME_PROMPT),
                            session.worldName ?: "",
                            maxLength = 32,
                        ),
                    ),
                    confirm = MenuDialogButton(
                        lang.getComponent(player, MyworldGuiCreationKeys.GUI_CREATION_CONFIRM_PROCEED_BUTTON),
                        MenuDialogHandler { target, response ->
                            applyWorldName(target, session, plugin, response.textValue("world_name"))
                        },
                    ),
                    cancel = MenuDialogButton(
                        lang.getComponent(player, MyworldGuiCreationKeys.GUI_CREATION_CONFIRM_ACTION_BACK),
                        MenuDialogHandler { target, _ ->
                            backFromName(target, session)
                        },
                    ),
                ),
            )
        }

        /**
         * シード値入力ダイアログを表示
         */
        fun showSeedInputDialog(player: Player, session: WorldCreationSession, errorMessage: String? = null) {
            val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
            val lang = plugin.languageManager
            // 入力指示は入力欄の枠タイトルに集約し、本文は検証エラー専用にする。
            CCSystem.getAPI().getMenuDialogService().show(
                player,
                MenuDialogRequest(
                    owner = "myworldmanager",
                    id = "creation-seed-input",
                    title = Component.text(
                        lang.getMessage(player, MyworldGuiCreationKeys.GUI_CREATION_DIALOG_SEED_TITLE),
                        NamedTextColor.YELLOW,
                    ),
                    body = errorMessage
                        ?.takeIf(String::isNotBlank)
                        ?.let { listOf(LegacyComponentSerializer.legacySection().deserialize(it)) }
                        .orEmpty(),
                    inputs = listOf(
                        MenuDialogInput.Text(
                            "seed_value",
                            lang.getComponent(player, MyworldMessagesKeys.MESSAGES_WIZARD_SEED_PROMPT),
                            session.inputSeedString ?: "",
                        ),
                    ),
                    confirm = MenuDialogButton(
                        lang.getComponent(player, MyworldGuiCreationKeys.GUI_CREATION_CONFIRM_PROCEED_BUTTON),
                        MenuDialogHandler { target, response ->
                            applySeed(target, session, plugin, response.textValue("seed_value"))
                        },
                    ),
                    cancel = MenuDialogButton(
                        lang.getComponent(player, MyworldGuiCreationKeys.GUI_CREATION_CONFIRM_ACTION_BACK),
                        MenuDialogHandler { _, _ ->
                            session.phase = WorldCreationPhase.TYPE_SELECT
                            // 入力画面を開く前の種類選択画面へ戻るだけなので、履歴を増やさず復帰する。
                            MenuActionResult.Success(MenuUpdate.Resume)
                        },
                    ),
                ),
            )
        }

        fun showSpawnLocationInputDialog(
            player: Player,
            session: WorldCreationSession,
            errorMessage: String? = null
        ) {
            val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
            val lang = plugin.languageManager
            val body = mutableListOf(
                lang.getComponent(player, MyworldGuiCreationKeys.GUI_CREATION_CONFIRM_SPAWN_LOCATION_INPUT_HELP)
            )
            if (!errorMessage.isNullOrBlank()) {
                body += LegacyComponentSerializer.legacySection().deserialize(errorMessage)
            }
            val coordinates = session.spawnCoordinates
            CCSystem.getAPI().getMenuDialogService().show(
                player,
                MenuDialogRequest(
                    owner = "myworldmanager",
                    id = "creation-spawn-input",
                    title = lang.getComponent(player, MyworldGuiCreationKeys.GUI_CREATION_CONFIRM_SPAWN_LOCATION_INPUT_TITLE),
                    body = body,
                    inputs = listOf(
                        MenuDialogInput.Text(
                            "spawn_x",
                            lang.getComponent(
                                player,
                                MyworldGuiCreationKeys.GUI_CREATION_CONFIRM_SPAWN_LOCATION_INPUT_AXIS,
                                mapOf("axis" to "X"),
                            ),
                            coordinates?.x?.toString() ?: "",
                        ),
                        MenuDialogInput.Text(
                            "spawn_y",
                            lang.getComponent(
                                player,
                                MyworldGuiCreationKeys.GUI_CREATION_CONFIRM_SPAWN_LOCATION_INPUT_AXIS,
                                mapOf("axis" to "Y"),
                            ),
                            coordinates?.y?.toString() ?: "",
                        ),
                        MenuDialogInput.Text(
                            "spawn_z",
                            lang.getComponent(
                                player,
                                MyworldGuiCreationKeys.GUI_CREATION_CONFIRM_SPAWN_LOCATION_INPUT_AXIS,
                                mapOf("axis" to "Z"),
                            ),
                            coordinates?.z?.toString() ?: "",
                        ),
                    ),
                    confirm = MenuDialogButton(
                        lang.getComponent(player, MyworldGuiCreationKeys.GUI_CREATION_CONFIRM_SPAWN_LOCATION_INPUT_APPLY),
                        MenuDialogHandler { target, response ->
                            applySpawnCoordinates(target, session, plugin, response)
                        },
                    ),
                    cancel = MenuDialogButton(
                        lang.getComponent(player, MyworldGuiCreationKeys.GUI_CREATION_CONFIRM_SPAWN_LOCATION_INPUT_BACK),
                        MenuDialogHandler { _, _ ->
                            session.phase = WorldCreationPhase.CONFIRM
                            // 確認画面はダイアログ表示前の現在Routeであり、通常遷移ではなく復帰する。
                            MenuActionResult.Success(MenuUpdate.Resume)
                        },
                    ),
                ),
            )
        }

        /**
         * 最終確認ダイアログを表示
         */
        fun showConfirmationDialog(player: Player, session: WorldCreationSession) {
            val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
            if (plugin.isEnabled) {
                plugin.creationGui.openConfirmation(player, session)
                return
            }
            val lang = plugin.languageManager
            val config = plugin.config

            val typeName = when (session.creationType) {
                WorldCreationType.TEMPLATE -> lang.getMessage(player, MyworldGuiCreationKeys.GUI_CREATION_TYPE_TEMPLATE_NAME)
                WorldCreationType.SEED -> lang.getMessage(player, MyworldGuiCreationKeys.GUI_CREATION_TYPE_SEED_NAME)
                WorldCreationType.RANDOM -> lang.getMessage(player, MyworldGuiCreationKeys.GUI_CREATION_TYPE_RANDOM_NAME)
                else -> lang.getMessage(player, CommonKeys.GENERAL_UNKNOWN)
            }

            val cost = session.creationType?.let { WorldRuntimePolicies.creationCost(config, it) } ?: 0

            val cleanedName = session.worldName ?: lang.getMessage(player, CommonKeys.GENERAL_UNKNOWN)

            val templateValue = if (session.creationType == WorldCreationType.TEMPLATE) {
                val template = session.templateId?.let(plugin.templateRepository::findById)
                template?.name ?: (session.templateId ?: lang.getMessage(player, CommonKeys.GENERAL_UNKNOWN))
            } else null

            val seedValue = if (session.creationType == WorldCreationType.SEED) session.inputSeedString ?: "" else null
            val seedDimensionValue = if (session.creationType == WorldCreationType.SEED) {
                val dimensionKey = when (session.seedEnvironment) {
                    org.bukkit.World.Environment.NORMAL -> "normal"
                    org.bukkit.World.Environment.NETHER -> "nether"
                    org.bukkit.World.Environment.THE_END -> "the_end"
                    else -> "normal"
                }
                lang.getMessage(player, "gui.creation.confirm.dimension.options.$dimensionKey")
            } else null

            val nameLabel = lang.getMessage(player, MyworldGuiCreationKeys.GUI_CREATION_CONFIRM_NAME_LABEL)
            val typeLabel = lang.getMessage(player, MyworldGuiCreationKeys.GUI_CREATION_CONFIRM_TYPE_LABEL)
            val costLabel = lang.getMessage(player, MyworldGuiCreationKeys.GUI_CREATION_CONFIRM_COST_LABEL)

            val loreLines = mutableListOf<GuiLoreLine>()
            loreLines.add(GuiLoreLine.Data(nameLabel, cleanedName, "§a"))
            loreLines.add(GuiLoreLine.Data(typeLabel, typeName, "§e"))
            templateValue?.let { loreLines.add(GuiLoreLine.Data(lang.getMessage(player, MyworldGuiCreationKeys.GUI_CREATION_CONFIRM_TEMPLATE_LABEL), it, "§f")) }
            if (session.creationType == WorldCreationType.TEMPLATE) {
                val origin = session.templateId
                    ?.let(plugin.templateRepository::findById)
                    ?.originLocation
                loreLines.add(
                    GuiLoreLine.Data(
                        lang.getMessage(player, MyworldGuiCreationKeys.GUI_CREATION_CONFIRM_TEMPLATE_SPAWN_LABEL),
                        origin?.let { "(${it.blockX}, ${it.blockY}, ${it.blockZ})" }
                            ?: lang.getMessage(player, CommonKeys.GENERAL_UNKNOWN),
                        "§6"
                    )
                )
            }
            seedValue?.let { loreLines.add(GuiLoreLine.Data(lang.getMessage(player, MyworldGuiCreationKeys.GUI_CREATION_CONFIRM_SEED_LABEL), it, "§f")) }
            seedDimensionValue?.let { loreLines.add(GuiLoreLine.Data(lang.getMessage(player, MyworldGuiCreationKeys.GUI_CREATION_CONFIRM_DIMENSION_LABEL), it, "§f")) }
            if (MyWorldManagerApi.isWorldPointEconomyEnabled()) {
                loreLines.add(GuiLoreLine.Data(costLabel, "§6🛖 §e$cost", ""))
                val remaining = plugin.playerStatsRepository.findByUuid(player.uniqueId).worldPoint - cost
                loreLines.add(
                    GuiLoreLine.Data(
                        lang.getMessage(player, MyworldGuiCreationKeys.GUI_CREATION_CONFIRM_REMAINING_POINTS_LABEL),
                        "§6🛖 §e${remaining.coerceAtLeast(0)}",
                        ""
                    )
                )
            }

            val bodyLines = CCSystem.getAPI().getLoreService()
                .render(GuiLoreSpec.Rich(loreLines, GuiLoreFrame.BOTH))

            val additionalActions = if (session.creationType == WorldCreationType.TEMPLATE) {
                listOf(
                    MenuDialogButton(
                        lang.getComponent(player, MyworldGuiCreationKeys.GUI_CREATION_CONFIRM_ACTION_PREVIEW),
                        MenuDialogHandler { target, _ ->
                            previewTemplate(target, session, plugin)
                        },
                    ),
                    MenuDialogButton(
                        lang.getComponent(player, MyworldGuiCreationKeys.GUI_CREATION_CONFIRM_CHANGE_TEMPLATE),
                        MenuDialogHandler { target, _ ->
                            session.phase = WorldCreationPhase.TEMPLATE_SELECT
                            plugin.creationGui.openTemplateSelection(target)
                            MenuActionResult.Success(MenuUpdate.None)
                        },
                    ),
                )
            } else {
                emptyList()
            }

            CCSystem.getAPI().getMenuDialogService().show(
                player,
                MenuDialogRequest(
                    owner = "myworldmanager",
                    id = "creation-confirmation",
                    title = LegacyComponentSerializer.legacySection()
                        .deserialize(lang.getMessage(player, MyworldGuiCreationKeys.GUI_CREATION_TITLE_CONFIRM)),
                    body = bodyLines,
                    confirm = MenuDialogButton(
                        lang.getComponent(player, MyworldGuiCreationKeys.GUI_CREATION_CONFIRM_ACTION_CREATE),
                        MenuDialogHandler { target, _ ->
                            performWorldCreation(target, session, plugin)
                            MenuActionResult.Success(MenuUpdate.Close)
                        },
                    ),
                    cancel = MenuDialogButton(
                        lang.getComponent(player, MyworldGuiCreationKeys.GUI_CREATION_CONFIRM_CHANGE_NAME),
                        MenuDialogHandler { target, _ ->
                            session.phase = WorldCreationPhase.NAME_INPUT
                            showNameInputDialog(target, session)
                            MenuActionResult.Success(MenuUpdate.None)
                        },
                    ),
                    additionalActions = additionalActions,
                    columns = 1,
                ),
            )
        }

        private fun previewTemplate(
            player: Player,
            session: WorldCreationSession,
            plugin: MyWorldManager,
        ): MenuActionResult {
            val templateId = session.templateId ?: return MenuActionResult.Rejected()
            session.phase = WorldCreationPhase.CONFIRM
            val started = plugin.previewSessionManager.startPreview(
                player,
                PreviewSessionManager.PreviewTarget.Template(templateId),
                PreviewSource.CREATION_CONFIRM,
            )
            if (!started) {
                org.bukkit.Bukkit.getScheduler().runTask(plugin, Runnable {
                    showConfirmationDialog(player, session)
                })
                return MenuActionResult.Rejected()
            }
            return MenuActionResult.Success(MenuUpdate.None)
        }

        private fun applyWorldName(
            player: Player,
            session: WorldCreationSession,
            plugin: MyWorldManager,
            rawName: String,
        ): MenuActionResult {
            val result = plugin.worldValidator.validateName(rawName)
            if (result is WorldNameValidation.Failure) {
                val message = plugin.languageManager.getComponent(
                    player,
                    result.messageKey,
                    result.placeholders,
                )
                org.bukkit.Bukkit.getScheduler().runTask(plugin, Runnable {
                    showNameInputDialog(player, session, message)
                })
                return MenuActionResult.Rejected()
            }
            val cleanName = cleanWorldName(rawName)
            if (plugin.worldConfigRepository.hasDisplayNameConflict(player.uniqueId, cleanName)) {
                val message = plugin.languageManager.getComponent(player, MyworldMessagesKeys.MESSAGES_WORLD_NAME_DUPLICATE)
                org.bukkit.Bukkit.getScheduler().runTask(plugin, Runnable {
                    showNameInputDialog(player, session, message)
                })
                return MenuActionResult.Rejected()
            }
            session.worldName = cleanName
            if (session.creationType == null) {
                session.phase = WorldCreationPhase.TYPE_SELECT
                plugin.creationGui.openTypeSelection(player)
            } else {
                session.phase = WorldCreationPhase.CONFIRM
                plugin.creationGui.openConfirmation(player, session)
            }
            return MenuActionResult.Success(MenuUpdate.None)
        }

        private fun backFromName(
            player: Player,
            session: WorldCreationSession,
        ): MenuActionResult = when (session.creationType) {
            WorldCreationType.TEMPLATE -> {
                // JEは一覧、BEは詳細画面がダイアログ表示前のRouteなので、どちらも履歴を増やさず復帰する。
                session.phase = if (session.isDialogMode) {
                    WorldCreationPhase.TEMPLATE_SELECT
                } else {
                    WorldCreationPhase.TEMPLATE_DETAIL
                }
                MenuActionResult.Success(MenuUpdate.Resume)
            }
            WorldCreationType.SEED -> {
                session.phase = WorldCreationPhase.SEED_INPUT
                showSeedInputDialog(player, session)
                // シード入力は別のダイアログへ戻るため、外部入力状態を継続する。
                MenuActionResult.Success(MenuUpdate.None)
            }
            WorldCreationType.RANDOM, null -> {
                session.phase = WorldCreationPhase.TYPE_SELECT
                MenuActionResult.Success(MenuUpdate.Resume)
            }
            }

        private fun applySeed(
            player: Player,
            session: WorldCreationSession,
            plugin: MyWorldManager,
            seed: String,
        ): MenuActionResult {
            if (seed.isBlank()) {
                val message = plugin.languageManager.getMessage(
                    player,
                    MyworldGuiCreationKeys.GUI_CREATION_DIALOG_SEED_REQUIRED,
                )
                org.bukkit.Bukkit.getScheduler().runTask(plugin, Runnable {
                    showSeedInputDialog(player, session, message)
                })
                return MenuActionResult.Rejected()
            }
            session.inputSeedString = seed
            session.phase = WorldCreationPhase.NAME_INPUT
            showNameInputDialog(player, session)
            return MenuActionResult.Success(MenuUpdate.None)
        }

        private fun applySpawnCoordinates(
            player: Player,
            session: WorldCreationSession,
            plugin: MyWorldManager,
            response: MenuDialogResponse,
        ): MenuActionResult {
            when (
                val result = WorldSpawnCoordinates.parse(
                    response.textValue("spawn_x"),
                    response.textValue("spawn_y"),
                    response.textValue("spawn_z"),
                )
            ) {
                is WorldSpawnCoordinates.ParseResult.Valid -> session.spawnCoordinates = result.coordinates
                WorldSpawnCoordinates.ParseResult.Unset -> session.spawnCoordinates = null
                WorldSpawnCoordinates.ParseResult.InvalidNumber -> {
                    val message = plugin.languageManager.getMessage(
                        player,
                        MyworldGuiCreationKeys.GUI_CREATION_CONFIRM_SPAWN_LOCATION_ERROR_NUMBER,
                    )
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, Runnable {
                        showSpawnLocationInputDialog(player, session, message)
                    })
                    return MenuActionResult.Rejected()
                }
                WorldSpawnCoordinates.ParseResult.OutOfRange -> {
                    val message = plugin.languageManager.getMessage(
                        player,
                        MyworldGuiCreationKeys.GUI_CREATION_CONFIRM_SPAWN_LOCATION_ERROR_RANGE,
                    )
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, Runnable {
                        showSpawnLocationInputDialog(player, session, message)
                    })
                    return MenuActionResult.Rejected()
                }
            }
            session.phase = WorldCreationPhase.CONFIRM
            plugin.creationGui.openConfirmation(player, session)
            return MenuActionResult.Success(MenuUpdate.None)
        }

        private fun performWorldCreation(
            player: Player,
            session: WorldCreationSession,
            plugin: MyWorldManager
        ) {
            val cost = session.creationType?.let { WorldRuntimePolicies.creationCost(plugin.config, it) } ?: 0

            val name = session.worldName ?: "New World"
            if (plugin.worldConfigRepository.hasDisplayNameConflict(player.uniqueId, name)) {
                player.sendMessage(plugin.languageManager.getMessage(player, MyworldMessagesKeys.MESSAGES_WORLD_NAME_DUPLICATE))
                return
            }

            when (session.creationType) {
                WorldCreationType.TEMPLATE -> {
                    val templateId = session.templateId ?: return
                    plugin.worldService.createWorld(templateId, player.uniqueId, name, cost, session.billingMode)
                }
                WorldCreationType.SEED -> {
                    val seedStr = session.inputSeedString ?: ""
                    plugin.worldService.generateWorld(
                        player.uniqueId,
                        name,
                        seedStr,
                         cost,
                         session.spawnCoordinates,
                         session.seedEnvironment,
                         session.billingMode
                    )
                }
                WorldCreationType.RANDOM -> {
                    plugin.worldService.generateWorld(
                        player.uniqueId,
                        name,
                        null,
                        cost,
                        billingMode = session.billingMode
                    )
                }
                else -> {}
            }

            plugin.creationSessionManager.endSession(player.uniqueId)
            CCSystem.getAPI().getMenuRuntimeService().close(player)
        }

        fun cleanWorldName(name: String): String {
            val regex = Regex("\\s?\\(.*?\\)")
            return name.replace(regex, "").trim()
        }

        fun safeCloseDialog(player: Player) {
            try {
                val method = try {
                    player.javaClass.getMethod("closeDialog")
                } catch (e: NoSuchMethodException) {
                    Player::class.java.getMethod("closeDialog")
                }
                method.invoke(player)
            } catch (e: Exception) {
            }
        }
    }
}
