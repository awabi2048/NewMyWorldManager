package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.session.SettingsAction
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.GuiItemFactory
import me.awabi2048.myworldmanager.util.GuiLoreActions
import me.awabi2048.myworldmanager.util.ItemTag
import me.awabi2048.myworldmanager.util.WorldRuntimePolicies
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class EnvironmentGui(private val plugin: MyWorldManager) {

    fun open(player: Player, worldData: WorldData) {
        val lang = plugin.languageManager
        val title = lang.getMessage(player, "gui.environment.title")
        val titleComponent = me.awabi2048.myworldmanager.util.GuiHelper.inventoryTitle(title)
        me.awabi2048.myworldmanager.util.GuiHelper.playMenuOpen(player, "environment")
        val currentTitle =
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(player.openInventory.title())

        plugin.settingsSessionManager.updateSessionAction(
                player,
                worldData.uuid,
                SettingsAction.VIEW_ENVIRONMENT_SETTINGS,
                isGui = true
        )
        me.awabi2048.myworldmanager.util.GuiHelper.scheduleGuiTransitionReset(plugin, player)

        val inventory =
                if (player.openInventory.topInventory.size == GuiHelper.threeChoiceLayout().size && currentTitle == title) {
                    player.openInventory.topInventory
                } else {
                    val holder = WorldSettingsGuiHolder()
                    val inventory = Bukkit.createInventory(holder, GuiHelper.threeChoiceLayout().size, titleComponent)
                    holder.inv = inventory
                    inventory
                }

        GuiItemFactory.applyStandardFrame(inventory)

        // Keep the three environment choices aligned with the shared layout.
        GuiHelper.setThreeChoiceItems(
                inventory,
                createGravityItem(player, worldData),
                createWeatherItem(player, worldData),
                createBiomeItem(player, worldData)
        )

        // 戻るボタン (スロット40)
        val backItem = ItemStack(Material.REDSTONE)
        val backMeta = backItem.itemMeta
        backMeta?.displayName(lang.getComponent(player, "gui.common.back"))
        backItem.itemMeta = backMeta
        ItemTag.tagItem(backItem, ItemTag.TYPE_GUI_CANCEL)
        GuiHelper.setThreeChoiceBack(inventory, backItem)

        player.openInventory(inventory)
    }

    private fun createGravityItem(player: Player, worldData: WorldData): ItemStack {
        val lang = plugin.languageManager
        val item = ItemStack(Material.FEATHER)
        val meta = item.itemMeta ?: return item

        val currentGravity = worldData.gravityValue ?: 0.08
        val gravityKey =
            when (currentGravity) {
                0.01 -> "moon"
                0.02 -> "mars"
                0.08 -> "earth"
                else -> "earth"
            }
        val currentName = lang.getMessage(player, "gui.environment.gravity.options.$gravityKey")
        val cost = WorldRuntimePolicies.environmentCost(plugin.config, "gravity")

        meta.displayName(lang.getComponent(player, "gui.environment.gravity.display"))
        meta.lore(
                GuiItemFactory.componentMenuLore(listOf(
                        Component.text(
                                lang.getMessage(
                                        player,
                                        "gui.environment.gravity.current",
                                        mapOf("multiplier" to currentName)
                                )
                        ),
                        Component.text(
                                lang.getMessage(
                                        player,
                                        "gui.environment.gravity.cost",
                                        mapOf("cost" to cost)
                                )
                        ),
                        Component.empty(),
                        Component.text(
                                lang.getMessage(player, "gui.environment.gravity.click_hint")
                        )
                ))
        )

        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_ENV_GRAVITY)
        return item
    }

    private fun createWeatherItem(player: Player, worldData: WorldData): ItemStack {
        val lang = plugin.languageManager
        val item = ItemStack(Material.WHITE_WOOL)
        val meta = item.itemMeta ?: return item

        val session = plugin.settingsSessionManager.getSession(player)
        val currentWeather = session?.tempWeather ?: worldData.fixedWeather ?: "DEFAULT"
        val cost = WorldRuntimePolicies.environmentCost(plugin.config, "weather")

        meta.displayName(lang.getComponent(player, "gui.environment.weather.display"))
        meta.lore(
                GuiItemFactory.componentMenuLore(listOf(
                        Component.text(
                                lang.getMessage(
                                        player,
                                        "gui.environment.weather.current",
                                        mapOf("weather" to currentWeather)
                                )
                        ),
                        Component.text(
                                lang.getMessage(
                                        player,
                                        "gui.environment.weather.cost",
                                        mapOf("cost" to cost)
                                )
                        ),
                        Component.text(lang.getMessage(player, "gui.environment.weather.desc"))
                ) + CCSystem.getAPI().getLoreService().render(
                        GuiLoreSpec.Rich(
                                listOf(
                                        GuiLoreLine.Spacer,
                                        GuiLoreLine.Action(
                                                lang.getMessage(player, "lore.click.left"),
                                                lang.getMessage(player, "gui.environment.weather.action.cycle")
                                        ),
                                        GuiLoreLine.Action(
                                                lang.getMessage(player, "lore.click.right"),
                                                lang.getMessage(player, "gui.environment.weather.action.confirm")
                                        )
                                ),
                                GuiLoreFrame.NONE
                        )
                ))
        )

        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_ENV_WEATHER)
        return item
    }

    private fun createBiomeItem(player: Player, worldData: WorldData): ItemStack {
        val lang = plugin.languageManager
        val item = ItemStack(Material.GRASS_BLOCK)
        val meta = item.itemMeta ?: return item

        val currentBiome = worldData.fixedBiome ?: "DEFAULT"
        val cost = WorldRuntimePolicies.environmentCost(plugin.config, "biome")

        meta.displayName(lang.getComponent(player, "gui.environment.biome.display"))
        meta.lore(
                GuiItemFactory.componentMenuLore(listOf(
                        Component.text(
                                lang.getMessage(
                                        player,
                                        "gui.environment.biome.current",
                                        mapOf("biome" to currentBiome)
                                )
                        ),
                        Component.text(
                                lang.getMessage(
                                        player,
                                        "gui.environment.biome.cost",
                                        mapOf("cost" to cost)
                                )
                        ),
                        Component.text(lang.getMessage(player, "gui.environment.biome.desc")),
                        Component.empty(),
                        Component.text(lang.getMessage(player, "gui.environment.biome.click_hint"))
                ))
        )

        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_ENV_BIOME)
        return item
    }
}
