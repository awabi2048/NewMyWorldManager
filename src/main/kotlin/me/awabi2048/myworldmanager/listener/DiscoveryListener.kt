package me.awabi2048.myworldmanager.listener

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuDialogButton
import com.awabi2048.ccsystem.api.gui.MenuDialogHandler
import com.awabi2048.ccsystem.api.gui.MenuDialogInput
import com.awabi2048.ccsystem.api.gui.MenuDialogRequest
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.myworldmanager.MyWorldManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.Listener

class DiscoveryListener(private val plugin: MyWorldManager) : Listener {
    private fun saveSpotlightDescription(player: Player, rawInput: String): MenuActionResult {
        val lang = plugin.languageManager
        if (!canManageSpotlight(player)) {
            return MenuActionResult.Rejected(
                Component.text(lang.getMessage(player, "general.no_permission")),
            )
        }
        val input = rawInput.trim()
        if (input.length > SPOTLIGHT_DESCRIPTION_MAX_LENGTH) {
            player.sendMessage(
                lang.getMessage(
                    player,
                    "error.discovery_spotlight_description_too_long",
                    mapOf("max" to SPOTLIGHT_DESCRIPTION_MAX_LENGTH),
                ),
            )
            Bukkit.getScheduler().runTask(
                plugin,
                Runnable { openSpotlightDescriptionDialog(player, input) },
            )
            return MenuActionResult.Rejected()
        }
        plugin.spotlightRepository.setDescription(input)
        player.sendMessage(
            lang.getMessage(
                player,
                if (input.isEmpty()) {
                    "messages.discovery_spotlight_description_reset"
                } else {
                    "messages.discovery_spotlight_description_updated"
                },
            ),
        )
        plugin.menuEntryRouter.openDiscovery(player)
        return MenuActionResult.Success(MenuUpdate.Close)
    }

    private fun canManageSpotlight(player: Player): Boolean =
        player.hasPermission("myworldmanager.admin")

    @Suppress("UnstableApiUsage")
    fun openSpotlightDescriptionDialog(player: Player, initialValue: String? = null) {
        val lang = plugin.languageManager
        val currentText = initialValue ?: plugin.spotlightRepository.getDescription().orEmpty()
        CCSystem.getAPI().getMenuDialogService().show(
            player,
            MenuDialogRequest(
                owner = "myworldmanager",
                id = "discovery-spotlight-description",
                title = Component.text(
                    lang.getMessage(player, "gui.discovery.spotlight_description_dialog.title"),
                    NamedTextColor.YELLOW,
                ),
                body = listOf(
                    Component.text(
                        lang.getMessage(
                            player,
                            "gui.discovery.spotlight_description_dialog.body",
                            mapOf("max" to SPOTLIGHT_DESCRIPTION_MAX_LENGTH),
                        ),
                    ),
                ),
                inputs = listOf(
                    MenuDialogInput.Text(
                        "spotlight_description",
                        Component.text(
                            lang.getMessage(
                                player,
                                "gui.discovery.spotlight_description_dialog.input_label",
                            ),
                        ),
                        currentText,
                        maxLength = SPOTLIGHT_DESCRIPTION_MAX_LENGTH,
                    ),
                ),
                confirm = MenuDialogButton(
                    Component.text(
                        lang.getMessage(player, "gui.common.confirm"),
                        NamedTextColor.GREEN,
                    ),
                    MenuDialogHandler { target, response ->
                        saveSpotlightDescription(
                            target,
                            response.textValue("spotlight_description"),
                        )
                    },
                ),
                cancel = MenuDialogButton(
                    Component.text(
                        lang.getMessage(player, "gui.common.cancel"),
                        NamedTextColor.RED,
                    ),
                    MenuDialogHandler { target, _ ->
                        plugin.menuEntryRouter.openDiscovery(target)
                        MenuActionResult.Success(MenuUpdate.Close)
                    },
                ),
            ),
        )
    }

    companion object {
        private const val SPOTLIGHT_DESCRIPTION_MAX_LENGTH = 100
    }
}
