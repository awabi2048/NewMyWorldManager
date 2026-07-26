package me.awabi2048.myworldmanager.gui

import com.awabi2048.ccsystem.api.gui.GuiCycle
import com.awabi2048.ccsystem.api.gui.GuiCycleDirection
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuActionContext
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import java.util.*
import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.api.extension.DiscoveryMenuRequest
import me.awabi2048.myworldmanager.api.event.MwmFavoriteAddSource
import me.awabi2048.myworldmanager.api.event.MwmWorldFavoritedEvent
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.session.PreviewSessionManager
import me.awabi2048.myworldmanager.session.PreviewSource
import me.awabi2048.myworldmanager.session.DiscoverySpecialFilter
import me.awabi2048.myworldmanager.session.DiscoverySort
import me.awabi2048.myworldmanager.util.GuiHelper
import me.awabi2048.myworldmanager.util.GuiLoreActions
import me.awabi2048.myworldmanager.util.GuiItemFactory
import me.awabi2048.myworldmanager.util.StructuredLore
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
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import java.time.LocalDate

class DiscoveryGui(private val plugin: MyWorldManager) {
        private val runtime = CCSystem.getAPI().getMenuRuntimeService()
        private val itemsPerPage = 10

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
                if (
                        MyWorldManagerApi.openDiscoveryMenuOverride(
                                player,
                                DiscoveryMenuRequest(
                                        page = page,
                                        showBackButton = session.showBackButton
                                )
                        )
                ) {
                        return
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

                val totalPages =
                        if (sortedWorlds.isEmpty()) 1
                        else (sortedWorlds.size + itemsPerPage - 1) / itemsPerPage
                val currentPage = route.payload[PAGE]?.toIntOrNull()?.coerceIn(0, totalPages - 1) ?: 0
                val worldItemSlots = listOf(21, 22, 23, 28, 29, 30, 31, 32, 33, 34)
                val pageWorlds = sortedWorlds.drop(currentPage * itemsPerPage).take(itemsPerPage)
                val elements = mutableListOf<MenuElement>()

                if (sortedWorlds.isEmpty()) {
                        if (session.sort == DiscoverySort.SPOTLIGHT) {
                                worldItemSlots.forEach { slot ->
                                        elements += MenuElement(
                                                slot,
                                                createSpotlightEmptyItem(player),
                                                GuiElementRole.ACTION,
                                                ACTION_SPOTLIGHT_EMPTY,
                                        )
                                }
                        } else {
                                val noResultItem = ItemStack(Material.GRAY_DYE)
                                val noResultMeta = noResultItem.itemMeta
                                noResultMeta.displayName(
                                        lang.getComponent(player, "gui.discovery.no_result")
                                                .decoration(TextDecoration.ITALIC, false)
                                )
                                noResultItem.itemMeta = noResultMeta
                                ItemTag.tagItem(noResultItem, ItemTag.TYPE_GUI_DECORATION)
                                elements += MenuElement(31, noResultItem, GuiElementRole.CONTENT)
                        }
                } else {
                        pageWorlds.forEachIndexed { index, worldData ->
                                elements += MenuElement(
                                        worldItemSlots[index],
                                        createWorldItem(player, worldData),
                                        GuiElementRole.ACTION,
                                        ACTION_WORLD,
                                        mapOf(WORLD_UUID to worldData.uuid.toString()),
                                )
                        }
                        if (session.sort == DiscoverySort.SPOTLIGHT) {
                                for (i in pageWorlds.size until worldItemSlots.size) {
                                        elements += MenuElement(
                                                worldItemSlots[i],
                                                createSpotlightEmptyItem(player),
                                                GuiElementRole.ACTION,
                                                ACTION_SPOTLIGHT_EMPTY,
                                        )
                                }
                        }
                }
                if (session.showBackButton) {
                        elements += MenuElement(
                                45,
                                GuiHelper.createReturnItem(plugin, player, "discovery"),
                                GuiElementRole.BACK,
                                ACTION_BACK,
                        )
                }
                if (currentPage > 0) {
                        elements += MenuElement(
                                46,
                                GuiHelper.createPrevPageItem(plugin, player, "discovery", currentPage - 1),
                                GuiElementRole.NAVIGATION,
                                ACTION_PAGE,
                                mapOf(PAGE to (currentPage - 1).toString()),
                        )
                }
                elements += MenuElement(47, createTagFilterButton(player, session.selectedTag), GuiElementRole.ACTION, ACTION_TAG)
                elements += MenuElement(48, createSortButton(player, session.sort), GuiElementRole.ACTION, ACTION_SORT)
                elements += MenuElement(49, createStatsItem(player, session.sort, session.selectedTag, sortedWorlds.size), GuiElementRole.CONTENT)
                elements += MenuElement(50, createSpecialFilterButton(player, session.specialFilter), GuiElementRole.ACTION, ACTION_SPECIAL_FILTER)
                if (currentPage < totalPages - 1) {
                        elements += MenuElement(
                                53,
                                GuiHelper.createNextPageItem(plugin, player, "discovery", currentPage + 1),
                                GuiElementRole.NAVIGATION,
                                ACTION_PAGE,
                                mapOf(PAGE to (currentPage + 1).toString()),
                        )
                }
                return InventoryMenuView(
                        GuiHelper.settingsLayout().size,
                        GuiHelper.inventoryTitle(lang.getMessage(player, "gui.discovery.title")),
                        elements,
                )
        }

        private fun page(context: MenuActionContext): MenuActionResult {
                val target = context.payload[PAGE]?.toIntOrNull() ?: return MenuActionResult.Rejected()
                return MenuActionResult.Success(MenuUpdate.Replace(route(target)))
        }

        private fun back(context: MenuActionContext): MenuActionResult {
                Bukkit.getScheduler().runTask(
                        plugin,
                        Runnable {
                                GuiHelper.handleReturnClick(
                                        plugin,
                                        context.player,
                                )
                        },
                )
                return MenuActionResult.Success(MenuUpdate.Close)
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
                        Bukkit.getScheduler().runTask(
                                plugin,
                                Runnable { plugin.discoveryListener.openSpotlightDescriptionDialog(player) },
                        )
                        return MenuActionResult.Success(MenuUpdate.Close)
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
                val title = Component.text(lang.getMessage(player, "gui.member_request_confirm.title"))
                val body = lang.getMessageList(
                        player,
                        "gui.member_request_confirm.lore",
                        mapOf("world" to worldData.name),
                ).map(Component::text)
                Bukkit.getScheduler().runTask(
                        plugin,
                        Runnable {
                                DialogConfirmManager.showConfirmationByPreference(
                                        player,
                                        plugin,
                                        title,
                                        body,
                                        "mwm:confirm/member_request_send/${worldData.uuid}",
                                        "mwm:confirm/cancel",
                                ) {
                                        plugin.menuEntryRouter.openMemberRequestConfirm(
                                                player,
                                                worldData,
                                                onBedrockConfirm = {
                                                        plugin.memberRequestManager.sendRequest(player, worldData.uuid)
                                                },
                                                onBedrockCancel = {
                                                        plugin.soundManager.playActionSound(
                                                                player,
                                                                "member_request",
                                                                "cancel",
                                                        )
                                                },
                                        )
                                }
                        },
                )
                return MenuActionResult.Success(MenuUpdate.Close)
        }

        private fun removeSpotlight(player: Player, worldData: WorldData): MenuActionResult {
                val lang = plugin.languageManager
                val title = Component.text(lang.getMessage(player, "gui.discovery.spotlight_remove_confirm.title"))
                val body = lang.getMessageList(
                        player,
                        "gui.discovery.spotlight_remove_confirm.lore",
                        mapOf("world" to worldData.name),
                ).map(Component::text)
                Bukkit.getScheduler().runTask(
                        plugin,
                        Runnable {
                                DialogConfirmManager.showConfirmationByPreference(
                                        player,
                                        plugin,
                                        title,
                                        body,
                                        "mwm:confirm/spotlight_remove/${worldData.uuid}",
                                        "mwm:confirm/cancel",
                                ) {
                                        plugin.menuEntryRouter.openSpotlightRemoveConfirm(
                                                player,
                                                worldData,
                                                onBedrockConfirm = {
                                                        plugin.spotlightRepository.remove(worldData.uuid)
                                                        player.sendMessage(
                                                                lang.getMessage(
                                                                        player,
                                                                        "messages.spotlight_removed",
                                                                        mapOf("world" to worldData.name),
                                                                ),
                                                        )
                                                        plugin.menuEntryRouter.openDiscovery(player)
                                                },
                                                onBedrockCancel = {
                                                        plugin.menuEntryRouter.openDiscovery(player)
                                                },
                                        )
                                }
                        },
                )
                return MenuActionResult.Success(MenuUpdate.Close)
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
                return MenuActionResult.Success(MenuUpdate.Close)
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
                val lang = plugin.languageManager
                val title = Component.text(lang.getMessage(player, "gui.spotlight_confirm.title"))
                val body = lang.getMessageList(
                        player,
                        "gui.spotlight_confirm.lore",
                        mapOf("world" to worldData.name),
                ).map(Component::text)
                Bukkit.getScheduler().runTask(
                        plugin,
                        Runnable {
                                DialogConfirmManager.showConfirmationByPreference(
                                        player,
                                        plugin,
                                        title,
                                        body,
                                        "mwm:confirm/spotlight_add/${worldData.uuid}",
                                        "mwm:confirm/cancel",
                                ) {
                                        plugin.menuEntryRouter.openSpotlightConfirm(
                                                player,
                                                worldData,
                                                onBedrockConfirm = {
                                                        if (plugin.spotlightRepository.isSpotlight(worldData.uuid)) {
                                                                player.sendMessage(
                                                                        lang.getMessage(
                                                                                player,
                                                                                "error.spotlight_already_registered",
                                                                        ),
                                                                )
                                                        } else if (plugin.spotlightRepository.add(worldData.uuid)) {
                                                                player.sendMessage(
                                                                        lang.getMessage(
                                                                                player,
                                                                                "messages.spotlight_added",
                                                                                mapOf("world" to worldData.name),
                                                                        ),
                                                                )
                                                        } else {
                                                                player.sendMessage(
                                                                        lang.getMessage(
                                                                                player,
                                                                                "error.spotlight_limit_reached",
                                                                        ),
                                                                )
                                                        }
                                                        plugin.menuEntryRouter.openDiscovery(player)
                                                },
                                                onBedrockCancel = {
                                                        plugin.menuEntryRouter.openDiscovery(player)
                                                },
                                        )
                                }
                        },
                )
                return MenuActionResult.Success(MenuUpdate.Close)
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

        private fun createWorldItem(player: Player, data: WorldData): ItemStack {
                val item = ItemStack(data.icon)
                val meta = item.itemMeta ?: return item
                val lang = plugin.languageManager
                val isBedrock = plugin.playerPlatformResolver.isBedrock(player)

                meta.displayName(
                        lang.getComponent(
                                player,
                                "gui.common.world_item_name",
                                mapOf("world" to data.name)
                        ).decoration(TextDecoration.ITALIC, false)
                )

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
                val leftClick = lang.getMessage(player, "lore.click.left")
                val rightClick = lang.getMessage(player, "lore.click.right")
                val shiftLeftClick = lang.getMessage(player, "lore.click.shift_left")
                val shiftRightClick = lang.getMessage(player, "lore.click.shift_right")
                val ownerName = PlayerNameUtil.getNameOrDefault(data.owner, lang.getMessage(player, "general.unknown"))
                // 操作文言は内容だけを言語キーから受け取り、操作方法は共通Loreモデルで表現する。
                meta.lore(CCSystem.getAPI().getLoreService().render(GuiLoreSpec.Blocks(buildList {
                        if (data.description.isNotBlank()) add(GuiLoreBlock(listOf(GuiLoreLine.UserText(data.description))))
                        add(GuiLoreBlock(buildList {
                                add(GuiLoreLine.Data(lang.getMessage(player, "gui.common.world_item.owner"), ownerName, "§f"))
                                add(GuiLoreLine.Data(lang.getMessage(player, "gui.common.world_item.favorite"), favorites, "§c"))
                                add(GuiLoreLine.Data(
                                        lang.getMessage(player, "gui.common.world_item.recent_visitors"),
                                        lang.getMessage(player, "gui.common.world_item.recent_visitors_value", mapOf("count" to visitors)),
                                        "§a"
                                ))
                                if (tagNames != null) add(GuiLoreLine.Data(lang.getMessage(player, "gui.common.world_item.tags"), tagNames, "§e"))
                        }))
                        add(GuiLoreBlock(buildList {
                                if (warpHint.isNotBlank()) add(GuiLoreLine.Action(leftClick, warpHint))
                                if (previewHint.isNotBlank()) add(GuiLoreLine.Action(rightClick, previewHint))
                                if (memberRequestHint.isNotBlank()) add(GuiLoreLine.Action(shiftLeftClick, memberRequestHint))
                                if (favoriteHint.isNotBlank()) add(GuiLoreLine.Action(shiftRightClick, favoriteHint))
                        }))
                })))

                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_WORLD_ITEM)
                ItemTag.setWorldUuid(item, data.uuid)
                return item
        }

        private fun createSortButton(player: Player, currentSort: DiscoverySort): ItemStack {
                val lang = plugin.languageManager
                val item = ItemStack(plugin.menuConfigManager.getIconMaterial("discovery", "sort", Material.HOPPER))
                val meta = item.itemMeta ?: return item

                val sortDesc = getSortDescription(player, currentSort)

                meta.displayName(lang.getComponent(player, "gui.discovery.sort.display"))
                val canEditSpotlight = currentSort == DiscoverySort.SPOTLIGHT && canManageSpotlight(player)
                val options = DiscoverySort.values().map { sort ->
                        sort to lang.getMessage(player, "gui.discovery.sort.type.${sort.name.lowercase()}")
                }
                meta.lore(CCSystem.getAPI().getLoreService().render(
                        GuiLoreSpec.Rich(buildList {
                                add(GuiLoreLine.Data(
                                        lang.getMessage(player, "gui.discovery.sort.label"),
                                        options.first { it.first == currentSort }.second,
                                        "\u00A7e"
                                ))
                                add(GuiLoreLine.Spacer)
                                add(GuiLoreLine.Text(sortDesc))
                                add(GuiLoreLine.Spacer)
                                options.forEach { (sort, displayName) ->
                                        val selected = sort == currentSort
                                        add(GuiLoreLine.Option(displayName, selected, "\u00A7e", "\u00A77"))
                                }
                                add(GuiLoreLine.Spacer)
                                addAll(GuiLoreActions.cyclePreviousNext(lang, player))
                                if (canEditSpotlight) {
                                        add(GuiLoreLine.Action(
                                                lang.getMessage(player, "lore.click.shift_left"),
                                                lang.getMessage(player, "gui.discovery.sort.action.edit_spotlight")
                                        ))
                                }
                        }, GuiLoreFrame.BOTH)
                ))

                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_DISCOVERY_SORT)
                return item
        }

        private fun createTagFilterButton(player: Player, selectedTag: String?): ItemStack {
                val lang = plugin.languageManager
                val item = ItemStack(plugin.menuConfigManager.getIconMaterial("discovery", "tag_filter", Material.NAME_TAG))
                val meta = item.itemMeta ?: return item
                meta.displayName(lang.getComponent(player, "gui.discovery.tag_filter.name"))
                val options = listOf(
                        "" to lang.getMessage(player, "gui.discovery.tag_filter.no_selection")
                ) + plugin.worldTagManager.getEnabledTagIds().map { tagId ->
                        tagId to plugin.worldTagManager.getDisplayName(player, tagId)
                }
                val selectedId = selectedTag.orEmpty()
                val selectedOption = options.firstOrNull { it.first == selectedId } ?: options.first()
                meta.lore(CCSystem.getAPI().getLoreService().render(
                        GuiLoreSpec.Rich(buildList {
                                add(GuiLoreLine.Data(
                                        lang.getMessage(player, "gui.discovery.tag_filter.label"),
                                        selectedOption.second,
                                        "\u00A7e"
                                ))
                                add(GuiLoreLine.Spacer)
                                options.forEach { (tagId, displayName) ->
                                        val selected = tagId == selectedOption.first
                                        add(GuiLoreLine.Option(displayName, selected, "\u00A7e", "\u00A77"))
                                }
                                add(GuiLoreLine.Spacer)
                                addAll(GuiLoreActions.cyclePreviousNext(lang, player))
                        }, GuiLoreFrame.BOTH)
                ))

                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_DISCOVERY_TAG)
                return item
        }

        private fun createSpecialFilterButton(player: Player, filter: DiscoverySpecialFilter): ItemStack {
                val lang = plugin.languageManager
                val item = ItemStack(Material.COMPASS)
                val meta = item.itemMeta ?: return item
                meta.displayName(lang.getComponent(player, "gui.discovery.special_filter.name"))
                val display = lang.getMessage(player, "gui.discovery.special_filter.type.${filter.name.lowercase()}")
                meta.lore(CCSystem.getAPI().getLoreService().render(
                        GuiLoreSpec.Rich(buildList {
                                add(GuiLoreLine.Data(
                                        lang.getMessage(player, "gui.discovery.special_filter.label"),
                                        display,
                                        "\u00A7e"
                                ))
                                add(GuiLoreLine.Spacer)
                                DiscoverySpecialFilter.values().forEach { option ->
                                        val selected = option == filter
                                        val name = lang.getMessage(player, "gui.discovery.special_filter.type.${option.name.lowercase()}")
                                        add(GuiLoreLine.Option(name, selected, "\u00A7e", "\u00A77"))
                                }
                                add(GuiLoreLine.Spacer)
                                addAll(GuiLoreActions.cyclePreviousNext(lang, player))
                        }, GuiLoreFrame.BOTH)
                ))

                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_DISCOVERY_SPECIAL_FILTER)
                return item
        }

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

        private fun createSpotlightEmptyItem(player: Player): ItemStack {
                val lang = plugin.languageManager
                val item = ItemStack(Material.GLASS_PANE)
                val meta = item.itemMeta ?: return item
                meta.displayName(
                        lang.getComponent(player, "gui.discovery.spotlight_empty.name")
                                .decoration(TextDecoration.ITALIC, false)
                )

                meta.lore(
                        CCSystem.getAPI().getLoreService().render(
                                GuiLoreSpec.Rich(
                                        buildList {
                                                add(GuiLoreLine.Text(lang.getMessage(player, "gui.discovery.spotlight_empty.description")))
                                                if (player.hasPermission("myworldmanager.admin")) {
                                                        add(GuiLoreLine.Spacer)
                                                        add(GuiLoreActions.singleClick(
                                                                lang,
                                                                player,
                                                                lang.getMessage(player, "gui.discovery.spotlight_empty.action.register")
                                                        ))
                                                }
                                        },
                                        GuiLoreFrame.BOTH
                                )
                        )
                )

                item.itemMeta = meta
                ItemTag.tagItem(item, "discovery_spotlight_empty")
                return item
        }

        private fun createStatsItem(
                player: Player,
                sort: DiscoverySort,
                tag: String?,
                count: Int
        ): ItemStack {
                val lang = plugin.languageManager
                val item = ItemStack(Material.BOOK)
                val meta = item.itemMeta ?: return item

                val sortName = lang.getMessage(player, "gui.discovery.sort.type.${sort.name.lowercase()}")
                val tagName = tag?.let { plugin.worldTagManager.getDisplayName(player, it) } ?: lang.getMessage(player, "gui.discovery.tag_filter.all")
                meta.displayName(lang.getComponent(player, "gui.discovery.stats.name"))
                meta.lore(GuiItemFactory.menuLore(listOf(
                        GuiLoreLine.Text(lang.getMessage(player, "gui.discovery.stats.desc")),
                        GuiLoreLine.Data(lang.getMessage(player, "gui.discovery.stats.sort_label"), sortName, "§b"),
                        GuiLoreLine.Data(lang.getMessage(player, "gui.discovery.stats.tag_label"), tagName, "§b"),
                        GuiLoreLine.Data(lang.getMessage(player, "gui.discovery.stats.count_label"), count, "§b")
                )))

                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_INFO)
                return item
        }

        private fun createNavButton(
                player: Player,
                label: String,
                material: Material,
                targetPage: Int
        ): ItemStack {
                val item = ItemStack(material)
                val meta = item.itemMeta ?: return item
                meta.displayName(
                        LegacyComponentSerializer.legacySection()
                                .deserialize(label)
                                .decoration(TextDecoration.ITALIC, false)
                )
                item.itemMeta = meta
                ItemTag.setTargetPage(item, targetPage)
                val lang = plugin.languageManager
                val type =
                        if (label == lang.getMessage(player, "gui.common.next_page"))
                                ItemTag.TYPE_GUI_NAV_NEXT
                        else ItemTag.TYPE_GUI_NAV_PREV
                ItemTag.tagItem(item, type)
                return item
        }

        private fun createReturnButton(player: Player): ItemStack {
                val lang = plugin.languageManager
                val item = ItemStack(Material.REDSTONE)
                val meta = item.itemMeta ?: return item
                meta.displayName(
                        lang.getComponent(player, "gui.common.return")
                                .color(NamedTextColor.YELLOW)
                                .decorate(TextDecoration.BOLD)
                )
                item.itemMeta = meta
                ItemTag.tagItem(item, ItemTag.TYPE_GUI_RETURN)
                return item
        }

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
