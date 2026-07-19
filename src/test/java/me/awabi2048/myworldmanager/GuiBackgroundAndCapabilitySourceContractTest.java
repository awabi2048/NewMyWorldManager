package me.awabi2048.myworldmanager;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GuiBackgroundAndCapabilitySourceContractTest {

    @Test
    void visitMenusFillAllRemainingBackgroundSlots() throws Exception {
        String visit = Files.readString(Path.of(
            "src/main/kotlin/me/awabi2048/myworldmanager/gui/VisitGui.kt"
        ));
        String visitWorld = Files.readString(Path.of(
            "src/main/kotlin/me/awabi2048/myworldmanager/gui/VisitWorldGui.kt"
        ));

        assertTrue(visit.contains("GuiItemFactory.fillEmpty(inventory)"));
        assertTrue(visitWorld.contains("GuiItemFactory.fillEmpty(inventory)"));
    }

    @Test
    void bedrockPlayerWorldHonorsRuntimePointAndSlotCapabilities() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/kotlin/me/awabi2048/myworldmanager/ui/bedrock/BedrockMenuService.kt"
        ));

        assertTrue(source.contains("if (MyWorldManagerApi.isWorldPointEconomyEnabled())"));
        assertTrue(source.contains("if (MyWorldManagerApi.isWorldSlotSystemEnabled())"));
        assertTrue(source.contains(
            "MyWorldManagerApi.isWorldSlotSystemEnabled() && currentCreateCount >= maxSlot"
        ));
    }
}
