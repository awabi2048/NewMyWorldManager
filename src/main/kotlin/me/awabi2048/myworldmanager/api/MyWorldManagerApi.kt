package me.awabi2048.myworldmanager.api

import me.awabi2048.myworldmanager.api.extension.MenuExtension
import me.awabi2048.myworldmanager.api.extension.CommandPolicy
import me.awabi2048.myworldmanager.api.extension.CreateCommandHandler
import me.awabi2048.myworldmanager.api.extension.DefaultWorldAccessPolicy
import me.awabi2048.myworldmanager.api.extension.DefaultWorldPublishPolicy
import me.awabi2048.myworldmanager.api.extension.DefaultWorldRuntimePolicy
import me.awabi2048.myworldmanager.api.extension.WorldAccessPolicy
import me.awabi2048.myworldmanager.api.extension.WorldDeleteGuard
import me.awabi2048.myworldmanager.api.extension.WorldPublishPolicy
import me.awabi2048.myworldmanager.api.extension.WorldRuntimePolicy
import me.awabi2048.myworldmanager.api.service.ApiMemberManager
import me.awabi2048.myworldmanager.api.service.ApiTemplateRepository
import me.awabi2048.myworldmanager.api.service.ApiWorldRepository
import me.awabi2048.myworldmanager.api.service.ApiWorldService
import me.awabi2048.myworldmanager.api.service.ApiWorldTagService
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import org.bukkit.entity.Player

object MyWorldManagerApi {

    private var worldPointService: WorldPointService? = null
    private var worldService: ApiWorldService? = null
    private var worldRepository: ApiWorldRepository? = null
    private var templateRepository: ApiTemplateRepository? = null
    private var memberManager: ApiMemberManager? = null
    private var worldTagService: ApiWorldTagService? = null
    private val menuExtensions = CopyOnWriteArrayList<MenuExtension>()
    private val worldDeleteGuards = CopyOnWriteArrayList<WorldDeleteGuard>()
    private val worldAccessPolicies = CopyOnWriteArrayList<WorldAccessPolicy>()
    private val commandPolicies = CopyOnWriteArrayList<CommandPolicy>()
    private val createCommandHandlers = CopyOnWriteArrayList<CreateCommandHandler>()
    private val worldRuntimePolicies = CopyOnWriteArrayList<WorldRuntimePolicy>()
    private val worldPublishPolicies = CopyOnWriteArrayList<WorldPublishPolicy>()

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
    fun addWorldPoint(playerUuid: UUID, amount: Int): Int {
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
    fun setMemberManagementOpener(opener: (Player, UUID) -> Unit) {
        memberManagementOpener = opener
    }

    @JvmStatic
    fun openMemberManagementMenu(player: Player, worldUuid: UUID) {
        memberManagementOpener?.invoke(player, worldUuid)
    }

    private var memberManagementOpener: ((Player, UUID) -> Unit)? = null
}
