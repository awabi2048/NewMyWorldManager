package me.awabi2048.myworldmanager.util

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.entity.Player
import me.awabi2048.myworldmanager.util.LanguageManager
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemFlag
import com.google.common.collect.ArrayListMultimap
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.CustomModelData

enum class CustomItem(val id: String) {
    WORLD_PORTAL("world_portal") {
        override fun create(lang: LanguageManager, player: Player?): ItemStack = PortalItemUtil.createBasePortalItem(lang, player)
    },

    WORLD_GATE("world_gate") {
        override fun create(lang: LanguageManager, player: Player?): ItemStack = WorldGateItemUtil.createBaseWorldGateItem(lang, player)
    },

    EMPTY_BIOME_BOTTLE("empty_biome_bottle") {
        override fun create(lang: LanguageManager, player: Player?): ItemStack {
            val item = ItemStack(Material.POISONOUS_POTATO)
            val meta = item.itemMeta ?: return item

            meta.displayName(lang.getComponent(player, "custom_item.empty_biome_bottle.name"))
            meta.lore(lang.getMenuLore(player, "custom_item.empty_biome_bottle.lore"))

            meta.setMaxStackSize(4)
            meta.setItemModel(NamespacedKey("kota_server", "mwm_misc"))
            item.itemMeta = meta

            item.unsetData(DataComponentTypes.CONSUMABLE)
            item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString("empty_biome_bottle").build())

            ItemTag.tagItem(item, ItemTag.TYPE_EMPTY_BIOME_BOTTLE)
            return item
        }
    },

    BOTTLED_BIOME_AIR("bottled_biome_air") {
        override fun create(lang: LanguageManager, player: Player?): ItemStack {
            // Default to plains if no biome info is provided
            return createWithBiome(lang, player, "plains")
        }
    },

    MOON_STONE("moon_stone") {
        override fun create(lang: LanguageManager, player: Player?): ItemStack {
            val item = ItemStack(Material.POISONOUS_POTATO)
            val meta = item.itemMeta ?: return item

            meta.displayName(lang.getComponent(player, "custom_item.moon_stone.name"))
            meta.lore(lang.getMenuLore(player, "custom_item.moon_stone.lore"))

            meta.setMaxStackSize(1)
            meta.setItemModel(NamespacedKey("kota_server", "mwm_misc"))
            item.itemMeta = meta

            item.unsetData(DataComponentTypes.CONSUMABLE)
            item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString("moon_stone").build())

            ItemTag.tagItem(item, ItemTag.TYPE_MOON_STONE)
            return item
        }
    },

    WORLD_SEED("world_seed") {
        override fun create(lang: LanguageManager, player: Player?): ItemStack {
            val item = ItemStack(Material.POISONOUS_POTATO)
            val meta = item.itemMeta ?: return item

            meta.displayName(lang.getComponent(player, "custom_item.world_seed.name"))
            meta.lore(actionLore(lang, player, "custom_item.world_seed"))

            meta.setMaxStackSize(1)
            meta.setItemModel(NamespacedKey("kota_server", "mwm_misc"))
            item.itemMeta = meta

            item.unsetData(DataComponentTypes.CONSUMABLE)
            item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString("world_seed").build())

            ItemTag.tagItem(item, ItemTag.TYPE_WORLD_SEED)
            return item
        }
    },

    TOUR_SIGN("tour_sign") {
        override fun create(lang: LanguageManager, player: Player?): ItemStack {
            val item = ItemStack(Material.END_PORTAL_FRAME)
            val meta = item.itemMeta ?: return item

            meta.displayName(lang.getComponent(player, "custom_item.tour_sign.name"))
            meta.lore(actionLore(lang, player, "custom_item.tour_sign"))

            meta.setMaxStackSize(16)
            meta.setItemModel(NamespacedKey("minecraft", "pale_oak_sign"))
            item.itemMeta = meta

            item.unsetData(DataComponentTypes.CONSUMABLE)
            item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString("tour_sign").build())

            ItemTag.tagItem(item, ItemTag.TYPE_TOUR_SIGN)
            return item
        }
    },

    @Deprecated("Use TOUR_SIGN instead")
    LIKE_SIGN("like_sign") {
        override fun create(lang: LanguageManager, player: Player?): ItemStack = TOUR_SIGN.create(lang, player)
    };

    abstract fun create(lang: LanguageManager, player: Player?): ItemStack

    fun createWithBiome(lang: LanguageManager, player: Player?, biomeId: String): ItemStack {
        val item = ItemStack(Material.POISONOUS_POTATO)
        val meta = item.itemMeta ?: return item

        val biomeName = lang.getMessage(player, "biomes.${biomeId.lowercase()}")
        meta.displayName(lang.getComponent(player, "custom_item.bottled_biome_air.name", mapOf("biome" to biomeName)))
        meta.lore(lang.getMenuLore(player, "custom_item.bottled_biome_air.lore"))

        meta.setMaxStackSize(1)
        meta.setItemModel(NamespacedKey("kota_server", "mwm_misc"))
        item.itemMeta = meta

        item.unsetData(DataComponentTypes.CONSUMABLE)
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString("bottled_biome_air").build())

        ItemTag.tagItem(item, ItemTag.TYPE_BOTTLED_BIOME_AIR)
        ItemTag.setBiomeId(item, biomeId)
        return item
    }

    companion object {
        fun fromId(id: String): CustomItem? = values().find { it.id.equals(id, ignoreCase = true) }

        private fun actionLore(lang: LanguageManager, player: Player?, key: String) =
            CCSystem.getAPI().getLoreService().render(GuiLoreSpec.Blocks(listOf(
                GuiLoreBlock(lang.getMessageList(player, "$key.description").map(GuiLoreLine::Text)),
                GuiLoreBlock(listOf(GuiLoreLine.Action(
                    lang.getMessage(player, "lore.click.right"),
                    lang.getMessage(player, "$key.action")
                )))
            )))
    }
}
