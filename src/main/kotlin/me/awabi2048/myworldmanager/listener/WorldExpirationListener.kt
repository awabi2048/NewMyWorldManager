package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.model.*
import me.awabi2048.myworldmanager.repository.*
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

class WorldExpirationListener(private val repository: WorldConfigRepository) : Listener {

    private val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    @EventHandler
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        updateWorldExpiration(event.player.world.name)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        updateWorldExpiration(event.player.world.name)
    }

    private fun updateWorldExpiration(worldName: String) {
        if (!worldName.startsWith("my_world.")) return

        try {
            val uuidString = worldName.substringAfter("my_world.")
            val uuid = UUID.fromString(uuidString)
            extendExpirationDate(uuid)
        } catch (e: Exception) {
            // UUIDフォーマットでない場合などは無視
        }
    }

    private fun extendExpirationDate(worldUuid: UUID) {
        val worldData = repository.findByUuid(worldUuid) ?: return
        
        // 現在の日付 + 延長日数
        val extensionDays = plugin.config.getInt("default_expiration.extension_days", 7)
        val newExpireDate = LocalDate.now().plusDays(extensionDays.toLong())
        
        // 現在の期限と比較して、新しい期限の方が未来であれば更新する
        // (ログインするたびに延長されるが、最大値を設けるかどうかは要件にないため、常に最終訪問/ログインから+N日とする)
        // 実装としては「最終訪問時の日付 + N日」になるように更新すればよい。
        // すでに未来すぎる日付になっている場合（例：昨日ログインして期限が+7日された。今日ログインしたらさらに+7日されて+8日になる？）
        // 要件: "任意のプレイヤーがワールドを訪れた、またはメンバーがログインした際に期限切れ日時を指定日数分加算する。"
        // "加算する (add specified days) implies adding to the current expireDate OR resetting based on now.
        // Usually, these systems work by "Reset expiration to Now + X days". If it says "Add", it could mean cumulative.
        // Given typical "expiration" mechanics (e.g. claim expiry), it usually means "refresh validity period".
        // If I add cumulatively, a player logging in 100 times in a day gives 700 days. That's likely unintended.
        // Interpretation: "Update the expiration date to be [Extension Days] from NOW." (Refreshing the timer).
        
        // However, if the current expiration date is ALREADY far in the future (e.g. paid extension), we shouldn't shorten it.
        // So: NewDate = Max(CurrentExpireDate, Now + ExtensionDays) ? 
        // Or just Now + ExtensionDays?
        // If the world is expiring tomorrow, and I visit, it becomes Now + 7 days.
        // If the world expires in 30 days (due to some other item), and I visit, should it stay 30 days or become 7 days? Usually stay 30.
        // Let's implement logic: Set expireDate to (Now + Extension) IF (Now + Extension) > CurrentExpireDate.

        val currentExpireDates = try {
            LocalDate.parse(worldData.expireDate, formatter)
        } catch (e: Exception) {
            LocalDate.now() // エラー時は現在日付と仮定
        }

        if (newExpireDate.isAfter(currentExpireDates)) {
            worldData.expireDate = newExpireDate.format(formatter)
            repository.save(worldData)
        }
    }
}
