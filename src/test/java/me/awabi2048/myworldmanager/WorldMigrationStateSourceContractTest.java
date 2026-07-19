package me.awabi2048.myworldmanager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorldMigrationStateSourceContractTest {
    @Test
    void manualQueueMigrationIsPersistentAndRetryBounded() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/kotlin/me/awabi2048/myworldmanager/migration/WorldMigrationService.kt"
        ), StandardCharsets.UTF_8);
        assertTrue(source.contains("world-migration-state.yml"));
        assertTrue(source.contains("MigrationWorldStatus.WAITING"));
        assertTrue(source.contains("MigrationWorldStatus.RETRY"));
        assertTrue(source.contains("MigrationWorldStatus.COMPLETED"));
        assertTrue(source.contains("MigrationWorldStatus.FAILED"));
        assertTrue(source.contains("MAX_ATTEMPTS = 2"));
        assertTrue(source.contains("StandardCopyOption.ATOMIC_MOVE"));
        assertTrue(source.contains("WorldOperation.MIGRATE"));
        assertFalse(source.contains("APPROVED"));
        assertFalse(source.contains("REJECTED"));
    }
}
