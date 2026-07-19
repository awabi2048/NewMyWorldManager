package me.awabi2048.myworldmanager.api.extension

interface WorldRuntimePolicy {
    fun getId(): String

    /** ワールドポイントの表示・決済・付与を運用上利用できるか。 */
    fun isWorldPointEconomyEnabled(): Boolean = true

    /** ワールドスロットとWorld Seedを運用上利用できるか。 */
    fun isWorldSlotSystemEnabled(): Boolean = true

    /** MWM標準の期限判定と自動アーカイブを実行するか。 */
    fun isExpirationArchiveEnabled(): Boolean = true

}

object DefaultWorldRuntimePolicy : WorldRuntimePolicy {
    override fun getId(): String = "myworldmanager.default_runtime"
}
