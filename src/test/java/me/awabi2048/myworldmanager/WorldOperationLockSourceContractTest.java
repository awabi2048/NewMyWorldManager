package me.awabi2048.myworldmanager;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorldOperationLockSourceContractTest {
    @Test
    void coreOperationsShareOneWorldScopedCoordinator() throws Exception {
        String lock = Files.readString(Path.of(
            "src/main/kotlin/me/awabi2048/myworldmanager/api/service/WorldOperationLock.kt"
        ));
        String service = Files.readString(Path.of(
            "src/main/kotlin/me/awabi2048/myworldmanager/service/WorldService.kt"
        ));
        assertTrue(lock.contains("ConcurrentHashMap<UUID, WorldOperationLease>"));
        assertTrue(lock.contains("LOAD"));
        assertTrue(lock.contains("BACKUP"));
        assertTrue(lock.contains("RESTORE"));
        assertTrue(lock.contains("MIGRATE"));
        assertTrue(service.contains("WorldOperation.LOAD"));
        assertTrue(service.contains("WorldOperation.DELETE"));
        assertTrue(service.contains("WorldOperation.ARCHIVE"));
        assertTrue(service.contains("getActiveWorldOperation(worldUuid)"));
    }
}
