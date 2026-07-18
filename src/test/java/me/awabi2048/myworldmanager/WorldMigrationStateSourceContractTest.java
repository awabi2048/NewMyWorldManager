package me.awabi2048.myworldmanager;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorldMigrationStateSourceContractTest {
    @Test
    void migrationApprovalAndFailureArePersisted() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/kotlin/me/awabi2048/myworldmanager/migration/WorldMigrationService.kt"
        ), StandardCharsets.UTF_8);
        assertTrue(source.contains("world-migration-state.yml"));
        assertTrue(source.contains("persistState(uuid, \"APPROVED\")"));
        assertTrue(source.contains("persistState(uuid, \"FAILED\")"));
        assertTrue(source.contains("persistState(uuid, \"COMMITTED\")"));
        assertTrue(source.contains("persistState(uuid, \"REJECTED\")"));
    }
}
