package me.awabi2048.myworldmanager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PointBillingSeparationSourceContractTest {

    @Test
    void creationRequestDistinguishesNoBillingFromZeroCost() throws Exception {
        String api = read("src/main/kotlin/me/awabi2048/myworldmanager/api/service/ApiWorldService.kt");
        String service = read("src/main/kotlin/me/awabi2048/myworldmanager/service/WorldService.kt");

        assertTrue(api.contains("enum class WorldPointBillingMode"));
        assertTrue(api.contains("val billingMode: WorldPointBillingMode"));
        assertTrue(service.contains("billingMode == WorldPointBillingMode.NONE"));
        assertTrue(service.contains("cumulativePoints = cost"));
        assertTrue(service.contains("finalizeWorldCreation"));
    }

    @Test
    void dialogDoesNotMutatePointsAndPreviewReturnsThroughProviderBoundary() throws Exception {
        String dialog = read("src/main/kotlin/me/awabi2048/myworldmanager/gui/CreationDialogManager.kt");
        String preview = read("src/main/kotlin/me/awabi2048/myworldmanager/session/PreviewSessionManager.kt");

        assertFalse(dialog.contains("stats.worldPoint -= cost"));
        assertTrue(dialog.contains("session.billingMode"));
        assertTrue(preview.contains("plugin.creationGui.openConfirmation(player, creationSession)"));
        assertFalse(preview.contains("CreationDialogManager.showConfirmationDialog"));
    }

    @Test
    void seedCreationIsIndependentFromSlotExpansionCapability() throws Exception {
        String gui = read("src/main/kotlin/me/awabi2048/myworldmanager/gui/CreationGui.kt");
        String listener = read("src/main/kotlin/me/awabi2048/myworldmanager/listener/CreationGuiListener.kt");
        String seed = read("src/main/kotlin/me/awabi2048/myworldmanager/listener/WorldSeedListener.kt");

        assertFalse(gui.contains("if (MyWorldManagerApi.isWorldSlotSystemEnabled())"));
        assertFalse(listener.contains("if (!MyWorldManagerApi.isWorldSlotSystemEnabled()) return"));
        assertTrue(seed.contains("if (!MyWorldManagerApi.isWorldSlotSystemEnabled()) return false"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
