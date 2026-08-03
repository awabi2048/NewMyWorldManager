package me.awabi2048.myworldmanager.util

import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiNameStyle
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material

private val legacyFormattingCode = Regex("(?i)[\\u00A7&][0-9A-FK-ORX]")

fun semanticLore(lines: List<GuiLoreLine>, frame: GuiLoreFrame): GuiLoreSpec =
    GuiLoreSpec.FramedBlocks(listOf(GuiLoreBlock(lines)), frame)

fun descriptionLine(text: String): GuiLoreLine = GuiLoreLine.Text(text)
fun warningLine(text: String): GuiLoreLine = GuiLoreLine.Warning(text)
fun dangerLine(text: String): GuiLoreLine = GuiLoreLine.Danger(text)

fun fixedLabelName(value: Component): GuiNameSpec = GuiNameSpec.FixedLabel(value)

fun fixedLabelName(text: String, style: GuiNameStyle): GuiNameSpec = GuiNameSpec.FixedLabel(
    com.awabi2048.ccsystem.CCSystem.getAPI().getGuiElementService().name(text, style),
)

/**
 * 確認ボタン用の固定Nameです。Lore用の言語キーを流用しても、元キーのLegacy装飾を
 * ボタンへ持ち込まず、確認ボタン本来の固定ラベル表示を維持します。
 */
fun confirmationButtonName(text: String): GuiNameSpec = fixedLabelName(
    legacyFormattingCode.replace(text, ""),
    GuiNameStyle.DEFAULT,
)

fun confirmationButtonName(value: Component): GuiNameSpec = confirmationButtonName(
    PlainTextComponentSerializer.plainText().serialize(value),
)

fun targetIdentityName(value: Component): GuiNameSpec = GuiNameSpec.TargetIdentity(value)

fun targetIdentityName(text: String, style: GuiNameStyle): GuiNameSpec = GuiNameSpec.TargetIdentity(
    com.awabi2048.ccsystem.CCSystem.getAPI().getGuiElementService().name(text, style),
)

fun opaqueName(value: Component): GuiNameSpec = GuiNameSpec.Opaque(value)

fun opaqueName(text: String, style: GuiNameStyle): GuiNameSpec = GuiNameSpec.Opaque(
    com.awabi2048.ccsystem.CCSystem.getAPI().getGuiElementService().name(text, style),
)

/** MWMが表示内容を宣言し、完成アイテムの生成はCC-Systemへ委ねるための意味型生成器です。 */
object GuiSpecFactory {
    fun spec(
        material: Material,
        name: String,
        lore: GuiLoreSpec,
        role: GuiElementRole = GuiElementRole.CONTENT,
    ): GuiItemSpec = GuiItemSpec(
        material,
        if (role == GuiElementRole.CONFIRM || role == GuiElementRole.CANCEL) {
            confirmationButtonName(name)
        } else {
            opaqueName(name, GuiNameStyle.DEFAULT)
        },
        lore,
        role,
        1,
    )

    fun spec(
        material: Material,
        name: Component,
        lore: GuiLoreSpec,
        role: GuiElementRole = GuiElementRole.CONTENT,
    ): GuiItemSpec = GuiItemSpec(
        material,
        if (role == GuiElementRole.CONFIRM || role == GuiElementRole.CANCEL) {
            confirmationButtonName(name)
        } else {
            GuiNameSpec.Opaque(name)
        },
        lore,
        role,
        1,
    )
}
