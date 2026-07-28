package me.awabi2048.myworldmanager.repository

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import java.io.File
import java.util.UUID

class PlayerLocationSnapshotRepositoryTest {
    @TempDir
    lateinit var temporaryFolder: File

    @Test
    fun `snapshot survives repository recreation and can be consumed`() {
        val playerUuid = UUID.randomUUID()
        val expected = PlayerLocationSnapshot(
            worldUuid = UUID.randomUUID(),
            x = 12.25,
            y = -31.5,
            z = 1048.75,
            yaw = 123.5f,
            pitch = -42.25f
        )

        PlayerLocationSnapshotRepository(temporaryFolder).save(playerUuid, expected)

        val recreated = PlayerLocationSnapshotRepository(temporaryFolder)
        assertEquals(expected, recreated.find(playerUuid))
        recreated.delete(playerUuid)
        assertNull(recreated.find(playerUuid))
    }
}
