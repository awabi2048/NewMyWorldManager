package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuActionContext
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.listener.WorldSeedListener
import me.awabi2048.myworldmanager.util.GuiItemFactory
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class WorldSeedConfirmGui(private val plugin: MyWorldManager) {
    private val runtime = CCSystem.getAPI().getMenuRuntimeService()

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
        val info = ItemStack(Material.PAPER).also { item ->
            item.editMeta { meta ->
                meta.displayName(lang.getComponent(player, "gui.world_seed_confirm.title"))
                meta.lore(
                    GuiItemFactory.menuLore(
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
                )
            }
        }
        return InventoryMenuView(
            27,
            lang.getComponent(player, "gui.world_seed_confirm.title"),
            listOf(
                MenuElement(13, info, GuiElementRole.CONTENT),
                MenuElement(11, button(player, Material.LIME_CONCRETE, "gui.common.confirm"), GuiElementRole.CONFIRM, ACTION_CONFIRM),
                MenuElement(15, button(player, Material.RED_CONCRETE, "gui.common.cancel"), GuiElementRole.CANCEL, ACTION_CANCEL),
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

    private fun button(player: Player, material: Material, key: String): ItemStack =
        ItemStack(material).also { item ->
            item.editMeta { meta -> meta.displayName(plugin.languageManager.getComponent(player, key)) }
        }

    private companion object {
        private const val OWNER = "myworldmanager"
        private const val ROUTE = "world-seed-confirm"
        private const val ACTION_CONFIRM = "confirm"
        private const val ACTION_CANCEL = "cancel"
        private const val CURRENT_SLOTS = "current_slots"
        private const val NEXT_SLOTS = "next_slots"
    }
}
