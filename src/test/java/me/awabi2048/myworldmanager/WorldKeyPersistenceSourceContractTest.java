package me.awabi2048.myworldmanager;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldKeyPersistenceSourceContractTest {
    @Test
    void persistedWorldReferencesUseOnlyNamespacedWorldKeys() throws Exception {
        String worldData = read("src/main/kotlin/me/awabi2048/myworldmanager/model/WorldData.kt");
        String portal = read("src/main/kotlin/me/awabi2048/myworldmanager/repository/PortalRepository.kt");
        String preview = read("src/main/kotlin/me/awabi2048/myworldmanager/session/PreviewSessionManager.kt");

        assertTrue(worldData.contains("world_key"));
        assertFalse(worldData.contains("getString(\"world\")"));
        assertTrue(portal.contains("location.world_key"));
        assertTrue(portal.contains("target_world_key"));
        assertFalse(portal.contains("target_world\""));
        assertTrue(preview.contains("world_key"));
        assertFalse(preview.contains("getString(\"world\")"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
