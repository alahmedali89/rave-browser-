package com.example.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import android.graphics.Bitmap
import com.example.R
import com.example.data.local.TopVisitEntity
import com.example.data.preferences.BrowserPreferences
import com.example.engine.HomeWallpaperData
import com.example.ui.theme.ApexMotion
import com.example.ui.theme.EmphasizedTypography
import com.example.ui.theme.rememberMorphingCornerShape
import com.example.ui.theme.resolveTonalColors
import com.example.ui.viewmodel.BrowserTab
import com.example.ui.viewmodel.TabGridEntry
import kotlin.math.roundToInt

private enum class TabSwitcherFilter {
    ALL_TABS,
    GROUPS_ONLY,
    INCOGNITO_ONLY
}

/**
 * Horizontal Tab Strip for tablets & landscape widths using expressive corner morphing.
 */
@Composable
fun ExpressiveHorizontalTabStrip(
    tabs: List<BrowserTab>,
    activeTabId: String,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("horizontal_tab_strip")
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(tabs, key = { it.id }) { tab ->
                val isSelected = tab.id == activeTabId
                val morphShape = rememberMorphingCornerShape(
                    targetRadius = if (isSelected) 24.dp else 12.dp,
                    label = "TabStripMorph_${tab.id}"
                )
                val tabSurfaceColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    animationSpec = ApexMotion.effectsSpec(),
                    label = "TabStripColor_${tab.id}"
                )
                val tabContentColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = ApexMotion.effectsSpec(),
                    label = "TabStripContentColor_${tab.id}"
                )

                Surface(
                    shape = morphShape,
                    color = tabSurfaceColor,
                    tonalElevation = if (isSelected) 4.dp else 1.dp,
                    border = if (isSelected) {
                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        null
                    },
                    modifier = Modifier
                        .height(42.dp)
                        .clickable { onSelectTab(tab.id) }
                        .testTag("tab_chip_${tab.id}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 14.dp, end = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (tab.isHomePage) Icons.Outlined.Home else Icons.Filled.Public,
                            contentDescription = null,
                            tint = tabContentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = tab.title,
                            style = if (isSelected) {
                                EmphasizedTypography.activeTabTitle
                            } else {
                                MaterialTheme.typography.labelLarge
                            },
                            color = tabContentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 150.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { onCloseTab(tab.id) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.cd_close_tab),
                                tint = tabContentColor,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = rememberMorphingCornerShape(targetRadius = 20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clickable(onClick = onNewTab)
                        .testTag("tab_strip_new_tab_button")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.fab_new_tab),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

/**
 * Full-screen Tab & Group Switcher (no Bottom Navigation Bar):
 * - Material 3 CenterAlignedTopAppBar featuring a centered `Tabs | Groups` segmented pill and shredder reset action.
 * - Floating squarish Add (+) button at BottomCenter and 3-dots overflow menu on the right in the bottom area.
 * - Draggable 2-column grid supporting drag-and-drop group creation and 2x2 group card previews.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraggableTabGridOverlay(
    gridEntries: List<TabGridEntry>,
    totalOpenTabs: Int,
    activeTabId: String,
    expandedGroupId: String?,
    isIncognitoAvailable: Boolean = false,
    isActiveTabIncognito: Boolean = false,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onCloseGroup: (String) -> Unit,
    onEditGroup: (String) -> Unit,
    onMergeEntries: (String, String) -> Unit,
    onMoveGroupedTabToTarget: (String, String?) -> Unit,
    onOpenGroup: (String?) -> Unit,
    onAddTabToGroup: (String) -> Unit,
    onUngroupTab: (String) -> Unit,
    onCycleTabColor: (String) -> Unit,
    onNewTab: () -> Unit,
    onNewIncognitoTab: () -> Unit = {},
    onCreateGroup: () -> Unit,
    onCreateIncognitoGroup: (() -> Unit)? = null,
    onResetAllTabs: () -> Unit,
    onCloseAllIncognitoTabs: () -> Unit = {},
    onClearBrowsingData: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onCloseSelectedTabs: (Set<String>) -> Unit = {},
    onGroupSelectedTabs: (Set<String>) -> Unit = {},
    topVisits: List<TopVisitEntity> = emptyList(),
    preferences: BrowserPreferences = BrowserPreferences(),
    homeWallpapers: Map<String, HomeWallpaperData> = emptyMap(),
    customWallpaperBitmap: Bitmap? = null,
    onDismiss: () -> Unit,
    backHandlerEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var isSelectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedTabIds by remember { mutableStateOf(setOf<String>()) }
    var isMoreMenuExpanded by remember { mutableStateOf(false) }

    BackHandler(enabled = backHandlerEnabled) {
        when {
            isSelectionMode -> {
                isSelectionMode = false
                selectedTabIds = emptySet()
            }
            expandedGroupId != null -> {
                onOpenGroup(null)
            }
            else -> {
                onDismiss()
            }
        }
    }

    var switcherFilter by rememberSaveable {
        mutableStateOf(
            if (isActiveTabIncognito && isIncognitoAvailable) {
                TabSwitcherFilter.INCOGNITO_ONLY
            } else {
                TabSwitcherFilter.ALL_TABS
            }
        )
    }

    val groupEntries = remember(gridEntries) {
        gridEntries.filterIsInstance<TabGridEntry.GroupTab>()
    }
    val regularEntries = remember(gridEntries) {
        gridEntries.mapNotNull { entry ->
            when (entry) {
                is TabGridEntry.SingleTab -> if (!entry.tab.isPrivate) entry else null
                is TabGridEntry.GroupTab -> {
                    val regularMembers = entry.tabs.filter { !it.isPrivate }
                    if (regularMembers.isNotEmpty()) entry.copy(tabs = regularMembers) else null
                }
            }
        }
    }
    val incognitoEntries = remember(gridEntries) {
        gridEntries.mapNotNull { entry ->
            when (entry) {
                is TabGridEntry.SingleTab -> if (entry.tab.isPrivate) entry else null
                is TabGridEntry.GroupTab -> {
                    val privateMembers = entry.tabs.filter { it.isPrivate }
                    if (privateMembers.isNotEmpty()) entry.copy(tabs = privateMembers) else null
                }
            }
        }
    }
    val totalIncognitoCount = remember(gridEntries) {
        gridEntries.sumOf { entry ->
            when (entry) {
                is TabGridEntry.SingleTab -> if (entry.tab.isPrivate) 1 else 0
                is TabGridEntry.GroupTab -> entry.tabs.count { it.isPrivate }
            }
        }
    }
    val totalRegularCount = (totalOpenTabs - totalIncognitoCount).coerceAtLeast(0)
    val showIncognitoSegment = isIncognitoAvailable || totalIncognitoCount > 0

    LaunchedEffect(isActiveTabIncognito, showIncognitoSegment) {
        if (isActiveTabIncognito && showIncognitoSegment) {
            switcherFilter = TabSwitcherFilter.INCOGNITO_ONLY
        } else if (!showIncognitoSegment && switcherFilter == TabSwitcherFilter.INCOGNITO_ONLY) {
            switcherFilter = TabSwitcherFilter.ALL_TABS
        }
    }

    val visibleEntries = remember(regularEntries, groupEntries, incognitoEntries, switcherFilter) {
        when (switcherFilter) {
            TabSwitcherFilter.ALL_TABS -> regularEntries
            TabSwitcherFilter.GROUPS_ONLY -> groupEntries
            TabSwitcherFilter.INCOGNITO_ONLY -> incognitoEntries
        }
    }

    LaunchedEffect(switcherFilter) {
        isSelectionMode = false
        selectedTabIds = emptySet()
    }

    LaunchedEffect(gridEntries) {
        val allExistingTabIds = gridEntries.flatMapTo(mutableSetOf()) { entry ->
            when (entry) {
                is TabGridEntry.SingleTab -> listOf(entry.tab.id)
                is TabGridEntry.GroupTab -> entry.tabs.map { it.id }
            }
        }
        val pruned = selectedTabIds.intersect(allExistingTabIds)
        if (pruned != selectedTabIds) {
            selectedTabIds = pruned
        }
    }

    LaunchedEffect(backHandlerEnabled) {
        if (!backHandlerEnabled) {
            isSelectionMode = false
            selectedTabIds = emptySet()
            isMoreMenuExpanded = false
        }
    }

    val itemBounds = remember { mutableStateMapOf<String, Rect>() }
    var rootPositionInRoot by remember { mutableStateOf(Offset.Zero) }
    var ungroupDropBannerBounds by remember { mutableStateOf<Rect?>(null) }

    // Whole-card dragging (single tab or group card)
    var draggedKey by remember { mutableStateOf<String?>(null) }
    var dragTranslation by remember { mutableStateOf(Offset.Zero) }
    var hoveredTargetKey by remember { mutableStateOf<String?>(null) }

    // Dragging an individual member tab out of a GroupTabGridCard
    var draggedMemberTab by remember { mutableStateOf<BrowserTab?>(null) }
    var draggedMemberStartRect by remember { mutableStateOf<Rect?>(null) }
    var draggedMemberOffset by remember { mutableStateOf(Offset.Zero) }

    fun updateHoverTarget(key: String, deltaTotal: Offset) {
        val originRect = itemBounds[key] ?: return
        val currentCenter = originRect.center + deltaTotal
        val hitKey = itemBounds.entries.firstOrNull { (otherKey, rect) ->
            otherKey != key && rect.contains(currentCenter)
        }?.key
        hoveredTargetKey = hitKey
    }

    fun finishDrag() {
        val from = draggedKey
        val to = hoveredTargetKey
        if (from != null && to != null && from != to) {
            onMergeEntries(from, to)
        }
        draggedKey = null
        dragTranslation = Offset.Zero
        hoveredTargetKey = null
    }

    fun updateMemberHoverTarget(memberTab: BrowserTab, startRect: Rect, deltaTotal: Offset) {
        val currentCenter = startRect.center + deltaTotal
        val sourceGroupKey = memberTab.groupId?.let { "group_$it" }
        val inUngroupBanner = ungroupDropBannerBounds?.contains(currentCenter) == true
        if (inUngroupBanner) {
            hoveredTargetKey = null
            return
        }
        val hitOtherKey = itemBounds.entries.firstOrNull { (otherKey, rect) ->
            otherKey != sourceGroupKey && rect.contains(currentCenter)
        }?.key
        hoveredTargetKey = hitOtherKey
    }

    fun finishMemberDrag() {
        val member = draggedMemberTab
        val startRect = draggedMemberStartRect
        if (member != null && startRect != null) {
            val dropCenter = startRect.center + draggedMemberOffset
            val sourceGroupKey = member.groupId?.let { "group_$it" }
            val sourceGroupRect = sourceGroupKey?.let { itemBounds[it] }
            val inUngroupBanner = ungroupDropBannerBounds?.contains(dropCenter) == true

            if (inUngroupBanner) {
                onMoveGroupedTabToTarget(member.id, null)
            } else if (hoveredTargetKey != null && hoveredTargetKey != sourceGroupKey) {
                onMoveGroupedTabToTarget(member.id, hoveredTargetKey)
            } else if (sourceGroupRect == null || !sourceGroupRect.contains(dropCenter)) {
                // Dropped outside the source group card -> remove from group!
                onMoveGroupedTabToTarget(member.id, null)
            }
        }
        draggedMemberTab = null
        draggedMemberStartRect = null
        draggedMemberOffset = Offset.Zero
        hoveredTargetKey = null
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("close_tab_switcher_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                },
                title = {
                    if (isSelectionMode) {
                        Text(
                            text = "${selectedTabIds.size} selected",
                            style = EmphasizedTypography.activeTabTitle,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        CenteredTabsAndGroupsModePill(
                            selectedFilter = switcherFilter,
                            totalTabs = totalRegularCount,
                            showIncognito = showIncognitoSegment,
                            onSelectFilter = { switcherFilter = it }
                        )
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(
                            onClick = {
                                isSelectionMode = false
                                selectedTabIds = emptySet()
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("cancel_select_tabs_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.cancel)
                            )
                        }
                    } else if (switcherFilter == TabSwitcherFilter.INCOGNITO_ONLY && totalIncognitoCount > 0) {
                        IconButton(
                            onClick = onCloseAllIncognitoTabs,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("reset_tabs_button")
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_shredder),
                                contentDescription = stringResource(R.string.incognito_end_session_button),
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onResetAllTabs,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("reset_tabs_button")
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_shredder),
                                contentDescription = stringResource(R.string.cd_reset_all_tabs),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            )
        },
        modifier = modifier
            .fillMaxSize()
            .testTag("draggable_tab_grid_switcher")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .onGloballyPositioned { coords ->
                    rootPositionInRoot = coords.positionInRoot()
                }
        ) {
            if (visibleEntries.isEmpty() && switcherFilter == TabSwitcherFilter.GROUPS_ONLY) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 32.dp)
                ) {
                    Card(
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 460.dp)
                            .testTag("empty_tab_groups_state")
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(54.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.FolderCopy,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.tabs_groups_empty_title),
                                style = EmphasizedTypography.activeTabTitle,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.tabs_groups_empty_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else if (visibleEntries.isEmpty() && switcherFilter == TabSwitcherFilter.INCOGNITO_ONLY) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 32.dp)
                ) {
                    Card(
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 460.dp)
                            .testTag("empty_incognito_tabs_state")
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.size(54.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.VisibilityOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.tabs_incognito_empty_title),
                                style = EmphasizedTypography.activeTabTitle,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.tabs_incognito_empty_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = onNewIncognitoTab,
                                modifier = Modifier.testTag("empty_state_open_incognito_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.VisibilityOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.menu_new_incognito_tab))
                            }
                        }
                    }
                }
            } else if (visibleEntries.isEmpty() && switcherFilter == TabSwitcherFilter.ALL_TABS) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 32.dp)
                ) {
                    Card(
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 460.dp)
                            .testTag("empty_regular_tabs_state")
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(54.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.Layers,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.tabs_all_empty_title),
                                style = EmphasizedTypography.activeTabTitle,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.tabs_all_empty_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = onNewTab,
                                modifier = Modifier.testTag("empty_state_open_regular_tab_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.tabs_menu_new_tab))
                            }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 156.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = if (draggedMemberTab != null) 66.dp else 8.dp,
                        bottom = 120.dp
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("tabs_lazy_grid")
                ) {
                    items(
                        items = visibleEntries,
                        key = { it.gridKey }
                    ) { entry ->
                        val isBeingDragged = draggedKey == entry.gridKey
                        val isDropTarget = hoveredTargetKey == entry.gridKey

                        val cardScale by animateFloatAsState(
                            targetValue = when {
                                isBeingDragged -> 1.06f
                                isDropTarget -> 1.04f
                                else -> 1f
                            },
                            animationSpec = ApexMotion.spatialSpec(),
                            label = "GridCardScale_${entry.gridKey}"
                        )

                        val animatedModifier = if (isBeingDragged) {
                            Modifier.zIndex(25f)
                        } else {
                            Modifier
                                .zIndex(if (isDropTarget) 5f else 0f)
                                .animateItem(placementSpec = ApexMotion.spatialSpring())
                        }

                        Box(
                            modifier = animatedModifier
                                .fillMaxWidth()
                                .onGloballyPositioned { coords ->
                                    if (!isBeingDragged) {
                                        itemBounds[entry.gridKey] = coords.boundsInRoot()
                                    }
                                }
                                .graphicsLayer {
                                    scaleX = cardScale
                                    scaleY = cardScale
                                    if (isBeingDragged) {
                                        translationX = dragTranslation.x
                                        translationY = dragTranslation.y
                                        alpha = 0.94f
                                    }
                                }
                                .let { baseModifier ->
                                    if (!isSelectionMode) {
                                        baseModifier.pointerInput(entry.gridKey) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    draggedKey = entry.gridKey
                                                    dragTranslation = Offset.Zero
                                                    hoveredTargetKey = null
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragTranslation += dragAmount
                                                    updateHoverTarget(entry.gridKey, dragTranslation)
                                                },
                                                onDragEnd = { finishDrag() },
                                                onDragCancel = { finishDrag() }
                                            )
                                        }
                                    } else {
                                        baseModifier
                                    }
                                }
                        ) {
                            when (entry) {
                                is TabGridEntry.SingleTab -> {
                                    val isTabMarkedSelected = entry.tab.id in selectedTabIds
                                    SingleTabGridCard(
                                        tab = entry.tab,
                                        isSelected = if (isSelectionMode) isTabMarkedSelected else entry.tab.id == activeTabId,
                                        isSelectionMode = isSelectionMode,
                                        isDropTarget = isDropTarget,
                                        swipeToDeleteEnabled = !isSelectionMode && !isBeingDragged && draggedKey == null,
                                        topVisits = topVisits,
                                        preferences = preferences,
                                        wallpaperData = homeWallpapers[entry.tab.homeWallpaperSeed],
                                        customWallpaperBitmap = customWallpaperBitmap,
                                        onClick = {
                                            if (isSelectionMode) {
                                                selectedTabIds = if (isTabMarkedSelected) {
                                                    selectedTabIds - entry.tab.id
                                                } else {
                                                    selectedTabIds + entry.tab.id
                                                }
                                            } else {
                                                onSelectTab(entry.tab.id)
                                            }
                                        },
                                        onClose = { onCloseTab(entry.tab.id) },
                                        onCycleTabColor = { onCycleTabColor(entry.tab.id) },
                                        onImmediateDragStart = {
                                            if (!isSelectionMode) {
                                                draggedKey = entry.gridKey
                                                dragTranslation = Offset.Zero
                                                hoveredTargetKey = null
                                            }
                                        },
                                        onImmediateDrag = { delta ->
                                            if (!isSelectionMode) {
                                                dragTranslation += delta
                                                updateHoverTarget(entry.gridKey, dragTranslation)
                                            }
                                        },
                                        onImmediateDragEnd = { finishDrag() }
                                    )
                                }

                                is TabGridEntry.GroupTab -> {
                                    val groupMemberIds = remember(entry.tabs) {
                                        entry.tabs.mapTo(mutableSetOf()) { it.id }
                                    }
                                    val isGroupChecked = groupMemberIds.isNotEmpty() &&
                                        groupMemberIds.all { it in selectedTabIds }

                                    GroupTabGridCard(
                                        entry = entry,
                                        activeTabId = activeTabId,
                                        isSelectionMode = isSelectionMode,
                                        isSelectionChecked = isGroupChecked,
                                        isDropTarget = isDropTarget,
                                        draggedMemberTabId = draggedMemberTab?.id,
                                        onOpenGroup = {
                                            if (isSelectionMode) {
                                                selectedTabIds = if (isGroupChecked) {
                                                    selectedTabIds - groupMemberIds
                                                } else {
                                                    selectedTabIds + groupMemberIds
                                                }
                                            } else {
                                                onOpenGroup(entry.group.id)
                                            }
                                        },
                                        onEditGroup = { onEditGroup(entry.group.id) },
                                        onCloseGroup = { onCloseGroup(entry.group.id) },
                                        onCloseMemberTab = onCloseTab,
                                        onImmediateDragStart = {
                                            if (!isSelectionMode) {
                                                draggedKey = entry.gridKey
                                                dragTranslation = Offset.Zero
                                                hoveredTargetKey = null
                                            }
                                        },
                                        onImmediateDrag = { delta ->
                                            if (!isSelectionMode) {
                                                dragTranslation += delta
                                                updateHoverTarget(entry.gridKey, dragTranslation)
                                            }
                                        },
                                        onImmediateDragEnd = { finishDrag() },
                                        onMemberTabDragStart = { memberTab, rect ->
                                            if (!isSelectionMode) {
                                                draggedMemberTab = memberTab
                                                draggedMemberStartRect = rect
                                                draggedMemberOffset = Offset.Zero
                                                hoveredTargetKey = null
                                            }
                                        },
                                        onMemberTabDrag = { delta ->
                                            if (!isSelectionMode) {
                                                draggedMemberOffset += delta
                                                val member = draggedMemberTab
                                                val start = draggedMemberStartRect
                                                if (member != null && start != null) {
                                                    updateMemberHoverTarget(member, start, draggedMemberOffset)
                                                }
                                            }
                                        },
                                        onMemberTabDragEnd = { finishMemberDrag() },
                                        preferences = preferences,
                                        homeWallpapers = homeWallpapers,
                                        customWallpaperBitmap = customWallpaperBitmap
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Drag-to-Remove-From-Group Banner when dragging a mini-tab out of a Group Card
            AnimatedVisibility(
                visible = draggedMemberTab != null,
                enter = fadeIn(ApexMotion.effectsSpring()) + scaleIn(initialScale = 0.92f, animationSpec = ApexMotion.spatialSpring()),
                exit = fadeOut(ApexMotion.effectsSpring()) + scaleOut(targetScale = 0.92f, animationSpec = ApexMotion.spatialSpring()),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .zIndex(50f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary),
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp)
                        .onGloballyPositioned { coords ->
                            ungroupDropBannerBounds = coords.boundsInRoot()
                        }
                        .testTag("grid_ungroup_drop_banner")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CallSplit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.tab_group_drop_ungroup_hint),
                            style = EmphasizedTypography.activeTabTitle,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            // Floating Ghost Pill while dragging a grouped tab out of its Group Card
            val activeDraggedMember = draggedMemberTab
            val activeMemberStartRect = draggedMemberStartRect
            if (activeDraggedMember != null && activeMemberStartRect != null) {
                val ghostTopLeft = activeMemberStartRect.topLeft - rootPositionInRoot + draggedMemberOffset
                val ghostPalette = activeDraggedMember.tabColor?.resolveTonalColors()
                Surface(
                    shape = CircleShape,
                    color = ghostPalette?.container ?: MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(
                        2.dp,
                        ghostPalette?.accent ?: MaterialTheme.colorScheme.primary
                    ),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .zIndex(65f)
                        .graphicsLayer {
                            translationX = ghostTopLeft.x
                            translationY = ghostTopLeft.y
                            scaleX = 1.06f
                            scaleY = 1.06f
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = if (activeDraggedMember.isHomePage) {
                                Icons.Outlined.Home
                            } else {
                                Icons.Filled.Public
                            },
                            contentDescription = null,
                            tint = ghostPalette?.accent ?: MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = activeDraggedMember.title,
                            style = EmphasizedTypography.activeTabTitle,
                            color = ghostPalette?.onContainer ?: MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 150.dp)
                        )
                    }
                }
            }

            // Floating Bottom Area (No Bottom Navigation Bar): Center Squarish Add (+) Button + Right 3-Dots Menu
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                if (isSelectionMode) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        tonalElevation = 8.dp,
                        shadowElevation = 4.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .testTag("tabs_selection_action_pill")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    if (selectedTabIds.isNotEmpty()) {
                                        onGroupSelectedTabs(selectedTabIds)
                                    }
                                    isSelectionMode = false
                                    selectedTabIds = emptySet()
                                },
                                enabled = selectedTabIds.isNotEmpty(),
                                modifier = Modifier.testTag("selection_group_tabs_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CreateNewFolder,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Group (${selectedTabIds.size})")
                            }

                            VerticalDivider(
                                modifier = Modifier.height(24.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )

                            TextButton(
                                onClick = {
                                    if (selectedTabIds.isNotEmpty()) {
                                        onCloseSelectedTabs(selectedTabIds)
                                    }
                                    isSelectionMode = false
                                    selectedTabIds = emptySet()
                                },
                                enabled = selectedTabIds.isNotEmpty(),
                                modifier = Modifier.testTag("selection_close_tabs_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteSweep,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Close (${selectedTabIds.size})")
                            }
                        }
                    }
                } else {
                    // Center: Squarish Add (+) Button
                    val addShape = RoundedCornerShape(16.dp)
                    Surface(
                        shape = addShape,
                        color = if (switcherFilter == TabSwitcherFilter.INCOGNITO_ONLY) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        tonalElevation = 8.dp,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(56.dp)
                            .clip(addShape)
                            .clickable {
                                if (switcherFilter == TabSwitcherFilter.INCOGNITO_ONLY) {
                                    onNewIncognitoTab()
                                } else {
                                    onNewTab()
                                }
                            }
                            .testTag("switcher_new_tab_fab")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.testTag("tabs_center_action_pill")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = stringResource(R.string.tabs_action_add_tab),
                                tint = if (switcherFilter == TabSwitcherFilter.INCOGNITO_ONLY) {
                                    MaterialTheme.colorScheme.onTertiary
                                } else {
                                    MaterialTheme.colorScheme.onPrimary
                                },
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    // Right Side: 3-Dots More Menu Button
                    Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            tonalElevation = 6.dp,
                            shadowElevation = 4.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .size(52.dp)
                                .clip(CircleShape)
                                .clickable { isMoreMenuExpanded = true }
                                .testTag("switcher_more_menu_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = stringResource(R.string.cd_tab_switcher_more_options),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = isMoreMenuExpanded,
                            onDismissRequest = { isMoreMenuExpanded = false },
                            shape = RoundedCornerShape(18.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .widthIn(min = 220.dp)
                                .testTag("switcher_more_dropdown_menu")
                        ) {
                            val isIncognitoFilter = switcherFilter == TabSwitcherFilter.INCOGNITO_ONLY
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (isIncognitoFilter) {
                                                R.string.tabs_menu_close_all_private
                                            } else {
                                                R.string.tabs_menu_close_all
                                            }
                                        )
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_shredder),
                                        contentDescription = null,
                                        tint = if (isIncognitoFilter) {
                                            MaterialTheme.colorScheme.tertiary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                enabled = if (isIncognitoFilter) totalIncognitoCount > 0 else true,
                                onClick = {
                                    isMoreMenuExpanded = false
                                    if (isIncognitoFilter) {
                                        onCloseAllIncognitoTabs()
                                    } else {
                                        onResetAllTabs()
                                    }
                                },
                                modifier = Modifier.testTag("switcher_menu_close_all_tabs")
                            )

                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tabs_menu_delete_browsing_data)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.CleaningServices,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    isMoreMenuExpanded = false
                                    onClearBrowsingData()
                                },
                                modifier = Modifier.testTag("switcher_menu_delete_browsing_data")
                            )

                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tabs_menu_select_tab)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.CheckBox,
                                        contentDescription = null
                                    )
                                },
                                enabled = visibleEntries.isNotEmpty(),
                                onClick = {
                                    isMoreMenuExpanded = false
                                    selectedTabIds = emptySet()
                                    isSelectionMode = true
                                },
                                modifier = Modifier.testTag("switcher_menu_select_tab")
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tabs_menu_settings)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Settings,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    isMoreMenuExpanded = false
                                    onOpenSettings()
                                },
                                modifier = Modifier.testTag("switcher_menu_settings")
                            )

                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tabs_menu_new_tab_group)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.CreateNewFolder,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    isMoreMenuExpanded = false
                                    if (isIncognitoFilter) {
                                        onCreateIncognitoGroup?.invoke() ?: onCreateGroup()
                                    } else {
                                        onCreateGroup()
                                    }
                                },
                                modifier = Modifier.testTag("switcher_menu_new_group")
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (isIncognitoFilter) {
                                                R.string.tabs_menu_new_private_tab
                                            } else {
                                                R.string.tabs_menu_new_tab
                                            }
                                        )
                                    )
                                },
                                leadingIcon = {
                                    if (isIncognitoFilter) {
                                        IncognitoGlassesIcon(
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.Add,
                                            contentDescription = null
                                        )
                                    }
                                },
                                onClick = {
                                    isMoreMenuExpanded = false
                                    if (isIncognitoFilter) {
                                        onNewIncognitoTab()
                                    } else {
                                        onNewTab()
                                    }
                                },
                                modifier = Modifier.testTag("switcher_menu_new_tab")
                            )
                        }
                    }
                }
            }

            // Expanded Tab Group Detail Modal
            val expandedGroupEntry = gridEntries
                .filterIsInstance<TabGridEntry.GroupTab>()
                .firstOrNull { it.group.id == expandedGroupId }

            AnimatedVisibility(
                visible = expandedGroupEntry != null,
                enter = fadeIn(ApexMotion.effectsSpring()) +
                    scaleIn(initialScale = 0.90f, animationSpec = ApexMotion.spatialSpring()),
                exit = fadeOut(ApexMotion.effectsSpring()) +
                    scaleOut(targetScale = 0.90f, animationSpec = ApexMotion.spatialSpring()),
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(40f)
            ) {
                if (expandedGroupEntry != null) {
                    ExpandedGroupModal(
                        groupEntry = expandedGroupEntry,
                        activeTabId = activeTabId,
                        onSelectTab = onSelectTab,
                        onEditGroup = { onEditGroup(expandedGroupEntry.group.id) },
                        onAddTabToGroup = { onAddTabToGroup(expandedGroupEntry.group.id) },
                        onUngroupTab = onUngroupTab,
                        onCycleTabColor = onCycleTabColor,
                        onCloseTab = onCloseTab,
                        onDismiss = { onOpenGroup(null) },
                        preferences = preferences,
                        homeWallpapers = homeWallpapers,
                        customWallpaperBitmap = customWallpaperBitmap
                    )
                }
            }
        }
    }
}

/**
 * Top Mode Pill with Icons:
 * - Left: Square with count at centre (same as Bottom Navigation Bar) for Tabs
 * - Center: Glasses icon for Incognito (hidden by default; appears when opened from the Menu section)
 * - Right: Grid View icon for Tab Groups
 */
@Composable
private fun CenteredTabsAndGroupsModePill(
    selectedFilter: TabSwitcherFilter,
    totalTabs: Int,
    showIncognito: Boolean = false,
    onSelectFilter: (TabSwitcherFilter) -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .height(44.dp)
            .testTag("tabs_groups_center_mode_pill")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            // 1. Left: Tabs (Square with count at centre, same as Bottom Navigation Bar)
            val tabsSelected = selectedFilter == TabSwitcherFilter.ALL_TABS
            val tabsBg by animateColorAsState(
                targetValue = if (tabsSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                },
                animationSpec = ApexMotion.effectsSpec(),
                label = "TabsPillSegmentBg"
            )

            Surface(
                shape = CircleShape,
                color = tabsBg,
                modifier = Modifier
                    .height(36.dp)
                    .width(52.dp)
                    .clip(CircleShape)
                    .clickable { onSelectFilter(TabSwitcherFilter.ALL_TABS) }
                    .testTag("mode_pill_tabs_segment")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    SquareTabCounterIcon(
                        count = totalTabs,
                        isSelected = tabsSelected
                    )
                }
            }

            // 2. Center: Incognito (Glasses icon — hidden by default; appears when opened from Menu section)
            AnimatedVisibility(visible = showIncognito) {
                val incognitoSelected = selectedFilter == TabSwitcherFilter.INCOGNITO_ONLY
                val incognitoBg by animateColorAsState(
                    targetValue = if (incognitoSelected) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        Color.Transparent
                    },
                    animationSpec = ApexMotion.effectsSpec(),
                    label = "IncognitoPillSegmentBg"
                )
                val incognitoFg by animateColorAsState(
                    targetValue = if (incognitoSelected) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = ApexMotion.effectsSpec(),
                    label = "IncognitoPillSegmentFg"
                )

                Surface(
                    shape = CircleShape,
                    color = incognitoBg,
                    modifier = Modifier
                        .height(36.dp)
                        .width(52.dp)
                        .clip(CircleShape)
                        .clickable { onSelectFilter(TabSwitcherFilter.INCOGNITO_ONLY) }
                        .testTag("mode_pill_incognito_segment")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        IncognitoGlassesIcon(
                            tint = incognitoFg,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // 3. Right: Groups (Grid View icon)
            val groupsSelected = selectedFilter == TabSwitcherFilter.GROUPS_ONLY
            val groupsBg by animateColorAsState(
                targetValue = if (groupsSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                },
                animationSpec = ApexMotion.effectsSpec(),
                label = "GroupsPillSegmentBg"
            )
            val groupsFg by animateColorAsState(
                targetValue = if (groupsSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = ApexMotion.effectsSpec(),
                label = "GroupsPillSegmentFg"
            )

            Surface(
                shape = CircleShape,
                color = groupsBg,
                modifier = Modifier
                    .height(36.dp)
                    .width(52.dp)
                    .clip(CircleShape)
                    .clickable { onSelectFilter(TabSwitcherFilter.GROUPS_ONLY) }
                    .testTag("mode_pill_groups_segment")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (groupsSelected) Icons.Filled.GridView else Icons.Outlined.GridView,
                        contentDescription = stringResource(R.string.tabs_pill_groups),
                        tint = groupsFg,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Custom vector Glasses icon for Incognito mode.
 */
@Composable
fun IncognitoGlassesIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = w * 0.10f
        val lensRadius = w * 0.21f
        val leftCenter = Offset(w * 0.27f, h * 0.56f)
        val rightCenter = Offset(w * 0.73f, h * 0.56f)

        // Subtle tinted lenses
        drawCircle(
            color = tint.copy(alpha = 0.18f),
            radius = lensRadius,
            center = leftCenter
        )
        drawCircle(
            color = tint.copy(alpha = 0.18f),
            radius = lensRadius,
            center = rightCenter
        )

        // Left & right lens rims
        drawCircle(
            color = tint,
            radius = lensRadius,
            center = leftCenter,
            style = Stroke(width = strokeWidth)
        )
        drawCircle(
            color = tint,
            radius = lensRadius,
            center = rightCenter,
            style = Stroke(width = strokeWidth)
        )

        // Bridge between lenses
        drawLine(
            color = tint,
            start = Offset(leftCenter.x + lensRadius * 0.85f, h * 0.50f),
            end = Offset(rightCenter.x - lensRadius * 0.85f, h * 0.50f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Left temple arm
        drawLine(
            color = tint,
            start = Offset(leftCenter.x - lensRadius, h * 0.52f),
            end = Offset(w * 0.03f, h * 0.42f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Right temple arm
        drawLine(
            color = tint,
            start = Offset(rightCenter.x + lensRadius, h * 0.52f),
            end = Offset(w * 0.97f, h * 0.42f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}
