package me.awabi2048.myworldmanager.util

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import java.time.LocalDate
import java.util.*

object PlayerTag {
    private val VISITED_WORLDS = NamespacedKey("myworldmanager", "visited_worlds")
    private val LAST_VISIT_RESET = NamespacedKey("myworldmanager", "last_visit_reset")
    private val gson = Gson()

    /**
     * 指定されたワールドをその日初めて訪れたかどうかを確認し、記録します。
     * 重複カウント防止のため、同日内の2回目以降の訪問は false を返します。
     */
    fun shouldCountVisit(player: Player, worldUuid: UUID): Boolean {
        val pdc = player.persistentDataContainer
        val today = LocalDate.now().toString()
        val lastReset = pdc.get(LAST_VISIT_RESET, PersistentDataType.STRING)

        // 日付が変わっている場合はリストをリセット
        val visitedUuids = if (lastReset != today) {
            pdc.set(LAST_VISIT_RESET, PersistentDataType.STRING, today)
            mutableListOf<String>()
        } else {
            val json = pdc.get(VISITED_WORLDS, PersistentDataType.STRING)
            if (json != null) {
                val type = object : TypeToken<MutableList<String>>() {}.type
                gson.fromJson<MutableList<String>>(json, type) ?: mutableListOf()
            } else {
                mutableListOf()
            }
        }

        val uuidStr = worldUuid.toString()
        return if (!visitedUuids.contains(uuidStr)) {
            // その日初めての訪問
            visitedUuids.add(uuidStr)
            pdc.set(VISITED_WORLDS, PersistentDataType.STRING, gson.toJson(visitedUuids))
            true
        } else {
            // 既に訪問済み
            false
        }
    }
}
