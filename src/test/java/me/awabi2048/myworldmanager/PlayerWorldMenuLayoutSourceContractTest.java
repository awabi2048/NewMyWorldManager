package me.awabi2048.myworldmanager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PlayerWorldMenuLayoutSourceContractTest {

    @Test
    void javaMenuUsesTheChanponThreePointFooterAndCompleteBackground() throws Exception {
        String source = Files.readString(
            Path.of("src/main/kotlin/me/awabi2048/myworldmanager/gui/PlayerWorldGui.kt"),
            StandardCharsets.UTF_8
        );

        assertTrue(source.contains("layout.actionSlot - 2, createCreationButton(player)"));
        assertTrue(source.contains("layout.actionSlot,"));
        assertTrue(source.contains("layout.actionSlot + 2,"));
        assertTrue(source.contains("GuiItemFactory.fillEmpty(inventory)"));
        assertFalse(source.contains("footerStart + 5"));
        assertFalse(source.contains("createPendingButton"));
    }

    @Test
    void pendingInteractionsRemainAvailableFromTheCenterPlayerHead() throws Exception {
        String guiSource = Files.readString(
            Path.of("src/main/kotlin/me/awabi2048/myworldmanager/gui/PlayerWorldGui.kt"),
            StandardCharsets.UTF_8
        );
        String listenerSource = Files.readString(
            Path.of("src/main/kotlin/me/awabi2048/myworldmanager/listener/PlayerWorldListener.kt"),
            StandardCharsets.UTF_8
        );
        String bedrockSource = Files.readString(
            Path.of("src/main/kotlin/me/awabi2048/myworldmanager/ui/bedrock/BedrockMenuService.kt"),
            StandardCharsets.UTF_8
        );

        assertTrue(guiSource.contains("getPendingCount(targetPlayerUuid)"));
        assertTrue(guiSource.contains("gui.player_world.pending_button.action"));
        assertTrue(listenerSource.contains("if (type == ItemTag.TYPE_GUI_PLAYER_STATS)"));
        assertTrue(listenerSource.contains("plugin.pendingInteractionGui.open("));
        assertFalse(listenerSource.contains("TYPE_GUI_PENDING_BUTTON"));
        assertTrue(bedrockSource.contains("if (pendingCount > 0) \"open_pending_interactions\" else \"noop\""));
        assertFalse(bedrockSource.contains("footerStart + 5"));
        assertFalse(bedrockSource.contains("createPendingButtonItem"));
    }
}
