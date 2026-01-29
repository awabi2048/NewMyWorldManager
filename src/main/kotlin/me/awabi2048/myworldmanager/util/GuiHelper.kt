package me.awabi2048.myworldmanager.util

import me.awabi2048.myworldmanager.MyWorldManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.Player

object GuiHelper {
    /**
     * Plays the menu open sound only if the menu title is different from the currently open inventory's title.
     * This prevents the sound from playing when simply refreshing the menu or navigating pages.
     */
    fun playMenuSoundIfTitleChanged(plugin: MyWorldManager, player: Player, menuId: String, newTitle: Component) {
        val currentTitle = player.openInventory.title()
        
        // Serialize to plain text to compare content while ignoring minor formatting differences if any,
        // and to handle the case where correct comparison allows skipping sound on refresh.
        val currentTitleStr = PlainTextComponentSerializer.plainText().serialize(currentTitle)
        val newTitleStr = PlainTextComponentSerializer.plainText().serialize(newTitle)

        // If the titles are different, it means we are opening a new menu type or context.
        // If they are the same, it's likely a page update or refresh, so suppress sound.
        // Note: This relies on different menus having different titles.
        if (currentTitleStr != newTitleStr) {
            plugin.soundManager.playMenuOpenSound(player, menuId)
        }
    }

    /**
     * Arrayの中から現在の値の次（または前）の値を取得します。
     */
    fun <T> getNextValue(current: T, values: Array<T>, isRightClick: Boolean): T {
        val index = values.indexOf(current).let { if (it == -1) 0 else it }
        return if (isRightClick) {
            values[(index + 1) % values.size]
        } else {
            values[(index + values.size - 1) % values.size]
        }
    }

    /**
     * Listの中から現在の値の次（または前）の値を取得します。
     */
    fun <T> getNextValue(current: T, values: List<T>, isRightClick: Boolean): T {
        val index = values.indexOf(current).let { if (it == -1) 0 else it }
        return if (isRightClick) {
            values[(index + 1) % values.size]
        } else {
            values[(index + values.size - 1) % values.size]
        }
    }

    /**
     * GUI遷移中フラグを一定時間後に解除します。
     */
    fun scheduleGuiTransitionReset(plugin: MyWorldManager, player: Player) {
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val session = plugin.settingsSessionManager.getSession(player)
            if (session != null && session.isGuiTransition) {
                session.isGuiTransition = false
            }
        }, 5L)
    }
}
