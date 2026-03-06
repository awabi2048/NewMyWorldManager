package me.awabi2048.myworldmanager.service

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.LikeSignData
import me.awabi2048.myworldmanager.model.LikeSignDisplayType
import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Sign
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class LikeSignManager(@Suppress("unused") private val plugin: MyWorldManager) {
    companion object {
        const val MAX_SIGNS_PER_WORLD = 10
        const val MAX_TITLE_LENGTH = 15
        const val MAX_DESCRIPTION_LENGTH = 30
        const val COOLDOWN_SECONDS = 3
        val LIKE_SIGN_ENTITY_KEY = org.bukkit.NamespacedKey("myworldmanager", "like_sign_uuid")
    }

    private val cooldownMap = ConcurrentHashMap<UUID, Long>()

    fun canPlaceSign(worldData: WorldData): Boolean = worldData.tourSigns.size < MAX_SIGNS_PER_WORLD

    fun isOnCooldown(playerUuid: UUID): Boolean {
        val lastAction = cooldownMap[playerUuid] ?: return false
        return System.currentTimeMillis() - lastAction < COOLDOWN_SECONDS * 1000L
    }

    fun setCooldown(playerUuid: UUID) {
        cooldownMap[playerUuid] = System.currentTimeMillis()
    }

    fun createSign(
        worldData: WorldData,
        player: Player,
        block: Block,
        blockFace: BlockFace,
        title: String,
        description: String,
        displayType: LikeSignDisplayType
    ): LikeSignData? = null

    internal fun createSignBlock(signData: LikeSignData, clickedBlock: Block, blockFace: BlockFace, player: Player) {}

    fun updateSignText(sign: Sign, signData: LikeSignData) {}

    internal fun createHologram(signData: LikeSignData, block: Block) {}

    fun updateHologramText(signData: LikeSignData, world: org.bukkit.World) {}

    fun removeSign(signData: LikeSignData, worldData: WorldData, player: Player? = null): Boolean = false

    fun findSignByUuid(worldData: WorldData, uuid: UUID): LikeSignData? = null

    fun findSignAtLocation(worldData: WorldData, block: Block): LikeSignData? = null

    fun findSignFromSignBlock(worldData: WorldData, signBlock: Block): LikeSignData? = null

    fun findSignFromEntity(worldData: WorldData, entity: org.bukkit.entity.Entity): LikeSignData? = null

    fun isWorldMember(worldData: WorldData, playerUuid: UUID): Boolean {
        return worldData.owner == playerUuid || worldData.members.contains(playerUuid) || worldData.moderators.contains(playerUuid)
    }

    fun canPlayerLike(worldData: WorldData, playerUuid: UUID): Boolean = !isWorldMember(worldData, playerUuid)

    fun refreshSignDisplay(signData: LikeSignData, worldData: WorldData) {}

    fun spawnHologramsForWorld(worldData: WorldData) {}
}
