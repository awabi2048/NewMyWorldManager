package me.awabi2048.myworldmanager.util

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.entity.Player
import me.awabi2048.myworldmanager.util.LanguageManager
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemFlag
import com.google.common.collect.ArrayListMultimap

enum class CustomItem(val id: String) {
    WORLD_PORTAL("world_portal") {
        override fun create(lang: LanguageManager, player: Player?): ItemStack = PortalItemUtil.createBasePortalItem(lang, player)
    },
    
    EMPTY_BIOME_BOTTLE("empty_biome_bottle") {
        override fun create(lang: LanguageManager, player: Player?): ItemStack {
            val item = ItemStack(Material.WOODEN_PICKAXE)
            val meta = item.itemMeta ?: return item
            
            meta.displayName(net.kyori.adventure.text.Component.text(lang.getMessage(player, "custom_item.empty_biome_bottle.name")).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false))
            meta.lore(lang.getMessageList(player, "custom_item.empty_biome_bottle.lore").map { net.kyori.adventure.text.Component.text(it).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false) })
            
            meta.setMaxStackSize(4)
            meta.setItemModel(NamespacedKey("my_world_manager", "empty_biome_bottle"))
            meta.setTool(null)
            meta.setAttributeModifiers(ArrayListMultimap.create())
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)

            item.itemMeta = meta
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
            val item = ItemStack(Material.WOODEN_PICKAXE)
            val meta = item.itemMeta ?: return item
            
            meta.displayName(net.kyori.adventure.text.Component.text(lang.getMessage(player, "custom_item.moon_stone.name")).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false))
            meta.lore(lang.getMessageList(player, "custom_item.moon_stone.lore").map { net.kyori.adventure.text.Component.text(it).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false) })
            
            meta.setMaxStackSize(1)
            meta.setItemModel(NamespacedKey("my_world_manager", "moon_stone"))
            meta.setTool(null)
            meta.setAttributeModifiers(ArrayListMultimap.create())
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)

            item.itemMeta = meta
            ItemTag.tagItem(item, ItemTag.TYPE_MOON_STONE)
            return item
        }
    },
    
    WORLD_SEED("world_seed") {
        override fun create(lang: LanguageManager, player: Player?): ItemStack {
            val item = ItemStack(Material.BEETROOT_SEEDS)
            val meta = item.itemMeta ?: return item
            
            meta.displayName(net.kyori.adventure.text.Component.text(lang.getMessage(player, "custom_item.world_seed.name")).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false))
            meta.lore(lang.getMessageList(player, "custom_item.world_seed.lore").map { net.kyori.adventure.text.Component.text(it).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false) })
            
            meta.setMaxStackSize(1)
            // No custom item model unless needed, sticking to material.
            // meta.setItemModel(NamespacedKey("my_world_manager", "world_seed")) 
            meta.setTool(null)
            meta.setAttributeModifiers(ArrayListMultimap.create())
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)

            item.itemMeta = meta
            ItemTag.tagItem(item, ItemTag.TYPE_WORLD_SEED)
            return item
        }
    };

    abstract fun create(lang: LanguageManager, player: Player?): ItemStack
    
    fun createWithBiome(lang: LanguageManager, player: Player?, biomeId: String): ItemStack {
        val item = ItemStack(Material.WOODEN_PICKAXE)
        val meta = item.itemMeta ?: return item
        
        val biomeName = lang.getMessage(player, "biomes.${biomeId.lowercase()}")
        meta.displayName(net.kyori.adventure.text.Component.text(lang.getMessage(player, "custom_item.bottled_biome_air.name", biomeName)).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false))
        meta.lore(lang.getMessageList(player, "custom_item.bottled_biome_air.lore").map { net.kyori.adventure.text.Component.text(it).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false) })
        
        meta.setMaxStackSize(1)
        meta.setItemModel(NamespacedKey("my_world_manager", "bottled_biome_air"))
        meta.setTool(null)
        meta.setAttributeModifiers(ArrayListMultimap.create())
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)

        item.itemMeta = meta
        ItemTag.tagItem(item, ItemTag.TYPE_BOTTLED_BIOME_AIR)
        ItemTag.setBiomeId(item, biomeId)
        return item
    }

    companion object {
        fun fromId(id: String): CustomItem? = values().find { it.id.equals(id, ignoreCase = true) }
    }
}
