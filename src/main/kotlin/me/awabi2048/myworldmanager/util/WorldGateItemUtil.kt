package me.awabi2048.myworldmanager.util

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.CustomModelData
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

object WorldGateItemUtil {
    private val WORLD_UUID_KEY = NamespacedKey("myworldmanager", "world_gate_world_uuid")
    private val TARGET_WORLD_NAME_KEY = NamespacedKey("myworldmanager", "world_gate_target_world_name")

    fun createBaseWorldGateItem(lang: LanguageManager, player: Player?): ItemStack {
        val item = ItemStack(Material.POISONOUS_POTATO)
        val meta = item.itemMeta ?: return item
        meta.displayName(Component.text(lang.getMessage(player, "gui.world_gate_item.name")).decoration(TextDecoration.ITALIC, false))
        meta.lore(lang.getComponentList(player, "gui.world_gate_item.lore_unbound"))
        try {
            meta.setMaxStackSize(1)
        } catch (_: Exception) {
        }

        meta.setItemModel(NamespacedKey("kota_server", "mwm_misc"))
        item.itemMeta = meta

        item.unsetData(DataComponentTypes.CONSUMABLE)
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString("world_gate").build())

        ItemTag.tagItem(item, ItemTag.TYPE_WORLD_GATE)
        return item
    }

    fun bindWorld(item: ItemStack, worldUuid: UUID, worldName: String, lang: LanguageManager, player: Player?) {
        val meta = item.itemMeta ?: return
        meta.persistentDataContainer.set(WORLD_UUID_KEY, PersistentDataType.STRING, worldUuid.toString())
        meta.persistentDataContainer.remove(TARGET_WORLD_NAME_KEY)
        meta.setEnchantmentGlintOverride(true)
        meta.lore(lang.getComponentList(player, "gui.world_gate_item.lore_bound", mapOf("destination" to worldName)))
        item.itemMeta = meta
    }

    fun bindExternalWorld(item: ItemStack, worldName: String, displayName: String, lang: LanguageManager, player: Player?) {
        val meta = item.itemMeta ?: return
        meta.persistentDataContainer.set(TARGET_WORLD_NAME_KEY, PersistentDataType.STRING, worldName)
        meta.persistentDataContainer.remove(WORLD_UUID_KEY)
        meta.setEnchantmentGlintOverride(true)
        meta.lore(lang.getComponentList(player, "gui.world_gate_item.lore_bound", mapOf("destination" to displayName)))
        item.itemMeta = meta
    }

    fun unbindWorld(item: ItemStack, lang: LanguageManager, player: Player?) {
        val meta = item.itemMeta ?: return
        meta.persistentDataContainer.remove(WORLD_UUID_KEY)
        meta.persistentDataContainer.remove(TARGET_WORLD_NAME_KEY)
        meta.setEnchantmentGlintOverride(false)

        val baseItem = createBaseWorldGateItem(lang, player)
        meta.displayName(baseItem.itemMeta.displayName())
        meta.lore(baseItem.itemMeta.lore())
        item.itemMeta = meta
    }

    fun getBoundWorldUuid(item: ItemStack): UUID? {
        val meta = item.itemMeta ?: return null
        val uuidStr = meta.persistentDataContainer.get(WORLD_UUID_KEY, PersistentDataType.STRING) ?: return null
        return try {
            UUID.fromString(uuidStr)
        } catch (_: Exception) {
            null
        }
    }

    fun getBoundTargetWorldName(item: ItemStack): String? {
        val meta = item.itemMeta ?: return null
        return meta.persistentDataContainer.get(TARGET_WORLD_NAME_KEY, PersistentDataType.STRING)
    }
}
