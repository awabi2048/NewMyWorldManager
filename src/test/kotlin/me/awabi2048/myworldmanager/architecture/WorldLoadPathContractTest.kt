package me.awabi2048.myworldmanager.architecture

import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 既存ワールドのロード経路が状態診断を迂回しないことを検証します。 */
class WorldLoadPathContractTest {
    private val portalSource = Path.of(
        "src/main/kotlin/me/awabi2048/myworldmanager/service/PortalManager.kt"
    ).readText()
    private val previewSource = Path.of(
        "src/main/kotlin/me/awabi2048/myworldmanager/session/PreviewSessionManager.kt"
    ).readText()
    private val adminCommandSource = Path.of(
        "src/main/kotlin/me/awabi2048/myworldmanager/listener/AdminCommandListener.kt"
    ).readText()

    @Test
    fun `ポータルは共通ロード診断を通す`() {
        assertTrue("worldService.loadWorldByKey" in portalSource)
        assertFalse("WorldCreator" in portalSource)
    }

    @Test
    fun `テンプレートプレビューは既存ワールドを直接ロードしない`() {
        assertTrue("worldService.loadWorldByKey" in previewSource)
        assertFalse("WorldCreator" in previewSource)
    }

    @Test
    fun `内部ポータルはテレポート完了後に成功状態を更新する`() {
        val callback = portalSource.indexOf("afterTeleported = {")
        val cooldown = portalSource.indexOf("warpCooldowns[player.uniqueId] =", callback)
        val successMessage = portalSource.indexOf("messages.portal_warped", callback)

        assertTrue(callback >= 0)
        assertTrue(cooldown > callback)
        assertTrue(successMessage > callback)
    }

    @Test
    fun `テンプレート修復の新規生成はMISSING判定後だけ実行する`() {
        val repair = adminCommandSource.indexOf("private fun performRepairTemplates")
        val missingCheck = adminCommandSource.indexOf(
            "resolution.state == WorldDirectoryState.MISSING",
            repair
        )
        val creator = adminCommandSource.indexOf("Bukkit.createWorld", repair)

        assertTrue(repair >= 0)
        assertTrue(missingCheck > repair)
        assertTrue(creator > missingCheck)
    }
}
