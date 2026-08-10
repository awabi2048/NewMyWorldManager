package me.awabi2048.myworldmanager.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FavoriteGroupInviteEligibilityTest {
    private val eligible = FavoriteGroupInviteCandidateEligibility(
        isSender = false,
        visibleToSender = true,
        receptionEnabled = true,
        allowedByPolicy = true,
        alreadyMember = false,
        alreadyAtDestination = false,
        alreadyPending = false,
    )

    @Test
    fun `同じ現在ワールドの受信可能プレイヤーは対象になる`() {
        assertTrue(favoriteGroupInviteCandidateIsEligible(eligible))
    }

    @Test
    fun `個人設定と重複状態はそれぞれ対象外にする`() {
        assertFalse(favoriteGroupInviteCandidateIsEligible(eligible.copy(receptionEnabled = false)))
        assertFalse(favoriteGroupInviteCandidateIsEligible(eligible.copy(alreadyMember = true)))
        assertFalse(favoriteGroupInviteCandidateIsEligible(eligible.copy(alreadyAtDestination = true)))
        assertFalse(favoriteGroupInviteCandidateIsEligible(eligible.copy(alreadyPending = true)))
    }

    @Test
    fun `送信者本人と不可視対象とポリシー拒否対象を除外する`() {
        assertFalse(favoriteGroupInviteCandidateIsEligible(eligible.copy(isSender = true)))
        assertFalse(favoriteGroupInviteCandidateIsEligible(eligible.copy(visibleToSender = false)))
        assertFalse(favoriteGroupInviteCandidateIsEligible(eligible.copy(allowedByPolicy = false)))
    }
}
