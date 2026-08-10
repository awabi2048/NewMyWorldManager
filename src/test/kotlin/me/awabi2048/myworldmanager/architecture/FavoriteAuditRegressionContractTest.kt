package me.awabi2048.myworldmanager.architecture

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

/** 監査で判明したJava/BE・コマンド入口・一覧ポリシーの乖離を再発させない契約です。 */
class FavoriteAuditRegressionContractTest {
    private val sourceRoot = Path.of("src/main/kotlin/me/awabi2048/myworldmanager")

    @Test
    fun `BE版myworldも統計をヘッダー中央に置き現在地を共通Factoryから描画する`() {
        val source = sourceRoot.resolve("ui/bedrock/BedrockMenuService.kt").readText()
        assertTrue("slot = PlayerWorldCapabilityContract.HEADER_CENTER_SLOT" in source)
        assertTrue("createStatsEntry(\n                    player,\n                    PlayerWorldCapabilityContract.HEADER_CENTER_SLOT" in source)
        assertTrue("plugin.currentWorldMenuElementFactory.create(player, footerStart + 4)" in source)
        assertFalse("createStatsEntry(player, footerStart + 4" in source)
    }

    @Test
    fun `visitで自分を指定した場合はmyworldコマンド入口へ委譲する`() {
        val source = sourceRoot.resolve("command/VisitCommand.kt").readText()
        assertTrue("Bukkit.dispatchCommand(player, \"myworld\")" in source)
        assertFalse("plugin.menuEntryRouter.openPlayerWorld(player)" in source)
    }

    @Test
    fun `自分の他ワールド一覧は第三者向け直接入場可能ポリシーを使う`() {
        val source = sourceRoot.resolve("gui/VisitGui.kt").readText()
        assertTrue(".canShowInGuestAccessibleWorldList(player, world)" in source)
        assertFalse(".canShowInVisitWorldList(player, world)" in source)
    }

    @Test
    fun `お気に入り詳細のNameのみ操作は案内Loreを非表示にする`() {
        val source = sourceRoot.resolve("gui/FavoriteMenuGui.kt").readText()
        assertTrue("interactionGuidance = GuiInteractionGuidance.HIDDEN" in source)
    }

    @Test
    fun `Java版myworldの保留通知は次ページと競合しない`() {
        val source = sourceRoot.resolve("gui/PlayerWorldGui.kt").readText()
        assertTrue("createPendingEntry(player, layout.size - 1)" in source)
        assertFalse("createPendingEntry(player, layout.size - 2)" in source)
    }
}
