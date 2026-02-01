package me.awabi2048.myworldmanager.util

import me.awabi2048.myworldmanager.MyWorldManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.Player

object GuiHelper {
    /**
     * Plays the menu open sound only if the menu title is different from the currently open inventory's title,
     * OR if the inventory holder class is different.
     * This prevents the sound from playing when simply refreshing the menu or navigating pages.
     */
    fun playMenuSoundIfTitleChanged(plugin: MyWorldManager, player: Player, menuId: String, newTitle: Component, targetHolderClass: Class<*>? = null) {
        val currentInventory = player.openInventory.topInventory
        val currentTitle = player.openInventory.title()
        
        // Serialize to plain text to compare content while ignoring minor formatting differences if any,
        // and to handle the case where correct comparison allows skipping sound on refresh.
        val currentTitleStr = PlainTextComponentSerializer.plainText().serialize(currentTitle)
        val newTitleStr = PlainTextComponentSerializer.plainText().serialize(newTitle)

        // Check if the current inventory holder matches the target holder class
        val isSameHolder = targetHolderClass != null && targetHolderClass.isInstance(currentInventory.holder)

        // If the titles are different AND the holder is also different (or not specified), it means we are opening a new menu type or context.
        // If they are the same, it's likely a page update or refresh, so suppress sound.
        // If the holder is the same, it's definitely a refresh of the same menu type.
        if (currentTitleStr != newTitleStr && !isSameHolder) {
            plugin.soundManager.playMenuOpenSound(player, menuId)
        }
    }

    /**
     * Arrayの中から現在の値の次（または前）の値を取得します。
     */
    fun <T> getNextValue(current: T, values: Array<T>, isRightClick: Boolean): T {
        val index = values.indexOf(current).let { if (it == -1) 0 else it }
        return if (isRightClick) {
            values[(index + 1) % values.size]
        } else {
            values[(index + values.size - 1) % values.size]
        }
    }

    /**
     * Listの中から現在の値の次（または前）の値を取得します。
     */
    fun <T> getNextValue(current: T, values: List<T>, isRightClick: Boolean): T {
        val index = values.indexOf(current).let { if (it == -1) 0 else it }
        return if (isRightClick) {
            values[(index + 1) % values.size]
        } else {
            values[(index + values.size - 1) % values.size]
        }
    }

    /**
     * GUI遷移中フラグを一定時間後に解除します。
     */
    fun scheduleGuiTransitionReset(plugin: MyWorldManager, player: Player) {
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val session = plugin.settingsSessionManager.getSession(player)
            if (session != null && session.isGuiTransition) {
                session.isGuiTransition = false
            }
        }, 5L)
    }

    /*
     * MenuConfigManagerを使用してア イコンMaterialを取得し、アイテムを作成するヘルパーメソッド
     */

    fun createNextPageItem(plugin: MyWorldManager, player: Player, menuId: String, targetPage: Int): org.bukkit.inventory.ItemStack {
        val material = plugin.menuConfigManager.getIconMaterial(menuId, "next_page", org.bukkit.Material.ARROW)
        val name = plugin.languageManager.getMessage(player, "gui.common.next_page")
        return createNavigationItem(player, material, name, targetPage, true)
    }

    fun createPrevPageItem(plugin: MyWorldManager, player: Player, menuId: String, targetPage: Int): org.bukkit.inventory.ItemStack {
        val material = plugin.menuConfigManager.getIconMaterial(menuId, "prev_page", org.bukkit.Material.ARROW)
        val name = plugin.languageManager.getMessage(player, "gui.common.prev_page")
        return createNavigationItem(player, material, name, targetPage, false)
    }

    fun createReturnItem(plugin: MyWorldManager, player: Player, menuId: String): org.bukkit.inventory.ItemStack {
        val material = plugin.menuConfigManager.getIconMaterial(menuId, "back", org.bukkit.Material.REDSTONE)
        val item = org.bukkit.inventory.ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.displayName(plugin.languageManager.getComponent(player, "gui.common.return").color(net.kyori.adventure.text.format.NamedTextColor.YELLOW).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD))
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_RETURN)
        return item
    }

    fun handleReturnClick(plugin: MyWorldManager, player: Player, item: org.bukkit.inventory.ItemStack) {
        plugin.soundManager.playClickSound(player, item)
        val command = plugin.config.getString("menu_command", "mwm")?.removePrefix("/") ?: "mwm"
        
        // 全てのセッション終了を試みる（安全のため）
        plugin.settingsSessionManager.endSession(player)
        
        player.closeInventory()
        player.performCommand(command)
    }

    /**
     * Deduplicates consecutive separators and removes leading/trailing separators in lore.
     */
    fun cleanupLore(lore: List<Component>, separator: Component): List<Component> {
        val plain = PlainTextComponentSerializer.plainText()
        val separatorText = plain.serialize(separator).trim()
        
        val result = mutableListOf<Component>()
        var lastWasSeparator = false
        
        // Regex to identify various separator-like strings (sequences of hyphens or full-width dashes)
        val separatorRegex = Regex("^[\\-－＝—_]+$")
        
        for (comp in lore) {
            val text = plain.serialize(comp).trim()
            if (text.isBlank()) continue
            
            // Check if it's a separator by:
            // 1. Direct match with current language's separator
            // 2. Matching a sequence of common separator characters
            val isSeparator = text == separatorText || separatorRegex.matches(text)
            
            if (isSeparator) {
                if (!lastWasSeparator) {
                    result.add(comp)
                }
                lastWasSeparator = true
            } else {
                result.add(comp)
                lastWasSeparator = false
            }
        }
        
        return result
    }

    private fun createNavigationItem(player: Player, material: org.bukkit.inventory.ItemStack, name: String, targetPage: Int, isNext: Boolean): org.bukkit.inventory.ItemStack {
        // Overload to accept ItemStack if needed, but below uses Material
        return createNavigationItem(player, material.type, name, targetPage, isNext)
    }

    private fun createNavigationItem(player: Player, material: org.bukkit.Material, name: String, targetPage: Int, isNext: Boolean): org.bukkit.inventory.ItemStack {
        val item = org.bukkit.inventory.ItemStack(material)
        val meta = item.itemMeta ?: return item
        
        meta.displayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(name).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false))
        
        item.itemMeta = meta
        ItemTag.setTargetPage(item, targetPage)
        val type = if (isNext) ItemTag.TYPE_GUI_NAV_NEXT else ItemTag.TYPE_GUI_NAV_PREV
        ItemTag.tagItem(item, type)
        return item
    }
}
