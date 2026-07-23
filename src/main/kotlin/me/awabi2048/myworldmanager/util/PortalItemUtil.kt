package me.awabi2048.myworldmanager.util

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import me.awabi2048.myworldmanager.MyWorldManager
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

object PortalItemUtil {
    private val WORLD_UUID_KEY = NamespacedKey("myworldmanager", "portal_world_uuid")
    private val TARGET_WORLD_NAME_KEY = NamespacedKey("myworldmanager", "portal_target_world_name")

    fun createBasePortalItem(lang: me.awabi2048.myworldmanager.util.LanguageManager, player: org.bukkit.entity.Player?): ItemStack {
        val item = ItemStack(Material.END_PORTAL_FRAME)
        val meta = item.itemMeta ?: return item
        meta.displayName(lang.getComponent(player, "gui.portal_item.name"))
        meta.lore(portalLore(lang, player, null))
        try { meta.setMaxStackSize(1) } catch (e: Exception) {}

        item.itemMeta = meta

        ItemTag.tagItem(item, ItemTag.TYPE_PORTAL)
        return item
    }

    fun bindWorld(item: ItemStack, worldUuid: UUID, worldName: String, lang: me.awabi2048.myworldmanager.util.LanguageManager, player: org.bukkit.entity.Player?) {
        val meta = item.itemMeta ?: return
        meta.persistentDataContainer.set(WORLD_UUID_KEY, PersistentDataType.STRING, worldUuid.toString())
        meta.persistentDataContainer.remove(TARGET_WORLD_NAME_KEY)
        meta.setEnchantmentGlintOverride(true)

        meta.lore(portalLore(lang, player, worldName))

        item.itemMeta = meta
    }

    fun bindExternalWorld(item: ItemStack, worldName: String, displayName: String, lang: me.awabi2048.myworldmanager.util.LanguageManager, player: org.bukkit.entity.Player?) {
        val meta = item.itemMeta ?: return
        meta.persistentDataContainer.set(TARGET_WORLD_NAME_KEY, PersistentDataType.STRING, worldName)
        meta.persistentDataContainer.remove(WORLD_UUID_KEY)
        meta.setEnchantmentGlintOverride(true)

        meta.lore(portalLore(lang, player, displayName))

        item.itemMeta = meta
    }

    fun unbindWorld(item: ItemStack, lang: me.awabi2048.myworldmanager.util.LanguageManager, player: org.bukkit.entity.Player?) {
        val meta = item.itemMeta ?: return
        meta.persistentDataContainer.remove(WORLD_UUID_KEY)
        meta.persistentDataContainer.remove(TARGET_WORLD_NAME_KEY)
        meta.setEnchantmentGlintOverride(false)

        val baseItem = createBasePortalItem(lang, player)
        meta.displayName(baseItem.itemMeta.displayName())
        meta.lore(baseItem.itemMeta.lore())

        item.itemMeta = meta
    }

    fun getBoundWorldUuid(item: ItemStack): UUID? {
        val meta = item.itemMeta ?: return null
        val uuidStr = meta.persistentDataContainer.get(WORLD_UUID_KEY, PersistentDataType.STRING) ?: return null
        return try { UUID.fromString(uuidStr) } catch (e: Exception) { null }
    }

    fun getBoundTargetWorldName(item: ItemStack): String? {
        val meta = item.itemMeta ?: return null
        return meta.persistentDataContainer.get(TARGET_WORLD_NAME_KEY, PersistentDataType.STRING)
    }

    private fun portalLore(lang: LanguageManager, player: org.bukkit.entity.Player?, destination: String?) =
        CCSystem.getAPI().getLoreService().render(GuiLoreSpec.Blocks(buildList {
            if (destination != null) {
                add(GuiLoreBlock(listOf(GuiLoreLine.Data(
                    lang.getMessage(player, "gui.portal_item.destination"),
                    destination,
                    "§f§n"
                ))))
            }
            add(GuiLoreBlock(buildList {
                add(GuiLoreLine.Action(
                    lang.getMessage(player, "lore.click.right"),
                    lang.getMessage(player, if (destination == null) "gui.portal_item.action.link" else "gui.portal_item.action.place")
                ))
                add(GuiLoreLine.Action(lang.getMessage(player, "lore.click.shift_right"), lang.getMessage(player, "gui.portal_item.action.unlink")))
            }))
        }))
}
