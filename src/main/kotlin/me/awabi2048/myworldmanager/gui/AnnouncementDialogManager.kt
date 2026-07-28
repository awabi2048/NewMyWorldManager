package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuDialogButton
import com.awabi2048.ccsystem.api.gui.MenuDialogHandler
import com.awabi2048.ccsystem.api.gui.MenuDialogInput
import com.awabi2048.ccsystem.api.gui.MenuDialogRequest
import com.awabi2048.ccsystem.api.gui.MenuDialogResponse
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.WorldData
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

/**
 * ワールド入場時の案内メッセージを編集する共通Dialogです。
 */
object AnnouncementDialogManager {

    fun showAnnouncementEditDialog(player: Player, worldData: WorldData) {
        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
        val lang = plugin.languageManager
        val maxLines = plugin.config.getInt("announcement.max_lines", 5)
        val maxLength = plugin.config.getInt("announcement.max_line_length", 100)
        val currentMessages = worldData.announcementMessages.map {
            it.removePrefix("§f").replace("§", "&")
        }

        val inputs = (0 until maxLines).map { index ->
            MenuDialogInput.Text(
                id = "announcement_line_$index",
                label = Component.text(
                    lang.getMessage(
                        player,
                        "gui.announcement_dialog.line_label",
                        mapOf("number" to index + 1),
                    ),
                ),
                initial = currentMessages.getOrElse(index) { "" },
                maxLength = maxLength,
            )
        }

        CCSystem.getAPI().getMenuDialogService().show(
            player,
            MenuDialogRequest(
                owner = "myworldmanager",
                id = "announcement-edit",
                title = Component.text(
                    lang.getMessage(player, "gui.announcement_dialog.title"),
                    NamedTextColor.YELLOW,
                ),
                body = listOf(
                    Component.text(
                        lang.getMessage(player, "gui.announcement_dialog.help"),
                        NamedTextColor.GRAY,
                    ),
                    Component.text(
                        lang.getMessage(
                            player,
                            "gui.announcement_dialog.max_lines",
                            mapOf("max" to maxLines),
                        ),
                        NamedTextColor.GRAY,
                    ),
                    Component.text(
                        lang.getMessage(
                            player,
                            "gui.announcement_dialog.max_length",
                            mapOf("max" to maxLength),
                        ),
                        NamedTextColor.GRAY,
                    ),
                ),
                inputs = inputs,
                confirm = MenuDialogButton(
                    label = Component.text(
                        lang.getMessage(player, "gui.announcement_dialog.save"),
                        NamedTextColor.GREEN,
                    ),
                    handler = MenuDialogHandler { editPlayer, response ->
                        save(editPlayer, worldData.uuid, response, maxLines, maxLength)
                    },
                ),
                cancel = MenuDialogButton(
                    label = Component.text(
                        lang.getMessage(player, "gui.announcement_dialog.cancel"),
                        NamedTextColor.RED,
                    ),
                    handler = MenuDialogHandler { cancelPlayer, _ ->
                        returnToSettings(cancelPlayer, worldData.uuid)
                        MenuActionResult.Success(MenuUpdate.Close)
                    },
                ),
            ),
        )
    }

    private fun save(
        player: Player,
        worldUuid: java.util.UUID,
        response: MenuDialogResponse,
        maxLines: Int,
        maxLength: Int,
    ): MenuActionResult {
        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
        val lang = plugin.languageManager
        val worldData = plugin.worldConfigRepository.findByUuid(worldUuid)
            ?: return MenuActionResult.Rejected()
        val blockedStrings = plugin.config.getStringList("announcement.blocked_strings")
        val newMessages = mutableListOf<String>()

        for (index in 0 until maxLines) {
            val line = response.textValue("announcement_line_$index")
            if (line.isBlank()) continue

            val blocked = blockedStrings.firstOrNull { line.contains(it, ignoreCase = true) }
            if (blocked != null) {
                player.sendMessage(
                    lang.getMessage(
                        player,
                        "messages.announcement_blocked_string",
                        mapOf("string" to blocked),
                    ),
                )
                reopenNextTick(plugin, player, worldData)
                return MenuActionResult.Rejected()
            }

            if (line.length > maxLength) {
                player.sendMessage(
                    lang.getMessage(
                        player,
                        "messages.announcement_invalid_length",
                        mapOf("max_lines" to maxLines, "max_length" to maxLength),
                    ),
                )
                reopenNextTick(plugin, player, worldData)
                return MenuActionResult.Rejected()
            }
            newMessages += "§f${line.replace("&", "§")}"
        }

        worldData.announcementMessages.clear()
        worldData.announcementMessages.addAll(newMessages)
        plugin.worldConfigRepository.save(worldData)
        player.sendMessage(lang.getMessage(player, "messages.announcement_set"))
        plugin.soundManager.playActionSound(player, "world_settings", "success")
        Bukkit.getScheduler().runTask(plugin, Runnable {
            plugin.worldSettingsGui.open(player, worldData, replaceCurrent = true)
        })
        return MenuActionResult.Success(MenuUpdate.Close)
    }

    private fun reopenNextTick(plugin: MyWorldManager, player: Player, worldData: WorldData) {
        Bukkit.getScheduler().runTask(plugin, Runnable {
            showAnnouncementEditDialog(player, worldData)
        })
    }

    private fun returnToSettings(player: Player, worldUuid: java.util.UUID) {
        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
        Bukkit.getScheduler().runTask(plugin, Runnable {
            plugin.worldConfigRepository.findByUuid(worldUuid)?.let {
                plugin.worldSettingsGui.open(player, it, replaceCurrent = true)
            }
        })
    }
}
