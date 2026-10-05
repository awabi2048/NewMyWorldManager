package me.awabi2048.myworldmanager.ui

import com.awabi2048.ccsystem.CCSystem
import me.awabi2048.myworldmanager.MyWorldManager
import org.bukkit.entity.Player

/**
 * プレイヤーの実行プラットフォームを判定します。
 *
 * 主判定はCC-System経由のfloodgate APIで、プレイヤー名ではなくUUIDで判定するため
 * 複数プロキシで接頭辞設定が異なる構成でも正しく動作します。
 * floodgate 未導入・未連携環境への最終保険として、
 * bedrock.player_name_prefix による接頭辞判定のみフォールバックとして残します。
 *
 * 判定は呼び出しごとに最新状態で行います。キャッシュしないのは、
 * プロキシ環境で floodgate 側のプレイヤー登録完了が join 直後にずれ込む
 * ケースで誤判定を固定化しないためです。
 */
class PlayerPlatformResolver(private val plugin: MyWorldManager) {

    fun resolve(player: Player): PlayerPlatform = detect(player)

    fun isBedrock(player: Player): Boolean {
        return resolve(player) == PlayerPlatform.BEDROCK
    }

    private fun detect(player: Player): PlayerPlatform {
        if (CCSystem.getAPI().isBedrockPlayer(player)) {
            return PlayerPlatform.BEDROCK
        }

        val configuredPrefix =
            plugin.config.getString("bedrock.player_name_prefix", "")?.trim().orEmpty()
        if (configuredPrefix.isNotEmpty() && player.name.startsWith(configuredPrefix)) {
            return PlayerPlatform.BEDROCK
        }

        return PlayerPlatform.JAVA
    }
}
