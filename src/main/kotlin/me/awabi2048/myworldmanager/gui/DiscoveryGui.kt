package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.api.gui.GuiCycle
import com.awabi2048.ccsystem.api.gui.GuiCycleDirection
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiMenuDisplaySpec
import com.awabi2048.ccsystem.api.gui.GuiMenuActionIntent
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryData
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryOption
import com.awabi2048.ccsystem.api.gui.GuiMenuEntrySpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiValueTone
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuActionContext
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuActionSafety
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuGesture
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import java.util.*
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.api.event.MwmFavoriteAddSource
import me.awabi2048.myworldmanager.api.event.MwmWorldFavoritedEvent
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.session.PreviewSessionManager
import me.awabi2048.myworldmanager.session.PreviewSource
import me.awabi2048.myworldmanager.session.DiscoverySpecialFilter
import me.awabi2048.myworldmanager.session.DiscoverySort
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.ItemTag
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import me.awabi2048.myworldmanager.util.PlayerNameUtil
import me.awabi2048.myworldmanager.util.WorldAccessMessageResolver
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import java.time.LocalDate

class DiscoveryGui(private val plugin: MyWorldManager) {
        private val runtime = CCSystem.getAPI().getMenuRuntimeService()

        init {
                runtime.register(
                        InventoryMenuDefinition(
                                owner = OWNER,
                                id = ROUTE_ID,
                                renderer = { context -> render(context.player, context.route) },
                                actions = mapOf(
                                        ACTION_WORLD to MenuActionHandler(::world),
                                        ACTION_TAG to MenuActionHandler(::tag),
                                        ACTION_SORT to MenuActionHandler(::sort),
                                        ACTION_SPECIAL_FILTER to MenuActionHandler(::specialFilter),
                                        ACTION_SPOTLIGHT_EMPTY to MenuActionHandler(::spotlightEmpty),
                                        ACTION_PAGE to MenuActionHandler(::page),
                                        ACTION_BACK to MenuActionHandler(::back),
                                ),
                        ),
                )
        }

        fun open(player: Player, page: Int = 0, showBackButton: Boolean? = null) {
                val session = plugin.discoverySessionManager.getSession(player.uniqueId)
                if (showBackButton != null) {
                        session.showBackButton = showBackButton
                }
                runtime.navigate(player, route(page))
        }

        private fun render(player: Player, route: MenuRoute): InventoryMenuView {
                val lang = plugin.languageManager
                val session = plugin.discoverySessionManager.getSession(player.uniqueId)
                val selectedTag = session.selectedTag?.takeIf {
                        it in plugin.worldTagManager.getEnabledTagIds()
                }
                if (selectedTag != session.selectedTag) {
                        session.selectedTag = null
                }
                val playerStats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                val allWorlds =
                        plugin.worldConfigRepository
                                .findAll()
                                .filter { MyWorldManagerApi.getWorldAccessPolicy().canShowInDiscovery(player, it) }
                                .filter {
                                        selectedTag == null || it.tags.contains(selectedTag)
                                }
                                .filter {
                                        session.specialFilter != DiscoverySpecialFilter.UNVISITED ||
                                                !playerStats.visitedWorlds.containsKey(it.uuid)
                                }

                val sortedWorlds =
                        when (session.sort) {
                                DiscoverySort.HOT ->
                                        allWorlds.sortedByDescending { it.recentVisitors.sum() }
                                DiscoverySort.NEW -> allWorlds.sortedByDescending { it.createdAt }
                                DiscoverySort.FAVORITES ->
                                        allWorlds.sortedByDescending { it.favorite }
                                DiscoverySort.SPOTLIGHT -> {
                                        val spotlightUuids = plugin.spotlightRepository.findAll()
                                        // Spotlight登録ワールドは公開設定に関わらず表示する
                                        spotlightUuids.mapNotNull { uuid ->
                                                plugin.worldConfigRepository.findByUuid(uuid)
                                        }.filter { MyWorldManagerApi.getWorldAccessPolicy().canShowInDiscovery(player, it) }
                                }
                                DiscoverySort.RANDOM -> {
                                        val seed = LocalDate.now().toEpochDay()
                                        allWorlds
                                                .sortedBy { it.uuid.toString() }
                                                .shuffled(Random(seed))
                                }
                        }

                val page = CCSystem.getAPI().getGuiLayoutService().sevenColumnPage(
                        sortedWorlds.size,
                        route.payload[PAGE]?.toIntOrNull() ?: 0,
                )
                val layout = page.layout
                val footerStart = layout.size - 9
                val pageWorlds = sortedWorlds.drop(page.startIndex).take(page.itemCount)
                val elements = mutableListOf<MenuElement>()

                if (sortedWorlds.isEmpty()) {
                        if (session.sort == DiscoverySort.SPOTLIGHT) {
                                layout.itemSlots.forEach { slot ->
                                        elements += createSpotlightEmptyEntry(player, slot)
                                }
                        } else {
                                elements += CCSystem.getAPI().getGuiElementService().menuDisplay(
                                        GuiMenuDisplaySpec(
                                                layout.itemSlots[layout.itemSlots.size / 2],
                                                GuiItemSpec(
                                                        Material.GRAY_DYE,
                                                        GuiNameSpec.Component(
                                                                lang.getComponent(player, "gui.discovery.no_result")
                                                                        .decoration(TextDecoration.ITALIC, false),
                                                        ),
                                                        GuiLoreSpec.None,
                                                        GuiElementRole.CONTENT,
                                                        1,
                                                ),
                                        ),
                                )
                        }
                } else {
                        pageWorlds.forEachIndexed { index, worldData ->
                                elements += createWorldEntry(player, worldData, layout.itemSlots[index])
                        }
                        if (session.sort == DiscoverySort.SPOTLIGHT) {
                                for (i in pageWorlds.size until layout.itemSlots.size) {
                                        elements += createSpotlightEmptyEntry(player, layout.itemSlots[i])
                                }
                        }
                }
                if (GuiHelper.canGoBack(player)) {
                        elements += backEntry(player, layout.backSlot)
                }
                if (page.page > 0) {
                        elements += navigationEntry(player, layout.previousPageSlot, false, page.page - 1)
                }
                elements += createTagFilterEntry(player, session.selectedTag, footerStart + 2)
                elements += createSortEntry(player, session.sort, footerStart + 3)
                elements += createStatsEntry(player, layout.actionSlot, session.sort, session.selectedTag, sortedWorlds.size)
                elements += createSpecialFilterEntry(player, session.specialFilter, footerStart + 5)
                if (page.page < page.totalPages - 1) {
                        elements += navigationEntry(player, layout.nextPageSlot, true, page.page + 1)
                }
                return InventoryMenuView(
                        layout.size,
                        GuiHelper.inventoryTitle(lang.getMessage(player, "gui.discovery.title")),
                        elements,
                )
        }

        private fun page(context: MenuActionContext): MenuActionResult {
                val target = context.payload[PAGE]?.toIntOrNull() ?: return MenuActionResult.Rejected()
                return MenuActionResult.Success(MenuUpdate.Replace(route(target)))
        }

        private fun back(context: MenuActionContext): MenuActionResult {
                return MenuActionResult.Success(MenuUpdate.Back)
        }

        private fun tag(context: MenuActionContext): MenuActionResult {
                val session = plugin.discoverySessionManager.getSession(context.player.uniqueId)
                val direction = cycleDirection(context) ?: return MenuActionResult.Ignored
                session.selectedTag = GuiCycle.selectNullable(
                        session.selectedTag,
                        plugin.worldTagManager.getEnabledTagIds() + null,
                        direction,
                )
                return MenuActionResult.Success(MenuUpdate.Replace(route(0)))
        }

        private fun sort(context: MenuActionContext): MenuActionResult {
                val player = context.player
                val session = plugin.discoverySessionManager.getSession(player.uniqueId)
                if (
                        !plugin.playerPlatformResolver.isBedrock(player) &&
                        context.click.isShiftClick &&
                        context.click.isLeftClick &&
                        session.sort == DiscoverySort.SPOTLIGHT &&
                        canManageSpotlight(player)
                ) {
                        plugin.discoveryListener.openSpotlightDescriptionDialog(player)
                        return MenuActionResult.Success(MenuUpdate.None)
                }
                val direction = cycleDirection(context) ?: return MenuActionResult.Ignored
                session.sort = GuiCycle.select(session.sort, DiscoverySort.values(), direction)
                return MenuActionResult.Success(MenuUpdate.Replace(route(0)))
        }

        private fun specialFilter(context: MenuActionContext): MenuActionResult {
                val session = plugin.discoverySessionManager.getSession(context.player.uniqueId)
                val direction = cycleDirection(context) ?: return MenuActionResult.Ignored
                session.specialFilter = GuiCycle.select(
                        session.specialFilter,
                        DiscoverySpecialFilter.values(),
                        direction,
                )
                return MenuActionResult.Success(MenuUpdate.Replace(route(0)))
        }

        private fun world(context: MenuActionContext): MenuActionResult {
                val uuid = context.payload[WORLD_UUID]
                        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: return MenuActionResult.Rejected()
                val worldData = plugin.worldConfigRepository.findByUuid(uuid)
                        ?: return MenuActionResult.Rejected()
                val player = context.player
                val isMember = player.uniqueId == worldData.owner ||
                        player.uniqueId in worldData.moderators ||
                        player.uniqueId in worldData.members
                if (plugin.playerPlatformResolver.isBedrock(player)) {
                        return visit(player, worldData, isMember)
                }
                val currentWorld = plugin.worldConfigRepository.findByWorldName(player.world.name)
                val isCurrentWorld = currentWorld?.uuid == worldData.uuid
                return when {
                        context.click.isShiftClick && context.click.isLeftClick ->
                                requestMembership(player, worldData, isMember)
                        context.click.isLeftClick && !isCurrentWorld ->
                                visit(player, worldData, isMember)
                        context.click.isShiftClick && context.click.isRightClick &&
                                plugin.discoverySessionManager.getSession(player.uniqueId).sort == DiscoverySort.SPOTLIGHT &&
                                canManageSpotlight(player) ->
                                removeSpotlight(player, worldData)
                        context.click.isShiftClick && context.click.isRightClick ->
                                toggleFavorite(player, worldData, isMember)
                        context.click.isRightClick && !isCurrentWorld ->
                                preview(player, worldData)
                        else -> MenuActionResult.Ignored
                }
        }

        private fun visit(player: Player, worldData: WorldData, isMember: Boolean): MenuActionResult {
                if (!MyWorldManagerApi.getWorldAccessPolicy().canUseVisitEntry(player, worldData, isMember)) {
                        player.sendMessage(
                                WorldAccessMessageResolver.visit(
                                        plugin.languageManager,
                                        player,
                                        worldData,
                                        isMember,
                                ),
                        )
                        plugin.soundManager.playActionSound(player, "discovery", "access_denied")
                        return MenuActionResult.Success(MenuUpdate.Close)
                }
                plugin.worldService.teleportToWorld(player, worldData.uuid) {
                        player.sendMessage(
                                plugin.languageManager.getMessage(
                                        player,
                                        "messages.warp_success",
                                        mapOf("world" to worldData.name),
                                ),
                        )
                }
                return MenuActionResult.Success(MenuUpdate.Close)
        }

        private fun requestMembership(
                player: Player,
                worldData: WorldData,
                isMember: Boolean,
        ): MenuActionResult {
                val lang = plugin.languageManager
                if (isMember) {
                        player.sendMessage(lang.getMessage(player, "error.member_request_already_member"))
                        return MenuActionResult.Rejected()
                }
                plugin.menuEntryRouter.openMemberRequestConfirm(player, worldData)
                return MenuActionResult.Success(MenuUpdate.None)
        }

        private fun removeSpotlight(player: Player, worldData: WorldData): MenuActionResult {
                val lang = plugin.languageManager
                plugin.menuEntryRouter.openSpotlightRemoveConfirm(player, worldData)
                return MenuActionResult.Success(MenuUpdate.None)
        }

        private fun toggleFavorite(
                player: Player,
                worldData: WorldData,
                isMember: Boolean,
        ): MenuActionResult {
                if (isMember) return MenuActionResult.Ignored
                val stats = plugin.playerStatsRepository.findByUuid(player.uniqueId)
                val lang = plugin.languageManager
                var added = false
                if (stats.favoriteWorlds.containsKey(worldData.uuid)) {
                        stats.favoriteWorlds.remove(worldData.uuid)
                        worldData.favorite = (worldData.favorite - 1).coerceAtLeast(0)
                        player.sendMessage(lang.getMessage(player, "messages.favorite_removed"))
                        plugin.soundManager.playActionSound(player, "discovery", "favorite_remove")
                } else {
                        val limit = plugin.config.getInt("favorite.max_count", 1000)
                        if (stats.favoriteWorlds.size >= limit) {
                                player.sendMessage(
                                        lang.getMessage(
                                                player,
                                                "error.favorite_limit_reached",
                                                mapOf("limit" to limit),
                                        ),
                                )
                                return MenuActionResult.Rejected()
                        }
                        val date = java.time.LocalDate.now()
                                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                        stats.favoriteWorlds[worldData.uuid] = date
                        worldData.favorite++
                        added = true
                        player.sendMessage(lang.getMessage(player, "messages.favorite_added"))
                        plugin.soundManager.playActionSound(player, "discovery", "favorite_add")
                }
                plugin.playerStatsRepository.save(stats)
                plugin.worldConfigRepository.save(worldData)
                if (added) {
                        Bukkit.getPluginManager().callEvent(
                                MwmWorldFavoritedEvent(
                                        worldData.uuid,
                                        worldData.name,
                                        player.uniqueId,
                                        player.name,
                                        MwmFavoriteAddSource.DISCOVERY_MENU,
                                ),
                        )
                }
                return MenuActionResult.Success(MenuUpdate.Refresh)
        }

        private fun preview(player: Player, worldData: WorldData): MenuActionResult {
                plugin.previewSessionManager.startPreview(
                        player,
                        PreviewSessionManager.PreviewTarget.World(worldData),
                        PreviewSource.DISCOVERY_MENU,
                )
                return MenuActionResult.Success(MenuUpdate.None)
        }

        private fun spotlightEmpty(context: MenuActionContext): MenuActionResult {
                val player = context.player
                if (!canManageSpotlight(player)) return MenuActionResult.Ignored
                val worldData = currentManagedWorld(player) ?: run {
                        player.sendMessage(
                                plugin.languageManager.getMessage(player, "error.spotlight_not_in_myworld"),
                        )
                        return MenuActionResult.Rejected()
                }
                plugin.menuEntryRouter.openSpotlightConfirm(player, worldData)
                return MenuActionResult.Success(MenuUpdate.None)
        }

        private fun currentManagedWorld(player: Player): WorldData? {
                if (player.world.name.startsWith("my_world.")) {
                        val uuid = runCatching {
                                UUID.fromString(player.world.name.removePrefix("my_world."))
                        }.getOrNull()
                        uuid?.let(plugin.worldConfigRepository::findByUuid)?.let { return it }
                }
                return plugin.worldConfigRepository.findByWorldName(player.world.name)
        }

        private fun cycleDirection(context: MenuActionContext): GuiCycleDirection? =
                if (plugin.playerPlatformResolver.isBedrock(context.player)) {
                        GuiCycleDirection.NEXT
                } else {
                        GuiCycle.direction(context.click)
                }

        private fun route(page: Int): MenuRoute =
                MenuRoute(OWNER, ROUTE_ID, mapOf(PAGE to page.toString()))

        private fun createWorldEntry(player: Player, data: WorldData, slot: Int): MenuElement {
                val lang = plugin.languageManager
                val isBedrock = plugin.playerPlatformResolver.isBedrock(player)

                val favorites = data.favorite
                val visitors = data.recentVisitors.sum()
                val currentWorldData = plugin.worldConfigRepository.findByWorldName(player.world.name)
                val isCurrentWorld = currentWorldData?.uuid == data.uuid
                val isFavoritedByViewer = plugin.playerStatsRepository.findByUuid(player.uniqueId).favoriteWorlds.containsKey(data.uuid)
                val isMember = data.owner == player.uniqueId ||
                        data.moderators.contains(player.uniqueId) ||
                        data.members.contains(player.uniqueId)

                val tagNames = if (data.tags.isNotEmpty()) {
                        data.tags.joinToString(", ") {
                                plugin.worldTagManager.getDisplayName(player, it)
                        }
                } else {
                        null
                }

                val warpHint = if (isCurrentWorld) {
                        ""
                } else {
                        lang.getMessage(player, "gui.discovery.world_item.warp_hint")
                }
                val previewHint = if (isCurrentWorld || isBedrock) "" else lang.getMessage(player, "gui.discovery.world_item.preview_hint")
                val memberRequestHint = if (isBedrock || isMember) "" else lang.getMessage(player, "gui.discovery.world_item.member_request_hint")
                val favoriteHint = if (isBedrock) {
                        ""
                } else if (isFavoritedByViewer) {
                        lang.getMessage(player, "gui.discovery.world_item.favorite_hint_remove")
                } else {
                        lang.getMessage(player, "gui.discovery.world_item.favorite_hint_add")
                }
                val ownerName = PlayerNameUtil.getNameOrDefault(data.owner, lang.getMessage(player, "general.unknown"))
                val payload = mapOf(WORLD_UUID to data.uuid.toString())
                return CCSystem.getAPI().getGuiElementService().menuEntry(
                        player,
                        GuiMenuEntrySpec(
                                slot = slot,
                                material = data.icon,
                                name = GuiNameSpec.Component(lang.getComponent(player, "gui.common.world_item_name", mapOf("world" to data.name))),
                                role = GuiElementRole.ACTION,
                                description = if (data.description.isBlank()) emptyList() else listOf(data.description),
                                data = buildList {
                                        add(GuiMenuEntryData(lang.getMessage(player, "gui.common.world_item.owner"), ownerName))
                                        add(GuiMenuEntryData(lang.getMessage(player, "gui.common.world_item.favorite"), favorites, GuiValueTone.DANGER))
                                        add(GuiMenuEntryData(
                                                lang.getMessage(player, "gui.common.world_item.recent_visitors"),
                                                lang.getMessage(player, "gui.common.world_item.recent_visitors_value", mapOf("count" to visitors)),
                                                GuiValueTone.SUCCESS,
                                        ))
                                        if (tagNames != null) add(GuiMenuEntryData(lang.getMessage(player, "gui.common.world_item.tags"), tagNames, GuiValueTone.PRIMARY))
                                },
                                actions = buildList {
                                        if (warpHint.isNotBlank()) add(menuGestureAction(ACTION_WORLD, MenuGesture.PLAIN_LEFT, warpHint, payload, safety = MenuActionSafety.EXTERNAL_SIDE_EFFECT))
                                        if (previewHint.isNotBlank()) add(menuGestureAction(ACTION_WORLD, MenuGesture.PLAIN_RIGHT, previewHint, payload, safety = MenuActionSafety.EXTERNAL_SIDE_EFFECT))
                                        if (memberRequestHint.isNotBlank()) add(menuGestureAction(ACTION_WORLD, MenuGesture.SHIFT_LEFT, memberRequestHint, payload, safety = MenuActionSafety.CONFIRM_ENTRY))
                                        if (favoriteHint.isNotBlank()) add(menuGestureAction(ACTION_WORLD, MenuGesture.SHIFT_RIGHT, favoriteHint, payload, safety = MenuActionSafety.CONFIRM_ENTRY))
                                },
                        ),
                )
        }

        private fun createSortEntry(player: Player, currentSort: DiscoverySort, slot: Int): MenuElement {
                val lang = plugin.languageManager
                val sortDesc = getSortDescription(player, currentSort)
                val canEditSpotlight = currentSort == DiscoverySort.SPOTLIGHT && canManageSpotlight(player)
                val options = DiscoverySort.values().map { sort ->
                        sort to lang.getMessage(player, "gui.discovery.sort.type.${sort.name.lowercase()}")
                }
                return CCSystem.getAPI().getGuiElementService().menuEntry(player, GuiMenuEntrySpec(
                        slot = slot,
                        material = plugin.menuConfigManager.getIconMaterial("discovery", "sort", Material.HOPPER),
                        name = GuiNameSpec.Component(lang.getComponent(player, "gui.discovery.sort.display")),
                        role = GuiElementRole.ACTION,
                        description = listOf(sortDesc),
                        data = listOf(GuiMenuEntryData(lang.getMessage(player, "gui.discovery.sort.label"), options.first { it.first == currentSort }.second, GuiValueTone.PRIMARY)),
                        options = options.map { GuiMenuEntryOption(it.second, it.first == currentSort) },
                        actions = buildList {
                                add(menuGestureAction(ACTION_SORT, MenuGesture.PLAIN_LEFT_RIGHT, lang.getMessage(player, "gui.common.action.cycle"), safety = MenuActionSafety.REVERSIBLE, reversibleContract = MwmMenuActionSemantics.contract("discovery-sort")))
                                if (canEditSpotlight) add(menuGestureAction(ACTION_SORT, MenuGesture.SHIFT_LEFT, lang.getMessage(player, "gui.discovery.sort.action.edit_spotlight"), safety = MenuActionSafety.INPUT_OR_EXTERNAL_SURFACE))
                        },
                ))
        }

        private fun createTagFilterEntry(player: Player, selectedTag: String?, slot: Int): MenuElement {
                val lang = plugin.languageManager
                val options = listOf(
                        "" to lang.getMessage(player, "gui.discovery.tag_filter.no_selection")
                ) + plugin.worldTagManager.getEnabledTagIds().map { tagId ->
                        tagId to plugin.worldTagManager.getDisplayName(player, tagId)
                }
                val selectedId = selectedTag.orEmpty()
                val selectedOption = options.firstOrNull { it.first == selectedId } ?: options.first()
                return cycleEntry(
                        player, slot,
                        plugin.menuConfigManager.getIconMaterial("discovery", "tag_filter", Material.NAME_TAG),
                        GuiNameSpec.Component(lang.getComponent(player, "gui.discovery.tag_filter.name")),
                        lang.getMessage(player, "gui.discovery.tag_filter.label"),
                        selectedOption.second,
                        options.map { GuiMenuEntryOption(it.second, it.first == selectedOption.first) },
                        ACTION_TAG,
                )
        }

        private fun createSpecialFilterEntry(player: Player, filter: DiscoverySpecialFilter, slot: Int): MenuElement {
                val lang = plugin.languageManager
                val display = lang.getMessage(player, "gui.discovery.special_filter.type.${filter.name.lowercase()}")
                return cycleEntry(
                        player, slot, Material.COMPASS,
                        GuiNameSpec.Component(lang.getComponent(player, "gui.discovery.special_filter.name")),
                        lang.getMessage(player, "gui.discovery.special_filter.label"),
                        display,
                        DiscoverySpecialFilter.values().map { option ->
                                GuiMenuEntryOption(
                                        lang.getMessage(player, "gui.discovery.special_filter.type.${option.name.lowercase()}"),
                                        option == filter,
                                )
                        },
                        ACTION_SPECIAL_FILTER,
                )
        }

        private fun cycleEntry(
                player: Player,
                slot: Int,
                material: Material,
                name: GuiNameSpec,
                label: String,
                currentValue: String,
                options: List<GuiMenuEntryOption>,
                actionId: String,
        ): MenuElement = CCSystem.getAPI().getGuiElementService().menuEntry(
                player,
                GuiMenuEntrySpec(
                        slot, material, name, GuiElementRole.ACTION,
                        data = listOf(GuiMenuEntryData(label, currentValue, GuiValueTone.PRIMARY)),
                        options = options,
                        actions = listOf(menuGestureAction(
                            actionId,
                            MenuGesture.PLAIN_LEFT_RIGHT,
                            plugin.languageManager.getMessage(player, "gui.common.action.cycle"),
                            safety = MenuActionSafety.REVERSIBLE,
                            reversibleContract = when (actionId) {
                                ACTION_TAG -> MwmMenuActionSemantics.contract("discovery-tag")
                                ACTION_SPECIAL_FILTER -> MwmMenuActionSemantics.contract("discovery-special")
                                else -> error("Unknown discovery reversible action: $actionId")
                            },
                        )),
                ),
        )

        private fun canManageSpotlight(player: Player): Boolean {
                return player.hasPermission("myworldmanager.admin")
        }

        private fun getSortDescription(player: Player, sort: DiscoverySort): String {
                val lang = plugin.languageManager
                if (sort != DiscoverySort.SPOTLIGHT) {
                        return lang.getMessage(player, "gui.discovery.sort_info.${sort.name.lowercase()}")
                }

                return plugin.spotlightRepository.getDescription()
                        ?: lang.getMessage(player, "gui.discovery.sort_info.spotlight")
        }

        private fun createSpotlightEmptyEntry(player: Player, slot: Int): MenuElement {
                val lang = plugin.languageManager
                return CCSystem.getAPI().getGuiElementService().menuEntry(player, GuiMenuEntrySpec(
                        slot = slot,
                        material = Material.GLASS_PANE,
                        name = GuiNameSpec.Component(lang.getComponent(player, "gui.discovery.spotlight_empty.name")),
                        role = if (player.hasPermission("myworldmanager.admin")) GuiElementRole.ACTION else GuiElementRole.CONTENT,
                        description = listOf(lang.getMessage(player, "gui.discovery.spotlight_empty.description")),
                        actions = if (player.hasPermission("myworldmanager.admin")) {
                                listOf(menuGestureAction(ACTION_SPOTLIGHT_EMPTY, MenuGesture.ANY, lang.getMessage(player, "gui.discovery.spotlight_empty.action.register"), safety = MenuActionSafety.CONFIRM_ENTRY))
                        } else emptyList(),
                ))
        }

        private fun createStatsEntry(
                player: Player,
                slot: Int,
                sort: DiscoverySort,
                tag: String?,
                count: Int,
        ): MenuElement {
                val lang = plugin.languageManager
                val sortName = lang.getMessage(player, "gui.discovery.sort.type.${sort.name.lowercase()}")
                val tagName = tag?.let { plugin.worldTagManager.getDisplayName(player, it) }
                        ?: lang.getMessage(player, "gui.discovery.tag_filter.all")
                return CCSystem.getAPI().getGuiElementService().menuDisplay(
                        GuiMenuDisplaySpec(
                                slot,
                                GuiItemSpec(
                                        Material.BOOK,
                                        GuiNameSpec.Component(lang.getComponent(player, "gui.discovery.stats.name")),
                                        GuiLoreSpec.Blocks(
                                                listOf(
                                                        GuiLoreBlock(
                                                                listOf(
                                                                        GuiLoreLine.Text(lang.getMessage(player, "gui.discovery.stats.desc")),
                                                                        GuiLoreLine.Data(lang.getMessage(player, "gui.discovery.stats.sort_label"), sortName, "§b"),
                                                                        GuiLoreLine.Data(lang.getMessage(player, "gui.discovery.stats.tag_label"), tagName, "§b"),
                                                                        GuiLoreLine.Data(lang.getMessage(player, "gui.discovery.stats.count_label"), count, "§b"),
                                                                ),
                                                        ),
                                                ),
                                        ),
                                        GuiElementRole.CONTENT,
                                        1,
                                ),
                        ),
                )
        }

        private fun navigationEntry(player: Player, slot: Int, next: Boolean, targetPage: Int): MenuElement {
                val key = if (next) "gui.common.next_page" else "gui.common.prev_page"
                val iconId = if (next) "next_page" else "prev_page"
                return CCSystem.getAPI().getGuiElementService().menuEntry(
                        player,
                        GuiMenuEntrySpec(
                                slot = slot,
                                material = plugin.menuConfigManager.getIconMaterial("discovery", iconId, Material.ARROW),
                                name = GuiNameSpec.Component(plugin.languageManager.getComponent(player, key)),
                                role = GuiElementRole.NAVIGATION,
                                actions = listOf(
                                        menuGestureAction(
                                                ACTION_PAGE,
                                                MenuGesture.LEFT_RIGHT,
                                                plugin.languageManager.getMessage(player, key),
                                                mapOf(PAGE to targetPage.toString()),
                                                safety = MenuActionSafety.NAVIGATION_ONLY,
                                        ),
                                ),
                        ),
                )
        }

        private fun backEntry(player: Player, slot: Int): MenuElement =
                CCSystem.getAPI().getGuiElementService().backEntry(
                        player,
                        slot,
                        plugin.menuConfigManager.getIconMaterial("world_settings", "back", Material.REDSTONE),
                )

        companion object {
                private const val OWNER = "myworldmanager"
                private const val ROUTE_ID = "discovery"
                private const val PAGE = "page"
                private const val WORLD_UUID = "worldUuid"
                private const val ACTION_WORLD = "world"
                private const val ACTION_TAG = "tag"
                private const val ACTION_SORT = "sort"
                private const val ACTION_SPECIAL_FILTER = "specialFilter"
                private const val ACTION_SPOTLIGHT_EMPTY = "spotlightEmpty"
                private const val ACTION_PAGE = "page"
                private const val ACTION_BACK = "back"
        }
}
