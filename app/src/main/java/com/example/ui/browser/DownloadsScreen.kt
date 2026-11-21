package com.example.ui.browser

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.DownloadItemEntity
import com.example.data.preferences.DownloadLocationMode
import com.example.engine.DownloadStorageHelper
import com.example.engine.FastDownloadEngine
import com.example.ui.theme.EmphasizedTypography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class DownloadFilterTab(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    DOCUMENTS("Documents"),
    IMAGES("Images"),
    MEDIA("Media"),
    ARCHIVES("Archives")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreenOverlay(
    downloads: List<DownloadItemEntity>,
    onStartNewDownload: (url: String, fileName: String?) -> Unit,
    onPauseDownload: (Long) -> Unit,
    onResumeDownload: (Long) -> Unit,
    onRetryDownload: (Long) -> Unit,
    onCancelDownload: (Long) -> Unit,
    onDeleteDownload: (Long) -> Unit,
    onClearCompletedDownloads: () -> Unit,
    onClearAllDownloads: () -> Unit,
    onOpenUrlInBrowser: (String) -> Unit,
    onDismiss: () -> Unit,
    backHandlerEnabled: Boolean = true,
    downloadLocationMode: DownloadLocationMode = DownloadLocationMode.APP_EXTERNAL,
    parallelDownloadingEnabled: Boolean = true,
    storageSpaceInfoOverride: DownloadStorageHelper.StorageSpaceInfo? = null,
    onOpenDownload: ((DownloadItemEntity) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = backHandlerEnabled, onBack = onDismiss)

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(DownloadFilterTab.ALL) }
    var inspectedDownload by remember { mutableStateOf<DownloadItemEntity?>(null) }
    var showNewDownloadDialog by rememberSaveable { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    val openDownloadedItem: (DownloadItemEntity) -> Unit = { targetItem ->
        if (onOpenDownload != null) {
            onOpenDownload(targetItem)
        } else {
            val result = DownloadStorageHelper.openDownloadedFile(
                context = context,
                localFilePath = targetItem.localFilePath,
                fileName = targetItem.fileName,
                mimeType = targetItem.mimeType
            )
            if (result is DownloadStorageHelper.OpenDownloadedFileResult.Failed) {
                Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val activeDownloads = remember(downloads) {
        downloads.filter {
            it.status == FastDownloadEngine.STATUS_DOWNLOADING ||
                it.status == FastDownloadEngine.STATUS_PAUSED
        }
    }
    val completedDownloadsCount = remember(downloads) {
        downloads.count { it.status == FastDownloadEngine.STATUS_COMPLETED }
    }
    val totalTrackedBytes = remember(downloads) {
        downloads.sumOf { maxOf(it.downloadedBytes, 0L) }
    }
    val storageSpaceInfo = remember(
        downloads.size,
        completedDownloadsCount,
        totalTrackedBytes,
        downloadLocationMode,
        storageSpaceInfoOverride
    ) {
        storageSpaceInfoOverride ?: DownloadStorageHelper.getStorageSpaceInfo(
            context = context,
            mode = downloadLocationMode,
            downloads = downloads
        )
    }
    val aggregateActiveSpeed = remember(downloads) {
        downloads
            .filter { it.status == FastDownloadEngine.STATUS_DOWNLOADING }
            .sumOf { it.speedBytesPerSec.coerceAtLeast(0L) }
    }

    val filteredDownloads = remember(downloads, searchQuery, selectedFilter) {
        val q = searchQuery.trim().lowercase(Locale.US)
        downloads.filter { item ->
            val matchesQuery = q.isEmpty() ||
                item.fileName.lowercase(Locale.US).contains(q) ||
                item.url.lowercase(Locale.US).contains(q) ||
                item.category.lowercase(Locale.US).contains(q)

            val matchesFilter = when (selectedFilter) {
                DownloadFilterTab.ALL -> true
                DownloadFilterTab.ACTIVE ->
                    item.status == FastDownloadEngine.STATUS_DOWNLOADING ||
                        item.status == FastDownloadEngine.STATUS_PAUSED
                DownloadFilterTab.DOCUMENTS -> item.category.equals("Document", ignoreCase = true)
                DownloadFilterTab.IMAGES -> item.category.equals("Image", ignoreCase = true)
                DownloadFilterTab.MEDIA ->
                    item.category.equals("Video", ignoreCase = true) ||
                        item.category.equals("Audio", ignoreCase = true)
                DownloadFilterTab.ARCHIVES ->
                    item.category.equals("Archive", ignoreCase = true) ||
                        item.category.equals("Other", ignoreCase = true)
            }
            matchesQuery && matchesFilter
        }
    }

    val activeGroupItems = remember(filteredDownloads) {
        filteredDownloads.filter {
            it.status == FastDownloadEngine.STATUS_DOWNLOADING ||
                it.status == FastDownloadEngine.STATUS_PAUSED
        }
    }
    val historyGroupItems = remember(filteredDownloads) {
        filteredDownloads.filterNot {
            it.status == FastDownloadEngine.STATUS_DOWNLOADING ||
                it.status == FastDownloadEngine.STATUS_PAUSED
        }
    }

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
                            .testTag("downloads_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.downloads_screen_title),
                            style = EmphasizedTypography.headlineHero,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (downloads.isNotEmpty()) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = downloads.size.toString(),
                                    style = EmphasizedTypography.badgeCounter,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(end = 10.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .clickable(role = Role.Button) { showNewDownloadDialog = true }
                                .testTag("downloads_new_url_button")
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(width = 48.dp, height = 38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = stringResource(R.string.downloads_new_button),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        if (downloads.isNotEmpty()) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(CircleShape)
                                    .clickable(role = Role.Button) { showClearDialog = true }
                                    .testTag("downloads_clear_button")
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.88f),
                                    modifier = Modifier.size(width = 48.dp, height = 38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Outlined.CleaningServices,
                                            contentDescription = stringResource(R.string.downloads_clear_button),
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            )
        },
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .testTag("downloads_screen_overlay")
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
        ) {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp)
                    .testTag("downloads_list_column")
            ) {
                // 1. Material 3 Expressive 3-Tile Fast Download Telemetry Header
                item(key = "downloads_expressive_summary") {
                    val activeStreamingDownloads = activeDownloads.filter {
                        it.status == FastDownloadEngine.STATUS_DOWNLOADING
                    }
                    val maxActiveChunks = activeStreamingDownloads.maxOfOrNull { it.chunksCount } ?: 1
                    DownloadsExpressiveSummaryCard(
                        completedCount = completedDownloadsCount,
                        storageSpaceInfo = storageSpaceInfo,
                        activeCount = activeDownloads.size,
                        activeSpeedLabel = when {
                            aggregateActiveSpeed > 0L -> FastDownloadEngine.formatSpeed(aggregateActiveSpeed)
                            activeStreamingDownloads.isNotEmpty() -> {
                                if (maxActiveChunks > 1) "${maxActiveChunks}× Turbo" else "Connecting"
                            }
                            parallelDownloadingEnabled -> "8× Turbo"
                            else -> "1× Stream"
                        }
                    )
                }

                // 2. Squared Search Bar + Category Filter Chips
                item(key = "downloads_search_and_filters") {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(14.dp)
                        ) {
                            // Squared Search Box (16.dp radius)
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                border = BorderStroke(
                                    width = if (searchQuery.isNotEmpty()) 1.5.dp else 1.dp,
                                    color = if (searchQuery.isNotEmpty()) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 14.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Box(
                                        contentAlignment = Alignment.CenterStart,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = stringResource(R.string.downloads_search_hint),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        BasicTextField(
                                            value = searchQuery,
                                            onValueChange = { searchQuery = it },
                                            singleLine = true,
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("downloads_search_input")
                                        )
                                    }
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { searchQuery = "" },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = stringResource(R.string.cd_clear_input),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Category Filter Chips
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                DownloadFilterTab.entries.forEach { tab ->
                                    val selected = selectedFilter == tab
                                    FilterChip(
                                        selected = selected,
                                        onClick = { selectedFilter = tab },
                                        label = { Text(tab.label) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (selected) {
                                                    Icons.Filled.Check
                                                } else {
                                                    when (tab) {
                                                        DownloadFilterTab.ALL -> Icons.Outlined.Download
                                                        DownloadFilterTab.ACTIVE -> Icons.Filled.Bolt
                                                        DownloadFilterTab.DOCUMENTS -> Icons.Outlined.Description
                                                        DownloadFilterTab.IMAGES -> Icons.Outlined.Image
                                                        DownloadFilterTab.MEDIA -> Icons.Outlined.VideoFile
                                                        DownloadFilterTab.ARCHIVES -> Icons.Outlined.FolderZip
                                                    }
                                                },
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        modifier = Modifier.testTag("downloads_filter_${tab.name.lowercase(Locale.US)}")
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Empty States or Grouped Download Lists
                if (downloads.isEmpty()) {
                    item(key = "downloads_empty_state") {
                        DownloadsEmptyStateCard(
                            onOpenNewDownloadDialog = { showNewDownloadDialog = true }
                        )
                    }
                } else if (filteredDownloads.isEmpty()) {
                    item(key = "downloads_no_matches_state") {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Download,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "No Matching Downloads",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Try clearing your search filter or switching back to All.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    // Section A: Active / Paused Turbo Transfers
                    if (activeGroupItems.isNotEmpty()) {
                        item(key = "downloads_active_section") {
                            val hasTurboTransfer = activeGroupItems.any { it.chunksCount > 1 }
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                MaterialGroupSectionHeader(
                                    icon = Icons.Filled.Bolt,
                                    title = "Active Transfers",
                                    subtitle = if (hasTurboTransfer && parallelDownloadingEnabled) {
                                        "Multi-TCP Work-Stealing Acceleration"
                                    } else {
                                        "Direct Stream Transfer"
                                    },
                                    trailingAction = {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = "${activeGroupItems.size} active",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                )

                                Column(
                                    verticalArrangement = Arrangement.spacedBy(GroupedListSpacing),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    activeGroupItems.forEachIndexed { idx, item ->
                                        key(item.id) {
                                            val shape = groupedListItemShape(
                                                index = idx,
                                                totalCount = activeGroupItems.size
                                            )
                                            DownloadGroupItemCard(
                                                item = item,
                                                shape = shape,
                                                onPrimaryClick = { inspectedDownload = item },
                                                onOpenFile = { openDownloadedItem(item) },
                                                onPauseOrResume = {
                                                    if (item.status == FastDownloadEngine.STATUS_DOWNLOADING) {
                                                        onPauseDownload(item.id)
                                                    } else {
                                                        onResumeDownload(item.id)
                                                    }
                                                },
                                                onRetry = { onRetryDownload(item.id) },
                                                onInspect = { inspectedDownload = item },
                                                onDelete = { onDeleteDownload(item.id) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section B: Completed & Saved Downloads
                    if (historyGroupItems.isNotEmpty()) {
                        item(key = "downloads_completed_section") {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                MaterialGroupSectionHeader(
                                    icon = Icons.Filled.DownloadDone,
                                    title = "Downloaded Files",
                                    subtitle = "Stored locally on device",
                                    trailingAction = {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                                        ) {
                                            Text(
                                                text = "${historyGroupItems.size} ${if (historyGroupItems.size == 1) "file" else "files"}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                )

                                Column(
                                    verticalArrangement = Arrangement.spacedBy(GroupedListSpacing),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    historyGroupItems.forEachIndexed { idx, item ->
                                        key(item.id) {
                                            val shape = groupedListItemShape(
                                                index = idx,
                                                totalCount = historyGroupItems.size
                                            )
                                            val isItemCompleted = item.status == FastDownloadEngine.STATUS_COMPLETED
                                            DownloadGroupItemCard(
                                                item = item,
                                                shape = shape,
                                                onPrimaryClick = {
                                                    if (isItemCompleted) {
                                                        openDownloadedItem(item)
                                                    } else {
                                                        inspectedDownload = item
                                                    }
                                                },
                                                onOpenFile = { openDownloadedItem(item) },
                                                onPauseOrResume = {
                                                    if (item.status == FastDownloadEngine.STATUS_DOWNLOADING) {
                                                        onPauseDownload(item.id)
                                                    } else {
                                                        onResumeDownload(item.id)
                                                    }
                                                },
                                                onRetry = { onRetryDownload(item.id) },
                                                onInspect = { inspectedDownload = item },
                                                onDelete = { onDeleteDownload(item.id) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Download Inspector ModalBottomSheet
    inspectedDownload?.let { item ->
        val latestItem = downloads.firstOrNull { it.id == item.id } ?: item
        DownloadDetailsBottomSheet(
            item = latestItem,
            onOpenFile = {
                val target = latestItem
                inspectedDownload = null
                openDownloadedItem(target)
            },
            onOpenSourceUrl = {
                val targetUrl = latestItem.url
                inspectedDownload = null
                onOpenUrlInBrowser(targetUrl)
                onDismiss()
            },
            onPauseOrResume = {
                if (latestItem.status == FastDownloadEngine.STATUS_DOWNLOADING) {
                    onPauseDownload(latestItem.id)
                } else {
                    onResumeDownload(latestItem.id)
                }
            },
            onRetry = {
                onRetryDownload(latestItem.id)
                inspectedDownload = null
            },
            onCopyUrl = {
                clipboardManager.setText(AnnotatedString(latestItem.url))
                Toast.makeText(context, context.getString(R.string.notice_link_copied), Toast.LENGTH_SHORT).show()
                inspectedDownload = null
            },
            onShareLink = {
                val shareUrl = latestItem.url
                val shareTitle = latestItem.fileName
                inspectedDownload = null
                runCatching {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, shareTitle)
                        putExtra(Intent.EXTRA_TEXT, shareUrl)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share download link"))
                }
            },
            onDelete = {
                val idToRemove = latestItem.id
                inspectedDownload = null
                onDeleteDownload(idToRemove)
            },
            onDismiss = { inspectedDownload = null }
        )
    }

    // Start Fast Download Dialog
    if (showNewDownloadDialog) {
        StartFastDownloadDialog(
            onStartDownload = { url, customFileName ->
                showNewDownloadDialog = false
                onStartNewDownload(url, customFileName)
            },
            onDismiss = { showNewDownloadDialog = false }
        )
    }

    // Clear Downloads Confirmation Dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.CleaningServices,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = "Clear Downloads",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = "Choose whether to clear only completed/failed download records or remove all downloads:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDialog = false
                        onClearAllDownloads()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("downloads_confirm_clear_all")
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    TextButton(
                        onClick = {
                            showClearDialog = false
                            onClearCompletedDownloads()
                        },
                        modifier = Modifier.testTag("downloads_confirm_clear_completed")
                    ) {
                        Text("Completed Only")
                    }
                }
            }
        )
    }
}

@Composable
private fun DownloadsExpressiveSummaryCard(
    completedCount: Int,
    storageSpaceInfo: DownloadStorageHelper.StorageSpaceInfo,
    activeCount: Int,
    activeSpeedLabel: String
) {
    val usedTotalLabel = remember(storageSpaceInfo) {
        DownloadStorageHelper.formatUsedOfTotalLabel(storageSpaceInfo)
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(GroupedListSpacing),
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max)
            .testTag("downloads_summary_card")
    ) {
        DownloadSummaryMetricTile(
            label = stringResource(R.string.downloads_metric_completed),
            value = completedCount.toString(),
            icon = Icons.Filled.DownloadDone,
            shape = RoundedCornerShape(
                topStart = 22.dp,
                bottomStart = 22.dp,
                topEnd = 6.dp,
                bottomEnd = 6.dp
            ),
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.90f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .weight(0.82f)
                .fillMaxHeight()
        )
        DownloadSummaryMetricTile(
            label = null,
            value = usedTotalLabel,
            icon = Icons.Outlined.Storage,
            shape = RoundedCornerShape(6.dp),
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.90f),
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            progressFraction = if (storageSpaceInfo.totalBytes > 0L) storageSpaceInfo.usedFraction else null,
            modifier = Modifier
                .weight(1.38f)
                .fillMaxHeight()
                .testTag("downloads_used_space_tile")
        )
        DownloadSummaryMetricTile(
            label = if (activeCount > 0) "$activeCount Active" else "Engine Speed",
            value = activeSpeedLabel,
            icon = Icons.Outlined.Speed,
            shape = RoundedCornerShape(
                topStart = 6.dp,
                bottomStart = 6.dp,
                topEnd = 22.dp,
                bottomEnd = 22.dp
            ),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.90f),
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier
                .weight(0.95f)
                .fillMaxHeight()
        )
    }
}

@Composable
private fun DownloadSummaryMetricTile(
    label: String?,
    value: String,
    icon: ImageVector,
    shape: androidx.compose.ui.graphics.Shape,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    progressFraction: Float? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = shape,
        color = containerColor,
        border = BorderStroke(
            0.75.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        ),
        modifier = modifier
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 10.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (progressFraction != null) {
                LinearProgressIndicator(
                    progress = { progressFraction },
                    color = contentColor,
                    trackColor = contentColor.copy(alpha = 0.18f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .testTag("downloads_storage_usage_bar")
                )
            }
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadGroupItemCard(
    item: DownloadItemEntity,
    shape: androidx.compose.ui.graphics.Shape,
    onPrimaryClick: () -> Unit,
    onOpenFile: () -> Unit,
    onPauseOrResume: () -> Unit,
    onRetry: () -> Unit,
    onInspect: () -> Unit,
    onDelete: () -> Unit
) {
    val isDownloading = item.status == FastDownloadEngine.STATUS_DOWNLOADING
    val isPaused = item.status == FastDownloadEngine.STATUS_PAUSED
    val isCompleted = item.status == FastDownloadEngine.STATUS_COMPLETED
    val isFailed = item.status == FastDownloadEngine.STATUS_FAILED ||
        item.status == FastDownloadEngine.STATUS_CANCELED

    val progressFraction = remember(item.downloadedBytes, item.totalBytes, isCompleted) {
        when {
            isCompleted -> 1f
            item.totalBytes > 0L ->
                (item.downloadedBytes.toFloat() / item.totalBytes.toFloat()).coerceIn(0.01f, 1f)
            else -> -1f
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = if (progressFraction >= 0f) progressFraction else 0.35f,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "DownloadItemProgress"
    )

    val hostLabel = remember(item.url) {
        item.url.substringAfter("://", item.url)
            .substringBefore('/')
            .removePrefix("www.")
            .ifBlank { "local" }
    }

    val categoryIcon = remember(item.category) {
        when (item.category.lowercase(Locale.US)) {
            "document" -> Icons.Outlined.Description
            "image" -> Icons.Outlined.Image
            "video" -> Icons.Outlined.VideoFile
            "audio" -> Icons.Outlined.AudioFile
            "archive" -> Icons.Outlined.Archive
            else -> Icons.Outlined.InsertDriveFile
        }
    }

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            0.75.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                onClick = onPrimaryClick,
                onLongClick = onInspect
            )
            .testTag("download_item_card_${item.id}")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Expressive Leading Icon or Spinning Morphing Indicator while downloading
                if (isDownloading) {
                    ExpressiveMiniDownloadSpinner(
                        modifier = Modifier.size(44.dp)
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = when {
                            isCompleted -> MaterialTheme.colorScheme.primaryContainer
                            isPaused -> MaterialTheme.colorScheme.secondaryContainer
                            isFailed -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isFailed) Icons.Outlined.ErrorOutline else categoryIcon,
                                contentDescription = null,
                                tint = when {
                                    isCompleted -> MaterialTheme.colorScheme.onPrimaryContainer
                                    isPaused -> MaterialTheme.colorScheme.onSecondaryContainer
                                    isFailed -> MaterialTheme.colorScheme.onErrorContainer
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = item.fileName,
                        style = EmphasizedTypography.activeTabTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val sizeSubtitle = when {
                        isDownloading || isPaused -> {
                            val dlStr = FastDownloadEngine.formatBytes(item.downloadedBytes)
                            val totalStr = if (item.totalBytes > 0L) {
                                FastDownloadEngine.formatBytes(item.totalBytes)
                            } else {
                                "Streaming"
                            }
                            "$dlStr / $totalStr • $hostLabel"
                        }
                        isCompleted -> {
                            val totalStr = FastDownloadEngine.formatBytes(
                                if (item.totalBytes > 0L) item.totalBytes else item.downloadedBytes
                            )
                            "$totalStr • $hostLabel"
                        }
                        item.downloadedBytes > 0L -> {
                            val dlStr = FastDownloadEngine.formatBytes(item.downloadedBytes)
                            val totalStr = if (item.totalBytes > 0L) {
                                " / ${FastDownloadEngine.formatBytes(item.totalBytes)}"
                            } else {
                                ""
                            }
                            "$dlStr$totalStr • ${item.errorMessage ?: "Interrupted"}"
                        }
                        else -> "${item.errorMessage ?: "Canceled"} • $hostLabel"
                    }

                    Text(
                        text = sizeSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isFailed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Expressive Metadata Pills Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        DownloadBadgePill(
                            text = item.category,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (item.chunksCount > 1) {
                            DownloadBadgePill(
                                text = "${item.chunksCount}× Turbo",
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }

                        if (isDownloading && item.speedBytesPerSec > 0L) {
                            DownloadBadgePill(
                                text = FastDownloadEngine.formatSpeed(item.speedBytesPerSec),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else if (isPaused) {
                            DownloadBadgePill(
                                text = "Paused",
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        } else if (isCompleted) {
                            DownloadBadgePill(
                                text = "Completed",
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // Trailing Actions (primary action + inspect/more menu to avoid squeezing text on compact screens)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCompleted) {
                        IconButton(
                            onClick = onOpenFile,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("download_open_${item.id}")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = stringResource(R.string.cd_open_download_item),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else if (isDownloading || isPaused || (isFailed && item.downloadedBytes > 0L)) {
                        IconButton(
                            onClick = onPauseOrResume,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("download_pause_resume_${item.id}")
                        ) {
                            Icon(
                                imageVector = if (isDownloading) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = stringResource(R.string.cd_pause_resume_download),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else if (isFailed) {
                        IconButton(
                            onClick = onRetry,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("download_retry_${item.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Retry download",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onInspect,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("download_inspect_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.cd_inspect_download_item),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            // Expressive Progress Bar for active, paused, or resumable interrupted transfers
            AnimatedVisibility(
                visible = isDownloading || isPaused || (isFailed && item.downloadedBytes > 0L),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (progressFraction >= 0f) {
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            color = when {
                                isFailed -> MaterialTheme.colorScheme.error
                                isPaused -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.primary
                            },
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    } else if (isPaused) {
                        LinearProgressIndicator(
                            progress = { 0.15f },
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    } else {
                        LinearProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact Material 3 Expressive Contained Morphing Spinner for active downloads.
 */
@Composable
private fun ExpressiveMiniDownloadSpinner(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ExpressiveDownloadSpinner")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SpinAngle"
    )
    val morphPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "MorphPhase"
    )

    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val shapeColor = MaterialTheme.colorScheme.primary
    val iconColor = MaterialTheme.colorScheme.onPrimaryContainer
    val path = remember { Path() }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        modifier = modifier
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.size(30.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val maxR = size.minDimension / 2f
                val idx = morphPhase.toInt() % 5
                val nextIdx = (idx + 1) % 5
                val rawT = morphPhase - morphPhase.toInt()
                val t = rawT * rawT * (3f - 2f * rawT)

                path.reset()
                val steps = 60
                for (i in 0..steps) {
                    val theta = (i.toFloat() / steps.toFloat()) * (2f * PI.toFloat())
                    val r1 = evalMiniMorphRadius(idx, theta)
                    val r2 = evalMiniMorphRadius(nextIdx, theta)
                    val r = maxR * (r1 + (r2 - r1) * t)
                    val x = cx + r * cos(theta)
                    val y = cy + r * sin(theta)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                rotate(degrees = spinAngle) {
                    drawPath(path = path, color = shapeColor.copy(alpha = 0.28f))
                }
            }
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun evalMiniMorphRadius(shapeIndex: Int, theta: Float): Float {
    return when (shapeIndex) {
        0 -> 0.84f + 0.16f * cos(8f * theta)
        1 -> 0.78f + 0.22f * cos(4f * theta)
        2 -> 0.80f + 0.20f * cos(6f * theta)
        3 -> 0.85f + 0.15f * cos(5f * theta)
        else -> 0.81f + 0.19f * cos(3f * theta)
    }
}

@Composable
private fun DownloadBadgePill(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun DownloadsEmptyStateCard(
    onOpenNewDownloadDialog: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("downloads_empty_state_card")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            Text(
                text = stringResource(R.string.downloads_empty_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.downloads_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onOpenNewDownloadDialog,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("downloads_empty_start_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Bolt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Download from URL")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadDetailsBottomSheet(
    item: DownloadItemEntity,
    onOpenFile: () -> Unit,
    onOpenSourceUrl: () -> Unit,
    onPauseOrResume: () -> Unit,
    onRetry: () -> Unit,
    onCopyUrl: () -> Unit,
    onShareLink: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val timestampLabel = remember(item.createdAt) {
        SimpleDateFormat("EEE, MMM d, yyyy • h:mm:ss a", Locale.getDefault()).format(Date(item.createdAt))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
        ) {
            // File Metadata Summary Card
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = item.fileName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = item.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    HorizontalDivider(
                        thickness = 0.6.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    DownloadMetaRow(label = "Status", value = item.status)
                    DownloadMetaRow(
                        label = "Size",
                        value = FastDownloadEngine.formatBytes(
                            if (item.totalBytes > 0L) item.totalBytes else item.downloadedBytes
                        )
                    )
                    DownloadMetaRow(label = "MIME Type", value = item.mimeType)
                    DownloadMetaRow(
                        label = "Acceleration",
                        value = if (item.chunksCount > 1) {
                            "${item.chunksCount}× Parallel Streams"
                        } else {
                            "1× Standard Stream"
                        }
                    )
                    DownloadMetaRow(label = "Started", value = timestampLabel)
                }
            }

            // Grouped Actions List
            val isCompleted = item.status == FastDownloadEngine.STATUS_COMPLETED
            val canPauseOrResume = item.status == FastDownloadEngine.STATUS_DOWNLOADING ||
                item.status == FastDownloadEngine.STATUS_PAUSED ||
                (item.status == FastDownloadEngine.STATUS_FAILED && item.downloadedBytes > 0L)
            val totalActions = 5 + (if (isCompleted) 1 else 0) + (if (canPauseOrResume) 1 else 0)
            var actionIndex = 0

            Column(
                verticalArrangement = Arrangement.spacedBy(GroupedListSpacing),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isCompleted) {
                    val isAppPackage = item.fileName.endsWith(".apk", ignoreCase = true) ||
                        item.mimeType.equals("application/vnd.android.package-archive", ignoreCase = true)
                    MaterialGroupListItem(
                        index = actionIndex++,
                        totalCount = totalActions,
                        title = stringResource(
                            if (isAppPackage) {
                                R.string.downloads_open_app_action
                            } else {
                                R.string.downloads_open_file_action
                            }
                        ),
                        subtitle = stringResource(R.string.downloads_open_file_subtitle),
                        onClick = onOpenFile,
                        leadingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier.testTag("downloads_sheet_open_file_action")
                    )
                }

                if (canPauseOrResume) {
                    MaterialGroupListItem(
                        index = actionIndex++,
                        totalCount = totalActions,
                        title = if (item.status == FastDownloadEngine.STATUS_DOWNLOADING) "Pause Download" else "Resume Download",
                        subtitle = if (item.downloadedBytes > 0L && item.status != FastDownloadEngine.STATUS_DOWNLOADING) {
                            "Continue from ${FastDownloadEngine.formatBytes(item.downloadedBytes)}"
                        } else {
                            "Supports HTTP Range byte-offset continuation"
                        },
                        onClick = {
                            onPauseOrResume()
                            onDismiss()
                        },
                        leadingContent = {
                            Icon(
                                imageVector = if (item.status == FastDownloadEngine.STATUS_DOWNLOADING) {
                                    Icons.Outlined.PauseCircle
                                } else {
                                    Icons.Filled.PlayArrow
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                }

                MaterialGroupListItem(
                    index = actionIndex++,
                    totalCount = totalActions,
                    title = "Download Again / Retry",
                    subtitle = "Accelerate transfer with ${maxOf(item.chunksCount, FastDownloadEngine.DEFAULT_TURBO_CHUNKS)}× parallel streams",
                    onClick = onRetry,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )

                MaterialGroupListItem(
                    index = actionIndex++,
                    totalCount = totalActions,
                    title = "Open Source URL in Tab",
                    subtitle = "Navigate to the download origin in browser",
                    onClick = onOpenSourceUrl,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.OpenInBrowser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )

                MaterialGroupListItem(
                    index = actionIndex++,
                    totalCount = totalActions,
                    title = "Copy Download Link",
                    subtitle = "Copy direct file URL to clipboard",
                    onClick = onCopyUrl,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )

                MaterialGroupListItem(
                    index = actionIndex++,
                    totalCount = totalActions,
                    title = "Share Download Link",
                    subtitle = "Send file link to another app",
                    onClick = onShareLink,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )

                MaterialGroupListItem(
                    index = actionIndex,
                    totalCount = totalActions,
                    title = "Delete Download",
                    subtitle = "Remove record and local file from device",
                    onClick = onDelete,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun DownloadMetaRow(
    label: String,
    value: String
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun StartFastDownloadDialog(
    onStartDownload: (url: String, customFileName: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var urlInput by rememberSaveable { mutableStateOf("") }
    var fileNameInput by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = stringResource(R.string.downloads_new_dialog_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.downloads_new_dialog_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("Direct File URL") },
                    placeholder = { Text(stringResource(R.string.downloads_url_input_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("downloads_dialog_url_input")
                )

                OutlinedTextField(
                    value = fileNameInput,
                    onValueChange = { fileNameInput = it },
                    label = { Text("File Name (Optional)") },
                    placeholder = { Text(stringResource(R.string.downloads_filename_input_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("downloads_dialog_filename_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedUrl = urlInput.trim()
                    if (trimmedUrl.isNotEmpty()) {
                        val finalUrl = if (!trimmedUrl.startsWith("http://", true) &&
                            !trimmedUrl.startsWith("https://", true) &&
                            !trimmedUrl.startsWith("data:", true)
                        ) {
                            "https://$trimmedUrl"
                        } else {
                            trimmedUrl
                        }
                        onStartDownload(finalUrl, fileNameInput.trim().takeIf { it.isNotEmpty() })
                    }
                },
                enabled = urlInput.trim().isNotEmpty(),
                modifier = Modifier.testTag("downloads_dialog_confirm_button")
            ) {
                Text(stringResource(R.string.downloads_start_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
