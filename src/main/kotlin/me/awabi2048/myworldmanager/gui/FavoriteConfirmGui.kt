package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.MenuSoundPolicy
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.util.GuiHelper
import org.bukkit.Material
import org.bukkit.entity.Player
import me.awabi2048.myworldmanager.util.GuiSpecFactory

class FavoriteConfirmGui(private val plugin: MyWorldManager) {
    fun open(player: Player, worldData: WorldData) {
        val lang = plugin.languageManager
        val worldName = lang.getMessageStrict(player, worldData.name) ?: worldData.name
        val worldItem = GuiHelper.createContextWorldIconItem(
            plugin,
            player,
            worldData,
            GuiLoreSpec.Rich(
                lang.getMessageList(
                    player,
                    "gui.favorite.remove_confirm.lore",
                    mapOf("world" to worldName),
                ).map(GuiLoreLine::Warning),
                GuiLoreFrame.BOTH,
            ),
        )
        val confirmLabel = lang.getMessage(player, "gui.common.confirm")
        val cancelLabel = lang.getMessage(player, "gui.common.cancel")
        val confirmItem = GuiSpecFactory.spec(Material.RED_CONCRETE, confirmLabel, GuiLoreSpec.None, GuiElementRole.CONFIRM)
        val cancelItem = GuiSpecFactory.spec(Material.LIME_CONCRETE, cancelLabel, GuiLoreSpec.None, GuiElementRole.CANCEL)
        plugin.confirmationMenuGui.open(
            player = player,
            menuId = "favorite_menu",
            title = GuiHelper.inventoryTitle(lang.getMessage(player, "gui.favorite.remove_confirm.title")),
            centerItem = worldItem,
            confirmItem = confirmItem,
            cancelItem = cancelItem,
            confirmActionText = confirmLabel,
            cancelActionText = cancelLabel,
            onConfirm = {
                val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                if (stats.favoriteWorlds.remove(worldData.uuid) != null) {
                    worldData.favorite = (worldData.favorite - 1).coerceAtLeast(0)
                    plugin.playerStatsRepository.save(stats)
                    plugin.worldConfigRepository.save(worldData)
                    player.sendMessage(lang.getMessage(player, "messages.favorite_removed"))
                    plugin.soundManager.playActionSound(player, "favorite", "favorite_remove")
                }
                MenuActionResult.Success(MenuUpdate.Back)
            },
            confirmSound = MenuSoundPolicy.Silent,
        )
    }

    private fun openList(player: Player) {
        val session = plugin.favoriteSessionManager.getSession(player.uniqueId)
        plugin.menuEntryRouter.openFavoriteList(
            player,
            0,
            returnToFavoriteMenu = session.returnToFavoriteMenu,
        )
    }
}
