package me.awabi2048.myworldmanager.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FavoriteMenuLayoutTest {
    @Test
    fun `一覧のヘッダーとフッター役割は指定位置で競合しない`() {
        assertEquals(4, FavoriteMenuLayout.HEADER_CENTER_SLOT)
        listOf(45, 54).forEach { size ->
            val footer = FavoriteMenuLayout.footer(size)
            val slots = listOf(footer.otherWorlds, footer.toggleCurrent, footer.currentWorld, footer.tagFilter)
            assertEquals(slots.size, slots.toSet().size)
            assertEquals(size - 7, footer.otherWorlds)
            assertEquals(size - 6, footer.toggleCurrent)
            assertEquals(size - 5, footer.currentWorld)
        }
    }

    @Test
    fun `詳細画面は5行で4操作を中央寄りに配置する`() {
        assertEquals(45, FavoriteMenuLayout.DETAIL_SIZE)
        assertEquals(listOf(19, 21, 23, 25), FavoriteMenuLayout.DETAIL_ACTION_SLOTS)
        assertTrue(FavoriteMenuLayout.DETAIL_ACTION_SLOTS.all { it in 18..26 })
    }
}
