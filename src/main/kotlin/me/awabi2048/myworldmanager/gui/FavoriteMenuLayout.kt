package me.awabi2048.myworldmanager.gui

/** お気に入り一覧と詳細画面の固定役割を、画面実装と契約テストで共有します。 */
internal object FavoriteMenuLayout {
    const val HEADER_CENTER_SLOT = 4
    const val DETAIL_SIZE = 45
    const val DETAIL_BACK_SLOT = 36
    val DETAIL_ACTION_SLOTS = listOf(19, 21, 23, 25)

    data class Footer(
        val otherWorlds: Int,
        val toggleCurrent: Int,
        val currentWorld: Int,
        val tagFilter: Int,
    )

    fun footer(size: Int): Footer {
        require(size >= 45 && size % 9 == 0) { "favorite list size must contain complete rows" }
        val start = size - 9
        return Footer(start + 2, start + 3, start + 4, start + 6)
    }
}
