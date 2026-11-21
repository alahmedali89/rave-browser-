package com.example.ui.browser

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
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
import com.example.data.local.BrowsingHistoryEntity
import com.example.ui.theme.EmphasizedTypography
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class HistoryFilterMode(val label: String) {
    ALL("All Visits"),
    TODAY("Today"),
    FREQUENT("Frequent"),
    SHIELDED("Shielded")
}

enum class HistoryClearRange(val label: String, val subtitle: String, val durationMillis: Long?) {
    LAST_HOUR("Last hour", "Remove pages visited in the past 60 minutes", 3600_000L),
    LAST_24_HOURS("Last 24 hours", "Remove pages visited since yesterday", 24 * 3600_000L),
    LAST_7_DAYS("Last 7 days", "Remove pages visited in the past week", 7 * 24 * 3600_000L),
    ALL_TIME("All time", "Remove your entire browsing history log", null)
}

private data class HistoryDateGroup(
    val key: String,
    val title: String,
    val subtitle: String,
    val entries: List<BrowsingHistoryEntity>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreenOverlay(
    historyItems: List<BrowsingHistoryEntity>,
    onSelectHistoryUrl: (String) -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onOpenInPrivateTab: (String) -> Unit,
    onBookmarkPage: (String, String) -> Unit,
    onDeleteHistoryItem: (Long) -> Unit,
    onClearHistoryRange: (Long?) -> Unit,
    onDismiss: () -> Unit,
    backHandlerEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = backHandlerEnabled, onBack = onDismiss)

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var filterMode by rememberSaveable { mutableStateOf(HistoryFilterMode.ALL) }
    var selectedHostFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var inspectedEntry by remember { mutableStateOf<BrowsingHistoryEntity?>(null) }
    var showClearRangeDialog by remember { mutableStateOf(false) }

    // Summary analytics computed from history
    val totalVisitsSum = remember(historyItems) {
        historyItems.sumOf { it.visitCount.coerceAtLeast(1) }
    }
    val uniqueSitesCount = remember(historyItems) {
        historyItems.map { it.host.ifBlank { it.url } }.distinct().size
    }
    val totalTrackersBlockedInHistory = remember(historyItems) {
        historyItems.sumOf { it.trackersBlockedOnVisit }
    }
    val topDomains = remember(historyItems) {
        historyItems
            .filter { it.host.isNotBlank() }
            .groupBy { it.host }
            .entries
            .sortedByDescending { (_, list) -> list.sumOf { it.visitCount } }
            .take(8)
            .map { (host, list) -> host to list.sumOf { it.visitCount } }
    }

    val startOfTodayMillis = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val filteredHistory = remember(historyItems, searchQuery, filterMode, selectedHostFilter, startOfTodayMillis) {
        val q = searchQuery.trim().lowercase()
        historyItems.filter { item ->
            val matchesQuery = q.isEmpty() ||
                item.title.lowercase().contains(q) ||
                item.url.lowercase().contains(q) ||
                item.host.lowercase().contains(q)

            val matchesMode = when (filterMode) {
                HistoryFilterMode.ALL -> true
                HistoryFilterMode.TODAY -> item.visitedAt >= startOfTodayMillis
                HistoryFilterMode.FREQUENT -> item.visitCount >= 2
                HistoryFilterMode.SHIELDED -> item.trackersBlockedOnVisit > 0 || item.isHttps
            }

            val matchesHost = selectedHostFilter == null || item.host.equals(selectedHostFilter, ignoreCase = true)
            matchesQuery && matchesMode && matchesHost
        }.let { list ->
            if (filterMode == HistoryFilterMode.FREQUENT) {
                list.sortedWith(compareByDescending<BrowsingHistoryEntity> { it.visitCount }.thenByDescending { it.visitedAt })
            } else {
                list
            }
        }
    }

    val dateGroups = remember(filteredHistory, startOfTodayMillis) {
        groupHistoryByDateSection(filteredHistory, startOfTodayMillis)
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
                            .testTag("history_back_button")
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
                            text = stringResource(R.string.history_screen_title),
                            style = EmphasizedTypography.headlineHero
                        )
                        if (historyItems.isNotEmpty()) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = historyItems.size.toString(),
                                    style = EmphasizedTypography.badgeCounter,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (historyItems.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(
                                    role = Role.Button,
                                    onClick = { showClearRangeDialog = true }
                                )
                                .testTag("history_clear_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteSweep,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = stringResource(R.string.history_clear_button),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            )
        },
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .testTag("history_screen")
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 780.dp)
                    .testTag("history_scroll_list")
            ) {
                // 1. Material 3 Expressive Activity Summary Dashboard
                if (historyItems.isNotEmpty()) {
                    item(key = "history_summary_dashboard") {
                        HistoryExpressiveSummaryCard(
                            totalVisits = totalVisitsSum,
                            uniqueSites = uniqueSitesCount,
                            trackersBlocked = totalTrackersBlockedInHistory
                        )
                    }
                }

                // 2. Squared Search Bar + Expressive Filter Chips + Domain Filter Pills
                if (historyItems.isNotEmpty()) {
                    item(key = "history_search_and_filters") {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
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
                                                text = stringResource(R.string.history_search_hint),
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
                                            textStyle = EmphasizedTypography.addressBarActive.copy(
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("history_search_input")
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

                            // Filter Mode Chips
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                HistoryFilterMode.entries.forEach { mode ->
                                    val selected = filterMode == mode
                                    val modeIcon = when (mode) {
                                        HistoryFilterMode.ALL -> Icons.Outlined.History
                                        HistoryFilterMode.TODAY -> Icons.Outlined.CalendarToday
                                        HistoryFilterMode.FREQUENT -> Icons.Outlined.TrendingUp
                                        HistoryFilterMode.SHIELDED -> Icons.Outlined.Security
                                    }
                                    FilterChip(
                                        selected = selected,
                                        onClick = { filterMode = mode },
                                        label = { Text(mode.label) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (selected) Icons.Filled.Check else modeIcon,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        modifier = Modifier.testTag("history_filter_${mode.name.lowercase()}")
                                    )
                                }
                            }

                            // Top Visited Domain Pills (when 2+ domains exist)
                            if (topDomains.size >= 2) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                ) {
                                    topDomains.forEach { (host, count) ->
                                        val isHostSelected = selectedHostFilter.equals(host, ignoreCase = true)
                                        FilterChip(
                                            selected = isHostSelected,
                                            onClick = {
                                                selectedHostFilter = if (isHostSelected) null else host
                                            },
                                            label = {
                                                Text(
                                                    text = "$host ($count)",
                                                    maxLines = 1
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Outlined.Language,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                                selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Empty States or Date-Grouped Material 3 Expressive Grouped Lists
                if (historyItems.isEmpty()) {
                    item(key = "history_empty_state") {
                        HistoryEmptyStateCard(
                            title = stringResource(R.string.history_empty_title),
                            subtitle = stringResource(R.string.history_empty_subtitle)
                        )
                    }
                } else if (filteredHistory.isEmpty()) {
                    item(key = "history_no_matches_state") {
                        HistoryEmptyStateCard(
                            title = "No Matching History",
                            subtitle = "Try clearing your search filter or switching back to All Visits."
                        )
                    }
                } else {
                    dateGroups.forEach { group ->
                        item(key = "section_${group.key}") {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                MaterialGroupSectionHeader(
                                    icon = Icons.Outlined.Schedule,
                                    title = group.title,
                                    subtitle = group.subtitle,
                                    trailingAction = {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                                        ) {
                                            Text(
                                                text = "${group.entries.size} ${if (group.entries.size == 1) "item" else "items"}",
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
                                    group.entries.forEachIndexed { index, entry ->
                                        val itemShape = groupedListItemShape(
                                            index = index,
                                            totalCount = group.entries.size
                                        )
                                        HistoryGroupListItemCard(
                                            entry = entry,
                                            shape = itemShape,
                                            onOpen = {
                                                onSelectHistoryUrl(entry.url)
                                                onDismiss()
                                            },
                                            onInspect = { inspectedEntry = entry },
                                            onDelete = { onDeleteHistoryItem(entry.id) }
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

    // Visit Details & Actions Bottom Sheet
    inspectedEntry?.let { entry ->
        HistoryVisitDetailsBottomSheet(
            entry = entry,
            onOpenCurrentTab = {
                val targetUrl = entry.url
                inspectedEntry = null
                onSelectHistoryUrl(targetUrl)
                onDismiss()
            },
            onOpenNewTab = {
                val targetUrl = entry.url
                inspectedEntry = null
                onOpenInNewTab(targetUrl)
                onDismiss()
            },
            onOpenPrivateTab = {
                val targetUrl = entry.url
                inspectedEntry = null
                onOpenInPrivateTab(targetUrl)
                onDismiss()
            },
            onBookmark = {
                onBookmarkPage(entry.title, entry.url)
                inspectedEntry = null
            },
            onCopyLink = {
                clipboardManager.setText(AnnotatedString(entry.url))
                Toast.makeText(context, context.getString(R.string.notice_link_copied), Toast.LENGTH_SHORT).show()
                inspectedEntry = null
            },
            onShareLink = {
                val shareUrl = entry.url
                val shareTitle = entry.title
                inspectedEntry = null
                runCatching {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, shareTitle)
                        putExtra(Intent.EXTRA_TEXT, shareUrl)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share link"))
                }
            },
            onDelete = {
                val idToRemove = entry.id
                inspectedEntry = null
                onDeleteHistoryItem(idToRemove)
            },
            onDismiss = { inspectedEntry = null }
        )
    }

    // Time-Range Clear Browsing History Dialog
    if (showClearRangeDialog) {
        ClearHistoryRangeDialog(
            onConfirmRange = { range ->
                showClearRangeDialog = false
                val sinceMillis = range.durationMillis?.let { dur ->
                    System.currentTimeMillis() - dur
                }
                onClearHistoryRange(sinceMillis)
            },
            onDismiss = { showClearRangeDialog = false }
        )
    }
}

@Composable
private fun HistoryExpressiveSummaryCard(
    totalVisits: Int,
    uniqueSites: Int,
    trackersBlocked: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(GroupedListSpacing),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_summary_card")
    ) {
        SummaryMetricTile(
            label = "Total Visits",
            value = totalVisits.toString(),
            icon = Icons.Filled.History,
            shape = RoundedCornerShape(
                topStart = 22.dp,
                bottomStart = 22.dp,
                topEnd = 6.dp,
                bottomEnd = 6.dp
            ),
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.90f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f)
        )
        SummaryMetricTile(
            label = "Unique Sites",
            value = uniqueSites.toString(),
            icon = Icons.Outlined.Language,
            shape = RoundedCornerShape(6.dp),
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.90f),
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f)
        )
        SummaryMetricTile(
            label = "Ads & Trackers",
            value = trackersBlocked.toString(),
            icon = Icons.Outlined.Security,
            shape = RoundedCornerShape(
                topStart = 6.dp,
                bottomStart = 6.dp,
                topEnd = 22.dp,
                bottomEnd = 22.dp
            ),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.90f),
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SummaryMetricTile(
    label: String,
    value: String,
    icon: ImageVector,
    shape: androidx.compose.ui.graphics.Shape,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
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
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = contentColor,
                    maxLines = 1
                )
            }
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

@Composable
private fun HistoryGroupListItemCard(
    entry: BrowsingHistoryEntity,
    shape: androidx.compose.ui.graphics.Shape,
    onOpen: () -> Unit,
    onInspect: () -> Unit,
    onDelete: () -> Unit
) {
    val monogram = remember(entry.host, entry.title) {
        entry.host.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString()
            ?: entry.title.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString()
            ?: "W"
    }
    val timeLabel = remember(entry.visitedAt) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(entry.visitedAt))
    }

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            0.75.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onOpen)
            .testTag("history_visit_row_${entry.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 74.dp)
                .padding(start = 14.dp, end = 6.dp, top = 11.dp, bottom = 11.dp)
        ) {
            // Leading Domain Monogram Badge with HTTPS indicator
            Box(contentAlignment = Alignment.BottomEnd) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = monogram,
                            style = EmphasizedTypography.activeTabTitle,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                if (entry.isHttps) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = entry.title,
                    style = EmphasizedTypography.activeTabTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = entry.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Detailed Metadata Badges Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    HistoryMetaPill(
                        text = timeLabel,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (entry.host.isNotBlank()) {
                        HistoryMetaPill(
                            text = entry.host,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    if (entry.visitCount > 1) {
                        HistoryMetaPill(
                            text = "${entry.visitCount}×",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    if (entry.trackersBlockedOnVisit > 0) {
                        HistoryMetaPill(
                            text = "${entry.trackersBlockedOnVisit} blocked",
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onInspect,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("history_inspect_button_${entry.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.cd_inspect_history_visit),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("history_delete_button_${entry.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(R.string.cd_delete_history_visit),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryMetaPill(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryVisitDetailsBottomSheet(
    entry: BrowsingHistoryEntity,
    onOpenCurrentTab: () -> Unit,
    onOpenNewTab: () -> Unit,
    onOpenPrivateTab: () -> Unit,
    onBookmark: () -> Unit,
    onCopyLink: () -> Unit,
    onShareLink: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val fullTimestamp = remember(entry.visitedAt) {
        SimpleDateFormat("EEE, MMM d, yyyy • h:mm:ss a", Locale.getDefault()).format(Date(entry.visitedAt))
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
            // Visit Summary Card
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
                        text = entry.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = entry.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    HorizontalDivider(
                        thickness = 0.6.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Visited",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = fullTimestamp,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Visit Frequency",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${entry.visitCount} ${if (entry.visitCount == 1) "visit" else "visits"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Security & Shields",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${if (entry.isHttps) "HTTPS Encrypted" else "Standard HTTP"} • ${entry.trackersBlockedOnVisit} blocked",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Grouped Actions List
            Column(
                verticalArrangement = Arrangement.spacedBy(GroupedListSpacing),
                modifier = Modifier.fillMaxWidth()
            ) {
                HistoryDetailActionRow(
                    index = 0,
                    totalCount = 7,
                    icon = Icons.Outlined.OpenInBrowser,
                    title = "Open in Current Tab",
                    onClick = onOpenCurrentTab
                )
                HistoryDetailActionRow(
                    index = 1,
                    totalCount = 7,
                    icon = Icons.Outlined.OpenInNew,
                    title = "Open in New Tab",
                    onClick = onOpenNewTab
                )
                HistoryDetailActionRow(
                    index = 2,
                    totalCount = 7,
                    icon = Icons.Outlined.VisibilityOff,
                    title = "Open in Private Tab",
                    onClick = onOpenPrivateTab
                )
                HistoryDetailActionRow(
                    index = 3,
                    totalCount = 7,
                    icon = Icons.Outlined.BookmarkAdd,
                    title = "Bookmark Page",
                    onClick = onBookmark
                )
                HistoryDetailActionRow(
                    index = 4,
                    totalCount = 7,
                    icon = Icons.Outlined.ContentCopy,
                    title = "Copy Link Address",
                    onClick = onCopyLink
                )
                HistoryDetailActionRow(
                    index = 5,
                    totalCount = 7,
                    icon = Icons.Outlined.Share,
                    title = "Share Link",
                    onClick = onShareLink
                )
                HistoryDetailActionRow(
                    index = 6,
                    totalCount = 7,
                    icon = Icons.Outlined.DeleteOutline,
                    title = "Remove from History",
                    isDestructive = true,
                    onClick = onDelete
                )
            }
        }
    }
}

@Composable
private fun HistoryDetailActionRow(
    index: Int,
    totalCount: Int,
    icon: ImageVector,
    title: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    MaterialGroupListItem(
        index = index,
        totalCount = totalCount,
        title = title,
        subtitle = null,
        role = Role.Button,
        onClick = onClick,
        leadingContent = {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDestructive) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isDestructive) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(19.dp)
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
        }
    )
}

@Composable
private fun ClearHistoryRangeDialog(
    onConfirmRange: (HistoryClearRange) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedRange by remember { mutableStateOf(HistoryClearRange.ALL_TIME) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.history_clear_dialog_title),
                style = EmphasizedTypography.headlineHero
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.history_clear_dialog_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(GroupedListSpacing),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HistoryClearRange.entries.forEachIndexed { index, range ->
                        val selected = selectedRange == range
                        val shape = groupedListItemShape(
                            index = index,
                            totalCount = HistoryClearRange.entries.size,
                            outerRadius = 18.dp,
                            innerRadius = 4.dp
                        )
                        Surface(
                            shape = shape,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .clickable(
                                    role = Role.RadioButton,
                                    onClick = { selectedRange = range }
                                )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = range.label,
                                        style = EmphasizedTypography.activeTabTitle,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = range.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                RadioButton(
                                    selected = selected,
                                    onClick = null
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmRange(selectedRange) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.testTag("history_confirm_clear_button")
            ) {
                Text("Clear Data")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun HistoryEmptyStateCard(
    title: String,
    subtitle: String
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 44.dp)
    ) {
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(60.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = title,
                    style = EmphasizedTypography.activeTabTitle,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun groupHistoryByDateSection(
    entries: List<BrowsingHistoryEntity>,
    startOfTodayMillis: Long
): List<HistoryDateGroup> {
    if (entries.isEmpty()) return emptyList()

    val startOfYesterdayMillis = startOfTodayMillis - 24 * 3600_000L
    val startOfWeekMillis = startOfTodayMillis - 7 * 24 * 3600_000L
    val dateFormatter = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())

    val todayList = mutableListOf<BrowsingHistoryEntity>()
    val yesterdayList = mutableListOf<BrowsingHistoryEntity>()
    val thisWeekList = mutableListOf<BrowsingHistoryEntity>()
    val earlierList = mutableListOf<BrowsingHistoryEntity>()

    for (entry in entries) {
        when {
            entry.visitedAt >= startOfTodayMillis -> todayList.add(entry)
            entry.visitedAt >= startOfYesterdayMillis -> yesterdayList.add(entry)
            entry.visitedAt >= startOfWeekMillis -> thisWeekList.add(entry)
            else -> earlierList.add(entry)
        }
    }

    return buildList {
        if (todayList.isNotEmpty()) {
            add(
                HistoryDateGroup(
                    key = "today",
                    title = "Today",
                    subtitle = dateFormatter.format(Date(startOfTodayMillis)),
                    entries = todayList
                )
            )
        }
        if (yesterdayList.isNotEmpty()) {
            add(
                HistoryDateGroup(
                    key = "yesterday",
                    title = "Yesterday",
                    subtitle = dateFormatter.format(Date(startOfYesterdayMillis)),
                    entries = yesterdayList
                )
            )
        }
        if (thisWeekList.isNotEmpty()) {
            add(
                HistoryDateGroup(
                    key = "this_week",
                    title = "Last 7 Days",
                    subtitle = "Recent activity from earlier this week",
                    entries = thisWeekList
                )
            )
        }
        if (earlierList.isNotEmpty()) {
            add(
                HistoryDateGroup(
                    key = "earlier",
                    title = "Earlier",
                    subtitle = "Older browsing activity",
                    entries = earlierList
                )
            )
        }
    }
}
