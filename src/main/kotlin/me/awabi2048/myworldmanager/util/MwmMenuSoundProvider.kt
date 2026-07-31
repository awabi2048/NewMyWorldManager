package me.awabi2048.myworldmanager.util

import com.awabi2048.ccsystem.api.gui.MenuClickType
import com.awabi2048.ccsystem.api.gui.MenuSound
import com.awabi2048.ccsystem.api.gui.MenuSoundProvider
import me.awabi2048.myworldmanager.MyWorldManager

@Suppress("DEPRECATION")
class MwmMenuSoundProvider(
    private val plugin: MyWorldManager,
) : MenuSoundProvider {
    override val sourceId: String = PROVIDER_SOURCE_ID
    private val fixedClickSound = MenuSound("UI_BUTTON_CLICK", pitch = 2.0f)

    override fun openSound(menuId: String): MenuSound? {
        val sound = plugin.menuConfigManager.getOpenSound(menuId) ?: return null
        val pitch = plugin.menuConfigManager.getOpenSoundPitch(menuId)
        return MenuSound(sound.name(), pitch = pitch)
    }

    override fun clickSound(menuId: String, clickType: MenuClickType): MenuSound = fixedClickSound

    override fun iconSound(menuId: String, iconId: String): MenuSound = fixedClickSound

    override fun genericClickSound(): MenuSound = fixedClickSound

    companion object {
        const val PROVIDER_SOURCE_ID = "my_world_manager"
    }
}
