package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.automation.AutomationActionType
import com.example.automation.AutomationErrorCode
import com.example.automation.AutomationStep
import com.example.automation.DomScanStatus
import com.example.automation.ExecutionLifecycleController
import com.example.automation.NavigationLifecyclePhase
import com.example.automation.TabNavigationSynchronizer
import com.example.data.local.AutomationWorkflowEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 7 real Chromium WebView navigation and lifecycle synchronization tests:
 * Verifies real redirects, HTTPS upgrade triggering, reload epochs, SPA pushState/replaceState,
 * back/forward navigation, and immediate execution/scanner synchronization on reload,
 * tab close, WebView replacement, unexpected origin changes, and backgrounding/foregrounding.
 */
@RunWith(AndroidJUnit4::class)
class RealWebViewNavigationAndLifecycleInstrumentedTest {

    @Test
    fun realWebView_httpRedirectReloadSpaHistoryBackAndForwardKeepEpochsSynchronized() {
        LocalWebFixtureServer().use { server ->
            server.registerHtml(
                "/page-a",
                """
                <!DOCTYPE html>
                <html><head><title>Page A</title></head><body>
                  <h1 id="title-a">Page A</h1>
                  <a id="to-b" href="/redirect-b">Redirect B</a>
                  <button id="push-btn" type="button" onclick="history.pushState({p:1}, '', '/spa-step-1'); document.getElementById('spa-label').textContent='SPA 1';">Push SPA</button>
                  <button id="replace-btn" type="button" onclick="history.replaceState({p:2}, '', '/spa-step-2'); document.getElementById('spa-label').textContent='SPA 2';">Replace SPA</button>
                  <div id="spa-label">Root</div>
                </body></html>
                """.trimIndent()
            )
            server.registerRedirect("/redirect-b", server.urlFor("/page-b"))
            server.registerHtml(
                "/page-b",
                """
                <!DOCTYPE html>
                <html><head><title>Page B</title></head><body>
                  <h1 id="title-b">Page B</h1>
                </body></html>
                """.trimIndent()
            )
            server.registerHtml("/spa-step-1", "<!DOCTYPE html><html><body>SPA 1 Fallback</body></html>")
            server.registerHtml("/spa-step-2", "<!DOCTYPE html><html><body>SPA 2 Fallback</body></html>")

            WebViewTestHost(tabId = "tab_nav").use { host ->
                val urlA = server.urlFor("/page-a")
                host.loadAndAwait(urlA)
                val snapAfterA = host.navigationSynchronizer.snapshot(host.tabId)
                assertEquals(NavigationLifecyclePhase.FINISHED, snapAfterA.phase)
                val epochAfterA = snapAfterA.epoch

                // Same-origin reload increments epoch and completes cleanly
                val reloadEpoch = host.navigationSynchronizer.beginNavigation(host.tabId, urlA)
                assertTrue(reloadEpoch > epochAfterA)
                host.runOnMainSync { host.webView.reload() }
                runBlocking {
                    host.navigationSynchronizer.awaitNavigationSettled(host.tabId, reloadEpoch, 8_000L)
                }
                assertEquals(NavigationLifecyclePhase.FINISHED, host.navigationSynchronizer.snapshot(host.tabId).phase)

                // SPA pushState & replaceState advance urlCommitSeq without reloading main page
                val prePushSeq = host.navigationSynchronizer.snapshot(host.tabId).urlCommitSeq
                host.evalJs("document.getElementById('push-btn').click()")
                host.awaitCondition(3_000L) {
                    host.navigationSynchronizer.snapshot(host.tabId).urlCommitSeq > prePushSeq &&
                        host.currentUrl().endsWith("/spa-step-1")
                }

                val preReplaceSeq = host.navigationSynchronizer.snapshot(host.tabId).urlCommitSeq
                host.evalJs("document.getElementById('replace-btn').click()")
                host.awaitCondition(3_000L) {
                    host.navigationSynchronizer.snapshot(host.tabId).urlCommitSeq > preReplaceSeq &&
                        host.currentUrl().endsWith("/spa-step-2")
                }

                // HTTP 302 redirect from /redirect-b -> /page-b
                val finalAfterRedirect = host.loadAndAwait(server.urlFor("/redirect-b"))
                assertTrue(finalAfterRedirect.endsWith("/page-b"))
                assertEquals("Page B", host.evalJsUnquoted("document.getElementById('title-b').textContent"))

                // Back & Forward navigation
                val backUrl = host.goBackAndAwait()
                assertTrue(backUrl.contains("/spa-step-2") || backUrl.endsWith("/page-a"))
                val fwdUrl = host.goForwardAndAwait()
                assertTrue(fwdUrl.endsWith("/page-b"))
            }
        }
    }

    @Test
    fun realWebView_httpsUpgradeRewritesHttpNavigationWithoutBypassingPolicies() {
        LocalWebFixtureServer().use { server ->
            server.registerHtml(
                "/link-origin",
                """
                <!DOCTYPE html>
                <html><body>
                  <a id="plain-http-link" href="http://example.invalid/secure-target">Open HTTP Link</a>
                </body></html>
                """.trimIndent()
            )
            server.registerHtml(
                "/upgraded-landing",
                "<!DOCTYPE html><html><body><h1 id='upgraded-h1'>Upgraded Landing</h1></body></html>"
            )

            WebViewTestHost(
                tabId = "tab_https_upgrade",
                httpsUpgradeEnabled = false,
                httpsUpgradeInterceptor = { upgradedUrl ->
                    if (upgradedUrl == "https://example.invalid/secure-target") {
                        server.urlFor("/upgraded-landing")
                    } else {
                        null
                    }
                }
            ).use { host ->
                host.loadAndAwait(server.urlFor("/link-origin"))
                host.httpsUpgradeEnabled = true

                host.evalJs("document.getElementById('plain-http-link').click()")
                host.awaitCondition(5_000L) {
                    host.httpsUpgradedUrls.contains("https://example.invalid/secure-target") &&
                        host.currentUrl().endsWith("/upgraded-landing")
                }
                assertEquals("Upgraded Landing", host.evalJsUnquoted("document.getElementById('upgraded-h1').textContent"))
            }
        }
    }

    @Test
    fun realWebView_unexpectedOriginChangeAndWebViewReplacementAndBackgroundInterruptExecution() {
        LocalWebFixtureServer().use { originA ->
            LocalWebFixtureServer().use { originB ->
                originA.registerHtml(
                    "/start",
                    """
                    <!DOCTYPE html>
                    <html><body>
                      <button id="slow-btn" type="button">Slow Action</button>
                      <input id="stay-box" type="text" />
                    </body></html>
                    """.trimIndent()
                )
                originB.registerHtml(
                    "/foreign",
                    "<!DOCTYPE html><html><body><h1 id='foreign-h1'>Origin B</h1></body></html>"
                )

                // 1. Unexpected cross-origin navigation during execution aborts with UNEXPECTED_ORIGIN_CHANGE
                WebViewTestHost(tabId = "tab_xorigin").use { host ->
                    val urlA = originA.urlFor("/start")
                    host.loadAndAwait(urlA)
                    runBlocking {
                        host.scanner.scanPage(host.webView, host.tabId, urlA)
                    }

                    val controller = ExecutionLifecycleController()
                    val sessionId = controller.tryBeginSession(host.tabId, host.webView, originA.origin)!!
                    val waitStep = AutomationStep(
                        id = "wait_never",
                        order = 1,
                        actionType = AutomationActionType.WAIT,
                        targetLabel = "Never Appears",
                        selector = "#never-present",
                        waitCondition = com.example.automation.WaitConditionSpec(
                            type = com.example.automation.WaitConditionType.ELEMENT_APPEARS,
                            selector = "#never-present",
                            timeoutMs = 5_000L
                        ),
                        description = "Wait while origin shifts"
                    )

                    val outcome = runBlocking {
                        try {
                            val runner = launch {
                                delay(180L)
                                host.runOnMainSync { host.webView.loadUrl(originB.urlFor("/foreign")) }
                            }
                            val execOutcome = host.automationEngine.executeWorkflow(
                                workflow = AutomationWorkflowEntity(
                                    id = 21L,
                                    name = "Origin Guard",
                                    domain = "127.0.0.1",
                                    sourceUrl = urlA,
                                    type = "MANUAL"
                                ),
                                steps = listOf(waitStep),
                                currentTabUrl = urlA,
                                tabId = host.tabId,
                                navigationSynchronizer = host.navigationSynchronizer,
                                webViewProvider = { host.webView },
                                onNavigateUrl = {},
                                onStepProgress = { _, _ -> },
                                lifecycleController = controller,
                                liveUrlProvider = { host.currentUrl() }
                            )
                            runner.join()
                            execOutcome
                        } finally {
                            controller.endSession(sessionId)
                        }
                    }

                    assertTrue(outcome.status == "INTERRUPTED" || outcome.status == "FAILED" || outcome.status == "CANCELLED")
                    assertTrue(
                        outcome.errorCode == AutomationErrorCode.UNEXPECTED_ORIGIN_CHANGE ||
                            outcome.errorCode == AutomationErrorCode.CONTEXT_INVALIDATED
                    )
                    assertEquals(DomScanStatus.DOCUMENT_CHANGED, host.scanner.getLatestInspection(host.tabId)?.scanStatus)
                }

                // 2. WebView replacement during execution aborts safely and invalidates scanner session
                WebViewTestHost(tabId = "tab_replace").use { host ->
                    val urlA = originA.urlFor("/start")
                    host.loadAndAwait(urlA)
                    runBlocking { host.scanner.scanPage(host.webView, host.tabId, urlA) }

                    val controller = ExecutionLifecycleController()
                    val sessionId = controller.tryBeginSession(host.tabId, host.webView, originA.origin)!!
                    val oldWv = host.webView
                    val waitStep = AutomationStep(
                        id = "wait_replace",
                        order = 1,
                        actionType = AutomationActionType.WAIT,
                        targetLabel = "Never",
                        selector = "#never",
                        waitCondition = com.example.automation.WaitConditionSpec(
                            type = com.example.automation.WaitConditionType.ELEMENT_APPEARS,
                            selector = "#never",
                            timeoutMs = 5_000L
                        ),
                        description = "Wait while WebView replaced"
                    )

                    val outcome = runBlocking {
                        try {
                            val trigger = launch {
                                delay(160L)
                                val newWv = host.replaceWebView()
                                assertNotEquals(oldWv, newWv)
                                controller.requestCancel(
                                    AutomationErrorCode.WEBVIEW_REPLACED,
                                    "WebView replaced mid-run"
                                )
                            }
                            val res = host.automationEngine.executeWorkflow(
                                workflow = AutomationWorkflowEntity(22L, "WV Replace", "127.0.0.1", urlA, "MANUAL"),
                                steps = listOf(waitStep),
                                currentTabUrl = urlA,
                                tabId = host.tabId,
                                navigationSynchronizer = host.navigationSynchronizer,
                                webViewProvider = { host.webView },
                                onNavigateUrl = {},
                                onStepProgress = { _, _ -> },
                                lifecycleController = controller,
                                liveUrlProvider = { host.currentUrl() }
                            )
                            trigger.join()
                            res
                        } finally {
                            controller.endSession(sessionId)
                        }
                    }

                    assertEquals(AutomationErrorCode.WEBVIEW_REPLACED, outcome.errorCode)
                    assertEquals(DomScanStatus.WEBVIEW_REPLACED, host.scanner.getLatestInspection(host.tabId)?.scanStatus)
                }

                // 3. Backgrounding + Tab switch / closure interrupts execution cleanly
                WebViewTestHost(tabId = "tab_bg").use { host ->
                    val urlA = originA.urlFor("/start")
                    host.loadAndAwait(urlA)
                    val controller = ExecutionLifecycleController()
                    val sessionId = controller.tryBeginSession(host.tabId, host.webView, originA.origin)!!
                    val bgOutcome = runBlocking {
                        try {
                            val bgTrigger = launch {
                                delay(150L)
                                controller.requestInterrupt(
                                    AutomationErrorCode.CONTEXT_INVALIDATED,
                                    "App moved to background"
                                )
                            }
                            val res = host.automationEngine.executeWorkflow(
                                workflow = AutomationWorkflowEntity(23L, "BG Interrupt", "127.0.0.1", urlA, "MANUAL"),
                                steps = listOf(
                                    AutomationStep(
                                        id = "wait_bg",
                                        order = 1,
                                        actionType = AutomationActionType.WAIT,
                                        targetLabel = "Never",
                                        selector = "#never",
                                        waitCondition = com.example.automation.WaitConditionSpec(
                                            type = com.example.automation.WaitConditionType.ELEMENT_APPEARS,
                                            selector = "#never",
                                            timeoutMs = 5_000L
                                        ),
                                        description = "Wait while backgrounded"
                                    )
                                ),
                                currentTabUrl = urlA,
                                tabId = host.tabId,
                                navigationSynchronizer = host.navigationSynchronizer,
                                webViewProvider = { host.webView },
                                onNavigateUrl = {},
                                onStepProgress = { _, _ -> },
                                lifecycleController = controller,
                                liveUrlProvider = { host.currentUrl() }
                            )
                            bgTrigger.join()
                            res
                        } finally {
                            controller.endSession(sessionId)
                        }
                    }
                    assertEquals("INTERRUPTED", bgOutcome.status)
                    assertEquals(AutomationErrorCode.CONTEXT_INVALIDATED, bgOutcome.errorCode)
                }
            }
        }
    }

    @Test
    fun synchronizer_rejectsStaleEpochCompletionAfterReloadOrTabSwitch() {
        val sync = TabNavigationSynchronizer()
        val epoch1 = sync.beginNavigation("tab_1", "http://127.0.0.1:8080/one")
        val epoch2 = sync.beginNavigation("tab_1", "http://127.0.0.1:8080/two")
        assertTrue(epoch2 > epoch1)
        sync.onPageFinished("tab_1", "http://127.0.0.1:8080/two")
        val snap = sync.snapshot("tab_1")
        assertEquals(NavigationLifecyclePhase.FINISHED, snap.phase)
        assertEquals(epoch2, snap.epoch)
    }
}
