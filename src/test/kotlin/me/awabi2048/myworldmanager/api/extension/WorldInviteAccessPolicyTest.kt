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

    @Test
    fun `第三者向け一覧は表示条件と直接ワープ条件の両方を要求する`() {
        val world = world(PublishLevel.PUBLIC)
        val blocked = object : WorldAccessPolicy {
            override fun getId(): String = "blocked"
            override fun canShowInVisitWorldList(viewer: Player, worldData: WorldData): Boolean = true
            override fun canDirectWorldWarp(player: Player, worldData: WorldData, isMember: Boolean): Boolean = false
        }
        assertFalse(blocked.canShowInGuestAccessibleWorldList(player, world))

        val accessible = object : WorldAccessPolicy {
            override fun getId(): String = "accessible"
            override fun canShowInVisitWorldList(viewer: Player, worldData: WorldData): Boolean = true
            override fun canDirectWorldWarp(player: Player, worldData: WorldData, isMember: Boolean): Boolean = !isMember
        }
        assertTrue(accessible.canShowInGuestAccessibleWorldList(player, world))
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
