package me.awabi2048.myworldmanager

import me.awabi2048.myworldmanager.command.*
import me.awabi2048.myworldmanager.gui.*
import me.awabi2048.myworldmanager.listener.*
import me.awabi2048.myworldmanager.model.WorldData
import me.awabi2048.myworldmanager.repository.*
import me.awabi2048.myworldmanager.service.*
import me.awabi2048.myworldmanager.session.*
import me.awabi2048.myworldmanager.util.*
import org.bukkit.configuration.serialization.ConfigurationSerialization
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class MyWorldManager : JavaPlugin() {

    lateinit var worldConfigRepository: WorldConfigRepository
    lateinit var worldService: WorldService
    lateinit var worldGui: WorldGui
    lateinit var creationSessionManager: CreationSessionManager
    lateinit var templateRepository: TemplateRepository
    lateinit var playerStatsRepository: PlayerStatsRepository
    lateinit var spotlightRepository: SpotlightRepository

    lateinit var settingsSessionManager: SettingsSessionManager
    lateinit var inviteSessionManager: InviteSessionManager
    lateinit var worldSettingsGui: WorldSettingsGui
    lateinit var worldValidator: WorldValidator
    lateinit var portalRepository: PortalRepository
    lateinit var portalManager: PortalManager
    lateinit var memberInviteManager: MemberInviteManager
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
    lateinit var meetGui: MeetGui
    lateinit var playerWorldGui: PlayerWorldGui
    lateinit var userSettingsGui: UserSettingsGui
    lateinit var adminPortalGui: AdminPortalGui
    lateinit var templateWizardGui: TemplateWizardGui
    lateinit var adminCommandGui: AdminCommandGui
    lateinit var spotlightConfirmGui: SpotlightConfirmGui
    lateinit var spotlightRemoveConfirmGui: SpotlightRemoveConfirmGui
    lateinit var environmentGui: EnvironmentGui
    lateinit var environmentConfirmGui: EnvironmentConfirmGui
    lateinit var worldSeedConfirmGui: WorldSeedConfirmGui
    lateinit var languageManager: LanguageManager
    lateinit var previewSessionManager: PreviewSessionManager
    lateinit var adminGuiSessionManager: AdminGuiSessionManager
    lateinit var macroManager: MacroManager
    lateinit var directoryManager: DirectoryManager
    lateinit var worldUnloadService: WorldUnloadService

    override fun onEnable() {
        // Serializationの登録
        ConfigurationSerialization.registerClass(WorldData::class.java)

        // 設定用フォルダの作成
        if (!dataFolder.exists()) {
            dataFolder.mkdirs()
        }
        saveDefaultConfig()

        // langフォルダが存在しなければ作成し、デフォルトの言語ファイルをコピー
        val langFolder = java.io.File(dataFolder, "lang")
        if (!langFolder.exists()) {
            langFolder.mkdirs()
        }
        saveResourceIfNotExists("lang/ja_jp.yml")
        saveResourceIfNotExists("lang/en_us.yml")

        // templates.ymlがなければコピー
        saveResourceIfNotExists("templates.yml")

        // 言語設定の初期化
        languageManager = LanguageManager(this)

        // リポジトリの初期化
        worldConfigRepository = WorldConfigRepository(this)
        templateRepository = TemplateRepository(this)

        directoryManager = DirectoryManager(this, worldConfigRepository, templateRepository)
        // ワールド・テンプレートディレクトリの存在チェック
        directoryManager.checkDirectories()

        playerStatsRepository = PlayerStatsRepository(this)
        portalRepository = PortalRepository(this)
        spotlightRepository = SpotlightRepository(this)

        // サービスの初期化
        worldService = WorldService(this, worldConfigRepository, playerStatsRepository)
        portalManager = PortalManager(this)
        portalManager.startTasks()
        worldUnloadService = WorldUnloadService(this)
        worldUnloadService.start()

        // MSPT監視タスクの開始
        me.awabi2048.myworldmanager.task.MsptMonitorTask(this).start()

        // GUIの初期化
        worldGui = WorldGui(this)
        creationGui = CreationGui(this)
        discoveryGui = DiscoveryGui(this)
        favoriteGui = FavoriteGui(this)
        favoriteMenuGui = FavoriteMenuGui(this)
        favoriteConfirmGui = FavoriteConfirmGui(this)
        meetGui = MeetGui(this)
        playerWorldGui = PlayerWorldGui(this)
        worldSettingsGui = WorldSettingsGui(this)
        userSettingsGui = UserSettingsGui(this)
        adminPortalGui = AdminPortalGui(this)
        adminCommandGui = AdminCommandGui(this)
        templateWizardGui = TemplateWizardGui(this)
        spotlightConfirmGui = SpotlightConfirmGui(this)
        spotlightRemoveConfirmGui = SpotlightRemoveConfirmGui(this)
        environmentGui = EnvironmentGui(this)
        environmentConfirmGui = EnvironmentConfirmGui(this)
        worldSeedConfirmGui = WorldSeedConfirmGui(this)

        creationSessionManager = CreationSessionManager(this)
        inviteSessionManager = InviteSessionManager()
        macroManager = MacroManager(this)
        // MemberInviteManagerの初期化に依存関係を渡す
        memberInviteManager = MemberInviteManager(this, worldConfigRepository, macroManager)

        // 設定機能の初期化
        settingsSessionManager = SettingsSessionManager()
        discoverySessionManager = DiscoverySessionManager()
        meetSessionManager = MeetSessionManager()
        favoriteSessionManager = FavoriteSessionManager()
        playerWorldSessionManager = PlayerWorldSessionManager()
        worldValidator = WorldValidator(this)
        soundManager = SoundManager(this)

        // メニュー設定ファイルの初期化
        val menusFolder = java.io.`File`(dataFolder, "menus")
        if (!menusFolder.exists()) {
            menusFolder.mkdirs()
        }
        listOf(
                        "player_world",
                        "creation",
                        "world_settings",
                        "visit",
                        "favorite",
                        "discovery",
                        "portal",
                        "admin_manage"
                )
                .forEach { menuId -> saveResourceIfNotExists("menus/$menuId.yml") }
        menuConfigManager = MenuConfigManager(this)
        menuConfigManager.initialize()

        previewSessionManager = PreviewSessionManager(this)
        adminGuiSessionManager = AdminGuiSessionManager()

        val inviteCmd = InviteCommand(this)

        // リスナーの登録
        server.pluginManager.registerEvents(WorldStatusListener(), this)
        server.pluginManager.registerEvents(AccessControlListener(this), this)
        server.pluginManager.registerEvents(SpawnListener(worldConfigRepository), this)
        // 旧 GuiListener を分割して登録
        server.pluginManager.registerEvents(PlayerWorldListener(this), this)
        server.pluginManager.registerEvents(VisitListener(this), this)
        server.pluginManager.registerEvents(FavoriteListener(this), this)
        server.pluginManager.registerEvents(MeetListener(this), this)

        server.pluginManager.registerEvents(AdminGuiListener(), this)
        server.pluginManager.registerEvents(AdminCommandListener(), this)
        server.pluginManager.registerEvents(CreationGuiListener(this), this)
        server.pluginManager.registerEvents(CreationChatListener(this), this)
        server.pluginManager.registerEvents(PlayerDataListener(), this)
        server.pluginManager.registerEvents(WorldSettingsListener(), this)
        server.pluginManager.registerEvents(WorldExpirationListener(worldConfigRepository), this)
        server.pluginManager.registerEvents(InviteChatListener(this, inviteCmd), this)
        server.pluginManager.registerEvents(PortalListener(this), this)
        server.pluginManager.registerEvents(PortalGui(this), this)
        server.pluginManager.registerEvents(DiscoveryListener(this), this)
        server.pluginManager.registerEvents(SpotlightListener(this), this)
        server.pluginManager.registerEvents(TemplatePreviewListener(), this)
        server.pluginManager.registerEvents(EnvironmentLogicListener(this), this)
        server.pluginManager.registerEvents(CustomItemListener(this), this)
        server.pluginManager.registerEvents(WorldSeedListener(this), this)
        server.pluginManager.registerEvents(WizardLunaChatListener(this), this)
        server.pluginManager.registerEvents(TemplateWizardListener(), this)
        server.pluginManager.registerEvents(TemplateWizardChatListener(this), this)
        server.pluginManager.registerEvents(ItemConversionListener(this), this)
        server.pluginManager.registerEvents(GlobalMenuListener(this), this)

        // コマンドの登録
        val mwmCmd = WorldCommand(worldService, creationSessionManager)
        getCommand("mwm")?.setExecutor(mwmCmd)
        getCommand("mwm")?.setTabCompleter(mwmCmd)
        getCommand("myworld")?.setExecutor(PlayerWorldCommand(this))
        getCommand("worldmenu")?.setExecutor(WorldMenuCommand(this))
        getCommand("visit")?.let {
            val visitCmd = VisitCommand(this)
            it.setExecutor(visitCmd)
            it.setTabCompleter(visitCmd)
        }

        getCommand("invite")?.setExecutor(inviteCmd)
        getCommand("invite")?.setTabCompleter(inviteCmd)
        getCommand("inviteaccept_internal")?.setExecutor { sender, _, _, _ ->
            if (sender is Player) inviteCmd.handleAccept(sender)
            true
        }
        getCommand("memberinviteaccept_internal")?.setExecutor { sender, _, _, _ ->
            if (sender is Player) memberInviteManager.handleMemberInviteAccept(sender)
            true
        }

        getCommand("favorite")?.setExecutor(FavoriteCommand(this))
        getCommand("discovery")?.setExecutor(DiscoveryCommand(this))

        val meetCmd = MeetCommand(this)
        getCommand("meet")?.setExecutor(meetCmd)
        getCommand("meet")?.setTabCompleter(meetCmd)

        getCommand("settings")?.setExecutor(SettingsCommand(this))

        // 起動完了ログ
        LogUtil.logWithSeparator(
                logger,
                listOf(
                        "MyWorldManager v${description.version} has been enabled!",
                        "Developed by ${description.authors.joinToString(", ")}"
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

    override fun onDisable() {
        if (::worldUnloadService.isInitialized) {
            worldUnloadService.stop()
        }

        LogUtil.logWithSeparator(
                logger,
                listOf("MyWorldManager v${description.version} has been disabled.", "Goodbye!")
        )
    }

    /** 設定ファイルとリポジトリのデータを再読み込みする（再起動と同等の処理） */
    fun reloadSystem() {
        // デフォルト設定の保存（ファイルが存在しない場合のみ作成）
        saveDefaultConfig()

        // config.ymlの再読み込み
        reloadConfig()

        // 各コンポーネントの再読み込み
        languageManager.loadAllLanguages()
        worldConfigRepository.loadAll()
        templateRepository.loadTemplates()
        portalRepository.loadAll()
        spotlightRepository.load()
        macroManager.loadConfig()
        menuConfigManager.initialize() // フォルダ作成・デフォルトコピー・全読み込みを一括実行

        // ディレクトリの再チェック
        directoryManager.checkDirectories()

        // プレイヤーキャッシュのクリア（次回アクセス時に再読み込みされる）
        playerStatsRepository.clearCache()

        // SoundManagerの設定再読み込み（config依存のためインスタンス再生成）
        soundManager = SoundManager(this)

        // WorldUnloadServiceの再起動
        worldUnloadService.start()

        logger.info("全てのシステム設定およびデータを再読み込みしました。")
    }

    /** リソースファイルが存在しない場合のみ保存する */
    private fun saveResourceIfNotExists(resourcePath: String) {
        val file = java.io.File(dataFolder, resourcePath)
        if (!file.exists()) {
            saveResource(resourcePath, false)
        }
    }
}
