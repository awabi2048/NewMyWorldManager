@file:Suppress("REDUNDANT_CALL_OF_CONVERSION_METHOD")

package me.awabi2048.myworldmanager.gui


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
                        .deserialize(lang.getMessage(player, "gui.creation.dialog.name_title")),
                    body = listOfNotNull(errorMessage),
                    inputs = listOf(
                        MenuDialogInput.Text(
                            "world_name",
                            lang.getComponent(player, "messages.wizard_name_prompt"),
                            session.worldName ?: "",
                            maxLength = 32,
                        ),
                    ),
                    confirm = MenuDialogButton(
                        lang.getComponent(player, "gui.creation.confirm.proceed_button"),
                        MenuDialogHandler { target, response ->
                            applyWorldName(target, session, plugin, response.textValue("world_name"))
                        },
                    ),
                    cancel = MenuDialogButton(
                        lang.getComponent(player, "gui.creation.confirm.action_back"),
                        MenuDialogHandler { target, _ ->
                            backFromName(target, session, plugin)
                            MenuActionResult.Success(MenuUpdate.None)
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
                        lang.getMessage(player, "gui.creation.dialog.seed_title"),
                        NamedTextColor.YELLOW,
                    ),
                    body = errorMessage
                        ?.takeIf(String::isNotBlank)
                        ?.let { listOf(LegacyComponentSerializer.legacySection().deserialize(it)) }
                        .orEmpty(),
                    inputs = listOf(
                        MenuDialogInput.Text(
                            "seed_value",
                            lang.getComponent(player, "messages.wizard_seed_prompt"),
                            session.inputSeedString ?: "",
                        ),
                    ),
                    confirm = MenuDialogButton(
                        lang.getComponent(player, "gui.creation.confirm.proceed_button"),
                        MenuDialogHandler { target, response ->
                            applySeed(target, session, plugin, response.textValue("seed_value"))
                        },
                    ),
                    cancel = MenuDialogButton(
                        lang.getComponent(player, "gui.creation.confirm.action_back"),
                        MenuDialogHandler { target, _ ->
                            session.phase = WorldCreationPhase.TYPE_SELECT
                            plugin.creationGui.openTypeSelection(target)
                            MenuActionResult.Success(MenuUpdate.None)
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
                lang.getComponent(player, "gui.creation.confirm.spawn_location.input.help")
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
                    title = lang.getComponent(player, "gui.creation.confirm.spawn_location.input.title"),
                    body = body,
                    inputs = listOf(
                        MenuDialogInput.Text(
                            "spawn_x",
                            lang.getComponent(
                                player,
                                "gui.creation.confirm.spawn_location.input.axis",
                                mapOf("axis" to "X"),
                            ),
                            coordinates?.x?.toString() ?: "",
                        ),
                        MenuDialogInput.Text(
                            "spawn_y",
                            lang.getComponent(
                                player,
                                "gui.creation.confirm.spawn_location.input.axis",
                                mapOf("axis" to "Y"),
                            ),
                            coordinates?.y?.toString() ?: "",
                        ),
                        MenuDialogInput.Text(
                            "spawn_z",
                            lang.getComponent(
                                player,
                                "gui.creation.confirm.spawn_location.input.axis",
                                mapOf("axis" to "Z"),
                            ),
                            coordinates?.z?.toString() ?: "",
                        ),
                    ),
                    confirm = MenuDialogButton(
                        lang.getComponent(player, "gui.creation.confirm.spawn_location.input.apply"),
                        MenuDialogHandler { target, response ->
                            applySpawnCoordinates(target, session, plugin, response)
                        },
                    ),
                    cancel = MenuDialogButton(
                        lang.getComponent(player, "gui.creation.confirm.spawn_location.input.back"),
                        MenuDialogHandler { target, _ ->
                            session.phase = WorldCreationPhase.CONFIRM
                            plugin.creationGui.openConfirmation(target, session)
                            MenuActionResult.Success(MenuUpdate.None)
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
                WorldCreationType.TEMPLATE -> lang.getMessage(player, "gui.creation.type.template.name")
                WorldCreationType.SEED -> lang.getMessage(player, "gui.creation.type.seed.name")
                WorldCreationType.RANDOM -> lang.getMessage(player, "gui.creation.type.random.name")
                else -> lang.getMessage(player, "general.unknown")
            }

            val cost = session.creationType?.let { WorldRuntimePolicies.creationCost(config, it) } ?: 0

            val cleanedName = session.worldName ?: lang.getMessage(player, "general.unknown")

            val templateValue = if (session.creationType == WorldCreationType.TEMPLATE) {
                val template = session.templateId?.let(plugin.templateRepository::findById)
                template?.name ?: (session.templateId ?: lang.getMessage(player, "general.unknown"))
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

            val nameLabel = lang.getMessage(player, "gui.creation.confirm.name_label")
            val typeLabel = lang.getMessage(player, "gui.creation.confirm.type_label")
            val costLabel = lang.getMessage(player, "gui.creation.confirm.cost_label")

            val loreLines = mutableListOf<GuiLoreLine>()
            loreLines.add(GuiLoreLine.Data(nameLabel, cleanedName, "§a"))
            loreLines.add(GuiLoreLine.Data(typeLabel, typeName, "§e"))
            templateValue?.let { loreLines.add(GuiLoreLine.Data(lang.getMessage(player, "gui.creation.confirm.template_label"), it, "§f")) }
            if (session.creationType == WorldCreationType.TEMPLATE) {
                val origin = session.templateId
                    ?.let(plugin.templateRepository::findById)
                    ?.originLocation
                loreLines.add(
                    GuiLoreLine.Data(
                        lang.getMessage(player, "gui.creation.confirm.template_spawn_label"),
                        origin?.let { "(${it.blockX}, ${it.blockY}, ${it.blockZ})" }
                            ?: lang.getMessage(player, "general.unknown"),
                        "§6"
                    )
                )
            }
            seedValue?.let { loreLines.add(GuiLoreLine.Data(lang.getMessage(player, "gui.creation.confirm.seed_label"), it, "§f")) }
            seedDimensionValue?.let { loreLines.add(GuiLoreLine.Data(lang.getMessage(player, "gui.creation.confirm.dimension_label"), it, "§f")) }
            if (MyWorldManagerApi.isWorldPointEconomyEnabled()) {
                loreLines.add(GuiLoreLine.Data(costLabel, "§6🛖 §e$cost", ""))
                val remaining = plugin.playerStatsRepository.findByUuid(player.uniqueId).worldPoint - cost
                loreLines.add(
                    GuiLoreLine.Data(
                        lang.getMessage(player, "gui.creation.confirm.remaining_points_label"),
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
                        lang.getComponent(player, "gui.creation.confirm.action_preview"),
                        MenuDialogHandler { target, _ ->
                            previewTemplate(target, session, plugin)
                        },
                    ),
                    MenuDialogButton(
                        lang.getComponent(player, "gui.creation.confirm.change_template"),
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
                        .deserialize(lang.getMessage(player, "gui.creation.title_confirm")),
                    body = bodyLines,
                    confirm = MenuDialogButton(
                        lang.getComponent(player, "gui.creation.confirm.action_create"),
                        MenuDialogHandler { target, _ ->
                            performWorldCreation(target, session, plugin)
                            MenuActionResult.Success(MenuUpdate.Close)
                        },
                    ),
                    cancel = MenuDialogButton(
                        lang.getComponent(player, "gui.creation.confirm.change_name"),
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
            if (plugin.worldConfigRepository.findByOwnerAndDisplayName(player.uniqueId, cleanName) != null) {
                val message = plugin.languageManager.getComponent(player, "messages.world_name_duplicate")
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
            plugin: MyWorldManager,
        ) {
            when (session.creationType) {
                WorldCreationType.TEMPLATE -> {
                    if (session.isDialogMode) {
                        // JEの一覧から直接名前入力へ進んだ場合は、戻る操作も一覧へ戻します。
                        session.phase = WorldCreationPhase.TEMPLATE_SELECT
                        plugin.creationGui.openTemplateSelection(player)
                    } else {
                        session.phase = WorldCreationPhase.TEMPLATE_DETAIL
                        plugin.creationGui.openTemplateDetail(player, session)
                    }
                }
                WorldCreationType.SEED -> {
                    session.phase = WorldCreationPhase.SEED_INPUT
                    showSeedInputDialog(player, session)
                }
                WorldCreationType.RANDOM, null -> {
                    session.phase = WorldCreationPhase.TYPE_SELECT
                    plugin.creationGui.openTypeSelection(player)
                }
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
                    "gui.creation.dialog.seed_required",
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
                        "gui.creation.confirm.spawn_location.error.number",
                    )
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, Runnable {
                        showSpawnLocationInputDialog(player, session, message)
                    })
                    return MenuActionResult.Rejected()
                }
                WorldSpawnCoordinates.ParseResult.OutOfRange -> {
                    val message = plugin.languageManager.getMessage(
                        player,
                        "gui.creation.confirm.spawn_location.error.range",
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
            if (plugin.worldConfigRepository.findByOwnerAndDisplayName(player.uniqueId, name) != null) {
                player.sendMessage(plugin.languageManager.getMessage(player, "messages.world_name_duplicate"))
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
