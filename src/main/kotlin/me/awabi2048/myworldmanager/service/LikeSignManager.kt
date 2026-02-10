package me.awabi2048.myworldmanager.service

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.LikeSignData
import me.awabi2048.myworldmanager.model.LikeSignDisplayType
import me.awabi2048.myworldmanager.model.WorldData
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Sign
import org.bukkit.block.data.Directional
import org.bukkit.entity.Interaction
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Vector3f
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class LikeSignManager(private val plugin: MyWorldManager) {

    companion object {
        const val MAX_SIGNS_PER_WORLD = 10
        const val MAX_TITLE_LENGTH = 15
        const val MAX_DESCRIPTION_LENGTH = 30
        const val COOLDOWN_SECONDS = 3
        
        val LIKE_SIGN_ENTITY_KEY = org.bukkit.NamespacedKey("myworldmanager", "like_sign_uuid")
    }

    private val cooldownMap = ConcurrentHashMap<UUID, Long>()

    fun canPlaceSign(worldData: WorldData): Boolean {
        return worldData.likeSigns.size < MAX_SIGNS_PER_WORLD
    }

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
    ): LikeSignData? {
        val signUuid = UUID.randomUUID()
        
        val (actualBlock, actualBlockFace) = when (displayType) {
            LikeSignDisplayType.SIGN -> {
                if (blockFace == BlockFace.UP) {
                    block.getRelative(BlockFace.UP) to BlockFace.UP
                } else {
                    block to blockFace
                }
            }
            LikeSignDisplayType.HOLOGRAM -> block to blockFace
        }
        
        val signData = LikeSignData(
            uuid = signUuid,
            worldUuid = worldData.uuid,
            title = title.take(MAX_TITLE_LENGTH),
            description = description.take(MAX_DESCRIPTION_LENGTH),
            displayType = displayType,
            placedBy = player.uniqueId,
            blockX = actualBlock.x,
            blockY = actualBlock.y,
            blockZ = actualBlock.z,
            blockFace = actualBlockFace.name
        )

        when (displayType) {
            LikeSignDisplayType.SIGN -> createSignBlock(signData, block, blockFace, player)
            LikeSignDisplayType.HOLOGRAM -> createHologram(signData, block)
        }

        worldData.likeSigns.add(signData)
        plugin.worldConfigRepository.save(worldData)

        return signData
    }

    internal fun createSignBlock(signData: LikeSignData, clickedBlock: Block, blockFace: BlockFace, player: Player) {
        val world = clickedBlock.world
        
        val signBlock = if (blockFace == BlockFace.UP) {
            clickedBlock.getRelative(BlockFace.UP)
        } else {
            clickedBlock
        }
        
        if (blockFace == BlockFace.UP) {
            signBlock.type = Material.PALE_OAK_SIGN
            val sign = signBlock.state as? Sign ?: return
            val rotatable = signBlock.blockData as? org.bukkit.block.data.Rotatable ?: return
            
            val playerYaw = player.location.yaw
            val rotation = when {
                playerYaw >= -22.5 && playerYaw < 22.5 -> org.bukkit.block.BlockFace.SOUTH
                playerYaw >= 22.5 && playerYaw < 67.5 -> org.bukkit.block.BlockFace.SOUTH_WEST
                playerYaw >= 67.5 && playerYaw < 112.5 -> org.bukkit.block.BlockFace.WEST
                playerYaw >= 112.5 && playerYaw < 157.5 -> org.bukkit.block.BlockFace.NORTH_WEST
                playerYaw >= 157.5 || playerYaw < -157.5 -> org.bukkit.block.BlockFace.NORTH
                playerYaw >= -157.5 && playerYaw < -112.5 -> org.bukkit.block.BlockFace.NORTH_EAST
                playerYaw >= -112.5 && playerYaw < -67.5 -> org.bukkit.block.BlockFace.EAST
                playerYaw >= -67.5 && playerYaw < -22.5 -> org.bukkit.block.BlockFace.SOUTH_EAST
                else -> org.bukkit.block.BlockFace.SOUTH
            }
            rotatable.rotation = rotation
            signBlock.blockData = rotatable
            updateSignText(sign, signData)
            sign.persistentDataContainer.set(LIKE_SIGN_ENTITY_KEY, PersistentDataType.STRING, signData.uuid.toString())
            sign.update()
        } else {
            signBlock.type = Material.PALE_OAK_WALL_SIGN
            val sign = signBlock.state as? Sign ?: return
            val directional = signBlock.blockData as? Directional ?: return
            directional.facing = blockFace
            signBlock.blockData = directional
            updateSignText(sign, signData)
            sign.persistentDataContainer.set(LIKE_SIGN_ENTITY_KEY, PersistentDataType.STRING, signData.uuid.toString())
            sign.update()
        }
    }

    fun updateSignText(sign: Sign, signData: LikeSignData) {
        sign.getSide(org.bukkit.block.sign.Side.FRONT).line(0, Component.text("§3[Like]"))
        sign.getSide(org.bukkit.block.sign.Side.FRONT).line(1, Component.text(signData.title))
        sign.getSide(org.bukkit.block.sign.Side.FRONT).line(2, Component.text(signData.description))
        sign.getSide(org.bukkit.block.sign.Side.FRONT).line(3, Component.text("§8${signData.likeCount()} Liked"))
        sign.update()
    }

    internal fun createHologram(signData: LikeSignData, block: Block) {
        val world = block.world
        val loc = Location(world, block.x + 0.5, block.y + 1.5, block.z + 0.5)

        val textDisplay = world.spawn(loc, TextDisplay::class.java) { display ->
            display.text(createHologramText(signData))
            display.isShadowed = true
            display.isSeeThrough = false
            display.backgroundColor = org.bukkit.Color.fromARGB(128, 0, 0, 0)
            display.billboard = org.bukkit.entity.Display.Billboard.CENTER
            display.transformation = Transformation(
                Vector3f(0f, 0f, 0f),
                AxisAngle4f(0f, 0f, 0f, 1f),
                Vector3f(1f, 1f, 1f),
                AxisAngle4f(0f, 0f, 0f, 1f)
            )
            display.persistentDataContainer.set(LIKE_SIGN_ENTITY_KEY, PersistentDataType.STRING, signData.uuid.toString())
        }

        val interactionLoc = Location(world, block.x + 0.5, block.y + 1.0, block.z + 0.5)
        val interaction = world.spawn(interactionLoc, Interaction::class.java) { inter ->
            inter.interactionWidth = 1.0f
            inter.interactionHeight = 1.5f
            inter.isResponsive = true
            inter.persistentDataContainer.set(LIKE_SIGN_ENTITY_KEY, PersistentDataType.STRING, signData.uuid.toString())
        }
    }

    fun updateHologramText(signData: LikeSignData, world: org.bukkit.World) {
        val entities = world.entities.filterIsInstance<TextDisplay>().filter { entity ->
            entity.persistentDataContainer.get(LIKE_SIGN_ENTITY_KEY, PersistentDataType.STRING) == signData.uuid.toString()
        }
        
        entities.forEach { display ->
            display.text(createHologramText(signData))
        }
    }

    private fun createHologramText(signData: LikeSignData): Component {
        return Component.text()
            .append(Component.text(signData.title, NamedTextColor.WHITE, TextDecoration.BOLD))
            .append(Component.newline())
            .append(Component.text(signData.description, NamedTextColor.GRAY))
            .append(Component.newline())
            .append(Component.text("§c❤ ${signData.likeCount()}"))
            .build()
    }

    fun removeSign(signData: LikeSignData, worldData: WorldData, player: Player? = null): Boolean {
        val world = Bukkit.getWorld("my_world.${worldData.uuid}") ?: return false

        when (signData.displayType) {
            LikeSignDisplayType.SIGN -> {
                val signBlock = world.getBlockAt(signData.blockX, signData.blockY, signData.blockZ)
                if (signBlock.type == Material.PALE_OAK_WALL_SIGN || signBlock.type == Material.PALE_OAK_SIGN) {
                    signBlock.type = Material.AIR
                    
                    if (player != null) {
                        val item = me.awabi2048.myworldmanager.util.CustomItem.LIKE_SIGN.create(plugin.languageManager, player)
                        player.inventory.addItem(item).values.forEach { dropped ->
                            player.world.dropItemNaturally(player.location, dropped)
                        }
                    }
                }
            }
            LikeSignDisplayType.HOLOGRAM -> {
                val entities = world.entities.filter { entity ->
                    entity.persistentDataContainer.get(LIKE_SIGN_ENTITY_KEY, PersistentDataType.STRING) == signData.uuid.toString()
                }
                entities.forEach { it.remove() }
                
                if (player != null) {
                    val item = me.awabi2048.myworldmanager.util.CustomItem.LIKE_SIGN.create(plugin.languageManager, player)
                    player.inventory.addItem(item).values.forEach { dropped ->
                        player.world.dropItemNaturally(player.location, dropped)
                    }
                }
            }
        }

        worldData.likeSigns.removeIf { it.uuid == signData.uuid }
        plugin.worldConfigRepository.save(worldData)
        
        return true
    }

    fun findSignByUuid(worldData: WorldData, uuid: UUID): LikeSignData? {
        return worldData.likeSigns.find { it.uuid == uuid }
    }

    fun findSignAtLocation(worldData: WorldData, block: Block): LikeSignData? {
        return worldData.likeSigns.find { sign ->
            sign.blockX == block.x && sign.blockY == block.y && sign.blockZ == block.z
        }
    }

    fun findSignFromSignBlock(worldData: WorldData, signBlock: Block): LikeSignData? {
        val sign = signBlock.state as? Sign ?: return null
        val uuidStr = sign.persistentDataContainer.get(LIKE_SIGN_ENTITY_KEY, PersistentDataType.STRING) ?: return null
        return findSignByUuid(worldData, UUID.fromString(uuidStr))
    }

    fun findSignFromEntity(worldData: WorldData, entity: org.bukkit.entity.Entity): LikeSignData? {
        val uuidStr = entity.persistentDataContainer.get(LIKE_SIGN_ENTITY_KEY, PersistentDataType.STRING) ?: return null
        return findSignByUuid(worldData, UUID.fromString(uuidStr))
    }

    fun isWorldMember(worldData: WorldData, playerUuid: UUID): Boolean {
        return worldData.owner == playerUuid ||
               worldData.members.contains(playerUuid) ||
               worldData.moderators.contains(playerUuid)
    }

    fun canPlayerLike(worldData: WorldData, playerUuid: UUID): Boolean {
        return !isWorldMember(worldData, playerUuid)
    }

    fun refreshSignDisplay(signData: LikeSignData, worldData: WorldData) {
        val world = Bukkit.getWorld("my_world.${worldData.uuid}") ?: return
        
        when (signData.displayType) {
            LikeSignDisplayType.SIGN -> {
                val signBlock = world.getBlockAt(signData.blockX, signData.blockY, signData.blockZ)
                val attachBlock = signBlock.getRelative(BlockFace.valueOf(signData.blockFace))
                val sign = attachBlock.state as? Sign ?: return
                updateSignText(sign, signData)
            }
            LikeSignDisplayType.HOLOGRAM -> {
                updateHologramText(signData, world)
            }
        }
    }

    fun spawnHologramsForWorld(worldData: WorldData) {
        val world = Bukkit.getWorld("my_world.${worldData.uuid}") ?: return
        
        for (signData in worldData.likeSigns) {
            if (signData.displayType == LikeSignDisplayType.HOLOGRAM) {
                val block = world.getBlockAt(signData.blockX, signData.blockY, signData.blockZ)
                
                val existingDisplay = world.entities.filterIsInstance<TextDisplay>().any { 
                    it.persistentDataContainer.get(LIKE_SIGN_ENTITY_KEY, PersistentDataType.STRING) == signData.uuid.toString()
                }
                val existingInteraction = world.entities.filterIsInstance<Interaction>().any { 
                    it.persistentDataContainer.get(LIKE_SIGN_ENTITY_KEY, PersistentDataType.STRING) == signData.uuid.toString()
                }
                
                if (!existingDisplay || !existingInteraction) {
                    createHologram(signData, block)
                }
            }
        }
    }
}
