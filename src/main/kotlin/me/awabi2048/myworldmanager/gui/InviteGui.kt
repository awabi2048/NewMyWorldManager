package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.util.GuiItemFactory
import me.awabi2048.myworldmanager.util.GuiLoreBuilder
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class InviteGui(private val plugin: MyWorldManager) {

    private val playerSlots = listOf(
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    )

    fun collectAvailableTargets(viewer: Player): List<Player> {
        return Bukkit.getOnlinePlayers().filter { target ->
            target.uniqueId != viewer.uniqueId &&
                target.world.uid != viewer.world.uid &&
                plugin.playerStatsRepository.findByUuid(target.uniqueId).meetStatus != "BUSY"
        }.sortedBy { it.name }
    }

    fun open(player: Player, showBackButton: Boolean = false): Boolean {
        val lang = plugin.languageManager
        val targets = collectAvailableTargets(player)
        if (targets.isEmpty()) {
            player.sendMessage(lang.getMessage(player, "messages.invite_no_available_targets"))
            return false
        }

        val title = me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(lang.getMessage(player, "gui.meet.title_list"))
        me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(
            plugin,
            player,
            "meet",
            title,
            InviteGuiHolder::class.java
        )

        val userCount = targets.size
        val rowCount = if (userCount <= 7) 3 else if (userCount <= 14) 4 else if (userCount <= 21) 5 else 6
        val statusSlot = (rowCount - 1) * 9 + 4
        val holder = InviteGuiHolder()
        val inventory = Bukkit.createInventory(holder, rowCount * 9, title)
        holder.inv = inventory

        val blackPane = GuiItemFactory.decoration(Material.BLACK_STAINED_GLASS_PANE)
        val greyPane = GuiItemFactory.decoration(Material.GRAY_STAINED_GLASS_PANE)

        for (i in 0..8) inventory.setItem(i, blackPane)
        for (i in (rowCount - 1) * 9 until rowCount * 9) inventory.setItem(i, blackPane)
        for (i in 9 until (rowCount - 1) * 9) inventory.setItem(i, greyPane)

        val headSlots = playerSlots.filter { it < inventory.size && it != statusSlot }
        targets.take(headSlots.size).forEachIndexed { index, target ->
            val slot = headSlots[index]
            inventory.setItem(slot, createTargetHead(target, player))
        }

        val currentWorldData = plugin.worldConfigRepository.findByWorldName(player.world.name)
        if (currentWorldData != null) {
            val statusLore = listOf(
                lang.getComponent(player, "gui.common.separator"),
                lang.getComponent(player, "gui.meet.world_item.current_world", mapOf("world" to currentWorldData.name)),
                lang.getComponent(player, "gui.common.separator")
            )
            inventory.setItem(
                statusSlot,
                me.awabi2048.myworldmanager.util.GuiHelper.createContextWorldIconItem(
                    plugin,
                    player,
                    currentWorldData,
                    statusLore
                )
            )
        }

        if (showBackButton) {
            val backButtonSlot = (rowCount - 1) * 9
            inventory.setItem(backButtonSlot, me.awabi2048.myworldmanager.util.GuiHelper.createReturnItem(plugin, player, "meet"))
        }

        player.openInventory(inventory)
        return true
    }

    private fun createTargetHead(target: Player, viewer: Player): ItemStack {
        val lang = plugin.languageManager
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as? org.bukkit.inventory.meta.SkullMeta ?: return item

        meta.owningPlayer = target
        val colorCode = lang.getMessage(viewer, "publish_level.color.online")
        meta.displayName(
            LegacyComponentSerializer.legacySection().deserialize("$colorCode${target.name}")
                .decoration(TextDecoration.ITALIC, false)
        )

        val status = plugin.playerStatsRepository.findByUuid(target.uniqueId).meetStatus
        val statusKey = "general.status.${status.lowercase()}"
        val statusName = if (lang.hasKey(viewer, statusKey)) lang.getMessage(viewer, statusKey) else status
        val lore = GuiLoreBuilder(lang, viewer)
            .componentBlock(listOf(lang.getComponent(viewer, "gui.meet.world_item.status", mapOf("status" to statusName))))
            .componentBlock(listOf(lang.getComponent(viewer, "gui.invite.target_head.click_invite")))
            .build()

        meta.lore(lore)
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_INVITE_TARGET_HEAD)
        ItemTag.setString(item, "invite_target_uuid", target.uniqueId.toString())
        ItemTag.setString(item, "invite_target_name", target.name)
        return item
    }

    class InviteGuiHolder : org.bukkit.inventory.InventoryHolder {
        lateinit var inv: org.bukkit.inventory.Inventory
        override fun getInventory(): org.bukkit.inventory.Inventory = inv
    }
}
