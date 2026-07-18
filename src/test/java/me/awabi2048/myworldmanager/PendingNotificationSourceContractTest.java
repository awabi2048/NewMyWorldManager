package me.awabi2048.myworldmanager;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingNotificationSourceContractTest {
    @Test
    void notificationUsesTheDisplayedFourDigitCommandAsTheWholeClickAction() throws Exception {
        String source = Files.readString(
            Path.of("src/main/kotlin/me/awabi2048/myworldmanager/service/PendingNotificationService.kt"),
            StandardCharsets.UTF_8
        );
        assertTrue(source.contains("val command = \"/myworld confirm $actionCode\""));
        assertTrue(source.contains(".color(NamedTextColor.AQUA)"));
        assertTrue(source.contains(".decoration(TextDecoration.UNDERLINED, true)"));
        assertTrue(source.contains(".clickEvent(ClickEvent.runCommand(command))"));
    }

    @Test
    void oldMixedNotificationPathsAreRemoved() throws Exception {
        String root = Files.readString(
            Path.of("src/main/kotlin/me/awabi2048/myworldmanager/service/PendingDecisionManager.kt"),
            StandardCharsets.UTF_8
        );
        assertFalse(root.contains("sendPendingHint"));
        assertFalse(Files.exists(Path.of(
            "src/main/kotlin/me/awabi2048/myworldmanager/util/ClickableInviteMessageFactory.kt"
        )));
    }
}
