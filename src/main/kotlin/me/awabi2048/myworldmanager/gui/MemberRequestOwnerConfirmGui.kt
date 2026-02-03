package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.service.MemberRequestInfo
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

class MemberRequestOwnerConfirmGui(private val plugin: MyWorldManager) {

    fun open(player: Player, info: MemberRequestInfo, key: String) {
        val lang = plugin.languageManager
        val title = lang.getComponent(player, "gui.member_request_owner_confirm.title").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD)
        me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(plugin, player, "member_request_owner_confirm", title, null)
        
        val holder = MemberRequestOwnerConfirmHolder()
        val inventory = Bukkit.createInventory(holder, 27, title)
        holder.inv = inventory

        // 背景
        val grayPane = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        grayPane.itemMeta = grayPane.itemMeta?.apply {
            displayName(Component.empty())
            isHideTooltip = true
        }
        ItemTag.tagItem(grayPane, ItemTag.TYPE_GUI_DECORATION)
        for (i in 0 until 27) inventory.setItem(i, grayPane)

        // 情報
        val requestorName = Bukkit.getPlayer(info.requestorUuid)?.name ?: "Unknown"
        val infoItem = ItemStack(Material.PAPER)
        val infoMeta = infoItem.itemMeta
        infoMeta.displayName(lang.getComponent(player, "gui.member_request_owner_confirm.title"))
        infoMeta.lore(lang.getComponentList(player, "gui.member_request_owner_confirm.lore", mapOf("player" to requestorName)))
        infoItem.itemMeta = infoMeta
        inventory.setItem(13, infoItem)

        // 承認 (11)
        val yesItem = ItemStack(Material.LIME_CONCRETE)
        val yesMeta = yesItem.itemMeta
        yesMeta.displayName(lang.getComponent(player, "gui.member_request_owner_confirm.confirm"))
        yesItem.itemMeta = yesMeta
        ItemTag.tagItem(yesItem, "member_request_owner_yes")
        ItemTag.setString(yesItem, "key", key)
        inventory.setItem(11, yesItem)

        // 却下 (15)
        val noItem = ItemStack(Material.RED_CONCRETE)
        val noMeta = noItem.itemMeta
        noMeta.displayName(lang.getComponent(player, "gui.member_request_owner_confirm.reject"))
        noItem.itemMeta = noMeta
        ItemTag.tagItem(noItem, "member_request_owner_no")
        ItemTag.setString(noItem, "key", key)
        inventory.setItem(15, noItem)

        player.openInventory(inventory)
    }

    class MemberRequestOwnerConfirmHolder : InventoryHolder {
        lateinit var inv: Inventory
        override fun getInventory(): Inventory = inv
    }
}
