package me.awabi2048.myworldmanager.api.extension

data class MenuExtensionContext(
    val menuType: String,
    val data: MutableMap<String, Any?> = mutableMapOf()
) {
    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? = data[key] as? T
}
