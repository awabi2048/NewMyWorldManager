package me.awabi2048.myworldmanager.listener

import com.sk89q.worldedit.IncompleteRegionException
import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.math.Vector3
import com.sk89q.worldedit.math.transform.AffineTransform
import com.sk89q.worldedit.math.transform.Transform
import com.sk89q.worldedit.regions.Region
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.PortalData
import me.awabi2048.myworldmanager.model.PortalType
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

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
        val targetWorldKey: String?,
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

    private data class PendingMove(
        val worldKey: String,
        val offset: BlockVector3,
        val portals: List<RelativePortal>,
        var completedChecks: Int = 0
    )

    private val pendingClipboards = java.util.concurrent.ConcurrentHashMap<UUID, PendingClipboard>()
    private val pendingMoves = java.util.concurrent.ConcurrentHashMap<UUID, PendingMove>()
    private val createdAtFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private companion object {
        // FAWE は大規模な編集を非同期キューで処理するため、次の tick に一度だけ
        // 確認すると、移動先のブロックがまだ配置されていない場合があります。
        private const val MOVE_SYNC_RETRY_INTERVAL_TICKS = 1L
        private const val MOVE_SYNC_MAX_ATTEMPTS = 40
        private const val MOVE_SYNC_REQUIRED_COMPLETED_CHECKS = 2
    }

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
            pastePendingPortals(player.uniqueId, player.world.key.toString())
        })
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onWorldEditMoveCommand(event: PlayerCommandPreprocessEvent) {
        val command = WorldEditMoveCommandParser.parse(event.message) ?: return
        val offset = resolveMoveOffset(command, event.player) ?: return
        if (offset.x() == 0 && offset.y() == 0 && offset.z() == 0) return

        captureMove(event.player, offset)
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
        val sourceWorldKey = player.world.key.toString()
        val origin = runCatching { session.getPlacementPosition(actor) }
            .getOrElse { BukkitAdapter.asBlockVector(player.location) }

        val portals = plugin.portalRepository.findAll()
            .filter { it.worldKey == sourceWorldKey && isFullyContained(selection, it) }
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

        plugin.portalRepository.ensureWritableForOperation()

        for (portal in pending.portals) {
            plugin.portalRepository.removePortal(portal.sourceId)
            plugin.portalManager.removePortalVisuals(portal.sourceId)
        }
        pending.sourceRemoved = true
    }

    private fun pastePendingPortals(playerUuid: UUID, targetWorldKey: String) {
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

        // 貼り付け中に既存ポータルを先に削除すると、移行拒否時に一部だけ消えるため、
        // 追加・削除のどちらよりも前に永続書き込み可否を確定させます。
        runCatching { plugin.portalRepository.ensureWritableForOperation() }
            .onFailure {
                player.sendMessage(plugin.languageManager.getMessage(player, "messages.migration.required"))
                return
            }

        for (relativePortal in pending.portals) {
            val portal = relativePortal.toPortalData(
                targetWorldKey = targetWorldKey,
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
        plugin.portalManager.refreshWorldDisplayLifecycle(targetWorldKey)
    }

    private fun captureMove(player: org.bukkit.entity.Player, offset: BlockVector3) {
        val actor = BukkitAdapter.adapt(player)
        val session = WorldEdit.getInstance().sessionManager.get(actor)
        val selection = try {
            session.getSelection()
        } catch (_: IncompleteRegionException) {
            pendingMoves.remove(player.uniqueId)
            return
        }

        val sourceWorldKey = player.world.key.toString()
        val portals = plugin.portalRepository.findAll()
            // WorldGate は範囲データと課金を持つ別機能のため、//move 同期の対象から除外する。
            .filter { it.worldKey == sourceWorldKey && !it.isGate() && isFullyContained(selection, it) }
            // 原点をゼロにすることで、RelativePortal の座標を移動元の絶対座標として再利用する。
            .map { toRelativePortal(it, BlockVector3.at(0, 0, 0)) }

        if (portals.isEmpty()) {
            pendingMoves.remove(player.uniqueId)
            return
        }

        val pending = PendingMove(sourceWorldKey, offset, portals)
        pendingMoves[player.uniqueId] = pending

        // PlayerCommandPreprocessEvent はコマンド実行前に発生するため、実ブロックの
        // 移動が完了するまで次の tick 以降で再確認し、成功したものだけメタデータを更新する。
        schedulePendingMoveApply(player.uniqueId, pending, attempt = 0)
    }

    private fun schedulePendingMoveApply(playerUuid: UUID, pending: PendingMove, attempt: Int) {
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            applyPendingMove(playerUuid, pending, attempt)
        }, MOVE_SYNC_RETRY_INTERVAL_TICKS)
    }

    private fun applyPendingMove(playerUuid: UUID, pending: PendingMove, attempt: Int) {
        // 新しい //move が同じプレイヤーから発行された場合、古い再試行タスクが
        // 新しい操作へ干渉しないよう、保留オブジェクトの同一性を確認する。
        if (pendingMoves[playerUuid] !== pending) return

        val world = NamespacedKey.fromString(pending.worldKey)?.let(Bukkit::getWorld)
            ?: run {
                pendingMoves.remove(playerUuid, pending)
                return
            }

        // 移動先だけが先に配置された途中状態で確定すると、移動元のUUIDを早く解放して
        // 重複登録を招くため、移動元のフレーム消去も完了条件に含める。
        val moveNotReady = pending.portals.any { relativePortal ->
            val sourceBlock = world.getBlockAt(relativePortal.relativeX, relativePortal.relativeY, relativePortal.relativeZ)
            val targetX = relativePortal.relativeX + pending.offset.x()
            val targetY = relativePortal.relativeY + pending.offset.y()
            val targetZ = relativePortal.relativeZ + pending.offset.z()
            val targetBlock = world.getBlockAt(targetX, targetY, targetZ)
            sourceBlock.type == Material.END_PORTAL_FRAME || targetBlock.type != Material.END_PORTAL_FRAME
        }

        if (moveNotReady) {
            pending.completedChecks = 0
        } else {
            pending.completedChecks++
        }

        if (pending.completedChecks < MOVE_SYNC_REQUIRED_COMPLETED_CHECKS) {
            if (attempt < MOVE_SYNC_MAX_ATTEMPTS) {
                schedulePendingMoveApply(playerUuid, pending, attempt + 1)
                return
            }

            // タイムアウト時はFAWEの処理が終わっていない可能性があるため、リポジトリを
            // 変更せず保留だけ解除する。途中状態で削除・再登録するとデータを失うためである。
            pendingMoves.remove(playerUuid, pending)
            plugin.logger.warning(
                "[Portal] //move の完了状態を確認できないまま同期期限に達しました: " +
                    "world=${pending.worldKey}, attempt=$attempt"
            )
            return
        }

        pendingMoves.remove(playerUuid, pending)
        var changed = false

        for (relativePortal in pending.portals) {
            val sourceBlock = world.getBlockAt(relativePortal.relativeX, relativePortal.relativeY, relativePortal.relativeZ)
            val targetX = relativePortal.relativeX + pending.offset.x()
            val targetY = relativePortal.relativeY + pending.offset.y()
            val targetZ = relativePortal.relativeZ + pending.offset.z()
            val targetBlock = world.getBlockAt(targetX, targetY, targetZ)
            val sourceStillPortal = sourceBlock.type == Material.END_PORTAL_FRAME
            val targetIsPortal = targetBlock.type == Material.END_PORTAL_FRAME

            if (targetIsPortal) {
                val existing = plugin.portalRepository.findByContainingLocation(targetBlock.location)
                if (existing?.isGate() == true) {
                    // ゲートの登録を壊さず、ゲート内へのポータル登録だけを見送る。
                    plugin.logger.warning(
                        "[Portal] //move の移動先が WorldGate の範囲内にあるため、ポータル同期を見送りました: " +
                            "world=${pending.worldKey}, x=$targetX, y=$targetY, z=$targetZ"
                    )
                    continue
                }

                if (existing != null && existing.id != relativePortal.sourceId) {
                    plugin.portalRepository.removePortal(existing.id)
                    plugin.portalManager.removePortalVisuals(existing.id)
                }

                // 移動元のフレームが消えていれば同じ UUID を移し、leave pattern などで
                // 移動元にもフレームが残っていればコピー相当として新しい UUIDを発行する。
                val preserveId = !sourceStillPortal
                if (preserveId) {
                    plugin.portalRepository.removePortal(relativePortal.sourceId)
                    plugin.portalManager.removePortalVisuals(relativePortal.sourceId)
                }
                plugin.portalRepository.addPortal(
                    relativePortal.toTranslatedPortal(
                        worldKey = pending.worldKey,
                        offset = pending.offset,
                        preserveId = preserveId
                    )
                )
                changed = true
            } else if (!sourceStillPortal) {
                // 移動元も移動先もポータルフレームでない場合は、ポータル自体が
                // 消去された結果として扱い、残ったメタデータを掃除する。
                plugin.portalRepository.removePortal(relativePortal.sourceId)
                plugin.portalManager.removePortalVisuals(relativePortal.sourceId)
                changed = true
            }
        }

        if (changed) {
            plugin.portalManager.refreshWorldDisplayLifecycle(pending.worldKey)
        }
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
            targetWorldKey = portal.targetWorldKey,
            showText = portal.showText,
            particleColor = portal.particleColor,
            ownerUuid = portal.ownerUuid,
            createdAt = portal.createdAt,
            typeKey = portal.type.key
        )
    }

    private fun RelativePortal.toPortalData(
        targetWorldKey: String,
        pasteOrigin: BlockVector3,
        transform: Transform,
        preserveId: Boolean
    ): PortalData {
        val transformedPosition = transformRelative(relativeX, relativeY, relativeZ, transform)
        val transformedArea = transformArea(transform)

        return PortalData(
            id = if (preserveId) sourceId else UUID.randomUUID(),
            worldKey = targetWorldKey,
            x = pasteOrigin.x() + transformedPosition.x(),
            y = pasteOrigin.y() + transformedPosition.y(),
            z = pasteOrigin.z() + transformedPosition.z(),
            worldUuid = worldUuid,
            targetWorldKey = this.targetWorldKey,
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

    private fun RelativePortal.toTranslatedPortal(
        worldKey: String,
        offset: BlockVector3,
        preserveId: Boolean
    ): PortalData {
        return PortalData(
            id = if (preserveId) sourceId else UUID.randomUUID(),
            worldKey = worldKey,
            x = relativeX + offset.x(),
            y = relativeY + offset.y(),
            z = relativeZ + offset.z(),
            worldUuid = worldUuid,
            targetWorldKey = targetWorldKey,
            showText = showText,
            particleColor = particleColor,
            ownerUuid = ownerUuid,
            createdAt = if (preserveId) createdAt else LocalDateTime.now().format(createdAtFormatter),
            type = PortalType.fromKey(typeKey)
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

    private fun resolveMoveOffset(
        command: WorldEditMoveCommand,
        player: org.bukkit.entity.Player
    ): BlockVector3? {
        val offset = parseExplicitMoveVector(command.offsetToken, player)
            ?: runCatching {
                // WorldEdit 自身の方向解決を使用し、forward/me/left などの相対方向と
                // north-east 等の斜め方向を、プレイヤーの向きに合わせて解決する。
                WorldEdit.getInstance()
                    .getDiagonalDirection(BukkitAdapter.adapt(player), command.offsetToken)
            }.getOrNull()
            ?: return null

        return offset.multiply(command.multiplier)
    }

    private fun parseExplicitMoveVector(
        token: String,
        player: org.bukkit.entity.Player
    ): BlockVector3? {
        val isLocalVector = token.startsWith('^')
        val raw = if (isLocalVector) token.drop(1) else token
        val components = raw.split(',')
            .map { it.removePrefix("^").toIntOrNull() }
        if (components.size != 3 || components.any { it == null }) return null

        val vector = BlockVector3.at(
            components[0]!!,
            components[1]!!,
            components[2]!!
        )
        if (!isLocalVector) return vector

        // ^x,^y,^z は WorldEdit のローカル座標です。WorldEdit の OffsetConverter と
        // 同じ基底ベクトルで変換し、コマンド側と移動先の座標を一致させます。
        val location = BukkitAdapter.adapt(player.location)
        val yaw = Math.toRadians(location.yaw.toDouble() + 90.0)
        val pitch = Math.toRadians(-location.pitch.toDouble() + 90.0)
        val cosYaw = cos(yaw)
        val sinYaw = sin(yaw)
        val cosPitch = cos(pitch)
        val sinPitch = sin(pitch)
        val forward = location.direction
        val up = Vector3.at(
            cosYaw * cosPitch,
            sinPitch,
            sinYaw * cosPitch
        )
        val right = forward.cross(up).multiply(-1.0)
        val transform = AffineTransform(
            forward.x(), up.x(), right.x(), 0.0,
            forward.y(), up.y(), right.y(), 0.0,
            forward.z(), up.z(), right.z(), 0.0
        )
        return transform.apply(vector.toVector3()).round().toBlockPoint()
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
        pendingMoves.remove(event.player.uniqueId)
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
