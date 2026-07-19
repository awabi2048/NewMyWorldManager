package me.awabi2048.myworldmanager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RuntimeSeparationSourceContractTest {

    @Test
    void expirationSortsRemainAvailableInMwm() throws Exception {
        String gui = read("src/main/kotlin/me/awabi2048/myworldmanager/gui/WorldGui.kt");
        String session = read("src/main/kotlin/me/awabi2048/myworldmanager/session/AdminGuiSession.kt");
        assertFalse(gui.contains("session.sortBy = AdminSortType.CREATED_DESC"));
        assertFalse(gui.contains("it != AdminSortType.EXPIRE_ASC"));
        assertFalse(session.contains("it != AdminSortType.EXPIRE_ASC"));
    }

    @Test
    void unmanagedWorldFlightIsNotMutated() throws Exception {
        String source = read("src/main/kotlin/me/awabi2048/myworldmanager/service/WorldEnvironmentService.kt");
        assertTrue(source.contains("findByWorldName(worldName) ?: return false"));
        assertFalse(source.contains("worldData?.allowFlight ?: true"));
    }

    @Test
    void providerFailuresAreIsolated() throws Exception {
        String source = read("src/main/kotlin/me/awabi2048/myworldmanager/api/MyWorldManagerApi.kt");
        assertTrue(source.contains("runCatching { provider.open(player, session) }"));
        assertTrue(source.contains("getOrDefault(false)"));
    }

    @Test
    void externalMemberSourceCarriesProviderMetadata() throws Exception {
        String enums = read("src/main/kotlin/me/awabi2048/myworldmanager/api/event/EventEnums.kt");
        String event = read("src/main/kotlin/me/awabi2048/myworldmanager/api/event/MwmMemberAddedEvent.kt");
        assertTrue(enums.contains("EXTERNAL"));
        assertFalse(enums.contains("PASSWORD"));
        assertTrue(event.contains("val providerId: String?"));
        assertTrue(event.contains("val detail: String?"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
