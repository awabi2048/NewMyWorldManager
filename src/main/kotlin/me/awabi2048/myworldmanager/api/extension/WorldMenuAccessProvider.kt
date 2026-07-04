package me.awabi2048.myworldmanager.api.extension

import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.entity.Player

interface WorldMenuAccessProvider {
    fun getId(): String

    /**
     * /worldmenu の標準権限では開けないプレイヤーに対して、連携プラグイン側の参加導線を提供する。
     */
    fun open(player: Player, worldData: WorldData, showBackButton: Boolean): Boolean
}
