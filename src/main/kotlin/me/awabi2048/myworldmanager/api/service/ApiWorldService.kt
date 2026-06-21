package me.awabi2048.myworldmanager.api.service

import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.io.File
import java.util.UUID
import java.util.concurrent.CompletableFuture

interface ApiWorldService {

    /** Uses the same validation rules as MWM's own creation flow. */
    fun validateWorldName(worldName: String): String?

    fun createFromTemplate(
        templateName: String,
        ownerUuid: UUID,
        worldName: String,
        cost: Int
    ): CompletableFuture<Boolean>

    fun teleportToWorld(
        player: Player,
        worldUuid: UUID
    )

    fun loadWorld(worldUuid: UUID): Boolean

    fun getWorldFolderName(worldData: WorldData): String

    fun getWorldDirectory(worldData: WorldData): File

    fun getWorldDataFile(worldUuid: UUID): File

    fun unloadWorldForMaintenance(worldUuid: UUID, save: Boolean): CompletableFuture<Boolean>

    fun startWorldBorderExpansionSequence(
        player: Player,
        worldUuid: UUID,
        options: ExpansionSequenceOptions
    ): Boolean

    fun expandWorldBorder(worldUuid: UUID, direction: BlockFace?): Boolean

    fun stepBackWorldBorder(worldUuid: UUID): Boolean

    fun reloadWorldData()

    fun reloadPortalData()

    fun getRelatedPortals(worldUuid: UUID): List<ApiPortalSnapshot>

    fun replaceRelatedPortals(worldUuid: UUID, portals: List<ApiPortalSnapshot>)

    fun createLinkedWorldPortalItem(player: Player, worldUuid: UUID): ItemStack?

    fun deleteWorld(worldUuid: UUID): CompletableFuture<Boolean>

    fun deleteWorldForMaintenance(worldUuid: UUID): CompletableFuture<Boolean>
}
