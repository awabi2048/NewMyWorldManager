package me.awabi2048.myworldmanager.util

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.MenuClickType
import me.awabi2048.myworldmanager.MyWorldManager
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

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
    fun playClickSound(player: Player, item: ItemStack?, menuId: String? = null) {
        val type = item?.let(ItemTag::getType)
        if (menuId == null) {
            menuSoundService.onGenericClick(player)
            return
        }

        val iconId = iconIdFor(type)
        if (iconId != null) {
            menuSoundService.onMenuIconClick(player, menuId, iconId, clickTypeFor(type))
            return
        }

        menuSoundService.onMenuClick(player, menuId, clickTypeFor(type))
    }

    fun playActionSound(player: Player, menuId: String, actionId: String) {
        menuSoundService.onMenuIconClick(player, menuId, actionId)
    }

    fun playCopySound(player: Player) {
        menuSoundService.onGenericClick(player)
        player.playSound(player.location, Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, 1.0f, 1.0f)
    }

    fun playAdminClickSound(player: Player) {
        menuSoundService.onGenericClick(player)
    }

    fun playTeleportSound(player: Player) {
        player.playSound(player.location, Sound.ENTITY_PLAYER_TELEPORT, 1.0f, 2.0f)
    }

    fun playGlobalClickSound(player: Player) {
        menuSoundService.onGenericClick(player)
    }

    fun playChatClickSound(player: Player) {
        menuSoundService.onGenericClick(player)
    }

    private fun iconIdFor(type: String?): String? {
        return when (type) {
            ItemTag.TYPE_GUI_NAV_NEXT -> "next_page"
            ItemTag.TYPE_GUI_NAV_PREV -> "prev_page"
            ItemTag.TYPE_GUI_RETURN, ItemTag.TYPE_GUI_BACK -> "back"
            ItemTag.TYPE_GUI_CONFIRM -> "confirm"
            ItemTag.TYPE_GUI_CANCEL -> "cancel"
            ItemTag.TYPE_GUI_WORLD_ITEM -> "world_item"
            ItemTag.TYPE_GUI_CREATION_TYPE_TEMPLATE -> "template"
            ItemTag.TYPE_GUI_CREATION_TYPE_SEED -> "seed"
            ItemTag.TYPE_GUI_CREATION_TYPE_RANDOM -> "random"
            ItemTag.TYPE_GUI_DISCOVERY_SORT -> "sort"
            ItemTag.TYPE_GUI_DISCOVERY_TAG -> "tag_filter"
            else -> null
        }
    }

    private fun clickTypeFor(type: String?): MenuClickType {
        return when (type) {
            ItemTag.TYPE_GUI_CONFIRM -> MenuClickType.CONFIRM
            ItemTag.TYPE_GUI_CANCEL,
            ItemTag.TYPE_GUI_RETURN,
            ItemTag.TYPE_GUI_BACK -> MenuClickType.CANCEL
            ItemTag.TYPE_GUI_NAV_NEXT,
            ItemTag.TYPE_GUI_NAV_PREV -> MenuClickType.NAVIGATION
            ItemTag.TYPE_GUI_INFO -> MenuClickType.INFO
            else -> MenuClickType.DEFAULT
        }
    }
}
