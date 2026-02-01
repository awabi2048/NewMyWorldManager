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
    fun playClickSound(player: Player, item: ItemStack?, menuId: String? = null) {
        val type = if (item != null) ItemTag.getType(item) else null
        
        // 1. メニュー固有のサウンド設定を取得
        var specificSoundPlayed = false
        var specificSound: Sound? = null
        var specificPitch = 1.0f

        if (menuId != null && item != null) {
            val iconId = when (type) {
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
                // 汎用的なアイテムタイプからアイコンIDへのマッピングが必要な場合はここに追加
                else -> null
            }

            if (iconId != null) {
                specificSound = plugin.menuConfigManager.getIconSound(menuId, iconId)
                if (specificSound != null) {
                    specificPitch = plugin.menuConfigManager.getIconSoundPitch(menuId, iconId)
                    player.playSound(player.location, specificSound, 1.0f, specificPitch)
                    specificSoundPlayed = true
                }
            }
        }

        // 2. アイテムタイプに基づくデフォルトサウンド設定（メニュー固有設定がない場合）
        if (!specificSoundPlayed) {
            val soundKey = when (type) {
                ItemTag.TYPE_GUI_CONFIRM -> "confirm"
                ItemTag.TYPE_GUI_CANCEL -> "cancel"
                ItemTag.TYPE_GUI_NAV_NEXT, ItemTag.TYPE_GUI_NAV_PREV -> "navigation"
                ItemTag.TYPE_GUI_INFO -> "info"
                else -> "default"
            }

            val soundStr = plugin.config.getString("sounds.clicks.$soundKey.sound")
            if (soundStr != null) {
                try {
                    val sound = Sound.valueOf(soundStr.uppercase())
                    val pitch = plugin.config.getDouble("sounds.clicks.$soundKey.pitch", 2.0).toFloat()
                    player.playSound(player.location, sound, 1.0f, pitch)
                    specificSound = sound
                    specificPitch = pitch
                    specificSoundPlayed = true
                } catch (e: IllegalArgumentException) {
                   // ignore
                }
            }
        }

        // 3. グローバルクリック音の再生
        // 設定された固有音が「グローバル音と完全に一致（音種類・ピッチ）」していない限り、追加で再生する
        val (globalSound, globalPitch) = getGlobalClickSoundInfo()
        
        // 固有音が再生されていない、または固有音がグローバル音と異なる場合に再生
        val isSameAsGlobal = specificSoundPlayed && specificSound == globalSound && specificPitch == globalPitch
        
        if (!isSameAsGlobal && globalSound != null) {
            player.playSound(player.location, globalSound, 1.0f, globalPitch)
        }
    }

    /**
     * GUI内の特定アクション（キャンセル、エラー等）の効果音を再生
     */
    fun playActionSound(player: Player, menuId: String, actionId: String) {
        val sound = plugin.menuConfigManager.getIconSound(menuId, actionId) ?: return
        val pitch = plugin.menuConfigManager.getIconSoundPitch(menuId, actionId)
        
        player.playSound(player.location, sound, 1.0f, pitch)
        
        // 共通クリック音を追加再生（重複防止）
        val (globalSound, globalPitch) = getGlobalClickSoundInfo()
        if (globalSound != null && !(sound == globalSound && pitch == globalPitch)) {
             player.playSound(player.location, globalSound, 1.0f, globalPitch)
        }
    }

    /**
     * 情報コピーメッセージ送信時の効果音を再生
     */
    fun playCopySound(player: Player) {
        playGlobalClickSound(player)
        player.playSound(player.location, Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, 1.0f, 1.0f)
    }

    /**
     * 管理者メニュー等でのクリック音を再生
     */
    fun playAdminClickSound(player: Player) {
        playGlobalClickSound(player)
    }

    /**
     * テレポート時の効果音を再生 (ENTITY_PLAYER_TELEPORT, pitch 2.0)
     */
    fun playTeleportSound(player: Player) {
        player.playSound(player.location, Sound.ENTITY_PLAYER_TELEPORT, 1.0f, 2.0f)
    }
    /**
     * グローバルクリック音を再生する
     * 設定が無効またはエラーの場合はデフォルト (UI_BUTTON_CLICK, 2.0) を再生
     */
    fun playGlobalClickSound(player: Player) {
        val (sound, pitch) = getGlobalClickSoundInfo()
        if (sound != null) {
            player.playSound(player.location, sound, 1.0f, pitch)
        }
    }
    
    /**
     * グローバルクリック音の設定を取得する
     * @return Pair(Sound?, Float)
     */
    private fun getGlobalClickSoundInfo(): Pair<Sound?, Float> {
        val globalSoundStr = plugin.config.getString("sounds.global_click.sound", "UI_BUTTON_CLICK")
        val globalPitch = plugin.config.getDouble("sounds.global_click.pitch", 2.0).toFloat()
        
        return try {
            val globalSound = Sound.valueOf(globalSoundStr!!.uppercase())
            Pair(globalSound, globalPitch)
        } catch (e: Exception) {
            // エラー時はデフォルトを返す
            Pair(Sound.UI_BUTTON_CLICK, 2.0f)
        }
    }
}
