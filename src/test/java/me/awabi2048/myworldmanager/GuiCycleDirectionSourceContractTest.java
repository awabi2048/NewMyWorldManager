package me.awabi2048.myworldmanager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GuiCycleDirectionSourceContractTest {

    @Test
    void leftClickSelectsPreviousAndRightClickSelectsNext() throws Exception {
        String actions = Files.readString(
            Path.of("src/main/kotlin/me/awabi2048/myworldmanager/util/GuiItemFactory.kt"),
            StandardCharsets.UTF_8
        );
        String admin = Files.readString(
            Path.of("src/main/kotlin/me/awabi2048/myworldmanager/listener/AdminGuiListener.kt"),
            StandardCharsets.UTF_8
        );
        String discovery = Files.readString(
            Path.of("src/main/kotlin/me/awabi2048/myworldmanager/listener/DiscoveryListener.kt"),
            StandardCharsets.UTF_8
        );

        assertTrue(actions.contains("fun cyclePreviousNext("));
        assertTrue(actions.contains("\"lore.click.left\""));
        assertTrue(actions.contains("\"gui.common.action.previous\""));
        assertTrue(actions.contains("\"lore.click.right\""));
        assertTrue(actions.contains("\"gui.common.action.next\""));
        assertTrue(admin.contains("cycleSortType(player.uniqueId, event.isLeftClick)"));
        assertTrue(discovery.contains("reverse = !isBedrock && event.isLeftClick"));
        assertFalse(admin.contains("cycleSortType(player.uniqueId, event.isRightClick)"));
    }
}
