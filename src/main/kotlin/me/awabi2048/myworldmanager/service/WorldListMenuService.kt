package me.awabi2048.myworldmanager.service

import com.awabi2048.ccsystem.api.localization.generated.CommonKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldMessagesKeys

import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuReversibleContract
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.api.event.MwmFavoriteAddSource
import me.awabi2048.myworldmanager.api.extension.ApiWorldListMenuService
import me.awabi2048.myworldmanager.api.extension.DiscoveryListSnapshot
import me.awabi2048.myworldmanager.api.extension.DiscoveryWorldAction
import me.awabi2048.myworldmanager.api.extension.FavoriteListAction
import me.awabi2048.myworldmanager.api.extension.FavoriteListSnapshot
import me.awabi2048.myworldmanager.gui.MwmMenuActionSemantics
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.session.PreviewSessionManager
import me.awabi2048.myworldmanager.session.PreviewSource
import me.awabi2048.myworldmanager.util.FavoriteRegistrationTimestamp
import me.awabi2048.myworldmanager.util.WorldAccessMessageResolver
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

/**
 * MWMが所有するDiscovery/Favorite一覧のQueryと既存操作を一つの境界へ集約します。
 *
 * 差し替え画面は描画とRouteだけを担当し、クリック時の再取得・再認可、Favorite更新、
 * 確認画面、プレビュー復帰などの意味をこのサービスへ委譲します。
 */
class WorldListMenuService(private val plugin: MyWorldManager) : ApiWorldListMenuService {
    override fun discoverySnapshot(player: Player): DiscoveryListSnapshot {
        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        val currentWorldUuid = plugin.worldConfigRepository.findByWorldName(player.world.name)?.uuid
        val worlds = plugin.worldConfigRepository.findAll()
            .filter { MyWorldManagerApi.getWorldAccessPolicy().canShowInDiscovery(player, it) }
        return DiscoveryListSnapshot(
            worlds = worlds,
            visitedWorldUuids = stats.visitedWorlds.keys.toSet(),
            favoriteWorldUuids = stats.favoriteWorlds.keys.toSet(),
            currentWorldUuid = currentWorldUuid,
        )
    }

    override fun favoriteSnapshot(player: Player): FavoriteListSnapshot {
        val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
        val favoriteIds = stats.favoriteWorlds.entries
            .sortedBy { FavoriteRegistrationTimestamp.sortValue(it.value) }
            .map { it.key }
        val resolved = favoriteIds.mapNotNull { uuid ->
            plugin.worldConfigRepository.findByUuid(uuid).also {
                if (it == null) stats.favoriteWorlds.remove(uuid)
            }
        }
        if (resolved.size != favoriteIds.size) plugin.playerStatsRepository.save(stats)
        val currentWorldUuid = plugin.worldConfigRepository.findByWorldName(player.world.name)?.uuid
        return FavoriteListSnapshot(
            worlds = resolved,
            currentWorldUuid = currentWorldUuid,
            currentWorldIsFavorite = currentWorldUuid in stats.favoriteWorlds,
        )
    }

    override fun executeDiscovery(
        player: Player,
        worldUuid: UUID,
        action: DiscoveryWorldAction,
    ): MenuActionResult {
        val worldData = plugin.worldConfigRepository.findByUuid(worldUuid)
            ?: return MenuActionResult.Rejected()
        val isMember = isMember(player, worldData)
        if (action != DiscoveryWorldAction.VISIT &&
            !MyWorldManagerApi.getWorldAccessPolicy().canShowInDiscovery(player, worldData)
        ) {
            // 一覧描画後に公開状態やChanponの完成状態が変化した場合は、古い項目から操作させません。
            return MenuActionResult.Rejected()
        }
        return when (action) {
            DiscoveryWorldAction.VISIT -> {
                if (isCurrentWorld(player, worldData)) MenuActionResult.Ignored
                else visit(player, worldData, isMember)
            }
            DiscoveryWorldAction.PREVIEW -> {
                if (isCurrentWorld(player, worldData)) MenuActionResult.Ignored
                else preview(player, worldData)
            }
            DiscoveryWorldAction.REQUEST_MEMBERSHIP -> requestMembership(player, worldData, isMember)
            DiscoveryWorldAction.TOGGLE_FAVORITE -> toggleFavorite(
                player,
                worldData,
                isMember,
                MwmFavoriteAddSource.DISCOVERY_MENU,
            )
        }
    }

    override fun executeFavorite(
        player: Player,
        action: FavoriteListAction,
    ): MenuActionResult = when (action) {
        is FavoriteListAction.OpenWorldDetail -> {
            val worldData = plugin.worldConfigRepository.findByUuid(action.worldUuid)
                ?: return MenuActionResult.Rejected()
            plugin.menuEntryRouter.openFavoriteMenu(player, worldData)
            MenuActionResult.Success(MenuUpdate.None)
        }

        is FavoriteListAction.OpenOtherWorlds -> {
            val currentWorld = currentWorld(player, action.expectedCurrentWorldUuid)
                ?: return MenuActionResult.Rejected()
            plugin.menuEntryRouter.openVisitMenu(
                player,
                Bukkit.getOfflinePlayer(currentWorld.owner),
                0,
                currentWorld,
                guestAccessibleOnly = currentWorld.owner == player.uniqueId,
            )
            MenuActionResult.Success(MenuUpdate.None)
        }

        is FavoriteListAction.ToggleCurrentFavorite -> {
            val currentWorld = currentWorld(player, action.expectedCurrentWorldUuid)
                ?: return MenuActionResult.Rejected()
            if (currentWorld.owner == player.uniqueId) return MenuActionResult.Ignored
            val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
            if (currentWorld.uuid in stats.favoriteWorlds) {
                plugin.menuEntryRouter.openFavoriteRemoveConfirm(player, currentWorld)
                return MenuActionResult.Success(MenuUpdate.None)
            }
            toggleFavorite(
                player,
                currentWorld,
                isMember(player, currentWorld),
                MwmFavoriteAddSource.FAVORITE_MENU,
            )
        }
    }

    override fun favoriteToggleContract(): MenuReversibleContract =
        MwmMenuActionSemantics.contract("favorite-toggle")

    private fun visit(
        player: Player,
        worldData: WorldData,
        isMember: Boolean,
    ): MenuActionResult {
        if (!MyWorldManagerApi.getWorldAccessPolicy().canUseVisitEntry(player, worldData, isMember)) {
            player.sendMessage(
                WorldAccessMessageResolver.visit(
                    plugin.languageManager,
                    player,
                    worldData,
                    isMember,
                ),
            )
            plugin.soundManager.playActionSound(player, "discovery", "access_denied")
            return MenuActionResult.Success(MenuUpdate.Close)
        }
        plugin.worldService.teleportToWorld(player, worldData.uuid) {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    player,
                    MyworldMessagesKeys.MESSAGES_WARP_SUCCESS,
                    mapOf("world" to worldData.name),
                ),
            )
        }
        return MenuActionResult.Success(MenuUpdate.Close)
    }

    private fun preview(player: Player, worldData: WorldData): MenuActionResult {
        if (worldData.isArchived) return MenuActionResult.Ignored
        val started = plugin.previewSessionManager.startPreview(
            player,
            PreviewSessionManager.PreviewTarget.World(worldData),
            PreviewSource.DISCOVERY_MENU,
        )
        return if (started) MenuActionResult.Success(MenuUpdate.None) else MenuActionResult.Rejected()
    }

    private fun requestMembership(
        player: Player,
        worldData: WorldData,
        isMember: Boolean,
    ): MenuActionResult {
        if (isMember) {
            player.sendMessage(plugin.languageManager.getMessage(player, CommonKeys.ERROR_MEMBER_REQUEST_ALREADY_MEMBER))
            return MenuActionResult.Rejected()
        }
        plugin.menuEntryRouter.openMemberRequestConfirm(player, worldData)
        return MenuActionResult.Success(MenuUpdate.None)
    }

    private fun toggleFavorite(
        player: Player,
        worldData: WorldData,
        isMember: Boolean,
        source: MwmFavoriteAddSource,
    ): MenuActionResult {
        if (isMember) return MenuActionResult.Ignored
        return when (plugin.favoriteStateService.toggle(player, worldData, source)) {
            FavoriteStateService.ToggleResult.Added -> {
                player.sendMessage(plugin.languageManager.getMessage(player, MyworldMessagesKeys.MESSAGES_FAVORITE_ADDED))
                plugin.soundManager.playActionSound(
                    player,
                    if (source == MwmFavoriteAddSource.FAVORITE_MENU) "favorite" else "discovery",
                    "favorite_add",
                )
                MenuActionResult.Success(MenuUpdate.Refresh)
            }

            FavoriteStateService.ToggleResult.Removed -> {
                player.sendMessage(plugin.languageManager.getMessage(player, MyworldMessagesKeys.MESSAGES_FAVORITE_REMOVED))
                plugin.soundManager.playActionSound(
                    player,
                    if (source == MwmFavoriteAddSource.FAVORITE_MENU) "favorite" else "discovery",
                    "favorite_remove",
                )
                MenuActionResult.Success(MenuUpdate.Refresh)
            }

            FavoriteStateService.ToggleResult.LimitReached -> {
                player.sendMessage(
                    plugin.languageManager.getMessage(
                        player,
                        CommonKeys.ERROR_FAVORITE_LIMIT_REACHED,
                        mapOf("limit" to plugin.config.getInt("favorite.max_count", 1000)),
                    ),
                )
                MenuActionResult.Rejected()
            }
        }
    }

    private fun currentWorld(player: Player, expectedUuid: UUID): WorldData? {
        val current = plugin.worldConfigRepository.findByWorldName(player.world.name) ?: return null
        return current.takeIf { it.uuid == expectedUuid }
    }

    private fun isCurrentWorld(player: Player, worldData: WorldData): Boolean =
        plugin.worldConfigRepository.findByWorldName(player.world.name)?.uuid == worldData.uuid

    private fun isMember(player: Player, worldData: WorldData): Boolean =
        player.uniqueId == worldData.owner ||
            player.uniqueId in worldData.moderators ||
            player.uniqueId in worldData.members
}
