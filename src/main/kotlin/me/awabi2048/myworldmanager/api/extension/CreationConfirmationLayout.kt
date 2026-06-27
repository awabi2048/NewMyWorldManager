package me.awabi2048.myworldmanager.api.extension

/**
 * 作成処理は MWM が所有したまま、アドオンが確認画面の競合するスロットだけを調整するためのレイアウト設定。
 */
data class CreationConfirmationLayout(
    var spawnLocationSlot: Int = 41
)
