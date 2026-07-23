package me.awabi2048.myworldmanager.util

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiNameStyle
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
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
    val EXTENSION_ID = NamespacedKey("myworldmanager", "extension_id")
    val TEMPLATE_ID = NamespacedKey("myworldmanager", "template_id")

    // Types
    const val TYPE_PORTAL = "portal"
    const val TYPE_WORLD_GATE = "world_gate"
    const val TYPE_EMPTY_BIOME_BOTTLE = "empty_biome_bottle"
    const val TYPE_BOTTLED_BIOME_AIR = "bottled_biome_air"
    const val TYPE_MOON_STONE = "moon_stone"
    const val TYPE_WORLD_SEED = "world_seed"
    const val TYPE_TOUR_SIGN = "tour_sign"
    const val TYPE_LIKE_SIGN = TYPE_TOUR_SIGN

    const val TYPE_GUI_NAV_PREV = "gui_nav_prev"
    const val TYPE_GUI_NAV_NEXT = "gui_nav_next"
    const val TYPE_GUI_BACK = "gui_back"
    const val TYPE_GUI_INVITE = "gui_invite"
    const val TYPE_GUI_INVITE_TARGET_HEAD = "gui_invite_target_head"
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
    const val TYPE_GUI_CREATION_TEMPLATE_USE = "gui_creation_template_use"
    const val TYPE_GUI_CREATION_TEMPLATE_PREVIEW = "gui_creation_template_preview"
    const val TYPE_GUI_CREATION_TEMPLATE_CHANGE = "gui_creation_template_change"
    const val TYPE_GUI_CREATION_SPAWN_LOCATION = "gui_creation_spawn_location"
    const val TYPE_GUI_CREATION_DIMENSION = "gui_creation_dimension"
    const val TYPE_GUI_MEMBER_ITEM = "gui_member_item"
    const val TYPE_GUI_MEMBER_INVITE = "gui_member_invite"
    const val TYPE_GUI_MEMBER_ADMIN_OWNER_RESET = "gui_member_admin_owner_reset"
    const val TYPE_GUI_MEMBER_PENDING_INVITE = "gui_member_pending_invite"
    const val TYPE_GUI_MEMBER_PENDING_REQUEST = "gui_member_pending_request"
    const val TYPE_GUI_SETTING_TAGS = "gui_setting_tags"
    const val TYPE_GUI_DISCOVERY_ITEM = "discovery_world_item"
    const val TYPE_GUI_DISCOVERY_SORT = "discovery_sort_button"
    const val TYPE_GUI_DISCOVERY_TAG = "discovery_tag_button"
    const val TYPE_GUI_DISCOVERY_SPECIAL_FILTER = "discovery_special_filter_button"
    const val TYPE_GUI_WORLD_ITEM = "gui_world_item"
    const val TYPE_GUI_SETTING_VISITOR = "gui_setting_visitor"
    const val TYPE_GUI_VISITOR_ITEM = "gui_visitor_item"
    const val TYPE_GUI_SETTING_NOTIFICATION = "gui_setting_notification"
    const val TYPE_GUI_SETTING_CRITICAL = "gui_setting_critical"
    const val TYPE_GUI_SETTING_RESET_EXPANSION = "gui_setting_reset_expansion"
    const val TYPE_GUI_SETTING_STEP_BACK_EXPANSION = "gui_setting_step_back_expansion"
    const val TYPE_GUI_SETTING_DELETE_WORLD = "gui_setting_delete_world"
    const val TYPE_GUI_USER_SETTINGS_BUTTON = "gui_user_settings_button"
    const val TYPE_GUI_USER_SETTING_NOTIFICATION = "gui_user_setting_notification"
    const val TYPE_GUI_USER_SETTING_LANGUAGE = "gui_user_setting_language"
    const val TYPE_GUI_USER_SETTING_CRITICAL_VISIBILITY = "gui_user_setting_critical_visibility"
    const val TYPE_GUI_USER_SETTING_TOUR_NAVIGATION = "gui_user_setting_tour_navigation"
    const val TYPE_GUI_USER_SETTING_MEET_ENABLED = "gui_user_setting_meet_enabled"
    const val TYPE_GUI_RETURN = "gui_return"
    const val TYPE_GUI_SETTING_ANNOUNCEMENT = "gui_setting_announcement"
    const val TYPE_GUI_SETTING_PORTALS = "gui_setting_portals"
    const val TYPE_GUI_CREATION_BUTTON = "gui_creation_button"
    const val TYPE_GUI_PLAYER_STATS = "gui_player_stats"
    const val TYPE_GUI_PENDING_BUTTON = "gui_pending_button"
    const val TYPE_GUI_SETTING_ENVIRONMENT = "gui_setting_environment"
    const val TYPE_GUI_ENV_GRAVITY = "gui_env_gravity"
    const val TYPE_GUI_ENV_WEATHER = "gui_env_weather"
    const val TYPE_GUI_ENV_BIOME = "gui_env_biome"

    const val TYPE_GUI_MEET_STATUS_SELECTOR = "gui_meet_status_selector"
    const val TYPE_GUI_MEET_SETTINGS_BUTTON = "gui_meet_settings_button"
    const val TYPE_GUI_MEET_STATUS_TOGGLE = "gui_meet_status_toggle"
    const val TYPE_GUI_PENDING_ENTRY = "gui_pending_entry"
    const val TYPE_GUI_EXTENSION = "gui_extension"

    // お気に入りメニュー用タグ
    const val TYPE_GUI_FAVORITE_OTHER_WORLDS = "gui_favorite_other_worlds"
    const val TYPE_GUI_FAVORITE_TOGGLE = "gui_favorite_toggle"
    const val TYPE_GUI_FAVORITE_LIST = "gui_favorite_list"
    const val TYPE_GUI_FAVORITE_TAG = "gui_favorite_tag"
    const val TYPE_GUI_MEMBER_REQUEST_CONFIRM = "gui_member_request_confirm"
    const val TYPE_GUI_TOUR_ITEM = "gui_tour_item"
    const val TYPE_GUI_TOUR_CURRENT_WORLD = "gui_tour_current_world"
    const val TYPE_GUI_TOUR_CREATE = "gui_tour_create"
    const val TYPE_GUI_TOUR_INFO = "gui_tour_info"
    const val TYPE_GUI_TOUR_BACK = "gui_tour_back"
    const val TYPE_GUI_TOUR_SAVE = "gui_tour_save"
    const val TYPE_GUI_TOUR_DELETE = "gui_tour_delete"
    const val TYPE_GUI_TOUR_EDIT_TEXT = "gui_tour_edit_text"
    const val TYPE_GUI_TOUR_SIGN_ITEM = "gui_tour_sign_item"
    const val TYPE_GUI_TOUR_ADD_SIGN = "gui_tour_add_sign"
    const val TYPE_GUI_TOUR_WAYPOINT_ITEM = "gui_tour_waypoint_item"
    const val TYPE_GUI_TOUR_ADD_WAYPOINT = "gui_tour_add_waypoint"
    const val TYPE_GUI_SETTING_TOUR = "gui_setting_tour"

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
    const val TYPE_GUI_ADMIN_CURRENT_WORLD_INFO = "gui_admin_current_world_info"
    const val TYPE_GUI_ADMIN_MENU_SWITCH = "gui_admin_menu_switch"

    @JvmStatic
    fun tagItem(item: ItemStack, type: String) {
        val meta = item.itemMeta ?: return
        meta.persistentDataContainer.set(ITEM_TYPE, PersistentDataType.STRING, type)
        val genericStyle = when (type) {
            TYPE_GUI_RETURN,
            TYPE_GUI_BACK,
            TYPE_GUI_NAV_PREV,
            TYPE_GUI_NAV_NEXT -> GuiNameStyle.MUTED
            TYPE_GUI_CONFIRM -> GuiNameStyle.SUCCESS
            TYPE_GUI_CANCEL -> GuiNameStyle.DANGER
            else -> null
        }
        if (genericStyle != null) {
            // 生成元に依存せず、汎用操作アイコンをNameのみの共通表示へ揃える。
            val fallbackLabel = when (type) {
                TYPE_GUI_CONFIRM -> "実行"
                TYPE_GUI_CANCEL -> "キャンセル"
                TYPE_GUI_NAV_PREV -> "前へ"
                TYPE_GUI_NAV_NEXT -> "次へ"
                else -> "戻る"
            }
            val label = PlainTextComponentSerializer.plainText()
                .serialize(meta.displayName() ?: Component.text(fallbackLabel))
                .ifBlank { fallbackLabel }
            meta.displayName(CCSystem.getAPI().getGuiElementService().name(label, genericStyle))
            meta.lore(null)
        }
        applyIconSettings(meta)
        item.itemMeta = meta
    }

    private fun applyIconSettings(meta: ItemMeta) {
        // GUIアイコンでは素材固有のレコード/道具/属性ツールチップをまとめて隠し、名前とLoreだけを見せる。
        meta.addItemFlags(*ItemFlag.values())
        ItemMetaCompat.hideAdditionalTooltip(meta)
    }

    @JvmStatic
    fun getType(item: ItemStack): String? {
        val meta = item.itemMeta ?: return null
        return meta.persistentDataContainer.get(ITEM_TYPE, PersistentDataType.STRING)
    }

    @JvmStatic
    fun setTemplateId(item: ItemStack, templateId: String) {
        val meta = item.itemMeta ?: return
        meta.persistentDataContainer.set(TEMPLATE_ID, PersistentDataType.STRING, templateId)
        item.itemMeta = meta
    }

    @JvmStatic
    fun getTemplateId(item: ItemStack): String? =
        item.itemMeta?.persistentDataContainer?.get(TEMPLATE_ID, PersistentDataType.STRING)

    @JvmStatic
    fun isType(item: ItemStack, type: String): Boolean {
        return getType(item) == type
    }

    @JvmStatic
    fun setWorldUuid(item: ItemStack, uuid: java.util.UUID) {
        val meta = item.itemMeta ?: return
        meta.persistentDataContainer.set(WORLD_UUID, PersistentDataType.STRING, uuid.toString())
        applyIconSettings(meta)
        item.itemMeta = meta
    }

    @JvmStatic
    fun getWorldUuid(item: ItemStack): java.util.UUID? {
        val meta = item.itemMeta ?: return null
        val uuidStr = meta.persistentDataContainer.get(WORLD_UUID, PersistentDataType.STRING) ?: return null
        return try { java.util.UUID.fromString(uuidStr) } catch (e: Exception) { null }
    }

    @JvmStatic
    fun setTargetPage(item: ItemStack, page: Int) {
        val meta = item.itemMeta ?: return
        meta.persistentDataContainer.set(TARGET_PAGE, PersistentDataType.INTEGER, page)
        item.itemMeta = meta
    }

    @JvmStatic
    fun getTargetPage(item: ItemStack): Int? {
        val meta = item.itemMeta ?: return null
        return meta.persistentDataContainer.get(TARGET_PAGE, PersistentDataType.INTEGER)
    }

    @JvmStatic
    fun setPortalUuid(item: ItemStack, uuid: java.util.UUID) {
        val meta = item.itemMeta ?: return
        meta.persistentDataContainer.set(PORTAL_UUID, PersistentDataType.STRING, uuid.toString())
        item.itemMeta = meta
    }

    @JvmStatic
    fun getPortalUuid(item: ItemStack): java.util.UUID? {
        val meta = item.itemMeta ?: return null
        val uuidStr = meta.persistentDataContainer.get(PORTAL_UUID, PersistentDataType.STRING) ?: return null
        return try { java.util.UUID.fromString(uuidStr) } catch (e: Exception) { null }
    }

    @JvmStatic
    fun setBiomeId(item: ItemStack, biomeId: String) {
        val meta = item.itemMeta ?: return
        meta.persistentDataContainer.set(BIOME_ID, PersistentDataType.STRING, biomeId)
        item.itemMeta = meta
    }

    @JvmStatic
    fun getBiomeId(item: ItemStack): String? {
        val meta = item.itemMeta ?: return null
        return meta.persistentDataContainer.get(BIOME_ID, PersistentDataType.STRING)
    }

    @JvmStatic
    fun setString(item: ItemStack, keyStr: String, value: String) {
        val meta = item.itemMeta ?: return
        val key = NamespacedKey("myworldmanager", keyStr)
        meta.persistentDataContainer.set(key, PersistentDataType.STRING, value)
        item.itemMeta = meta
    }

    @JvmStatic
    fun getString(item: ItemStack, keyStr: String): String? {
        val meta = item.itemMeta ?: return null
        val key = NamespacedKey("myworldmanager", keyStr)
        return meta.persistentDataContainer.get(key, PersistentDataType.STRING)
    }

    @JvmStatic
    fun setExtensionId(item: ItemStack, extensionId: String) {
        val meta = item.itemMeta ?: return
        meta.persistentDataContainer.set(EXTENSION_ID, PersistentDataType.STRING, extensionId)
        applyIconSettings(meta)
        item.itemMeta = meta
    }

    @JvmStatic
    fun getExtensionId(item: ItemStack): String? {
        val meta = item.itemMeta ?: return null
        return meta.persistentDataContainer.get(EXTENSION_ID, PersistentDataType.STRING)
    }
}
