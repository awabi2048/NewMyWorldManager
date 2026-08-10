package me.awabi2048.myworldmanager.service

import me.awabi2048.myworldmanager.migration.WorldDirectoryState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WorldLoadDirectoryPolicyTest {
    @Test
    fun `CURRENTだけをロード可能とする`() {
        assertNull(WorldLoadDirectoryPolicy.rejectionFor(WorldDirectoryState.CURRENT))
        assertEquals(
            WorldLoadFailure.MIGRATION_REQUIRED,
            WorldLoadDirectoryPolicy.rejectionFor(WorldDirectoryState.LEGACY)
        )
        assertEquals(
            WorldLoadFailure.DIRECTORY_CONFLICT,
            WorldLoadDirectoryPolicy.rejectionFor(WorldDirectoryState.CONFLICT)
        )
        assertEquals(
            WorldLoadFailure.DIRECTORY_MISSING,
            WorldLoadDirectoryPolicy.rejectionFor(WorldDirectoryState.MISSING)
        )
        assertEquals(
            WorldLoadFailure.DIRECTORY_UNSAFE,
            WorldLoadDirectoryPolicy.rejectionFor(WorldDirectoryState.UNSAFE)
        )
    }
}
