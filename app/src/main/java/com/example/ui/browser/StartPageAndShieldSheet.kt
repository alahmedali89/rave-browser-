package com.example.ui.browser

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.TopVisitEntity
import com.example.data.preferences.BrowserPreferences
import com.example.data.preferences.HomeWallpaperMode
import com.example.engine.HomeWallpaperData
import com.example.ui.theme.EmphasizedTypography
import com.example.ui.theme.LocalApexDarkTheme
import java.net.URI

@Composable
fun StartPageHome(
    preferences: BrowserPreferences,
    topVisits: List<TopVisitEntity>,
    onOpenSearchScreen: () -> Unit,
    onSelectDestination: (String) -> Unit,
    onAddTopVisit: (String, String) -> Unit,
    onDeleteTopVisit: (Long) -> Unit,
    isIncognito: Boolean = false,
    incognitoTabsCount: Int = 0,
    onCloseAllIncognitoTabs: () -> Unit = {},
    wallpaperData: HomeWallpaperData? = null,
    customWallpaperBitmap: Bitmap? = null,
    onRefreshWallpaper: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var visitPendingDelete by remember { mutableStateOf<TopVisitEntity?>(null) }

    val isDarkTheme = LocalApexDarkTheme.current ?: isSystemInDarkTheme()
    val bwColorFilter = remember(preferences.homeWallpaperBlackAndWhite) {
        if (preferences.homeWallpaperBlackAndWhite) {
            ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
        } else {
            null
        }
    }

    val activePhotoBitmap = when (preferences.homeWallpaperMode) {
        HomeWallpaperMode.ONLINE_API -> wallpaperData?.bitmap
        HomeWallpaperMode.CUSTOM_PHONE -> customWallpaperBitmap
        HomeWallpaperMode.BLACK_AND_WHITE -> null
    }

    val solidContainerColor = when (preferences.homeWallpaperMode) {
        HomeWallpaperMode.BLACK_AND_WHITE -> if (isDarkTheme) Color.Black else Color.White
        else -> MaterialTheme.colorScheme.background
    }

    val hasWallpaperPhoto = activePhotoBitmap != null

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier
            .fillMaxSize()
            .background(solidContainerColor)
            .testTag("start_page_hub")
    ) {
        if (preferences.homeWallpaperMode == HomeWallpaperMode.BLACK_AND_WHITE) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(solidContainerColor)
                    .testTag("home_wallpaper_bw_canvas")
            )
        }

        if (activePhotoBitmap != null) {
            val imageBitmap = remember(activePhotoBitmap) { activePhotoBitmap.asImageBitmap() }
            Image(
                bitmap = imageBitmap,
                contentDescription = wallpaperData?.author?.let { "Wallpaper by $it" }
                    ?: "Custom home wallpaper",
                contentScale = ContentScale.Crop,
                colorFilter = bwColorFilter,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("home_wallpaper_image")
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.24f),
                                Color.Black.copy(alpha = 0.16f),
                                Color.Black.copy(alpha = 0.42f)
                            )
                        )
                    )
            )
        }

        if (isIncognito || preferences.showTopVisitsOnHome) {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
            ) {
                if (isIncognito) {
                    item(key = "incognito_status_card") {
                        IncognitoHomeStatusCard(
                            incognitoTabsCount = incognitoTabsCount,
                            onCloseAllIncognitoTabs = onCloseAllIncognitoTabs
                        )
                    }
                }

                // User-saved Top Visits Horizontal Row with + Add Button (no default items; only Add button when empty)
                if (preferences.showTopVisitsOnHome) {
                    item(key = "top_visits_row_item") {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("home_top_visits_section")
                        ) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("home_top_visits_row")
                            ) {
                                // Real user-added top visits shortcuts
                                items(
                                    items = topVisits,
                                    key = { it.id }
                                ) { visit ->
                                    TopVisitTile(
                                        topVisit = visit,
                                        hasWallpaperBackdrop = hasWallpaperPhoto,
                                        onClick = { onSelectDestination(visit.url) },
                                        onDelete = { visitPendingDelete = visit }
                                    )
                                }

                                // Add button tile
                                item(key = "add_shortcut_tile") {
                                    AddTopVisitTile(
                                        hasWallpaperBackdrop = hasWallpaperPhoto,
                                        onClick = { showAddDialog = true }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (preferences.homeWallpaperMode == HomeWallpaperMode.ONLINE_API && wallpaperData != null) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.48f),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.24f)),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 96.dp)
                    .testTag("home_wallpaper_credit_pill")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = wallpaperData.author,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 160.dp)
                    )
                    IconButton(
                        onClick = onRefreshWallpaper,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(32.dp)
                            .testTag("home_wallpaper_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.cd_refresh_home_wallpaper),
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }

    // Add Shortcut Dialog
    if (showAddDialog) {
        AddTopVisitDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, url ->
                onAddTopVisit(name, url)
                showAddDialog = false
            }
        )
    }

    // Delete Shortcut Confirmation Dialog
    visitPendingDelete?.let { visit ->
        AlertDialog(
            onDismissRequest = { visitPendingDelete = null },
            title = { Text(stringResource(R.string.cd_remove_top_visit)) },
            text = { Text("Remove \"${visit.title}\" from your top visits?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteTopVisit(visit.id)
                        visitPendingDelete = null
                    }
                ) {
                    Text(stringResource(R.string.remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { visitPendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun IncognitoHomeStatusCard(
    incognitoTabsCount: Int,
    onCloseAllIncognitoTabs: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("incognito_home_card")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.VisibilityOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.incognito_home_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (incognitoTabsCount > 1) {
                            "$incognitoTabsCount Incognito tabs open • Zero local retention"
                        } else {
                            "1 Incognito tab open • Zero local retention"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            Text(
                text = stringResource(R.string.incognito_home_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "No History • No Cookies • No Site Data",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            FilledTonalButton(
                onClick = onCloseAllIncognitoTabs,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("incognito_end_session_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.CleaningServices,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.incognito_end_session_button),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun AddTopVisitTile(
    hasWallpaperBackdrop: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tileShape = RoundedCornerShape(16.dp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .width(68.dp)
            .testTag("home_add_top_visit_tile")
    ) {
        Surface(
            shape = tileShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(
                alpha = if (hasWallpaperBackdrop) 0.90f else 1f
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .size(56.dp)
                .clip(tileShape)
                .clickable(onClick = onClick)
                .testTag("home_add_top_visit_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.home_add_top_visit),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        if (hasWallpaperBackdrop) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.45f)
            ) {
                Text(
                    text = stringResource(R.string.home_add_top_visit),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        } else {
            Text(
                text = stringResource(R.string.home_add_top_visit),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TopVisitTile(
    topVisit: TopVisitEntity,
    hasWallpaperBackdrop: Boolean = false,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tileShape = RoundedCornerShape(16.dp)

    val hostLabel = remember(topVisit.url) {
        runCatching {
            URI(topVisit.url).host?.removePrefix("www.")?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: topVisit.url
    }

    val monogram = remember(hostLabel, topVisit.title) {
        topVisit.title.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString()
            ?: hostLabel.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString()
            ?: "W"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .width(68.dp)
            .testTag("home_top_visit_tile_${topVisit.id}")
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Surface(
                shape = tileShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .size(56.dp)
                    .clip(tileShape)
                    .clickable(onClick = onClick)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = monogram,
                                style = EmphasizedTypography.activeTabTitle,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Quick delete trigger button
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDelete)
                    .testTag("home_remove_top_visit_${topVisit.id}")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cd_remove_top_visit),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        if (hasWallpaperBackdrop) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.45f)
            ) {
                Text(
                    text = topVisit.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        } else {
            Text(
                text = topVisit.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AddTopVisitDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_add_top_visit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.dialog_top_visit_name_label)) },
                    placeholder = { Text(stringResource(R.string.dialog_top_visit_name_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_top_visit_name_input")
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.dialog_top_visit_url_label)) },
                    placeholder = { Text(stringResource(R.string.dialog_top_visit_url_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_top_visit_url_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedUrl = url.trim()
                    val cleanUrl = if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
                        "https://$trimmedUrl"
                    } else {
                        trimmedUrl
                    }
                    val cleanTitle = title.trim().ifEmpty {
                        runCatching { URI(cleanUrl).host?.removePrefix("www.") }.getOrNull() ?: cleanUrl
                    }
                    onConfirm(cleanTitle, cleanUrl)
                },
                enabled = url.isNotBlank(),
                modifier = Modifier.testTag("dialog_top_visit_confirm_button")
            ) {
                Text(stringResource(R.string.dialog_add_button))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dialog_top_visit_cancel_button")
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
