package com.example.ui.browser

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.preferences.BrowserPreferences
import com.example.ui.theme.EmphasizedTypography
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AccessibilityFullPage(
    preferences: BrowserPreferences,
    onChangeDefaultZoom: (Int) -> Unit,
    onSetSiteZoom: (String, Int) -> Unit,
    onRemoveSiteZoom: (String) -> Unit,
    onClearSavedSiteZooms: () -> Unit,
    onToggleShowZoomInMainMenu: (Boolean) -> Unit,
    onToggleForceEnableZoom: (Boolean) -> Unit,
    onToggleSimplifiedView: (Boolean) -> Unit,
    onToggleCaretBrowsing: (Boolean) -> Unit,
    onToggleTouchpadSwipeNavigation: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onNavigateBack)

    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var showSavedZoomDialog by rememberSaveable { mutableStateOf(false) }
    var showHelpDialog by rememberSaveable { mutableStateOf(false) }

    val zoomPercent = BrowserPreferences.clampZoomPercent(preferences.defaultZoomPercent)
    val zoomScale = zoomPercent / 100f

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
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("settings_accessibility_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.settings_accessibility_section),
                        style = EmphasizedTypography.headlineHero
                    )
                },
                actions = {
                    IconButton(
                        onClick = { showHelpDialog = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("settings_accessibility_help_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.HelpOutline,
                            contentDescription = stringResource(R.string.cd_accessibility_help)
                        )
                    }
                }
            )
        },
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .testTag("settings_accessibility_screen")
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .testTag("settings_accessibility_list")
            ) {
                // Card 1: Default zoom slider card
                item(key = "accessibility_default_zoom") {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            0.75.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("accessibility_default_zoom_card")
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 18.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.accessibility_default_zoom_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.accessibility_default_zoom_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.padding(top = 2.dp))
                            Text(
                                text = stringResource(R.string.accessibility_zoom_percent_format, zoomPercent),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .testTag("accessibility_default_zoom_value")
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                IconButton(
                                    onClick = {
                                        onChangeDefaultZoom(
                                            (zoomPercent - BrowserPreferences.ZOOM_STEP_PERCENT)
                                                .coerceAtLeast(BrowserPreferences.MIN_ZOOM_PERCENT)
                                        )
                                    },
                                    enabled = zoomPercent > BrowserPreferences.MIN_ZOOM_PERCENT,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("accessibility_zoom_out_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Remove,
                                        contentDescription = stringResource(R.string.cd_accessibility_zoom_out)
                                    )
                                }

                                Slider(
                                    value = zoomPercent.toFloat(),
                                    onValueChange = { rawValue ->
                                        val snapped = ((rawValue / BrowserPreferences.ZOOM_STEP_PERCENT.toFloat())
                                            .roundToInt() * BrowserPreferences.ZOOM_STEP_PERCENT)
                                            .coerceIn(
                                                BrowserPreferences.MIN_ZOOM_PERCENT,
                                                BrowserPreferences.MAX_ZOOM_PERCENT
                                            )
                                        if (snapped != zoomPercent) {
                                            onChangeDefaultZoom(snapped)
                                        }
                                    },
                                    valueRange = BrowserPreferences.MIN_ZOOM_PERCENT.toFloat()..
                                        BrowserPreferences.MAX_ZOOM_PERCENT.toFloat(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("accessibility_zoom_slider")
                                )

                                IconButton(
                                    onClick = {
                                        onChangeDefaultZoom(
                                            (zoomPercent + BrowserPreferences.ZOOM_STEP_PERCENT)
                                                .coerceAtMost(BrowserPreferences.MAX_ZOOM_PERCENT)
                                        )
                                    },
                                    enabled = zoomPercent < BrowserPreferences.MAX_ZOOM_PERCENT,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("accessibility_zoom_in_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = stringResource(R.string.cd_accessibility_zoom_in)
                                    )
                                }
                            }
                        }
                    }
                }

                // Card 2: Live zoom preview card
                item(key = "accessibility_preview") {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(
                            0.75.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("accessibility_preview_card")
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 18.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.accessibility_preview_label),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = Icons.Outlined.Apartment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(26.dp)
                            )
                            Text(
                                text = stringResource(R.string.accessibility_preview_headline),
                                fontSize = (18f * zoomScale).sp,
                                lineHeight = (24f * zoomScale).sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.accessibility_preview_chapter),
                                fontSize = (15f * zoomScale).sp,
                                lineHeight = (21f * zoomScale).sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.accessibility_preview_body),
                                fontSize = (13f * zoomScale).sp,
                                lineHeight = (18f * zoomScale).sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Group: Material 3 Expressive Grouped List (7 items matching screenshots)
                item(key = "accessibility_options_group") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(GroupedListSpacing),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // 0: Saved zoom for sites
                        MaterialGroupListItem(
                            index = 0,
                            totalCount = 7,
                            title = stringResource(R.string.accessibility_saved_zoom_title),
                            role = Role.Button,
                            onClick = { showSavedZoomDialog = true },
                            trailingContent = if (preferences.savedSiteZooms.isNotEmpty()) {
                                {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Text(
                                            text = preferences.savedSiteZooms.size.toString(),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                            modifier = Modifier.testTag("settings_accessibility_saved_zoom")
                        )

                        // 1: Show zoom option in main menu
                        AccessibilitySwitchGroupItem(
                            index = 1,
                            totalCount = 7,
                            title = stringResource(R.string.accessibility_show_zoom_menu_title),
                            description = stringResource(R.string.accessibility_show_zoom_menu_desc),
                            checked = preferences.showZoomInMainMenu,
                            onCheckedChange = onToggleShowZoomInMainMenu,
                            tag = "settings_switch_show_zoom_menu"
                        )

                        // 2: Force enable zoom
                        AccessibilitySwitchGroupItem(
                            index = 2,
                            totalCount = 7,
                            title = stringResource(R.string.accessibility_force_zoom_title),
                            description = stringResource(R.string.accessibility_force_zoom_desc),
                            checked = preferences.forceEnableZoom,
                            onCheckedChange = onToggleForceEnableZoom,
                            tag = "settings_switch_force_zoom"
                        )

                        // 3: Simplified view for web pages
                        AccessibilitySwitchGroupItem(
                            index = 3,
                            totalCount = 7,
                            title = stringResource(R.string.accessibility_simplified_view_title),
                            description = stringResource(R.string.accessibility_simplified_view_desc),
                            checked = preferences.simplifiedViewEnabled,
                            onCheckedChange = onToggleSimplifiedView,
                            tag = "settings_switch_simplified_view"
                        )

                        // 4: Navigate pages with a text cursor when a keyboard is attached
                        AccessibilitySwitchGroupItem(
                            index = 4,
                            totalCount = 7,
                            title = stringResource(R.string.accessibility_caret_browsing_title),
                            description = stringResource(R.string.accessibility_caret_browsing_desc),
                            checked = preferences.caretBrowsingEnabled,
                            onCheckedChange = onToggleCaretBrowsing,
                            tag = "settings_switch_caret_browsing"
                        )

                        // 5: Captions
                        MaterialGroupListItem(
                            index = 5,
                            totalCount = 7,
                            title = stringResource(R.string.accessibility_captions_title),
                            role = Role.Button,
                            onClick = {
                                val launched = runCatching {
                                    val intent = Intent(Settings.ACTION_CAPTIONING_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }.isSuccess
                                if (!launched) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.accessibility_captions_unavailable),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.testTag("settings_accessibility_captions")
                        )

                        // 6: Swipe between pages using a touchpad
                        AccessibilitySwitchGroupItem(
                            index = 6,
                            totalCount = 7,
                            title = stringResource(R.string.accessibility_touchpad_swipe_title),
                            description = stringResource(R.string.accessibility_touchpad_swipe_desc),
                            checked = preferences.touchpadSwipeNavigationEnabled,
                            onCheckedChange = onToggleTouchpadSwipeNavigation,
                            tag = "settings_switch_touchpad_swipe"
                        )
                    }
                }
            }
        }
    }

    if (showSavedZoomDialog) {
        SavedSiteZoomsDialog(
            savedSiteZooms = preferences.savedSiteZooms,
            onSetSiteZoom = onSetSiteZoom,
            onRemoveSiteZoom = onRemoveSiteZoom,
            onClearAll = onClearSavedSiteZooms,
            onDismiss = { showSavedZoomDialog = false }
        )
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.HelpOutline,
                    contentDescription = null
                )
            },
            title = {
                Text(stringResource(R.string.accessibility_help_title))
            },
            text = {
                Text(
                    text = stringResource(R.string.accessibility_help_body),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showHelpDialog = false },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.testTag("accessibility_help_close_button")
                ) {
                    Text(stringResource(R.string.action_close))
                }
            },
            modifier = Modifier.testTag("accessibility_help_dialog")
        )
    }
}

@Composable
private fun AccessibilitySwitchGroupItem(
    index: Int,
    totalCount: Int,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    MaterialGroupListItem(
        index = index,
        totalCount = totalCount,
        title = title,
        subtitle = description,
        role = Role.Switch,
        onClick = { onCheckedChange(!checked) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null
            )
        },
        modifier = Modifier.testTag(tag)
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SavedSiteZoomsDialog(
    savedSiteZooms: Map<String, Int>,
    onSetSiteZoom: (String, Int) -> Unit,
    onRemoveSiteZoom: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val entries = savedSiteZooms.entries.sortedBy { it.key }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.accessibility_saved_zoom_title))
        },
        text = {
            if (entries.isEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.accessibility_saved_zoom_empty_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.accessibility_saved_zoom_empty_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(GroupedListSpacing),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .testTag("saved_site_zooms_list")
                ) {
                    items(entries, key = { it.key }) { (host, percent) ->
                        val idx = entries.indexOfFirst { it.key == host }.coerceAtLeast(0)
                        Surface(
                            shape = groupedListItemShape(index = idx, totalCount = entries.size),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = host,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = stringResource(R.string.accessibility_zoom_percent_format, percent),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                FilledTonalIconButton(
                                    onClick = {
                                        onSetSiteZoom(
                                            host,
                                            (percent - BrowserPreferences.ZOOM_STEP_PERCENT)
                                                .coerceAtLeast(BrowserPreferences.MIN_ZOOM_PERCENT)
                                        )
                                    },
                                    enabled = percent > BrowserPreferences.MIN_ZOOM_PERCENT,
                                    shapes = IconButtonDefaults.shapes(),
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("saved_site_zoom_minus_$host")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Remove,
                                        contentDescription = stringResource(R.string.cd_accessibility_zoom_out),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                FilledTonalIconButton(
                                    onClick = {
                                        onSetSiteZoom(
                                            host,
                                            (percent + BrowserPreferences.ZOOM_STEP_PERCENT)
                                                .coerceAtMost(BrowserPreferences.MAX_ZOOM_PERCENT)
                                        )
                                    },
                                    enabled = percent < BrowserPreferences.MAX_ZOOM_PERCENT,
                                    shapes = IconButtonDefaults.shapes(),
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("saved_site_zoom_plus_$host")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = stringResource(R.string.cd_accessibility_zoom_in),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onRemoveSiteZoom(host) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("saved_site_zoom_remove_$host")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = stringResource(R.string.remove),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            if (entries.isNotEmpty()) {
                TextButton(
                    onClick = onClearAll,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.testTag("saved_site_zoom_reset_all")
                ) {
                    Text(stringResource(R.string.accessibility_saved_zoom_clear_all))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.testTag("saved_site_zoom_close")
            ) {
                Text(stringResource(R.string.action_close))
            }
        },
        modifier = Modifier.testTag("saved_site_zooms_dialog")
    )
}
