package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.PortalData
import me.awabi2048.myworldmanager.util.GuiItemFactory
import me.awabi2048.myworldmanager.util.PortalItemUtil
import me.awabi2048.myworldmanager.util.ItemTag
import me.awabi2048.myworldmanager.util.WorldGateItemUtil
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import java.util.UUID
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import me.awabi2048.myworldmanager.util.cancelWithDebug

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
        val titleComponent = me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(title)
        me.awabi2048.myworldmanager.util.GuiHelper.playMenuOpen(player, "portal")

        val holder = PortalHolder(portal.id)
        val inventory = Bukkit.createInventory(holder, 27, titleComponent)
        holder.inv = inventory

        GuiItemFactory.applyStandardFrame(inventory)

        val textStatus = if (portal.showText) lang.getMessage(player, "messages.status_on") else lang.getMessage(player, "messages.status_off")
        inventory.setItem(11, structuredItem(
            player,
            Material.OAK_SIGN,
            "gui.portal.toggle_text",
            listOf(GuiLoreLine.Data(lang.getMessage(player, "gui.portal.toggle_text.current_label"), textStatus, "§e")),
            ItemTag.TYPE_GUI_PORTAL_TOGGLE_TEXT
        ))

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

        val colorName = lang.getMessage(player, "colors.$colorKey")
        val nextColorKey = getColorKey(colors[nextIndex])
        val prevColorKey = getColorKey(colors[prevIndex])
        val nextColorName = lang.getMessage(player, "colors.$nextColorKey")
        val prevColorName = lang.getMessage(player, "colors.$prevColorKey")

        inventory.setItem(13, structuredItem(
            player,
            getWoolColor(portal.particleColor),
            "gui.portal.color",
            listOf(
                GuiLoreLine.Data(lang.getMessage(player, "gui.portal.color.current_label"), colorName, "§e"),
                GuiLoreLine.Text(lang.getMessage(player, "gui.portal.color.previous", mapOf("color" to prevColorName))),
                GuiLoreLine.Text(lang.getMessage(player, "gui.portal.color.next", mapOf("color" to nextColorName)))
            ),
            ItemTag.TYPE_GUI_PORTAL_CYCLE_COLOR
        ))

        inventory.setItem(15, structuredItem(
            player,
            Material.LAVA_BUCKET,
            "gui.portal.remove",
            listOf(GuiLoreLine.Danger(lang.getMessage(player, "gui.portal.remove.description"))),
            ItemTag.TYPE_GUI_PORTAL_REMOVE
        ))

        // Portal IDの保持
        val metaItem = inventory.getItem(0)!!
        val meta = metaItem.itemMeta ?: return
        meta.lore(GuiItemFactory.menuLore(listOf(
            GuiLoreLine.Data(lang.getMessage(player, "gui.portal.id_label"), portal.id, "§8")
        )))
        metaItem.itemMeta = meta

        player.openInventory(inventory)
    }

    private fun structuredItem(
        player: Player,
        material: Material,
        key: String,
        information: List<GuiLoreLine>,
        tag: String
    ): ItemStack {
        val lang = plugin.languageManager
        return GuiItemFactory.item(
            material,
            lang.getMessage(player, "$key.name"),
            GuiLoreSpec.Blocks(listOf(
                GuiLoreBlock(information),
                GuiLoreBlock(listOf(me.awabi2048.myworldmanager.util.GuiLoreActions.singleClick(
                    lang,
                    player,
                    lang.getMessage(player, "$key.action")
                )))
            )),
            tag
        )
    }

    @EventHandler(ignoreCancelled = false)
    fun onClick(event: InventoryClickEvent) {
        val lang = plugin.languageManager
        val player = event.whoClicked as? Player ?: return
        val holder = event.view.topInventory.holder as? PortalHolder ?: return

        event.cancelWithDebug("PortalGui.onClick: portal GUI click")
        if (event.clickedInventory != event.view.topInventory) return
        val portal = plugin.portalRepository.findAll().find { it.id == holder.portalId } ?: return

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
                  val refundResult = if (portal.isGate()) plugin.portalManager.refundPointsForRemovedGate(portal) else null

                  // ビジュアル要素を先に削除（タイミング問題を防ぐ）
                  plugin.portalManager.removePortalVisuals(portal.id)

                 // その後、リポジトリから削除
                 plugin.portalRepository.removePortal(portal.id)

                  if (!portal.isGate()) {
                      val world = Bukkit.getWorld(portal.worldName)
                      val block = world?.getBlockAt(portal.x, portal.y, portal.z)
                      if (block != null && block.type == Material.END_PORTAL_FRAME) {
                          block.type = Material.AIR
                      }
                  }

                val returnItem = if (portal.isGate()) {
                    WorldGateItemUtil.createBaseWorldGateItem(lang, player)
                } else {
                    PortalItemUtil.createBasePortalItem(lang, player)
                }
                if (portal.worldUuid != null) {
                    val destData = plugin.worldConfigRepository.findByUuid(portal.worldUuid!!)
                    if (portal.isGate()) {
                        WorldGateItemUtil.bindWorld(returnItem, portal.worldUuid!!, worldName = destData?.name ?: lang.getMessage(player, "general.unknown"), lang, player)
                    } else {
                        PortalItemUtil.bindWorld(returnItem, portal.worldUuid!!, worldName = destData?.name ?: lang.getMessage(player, "general.unknown"), lang, player)
                    }
                } else if (portal.targetWorldName != null) {
                    val displayName = plugin.config.getString("portal_targets.${portal.targetWorldName}") ?: portal.targetWorldName!!
                    if (portal.isGate()) {
                        WorldGateItemUtil.bindExternalWorld(returnItem, portal.targetWorldName!!, displayName, lang, player)
                    } else {
                        PortalItemUtil.bindExternalWorld(returnItem, portal.targetWorldName!!, displayName, lang, player)
                    }
                }

                player.inventory.addItem(returnItem)
                if (portal.isGate()) {
                    val ownerName = Bukkit.getOfflinePlayer(portal.ownerUuid).name ?: portal.ownerUuid.toString()
                    player.sendMessage(
                        lang.getMessage(
                            player,
                            "messages.world_gate_removed_refund",
                            mapOf(
                                "points" to (refundResult?.points ?: 0),
                                "percent" to (refundResult?.percent ?: 0),
                                "owner" to ownerName
                            )
                        )
                    )
                } else {
                    player.sendMessage(lang.getMessage(player, "messages.portal_removed"))
                }
                player.closeInventory()
            }
        }
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

    private class PortalHolder(val portalId: UUID) : InventoryHolder {
        lateinit var inv: Inventory
        override fun getInventory(): Inventory = inv
    }
}
