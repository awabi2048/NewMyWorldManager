package me.awabi2048.myworldmanager.ui.bedrock

enum class BedrockUiMode {
    AUTO,
    FORM,
    INVENTORY;

    companion object {
        fun fromRaw(value: String?): BedrockUiMode {
            return when (value?.trim()?.lowercase()) {
                "form" -> FORM
                "inventory" -> INVENTORY
                else -> AUTO
            }
        }
    }
}
