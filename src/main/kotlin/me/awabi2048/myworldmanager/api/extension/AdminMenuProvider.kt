package me.awabi2048.myworldmanager.api.extension

import org.bukkit.entity.Player

/**
 * MWMの管理メニュー種別を外部プラグインから追加するための入口。
 * /mwm のフッター切替を本体側に集約し、アドオン側の独自管理コマンドを不要にする。
 */
interface AdminMenuProvider {
    fun getId(): String
    fun getDisplayName(player: Player): String
    fun open(player: Player)
}
