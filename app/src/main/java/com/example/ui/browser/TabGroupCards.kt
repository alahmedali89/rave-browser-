package com.example.ui.browser

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.example.R
import com.example.data.local.TopVisitEntity
import com.example.data.preferences.BrowserPreferences
import com.example.data.preferences.HomeWallpaperMode
import com.example.data.preferences.SearchEngineOption
import com.example.engine.HomeWallpaperData
import com.example.ui.theme.ApexMotion
import com.example.ui.theme.EmphasizedTypography
import com.example.ui.theme.LocalApexDarkTheme
import com.example.ui.theme.TabGroupTonalColors
import com.example.ui.theme.rememberMorphingCornerShape
import com.example.ui.theme.resolveTonalColors
import com.example.ui.viewmodel.BrowserTab
import com.example.ui.viewmodel.TabGridEntry
import java.net.URI
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * Horizontal swipe-to-delete wrapper for tab cards and tab rows.
 * Translates and fades the card cleanly without rendering any red backdrop or icon behind it.
 */
@Composable
internal fun SwipeToDeleteTabContainer(
    tabId: String,
    enabled: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val currentOnDelete by rememberUpdatedState(onDelete)
    val currentEnabled by rememberUpdatedState(enabled)

    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    val offsetX = remember(tabId) { Animatable(0f) }
    var isDismissed by remember(tabId) { mutableStateOf(false) }

    LaunchedEffect(enabled) {
        if (!enabled && offsetX.value != 0f && !isDismissed) {
            offsetX.animateTo(0f, ApexMotion.spatialSpring())
        }
    }

    val draggableState = rememberDraggableState { delta ->
        if (!currentEnabled || isDismissed) return@rememberDraggableState
        scope.launch {
            val maxSwipe = if (containerWidthPx > 0f) containerWidthPx * 1.15f else 900f
            offsetX.snapTo((offsetX.value + delta).coerceIn(-maxSwipe, maxSwipe))
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { size ->
                containerWidthPx = size.width.toFloat()
            }
            .graphicsLayer {
                val width = if (containerWidthPx > 0f) containerWidthPx else 420f
                val fadeProgress = (abs(offsetX.value) / width).coerceIn(0f, 1f)
                translationX = offsetX.value
                rotationZ = (offsetX.value / width) * 3.5f
                alpha = (1f - fadeProgress * 0.55f).coerceIn(0.25f, 1f)
            }
            .draggable(
                state = draggableState,
                orientation = Orientation.Horizontal,
                enabled = currentEnabled && !isDismissed,
                onDragStopped = { velocity ->
                    if (isDismissed) return@draggable
                    val currentOffset = offsetX.value
                    val width = if (containerWidthPx > 0f) containerWidthPx else 420f
                    val shouldDismissByDistance = abs(currentOffset) >= width * 0.38f
                    val shouldDismissByFling = abs(velocity) >= 750f &&
                        abs(currentOffset) >= width * 0.14f &&
                        (currentOffset * velocity > 0f)

                    if (shouldDismissByDistance || shouldDismissByFling) {
                        isDismissed = true
                        val direction = when {
                            currentOffset != 0f -> if (currentOffset > 0f) 1f else -1f
                            velocity > 0f -> 1f
                            else -> -1f
                        }
                        val targetExit = direction * (width * 1.25f)
                        scope.launch {
                            offsetX.animateTo(
                                targetValue = targetExit,
                                animationSpec = tween(durationMillis = 160)
                            )
                            currentOnDelete()
                        }
                    } else {
                        scope.launch {
                            offsetX.animateTo(
                                targetValue = 0f,
                                animationSpec = ApexMotion.spatialSpring()
                            )
                        }
                    }
                }
            )
    ) {
        content()
    }
}

@Composable
internal fun SingleTabGridCard(
    tab: BrowserTab,
    isSelected: Boolean,
    isSelectionMode: Boolean = false,
    isDropTarget: Boolean,
    swipeToDeleteEnabled: Boolean = true,
    topVisits: List<TopVisitEntity> = emptyList(),
    preferences: BrowserPreferences = BrowserPreferences(),
    wallpaperData: HomeWallpaperData? = null,
    customWallpaperBitmap: Bitmap? = null,
    onClick: () -> Unit,
    onClose: () -> Unit,
    onCycleTabColor: () -> Unit = {},
    onImmediateDragStart: () -> Unit,
    onImmediateDrag: (Offset) -> Unit,
    onImmediateDragEnd: () -> Unit
) {
    val customTabPalette = tab.tabColor?.resolveTonalColors()

    val cardShape = rememberMorphingCornerShape(
        targetRadius = when {
            isDropTarget -> 24.dp
            else -> 18.dp
        },
        label = "SingleGridCardShape_${tab.id}"
    )

    val containerColor by animateColorAsState(
        targetValue = when {
            isDropTarget -> MaterialTheme.colorScheme.tertiaryContainer
            isSelected && customTabPalette != null -> customTabPalette.container
            isSelected && tab.isPrivate -> MaterialTheme.colorScheme.tertiaryContainer
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            customTabPalette != null -> customTabPalette.subtleSurface
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = ApexMotion.effectsSpec(),
        label = "SingleGridCardBg_${tab.id}"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            isDropTarget -> MaterialTheme.colorScheme.onTertiaryContainer
            isSelected && customTabPalette != null -> customTabPalette.onContainer
            isSelected && tab.isPrivate -> MaterialTheme.colorScheme.onTertiaryContainer
            isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        },
        animationSpec = ApexMotion.effectsSpec(),
        label = "SingleGridCardFg_${tab.id}"
    )

    val borderColor = when {
        isDropTarget -> MaterialTheme.colorScheme.tertiary
        customTabPalette != null && isSelected -> customTabPalette.accent
        isSelected && tab.isPrivate -> MaterialTheme.colorScheme.tertiary
        isSelected -> MaterialTheme.colorScheme.primary
        customTabPalette != null -> customTabPalette.border.copy(alpha = 0.65f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    }

    val borderWidth = when {
        isDropTarget -> 2.5.dp
        isSelected -> 1.5.dp
        else -> 1.dp
    }

    val displayTitle = remember(tab.title, tab.url, tab.isHomePage, tab.isPrivate) {
        when {
            tab.isHomePage && tab.title.equals("Home", ignoreCase = true) -> "New tab"
            tab.title.isNotBlank() -> tab.title
            tab.isPrivate -> "Incognito"
            else -> tab.url
        }
    }

    SwipeToDeleteTabContainer(
        tabId = tab.id,
        enabled = swipeToDeleteEnabled,
        onDelete = onClose,
        modifier = Modifier.fillMaxWidth()
    ) {
        Card(
            shape = cardShape,
            colors = CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = contentColor
            ),
            border = BorderStroke(
                width = borderWidth,
                color = borderColor
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (isSelected) 4.dp else 1.dp
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(262.dp)
                .clickable(onClick = onClick)
                .testTag("grid_tab_card_${tab.id}")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
            ) {
                // Brave-style compact header bar: [Favicon] [1-line Title] [Close ×]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .padding(horizontal = 6.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(22.dp)
                            .let { baseModifier ->
                                if (!isSelectionMode) {
                                    baseModifier.pointerInput(tab.id) {
                                        detectDragGestures(
                                            onDragStart = { onImmediateDragStart() },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                onImmediateDrag(dragAmount)
                                            },
                                            onDragEnd = { onImmediateDragEnd() },
                                            onDragCancel = { onImmediateDragEnd() }
                                        )
                                    }
                                } else {
                                    baseModifier
                                }
                            }
                            .testTag("drag_handle_tab_${tab.id}")
                    ) {
                        TabCardFavicon(
                            tab = tab,
                            isSelected = isSelected,
                            contentColor = contentColor,
                            accentOverride = customTabPalette?.accent,
                            size = 18.dp,
                            modifier = if (tab.isPrivate) {
                                Modifier.testTag("incognito_tab_badge_${tab.id}")
                            } else {
                                Modifier
                            }
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                        ),
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    if (isSelectionMode) {
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f)
                            },
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .clickable(
                                    role = Role.Checkbox,
                                    onClick = onClick
                                )
                                .testTag("select_tab_checkbox_${tab.id}")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isSelected) {
                                        Icons.Filled.CheckCircle
                                    } else {
                                        Icons.Outlined.RadioButtonUnchecked
                                    },
                                    contentDescription = null,
                                    tint = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .clickable(
                                    role = Role.Button,
                                    onClick = onClose
                                )
                                .testTag("close_tab_button_${tab.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.cd_close_tab),
                                tint = contentColor.copy(alpha = 0.88f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Full-bleed rounded inner preview thumbnail
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(
                        0.75.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                ) {
                    when {
                        isDropTarget -> {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f))
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.tertiary
                                ) {
                                    Text(
                                        text = stringResource(R.string.tab_group_drop_hint),
                                        style = EmphasizedTypography.badgeCounter,
                                        color = MaterialTheme.colorScheme.onTertiary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        tab.isHomePage -> {
                            StartPageMiniPreview(
                                tabId = tab.id,
                                topVisits = topVisits,
                                isPrivate = tab.isPrivate,
                                preferences = preferences,
                                wallpaperData = wallpaperData,
                                customWallpaperBitmap = customWallpaperBitmap,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        tab.previewThumbnail != null && !tab.isPrivate -> {
                            Image(
                                bitmap = tab.previewThumbnail.asImageBitmap(),
                                contentDescription = displayTitle,
                                contentScale = ContentScale.Crop,
                                alignment = Alignment.TopCenter,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        else -> {
                            WebPagePlaceholderPreview(
                                tab = tab,
                                isSelected = isSelected,
                                accentOverride = customTabPalette?.accent,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabCardFavicon(
    tab: BrowserTab,
    isSelected: Boolean,
    contentColor: Color,
    accentOverride: Color?,
    size: Dp = 18.dp,
    modifier: Modifier = Modifier
) {
    val cleanHost = remember(tab.url) {
        runCatching {
            URI(tab.url).host?.removePrefix("www.")?.takeIf { it.isNotBlank() }
        }.getOrNull()
            ?: tab.url.removePrefix("https://").removePrefix("http://").substringBefore("/").trim()
    }

    when {
        tab.isPrivate -> {
            IncognitoGlassesIcon(
                tint = if (isSelected) contentColor else MaterialTheme.colorScheme.tertiary,
                modifier = modifier.size(size)
            )
        }

        tab.isHomePage -> {
            Icon(
                imageVector = Icons.Outlined.Home,
                contentDescription = null,
                tint = if (isSelected) contentColor else (accentOverride ?: MaterialTheme.colorScheme.primary),
                modifier = modifier.size(size)
            )
        }

        tab.url.contains("google.", ignoreCase = true) -> {
            SearchEngineLogo(SearchEngineOption.GOOGLE, size = size, modifier = modifier)
        }

        tab.url.contains("duckduckgo.", ignoreCase = true) -> {
            SearchEngineLogo(SearchEngineOption.DUCKDUCKGO, size = size, modifier = modifier)
        }

        tab.url.contains("brave.", ignoreCase = true) -> {
            SearchEngineLogo(SearchEngineOption.BRAVE, size = size, modifier = modifier)
        }

        tab.url.contains("bing.", ignoreCase = true) -> {
            SearchEngineLogo(SearchEngineOption.BING, size = size, modifier = modifier)
        }

        tab.url.contains("wikipedia.", ignoreCase = true) -> {
            SearchEngineLogo(SearchEngineOption.WIKIPEDIA, size = size, modifier = modifier)
        }

        cleanHost.isNotBlank() -> {
            val initial = remember(cleanHost, tab.title) {
                cleanHost.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString()
                    ?: tab.title.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString()
                    ?: "W"
            }
            val badgeBg = accentOverride?.copy(alpha = 0.22f)
                ?: if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                }
            val badgeFg = accentOverride
                ?: if (isSelected) {
                    contentColor
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                }
            Surface(
                shape = CircleShape,
                color = badgeBg,
                modifier = modifier.size(size)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = badgeFg
                    )
                }
            }
        }

        else -> {
            Icon(
                imageVector = Icons.Filled.Public,
                contentDescription = null,
                tint = if (isSelected) contentColor else (accentOverride ?: MaterialTheme.colorScheme.primary),
                modifier = modifier.size(size)
            )
        }
    }
}

@Composable
private fun StartPageMiniPreview(
    tabId: String = "",
    topVisits: List<TopVisitEntity>,
    isPrivate: Boolean,
    preferences: BrowserPreferences = BrowserPreferences(),
    wallpaperData: HomeWallpaperData? = null,
    customWallpaperBitmap: Bitmap? = null,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = LocalApexDarkTheme.current ?: isSystemInDarkTheme()
    val isBwSolidMode = preferences.homeWallpaperMode == HomeWallpaperMode.BLACK_AND_WHITE
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
    val hasWallpaperPhoto = activePhotoBitmap != null
    val solidBwColor = if (isDarkTheme) Color.Black else Color.White

    val topWash = if (isPrivate) {
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.42f)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
    }
    val midWash = MaterialTheme.colorScheme.surfaceContainerLow
    val bottomWash = MaterialTheme.colorScheme.background

    val labelColor = when {
        hasWallpaperPhoto -> Color.White
        isBwSolidMode -> if (isDarkTheme) Color.White.copy(alpha = 0.88f) else Color.Black.copy(alpha = 0.84f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val miniTileColor = when {
        hasWallpaperPhoto -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f)
        isBwSolidMode -> if (isDarkTheme) Color(0xFF1A1A1A) else Color(0xFFF3F3F3)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)
    }
    val miniTileBorder = when {
        isBwSolidMode -> if (isDarkTheme) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.16f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }

    Box(
        modifier = modifier.then(
            if (isBwSolidMode) {
                Modifier.background(solidBwColor)
            } else if (!hasWallpaperPhoto) {
                Modifier.background(
                    brush = Brush.verticalGradient(
                        colors = listOf(topWash, midWash, bottomWash)
                    )
                )
            } else {
                Modifier.background(MaterialTheme.colorScheme.background)
            }
        )
    ) {
        if (isBwSolidMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(solidBwColor)
                    .testTag("tab_preview_wallpaper_bw_$tabId")
            )
        }

        if (activePhotoBitmap != null) {
            val imageBitmap = remember(activePhotoBitmap) { activePhotoBitmap.asImageBitmap() }
            Image(
                bitmap = imageBitmap,
                contentDescription = wallpaperData?.author?.let { "Wallpaper by $it" }
                    ?: "Custom home wallpaper preview",
                contentScale = ContentScale.Crop,
                colorFilter = bwColorFilter,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("tab_preview_wallpaper_image_$tabId")
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.24f),
                                Color.Black.copy(alpha = 0.14f),
                                Color.Black.copy(alpha = 0.42f)
                            )
                        )
                    )
            )
        }

        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 12.dp)
        ) {
            // Miniature Top Visits shortcut row matching StartPageHome & Brave New Tab preview
            val shownVisits = topVisits.take(4)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                shownVisits.forEach { visit ->
                    val monogram = remember(visit.title, visit.url) {
                        visit.title.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString()
                            ?: "W"
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = miniTileColor,
                            border = BorderStroke(0.5.dp, miniTileBorder),
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = monogram,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            text = visit.title,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = labelColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                if (shownVisits.size < 4) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = if (shownVisits.isEmpty()) Modifier.width(34.dp) else Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = miniTileColor,
                            border = BorderStroke(0.5.dp, miniTileBorder),
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                        Text(
                            text = "Add",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = labelColor,
                            maxLines = 1
                        )
                    }
                }
            }

            if (isPrivate) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                    border = BorderStroke(
                        0.8.dp,
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        IncognitoGlassesIcon(
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = stringResource(R.string.incognito_home_title),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = miniTileColor,
                    border = BorderStroke(0.6.dp, miniTileBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)
                    ) {
                        SearchEngineLogo(SearchEngineOption.BRAVE, size = 12.dp)
                        Text(
                            text = stringResource(R.string.home_search_placeholder_clean),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = if (isBwSolidMode) labelColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WebPagePlaceholderPreview(
    tab: BrowserTab,
    isSelected: Boolean,
    accentOverride: Color?,
    modifier: Modifier = Modifier
) {
    val cleanHost = remember(tab.url) {
        runCatching {
            URI(tab.url).host?.removePrefix("www.")?.takeIf { it.isNotBlank() }
        }.getOrNull()
            ?: tab.url.removePrefix("https://").removePrefix("http://").substringBefore("/").trim()
    }

    Box(
        modifier = modifier.background(
            if (tab.isPrivate) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            }
        )
    ) {
        if (tab.isLoading) {
            LinearProgressIndicator(
                progress = { tab.progress.coerceIn(0.08f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .align(Alignment.TopCenter),
                color = MaterialTheme.colorScheme.primary
            )
        }

        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (tab.isPrivate) {
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    TabCardFavicon(
                        tab = tab,
                        isSelected = isSelected,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        accentOverride = accentOverride,
                        size = 24.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = cleanHost.ifBlank { tab.title },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            if (tab.isHttps && !tab.isPrivate) {
                Spacer(modifier = Modifier.height(4.dp))
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.75f),
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

/**
 * Group Tab Card displaying a 2x2 mini-tab grid:
 * - Tinted with the group's chosen [TabGroupColorOption].
 * - Includes an Edit button to rename the group & change its color.
 * - If `tabs.size > 4`: 3 tabs are shown in slots 1–3, and the 4th slot displays `+N` remaining count.
 * - Else (`tabs.size <= 4`): up to all 4 tabs stay shown directly in slots 1–4.
 * - Individual mini-tab slots support long-press dragging out of the group to ungroup/move!
 */
@Composable
internal fun GroupTabGridCard(
    entry: TabGridEntry.GroupTab,
    activeTabId: String,
    isSelectionMode: Boolean = false,
    isSelectionChecked: Boolean = false,
    isDropTarget: Boolean,
    draggedMemberTabId: String?,
    onOpenGroup: () -> Unit,
    onEditGroup: () -> Unit,
    onCloseGroup: () -> Unit,
    onCloseMemberTab: (String) -> Unit,
    onImmediateDragStart: () -> Unit,
    onImmediateDrag: (Offset) -> Unit,
    onImmediateDragEnd: () -> Unit,
    onMemberTabDragStart: (BrowserTab, Rect) -> Unit,
    onMemberTabDrag: (Offset) -> Unit,
    onMemberTabDragEnd: () -> Unit,
    preferences: BrowserPreferences = BrowserPreferences(),
    homeWallpapers: Map<String, HomeWallpaperData> = emptyMap(),
    customWallpaperBitmap: Bitmap? = null
) {
    val groupPalette = entry.group.colorOption.resolveTonalColors()
    val containsActive = entry.tabs.any { it.id == activeTabId }
    val containsIncognito = entry.tabs.any { it.isPrivate }
    val isEmphasized = if (isSelectionMode) isSelectionChecked else containsActive
    val totalTabs = entry.tabs.size
    val hasMoreThanFour = totalTabs > 4
    val overflowRemainingCount = totalTabs - 3

    val cardShape = rememberMorphingCornerShape(
        targetRadius = when {
            isDropTarget -> 32.dp
            isSelectionMode && isSelectionChecked -> 28.dp
            else -> 26.dp
        },
        label = "GroupGridCardShape_${entry.group.id}"
    )

    val containerColor by animateColorAsState(
        targetValue = when {
            isDropTarget -> MaterialTheme.colorScheme.tertiaryContainer
            isSelectionMode && isSelectionChecked -> MaterialTheme.colorScheme.primaryContainer
            isSelectionMode -> groupPalette.subtleSurface
            containsActive -> groupPalette.container
            else -> groupPalette.subtleSurface
        },
        animationSpec = ApexMotion.effectsSpec(),
        label = "GroupGridCardBg_${entry.group.id}"
    )

    Card(
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(
            width = if (isDropTarget || isEmphasized) 2.5.dp else 1.5.dp,
            color = when {
                isDropTarget -> MaterialTheme.colorScheme.tertiary
                isSelectionMode && isSelectionChecked -> MaterialTheme.colorScheme.primary
                isSelectionMode -> groupPalette.border
                containsActive -> groupPalette.accent
                else -> groupPalette.border
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(262.dp)
            .clickable(onClick = onOpenGroup)
            .testTag("grid_group_card_${entry.group.id}")
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(11.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(24.dp)
                        .let { baseModifier ->
                            if (!isSelectionMode) {
                                baseModifier.pointerInput(entry.group.id) {
                                    detectDragGestures(
                                        onDragStart = { onImmediateDragStart() },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            onImmediateDrag(dragAmount)
                                        },
                                        onDragEnd = { onImmediateDragEnd() },
                                        onDragCancel = { onImmediateDragEnd() }
                                    )
                                }
                            } else {
                                baseModifier
                            }
                        }
                ) {
                    Icon(
                        imageVector = Icons.Filled.DragIndicator,
                        contentDescription = stringResource(R.string.cd_drag_tab),
                        tint = groupPalette.onContainer.copy(alpha = 0.72f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(groupPalette.accent)
                )
                Spacer(modifier = Modifier.width(6.dp))

                if (containsIncognito) {
                    IncognitoGlassesIcon(
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                }

                Text(
                    text = "${entry.group.title} ($totalTabs)",
                    style = EmphasizedTypography.activeTabTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (isSelectionMode) {
                    Surface(
                        shape = CircleShape,
                        color = if (isSelectionChecked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f)
                        },
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable(
                                role = Role.Checkbox,
                                onClick = onOpenGroup
                            )
                            .testTag("select_group_checkbox_${entry.group.id}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isSelectionChecked) {
                                    Icons.Filled.CheckCircle
                                } else {
                                    Icons.Outlined.RadioButtonUnchecked
                                },
                                contentDescription = null,
                                tint = if (isSelectionChecked) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                } else {
                    IconButton(
                        onClick = onEditGroup,
                        modifier = Modifier
                            .size(26.dp)
                            .testTag("edit_group_button_${entry.group.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.cd_edit_group),
                            tint = groupPalette.accent,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    IconButton(
                        onClick = onCloseGroup,
                        modifier = Modifier
                            .size(26.dp)
                            .testTag("close_group_button_${entry.group.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.cd_close_tab),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    val tab0 = entry.tabs.getOrNull(0)
                    GroupMiniSlotTile(
                        tab = tab0,
                        groupPalette = groupPalette,
                        overflowCount = null,
                        isActiveTab = tab0?.id == activeTabId,
                        isSelectionMode = isSelectionMode,
                        isBeingDraggedOut = tab0 != null && tab0.id == draggedMemberTabId,
                        onOpenGroup = onOpenGroup,
                        onCloseTab = onCloseMemberTab,
                        onDragOutStart = onMemberTabDragStart,
                        onDragOutMove = onMemberTabDrag,
                        onDragOutEnd = onMemberTabDragEnd,
                        preferences = preferences,
                        wallpaperData = tab0?.let { homeWallpapers[it.homeWallpaperSeed] },
                        customWallpaperBitmap = customWallpaperBitmap,
                        modifier = Modifier.weight(1f)
                    )
                    val tab1 = entry.tabs.getOrNull(1)
                    GroupMiniSlotTile(
                        tab = tab1,
                        groupPalette = groupPalette,
                        overflowCount = null,
                        isActiveTab = tab1?.id == activeTabId,
                        isSelectionMode = isSelectionMode,
                        isBeingDraggedOut = tab1 != null && tab1.id == draggedMemberTabId,
                        onOpenGroup = onOpenGroup,
                        onCloseTab = onCloseMemberTab,
                        onDragOutStart = onMemberTabDragStart,
                        onDragOutMove = onMemberTabDrag,
                        onDragOutEnd = onMemberTabDragEnd,
                        preferences = preferences,
                        wallpaperData = tab1?.let { homeWallpapers[it.homeWallpaperSeed] },
                        customWallpaperBitmap = customWallpaperBitmap,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    val tab2 = entry.tabs.getOrNull(2)
                    GroupMiniSlotTile(
                        tab = tab2,
                        groupPalette = groupPalette,
                        overflowCount = null,
                        isActiveTab = tab2?.id == activeTabId,
                        isSelectionMode = isSelectionMode,
                        isBeingDraggedOut = tab2 != null && tab2.id == draggedMemberTabId,
                        onOpenGroup = onOpenGroup,
                        onCloseTab = onCloseMemberTab,
                        onDragOutStart = onMemberTabDragStart,
                        onDragOutMove = onMemberTabDrag,
                        onDragOutEnd = onMemberTabDragEnd,
                        preferences = preferences,
                        wallpaperData = tab2?.let { homeWallpapers[it.homeWallpaperSeed] },
                        customWallpaperBitmap = customWallpaperBitmap,
                        modifier = Modifier.weight(1f)
                    )
                    if (hasMoreThanFour) {
                        GroupMiniSlotTile(
                            tab = null,
                            groupPalette = groupPalette,
                            overflowCount = overflowRemainingCount,
                            isActiveTab = false,
                            isSelectionMode = isSelectionMode,
                            isBeingDraggedOut = false,
                            onOpenGroup = onOpenGroup,
                            onCloseTab = onCloseMemberTab,
                            onDragOutStart = onMemberTabDragStart,
                            onDragOutMove = onMemberTabDrag,
                            onDragOutEnd = onMemberTabDragEnd,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("group_overflow_count_slot_${entry.group.id}")
                        )
                    } else {
                        val tab3 = entry.tabs.getOrNull(3)
                        GroupMiniSlotTile(
                            tab = tab3,
                            groupPalette = groupPalette,
                            overflowCount = null,
                            isActiveTab = tab3?.id == activeTabId,
                            isSelectionMode = isSelectionMode,
                            isBeingDraggedOut = tab3 != null && tab3.id == draggedMemberTabId,
                            onOpenGroup = onOpenGroup,
                            onCloseTab = onCloseMemberTab,
                            onDragOutStart = onMemberTabDragStart,
                            onDragOutMove = onMemberTabDrag,
                            onDragOutEnd = onMemberTabDragEnd,
                            preferences = preferences,
                            wallpaperData = tab3?.let { homeWallpapers[it.homeWallpaperSeed] },
                            customWallpaperBitmap = customWallpaperBitmap,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("group_fourth_tab_slot_${entry.group.id}")
                        )
                    }
                }
            }

            if (isDropTarget) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.tab_group_drop_hint),
                    style = EmphasizedTypography.badgeCounter,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
private fun GroupMiniSlotTile(
    tab: BrowserTab?,
    groupPalette: TabGroupTonalColors,
    overflowCount: Int?,
    isActiveTab: Boolean,
    isSelectionMode: Boolean = false,
    isBeingDraggedOut: Boolean,
    onOpenGroup: () -> Unit,
    onCloseTab: (String) -> Unit,
    onDragOutStart: (BrowserTab, Rect) -> Unit,
    onDragOutMove: (Offset) -> Unit,
    onDragOutEnd: () -> Unit,
    preferences: BrowserPreferences = BrowserPreferences(),
    wallpaperData: HomeWallpaperData? = null,
    customWallpaperBitmap: Bitmap? = null,
    modifier: Modifier = Modifier
) {
    when {
        overflowCount != null -> {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = groupPalette.accent,
                modifier = modifier.fillMaxSize()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "+$overflowCount",
                        style = EmphasizedTypography.activeTabTitle,
                        color = groupPalette.onAccent
                    )
                }
            }
        }

        tab != null -> {
            var slotBounds by remember { mutableStateOf<Rect?>(null) }
            val tabPalette = tab.tabColor?.resolveTonalColors() ?: groupPalette

            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (isActiveTab) {
                    tabPalette.container
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLowest
                },
                border = BorderStroke(
                    width = if (isActiveTab) 1.5.dp else 0.8.dp,
                    color = if (isActiveTab) {
                        tabPalette.accent
                    } else {
                        tabPalette.border.copy(alpha = 0.70f)
                    }
                ),
                modifier = modifier
                    .fillMaxSize()
                    .alpha(if (isBeingDraggedOut) 0.32f else 1f)
                    .onGloballyPositioned { coords ->
                        slotBounds = coords.boundsInRoot()
                    }
                    .clickable(onClick = onOpenGroup)
                    .let { baseModifier ->
                        if (!isSelectionMode) {
                            baseModifier.pointerInput(tab.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        val rect = slotBounds
                                        if (rect != null) {
                                            onDragOutStart(tab, rect)
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        onDragOutMove(dragAmount)
                                    },
                                    onDragEnd = { onDragOutEnd() },
                                    onDragCancel = { onDragOutEnd() }
                                )
                            }
                        } else {
                            baseModifier
                        }
                    }
                    .testTag("group_mini_tab_${tab.id}")
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (tab.previewThumbnail != null && !tab.isHomePage && !tab.isPrivate) {
                        Image(
                            bitmap = tab.previewThumbnail.asImageBitmap(),
                            contentDescription = tab.title,
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.TopCenter,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.scrim.copy(alpha = 0.78f)
                                        )
                                    )
                                )
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (com.example.ui.theme.LocalApexDarkTheme.current == true) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLowest
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else if (tab.isHomePage) {
                        val isDarkTheme = LocalApexDarkTheme.current ?: isSystemInDarkTheme()
                        val isBwSolidMode = preferences.homeWallpaperMode == HomeWallpaperMode.BLACK_AND_WHITE
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
                        val solidBwColor = if (isDarkTheme) Color.Black else Color.White
                        val hasPhoto = activePhotoBitmap != null

                        Box(modifier = Modifier.fillMaxSize()) {
                            if (isBwSolidMode) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(solidBwColor)
                                        .testTag("tab_preview_wallpaper_bw_${tab.id}")
                                )
                            } else if (activePhotoBitmap != null) {
                                val imageBitmap = remember(activePhotoBitmap) {
                                    activePhotoBitmap.asImageBitmap()
                                }
                                Image(
                                    bitmap = imageBitmap,
                                    contentDescription = wallpaperData?.author?.let { "Wallpaper by $it" }
                                        ?: "Custom home wallpaper preview",
                                    contentScale = ContentScale.Crop,
                                    colorFilter = bwColorFilter,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("tab_preview_wallpaper_image_${tab.id}")
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Black.copy(alpha = 0.22f),
                                                    Color.Black.copy(alpha = 0.48f)
                                                )
                                            )
                                        )
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            brush = Brush.verticalGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                                                    MaterialTheme.colorScheme.surfaceContainerLow
                                                )
                                            )
                                        )
                                )
                            }

                            val slotIconTint = when {
                                hasPhoto -> Color.White
                                isBwSolidMode -> if (isDarkTheme) Color.White else Color.Black
                                else -> tabPalette.accent
                            }
                            val slotTextColor = when {
                                hasPhoto -> Color.White
                                isBwSolidMode -> if (isDarkTheme) Color.White else Color.Black
                                else -> MaterialTheme.colorScheme.onSurface
                            }

                            Column(
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Home,
                                    contentDescription = null,
                                    tint = slotIconTint,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = if (tab.title.equals("Home", ignoreCase = true)) "New tab" else tab.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = slotTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            TabCardFavicon(
                                tab = tab,
                                isSelected = isActiveTab,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                accentOverride = tabPalette.accent,
                                size = 16.dp
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Top-right close button for this tab inside the group card
                    if (!isSelectionMode) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(3.dp)
                                .size(18.dp)
                                .clip(CircleShape)
                                .clickable(
                                    role = Role.Button,
                                    onClick = { onCloseTab(tab.id) }
                                )
                                .testTag("group_mini_tab_close_${tab.id}")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.cd_close_tab),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        else -> {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.45f),
                modifier = modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
internal fun ExpandedGroupModal(
    groupEntry: TabGridEntry.GroupTab,
    activeTabId: String,
    onSelectTab: (String) -> Unit,
    onEditGroup: () -> Unit,
    onAddTabToGroup: () -> Unit,
    onUngroupTab: (String) -> Unit,
    onCycleTabColor: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onDismiss: () -> Unit,
    preferences: BrowserPreferences = BrowserPreferences(),
    homeWallpapers: Map<String, HomeWallpaperData> = emptyMap(),
    customWallpaperBitmap: Bitmap? = null
) {
    val groupPalette = groupEntry.group.colorOption.resolveTonalColors()

    var modalBounds by remember { mutableStateOf<Rect?>(null) }
    var dropZoneBounds by remember { mutableStateOf<Rect?>(null) }
    val rowBoundsMap = remember { mutableStateMapOf<String, Rect>() }

    var draggedTabId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    val isHoveringUngroupZone = remember(draggedTabId, dragOffset, dropZoneBounds, modalBounds) {
        val currentId = draggedTabId ?: return@remember false
        val startRect = rowBoundsMap[currentId] ?: return@remember false
        val center = startRect.center + dragOffset
        val inDropZone = dropZoneBounds?.contains(center) == true
        val outsideModal = modalBounds?.let { !it.contains(center) } == true
        val draggedFarHorizontally = abs(dragOffset.x) > 200f
        inDropZone || outsideModal || draggedFarHorizontally
    }

    fun finishModalRowDrag() {
        val id = draggedTabId
        if (id != null && isHoveringUngroupZone) {
            onUngroupTab(id)
        }
        draggedTabId = null
        dragOffset = Offset.Zero
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.52f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            border = BorderStroke(2.dp, groupPalette.accent),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 540.dp)
                .padding(20.dp)
                .onGloballyPositioned { coords ->
                    modalBounds = coords.boundsInRoot()
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* consume */ }
                .testTag("expanded_group_modal")
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onEditGroup)
                            .padding(vertical = 4.dp)
                            .testTag("expanded_group_rename_header")
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = groupPalette.container,
                            border = BorderStroke(1.5.dp, groupPalette.accent),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.FolderCopy,
                                    contentDescription = null,
                                    tint = groupPalette.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = groupEntry.group.title,
                                    style = EmphasizedTypography.headlineHero,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = stringResource(R.string.cd_edit_group),
                                    tint = groupPalette.accent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (groupEntry.tabs.any { it.isPrivate }) {
                                    IncognitoGlassesIcon(
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                Text(
                                    text = "${groupEntry.tabs.size} tabs • ${groupEntry.group.colorOption.label}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalButton(
                            onClick = onAddTabToGroup,
                            modifier = Modifier.testTag("group_add_tab_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Tab")
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_close_tab))
                        }
                    }
                }

                // Drag-to-Remove-From-Group Drop Zone
                val dropZoneBg by animateColorAsState(
                    targetValue = if (isHoveringUngroupZone) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
                    },
                    animationSpec = ApexMotion.effectsSpec(),
                    label = "ModalUngroupDropZoneBg"
                )
                val dropZoneBorder = if (isHoveringUngroupZone) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = dropZoneBg,
                    border = BorderStroke(
                        width = if (isHoveringUngroupZone) 2.dp else 1.dp,
                        color = dropZoneBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            dropZoneBounds = coords.boundsInRoot()
                        }
                        .testTag("expanded_group_ungroup_drop_zone")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CallSplit,
                            contentDescription = null,
                            tint = if (isHoveringUngroupZone) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isHoveringUngroupZone) {
                                stringResource(R.string.tab_group_drop_ungroup_hint)
                            } else {
                                stringResource(R.string.tab_group_drag_out_subtitle)
                            },
                            style = if (isHoveringUngroupZone) {
                                EmphasizedTypography.activeTabTitle
                            } else {
                                MaterialTheme.typography.labelMedium
                            },
                            color = if (isHoveringUngroupZone) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(GroupedListSpacing),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 280.dp)
                ) {
                    itemsIndexed(
                        items = groupEntry.tabs,
                        key = { _, tab -> tab.id }
                    ) { index, tab ->
                        val isSelected = tab.id == activeTabId
                        val isRowDragged = draggedTabId == tab.id
                        val tabPalette = (tab.tabColor ?: groupEntry.group.colorOption).resolveTonalColors()
                        val itemShape = groupedListItemShape(
                            index = index,
                            totalCount = groupEntry.tabs.size,
                            outerRadius = 22.dp,
                            innerRadius = 5.dp
                        )

                        SwipeToDeleteTabContainer(
                            tabId = tab.id,
                            enabled = draggedTabId == null,
                            onDelete = { onCloseTab(tab.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isRowDragged) 30f else 0f)
                        ) {
                            Surface(
                                shape = itemShape,
                                color = if (isSelected) {
                                    tabPalette.container
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                border = if (isSelected || isRowDragged) {
                                    BorderStroke(1.5.dp, tabPalette.accent)
                                } else {
                                    null
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned { coords ->
                                        if (!isRowDragged) {
                                            rowBoundsMap[tab.id] = coords.boundsInRoot()
                                        }
                                    }
                                    .graphicsLayer {
                                        if (isRowDragged) {
                                            translationX = dragOffset.x
                                            translationY = dragOffset.y
                                            scaleX = 1.03f
                                            scaleY = 1.03f
                                            alpha = 0.94f
                                        }
                                    }
                                    .clip(itemShape)
                                    .clickable { onSelectTab(tab.id) }
                                    .testTag("group_modal_tab_row_${tab.id}")
                                    .pointerInput(tab.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggedTabId = tab.id
                                            dragOffset = Offset.Zero
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffset += dragAmount
                                        },
                                        onDragEnd = { finishModalRowDrag() },
                                        onDragCancel = { finishModalRowDrag() }
                                    )
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .pointerInput(tab.id) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    draggedTabId = tab.id
                                                    dragOffset = Offset.Zero
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragOffset += dragAmount
                                                },
                                                onDragEnd = { finishModalRowDrag() },
                                                onDragCancel = { finishModalRowDrag() }
                                            )
                                        }
                                        .testTag("modal_drag_handle_${tab.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DragIndicator,
                                        contentDescription = stringResource(R.string.cd_drag_tab),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                val modalRowWallpaper = if (tab.isHomePage) {
                                    when (preferences.homeWallpaperMode) {
                                        HomeWallpaperMode.ONLINE_API -> homeWallpapers[tab.homeWallpaperSeed]?.bitmap
                                        HomeWallpaperMode.CUSTOM_PHONE -> customWallpaperBitmap
                                        HomeWallpaperMode.BLACK_AND_WHITE -> null
                                    }
                                } else {
                                    null
                                }
                                val isDarkTheme = LocalApexDarkTheme.current ?: isSystemInDarkTheme()
                                val isBwSolidModal = tab.isHomePage &&
                                    preferences.homeWallpaperMode == HomeWallpaperMode.BLACK_AND_WHITE
                                val modalBwFilter = remember(preferences.homeWallpaperBlackAndWhite) {
                                    if (preferences.homeWallpaperBlackAndWhite) {
                                        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                                    } else {
                                        null
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = when {
                                        isBwSolidModal -> if (isDarkTheme) Color.Black else Color.White
                                        isSelected -> tabPalette.accent
                                        else -> tabPalette.container
                                    },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onCycleTabColor(tab.id) }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (modalRowWallpaper != null) {
                                            val rowBmp = remember(modalRowWallpaper) {
                                                modalRowWallpaper.asImageBitmap()
                                            }
                                            Image(
                                                bitmap = rowBmp,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                colorFilter = modalBwFilter,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .testTag("group_modal_tab_wallpaper_${tab.id}")
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color.Black.copy(alpha = 0.28f))
                                            )
                                        }
                                        Icon(
                                            imageVector = if (tab.isHomePage) Icons.Outlined.Home else Icons.Filled.Public,
                                            contentDescription = stringResource(R.string.cd_cycle_tab_color),
                                            tint = when {
                                                modalRowWallpaper != null -> Color.White
                                                isBwSolidModal -> if (isDarkTheme) Color.White else Color.Black
                                                isSelected -> tabPalette.onAccent
                                                else -> tabPalette.accent
                                            },
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tab.title,
                                        style = EmphasizedTypography.activeTabTitle,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = tab.url,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { onUngroupTab(tab.id) },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CallSplit,
                                        contentDescription = stringResource(R.string.tab_group_ungroup),
                                        tint = tabPalette.accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onCloseTab(tab.id) },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.cd_close_tab),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
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
