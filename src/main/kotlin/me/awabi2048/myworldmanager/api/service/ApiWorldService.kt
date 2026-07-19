package me.awabi2048.myworldmanager.api.service

import me.awabi2048.myworldmanager.model.WorldData
import net.kyori.adventure.text.Component
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.World
import org.bukkit.WorldType
import org.bukkit.generator.ChunkGenerator
import org.bukkit.inventory.ItemStack
import java.io.File
import java.util.UUID
import java.util.concurrent.CompletableFuture

data class ManagedWorldSpawn(val x: Int, val y: Int, val z: Int)

enum class WorldPointBillingMode {
    STANDARD,
    NONE
}

data class ManagedWorldCreationRequest(
    val ownerUuid: UUID,
    val worldName: String,
    val sourceId: String,
    val type: me.awabi2048.myworldmanager.api.extension.WorldCreationType,
    val environment: World.Environment = World.Environment.NORMAL,
    val worldType: WorldType = WorldType.NORMAL,
    val generator: ChunkGenerator? = null,
    val generateStructures: Boolean = true,
    val initialSpawn: ManagedWorldSpawn? = null,
    val cost: Int,
    val billingMode: WorldPointBillingMode = WorldPointBillingMode.STANDARD,
    val initializeWorld: (World) -> Unit = {}
)

interface ApiWorldService {

    /**
     * Uses the same validation rules as MWM's own creation flow.
     * Returns a localized [Component] error message, or null when the name is valid.
     * Color codes are already decoded so callers can send the result as-is.
     */
    fun validateWorldName(player: Player, worldName: String): Component?

    fun createFromTemplate(
        templateId: String,
        ownerUuid: UUID,
        worldName: String,
        cost: Int,
        billingMode: WorldPointBillingMode = WorldPointBillingMode.STANDARD
    ): CompletableFuture<Boolean>

    /**
     * アドオン固有の生成設定を受け取りつつ、検証・排他・保存・イベント・失敗補償はMWMで確定する。
     */
    fun createManagedWorld(request: ManagedWorldCreationRequest): CompletableFuture<Boolean>

    fun previewTemplate(
        player: Player,
        templateId: String,
        onReturn: () -> Unit
    ): Boolean

    fun teleportToWorld(
        player: Player,
        worldUuid: UUID
    )

    fun loadWorld(worldUuid: UUID): Boolean

    fun getWorldFolderName(worldData: WorldData): String

    fun getWorldDirectory(worldData: WorldData): File

    fun getWorldCreationDirectory(worldData: WorldData): File

    fun getWorldCreationDirectory(folderName: String): File

    fun getMainWorldDataDirectory(): File

    fun getWorldDataFile(worldUuid: UUID): File

    fun unloadWorldForMaintenance(
        worldUuid: UUID,
        save: Boolean,
        lease: WorldOperationLease? = null
    ): CompletableFuture<Boolean>

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
