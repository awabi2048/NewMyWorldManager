package me.awabi2048.myworldmanager.util

import me.awabi2048.myworldmanager.MyWorldManager
import org.bukkit.Material
import org.bukkit.Sound

/**
 * メニュー設定をコード内の固定値として管理するクラス
 */
class MenuConfigManager(private val plugin: MyWorldManager) {

    private data class IconConfig(
            val material: Material? = null,
            val sound: Sound? = null,
            val pitch: Float = 1.0f
    )

    private data class MenuConfig(
            val openSound: Sound,
            val openPitch: Float,
            val icons: Map<String, IconConfig>
    )

    private val menuConfigs: Map<String, MenuConfig> =
            mapOf(
                    "admin_manage" to
                            menuConfig(
                                    openSound = Sound.BLOCK_IRON_TRAPDOOR_OPEN,
                                    icons =
                                            mapOf(
                                                    "back" to icon(Material.ARROW, Sound.UI_BUTTON_CLICK, 2.0f),
                                                    "next_page" to icon(Material.ARROW, Sound.UI_BUTTON_CLICK, 1.5f),
                                                    "prev_page" to icon(Material.ARROW, Sound.UI_BUTTON_CLICK, 1.5f),
                                                    "info" to icon(Material.PAPER, Sound.UI_BUTTON_CLICK, 2.0f),
                                                    "world_item" to icon(sound = Sound.UI_BUTTON_CLICK, pitch = 2.0f)
                                            )
                            ),
                    "admin_portals" to
                            menuConfig(
                                    openSound = Sound.BLOCK_CHEST_OPEN,
                                    icons =
                                            mapOf(
                                                    "portal_item" to icon(Material.END_PORTAL_FRAME, Sound.UI_BUTTON_CLICK, 1.0f),
                                                    "sort" to icon(Material.HOPPER, Sound.UI_BUTTON_CLICK, 1.5f),
                                                    "back" to icon(Material.ARROW, Sound.UI_BUTTON_CLICK, 2.0f),
                                                    "next_page" to icon(Material.ARROW, Sound.UI_BUTTON_CLICK, 1.5f),
                                                    "prev_page" to icon(Material.ARROW, Sound.UI_BUTTON_CLICK, 1.5f)
                                            )
                            ),
                    "admin_world" to
                            menuConfig(
                                    openSound = Sound.BLOCK_CHEST_OPEN,
                                    icons =
                                            mapOf(
                                                    "world_item" to icon(Material.GRASS_BLOCK, Sound.UI_BUTTON_CLICK, 1.0f),
                                                    "filter_archive" to icon(Material.CHEST, Sound.UI_BUTTON_CLICK, 1.2f),
                                                    "filter_publish" to icon(Material.ENDER_EYE, Sound.UI_BUTTON_CLICK, 1.2f),
                                                    "filter_player" to icon(Material.PLAYER_HEAD, Sound.UI_BUTTON_CLICK, 1.2f),
                                                    "sort" to icon(Material.HOPPER, Sound.UI_BUTTON_CLICK, 1.5f),
                                                    "back" to icon(Material.ARROW, Sound.UI_BUTTON_CLICK, 2.0f),
                                                    "next_page" to icon(Material.ARROW, Sound.UI_BUTTON_CLICK, 1.5f),
                                                    "prev_page" to icon(Material.ARROW, Sound.UI_BUTTON_CLICK, 1.5f)
                                            )
                            ),
                    "creation" to
                            menuConfig(
                                    openSound = Sound.BLOCK_IRON_TRAPDOOR_OPEN,
                                    icons =
                                            mapOf(
                                                    "back" to icon(Material.ARROW, Sound.UI_BUTTON_CLICK, 2.0f),
                                                    "confirm" to icon(Material.LIME_WOOL, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.5f),
                                                    "cancel" to icon(Material.RED_WOOL, Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f),
                                                    "template" to icon(Material.GRASS_BLOCK, Sound.UI_BUTTON_CLICK, 2.0f),
                                                    "seed" to icon(Material.WHEAT_SEEDS, Sound.UI_BUTTON_CLICK, 2.0f),
                                                    "random" to icon(Material.ENDER_PEARL, Sound.UI_BUTTON_CLICK, 2.0f),
                                                    "insufficient_points" to icon(sound = Sound.BLOCK_ANVIL_PLACE, pitch = 1.0f),
                                                    "limit_reached" to icon(sound = Sound.BLOCK_ANVIL_PLACE, pitch = 1.0f)
                                            )
                            ),
                    "discovery" to
                            menuConfig(
                                    openSound = Sound.UI_TOAST_IN,
                                    icons =
                                            mapOf(
                                                    "back" to icon(Material.ARROW, Sound.UI_BUTTON_CLICK, 2.0f),
                                                    "next_page" to icon(Material.ARROW, Sound.UI_BUTTON_CLICK, 1.5f),
                                                    "prev_page" to icon(Material.ARROW, Sound.UI_BUTTON_CLICK, 1.5f),
                                                    "sort" to icon(Material.HOPPER, Sound.UI_BUTTON_CLICK, 2.0f),
                                                    "tag_filter" to icon(Material.NAME_TAG, Sound.UI_BUTTON_CLICK, 2.0f),
                                                    "world_item" to icon(sound = Sound.UI_BUTTON_CLICK, pitch = 2.0f),
                                                    "favorite_add" to icon(sound = Sound.ENTITY_PLAYER_LEVELUP, pitch = 2.0f),
                                                    "favorite_remove" to icon(sound = Sound.BLOCK_IRON_TRAPDOOR_CLOSE, pitch = 1.5f),
                                                    "access_denied" to icon(sound = Sound.ENTITY_VILLAGER_NO, pitch = 1.0f)
                                            )
                            ),
                    "environment" to
                            menuConfig(
                                    openSound = Sound.BLOCK_CHEST_OPEN,
                                    icons =
                                            mapOf(
                                                    "back" to icon(Material.ARROW, Sound.UI_BUTTON_CLICK, 2.0f),
                                                    "insufficient_points" to icon(sound = Sound.BLOCK_ANVIL_PLACE, pitch = 1.0f),
                                                    "gravity_change" to icon(sound = Sound.BLOCK_ANVIL_USE, pitch = 1.0f),
                                                    "biome_change" to icon(sound = Sound.ITEM_BOTTLE_EMPTY, pitch = 1.0f),
                                                    "weather_change" to icon(sound = Sound.ENTITY_PLAYER_LEVELUP, pitch = 2.0f)
                                            )
                            ),
                    "environment_confirm" to
                            menuConfig(
                                    openSound = Sound.BLOCK_CHEST_OPEN,
                                    icons =
                                            mapOf(
                                                    "confirm" to icon(Material.LIME_CONCRETE, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.5f),
                                                    "cancel" to icon(Material.RED_CONCRETE, Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f)
                                            )
                            ),
                    "favorite" to
                            menuConfig(
                                    openSound = Sound.BLOCK_CHEST_OPEN,
                                    icons =
                                            mapOf(
                                                    "back" to icon(Material.ARROW, Sound.UI_BUTTON_CLICK, 2.0f),
                                                    "next_page" to icon(Material.ARROW, Sound.UI_BUTTON_CLICK, 1.5f),
                                                    "prev_page" to icon(Material.ARROW, Sound.UI_BUTTON_CLICK, 1.5f),
                                                    "world_item" to icon(sound = Sound.UI_BUTTON_CLICK, pitch = 2.0f),
                                                    "favorite_add" to icon(sound = Sound.ENTITY_PLAYER_LEVELUP, pitch = 2.0f),
                                                    "favorite_remove" to icon(sound = Sound.BLOCK_IRON_TRAPDOOR_CLOSE, pitch = 1.5f)
                                            )
                            ),
                    "favorite_menu" to
                            menuConfig(
                                    openSound = Sound.BLOCK_CHEST_OPEN,
                                    icons =
                                            mapOf(
                                                    "other_worlds" to icon(Material.COMPASS, Sound.UI_BUTTON_CLICK, 2.0f),
                                                    "toggle_on" to icon(Material.RED_DYE, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.5f),
                                                    "toggle_off" to icon(Material.GRAY_DYE, Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f),
                                                    "list" to icon(Material.BOOK, Sound.UI_BUTTON_CLICK, 2.0f)
                                            )
                            ),
                    "meet" to
                            menuConfig(
                                    openSound = Sound.BLOCK_CHEST_OPEN,
                                    icons =
                                            mapOf(
                                                    "back" to icon(Material.ARROW, Sound.UI_BUTTON_CLICK, 2.0f),
                                                    "access_denied" to icon(sound = Sound.ENTITY_VILLAGER_NO, pitch = 1.0f)
                                            )
                            ),
                    "player_world" to
                            menuConfig(
                                    openSound = Sound.BLOCK_CHEST_OPEN,
                                    icons =
                                            mapOf(
                                                    "back" to icon(Material.ARROW, Sound.UI_BUTTON_CLICK, 2.0f),
                                                    "next_page" to icon(Material.ARROW, Sound.UI_BUTTON_CLICK, 1.5f),
                                                    "prev_page" to icon(Material.ARROW, Sound.UI_BUTTON_CLICK, 1.5f),
                                                    "world_item" to icon(sound = Sound.UI_BUTTON_CLICK, pitch = 2.0f)
                                            )
                            ),
                    "portal" to
                            menuConfig(
                                    openSound = Sound.BLOCK_ENDER_CHEST_OPEN,
                                    icons =
                                            mapOf(
                                                    "back" to icon(Material.ARROW, Sound.UI_BUTTON_CLICK, 2.0f),
                                                    "confirm" to icon(Material.LIME_WOOL, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.5f),
                                                    "cancel" to icon(Material.RED_WOOL, Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f),
                                                    "toggle_text" to icon(Material.OAK_SIGN, Sound.UI_BUTTON_CLICK, 2.0f),
                                                    "color" to icon(Material.MAGENTA_DYE, Sound.UI_BUTTON_CLICK, 2.0f),
                                                    "remove" to icon(Material.BARRIER, Sound.UI_BUTTON_CLICK, 2.0f)
                                            )
                            ),
                    "portal_manage" to
                            menuConfig(
                                    openSound = Sound.BLOCK_CHEST_OPEN,
                                    icons =
                                            mapOf(
                                                    "back" to icon(Material.ARROW, Sound.UI_BUTTON_CLICK, 2.0f),
                                                    "next_page" to icon(Material.ARROW, Sound.UI_BUTTON_CLICK, 1.5f),
                                                    "prev_page" to icon(Material.ARROW, Sound.UI_BUTTON_CLICK, 1.5f),
                                                    "portal_item" to icon(sound = Sound.ENTITY_ENDERMAN_TELEPORT, pitch = 1.0f),
                                                    "remove" to icon(Material.BARRIER, Sound.ENTITY_ITEM_BREAK, 0.5f)
                                            )
                            ),
                    "spotlight_confirm" to
                            menuConfig(
                                    openSound = Sound.BLOCK_CHEST_OPEN,
                                    icons =
                                            mapOf(
                                                    "confirm" to icon(Material.LIME_CONCRETE, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.5f),
                                                    "cancel" to icon(Material.RED_CONCRETE, Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f)
                                            )
                            ),
                    "template_wizard" to
                            menuConfig(
                                    openSound = Sound.BLOCK_ENCHANTMENT_TABLE_USE,
                                    icons =
                                            mapOf(
                                                    "back" to icon(Material.ARROW, Sound.UI_BUTTON_CLICK, 2.0f),
                                                    "icon_select" to icon(Material.ITEM_FRAME, Sound.ITEM_ARMOR_EQUIP_GENERIC, 1.2f),
                                                    "name_input" to icon(Material.NAME_TAG, Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, 1.5f),
                                                    "desc_input" to icon(Material.WRITABLE_BOOK, Sound.ITEM_BOOK_PAGE_TURN, 1.0f),
                                                    "origin_set" to icon(Material.COMPASS, Sound.BLOCK_ANVIL_USE, 2.0f),
                                                    "save_confirm" to icon(Material.NETHER_STAR, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.2f)
                                            )
                            ),
                    "user_settings" to
                            menuConfig(
                                    openSound = Sound.BLOCK_CHEST_OPEN,
                                    icons =
                                            mapOf(
                                                    "back" to icon(Material.ARROW, Sound.UI_BUTTON_CLICK, 2.0f)
                                            )
                            ),
                    "tour" to
                            menuConfig(
                                    openSound = Sound.BLOCK_CHEST_OPEN,
                                    icons = mapOf(
                                            "back" to icon(Material.REDSTONE, Sound.UI_BUTTON_CLICK, 2.0f),
                                            "create" to icon(Material.NETHER_STAR, Sound.UI_BUTTON_CLICK, 2.0f),
                                            "world" to icon(Material.GRASS_BLOCK, Sound.UI_BUTTON_CLICK, 1.5f),
                                            "info" to icon(Material.REDSTONE_TORCH, Sound.UI_BUTTON_CLICK, 2.0f),
                                            "tour" to icon(Material.WHITE_CARPET, Sound.UI_BUTTON_CLICK, 2.0f),
                                            "sign" to icon(Material.OAK_SIGN, Sound.UI_BUTTON_CLICK, 2.0f),
                                            "save" to icon(Material.LAVA_BUCKET, Sound.UI_BUTTON_CLICK, 1.5f)
                                    )
                            ),
                    "visit" to
                            menuConfig(
                                    openSound = Sound.BLOCK_CHEST_OPEN,
                                    icons =
                                            mapOf(
                                                    "back" to icon(Material.ARROW, Sound.UI_BUTTON_CLICK, 2.0f),
                                                    "next_page" to icon(Material.ARROW, Sound.UI_BUTTON_CLICK, 1.5f),
                                                    "prev_page" to icon(Material.ARROW, Sound.UI_BUTTON_CLICK, 1.5f),
                                                    "world_item" to icon(sound = Sound.UI_BUTTON_CLICK, pitch = 2.0f),
                                                    "favorite_add" to icon(sound = Sound.ENTITY_PLAYER_LEVELUP, pitch = 2.0f),
                                                    "favorite_remove" to icon(sound = Sound.BLOCK_IRON_TRAPDOOR_CLOSE, pitch = 1.5f),
                                                    "access_denied" to icon(sound = Sound.ENTITY_VILLAGER_NO, pitch = 1.0f)
                                            )
                            ),
                    "world_settings" to
                            menuConfig(
                                    openSound = Sound.BLOCK_IRON_TRAPDOOR_OPEN,
                                    icons =
                                            mapOf(
                                                    "back" to icon(Material.ARROW, Sound.UI_BUTTON_CLICK, 2.0f),
                                                    "confirm" to icon(Material.LIME_WOOL, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.5f),
                                                    "cancel" to icon(Material.RED_WOOL, Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f),
                                                    "info" to icon(Material.NAME_TAG, Sound.UI_BUTTON_CLICK, 2.0f),
                                                    "spawn" to icon(Material.COMPASS, Sound.UI_BUTTON_CLICK, 2.0f),
                                                    "icon" to icon(Material.ANVIL, Sound.UI_BUTTON_CLICK, 2.0f),
                                                    "tags" to icon(Material.BOOK, Sound.UI_BUTTON_CLICK, 2.0f),
                                                    "expand" to icon(Material.FILLED_MAP, Sound.UI_BUTTON_CLICK, 2.0f),
                                                    "publish" to icon(Material.OAK_DOOR, Sound.UI_BUTTON_CLICK, 2.0f),
                                                    "member" to icon(Material.PLAYER_HEAD, Sound.UI_BUTTON_CLICK, 2.0f),
                                                    "critical" to icon(Material.TNT, Sound.UI_BUTTON_CLICK, 2.0f),
                                                    "visitors" to icon(Material.SPYGLASS, Sound.UI_BUTTON_CLICK, 2.0f),
                                                    "notification" to icon(Material.BELL, Sound.UI_BUTTON_CLICK, 2.0f)
                                            )
                            )
            )

    private fun menuConfig(
            openSound: Sound,
            openPitch: Float = 1.0f,
            icons: Map<String, IconConfig>
    ): MenuConfig = MenuConfig(openSound, openPitch, icons)

    private fun icon(
            material: Material? = null,
            sound: Sound? = null,
            pitch: Float = 1.0f
    ): IconConfig = IconConfig(material, sound, pitch)

    /**
     * 初期化
     */
    fun initialize() {
        plugin.logger.info("Loaded ${menuConfigs.size} hardcoded menu configurations")
    }

    /**
     * 指定メニューのアイコンMaterialを取得
     */
    fun getIconMaterial(menuId: String, iconId: String, default: Material = Material.BARRIER): Material {
        return menuConfigs[menuId]?.icons?.get(iconId)?.material ?: default
    }

    /**
     * 指定メニューのアイコン用サウンドを取得
     */
    fun getIconSound(menuId: String, iconId: String): Sound? {
        return menuConfigs[menuId]?.icons?.get(iconId)?.sound
    }

    /**
     * 指定メニューのアイコン用サウンドピッチを取得
     */
    fun getIconSoundPitch(menuId: String, iconId: String, default: Float = 1.0f): Float {
        return menuConfigs[menuId]?.icons?.get(iconId)?.pitch ?: default
    }

    /**
     * メニューを開いた時のサウンドを取得
     */
    fun getOpenSound(menuId: String): Sound? {
        return menuConfigs[menuId]?.openSound
    }

    /**
     * メニューを開いた時のサウンドピッチを取得
     */
    fun getOpenSoundPitch(menuId: String, default: Float = 1.0f): Float {
        return menuConfigs[menuId]?.openPitch ?: default
    }
}
