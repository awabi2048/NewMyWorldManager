package me.awabi2048.myworldmanager.util

import com.awabi2048.ccsystem.api.gui.MenuClickType
import com.awabi2048.ccsystem.api.gui.MenuSound
import com.awabi2048.ccsystem.api.gui.MenuSoundProvider
import me.awabi2048.myworldmanager.MyWorldManager

/**
 * MWM の MenuConfigManager が持つメニュー音設定を、cc-system の MenuSoundService 経由で
 * 参照できるようにするプロバイダ。
 *
 * 既存のメニューID・アイコンID（"confirm"/"cancel"/"next_page" 等）を MenuClickType に割り当て、
 * 登録されていないメニューやアイコンは null を返して cc-system の共通デフォルト音に委ねる。
 */
@Suppress("DEPRECATION")
class MwmMenuSoundProvider(
    private val plugin: MyWorldManager,
) : MenuSoundProvider {
    override val sourceId: String = PROVIDER_SOURCE_ID
    private val menuConfigManager: MenuConfigManager
        get() = plugin.menuConfigManager

    override fun openSound(menuId: String): MenuSound? {
        val sound = menuConfigManager.getOpenSound(menuId) ?: return null
        val pitch = menuConfigManager.getOpenSoundPitch(menuId)
        return MenuSound(sound.name(), pitch = pitch)
    }

    override fun clickSound(menuId: String, clickType: MenuClickType): MenuSound? {
        val iconId = clickType.toIconId()
        return iconId?.let { iconSound(menuId, it) } ?: configuredClickSound(clickType)
    }

    override fun iconSound(menuId: String, iconId: String): MenuSound? {
        val sound = menuConfigManager.getIconSound(menuId, iconId) ?: return null
        val pitch = menuConfigManager.getIconSoundPitch(menuId, iconId)
        return MenuSound(sound.name(), pitch = pitch)
    }

    override fun genericClickSound(): MenuSound? {
        val sound = plugin.config.getString("sounds.global_click.sound", "UI_BUTTON_CLICK") ?: "UI_BUTTON_CLICK"
        val pitch = plugin.config.getDouble("sounds.global_click.pitch", 2.0).toFloat()
        return MenuSound(sound, pitch = pitch)
    }

    private fun configuredClickSound(clickType: MenuClickType): MenuSound? {
        val key = when (clickType) {
            MenuClickType.CONFIRM -> "confirm"
            MenuClickType.CANCEL -> "cancel"
            MenuClickType.NAVIGATION -> "navigation"
            MenuClickType.INFO -> "info"
            MenuClickType.DEFAULT -> "default"
        }
        val sound = plugin.config.getString("sounds.clicks.$key.sound") ?: return genericClickSound()
        val pitch = plugin.config.getDouble("sounds.clicks.$key.pitch", 2.0).toFloat()
        return MenuSound(sound, pitch = pitch)
    }

    /**
     * MenuClickType を MWM のアイコンIDに割り当てる。
     * confirm/cancel/navigation は既存のアイコンIDと対応する。
     */
    private fun MenuClickType.toIconId(): String? = when (this) {
        MenuClickType.CONFIRM -> "confirm"
        MenuClickType.CANCEL -> "cancel"
        MenuClickType.NAVIGATION -> "next_page"
        MenuClickType.INFO -> "info"
        // DEFAULT はメニュー固有設定に頼らず、cc-system の共通デフォルト音に委ねる
        MenuClickType.DEFAULT -> null
    }

    companion object {
        const val PROVIDER_SOURCE_ID = "my_world_manager"
    }
}
