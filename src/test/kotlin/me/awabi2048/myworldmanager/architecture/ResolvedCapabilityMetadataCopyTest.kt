package me.awabi2048.myworldmanager.architecture

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResolvedCapabilityMetadataCopyTest {
    @Test
    fun `admin capability presentation changes preserve resolution metadata`() {
        val source = Files.readString(
            Path.of("src/main/kotlin/me/awabi2048/myworldmanager/gui/AdminCommandGui.kt"),
        )

        assertTrue(source.contains("copyPreservingResolutionMetadata("))
        assertFalse(source.contains("requireExplicitActionSafety().copy("))
    }

    @Test
    fun `menu element changes preserve presentation semantics`() {
        val creation = Files.readString(
            Path.of("src/main/kotlin/me/awabi2048/myworldmanager/gui/CreationGui.kt"),
        )
        val settings = Files.readString(
            Path.of("src/main/kotlin/me/awabi2048/myworldmanager/gui/WorldSettingsGui.kt"),
        )

        assertTrue(creation.contains("getGuiElementService().menuUnavailable("))
        assertTrue(settings.contains("menuEntry(viewer, spec).copyWithPresentationSemantics("))
    }
}
