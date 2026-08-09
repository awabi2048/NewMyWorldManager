package me.awabi2048.myworldmanager.listener

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WorldEditMoveCommandParserTest {
    @Test
    fun `defaults to one forward move`() {
        assertEquals(
            WorldEditMoveCommand(multiplier = 1, offsetToken = "forward"),
            WorldEditMoveCommandParser.parse("//move")
        )
    }

    @Test
    fun `parses multiplier and direction while ignoring switches`() {
        assertEquals(
            WorldEditMoveCommand(multiplier = 3, offsetToken = "north"),
            WorldEditMoveCommandParser.parse("//move -s 3 north -m stone")
        )
    }

    @Test
    fun `accepts an offset without an explicit multiplier`() {
        assertEquals(
            WorldEditMoveCommand(multiplier = 1, offsetToken = "1,2,-3"),
            WorldEditMoveCommandParser.parse("/worldedit:move 1,2,-3")
        )

        assertEquals(
            WorldEditMoveCommand(multiplier = 1, offsetToken = "-1,0,0"),
            WorldEditMoveCommandParser.parse("//move -1,0,0")
        )
    }

    @Test
    fun `rejects invalid multipliers and unrelated commands`() {
        assertNull(WorldEditMoveCommandParser.parse("//move 0 north"))
        assertNull(WorldEditMoveCommandParser.parse("//move -1 north"))
        assertNull(WorldEditMoveCommandParser.parse("//paste"))
    }
}
