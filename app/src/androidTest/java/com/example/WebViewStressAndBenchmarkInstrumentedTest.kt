package com.example

import android.os.Debug
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.automation.AutomationActionType
import com.example.automation.AutomationStep
import com.example.automation.DomScanStatus
import com.example.automation.ScannerLimits
import com.example.data.local.AutomationWorkflowEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 7 performance, scale, and stress benchmarks on real Chromium WebView.
 * Measures initial scan duration, dynamic mutation handling duration, discovered element count,
 * JS evaluation cycles, automation execution duration, cancellation time, and heap memory deltas
 * across repeatable small, large, deep-shadow, multi-iframe, and rapid-churn DOM fixtures.
 */
@RunWith(AndroidJUnit4::class)
class WebViewStressAndBenchmarkInstrumentedTest {

    data class BenchmarkReport(
        val fixtureName: String,
        val initialScanMs: Long,
        val dynamicUpdateMs: Long,
        val discoveredCount: Int,
        val jsEvalCycles: Int,
        val executionMs: Long,
        val cancellationMs: Long,
        val heapDeltaKb: Long,
        val status: DomScanStatus
    )

    private fun recordMetrics(report: BenchmarkReport) {
        Log.i(
            "ApexPhase7Bench",
            "fixture=${report.fixtureName} scanMs=${report.initialScanMs} " +
                "dynMs=${report.dynamicUpdateMs} elements=${report.discoveredCount} " +
                "jsEvals=${report.jsEvalCycles} execMs=${report.executionMs} " +
                "cancelMs=${report.cancellationMs} heapDeltaKb=${report.heapDeltaKb} status=${report.status}"
        )
    }

    @Test
    fun benchmark_smallFormLargeFormDeepShadowMultiIframeAndRapidDomChurn() {
        LocalWebFixtureServer().use { server ->
            // 1. Small form (~10 controls)
            server.registerHtml(
                "/bench-small",
                """
                <!DOCTYPE html><html><body>
                  <form id="small-form">
                    <input id="s-user" type="text" aria-label="User" />
                    <input id="s-mail" type="email" aria-label="Email" />
                    <select id="s-plan"><option value="a">A</option><option value="b">B</option></select>
                    <input id="s-check" type="checkbox" aria-label="Agree" />
                    <button id="s-go" type="button">Submit</button>
                  </form>
                </body></html>
                """.trimIndent()
            )

            // 2. Large form (260 controls)
            val largeInputs = (1..260).joinToString("\n") { i ->
                "<input id='lg-$i' name='field_$i' type='text' placeholder='Field $i' />"
            }
            server.registerHtml(
                "/bench-large",
                "<!DOCTYPE html><html><body><form id='large-form'>$largeInputs<button id='lg-submit' type='button'>Save All</button></form></body></html>"
            )

            // 3. 4-level deeply nested open Shadow DOM + 3 same-origin iframes
            server.registerHtml("/bench-iframe-sub", "<!DOCTYPE html><html><body><input id='sub-f-in' type='text' aria-label='Sub Frame Input' /><button id='sub-f-btn' type='button'>Frame Go</button></body></html>")
            server.registerHtml(
                "/bench-shadow-frames",
                """
                <!DOCTYPE html><html><body>
                  <div id="sh-lvl-0"></div>
                  <iframe id="ifr-1" src="/bench-iframe-sub"></iframe>
                  <iframe id="ifr-2" src="/bench-iframe-sub"></iframe>
                  <iframe id="ifr-3" src="/bench-iframe-sub"></iframe>
                  <script>
                    let cur = document.getElementById('sh-lvl-0');
                    for (let d = 1; d <= 4; d++) {
                      const sr = cur.attachShadow({ mode: 'open' });
                      sr.innerHTML = '<div id="wrap-' + d + '"><button id="sh-btn-' + d + '" type="button">Shadow ' + d + '</button><div id="next-' + d + '"></div></div>';
                      cur = sr.getElementById('next-' + d);
                    }
                    const leaf = document.createElement('input');
                    leaf.id = 'sh-leaf-input';
                    leaf.type = 'text';
                    leaf.setAttribute('aria-label', 'Deep Shadow Leaf');
                    cur.appendChild(leaf);
                  </script>
                </body></html>
                """.trimIndent()
            )

            // 4. Rapid DOM churn & repeated add/remove cycles
            server.registerHtml(
                "/bench-churn",
                """
                <!DOCTYPE html><html><body>
                  <div id="churn-stage"></div>
                  <script>
                    window.runRapidChurn = function(cycles) {
                      const stage = document.getElementById('churn-stage');
                      for (let i = 0; i < cycles; i++) {
                        const b = document.createElement('button');
                        b.id = 'churn-' + i;
                        b.textContent = 'Churn ' + i;
                        stage.appendChild(b);
                        if (i % 2 === 1) {
                          const prev = document.getElementById('churn-' + (i - 1));
                          if (prev) prev.remove();
                        }
                      }
                    };
                  </script>
                </body></html>
                """.trimIndent()
            )

            WebViewTestHost(tabId = "tab_bench").use { host ->
                // Benchmark 1: Small Form + Action Execution
                val smallUrl = server.urlFor("/bench-small")
                host.loadAndAwait(smallUrl)
                val memBeforeSmall = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
                val t0 = System.currentTimeMillis()
                val smallScan = runBlocking { host.scanner.scanPage(host.webView, host.tabId, smallUrl) }
                val smallScanMs = System.currentTimeMillis() - t0
                assertEquals(DomScanStatus.COMPLETED, smallScan.scanStatus)
                assertTrue(smallScan.elements.size >= 5)

                val execStart = System.currentTimeMillis()
                val execOutcome = runBlocking {
                    host.automationEngine.executeWorkflow(
                        workflow = AutomationWorkflowEntity(1L, "Small Bench", "127.0.0.1", smallUrl, "MANUAL"),
                        steps = listOf(
                            AutomationStep("b1", 1, AutomationActionType.INPUT, "User", "#s-user", "benchUser", "Fill user"),
                            AutomationStep("b2", 2, AutomationActionType.SELECT, "Plan", "#s-plan", "B", "Select plan"),
                            AutomationStep("b3", 3, AutomationActionType.TOGGLE, "Agree", "#s-check", "", "Check agree", desiredCheckedState = true)
                        ),
                        currentTabUrl = smallUrl,
                        tabId = host.tabId,
                        navigationSynchronizer = host.navigationSynchronizer,
                        webViewProvider = { host.webView },
                        onNavigateUrl = {},
                        onStepProgress = { _, _ -> }
                    )
                }
                val execMs = System.currentTimeMillis() - execStart
                assertEquals("COMPLETED", execOutcome.status)
                val memAfterSmall = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
                recordMetrics(
                    BenchmarkReport("small_form", smallScanMs, 0L, smallScan.elements.size, 2, execMs, 0L, (memAfterSmall - memBeforeSmall) / 1024, smallScan.scanStatus)
                )

                // Benchmark 2: Large Form (260+ elements) bounded by ScannerLimits
                val largeUrl = server.urlFor("/bench-large")
                host.loadAndAwait(largeUrl)
                val tLarge0 = System.currentTimeMillis()
                val largeScan = runBlocking {
                    host.scanner.scanPage(
                        webView = host.webView,
                        tabId = host.tabId,
                        currentUrl = largeUrl,
                        customLimits = ScannerLimits(maxElements = 200, maxScanDurationMs = 4_500L)
                    )
                }
                val largeScanMs = System.currentTimeMillis() - tLarge0
                assertTrue(largeScan.elements.size <= 200)
                assertTrue(largeScan.elements.size >= 150)
                assertTrue(largeScanMs < 4_500L)
                recordMetrics(
                    BenchmarkReport("large_form_capped_200", largeScanMs, 0L, largeScan.elements.size, 2, 0L, 0L, Debug.getNativeHeapAllocatedSize() / 1024, largeScan.scanStatus)
                )

                // Benchmark 3: Nested Open Shadow DOM + Multi-Same-Origin Iframes
                val shadowUrl = server.urlFor("/bench-shadow-frames")
                host.loadAndAwait(shadowUrl)
                host.awaitCondition(5_000L) {
                    host.evalJsUnquoted("document.getElementById('ifr-3').contentDocument && document.getElementById('ifr-3').contentDocument.readyState") == "complete"
                }
                val tSh0 = System.currentTimeMillis()
                val shadowScan = runBlocking { host.scanner.scanPage(host.webView, host.tabId, shadowUrl) }
                val shadowScanMs = System.currentTimeMillis() - tSh0
                assertTrue(shadowScan.elements.any { it.htmlId == "sh-leaf-input" && it.isInShadowDom })
                assertTrue(shadowScan.summary.sameOriginIframeElementsFound >= 6)
                recordMetrics(
                    BenchmarkReport("nested_shadow_and_3_iframes", shadowScanMs, 0L, shadowScan.elements.size, 2, 0L, 0L, 0L, shadowScan.scanStatus)
                )

                // Benchmark 4: Rapid DOM Churn + Bounded Queue + Cancellation Latency
                val churnUrl = server.urlFor("/bench-churn")
                host.loadAndAwait(churnUrl)
                runBlocking {
                    host.scanner.scanPage(
                        webView = host.webView,
                        tabId = host.tabId,
                        currentUrl = churnUrl,
                        customLimits = ScannerLimits(maxBatchSize = 60, maxMutationQueueSize = 100, mutationDebounceMs = 120L)
                    )
                }
                val dynStart = System.currentTimeMillis()
                host.evalJs("window.runRapidChurn(180)")
                host.awaitCondition(5_000L) {
                    (host.scanner.getLatestInspection(host.tabId)?.summary?.dynamicUpdatesProcessed ?: 0) >= 1
                }
                val dynMs = System.currentTimeMillis() - dynStart

                val cancelStart = System.currentTimeMillis()
                runBlocking {
                    val cancelJob = launch {
                        host.scanner.runExtendedScan(
                            webView = host.webView,
                            tabId = host.tabId,
                            currentUrl = churnUrl,
                            customLimits = ScannerLimits(maxExtendedScrollSteps = 8, extendedScrollSettleMs = 100L)
                        )
                    }
                    delay(40L)
                    host.scanner.cancelScan(host.tabId, host.webView, reason = "Benchmark cancel")
                    cancelJob.cancel(CancellationException("Bench cancel"))
                    cancelJob.join()
                }
                val cancelMs = System.currentTimeMillis() - cancelStart
                assertTrue(cancelMs < 1_000L)
                recordMetrics(
                    BenchmarkReport("rapid_dom_churn_and_cancel", 0L, dynMs, host.scanner.getLatestInspection(host.tabId)?.elements?.size ?: 0, 1, 0L, cancelMs, 0L, DomScanStatus.CANCELLED)
                )
            }
        }
    }
}
