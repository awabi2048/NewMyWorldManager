package me.awabi2048.myworldmanager.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FavoriteGroupInvitePresentationTest {
    @Test
    fun `Java版は人数ホバーとクリック導線を表示する`() {
        val presentation = favoriteGroupInvitePresentation(isBedrock = false)
        assertTrue(presentation.senderCancelLine)
        assertTrue(presentation.interactiveRecipientAction)
        assertTrue(presentation.countHover)
    }

    @Test
    fun `統合版はホバーと送信者取消行を表示しない`() {
        val presentation = favoriteGroupInvitePresentation(isBedrock = true)
        assertFalse(presentation.senderCancelLine)
        assertFalse(presentation.interactiveRecipientAction)
        assertFalse(presentation.countHover)
    }
}
