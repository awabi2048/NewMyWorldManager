package me.awabi2048.myworldmanager.listener

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.gui.TemplateWizardGui
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.plugin.java.JavaPlugin

class TemplateWizardListener : Listener {

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
        val view = event.view
        val title = PlainTextComponentSerializer.plainText().serialize(view.title())
        val player = event.whoClicked as? Player ?: return
        val lang = plugin.languageManager

        // Check title using language manager/key
        if (!lang.isKeyMatch(title, "gui.template_wizard.title")) return

        event.isCancelled = true
        val currentItem = event.currentItem ?: return
        if (currentItem.type == Material.AIR) return
        
        val id = ItemTag.getType(currentItem) ?: return
        val session = plugin.templateWizardGui.getSession(player.uniqueId) ?: return

        when (id) {
            "name_input" -> {
                plugin.soundManager.playClickSound(player, currentItem)
                session.inputState = TemplateWizardGui.InputState.NAME
                player.closeInventory()
                val cancelWord = plugin.config.getString("creation.cancel_word", "cancel") ?: "cancel"
                val cancelInfo = lang.getMessage(player, "messages.chat_input_cancel_hint", mapOf("word" to cancelWord))
                player.sendMessage(lang.getMessage(player, "messages.wizard_name_prompt") + " " + cancelInfo)
            }
            "desc_input" -> {
                plugin.soundManager.playClickSound(player, currentItem)
                session.inputState = TemplateWizardGui.InputState.DESCRIPTION
                player.closeInventory()
                val cancelWord = plugin.config.getString("creation.cancel_word", "cancel") ?: "cancel"
                val cancelInfo = lang.getMessage(player, "messages.chat_input_cancel_hint", mapOf("word" to cancelWord))
                player.sendMessage(lang.getMessage(player, "messages.world_desc_prompt") + " " + cancelInfo)
            }
            "icon_select" -> {
                plugin.soundManager.playClickSound(player, currentItem)
                // アイコン変更モード: インベントリ内のアイテムをクリックさせる
                // ここでは簡易的に「アイコン変更モード」としてチャットで案内し、InventoryClickEventで処理...
                // 実はMyWorldManagerにはicon変更用のロジックが他にあるかもしれないが、
                // ここではシンプルに「手に持っているアイテム」を設定させるか、
                // あるいは「次のクリックで設定」か。
                
                // 既存のWorldSettingsGuiでは「クリックした後、インベントリ内のアイテムをクリック」となっている。
                // 同じようにするか、あるいはカーソルのアイテムをセットするか。
                
                // カーソルにアイテムを持っている場合
                if (event.cursor != null && event.cursor!!.type != Material.AIR) {
                    session.icon = event.cursor!!.type
                    plugin.soundManager.playClickSound(player, currentItem)
                    plugin.templateWizardGui.open(player)
                    return
                }
                
                // カーソルが空の場合、インベントリをクリックしてセットするように促す
                // しかし、このGUIはイベントキャンセルされている。
                // 下のインベントリ(プレイヤインベントリ)をクリックした場合を検知する必要がある。
                
                // 今回はシンプルに: カーソルにアイテムを持った状態でクリックしてください、というメッセージにするか、
                // あるいはプレイヤインベントリのクリックを許可するか。
                
                player.sendMessage("§e[MyWorldManager] アイコンにしたいアイテムを持ってクリックしてください。")
            }
            "origin_set" -> {
                plugin.soundManager.playClickSound(player, currentItem)
                session.originLocation = player.location
                player.sendMessage("§a現在位置を原点に設定しました。")
                plugin.templateWizardGui.open(player)
            }
            "save_confirm" -> {
                plugin.soundManager.playClickSound(player, currentItem)
                
                // 保存処理
                val templateId = session.id
                if (templateId.isEmpty()) {
                    player.sendMessage("§cIDが設定されていません。")
                    return
                }
                
                // templates.ymlに保存
                val config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(java.io.File(plugin.dataFolder, "templates.yml"))
                val key = templateId
                
                // 既に存在するかチェック
                if (config.contains(key)) {
                    player.sendMessage("§cそのIDは既に存在します。")
                    return
                }
                
                config.set("$key.name", session.name)
                config.set("$key.description", session.description) // List
                config.set("$key.icon", session.icon.name)
                config.set("$key.path", player.world.name) // 現在のワールドをテンプレートとする
                
                val loc = session.originLocation!!
                config.set("$key.origin.x", loc.blockX)
                config.set("$key.origin.y", loc.blockY)
                config.set("$key.origin.z", loc.blockZ)
                
                // コスト設定など(デフォルト値)
                config.set("$key.cost", 0)
                
                config.save(java.io.File(plugin.dataFolder, "templates.yml"))
                
                player.sendMessage("§aテンプレート「$key」を登録しました！")
                player.closeInventory()
                plugin.templateWizardGui.removeSession(player.uniqueId)
                plugin.templateRepository.loadTemplates()
            }
        }
    }
    
    // プレイヤーインベントリのクリック（アイコン選択用）
    @EventHandler
    fun onPlayerInventoryClick(event: InventoryClickEvent) {
        val plugin = JavaPlugin.getPlugin(MyWorldManager::class.java)
        val player = event.whoClicked as? Player ?: return
        val session = plugin.templateWizardGui.getSession(player.uniqueId) ?: return
        
        // 上のインベントリがTemplateWizardであること
        val view = event.view
        val title = PlainTextComponentSerializer.plainText().serialize(view.title())
        val lang = plugin.languageManager
        if (!lang.isKeyMatch(title, "gui.template_wizard.title")) return
        
        // クリックされたインベントリがプレイヤーインベントリである場合
        if (event.clickedInventory == player.inventory) {
            val item = event.currentItem
            if (item != null && item.type != Material.AIR) {
                // アイコンとして設定
                session.icon = item.type
                plugin.soundManager.playClickSound(player, item)
                plugin.templateWizardGui.open(player) // GUI更新
                event.isCancelled = true // アイテム移動を防ぐ
            }
        }
    }
}
