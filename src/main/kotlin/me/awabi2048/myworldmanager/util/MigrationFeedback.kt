package me.awabi2048.myworldmanager.util

import com.awabi2048.ccsystem.api.localization.generated.MyworldMessagesKeys
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

/**
 * 移行が必要なデータへの直接操作を、無言の例外ではなく操作者への明示的な拒否として返すための共通ヘルパです。
 *
 * - 対象ワールドの設定変更などワールド単位の操作 → [worldRequired]
 * - 個人設定や招待などワールドに紐づかない操作 → [operationRequired]
 * - 管理者ログイン時の全体通知 → [adminNotice]
 *
 * 判定は例外メッセージに `requires /mwm migration` / `requires migration` を含むかで統一します。
 * これにより PlayerStatsRepository / WorldConfigRepository / PendingInteractionRepository / PortalRepository /
 * SpotlightRepository / Chanpon 側ストレージ の全ゲートを横断的に扱えます。
 */
object MigrationFeedback {

    /** 移行起因の IllegalStateException かを判定します。 */
    fun isMigrationRequired(throwable: Throwable): Boolean {
        val message = throwable.message ?: return false
        return message.contains("requires /mwm migration") || message.contains("requires migration")
    }

    /** ワールド単位操作用の拒否結果を返します。 `MenuRuntimeServiceImpl` が自動でチャット通知します。 */
    fun worldRequired(player: Player, languageManager: LanguageManager): MenuActionResult.Rejected =
        MenuActionResult.Rejected(languageManager.getComponent(player, MyworldMessagesKeys.MESSAGES_MIGRATION_WORLD_REQUIRED))

    /** ワールドに紐づかない操作用の拒否結果を返します。 */
    fun operationRequired(player: Player, languageManager: LanguageManager): MenuActionResult.Rejected =
        MenuActionResult.Rejected(languageManager.getComponent(player, MyworldMessagesKeys.MESSAGES_MIGRATION_OPERATION_REQUIRED))

    /** 管理者向けログイン通知の Component を返します。 */
    fun adminNotice(player: Player, languageManager: LanguageManager): Component =
        languageManager.getComponent(player, MyworldMessagesKeys.MESSAGES_MIGRATION_ADMIN_NOTICE)

    /**
     * 保存失敗を捕捉し、移行起因なら適切な Rejected を返し、そうでなければ null を返して呼び出し側で再スローさせます。
     * @param worldScoped true ならワールド単位メッセージ、false なら操作単位メッセージ
     */
    fun handleSaveException(
        player: Player,
        languageManager: LanguageManager,
        exception: Throwable,
        worldScoped: Boolean,
    ): MenuActionResult.Rejected? {
        if (!isMigrationRequired(exception)) return null
        return if (worldScoped) worldRequired(player, languageManager) else operationRequired(player, languageManager)
    }
}
