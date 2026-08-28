package me.awabi2048.myworldmanager.api.service

/**
 * 外部プラグインがMyWorldManagerのマクロ実行基盤を利用するための公開境界です。
 *
 * マクロ設定ファイルの所有者と実行主体は呼出側が決められるため、
 * 外部拡張は自分のデータフォルダに設定を置いたまま、MacroManagerの
 * プレースホルダー展開・コンソール実行だけを再利用できます。
 * 遅延が必要な場合は、コマンド文字列へCC-Systemの `delay` コマンドを記述します。
 */
fun interface ApiMacroService {
    fun execute(trigger: String, params: Map<String, String>)
}
