package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import java.util.UUID

class GlobalMenuListener(private val plugin: MyWorldManager) : Listener {

    private val lastClickTime = mutableMapOf<UUID, Long>()
    private val COOLDOWN_MS = 150L // 3 ticks = 150ms (reduced from 200ms)

    @EventHandler(priority = EventPriority.LOWEST)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked
        val inventory = event.inventory
        
        // マイワールドマネージャーのメニューはHolderを持っているか、特定のタイトルを持つ
        // ここでは、トップインベントリをクリックした場合のみ判定する（プレイヤーインベントリの操作は除外するかもしれないが、メニューを開いている間は全体的に制限したほうが安全）
        
        // プレイヤーインベントリ（下のインベントリ）のクリックは除外する場合：
        // if (event.clickedInventory == event.view.bottomInventory) return

        // しかし、メニュー操作中にプレイヤーインベントリをクリックしてアイテムを動かすこともあるかもしれないが、
        // ダブルクリック防止の観点からは、メニューを開いている間はすべてのクリックを制限したほうが無難。
        // ただし、通常のチェストを開いているときなどに誤爆しないように、MyWorldManagerのGUIかどうかの判定が必要。
        
        val holder = inventory.holder
        val title = event.view.title
        
        // Holderによる判定 (MyWorldManagerのGUIは専用のHolderを持つ場合があるが、全てではないかもしれない)
        // 現状の実装を見ると、WorldSettingsGuiHolder, DiscoveryGuiHolderなどがある。
        // また、Holderがnullの場合もカスタムインベントリの可能性がある。
        
        // 簡易的な判定として、MyWorldManagerのGUIを開いているかどうかをチェックする手段があると良いが、
        // 既存のコードでは、各Listenerでタイトル判定などを行っている。
        // ここでは、「カスタムインベントリ（タイプがCHESTなど）を開いている」かつ
        // 「MyWorldManagerのパッケージに含まれるHolderを持っている」または「Holderがnull（一部のGUI）」の場合に適用する。
        
        val isPluginGui = holder?.javaClass?.name?.startsWith("me.awabi2048.myworldmanager") == true || holder == null
        
        if (!isPluginGui) return

        val p = player as? org.bukkit.entity.Player ?: return
        val uuid = p.uniqueId
        val session = plugin.settingsSessionManager.getSession(p)
        
        // 詳細なデバッグログ除去
        // val titleStr = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.view.title())
        // p.sendMessage("§d[Debug-Click] Title: $titleStr")
        // p.sendMessage("§d[Debug-Click] Session: ${session?.action ?: "None"}, Transition: ${session?.isGuiTransition ?: "N/A"}")

        // GUI遷移中のクリックは、個別のリスナーでキャンセルされるためクールダウンとしてはカウントしない
        if (session != null && session.isGuiTransition) {
            return
        }

        val currentTime = System.currentTimeMillis()
        val lastTime = lastClickTime[uuid] ?: 0L

        if (currentTime - lastTime < COOLDOWN_MS) {
            // player.sendMessage("§7[Debug] Click cancelled (Cooldown: ${COOLDOWN_MS}ms)")
            event.isCancelled = true
            return
        }

        lastClickTime[uuid] = currentTime
    }
}
