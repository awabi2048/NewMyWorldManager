package me.awabi2048.myworldmanager.util

import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.meta.ItemMeta

object ItemMetaCompat {
    private val additionalTooltipFlag = runCatching {
        ItemFlag.valueOf("HIDE_ADDITIONAL_TOOLTIP")
    }.getOrNull()

    fun hideAdditionalTooltip(meta: ItemMeta) {
        additionalTooltipFlag?.let { meta.addItemFlags(it) }
    }
}
