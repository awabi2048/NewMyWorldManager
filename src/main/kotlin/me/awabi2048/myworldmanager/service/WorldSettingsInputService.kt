package me.awabi2048.myworldmanager.service

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuDialogButton
import com.awabi2048.ccsystem.api.gui.MenuDialogHandler
import com.awabi2048.ccsystem.api.gui.MenuDialogInput
import com.awabi2048.ccsystem.api.gui.MenuDialogRequest
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.gui.AnnouncementDialogManager
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.session.SettingsAction
import me.awabi2048.myworldmanager.util.WorldNameValidation
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player

/** ワールド設定のDialog/Form入力を所有します。 */
internal class WorldSettingsInputService(private val plugin: MyWorldManager) {
    fun editInfo(player: Player, worldData: WorldData): MenuActionResult {
        if (openBedrockInfo(player, worldData)) return MenuActionResult.Success(MenuUpdate.None)
        if (plugin.playerPlatformResolver.isBedrock(player)) {
            plugin.floodgateFormBridge.notifyFallbackCancelled(player)
            return MenuActionResult.Success(MenuUpdate.Refresh)
        }
        plugin.settingsSessionManager.updateSessionAction(player, worldData.uuid, SettingsAction.RENAME_WORLD)
        showInfoDialog(player, worldData)
        return MenuActionResult.Success(MenuUpdate.None)
    }

    fun editAnnouncement(player: Player, worldData: WorldData, rightClick: Boolean): MenuActionResult {
        if (openBedrockAnnouncementActionForm(player, worldData)) return MenuActionResult.Success(MenuUpdate.None)
        if (plugin.playerPlatformResolver.isBedrock(player)) {
            plugin.floodgateFormBridge.notifyFallbackCancelled(player)
            return MenuActionResult.Success(MenuUpdate.Refresh)
        }
        if (rightClick) {
            worldData.announcementMessages.clear()
            plugin.worldConfigRepository.save(worldData)
            player.sendMessage(plugin.languageManager.getMessage("messages.announcement_reset"))
            return MenuActionResult.Success(MenuUpdate.Refresh)
        }
        plugin.settingsSessionManager.updateSessionAction(player, worldData.uuid, SettingsAction.SET_ANNOUNCEMENT)
        AnnouncementDialogManager.showAnnouncementEditDialog(player, worldData)
        return MenuActionResult.Success(MenuUpdate.None)
    }

    private fun openBedrockInfo(player: Player, worldData: WorldData): Boolean {
        if (!plugin.playerPlatformResolver.isBedrock(player) || !plugin.floodgateFormBridge.isAvailable(player)) return false
        val uuid = worldData.uuid
        val lang = plugin.languageManager
        CCSystem.getAPI().getMenuRuntimeService().suspendForExternal(player)
        return plugin.floodgateFormBridge.sendCustomForm(
            player,
            lang.getMessage(player, "gui.bedrock.input.info_form.title"),
            listOf(
                me.awabi2048.myworldmanager.ui.bedrock.FloodgateFormBridge.CustomFormInput(
                    lang.getMessage(player, "gui.bedrock.input.rename.label"),
                    lang.getMessage(player, "gui.bedrock.input.rename.placeholder"), worldData.name,
                ),
                me.awabi2048.myworldmanager.ui.bedrock.FloodgateFormBridge.CustomFormInput(
                    lang.getMessage(player, "gui.bedrock.input.description.label"),
                    lang.getMessage(player, "gui.bedrock.input.description.placeholder"), worldData.description,
                ),
            ),
            onSubmit = { values -> plugin.worldConfigRepository.findByUuid(uuid)?.let { applyInfo(player, it, values.getOrNull(0).orEmpty().trim(), values.getOrNull(1).orEmpty().trim()) } },
            onClosed = { if (player.isOnline && plugin.worldConfigRepository.findByUuid(uuid) != null) CCSystem.getAPI().getMenuRuntimeService().finishExternal(player) },
        )
    }

    private fun openBedrockAnnouncementActionForm(player: Player, worldData: WorldData): Boolean {
        if (!plugin.playerPlatformResolver.isBedrock(player) || !plugin.floodgateFormBridge.isAvailable(player)) return false
        val lang = plugin.languageManager
        val worldUuid = worldData.uuid
        CCSystem.getAPI().getMenuRuntimeService().suspendForExternal(player)
        return plugin.floodgateFormBridge.sendSimpleForm(
            player = player,
            title = lang.getMessage(player, "gui.bedrock.input.announcement_menu.title"),
            content = lang.getMessage(player, "gui.bedrock.input.announcement_menu.content"),
            buttons = listOf(lang.getMessage(player, "gui.bedrock.input.announcement_menu.edit"), lang.getMessage(player, "gui.bedrock.input.announcement_menu.reset")),
            onSelect = { index ->
                val latest = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return@sendSimpleForm
                if (index == 1) {
                    latest.announcementMessages.clear(); plugin.worldConfigRepository.save(latest)
                    player.sendMessage(lang.getMessage(player, "messages.announcement_reset"))
                    CCSystem.getAPI().getMenuRuntimeService().finishExternal(player)
                    return@sendSimpleForm
                }
                if (!openBedrockAnnouncementEditForm(player, latest)) {
                    player.sendMessage(lang.getMessage(player, "messages.operation_cancelled"))
                    CCSystem.getAPI().getMenuRuntimeService().finishExternal(player)
                }
            },
            onClosed = { if (player.isOnline && plugin.worldConfigRepository.findByUuid(worldUuid) != null) CCSystem.getAPI().getMenuRuntimeService().finishExternal(player) },
        )
    }

    private fun openBedrockAnnouncementEditForm(player: Player, worldData: WorldData): Boolean {
        if (!plugin.playerPlatformResolver.isBedrock(player) || !plugin.floodgateFormBridge.isAvailable(player)) return false
        val lang = plugin.languageManager
        val maxLines = plugin.config.getInt("announcement.max_lines", 5)
        val maxLength = plugin.config.getInt("announcement.max_line_length", 100)
        val worldUuid = worldData.uuid
        val inputs = (0 until maxLines).map { index ->
            val current = worldData.announcementMessages.getOrNull(index).orEmpty()
            me.awabi2048.myworldmanager.ui.bedrock.FloodgateFormBridge.CustomFormInput(
                label = lang.getMessage(player, "gui.bedrock.input.announcement_edit.label", mapOf("line" to index + 1)),
                placeholder = lang.getMessage(player, "gui.bedrock.input.announcement_edit.placeholder", mapOf("max" to maxLength)),
                defaultValue = current.removePrefix("ﾂｧf").replace("ﾂｧ", "&"),
            )
        }
        CCSystem.getAPI().getMenuRuntimeService().suspendForExternal(player)
        return plugin.floodgateFormBridge.sendCustomForm(
            player = player,
            title = lang.getMessage(player, "gui.bedrock.input.announcement_edit.title", mapOf("max_lines" to maxLines, "max_length" to maxLength)),
            inputs = inputs,
            onSubmit = { values -> plugin.worldConfigRepository.findByUuid(worldUuid)?.let { applyAnnouncementUpdateFromForm(player, it, values) } },
            onClosed = { if (player.isOnline && plugin.worldConfigRepository.findByUuid(worldUuid) != null) CCSystem.getAPI().getMenuRuntimeService().finishExternal(player) },
        )
    }

    private fun applyAnnouncementUpdateFromForm(player: Player, worldData: WorldData, rawInputs: List<String>) {
        val lang = plugin.languageManager
        val maxLines = plugin.config.getInt("announcement.max_lines", 5)
        val maxLength = plugin.config.getInt("announcement.max_line_length", 100)
        val blockedStrings = plugin.config.getStringList("announcement.blocked_strings")
        val trimmed = rawInputs.map { it.trim() }.filter { it.isNotEmpty() }
        fun invalid(key: String, placeholders: Map<String, Any>) {
            player.sendMessage(lang.getMessage(player, key, placeholders)); plugin.worldSettingsGui.open(player, worldData)
        }
        if (trimmed.size > maxLines) { invalid("messages.announcement_invalid_length", mapOf("max_lines" to maxLines, "max_length" to maxLength)); return }
        for (line in trimmed) {
            if (line.length > maxLength) { invalid("messages.announcement_invalid_length", mapOf("max_lines" to maxLines, "max_length" to maxLength)); return }
            val blocked = blockedStrings.firstOrNull { line.contains(it, ignoreCase = true) }
            if (blocked != null) { invalid("messages.announcement_blocked_string", mapOf("string" to blocked)); return }
        }
        worldData.announcementMessages.clear()
        trimmed.forEach { worldData.announcementMessages.add("ﾂｧf${it.replace("&", "ﾂｧ")}") }
        plugin.worldConfigRepository.save(worldData)
        player.sendMessage(lang.getMessage(player, "messages.announcement_set"))
        plugin.worldSettingsGui.open(player, worldData)
    }

    private fun showInfoDialog(player: Player, worldData: WorldData) {
        val lang = plugin.languageManager
        CCSystem.getAPI().getMenuDialogService().show(player, MenuDialogRequest(
            owner = "myworldmanager", id = "settings-world-info",
            title = Component.text(lang.getMessage(player, "gui.bedrock.input.info_form.title"), NamedTextColor.YELLOW),
            body = listOf(Component.text(lang.getMessage(player, "gui.settings.info.dialog.body"))),
            inputs = listOf(
                MenuDialogInput.Text("world_name", Component.text(lang.getMessage(player, "gui.bedrock.input.rename.label")), worldData.name.take(16), maxLength = 16),
                MenuDialogInput.Text("world_desc", Component.text(lang.getMessage(player, "gui.bedrock.input.description.label")), worldData.description.take(100), maxLength = 100),
            ),
            confirm = MenuDialogButton(Component.text(lang.getMessage(player, "gui.common.confirm"), NamedTextColor.GREEN), MenuDialogHandler { target, response ->
                applyInfo(target, worldData, response.textValue("world_name").trim(), response.textValue("world_desc").trim()); MenuActionResult.Success(MenuUpdate.None)
            }),
            cancel = MenuDialogButton(Component.text(lang.getMessage(player, "gui.common.cancel"), NamedTextColor.GRAY), MenuDialogHandler { _, _ -> MenuActionResult.Success(MenuUpdate.Resume) }),
        ))
    }

    private fun applyInfo(player: Player, worldData: WorldData, name: String, description: String) {
        var updated = false
        if (name.isNotBlank()) when (val result = plugin.worldValidator.validateName(name)) {
            is WorldNameValidation.Failure -> player.sendMessage(plugin.languageManager.getComponent(player, result.messageKey, result.placeholders))
            else -> if (plugin.worldConfigRepository.findByOwnerAndDisplayName(worldData.owner, name, worldData.uuid) != null) player.sendMessage(plugin.languageManager.getMessage(player, "messages.world_name_duplicate")) else if (worldData.name != name) { worldData.name = name; updated = true; player.sendMessage(plugin.languageManager.getMessage(player, "messages.world_name_change")) }
        }
        if (worldData.description != description) { worldData.description = description; updated = true; player.sendMessage(plugin.languageManager.getMessage(player, "messages.world_desc_change")) }
        if (updated) plugin.worldConfigRepository.save(worldData)
        CCSystem.getAPI().getMenuRuntimeService().finishExternal(player)
    }
}
