package me.awabi2048.myworldmanager.api.service

import com.awabi2048.ccsystem.api.gui.MenuActionResult
import org.bukkit.entity.Player

/** Floodgate/Cumulusの実装詳細を外部拡張へ漏らさないフォーム境界。 */
interface ApiBedrockFormService {
    fun isBedrock(player: Player): Boolean

    fun sendSimpleForm(
        player: Player,
        title: String,
        content: String,
        buttons: List<String>,
        onSelect: (Int) -> MenuActionResult,
        onClosed: (() -> MenuActionResult)? = null
    ): Boolean

    fun sendCustomInputForm(
        player: Player,
        title: String,
        label: String,
        placeholder: String,
        defaultValue: String,
        onSubmit: (String) -> MenuActionResult,
        onClosed: (() -> MenuActionResult)? = null
    ): Boolean
}
