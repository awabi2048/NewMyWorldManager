package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.ui.ManagedMenuPresenter

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.util.GuiItemFactory
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.StructuredLore
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec

class VisitGui(private val plugin: MyWorldManager) {

        private val repository = plugin.worldConfigRepository
        fun open(
                player: Player,
                targetPlayer: OfflinePlayer,
                page: Int = 0,
                returnToWorld: WorldData? = null
        ) {

                val allWorlds = repository.findAll()
                val targetWorlds =
                        allWorlds.filter { world ->
                                if (world.owner != targetPlayer.uniqueId || world.isArchived)
                                        return@filter false

                                val isMember =
                                        world.owner == player.uniqueId ||
                                                world.moderators.contains(player.uniqueId) ||
                                                world.members.contains(player.uniqueId)

                                MyWorldManagerApi.getWorldAccessPolicy().canUseVisitEntry(player, world, isMember)
                        }

                val worldCount = targetWorlds.size
                val lang = plugin.languageManager
                val titleKey = "gui.visit.title"
                if (!lang.hasKey(player, titleKey)) {
                        player.sendMessage(
                                "§c[MyWorldManager] Error: Missing translation key: $titleKey"
                        )
                        return
                }

                val pageLayout = CCSystem.getAPI().getGuiLayoutService().sevenColumnPage(worldCount, page)
                val currentPage = pageLayout.page
                val layout = pageLayout.layout
                val targetName = PlayerNameUtil.getNameOrDefault(targetPlayer.uniqueId, "Unknown")
                val titleComp = me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(lang.getComponent(player, titleKey, mapOf("player" to targetName)))

                me.awabi2048.myworldmanager.util.GuiHelper.playMenuOpen(player, "visit")

                val holder = VisitGuiHolder()
                val inventory = Bukkit.createInventory(holder, layout.size, titleComp)
                holder.inv = inventory

                val blackPane = GuiItemFactory.decoration(Material.BLACK_STAINED_GLASS_PANE)
                if (returnToWorld != null) ItemTag.setWorldUuid(blackPane, returnToWorld.uuid)
                val greyPane = GuiItemFactory.decoration(Material.GRAY_STAINED_GLASS_PANE)
                if (returnToWorld != null) ItemTag.setWorldUuid(greyPane, returnToWorld.uuid)

                GuiItemFactory.applyStandardFrame(inventory, emptyMaterial = null)
                layout.itemSlots.forEachIndexed { index, slot ->
                        inventory.setItem(
                                slot,
                                targetWorlds.drop(pageLayout.startIndex).getOrNull(index)?.let {
                                        createWorldItem(player, it)
                                } ?: greyPane
                        )
                }

                // 戻るボタン
                if (returnToWorld != null) {
                        inventory.setItem(layout.backSlot, createBackButton(player, returnToWorld))
                }
                if (currentPage > 0) {
                        inventory.setItem(layout.previousPageSlot, GuiHelper.createPrevPageItem(plugin, player, "visit", currentPage - 1))
                }
                if (currentPage < pageLayout.totalPages - 1) {
                        inventory.setItem(layout.nextPageSlot, GuiHelper.createNextPageItem(plugin, player, "visit", currentPage + 1))
                }

                GuiItemFactory.fillEmpty(inventory)
                ManagedMenuPresenter.open(player, inventory)
        }

        private fun createWorldItem(viewer: Player, world: WorldData): ItemStack {
                val item = ItemStack(world.icon)
                val meta = item.itemMeta ?: return item
                val lang = plugin.languageManager

                meta.displayName(
                        lang.getComponent(
                                viewer,
                                "gui.common.world_item_name",
                                mapOf("world" to world.name)
                        )
                )

                val ownerName = PlayerNameUtil.getNameOrDefault(world.owner, lang.getMessage(viewer, "general.unknown"))
                val tagNames = if (world.tags.isNotEmpty()) {
                        world.tags.joinToString(", ") {
                                plugin.worldTagManager.getDisplayName(viewer, it)
                        }
                } else null

                val warpAction = lang.getMessage(viewer, "gui.visit.world_item.warp")

                val stats = plugin.playerStatsRepository.findByUuid(viewer.uniqueId)
                val viewerPlayerUuid = viewer.uniqueId
                val isMember = world.owner == viewerPlayerUuid ||
                                world.moderators.contains(viewerPlayerUuid) ||
                                world.members.contains(viewerPlayerUuid)

                val favoriteAction = if (!isMember) {
                        if (stats.favoriteWorlds.containsKey(world.uuid)) {
                                lang.getMessage(viewer, "gui.visit.world_item.fav_remove")
                        } else {
                                lang.getMessage(viewer, "gui.visit.world_item.fav_add")
                        }
                } else ""

                meta.lore(CCSystem.getAPI().getLoreService().render(GuiLoreSpec.Blocks(buildList {
                        if (world.description.isNotBlank()) add(GuiLoreBlock(listOf(GuiLoreLine.UserText(world.description))))
                        add(GuiLoreBlock(buildList {
                                add(GuiLoreLine.Data(lang.getMessage(viewer, "gui.common.world_item.owner"), ownerName, "§b"))
                                add(GuiLoreLine.Data(lang.getMessage(viewer, "gui.common.world_item.favorite"), world.favorite, "§c"))
                                add(GuiLoreLine.Data(
                                        lang.getMessage(viewer, "gui.common.world_item.recent_visitors"),
                                        lang.getMessage(viewer, "gui.common.world_item.recent_visitors_value", mapOf("count" to world.recentVisitors.sum())),
                                        "§a"
                                ))
                                if (tagNames != null) add(GuiLoreLine.Data(lang.getMessage(viewer, "gui.common.world_item.tags"), tagNames, "§e"))
                        }))
                        add(GuiLoreBlock(buildList {
                                add(GuiLoreLine.Action(lang.getMessage(viewer, "gui.settings.click.left"), warpAction))
                                if (favoriteAction.isNotBlank()) {
                                        add(GuiLoreLine.Action(lang.getMessage(viewer, "gui.settings.click.right"), favoriteAction))
                                }
                        }))
                })))

                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_WORLD_ITEM)
                ItemTag.setWorldUuid(item, world.uuid)
                return item
        }

        private fun createBackButton(player: Player, returnToWorld: WorldData): ItemStack {
                val lang = plugin.languageManager
                val item = GuiItemFactory.item(
                        Material.REDSTONE,
                        lang.getComponent(player, "gui.common.return"),
                        GuiLoreSpec.None,
                        ItemTag.TYPE_GUI_RETURN
                )
                ItemTag.setWorldUuid(item, returnToWorld.uuid)
                return item
        }

        class VisitGuiHolder : org.bukkit.inventory.InventoryHolder {
                lateinit var inv: org.bukkit.inventory.Inventory
                override fun getInventory(): org.bukkit.inventory.Inventory = inv
        }
}
