package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.PortalData
import me.awabi2048.myworldmanager.util.PortalItemUtil
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.Inventory
import java.util.UUID
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent

class PortalGui(private val plugin: MyWorldManager) : Listener {

    private val colors = listOf(
        Color.WHITE, Color.SILVER, Color.GRAY, Color.BLACK,
        Color.RED, Color.MAROON, Color.YELLOW, Color.OLIVE,
        Color.LIME, Color.GREEN, Color.AQUA, Color.TEAL,
        Color.BLUE, Color.NAVY, Color.FUCHSIA, Color.PURPLE,
        Color.ORANGE
    )

    fun open(player: Player, portal: PortalData) {
        val lang = plugin.languageManager
        val titleKey = "gui.portal.title"
        if (!lang.hasKey(player, titleKey)) {
            player.sendMessage("§c[MyWorldManager] Error: Missing translation key: $titleKey")
            return
        }
        
        // 現在開いているインベントリのタイトルと一致する場合は音を鳴らさない（画面更新とみなす）
        // 現在開いているインベントリのタイトルと一致する場合は音を鳴らさない（画面更新とみなす）
        val title = lang.getMessage(player, titleKey)
        me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(plugin, player, "portal", Component.text(title))
        
        val inventory = Bukkit.createInventory(null, 27, Component.text(title))

        val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
        val greyPane = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE)

        for (i in 0..8) inventory.setItem(i, blackPane)
        for (i in 18..26) inventory.setItem(i, blackPane)
        for (i in 9..17) inventory.setItem(i, greyPane)

        val textStatus = if (portal.showText) lang.getMessage(player, "messages.status_on") else lang.getMessage(player, "messages.status_off")
        inventory.setItem(11, createItem(Material.OAK_SIGN, lang.getMessage(player, "gui.portal.toggle_text.name"), listOf(lang.getMessage(player, "gui.portal.toggle_text.current", textStatus), "", lang.getMessage(player, "gui.portal.toggle_text.click")), ItemTag.TYPE_GUI_PORTAL_TOGGLE_TEXT))

        // 色アイコン決定処理
        val colorKey = when (portal.particleColor) {
            Color.WHITE -> "white"; Color.SILVER -> "silver"; Color.GRAY -> "gray"; Color.BLACK -> "black"
            Color.RED -> "red"; Color.MAROON -> "maroon"; Color.YELLOW -> "yellow"; Color.OLIVE -> "olive"
            Color.LIME -> "lime"; Color.GREEN -> "green"; Color.AQUA -> "aqua"; Color.TEAL -> "teal"
            Color.BLUE -> "blue"; Color.NAVY -> "navy"; Color.FUCHSIA -> "fuchsia"; Color.PURPLE -> "purple"
            Color.ORANGE -> "orange"
            else -> "white"
        }
        val currentIndex = colors.indexOf(portal.particleColor)
        val nextIndex = (currentIndex + 1) % colors.size
        val prevIndex = (currentIndex + colors.size - 1) % colors.size
        
        val colorName = lang.getMessage(player, "gui.portal.colors.$colorKey")
        val nextColorKey = getColorKey(colors[nextIndex])
        val prevColorKey = getColorKey(colors[prevIndex])
        val nextColorName = lang.getMessage(player, "gui.portal.colors.$nextColorKey")
        val prevColorName = lang.getMessage(player, "gui.portal.colors.$prevColorKey")

        inventory.setItem(13, createItem(
            getWoolColor(portal.particleColor),
            lang.getMessage(player, "gui.portal.color.name"), 
            listOf(
                lang.getMessage(player, "gui.portal.color.current", colorName),
                "", 
                lang.getMessage(player, "gui.portal.color.click_prev", prevColorName),
                lang.getMessage(player, "gui.portal.color.click_next", nextColorName)
            ),
            ItemTag.TYPE_GUI_PORTAL_CYCLE_COLOR
        ))
        
        inventory.setItem(15, createItem(Material.LAVA_BUCKET, lang.getMessage(player, "gui.portal.remove.name"), listOf(lang.getMessage(player, "gui.portal.remove.desc"), "", lang.getMessage(player, "gui.portal.remove.click")), ItemTag.TYPE_GUI_PORTAL_REMOVE))

        // Portal IDの保持
        val metaItem = inventory.getItem(0)!!
        val meta = metaItem.itemMeta ?: return
        meta.lore(listOf(lang.getComponent(player, "gui.portal.id_format", portal.id)))
        metaItem.itemMeta = meta

        // 背景埋め
        for (i in 0 until inventory.size) {
            if (inventory.getItem(i) == null) inventory.setItem(i, greyPane)
        }

        player.openInventory(inventory)
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val lang = plugin.languageManager
        val view = event.view
        val player = event.whoClicked as? Player ?: return
        val title = PlainTextComponentSerializer.plainText().serialize(view.title())
        if (!lang.isKeyMatch(title, "gui.portal.title")) return
        
        event.isCancelled = true
        val inv = event.inventory
        
        val firstItem = inv.getItem(0) ?: return
        val firstLore = firstItem.itemMeta?.lore() ?: return
        val portalIdStr = PlainTextComponentSerializer.plainText().serialize(firstLore[0]).substringAfter("PORTAL_ID: ").trim()
        val portalId = try { UUID.fromString(portalIdStr) } catch(e: Exception) { return }
        val portal = plugin.portalRepository.findAll().find { it.id == portalId } ?: return

        val item = event.currentItem ?: return
        val type = ItemTag.getType(item)

        when (type) {
            ItemTag.TYPE_GUI_PORTAL_TOGGLE_TEXT -> {
                plugin.soundManager.playClickSound(player, item)
                portal.showText = !portal.showText
                plugin.portalRepository.saveAll()
                open(player, portal)
            }
            ItemTag.TYPE_GUI_PORTAL_CYCLE_COLOR -> {
                plugin.soundManager.playClickSound(player, item)
                val currentIndex = colors.indexOf(portal.particleColor)
                val nextIndex = if (event.isLeftClick) {
                    (currentIndex + colors.size - 1) % colors.size
                } else {
                    (currentIndex + 1) % colors.size
                }
                portal.particleColor = colors[nextIndex]
                plugin.portalRepository.saveAll()
                open(player, portal)
            }
            ItemTag.TYPE_GUI_PORTAL_REMOVE -> {
                plugin.soundManager.playClickSound(player, item)
                val world = Bukkit.getWorld(portal.worldName)
                val block = world?.getBlockAt(portal.x, portal.y, portal.z)
                if (block != null && block.type == Material.END_PORTAL_FRAME) {
                    block.type = Material.AIR
                }
                plugin.portalManager.removePortalVisuals(portal.id)
                plugin.portalRepository.removePortal(portal.id)
                
                val returnItem = PortalItemUtil.createBasePortalItem(lang, player)
                if (portal.worldUuid != null) {
                    val destData = plugin.worldConfigRepository.findByUuid(portal.worldUuid!!)
                    PortalItemUtil.bindWorld(returnItem, portal.worldUuid!!, worldName = destData?.name ?: lang.getMessage(player, "general.unknown"), lang, player)
                } else if (portal.targetWorldName != null) {
                    val displayName = plugin.config.getString("portal_targets.${portal.targetWorldName}") ?: portal.targetWorldName!!
                    PortalItemUtil.bindExternalWorld(returnItem, portal.targetWorldName!!, displayName, lang, player)
                }
                
                player.inventory.addItem(returnItem)
                player.sendMessage(lang.getMessage(player, "messages.portal_removed"))
                player.closeInventory()
            }
        }
    }

    private fun createItem(material: Material, name: String, loreLines: List<String>, type: String): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        
        meta.displayName(LegacyComponentSerializer.legacySection().deserialize(name).decoration(TextDecoration.ITALIC, false))
        meta.lore(loreLines.map { LegacyComponentSerializer.legacySection().deserialize(it).decoration(TextDecoration.ITALIC, false) })
        
        item.itemMeta = meta
        ItemTag.tagItem(item, type)
        return item
    }

    private fun createDecorationItem(material: Material): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.displayName(Component.empty())
        meta.isHideTooltip = true
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_DECORATION)
        return item
    }

    private fun getWoolColor(color: Color): Material {
        return when (color) {
            Color.WHITE -> Material.WHITE_WOOL; Color.SILVER -> Material.LIGHT_GRAY_WOOL; Color.GRAY -> Material.GRAY_WOOL; Color.BLACK -> Material.BLACK_WOOL
            Color.RED -> Material.RED_WOOL; Color.MAROON -> Material.RED_WOOL; Color.YELLOW -> Material.YELLOW_WOOL; Color.OLIVE -> Material.GREEN_WOOL
            Color.LIME -> Material.LIME_WOOL; Color.GREEN -> Material.GREEN_WOOL; Color.AQUA -> Material.LIGHT_BLUE_WOOL; Color.TEAL -> Material.CYAN_WOOL
            Color.BLUE -> Material.BLUE_WOOL; Color.NAVY -> Material.BLUE_WOOL; Color.FUCHSIA -> Material.MAGENTA_WOOL; Color.PURPLE -> Material.PURPLE_WOOL
            Color.ORANGE -> Material.ORANGE_WOOL
            else -> Material.WHITE_WOOL
        }
    }

    private fun getColorKey(color: Color): String {
        return when (color) {
            Color.WHITE -> "white"; Color.SILVER -> "silver"; Color.GRAY -> "gray"; Color.BLACK -> "black"
            Color.RED -> "red"; Color.MAROON -> "maroon"; Color.YELLOW -> "yellow"; Color.OLIVE -> "olive"
            Color.LIME -> "lime"; Color.GREEN -> "green"; Color.AQUA -> "aqua"; Color.TEAL -> "teal"
            Color.BLUE -> "blue"; Color.NAVY -> "navy"; Color.FUCHSIA -> "fuchsia"; Color.PURPLE -> "purple"
            Color.ORANGE -> "orange"
            else -> "white"
        }
    }
}

