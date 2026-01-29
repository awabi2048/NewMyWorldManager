package me.awabi2048.myworldmanager.gui

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.session.SettingsAction
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

class EnvironmentGui(private val plugin: MyWorldManager) {

    fun open(player: Player, worldData: WorldData) {
        val lang = plugin.languageManager
        val title = lang.getMessage(player, "gui.environment.title")
        me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(plugin, player, "environment", Component.text(title))
        val currentTitle = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(player.openInventory.title())
        
        plugin.settingsSessionManager.updateSessionAction(player, worldData.uuid, SettingsAction.VIEW_ENVIRONMENT_SETTINGS, isGui = true)
        scheduleGuiTransitionReset(player)
        
        val inventory = if (player.openInventory.topInventory.size == 45 && currentTitle == title) {
            player.openInventory.topInventory
        } else {
            Bukkit.createInventory(null, 45, Component.text(title))
        }

        // 背景アイテム作成
        val blackPane = ItemStack(Material.BLACK_STAINED_GLASS_PANE)
        val blackMeta = blackPane.itemMeta
        blackMeta?.displayName(Component.empty())
        blackMeta?.isHideTooltip = true
        blackPane.itemMeta = blackMeta
        ItemTag.tagItem(blackPane, ItemTag.TYPE_GUI_DECORATION)

        val grayPane = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        val grayMeta = grayPane.itemMeta
        grayMeta?.displayName(Component.empty())
        grayMeta?.isHideTooltip = true
        grayPane.itemMeta = grayMeta
        ItemTag.tagItem(grayPane, ItemTag.TYPE_GUI_DECORATION)

        // バックグラウンド埋め
        // ヘッダー・フッター
        for (i in 0..8) inventory.setItem(i, blackPane)
        for (i in 36..44) inventory.setItem(i, blackPane)
        
        // コンテンツエリア
        for (i in 9..35) inventory.setItem(i, grayPane)

        // スロット20: 重力
        inventory.setItem(20, createGravityItem(player, worldData))
        
        // スロット22: 天候
        inventory.setItem(22, createWeatherItem(player, worldData))
        
        // スロット24: バイオーム
        inventory.setItem(24, createBiomeItem(player, worldData))

        // 戻るボタン (スロット40)
        val backItem = ItemStack(Material.ARROW)
        val backMeta = backItem.itemMeta
        backMeta?.displayName(Component.text(lang.getMessage(player, "gui.common.back")))
        backItem.itemMeta = backMeta
        ItemTag.tagItem(backItem, ItemTag.TYPE_GUI_CANCEL)
        inventory.setItem(40, backItem)

        player.openInventory(inventory)
    }

    private fun createGravityItem(player: Player, worldData: WorldData): ItemStack {
        val lang = plugin.languageManager
        val item = ItemStack(Material.FEATHER)
        val meta = item.itemMeta ?: return item
        
        val currentMultiplier = worldData.gravityMultiplier
        val gravityKey = when (currentMultiplier) {
            0.0 -> "zero"
            0.17 -> "moon"
            0.38 -> "mars"
            1.0 -> "earth"
            else -> "earth"
        }
        val currentName = lang.getMessage(player, "gui.environment.gravity.options.$gravityKey")
        val cost = plugin.config.getInt("environment.gravity.cost", 100)
        
        meta.displayName(Component.text(lang.getMessage(player, "gui.environment.gravity.display")))
        meta.lore(listOf(
            Component.text(lang.getMessage(player, "gui.environment.gravity.current", currentName)),
            Component.text(lang.getMessage(player, "gui.environment.gravity.cost", cost)),
            Component.empty(),
            Component.text(lang.getMessage(player, "gui.environment.gravity.click_hint"))
        ))
        
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
        val cost = plugin.config.getInt("environment.weather.cost", 50)
        
        meta.displayName(Component.text(lang.getMessage(player, "gui.environment.weather.display")))
        meta.lore(listOf(
            Component.text(lang.getMessage(player, "gui.environment.weather.current", currentWeather)),
            Component.text(lang.getMessage(player, "gui.environment.weather.cost", cost)),
            Component.text(lang.getMessage(player, "gui.environment.weather.desc")),
            Component.empty(),
            Component.text(lang.getMessage(player, "gui.environment.weather.click_cycle")),
            Component.text(lang.getMessage(player, "gui.environment.weather.click_confirm"))
        ))
        
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_ENV_WEATHER)
        return item
    }

    private fun createBiomeItem(player: Player, worldData: WorldData): ItemStack {
        val lang = plugin.languageManager
        val item = ItemStack(Material.GRASS_BLOCK)
        val meta = item.itemMeta ?: return item
        
        val currentBiome = worldData.fixedBiome ?: "DEFAULT"
        val cost = plugin.config.getInt("environment.biome.cost", 500)
        
        meta.displayName(Component.text(lang.getMessage(player, "gui.environment.biome.display")))
        meta.lore(listOf(
            Component.text(lang.getMessage(player, "gui.environment.biome.current", currentBiome)),
            Component.text(lang.getMessage(player, "gui.environment.biome.cost", cost)),
            Component.text(lang.getMessage(player, "gui.environment.biome.desc")),
            Component.empty(),
            Component.text(lang.getMessage(player, "gui.environment.biome.click_hint"))
        ))
        
        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_GUI_ENV_BIOME)
        return item
    }

    private fun scheduleGuiTransitionReset(player: Player) {
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val session = plugin.settingsSessionManager.getSession(player)
            if (session != null && session.isGuiTransition) {
                session.isGuiTransition = false
            }
        }, 5L)
    }
}
