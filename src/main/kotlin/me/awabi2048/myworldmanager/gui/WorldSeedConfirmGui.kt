package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class WorldSeedConfirmGui(private val plugin: MyWorldManager) {

    fun open(player: Player, currentSlots: Int, nextSlots: Int) {
        val lang = plugin.languageManager
        val title = me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(lang.getComponent(player, "gui.world_seed_confirm.title"))
        player.playSound(player.location, org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f)
        val inventory = GuiHelper.createConfirmationInventory(null, title)
        me.awabi2048.myworldmanager.util.GuiHelper.applyConfirmationFrame(inventory)

        // 情報
        val infoItem = ItemStack(Material.PAPER)
        val infoMeta = infoItem.itemMeta
        infoMeta.displayName(lang.getComponent(player, "gui.world_seed_confirm.title"))
        infoMeta.lore(me.awabi2048.myworldmanager.util.GuiItemFactory.menuLore(
            listOf(
                com.awabi2048.ccsystem.api.gui.GuiLoreLine.Warning(lang.getMessage(player, "gui.world_seed_confirm.question")),
                com.awabi2048.ccsystem.api.gui.GuiLoreLine.Data(lang.getMessage(player, "gui.world_seed_confirm.current_label"), currentSlots, "§a"),
                com.awabi2048.ccsystem.api.gui.GuiLoreLine.Data(lang.getMessage(player, "gui.world_seed_confirm.next_label"), nextSlots, "§a")
            ) + lang.getMessageList(player, "gui.world_seed_confirm.description").map(com.awabi2048.ccsystem.api.gui.GuiLoreLine::Text)
        ))
        infoItem.itemMeta = infoMeta

        // はい
        val yesItem = ItemStack(Material.LIME_CONCRETE)
        val yesMeta = yesItem.itemMeta
        yesMeta.displayName(lang.getComponent(player, "gui.common.confirm"))
        yesItem.itemMeta = yesMeta
        ItemTag.tagItem(yesItem, "world_seed_confirm_yes")

        // いいえ
        val noItem = ItemStack(Material.RED_CONCRETE)
        val noMeta = noItem.itemMeta
        noMeta.displayName(lang.getComponent(player, "gui.common.cancel"))
        noItem.itemMeta = noMeta
        ItemTag.tagItem(noItem, "world_seed_confirm_no")
        GuiHelper.setConfirmationItems(inventory, infoItem, yesItem, noItem)

        player.openInventory(inventory)
    }
}
