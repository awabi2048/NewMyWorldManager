package me.awabi2048.myworldmanager.util

import me.awabi2048.myworldmanager.MyWorldManager
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

object ItemConverter {
    private val plainSerializer = PlainTextComponentSerializer.plainText()

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
        val displayName = meta.displayName() ?: return item
        val plainName = plainSerializer.serialize(displayName).trim()
        val pdc = meta.persistentDataContainer
        val legacyIdKey = org.bukkit.NamespacedKey("myworldmanager", "item_id")
        val legacyId = pdc.get(legacyIdKey, org.bukkit.persistence.PersistentDataType.STRING)
        
        // デバッグ用ログ: 対象のマテリアルを持つアイテム、またはPDCタグを持つアイテムのみログ出力
        if (item.type == Material.END_PORTAL_FRAME || item.type == Material.MAGMA_CREAM || legacyId != null) {
            plugin.logger.info("[ItemConverter Debug] Checking Item: ${item.type}, PlainName: '$plainName'")
            if (pdc.keys.isEmpty()) {
                plugin.logger.info("  > No PDC keys found.")
            } else {
                plugin.logger.info("  > PDC Keys:")
                pdc.keys.forEach { key ->
                    val strVal = pdc.get(key, org.bukkit.persistence.PersistentDataType.STRING)
                    val intVal = try { pdc.get(key, org.bukkit.persistence.PersistentDataType.INTEGER) } catch(e: Exception) { null }
                    val doubleVal = try { pdc.get(key, org.bukkit.persistence.PersistentDataType.DOUBLE) } catch(e: Exception) { null }
                    
                    val dispVal = strVal ?: intVal ?: doubleVal ?: "Unknown Type"
                    plugin.logger.info("    - $key: $dispVal")
                }
            }
        }

        // 1. ワールドポータル
        // 旧仕様: PDC["myworldmanager:item_id"] == "WORLD_PORTAL" OR (Material.END_PORTAL_FRAME AND Name: ワールドポータル)
        if (item.type == Material.END_PORTAL_FRAME && (plainName == "§bワールドポータル")) {
            val newItem = CustomItem.WORLD_PORTAL.create(plugin.languageManager, null)
            newItem.amount = item.amount
            return newItem
        }

        // 2. ワールドの種
        // 旧仕様: Material.MAGMA_CREAM, Name: §2§l【§c§lワールドの種§2§l】
        if (item.type == Material.MAGMA_CREAM && (plainName == "【ワールドの種】" || plainName.contains("ワールドの種"))) {
            val newItem = CustomItem.WORLD_SEED.create(plugin.languageManager, null)
            newItem.amount = item.amount
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
