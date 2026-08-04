package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.api.gui.MenuActionSafety
import com.awabi2048.ccsystem.api.gui.MenuReversibleContract
import me.awabi2048.myworldmanager.service.MwmReversibleContracts

/**
 * 可逆メニュー操作の正規定義です。
 *
 * 各画面はこの定義から契約を取得するため、分類表と実際にランタイムへ渡す契約が乖離しません。
 */
object MwmMenuActionSemantics {
    data class Entry(
        val id: String,
        val file: String,
        val action: String,
        val safety: MenuActionSafety,
        val providerId: String? = null,
        val operation: String? = null,
        val reason: String,
    )

    private val entries = listOf(
        Entry("admin-command-cancel", "AdminCommandGui.kt", "cancel", MenuActionSafety.NAVIGATION_ONLY, reason = "navigation bookkeeping and route replacement only"),
        Entry("admin-portal-sort", "AdminPortalGui.kt", "sort", MenuActionSafety.REVERSIBLE, MwmReversibleContracts.MENU_SESSION_PROVIDER, "admin_portal_sort", "portal sort session mutation"),
        Entry("creation-dimension", "CreationGui.kt", "DIMENSION", MenuActionSafety.REVERSIBLE, MwmReversibleContracts.CREATION_SESSION_PROVIDER, "dimension", "creation draft dimension mutation"),
        Entry("creation-cancel", "CreationGui.kt", "CANCEL", MenuActionSafety.REVERSIBLE, MwmReversibleContracts.CREATION_SESSION_PROVIDER, "cancel", "creation draft removal"),
        Entry("discovery-sort", "DiscoveryGui.kt", "sort", MenuActionSafety.REVERSIBLE, MwmReversibleContracts.MENU_SESSION_PROVIDER, "discovery_sort", "discovery session sort mutation"),
        Entry("discovery-tag", "DiscoveryGui.kt", "tag", MenuActionSafety.REVERSIBLE, MwmReversibleContracts.MENU_SESSION_PROVIDER, "discovery_tag", "discovery session tag mutation"),
        Entry("discovery-special", "DiscoveryGui.kt", "special_filter", MenuActionSafety.REVERSIBLE, MwmReversibleContracts.MENU_SESSION_PROVIDER, "discovery_special_filter", "discovery session filter mutation"),
        Entry("environment-weather", "EnvironmentGui.kt", "weather", MenuActionSafety.REVERSIBLE, MwmReversibleContracts.SETTINGS_SESSION_PROVIDER, "weather", "temporary weather mutation"),
        Entry("favorite-tag", "FavoriteGui.kt", "tag", MenuActionSafety.REVERSIBLE, MwmReversibleContracts.MENU_SESSION_PROVIDER, "favorite_tag", "favorite session tag mutation"),
        Entry("favorite-toggle", "FavoriteMenuGui.kt", "toggle", MenuActionSafety.REVERSIBLE, MwmReversibleContracts.PLAYER_STATE_PROVIDER, "favorite_toggle", "favorite repository mutation"),
        Entry("gate-cancel", "PortalListener.kt", "cancel_gate", MenuActionSafety.NAVIGATION_ONLY, reason = "message and close only"),
        Entry("bedrock-publish", "BedrockMenuService.kt", "cycle_publish", MenuActionSafety.REVERSIBLE, MwmReversibleContracts.WORLD_STATE_PROVIDER, "publish", "world publish metadata mutation"),
        Entry("bedrock-create", "BedrockMenuService.kt", "start_creation", MenuActionSafety.REVERSIBLE, MwmReversibleContracts.CREATION_SESSION_PROVIDER, "bedrock_start", "creation draft replacement"),
        Entry("bedrock-notification", "BedrockMenuService.kt", "toggle_notification", MenuActionSafety.REVERSIBLE, MwmReversibleContracts.USER_SETTINGS_PROVIDER, "notification", "user notification mutation"),
        Entry("bedrock-critical", "BedrockMenuService.kt", "toggle_critical", MenuActionSafety.REVERSIBLE, MwmReversibleContracts.USER_SETTINGS_PROVIDER, "critical_visibility", "user visibility mutation"),
        Entry("bedrock-tour", "BedrockMenuService.kt", "cycle_tour_navigation", MenuActionSafety.REVERSIBLE, MwmReversibleContracts.USER_SETTINGS_PROVIDER, "tour_navigation", "user navigation mode mutation"),
        Entry("meet-status", "MeetGui.kt", "status", MenuActionSafety.REVERSIBLE, MwmReversibleContracts.PLAYER_STATE_PROVIDER, "meet_status", "meet status and page mutation"),
        Entry("player-world-priority", "PlayerWorldGui.kt", "world", MenuActionSafety.REVERSIBLE, MwmReversibleContracts.DISPLAY_ORDER_PROVIDER, reason = "display order mutation"),
        Entry("portal-text", "PortalGui.kt", "toggle_text", MenuActionSafety.REVERSIBLE, MwmReversibleContracts.PORTAL_STATE_PROVIDER, "text", "portal text mutation"),
        Entry("portal-color", "PortalGui.kt", "cycle_color", MenuActionSafety.REVERSIBLE, MwmReversibleContracts.PORTAL_STATE_PROVIDER, "color", "portal color mutation"),
        Entry("template-icon", "TemplateWizardGui.kt", "icon", MenuActionSafety.INPUT_OR_EXTERNAL_SURFACE, reason = "cursor item is operation input"),
        Entry("template-origin", "TemplateWizardGui.kt", "origin", MenuActionSafety.REVERSIBLE, MwmReversibleContracts.DRAFT_PROVIDER, "template_origin", "template origin draft mutation"),
        Entry("template-cancel", "TemplateWizardGui.kt", "cancel", MenuActionSafety.REVERSIBLE, MwmReversibleContracts.DRAFT_PROVIDER, "template_cancel", "template draft removal"),
        Entry("tour-icon", "TourGui.kt", "edit_text_right", MenuActionSafety.REVERSIBLE, MwmReversibleContracts.DRAFT_PROVIDER, "tour_icon_pick", "tour icon-pick draft mutation"),
        Entry("tour-discard", "TourGui.kt", "discard_confirm", MenuActionSafety.REVERSIBLE, MwmReversibleContracts.DRAFT_PROVIDER, "tour_discard", "tour draft removal"),
        Entry("user-notification", "UserSettingsGui.kt", "notification", MenuActionSafety.REVERSIBLE, MwmReversibleContracts.USER_SETTINGS_PROVIDER, "notification", "user notification mutation"),
        Entry("user-critical", "UserSettingsGui.kt", "critical_visibility", MenuActionSafety.REVERSIBLE, MwmReversibleContracts.USER_SETTINGS_PROVIDER, "critical_visibility", "user visibility mutation"),
        Entry("user-tour", "UserSettingsGui.kt", "tour_navigation", MenuActionSafety.REVERSIBLE, MwmReversibleContracts.USER_SETTINGS_PROVIDER, "tour_navigation", "user navigation mode mutation"),
        Entry("visit-favorite", "VisitGui.kt", "world_right", MenuActionSafety.REVERSIBLE, MwmReversibleContracts.PLAYER_STATE_PROVIDER, "favorite_toggle", "favorite repository mutation"),
        Entry("visit-world-favorite", "VisitWorldGui.kt", "world_right", MenuActionSafety.REVERSIBLE, MwmReversibleContracts.PLAYER_STATE_PROVIDER, "favorite_toggle", "favorite repository mutation"),
        Entry("admin-archive-filter", "WorldGui.kt", "archiveFilter", MenuActionSafety.REVERSIBLE, MwmReversibleContracts.MENU_SESSION_PROVIDER, "admin_archive_filter", "admin session filter mutation"),
        Entry("admin-publish-filter", "WorldGui.kt", "publishFilter", MenuActionSafety.REVERSIBLE, MwmReversibleContracts.MENU_SESSION_PROVIDER, "admin_publish_filter", "admin session filter mutation"),
        Entry("admin-player-filter-left", "WorldGui.kt", "playerFilter_left", MenuActionSafety.REVERSIBLE, MwmReversibleContracts.MENU_SESSION_PROVIDER, "admin_player_filter", "admin session filter mutation"),
        Entry("admin-player-filter-right", "WorldGui.kt", "playerFilter_right", MenuActionSafety.INPUT_OR_EXTERNAL_SURFACE, reason = "opens player-name input"),
        Entry("admin-sort", "WorldGui.kt", "sort", MenuActionSafety.REVERSIBLE, MwmReversibleContracts.MENU_SESSION_PROVIDER, "admin_sort", "admin session sort mutation"),
        Entry("world-publish", "WorldSettingsGui.kt", "CYCLE_PUBLISH", MenuActionSafety.REVERSIBLE, MwmReversibleContracts.WORLD_STATE_PROVIDER, "publish", "world publish metadata mutation"),
        Entry("world-notification", "WorldSettingsGui.kt", "TOGGLE_NOTIFICATION", MenuActionSafety.REVERSIBLE, MwmReversibleContracts.WORLD_STATE_PROVIDER, "notification", "world notification mutation"),
        Entry("member-role", "WorldSettingsGui.kt", "MEMBER_left", MenuActionSafety.REVERSIBLE, MwmReversibleContracts.WORLD_STATE_PROVIDER, "member_role", "member role mutation"),
    ).associateBy(Entry::id)

    fun all(): List<Entry> = entries.values.sortedBy(Entry::id)

    fun safety(id: String): MenuActionSafety = entry(id).safety

    fun contract(id: String): MenuReversibleContract {
        val entry = entry(id)
        require(entry.safety == MenuActionSafety.REVERSIBLE) { "Action '$id' is not reversible" }
        val provider = requireNotNull(entry.providerId) { "Reversible action '$id' has no provider" }
        return MenuReversibleContract(provider, entry.operation?.let { mapOf(MwmReversibleContracts.OPERATION to it) }.orEmpty())
    }

    private fun entry(id: String): Entry = requireNotNull(entries[id]) { "Unknown MWM menu action semantic: $id" }
}
