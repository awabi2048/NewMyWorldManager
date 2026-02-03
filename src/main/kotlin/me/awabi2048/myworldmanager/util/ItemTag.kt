package me.awabi2048.myworldmanager.util

import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType

object ItemTag {
    val ITEM_TYPE = NamespacedKey("myworldmanager", "item_type")
    val WORLD_UUID = NamespacedKey("myworldmanager", "world_uuid")
    val PORTAL_UUID = NamespacedKey("myworldmanager", "portal_uuid")
    val TARGET_PAGE = NamespacedKey("myworldmanager", "target_page")
    val BIOME_ID = NamespacedKey("myworldmanager", "biome_id")
    
    // Types
    const val TYPE_PORTAL = "portal"
    const val TYPE_EMPTY_BIOME_BOTTLE = "empty_biome_bottle"
    const val TYPE_BOTTLED_BIOME_AIR = "bottled_biome_air"
    const val TYPE_MOON_STONE = "moon_stone"
    const val TYPE_WORLD_SEED = "world_seed"

    const val TYPE_GUI_NAV_PREV = "gui_nav_prev"
    const val TYPE_GUI_NAV_NEXT = "gui_nav_next"
    const val TYPE_GUI_BACK = "gui_back"
    const val TYPE_GUI_INVITE = "gui_invite"
    const val TYPE_GUI_DECORATION = "gui_decoration"
    const val TYPE_GUI_INFO = "gui_info"
    const val TYPE_GUI_PORTAL_TOGGLE_TEXT = "gui_portal_toggle_text"
    const val TYPE_GUI_PORTAL_CYCLE_COLOR = "gui_portal_cycle_color"
    const val TYPE_GUI_PORTAL_REMOVE = "gui_portal_remove"
    const val TYPE_GUI_SETTING_INFO = "gui_setting_info"
    const val TYPE_GUI_SETTING_SPAWN = "gui_setting_spawn"
    const val TYPE_GUI_SETTING_ICON = "gui_setting_icon"
    const val TYPE_GUI_SETTING_EXPAND = "gui_setting_expand"
    const val TYPE_GUI_SETTING_EXPAND_DIRECTION = "gui_setting_expand_direction"
    const val TYPE_GUI_SETTING_PUBLISH = "gui_setting_publish"
    const val TYPE_GUI_SETTING_MEMBER = "gui_setting_member"
    const val TYPE_GUI_SETTING_ARCHIVE = "gui_setting_archive"
    const val TYPE_GUI_CANCEL = "gui_cancel"
    const val TYPE_GUI_CONFIRM = "gui_confirm"
    const val TYPE_GUI_CREATION_TYPE_TEMPLATE = "gui_creation_type_template"
    const val TYPE_GUI_CREATION_TYPE_SEED = "gui_creation_type_seed"
    const val TYPE_GUI_CREATION_TYPE_RANDOM = "gui_creation_type_random"
    const val TYPE_GUI_CREATION_TEMPLATE_ITEM = "gui_creation_template_item"
    const val TYPE_GUI_MEMBER_ITEM = "gui_member_item"
    const val TYPE_GUI_MEMBER_INVITE = "gui_member_invite"
    const val TYPE_GUI_SETTING_TAGS = "gui_setting_tags"
    const val TYPE_GUI_DISCOVERY_ITEM = "discovery_world_item"
    const val TYPE_GUI_DISCOVERY_SORT = "discovery_sort_button"
    const val TYPE_GUI_DISCOVERY_TAG = "discovery_tag_button"
    const val TYPE_GUI_WORLD_ITEM = "gui_world_item"
    const val TYPE_GUI_SETTING_VISITOR = "gui_setting_visitor"
    const val TYPE_GUI_VISITOR_ITEM = "gui_visitor_item"
    const val TYPE_GUI_SETTING_NOTIFICATION = "gui_setting_notification"
    const val TYPE_GUI_SETTING_CRITICAL = "gui_setting_critical"
    const val TYPE_GUI_SETTING_RESET_EXPANSION = "gui_setting_reset_expansion"
    const val TYPE_GUI_SETTING_DELETE_WORLD = "gui_setting_delete_world"
    const val TYPE_GUI_USER_SETTINGS_BUTTON = "gui_user_settings_button"
    const val TYPE_GUI_USER_SETTING_NOTIFICATION = "gui_user_setting_notification"
    const val TYPE_GUI_USER_SETTING_LANGUAGE = "gui_user_setting_language"
    const val TYPE_GUI_USER_SETTING_CRITICAL_VISIBILITY = "gui_user_setting_critical_visibility"
    const val TYPE_GUI_USER_SETTING_BETA_FEATURES = "gui_user_setting_beta_features"
    const val TYPE_GUI_USER_SETTING_MEET_ENABLED = "gui_user_setting_meet_enabled"
    const val TYPE_GUI_RETURN = "gui_return"
    const val TYPE_GUI_SETTING_ANNOUNCEMENT = "gui_setting_announcement"
    const val TYPE_GUI_SETTING_PORTALS = "gui_setting_portals"
    const val TYPE_GUI_CREATION_BUTTON = "gui_creation_button"
    const val TYPE_GUI_PLAYER_STATS = "gui_player_stats"
    const val TYPE_GUI_SETTING_ENVIRONMENT = "gui_setting_environment"
    const val TYPE_GUI_ENV_GRAVITY = "gui_env_gravity"
    const val TYPE_GUI_ENV_WEATHER = "gui_env_weather"
    const val TYPE_GUI_ENV_BIOME = "gui_env_biome"

    const val TYPE_GUI_MEET_STATUS_SELECTOR = "gui_meet_status_selector"
    const val TYPE_GUI_MEET_SETTINGS_BUTTON = "gui_meet_settings_button"
    const val TYPE_GUI_MEET_STATUS_TOGGLE = "gui_meet_status_toggle"
    
    // お気に入りメニュー用タグ
    const val TYPE_GUI_FAVORITE_OTHER_WORLDS = "gui_favorite_other_worlds"
    const val TYPE_GUI_FAVORITE_TOGGLE = "gui_favorite_toggle"
    const val TYPE_GUI_FAVORITE_LIST = "gui_favorite_list"
    const val TYPE_GUI_FAVORITE_TAG = "gui_favorite_tag"
    
    // 管理者GUIフィルター/ソート用タグ
    const val TYPE_GUI_ADMIN_FILTER_ARCHIVE = "gui_admin_filter_archive"
    const val TYPE_GUI_ADMIN_FILTER_PUBLISH = "gui_admin_filter_publish"
    const val TYPE_GUI_ADMIN_FILTER_PLAYER = "gui_admin_filter_player"
    const val TYPE_GUI_ADMIN_SORT = "gui_admin_sort"
    const val TYPE_GUI_ADMIN_PORTAL_SORT = "gui_admin_portal_sort"

    // 管理者メニュー用タグ
    const val TYPE_GUI_ADMIN_UPDATE_DATA = "gui_admin_update_data"
    const val TYPE_GUI_ADMIN_REPAIR_TEMPLATES = "gui_admin_repair_templates"
    const val TYPE_GUI_ADMIN_CREATE_TEMPLATE = "gui_admin_create_template"
    const val TYPE_GUI_ADMIN_ARCHIVE_ALL = "gui_admin_archive_all"
    const val TYPE_GUI_ADMIN_CONVERT = "gui_admin_convert"
    const val TYPE_GUI_ADMIN_EXPORT = "gui_admin_export"
    const val TYPE_GUI_ADMIN_INFO = "gui_admin_info"
    const val TYPE_GUI_ADMIN_PORTALS = "gui_admin_portals"
    const val TYPE_GUI_ADMIN_UNLINK = "gui_admin_unlink"

    fun tagItem(item: ItemStack, type: String) {
        val meta = item.itemMeta ?: return
        meta.persistentDataContainer.set(ITEM_TYPE, PersistentDataType.STRING, type)
        applyIconSettings(meta)
        item.itemMeta = meta
    }

    private fun applyIconSettings(meta: ItemMeta) {
        // Hide attributes, enchantments, etc. while keeping Name and Lore
        meta.addItemFlags(
            ItemFlag.HIDE_ATTRIBUTES,
            ItemFlag.HIDE_ENCHANTS,
            ItemFlag.HIDE_DESTROYS,
            ItemFlag.HIDE_PLACED_ON,
            ItemFlag.HIDE_ADDITIONAL_TOOLTIP,
            ItemFlag.HIDE_DYE,
            ItemFlag.HIDE_ARMOR_TRIM,
            ItemFlag.HIDE_STORED_ENCHANTS
        )
    }

    fun getType(item: ItemStack): String? {
        val meta = item.itemMeta ?: return null
        return meta.persistentDataContainer.get(ITEM_TYPE, PersistentDataType.STRING)
    }
    
    fun isType(item: ItemStack, type: String): Boolean {
        return getType(item) == type
    }

    fun setWorldUuid(item: ItemStack, uuid: java.util.UUID) {
        val meta = item.itemMeta ?: return
        meta.persistentDataContainer.set(WORLD_UUID, PersistentDataType.STRING, uuid.toString())
        applyIconSettings(meta)
        item.itemMeta = meta
    }

    fun getWorldUuid(item: ItemStack): java.util.UUID? {
        val meta = item.itemMeta ?: return null
        val uuidStr = meta.persistentDataContainer.get(WORLD_UUID, PersistentDataType.STRING) ?: return null
        return try { java.util.UUID.fromString(uuidStr) } catch (e: Exception) { null }
    }

    fun setTargetPage(item: ItemStack, page: Int) {
        val meta = item.itemMeta ?: return
        meta.persistentDataContainer.set(TARGET_PAGE, PersistentDataType.INTEGER, page)
        item.itemMeta = meta
    }

    fun getTargetPage(item: ItemStack): Int? {
        val meta = item.itemMeta ?: return null
        return meta.persistentDataContainer.get(TARGET_PAGE, PersistentDataType.INTEGER)
    }

    fun setPortalUuid(item: ItemStack, uuid: java.util.UUID) {
        val meta = item.itemMeta ?: return
        meta.persistentDataContainer.set(PORTAL_UUID, PersistentDataType.STRING, uuid.toString())
        item.itemMeta = meta
    }

    fun getPortalUuid(item: ItemStack): java.util.UUID? {
        val meta = item.itemMeta ?: return null
        val uuidStr = meta.persistentDataContainer.get(PORTAL_UUID, PersistentDataType.STRING) ?: return null
        return try { java.util.UUID.fromString(uuidStr) } catch (e: Exception) { null }
    }
    
    fun setBiomeId(item: ItemStack, biomeId: String) {
        val meta = item.itemMeta ?: return
        meta.persistentDataContainer.set(BIOME_ID, PersistentDataType.STRING, biomeId)
        item.itemMeta = meta
    }
    
    fun getBiomeId(item: ItemStack): String? {
        val meta = item.itemMeta ?: return null
        return meta.persistentDataContainer.get(BIOME_ID, PersistentDataType.STRING)
    }
}
