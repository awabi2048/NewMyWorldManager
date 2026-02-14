package me.awabi2048.myworldmanager.ui.bedrock

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import java.util.UUID

class BedrockPlayerWorldListHolder(
    val page: Int,
    val showBackButton: Boolean
) : InventoryHolder {
    lateinit var inv: Inventory

    override fun getInventory(): Inventory {
        return inv
    }
}

class BedrockWorldActionHolder(
    val worldUuid: UUID,
    val returnPage: Int,
    val showBackButton: Boolean
) : InventoryHolder {
    lateinit var inv: Inventory

    override fun getInventory(): Inventory {
        return inv
    }
}

class BedrockSettingsHolder(
    val showBackButton: Boolean,
    val returnPage: Int
) : InventoryHolder {
    lateinit var inv: Inventory

    override fun getInventory(): Inventory {
        return inv
    }
}
