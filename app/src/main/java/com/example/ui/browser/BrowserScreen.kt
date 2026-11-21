package com.example.ui.browser

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.SubscriptionActivity
import com.example.data.preferences.BrowserPreferences
import com.example.data.preferences.DownloadLocationMode
import com.example.engine.DefaultBrowserHelper
import com.example.engine.DownloadStorageHelper
import com.example.engine.FastDownloadEngine
import com.example.ui.theme.ApexMotion
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.viewmodel.BrowserViewModel
import com.example.ui.viewmodel.PendingDownloadPrompt

@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences by viewModel.preferencesState.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarksState.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistoryState.collectAsStateWithLifecycle()
    val topVisits by viewModel.topVisitsState.collectAsStateWithLifecycle()
    val browsingHistory by viewModel.browsingHistoryState.collectAsStateWithLifecycle()
    val browsingHistoryCount by viewModel.browsingHistoryCountState.collectAsStateWithLifecycle()
    val downloads by viewModel.downloadsState.collectAsStateWithLifecycle()
    val downloadsCount by viewModel.downloadsCountState.collectAsStateWithLifecycle()
    val savedPasswords by viewModel.savedPasswordsState.collectAsStateWithLifecycle()
    val savedPasswordsCount by viewModel.savedPasswordsCountState.collectAsStateWithLifecycle()
    val automationWorkflows by viewModel.automationWorkflowsState.collectAsStateWithLifecycle()
    val automationExecutions by viewModel.automationExecutionsState.collectAsStateWithLifecycle()
    val extractionRules by viewModel.extractionRulesState.collectAsStateWithLifecycle()
    val credentialVaultEntries by viewModel.credentialVaultEntriesState.collectAsStateWithLifecycle()
    val automationVaultEntries by viewModel.automationVaultEntriesState.collectAsStateWithLifecycle()
    val automationVaultCount by viewModel.automationVaultCountState.collectAsStateWithLifecycle()
    val automationVaultFolders by viewModel.automationVaultFoldersState.collectAsStateWithLifecycle()
    val automationVaultFiles by viewModel.automationVaultFilesState.collectAsStateWithLifecycle()
    val homeWallpapers by viewModel.homeWallpapersState.collectAsStateWithLifecycle()
    val customWallpaperBitmap by viewModel.customHomeWallpaperBitmapState.collectAsStateWithLifecycle()

    val reducedMotion = LocalReducedMotion.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val settingsListState = rememberLazyListState()
    val viewBookmarksLabel = stringResource(R.string.action_view_bookmarks)
    val viewDownloadsLabel = stringResource(R.string.menu_downloads)
    val undoLabel = stringResource(R.string.action_undo)

    var defaultBrowserStatus by remember {
        mutableStateOf(DefaultBrowserHelper.getStatus(context))
    }
    var subscriptionNowMillis by remember {
        androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis())
    }

    val defaultBrowserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        defaultBrowserStatus = DefaultBrowserHelper.getStatus(context)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val requestNotificationPermissionIfNeeded = remember(context) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val mainHandler = Handler(Looper.getMainLooper())
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        val evaluateAndNotify: () -> Unit = {
            val connected = isDeviceInternetConnected(context)
            mainHandler.post {
                viewModel.onNetworkConnectivityChanged(connected)
            }
        }

        // Check initial state
        evaluateAndNotify()

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                evaluateAndNotify()
            }

            override fun onLost(network: Network) {
                evaluateAndNotify()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                evaluateAndNotify()
            }

            override fun onUnavailable() {
                mainHandler.post {
                    viewModel.onNetworkConnectivityChanged(false)
                }
            }
        }

        val connectivityReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    android.content.Intent.ACTION_SCREEN_OFF -> {
                        viewModel.onDeviceLocked()
                    }
                    else -> evaluateAndNotify()
                }
            }
        }

        runCatching {
            cm?.registerDefaultNetworkCallback(networkCallback)
        }
        runCatching {
            @Suppress("DEPRECATION")
            val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION).apply {
                addAction(android.content.Intent.ACTION_SCREEN_OFF)
            }
            context.registerReceiver(connectivityReceiver, filter)
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    defaultBrowserStatus = DefaultBrowserHelper.getStatus(context)
                    subscriptionNowMillis = System.currentTimeMillis()
                    evaluateAndNotify()
                }
                Lifecycle.Event.ON_STOP -> {
                    viewModel.onAppBackgrounded()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { cm?.unregisterNetworkCallback(networkCallback) }
            runCatching { context.unregisterReceiver(connectivityReceiver) }
        }
    }

    LaunchedEffect(uiState.isMenuSheetOpen, uiState.isSettingsScreenOpen) {
        if (uiState.isMenuSheetOpen || uiState.isSettingsScreenOpen) {
            defaultBrowserStatus = DefaultBrowserHelper.getStatus(context)
        }
    }

    LaunchedEffect(uiState.isSubscriptionDialogOpen) {
        if (uiState.isSubscriptionDialogOpen) {
            val intent = SubscriptionActivity.createIntent(
                context = context,
                promptReason = uiState.subscriptionPromptReason
            )
            viewModel.setSubscriptionDialogOpen(false)
            runCatching { context.startActivity(intent) }
        }
    }

    LaunchedEffect(uiState.pendingAutoOpenDownload) {
        val completed = uiState.pendingAutoOpenDownload ?: return@LaunchedEffect
        viewModel.consumePendingAutoOpenDownload()
        if (preferences.autoOpenDownloadedFiles) {
            val viewIntent = DownloadStorageHelper.buildOpenFileIntent(
                context = context,
                localFilePath = completed.localFilePath,
                fileName = completed.fileName,
                mimeType = completed.mimeType
            )
            if (viewIntent != null) {
                runCatching { context.startActivity(viewIntent) }
            }
        }
    }

    val isWideScreen = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    val activeTab = uiState.activeTab
    LaunchedEffect(
        activeTab.id,
        activeTab.isHomePage,
        activeTab.homeWallpaperSeed,
        preferences.homeWallpaperMode
    ) {
        if (activeTab.isHomePage &&
            preferences.homeWallpaperMode == com.example.data.preferences.HomeWallpaperMode.ONLINE_API
        ) {
            viewModel.ensureHomeWallpaperForTab(activeTab.homeWallpaperSeed)
        }
    }
    val canNavigateBack = !activeTab.isHomePage || activeTab.canGoBack
    val isCurrentWebPageBookmarked = remember(activeTab.url, bookmarks) {
        if (activeTab.isHomePage) {
            false
        } else {
            val activeNormalized = activeTab.url.trim().substringBefore('#').trimEnd('/')
            bookmarks.any { bookmark ->
                bookmark.url.trim().equals(activeTab.url.trim(), ignoreCase = true) ||
                    bookmark.url.trim().substringBefore('#').trimEnd('/')
                        .equals(activeNormalized, ignoreCase = true)
            }
        }
    }

    LaunchedEffect(uiState.snackbarNotice) {
        val notice = uiState.snackbarNotice ?: return@LaunchedEffect
        val isBookmarkNotice = notice.startsWith("Bookmarked") || notice.startsWith("Removed bookmark")
        val isDownloadNotice = notice.startsWith("Downloading") || notice.startsWith("Downloaded")
        val result = snackbarHostState.showSnackbar(
            message = notice,
            actionLabel = when {
                isBookmarkNotice -> viewBookmarksLabel
                isDownloadNotice -> viewDownloadsLabel
                else -> null
            },
            duration = SnackbarDuration.Short
        )
        viewModel.consumeSnackbarNotice()
        if (result == SnackbarResult.ActionPerformed) {
            if (isBookmarkNotice) {
                viewModel.setBookmarksScreenOpen(true)
            } else if (isDownloadNotice) {
                viewModel.setDownloadsScreenOpen(true)
            }
        }
    }

    LaunchedEffect(uiState.pendingClosedTabUndo?.token) {
        val undoEvent = uiState.pendingClosedTabUndo ?: return@LaunchedEffect
        val displayTitle = undoEvent.tabTitle.trim().ifBlank { "Tab" }.let {
            if (it.length > 28) "${it.take(27)}…" else it
        }
        snackbarHostState.currentSnackbarData?.dismiss()
        val result = snackbarHostState.showSnackbar(
            message = context.getString(R.string.tab_closed_snackbar_message, displayTitle),
            actionLabel = undoLabel,
            duration = SnackbarDuration.Short
        )
        viewModel.consumeClosedTabUndo(undoEvent.token)
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoLastClosedTab()
        }
    }

    BackHandler(enabled = uiState.isFindInPageOpen) {
        viewModel.closeFindInPage()
    }

    BackHandler(
        enabled = canNavigateBack &&
            !uiState.isFindInPageOpen &&
            !uiState.isTabSwitcherOpen &&
            !uiState.isBookmarksScreenOpen &&
            !uiState.isHistoryScreenOpen &&
            !uiState.isDownloadsScreenOpen &&
            !uiState.isPasswordManagerOpen &&
            !uiState.isSettingsScreenOpen &&
            !uiState.isAutomationWorkspaceOpen &&
            !uiState.isAutomationVaultOpen &&
            !uiState.isSearchScreenOpen &&
            !uiState.isMenuSheetOpen &&
            !uiState.isAutomationHudOpen &&
            !uiState.isAutomationOpen
    ) {
        viewModel.navigateBack()
    }

    val (enterOverlay, exitOverlay) = ApexMotion.containerTransformSpec(reducedMotion)
    val isAnyFullScreenOverlayOpen = uiState.isSearchScreenOpen ||
        uiState.isSettingsScreenOpen ||
        uiState.isAutomationWorkspaceOpen ||
        uiState.isAutomationVaultOpen ||
        uiState.isBookmarksScreenOpen ||
        uiState.isHistoryScreenOpen ||
        uiState.isDownloadsScreenOpen ||
        uiState.isPasswordManagerOpen ||
        uiState.isTabSwitcherOpen

    val showBottomBar = uiState.isBottomBarVisible ||
        activeTab.isHomePage ||
        isAnyFullScreenOverlayOpen ||
        uiState.isMenuSheetOpen ||
        uiState.isFindInPageOpen ||
        uiState.isAutomationHudOpen ||
        uiState.isAutomationOpen

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    CompactTopToolbar(
                        visible = uiState.isToolbarVisible || activeTab.isHomePage,
                        activeTab = activeTab,
                        activeTabIndex = uiState.activeTabIndex,
                        totalTabs = uiState.tabs.size,
                        onAddressPillClick = {
                            viewModel.openSearchScreen(prefillCurrentUrl = !activeTab.isHomePage)
                        },
                        onReloadClick = viewModel::reloadActiveTab,
                        onSwipeToNextTab = viewModel::selectNextTab,
                        onSwipeToPreviousTab = viewModel::selectPreviousTab,
                        searchEngine = preferences.searchEngine,
                        isOnline = uiState.isDeviceOnline,
                        networkBannerState = uiState.networkBannerState,
                        automationStatus = uiState.automationToolbarStatus,
                        detectedElementsCount = uiState.pageInspection.elements.size,
                        onAutomationClick = { viewModel.openAutomationHud() }
                    )

                    if (isWideScreen) {
                        ExpressiveHorizontalTabStrip(
                            tabs = uiState.tabs,
                            activeTabId = uiState.activeTabId,
                            onSelectTab = viewModel::selectTab,
                            onCloseTab = viewModel::closeTab,
                            onNewTab = { viewModel.createNewTab() }
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Main Browser Content Area with tear-free Tab Switching
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        androidx.compose.runtime.key(activeTab.id) {
                            val currentTab = activeTab
                            if (currentTab.isHomePage) {
                                StartPageHome(
                                    preferences = preferences,
                                    topVisits = topVisits,
                                    onOpenSearchScreen = {
                                        viewModel.openSearchScreen(prefillCurrentUrl = false)
                                    },
                                    onSelectDestination = { url ->
                                        viewModel.submitNavigation(url, currentTab.id)
                                    },
                                    onAddTopVisit = viewModel::addTopVisit,
                                    onDeleteTopVisit = viewModel::deleteTopVisit,
                                    isIncognito = currentTab.isPrivate,
                                    incognitoTabsCount = uiState.incognitoTabCount,
                                    onCloseAllIncognitoTabs = viewModel::closeAllIncognitoTabs,
                                    wallpaperData = homeWallpapers[currentTab.homeWallpaperSeed],
                                    customWallpaperBitmap = customWallpaperBitmap,
                                    onRefreshWallpaper = viewModel::refreshActiveHomeWallpaper
                                )
                            } else {
                                WebViewViewport(
                                    tab = currentTab,
                                    preferences = preferences,
                                    privacyManager = viewModel.privacyManager,
                                    performanceOptimizer = viewModel.performanceOptimizer,
                                    existingWebView = viewModel.getWebView(currentTab.id),
                                    onRegisterWebView = viewModel::registerWebView,
                                    onPageStarted = viewModel::onWebViewPageStarted,
                                    onUrlCommitted = viewModel::onWebViewUrlCommitted,
                                    onProgressChanged = viewModel::onWebViewProgress,
                                    onPageFinished = viewModel::onWebViewPageFinished,
                                    onSubresourceBlocked = viewModel::onSubresourceBlocked,
                                    onHttpsUpgradeTriggered = { upgradedUrl ->
                                        viewModel.onWebViewHttpsUpgraded(currentTab.id, upgradedUrl)
                                    },
                                    onScrollDelta = { dy -> viewModel.onWebViewScrollDelta(dy) },
                                    onScrollPositionChanged = { dy, scrollY ->
                                        viewModel.onWebViewScrollDelta(dy, scrollY)
                                    },
                                    onPageLoadFailed = viewModel::onWebViewPageLoadFailed,
                                    onPageLoadFailedWithReason = viewModel::onWebViewPageLoadFailedWithReason,
                                    onRefreshPage = viewModel::reloadActiveTab,
                                    onNavigateHome = viewModel::onBottomHomePressed,
                                    onOpenInNewTab = { url ->
                                        viewModel.openLinkInNewTab(url, inGroup = false, isPrivate = false)
                                    },
                                    onOpenInNewTabInGroup = { url ->
                                        viewModel.openLinkInNewTab(url, inGroup = true, isPrivate = false)
                                    },
                                    onOpenInPrivateTab = { url ->
                                        viewModel.openLinkInNewTab(url, inGroup = false, isPrivate = true)
                                    },
                                    onOpenInNewWindow = { url ->
                                        viewModel.openLinkInNewTab(url, inGroup = false, isPrivate = false, switchToTab = true)
                                    },
                                    onAddToReadingList = { title, url ->
                                        viewModel.addLinkToReadingList(title, url)
                                    },
                                    onDownloadFile = { url, fileName, contentDisposition, mimeType, contentLength, userAgent ->
                                        if (preferences.showDownloadProgressNotifications) {
                                            requestNotificationPermissionIfNeeded()
                                        }
                                        viewModel.startFastDownload(
                                            url = url,
                                            suggestedFileName = fileName,
                                            contentDisposition = contentDisposition,
                                            mimeType = mimeType,
                                            contentLength = contentLength,
                                            userAgent = userAgent
                                        )
                                    },
                                    onCredentialSubmitted = viewModel::onWebCredentialSubmitted,
                                    onToggleCaretBrowsingShortcut = {
                                        viewModel.toggleCaretBrowsing(
                                            enabled = !preferences.caretBrowsingEnabled,
                                            announceShortcut = true
                                        )
                                    },
                                    onRecordedAutomationEvent = viewModel::onRecordedDomEvent,
                                    onDomMutationBatch = viewModel::onDomMutationBatch,
                                    activeDomHighlight = uiState.activeDomHighlight ?: uiState.executionState?.activeElementHighlight,
                                    scannedElements = uiState.pageInspection.elements,
                                    isScannerOutlineOverlayEnabled = uiState.isScannerOutlineOverlayEnabled,
                                    isAutomationRunning = uiState.executionState?.status == "RUNNING" ||
                                        uiState.isNanoRunning ||
                                        uiState.pageInspection.isScanning ||
                                        uiState.recordingSession.status == com.example.automation.RecordingStatus.RECORDING,
                                    onDismissDomHighlight = viewModel::clearActiveDomHighlight,
                                    onToggleScannerOutlines = viewModel::toggleScannerOutlineOverlay,
                                    isFindInPageOpen = uiState.isFindInPageOpen,
                                    findInPageQuery = uiState.findInPageQuery,
                                    findInPageActiveMatch = uiState.findInPageActiveMatchIndex,
                                    findInPageTotalMatches = uiState.findInPageTotalMatches,
                                    onFindInPageResults = viewModel::onWebViewFindResults,
                                    onFindQueryChange = viewModel::updateFindInPageQuery,
                                    onFindNextMatch = { viewModel.findNextInPage(forward = true) },
                                    onFindPreviousMatch = { viewModel.findNextInPage(forward = false) },
                                    onCloseFindInPage = viewModel::closeFindInPage,
                                    canCapturePreview = !isAnyFullScreenOverlayOpen &&
                                        !uiState.isMenuSheetOpen &&
                                        !uiState.isFindInPageOpen &&
                                        !uiState.isAutomationHudOpen &&
                                        !uiState.isAutomationVaultOpen &&
                                        !uiState.isAutomationOpen &&
                                        !uiState.isSyncBackupDialogOpen &&
                                        uiState.editingGroup == null &&
                                        uiState.pendingCredentialReplayAuth == null,
                                    onThumbnailCaptured = viewModel::updateTabThumbnail
                                )
                            }
                        }
                    }
                }

            // Live 1-Tap Password Autofill Bar when visiting sites with saved credentials
            val showAutofillBar = !activeTab.isHomePage &&
                preferences.passwordAutofillEnabled &&
                uiState.matchedCredentialsForActiveTab.isNotEmpty() &&
                !isAnyFullScreenOverlayOpen &&
                !uiState.isMenuSheetOpen &&
                uiState.pendingCredentialPrompt == null

            val execState = uiState.executionState
            val showLiveExecutionBar = execState != null &&
                !execState.isAwaitingConfirmation &&
                !isAnyFullScreenOverlayOpen &&
                !uiState.isAutomationHudOpen &&
                !uiState.isAutomationOpen &&
                !uiState.isMenuSheetOpen &&
                uiState.pendingCredentialReplayAuth == null

            // Live Automation Execution Floating Bar (TopCenter below URL toolbar, clear of bottom SnackbarHost)
            AnimatedVisibility(
                visible = showLiveExecutionBar,
                enter = if (reducedMotion) fadeIn() else expandVertically(ApexMotion.spatialSpring()) + fadeIn(),
                exit = if (reducedMotion) fadeOut() else shrinkVertically(ApexMotion.spatialSpring()) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .zIndex(5f)
            ) {
                if (execState != null) {
                        val isRunning = execState.status == "RUNNING"
                        val isPaused = execState.status == "PAUSED"
                        val isActiveExec = isRunning || isPaused
                        val totalSteps = execState.totalSteps.coerceAtLeast(1)
                        val displayStepNum = (execState.currentStepIndex + 1).coerceIn(1, totalSteps)
                        val latestLog = execState.stepLogs.lastOrNull().orEmpty()
                        val subtitleText = when {
                            isRunning -> execState.currentStepDescription.ifBlank {
                                latestLog.ifBlank { execState.verificationMessage }
                            }
                            else -> execState.verificationMessage.ifBlank { latestLog }
                        }

                        ElevatedCard(
                            onClick = { viewModel.openAutomationHud() },
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = when {
                                    execState.status == "FAILED" -> MaterialTheme.colorScheme.errorContainer
                                    isActiveExec -> MaterialTheme.colorScheme.surfaceContainerHigh
                                    else -> MaterialTheme.colorScheme.secondaryContainer
                                }
                            ),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("browser_live_execution_bar")
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
                                ) {
                                    when {
                                        isRunning -> {
                                            CircularProgressIndicator(
                                                strokeWidth = 2.5.dp,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        isPaused -> {
                                            Icon(
                                                imageVector = Icons.Filled.Pause,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.tertiary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        execState.status == "FAILED" -> {
                                            Icon(
                                                imageVector = Icons.Filled.ErrorOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        execState.status == "CANCELLED" -> {
                                            Icon(
                                                imageVector = Icons.Filled.Stop,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        else -> {
                                            Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = execState.workflowName,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (execState.totalSteps > 0 && isActiveExec) {
                                                Text(
                                                    text = "$displayStepNum/${execState.totalSteps}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                        if (subtitleText.isNotBlank()) {
                                            Text(
                                                text = subtitleText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (execState.status == "FAILED") {
                                                    MaterialTheme.colorScheme.onErrorContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    if (isActiveExec) {
                                        IconButton(
                                            onClick = {
                                                if (isPaused) {
                                                    viewModel.resumeWorkflowExecution()
                                                } else {
                                                    viewModel.pauseWorkflowExecution()
                                                }
                                            },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("browser_exec_pause_resume_button")
                                        ) {
                                            Icon(
                                                imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                                contentDescription = stringResource(
                                                    if (isPaused) R.string.automation_resume else R.string.automation_pause
                                                )
                                            )
                                        }
                                        IconButton(
                                            onClick = viewModel::cancelWorkflowExecution,
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("browser_exec_cancel_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Stop,
                                                contentDescription = stringResource(R.string.automation_stop)
                                            )
                                        }
                                    } else {
                                        if (execState.workflowId > 0L) {
                                            IconButton(
                                                onClick = { viewModel.replayWorkflow(execState.workflowId) },
                                                modifier = Modifier
                                                    .minimumInteractiveComponentSize()
                                                    .testTag("browser_exec_replay_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Refresh,
                                                    contentDescription = stringResource(R.string.automation_run)
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = viewModel::clearExecutionBanner,
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("browser_exec_dismiss_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = stringResource(R.string.automation_live_bar_dismiss)
                                            )
                                        }
                                    }
                                }

                                if (isActiveExec && execState.totalSteps > 0) {
                                    LinearProgressIndicator(
                                        progress = {
                                            (maxOf(execState.completedSteps, execState.currentStepIndex).toFloat() /
                                                execState.totalSteps.toFloat())
                                                .coerceIn(0.05f, 1f)
                                        },
                                        color = if (isPaused) {
                                            MaterialTheme.colorScheme.tertiary
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(3.dp)
                                            .testTag("browser_exec_progress_bar")
                                    )
                                }
                            }
                        }
                    }
                }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .zIndex(4f)
            ) {
                AnimatedVisibility(
                    visible = showAutofillBar,
                    enter = if (reducedMotion) fadeIn() else expandVertically(ApexMotion.spatialSpring()) + fadeIn(),
                    exit = if (reducedMotion) fadeOut() else shrinkVertically(ApexMotion.spatialSpring()) + fadeOut(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .then(
                            if (!showBottomBar) {
                                Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                            } else {
                                Modifier
                            }
                        )
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 6.dp,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_autofill_bar")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.passwords_fill_action),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            uiState.matchedCredentialsForActiveTab.forEach { credential ->
                                AssistChip(
                                    onClick = { viewModel.fillCredentialIntoActiveWebView(credential) },
                                    label = {
                                        Text(
                                            text = credential.username,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Key,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        leadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.testTag("password_autofill_chip_${credential.id}")
                                )
                            }
                        }
                    }
                }

                val activeGroup = uiState.activeTabGroup
                val showGroupStrip = activeGroup != null
                AnimatedVisibility(
                    visible = showBottomBar,
                    enter = if (reducedMotion) {
                        fadeIn(tween(120))
                    } else {
                        slideInVertically(
                            animationSpec = ApexMotion.spatialSpring(),
                            initialOffsetY = { it }
                        ) + fadeIn(animationSpec = ApexMotion.effectsSpring())
                    },
                    exit = if (reducedMotion) {
                        fadeOut(tween(100))
                    } else {
                        slideOutVertically(
                            animationSpec = ApexMotion.spatialSpring(),
                            targetOffsetY = { it }
                        ) + fadeOut(animationSpec = ApexMotion.effectsSpring())
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AnimatedVisibility(
                            visible = showGroupStrip,
                            enter = if (reducedMotion) {
                                fadeIn()
                            } else {
                                expandVertically(animationSpec = ApexMotion.spatialSpring()) +
                                    fadeIn(animationSpec = ApexMotion.effectsSpring())
                            },
                            exit = if (reducedMotion) {
                                fadeOut()
                            } else {
                                shrinkVertically(animationSpec = ApexMotion.spatialSpring()) +
                                    fadeOut(animationSpec = ApexMotion.effectsSpring())
                            }
                        ) {
                            if (activeGroup != null) {
                                BottomGroupTabStrip(
                                    group = activeGroup,
                                    groupTabs = uiState.activeGroupTabs,
                                    activeTabId = uiState.activeTabId,
                                    onSelectTab = viewModel::selectTab,
                                    onCloseTab = viewModel::closeTab,
                                    onAddTabToGroup = {
                                        viewModel.createNewTab(
                                            groupId = activeGroup.id,
                                            stayInSwitcher = false
                                        )
                                    },
                                    onEditGroup = { viewModel.openGroupEditor(activeGroup.id) }
                                )
                            }
                        }

                        BraveBottomNavigationBar(
                            isHomeActive = activeTab.isHomePage,
                            isBookmarksScreenOpen = uiState.isBookmarksScreenOpen,
                            isSettingsScreenOpen = uiState.isSettingsScreenOpen,
                            isMenuSheetOpen = uiState.isMenuSheetOpen,
                            isCurrentWebPageBookmarked = isCurrentWebPageBookmarked,
                            isTabSwitcherOpen = uiState.isTabSwitcherOpen,
                            isSearchScreenOpen = uiState.isSearchScreenOpen,
                            openTabCount = uiState.tabs.size,
                            onHomeClick = viewModel::onBottomHomePressed,
                            onBookmarksClick = viewModel::onBottomBookmarksPressed,
                            onSearchClick = {
                                if (uiState.isSearchScreenOpen) {
                                    viewModel.closeSearchScreen()
                                } else {
                                    viewModel.openSearchScreen(prefillCurrentUrl = false)
                                }
                            },
                            onTabsClick = { viewModel.setTabSwitcherOpen(!uiState.isTabSwitcherOpen) },
                            onMenuClick = viewModel::toggleMenuSheet,
                            onNewTabFromTabsMenu = { viewModel.createNewTab() },
                            onNewPrivateTabFromTabsMenu = { viewModel.createNewIncognitoTab() },
                            onCloseCurrentTabFromTabsMenu = { viewModel.closeTab(activeTab.id) },
                            isTabsQuickMenuAvailable = !activeTab.isHomePage,
                            isOtherOverlayOpen = uiState.isHistoryScreenOpen ||
                                uiState.isDownloadsScreenOpen ||
                                uiState.isPasswordManagerOpen ||
                                uiState.isAutomationWorkspaceOpen ||
                                uiState.isAutomationVaultOpen ||
                                uiState.isAutomationHudOpen ||
                                uiState.isAutomationOpen
                        )
                    }
                }
            }

            // Live Credential Capture Prompt ("Save / Update Password?")
            val pendingCredential = uiState.pendingCredentialPrompt
            AnimatedVisibility(
                visible = pendingCredential != null && !uiState.isPasswordManagerOpen,
                enter = if (reducedMotion) fadeIn() else expandVertically(ApexMotion.spatialSpring()) + fadeIn(),
                exit = if (reducedMotion) fadeOut() else shrinkVertically(ApexMotion.spatialSpring()) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                if (pendingCredential != null) {
                    ElevatedCard(
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_save_prompt_card")
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.Key,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(
                                            if (pendingCredential.existingId != null) {
                                                R.string.passwords_update_prompt_title
                                            } else {
                                                R.string.passwords_save_prompt_title
                                            }
                                        ),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${pendingCredential.siteName} • ${pendingCredential.username}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = viewModel::dismissPendingCredentialPrompt,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("password_save_prompt_close")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.cancel)
                                    )
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                TextButton(
                                    onClick = viewModel::dismissPendingCredentialPrompt,
                                    modifier = Modifier.testTag("password_save_prompt_never")
                                ) {
                                    Text(stringResource(R.string.cancel))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = viewModel::confirmSavePendingCredential,
                                    modifier = Modifier.testTag("password_save_prompt_confirm")
                                ) {
                                    Text(
                                        text = if (pendingCredential.existingId != null) "Update" else "Save"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

        // Dedicated Search Screen (layered above Scaffold so topBar/bottomBar never jump)
        AnimatedVisibility(
            visible = uiState.isSearchScreenOpen,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(100)),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(10f)
        ) {
                SearchScreenOverlay(
                    query = uiState.searchQuery,
                    selectedEngine = preferences.searchEngine,
                    suggestions = uiState.searchSuggestions,
                    recentHistory = searchHistory,
                    activeTabUrl = if (!activeTab.isHomePage) activeTab.url else null,
                    activeTabTitle = if (!activeTab.isHomePage) activeTab.title else null,
                    networkBannerState = uiState.networkBannerState,
                    onQueryChange = viewModel::onSearchQueryChanged,
                    onSubmitSearch = { submitted -> viewModel.submitNavigation(submitted) },
                    onFillQuery = viewModel::onSearchQueryChanged,
                    onDeleteHistoryItem = viewModel::deleteSearchHistoryItem,
                    onClearAllHistory = viewModel::clearAllSearchHistory,
                    onDismiss = viewModel::closeSearchScreen
                )
            }

            // Settings Screen Overlay (Opened from Hamburger Menu Bottom Sheet)
            AnimatedVisibility(
                visible = uiState.isSettingsScreenOpen,
                enter = if (reducedMotion) fadeIn(tween(90)) else enterOverlay,
                exit = if (reducedMotion) fadeOut(tween(70)) else exitOverlay,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(10f)
            ) {
                SettingsScreenOverlay(
                    preferences = preferences,
                    initialSection = uiState.settingsInitialSection,
                    onSelectSearchEngine = viewModel::selectSearchEngine,
                    onToggleShields = viewModel::toggleShields,
                    onToggleHttpsUpgrade = viewModel::toggleHttpsUpgrade,
                    onToggleScriptGuard = viewModel::toggleScriptGuard,
                    onToggleSmartPreload = viewModel::toggleAggressivePreload,
                    onToggleDesktopViewport = viewModel::toggleDesktopViewport,
                    onToggleDynamicColor = viewModel::toggleDynamicColor,
                    onSelectSeedPalette = viewModel::selectSeedPalette,
                    onSelectThemeMode = viewModel::selectThemeMode,
                    onClearBrowsingData = viewModel::resetPrivacyStats,
                    onToggleShowTopVisitsOnHome = viewModel::setShowTopVisitsOnHome,
                    customWallpaperBitmap = customWallpaperBitmap,
                    activeOnlineWallpaper = homeWallpapers[activeTab.homeWallpaperSeed],
                    onSelectHomeWallpaperMode = viewModel::selectHomeWallpaperMode,
                    onPickCustomWallpaperUri = { uri ->
                        viewModel.setCustomHomeWallpaperFromUri(context, uri)
                    },
                    onToggleHomeWallpaperBlackAndWhite = viewModel::toggleHomeWallpaperBlackAndWhite,
                    onChangeDefaultZoom = viewModel::setDefaultZoomPercent,
                    onSetSiteZoom = viewModel::setSiteZoom,
                    onRemoveSiteZoom = viewModel::removeSiteZoom,
                    onClearSavedSiteZooms = viewModel::clearSavedSiteZooms,
                    onToggleShowZoomInMainMenu = viewModel::toggleShowZoomInMainMenu,
                    onToggleForceEnableZoom = viewModel::toggleForceEnableZoom,
                    onToggleSimplifiedView = viewModel::toggleSimplifiedView,
                    onToggleCaretBrowsing = { enabled ->
                        viewModel.toggleCaretBrowsing(enabled, announceShortcut = false)
                    },
                    onToggleTouchpadSwipeNavigation = viewModel::toggleTouchpadSwipeNavigation,
                    onSelectDownloadLocation = viewModel::selectDownloadLocation,
                    onToggleAskWhereToSaveFiles = viewModel::toggleAskWhereToSaveFiles,
                    onToggleAutoOpenDownloadedFiles = viewModel::toggleAutoOpenDownloadedFiles,
                    onToggleShowDownloadProgressNotifications = viewModel::toggleShowDownloadProgressNotifications,
                    onToggleParallelDownloading = viewModel::toggleParallelDownloading,
                    onOpenPasswordManager = { viewModel.setPasswordManagerOpen(true) },
                    onOpenDownloads = { viewModel.setDownloadsScreenOpen(true) },
                    onOpenHistory = { viewModel.setHistoryScreenOpen(true) },
                    onOpenAutomations = { viewModel.setAutomationWorkspaceOpen(true) },
                    defaultBrowserStatus = defaultBrowserStatus,
                    onOpenDefaultBrowserSettings = {
                        runCatching {
                            defaultBrowserLauncher.launch(
                                DefaultBrowserHelper.createRequestOrSettingsIntent(
                                    context = context,
                                    preferSystemSettingsWhenDefault = true
                                )
                            )
                        }
                    },
                    mainListState = settingsListState,
                    backHandlerEnabled = uiState.isSettingsScreenOpen,
                    onDismiss = { viewModel.setSettingsScreenOpen(false) }
                )
            }

            // Bookmarks Screen Overlay
            AnimatedVisibility(
                visible = uiState.isBookmarksScreenOpen,
                enter = if (reducedMotion) fadeIn(tween(90)) else enterOverlay,
                exit = if (reducedMotion) fadeOut(tween(70)) else exitOverlay,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(10f)
            ) {
                BookmarksScreenOverlay(
                    bookmarks = bookmarks,
                    onSelectBookmark = { url -> viewModel.submitNavigation(url) },
                    onDeleteBookmark = viewModel::deleteBookmark,
                    onImportBookmarks = viewModel::importBookmarksFromText,
                    onNotifyExported = viewModel::notifyBookmarksExported,
                    backHandlerEnabled = uiState.isBookmarksScreenOpen,
                    onDismiss = { viewModel.setBookmarksScreenOpen(false) }
                )
            }

            // Dedicated Browsing History Screen Overlay
            AnimatedVisibility(
                visible = uiState.isHistoryScreenOpen,
                enter = if (reducedMotion) fadeIn(tween(90)) else enterOverlay,
                exit = if (reducedMotion) fadeOut(tween(70)) else exitOverlay,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(10f)
            ) {
                HistoryScreenOverlay(
                    historyItems = browsingHistory,
                    onSelectHistoryUrl = { url -> viewModel.submitNavigation(url) },
                    onOpenInNewTab = { url ->
                        viewModel.openLinkInNewTab(url, inGroup = false, isPrivate = false, switchToTab = true)
                    },
                    onOpenInPrivateTab = { url ->
                        viewModel.openLinkInNewTab(url, inGroup = false, isPrivate = true, switchToTab = true)
                    },
                    onBookmarkPage = { title, url ->
                        viewModel.addLinkToReadingList(title, url)
                    },
                    onDeleteHistoryItem = viewModel::deleteBrowsingHistoryItem,
                    onClearHistoryRange = viewModel::clearBrowsingHistoryRange,
                    backHandlerEnabled = uiState.isHistoryScreenOpen,
                    onDismiss = { viewModel.setHistoryScreenOpen(false) }
                )
            }

            // Dedicated Fast Downloads Screen Overlay
            AnimatedVisibility(
                visible = uiState.isDownloadsScreenOpen,
                enter = if (reducedMotion) fadeIn(tween(90)) else enterOverlay,
                exit = if (reducedMotion) fadeOut(tween(70)) else exitOverlay,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(10f)
            ) {
                DownloadsScreenOverlay(
                    downloads = downloads,
                    downloadLocationMode = preferences.downloadLocationMode,
                    parallelDownloadingEnabled = preferences.parallelDownloadingEnabled,
                    onStartNewDownload = { url, customFileName ->
                        if (preferences.showDownloadProgressNotifications) {
                            requestNotificationPermissionIfNeeded()
                        }
                        viewModel.startFastDownload(
                            url = url,
                            suggestedFileName = customFileName
                        )
                    },
                    onPauseDownload = viewModel::pauseDownload,
                    onResumeDownload = { id ->
                        if (preferences.showDownloadProgressNotifications) {
                            requestNotificationPermissionIfNeeded()
                        }
                        viewModel.resumeDownload(id)
                    },
                    onRetryDownload = { id ->
                        if (preferences.showDownloadProgressNotifications) {
                            requestNotificationPermissionIfNeeded()
                        }
                        viewModel.retryDownload(id)
                    },
                    onCancelDownload = viewModel::cancelDownload,
                    onDeleteDownload = viewModel::deleteDownload,
                    onClearCompletedDownloads = viewModel::clearCompletedDownloads,
                    onClearAllDownloads = viewModel::clearAllDownloads,
                    onOpenUrlInBrowser = { sourceUrl ->
                        viewModel.submitNavigation(sourceUrl)
                    },
                    backHandlerEnabled = uiState.isDownloadsScreenOpen,
                    onDismiss = { viewModel.setDownloadsScreenOpen(false) }
                )
            }

            // Dedicated Material 3 Expressive Password Manager Overlay
            AnimatedVisibility(
                visible = uiState.isPasswordManagerOpen,
                enter = if (reducedMotion) fadeIn(tween(90)) else enterOverlay,
                exit = if (reducedMotion) fadeOut(tween(70)) else exitOverlay,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(10f)
            ) {
                PasswordManagerScreenOverlay(
                    passwords = savedPasswords,
                    autofillEnabled = preferences.passwordAutofillEnabled,
                    decryptPassword = viewModel::decryptPassword,
                    onSavePassword = viewModel::saveOrUpdatePassword,
                    onToggleFavorite = viewModel::togglePasswordFavorite,
                    onDeletePassword = viewModel::deleteSavedPassword,
                    onClearVault = viewModel::clearAllSavedPasswords,
                    onImportCsv = viewModel::importPasswordsFromCsv,
                    onExportCsv = viewModel::exportPasswordsToCsv,
                    onToggleAutofill = viewModel::togglePasswordAutofill,
                    onOpenWebsite = { siteUrl ->
                        viewModel.submitNavigation(siteUrl)
                    },
                    backHandlerEnabled = uiState.isPasswordManagerOpen,
                    onDismiss = { viewModel.setPasswordManagerOpen(false) },
                    credentialVaultEntries = credentialVaultEntries,
                    vaultStatus = uiState.vaultStatus,
                    vaultStatusMessage = uiState.vaultStatusMessage,
                    revealedVaultCredential = uiState.revealedVaultCredential,
                    onUnlockVault = viewModel::unlockCredentialVault,
                    onLockVault = viewModel::lockCredentialVault,
                    onAddVaultCredential = { originUrl, label, username, password ->
                        viewModel.addVaultCredential(
                            websiteUrl = originUrl,
                            username = username,
                            password = password,
                            label = label
                        )
                    },
                    onUpdateVaultCredential = { credId, originUrl, label, username, password ->
                        viewModel.updateVaultCredential(
                            credentialId = credId,
                            websiteUrl = originUrl,
                            username = username,
                            password = password,
                            label = label
                        )
                    },
                    onRevealVaultCredential = viewModel::revealVaultCredential,
                    onHideRevealedVaultCredential = viewModel::hideRevealedVaultCredential,
                    onDeleteVaultCredential = viewModel::deleteVaultCredential,
                    onResetInvalidatedVault = viewModel::resetInvalidatedCredentialVault
                )
            }

            // Ensure wallpapers for any Home tabs visible in the Tab Switcher are loaded
            LaunchedEffect(uiState.isTabSwitcherOpen, uiState.tabs, preferences.homeWallpaperMode) {
                if (uiState.isTabSwitcherOpen &&
                    preferences.homeWallpaperMode == com.example.data.preferences.HomeWallpaperMode.ONLINE_API
                ) {
                    uiState.tabs.forEach { tab ->
                        if (tab.isHomePage) {
                            viewModel.ensureHomeWallpaperForTab(tab.homeWallpaperSeed)
                        }
                    }
                }
            }

            // Draggable Grid Tab Switcher Overlay (No Bottom Nav — Centered Pills)
            AnimatedVisibility(
                visible = uiState.isTabSwitcherOpen,
                enter = if (reducedMotion) fadeIn(tween(90)) else enterOverlay,
                exit = if (reducedMotion) fadeOut(tween(70)) else exitOverlay,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(10f)
            ) {
                DraggableTabGridOverlay(
                    gridEntries = uiState.gridEntries,
                    totalOpenTabs = uiState.tabs.size,
                    activeTabId = uiState.activeTabId,
                    expandedGroupId = uiState.expandedGroupId,
                    isIncognitoAvailable = uiState.isIncognitoUnlocked || uiState.incognitoTabCount > 0,
                    isActiveTabIncognito = activeTab.isPrivate,
                    onSelectTab = viewModel::selectTab,
                    onCloseTab = viewModel::closeTab,
                    onCloseGroup = viewModel::closeGroup,
                    onEditGroup = viewModel::openGroupEditor,
                    onMergeEntries = viewModel::mergeGridEntriesIntoGroup,
                    onMoveGroupedTabToTarget = viewModel::moveGroupedTabToGridTarget,
                    onOpenGroup = viewModel::setExpandedGroup,
                    onAddTabToGroup = { grpId -> viewModel.createNewTab(groupId = grpId) },
                    onUngroupTab = viewModel::ungroupTab,
                    onCycleTabColor = viewModel::cycleTabColor,
                    onNewTab = { viewModel.createNewTab() },
                    onNewIncognitoTab = { viewModel.createNewIncognitoTab() },
                    onCreateGroup = { viewModel.createNewTabGroupFromSwitcher(isPrivate = false) },
                    onCreateIncognitoGroup = { viewModel.createNewTabGroupFromSwitcher(isPrivate = true) },
                    onResetAllTabs = viewModel::resetAllTabs,
                    onCloseAllIncognitoTabs = viewModel::closeAllIncognitoTabs,
                    onClearBrowsingData = viewModel::resetPrivacyStats,
                    onOpenSettings = { viewModel.setSettingsScreenOpen(true) },
                    onCloseSelectedTabs = viewModel::closeSelectedTabs,
                    onGroupSelectedTabs = viewModel::groupSelectedTabs,
                    topVisits = topVisits,
                    preferences = preferences,
                    homeWallpapers = homeWallpapers,
                    customWallpaperBitmap = customWallpaperBitmap,
                    backHandlerEnabled = uiState.isTabSwitcherOpen,
                    onDismiss = { viewModel.setTabSwitcherOpen(false) }
                )
            }

        val targetSnackbarBottomPadding = when {
            uiState.isTabSwitcherOpen -> 100.dp
            !isAnyFullScreenOverlayOpen && showBottomBar -> if (uiState.activeTabGroup != null) 144.dp else 88.dp
            else -> 16.dp
        }
        val snackbarBottomPadding by animateDpAsState(
            targetValue = targetSnackbarBottomPadding,
            animationSpec = tween(durationMillis = if (reducedMotion) 0 else 180),
            label = "SnackbarBottomPadding"
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = snackbarBottomPadding)
                .zIndex(60f)
                .testTag("browser_snackbar_host")
        )

        // Hamburger Menu ModalBottomSheet (contains Settings & Quick Browser Actions)
        if (uiState.isMenuSheetOpen) {
            val activeZoomHost = if (activeTab.isHomePage) {
                ""
            } else {
                com.example.data.preferences.BrowserPreferences.normalizeZoomHost(activeTab.url)
            }
            val currentActiveZoom = preferences.effectiveZoomForUrl(activeTab.url)
            val hasCustomZoom = activeZoomHost.isNotEmpty() &&
                preferences.savedSiteZooms.containsKey(activeZoomHost)

            BrowserMenuBottomSheet(
                isHomeActive = activeTab.isHomePage,
                canNavigateBack = canNavigateBack,
                canNavigateForward = !activeTab.isHomePage && activeTab.canGoForward,
                isCurrentPageBookmarked = isCurrentWebPageBookmarked,
                desktopViewportEnabled = preferences.desktopViewportEnabled,
                browsingHistoryCount = browsingHistoryCount,
                downloadsCount = downloadsCount,
                savedPasswordsCount = savedPasswordsCount,
                isDefaultBrowser = defaultBrowserStatus.isApexDefault,
                isDefaultPromoDismissed = uiState.isDefaultBrowserPromoDismissed,
                showZoomOption = preferences.showZoomInMainMenu,
                activeSiteHost = activeZoomHost,
                currentZoomPercent = currentActiveZoom,
                hasCustomSiteZoom = hasCustomZoom,
                onAdjustZoomPercent = viewModel::adjustActiveZoomPercent,
                onResetSiteZoom = viewModel::resetActiveSiteZoom,
                onBackClick = viewModel::navigateBack,
                onForwardClick = viewModel::navigateForward,
                onSharePageClick = {
                    if (!activeTab.isHomePage && activeTab.url.isNotBlank()) {
                        runCatching {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, activeTab.title)
                                putExtra(Intent.EXTRA_TEXT, activeTab.url)
                            }
                            context.startActivity(
                                Intent.createChooser(
                                    shareIntent,
                                    context.getString(R.string.menu_action_share)
                                )
                            )
                        }
                    }
                },
                onBookmarkPageClick = viewModel::toggleBookmarkOnActiveTab,
                onFindInPageClick = viewModel::openFindInPage,
                onOpenAutomationHudClick = { viewModel.openAutomationHud() },
                onOpenAutomationWorkspaceClick = { viewModel.setAutomationWorkspaceOpen(true) },
                onOpenBookmarksClick = { viewModel.setBookmarksScreenOpen(true) },
                isProActivated = preferences.isProCurrentlyActive(subscriptionNowMillis),
                remainingFreePlaysToday = preferences.remainingFreeAutomationPlaysToday(nowMillis = subscriptionNowMillis),
                dailyPlayLimit = com.example.data.preferences.BrowserPreferences.FREE_DAILY_AUTOMATION_PLAY_LIMIT,
                onSignInClick = { viewModel.setSubscriptionDialogOpen(true) },
                onOpenAppearanceClick = viewModel::openAppearanceSettings,
                onDismissDefaultPromo = viewModel::dismissDefaultBrowserPromo,
                onOpenSettings = { viewModel.setSettingsScreenOpen(true) },
                onSetAsDefaultBrowserClick = {
                    runCatching {
                        defaultBrowserLauncher.launch(
                            DefaultBrowserHelper.createRequestOrSettingsIntent(
                                context = context,
                                preferSystemSettingsWhenDefault = false
                            )
                        )
                    }
                },
                onNewTabClick = { viewModel.createNewTab() },
                onNewIncognitoTabClick = { viewModel.createNewIncognitoTab() },
                onOpenHistoryClick = { viewModel.setHistoryScreenOpen(true) },
                onOpenDownloadsClick = { viewModel.setDownloadsScreenOpen(true) },
                onOpenPasswordsClick = { viewModel.setPasswordManagerOpen(true) },
                onReloadPageClick = viewModel::reloadActiveTab,
                onToggleDesktopMode = viewModel::toggleDesktopViewport,
                onClearBrowsingData = viewModel::resetPrivacyStats,
                onDismiss = { viewModel.setMenuSheetOpen(false) }
            )
        }

        // Group Rename & Tonal Color Picker Dialog
        val groupToEdit = uiState.editingGroup
        if (groupToEdit != null) {
            GroupRenameColorDialog(
                group = groupToEdit,
                onSave = { newTitle, selectedColor ->
                    viewModel.updateGroupTitleAndColor(
                        groupId = groupToEdit.id,
                        newTitle = newTitle,
                        colorOption = selectedColor
                    )
                },
                onUngroupAll = { viewModel.ungroupAllInGroup(groupToEdit.id) },
                onDismiss = viewModel::dismissGroupEditor
            )
        }

        if (uiState.pendingCredentialReplayAuth != null &&
            !uiState.isAutomationHudOpen &&
            !uiState.isAutomationWorkspaceOpen
        ) {
            CredentialReplayAuthorizationDialog(
                authorization = uiState.pendingCredentialReplayAuth!!,
                onConfirm = viewModel::confirmPendingCredentialReplay,
                onDismiss = viewModel::cancelPendingCredentialReplay
            )
        }

        // Dedicated Full-Screen Automation Workspace Overlay (accessible from HUD & Settings)
        AnimatedVisibility(
            visible = uiState.isAutomationWorkspaceOpen,
            enter = if (reducedMotion) fadeIn(tween(90)) else enterOverlay,
            exit = if (reducedMotion) fadeOut(tween(70)) else exitOverlay,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(12f)
        ) {
            AutomationWorkspaceScreenOverlay(
                workflows = automationWorkflows,
                executions = automationExecutions,
                activeTabDomain = uiState.pageInspection.domain,
                activeTabUrl = activeTab.url,
                executionState = uiState.executionState,
                onCreateWorkflow = { name, domain, url, instructions ->
                    viewModel.createManualWorkflow(
                        name = name,
                        domain = domain,
                        sourceUrl = url,
                        instructionsText = instructions,
                        steps = emptyList()
                    )
                },
                onRunWorkflow = { id -> viewModel.replayWorkflow(id) },
                onOpenWorkflowDetails = viewModel::openAutomationDetails,
                onRenameWorkflow = viewModel::renameWorkflow,
                onDuplicateWorkflow = viewModel::duplicateWorkflow,
                onDeleteWorkflow = viewModel::deleteWorkflow,
                onExportJson = viewModel::exportAutomationsJson,
                onImportJson = viewModel::importAutomationsJson,
                onConfirmDestructiveExecution = viewModel::confirmPendingDestructiveExecution,
                onCancelDestructiveExecution = viewModel::cancelPendingDestructiveExecution,
                onClearExecutionBanner = viewModel::clearExecutionBanner,
                onCancelExecution = viewModel::cancelWorkflowExecution,
                onPauseExecution = viewModel::pauseWorkflowExecution,
                onResumeExecution = viewModel::resumeWorkflowExecution,
                interruptedCandidate = uiState.interruptedExecutionCandidate,
                onResumeInterrupted = { viewModel.confirmResumeInterruptedExecution() },
                onRestartInterrupted = { viewModel.restartInterruptedExecution() },
                onDismissInterrupted = { viewModel.dismissInterruptedExecution() },
                sortOption = uiState.workspaceSortOption,
                selectedFolderFilter = uiState.workspaceFolderFilter,
                selectedTagFilter = uiState.workspaceTagFilter,
                lastImportResult = uiState.lastWorkflowImportResult,
                onChangeSortOption = viewModel::setWorkspaceSortOption,
                onSelectFolderFilter = viewModel::setWorkspaceFolderFilter,
                onSelectTagFilter = viewModel::setWorkspaceTagFilter,
                onToggleFavorite = viewModel::toggleWorkflowFavorite,
                onUpdateOrganization = viewModel::updateWorkflowOrganization,
                onArchiveWorkflow = viewModel::archiveWorkflow,
                onRestoreArchivedWorkflow = viewModel::restoreArchivedWorkflow,
                onExportSinglePackageJson = viewModel::exportSingleWorkflowPackageJson,
                onExportZipBytes = { ids, onReady ->
                    viewModel.exportWorkflowsZipBytes(
                        workflowIds = ids,
                        packageType = if (ids != null && ids.size == 1) {
                            com.example.automation.ExportPackageType.SINGLE_WITH_DEPENDENCIES
                        } else {
                            com.example.automation.ExportPackageType.COLLECTION
                        },
                        includeDependencies = true,
                        onReady = onReady
                    )
                },
                onImportPackageJson = viewModel::importWorkflowPackageJson,
                onImportZipBytes = viewModel::importWorkflowsZipBytes,
                onDismissImportResult = viewModel::clearLastWorkflowImportResult,
                onOpenDisambiguation = viewModel::openDisambiguationForExecution,
                lastLocatorDiagnostics = uiState.lastLocatorDiagnostics,
                automationVaultCount = automationVaultCount,
                onOpenAutomationVault = { viewModel.setAutomationVaultOpen(true) },
                isProActivated = preferences.isProCurrentlyActive(subscriptionNowMillis),
                dailyPlaysRemaining = preferences.remainingFreeAutomationPlaysToday(nowMillis = subscriptionNowMillis),
                dailyPlayLimit = com.example.data.preferences.BrowserPreferences.FREE_DAILY_AUTOMATION_PLAY_LIMIT,
                onOpenProSubscription = { viewModel.setSubscriptionDialogOpen(true) },
                backHandlerEnabled = uiState.isAutomationWorkspaceOpen &&
                    uiState.selectedAutomationWorkflowId == null &&
                    !uiState.isAutomationVaultOpen,
                onDismiss = { viewModel.setAutomationWorkspaceOpen(false) }
            )
        }

        // Dedicated Automation Details Overlay
        val selectedWorkflow = uiState.selectedAutomationWorkflowId?.let { targetId ->
            automationWorkflows.firstOrNull { it.id == targetId }
        }
        AnimatedVisibility(
            visible = uiState.isAutomationWorkspaceOpen && selectedWorkflow != null,
            enter = if (reducedMotion) fadeIn(tween(90)) else enterOverlay,
            exit = if (reducedMotion) fadeOut(tween(70)) else exitOverlay,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(14f)
        ) {
            if (selectedWorkflow != null) {
                val workflowExecutions by viewModel.observeExecutionsForWorkflow(selectedWorkflow.id)
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                AutomationDetailsScreenOverlay(
                    workflow = selectedWorkflow,
                    executions = workflowExecutions,
                    executionState = uiState.executionState,
                    onRunWorkflow = { id -> viewModel.replayWorkflow(id) },
                    onRenameWorkflow = viewModel::renameWorkflow,
                    onUpdateSteps = viewModel::updateWorkflowSteps,
                    onAddStep = viewModel::addStepToWorkflow,
                    onConfirmDestructiveExecution = viewModel::confirmPendingDestructiveExecution,
                    onCancelDestructiveExecution = viewModel::cancelPendingDestructiveExecution,
                    onClearExecutionBanner = viewModel::clearExecutionBanner,
                    onCancelExecution = viewModel::cancelWorkflowExecution,
                    onPauseExecution = viewModel::pauseWorkflowExecution,
                    onResumeExecution = viewModel::resumeWorkflowExecution,
                    credentialVaultEntries = credentialVaultEntries,
                    pendingCredentialReplayAuth = uiState.pendingCredentialReplayAuth,
                    onConfirmCredentialReplay = viewModel::confirmPendingCredentialReplay,
                    onCancelCredentialReplay = viewModel::cancelPendingCredentialReplay,
                    onAssignCredentialToStep = viewModel::assignCredentialToWorkflowStep,
                    onOpenCredentialVault = {
                        viewModel.setAutomationWorkspaceOpen(false)
                        viewModel.setPasswordManagerOpen(true)
                    },
                    allWorkflows = automationWorkflows,
                    onUpdateDocument = viewModel::updateWorkflowDocument,
                    onAddVariable = viewModel::addWorkflowVariable,
                    onRemoveVariable = viewModel::removeWorkflowVariable,
                    onUpdateDefaultRecovery = viewModel::updateWorkflowDefaultRecovery,
                    onAddPhase6Step = viewModel::addPhase6StepToWorkflow,
                    interruptedCandidate = uiState.interruptedExecutionCandidate,
                    onResumeInterrupted = { viewModel.confirmResumeInterruptedExecution() },
                    onRestartInterrupted = { viewModel.restartInterruptedExecution() },
                    onDismissInterrupted = { viewModel.dismissInterruptedExecution() },
                    validationReport = uiState.validationReportsByWorkflowId[selectedWorkflow.id],
                    dryRunReport = uiState.activeDryRunReport,
                    versions = uiState.activeWorkflowVersions,
                    diffReport = uiState.activeDiffReport,
                    onValidateWorkflow = viewModel::validateWorkflow,
                    onRunDryRun = viewModel::runWorkflowDryRun,
                    onClearDryRun = viewModel::clearDryRunReport,
                    onLoadVersions = viewModel::loadWorkflowVersions,
                    onCompareVersions = viewModel::compareWorkflowVersions,
                    onRestoreVersion = viewModel::restoreWorkflowVersion,
                    onClearDiffReport = viewModel::clearDiffReport,
                    onUpdateOrganization = viewModel::updateWorkflowOrganization,
                    onArchiveWorkflow = viewModel::archiveWorkflow,
                    onRestoreArchivedWorkflow = viewModel::restoreArchivedWorkflow,
                    onExportSinglePackageJson = viewModel::exportSingleWorkflowPackageJson,
                    onExportSingleZipBytes = { wfId, onReady ->
                        viewModel.exportWorkflowsZipBytes(
                            workflowIds = listOf(wfId),
                            packageType = com.example.automation.ExportPackageType.SINGLE_WITH_DEPENDENCIES,
                            includeDependencies = true,
                            onReady = onReady
                        )
                    },
                    onOpenDisambiguation = viewModel::openDisambiguationForExecution,
                    lastLocatorDiagnostics = uiState.lastLocatorDiagnostics,
                    automationVaultEntries = automationVaultEntries,
                    onOpenAutomationVault = { viewModel.setAutomationVaultOpen(true) },
                    onUpdateReplaySettings = viewModel::updateWorkflowReplaySettings,
                    onRunWorkflowWithOptions = { id, opts -> viewModel.replayWorkflow(id, opts) },
                    isProActivated = preferences.isProCurrentlyActive(subscriptionNowMillis),
                    dailyPlaysRemaining = preferences.remainingFreeAutomationPlaysToday(nowMillis = subscriptionNowMillis),
                    dailyPlayLimit = com.example.data.preferences.BrowserPreferences.FREE_DAILY_AUTOMATION_PLAY_LIMIT,
                    onOpenProSubscription = { viewModel.setSubscriptionDialogOpen(true) },
                    backHandlerEnabled = uiState.isAutomationWorkspaceOpen &&
                        selectedWorkflow != null &&
                        !uiState.isAutomationVaultOpen,
                    onDismiss = viewModel::closeAutomationDetails
                )
            }
        }

        // Existing Nano Multi-Agent & Retriever Panel (accessible from HUD quick actions)
        AnimatedVisibility(
            visible = uiState.isAutomationOpen,
            enter = if (reducedMotion) fadeIn(tween(90)) else enterOverlay,
            exit = if (reducedMotion) fadeOut(tween(70)) else exitOverlay,
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isWideScreen) 16.dp else 0.dp)
                .zIndex(15f)
        ) {
            AutomationPanel(
                activeModule = uiState.activeExtension,
                nanoMessages = uiState.nanoMessages,
                isNanoRunning = uiState.isNanoRunning,
                extractionRules = extractionRules,
                selectedRuleId = uiState.selectedRuleId,
                retrieverPromptInput = uiState.retrieverPromptInput,
                extractedRecords = uiState.extractedRecords,
                isRetrieverRunning = uiState.isRetrieverRunning,
                retrieverStatusBanner = uiState.retrieverStatusBanner,
                retrieverDisplayMode = uiState.retrieverDisplayMode,
                openTabsCount = uiState.tabs.size,
                isSideSheet = isWideScreen,
                onSelectModule = viewModel::selectExtensionModule,
                onRunNanoPrompt = viewModel::runNanoTask,
                onRetrieverPromptChange = viewModel::onRetrieverPromptChanged,
                onSelectRule = viewModel::selectExtractionRule,
                onSaveRule = viewModel::saveCurrentRetrieverRule,
                onDeleteRule = viewModel::deleteRetrieverRule,
                onCaptureCurrentPage = viewModel::captureCurrentPageWithRetriever,
                onRunParallelAllTabs = viewModel::runParallelMultiTabExtraction,
                onToggleDisplayMode = viewModel::setRetrieverDisplayMode,
                onDismiss = { viewModel.setAutomationOpen(false) }
            )
        }

        // Primary Automation HUD Bottom Sheet (opened from Toolbar Flutter Dash icon)
        if (uiState.isAutomationHudOpen) {
            AutomationHudSheet(
                hudMode = uiState.automationHudMode,
                pageInspection = uiState.pageInspection,
                recordingSession = uiState.recordingSession,
                activeTabId = activeTab.id,
                instructionDraftText = uiState.instructionDraftText,
                instructionDraftSteps = uiState.instructionDraftSteps,
                executionState = uiState.executionState,
                workflows = automationWorkflows,
                executions = automationExecutions,
                onSelectMode = viewModel::setAutomationHudMode,
                onScanPage = { viewModel.scanActivePageElements(silent = false) },
                onStartRecording = viewModel::startRecording,
                onPauseRecording = viewModel::pauseRecording,
                onResumeRecording = viewModel::resumeRecording,
                onStopRecording = viewModel::stopRecording,
                onMinimizeRecording = viewModel::minimizeRecordingHud,
                onRenameRecordingDraft = viewModel::renameRecordingDraft,
                onEditRecordingStep = viewModel::editRecordingStep,
                onDeleteRecordingStep = viewModel::deleteRecordingStep,
                onSaveRecording = viewModel::saveRecordingWorkflow,
                onDiscardRecording = viewModel::discardRecording,
                onInstructionTextChange = viewModel::onInstructionDraftChanged,
                onAddInstructions = viewModel::addInstructionsToDraft,
                onClearInstructions = viewModel::clearInstructionDraft,
                onReviewInstructionSteps = { viewModel.reviewInstructionSteps() },
                onEditInstructionStep = viewModel::editInstructionStep,
                onDeleteInstructionStep = viewModel::deleteInstructionStep,
                onMoveInstructionStepUp = viewModel::moveInstructionStepUp,
                onMoveInstructionStepDown = viewModel::moveInstructionStepDown,
                onSaveInstructionsAsWorkflow = viewModel::saveInstructionDraftAsWorkflow,
                onExecuteInstructions = { viewModel.executeInstructionDraft() },
                onRunWorkflow = { id -> viewModel.replayWorkflow(id) },
                onReplayWorkflow = viewModel::replayWorkflow,
                onPlayWorkflowWithOptions = { id, opts -> viewModel.replayWorkflow(id, opts) },
                onOpenWorkflowDetails = viewModel::openAutomationDetails,
                onOpenWorkspace = { viewModel.setAutomationWorkspaceOpen(true) },
                onOpenNanoRetrieverPanel = { viewModel.setAutomationOpen(true) },
                onConfirmDestructiveExecution = viewModel::confirmPendingDestructiveExecution,
                onCancelDestructiveExecution = viewModel::cancelPendingDestructiveExecution,
                onClearExecutionBanner = viewModel::clearExecutionBanner,
                onCancelExecution = viewModel::cancelWorkflowExecution,
                onPauseExecution = viewModel::pauseWorkflowExecution,
                onResumeExecution = viewModel::resumeWorkflowExecution,
                onEditRecordingStepDetailed = { stepId, desc, valText, targetLabel, selector ->
                    viewModel.editRecordingStep(stepId, desc, valText, targetLabel, selector)
                },
                onEditInstructionStepDetailed = { stepId, desc, actionType, valText, targetLabel, selector ->
                    viewModel.editInstructionStep(stepId, desc, actionType, valText, targetLabel, selector)
                },
                credentialVaultEntries = credentialVaultEntries,
                pendingCredentialReplayAuth = uiState.pendingCredentialReplayAuth,
                onConfirmCredentialReplay = viewModel::confirmPendingCredentialReplay,
                onCancelCredentialReplay = viewModel::cancelPendingCredentialReplay,
                onAssignCredentialToRecordingStep = viewModel::assignCredentialToRecordingStep,
                onOpenCredentialVault = {
                    viewModel.dismissAutomationHud()
                    viewModel.setPasswordManagerOpen(true)
                },
                onExtendedScan = viewModel::runExtendedPageScan,
                onCancelScan = { viewModel.cancelPageScan() },
                interruptedCandidate = uiState.interruptedExecutionCandidate,
                onResumeInterrupted = { viewModel.confirmResumeInterruptedExecution() },
                onRestartInterrupted = { viewModel.restartInterruptedExecution() },
                onDismissInterrupted = { viewModel.dismissInterruptedExecution() },
                onOpenDisambiguation = viewModel::openDisambiguationForExecution,
                lastLocatorDiagnostics = uiState.lastLocatorDiagnostics,
                isScannerOutlineOverlayEnabled = uiState.isScannerOutlineOverlayEnabled,
                onToggleScannerOutlines = viewModel::toggleScannerOutlineOverlay,
                onHighlightElement = viewModel::highlightDetectedElement,
                nanoMessages = uiState.nanoMessages,
                isNanoRunning = uiState.isNanoRunning,
                extractedRecords = uiState.extractedRecords,
                isRetrieverRunning = uiState.isRetrieverRunning,
                openTabsCount = uiState.tabs.size,
                onRunScannerAgent = viewModel::runNanoTask,
                onExtractCurrentPage = viewModel::captureCurrentPageWithRetriever,
                onExtractAllTabs = viewModel::runParallelMultiTabExtraction,
                isHomePage = activeTab.isHomePage,
                automationVaultCount = automationVaultCount,
                automationVaultEntries = automationVaultEntries,
                recordingVaultCandidates = uiState.recordingVaultCandidates,
                onOpenAutomationVault = { viewModel.setAutomationVaultOpen(true) },
                onToggleRecordingVaultCandidate = viewModel::toggleRecordingVaultCandidate,
                onUpdateRecordingVaultCandidate = viewModel::updateRecordingVaultCandidate,
                onAssignAutomationVaultToRecordingStep = viewModel::assignAutomationVaultEntryToRecordingStep,
                isProActivated = preferences.isProCurrentlyActive(subscriptionNowMillis),
                dailyPlaysRemaining = preferences.remainingFreeAutomationPlaysToday(nowMillis = subscriptionNowMillis),
                dailyPlayLimit = com.example.data.preferences.BrowserPreferences.FREE_DAILY_AUTOMATION_PLAY_LIMIT,
                onOpenProSubscription = { viewModel.setSubscriptionDialogOpen(true) },
                onDismiss = viewModel::dismissAutomationHud
            )
        }

        // Dedicated Automation Vault Overlay (Encrypted values for unlimited replays)
        AnimatedVisibility(
            visible = uiState.isAutomationVaultOpen,
            enter = if (reducedMotion) fadeIn(tween(90)) else enterOverlay,
            exit = if (reducedMotion) fadeOut(tween(70)) else exitOverlay,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(16f)
        ) {
            AutomationVaultScreenOverlay(
                entries = automationVaultEntries,
                revealedValues = uiState.automationVaultRevealedValues,
                activeTabUrl = activeTab.url,
                folders = automationVaultFolders,
                files = automationVaultFiles,
                onSaveEntry = { entryId, category, label, value, websiteOrigin, isSensitive ->
                    viewModel.saveAutomationVaultEntry(
                        entryId = entryId,
                        category = category,
                        label = label,
                        value = value,
                        websiteOrigin = websiteOrigin,
                        isSensitive = isSensitive
                    )
                },
                onSaveEntryInFolder = { entryId, category, label, value, websiteOrigin, isSensitive, folderId ->
                    viewModel.saveAutomationVaultEntry(
                        entryId = entryId,
                        category = category,
                        label = label,
                        value = value,
                        websiteOrigin = websiteOrigin,
                        isSensitive = isSensitive,
                        folderId = folderId
                    )
                },
                onImportFileCandidates = { candidates ->
                    viewModel.importAutomationVaultCandidates(candidates)
                },
                onImportFileCandidatesToFolder = { candidates, folderId ->
                    viewModel.importAutomationVaultCandidates(candidates, folderId)
                },
                onCreateFolder = viewModel::createAutomationVaultFolder,
                onRenameFolder = viewModel::renameAutomationVaultFolder,
                onDeleteFolder = viewModel::deleteAutomationVaultFolder,
                onUploadFileToFolder = { folderId, fileName, mimeType, bytes ->
                    viewModel.uploadFileToAutomationVaultFolder(
                        folderId = folderId,
                        fileName = fileName,
                        mimeType = mimeType,
                        bytes = bytes,
                        filesRootDir = context.filesDir
                    )
                },
                onUploadDirectoryFolder = { folderName, uploadedFiles ->
                    viewModel.uploadFolderToAutomationVault(
                        folderName = folderName,
                        files = uploadedFiles,
                        filesRootDir = context.filesDir
                    )
                },
                onDeleteFile = viewModel::deleteAutomationVaultFile,
                onDeleteEntry = viewModel::deleteAutomationVaultEntry,
                onRevealEntry = viewModel::revealAutomationVaultEntry,
                onHideEntry = viewModel::hideAutomationVaultEntry,
                backHandlerEnabled = uiState.isAutomationVaultOpen,
                initialFolderId = uiState.initialAutomationVaultFolderId,
                onDismiss = { viewModel.setAutomationVaultOpen(false) }
            )
        }

        uiState.vaultExhaustionPromptState?.let { exhaustedPrompt ->
            VaultExhaustionPromptDialog(
                prompt = exhaustedPrompt,
                onGenerateAndResume = { count ->
                    viewModel.generateValuesForExhaustedPrompt(
                        count = count,
                        autoReplayAfter = true
                    )
                },
                onOpenVault = viewModel::openVaultFromExhaustionPrompt,
                onDismiss = viewModel::dismissVaultExhaustionPrompt
            )
        }

        uiState.pendingDisambiguation?.let { pending ->
            LocatorDisambiguationDialog(
                state = pending,
                onSelectAndSave = { candidate ->
                    viewModel.resolveAmbiguousLocator(candidate, rerunAfterSelection = false)
                },
                onSelectAndRerun = { candidate ->
                    viewModel.resolveAmbiguousLocator(candidate, rerunAfterSelection = true)
                },
                onDismiss = viewModel::dismissPendingDisambiguation
            )
        }

        uiState.pendingDownloadPrompt?.let { pendingDownload ->
            AskWhereToSaveDialog(
                prompt = pendingDownload,
                preferences = preferences,
                onConfirm = { fileName, mode, customUri, customLabel, keepAsking ->
                    if (preferences.showDownloadProgressNotifications) {
                        requestNotificationPermissionIfNeeded()
                    }
                    viewModel.confirmPendingDownload(
                        fileName = fileName,
                        locationMode = mode,
                        customTreeUri = customUri,
                        customFolderLabel = customLabel,
                        keepAskingEveryTime = keepAsking
                    )
                },
                onDismiss = viewModel::dismissPendingDownload
            )
        }
    }
}

@Composable
private fun AskWhereToSaveDialog(
    prompt: PendingDownloadPrompt,
    preferences: BrowserPreferences,
    onConfirm: (
        fileName: String,
        locationMode: DownloadLocationMode,
        customTreeUri: String,
        customFolderLabel: String,
        keepAskingEveryTime: Boolean
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var fileNameInput by remember(prompt) { mutableStateOf(prompt.suggestedFileName) }
    var selectedMode by remember(prompt) { mutableStateOf(preferences.downloadLocationMode) }
    var selectedCustomUri by remember(prompt) { mutableStateOf(preferences.customDownloadTreeUri) }
    var selectedCustomLabel by remember(prompt) { mutableStateOf(preferences.customDownloadFolderLabel) }
    var keepAsking by remember(prompt) { mutableStateOf(preferences.askWhereToSaveFiles) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val uriStr = treeUri.toString()
            selectedMode = DownloadLocationMode.CUSTOM_SAF
            selectedCustomUri = uriStr
            selectedCustomLabel = DownloadStorageHelper.extractFolderLabelFromUri(uriStr)
                .ifBlank { context.getString(R.string.settings_download_location_custom) }
        }
    }

    val (activeLocationLabel, activeLocationPath) = remember(
        context,
        selectedMode,
        selectedCustomUri,
        selectedCustomLabel
    ) {
        DownloadStorageHelper.formatLocationSummary(
            context = context,
            mode = selectedMode,
            customTreeUri = selectedCustomUri,
            customLabel = selectedCustomLabel
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = stringResource(R.string.download_save_dialog_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ask_where_to_save_dialog")
            ) {
                OutlinedTextField(
                    value = fileNameInput,
                    onValueChange = { fileNameInput = it },
                    label = { Text(stringResource(R.string.download_save_filename_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ask_save_filename_input")
                )

                Text(
                    text = stringResource(R.string.download_save_location_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val optionCount = if (selectedCustomUri.isNotBlank()) 3 else 2
                Column(verticalArrangement = Arrangement.spacedBy(GroupedListSpacing)) {
                    MaterialGroupListItem(
                        index = 0,
                        totalCount = optionCount,
                        title = stringResource(R.string.settings_download_location_app_external),
                        subtitle = if (selectedMode == DownloadLocationMode.APP_EXTERNAL) activeLocationPath else null,
                        role = Role.RadioButton,
                        onClick = { selectedMode = DownloadLocationMode.APP_EXTERNAL },
                        trailingContent = {
                            RadioButton(
                                selected = selectedMode == DownloadLocationMode.APP_EXTERNAL,
                                onClick = null
                            )
                        },
                        modifier = Modifier.testTag("ask_save_location_external")
                    )

                    MaterialGroupListItem(
                        index = 1,
                        totalCount = optionCount,
                        title = stringResource(R.string.settings_download_location_app_internal),
                        subtitle = if (selectedMode == DownloadLocationMode.APP_INTERNAL) activeLocationPath else null,
                        role = Role.RadioButton,
                        onClick = { selectedMode = DownloadLocationMode.APP_INTERNAL },
                        trailingContent = {
                            RadioButton(
                                selected = selectedMode == DownloadLocationMode.APP_INTERNAL,
                                onClick = null
                            )
                        },
                        modifier = Modifier.testTag("ask_save_location_internal")
                    )

                    if (selectedCustomUri.isNotBlank()) {
                        MaterialGroupListItem(
                            index = 2,
                            totalCount = optionCount,
                            title = activeLocationLabel,
                            subtitle = selectedCustomUri,
                            role = Role.RadioButton,
                            onClick = { selectedMode = DownloadLocationMode.CUSTOM_SAF },
                            trailingContent = {
                                RadioButton(
                                    selected = selectedMode == DownloadLocationMode.CUSTOM_SAF,
                                    onClick = null
                                )
                            },
                            modifier = Modifier.testTag("ask_save_location_custom")
                        )
                    }
                }

                OutlinedButton(
                    onClick = { runCatching { folderPickerLauncher.launch(null) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ask_save_choose_folder_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_download_location_choose_folder))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = keepAsking,
                        onCheckedChange = { keepAsking = it },
                        modifier = Modifier.testTag("ask_save_always_ask_checkbox")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.download_save_always_ask_toggle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        fileNameInput,
                        selectedMode,
                        selectedCustomUri,
                        selectedCustomLabel,
                        keepAsking
                    )
                },
                enabled = fileNameInput.trim().isNotEmpty(),
                modifier = Modifier.testTag("ask_save_confirm_button")
            ) {
                Text(stringResource(R.string.download_save_confirm_action))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("ask_save_cancel_button")
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

fun isDeviceInternetConnected(context: Context): Boolean {
    return runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return@runCatching true
        val activeNetwork = cm.activeNetwork ?: return@runCatching false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return@runCatching false
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
    }.getOrDefault(true)
}
