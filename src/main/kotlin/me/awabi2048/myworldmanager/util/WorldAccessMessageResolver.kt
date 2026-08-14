package me.awabi2048.myworldmanager.util

import com.awabi2048.ccsystem.api.localization.generated.CommonKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldMessagesKeys
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
                ?: CommonKeys.ERROR_INVITE_LOCKED_ERROR
        )

    fun inviteTarget(language: LanguageManager, player: Player, world: WorldData, target: OfflinePlayer): String =
        language.getMessage(
            player,
            MyWorldManagerApi.getWorldAccessPolicy().inviteTargetDeniedMessageKey(player, world, target)
                ?: CommonKeys.ERROR_INVITE_LOCKED_ERROR
        )

    fun visit(language: LanguageManager, player: Player, world: WorldData?, isMember: Boolean): String =
        language.getMessage(
            player,
            world?.let { MyWorldManagerApi.getWorldAccessPolicy().visitDeniedMessageKey(player, it, isMember) }
                ?: CommonKeys.ERROR_WORLD_NOT_PUBLIC
        )

    fun enter(language: LanguageManager, player: Player, world: WorldData, isMember: Boolean): String =
        language.getMessage(
            player,
            MyWorldManagerApi.getWorldAccessPolicy().enterDeniedMessageKey(player, world, isMember)
                ?: CommonKeys.ERROR_PORTAL_DEST_LOCKED
        )

    fun warp(language: LanguageManager, player: Player, world: WorldData, isMember: Boolean): String =
        language.getMessage(
            player,
            MyWorldManagerApi.getWorldAccessPolicy().enterDeniedMessageKey(player, world, isMember)
                ?: MyworldMessagesKeys.MESSAGES_WORLDWARP_ACCESS_DENIED
        )

    fun sharedEntry(language: LanguageManager, player: Player, world: WorldData, isMember: Boolean): String =
        language.getMessage(
            player,
            MyWorldManagerApi.getWorldAccessPolicy().sharedEntryDeniedMessageKey(player, world, isMember)
                ?: CommonKeys.ERROR_PORTAL_BIND_INVALID_PUBLISH
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
                ?: CommonKeys.ERROR_WORLD_NOT_PUBLIC
        )
}
