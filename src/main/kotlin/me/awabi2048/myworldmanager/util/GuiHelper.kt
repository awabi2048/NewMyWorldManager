package me.awabi2048.myworldmanager.util

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiConfirmationLayout
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiPagedListLayout
import com.awabi2048.ccsystem.api.gui.GuiSettingsLayout
import com.awabi2048.ccsystem.api.gui.GuiThreeChoiceLayout
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.WorldData
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

object GuiHelper {
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

    fun confirmationLayout(): GuiConfirmationLayout = layoutService.confirmation45()

    fun pagedListLayout(): GuiPagedListLayout = layoutService.pagedList54()

    fun settingsLayout(): GuiSettingsLayout = layoutService.settings54()

    fun threeChoiceLayout(): GuiThreeChoiceLayout = layoutService.threeChoice45()

    fun createConfirmationInventory(holder: InventoryHolder?, title: Component): Inventory {
        val layout = confirmationLayout()
        return Bukkit.createInventory(holder, layout.size, inventoryTitle(title))
    }

    fun applyConfirmationFrame(inventory: Inventory) {
        layoutService.applyStandardFrame(inventory)
    }

    fun setConfirmationItems(
        inventory: Inventory,
        centerItem: ItemStack,
        confirmItem: ItemStack,
        cancelItem: ItemStack
    ) {
        val layout = confirmationLayout()
        inventory.setItem(layout.previewSlot, centerItem)
        inventory.setItem(layout.confirmSlot, confirmItem)
        inventory.setItem(layout.cancelSlot, cancelItem)
    }

    fun setThreeChoiceItems(inventory: Inventory, leftItem: ItemStack, centerItem: ItemStack, rightItem: ItemStack) {
        val layout = threeChoiceLayout()
        inventory.setItem(layout.leftSlot, leftItem)
        inventory.setItem(layout.centerSlot, centerItem)
        inventory.setItem(layout.rightSlot, rightItem)
    }

    fun setThreeChoiceBack(inventory: Inventory, backItem: ItemStack) {
        inventory.setItem(threeChoiceLayout().backSlot, backItem)
    }

    fun setSettingsFooter(inventory: Inventory, backItem: ItemStack?, infoItem: ItemStack?) {
        val layout = settingsLayout()
        backItem?.let { inventory.setItem(layout.backSlot, it) }
        infoItem?.let { inventory.setItem(layout.infoSlot, it) }
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
        if (plugin.menuRouteHistory.openPrevious(player)) {
            return
        }

        // 全てのセッション終了を試みる（安全のため）
        plugin.settingsSessionManager.endSession(player)

        player.closeInventory()
    }

    fun createContextWorldIconItem(
        plugin: MyWorldManager,
        player: Player,
        worldData: WorldData,
        lore: List<Component> = emptyList(),
        attachWorldUuid: Boolean = true
    ): org.bukkit.inventory.ItemStack {
        val renderedLore = if (lore.isEmpty()) emptyList() else GuiItemFactory.componentMenuLore(lore)
        return createContextWorldIconItemRendered(plugin, player, worldData, renderedLore, attachWorldUuid)
    }

    fun createContextWorldIconItem(
        plugin: MyWorldManager,
        player: Player,
        worldData: WorldData,
        lore: GuiLoreSpec,
        attachWorldUuid: Boolean = true
    ): org.bukkit.inventory.ItemStack {
        return createContextWorldIconItemRendered(
            plugin,
            player,
            worldData,
            CCSystem.getAPI().getLoreService().render(lore),
            attachWorldUuid
        )
    }

    private fun createContextWorldIconItemRendered(
        plugin: MyWorldManager,
        player: Player,
        worldData: WorldData,
        renderedLore: List<Component>,
        attachWorldUuid: Boolean
    ): org.bukkit.inventory.ItemStack {
        val lang = plugin.languageManager
        val item = org.bukkit.inventory.ItemStack(worldData.icon)
        val meta = item.itemMeta ?: return item

        val worldName = lang.getMessageStrict(player, worldData.name) ?: worldData.name
        meta.displayName(
            lang.getComponent(player, "gui.common.world_item_name", mapOf("world" to worldName))
                .decoration(TextDecoration.ITALIC, false)
        )
        if (renderedLore.isNotEmpty()) {
            meta.lore(renderedLore)
        }

        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_INFO)
        if (attachWorldUuid) {
            ItemTag.setWorldUuid(item, worldData.uuid)
        }
        return item
    }

    private fun createNavigationItem(player: Player, material: org.bukkit.inventory.ItemStack, name: String, targetPage: Int, isNext: Boolean): org.bukkit.inventory.ItemStack {
        // Overload to accept ItemStack if needed, but below uses Material
        return createNavigationItem(player, material.type, name, targetPage, isNext)
    }

    private fun createNavigationItem(player: Player, material: org.bukkit.Material, name: String, targetPage: Int, isNext: Boolean): org.bukkit.inventory.ItemStack {
        val item = org.bukkit.inventory.ItemStack(material)
        val meta = item.itemMeta ?: return item

        meta.displayName(GuiItemFactory.legacy(name))

        item.itemMeta = meta
        ItemTag.setTargetPage(item, targetPage)
        val type = if (isNext) ItemTag.TYPE_GUI_NAV_NEXT else ItemTag.TYPE_GUI_NAV_PREV
        ItemTag.tagItem(item, type)
        return item
    }
}

fun InventoryClickEvent.cancelWithDebug(source: String, force: Boolean = false) {
    val player = this.whoClicked as? Player ?: return
    val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(MyWorldManager::class.java)
    if (!force && !shouldCancelProtectedMenuClick()) return
    val parts = source.split(": ", limit = 2)
    val path = parts[0]
    val reason = parts.getOrElse(1) { "unspecified" }
    plugin.logger.info("[ClickCancel] player=${player.name} | source=${path} | reason=${reason} | title=${this.view.title()} | slot=${this.slot} | click=${this.click} | action=${this.action}")
    this.isCancelled = true
}

private fun InventoryClickEvent.shouldCancelProtectedMenuClick(): Boolean {
    if (clickedInventory == view.topInventory) return true
    if (clickedInventory != view.bottomInventory) return false
    return action == InventoryAction.MOVE_TO_OTHER_INVENTORY ||
        action == InventoryAction.COLLECT_TO_CURSOR ||
        action == InventoryAction.HOTBAR_SWAP ||
        action == InventoryAction.HOTBAR_MOVE_AND_READD ||
        action == InventoryAction.UNKNOWN
}
