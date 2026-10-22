package me.awabi2048.myworldmanager.service

import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.isDirectory
import me.awabi2048.myworldmanager.api.service.ApiWorldSnapshot

/**
 * [ApiWorldSnapshot] の実装です。
 *
 * 取得時点のディレクトリ最終書き込み時刻を基準値として保持し、それ以降に新しい
 * 書き込み（mtime の更新）が検出されたら静止が崩れたと判定します。リージョン
 * ファイルはヘッダと本体を別タイミングで書き換えるため、複製中に一度でも書き込みが
 * 入ると複製物は世代不整合を含み得ます。
 */
internal class ManagedWorldSnapshot(
    override val worldUuid: UUID,
    override val worldDirectory: File,
    private val baselineModifiedMillis: Long,
    private val quiescentCheck: () -> Boolean,
    private val restoreAction: () -> Unit,
) : ApiWorldSnapshot {
    private val closed = AtomicBoolean(false)

    override val quiescent: Boolean
        get() = true

    override val failureReason: String?
        get() = null

    override fun isStillQuiescent(): Boolean {
        if (closed.get()) return false
        return quiescentCheck() && newestModifiedMillis(worldDirectory) <= baselineModifiedMillis
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            restoreAction()
        }
    }

    companion object {
        /**
         * ディレクトリ配下のファイルの最終更新時刻の最大値です。
         * 書き込み主体が残っていても、新規書き込みがなければ値は進みません。
         */
        fun newestModifiedMillis(dir: File): Long {
            if (!dir.isDirectory) return 0L
            return runCatching {
                Files.walk(dir.toPath()).use { stream ->
                    stream
                        .filter { !it.isDirectory() }
                        .mapToLong { runCatching { Files.getLastModifiedTime(it).toMillis() }.getOrDefault(0L) }
                        .max()
                        .orElse(0L)
                }
            }.getOrDefault(0L)
        }
    }
}

/** 静止を確立できなかった場合の失敗スナップショットです。複製には使えません。 */
internal class FailedWorldSnapshot(
    override val worldUuid: UUID,
    override val failureReason: String,
) : ApiWorldSnapshot {
    override val worldDirectory: File
        get() = File("")
    override val quiescent: Boolean
        get() = false

    override fun isStillQuiescent(): Boolean = false

    override fun close() {}
}
