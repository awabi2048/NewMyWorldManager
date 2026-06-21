package me.awabi2048.myworldmanager.util

import org.bukkit.plugin.java.JavaPlugin

class WorldValidator(private val plugin: JavaPlugin) {

    fun validateName(name: String): String? {
        val maxLength = plugin.config.getInt("world_name.max_length", 32)
        val blockedStrings = plugin.config.getStringList("validation.blocked_strings")

        if (name.isBlank()) {
            return "§cワールド名を入力してください"
        }
        if (name.length > maxLength) {
            return "§cワールド名は${maxLength}文字以下で入力してください"
        }
        if (blockedStrings.any { name.contains(it, ignoreCase = true) }) {
            return "ワールド名に使用できない文字列が含まれています。"
        }

        // 基本的な文字種チェック：英数字、アンダースコア、ハイフン、日本語など。
        // ここでは簡易的に、制御文字を含まない程度にするか、必要に応じて厳格に
        // 今回はConfig見直しがメインなので、ファイルシステム的に危険な文字だけ弾く
        if (name.contains("/") || name.contains("\\") || name.contains(":") || name.contains("*") || 
            name.contains("?") || name.contains("\"") || name.contains("<") || name.contains(">") || name.contains("|")) {
            return "ワールド名に使用できない記号が含まれています。"
        }

        return null
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
