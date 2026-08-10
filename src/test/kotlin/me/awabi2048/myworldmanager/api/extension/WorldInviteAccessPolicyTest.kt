package me.awabi2048.myworldmanager.api.extension

import me.awabi2048.myworldmanager.model.PublishLevel
import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.Material
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.util.UUID

class WorldInviteAccessPolicyTest {
    private val player = Proxy.newProxyInstance(
        Player::class.java.classLoader,
        arrayOf(Player::class.java),
    ) { _, method, _ -> error("Player method must not be called by the default invite policy: ${method.name}") } as Player

    @Test
    fun `非公開ワールドでも有効な招待は承認できる`() {
        val world = world(PublishLevel.PRIVATE)
        assertTrue(DefaultWorldAccessPolicy.canAcceptWorldInvite(player, world))
    }

    @Test
    fun `封鎖とアーカイブ後は招待を承認できない`() {
        assertFalse(DefaultWorldAccessPolicy.canAcceptWorldInvite(player, world(PublishLevel.LOCKED)))
        assertFalse(DefaultWorldAccessPolicy.canAcceptWorldInvite(player, world(PublishLevel.PUBLIC).copy(isArchived = true)))
    }

    private fun world(level: PublishLevel) = WorldData(
        uuid = UUID.randomUUID(),
        name = "test",
        description = "",
        icon = Material.GRASS_BLOCK,
        sourceWorld = "NORMAL",
        expireDate = "",
        owner = UUID.randomUUID(),
        publishLevel = level,
    )
}
