package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.session.SettingsAction
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class EnvironmentConfirmGui(private val plugin: MyWorldManager) {

    fun open(player: Player, worldData: WorldData, itemToConsume: ItemStack, cost: Int) {
        val lang = plugin.languageManager
        val title = lang.getMessage(player, "gui.common.confirmation")
        me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(
                plugin,
                player,
                "environment_confirm",
                Component.text(title)
        )

        plugin.settingsSessionManager.updateSessionAction(
                player,
                worldData.uuid,
                SettingsAction.ENV_CONFIRM,
                isGui = true
        )
        me.awabi2048.myworldmanager.util.GuiHelper.scheduleGuiTransitionReset(plugin, player)
        val session = plugin.settingsSessionManager.getSession(player)
        session?.confirmItem = itemToConsume.clone()

        val holder = WorldSettingsGuiHolder()
        val inventory = Bukkit.createInventory(holder, 27, Component.text(title))
        holder.inv = inventory

        // 背景
        val grayPane = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        val grayMeta = grayPane.itemMeta
        grayMeta?.displayName(Component.empty())
        grayMeta?.isHideTooltip = true
        grayPane.itemMeta = grayMeta
        ItemTag.tagItem(grayPane, ItemTag.TYPE_GUI_DECORATION)

        for (i in 0 until 27) inventory.setItem(i, grayPane)

        // 中央: 消費アイテム情報
        val displayItem = itemToConsume.clone()
        displayItem.amount = 1
        val meta = displayItem.itemMeta
        val lore = meta.lore() ?: mutableListOf()
        lore.add(Component.empty())
        lore.add(
                Component.text(
                        lang.getMessage(player, "gui.settings.expand.cost", mapOf("cost" to cost))
                )
        )
        meta.lore(lore)
        displayItem.itemMeta = meta
        ItemTag.tagItem(displayItem, ItemTag.TYPE_GUI_INFO)
        inventory.setItem(13, displayItem)

        // 確定ボタン (スロット15)
        val confirmItem = ItemStack(Material.LIME_CONCRETE)
        val confirmMeta = confirmItem.itemMeta
        confirmMeta?.displayName(Component.text(lang.getMessage(player, "gui.common.confirm")))
        confirmItem.itemMeta = confirmMeta
        ItemTag.tagItem(confirmItem, ItemTag.TYPE_GUI_CONFIRM)
        inventory.setItem(15, confirmItem)

        // キャンセルボタン (スロット11)
        val cancelItem = ItemStack(Material.RED_CONCRETE)
        val cancelMeta = cancelItem.itemMeta
        cancelMeta?.displayName(Component.text(lang.getMessage(player, "gui.common.cancel")))
        cancelItem.itemMeta = cancelMeta
        ItemTag.tagItem(cancelItem, ItemTag.TYPE_GUI_CANCEL)
        inventory.setItem(11, cancelItem)

        player.openInventory(inventory)
    }
}
