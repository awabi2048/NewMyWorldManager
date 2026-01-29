package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.*

class SpotlightConfirmGui(private val plugin: MyWorldManager) {

    fun open(player: Player, worldData: WorldData) {
        val lang = plugin.languageManager
        val title = lang.getComponent(player, "gui.spotlight_confirm.title").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD)
        plugin.soundManager.playMenuOpenSound(player, "spotlight_confirm")
        val inventory = Bukkit.createInventory(null, 27, title)

        // 背景
        val blackPane = ItemStack(Material.BLACK_STAINED_GLASS_PANE)
        blackPane.itemMeta = blackPane.itemMeta.apply {
            displayName(Component.empty())
            isHideTooltip = true
        }
        ItemTag.tagItem(blackPane, ItemTag.TYPE_GUI_DECORATION)

        val grayPane = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        grayPane.itemMeta = grayPane.itemMeta.apply {
            displayName(Component.empty())
            isHideTooltip = true
        }
        ItemTag.tagItem(grayPane, ItemTag.TYPE_GUI_DECORATION)

        // 1行目: 黒
        for (i in 0..8) inventory.setItem(i, blackPane)
        // 2行目: 灰
        for (i in 9..17) inventory.setItem(i, grayPane)
        // 3行目: 黒
        for (i in 18..26) inventory.setItem(i, blackPane)

        // 情報
        val infoItem = ItemStack(Material.PAPER)
        val infoMeta = infoItem.itemMeta
        infoMeta.displayName(lang.getComponent(player, "gui.spotlight_confirm.title"))
        infoMeta.lore(lang.getComponentList(player, "gui.spotlight_confirm.lore", worldData.name))
        infoItem.itemMeta = infoMeta
        inventory.setItem(13, infoItem)

        // はい (11)
        val yesItem = ItemStack(Material.LIME_CONCRETE)
        val yesMeta = yesItem.itemMeta
        yesMeta.displayName(lang.getComponent(player, "gui.common.confirm"))
        yesItem.itemMeta = yesMeta
        ItemTag.tagItem(yesItem, "spotlight_confirm_yes")
        ItemTag.setWorldUuid(yesItem, worldData.uuid)
        inventory.setItem(11, yesItem)

        // いいえ (15)
        val noItem = ItemStack(Material.RED_CONCRETE)
        val noMeta = noItem.itemMeta
        noMeta.displayName(lang.getComponent(player, "gui.common.cancel"))
        noItem.itemMeta = noMeta
        ItemTag.tagItem(noItem, "spotlight_confirm_no")
        inventory.setItem(15, noItem)

        player.openInventory(inventory)
    }
}
