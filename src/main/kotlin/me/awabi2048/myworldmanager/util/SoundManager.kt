package me.awabi2048.myworldmanager.util

import com.awabi2048.ccsystem.CCSystem
import me.awabi2048.myworldmanager.MyWorldManager
import org.bukkit.Sound
import org.bukkit.entity.Player

class SoundManager(@Suppress("UNUSED_PARAMETER") plugin: MyWorldManager) {
    private val menuSoundService
        get() = CCSystem.getAPI().getMenuSoundService()

    /**
     * MWM側の既存呼び出し口を残しつつ、実際のメニュー開封音は cc-system の共通サービスへ委譲する。
     */
    fun playMenuOpenSound(player: Player, menuId: String) {
        menuSoundService.onMenuOpen(player, menuId)
    }

    /**
     * 既存の ItemTag -> アイコンID対応を保ったまま、メニュークリック音を共通サービスへ委譲する。
     */
    fun playActionSound(player: Player, menuId: String, actionId: String) {
        // Runtime管理中の画面では、Action結果に基づく効果音をRuntimeだけが再生する。
        // 移行前の呼び出しが残っていても、同一クリックで二重再生させない。
        if (CCSystem.getAPI().getMenuNavigationService().currentRoute(player) != null) {
            return
        }
        menuSoundService.onMenuIconClick(player, menuId, actionId)
    }

    fun playCopySound(player: Player) {
        menuSoundService.onGenericClick(player)
        player.playSound(player.location, Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, 1.0f, 1.0f)
    }

    fun playTeleportSound(player: Player) {
        player.playSound(player.location, Sound.ENTITY_PLAYER_TELEPORT, 1.0f, 2.0f)
    }

    fun playGlobalClickSound(player: Player) {
        CCSystem.getAPI().getMenuSoundService().onGenericClick(player)
    }

    fun playChatClickSound(player: Player) {
        CCSystem.getAPI().getMenuSoundService().onGenericClick(player)
    }

}
