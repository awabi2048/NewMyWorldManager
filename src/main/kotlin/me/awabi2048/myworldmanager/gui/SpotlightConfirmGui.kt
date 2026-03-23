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
        val title = me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(lang.getComponent(player, "gui.spotlight_confirm.title"))
        me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(plugin, player, "spotlight_confirm", title, null)
        val inventory = Bukkit.createInventory(null, 45, title)
        me.awabi2048.myworldmanager.util.GuiHelper.applyConfirmationFrame(inventory)

        // 情報
        val infoItem = ItemStack(Material.PAPER)
        val infoMeta = infoItem.itemMeta
        infoMeta.displayName(lang.getComponent(player, "gui.spotlight_confirm.title"))
        infoMeta.lore(lang.getComponentList(player, "gui.spotlight_confirm.lore", mapOf("world" to worldData.name)))
        infoItem.itemMeta = infoMeta
        inventory.setItem(22, infoItem)

        // はい
        val yesItem = ItemStack(Material.LIME_CONCRETE)
        val yesMeta = yesItem.itemMeta
        yesMeta.displayName(lang.getComponent(player, "gui.common.confirm"))
        yesItem.itemMeta = yesMeta
        ItemTag.tagItem(yesItem, "spotlight_confirm_yes")
        ItemTag.setWorldUuid(yesItem, worldData.uuid)
        inventory.setItem(20, yesItem)

        // いいえ
        val noItem = ItemStack(Material.RED_CONCRETE)
        val noMeta = noItem.itemMeta
        noMeta.displayName(lang.getComponent(player, "gui.common.cancel"))
        noItem.itemMeta = noMeta
        ItemTag.tagItem(noItem, "spotlight_confirm_no")
        inventory.setItem(24, noItem)

        player.openInventory(inventory)
    }
}
