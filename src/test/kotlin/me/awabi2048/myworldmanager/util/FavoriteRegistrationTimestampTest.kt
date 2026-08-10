package me.awabi2048.myworldmanager.util

import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FavoriteRegistrationTimestampTest {
    @Test
    fun `旧日付は当日00時として正規化する`() {
        assertEquals("2026-08-10T00:00:00", FavoriteRegistrationTimestamp.normalize("2026-08-10"))
    }

    @Test
    fun `時刻付き値は秒精度へ正規化する`() {
        assertEquals(
            "2026-08-10T12:34:56",
            FavoriteRegistrationTimestamp.normalize("2026-08-10T12:34:56.987"),
        )
    }

    @Test
    fun `並び替え値は古い日時ほど小さい`() {
        val old = FavoriteRegistrationTimestamp.sortValue("2026-08-09T23:59:59")
        val recent = FavoriteRegistrationTimestamp.sortValue("2026-08-10T00:00:00")
        assertTrue(old < recent)
        assertEquals(LocalDateTime.MAX, FavoriteRegistrationTimestamp.sortValue("invalid"))
    }
}
