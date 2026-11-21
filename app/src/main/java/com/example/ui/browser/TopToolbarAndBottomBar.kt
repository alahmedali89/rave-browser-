package com.example.ui.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import com.example.R
import com.example.automation.AutomationToolbarStatus
import com.example.data.preferences.BrowserPreferences
import com.example.ui.theme.ApexMotion
import com.example.ui.theme.EmphasizedTypography
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.rememberMorphingCornerShape
import com.example.ui.viewmodel.BrowserTab
import com.example.ui.viewmodel.NetworkStatusBannerState
import java.net.URI
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * Status bar connectivity indicator:
 * - Offline: Deep theme-harmonized slate status bar + slim row with CloudOff icon and "No internet connection"
 * - Back online: Rich theme-primary status bar + slim row with Globe icon and "Back online"
 * - Hidden: Normal surface status bar inset without the connectivity row
 */
@Composable
fun StatusBarConnectivityHeader(
    networkBannerState: NetworkStatusBannerState,
    defaultContainerColor: Color = MaterialTheme.colorScheme.surface,
    modifier: Modifier = Modifier
) {
    val reducedMotion = LocalReducedMotion.current
    val isBannerVisible = networkBannerState != NetworkStatusBannerState.HIDDEN
    val offlineBannerColor = androidx.compose.ui.graphics.lerp(
        MaterialTheme.colorScheme.surfaceTint,
        MaterialTheme.colorScheme.scrim,
        0.72f
    )
    val onlineBannerColor = androidx.compose.ui.graphics.lerp(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.scrim,
        0.34f
    )
    val bannerContentColor = androidx.compose.ui.graphics.lerp(
        MaterialTheme.colorScheme.primary,
        if (com.example.ui.theme.LocalApexDarkTheme.current == true) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        0.88f
    )
    val targetBgColor = when (networkBannerState) {
        NetworkStatusBannerState.OFFLINE -> offlineBannerColor
        NetworkStatusBannerState.BACK_ONLINE -> onlineBannerColor
        NetworkStatusBannerState.HIDDEN -> defaultContainerColor
    }
    val animatedBgColor by animateColorAsState(
        targetValue = targetBgColor,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 220),
        label = "StatusBarConnectivityBgColor"
    )

    var lastVisibleState by remember {
        mutableStateOf(
            if (networkBannerState != NetworkStatusBannerState.HIDDEN) {
                networkBannerState
            } else {
                NetworkStatusBannerState.OFFLINE
            }
        )
    }
    if (networkBannerState != NetworkStatusBannerState.HIDDEN) {
        lastVisibleState = networkBannerState
    }

    Surface(
        color = animatedBgColor,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
            )
            AnimatedVisibility(
                visible = isBannerVisible,
                enter = if (reducedMotion) {
                    fadeIn()
                } else {
                    expandVertically(animationSpec = tween(200)) + fadeIn(animationSpec = tween(180))
                },
                exit = if (reducedMotion) {
                    fadeOut()
                } else {
                    shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(160))
                }
            ) {
                val activeBannerState = if (networkBannerState != NetworkStatusBannerState.HIDDEN) {
                    networkBannerState
                } else {
                    lastVisibleState
                }
                val isOffline = activeBannerState == NetworkStatusBannerState.OFFLINE
                val specificTag = if (isOffline) {
                    "status_bar_offline_banner"
                } else {
                    "status_bar_online_banner"
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .testTag("status_bar_connectivity_banner")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .testTag(specificTag)
                    ) {
                        Icon(
                            imageVector = if (isOffline) {
                                Icons.Outlined.CloudOff
                            } else {
                                Icons.Outlined.Public
                            },
                            contentDescription = null,
                            tint = bannerContentColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(
                                if (isOffline) {
                                    R.string.status_no_internet_connection
                                } else {
                                    R.string.status_back_online
                                }
                            ),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            color = bannerContentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Formats a web URL into a clean host display string (e.g. "github.com") for the address bar,
 * matching the clean address bar presentation in the user's screenshots.
 */
fun formatCleanAddressBarUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim()
    if (trimmed.isEmpty() || trimmed.startsWith("apex://", ignoreCase = true)) {
        return trimmed
    }
    val parsedHost = runCatching {
        URI(trimmed).host?.removePrefix("www.")?.takeIf { it.isNotBlank() }
    }.getOrNull()
    if (parsedHost != null) {
        return parsedHost
    }
    return trimmed
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
        .ifBlank { trimmed }
}

/**
 * Custom vector site-settings / tune sliders icon (two horizontal lines with opposing circles)
 * matching the left address bar icon in the user's online screenshot.
 */
@Composable
fun PageTuneSettingsIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.105f
        val radius = size.minDimension * 0.145f
        val padX = size.width * 0.12f
        val topY = size.height * 0.33f
        val bottomY = size.height * 0.67f
        val topKnobX = size.width * 0.34f
        val bottomKnobX = size.width * 0.66f

        // Top line segments around top knob
        if (topKnobX - radius > padX) {
            drawLine(
                color = tint,
                start = Offset(padX, topY),
                end = Offset(topKnobX - radius, topY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
        drawLine(
            color = tint,
            start = Offset(topKnobX + radius, topY),
            end = Offset(size.width - padX, topY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = tint,
            radius = radius,
            center = Offset(topKnobX, topY),
            style = Stroke(width = strokeWidth)
        )

        // Bottom line segments around bottom knob
        drawLine(
            color = tint,
            start = Offset(padX, bottomY),
            end = Offset(bottomKnobX - radius, bottomY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        if (bottomKnobX + radius < size.width - padX) {
            drawLine(
                color = tint,
                start = Offset(bottomKnobX + radius, bottomY),
                end = Offset(size.width - padX, bottomY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
        drawCircle(
            color = tint,
            radius = radius,
            center = Offset(bottomKnobX, bottomY),
            style = Stroke(width = strokeWidth)
        )
    }
}

/**
 * Clean Material 3 Top Toolbar with integrated Status Bar Offline/Online indicator:
 * - On Home screen: Displays the clean borderless search bar with selected search engine logo.
 * - On Web pages: Displays the clean address bar pill with Info (offline/HTTP) or Page Tune (online HTTPS) icon and domain.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactTopToolbar(
    visible: Boolean,
    activeTab: BrowserTab,
    activeTabIndex: Int = 0,
    totalTabs: Int = 1,
    onAddressPillClick: () -> Unit,
    onReloadClick: () -> Unit,
    onSwipeToNextTab: () -> Unit = {},
    onSwipeToPreviousTab: () -> Unit = {},
    searchEngine: com.example.data.preferences.SearchEngineOption = com.example.data.preferences.SearchEngineOption.BRAVE,
    isOnline: Boolean = true,
    networkBannerState: NetworkStatusBannerState = NetworkStatusBannerState.HIDDEN,
    automationStatus: AutomationToolbarStatus = AutomationToolbarStatus.IDLE,
    detectedElementsCount: Int = 0,
    onAutomationClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val reducedMotion = LocalReducedMotion.current
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current

    val currentTabIndex by rememberUpdatedState(activeTabIndex)
    val currentTotalTabs by rememberUpdatedState(totalTabs)
    val currentOnNext by rememberUpdatedState(onSwipeToNextTab)
    val currentOnPrev by rememberUpdatedState(onSwipeToPreviousTab)

    val switchThresholdPx = remember(density) { with(density) { 52.dp.toPx() } }
    val flickMinDistancePx = remember(density) { with(density) { 20.dp.toPx() } }
    val maxVisualOffsetPx = remember(density) { with(density) { 76.dp.toPx() } }
    val boundaryMaxOffsetPx = remember(density) { with(density) { 18.dp.toPx() } }

    var rawDragOffsetPx by remember { mutableFloatStateOf(0f) }
    var isDraggingBar by remember { mutableStateOf(false) }

    LaunchedEffect(activeTab.id) {
        if (!isDraggingBar) {
            rawDragOffsetPx = 0f
        }
    }

    val animatedTranslationX by animateFloatAsState(
        targetValue = if (isDraggingBar) rawDragOffsetPx else 0f,
        animationSpec = if (isDraggingBar || reducedMotion) {
            snap()
        } else {
            spring(dampingRatio = 0.82f, stiffness = 700f)
        },
        label = "SearchBarSwipeTranslation"
    )

    val canGoNext = activeTabIndex < totalTabs - 1
    val canGoPrev = activeTabIndex > 0

    val swipeModifier = Modifier.pointerInput(switchThresholdPx) {
        val velocityTracker = VelocityTracker()
        var totalDrag = 0f
        var crossedHaptic = false
        detectHorizontalDragGestures(
            onDragStart = {
                velocityTracker.resetTracking()
                totalDrag = 0f
                crossedHaptic = false
                isDraggingBar = true
            },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                totalDrag += dragAmount

                val hasNext = currentTabIndex < currentTotalTabs - 1
                val hasPrev = currentTabIndex > 0
                val canMoveInDir = (totalDrag < 0f && hasNext) || (totalDrag > 0f && hasPrev)

                rawDragOffsetPx = if (canMoveInDir) {
                    (totalDrag * 0.65f).coerceIn(-maxVisualOffsetPx, maxVisualOffsetPx)
                } else {
                    (totalDrag * 0.2f).coerceIn(-boundaryMaxOffsetPx, boundaryMaxOffsetPx)
                }

                val nowCrossed = canMoveInDir && kotlin.math.abs(totalDrag) >= switchThresholdPx
                if (nowCrossed && !crossedHaptic) {
                    crossedHaptic = true
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                } else if (!nowCrossed) {
                    crossedHaptic = false
                }
            },
            onDragCancel = {
                isDraggingBar = false
                rawDragOffsetPx = 0f
            },
            onDragEnd = {
                val velocityX = velocityTracker.calculateVelocity().x
                val hasNext = currentTabIndex < currentTotalTabs - 1
                val hasPrev = currentTabIndex > 0

                val goNext = hasNext && (
                    totalDrag <= -switchThresholdPx ||
                        (totalDrag <= -flickMinDistancePx && velocityX <= -480f)
                    )
                val goPrev = hasPrev && (
                    totalDrag >= switchThresholdPx ||
                        (totalDrag >= flickMinDistancePx && velocityX >= 480f)
                    )

                isDraggingBar = false
                rawDragOffsetPx = 0f

                when {
                    goNext -> currentOnNext()
                    goPrev -> currentOnPrev()
                }
            }
        )
    }

    val enterTransition = if (reducedMotion) {
        fadeIn()
    } else {
        expandVertically(animationSpec = ApexMotion.spatialSpring()) +
            fadeIn(animationSpec = ApexMotion.effectsSpring())
    }
    val exitTransition = if (reducedMotion) {
        fadeOut()
    } else {
        shrinkVertically(animationSpec = ApexMotion.spatialSpring()) +
            fadeOut(animationSpec = ApexMotion.effectsSpring())
    }

    AnimatedVisibility(
        visible = visible,
        enter = enterTransition,
        exit = exitTransition,
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Status bar + "No internet connection" (Black) / "Back online" (Blue) strip
            StatusBarConnectivityHeader(
                networkBannerState = networkBannerState,
                defaultContainerColor = MaterialTheme.colorScheme.surface
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("material_top_app_bar")
                ) {
                    val dragProgress = (kotlin.math.abs(animatedTranslationX) / (switchThresholdPx * 0.65f))
                        .coerceIn(0f, 1f)
                    val swipingLeft = animatedTranslationX < -4f
                    val swipingRight = animatedTranslationX > 4f
                    val readyNext = swipingLeft && canGoNext && dragProgress >= 0.95f
                    val readyPrev = swipingRight && canGoPrev && dragProgress >= 0.95f

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .then(swipeModifier)
                    ) {
                        // Subtle edge directional tab-switch indicator behind the sliding pill
                        if (swipingLeft && canGoNext) {
                            Surface(
                                shape = CircleShape,
                                color = if (readyNext) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 4.dp)
                                    .size(32.dp)
                                    .alpha(dragProgress)
                                    .testTag("search_swipe_next_indicator")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = stringResource(R.string.cd_swipe_next_tab),
                                        tint = if (readyNext) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        } else if (swipingRight && canGoPrev) {
                            Surface(
                                shape = CircleShape,
                                color = if (readyPrev) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 4.dp)
                                    .size(32.dp)
                                    .alpha(dragProgress)
                                    .testTag("search_swipe_prev_indicator")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                        contentDescription = stringResource(R.string.cd_swipe_previous_tab),
                                        tint = if (readyPrev) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                if (activeTab.isHomePage) {
                                    val homeBarShape = RoundedCornerShape(22.dp)
                                    Surface(
                                        shape = homeBarShape,
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        border = if (activeTab.isPrivate) {
                                            BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)
                                        } else {
                                            BorderStroke(
                                                0.85.dp,
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f)
                                            )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .graphicsLayer { translationX = animatedTranslationX }
                                            .clip(homeBarShape)
                                            .clickable(onClick = onAddressPillClick)
                                            .testTag("home_top_search_bar")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 14.dp)
                                        ) {
                                            SearchEngineLogo(
                                                engine = searchEngine,
                                                size = 22.dp
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = stringResource(R.string.home_search_placeholder_clean),
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (!isOnline || networkBannerState == NetworkStatusBannerState.OFFLINE) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Icon(
                                                    imageVector = Icons.Outlined.CloudOff,
                                                    contentDescription = stringResource(R.string.status_no_internet_connection),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            if (activeTab.isPrivate) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                IncognitoToolbarBadge()
                                            }
                                        }
                                    }
                                } else {
                                    val addressBarShape = RoundedCornerShape(22.dp)
                                    val showOfflineOrInfoIcon = !isOnline ||
                                        networkBannerState == NetworkStatusBannerState.OFFLINE ||
                                        !activeTab.isHttps
                                    Surface(
                                        shape = addressBarShape,
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        border = if (activeTab.isPrivate) {
                                            BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)
                                        } else {
                                            BorderStroke(
                                                0.85.dp,
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f)
                                            )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .graphicsLayer { translationX = animatedTranslationX }
                                            .clip(addressBarShape)
                                            .clickable(onClick = onAddressPillClick)
                                            .testTag("address_bar_container")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 14.dp)
                                        ) {
                                            if (showOfflineOrInfoIcon) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Info,
                                                    contentDescription = stringResource(R.string.cd_page_info),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .testTag("address_bar_info_icon")
                                                )
                                            } else {
                                                PageTuneSettingsIcon(
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .testTag("address_bar_tune_icon")
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(10.dp))

                                            Text(
                                                text = formatCleanAddressBarUrl(activeTab.url),
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (activeTab.isPrivate) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                IncognitoToolbarBadge()
                                            }
                                        }
                                    }
                                }
                            }

                            AutomationToolbarIconButton(
                                status = automationStatus,
                                detectedElementsCount = detectedElementsCount,
                                onClick = onAutomationClick
                            )
                        }
                    }
                }

                if (activeTab.isLoading && activeTab.progress < 1f) {
                    val animatedProgress = androidx.compose.animation.core.animateFloatAsState(
                        targetValue = activeTab.progress.coerceIn(0.04f, 1f),
                        label = "WebPageLoadingProgress"
                    )
                    LinearProgressIndicator(
                        progress = { animatedProgress.value },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp)
                            .testTag("page_loading_indicator")
                    )
                }
            }
        }
    }
}

@Composable
fun AutomationToolbarIconButton(
    status: AutomationToolbarStatus,
    detectedElementsCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reducedMotion = LocalReducedMotion.current
    val pulseTransition = rememberInfiniteTransition(label = "AutomationToolbarPulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (!reducedMotion && (status == AutomationToolbarStatus.RECORDING || status == AutomationToolbarStatus.EXECUTING)) {
            0.45f
        } else {
            1f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AutomationPulseAlpha"
    )

    val containerColor by animateColorAsState(
        targetValue = when (status) {
            AutomationToolbarStatus.IDLE -> MaterialTheme.colorScheme.secondaryContainer
            AutomationToolbarStatus.ELEMENTS_DETECTED -> MaterialTheme.colorScheme.primaryContainer
            AutomationToolbarStatus.RECORDING -> MaterialTheme.colorScheme.errorContainer
            AutomationToolbarStatus.PAUSED -> MaterialTheme.colorScheme.tertiaryContainer
            AutomationToolbarStatus.EXECUTING -> MaterialTheme.colorScheme.primaryContainer
            AutomationToolbarStatus.COMPLETED -> MaterialTheme.colorScheme.primaryContainer
            AutomationToolbarStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
        },
        animationSpec = if (reducedMotion) snap() else ApexMotion.effectsSpring(),
        label = "AutomationButtonContainerColor"
    )

    val iconTint by animateColorAsState(
        targetValue = when (status) {
            AutomationToolbarStatus.IDLE -> MaterialTheme.colorScheme.onSecondaryContainer
            AutomationToolbarStatus.ELEMENTS_DETECTED -> MaterialTheme.colorScheme.onPrimaryContainer
            AutomationToolbarStatus.RECORDING -> MaterialTheme.colorScheme.onErrorContainer
            AutomationToolbarStatus.PAUSED -> MaterialTheme.colorScheme.onTertiaryContainer
            AutomationToolbarStatus.EXECUTING -> MaterialTheme.colorScheme.onPrimaryContainer
            AutomationToolbarStatus.COMPLETED -> MaterialTheme.colorScheme.onPrimaryContainer
            AutomationToolbarStatus.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        },
        animationSpec = if (reducedMotion) snap() else ApexMotion.effectsSpring(),
        label = "AutomationButtonIconTint"
    )

    val shapeRadius = when (status) {
        AutomationToolbarStatus.RECORDING,
        AutomationToolbarStatus.EXECUTING -> 14.dp
        else -> 22.dp
    }
    val shape = rememberMorphingCornerShape(targetRadius = shapeRadius, label = "AutomationToolbarCornerMorph")

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(width = 48.dp, height = 48.dp)
            .minimumInteractiveComponentSize()
    ) {
        Surface(
            shape = shape,
            color = containerColor,
            border = when (status) {
                AutomationToolbarStatus.RECORDING,
                AutomationToolbarStatus.ERROR -> BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.65f))
                AutomationToolbarStatus.ELEMENTS_DETECTED -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                AutomationToolbarStatus.COMPLETED,
                AutomationToolbarStatus.EXECUTING -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                else -> null
            },
            modifier = Modifier
                .size(44.dp)
                .clip(shape)
                .clickable(
                    role = Role.Button,
                    onClick = onClick
                )
                .testTag("toolbar_automation_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (status == AutomationToolbarStatus.EXECUTING) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("toolbar_automation_badge_executing")
                    )
                }

                Icon(
                    imageVector = ApexIcons.MotionSensorActive,
                    contentDescription = stringResource(R.string.cd_toolbar_automation),
                    tint = iconTint,
                    modifier = Modifier
                        .size(22.dp)
                        .alpha(pulseAlpha)
                        .testTag("toolbar_automation_icon")
                )

                when (status) {
                    AutomationToolbarStatus.ELEMENTS_DETECTED -> {
                        val badgeLabel = when {
                            detectedElementsCount > 99 -> "99+"
                            detectedElementsCount > 0 -> detectedElementsCount.toString()
                            else -> "•"
                        }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(3.dp)
                                .testTag("toolbar_automation_badge_elements")
                        ) {
                            Text(
                                text = badgeLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    lineHeight = 10.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    AutomationToolbarStatus.RECORDING -> {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                                .alpha(pulseAlpha)
                                .testTag("toolbar_automation_badge_recording")
                        )
                    }
                    AutomationToolbarStatus.PAUSED -> {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(3.dp)
                                .size(14.dp)
                                .testTag("toolbar_automation_badge_paused")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Pause,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiary,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }
                    AutomationToolbarStatus.COMPLETED -> {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(3.dp)
                                .size(14.dp)
                                .testTag("toolbar_automation_badge_completed")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }
                    AutomationToolbarStatus.ERROR -> {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(3.dp)
                                .size(14.dp)
                                .testTag("toolbar_automation_badge_error")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.PriorityHigh,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onError,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }
                    AutomationToolbarStatus.IDLE,
                    AutomationToolbarStatus.EXECUTING -> Unit
                }
            }
        }
    }
}

@Composable
private fun IncognitoToolbarBadge() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
        modifier = Modifier.testTag("incognito_mode_badge")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            IncognitoGlassesIcon(
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = stringResource(R.string.incognito_badge_label),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

/**
 * Ergonomic Bottom NavigationBar featuring:
 * Home • Bookmarks • Search • Tabs • Hamburger Menu (opens the Browser Menu Bottom Sheet with Settings).
 */
@Composable
fun BraveBottomNavigationBar(
    isHomeActive: Boolean,
    isBookmarksScreenOpen: Boolean,
    isSettingsScreenOpen: Boolean,
    isMenuSheetOpen: Boolean,
    isCurrentWebPageBookmarked: Boolean,
    isTabSwitcherOpen: Boolean,
    isSearchScreenOpen: Boolean,
    openTabCount: Int,
    onHomeClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    onSearchClick: () -> Unit,
    onTabsClick: () -> Unit,
    onMenuClick: () -> Unit,
    onNewTabFromTabsMenu: () -> Unit = {},
    onNewPrivateTabFromTabsMenu: () -> Unit = {},
    onCloseCurrentTabFromTabsMenu: () -> Unit = {},
    isTabsQuickMenuAvailable: Boolean = !isHomeActive,
    isOtherOverlayOpen: Boolean = false,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    val viewConfig = LocalViewConfiguration.current
    val canOpenTabsQuickMenu by rememberUpdatedState(
        isTabsQuickMenuAvailable &&
            !isHomeActive &&
            !isTabSwitcherOpen &&
            !isBookmarksScreenOpen &&
            !isSettingsScreenOpen &&
            !isSearchScreenOpen &&
            !isMenuSheetOpen &&
            !isOtherOverlayOpen
    )
    var isTabsQuickMenuOpen by remember { mutableStateOf(false) }
    var suppressNextTabClick by remember { mutableStateOf(false) }
    val tabsInteractionSource = remember { MutableInteractionSource() }
    val tabsQuickMenuLabel = stringResource(R.string.cd_tab_switcher_more_options)

    LaunchedEffect(isTabsQuickMenuOpen) {
        if (!isTabsQuickMenuOpen) {
            suppressNextTabClick = false
        }
    }

    LaunchedEffect(tabsInteractionSource) {
        tabsInteractionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    suppressNextTabClick = false
                    if (canOpenTabsQuickMenu) {
                        delay(viewConfig.longPressTimeoutMillis)
                        suppressNextTabClick = true
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        isTabsQuickMenuOpen = true
                    }
                }
                is PressInteraction.Cancel -> {
                    suppressNextTabClick = false
                }
                else -> Unit
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(
            thickness = 0.75.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
        )
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("bottom_navigation_bar")
        ) {
        // 1. Home
        val homeSelected = isHomeActive &&
            !isBookmarksScreenOpen &&
            !isSettingsScreenOpen &&
            !isTabSwitcherOpen &&
            !isSearchScreenOpen &&
            !isMenuSheetOpen &&
            !isOtherOverlayOpen

        NavigationBarItem(
            selected = homeSelected,
            onClick = onHomeClick,
            icon = {
                Icon(
                    imageVector = if (homeSelected) Icons.Filled.Home else Icons.Outlined.Home,
                    contentDescription = stringResource(R.string.nav_home)
                )
            },
            label = { Text(stringResource(R.string.nav_home), maxLines = 1) },
            modifier = Modifier.testTag("nav_home_button")
        )

        // 2. Bookmarks
        val bookmarksSelected = isBookmarksScreenOpen || (
            !isHomeActive &&
                isCurrentWebPageBookmarked &&
                !isSearchScreenOpen &&
                !isSettingsScreenOpen &&
                !isMenuSheetOpen &&
                !isTabSwitcherOpen &&
                !isOtherOverlayOpen
            )
        NavigationBarItem(
            selected = bookmarksSelected,
            onClick = onBookmarksClick,
            icon = {
                val bookmarkIcon = when {
                    isBookmarksScreenOpen -> Icons.Filled.Bookmarks
                    isHomeActive ||
                        isSettingsScreenOpen ||
                        isSearchScreenOpen ||
                        isMenuSheetOpen ||
                        isTabSwitcherOpen ||
                        isOtherOverlayOpen -> Icons.Outlined.Bookmarks
                    isCurrentWebPageBookmarked -> Icons.Filled.Bookmark
                    else -> Icons.Outlined.BookmarkAdd
                }
                Icon(
                    imageVector = bookmarkIcon,
                    contentDescription = stringResource(R.string.nav_bookmarks)
                )
            },
            label = { Text(stringResource(R.string.nav_bookmarks), maxLines = 1) },
            modifier = Modifier.testTag("nav_bookmarks_button")
        )

        // 3. Search
        val searchSelected = isSearchScreenOpen &&
            !isBookmarksScreenOpen &&
            !isSettingsScreenOpen &&
            !isTabSwitcherOpen &&
            !isMenuSheetOpen
        NavigationBarItem(
            selected = searchSelected,
            onClick = onSearchClick,
            icon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = stringResource(R.string.nav_search)
                )
            },
            label = { Text(stringResource(R.string.nav_search), maxLines = 1) },
            modifier = Modifier.testTag("nav_search_button")
        )

        // 4. Tabs with square count icon (tap opens Tab Switcher, hold on webpage opens Quick Menu)
        val tabsSelected = isTabSwitcherOpen &&
            !isBookmarksScreenOpen &&
            !isSettingsScreenOpen &&
            !isSearchScreenOpen &&
            !isMenuSheetOpen
        Box(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                NavigationBarItem(
                    selected = tabsSelected,
                    onClick = {
                        if (suppressNextTabClick) {
                            suppressNextTabClick = false
                        } else {
                            onTabsClick()
                        }
                    },
                    interactionSource = tabsInteractionSource,
                    icon = {
                        SquareTabCounterIcon(
                            count = openTabCount,
                            isSelected = tabsSelected
                        )
                    },
                    label = { Text(stringResource(R.string.nav_tabs), maxLines = 1) },
                    modifier = Modifier
                        .semantics {
                            if (canOpenTabsQuickMenu) {
                                onLongClick(label = tabsQuickMenuLabel) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isTabsQuickMenuOpen = true
                                    true
                                }
                            }
                        }
                        .testTag("nav_tabs_button")
                )
            }

            DropdownMenu(
                expanded = isTabsQuickMenuOpen,
                onDismissRequest = {
                    isTabsQuickMenuOpen = false
                    suppressNextTabClick = false
                },
                shape = RoundedCornerShape(18.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .widthIn(min = 210.dp)
                    .testTag("tabs_quick_dropdown_menu")
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tabs_menu_new_tab)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        isTabsQuickMenuOpen = false
                        suppressNextTabClick = false
                        onNewTabFromTabsMenu()
                    },
                    modifier = Modifier.testTag("tabs_quick_menu_new_tab")
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tabs_menu_new_private_tab)) },
                    leadingIcon = {
                        IncognitoGlassesIcon(
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onClick = {
                        isTabsQuickMenuOpen = false
                        suppressNextTabClick = false
                        onNewPrivateTabFromTabsMenu()
                    },
                    modifier = Modifier.testTag("tabs_quick_menu_new_private_tab")
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tabs_menu_close_tab)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        isTabsQuickMenuOpen = false
                        suppressNextTabClick = false
                        onCloseCurrentTabFromTabsMenu()
                    },
                    modifier = Modifier.testTag("tabs_quick_menu_close_tab")
                )
            }
        }

        // 5. Hamburger Menu (opens Browser Menu ModalBottomSheet containing Settings)
        val menuSelected = isMenuSheetOpen || isSettingsScreenOpen
        NavigationBarItem(
            selected = menuSelected,
            onClick = onMenuClick,
            icon = {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = stringResource(R.string.cd_open_menu)
                )
            },
            label = { Text(stringResource(R.string.nav_menu), maxLines = 1) },
            modifier = Modifier.testTag("nav_menu_button")
        )
        }
    }
}

/**
 * Brave & Chrome style square tab counter icon with rounded corners, stroke border,
 * and the open tabs count rendered inside the square.
 */
@Composable
fun SquareTabCounterIcon(
    count: Int,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val displayText = when {
        count > 99 -> ":D"
        count <= 0 -> "0"
        else -> count.toString()
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        border = BorderStroke(2.dp, borderColor),
        modifier = modifier
            .size(22.dp)
            .testTag("square_tab_counter_icon")
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = displayText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = if (displayText.length >= 2) 10.sp else 12.sp,
                    lineHeight = 12.sp,
                    textAlign = TextAlign.Center
                ),
                color = borderColor
            )
        }
    }
}

/**
 * Material 3 Expressive ModalBottomSheet opened from the bottom Hamburger Menu button.
 * Matches the modern M3 Expressive grouped layout:
 * - Top 4-action quick bar (Back, Forward, Share, Refresh)
 * - Optional site/default Zoom control bar (when enabled in Accessibility settings)
 * - Group 1: Bookmark page, Find in page, Desktop site (expressive pill ToggleButton),
 *            Automation (replaces Extensions, uses MotionSensorActive), More (expandable)
 * - Segmented 4-tile bar: History, Bookmarks, Downloads, Passwords
 * - Group 2: Sign in & Settings
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BrowserMenuBottomSheet(
    isHomeActive: Boolean = true,
    canNavigateBack: Boolean = false,
    canNavigateForward: Boolean = false,
    isCurrentPageBookmarked: Boolean = false,
    desktopViewportEnabled: Boolean,
    browsingHistoryCount: Int = 0,
    downloadsCount: Int = 0,
    savedPasswordsCount: Int = 0,
    isDefaultBrowser: Boolean = false,
    isDefaultPromoDismissed: Boolean = false,
    showZoomOption: Boolean = false,
    activeSiteHost: String = "",
    currentZoomPercent: Int = 100,
    hasCustomSiteZoom: Boolean = false,
    onAdjustZoomPercent: (Int) -> Unit = {},
    onResetSiteZoom: () -> Unit = {},
    onBackClick: () -> Unit = {},
    onForwardClick: () -> Unit = {},
    onSharePageClick: () -> Unit = {},
    onBookmarkPageClick: () -> Unit = {},
    onFindInPageClick: () -> Unit = {},
    onOpenAutomationHudClick: () -> Unit = {},
    onOpenAutomationWorkspaceClick: () -> Unit = {},
    onOpenBookmarksClick: () -> Unit = {},
    isProActivated: Boolean = false,
    remainingFreePlaysToday: Int = BrowserPreferences.FREE_DAILY_AUTOMATION_PLAY_LIMIT,
    dailyPlayLimit: Int = BrowserPreferences.FREE_DAILY_AUTOMATION_PLAY_LIMIT,
    onSignInClick: () -> Unit = {},
    onOpenAppearanceClick: () -> Unit = {},
    onDismissDefaultPromo: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onSetAsDefaultBrowserClick: () -> Unit = {},
    onNewTabClick: () -> Unit = {},
    onNewIncognitoTabClick: () -> Unit = {},
    onOpenHistoryClick: () -> Unit = {},
    onOpenDownloadsClick: () -> Unit = {},
    onOpenPasswordsClick: () -> Unit = {},
    onReloadPageClick: () -> Unit = {},
    onToggleDesktopMode: (Boolean) -> Unit,
    onClearBrowsingData: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val density = LocalDensity.current
    var isAutomationExpanded by remember { mutableStateOf(false) }
    var isMoreExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.testTag("hamburger_menu_bottom_sheet")
    ) {
        if (isHomeActive) {
            // Home Page Browser Menu (matches Home Menu layout with M3 Expressive components)
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 16.dp, end = 16.dp, bottom = 20.dp)
            ) {
                if (!isDefaultBrowser && !isDefaultPromoDismissed) {
                    DefaultBrowserPromoCard(
                        onSetAsDefault = {
                            onDismiss()
                            onSetAsDefaultBrowserClick()
                        },
                        onDismissPromo = onDismissDefaultPromo
                    )
                }

                if (showZoomOption) {
                    MenuZoomControlCard(
                        isHomeActive = true,
                        activeSiteHost = activeSiteHost,
                        currentZoomPercent = currentZoomPercent,
                        hasCustomSiteZoom = hasCustomSiteZoom,
                        onAdjustZoomPercent = onAdjustZoomPercent,
                        onResetSiteZoom = onResetSiteZoom
                    )
                }

                // Primary Home Group: New private tab above Automation
                Column(
                    verticalArrangement = Arrangement.spacedBy(GroupedListSpacing),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val homePrivateTabShape = groupedListItemShape(0, 2)
                    Surface(
                        shape = homePrivateTabShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            0.75.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(homePrivateTabShape)
                            .clickable(role = Role.Button) {
                                onDismiss()
                                onNewIncognitoTabClick()
                            }
                            .testTag("menu_sheet_new_incognito_tab")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 56.dp)
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            IncognitoGlassesIcon(
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = stringResource(R.string.tabs_menu_new_private_tab),
                                style = EmphasizedTypography.activeTabTitle,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    val homeAutomationShape = groupedListItemShape(1, 2)
                    Surface(
                        shape = homeAutomationShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            0.75.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(homeAutomationShape)
                            .clickable(role = Role.Button) {
                                onDismiss()
                                onOpenAutomationHudClick()
                            }
                            .testTag("menu_sheet_automation")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 58.dp)
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = ApexIcons.MotionSensorActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = stringResource(R.string.menu_automation_title),
                                style = EmphasizedTypography.activeTabTitle,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 4-Tile M3 Expressive Segmented Quick-Access Row
                MenuQuickTilesRow(
                    onOpenHistoryClick = {
                        onDismiss()
                        onOpenHistoryClick()
                    },
                    onOpenBookmarksClick = {
                        onDismiss()
                        onOpenBookmarksClick()
                    },
                    onOpenDownloadsClick = {
                        onDismiss()
                        onOpenDownloadsClick()
                    },
                    onOpenPasswordsClick = {
                        onDismiss()
                        onOpenPasswordsClick()
                    }
                )

                // Bottom 3-item Grouped List: Sign in, Appearance, Settings
                Column(
                    verticalArrangement = Arrangement.spacedBy(GroupedListSpacing),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MenuSignInGroupedRow(
                        index = 0,
                        totalCount = 3,
                        isProActivated = isProActivated,
                        remainingFreePlaysToday = remainingFreePlaysToday,
                        dailyPlayLimit = dailyPlayLimit,
                        onClick = {
                            onDismiss()
                            onSignInClick()
                        }
                    )

                    val appearanceShape = groupedListItemShape(1, 3)
                    Surface(
                        shape = appearanceShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            0.75.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(appearanceShape)
                            .clickable(role = Role.Button) {
                                onDismiss()
                                onOpenAppearanceClick()
                            }
                            .testTag("menu_sheet_appearance")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 56.dp)
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = stringResource(R.string.menu_appearance),
                                style = EmphasizedTypography.activeTabTitle,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    MenuSettingsGroupedRow(
                        index = 2,
                        totalCount = 3,
                        onClick = {
                            onDismiss()
                            onOpenSettings()
                        }
                    )
                }
            }
        } else {
            // Web Page Browser Menu:
            // - Top Back / Forward / Share / Refresh stays FIXED at top
            // - Sheet height stays FIXED at collapsed height when More or Automation expands
            // - Expanded body items scroll smoothly inside the fixed-height container
            val bodyScrollState = rememberScrollState()
            var collapsedBodyHeightPx by remember { mutableIntStateOf(0) }
            val isAnyExpanded = isMoreExpanded || isAutomationExpanded

            LaunchedEffect(isMoreExpanded) {
                if (isMoreExpanded) {
                    androidx.compose.runtime.withFrameNanos { }
                    val deltaPx = with(density) { 140.dp.roundToPx() }
                    bodyScrollState.animateScrollTo(
                        (bodyScrollState.value + deltaPx).coerceAtMost(bodyScrollState.maxValue)
                    )
                }
            }

            LaunchedEffect(isAutomationExpanded) {
                if (isAutomationExpanded) {
                    androidx.compose.runtime.withFrameNanos { }
                    val deltaPx = with(density) { 110.dp.roundToPx() }
                    bodyScrollState.animateScrollTo(
                        (bodyScrollState.value + deltaPx).coerceAtMost(bodyScrollState.maxValue)
                    )
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 16.dp, end = 16.dp, bottom = 20.dp)
            ) {
                val maxBodyHeight = (maxHeight - 88.dp).coerceAtLeast(220.dp)
                val fixedExpandedHeightDp = if (collapsedBodyHeightPx > 0) {
                    with(density) { collapsedBodyHeightPx.toDp() }.coerceAtMost(maxBodyHeight)
                } else {
                    maxBodyHeight
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // FIXED Top 4-action M3 Expressive Navigation & Share Bar
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                            .testTag("menu_sheet_top_actions")
                    ) {
                        MenuTopActionButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            label = stringResource(R.string.menu_action_back),
                            enabled = canNavigateBack,
                            onClick = {
                                onDismiss()
                                onBackClick()
                            },
                            tag = "menu_sheet_back",
                            modifier = Modifier.weight(1f)
                        )
                        MenuTopActionButton(
                            icon = Icons.AutoMirrored.Filled.ArrowForward,
                            label = stringResource(R.string.menu_action_forward),
                            enabled = canNavigateForward,
                            onClick = {
                                onDismiss()
                                onForwardClick()
                            },
                            tag = "menu_sheet_forward",
                            modifier = Modifier.weight(1f)
                        )
                        MenuTopActionButton(
                            icon = Icons.Outlined.Share,
                            label = stringResource(R.string.menu_action_share),
                            enabled = true,
                            onClick = {
                                onDismiss()
                                onSharePageClick()
                            },
                            tag = "menu_sheet_share",
                            modifier = Modifier.weight(1f)
                        )
                        MenuTopActionButton(
                            icon = Icons.Filled.Refresh,
                            label = stringResource(R.string.menu_action_refresh),
                            enabled = true,
                            onClick = {
                                onDismiss()
                                onReloadPageClick()
                            },
                            tag = "menu_sheet_refresh",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Scrollable menu body with FIXED height once collapsed size is measured
                    val bodyHeightModifier = if (isAnyExpanded) {
                        Modifier.height(fixedExpandedHeightDp)
                    } else {
                        Modifier.heightIn(max = maxBodyHeight)
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(bodyHeightModifier)
                            .onSizeChanged { size ->
                                if (!isAnyExpanded && size.height > 0) {
                                    collapsedBodyHeightPx = size.height
                                }
                            }
                            .verticalScroll(bodyScrollState)
                            .testTag("menu_sheet_scrollable_body")
                    ) {
                        if (showZoomOption) {
                            MenuZoomControlCard(
                                isHomeActive = false,
                                activeSiteHost = activeSiteHost,
                                currentZoomPercent = currentZoomPercent,
                                hasCustomSiteZoom = hasCustomSiteZoom,
                                onAdjustZoomPercent = onAdjustZoomPercent,
                                onResetSiteZoom = onResetSiteZoom
                            )
                        }

                        // Group 1: Bookmark page, Find in page, Desktop site, New private tab, Automation, More (+ expandable sub-items)
                        val secondaryMoreCount = if (isMoreExpanded) {
                            2 + if (!isDefaultBrowser) 1 else 0
                        } else {
                            0
                        }
                        val automationExpandedCount = if (isAutomationExpanded) 2 else 0
                        val group1TotalCount = 6 + automationExpandedCount + secondaryMoreCount

                        Column(
                            verticalArrangement = Arrangement.spacedBy(GroupedListSpacing),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            var group1Idx = 0

                            // 1. Bookmark page
                            val bookmarkShape = groupedListItemShape(group1Idx++, group1TotalCount)
                            Surface(
                                shape = bookmarkShape,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(
                                    0.75.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(bookmarkShape)
                                    .clickable(
                                        role = Role.Button,
                                        onClick = {
                                            onDismiss()
                                            onBookmarkPageClick()
                                        }
                                    )
                                    .testTag("menu_sheet_bookmark_page")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 56.dp)
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isCurrentPageBookmarked) {
                                            Icons.Filled.Star
                                        } else {
                                            Icons.Outlined.StarOutline
                                        },
                                        contentDescription = null,
                                        tint = if (isCurrentPageBookmarked) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.menu_bookmark_page),
                                            style = EmphasizedTypography.activeTabTitle,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isCurrentPageBookmarked) {
                                            Text(
                                                text = stringResource(R.string.menu_bookmarked_subtitle),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }

                            // 2. Find in page
                            val findShape = groupedListItemShape(group1Idx++, group1TotalCount)
                            Surface(
                                shape = findShape,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(
                                    0.75.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(findShape)
                                    .clickable(
                                        role = Role.Button,
                                        onClick = {
                                            onDismiss()
                                            onFindInPageClick()
                                        }
                                    )
                                    .testTag("menu_sheet_find_in_page")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 56.dp)
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = stringResource(R.string.menu_find_in_page),
                                        style = EmphasizedTypography.activeTabTitle,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // 3. Desktop site (M3 Expressive ToggleButton pill showing On / Off)
                            val desktopShape = groupedListItemShape(group1Idx++, group1TotalCount)
                            val toggleDesktop = {
                                val enabling = !desktopViewportEnabled
                                onToggleDesktopMode(enabling)
                                if (enabling) {
                                    onDismiss()
                                }
                            }
                            Surface(
                                shape = desktopShape,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(
                                    0.75.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(desktopShape)
                                    .clickable(role = Role.Switch, onClick = toggleDesktop)
                                    .testTag("menu_sheet_desktop_switch")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 56.dp)
                                        .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DesktopWindows,
                                        contentDescription = null,
                                        tint = if (desktopViewportEnabled) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = stringResource(R.string.menu_desktop_site),
                                        style = EmphasizedTypography.activeTabTitle,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    ToggleButton(
                                        checked = desktopViewportEnabled,
                                        onCheckedChange = { checked ->
                                            onToggleDesktopMode(checked)
                                            if (checked) {
                                                onDismiss()
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .heightIn(min = 36.dp)
                                            .testTag("menu_sheet_desktop_pill")
                                    ) {
                                        Text(
                                            text = if (desktopViewportEnabled) {
                                                stringResource(R.string.menu_toggle_on)
                                            } else {
                                                stringResource(R.string.menu_toggle_off)
                                            },
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                            }

                            // 4. New private tab (directly above Automation)
                            val privateTabShape = groupedListItemShape(group1Idx++, group1TotalCount)
                            Surface(
                                shape = privateTabShape,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(
                                    0.75.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(privateTabShape)
                                    .clickable(role = Role.Button) {
                                        onDismiss()
                                        onNewIncognitoTabClick()
                                    }
                                    .testTag("menu_sheet_new_incognito_tab")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 56.dp)
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    IncognitoGlassesIcon(
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = stringResource(R.string.tabs_menu_new_private_tab),
                                        style = EmphasizedTypography.activeTabTitle,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // 5. Automation (replaces Extensions, uses exact MotionSensorActive icon)
                            val automationChevronRotation by animateFloatAsState(
                                targetValue = if (isAutomationExpanded) 180f else 0f,
                                animationSpec = ApexMotion.spatialSpring(),
                                label = "AutomationChevronRotation"
                            )
                            val automationShape = groupedListItemShape(group1Idx++, group1TotalCount)
                            Surface(
                                shape = automationShape,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(
                                    0.75.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(automationShape)
                                    .clickable(role = Role.Button) {
                                        onDismiss()
                                        onOpenAutomationHudClick()
                                    }
                                    .testTag("menu_sheet_automation")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 62.dp)
                                        .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = ApexIcons.MotionSensorActive,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.menu_automation_title),
                                            style = EmphasizedTypography.activeTabTitle,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = stringResource(R.string.menu_automation_subtitle),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    FilledTonalIconButton(
                                        onClick = { isAutomationExpanded = !isAutomationExpanded },
                                        shapes = IconButtonDefaults.shapes(),
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .size(36.dp)
                                            .testTag("menu_sheet_automation_expand")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.KeyboardArrowDown,
                                            contentDescription = stringResource(R.string.cd_expand_automation),
                                            modifier = Modifier
                                                .size(20.dp)
                                                .graphicsLayer { rotationZ = automationChevronRotation }
                                        )
                                    }
                                }
                            }

                            if (isAutomationExpanded) {
                                MenuSheetExpandableSubRow(
                                    index = group1Idx++,
                                    totalCount = group1TotalCount,
                                    icon = ApexIcons.MotionSensorActive,
                                    title = stringResource(R.string.menu_automation_open_hud),
                                    onClick = {
                                        onDismiss()
                                        onOpenAutomationHudClick()
                                    },
                                    tag = "menu_sheet_automation_hud"
                                )
                                MenuSheetExpandableSubRow(
                                    index = group1Idx++,
                                    totalCount = group1TotalCount,
                                    icon = Icons.Outlined.Layers,
                                    title = stringResource(R.string.menu_automation_open_workspace),
                                    onClick = {
                                        onDismiss()
                                        onOpenAutomationWorkspaceClick()
                                    },
                                    tag = "menu_sheet_automation_workspace"
                                )
                            }

                            // 5. More (expandable secondary browser actions; scrolls inside fixed sheet height)
                            val moreChevronRotation by animateFloatAsState(
                                targetValue = if (isMoreExpanded) 180f else 0f,
                                animationSpec = ApexMotion.spatialSpring(),
                                label = "MoreChevronRotation"
                            )
                            val moreShape = groupedListItemShape(group1Idx++, group1TotalCount)
                            Surface(
                                shape = moreShape,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(
                                    0.75.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(moreShape)
                                    .clickable(role = Role.Button) {
                                        isMoreExpanded = !isMoreExpanded
                                    }
                                    .testTag("menu_sheet_more")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 56.dp)
                                        .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreHoriz,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = stringResource(R.string.menu_more_title),
                                        style = EmphasizedTypography.activeTabTitle,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilledTonalIconButton(
                                        onClick = { isMoreExpanded = !isMoreExpanded },
                                        shapes = IconButtonDefaults.shapes(),
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .size(36.dp)
                                            .testTag("menu_sheet_more_expand")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.KeyboardArrowDown,
                                            contentDescription = stringResource(R.string.cd_expand_more),
                                            modifier = Modifier
                                                .size(20.dp)
                                                .graphicsLayer { rotationZ = moreChevronRotation }
                                        )
                                    }
                                }
                            }

                            if (isMoreExpanded) {
                                MenuSheetExpandableSubRow(
                                    index = group1Idx++,
                                    totalCount = group1TotalCount,
                                    icon = Icons.Filled.Add,
                                    title = stringResource(R.string.menu_new_tab),
                                    onClick = {
                                        onDismiss()
                                        onNewTabClick()
                                    },
                                    tag = "menu_sheet_new_tab"
                                )

                                if (!isDefaultBrowser) {
                                    MenuSheetExpandableSubRow(
                                        index = group1Idx++,
                                        totalCount = group1TotalCount,
                                        icon = Icons.Outlined.Public,
                                        title = stringResource(R.string.menu_set_default_browser),
                                        onClick = {
                                            onDismiss()
                                            onSetAsDefaultBrowserClick()
                                        },
                                        highlighted = true,
                                        tag = "menu_sheet_set_default_browser"
                                    )
                                }

                                MenuSheetExpandableSubRow(
                                    index = group1Idx,
                                    totalCount = group1TotalCount,
                                    icon = Icons.Outlined.CleaningServices,
                                    title = stringResource(R.string.menu_clear_data),
                                    onClick = {
                                        onDismiss()
                                        onClearBrowsingData()
                                    },
                                    isDestructive = true,
                                    tag = "menu_sheet_clear_data"
                                )
                            }
                        }

                        // Middle 4-Tile M3 Expressive Segmented Quick-Access Row
                        MenuQuickTilesRow(
                            onOpenHistoryClick = {
                                onDismiss()
                                onOpenHistoryClick()
                            },
                            onOpenBookmarksClick = {
                                onDismiss()
                                onOpenBookmarksClick()
                            },
                            onOpenDownloadsClick = {
                                onDismiss()
                                onOpenDownloadsClick()
                            },
                            onOpenPasswordsClick = {
                                onDismiss()
                                onOpenPasswordsClick()
                            }
                        )

                        // Group 2: Sign in & Settings
                        Column(
                            verticalArrangement = Arrangement.spacedBy(GroupedListSpacing),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            MenuSignInGroupedRow(
                                index = 0,
                                totalCount = 2,
                                isProActivated = isProActivated,
                                remainingFreePlaysToday = remainingFreePlaysToday,
                                dailyPlayLimit = dailyPlayLimit,
                                onClick = {
                                    onDismiss()
                                    onSignInClick()
                                }
                            )
                            MenuSettingsGroupedRow(
                                index = 1,
                                totalCount = 2,
                                onClick = {
                                    onDismiss()
                                    onOpenSettings()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultBrowserPromoCard(
    onSetAsDefault: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onDismissPromo: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            0.75.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onSetAsDefault)
            .testTag("menu_sheet_default_promo_card")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.menu_default_promo_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.menu_default_promo_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Image(
                painter = painterResource(id = R.drawable.img_app_icon_fg_1793695901318),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MenuZoomControlCard(
    isHomeActive: Boolean,
    activeSiteHost: String,
    currentZoomPercent: Int,
    hasCustomSiteZoom: Boolean,
    onAdjustZoomPercent: (Int) -> Unit,
    onResetSiteZoom: () -> Unit
) {
    val boundedZoom = currentZoomPercent.coerceIn(50, 200)
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            0.75.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("menu_sheet_zoom_row")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.ZoomIn,
                contentDescription = null,
                tint = if (hasCustomSiteZoom || boundedZoom != 100) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.menu_zoom_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!isHomeActive && activeSiteHost.isNotBlank()) {
                    Text(
                        text = activeSiteHost,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (hasCustomSiteZoom) {
                TextButton(
                    onClick = onResetSiteZoom,
                    modifier = Modifier.testTag("menu_sheet_zoom_reset")
                ) {
                    Text(stringResource(R.string.menu_zoom_reset))
                }
            }
            FilledTonalIconButton(
                onClick = { onAdjustZoomPercent((boundedZoom - 5).coerceAtLeast(50)) },
                enabled = boundedZoom > 50,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier
                    .size(38.dp)
                    .testTag("menu_sheet_zoom_out")
            ) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = stringResource(R.string.cd_accessibility_zoom_out),
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = stringResource(R.string.accessibility_zoom_percent_format, boundedZoom),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("menu_sheet_zoom_value")
            )
            FilledTonalIconButton(
                onClick = { onAdjustZoomPercent((boundedZoom + 5).coerceAtMost(200)) },
                enabled = boundedZoom < 200,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier
                    .size(38.dp)
                    .testTag("menu_sheet_zoom_in")
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.cd_accessibility_zoom_in),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun MenuQuickTilesRow(
    onOpenHistoryClick: () -> Unit,
    onOpenBookmarksClick: () -> Unit,
    onOpenDownloadsClick: () -> Unit,
    onOpenPasswordsClick: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(GroupedListSpacing),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("menu_sheet_quick_tiles")
    ) {
        MenuQuickTile(
            index = 0,
            totalCount = 4,
            icon = Icons.Outlined.History,
            label = stringResource(R.string.menu_history),
            onClick = onOpenHistoryClick,
            tag = "menu_sheet_history",
            modifier = Modifier.weight(1f)
        )
        MenuQuickTile(
            index = 1,
            totalCount = 4,
            icon = Icons.Outlined.Bookmarks,
            label = stringResource(R.string.menu_bookmarks),
            onClick = onOpenBookmarksClick,
            tag = "menu_sheet_bookmarks",
            modifier = Modifier.weight(1f)
        )
        MenuQuickTile(
            index = 2,
            totalCount = 4,
            icon = Icons.Outlined.Download,
            label = stringResource(R.string.menu_downloads),
            onClick = onOpenDownloadsClick,
            tag = "menu_sheet_downloads",
            modifier = Modifier.weight(1f)
        )
        MenuQuickTile(
            index = 3,
            totalCount = 4,
            icon = Icons.Outlined.Key,
            label = stringResource(R.string.menu_passwords),
            onClick = onOpenPasswordsClick,
            tag = "menu_sheet_passwords",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MenuSignInGroupedRow(
    index: Int,
    totalCount: Int,
    isProActivated: Boolean,
    remainingFreePlaysToday: Int,
    dailyPlayLimit: Int,
    onClick: () -> Unit
) {
    val signInShape = groupedListItemShape(index, totalCount)
    val clampedRemaining = remainingFreePlaysToday.coerceIn(0, dailyPlayLimit)
    val isExhausted = !isProActivated && clampedRemaining <= 0
    val badgeBg = when {
        isProActivated -> MaterialTheme.colorScheme.primaryContainer
        isExhausted -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val badgeFg = when {
        isProActivated -> MaterialTheme.colorScheme.onPrimaryContainer
        isExhausted -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    val subtitleText = if (isProActivated) {
        stringResource(R.string.menu_pro_active_subtitle)
    } else {
        stringResource(R.string.menu_pro_free_subtitle_format, clampedRemaining, dailyPlayLimit)
    }
    val badgeText = if (isProActivated) {
        stringResource(R.string.menu_pro_badge)
    } else {
        stringResource(R.string.menu_pro_left_badge_format, clampedRemaining, dailyPlayLimit)
    }

    Surface(
        shape = signInShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            0.75.dp,
            if (isProActivated) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(signInShape)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("menu_sheet_sign_in")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 62.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = if (isProActivated) {
                    Icons.Filled.Verified
                } else {
                    Icons.Outlined.WorkspacePremium
                },
                contentDescription = null,
                tint = if (isProActivated) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.menu_sign_in),
                    style = EmphasizedTypography.activeTabTitle,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = badgeBg,
                modifier = Modifier.testTag("menu_sheet_pro_badge")
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = badgeFg,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun MenuSettingsGroupedRow(
    index: Int,
    totalCount: Int,
    onClick: () -> Unit
) {
    val settingsShape = groupedListItemShape(index, totalCount)
    Surface(
        shape = settingsShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            0.75.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(settingsShape)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("menu_sheet_open_settings")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.menu_settings),
                style = EmphasizedTypography.activeTabTitle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MenuTopActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
    val contentTint = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .testTag(tag)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentTint,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentTint,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MenuQuickTile(
    index: Int,
    totalCount: Int,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
    val tileShape = horizontalGroupedTileShape(index = index, totalCount = totalCount)
    Surface(
        shape = tileShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            0.75.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(tileShape)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(tag)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 76.dp)
                .padding(horizontal = 4.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MenuSheetExpandableSubRow(
    index: Int,
    totalCount: Int,
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    isDestructive: Boolean = false,
    tag: String
) {
    val shape = groupedListItemShape(index = index, totalCount = totalCount)
    val iconTint = when {
        isDestructive -> MaterialTheme.colorScheme.error
        highlighted -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
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
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(tag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 54.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                style = EmphasizedTypography.activeTabTitle,
                color = if (isDestructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
