package me.awabi2048.myworldmanager.util

import me.awabi2048.myworldmanager.MyWorldManager
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import io.papermc.paper.datacomponent.DataComponentTypes
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

object ItemConverter {
    private val plainSerializer = PlainTextComponentSerializer.plainText()
    private val legacySerializer = LegacyComponentSerializer.legacySection()
    private val legacyWorldSeedNames = listOf("ワールドの種", "マイワールドの種")

    private fun containsAnyLegacySeedName(value: String): Boolean {
        return legacyWorldSeedNames.any { value.contains(it) }
    }

    /**
     * 旧仕様のアイテムを現行のアイテムに変換する
     * @param item 変換対象のアイテム
     * @param plugin プラグインインスタンス
     * @return 変換後のアイテム（変換不要な場合は元のアイテム）
     */
    fun convert(item: ItemStack?, plugin: MyWorldManager): ItemStack? {
        if (item == null || item.type == Material.AIR) return item
        val meta = item.itemMeta ?: return item
        
        // アイテム表示名の取得
        val displayName = meta.displayName()
        val plainName = displayName?.let { plainSerializer.serialize(it).trim() } ?: ""
        val legacyName = displayName?.let { legacySerializer.serialize(it).trim() } ?: ""
        
        // DataComponent (minecraft:item_name) の取得
        val itemNameComponent = item.getData(DataComponentTypes.ITEM_NAME)
        val plainItemName = itemNameComponent?.let { plainSerializer.serialize(it).trim() } ?: ""
        val legacyItemName = itemNameComponent?.let { legacySerializer.serialize(it).trim() } ?: ""

        val pdc = meta.persistentDataContainer
        val legacyIdKey = org.bukkit.NamespacedKey("myworldmanager", "item_id")
        val legacyId = pdc.get(legacyIdKey, org.bukkit.persistence.PersistentDataType.STRING)

        // 1. ワールドポータル
        if (item.type == Material.END_PORTAL_FRAME && (legacyName == "§dワールドポータル" || legacyItemName == "§dワールドポータル")) {
            val newItem = CustomItem.WORLD_PORTAL.create(plugin.languageManager, null)
            newItem.amount = item.amount
            return newItem
        }

        // 2. ワールドの種
        val isLegacySeedByName =
            plainName == "【ワールドの種】" ||
                containsAnyLegacySeedName(plainName) ||
                plainItemName == "【ワールドの種】" ||
                containsAnyLegacySeedName(plainItemName)
        val isLegacySeedById = legacyId == "my_world_seed" || legacyId == "world_seed"

        if ((item.type == Material.MAGMA_CREAM && isLegacySeedByName) || isLegacySeedById) {
            val newItem = CustomItem.WORLD_SEED.create(plugin.languageManager, null)
            newItem.amount = item.amount.coerceAtLeast(1)
            return newItem
        }

        return item
    }
    
    /**
     * インベントリ内の全アイテムを変換する
     */
    fun convertInventory(inventory: org.bukkit.inventory.Inventory, plugin: MyWorldManager) {
        for (i in 0 until inventory.size) {
            val item = inventory.getItem(i) ?: continue
            val converted = convert(item, plugin)
            if (converted != item) {
                inventory.setItem(i, converted)
            }
        }
    }
}
