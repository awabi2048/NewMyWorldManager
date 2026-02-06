package me.awabi2048.myworldmanager.util

import org.bukkit.Bukkit
import org.bukkit.entity.Player

object CCSystemUtil {
    private var ccSystemAvailable: Boolean? = null  // null = 未チェック, true/false = チェック済み
    private var ccSystemAPI: Any? = null

    /**
     * CC-Systemプラグインが利用可能かどうかを初期化する
     * プラグインの onEnable() メソッド内で呼び出すことを想定
     * または遅延初期化により初回利用時に自動実行される
     */
    fun initialize() {
        if (ccSystemAvailable != null) {
            Bukkit.getLogger().info("[CCSystemUtil] 既に初期化済みです")
            return
        }
        ccSystemAvailable = try {
            Bukkit.getLogger().info("[CCSystemUtil] CC-System初期化開始...")
            
            val ccSystemPlugin = Bukkit.getPluginManager().getPlugin("CC-System")
            Bukkit.getLogger().info("[CCSystemUtil] プラグイン検索結果: $ccSystemPlugin")
            
            if (ccSystemPlugin != null) {
                Bukkit.getLogger().info("[CCSystemUtil] CC-Systemプラグインが見つかりました。有効状態: ${ccSystemPlugin.isEnabled}")
            } else {
                Bukkit.getLogger().warning("[CCSystemUtil] CC-Systemプラグインが見つかりません")
            }
            
            if (ccSystemPlugin != null && ccSystemPlugin.isEnabled) {
                // CCSystem.getAPI() を呼び出す
                try {
                    Bukkit.getLogger().info("[CCSystemUtil] CCSystem クラスをロード中...")
                    val ccSystemClass = Class.forName("com.awabi2048.ccsystem.CCSystem")
                    Bukkit.getLogger().info("[CCSystemUtil] CCSystem クラスロード成功: $ccSystemClass")
                    
                    val getAPIMethod = ccSystemClass.getMethod("getAPI")
                    Bukkit.getLogger().info("[CCSystemUtil] getAPI メソッド取得成功: $getAPIMethod")
                    
                    ccSystemAPI = getAPIMethod.invoke(null)
                    Bukkit.getLogger().info("[CCSystemUtil] API呼び出し成功: $ccSystemAPI")
                    Bukkit.getLogger().info("[CCSystemUtil] APIクラス: ${ccSystemAPI?.javaClass?.name}")
                    
                    Bukkit.getLogger().info("[CCSystemUtil] CC-Systemプラグインが正常に検出され、APIが初期化されました")
                    true
                } catch (e: Exception) {
                    Bukkit.getLogger().warning("[CCSystemUtil] API取得エラー: ${e.message}")
                    e.printStackTrace()
                    false
                }
            } else {
                Bukkit.getLogger().info("[CCSystemUtil] CC-Systemプラグインが見つかりません（未導入または無効）")
                false
            }
        } catch (e: Exception) {
            Bukkit.getLogger().warning("[CCSystemUtil] CC-System初期化エラー: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * CC-Systemが利用可能かどうかを返す
     * 未初期化の場合は自動初期化される
     */
    fun isCCSystemAvailable(): Boolean {
        // 未初期化の場合は遅延初期化
        if (ccSystemAvailable == null) {
            Bukkit.getLogger().info("[CCSystemUtil] 遅延初期化を実行中...")
            initialize()
        }
        return ccSystemAvailable == true && ccSystemAPI != null
    }

    /**
     * プレイヤーの言語設定をCC-Systemから取得する
     * CC-Systemが利用不可の場合は null を返す
     *
     * @param player プレイヤー
     * @return 言語コード (例: "ja_jp", "en_us") または null
     */
    fun getPlayerLanguageFromCCSystem(player: Player): String? {
        if (!isCCSystemAvailable() || ccSystemAPI == null) {
            Bukkit.getLogger().warning("[CCSystemUtil] CC-Systemが利用不可です")
            return null
        }

        return try {
            val apiClass = ccSystemAPI!!.javaClass
            val getLanguageMethod = apiClass.getMethod("getPlayerLanguage", Player::class.java)
            val result = getLanguageMethod.invoke(ccSystemAPI, player) as? String
            Bukkit.getLogger().info("[CCSystemUtil] CC-Systemから言語取得成功 - Player: ${player.name}, Language: $result")
            result
        } catch (e: Exception) {
            Bukkit.getLogger().warning("[CCSystemUtil] CC-System言語取得エラー: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * CC-Systemがサポートしている言語一覧を取得する
     * CC-Systemが利用不可の場合は空のセットを返す
     *
     * @return サポートされている言語コードのセット
     */
    fun getSupportedLanguagesFromCCSystem(): Set<String> {
        if (!isCCSystemAvailable() || ccSystemAPI == null) return emptySet()

        return try {
            val apiClass = ccSystemAPI!!.javaClass
            val getSupportedMethod = apiClass.getMethod("getSupportedLanguages")
            @Suppress("UNCHECKED_CAST")
            val result = getSupportedMethod.invoke(ccSystemAPI) as? Set<String> ?: emptySet()
            Bukkit.getLogger().info("[CCSystemUtil] CC-Systemサポート言語: $result")
            result
        } catch (e: Exception) {
            Bukkit.getLogger().warning("[CCSystemUtil] サポート言語取得エラー: ${e.message}")
            emptySet()
        }
    }
}
