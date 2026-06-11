package me.awabi2048.myworldmanager.api.internal

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.service.ApiPortalSnapshot
import me.awabi2048.myworldmanager.api.service.ApiWorldService
import me.awabi2048.myworldmanager.api.service.ExpansionSequenceOptions
import me.awabi2048.myworldmanager.model.BorderExpansionRecord
import me.awabi2048.myworldmanager.model.PortalData
import me.awabi2048.myworldmanager.model.PortalType
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.util.PortalItemUtil
import me.awabi2048.myworldmanager.util.WorldRuntimePolicies
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

    override fun createFromTemplate(
        templateName: String,
        ownerUuid: UUID,
        worldName: String,
        cost: Int
    ): CompletableFuture<Boolean> {
        return plugin.worldService.createWorld(templateName, ownerUuid, worldName, cost)
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
        return File(Bukkit.getWorldContainer(), getWorldFolderName(worldData))
    }

    override fun getWorldDataFile(worldUuid: UUID): File {
        return File(File(plugin.dataFolder, "my_worlds"), "$worldUuid.yml")
    }

    override fun unloadWorldForMaintenance(worldUuid: UUID, save: Boolean): CompletableFuture<Boolean> {
        return plugin.worldService.unloadWorldForMaintenance(worldUuid, save)
    }

    override fun startWorldBorderExpansionSequence(
        player: Player,
        worldUuid: UUID,
        options: ExpansionSequenceOptions
    ): Boolean {
        return plugin.worldSettingsListener.startWorldBorderExpansionSequence(player, worldUuid, options)
    }

    override fun expandWorldBorder(worldUuid: UUID, direction: BlockFace?): Boolean {
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

        border.setSize(newSize, 0)
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
        world.worldBorder.setSize(record.oldSize, 0)
        worldData.borderCenterPos = oldCenter
        worldData.borderExpansionLevel = record.levelBefore
        val removedCost = WorldRuntimePolicies.expansionCost(plugin.config, record.levelAfter)
        worldData.cumulativePoints = (worldData.cumulativePoints - removedCost).coerceAtLeast(0)
        val recordIndex = worldData.borderExpansionHistory.indexOfLast { it == record }
        if (recordIndex >= 0) {
            worldData.borderExpansionHistory.removeAt(recordIndex)
        }

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
        val folderName = getWorldFolderName(worldData)
        return plugin.portalRepository.findAll()
            .filter { it.worldUuid == worldUuid || it.worldName == folderName || it.targetWorldName == folderName }
            .map(::toSnapshot)
    }

    override fun replaceRelatedPortals(worldUuid: UUID, portals: List<ApiPortalSnapshot>) {
        val worldData = plugin.worldConfigRepository.findByUuid(worldUuid)
        val folderName = worldData?.let { getWorldFolderName(it) } ?: "my_world.$worldUuid"
        val existing = plugin.portalRepository.findAll()
            .filter { it.worldUuid == worldUuid || it.worldName == folderName || it.targetWorldName == folderName }
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
            worldName = portal.worldName,
            x = portal.x,
            y = portal.y,
            z = portal.z,
            worldUuid = portal.worldUuid,
            targetWorldName = portal.targetWorldName,
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
            worldName = snapshot.worldName,
            x = snapshot.x,
            y = snapshot.y,
            z = snapshot.z,
            worldUuid = snapshot.worldUuid,
            targetWorldName = snapshot.targetWorldName,
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
