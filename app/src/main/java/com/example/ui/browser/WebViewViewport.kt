package com.example.ui.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.Window
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.concurrent.atomic.AtomicReference
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import com.example.R
import com.example.automation.ActiveDomElementHighlight
import com.example.automation.AmbiguousCandidateInfo
import com.example.automation.DetectedPageElement
import com.example.automation.ElementBoundingBox
import com.example.automation.LocatorCandidate
import com.example.automation.RecordedDomEvent
import com.example.data.preferences.BrowserPreferences
import com.example.engine.BuiltInShowcasePages
import com.example.engine.PerformanceOptimizer
import com.example.engine.PrivacyManager
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.viewmodel.BrowserTab
import java.net.URI
import java.net.URLEncoder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.json.JSONTokener

private const val LAST_LOADED_URL_TAG = -19048102
private const val LAST_HANDLED_REQUEST_ID_TAG = -19048103

private data class WebPageErrorState(
    val failingUrl: String,
    val reason: String,
    val errorCode: Int
)

private fun resolveWebViewCanvasColorArgb(url: String): Int {
    return if (BuiltInShowcasePages.resolveHtml(url) != null) {
        0xFF0D1321.toInt()
    } else {
        android.graphics.Color.WHITE
    }
}

private fun extractHostLower(rawUrl: String): String {
    return runCatching {
        URI(rawUrl).host?.lowercase().orEmpty()
    }.getOrElse {
        rawUrl.substringAfter("://", rawUrl)
            .substringBefore('/')
            .substringBefore(':')
            .lowercase()
    }
}

private fun isLoopbackHost(host: String): Boolean {
    return host == "localhost" ||
        host == "127.0.0.1" ||
        host == "10.0.2.2" ||
        host == "::1" ||
        host == "[::1]"
}

data class WebLongPressTarget(
    val title: String,
    val url: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("ClickableViewAccessibility")
@Composable
fun WebViewViewport(
    tab: BrowserTab,
    preferences: BrowserPreferences,
    privacyManager: PrivacyManager,
    performanceOptimizer: PerformanceOptimizer,
    existingWebView: WebView?,
    onRegisterWebView: (String, WebView) -> Unit,
    onPageStarted: (String, String) -> Unit,
    onUrlCommitted: (String, String) -> Unit = { _, _ -> },
    onProgressChanged: (String, Int) -> Unit,
    onPageFinished: (String, String?, String?, Boolean, Boolean) -> Unit,
    onSubresourceBlocked: (String, String, String, String, Long, Long) -> Unit,
    onHttpsUpgradeTriggered: (String) -> Unit,
    onScrollDelta: (Int) -> Unit = {},
    onScrollPositionChanged: (Int, Int) -> Unit = { dy, _ -> onScrollDelta(dy) },
    onPageLoadFailed: (String, String) -> Unit = { _, _ -> },
    onPageLoadFailedWithReason: (String, String, String) -> Unit = { tabId, url, _ ->
        onPageLoadFailed(tabId, url)
    },
    onRefreshPage: () -> Unit = {},
    onNavigateHome: () -> Unit = {},
    onOpenInNewTab: (String) -> Unit = {},
    onOpenInNewTabInGroup: (String) -> Unit = {},
    onOpenInPrivateTab: (String) -> Unit = {},
    onOpenInNewWindow: (String) -> Unit = {},
    onAddToReadingList: (String, String) -> Unit = { _, _ -> },
    onDownloadFile: (
        url: String,
        suggestedFileName: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long,
        userAgent: String?
    ) -> Unit = { _, _, _, _, _, _ -> },
    onCredentialSubmitted: (
        url: String,
        title: String,
        username: String,
        password: String
    ) -> Unit = { _, _, _, _ -> },
    onToggleCaretBrowsingShortcut: () -> Unit = {},
    onRecordedAutomationEvent: (
        event: RecordedDomEvent,
        sourceTabId: String,
        sourceWebView: WebView?,
        sourceWebViewUrl: String
    ) -> Unit = { _, _, _, _ -> },
    onDomMutationBatch: (
        rawJson: String,
        sourceTabId: String,
        sourceWebView: WebView?,
        sourceWebViewUrl: String
    ) -> Unit = { _, _, _, _ -> },
    activeDomHighlight: ActiveDomElementHighlight? = null,
    scannedElements: List<DetectedPageElement> = emptyList(),
    isScannerOutlineOverlayEnabled: Boolean = true,
    isAutomationRunning: Boolean = false,
    onDismissDomHighlight: () -> Unit = {},
    onToggleScannerOutlines: () -> Unit = {},
    isFindInPageOpen: Boolean = false,
    findInPageQuery: String = "",
    findInPageActiveMatch: Int = 0,
    findInPageTotalMatches: Int = 0,
    onFindInPageResults: (String, Int, Int) -> Unit = { _, _, _ -> },
    onFindQueryChange: (String) -> Unit = {},
    onFindNextMatch: () -> Unit = {},
    onFindPreviousMatch: () -> Unit = {},
    onCloseFindInPage: () -> Unit = {},
    canCapturePreview: Boolean = true,
    onThumbnailCaptured: (String, Bitmap) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val density = LocalDensity.current
    // Keep standard web pages on a crisp white canvas so pages without an explicit body background
    // never render dark text onto a dark/tinted Material surface; use dark canvas for dark built-ins.
    val canvasColorArgb = remember(tab.url) { resolveWebViewCanvasColorArgb(tab.url) }
    val currentPreferences by rememberUpdatedState(preferences)
    val currentTab by rememberUpdatedState(tab)
    val currentOnPageStarted by rememberUpdatedState(onPageStarted)
    val currentOnProgressChanged by rememberUpdatedState(onProgressChanged)
    val currentOnPageFinished by rememberUpdatedState(onPageFinished)
    val currentOnSubresourceBlocked by rememberUpdatedState(onSubresourceBlocked)
    val currentOnHttpsUpgradeTriggered by rememberUpdatedState(onHttpsUpgradeTriggered)
    val currentOnScrollPositionChanged by rememberUpdatedState(onScrollPositionChanged)
    val currentOnPageLoadFailed by rememberUpdatedState(onPageLoadFailed)
    val currentOnPageLoadFailedWithReason by rememberUpdatedState(onPageLoadFailedWithReason)
    val currentOnRefreshPage by rememberUpdatedState(onRefreshPage)
    val currentOnNavigateHome by rememberUpdatedState(onNavigateHome)
    val currentOnUrlCommitted by rememberUpdatedState(onUrlCommitted)
    val currentOnDownloadFile by rememberUpdatedState(onDownloadFile)
    val currentOnCredentialSubmitted by rememberUpdatedState(onCredentialSubmitted)
    val currentOnToggleCaretBrowsingShortcut by rememberUpdatedState(onToggleCaretBrowsingShortcut)
    val currentOnRecordedAutomationEvent by rememberUpdatedState(onRecordedAutomationEvent)
    val currentOnDomMutationBatch by rememberUpdatedState(onDomMutationBatch)
    val currentOnFindInPageResults by rememberUpdatedState(onFindInPageResults)
    val currentOnThumbnailCaptured by rememberUpdatedState(onThumbnailCaptured)

    var lastAppliedScriptGuard by remember(tab.id) {
        mutableStateOf(preferences.scriptGuardEnabled)
    }
    var lastAppliedDesktopMode by remember(tab.id) {
        mutableStateOf(preferences.desktopViewportEnabled)
    }
    var lastAppliedPreload by remember(tab.id) {
        mutableStateOf(preferences.aggressivePreloadEnabled)
    }
    var lastAppliedForceZoom by remember(tab.id) {
        mutableStateOf(preferences.forceEnableZoom)
    }
    var lastAppliedCaretBrowsing by remember(tab.id) {
        mutableStateOf(preferences.caretBrowsingEnabled)
    }

    // Simplified reader mode state
    var simplifiedAvailable by remember(tab.id) { mutableStateOf(false) }
    var simplifiedBannerDismissedForUrl by remember(tab.id) { mutableStateOf<String?>(null) }
    var simplifiedTitle by remember(tab.id) { mutableStateOf("") }
    var simplifiedParagraphs by remember(tab.id) { mutableStateOf<List<String>>(emptyList()) }
    var showSimplifiedReader by remember(tab.id) { mutableStateOf(false) }

    // Long-press context popup & Preview page state
    var longPressTarget by remember(tab.id) { mutableStateOf<WebLongPressTarget?>(null) }
    var previewTarget by remember(tab.id) { mutableStateOf<WebLongPressTarget?>(null) }

    // Material 3 Expressive Pull-to-Refresh state
    val refreshTriggerPx = remember(density) { with(density) { 80.dp.toPx() } }
    val maxPullPx = remember(density) { with(density) { 136.dp.toPx() } }
    val restingRefreshOffsetPx = remember(density) { with(density) { 68.dp.toPx() } }
    val touchSlopPx = remember(context) {
        ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    }

    var pullDistancePx by remember(tab.id) { mutableFloatStateOf(0f) }
    var isActivelyPulling by remember(tab.id) { mutableStateOf(false) }
    var isRefreshing by remember(tab.id) { mutableStateOf(false) }
    var refreshSawLoading by remember(tab.id) { mutableStateOf(false) }
    var refreshStartedAtMillis by remember(tab.id) { mutableLongStateOf(0L) }
    var scrollSnapshotTick by remember(tab.id) { mutableLongStateOf(0L) }
    var interactionSnapshotTick by remember(tab.id) { mutableLongStateOf(0L) }

    // Keep the expressive indicator spinning until page load finishes (with a minimum 650ms showcase spin)
    LaunchedEffect(isRefreshing, tab.isLoading) {
        if (!isRefreshing) {
            refreshSawLoading = false
            return@LaunchedEffect
        }
        if (tab.isLoading) {
            refreshSawLoading = true
        } else {
            if (!refreshSawLoading) {
                delay(1200L)
            }
            val elapsed = System.currentTimeMillis() - refreshStartedAtMillis
            val remaining = (650L - elapsed).coerceAtLeast(120L)
            delay(remaining)
            isRefreshing = false
            pullDistancePx = 0f
            refreshSawLoading = false
        }
    }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            delay(10_000L)
            isRefreshing = false
            pullDistancePx = 0f
            refreshSawLoading = false
        }
    }

    var renderRecoveryNonce by remember(tab.id) { mutableIntStateOf(0) }
    var renderCrashCount by remember(tab.id) { mutableIntStateOf(0) }
    var mainFrameLoadError by remember(tab.id) { mutableStateOf<WebPageErrorState?>(null) }
    val bypassedHttpsUpgradeHosts = remember(tab.id) { mutableSetOf<String>() }
    var lastUpgradedHttpsPair by remember(tab.id) { mutableStateOf<Pair<String, String>?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    val webView = remember(tab.id, renderRecoveryNonce) {
        val candidateExisting = if (renderRecoveryNonce == 0) existingWebView else null
        (candidateExisting?.also { existing ->
            (existing.parent as? ViewGroup)?.removeView(existing)
        } ?: WebView(context)).apply {
            performanceOptimizer.configureFastWebView(
                webView = this,
                scriptGuardEnabled = preferences.scriptGuardEnabled,
                desktopMode = preferences.desktopViewportEnabled,
                aggressiveCaching = preferences.aggressivePreloadEnabled,
                isIncognito = tab.isPrivate,
                textZoomPercent = preferences.effectiveZoomForUrl(tab.url)
            )
            setBackgroundColor(canvasColorArgb)
            overScrollMode = android.view.View.OVER_SCROLL_NEVER
            isNestedScrollingEnabled = true
            setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
                currentOnFindInPageResults(currentTab.id, activeMatchOrdinal, numberOfMatches)
            }
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_F7 && event.action == KeyEvent.ACTION_DOWN) {
                    currentOnToggleCaretBrowsingShortcut()
                    true
                } else {
                    false
                }
            }
            var lastTouchpadNavAt = 0L
            setOnGenericMotionListener { v, event ->
                if (currentPreferences.touchpadSwipeNavigationEnabled &&
                    event.action == MotionEvent.ACTION_SCROLL &&
                    (event.isFromSource(InputDevice.SOURCE_TOUCHPAD) ||
                        event.isFromSource(InputDevice.SOURCE_CLASS_POINTER))
                ) {
                    val hScroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
                    val now = System.currentTimeMillis()
                    if (kotlin.math.abs(hScroll) >= 1.5f && (now - lastTouchpadNavAt) > 550L) {
                        val wv = v as? WebView
                        if (wv != null) {
                            if (hScroll < 0f && wv.canGoBack()) {
                                lastTouchpadNavAt = now
                                wv.goBack()
                                return@setOnGenericMotionListener true
                            } else if (hScroll > 0f && wv.canGoForward()) {
                                lastTouchpadNavAt = now
                                wv.goForward()
                                return@setOnGenericMotionListener true
                            }
                        }
                    }
                }
                false
            }
            runCatching {
                removeJavascriptInterface("ApexPasswordBridge")
            }
            val ownerWebView = this@apply
            runCatching {
                removeJavascriptInterface("ApexAutomationBridge")
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onRecordedEventJson(payloadJson: String?) {
                            val raw = payloadJson?.trim().orEmpty()
                            if (raw.isEmpty()) return
                            val parsed = runCatching {
                                val obj = JSONObject(raw)
                                val isPwd = obj.optBoolean("isPasswordField", false)
                                val candArr = obj.optJSONArray("locatorCandidates")
                                val parsedCandidates = if (candArr != null) {
                                    buildList {
                                        for (i in 0 until candArr.length()) {
                                            LocatorCandidate.fromJson(candArr.optJSONObject(i))?.let { add(it) }
                                        }
                                    }
                                } else emptyList()
                                val fArr = obj.optJSONArray("framePath")
                                val framePath = if (fArr != null) {
                                    buildList { for (i in 0 until fArr.length()) add(fArr.optString(i, "")) }.filter { it.isNotBlank() }
                                } else emptyList()
                                val sArr = obj.optJSONArray("shadowHostPath")
                                val shadowPath = if (sArr != null) {
                                    buildList { for (i in 0 until sArr.length()) add(sArr.optString(i, "")) }.filter { it.isNotBlank() }
                                } else emptyList()
                                val attrObj = obj.optJSONObject("relevantAttributes")
                                val attrs = if (attrObj != null) {
                                    buildMap {
                                        val keys = attrObj.keys()
                                        while (keys.hasNext()) {
                                            val k = keys.next()
                                            if (!k.equals("value", ignoreCase = true) && !k.contains("password", ignoreCase = true)) {
                                                put(k, attrObj.optString(k, ""))
                                            }
                                        }
                                    }
                                } else emptyMap()
                                val ambigArr = obj.optJSONArray("ambiguousCandidates")
                                val ambigCands = if (ambigArr != null) {
                                    buildList {
                                        for (i in 0 until ambigArr.length()) {
                                            AmbiguousCandidateInfo.fromJson(ambigArr.optJSONObject(i))?.let { add(it) }
                                        }
                                    }
                                } else emptyList()
                                RecordedDomEvent(
                                    sessionId = obj.optString("sessionId", "").trim(),
                                    tabId = obj.optString("tabId", "").trim(),
                                    origin = obj.optString("origin", "").trim(),
                                    sequenceNumber = obj.optLong("sequenceNumber", 0L),
                                    eventType = obj.optString("eventType", "").trim(),
                                    targetLabel = obj.optString("targetLabel", ""),
                                    selector = obj.optString("selector", ""),
                                    value = if (isPwd) "" else obj.optString("value", ""),
                                    isDestructive = obj.optBoolean("isDestructive", false),
                                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                                    isPasswordField = isPwd,
                                    fieldRoleHint = obj.optString("fieldRole", ""),
                                    locatorCandidates = parsedCandidates,
                                    framePath = framePath,
                                    shadowHostPath = shadowPath,
                                    parentContext = obj.optString("parentContext", ""),
                                    nearbyText = obj.optString("nearbyText", ""),
                                    relevantAttributes = attrs,
                                    matchCount = obj.optInt("matchCount", 1),
                                    ambiguousCandidates = ambigCands,
                                    tagName = obj.optString("tagName", "").trim().lowercase(),
                                    boundingBox = ElementBoundingBox.fromJson(obj.optJSONObject("boundingBox")),
                                    sensitiveCapturedValue = if (isPwd) obj.optString("vaultSecret", "") else ""
                                )
                            }.getOrNull() ?: RecordedDomEvent(
                                sessionId = "",
                                tabId = "",
                                origin = "",
                                sequenceNumber = 0L,
                                eventType = "",
                                targetLabel = "",
                                selector = "",
                                value = "",
                                isDestructive = false
                            )
                            post {
                                currentOnRecordedAutomationEvent(
                                    parsed,
                                    currentTab.id,
                                    ownerWebView,
                                    ownerWebView.url.orEmpty()
                                )
                            }
                        }

                        @JavascriptInterface
                        fun onDomMutationBatchJson(payloadJson: String?) {
                            val raw = payloadJson?.trim().orEmpty()
                            if (raw.isEmpty()) return
                            post {
                                currentOnDomMutationBatch(
                                    raw,
                                    currentTab.id,
                                    ownerWebView,
                                    ownerWebView.url.orEmpty()
                                )
                            }
                        }

                        @JavascriptInterface
                        fun onRecordedEvent(
                            actionType: String?,
                            targetLabel: String?,
                            selector: String?,
                            value: String?,
                            isDestructive: Boolean
                        ) {
                            val rejectedEvent = RecordedDomEvent(
                                sessionId = "",
                                tabId = "",
                                origin = "",
                                sequenceNumber = 0L,
                                eventType = actionType.orEmpty(),
                                targetLabel = targetLabel.orEmpty(),
                                selector = selector.orEmpty(),
                                value = value.orEmpty(),
                                isDestructive = isDestructive
                            )
                            post {
                                currentOnRecordedAutomationEvent(
                                    rejectedEvent,
                                    currentTab.id,
                                    ownerWebView,
                                    ownerWebView.url.orEmpty()
                                )
                            }
                        }
                    },
                    "ApexAutomationBridge"
                )
            }
            var lastTouchLocalX = 0f
            var lastTouchLocalY = 0f
            var midLoadGuardsAppliedForUrl: String? = null
            var pendingPageFinishedUrl: String? = null
            val liveMainFrameUrl = AtomicReference(tab.url)

            setOnTouchListener { _, event ->
                lastTouchLocalX = event.x
                lastTouchLocalY = event.y
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    interactionSnapshotTick += 1L
                }
                false
            }

            setOnLongClickListener { v ->
                val wv = v as? WebView ?: return@setOnLongClickListener false

                val hitResult = wv.hitTestResult
                if (hitResult.type == WebView.HitTestResult.EDIT_TEXT_TYPE) {
                    return@setOnLongClickListener false
                }

                val hitExtra = hitResult.extra.orEmpty()
                val jsInspectScript = """
                    (function(viewX, viewY, viewWidth, viewHeight) {
                        try {
                            var cssX = (viewWidth > 0 && window.innerWidth > 0) ? (viewX * window.innerWidth / viewWidth) : viewX;
                            var cssY = (viewHeight > 0 && window.innerHeight > 0) ? (viewY * window.innerHeight / viewHeight) : viewY;
                            var el = document.elementFromPoint(cssX, cssY);
                            if (!el) return JSON.stringify({ ignore: false, href: "", text: "" });
                            var tag = (el.tagName || "").toUpperCase();
                            if (tag === "INPUT" || tag === "TEXTAREA" || el.isContentEditable) {
                                return JSON.stringify({ ignore: true });
                            }
                            var anchor = el.closest ? el.closest("a[href]") : null;
                            var selText = "";
                            if (window.getSelection) {
                                selText = (window.getSelection().toString() || "").trim();
                            }
                            if (anchor && anchor.href) {
                                var aText = (anchor.innerText || anchor.textContent || anchor.getAttribute("aria-label") || anchor.title || "").replace(/\s+/g, " ").trim();
                                return JSON.stringify({
                                    ignore: false,
                                    href: anchor.href,
                                    text: aText || selText || document.title || anchor.href
                                });
                            }
                            var nodeText = selText || (el.innerText || el.textContent || "").replace(/\s+/g, " ").trim();
                            if (nodeText.length > 180) {
                                nodeText = nodeText.substring(0, 180).trim();
                            }
                            return JSON.stringify({
                                ignore: false,
                                href: "",
                                text: nodeText || document.title || ""
                            });
                        } catch (e) {
                            return JSON.stringify({ ignore: false, href: "", text: "" });
                        }
                    })($lastTouchLocalX, $lastTouchLocalY, ${wv.width.coerceAtLeast(1)}, ${wv.height.coerceAtLeast(1)});
                """.trimIndent()

                wv.evaluateJavascript(jsInspectScript) { rawJson ->
                    val parsed = parseJsLongPressJson(rawJson)
                    if (parsed?.optBoolean("ignore", false) == true) {
                        return@evaluateJavascript
                    }
                    val jsHref = parsed?.optString("href", "").orEmpty().trim()
                    val jsText = parsed?.optString("text", "").orEmpty().trim()

                    val resolvedHref = when {
                        jsHref.startsWith("http://", true) || jsHref.startsWith("https://", true) -> jsHref
                        hitExtra.startsWith("http://", true) || hitExtra.startsWith("https://", true) -> hitExtra
                        else -> ""
                    }

                    val resolvedTitle = when {
                        jsText.isNotBlank() -> jsText
                        resolvedHref.isNotBlank() -> wv.title?.takeIf { it.isNotBlank() } ?: resolvedHref
                        else -> wv.title?.takeIf { it.isNotBlank() } ?: currentTab.title
                    }

                    val finalUrl = if (resolvedHref.isNotBlank()) {
                        resolvedHref
                    } else if (resolvedTitle.isNotBlank()) {
                        val trimmedText = resolvedTitle.trim()
                        val looksLikeUrl = !trimmedText.contains(' ') && trimmedText.contains('.') && trimmedText.length >= 4
                        if (looksLikeUrl) {
                            if (trimmedText.startsWith("http://", true) || trimmedText.startsWith("https://", true)) {
                                trimmedText
                            } else {
                                "https://$trimmedText"
                            }
                        } else {
                            val encoded = URLEncoder.encode(trimmedText, "UTF-8")
                            currentPreferences.searchEngine.searchUrlTemplate.replace("%s", encoded)
                        }
                    } else {
                        wv.url ?: currentTab.url
                    }

                    if (finalUrl.isNotBlank()) {
                        longPressTarget = WebLongPressTarget(
                            title = resolvedTitle.ifBlank { finalUrl },
                            url = finalUrl
                        )
                    }
                }
                true
            }

            setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                val dy = scrollY - oldScrollY
                if (dy != 0 || scrollY == 0) {
                    currentOnScrollPositionChanged(dy, scrollY)
                    if (dy != 0) {
                        scrollSnapshotTick += 1L
                    }
                }
            }
            setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                if (!url.isNullOrBlank()) {
                    currentOnDownloadFile(
                        url,
                        null,
                        contentDisposition,
                        mimetype,
                        contentLength,
                        userAgent
                    )
                }
            }
            fun finalizeMainFramePageLoad(view: WebView, mainUrl: String) {
                mainFrameLoadError = null
                performanceOptimizer.installWebGlZeroCountGuard(
                    view,
                    currentPreferences.scriptGuardEnabled
                )
                val desiredZoom = currentPreferences.effectiveZoomForUrl(mainUrl)
                if (view.settings.textZoom != desiredZoom) {
                    view.settings.textZoom = desiredZoom
                }
                if (currentPreferences.desktopViewportEnabled) {
                    performanceOptimizer.applyDesktopViewportMode(view, true)
                }
                if (currentPreferences.forceEnableZoom) {
                    performanceOptimizer.applyForceEnableZoom(view, true)
                }
                if (currentPreferences.caretBrowsingEnabled) {
                    performanceOptimizer.applyCaretBrowsing(view, true)
                }
                if (currentPreferences.shieldsEnabled && !currentPreferences.scriptGuardEnabled) {
                    val stealthScript = privacyManager.buildStealthAdblockScript(true)
                    if (stealthScript.isNotEmpty()) {
                        view.evaluateJavascript(stealthScript, null)
                    }
                }
                if (currentPreferences.simplifiedViewEnabled) {
                    view.evaluateJavascript(
                        """
                        (function() {
                            try {
                                var container = document.querySelector('article, main, [role="main"]') || document.body;
                                var paragraphs = Array.from(container.querySelectorAll('p'))
                                    .map(function(p) { return (p.innerText || '').trim(); })
                                    .filter(function(t) { return t.length >= 40; });
                                var totalChars = paragraphs.reduce(function(sum, t) { return sum + t.length; }, 0);
                                return JSON.stringify({
                                    eligible: totalChars >= 220 && paragraphs.length >= 2,
                                    title: (document.querySelector('h1') && document.querySelector('h1').innerText || document.title || '').trim(),
                                    paragraphs: paragraphs.slice(0, 40)
                                });
                            } catch (e) {
                                return JSON.stringify({ eligible: false });
                            }
                        })();
                        """.trimIndent()
                    ) { raw ->
                        val parsed = parseJsLongPressJson(raw)
                        val eligible = parsed?.optBoolean("eligible", false) == true
                        if (eligible && simplifiedBannerDismissedForUrl != mainUrl) {
                            val list = mutableListOf<String>()
                            val arr = parsed?.optJSONArray("paragraphs")
                            if (arr != null) {
                                for (i in 0 until arr.length()) {
                                    val item = arr.optString(i).trim()
                                    if (item.isNotEmpty()) list.add(item)
                                }
                            }
                            simplifiedTitle = parsed?.optString("title", "").orEmpty()
                                .ifBlank { view.title ?: mainUrl }
                            simplifiedParagraphs = list
                            simplifiedAvailable = list.isNotEmpty()
                        } else {
                            simplifiedAvailable = false
                        }
                    }
                } else {
                    simplifiedAvailable = false
                }
                currentOnPageFinished(
                    currentTab.id,
                    view.title,
                    mainUrl,
                    view.canGoBack(),
                    view.canGoForward()
                )
            }

            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: android.os.Message?
                ): Boolean {
                    // Block unsolicited popup and pop-under ad windows
                    if (currentPreferences.shieldsEnabled) {
                        return false
                    }
                    return super.onCreateWindow(view, isDialog, isUserGesture, resultMsg)
                }

                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (view != null && newProgress >= 30) {
                        val activeUrl = view.url.orEmpty()
                        if (activeUrl.isNotBlank() && midLoadGuardsAppliedForUrl != activeUrl) {
                            midLoadGuardsAppliedForUrl = activeUrl
                            performanceOptimizer.installWebGlZeroCountGuard(
                                view,
                                currentPreferences.scriptGuardEnabled
                            )
                            if (currentPreferences.desktopViewportEnabled) {
                                performanceOptimizer.applyDesktopViewportMode(view, true)
                            }
                            if (currentPreferences.forceEnableZoom) {
                                performanceOptimizer.applyForceEnableZoom(view, true)
                            }
                            if (currentPreferences.caretBrowsingEnabled) {
                                performanceOptimizer.applyCaretBrowsing(view, true)
                            }
                            if (currentPreferences.shieldsEnabled && !currentPreferences.scriptGuardEnabled) {
                                val stealthScript = privacyManager.buildStealthAdblockScript(true)
                                if (stealthScript.isNotEmpty()) {
                                    view.evaluateJavascript(stealthScript, null)
                                }
                            }
                        }
                    }
                    if (view != null && newProgress >= 100) {
                        val pendingUrl = pendingPageFinishedUrl
                        val currentMainUrl = view.url?.trim().orEmpty()
                        if (!pendingUrl.isNullOrBlank() &&
                            currentMainUrl.isNotBlank() &&
                            !currentMainUrl.equals("about:blank", ignoreCase = true) &&
                            !currentMainUrl.startsWith("data:", ignoreCase = true)
                        ) {
                            pendingPageFinishedUrl = null
                            finalizeMainFramePageLoad(view, currentMainUrl)
                        }
                    }
                    currentOnProgressChanged(currentTab.id, newProgress)
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val isMainFrame = request?.isForMainFrame ?: true
                    val reqUrl = request?.url?.toString().orEmpty()
                    if (reqUrl.isBlank()) return false

                    val lower = reqUrl.lowercase()
                    if (lower.startsWith("apex://")) {
                        if (isMainFrame) {
                            if (lower == "apex://home") {
                                currentOnNavigateHome()
                            } else if (view != null && BuiltInShowcasePages.resolveHtml(reqUrl) != null) {
                                mainFrameLoadError = null
                                currentOnPageStarted(currentTab.id, reqUrl)
                                loadUrlIntoWebView(
                                    webView = view,
                                    url = reqUrl,
                                    desktopMode = currentPreferences.desktopViewportEnabled,
                                    performanceOptimizer = performanceOptimizer
                                )
                            }
                        }
                        return true
                    }
                    if (!lower.startsWith("http://") &&
                        !lower.startsWith("https://") &&
                        !lower.startsWith("about:") &&
                        !lower.startsWith("data:") &&
                        !lower.startsWith("blob:") &&
                        !lower.startsWith("javascript:")
                    ) {
                        runCatching {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(reqUrl))
                            context.startActivity(intent)
                        }
                        return true
                    }

                    // Block known ad-network / pop-under main-frame hijack redirects when Shields are enabled
                    // without falsely blocking legitimate pages containing keywords like "/ads/" or "/adblock".
                    if (currentPreferences.shieldsEnabled) {
                        val eval = privacyManager.evaluateMainFrameRequest(
                            requestUrl = reqUrl,
                            shieldsEnabled = true
                        )
                        if (eval.blocked) {
                            currentOnSubresourceBlocked(
                                currentTab.id,
                                currentTab.title,
                                eval.matchedDomain,
                                eval.category,
                                eval.estimatedBytesSaved,
                                eval.estimatedTimeSavedMs
                            )
                            return true
                        }
                    }

                    if (isMainFrame && !reqUrl.startsWith("javascript:", ignoreCase = true)) {
                        liveMainFrameUrl.set(reqUrl)
                    }
                    if (isMainFrame && reqUrl.startsWith("http://", ignoreCase = true) && currentPreferences.httpsUpgradeEnabled) {
                        val host = extractHostLower(reqUrl)
                        if (host.isNotEmpty() && !isLoopbackHost(host) && host !in bypassedHttpsUpgradeHosts) {
                            // Break infinite HTTPS -> HTTP server downgrade loops
                            if (lastUpgradedHttpsPair?.first.equals(reqUrl, ignoreCase = true)) {
                                bypassedHttpsUpgradeHosts.add(host)
                                return false
                            }
                            val upgraded = "https://" + reqUrl.substring(7)
                            lastUpgradedHttpsPair = reqUrl to upgraded
                            liveMainFrameUrl.set(upgraded)
                            currentOnHttpsUpgradeTriggered(upgraded)
                            view?.loadUrl(
                                upgraded,
                                performanceOptimizer.desktopRequestHeaders(currentPreferences.desktopViewportEnabled)
                            )
                            return true
                        }
                    }
                    return false
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val reqUrl = request?.url?.toString().orEmpty()
                    if (request?.isForMainFrame == true) {
                        return super.shouldInterceptRequest(view, request)
                    }
                    val activePageUrl = liveMainFrameUrl.get().ifBlank { currentTab.url }
                    val result = privacyManager.evaluateRequest(
                        requestUrl = reqUrl,
                        pageUrl = activePageUrl,
                        shieldsEnabled = currentPreferences.shieldsEnabled
                    )
                    if (result.blocked) {
                        currentOnSubresourceBlocked(
                            currentTab.id,
                            currentTab.title,
                            result.matchedDomain,
                            result.category,
                            result.estimatedBytesSaved,
                            result.estimatedTimeSavedMs
                        )
                        return privacyManager.stealthBlockedResponse(
                            requestUrl = reqUrl,
                            requestHeaders = request?.requestHeaders
                        )
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    midLoadGuardsAppliedForUrl = null
                    pendingPageFinishedUrl = null
                    if (!url.isNullOrBlank() && !url.startsWith("chrome-error://", ignoreCase = true)) {
                        mainFrameLoadError = null
                        liveMainFrameUrl.set(url)
                    }
                    if (view != null) {
                        performanceOptimizer.installWebGlZeroCountGuard(
                            view,
                            currentPreferences.scriptGuardEnabled
                        )
                        if (currentPreferences.desktopViewportEnabled) {
                            performanceOptimizer.applyDesktopViewportMode(view, true)
                        }
                        if (currentPreferences.shieldsEnabled && !currentPreferences.scriptGuardEnabled) {
                            val stealthScript = privacyManager.buildStealthAdblockScript(true)
                            if (stealthScript.isNotEmpty()) {
                                view.evaluateJavascript(stealthScript, null)
                            }
                        }
                    }
                    if (!url.isNullOrBlank()) {
                        currentOnPageStarted(currentTab.id, url)
                    }
                }

                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    super.doUpdateVisitedHistory(view, url, isReload)
                    if (!url.isNullOrBlank()) {
                        liveMainFrameUrl.set(url)
                        if (isReload) {
                            currentOnPageStarted(currentTab.id, url)
                        } else {
                            currentOnUrlCommitted(currentTab.id, url)
                        }
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (view == null) return
                    val mainUrl = view.url?.trim().takeIf { !it.isNullOrBlank() } ?: url?.trim().orEmpty()
                    if (mainUrl.isEmpty() ||
                        mainUrl.equals("about:blank", ignoreCase = true) ||
                        mainUrl.startsWith("data:", ignoreCase = true)
                    ) {
                        return
                    }
                    liveMainFrameUrl.set(mainUrl)
                    if (!url.isNullOrBlank() &&
                        !view.url.isNullOrBlank() &&
                        !url.trim().equals(view.url?.trim(), ignoreCase = true)
                    ) {
                        // Ignore subframe/iframe finish callbacks whose URL differs from the main frame URL
                        return
                    }
                    if (view.progress < 100) {
                        pendingPageFinishedUrl = mainUrl
                        return
                    }
                    pendingPageFinishedUrl = null
                    finalizeMainFramePageLoad(view, mainUrl)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        val reqUrl = request.url?.toString().orEmpty()
                        val code = error?.errorCode ?: -1
                        val reason = error?.description?.toString()?.takeIf { it.isNotBlank() }
                            ?: "Page load failed"
                        // Ignore user-cancelled or superseded loads
                        if (reason.contains("ERR_ABORTED", ignoreCase = true)) {
                            return
                        }
                        // Automatically fall back to HTTP if an optimistic HTTPS upgrade failed
                        val upgradedPair = lastUpgradedHttpsPair
                        if (upgradedPair != null &&
                            upgradedPair.second.equals(reqUrl, ignoreCase = true) &&
                            view != null
                        ) {
                            val host = extractHostLower(upgradedPair.first)
                            if (host.isNotEmpty()) {
                                bypassedHttpsUpgradeHosts.add(host)
                            }
                            lastUpgradedHttpsPair = null
                            view.loadUrl(
                                upgradedPair.first,
                                performanceOptimizer.desktopRequestHeaders(currentPreferences.desktopViewportEnabled)
                            )
                            return
                        }
                        mainFrameLoadError = WebPageErrorState(
                            failingUrl = reqUrl.ifBlank { currentTab.url },
                            reason = reason,
                            errorCode = code
                        )
                        currentOnPageLoadFailedWithReason(currentTab.id, reqUrl, reason)
                    }
                }

                override fun onRenderProcessGone(
                    view: WebView?,
                    detail: RenderProcessGoneDetail?
                ): Boolean {
                    val failingUrl = view?.url ?: currentTab.url
                    runCatching {
                        view?.stopLoading()
                        (view?.parent as? ViewGroup)?.removeView(view)
                        view?.destroy()
                    }
                    currentOnPageLoadFailedWithReason(
                        currentTab.id,
                        failingUrl,
                        "WebView renderer process terminated"
                    )
                    if (renderCrashCount < 2) {
                        renderCrashCount += 1
                        Handler(Looper.getMainLooper()).post {
                            mainFrameLoadError = null
                            renderRecoveryNonce += 1
                        }
                    } else {
                        mainFrameLoadError = WebPageErrorState(
                            failingUrl = failingUrl,
                            reason = "Web page process closed unexpectedly",
                            errorCode = -1
                        )
                    }
                    return true
                }
            }
            onRegisterWebView(tab.id, this)
            // Start initial page load immediately on frame 1 so the WebView never renders a blank uninitialized frame
            val initialTargetUrl = tab.url.ifBlank { tab.requestedUrl }
            val lastTarget = getTag(LAST_LOADED_URL_TAG) as? String
            val lastHandledReqId = getTag(LAST_HANDLED_REQUEST_ID_TAG) as? Long
            val alreadyLoadedSameRequest = lastTarget != null &&
                lastHandledReqId == tab.loadRequestId &&
                !this.url.isNullOrBlank() &&
                !this.url.equals("about:blank", ignoreCase = true)
            if (!tab.isHomePage && !alreadyLoadedSameRequest) {
                setTag(LAST_LOADED_URL_TAG, "${tab.loadRequestId}#$initialTargetUrl")
                setTag(LAST_HANDLED_REQUEST_ID_TAG, tab.loadRequestId)
                loadUrlIntoWebView(
                    webView = this,
                    url = initialTargetUrl,
                    desktopMode = preferences.desktopViewportEnabled,
                    performanceOptimizer = performanceOptimizer
                )
            }
        }
    }

    LaunchedEffect(canvasColorArgb, webView) {
        webView.setBackgroundColor(canvasColorArgb)
    }

    DisposableEffect(webView, preferences.shieldsEnabled, preferences.scriptGuardEnabled) {
        var scriptHandler: ScriptHandler? = null
        if (preferences.shieldsEnabled && !preferences.scriptGuardEnabled) {
            val stealthScript = privacyManager.buildStealthAdblockScript(true)
            if (stealthScript.isNotEmpty()) {
                runCatching {
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                        scriptHandler = WebViewCompat.addDocumentStartJavaScript(
                            webView,
                            stealthScript,
                            setOf("*")
                        )
                    }
                }
            }
        }
        onDispose {
            runCatching { scriptHandler?.remove() }
        }
    }

    LaunchedEffect(
        preferences.scriptGuardEnabled,
        preferences.desktopViewportEnabled,
        preferences.aggressivePreloadEnabled
    ) {
        val scriptGuardChanged = lastAppliedScriptGuard != preferences.scriptGuardEnabled
        val desktopModeChanged = lastAppliedDesktopMode != preferences.desktopViewportEnabled
        val preloadChanged = lastAppliedPreload != preferences.aggressivePreloadEnabled
        if (!scriptGuardChanged && !desktopModeChanged && !preloadChanged) {
            return@LaunchedEffect
        }
        lastAppliedScriptGuard = preferences.scriptGuardEnabled
        lastAppliedDesktopMode = preferences.desktopViewportEnabled
        lastAppliedPreload = preferences.aggressivePreloadEnabled

        performanceOptimizer.configureFastWebView(
            webView = webView,
            scriptGuardEnabled = preferences.scriptGuardEnabled,
            desktopMode = preferences.desktopViewportEnabled,
            aggressiveCaching = preferences.aggressivePreloadEnabled,
            isIncognito = tab.isPrivate,
            textZoomPercent = preferences.effectiveZoomForUrl(tab.url)
        )
        performanceOptimizer.applyDesktopViewportMode(
            webView = webView,
            desktopMode = preferences.desktopViewportEnabled
        )

        if (desktopModeChanged && !tab.isHomePage) {
            val targetUrl = tab.url.ifBlank { tab.requestedUrl }
            val targetKey = "${tab.loadRequestId}#$targetUrl"
            mainFrameLoadError = null
            webView.setTag(LAST_LOADED_URL_TAG, targetKey)
            webView.setTag(LAST_HANDLED_REQUEST_ID_TAG, tab.loadRequestId)
            loadUrlIntoWebView(
                webView = webView,
                url = targetUrl,
                desktopMode = preferences.desktopViewportEnabled,
                performanceOptimizer = performanceOptimizer
            )
        }
    }

    val effectiveZoomPercent = preferences.effectiveZoomForUrl(tab.url)
    LaunchedEffect(
        effectiveZoomPercent,
        preferences.forceEnableZoom,
        preferences.caretBrowsingEnabled,
        preferences.simplifiedViewEnabled
    ) {
        if (webView.settings.textZoom != effectiveZoomPercent) {
            webView.settings.textZoom = effectiveZoomPercent
        }
        if (lastAppliedForceZoom != preferences.forceEnableZoom || preferences.forceEnableZoom) {
            lastAppliedForceZoom = preferences.forceEnableZoom
            performanceOptimizer.applyForceEnableZoom(webView, preferences.forceEnableZoom)
        }
        if (lastAppliedCaretBrowsing != preferences.caretBrowsingEnabled || preferences.caretBrowsingEnabled) {
            lastAppliedCaretBrowsing = preferences.caretBrowsingEnabled
            performanceOptimizer.applyCaretBrowsing(webView, preferences.caretBrowsingEnabled)
        }
        if (!preferences.simplifiedViewEnabled) {
            simplifiedAvailable = false
            showSimplifiedReader = false
        }
    }

    LaunchedEffect(tab.id, tab.loadRequestId) {
        val targetUrl = tab.url.ifBlank { tab.requestedUrl }
        val lastTarget = webView.getTag(LAST_LOADED_URL_TAG) as? String
        val lastHandledReqId = webView.getTag(LAST_HANDLED_REQUEST_ID_TAG) as? Long
        val alreadyLoadedSameRequest = lastTarget != null &&
            lastHandledReqId == tab.loadRequestId &&
            !webView.url.isNullOrBlank() &&
            !webView.url.equals("about:blank", ignoreCase = true)
        if (!tab.isHomePage && !alreadyLoadedSameRequest) {
            mainFrameLoadError = null
            webView.setTag(LAST_LOADED_URL_TAG, "${tab.loadRequestId}#$targetUrl")
            webView.setTag(LAST_HANDLED_REQUEST_ID_TAG, tab.loadRequestId)
            loadUrlIntoWebView(
                webView = webView,
                url = targetUrl,
                desktopMode = preferences.desktopViewportEnabled,
                performanceOptimizer = performanceOptimizer
            )
        }
    }

    val loadMilestoneReached = !tab.isLoading || tab.progress >= 0.72f
    LaunchedEffect(tab.id, tab.url, loadMilestoneReached, canCapturePreview) {
        if (loadMilestoneReached && !tab.isHomePage && !tab.isPrivate && canCapturePreview) {
            delay(200L)
            captureWebViewPixelCopy(webView) { bmp ->
                currentOnThumbnailCaptured(tab.id, bmp)
            }
            if (!tab.isLoading) {
                delay(550L)
                captureWebViewPixelCopy(webView) { bmp ->
                    currentOnThumbnailCaptured(tab.id, bmp)
                }
            }
        }
    }

    LaunchedEffect(tab.id, scrollSnapshotTick, canCapturePreview) {
        if (scrollSnapshotTick > 0L && !tab.isLoading && !tab.isHomePage && !tab.isPrivate && canCapturePreview) {
            delay(220L)
            captureWebViewPixelCopy(webView) { bmp ->
                currentOnThumbnailCaptured(tab.id, bmp)
            }
        }
    }

    LaunchedEffect(tab.id, interactionSnapshotTick, canCapturePreview) {
        if (interactionSnapshotTick > 0L && !tab.isLoading && !tab.isHomePage && !tab.isPrivate && canCapturePreview) {
            delay(420L)
            captureWebViewPixelCopy(webView) { bmp ->
                currentOnThumbnailCaptured(tab.id, bmp)
            }
        }
    }

    DisposableEffect(webView, lifecycleOwner) {
        webView.onResume()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> webView.onResume()
                Lifecycle.Event.ON_PAUSE -> webView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webView.onPause()
        }
    }

    val pullContainer = remember(tab.id, webView, touchSlopPx, maxPullPx) {
        PullToRefreshWebViewContainer(
            context = context,
            webView = webView,
            touchSlopPx = touchSlopPx,
            maxPullPx = maxPullPx
        ).apply {
            setBackgroundColor(canvasColorArgb)
        }
    }

    LaunchedEffect(canvasColorArgb, pullContainer) {
        pullContainer.setBackgroundColor(canvasColorArgb)
    }

    val targetOffsetPx = if (isRefreshing) restingRefreshOffsetPx else pullDistancePx
    val animatedPullOffsetState = animateFloatAsState(
        targetValue = targetOffsetPx,
        animationSpec = if (isActivelyPulling) {
            snap()
        } else {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        },
        label = "ExpressivePullToRefreshOffset"
    )
    val showPullIndicator by remember(tab.id) {
        derivedStateOf { isRefreshing || pullDistancePx > 1f || animatedPullOffsetState.value > 1f }
    }

    Box(modifier = modifier.fillMaxSize().clipToBounds()) {
        androidx.compose.runtime.key(tab.id, renderRecoveryNonce) {
            AndroidView(
                factory = {
                    pullContainer.apply {
                        isRefreshingProvider = { isRefreshing }
                        onPullProgress = { distancePx, activelyPulling ->
                            if (isActivelyPulling != activelyPulling) {
                                isActivelyPulling = activelyPulling
                            }
                            if (abs(pullDistancePx - distancePx) >= 0.5f || distancePx == 0f) {
                                pullDistancePx = distancePx
                            }
                        }
                        onPullReleased = { releasedDistancePx ->
                            isActivelyPulling = false
                            if (releasedDistancePx >= refreshTriggerPx) {
                                isRefreshing = true
                                refreshSawLoading = currentTab.isLoading
                                refreshStartedAtMillis = System.currentTimeMillis()
                                pullDistancePx = restingRefreshOffsetPx
                                currentOnRefreshPage()
                            } else {
                                pullDistancePx = 0f
                            }
                        }
                    }
                },
                update = { container ->
                    if (container.webView !== webView) {
                        onRegisterWebView(tab.id, container.webView)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("browser_webview_${tab.id}")
            )
        }

        mainFrameLoadError?.let { errorState ->
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(19f)
                    .testTag("webview_error_overlay")
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    ElevatedCard(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.size(52.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.WarningAmber,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Unable to load page",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = errorState.reason,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = errorState.failingUrl,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                if (webView.canGoBack()) {
                                    OutlinedButton(
                                        onClick = {
                                            mainFrameLoadError = null
                                            webView.goBack()
                                        },
                                        modifier = Modifier.testTag("webview_error_back_button")
                                    ) {
                                        Text("Go back")
                                    }
                                }
                                Button(
                                    onClick = {
                                        mainFrameLoadError = null
                                        if (renderCrashCount >= 2) {
                                            renderCrashCount = 0
                                            renderRecoveryNonce += 1
                                        } else {
                                            loadUrlIntoWebView(
                                                webView = webView,
                                                url = errorState.failingUrl,
                                                desktopMode = currentPreferences.desktopViewportEnabled,
                                                performanceOptimizer = performanceOptimizer
                                            )
                                        }
                                    },
                                    modifier = Modifier.testTag("webview_error_retry_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Reload")
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showPullIndicator) {
            Material3ExpressivePullToRefreshIndicator(
                pullProgressProvider = { (animatedPullOffsetState.value / refreshTriggerPx).coerceIn(0f, 1.25f) },
                isRefreshing = isRefreshing,
                isTriggered = isRefreshing || pullDistancePx >= refreshTriggerPx,
                offsetYProvider = { animatedPullOffsetState.value },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(20f)
            )
        }

        AnimatedVisibility(
            visible = preferences.simplifiedViewEnabled && simplifiedAvailable && !showSimplifiedReader,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .zIndex(18f)
        ) {
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("simplified_view_banner")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Article,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = stringResource(R.string.accessibility_simplified_banner_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { showSimplifiedReader = true },
                        modifier = Modifier.testTag("simplified_view_open_button")
                    ) {
                        Text(stringResource(R.string.accessibility_simplified_banner_action))
                    }
                    IconButton(
                        onClick = {
                            simplifiedAvailable = false
                            simplifiedBannerDismissedForUrl = tab.url
                        },
                        modifier = Modifier.testTag("simplified_view_dismiss_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.cancel),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        DomScannerVisualIndicatorOverlay(
            activeHighlight = activeDomHighlight,
            scannedElements = scannedElements,
            isOutlineEnabled = isScannerOutlineOverlayEnabled,
            isAutomationRunning = isAutomationRunning,
            onDismissHighlight = onDismissDomHighlight,
            onToggleOutlines = onToggleScannerOutlines,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(17f)
        )

        val findFocusRequester = remember { FocusRequester() }
        LaunchedEffect(isFindInPageOpen) {
            if (isFindInPageOpen) {
                runCatching { findFocusRequester.requestFocus() }
            }
        }
        AnimatedVisibility(
            visible = isFindInPageOpen,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .zIndex(21f)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("find_in_page_bar")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (findInPageQuery.isEmpty()) {
                            Text(
                                text = stringResource(R.string.find_in_page_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = findInPageQuery,
                            onValueChange = onFindQueryChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onFindNextMatch() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(findFocusRequester)
                                .testTag("find_in_page_input")
                        )
                    }
                    if (findInPageQuery.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.find_in_page_matches_format,
                                findInPageActiveMatch,
                                findInPageTotalMatches
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (findInPageTotalMatches > 0) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .testTag("find_in_page_count")
                        )
                    }
                    FilledTonalIconButton(
                        onClick = onFindPreviousMatch,
                        enabled = findInPageTotalMatches > 0,
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(36.dp)
                            .testTag("find_in_page_prev")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.cd_find_previous),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    FilledTonalIconButton(
                        onClick = onFindNextMatch,
                        enabled = findInPageTotalMatches > 0,
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(36.dp)
                            .testTag("find_in_page_next")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.cd_find_next),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onCloseFindInPage,
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(36.dp)
                            .testTag("find_in_page_close")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.cd_close_find_in_page),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    if (showSimplifiedReader) {
        val readerScale = effectiveZoomPercent / 100f
        ModalBottomSheet(
            onDismissRequest = { showSimplifiedReader = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.testTag("simplified_view_sheet")
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.accessibility_simplified_view_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(
                        onClick = { showSimplifiedReader = false },
                        modifier = Modifier.testTag("simplified_view_exit_button")
                    ) {
                        Text(stringResource(R.string.accessibility_simplified_exit))
                    }
                }
                Text(
                    text = simplifiedTitle,
                    fontSize = (22f * readerScale).sp,
                    lineHeight = (28f * readerScale).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                simplifiedParagraphs.forEach { para ->
                    Text(
                        text = para,
                        fontSize = (16f * readerScale).sp,
                        lineHeight = (24f * readerScale).sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Long-Press Context Popup Dialog (matches user screenshot)
    longPressTarget?.let { target ->
        WebLongPressContextDialog(
            target = target,
            onDismiss = { longPressTarget = null },
            onOpenInNewTab = {
                longPressTarget = null
                onOpenInNewTab(target.url)
            },
            onOpenInNewTabInGroup = {
                longPressTarget = null
                onOpenInNewTabInGroup(target.url)
            },
            onOpenInPrivateTab = {
                longPressTarget = null
                onOpenInPrivateTab(target.url)
            },
            onOpenInNewWindow = {
                longPressTarget = null
                onOpenInNewWindow(target.url)
            },
            onPreviewPage = {
                longPressTarget = null
                previewTarget = target
            },
            onCopyLinkAddress = {
                longPressTarget = null
                clipboardManager.setText(AnnotatedString(target.url))
                Toast.makeText(context, "Link address copied", Toast.LENGTH_SHORT).show()
            },
            onCopyCleanLink = {
                longPressTarget = null
                val cleanUrl = stripTrackingParams(target.url)
                clipboardManager.setText(AnnotatedString(cleanUrl))
                Toast.makeText(context, "Clean link copied", Toast.LENGTH_SHORT).show()
            },
            onCopyLinkText = {
                longPressTarget = null
                clipboardManager.setText(AnnotatedString(target.title))
                Toast.makeText(context, "Link text copied", Toast.LENGTH_SHORT).show()
            },
            onDownloadLink = {
                longPressTarget = null
                currentOnDownloadFile(target.url, target.title, null, null, -1L, null)
            },
            onAddToReadingList = {
                longPressTarget = null
                onAddToReadingList(target.title, target.url)
            },
            onShareLink = {
                longPressTarget = null
                runCatching {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, target.title)
                        putExtra(Intent.EXTRA_TEXT, target.url)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share link"))
                }
            }
        )
    }

    // Live Page Preview Modal Sheet ("Preview page" action)
    previewTarget?.let { target ->
        WebPagePreviewBottomSheet(
            target = target,
            preferences = preferences,
            performanceOptimizer = performanceOptimizer,
            onOpenInNewTab = {
                val urlToOpen = target.url
                previewTarget = null
                onOpenInNewTab(urlToOpen)
            },
            onDismiss = { previewTarget = null }
        )
    }
}

private tailrec fun Context.findActivityWindow(): Window? = when (this) {
    is Activity -> window
    is ContextWrapper -> baseContext.findActivityWindow()
    else -> null
}

private fun captureWebViewPixelCopy(
    webView: WebView,
    onCaptured: (Bitmap) -> Unit
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    if (!webView.isAttachedToWindow ||
        webView.windowToken == null ||
        webView.visibility != android.view.View.VISIBLE ||
        webView.width <= 0 ||
        webView.height <= 0
    ) {
        return
    }
    val window = webView.context.findActivityWindow() ?: return
    val decorView = window.decorView
    if (decorView.width <= 0 || decorView.height <= 0) return

    val location = IntArray(2)
    webView.getLocationInWindow(location)
    val cropHeight = minOf(webView.height, (webView.width * 1.28f).toInt().coerceAtLeast(1))
    val left = location[0].coerceIn(0, (decorView.width - 1).coerceAtLeast(0))
    val top = location[1].coerceIn(0, (decorView.height - 1).coerceAtLeast(0))
    val right = (location[0] + webView.width).coerceAtMost(decorView.width)
    val bottom = (location[1] + cropHeight).coerceAtMost(decorView.height)
    val srcWidth = right - left
    val srcHeight = bottom - top
    if (srcWidth <= 16 || srcHeight <= 16) return

    val thumbWidth = 360
    val thumbHeight = (srcHeight.toFloat() / srcWidth.toFloat() * thumbWidth)
        .toInt()
        .coerceIn(240, 520)
    val destBitmap = Bitmap.createBitmap(thumbWidth, thumbHeight, Bitmap.Config.ARGB_8888)
    val srcRect = Rect(left, top, right, bottom)

    runCatching {
        PixelCopy.request(
            window,
            srcRect,
            destBitmap,
            { copyResult ->
                if (copyResult == PixelCopy.SUCCESS) {
                    onCaptured(destBitmap)
                } else {
                    destBitmap.recycle()
                }
            },
            Handler(Looper.getMainLooper())
        )
    }.onFailure {
        destBitmap.recycle()
    }
}

private class PullToRefreshWebViewContainer(
    context: Context,
    val webView: WebView,
    private val touchSlopPx: Float,
    private val maxPullPx: Float
) : android.widget.FrameLayout(context) {
    var isRefreshingProvider: () -> Boolean = { false }
    var onPullProgress: (distancePx: Float, activelyPulling: Boolean) -> Unit = { _, _ -> }
    var onPullReleased: (distancePx: Float) -> Unit = { _ -> }

    private var startY = 0f
    private var startX = 0f
    private var pullAnchorY = 0f
    private var canPullFromTop = false
    private var isPullingGesture = false
    private var currentPullDistancePx = 0f

    init {
        (webView.parent as? ViewGroup)?.removeView(webView)
        addView(
            webView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    private fun isAtTop(): Boolean {
        return webView.scrollY <= 0 && !webView.canScrollVertically(-1)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startY = ev.y
                startX = ev.x
                isPullingGesture = false
                currentPullDistancePx = 0f
                canPullFromTop = isAtTop() && !isRefreshingProvider() && ev.pointerCount == 1
                parent?.requestDisallowInterceptTouchEvent(true)
                return false
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                canPullFromTop = false
                if (isPullingGesture) {
                    isPullingGesture = false
                    currentPullDistancePx = 0f
                    onPullProgress(0f, false)
                }
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (ev.pointerCount > 1 || isRefreshingProvider()) {
                    canPullFromTop = false
                    return false
                }
                if (!isAtTop()) {
                    canPullFromTop = false
                    return false
                }
                if (!canPullFromTop) return false

                val dy = ev.y - startY
                val dx = abs(ev.x - startX)
                if (dy < -touchSlopPx * 0.5f || dx > touchSlopPx * 1.1f && dx > abs(dy)) {
                    canPullFromTop = false
                    return false
                }
                if (dy > touchSlopPx * 1.35f && dy > dx * 1.6f) {
                    isPullingGesture = true
                    pullAnchorY = ev.y - (touchSlopPx * 0.5f)
                    currentPullDistancePx = ((ev.y - pullAnchorY) * 0.58f).coerceIn(0f, maxPullPx)
                    onPullProgress(currentPullDistancePx, true)
                    return true
                }
                return false
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                canPullFromTop = false
                return false
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startY = ev.y
                startX = ev.x
                isPullingGesture = false
                currentPullDistancePx = 0f
                canPullFromTop = isAtTop() && !isRefreshingProvider() && ev.pointerCount == 1
                parent?.requestDisallowInterceptTouchEvent(true)
                return canPullFromTop
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                canPullFromTop = false
                if (isPullingGesture) {
                    isPullingGesture = false
                    currentPullDistancePx = 0f
                    onPullProgress(0f, false)
                }
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (ev.pointerCount > 1 || isRefreshingProvider()) {
                    canPullFromTop = false
                    if (isPullingGesture) {
                        isPullingGesture = false
                        currentPullDistancePx = 0f
                        onPullProgress(0f, false)
                    }
                    return false
                }
                if (!isPullingGesture) {
                    if (!canPullFromTop || !isAtTop()) return false
                    val dy = ev.y - startY
                    val dx = abs(ev.x - startX)
                    if (dy < -touchSlopPx * 0.5f || dx > touchSlopPx * 1.1f && dx > abs(dy)) {
                        canPullFromTop = false
                        return false
                    }
                    if (dy > touchSlopPx * 1.35f && dy > dx * 1.6f) {
                        isPullingGesture = true
                        pullAnchorY = ev.y - (touchSlopPx * 0.5f)
                    } else {
                        return true
                    }
                }
                val rawPull = ev.y - pullAnchorY
                if (rawPull <= 0f) {
                    currentPullDistancePx = 0f
                    onPullProgress(0f, true)
                } else {
                    currentPullDistancePx = (rawPull * 0.58f).coerceIn(0f, maxPullPx)
                    onPullProgress(currentPullDistancePx, true)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (isPullingGesture) {
                    val releasedDistance = currentPullDistancePx
                    isPullingGesture = false
                    canPullFromTop = false
                    currentPullDistancePx = 0f
                    onPullReleased(releasedDistance)
                    return true
                }
                canPullFromTop = false
                return false
            }

            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (isPullingGesture) {
                    isPullingGesture = false
                    canPullFromTop = false
                    currentPullDistancePx = 0f
                    onPullProgress(0f, false)
                    return true
                }
                canPullFromTop = false
                return false
            }
        }
        return super.onTouchEvent(ev)
    }
}

private fun loadUrlIntoWebView(
    webView: WebView,
    url: String,
    desktopMode: Boolean,
    performanceOptimizer: PerformanceOptimizer
) {
    val builtIn = BuiltInShowcasePages.resolveHtml(url)
    if (builtIn != null) {
        webView.loadDataWithBaseURL(
            builtIn.first,
            builtIn.second,
            "text/html",
            "UTF-8",
            null
        )
    } else {
        webView.loadUrl(
            url,
            performanceOptimizer.desktopRequestHeaders(desktopMode)
        )
    }
}

/**
 * Long-Press Context Popup Dialog modeled after the user's screenshot:
 * - Rounded container (`RoundedCornerShape(24.dp)`)
 * - Header with circular monogram badge, 1-line link title, 1-line compact URL, and divider
 * - 11 action items:
 *   1. Open in new tab
 *   2. Open in new tab in group
 *   3. Open in Private tab
 *   4. Open in new window
 *   5. Preview page
 *   6. Copy link address
 *   7. Copy clean link
 *   8. Copy link text
 *   9. Download link
 *   10. Add to reading list
 *   11. Share link (with green quick-share badge on the right)
 */
@Composable
fun WebLongPressContextDialog(
    target: WebLongPressTarget,
    onDismiss: () -> Unit,
    onOpenInNewTab: () -> Unit,
    onOpenInNewTabInGroup: () -> Unit,
    onOpenInPrivateTab: () -> Unit,
    onOpenInNewWindow: () -> Unit,
    onPreviewPage: () -> Unit,
    onCopyLinkAddress: () -> Unit,
    onCopyCleanLink: () -> Unit,
    onCopyLinkText: () -> Unit,
    onDownloadLink: () -> Unit,
    onAddToReadingList: () -> Unit,
    onShareLink: () -> Unit
) {
    val compactUrl = remember(target.url) { formatCompactDialogUrl(target.url) }
    val monogram = remember(target.url, target.title) {
        val host = runCatching { URI(target.url).host?.removePrefix("www.") }.getOrNull().orEmpty()
        host.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString()
            ?: target.title.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString()
            ?: "G"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            shadowElevation = 14.dp,
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .clip(RoundedCornerShape(24.dp))
                .testTag("web_long_press_context_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                // Dialog Header: Monogram Badge + Title + Compact URL
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.size(50.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = monogram,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = target.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = compactUrl,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider(
                    thickness = 0.8.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                )

                Spacer(modifier = Modifier.height(4.dp))

                ContextDialogMenuRow(
                    label = "Open in new tab",
                    onClick = onOpenInNewTab,
                    tag = "ctx_open_new_tab"
                )
                ContextDialogMenuRow(
                    label = "Open in new tab in group",
                    onClick = onOpenInNewTabInGroup,
                    tag = "ctx_open_new_tab_in_group"
                )
                ContextDialogMenuRow(
                    label = "Open in Private tab",
                    onClick = onOpenInPrivateTab,
                    tag = "ctx_open_private_tab"
                )
                ContextDialogMenuRow(
                    label = "Open in new window",
                    onClick = onOpenInNewWindow,
                    tag = "ctx_open_new_window"
                )
                ContextDialogMenuRow(
                    label = "Preview page",
                    onClick = onPreviewPage,
                    tag = "ctx_preview_page"
                )
                ContextDialogMenuRow(
                    label = "Copy link address",
                    onClick = onCopyLinkAddress,
                    tag = "ctx_copy_link_address"
                )
                ContextDialogMenuRow(
                    label = "Copy clean link",
                    onClick = onCopyCleanLink,
                    tag = "ctx_copy_clean_link"
                )
                ContextDialogMenuRow(
                    label = "Copy link text",
                    onClick = onCopyLinkText,
                    tag = "ctx_copy_link_text"
                )
                ContextDialogMenuRow(
                    label = "Download link",
                    onClick = onDownloadLink,
                    tag = "ctx_download_link"
                )
                ContextDialogMenuRow(
                    label = "Add to reading list",
                    onClick = onAddToReadingList,
                    tag = "ctx_add_reading_list"
                )
                ContextDialogMenuRow(
                    label = "Share link",
                    onClick = onShareLink,
                    tag = "ctx_share_link",
                    trailingBadge = {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Share,
                                    contentDescription = "Quick share",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun ContextDialogMenuRow(
    label: String,
    onClick: () -> Unit,
    tag: String,
    trailingBadge: (@Composable () -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 13.dp)
            .testTag(tag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 16.5.sp,
                fontWeight = FontWeight.Normal
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (trailingBadge != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailingBadge()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebPagePreviewBottomSheet(
    target: WebLongPressTarget,
    preferences: BrowserPreferences,
    performanceOptimizer: PerformanceOptimizer,
    onOpenInNewTab: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var previewProgress by remember { mutableFloatStateOf(0.1f) }
    var previewTitle by remember { mutableStateOf(target.title) }

    val previewWebView = remember(target.url) {
        WebView(context).apply {
            setBackgroundColor(resolveWebViewCanvasColorArgb(target.url))
            performanceOptimizer.configureFastWebView(
                webView = this,
                scriptGuardEnabled = preferences.scriptGuardEnabled,
                desktopMode = preferences.desktopViewportEnabled,
                aggressiveCaching = false,
                textZoomPercent = preferences.effectiveZoomForUrl(target.url)
            )
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (view != null && newProgress in 15..95) {
                        performanceOptimizer.installWebGlZeroCountGuard(
                            view,
                            preferences.scriptGuardEnabled
                        )
                    }
                    previewProgress = (newProgress / 100f).coerceIn(0f, 1f)
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    if (!title.isNullOrBlank()) {
                        previewTitle = title
                    }
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    if (view != null) {
                        performanceOptimizer.installWebGlZeroCountGuard(
                            view,
                            preferences.scriptGuardEnabled
                        )
                    }
                }

                override fun onRenderProcessGone(
                    view: WebView?,
                    detail: RenderProcessGoneDetail?
                ): Boolean {
                    onDismiss()
                    return true
                }
            }
            loadUrl(target.url, performanceOptimizer.desktopRequestHeaders(preferences.desktopViewportEnabled))
        }
    }

    DisposableEffect(previewWebView) {
        previewWebView.onResume()
        onDispose {
            runCatching {
                previewWebView.stopLoading()
                previewWebView.onPause()
                (previewWebView.parent as? ViewGroup)?.removeView(previewWebView)
                previewWebView.removeAllViews()
                previewWebView.destroy()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.84f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = previewTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatCompactDialogUrl(target.url),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onOpenInNewTab) {
                    Icon(
                        imageVector = Icons.Outlined.OpenInNew,
                        contentDescription = "Open in new tab",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close preview",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (previewProgress < 1f) {
                LinearProgressIndicator(
                    progress = { previewProgress.coerceIn(0.05f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                )
            } else {
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }

            AndroidView(
                factory = { previewWebView },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

private fun parseJsLongPressJson(rawJson: String?): JSONObject? {
    if (rawJson.isNullOrBlank() || rawJson == "null") return null
    return runCatching {
        val unescaped = if (rawJson.startsWith("\"") && rawJson.endsWith("\"")) {
            JSONTokener(rawJson).nextValue().toString()
        } else {
            rawJson
        }
        JSONObject(unescaped)
    }.getOrNull()
}

private fun formatCompactDialogUrl(rawUrl: String): String {
    return runCatching {
        val uri = URI(rawUrl)
        val host = uri.host?.removePrefix("www.").orEmpty()
        val path = uri.rawPath.orEmpty()
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        if (host.isNotEmpty()) {
            "$host$path$query"
        } else {
            rawUrl.removePrefix("https://").removePrefix("http://").removePrefix("www.")
        }
    }.getOrElse {
        rawUrl.removePrefix("https://").removePrefix("http://").removePrefix("www.")
    }
}

private fun stripTrackingParams(rawUrl: String): String {
    return runCatching {
        val uri = Uri.parse(rawUrl)
        val blockedKeys = setOf(
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "fbclid", "gclid", "igshid", "mc_cid", "mc_eid", "ref", "si",
            "ved", "ei", "oq", "gs_lcrp", "sourceid", "sclient"
        )
        val builder = uri.buildUpon().clearQuery()
        for (name in uri.queryParameterNames) {
            if (name.lowercase() !in blockedKeys) {
                uri.getQueryParameters(name).forEach { valStr ->
                    builder.appendQueryParameter(name, valStr)
                }
            }
        }
        builder.build().toString()
    }.getOrDefault(rawUrl)
}

private fun enqueueLinkDownload(context: Context, url: String, title: String) {
    runCatching {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (dm != null && (url.startsWith("http://", true) || url.startsWith("https://", true))) {
            val fileName = URLUtil.guessFileName(url, null, null)
            val req = DownloadManager.Request(Uri.parse(url))
                .setTitle(title.ifBlank { fileName })
                .setDescription(url)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            dm.enqueue(req)
            Toast.makeText(context, "Downloading $fileName…", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()
        }
    }.onFailure {
        Toast.makeText(context, "Unable to download link", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Material 3 Expressive Pull-to-Refresh Contained Loading Indicator:
 * Features an elevated circular container (`primaryContainer`) housing a morphing,
 * spinning Material 3 Expressive polygon shape (`primary` / `onPrimaryContainer`)
 * that scales & rotates during pull and continuously spins & morphs through expressive
 * M3 shapes (scallop cookie -> quatrefoil clover -> 6-petal flower -> soft pentagon -> trefoil)
 * while refreshing.
 */
@Composable
fun Material3ExpressivePullToRefreshIndicator(
    pullProgressProvider: () -> Float,
    isRefreshing: Boolean,
    isTriggered: Boolean,
    offsetYProvider: () -> Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val startHiddenPx = remember(density) { with(density) { -48.dp.toPx() } }

    val infiniteTransition = rememberInfiniteTransition(label = "M3ExpressiveLoadingIndicator")
    val spinAngleState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1050, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ExpressiveSpinAngle"
    )
    val morphPhaseState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ExpressiveShapeMorphPhase"
    )

    val containerScaleState = animateFloatAsState(
        targetValue = if (isRefreshing) 1f else if (isTriggered) 1.06f else 0.94f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "ExpressiveContainerScale"
    )

    val containerColor = if (isRefreshing || isTriggered) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val innerShapeColor = if (isRefreshing || isTriggered) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val reusablePath = remember { Path() }

    Surface(
        shape = CircleShape,
        color = containerColor,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = if (isRefreshing || isTriggered) 0.28f else 0.14f)
        ),
        modifier = modifier
            .size(48.dp)
            .graphicsLayer {
                val progress = pullProgressProvider()
                val scale = if (isRefreshing) {
                    containerScaleState.value
                } else {
                    (progress * 0.65f + 0.40f).coerceIn(0.40f, 1.08f)
                }
                translationY = startHiddenPx + offsetYProvider()
                scaleX = scale
                scaleY = scale
                alpha = if (isRefreshing) 1f else (progress * 1.45f).coerceIn(0f, 1f)
            }
            .testTag("expressive_pull_to_refresh_indicator")
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.size(28.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val maxRadius = size.minDimension / 2f

                val activePhase = if (isRefreshing) {
                    morphPhaseState.value
                } else {
                    (pullProgressProvider() * 2.5f).coerceIn(0f, 4.99f)
                }
                val effectiveRotation = if (isRefreshing) {
                    spinAngleState.value + (morphPhaseState.value * 72f)
                } else {
                    pullProgressProvider() * 220f
                }

                val shapeIndex = activePhase.toInt() % 5
                val nextShapeIndex = (shapeIndex + 1) % 5
                val rawT = activePhase - activePhase.toInt()
                // Smoothstep interpolation between expressive M3 shapes
                val t = rawT * rawT * (3f - 2f * rawT)

                reusablePath.reset()
                val steps = 72
                for (i in 0..steps) {
                    val theta = (i.toFloat() / steps.toFloat()) * (2f * PI.toFloat())
                    val r1 = evaluateExpressiveShapeRadius(shapeIndex, theta)
                    val r2 = evaluateExpressiveShapeRadius(nextShapeIndex, theta)
                    val r = maxRadius * (r1 + (r2 - r1) * t)
                    val x = cx + r * cos(theta)
                    val y = cy + r * sin(theta)
                    if (i == 0) {
                        reusablePath.moveTo(x, y)
                    } else {
                        reusablePath.lineTo(x, y)
                    }
                }
                reusablePath.close()

                rotate(degrees = effectiveRotation) {
                    drawPath(
                        path = reusablePath,
                        color = innerShapeColor
                    )
                }
            }
        }
    }
}

/**
 * Evaluates normalized polar radius `r(theta)` in `[0.65, 1.0]` for the 5 canonical
 * Material 3 Expressive Loading Indicator shapes:
 * 0 -> 8-lobed Soft Scallop Cookie
 * 1 -> 4-lobed Expressive Quatrefoil Clover
 * 2 -> 6-lobed Soft Starburst Flower
 * 3 -> 5-lobed Rounded Pentagon Cushion
 * 4 -> 3-lobed Expressive Soft Trefoil
 */
private fun evaluateExpressiveShapeRadius(shapeIndex: Int, theta: Float): Float {
    return when (shapeIndex) {
        0 -> 0.84f + 0.16f * cos(8f * theta)
        1 -> 0.78f + 0.22f * cos(4f * theta)
        2 -> 0.80f + 0.20f * cos(6f * theta)
        3 -> 0.85f + 0.15f * cos(5f * theta)
        else -> 0.81f + 0.19f * cos(3f * theta)
    }
}

@Composable
fun DomScannerVisualIndicatorOverlay(
    activeHighlight: ActiveDomElementHighlight?,
    scannedElements: List<DetectedPageElement>,
    isOutlineEnabled: Boolean,
    isAutomationRunning: Boolean,
    onDismissHighlight: () -> Unit,
    onToggleOutlines: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleElementsWithBounds = remember(scannedElements) {
        scannedElements
            .filter { !it.stale && it.visible && it.inViewport && it.boundingBox?.isValid == true }
            .sortedByDescending { it.interactable }
    }
    val hasActiveOverlay = activeHighlight != null || (isOutlineEnabled && isAutomationRunning && visibleElementsWithBounds.isNotEmpty())
    if (!hasActiveOverlay) return

    val resolvedTargetBox: ElementBoundingBox? = remember(activeHighlight, visibleElementsWithBounds) {
        activeHighlight?.boundingBox?.takeIf { it.isValid }
            ?: activeHighlight?.let { hl ->
                visibleElementsWithBounds.firstOrNull { el ->
                    (hl.selector.isNotBlank() && el.selector == hl.selector) ||
                        (hl.targetLabel.isNotBlank() && el.label.equals(hl.targetLabel, ignoreCase = true))
                }?.boundingBox
            }
    }

    val reducedMotion = LocalReducedMotion.current
    val shouldAnimateCanvas = isOutlineEnabled &&
        !reducedMotion &&
        (activeHighlight?.isScanning == true || (isAutomationRunning && resolvedTargetBox != null))
    val pulseFraction = if (shouldAnimateCanvas) {
        val infiniteTransition = rememberInfiniteTransition(label = "DomScannerOutlinePulse")
        val animatedPulse by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "DomTargetPulse"
        )
        animatedPulse
    } else {
        0.35f
    }
    val scanLineProgress = if (isOutlineEnabled && activeHighlight?.isScanning == true && !reducedMotion) {
        val scanTransition = rememberInfiniteTransition(label = "DomScannerSweep")
        val animatedScan by scanTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "DomScannerLaserLine"
        )
        animatedScan
    } else {
        0.5f
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("dom_scanner_visual_indicator_overlay")
    ) {
        if (isOutlineEnabled) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("dom_scanner_visual_indicator_canvas")
            ) {
                val canvasW = size.width
                val canvasH = size.height
                if (canvasW <= 0f || canvasH <= 0f) return@Canvas

                // 1. Subtle outlines for all detected interactive controls and text regions in viewport
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                visibleElementsWithBounds.take(80).forEach { el ->
                    val box = el.boundingBox ?: return@forEach
                    if (!box.isValid) return@forEach
                    val isPrimaryTarget = activeHighlight != null &&
                        (el.selector == activeHighlight.selector || el.label.equals(activeHighlight.targetLabel, ignoreCase = true))
                    if (isPrimaryTarget) return@forEach

                    val scaleX = canvasW / box.viewportWidth.coerceAtLeast(1f)
                    val scaleY = canvasH / box.viewportHeight.coerceAtLeast(1f)
                    val left = (box.left * scaleX).coerceIn(4f, (canvasW - 24f).coerceAtLeast(4f))
                    val top = (box.top * scaleY).coerceIn(4f, (canvasH - 24f).coerceAtLeast(4f))
                    val w = (box.width * scaleX).coerceIn(20f, (canvasW - left - 4f).coerceAtLeast(20f))
                    val h = (box.height * scaleY).coerceIn(18f, (canvasH - top - 4f).coerceAtLeast(18f))
                    val outlineTint = if (el.controlKind == com.example.automation.ControlKind.TEXT) {
                        secondaryColor
                    } else {
                        tertiaryColor
                    }
                    val fillAlpha = if (el.controlKind == com.example.automation.ControlKind.TEXT) 0.04f else 0.08f
                    val strokeAlpha = if (el.controlKind == com.example.automation.ControlKind.TEXT) 0.28f else 0.44f

                    drawRoundRect(
                        color = outlineTint.copy(alpha = fillAlpha),
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                    drawRoundRect(
                        color = outlineTint.copy(alpha = strokeAlpha),
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx(), pathEffect = dashEffect)
                    )
                }

                // 2. Active scanning sweep beam when DOM scanner is actively scanning the page
                if (activeHighlight?.isScanning == true) {
                    val beamY = (canvasH * scanLineProgress).coerceIn(0f, canvasH)
                    drawLine(
                        color = primaryColor.copy(alpha = 0.65f),
                        start = Offset(0f, beamY),
                        end = Offset(canvasW, beamY),
                        strokeWidth = 2.5.dp.toPx()
                    )
                    drawRect(
                        color = primaryColor.copy(alpha = 0.08f),
                        topLeft = Offset(0f, (beamY - 28.dp.toPx()).coerceAtLeast(0f)),
                        size = Size(canvasW, 28.dp.toPx())
                    )
                }

                // 3. Prominent animated target outline + corner brackets on the actively interacted element
                val targetBox = resolvedTargetBox
                if (targetBox != null && targetBox.isValid) {
                    val scaleX = canvasW / targetBox.viewportWidth.coerceAtLeast(1f)
                    val scaleY = canvasH / targetBox.viewportHeight.coerceAtLeast(1f)
                    val left = (targetBox.left * scaleX).coerceIn(6f, (canvasW - 36f).coerceAtLeast(6f))
                    val top = (targetBox.top * scaleY).coerceIn(6f, (canvasH - 36f).coerceAtLeast(6f))
                    val w = (targetBox.width * scaleX).coerceIn(28f, (canvasW - left - 6f).coerceAtLeast(28f))
                    val h = (targetBox.height * scaleY).coerceIn(24f, (canvasH - top - 6f).coerceAtLeast(24f))
                    val cornerR = 8.dp.toPx()
                    val pad = 4.dp.toPx() * (0.6f + 0.6f * pulseFraction)

                    // Outer pulsing halo
                    drawRoundRect(
                        color = primaryColor.copy(alpha = 0.16f + 0.12f * pulseFraction),
                        topLeft = Offset(left - pad, top - pad),
                        size = Size(w + pad * 2f, h + pad * 2f),
                        cornerRadius = CornerRadius(cornerR + pad, cornerR + pad)
                    )
                    // Inner tint fill
                    drawRoundRect(
                        color = secondaryColor.copy(alpha = 0.10f),
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(cornerR, cornerR)
                    )
                    // Crisp primary outline
                    drawRoundRect(
                        color = primaryColor,
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(cornerR, cornerR),
                        style = Stroke(width = 2.5.dp.toPx())
                    )

                    // 4 Corner precision brackets
                    val bracketLen = minOf(14.dp.toPx(), w * 0.3f, h * 0.3f)
                    val bStroke = 3.5.dp.toPx()
                    val bOffset = 2.dp.toPx()
                    val bLeft = left - bOffset
                    val bTop = top - bOffset
                    val bRight = left + w + bOffset
                    val bBottom = top + h + bOffset

                    // Top-left bracket
                    drawLine(primaryColor, Offset(bLeft, bTop), Offset(bLeft + bracketLen, bTop), bStroke, StrokeCap.Round)
                    drawLine(primaryColor, Offset(bLeft, bTop), Offset(bLeft, bTop + bracketLen), bStroke, StrokeCap.Round)
                    // Top-right bracket
                    drawLine(primaryColor, Offset(bRight, bTop), Offset(bRight - bracketLen, bTop), bStroke, StrokeCap.Round)
                    drawLine(primaryColor, Offset(bRight, bTop), Offset(bRight, bTop + bracketLen), bStroke, StrokeCap.Round)
                    // Bottom-left bracket
                    drawLine(primaryColor, Offset(bLeft, bBottom), Offset(bLeft + bracketLen, bBottom), bStroke, StrokeCap.Round)
                    drawLine(primaryColor, Offset(bLeft, bBottom - bracketLen), Offset(bLeft, bBottom), bStroke, StrokeCap.Round)
                    // Bottom-right bracket
                    drawLine(primaryColor, Offset(bRight - bracketLen, bBottom), Offset(bRight, bBottom), bStroke, StrokeCap.Round)
                    drawLine(primaryColor, Offset(bRight, bBottom - bracketLen), Offset(bRight, bBottom), bStroke, StrokeCap.Round)

                    // Center target pulse point
                    val cx = left + w * 0.5f
                    val cy = top + h * 0.5f
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.30f * (1f - pulseFraction)),
                        radius = (8.dp.toPx() + 10.dp.toPx() * pulseFraction),
                        center = Offset(cx, cy)
                    )
                    drawCircle(
                        color = primaryColor,
                        radius = 3.5.dp.toPx(),
                        center = Offset(cx, cy)
                    )
                }
            }
        }

        // 4. Floating Real-Time DOM Interaction Feedback Pill
        if (activeHighlight != null) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(
                    width = 1.5.dp,
                    color = if (activeHighlight.status == "VERIFIED") {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.75f)
                    }
                ),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .fillMaxWidth()
                    .testTag("dom_scanner_visual_indicator_pill")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (activeHighlight.status == "VERIFIED") {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.tertiaryContainer
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (activeHighlight.isScanning) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = if (activeHighlight.status == "VERIFIED") {
                                        Icons.Filled.CheckCircle
                                    } else {
                                        Icons.Outlined.DocumentScanner
                                    },
                                    contentDescription = activeHighlight.actionLabel,
                                    tint = if (activeHighlight.status == "VERIFIED") {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                            }
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
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = activeHighlight.actionLabel.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                        .testTag("dom_scanner_active_action_badge")
                                )
                            }
                            if (activeHighlight.totalSteps > 0) {
                                Text(
                                    text = "Step ${activeHighlight.stepIndex}/${activeHighlight.totalSteps}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("dom_scanner_active_step_counter")
                                )
                            }
                            if (activeHighlight.tagName.isNotBlank()) {
                                Text(
                                    text = "<${activeHighlight.tagName}>",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }

                        Text(
                            text = activeHighlight.targetLabel.ifBlank { activeHighlight.selector },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("dom_scanner_active_target_label")
                        )

                        if (activeHighlight.selector.isNotBlank()) {
                            Text(
                                text = activeHighlight.selector,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("dom_scanner_active_selector_label")
                            )
                        }
                    }

                    IconButton(
                        onClick = onToggleOutlines,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("dom_scanner_toggle_outlines_btn")
                    ) {
                        Icon(
                            imageVector = if (isOutlineEnabled) {
                                Icons.Outlined.Visibility
                            } else {
                                Icons.Outlined.VisibilityOff
                            },
                            contentDescription = if (isOutlineEnabled) {
                                "Hide DOM element outlines"
                            } else {
                                "Show DOM element outlines"
                            },
                            tint = if (isOutlineEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onDismissHighlight,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("dom_scanner_dismiss_indicator_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Dismiss DOM indicator",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
