package me.awabi2048.myworldmanager.api.extension

import me.awabi2048.myworldmanager.session.WorldCreationSession
import org.bukkit.entity.Player

/**
 * アドオン固有の作成確認画面を完全に所有するProvider。
 *
 * 標準ItemTagとCreationGuiHolderを使うことで、作成確定や入力遷移などの
 * 業務処理はMWM本体のリスナーへ委譲できる。
 */
interface CreationConfirmationMenuProvider {
    fun getId(): String

    fun open(player: Player, session: WorldCreationSession): Boolean
}
