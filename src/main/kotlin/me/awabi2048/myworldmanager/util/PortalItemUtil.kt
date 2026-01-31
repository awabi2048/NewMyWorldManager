package me.awabi2048.myworldmanager.util

import me.awabi2048.myworldmanager.MyWorldManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

object PortalItemUtil {
    private val WORLD_UUID_KEY = NamespacedKey("myworldmanager", "portal_world_uuid")
    private val TARGET_WORLD_NAME_KEY = NamespacedKey("myworldmanager", "portal_target_world_name")

    fun createBasePortalItem(lang: me.awabi2048.myworldmanager.util.LanguageManager, player: org.bukkit.entity.Player?): ItemStack {
        val item = ItemStack(Material.STONE_PICKAXE)
        val meta = item.itemMeta ?: return item
        meta.displayName(Component.text(lang.getMessage(player, "gui.portal_item.name")).decoration(TextDecoration.ITALIC, false))
        meta.lore(lang.getComponentList(player, "gui.portal_item.lore_unbound"))
        try { meta.setMaxStackSize(1) } catch (e: Exception) {}

        meta.setItemModel(NamespacedKey("kota_server", "mwm_misc"))
        item.itemMeta = meta
        
        item.setData(io.papermc.paper.datacomponent.DataComponentTypes.TOOL, io.papermc.paper.datacomponent.item.Tool.tool().build())
        item.setData(io.papermc.paper.datacomponent.DataComponentTypes.ATTRIBUTE_MODIFIERS, io.papermc.paper.datacomponent.item.ItemAttributeModifiers.itemAttributes().build())
        item.setData(io.papermc.paper.datacomponent.DataComponentTypes.CUSTOM_MODEL_DATA, io.papermc.paper.datacomponent.item.CustomModelData.customModelData().addString("world_portal").build())

        ItemTag.tagItem(item, ItemTag.TYPE_PORTAL)
        return item
    }

    fun bindWorld(item: ItemStack, worldUuid: UUID, worldName: String, lang: me.awabi2048.myworldmanager.util.LanguageManager, player: org.bukkit.entity.Player?) {
        val meta = item.itemMeta ?: return
        meta.persistentDataContainer.set(WORLD_UUID_KEY, PersistentDataType.STRING, worldUuid.toString())
        meta.persistentDataContainer.remove(TARGET_WORLD_NAME_KEY)
        meta.setEnchantmentGlintOverride(true)
        
        val lore = lang.getComponentList(player, "gui.portal_item.lore_bound", mapOf("destination" to worldName))
        meta.lore(lore)
        
        item.itemMeta = meta
    }

    fun bindExternalWorld(item: ItemStack, worldName: String, displayName: String, lang: me.awabi2048.myworldmanager.util.LanguageManager, player: org.bukkit.entity.Player?) {
        val meta = item.itemMeta ?: return
        meta.persistentDataContainer.set(TARGET_WORLD_NAME_KEY, PersistentDataType.STRING, worldName)
        meta.persistentDataContainer.remove(WORLD_UUID_KEY)
        meta.setEnchantmentGlintOverride(true)
        
        val lore = lang.getComponentList(player, "gui.portal_item.lore_bound", mapOf("destination" to displayName))
        meta.lore(lore)
        
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
}
