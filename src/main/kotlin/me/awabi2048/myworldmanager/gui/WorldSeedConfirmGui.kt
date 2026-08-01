package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiMenuDisplaySpec
import com.awabi2048.ccsystem.api.gui.GuiMenuActionIntent
import com.awabi2048.ccsystem.api.gui.GuiMenuEntrySpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuGesture
import com.awabi2048.ccsystem.api.gui.MenuActionContext
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuActionSafety
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.listener.WorldSeedListener
import org.bukkit.Material
import org.bukkit.entity.Player

class WorldSeedConfirmGui(private val plugin: MyWorldManager) {
    private val runtime = CCSystem.getAPI().getMenuRuntimeService()
    private val guiElements = CCSystem.getAPI().getGuiElementService()

    init {
        runtime.register(
            InventoryMenuDefinition(
                owner = OWNER,
                id = ROUTE,
                renderer = { context -> render(context.player, context.route) },
                actions = mapOf(
                    ACTION_CONFIRM to MenuActionHandler(::confirm),
                    ACTION_CANCEL to MenuActionHandler { MenuActionResult.Success(MenuUpdate.Close) },
                ),
            ),
        )
    }

    fun open(player: Player, currentSlots: Int, nextSlots: Int) {
        runtime.open(
            player,
            MenuRoute(
                OWNER,
                ROUTE,
                mapOf(CURRENT_SLOTS to currentSlots.toString(), NEXT_SLOTS to nextSlots.toString()),
            ),
        )
    }

    private fun render(player: Player, route: MenuRoute): InventoryMenuView {
        val lang = plugin.languageManager
        val currentSlots = route.payload[CURRENT_SLOTS]?.toIntOrNull() ?: 0
        val nextSlots = route.payload[NEXT_SLOTS]?.toIntOrNull() ?: 0
        val info = guiElements.menuDisplay(
            GuiMenuDisplaySpec(
                slot = 13,
                item = GuiItemSpec(
                    material = Material.PAPER,
                    name = GuiNameSpec.Component(lang.getComponent(player, "gui.world_seed_confirm.title")),
                    lore = GuiLoreSpec.Blocks(
                        listOf(
                            GuiLoreBlock(
                                listOf(
                                    GuiLoreLine.Warning(lang.getMessage(player, "gui.world_seed_confirm.question")),
                                    GuiLoreLine.Data(
                                        lang.getMessage(player, "gui.world_seed_confirm.current_label"),
                                        currentSlots,
                                        "§a",
                                    ),
                                    GuiLoreLine.Data(
                                        lang.getMessage(player, "gui.world_seed_confirm.next_label"),
                                        nextSlots,
                                        "§a",
                                    ),
                                ) + lang.getMessageList(player, "gui.world_seed_confirm.description")
                                    .map(GuiLoreLine::Text),
                            ),
                        ),
                    ),
                    role = GuiElementRole.CONTENT,
                    amount = 1,
                ),
            ),
        )
        return InventoryMenuView(
            27,
            lang.getComponent(player, "gui.world_seed_confirm.title"),
            listOf(
                info,
                button(player, 11, Material.LIME_CONCRETE, "gui.common.confirm", GuiElementRole.CONFIRM, ACTION_CONFIRM),
                button(player, 15, Material.RED_CONCRETE, "gui.common.cancel", GuiElementRole.CANCEL, ACTION_CANCEL),
            ),
        )
    }

    private fun confirm(context: MenuActionContext): MenuActionResult {
        return if (WorldSeedListener.expandWorldSlot(plugin, context.player)) {
            MenuActionResult.Success(MenuUpdate.Close)
        } else {
            MenuActionResult.Rejected()
        }
    }

    private fun button(
        player: Player,
        slot: Int,
        material: Material,
        key: String,
        role: GuiElementRole,
        actionId: String,
    ): MenuElement = guiElements.menuEntry(
        player,
        GuiMenuEntrySpec(
            slot = slot,
            material = material,
            name = GuiNameSpec.Component(plugin.languageManager.getComponent(player, key)),
            role = role,
            actions = listOf(
                menuGestureAction(
                    actionId,
                    MenuGesture.ANY,
                    plugin.languageManager.getMessage(player, key),
                    safety = when (actionId) {
                        ACTION_CONFIRM -> MenuActionSafety.IRREVERSIBLE
                        ACTION_CANCEL -> MenuActionSafety.NAVIGATION_ONLY
                        else -> error("Unknown world seed confirmation action safety: $actionId")
                    },
                ),
            ),
        ),
    )

    private companion object {
        private const val OWNER = "myworldmanager"
        private const val ROUTE = "world-seed-confirm"
        private const val ACTION_CONFIRM = "confirm"
        private const val ACTION_CANCEL = "cancel"
        private const val CURRENT_SLOTS = "current_slots"
        private const val NEXT_SLOTS = "next_slots"
    }
}
