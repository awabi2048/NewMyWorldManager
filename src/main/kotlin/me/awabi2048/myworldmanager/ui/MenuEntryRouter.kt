package me.awabi2048.myworldmanager.ui

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.service.MemberRequestInfo
import me.awabi2048.myworldmanager.ui.bedrock.BedrockMenuService
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player

class MenuEntryRouter(
    private val plugin: MyWorldManager,
    private val platformResolver: PlayerPlatformResolver,
    private val bedrockMenuService: BedrockMenuService
) {

    fun openPlayerWorld(player: Player, page: Int = 0, showBackButton: Boolean = false) {
        if (platformResolver.isBedrock(player)) {
            bedrockMenuService.openPlayerWorld(player, page, showBackButton)
            return
        }

        plugin.playerWorldGui.open(player, page, showBackButton)
    }

    fun openUserSettings(player: Player, showBackButton: Boolean = false) {
        if (platformResolver.isBedrock(player)) {
            bedrockMenuService.openSettings(player, showBackButton)
            return
        }

        plugin.userSettingsGui.open(player, showBackButton)
    }

    fun openWorldSettings(player: Player, worldData: WorldData, showBackButton: Boolean = false) {
        if (platformResolver.isBedrock(player)) {
            bedrockMenuService.openCurrentWorldMenu(player, worldData, showBackButton)
            return
        }

        plugin.worldSettingsGui.open(player, worldData, showBackButton)
    }

    fun openDiscovery(player: Player, page: Int = 0, showBackButton: Boolean = false) {
        if (platformResolver.isBedrock(player)) {
            bedrockMenuService.openDiscovery(player, page, showBackButton)
            return
        }

        plugin.discoveryGui.open(player, page, showBackButton)
    }

    fun openFavoriteList(
        player: Player,
        page: Int = 0,
        worldData: WorldData? = null,
        returnToFavoriteMenu: Boolean = false,
        showBackButton: Boolean = false
    ) {
        if (platformResolver.isBedrock(player)) {
            bedrockMenuService.openFavoriteList(player, page, worldData, returnToFavoriteMenu, showBackButton)
            return
        }

        plugin.favoriteGui.open(player, page, worldData, returnToFavoriteMenu, showBackButton)
    }

    fun openFavoriteMenu(player: Player, worldData: WorldData?) {
        if (platformResolver.isBedrock(player)) {
            bedrockMenuService.openFavoriteMenu(player, worldData)
            return
        }

        plugin.favoriteMenuGui.open(player, worldData)
    }

    fun openVisitMenu(
        player: Player,
        owner: OfflinePlayer,
        page: Int = 0,
        worldData: WorldData? = null
    ) {
        if (platformResolver.isBedrock(player)) {
            bedrockMenuService.openVisitMenu(player, owner, page, worldData)
            return
        }

        plugin.visitGui.open(player, owner, page, worldData)
    }

    fun openMeet(player: Player, showBackButton: Boolean? = null) {
        if (platformResolver.isBedrock(player)) {
            bedrockMenuService.openMeet(player, showBackButton)
            return
        }

        plugin.meetGui.open(player, showBackButton)
    }

    fun openFavoriteRemoveConfirm(
        player: Player,
        worldData: WorldData,
        onBedrockConfirm: () -> Unit,
        onBedrockCancel: () -> Unit = {}
    ) {
        if (platformResolver.isBedrock(player) &&
            bedrockMenuService.openFavoriteRemoveConfirm(player, worldData, onBedrockConfirm, onBedrockCancel)
        ) {
            return
        }

        plugin.favoriteConfirmGui.open(player, worldData)
    }

    fun openSpotlightConfirm(
        player: Player,
        worldData: WorldData,
        onBedrockConfirm: () -> Unit,
        onBedrockCancel: () -> Unit = {}
    ) {
        if (platformResolver.isBedrock(player) &&
            bedrockMenuService.openSpotlightConfirm(player, worldData, onBedrockConfirm, onBedrockCancel)
        ) {
            return
        }

        plugin.spotlightConfirmGui.open(player, worldData)
    }

    fun openSpotlightRemoveConfirm(
        player: Player,
        worldData: WorldData,
        onBedrockConfirm: () -> Unit,
        onBedrockCancel: () -> Unit = {}
    ) {
        if (platformResolver.isBedrock(player) &&
            bedrockMenuService.openSpotlightRemoveConfirm(player, worldData, onBedrockConfirm, onBedrockCancel)
        ) {
            return
        }

        plugin.spotlightRemoveConfirmGui.open(player, worldData)
    }

    fun openMemberRequestConfirm(
        player: Player,
        worldData: WorldData,
        onBedrockConfirm: () -> Unit,
        onBedrockCancel: () -> Unit = {}
    ) {
        if (platformResolver.isBedrock(player) &&
            bedrockMenuService.openMemberRequestConfirm(player, worldData, onBedrockConfirm, onBedrockCancel)
        ) {
            return
        }

        plugin.memberRequestConfirmGui.open(player, worldData)
    }

    fun openMemberRequestOwnerConfirm(player: Player, info: MemberRequestInfo, key: String) {
        // 受信者側はコマンドベース承認を維持するため、Form 化しない
        plugin.memberRequestOwnerConfirmGui.open(player, info, key)
    }

    fun openWorldSeedConfirm(
        player: Player,
        currentSlots: Int,
        nextSlots: Int,
        onBedrockConfirm: () -> Unit,
        onBedrockCancel: () -> Unit = {}
    ) {
        if (platformResolver.isBedrock(player) &&
            bedrockMenuService.openWorldSeedConfirm(
                player,
                currentSlots,
                nextSlots,
                onBedrockConfirm,
                onBedrockCancel
            )
        ) {
            return
        }

        plugin.worldSeedConfirmGui.open(player, currentSlots, nextSlots)
    }
}
