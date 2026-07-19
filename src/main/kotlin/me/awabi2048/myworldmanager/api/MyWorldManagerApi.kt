package me.awabi2048.myworldmanager.api

import me.awabi2048.myworldmanager.api.extension.AdminWorldListProvider
import me.awabi2048.myworldmanager.api.extension.AdminWorldListRequest
import me.awabi2048.myworldmanager.api.extension.AdminMenuProvider
import me.awabi2048.myworldmanager.api.extension.DiscoveryMenuProvider
import me.awabi2048.myworldmanager.api.extension.DiscoveryMenuRequest
import me.awabi2048.myworldmanager.api.extension.FavoriteListMenuProvider
import me.awabi2048.myworldmanager.api.extension.FavoriteListMenuRequest
import me.awabi2048.myworldmanager.api.extension.FavoriteMenuProvider
import me.awabi2048.myworldmanager.api.extension.MenuExtension
import me.awabi2048.myworldmanager.api.extension.CommandPolicy
import me.awabi2048.myworldmanager.api.extension.CreateCommandHandler
import me.awabi2048.myworldmanager.api.extension.CreationConfirmationMenuProvider
import me.awabi2048.myworldmanager.api.extension.DefaultWorldAccessPolicy
import me.awabi2048.myworldmanager.api.extension.DefaultWorldPublishPolicy
import me.awabi2048.myworldmanager.api.extension.DefaultWorldPortalPolicy
import me.awabi2048.myworldmanager.api.extension.DefaultWorldRuntimePolicy
import me.awabi2048.myworldmanager.api.extension.PlayerWorldMenuProvider
import me.awabi2048.myworldmanager.api.extension.PlayerWorldMenuRequest
import me.awabi2048.myworldmanager.api.extension.VisitMenuProvider
import me.awabi2048.myworldmanager.api.extension.VisitMenuRequest
import me.awabi2048.myworldmanager.api.extension.WorldAccessPolicy
import me.awabi2048.myworldmanager.api.extension.WorldCreationDecision
import me.awabi2048.myworldmanager.api.extension.WorldCreationGuard
import me.awabi2048.myworldmanager.api.extension.WorldCreationRequest
import me.awabi2048.myworldmanager.api.extension.WorldDeleteGuard
import me.awabi2048.myworldmanager.api.extension.WorldEvacuationProvider
import me.awabi2048.myworldmanager.api.extension.WorldMenuAccessProvider
import me.awabi2048.myworldmanager.api.extension.WorldSettingsMenuProvider
import me.awabi2048.myworldmanager.api.extension.WorldSettingsMenuRequest
import me.awabi2048.myworldmanager.api.extension.WorldPublishPolicy
import me.awabi2048.myworldmanager.api.extension.WorldPortalPolicy
import me.awabi2048.myworldmanager.api.extension.WorldRuntimePolicy
import me.awabi2048.myworldmanager.api.extension.WorldPlayerStateDecision
import me.awabi2048.myworldmanager.api.extension.WorldPlayerStatePolicy
import me.awabi2048.myworldmanager.api.extension.WorldWorkPermissionPolicy
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.api.service.ApiMemberManager
import me.awabi2048.myworldmanager.api.service.ApiBedrockFormService
import me.awabi2048.myworldmanager.api.service.ApiTemplateRepository
import me.awabi2048.myworldmanager.api.service.ApiWorldRepository
import me.awabi2048.myworldmanager.api.service.ApiWorldEnvironmentService
import me.awabi2048.myworldmanager.api.service.ApiWorldService
import me.awabi2048.myworldmanager.api.service.ApiWorldTagService
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import org.bukkit.Location
import org.bukkit.OfflinePlayer
import org.bukkit.Bukkit
import org.bukkit.entity.Player

object MyWorldManagerApi {

    private var worldPointService: WorldPointService? = null
    private var worldService: ApiWorldService? = null
    private var worldEnvironmentService: ApiWorldEnvironmentService? = null
    private var worldRepository: ApiWorldRepository? = null
    private var templateRepository: ApiTemplateRepository? = null
    private var memberManager: ApiMemberManager? = null
    private var worldTagService: ApiWorldTagService? = null
    private val worldCreationGuards = CopyOnWriteArrayList<WorldCreationGuard>()
    private val worldPlayerStatePolicies = CopyOnWriteArrayList<WorldPlayerStatePolicy>()
    private val menuExtensions = CopyOnWriteArrayList<MenuExtension>()
    private val worldDeleteGuards = CopyOnWriteArrayList<WorldDeleteGuard>()
    private val worldAccessPolicies = CopyOnWriteArrayList<WorldAccessPolicy>()
    private val commandPolicies = CopyOnWriteArrayList<CommandPolicy>()
    private val createCommandHandlers = CopyOnWriteArrayList<CreateCommandHandler>()
    private val worldRuntimePolicies = CopyOnWriteArrayList<WorldRuntimePolicy>()
    private val worldPublishPolicies = CopyOnWriteArrayList<WorldPublishPolicy>()
    private val worldPortalPolicies = CopyOnWriteArrayList<WorldPortalPolicy>()
    private val worldSettingsMenuProviders = CopyOnWriteArrayList<WorldSettingsMenuProvider>()
    private val adminWorldListProviders = CopyOnWriteArrayList<AdminWorldListProvider>()
    private val adminMenuProviders = CopyOnWriteArrayList<AdminMenuProvider>()
    private val playerWorldMenuProviders = CopyOnWriteArrayList<PlayerWorldMenuProvider>()
    private val creationConfirmationMenuProviders = CopyOnWriteArrayList<CreationConfirmationMenuProvider>()
    private val discoveryMenuProviders = CopyOnWriteArrayList<DiscoveryMenuProvider>()
    private val favoriteListMenuProviders = CopyOnWriteArrayList<FavoriteListMenuProvider>()
    private val favoriteMenuProviders = CopyOnWriteArrayList<FavoriteMenuProvider>()
    private val visitMenuProviders = CopyOnWriteArrayList<VisitMenuProvider>()
    private val worldEvacuationProviders = CopyOnWriteArrayList<WorldEvacuationProvider>()
    private val worldMenuAccessProviders = CopyOnWriteArrayList<WorldMenuAccessProvider>()
    private val worldWorkPermissionPolicies = CopyOnWriteArrayList<WorldWorkPermissionPolicy>()
    private var worldWorkPermissionSyncService: WorldWorkPermissionSyncService? = null
    private var bedrockFormService: ApiBedrockFormService? = null

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
    fun registerMenuExtension(extension: MenuExtension) {
        menuExtensions.removeIf { it.getId() == extension.getId() }
        menuExtensions.add(extension)
    }

    @JvmStatic
    fun unregisterMenuExtension(extension: MenuExtension) {
        menuExtensions.removeIf { it === extension || it.getId() == extension.getId() }
    }

    @JvmStatic
    fun getMenuExtensions(): List<MenuExtension> {
        return menuExtensions.toList()
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
    fun registerWorldSettingsMenuProvider(provider: WorldSettingsMenuProvider) {
        worldSettingsMenuProviders.removeIf { it.getId() == provider.getId() }
        worldSettingsMenuProviders.add(provider)
    }

    @JvmStatic
    fun unregisterWorldSettingsMenuProvider(provider: WorldSettingsMenuProvider) {
        worldSettingsMenuProviders.removeIf { it === provider || it.getId() == provider.getId() }
    }

    @JvmStatic
    fun openWorldSettingsMenuOverride(
        player: Player,
        worldData: WorldData,
        request: WorldSettingsMenuRequest
    ): Boolean {
        return worldSettingsMenuProviders.asReversed().any { it.open(player, worldData, request) }
    }

    @JvmStatic
    fun registerAdminWorldListProvider(provider: AdminWorldListProvider) {
        adminWorldListProviders.removeIf { it.getId() == provider.getId() }
        adminWorldListProviders.add(provider)
    }

    @JvmStatic
    fun unregisterAdminWorldListProvider(provider: AdminWorldListProvider) {
        adminWorldListProviders.removeIf { it === provider || it.getId() == provider.getId() }
    }

    @JvmStatic
    fun openAdminWorldListOverride(player: Player, request: AdminWorldListRequest): Boolean {
        return adminWorldListProviders.asReversed().any { it.open(player, request) }
    }

    @JvmStatic
    fun registerAdminMenuProvider(provider: AdminMenuProvider) {
        adminMenuProviders.removeIf { it.getId() == provider.getId() }
        adminMenuProviders.add(provider)
    }

    @JvmStatic
    fun unregisterAdminMenuProvider(provider: AdminMenuProvider) {
        adminMenuProviders.removeIf { it === provider || it.getId() == provider.getId() }
    }

    @JvmStatic
    fun getAdminMenuProviders(): List<AdminMenuProvider> {
        return adminMenuProviders.toList()
    }

    @JvmStatic
    fun openNextAdminMenu(player: Player, currentProviderId: String? = null): Boolean {
        val providers = adminMenuProviders.toList()
        if (providers.isEmpty()) return false
        val currentIndex = currentProviderId?.let { id -> providers.indexOfFirst { it.getId() == id } } ?: -1
        val nextIndex = if (currentIndex < 0 || currentIndex + 1 >= providers.size) 0 else currentIndex + 1
        providers[nextIndex].open(player)
        return true
    }

    @JvmStatic
    fun registerPlayerWorldMenuProvider(provider: PlayerWorldMenuProvider) {
        playerWorldMenuProviders.removeIf { it.getId() == provider.getId() }
        playerWorldMenuProviders.add(provider)
    }

    @JvmStatic
    fun unregisterPlayerWorldMenuProvider(provider: PlayerWorldMenuProvider) {
        playerWorldMenuProviders.removeIf { it === provider || it.getId() == provider.getId() }
    }

    @JvmStatic
    fun openPlayerWorldMenuOverride(player: Player, request: PlayerWorldMenuRequest): Boolean {
        return playerWorldMenuProviders.asReversed().any { it.open(player, request) }
    }

    @JvmStatic
    fun registerCreationConfirmationMenuProvider(provider: CreationConfirmationMenuProvider) {
        creationConfirmationMenuProviders.removeIf { it.getId() == provider.getId() }
        creationConfirmationMenuProviders.add(provider)
    }

    @JvmStatic
    fun unregisterCreationConfirmationMenuProvider(provider: CreationConfirmationMenuProvider) {
        creationConfirmationMenuProviders.removeIf {
            it === provider || it.getId() == provider.getId()
        }
    }

    @JvmStatic
    fun openCreationConfirmationMenuOverride(
        player: Player,
        session: me.awabi2048.myworldmanager.session.WorldCreationSession
    ): Boolean {
        for (provider in creationConfirmationMenuProviders.asReversed()) {
            val handled = runCatching { provider.open(player, session) }
                .onFailure { error ->
                    Bukkit.getLogger().log(
                        java.util.logging.Level.SEVERE,
                        "Creation confirmation provider '${provider.getId()}' failed; using the next provider or MWM fallback.",
                        error
                    )
                }
                .getOrDefault(false)
            if (handled) return true
        }
        return false
    }

    @JvmStatic
    fun registerDiscoveryMenuProvider(provider: DiscoveryMenuProvider) {
        discoveryMenuProviders.removeIf { it.getId() == provider.getId() }
        discoveryMenuProviders.add(provider)
    }

    @JvmStatic
    fun unregisterDiscoveryMenuProvider(provider: DiscoveryMenuProvider) {
        discoveryMenuProviders.removeIf { it === provider || it.getId() == provider.getId() }
    }

    @JvmStatic
    fun openDiscoveryMenuOverride(player: Player, request: DiscoveryMenuRequest): Boolean {
        return discoveryMenuProviders.asReversed().any { it.open(player, request) }
    }

    @JvmStatic
    fun registerFavoriteListMenuProvider(provider: FavoriteListMenuProvider) {
        favoriteListMenuProviders.removeIf { it.getId() == provider.getId() }
        favoriteListMenuProviders.add(provider)
    }

    @JvmStatic
    fun unregisterFavoriteListMenuProvider(provider: FavoriteListMenuProvider) {
        favoriteListMenuProviders.removeIf { it === provider || it.getId() == provider.getId() }
    }

    @JvmStatic
    fun openFavoriteListMenuOverride(player: Player, request: FavoriteListMenuRequest): Boolean {
        return favoriteListMenuProviders.asReversed().any { it.open(player, request) }
    }

    @JvmStatic
    fun registerFavoriteMenuProvider(provider: FavoriteMenuProvider) {
        favoriteMenuProviders.removeIf { it.getId() == provider.getId() }
        favoriteMenuProviders.add(provider)
    }

    @JvmStatic
    fun unregisterFavoriteMenuProvider(provider: FavoriteMenuProvider) {
        favoriteMenuProviders.removeIf { it === provider || it.getId() == provider.getId() }
    }

    @JvmStatic
    fun openFavoriteMenuOverride(player: Player, worldData: WorldData?): Boolean {
        return favoriteMenuProviders.asReversed().any { it.open(player, worldData) }
    }

    @JvmStatic
    fun registerVisitMenuProvider(provider: VisitMenuProvider) {
        visitMenuProviders.removeIf { it.getId() == provider.getId() }
        visitMenuProviders.add(provider)
    }

    @JvmStatic
    fun unregisterVisitMenuProvider(provider: VisitMenuProvider) {
        visitMenuProviders.removeIf { it === provider || it.getId() == provider.getId() }
    }

    @JvmStatic
    fun openVisitMenuOverride(player: Player, owner: OfflinePlayer, request: VisitMenuRequest): Boolean {
        return visitMenuProviders.asReversed().any { it.open(player, owner, request) }
    }

    @JvmStatic
    fun registerWorldMenuAccessProvider(provider: WorldMenuAccessProvider) {
        worldMenuAccessProviders.removeIf { it.getId() == provider.getId() }
        worldMenuAccessProviders.add(provider)
    }

    @JvmStatic
    fun unregisterWorldMenuAccessProvider(provider: WorldMenuAccessProvider) {
        worldMenuAccessProviders.removeIf { it === provider || it.getId() == provider.getId() }
    }

    @JvmStatic
    fun openWorldMenuAccessOverride(player: Player, worldData: WorldData, showBackButton: Boolean): Boolean {
        return worldMenuAccessProviders.asReversed().any { it.open(player, worldData, showBackButton) }
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
