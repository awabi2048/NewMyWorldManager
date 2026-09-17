package me.awabi2048.myworldmanager.repository

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * 旧ポータル形式が永久に FAILED となって移行できなくなる回帰を防ぎます。
 * 版数判定を隔離件数より先に行い、旧版数・旧キー残存を PENDING として移行経路へ載せる契約です。
 */
class PortalMigrationStatusContractTest {
    private val source: String by lazy {
        Path.of(
            "src/main/kotlin/me/awabi2048/myworldmanager/repository/PortalRepository.kt",
        ).readText()
    }

    @Test
    fun `current schema version covers legacy world keys`() {
        assertTrue(
            "const val CURRENT_SCHEMA_VERSION = 2" in source,
            "旧形式（location.world）も config_version: 1 を持つため、現行版数は 2 である必要があります",
        )
    }

    @Test
    fun `status prioritizes pending migration over quarantined count`() {
        val pendingIndex = source.indexOf("scan.configVersion < CURRENT_SCHEMA_VERSION")
        val quarantinedIndex = source.indexOf("scan.quarantined > 0")
        assertTrue(pendingIndex >= 0)
        assertTrue(quarantinedIndex >= 0)
        assertTrue(
            pendingIndex < quarantinedIndex,
            "旧版数は隔離件数より先に PENDING 判定される必要があります",
        )
    }

    @Test
    fun `migration converts legacy keys instead of only reparsing`() {
        assertTrue(
            "PortalYamlMigration.migrateSection" in source,
            "移行処理は旧キー変換を経由する必要があります",
        )
    }
}
