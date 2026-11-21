package com.example

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.automation.AutomationEngine
import com.example.automation.NavigationWaitResult
import com.example.automation.PageInspectionState
import com.example.automation.TabNavigationSynchronizer
import com.example.automation.UniversalDomScanner
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking

/**
 * Attaches an actual Chromium WebView to a live Activity window with real viewport dimensions,
 * JS bridge (`ApexAutomationBridge`), and `TabNavigationSynchronizer` lifecycle wiring so Phase 7
 * instrumentation tests run against genuine Chromium DOM, Shadow DOM, iframes, and navigation.
 */
class WebViewTestHost(
    val tabId: String = "tab_real_webview_test",
    val automationEngine: AutomationEngine = AutomationEngine(),
    val navigationSynchronizer: TabNavigationSynchronizer = TabNavigationSynchronizer(),
    var httpsUpgradeEnabled: Boolean = false,
    private val httpsUpgradeInterceptor: ((String) -> String?)? = null
) : Closeable {

    private val scenario: ActivityScenario<MainActivity> = ActivityScenario.launch(MainActivity::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val hostContainerRef = AtomicReference<FrameLayout?>(null)
    private val webViewRef = AtomicReference<WebView?>(null)

    val scanner: UniversalDomScanner get() = automationEngine.universalScanner
    val mutationStates = CopyOnWriteArrayList<PageInspectionState>()
    val httpsUpgradedUrls = CopyOnWriteArrayList<String>()
    val urlHistory = CopyOnWriteArrayList<String>()
    val pageFinishedCount = AtomicInteger(0)

    @Volatile
    var lastErrorDescription: String? = null

    val webView: WebView
        get() = webViewRef.get() ?: error("WebView has been destroyed or not yet initialized")

    init {
        createAndAttachWebView()
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun createAndAttachWebView() {
        val latch = CountDownLatch(1)
        scenario.onActivity { activity ->
            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            val container = hostContainerRef.get() ?: FrameLayout(activity).also { frame ->
                root.addView(
                    frame,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                hostContainerRef.set(frame)
            }
            val wv = buildConfiguredWebView(activity)
            container.removeAllViews()
            container.addView(
                wv,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            webViewRef.set(wv)
            latch.countDown()
        }
        check(latch.await(10, TimeUnit.SECONDS)) { "Timed out attaching real WebView to Activity" }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun buildConfiguredWebView(activity: MainActivity): WebView {
        val wv = WebView(activity)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        wv.webChromeClient = WebChromeClient()
        wv.addJavascriptInterface(
            object {
                @JavascriptInterface
                fun onDomMutationBatchJson(payloadJson: String?) {
                    val raw = payloadJson?.trim().orEmpty()
                    if (raw.isEmpty()) return
                    wv.post {
                        val updated = scanner.processDynamicMutationBatch(
                            rawJson = raw,
                            sourceTabId = tabId,
                            sourceWebView = wv,
                            liveWebViewUrl = wv.url.orEmpty(),
                            currentNavigationEpoch = navigationSynchronizer.snapshot(tabId).epoch
                        )
                        if (updated != null) {
                            mutationStates.add(updated)
                        }
                    }
                }

                @JavascriptInterface
                fun onRecordedEventJson(payloadJson: String?) {
                    // Accepted for bridge compatibility during instrumentation tests
                }

                @JavascriptInterface
                fun onRecordedEvent(
                    actionType: String?,
                    targetLabel: String?,
                    selector: String?,
                    value: String?,
                    isDestructive: Boolean
                ) {
                    // Accepted for bridge compatibility during instrumentation tests
                }
            },
            "ApexAutomationBridge"
        )
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val isMainFrame = request?.isForMainFrame ?: true
                val reqUrl = request?.url?.toString().orEmpty()
                if (isMainFrame && reqUrl.startsWith("http://", ignoreCase = true) && httpsUpgradeEnabled) {
                    val upgraded = "https://" + reqUrl.substring(7)
                    httpsUpgradedUrls.add(upgraded)
                    val mappedLocalFallback = httpsUpgradeInterceptor?.invoke(upgraded)
                    if (mappedLocalFallback != null) {
                        view?.loadUrl(mappedLocalFallback)
                    } else {
                        view?.loadUrl(upgraded)
                    }
                    return true
                }
                return false
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (!url.isNullOrBlank()) {
                    navigationSynchronizer.onPageStarted(tabId, url)
                    scanner.onTabNavigated(tabId, url, view)
                }
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                if (!url.isNullOrBlank()) {
                    urlHistory.add(url)
                    navigationSynchronizer.onUrlCommitted(tabId, url, isReload = isReload)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (view == null || view.progress < 100) return
                val mainUrl = view.url?.trim().takeIf { !it.isNullOrBlank() } ?: url?.trim().orEmpty()
                if (mainUrl.isEmpty() || mainUrl.equals("about:blank", ignoreCase = true)) return
                if (!url.isNullOrBlank() && !view.url.isNullOrBlank() && !url.trim().equals(view.url?.trim(), ignoreCase = true)) {
                    return
                }
                pageFinishedCount.incrementAndGet()
                navigationSynchronizer.onPageFinished(tabId, mainUrl)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    val reason = error?.description?.toString() ?: "Load error"
                    lastErrorDescription = reason
                    navigationSynchronizer.onPageFailed(tabId, request.url?.toString().orEmpty(), reason)
                }
            }
        }
        return wv
    }

    fun replaceWebView(): WebView {
        val oldWv = webViewRef.get()
        val newWvRef = AtomicReference<WebView>()
        val latch = CountDownLatch(1)
        mainHandler.post {
            scenario.onActivity { activity ->
                val container = hostContainerRef.get()!!
                val newWv = buildConfiguredWebView(activity)
                container.removeAllViews()
                container.addView(
                    newWv,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                webViewRef.set(newWv)
                newWvRef.set(newWv)
                if (oldWv != null) {
                    scanner.onWebViewReplaced(tabId, oldWv, newWv)
                    runCatching { oldWv.removeJavascriptInterface("ApexAutomationBridge") }
                    runCatching { oldWv.stopLoading(); oldWv.destroy() }
                }
                latch.countDown()
            }
        }
        check(latch.await(10, TimeUnit.SECONDS)) { "Timed out replacing WebView" }
        return newWvRef.get()
    }

    fun loadAndAwait(url: String, timeoutMs: Long = 10_000L): String {
        lastErrorDescription = null
        val epoch = navigationSynchronizer.beginNavigation(tabId, url)
        runOnMainSync {
            webView.loadUrl(url)
        }
        val waitRes = runBlocking {
            navigationSynchronizer.awaitNavigationSettled(tabId, epoch, timeoutMs)
        }
        check(waitRes is NavigationWaitResult.Completed) {
            "Expected navigation to complete for $url, got $waitRes (lastError=$lastErrorDescription)"
        }
        awaitCondition(timeoutMs = 3_000L) {
            evalJs("document.readyState") == "\"complete\""
        }
        return currentUrl()
    }

    fun goBackAndAwait(timeoutMs: Long = 8_000L): String {
        val prevSeq = navigationSynchronizer.snapshot(tabId).urlCommitSeq
        val started = runOnMainSyncResult {
            if (webView.canGoBack()) {
                webView.goBack()
                true
            } else {
                false
            }
        }
        check(started) { "WebView cannot goBack() from ${currentUrl()}" }
        awaitCondition(timeoutMs) {
            val snap = navigationSynchronizer.snapshot(tabId)
            snap.urlCommitSeq > prevSeq && evalJs("document.readyState") == "\"complete\""
        }
        return currentUrl()
    }

    fun goForwardAndAwait(timeoutMs: Long = 8_000L): String {
        val prevSeq = navigationSynchronizer.snapshot(tabId).urlCommitSeq
        val started = runOnMainSyncResult {
            if (webView.canGoForward()) {
                webView.goForward()
                true
            } else {
                false
            }
        }
        check(started) { "WebView cannot goForward() from ${currentUrl()}" }
        awaitCondition(timeoutMs) {
            val snap = navigationSynchronizer.snapshot(tabId)
            snap.urlCommitSeq > prevSeq && evalJs("document.readyState") == "\"complete\""
        }
        return currentUrl()
    }

    fun reloadOnMain() {
        runOnMainSync {
            navigationSynchronizer.beginNavigation(tabId, webView.url.orEmpty())
            webView.reload()
        }
    }

    fun currentUrl(): String = runOnMainSyncResult { webView.url.orEmpty() }

    fun evalJs(script: String): String {
        val resultRef = AtomicReference<String>("")
        val latch = CountDownLatch(1)
        mainHandler.post {
            webView.evaluateJavascript(script) { value ->
                resultRef.set(value ?: "")
                latch.countDown()
            }
        }
        check(latch.await(5, TimeUnit.SECONDS)) { "Timed out waiting for WebView evaluateJavascript" }
        return resultRef.get()
    }

    fun evalJsUnquoted(script: String): String {
        val raw = evalJs(script).trim()
        return if (raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            org.json.JSONTokener(raw).nextValue().toString()
        } else {
            raw
        }
    }

    fun runOnMainSync(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
            return
        }
        val latch = CountDownLatch(1)
        val err = AtomicReference<Throwable?>(null)
        mainHandler.post {
            try {
                block()
            } catch (t: Throwable) {
                err.set(t)
            } finally {
                latch.countDown()
            }
        }
        check(latch.await(10, TimeUnit.SECONDS)) { "Timed out running main-thread WebView action" }
        err.get()?.let { throw it }
    }

    fun <T> runOnMainSyncResult(block: () -> T): T {
        val res = AtomicReference<T>()
        runOnMainSync { res.set(block()) }
        return res.get()
    }

    fun awaitCondition(timeoutMs: Long = 5_000L, pollMs: Long = 50L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(pollMs)
        }
        check(predicate()) { "Condition did not become true within ${timeoutMs}ms" }
    }

    override fun close() {
        runCatching {
            val wv = webViewRef.getAndSet(null)
            if (wv != null) {
                runOnMainSync {
                    runCatching { wv.removeJavascriptInterface("ApexAutomationBridge") }
                    wv.stopLoading()
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    wv.removeAllViews()
                    wv.destroy()
                }
            }
        }
        runCatching { scenario.close() }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }
}
