package me.awabi2048.myworldmanager.repository

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PendingInteractionOnlineStatePersistenceTest {
    @Test
    fun missingFieldUsesSafeOnlineDefaultForExistingRecords() {
        val config = YamlConfiguration()

        assertTrue(readTargetOnlineAtCreation(config, "entries.invite"))
    }

    @Test
    fun explicitOnlineStateRoundTripsThroughStorageShape() {
        val config = YamlConfiguration()

        writeTargetOnlineAtCreation(config, "entries.offline", false)
        writeTargetOnlineAtCreation(config, "entries.online", true)

        assertFalse(readTargetOnlineAtCreation(config, "entries.offline"))
        assertTrue(readTargetOnlineAtCreation(config, "entries.online"))
    }
}
