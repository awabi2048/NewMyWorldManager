package me.awabi2048.myworldmanager.service

import me.awabi2048.myworldmanager.model.PortalData
import me.awabi2048.myworldmanager.model.PortalType
import org.bukkit.util.BoundingBox
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class PortalContactPredicateTest {

    private fun blockPortal(x: Int = 10, y: Int = 64, z: Int = 20) = PortalData(
        worldKey = "minecraft:world",
        x = x,
        y = y,
        z = z,
        worldUuid = UUID.randomUUID(),
        ownerUuid = UUID.randomUUID()
    )

    // プレイヤーの標準AABB（幅0.6・高さ1.8）を足元位置から組み立てる。
    private fun playerBox(px: Double, py: Double, pz: Double) =
        BoundingBox(px - 0.3, py, pz - 0.3, px + 0.3, py + 1.8, pz + 0.3)

    @Test
    fun `standing on portal block detects contact`() {
        val portal = blockPortal()
        // フレーム直上に立つ: 足元 y=65、AABBは [65, 66.8]
        val box = playerBox(10.5, 65.0, 20.5)
        assertTrue(PortalManager.isPortalContact(box, portal))
    }

    @Test
    fun `standing still centered on portal detects contact`() {
        val portal = blockPortal()
        // 移動イベントがなくても同じ述語で検出できることを固定する
        val box = playerBox(10.5, 65.0, 20.5)
        assertTrue(PortalManager.isPortalContact(box, portal))
    }

    @Test
    fun `player falling through portal height detects contact`() {
        val portal = blockPortal()
        // 落下中にAABBが検知角柱と交差する帯域は十分ある
        val box = playerBox(10.5, 64.2, 20.5)
        assertTrue(PortalManager.isPortalContact(box, portal))
    }

    @Test
    fun `player below portal does not detect contact`() {
        val portal = blockPortal()
        // 頭頂が portalY 未満
        val box = playerBox(10.5, 62.0, 20.5)
        assertFalse(PortalManager.isPortalContact(box, portal))
    }

    @Test
    fun `player above portal detection range does not detect contact`() {
        val portal = blockPortal()
        // 足元が portalY+2 を超えた位置
        val box = playerBox(10.5, 66.5, 20.5)
        assertFalse(PortalManager.isPortalContact(box, portal))
    }

    @Test
    fun `player beside portal column does not detect contact`() {
        val portal = blockPortal()
        // X方向に検知断面とAABBが届かない位置
        val box = playerBox(11.6, 65.0, 20.5)
        assertFalse(PortalManager.isPortalContact(box, portal))
    }

    @Test
    fun `player partially overlapping portal column detects contact`() {
        val portal = blockPortal()
        // 0.3断面の外縁にAABBが僅かに掛かる位置
        val box = playerBox(10.9, 65.0, 20.5)
        assertTrue(PortalManager.isPortalContact(box, portal))
    }

    @Test
    fun `gate portal detects contact inside volume`() {
        val gate = PortalData(
            worldKey = "minecraft:world",
            x = 0, y = 60, z = 0,
            worldUuid = UUID.randomUUID(),
            ownerUuid = UUID.randomUUID(),
            type = PortalType.GATE,
            minX = 0, minY = 60, minZ = 0,
            maxX = 2, maxY = 62, maxZ = 2
        )
        assertTrue(PortalManager.isPortalContact(playerBox(1.5, 61.0, 1.5), gate))
    }

    @Test
    fun `gate portal does not detect contact outside volume`() {
        val gate = PortalData(
            worldKey = "minecraft:world",
            x = 0, y = 60, z = 0,
            worldUuid = UUID.randomUUID(),
            ownerUuid = UUID.randomUUID(),
            type = PortalType.GATE,
            minX = 0, minY = 60, minZ = 0,
            maxX = 2, maxY = 62, maxZ = 2
        )
        assertFalse(PortalManager.isPortalContact(playerBox(5.5, 61.0, 5.5), gate))
    }

    @Test
    fun `gate portal detects aabb overlap at volume edge`() {
        val gate = PortalData(
            worldKey = "minecraft:world",
            x = 0, y = 60, z = 0,
            worldUuid = UUID.randomUUID(),
            ownerUuid = UUID.randomUUID(),
            type = PortalType.GATE,
            minX = 0, minY = 60, minZ = 0,
            maxX = 2, maxY = 62, maxZ = 2
        )
        // 中心は体積外でもAABBが体積内へ食い込めば接触
        assertTrue(PortalManager.isPortalContact(playerBox(3.1, 61.0, 1.5), gate))
    }
}
