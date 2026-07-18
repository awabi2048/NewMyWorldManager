package me.awabi2048.myworldmanager

import com.awabi2048.ccsystem.CCSystem
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.api.internal.MemberManagerAdapter
import me.awabi2048.myworldmanager.api.internal.BedrockFormServiceAdapter
import me.awabi2048.myworldmanager.api.internal.TemplateRepositoryAdapter
import me.awabi2048.myworldmanager.api.internal.WorldRepositoryAdapter
import me.awabi2048.myworldmanager.api.internal.WorldServiceAdapter
import me.awabi2048.myworldmanager.api.internal.WorldTagServiceAdapter
import me.awabi2048.myworldmanager.command.*
import me.awabi2048.myworldmanager.gui.*
import me.awabi2048.myworldmanager.integration.MultiverseWorldExclusionService
import me.awabi2048.myworldmanager.integration.WorldPermissionPolicyService
import me.awabi2048.myworldmanager.integration.MyWorldManagerArchiveStatusProvider
import me.awabi2048.myworldmanager.listener.*
import me.awabi2048.myworldmanager.model.LikeSignData
import me.awabi2048.myworldmanager.model.TourData
import me.awabi2048.myworldmanager.model.TourSignData
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.migration.WorldDirectoryResolver
import me.awabi2048.myworldmanager.migration.WorldMigrationService
import me.awabi2048.myworldmanager.repository.*
import me.awabi2048.myworldmanager.service.*
import me.awabi2048.myworldmanager.session.*
import me.awabi2048.myworldmanager.ui.MenuEntryRouter
import me.awabi2048.myworldmanager.ui.MenuRouteHistory
import me.awabi2048.myworldmanager.ui.PlayerPlatformResolver
import me.awabi2048.myworldmanager.ui.bedrock.BedrockMenuService
import me.awabi2048.myworldmanager.ui.bedrock.BedrockUiRoutingService
import me.awabi2048.myworldmanager.ui.bedrock.FloodgateFormBridge
import me.awabi2048.myworldmanager.util.*
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.configuration.serialization.ConfigurationSerialization
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import com.awabi2048.ccsystem.api.config.ConfigClassification
import com.awabi2048.ccsystem.api.config.ManagedConfigSpec
import com.awabi2048.ccsystem.api.gui.MenuTargetPolicy
import com.awabi2048.ccsystem.api.gui.PublicMenuDefinition
import net.luckperms.api.LuckPerms
import java.io.File
import java.util.UUID
import java.util.jar.JarFile

class MyWorldManager : JavaPlugin() {

    lateinit var worldConfigRepository: WorldConfigRepository
    lateinit var worldService: WorldService
    lateinit var worldEnvironmentService: WorldEnvironmentService
    lateinit var worldGui: WorldGui
    lateinit var creationSessionManager: CreationSessionManager
    lateinit var templateRepository: TemplateRepository
    lateinit var playerStatsRepository: PlayerStatsRepository
    private var worldPointApiService: MyWorldManagerApi.WorldPointService? = null
    private var worldWorkPermissionSyncService: MyWorldManagerApi.WorldWorkPermissionSyncService? = null
    lateinit var spotlightRepository: SpotlightRepository
    lateinit var pendingInteractionRepository: PendingInteractionRepository
    lateinit var worldDirectoryResolver: WorldDirectoryResolver

    lateinit var settingsSessionManager: SettingsSessionManager
    lateinit var inviteSessionManager: InviteSessionManager
    lateinit var worldSettingsGui: WorldSettingsGui
    lateinit var worldValidator: WorldValidator
    lateinit var portalRepository: PortalRepository
    lateinit var portalManager: PortalManager
    lateinit var memberInviteManager: MemberInviteManager
    lateinit var memberRequestManager: MemberRequestManager
    lateinit var pendingDecisionManager: PendingDecisionManager
    lateinit var pendingNotificationService: PendingNotificationService
    lateinit var discoverySessionManager: DiscoverySessionManager
    lateinit var meetSessionManager: MeetSessionManager
    lateinit var favoriteSessionManager: FavoriteSessionManager
    lateinit var playerWorldSessionManager: PlayerWorldSessionManager
    lateinit var soundManager: SoundManager
    lateinit var menuConfigManager: MenuConfigManager

    lateinit var creationGui: CreationGui
    lateinit var discoveryGui: DiscoveryGui
    lateinit var favoriteGui: FavoriteGui
    lateinit var favoriteMenuGui: FavoriteMenuGui
    lateinit var favoriteConfirmGui: FavoriteConfirmGui
    lateinit var visitGui: VisitGui
    lateinit var visitWorldGui: VisitWorldGui
    lateinit var meetGui: MeetGui
    lateinit var inviteGui: InviteGui
    lateinit var playerWorldGui: PlayerWorldGui
    lateinit var pendingInteractionGui: PendingInteractionGui
    lateinit var userSettingsGui: UserSettingsGui
    lateinit var adminPortalGui: AdminPortalGui
    lateinit var templateWizardGui: TemplateWizardGui
    lateinit var adminCommandGui: AdminCommandGui
    lateinit var spotlightConfirmGui: SpotlightConfirmGui
    lateinit var spotlightRemoveConfirmGui: SpotlightRemoveConfirmGui
    lateinit var environmentGui: EnvironmentGui
    lateinit var environmentConfirmGui: EnvironmentConfirmGui
    lateinit var memberRequestConfirmGui: MemberRequestConfirmGui
    lateinit var memberRequestOwnerConfirmGui: MemberRequestOwnerConfirmGui
    lateinit var worldSeedConfirmGui: WorldSeedConfirmGui
    lateinit var confirmationMenuGui: ConfirmationMenuGui
    lateinit var languageManager: LanguageManager
    lateinit var worldTagManager: WorldTagManager
    lateinit var previewSessionManager: PreviewSessionManager
    lateinit var adminGuiSessionManager: AdminGuiSessionManager
    lateinit var macroManager: MacroManager
    lateinit var directoryManager: DirectoryManager
    lateinit var worldMigrationService: WorldMigrationService
    lateinit var worldUnloadService: WorldUnloadService
    lateinit var msptMonitorTask: me.awabi2048.myworldmanager.task.MsptMonitorTask
    lateinit var inviteCommand: me.awabi2048.myworldmanager.command.InviteCommand
    lateinit var likeSignManager: me.awabi2048.myworldmanager.service.LikeSignManager
    lateinit var tourManager: me.awabi2048.myworldmanager.service.TourManager
    lateinit var tourSessionManager: TourSessionManager
    lateinit var playerPlatformResolver: PlayerPlatformResolver
    lateinit var playerVisibilityService: PlayerVisibilityService
    lateinit var floodgateFormBridge: FloodgateFormBridge
    private var bedrockFormApiService: me.awabi2048.myworldmanager.api.service.ApiBedrockFormService? = null
    lateinit var bedrockUiRoutingService: BedrockUiRoutingService
    lateinit var bedrockMenuService: BedrockMenuService
    lateinit var menuEntryRouter: MenuEntryRouter
    lateinit var menuRouteHistory: MenuRouteHistory
    lateinit var internalCommandTokenManager: InternalCommandTokenManager
    lateinit var tourGui: TourGui
    lateinit var worldSettingsListener: WorldSettingsListener
    lateinit var worldPermissionPolicyService: WorldPermissionPolicyService

    override fun onEnable() {
        ensureCCSystemAvailable()

        // Serializationの登録
        ConfigurationSerialization.registerClass(WorldData::class.java)
        ConfigurationSerialization.registerClass(LikeSignData::class.java)
        ConfigurationSerialization.registerClass(TourSignData::class.java)
        ConfigurationSerialization.registerClass(TourData::class.java)

        // 設定用フォルダの作成
        if (!dataFolder.exists()) {
            dataFolder.mkdirs()
        }
        registerManagedConfigs()
        reloadConfig()
        if (!config.contains("debug.world_settings", true)) {
            config.set("debug.world_settings", true)
            saveConfig()
        }

        // langフォルダが存在しなければ作成し、デフォルトの言語ファイルをコピー
        val langFolder = java.io.File(dataFolder, "lang")
        if (!langFolder.exists()) {
            langFolder.mkdirs()
        }
        saveSplitLanguageResources()

        // templates.ymlがなければコピー
        saveResourceIfNotExists("templates.yml")

        // 言語設定の初期化
        languageManager = LanguageManager(this)
        CCSystem.getAPI().getItemGrantService().register(MyWorldItemGrantProvider(this))
        worldTagManager = WorldTagManager(this)
        worldTagManager.reload()

        worldDirectoryResolver = WorldDirectoryResolver(
                com.awabi2048.ccsystem.CCSystem.getAPI().getWorldDirectoryService()
        )

        // リポジトリの初期化
        worldConfigRepository = WorldConfigRepository(this)
        templateRepository = TemplateRepository(this)

        directoryManager = DirectoryManager(this, worldConfigRepository, templateRepository)
        worldMigrationService = WorldMigrationService(
                this,
                worldDirectoryResolver
        )
        // ワールド・テンプレートディレクトリの存在チェック
        directoryManager.checkDirectories()

        playerStatsRepository = PlayerStatsRepository(this)
        worldPointApiService = MyWorldManagerApi.WorldPointService { playerUuid, amount ->
            require(amount >= 0) { "amount must be non-negative" }
            val stats = playerStatsRepository.findByUuid(playerUuid)
            stats.worldPoint += amount
            playerStatsRepository.save(stats)
            stats.worldPoint
        }.also { MyWorldManagerApi.registerWorldPointService(it) }
        portalRepository = PortalRepository(this)
        spotlightRepository = SpotlightRepository(this)
        pendingInteractionRepository = PendingInteractionRepository(this)
        // サービスの初期化
        worldService = WorldService(this, worldConfigRepository, playerStatsRepository)
        worldEnvironmentService = WorldEnvironmentService(this)
        portalManager = PortalManager(this)
        portalManager.startTasks()
        worldUnloadService = WorldUnloadService(this)
        worldUnloadService.start()
        likeSignManager = LikeSignManager(this)
        tourManager = TourManager(this)

        loadWorldsFromPreviousShutdown()
        worldMigrationService.reportPending()

        // MSPT監視タスクの開始
        msptMonitorTask = me.awabi2048.myworldmanager.task.MsptMonitorTask(this)
        msptMonitorTask.start()

        // GUIの初期化
        worldGui = WorldGui(this)
        creationGui = CreationGui(this)
        discoveryGui = DiscoveryGui(this)
        favoriteGui = FavoriteGui(this)
        favoriteMenuGui = FavoriteMenuGui(this)
        favoriteConfirmGui = FavoriteConfirmGui(this)
        visitGui = VisitGui(this)
        visitWorldGui = VisitWorldGui(this)
        meetGui = MeetGui(this)
        inviteGui = InviteGui(this)
        playerWorldGui = PlayerWorldGui(this)
        pendingInteractionGui = PendingInteractionGui(this)
        worldSettingsGui = WorldSettingsGui(this)
        userSettingsGui = UserSettingsGui(this)
        adminPortalGui = AdminPortalGui(this)
        adminCommandGui = AdminCommandGui(this)
        templateWizardGui = TemplateWizardGui(this)
        spotlightConfirmGui = SpotlightConfirmGui(this)
        spotlightRemoveConfirmGui = SpotlightRemoveConfirmGui(this)
        environmentGui = EnvironmentGui(this)
        environmentConfirmGui = EnvironmentConfirmGui(this)
        memberRequestConfirmGui = MemberRequestConfirmGui(this)
        memberRequestOwnerConfirmGui = MemberRequestOwnerConfirmGui(this)
        worldSeedConfirmGui = WorldSeedConfirmGui(this)
        confirmationMenuGui = ConfirmationMenuGui(this)
        tourGui = TourGui(this)

        creationSessionManager = CreationSessionManager(this)
        inviteSessionManager = InviteSessionManager()
        macroManager = MacroManager(this)
        worldPermissionPolicyService = WorldPermissionPolicyService(
                server.servicesManager.getRegistration(LuckPerms::class.java)?.provider,
                server.pluginManager.isPluginEnabled("WorldGuard"),
                config.getString("permissions.world_work_group", "builder").orEmpty().trim(),
                logger,
                dataFolder
        )
        tourSessionManager = TourSessionManager()
        // MemberInviteManagerの初期化に依存関係を渡す
        memberInviteManager = MemberInviteManager(this, worldConfigRepository, macroManager)
        memberRequestManager = MemberRequestManager(this)
        pendingDecisionManager = PendingDecisionManager(this)
        pendingNotificationService = PendingNotificationService(this)

        // 設定機能の初期化
        settingsSessionManager = SettingsSessionManager(::logWorldSettingsDebug)
        discoverySessionManager = DiscoverySessionManager()
        meetSessionManager = MeetSessionManager()
        favoriteSessionManager = FavoriteSessionManager()
        playerWorldSessionManager = PlayerWorldSessionManager()
        worldValidator = WorldValidator(this)
        soundManager = SoundManager(this)

        // メニュー設定の初期化
        menuConfigManager = MenuConfigManager(this)
        menuConfigManager.initialize()
        registerMenuSoundProvider()

        previewSessionManager = PreviewSessionManager(this)
        adminGuiSessionManager = AdminGuiSessionManager()

        playerPlatformResolver = PlayerPlatformResolver(this)
        playerVisibilityService = PlayerVisibilityService(this)
        floodgateFormBridge = FloodgateFormBridge(this)
        bedrockFormApiService = BedrockFormServiceAdapter(playerPlatformResolver, floodgateFormBridge)
            .also(MyWorldManagerApi::registerBedrockFormService)
        bedrockUiRoutingService =
                BedrockUiRoutingService(this, playerPlatformResolver, floodgateFormBridge)
        bedrockMenuService =
                BedrockMenuService(this, bedrockUiRoutingService, floodgateFormBridge)
        menuRouteHistory = MenuRouteHistory(this)
        menuEntryRouter = MenuEntryRouter(this, playerPlatformResolver, bedrockMenuService)
        val worldMenuCommand = WorldMenuCommand(this)
        CCSystem.getAPI().getMenuCommandService().unregisterOwner("myworld")
        CCSystem.getAPI().getMenuCommandService().register(
            PublicMenuDefinition(
                owner = "myworld",
                id = "worlds",
                permission = PermissionManager.COMMAND_MYWORLD,
                targetPolicy = MenuTargetPolicy.SELF_ONLY,
                argumentKeys = setOf("back"),
                opener = { player, arguments ->
                    menuEntryRouter.openPlayerWorld(player, 0, arguments["back"].toBoolean())
                    true
                }
            )
        )
        CCSystem.getAPI().getMenuCommandService().register(
            PublicMenuDefinition(
                owner = "myworld",
                id = "world-settings",
                permission = PermissionManager.COMMAND_WORLDMENU,
                targetPolicy = MenuTargetPolicy.SELF_ONLY,
                argumentKeys = setOf("back"),
                opener = { player, arguments ->
                    worldMenuCommand.openCurrent(player, arguments["back"].toBoolean())
                }
            )
        )
        internalCommandTokenManager = InternalCommandTokenManager(this)
        server.scheduler.runTaskTimer(this, Runnable {
            internalCommandTokenManager.cleanupExpired()
        }, 20L, 20L)

        MyWorldManagerApi.registerWorldService(WorldServiceAdapter(this))
        MyWorldManagerApi.registerWorldEnvironmentService(worldEnvironmentService)
        MyWorldManagerApi.registerWorldRepository(WorldRepositoryAdapter(this))
        com.awabi2048.ccsystem.api.lwc.WorldArchiveStatusProviders.register(MyWorldManagerArchiveStatusProvider)
        MyWorldManagerApi.registerTemplateRepository(TemplateRepositoryAdapter(this))
        MyWorldManagerApi.registerMemberManager(MemberManagerAdapter(this))
        MyWorldManagerApi.registerWorldTagService(WorldTagServiceAdapter(this))
        worldWorkPermissionSyncService = MyWorldManagerApi.WorldWorkPermissionSyncService { worldUuid ->
            worldConfigRepository.findByUuid(worldUuid)?.let(worldPermissionPolicyService::syncParticipants)
        }.also(MyWorldManagerApi::registerWorldWorkPermissionSyncService)

        MyWorldManagerApi.setMemberManagementOpener { player, worldUuid ->
            val worldData = worldConfigRepository.findByUuid(worldUuid) ?: return@setMemberManagementOpener
            worldSettingsGui.openMemberManagement(player, worldData)
        }

        inviteCommand = InviteCommand(this)

        // リスナーの登録
        server.pluginManager.registerEvents(WorldStatusListener(this), this)
        MultiverseWorldExclusionService(this, worldConfigRepository).start()
        server.pluginManager.registerEvents(
                WorldPermissionPolicyListener(worldConfigRepository, worldPermissionPolicyService),
                this
        )
        // サーバー起動時点ですでにロード済みのMyWorldにも、一度だけ初期ポリシーを適用する。
        server.scheduler.runTask(this, Runnable {
            worldConfigRepository.findAll()
                    .filter { Bukkit.getWorld(WorldPermissionPolicyService.worldName(it)) != null }
                    .forEach(worldPermissionPolicyService::initializeWorld)
        })
        server.pluginManager.registerEvents(AccessControlListener(this), this)
        server.pluginManager.registerEvents(BorderExpansionChangeListener(this), this)
        server.pluginManager.registerEvents(SpawnListener(worldConfigRepository), this)
        // 旧 GuiListener を分割して登録
        server.pluginManager.registerEvents(PlayerWorldListener(this), this)
        server.pluginManager.registerEvents(VisitListener(this), this)
        server.pluginManager.registerEvents(VisitWorldListener(this), this)
        server.pluginManager.registerEvents(FavoriteListener(this), this)
        server.pluginManager.registerEvents(MeetListener(this), this)
        server.pluginManager.registerEvents(InviteListener(this), this)

        server.pluginManager.registerEvents(AdminGuiListener(), this)
        server.pluginManager.registerEvents(AdminCommandListener(), this)
        server.pluginManager.registerEvents(CreationGuiListener(this), this)
        server.pluginManager.registerEvents(PlayerDataListener(), this)
        worldSettingsListener = WorldSettingsListener()
        server.pluginManager.registerEvents(worldSettingsListener, this)
        server.pluginManager.registerEvents(WorldExpirationListener(worldConfigRepository), this)
        server.pluginManager.registerEvents(PortalListener(this), this)
        server.pluginManager.registerEvents(PortalDisplayLifecycleListener(this), this)
        server.pluginManager.registerEvents(PortalGui(this), this)
        if (server.pluginManager.isPluginEnabled("WorldEdit") || server.pluginManager.isPluginEnabled("FastAsyncWorldEdit")) {
            // WorldEditのcut/copy/pasteはBukkitの通常ブロックイベントを通らないため、ポータルメタデータを別途同期する。
            server.pluginManager.registerEvents(WorldEditPortalSyncListener(this), this)
        }
        server.pluginManager.registerEvents(DiscoveryListener(this), this)
        server.pluginManager.registerEvents(SpotlightListener(this), this)
        server.pluginManager.registerEvents(TemplatePreviewListener(), this)
        server.pluginManager.registerEvents(EnvironmentLogicListener(this), this)
        server.pluginManager.registerEvents(CustomItemListener(this), this)
        server.pluginManager.registerEvents(MemberRequestConfirmListener(this), this)
        server.pluginManager.registerEvents(MemberRequestOwnerConfirmListener(this), this)
        server.pluginManager.registerEvents(ConfirmationMenuListener(), this)
        server.pluginManager.registerEvents(WorldSeedListener(this), this)
        server.pluginManager.registerEvents(TemplateWizardListener(), this)
        server.pluginManager.registerEvents(ItemConversionListener(this), this)
        server.pluginManager.registerEvents(GlobalMenuListener(this), this)
        server.pluginManager.registerEvents(CreationDialogManager(), this)
        server.pluginManager.registerEvents(AnnouncementDialogManager(), this)
        server.pluginManager.registerEvents(TourListener(this), this)
        server.pluginManager.registerEvents(TourDialogManager(), this)
        server.pluginManager.registerEvents(BedrockInventoryListener(this), this)

        // コマンドの登録
        val mwmCmd = WorldCommand(worldService, creationSessionManager)
        getCommand("mwm")?.setExecutor(mwmCmd)
        getCommand("mwm")?.setTabCompleter(mwmCmd)
        val myWorldCmd = PlayerWorldCommand(this)
        getCommand("myworld")?.setExecutor(myWorldCmd)
        getCommand("myworld")?.setTabCompleter(myWorldCmd)
        getCommand("worldmenu")?.setExecutor(worldMenuCommand)
        getCommand("worldwarp")?.let {
            val worldWarpCmd = WorldWarpCommand(this)
            it.setExecutor(worldWarpCmd)
            it.setTabCompleter(worldWarpCmd)
        }
        getCommand("visit")?.let {
            val visitCmd = VisitCommand(this)
            it.setExecutor(visitCmd)
            it.setTabCompleter(visitCmd)
            server.pluginManager.registerEvents(visitCmd, this)
        }

        getCommand("findworld")?.let {
            val visitWorldCmd = VisitWorldCommand(this)
            it.setExecutor(visitWorldCmd)
            it.setTabCompleter(visitWorldCmd)
            server.pluginManager.registerEvents(visitWorldCmd, this)
        }

        getCommand("invite")?.setExecutor(inviteCommand)
        getCommand("invite")?.setTabCompleter(inviteCommand)


        getCommand("favorite")?.setExecutor(FavoriteCommand(this))
        getCommand("discovery")?.setExecutor(DiscoveryCommand(this))

        val meetCmd = MeetCommand(this)
        getCommand("meet")?.setExecutor(meetCmd)
        getCommand("meet")?.setTabCompleter(meetCmd)

        getCommand("settings")?.setExecutor(SettingsCommand(this))
        getCommand("tour")?.setExecutor(TourCommand(this))

        // 起動完了ログ
        LogUtil.logWithSeparator(
                logger,
                listOf(
                        "MyWorldManager ${pluginMeta.version} has been enabled!",
                        "Developed by awabi2048"
                )
        )

        // Chiyogamiチェック
        if (!ChiyogamiUtil.isChiyogamiActive()) {
            LogUtil.logWarningBox(
                    logger,
                    listOf(
                            "Chiyogami is NOT detected!",
                            "This plugin is optimized for Chiyogami server software.",
                            "Using it on other software may cause performance issues or unexpected behavior.",
                            "It is strongly recommended to use Chiyogami."
                    )
            )
        }
    }

    private fun registerManagedConfigs() {
        val classifications = mapOf(
            "config.yml" to ConfigClassification.MANAGED_CONFIG,
            "templates.yml" to ConfigClassification.BUNDLED_DEFINITION,
            "macro.yml" to ConfigClassification.MANAGED_CONFIG,
            "spotlight.yml" to ConfigClassification.MANAGED_CONFIG
        )
        val specs = classifications.map { (resourcePath, classification) ->
            ManagedConfigSpec(
                owner = "myworld",
                sourcePlugin = this,
                resourcePath = resourcePath,
                targetPath = File(dataFolder, resourcePath).toPath(),
                currentVersion = 1,
                classification = classification,
                migrations = emptyMap(),
                validator = com.awabi2048.ccsystem.api.config.ConfigValidator {},
                reloadAction = null
            )
        }
        com.awabi2048.ccsystem.CCSystem.getAPI().getConfigSchemaService().register("myworld", specs)
        val result = com.awabi2048.ccsystem.CCSystem.getAPI().getConfigSchemaService().prepare("myworld")
        check(result.successful) {
            "MyWorldManager Config preparation failed: ${result.statuses.filter { it.message != null }}"
        }
    }

    override fun onDisable() {
        if (::menuRouteHistory.isInitialized) menuRouteHistory.closeOwnedMenus()
        clearAllTransientMenuState()
        worldPointApiService?.let { MyWorldManagerApi.unregisterWorldPointService(it) }
        worldPointApiService = null
        bedrockFormApiService?.let(MyWorldManagerApi::unregisterBedrockFormService)
        bedrockFormApiService = null
        worldWorkPermissionSyncService?.let(MyWorldManagerApi::unregisterWorldWorkPermissionSyncService)
        worldWorkPermissionSyncService = null
        com.awabi2048.ccsystem.api.lwc.WorldArchiveStatusProviders.unregister(MyWorldManagerArchiveStatusProvider)
        if (::worldEnvironmentService.isInitialized) {
            MyWorldManagerApi.unregisterWorldEnvironmentService(worldEnvironmentService)
        }
        runCatching { CCSystem.getAPI().unregisterI18nSource(name) }
        runCatching { CCSystem.getAPI().getItemGrantService().unregister("myworld") }
        runCatching { CCSystem.getAPI().getConfigSchemaService().unregister("myworld") }
        runCatching { CCSystem.getAPI().getMenuCommandService().unregisterOwner("myworld") }
        runCatching { CCSystem.getAPI().getMenuSoundService().unregisterProvider(MwmMenuSoundProvider.PROVIDER_SOURCE_ID) }
        if (::worldUnloadService.isInitialized) {
            worldUnloadService.stop()
        }
        if (::tourManager.isInitialized) {
            tourManager.shutdown()
        }
        if (::internalCommandTokenManager.isInitialized) {
            internalCommandTokenManager.clearAll()
        }

        saveLoadedWorldsForShutdown()

        LogUtil.logWithSeparator(
                logger,
                listOf("MyWorldManager ${pluginMeta.version} has been disabled.", "Goodbye!")
        )
    }

    fun clearTransientPlayerMenuState(playerUuid: UUID) {
        Bukkit.getPlayer(playerUuid)?.let { player ->
            if (::menuRouteHistory.isInitialized) menuRouteHistory.clear(player)
        }
        if (::settingsSessionManager.isInitialized) settingsSessionManager.endSession(playerUuid)
        if (::creationSessionManager.isInitialized) creationSessionManager.endSession(playerUuid)
        if (::inviteSessionManager.isInitialized) inviteSessionManager.endSession(playerUuid)
        if (::discoverySessionManager.isInitialized) discoverySessionManager.clearSession(playerUuid)
        if (::meetSessionManager.isInitialized) meetSessionManager.clearSession(playerUuid)
        if (::favoriteSessionManager.isInitialized) favoriteSessionManager.clearSession(playerUuid)
        if (::playerWorldSessionManager.isInitialized) playerWorldSessionManager.clearSession(playerUuid)
        if (::adminGuiSessionManager.isInitialized) adminGuiSessionManager.clearSession(playerUuid)
        if (::tourSessionManager.isInitialized) tourSessionManager.clearPlayer(playerUuid)
        if (::templateWizardGui.isInitialized) templateWizardGui.removeSession(playerUuid)
    }

    private fun clearAllTransientMenuState() {
        if (::menuRouteHistory.isInitialized) menuRouteHistory.unregister()
        if (::settingsSessionManager.isInitialized) settingsSessionManager.clearAll()
        if (::creationSessionManager.isInitialized) {
            creationSessionManager.clearAll()
            creationSessionManager.stopTimeoutChecker()
        }
        if (::inviteSessionManager.isInitialized) inviteSessionManager.clearAll()
        if (::discoverySessionManager.isInitialized) discoverySessionManager.clearAll()
        if (::meetSessionManager.isInitialized) meetSessionManager.clearAll()
        if (::favoriteSessionManager.isInitialized) favoriteSessionManager.clearAll()
        if (::playerWorldSessionManager.isInitialized) playerWorldSessionManager.clearAll()
        if (::adminGuiSessionManager.isInitialized) adminGuiSessionManager.clearAll()
        if (::tourSessionManager.isInitialized) tourSessionManager.clearAll()
        if (::templateWizardGui.isInitialized) templateWizardGui.clearAll()
    }

    fun logWorldSettingsDebug(message: String) {
        if (config.getBoolean("debug.world_settings", true)) {
            logger.info("[WorldSettingsDebug] $message")
        }
    }

    private fun loadWorldsFromPreviousShutdown() {
        val file = File(dataFolder, "data/loaded_worlds_at_shutdown.yml")
        if (!file.exists()) return

        val yaml = YamlConfiguration.loadConfiguration(file)
        val uuids = yaml.getStringList("uuids").mapNotNull {
            runCatching { UUID.fromString(it) }.getOrNull()
        }

        uuids.forEach { uuid ->
            runCatching { worldService.loadWorld(uuid) }
        }

        file.delete()
        logger.info("Loaded ${uuids.size} MyWorld(s) from previous shutdown")
    }

    private fun saveLoadedWorldsForShutdown() {
        if (!::worldConfigRepository.isInitialized) return

        val loadedWorldUuids = Bukkit.getWorlds()
            .filter { it.players.isNotEmpty() }
            .mapNotNull { worldConfigRepository.findByWorldName(it.name) }
            .map { it.uuid }
            .distinct()

        if (loadedWorldUuids.isEmpty()) return

        val dataDir = File(dataFolder, "data")
        if (!dataDir.exists()) dataDir.mkdirs()

        val yaml = YamlConfiguration()
        yaml.set("uuids", loadedWorldUuids.map { it.toString() })
        yaml.set("timestamp", System.currentTimeMillis())
        yaml.save(File(dataDir, "loaded_worlds_at_shutdown.yml"))
    }

    /** 設定ファイルとリポジトリのデータを再読み込みする（再起動と同等の処理） */
    fun reloadSystem() {
        // デフォルト設定の保存（ファイルが存在しない場合のみ作成）
        saveDefaultConfig()

        // config.ymlの再読み込み
        reloadConfig()

        // 各コンポーネントの再読み込み
        worldTagManager.reload()
        worldConfigRepository.loadAll()
        templateRepository.loadTemplates()
        portalRepository.loadAll()
        spotlightRepository.load()
        macroManager.loadConfig()
        menuConfigManager.initialize() // フォルダ作成・デフォルトコピー・全読み込みを一括実行
        registerMenuSoundProvider()
        pendingInteractionRepository.load()

        // ディレクトリの再チェック
        directoryManager.checkDirectories()

        // プレイヤーキャッシュのクリア（次回アクセス時に再読み込みされる）
        playerStatsRepository.clearCache()

        // SoundManagerの設定再読み込み（config依存のためインスタンス再生成）
        soundManager = SoundManager(this)

        if (::playerPlatformResolver.isInitialized) {
            playerPlatformResolver.clearCache()
        }

        // WorldUnloadServiceの再起動
        worldUnloadService.start()

        logger.info("全てのシステム設定およびデータを再読み込みしました。")
    }

    /** プレイヤーが所有するマイワールド一覧を取得する */
    fun getOwnedWorlds(player: Player): List<WorldData> {
        return worldConfigRepository.findByOwner(player)
    }

    /** リソースファイルが存在しない場合のみ保存する */
    private fun saveResourceIfNotExists(resourcePath: String) {
        val file = java.io.File(dataFolder, resourcePath)
        if (!file.exists()) {
            saveResource(resourcePath, false)
        }
    }

    private fun ensureCCSystemAvailable() {
        val ccSystemPlugin = server.pluginManager.getPlugin("CC-System")
        if (ccSystemPlugin == null || !ccSystemPlugin.isEnabled) {
            throw IllegalStateException("CC-System が有効化されていないため MyWorldManager を起動できません")
        }
        CCSystem.getAPI()
    }

    /**
     * MWM のメニュー音設定を cc-system の MenuSoundService に登録する。
     * onEnable / reloadPlugin の両経路で呼ばれ、Provider を最新の MenuConfigManager で再登録する。
     */
    private fun registerMenuSoundProvider() {
        runCatching {
            CCSystem.getAPI().getMenuSoundService()
                .registerProvider(MwmMenuSoundProvider(this))
        }
    }

    private fun saveSplitLanguageResources() {
        val codeSource = runCatching {
            File(javaClass.protectionDomain.codeSource.location.toURI())
        }.getOrNull() ?: return
        if (!codeSource.isFile) {
            return
        }

        JarFile(codeSource).use { jar ->
            jar.entries().asSequence()
                .filter { !it.isDirectory && it.name.startsWith("lang/") && it.name.endsWith(".yml") }
                .forEach { entry ->
                    val file = File(dataFolder, entry.name)
                    if (!file.exists()) {
                        file.parentFile?.mkdirs()
                        saveResource(entry.name, false)
                    }
                }
        }
    }
}
