package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.automation.ControlKind
import com.example.automation.DomScanStatus
import com.example.automation.DropdownKind
import com.example.automation.ScannerLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 7 real Chromium WebView instrumentation tests for UniversalDomScanner:
 * Exercises actual DOM, Shadow DOM, iframes, MutationObserver, scrolling, and observer cleanup
 * against deterministic local HTTP servers with NO mocked evaluateJavascript responses.
 */
@RunWith(AndroidJUnit4::class)
class RealWebViewDomScannerInstrumentedTest {

    @Test
    fun realWebView_discoversStandardInputsContentEditableDropdownsRadiosDatesAndAria() {
        LocalWebFixtureServer().use { server ->
            server.registerHtml(
                "/controls",
                """
                <!DOCTYPE html>
                <html>
                <head><meta charset="utf-8"><title>Controls Fixture</title></head>
                <body>
                  <form id="profile-form" name="profileForm">
                    <label for="full-name">Full Name</label>
                    <input id="full-name" name="fullName" type="text" placeholder="Enter full name" />

                    <label for="bio-area">Biography</label>
                    <textarea id="bio-area" name="bio" placeholder="Tell us about you"></textarea>

                    <div id="rich-notes" contenteditable="true" role="textbox" aria-label="Rich Notes" style="min-height:24px;border:1px solid #333;">Initial note</div>

                    <label for="country-select">Country</label>
                    <select id="country-select" name="country">
                      <option value="us">United States</option>
                      <option value="ca">Canada</option>
                      <option value="uk">United Kingdom</option>
                    </select>

                    <div id="role-dropdown" role="combobox" aria-label="Workspace Role" aria-expanded="false" aria-controls="role-listbox" tabindex="0">Viewer</div>
                    <ul id="role-listbox" role="listbox" aria-label="Role Options">
                      <li id="role-opt-admin" role="option" aria-selected="false">Admin</li>
                      <li id="role-opt-editor" role="option" aria-selected="true">Editor</li>
                    </ul>

                    <fieldset>
                      <legend>Contact Method</legend>
                      <label for="contact-email">Email Option</label>
                      <input id="contact-email" type="radio" name="contact_pref" value="email" checked />
                      <label for="contact-sms">SMS Option</label>
                      <input id="contact-sms" type="radio" name="contact_pref" value="sms" />
                    </fieldset>

                    <label for="terms-box">Accept Terms</label>
                    <input id="terms-box" type="checkbox" name="terms" />

                    <label for="start-date">Start Date</label>
                    <input id="start-date" type="date" min="2024-01-01" max="2028-12-31" value="2026-06-15" />

                    <label for="meeting-time">Meeting Time</label>
                    <input id="meeting-time" type="time" value="09:30" />

                    <button id="alerts-switch" type="button" role="switch" aria-checked="true" aria-controls="alerts-panel" aria-expanded="true">Enable Alerts</button>
                    <div id="alerts-panel">Panel</div>
                  </form>
                </body>
                </html>
                """.trimIndent()
            )

            WebViewTestHost(tabId = "tab_controls").use { host ->
                val url = server.urlFor("/controls")
                host.loadAndAwait(url)

                val state = runBlocking {
                    host.scanner.scanPage(
                        webView = host.webView,
                        tabId = host.tabId,
                        currentUrl = url,
                        navigationEpoch = host.navigationSynchronizer.snapshot(host.tabId).epoch,
                        installDynamicObserver = true
                    )
                }

                assertEquals(DomScanStatus.COMPLETED, state.scanStatus)
                assertTrue(state.isDynamicObserverActive)
                assertTrue(state.elements.any { it.htmlId == "full-name" && it.controlKind == ControlKind.STANDARD_INPUT })
                assertTrue(state.elements.any { it.htmlId == "bio-area" && it.controlKind == ControlKind.TEXTAREA })
                assertTrue(state.elements.any { it.htmlId == "rich-notes" && it.controlKind == ControlKind.CONTENTEDITABLE })
                assertTrue(state.elements.any { it.htmlId == "country-select" && it.controlKind == ControlKind.SELECT && it.dropdownKind == DropdownKind.NATIVE_SELECT })
                assertTrue(state.elements.any { it.htmlId == "role-dropdown" && it.controlKind == ControlKind.CUSTOM_DROPDOWN && it.dropdownKind == DropdownKind.CUSTOM_COMBOBOX })
                assertTrue(state.elements.any { it.htmlId == "contact-email" && it.controlKind == ControlKind.RADIO && it.checked == true })
                assertTrue(state.elements.any { it.htmlId == "contact-sms" && it.controlKind == ControlKind.RADIO && it.checked == false })
                assertTrue(state.elements.any { it.htmlId == "terms-box" && it.controlKind == ControlKind.CHECKBOX })
                assertTrue(state.elements.any { it.htmlId == "start-date" && it.controlKind == ControlKind.DATE_TIME && it.dateTimeMetadata?.controlType == "date" })
                assertTrue(state.elements.any { it.htmlId == "meeting-time" && it.controlKind == ControlKind.DATE_TIME && it.dateTimeMetadata?.controlType == "time" })
                val switchEl = state.elements.firstOrNull { it.htmlId == "alerts-switch" }
                assertNotNull(switchEl)
                assertEquals("alerts-panel", switchEl!!.controlledElementId)
                assertEquals("true", switchEl.ariaAttributes["aria-expanded"])
            }
        }
    }

    @Test
    fun realWebView_traversesOpenShadowDomAndSameOriginIframe_blocksCrossOriginAndClosedShadow() {
        LocalWebFixtureServer().use { originA ->
            LocalWebFixtureServer().use { originB ->
                originB.registerHtml(
                    "/cross-origin-secret",
                    """
                    <!DOCTYPE html>
                    <html><body>
                      <form><input id="xorigin-secret-input" type="password" value="should_not_leak" /></form>
                    </body></html>
                    """.trimIndent()
                )
                originA.registerHtml(
                    "/same-origin-frame",
                    """
                    <!DOCTYPE html>
                    <html><body>
                      <label for="iframe-note">Iframe Note</label>
                      <input id="iframe-note" type="text" placeholder="Same origin frame input" />
                      <button id="iframe-btn" type="button">Iframe Save</button>
                    </body></html>
                    """.trimIndent()
                )
                originA.registerHtml(
                    "/boundaries",
                    """
                    <!DOCTYPE html>
                    <html>
                    <head><meta charset="utf-8"></head>
                    <body>
                      <div id="open-shadow-host"></div>
                      <div id="closed-shadow-host"></div>
                      <iframe id="same-frame" src="/same-origin-frame" width="320" height="160"></iframe>
                      <iframe id="cross-frame" src="${originB.urlFor("/cross-origin-secret")}" width="320" height="160"></iframe>
                      <script>
                        const openHost = document.getElementById('open-shadow-host');
                        const openRoot = openHost.attachShadow({ mode: 'open' });
                        openRoot.innerHTML = '<label for="shadow-email">Shadow Email</label><input id="shadow-email" type="email" placeholder="Shadow user" /><button id="shadow-submit" type="button">Shadow Go</button>';

                        const closedHost = document.getElementById('closed-shadow-host');
                        const closedRoot = closedHost.attachShadow({ mode: 'closed' });
                        closedRoot.innerHTML = '<input id="closed-secret-input" type="text" value="sealed" />';
                      </script>
                    </body>
                    </html>
                    """.trimIndent()
                )

                WebViewTestHost(tabId = "tab_boundaries").use { host ->
                    val url = originA.urlFor("/boundaries")
                    host.loadAndAwait(url)
                    // Wait for both iframes to complete loading inside Chromium
                    host.awaitCondition(5_000L) {
                        val sameReady = host.evalJsUnquoted("document.getElementById('same-frame').contentDocument && document.getElementById('same-frame').contentDocument.readyState")
                        sameReady == "complete"
                    }

                    val state = runBlocking {
                        host.scanner.scanPage(
                            webView = host.webView,
                            tabId = host.tabId,
                            currentUrl = url,
                            navigationEpoch = host.navigationSynchronizer.snapshot(host.tabId).epoch,
                            installDynamicObserver = true
                        )
                    }

                    assertEquals(DomScanStatus.COMPLETED, state.scanStatus)
                    val shadowInput = state.elements.firstOrNull { it.htmlId == "shadow-email" }
                    assertNotNull(shadowInput)
                    assertTrue(shadowInput!!.isInShadowDom)
                    assertTrue(shadowInput.selector.contains(">>>"))

                    val sameFrameInput = state.elements.firstOrNull { it.htmlId == "iframe-note" }
                    assertNotNull(sameFrameInput)
                    assertTrue(sameFrameInput!!.isInSameOriginIframe)
                    assertTrue(sameFrameInput.selector.contains(">>"))

                    // Cross-origin & closed shadow boundaries must NEVER leak child DOM controls
                    assertFalse(state.elements.any { it.htmlId == "xorigin-secret-input" })
                    assertFalse(state.elements.any { it.htmlId == "closed-secret-input" })
                    assertTrue(state.inaccessibleFrames.any { it.reason == "cross-origin" && it.frameOrigin == originB.origin })
                }
            }
        }
    }

    @Test
    fun realWebView_dynamicMutationObserverDetectsAddedAndRemovedElements_andCleanupStopsCallbacks() {
        LocalWebFixtureServer().use { server ->
            server.registerHtml(
                "/dynamic",
                """
                <!DOCTYPE html>
                <html><body>
                  <div id="container">
                    <button id="initial-btn" type="button">Initial</button>
                  </div>
                  <script>
                    window.triggerDomMutation = function() {
                      const c = document.getElementById('container');
                      const oldBtn = document.getElementById('initial-btn');
                      if (oldBtn) oldBtn.remove();
                      const newInput = document.createElement('input');
                      newInput.id = 'late-field';
                      newInput.type = 'text';
                      newInput.setAttribute('aria-label', 'Late Field');
                      c.appendChild(newInput);
                    };
                    window.triggerSecondMutation = function() {
                      const c = document.getElementById('container');
                      const extra = document.createElement('button');
                      extra.id = 'after-cancel-btn';
                      extra.textContent = 'After Cancel';
                      c.appendChild(extra);
                    };
                  </script>
                </body></html>
                """.trimIndent()
            )

            WebViewTestHost(tabId = "tab_dynamic").use { host ->
                val url = server.urlFor("/dynamic")
                host.loadAndAwait(url)

                val initialState = runBlocking {
                    host.scanner.scanPage(
                        webView = host.webView,
                        tabId = host.tabId,
                        currentUrl = url,
                        navigationEpoch = host.navigationSynchronizer.snapshot(host.tabId).epoch,
                        installDynamicObserver = true
                    )
                }
                assertTrue(initialState.isDynamicObserverActive)
                assertTrue(initialState.elements.any { it.htmlId == "initial-btn" && !it.stale })

                // Mutate real Chromium DOM; MutationObserver batches via ApexAutomationBridge
                host.evalJs("window.triggerDomMutation()")
                host.awaitCondition(5_000L) {
                    val latest = host.scanner.getLatestInspection(host.tabId)
                    latest != null &&
                        latest.elements.any { it.htmlId == "late-field" && !it.stale } &&
                        latest.elements.any { it.htmlId == "initial-btn" && it.stale }
                }

                val updated = host.scanner.getLatestInspection(host.tabId)!!
                assertTrue(updated.summary.dynamicUpdatesProcessed >= 1)
                assertTrue(updated.summary.staleElementsCount >= 1)

                // Cancel scan and verify JS MutationObserver disconnects cleanly
                val cancelled = host.scanner.cancelScan(host.tabId, host.webView, reason = "User cancelled test")
                assertEquals(DomScanStatus.CANCELLED, cancelled.scanStatus)
                assertFalse(cancelled.isDynamicObserverActive)

                host.awaitCondition(3_000L) {
                    host.evalJsUnquoted("String(window.__apexDomScannerObserver === null || typeof window.__apexDomScannerObserver === 'undefined')") == "true"
                }

                val batchesBefore = host.mutationStates.size
                host.evalJs("window.triggerSecondMutation()")
                Thread.sleep(350)
                assertEquals(batchesBefore, host.mutationStates.size)
            }
        }
    }

    @Test
    fun realWebView_offScreenElementsAndExtendedScanRestoresOriginalScrollPosition() {
        LocalWebFixtureServer().use { server ->
            val filler = "<div style='height:900px'>Spacer</div>".repeat(3)
            server.registerHtml(
                "/long-page",
                """
                <!DOCTYPE html>
                <html><body>
                  <button id="top-btn" type="button">Top Button</button>
                  <div style="height:260px">Intro</div>
                  $filler
                  <div id="lazy-slot"></div>
                  <button id="bottom-btn" type="button">Bottom Button</button>
                  <script>
                    window.scrollTo(0, 120);
                    let injected = false;
                    window.addEventListener('scroll', function() {
                      if (!injected && window.scrollY > 400) {
                        injected = true;
                        const btn = document.createElement('button');
                        btn.id = 'lazy-scroll-btn';
                        btn.textContent = 'Lazy Scroll Action';
                        document.getElementById('lazy-slot').appendChild(btn);
                      }
                    });
                  </script>
                </body></html>
                """.trimIndent()
            )

            WebViewTestHost(tabId = "tab_scroll").use { host ->
                val url = server.urlFor("/long-page")
                host.loadAndAwait(url)
                host.awaitCondition(3_000L) {
                    (host.evalJsUnquoted("String(Math.round(window.scrollY))").toIntOrNull() ?: 0) >= 100
                }
                val startY = host.evalJsUnquoted("String(Math.round(window.scrollY))").toInt()

                val extendedState = runBlocking {
                    host.scanner.runExtendedScan(
                        webView = host.webView,
                        tabId = host.tabId,
                        currentUrl = url,
                        navigationEpoch = host.navigationSynchronizer.snapshot(host.tabId).epoch,
                        customLimits = ScannerLimits(
                            maxExtendedScrollSteps = 6,
                            extendedScrollStepPx = 650,
                            extendedScrollSettleMs = 80L
                        )
                    )
                }

                assertEquals(DomScanStatus.COMPLETED, extendedState.scanStatus)
                assertTrue(extendedState.elements.any { it.htmlId == "bottom-btn" })
                assertTrue(extendedState.elements.any { it.htmlId == "lazy-scroll-btn" })
                assertTrue(extendedState.summary.offScreenElementsFound >= 1)

                host.awaitCondition(3_000L) {
                    val restoredY = host.evalJsUnquoted("String(Math.round(window.scrollY))").toIntOrNull() ?: -1
                    kotlin.math.abs(restoredY - startY) <= 10
                }
            }
        }
    }

    @Test
    fun realWebView_coroutineCancellationCleansUpObserverAndReturnsControlCleanly() {
        LocalWebFixtureServer().use { server ->
            val many = (1..80).joinToString("\n") { i ->
                "<button id='btn-$i' type='button'>Action $i</button>"
            }
            server.registerHtml("/cancel-test", "<!DOCTYPE html><html><body>$many</body></html>")

            WebViewTestHost(tabId = "tab_cancel_scan").use { host ->
                val url = server.urlFor("/cancel-test")
                host.loadAndAwait(url)

                runBlocking {
                    val job = launch {
                        host.scanner.runExtendedScan(
                            webView = host.webView,
                            tabId = host.tabId,
                            currentUrl = url,
                            customLimits = ScannerLimits(
                                maxExtendedScrollSteps = 10,
                                extendedScrollSettleMs = 150L
                            )
                        )
                    }
                    delay(80L)
                    host.scanner.cancelScan(host.tabId, host.webView, reason = "Cancelled mid-extended-scan")
                    job.cancel(CancellationException("Aborted"))
                    job.join()
                }

                val cancelled = host.scanner.getLatestInspection(host.tabId)
                assertNotNull(cancelled)
                assertEquals(DomScanStatus.CANCELLED, cancelled!!.scanStatus)
                assertFalse(cancelled.isDynamicObserverActive)
            }
        }
    }
}
