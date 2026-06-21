package me.awabi2048.myworldmanager.integration

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.flags.Flags
import com.sk89q.worldguard.protection.flags.RegionGroup
import com.sk89q.worldguard.protection.flags.StateFlag
import com.sk89q.worldguard.protection.regions.GlobalProtectedRegion
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.model.WorldData
import net.luckperms.api.LuckPerms
import net.luckperms.api.node.types.InheritanceNode
import net.luckperms.api.node.types.PermissionNode
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration

/** Keeps invariant MyWorld protection and participant permissions in sync with external plugins. */
class WorldPermissionPolicyService(
    private val luckPerms: LuckPerms?,
    private val worldGuardAvailable: Boolean,
    private val workGroupName: String,
    private val logger: Logger,
    dataFolder: File
) {
    private val stateFile = File(dataFolder, "data/world_permission_policy.yml")
    private val missingWorkGroupWarned = AtomicBoolean(false)
    private val managedWorkGroups: Set<String> = loadManagedWorkGroups()

    fun initializeWorld(worldData: WorldData) {
        initializeDefaultsOnce(worldData)
        syncParticipants(worldData)
    }

    fun initializeDefaultsOnce(worldData: WorldData) {
        val state = YamlConfiguration.loadConfiguration(stateFile)
        val key = "initialized.${worldData.uuid}"
        if (state.getBoolean(key, false)) return
        val world = Bukkit.getWorld(worldName(worldData)) ?: return
        world.setGameRule(org.bukkit.GameRule.MOB_GRIEFING, true)
        world.setGameRule(org.bukkit.GameRule.GLOBAL_SOUND_EVENTS, false)
        if (worldGuardAvailable) {
            val manager = WorldGuard.getInstance().platform.regionContainer.get(BukkitAdapter.adapt(world))
            if (manager != null) {
                val region = manager.getRegion(GLOBAL_REGION_ID) ?: GlobalProtectedRegion(GLOBAL_REGION_ID).also(manager::addRegion)
                if (region.getFlag(Flags.PVP) == null) {
                    region.setFlag(Flags.PVP, StateFlag.State.DENY)
                }
                if (region.getFlag(Flags.PASSTHROUGH) == null) {
                    region.setFlag(Flags.PASSTHROUGH, StateFlag.State.DENY)
                }
                if (region.getFlag(Flags.PASSTHROUGH.regionGroupFlag) == null) {
                    region.setFlag(Flags.PASSTHROUGH.regionGroupFlag, RegionGroup.NON_MEMBERS)
                }
                manager.saveChanges()
            }
        }

        stateFile.parentFile.mkdirs()
        state.set(key, true)
        state.save(stateFile)
    }

    fun syncParticipants(worldData: WorldData) {
        syncWorldGuardDomains(worldData)
        syncParticipantPermissions(worldData, worldData.owner, OWNER_PERMISSIONS)
        (worldData.members + worldData.moderators)
            .distinct()
            .filter { it != worldData.owner }
            .forEach { syncParticipantPermissions(worldData, it, MEMBER_PERMISSIONS) }
    }

    fun removeMember(worldData: WorldData, memberUuid: UUID) {
        updateWorldGuardRegion(worldData) { region ->
            region.members.removePlayer(memberUuid)
        }
        clearParticipantPermissions(memberUuid, worldName(worldData))
    }

    fun transferOwner(worldData: WorldData, oldOwnerUuid: UUID, newOwnerUuid: UUID) {
        updateWorldGuardRegion(worldData) { region ->
            region.owners.removePlayer(oldOwnerUuid)
            region.owners.addPlayer(newOwnerUuid)
            region.members.removePlayer(newOwnerUuid)
            if (oldOwnerUuid in worldData.members || oldOwnerUuid in worldData.moderators) {
                region.members.addPlayer(oldOwnerUuid)
            }
        }
        if (oldOwnerUuid in worldData.members || oldOwnerUuid in worldData.moderators) {
            syncParticipantPermissions(worldData, oldOwnerUuid, MEMBER_PERMISSIONS)
        } else {
            clearParticipantPermissions(oldOwnerUuid, worldName(worldData))
        }
        syncParticipantPermissions(worldData, newOwnerUuid, OWNER_PERMISSIONS)
    }

    fun clearWorld(worldName: String, participants: Iterable<UUID>) {
        participants.toSet().forEach { clearParticipantPermissions(it, worldName) }
    }

    fun handlePlayerEnteredWorld(player: org.bukkit.entity.Player, worldData: WorldData) {
        player.resetPlayerTime()
        val world = player.world
        world.setGameRule(org.bukkit.GameRule.MOB_GRIEFING, true)
        world.setGameRule(org.bukkit.GameRule.GLOBAL_SOUND_EVENTS, false)
        // This replaces the former macro while keeping the cleanup scoped to MyWorld entry.
        world.getEntitiesByClass(org.bukkit.entity.Shulker::class.java).forEach(org.bukkit.entity.Entity::remove)
    }

    private fun syncWorldGuardDomains(worldData: WorldData) {
        updateWorldGuardRegion(worldData) { region ->
            region.owners.addPlayer(worldData.owner)
            region.members.removePlayer(worldData.owner)
            (worldData.members + worldData.moderators).distinct().forEach(region.members::addPlayer)
        }
    }

    private fun updateWorldGuardRegion(
        worldData: WorldData,
        mutation: (com.sk89q.worldguard.protection.regions.ProtectedRegion) -> Unit
    ) {
        if (!worldGuardAvailable) return
        val world = Bukkit.getWorld(worldName(worldData)) ?: return
        val manager = WorldGuard.getInstance().platform.regionContainer.get(BukkitAdapter.adapt(world)) ?: return
        val region = manager.getRegion(GLOBAL_REGION_ID) ?: GlobalProtectedRegion(GLOBAL_REGION_ID).also(manager::addRegion)
        mutation(region)
        manager.saveChanges()
    }

    private fun syncParticipantPermissions(worldData: WorldData, playerUuid: UUID, rolePermissions: Set<String>) {
        val api = luckPerms ?: return
        val worldName = worldName(worldData)
        val workGroupAllowed = workGroupName.isNotEmpty() &&
            MyWorldManagerApi.canAssignWorldWorkPermission(worldData, playerUuid) &&
            isWorkGroupAvailable(api)
        api.userManager.loadUser(playerUuid)
            .thenCompose { user ->
                ALL_ROLE_PERMISSIONS.forEach { permission ->
                    user.data().remove(permissionNode(permission, worldName))
                }
                rolePermissions.forEach { permission ->
                    user.data().add(permissionNode(permission, worldName))
                }
                managedWorkGroups.forEach { group ->
                    user.data().remove(workGroupNode(group, worldName))
                }
                if (workGroupAllowed) {
                    user.data().add(workGroupNode(workGroupName, worldName))
                }
                api.userManager.saveUser(user)
            }
            .exceptionally { error ->
                logger.warning("Failed to update MyWorld permissions for $playerUuid in $worldName: ${error.message}")
                null
            }
    }

    private fun clearParticipantPermissions(playerUuid: UUID, worldName: String) {
        val api = luckPerms ?: return
        api.userManager.loadUser(playerUuid)
            .thenCompose { user ->
                ALL_ROLE_PERMISSIONS.forEach { permission ->
                    user.data().remove(permissionNode(permission, worldName))
                }
                managedWorkGroups.forEach { group ->
                    user.data().remove(workGroupNode(group, worldName))
                }
                api.userManager.saveUser(user)
            }
            .exceptionally { error ->
                logger.warning("Failed to clear MyWorld permissions for $playerUuid in $worldName: ${error.message}")
                null
            }
    }

    private fun isWorkGroupAvailable(api: LuckPerms): Boolean {
        if (api.groupManager.getGroup(workGroupName) != null) return true
        if (missingWorkGroupWarned.compareAndSet(false, true)) {
            logger.warning("LuckPerms work group '$workGroupName' does not exist; automatic MyWorld work access is disabled.")
        }
        return false
    }

    private fun loadManagedWorkGroups(): Set<String> {
        val state = YamlConfiguration.loadConfiguration(stateFile)
        val groups = state.getStringList(MANAGED_GROUPS_KEY)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toMutableSet()
        if (workGroupName.isNotEmpty() && groups.add(workGroupName)) {
            stateFile.parentFile.mkdirs()
            state.set(MANAGED_GROUPS_KEY, groups.sorted())
            state.save(stateFile)
        }
        return groups
    }

    private fun permissionNode(permission: String, worldName: String): PermissionNode =
        PermissionNode.builder(permission).withContext("world", worldName).value(true).build()

    private fun workGroupNode(groupName: String, worldName: String): InheritanceNode =
        InheritanceNode.builder(groupName).withContext("world", worldName).value(true).build()

    companion object {
        private const val GLOBAL_REGION_ID = "__global__"
        private const val MANAGED_GROUPS_KEY = "managed_work_groups"
        private val OWNER_PERMISSIONS = setOf("worldguard.region.claim", "worldguard.region.unlimited")
        private val MEMBER_PERMISSIONS = setOf("worldguard.region.claim")
        private val ALL_ROLE_PERMISSIONS = OWNER_PERMISSIONS + MEMBER_PERMISSIONS

        fun worldName(worldData: WorldData): String = worldData.customWorldName ?: "my_world.${worldData.uuid}"
    }
}
