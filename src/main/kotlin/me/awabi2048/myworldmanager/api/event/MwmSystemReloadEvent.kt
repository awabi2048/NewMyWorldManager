package me.awabi2048.myworldmanager.api.event

import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/** MWMの設定再読込完了後、連携プラグインが自身の設定を同期するためのイベントです。 */
class MwmSystemReloadEvent : Event() {
    private val failures = mutableListOf<String>()
    val failedParticipants: List<String> get() = failures.toList()

    /** 連携側が失敗を報告し、コマンドが成功と誤表示しないようにします。 */
    fun reportFailure(participant: String) {
        require(participant.isNotBlank())
        failures += participant
    }

    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        private val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
}
