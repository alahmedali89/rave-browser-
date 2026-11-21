package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.automation.ActiveDomElementHighlight
import com.example.automation.ActiveRecordingSession
import com.example.automation.AgentRole
import com.example.automation.AmbiguousCandidateInfo
import com.example.automation.AutomationActionType
import com.example.automation.AutomationEngine
import com.example.automation.AutomationErrorCode
import com.example.automation.AutomationHudMode
import com.example.automation.AutomationStep
import com.example.automation.AutomationStepCodec
import com.example.automation.AutomationStepDecodeResult
import com.example.automation.AutomationToolbarStatus
import com.example.automation.AutomationType
import com.example.automation.AutomationVaultCategory
import com.example.automation.AutomationVaultManager
import com.example.automation.BranchTraceRecord
import com.example.automation.ControlKind
import com.example.automation.CredentialFieldRole
import com.example.automation.CredentialFillCoordinator
import com.example.automation.CredentialReplayItemSummary
import com.example.automation.CredentialVaultManager
import com.example.automation.CredentialVaultOperationResult
import com.example.automation.DetectedPageElement
import com.example.automation.DisambiguationSource
import com.example.automation.DomScanStatus
import com.example.automation.DropdownKind
import com.example.automation.ExecutionLifecycleController
import com.example.automation.ExecutionStatus
import com.example.automation.ExportPackageType
import com.example.automation.ExtractedRecord
import com.example.automation.InterruptedExecutionCandidate
import com.example.automation.LocatorCandidate
import com.example.automation.LocatorResolutionDiagnostics
import com.example.automation.LocatorStability
import com.example.automation.LocatorStrategy
import com.example.automation.NanoChatMessage
import com.example.automation.NanoMultiAgentEngine
import com.example.automation.NavigationLifecyclePhase
import com.example.automation.PageInspectionState
import com.example.automation.PendingCredentialReplayAuthorization
import com.example.automation.PendingDisambiguationState
import com.example.automation.PersistedExecutionCheckpoint
import com.example.automation.StepStatus
import com.example.automation.Phase6ExecutionCoordinator
import com.example.automation.RecordedDomEvent
import com.example.automation.RecorderLifecycleResult
import com.example.automation.RecordingErrorInfo
import com.example.automation.RecordingOriginResult
import com.example.automation.RecordingStatus
import com.example.automation.RecordingVaultCandidate
import com.example.automation.RecoveryAttemptRecord
import com.example.automation.RecoveryPolicy
import com.example.automation.RetrieverEngine
import com.example.automation.RevealedCredentialDetails
import com.example.automation.SensitiveFieldCategory
import com.example.automation.StepExecutionRecord
import com.example.automation.StepExecutionResult
import com.example.automation.TabNavigationSynchronizer
import com.example.automation.TabSnapshotTarget
import com.example.automation.VariableDisplayBadge
import com.example.automation.VaultAuthOperation
import com.example.automation.VaultAuthResult
import com.example.automation.VaultExhaustionPromptState
import com.example.automation.VaultFileImportCandidate
import com.example.automation.VaultStatus
import com.example.automation.VaultValueGenerator
import com.example.automation.VaultValuesExhaustedException
import com.example.automation.WorkflowConcurrencyConflictException
import com.example.automation.WorkflowConflictResolution
import com.example.automation.WorkflowDiffReport
import com.example.automation.WorkflowDocument
import com.example.automation.WorkflowDocumentMetadata
import com.example.automation.WorkflowDryRunReport
import com.example.automation.WorkflowExecutionOutcome
import com.example.automation.WorkflowExecutionState
import com.example.automation.WorkflowImportResult
import com.example.automation.WorkflowOrganizationMetadata
import com.example.automation.WorkflowPlayOptions
import com.example.automation.WorkflowSortOption
import com.example.automation.WorkflowValidationEngine
import com.example.automation.WorkflowValidationReport
import com.example.automation.WorkflowValidator
import com.example.automation.WorkflowVariableDefinition
import com.example.automation.WorkflowVariableStore
import com.example.data.local.ApexDatabase
import com.example.data.local.AutomationExecutionEntity
import com.example.data.local.AutomationVaultEntryEntity
import com.example.data.local.AutomationVaultFileEntity
import com.example.data.local.AutomationVaultFolderEntity
import com.example.data.local.AutomationWorkflowEntity
import com.example.data.local.AutomationWorkflowVersionEntity
import com.example.data.local.BlockedDomainLogEntity
import com.example.data.local.BookmarkEntity
import com.example.data.local.BrowsingHistoryEntity
import com.example.data.local.CredentialVaultEntity
import com.example.data.local.DownloadItemEntity
import com.example.data.local.ExtractionRuleEntity
import com.example.data.local.PrivacyStatSummaryEntity
import com.example.data.local.SavedPasswordEntity
import com.example.data.local.SearchHistoryEntity
import com.example.data.local.TopVisitEntity
import com.example.data.preferences.BrowserPreferences
import com.example.data.preferences.DownloadLocationMode
import com.example.data.preferences.HomeWallpaperMode
import com.example.data.preferences.ProSubscriptionPackage
import com.example.data.preferences.SearchEngineOption
import com.example.data.preferences.ThemeModeOption
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.BrowserRepository
import com.example.engine.DownloadStorageHelper
import com.example.engine.FastDownloadEngine
import com.example.engine.HomeWallpaperData
import com.example.engine.HomeWallpaperEngine
import com.example.engine.PasswordVaultUtil
import com.example.engine.PerformanceOptimizer
import com.example.engine.PreloadCandidate
import com.example.engine.PrivacyManager
import com.example.engine.SearchSuggestionEngine
import com.example.engine.SearchSuggestionItem
import org.json.JSONObject
import com.example.ui.browser.SettingsInitialSection
import com.example.ui.theme.SeedPalettePreset
import com.example.ui.theme.TabGroupColorOption
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TabGroupInfo(
    val id: String = "grp_${UUID.randomUUID()}",
    val title: String,
    val colorOption: TabGroupColorOption = TabGroupColorOption.COBALT
)

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Home",
    val url: String = "apex://home",
    val requestedUrl: String = url,
    val loadRequestId: Long = 1L,
    val isPrivate: Boolean = false,
    val groupId: String? = null,
    val tabColor: TabGroupColorOption? = null,
    val isLoading: Boolean = false,
    val progress: Float = 0f,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val blockedCountOnPage: Int = 0,
    val isHttps: Boolean = true,
    val wasUpgradedToHttps: Boolean = false,
    val previewThumbnail: Bitmap? = null,
    val lastActiveTimeMillis: Long = System.currentTimeMillis(),
    val homeWallpaperSeed: String = UUID.randomUUID().toString()
) {
    val isHomePage: Boolean
        get() = url.isBlank() || url.equals("apex://home", ignoreCase = true)
}

sealed interface TabGridEntry {
    val gridKey: String

    data class SingleTab(val tab: BrowserTab) : TabGridEntry {
        override val gridKey: String = "single_${tab.id}"
    }

    data class GroupTab(
        val group: TabGroupInfo,
        val tabs: List<BrowserTab>
    ) : TabGridEntry {
        override val gridKey: String = "group_${group.id}"
    }
}

enum class ExtensionModule {
    NANO,
    RETRIEVER
}

enum class RetrieverDisplayMode {
    TABLE,
    CARDS
}

data class PendingCredentialPrompt(
    val siteName: String,
    val siteUrl: String,
    val username: String,
    val password: String,
    val existingId: Long? = null
)

data class PendingClosedTabUndo(
    val token: Long,
    val tabTitle: String
)

enum class NetworkStatusBannerState {
    HIDDEN,
    OFFLINE,
    BACK_ONLINE
}

enum class BrowserOverlayDestination {
    SETTINGS,
    PASSWORDS,
    DOWNLOADS,
    HISTORY,
    BOOKMARKS,
    TAB_SWITCHER,
    AUTOMATIONS
}

data class PendingDownloadPrompt(
    val url: String,
    val suggestedFileName: String,
    val contentDisposition: String? = null,
    val mimeType: String? = null,
    val contentLength: Long = -1L,
    val userAgent: String? = null
)

data class BrowserUiState(
    // Fresh launch: strictly 1 clean Home tab — no pre-opened tabs
    val tabs: List<BrowserTab> = listOf(
        BrowserTab(id = "tab_home", title = "Home", url = "apex://home")
    ),
    val tabGroups: List<TabGroupInfo> = emptyList(),
    val expandedGroupId: String? = null,
    val editingGroupId: String? = null,
    val activeTabId: String = "tab_home",
    val previousTabIndex: Int = 0,
    val isToolbarVisible: Boolean = true,
    val isBottomBarVisible: Boolean = true,
    val isDeviceOnline: Boolean = true,
    val networkBannerState: NetworkStatusBannerState = NetworkStatusBannerState.HIDDEN,
    val isSearchScreenOpen: Boolean = false,
    val searchQuery: String = "",
    val searchSuggestions: List<SearchSuggestionItem> = emptyList(),
    val isTabSwitcherOpen: Boolean = false,
    val isBookmarksScreenOpen: Boolean = false,
    val isHistoryScreenOpen: Boolean = false,
    val isDownloadsScreenOpen: Boolean = false,
    val isPasswordManagerOpen: Boolean = false,
    val isSettingsScreenOpen: Boolean = false,
    val isAutomationOpen: Boolean = false,
    val isAutomationHudOpen: Boolean = false,
    val isAutomationWorkspaceOpen: Boolean = false,
    val isAutomationVaultOpen: Boolean = false,
    val selectedAutomationWorkflowId: Long? = null,
    val overlayBackStack: List<BrowserOverlayDestination> = emptyList(),
    val isMenuSheetOpen: Boolean = false,
    val settingsInitialSection: SettingsInitialSection = SettingsInitialSection.MAIN,
    val isDefaultBrowserPromoDismissed: Boolean = false,
    val isFindInPageOpen: Boolean = false,
    val findInPageQuery: String = "",
    val findInPageActiveMatchIndex: Int = 0,
    val findInPageTotalMatches: Int = 0,
    val isSyncBackupDialogOpen: Boolean = false,
    val subscriptionPromptReason: String? = null,
    val proActivationError: String? = null,
    val isIncognitoUnlocked: Boolean = false,
    val automationHudMode: AutomationHudMode = AutomationHudMode.OVERVIEW,
    val automationToolbarStatus: AutomationToolbarStatus = AutomationToolbarStatus.IDLE,
    val pageInspection: PageInspectionState = PageInspectionState(),
    val recordingSession: ActiveRecordingSession = ActiveRecordingSession(),
    val recordingVaultCandidates: List<RecordingVaultCandidate> = emptyList(),
    val automationVaultRevealedValues: Map<String, String> = emptyMap(),
    val instructionDraftText: String = "",
    val instructionDraftSteps: List<AutomationStep> = emptyList(),
    val executionState: WorkflowExecutionState? = null,
    val interruptedExecutionCandidate: InterruptedExecutionCandidate? = null,
    val pendingCredentialPrompt: PendingCredentialPrompt? = null,
    val matchedCredentialsForActiveTab: List<SavedPasswordEntity> = emptyList(),
    val vaultStatus: VaultStatus = VaultStatus.LOCKED,
    val vaultStatusMessage: String? = null,
    val revealedVaultCredential: RevealedCredentialDetails? = null,
    val pendingCredentialReplayAuth: PendingCredentialReplayAuthorization? = null,
    val pendingDownloadPrompt: PendingDownloadPrompt? = null,
    val pendingAutoOpenDownload: DownloadItemEntity? = null,
    val activeExtension: ExtensionModule = ExtensionModule.NANO,
    val snackbarNotice: String? = null,
    val pendingClosedTabUndo: PendingClosedTabUndo? = null,
    val preloadCandidates: List<PreloadCandidate> = emptyList(),
    val nanoMessages: List<NanoChatMessage> = listOf(
        NanoChatMessage(
            id = "welcome_nano",
            isUser = false,
            text = "Nano Multi-Agent ready. PlannerAgent, NavigatorAgent, and ValidatorAgent run locally over the active WebView DOM."
        )
    ),
    val isNanoRunning: Boolean = false,
    val selectedRuleId: Long? = null,
    val retrieverPromptInput: String = "",
    val extractedRecords: List<ExtractedRecord> = emptyList(),
    val isRetrieverRunning: Boolean = false,
    val retrieverStatusBanner: String = "Ready for single-page or multi-tab parallel capture",
    val retrieverDisplayMode: RetrieverDisplayMode = RetrieverDisplayMode.TABLE,
    val validationReportsByWorkflowId: Map<Long, WorkflowValidationReport> = emptyMap(),
    val activeDryRunReport: WorkflowDryRunReport? = null,
    val activeWorkflowVersions: List<AutomationWorkflowVersionEntity> = emptyList(),
    val activeDiffReport: WorkflowDiffReport? = null,
    val lastWorkflowImportResult: WorkflowImportResult? = null,
    val workspaceSortOption: WorkflowSortOption = WorkflowSortOption.UPDATED_DESC,
    val workspaceFolderFilter: String? = null,
    val workspaceTagFilter: String? = null,
    val pendingDisambiguation: PendingDisambiguationState? = null,
    val lastLocatorDiagnostics: LocatorResolutionDiagnostics? = null,
    val activeDomHighlight: ActiveDomElementHighlight? = null,
    val isScannerOutlineOverlayEnabled: Boolean = true,
    val vaultExhaustionPromptState: VaultExhaustionPromptState? = null,
    val initialAutomationVaultFolderId: String? = null
) {
    val isSubscriptionDialogOpen: Boolean
        get() = isSyncBackupDialogOpen

    val activeTab: BrowserTab
        get() = tabs.firstOrNull { it.id == activeTabId } ?: tabs.first()

    val activeTabIndex: Int
        get() = tabs.indexOfFirst { it.id == activeTabId }.coerceAtLeast(0)

    val activeTabGroup: TabGroupInfo?
        get() {
            val gId = activeTab.groupId ?: return null
            return tabGroups.firstOrNull { it.id == gId }
        }

    val activeGroupTabs: List<BrowserTab>
        get() {
            val gId = activeTabGroup?.id ?: return emptyList()
            return tabs.filter { it.groupId == gId }
        }

    val editingGroup: TabGroupInfo?
        get() {
            val editId = editingGroupId ?: return null
            return tabGroups.firstOrNull { it.id == editId }
        }

    val incognitoTabCount: Int
        get() = tabs.count { it.isPrivate }

    val gridEntries: List<TabGridEntry>
        get() {
            val emittedGroups = mutableSetOf<String>()
            val result = mutableListOf<TabGridEntry>()
            val groupLookup = tabGroups.associateBy { it.id }

            for (tab in tabs) {
                val gId = tab.groupId
                if (gId == null) {
                    result.add(TabGridEntry.SingleTab(tab))
                } else if (emittedGroups.add(gId)) {
                    val memberTabs = tabs.filter { it.groupId == gId }
                    val info = groupLookup[gId]
                    if (info != null && memberTabs.isNotEmpty()) {
                        result.add(TabGridEntry.GroupTab(group = info, tabs = memberTabs))
                    } else if (memberTabs.size >= 2) {
                        result.add(
                            TabGridEntry.GroupTab(
                                group = TabGroupInfo(id = gId, title = "Tab Group"),
                                tabs = memberTabs
                            )
                        )
                    } else {
                        result.add(TabGridEntry.SingleTab(tab))
                    }
                }
            }
            return result
        }
}

class BrowserViewModel(
    private val repository: BrowserRepository,
    val privacyManager: PrivacyManager = PrivacyManager(),
    val performanceOptimizer: PerformanceOptimizer = PerformanceOptimizer(),
    private val suggestionEngine: SearchSuggestionEngine = SearchSuggestionEngine(),
    private val nanoEngine: NanoMultiAgentEngine = NanoMultiAgentEngine(),
    private val retrieverEngine: RetrieverEngine = RetrieverEngine(),
    private val fastDownloadEngine: FastDownloadEngine? = null,
    private val automationEngine: AutomationEngine = AutomationEngine(),
    private val wallpaperEngine: HomeWallpaperEngine = HomeWallpaperEngine()
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    private val _homeWallpapersBySeed = MutableStateFlow<Map<String, HomeWallpaperData>>(emptyMap())
    val homeWallpapersState: StateFlow<Map<String, HomeWallpaperData>> = _homeWallpapersBySeed.asStateFlow()

    private val _isHomeWallpaperLoading = MutableStateFlow(false)
    val isHomeWallpaperLoadingState: StateFlow<Boolean> = _isHomeWallpaperLoading.asStateFlow()

    private val _customHomeWallpaperBitmap = MutableStateFlow<Bitmap?>(null)
    val customHomeWallpaperBitmapState: StateFlow<Bitmap?> = _customHomeWallpaperBitmap.asStateFlow()

    private val inFlightWallpaperSeeds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    val preferencesState: StateFlow<BrowserPreferences> = repository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = BrowserPreferences()
        )

    val privacyStatsState: StateFlow<PrivacyStatSummaryEntity> = repository.privacyStatsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PrivacyStatSummaryEntity()
        )

    val blockedLogsState: StateFlow<List<BlockedDomainLogEntity>> = repository.recentBlockedLogsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val extractionRulesState: StateFlow<List<ExtractionRuleEntity>> = repository.extractionRulesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val bookmarksState: StateFlow<List<BookmarkEntity>> = repository.bookmarksFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val searchHistoryState: StateFlow<List<SearchHistoryEntity>> = repository.searchHistoryFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val topVisitsState: StateFlow<List<TopVisitEntity>> = repository.topVisitsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val browsingHistoryState: StateFlow<List<BrowsingHistoryEntity>> = repository.browsingHistoryFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val browsingHistoryCountState: StateFlow<Int> = repository.browsingHistoryCountFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )

    val downloadsState: StateFlow<List<DownloadItemEntity>> = (
        if (fastDownloadEngine != null) {
            combine(repository.downloadsFlow, fastDownloadEngine.liveProgress) { items, liveMap ->
                if (liveMap.isEmpty()) {
                    items
                } else {
                    items.map { item ->
                        val live = liveMap[item.id]
                        if (live != null && item.status == FastDownloadEngine.STATUS_DOWNLOADING) {
                            item.copy(
                                downloadedBytes = maxOf(item.downloadedBytes, live.downloadedBytes),
                                totalBytes = if (live.totalBytes > 0L) live.totalBytes else item.totalBytes,
                                speedBytesPerSec = live.speedBytesPerSec,
                                chunksCount = live.chunksCount
                            )
                        } else {
                            item
                        }
                    }
                }
            }
        } else {
            repository.downloadsFlow
        }
        ).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val downloadsCountState: StateFlow<Int> = repository.downloadsCountFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )

    val savedPasswordsState: StateFlow<List<SavedPasswordEntity>> = repository.savedPasswordsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val savedPasswordsCountState: StateFlow<Int> = repository.savedPasswordsCountFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )

    val credentialVaultManager: CredentialVaultManager = repository.credentialVaultManager

    internal val credentialFillCoordinator: CredentialFillCoordinator = CredentialFillCoordinator(
        vaultManager = credentialVaultManager,
        automationEngine = automationEngine
    )

    val credentialVaultEntriesState: StateFlow<List<CredentialVaultEntity>> = repository.credentialVaultEntriesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val credentialVaultCountState: StateFlow<Int> = repository.credentialVaultCountFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )

    val vaultStatusState: StateFlow<VaultStatus> = credentialVaultManager.vaultStatusFlow

    val automationWorkflowsState: StateFlow<List<AutomationWorkflowEntity>> = repository.automationWorkflowsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val automationExecutionsState: StateFlow<List<AutomationExecutionEntity>> = repository.automationExecutionsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val automationVaultEntriesState: StateFlow<List<AutomationVaultEntryEntity>> =
        repository.automationVaultEntriesFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    val automationVaultCountState: StateFlow<Int> = repository.automationVaultCountFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )

    val automationVaultFoldersState: StateFlow<List<AutomationVaultFolderEntity>> =
        repository.automationVaultFoldersFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = AutomationVaultManager.DEFAULT_FOLDERS
            )

    val automationVaultFilesState: StateFlow<List<AutomationVaultFileEntity>> =
        repository.automationVaultFilesFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    // Volatile in-memory secret buffer captured during an active recording session ONLY.
    // Cleared on start/discard/save/onCleared; never persisted in AutomationStep.value or stepsJson.
    private val recordedSecretsByStepId = java.util.concurrent.ConcurrentHashMap<String, String>()
    private var reopenHudAfterVaultClose: Boolean = false

    fun observeExecutionsForWorkflow(workflowId: Long): Flow<List<AutomationExecutionEntity>> {
        return repository.observeAutomationExecutionsForWorkflow(workflowId)
    }

    internal val navigationSynchronizer = TabNavigationSynchronizer()

    private data class RecordedTabVisit(
        val loadRequestId: Long,
        val canonicalUrl: String,
        val host: String,
        val recordedAtMillis: Long
    )

    private data class ClosedTabSnapshot(
        val token: Long,
        val tab: BrowserTab,
        val originalIndex: Int,
        val previousActiveTabId: String,
        val previousExpandedGroupId: String?,
        val groupInfo: TabGroupInfo?,
        val autoCreatedReplacementTabId: String?
    )

    private data class PendingBlockedBatch(
        val pageHost: String,
        val totalCount: Int,
        val trackersDelta: Int,
        val adsDelta: Int,
        val bytesDelta: Long,
        val timeMsDelta: Long,
        val sampleDomain: String,
        val sampleCategory: String
    )

    private val webViewRegistry = mutableMapOf<String, WebView>()
    private val lastRecordedVisitByTab = mutableMapOf<String, RecordedTabVisit>()
    private val lastProgressUpdateAtByTab = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val pendingBlockedByTab = java.util.concurrent.ConcurrentHashMap<String, PendingBlockedBatch>()
    private val closedTabHistory = mutableListOf<ClosedTabSnapshot>()
    private val offlineFailedTabIds = mutableSetOf<String>()
    private var nextClosedTabUndoToken = 1L
    private var scrollDirectionAccumulatorPx: Int = 0
    private var lastScrollDirection: Int = 0
    private var suggestionJob: Job? = null
    private var backOnlineBannerJob: Job? = null
    private var blockedFlushJob: Job? = null
    private var toolbarCompletedResetJob: Job? = null
    private var automationExecutionJob: Job? = null
    private val executionLifecycleController = ExecutionLifecycleController()
    @Volatile
    private var activeExecutionSessionId: String = ""
    private var recordingLifecycleJob: Job? = null
    internal var lastRecordingRejectionCode: AutomationErrorCode = AutomationErrorCode.NONE
        private set
    private val pageInspectionByTab = mutableMapOf<String, PageInspectionState>()
    private val pageScanJobByTab = mutableMapOf<String, Job>()
    private var pendingDestructiveInstructionConfirm: Boolean = false
    private var pendingDestructiveWorkflowId: Long? = null
    private var pendingDestructivePlayOptions: WorkflowPlayOptions? = null
    private var pendingCredentialPlayOptions: WorkflowPlayOptions? = null
    @Volatile
    private var latestExecutionCheckpoint: PersistedExecutionCheckpoint? = null
    @Volatile
    private var pendingInterruptedResumeCheckpoint: PersistedExecutionCheckpoint? = null
    @Volatile
    private var latestInterruptedEntity: AutomationExecutionEntity? = null
    private val blockedBatchLock = Any()

    init {
        nanoEngine.bindScanner(automationEngine.universalScanner)
        retrieverEngine.bindScanner(automationEngine.universalScanner)
        viewModelScope.launch {
            repository.ensureFreshInitialized()
        }
        viewModelScope.launch {
            repository.savedPasswordsFlow.collect {
                refreshMatchedCredentialsForActiveTab()
            }
        }
        viewModelScope.launch {
            credentialVaultManager.vaultStatusFlow.collect { status ->
                _uiState.update { it.copy(vaultStatus = status) }
            }
        }
        viewModelScope.launch {
            repository.latestInterruptedExecutionFlow.collect { entity ->
                latestInterruptedEntity = entity
                refreshInterruptedCandidateFromState()
            }
        }
        if (fastDownloadEngine != null) {
            viewModelScope.launch {
                fastDownloadEngine.autoOpenEvents.collect { completed ->
                    if (preferencesState.value.autoOpenDownloadedFiles) {
                        _uiState.update { it.copy(pendingAutoOpenDownload = completed) }
                    }
                }
            }
        }
        viewModelScope.launch {
            preferencesState
                .map { it.customHomeWallpaperPath }
                .distinctUntilChanged()
                .collect { customPath ->
                    _customHomeWallpaperBitmap.value = if (customPath.isBlank()) {
                        null
                    } else {
                        wallpaperEngine.loadCustomWallpaperBitmap(customPath)
                    }
                }
        }
        viewModelScope.launch {
            preferencesState
                .map { it.homeWallpaperMode }
                .distinctUntilChanged()
                .collect { mode ->
                    if (mode == HomeWallpaperMode.ONLINE_API) {
                        _uiState.value.tabs.forEach { tab ->
                            if (tab.isHomePage) {
                                ensureHomeWallpaperForTab(tab.homeWallpaperSeed)
                            }
                        }
                    }
                }
        }
    }

    fun registerWebView(tabId: String, webView: WebView) {
        val previous = webViewRegistry.put(tabId, webView)
        if (previous != null && previous !== webView) {
            runCatching { previous.removeJavascriptInterface("ApexAutomationBridge") }
            runCatching { previous.removeJavascriptInterface("ApexPasswordBridge") }
            val session = _uiState.value.recordingSession
            if (session.initiatingTabId == tabId) {
                automationEngine.detachRecorderBestEffort(previous, session.sessionId.ifBlank { null })
            }
            if (executionLifecycleController.boundTabId == tabId) {
                executionLifecycleController.requestCancel(
                    code = AutomationErrorCode.WEBVIEW_REPLACED,
                    message = "Execution aborted because the WebView instance was replaced."
                )
                automationExecutionJob?.cancel()
            }
            pageScanJobByTab.remove(tabId)?.cancel()
            automationEngine.universalScanner.onWebViewReplaced(tabId, previous, webView)
            val invalidated = automationEngine.universalScanner.getLatestInspection(tabId)
            if (invalidated != null) {
                pageInspectionByTab[tabId] = invalidated
                if (_uiState.value.activeTabId == tabId) {
                    _uiState.update { it.copy(pageInspection = invalidated) }
                }
            }
        }
    }

    fun getWebView(tabId: String): WebView? = webViewRegistry[tabId]

    fun onWebViewPageLoadFailed(tabId: String, failingUrl: String) {
        onWebViewPageLoadFailedWithReason(tabId, failingUrl, "Page failed to load")
    }

    fun onWebViewPageLoadFailedWithReason(tabId: String, failingUrl: String, reason: String) {
        navigationSynchronizer.onPageFailed(tabId, failingUrl, reason)
        handleDocumentUnavailableForRecording(tabId, reason)
        if (!_uiState.value.isDeviceOnline) {
            offlineFailedTabIds.add(tabId)
        }
    }

    fun onNetworkConnectivityChanged(isOnline: Boolean, autoHideDelayMs: Long = 2_800L) {
        val prevState = _uiState.value
        if (!isOnline) {
            backOnlineBannerJob?.cancel()
            backOnlineBannerJob = null
            if (prevState.isDeviceOnline || prevState.networkBannerState != NetworkStatusBannerState.OFFLINE) {
                _uiState.update {
                    it.copy(
                        isDeviceOnline = false,
                        networkBannerState = NetworkStatusBannerState.OFFLINE
                    )
                }
            }
        } else {
            fastDownloadEngine?.onNetworkAvailable()
            val wasOffline = !prevState.isDeviceOnline || prevState.networkBannerState == NetworkStatusBannerState.OFFLINE
            if (wasOffline) {
                backOnlineBannerJob?.cancel()
                _uiState.update {
                    it.copy(
                        isDeviceOnline = true,
                        networkBannerState = NetworkStatusBannerState.BACK_ONLINE
                    )
                }
                val activeTab = _uiState.value.activeTab
                if (!activeTab.isHomePage && (activeTab.id in offlineFailedTabIds || !prevState.isDeviceOnline)) {
                    offlineFailedTabIds.remove(activeTab.id)
                    reloadActiveTab()
                }
                if (autoHideDelayMs > 0L) {
                    backOnlineBannerJob = viewModelScope.launch {
                        delay(autoHideDelayMs)
                        _uiState.update { current ->
                            if (current.isDeviceOnline && current.networkBannerState == NetworkStatusBannerState.BACK_ONLINE) {
                                current.copy(networkBannerState = NetworkStatusBannerState.HIDDEN)
                            } else {
                                current
                            }
                        }
                    }
                }
            }
        }
    }

    fun consumeSnackbarNotice() {
        _uiState.update { it.copy(snackbarNotice = null) }
    }

    fun consumeClosedTabUndo(token: Long) {
        _uiState.update { state ->
            if (state.pendingClosedTabUndo?.token == token) {
                state.copy(pendingClosedTabUndo = null)
            } else {
                state
            }
        }
    }

    // --- Dedicated Search Screen & Search-Engine Suggestions ---

    fun openSearchScreen(prefillCurrentUrl: Boolean = false) {
        resetBottomBarScrollTracking()
        _uiState.update {
            it.copy(
                isSearchScreenOpen = true,
                isBookmarksScreenOpen = false,
                isHistoryScreenOpen = false,
                isDownloadsScreenOpen = false,
                isPasswordManagerOpen = false,
                isSettingsScreenOpen = false,
                isAutomationWorkspaceOpen = false,
                selectedAutomationWorkflowId = null,
                isAutomationHudOpen = false,
                overlayBackStack = emptyList(),
                isMenuSheetOpen = false,
                isTabSwitcherOpen = false,
                isAutomationOpen = false,
                isToolbarVisible = true,
                isBottomBarVisible = true,
                searchQuery = "",
                searchSuggestions = emptyList()
            )
        }
    }

    fun closeSearchScreen() {
        suggestionJob?.cancel()
        _uiState.update {
            it.copy(
                isSearchScreenOpen = false
            )
        }
    }

    fun onSearchQueryChanged(newQuery: String) {
        _uiState.update {
            it.copy(
                searchQuery = newQuery,
                searchSuggestions = if (newQuery.isBlank()) emptyList() else it.searchSuggestions
            )
        }
        triggerSuggestionsFetch(newQuery, preferencesState.value.searchEngine)
    }

    fun selectSearchEngine(engine: SearchEngineOption) {
        viewModelScope.launch {
            repository.setSearchEngine(engine)
            val currentQ = _uiState.value.searchQuery
            if (currentQ.isNotBlank()) {
                triggerSuggestionsFetch(currentQ, engine)
            }
        }
    }

    private fun triggerSuggestionsFetch(query: String, engine: SearchEngineOption) {
        suggestionJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _uiState.update {
                it.copy(searchSuggestions = emptyList())
            }
            return
        }
        suggestionJob = viewModelScope.launch {
            delay(160)
            val results = suggestionEngine.fetchSuggestions(trimmed, engine)
            _uiState.update { state ->
                if (state.searchQuery.trim() == trimmed) {
                    state.copy(
                        searchSuggestions = results
                    )
                } else {
                    state
                }
            }
        }
    }

    fun deleteSearchHistoryItem(id: Long) {
        viewModelScope.launch { repository.deleteSearchHistoryItem(id) }
    }

    fun clearAllSearchHistory() {
        viewModelScope.launch { repository.clearAllSearchHistory() }
    }

    fun submitNavigation(rawInput: String, targetTabId: String = _uiState.value.activeTabId) {
        val trimmedInput = rawInput.trim()
        if (trimmedInput.isEmpty()) return

        val prefs = preferencesState.value
        val normalized = privacyManager.normalizeAndUpgradeUrl(
            rawInput = trimmedInput,
            httpsUpgradeEnabled = prefs.httpsUpgradeEnabled,
            searchEngineTemplate = prefs.searchEngineTemplate
        )

        val targetTab = _uiState.value.tabs.firstOrNull { it.id == targetTabId }
        if (!normalized.isBuiltInScheme && targetTab?.isPrivate != true) {
            viewModelScope.launch {
                repository.recordSearchQuery(
                    query = trimmedInput,
                    engineName = prefs.searchEngine.displayName
                )
            }
        }

        if (normalized.wasUpgradedToHttps) {
            viewModelScope.launch { repository.recordHttpsUpgrade() }
        }

        suggestionJob?.cancel()
        lastRecordedVisitByTab.remove(targetTabId)
        handleOriginNavigationForRecording(targetTabId, normalized.finalUrl)

        navigationSynchronizer.beginNavigation(targetTabId, normalized.finalUrl)
        pageScanJobByTab.remove(targetTabId)?.cancel()
        automationEngine.universalScanner.onTabNavigated(targetTabId, normalized.finalUrl)
        val navInvalidated = automationEngine.universalScanner.getLatestInspection(targetTabId)
        if (navInvalidated != null) {
            pageInspectionByTab[targetTabId] = navInvalidated
        }
        val isHomeDest = normalized.finalUrl.equals("apex://home", ignoreCase = true)
        _uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == targetTabId) {
                    tab.copy(
                        url = normalized.finalUrl,
                        requestedUrl = normalized.finalUrl,
                        loadRequestId = tab.loadRequestId + 1L,
                        title = if (isHomeDest) {
                            "Home"
                        } else {
                            deriveShortTitle(normalized.finalUrl)
                        },
                        blockedCountOnPage = 0,
                        isHttps = normalized.finalUrl.startsWith("https://") || normalized.isBuiltInScheme,
                        wasUpgradedToHttps = normalized.wasUpgradedToHttps,
                        isLoading = !isHomeDest,
                        progress = if (isHomeDest) 0f else 0.06f,
                        previewThumbnail = null
                    )
                } else {
                    tab
                }
            }
            state.copy(
                tabs = updatedTabs,
                isSearchScreenOpen = false,
                isBookmarksScreenOpen = false,
                isHistoryScreenOpen = false,
                isDownloadsScreenOpen = false,
                isPasswordManagerOpen = false,
                isSettingsScreenOpen = false,
                isAutomationWorkspaceOpen = false,
                selectedAutomationWorkflowId = null,
                isTabSwitcherOpen = false,
                overlayBackStack = emptyList(),
                isToolbarVisible = true,
                isBottomBarVisible = true
            )
        }
        resetBottomBarScrollTracking()
        if (targetTabId == _uiState.value.activeTabId) {
            if (isHomeDest) {
                pageInspectionByTab[targetTabId] = PageInspectionState()
                _uiState.update { it.copy(pageInspection = PageInspectionState()) }
                refreshAutomationToolbarStatus(clearTransientResult = true)
            } else {
                val nextInspection = navInvalidated ?: PageInspectionState(
                    domain = automationEngine.extractDomain(normalized.finalUrl),
                    url = normalized.finalUrl,
                    tabId = targetTabId
                )
                pageInspectionByTab[targetTabId] = nextInspection
                _uiState.update {
                    it.copy(pageInspection = nextInspection)
                }
                refreshAutomationToolbarStatus(clearTransientResult = true)
            }
        }
    }

    fun recordHttpsUpgradeStat() {
        viewModelScope.launch { repository.recordHttpsUpgrade() }
    }

    fun onWebViewHttpsUpgraded(tabId: String, upgradedUrl: String) {
        recordHttpsUpgradeStat()
        if (upgradedUrl.isNotBlank()) {
            handleOriginNavigationForRecording(tabId, upgradedUrl)
        }
    }

    fun openLinkInNewTab(
        rawUrl: String,
        inGroup: Boolean = false,
        isPrivate: Boolean = false,
        switchToTab: Boolean = true
    ) {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return

        if (switchToTab) {
            snapshotOutgoingTabThumbnail(_uiState.value.activeTabId)
        }

        val prefs = preferencesState.value
        val normalized = privacyManager.normalizeAndUpgradeUrl(
            rawInput = trimmed,
            httpsUpgradeEnabled = prefs.httpsUpgradeEnabled,
            searchEngineTemplate = prefs.searchEngineTemplate
        )

        _uiState.update { state ->
            val active = state.activeTab
            var updatedGroups = state.tabGroups
            var targetGroupId: String? = null
            var chosenColor: TabGroupColorOption? = null

            if (inGroup) {
                if (active.groupId != null) {
                    targetGroupId = active.groupId
                    chosenColor = state.tabGroups.firstOrNull { it.id == targetGroupId }?.colorOption ?: active.tabColor
                } else {
                    val newGrpId = "grp_${UUID.randomUUID()}"
                    val grpNumber = state.tabGroups.size + 1
                    chosenColor = active.tabColor ?: TabGroupColorOption.entries[
                        state.tabGroups.size % TabGroupColorOption.entries.size
                    ]
                    val newGrp = TabGroupInfo(
                        id = newGrpId,
                        title = "Group $grpNumber",
                        colorOption = chosenColor
                    )
                    updatedGroups = state.tabGroups + newGrp
                    targetGroupId = newGrpId
                }
            }

            val newTab = BrowserTab(
                title = deriveShortTitle(normalized.finalUrl),
                url = normalized.finalUrl,
                requestedUrl = normalized.finalUrl,
                loadRequestId = 1L,
                isPrivate = isPrivate,
                groupId = targetGroupId,
                tabColor = chosenColor,
                isLoading = true,
                progress = 0.06f,
                isHttps = normalized.finalUrl.startsWith("https://") || normalized.isBuiltInScheme,
                wasUpgradedToHttps = normalized.wasUpgradedToHttps
            )

            val baseTabs = if (inGroup && active.groupId == null && targetGroupId != null) {
                state.tabs.map { tab ->
                    if (tab.id == active.id) {
                        tab.copy(groupId = targetGroupId, tabColor = tab.tabColor ?: chosenColor)
                    } else {
                        tab
                    }
                }
            } else {
                state.tabs
            }

            val noticeText = when {
                isPrivate -> "Opened in Private tab"
                inGroup -> "Opened in new tab in group"
                else -> "Opened in new tab"
            }

            state.copy(
                previousTabIndex = state.activeTabIndex,
                tabs = baseTabs + newTab,
                tabGroups = updatedGroups,
                activeTabId = if (switchToTab) newTab.id else state.activeTabId,
                isHistoryScreenOpen = if (switchToTab) false else state.isHistoryScreenOpen,
                isDownloadsScreenOpen = if (switchToTab) false else state.isDownloadsScreenOpen,
                isPasswordManagerOpen = if (switchToTab) false else state.isPasswordManagerOpen,
                isBookmarksScreenOpen = if (switchToTab) false else state.isBookmarksScreenOpen,
                isSettingsScreenOpen = if (switchToTab) false else state.isSettingsScreenOpen,
                isAutomationWorkspaceOpen = if (switchToTab) false else state.isAutomationWorkspaceOpen,
                selectedAutomationWorkflowId = if (switchToTab) null else state.selectedAutomationWorkflowId,
                isAutomationHudOpen = if (switchToTab) false else state.isAutomationHudOpen,
                isTabSwitcherOpen = if (switchToTab) false else state.isTabSwitcherOpen,
                overlayBackStack = if (switchToTab) emptyList() else state.overlayBackStack,
                isIncognitoUnlocked = state.isIncognitoUnlocked || isPrivate,
                isToolbarVisible = true,
                isBottomBarVisible = true,
                snackbarNotice = noticeText
            )
        }
        if (switchToTab) {
            resetBottomBarScrollTracking()
        }
    }

    fun addLinkToReadingList(title: String, url: String) {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) return
        val cleanTitle = title.trim().ifEmpty { deriveShortTitle(cleanUrl) }
        viewModelScope.launch {
            repository.saveBookmark(url = cleanUrl, title = cleanTitle)
            _uiState.update {
                it.copy(snackbarNotice = "Added '$cleanTitle' to reading list")
            }
        }
    }

    fun onWebViewPageStarted(tabId: String, url: String) {
        if (url.startsWith("data:", ignoreCase = true)) {
            handleOriginNavigationForRecording(tabId, url)
            return
        }
        if (url.isBlank()) return
        lastProgressUpdateAtByTab[tabId] = System.currentTimeMillis()
        val currentTab = _uiState.value.tabs.firstOrNull { it.id == tabId }
        val resolvedStartedUrl = if (currentTab != null) mapBaseUrlBackToScheme(url, currentTab.url) else url
        navigationSynchronizer.onPageStarted(tabId, resolvedStartedUrl)
        handleOriginNavigationForRecording(tabId, resolvedStartedUrl)
        automationEngine.universalScanner.onTabNavigated(tabId, resolvedStartedUrl)
        val navInvalidated = automationEngine.universalScanner.getLatestInspection(tabId)
        if (navInvalidated != null) {
            pageScanJobByTab.remove(tabId)?.cancel()
            pageInspectionByTab[tabId] = navInvalidated
            if (_uiState.value.activeTabId == tabId) {
                _uiState.update { it.copy(pageInspection = navInvalidated) }
            }
        }
        if (tabId == _uiState.value.activeTabId) {
            resetBottomBarScrollTracking()
        }
        _uiState.update { state ->
            state.copy(
                isBottomBarVisible = if (tabId == state.activeTabId) true else state.isBottomBarVisible,
                tabs = state.tabs.map { tab ->
                    if (tab.id == tabId) {
                        val effectiveUrl = mapBaseUrlBackToScheme(url, tab.url)
                        tab.copy(
                            url = effectiveUrl,
                            isLoading = true,
                            progress = if (tab.isLoading) tab.progress.coerceAtLeast(0.08f) else 0.08f,
                            isHttps = effectiveUrl.startsWith("https://") || effectiveUrl.startsWith("apex://")
                        )
                    } else {
                        tab
                    }
                }
            )
        }
    }

    fun onWebViewUrlCommitted(tabId: String, url: String) {
        if (url.startsWith("data:", ignoreCase = true)) {
            handleOriginNavigationForRecording(tabId, url)
            return
        }
        if (url.isBlank()) return
        val currentTab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
        val effectiveUrl = mapBaseUrlBackToScheme(url, currentTab.url)
        navigationSynchronizer.onUrlCommitted(tabId, effectiveUrl, isReload = false)
        handleOriginNavigationForRecording(tabId, effectiveUrl)
        automationEngine.universalScanner.onTabNavigated(tabId, effectiveUrl)
        val navInvalidated = automationEngine.universalScanner.getLatestInspection(tabId)
        if (navInvalidated != null) {
            pageScanJobByTab.remove(tabId)?.cancel()
            pageInspectionByTab[tabId] = navInvalidated
            if (_uiState.value.activeTabId == tabId) {
                _uiState.update { it.copy(pageInspection = navInvalidated) }
            }
        }
        val isHttps = effectiveUrl.startsWith("https://") || effectiveUrl.startsWith("apex://")
        if (currentTab.url == effectiveUrl && currentTab.isHttps == isHttps) return
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map { tab ->
                    if (tab.id == tabId) {
                        tab.copy(
                            url = effectiveUrl,
                            isHttps = isHttps
                        )
                    } else {
                        tab
                    }
                }
            )
        }
    }

    fun onWebViewProgress(tabId: String, progress: Int) {
        val wv = webViewRegistry[tabId]
        val existingTab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
        val isDone = progress >= 100

        // Prevent late subframe/iframe progress callbacks (< 100) from flipping a finished page
        // back into isLoading = true and causing the top loading bar to flicker on and off.
        if (!isDone && !existingTab.isLoading) {
            return
        }

        val rawTargetProgress = (progress / 100f).coerceIn(0f, 1f)
        val monotonicProgress = if (isDone) {
            1f
        } else {
            rawTargetProgress.coerceAtLeast(existingTab.progress)
        }
        val rawWvUrl = wv?.url?.trim().orEmpty()
        if (rawWvUrl.isNotEmpty() && rawWvUrl != existingTab.url) {
            val checkUrl = if (rawWvUrl.startsWith("data:", ignoreCase = true)) {
                rawWvUrl
            } else {
                mapBaseUrlBackToScheme(rawWvUrl, existingTab.url)
            }
            handleOriginNavigationForRecording(tabId, checkUrl)
        }
        val liveUrl = wv?.url?.takeIf { it.isNotBlank() && !it.startsWith("data:") }
            ?.let { mapBaseUrlBackToScheme(it, existingTab.url) }
            ?: existingTab.url
        val liveTitle = wv?.title?.takeIf { it.isNotBlank() } ?: existingTab.title
        val canGoBack = wv?.canGoBack() ?: existingTab.canGoBack
        val canGoForward = wv?.canGoForward() ?: existingTab.canGoForward

        val now = System.currentTimeMillis()
        val lastUpdateAt = lastProgressUpdateAtByTab[tabId] ?: 0L
        val navStateChanged = existingTab.url != liveUrl ||
            existingTab.canGoBack != canGoBack ||
            existingTab.canGoForward != canGoForward

        if (!isDone && !navStateChanged) {
            val progressDelta = monotonicProgress - existingTab.progress
            if (progressDelta < 0.08f || (now - lastUpdateAt) < 80L) {
                return
            }
        }
        lastProgressUpdateAtByTab[tabId] = now
        if (liveUrl != existingTab.url) {
            handleOriginNavigationForRecording(tabId, liveUrl)
        }

        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map { tab ->
                    if (tab.id == tabId) {
                        tab.copy(
                            url = liveUrl,
                            title = if (isDone) liveTitle else tab.title,
                            progress = monotonicProgress,
                            isLoading = !isDone,
                            canGoBack = canGoBack,
                            canGoForward = canGoForward
                        )
                    } else {
                        tab
                    }
                }
            )
        }
    }

    fun onWebViewPageFinished(
        tabId: String,
        title: String?,
        url: String?,
        canGoBack: Boolean,
        canGoForward: Boolean
    ) {
        flushPendingBlockedBatches()
        lastProgressUpdateAtByTab[tabId] = System.currentTimeMillis()
        var finishedUrl = ""
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map { tab ->
                    if (tab.id == tabId) {
                        val effectiveUrl = if (!url.isNullOrBlank()) {
                            mapBaseUrlBackToScheme(url, tab.url)
                        } else {
                            tab.url
                        }
                        finishedUrl = effectiveUrl
                        tab.copy(
                            title = title?.takeIf { it.isNotBlank() } ?: deriveShortTitle(effectiveUrl),
                            url = effectiveUrl,
                            isLoading = false,
                            progress = 1f,
                            canGoBack = canGoBack,
                            canGoForward = canGoForward,
                            lastActiveTimeMillis = System.currentTimeMillis()
                        )
                    } else {
                        tab
                    }
                }
            )
        }

        val finishedTab = _uiState.value.tabs.firstOrNull { it.id == tabId }
        val resolvedFinishedUrl = finishedUrl.ifBlank { url.orEmpty() }
        navigationSynchronizer.onPageFinished(
            tabId = tabId,
            url = resolvedFinishedUrl
        )
        handleOriginNavigationForRecording(tabId, resolvedFinishedUrl)
        if (tabId == _uiState.value.activeTabId) {
            refreshMatchedCredentialsForActiveTab()
        }
        val wv = webViewRegistry[tabId]
        val activeSession = _uiState.value.recordingSession
        if (wv != null &&
            activeSession.status == RecordingStatus.RECORDING &&
            activeSession.initiatingTabId == tabId &&
            activeSession.sessionId.isNotBlank() &&
            automationEngine.areSameOrigin(resolvedFinishedUrl, activeSession.currentOrigin)
        ) {
            val expectedSessionId = activeSession.sessionId
            val expectedOrigin = activeSession.currentOrigin
            val expectedSeq = activeSession.lastSequenceNumber
            recordingLifecycleJob?.cancel()
            recordingLifecycleJob = viewModelScope.launch {
                val reinstall = automationEngine.installRecorderScript(
                    webView = wv,
                    sessionId = expectedSessionId,
                    tabId = tabId,
                    expectedOrigin = expectedOrigin,
                    initialSequenceNumber = expectedSeq
                )
                if (!reinstall.success) {
                    val current = _uiState.value.recordingSession
                    if (current.sessionId == expectedSessionId && current.status == RecordingStatus.RECORDING) {
                        _uiState.update { state ->
                            state.copy(
                                recordingSession = state.recordingSession.copy(
                                    status = RecordingStatus.ERROR,
                                    sessionId = "",
                                    recordingError = RecordingErrorInfo(
                                        code = reinstall.errorCode,
                                        message = reinstall.message.ifBlank { "Recorder failed to reattach after page load." }
                                    )
                                )
                            )
                        }
                        refreshAutomationToolbarStatus(clearTransientResult = false)
                    }
                }
            }
        }
        if (wv != null && tabId == _uiState.value.activeTabId) {
            if (finishedTab != null && !finishedTab.isHomePage) {
                scanActivePageElements(silent = true)
            } else {
                pageInspectionByTab[tabId] = PageInspectionState()
                refreshAutomationToolbarStatus(clearTransientResult = false)
            }
        }
        if (wv != null && tabId == _uiState.value.activeTabId && finishedTab?.isPrivate != true) {
            performanceOptimizer.preloadLikelyNextPages(
                webView = wv,
                enabled = preferencesState.value.aggressivePreloadEnabled
            ) { candidates ->
                _uiState.update { it.copy(preloadCandidates = candidates) }
            }
        }

        // Record browsing history for non-private tabs (deduplicated across multiple WebView onPageFinished callbacks)
        if (finishedTab != null &&
            !finishedTab.isPrivate &&
            !finishedTab.isHomePage &&
            finishedUrl.isNotBlank() &&
            !finishedUrl.equals("about:blank", ignoreCase = true) &&
            !finishedUrl.startsWith("data:", ignoreCase = true)
        ) {
            val canonicalUrl = repository.normalizeHistoryUrl(finishedUrl)
            if (canonicalUrl.isNotBlank()) {
                val now = System.currentTimeMillis()
                val host = canonicalUrl.substringAfter("://", canonicalUrl)
                    .substringBefore('/')
                    .removePrefix("www.")
                    .lowercase()
                val prev = lastRecordedVisitByTab[tabId]

                val isSameNavigationVisit = prev != null && (
                    prev.canonicalUrl.equals(canonicalUrl, ignoreCase = true) ||
                        (prev.loadRequestId == finishedTab.loadRequestId &&
                            (now - prev.recordedAtMillis) < 12_000L) ||
                        ((now - prev.recordedAtMillis) < 4_500L)
                )

                val previousUrlInSameLoad = if (isSameNavigationVisit) prev?.canonicalUrl else null
                lastRecordedVisitByTab[tabId] = RecordedTabVisit(
                    loadRequestId = finishedTab.loadRequestId,
                    canonicalUrl = canonicalUrl,
                    host = host,
                    recordedAtMillis = if (isSameNavigationVisit && prev != null) prev.recordedAtMillis else now
                )

                val shouldIncrementVisit = !isSameNavigationVisit
                val pageTitle = finishedTab.title
                val pageIsHttps = finishedTab.isHttps
                val pageBlockedCount = finishedTab.blockedCountOnPage

                viewModelScope.launch {
                    repository.recordPageVisit(
                        url = canonicalUrl,
                        title = pageTitle,
                        isHttps = pageIsHttps,
                        trackersBlockedOnVisit = pageBlockedCount,
                        incrementVisitCount = shouldIncrementVisit,
                        previousUrlInSameLoad = previousUrlInSameLoad
                    )
                }
            }
        }
    }

    fun onSubresourceBlocked(
        tabId: String,
        pageHost: String,
        blockedDomain: String,
        category: String,
        bytesSaved: Long,
        timeSavedMs: Long
    ) {
        val trackersAdd = if (category == "Tracker") 1 else 0
        val adsAdd = if (category == "Ad Network") 1 else 0
        synchronized(blockedBatchLock) {
            val current = pendingBlockedByTab[tabId]
            pendingBlockedByTab[tabId] = if (current == null) {
                PendingBlockedBatch(
                    pageHost = pageHost,
                    totalCount = 1,
                    trackersDelta = trackersAdd,
                    adsDelta = adsAdd,
                    bytesDelta = bytesSaved,
                    timeMsDelta = timeSavedMs,
                    sampleDomain = blockedDomain,
                    sampleCategory = category
                )
            } else {
                current.copy(
                    pageHost = pageHost.ifBlank { current.pageHost },
                    totalCount = current.totalCount + 1,
                    trackersDelta = current.trackersDelta + trackersAdd,
                    adsDelta = current.adsDelta + adsAdd,
                    bytesDelta = current.bytesDelta + bytesSaved,
                    timeMsDelta = current.timeMsDelta + timeSavedMs,
                    sampleDomain = blockedDomain.ifBlank { current.sampleDomain },
                    sampleCategory = category.ifBlank { current.sampleCategory }
                )
            }
            if (blockedFlushJob?.isActive != true) {
                blockedFlushJob = viewModelScope.launch {
                    delay(380L)
                    flushPendingBlockedBatches()
                }
            }
        }
    }

    private fun flushPendingBlockedBatches() {
        val drained: Map<String, PendingBlockedBatch>
        synchronized(blockedBatchLock) {
            blockedFlushJob?.cancel()
            blockedFlushJob = null
            if (pendingBlockedByTab.isEmpty()) return
            drained = HashMap(pendingBlockedByTab)
            pendingBlockedByTab.clear()
        }

        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map { tab ->
                    val batch = drained[tab.id]
                    if (batch != null) {
                        tab.copy(blockedCountOnPage = tab.blockedCountOnPage + batch.totalCount)
                    } else {
                        tab
                    }
                }
            )
        }

        viewModelScope.launch {
            drained.values.forEach { batch ->
                repository.recordBlockedItems(
                    pageHost = batch.pageHost,
                    trackersDelta = batch.trackersDelta,
                    adsDelta = batch.adsDelta,
                    bytesDelta = batch.bytesDelta,
                    timeMsDelta = batch.timeMsDelta,
                    sampleDomain = batch.sampleDomain,
                    sampleCategory = batch.sampleCategory
                )
            }
        }
    }

    private fun resetBottomBarScrollTracking() {
        scrollDirectionAccumulatorPx = 0
        lastScrollDirection = 0
    }

    fun onWebViewScrollDelta(dy: Int, scrollY: Int = -1) {
        // Keep the top toolbar stably visible during WebView scrolling so Scaffold
        // never animates innerPadding.top and resizes the WebView mid-gesture.
        val currentState = _uiState.value
        if (!currentState.isToolbarVisible) {
            _uiState.update { it.copy(isToolbarVisible = true) }
        }
        if (currentState.activeTab.isHomePage) {
            resetBottomBarScrollTracking()
            if (!currentState.isBottomBarVisible) {
                _uiState.update { it.copy(isBottomBarVisible = true) }
            }
            return
        }
        if (scrollY in 0..12) {
            resetBottomBarScrollTracking()
            if (!currentState.isBottomBarVisible) {
                _uiState.update { it.copy(isBottomBarVisible = true) }
            }
            return
        }
        if (dy == 0) return

        val direction = if (dy > 0) 1 else -1
        if (lastScrollDirection != 0 && lastScrollDirection != direction) {
            scrollDirectionAccumulatorPx = 0
        }
        lastScrollDirection = direction
        scrollDirectionAccumulatorPx += dy

        if (direction > 0) {
            // Scrolling down the webpage (finger swiping toward top): hide bottom navigation bar
            val pastTopBuffer = scrollY < 0 || scrollY > 28
            if (pastTopBuffer && scrollDirectionAccumulatorPx >= 24) {
                scrollDirectionAccumulatorPx = 0
                if (_uiState.value.isBottomBarVisible) {
                    _uiState.update { it.copy(isBottomBarVisible = false) }
                }
            }
        } else {
            // Scrolling up the webpage (finger swiping toward bottom): show bottom navigation bar
            if (scrollDirectionAccumulatorPx <= -18) {
                scrollDirectionAccumulatorPx = 0
                if (!_uiState.value.isBottomBarVisible) {
                    _uiState.update { it.copy(isBottomBarVisible = true) }
                }
            }
        }
    }

    // --- Tab & Draggable Tab Group Management ---

    fun selectTab(tabId: String) {
        val outgoingTabId = _uiState.value.activeTabId
        if (outgoingTabId != tabId && !_uiState.value.isTabSwitcherOpen) {
            snapshotOutgoingTabThumbnail(outgoingTabId)
        }
        resetBottomBarScrollTracking()
        _uiState.update { state ->
            val prevIdx = state.activeTabIndex
            val targetTab = state.tabs.firstOrNull { it.id == tabId } ?: return@update state
            state.copy(
                previousTabIndex = prevIdx,
                activeTabId = tabId,
                expandedGroupId = null,
                isTabSwitcherOpen = false,
                isBookmarksScreenOpen = false,
                isHistoryScreenOpen = false,
                isDownloadsScreenOpen = false,
                isPasswordManagerOpen = false,
                isSettingsScreenOpen = false,
                isAutomationWorkspaceOpen = false,
                selectedAutomationWorkflowId = null,
                overlayBackStack = emptyList(),
                isSearchScreenOpen = false,
                isToolbarVisible = true,
                isBottomBarVisible = true,
                pageInspection = pageInspectionByTab[tabId] ?: if (targetTab.isHomePage) {
                    PageInspectionState(tabId = tabId)
                } else {
                    PageInspectionState(
                        tabId = tabId,
                        domain = automationEngine.extractDomain(targetTab.url),
                        url = targetTab.url
                    )
                }
            )
        }
        refreshMatchedCredentialsForActiveTab()
        val selectedTab = _uiState.value.activeTab
        val wv = webViewRegistry[tabId]
        if (wv != null) {
            if (!selectedTab.isHomePage && (pageInspectionByTab[tabId]?.elements.isNullOrEmpty())) {
                scanActivePageElements(silent = true)
            }
        }
        val activeSession = _uiState.value.recordingSession
        if ((activeSession.status == RecordingStatus.RECORDING || activeSession.status == RecordingStatus.PAUSED) &&
            activeSession.initiatingTabId.isNotBlank() &&
            tabId != activeSession.initiatingTabId
        ) {
            _uiState.update { state ->
                state.copy(
                    snackbarNotice = "Recording continues in ${activeSession.targetDomain.ifBlank { "original" }} tab"
                )
            }
        }
        refreshAutomationToolbarStatus(clearTransientResult = true)
    }

    fun selectNextTab() {
        val state = _uiState.value
        val nextIndex = state.activeTabIndex + 1
        if (nextIndex in state.tabs.indices) {
            selectTab(state.tabs[nextIndex].id)
        }
    }

    fun selectPreviousTab() {
        val state = _uiState.value
        val prevIndex = state.activeTabIndex - 1
        if (prevIndex in state.tabs.indices) {
            selectTab(state.tabs[prevIndex].id)
        }
    }

    fun createNewTab(
        initialUrl: String = "apex://home",
        groupId: String? = null,
        stayInSwitcher: Boolean = groupId != null
    ) {
        val currentState = _uiState.value
        if (!stayInSwitcher && !currentState.isTabSwitcherOpen) {
            snapshotOutgoingTabThumbnail(currentState.activeTabId)
        }
        val inheritedColor = groupId?.let { gId ->
            currentState.tabGroups.firstOrNull { it.id == gId }?.colorOption
        }
        val groupIsPrivate = groupId?.let { gId ->
            currentState.tabs.any { it.groupId == gId && it.isPrivate }
        } ?: false
        val newTab = BrowserTab(
            title = if (initialUrl == "apex://home") {
                if (groupIsPrivate) "Incognito" else "Home"
            } else {
                deriveShortTitle(initialUrl)
            },
            url = initialUrl,
            isPrivate = groupIsPrivate,
            groupId = groupId,
            tabColor = inheritedColor
        )
        _uiState.update { state ->
            state.copy(
                previousTabIndex = state.activeTabIndex,
                tabs = state.tabs + newTab,
                activeTabId = if (!stayInSwitcher) newTab.id else state.activeTabId,
                isTabSwitcherOpen = stayInSwitcher,
                isIncognitoUnlocked = state.isIncognitoUnlocked || groupIsPrivate,
                isBookmarksScreenOpen = false,
                isHistoryScreenOpen = false,
                isDownloadsScreenOpen = false,
                isPasswordManagerOpen = false,
                isSettingsScreenOpen = false,
                isAutomationWorkspaceOpen = false,
                selectedAutomationWorkflowId = null,
                isAutomationHudOpen = false,
                overlayBackStack = if (stayInSwitcher) state.overlayBackStack else emptyList(),
                isSearchScreenOpen = false,
                isToolbarVisible = true,
                isBottomBarVisible = true
            )
        }
        if (!stayInSwitcher) {
            resetBottomBarScrollTracking()
        }
        if (initialUrl != "apex://home") {
            submitNavigation(initialUrl, newTab.id)
        } else {
            ensureHomeWallpaperForTab(newTab.homeWallpaperSeed)
        }
    }

    fun createNewIncognitoTab(
        initialUrl: String = "apex://home",
        stayInSwitcher: Boolean = false
    ) {
        val outgoingState = _uiState.value
        if (!stayInSwitcher && !outgoingState.isTabSwitcherOpen) {
            snapshotOutgoingTabThumbnail(outgoingState.activeTabId)
        }
        val newTab = BrowserTab(
            title = if (initialUrl == "apex://home") "Incognito" else deriveShortTitle(initialUrl),
            url = initialUrl,
            isPrivate = true
        )
        _uiState.update { state ->
            state.copy(
                previousTabIndex = state.activeTabIndex,
                tabs = state.tabs + newTab,
                activeTabId = if (!stayInSwitcher) newTab.id else state.activeTabId,
                isTabSwitcherOpen = stayInSwitcher,
                isBookmarksScreenOpen = false,
                isHistoryScreenOpen = false,
                isDownloadsScreenOpen = false,
                isPasswordManagerOpen = false,
                isSettingsScreenOpen = false,
                isAutomationWorkspaceOpen = false,
                selectedAutomationWorkflowId = null,
                isAutomationHudOpen = false,
                overlayBackStack = if (stayInSwitcher) state.overlayBackStack else emptyList(),
                isMenuSheetOpen = false,
                isSearchScreenOpen = false,
                isIncognitoUnlocked = true,
                isToolbarVisible = true,
                isBottomBarVisible = true,
                snackbarNotice = "Incognito mode active • History, cookies & site data will not be saved"
            )
        }
        if (!stayInSwitcher) {
            resetBottomBarScrollTracking()
        }
        if (initialUrl != "apex://home") {
            submitNavigation(initialUrl, newTab.id)
        } else {
            ensureHomeWallpaperForTab(newTab.homeWallpaperSeed)
        }
    }

    fun closeAllIncognitoTabs() {
        val currentState = _uiState.value
        val incognitoIds = currentState.tabs.filter { it.isPrivate }.map { it.id }.toSet()
        if (incognitoIds.isEmpty()) {
            _uiState.update { it.copy(isIncognitoUnlocked = false) }
            return
        }

        handleTabsClosedForRecording(incognitoIds)
        incognitoIds.forEach { id ->
            lastRecordedVisitByTab.remove(id)
            navigationSynchronizer.onTabClosed(id)
            pageScanJobByTab.remove(id)?.cancel()
            pageInspectionByTab.remove(id)
            automationEngine.universalScanner.invalidateTabSession(id, DomScanStatus.SESSION_INVALIDATED, "Tab closed.", webViewRegistry[id])
            webViewRegistry.remove(id)?.let { wv ->
                destroyWebViewSafely(wv, isIncognito = true)
            }
        }
        performanceOptimizer.wipeIncognitoSessionStorage()

        _uiState.update { state ->
            val remaining = state.tabs.filterNot { it.isPrivate }
            if (remaining.isEmpty()) {
                val fresh = BrowserTab(title = "Home", url = "apex://home")
                state.copy(
                    tabs = listOf(fresh),
                    tabGroups = emptyList(),
                    expandedGroupId = null,
                    editingGroupId = null,
                    activeTabId = fresh.id,
                    isIncognitoUnlocked = false,
                    snackbarNotice = "Incognito session ended • Cookies & site data wiped"
                )
            } else {
                val (cleanTabs, cleanGroups) = normalizeGroups(remaining, state.tabGroups)
                val nextActiveId = if (state.activeTabId in incognitoIds) {
                    cleanTabs.last().id
                } else {
                    state.activeTabId
                }
                state.copy(
                    tabs = cleanTabs,
                    tabGroups = cleanGroups,
                    activeTabId = nextActiveId,
                    isIncognitoUnlocked = false,
                    snackbarNotice = "Incognito session ended • Cookies & site data wiped"
                )
            }
        }
    }

    fun closeSelectedTabs(tabIds: Set<String>) {
        if (tabIds.isEmpty()) return
        val currentTabs = _uiState.value.tabs
        val closingTabs = currentTabs.filter { it.id in tabIds }
        val closedAnyIncognito = closingTabs.any { it.isPrivate }

        handleTabsClosedForRecording(closingTabs.map { it.id }.toSet())
        closingTabs.forEach { tab ->
            lastRecordedVisitByTab.remove(tab.id)
            navigationSynchronizer.onTabClosed(tab.id)
            pageScanJobByTab.remove(tab.id)?.cancel()
            pageInspectionByTab.remove(tab.id)
            automationEngine.universalScanner.invalidateTabSession(tab.id, DomScanStatus.SESSION_INVALIDATED, "Tab closed.", webViewRegistry[tab.id])
            webViewRegistry.remove(tab.id)?.let { wv ->
                destroyWebViewSafely(wv, isIncognito = tab.isPrivate)
            }
        }

        val remainingAfterClose = currentTabs.filterNot { it.id in tabIds }
        val anyIncognitoLeft = remainingAfterClose.any { it.isPrivate }
        if (closedAnyIncognito && !anyIncognitoLeft) {
            performanceOptimizer.wipeIncognitoSessionStorage()
        }

        _uiState.update { state ->
            val remaining = state.tabs.filterNot { it.id in tabIds }
            if (remaining.isEmpty()) {
                val fresh = BrowserTab(title = "Home", url = "apex://home")
                state.copy(
                    tabs = listOf(fresh),
                    tabGroups = emptyList(),
                    expandedGroupId = null,
                    editingGroupId = null,
                    activeTabId = fresh.id,
                    isIncognitoUnlocked = false,
                    snackbarNotice = "Closed ${closingTabs.size} tab${if (closingTabs.size == 1) "" else "s"}"
                )
            } else {
                val (cleanTabs, cleanGroups) = normalizeGroups(remaining, state.tabGroups)
                val nextActiveId = if (state.activeTabId in tabIds) {
                    cleanTabs.last().id
                } else {
                    state.activeTabId
                }
                state.copy(
                    tabs = cleanTabs,
                    tabGroups = cleanGroups,
                    activeTabId = nextActiveId,
                    isIncognitoUnlocked = anyIncognitoLeft,
                    snackbarNotice = "Closed ${closingTabs.size} tab${if (closingTabs.size == 1) "" else "s"}"
                )
            }
        }
    }

    fun groupSelectedTabs(tabIds: Set<String>) {
        if (tabIds.isEmpty()) return
        _uiState.update { state ->
            val validIds = state.tabs.map { it.id }.toSet()
            val matchedIds = tabIds.intersect(validIds)
            if (matchedIds.isEmpty()) return@update state
            val newGroupId = "grp_${UUID.randomUUID()}"
            val groupNumber = state.tabGroups.size + 1
            val defaultColor = TabGroupColorOption.entries[
                state.tabGroups.size % TabGroupColorOption.entries.size
            ]
            val newGroup = TabGroupInfo(
                id = newGroupId,
                title = "Group $groupNumber",
                colorOption = defaultColor
            )
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id in matchedIds) {
                    tab.copy(groupId = newGroupId, tabColor = tab.tabColor ?: defaultColor)
                } else {
                    tab
                }
            }
            val (cleanTabs, cleanGroups) = normalizeGroups(updatedTabs, state.tabGroups + newGroup)
            state.copy(
                tabs = cleanTabs,
                tabGroups = cleanGroups,
                snackbarNotice = "Grouped ${matchedIds.size} tab${if (matchedIds.size == 1) "" else "s"} into ${newGroup.title}"
            )
        }
    }

    fun createNewTabGroupFromSwitcher(isPrivate: Boolean = false) {
        _uiState.update { state ->
            val newGroupId = "grp_${UUID.randomUUID()}"
            val groupNumber = state.tabGroups.size + 1
            val defaultColor = TabGroupColorOption.entries[
                state.tabGroups.size % TabGroupColorOption.entries.size
            ]
            val newGroup = TabGroupInfo(
                id = newGroupId,
                title = "Group $groupNumber",
                colorOption = defaultColor
            )

            val defaultTabTitle = if (isPrivate) "Incognito" else "Home"
            val ungroupedTabs = state.tabs.filter { it.groupId == null && it.isPrivate == isPrivate }
            val updatedTabs = if (ungroupedTabs.size >= 2) {
                val idsToGroup = ungroupedTabs.takeLast(2).map { it.id }.toSet()
                state.tabs.map { tab ->
                    if (tab.id in idsToGroup) {
                        tab.copy(groupId = newGroupId, tabColor = tab.tabColor ?: defaultColor)
                    } else {
                        tab
                    }
                }
            } else if (ungroupedTabs.size == 1) {
                val existingUngroupedId = ungroupedTabs.single().id
                val companionTab = BrowserTab(
                    title = defaultTabTitle,
                    url = "apex://home",
                    isPrivate = isPrivate,
                    groupId = newGroupId,
                    tabColor = defaultColor
                )
                state.tabs.map { tab ->
                    if (tab.id == existingUngroupedId) {
                        tab.copy(groupId = newGroupId, tabColor = tab.tabColor ?: defaultColor)
                    } else {
                        tab
                    }
                } + companionTab
            } else {
                val t1 = BrowserTab(
                    title = defaultTabTitle,
                    url = "apex://home",
                    isPrivate = isPrivate,
                    groupId = newGroupId,
                    tabColor = defaultColor
                )
                val t2 = BrowserTab(
                    title = defaultTabTitle,
                    url = "apex://home",
                    isPrivate = isPrivate,
                    groupId = newGroupId,
                    tabColor = defaultColor
                )
                state.tabs + t1 + t2
            }

            val (cleanTabs, cleanGroups) = normalizeGroups(updatedTabs, state.tabGroups + newGroup)
            state.copy(
                tabs = cleanTabs,
                tabGroups = cleanGroups,
                isIncognitoUnlocked = state.isIncognitoUnlocked || isPrivate,
                editingGroupId = newGroupId,
                isTabSwitcherOpen = true
            )
        }
        _uiState.value.tabs.forEach { tab ->
            if (tab.isHomePage) {
                ensureHomeWallpaperForTab(tab.homeWallpaperSeed)
            }
        }
    }

    fun mergeGridEntriesIntoGroup(draggedKey: String, targetKey: String) {
        if (draggedKey == targetKey) return

        _uiState.update { state ->
            val draggedIsGroup = draggedKey.startsWith("group_")
            val targetIsGroup = targetKey.startsWith("group_")

            val draggedId = draggedKey.removePrefix("single_").removePrefix("group_")
            val targetId = targetKey.removePrefix("single_").removePrefix("group_")

            when {
                !draggedIsGroup && !targetIsGroup -> {
                    val newGroupId = "grp_${UUID.randomUUID()}"
                    val groupNumber = state.tabGroups.size + 1
                    val targetTab = state.tabs.firstOrNull { it.id == targetId }
                    val draggedTab = state.tabs.firstOrNull { it.id == draggedId }
                    val chosenColor = targetTab?.tabColor
                        ?: draggedTab?.tabColor
                        ?: TabGroupColorOption.entries[
                            state.tabGroups.size % TabGroupColorOption.entries.size
                        ]
                    val newGroup = TabGroupInfo(
                        id = newGroupId,
                        title = "Group $groupNumber",
                        colorOption = chosenColor
                    )

                    val updatedTabs = state.tabs.map { tab ->
                        if (tab.id == targetId || tab.id == draggedId) {
                            tab.copy(groupId = newGroupId, tabColor = tab.tabColor ?: chosenColor)
                        } else {
                            tab
                        }
                    }
                    normalizeGroups(
                        tabs = updatedTabs,
                        groups = state.tabGroups + newGroup
                    ).let { (cleanTabs, cleanGroups) ->
                        state.copy(tabs = cleanTabs, tabGroups = cleanGroups)
                    }
                }

                !draggedIsGroup && targetIsGroup -> {
                    val targetGroupColor = state.tabGroups.firstOrNull { it.id == targetId }?.colorOption
                    val updatedTabs = state.tabs.map { tab ->
                        if (tab.id == draggedId) {
                            tab.copy(groupId = targetId, tabColor = tab.tabColor ?: targetGroupColor)
                        } else {
                            tab
                        }
                    }
                    normalizeGroups(updatedTabs, state.tabGroups).let { (cleanTabs, cleanGroups) ->
                        state.copy(tabs = cleanTabs, tabGroups = cleanGroups)
                    }
                }

                draggedIsGroup && !targetIsGroup -> {
                    val draggedGroupColor = state.tabGroups.firstOrNull { it.id == draggedId }?.colorOption
                    val updatedTabs = state.tabs.map { tab ->
                        if (tab.id == targetId) {
                            tab.copy(groupId = draggedId, tabColor = tab.tabColor ?: draggedGroupColor)
                        } else {
                            tab
                        }
                    }
                    normalizeGroups(updatedTabs, state.tabGroups).let { (cleanTabs, cleanGroups) ->
                        state.copy(tabs = cleanTabs, tabGroups = cleanGroups)
                    }
                }

                else -> {
                    val targetGroupColor = state.tabGroups.firstOrNull { it.id == targetId }?.colorOption
                    val updatedTabs = state.tabs.map { tab ->
                        if (tab.groupId == draggedId) {
                            tab.copy(groupId = targetId, tabColor = targetGroupColor ?: tab.tabColor)
                        } else {
                            tab
                        }
                    }
                    normalizeGroups(updatedTabs, state.tabGroups).let { (cleanTabs, cleanGroups) ->
                        state.copy(
                            tabs = cleanTabs,
                            tabGroups = cleanGroups,
                            expandedGroupId = if (state.expandedGroupId == draggedId) targetId else state.expandedGroupId
                        )
                    }
                }
            }
        }
    }

    fun moveGroupedTabToGridTarget(tabId: String, targetGridKey: String?) {
        if (targetGridKey == null) {
            ungroupTab(tabId)
            return
        }
        val currentTab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
        val sourceGroupKey = currentTab.groupId?.let { "group_$it" }
        if (targetGridKey == sourceGroupKey || targetGridKey == "single_$tabId") {
            return
        }

        _uiState.update { state ->
            if (targetGridKey.startsWith("group_")) {
                val destGroupId = targetGridKey.removePrefix("group_")
                val destColor = state.tabGroups.firstOrNull { it.id == destGroupId }?.colorOption
                val updatedTabs = state.tabs.map { tab ->
                    if (tab.id == tabId) {
                        tab.copy(groupId = destGroupId, tabColor = destColor ?: tab.tabColor)
                    } else {
                        tab
                    }
                }
                val (cleanTabs, cleanGroups) = normalizeGroups(updatedTabs, state.tabGroups)
                state.copy(tabs = cleanTabs, tabGroups = cleanGroups)
            } else {
                val otherTabId = targetGridKey.removePrefix("single_")
                val newGroupId = "grp_${UUID.randomUUID()}"
                val groupNumber = state.tabGroups.size + 1
                val chosenColor = TabGroupColorOption.entries[
                    state.tabGroups.size % TabGroupColorOption.entries.size
                ]
                val newGroup = TabGroupInfo(
                    id = newGroupId,
                    title = "Group $groupNumber",
                    colorOption = chosenColor
                )
                val updatedTabs = state.tabs.map { tab ->
                    if (tab.id == tabId || tab.id == otherTabId) {
                        tab.copy(groupId = newGroupId, tabColor = chosenColor)
                    } else {
                        tab
                    }
                }
                val (cleanTabs, cleanGroups) = normalizeGroups(updatedTabs, state.tabGroups + newGroup)
                state.copy(tabs = cleanTabs, tabGroups = cleanGroups)
            }
        }
    }

    fun openGroupEditor(groupId: String) {
        _uiState.update { it.copy(editingGroupId = groupId) }
    }

    fun dismissGroupEditor() {
        _uiState.update { it.copy(editingGroupId = null) }
    }

    fun updateGroupTitleAndColor(
        groupId: String,
        newTitle: String,
        colorOption: TabGroupColorOption
    ) {
        _uiState.update { state ->
            val trimmed = newTitle.trim()
            val updatedGroups = state.tabGroups.map { grp ->
                if (grp.id == groupId) {
                    grp.copy(
                        title = trimmed.ifEmpty { grp.title },
                        colorOption = colorOption
                    )
                } else {
                    grp
                }
            }
            val updatedTabs = state.tabs.map { tab ->
                if (tab.groupId == groupId) {
                    tab.copy(tabColor = colorOption)
                } else {
                    tab
                }
            }
            state.copy(
                tabs = updatedTabs,
                tabGroups = updatedGroups,
                editingGroupId = null
            )
        }
    }

    fun cycleTabColor(tabId: String) {
        _uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == tabId) {
                    val nextColor = tab.tabColor?.next() ?: TabGroupColorOption.COBALT
                    tab.copy(tabColor = nextColor)
                } else {
                    tab
                }
            }
            state.copy(tabs = updatedTabs)
        }
    }

    fun ungroupTab(tabId: String) {
        _uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == tabId) tab.copy(groupId = null) else tab
            }
            val (cleanTabs, cleanGroups) = normalizeGroups(updatedTabs, state.tabGroups)
            val validExpanded = state.expandedGroupId?.takeIf { expId ->
                cleanGroups.any { it.id == expId }
            }
            val validEditing = state.editingGroupId?.takeIf { editId ->
                cleanGroups.any { it.id == editId }
            }
            state.copy(
                tabs = cleanTabs,
                tabGroups = cleanGroups,
                expandedGroupId = validExpanded,
                editingGroupId = validEditing
            )
        }
    }

    fun ungroupAllInGroup(groupId: String) {
        _uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.groupId == groupId) tab.copy(groupId = null) else tab
            }
            val remainingGroups = state.tabGroups.filterNot { it.id == groupId }
            state.copy(
                tabs = updatedTabs,
                tabGroups = remainingGroups,
                expandedGroupId = if (state.expandedGroupId == groupId) null else state.expandedGroupId,
                editingGroupId = if (state.editingGroupId == groupId) null else state.editingGroupId
            )
        }
    }

    fun setExpandedGroup(groupId: String?) {
        _uiState.update { it.copy(expandedGroupId = groupId) }
    }

    fun closeGroup(groupId: String) {
        val currentTabs = _uiState.value.tabs
        val groupTabs = currentTabs.filter { it.groupId == groupId }
        val groupTabIds = groupTabs.map { it.id }.toSet()
        val closedAnyIncognito = groupTabs.any { it.isPrivate }

        handleTabsClosedForRecording(groupTabIds)
        if (executionLifecycleController.boundTabId in groupTabIds) {
            executionLifecycleController.requestInterrupt(
                code = AutomationErrorCode.TAB_CLOSED,
                message = "Execution interrupted because the target tab group was closed."
            )
            automationExecutionJob?.cancel()
        }
        groupTabs.forEach { tab ->
            lastRecordedVisitByTab.remove(tab.id)
            navigationSynchronizer.onTabClosed(tab.id)
            pageScanJobByTab.remove(tab.id)?.cancel()
            pageInspectionByTab.remove(tab.id)
            automationEngine.universalScanner.invalidateTabSession(tab.id, DomScanStatus.SESSION_INVALIDATED, "Tab closed.", webViewRegistry[tab.id])
            webViewRegistry.remove(tab.id)?.let { wv ->
                destroyWebViewSafely(wv, isIncognito = tab.isPrivate)
            }
        }

        val remainingTabsAfterClose = currentTabs.filterNot { it.id in groupTabIds }
        val incognitoSessionEnded = closedAnyIncognito && remainingTabsAfterClose.none { it.isPrivate }
        if (incognitoSessionEnded) {
            performanceOptimizer.wipeIncognitoSessionStorage()
        }

        _uiState.update { state ->
            val remaining = state.tabs.filterNot { it.id in groupTabIds }
            val sessionNotice = if (incognitoSessionEnded) {
                "Incognito session ended • Cookies & site data wiped"
            } else {
                state.snackbarNotice
            }
            if (remaining.isEmpty()) {
                val fresh = BrowserTab(title = "Home", url = "apex://home")
                state.copy(
                    tabs = listOf(fresh),
                    tabGroups = emptyList(),
                    expandedGroupId = null,
                    editingGroupId = null,
                    activeTabId = fresh.id,
                    snackbarNotice = sessionNotice
                )
            } else {
                val (cleanTabs, cleanGroups) = normalizeGroups(remaining, state.tabGroups)
                val nextActive = if (state.activeTabId in groupTabIds) {
                    cleanTabs.last().id
                } else {
                    state.activeTabId
                }
                state.copy(
                    tabs = cleanTabs,
                    tabGroups = cleanGroups,
                    expandedGroupId = if (state.expandedGroupId == groupId) null else state.expandedGroupId,
                    editingGroupId = if (state.editingGroupId == groupId) null else state.editingGroupId,
                    activeTabId = nextActive,
                    snackbarNotice = sessionNotice
                )
            }
        }
    }

    fun closeTab(tabId: String) {
        val snapshotBeforeClose = _uiState.value
        val closingIndex = snapshotBeforeClose.tabs.indexOfFirst { it.id == tabId }
        val closingTab = if (closingIndex >= 0) snapshotBeforeClose.tabs[closingIndex] else null
        if (closingTab == null) return

        val closingGroupInfo = closingTab.groupId?.let { gId ->
            snapshotBeforeClose.tabGroups.firstOrNull { it.id == gId }
        }
        val undoToken = nextClosedTabUndoToken++

        handleTabsClosedForRecording(setOf(tabId))
        if (executionLifecycleController.boundTabId == tabId) {
            executionLifecycleController.requestInterrupt(
                code = AutomationErrorCode.TAB_CLOSED,
                message = "Execution interrupted because the target tab was closed."
            )
            automationExecutionJob?.cancel()
        }
        lastRecordedVisitByTab.remove(tabId)
        navigationSynchronizer.onTabClosed(tabId)
        pageScanJobByTab.remove(tabId)?.cancel()
        pageInspectionByTab.remove(tabId)
        automationEngine.universalScanner.invalidateTabSession(tabId, DomScanStatus.SESSION_INVALIDATED, "Tab closed.", webViewRegistry[tabId])
        webViewRegistry.remove(tabId)?.let { wv ->
            destroyWebViewSafely(wv, isIncognito = closingTab.isPrivate)
        }

        val remainingAfterClose = snapshotBeforeClose.tabs.filterNot { it.id == tabId }
        val incognitoSessionEnded = closingTab.isPrivate && remainingAfterClose.none { it.isPrivate }
        if (incognitoSessionEnded) {
            performanceOptimizer.wipeIncognitoSessionStorage()
        }

        var autoReplacementTabId: String? = null
        _uiState.update { state ->
            val closedTab = state.tabs.firstOrNull { it.id == tabId }
            val remaining = state.tabs.filterNot { it.id == tabId }
            val sessionNotice = if (incognitoSessionEnded) {
                "Incognito session ended • Cookies & site data wiped"
            } else {
                state.snackbarNotice
            }
            if (remaining.isEmpty()) {
                val fresh = BrowserTab(title = "Home", url = "apex://home")
                autoReplacementTabId = fresh.id
                state.copy(
                    tabs = listOf(fresh),
                    tabGroups = emptyList(),
                    expandedGroupId = null,
                    editingGroupId = null,
                    activeTabId = fresh.id,
                    isIncognitoUnlocked = false,
                    snackbarNotice = sessionNotice,
                    pendingClosedTabUndo = PendingClosedTabUndo(
                        token = undoToken,
                        tabTitle = closedTab?.title ?: closingTab.title
                    )
                )
            } else {
                val (cleanTabs, cleanGroups) = normalizeGroups(remaining, state.tabGroups)
                val nextActiveId = if (state.activeTabId == tabId) {
                    val closedGroupId = closedTab?.groupId
                    val siblingInGroup = closedGroupId?.let { gId ->
                        cleanTabs.lastOrNull { it.groupId == gId }
                    }
                    siblingInGroup?.id ?: cleanTabs.last().id
                } else {
                    state.activeTabId
                }
                val validExpanded = state.expandedGroupId?.takeIf { expId ->
                    cleanGroups.any { it.id == expId }
                }
                val validEditing = state.editingGroupId?.takeIf { editId ->
                    cleanGroups.any { it.id == editId }
                }
                state.copy(
                    tabs = cleanTabs,
                    tabGroups = cleanGroups,
                    expandedGroupId = validExpanded,
                    editingGroupId = validEditing,
                    activeTabId = nextActiveId,
                    isIncognitoUnlocked = cleanTabs.any { it.isPrivate },
                    snackbarNotice = sessionNotice,
                    pendingClosedTabUndo = PendingClosedTabUndo(
                        token = undoToken,
                        tabTitle = closedTab?.title ?: closingTab.title
                    )
                )
            }
        }

        closedTabHistory.add(
            ClosedTabSnapshot(
                token = undoToken,
                tab = closingTab,
                originalIndex = closingIndex,
                previousActiveTabId = snapshotBeforeClose.activeTabId,
                previousExpandedGroupId = snapshotBeforeClose.expandedGroupId,
                groupInfo = closingGroupInfo,
                autoCreatedReplacementTabId = autoReplacementTabId
            )
        )
        if (closedTabHistory.size > 10) {
            closedTabHistory.removeAt(0)
        }
    }

    fun undoLastClosedTab() {
        val snapshot = closedTabHistory.removeLastOrNull() ?: return
        _uiState.update { state ->
            val baseTabs = if (
                snapshot.autoCreatedReplacementTabId != null &&
                state.tabs.size == 1 &&
                state.tabs.first().id == snapshot.autoCreatedReplacementTabId
            ) {
                emptyList()
            } else {
                state.tabs
            }

            if (baseTabs.any { it.id == snapshot.tab.id }) {
                return@update state.copy(pendingClosedTabUndo = null)
            }

            val restoredTabs = baseTabs.toMutableList().apply {
                add(snapshot.originalIndex.coerceIn(0, size), snapshot.tab)
            }
            val restoredGroups = if (
                snapshot.groupInfo != null &&
                state.tabGroups.none { it.id == snapshot.groupInfo.id }
            ) {
                state.tabGroups + snapshot.groupInfo
            } else {
                state.tabGroups
            }
            val nextActiveId = when {
                snapshot.autoCreatedReplacementTabId != null -> snapshot.tab.id
                snapshot.previousActiveTabId == snapshot.tab.id -> snapshot.tab.id
                restoredTabs.any { it.id == state.activeTabId } -> state.activeTabId
                else -> snapshot.tab.id
            }
            val nextExpandedGroupId = when {
                state.expandedGroupId != null && restoredGroups.any { it.id == state.expandedGroupId } -> {
                    state.expandedGroupId
                }
                snapshot.previousExpandedGroupId != null &&
                    restoredGroups.any { it.id == snapshot.previousExpandedGroupId } -> {
                    snapshot.previousExpandedGroupId
                }
                else -> null
            }

            state.copy(
                tabs = restoredTabs,
                tabGroups = restoredGroups,
                activeTabId = nextActiveId,
                expandedGroupId = nextExpandedGroupId,
                isIncognitoUnlocked = restoredTabs.any { it.isPrivate },
                pendingClosedTabUndo = null
            )
        }
    }

    private fun normalizeGroups(
        tabs: List<BrowserTab>,
        groups: List<TabGroupInfo>
    ): Pair<List<BrowserTab>, List<TabGroupInfo>> {
        val countsByGroup = tabs.mapNotNull { it.groupId }.groupingBy { it }.eachCount()
        val knownGroupIds = groups.map { it.id }.toSet()
        val validGroupIds = countsByGroup.filter { (gId, count) ->
            if (gId in knownGroupIds) count >= 1 else count >= 2
        }.keys
        val cleanedTabs = tabs.map { tab ->
            if (tab.groupId != null && tab.groupId !in validGroupIds) {
                tab.copy(groupId = null)
            } else {
                tab
            }
        }
        val cleanedGroups = groups.filter { it.id in validGroupIds }
        return cleanedTabs to cleanedGroups
    }

    fun resetAllTabs() {
        val currentTabs = _uiState.value.tabs
        val hadIncognito = currentTabs.any { it.isPrivate }
        handleTabsClosedForRecording(currentTabs.map { it.id }.toSet())
        if (executionLifecycleController.boundTabId.isNotEmpty()) {
            executionLifecycleController.requestInterrupt(
                code = AutomationErrorCode.TAB_CLOSED,
                message = "Execution interrupted because all tabs were reset."
            )
            automationExecutionJob?.cancel()
        }
        currentTabs.forEach { tab ->
            pageScanJobByTab.remove(tab.id)?.cancel()
            pageInspectionByTab.remove(tab.id)
            navigationSynchronizer.onTabClosed(tab.id)
            val wv = webViewRegistry.remove(tab.id)
            automationEngine.universalScanner.invalidateTabSession(
                tabId = tab.id,
                reason = "All tabs reset",
                webView = wv
            )
            if (wv != null) {
                destroyWebViewSafely(wv, isIncognito = tab.isPrivate)
            }
        }
        pageScanJobByTab.values.forEach { it.cancel() }
        pageScanJobByTab.clear()
        pageInspectionByTab.clear()
        webViewRegistry.values.forEach { destroyWebViewSafely(it, isIncognito = false) }
        webViewRegistry.clear()
        lastRecordedVisitByTab.clear()
        closedTabHistory.clear()
        if (hadIncognito) {
            performanceOptimizer.wipeIncognitoSessionStorage()
        }
        val fresh = BrowserTab(title = "Home", url = "apex://home")
        _uiState.update {
            it.copy(
                tabs = listOf(fresh),
                tabGroups = emptyList(),
                expandedGroupId = null,
                editingGroupId = null,
                activeTabId = fresh.id,
                isIncognitoUnlocked = false,
                isTabSwitcherOpen = false,
                pendingClosedTabUndo = null,
                pageInspection = PageInspectionState(tabId = fresh.id),
                snackbarNotice = if (hadIncognito) {
                    "All tabs closed • Incognito session data wiped"
                } else {
                    it.snackbarNotice
                }
            )
        }
    }

    // --- Context-Aware Bottom Bar Controls (Home, Bookmarks, Search, Tabs) ---

    fun navigateBack() {
        val active = _uiState.value.activeTab
        val wv = webViewRegistry[active.id]
        if (!active.isHomePage && wv != null && wv.canGoBack()) {
            wv.goBack()
        } else if (!active.isHomePage) {
            handleOriginNavigationForRecording(active.id, "apex://home")
            navigationSynchronizer.beginNavigation(active.id, "apex://home")
            wv?.setTag(-19048102, null)
            wv?.clearMatches()
            lastRecordedVisitByTab.remove(active.id)
            pageInspectionByTab[active.id] = PageInspectionState()
            _uiState.update { state ->
                state.copy(
                    tabs = state.tabs.map { tab ->
                        if (tab.id == active.id) {
                            tab.copy(
                                url = "apex://home",
                                requestedUrl = "apex://home",
                                title = if (tab.isPrivate) "Incognito" else "Home",
                                isLoading = false,
                                progress = 0f,
                                canGoBack = false,
                                canGoForward = false,
                                isHttps = true,
                                wasUpgradedToHttps = false,
                                blockedCountOnPage = 0,
                                previewThumbnail = null
                            )
                        } else {
                            tab
                        }
                    },
                    isFindInPageOpen = false,
                    findInPageQuery = "",
                    findInPageActiveMatchIndex = 0,
                    findInPageTotalMatches = 0,
                    isToolbarVisible = true,
                    matchedCredentialsForActiveTab = emptyList(),
                    pendingCredentialPrompt = null,
                    pageInspection = PageInspectionState()
                )
            }
            refreshAutomationToolbarStatus(clearTransientResult = true)
        }
    }

    fun navigateForward() {
        val active = _uiState.value.activeTab
        val wv = webViewRegistry[active.id]
        if (!active.isHomePage && wv != null && wv.canGoForward()) {
            wv.goForward()
        }
    }

    private fun BrowserUiState.activeOverlayDestination(): BrowserOverlayDestination? = when {
        isAutomationWorkspaceOpen -> BrowserOverlayDestination.AUTOMATIONS
        isPasswordManagerOpen -> BrowserOverlayDestination.PASSWORDS
        isDownloadsScreenOpen -> BrowserOverlayDestination.DOWNLOADS
        isHistoryScreenOpen -> BrowserOverlayDestination.HISTORY
        isBookmarksScreenOpen -> BrowserOverlayDestination.BOOKMARKS
        isSettingsScreenOpen -> BrowserOverlayDestination.SETTINGS
        isTabSwitcherOpen -> BrowserOverlayDestination.TAB_SWITCHER
        else -> null
    }

    private fun BrowserUiState.withActiveOverlay(
        destination: BrowserOverlayDestination?,
        backStack: List<BrowserOverlayDestination>
    ): BrowserUiState = copy(
        isSettingsScreenOpen = destination == BrowserOverlayDestination.SETTINGS,
        isPasswordManagerOpen = destination == BrowserOverlayDestination.PASSWORDS,
        isDownloadsScreenOpen = destination == BrowserOverlayDestination.DOWNLOADS,
        isHistoryScreenOpen = destination == BrowserOverlayDestination.HISTORY,
        isBookmarksScreenOpen = destination == BrowserOverlayDestination.BOOKMARKS,
        isTabSwitcherOpen = destination == BrowserOverlayDestination.TAB_SWITCHER,
        isAutomationWorkspaceOpen = destination == BrowserOverlayDestination.AUTOMATIONS,
        selectedAutomationWorkflowId = if (destination == BrowserOverlayDestination.AUTOMATIONS) {
            selectedAutomationWorkflowId
        } else {
            null
        },
        expandedGroupId = if (destination != BrowserOverlayDestination.TAB_SWITCHER) null else expandedGroupId,
        isMenuSheetOpen = if (destination != null) false else isMenuSheetOpen,
        isFindInPageOpen = if (destination != null) false else isFindInPageOpen,
        isSearchScreenOpen = if (destination != null) false else isSearchScreenOpen,
        isAutomationOpen = if (destination != null) false else isAutomationOpen,
        isAutomationHudOpen = if (destination != null) false else isAutomationHudOpen,
        isToolbarVisible = true,
        isBottomBarVisible = true,
        overlayBackStack = backStack
    )

    private fun BrowserUiState.transitionOverlay(
        target: BrowserOverlayDestination,
        open: Boolean
    ): BrowserUiState {
        val current = activeOverlayDestination()
        return if (open) {
            if (current == target) {
                copy(
                    isMenuSheetOpen = false,
                    isSearchScreenOpen = false,
                    isAutomationOpen = false,
                    isAutomationHudOpen = false,
                    isToolbarVisible = true,
                    isBottomBarVisible = true
                )
            } else {
                val updatedStack = if (current != null) {
                    (overlayBackStack.filterNot { it == target || it == current }) + current
                } else {
                    overlayBackStack.filterNot { it == target }
                }
                withActiveOverlay(destination = target, backStack = updatedStack)
            }
        } else {
            if (current == target) {
                val previous = overlayBackStack.lastOrNull()
                val remainingStack = if (previous != null) {
                    overlayBackStack.dropLast(1)
                } else {
                    emptyList()
                }
                withActiveOverlay(destination = previous, backStack = remainingStack)
            } else {
                val filteredStack = overlayBackStack.filterNot { it == target }
                when (target) {
                    BrowserOverlayDestination.SETTINGS -> copy(isSettingsScreenOpen = false, overlayBackStack = filteredStack)
                    BrowserOverlayDestination.PASSWORDS -> copy(isPasswordManagerOpen = false, overlayBackStack = filteredStack)
                    BrowserOverlayDestination.DOWNLOADS -> copy(isDownloadsScreenOpen = false, overlayBackStack = filteredStack)
                    BrowserOverlayDestination.HISTORY -> copy(isHistoryScreenOpen = false, overlayBackStack = filteredStack)
                    BrowserOverlayDestination.BOOKMARKS -> copy(isBookmarksScreenOpen = false, overlayBackStack = filteredStack)
                    BrowserOverlayDestination.TAB_SWITCHER -> copy(
                        isTabSwitcherOpen = false,
                        expandedGroupId = null,
                        overlayBackStack = filteredStack
                    )
                    BrowserOverlayDestination.AUTOMATIONS -> copy(
                        isAutomationWorkspaceOpen = false,
                        selectedAutomationWorkflowId = null,
                        overlayBackStack = filteredStack
                    )
                }
            }
        }
    }

    fun onBottomHomePressed() {
        val currentState = _uiState.value
        if (!currentState.activeTab.isHomePage) {
            handleOriginNavigationForRecording(currentState.activeTabId, "apex://home")
            navigationSynchronizer.beginNavigation(currentState.activeTabId, "apex://home")
        }
        webViewRegistry[currentState.activeTabId]?.setTag(-19048102, null)
        lastRecordedVisitByTab.remove(currentState.activeTabId)
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map { tab ->
                    if (tab.id == currentState.activeTabId && !tab.isHomePage) {
                        tab.copy(
                            url = "apex://home",
                            requestedUrl = "apex://home",
                            title = if (tab.isPrivate) "Incognito" else "Home",
                            isLoading = false,
                            progress = 0f,
                            canGoBack = false,
                            canGoForward = false,
                            isHttps = true,
                            wasUpgradedToHttps = false,
                            blockedCountOnPage = 0,
                            previewThumbnail = null
                        )
                    } else {
                        tab
                    }
                },
                isBookmarksScreenOpen = false,
                isHistoryScreenOpen = false,
                isDownloadsScreenOpen = false,
                isPasswordManagerOpen = false,
                isSettingsScreenOpen = false,
                isAutomationWorkspaceOpen = false,
                selectedAutomationWorkflowId = null,
                isAutomationHudOpen = false,
                overlayBackStack = emptyList(),
                isMenuSheetOpen = false,
                isFindInPageOpen = false,
                findInPageQuery = "",
                findInPageActiveMatchIndex = 0,
                findInPageTotalMatches = 0,
                isSearchScreenOpen = false,
                isTabSwitcherOpen = false,
                isAutomationOpen = false,
                isToolbarVisible = true,
                isBottomBarVisible = true,
                matchedCredentialsForActiveTab = emptyList(),
                pendingCredentialPrompt = null,
                pageInspection = PageInspectionState()
            )
        }
        resetBottomBarScrollTracking()
        webViewRegistry[currentState.activeTabId]?.clearMatches()
        pageInspectionByTab[currentState.activeTabId] = PageInspectionState()
        refreshAutomationToolbarStatus(clearTransientResult = true)
    }

    fun toggleBookmarkOnActiveTab() {
        val state = _uiState.value
        val active = state.activeTab
        if (active.isHomePage) {
            setBookmarksScreenOpen(true)
            return
        }
        viewModelScope.launch {
            val existing = repository.getBookmarkByUrl(active.url)
            if (existing != null) {
                repository.deleteBookmark(existing.id)
                _uiState.update {
                    it.copy(
                        isMenuSheetOpen = false,
                        snackbarNotice = "Removed bookmark '${active.title}'"
                    )
                }
            } else {
                repository.saveBookmark(url = active.url, title = active.title)
                _uiState.update {
                    it.copy(
                        isMenuSheetOpen = false,
                        snackbarNotice = "Bookmarked '${active.title}'"
                    )
                }
            }
        }
    }

    fun onBottomBookmarksPressed() {
        val state = _uiState.value
        if (state.isBookmarksScreenOpen) {
            setBookmarksScreenOpen(false)
            return
        }
        if (state.activeTab.isHomePage ||
            state.isSettingsScreenOpen ||
            state.isHistoryScreenOpen ||
            state.isDownloadsScreenOpen ||
            state.isPasswordManagerOpen ||
            state.isAutomationWorkspaceOpen ||
            state.isAutomationOpen ||
            state.isTabSwitcherOpen ||
            state.isSearchScreenOpen ||
            state.isMenuSheetOpen
        ) {
            setBookmarksScreenOpen(true)
        } else {
            val active = state.activeTab
            viewModelScope.launch {
                val existing = repository.getBookmarkByUrl(active.url)
                if (existing != null) {
                    repository.deleteBookmark(existing.id)
                    _uiState.update {
                        it.copy(
                            isTabSwitcherOpen = false,
                            isHistoryScreenOpen = false,
                            isDownloadsScreenOpen = false,
                            isPasswordManagerOpen = false,
                            isSettingsScreenOpen = false,
                            overlayBackStack = emptyList(),
                            isMenuSheetOpen = false,
                            isSearchScreenOpen = false,
                            snackbarNotice = "Removed bookmark '${active.title}'"
                        )
                    }
                } else {
                    repository.saveBookmark(url = active.url, title = active.title)
                    _uiState.update {
                        it.copy(
                            isTabSwitcherOpen = false,
                            isHistoryScreenOpen = false,
                            isDownloadsScreenOpen = false,
                            isPasswordManagerOpen = false,
                            isSettingsScreenOpen = false,
                            overlayBackStack = emptyList(),
                            isMenuSheetOpen = false,
                            isSearchScreenOpen = false,
                            snackbarNotice = "Bookmarked '${active.title}'"
                        )
                    }
                }
            }
        }
    }

    fun setBookmarksScreenOpen(open: Boolean) {
        _uiState.update { it.transitionOverlay(BrowserOverlayDestination.BOOKMARKS, open) }
    }

    fun setHistoryScreenOpen(open: Boolean) {
        _uiState.update { it.transitionOverlay(BrowserOverlayDestination.HISTORY, open) }
    }

    fun setDownloadsScreenOpen(open: Boolean) {
        _uiState.update { it.transitionOverlay(BrowserOverlayDestination.DOWNLOADS, open) }
    }

    fun setPasswordManagerOpen(open: Boolean) {
        _uiState.update { it.transitionOverlay(BrowserOverlayDestination.PASSWORDS, open) }
    }

    fun decryptPassword(entity: SavedPasswordEntity): String {
        return repository.decryptPassword(entity)
    }

    fun saveOrUpdatePassword(
        id: Long = 0L,
        siteName: String,
        siteUrl: String,
        username: String,
        plainPassword: String,
        notes: String = "",
        category: String = "Personal",
        isFavorite: Boolean = false
    ) {
        if (username.isBlank() || plainPassword.isEmpty()) return
        viewModelScope.launch {
            repository.saveOrUpdatePassword(
                id = id,
                siteName = siteName,
                siteUrl = siteUrl,
                username = username,
                plainPassword = plainPassword,
                notes = notes,
                category = category,
                isFavorite = isFavorite
            )
            refreshMatchedCredentialsForActiveTab()
            _uiState.update {
                it.copy(
                    snackbarNotice = if (id > 0L) "Password updated in vault" else "Password saved to encrypted vault"
                )
            }
        }
    }

    fun togglePasswordFavorite(id: Long) {
        viewModelScope.launch { repository.togglePasswordFavorite(id) }
    }

    fun deleteSavedPassword(id: Long) {
        viewModelScope.launch {
            repository.deleteSavedPassword(id)
            refreshMatchedCredentialsForActiveTab()
            _uiState.update { it.copy(snackbarNotice = "Password removed from vault") }
        }
    }

    fun clearAllSavedPasswords() {
        viewModelScope.launch {
            repository.clearAllSavedPasswords()
            _uiState.update {
                it.copy(
                    matchedCredentialsForActiveTab = emptyList(),
                    snackbarNotice = "Password vault cleared"
                )
            }
        }
    }

    fun importPasswordsFromCsv(rawCsv: String) {
        viewModelScope.launch {
            val count = repository.importPasswordsFromCsv(rawCsv)
            refreshMatchedCredentialsForActiveTab()
            _uiState.update {
                it.copy(
                    snackbarNotice = if (count > 0) {
                        "Imported $count password${if (count == 1) "" else "s"}"
                    } else {
                        "No valid credentials found in CSV"
                    }
                )
            }
        }
    }

    fun exportPasswordsToCsv(onExportReady: (String) -> Unit) {
        viewModelScope.launch {
            val csv = repository.exportPasswordsToCsv()
            onExportReady(csv)
        }
    }

    fun togglePasswordAutofill(enabled: Boolean) {
        viewModelScope.launch {
            repository.setPasswordAutofillEnabled(enabled)
            refreshMatchedCredentialsForActiveTab()
        }
    }

    fun refreshMatchedCredentialsForActiveTab() {
        val state = _uiState.value
        val active = state.activeTab
        if (active.isHomePage || !preferencesState.value.passwordAutofillEnabled) {
            _uiState.update { it.copy(matchedCredentialsForActiveTab = emptyList()) }
            return
        }
        viewModelScope.launch {
            val matches = repository.getPasswordsForUrl(active.url)
            if (_uiState.value.activeTabId == active.id) {
                _uiState.update { it.copy(matchedCredentialsForActiveTab = matches) }
            }
        }
    }

    fun onWebCredentialSubmitted(
        pageUrl: String,
        pageTitle: String,
        username: String,
        password: String
    ) {
        val state = _uiState.value
        val active = state.activeTab
        if (active.isPrivate) return

        // Capture submitted credential values into volatile recording memory when actively recording
        val rec = state.recordingSession
        if (rec.status == RecordingStatus.RECORDING && active.id == rec.initiatingTabId) {
            if (password.isNotEmpty()) {
                val pwdStep = rec.steps.lastOrNull { step ->
                    step.isCredentialStep ||
                        step.credentialFieldRole == CredentialFieldRole.PASSWORD ||
                        AutomationVaultManager.detectCategory(
                            targetLabel = step.targetLabel,
                            selector = step.selector,
                            isCredentialStep = step.isCredentialStep,
                            credentialFieldRole = step.credentialFieldRole
                        ) == AutomationVaultCategory.PASSWORD
                }
                if (pwdStep != null) {
                    recordedSecretsByStepId[pwdStep.id] = password
                }
            }
            val cleanUserRec = username.trim()
            if (cleanUserRec.isNotEmpty()) {
                val userStep = rec.steps.lastOrNull { step ->
                    step.actionType == AutomationActionType.INPUT &&
                        !step.isCredentialStep &&
                        (step.value.isBlank() || step.value == cleanUserRec) &&
                        AutomationVaultManager.detectCategory(
                            targetLabel = step.targetLabel,
                            selector = step.selector,
                            value = step.value
                        ) in setOf(
                            AutomationVaultCategory.EMAIL,
                            AutomationVaultCategory.USERNAME,
                            AutomationVaultCategory.PHONE
                        )
                }
                if (userStep != null) {
                    recordedSecretsByStepId[userStep.id] = cleanUserRec
                }
            }
        }

        if (!preferencesState.value.passwordAutofillEnabled) return
        val cleanUser = username.trim()
        if (cleanUser.isBlank() || password.isBlank()) return
        val host = PasswordVaultUtil.extractHost(pageUrl)
        if (host.isBlank()) return

        viewModelScope.launch {
            val existing = repository.getPasswordsForUrl(pageUrl)
                .firstOrNull { it.username.equals(cleanUser, ignoreCase = true) }
            if (existing != null && repository.decryptPassword(existing) == password) {
                return@launch
            }
            val siteName = PasswordVaultUtil.deriveSiteName(pageUrl, pageTitle)
            _uiState.update {
                it.copy(
                    pendingCredentialPrompt = PendingCredentialPrompt(
                        siteName = siteName,
                        siteUrl = pageUrl,
                        username = cleanUser,
                        password = password,
                        existingId = existing?.id
                    )
                )
            }
        }
    }

    fun confirmSavePendingCredential() {
        val pending = _uiState.value.pendingCredentialPrompt ?: return
        _uiState.update { it.copy(pendingCredentialPrompt = null) }
        saveOrUpdatePassword(
            id = pending.existingId ?: 0L,
            siteName = pending.siteName,
            siteUrl = pending.siteUrl,
            username = pending.username,
            plainPassword = pending.password
        )
    }

    fun dismissPendingCredentialPrompt() {
        _uiState.update { it.copy(pendingCredentialPrompt = null) }
    }

    fun fillCredentialIntoActiveWebView(entity: SavedPasswordEntity) {
        val active = _uiState.value.activeTab
        val wv = webViewRegistry[active.id] ?: return
        val plainPassword = repository.decryptPassword(entity)
        val userLiteral = JSONObject.quote(entity.username)
        val passLiteral = JSONObject.quote(plainPassword)
        val js = """
            (function() {
                var userVal = $userLiteral;
                var passVal = $passLiteral;
                function setNativeValue(el, value) {
                    if (!el) return;
                    var proto = Object.getPrototypeOf(el);
                    var desc = Object.getOwnPropertyDescriptor(proto, 'value') ||
                        Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value');
                    if (desc && desc.set) {
                        desc.set.call(el, value);
                    } else {
                        el.value = value;
                    }
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                    el.dispatchEvent(new Event('change', { bubbles: true }));
                }
                var passInput = document.querySelector('input[type="password"]:not([disabled])');
                if (passInput) {
                    setNativeValue(passInput, passVal);
                    var form = passInput.form || document;
                    var candidates = Array.prototype.slice.call(
                        form.querySelectorAll('input[type="email"], input[type="text"], input[name*="user" i], input[name*="mail" i], input[name*="login" i], input:not([type])')
                    );
                    var userInput = candidates.find(function(inp) {
                        return inp !== passInput && !inp.disabled && inp.type !== 'hidden' && inp.offsetParent !== null;
                    }) || candidates[0];
                    if (userInput) {
                        setNativeValue(userInput, userVal);
                    }
                }
            })();
        """.trimIndent()
        wv.evaluateJavascript(js, null)
        viewModelScope.launch { repository.markPasswordUsed(entity.id) }
        _uiState.update {
            it.copy(snackbarNotice = "Filled credentials for ${entity.siteName}")
        }
    }

    fun startFastDownload(
        url: String,
        suggestedFileName: String? = null,
        contentDisposition: String? = null,
        mimeType: String? = null,
        contentLength: Long = -1L,
        userAgent: String? = null,
        bypassAskPrompt: Boolean = false,
        locationModeOverride: DownloadLocationMode? = null,
        customTreeUriOverride: String? = null
    ) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return
        viewModelScope.launch {
            val prefs = runCatching { repository.preferencesFlow.first() }
                .getOrElse { preferencesState.value }
            if (!bypassAskPrompt && prefs.askWhereToSaveFiles) {
                val resolvedMime = FastDownloadEngine.resolveMimeType(cleanUrl, mimeType)
                val guessedName = suggestedFileName?.takeIf { it.isNotBlank() }
                    ?: runCatching {
                        android.webkit.URLUtil.guessFileName(cleanUrl, contentDisposition, resolvedMime)
                    }.getOrNull()
                    ?: cleanUrl.substringAfterLast('/').substringBefore('?').ifBlank { "download.bin" }
                _uiState.update {
                    it.copy(
                        pendingDownloadPrompt = PendingDownloadPrompt(
                            url = cleanUrl,
                            suggestedFileName = FastDownloadEngine.sanitizeFileName(guessedName),
                            contentDisposition = contentDisposition,
                            mimeType = resolvedMime,
                            contentLength = contentLength,
                            userAgent = userAgent
                        )
                    )
                }
                return@launch
            }

            val engine = fastDownloadEngine
            if (engine != null) {
                engine.updateDownloadSettings(prefs)
                engine.enqueueDownload(
                    url = cleanUrl,
                    suggestedFileName = suggestedFileName,
                    contentDisposition = contentDisposition,
                    mimeType = mimeType,
                    contentLength = contentLength,
                    userAgent = userAgent,
                    locationModeOverride = locationModeOverride,
                    customTreeUriOverride = customTreeUriOverride
                ) { entity ->
                    val turboSuffix = if (entity.chunksCount > 1) " (${entity.chunksCount}× Turbo)" else ""
                    _uiState.update {
                        it.copy(snackbarNotice = "Downloading ${entity.fileName}$turboSuffix…")
                    }
                }
            } else {
                val rawName = suggestedFileName?.takeIf { it.isNotBlank() }
                    ?: cleanUrl.substringAfterLast('/').substringBefore('?').ifBlank { "download.bin" }
                val resolvedName = FastDownloadEngine.sanitizeFileName(rawName)
                val resolvedMime = FastDownloadEngine.resolveMimeType(cleanUrl, mimeType)
                val category = FastDownloadEngine.inferCategory(resolvedName, resolvedMime)
                val now = System.currentTimeMillis()
                val completed = DownloadItemEntity(
                    fileName = resolvedName,
                    url = cleanUrl,
                    mimeType = resolvedMime,
                    category = category,
                    totalBytes = if (contentLength > 0) contentLength else 128_000L,
                    downloadedBytes = if (contentLength > 0) contentLength else 128_000L,
                    speedBytesPerSec = 8_400_000L,
                    chunksCount = if (prefs.parallelDownloadingEnabled) FastDownloadEngine.DEFAULT_TURBO_CHUNKS else 1,
                    status = FastDownloadEngine.STATUS_COMPLETED,
                    createdAt = now,
                    completedAt = now
                )
                val insertedId = repository.upsertDownload(completed)
                val savedCompleted = completed.copy(id = insertedId)
                _uiState.update {
                    it.copy(
                        snackbarNotice = "Downloaded $resolvedName",
                        pendingAutoOpenDownload = if (prefs.autoOpenDownloadedFiles) {
                            savedCompleted
                        } else {
                            it.pendingAutoOpenDownload
                        }
                    )
                }
            }
        }
    }

    fun confirmPendingDownload(
        fileName: String,
        locationMode: DownloadLocationMode,
        customTreeUri: String,
        customFolderLabel: String,
        keepAskingEveryTime: Boolean
    ) {
        val pending = _uiState.value.pendingDownloadPrompt ?: return
        _uiState.update { it.copy(pendingDownloadPrompt = null) }
        viewModelScope.launch {
            repository.setDownloadLocation(
                mode = locationMode,
                customTreeUri = customTreeUri,
                customFolderLabel = customFolderLabel
            )
            if (!keepAskingEveryTime) {
                repository.setAskWhereToSaveFiles(false)
            }
            startFastDownload(
                url = pending.url,
                suggestedFileName = fileName.trim().ifBlank { pending.suggestedFileName },
                contentDisposition = pending.contentDisposition,
                mimeType = pending.mimeType,
                contentLength = pending.contentLength,
                userAgent = pending.userAgent,
                bypassAskPrompt = true,
                locationModeOverride = locationMode,
                customTreeUriOverride = customTreeUri
            )
        }
    }

    fun dismissPendingDownload() {
        _uiState.update { it.copy(pendingDownloadPrompt = null) }
    }

    fun consumePendingAutoOpenDownload() {
        _uiState.update { it.copy(pendingAutoOpenDownload = null) }
    }

    fun selectDownloadLocation(
        mode: DownloadLocationMode,
        customTreeUri: String = "",
        customFolderLabel: String = ""
    ) {
        viewModelScope.launch {
            repository.setDownloadLocation(mode, customTreeUri, customFolderLabel)
            val updated = preferencesState.value.copy(
                downloadLocationMode = mode,
                customDownloadTreeUri = customTreeUri.ifBlank { preferencesState.value.customDownloadTreeUri },
                customDownloadFolderLabel = customFolderLabel.ifBlank { preferencesState.value.customDownloadFolderLabel }
            )
            fastDownloadEngine?.updateDownloadSettings(updated)
        }
    }

    fun toggleAskWhereToSaveFiles(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAskWhereToSaveFiles(enabled)
            fastDownloadEngine?.updateDownloadSettings(
                preferencesState.value.copy(askWhereToSaveFiles = enabled)
            )
        }
    }

    fun toggleAutoOpenDownloadedFiles(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoOpenDownloadedFiles(enabled)
            fastDownloadEngine?.updateDownloadSettings(
                preferencesState.value.copy(autoOpenDownloadedFiles = enabled)
            )
        }
    }

    fun toggleShowDownloadProgressNotifications(enabled: Boolean) {
        viewModelScope.launch {
            repository.setShowDownloadProgressNotifications(enabled)
            fastDownloadEngine?.updateDownloadSettings(
                preferencesState.value.copy(showDownloadProgressNotifications = enabled)
            )
        }
    }

    fun toggleParallelDownloading(enabled: Boolean) {
        viewModelScope.launch {
            repository.setParallelDownloadingEnabled(enabled)
            fastDownloadEngine?.updateDownloadSettings(
                preferencesState.value.copy(parallelDownloadingEnabled = enabled)
            )
        }
    }

    fun pauseDownload(id: Long) {
        fastDownloadEngine?.pauseDownload(id)
    }

    fun resumeDownload(id: Long) {
        fastDownloadEngine?.resumeDownload(id)
    }

    fun retryDownload(id: Long) {
        fastDownloadEngine?.retryDownload(id)
    }

    fun cancelDownload(id: Long) {
        fastDownloadEngine?.cancelDownload(id)
    }

    fun deleteDownload(id: Long) {
        val engine = fastDownloadEngine
        if (engine != null) {
            engine.deleteDownload(id)
        } else {
            viewModelScope.launch { repository.deleteDownloadById(id) }
        }
        _uiState.update { it.copy(snackbarNotice = "Removed download item") }
    }

    fun clearCompletedDownloads() {
        viewModelScope.launch {
            repository.clearCompletedDownloads()
            _uiState.update { it.copy(snackbarNotice = "Cleared completed downloads") }
        }
    }

    fun clearAllDownloads() {
        val engine = fastDownloadEngine
        if (engine != null) {
            engine.clearAllDownloads()
            _uiState.update { it.copy(snackbarNotice = "Cleared all downloads") }
        } else {
            viewModelScope.launch {
                repository.clearAllDownloads()
                _uiState.update { it.copy(snackbarNotice = "Cleared all downloads") }
            }
        }
    }

    fun deleteBrowsingHistoryItem(id: Long) {
        viewModelScope.launch {
            repository.deleteBrowsingHistoryItem(id)
            _uiState.update { it.copy(snackbarNotice = "Removed visit from history") }
        }
    }

    fun clearBrowsingHistoryRange(sinceMillis: Long?) {
        viewModelScope.launch {
            if (sinceMillis == null) {
                repository.clearAllBrowsingHistory()
                _uiState.update { it.copy(snackbarNotice = "Cleared all browsing history") }
            } else {
                repository.clearBrowsingHistorySince(sinceMillis)
                _uiState.update { it.copy(snackbarNotice = "Cleared selected browsing history range") }
            }
        }
    }

    fun setSettingsScreenOpen(
        open: Boolean,
        initialSection: SettingsInitialSection = SettingsInitialSection.MAIN
    ) {
        _uiState.update {
            it.transitionOverlay(BrowserOverlayDestination.SETTINGS, open).copy(
                settingsInitialSection = if (open) initialSection else SettingsInitialSection.MAIN
            )
        }
    }

    fun openAppearanceSettings() {
        setSettingsScreenOpen(open = true, initialSection = SettingsInitialSection.APPEARANCE)
    }

    fun dismissDefaultBrowserPromo() {
        _uiState.update { it.copy(isDefaultBrowserPromoDismissed = true) }
    }

    fun setMenuSheetOpen(open: Boolean) {
        if (open) resetBottomBarScrollTracking()
        _uiState.update {
            it.copy(
                isMenuSheetOpen = open,
                isBottomBarVisible = if (open) true else it.isBottomBarVisible
            )
        }
    }

    fun toggleMenuSheet() {
        val nextOpen = !_uiState.value.isMenuSheetOpen
        if (nextOpen) resetBottomBarScrollTracking()
        _uiState.update {
            it.copy(
                isMenuSheetOpen = nextOpen,
                isBottomBarVisible = if (nextOpen) true else it.isBottomBarVisible
            )
        }
    }

    fun setSyncBackupDialogOpen(open: Boolean) {
        setSubscriptionDialogOpen(open = open, reason = null)
    }

    fun setSubscriptionDialogOpen(open: Boolean, reason: String? = null) {
        _uiState.update {
            it.copy(
                isSyncBackupDialogOpen = open,
                subscriptionPromptReason = if (open) reason else null,
                proActivationError = if (open) it.proActivationError else null
            )
        }
    }

    fun clearProActivationError() {
        _uiState.update { it.copy(proActivationError = null) }
    }

    fun activateProSubscription(
        rawKey: String,
        selectedPackage: ProSubscriptionPackage = ProSubscriptionPackage.MONTH
    ) {
        val trimmed = rawKey.trim()
        if (trimmed.isEmpty()) {
            _uiState.update {
                it.copy(
                    proActivationError = "Please paste or enter an activation key from the author."
                )
            }
            return
        }
        viewModelScope.launch {
            val effectivePackage = BrowserPreferences.resolvePackageForKey(trimmed, selectedPackage)
            val activated = repository.activateProSubscription(trimmed, selectedPackage)
            if (activated) {
                val resolvedPkg = effectivePackage ?: selectedPackage
                _uiState.update {
                    it.copy(
                        proActivationError = null,
                        subscriptionPromptReason = null,
                        snackbarNotice = "Apex Pro (${resolvedPkg.displayName}) activated for ${resolvedPkg.billingUnitLabel.lowercase()}!"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        proActivationError = "Invalid activation key. Contact the author to get a valid key."
                    )
                }
            }
        }
    }

    fun deactivateProSubscription() {
        viewModelScope.launch {
            repository.deactivateProSubscription()
            _uiState.update {
                it.copy(
                    proActivationError = null,
                    snackbarNotice = "Switched to Free plan (5 daily automation plays)."
                )
            }
        }
    }

    private suspend fun ensureAutomationPlayQuotaOrPrompt(): Boolean {
        val prefs = repository.preferencesFlow.first()
        if (prefs.canPlayAutomationToday()) return true
        val reason = if (prefs.isProExpired()) {
            "Your ${prefs.proPackage.displayName} Apex Pro pass has expired. Select a Month, Week, or Day package and activate a new key to continue unlimited automation."
        } else {
            "Daily free limit reached (${BrowserPreferences.FREE_DAILY_AUTOMATION_PLAY_LIMIT}/${BrowserPreferences.FREE_DAILY_AUTOMATION_PLAY_LIMIT} plays used today). Select a package and activate a key to unlock Apex Pro."
        }
        _uiState.update {
            it.copy(
                isSyncBackupDialogOpen = true,
                subscriptionPromptReason = reason,
                proActivationError = null,
                snackbarNotice = "Daily 5-play limit reached — Activate Apex Pro to continue"
            )
        }
        return false
    }

    fun openFindInPage() {
        val active = _uiState.value.activeTab
        if (active.isHomePage) {
            _uiState.update {
                it.copy(
                    isMenuSheetOpen = false,
                    snackbarNotice = "Open a webpage to use Find in page"
                )
            }
            return
        }
        resetBottomBarScrollTracking()
        _uiState.update {
            it.copy(
                isMenuSheetOpen = false,
                isFindInPageOpen = true,
                isBottomBarVisible = true
            )
        }
        val currentQuery = _uiState.value.findInPageQuery
        if (currentQuery.isNotBlank()) {
            webViewRegistry[active.id]?.findAllAsync(currentQuery)
        }
    }

    fun updateFindInPageQuery(query: String) {
        val active = _uiState.value.activeTab
        _uiState.update {
            it.copy(
                findInPageQuery = query,
                findInPageActiveMatchIndex = if (query.isEmpty()) 0 else it.findInPageActiveMatchIndex,
                findInPageTotalMatches = if (query.isEmpty()) 0 else it.findInPageTotalMatches
            )
        }
        val wv = webViewRegistry[active.id]
        if (query.isEmpty()) {
            wv?.clearMatches()
        } else {
            wv?.findAllAsync(query)
        }
    }

    fun findNextInPage(forward: Boolean) {
        val state = _uiState.value
        if (state.findInPageQuery.isEmpty()) return
        webViewRegistry[state.activeTabId]?.findNext(forward)
    }

    fun onWebViewFindResults(tabId: String, activeOrdinal: Int, totalMatches: Int) {
        if (_uiState.value.activeTabId != tabId) return
        _uiState.update {
            it.copy(
                findInPageActiveMatchIndex = if (totalMatches > 0) (activeOrdinal + 1).coerceIn(1, totalMatches) else 0,
                findInPageTotalMatches = totalMatches.coerceAtLeast(0)
            )
        }
    }

    fun closeFindInPage() {
        val activeId = _uiState.value.activeTabId
        webViewRegistry[activeId]?.clearMatches()
        _uiState.update {
            it.copy(
                isFindInPageOpen = false,
                findInPageQuery = "",
                findInPageActiveMatchIndex = 0,
                findInPageTotalMatches = 0
            )
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch { repository.deleteBookmark(id) }
    }

    fun importBookmarksFromText(rawContent: String) {
        viewModelScope.launch {
            val importedCount = repository.importBookmarksFromRawContent(rawContent)
            val message = if (importedCount > 0) {
                "Imported $importedCount bookmark${if (importedCount == 1) "" else "s"}"
            } else {
                "No valid bookmarks found to import"
            }
            _uiState.update { it.copy(snackbarNotice = message) }
        }
    }

    fun notifyBookmarksExported(count: Int, formatLabel: String) {
        _uiState.update {
            it.copy(
                snackbarNotice = "Exported $count bookmark${if (count == 1) "" else "s"} ($formatLabel)"
            )
        }
    }

    fun addTopVisit(title: String, url: String) {
        viewModelScope.launch {
            repository.addTopVisit(title, url)
            _uiState.update { it.copy(snackbarNotice = "Added to Top Visits") }
        }
    }

    fun deleteTopVisit(id: Long) {
        viewModelScope.launch {
            repository.deleteTopVisit(id)
            _uiState.update { it.copy(snackbarNotice = "Removed from Top Visits") }
        }
    }

    fun setShowTopVisitsOnHome(enabled: Boolean) {
        viewModelScope.launch {
            repository.setShowTopVisitsOnHome(enabled)
        }
    }

    fun reloadActiveTab() {
        val active = _uiState.value.activeTab
        if (active.isHomePage) return
        val builtIn = com.example.engine.BuiltInShowcasePages.resolveHtml(active.url)
        val wv = webViewRegistry[active.id]
        navigationSynchronizer.beginNavigation(active.id, active.url)
        resetBottomBarScrollTracking()
        _uiState.update { state ->
            state.copy(
                isBottomBarVisible = true,
                tabs = state.tabs.map { tab ->
                    if (tab.id == active.id) {
                        tab.copy(
                            isLoading = true,
                            progress = 0.08f,
                            loadRequestId = if (wv == null) tab.loadRequestId + 1L else tab.loadRequestId
                        )
                    } else {
                        tab
                    }
                }
            )
        }
        if (wv != null) {
            if (builtIn != null) {
                wv.loadDataWithBaseURL(builtIn.first, builtIn.second, "text/html", "UTF-8", null)
            } else {
                wv.reload()
            }
        }
    }

    private fun isBitmapBlank(bitmap: Bitmap): Boolean {
        if (bitmap.width <= 0 || bitmap.height <= 0) return true
        val firstPixel = bitmap.getPixel(0, 0)
        val gridCount = 12
        val stepX = (bitmap.width / gridCount).coerceAtLeast(1)
        val stepY = (bitmap.height / gridCount).coerceAtLeast(1)
        var x = stepX / 3
        while (x < bitmap.width) {
            var y = stepY / 3
            while (y < bitmap.height) {
                if (bitmap.getPixel(x, y) != firstPixel) {
                    return false
                }
                y += stepY
            }
            x += stepX
        }
        return true
    }

    fun updateTabThumbnail(tabId: String, bitmap: Bitmap) {
        val currentTab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
        if (currentTab.isPrivate || currentTab.isHomePage) return
        if (isBitmapBlank(bitmap)) {
            bitmap.recycle()
            return
        }
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map { tab ->
                    if (tab.id == tabId && !tab.isPrivate && !tab.isHomePage) {
                        tab.copy(previewThumbnail = bitmap)
                    } else {
                        tab
                    }
                }
            )
        }
    }

    fun captureTabThumbnail(tabId: String): Bitmap? {
        val tab = _uiState.value.tabs.firstOrNull { it.id == tabId }
        if (tab == null || tab.isPrivate || tab.isHomePage) return null
        val wv = webViewRegistry[tabId] ?: return null
        return runCatching {
            if (wv.width > 0 && wv.height > 0 && wv.isAttachedToWindow && wv.windowToken != null) {
                val cropHeight = minOf(wv.height, (wv.width * 1.28f).toInt().coerceAtLeast(1))
                val thumbWidth = 360
                val thumbHeight = (cropHeight.toFloat() / wv.width.toFloat() * thumbWidth)
                    .toInt()
                    .coerceIn(240, 520)
                val thumbBitmap = Bitmap.createBitmap(thumbWidth, thumbHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(thumbBitmap)
                val bgColor = (wv.background as? android.graphics.drawable.ColorDrawable)?.color
                    ?: android.graphics.Color.WHITE
                canvas.drawColor(bgColor)
                val scale = thumbWidth.toFloat() / wv.width.toFloat()
                canvas.scale(scale, scale)
                wv.draw(canvas)
                if (isBitmapBlank(thumbBitmap)) {
                    thumbBitmap.recycle()
                    null
                } else {
                    thumbBitmap
                }
            } else null
        }.getOrNull()
    }

    private fun snapshotOutgoingTabThumbnail(tabId: String) {
        val captured = captureTabThumbnail(tabId) ?: return
        updateTabThumbnail(tabId, captured)
    }

    fun setTabSwitcherOpen(open: Boolean) {
        if (open) {
            val currentTabId = _uiState.value.activeTabId
            val freshThumb = captureTabThumbnail(currentTabId)
            _uiState.update { state ->
                state.copy(
                    tabs = state.tabs.map { tab ->
                        if (tab.id == currentTabId) {
                            tab.copy(
                                previewThumbnail = freshThumb ?: tab.previewThumbnail,
                                lastActiveTimeMillis = System.currentTimeMillis()
                            )
                        } else tab
                    }
                )
            }
        }
        _uiState.update { it.transitionOverlay(BrowserOverlayDestination.TAB_SWITCHER, open) }
    }

    fun setAutomationOpen(open: Boolean, module: ExtensionModule = _uiState.value.activeExtension) {
        _uiState.update {
            it.copy(
                isAutomationOpen = open,
                isAutomationHudOpen = if (open) false else it.isAutomationHudOpen,
                activeExtension = module,
                isSettingsScreenOpen = if (open) false else it.isSettingsScreenOpen,
                isSearchScreenOpen = if (open) false else it.isSearchScreenOpen,
                isToolbarVisible = true
            )
        }
    }

    fun selectExtensionModule(module: ExtensionModule) {
        _uiState.update { it.copy(activeExtension = module) }
    }

    // --- Apex Automation HUD, Recording, Instructions, Workspace & Details ---

    fun openAutomationHud(initialMode: AutomationHudMode? = null) {
        val state = _uiState.value
        val activeTab = state.activeTab
        val resolvedMode = initialMode ?: when {
            state.recordingSession.status == RecordingStatus.RECORDING ||
                state.recordingSession.status == RecordingStatus.PAUSED ||
                state.recordingSession.status == RecordingStatus.STOPPED -> AutomationHudMode.RECORDING
            else -> AutomationHudMode.OVERVIEW
        }
        val currentInspection = state.pageInspection.copy(
            domain = automationEngine.extractDomain(activeTab.url),
            url = activeTab.url
        )
        _uiState.update {
            it.copy(
                isAutomationHudOpen = true,
                automationHudMode = resolvedMode,
                isMenuSheetOpen = false,
                pageInspection = currentInspection
            )
        }
        if (!activeTab.isHomePage && currentInspection.elements.isEmpty()) {
            scanActivePageElements(silent = true)
        }
    }

    fun dismissAutomationHud() {
        _uiState.update { it.copy(isAutomationHudOpen = false) }
    }

    fun setAutomationHudMode(mode: AutomationHudMode) {
        val currentSession = _uiState.value.recordingSession
        if (mode == AutomationHudMode.RECORDING &&
            (currentSession.status == RecordingStatus.IDLE ||
                currentSession.status == RecordingStatus.CANCELLED ||
                currentSession.status == RecordingStatus.ERROR)
        ) {
            startRecording()
            return
        }
        _uiState.update { it.copy(automationHudMode = mode) }
        if (mode == AutomationHudMode.ELEMENTS &&
            !_uiState.value.activeTab.isHomePage &&
            _uiState.value.pageInspection.elements.isEmpty()
        ) {
            scanActivePageElements(silent = false)
        }
    }

    fun setAutomationWorkspaceOpen(open: Boolean) {
        _uiState.update {
            val transitioned = it.transitionOverlay(BrowserOverlayDestination.AUTOMATIONS, open)
            if (open) {
                transitioned.copy(isAutomationHudOpen = false)
            } else {
                transitioned.copy(selectedAutomationWorkflowId = null)
            }
        }
    }

    fun openAutomationDetails(workflowId: Long) {
        _uiState.update { state ->
            val withWorkspace = if (state.isAutomationWorkspaceOpen) {
                state
            } else {
                state.transitionOverlay(BrowserOverlayDestination.AUTOMATIONS, true)
            }
            withWorkspace.copy(
                isAutomationHudOpen = false,
                selectedAutomationWorkflowId = workflowId,
                activeDiffReport = null
            )
        }
        validateWorkflow(workflowId)
        loadWorkflowVersions(workflowId)
    }

    fun closeAutomationDetails() {
        _uiState.update {
            it.copy(
                selectedAutomationWorkflowId = null,
                activeDryRunReport = null,
                activeDiffReport = null
            )
        }
    }

    // --- Automation Vault (Encrypted Values for Repeatable Replays) ---

    fun setAutomationVaultOpen(open: Boolean, initialFolderId: String? = null) {
        _uiState.update { state ->
            if (open) {
                reopenHudAfterVaultClose = state.isAutomationHudOpen && !state.isAutomationWorkspaceOpen
                state.copy(
                    isAutomationVaultOpen = true,
                    isAutomationHudOpen = false,
                    initialAutomationVaultFolderId = initialFolderId
                )
            } else {
                val shouldReopenHud = reopenHudAfterVaultClose && !state.isAutomationWorkspaceOpen
                reopenHudAfterVaultClose = false
                state.copy(
                    isAutomationVaultOpen = false,
                    isAutomationHudOpen = state.isAutomationHudOpen || shouldReopenHud,
                    automationVaultRevealedValues = emptyMap(),
                    initialAutomationVaultFolderId = null
                )
            }
        }
        if (open) {
            revealAllAutomationVaultEntries()
        }
    }

    fun revealAllAutomationVaultEntries() {
        viewModelScope.launch {
            val entries = repository.automationVaultManager.getAllEntriesOnce()
            val resolved = mutableMapOf<String, String>()
            for (entry in entries) {
                if (entry.isSensitive) {
                    repository.automationVaultManager.decryptEntryValue(entry.entryId, markUsed = false)
                        .onSuccess { plain -> resolved[entry.entryId] = plain }
                }
            }
            if (resolved.isNotEmpty()) {
                _uiState.update { state ->
                    state.copy(
                        automationVaultRevealedValues = state.automationVaultRevealedValues + resolved
                    )
                }
            }
        }
    }

    fun saveAutomationVaultEntry(
        entryId: String? = null,
        category: AutomationVaultCategory,
        label: String,
        value: String,
        websiteOrigin: String = "",
        isSensitive: Boolean = category.isSensitiveDefault,
        folderId: String = ""
    ) {
        viewModelScope.launch {
            repository.automationVaultManager.saveOrUpdateEntry(
                entryId = entryId,
                category = category,
                label = label,
                plainValue = value,
                websiteOrigin = websiteOrigin,
                isSensitive = isSensitive,
                folderId = folderId
            ).onSuccess { saved ->
                _uiState.update { state ->
                    state.copy(
                        automationVaultRevealedValues = state.automationVaultRevealedValues + (saved.entryId to value),
                        snackbarNotice = "Saved '${saved.label}' to Automation Vault"
                    )
                }
            }.onFailure { ex ->
                _uiState.update {
                    it.copy(snackbarNotice = ex.message ?: "Unable to save Automation Vault value")
                }
            }
        }
    }

    fun importAutomationVaultCandidates(
        candidates: List<VaultFileImportCandidate>,
        targetFolderId: String = ""
    ) {
        viewModelScope.launch {
            repository.automationVaultManager.importSelectedCandidates(
                candidates = candidates,
                targetFolderId = targetFolderId
            )
                .onSuccess { savedEntries ->
                    _uiState.update {
                        it.copy(
                            snackbarNotice = "Saved ${savedEntries.size} selected value${if (savedEntries.size == 1) "" else "s"} from file to Automation Vault"
                        )
                    }
                    revealAllAutomationVaultEntries()
                }
                .onFailure { ex ->
                    _uiState.update {
                        it.copy(snackbarNotice = ex.message ?: "Unable to import selected file values")
                    }
                }
        }
    }

    fun createAutomationVaultFolder(name: String) {
        viewModelScope.launch {
            repository.automationVaultManager.createFolder(name)
                .onSuccess { folder ->
                    _uiState.update {
                        it.copy(snackbarNotice = "Created folder '${folder.name}'")
                    }
                }
                .onFailure { ex ->
                    _uiState.update {
                        it.copy(snackbarNotice = ex.message ?: "Unable to create folder")
                    }
                }
        }
    }

    fun renameAutomationVaultFolder(folderId: String, newName: String) {
        viewModelScope.launch {
            repository.automationVaultManager.renameFolder(folderId, newName)
                .onSuccess { folder ->
                    _uiState.update {
                        it.copy(snackbarNotice = "Renamed folder to '${folder.name}'")
                    }
                }
                .onFailure { ex ->
                    _uiState.update {
                        it.copy(snackbarNotice = ex.message ?: "Unable to rename folder")
                    }
                }
        }
    }

    fun deleteAutomationVaultFolder(folderId: String) {
        viewModelScope.launch {
            repository.automationVaultManager.deleteFolder(folderId)
                .onSuccess {
                    _uiState.update {
                        it.copy(snackbarNotice = "Deleted folder and its contents")
                    }
                }
                .onFailure { ex ->
                    _uiState.update {
                        it.copy(snackbarNotice = ex.message ?: "Unable to delete folder")
                    }
                }
        }
    }

    fun uploadFileToAutomationVaultFolder(
        folderId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        filesRootDir: File
    ) {
        viewModelScope.launch {
            repository.automationVaultManager.saveUploadedFileToFolder(
                folderId = folderId,
                fileName = fileName,
                mimeType = mimeType,
                bytes = bytes,
                filesRootDir = filesRootDir
            ).onSuccess { savedFile ->
                _uiState.update {
                    it.copy(snackbarNotice = "Uploaded '${savedFile.fileName}' to folder")
                }
            }.onFailure { ex ->
                _uiState.update {
                    it.copy(snackbarNotice = ex.message ?: "Unable to upload file to folder")
                }
            }
        }
    }

    fun uploadFolderToAutomationVault(
        folderName: String,
        files: List<com.example.automation.VaultFolderFilePayload>,
        filesRootDir: File
    ) {
        viewModelScope.launch {
            repository.automationVaultManager.uploadDirectoryFolder(
                folderName = folderName,
                files = files,
                filesRootDir = filesRootDir
            ).onSuccess { result ->
                val count = result.uploadedFiles.size
                _uiState.update {
                    it.copy(
                        snackbarNotice = "Uploaded folder '${result.folder.name}' ($count file${if (count == 1) "" else "s"})"
                    )
                }
            }.onFailure { ex ->
                _uiState.update {
                    it.copy(snackbarNotice = ex.message ?: "Unable to upload folder")
                }
            }
        }
    }

    fun deleteAutomationVaultFile(fileId: String) {
        viewModelScope.launch {
            repository.automationVaultManager.deleteUploadedFile(fileId)
                .onSuccess {
                    _uiState.update {
                        it.copy(snackbarNotice = "Removed file from folder")
                    }
                }
                .onFailure { ex ->
                    _uiState.update {
                        it.copy(snackbarNotice = ex.message ?: "Unable to remove file")
                    }
                }
        }
    }

    fun deleteAutomationVaultEntry(entryId: String) {
        viewModelScope.launch {
            repository.automationVaultManager.deleteEntry(entryId)
            _uiState.update { state ->
                state.copy(
                    automationVaultRevealedValues = state.automationVaultRevealedValues - entryId,
                    snackbarNotice = "Removed value from Automation Vault"
                )
            }
        }
    }

    fun revealAutomationVaultEntry(entryId: String) {
        viewModelScope.launch {
            repository.automationVaultManager.decryptEntryValue(entryId, markUsed = false)
                .onSuccess { plain ->
                    _uiState.update { state ->
                        state.copy(
                            automationVaultRevealedValues = state.automationVaultRevealedValues + (entryId to plain)
                        )
                    }
                }
                .onFailure { ex ->
                    _uiState.update {
                        it.copy(snackbarNotice = ex.message ?: "Unable to reveal vault value")
                    }
                }
        }
    }

    fun hideAutomationVaultEntry(entryId: String) {
        _uiState.update { state ->
            state.copy(automationVaultRevealedValues = state.automationVaultRevealedValues - entryId)
        }
    }

    private fun buildMergedRecordingCandidates(
        steps: List<AutomationStep>,
        targetUrl: String,
        previousCandidates: List<RecordingVaultCandidate>
    ): List<RecordingVaultCandidate> {
        val extracted = AutomationVaultManager.extractRecordingCandidates(
            steps = steps,
            capturedSecretsByStepId = recordedSecretsByStepId,
            existingEntries = automationVaultEntriesState.value,
            sourceUrl = targetUrl
        )
        if (previousCandidates.isEmpty()) return extracted
        val prevByStepId = previousCandidates.associateBy { it.stepId }
        return extracted.map { fresh ->
            val prev = prevByStepId[fresh.stepId] ?: return@map fresh
            fresh.copy(
                category = prev.category,
                vaultLabel = prev.vaultLabel.ifBlank { fresh.vaultLabel },
                capturedValue = prev.capturedValue.ifBlank { fresh.capturedValue },
                hasSecretCapture = prev.hasSecretCapture || fresh.hasSecretCapture,
                isSensitive = prev.category.isSensitiveDefault,
                isSelected = prev.isSelected,
                existingEntryId = prev.existingEntryId ?: fresh.existingEntryId
            )
        }
    }

    fun toggleRecordingVaultCandidate(stepId: String) {
        _uiState.update { state ->
            state.copy(
                recordingVaultCandidates = state.recordingVaultCandidates.map { cand ->
                    if (cand.stepId == stepId) cand.copy(isSelected = !cand.isSelected) else cand
                }
            )
        }
    }

    fun updateRecordingVaultCandidate(
        stepId: String,
        category: AutomationVaultCategory,
        label: String,
        value: String,
        existingEntryId: String? = null
    ) {
        if (category == AutomationVaultCategory.PASSWORD && value.isNotEmpty()) {
            recordedSecretsByStepId[stepId] = value
        }
        _uiState.update { state ->
            val found = state.recordingVaultCandidates.any { it.stepId == stepId }
            val updated = if (found) {
                state.recordingVaultCandidates.map { cand ->
                    if (cand.stepId == stepId) {
                        cand.copy(
                            category = category,
                            vaultLabel = label.trim().ifBlank { cand.vaultLabel },
                            capturedValue = value,
                            hasSecretCapture = value.isNotEmpty() || cand.hasSecretCapture,
                            isSensitive = category.isSensitiveDefault,
                            isSelected = true,
                            existingEntryId = existingEntryId
                        )
                    } else {
                        cand
                    }
                }
            } else {
                state.recordingVaultCandidates
            }
            state.copy(recordingVaultCandidates = updated)
        }
    }

    fun assignAutomationVaultEntryToRecordingStep(
        stepId: String,
        entryId: String?,
        category: AutomationVaultCategory? = null
    ) {
        viewModelScope.launch {
            val cleanId = entryId?.trim()?.takeIf { it.isNotEmpty() }
            val resolvedEntry = if (cleanId != null) {
                repository.automationVaultManager.getEntryById(cleanId)
            } else {
                null
            }
            if (cleanId != null && resolvedEntry == null) {
                _uiState.update { it.copy(snackbarNotice = "Selected Automation Vault value was not found") }
                return@launch
            }
            val effectiveCategory = resolvedEntry?.let { AutomationVaultCategory.fromId(it.category) }
                ?: category
                ?: AutomationVaultCategory.CUSTOM
            val isPasswordLike = effectiveCategory == AutomationVaultCategory.PASSWORD

            _uiState.update { state ->
                val updatedSteps = state.recordingSession.steps.map { step ->
                    if (step.id == stepId) {
                        if (resolvedEntry != null) {
                            step.copy(
                                value = "",
                                vaultEntryId = resolvedEntry.entryId,
                                vaultCategory = resolvedEntry.category,
                                requiresCredential = false,
                                description = AutomationStepCodec.buildStepSummary(
                                    actionType = step.actionType,
                                    targetLabel = step.targetLabel,
                                    value = if (isPasswordLike) "" else "${resolvedEntry.label} (Vault)",
                                    isCredentialStep = isPasswordLike,
                                    credentialFieldRole = step.credentialFieldRole
                                )
                            )
                        } else {
                            step.copy(
                                vaultEntryId = null,
                                vaultCategory = ""
                            )
                        }
                    } else {
                        step
                    }
                }
                val updatedCandidates = if (resolvedEntry != null) {
                    state.recordingVaultCandidates.map { cand ->
                        if (cand.stepId == stepId) {
                            cand.copy(
                                category = effectiveCategory,
                                vaultLabel = resolvedEntry.label,
                                existingEntryId = resolvedEntry.entryId,
                                isSelected = true,
                                isSensitive = resolvedEntry.isSensitive
                            )
                        } else {
                            cand
                        }
                    }
                } else {
                    state.recordingVaultCandidates
                }
                state.copy(
                    recordingSession = state.recordingSession.copy(
                        steps = AutomationStepCodec.reindex(updatedSteps)
                    ),
                    recordingVaultCandidates = updatedCandidates,
                    snackbarNotice = if (resolvedEntry != null) {
                        "Linked step to Automation Vault: ${resolvedEntry.label}"
                    } else {
                        "Cleared Automation Vault link for step"
                    }
                )
            }
        }
    }

    fun assignAutomationVaultEntryToWorkflowStep(
        workflowId: Long,
        stepId: String,
        entryId: String?,
        category: AutomationVaultCategory? = null
    ) {
        viewModelScope.launch {
            val workflow = repository.getAutomationWorkflowById(workflowId)
                ?: automationWorkflowsState.value.firstOrNull { it.id == workflowId }
                ?: return@launch
            val doc = AutomationStepCodec.decodeDocument(workflow.stepsJson)
            val cleanId = entryId?.trim()?.takeIf { it.isNotEmpty() }
            val resolvedEntry = if (cleanId != null) {
                repository.automationVaultManager.getEntryById(cleanId)
            } else {
                null
            }
            if (cleanId != null && resolvedEntry == null) {
                _uiState.update { it.copy(snackbarNotice = "Selected Automation Vault value was not found") }
                return@launch
            }
            val effectiveCategory = resolvedEntry?.let { AutomationVaultCategory.fromId(it.category) }
                ?: category
                ?: AutomationVaultCategory.CUSTOM
            val isPasswordLike = effectiveCategory == AutomationVaultCategory.PASSWORD

            val updatedSteps = doc.steps.map { step ->
                if (step.id == stepId) {
                    if (resolvedEntry != null) {
                        step.copy(
                            value = "",
                            vaultEntryId = resolvedEntry.entryId,
                            vaultCategory = resolvedEntry.category,
                            requiresCredential = false,
                            description = AutomationStepCodec.buildStepSummary(
                                actionType = step.actionType,
                                targetLabel = step.targetLabel,
                                value = if (isPasswordLike) "" else "${resolvedEntry.label} (Vault)",
                                isCredentialStep = isPasswordLike,
                                credentialFieldRole = step.credentialFieldRole
                            )
                        )
                    } else {
                        step.copy(
                            vaultEntryId = null,
                            vaultCategory = ""
                        )
                    }
                } else {
                    step
                }
            }
            runCatching {
                repository.saveAutomationWorkflowDocument(
                    id = workflowId,
                    document = doc.copy(steps = AutomationStepCodec.reindex(updatedSteps))
                )
            }.onSuccess {
                validateWorkflow(workflowId)
                loadWorkflowVersions(workflowId)
                _uiState.update {
                    it.copy(
                        snackbarNotice = if (resolvedEntry != null) {
                            "Step will read '${resolvedEntry.label}' from Automation Vault on replay"
                        } else {
                            "Removed Automation Vault link from step"
                        }
                    )
                }
            }.onFailure { ex ->
                _uiState.update {
                    it.copy(snackbarNotice = ex.message ?: "Failed to update step vault binding")
                }
            }
        }
    }

    fun scanActivePageElements(silent: Boolean = false) {
        val activeTab = _uiState.value.activeTab
        val tabId = activeTab.id
        val domain = automationEngine.extractDomain(activeTab.url)
        pageScanJobByTab.remove(tabId)?.cancel()
        if (!silent) {
            _uiState.update {
                it.copy(
                    activeDomHighlight = ActiveDomElementHighlight(
                        selector = "document",
                        targetLabel = domain,
                        actionLabel = "Scanning DOM",
                        isScanning = true,
                        status = "SCANNING"
                    ),
                    pageInspection = it.pageInspection.copy(
                        domain = domain,
                        url = activeTab.url,
                        tabId = tabId,
                        isScanning = true,
                        scanStatus = DomScanStatus.SCANNING,
                        errorMessage = null
                    )
                )
            }
        }
        pageScanJobByTab[tabId] = viewModelScope.launch {
            val epoch = navigationSynchronizer.snapshot(tabId).epoch
            val result = automationEngine.scanPageElements(
                webView = webViewRegistry[tabId],
                currentUrl = activeTab.url,
                tabId = tabId,
                navigationEpoch = epoch
            )
            pageInspectionByTab[tabId] = result
            if (_uiState.value.activeTabId == tabId) {
                _uiState.update { state ->
                    val currentHighlight = state.activeDomHighlight
                    val updatedHighlight = if (!silent && (currentHighlight == null || currentHighlight.isScanning)) {
                        val firstInteractive = result.elements.firstOrNull { it.visible && it.interactable }
                        if (firstInteractive != null) {
                            ActiveDomElementHighlight(
                                selector = firstInteractive.selector,
                                targetLabel = "${result.elements.size} elements on ${result.domain}",
                                actionLabel = "DOM Scanned",
                                tagName = firstInteractive.tagName,
                                boundingBox = firstInteractive.boundingBox,
                                isScanning = false,
                                status = "SCANNED"
                            )
                        } else {
                            null
                        }
                    } else if (currentHighlight?.isScanning == true) {
                        null
                    } else {
                        currentHighlight
                    }
                    state.copy(
                        pageInspection = result,
                        activeDomHighlight = updatedHighlight,
                        extractedRecords = if (result.extractedRecords.isNotEmpty()) {
                            result.extractedRecords
                        } else {
                            state.extractedRecords
                        }
                    )
                }
                refreshAutomationToolbarStatus(clearTransientResult = false)
            }
        }
    }

    fun runExtendedPageScan() {
        val activeTab = _uiState.value.activeTab
        val tabId = activeTab.id
        val domain = automationEngine.extractDomain(activeTab.url)
        pageScanJobByTab.remove(tabId)?.cancel()
        _uiState.update {
            it.copy(
                activeDomHighlight = ActiveDomElementHighlight(
                    selector = "document",
                    targetLabel = domain,
                    actionLabel = "Extended DOM Scan",
                    isScanning = true,
                    status = "SCANNING"
                ),
                pageInspection = it.pageInspection.copy(
                    domain = domain,
                    url = activeTab.url,
                    tabId = tabId,
                    isScanning = true,
                    isExtendedScanRunning = true,
                    scanStatus = DomScanStatus.SCANNING,
                    errorMessage = null
                )
            )
        }
        pageScanJobByTab[tabId] = viewModelScope.launch {
            val epoch = navigationSynchronizer.snapshot(tabId).epoch
            val result = automationEngine.universalScanner.runExtendedScan(
                webView = webViewRegistry[tabId],
                tabId = tabId,
                currentUrl = activeTab.url,
                navigationEpoch = epoch
            )
            pageInspectionByTab[tabId] = result
            if (_uiState.value.activeTabId == tabId) {
                val firstEl = result.elements.firstOrNull { it.visible && it.interactable }
                _uiState.update {
                    it.copy(
                        pageInspection = result,
                        activeDomHighlight = if (firstEl != null) {
                            ActiveDomElementHighlight(
                                selector = firstEl.selector,
                                targetLabel = "${result.elements.size} elements on ${result.domain}",
                                actionLabel = "Extended Scan Complete",
                                tagName = firstEl.tagName,
                                boundingBox = firstEl.boundingBox,
                                isScanning = false,
                                status = "SCANNED"
                            )
                        } else {
                            null
                        },
                        snackbarNotice = "Extended scan finished (${result.elements.size} elements, scroll restored)",
                        extractedRecords = if (result.extractedRecords.isNotEmpty()) {
                            result.extractedRecords
                        } else {
                            it.extractedRecords
                        }
                    )
                }
                refreshAutomationToolbarStatus(clearTransientResult = false)
            }
        }
    }

    fun cancelPageScan(tabId: String = _uiState.value.activeTabId) {
        pageScanJobByTab.remove(tabId)?.cancel()
        val cancelledState = automationEngine.universalScanner.cancelScan(tabId, webViewRegistry[tabId])
        pageInspectionByTab[tabId] = cancelledState
        if (_uiState.value.activeTabId == tabId) {
            _uiState.update {
                it.copy(
                    pageInspection = cancelledState,
                    activeDomHighlight = null,
                    snackbarNotice = "DOM scan cancelled"
                )
            }
        }
    }

    fun highlightDetectedElement(element: DetectedPageElement) {
        val activeTabId = _uiState.value.activeTabId
        val initialHighlight = ActiveDomElementHighlight(
            selector = element.selector,
            targetLabel = element.label.ifBlank { element.accessibleName.ifBlank { element.tagName } },
            actionLabel = "Inspect <${element.tagName}>",
            tagName = element.tagName,
            boundingBox = element.boundingBox,
            isScanning = false,
            status = "INSPECTING"
        )
        _uiState.update { state ->
            state.copy(
                activeDomHighlight = initialHighlight,
                pageInspection = state.pageInspection.copy(
                    highlightedElementSelector = element.selector
                )
            )
        }
        viewModelScope.launch {
            val liveBox = automationEngine.highlightElementOnPage(
                webView = webViewRegistry[activeTabId],
                selector = element.selector,
                targetLabel = element.label,
                scrollIntoView = true
            )
            if (liveBox != null) {
                _uiState.update { state ->
                    val cur = state.activeDomHighlight
                    if (cur != null && cur.selector == element.selector) {
                        state.copy(activeDomHighlight = cur.copy(boundingBox = liveBox))
                    } else {
                        state
                    }
                }
            }
        }
    }

    fun clearActiveDomHighlight() {
        val activeTabId = _uiState.value.activeTabId
        automationEngine.clearElementHighlightsOnPage(webViewRegistry[activeTabId])
        _uiState.update { state ->
            state.copy(
                activeDomHighlight = null,
                pageInspection = state.pageInspection.copy(highlightedElementSelector = "")
            )
        }
    }

    fun toggleScannerOutlineOverlay() {
        _uiState.update { state ->
            val nextEnabled = !state.isScannerOutlineOverlayEnabled
            state.copy(
                isScannerOutlineOverlayEnabled = nextEnabled,
                snackbarNotice = if (nextEnabled) {
                    "DOM element outline overlay enabled"
                } else {
                    "DOM element outline overlay hidden"
                }
            )
        }
    }

    fun onDomMutationBatch(
        rawJson: String,
        sourceTabId: String,
        sourceWebView: WebView?,
        sourceWebViewUrl: String
    ) {
        val updated = automationEngine.universalScanner.processDynamicMutationBatch(
            rawJson = rawJson,
            sourceTabId = sourceTabId,
            sourceWebView = sourceWebView,
            liveWebViewUrl = sourceWebViewUrl,
            currentNavigationEpoch = navigationSynchronizer.snapshot(sourceTabId).epoch
        ) ?: return
        pageInspectionByTab[sourceTabId] = updated
        if (_uiState.value.activeTabId == sourceTabId) {
            _uiState.update { it.copy(pageInspection = updated) }
            refreshAutomationToolbarStatus(clearTransientResult = false)
        }
    }

    // --- Recording Mode (Isolated to Initiating Tab, Origin & Session) ---

    fun startRecording() {
        recordingLifecycleJob?.cancel()
        val stateSnapshot = _uiState.value
        val activeTab = stateSnapshot.activeTab
        val tabId = activeTab.id
        val tabUrl = activeTab.url
        val domain = automationEngine.extractDomain(tabUrl)
        val parsedOrigin = automationEngine.parseOrigin(tabUrl)
        val wv = webViewRegistry[tabId]

        if (parsedOrigin !is RecordingOriginResult.Valid) {
            setRecordingErrorState(
                initiatingTabId = tabId,
                targetDomain = domain,
                targetUrl = tabUrl,
                code = AutomationErrorCode.RECORDING_DOCUMENT_UNAVAILABLE,
                message = "Open an HTTP or HTTPS website before starting recording.",
                preserveSteps = false
            )
            return
        }

        if (wv == null) {
            setRecordingErrorState(
                initiatingTabId = tabId,
                targetDomain = domain,
                targetUrl = tabUrl,
                code = AutomationErrorCode.RECORDING_DOCUMENT_UNAVAILABLE,
                message = "Active webpage is not ready for recording yet.",
                preserveSteps = false
            )
            return
        }

        val navSnap = navigationSynchronizer.snapshot(tabId)
        if (navSnap.phase == NavigationLifecyclePhase.FAILED) {
            setRecordingErrorState(
                initiatingTabId = tabId,
                targetDomain = domain,
                targetUrl = tabUrl,
                code = AutomationErrorCode.RECORDING_DOCUMENT_UNAVAILABLE,
                message = "Active webpage failed to load (${navSnap.failureReason ?: "load error"}). Reload before recording.",
                preserveSteps = false
            )
            return
        }

        val liveWebViewUrl = wv.url?.trim()?.takeIf { it.isNotBlank() }
            ?.let { if (it.startsWith("data:", ignoreCase = true)) it else mapBaseUrlBackToScheme(it, tabUrl) }
        if (liveWebViewUrl != null) {
            val liveOrigin = automationEngine.parseOrigin(liveWebViewUrl)
            if (liveOrigin !is RecordingOriginResult.Valid || liveOrigin.canonicalOrigin != parsedOrigin.canonicalOrigin) {
                setRecordingErrorState(
                    initiatingTabId = tabId,
                    targetDomain = domain,
                    targetUrl = tabUrl,
                    code = AutomationErrorCode.RECORDING_ORIGIN_CHANGED,
                    message = "WebView origin does not match the active tab URL.",
                    preserveSteps = false
                )
                return
            }
        }

        val existing = stateSnapshot.recordingSession
        if (existing.initiatingTabId.isNotBlank() && existing.sessionId.isNotBlank()) {
            automationEngine.detachRecorderBestEffort(webViewRegistry[existing.initiatingTabId], existing.sessionId)
        }

        val newSessionId = "rec_sess_${UUID.randomUUID()}"
        val expectedOrigin = parsedOrigin.canonicalOrigin
        recordedSecretsByStepId.clear()

        _uiState.update {
            it.copy(
                isAutomationHudOpen = false,
                automationHudMode = AutomationHudMode.RECORDING,
                recordingVaultCandidates = emptyList(),
                recordingSession = ActiveRecordingSession(
                    sessionId = "",
                    initiatingTabId = tabId,
                    initialOrigin = expectedOrigin,
                    currentOrigin = expectedOrigin,
                    status = RecordingStatus.IDLE,
                    steps = emptyList(),
                    targetDomain = domain,
                    targetUrl = tabUrl,
                    suggestedName = if (domain == "Start Page") "Recorded Workflow" else "$domain Workflow",
                    recordingError = null,
                    pendingOriginChangeUrl = null
                )
            )
        }
        refreshAutomationToolbarStatus(clearTransientResult = true)

        recordingLifecycleJob = viewModelScope.launch {
            val result = automationEngine.installRecorderScript(
                webView = wv,
                sessionId = newSessionId,
                tabId = tabId,
                expectedOrigin = expectedOrigin,
                initialSequenceNumber = 0L
            )
            val latestTab = _uiState.value.tabs.firstOrNull { it.id == tabId }
            val latestOrigin = latestTab?.let { automationEngine.parseOrigin(it.url) }
            val latestWvUrl = wv.url?.trim()?.takeIf { it.isNotBlank() }?.let {
                if (it.startsWith("data:", ignoreCase = true)) it else mapBaseUrlBackToScheme(it, latestTab?.url ?: tabUrl)
            }
            val latestWvOriginOk = latestWvUrl == null ||
                (automationEngine.parseOrigin(latestWvUrl) as? RecordingOriginResult.Valid)?.canonicalOrigin == expectedOrigin
            val stillValidDocument = latestTab != null &&
                webViewRegistry[tabId] === wv &&
                latestOrigin is RecordingOriginResult.Valid &&
                latestOrigin.canonicalOrigin == expectedOrigin &&
                latestWvOriginOk

            if (!result.success || !stillValidDocument) {
                if (result.success && !stillValidDocument) {
                    automationEngine.detachRecorderBestEffort(wv, newSessionId)
                }
                val errCode = if (!stillValidDocument) {
                    if (latestTab == null) AutomationErrorCode.RECORDING_TAB_CLOSED
                    else AutomationErrorCode.RECORDING_ORIGIN_CHANGED
                } else {
                    result.errorCode
                }
                val errMsg = if (!stillValidDocument) {
                    "Recording target changed before recorder could attach."
                } else {
                    result.message.ifBlank { "Failed to start recording on $domain." }
                }
                setRecordingErrorState(
                    initiatingTabId = tabId,
                    targetDomain = domain,
                    targetUrl = tabUrl,
                    code = errCode,
                    message = errMsg,
                    preserveSteps = false
                )
                return@launch
            }

            _uiState.update {
                it.copy(
                    isAutomationHudOpen = false,
                    automationHudMode = AutomationHudMode.RECORDING,
                    recordingSession = ActiveRecordingSession(
                        sessionId = newSessionId,
                        initiatingTabId = tabId,
                        initialOrigin = expectedOrigin,
                        currentOrigin = expectedOrigin,
                        status = RecordingStatus.RECORDING,
                        steps = emptyList(),
                        targetDomain = domain,
                        targetUrl = tabUrl,
                        suggestedName = if (domain == "Start Page") "Recorded Workflow" else "$domain Workflow",
                        startedAt = System.currentTimeMillis(),
                        lastSequenceNumber = 0L,
                        recordingError = null,
                        pendingOriginChangeUrl = null
                    ),
                    snackbarNotice = "Recording active — interact with page, tap icon to return"
                )
            }
            refreshAutomationToolbarStatus(clearTransientResult = true)
        }
    }

    fun pauseRecording() {
        val session = _uiState.value.recordingSession
        if (session.status != RecordingStatus.RECORDING) return
        recordingLifecycleJob?.cancel()
        val targetTabId = session.initiatingTabId
        val targetSessionId = session.sessionId
        _uiState.update {
            it.copy(
                recordingSession = it.recordingSession.copy(status = RecordingStatus.PAUSED)
            )
        }
        refreshAutomationToolbarStatus(clearTransientResult = false)
        val wv = webViewRegistry[targetTabId]
        if (wv != null) {
            recordingLifecycleJob = viewModelScope.launch {
                automationEngine.uninstallRecorderScript(wv, targetSessionId)
            }
        }
    }

    fun resumeRecording() {
        val session = _uiState.value.recordingSession
        if (session.status != RecordingStatus.PAUSED) return
        recordingLifecycleJob?.cancel()

        if (session.sessionId.isBlank() || session.initiatingTabId.isBlank()) {
            setRecordingErrorState(
                initiatingTabId = session.initiatingTabId,
                targetDomain = session.targetDomain,
                targetUrl = session.targetUrl,
                code = AutomationErrorCode.RECORDING_SESSION_INVALID,
                message = "Recording session is no longer valid.",
                preserveSteps = true
            )
            return
        }

        val initiatingTab = _uiState.value.tabs.firstOrNull { it.id == session.initiatingTabId }
        if (initiatingTab == null) {
            _uiState.update { state ->
                state.copy(
                    recordingSession = state.recordingSession.copy(
                        status = RecordingStatus.CANCELLED,
                        sessionId = "",
                        recordingError = RecordingErrorInfo(
                            code = AutomationErrorCode.RECORDING_TAB_CLOSED,
                            message = "Recording cancelled because the recording tab was closed."
                        )
                    )
                )
            }
            refreshAutomationToolbarStatus(clearTransientResult = true)
            return
        }

        val tabOrigin = automationEngine.parseOrigin(initiatingTab.url)
        val expectedOrigin = session.currentOrigin
        if (tabOrigin !is RecordingOriginResult.Valid || tabOrigin.canonicalOrigin != expectedOrigin) {
            _uiState.update { state ->
                state.copy(
                    recordingSession = state.recordingSession.copy(
                        status = RecordingStatus.STOPPED,
                        sessionId = "",
                        pendingOriginChangeUrl = initiatingTab.url,
                        recordingError = RecordingErrorInfo(
                            code = AutomationErrorCode.RECORDING_ORIGIN_CHANGED,
                            message = "Recording stopped: tab navigated away from ${session.targetDomain}."
                        )
                    ),
                    snackbarNotice = "Recording stopped — tab origin changed"
                )
            }
            refreshAutomationToolbarStatus(clearTransientResult = false)
            return
        }

        val wv = webViewRegistry[session.initiatingTabId]
        if (wv == null) {
            setRecordingErrorState(
                initiatingTabId = session.initiatingTabId,
                targetDomain = session.targetDomain,
                targetUrl = session.targetUrl,
                code = AutomationErrorCode.RECORDING_DOCUMENT_UNAVAILABLE,
                message = "Recording tab WebView is unavailable to resume.",
                preserveSteps = true
            )
            return
        }

        val navSnap = navigationSynchronizer.snapshot(session.initiatingTabId)
        if (navSnap.phase == NavigationLifecyclePhase.FAILED) {
            setRecordingErrorState(
                initiatingTabId = session.initiatingTabId,
                targetDomain = session.targetDomain,
                targetUrl = session.targetUrl,
                code = AutomationErrorCode.RECORDING_DOCUMENT_UNAVAILABLE,
                message = "Recording cannot resume because the page failed to load.",
                preserveSteps = true
            )
            return
        }

        val liveWebViewUrl = wv.url?.trim()?.takeIf { it.isNotBlank() }
            ?.let { if (it.startsWith("data:", ignoreCase = true)) it else mapBaseUrlBackToScheme(it, initiatingTab.url) }
        if (liveWebViewUrl != null) {
            val liveOrigin = automationEngine.parseOrigin(liveWebViewUrl)
            if (liveOrigin !is RecordingOriginResult.Valid || liveOrigin.canonicalOrigin != expectedOrigin) {
                _uiState.update { state ->
                    state.copy(
                        recordingSession = state.recordingSession.copy(
                            status = RecordingStatus.STOPPED,
                            sessionId = "",
                            pendingOriginChangeUrl = liveWebViewUrl,
                            recordingError = RecordingErrorInfo(
                                code = AutomationErrorCode.RECORDING_ORIGIN_CHANGED,
                                message = "Recording stopped: WebView origin changed away from ${session.targetDomain}."
                            )
                        ),
                        snackbarNotice = "Recording stopped — tab origin changed"
                    )
                }
                refreshAutomationToolbarStatus(clearTransientResult = false)
                return
            }
        }

        val resumeSessionId = session.sessionId
        val resumeTabId = session.initiatingTabId
        val resumeSeq = session.lastSequenceNumber

        recordingLifecycleJob = viewModelScope.launch {
            val result = automationEngine.installRecorderScript(
                webView = wv,
                sessionId = resumeSessionId,
                tabId = resumeTabId,
                expectedOrigin = expectedOrigin,
                initialSequenceNumber = resumeSeq
            )
            val currentSession = _uiState.value.recordingSession
            val latestTab = _uiState.value.tabs.firstOrNull { it.id == resumeTabId }
            val latestOrigin = latestTab?.let { automationEngine.parseOrigin(it.url) }
            val latestWvUrl = wv.url?.trim()?.takeIf { it.isNotBlank() }?.let {
                if (it.startsWith("data:", ignoreCase = true)) it else mapBaseUrlBackToScheme(it, latestTab?.url ?: session.targetUrl)
            }
            val latestWvOriginOk = latestWvUrl == null ||
                (automationEngine.parseOrigin(latestWvUrl) as? RecordingOriginResult.Valid)?.canonicalOrigin == expectedOrigin
            val stillConsistent = currentSession.status == RecordingStatus.PAUSED &&
                currentSession.sessionId == resumeSessionId &&
                latestTab != null &&
                webViewRegistry[resumeTabId] === wv &&
                latestOrigin is RecordingOriginResult.Valid &&
                latestOrigin.canonicalOrigin == expectedOrigin &&
                latestWvOriginOk

            if (!result.success || !stillConsistent) {
                if (result.success && !stillConsistent) {
                    automationEngine.detachRecorderBestEffort(wv, resumeSessionId)
                }
                if (!stillConsistent) {
                    if (latestTab == null) {
                        _uiState.update { state ->
                            state.copy(
                                recordingSession = state.recordingSession.copy(
                                    status = RecordingStatus.CANCELLED,
                                    sessionId = "",
                                    recordingError = RecordingErrorInfo(
                                        code = AutomationErrorCode.RECORDING_TAB_CLOSED,
                                        message = "Recording cancelled because the recording tab was closed."
                                    )
                                ),
                                snackbarNotice = "Recording cancelled — recording tab closed"
                            )
                        }
                        refreshAutomationToolbarStatus(clearTransientResult = true)
                    } else if (currentSession.sessionId == resumeSessionId && currentSession.status == RecordingStatus.PAUSED) {
                        _uiState.update { state ->
                            state.copy(
                                recordingSession = state.recordingSession.copy(
                                    status = RecordingStatus.STOPPED,
                                    sessionId = "",
                                    pendingOriginChangeUrl = latestWvUrl ?: latestTab.url,
                                    recordingError = RecordingErrorInfo(
                                        code = AutomationErrorCode.RECORDING_ORIGIN_CHANGED,
                                        message = "Recording stopped because navigation left ${session.targetDomain}."
                                    )
                                ),
                                snackbarNotice = "Recording stopped — left ${session.targetDomain}"
                            )
                        }
                        refreshAutomationToolbarStatus(clearTransientResult = false)
                    }
                } else {
                    setRecordingErrorState(
                        initiatingTabId = resumeTabId,
                        targetDomain = session.targetDomain,
                        targetUrl = session.targetUrl,
                        code = result.errorCode,
                        message = result.message.ifBlank { "Unable to resume recording on ${session.targetDomain}." },
                        preserveSteps = true
                    )
                }
                return@launch
            }

            _uiState.update { state ->
                state.copy(
                    isAutomationHudOpen = false,
                    recordingSession = state.recordingSession.copy(
                        status = RecordingStatus.RECORDING,
                        recordingError = null
                    )
                )
            }
            refreshAutomationToolbarStatus(clearTransientResult = false)
        }
    }

    fun stopRecording() {
        val session = _uiState.value.recordingSession
        if (session.status != RecordingStatus.RECORDING && session.status != RecordingStatus.PAUSED) return
        recordingLifecycleJob?.cancel()
        val oldSessionId = session.sessionId
        val targetTabId = session.initiatingTabId
        _uiState.update { state ->
            val cur = state.recordingSession
            val candidates = buildMergedRecordingCandidates(
                steps = cur.steps,
                targetUrl = cur.targetUrl,
                previousCandidates = state.recordingVaultCandidates
            )
            state.copy(
                recordingSession = cur.copy(
                    status = RecordingStatus.STOPPED,
                    sessionId = ""
                ),
                recordingVaultCandidates = candidates
            )
        }
        refreshAutomationToolbarStatus(clearTransientResult = false)
        val wv = webViewRegistry[targetTabId]
        if (wv != null) {
            recordingLifecycleJob = viewModelScope.launch {
                automationEngine.uninstallRecorderScript(wv, oldSessionId.ifBlank { null })
            }
        }
    }

    fun minimizeRecordingHud() {
        _uiState.update {
            it.copy(
                isAutomationHudOpen = false,
                snackbarNotice = "Recording active — interact with page, tap icon to return"
            )
        }
    }

    fun onRecordedDomEvent(
        event: RecordedDomEvent,
        sourceTabId: String,
        sourceWebView: WebView?,
        sourceWebViewUrl: String
    ) {
        processRecordedDomEvent(
            event = event,
            sourceTabId = sourceTabId,
            sourceWebView = sourceWebView,
            sourceWebViewUrl = sourceWebViewUrl
        )
    }

    internal fun processRecordedDomEvent(
        event: RecordedDomEvent,
        sourceTabId: String,
        sourceWebView: WebView?,
        sourceWebViewUrl: String
    ): RecorderLifecycleResult {
        val stateSnapshot = _uiState.value
        val session = stateSnapshot.recordingSession

        fun reject(code: AutomationErrorCode, reason: String): RecorderLifecycleResult {
            lastRecordingRejectionCode = code
            return RecorderLifecycleResult(success = false, errorCode = code, message = reason)
        }

        // A. Session validation
        if (session.status != RecordingStatus.RECORDING || session.sessionId.isBlank()) {
            return reject(
                AutomationErrorCode.RECORDING_SESSION_INVALID,
                "No active recording session."
            )
        }
        if (event.sessionId.isBlank()) {
            return reject(
                AutomationErrorCode.RECORDING_SESSION_INVALID,
                "Recorded event missing sessionId."
            )
        }
        if (event.sessionId != session.sessionId) {
            return reject(
                AutomationErrorCode.RECORDING_SESSION_MISMATCH,
                "Recorded event came from a stale or mismatched session."
            )
        }

        // B. Tab validation
        if (session.initiatingTabId.isBlank()) {
            return reject(
                AutomationErrorCode.RECORDING_SESSION_INVALID,
                "Recording session has no initiating tab."
            )
        }
        if (event.tabId.isBlank() ||
            event.tabId != session.initiatingTabId ||
            sourceTabId.isBlank() ||
            sourceTabId != session.initiatingTabId
        ) {
            return reject(
                AutomationErrorCode.RECORDING_TAB_MISMATCH,
                "Recorded event did not originate from the initiating tab."
            )
        }
        val initiatingTab = stateSnapshot.tabs.firstOrNull { it.id == session.initiatingTabId }
            ?: return reject(
                AutomationErrorCode.RECORDING_TAB_CLOSED,
                "Initiating tab is no longer open."
            )

        // C. Native WebView instance validation
        val expectedWebView = webViewRegistry[session.initiatingTabId]
            ?: return reject(
                AutomationErrorCode.RECORDING_DOCUMENT_UNAVAILABLE,
                "Initiating tab WebView is unavailable."
            )
        if (sourceWebView != null && sourceWebView !== expectedWebView) {
            return reject(
                AutomationErrorCode.RECORDING_TAB_MISMATCH,
                "Recorded event came from a non-initiating WebView instance."
            )
        }

        // D. Origin validation (event origin + tab URL + live WebView URL)
        val expectedOrigin = automationEngine.parseOrigin(session.currentOrigin)
        if (expectedOrigin !is RecordingOriginResult.Valid) {
            return reject(
                AutomationErrorCode.RECORDING_SESSION_INVALID,
                "Session origin is invalid."
            )
        }
        val eventOrigin = automationEngine.parseOrigin(event.origin)
        if (eventOrigin !is RecordingOriginResult.Valid ||
            eventOrigin.canonicalOrigin != expectedOrigin.canonicalOrigin
        ) {
            return reject(
                AutomationErrorCode.RECORDING_ORIGIN_CHANGED,
                "Recorded event origin '${event.origin}' does not match '${expectedOrigin.canonicalOrigin}'."
            )
        }
        val tabOrigin = automationEngine.parseOrigin(initiatingTab.url)
        if (tabOrigin !is RecordingOriginResult.Valid ||
            tabOrigin.canonicalOrigin != expectedOrigin.canonicalOrigin
        ) {
            return reject(
                AutomationErrorCode.RECORDING_ORIGIN_CHANGED,
                "Initiating tab URL origin changed away from '${expectedOrigin.canonicalOrigin}'."
            )
        }
        val candidateWebViewUrls = listOf(sourceWebViewUrl, expectedWebView.url.orEmpty())
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        for (rawWvUrl in candidateWebViewUrls) {
            val mappedWvUrl = if (rawWvUrl.startsWith("data:", ignoreCase = true)) {
                rawWvUrl
            } else {
                mapBaseUrlBackToScheme(rawWvUrl, initiatingTab.url)
            }
            val wvOrigin = automationEngine.parseOrigin(mappedWvUrl)
            if (wvOrigin !is RecordingOriginResult.Valid ||
                wvOrigin.canonicalOrigin != expectedOrigin.canonicalOrigin
            ) {
                return reject(
                    AutomationErrorCode.RECORDING_ORIGIN_CHANGED,
                    "WebView URL origin does not match recording origin."
                )
            }
        }

        // E. Monotonic sequence check
        if (event.sequenceNumber <= 0L || event.sequenceNumber <= session.lastSequenceNumber) {
            return reject(
                AutomationErrorCode.RECORDING_SESSION_INVALID,
                "Out-of-order or duplicate event sequence ${event.sequenceNumber}."
            )
        }

        // F. Event type & security sanitization
        val matchedActionType = AutomationActionType.entries.firstOrNull {
            it != AutomationActionType.UNSUPPORTED &&
                it.id.equals(event.eventType.trim(), ignoreCase = true)
        } ?: return reject(
            AutomationErrorCode.RECORDING_SESSION_INVALID,
            "Unsupported recorded event type '${event.eventType}'."
        )

        val isPageLevelScroll = matchedActionType == AutomationActionType.SCROLL &&
            event.selector.isBlank() &&
            event.locatorCandidates.isEmpty()
        val matchedElement = if (isPageLevelScroll) {
            null
        } else {
            automationEngine.universalScanner.matchRecordedEventToElement(
                event = event,
                inspectionState = pageInspectionByTab[session.initiatingTabId]
            )
        }
        val rawCleanLabel = event.targetLabel.trim().ifBlank { "Page element" }
        val cleanLabel = matchedElement?.accessibleName?.takeIf { it.isNotBlank() } ?: rawCleanLabel
        val resolvedSelector = if (isPageLevelScroll) {
            ""
        } else {
            matchedElement?.selector?.takeIf { it.isNotBlank() } ?: event.selector
        }
        val rawValue = event.value.trim()
        val isPasswordField = event.isPasswordField ||
            matchedElement?.sensitiveCategory == SensitiveFieldCategory.PASSWORD ||
            event.fieldRoleHint.equals("PASSWORD", ignoreCase = true) ||
            cleanLabel.contains("password", ignoreCase = true) ||
            resolvedSelector.contains("password", ignoreCase = true) ||
            Regex("^•{2,}$").matches(rawValue)
        val isOtherSensitiveField = matchedElement != null &&
            matchedElement.sensitiveCategory.isSensitive &&
            matchedElement.sensitiveCategory != SensitiveFieldCategory.NONE
        val cleanValue = if (isPasswordField || isOtherSensitiveField) {
            ""
        } else {
            rawValue
        }
        val ephemeralSecret = when {
            event.sensitiveCapturedValue.isNotEmpty() -> event.sensitiveCapturedValue
            (isPasswordField || isOtherSensitiveField) &&
                rawValue.isNotEmpty() &&
                !Regex("^•{2,}$").matches(rawValue) -> rawValue
            else -> ""
        }
        val inferredRole = if (isPasswordField && matchedActionType == AutomationActionType.INPUT) {
            CredentialFieldRole.PASSWORD
        } else {
            CredentialFieldRole.fromIdOrNull(event.fieldRoleHint)
        }
        val requiresCred = isPasswordField && matchedActionType == AutomationActionType.INPUT
        val resolvedKeyboardKey = if (matchedActionType == AutomationActionType.KEY_PRESS) {
            cleanValue
        } else {
            ""
        }
        val resolvedDesiredCheckedState = if (matchedActionType == AutomationActionType.TOGGLE) {
            when (cleanValue.lowercase()) {
                "checked", "true" -> true
                "unchecked", "false" -> false
                else -> null
            }
        } else {
            null
        }

        val mergedCandidates = (matchedElement?.locatorCandidates.orEmpty() + event.locatorCandidates)
            .distinctBy { it.selector }
            .sortedWith(compareByDescending<LocatorCandidate> { it.isUnique }.thenByDescending { it.confidence })
        val resolvedFramePath = matchedElement?.framePath?.takeIf { it.isNotEmpty() } ?: event.framePath
        val resolvedShadowPath = matchedElement?.shadowHostPath?.takeIf { it.isNotEmpty() } ?: event.shadowHostPath
        val resolvedBoundingBox = event.boundingBox?.takeIf { it.isValid } ?: matchedElement?.boundingBox
        val resolvedTagName = event.tagName.ifBlank { matchedElement?.tagName.orEmpty() }

        var accepted = false
        var createdStep: AutomationStep? = null
        _uiState.update { state ->
            val current = state.recordingSession
            if (current.status != RecordingStatus.RECORDING ||
                current.sessionId != event.sessionId ||
                current.initiatingTabId != event.tabId ||
                event.sequenceNumber <= current.lastSequenceNumber
            ) {
                state
            } else {
                accepted = true
                val last = current.steps.lastOrNull()
                val isDuplicateFocusStep = last != null &&
                    (matchedActionType == AutomationActionType.SELECT ||
                        matchedActionType == AutomationActionType.FOCUS ||
                        matchedActionType == AutomationActionType.BLUR) &&
                    last.actionType == matchedActionType &&
                    last.targetLabel.equals(cleanLabel, ignoreCase = true) &&
                    last.value == cleanValue &&
                    last.requiresCredential == requiresCred

                val isSameInputFieldUpdate = last != null &&
                    matchedActionType == AutomationActionType.INPUT &&
                    last.actionType == AutomationActionType.INPUT &&
                    last.requiresCredential == requiresCred &&
                    (last.selector == resolvedSelector || last.targetLabel.equals(cleanLabel, ignoreCase = true))

                val isSameSelectFieldUpdate = last != null &&
                    matchedActionType == AutomationActionType.SELECT &&
                    last.actionType == AutomationActionType.SELECT &&
                    (last.selector == resolvedSelector ||
                        last.targetLabel.removeSuffix(" field").trim().equals(cleanLabel.removeSuffix(" field").trim(), ignoreCase = true))

                val detectedFieldCategory = when (matchedActionType) {
                    AutomationActionType.INPUT,
                    AutomationActionType.SELECT,
                    AutomationActionType.TOGGLE -> AutomationVaultManager.detectCategory(
                        targetLabel = cleanLabel,
                        selector = resolvedSelector,
                        value = cleanValue,
                        isCredentialStep = isPasswordField,
                        credentialFieldRole = inferredRole
                    )
                    else -> AutomationVaultCategory.CUSTOM
                }
                val isFieldOnlyPoolCandidate = (matchedActionType == AutomationActionType.INPUT ||
                    matchedActionType == AutomationActionType.SELECT) &&
                    !requiresCred &&
                    cleanValue.isEmpty() &&
                    ephemeralSecret.isEmpty()

                val updatedSteps = when {
                    isDuplicateFocusStep -> current.steps
                    isSameInputFieldUpdate || isSameSelectFieldUpdate -> {
                        val usePoolNow = isFieldOnlyPoolCandidate
                        val catIdNow = if (usePoolNow) detectedFieldCategory.id else ""
                        val folderIdNow = if (usePoolNow) {
                            AutomationVaultManager.defaultFolderIdForCategory(detectedFieldCategory)
                        } else {
                            ""
                        }
                        val preferredLabel = if (isSameSelectFieldUpdate &&
                            last!!.targetLabel.endsWith(" field") &&
                            !cleanLabel.endsWith(" field")
                        ) {
                            cleanLabel
                        } else if (isSameSelectFieldUpdate && cleanLabel.endsWith(" field") && !last!!.targetLabel.endsWith(" field")) {
                            last.targetLabel
                        } else {
                            cleanLabel
                        }
                        val summary = AutomationStepCodec.buildStepSummary(
                            actionType = matchedActionType,
                            targetLabel = preferredLabel,
                            value = cleanValue,
                            isCredentialStep = requiresCred,
                            credentialFieldRole = inferredRole,
                            useVaultPool = usePoolNow,
                            vaultCategoryLabel = detectedFieldCategory.label
                        )
                        val updatedLast = last!!.copy(
                            targetLabel = preferredLabel,
                            selector = resolvedSelector,
                            value = cleanValue,
                            description = summary,
                            useVaultPool = usePoolNow,
                            vaultCategory = catIdNow,
                            vaultFolderId = folderIdNow,
                            locatorCandidates = if (mergedCandidates.isNotEmpty()) mergedCandidates else last.locatorCandidates,
                            framePath = if (resolvedFramePath.isNotEmpty()) resolvedFramePath else last.framePath,
                            shadowHostPath = if (resolvedShadowPath.isNotEmpty()) resolvedShadowPath else last.shadowHostPath
                        )
                        createdStep = updatedLast
                        current.steps.dropLast(1) + updatedLast
                    }
                    else -> {
                        val usePoolNow = isFieldOnlyPoolCandidate
                        val catIdNow = if (usePoolNow) detectedFieldCategory.id else ""
                        val folderIdNow = if (usePoolNow) {
                            AutomationVaultManager.defaultFolderIdForCategory(detectedFieldCategory)
                        } else {
                            ""
                        }
                        val summary = AutomationStepCodec.buildStepSummary(
                            actionType = matchedActionType,
                            targetLabel = cleanLabel,
                            value = cleanValue,
                            isCredentialStep = requiresCred,
                            credentialFieldRole = inferredRole,
                            useVaultPool = usePoolNow,
                            vaultCategoryLabel = detectedFieldCategory.label
                        )
                        val newStep = AutomationStep(
                            id = "rec_${UUID.randomUUID()}",
                            order = current.steps.size + 1,
                            actionType = matchedActionType,
                            targetLabel = cleanLabel,
                            selector = resolvedSelector,
                            value = cleanValue,
                            description = summary,
                            isDestructive = event.isDestructive,
                            credentialReferenceId = null,
                            credentialFieldRole = inferredRole,
                            expectedOrigin = if (requiresCred) expectedOrigin.canonicalOrigin else "",
                            requiresCredential = requiresCred,
                            locatorCandidates = mergedCandidates,
                            controlKindHint = matchedElement?.controlKind,
                            dropdownKindHint = matchedElement?.dropdownKind,
                            keyboardKey = resolvedKeyboardKey,
                            desiredCheckedState = resolvedDesiredCheckedState,
                            framePath = resolvedFramePath,
                            shadowHostPath = resolvedShadowPath,
                            vaultCategory = catIdNow,
                            useVaultPool = usePoolNow,
                            vaultFolderId = folderIdNow
                        )
                        createdStep = newStep
                        AutomationStepCodec.reindex(current.steps + newStep)
                    }
                }

                val recordingAmbig = if (createdStep != null &&
                    (matchedElement == null || matchedElement.isLocatorAmbiguous) &&
                    event.matchCount > 1 &&
                    event.ambiguousCandidates.isNotEmpty()
                ) {
                    PendingDisambiguationState(
                        source = DisambiguationSource.RECORDING,
                        stepId = createdStep!!.id,
                        stepOrder = createdStep!!.order,
                        stepDescription = createdStep!!.description,
                        actionType = createdStep!!.actionType,
                        originalTarget = cleanLabel.ifBlank { resolvedSelector },
                        originalSelector = resolvedSelector,
                        candidates = event.ambiguousCandidates
                    )
                } else {
                    state.pendingDisambiguation
                }

                state.copy(
                    activeDomHighlight = ActiveDomElementHighlight(
                        selector = resolvedSelector,
                        targetLabel = cleanLabel,
                        actionLabel = "Recorded ${matchedActionType.label}",
                        tagName = resolvedTagName,
                        boundingBox = resolvedBoundingBox,
                        isScanning = false,
                        stepIndex = updatedSteps.size,
                        totalSteps = updatedSteps.size,
                        status = "RECORDED"
                    ),
                    recordingSession = current.copy(
                        steps = updatedSteps,
                        lastSequenceNumber = event.sequenceNumber
                    ),
                    pendingDisambiguation = recordingAmbig
                )
            }
        }
        if (accepted && createdStep != null) {
            if (ephemeralSecret.isNotEmpty()) {
                recordedSecretsByStepId[createdStep!!.id] = ephemeralSecret
            } else if (matchedActionType == AutomationActionType.CLEAR) {
                recordedSecretsByStepId.remove(createdStep!!.id)
            }
        }
        if (accepted && !isPageLevelScroll && webViewRegistry[session.initiatingTabId] != null) {
            val mayOpenPopupOrSheet = matchedElement == null ||
                (matchedActionType in setOf(AutomationActionType.CLICK, AutomationActionType.SELECT) && (
                    matchedElement.controlKind == ControlKind.CUSTOM_DROPDOWN ||
                        matchedElement.dropdownKind != DropdownKind.NONE ||
                        matchedElement.ariaAttributes.containsKey("aria-haspopup") ||
                        matchedElement.ariaAttributes.containsKey("aria-expanded") ||
                        matchedElement.relevantAttributes.keys.any { k ->
                            k == "aria-haspopup" || k == "data-datepicker" || k == "data-timepicker" ||
                                k == "data-calendar" || k == "data-bottom-sheet" || k == "data-sheet" ||
                                k == "data-drawer" || k == "data-modal" || k == "data-bs-toggle"
                        } ||
                        event.relevantAttributes.keys.any { k ->
                            k == "aria-haspopup" || k == "data-datepicker" || k == "data-timepicker" ||
                                k == "data-calendar" || k == "data-bottom-sheet" || k == "data-sheet" ||
                                k == "data-drawer"
                        }
                    ))
            if (mayOpenPopupOrSheet) {
                scanActivePageElements(silent = true)
            }
        }
        return if (accepted) {
            RecorderLifecycleResult(success = true)
        } else {
            reject(
                AutomationErrorCode.RECORDING_SESSION_INVALID,
                "Recording session changed while applying recorded event."
            )
        }
    }

    private fun handleOriginNavigationForRecording(tabId: String, destinationUrl: String) {
        val session = _uiState.value.recordingSession
        if (session.initiatingTabId != tabId) return
        if (session.status != RecordingStatus.RECORDING && session.status != RecordingStatus.PAUSED) return

        val destOrigin = automationEngine.parseOrigin(destinationUrl)
        val isSameOrigin = destOrigin is RecordingOriginResult.Valid &&
            destOrigin.canonicalOrigin == session.currentOrigin
        if (isSameOrigin) return

        recordingLifecycleJob?.cancel()
        val oldSessionId = session.sessionId
        automationEngine.detachRecorderBestEffort(
            webViewRegistry[tabId],
            oldSessionId.ifBlank { null }
        )

        _uiState.update { state ->
            val current = state.recordingSession
            if (current.initiatingTabId != tabId ||
                (current.status != RecordingStatus.RECORDING && current.status != RecordingStatus.PAUSED)
            ) {
                state
            } else {
                val candidates = buildMergedRecordingCandidates(
                    steps = current.steps,
                    targetUrl = current.targetUrl,
                    previousCandidates = state.recordingVaultCandidates
                )
                state.copy(
                    recordingSession = current.copy(
                        status = RecordingStatus.STOPPED,
                        sessionId = "",
                        pendingOriginChangeUrl = destinationUrl,
                        recordingError = RecordingErrorInfo(
                            code = AutomationErrorCode.RECORDING_ORIGIN_CHANGED,
                            message = "Recording stopped because navigation left ${current.targetDomain}."
                        )
                    ),
                    recordingVaultCandidates = candidates,
                    snackbarNotice = "Recording stopped — left ${current.targetDomain}"
                )
            }
        }
        refreshAutomationToolbarStatus(clearTransientResult = false)
    }

    private fun handleDocumentUnavailableForRecording(tabId: String, reason: String) {
        val session = _uiState.value.recordingSession
        if (session.initiatingTabId != tabId) return
        if (session.status != RecordingStatus.RECORDING && session.status != RecordingStatus.PAUSED) return

        recordingLifecycleJob?.cancel()
        val oldSessionId = session.sessionId
        automationEngine.detachRecorderBestEffort(
            webViewRegistry[tabId],
            oldSessionId.ifBlank { null }
        )

        _uiState.update { state ->
            val current = state.recordingSession
            if (current.initiatingTabId != tabId ||
                (current.status != RecordingStatus.RECORDING && current.status != RecordingStatus.PAUSED)
            ) {
                state
            } else {
                state.copy(
                    recordingSession = current.copy(
                        status = RecordingStatus.ERROR,
                        sessionId = "",
                        recordingError = RecordingErrorInfo(
                            code = AutomationErrorCode.RECORDING_DOCUMENT_UNAVAILABLE,
                            message = "Recording stopped: document failed to load ($reason)."
                        )
                    ),
                    snackbarNotice = "Recording stopped — page failed to load"
                )
            }
        }
        refreshAutomationToolbarStatus(clearTransientResult = false)
    }

    private fun handleTabsClosedForRecording(closedTabIds: Set<String>) {
        val session = _uiState.value.recordingSession
        if (session.initiatingTabId.isBlank() || session.initiatingTabId !in closedTabIds) return
        if (session.status != RecordingStatus.RECORDING && session.status != RecordingStatus.PAUSED) return

        recordingLifecycleJob?.cancel()
        automationEngine.detachRecorderBestEffort(
            webViewRegistry[session.initiatingTabId],
            session.sessionId.ifBlank { null }
        )

        _uiState.update { state ->
            val current = state.recordingSession
            if (current.initiatingTabId !in closedTabIds) {
                state
            } else {
                state.copy(
                    recordingSession = current.copy(
                        status = RecordingStatus.CANCELLED,
                        sessionId = "",
                        recordingError = RecordingErrorInfo(
                            code = AutomationErrorCode.RECORDING_TAB_CLOSED,
                            message = "Recording cancelled because the recording tab was closed."
                        )
                    ),
                    snackbarNotice = "Recording cancelled — recording tab closed"
                )
            }
        }
        refreshAutomationToolbarStatus(clearTransientResult = true)
    }

    private fun setRecordingErrorState(
        initiatingTabId: String,
        targetDomain: String,
        targetUrl: String,
        code: AutomationErrorCode,
        message: String,
        preserveSteps: Boolean
    ) {
        _uiState.update { state ->
            val prev = state.recordingSession
            val preservedSteps = if (preserveSteps) prev.steps else emptyList()
            state.copy(
                automationHudMode = AutomationHudMode.RECORDING,
                recordingSession = ActiveRecordingSession(
                    sessionId = "",
                    initiatingTabId = initiatingTabId,
                    initialOrigin = if (preserveSteps) prev.initialOrigin else "",
                    currentOrigin = if (preserveSteps) prev.currentOrigin else "",
                    status = RecordingStatus.ERROR,
                    steps = preservedSteps,
                    targetDomain = targetDomain,
                    targetUrl = targetUrl,
                    suggestedName = if (preserveSteps) prev.suggestedName else "",
                    startedAt = if (preserveSteps) prev.startedAt else 0L,
                    lastSequenceNumber = if (preserveSteps) prev.lastSequenceNumber else 0L,
                    recordingError = RecordingErrorInfo(code = code, message = message),
                    pendingOriginChangeUrl = prev.pendingOriginChangeUrl
                ),
                snackbarNotice = message
            )
        }
        refreshAutomationToolbarStatus(clearTransientResult = false)
    }

    fun renameRecordingDraft(name: String) {
        _uiState.update {
            it.copy(recordingSession = it.recordingSession.copy(suggestedName = name))
        }
    }

    fun editRecordingStep(
        stepId: String,
        description: String,
        value: String,
        targetLabel: String? = null,
        selector: String? = null,
        credentialReferenceId: String? = null,
        credentialFieldRole: CredentialFieldRole? = null,
        requiresCredential: Boolean? = null
    ) {
        _uiState.update { state ->
            val sessionOrigin = state.recordingSession.currentOrigin.ifBlank {
                automationEngine.parseOrigin(state.recordingSession.targetUrl).canonicalOrigin
            }
            val updated = state.recordingSession.steps.map { step ->
                if (step.id == stepId) {
                    val effectiveRequiresCred = requiresCredential ?: step.requiresCredential
                    val effectiveRefId = credentialReferenceId ?: step.credentialReferenceId
                    val effectiveRole = credentialFieldRole ?: step.credentialFieldRole
                    AutomationStepCodec.applyStepEdit(
                        existing = step,
                        description = description,
                        actionType = step.actionType,
                        value = value,
                        targetLabel = targetLabel ?: step.targetLabel,
                        selector = selector ?: step.selector,
                        destructiveClassifier = automationEngine::isClauseDestructive,
                        credentialReferenceId = effectiveRefId,
                        credentialFieldRole = effectiveRole,
                        expectedOrigin = step.expectedOrigin.ifBlank { sessionOrigin },
                        requiresCredential = effectiveRequiresCred
                    )
                } else {
                    step
                }
            }
            val reindexed = AutomationStepCodec.reindex(updated)
            val updatedCandidates = if (state.recordingSession.status == RecordingStatus.STOPPED) {
                buildMergedRecordingCandidates(
                    steps = reindexed,
                    targetUrl = state.recordingSession.targetUrl,
                    previousCandidates = state.recordingVaultCandidates
                )
            } else {
                state.recordingVaultCandidates
            }
            state.copy(
                recordingSession = state.recordingSession.copy(
                    steps = reindexed
                ),
                recordingVaultCandidates = updatedCandidates
            )
        }
    }

    fun assignCredentialToRecordingStep(
        stepId: String,
        credentialId: String?,
        fieldRole: CredentialFieldRole = CredentialFieldRole.PASSWORD
    ) {
        viewModelScope.launch {
            val session = _uiState.value.recordingSession
            val sessionOrigin = session.currentOrigin.ifBlank {
                automationEngine.parseOrigin(session.targetUrl).canonicalOrigin
            }
            val cleanRef = credentialId?.trim()?.takeIf { it.isNotEmpty() }
            if (cleanRef != null) {
                val entry = credentialVaultManager.getCredentialMetadataById(cleanRef)
                if (entry == null) {
                    _uiState.update { it.copy(snackbarNotice = "Selected credential was not found in the Vault") }
                    return@launch
                }
                if (sessionOrigin.isNotBlank() && entry.websiteOrigin != sessionOrigin) {
                    _uiState.update {
                        it.copy(
                            snackbarNotice = "Origin mismatch: '${entry.label}' is bound to ${entry.displayOrigin}"
                        )
                    }
                    return@launch
                }
            }
            _uiState.update { state ->
                val updated = state.recordingSession.steps.map { step ->
                    if (step.id == stepId) {
                        step.copy(
                            value = "",
                            credentialReferenceId = cleanRef,
                            credentialFieldRole = fieldRole,
                            expectedOrigin = sessionOrigin.ifBlank { step.expectedOrigin },
                            requiresCredential = true,
                            description = AutomationStepCodec.buildStepSummary(
                                actionType = step.actionType,
                                targetLabel = step.targetLabel,
                                value = "",
                                isCredentialStep = true,
                                credentialFieldRole = fieldRole
                            )
                        )
                    } else {
                        step
                    }
                }
                state.copy(
                    recordingSession = state.recordingSession.copy(
                        steps = AutomationStepCodec.reindex(updated)
                    ),
                    snackbarNotice = if (cleanRef != null) {
                        "Bound step to saved credential (${fieldRole.label})"
                    } else {
                        "Cleared credential binding for step"
                    }
                )
            }
        }
    }

    fun assignCredentialToWorkflowStep(
        workflowId: Long,
        stepId: String,
        credentialId: String?,
        fieldRole: CredentialFieldRole = CredentialFieldRole.PASSWORD
    ) {
        viewModelScope.launch {
            val workflow = repository.getAutomationWorkflowById(workflowId)
                ?: automationWorkflowsState.value.firstOrNull { it.id == workflowId }
                ?: return@launch
            val steps = AutomationStepCodec.decode(workflow.stepsJson)
            val targetStep = steps.firstOrNull { it.id == stepId } ?: return@launch
            val wfOrigin = automationEngine.parseOrigin(workflow.sourceUrl).canonicalOrigin
                .ifBlank { targetStep.expectedOrigin }

            val cleanRef = credentialId?.trim()?.takeIf { it.isNotEmpty() }
            var resolvedOrigin = wfOrigin
            if (cleanRef != null) {
                val entry = credentialVaultManager.getCredentialMetadataById(cleanRef)
                if (entry == null) {
                    _uiState.update { it.copy(snackbarNotice = "Selected credential was not found in the Vault") }
                    return@launch
                }
                if (wfOrigin.isNotBlank() && entry.websiteOrigin != wfOrigin) {
                    _uiState.update {
                        it.copy(
                            snackbarNotice = "Origin mismatch: '${entry.label}' is bound to ${entry.displayOrigin}"
                        )
                    }
                    return@launch
                }
                resolvedOrigin = entry.websiteOrigin
            }
            val updated = steps.map { step ->
                if (step.id == stepId) {
                    step.copy(
                        value = "",
                        credentialReferenceId = cleanRef,
                        credentialFieldRole = fieldRole,
                        expectedOrigin = resolvedOrigin,
                        requiresCredential = true,
                        description = AutomationStepCodec.buildStepSummary(
                            actionType = step.actionType,
                            targetLabel = step.targetLabel,
                            value = "",
                            isCredentialStep = true,
                            credentialFieldRole = fieldRole
                        )
                    )
                } else {
                    step
                }
            }
            runCatching { repository.updateAutomationWorkflowSteps(workflowId, updated) }
                .onSuccess {
                    validateWorkflow(workflowId)
                    loadWorkflowVersions(workflowId)
                    _uiState.update {
                        it.copy(
                            snackbarNotice = if (cleanRef != null) {
                                "Bound workflow step to saved credential (${fieldRole.label})"
                            } else {
                                "Left workflow credential step unassigned"
                            }
                        )
                    }
                }
                .onFailure { ex ->
                    _uiState.update {
                        it.copy(snackbarNotice = ex.message ?: "Failed to update credential binding")
                    }
                }
        }
    }

    fun deleteRecordingStep(stepId: String) {
        recordedSecretsByStepId.remove(stepId)
        _uiState.update { state ->
            val updated = state.recordingSession.steps.filterNot { it.id == stepId }
            val reindexed = AutomationStepCodec.reindex(updated)
            state.copy(
                recordingSession = state.recordingSession.copy(
                    steps = reindexed
                ),
                recordingVaultCandidates = state.recordingVaultCandidates.filterNot { it.stepId == stepId }
            )
        }
    }

    fun saveRecordingWorkflow(customName: String? = null) {
        val stateBeforeSave = _uiState.value
        val session = stateBeforeSave.recordingSession
        val stepsSnapshot = session.steps
        if (stepsSnapshot.isEmpty()) return
        recordingLifecycleJob?.cancel()
        val name = (customName ?: session.suggestedName).trim().ifBlank {
            if (session.targetDomain.isNotBlank() && session.targetDomain != "Start Page") {
                "${session.targetDomain} Recording"
            } else {
                "Recorded Automation"
            }
        }
        val activeTab = stateBeforeSave.activeTab
        val domain = session.targetDomain.ifBlank { automationEngine.extractDomain(activeTab.url) }
        val sourceUrl = session.targetUrl.ifBlank { activeTab.url }
        val targetTabId = session.initiatingTabId.ifBlank { activeTab.id }
        val oldSessionId = session.sessionId.ifBlank { null }
        val targetWebView = webViewRegistry[targetTabId]
        val candidateSnapshot = buildMergedRecordingCandidates(
            steps = stepsSnapshot,
            targetUrl = sourceUrl,
            previousCandidates = stateBeforeSave.recordingVaultCandidates
        )
        // Synchronously invalidate the active recording session before async persistence so
        // no late DOM events from WebView can sneak in during the database write.
        automationEngine.detachRecorderBestEffort(targetWebView, oldSessionId)
        _uiState.update {
            it.copy(
                recordingSession = it.recordingSession.copy(
                    status = RecordingStatus.STOPPED,
                    sessionId = ""
                )
            )
        }
        refreshAutomationToolbarStatus(clearTransientResult = false)
        recordingLifecycleJob = viewModelScope.launch {
            val stepVaultBindings = mutableMapOf<String, Pair<String, AutomationVaultCategory>>()
            val stepPoolBindings = mutableMapOf<String, Pair<AutomationVaultCategory, String>>()
            val deselectedStepIds = candidateSnapshot.filterNot { it.isSelected }.map { it.stepId }.toSet()
            var savedToVaultCount = 0

            candidateSnapshot
                .filter { it.isSelected }
                .forEach { cand ->
                    val existingId = cand.existingEntryId?.trim()?.takeIf { it.isNotEmpty() }
                    val secretOrCaptured = cand.capturedValue
                    if (existingId != null && secretOrCaptured.isEmpty()) {
                        stepVaultBindings[cand.stepId] = existingId to cand.category
                    } else if (secretOrCaptured.isNotEmpty()) {
                        val originUrl = if (sourceUrl.startsWith("http", ignoreCase = true)) {
                            sourceUrl
                        } else {
                            ""
                        }
                        repository.automationVaultManager.saveOrUpdateEntry(
                            entryId = existingId,
                            category = cand.category,
                            label = cand.vaultLabel,
                            plainValue = secretOrCaptured,
                            websiteOrigin = originUrl,
                            isSensitive = cand.isSensitive || cand.category == AutomationVaultCategory.PASSWORD
                        ).onSuccess { savedEntry ->
                            stepVaultBindings[cand.stepId] = savedEntry.entryId to cand.category
                            savedToVaultCount++
                        }
                    } else {
                        // Field-only step recorded without typing: bind dynamically to Automation Vault pool
                        val folderId = AutomationVaultManager.defaultFolderIdForCategory(cand.category)
                        stepPoolBindings[cand.stepId] = cand.category to folderId
                    }
                }

            val finalSteps = stepsSnapshot.map { step ->
                val binding = stepVaultBindings[step.id]
                val poolBinding = stepPoolBindings[step.id]
                when {
                    binding != null -> {
                        val (entryId, cat) = binding
                        val isPwd = cat == AutomationVaultCategory.PASSWORD || step.isCredentialStep
                        step.copy(
                            value = "",
                            vaultEntryId = entryId,
                            vaultCategory = cat.id,
                            useVaultPool = false,
                            vaultFolderId = AutomationVaultManager.defaultFolderIdForCategory(cat),
                            requiresCredential = false,
                            description = AutomationStepCodec.buildStepSummary(
                                actionType = step.actionType,
                                targetLabel = step.targetLabel,
                                value = if (isPwd) "" else "${cat.label} (Vault)",
                                isCredentialStep = isPwd,
                                credentialFieldRole = step.credentialFieldRole
                            )
                        )
                    }
                    poolBinding != null && step.credentialReferenceId.isNullOrBlank() -> {
                        val (cat, folderId) = poolBinding
                        step.copy(
                            value = "",
                            vaultEntryId = null,
                            vaultCategory = cat.id,
                            useVaultPool = true,
                            vaultFolderId = folderId,
                            requiresCredential = false,
                            description = AutomationStepCodec.buildStepSummary(
                                actionType = step.actionType,
                                targetLabel = step.targetLabel,
                                value = "",
                                isCredentialStep = false,
                                credentialFieldRole = step.credentialFieldRole,
                                useVaultPool = true,
                                vaultCategoryLabel = cat.label
                            )
                        )
                    }
                    step.id in deselectedStepIds && step.useVaultPool -> {
                        step.copy(
                            useVaultPool = false,
                            vaultCategory = "",
                            vaultFolderId = ""
                        )
                    }
                    else -> step
                }
            }

            recordedSecretsByStepId.clear()

            val savedId = repository.saveAutomationWorkflow(
                name = name,
                domain = domain,
                sourceUrl = sourceUrl,
                type = AutomationType.RECORDED,
                steps = finalSteps
            )
            if (targetWebView != null) {
                automationEngine.uninstallRecorderScript(targetWebView, oldSessionId)
            }
            _uiState.update {
                it.copy(
                    recordingSession = ActiveRecordingSession(),
                    recordingVaultCandidates = emptyList(),
                    automationHudMode = AutomationHudMode.SAVED,
                    snackbarNotice = buildString {
                        append("Saved '$name' (${finalSteps.size} steps)")
                        if (savedToVaultCount > 0) {
                            append(" • $savedToVaultCount value${if (savedToVaultCount == 1) "" else "s"} in Automation Vault")
                        }
                    }
                )
            }
            refreshAutomationToolbarStatus(clearTransientResult = false)
            if (savedId > 0L) {
                refreshAutomationToolbarStatus(clearTransientResult = false)
            }
        }
    }

    fun discardRecording() {
        recordingLifecycleJob?.cancel()
        recordedSecretsByStepId.clear()
        val session = _uiState.value.recordingSession
        val targetTabId = session.initiatingTabId.ifBlank { _uiState.value.activeTabId }
        val oldSessionId = session.sessionId.ifBlank { null }
        automationEngine.detachRecorderBestEffort(webViewRegistry[targetTabId], oldSessionId)
        _uiState.update {
            it.copy(
                recordingSession = ActiveRecordingSession(),
                recordingVaultCandidates = emptyList(),
                automationHudMode = AutomationHudMode.OVERVIEW,
                snackbarNotice = "Recording discarded"
            )
        }
        refreshAutomationToolbarStatus(clearTransientResult = true)
    }

    // --- Manual Instructions Mode ---

    fun onInstructionDraftChanged(text: String) {
        _uiState.update { it.copy(instructionDraftText = text) }
    }

    fun addInstructionsToDraft() {
        val raw = _uiState.value.instructionDraftText.trim()
        if (raw.isEmpty()) return
        val activeTab = _uiState.value.activeTab
        val parsed = automationEngine.parseInstructionsToSteps(
            rawInstructions = raw,
            currentUrl = activeTab.url,
            inspectionState = _uiState.value.pageInspection
        )
        if (parsed.isEmpty()) return
        _uiState.update { state ->
            state.copy(
                instructionDraftSteps = AutomationStepCodec.reindex(state.instructionDraftSteps + parsed),
                instructionDraftText = ""
            )
        }
    }

    fun clearInstructionDraft() {
        _uiState.update {
            it.copy(
                instructionDraftText = "",
                instructionDraftSteps = emptyList()
            )
        }
    }

    fun reviewInstructionSteps(): Boolean {
        val text = _uiState.value.instructionDraftText.trim()
        if (text.isNotEmpty() && _uiState.value.instructionDraftSteps.isEmpty()) {
            addInstructionsToDraft()
        }
        return _uiState.value.instructionDraftSteps.isNotEmpty()
    }

    fun editInstructionStep(
        stepId: String,
        description: String,
        actionType: AutomationActionType,
        value: String,
        targetLabel: String? = null,
        selector: String? = null
    ) {
        _uiState.update { state ->
            val updated = state.instructionDraftSteps.map { step ->
                if (step.id == stepId) {
                    AutomationStepCodec.applyStepEdit(
                        existing = step,
                        description = description,
                        actionType = actionType,
                        value = value,
                        targetLabel = targetLabel ?: step.targetLabel,
                        selector = selector ?: step.selector,
                        destructiveClassifier = automationEngine::isClauseDestructive
                    )
                } else {
                    step
                }
            }
            state.copy(instructionDraftSteps = AutomationStepCodec.reindex(updated))
        }
    }

    fun deleteInstructionStep(stepId: String) {
        _uiState.update { state ->
            val updated = state.instructionDraftSteps.filterNot { it.id == stepId }
            state.copy(instructionDraftSteps = AutomationStepCodec.reindex(updated))
        }
    }

    fun moveInstructionStepUp(stepId: String) {
        _uiState.update { state ->
            val list = state.instructionDraftSteps.toMutableList()
            val idx = list.indexOfFirst { it.id == stepId }
            if (idx > 0) {
                val item = list.removeAt(idx)
                list.add(idx - 1, item)
            }
            state.copy(instructionDraftSteps = AutomationStepCodec.reindex(list))
        }
    }

    fun moveInstructionStepDown(stepId: String) {
        _uiState.update { state ->
            val list = state.instructionDraftSteps.toMutableList()
            val idx = list.indexOfFirst { it.id == stepId }
            if (idx in 0 until list.lastIndex) {
                val item = list.removeAt(idx)
                list.add(idx + 1, item)
            }
            state.copy(instructionDraftSteps = AutomationStepCodec.reindex(list))
        }
    }

    fun saveInstructionDraftAsWorkflow(name: String) {
        if (_uiState.value.instructionDraftText.isNotBlank() && _uiState.value.instructionDraftSteps.isEmpty()) {
            addInstructionsToDraft()
        }
        val state = _uiState.value
        val steps = state.instructionDraftSteps
        if (steps.isEmpty()) return
        val activeTab = state.activeTab
        val domain = automationEngine.extractDomain(activeTab.url)
        val cleanName = name.trim().ifBlank {
            if (domain == "Start Page") "Manual Automation" else "$domain Instructions"
        }
        val summaryText = steps.joinToString("\n") { it.description }
        viewModelScope.launch {
            repository.saveAutomationWorkflow(
                name = cleanName,
                domain = domain,
                sourceUrl = activeTab.url,
                type = AutomationType.MANUAL,
                steps = steps,
                instructionsText = summaryText
            )
            _uiState.update {
                it.copy(
                    snackbarNotice = "Saved '$cleanName' (${steps.size} steps)"
                )
            }
        }
    }

    fun executeInstructionDraft(confirmDestructive: Boolean = false) {
        if (automationExecutionJob?.isActive == true ||
            _uiState.value.executionState?.executionStatus?.isActive == true
        ) {
            return
        }
        if (_uiState.value.instructionDraftText.isNotBlank() && _uiState.value.instructionDraftSteps.isEmpty()) {
            addInstructionsToDraft()
        }
        val steps = _uiState.value.instructionDraftSteps
        if (steps.isEmpty()) return
        val destructiveSteps = steps.filter { it.isDestructive }
        if (destructiveSteps.isNotEmpty() && !confirmDestructive) {
            pendingDestructiveInstructionConfirm = true
            pendingDestructiveWorkflowId = null
            _uiState.update {
                it.copy(
                    executionState = WorkflowExecutionState(
                        workflowId = 0L,
                        workflowName = "Manual Instructions",
                        status = "AWAITING_CONFIRMATION",
                        currentStepIndex = 0,
                        totalSteps = steps.size,
                        stepLogs = emptyList(),
                        verificationMessage = "This workflow includes potentially destructive actions.",
                        isAwaitingConfirmation = true,
                        pendingDestructiveSteps = destructiveSteps
                    )
                )
            }
            return
        }

        val activeTab = _uiState.value.activeTab
        val domain = automationEngine.extractDomain(activeTab.url)
        val summaryText = steps.joinToString("\n") { it.description }
        viewModelScope.launch {
            if (automationExecutionJob?.isActive == true ||
                _uiState.value.executionState?.executionStatus?.isActive == true
            ) {
                return@launch
            }
            if (!ensureAutomationPlayQuotaOrPrompt()) {
                return@launch
            }
            val savedId = repository.saveAutomationWorkflow(
                name = if (domain == "Start Page") "Manual Run" else "$domain Task",
                domain = domain,
                sourceUrl = activeTab.url,
                type = AutomationType.MANUAL,
                steps = steps,
                instructionsText = summaryText
            )
            runWorkflow(savedId, confirmDestructive = true)
        }
    }

    fun dismissPendingDisambiguation() {
        _uiState.update { it.copy(pendingDisambiguation = null) }
    }

    private fun selectPrimaryFailedStepRecord(records: List<StepExecutionRecord>): StepExecutionRecord? {
        return records.firstOrNull {
            it.status == "FAILED" &&
                it.errorCode == AutomationErrorCode.ELEMENT_AMBIGUOUS &&
                it.ambiguousCandidates.isNotEmpty()
        } ?: records.firstOrNull {
            it.status == "FAILED" && it.errorCode == AutomationErrorCode.ELEMENT_AMBIGUOUS
        } ?: records.firstOrNull { it.status == "FAILED" }
            ?: records.lastOrNull()
    }

    fun openDisambiguationForExecution() {
        val state = _uiState.value
        val exec = state.executionState ?: return
        val failedRecord = selectPrimaryFailedStepRecord(exec.stepRecords)
        val candidates = failedRecord?.ambiguousCandidates?.ifEmpty { exec.ambiguousCandidates }
            ?: exec.ambiguousCandidates
        if (candidates.isEmpty()) return
        val failedStepId = failedRecord?.stepId.orEmpty()
        val diagnostics = failedRecord?.locatorDiagnostics ?: exec.lastLocatorDiagnostics ?: state.lastLocatorDiagnostics
        _uiState.update {
            it.copy(
                pendingDisambiguation = PendingDisambiguationState(
                    source = DisambiguationSource.EXECUTION,
                    workflowId = exec.workflowId.takeIf { id -> id > 0L },
                    stepId = failedStepId,
                    stepOrder = failedRecord?.stepOrder ?: (exec.currentStepIndex + 1),
                    stepDescription = exec.currentStepDescription.ifBlank { exec.workflowName },
                    actionType = failedRecord?.actionType ?: AutomationActionType.CLICK,
                    originalTarget = diagnostics?.targetLabel?.takeIf { t -> t.isNotBlank() }
                        ?: diagnostics?.recordedLocator?.takeIf { r -> r.isNotBlank() }
                        ?: exec.currentStepDescription,
                    originalSelector = diagnostics?.recordedLocator.orEmpty(),
                    candidates = candidates,
                    diagnostics = diagnostics
                )
            )
        }
    }

    fun resolveAmbiguousLocator(
        candidate: AmbiguousCandidateInfo,
        rerunAfterSelection: Boolean = true
    ) {
        val pending = _uiState.value.pendingDisambiguation ?: return
        val refinedSel = candidate.recommendedSelector.trim()
        if (refinedSel.isBlank()) {
            _uiState.update { it.copy(pendingDisambiguation = null) }
            return
        }
        val refinedStrategy = when {
            refinedSel.contains(">>>") -> LocatorStrategy.SHADOW_DOM_CHAIN
            refinedSel.contains(">>") -> LocatorStrategy.IFRAME_CHAIN
            refinedSel.startsWith("#") || (refinedSel.contains("#") && !refinedSel.contains(" ")) -> LocatorStrategy.STABLE_ID
            refinedSel.contains("[data-test") || refinedSel.contains("[data-qa") || refinedSel.contains("[data-cy") -> LocatorStrategy.STABLE_DATA_ATTR
            refinedSel.contains("[aria-label") -> LocatorStrategy.ARIA_ROLE_AND_NAME
            refinedSel.contains("[name=") -> LocatorStrategy.STABLE_NAME
            refinedSel.contains("[href=") -> LocatorStrategy.HREF_ATTR
            refinedSel.contains("[placeholder=") -> LocatorStrategy.PLACEHOLDER_ATTR
            else -> LocatorStrategy.SCOPED_CSS
        }
        val newCand = LocatorCandidate(
            strategy = refinedStrategy,
            selector = refinedSel,
            scope = when {
                candidate.shadowHostPath.isNotEmpty() -> "SHADOW"
                candidate.framePath.isNotEmpty() -> "IFRAME"
                else -> "DOCUMENT"
            },
            confidence = 0.98f,
            isUnique = true,
            matchCount = 1,
            isAmbiguous = false,
            stability = LocatorStability.HIGH
        )
        val refinedLabel = candidate.accessibleName.ifBlank { candidate.visibleText }

        fun refineStep(existing: AutomationStep): AutomationStep {
            val remaining = existing.locatorCandidates.filterNot {
                it.selector == refinedSel || it.selector == existing.selector
            }
            return existing.copy(
                selector = refinedSel,
                targetLabel = if (refinedLabel.isNotBlank()) refinedLabel else existing.targetLabel,
                locatorCandidates = listOf(newCand) + remaining,
                framePath = candidate.framePath.ifEmpty { existing.framePath },
                shadowHostPath = candidate.shadowHostPath.ifEmpty { existing.shadowHostPath }
            )
        }

        fun refineNestedSteps(
            steps: List<AutomationStep>,
            targetStepId: String
        ): Pair<List<AutomationStep>, Boolean> {
            var updatedAny = false
            val mapped = steps.map { step ->
                if (step.id == targetStepId) {
                    updatedAny = true
                    refineStep(step)
                } else {
                    var current = step
                    if (current.thenSteps.isNotEmpty()) {
                        val (thenUpdated, found) = refineNestedSteps(current.thenSteps, targetStepId)
                        if (found) {
                            updatedAny = true
                            current = current.copy(thenSteps = thenUpdated)
                        }
                    }
                    if (current.elseSteps.isNotEmpty()) {
                        val (elseUpdated, found) = refineNestedSteps(current.elseSteps, targetStepId)
                        if (found) {
                            updatedAny = true
                            current = current.copy(elseSteps = elseUpdated)
                        }
                    }
                    if (current.elseIfBranches.isNotEmpty()) {
                        val updatedBranches = current.elseIfBranches.map { branch ->
                            val (bSteps, found) = refineNestedSteps(branch.steps, targetStepId)
                            if (found) {
                                updatedAny = true
                                branch.copy(steps = bSteps)
                            } else {
                                branch
                            }
                        }
                        current = current.copy(elseIfBranches = updatedBranches)
                    }
                    val rp = current.recoveryPolicy
                    if (rp != null && rp.alternativeSteps.isNotEmpty()) {
                        val (altUpdated, found) = refineNestedSteps(rp.alternativeSteps, targetStepId)
                        if (found) {
                            updatedAny = true
                            current = current.copy(recoveryPolicy = rp.copy(alternativeSteps = altUpdated))
                        }
                    }
                    current
                }
            }
            return mapped to updatedAny
        }

        when (pending.source) {
            DisambiguationSource.RECORDING -> {
                _uiState.update { state ->
                    val (updated, _) = refineNestedSteps(state.recordingSession.steps, pending.stepId)
                    state.copy(
                        recordingSession = state.recordingSession.copy(
                            steps = AutomationStepCodec.reindex(updated)
                        ),
                        pendingDisambiguation = null,
                        snackbarNotice = "Updated recorded target locator"
                    )
                }
            }
            DisambiguationSource.INSTRUCTION_DRAFT -> {
                _uiState.update { state ->
                    val (updated, _) = refineNestedSteps(state.instructionDraftSteps, pending.stepId)
                    state.copy(
                        instructionDraftSteps = AutomationStepCodec.reindex(updated),
                        pendingDisambiguation = null,
                        snackbarNotice = "Updated draft step locator"
                    )
                }
            }
            DisambiguationSource.EXECUTION -> {
                val wfId = pending.workflowId ?: run {
                    _uiState.update { it.copy(pendingDisambiguation = null) }
                    return
                }
                viewModelScope.launch {
                    val wf = repository.getAutomationWorkflowById(wfId) ?: run {
                        _uiState.update { it.copy(pendingDisambiguation = null) }
                        return@launch
                    }
                    fun collectSubWorkflowIds(stepList: List<AutomationStep>): List<Long> {
                        val ids = mutableListOf<Long>()
                        stepList.forEach { s ->
                            s.subWorkflowRef?.targetWorkflowId?.takeIf { id -> id > 0L }?.let { ids.add(it) }
                            if (s.thenSteps.isNotEmpty()) ids.addAll(collectSubWorkflowIds(s.thenSteps))
                            if (s.elseSteps.isNotEmpty()) ids.addAll(collectSubWorkflowIds(s.elseSteps))
                            s.elseIfBranches.forEach { br ->
                                if (br.steps.isNotEmpty()) ids.addAll(collectSubWorkflowIds(br.steps))
                            }
                            s.recoveryPolicy?.alternativeSteps?.let { alt ->
                                if (alt.isNotEmpty()) ids.addAll(collectSubWorkflowIds(alt))
                            }
                        }
                        return ids
                    }

                    val steps = AutomationStepCodec.decode(wf.stepsJson)
                    val (nestedUpdated, foundInTree) = if (pending.stepId.isNotBlank()) {
                        refineNestedSteps(steps, pending.stepId)
                    } else {
                        steps to false
                    }
                    var refinedAnyWorkflow = false
                    if (foundInTree) {
                        repository.updateAutomationWorkflowSteps(
                            id = wfId,
                            steps = AutomationStepCodec.reindex(nestedUpdated),
                            changeSummary = "Disambiguated target locator to '$refinedSel'"
                        )
                        refinedAnyWorkflow = true
                    } else if (pending.stepId.isNotBlank()) {
                        val visited = mutableSetOf(wfId)
                        val queue = ArrayDeque(collectSubWorkflowIds(steps))
                        while (queue.isNotEmpty()) {
                            val subId = queue.removeFirst()
                            if (!visited.add(subId)) continue
                            val subWf = repository.getAutomationWorkflowById(subId) ?: continue
                            val subSteps = AutomationStepCodec.decode(subWf.stepsJson)
                            val (subUpdated, subFound) = refineNestedSteps(subSteps, pending.stepId)
                            if (subFound) {
                                repository.updateAutomationWorkflowSteps(
                                    id = subId,
                                    steps = AutomationStepCodec.reindex(subUpdated),
                                    changeSummary = "Disambiguated target locator to '$refinedSel'"
                                )
                                refinedAnyWorkflow = true
                                break
                            }
                            collectSubWorkflowIds(subSteps).forEach { nestedSubId ->
                                if (nestedSubId !in visited) queue.addLast(nestedSubId)
                            }
                        }
                    } else if (steps.isNotEmpty()) {
                        val fallbackSteps = steps.mapIndexed { idx, step ->
                            if (idx == 0) refineStep(step) else step
                        }
                        repository.updateAutomationWorkflowSteps(
                            id = wfId,
                            steps = AutomationStepCodec.reindex(fallbackSteps),
                            changeSummary = "Disambiguated target locator to '$refinedSel'"
                        )
                        refinedAnyWorkflow = true
                    }
                    _uiState.update { state ->
                        state.copy(
                            pendingDisambiguation = null,
                            executionState = state.executionState?.copy(
                                ambiguousCandidates = emptyList()
                            ),
                            snackbarNotice = if (refinedAnyWorkflow) {
                                "Updated step target locator"
                            } else {
                                "Target step could not be located for refinement"
                            }
                        )
                    }
                    if (rerunAfterSelection && refinedAnyWorkflow) {
                        runWorkflow(wfId, confirmDestructive = true)
                    }
                }
            }
        }
    }

    // --- Unified Workspace, Details & Execution Engine ---

    fun createManualWorkflow(
        name: String,
        domain: String,
        sourceUrl: String,
        instructionsText: String,
        steps: List<AutomationStep>
    ) {
        val resolvedSteps = if (steps.isNotEmpty()) {
            steps
        } else {
            automationEngine.parseInstructionsToSteps(
                rawInstructions = instructionsText,
                currentUrl = sourceUrl,
                inspectionState = _uiState.value.pageInspection
            )
        }
        if (resolvedSteps.isEmpty()) return
        val activeUrl = _uiState.value.activeTab.url
        val resolvedUrl = sourceUrl.trim().ifBlank { activeUrl }
        val resolvedDomain = domain.trim().ifBlank { automationEngine.extractDomain(resolvedUrl) }
        val cleanName = name.trim().ifBlank { "$resolvedDomain Workflow" }
        viewModelScope.launch {
            val newId = repository.saveAutomationWorkflow(
                name = cleanName,
                domain = resolvedDomain,
                sourceUrl = resolvedUrl,
                type = AutomationType.MANUAL,
                steps = resolvedSteps,
                instructionsText = instructionsText
            )
            _uiState.update {
                it.copy(
                    selectedAutomationWorkflowId = newId,
                    snackbarNotice = "Created automation '$cleanName'"
                )
            }
        }
    }

    fun renameWorkflow(workflowId: Long, newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.renameAutomationWorkflow(workflowId, clean) }
                .onSuccess {
                    _uiState.update { it.copy(snackbarNotice = "Renamed automation to '$clean'") }
                    loadWorkflowVersions(workflowId)
                }
                .onFailure { ex ->
                    _uiState.update { it.copy(snackbarNotice = ex.message ?: "Failed to rename automation") }
                }
        }
    }

    fun duplicateWorkflow(workflowId: Long) {
        viewModelScope.launch {
            val copyId = repository.duplicateAutomationWorkflow(workflowId)
            if (copyId != null) {
                _uiState.update { it.copy(snackbarNotice = "Duplicated automation") }
            }
        }
    }

    fun deleteWorkflow(workflowId: Long) {
        viewModelScope.launch {
            repository.deleteAutomationWorkflow(workflowId)
            _uiState.update {
                it.copy(
                    selectedAutomationWorkflowId = if (it.selectedAutomationWorkflowId == workflowId) {
                        null
                    } else {
                        it.selectedAutomationWorkflowId
                    },
                    snackbarNotice = "Automation deleted"
                )
            }
        }
    }

    fun updateWorkflowSteps(workflowId: Long, steps: List<AutomationStep>) {
        viewModelScope.launch {
            runCatching { repository.updateAutomationWorkflowSteps(workflowId, steps) }
                .onSuccess {
                    validateWorkflow(workflowId)
                    loadWorkflowVersions(workflowId)
                }
                .onFailure { ex ->
                    _uiState.update { it.copy(snackbarNotice = ex.message ?: "Failed to update steps") }
                }
        }
    }

    fun updateWorkflowDocument(
        workflowId: Long,
        steps: List<AutomationStep>,
        metadata: WorkflowDocumentMetadata
    ) {
        viewModelScope.launch {
            runCatching {
                repository.saveAutomationWorkflowDocument(
                    id = workflowId,
                    document = WorkflowDocument(metadata = metadata, steps = steps)
                )
            }.onSuccess {
                validateWorkflow(workflowId)
                loadWorkflowVersions(workflowId)
            }.onFailure { ex ->
                _uiState.update { it.copy(snackbarNotice = ex.message ?: "Failed to update workflow") }
            }
        }
    }

    fun addWorkflowVariable(workflowId: Long, variable: WorkflowVariableDefinition) {
        val cleanName = variable.name.trim()
        if (!WorkflowVariableStore.isValidVariableName(cleanName)) {
            _uiState.update { it.copy(snackbarNotice = "Invalid variable name '$cleanName'") }
            return
        }
        if (WorkflowVariableStore.isBlockedCredentialVariableName(cleanName)) {
            _uiState.update { it.copy(snackbarNotice = "Password/secret names cannot be stored in variables") }
            return
        }
        viewModelScope.launch {
            runCatching {
                val wf = repository.getAutomationWorkflowById(workflowId) ?: return@launch
                val doc = AutomationStepCodec.decodeDocument(wf.stepsJson)
                val updatedVars = doc.metadata.variables.filterNot { it.name == cleanName } +
                    variable.copy(name = cleanName)
                repository.saveAutomationWorkflowDocument(
                    id = workflowId,
                    document = doc.copy(metadata = doc.metadata.copy(variables = updatedVars))
                )
            }.onSuccess {
                validateWorkflow(workflowId)
                loadWorkflowVersions(workflowId)
                _uiState.update { it.copy(snackbarNotice = "Saved variable \${$cleanName}") }
            }.onFailure { ex ->
                _uiState.update { it.copy(snackbarNotice = ex.message ?: "Failed to save variable") }
            }
        }
    }

    fun removeWorkflowVariable(workflowId: Long, variableName: String) {
        viewModelScope.launch {
            runCatching {
                val wf = repository.getAutomationWorkflowById(workflowId) ?: return@launch
                val doc = AutomationStepCodec.decodeDocument(wf.stepsJson)
                val updatedVars = doc.metadata.variables.filterNot { it.name == variableName }
                repository.saveAutomationWorkflowDocument(
                    id = workflowId,
                    document = doc.copy(metadata = doc.metadata.copy(variables = updatedVars))
                )
            }.onSuccess {
                validateWorkflow(workflowId)
                loadWorkflowVersions(workflowId)
            }.onFailure { ex ->
                _uiState.update { it.copy(snackbarNotice = ex.message ?: "Failed to remove variable") }
            }
        }
    }

    fun updateWorkflowDefaultRecovery(workflowId: Long, policy: RecoveryPolicy?) {
        viewModelScope.launch {
            runCatching {
                val wf = repository.getAutomationWorkflowById(workflowId) ?: return@launch
                val doc = AutomationStepCodec.decodeDocument(wf.stepsJson)
                repository.saveAutomationWorkflowDocument(
                    id = workflowId,
                    document = doc.copy(metadata = doc.metadata.copy(defaultRecoveryPolicy = policy))
                )
            }.onSuccess {
                validateWorkflow(workflowId)
                loadWorkflowVersions(workflowId)
                _uiState.update {
                    it.copy(
                        snackbarNotice = if (policy != null) "Default recovery policy saved" else "Default recovery cleared"
                    )
                }
            }.onFailure { ex ->
                _uiState.update { it.copy(snackbarNotice = ex.message ?: "Failed to update default recovery") }
            }
        }
    }

    fun addPhase6StepToWorkflow(workflowId: Long, step: AutomationStep) {
        viewModelScope.launch {
            runCatching {
                val wf = repository.getAutomationWorkflowById(workflowId) ?: return@launch
                val doc = AutomationStepCodec.decodeDocument(wf.stepsJson)
                val newOrder = doc.steps.size + 1
                repository.saveAutomationWorkflowDocument(
                    id = workflowId,
                    document = doc.copy(steps = doc.steps + step.copy(order = newOrder))
                )
            }.onSuccess {
                validateWorkflow(workflowId)
                loadWorkflowVersions(workflowId)
            }.onFailure { ex ->
                _uiState.update { it.copy(snackbarNotice = ex.message ?: "Failed to add workflow step") }
            }
        }
    }

    fun addStepToWorkflow(
        workflowId: Long,
        actionType: AutomationActionType,
        targetLabel: String,
        value: String,
        description: String
    ) {
        val wf = automationWorkflowsState.value.firstOrNull { it.id == workflowId } ?: return
        val existing = AutomationStepCodec.decode(wf.stepsJson)
        val cleanDesc = description.trim().ifBlank {
            AutomationStepCodec.buildStepSummary(actionType, targetLabel, value)
        }
        val cleanLabel = targetLabel.trim().ifBlank { cleanDesc }
        val newStep = AutomationStep(
            id = "step_${UUID.randomUUID()}",
            order = existing.size + 1,
            actionType = actionType,
            targetLabel = cleanLabel,
            selector = "",
            value = value,
            description = cleanDesc,
            isDestructive = actionType == AutomationActionType.SUBMIT ||
                automationEngine.isClauseDestructive(cleanDesc.lowercase()) ||
                automationEngine.isClauseDestructive(cleanLabel.lowercase())
        )
        updateWorkflowSteps(workflowId, existing + newStep)
    }

    fun updateWorkflowReplaySettings(
        workflowId: Long,
        repeatCount: Int,
        clearDataAfterRun: Boolean
    ) {
        val normalizedRepeat = repeatCount.coerceIn(1, 500)
        viewModelScope.launch {
            runCatching {
                val wf = repository.getAutomationWorkflowById(workflowId) ?: return@launch
                val doc = AutomationStepCodec.decodeDocument(wf.stepsJson)
                repository.saveAutomationWorkflowDocument(
                    id = workflowId,
                    document = doc.copy(
                        metadata = doc.metadata.copy(
                            repeatCount = normalizedRepeat,
                            clearDataAfterRun = clearDataAfterRun
                        )
                    )
                )
            }.onSuccess {
                validateWorkflow(workflowId)
                loadWorkflowVersions(workflowId)
                _uiState.update {
                    it.copy(
                        snackbarNotice = "Saved replay settings (${normalizedRepeat}x${if (clearDataAfterRun) ", clear data after each run" else ""})"
                    )
                }
            }.onFailure { ex ->
                _uiState.update { it.copy(snackbarNotice = ex.message ?: "Failed to save replay settings") }
            }
        }
    }

    fun dismissVaultExhaustionPrompt() {
        _uiState.update { it.copy(vaultExhaustionPromptState = null) }
    }

    fun openVaultFromExhaustionPrompt() {
        val prompt = _uiState.value.vaultExhaustionPromptState
        _uiState.update { it.copy(vaultExhaustionPromptState = null) }
        setAutomationVaultOpen(open = true, initialFolderId = prompt?.folderId)
    }

    fun generateValuesForExhaustedPrompt(
        count: Int = 5,
        autoReplayAfter: Boolean = false
    ) {
        val prompt = _uiState.value.vaultExhaustionPromptState ?: return
        val safeCount = count.coerceIn(1, 50)
        viewModelScope.launch {
            val allFolders = repository.automationVaultManager.getAllFoldersOnce()
            val targetFolder = allFolders.firstOrNull { it.folderId == prompt.folderId }
                ?: AutomationVaultManager.DEFAULT_FOLDERS.firstOrNull {
                    it.categoryId.equals(prompt.category.id, ignoreCase = true)
                }
                ?: AutomationVaultFolderEntity(
                    folderId = prompt.folderId.ifBlank { AutomationVaultManager.defaultFolderIdForCategory(prompt.category) },
                    name = prompt.folderName.ifBlank { prompt.category.label },
                    categoryId = prompt.category.id,
                    isDefault = true,
                    createdAt = 0L,
                    updatedAt = 0L
                )
            val existingEntries = repository.automationVaultManager.getAllEntriesOnce()
                .filter { AutomationVaultManager.resolveEffectiveFolderId(it) == targetFolder.folderId }
            val existingValues = mutableSetOf<String>()
            for (entry in existingEntries) {
                repository.automationVaultManager.decryptEntryValue(entry.entryId, markUsed = false)
                    .onSuccess { existingValues.add(it) }
            }
            val generatorType = VaultValueGenerator.analyzeFolder(targetFolder).type
            var generatedCount = 0
            val activeUrl = _uiState.value.activeTab.url.takeIf { it.startsWith("http", ignoreCase = true) }.orEmpty()
            repeat(safeCount) { idx ->
                val (_, generatedVal) = VaultValueGenerator.generateValue(
                    folder = targetFolder,
                    overrideType = generatorType,
                    existingValues = existingValues
                )
                existingValues.add(generatedVal)
                val cat = AutomationVaultCategory.fromId(targetFolder.categoryId)
                repository.automationVaultManager.saveOrUpdateEntry(
                    category = cat,
                    label = "${cat.label} #${existingEntries.size + idx + 1}",
                    plainValue = generatedVal,
                    websiteOrigin = activeUrl,
                    isSensitive = cat.isSensitiveDefault,
                    folderId = targetFolder.folderId
                ).onSuccess { generatedCount++ }
            }
            _uiState.update {
                it.copy(
                    vaultExhaustionPromptState = null,
                    snackbarNotice = "Generated $generatedCount ${prompt.category.label} value(s) in '${targetFolder.name}'"
                )
            }
            if (autoReplayAfter && prompt.workflowId > 0L) {
                val remaining = (prompt.totalRuns - (prompt.failedRunIndex - 1)).coerceAtLeast(1)
                replayWorkflow(
                    workflowId = prompt.workflowId,
                    options = WorkflowPlayOptions(
                        repeatCount = remaining,
                        clearDataAfterRun = _uiState.value.executionState?.clearDataAfterRun ?: false
                    )
                )
            }
        }
    }

    private suspend fun clearBrowserDataForAutomation(webView: WebView?): StepExecutionResult {
        runCatching {
            CookieManager.getInstance().apply {
                removeAllCookies(null)
                removeSessionCookies(null)
                flush()
            }
        }
        runCatching {
            WebStorage.getInstance().deleteAllData()
        }
        withContext(Dispatchers.Main) {
            runCatching {
                webView?.clearCache(true)
                webView?.clearFormData()
                webView?.clearHistory()
            }
        }
        if (webView != null) {
            val clearDomJs = """
                (function() {
                  try { if (window.localStorage) window.localStorage.clear(); } catch (_) {}
                  try { if (window.sessionStorage) window.sessionStorage.clear(); } catch (_) {}
                  try {
                    if (document.cookie) {
                      document.cookie.split(';').forEach(function(c) {
                        var name = c.split('=')[0].trim();
                        if (name) {
                          document.cookie = name + '=;expires=Thu, 01 Jan 1970 00:00:00 GMT;path=/';
                        }
                      });
                    }
                  } catch (_) {}
                  try {
                    if (window.caches && window.caches.keys) {
                      window.caches.keys().then(function(keys) {
                        keys.forEach(function(k) { try { window.caches.delete(k); } catch (_) {} });
                      });
                    }
                  } catch (_) {}
                  try {
                    if (window.indexedDB && window.indexedDB.databases) {
                      window.indexedDB.databases().then(function(dbs) {
                        (dbs || []).forEach(function(db) {
                          if (db && db.name) { try { window.indexedDB.deleteDatabase(db.name); } catch (_) {} }
                        });
                      });
                    }
                  } catch (_) {}
                  try {
                    document.querySelectorAll('form').forEach(function(f) { try { f.reset(); } catch (_) {} });
                  } catch (_) {}
                  return JSON.stringify({ ok: true });
                })();
            """.trimIndent()
            runCatching {
                automationEngine.evalJsWithResultInternal(webView, clearDomJs, timeoutMs = 1_500L)
            }
        }
        return StepExecutionResult(
            success = true,
            message = "Cleared browser data (cookies, cache & storage).",
            verified = true
        )
    }

    fun replayWorkflow(workflowId: Long, options: WorkflowPlayOptions? = null) {
        if (automationExecutionJob?.isActive == true ||
            _uiState.value.executionState?.executionStatus?.isActive == true
        ) {
            return
        }
        pendingDestructiveWorkflowId = null
        pendingDestructiveInstructionConfirm = false
        pendingDestructivePlayOptions = null
        pendingCredentialPlayOptions = null
        pendingInterruptedResumeCheckpoint = null
        _uiState.update {
            it.copy(
                pendingCredentialReplayAuth = null,
                vaultExhaustionPromptState = null,
                executionState = null
            )
        }
        runWorkflow(workflowId = workflowId, playOptions = options)
    }

    fun runWorkflow(
        workflowId: Long,
        confirmDestructive: Boolean = false,
        confirmCredentialReplay: Boolean = false,
        authorizedCredentialIds: Set<String> = emptySet(),
        resumeCheckpoint: PersistedExecutionCheckpoint? = null,
        confirmedUnsafeRetry: Boolean = false,
        playOptions: WorkflowPlayOptions? = null
    ) {
        if (automationExecutionJob?.isActive == true ||
            _uiState.value.executionState?.executionStatus?.isActive == true
        ) {
            return
        }
        toolbarCompletedResetJob?.cancel()

        automationExecutionJob = viewModelScope.launch {
            val currentJob = coroutineContext[Job]
            try {
                if (resumeCheckpoint == null && !ensureAutomationPlayQuotaOrPrompt()) {
                    return@launch
                }
                val workflow = repository.getAutomationWorkflowById(workflowId)
                    ?: automationWorkflowsState.value.firstOrNull { it.id == workflowId }
                    ?: return@launch

                if (workflow.isArchived) {
                    val archivedMsg = "Archived workflow '${workflow.name}' cannot run until restored from Archive."
                    _uiState.update {
                        it.copy(
                            executionState = WorkflowExecutionState(
                                workflowId = workflow.id,
                                workflowName = workflow.name,
                                status = "FAILED",
                                currentStepIndex = 0,
                                totalSteps = workflow.stepCount,
                                verificationMessage = archivedMsg,
                                errorCode = AutomationErrorCode.WORKFLOW_ARCHIVED
                            ),
                            automationToolbarStatus = AutomationToolbarStatus.ERROR,
                            snackbarNotice = archivedMsg
                        )
                    }
                    return@launch
                }

                val decodeResult = AutomationStepCodec.decodeSafely(workflow.stepsJson)
                val (steps, docMetadata) = when (decodeResult) {
                    is AutomationStepDecodeResult.Success -> decodeResult.steps to decodeResult.metadata
                    is AutomationStepDecodeResult.UnsupportedVersion -> {
                        _uiState.update {
                            it.copy(
                                executionState = WorkflowExecutionState(
                                    workflowId = workflow.id,
                                    workflowName = workflow.name,
                                    status = "FAILED",
                                    currentStepIndex = 0,
                                    totalSteps = 0,
                                    verificationMessage = decodeResult.message,
                                    errorCode = AutomationErrorCode.UNSUPPORTED_SCHEMA
                                ),
                                automationToolbarStatus = AutomationToolbarStatus.ERROR
                            )
                        }
                        return@launch
                    }
                    is AutomationStepDecodeResult.MalformedJson -> {
                        _uiState.update {
                            it.copy(
                                executionState = WorkflowExecutionState(
                                    workflowId = workflow.id,
                                    workflowName = workflow.name,
                                    status = "FAILED",
                                    currentStepIndex = 0,
                                    totalSteps = 0,
                                    verificationMessage = decodeResult.message,
                                    errorCode = AutomationErrorCode.UNSUPPORTED_SCHEMA
                                ),
                                automationToolbarStatus = AutomationToolbarStatus.ERROR
                            )
                        }
                        return@launch
                    }
                }

                val effectiveRepeatCount = if (resumeCheckpoint != null) {
                    1
                } else {
                    (playOptions?.repeatCount ?: docMetadata.repeatCount).coerceIn(1, 500)
                }
                val effectiveClearData = playOptions?.clearDataAfterRun ?: docMetadata.clearDataAfterRun

                if (steps.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            executionState = WorkflowExecutionState(
                                workflowId = workflow.id,
                                workflowName = workflow.name,
                                status = "FAILED",
                                currentStepIndex = 0,
                                totalSteps = 0,
                                verificationMessage = "This workflow has no steps to run.",
                                errorCode = AutomationErrorCode.EMPTY_WORKFLOW
                            ),
                            automationToolbarStatus = AutomationToolbarStatus.ERROR
                        )
                    }
                    return@launch
                }

                val workflowDoc = WorkflowDocument(metadata = docMetadata, steps = steps)
                val subWorkflowLookup: suspend (Long) -> Pair<String, WorkflowDocument>? = { subId ->
                    repository.getAutomationWorkflowById(subId)?.let { sub ->
                        sub.name to AutomationStepCodec.decodeDocument(sub.stepsJson)
                    }
                }
                val validationResult = WorkflowValidator.validateWorkflow(
                    rootWorkflowId = workflow.id,
                    document = workflowDoc,
                    workflowLookup = subWorkflowLookup,
                    preSeededVariableNames = resumeCheckpoint?.safeVariables?.keys.orEmpty(),
                    engine = automationEngine
                )
                if (!validationResult.isValid) {
                    val execId = repository.recordAutomationExecutionStart(workflow, steps.size)
                    repository.finishAutomationExecution(
                        workflowId = workflow.id,
                        executionId = execId,
                        status = "FAILED",
                        completedSteps = 0,
                        totalSteps = steps.size,
                        verificationSummary = validationResult.message
                    )
                    _uiState.update {
                        it.copy(
                            automationToolbarStatus = AutomationToolbarStatus.ERROR,
                            executionState = WorkflowExecutionState(
                                workflowId = workflow.id,
                                workflowName = workflow.name,
                                status = "FAILED",
                                currentStepIndex = 0,
                                totalSteps = steps.size,
                                stepLogs = listOf(validationResult.message),
                                verificationMessage = validationResult.message,
                                errorCode = validationResult.errorCode
                            ),
                            snackbarNotice = validationResult.message
                        )
                    }
                    return@launch
                }

                val targetTab = if (resumeCheckpoint != null && resumeCheckpoint.tabId.isNotBlank()) {
                    _uiState.value.tabs.firstOrNull { it.id == resumeCheckpoint.tabId } ?: _uiState.value.activeTab
                } else {
                    _uiState.value.activeTab
                }
                val targetTabId = targetTab.id
                val redirectTargetUrl = Phase6ExecutionCoordinator.resolveWorkflowStartUrl(workflow, steps)
                val effectiveStartUrlForRun = if (resumeCheckpoint == null && redirectTargetUrl.isNotBlank()) {
                    redirectTargetUrl
                } else {
                    targetTab.url
                }
                val allStepsForPrecheck = validationResult.allNestedSteps.ifEmpty {
                    steps.flatMap { it.collectNestedSteps() }
                }
                val credentialSteps = allStepsForPrecheck.filter { it.isCredentialStep && it.vaultEntryId.isNullOrBlank() }
                val automationVaultSteps = allStepsForPrecheck.filter { !it.vaultEntryId.isNullOrBlank() }
                val destructiveSteps = allStepsForPrecheck.filter { it.isDestructive }

                // Validate Automation Vault entries referenced by steps before starting replay
                if (automationVaultSteps.isNotEmpty()) {
                    for (vStep in automationVaultSteps) {
                        val refId = vStep.vaultEntryId!!.trim()
                        val vaultEntry = repository.automationVaultManager.getEntryById(refId)
                        if (vaultEntry == null) {
                            val missingMsg = "Automation Vault value for step ${vStep.order} ('${vStep.description}') is missing or deleted. Update the step or add it in Automation Vault."
                            val execId = repository.recordAutomationExecutionStart(workflow, steps.size)
                            repository.finishAutomationExecution(
                                workflowId = workflow.id,
                                executionId = execId,
                                status = "FAILED",
                                completedSteps = 0,
                                totalSteps = steps.size,
                                verificationSummary = missingMsg
                            )
                            _uiState.update {
                                it.copy(
                                    automationToolbarStatus = AutomationToolbarStatus.ERROR,
                                    executionState = WorkflowExecutionState(
                                        workflowId = workflow.id,
                                        workflowName = workflow.name,
                                        status = "FAILED",
                                        currentStepIndex = 0,
                                        totalSteps = steps.size,
                                        stepLogs = listOf(missingMsg),
                                        verificationMessage = missingMsg,
                                        errorCode = AutomationErrorCode.CREDENTIAL_NOT_FOUND
                                    ),
                                    snackbarNotice = missingMsg
                                )
                            }
                            return@launch
                        }
                    }
                }

                // Phase 4B Pre-Replay Validation & Explicit Authorization for Credential Steps (including nested/sub-workflows)
                if (credentialSteps.isNotEmpty()) {
                    suspend fun failCredentialPrecheck(code: AutomationErrorCode, msg: String) {
                        val execId = repository.recordAutomationExecutionStart(workflow, steps.size)
                        repository.finishAutomationExecution(
                            workflowId = workflow.id,
                            executionId = execId,
                            status = "FAILED",
                            completedSteps = 0,
                            totalSteps = steps.size,
                            verificationSummary = msg
                        )
                        _uiState.update {
                            it.copy(
                                pendingCredentialReplayAuth = null,
                                automationToolbarStatus = AutomationToolbarStatus.ERROR,
                                executionState = WorkflowExecutionState(
                                    workflowId = workflow.id,
                                    workflowName = workflow.name,
                                    status = "FAILED",
                                    currentStepIndex = 0,
                                    totalSteps = steps.size,
                                    stepLogs = listOf(msg),
                                    verificationMessage = msg,
                                    errorCode = code
                                ),
                                snackbarNotice = msg
                            )
                        }
                    }

                    if (credentialVaultManager.crypto.keyProvider.isKeyInvalidated()) {
                        credentialVaultManager.refreshVaultStatus()
                        failCredentialPrecheck(
                            AutomationErrorCode.CREDENTIAL_KEY_INVALIDATED,
                            "Credential Vault encryption key was invalidated. Reset the Vault before running credential workflows."
                        )
                        return@launch
                    }

                    val unassignedStep = credentialSteps.firstOrNull { it.credentialReferenceId.isNullOrBlank() }
                    if (unassignedStep != null) {
                        failCredentialPrecheck(
                            AutomationErrorCode.CREDENTIAL_UNASSIGNED,
                            "Step ${unassignedStep.order} ('${unassignedStep.description}') requires a saved credential, but none is assigned."
                        )
                        return@launch
                    }

                    val effectiveStartUrl = effectiveStartUrlForRun
                    val targetOrigin = automationEngine.parseOrigin(effectiveStartUrl)
                    if (!targetOrigin.isValid) {
                        failCredentialPrecheck(
                            AutomationErrorCode.CREDENTIAL_ORIGIN_MISMATCH,
                            "Credential workflows can only run on valid HTTP(S) origins (current URL: '$effectiveStartUrl')."
                        )
                        return@launch
                    }

                    if (workflow.sourceUrl.isNotBlank() && !workflow.sourceUrl.equals("apex://home", ignoreCase = true)) {
                        val wfSourceOrigin = automationEngine.parseOrigin(workflow.sourceUrl)
                        if (!wfSourceOrigin.isValid || wfSourceOrigin.canonicalOrigin != targetOrigin.canonicalOrigin) {
                            failCredentialPrecheck(
                                AutomationErrorCode.CREDENTIAL_ORIGIN_MISMATCH,
                                "Current tab origin '${targetOrigin.canonicalOrigin}' does not match workflow origin '${wfSourceOrigin.canonicalOrigin.ifBlank { workflow.sourceUrl }}'."
                            )
                            return@launch
                        }
                    }

                    val replaySummaries = mutableListOf<CredentialReplayItemSummary>()
                    for (credStep in credentialSteps) {
                        val refId = credStep.credentialReferenceId!!.trim()
                        val entity = credentialVaultManager.getCredentialMetadataById(refId)
                        if (entity == null) {
                            failCredentialPrecheck(
                                AutomationErrorCode.CREDENTIAL_NOT_FOUND,
                                "Saved credential for step ${credStep.order} ('${credStep.description}') was deleted or does not exist."
                            )
                            return@launch
                        }
                        if (entity.websiteOrigin != targetOrigin.canonicalOrigin) {
                            failCredentialPrecheck(
                                AutomationErrorCode.CREDENTIAL_ORIGIN_MISMATCH,
                                "Origin mismatch: credential '${entity.label}' is bound to '${entity.websiteOrigin}', but target page is '${targetOrigin.canonicalOrigin}'."
                            )
                            return@launch
                        }
                        if (credStep.expectedOrigin.isNotBlank()) {
                            val stepOrigin = automationEngine.parseOrigin(credStep.expectedOrigin)
                            if (!stepOrigin.isValid || stepOrigin.canonicalOrigin != entity.websiteOrigin) {
                                failCredentialPrecheck(
                                    AutomationErrorCode.CREDENTIAL_ORIGIN_MISMATCH,
                                    "Step ${credStep.order} expected origin '${credStep.expectedOrigin}' does not match credential origin '${entity.websiteOrigin}'."
                                )
                                return@launch
                            }
                        }
                        val role = credStep.credentialFieldRole ?: CredentialFillCoordinator.inferFieldRole(credStep)
                        replaySummaries.add(
                            CredentialReplayItemSummary(
                                stepId = credStep.id,
                                stepOrder = credStep.order,
                                stepDescription = credStep.description,
                                credentialId = entity.credentialId,
                                credentialLabel = entity.label,
                                usernameIdentifier = entity.usernameIdentifier,
                                websiteOrigin = entity.websiteOrigin,
                                displayOrigin = entity.displayOrigin,
                                fieldRole = role
                            )
                        )
                    }

                    val requiredIds = replaySummaries.map { it.credentialId }.toSet()
                    val isFullyAuthorized = confirmCredentialReplay && requiredIds.all { it in authorizedCredentialIds }
                    if (!isFullyAuthorized) {
                        val hasSubmit = allStepsForPrecheck.any {
                            it.actionType == AutomationActionType.SUBMIT || it.isDestructive
                        }
                        pendingInterruptedResumeCheckpoint = resumeCheckpoint
                        pendingCredentialPlayOptions = playOptions
                        val pendingAuth = PendingCredentialReplayAuthorization(
                            workflowId = workflow.id,
                            workflowName = workflow.name,
                            tabId = targetTabId,
                            websiteOrigin = targetOrigin.canonicalOrigin,
                            displayOrigin = targetOrigin.displayOrigin,
                            items = replaySummaries,
                            hasSubmissionStep = hasSubmit,
                            pendingDestructiveSteps = destructiveSteps
                        )
                        _uiState.update {
                            it.copy(
                                pendingCredentialReplayAuth = pendingAuth,
                                executionState = WorkflowExecutionState(
                                    workflowId = workflow.id,
                                    workflowName = workflow.name,
                                    status = "AWAITING_CREDENTIAL_AUTH",
                                    currentStepIndex = resumeCheckpoint?.completedStepsCount ?: 0,
                                    totalSteps = steps.size,
                                    stepLogs = emptyList(),
                                    verificationMessage = "Authorization required to fill saved credentials on ${targetOrigin.displayOrigin}.",
                                    isAwaitingConfirmation = true,
                                    pendingDestructiveSteps = destructiveSteps
                                )
                            )
                        }
                        return@launch
                    }
                }

                if (destructiveSteps.isNotEmpty() && !confirmDestructive) {
                    pendingDestructiveWorkflowId = workflow.id
                    pendingDestructiveInstructionConfirm = false
                    pendingDestructivePlayOptions = playOptions
                    _uiState.update {
                        it.copy(
                            executionState = WorkflowExecutionState(
                                workflowId = workflow.id,
                                workflowName = workflow.name,
                                status = "AWAITING_CONFIRMATION",
                                currentStepIndex = 0,
                                totalSteps = steps.size,
                                stepLogs = emptyList(),
                                verificationMessage = "Confirmation required before executing destructive steps.",
                                isAwaitingConfirmation = true,
                                pendingDestructiveSteps = destructiveSteps
                            )
                        )
                    }
                    return@launch
                }

                credentialFillCoordinator.resetLifecycleAbort()
                var lockedAuthorizedWebView: WebView? = webViewRegistry[targetTabId]
                val initialOrigin = automationEngine.parseOrigin(effectiveStartUrlForRun).canonicalOrigin
                val sessionId = executionLifecycleController.tryBeginSession(
                    tabId = targetTabId,
                    webView = lockedAuthorizedWebView,
                    canonicalOrigin = initialOrigin
                ) ?: run {
                    _uiState.update {
                        it.copy(
                            snackbarNotice = "Another automation workflow is already running"
                        )
                    }
                    return@launch
                }
                activeExecutionSessionId = sessionId

                val execId = if (resumeCheckpoint != null && resumeCheckpoint.executionId > 0L) {
                    repository.updateExecutionCheckpointSnapshot(
                        executionId = resumeCheckpoint.executionId,
                        status = "RUNNING",
                        completedSteps = resumeCheckpoint.completedStepsCount,
                        totalSteps = steps.size,
                        verificationSummary = "Resuming from step ${resumeCheckpoint.nextTopLevelIndex + 1}/${steps.size}…",
                        checkpointJson = resumeCheckpoint.toJsonString(),
                        traceJson = Phase6ExecutionCoordinator.serializeExecutionTrace(
                            resumeCheckpoint.branchTraces,
                            resumeCheckpoint.recoveryAttempts
                        )
                    )
                    resumeCheckpoint.executionId
                } else {
                    repository.recordAutomationExecutionStart(workflow, steps.size)
                }

                var completedStepsCount = resumeCheckpoint?.completedStepsCount ?: 0
                val startedAtMs = System.currentTimeMillis()
                latestExecutionCheckpoint = resumeCheckpoint ?: PersistedExecutionCheckpoint(
                    executionId = execId,
                    workflowId = workflow.id,
                    workflowName = workflow.name,
                    tabId = targetTabId,
                    boundOrigin = initialOrigin,
                    lastUrl = targetTab.url,
                    nextTopLevelIndex = 0,
                    totalTopLevelSteps = steps.size,
                    completedStepsCount = 0,
                    requiresCredentialReauth = credentialSteps.isNotEmpty()
                )

                val firstResumeIndex = (resumeCheckpoint?.nextTopLevelIndex ?: 0).coerceIn(0, steps.lastIndex)
                val firstStep = steps[firstResumeIndex]
                val initialMatchedEl = _uiState.value.pageInspection.elements.firstOrNull { el ->
                    (firstStep.selector.isNotBlank() && el.selector == firstStep.selector) ||
                        (firstStep.targetLabel.isNotBlank() && el.label.equals(firstStep.targetLabel, ignoreCase = true))
                }
                val initialStepHighlight = ActiveDomElementHighlight(
                    selector = firstStep.selector.ifBlank { firstStep.targetLabel },
                    targetLabel = firstStep.targetLabel.ifBlank { firstStep.description },
                    actionLabel = firstStep.actionType.label,
                    tagName = initialMatchedEl?.tagName.orEmpty(),
                    boundingBox = initialMatchedEl?.boundingBox,
                    isScanning = false,
                    stepIndex = firstResumeIndex + 1,
                    totalSteps = steps.size,
                    status = "INTERACTING"
                )
                _uiState.update {
                    it.copy(
                        isAutomationHudOpen = false,
                        isAutomationWorkspaceOpen = false,
                        selectedAutomationWorkflowId = null,
                        isAutomationOpen = false,
                        isSearchScreenOpen = false,
                        isTabSwitcherOpen = false,
                        isMenuSheetOpen = false,
                        pendingCredentialReplayAuth = null,
                        vaultExhaustionPromptState = null,
                        interruptedExecutionCandidate = null,
                        automationToolbarStatus = AutomationToolbarStatus.EXECUTING,
                        activeDomHighlight = initialStepHighlight,
                        executionState = WorkflowExecutionState(
                            workflowId = workflow.id,
                            workflowName = workflow.name,
                            status = "RUNNING",
                            currentStepIndex = firstResumeIndex,
                            totalSteps = steps.size,
                            stepLogs = listOf(
                                if (resumeCheckpoint != null) {
                                    "Resumed '${workflow.name}' from step ${firstResumeIndex + 1}/${steps.size}"
                                } else if (effectiveRepeatCount > 1) {
                                    "Started '${workflow.name}' ($effectiveRepeatCount runs, ${steps.size} steps each)"
                                } else {
                                    "Started '${workflow.name}' (${steps.size} steps)"
                                }
                            ),
                            verificationMessage = if (effectiveRepeatCount > 1) {
                                "Run 1/$effectiveRepeatCount on ${workflow.domain}…"
                            } else {
                                "Executing on ${workflow.domain}…"
                            },
                            currentStepDescription = firstStep.description,
                            currentActionLabel = firstStep.actionType.label,
                            completedSteps = completedStepsCount,
                            startedAtMs = startedAtMs,
                            elapsedMs = 0L,
                            variableSnapshots = resumeCheckpoint?.safeVariables?.entries?.map { (k, v) ->
                                val masked = WorkflowVariableStore.shouldMaskInUi(k)
                                VariableDisplayBadge(
                                    name = k,
                                    type = v.type,
                                    previewValue = if (masked) "••••" else v.rawValue.take(36),
                                    isMasked = masked
                                )
                            }.orEmpty(),
                            branchTraces = resumeCheckpoint?.branchTraces.orEmpty(),
                            recoveryAttempts = resumeCheckpoint?.recoveryAttempts.orEmpty(),
                            activeElementHighlight = initialStepHighlight,
                            currentRunIndex = 1,
                            totalRuns = effectiveRepeatCount,
                            clearDataAfterRun = effectiveClearData
                        )
                    )
                }

                try {
                    val consumedVaultEntriesByPool = mutableMapOf<String, MutableSet<String>>()
                    val dobValueByRunAndPool = mutableMapOf<Pair<Int, String>, String>()
                    var lastExhaustedException: VaultValuesExhaustedException? = null
                    var lastVaultStep: AutomationStep? = null
                    var runsCompleted = 0
                    var activeRunIndex = 1
                    var finalOutcome: WorkflowExecutionOutcome? = null
                    var limitReachedMidBatch = false

                    for (runIndex in 1..effectiveRepeatCount) {
                        coroutineContext.ensureActive()
                        activeRunIndex = runIndex
                        if (resumeCheckpoint == null || runIndex > 1) {
                            val currentPrefs = repository.preferencesFlow.first()
                            if (!currentPrefs.canPlayAutomationToday()) {
                                limitReachedMidBatch = true
                                break
                            }
                            if (!currentPrefs.isProCurrentlyActive()) {
                                repository.recordAutomationPlayUsage()
                            }
                        }
                        if (runIndex > 1) {
                            completedStepsCount = 0
                            val liveBeforeNextRun = _uiState.value.tabs.firstOrNull { it.id == targetTabId }?.url.orEmpty()
                            if (redirectTargetUrl.isNotBlank() &&
                                !Phase6ExecutionCoordinator.urlsMatchForWorkflowStart(liveBeforeNextRun, redirectTargetUrl)
                            ) {
                                navigateTabUrlForAutomation(targetTabId, redirectTargetUrl)
                            }
                            _uiState.update { state ->
                                val currentExec = state.executionState
                                if (currentExec != null && currentExec.workflowId == workflow.id) {
                                    state.copy(
                                        executionState = currentExec.copy(
                                            currentRunIndex = runIndex,
                                            totalRuns = effectiveRepeatCount,
                                            currentStepIndex = 0,
                                            completedSteps = 0,
                                            verificationMessage = "Run $runIndex/$effectiveRepeatCount on ${workflow.domain}…",
                                            stepLogs = currentExec.stepLogs + "Starting run $runIndex/$effectiveRepeatCount"
                                        )
                                    )
                                } else {
                                    state
                                }
                            }
                        }

                        val liveTabUrlNow = _uiState.value.tabs.firstOrNull { it.id == targetTabId }?.url
                            ?.ifBlank { targetTab.url } ?: targetTab.url

                        val runOutcome = automationEngine.executeWorkflow(
                            workflow = workflow,
                            steps = steps,
                            currentTabUrl = liveTabUrlNow,
                            webViewProvider = { webViewRegistry[targetTabId] },
                            onNavigateUrl = { dest ->
                                navigateTabUrlForAutomation(targetTabId, dest)
                            },
                            onStepProgress = { stepIndex, msg ->
                                _uiState.update { state ->
                                    val currentExec = state.executionState
                                    if (currentExec != null && currentExec.workflowId == workflow.id) {
                                        val prefix = if (effectiveRepeatCount > 1) "[Run $runIndex/$effectiveRepeatCount] " else ""
                                        state.copy(
                                            executionState = currentExec.copy(
                                                currentStepIndex = stepIndex,
                                                currentRunIndex = runIndex,
                                                totalRuns = effectiveRepeatCount,
                                                stepLogs = currentExec.stepLogs + (prefix + msg),
                                                elapsedMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L)
                                            )
                                        )
                                    } else {
                                        state
                                    }
                                }
                            },
                            tabId = targetTabId,
                            navigationSynchronizer = navigationSynchronizer,
                            onStepCompleted = { doneCount ->
                                completedStepsCount = doneCount
                            },
                            credentialStepExecutor = if (credentialSteps.isNotEmpty()) {
                                { credStep, liveUrl ->
                                    val currentWv = webViewRegistry[targetTabId]
                                    if (lockedAuthorizedWebView == null && currentWv != null) {
                                        lockedAuthorizedWebView = currentWv
                                        executionLifecycleController.updateBoundWebViewIfUnset(currentWv)
                                    }
                                    credentialFillCoordinator.executeAuthorizedCredentialStep(
                                        step = credStep,
                                        authorizedTabId = targetTabId,
                                        currentActiveTabId = _uiState.value.activeTabId,
                                        authorizedWebView = lockedAuthorizedWebView,
                                        currentWebViewProvider = { webViewRegistry[targetTabId] },
                                        liveTabUrlProvider = {
                                            _uiState.value.tabs.firstOrNull { it.id == targetTabId }?.url?.ifBlank { liveUrl } ?: liveUrl
                                        },
                                        navigationSynchronizer = navigationSynchronizer,
                                        userAuthorizedCredentialIds = authorizedCredentialIds
                                    )
                                }
                            } else {
                                null
                            },
                            lifecycleController = executionLifecycleController,
                            isTabOpenProvider = {
                                _uiState.value.tabs.any { it.id == targetTabId }
                            },
                            liveUrlProvider = {
                                _uiState.value.tabs.firstOrNull { it.id == targetTabId }?.url.orEmpty()
                            },
                            onStepDetailedProgress = { stepIndex, step, recoveryNotice, records ->
                                _uiState.update { state ->
                                    val currentExec = state.executionState
                                    if (currentExec != null && currentExec.workflowId == workflow.id) {
                                        val done = records.count {
                                            it.status == "COMPLETED" || it.status == "SKIPPED_OPTIONAL"
                                        }
                                        val matchedRecord = records.lastOrNull { it.stepId == step.id }
                                        val effectiveSelector = matchedRecord?.resolvedSelector?.takeIf { it.isNotBlank() }
                                            ?: step.selector
                                        val matchedEl = state.pageInspection.elements.firstOrNull { el ->
                                            (effectiveSelector.isNotBlank() && el.selector == effectiveSelector) ||
                                                (step.targetLabel.isNotBlank() && el.label.equals(step.targetLabel, ignoreCase = true))
                                        }
                                        val liveHighlight = ActiveDomElementHighlight(
                                            selector = effectiveSelector.ifBlank { step.targetLabel },
                                            targetLabel = step.targetLabel.ifBlank { step.description },
                                            actionLabel = step.actionType.label,
                                            tagName = matchedEl?.tagName.orEmpty(),
                                            boundingBox = matchedRecord?.targetBoundingBox ?: matchedEl?.boundingBox,
                                            isScanning = false,
                                            stepIndex = (stepIndex + 1).coerceIn(1, steps.size.coerceAtLeast(1)),
                                            totalSteps = steps.size,
                                            status = if (matchedRecord != null) "VERIFIED" else "INTERACTING"
                                        )
                                        state.copy(
                                            activeDomHighlight = liveHighlight,
                                            executionState = currentExec.copy(
                                                currentStepIndex = stepIndex,
                                                currentRunIndex = runIndex,
                                                totalRuns = effectiveRepeatCount,
                                                currentStepDescription = step.description,
                                                currentActionLabel = step.actionType.label,
                                                completedSteps = done,
                                                elapsedMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L),
                                                recoveryNotice = recoveryNotice.ifBlank { currentExec.recoveryNotice },
                                                stepRecords = records,
                                                activeElementHighlight = liveHighlight
                                            )
                                        )
                                    } else {
                                        state
                                    }
                                }
                            },
                            workflowDocument = workflowDoc,
                            initialCheckpoint = if (runIndex == 1) resumeCheckpoint else null,
                            workflowLookup = subWorkflowLookup,
                            confirmedUnsafeRetry = confirmedUnsafeRetry,
                            executionId = execId,
                            onPhase6Progress = { p6 ->
                                p6.latestCheckpoint?.let { latestExecutionCheckpoint = it }
                                _uiState.update { state ->
                                    val currentExec = state.executionState
                                    if (currentExec != null && currentExec.workflowId == workflow.id) {
                                        state.copy(
                                            executionState = currentExec.copy(
                                                activeBranchLabel = p6.activeBranchLabel,
                                                activeSubWorkflowName = p6.activeSubWorkflowName,
                                                variableSnapshots = p6.variableSnapshots,
                                                branchTraces = p6.branchTraces,
                                                recoveryAttempts = p6.recoveryAttempts
                                            )
                                        )
                                    } else {
                                        state
                                    }
                                }
                            },
                            vaultValueResolver = { vStep ->
                                lastVaultStep = vStep
                                val pinnedId = vStep.vaultEntryId?.trim()?.takeIf { it.isNotEmpty() }
                                val shouldUsePool = vStep.useVaultPool || effectiveRepeatCount > 1 || pinnedId == null
                                if (!shouldUsePool && pinnedId != null) {
                                    repository.automationVaultManager.decryptEntryValue(pinnedId, markUsed = true)
                                } else {
                                    val cat = if (vStep.vaultCategory.isNotBlank()) {
                                        AutomationVaultCategory.fromId(vStep.vaultCategory)
                                    } else {
                                        AutomationVaultManager.detectCategory(
                                            targetLabel = vStep.targetLabel,
                                            selector = vStep.selector,
                                            isCredentialStep = vStep.isCredentialStep,
                                            credentialFieldRole = vStep.credentialFieldRole
                                        )
                                    }
                                    val resolvedFolderId = vStep.vaultFolderId.trim()
                                        .ifBlank { AutomationVaultManager.defaultFolderIdForCategory(cat) }
                                    val poolKey = "$resolvedFolderId:${cat.id}"
                                    val cachedDob = if (cat == AutomationVaultCategory.DATE_OF_BIRTH) {
                                        dobValueByRunAndPool[activeRunIndex to poolKey]
                                    } else {
                                        null
                                    }
                                    if (cachedDob != null) {
                                        Result.success(cachedDob)
                                    } else {
                                        val consumedSet = consumedVaultEntriesByPool.getOrPut(poolKey) { mutableSetOf() }
                                        val res = repository.automationVaultManager.resolveNextPoolValue(
                                            category = cat,
                                            folderId = resolvedFolderId,
                                            pinnedEntryId = pinnedId,
                                            consumedEntryIds = consumedSet,
                                            markUsed = true
                                        )
                                        if (res.isSuccess && cat == AutomationVaultCategory.DATE_OF_BIRTH) {
                                            dobValueByRunAndPool[activeRunIndex to poolKey] = res.getOrThrow()
                                        }
                                        val err = res.exceptionOrNull()
                                        if (err is VaultValuesExhaustedException) {
                                            lastExhaustedException = err
                                        }
                                        res
                                    }
                                }
                            },
                            clearBrowserDataExecutor = {
                                clearBrowserDataForAutomation(webViewRegistry[targetTabId])
                            }
                        )

                        finalOutcome = runOutcome
                        if (runOutcome.status == "INTERRUPTED") {
                            break
                        }
                        if (runOutcome.status != "COMPLETED") {
                            break
                        }
                        runsCompleted++
                        if (effectiveClearData) {
                            clearBrowserDataForAutomation(webViewRegistry[targetTabId])
                            _uiState.update { state ->
                                val currentExec = state.executionState
                                if (currentExec != null && currentExec.workflowId == workflow.id) {
                                    state.copy(
                                        executionState = currentExec.copy(
                                            stepLogs = currentExec.stepLogs + "Cleared browser data after run $runIndex/$effectiveRepeatCount"
                                        )
                                    )
                                } else {
                                    state
                                }
                            }
                        }
                    }

                    val outcome = finalOutcome ?: WorkflowExecutionOutcome(
                        status = "FAILED",
                        completedSteps = 0,
                        totalSteps = steps.size,
                        verificationSummary = "Execution did not produce an outcome.",
                        errorCode = AutomationErrorCode.VERIFICATION_FAILED
                    )

                    val traceJson = Phase6ExecutionCoordinator.serializeExecutionTrace(
                        outcome.branchTraces,
                        outcome.recoveryAttempts
                    )

                    if (outcome.status == "INTERRUPTED") {
                        val cp = (outcome.lastCheckpoint ?: latestExecutionCheckpoint ?: PersistedExecutionCheckpoint(
                            executionId = execId,
                            workflowId = workflow.id,
                            workflowName = workflow.name,
                            tabId = targetTabId,
                            boundOrigin = initialOrigin,
                            lastUrl = targetTab.url,
                            nextTopLevelIndex = outcome.completedSteps,
                            totalTopLevelSteps = outcome.totalSteps,
                            completedStepsCount = outcome.completedSteps,
                            requiresCredentialReauth = credentialSteps.isNotEmpty()
                        )).copy(executionId = execId)
                        repository.interruptAutomationExecution(
                            workflowId = workflow.id,
                            executionId = execId,
                            completedSteps = outcome.completedSteps,
                            totalSteps = outcome.totalSteps,
                            verificationSummary = outcome.verificationSummary,
                            checkpointJson = cp.toJsonString(),
                            traceJson = traceJson
                        )
                        _uiState.update { state ->
                            val currentExec = state.executionState
                            state.copy(
                                automationToolbarStatus = AutomationToolbarStatus.PAUSED,
                                executionState = currentExec?.copy(
                                    status = "INTERRUPTED",
                                    currentStepIndex = outcome.completedSteps,
                                    completedSteps = outcome.completedSteps,
                                    stepLogs = currentExec.stepLogs + outcome.verificationSummary,
                                    verificationMessage = outcome.verificationSummary,
                                    isAwaitingConfirmation = false,
                                    isInterrupted = true,
                                    errorCode = outcome.errorCode,
                                    elapsedMs = outcome.durationMs,
                                    stepRecords = outcome.stepRecords,
                                    branchTraces = outcome.branchTraces,
                                    recoveryAttempts = outcome.recoveryAttempts,
                                    variableSnapshots = outcome.variableSnapshots
                                ),
                                interruptedExecutionCandidate = validateInterruptedCheckpoint(cp),
                                snackbarNotice = "Workflow interrupted — resume or restart safely"
                            )
                        }
                        return@launch
                    }

                    val finalSummary = when {
                        limitReachedMidBatch && runsCompleted > 0 ->
                            "Completed $runsCompleted/$effectiveRepeatCount runs (daily 5-play free limit reached). Activate Apex Pro for unlimited replays."
                        limitReachedMidBatch ->
                            "Daily 5-play free limit reached. Activate Apex Pro to continue."
                        outcome.status == "COMPLETED" && effectiveRepeatCount > 1 ->
                            "Completed $runsCompleted/$effectiveRepeatCount replay runs${if (effectiveClearData) " (browser data cleared after each run)" else ""}"
                        effectiveRepeatCount > 1 && outcome.status != "COMPLETED" ->
                            "Stopped on run $activeRunIndex/$effectiveRepeatCount: ${outcome.verificationSummary}"
                        else -> outcome.verificationSummary
                    }

                    repository.finishAutomationExecution(
                        workflowId = workflow.id,
                        executionId = execId,
                        status = outcome.status,
                        completedSteps = outcome.completedSteps,
                        totalSteps = outcome.totalSteps,
                        verificationSummary = finalSummary,
                        traceJson = traceJson
                    )

                    val succeeded = outcome.status == "COMPLETED"
                    val wasCancelled = outcome.status == "CANCELLED"
                    val isAmbiguousFailure = !succeeded &&
                        outcome.errorCode == AutomationErrorCode.ELEMENT_AMBIGUOUS &&
                        outcome.ambiguousCandidates.isNotEmpty()
                    val isVaultExhausted = !succeeded &&
                        outcome.errorCode == AutomationErrorCode.VAULT_VALUES_EXHAUSTED
                    val exhaustionPrompt = if (isVaultExhausted) {
                        val ex = lastExhaustedException
                        val cat = ex?.category ?: lastVaultStep?.vaultCategory
                            ?.takeIf { it.isNotBlank() }
                            ?.let { AutomationVaultCategory.fromId(it) }
                            ?: AutomationVaultCategory.CUSTOM
                        val folderId = ex?.folderId ?: lastVaultStep?.vaultFolderId
                            ?.takeIf { it.isNotBlank() }
                            ?: AutomationVaultManager.defaultFolderIdForCategory(cat)
                        val folderName = ex?.folderName ?: AutomationVaultManager.defaultFolderNameForCategory(cat)
                        VaultExhaustionPromptState(
                            workflowId = workflow.id,
                            workflowName = workflow.name,
                            folderId = folderId,
                            folderName = folderName,
                            category = cat,
                            targetLabel = lastVaultStep?.targetLabel?.ifBlank { cat.label } ?: cat.label,
                            failedRunIndex = activeRunIndex,
                            totalRuns = effectiveRepeatCount,
                            message = finalSummary
                        )
                    } else {
                        null
                    }
                    val failedStepRecord = selectPrimaryFailedStepRecord(outcome.stepRecords)
                    val failedStepObj = failedStepRecord?.let { rec ->
                        allStepsForPrecheck.firstOrNull { it.id == rec.stepId }
                    } ?: if (failedStepRecord == null) {
                        steps.getOrNull(outcome.completedSteps.coerceIn(0, steps.lastIndex))
                    } else {
                        null
                    }
                    val resolvedFailedDiagnostics = failedStepRecord?.locatorDiagnostics ?: outcome.lastLocatorDiagnostics
                    val resolvedAmbigCandidates = failedStepRecord?.ambiguousCandidates?.ifEmpty { outcome.ambiguousCandidates }
                        ?: outcome.ambiguousCandidates

                    _uiState.update { state ->
                        val currentExec = state.executionState
                        state.copy(
                            automationToolbarStatus = when {
                                succeeded -> AutomationToolbarStatus.COMPLETED
                                wasCancelled -> AutomationToolbarStatus.IDLE
                                else -> AutomationToolbarStatus.ERROR
                            },
                            vaultExhaustionPromptState = exhaustionPrompt ?: state.vaultExhaustionPromptState,
                            lastLocatorDiagnostics = resolvedFailedDiagnostics ?: state.lastLocatorDiagnostics,
                            pendingDisambiguation = if (isAmbiguousFailure) {
                                PendingDisambiguationState(
                                    source = DisambiguationSource.EXECUTION,
                                    workflowId = workflow.id,
                                    stepId = failedStepObj?.id ?: failedStepRecord?.stepId.orEmpty(),
                                    stepOrder = failedStepObj?.order ?: failedStepRecord?.stepOrder ?: (outcome.completedSteps + 1),
                                    stepDescription = failedStepObj?.description
                                        ?: resolvedFailedDiagnostics?.targetLabel?.takeIf { t -> t.isNotBlank() }
                                        ?: workflow.name,
                                    actionType = failedStepObj?.actionType ?: failedStepRecord?.actionType ?: AutomationActionType.CLICK,
                                    originalTarget = resolvedFailedDiagnostics?.targetLabel?.takeIf { t -> t.isNotBlank() }
                                        ?: failedStepObj?.targetLabel?.takeIf { t -> t.isNotBlank() }
                                        ?: finalSummary,
                                    originalSelector = resolvedFailedDiagnostics?.recordedLocator?.takeIf { r -> r.isNotBlank() }
                                        ?: failedStepObj?.selector.orEmpty(),
                                    candidates = resolvedAmbigCandidates,
                                    diagnostics = resolvedFailedDiagnostics
                                )
                            } else {
                                state.pendingDisambiguation
                            },
                            executionState = currentExec?.copy(
                                status = outcome.status,
                                currentStepIndex = outcome.completedSteps,
                                completedSteps = outcome.completedSteps,
                                stepLogs = currentExec.stepLogs + finalSummary,
                                verificationMessage = finalSummary,
                                isAwaitingConfirmation = false,
                                errorCode = outcome.errorCode,
                                elapsedMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L),
                                stepRecords = outcome.stepRecords,
                                branchTraces = outcome.branchTraces,
                                recoveryAttempts = outcome.recoveryAttempts,
                                variableSnapshots = emptyList(),
                                ambiguousCandidates = outcome.ambiguousCandidates,
                                lastLocatorDiagnostics = outcome.lastLocatorDiagnostics,
                                currentRunIndex = activeRunIndex,
                                totalRuns = effectiveRepeatCount,
                                clearDataAfterRun = effectiveClearData
                            ),
                            snackbarNotice = when {
                                limitReachedMidBatch -> "Daily 5-play limit reached — Activate Apex Pro to continue"
                                succeeded && effectiveRepeatCount > 1 ->
                                    "Completed $runsCompleted/$effectiveRepeatCount runs of '${workflow.name}'"
                                succeeded -> "Completed '${workflow.name}'"
                                wasCancelled -> "Cancelled '${workflow.name}'"
                                isVaultExhausted -> "Automation Vault values finished — add or generate more data"
                                isAmbiguousFailure -> "Multiple elements matched — choose target to continue"
                                else -> "Automation stopped: $finalSummary"
                            },
                            isSyncBackupDialogOpen = if (limitReachedMidBatch) true else state.isSyncBackupDialogOpen,
                            subscriptionPromptReason = if (limitReachedMidBatch) {
                                "Daily free limit reached (${BrowserPreferences.FREE_DAILY_AUTOMATION_PLAY_LIMIT}/${BrowserPreferences.FREE_DAILY_AUTOMATION_PLAY_LIMIT} plays used today). Select a package and paste an activation key to unlock Apex Pro."
                            } else {
                                state.subscriptionPromptReason
                            },
                            proActivationError = if (limitReachedMidBatch) null else state.proActivationError
                        )
                    }

                    if (succeeded || wasCancelled) {
                        toolbarCompletedResetJob?.cancel()
                        toolbarCompletedResetJob = viewModelScope.launch {
                            delay(2_800L)
                            _uiState.update { s ->
                                if (s.executionState?.status != "RUNNING") {
                                    s.copy(activeDomHighlight = null)
                                } else {
                                    s
                                }
                            }
                            refreshAutomationToolbarStatus(clearTransientResult = true)
                        }
                    }
                } catch (ce: CancellationException) {
                    withContext(NonCancellable) {
                        val abortReason = executionLifecycleController.abortReason.value
                        val wasInterruption = executionLifecycleController.isInterruptionAbort
                        if (wasInterruption) {
                            val liveTabUrl = _uiState.value.tabs.firstOrNull { it.id == targetTabId }?.url
                                ?: latestExecutionCheckpoint?.lastUrl
                                ?: targetTab.url
                            val cp = (latestExecutionCheckpoint ?: PersistedExecutionCheckpoint(
                                executionId = execId,
                                workflowId = workflow.id,
                                workflowName = workflow.name,
                                tabId = targetTabId,
                                boundOrigin = initialOrigin,
                                lastUrl = liveTabUrl,
                                nextTopLevelIndex = completedStepsCount.coerceIn(0, steps.size),
                                totalTopLevelSteps = steps.size,
                                completedStepsCount = completedStepsCount,
                                requiresCredentialReauth = credentialSteps.isNotEmpty()
                            )).copy(
                                executionId = execId,
                                lastUrl = liveTabUrl,
                                nextTopLevelIndex = completedStepsCount.coerceIn(0, steps.size),
                                completedStepsCount = completedStepsCount,
                                requiresCredentialReauth = credentialSteps.isNotEmpty()
                            )
                            val interruptSummary = abortReason?.message
                                ?: "Execution interrupted after $completedStepsCount/${steps.size} steps."
                            val interruptCode = abortReason?.code ?: AutomationErrorCode.CONTEXT_INVALIDATED
                            val traceJson = Phase6ExecutionCoordinator.serializeExecutionTrace(
                                cp.branchTraces,
                                cp.recoveryAttempts
                            )
                            repository.interruptAutomationExecution(
                                workflowId = workflow.id,
                                executionId = execId,
                                completedSteps = completedStepsCount,
                                totalSteps = steps.size,
                                verificationSummary = interruptSummary,
                                checkpointJson = cp.toJsonString(),
                                traceJson = traceJson
                            )
                            _uiState.update { state ->
                                val currentExec = state.executionState
                                state.copy(
                                    automationToolbarStatus = AutomationToolbarStatus.PAUSED,
                                    executionState = currentExec?.copy(
                                        status = "INTERRUPTED",
                                        currentStepIndex = completedStepsCount,
                                        completedSteps = completedStepsCount,
                                        stepLogs = currentExec.stepLogs + interruptSummary,
                                        verificationMessage = interruptSummary,
                                        isAwaitingConfirmation = false,
                                        isInterrupted = true,
                                        errorCode = interruptCode,
                                        elapsedMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L)
                                    ),
                                    interruptedExecutionCandidate = validateInterruptedCheckpoint(cp),
                                    snackbarNotice = "Workflow interrupted — resume or restart safely"
                                )
                            }
                        } else {
                            val cancelStatus = if (abortReason != null && abortReason.code != AutomationErrorCode.CANCELLED) {
                                "FAILED"
                            } else {
                                "CANCELLED"
                            }
                            val cancelCode = abortReason?.code ?: AutomationErrorCode.CANCELLED
                            val cancelSummary = abortReason?.message
                                ?: "Cancelled after $completedStepsCount/${steps.size} steps."
                            repository.finishAutomationExecution(
                                workflowId = workflow.id,
                                executionId = execId,
                                status = cancelStatus,
                                completedSteps = completedStepsCount,
                                totalSteps = steps.size,
                                verificationSummary = cancelSummary
                            )
                            _uiState.update { state ->
                                val currentExec = state.executionState
                                state.copy(
                                    executionState = currentExec?.copy(
                                        status = cancelStatus,
                                        currentStepIndex = completedStepsCount,
                                        completedSteps = completedStepsCount,
                                        stepLogs = currentExec.stepLogs + cancelSummary,
                                        verificationMessage = cancelSummary,
                                        isAwaitingConfirmation = false,
                                        errorCode = cancelCode,
                                        elapsedMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L)
                                    ),
                                    snackbarNotice = if (cancelStatus == "FAILED") {
                                        "Automation stopped: $cancelSummary"
                                    } else {
                                        "Cancelled '${workflow.name}'"
                                    }
                                )
                            }
                            refreshAutomationToolbarStatus(clearTransientResult = cancelStatus != "FAILED")
                        }
                    }
                    throw ce
                } finally {
                    latestExecutionCheckpoint = null
                    executionLifecycleController.endSession(sessionId)
                    if (activeExecutionSessionId == sessionId) {
                        activeExecutionSessionId = ""
                    }
                    _uiState.update { state ->
                        if (state.executionState?.status != "COMPLETED" && state.executionState?.status != "RUNNING") {
                            state.copy(activeDomHighlight = null)
                        } else {
                            state
                        }
                    }
                }
            } finally {
                if (automationExecutionJob === currentJob) {
                    automationExecutionJob = null
                }
            }
        }
    }

    private fun refreshInterruptedCandidateFromState() {
        val entity = latestInterruptedEntity
        val candidate = entity?.let { e ->
            val cp = PersistedExecutionCheckpoint.fromJson(e.checkpointJson)
                ?: PersistedExecutionCheckpoint(
                    executionId = e.id,
                    workflowId = e.workflowId,
                    workflowName = e.workflowName,
                    tabId = "",
                    boundOrigin = "",
                    lastUrl = "",
                    nextTopLevelIndex = e.completedSteps,
                    totalTopLevelSteps = e.totalSteps,
                    completedStepsCount = e.completedSteps
                )
            validateInterruptedCheckpoint(cp)
        }
        _uiState.update { it.copy(interruptedExecutionCandidate = candidate) }
    }

    internal fun validateInterruptedCheckpoint(cp: PersistedExecutionCheckpoint): InterruptedExecutionCandidate {
        val currentState = _uiState.value
        val targetTab = if (cp.tabId.isNotBlank()) {
            currentState.tabs.firstOrNull { it.id == cp.tabId }
        } else {
            currentState.activeTab
        }
        val currentUrl = targetTab?.url.orEmpty()
        val currentOrigin = automationEngine.parseOrigin(currentUrl).canonicalOrigin

        var canSafeResume = true
        var unsafeReason = ""
        when {
            cp.tabId.isNotBlank() && targetTab == null -> {
                canSafeResume = false
                unsafeReason = "Original browser tab was closed. Restart workflow cleanly."
            }
            cp.boundOrigin.isNotBlank() && currentOrigin != cp.boundOrigin -> {
                canSafeResume = false
                unsafeReason = "Tab origin changed from '${cp.boundOrigin}'. Restart workflow cleanly."
            }
            cp.totalTopLevelSteps > 0 && cp.nextTopLevelIndex >= cp.totalTopLevelSteps -> {
                canSafeResume = false
                unsafeReason = "No remaining steps left in checkpoint. Start workflow cleanly."
            }
        }
        return InterruptedExecutionCandidate(
            checkpoint = cp,
            canSafeResume = canSafeResume,
            unsafeReason = unsafeReason
        )
    }

    fun confirmResumeInterruptedExecution(
        candidate: InterruptedExecutionCandidate? = _uiState.value.interruptedExecutionCandidate
    ) {
        val target = candidate ?: return
        val revalidated = validateInterruptedCheckpoint(target.checkpoint)
        if (!revalidated.canSafeResume) {
            _uiState.update {
                it.copy(
                    interruptedExecutionCandidate = revalidated,
                    snackbarNotice = revalidated.unsafeReason.ifBlank { "Cannot safely resume here; restart cleanly" }
                )
            }
            return
        }
        val targetWorkflow = automationWorkflowsState.value.firstOrNull { it.id == revalidated.checkpoint.workflowId }
        if (targetWorkflow?.isArchived == true) {
            _uiState.update {
                it.copy(
                    snackbarNotice = "Archived workflow '${targetWorkflow.name}' cannot resume until restored from Archive."
                )
            }
            return
        }
        _uiState.update { it.copy(interruptedExecutionCandidate = null) }
        runWorkflow(
            workflowId = revalidated.checkpoint.workflowId,
            confirmDestructive = true,
            resumeCheckpoint = revalidated.checkpoint
        )
    }

    fun restartInterruptedExecution(
        candidate: InterruptedExecutionCandidate? = _uiState.value.interruptedExecutionCandidate
    ) {
        val target = candidate ?: return
        viewModelScope.launch {
            if (target.checkpoint.executionId > 0L) {
                repository.finishAutomationExecution(
                    workflowId = target.checkpoint.workflowId,
                    executionId = target.checkpoint.executionId,
                    status = "CANCELLED",
                    completedSteps = target.checkpoint.completedStepsCount,
                    totalSteps = target.checkpoint.totalTopLevelSteps,
                    verificationSummary = "Restarted cleanly from step 1 by user"
                )
            }
            pendingInterruptedResumeCheckpoint = null
            _uiState.update {
                it.copy(
                    interruptedExecutionCandidate = null,
                    executionState = null
                )
            }
            runWorkflow(workflowId = target.checkpoint.workflowId)
        }
    }

    fun dismissInterruptedExecution(
        candidate: InterruptedExecutionCandidate? = _uiState.value.interruptedExecutionCandidate
    ) {
        val target = candidate ?: return
        viewModelScope.launch {
            if (target.checkpoint.executionId > 0L) {
                repository.finishAutomationExecution(
                    workflowId = target.checkpoint.workflowId,
                    executionId = target.checkpoint.executionId,
                    status = "CANCELLED",
                    completedSteps = target.checkpoint.completedStepsCount,
                    totalSteps = target.checkpoint.totalTopLevelSteps,
                    verificationSummary = "Interrupted checkpoint discarded by user"
                )
            }
            pendingInterruptedResumeCheckpoint = null
            _uiState.update { state ->
                state.copy(
                    interruptedExecutionCandidate = null,
                    executionState = if (state.executionState?.isInterrupted == true) null else state.executionState
                )
            }
            refreshAutomationToolbarStatus(clearTransientResult = true)
        }
    }

    fun confirmPendingCredentialReplay() {
        val pendingAuth = _uiState.value.pendingCredentialReplayAuth ?: return
        viewModelScope.launch {
            val uniqueCredentials = pendingAuth.items.distinctBy { it.credentialId }
            val grantedIds = mutableSetOf<String>()
            for (item in uniqueCredentials) {
                val authRes = credentialVaultManager.authManager.authenticateForCredentialOperation(
                    operation = VaultAuthOperation.REPLAY_CREDENTIAL,
                    credentialId = item.credentialId,
                    websiteOrigin = item.websiteOrigin,
                    reason = "Authorize '${pendingAuth.workflowName}' to fill ${item.fieldRole.label.lowercase()} on ${item.displayOrigin}"
                )
                credentialVaultManager.refreshVaultStatus()
                when (authRes) {
                    is VaultAuthResult.Authenticated -> {
                        grantedIds.add(item.credentialId)
                    }
                    is VaultAuthResult.Cancelled -> {
                        val msg = authRes.reason.ifBlank { "Credential authentication was cancelled by the user." }
                        val wf = repository.getAutomationWorkflowById(pendingAuth.workflowId)
                        if (wf != null) {
                            val total = AutomationStepCodec.decode(wf.stepsJson).size
                            val execId = repository.recordAutomationExecutionStart(wf, total)
                            repository.finishAutomationExecution(wf.id, execId, "FAILED", 0, total, msg)
                        }
                        pendingInterruptedResumeCheckpoint = null
                        _uiState.update {
                            it.copy(
                                pendingCredentialReplayAuth = null,
                                automationToolbarStatus = AutomationToolbarStatus.ERROR,
                                executionState = WorkflowExecutionState(
                                    workflowId = pendingAuth.workflowId,
                                    workflowName = pendingAuth.workflowName,
                                    status = "FAILED",
                                    currentStepIndex = 0,
                                    totalSteps = pendingAuth.items.size,
                                    stepLogs = listOf(msg),
                                    verificationMessage = msg,
                                    isAwaitingConfirmation = false,
                                    errorCode = AutomationErrorCode.CREDENTIAL_AUTH_CANCELLED
                                ),
                                snackbarNotice = msg
                            )
                        }
                        return@launch
                    }
                    is VaultAuthResult.Failed,
                    is VaultAuthResult.Unavailable,
                    is VaultAuthResult.TimedOut -> {
                        val reason = when (authRes) {
                            is VaultAuthResult.Failed -> authRes.reason
                            is VaultAuthResult.Unavailable -> authRes.reason
                            is VaultAuthResult.TimedOut -> authRes.reason
                            else -> "Credential authentication failed."
                        }
                        val wf = repository.getAutomationWorkflowById(pendingAuth.workflowId)
                        if (wf != null) {
                            val total = AutomationStepCodec.decode(wf.stepsJson).size
                            val execId = repository.recordAutomationExecutionStart(wf, total)
                            repository.finishAutomationExecution(wf.id, execId, "FAILED", 0, total, reason)
                        }
                        pendingInterruptedResumeCheckpoint = null
                        _uiState.update {
                            it.copy(
                                pendingCredentialReplayAuth = null,
                                automationToolbarStatus = AutomationToolbarStatus.ERROR,
                                executionState = WorkflowExecutionState(
                                    workflowId = pendingAuth.workflowId,
                                    workflowName = pendingAuth.workflowName,
                                    status = "FAILED",
                                    currentStepIndex = 0,
                                    totalSteps = pendingAuth.items.size,
                                    stepLogs = listOf(reason),
                                    verificationMessage = reason,
                                    isAwaitingConfirmation = false,
                                    errorCode = AutomationErrorCode.CREDENTIAL_AUTH_FAILED
                                ),
                                snackbarNotice = reason
                            )
                        }
                        return@launch
                    }
                }
            }
            val resumeCp = pendingInterruptedResumeCheckpoint
            pendingInterruptedResumeCheckpoint = null
            val savedPlayOptions = pendingCredentialPlayOptions
            pendingCredentialPlayOptions = null
            _uiState.update { it.copy(pendingCredentialReplayAuth = null, executionState = null) }
            runWorkflow(
                workflowId = pendingAuth.workflowId,
                confirmDestructive = true,
                confirmCredentialReplay = true,
                authorizedCredentialIds = grantedIds,
                resumeCheckpoint = resumeCp,
                playOptions = savedPlayOptions
            )
        }
    }

    fun cancelPendingCredentialReplay() {
        val pendingAuth = _uiState.value.pendingCredentialReplayAuth
        pendingInterruptedResumeCheckpoint = null
        pendingCredentialPlayOptions = null
        _uiState.update {
            it.copy(
                pendingCredentialReplayAuth = null,
                executionState = if (pendingAuth != null) {
                    WorkflowExecutionState(
                        workflowId = pendingAuth.workflowId,
                        workflowName = pendingAuth.workflowName,
                        status = "CANCELLED",
                        currentStepIndex = 0,
                        totalSteps = pendingAuth.items.size,
                        stepLogs = listOf("Credential replay authorization cancelled by user."),
                        verificationMessage = "Credential replay authorization cancelled by user.",
                        isAwaitingConfirmation = false,
                        errorCode = AutomationErrorCode.CREDENTIAL_AUTH_CANCELLED
                    )
                } else {
                    null
                },
                snackbarNotice = "Credential replay cancelled"
            )
        }
        refreshAutomationToolbarStatus(clearTransientResult = true)
    }

    // --- Phase 4B Credential Vault Management & Lifecycle Actions ---

    fun unlockCredentialVault() {
        viewModelScope.launch {
            when (val res = credentialVaultManager.unlockVault()) {
                is CredentialVaultOperationResult.Success -> {
                    _uiState.update {
                        it.copy(
                            vaultStatus = credentialVaultManager.refreshVaultStatus(),
                            vaultStatusMessage = "Credential Vault unlocked",
                            snackbarNotice = "Credential Vault unlocked"
                        )
                    }
                }
                is CredentialVaultOperationResult.KeyInvalidated -> {
                    _uiState.update {
                        it.copy(
                            vaultStatus = VaultStatus.KEY_INVALIDATED,
                            vaultStatusMessage = res.message,
                            snackbarNotice = res.message
                        )
                    }
                }
                is CredentialVaultOperationResult.AuthCancelled -> {
                    _uiState.update {
                        it.copy(
                            vaultStatus = credentialVaultManager.refreshVaultStatus(),
                            vaultStatusMessage = res.message,
                            snackbarNotice = res.message
                        )
                    }
                }
                is CredentialVaultOperationResult.AuthFailed -> {
                    _uiState.update {
                        it.copy(
                            vaultStatus = credentialVaultManager.refreshVaultStatus(),
                            vaultStatusMessage = res.message,
                            snackbarNotice = res.message
                        )
                    }
                }
                else -> Unit
            }
        }
    }

    fun lockCredentialVault(reason: String = "Credential Vault locked") {
        _uiState.value.revealedVaultCredential?.clear()
        credentialVaultManager.lockVault(reason)
        _uiState.update {
            it.copy(
                vaultStatus = credentialVaultManager.refreshVaultStatus(),
                revealedVaultCredential = null,
                vaultStatusMessage = reason,
                snackbarNotice = reason
            )
        }
    }

    fun addVaultCredential(
        websiteUrl: String,
        username: String,
        password: String,
        label: String = "",
        onSuccess: (CredentialVaultEntity) -> Unit = {}
    ) {
        val passChars = password.toCharArray()
        viewModelScope.launch {
            when (
                val res = credentialVaultManager.addCredential(
                    websiteUrl = websiteUrl,
                    username = username,
                    passwordChars = passChars,
                    label = label
                )
            ) {
                is CredentialVaultOperationResult.Success -> {
                    _uiState.update {
                        it.copy(
                            vaultStatus = credentialVaultManager.refreshVaultStatus(),
                            vaultStatusMessage = "Saved encrypted credential for ${res.value.displayOrigin}",
                            snackbarNotice = "Saved credential for ${res.value.displayOrigin}"
                        )
                    }
                    onSuccess(res.value)
                }
                is CredentialVaultOperationResult.InvalidOrigin -> {
                    _uiState.update { it.copy(vaultStatusMessage = res.message, snackbarNotice = res.message) }
                }
                is CredentialVaultOperationResult.KeyInvalidated -> {
                    _uiState.update {
                        it.copy(
                            vaultStatus = VaultStatus.KEY_INVALIDATED,
                            vaultStatusMessage = res.message,
                            snackbarNotice = res.message
                        )
                    }
                }
                is CredentialVaultOperationResult.AuthCancelled -> {
                    _uiState.update { it.copy(vaultStatusMessage = res.message, snackbarNotice = res.message) }
                }
                is CredentialVaultOperationResult.AuthFailed -> {
                    _uiState.update { it.copy(vaultStatusMessage = res.message, snackbarNotice = res.message) }
                }
                is CredentialVaultOperationResult.EncryptionFailed -> {
                    _uiState.update { it.copy(vaultStatusMessage = res.message, snackbarNotice = res.message) }
                }
                else -> Unit
            }
        }
    }

    fun updateVaultCredential(
        credentialId: String,
        websiteUrl: String,
        username: String,
        password: String,
        label: String = "",
        onSuccess: (CredentialVaultEntity) -> Unit = {}
    ) {
        val passChars = password.toCharArray()
        viewModelScope.launch {
            when (
                val res = credentialVaultManager.updateCredential(
                    credentialId = credentialId,
                    websiteUrl = websiteUrl,
                    username = username,
                    passwordChars = passChars,
                    label = label
                )
            ) {
                is CredentialVaultOperationResult.Success -> {
                    _uiState.value.revealedVaultCredential?.let { revealed ->
                        if (revealed.credentialId == credentialId) {
                            revealed.clear()
                        }
                    }
                    _uiState.update {
                        it.copy(
                            vaultStatus = credentialVaultManager.refreshVaultStatus(),
                            revealedVaultCredential = if (it.revealedVaultCredential?.credentialId == credentialId) {
                                null
                            } else {
                                it.revealedVaultCredential
                            },
                            vaultStatusMessage = "Updated credential for ${res.value.displayOrigin}",
                            snackbarNotice = "Updated credential for ${res.value.displayOrigin}"
                        )
                    }
                    onSuccess(res.value)
                }
                is CredentialVaultOperationResult.InvalidOrigin -> {
                    _uiState.update { it.copy(vaultStatusMessage = res.message, snackbarNotice = res.message) }
                }
                is CredentialVaultOperationResult.NotFound -> {
                    _uiState.update { it.copy(vaultStatusMessage = res.message, snackbarNotice = res.message) }
                }
                is CredentialVaultOperationResult.KeyInvalidated -> {
                    _uiState.update {
                        it.copy(
                            vaultStatus = VaultStatus.KEY_INVALIDATED,
                            vaultStatusMessage = res.message,
                            snackbarNotice = res.message
                        )
                    }
                }
                is CredentialVaultOperationResult.AuthCancelled -> {
                    _uiState.update { it.copy(vaultStatusMessage = res.message, snackbarNotice = res.message) }
                }
                is CredentialVaultOperationResult.AuthFailed -> {
                    _uiState.update { it.copy(vaultStatusMessage = res.message, snackbarNotice = res.message) }
                }
                is CredentialVaultOperationResult.EncryptionFailed -> {
                    _uiState.update { it.copy(vaultStatusMessage = res.message, snackbarNotice = res.message) }
                }
                else -> Unit
            }
        }
    }

    fun revealVaultCredential(credentialId: String) {
        viewModelScope.launch {
            _uiState.value.revealedVaultCredential?.clear()
            when (val res = credentialVaultManager.revealCredential(credentialId)) {
                is CredentialVaultOperationResult.Success -> {
                    _uiState.update {
                        it.copy(
                            vaultStatus = credentialVaultManager.refreshVaultStatus(),
                            revealedVaultCredential = res.value,
                            vaultStatusMessage = null
                        )
                    }
                }
                is CredentialVaultOperationResult.AuthCancelled -> {
                    _uiState.update {
                        it.copy(
                            revealedVaultCredential = null,
                            vaultStatusMessage = res.message,
                            snackbarNotice = res.message
                        )
                    }
                }
                is CredentialVaultOperationResult.AuthFailed -> {
                    _uiState.update {
                        it.copy(
                            revealedVaultCredential = null,
                            vaultStatusMessage = res.message,
                            snackbarNotice = res.message
                        )
                    }
                }
                is CredentialVaultOperationResult.KeyInvalidated -> {
                    _uiState.update {
                        it.copy(
                            vaultStatus = VaultStatus.KEY_INVALIDATED,
                            revealedVaultCredential = null,
                            vaultStatusMessage = res.message,
                            snackbarNotice = res.message
                        )
                    }
                }
                is CredentialVaultOperationResult.CorruptedData -> {
                    _uiState.update {
                        it.copy(
                            revealedVaultCredential = null,
                            vaultStatusMessage = res.message,
                            snackbarNotice = res.message
                        )
                    }
                }
                is CredentialVaultOperationResult.DecryptionFailed -> {
                    _uiState.update {
                        it.copy(
                            revealedVaultCredential = null,
                            vaultStatusMessage = res.message,
                            snackbarNotice = res.message
                        )
                    }
                }
                is CredentialVaultOperationResult.NotFound -> {
                    _uiState.update {
                        it.copy(
                            revealedVaultCredential = null,
                            vaultStatusMessage = res.message,
                            snackbarNotice = res.message
                        )
                    }
                }
                else -> Unit
            }
        }
    }

    fun hideRevealedVaultCredential() {
        _uiState.value.revealedVaultCredential?.clear()
        _uiState.update { it.copy(revealedVaultCredential = null) }
    }

    fun deleteVaultCredential(credentialId: String) {
        viewModelScope.launch {
            when (val res = credentialVaultManager.deleteCredential(credentialId)) {
                is CredentialVaultOperationResult.Success -> {
                    if (_uiState.value.revealedVaultCredential?.credentialId == credentialId) {
                        _uiState.value.revealedVaultCredential?.clear()
                    }
                    _uiState.update {
                        it.copy(
                            revealedVaultCredential = if (it.revealedVaultCredential?.credentialId == credentialId) {
                                null
                            } else {
                                it.revealedVaultCredential
                            },
                            vaultStatusMessage = "Credential deleted from Vault",
                            snackbarNotice = "Credential deleted from Vault"
                        )
                    }
                }
                is CredentialVaultOperationResult.AuthCancelled -> {
                    _uiState.update { it.copy(vaultStatusMessage = res.message, snackbarNotice = res.message) }
                }
                is CredentialVaultOperationResult.AuthFailed -> {
                    _uiState.update { it.copy(vaultStatusMessage = res.message, snackbarNotice = res.message) }
                }
                is CredentialVaultOperationResult.NotFound -> {
                    _uiState.update { it.copy(vaultStatusMessage = res.message, snackbarNotice = res.message) }
                }
                else -> Unit
            }
        }
    }

    fun resetInvalidatedCredentialVault() {
        viewModelScope.launch {
            _uiState.value.revealedVaultCredential?.clear()
            when (val res = credentialVaultManager.resetVaultAfterUserConfirmation()) {
                is CredentialVaultOperationResult.Success -> {
                    _uiState.update {
                        it.copy(
                            vaultStatus = credentialVaultManager.refreshVaultStatus(),
                            revealedVaultCredential = null,
                            vaultStatusMessage = "Credential Vault reset with a new encryption key",
                            snackbarNotice = "Credential Vault reset with a new encryption key"
                        )
                    }
                }
                is CredentialVaultOperationResult.AuthCancelled -> {
                    _uiState.update { it.copy(vaultStatusMessage = res.message, snackbarNotice = res.message) }
                }
                else -> Unit
            }
        }
    }

    fun onAppBackgrounded() {
        _uiState.value.revealedVaultCredential?.clear()
        credentialFillCoordinator.notifyLifecycleAbort("Credential replay stopped because the application moved to the background.")
        credentialVaultManager.authManager.onAppBackgrounded()
        val status = credentialVaultManager.refreshVaultStatus()
        if (automationExecutionJob?.isActive == true) {
            executionLifecycleController.requestInterrupt(
                code = AutomationErrorCode.CONTEXT_INVALIDATED,
                message = "Execution interrupted because the application moved to the background."
            )
            automationExecutionJob?.cancel()
        }
        _uiState.update { state ->
            state.copy(
                vaultStatus = status,
                revealedVaultCredential = null,
                pendingCredentialReplayAuth = null
            )
        }
    }

    fun onDeviceLocked() {
        _uiState.value.revealedVaultCredential?.clear()
        credentialFillCoordinator.notifyLifecycleAbort("Credential replay stopped because the device screen locked.")
        credentialVaultManager.authManager.onDeviceLocked()
        val status = credentialVaultManager.refreshVaultStatus()
        if (automationExecutionJob?.isActive == true) {
            executionLifecycleController.requestInterrupt(
                code = AutomationErrorCode.CONTEXT_INVALIDATED,
                message = "Execution interrupted because the device screen locked."
            )
            automationExecutionJob?.cancel()
        }
        _uiState.update { state ->
            state.copy(
                vaultStatus = status,
                revealedVaultCredential = null,
                pendingCredentialReplayAuth = null
            )
        }
    }

    fun pauseWorkflowExecution() {
        if (!executionLifecycleController.requestPause()) return
        _uiState.update { state ->
            val currentExec = state.executionState ?: return@update state
            val stepNum = (currentExec.currentStepIndex + 1).coerceIn(1, currentExec.totalSteps.coerceAtLeast(1))
            state.copy(
                automationToolbarStatus = AutomationToolbarStatus.PAUSED,
                executionState = currentExec.copy(
                    status = "PAUSED",
                    elapsedMs = if (currentExec.startedAtMs > 0L) {
                        (System.currentTimeMillis() - currentExec.startedAtMs).coerceAtLeast(0L)
                    } else {
                        currentExec.elapsedMs
                    },
                    verificationMessage = "Paused at step $stepNum/${currentExec.totalSteps}",
                    stepLogs = currentExec.stepLogs + "Paused at step $stepNum/${currentExec.totalSteps}"
                )
            )
        }
    }

    fun resumeWorkflowExecution() {
        if (!executionLifecycleController.requestResume()) return
        _uiState.update { state ->
            val currentExec = state.executionState ?: return@update state
            state.copy(
                automationToolbarStatus = AutomationToolbarStatus.EXECUTING,
                executionState = currentExec.copy(
                    status = "RUNNING",
                    verificationMessage = "Resuming '${currentExec.workflowName}'…",
                    stepLogs = currentExec.stepLogs + "Resumed execution"
                )
            )
        }
    }

    fun cancelWorkflowExecution() {
        executionLifecycleController.requestCancel(
            code = AutomationErrorCode.CANCELLED,
            message = "Execution cancelled by user."
        )
        val activeJob = automationExecutionJob
        if (activeJob != null && activeJob.isActive) {
            activeJob.cancel()
            return
        }
        val currentExec = _uiState.value.executionState
        if (currentExec?.executionStatus?.isActive == true) {
            _uiState.update { state ->
                state.copy(
                    executionState = currentExec.copy(
                        status = "CANCELLED",
                        verificationMessage = "Execution cancelled.",
                        isAwaitingConfirmation = false,
                        errorCode = AutomationErrorCode.CANCELLED
                    )
                )
            }
            refreshAutomationToolbarStatus(clearTransientResult = true)
        }
    }

    fun confirmPendingDestructiveExecution() {
        val exec = _uiState.value.executionState ?: return
        if (!exec.isAwaitingConfirmation) return
        val targetId = pendingDestructiveWorkflowId
        val wasDraft = pendingDestructiveInstructionConfirm
        val savedPlayOptions = pendingDestructivePlayOptions
        pendingDestructiveWorkflowId = null
        pendingDestructiveInstructionConfirm = false
        pendingDestructivePlayOptions = null
        _uiState.update { it.copy(executionState = null) }
        if (wasDraft) {
            executeInstructionDraft(confirmDestructive = true)
        } else if (targetId != null) {
            runWorkflow(targetId, confirmDestructive = true, playOptions = savedPlayOptions)
        }
    }

    fun cancelPendingDestructiveExecution() {
        pendingDestructiveWorkflowId = null
        pendingDestructiveInstructionConfirm = false
        pendingDestructivePlayOptions = null
        _uiState.update { it.copy(executionState = null) }
        refreshAutomationToolbarStatus(clearTransientResult = true)
    }

    fun clearExecutionBanner() {
        _uiState.update { it.copy(executionState = null) }
        refreshAutomationToolbarStatus(clearTransientResult = true)
    }

    fun exportAutomationsJson(onExportReady: (String) -> Unit) {
        viewModelScope.launch {
            val json = repository.exportAllWorkflowsPackageJson(includeDependencies = true)
            onExportReady(json)
        }
    }

    fun importAutomationsJson(rawJson: String) {
        importWorkflowPackageJson(rawJson, WorkflowConflictResolution.CREATE_COPY)
    }

    // --- Phase 8 Workflow Lifecycle, Validation, Dry-Run, Organization & Portability Actions ---

    fun validateWorkflow(workflowId: Long) {
        viewModelScope.launch {
            val report = repository.validateWorkflowDetailed(workflowId) ?: return@launch
            _uiState.update { state ->
                state.copy(
                    validationReportsByWorkflowId = state.validationReportsByWorkflowId + (workflowId to report)
                )
            }
        }
    }

    fun runWorkflowDryRun(
        workflowId: Long,
        runtimeInputs: Map<String, String> = emptyMap()
    ) {
        viewModelScope.launch {
            val activeUrl = _uiState.value.activeTab.url
            val passiveSnapshot = _uiState.value.pageInspection.elements
            val report = repository.runWorkflowDryRun(
                workflowId = workflowId,
                runtimeInputs = runtimeInputs,
                currentPageUrl = activeUrl,
                passiveDomSnapshot = passiveSnapshot
            ) ?: return@launch
            _uiState.update { state ->
                state.copy(
                    activeDryRunReport = report,
                    validationReportsByWorkflowId = state.validationReportsByWorkflowId + (workflowId to report.validationReport)
                )
            }
        }
    }

    fun clearDryRunReport() {
        _uiState.update { it.copy(activeDryRunReport = null) }
    }

    fun loadWorkflowVersions(workflowId: Long) {
        viewModelScope.launch {
            val versions = repository.getWorkflowVersionsOnce(workflowId)
            _uiState.update { it.copy(activeWorkflowVersions = versions) }
        }
    }

    fun compareWorkflowVersions(workflowId: Long, baseVersion: Int, targetVersion: Int) {
        viewModelScope.launch {
            val diff = repository.compareWorkflowVersions(workflowId, baseVersion, targetVersion)
            _uiState.update { it.copy(activeDiffReport = diff) }
        }
    }

    fun clearDiffReport() {
        _uiState.update { it.copy(activeDiffReport = null) }
    }

    fun restoreWorkflowVersion(workflowId: Long, targetVersion: Int) {
        viewModelScope.launch {
            runCatching { repository.restoreWorkflowVersion(workflowId, targetVersion) }
                .onSuccess { res ->
                    loadWorkflowVersions(workflowId)
                    validateWorkflow(workflowId)
                    _uiState.update { it.copy(snackbarNotice = "Restored v$targetVersion as v${res.versionNumber}") }
                }
                .onFailure { ex ->
                    _uiState.update {
                        it.copy(
                            snackbarNotice = if (ex is WorkflowConcurrencyConflictException) {
                                ex.message
                            } else {
                                "Failed to restore v$targetVersion: ${ex.message ?: "error"}"
                            }
                        )
                    }
                }
        }
    }

    fun updateWorkflowOrganization(workflowId: Long, organization: WorkflowOrganizationMetadata) {
        viewModelScope.launch {
            repository.updateWorkflowOrganization(workflowId, organization)
            _uiState.update { it.copy(snackbarNotice = "Updated workflow organization") }
        }
    }

    fun toggleWorkflowFavorite(workflowId: Long) {
        viewModelScope.launch {
            repository.toggleWorkflowFavorite(workflowId)
        }
    }

    fun archiveWorkflow(workflowId: Long) {
        viewModelScope.launch {
            repository.setWorkflowArchived(workflowId, archived = true)
            validateWorkflow(workflowId)
            _uiState.update { it.copy(snackbarNotice = "Workflow archived (execution disabled)") }
        }
    }

    fun restoreArchivedWorkflow(workflowId: Long) {
        viewModelScope.launch {
            repository.setWorkflowArchived(workflowId, archived = false)
            validateWorkflow(workflowId)
            _uiState.update { it.copy(snackbarNotice = "Workflow restored from Archive") }
        }
    }

    fun setWorkspaceSortOption(option: WorkflowSortOption) {
        _uiState.update { it.copy(workspaceSortOption = option) }
    }

    fun setWorkspaceFolderFilter(folder: String?) {
        _uiState.update { it.copy(workspaceFolderFilter = folder?.takeIf { f -> f.isNotBlank() }) }
    }

    fun setWorkspaceTagFilter(tag: String?) {
        _uiState.update { it.copy(workspaceTagFilter = tag?.takeIf { t -> t.isNotBlank() }) }
    }

    fun exportSingleWorkflowPackageJson(
        workflowId: Long,
        includeDependencies: Boolean,
        onReady: (String) -> Unit
    ) {
        viewModelScope.launch {
            val json = repository.exportWorkflowPackageJson(workflowId, includeDependencies)
            onReady(json)
        }
    }

    fun exportWorkflowsZipBytes(
        workflowIds: List<Long>? = null,
        packageType: ExportPackageType = ExportPackageType.COLLECTION,
        includeDependencies: Boolean = true,
        onReady: (ByteArray) -> Unit
    ) {
        viewModelScope.launch {
            val bytes = repository.exportWorkflowsZipBytes(workflowIds, packageType, includeDependencies)
            onReady(bytes)
        }
    }

    fun importWorkflowPackageJson(
        rawJson: String,
        conflictResolution: WorkflowConflictResolution = WorkflowConflictResolution.CREATE_COPY
    ) {
        viewModelScope.launch {
            val result = repository.importWorkflowPackageJson(rawJson, conflictResolution)
            _uiState.update {
                it.copy(
                    lastWorkflowImportResult = result,
                    snackbarNotice = result.summary
                )
            }
            result.importedWorkflowIds.forEach { wfId -> validateWorkflow(wfId) }
        }
    }

    fun importWorkflowsZipBytes(
        zipBytes: ByteArray,
        conflictResolution: WorkflowConflictResolution = WorkflowConflictResolution.CREATE_COPY
    ) {
        viewModelScope.launch {
            val result = repository.importWorkflowsZipBytes(zipBytes, conflictResolution)
            _uiState.update {
                it.copy(
                    lastWorkflowImportResult = result,
                    snackbarNotice = result.summary
                )
            }
            result.importedWorkflowIds.forEach { wfId -> validateWorkflow(wfId) }
        }
    }

    fun clearLastWorkflowImportResult() {
        _uiState.update { it.copy(lastWorkflowImportResult = null) }
    }

    private fun navigateTabUrlForAutomation(tabId: String, rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return
        val prefs = preferencesState.value
        val normalized = privacyManager.normalizeAndUpgradeUrl(
            rawInput = trimmed,
            httpsUpgradeEnabled = prefs.httpsUpgradeEnabled,
            searchEngineTemplate = prefs.searchEngineTemplate
        )
        handleOriginNavigationForRecording(tabId, normalized.finalUrl)
        navigationSynchronizer.beginNavigation(tabId, normalized.finalUrl)
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map { tab ->
                    if (tab.id == tabId) {
                        tab.copy(
                            url = normalized.finalUrl,
                            requestedUrl = normalized.finalUrl,
                            loadRequestId = tab.loadRequestId + 1L,
                            title = deriveShortTitle(normalized.finalUrl),
                            isHttps = normalized.finalUrl.startsWith("https://") || normalized.isBuiltInScheme,
                            isLoading = !normalized.finalUrl.equals("apex://home", ignoreCase = true),
                            progress = if (normalized.finalUrl.equals("apex://home", ignoreCase = true)) 1f else 0.08f
                        )
                    } else {
                        tab
                    }
                }
            )
        }
    }

    private fun refreshAutomationToolbarStatus(clearTransientResult: Boolean) {
        _uiState.update { state ->
            val currentToolbar = state.automationToolbarStatus
            val newStatus = when {
                state.recordingSession.status == RecordingStatus.RECORDING ->
                    AutomationToolbarStatus.RECORDING
                state.recordingSession.status == RecordingStatus.PAUSED ||
                    state.executionState?.status == "PAUSED" ||
                    state.executionState?.status == "INTERRUPTED" ->
                    AutomationToolbarStatus.PAUSED
                state.executionState?.status == "RUNNING" ->
                    AutomationToolbarStatus.EXECUTING
                !clearTransientResult && currentToolbar == AutomationToolbarStatus.COMPLETED ->
                    AutomationToolbarStatus.COMPLETED
                !clearTransientResult &&
                    (state.executionState?.status == "FAILED" ||
                        state.pageInspection.errorMessage != null ||
                        state.recordingSession.status == RecordingStatus.ERROR) ->
                    AutomationToolbarStatus.ERROR
                state.pageInspection.elements.isNotEmpty() ->
                    AutomationToolbarStatus.ELEMENTS_DETECTED
                else -> AutomationToolbarStatus.IDLE
            }
            state.copy(automationToolbarStatus = newStatus)
        }
    }

    // --- Preferences & Settings Controls ---

    fun toggleShields(enabled: Boolean) {
        viewModelScope.launch { repository.setShieldsEnabled(enabled) }
    }

    fun toggleHttpsUpgrade(enabled: Boolean) {
        viewModelScope.launch { repository.setHttpsUpgradeEnabled(enabled) }
    }

    fun toggleAggressivePreload(enabled: Boolean) {
        viewModelScope.launch { repository.setAggressivePreloadEnabled(enabled) }
    }

    fun toggleScriptGuard(enabled: Boolean) {
        viewModelScope.launch { repository.setScriptGuardEnabled(enabled) }
    }

    fun toggleDesktopViewport(enabled: Boolean) {
        viewModelScope.launch {
            repository.setDesktopViewportEnabled(enabled)
            val currentPrefs = preferencesState.value
            val tabsById = _uiState.value.tabs.associateBy { it.id }
            webViewRegistry.forEach { (tabId, wv) ->
                val tab = tabsById[tabId]
                val isPrivate = tab?.isPrivate == true
                performanceOptimizer.configureFastWebView(
                    webView = wv,
                    scriptGuardEnabled = currentPrefs.scriptGuardEnabled,
                    desktopMode = enabled,
                    aggressiveCaching = currentPrefs.aggressivePreloadEnabled,
                    isIncognito = isPrivate,
                    textZoomPercent = currentPrefs.effectiveZoomForUrl(tab?.url)
                )
                performanceOptimizer.applyDesktopViewportMode(wv, enabled)
            }
            _uiState.update {
                it.copy(
                    snackbarNotice = if (enabled) "Desktop site enabled" else "Mobile site enabled"
                )
            }
        }
    }

    fun toggleDynamicColor(enabled: Boolean) {
        viewModelScope.launch { repository.setDynamicColorEnabled(enabled) }
    }

    fun selectSeedPalette(preset: SeedPalettePreset) {
        viewModelScope.launch { repository.setSeedPaletteOrdinal(preset.ordinal) }
    }

    fun selectThemeMode(mode: ThemeModeOption) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun cycleSeedTheme() {
        viewModelScope.launch { repository.cycleSeedPalette() }
    }

    fun ensureHomeWallpaperForTab(seed: String, forceRefresh: Boolean = false) {
        if (seed.isBlank()) return
        if (preferencesState.value.homeWallpaperMode != HomeWallpaperMode.ONLINE_API) return
        if (!forceRefresh && _homeWallpapersBySeed.value.containsKey(seed)) return
        if (!inFlightWallpaperSeeds.add(seed)) return

        viewModelScope.launch {
            _isHomeWallpaperLoading.value = true
            try {
                val loaded = wallpaperEngine.loadWallpaperForSeed(
                    seed = seed,
                    forceRefresh = forceRefresh
                )
                if (loaded != null) {
                    _homeWallpapersBySeed.update { current ->
                        val activeSeeds = _uiState.value.tabs.map { it.homeWallpaperSeed }.toSet()
                        val pruned = if (current.size > 16) {
                            current.filterKeys { it in activeSeeds || it == seed }
                        } else {
                            current
                        }
                        pruned + (seed to loaded)
                    }
                }
            } finally {
                inFlightWallpaperSeeds.remove(seed)
                _isHomeWallpaperLoading.value = inFlightWallpaperSeeds.isNotEmpty()
            }
        }
    }

    fun refreshActiveHomeWallpaper() {
        val active = _uiState.value.activeTab
        val newSeed = UUID.randomUUID().toString()
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map { tab ->
                    if (tab.id == active.id) {
                        tab.copy(homeWallpaperSeed = newSeed)
                    } else {
                        tab
                    }
                }
            )
        }
        ensureHomeWallpaperForTab(newSeed, forceRefresh = true)
    }

    fun selectHomeWallpaperMode(mode: HomeWallpaperMode) {
        viewModelScope.launch {
            repository.setHomeWallpaperMode(mode)
            if (mode == HomeWallpaperMode.ONLINE_API) {
                _uiState.value.tabs.forEach { tab ->
                    if (tab.isHomePage) {
                        ensureHomeWallpaperForTab(tab.homeWallpaperSeed)
                    }
                }
            }
        }
    }

    fun setCustomHomeWallpaperFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val savedPath = wallpaperEngine.saveCustomWallpaperFromUri(context, uri)
            if (savedPath != null) {
                val bitmap = wallpaperEngine.loadCustomWallpaperBitmap(savedPath)
                _customHomeWallpaperBitmap.value = bitmap
                repository.setCustomHomeWallpaperPath(savedPath)
                repository.setHomeWallpaperMode(HomeWallpaperMode.CUSTOM_PHONE)
                _uiState.update { it.copy(snackbarNotice = "Home wallpaper updated") }
            } else {
                _uiState.update { it.copy(snackbarNotice = "Could not load selected image") }
            }
        }
    }

    fun toggleHomeWallpaperBlackAndWhite(enabled: Boolean) {
        viewModelScope.launch {
            repository.setHomeWallpaperBlackAndWhite(enabled)
        }
    }

    fun setDefaultZoomPercent(percent: Int) {
        val clamped = BrowserPreferences.clampZoomPercent(percent)
        viewModelScope.launch {
            repository.setDefaultZoomPercent(clamped)
            syncWebViewAccessibilitySettings()
        }
    }

    fun setSiteZoom(hostOrUrl: String, percent: Int) {
        val clamped = BrowserPreferences.clampZoomPercent(percent)
        viewModelScope.launch {
            repository.setSiteZoom(hostOrUrl, clamped)
            syncWebViewAccessibilitySettings()
        }
    }

    fun removeSiteZoom(hostOrUrl: String) {
        viewModelScope.launch {
            repository.removeSiteZoom(hostOrUrl)
            syncWebViewAccessibilitySettings()
        }
    }

    fun clearSavedSiteZooms() {
        viewModelScope.launch {
            repository.clearSavedSiteZooms()
            syncWebViewAccessibilitySettings()
        }
    }

    fun adjustActiveZoomPercent(newPercent: Int) {
        val clamped = BrowserPreferences.clampZoomPercent(newPercent)
        val active = _uiState.value.activeTab
        val host = if (active.isHomePage) "" else BrowserPreferences.normalizeZoomHost(active.url)
        if (host.isNotEmpty()) {
            setSiteZoom(host, clamped)
        } else {
            setDefaultZoomPercent(clamped)
        }
    }

    fun resetActiveSiteZoom() {
        val active = _uiState.value.activeTab
        val host = if (active.isHomePage) "" else BrowserPreferences.normalizeZoomHost(active.url)
        if (host.isNotEmpty()) {
            removeSiteZoom(host)
        } else {
            setDefaultZoomPercent(100)
        }
    }

    fun toggleShowZoomInMainMenu(enabled: Boolean) {
        viewModelScope.launch { repository.setShowZoomInMainMenu(enabled) }
    }

    fun toggleForceEnableZoom(enabled: Boolean) {
        viewModelScope.launch {
            repository.setForceEnableZoom(enabled)
            webViewRegistry.values.forEach { wv ->
                performanceOptimizer.applyForceEnableZoom(wv, enabled)
            }
        }
    }

    fun toggleSimplifiedView(enabled: Boolean) {
        viewModelScope.launch { repository.setSimplifiedViewEnabled(enabled) }
    }

    fun toggleCaretBrowsing(enabled: Boolean, announceShortcut: Boolean = false) {
        viewModelScope.launch {
            repository.setCaretBrowsingEnabled(enabled)
            webViewRegistry.values.forEach { wv ->
                performanceOptimizer.applyCaretBrowsing(wv, enabled)
            }
            if (announceShortcut) {
                _uiState.update {
                    it.copy(
                        snackbarNotice = if (enabled) {
                            "Caret browsing enabled (F7)"
                        } else {
                            "Caret browsing disabled (F7)"
                        }
                    )
                }
            }
        }
    }

    fun toggleTouchpadSwipeNavigation(enabled: Boolean) {
        viewModelScope.launch { repository.setTouchpadSwipeNavigationEnabled(enabled) }
    }

    private fun syncWebViewAccessibilitySettings() {
        val prefs = preferencesState.value
        val tabsById = _uiState.value.tabs.associateBy { it.id }
        webViewRegistry.forEach { (tabId, wv) ->
            val tabUrl = tabsById[tabId]?.url
            wv.settings.textZoom = prefs.effectiveZoomForUrl(tabUrl)
        }
    }

    fun resetPrivacyStats() {
        viewModelScope.launch {
            repository.resetTelemetryStats()
            performanceOptimizer.wipeIncognitoSessionStorage()
            _uiState.update {
                it.copy(snackbarNotice = "Search history and browsing data cleared")
            }
        }
    }

    // --- Extension 1: Nano Multi-Agent Automation ---

    fun runNanoTask(userPrompt: String) {
        val cleanPrompt = userPrompt.trim()
        if (cleanPrompt.isEmpty() || _uiState.value.isNanoRunning) return

        val userMsg = NanoChatMessage(
            id = "u_${System.currentTimeMillis()}",
            isUser = true,
            text = cleanPrompt
        )
        val agentMsgId = "a_${System.currentTimeMillis()}"
        val placeholderAgentMsg = NanoChatMessage(
            id = agentMsgId,
            isUser = false,
            text = "Executing multi-agent DOM workflow…",
            steps = emptyList(),
            isRunning = true
        )

        _uiState.update {
            it.copy(
                nanoMessages = it.nanoMessages + userMsg + placeholderAgentMsg,
                isNanoRunning = true
            )
        }

        viewModelScope.launch {
            val prefs = repository.preferencesFlow.first()
            if (!prefs.canPlayAutomationToday()) {
                _uiState.update { state ->
                    state.copy(
                        isNanoRunning = false,
                        nanoMessages = state.nanoMessages.filterNot { it.id == agentMsgId },
                        isSyncBackupDialogOpen = true,
                        subscriptionPromptReason = if (prefs.isProExpired()) {
                            "Your ${prefs.proPackage.displayName} Apex Pro pass has expired. Select a package and activate a new key to continue."
                        } else {
                            "Daily free limit reached (${BrowserPreferences.FREE_DAILY_AUTOMATION_PLAY_LIMIT}/${BrowserPreferences.FREE_DAILY_AUTOMATION_PLAY_LIMIT} plays used today). Select a package and activate a key to unlock Apex Pro."
                        },
                        proActivationError = null,
                        snackbarNotice = "Daily 5-play limit reached — Activate Apex Pro to continue"
                    )
                }
                return@launch
            }
            if (!prefs.isProCurrentlyActive()) {
                repository.recordAutomationPlayUsage()
            }
            val startTabId = _uiState.value.activeTabId
            val startTabUrl = _uiState.value.activeTab.url
            val outcome = nanoEngine.runTask(
                prompt = cleanPrompt,
                currentUrl = startTabUrl,
                tabId = startTabId,
                webViewProvider = { webViewRegistry[_uiState.value.activeTabId] },
                onNavigateUrl = { destUrl ->
                    submitNavigation(destUrl, _uiState.value.activeTabId)
                },
                onStepUpdate = { liveSteps ->
                    val latestActive = liveSteps.lastOrNull { it.status == StepStatus.RUNNING || it.status == StepStatus.COMPLETED }
                    _uiState.update { state ->
                        val syncedInspection = automationEngine.universalScanner.getLatestInspection(_uiState.value.activeTabId)
                            ?: state.pageInspection
                        val matchedEl = syncedInspection.elements.firstOrNull { it.visible && it.interactable }
                        state.copy(
                            pageInspection = syncedInspection,
                            activeDomHighlight = if (latestActive != null) {
                                ActiveDomElementHighlight(
                                    selector = matchedEl?.selector ?: "body",
                                    targetLabel = latestActive.actionTitle,
                                    actionLabel = latestActive.role.badge,
                                    tagName = matchedEl?.tagName.orEmpty(),
                                    boundingBox = matchedEl?.boundingBox,
                                    isScanning = latestActive.role == AgentRole.PLANNER,
                                    stepIndex = liveSteps.indexOf(latestActive) + 1,
                                    totalSteps = liveSteps.size,
                                    status = if (latestActive.status == StepStatus.RUNNING) "INTERACTING" else "VERIFIED"
                                )
                            } else {
                                state.activeDomHighlight
                            },
                            nanoMessages = state.nanoMessages.map { msg ->
                                if (msg.id == agentMsgId) msg.copy(steps = liveSteps) else msg
                            }
                        )
                    }
                }
            )

            val updatedInspection = automationEngine.universalScanner.getLatestInspection(startTabId)
            if (updatedInspection != null) {
                pageInspectionByTab[startTabId] = updatedInspection
            }
            _uiState.update { state ->
                val syncedInspection = if (state.activeTabId == startTabId && updatedInspection != null) {
                    updatedInspection
                } else {
                    state.pageInspection
                }
                state.copy(
                    isNanoRunning = false,
                    activeDomHighlight = null,
                    pageInspection = syncedInspection,
                    extractedRecords = if (syncedInspection.extractedRecords.isNotEmpty()) {
                        syncedInspection.extractedRecords
                    } else {
                        state.extractedRecords
                    },
                    nanoMessages = state.nanoMessages.map { msg ->
                        if (msg.id == agentMsgId) {
                            msg.copy(
                                text = outcome.finalSummary,
                                isRunning = false
                            )
                        } else {
                            msg
                        }
                    }
                )
            }
        }
    }

    // --- Extension 2: Retriever Local-First Web Scraper ---

    fun onRetrieverPromptChanged(value: String) {
        _uiState.update { it.copy(retrieverPromptInput = value) }
    }

    fun selectExtractionRule(ruleId: Long) {
        val rule = extractionRulesState.value.firstOrNull { it.id == ruleId }
        _uiState.update {
            it.copy(
                selectedRuleId = ruleId,
                retrieverPromptInput = rule?.promptDescription ?: it.retrieverPromptInput
            )
        }
    }

    fun setRetrieverDisplayMode(mode: RetrieverDisplayMode) {
        _uiState.update { it.copy(retrieverDisplayMode = mode) }
    }

    fun saveCurrentRetrieverRule() {
        val input = _uiState.value.retrieverPromptInput.trim()
        if (input.isEmpty()) return
        viewModelScope.launch {
            val built = retrieverEngine.buildRuleFromInput(input)
            val newId = repository.saveExtractionRule(built)
            _uiState.update {
                it.copy(
                    selectedRuleId = newId,
                    retrieverStatusBanner = "Saved rule '${built.name}' locally"
                )
            }
        }
    }

    fun deleteRetrieverRule(ruleId: Long) {
        viewModelScope.launch {
            repository.deleteExtractionRule(ruleId)
            if (_uiState.value.selectedRuleId == ruleId) {
                _uiState.update { it.copy(selectedRuleId = null) }
            }
        }
    }

    fun captureCurrentPageWithRetriever() {
        val state = _uiState.value
        if (state.isRetrieverRunning) return
        val rule = resolveActiveRule(state)
        val activeTab = state.activeTab

        _uiState.update {
            it.copy(
                isRetrieverRunning = true,
                retrieverStatusBanner = "Capturing DOM from '${activeTab.title}'…"
            )
        }

        viewModelScope.launch {
            val target = TabSnapshotTarget(
                tabId = activeTab.id,
                title = activeTab.title,
                url = activeTab.url
            )
            val records = retrieverEngine.extractFromSingleTab(
                target = target,
                webView = webViewRegistry[activeTab.id],
                rule = rule
            )
            if (rule.id != 0L) {
                repository.updateRuleRunCount(rule.id, records.size)
            }
            val syncedInspection = automationEngine.universalScanner.getLatestInspection(activeTab.id)
                ?: _uiState.value.pageInspection.copy(
                    tabId = activeTab.id,
                    url = activeTab.url,
                    domain = automationEngine.extractDomain(activeTab.url),
                    extractedRecords = records,
                    summary = _uiState.value.pageInspection.summary.copy(
                        structuredRecordsFound = records.size
                    )
                )
            pageInspectionByTab[activeTab.id] = syncedInspection
            _uiState.update {
                it.copy(
                    isRetrieverRunning = false,
                    extractedRecords = records,
                    pageInspection = if (it.activeTabId == activeTab.id) syncedInspection else it.pageInspection,
                    retrieverStatusBanner = "Captured ${records.size} structured rows from '${activeTab.title}'"
                )
            }
        }
    }

    fun runParallelMultiTabExtraction() {
        val state = _uiState.value
        if (state.isRetrieverRunning) return
        val rule = resolveActiveRule(state)
        val targets = state.tabs.map {
            TabSnapshotTarget(tabId = it.id, title = it.title, url = it.url)
        }

        _uiState.update {
            it.copy(
                isRetrieverRunning = true,
                retrieverStatusBanner = "Running parallel DOM extraction across ${targets.size} open tabs…"
            )
        }

        viewModelScope.launch {
            val records = retrieverEngine.extractAcrossTabsInParallel(
                tabs = targets,
                webViewLookup = { id -> webViewRegistry[id] },
                rule = rule
            )
            if (rule.id != 0L) {
                repository.updateRuleRunCount(rule.id, records.size)
            }
            _uiState.update {
                it.copy(
                    isRetrieverRunning = false,
                    extractedRecords = records,
                    retrieverStatusBanner = "Parallel run complete: ${records.size} rows across ${targets.size} tabs"
                )
            }
        }
    }

    private fun resolveActiveRule(state: BrowserUiState): ExtractionRuleEntity {
        val rules = extractionRulesState.value
        val byId = state.selectedRuleId?.let { id -> rules.firstOrNull { it.id == id } }
        if (byId != null && (state.retrieverPromptInput.isBlank() || state.retrieverPromptInput == byId.promptDescription)) {
            return byId
        }
        if (state.retrieverPromptInput.isNotBlank()) {
            return retrieverEngine.buildRuleFromInput(state.retrieverPromptInput)
        }
        return rules.firstOrNull() ?: retrieverEngine.buildRuleFromInput("Extract headlines, prices, and links")
    }

    private fun mapBaseUrlBackToScheme(loadedUrl: String, currentTabUrl: String): String {
        return when {
            loadedUrl.startsWith("https://news.apex.browser") -> "apex://news"
            loadedUrl.startsWith("https://store.apex.browser") -> "apex://store"
            currentTabUrl.startsWith("apex://") && loadedUrl == "about:blank" -> currentTabUrl
            else -> loadedUrl
        }
    }

    private fun deriveShortTitle(url: String): String {
        return when (url.lowercase()) {
            "apex://home" -> "Home"
            "apex://news" -> "AI News Pulse"
            "apex://store" -> "Apex Gear Store"
            else -> url.removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("www.")
                .substringBefore('/')
                .ifBlank { "Web Tab" }
        }
    }

    private fun destroyWebViewSafely(wv: WebView, isIncognito: Boolean) {
        runCatching {
            if (isIncognito) {
                performanceOptimizer.wipeIncognitoWebView(wv)
            } else {
                wv.stopLoading()
            }
            runCatching { wv.removeJavascriptInterface("ApexAutomationBridge") }
            runCatching { wv.removeJavascriptInterface("ApexPasswordBridge") }
            wv.webChromeClient = null
            wv.setDownloadListener(null)
            wv.setOnTouchListener(null)
            wv.setOnLongClickListener(null)
            wv.setOnKeyListener(null)
            wv.setOnGenericMotionListener(null)
            wv.setTag(-19048102, null)
            wv.onPause()
            wv.clearHistory()
            (wv.parent as? android.view.ViewGroup)?.removeView(wv)
            wv.removeAllViews()
            wv.destroy()
        }
    }

    override fun onCleared() {
        super.onCleared()
        suggestionJob?.cancel()
        backOnlineBannerJob?.cancel()
        blockedFlushJob?.cancel()
        toolbarCompletedResetJob?.cancel()
        recordingLifecycleJob?.cancel()
        if (executionLifecycleController.boundTabId.isNotEmpty()) {
            executionLifecycleController.requestCancel(
                code = AutomationErrorCode.CANCELLED,
                message = "Execution stopped because the browser ViewModel was cleared."
            )
        }
        automationExecutionJob?.cancel()
        credentialFillCoordinator.notifyLifecycleAbort("Browser session cleared.")
        pageScanJobByTab.values.forEach { it.cancel() }
        pageScanJobByTab.clear()
        pageInspectionByTab.clear()
        pendingBlockedByTab.clear()
        lastProgressUpdateAtByTab.clear()
        recordedSecretsByStepId.clear()
        latestExecutionCheckpoint = null
        pendingInterruptedResumeCheckpoint = null
        val hadIncognito = _uiState.value.tabs.any { it.isPrivate }
        handleTabsClosedForRecording(_uiState.value.tabs.map { it.id }.toSet())
        _uiState.value.tabs.forEach { tab ->
            val wv = webViewRegistry.remove(tab.id)
            automationEngine.universalScanner.invalidateTabSession(
                tabId = tab.id,
                reason = "ViewModel cleared",
                webView = wv
            )
            if (wv != null) {
                destroyWebViewSafely(wv, isIncognito = tab.isPrivate)
            }
        }
        webViewRegistry.values.forEach { destroyWebViewSafely(it, isIncognito = false) }
        webViewRegistry.clear()
        if (hadIncognito) {
            performanceOptimizer.wipeIncognitoSessionStorage()
        }
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val database = ApexDatabase.getInstance(appContext)
                    val prefsRepo = UserPreferencesRepository(appContext)
                    val sharedPrefs = appContext.getSharedPreferences(
                        "apex_browser_storage",
                        Context.MODE_PRIVATE
                    )
                    val repo = BrowserRepository(database.apexDao(), prefsRepo, sharedPrefs)
                    val downloadEngine = FastDownloadEngine.getInstance(appContext, repo)
                    val homeWallpaperEngine = HomeWallpaperEngine.getInstance(appContext)
                    return BrowserViewModel(
                        repository = repo,
                        fastDownloadEngine = downloadEngine,
                        wallpaperEngine = homeWallpaperEngine
                    ) as T
                }
            }
        }
    }
}
