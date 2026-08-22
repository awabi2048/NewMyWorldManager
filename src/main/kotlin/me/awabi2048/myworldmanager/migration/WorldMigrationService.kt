package me.awabi2048.myworldmanager.migration

import com.awabi2048.ccsystem.api.localization.generated.MyworldGuiAdminKeys
import com.awabi2048.ccsystem.api.localization.generated.MyworldMessagesKeys
import com.awabi2048.ccsystem.api.localization.LocalizationKey

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiMenuDisplaySpec
import com.awabi2048.ccsystem.api.gui.GuiMenuActionIntent
import com.awabi2048.ccsystem.api.gui.GuiMenuEntrySpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuActionSafety
import com.awabi2048.ccsystem.api.gui.MenuGesture
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuSoundPresets
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import com.awabi2048.ccsystem.api.gui.MenuViewCategory
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.gui.menuGestureAction
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.api.service.ApiMigrationParticipantResult
import me.awabi2048.myworldmanager.api.service.ApiMigrationParticipantResultState
import me.awabi2048.myworldmanager.api.service.ApiMigrationParticipantState
import me.awabi2048.myworldmanager.api.service.ApiMigrationParticipantStatus
import me.awabi2048.myworldmanager.api.service.ApiMigrationTargetKind
import me.awabi2048.myworldmanager.api.service.ApiMigrationBlock
import me.awabi2048.myworldmanager.api.service.ApiMigrationPreflight
import me.awabi2048.myworldmanager.api.service.WorldOperation
import me.awabi2048.myworldmanager.model.ManagedDimension
import me.awabi2048.myworldmanager.repository.MetadataMigrationResult
import me.awabi2048.myworldmanager.repository.MetadataMigrationStatus
import me.awabi2048.myworldmanager.repository.QuarantinedTemplateData
import me.awabi2048.myworldmanager.repository.QuarantinedWorldData
import me.awabi2048.myworldmanager.util.GuiHelper
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

enum class MigrationWorldStatus {
    WAITING,
    RUNNING,
    RETRY,
    COMPLETED,
    FAILED
}

enum class MigrationWorldPhase {
    QUEUED,
    MOVE_STARTED,
    MOVED,
    VERIFIED,
}

data class MigrationWorldState(
    val uuid: UUID,
    var folderName: String,
    var status: MigrationWorldStatus,
    var phase: MigrationWorldPhase = MigrationWorldPhase.QUEUED,
    var attempts: Int = 0,
    var lastError: String? = null,
    var updatedAt: Instant = Instant.now()
)

data class MigrationStatusSnapshot(
    val running: Boolean,
    val worlds: List<MigrationWorldState>,
    val currentWorld: UUID?
)

/** statusの1行で使う状態別の表示色と短縮コードの組み合わせです。 */
private data class StatusPresentation(
    val color: NamedTextColor,
    val shortCodeKey: LocalizationKey<String>,
)

private fun MigrationWorldStatus.presentation(): StatusPresentation = when (this) {
    MigrationWorldStatus.WAITING -> StatusPresentation(
        NamedTextColor.YELLOW,
        MyworldMessagesKeys.MESSAGES_MIGRATION_STATUS_SHORT_WAITING,
    )
    MigrationWorldStatus.RUNNING -> StatusPresentation(
        NamedTextColor.GOLD,
        MyworldMessagesKeys.MESSAGES_MIGRATION_STATUS_SHORT_RUNNING,
    )
    MigrationWorldStatus.RETRY -> StatusPresentation(
        NamedTextColor.YELLOW,
        MyworldMessagesKeys.MESSAGES_MIGRATION_STATUS_SHORT_RETRY,
    )
    MigrationWorldStatus.COMPLETED -> StatusPresentation(
        NamedTextColor.GREEN,
        MyworldMessagesKeys.MESSAGES_MIGRATION_STATUS_SHORT_COMPLETED,
    )
    MigrationWorldStatus.FAILED -> StatusPresentation(
        NamedTextColor.RED,
        MyworldMessagesKeys.MESSAGES_MIGRATION_STATUS_SHORT_FAILED,
    )
}

/**
 * 旧ワールドの物理移行は、管理者が確認したexecuteだけを開始経路とする。
 * 状態を毎段階で永続化し、1ワールドずつ直列に処理する。
 */
class WorldMigrationService(
    private val plugin: MyWorldManager,
    private val resolver: WorldDirectoryResolver
) {
    private val runtime = CCSystem.getAPI().getMenuRuntimeService()
    private val stateFile = File(plugin.dataFolder, "data/world-migration-state.yml")
    private val states = ConcurrentHashMap<UUID, MigrationWorldState>()
    @Volatile private var stateFileUnreadableReason: String? = null
    @Volatile private var stateRecoveredFromBackup = false
    @Volatile private var running = false
    /** メタデータ移行はワールドディレクトリ移行と異なり、状態ファイルへ個別状態を持たせないため別途保持します。 */
    @Volatile private var metadataMigrationRunning = false
    @Volatile private var currentWorld: UUID? = null

    init {
        runtime.register(
            InventoryMenuDefinition(
                owner = OWNER,
                id = ROUTE_ID,
                renderer = { context -> renderConfirmation(context.player) },
                actions = mapOf(
                    ACTION_EXECUTE to MenuActionHandler { context ->
                        requestExecute(context.player, confirmed = true)
                        MenuActionResult.Success(MenuUpdate.Close)
                    },
                    ACTION_CANCEL to MenuActionHandler { MenuActionResult.Success(MenuUpdate.Close) },
                ),
                openSound = MenuSoundPresets.CONFIRMATION_OPEN,
            ),
        )
        loadState()
        if (stateRecoveredFromBackup) {
            // 破損したprimaryを、検証済みbackupから復元した状態で置き換えます。
            // 次回起動時に同じbackupへ依存し続けないため、状態の確定後に再保存します。
            runCatching { persistState() }
                .onFailure { plugin.logger.warning("Could not rewrite recovered world migration state: ${it.message}") }
            stateRecoveredFromBackup = false
        }
        var interrupted = false
        states.values.filter { it.status == MigrationWorldStatus.RUNNING }.forEach {
            it.status = MigrationWorldStatus.RETRY
            it.phase = MigrationWorldPhase.MOVED
            it.attempts = 0
            it.lastError = "server_stopped_during_migration"
            it.updatedAt = Instant.now()
            interrupted = true
        }
        if (interrupted) {
            running = false
            currentWorld = null
            persistState()
        }
        // 前回終了後に対象が消えた（アーカイブ・削除等）未完エントリを起動時に確定させます。
        reconcileStaleStates(persist = true)
    }

    fun pendingWorlds(): List<LegacyWorldDirectory> = pendingDirectoryTargets()

    /**
     * 物理ディレクトリがどこにも存在しない(MISSING)未完エントリを、移行対象が実在しないと判断して
     * COMPLETEDへ昇格します。アーカイブ済み・削除済み・データ欠損のワールドが状態ファイルへ残骸として
     * 残り、hasPendingWork / hasMigrationFailure / isPending が恒久ブロックする自己ロックを解消します。
     *
     * 昇格しない状態:
     * - LEGACY / CURRENT: 移行すべき実在ディレクトリがある（再実行で解決）
     * - CONFLICT / UNSAFE: 手動修復が必要なため昇格で隠蔽しない
     * - メタデータ隔離中: metadata移行が先（解決後のチェックポイントで昇格される）
     *
     * persist=true は init / execute のように状態を確定できる場面だけに使い、
     * false は isPending 等の非同期経路から呼ばれる場面に使い、状態ファイル書込みの並行競合を避けます。
     */
    private fun reconcileStaleStates(persist: Boolean): Boolean {
        var changed = false
        states.values.forEach { state ->
            val directoryState = runCatching { resolver.inspect(state.folderName)?.state }.getOrNull()
            if (!isReconcilableStaleState(
                    state.status,
                    directoryState,
                    plugin.worldConfigRepository.isQuarantined(state.uuid),
                )
            ) return@forEach
            state.status = MigrationWorldStatus.COMPLETED
            state.phase = MigrationWorldPhase.VERIFIED
            state.attempts = 0
            state.lastError = null
            state.updatedAt = Instant.now()
            changed = true
        }
        if (persist && changed) persistState()
        return changed
    }

    fun isPending(uuid: UUID): Boolean =
        !evaluatePreflight(listOf(uuid), includeGlobal = false).allowed

    /**
     * 操作対象を一括評価します。対象単位の問題は指定UUIDにだけ返し、
     * グローバル問題は includeGlobal=true の場合だけ返します。
     */
    fun evaluatePreflight(
        worldUuids: Collection<UUID>,
        includeGlobal: Boolean,
        includeUnresolved: Boolean = false,
    ): ApiMigrationPreflight {
        val blocks = mutableListOf<ApiMigrationBlock>()
        // アーカイブ・削除等で対象が消えた未完エントリはここで昇格させ、isPending ゲートの
        // 自己ロック（移行失敗後にアーカイブ/削除できなくなる）を即時解除します。非同期経路のため永続化はしない。
        reconcileStaleStates(persist = false)
        if (running || metadataMigrationRunning) {
            blocks += ApiMigrationBlock(
                participantId = "mwm-migration-state",
                state = ApiMigrationParticipantState.PENDING,
                message = "world migration is already running",
            )
        }
        if (includeGlobal) {
            if (stateFileUnreadableReason != null) {
                blocks += ApiMigrationBlock(
                    participantId = "mwm-migration-state",
                    state = ApiMigrationParticipantState.FAILED,
                    message = stateFileUnreadableReason ?: "migration state is unreadable",
                )
            }
            if (plugin.templateRepository.quarantinedTemplates().isNotEmpty()) {
                blocks += ApiMigrationBlock(
                    participantId = "myworld-templates",
                    state = ApiMigrationParticipantState.PENDING,
                    message = "template data requires /mwm migration",
                )
            }
            MyWorldManagerApi.getMigrationParticipants()
                .filter { it.targetKind() == null }
                .forEach { participant ->
                    val status = runCatching { participant.status() }.getOrElse { error ->
                        ApiMigrationParticipantStatus(
                            participant.getId(),
                            ApiMigrationParticipantState.FAILED,
                            "status failed: ${error.message ?: error.javaClass.simpleName}",
                        )
                    }
                    if (status.state != ApiMigrationParticipantState.CURRENT) {
                        blocks += ApiMigrationBlock(
                            participantId = status.id,
                            state = status.state,
                            message = status.message ?: "participant requires /mwm migration",
                        )
                    }
                }
            if (includeUnresolved) {
                MyWorldManagerApi.getMigrationParticipants()
                    .filter { it.targetKind() != null }
                    .forEach { participant ->
                        val status = runCatching { participant.status() }.getOrElse { error ->
                            ApiMigrationParticipantStatus(
                                participant.getId(),
                                ApiMigrationParticipantState.FAILED,
                                "status failed: ${error.message ?: error.javaClass.simpleName}",
                            )
                        }
                        if (status.state != ApiMigrationParticipantState.CURRENT) {
                            blocks += ApiMigrationBlock(
                                participantId = status.id,
                                state = status.state,
                                message = status.message
                                    ?: "target-scoped data requires /mwm migration",
                            )
                        }
                    }
                val selected = worldUuids.toSet()
                plugin.worldConfigRepository.quarantinedWorlds()
                    .filter { it.uuid == null || it.uuid !in selected }
                    .forEach { data ->
                        blocks += ApiMigrationBlock(
                            participantId = "mwm-world-metadata",
                            state = ApiMigrationParticipantState.FAILED,
                            targetUuid = data.uuid,
                            message = "unresolved world metadata requires manual repair: ${data.fileName}",
                        )
                    }
                pendingDirectoryTargets()
                    .filter { it.uuid !in selected }
                    .forEach { candidate ->
                        blocks += ApiMigrationBlock(
                            participantId = "mwm-world-directory",
                            state = ApiMigrationParticipantState.PENDING,
                            targetUuid = candidate.uuid,
                            message = "unselected world directory requires /mwm migration: ${candidate.folderName}",
                        )
                    }
                resolver.findConflictingWorlds().forEach { candidate ->
                    if (candidate.uuid !in selected) {
                        blocks += ApiMigrationBlock(
                            participantId = "mwm-world-directory",
                            state = ApiMigrationParticipantState.FAILED,
                            targetUuid = candidate.uuid,
                            message = "conflicting world directories require manual repair: ${candidate.folderName}",
                        )
                    }
                }
                resolver.findUnresolvedWorldDirectories().forEach { folderName ->
                    blocks += ApiMigrationBlock(
                        participantId = "mwm-world-directory",
                        state = ApiMigrationParticipantState.FAILED,
                        message = "unresolved world directory requires manual repair: $folderName",
                    )
                }
            }
        }

        worldUuids.distinct().forEach { uuid ->
            states[uuid]
                ?.takeIf { it.status != MigrationWorldStatus.COMPLETED }
                ?.let { state ->
                    blocks += ApiMigrationBlock(
                        participantId = "mwm-world-directory",
                        state = if (state.status == MigrationWorldStatus.FAILED) {
                            ApiMigrationParticipantState.FAILED
                        } else {
                            ApiMigrationParticipantState.PENDING
                        },
                        targetUuid = uuid,
                        message = state.lastError
                            ?: "world migration is incomplete: ${state.folderName} (${state.status})",
                    )
                }
            pendingDirectoryTargets()
                .firstOrNull { it.uuid == uuid }
                ?.let { candidate ->
                    blocks += ApiMigrationBlock(
                        participantId = "mwm-world-directory",
                        state = ApiMigrationParticipantState.PENDING,
                        targetUuid = uuid,
                        message = "world directory requires /mwm migration: ${candidate.folderName}",
                    )
                }
            if (plugin.worldConfigRepository.isQuarantined(uuid)) {
                blocks += ApiMigrationBlock(
                    participantId = "mwm-world-metadata",
                    state = ApiMigrationParticipantState.FAILED,
                    targetUuid = uuid,
                    message = "world metadata is quarantined: $uuid",
                )
            }
            MyWorldManagerApi.getMigrationParticipants()
                .filter { it.targetKind() == ApiMigrationTargetKind.WORLD }
                .forEach { participant ->
                    val status = runCatching { participant.statusFor(uuid) }.getOrElse { error ->
                        ApiMigrationParticipantStatus(
                            participant.getId(),
                            ApiMigrationParticipantState.FAILED,
                            "status failed: ${error.message ?: error.javaClass.simpleName}",
                        )
                    } ?: ApiMigrationParticipantStatus(
                        participant.getId(),
                        ApiMigrationParticipantState.FAILED,
                        "target-scoped participant did not return a status for $uuid",
                    )
                    if (status.state != ApiMigrationParticipantState.CURRENT) {
                        blocks += ApiMigrationBlock(
                            participantId = status.id,
                            state = status.state,
                            targetUuid = uuid,
                            message = status.message ?: "world data requires /mwm migration: $uuid",
                        )
                    }
                }
        }
        return ApiMigrationPreflight(blocks.distinctBy { Triple(it.participantId, it.targetUuid, it.message) })
    }

    /**
     * 全参加者・全データセットを含む状態です。
     * バックアップ全体や年次アーカイブのように対象を限定できない操作は、
     * 一部だけの成功で状態を分断しないため、この判定を事前条件にします。
     */
    fun hasPendingWork(): Boolean =
        run {
            // 外部APIからも一貫した状態を返すため、ここでも残骸を昇格させます。
            reconcileStaleStates(persist = false)
            stateFileUnreadableReason != null ||
                pendingDirectoryTargets().isNotEmpty() ||
                hasMetadataRemaining() ||
                states.values.any { it.status != MigrationWorldStatus.COMPLETED }
        }

    /**
     * 集約ファイルとコアメタデータの保留状態です。
     * プレイヤー単位・ワールド単位の別対象の保留はここでは全体停止理由にしません。
     */
    fun hasGlobalPendingWork(): Boolean =
        !evaluatePreflight(emptyList(), includeGlobal = true).allowed

    fun reportPending(sender: CommandSender? = null): List<LegacyWorldDirectory> {
        val pending = pendingWorlds()
        pending.forEach { candidate ->
            send(
                sender,
                MyworldMessagesKeys.MESSAGES_MIGRATION_PENDING,
                mapOf("world" to candidate.folderName, "uuid" to candidate.uuid)
            )
        }
        plugin.worldConfigRepository.quarantinedWorlds().forEach { data ->
            send(
                sender,
                MyworldMessagesKeys.MESSAGES_MIGRATION_PENDING,
                mapOf(
                    "world" to "metadata:${data.fileName}",
                    "uuid" to (data.uuid?.toString() ?: "-"),
                )
            )
        }
        plugin.templateRepository.quarantinedTemplates().forEach { data ->
            send(
                sender,
                MyworldMessagesKeys.MESSAGES_MIGRATION_PENDING,
                mapOf("world" to "template:${data.id}", "uuid" to "-")
            )
        }
        MyWorldManagerApi.getMigrationParticipants()
            .filter { it.status().state != ApiMigrationParticipantState.CURRENT }
            .forEach { participant ->
                val status = participant.status()
                send(
                    sender,
                    MyworldMessagesKeys.MESSAGES_MIGRATION_PENDING,
                    mapOf(
                        "world" to "participant:${participant.getId()}",
                        "uuid" to (status.message ?: status.state.name),
                    ),
                )
            }
        resolver.findConflictingWorlds().forEach {
            send(sender, MyworldMessagesKeys.MESSAGES_MIGRATION_CONFLICT, mapOf("world" to it.folderName))
        }
        return pending
    }

    internal fun requestExecute(sender: CommandSender, force: Boolean = false, confirmed: Boolean = false) {
        if (running || metadataMigrationRunning) {
            send(sender, MyworldMessagesKeys.MESSAGES_MIGRATION_ALREADY_RUNNING)
            return
        }
        stateFileUnreadableReason?.let { reason ->
            plugin.logger.warning("World migration is blocked because its state file is unreadable: $reason")
            send(sender, MyworldMessagesKeys.MESSAGES_MIGRATION_INCOMPLETE)
            return
        }
        // 強制移行は確認状態をGUIへ持ち越さず、完全なコマンドの再入力だけを実行意図とします。
        if (force && !confirmed) {
            send(sender, MyworldMessagesKeys.MESSAGES_MIGRATION_FORCE_CONSOLE_CONFIRM)
            return
        }
        if (sender is Player && !confirmed) {
            openConfirmation(sender)
            return
        }
        if (!confirmed) {
        send(sender, if (force) MyworldMessagesKeys.MESSAGES_MIGRATION_FORCE_CONSOLE_CONFIRM else MyworldMessagesKeys.MESSAGES_MIGRATION_CONSOLE_CONFIRM)
            return
        }
        execute(sender, force)
    }

    fun status(sender: CommandSender) {
        // 表示前に残骸を昇格させ、実際の移行対象と状態を一致させます。
        reconcileStaleStates(persist = false)
        val snapshot = snapshot()
        val completed = snapshot.worlds.count { it.status == MigrationWorldStatus.COMPLETED }
        val waiting = snapshot.worlds.count {
            it.status == MigrationWorldStatus.WAITING || it.status == MigrationWorldStatus.RETRY
        }
        val retries = snapshot.worlds.count { it.status == MigrationWorldStatus.RETRY }
        val quarantinedWorlds = plugin.worldConfigRepository.quarantinedWorlds()
        val quarantinedTemplates = plugin.templateRepository.quarantinedTemplates()
        val participantStatuses = MyWorldManagerApi.getMigrationParticipants().map { it.status() }
        val failed = snapshot.worlds.count { it.status == MigrationWorldStatus.FAILED } +
            quarantinedWorlds.size + quarantinedTemplates.size +
            participantStatuses.count { it.state != ApiMigrationParticipantState.CURRENT } +
            stateFileUnreadableReason.countIfPresent()
        send(
            sender,
            MyworldMessagesKeys.MESSAGES_MIGRATION_STATUS_SUMMARY,
            mapOf(
                "state" to if (snapshot.running) "RUNNING" else "IDLE",
                "total" to snapshot.worlds.size + quarantinedWorlds.size + quarantinedTemplates.size +
                    participantStatuses.count { it.state != ApiMigrationParticipantState.CURRENT } +
                    stateFileUnreadableReason.countIfPresent(),
                "completed" to completed,
                "waiting" to waiting,
                "retries" to retries,
                "failed" to failed,
                "current" to (snapshot.currentWorld?.toString() ?: "-")
            )
        )
        snapshot.worlds.sortedBy { it.folderName }.forEach {
            val presentation = it.status.presentation()
            sendStatusLine(
                sender,
                identifier = it.uuid.toString(),
                color = presentation.color,
                shortCodeKey = presentation.shortCodeKey,
                hover = statusHover(
                    sender,
                    it.folderName,
                    it.status.name,
                    it.attempts,
                    it.lastError ?: "-",
                    it.updatedAt,
                ),
            )
        }
        quarantinedWorlds.forEach { data ->
            sendStatusLine(
                sender,
                identifier = "metadata:${data.fileName}",
                color = NamedTextColor.RED,
                shortCodeKey = MyworldMessagesKeys.MESSAGES_MIGRATION_STATUS_SHORT_QUARANTINED,
                hover = statusHover(
                    sender,
                    "metadata:${data.fileName}",
                    "QUARANTINED",
                    0,
                    data.reason,
                    data.detectedAt,
                ),
            )
        }
        quarantinedTemplates.forEach { data ->
            sendStatusLine(
                sender,
                identifier = "template:${data.id}",
                color = NamedTextColor.RED,
                shortCodeKey = MyworldMessagesKeys.MESSAGES_MIGRATION_STATUS_SHORT_QUARANTINED,
                hover = statusHover(
                    sender,
                    "template:${data.id}",
                    "QUARANTINED",
                    0,
                    data.reason,
                    data.detectedAt,
                ),
            )
        }
        MyWorldManagerApi.getMigrationParticipants()
            .filter { it.status().state != ApiMigrationParticipantState.CURRENT }
            .forEach { participant ->
                val participantStatus = participant.status()
                sendStatusLine(
                    sender,
                    identifier = "participant:${participant.getId()}",
                    color = NamedTextColor.RED,
                    shortCodeKey = MyworldMessagesKeys.MESSAGES_MIGRATION_STATUS_SHORT_QUARANTINED,
                    hover = statusHover(
                        sender,
                        "participant:${participant.getId()}",
                        "QUARANTINED",
                        0,
                        participantStatus.message ?: participantStatus.state.name,
                        Instant.now(),
                    ),
                )
            }
        stateFileUnreadableReason?.let { reason ->
            sendStatusLine(
                sender,
                identifier = "migration-state",
                color = NamedTextColor.RED,
                shortCodeKey = MyworldMessagesKeys.MESSAGES_MIGRATION_STATUS_SHORT_FAILED,
                hover = statusHover(
                    sender,
                    "migration-state",
                    "FAILED",
                    0,
                    reason,
                    Instant.now(),
                ),
            )
        }
    }

    fun snapshot(): MigrationStatusSnapshot =
        MigrationStatusSnapshot(running || metadataMigrationRunning, states.values.map(MigrationWorldState::copy), currentWorld)

    private fun execute(sender: CommandSender, force: Boolean) {
        if (stateFileUnreadableReason != null) {
            send(sender, MyworldMessagesKeys.MESSAGES_MIGRATION_INCOMPLETE)
            return
        }
        // 実行前に残骸を昇格・永続化し、以降の hasPendingWork / hasMigrationFailure 判定を最新化します。
        reconcileStaleStates(persist = true)
        val metadataCount = metadataTargetCount()
        val metadataResults = if (metadataCount > 0) {
            metadataMigrationRunning = true
            try {
                migrateMetadata(sender, force)
            } finally {
                metadataMigrationRunning = false
            }
        } else {
            emptyList()
        }
        val candidates = pendingDirectoryTargets()
        if (candidates.isEmpty() && metadataCount == 0) {
            send(sender, if (hasMigrationFailure()) MyworldMessagesKeys.MESSAGES_MIGRATION_INCOMPLETE else MyworldMessagesKeys.MESSAGES_MIGRATION_NONE_PENDING)
            return
        }
        if (candidates.isEmpty()) {
            send(sender, MyworldMessagesKeys.MESSAGES_MIGRATION_STARTED, mapOf("count" to metadataCount))
            if (hasMigrationFailure() || metadataResults.any { it.state == ApiMigrationParticipantResultState.FAILED }) {
                send(sender, MyworldMessagesKeys.MESSAGES_MIGRATION_INCOMPLETE)
            } else {
                // 全移行が成功した時点で、過去の移行バックアップ残骸も一括整理する。
                cleanupMigrationBackups()
                send(sender, MyworldMessagesKeys.MESSAGES_MIGRATION_COMPLETED)
            }
            return
        }
        candidates.forEach { candidate ->
            val existing = states[candidate.uuid]
            states[candidate.uuid] = existing?.apply {
                folderName = candidate.folderName
                if (status == MigrationWorldStatus.COMPLETED || status == MigrationWorldStatus.FAILED) {
                    status = MigrationWorldStatus.RETRY
                    phase = MigrationWorldPhase.QUEUED
                    attempts = 0
                }
                updatedAt = Instant.now()
            } ?: MigrationWorldState(
                candidate.uuid,
                candidate.folderName,
                MigrationWorldStatus.WAITING
            )
        }
        persistState()
        running = true
        send(sender, MyworldMessagesKeys.MESSAGES_MIGRATION_STARTED, mapOf("count" to candidates.size + metadataCount))
        scheduleNext()
    }

    private fun metadataTargetCount(): Int =
        plugin.worldConfigRepository.quarantinedWorlds().size +
            plugin.templateRepository.quarantinedTemplates().size +
            MyWorldManagerApi.getMigrationParticipants().count { it.status().state != ApiMigrationParticipantState.CURRENT }

    /**
     * 旧ディレクトリだけでなく、移動直後に停止したため現行ディレクトリだけが残った対象も再開対象にします。
     * 状態ファイルに記録されたフォルダー名と実ディレクトリを突き合わせ、完了前の対象を再検証します。
     */
    private fun pendingDirectoryTargets(): List<LegacyWorldDirectory> {
        val legacy = resolver.findLegacyWorlds()
        val known = legacy.mapTo(mutableSetOf()) { it.uuid }
        val recovered = states.values
            .filter { it.status != MigrationWorldStatus.COMPLETED && it.uuid !in known }
            .mapNotNull { state ->
                val resolution = resolver.inspect(state.folderName) ?: return@mapNotNull null
                if (resolution.state == WorldDirectoryState.CURRENT && resolution.currentPath != null) {
                    LegacyWorldDirectory(state.uuid, state.folderName, resolution.currentPath)
                } else {
                    null
                }
            }
        return (legacy + recovered).distinctBy { it.uuid }.sortedBy { it.folderName }
    }

    private fun hasMetadataRemaining(): Boolean =
        plugin.worldConfigRepository.quarantinedWorlds().isNotEmpty() ||
            plugin.templateRepository.quarantinedTemplates().isNotEmpty() ||
            MyWorldManagerApi.getMigrationParticipants().any {
                it.status().state != ApiMigrationParticipantState.CURRENT
            }

    private fun hasMigrationFailure(): Boolean =
        states.values.any { it.status == MigrationWorldStatus.FAILED } ||
            hasMetadataRemaining() ||
            stateFileUnreadableReason != null

    /**
     * 移行が全て成功した時点で、過去の移行バックアップ（*.pre-migration-*.bak）を一括削除する。
     * 失敗分はリストア用に残すため、失敗が残っている間は実行しない。
     * 対象は自プラグインのデータ領域（直下・my_worlds・playerdata）に限定し、
     * 他プラグイン由来の残骸（*.bak-* 等）やロールバック用途の
     * paper-world.yml.pre-mwm-end-policy.bak には触れない。
     */
    private fun cleanupMigrationBackups() {
        val directories = buildList {
            add(plugin.dataFolder)
            File(plugin.dataFolder, "my_worlds").takeIf { it.isDirectory }?.let(::add)
            File(plugin.dataFolder, "playerdata").takeIf { it.isDirectory }?.let(::add)
        }
        var removed = 0
        for (directory in directories) {
            directory.listFiles { file ->
                file.isFile && file.name.contains(".pre-migration-") && file.name.endsWith(".bak")
            }?.forEach { file ->
                if (file.delete()) removed++
            }
        }
        if (removed > 0) {
            plugin.logger.info("移行バックアップを $removed 件削除しました（移行完了後の整理）")
        }
    }

    private fun migrateMetadata(sender: CommandSender, force: Boolean) = buildList {
        plugin.worldConfigRepository.quarantinedWorlds()
            .groupBy { it.uuid }
            .forEach { (uuid, candidates) ->
                if (uuid == null) {
                    plugin.logger.warning(
                        "Metadata migration [world-file:${candidates.joinToString { it.fileName }}] FAILED: " +
                            "world UUID is missing; manual repair is required",
                    )
                    return@forEach
                }
                if (candidates.size > 1) {
                    plugin.logger.warning(
                        "Metadata migration [world:$uuid] FAILED: multiple quarantined files " +
                            candidates.joinToString { it.fileName },
                    )
                    return@forEach
                }
                val data = candidates.single()
                val inferred = inferDimension(data)
                val dimension = inferred ?: ManagedDimension.OVERWORLD.takeIf { force }
                val lease = MyWorldManagerApi.tryAcquireWorldOperation(uuid, WorldOperation.MIGRATE)
                if (lease == null) {
                    logMetadataResult(
                        "world:$uuid",
                        MetadataMigrationResult(MetadataMigrationStatus.FAILED, "world_operation_locked"),
                    )
                    return@forEach
                }
                try {
                    val result = plugin.worldConfigRepository.migrateWorldData(uuid, dimension)
                    logMetadataResult("world:$uuid", result)
                    if (force && inferred == null && result.status == MetadataMigrationStatus.MIGRATED) {
                        send(sender, MyworldMessagesKeys.MESSAGES_MIGRATION_FORCE_DEFAULT_APPLIED, mapOf("target" to "world:$uuid"))
                    }
                } finally {
                    lease.close()
                }
        }
        val templateTargets = plugin.templateRepository.quarantinedTemplates()
        templateTargets.firstOrNull { it.id == "<file>" }?.let { data ->
            val inferred = inferDimension(data)
            val dimension = inferred ?: ManagedDimension.OVERWORLD.takeIf { force }
            val result = plugin.templateRepository.migrateTemplate(data.id, dimension)
            logMetadataResult("template:${data.id}", result)
            if (force && inferred == null && result.status == MetadataMigrationStatus.MIGRATED) {
                send(sender, MyworldMessagesKeys.MESSAGES_MIGRATION_FORCE_DEFAULT_APPLIED, mapOf("target" to "templates.yml"))
            }
            reloadMetadataRepositories()
        }
        if (plugin.templateRepository.quarantinedTemplates().none { it.id == "<file>" }) {
            plugin.templateRepository.quarantinedTemplates().forEach { data ->
                val inferred = inferDimension(data)
                val dimension = inferred ?: ManagedDimension.OVERWORLD.takeIf { force }
                val result = plugin.templateRepository.migrateTemplate(data.id, dimension)
                logMetadataResult("template:${data.id}", result)
                if (force && inferred == null && result.status == MetadataMigrationStatus.MIGRATED) {
                    send(sender, MyworldMessagesKeys.MESSAGES_MIGRATION_FORCE_DEFAULT_APPLIED, mapOf("target" to "template:${data.id}"))
                }
            }
        }
        MyWorldManagerApi.getMigrationParticipants().forEach { participant ->
            val status = participant.status()
            if (status.state == ApiMigrationParticipantState.CURRENT) return@forEach
            val result = runCatching {
                MyWorldManagerApi.executeMigrationParticipant(participant.getId())
                    ?: ApiMigrationParticipantResult(
                        ApiMigrationParticipantResultState.FAILED,
                        "no migration executor is registered for ${participant.getId()}",
                    )
            }.getOrElse { error ->
                ApiMigrationParticipantResult(
                    ApiMigrationParticipantResultState.FAILED,
                    "migration executor failed for ${participant.getId()}: ${error.message ?: error.javaClass.simpleName}",
                )
            }
            add(result)
            logMetadataResult("participant:${participant.getId()}", result)
        }
        reloadMetadataRepositories()
    }

    private fun reloadMetadataRepositories() {
        plugin.worldConfigRepository.loadAll()
        plugin.templateRepository.loadTemplates()
    }

    private fun inferDimension(data: QuarantinedWorldData): ManagedDimension? {
        val loaded = data.worldKey
            ?.let(NamespacedKey::fromString)
            ?.let { Bukkit.getWorld(it) }
            ?: data.customWorldName?.let { Bukkit.getWorld(it) }
        loaded?.let { world ->
            runCatching { ManagedDimension.fromBukkit(world.environment) }.getOrNull()?.let { return it }
        }

        // 旧データの生成元テンプレートが現行定義に残っていれば、その明示dimensionを根拠にできます。
        val templateId = data.sourceWorld
            ?.takeIf { it.startsWith("template:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.takeIf { it.isNotBlank() && !it.equals("none", ignoreCase = true) }
        return templateId?.let(plugin.templateRepository::findById)?.dimension
    }

    private fun inferDimension(data: QuarantinedTemplateData): ManagedDimension? {
        val loaded = data.path
            ?.let(NamespacedKey::fromString)
            ?.let { Bukkit.getWorld(it) }
            ?: data.path?.let { Bukkit.getWorld(it) }
        return loaded?.let { runCatching { ManagedDimension.fromBukkit(it.environment) }.getOrNull() }
    }

    private fun logMetadataResult(target: String, result: MetadataMigrationResult) {
        val level = if (result.status == MetadataMigrationStatus.FAILED) Level.WARNING else Level.INFO
        plugin.logger.log(level, "Metadata migration [$target] ${result.status}: ${result.message}")
    }

    private fun logMetadataResult(
        target: String,
        result: me.awabi2048.myworldmanager.api.service.ApiMigrationParticipantResult,
    ) {
        val level = if (result.state == ApiMigrationParticipantResultState.FAILED) Level.WARNING else Level.INFO
        plugin.logger.log(level, "Metadata migration [$target] ${result.state}: ${result.message}")
    }

    private fun sendMetadataResult(
        sender: CommandSender,
        targetKind: String,
        identifier: String,
        result: MetadataMigrationResult,
    ) {
        val key = when (result.status) {
            MetadataMigrationStatus.MIGRATED,
            MetadataMigrationStatus.ALREADY_CURRENT -> MyworldMessagesKeys.MESSAGES_MIGRATION_METADATA_UPDATED
            MetadataMigrationStatus.UNRESOLVED -> MyworldMessagesKeys.MESSAGES_MIGRATION_METADATA_PENDING
            MetadataMigrationStatus.FAILED -> MyworldMessagesKeys.MESSAGES_MIGRATION_METADATA_FAILED
        }
        send(
            sender,
            key,
            mapOf("target" to "$targetKind:$identifier", "reason" to result.message),
        )
    }

    private fun scheduleNext() {
        Bukkit.getScheduler().runTask(plugin, Runnable { processNext() })
    }

    private fun processNext() {
        val next = states.values
            .filter { it.status == MigrationWorldStatus.WAITING || it.status == MigrationWorldStatus.RETRY }
            .sortedWith(compareBy<MigrationWorldState> { it.attempts }.thenBy { it.folderName })
            .firstOrNull()
        if (next == null) {
            running = false
            currentWorld = null
            persistState()
            val completionKey = if (hasMigrationFailure()) {
                MyworldMessagesKeys.MESSAGES_MIGRATION_INCOMPLETE
            } else {
                // 全移行が成功した時点で、過去の移行バックアップ残骸も一括整理する。
                cleanupMigrationBackups()
                MyworldMessagesKeys.MESSAGES_MIGRATION_COMPLETED
            }
            plugin.server.consoleSender.sendMessage(plugin.languageManager.getMessage(completionKey))
            return
        }

        currentWorld = next.uuid
        next.status = MigrationWorldStatus.RUNNING
        next.attempts++
        next.updatedAt = Instant.now()
        persistState()
        val started = System.nanoTime()
        val result = runCatching { migrateOne(next) }
        result.onSuccess {
            next.status = MigrationWorldStatus.COMPLETED
            next.lastError = null
        }.onFailure { error ->
            next.lastError = error.message ?: error.javaClass.simpleName
            next.status = if (next.attempts >= MAX_ATTEMPTS) {
                MigrationWorldStatus.FAILED
            } else {
                MigrationWorldStatus.RETRY
            }
            plugin.logger.log(
                if (next.status == MigrationWorldStatus.FAILED) Level.SEVERE else Level.WARNING,
                "World migration failed (${next.attempts}/$MAX_ATTEMPTS): ${next.folderName}",
                error
            )
        }
        next.updatedAt = Instant.now()
        currentWorld = null
        persistState()
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000
        if (elapsedMillis > YIELD_THRESHOLD_MILLIS) {
            plugin.logger.info("World migration stage took ${elapsedMillis}ms; yielding before the next world.")
        }
        scheduleNext()
    }

    private fun migrateOne(state: MigrationWorldState) {
        val lease = MyWorldManagerApi.tryAcquireWorldOperation(state.uuid, WorldOperation.MIGRATE)
            ?: error("world_operation_locked")
        try {
            val worldData = plugin.worldConfigRepository.findByUuid(state.uuid)
                ?: error("world_data_missing")
            // アーカイブ済みワールドは archived_worlds 配下にあり移行対象になり得ません。
            // 現行のスキャンでは候補に入らないため到達不能だが、将来のスキャン拡張で
            // 誤って移行・ロードされないよう防御します。
            if (worldData.isArchived) error("world_archived")
            val resolution = resolver.inspect(state.folderName)
                ?: error("unsafe_world_directory")
            val movedByThisAttempt = resolution.state == WorldDirectoryState.LEGACY
            val source = resolution.legacyPath
            val target = resolution.currentPath ?: error("current_directory_missing")
            if (movedByThisAttempt) {
                state.phase = MigrationWorldPhase.MOVE_STARTED
                state.updatedAt = Instant.now()
                persistState()
                target.parent?.let(Files::createDirectories)
                moveDirectory(source ?: error("legacy_directory_missing"), target)
                state.phase = MigrationWorldPhase.MOVED
                state.updatedAt = Instant.now()
                persistState()
            } else if (resolution.state != WorldDirectoryState.CURRENT) {
                error("unexpected_directory_state:${resolution.state}")
            }
            val existingWorld = Bukkit.getWorld(NamespacedKey.minecraft(state.folderName))
            var committed = false
            try {
                val world = existingWorld
                    ?: plugin.server.createWorld(
                        plugin.managedWorldCreatorFactory.create(
                            NamespacedKey.minecraft(state.folderName),
                            worldData.dimension
                        )
                    )
                    ?: error("world_load_failed")
                plugin.managedWorldCreatorFactory.requireMatchingDimension(world, worldData.dimension)
                plugin.worldEnvironmentService.applyAll(world, worldData)
                committed = true
                state.phase = MigrationWorldPhase.VERIFIED
                state.updatedAt = Instant.now()
                persistState()
            } finally {
                if (!committed) {
                    if (existingWorld == null) {
                        Bukkit.getWorld(NamespacedKey.minecraft(state.folderName))?.let {
                            plugin.server.unloadWorld(it, false)
                        }
                    }
                    if (movedByThisAttempt && source != null && Files.exists(target) && !Files.exists(source)) {
                        moveDirectory(target, source)
                    }
                }
            }
        } finally {
            lease.close()
        }
    }

    private fun moveDirectory(source: java.nio.file.Path, target: java.nio.file.Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target)
        }
    }

    private fun openConfirmation(player: Player) {
        runtime.navigate(player, MenuRoute(OWNER, ROUTE_ID))
    }

    private fun renderConfirmation(player: Player): InventoryMenuView {
        val layout = GuiHelper.confirmationLayout()
        return InventoryMenuView(
            size = layout.size,
            title = GuiHelper.inventoryTitle(
                plugin.languageManager.getComponent(player, MyworldGuiAdminKeys.GUI_MIGRATION_CONFIRM_TITLE),
            ),
            elements = listOf(
                CCSystem.getAPI().getGuiElementService().menuDisplay(
                    GuiMenuDisplaySpec(
                        layout.previewSlot,
                        GuiItemSpec(
                            Material.COMPASS,
                            GuiNameSpec.FixedLabel(plugin.languageManager.getComponent(player, MyworldGuiAdminKeys.GUI_MIGRATION_CONFIRM_SUMMARY)),
                            GuiLoreSpec.None,
                            GuiElementRole.CONTENT,
                            1,
                        ),
                    ),
                ),
                actionEntry(player, layout.confirmSlot, Material.LIME_CONCRETE, MyworldGuiAdminKeys.GUI_MIGRATION_CONFIRM_EXECUTE, GuiElementRole.CONFIRM, ACTION_EXECUTE),
                actionEntry(player, layout.cancelSlot, Material.RED_CONCRETE, MyworldGuiAdminKeys.GUI_MIGRATION_CONFIRM_CANCEL, GuiElementRole.CANCEL, ACTION_CANCEL),
            ),
            category = MenuViewCategory.CONFIRMATION,
        )
    }

    @Synchronized
    private fun loadState() {
        stateFileUnreadableReason = null
        stateRecoveredFromBackup = false
        val backup = File(stateFile.parentFile, "${stateFile.name}.bak")
        val primaryResult = if (stateFile.exists()) {
            runCatching { readStateStrict(stateFile) }
        } else {
            Result.failure(IllegalStateException("primary state file is missing"))
        }
        val yaml = primaryResult.getOrNull() ?: run {
            if (!backup.exists()) {
                if (stateFile.exists()) {
                    val error = primaryResult.exceptionOrNull()
                    stateFileUnreadableReason = "state file: ${error?.message ?: error?.javaClass?.simpleName}"
                    plugin.logger.severe(
                        "World migration is blocked because its state file is unreadable: $stateFileUnreadableReason",
                    )
                }
                return
            }
            runCatching { readStateStrict(backup) }
                .onSuccess {
                    stateRecoveredFromBackup = true
                    plugin.logger.warning(
                        "World migration state file was invalid; using the verified backup: ${backup.name}",
                    )
                }
                .getOrElse { backupError ->
                    val primaryError = primaryResult.exceptionOrNull()
                    stateFileUnreadableReason =
                        "state file: ${primaryError?.message ?: primaryError?.javaClass?.simpleName}; " +
                            "backup: ${backupError.message ?: backupError.javaClass.simpleName}"
                    plugin.logger.severe(
                        "World migration is blocked because both state files are unreadable: $stateFileUnreadableReason",
                    )
                    return
                }
        }
        val parsedStates = parseStates(yaml)
        states.clear()
        states.putAll(parsedStates)
    }

    /** YAMLとして読めるだけでは復旧に使わず、状態レコード全体を検証してから採用します。 */
    private fun readStateStrict(file: File): YamlConfiguration {
        require(file.isFile) { "state file is not a regular file: ${file.name}" }
        return YamlConfiguration().also { it.load(file) }.also { parseStates(it) }
    }

    private fun parseStates(yaml: YamlConfiguration): Map<UUID, MigrationWorldState> {
        require(yaml.contains("running")) { "running is missing" }
        yaml.get("running")?.let { require(it is Boolean) { "running must be boolean" } }
        yaml.get("current_world")?.let { raw ->
            require(raw is String && runCatching { UUID.fromString(raw) }.isSuccess) {
                "current_world must be a UUID string"
            }
        }
        val rawWorlds = yaml.get("worlds")
        if (rawWorlds == null) return emptyMap()
        val section = yaml.getConfigurationSection("worlds")
            ?: error("worlds must be a section")
        val parsed = linkedMapOf<UUID, MigrationWorldState>()
        section.getKeys(false).forEach { raw ->
            val uuid = runCatching { UUID.fromString(raw) }
                .getOrElse { error("worlds.$raw is not a UUID") }
            val base = "worlds.$raw"
            val folderName = yaml.getString("$base.folder_name")
                ?.takeIf { it.isNotBlank() && it != "." && it != ".." && !it.contains('/') && !it.contains('\\') }
                ?: error("$base.folder_name is missing or unsafe")
            val status = runCatching {
                MigrationWorldStatus.valueOf(yaml.getString("$base.status").orEmpty())
            }.getOrElse { error("$base.status is invalid") }
            // 旧版の状態ファイルにはphaseがないため、未移動のキューとして安全に復元します。
            val phase = yaml.getString("$base.phase")?.let { rawPhase ->
                runCatching { MigrationWorldPhase.valueOf(rawPhase) }
                    .getOrElse { error("$base.phase is invalid") }
            } ?: MigrationWorldPhase.QUEUED
            val attemptsRaw = yaml.get("$base.attempts")
            val attempts = if (attemptsRaw == null) {
                0
            } else {
                require(attemptsRaw is Number && attemptsRaw.toDouble() >= 0.0 && attemptsRaw.toDouble() % 1.0 == 0.0) {
                    "$base.attempts must be a non-negative integer"
                }
                attemptsRaw.toInt()
            }
            val updatedAt = yaml.getString("$base.updated_at")?.let { rawUpdatedAt ->
                runCatching { Instant.parse(rawUpdatedAt) }
                    .getOrElse { error("$base.updated_at is invalid") }
            } ?: Instant.EPOCH
            parsed[uuid] = MigrationWorldState(
                uuid = uuid,
                folderName = folderName,
                status = status,
                phase = phase,
                attempts = attempts,
                lastError = yaml.getString("$base.last_error"),
                updatedAt = updatedAt,
            )
        }
        return parsed
    }

    @Synchronized
    private fun persistState() {
        val yaml = YamlConfiguration()
        yaml.set("running", running)
        yaml.set("current_world", currentWorld?.toString())
        states.values.sortedBy { it.folderName }.forEach {
            val base = "worlds.${it.uuid}"
            yaml.set("$base.folder_name", it.folderName)
            yaml.set("$base.status", it.status.name)
            yaml.set("$base.phase", it.phase.name)
            yaml.set("$base.attempts", it.attempts)
            yaml.set("$base.last_error", it.lastError)
            yaml.set("$base.updated_at", it.updatedAt.toString())
        }
        stateFile.parentFile.mkdirs()
        val temporary = File(stateFile.parentFile, "${stateFile.name}.tmp")
        try {
            yaml.save(temporary)
            Files.move(
                temporary.toPath(),
                stateFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), stateFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary.toPath())
        }
        // 現行ファイルを原子的に確定した後で検証済みバックアップを更新します。
        val backup = File(stateFile.parentFile, "${stateFile.name}.bak")
        runCatching {
            Files.copy(stateFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }.onFailure {
            plugin.logger.warning("Could not refresh migration state backup: ${it.message}")
        }
    }

    private fun actionEntry(
        player: Player,
        slot: Int,
        material: Material,
        key: LocalizationKey<String>,
        role: GuiElementRole,
        actionId: String,
    ): MenuElement = CCSystem.getAPI().getGuiElementService().menuEntry(
        player,
        GuiMenuEntrySpec(
            slot = slot,
            material = material,
            name = GuiNameSpec.FixedLabel(plugin.languageManager.getComponent(player, key)),
            role = role,
            actions = listOf(
                menuGestureAction(
                    actionId,
                    MenuGesture.ANY,
                    plugin.languageManager.getMessage(player, key),
                    safety = when (actionId) {
                        ACTION_EXECUTE -> MenuActionSafety.IRREVERSIBLE
                        ACTION_CANCEL -> MenuActionSafety.NAVIGATION_ONLY
                        else -> error("Unknown migration action safety: $actionId")
                    },
                ),
            ),
        ),
    )

    private fun send(
        sender: CommandSender?,
        key: LocalizationKey<String>,
        placeholders: Map<String, Any> = emptyMap()
    ) {
        val message = plugin.languageManager.getMessage(key, placeholders)
        (sender ?: plugin.server.consoleSender).sendMessage(message)
    }

    /**
     * statusの1行を <状態色>● <識別子> <短縮状態コード> の圧縮形式で描画します。
     * ワールド名・状態・試行回数・最終エラー・更新時刻などの詳細はホバーテキストへ分離し、
     * 対象が多くても1行ごとに状態を俯瞰できるようにします。
     * ● の色と短縮コードは状態ごとに決まり、区切りや配色はここで組み立てます（言語ファイルには状態ラベルだけを持つ）。
     */
    private fun sendStatusLine(
        sender: CommandSender,
        identifier: String,
        color: NamedTextColor,
        shortCodeKey: LocalizationKey<String>,
        hover: Component,
    ) {
        val shortCode = plugin.languageManager.getMessage(sender as? Player, shortCodeKey)
        val line = Component.empty()
            .append(Component.text("●", color))
            .append(Component.text(" $identifier", NamedTextColor.GRAY))
            .append(Component.text(" $shortCode"))
            .hoverEvent(HoverEvent.showText(hover))
        sender.sendMessage(line)
    }

    /** status行のホバーテキスト。旧形式の詳細行を意味データとして再構成します。 */
    private fun statusHover(
        sender: CommandSender,
        folderName: String,
        status: String,
        attempts: Int,
        error: String,
        updated: Instant,
    ): Component = plugin.languageManager.getComponent(
        sender as? Player,
        MyworldMessagesKeys.MESSAGES_MIGRATION_STATUS_WORLD,
        mapOf(
            "world" to folderName,
            "status" to status,
            "attempts" to attempts,
            "error" to error,
            "updated" to updated,
        ),
    )

    private companion object {
        private const val OWNER = "myworldmanager"
        private const val ROUTE_ID = "world_migration_confirmation"
        private const val ACTION_EXECUTE = "execute"
        private const val ACTION_CANCEL = "cancel"
        private const val MAX_ATTEMPTS = 2
        private const val YIELD_THRESHOLD_MILLIS = 1_000L
    }
}

private fun String?.countIfPresent(): Int = if (this == null) 0 else 1

/**
 * 残骸昇格の可否判定。外部依存（ディレクトリ診断・隔離判定）を引数で渡して純粋に保ち、
 * 状態別の昇格挙動を単体テストで固定できるようにします。
 * - 移行状態が COMPLETED / RUNNING でない
 * - 物理ディレクトリが MISSING（実在せず、CONFLICT / UNSAFE / LEGACY / CURRENT ではない）
 * - メタデータが隔離されていない（metadata移行が先）
 * のすべてを満たす未完エントリだけを昇格対象とします。
 */
internal fun isReconcilableStaleState(
    status: MigrationWorldStatus,
    directoryState: WorldDirectoryState?,
    quarantined: Boolean,
): Boolean = status != MigrationWorldStatus.COMPLETED &&
    status != MigrationWorldStatus.RUNNING &&
    directoryState == WorldDirectoryState.MISSING &&
    !quarantined
