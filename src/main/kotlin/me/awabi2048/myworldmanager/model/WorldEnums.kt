package me.awabi2048.myworldmanager.model

/** マイワールドの公開レベルの定義 */
enum class PublishLevel(val displayName: String) {
    PRIVATE("非公開"),
    FRIEND("限定公開"),
    PUBLIC("全体公開"),
    LOCKED("封鎖中")
}

/** ワールドタグの定義 */
enum class WorldTag(val displayName: String) {
    SHOP("ショップ"),
    MINIGAME("ミニゲーム"),
    BUILDING("建築"),
    FACILITY("共用施設"),
    STREAMING("放送・撮影可");

    companion object {
        fun fromDisplayName(name: String): WorldTag? = values().find { it.displayName == name }
    }
}
