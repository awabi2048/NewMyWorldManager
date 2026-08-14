package me.awabi2048.myworldmanager.util

import com.awabi2048.ccsystem.api.localization.LocalizationKey
import com.awabi2048.ccsystem.api.localization.generated.CommonKeys

/** 永続化文字列を翻訳キーへ変換する唯一の有限対応表です。 */
object MeetStatusLocalization {
    fun name(status: String): LocalizationKey<String> = when (status.uppercase()) {
        "JOIN_ME" -> CommonKeys.GENERAL_STATUS_JOIN_ME
        "ASK_ME" -> CommonKeys.GENERAL_STATUS_ASK_ME
        else -> CommonKeys.GENERAL_STATUS_BUSY
    }

    fun description(status: String): LocalizationKey<String> = when (status.uppercase()) {
        "JOIN_ME" -> CommonKeys.GENERAL_STATUS_DESCRIPTION_JOIN_ME
        "ASK_ME" -> CommonKeys.GENERAL_STATUS_DESCRIPTION_ASK_ME
        else -> CommonKeys.GENERAL_STATUS_DESCRIPTION_BUSY
    }
}
