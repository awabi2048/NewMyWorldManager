package me.awabi2048.myworldmanager.service

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PendingNotificationActionTest {
    @Test
    fun entireDisplayedActionRunsTheMatchingCommand() {
        val action = PendingNotificationAction.create(
            Component.text("/myworld confirm 0123"),
            Component.text("確認する"),
            "0123"
        )

        assertEquals(NamedTextColor.AQUA, action.color())
        assertEquals(TextDecoration.State.TRUE, action.decoration(TextDecoration.UNDERLINED))
        assertEquals(ClickEvent.runCommand("/myworld confirm 0123"), action.clickEvent())
    }
}
