package me.awabi2048.myworldmanager.api.internal

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.service.ApiPortalSnapshot
import me.awabi2048.myworldmanager.api.service.ApiWorldService
import me.awabi2048.myworldmanager.model.PortalData
import me.awabi2048.myworldmanager.model.PortalType
import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.entity.Player
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
}
