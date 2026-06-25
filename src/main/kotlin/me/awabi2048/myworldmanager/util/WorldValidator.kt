package me.awabi2048.myworldmanager.util

import org.bukkit.plugin.java.JavaPlugin

/**
 * ワールド名の検証結果。メッセージ本文は cc-system の言語ファイル側で管理するため、
 * ここでは判定結果と対応する言語キー・プレースホルダーのみを保持する。
 */
sealed class WorldNameValidation {
    object Ok : WorldNameValidation()

    sealed class Failure(
        val messageKey: String,
        val placeholders: Map<String, Any> = emptyMap(),
    ) : WorldNameValidation() {
        class Blank : Failure("messages.world_name_blank")
        class TooLong(maxLength: Int) : Failure("messages.world_name_too_long", mapOf("max" to maxLength))
        class BlockedString : Failure("messages.world_name_blocked")
        class ForbiddenSymbol : Failure("messages.world_name_symbol")
    }
}

class WorldValidator(private val plugin: JavaPlugin) {

    fun validateName(name: String): WorldNameValidation {
        val maxLength = plugin.config.getInt("world_name.max_length", 32)
        val blockedStrings = plugin.config.getStringList("validation.blocked_strings")

        return when {
            name.isBlank() -> WorldNameValidation.Failure.Blank()
            name.length > maxLength -> WorldNameValidation.Failure.TooLong(maxLength)
            blockedStrings.any { name.contains(it, ignoreCase = true) } ->
                WorldNameValidation.Failure.BlockedString()
            // 基本的な文字種チェック：英数字、アンダースコア、ハイフン、日本語など。
            // ここでは簡易的に、制御文字を含まない程度にするか、必要に応じて厳格に
            // 今回はConfig見直しがメインなので、ファイルシステム的に危険な文字だけ弾く
            name.contains("/") || name.contains("\\") || name.contains(":") || name.contains("*") ||
                name.contains("?") || name.contains("\"") || name.contains("<") || name.contains(">") ||
                name.contains("|") -> WorldNameValidation.Failure.ForbiddenSymbol()
            else -> WorldNameValidation.Ok
        }
    }

    fun validateDescription(description: String): String? {
        val blockedStrings = plugin.config.getStringList("validation.blocked_strings")

        if (blockedStrings.any { description.contains(it, ignoreCase = true) }) {
            return "説明文に使用できない文字列が含まれています。"
        }

        // 極端に長い説明文を弾くなどが必要ならここに追加
        if (description.length > 50) {
             return "説明文は50文字以下である必要があります。"
        }

        return null
    }
}
