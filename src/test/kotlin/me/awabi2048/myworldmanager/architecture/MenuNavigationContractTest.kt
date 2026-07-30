package me.awabi2048.myworldmanager.architecture

import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MenuNavigationContractTest {
    private val guiRoot = Path.of("src/main/kotlin/me/awabi2048/myworldmanager/gui")
    private val pluginSource = Path.of("src/main/kotlin/me/awabi2048/myworldmanager/MyWorldManager.kt")

    @Test
    fun `履歴を持つ子画面の戻る操作はRuntimeのBackへ委譲する`() {
        BACK_SCREENS.forEach { fileName ->
            val source = guiRoot.resolve(fileName).readText()
            val body = runCatching { functionBody(source, "back") }.getOrNull()
            assertTrue(
                body?.contains("MenuUpdate.Back") == true || "MenuInteraction.Back" in source,
                "$fileName の戻る操作がRuntimeの履歴へ委譲されていません",
            )
            assertFalse(
                body?.contains("MenuUpdate.Close") == true,
                "$fileName の戻る操作で履歴を破棄するCloseを返しています",
            )
        }
    }

    @Test
    fun `申請項目から確認画面を開いた後に新しい画面を閉じない`() {
        val source = guiRoot.resolve("PendingInteractionGui.kt").readText()
        val body = functionBody(source, "openEntry")
        assertTrue("MenuUpdate.None" in body)
        assertFalse("MenuUpdate.Close" in body)
    }

    @Test
    fun `テンプレート作成の取消は管理画面の履歴へ戻る`() {
        val source = guiRoot.resolve("TemplateWizardGui.kt").readText()
        val body = functionBody(source, "cancel")
        assertTrue("MenuUpdate.Back" in body)
        assertFalse("MenuUpdate.Close" in body)
    }

    @Test
    fun `Runtimeの全ownerを無効化時に解除する`() {
        val source = pluginSource.readText()
        assertTrue("""unregisterOwner("mwm")""" in source)
        assertTrue("""unregisterOwner("myworldmanager")""" in source)
    }

    @Test
    fun `アイコン選択時だけプレイヤーインベントリを選択モードにする`() {
        val source = guiRoot.resolve("WorldSettingsGui.kt").readText()
        assertTrue("""id = RUNTIME_SELECTION_ROUTE""" in source)
        assertTrue("enableRuntimeIconSelection" in source)
        assertTrue("disableRuntimeIconSelection" in source)
    }

    @Test
    fun `単独起動可能な一覧は戻る表示時だけ履歴へ積む`() {
        listOf("InviteGui.kt", "MeetGui.kt", "VisitWorldGui.kt").forEach { fileName ->
            val source = guiRoot.resolve(fileName).readText()
            assertTrue(
                "runtime.navigate(player, route)" in source,
                "$fileName に親画面からのNavigate経路がありません",
            )
            assertTrue(
                "runtime.open(player, route)" in source,
                "$fileName に単独起動用のRoot経路がありません",
            )
        }
    }

    @Test
    fun `dialog transitions do not return a destructive inventory update`() {
        val wizard = guiRoot.resolve("TemplateWizardGui.kt").readText()
        listOf("name", "description").forEach { function ->
            val body = functionBody(wizard, function)
            assertTrue("MenuUpdate.None" in body)
            assertFalse("MenuUpdate.Close" in body)
            assertFalse("runTask" in body)
        }

        val discovery = guiRoot.resolve("DiscoveryGui.kt").readText()
        val sortBody = functionBody(discovery, "sort")
        assertTrue("MenuUpdate.None" in sortBody)
        assertFalse("MenuUpdate.Close" in sortBody)
        assertFalse("runTask" in sortBody)
    }

    @Test
    fun `confirmation cancellation pops one runtime history entry`() {
        val source = guiRoot.resolve("ConfirmationMenuGui.kt").readText()
        val body = functionBody(source, "cancel")
        assertTrue("return session.onCancel()" in body)
        assertFalse("MenuUpdate.Close" in body)
        assertTrue("onCancel: () -> MenuActionResult = { MenuActionResult.Success(MenuUpdate.Back) }" in source)
    }

    @Test
    fun `world settings child menus do not manually push before runtime navigation`() {
        val listener = Path.of(
            "src/main/kotlin/me/awabi2048/myworldmanager/listener/WorldSettingsListener.kt",
        ).readText()

        listOf(
            "openExpansionMethodSelection(",
            "openMemberManagement(",
            "openVisitorManagement(",
            "openCriticalSettings(",
            "openPortalManagement(",
            "environmentGui.open(",
        ).forEach { childOpen ->
            listener.indices
                .filter { listener.startsWith(childOpen, it) }
                .forEach { callIndex ->
                    val preceding = listener.substring(maxOf(0, callIndex - 300), callIndex)
                    assertFalse(
                        "mwmMenuRoutes.pushWorldSettings(" in preceding,
                        "Runtime子画面の直前で履歴を二重追加しています: $childOpen",
                    )
                }
        }
    }

    @Test
    fun `inventory to dialog transitions preserve runtime history and avoid synthetic open sounds`() {
        val listener = Path.of(
            "src/main/kotlin/me/awabi2048/myworldmanager/listener/WorldSettingsListener.kt",
        ).readText()
        listOf(
            "showWorldInfoDialog(player, worldData)",
            "showMemberInviteDialog(player, forceAddMode)",
            "showTagEditorDialog(player, worldData)",
            "AnnouncementDialogManager.showAnnouncementEditDialog(player, worldData)",
        ).forEach { dialogCall ->
            val callIndex = listener.indexOf(dialogCall)
            assertTrue(callIndex >= 0, "Dialog call was not found: $dialogCall")
            val precedingTransition = listener.substring(maxOf(0, callIndex - 300), callIndex)
            assertFalse(
                "getMenuRuntimeService().close(player)" in precedingTransition,
                "Runtime history is cleared immediately before $dialogCall",
            )
        }
        assertTrue("MenuUpdate.Replace(" in listener)

        val announcement = guiRoot.resolve("AnnouncementDialogManager.kt").readText()
        assertFalse("playMenuOpen" in announcement)
    }

    @Test
    fun `一時画面とウィザード終了はRuntimeの経路状態を尊重する`() {
        val visit = guiRoot.resolve("VisitGui.kt").readText()
        assertTrue("returnToWorld != null" in visit)
        assertTrue("runtime.navigate(player, targetRoute)" in visit)
        assertTrue("runtime.open(player, targetRoute)" in visit)

        val wizard = guiRoot.resolve("TemplateWizardGui.kt").readText()
        assertTrue("onClose =" in wizard)
        assertTrue("currentRoute(context.player)" in wizard)
    }

    private fun functionBody(source: String, name: String): String {
        val start = source.indexOf("private fun $name").takeIf { it >= 0 }
            ?: source.indexOf("fun $name")
        require(start >= 0) { "$name が見つかりません" }
        val bodyStart = source.indexOf('{', start)
        require(bodyStart >= 0) { "$name の本体が見つかりません" }
        var depth = 0
        for (index in bodyStart until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(bodyStart, index + 1)
                }
            }
        }
        error("$name の本体が閉じられていません")
    }

    private companion object {
        val BACK_SCREENS = listOf(
            "AdminPortalGui.kt",
            "EnvironmentGui.kt",
            "FavoriteGui.kt",
            "InviteGui.kt",
            "MeetGui.kt",
            "PendingInteractionGui.kt",
            "DiscoveryGui.kt",
            "VisitGui.kt",
            "WorldGui.kt",
            "VisitWorldGui.kt",
        )
    }
}
