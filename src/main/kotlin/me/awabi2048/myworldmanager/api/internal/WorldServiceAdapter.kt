package me.awabi2048.myworldmanager.api.internal

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.service.ApiPortalSnapshot
import me.awabi2048.myworldmanager.api.service.ApiWorldService
import me.awabi2048.myworldmanager.api.service.ExpansionSequenceOptions
import me.awabi2048.myworldmanager.api.service.ManagedWorldCreationRequest
import me.awabi2048.myworldmanager.api.service.WorldOperationLease
import me.awabi2048.myworldmanager.api.service.WorldOperation
import me.awabi2048.myworldmanager.model.BorderExpansionRecord
import me.awabi2048.myworldmanager.model.PortalData
import me.awabi2048.myworldmanager.model.PortalType
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.service.BorderResetSpawnService
import me.awabi2048.myworldmanager.util.PortalItemUtil
import me.awabi2048.myworldmanager.util.WorldNameValidation
import me.awabi2048.myworldmanager.util.WorldRuntimePolicies
import me.awabi2048.myworldmanager.session.PreviewSessionManager
import me.awabi2048.myworldmanager.session.PreviewSource
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.io.File
import java.util.UUID
import java.util.concurrent.CompletableFuture

internal class WorldServiceAdapter(private val plugin: MyWorldManager) : ApiWorldService {
    private val borderResetSpawnService = BorderResetSpawnService()

    override fun validateWorldName(player: Player, worldName: String): Component? {
        return when (val result = plugin.worldValidator.validateName(worldName)) {
            is WorldNameValidation.Ok -> null
            is WorldNameValidation.Failure ->
                plugin.languageManager.getComponent(player, result.messageKey, result.placeholders)
        }
    }

    override fun createFromTemplate(
        templateId: String,
        ownerUuid: UUID,
        worldName: String,
        cost: Int
    ): CompletableFuture<Boolean> {
        return plugin.worldService.createWorld(templateId, ownerUuid, worldName, cost)
    }

    override fun createManagedWorld(request: ManagedWorldCreationRequest): CompletableFuture<Boolean> {
        return plugin.worldService.createManagedWorld(request)
    }

    override fun previewTemplate(
        player: Player,
        templateId: String,
        onReturn: () -> Unit
    ): Boolean {
        return plugin.previewSessionManager.startPreview(
            player,
            PreviewSessionManager.PreviewTarget.Template(templateId),
            PreviewSource.EXTERNAL,
            onReturn
        )
    }

    override fun teleportToWorld(player: Player, worldUuid: UUID) {
        plugin.worldService.teleportToWorld(player, worldUuid)
    }

    override fun loadWorld(worldUuid: UUID): Boolean {
        return plugin.worldService.loadWorld(worldUuid)
    }

    override fun getWorldFolderName(worldData: WorldData): String {
        return plugin.worldService.getWorldFolderName(worldData)
    }

    override fun getWorldDirectory(worldData: WorldData): File {
        return plugin.worldService.getWorldDirectory(worldData)
    }

    override fun getWorldCreationDirectory(worldData: WorldData): File {
        return plugin.worldService.getWorldCreationDirectory(worldData)
    }

    override fun getWorldCreationDirectory(folderName: String): File {
        return plugin.worldService.getWorldCreationDirectory(folderName)
    }

    override fun getMainWorldDataDirectory(): File {
        return com.awabi2048.ccsystem.CCSystem.getAPI()
            .getWorldDirectoryService()
            .mainWorldDataDirectory()
            .toFile()
    }

    override fun getWorldDataFile(worldUuid: UUID): File {
        return File(File(plugin.dataFolder, "my_worlds"), "$worldUuid.yml")
    }

    override fun unloadWorldForMaintenance(
        worldUuid: UUID,
        save: Boolean,
        lease: WorldOperationLease?
    ): CompletableFuture<Boolean> {
        return plugin.worldService.unloadWorldForMaintenance(worldUuid, save, lease)
    }

    override fun startWorldBorderExpansionSequence(
        player: Player,
        worldUuid: UUID,
        options: ExpansionSequenceOptions
    ): Boolean {
        return plugin.worldSettingsListener.startWorldBorderExpansionSequence(player, worldUuid, options)
    }

    override fun expandWorldBorder(worldUuid: UUID, direction: BlockFace?): Boolean {
        val lease = me.awabi2048.myworldmanager.api.MyWorldManagerApi
            .tryAcquireWorldOperation(worldUuid, WorldOperation.EXPAND) ?: return false
        return try {
            expandWorldBorderUnlocked(worldUuid, direction)
        } finally {
            lease.close()
        }
    }

    private fun expandWorldBorderUnlocked(worldUuid: UUID, direction: BlockFace?): Boolean {
        val worldData = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return false
        val world = Bukkit.getWorld(getWorldFolderName(worldData)) ?: return false
        val border = world.worldBorder
        val oldSize = border.size
        val oldCenter = border.center.clone()
        val levelBefore = worldData.borderExpansionLevel
        val newSize = oldSize * 2

        if (direction != null) {
            val radius = oldSize / 2.0
            val (shiftX, shiftZ) = when (direction) {
                BlockFace.NORTH_WEST -> -radius to -radius
                BlockFace.NORTH_EAST -> radius to -radius
                BlockFace.SOUTH_WEST -> -radius to radius
                BlockFace.SOUTH_EAST -> radius to radius
                else -> 0.0 to 0.0
            }
            val newCenter = oldCenter.clone().add(shiftX, 0.0, shiftZ)
            newCenter.x = Math.round(newCenter.x).toDouble()
            newCenter.z = Math.round(newCenter.z).toDouble()
            border.setCenter(newCenter)
            worldData.borderCenterPos = newCenter
        }

        border.setSize(newSize)
        worldData.borderExpansionLevel += 1
        val newCenter = border.center
        worldData.borderExpansionHistory.add(
            BorderExpansionRecord(
                levelBefore = levelBefore,
                levelAfter = worldData.borderExpansionLevel,
                direction = direction?.name,
                oldCenterX = oldCenter.x,
                oldCenterZ = oldCenter.z,
                oldSize = oldSize,
                newCenterX = newCenter.x,
                newCenterZ = newCenter.z,
                newSize = newSize
            )
        )
        plugin.worldConfigRepository.save(worldData)
        return true
    }

    override fun stepBackWorldBorder(worldUuid: UUID): Boolean {
        val worldData = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return false
        val record = worldData.latestBorderExpansionRecord() ?: return false
        val world = Bukkit.getWorld(getWorldFolderName(worldData)) ?: return false
        val oldCenter = Location(world, record.oldCenterX, world.spawnLocation.y, record.oldCenterZ)

        world.worldBorder.setCenter(oldCenter)
        world.worldBorder.setSize(record.oldSize)
        worldData.borderCenterPos = oldCenter
        worldData.borderExpansionLevel = record.levelBefore
        val removedCost = WorldRuntimePolicies.expansionCost(plugin.config, record.levelAfter)
        worldData.cumulativePoints = (worldData.cumulativePoints - removedCost).coerceAtLeast(0)
        val recordIndex = worldData.borderExpansionHistory.indexOfLast { it == record }
        if (recordIndex >= 0) {
            worldData.borderExpansionHistory.removeAt(recordIndex)
        }

        borderResetSpawnService.apply(world, worldData)
        teleportPlayersOutsideBorder(world, oldCenter)
        plugin.worldConfigRepository.save(worldData)
        return true
    }

    override fun reloadWorldData() {
        plugin.worldConfigRepository.loadAll()
    }

    override fun reloadPortalData() {
        plugin.portalRepository.loadAll()
    }

    override fun getRelatedPortals(worldUuid: UUID): List<ApiPortalSnapshot> {
        val worldData = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return emptyList()
        val worldKey = worldData.worldKey
        return plugin.portalRepository.findAll()
            .filter { it.worldUuid == worldUuid || it.worldKey == worldKey || it.targetWorldKey == worldKey }
            .map(::toSnapshot)
    }

    override fun replaceRelatedPortals(worldUuid: UUID, portals: List<ApiPortalSnapshot>) {
        val worldData = plugin.worldConfigRepository.findByUuid(worldUuid)
        val worldKey = worldData?.worldKey ?: "minecraft:my_world.$worldUuid"
        val existing = plugin.portalRepository.findAll()
            .filter { it.worldUuid == worldUuid || it.worldKey == worldKey || it.targetWorldKey == worldKey }
            .map { it.id }
        existing.forEach(plugin.portalRepository::removePortal)
        portals.map(::toPortalData).forEach(plugin.portalRepository::addPortal)
        plugin.portalRepository.loadAll()
    }

    override fun createLinkedWorldPortalItem(player: Player, worldUuid: UUID): ItemStack? {
        val worldData = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return null
        val item = PortalItemUtil.createBasePortalItem(plugin.languageManager, player)
        PortalItemUtil.bindWorld(item, worldUuid, worldData.name, plugin.languageManager, player)
        return item
    }

    override fun deleteWorld(worldUuid: UUID): CompletableFuture<Boolean> {
        return plugin.worldService.deleteWorld(worldUuid)
    }

    override fun deleteWorldForMaintenance(worldUuid: UUID): CompletableFuture<Boolean> {
        return plugin.worldService.deleteWorldForMaintenance(worldUuid)
    }

    private fun toSnapshot(portal: PortalData): ApiPortalSnapshot {
        return ApiPortalSnapshot(
            id = portal.id,
            worldKey = portal.worldKey,
            x = portal.x,
            y = portal.y,
            z = portal.z,
            worldUuid = portal.worldUuid,
            targetWorldKey = portal.targetWorldKey,
            showText = portal.showText,
            particleColorRgb = portal.particleColor.asRGB(),
            ownerUuid = portal.ownerUuid,
            createdAt = portal.createdAt,
            textDisplayUuid = portal.textDisplayUuid,
            type = portal.type.key,
            minX = portal.minX,
            minY = portal.minY,
            minZ = portal.minZ,
            maxX = portal.maxX,
            maxY = portal.maxY,
            maxZ = portal.maxZ
        )
    }

    private fun toPortalData(snapshot: ApiPortalSnapshot): PortalData {
        return PortalData(
            id = snapshot.id,
            worldKey = snapshot.worldKey,
            x = snapshot.x,
            y = snapshot.y,
            z = snapshot.z,
            worldUuid = snapshot.worldUuid,
            targetWorldKey = snapshot.targetWorldKey,
            showText = snapshot.showText,
            particleColor = Color.fromRGB(snapshot.particleColorRgb),
            ownerUuid = snapshot.ownerUuid,
            createdAt = snapshot.createdAt,
            textDisplayUuid = snapshot.textDisplayUuid,
            type = PortalType.fromKey(snapshot.type),
            minX = snapshot.minX,
            minY = snapshot.minY,
            minZ = snapshot.minZ,
            maxX = snapshot.maxX,
            maxY = snapshot.maxY,
            maxZ = snapshot.maxZ
        )
    }

    private fun teleportPlayersOutsideBorder(world: org.bukkit.World, targetLocation: Location) {
        val border = world.worldBorder
        val target = targetLocation.clone()
        world.players
            .filter { !border.isInside(it.location) }
            .forEach { it.teleport(target) }
    }
}
