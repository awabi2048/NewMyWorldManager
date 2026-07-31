package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiMenuDisplaySpec
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryAction
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryData
import com.awabi2048.ccsystem.api.gui.GuiMenuEntrySpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiValueTone
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuActionContext
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuAcceptedClicks
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import java.util.UUID
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.InviteTargetResolver
import me.awabi2048.myworldmanager.util.WorldAccessMessageResolver
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player

class InviteGui(private val plugin: MyWorldManager) {
    private val runtime = CCSystem.getAPI().getMenuRuntimeService()

    init {
        runtime.register(
            InventoryMenuDefinition(
                owner = OWNER,
                id = ROUTE_ID,
                renderer = { context -> render(context.player, context.route) },
                actions = mapOf(
                    ACTION_BACK to MenuActionHandler(::back),
                    ACTION_PAGE to MenuActionHandler(::changePage),
                    ACTION_INVITE to MenuActionHandler(::invite),
                ),
            ),
        )
    }

    fun collectAvailableTargets(viewer: Player): List<Player> {
        val currentWorldData = plugin.worldConfigRepository.findByWorldName(viewer.world.name)
        return InviteTargetResolver.collectAvailableTargets(plugin, viewer, currentWorldData)
    }

    fun open(player: Player, showBackButton: Boolean = false): Boolean {
        val lang = plugin.languageManager
        val currentWorldData = plugin.worldConfigRepository.findByWorldName(player.world.name)
        if (currentWorldData == null) {
            player.sendMessage(lang.getMessage(player, "messages.invite_not_in_myworld"))
            return false
        }
        if (!MyWorldManagerApi.getWorldAccessPolicy().canInviteToWorld(player, currentWorldData)) {
            player.sendMessage(WorldAccessMessageResolver.inviteToWorld(lang, player, currentWorldData))
            return false
        }

        val targets = InviteTargetResolver.collectAvailableTargets(plugin, player, currentWorldData)
        if (targets.isEmpty()) {
            player.sendMessage(lang.getMessage(player, "messages.invite_no_available_targets"))
            return false
        }
        val route = MenuRoute(
            OWNER,
            ROUTE_ID,
            mapOf(
                WORLD_UUID to currentWorldData.uuid.toString(),
                SHOW_BACK to showBackButton.toString(),
                PAGE to "0",
            ),
        )
        return if (showBackButton) runtime.navigate(player, route) else runtime.open(player, route)
    }

    private fun render(player: Player, route: MenuRoute): InventoryMenuView {
        val lang = plugin.languageManager
        val currentWorldData = route.uuid(WORLD_UUID)?.let(plugin.worldConfigRepository::findByUuid)
            ?: error("招待元ワールドが見つかりません。")
        val targets = InviteTargetResolver.collectAvailableTargets(plugin, player, currentWorldData)
        val title = me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(lang.getMessage(player, "gui.meet.title_list"))
        val pageLayout = CCSystem.getAPI().getGuiLayoutService()
            .sevenColumnPage(targets.size, route.payload[PAGE]?.toIntOrNull() ?: 0)
        val page = pageLayout.page
        val layout = pageLayout.layout
        val pageTargets = targets.drop(pageLayout.startIndex).take(pageLayout.itemCount)
        val elements = mutableListOf<MenuElement>()
        pageTargets.forEachIndexed { index, target ->
            elements += createTargetEntry(target, player, layout.itemSlots[index])
        }

        val statusLore = GuiLoreSpec.Blocks(
            listOf(
                GuiLoreBlock(
                    listOf(
                        GuiLoreLine.Data(
                            lang.getMessage(player, "gui.meet.world_item.current_world"),
                            currentWorldData.name,
                            "§f"
                        )
                    )
                )
            )
        )
        elements += CCSystem.getAPI().getGuiElementService().menuDisplay(
            GuiMenuDisplaySpec(
                slot = 4,
                item = GuiItemSpec(
                    material = currentWorldData.icon,
                    name = GuiNameSpec.Component(
                        lang.getComponent(
                            player,
                            "gui.common.world_item_name",
                            mapOf(
                                "world" to (lang.getMessageStrict(player, currentWorldData.name)
                                    ?: currentWorldData.name),
                            ),
                        ).decoration(TextDecoration.ITALIC, false),
                    ),
                    lore = statusLore,
                    role = GuiElementRole.CONTENT,
                    amount = 1,
                ),
            ),
        )

        if (GuiHelper.canGoBack(player)) {
            elements += backEntry(player, layout.backSlot)
        }
        if (page > 0) {
            elements += navigationEntry(player, layout.previousPageSlot, false, page - 1)
        }
        if (page < pageLayout.totalPages - 1) {
            elements += navigationEntry(player, layout.nextPageSlot, true, page + 1)
        }

        return InventoryMenuView(layout.size, title, elements)
    }

    private fun back(context: MenuActionContext): MenuActionResult {
        return MenuActionResult.Success(MenuUpdate.Back)
    }

    private fun changePage(context: MenuActionContext): MenuActionResult {
        val page = context.payload[PAGE]?.toIntOrNull() ?: return MenuActionResult.Rejected()
        return MenuActionResult.Success(
            MenuUpdate.Replace(context.route.copy(payload = context.route.payload + (PAGE to page.toString()))),
        )
    }

    private fun invite(context: MenuActionContext): MenuActionResult {
        val targetUuid = context.payload[TARGET_UUID]
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: return MenuActionResult.Rejected()
        val targetName = context.payload[TARGET_NAME].orEmpty()
        val target = Bukkit.getPlayer(targetUuid)
        if (target == null || !target.isOnline) {
            context.player.sendMessage(
                plugin.languageManager.getMessage(
                    context.player,
                    "messages.invite_target_offline",
                    mapOf("player" to targetName),
                ),
            )
            return MenuActionResult.Rejected()
        }
        context.player.performCommand("invite ${target.name}")
        return MenuActionResult.Success(MenuUpdate.Close)
    }

    private fun createTargetEntry(target: Player, viewer: Player, slot: Int): MenuElement {
        val lang = plugin.languageManager
        val colorCode = lang.getMessage(viewer, "publish_level.color.online")
        val status = plugin.playerStatsRepository.findByUuid(target.uniqueId).meetStatus
        val statusKey = "general.status.${status.lowercase()}"
        val statusName = if (lang.hasKey(viewer, statusKey)) lang.getMessage(viewer, statusKey) else status
        return CCSystem.getAPI().getGuiElementService().menuEntry(
            viewer,
            GuiMenuEntrySpec(
                slot = slot,
                material = Material.PLAYER_HEAD,
                name = GuiNameSpec.Component(
                    LegacyComponentSerializer.legacySection().deserialize("$colorCode${target.name}")
                        .decoration(TextDecoration.ITALIC, false),
                ),
                role = GuiElementRole.ACTION,
                data = listOf(
                    GuiMenuEntryData(
                        lang.getMessage(viewer, "gui.meet.world_item.status"),
                        statusName,
                        GuiValueTone.PRIMARY,
                    ),
                ),
                actions = listOf(
                    GuiMenuEntryAction(
                        ACTION_INVITE,
                        MenuAcceptedClicks.LEFT_RIGHT,
                        lang.getMessage(viewer, "gui.invite.target_head.click_invite"),
                        mapOf(
                            TARGET_UUID to target.uniqueId.toString(),
                            TARGET_NAME to target.name,
                        ),
                    ),
                ),
                playerHeadOwner = target.uniqueId,
            ),
        )
    }

    private fun navigationEntry(player: Player, slot: Int, next: Boolean, targetPage: Int): MenuElement {
        val key = if (next) "gui.common.next_page" else "gui.common.prev_page"
        val iconId = if (next) "next_page" else "prev_page"
        return CCSystem.getAPI().getGuiElementService().menuEntry(
            player,
            GuiMenuEntrySpec(
                slot = slot,
                material = plugin.menuConfigManager.getIconMaterial("meet", iconId, Material.ARROW),
                name = GuiNameSpec.Component(plugin.languageManager.getComponent(player, key)),
                role = GuiElementRole.NAVIGATION,
                actions = listOf(
                    GuiMenuEntryAction(
                        ACTION_PAGE,
                        MenuAcceptedClicks.LEFT_RIGHT,
                        plugin.languageManager.getMessage(player, key),
                        mapOf(PAGE to targetPage.toString()),
                    ),
                ),
            ),
        )
    }

    private fun backEntry(player: Player, slot: Int): MenuElement =
        CCSystem.getAPI().getGuiElementService().menuEntry(
            player,
            GuiMenuEntrySpec(
                slot = slot,
                material = plugin.menuConfigManager.getIconMaterial("meet", "back", Material.REDSTONE),
                name = GuiNameSpec.Component(plugin.languageManager.getComponent(player, "gui.common.return")),
                role = GuiElementRole.BACK,
                actions = listOf(
                    GuiMenuEntryAction(
                        ACTION_BACK,
                        MenuAcceptedClicks.LEFT_RIGHT,
                        plugin.languageManager.getMessage(player, "gui.common.return"),
                    ),
                ),
            ),
        )

    private fun MenuRoute.uuid(key: String): UUID? =
        payload[key]?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    companion object {
        private const val OWNER = "myworldmanager"
        private const val ROUTE_ID = "invite"
        private const val WORLD_UUID = "world_uuid"
        private const val SHOW_BACK = "show_back"
        private const val TARGET_UUID = "target_uuid"
        private const val TARGET_NAME = "target_name"
        private const val PAGE = "page"
        private const val ACTION_BACK = "back"
        private const val ACTION_PAGE = "page"
        private const val ACTION_INVITE = "invite"
    }
}
