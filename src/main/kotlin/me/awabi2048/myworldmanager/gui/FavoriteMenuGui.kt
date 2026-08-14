package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.api.localization.generated.MyworldGuiCommonKeys

import com.awabi2048.ccsystem.api.localization.generated.CommonKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldGuiFavoriteKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldMessagesKeys

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiInteractionGuidance
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryData
import com.awabi2048.ccsystem.api.gui.GuiMenuEntrySpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiValueTone
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuActionContext
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuActionSafety
import com.awabi2048.ccsystem.api.gui.MenuGesture
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.service.FavoriteGroupInviteService
import me.awabi2048.myworldmanager.session.PreviewSessionManager
import me.awabi2048.myworldmanager.session.PreviewSource
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.confirmationButtonName
import org.bukkit.Material
import org.bukkit.entity.Player
import java.util.UUID

/** お気に入り一覧から一段深く開く、ワールド単位の操作メニューです。 */
class FavoriteMenuGui(private val plugin: MyWorldManager) {
    private val runtime = CCSystem.getAPI().getMenuRuntimeService()

    init {
        runtime.register(
            InventoryMenuDefinition(
                owner = OWNER,
                id = ROUTE_ID,
                renderer = { context -> render(context.player, context.route) },
                actions = mapOf(
                    ACTION_BACK to MenuActionHandler(::back),
                    ACTION_WARP to MenuActionHandler(::warp),
                    ACTION_PREVIEW to MenuActionHandler(::preview),
                    ACTION_INVITE to MenuActionHandler(::invite),
                    ACTION_UNFAVORITE to MenuActionHandler(::unfavorite),
                ),
            ),
        )
    }

    fun open(player: Player, worldData: WorldData) {
        plugin.settingsSessionManager.updateSessionAction(
            player,
            worldData.uuid,
            me.awabi2048.myworldmanager.session.SettingsAction.FAVORITE_MENU_GUI,
            isGui = true,
        )
        runtime.navigate(player, MenuRoute(OWNER, ROUTE_ID, mapOf(WORLD_UUID to worldData.uuid.toString())))
    }

    private fun render(player: Player, route: MenuRoute): InventoryMenuView {
        val worldData = route.worldData() ?: error("お気に入り操作対象のワールドが見つかりません。")
        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        val isMember = player.uniqueId == worldData.owner ||
            player.uniqueId in worldData.moderators || player.uniqueId in worldData.members
        val canWarp = MyWorldManagerApi.getWorldAccessPolicy().canDirectWorldWarp(player, worldData, isMember)
        val canInvite = plugin.favoriteGroupInviteService.canSend(player, worldData)
        val recipients = if (canInvite) plugin.favoriteGroupInviteService.eligibleRecipients(player, worldData) else emptyList()
        val elements = mutableListOf<MenuElement>()
        elements += nameOnlyAction(
            player,
            FavoriteMenuLayout.DETAIL_ACTION_SLOTS[0],
            Material.ENDER_PEARL,
            "gui.favorite.actions.warp",
            ACTION_WARP,
            canWarp,
            MenuActionSafety.EXTERNAL_SIDE_EFFECT,
        )
        elements += nameOnlyAction(
            player,
            FavoriteMenuLayout.DETAIL_ACTION_SLOTS[1],
            Material.SPYGLASS,
            "gui.favorite.actions.preview",
            ACTION_PREVIEW,
            !worldData.isArchived,
            MenuActionSafety.EXTERNAL_SIDE_EFFECT,
        )
        elements += inviteEntry(player, FavoriteMenuLayout.DETAIL_ACTION_SLOTS[2], canInvite, recipients.size)
        elements += nameOnlyAction(
            player,
            FavoriteMenuLayout.DETAIL_ACTION_SLOTS[3],
            Material.RED_DYE,
            "gui.favorite.actions.unfavorite",
            ACTION_UNFAVORITE,
            worldData.uuid in stats.favoriteWorlds,
            MenuActionSafety.CONFIRM_ENTRY,
        )
        if (GuiHelper.canGoBack(player)) {
            elements += CCSystem.getAPI().getGuiElementService().backEntry(
                player,
                FavoriteMenuLayout.DETAIL_BACK_SLOT,
                Material.REDSTONE,
            )
        }
        return InventoryMenuView(
            FavoriteMenuLayout.DETAIL_SIZE,
            GuiHelper.inventoryTitle(plugin.languageManager.getMessage(
                player,
                MyworldGuiFavoriteKeys.GUI_FAVORITE_ACTIONS_TITLE,
                mapOf("world" to worldData.name),
            )),
            elements,
        )
    }

    private fun back(context: MenuActionContext): MenuActionResult =
        MenuActionResult.Success(MenuUpdate.Back)

    private fun warp(context: MenuActionContext): MenuActionResult {
        val worldData = context.route.worldData() ?: return MenuActionResult.Rejected()
        val isMember = context.player.uniqueId == worldData.owner ||
            context.player.uniqueId in worldData.moderators || context.player.uniqueId in worldData.members
        if (!MyWorldManagerApi.getWorldAccessPolicy().canDirectWorldWarp(context.player, worldData, isMember)) {
            return MenuActionResult.Ignored
        }
        plugin.worldService.teleportToWorld(context.player, worldData.uuid) {
            context.player.sendMessage(plugin.languageManager.getMessage(
                context.player,
                MyworldMessagesKeys.MESSAGES_WARP_SUCCESS,
                mapOf("world" to worldData.name),
            ))
        }
        return MenuActionResult.Success(MenuUpdate.Close)
    }

    private fun preview(context: MenuActionContext): MenuActionResult {
        val worldData = context.route.worldData()?.takeUnless(WorldData::isArchived)
            ?: return MenuActionResult.Ignored
        plugin.previewSessionManager.startPreview(
            context.player,
            PreviewSessionManager.PreviewTarget.World(worldData),
            PreviewSource.FAVORITE_MENU,
        )
        return MenuActionResult.Success(MenuUpdate.None)
    }

    private fun invite(context: MenuActionContext): MenuActionResult {
        val player = context.player
        val worldData = context.route.worldData() ?: return MenuActionResult.Rejected()
        val recipients = plugin.favoriteGroupInviteService.eligibleRecipients(player, worldData)
        if (recipients.isEmpty()) return MenuActionResult.Ignored
        val lang = plugin.languageManager
        plugin.confirmationMenuGui.open(
            player = player,
            menuId = "favorite-group-invite",
            title = GuiHelper.inventoryTitle(lang.getMessage(player, MyworldGuiFavoriteKeys.GUI_FAVORITE_INVITE_CONFIRM_TITLE)),
            centerItem = GuiItemSpec(
                worldData.icon,
                GuiNameSpec.TargetIdentity(lang.getComponent(
                    player,
                    MyworldGuiCommonKeys.GUI_COMMON_WORLD_ITEM_NAME,
                    mapOf("world" to worldData.name),
                )),
                GuiLoreSpec.Blocks(listOf(GuiLoreBlock(listOf(
                    GuiLoreLine.Data(
                        lang.getMessage(player, MyworldGuiFavoriteKeys.GUI_FAVORITE_INVITE_CONFIRM_RECIPIENT_COUNT),
                        recipients.size,
                        "§b",
                    ),
                )))),
                GuiElementRole.CONTENT,
                1,
            ),
            confirmItem = GuiItemSpec(
                Material.LIME_CONCRETE,
                confirmationButtonName(lang.getMessage(player, MyworldGuiFavoriteKeys.GUI_FAVORITE_INVITE_CONFIRM_CONFIRM)),
                GuiLoreSpec.None,
                GuiElementRole.CONFIRM,
                1,
            ),
            cancelItem = GuiItemSpec(
                Material.RED_CONCRETE,
                confirmationButtonName(lang.getMessage(player, CommonKeys.GUI_COMMON_CANCEL)),
                GuiLoreSpec.None,
                GuiElementRole.CANCEL,
                1,
            ),
            confirmActionText = lang.getMessage(player, MyworldGuiFavoriteKeys.GUI_FAVORITE_INVITE_CONFIRM_CONFIRM),
            cancelActionText = lang.getMessage(player, CommonKeys.GUI_COMMON_CANCEL),
            onConfirm = {
                when (plugin.favoriteGroupInviteService.send(player, worldData)) {
                    is FavoriteGroupInviteService.SendResult.Sent ->
                        MenuActionResult.Success(MenuUpdate.Close)
                    FavoriteGroupInviteService.SendResult.NoRecipients -> {
                        player.sendMessage(lang.getMessage(player, MyworldMessagesKeys.MESSAGES_FAVORITE_GROUP_INVITE_NO_RECIPIENTS))
                        MenuActionResult.Success(MenuUpdate.Back)
                    }
                    FavoriteGroupInviteService.SendResult.NotAllowed -> {
                        player.sendMessage(lang.getMessage(player, MyworldMessagesKeys.MESSAGES_FAVORITE_GROUP_INVITE_NOT_ALLOWED))
                        MenuActionResult.Success(MenuUpdate.Back)
                    }
                }
            },
        )
        return MenuActionResult.Success(MenuUpdate.None)
    }

    private fun unfavorite(context: MenuActionContext): MenuActionResult {
        val worldData = context.route.worldData() ?: return MenuActionResult.Rejected()
        if (worldData.uuid !in plugin.playerStatsRepository.findByUuid(context.player.uniqueId).favoriteWorlds) {
            return MenuActionResult.Ignored
        }
        plugin.menuEntryRouter.openFavoriteRemoveConfirm(context.player, worldData)
        return MenuActionResult.Success(MenuUpdate.None)
    }

    private fun nameOnlyAction(
        player: Player,
        slot: Int,
        material: Material,
        nameKey: String,
        actionId: String,
        enabled: Boolean,
        safety: MenuActionSafety,
    ): MenuElement = CCSystem.getAPI().getGuiElementService().menuEntry(
        player,
        GuiMenuEntrySpec(
            slot = slot,
            material = if (enabled) material else Material.BARRIER,
            name = GuiNameSpec.FixedLabel(plugin.languageManager.getComponent(player, nameKey)),
            role = if (enabled) GuiElementRole.ACTION else GuiElementRole.CONTENT,
            actions = if (enabled) listOf(menuGestureAction(
                actionId,
                MenuGesture.ANY,
                plugin.languageManager.getMessage(player, nameKey),
                safety = safety,
            )) else emptyList(),
            interactionGuidance = GuiInteractionGuidance.HIDDEN,
        ),
    )

    private fun inviteEntry(player: Player, slot: Int, allowed: Boolean, recipientCount: Int): MenuElement {
        val lang = plugin.languageManager
        val enabled = allowed && recipientCount > 0
        return CCSystem.getAPI().getGuiElementService().menuEntry(
            player,
            GuiMenuEntrySpec(
                slot = slot,
                material = if (allowed) Material.GOAT_HORN else Material.BARRIER,
                name = GuiNameSpec.FixedLabel(lang.getComponent(player, MyworldGuiFavoriteKeys.GUI_FAVORITE_ACTIONS_INVITE)),
                role = if (enabled) GuiElementRole.ACTION else GuiElementRole.CONTENT,
                description = lang.getMessageList(player, MyworldGuiFavoriteKeys.GUI_FAVORITE_ACTIONS_INVITE_DESCRIPTION),
                data = if (allowed) listOf(GuiMenuEntryData(
                    lang.getMessage(player, MyworldGuiFavoriteKeys.GUI_FAVORITE_ACTIONS_INVITE_RECIPIENT_COUNT),
                    recipientCount,
                    GuiValueTone.PRIMARY,
                )) else emptyList(),
                warnings = when {
                    !allowed -> listOf(lang.getMessage(player, MyworldGuiFavoriteKeys.GUI_FAVORITE_ACTIONS_INVITE_UNAVAILABLE))
                    recipientCount == 0 -> listOf(lang.getMessage(player, MyworldGuiFavoriteKeys.GUI_FAVORITE_ACTIONS_INVITE_NO_RECIPIENTS))
                    else -> emptyList()
                },
                actions = if (enabled) listOf(menuGestureAction(
                    ACTION_INVITE,
                    MenuGesture.ANY,
                    lang.getMessage(player, MyworldGuiFavoriteKeys.GUI_FAVORITE_ACTIONS_INVITE),
                    safety = MenuActionSafety.CONFIRM_ENTRY,
                )) else emptyList(),
            ),
        )
    }

    private fun MenuRoute.worldData(): WorldData? =
        payload[WORLD_UUID]
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?.let(plugin.worldConfigRepository::findByUuid)

    companion object {
        private const val OWNER = "myworldmanager"
        private const val ROUTE_ID = "favorite_menu"
        private const val WORLD_UUID = "world_uuid"
        private const val ACTION_BACK = "back"
        private const val ACTION_WARP = "warp"
        private const val ACTION_PREVIEW = "preview"
        private const val ACTION_INVITE = "invite"
        private const val ACTION_UNFAVORITE = "unfavorite"
    }
}
