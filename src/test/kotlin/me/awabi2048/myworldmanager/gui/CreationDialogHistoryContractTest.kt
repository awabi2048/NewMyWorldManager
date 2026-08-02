package me.awabi2048.myworldmanager.gui

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

class CreationDialogHistoryContractTest {
    private val source = Path.of(
        "src/main/kotlin/me/awabi2048/myworldmanager/gui/CreationDialogManager.kt",
    ).readText()

    @Test
    fun `creation dialog cancellation resumes the suspended inventory route`() {
        val seedDialog = source.substringAfter("fun showSeedInputDialog")
            .substringBefore("fun showSpawnLocationInputDialog")
        val spawnDialog = source.substringAfter("fun showSpawnLocationInputDialog")
            .substringBefore("fun showConfirmationDialog")

        assertResumesWithoutNavigating(seedDialog, "WorldCreationPhase.TYPE_SELECT", "openTypeSelection")
        assertResumesWithoutNavigating(spawnDialog, "WorldCreationPhase.CONFIRM", "openConfirmation")
    }

    @Test
    fun `name dialog returns to its suspended route except when returning to seed input`() {
        val nameDialog = source.substringAfter("fun showNameInputDialog")
            .substringBefore("fun showSeedInputDialog")
        val backFromName = source.substringAfter("private fun backFromName")
            .substringBefore("private fun applySeed")

        assertTrue(nameDialog.contains("backFromName(target, session)"))
        assertTrue(backFromName.contains("WorldCreationType.TEMPLATE"))
        assertTrue(backFromName.contains("WorldCreationType.RANDOM, null"))
        assertTrue(backFromName.contains("MenuUpdate.Resume"))
        assertTrue(backFromName.contains("WorldCreationType.SEED"))
        assertTrue(backFromName.contains("MenuUpdate.None"))
        assertFalse(backFromName.contains("openTemplateSelection"))
        assertFalse(backFromName.contains("openTemplateDetail"))
        assertFalse(backFromName.contains("openTypeSelection"))
    }

    private fun assertResumesWithoutNavigating(
        dialogSource: String,
        phase: String,
        navigationMethod: String,
    ) {
        val cancel = dialogSource.substringAfter("cancel = MenuDialogButton")
        assertTrue(cancel.contains("session.phase = $phase"))
        assertTrue(cancel.contains("MenuUpdate.Resume"))
        assertFalse(cancel.contains(navigationMethod))
    }
}
