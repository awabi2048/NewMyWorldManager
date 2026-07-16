package me.awabi2048.myworldmanager.integration

import com.awabi2048.ccsystem.api.lwc.WorldArchiveStatusProvider
import me.awabi2048.myworldmanager.api.MyWorldManagerApi

/** CC-SystemのLWC残存情報からMWMアーカイブを除外する登録用provider。 */
object MyWorldManagerArchiveStatusProvider : WorldArchiveStatusProvider {
    override fun getId(): String = "my-world-manager"

    override fun isArchived(worldName: String): Boolean {
        return MyWorldManagerApi.getWorldRepository()?.findByWorldName(worldName)?.isArchived == true
    }
}
