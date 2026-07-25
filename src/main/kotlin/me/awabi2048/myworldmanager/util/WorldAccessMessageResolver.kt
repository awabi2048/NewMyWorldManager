package me.awabi2048.myworldmanager.util

import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player

/** Keeps MWM's normal messages as fallbacks while allowing an overlay to explain its own denial. */
object WorldAccessMessageResolver {
    fun inviteToWorld(language: LanguageManager, player: Player, world: WorldData): String =
        language.getMessage(
            player,
            MyWorldManagerApi.getWorldAccessPolicy().inviteToWorldDeniedMessageKey(player, world)
                ?: "error.invite_locked_error"
        )

    fun inviteTarget(language: LanguageManager, player: Player, world: WorldData, target: OfflinePlayer): String =
        language.getMessage(
            player,
            MyWorldManagerApi.getWorldAccessPolicy().inviteTargetDeniedMessageKey(player, world, target)
                ?: "error.invite_locked_error"
        )

    fun visit(language: LanguageManager, player: Player, world: WorldData?, isMember: Boolean): String =
        language.getMessage(
            player,
            world?.let { MyWorldManagerApi.getWorldAccessPolicy().visitDeniedMessageKey(player, it, isMember) }
                ?: "error.world_not_public"
        )

    fun enter(language: LanguageManager, player: Player, world: WorldData, isMember: Boolean): String =
        language.getMessage(
            player,
            MyWorldManagerApi.getWorldAccessPolicy().enterDeniedMessageKey(player, world, isMember)
                ?: "error.portal_dest_locked"
        )

    fun warp(language: LanguageManager, player: Player, world: WorldData, isMember: Boolean): String =
        language.getMessage(
            player,
            MyWorldManagerApi.getWorldAccessPolicy().enterDeniedMessageKey(player, world, isMember)
                ?: "messages.worldwarp_access_denied"
        )

    fun sharedEntry(language: LanguageManager, player: Player, world: WorldData, isMember: Boolean): String =
        language.getMessage(
            player,
            MyWorldManagerApi.getWorldAccessPolicy().sharedEntryDeniedMessageKey(player, world, isMember)
                ?: "error.portal_bind_invalid_publish"
        )

    fun meet(
        language: LanguageManager,
        player: Player,
        target: Player,
        world: WorldData,
        isMember: Boolean
    ): String =
        language.getMessage(
            player,
            MyWorldManagerApi.getWorldAccessPolicy().meetDeniedMessageKey(player, target, world, isMember)
                ?: "error.world_not_public"
        )
}
