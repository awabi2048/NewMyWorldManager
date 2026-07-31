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
                                                    "back" to icon(Material.ARROW),
                                                    "next_page" to icon(Material.ARROW),
                                                    "prev_page" to icon(Material.ARROW),
                                                    "info" to icon(Material.PAPER),
                                                    "world_item" to icon()
                                            )
                            ),
                    "admin_portals" to
                            menuConfig(
                                    openSound = Sound.BLOCK_CHEST_OPEN,
                                    icons =
                                            mapOf(
                                                    "portal_item" to icon(Material.END_PORTAL_FRAME),
                                                    "sort" to icon(Material.HOPPER),
                                                    "back" to icon(Material.ARROW),
                                                    "next_page" to icon(Material.ARROW),
                                                    "prev_page" to icon(Material.ARROW)
                                            )
                            ),
                    "admin_world" to
                            menuConfig(
                                    openSound = Sound.BLOCK_CHEST_OPEN,
                                    icons =
                                            mapOf(
                                                    "world_item" to icon(Material.GRASS_BLOCK),
                                                    "filter_archive" to icon(Material.CHEST),
                                                    "filter_publish" to icon(Material.ENDER_EYE),
                                                    "filter_player" to icon(Material.PLAYER_HEAD),
                                                    "sort" to icon(Material.HOPPER),
                                                    "back" to icon(Material.ARROW),
                                                    "next_page" to icon(Material.ARROW),
                                                    "prev_page" to icon(Material.ARROW)
                                            )
                            ),
                    "creation" to
                            menuConfig(
                                    openSound = Sound.BLOCK_IRON_TRAPDOOR_OPEN,
                                    icons =
                                            mapOf(
                                                    "back" to icon(Material.ARROW),
                                                    "confirm" to icon(Material.LIME_WOOL),
                                                    "cancel" to icon(Material.RED_WOOL),
                                                    "template" to icon(Material.GRASS_BLOCK),
                                                    "seed" to icon(Material.WHEAT_SEEDS),
                                                    "random" to icon(Material.ENDER_PEARL),
                                                    "insufficient_points" to icon(),
                                                    "limit_reached" to icon()
                                            )
                            ),
                    "discovery" to
                            menuConfig(
                                    openSound = Sound.UI_TOAST_IN,
                                    icons =
                                            mapOf(
                                                    "back" to icon(Material.ARROW),
                                                    "next_page" to icon(Material.ARROW),
                                                    "prev_page" to icon(Material.ARROW),
                                                    "sort" to icon(Material.HOPPER),
                                                    "tag_filter" to icon(Material.NAME_TAG),
                                                    "world_item" to icon(),
                                                    "favorite_add" to icon(),
                                                    "favorite_remove" to icon(),
                                                    "access_denied" to icon()
                                            )
                            ),
                    "environment" to
                            menuConfig(
                                    openSound = Sound.BLOCK_CHEST_OPEN,
                                    icons =
                                            mapOf(
                                                    "back" to icon(Material.ARROW),
                                                    "insufficient_points" to icon(),
                                                    "gravity_change" to icon(),
                                                    "biome_change" to icon(),
                                                    "weather_change" to icon()
                                            )
                            ),
                    "environment_confirm" to
                            menuConfig(
                                    openSound = Sound.BLOCK_CHEST_OPEN,
                                    icons =
                                            mapOf(
                                                    "confirm" to icon(Material.LIME_CONCRETE),
                                                    "cancel" to icon(Material.RED_CONCRETE)
                                            )
                            ),
                    "favorite" to
                            menuConfig(
                                    openSound = Sound.BLOCK_CHEST_OPEN,
                                    icons =
                                            mapOf(
                                                    "back" to icon(Material.ARROW),
                                                    "next_page" to icon(Material.ARROW),
                                                    "prev_page" to icon(Material.ARROW),
                                                    "world_item" to icon(),
                                                    "favorite_add" to icon(),
                                                    "favorite_remove" to icon()
                                            )
                            ),
                    "favorite_menu" to
                            menuConfig(
                                    openSound = Sound.BLOCK_CHEST_OPEN,
                                    icons =
                                            mapOf(
                                                    "other_worlds" to icon(Material.COMPASS),
                                                    "toggle_on" to icon(Material.RED_DYE),
                                                    "toggle_off" to icon(Material.GRAY_DYE),
                                                    "list" to icon(Material.BOOK)
                                            )
                            ),
                    "meet" to
                            menuConfig(
                                    openSound = Sound.BLOCK_CHEST_OPEN,
                                    icons =
                                            mapOf(
                                                    "back" to icon(Material.ARROW),
                                                    "access_denied" to icon()
                                            )
                            ),
                    "player_world" to
                            menuConfig(
                                    openSound = Sound.BLOCK_CHEST_OPEN,
                                    icons =
                                            mapOf(
                                                    "back" to icon(Material.ARROW),
                                                    "next_page" to icon(Material.ARROW),
                                                    "prev_page" to icon(Material.ARROW),
                                                    "world_item" to icon()
                                            )
                            ),
                    "portal" to
                            menuConfig(
                                    openSound = Sound.BLOCK_ENDER_CHEST_OPEN,
                                    icons =
                                            mapOf(
                                                    "back" to icon(Material.ARROW),
                                                    "confirm" to icon(Material.LIME_WOOL),
                                                    "cancel" to icon(Material.RED_WOOL),
                                                    "toggle_text" to icon(Material.OAK_SIGN),
                                                    "color" to icon(Material.MAGENTA_DYE),
                                                    "remove" to icon(Material.BARRIER)
                                            )
                            ),
                    "portal_manage" to
                            menuConfig(
                                    openSound = Sound.BLOCK_CHEST_OPEN,
                                    icons =
                                            mapOf(
                                                    "back" to icon(Material.ARROW),
                                                    "next_page" to icon(Material.ARROW),
                                                    "prev_page" to icon(Material.ARROW),
                                                    "portal_item" to icon(),
                                                    "remove" to icon(Material.BARRIER)
                                            )
                            ),
                    "spotlight_confirm" to
                            menuConfig(
                                    openSound = Sound.BLOCK_CHEST_OPEN,
                                    icons =
                                            mapOf(
                                                    "confirm" to icon(Material.LIME_CONCRETE),
                                                    "cancel" to icon(Material.RED_CONCRETE)
                                            )
                            ),
                    "template_wizard" to
                            menuConfig(
                                    openSound = Sound.BLOCK_ENCHANTMENT_TABLE_USE,
                                    icons =
                                            mapOf(
                                                    "back" to icon(Material.ARROW),
                                                    "icon_select" to icon(Material.ITEM_FRAME),
                                                    "name_input" to icon(Material.NAME_TAG),
                                                    "desc_input" to icon(Material.WRITABLE_BOOK),
                                                    "origin_set" to icon(Material.COMPASS),
                                                    "save_confirm" to icon(Material.NETHER_STAR)
                                            )
                            ),
                    "user_settings" to
                            menuConfig(
                                    openSound = Sound.BLOCK_CHEST_OPEN,
                                    icons =
                                            mapOf(
                                                    "back" to icon(Material.ARROW)
                                            )
                            ),
                    "tour" to
                            menuConfig(
                                    openSound = Sound.BLOCK_CHEST_OPEN,
                                    icons = mapOf(
                                            "back" to icon(Material.REDSTONE),
                                            "create" to icon(Material.NETHER_STAR),
                                            "world" to icon(Material.GRASS_BLOCK),
                                            "info" to icon(Material.REDSTONE_TORCH),
                                            "tour" to icon(Material.WHITE_CARPET),
                                            "sign" to icon(Material.OAK_SIGN),
                                            "save" to icon(Material.LAVA_BUCKET)
                                    )
                            ),
                    "visit" to
                            menuConfig(
                                    openSound = Sound.BLOCK_CHEST_OPEN,
                                    icons =
                                            mapOf(
                                                    "back" to icon(Material.ARROW),
                                                    "next_page" to icon(Material.ARROW),
                                                    "prev_page" to icon(Material.ARROW),
                                                    "world_item" to icon(),
                                                    "favorite_add" to icon(),
                                                    "favorite_remove" to icon(),
                                                    "access_denied" to icon()
                                            )
                            ),
                    "world_settings" to
                            menuConfig(
                                    openSound = Sound.BLOCK_IRON_TRAPDOOR_OPEN,
                                    icons =
                                            mapOf(
                                                    "back" to icon(Material.ARROW),
                                                    "confirm" to icon(Material.LIME_WOOL),
                                                    "cancel" to icon(Material.RED_WOOL),
                                                    "info" to icon(Material.NAME_TAG),
                                                    "spawn" to icon(Material.COMPASS),
                                                    "icon" to icon(Material.ANVIL),
                                                    "tags" to icon(Material.BOOK),
                                                    "expand" to icon(Material.FILLED_MAP),
                                                    "publish" to icon(Material.OAK_DOOR),
                                                    "member" to icon(Material.PLAYER_HEAD),
                                                    "critical" to icon(Material.TNT),
                                                    "visitors" to icon(Material.SPYGLASS),
                                                    "notification" to icon(Material.BELL)
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
    ): IconConfig = IconConfig(material)

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
        // 戻るボタンは全メニューでCC-System規則のREDSTONEに統一する。
        if (iconId == "back") return Material.REDSTONE
        return menuConfigs[menuId]?.icons?.get(iconId)?.material ?: default
    }

    /**
     * 指定メニューのアイコン用サウンドを取得
     */

    /**
     * 指定メニューのアイコン用サウンドピッチを取得
     */

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
