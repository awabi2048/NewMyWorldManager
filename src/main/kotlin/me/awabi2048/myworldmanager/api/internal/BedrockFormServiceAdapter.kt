package me.awabi2048.myworldmanager.api.internal

import me.awabi2048.myworldmanager.api.service.ApiBedrockFormService
import me.awabi2048.myworldmanager.ui.bedrock.FloodgateFormBridge
import me.awabi2048.myworldmanager.ui.PlayerPlatformResolver
import org.bukkit.entity.Player

class BedrockFormServiceAdapter(
    private val platformResolver: PlayerPlatformResolver,
    private val formBridge: FloodgateFormBridge
) : ApiBedrockFormService {
    override fun isBedrock(player: Player): Boolean = platformResolver.isBedrock(player)

    override fun sendSimpleForm(
        player: Player,
        title: String,
        content: String,
        buttons: List<String>,
        onSelect: (Int) -> Unit,
        onClosed: (() -> Unit)?
    ): Boolean = formBridge.sendSimpleForm(player, title, content, buttons, onSelect, onClosed)

    override fun sendCustomInputForm(
        player: Player,
        title: String,
        label: String,
        placeholder: String,
        defaultValue: String,
        onSubmit: (String) -> Unit,
        onClosed: (() -> Unit)?
    ): Boolean = formBridge.sendCustomInputForm(
        player,
        title,
        label,
        placeholder,
        defaultValue,
        onSubmit,
        onClosed
    )
}
