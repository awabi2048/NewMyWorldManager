package me.awabi2048.myworldmanager.ui.bedrock

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuCustomFormRequest
import com.awabi2048.ccsystem.api.gui.MenuFormButton
import com.awabi2048.ccsystem.api.gui.MenuFormHandler
import com.awabi2048.ccsystem.api.gui.MenuFormInput
import com.awabi2048.ccsystem.api.gui.MenuSimpleFormRequest
import me.awabi2048.myworldmanager.MyWorldManager
import org.bukkit.entity.Player

/**
 * 既存呼び出しをCC-SystemのForm APIへ接続する移行用ファサード。
 * Floodgate/Cumulusの描画、応答受付、効果音処理はCC-Systemが所有する。
 */
class FloodgateFormBridge(private val plugin: MyWorldManager) {
    data class SimpleFormButton(val label: String, val imagePath: String? = null)
    data class CustomFormInput(
        val label: String,
        val placeholder: String = "",
        val defaultValue: String = ""
    )

    private val forms get() = CCSystem.getAPI().getMenuFormService()

    fun isAvailable(player: Player): Boolean = forms.isAvailable(player)

    fun notifyFallbackCancelled(player: Player) {
        player.sendMessage(plugin.languageManager.getMessage(player, "messages.operation_cancelled"))
    }

    fun sendSimpleForm(
        player: Player,
        title: String,
        content: String,
        buttons: List<String>,
        onSelect: (Int) -> Unit,
        onClosed: (() -> Unit)? = null
    ): Boolean = sendSimpleFormWithImages(
        player,
        title,
        content,
        buttons.map(::SimpleFormButton),
        onSelect,
        onClosed
    )

    fun sendSimpleFormWithImages(
        player: Player,
        title: String,
        content: String,
        buttons: List<SimpleFormButton>,
        onSelect: (Int) -> Unit,
        onClosed: (() -> Unit)? = null
    ): Boolean {
        val request = MenuSimpleFormRequest(
            owner = "my-world-manager",
            id = "legacy-simple-form",
            title = title,
            content = content,
            buttons = buttons.mapIndexed { index, button ->
                MenuFormButton(index.toString(), button.label, button.imagePath)
            },
            handler = MenuFormHandler { _, response ->
                response.textValue("button").toIntOrNull()?.let(onSelect)
                MenuActionResult.Success()
            },
            onClosed = onClosed?.let { callback ->
                MenuFormHandler { _, _ -> callback(); MenuActionResult.Ignored }
            }
        )
        return forms.show(player, request)
    }

    fun sendCustomInputForm(
        player: Player,
        title: String,
        label: String,
        placeholder: String,
        defaultValue: String,
        onSubmit: (String) -> Unit,
        onClosed: (() -> Unit)? = null
    ): Boolean = sendCustomForm(
        player,
        title,
        listOf(CustomFormInput(label, placeholder, defaultValue)),
        { values -> onSubmit(values.firstOrNull().orEmpty()) },
        onClosed
    )

    fun sendCustomForm(
        player: Player,
        title: String,
        inputs: List<CustomFormInput>,
        onSubmit: (List<String>) -> Unit,
        onClosed: (() -> Unit)? = null
    ): Boolean {
        val request = MenuCustomFormRequest(
            owner = "my-world-manager",
            id = "legacy-custom-form",
            title = title,
            inputs = inputs.mapIndexed { index, input ->
                MenuFormInput.Text(index.toString(), input.label, input.placeholder, input.defaultValue)
            },
            handler = MenuFormHandler { _, response ->
                onSubmit(inputs.indices.map { response.textValue(it.toString()) })
                MenuActionResult.Success()
            },
            onClosed = onClosed?.let { callback ->
                MenuFormHandler { _, _ -> callback(); MenuActionResult.Ignored }
            }
        )
        return forms.show(player, request)
    }
}
