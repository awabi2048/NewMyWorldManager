package me.awabi2048.myworldmanager.repository

import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/**
 * 型付きモデルへ変換できず、通常操作から隔離されたワールドデータです。
 * 隔離データは「未登録」ではなく、移行または管理者判断が必要な既存データとして扱います。
 */
data class QuarantinedWorldData(
    val uuid: UUID?,
    val fileName: String,
    val reason: String,
    val detectedAt: Instant,
    val worldName: String? = null,
    val owner: UUID? = null,
    val worldKey: String? = null,
    val customWorldName: String? = null,
    val contentHash: String? = null,
)

/** テンプレート単位で隔離された定義です。 */
data class QuarantinedTemplateData(
    val id: String,
    val reason: String,
    val detectedAt: Instant,
    val path: String? = null,
    val contentHash: String? = null,
)

/**
 * 管理者が隔離内容を確認してから移行するための、現在ファイルの指紋です。
 * 確認後に別プロセスや手作業で内容が変わった場合は、移行を中断します。
 */
object MigrationFileFingerprint {
    fun sha256(file: File): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    }.getOrNull()
}

enum class MetadataMigrationStatus {
    MIGRATED,
    ALREADY_CURRENT,
    NEEDS_INPUT,
    FAILED,
}

data class MetadataMigrationResult(
    val status: MetadataMigrationStatus,
    val message: String,
)
