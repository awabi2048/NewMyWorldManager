package me.awabi2048.myworldmanager.architecture

import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * モデレータのメンバー管理権限に関する契約テスト。
 *
 * メンバー管理画面への到達・ツール権限の編集はモデレータへ開放しつつ、
 * 役割変更・オーナー譲渡・除名はオーナー（管理フロー）限定を保つ。
 * 表示側のバインドと実行側の判定が同じ境界を使うことを保証します。
 */
class ModeratorMemberManagementContractTest {
    @Test
    fun `MANAGE_MEMBERSはオーナー限定ブランチへ含めない`() {
        val source = Path.of(
            "src/main/kotlin/me/awabi2048/myworldmanager/service/WorldSettingsActionService.kt",
        ).readText()
        val evaluate = functionBody(source, "evaluate")
        val permitted = evaluate
            .substringAfter("val permitted")
            .substringBefore("if (!permitted)")

        assertFalse("MANAGE_MEMBERS" in permitted, "MANAGE_MEMBERSをオーナー限定へ含めないこと")
        assertTrue("WorldSettingsAction.MANAGE_PORTALS -> owner || admin" in permitted)
        assertTrue("else -> owner || moderator || admin" in permitted)
    }

    @Test
    fun `メンバー管理ボタンはモデレータへも表示する`() {
        val source = Path.of(
            "src/main/kotlin/me/awabi2048/myworldmanager/gui/WorldSettingsGui.kt",
        ).readText()
        val memberButtonBlock = source
            .substringAfter("// スロット25: メンバー管理")
            .substringBefore("val memberContract")

        assertTrue("if (hasManagePermission) {" in memberButtonBlock)
        assertFalse("if (ownerActionsAllowed) {" in memberButtonBlock)
    }

    @Test
    fun `役割変更と除名と譲渡は実行時にもオーナー系チェックを行う`() {
        val source = Path.of(
            "src/main/kotlin/me/awabi2048/myworldmanager/listener/WorldSettingsListener.kt",
        ).readText()

        assertTrue("canManageMemberRoles" in functionBody(source, "handleMemberManagementMemberItemClick"))
        assertTrue("canManageMemberRoles" in functionBody(source, "handleMemberRemoveConfirmationRuntime"))
        assertTrue("canManageMemberRoles" in functionBody(source, "handleMemberTransferConfirmationRuntime"))
    }

    @Test
    fun `役割変更はイベントで通知して参加者同期へ戻す`() {
        val stateService = Path.of(
            "src/main/kotlin/me/awabi2048/myworldmanager/service/WorldSettingsStateService.kt",
        ).readText()
        val listener = Path.of(
            "src/main/kotlin/me/awabi2048/myworldmanager/listener/WorldPermissionPolicyListener.kt",
        ).readText()

        assertTrue("MwmMemberRoleChangedEvent" in functionBody(stateService, "toggleMemberRole"))
        assertTrue("MwmMemberRoleChangedEvent" in functionBody(stateService, "restoreMemberRole"))
        assertTrue("fun onMemberRoleChanged(event: MwmMemberRoleChangedEvent)" in listener)
        assertTrue("syncParticipants" in functionBody(listener, "onMemberRoleChanged"))
    }

    @Test
    fun `作成経路はプラットフォーム判定でDialogモードを決める`() {
        val source = Path.of(
            "src/main/kotlin/me/awabi2048/myworldmanager/api/MyWorldManagerApi.kt",
        ).readText()
        val body = functionBody(source, "prepareStandardWorldCreationRoute")

        assertTrue("isDialogMode = !plugin.playerPlatformResolver.isBedrock(player)" in body)
        assertFalse("isDialogMode = true" in body)
    }

    private fun functionBody(source: String, name: String): String {
        val start = source.indexOf("fun $name").takeIf { it >= 0 }
            ?: error("$name が見つかりません")
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
}
