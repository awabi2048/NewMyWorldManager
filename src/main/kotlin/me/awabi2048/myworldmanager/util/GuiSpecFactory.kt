package me.awabi2048.myworldmanager.util

import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiNameStyle
import net.kyori.adventure.text.Component
import org.bukkit.Material

/** MWMが表示内容を宣言し、完成アイテムの生成はCC-Systemへ委ねるための意味型生成器です。 */
object GuiSpecFactory {
    fun spec(
        material: Material,
        name: String,
        lore: GuiLoreSpec,
        role: GuiElementRole = GuiElementRole.CONTENT,
    ): GuiItemSpec = GuiItemSpec(
        material,
        GuiNameSpec.Text(name, GuiNameStyle.DEFAULT),
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
        GuiNameSpec.Component(name),
        lore,
        role,
        1,
    )
}
