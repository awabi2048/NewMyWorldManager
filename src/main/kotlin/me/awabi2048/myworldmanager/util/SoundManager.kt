package me.awabi2048.myworldmanager.util

import me.awabi2048.myworldmanager.MyWorldManager
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

class SoundManager(private val plugin: MyWorldManager) {

    /**
     * GUIを開いた際の効果音を再生
     */
    fun playMenuOpenSound(player: Player, menuId: String) {
        val sound = plugin.menuConfigManager.getOpenSound(menuId)
        val pitch = plugin.menuConfigManager.getOpenSoundPitch(menuId)

        if (sound != null) {
            player.playSound(player.location, sound, 1.0f, pitch)
        }
    }

    /**
     * GUI内の要素をクリックした際の効果音を再生
     */
    fun playClickSound(player: Player, item: ItemStack?) {
        val type = if (item != null) ItemTag.getType(item) else null
        
        // アイテムタイプに基づくサウンド設定（優先）
        val soundKey = when (type) {
            ItemTag.TYPE_GUI_CONFIRM -> "confirm"
            ItemTag.TYPE_GUI_CANCEL -> "cancel"
            ItemTag.TYPE_GUI_NAV_NEXT, ItemTag.TYPE_GUI_NAV_PREV -> "navigation"
            ItemTag.TYPE_GUI_INFO -> "info"
            null -> "default"
            else -> "default"
        }

        val soundStr = plugin.config.getString("sounds.clicks.$soundKey.sound") ?: plugin.config.getString("sounds.clicks.default.sound")
        val pitch = plugin.config.getDouble("sounds.clicks.$soundKey.pitch", 
                    plugin.config.getDouble("sounds.clicks.default.pitch", 2.0)).toFloat()

        if (soundStr != null) {
            try {
                val sound = Sound.valueOf(soundStr.uppercase())
                player.playSound(player.location, sound, 1.0f, pitch)
                
                // 設定された音が UI_BUTTON_CLICK でピッチ 2.0 でない場合、共通クリック音を追加再生
                if (!(sound == Sound.UI_BUTTON_CLICK && pitch == 2.0f)) {
                    player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 2.0f)
                }
            } catch (e: IllegalArgumentException) {
                plugin.logger.warning("Invalid click sound effect name in config: $soundStr")
            }
        }
    }

    /**
     * GUI内の特定アクション（キャンセル、エラー等）の効果音を再生
     */
    fun playActionSound(player: Player, menuId: String, actionId: String) {
        val path = "icons.$actionId.sound"
        val sound = plugin.menuConfigManager.getIconSound(menuId, actionId) ?: return
        val pitch = plugin.menuConfigManager.getIconSoundPitch(menuId, actionId)
        
        player.playSound(player.location, sound, 1.0f, pitch)
        
        // 共通クリック音を追加再生（重複防止）
        if (!(sound == Sound.UI_BUTTON_CLICK && pitch == 2.0f)) {
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 2.0f)
        }
    }

    /**
     * 情報コピーメッセージ送信時の効果音を再生
     */
    fun playCopySound(player: Player) {
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 2.0f)
        player.playSound(player.location, Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, 1.0f, 1.0f)
    }

    /**
     * 管理者メニュー等でのクリック音を再生 (UI_BUTTON_CLICK, pitch 2.0)
     */
    fun playAdminClickSound(player: Player) {
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 2.0f)
    }
}
