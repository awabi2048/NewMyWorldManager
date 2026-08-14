package me.awabi2048.myworldmanager.util

import com.awabi2048.ccsystem.api.localization.generated.MyworldCustomItemKeys

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.MenuGesture
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

            meta.displayName(lang.getComponent(player, MyworldCustomItemKeys.CUSTOM_ITEM_EMPTY_BIOME_BOTTLE_NAME))
            meta.lore(com.awabi2048.ccsystem.CCSystem.getAPI().getLoreService().render(
                com.awabi2048.ccsystem.api.gui.GuiLoreSpec.Rich(
                    lang.getMessageList(player, MyworldCustomItemKeys.CUSTOM_ITEM_EMPTY_BIOME_BOTTLE_LORE)
                        .map(com.awabi2048.ccsystem.api.gui.GuiLoreLine::Text),
                    com.awabi2048.ccsystem.api.gui.GuiLoreFrame.BOTH,
                ),
            ))

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

            meta.displayName(lang.getComponent(player, MyworldCustomItemKeys.CUSTOM_ITEM_MOON_STONE_NAME))
            meta.lore(com.awabi2048.ccsystem.CCSystem.getAPI().getLoreService().render(
                com.awabi2048.ccsystem.api.gui.GuiLoreSpec.Rich(
                    lang.getMessageList(player, MyworldCustomItemKeys.CUSTOM_ITEM_MOON_STONE_LORE)
                        .map(com.awabi2048.ccsystem.api.gui.GuiLoreLine::Text),
                    com.awabi2048.ccsystem.api.gui.GuiLoreFrame.BOTH,
                ),
            ))

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

            meta.displayName(lang.getComponent(player, MyworldCustomItemKeys.CUSTOM_ITEM_WORLD_SEED_NAME))
            meta.lore(actionLore(lang, player, "custom_item.world_seed"))

            meta.setMaxStackSize(1)
            // ワールドの種は独自リソースパックに依存せず、バニラの種モデルを表示します。
            meta.setItemModel(NamespacedKey.minecraft("beetroot_seeds"))
            item.itemMeta = meta

            item.unsetData(DataComponentTypes.CONSUMABLE)

            ItemTag.tagItem(item, ItemTag.TYPE_WORLD_SEED)
            return item
        }
    },

    TOUR_SIGN("tour_sign") {
        override fun create(lang: LanguageManager, player: Player?): ItemStack {
            val item = ItemStack(TourSignItemPolicy.baseMaterial)
            val meta = item.itemMeta ?: return item

            meta.displayName(lang.getComponent(player, MyworldCustomItemKeys.CUSTOM_ITEM_TOUR_SIGN_NAME))
            meta.lore(actionLore(lang, player, "custom_item.tour_sign"))

            meta.setMaxStackSize(16)
            meta.setItemModel(TourSignItemPolicy.itemModel)
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
        meta.displayName(lang.getComponent(player, MyworldCustomItemKeys.CUSTOM_ITEM_BOTTLED_BIOME_AIR_NAME, mapOf("biome" to biomeName)))
        meta.lore(com.awabi2048.ccsystem.CCSystem.getAPI().getLoreService().render(
            com.awabi2048.ccsystem.api.gui.GuiLoreSpec.Rich(
                lang.getMessageList(player, MyworldCustomItemKeys.CUSTOM_ITEM_BOTTLED_BIOME_AIR_LORE)
                    .map(com.awabi2048.ccsystem.api.gui.GuiLoreLine::Text),
                com.awabi2048.ccsystem.api.gui.GuiLoreFrame.BOTH,
            ),
        ))

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
            CCSystem.getAPI().getLoreService().render(
                CCSystem.getAPI().getLoreService().compose(
                    GuiLoreSpec.Blocks(listOf(
                        GuiLoreBlock(lang.getMessageList(player, "$key.description").map(GuiLoreLine::Text))
                    )),
                    listOf(GuiLoreLine.Interaction(
                    player,
                    MenuGesture.RIGHT,
                    lang.getMessage(player, "$key.action")
                    ))
                )
            )
    }
}

internal object TourSignItemPolicy {
    val baseMaterial: Material = Material.POISONOUS_POTATO
    val itemModel: NamespacedKey = NamespacedKey("minecraft", "pale_oak_sign")
}
