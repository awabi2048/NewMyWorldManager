package me.awabi2048.myworldmanager.util

import com.awabi2048.ccsystem.api.localization.LocalizationKey
import com.awabi2048.ccsystem.api.localization.generated.CommonKeys
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
        WorldSettingsActionRestriction.INSUFFICIENT_ROLE_MEMBER,
        WorldSettingsActionRestriction.INSUFFICIENT_ROLE_MODERATOR -> CommonKeys.GENERAL_NO_PERMISSION
    }

    /**
     * 閲覧者の役割別警告を、ワールド外最優先で解決する表示専用ヘルパーです。
     * 権限判定自体は置換せず、なぜ表示だけなのかをLoreへ伝えるためにだけ用います。
     */
    fun viewerRestriction(
        isInWorld: Boolean,
        isOwner: Boolean,
        isModerator: Boolean,
        isMember: Boolean,
    ): WorldSettingsActionRestriction? = when {
        !isInWorld -> WorldSettingsActionRestriction.NOT_IN_TARGET_WORLD
        isOwner -> null
        isModerator -> WorldSettingsActionRestriction.INSUFFICIENT_ROLE_MODERATOR
        isMember -> WorldSettingsActionRestriction.INSUFFICIENT_ROLE_MEMBER
        else -> null
    }
}
