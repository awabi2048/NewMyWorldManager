package me.awabi2048.myworldmanager.util

import com.awabi2048.ccsystem.api.localization.LocalizationKey
import com.awabi2048.ccsystem.api.localization.generated.MyworldGuiSettingsKeys
import me.awabi2048.myworldmanager.api.extension.WorldSettingsActionRestriction

/**
 * 操作契約の制約理由(WorldSettingsActionRestriction)を、GUI警告ブロック用の言語キーへ
 * 写像する唯一の窓口です。
 *
 * GUIごとに「なぜ押せないか」の警告文を手組みすると、契約側の判定変更に表示が追従できず
 * 漏れが発生するため、理由→キーの対応はここで一元管理します。
 * when を網羅的に書くことで、理由の追加時に未対応箇所がコンパイルエラーとして検出されます。
 */
object WorldSettingsRestrictionMessages {
    fun warningKey(restriction: WorldSettingsActionRestriction): LocalizationKey<String> = when (restriction) {
        WorldSettingsActionRestriction.NOT_IN_TARGET_WORLD -> MyworldGuiSettingsKeys.GUI_SETTINGS_COMMON_MUST_BE_IN_WORLD
    }
}
