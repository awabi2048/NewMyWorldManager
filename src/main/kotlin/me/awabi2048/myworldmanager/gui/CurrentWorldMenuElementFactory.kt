package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.api.localization.generated.CommonKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldGuiCommonKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldGuiFavoriteKeys

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryData
import com.awabi2048.ccsystem.api.gui.GuiMenuEntrySpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiValueTone
import com.awabi2048.ccsystem.api.gui.MenuElement
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import org.bukkit.Material
import org.bukkit.entity.Player

/** `/myworld` と `/favorite` が同じ現在ワールド情報を表示するための唯一の生成元です。 */
class CurrentWorldMenuElementFactory(private val plugin: MyWorldManager) {
    fun create(player: Player, slot: Int): MenuElement {
        val worldData = plugin.worldConfigRepository.findByWorldName(player.world.name)
        return if (worldData == null) createUnmanaged(player, slot) else createManaged(player, worldData, slot)
    }

    private fun createManaged(player: Player, worldData: WorldData, slot: Int): MenuElement {
        val lang = plugin.languageManager
        val worldName = worldData.name
        val ownerName = PlayerNameUtil.getNameOrDefault(worldData.owner, lang.getMessage(player, CommonKeys.GENERAL_UNKNOWN))
        val tagNames = worldData.tags.takeIf(List<String>::isNotEmpty)?.joinToString(", ") {
            plugin.worldTagManager.getDisplayName(player, it)
        }
        return CCSystem.getAPI().getGuiElementService().menuEntry(
            player,
            GuiMenuEntrySpec(
                slot = slot,
                material = worldData.icon,
                name = GuiNameSpec.FixedLabel(lang.getComponent(player, MyworldGuiFavoriteKeys.GUI_FAVORITE_CURRENT_WORLD_NAME)),
                role = GuiElementRole.CONTENT,
                description = listOfNotNull(worldData.description.takeIf(String::isNotBlank)),
                data = buildList {
                    add(GuiMenuEntryData(lang.getMessage(player, MyworldGuiCommonKeys.GUI_COMMON_WORLD_ITEM_WORLD_NAME), worldName, GuiValueTone.SUCCESS))
                    add(GuiMenuEntryData(lang.getMessage(player, MyworldGuiCommonKeys.GUI_COMMON_WORLD_ITEM_OWNER), ownerName, GuiValueTone.INFO))
                    add(GuiMenuEntryData(lang.getMessage(player, MyworldGuiCommonKeys.GUI_COMMON_WORLD_ITEM_FAVORITE), worldData.favorite, GuiValueTone.DANGER))
                    add(
                        GuiMenuEntryData(
                            lang.getMessage(player, MyworldGuiCommonKeys.GUI_COMMON_WORLD_ITEM_RECENT_VISITORS),
                            lang.getMessage(
                                player,
                                MyworldGuiCommonKeys.GUI_COMMON_WORLD_ITEM_RECENT_VISITORS_VALUE,
                                mapOf("count" to worldData.recentVisitors.sum()),
                            ),
                            GuiValueTone.SUCCESS,
                        ),
                    )
                    tagNames?.let {
                        add(GuiMenuEntryData(lang.getMessage(player, MyworldGuiCommonKeys.GUI_COMMON_WORLD_ITEM_TAGS), it, GuiValueTone.PRIMARY))
                    }
                },
            ),
        )
    }

    private fun createUnmanaged(player: Player, slot: Int): MenuElement {
        // 管理対象外のワールドは「現在のマイワールド」ではないため、
        // コンパスの情報項目を置かず、標準フッターの背景だけを維持します。
        // これにより、/myworld・Favorite・Bedrock一覧で同じ表示契約になります。
        return CCSystem.getAPI().getGuiElementService().backgroundEntry(
            slot,
            Material.BLACK_STAINED_GLASS_PANE,
        )
    }
}
