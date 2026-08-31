package me.awabi2048.myworldmanager.api

import com.awabi2048.ccsystem.CCSystem
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.extension.CommandPolicy
import me.awabi2048.myworldmanager.api.extension.CreateCommandHandler
import me.awabi2048.myworldmanager.api.extension.DefaultWorldAccessPolicy
import me.awabi2048.myworldmanager.api.extension.DefaultWorldPublishPolicy
import me.awabi2048.myworldmanager.api.extension.DefaultWorldPortalPolicy
import me.awabi2048.myworldmanager.api.extension.DefaultWorldRuntimePolicy
import me.awabi2048.myworldmanager.api.extension.WorldAccessPolicy
import me.awabi2048.myworldmanager.api.extension.WorldCreationDecision
import me.awabi2048.myworldmanager.api.extension.WorldCreationGuard
import me.awabi2048.myworldmanager.api.extension.WorldCreationRequest
import me.awabi2048.myworldmanager.api.extension.WorldDeleteGuard
import me.awabi2048.myworldmanager.api.extension.WorldEvacuationProvider
import me.awabi2048.myworldmanager.api.extension.WorldMenuAccessPolicy
import me.awabi2048.myworldmanager.api.extension.WorldMenuAccessContext
import me.awabi2048.myworldmanager.api.extension.WorldMenuAccessChallenge
import me.awabi2048.myworldmanager.api.extension.WorldSettingsStatePolicy
import me.awabi2048.myworldmanager.api.extension.WorldSettingsNavigationRequest
import me.awabi2048.myworldmanager.api.extension.WorldPublishPolicy
import me.awabi2048.myworldmanager.api.extension.WorldPortalPolicy
import me.awabi2048.myworldmanager.api.extension.WorldRuntimePolicy
import me.awabi2048.myworldmanager.api.extension.WorldPlayerStateDecision
import me.awabi2048.myworldmanager.api.extension.WorldPlayerStatePolicy
import me.awabi2048.myworldmanager.api.extension.WorldWorkPermissionPolicy
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.api.service.ApiMemberManager
import me.awabi2048.myworldmanager.api.service.ApiBedrockFormService
import me.awabi2048.myworldmanager.api.service.ApiMacroService
import me.awabi2048.myworldmanager.api.service.ApiMigrationParticipant
import me.awabi2048.myworldmanager.api.service.ApiMigrationParticipantResult
import me.awabi2048.myworldmanager.api.service.ApiMigrationPreflight
import me.awabi2048.myworldmanager.api.service.ApiWorldRepository
import me.awabi2048.myworldmanager.api.extension.ApiWorldListMenuService
import me.awabi2048.myworldmanager.api.extension.DiscoveryRouteCapability
import me.awabi2048.myworldmanager.api.extension.DiscoveryRouteRequest
import me.awabi2048.myworldmanager.api.extension.FavoriteListRouteCapability
import me.awabi2048.myworldmanager.api.extension.FavoriteListRouteRequest
import me.awabi2048.myworldmanager.api.service.ApiTemplateRepository
import me.awabi2048.myworldmanager.api.service.ApiWorldEnvironmentService
import me.awabi2048.myworldmanager.api.service.ApiWorldService
import me.awabi2048.myworldmanager.api.service.ApiWorldTagService
import me.awabi2048.myworldmanager.api.extension.PendingOfflineMemberInvite
import me.awabi2048.myworldmanager.api.service.WorldOperation
import me.awabi2048.myworldmanager.api.service.WorldOperationLease
import me.awabi2048.myworldmanager.api.service.WorldOperationLocks
import me.awabi2048.myworldmanager.api.service.WorldPointBillingMode
import java.util.UUID
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import me.awabi2048.myworldmanager.session.SettingsAction

object MyWorldManagerApi {
    private val logoutRelocations = java.util.concurrent.ConcurrentHashMap<UUID, String>()

    fun isLogoutRelocation(player: Player): Boolean =
        logoutRelocations.containsKey(player.uniqueId)

    fun getLogoutRelocationOrigin(player: Player): String? =
        logoutRelocations[player.uniqueId]

    internal fun beginLogoutRelocation(player: Player, plugin: Plugin, originWorldName: String) {
        val playerUuid = player.uniqueId
        logoutRelocations[playerUuid] = originWorldName
        plugin.server.scheduler.runTask(plugin, Runnable {
            logoutRelocations.remove(playerUuid)
        })
    }


    @JvmStatic
    fun tryAcquireWorldOperation(worldUuid: UUID, operation: WorldOperation): WorldOperationLease? =
        WorldOperationLocks.tryAcquire(worldUuid, operation)

    @JvmStatic
    fun isWorldOperationLeaseActive(lease: WorldOperationLease): Boolean =
        WorldOperationLocks.isActive(lease)

    @JvmStatic
    fun getActiveWorldOperation(worldUuid: UUID): WorldOperation? =
        WorldOperationLocks.current(worldUuid)

    @JvmStatic
    fun clearWorldOperationLocks() = WorldOperationLocks.clear()

    private var worldPointService: WorldPointService? = null
    private var worldService: ApiWorldService? = null
    private var worldEnvironmentService: ApiWorldEnvironmentService? = null
    private var worldRepository: ApiWorldRepository? = null
    private var templateRepository: ApiTemplateRepository? = null
    private var memberManager: ApiMemberManager? = null
    private var worldTagService: ApiWorldTagService? = null
    private val worldCreationGuards = CopyOnWriteArrayList<WorldCreationGuard>()
    private val worldPlayerStatePolicies = CopyOnWriteArrayList<WorldPlayerStatePolicy>()
    private val worldSettingsStatePolicies =
        CopyOnWriteArrayList<WorldSettingsStatePolicy>()
    private val worldSettingsRouteCapabilities =
        CopyOnWriteArrayList<me.awabi2048.myworldmanager.api.extension.WorldSettingsRouteCapability>()
    private val playerWorldRouteCapabilities =
        CopyOnWriteArrayList<me.awabi2048.myworldmanager.api.extension.PlayerWorldRouteCapability>()
    private val discoveryRouteCapabilities =
        CopyOnWriteArrayList<DiscoveryRouteCapability>()
    private val favoriteListRouteCapabilities =
        CopyOnWriteArrayList<FavoriteListRouteCapability>()
    private val worldDeleteGuards = CopyOnWriteArrayList<WorldDeleteGuard>()
    private val worldAccessPolicies = CopyOnWriteArrayList<WorldAccessPolicy>()
    private val commandPolicies = CopyOnWriteArrayList<CommandPolicy>()
    private val createCommandHandlers = CopyOnWriteArrayList<CreateCommandHandler>()
    private val worldRuntimePolicies = CopyOnWriteArrayList<WorldRuntimePolicy>()
    private val worldPublishPolicies = CopyOnWriteArrayList<WorldPublishPolicy>()
    private val worldPortalPolicies = CopyOnWriteArrayList<WorldPortalPolicy>()
    private val worldEvacuationProviders = CopyOnWriteArrayList<WorldEvacuationProvider>()
    private val worldMenuAccessPolicies = CopyOnWriteArrayList<WorldMenuAccessPolicy>()
    private val worldWorkPermissionPolicies = CopyOnWriteArrayList<WorldWorkPermissionPolicy>()
    private var worldWorkPermissionSyncService: WorldWorkPermissionSyncService? = null
    private var bedrockFormService: ApiBedrockFormService? = null
    private val migrationParticipants = CopyOnWriteArrayList<ApiMigrationParticipant>()
    /**
     * 移行の書き込み処理はステータス公開用の participant と分離して保持します。
     * この map は MWM 内部からしか実行できないため、外部プラグインが participant を取得して
     * 任意のタイミングで移行書き込みを開始する経路を作りません。
     */
    private val migrationExecutors = java.util.concurrent.ConcurrentHashMap<String, () -> ApiMigrationParticipantResult>()

    @JvmStatic
    fun registerMigrationParticipant(participant: ApiMigrationParticipant) {
        registerMigrationParticipant(participant, null)
    }

    @JvmStatic
    fun registerMigrationParticipant(
        participant: ApiMigrationParticipant,
        migrationExecutor: (() -> ApiMigrationParticipantResult)?,
    ) {
        migrationParticipants.removeIf { it.getId() == participant.getId() }
        migrationExecutors.remove(participant.getId())
        migrationParticipants.add(participant)
        migrationExecutor?.let { migrationExecutors[participant.getId()] = it }
    }

    @JvmStatic
    fun unregisterMigrationParticipant(participant: ApiMigrationParticipant) {
        migrationParticipants.removeIf { it === participant || it.getId() == participant.getId() }
        migrationExecutors.remove(participant.getId())
    }

    @JvmStatic
    fun getMigrationParticipants(): List<ApiMigrationParticipant> = migrationParticipants.toList()

    /**
     * /mwm migration からだけ呼び出す、登録済み移行処理の内部実行口です。
     * 外部 API には公開せず、実行元を WorldMigrationService に限定します。
     */
    @kotlin.jvm.JvmSynthetic
    internal fun executeMigrationParticipant(id: String): ApiMigrationParticipantResult? =
        migrationExecutors[id]?.invoke()

    /** /mwm migration の全体状態を、破壊的な全体操作の事前検査から利用します。 */
    @JvmStatic
    fun isMigrationPending(): Boolean =
        runCatching {
            JavaPlugin.getPlugin(MyWorldManager::class.java).worldMigrationService.hasPendingWork()
        }.getOrDefault(true)

    /** 対象ワールドに限定した移行保留状態です。別ワールドの隔離状態は含めません。 */
    @JvmStatic
    fun isWorldMigrationPending(worldUuid: UUID): Boolean =
        runCatching {
            !evaluateMigration(listOf(worldUuid), includeGlobal = false).allowed
        }.getOrDefault(true)

    /** 集約データ・コアメタデータを含む全体操作用の状態です。対象単位の別ワールドは除外します。 */
    @JvmStatic
    fun isGlobalMigrationPending(): Boolean =
        runCatching {
            !evaluateMigration(emptyList(), includeGlobal = true).allowed
        }.getOrDefault(true)

    /**
     * 破壊的操作・復元・全体同期が開始する前に、影響対象をまとめて評価します。
     * MWM外のプラグインは個別のbooleanではなく、ブロック理由もこの結果から表示できます。
     */
    @JvmStatic
    fun evaluateMigration(
        worldUuids: Collection<UUID> = emptyList(),
        includeGlobal: Boolean = true,
        includeUnresolved: Boolean = false,
    ): ApiMigrationPreflight =
        runCatching {
            JavaPlugin.getPlugin(MyWorldManager::class.java)
                .worldMigrationService
                .evaluatePreflight(worldUuids, includeGlobal, includeUnresolved)
        }.getOrElse { error ->
            ApiMigrationPreflight(
                listOf(
                    me.awabi2048.myworldmanager.api.service.ApiMigrationBlock(
                        participantId = "myworldmanager",
                        state = me.awabi2048.myworldmanager.api.service.ApiMigrationParticipantState.FAILED,
                        message = "migration state is unavailable: ${error.message ?: error.javaClass.simpleName}",
                    )
                )
            )
        }

    @JvmStatic
    fun registerWorldSettingsStatePolicy(policy: WorldSettingsStatePolicy) {
        worldSettingsStatePolicies.removeIf { it.getId() == policy.getId() }
        worldSettingsStatePolicies.add(policy)
    }

    @JvmStatic
    fun unregisterWorldSettingsStatePolicy(policy: WorldSettingsStatePolicy) {
        worldSettingsStatePolicies.removeIf {
            it === policy || it.getId() == policy.getId()
        }
    }

    @JvmStatic
    fun getWorldSettingsStatePolicies(): List<WorldSettingsStatePolicy> =
        worldSettingsStatePolicies.toList()

    @JvmStatic
    fun registerWorldSettingsRouteCapability(
        capability: me.awabi2048.myworldmanager.api.extension.WorldSettingsRouteCapability,
    ) {
        worldSettingsRouteCapabilities.remove(capability)
        worldSettingsRouteCapabilities.add(capability)
    }

    @JvmStatic
    fun unregisterWorldSettingsRouteCapability(
        capability: me.awabi2048.myworldmanager.api.extension.WorldSettingsRouteCapability,
    ) {
        worldSettingsRouteCapabilities.remove(capability)
    }

    @JvmStatic
    fun registerPlayerWorldRouteCapability(
        capability: me.awabi2048.myworldmanager.api.extension.PlayerWorldRouteCapability,
    ) {
        playerWorldRouteCapabilities.remove(capability)
        playerWorldRouteCapabilities.add(capability)
    }

    @JvmStatic
    fun unregisterPlayerWorldRouteCapability(
        capability: me.awabi2048.myworldmanager.api.extension.PlayerWorldRouteCapability,
    ) {
        playerWorldRouteCapabilities.remove(capability)
    }

    @JvmStatic
    fun registerDiscoveryRouteCapability(capability: DiscoveryRouteCapability) {
        discoveryRouteCapabilities.remove(capability)
        discoveryRouteCapabilities.add(capability)
    }

    @JvmStatic
    fun unregisterDiscoveryRouteCapability(capability: DiscoveryRouteCapability) {
        discoveryRouteCapabilities.remove(capability)
    }

    /**
     * 差し替えRouteだけを解決します。nullの場合は呼び出し側が標準/Bedrock経路へ戻ります。
     */
    @JvmStatic
    fun resolveDiscoveryRouteOverride(
        player: Player,
        request: DiscoveryRouteRequest,
    ): com.awabi2048.ccsystem.api.gui.MenuRoute? =
        discoveryRouteCapabilities.asReversed().firstNotNullOfOrNull { capability ->
            capability.prepare(player, request)
        }

    @JvmStatic
    fun registerFavoriteListRouteCapability(capability: FavoriteListRouteCapability) {
        favoriteListRouteCapabilities.remove(capability)
        favoriteListRouteCapabilities.add(capability)
    }

    @JvmStatic
    fun unregisterFavoriteListRouteCapability(capability: FavoriteListRouteCapability) {
        favoriteListRouteCapabilities.remove(capability)
    }

    /** 差し替えRouteがなければnullを返し、標準/Bedrock経路の選択を呼び出し側へ委ねます。 */
    @JvmStatic
    fun resolveFavoriteListRouteOverride(
        player: Player,
        request: FavoriteListRouteRequest,
    ): com.awabi2048.ccsystem.api.gui.MenuRoute? =
        favoriteListRouteCapabilities.asReversed().firstNotNullOfOrNull { capability ->
            capability.prepare(player, request)
        }

    @JvmStatic
    fun prepareWorldSettingsRoute(
        player: Player,
        worldUuid: UUID,
        request: WorldSettingsNavigationRequest = WorldSettingsNavigationRequest(),
    ): com.awabi2048.ccsystem.api.gui.MenuRoute? {
        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
        val worldData = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return null
        worldSettingsRouteCapabilities.asReversed().forEach { capability ->
            capability.prepare(player, worldData, request)?.let { return it }
        }
        plugin.settingsSessionManager.updateSessionAction(
            player,
            worldUuid,
            SettingsAction.VIEW_SETTINGS,
            isGui = true,
            isAdminFlow = request.isAdminFlow,
            isPlayerWorldFlow = request.isPlayerWorldFlow,
            parentShowBackButton = request.parentShowBackButton,
        )
        plugin.settingsSessionManager.getSession(player)?.showBackButton = request.showBackButton
        return plugin.worldSettingsGui.route(worldUuid)
    }

    @JvmStatic
    fun preparePlayerWorldRoute(
        player: Player,
        request: me.awabi2048.myworldmanager.api.extension.PlayerWorldRouteRequest,
    ): com.awabi2048.ccsystem.api.gui.MenuRoute {
        playerWorldRouteCapabilities.asReversed().forEach { capability ->
            capability.prepare(player, request)?.let { return it }
        }
        return JavaPlugin.getPlugin(MyWorldManager::class.java).playerWorldGui.route(
            request.page,
            request.targetPlayerUuid,
            request.targetPlayerName,
            request.showBackButton,
        )
    }

    @JvmStatic
    fun getPlayerWorlds(playerUuid: UUID): List<WorldData> =
        JavaPlugin.getPlugin(MyWorldManager::class.java).playerWorldGui
            .getPlayerWorlds(playerUuid)

    /** Discovery/Favorite差し替え画面が利用する、MWM所有の一覧Query/Action境界です。 */
    @JvmStatic
    fun getWorldListMenuService(): ApiWorldListMenuService =
        JavaPlugin.getPlugin(MyWorldManager::class.java).worldListMenuService

    /** ルート差し替え側でも標準画面と同じ現在ワールド表示を再利用します。 */
    @JvmStatic
    fun createCurrentWorldMenuElement(
        player: Player,
        slot: Int,
    ): com.awabi2048.ccsystem.api.gui.MenuElement =
        JavaPlugin.getPlugin(MyWorldManager::class.java).currentWorldMenuElementFactory.create(player, slot)

    @JvmStatic
    fun prepareUserSettingsRoute(
        player: Player,
        showBackButton: Boolean = true,
    ): com.awabi2048.ccsystem.api.gui.MenuRoute? =
        JavaPlugin.getPlugin(MyWorldManager::class.java).userSettingsGui
            .prepareOpen(player, showBackButton)

    @JvmStatic
    fun preparePendingInteractionRoute(
        page: Int,
        returnPage: Int,
        showBackButton: Boolean,
    ): com.awabi2048.ccsystem.api.gui.MenuRoute =
        JavaPlugin.getPlugin(MyWorldManager::class.java).pendingInteractionGui
            .prepareOpen(page, returnPage, showBackButton, fromBedrockMenu = false)

    @JvmStatic
    fun getPendingInteractionSummary(
        playerUuid: UUID,
    ): me.awabi2048.myworldmanager.api.extension.PendingInteractionSummary {
        val manager = JavaPlugin.getPlugin(MyWorldManager::class.java).pendingDecisionManager
        return me.awabi2048.myworldmanager.api.extension.PendingInteractionSummary(
            manager.getPendingCount(playerUuid),
            manager.getLatestPendingCreatedAt(playerUuid),
        )
    }

    /**
     * MWM-Chanpon等のアドオンがログイン時通知を判定するための公開照会APIです。
     * 内部Repositoryは公開せず、MWMのメンバー招待サービスが対象を絞り込みます。
     */
    @JvmStatic
    fun getPendingOfflineMemberInvites(targetUuid: UUID): List<PendingOfflineMemberInvite> =
        JavaPlugin.getPlugin(MyWorldManager::class.java)
            .memberInviteManager
            .getPendingOfflineMemberInvites(targetUuid)

    @JvmStatic
    fun executeWorldSettingsAction(
        request: me.awabi2048.myworldmanager.api.extension.WorldSettingsActionRequest,
    ): com.awabi2048.ccsystem.api.gui.MenuActionResult {
        return JavaPlugin.getPlugin(MyWorldManager::class.java).worldSettingsActionService.execute(request)
    }

    @JvmStatic
    fun getWorldSettingsActionContract(
        player: Player,
        worldUuid: UUID,
        action: me.awabi2048.myworldmanager.api.extension.WorldSettingsAction,
    ): me.awabi2048.myworldmanager.api.extension.WorldSettingsActionContract? {
        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
        val worldData = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return null
        return plugin.worldSettingsActionService.contract(player, worldData, action)
    }

    @JvmStatic
    fun openWorldSettings(
        player: Player,
        worldUuid: UUID,
        request: WorldSettingsNavigationRequest = WorldSettingsNavigationRequest(),
    ): Boolean {
        val route = prepareWorldSettingsRoute(player, worldUuid, request) ?: return false
        return CCSystem.getAPI().getMenuRuntimeService().navigate(player, route)
    }

    @JvmStatic
    fun prepareAdminMenuRoute(player: Player): com.awabi2048.ccsystem.api.gui.MenuRoute? {
        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
        if (!player.hasPermission("myworldmanager.admin")) return null
        plugin.settingsSessionManager.updateSessionAction(
            player,
            player.uniqueId,
            SettingsAction.ADMIN_MENU,
            isGui = true,
        )
        return plugin.adminCommandGui.route()
    }

    @JvmStatic
    fun openAdminMenu(player: Player): Boolean {
        val route = prepareAdminMenuRoute(player) ?: return false
        return CCSystem.getAPI().getMenuRuntimeService().open(player, route)
    }

    @JvmStatic
    fun closeWorldSettingsContext(player: Player) {
        JavaPlugin.getPlugin(MyWorldManager::class.java)
            .settingsSessionManager
            .endSession(player)
    }

    @JvmStatic
    fun toggleWorldVisitNotification(player: Player, worldUuid: UUID): Boolean {
        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
        val worldData = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return false
        plugin.worldSettingsGui.toggleNotification(player, worldData)
        return true
    }

    @JvmStatic
    fun hasWorldPortals(worldUuid: UUID): Boolean {
        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
        val worldData = plugin.worldConfigRepository.findByUuid(worldUuid) ?: return false
        return plugin.portalRepository.findAll().any { it.worldKey == worldData.worldKey }
    }

    @JvmStatic
    fun isCriticalSettingsEnabled(playerUuid: UUID): Boolean =
        JavaPlugin.getPlugin(MyWorldManager::class.java)
            .playerStatsRepository
            .findByUuid(playerUuid)
            .criticalSettingsEnabled

    @JvmStatic
    fun getConfiguredMenuIcon(
        menuId: String,
        iconId: String,
        fallback: Material,
    ): Material =
        JavaPlugin.getPlugin(MyWorldManager::class.java)
            .menuConfigManager
            .getIconMaterial(menuId, iconId, fallback)

    @JvmStatic
    fun prioritizePlayerWorld(playerUuid: UUID, worldUuid: UUID) {
        val repository = JavaPlugin.getPlugin(MyWorldManager::class.java).playerStatsRepository
        val stats = repository.findByUuid(playerUuid)
        stats.worldDisplayOrder.remove(worldUuid)
        stats.worldDisplayOrder.add(0, worldUuid)
        repository.save(stats)
    }

    @JvmStatic
    fun getPlayerWorldDisplayOrder(playerUuid: UUID): List<UUID> =
        JavaPlugin.getPlugin(MyWorldManager::class.java).playerStatsRepository
            .findByUuid(playerUuid).worldDisplayOrder.toList()

    @JvmStatic
    fun restorePlayerWorldDisplayOrder(playerUuid: UUID, worldUuid: UUID, order: List<UUID>): Boolean {
        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
        if (plugin.worldConfigRepository.findByUuid(worldUuid) == null) return false
        val stats = plugin.playerStatsRepository.findByUuid(playerUuid)
        stats.worldDisplayOrder.clear()
        stats.worldDisplayOrder.addAll(order)
        plugin.playerStatsRepository.save(stats)
        return true
    }

    @JvmStatic
    fun prepareStandardWorldCreationRoute(
        player: Player,
        billingMode: WorldPointBillingMode = WorldPointBillingMode.STANDARD,
    ): com.awabi2048.ccsystem.api.gui.MenuRoute {
        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
        val session = plugin.creationSessionManager.startSession(player.uniqueId)
        session.isDialogMode = true
        session.billingMode = billingMode
        // 作成開始の状態は直後に表示するGUIで確認できるため、チャット通知を重ねない。
        return plugin.creationGui.typeSelectionRoute()
    }

    @JvmStatic
    fun openStandardWorldCreation(
        player: Player,
        billingMode: WorldPointBillingMode = WorldPointBillingMode.STANDARD,
    ): Boolean {
        val route = prepareStandardWorldCreationRoute(player, billingMode)
        return CCSystem.getAPI().getMenuRuntimeService().navigate(player, route)
    }

    @JvmStatic
    fun hasWorldSettingsAdminAccess(player: Player): Boolean =
        player.hasPermission("myworldmanager.admin") ||
            JavaPlugin.getPlugin(MyWorldManager::class.java)
                .settingsSessionManager
                .getSession(player)
                ?.isAdminFlow == true

    @JvmStatic
    fun registerBedrockFormService(service: ApiBedrockFormService) {
        bedrockFormService = service
    }

    @JvmStatic
    fun unregisterBedrockFormService(service: ApiBedrockFormService) {
        if (bedrockFormService === service) bedrockFormService = null
    }

    @JvmStatic
    fun getBedrockFormService(): ApiBedrockFormService? = bedrockFormService

    @JvmStatic
    fun registerWorldPointService(service: WorldPointService) {
        worldPointService = service
    }

    @JvmStatic
    fun unregisterWorldPointService(service: WorldPointService) {
        if (worldPointService === service) {
            worldPointService = null
        }
    }

    @JvmStatic
    fun isWorldPointServiceAvailable(): Boolean {
        return worldPointService != null
    }

    @JvmStatic
    fun isWorldPointEconomyEnabled(): Boolean = getWorldRuntimePolicy().isWorldPointEconomyEnabled()

    @JvmStatic
    fun isWorldSlotSystemEnabled(): Boolean = getWorldRuntimePolicy().isWorldSlotSystemEnabled()

    @JvmStatic
    fun addWorldPoint(playerUuid: UUID, amount: Int): Int {
        check(isWorldPointEconomyEnabled()) { "MyWorldManager world point economy is disabled by runtime policy" }
        val service = worldPointService
            ?: throw IllegalStateException("MyWorldManager world point service is not available")
        return service.addWorldPoint(playerUuid, amount)
    }

    fun interface WorldPointService {
        fun addWorldPoint(playerUuid: UUID, amount: Int): Int
    }

    @JvmStatic
    fun registerWorldService(service: ApiWorldService) {
        worldService = service
    }

    @JvmStatic
    fun getWorldService(): ApiWorldService? {
        return worldService
    }

    @JvmStatic
    fun registerWorldEnvironmentService(service: ApiWorldEnvironmentService) {
        worldEnvironmentService = service
    }

    @JvmStatic
    fun unregisterWorldEnvironmentService(service: ApiWorldEnvironmentService) {
        if (worldEnvironmentService === service) {
            worldEnvironmentService = null
        }
    }

    @JvmStatic
    fun getWorldEnvironmentService(): ApiWorldEnvironmentService? {
        return worldEnvironmentService
    }

    @JvmStatic
    fun registerWorldRepository(repository: ApiWorldRepository) {
        worldRepository = repository
    }

    @JvmStatic
    fun getWorldRepository(): ApiWorldRepository? {
        return worldRepository
    }

    @JvmStatic
    fun registerTemplateRepository(repository: ApiTemplateRepository) {
        templateRepository = repository
    }

    @JvmStatic
    fun getTemplateRepository(): ApiTemplateRepository? {
        return templateRepository
    }

    @JvmStatic
    fun registerMemberManager(manager: ApiMemberManager) {
        memberManager = manager
    }

    @JvmStatic
    fun getMemberManager(): ApiMemberManager? {
        return memberManager
    }

    /**
     * 指定された設定ファイルを使うマクロサービスを生成します。
     *
     * 設定ファイルは呼出側のデータフォルダに配置し、存在確認と初期ファイルの
     * 配布も呼出側が担当します。これにより外部プラグインがこのAPIを利用しても、
     * MyWorldManager本体のmacro.ymlを読み替えたり上書きしたりしません。
     */
    @JvmStatic
    fun createMacroService(plugin: JavaPlugin, configFile: File): ApiMacroService {
        require(configFile.isFile) {
            "Macro configuration file is missing: ${configFile.absolutePath}"
        }
        return me.awabi2048.myworldmanager.service.MacroManager(plugin, configFile)
    }

    @JvmStatic
    fun registerWorldTagService(service: ApiWorldTagService) {
        worldTagService = service
    }

    @JvmStatic
    fun getWorldTagService(): ApiWorldTagService? {
        return worldTagService
    }

    @JvmStatic
    fun registerWorldCreationGuard(guard: WorldCreationGuard) {
        worldCreationGuards.removeIf { it.getId() == guard.getId() }
        worldCreationGuards.add(guard)
    }

    @JvmStatic
    fun unregisterWorldCreationGuard(guard: WorldCreationGuard) {
        worldCreationGuards.removeIf { it === guard || it.getId() == guard.getId() }
    }

    @JvmStatic
    fun getWorldCreationGuards(): List<WorldCreationGuard> = worldCreationGuards.toList()

    @JvmStatic
    /** 登録済みの純粋な作成可否Guardだけを照会します。GUI描画・Runtime inspectからも呼ばれます。 */
    fun checkWorldCreation(request: WorldCreationRequest): WorldCreationDecision {
        return worldCreationGuards
            .asReversed()
            .asSequence()
            .map { it.evaluate(request) }
            .firstOrNull { !it.allowed }
            ?: WorldCreationDecision.allow()
    }

    @JvmStatic
    fun registerWorldPlayerStatePolicy(policy: WorldPlayerStatePolicy) {
        worldPlayerStatePolicies.removeIf { it.getId() == policy.getId() }
        worldPlayerStatePolicies.add(policy)
    }

    @JvmStatic
    fun unregisterWorldPlayerStatePolicy(policy: WorldPlayerStatePolicy) {
        worldPlayerStatePolicies.removeIf { it === policy || it.getId() == policy.getId() }
    }

    @JvmStatic
    fun getWorldPlayerStatePolicies(): List<WorldPlayerStatePolicy> = worldPlayerStatePolicies.toList()

    /** 登録されたポリシーを合成し、SPECTATORは一時表示状態として変更しない。 */
    @JvmStatic
    fun syncWorldPlayerState(player: Player, worldData: WorldData) {
        if (player.gameMode == org.bukkit.GameMode.SPECTATOR) return
        val decisions = worldPlayerStatePolicies
            .sortedByDescending { it.getPriority() }
            .map { it.evaluate(player, worldData) }
        val gameMode = decisions.firstNotNullOfOrNull(WorldPlayerStateDecision::gameMode)
        val effectiveGameMode = gameMode ?: player.gameMode
        val gameModeAllowsFlight =
            effectiveGameMode == org.bukkit.GameMode.CREATIVE ||
                effectiveGameMode == org.bukkit.GameMode.SPECTATOR
        val flightAllowed =
            gameModeAllowsFlight ||
                (worldData.allowFlight && decisions.all { it.flightAllowed != false })

        gameMode?.let { desired ->
            if (player.gameMode != desired) player.gameMode = desired
        }
        if (!flightAllowed) {
            player.allowFlight = false
            if (player.isFlying) player.isFlying = false
        } else if (gameModeAllowsFlight || decisions.any { it.flightAllowed == true }) {
            player.allowFlight = true
        }
    }

    @JvmStatic
    fun canBuildInWorld(player: Player, worldData: WorldData): Boolean {
        return worldPlayerStatePolicies
            .map { it.evaluate(player, worldData) }
            .all { it.buildAllowed != false }
    }

    @JvmStatic
    fun syncOnlineWorldPlayerStates() {
        val repository = worldRepository ?: return
        org.bukkit.Bukkit.getOnlinePlayers().forEach { player ->
            repository.findByWorldKey(player.world.key.toString())?.let { syncWorldPlayerState(player, it) }
        }
    }

    @JvmStatic
    fun registerWorldDeleteGuard(guard: WorldDeleteGuard) {
        worldDeleteGuards.removeIf { it.getId() == guard.getId() }
        worldDeleteGuards.add(guard)
    }

    @JvmStatic
    fun unregisterWorldDeleteGuard(guard: WorldDeleteGuard) {
        worldDeleteGuards.removeIf { it === guard || it.getId() == guard.getId() }
    }

    @JvmStatic
    fun getWorldDeleteGuards(): List<WorldDeleteGuard> {
        return worldDeleteGuards.toList()
    }

    @JvmStatic
    fun registerWorldAccessPolicy(policy: WorldAccessPolicy) {
        worldAccessPolicies.removeIf { it.getId() == policy.getId() }
        worldAccessPolicies.add(policy)
    }

    @JvmStatic
    fun unregisterWorldAccessPolicy(policy: WorldAccessPolicy) {
        worldAccessPolicies.removeIf { it === policy || it.getId() == policy.getId() }
    }

    @JvmStatic
    fun getWorldAccessPolicy(): WorldAccessPolicy {
        return worldAccessPolicies.lastOrNull() ?: DefaultWorldAccessPolicy
    }

    @JvmStatic
    fun registerCommandPolicy(policy: CommandPolicy) {
        commandPolicies.removeIf { it.getId() == policy.getId() }
        commandPolicies.add(policy)
    }

    @JvmStatic
    fun unregisterCommandPolicy(policy: CommandPolicy) {
        commandPolicies.removeIf { it === policy || it.getId() == policy.getId() }
    }

    @JvmStatic
    fun getCommandPolicies(): List<CommandPolicy> {
        return commandPolicies.toList()
    }

    @JvmStatic
    fun registerCreateCommandHandler(handler: CreateCommandHandler) {
        createCommandHandlers.removeIf { it.getId() == handler.getId() }
        createCommandHandlers.add(handler)
    }

    @JvmStatic
    fun unregisterCreateCommandHandler(handler: CreateCommandHandler) {
        createCommandHandlers.removeIf { it === handler || it.getId() == handler.getId() }
    }

    @JvmStatic
    fun getCreateCommandHandler(): CreateCommandHandler? {
        return createCommandHandlers.lastOrNull()
    }

    @JvmStatic
    fun registerWorldRuntimePolicy(policy: WorldRuntimePolicy) {
        worldRuntimePolicies.removeIf { it.getId() == policy.getId() }
        worldRuntimePolicies.add(policy)
    }

    @JvmStatic
    fun unregisterWorldRuntimePolicy(policy: WorldRuntimePolicy) {
        worldRuntimePolicies.removeIf { it === policy || it.getId() == policy.getId() }
    }

    @JvmStatic
    fun getWorldRuntimePolicy(): WorldRuntimePolicy {
        return worldRuntimePolicies.lastOrNull() ?: DefaultWorldRuntimePolicy
    }

    @JvmStatic
    fun registerWorldPublishPolicy(policy: WorldPublishPolicy) {
        worldPublishPolicies.removeIf { it.getId() == policy.getId() }
        worldPublishPolicies.add(policy)
    }

    @JvmStatic
    fun unregisterWorldPublishPolicy(policy: WorldPublishPolicy) {
        worldPublishPolicies.removeIf { it === policy || it.getId() == policy.getId() }
    }

    @JvmStatic
    fun getWorldPublishPolicy(): WorldPublishPolicy {
        return worldPublishPolicies.lastOrNull() ?: DefaultWorldPublishPolicy
    }

    @JvmStatic
    fun registerWorldPortalPolicy(policy: WorldPortalPolicy) {
        worldPortalPolicies.removeIf { it.getId() == policy.getId() }
        worldPortalPolicies.add(policy)
    }

    @JvmStatic
    fun unregisterWorldPortalPolicy(policy: WorldPortalPolicy) {
        worldPortalPolicies.removeIf { it === policy || it.getId() == policy.getId() }
    }

    @JvmStatic
    fun getWorldPortalPolicy(): WorldPortalPolicy {
        return worldPortalPolicies.lastOrNull() ?: DefaultWorldPortalPolicy
    }

    @JvmStatic
    fun registerWorldMenuAccessPolicy(policy: WorldMenuAccessPolicy) {
        worldMenuAccessPolicies.removeIf { it.getId() == policy.getId() }
        worldMenuAccessPolicies.add(policy)
    }

    @JvmStatic
    fun unregisterWorldMenuAccessPolicy(policy: WorldMenuAccessPolicy) {
        worldMenuAccessPolicies.removeIf { it === policy || it.getId() == policy.getId() }
    }

    @JvmStatic
    fun getWorldMenuAccessChallenge(
        player: Player,
        worldData: WorldData,
    ): WorldMenuAccessChallenge? =
        worldMenuAccessPolicies.asReversed()
            .firstNotNullOfOrNull {
                it.challenge(WorldMenuAccessContext(player, worldData))
            }

    @JvmStatic
    fun registerWorldEvacuationProvider(provider: WorldEvacuationProvider) {
        worldEvacuationProviders.removeIf { it.getId() == provider.getId() }
        worldEvacuationProviders.add(provider)
    }

    @JvmStatic
    fun unregisterWorldEvacuationProvider(provider: WorldEvacuationProvider) {
        worldEvacuationProviders.removeIf { it === provider || it.getId() == provider.getId() }
    }

    @JvmStatic
    fun getEvacuationLocationOverride(): Location? {
        return worldEvacuationProviders.asReversed().firstNotNullOfOrNull { it.getEvacuationLocation() }
    }

    @JvmStatic
    fun registerWorldWorkPermissionPolicy(policy: WorldWorkPermissionPolicy) {
        worldWorkPermissionPolicies.removeIf { it.getId() == policy.getId() }
        worldWorkPermissionPolicies.add(policy)
    }

    @JvmStatic
    fun unregisterWorldWorkPermissionPolicy(policy: WorldWorkPermissionPolicy) {
        worldWorkPermissionPolicies.removeIf { it === policy || it.getId() == policy.getId() }
    }

    @JvmStatic
    fun canAssignWorldWorkPermission(worldData: WorldData, playerUuid: UUID): Boolean {
        return worldWorkPermissionPolicies.all { it.canAssign(worldData, playerUuid) }
    }

    @JvmStatic
    fun registerWorldWorkPermissionSyncService(service: WorldWorkPermissionSyncService) {
        worldWorkPermissionSyncService = service
    }

    @JvmStatic
    fun unregisterWorldWorkPermissionSyncService(service: WorldWorkPermissionSyncService) {
        if (worldWorkPermissionSyncService === service) {
            worldWorkPermissionSyncService = null
        }
    }

    @JvmStatic
    fun syncWorldWorkPermissions(worldUuid: UUID) {
        worldWorkPermissionSyncService?.sync(worldUuid)
    }

    fun interface WorldWorkPermissionSyncService {
        fun sync(worldUuid: UUID)
    }

    @JvmStatic
    fun setMemberManagementOpener(opener: (Player, UUID) -> Unit) {
        memberManagementOpener = opener
    }

    @JvmStatic
    fun openMemberManagementMenu(player: Player, worldUuid: UUID) {
        memberManagementOpener?.invoke(player, worldUuid)
    }

    private var memberManagementOpener: ((Player, UUID) -> Unit)? = null
}
