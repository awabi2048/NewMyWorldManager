package me.awabi2048.myworldmanager.task

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.util.ChiyogamiUtil
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.scheduler.BukkitRunnable
import java.time.Duration

class MsptMonitorTask(private val plugin: MyWorldManager) : BukkitRunnable() {

    override fun run() {
        if (!plugin.config.getBoolean("mspt_monitor.enabled", true)) return
        if (!ChiyogamiUtil.isChiyogamiActive()) return

        val threshold = plugin.config.getDouble("mspt_monitor.threshold", 40.0)
        val lang = plugin.languageManager

        Bukkit.getWorlds().forEach { world ->
            val mspt = ChiyogamiUtil.getWorldMspt(world) ?: return@forEach
            
            if (mspt >= threshold) {
                notifyAdmins(world.name, mspt)
            }
        }
    }

    private fun notifyAdmins(worldName: String, mspt: Double) {
        val msptString = String.format("%.1f", mspt)
        val lang = plugin.languageManager

        Bukkit.getOnlinePlayers().filter { it.isOp }.forEach { admin ->
            // チャットメッセージ
            admin.sendMessage(
                lang.getComponent(
                    admin,
                    "messages.mspt_warning_chat",
                    mapOf("world" to worldName, "mspt" to msptString)
                )
            )

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
                mapOf("world" to worldName, "mspt" to msptString)
            )
            
            val times = Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
            val title = Title.title(Component.empty(), subtitle, times)
            admin.showTitle(title)
        }
    }

    fun start() {
        val interval = plugin.config.getLong("mspt_monitor.interval_seconds", 30) * 20L
        this.runTaskTimer(plugin, 200L, interval) // 起動10秒後から開始
    }
}
