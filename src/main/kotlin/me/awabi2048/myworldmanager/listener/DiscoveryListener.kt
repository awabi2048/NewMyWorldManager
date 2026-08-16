package me.awabi2048.myworldmanager.listener

import com.awabi2048.ccsystem.api.localization.generated.CommonKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldGuiDiscoveryKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldMessagesKeys

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

class DiscoveryListener(private val plugin: MyWorldManager) {
    private fun saveSpotlightDescription(player: Player, rawInput: String): MenuActionResult {
        val lang = plugin.languageManager
        if (!canManageSpotlight(player)) {
            return MenuActionResult.Rejected(
                Component.text(lang.getMessage(player, CommonKeys.GENERAL_NO_PERMISSION)),
            )
        }
        val input = rawInput.trim()
        if (input.length > SPOTLIGHT_DESCRIPTION_MAX_LENGTH) {
            player.sendMessage(
                lang.getMessage(
                    player,
                    CommonKeys.ERROR_DISCOVERY_SPOTLIGHT_DESCRIPTION_TOO_LONG,
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
                    MyworldMessagesKeys.MESSAGES_DISCOVERY_SPOTLIGHT_DESCRIPTION_RESET
                } else {
                    MyworldMessagesKeys.MESSAGES_DISCOVERY_SPOTLIGHT_DESCRIPTION_UPDATED
                },
            ),
        )
        plugin.menuEntryRouter.openDiscovery(player)
        return MenuActionResult.Success(MenuUpdate.None)
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
                    lang.getMessage(player, MyworldGuiDiscoveryKeys.GUI_DISCOVERY_SPOTLIGHT_DESCRIPTION_DIALOG_TITLE),
                    NamedTextColor.YELLOW,
                ),
                body = listOf(
                    Component.text(
                        lang.getMessage(
                            player,
                            MyworldGuiDiscoveryKeys.GUI_DISCOVERY_SPOTLIGHT_DESCRIPTION_DIALOG_BODY,
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
                                MyworldGuiDiscoveryKeys.GUI_DISCOVERY_SPOTLIGHT_DESCRIPTION_DIALOG_INPUT_LABEL,
                            ),
                        ),
                        currentText,
                        maxLength = SPOTLIGHT_DESCRIPTION_MAX_LENGTH,
                    ),
                ),
                confirm = MenuDialogButton(
                    Component.text(
                        lang.getMessage(player, CommonKeys.GUI_COMMON_CONFIRM),
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
                        lang.getMessage(player, CommonKeys.GUI_COMMON_CANCEL),
                        NamedTextColor.RED,
                    ),
                    MenuDialogHandler { target, _ ->
                        plugin.menuEntryRouter.openDiscovery(target)
                        MenuActionResult.Success(MenuUpdate.None)
                    },
                ),
            ),
        )
    }

    companion object {
        private const val SPOTLIGHT_DESCRIPTION_MAX_LENGTH = 100
    }
}
