package me.awabi2048.myworldmanager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreationTransactionSourceContractTest {

    @Test
    void repositoriesUseVerifiedTemporaryFilesAndAtomicReplacement() throws Exception {
        String worlds = Files.readString(Path.of(
            "src/main/kotlin/me/awabi2048/myworldmanager/repository/WorldConfigRepository.kt"
        ));
        String players = Files.readString(Path.of(
            "src/main/kotlin/me/awabi2048/myworldmanager/repository/PlayerStatsRepository.kt"
        ));

        assertTrue(worlds.contains("StandardCopyOption.ATOMIC_MOVE"));
        assertTrue(worlds.contains("Temporary world data verification failed"));
        assertTrue(worlds.contains("throw IllegalStateException"));
        assertTrue(players.contains("StandardCopyOption.ATOMIC_MOVE"));
        assertTrue(players.contains("Temporary player stats verification failed"));
    }

    @Test
    void creationChargesAfterMetadataSaveAndRollsBackOnFailure() throws Exception {
        String service = Files.readString(Path.of(
            "src/main/kotlin/me/awabi2048/myworldmanager/service/WorldService.kt"
        ));
        String listener = Files.readString(Path.of(
            "src/main/kotlin/me/awabi2048/myworldmanager/listener/CreationGuiListener.kt"
        ));

        int metadataSave = service.indexOf("repository.save(worldData)");
        int pointCharge = service.indexOf("adjustWorldPoints(player.uniqueId, -cost)");
        int successEvent = service.indexOf("MwmWorldCreatedEvent(");
        assertTrue(metadataSave >= 0 && metadataSave < pointCharge);
        assertTrue(pointCharge < successEvent);
        assertTrue(service.contains("adjustWorldPoints(player.uniqueId, cost)"));
        assertTrue(service.contains("cleanupFailedCreatedWorld"));
        assertFalse(listener.contains("stats.worldPoint -= cost"));
    }

    @Test
    void addonManagedCreationUsesTheSameCommitPipeline() throws Exception {
        String service = Files.readString(Path.of(
            "src/main/kotlin/me/awabi2048/myworldmanager/service/WorldService.kt"
        ));
        String api = Files.readString(Path.of(
            "src/main/kotlin/me/awabi2048/myworldmanager/api/service/ApiWorldService.kt"
        ));
        assertTrue(api.contains("fun createManagedWorld(request: ManagedWorldCreationRequest)"));
        assertTrue(service.contains("fun createManagedWorld(request: ManagedWorldCreationRequest)"));
        assertTrue(service.contains("finalizeWorldCreation("));
        assertTrue(service.contains("cleanupFailedCreatedWorld(folderName"));
    }
}
