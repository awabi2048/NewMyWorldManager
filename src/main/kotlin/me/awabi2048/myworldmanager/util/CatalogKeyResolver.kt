package me.awabi2048.myworldmanager.util

import com.awabi2048.ccsystem.api.localization.LocalizationCatalogContract
import com.awabi2048.ccsystem.api.localization.LocalizationKey
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Minecraftや設定から渡される識別子を、利用開始時に型付きカタログキーへ解決します。
 * 翻訳キーの文字列を表示処理まで持ち回らず、未知識別子は欠損として即座に失敗させます。
 */
internal object CatalogKeyResolver {
    private val biomeKeys = ConcurrentHashMap<String, LocalizationKey<String>>()
    private val worldTagKeys = ConcurrentHashMap<String, LocalizationKey<String>>()

    fun biome(id: String): LocalizationKey<String> = resolve(biomeKeys, "biomes", id)

    fun worldTag(id: String): LocalizationKey<String> = resolve(worldTagKeys, "world_tag", id)

    /**
     * サーバーが提供するbiome集合を有効化中に全件解決します。
     * これにより、新規biomeのカタログ不足がGUI表示時まで遅延しません。
     */
    fun validateBiomes(ids: Iterable<String>) {
        ids.forEach(::biome)
    }

    private fun resolve(
        cache: ConcurrentHashMap<String, LocalizationKey<String>>,
        domain: String,
        rawId: String,
    ): LocalizationKey<String> {
        val id = rawId.trim().lowercase(Locale.ROOT)
        require(id.matches(IDENTIFIER)) { "不正なローカライズ識別子です: domain=$domain, id=$rawId" }
        return cache.computeIfAbsent(id) {
            LocalizationCatalogContract.resolveText("$domain.$id")
        }
    }

    private val IDENTIFIER = Regex("[a-z0-9_]+")
}
