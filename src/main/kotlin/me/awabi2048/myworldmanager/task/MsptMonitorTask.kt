package me.awabi2048.myworldmanager.task

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.util.ChiyogamiUtil
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.scheduler.BukkitRunnable
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.ArrayDeque

class MsptMonitorTask(private val plugin: MyWorldManager) : BukkitRunnable() {

    // 監視対象ワールドのMSPT履歴（直近10秒分）
    private val worldMsptHistory = ConcurrentHashMap<String, ArrayDeque<Double>>()
    // 警告済みワールドと警告時刻
    private val warnedWorlds = ConcurrentHashMap<String, Long>()
    // サーバー全体のMSPT（1秒ごとに更新）
    @Volatile
    var currentServerMspt: Double = 0.0
        private set

    override fun run() {
        if (!plugin.config.getBoolean("mspt_monitor.enabled", true)) return
        if (!ChiyogamiUtil.isChiyogamiActive()) return

        val threshold = plugin.config.getDouble("mspt_monitor.threshold", 40.0)
        val lang = plugin.languageManager

        Bukkit.getWorlds().forEach { world ->
            val mspt = ChiyogamiUtil.getWorldMspt(world) ?: return@forEach
            val worldName = world.name
            
            // 履歴を更新（最大10エントリ、約10秒分）
            val history = worldMsptHistory.getOrPut(worldName) { ArrayDeque() }
            history.addLast(mspt)
            while (history.size > 10) {
                history.removeFirst()
            }
            
            // 平均MSPTを計算（履歴が3未満の場合は無視）
            if (history.size < 3) return@forEach
            val avgMspt = history.average()
            
            // しきい値チェック
            if (avgMspt >= threshold) {
                // 警告済みかチェック
                val lastWarned = warnedWorlds[worldName]
                if (lastWarned == null) {
                    // 初回警告
                    notifyAdmins(worldName, avgMspt)
                    warnedWorlds[worldName] = System.currentTimeMillis()
                }
                // 警告済みでも監視は継続（履歴更新）
            } else {
                // 平均がしきい値未満の場合、安定化カウンターをリセット
                // 1分以上安定したら警告状態を解除
                val lastWarned = warnedWorlds[worldName]
                if (lastWarned != null && System.currentTimeMillis() - lastWarned > 60000) {
                    warnedWorlds.remove(worldName)
                    worldMsptHistory.remove(worldName) // 履歴もクリア
                }
            }
        }
    }

    /** サーバー全体のMSPTを更新するタスク（1秒間隔） */
    fun startServerMsptUpdate() {
        object : BukkitRunnable() {
            override fun run() {
                updateServerMspt()
            }
        }.runTaskTimer(plugin, 20L, 20L)
    }

    /** サーバー全体のMSPTを取得して更新 */
    fun updateServerMspt() {
        currentServerMspt = ChiyogamiUtil.getServerMspt()
    }

    private fun notifyAdmins(worldName: String, mspt: Double) {
        val msptString = String.format("%.1f", mspt)
        val lang = plugin.languageManager
        
        // 登録ワールド名を取得（表示名）
        val registeredName = plugin.worldConfigRepository.findByWorldName(worldName)?.name ?: worldName

        val clickableMessage = lang.getComponent(
            null,
            "messages.mspt_warning_chat",
            mapOf("world" to registeredName, "mspt" to msptString)
         ).clickEvent(ClickEvent.runCommand("/mwm_internal mspt-sort"))
         .hoverEvent(HoverEvent.showText(Component.text("クリックしてMSPT順のワールド一覧を開く").color(NamedTextColor.GRAY)))

        Bukkit.getOnlinePlayers().filter { it.isOp }.forEach { admin ->
            // クリック可能なチャットメッセージ
            admin.sendMessage(clickableMessage)

            // 効果音
            val soundStr = plugin.config.getString("sounds.mspt_warning.sound", "BLOCK_NOTE_BLOCK_BASS")
            val pitch = plugin.config.getDouble("sounds.mspt_warning.pitch", 0.5).toFloat()
            try {
                admin.playSound(admin.location, Sound.valueOf(soundStr!!.uppercase()), 1.0f, pitch)
            } catch (e: Exception) {
                admin.playSound(admin.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f)
            }

            // サブタイトル表示
            val subtitle = lang.getComponent(
                admin,
                "messages.mspt_warning_subtitle",
                mapOf("world" to registeredName, "mspt" to msptString)
            )
            
            val times = Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
            val title = Title.title(Component.empty(), subtitle, times)
            admin.showTitle(title)
        }
    }

    fun start() {
        val interval = plugin.config.getLong("mspt_monitor.interval_seconds", 30) * 20L
        this.runTaskTimer(plugin, 200L, interval) // 起動10秒後から開始
        
        // サーバーMSPT更新タスクも開始
        startServerMsptUpdate()
    }
}