package me.awabi2048.myworldmanager.gui

import java.util.UUID
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.PortalData
import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.model.WorldTag
import me.awabi2048.myworldmanager.session.SettingsAction
import me.awabi2048.myworldmanager.util.GuiHelper.scheduleGuiTransitionReset
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.Component
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class WorldSettingsGui(private val plugin: MyWorldManager) {

	fun open(player: Player, worldData: WorldData, showBackButton: Boolean? = null, isPlayerWorldFlow: Boolean? = null, parentShowBackButton: Boolean? = null) {
		val lang = plugin.languageManager
		val titleKey = "gui.settings.title"
		if (!lang.hasKey(player, titleKey)) {
			player.sendMessage(
				"§c[MyWorldManager] Error: Missing translation key: $titleKey"
			)
		}

		// セッションの更新
		if (showBackButton != null || isPlayerWorldFlow != null || parentShowBackButton != null) {
			plugin.settingsSessionManager.updateSessionAction(
				player,
				worldData.uuid,
				SettingsAction.VIEW_SETTINGS,
				isGui = true,
				isPlayerWorldFlow = isPlayerWorldFlow,
				parentShowBackButton = parentShowBackButton
			)
			if (showBackButton != null) {
			    plugin.settingsSessionManager.getSession(player)?.showBackButton =
				showBackButton
			}
		} else {
			plugin.settingsSessionManager.updateSessionAction(
				player,
				worldData.uuid,
				SettingsAction.VIEW_SETTINGS,
				isGui = true
			)
		}

		val currentShowBack =
			plugin.settingsSessionManager.getSession(player)?.showBackButton ?: false

		me.awabi2048.myworldmanager.util.GuiHelper.scheduleGuiTransitionReset(
			plugin,
			player
		)

		val title =
			lang.getComponent(
				player,
				"gui.settings.title",
				mapOf("world" to worldData.name)
			)
		me.awabi2048.myworldmanager.util.GuiHelper.playMenuSoundIfTitleChanged(
			plugin,
			player,
			"world_settings",
			title,
			WorldSettingsGuiHolder::class.java
		)

		val holder = WorldSettingsGuiHolder()
		val inventory = Bukkit.createInventory(holder, 54, title)
		holder.inv = inventory

		// 背景 (黒の板ガラス)
		val blackPane = createDecorationItem(Material.BLACK_STAINED_GLASS_PANE)
		for (i in 0..8) inventory.setItem(i, blackPane)
		for (i in 45..53) inventory.setItem(i, blackPane)

		// 戻るボタン
		if (currentShowBack) {
			inventory.setItem(
				45,
				me.awabi2048.myworldmanager.util.GuiHelper.createReturnItem(
					plugin,
					player,
					"world_settings"
				)
			)
		}

		// 権限判定
		val currentSession = plugin.settingsSessionManager.getSession(player)
		val isOwner = worldData.owner == player.uniqueId || currentSession?.isAdminFlow == true
<truncated snippet for compactness since content is large, assuming plugin pushes the whole file>
