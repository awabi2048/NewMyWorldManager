package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuActionContext
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import java.util.UUID
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.GuiItemFactory
import me.awabi2048.myworldmanager.util.ItemTag
import me.awabi2048.myworldmanager.util.InviteTargetResolver
import me.awabi2048.myworldmanager.util.WorldAccessMessageResolver
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class InviteGui(private val plugin: MyWorldManager) {
    private val runtime = CCSystem.getAPI().getMenuRuntimeService()

    private val playerSlots = listOf(
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34
    )

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
        val maxPage = (targets.size - 1).coerceAtLeast(0) / playerSlots.size
        val page = (route.payload[PAGE]?.toIntOrNull() ?: 0).coerceIn(0, maxPage)
        val pageTargets = targets.drop(page * playerSlots.size).take(playerSlots.size)
        val elements = mutableListOf<MenuElement>()
        pageTargets.forEachIndexed { index, target ->
            elements += MenuElement(
                playerSlots[index],
                createTargetHead(target, player),
                GuiElementRole.ACTION,
                ACTION_INVITE,
                mapOf(
                    TARGET_UUID to target.uniqueId.toString(),
                    TARGET_NAME to target.name,
                ),
            )
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
        elements += MenuElement(
            4,
            me.awabi2048.myworldmanager.util.GuiHelper.createContextWorldIconItem(
                plugin,
                player,
                currentWorldData,
                statusLore
            ),
            GuiElementRole.CONTENT,
        )

        if (GuiHelper.canGoBack(player)) {
            elements += MenuElement(
                40,
                me.awabi2048.myworldmanager.util.GuiHelper.createReturnItem(plugin, player, "meet"),
                GuiElementRole.BACK,
                ACTION_BACK,
            )
        }
        if (page > 0) {
            elements += MenuElement(
                37,
                GuiHelper.createPrevPageItem(plugin, player, "meet", page - 1),
                GuiElementRole.NAVIGATION,
                ACTION_PAGE,
                mapOf(PAGE to (page - 1).toString()),
            )
        }
        if (page < maxPage) {
            elements += MenuElement(
                43,
                GuiHelper.createNextPageItem(plugin, player, "meet", page + 1),
                GuiElementRole.NAVIGATION,
                ACTION_PAGE,
                mapOf(PAGE to (page + 1).toString()),
            )
        }

        return InventoryMenuView(45, title, elements)
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

    private fun createTargetHead(target: Player, viewer: Player): ItemStack {
        val lang = plugin.languageManager
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as? org.bukkit.inventory.meta.SkullMeta ?: return item

        meta.owningPlayer = target
        val colorCode = lang.getMessage(viewer, "publish_level.color.online")
        meta.displayName(
            LegacyComponentSerializer.legacySection().deserialize("$colorCode${target.name}")
                .decoration(TextDecoration.ITALIC, false)
        )

        val status = plugin.playerStatsRepository.findByUuid(target.uniqueId).meetStatus
        val statusKey = "general.status.${status.lowercase()}"
        val statusName = if (lang.hasKey(viewer, statusKey)) lang.getMessage(viewer, statusKey) else status
        val lore = CCSystem.getAPI().getLoreService().render(GuiLoreSpec.Blocks(listOf(
            GuiLoreBlock(listOf(GuiLoreLine.Data(
                lang.getMessage(viewer, "gui.meet.world_item.status"),
                statusName,
                "§e"
            ))),
            GuiLoreBlock(listOf(GuiLoreLine.Action(
                lang.getMessage(viewer, "lore.click.any"),
                lang.getMessage(viewer, "gui.invite.target_head.click_invite")
            )))
        )))

        meta.lore(lore)
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_INVITE_TARGET_HEAD)
        ItemTag.setString(item, "invite_target_uuid", target.uniqueId.toString())
        ItemTag.setString(item, "invite_target_name", target.name)
        return item
    }

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
