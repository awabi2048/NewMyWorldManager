package me.awabi2048.myworldmanager.util

import com.awabi2048.ccsystem.api.localization.generated.MyworldGuiCommonKeys


import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiConfirmationLayout
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiPagedListLayout
import com.awabi2048.ccsystem.api.gui.GuiSettingsLayout
import com.awabi2048.ccsystem.api.gui.GuiThreeChoiceLayout
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.WorldData
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory

object GuiHelper {
        fun canGoBack(player: Player): Boolean =
                CCSystem.getAPI().getMenuNavigationService().canGoBack(player)

    private val layoutService
        get() = CCSystem.getAPI().getGuiLayoutService()

    fun isPluginGuiInventory(inventory: Inventory): Boolean {
        val holderClassName = inventory.holder?.javaClass?.name
        if (holderClassName?.startsWith("me.awabi2048.myworldmanager") == true) return true
        return inventory.contents.filterNotNull().any { item ->
            val type = ItemTag.getType(item) ?: return@any false
            type.startsWith("gui_") || type.startsWith("discovery_")
        }
    }

    fun inventoryTitle(title: String): Component {
        return Component.text(title, NamedTextColor.DARK_GRAY)
            .decoration(TextDecoration.ITALIC, false)
    }

    fun inventoryTitle(title: Component): Component {
        return title.color(NamedTextColor.DARK_GRAY)
            .decoration(TextDecoration.ITALIC, false)
    }

    /**
     * メニューを開いたときの効果音を明示的に再生する。
     *
     * 明示的再生方式: 新しい画面を開いたときに呼び出し側が明示的に呼ぶ。
     * 同一画面の再描画（ページ送り・ソート更新等）では呼ばないことで音を抑制する。
     * 間接的なタイトル/ホルダー比較には依存しない。
     */
    fun playMenuOpen(player: Player, menuId: String) {
        // 開く音はCC-System Runtimeが一度だけ再生する。
    }

    /**
     * GUI遷移中フラグを一定時間後に解除します。
     */
    fun confirmationLayout(): GuiConfirmationLayout = layoutService.confirmation45()

    fun pagedListLayout(): GuiPagedListLayout = layoutService.pagedList54()

    fun settingsLayout(): GuiSettingsLayout = layoutService.settings54()

    fun threeChoiceLayout(): GuiThreeChoiceLayout = layoutService.threeChoice45()

    fun handleReturnClick(plugin: MyWorldManager, player: Player) {
        if (CCSystem.getAPI().getMenuRuntimeService().back(player)) {
            return
        }

        // 全てのセッション終了を試みる（安全のため）
        plugin.settingsSessionManager.endSession(player)

        CCSystem.getAPI().getMenuRuntimeService().close(player)
    }

    fun createContextWorldIconItem(
        plugin: MyWorldManager,
        player: Player,
        worldData: WorldData,
        lore: GuiLoreSpec,
    ): GuiItemSpec {
        val lang = plugin.languageManager
        val worldName = worldData.name
        return GuiItemSpec(
            worldData.icon,
            GuiNameSpec.TargetIdentity(
                lang.getComponent(player, MyworldGuiCommonKeys.GUI_COMMON_WORLD_ITEM_NAME, mapOf("world" to worldName))
                    .decoration(TextDecoration.ITALIC, false),
            ),
            lore,
            GuiElementRole.CONTENT,
            1,
        )
    }
}
