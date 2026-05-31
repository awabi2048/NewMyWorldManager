package me.awabi2048.myworldmanager.api

import me.awabi2048.myworldmanager.api.extension.MenuExtension
import me.awabi2048.myworldmanager.api.extension.WorldDeleteGuard
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
    fun setMemberManagementOpener(opener: (Player, UUID) -> Unit) {
        memberManagementOpener = opener
    }

    @JvmStatic
    fun openMemberManagementMenu(player: Player, worldUuid: UUID) {
        memberManagementOpener?.invoke(player, worldUuid)
    }

    private var memberManagementOpener: ((Player, UUID) -> Unit)? = null
}
