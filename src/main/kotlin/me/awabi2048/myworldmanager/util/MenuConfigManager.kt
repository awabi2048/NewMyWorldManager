package me.awabi2048.myworldmanager.util

import me.awabi2048.myworldmanager.MyWorldManager
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

/**
 * メニュー設定ファイルを管理するクラス
 * 各メニューのアイコンとサウンド設定を外部YAMLから読み込む
 */
class MenuConfigManager(private val plugin: MyWorldManager) {
    
    private val menuConfigs = mutableMapOf<String, YamlConfiguration>()
    private val menusFolder: File = File(plugin.dataFolder, "menus")
    
    // デフォルトで管理するメニューID一覧
    private val defaultMenus = listOf(
        "player_world",
        "creation",
        "world_settings",
        "visit",
        "favorite",
        "discovery",
        "portal",
        "admin_manage",
        "admin_world",
        "admin_portals",
        "portal_manage",
        "template_wizard",
        "meet",
        "environment",
        "favorite_menu",
        "environment_confirm",
        "spotlight_confirm",
        "user_settings"
    )
    
    /**
     * 初期化: menusフォルダ作成とデフォルト設定ファイルのコピー
     */
    fun initialize() {
        if (!menusFolder.exists()) {
            menusFolder.mkdirs()
        }
        
        // デフォルト設定ファイルをコピー
        defaultMenus.forEach { menuId ->
            val targetFile = File(menusFolder, "$menuId.yml")
            if (!targetFile.exists()) {
                val resourcePath = "menus/$menuId.yml"
                try {
                    plugin.saveResource(resourcePath, false)
                } catch (e: Exception) {
                    // リソースが存在しない場合はデフォルト設定を生成
                    createDefaultMenuConfig(targetFile)
                }
            }
        }
        
        loadAllConfigs()
    }
    
    /**
     * すべてのメニュー設定を再読み込み
     */
    fun loadAllConfigs() {
        menuConfigs.clear()
        menusFolder.listFiles()?.filter { it.extension == "yml" }?.forEach { file ->
            val menuId = file.nameWithoutExtension
            menuConfigs[menuId] = YamlConfiguration.loadConfiguration(file)
        }
        plugin.logger.info("Loaded ${menuConfigs.size} menu configurations")
    }
    
    /**
     * 指定メニューのアイコンMaterialを取得
     */
    fun getIconMaterial(menuId: String, iconId: String, default: Material = Material.BARRIER): Material {
        val config = menuConfigs[menuId] ?: return default
        val materialStr = config.getString("icons.$iconId.material") ?: return default
        return try {
            Material.matchMaterial(materialStr.uppercase()) ?: default
        } catch (e: Exception) {
            plugin.logger.warning("Invalid material '$materialStr' for icon '$iconId' in menu '$menuId'")
            default
        }
    }
    
    /**
     * 指定メニューのアイコン用サウンドを取得
     */
    fun getIconSound(menuId: String, iconId: String): Sound? {
        val config = menuConfigs[menuId] ?: return null
        val soundStr = config.getString("icons.$iconId.sound") ?: return null
        return try {
            Sound.valueOf(soundStr.uppercase())
        } catch (e: Exception) {
            plugin.logger.warning("Invalid sound '$soundStr' for icon '$iconId' in menu '$menuId'")
            null
        }
    }
    
    /**
     * 指定メニューのアイコン用サウンドピッチを取得
     */
    fun getIconSoundPitch(menuId: String, iconId: String, default: Float = 1.0f): Float {
        val config = menuConfigs[menuId] ?: return default
        return config.getDouble("icons.$iconId.pitch", default.toDouble()).toFloat()
    }
    
    /**
     * メニューを開いた時のサウンドを取得
     */
    fun getOpenSound(menuId: String): Sound? {
        val config = menuConfigs[menuId] ?: return null
        val soundStr = config.getString("open_sound.sound") ?: return null
        return try {
            Sound.valueOf(soundStr.uppercase())
        } catch (e: Exception) {
            plugin.logger.warning("Invalid open sound '$soundStr' in menu '$menuId'")
            null
        }
    }
    
    /**
     * メニューを開いた時のサウンドピッチを取得
     */
    fun getOpenSoundPitch(menuId: String, default: Float = 1.0f): Float {
        val config = menuConfigs[menuId] ?: return default
        return config.getDouble("open_sound.pitch", default.toDouble()).toFloat()
    }
    
    /**
     * デフォルトのメニュー設定ファイルを生成
     */
    private fun createDefaultMenuConfig(file: File) {
        val config = YamlConfiguration()
        
        // デフォルトオープンサウンド
        config.set("open_sound.sound", "BLOCK_CHEST_OPEN")
        config.set("open_sound.pitch", 1.0)
        
        // 共通のデフォルトアイコン設定
        config.set("icons.back.material", "ARROW")
        config.set("icons.back.sound", "UI_BUTTON_CLICK")
        config.set("icons.back.pitch", 2.0)
        config.set("icons.next_page.material", "ARROW")
        config.set("icons.next_page.sound", "UI_BUTTON_CLICK")
        config.set("icons.next_page.pitch", 1.5)
        config.set("icons.prev_page.material", "ARROW")
        config.set("icons.prev_page.sound", "UI_BUTTON_CLICK")
        config.set("icons.prev_page.pitch", 1.5)
        config.set("icons.confirm.material", "LIME_WOOL")
        config.set("icons.confirm.sound", "ENTITY_EXPERIENCE_ORB_PICKUP")
        config.set("icons.confirm.pitch", 1.5)
        config.set("icons.cancel.material", "RED_WOOL")
        config.set("icons.cancel.sound", "BLOCK_NOTE_BLOCK_BASS")
        config.set("icons.cancel.pitch", 0.5)
        
        config.save(file)
    }
}
