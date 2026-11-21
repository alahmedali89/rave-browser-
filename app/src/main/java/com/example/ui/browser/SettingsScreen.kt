package com.example.ui.browser

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Https
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.SaveAs
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.WebAssetOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.preferences.BrowserPreferences
import com.example.data.preferences.DownloadLocationMode
import com.example.data.preferences.HomeWallpaperMode
import com.example.data.preferences.SearchEngineOption
import com.example.data.preferences.ThemeModeOption
import com.example.engine.DefaultBrowserStatus
import com.example.engine.DownloadStorageHelper
import com.example.engine.HomeWallpaperData
import com.example.ui.theme.EmphasizedTypography
import com.example.ui.theme.LocalApexDarkTheme
import com.example.ui.theme.SeedPalettePreset

enum class SettingsInitialSection {
    MAIN,
    APPEARANCE
}

private enum class SettingsSubPage {
    MAIN,
    SEARCH_ENGINES,
    PRIVACY_AND_SAFETY,
    DOWNLOADS,
    APPEARANCE,
    BROWSING,
    ACCESSIBILITY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenOverlay(
    preferences: BrowserPreferences,
    onSelectSearchEngine: (SearchEngineOption) -> Unit,
    onToggleShields: (Boolean) -> Unit,
    onToggleHttpsUpgrade: (Boolean) -> Unit,
    onToggleScriptGuard: (Boolean) -> Unit,
    onToggleSmartPreload: (Boolean) -> Unit,
    onToggleDesktopViewport: (Boolean) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onSelectSeedPalette: (SeedPalettePreset) -> Unit,
    onClearBrowsingData: () -> Unit,
    onSelectThemeMode: (ThemeModeOption) -> Unit = {},
    onToggleShowTopVisitsOnHome: (Boolean) -> Unit = {},
    customWallpaperBitmap: Bitmap? = null,
    activeOnlineWallpaper: HomeWallpaperData? = null,
    onSelectHomeWallpaperMode: (HomeWallpaperMode) -> Unit = {},
    onPickCustomWallpaperUri: (Uri) -> Unit = {},
    onToggleHomeWallpaperBlackAndWhite: (Boolean) -> Unit = {},
    onChangeDefaultZoom: (Int) -> Unit = {},
    onSetSiteZoom: (String, Int) -> Unit = { _, _ -> },
    onRemoveSiteZoom: (String) -> Unit = {},
    onClearSavedSiteZooms: () -> Unit = {},
    onToggleShowZoomInMainMenu: (Boolean) -> Unit = {},
    onToggleForceEnableZoom: (Boolean) -> Unit = {},
    onToggleSimplifiedView: (Boolean) -> Unit = {},
    onToggleCaretBrowsing: (Boolean) -> Unit = {},
    onToggleTouchpadSwipeNavigation: (Boolean) -> Unit = {},
    onSelectDownloadLocation: (DownloadLocationMode, String, String) -> Unit = { _, _, _ -> },
    onToggleAskWhereToSaveFiles: (Boolean) -> Unit = {},
    onToggleAutoOpenDownloadedFiles: (Boolean) -> Unit = {},
    onToggleShowDownloadProgressNotifications: (Boolean) -> Unit = {},
    onToggleParallelDownloading: (Boolean) -> Unit = {},
    onOpenPasswordManager: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenAutomations: () -> Unit = {},
    defaultBrowserStatus: DefaultBrowserStatus = DefaultBrowserStatus(),
    onOpenDefaultBrowserSettings: () -> Unit = {},
    initialSection: SettingsInitialSection = SettingsInitialSection.MAIN,
    mainListState: LazyListState = rememberLazyListState(),
    backHandlerEnabled: Boolean = true,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentPage by rememberSaveable(initialSection) {
        mutableStateOf(
            if (initialSection == SettingsInitialSection.APPEARANCE) {
                SettingsSubPage.APPEARANCE
            } else {
                SettingsSubPage.MAIN
            }
        )
    }

    when (currentPage) {
        SettingsSubPage.SEARCH_ENGINES -> {
            SearchEnginesFullPage(
                selectedEngine = preferences.searchEngine,
                onSelectSearchEngine = onSelectSearchEngine,
                onNavigateBack = { currentPage = SettingsSubPage.MAIN },
                modifier = modifier
            )
            return
        }
        SettingsSubPage.PRIVACY_AND_SAFETY -> {
            PrivacySafetyFullPage(
                preferences = preferences,
                onToggleShields = onToggleShields,
                onToggleHttpsUpgrade = onToggleHttpsUpgrade,
                onToggleScriptGuard = onToggleScriptGuard,
                onClearBrowsingData = onClearBrowsingData,
                onNavigateBack = { currentPage = SettingsSubPage.MAIN },
                modifier = modifier
            )
            return
        }
        SettingsSubPage.DOWNLOADS -> {
            DownloadsSettingsFullPage(
                preferences = preferences,
                onSelectDownloadLocation = onSelectDownloadLocation,
                onToggleAskWhereToSaveFiles = onToggleAskWhereToSaveFiles,
                onToggleAutoOpenDownloadedFiles = onToggleAutoOpenDownloadedFiles,
                onToggleShowDownloadProgressNotifications = onToggleShowDownloadProgressNotifications,
                onToggleParallelDownloading = onToggleParallelDownloading,
                onNavigateBack = { currentPage = SettingsSubPage.MAIN },
                onCloseAll = onDismiss,
                modifier = modifier
            )
            return
        }
        SettingsSubPage.APPEARANCE -> {
            AppearanceFullPage(
                preferences = preferences,
                onSelectThemeMode = onSelectThemeMode,
                onToggleDynamicColor = onToggleDynamicColor,
                onSelectSeedPalette = onSelectSeedPalette,
                onToggleShowTopVisitsOnHome = onToggleShowTopVisitsOnHome,
                customWallpaperBitmap = customWallpaperBitmap,
                activeOnlineWallpaper = activeOnlineWallpaper,
                onSelectHomeWallpaperMode = onSelectHomeWallpaperMode,
                onPickCustomWallpaperUri = onPickCustomWallpaperUri,
                onToggleHomeWallpaperBlackAndWhite = onToggleHomeWallpaperBlackAndWhite,
                onNavigateBack = {
                    if (initialSection == SettingsInitialSection.APPEARANCE) {
                        onDismiss()
                    } else {
                        currentPage = SettingsSubPage.MAIN
                    }
                },
                modifier = modifier
            )
            return
        }
        SettingsSubPage.BROWSING -> {
            BrowsingFullPage(
                preferences = preferences,
                onToggleSmartPreload = onToggleSmartPreload,
                onToggleDesktopViewport = onToggleDesktopViewport,
                onNavigateBack = { currentPage = SettingsSubPage.MAIN },
                modifier = modifier
            )
            return
        }
        SettingsSubPage.ACCESSIBILITY -> {
            AccessibilityFullPage(
                preferences = preferences,
                onChangeDefaultZoom = onChangeDefaultZoom,
                onSetSiteZoom = onSetSiteZoom,
                onRemoveSiteZoom = onRemoveSiteZoom,
                onClearSavedSiteZooms = onClearSavedSiteZooms,
                onToggleShowZoomInMainMenu = onToggleShowZoomInMainMenu,
                onToggleForceEnableZoom = onToggleForceEnableZoom,
                onToggleSimplifiedView = onToggleSimplifiedView,
                onToggleCaretBrowsing = onToggleCaretBrowsing,
                onToggleTouchpadSwipeNavigation = onToggleTouchpadSwipeNavigation,
                onNavigateBack = { currentPage = SettingsSubPage.MAIN },
                modifier = modifier
            )
            return
        }
        SettingsSubPage.MAIN -> Unit
    }

    BackHandler(enabled = backHandlerEnabled, onBack = onDismiss)

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = EmphasizedTypography.headlineHero
                    )
                }
            )
        },
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .testTag("settings_screen")
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                state = mainListState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .testTag("settings_scroll_list")
            ) {
                // Group 1: Search Engine (selected engine as subheading) & Default Browser
                item(key = "group_defaults") {
                    val subtitleText = when {
                        defaultBrowserStatus.isApexDefault ->
                            stringResource(R.string.settings_default_browser_apex_selected)
                        defaultBrowserStatus.currentDefaultPackage != null ->
                            stringResource(
                                R.string.settings_default_browser_other_selected,
                                defaultBrowserStatus.currentDefaultName
                            )
                        else ->
                            stringResource(R.string.settings_default_browser_none_selected)
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(GroupedListSpacing),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MaterialGroupListItem(
                            index = 0,
                            totalCount = 2,
                            title = stringResource(R.string.settings_search_engines_title),
                            subtitle = preferences.searchEngine.displayName,
                            role = Role.Button,
                            onClick = { currentPage = SettingsSubPage.SEARCH_ENGINES },
                            leadingContent = {
                                SearchEngineLogoBadge(
                                    engine = preferences.searchEngine,
                                    highlighted = true
                                )
                            },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.testTag("settings_search_engine_card")
                        )

                        MaterialGroupListItem(
                            index = 1,
                            totalCount = 2,
                            title = stringResource(R.string.settings_default_browser_title),
                            subtitle = subtitleText,
                            containerColor = if (defaultBrowserStatus.isApexDefault) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            border = if (defaultBrowserStatus.isApexDefault) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                            } else {
                                null
                            },
                            role = Role.Button,
                            onClick = onOpenDefaultBrowserSettings,
                            leadingContent = {
                                SettingsIconBadge(
                                    icon = Icons.Outlined.Public,
                                    highlighted = defaultBrowserStatus.isApexDefault
                                )
                            },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.testTag("settings_default_browser_card")
                        )
                    }
                }

                // Group 2: Category items opening dedicated sub-screens & tools
                item(key = "group_categories") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(GroupedListSpacing),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SettingsCategoryItem(
                            index = 0,
                            totalCount = 8,
                            icon = Icons.Outlined.Security,
                            title = stringResource(R.string.settings_privacy_section),
                            onClick = { currentPage = SettingsSubPage.PRIVACY_AND_SAFETY },
                            tag = "settings_privacy_card"
                        )
                        SettingsCategoryItem(
                            index = 1,
                            totalCount = 8,
                            icon = Icons.Outlined.Key,
                            title = stringResource(R.string.menu_passwords),
                            onClick = onOpenPasswordManager,
                            tag = "settings_passwords_card"
                        )
                        SettingsCategoryItem(
                            index = 2,
                            totalCount = 8,
                            icon = Icons.Outlined.Download,
                            title = stringResource(R.string.settings_downloads_section),
                            onClick = { currentPage = SettingsSubPage.DOWNLOADS },
                            tag = "settings_downloads_card"
                        )
                        SettingsCategoryItem(
                            index = 3,
                            totalCount = 8,
                            icon = Icons.Outlined.History,
                            title = stringResource(R.string.menu_history),
                            onClick = onOpenHistory,
                            tag = "settings_history_card"
                        )
                        SettingsCategoryItem(
                            index = 4,
                            totalCount = 8,
                            icon = Icons.Outlined.Palette,
                            title = stringResource(R.string.settings_appearance_section),
                            onClick = { currentPage = SettingsSubPage.APPEARANCE },
                            tag = "settings_appearance_card"
                        )
                        SettingsCategoryItem(
                            index = 5,
                            totalCount = 8,
                            icon = Icons.Outlined.AccessibilityNew,
                            title = stringResource(R.string.settings_accessibility_section),
                            onClick = { currentPage = SettingsSubPage.ACCESSIBILITY },
                            tag = "settings_accessibility_card"
                        )
                        SettingsCategoryItem(
                            index = 6,
                            totalCount = 8,
                            icon = ApexIcons.MotionSensorActive,
                            title = stringResource(R.string.settings_automations_section),
                            onClick = onOpenAutomations,
                            tag = "settings_automations_card"
                        )
                        SettingsCategoryItem(
                            index = 7,
                            totalCount = 8,
                            icon = Icons.Outlined.Speed,
                            title = stringResource(R.string.settings_browsing_section),
                            onClick = { currentPage = SettingsSubPage.BROWSING },
                            tag = "settings_browsing_card"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCategoryItem(
    index: Int,
    totalCount: Int,
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    tag: String
) {
    MaterialGroupListItem(
        index = index,
        totalCount = totalCount,
        title = title,
        subtitle = null,
        role = Role.Button,
        onClick = onClick,
        leadingContent = {
            SettingsIconBadge(icon = icon)
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier.testTag(tag)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSubPageScaffold(
    title: String,
    screenTag: String,
    backButtonTag: String,
    listTag: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    BackHandler(onBack = onNavigateBack)

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag(backButtonTag)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                },
                title = {
                    Text(
                        text = title,
                        style = EmphasizedTypography.headlineHero
                    )
                },
                actions = actions
            )
        },
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .testTag(screenTag)
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .testTag(listTag),
                content = content
            )
        }
    }
}

@Composable
private fun PrivacySafetyFullPage(
    preferences: BrowserPreferences,
    onToggleShields: (Boolean) -> Unit,
    onToggleHttpsUpgrade: (Boolean) -> Unit,
    onToggleScriptGuard: (Boolean) -> Unit,
    onClearBrowsingData: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsSubPageScaffold(
        title = stringResource(R.string.settings_privacy_section),
        screenTag = "settings_privacy_screen",
        backButtonTag = "settings_privacy_back_button",
        listTag = "settings_privacy_list",
        onNavigateBack = onNavigateBack,
        modifier = modifier
    ) {
        item(key = "privacy_safety_items") {
            Column(
                verticalArrangement = Arrangement.spacedBy(GroupedListSpacing),
                modifier = Modifier.fillMaxWidth()
            ) {
                FormattedSettingsSwitchItem(
                    index = 0,
                    totalCount = 4,
                    icon = Icons.Outlined.Security,
                    title = stringResource(R.string.settings_shields_title),
                    checked = preferences.shieldsEnabled,
                    onCheckedChange = onToggleShields,
                    tag = "settings_switch_shields"
                )
                FormattedSettingsSwitchItem(
                    index = 1,
                    totalCount = 4,
                    icon = Icons.Outlined.Https,
                    title = stringResource(R.string.settings_https_title),
                    checked = preferences.httpsUpgradeEnabled,
                    onCheckedChange = onToggleHttpsUpgrade,
                    tag = "settings_switch_https"
                )
                FormattedSettingsSwitchItem(
                    index = 2,
                    totalCount = 4,
                    icon = Icons.Outlined.WebAssetOff,
                    title = stringResource(R.string.settings_script_guard_title),
                    checked = preferences.scriptGuardEnabled,
                    onCheckedChange = onToggleScriptGuard,
                    tag = "settings_switch_script_guard"
                )
                MaterialGroupListItem(
                    index = 3,
                    totalCount = 4,
                    title = stringResource(R.string.settings_clear_data_button),
                    subtitle = null,
                    role = Role.Button,
                    onClick = onClearBrowsingData,
                    leadingContent = {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.CleaningServices,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.testTag("settings_clear_data_button")
                )
            }
        }
    }
}

@Composable
private fun DownloadsSettingsFullPage(
    preferences: BrowserPreferences,
    onSelectDownloadLocation: (DownloadLocationMode, String, String) -> Unit,
    onToggleAskWhereToSaveFiles: (Boolean) -> Unit,
    onToggleAutoOpenDownloadedFiles: (Boolean) -> Unit,
    onToggleShowDownloadProgressNotifications: (Boolean) -> Unit,
    onToggleParallelDownloading: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    onCloseAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showLocationDialog by remember { mutableStateOf(false) }

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
            val label = DownloadStorageHelper.extractFolderLabelFromUri(uriStr)
                .ifBlank { context.getString(R.string.settings_download_location_custom) }
            onSelectDownloadLocation(DownloadLocationMode.CUSTOM_SAF, uriStr, label)
            showLocationDialog = false
        }
    }

    val (locationLabel, locationPath) = remember(
        context,
        preferences.downloadLocationMode,
        preferences.customDownloadTreeUri,
        preferences.customDownloadFolderLabel
    ) {
        DownloadStorageHelper.formatLocationSummary(context, preferences)
    }

    val onLabel = stringResource(R.string.menu_toggle_on)
    val offLabel = stringResource(R.string.menu_toggle_off)

    SettingsSubPageScaffold(
        title = stringResource(R.string.settings_downloads_section),
        screenTag = "settings_downloads_screen",
        backButtonTag = "settings_downloads_back_button",
        listTag = "settings_downloads_list",
        onNavigateBack = onNavigateBack,
        actions = {
            IconButton(
                onClick = onCloseAll,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("settings_downloads_close_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cd_close_settings)
                )
            }
        },
        modifier = modifier
    ) {
        item(key = "downloads_settings_items") {
            Column(
                verticalArrangement = Arrangement.spacedBy(GroupedListSpacing),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. Download location
                MaterialGroupListItem(
                    index = 0,
                    totalCount = 5,
                    title = stringResource(R.string.settings_download_location_title),
                    subtitle = "$locationLabel\n$locationPath",
                    role = Role.Button,
                    onClick = { showLocationDialog = true },
                    leadingContent = {
                        SettingsIconBadge(icon = Icons.Outlined.FolderOpen)
                    },
                    modifier = Modifier.testTag("settings_download_location_card")
                )

                // 2. Ask where to save files
                FormattedSettingsSwitchItem(
                    index = 1,
                    totalCount = 5,
                    icon = Icons.Outlined.SaveAs,
                    title = stringResource(R.string.settings_ask_where_to_save_title),
                    description = if (preferences.askWhereToSaveFiles) onLabel else offLabel,
                    checked = preferences.askWhereToSaveFiles,
                    onCheckedChange = onToggleAskWhereToSaveFiles,
                    tag = "settings_switch_ask_where_to_save"
                )

                // 3. Automatically open when possible
                FormattedSettingsSwitchItem(
                    index = 2,
                    totalCount = 5,
                    icon = Icons.Outlined.OpenInNew,
                    title = stringResource(R.string.settings_auto_open_downloads_title),
                    description = if (preferences.autoOpenDownloadedFiles) onLabel else offLabel,
                    checked = preferences.autoOpenDownloadedFiles,
                    onCheckedChange = onToggleAutoOpenDownloadedFiles,
                    tag = "settings_switch_auto_open_downloads"
                )

                // 4. Show download progress notifications
                FormattedSettingsSwitchItem(
                    index = 3,
                    totalCount = 5,
                    icon = Icons.Outlined.NotificationsActive,
                    title = stringResource(R.string.settings_download_notifications_title),
                    description = if (preferences.showDownloadProgressNotifications) onLabel else offLabel,
                    checked = preferences.showDownloadProgressNotifications,
                    onCheckedChange = onToggleShowDownloadProgressNotifications,
                    tag = "settings_switch_download_notifications"
                )

                // 5. Parallel downloading
                FormattedSettingsSwitchItem(
                    index = 4,
                    totalCount = 5,
                    icon = Icons.Outlined.Bolt,
                    title = stringResource(R.string.settings_parallel_downloading_title),
                    description = stringResource(R.string.settings_parallel_downloading_desc),
                    checked = preferences.parallelDownloadingEnabled,
                    onCheckedChange = onToggleParallelDownloading,
                    tag = "settings_switch_parallel_downloading"
                )
            }
        }
    }

    if (showLocationDialog) {
        DownloadLocationPickerDialog(
            preferences = preferences,
            onSelectMode = { mode ->
                onSelectDownloadLocation(
                    mode,
                    preferences.customDownloadTreeUri,
                    preferences.customDownloadFolderLabel
                )
                showLocationDialog = false
            },
            onPickCustomFolder = {
                runCatching {
                    folderPickerLauncher.launch(null)
                }
            },
            onDismiss = { showLocationDialog = false }
        )
    }
}

@Composable
private fun DownloadLocationPickerDialog(
    preferences: BrowserPreferences,
    onSelectMode: (DownloadLocationMode) -> Unit,
    onPickCustomFolder: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val externalPath = remember(context) {
        DownloadStorageHelper.resolveDirectory(context, DownloadLocationMode.APP_EXTERNAL).absolutePath
    }
    val internalPath = remember(context) {
        DownloadStorageHelper.resolveDirectory(context, DownloadLocationMode.APP_INTERNAL).absolutePath
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = stringResource(R.string.settings_download_location_dialog_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(GroupedListSpacing),
                modifier = Modifier.fillMaxWidth()
            ) {
                val hasCustomUri = preferences.customDownloadTreeUri.isNotBlank()
                val optionCount = if (hasCustomUri) 3 else 2

                MaterialGroupListItem(
                    index = 0,
                    totalCount = optionCount,
                    title = stringResource(R.string.settings_download_location_app_external),
                    subtitle = externalPath,
                    role = Role.RadioButton,
                    onClick = { onSelectMode(DownloadLocationMode.APP_EXTERNAL) },
                    trailingContent = {
                        RadioButton(
                            selected = preferences.downloadLocationMode == DownloadLocationMode.APP_EXTERNAL,
                            onClick = null
                        )
                    },
                    modifier = Modifier.testTag("download_location_option_external")
                )

                MaterialGroupListItem(
                    index = 1,
                    totalCount = optionCount,
                    title = stringResource(R.string.settings_download_location_app_internal),
                    subtitle = internalPath,
                    role = Role.RadioButton,
                    onClick = { onSelectMode(DownloadLocationMode.APP_INTERNAL) },
                    trailingContent = {
                        RadioButton(
                            selected = preferences.downloadLocationMode == DownloadLocationMode.APP_INTERNAL,
                            onClick = null
                        )
                    },
                    modifier = Modifier.testTag("download_location_option_internal")
                )

                if (hasCustomUri) {
                    val customSub = preferences.customDownloadFolderLabel.ifBlank {
                        preferences.customDownloadTreeUri
                    }
                    MaterialGroupListItem(
                        index = 2,
                        totalCount = optionCount,
                        title = stringResource(R.string.settings_download_location_custom),
                        subtitle = customSub,
                        role = Role.RadioButton,
                        onClick = { onSelectMode(DownloadLocationMode.CUSTOM_SAF) },
                        trailingContent = {
                            RadioButton(
                                selected = preferences.downloadLocationMode == DownloadLocationMode.CUSTOM_SAF,
                                onClick = null
                            )
                        },
                        modifier = Modifier.testTag("download_location_option_custom")
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedButton(
                    onClick = onPickCustomFolder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("download_location_choose_folder_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = stringResource(R.string.settings_download_location_choose_folder),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("download_location_done_button")
            ) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

@Composable
private fun AppearanceFullPage(
    preferences: BrowserPreferences,
    onSelectThemeMode: (ThemeModeOption) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onSelectSeedPalette: (SeedPalettePreset) -> Unit,
    onToggleShowTopVisitsOnHome: (Boolean) -> Unit,
    customWallpaperBitmap: Bitmap? = null,
    activeOnlineWallpaper: HomeWallpaperData? = null,
    onSelectHomeWallpaperMode: (HomeWallpaperMode) -> Unit = {},
    onPickCustomWallpaperUri: (Uri) -> Unit = {},
    onToggleHomeWallpaperBlackAndWhite: (Boolean) -> Unit = {},
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onPickCustomWallpaperUri(uri)
        }
    }

    val isDarkTheme = LocalApexDarkTheme.current ?: isSystemInDarkTheme()

    SettingsSubPageScaffold(
        title = stringResource(R.string.settings_appearance_section),
        screenTag = "settings_appearance_screen",
        backButtonTag = "settings_appearance_back_button",
        listTag = "settings_appearance_list",
        onNavigateBack = onNavigateBack,
        modifier = modifier
    ) {
        item(key = "appearance_items") {
            Column(
                verticalArrangement = Arrangement.spacedBy(GroupedListSpacing),
                modifier = Modifier.fillMaxWidth()
            ) {
                val totalAppearanceCards = 5
                val themeModeShape = groupedListItemShape(index = 0, totalCount = totalAppearanceCards)
                Surface(
                    shape = themeModeShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        0.75.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(themeModeShape)
                        .testTag("settings_theme_mode_card")
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            val headerIcon = when (preferences.themeMode) {
                                ThemeModeOption.SYSTEM -> Icons.Outlined.BrightnessAuto
                                ThemeModeOption.LIGHT -> Icons.Outlined.LightMode
                                ThemeModeOption.DARK -> Icons.Outlined.DarkMode
                            }
                            SettingsIconBadge(icon = headerIcon, highlighted = true)
                            Text(
                                text = stringResource(R.string.settings_theme_mode_label),
                                style = EmphasizedTypography.activeTabTitle,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            ThemeModeOption.entries.forEach { mode ->
                                val isSelected = preferences.themeMode == mode
                                val modeIcon = when (mode) {
                                    ThemeModeOption.SYSTEM -> Icons.Outlined.BrightnessAuto
                                    ThemeModeOption.LIGHT -> Icons.Outlined.LightMode
                                    ThemeModeOption.DARK -> Icons.Outlined.DarkMode
                                }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onSelectThemeMode(mode) },
                                    label = { Text(mode.label) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = modeIcon,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.testTag("settings_theme_mode_${mode.id}")
                                )
                            }
                        }
                    }
                }

                FormattedSettingsSwitchItem(
                    index = 1,
                    totalCount = totalAppearanceCards,
                    icon = Icons.Outlined.Palette,
                    title = stringResource(R.string.settings_dynamic_color_title),
                    checked = preferences.dynamicColorEnabled,
                    onCheckedChange = onToggleDynamicColor,
                    tag = "settings_switch_dynamic_color"
                )

                val paletteItemShape = groupedListItemShape(index = 2, totalCount = totalAppearanceCards)
                Surface(
                    shape = paletteItemShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        0.75.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(paletteItemShape)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            SettingsIconBadge(icon = Icons.Outlined.ColorLens)
                            Text(
                                text = stringResource(R.string.settings_seed_palette_label),
                                style = EmphasizedTypography.activeTabTitle,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            SeedPalettePreset.entries.forEach { preset ->
                                val isSelected = !preferences.dynamicColorEnabled &&
                                    preferences.seedPreset == preset
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onSelectSeedPalette(preset) },
                                    label = { Text(preset.label) },
                                    leadingIcon = {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(preset.seedColor)
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Filled.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.surface,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.testTag("settings_palette_${preset.name}")
                                )
                            }
                        }
                    }
                }

                val wallpaperCardShape = groupedListItemShape(index = 3, totalCount = totalAppearanceCards)
                Surface(
                    shape = wallpaperCardShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        0.75.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(wallpaperCardShape)
                        .testTag("settings_home_wallpaper_card")
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            SettingsIconBadge(icon = Icons.Outlined.Wallpaper, highlighted = true)
                            Text(
                                text = stringResource(R.string.settings_home_wallpaper_label),
                                style = EmphasizedTypography.activeTabTitle,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Live miniature wallpaper preview
                        val previewBitmap = when (preferences.homeWallpaperMode) {
                            HomeWallpaperMode.ONLINE_API -> activeOnlineWallpaper?.bitmap
                            HomeWallpaperMode.CUSTOM_PHONE -> customWallpaperBitmap
                            HomeWallpaperMode.BLACK_AND_WHITE -> null
                        }
                        val previewBwFilter = remember(preferences.homeWallpaperBlackAndWhite) {
                            if (preferences.homeWallpaperBlackAndWhite) {
                                ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                            } else {
                                null
                            }
                        }
                        val previewSolidColor = when (preferences.homeWallpaperMode) {
                            HomeWallpaperMode.BLACK_AND_WHITE -> if (isDarkTheme) Color.Black else Color.White
                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                        }
                        val previewTextColor = when {
                            previewBitmap != null -> Color.White
                            preferences.homeWallpaperMode == HomeWallpaperMode.BLACK_AND_WHITE ->
                                if (isDarkTheme) Color.White else Color.Black
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val previewCaption = when (preferences.homeWallpaperMode) {
                            HomeWallpaperMode.ONLINE_API ->
                                activeOnlineWallpaper?.author?.let { "Online API • $it" }
                                    ?: stringResource(R.string.settings_wallpaper_preview_online)
                            HomeWallpaperMode.CUSTOM_PHONE ->
                                if (customWallpaperBitmap != null || preferences.customHomeWallpaperPath.isNotBlank()) {
                                    stringResource(R.string.settings_wallpaper_preview_phone_set)
                                } else {
                                    stringResource(R.string.settings_wallpaper_preview_phone_empty)
                                }
                            HomeWallpaperMode.BLACK_AND_WHITE ->
                                stringResource(R.string.settings_wallpaper_preview_bw)
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = previewSolidColor,
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(84.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .testTag("settings_wallpaper_preview")
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (previewBitmap != null) {
                                    val previewImage = remember(previewBitmap) { previewBitmap.asImageBitmap() }
                                    Image(
                                        bitmap = previewImage,
                                        contentDescription = previewCaption,
                                        contentScale = ContentScale.Crop,
                                        colorFilter = previewBwFilter,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.32f))
                                    )
                                }
                                Text(
                                    text = previewCaption,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = previewTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }
                        }

                        // Source mode chips
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            HomeWallpaperMode.entries.forEach { mode ->
                                val isSelected = preferences.homeWallpaperMode == mode
                                val modeIcon = when (mode) {
                                    HomeWallpaperMode.ONLINE_API -> Icons.Outlined.Public
                                    HomeWallpaperMode.CUSTOM_PHONE -> Icons.Outlined.PhotoLibrary
                                    HomeWallpaperMode.BLACK_AND_WHITE -> Icons.Outlined.Contrast
                                }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        onSelectHomeWallpaperMode(mode)
                                        if (mode == HomeWallpaperMode.CUSTOM_PHONE &&
                                            preferences.customHomeWallpaperPath.isBlank()
                                        ) {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(
                                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                                )
                                            )
                                        }
                                    },
                                    label = { Text(mode.label) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = modeIcon,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.testTag("settings_wallpaper_mode_${mode.id}")
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("settings_wallpaper_pick_phone_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = stringResource(
                                    if (preferences.customHomeWallpaperPath.isNotBlank()) {
                                        R.string.settings_wallpaper_change_phone
                                    } else {
                                        R.string.settings_wallpaper_pick_phone
                                    }
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (preferences.homeWallpaperMode != HomeWallpaperMode.BLACK_AND_WHITE) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp)
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_wallpaper_bw_filter),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_wallpaper_bw_filter_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = preferences.homeWallpaperBlackAndWhite,
                                    onCheckedChange = onToggleHomeWallpaperBlackAndWhite,
                                    modifier = Modifier.testTag("settings_wallpaper_bw_switch")
                                )
                            }
                        }
                    }
                }

                FormattedSettingsSwitchItem(
                    index = 4,
                    totalCount = totalAppearanceCards,
                    icon = Icons.Outlined.Bookmarks,
                    title = stringResource(R.string.settings_top_visits_title),
                    checked = preferences.showTopVisitsOnHome,
                    onCheckedChange = onToggleShowTopVisitsOnHome,
                    tag = "settings_switch_top_visits"
                )
            }
        }
    }
}

@Composable
private fun BrowsingFullPage(
    preferences: BrowserPreferences,
    onToggleSmartPreload: (Boolean) -> Unit,
    onToggleDesktopViewport: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsSubPageScaffold(
        title = stringResource(R.string.settings_browsing_section),
        screenTag = "settings_browsing_screen",
        backButtonTag = "settings_browsing_back_button",
        listTag = "settings_browsing_list",
        onNavigateBack = onNavigateBack,
        modifier = modifier
    ) {
        item(key = "browsing_items") {
            Column(
                verticalArrangement = Arrangement.spacedBy(GroupedListSpacing),
                modifier = Modifier.fillMaxWidth()
            ) {
                FormattedSettingsSwitchItem(
                    index = 0,
                    totalCount = 2,
                    icon = Icons.Outlined.Speed,
                    title = stringResource(R.string.settings_preload_title),
                    checked = preferences.aggressivePreloadEnabled,
                    onCheckedChange = onToggleSmartPreload,
                    tag = "settings_switch_preload"
                )
                FormattedSettingsSwitchItem(
                    index = 1,
                    totalCount = 2,
                    icon = Icons.Outlined.DesktopWindows,
                    title = stringResource(R.string.settings_desktop_title),
                    checked = preferences.desktopViewportEnabled,
                    onCheckedChange = onToggleDesktopViewport,
                    tag = "settings_switch_desktop"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchEnginesFullPage(
    selectedEngine: SearchEngineOption,
    onSelectSearchEngine: (SearchEngineOption) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onNavigateBack)

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val engines = SearchEngineOption.entries

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("settings_search_engines_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.settings_search_engines_title),
                        style = EmphasizedTypography.headlineHero
                    )
                }
            )
        },
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .testTag("settings_search_engines_screen")
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .testTag("settings_search_engines_list")
            ) {
                item(key = "search_engines_grouped_items") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(GroupedListSpacing),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        engines.forEachIndexed { index, engine ->
                            val selected = engine == selectedEngine
                            MaterialGroupListItem(
                                index = index,
                                totalCount = engines.size,
                                title = engine.displayName,
                                subtitle = null,
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                                border = if (selected) {
                                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                } else {
                                    null
                                },
                                role = Role.RadioButton,
                                onClick = { onSelectSearchEngine(engine) },
                                leadingContent = {
                                    SearchEngineLogoBadge(
                                        engine = engine,
                                        highlighted = selected
                                    )
                                },
                                trailingContent = {
                                    RadioButton(
                                        selected = selected,
                                        onClick = null
                                    )
                                },
                                modifier = Modifier.testTag("settings_engine_option_${engine.id}")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchEngineLogoBadge(
    engine: SearchEngineOption,
    highlighted: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            SearchEngineLogo(
                engine = engine,
                size = 24.dp
            )
        }
    }
}

@Composable
internal fun SettingsIconBadge(
    icon: ImageVector,
    highlighted: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (highlighted) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun FormattedSettingsSwitchItem(
    index: Int,
    totalCount: Int,
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String,
    description: String? = null
) {
    MaterialGroupListItem(
        index = index,
        totalCount = totalCount,
        title = title,
        subtitle = description,
        role = Role.Switch,
        onClick = { onCheckedChange(!checked) },
        leadingContent = {
            SettingsIconBadge(icon = icon, highlighted = checked)
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null
            )
        },
        modifier = Modifier.testTag(tag)
    )
}
