package me.awabi2048.myworldmanager.command

import com.awabi2048.ccsystem.api.item.ItemGrantDefinition
import com.awabi2048.ccsystem.api.item.ItemGrantProvider
import com.awabi2048.ccsystem.api.item.ItemGrantRequest
import com.awabi2048.ccsystem.api.item.ItemGrantResult
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.util.CustomItem

class MyWorldItemGrantProvider(
    private val plugin: MyWorldManager
) : ItemGrantProvider {
    override val owner: String = "myworld"

    override fun definitions(): Collection<ItemGrantDefinition> =
        CustomItem.entries
            .filterNot { it.id == "like_sign" }
            .map { item ->
                ItemGrantDefinition(
                    id = "myworld.${item.id}",
                    permission = "cc.item.give.myworld",
                    maximumAmount = item.create(plugin.languageManager, null).maxStackSize,
                    argumentSuggestions = if (item == CustomItem.BOTTLED_BIOME_AIR) {
                        { emptyList() }
                    } else {
                        { emptyList() }
                    }
                )
            }

    override fun grant(request: ItemGrantRequest): ItemGrantResult {
        val customItem = CustomItem.fromId(request.definition.id.removePrefix("myworld."))
            ?: return ItemGrantResult(false, 0, 0, "unknown item id")
        val item = if (customItem == CustomItem.BOTTLED_BIOME_AIR) {
            customItem.createWithBiome(
                plugin.languageManager,
                request.target,
                request.arguments.firstOrNull() ?: "plains"
            )
        } else {
            customItem.create(plugin.languageManager, request.target)
        }
        item.amount = request.amount

        var dropped = 0
        request.target.inventory.addItem(item).values.forEach { overflow ->
            dropped += overflow.amount
            request.target.world.dropItemNaturally(request.target.location, overflow)
        }
        return ItemGrantResult(
            success = true,
            grantedAmount = (request.amount - dropped).coerceAtLeast(0),
            droppedAmount = dropped,
            message = null
        )
    }
}
