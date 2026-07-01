package me.awabi2048.myworldmanager.listener

import com.sk89q.worldedit.IncompleteRegionException
import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.math.Vector3
import com.sk89q.worldedit.math.transform.Transform
import com.sk89q.worldedit.regions.Region
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.PortalData
import me.awabi2048.myworldmanager.model.PortalType
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

class WorldEditPortalSyncListener(private val plugin: MyWorldManager) : Listener {
    private enum class ClipboardMode {
        COPY,
        CUT
    }

    private data class RelativePortal(
        val sourceId: UUID,
        val relativeX: Int,
        val relativeY: Int,
        val relativeZ: Int,
        val relativeMinX: Int?,
        val relativeMinY: Int?,
        val relativeMinZ: Int?,
        val relativeMaxX: Int?,
        val relativeMaxY: Int?,
        val relativeMaxZ: Int?,
        val worldUuid: UUID?,
        val targetWorldName: String?,
        val showText: Boolean,
        val particleColor: Color,
        val ownerUuid: UUID,
        val createdAt: String,
        val typeKey: String
    )

    private data class PendingClipboard(
        val mode: ClipboardMode,
        val sourceOrigin: BlockVector3,
        val portals: List<RelativePortal>,
        var sourceRemoved: Boolean = false,
        var sameIdPasteConsumed: Boolean = false
    )

    private val pendingClipboards = java.util.concurrent.ConcurrentHashMap<UUID, PendingClipboard>()
    private val createdAtFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onWorldEditClipboardCommand(event: PlayerCommandPreprocessEvent) {
        val operation = parseClipboardOperation(event.message) ?: return
        when (operation) {
            ClipboardMode.COPY,
            ClipboardMode.CUT -> captureClipboard(event.player, operation)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onWorldEditPasteCommand(event: PlayerCommandPreprocessEvent) {
        if (!isPasteCommand(event.message)) return
        val player = event.player
        Bukkit.getScheduler().runTask(plugin, Runnable {
            pastePendingPortals(player.uniqueId, player.world.name)
        })
    }

    private fun captureClipboard(player: org.bukkit.entity.Player, mode: ClipboardMode) {
        val actor = BukkitAdapter.adapt(player)
        val session = WorldEdit.getInstance().sessionManager.get(actor)
        val selection = try {
            session.getSelection()
        } catch (_: IncompleteRegionException) {
            pendingClipboards.remove(player.uniqueId)
            return
        }
        val sourceWorldName = BukkitAdapter.adapt(selection.world).name
        val origin = runCatching { session.getPlacementPosition(actor) }
            .getOrElse { BukkitAdapter.asBlockVector(player.location) }

        val portals = plugin.portalRepository.findAll()
            .filter { it.worldName == sourceWorldName && isFullyContained(selection, it) }
            .map { toRelativePortal(it, origin) }

        if (portals.isEmpty()) {
            pendingClipboards.remove(player.uniqueId)
            return
        }

        pendingClipboards[player.uniqueId] = PendingClipboard(mode, origin, portals)

        if (mode == ClipboardMode.CUT) {
            // WorldEditのcutはBukkitのBlockBreakEventを通らないため、メタデータ側も同じタイミングで移動元を消す。
            Bukkit.getScheduler().runTask(plugin, Runnable {
                removeCutSourcePortals(player.uniqueId)
            })
        }
    }

    private fun removeCutSourcePortals(playerUuid: UUID) {
        val pending = pendingClipboards[playerUuid] ?: return
        if (pending.mode != ClipboardMode.CUT || pending.sourceRemoved) return
        val player = Bukkit.getPlayer(playerUuid) ?: return
        val actor = BukkitAdapter.adapt(player)
        val session = WorldEdit.getInstance().sessionManager.get(actor)
        val clipboard = runCatching { session.getClipboard().clipboard }.getOrNull() ?: return
        if (clipboard.origin != pending.sourceOrigin) {
            return
        }

        for (portal in pending.portals) {
            plugin.portalManager.removePortalVisuals(portal.sourceId)
            plugin.portalRepository.removePortal(portal.sourceId)
        }
        pending.sourceRemoved = true
    }

    private fun pastePendingPortals(playerUuid: UUID, targetWorldName: String) {
        val pending = pendingClipboards[playerUuid] ?: return
        val player = Bukkit.getPlayer(playerUuid) ?: return
        val actor = BukkitAdapter.adapt(player)
        val session = WorldEdit.getInstance().sessionManager.get(actor)
        val clipboard = runCatching { session.getClipboard() }.getOrNull() ?: return
        if (clipboard.clipboard.origin != pending.sourceOrigin) {
            return
        }
        val transform = clipboard.transform

        val pasteOrigin = runCatching { session.getPlacementPosition(actor) }
            .getOrElse { BukkitAdapter.asBlockVector(player.location) }
        val preserveFirstCutIds = pending.mode == ClipboardMode.CUT && !pending.sameIdPasteConsumed

        for (relativePortal in pending.portals) {
            val portal = relativePortal.toPortalData(
                targetWorldName = targetWorldName,
                pasteOrigin = pasteOrigin,
                transform = transform,
                preserveId = preserveFirstCutIds
            )
            plugin.portalRepository.addPortal(portal)
        }

        if (preserveFirstCutIds) {
            pending.sameIdPasteConsumed = true
        }

        // addPortal はデータ保存のみのため、ペースト先ワールドの視覚表示（TextDisplay・パーティクル）を手動で復元する。
        plugin.portalManager.refreshWorldDisplayLifecycle(targetWorldName)
    }

    private fun toRelativePortal(portal: PortalData, origin: BlockVector3): RelativePortal {
        return RelativePortal(
            sourceId = portal.id,
            relativeX = portal.x - origin.x(),
            relativeY = portal.y - origin.y(),
            relativeZ = portal.z - origin.z(),
            relativeMinX = portal.minX?.minus(origin.x()),
            relativeMinY = portal.minY?.minus(origin.y()),
            relativeMinZ = portal.minZ?.minus(origin.z()),
            relativeMaxX = portal.maxX?.minus(origin.x()),
            relativeMaxY = portal.maxY?.minus(origin.y()),
            relativeMaxZ = portal.maxZ?.minus(origin.z()),
            worldUuid = portal.worldUuid,
            targetWorldName = portal.targetWorldName,
            showText = portal.showText,
            particleColor = portal.particleColor,
            ownerUuid = portal.ownerUuid,
            createdAt = portal.createdAt,
            typeKey = portal.type.key
        )
    }

    private fun RelativePortal.toPortalData(
        targetWorldName: String,
        pasteOrigin: BlockVector3,
        transform: Transform,
        preserveId: Boolean
    ): PortalData {
        val transformedPosition = transformRelative(relativeX, relativeY, relativeZ, transform)
        val transformedArea = transformArea(transform)

        return PortalData(
            id = if (preserveId) sourceId else UUID.randomUUID(),
            worldName = targetWorldName,
            x = pasteOrigin.x() + transformedPosition.x(),
            y = pasteOrigin.y() + transformedPosition.y(),
            z = pasteOrigin.z() + transformedPosition.z(),
            worldUuid = worldUuid,
            targetWorldName = this.targetWorldName,
            showText = showText,
            particleColor = particleColor,
            ownerUuid = ownerUuid,
            createdAt = if (preserveId) createdAt else LocalDateTime.now().format(createdAtFormatter),
            type = PortalType.fromKey(typeKey),
            minX = transformedArea?.let { pasteOrigin.x() + it.first.x() },
            minY = transformedArea?.let { pasteOrigin.y() + it.first.y() },
            minZ = transformedArea?.let { pasteOrigin.z() + it.first.z() },
            maxX = transformedArea?.let { pasteOrigin.x() + it.second.x() },
            maxY = transformedArea?.let { pasteOrigin.y() + it.second.y() },
            maxZ = transformedArea?.let { pasteOrigin.z() + it.second.z() }
        )
    }

    private fun RelativePortal.transformArea(transform: Transform): Pair<BlockVector3, BlockVector3>? {
        val minX = relativeMinX ?: return null
        val minY = relativeMinY ?: return null
        val minZ = relativeMinZ ?: return null
        val maxX = relativeMaxX ?: return null
        val maxY = relativeMaxY ?: return null
        val maxZ = relativeMaxZ ?: return null

        // rotate/flip後もワールドゲートを直方体として扱うため、8隅を変換してから包含範囲を再計算する。
        val corners = listOf(
            transformRelative(minX, minY, minZ, transform),
            transformRelative(minX, minY, maxZ, transform),
            transformRelative(minX, maxY, minZ, transform),
            transformRelative(minX, maxY, maxZ, transform),
            transformRelative(maxX, minY, minZ, transform),
            transformRelative(maxX, minY, maxZ, transform),
            transformRelative(maxX, maxY, minZ, transform),
            transformRelative(maxX, maxY, maxZ, transform)
        )

        val transformedMin = BlockVector3.at(
            corners.minOf { it.x() },
            corners.minOf { it.y() },
            corners.minOf { it.z() }
        )
        val transformedMax = BlockVector3.at(
            corners.maxOf { it.x() },
            corners.maxOf { it.y() },
            corners.maxOf { it.z() }
        )
        return transformedMin to transformedMax
    }

    private fun transformRelative(x: Int, y: Int, z: Int, transform: Transform): BlockVector3 {
        if (transform.isIdentity) return BlockVector3.at(x, y, z)
        return transform.apply(Vector3.at(x.toDouble(), y.toDouble(), z.toDouble()))
            .round()
            .toBlockPoint()
    }

    private fun isFullyContained(region: Region, portal: PortalData): Boolean {
        return if (portal.isGate()) {
            val min = BlockVector3.at(portal.getMinX(), portal.getMinY(), portal.getMinZ())
            val max = BlockVector3.at(portal.getMaxX(), portal.getMaxY(), portal.getMaxZ())
            region.contains(min) && region.contains(max)
        } else {
            region.contains(BlockVector3.at(portal.x, portal.y, portal.z))
        }
    }

    private fun parseClipboardOperation(message: String): ClipboardMode? {
        return when (normalizedCommandName(message)) {
            "copy" -> ClipboardMode.COPY
            "cut" -> ClipboardMode.CUT
            else -> null
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        pendingClipboards.remove(event.player.uniqueId)
    }

    private fun isPasteCommand(message: String): Boolean {
        return normalizedCommandName(message) == "paste"
    }

    private fun normalizedCommandName(message: String): String {
        val firstToken = message.trim().substringBefore(' ')
        val withoutSlash = firstToken.dropWhile { it == '/' }
        val withoutNamespace = withoutSlash.substringAfterLast(':')
        return withoutNamespace.dropWhile { it == '/' }.lowercase(Locale.ROOT)
    }
}
