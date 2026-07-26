@file:Suppress("REDUNDANT_CALL_OF_CONVERSION_METHOD")

package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuDialogButton
import com.awabi2048.ccsystem.api.gui.MenuDialogHandler
import com.awabi2048.ccsystem.api.gui.MenuDialogInput
import com.awabi2048.ccsystem.api.gui.MenuDialogRequest
import com.awabi2048.ccsystem.api.gui.MenuDialogResponse
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.LikeSignData
import me.awabi2048.myworldmanager.model.LikeSignDisplayType
import me.awabi2048.myworldmanager.service.LikeSignManager
import me.awabi2048.myworldmanager.util.GuiItemFactory
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class LikeSignDialogManager {

    data class PlacementSession(
        val playerUuid: UUID,
        val worldUuid: UUID,
        val blockX: Int,
        val blockY: Int,
        val blockZ: Int,
        val blockFace: String,
        val hand: EquipmentSlot,
        var title: String = "",
        var description: String = "",
        var displayType: LikeSignDisplayType = LikeSignDisplayType.HOLOGRAM
    )

    data class EditSession(
        val playerUuid: UUID,
        val signUuid: UUID,
        val worldUuid: UUID
    )

    data class UnlikeSession(
        val playerUuid: UUID,
        val signUuid: UUID,
        val worldUuid: UUID
    )

    companion object {
        private val placementSessions = ConcurrentHashMap<UUID, PlacementSession>()
        private val editSessions = ConcurrentHashMap<UUID, EditSession>()
        private val unlikeSessions = ConcurrentHashMap<UUID, UnlikeSession>()

        fun startPlacementSession(
            player: Player,
            plugin: MyWorldManager,
            block: Block,
            blockFace: BlockFace,
            hand: EquipmentSlot
        ) {
            val worldName = player.world.name
            if (!worldName.startsWith("my_world.")) {
                player.sendMessage(plugin.languageManager.getMessage(player, "error.like_sign.my_world_only"))
                return
            }

            val worldUuidStr = worldName.removePrefix("my_world.")
            val worldUuid = try { UUID.fromString(worldUuidStr) } catch (e: Exception) { return }
            val worldData = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return

            if (!plugin.likeSignManager.isWorldMember(worldData, player.uniqueId)) {
                player.sendMessage(plugin.languageManager.getMessage(player, "error.like_sign.no_permission"))
                return
            }

            if (!plugin.likeSignManager.canPlaceSign(worldData)) {
                player.sendMessage(plugin.languageManager.getMessage(player, "error.like_sign.limit_reached"))
                return
            }

            val session = PlacementSession(
                playerUuid = player.uniqueId,
                worldUuid = worldUuid,
                blockX = block.x,
                blockY = block.y,
                blockZ = block.z,
                blockFace = blockFace.name,
                hand = hand
            )
            placementSessions[player.uniqueId] = session

            showPlacementDialog(player, plugin, session)
        }

        fun startEditSession(
            player: Player,
            plugin: MyWorldManager,
            signData: LikeSignData,
            worldUuid: UUID
        ) {
            val session = EditSession(
                playerUuid = player.uniqueId,
                signUuid = signData.uuid,
                worldUuid = worldUuid
            )
            editSessions[player.uniqueId] = session

            showEditDialog(player, plugin, signData)
        }

        private fun showPlacementDialog(player: Player, plugin: MyWorldManager, session: PlacementSession) {
            val lang = plugin.languageManager
            CCSystem.getAPI().getMenuDialogService().show(
                player,
                MenuDialogRequest(
                    owner = "myworldmanager",
                    id = "like-sign-placement",
                    title = Component.text(
                        lang.getMessage(player, "gui.like_sign.placement.title"),
                        NamedTextColor.GOLD,
                    ),
                    body = listOf(
                        Component.text(lang.getMessage(player, "gui.like_sign.placement.description")),
                    ),
                    inputs = listOf(
                        MenuDialogInput.Text(
                            "title",
                            Component.text(lang.getMessage(player, "gui.like_sign.input.title")),
                            session.title,
                            maxLength = LikeSignManager.MAX_TITLE_LENGTH,
                        ),
                        MenuDialogInput.Text(
                            "description",
                            Component.text(lang.getMessage(player, "gui.like_sign.input.description")),
                            session.description,
                            maxLength = LikeSignManager.MAX_DESCRIPTION_LENGTH,
                        ),
                        MenuDialogInput.SingleOption(
                            "display_type",
                            Component.text(lang.getMessage(player, "gui.like_sign.input.display_type")),
                            listOf(
                                MenuDialogInput.SingleOption.Option(
                                    "sign",
                                    Component.text(
                                        lang.getMessage(player, "gui.like_sign.display_type.sign"),
                                        NamedTextColor.YELLOW,
                                    ),
                                    session.displayType == LikeSignDisplayType.SIGN,
                                ),
                                MenuDialogInput.SingleOption.Option(
                                    "hologram",
                                    Component.text(
                                        lang.getMessage(player, "gui.like_sign.display_type.hologram"),
                                        NamedTextColor.AQUA,
                                    ),
                                    session.displayType == LikeSignDisplayType.HOLOGRAM,
                                ),
                            ),
                        ),
                    ),
                    confirm = MenuDialogButton(
                        Component.text(
                            lang.getMessage(player, "gui.like_sign.button.place"),
                            NamedTextColor.GREEN,
                        ),
                        MenuDialogHandler { target, response ->
                            placeSign(target, plugin, response)
                        },
                    ),
                    cancel = MenuDialogButton(
                        Component.text(
                            lang.getMessage(player, "gui.like_sign.button.cancel"),
                            NamedTextColor.RED,
                        ),
                        MenuDialogHandler { target, _ ->
                            placementSessions.remove(target.uniqueId)
                            MenuActionResult.Success(MenuUpdate.Close)
                        },
                    ),
                ),
            )
        }

        private fun showEditDialog(player: Player, plugin: MyWorldManager, signData: LikeSignData) {
            val lang = plugin.languageManager

            val likesLabel = lang.getMessage(player, "gui.like_sign.edit.current_likes")
            val typeLabel = lang.getMessage(player, "gui.like_sign.input.display_type")

            val bodyLines = CCSystem.getAPI().getLoreService().render(
                GuiLoreSpec.Rich(
                    listOf(
                        GuiLoreLine.Data(likesLabel, "§c❤ ${signData.likeCount()}", ""),
                        GuiLoreLine.Data(typeLabel, signData.displayType.name, "§f")
                    ),
                    GuiLoreFrame.BOTH
                )
            )

            CCSystem.getAPI().getMenuDialogService().show(
                player,
                MenuDialogRequest(
                    owner = "myworldmanager",
                    id = "like-sign-edit",
                    title = Component.text(
                        lang.getMessage(player, "gui.like_sign.edit.title"),
                        NamedTextColor.GOLD,
                    ),
                    body = bodyLines,
                    inputs = listOf(
                        MenuDialogInput.Text(
                            "title",
                            Component.text(lang.getMessage(player, "gui.like_sign.input.title")),
                            signData.title,
                            maxLength = LikeSignManager.MAX_TITLE_LENGTH,
                        ),
                        MenuDialogInput.Text(
                            "description",
                            Component.text(lang.getMessage(player, "gui.like_sign.input.description")),
                            signData.description,
                            maxLength = LikeSignManager.MAX_DESCRIPTION_LENGTH,
                        ),
                    ),
                    confirm = MenuDialogButton(
                        Component.text(
                            lang.getMessage(player, "gui.like_sign.button.save"),
                            NamedTextColor.GREEN,
                        ),
                        MenuDialogHandler { target, response ->
                            saveSign(target, plugin, response)
                        },
                    ),
                    cancel = MenuDialogButton(
                        Component.text(
                            lang.getMessage(player, "gui.like_sign.button.delete"),
                            NamedTextColor.RED,
                        ),
                        MenuDialogHandler { target, _ ->
                            deleteSign(target, plugin)
                        },
                    ),
                ),
            )
        }

        private fun placeSign(
            player: Player,
            plugin: MyWorldManager,
            response: MenuDialogResponse,
        ): MenuActionResult {
            val session = placementSessions.remove(player.uniqueId)
                ?: return MenuActionResult.Rejected()
            val title = response.textValue("title")
            if (title.isBlank()) {
                player.sendMessage(plugin.languageManager.getMessage(player, "error.like_sign.title_required"))
                placementSessions[player.uniqueId] = session
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    showPlacementDialog(player, plugin, session)
                })
                return MenuActionResult.Rejected()
            }

            val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid)
                ?: return MenuActionResult.Rejected()
            val block = player.world.getBlockAt(session.blockX, session.blockY, session.blockZ)
            val blockFace = runCatching { BlockFace.valueOf(session.blockFace) }.getOrDefault(BlockFace.NORTH)
            val displayType = when (response.selectedValue("display_type")) {
                "sign" -> LikeSignDisplayType.SIGN
                else -> LikeSignDisplayType.HOLOGRAM
            }
            val signData = plugin.likeSignManager.createSign(
                worldData,
                player,
                block,
                blockFace,
                title,
                response.textValue("description"),
                displayType,
            )
            if (signData != null) {
                val item = if (session.hand == EquipmentSlot.HAND) {
                    player.inventory.itemInMainHand
                } else {
                    player.inventory.itemInOffHand
                }
                item.amount -= 1
                player.sendMessage(plugin.languageManager.getMessage(player, "messages.like_sign.placed"))
                player.playSound(player.location, org.bukkit.Sound.BLOCK_WOOD_PLACE, 1.0f, 1.0f)
            }
            return MenuActionResult.Success(MenuUpdate.Close)
        }

        private fun saveSign(
            player: Player,
            plugin: MyWorldManager,
            response: MenuDialogResponse,
        ): MenuActionResult {
            val session = editSessions.remove(player.uniqueId)
                ?: return MenuActionResult.Rejected()
            val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid)
                ?: return MenuActionResult.Rejected()
            val signData = plugin.likeSignManager.findSignByUuid(worldData, session.signUuid)
                ?: return MenuActionResult.Rejected()
            signData.title = response.textValue("title").take(LikeSignManager.MAX_TITLE_LENGTH)
            signData.description = response.textValue("description")
                .take(LikeSignManager.MAX_DESCRIPTION_LENGTH)
            plugin.worldConfigRepository.save(worldData)
            plugin.likeSignManager.refreshSignDisplay(signData, worldData)
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.like_sign.saved"))
            player.playSound(player.location, org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f)
            return MenuActionResult.Success(MenuUpdate.Close)
        }

        private fun deleteSign(player: Player, plugin: MyWorldManager): MenuActionResult {
            val session = editSessions.remove(player.uniqueId)
                ?: return MenuActionResult.Rejected()
            val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid)
                ?: return MenuActionResult.Rejected()
            val signData = plugin.likeSignManager.findSignByUuid(worldData, session.signUuid)
                ?: return MenuActionResult.Rejected()
            plugin.likeSignManager.removeSign(signData, worldData, player)
            player.sendMessage(plugin.languageManager.getMessage(player, "messages.like_sign.deleted"))
            player.playSound(player.location, org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f)
            return MenuActionResult.Success(MenuUpdate.Close)
        }

        fun showLikeConfirmDialog(player: Player, plugin: MyWorldManager, signData: LikeSignData, worldUuid: UUID) {
            val lang = plugin.languageManager

            val session = UnlikeSession(
                playerUuid = player.uniqueId,
                signUuid = signData.uuid,
                worldUuid = worldUuid
            )
            unlikeSessions[player.uniqueId] = session

            val title = Component.text(lang.getMessage(player, "gui.like_sign.unlike_confirm.title"), NamedTextColor.RED)
            val center = GuiItemFactory.item(
                org.bukkit.Material.RED_DYE,
                lang.getMessage(player, "gui.like_sign.unlike_confirm.title"),
                GuiLoreSpec.Rich(
                    listOf(GuiLoreLine.Warning(lang.getMessage(player, "gui.like_sign.unlike_confirm.description"))),
                    GuiLoreFrame.BOTH
                ),
                ItemTag.TYPE_GUI_INFO
            )
            val confirmItem = GuiItemFactory.item(
                org.bukkit.Material.LIME_CONCRETE,
                lang.getMessage(player, "gui.common.confirm"),
                GuiLoreSpec.None,
                ItemTag.TYPE_GUI_CONFIRM
            )
            val cancelItem = GuiItemFactory.item(
                org.bukkit.Material.RED_CONCRETE,
                lang.getMessage(player, "gui.common.cancel"),
                GuiLoreSpec.None,
                ItemTag.TYPE_GUI_CANCEL
            )

            plugin.confirmationMenuGui.open(
                player = player,
                menuId = "like_sign",
                title = title,
                centerItem = center,
                confirmItem = confirmItem,
                cancelItem = cancelItem,
                onConfirm = {
                    val session = unlikeSessions.remove(player.uniqueId) ?: return@open
                    val worldData = plugin.worldConfigRepository.findByUuid(session.worldUuid) ?: return@open
                    val sign = plugin.likeSignManager.findSignByUuid(worldData, session.signUuid) ?: return@open
                    if (sign.hasLiked(player.uniqueId)) {
                        sign.removeLike(player.uniqueId)
                        plugin.worldConfigRepository.save(worldData)
                        plugin.likeSignManager.refreshSignDisplay(sign, worldData)
                        player.sendMessage(plugin.languageManager.getMessage(player, "messages.like_sign.unliked"))
                        player.playSound(player.location, org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f)
                    }
                },
                onCancel = {
                    unlikeSessions.remove(player.uniqueId)
                }
            )
        }
    }

}
