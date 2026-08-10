package me.awabi2048.myworldmanager.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * お気に入り登録時刻の保存形式を一元管理します。
 *
 * 旧データの日付だけの値は、その日の開始時刻として扱います。これにより、過去データを失わずに
 * 新しい時刻精度の並び順へ移行できます。
 */
object FavoriteRegistrationTimestamp {
    private val outputFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    fun now(): String = format(LocalDateTime.now())

    fun format(value: LocalDateTime): String = value.withNano(0).format(outputFormatter)

    fun normalize(stored: String): String {
        parseDateTime(stored)?.let { return format(it) }
        return runCatching { LocalDate.parse(stored).atStartOfDay() }
            .map(::format)
            .getOrElse { stored }
    }

    fun sortValue(stored: String): LocalDateTime =
        parseDateTime(normalize(stored)) ?: LocalDateTime.MAX

    private fun parseDateTime(value: String): LocalDateTime? = try {
        LocalDateTime.parse(value, outputFormatter)
    } catch (_: DateTimeParseException) {
        null
    }
}
