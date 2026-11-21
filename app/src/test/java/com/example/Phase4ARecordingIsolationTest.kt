package com.example

import android.content.Context
import android.os.Looper
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.automation.ActiveRecordingSession
import com.example.automation.AutomationActionType
import com.example.automation.AutomationEngine
import com.example.automation.AutomationErrorCode
import com.example.automation.AutomationHudMode
import com.example.automation.AutomationStep
import com.example.automation.AutomationType
import com.example.automation.PageInspectionState
import com.example.automation.RecordedDomEvent
import com.example.automation.RecordingErrorInfo
import com.example.automation.RecordingOriginResult
import com.example.automation.RecordingStatus
import com.example.data.local.ApexDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.BrowserRepository
import com.example.engine.FastDownloadEngine
import com.example.ui.browser.AutomationHudSheet
import com.example.ui.theme.ApexBrowserTheme
import com.example.ui.viewmodel.BrowserViewModel
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase4ARecordingIsolationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class RecordingFakeWebView(
        context: Context,
        var fakeUrl: String? = null,
        private val onEval: ((script: String, callback: ValueCallback<String>?) -> Unit)? = null
    ) : WebView(context) {
        val evaluatedScripts = mutableListOf<String>()
        var destroyedBeforeDetach = false
        var wasDestroyed = false

        override fun getUrl(): String? = fakeUrl

        override fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {
            evaluatedScripts.add(script)
            if (onEval != null) {
                onEval.invoke(script, resultCallback)
                return
            }
            val sidMatch = Regex("""sessionId:\s*("(?:[^"\\]|\\.)*")""").find(script)
            val sid = sidMatch?.groupValues?.getOrNull(1)?.let { JSONObject("{\"v\":$it}").optString("v") } ?: ""
            val payload = if (script.contains("__apexRecorderInstance = inst")) {
                JSONObject().apply {
                    put("ok", true)
                    put("sessionId", sid)
                }.toString()
            } else {
                JSONObject().apply {
                    put("ok", true)
                    put("removed", true)
                }.toString()
            }
            resultCallback?.onReceiveValue(JSONObject.quote(payload))
        }

        override fun destroy() {
            val sawUninstall = evaluatedScripts.any { it.contains("var inst = window.__apexRecorderInstance") }
            if (!sawUninstall && evaluatedScripts.any { it.contains("__apexRecorderInstance = inst") }) {
                destroyedBeforeDetach = true
            }
            wasDestroyed = true
            super.destroy()
        }
    }

    private class TestVmHarness(
        val db: ApexDatabase,
        val repo: BrowserRepository,
        val vm: BrowserViewModel,
        val context: Context,
        private val store: ViewModelStore
    ) {
        fun close() {
            store.clear()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            db.close()
        }
    }

    private fun createTestHarness(
        engine: AutomationEngine = AutomationEngine()
    ): TestVmHarness {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        val prefsRepo = UserPreferencesRepository(context)
        val sharedPrefs = context.getSharedPreferences("phase4a_test_${UUID.randomUUID()}", Context.MODE_PRIVATE)
        val repo = BrowserRepository(db.apexDao(), prefsRepo, sharedPrefs)
        val dl = FastDownloadEngine(context, repo)
        val store = ViewModelStore()
        val vm = ViewModelProvider(
            store,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return BrowserViewModel(
                        repository = repo,
                        fastDownloadEngine = dl,
                        automationEngine = engine
                    ) as T
                }
            }
        )[BrowserViewModel::class.java]
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        return TestVmHarness(db = db, repo = repo, vm = vm, context = context, store = store)
    }

    private fun idleMainUntil(timeoutMs: Long = 3_000L, predicate: () -> Boolean) {
        val mainLooper = Shadows.shadowOf(Looper.getMainLooper())
        val deadline = System.currentTimeMillis() + timeoutMs
        mainLooper.idle()
        while (!predicate() && System.currentTimeMillis() < deadline) {
            mainLooper.idleFor(25L, TimeUnit.MILLISECONDS)
        }
        mainLooper.idle()
        assertTrue("Timed out waiting for condition", predicate())
    }

    private fun List<String>.recorderInstallScripts(): List<String> =
        filter { it.contains("__apexRecorderInstance = inst") }

    private fun List<String>.recorderUninstallScripts(): List<String> =
        filter { it.contains("var inst = window.__apexRecorderInstance") }

    // 1. Origin parsing & normalization
    @Test
    fun parseOrigin_normalizesDefaultPorts_andDistinguishesSchemesPortsSubdomainsAndLookalikes() {
        val engine = AutomationEngine()

        val httpsDefault = engine.parseOrigin("https://Example.COM/login?next=/home")
        val httpsExplicit443 = engine.parseOrigin("https://example.com:443/account")
        assertTrue(httpsDefault is RecordingOriginResult.Valid)
        assertEquals(httpsDefault, httpsExplicit443)
        assertEquals("https://example.com:443", (httpsDefault as RecordingOriginResult.Valid).canonicalOrigin)

        val httpDefault = engine.parseOrigin("http://Example.COM/login")
        val httpExplicit80 = engine.parseOrigin("http://example.com:80/dashboard")
        assertEquals(httpDefault, httpExplicit80)
        assertEquals("http://example.com:80", (httpDefault as RecordingOriginResult.Valid).canonicalOrigin)

        val https8443 = engine.parseOrigin("https://example.com:8443/login")
        val subdomain = engine.parseOrigin("https://sub.example.com/login")
        val lookalike = engine.parseOrigin("https://example.com.evil.test/login")
        val userinfoSpoof = engine.parseOrigin("https://example.com@evil.test/login")

        assertTrue(https8443 is RecordingOriginResult.Valid)
        assertTrue(subdomain is RecordingOriginResult.Valid)
        assertTrue(lookalike is RecordingOriginResult.Valid)
        assertTrue(userinfoSpoof is RecordingOriginResult.Valid)

        val canonicals = setOf(
            httpsDefault.canonicalOrigin,
            (https8443 as RecordingOriginResult.Valid).canonicalOrigin,
            httpDefault.canonicalOrigin,
            (subdomain as RecordingOriginResult.Valid).canonicalOrigin,
            (lookalike as RecordingOriginResult.Valid).canonicalOrigin
        )
        assertEquals(5, canonicals.size)
        assertNotEquals(httpsDefault.canonicalOrigin, (userinfoSpoof as RecordingOriginResult.Valid).canonicalOrigin)

        val opaqueInputs = listOf(
            "",
            "null",
            "about:blank",
            "data:text/html,<h1>Hi</h1>",
            "file:///sdcard/test.html",
            "javascript:alert(1)",
            "blob:https://example.com/abc",
            "apex://home",
            "apex://news",
            "apex://store"
        )
        opaqueInputs.forEach { raw ->
            assertTrue("Expected OpaqueOrUnsupported for $raw", engine.parseOrigin(raw) is RecordingOriginResult.OpaqueOrUnsupported)
        }
    }

    // 2. Session creation, opaque/missing document & install timeout rejection
    @Test
    fun startRecording_bindsSessionTabOrigin_andFailsCleanlyOnOpaqueOrMissingDocumentOrTimeout() {
        val shortEngine = AutomationEngine(jsEvalTimeoutMs = 150L)
        val harness = createTestHarness(shortEngine)
        val vm = harness.vm
        val context = harness.context
        try {
            // 1. Fail on apex://home (opaque internal page)
            vm.startRecording()
            val homeSession = vm.uiState.value.recordingSession
            assertEquals(RecordingStatus.ERROR, homeSession.status)
            assertEquals(AutomationErrorCode.RECORDING_DOCUMENT_UNAVAILABLE, homeSession.recordingError?.code)
            assertTrue(homeSession.sessionId.isEmpty())

            // 2. Fail on HTTP tab without registered WebView
            val tabA = vm.uiState.value.activeTabId
            vm.submitNavigation("https://shop.example.com/checkout", tabA)
            vm.startRecording()
            val noWvSession = vm.uiState.value.recordingSession
            assertEquals(RecordingStatus.ERROR, noWvSession.status)
            assertEquals(AutomationErrorCode.RECORDING_DOCUMENT_UNAVAILABLE, noWvSession.recordingError?.code)

            // 3. Fail cleanly if JS installation fails / mismatches origin
            val failingWv = RecordingFakeWebView(context, "https://shop.example.com/checkout") { _, cb ->
                val failJson = JSONObject().put("ok", false).put("reason", "origin_mismatch").toString()
                cb?.onReceiveValue(JSONObject.quote(failJson))
            }
            vm.registerWebView(tabA, failingWv)
            vm.startRecording()
            idleMainUntil { vm.uiState.value.recordingSession.status == RecordingStatus.ERROR }
            val failedInstall = vm.uiState.value.recordingSession
            assertEquals(AutomationErrorCode.RECORDING_ORIGIN_CHANGED, failedInstall.recordingError?.code)

            // 4. Fail on JS timeout without leaking prior error code or reporting RECORDING prematurely
            val hangingWv = RecordingFakeWebView(context, "https://shop.example.com/checkout") { _, _ ->
                // Intentionally never invokes callback
            }
            vm.registerWebView(tabA, hangingWv)
            vm.startRecording()
            // While awaiting JS install confirmation, must NOT report RECORDING
            assertNotEquals(RecordingStatus.RECORDING, vm.uiState.value.recordingSession.status)
            idleMainUntil { vm.uiState.value.recordingSession.status == RecordingStatus.ERROR }
            assertEquals(AutomationErrorCode.RECORDING_INSTALL_FAILED, vm.uiState.value.recordingSession.recordingError?.code)

            // 5. Succeed cleanly when valid WebView confirms installation
            val goodWv = RecordingFakeWebView(context, "https://shop.example.com/checkout")
            vm.registerWebView(tabA, goodWv)
            vm.startRecording()
            idleMainUntil { vm.uiState.value.recordingSession.status == RecordingStatus.RECORDING }
            val active = vm.uiState.value.recordingSession
            assertTrue(active.sessionId.isNotBlank())
            assertEquals(tabA, active.initiatingTabId)
            assertEquals("https://shop.example.com:443", active.initialOrigin)
            assertEquals("https://shop.example.com:443", active.currentOrigin)
            assertEquals(0L, active.lastSequenceNumber)
            val installScript = goodWv.evaluatedScripts.recorderInstallScripts().last()
            assertTrue(installScript.contains(active.sessionId))
            assertTrue(installScript.contains(tabA))
        } finally {
            harness.close()
        }
    }

    // 3. Tab isolation & WebView instance mismatch protection
    @Test
    fun tabSwitching_andForeignTabOrWebViewEvents_areStrictlyIsolated() {
        val harness = createTestHarness()
        val vm = harness.vm
        val context = harness.context
        try {
            val tabA = vm.uiState.value.activeTabId
            val wvA = RecordingFakeWebView(context, "https://app.example.com/form")
            vm.submitNavigation("https://app.example.com/form", tabA)
            vm.registerWebView(tabA, wvA)

            vm.startRecording()
            idleMainUntil { vm.uiState.value.recordingSession.status == RecordingStatus.RECORDING }
            val session = vm.uiState.value.recordingSession

            // Create Tab B with same origin and switch to it
            vm.createNewTab("https://app.example.com/other")
            val tabB = vm.uiState.value.activeTabId
            assertNotEquals(tabA, tabB)
            val wvB = RecordingFakeWebView(context, "https://app.example.com/other")
            vm.registerWebView(tabB, wvB)

            vm.selectTab(tabB)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertTrue(wvB.evaluatedScripts.recorderInstallScripts().isEmpty())
            assertEquals(RecordingStatus.RECORDING, vm.uiState.value.recordingSession.status)
            assertEquals(tabA, vm.uiState.value.recordingSession.initiatingTabId)

            // Page finish on Tab B must NEVER install the recorder into Tab B
            vm.onWebViewPageFinished(tabB, "Other", "https://app.example.com/other", false, false)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertTrue(wvB.evaluatedScripts.recorderInstallScripts().isEmpty())

            // Event from Tab B (wrong tabId / sourceTabId) rejected
            val resTabMismatch = vm.processRecordedDomEvent(
                RecordedDomEvent(session.sessionId, tabB, "https://app.example.com", 1L, "CLICK", "Save", "#save", "", false),
                tabB, wvB, "https://app.example.com/other"
            )
            assertFalse(resTabMismatch.success)
            assertEquals(AutomationErrorCode.RECORDING_TAB_MISMATCH, resTabMismatch.errorCode)

            // Spoofed event pretending tabId == tabA coming from native WebView wvB rejected
            val resWvMismatch = vm.processRecordedDomEvent(
                RecordedDomEvent(session.sessionId, tabA, "https://app.example.com", 1L, "CLICK", "Save", "#save", "", false),
                tabA, wvB, "https://app.example.com/form"
            )
            assertFalse(resWvMismatch.success)
            assertEquals(AutomationErrorCode.RECORDING_TAB_MISMATCH, resWvMismatch.errorCode)
            assertTrue(vm.uiState.value.recordingSession.steps.isEmpty())

            // Valid event from Tab A still succeeds while user is viewing Tab B
            val resValid = vm.processRecordedDomEvent(
                RecordedDomEvent(session.sessionId, tabA, "https://app.example.com", 1L, "CLICK", "Submit", "#btn", "", false),
                tabA, wvA, "https://app.example.com/form"
            )
            assertTrue(resValid.success)
            assertEquals(1, vm.uiState.value.recordingSession.steps.size)
            assertEquals(1L, vm.uiState.value.recordingSession.lastSequenceNumber)
        } finally {
            harness.close()
        }
    }

    // 4. Same-origin continuation & cross-origin stop preserving captured steps
    @Test
    fun sameOriginNavigationContinues_andCrossOriginOrOpaqueNavigationStopsRecordingAndPreservesSteps() {
        val harness = createTestHarness()
        val vm = harness.vm
        val context = harness.context
        try {
            val tabA = vm.uiState.value.activeTabId
            val wvA = RecordingFakeWebView(context, "https://portal.example.com/step1")
            vm.submitNavigation("https://portal.example.com/step1", tabA)
            vm.registerWebView(tabA, wvA)
            vm.startRecording()
            idleMainUntil { vm.uiState.value.recordingSession.status == RecordingStatus.RECORDING }
            val sid = vm.uiState.value.recordingSession.sessionId

            assertTrue(
                vm.processRecordedDomEvent(
                    RecordedDomEvent(sid, tabA, "https://portal.example.com", 1L, "INPUT", "Name", "#name", "Ada", false),
                    tabA, wvA, "https://portal.example.com/step1"
                ).success
            )

            // Same-origin SPA/document navigation preserves session & reinstalls cleanly with seq=1
            wvA.fakeUrl = "https://portal.example.com/step2"
            vm.onWebViewPageStarted(tabA, "https://portal.example.com/step2")
            vm.onWebViewUrlCommitted(tabA, "https://portal.example.com/step2")
            vm.onWebViewPageFinished(tabA, "Step 2", "https://portal.example.com/step2", true, false)

            assertEquals(RecordingStatus.RECORDING, vm.uiState.value.recordingSession.status)
            assertEquals(sid, vm.uiState.value.recordingSession.sessionId)
            idleMainUntil {
                wvA.evaluatedScripts.recorderInstallScripts().any { it.contains("seq: 1") }
            }

            // Cross-origin navigation immediately stops recording and detaches script
            wvA.fakeUrl = "https://evil.example.org/landing"
            vm.onWebViewUrlCommitted(tabA, "https://evil.example.org/landing")
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            val stopped = vm.uiState.value.recordingSession
            assertEquals(RecordingStatus.STOPPED, stopped.status)
            assertEquals("", stopped.sessionId)
            assertEquals(AutomationErrorCode.RECORDING_ORIGIN_CHANGED, stopped.recordingError?.code)
            assertEquals(1, stopped.steps.size)
            assertTrue(wvA.evaluatedScripts.recorderUninstallScripts().isNotEmpty())

            // Subsequent spoofed/late event on new origin is rejected
            val late = vm.processRecordedDomEvent(
                RecordedDomEvent(sid, tabA, "https://evil.example.org", 2L, "CLICK", "Hack", "#x", "", false),
                tabA, wvA, "https://evil.example.org/landing"
            )
            assertFalse(late.success)
            assertEquals(1, vm.uiState.value.recordingSession.steps.size)
        } finally {
            harness.close()
        }
    }

    // 5. Subdomain, opaque URI, port, scheme changes & document load failure
    @Test
    fun opaqueOrSubdomainOrPortChange_andDocumentFailure_haltRecording() {
        val harness = createTestHarness()
        val vm = harness.vm
        val context = harness.context
        try {
            // Subdomain cross-origin stop
            val tabA = vm.uiState.value.activeTabId
            val wvA = RecordingFakeWebView(context, "https://example.com/home")
            vm.submitNavigation("https://example.com/home", tabA)
            vm.registerWebView(tabA, wvA)
            vm.startRecording()
            vm.onWebViewPageStarted(tabA, "https://sub.example.com/home")
            assertEquals(RecordingStatus.STOPPED, vm.uiState.value.recordingSession.status)
            assertEquals(AutomationErrorCode.RECORDING_ORIGIN_CHANGED, vm.uiState.value.recordingSession.recordingError?.code)
            vm.discardRecording()

            // data: URI navigation stop
            wvA.fakeUrl = "https://example.com/home"
            vm.submitNavigation("https://example.com/home", tabA)
            vm.startRecording()
            vm.onWebViewPageStarted(tabA, "data:text/html,<script></script>")
            assertEquals(RecordingStatus.STOPPED, vm.uiState.value.recordingSession.status)
            assertEquals(AutomationErrorCode.RECORDING_ORIGIN_CHANGED, vm.uiState.value.recordingSession.recordingError?.code)
            vm.discardRecording()

            // Document load failure stops with ERROR + RECORDING_DOCUMENT_UNAVAILABLE
            wvA.fakeUrl = "https://example.com/home"
            vm.submitNavigation("https://example.com/home", tabA)
            vm.startRecording()
            vm.onWebViewPageLoadFailedWithReason(tabA, "https://example.com/home", "ERR_NAME_NOT_RESOLVED")
            val errored = vm.uiState.value.recordingSession
            assertEquals(RecordingStatus.ERROR, errored.status)
            assertEquals("", errored.sessionId)
            assertEquals(AutomationErrorCode.RECORDING_DOCUMENT_UNAVAILABLE, errored.recordingError?.code)
        } finally {
            harness.close()
        }
    }

    // 6. Redirects & HTTPS upgrade cannot bypass origin validation
    @Test
    fun redirectsAndHttpsUpgrade_stopRecordingImmediatelyAndPreventReinstall() {
        val harness = createTestHarness()
        val vm = harness.vm
        val context = harness.context
        try {
            // 1. HTTPS upgrade while recording http://127.0.0.1:8080/form stops recording (http:8080 != https:8080)
            val tabA = vm.uiState.value.activeTabId
            val wvA = RecordingFakeWebView(context, "http://127.0.0.1:8080/form")
            vm.submitNavigation("http://127.0.0.1:8080/form", tabA)
            vm.registerWebView(tabA, wvA)
            vm.startRecording()
            idleMainUntil { vm.uiState.value.recordingSession.status == RecordingStatus.RECORDING }

            vm.onWebViewHttpsUpgraded(tabA, "https://127.0.0.1:8080/form")
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertEquals(RecordingStatus.STOPPED, vm.uiState.value.recordingSession.status)
            assertEquals(AutomationErrorCode.RECORDING_ORIGIN_CHANGED, vm.uiState.value.recordingSession.recordingError?.code)
            assertTrue(wvA.evaluatedScripts.recorderUninstallScripts().isNotEmpty())
            vm.discardRecording()

            // 2. Server redirect a.com -> b.com during navigation stops at onPageStarted and never reinstalls on onPageFinished
            wvA.fakeUrl = "https://a.example.com/start"
            vm.submitNavigation("https://a.example.com/start", tabA)
            vm.startRecording()
            idleMainUntil { vm.uiState.value.recordingSession.status == RecordingStatus.RECORDING }
            val installsBeforeRedirect = wvA.evaluatedScripts.recorderInstallScripts().size

            vm.onWebViewPageStarted(tabA, "https://a.example.com/redirecting")
            assertEquals(RecordingStatus.RECORDING, vm.uiState.value.recordingSession.status)
            wvA.fakeUrl = "https://b.example.com/sso"
            vm.onWebViewPageStarted(tabA, "https://b.example.com/sso")
            assertEquals(RecordingStatus.STOPPED, vm.uiState.value.recordingSession.status)
            assertEquals(AutomationErrorCode.RECORDING_ORIGIN_CHANGED, vm.uiState.value.recordingSession.recordingError?.code)

            vm.onWebViewPageFinished(tabA, "SSO", "https://b.example.com/sso", true, false)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertEquals(installsBeforeRedirect, wvA.evaluatedScripts.recorderInstallScripts().size)
        } finally {
            harness.close()
        }
    }

    // 7. Port/scheme transitions, Home/Back escape, and live WebView mismatch on start
    @Test
    fun portOrSchemeChanges_homeBackEscape_andWebViewMismatchOnStart_enforceOriginIsolation() {
        val harness = createTestHarness()
        val vm = harness.vm
        val context = harness.context
        try {
            val tabA = vm.uiState.value.activeTabId
            // Live WebView URL disagrees with tab URL -> refuse startRecording
            val spoofedWv = RecordingFakeWebView(context, "https://evil.example.org/phish")
            vm.submitNavigation("https://trusted.example.com/app", tabA)
            vm.registerWebView(tabA, spoofedWv)
            vm.startRecording()
            assertEquals(RecordingStatus.ERROR, vm.uiState.value.recordingSession.status)
            assertEquals(AutomationErrorCode.RECORDING_ORIGIN_CHANGED, vm.uiState.value.recordingSession.recordingError?.code)
            assertTrue(spoofedWv.evaluatedScripts.recorderInstallScripts().isEmpty())

            // Non-default port 8443 -> default 443 stops recording
            val wvPort = RecordingFakeWebView(context, "https://trusted.example.com:8443/app")
            vm.submitNavigation("https://trusted.example.com:8443/app", tabA)
            vm.registerWebView(tabA, wvPort)
            vm.startRecording()
            idleMainUntil { vm.uiState.value.recordingSession.status == RecordingStatus.RECORDING }
            vm.onWebViewPageStarted(tabA, "https://trusted.example.com/app")
            assertEquals(RecordingStatus.STOPPED, vm.uiState.value.recordingSession.status)
            assertEquals(AutomationErrorCode.RECORDING_ORIGIN_CHANGED, vm.uiState.value.recordingSession.recordingError?.code)
            vm.discardRecording()

            // Pressing bottom Home stops recording cleanly
            wvPort.fakeUrl = "https://trusted.example.com:8443/app"
            vm.submitNavigation("https://trusted.example.com:8443/app", tabA)
            vm.startRecording()
            idleMainUntil { vm.uiState.value.recordingSession.status == RecordingStatus.RECORDING }
            vm.onBottomHomePressed()
            assertEquals(RecordingStatus.STOPPED, vm.uiState.value.recordingSession.status)
            assertEquals(AutomationErrorCode.RECORDING_ORIGIN_CHANGED, vm.uiState.value.recordingSession.recordingError?.code)
            vm.discardRecording()

            // Navigating back to apex://home fallback stops recording cleanly
            wvPort.fakeUrl = "https://trusted.example.com:8443/app"
            vm.submitNavigation("https://trusted.example.com:8443/app", tabA)
            vm.startRecording()
            idleMainUntil { vm.uiState.value.recordingSession.status == RecordingStatus.RECORDING }
            vm.navigateBack()
            assertEquals(RecordingStatus.STOPPED, vm.uiState.value.recordingSession.status)
            assertEquals(AutomationErrorCode.RECORDING_ORIGIN_CHANGED, vm.uiState.value.recordingSession.recordingError?.code)
        } finally {
            harness.close()
        }
    }

    // 8. Closing initiating tab across closeTab, closeSelectedTabs, closeAllIncognitoTabs, closeGroup, resetAllTabs
    @Test
    fun closingInitiatingTab_cancelsRecordingAndDetachesBeforeDestroy_acrossClosePaths() {
        val harness = createTestHarness()
        val vm = harness.vm
        val context = harness.context
        try {
            // 1. closeTab on initiating tab cancels & detaches before destroy
            val tabA = vm.uiState.value.activeTabId
            val wvA = RecordingFakeWebView(context, "https://bank.example.com")
            vm.submitNavigation("https://bank.example.com", tabA)
            vm.registerWebView(tabA, wvA)
            vm.startRecording()
            idleMainUntil { vm.uiState.value.recordingSession.status == RecordingStatus.RECORDING }

            // Closing a non-initiating tab leaves recording active
            vm.createNewTab("https://news.example.com", stayInSwitcher = false)
            val tabB = vm.uiState.value.activeTabId
            vm.closeTab(tabB)
            assertEquals(RecordingStatus.RECORDING, vm.uiState.value.recordingSession.status)

            // Closing initiating tab cancels recording
            vm.closeTab(tabA)
            val cancelled = vm.uiState.value.recordingSession
            assertEquals(RecordingStatus.CANCELLED, cancelled.status)
            assertEquals("", cancelled.sessionId)
            assertEquals(AutomationErrorCode.RECORDING_TAB_CLOSED, cancelled.recordingError?.code)
            assertFalse(wvA.destroyedBeforeDetach)
            assertTrue(wvA.wasDestroyed)

            // 2. closeSelectedTabs cancels paused recording on initiating tab
            val tabC = vm.uiState.value.activeTabId
            val wvC = RecordingFakeWebView(context, "https://app.example.com")
            vm.submitNavigation("https://app.example.com", tabC)
            vm.registerWebView(tabC, wvC)
            vm.startRecording()
            vm.pauseRecording()
            assertEquals(RecordingStatus.PAUSED, vm.uiState.value.recordingSession.status)
            vm.closeSelectedTabs(setOf(tabC))
            assertEquals(RecordingStatus.CANCELLED, vm.uiState.value.recordingSession.status)
            assertEquals(AutomationErrorCode.RECORDING_TAB_CLOSED, vm.uiState.value.recordingSession.recordingError?.code)
            assertFalse(wvC.destroyedBeforeDetach)

            // 3. closeAllIncognitoTabs cancels recording started inside an incognito tab
            vm.createNewIncognitoTab("https://private.example.com")
            val incTab = vm.uiState.value.activeTabId
            val wvInc = RecordingFakeWebView(context, "https://private.example.com")
            vm.registerWebView(incTab, wvInc)
            vm.startRecording()
            assertEquals(RecordingStatus.RECORDING, vm.uiState.value.recordingSession.status)
            vm.closeAllIncognitoTabs()
            assertEquals(RecordingStatus.CANCELLED, vm.uiState.value.recordingSession.status)
            assertEquals(AutomationErrorCode.RECORDING_TAB_CLOSED, vm.uiState.value.recordingSession.recordingError?.code)
            assertFalse(wvInc.destroyedBeforeDetach)

            // 4. resetAllTabs cancels active recording and detaches before destroy
            val tabD = vm.uiState.value.activeTabId
            val wvD = RecordingFakeWebView(context, "https://reset.example.com")
            vm.submitNavigation("https://reset.example.com", tabD)
            vm.registerWebView(tabD, wvD)
            vm.startRecording()
            assertEquals(RecordingStatus.RECORDING, vm.uiState.value.recordingSession.status)
            vm.resetAllTabs()
            assertEquals(RecordingStatus.CANCELLED, vm.uiState.value.recordingSession.status)
            assertEquals(AutomationErrorCode.RECORDING_TAB_CLOSED, vm.uiState.value.recordingSession.recordingError?.code)
            assertFalse(wvD.destroyedBeforeDetach)
        } finally {
            harness.close()
        }
    }

    // 9. Pause, resume, stop, discard, monotonic sequence replay & stale session checks
    @Test
    fun pauseResumeStopDiscard_sequenceReplay_andStaleSessionValidation() {
        val harness = createTestHarness()
        val vm = harness.vm
        val context = harness.context
        try {
            val tabA = vm.uiState.value.activeTabId
            val wvA = RecordingFakeWebView(context, "https://docs.example.com/edit")
            vm.submitNavigation("https://docs.example.com/edit", tabA)
            vm.registerWebView(tabA, wvA)

            vm.startRecording()
            val sid1 = vm.uiState.value.recordingSession.sessionId
            assertTrue(
                vm.processRecordedDomEvent(
                    RecordedDomEvent(sid1, tabA, "https://docs.example.com", 1L, "CLICK", "Edit", "#edit", "", false),
                    tabA, wvA, "https://docs.example.com/edit"
                ).success
            )

            // Duplicate / replayed sequence 1 rejected
            val replay = vm.processRecordedDomEvent(
                RecordedDomEvent(sid1, tabA, "https://docs.example.com", 1L, "CLICK", "Edit", "#edit", "", false),
                tabA, wvA, "https://docs.example.com/edit"
            )
            assertFalse(replay.success)
            assertEquals(AutomationErrorCode.RECORDING_SESSION_INVALID, replay.errorCode)

            // Stale / wrong sessionId rejected without disturbing active session
            val mismatch = vm.processRecordedDomEvent(
                RecordedDomEvent("old_session_id", tabA, "https://docs.example.com", 2L, "CLICK", "Delete", "#del", "", true),
                tabA, wvA, "https://docs.example.com/edit"
            )
            assertFalse(mismatch.success)
            assertEquals(AutomationErrorCode.RECORDING_SESSION_MISMATCH, mismatch.errorCode)
            assertEquals(RecordingStatus.RECORDING, vm.uiState.value.recordingSession.status)

            // Pause uninstalls & drops events; Resume validates origin and continues sequence
            vm.pauseRecording()
            assertEquals(RecordingStatus.PAUSED, vm.uiState.value.recordingSession.status)
            assertFalse(
                vm.processRecordedDomEvent(
                    RecordedDomEvent(sid1, tabA, "https://docs.example.com", 2L, "CLICK", "WhilePaused", "#p", "", false),
                    tabA, wvA, "https://docs.example.com/edit"
                ).success
            )
            vm.resumeRecording()
            assertEquals(RecordingStatus.RECORDING, vm.uiState.value.recordingSession.status)

            // Pause then cross-origin nav -> resume refuses to reattach
            vm.pauseRecording()
            wvA.fakeUrl = "https://other.example.net"
            vm.submitNavigation("https://other.example.net", tabA)
            assertEquals(RecordingStatus.STOPPED, vm.uiState.value.recordingSession.status)
            vm.resumeRecording()
            assertEquals(RecordingStatus.STOPPED, vm.uiState.value.recordingSession.status)

            // Discard from another tab still detaches initiating tab
            vm.discardRecording()
            assertEquals(RecordingStatus.IDLE, vm.uiState.value.recordingSession.status)
            assertTrue(vm.uiState.value.recordingSession.steps.isEmpty())

            // Stop recording invalidates session and rejects late events
            wvA.fakeUrl = "https://docs.example.com/edit"
            vm.submitNavigation("https://docs.example.com/edit", tabA)
            vm.startRecording()
            val sid2 = vm.uiState.value.recordingSession.sessionId
            assertNotEquals(sid1, sid2)
            vm.stopRecording()
            assertEquals(RecordingStatus.STOPPED, vm.uiState.value.recordingSession.status)
            assertEquals("", vm.uiState.value.recordingSession.sessionId)
            val lateAfterStop = vm.processRecordedDomEvent(
                RecordedDomEvent(sid2, tabA, "https://docs.example.com", 1L, "CLICK", "Late", "#late", "", false),
                tabA, wvA, "https://docs.example.com/edit"
            )
            assertFalse(lateAfterStop.success)
            assertEquals(AutomationErrorCode.RECORDING_SESSION_INVALID, lateAfterStop.errorCode)
        } finally {
            harness.close()
        }
    }

    // 10. Resume checks live WebView origin, mid-resume tab close, and failed-page start guard
    @Test
    fun resumeValidatesLiveWebViewAndMidResumeTabClose_andFailedNavCannotStart() = runBlocking {
        val harness = createTestHarness()
        val vm = harness.vm
        val context = harness.context
        try {
            val tabA = vm.uiState.value.activeTabId
            val wvA = RecordingFakeWebView(context, "https://app.example.com/step1")
            vm.submitNavigation("https://app.example.com/step1", tabA)
            vm.registerWebView(tabA, wvA)

            // Failed navigation synchronizer state blocks startRecording immediately
            vm.navigationSynchronizer.onPageFailed(tabA, "https://app.example.com/step1", "ERR_TIMED_OUT")
            vm.startRecording()
            assertEquals(RecordingStatus.ERROR, vm.uiState.value.recordingSession.status)
            assertEquals(AutomationErrorCode.RECORDING_DOCUMENT_UNAVAILABLE, vm.uiState.value.recordingSession.recordingError?.code)
            vm.discardRecording()

            // Clear navigation failure & start recording
            vm.navigationSynchronizer.beginNavigation(tabA, "https://app.example.com/step1")
            vm.navigationSynchronizer.onPageFinished(tabA, "https://app.example.com/step1")
            vm.startRecording()
            assertEquals(RecordingStatus.RECORDING, vm.uiState.value.recordingSession.status)

            // Pause then WebView's live URL shifts cross-origin without tab URL update -> resume refuses
            vm.pauseRecording()
            wvA.fakeUrl = "https://external.example.org/auth"
            vm.resumeRecording()
            assertEquals(RecordingStatus.STOPPED, vm.uiState.value.recordingSession.status)
            assertEquals(AutomationErrorCode.RECORDING_ORIGIN_CHANGED, vm.uiState.value.recordingSession.recordingError?.code)
            vm.discardRecording()

            // Mid-resume tab close cancels and detaches cleanly
            var delayedCb: ValueCallback<String>? = null
            val sidRegex = Regex("""sessionId:\s*("(?:[^"\\]|\\.)*")""")
            val slowWv = RecordingFakeWebView(context, "https://app.example.com/step1") { script, cb ->
                val scriptSid = sidRegex.find(script)?.groupValues?.getOrNull(1)
                    ?.let { JSONObject("{\"v\":$it}").optString("v") }.orEmpty()
                if (script.contains("__apexRecorderInstance = inst") && delayedCb == null && vm.uiState.value.recordingSession.status == RecordingStatus.PAUSED) {
                    delayedCb = cb
                } else if (script.contains("__apexRecorderInstance = inst")) {
                    cb?.onReceiveValue(JSONObject.quote(JSONObject().put("ok", true).put("sessionId", scriptSid).toString()))
                } else {
                    cb?.onReceiveValue(JSONObject.quote(JSONObject().put("ok", true).put("removed", true).toString()))
                }
            }
            vm.submitNavigation("https://app.example.com/step1", tabA)
            vm.registerWebView(tabA, slowWv)
            vm.startRecording()
            idleMainUntil { vm.uiState.value.recordingSession.status == RecordingStatus.RECORDING }
            val sid = vm.uiState.value.recordingSession.sessionId
            assertTrue(sid.isNotBlank())
            vm.pauseRecording()
            vm.resumeRecording()
            // Close initiating tab while resume JS callback is pending
            vm.closeTab(tabA)
            delayedCb?.onReceiveValue(JSONObject.quote(JSONObject().put("ok", true).put("sessionId", sid).toString()))
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertEquals(RecordingStatus.CANCELLED, vm.uiState.value.recordingSession.status)
            assertEquals(AutomationErrorCode.RECORDING_TAB_CLOSED, vm.uiState.value.recordingSession.recordingError?.code)
        } finally {
            harness.close()
        }
    }

    // 11. Save workflow synchronously invalidates session against late DOM events and persists Room workflow
    @Test
    fun saveRecordingWorkflow_invalidatesSessionSynchronously_andPersistsRecordedWorkflow() = runBlocking {
        val harness = createTestHarness()
        val vm = harness.vm
        val context = harness.context
        try {
            val tabA = vm.uiState.value.activeTabId
            val wvA = RecordingFakeWebView(context, "https://orders.example.com/new")
            vm.submitNavigation("https://orders.example.com/new", tabA)
            vm.registerWebView(tabA, wvA)
            vm.startRecording()
            val sid = vm.uiState.value.recordingSession.sessionId

            assertTrue(
                vm.processRecordedDomEvent(
                    RecordedDomEvent(sid, tabA, "https://orders.example.com", 1L, "INPUT", "Order ID", "#order", "ORD-42", false),
                    tabA, wvA, "https://orders.example.com/new"
                ).success
            )
            assertTrue(
                vm.processRecordedDomEvent(
                    RecordedDomEvent(sid, tabA, "https://orders.example.com", 2L, "CLICK", "Continue", "#continue", "", false),
                    tabA, wvA, "https://orders.example.com/new"
                ).success
            )

            vm.saveRecordingWorkflow("Order Entry")
            // Immediately after saveRecordingWorkflow(), stale events for sid must be rejected
            val lateWhileSaving = vm.processRecordedDomEvent(
                RecordedDomEvent(sid, tabA, "https://orders.example.com", 3L, "CLICK", "Extra", "#extra", "", false),
                tabA, wvA, "https://orders.example.com/new"
            )
            assertFalse(lateWhileSaving.success)
            assertEquals(AutomationErrorCode.RECORDING_SESSION_INVALID, lateWhileSaving.errorCode)

            idleMainUntil { vm.uiState.value.automationHudMode == AutomationHudMode.SAVED }
            val savedWorkflows = harness.repo.automationWorkflowsFlow.first()
            assertEquals(1, savedWorkflows.size)
            assertEquals("Order Entry", savedWorkflows.first().name)
            assertEquals(AutomationType.RECORDED.id, savedWorkflows.first().type)
            assertEquals(2, savedWorkflows.first().stepCount)
        } finally {
            harness.close()
        }
    }

    // 12. Password protection, single-instance script structure, and idempotent cleanup
    @Test
    fun passwordProtection_neverRecordsBulletsOrSecretValues_andScriptIsIdempotentSingleInstance() = runBlocking {
        val engine = AutomationEngine()
        val installJs = engine.buildRecorderInstallScript(
            sessionId = "sess_pw_1",
            tabId = "tab_pw_1",
            expectedOrigin = "https://auth.example.com:443",
            initialSequenceNumber = 0L
        )
        // Must NEVER emit fake bullet mask or capture password text
        assertFalse(installJs.contains("••••••"))
        assertTrue(installJs.contains("type === 'password' ? ''"))
        assertTrue(installJs.contains("window.__apexRecorderInstance"))
        assertTrue(installJs.contains("window.__apexRecorderInstance.cleanup()"))
        assertTrue(installJs.contains("document.removeEventListener('focusin'"))
        assertTrue(installJs.contains("document.removeEventListener('change'"))
        assertTrue(installJs.contains("document.removeEventListener('click'"))
        assertTrue(installJs.contains("onRecordedEventJson"))
        assertFalse(installJs.contains("ApexAutomationBridge.onRecordedEvent("))

        val harness = createTestHarness(engine)
        val vm = harness.vm
        val context = harness.context
        try {
            val tabA = vm.uiState.value.activeTabId
            val wvA = RecordingFakeWebView(context, "https://auth.example.com/signin")
            vm.submitNavigation("https://auth.example.com/signin", tabA)
            vm.registerWebView(tabA, wvA)
            vm.startRecording()
            val sid = vm.uiState.value.recordingSession.sessionId

            val res = vm.processRecordedDomEvent(
                RecordedDomEvent(sid, tabA, "https://auth.example.com", 1L, "INPUT", "Password", "#password", "••••••", false),
                tabA, wvA, "https://auth.example.com/signin"
            )
            assertTrue(res.success)
            val captured = vm.uiState.value.recordingSession.steps.single()
            assertEquals("", captured.value)

            // Idempotent uninstall calls succeed repeatedly even after first cleanup
            val u1 = engine.uninstallRecorderScript(wvA, sid)
            val u2 = engine.uninstallRecorderScript(wvA, sid)
            val u3 = engine.uninstallRecorderScript(wvA, null)
            assertTrue(u1.success)
            assertTrue(u2.success)
            assertTrue(u3.success)
        } finally {
            harness.close()
        }
    }

    // 13. Event bridge validation rejects missing metadata, malformed events & out-of-order sequences
    @Test
    fun bridgeEventValidation_rejectsEmptyOriginOutOfOrderAndUnsupportedActionTypes() {
        val harness = createTestHarness()
        val vm = harness.vm
        val context = harness.context
        try {
            val tabA = vm.uiState.value.activeTabId
            val wvA = RecordingFakeWebView(context, "https://portal.example.com/home")
            vm.submitNavigation("https://portal.example.com/home", tabA)
            vm.registerWebView(tabA, wvA)
            vm.startRecording()
            val sid = vm.uiState.value.recordingSession.sessionId

            // Empty session/tab/origin (legacy unauthenticated bridge call shape) rejected
            val legacyRejected = vm.processRecordedDomEvent(
                RecordedDomEvent("", "", "", 0L, "CLICK", "Btn", "#btn", "", false),
                tabA, wvA, "https://portal.example.com/home"
            )
            assertFalse(legacyRejected.success)
            assertEquals(AutomationErrorCode.RECORDING_SESSION_INVALID, legacyRejected.errorCode)

            // Opaque or mismatched origin inside event rejected
            val badOrigin = vm.processRecordedDomEvent(
                RecordedDomEvent(sid, tabA, "data:text/html,hi", 1L, "CLICK", "Btn", "#btn", "", false),
                tabA, wvA, "https://portal.example.com/home"
            )
            assertFalse(badOrigin.success)
            assertEquals(AutomationErrorCode.RECORDING_ORIGIN_CHANGED, badOrigin.errorCode)

            // Sequence 0 or negative rejected
            val seqZero = vm.processRecordedDomEvent(
                RecordedDomEvent(sid, tabA, "https://portal.example.com", 0L, "CLICK", "Btn", "#btn", "", false),
                tabA, wvA, "https://portal.example.com/home"
            )
            assertFalse(seqZero.success)
            assertEquals(AutomationErrorCode.RECORDING_SESSION_INVALID, seqZero.errorCode)

            // Unsupported eventType rejected
            val badType = vm.processRecordedDomEvent(
                RecordedDomEvent(sid, tabA, "https://portal.example.com", 1L, "EVAL_SCRIPT", "Btn", "#btn", "", false),
                tabA, wvA, "https://portal.example.com/home"
            )
            assertFalse(badType.success)
            assertEquals(AutomationErrorCode.RECORDING_SESSION_INVALID, badType.errorCode)

            // Strictly increasing valid sequence numbers succeed (e.g., 2 then 5, while 4 after 5 is rejected)
            assertTrue(
                vm.processRecordedDomEvent(
                    RecordedDomEvent(sid, tabA, "https://portal.example.com", 2L, "CLICK", "First", "#first", "", false),
                    tabA, wvA, "https://portal.example.com/home"
                ).success
            )
            assertTrue(
                vm.processRecordedDomEvent(
                    RecordedDomEvent(sid, tabA, "https://portal.example.com", 5L, "CLICK", "Second", "#second", "", false),
                    tabA, wvA, "https://portal.example.com/home"
                ).success
            )
            val staleLowerSeq = vm.processRecordedDomEvent(
                RecordedDomEvent(sid, tabA, "https://portal.example.com", 4L, "CLICK", "OutOfOrder", "#ooo", "", false),
                tabA, wvA, "https://portal.example.com/home"
            )
            assertFalse(staleLowerSeq.success)
            assertEquals(AutomationErrorCode.RECORDING_SESSION_INVALID, staleLowerSeq.errorCode)
            assertEquals(2, vm.uiState.value.recordingSession.steps.size)
        } finally {
            harness.close()
        }
    }

    // 14. HUD renders CANCELLED error & other-tab status cleanly
    @Test
    fun recordingHud_rendersCancelledErrorAndOtherTabStatusCleanly() {
        val sessionState = ActiveRecordingSession(
            sessionId = "",
            initiatingTabId = "tab_1",
            status = RecordingStatus.CANCELLED,
            recordingError = RecordingErrorInfo(
                AutomationErrorCode.RECORDING_TAB_CLOSED,
                "Recording cancelled because the recording tab was closed."
            )
        )
        composeTestRule.setContent {
            ApexBrowserTheme(dynamicColor = false) {
                AutomationHudSheet(
                    hudMode = AutomationHudMode.RECORDING,
                    pageInspection = PageInspectionState(domain = "example.com", url = "https://example.com"),
                    recordingSession = sessionState,
                    activeTabId = "tab_2",
                    instructionDraftText = "",
                    instructionDraftSteps = emptyList(),
                    executionState = null,
                    workflows = emptyList(),
                    executions = emptyList(),
                    onSelectMode = {},
                    onScanPage = {},
                    onStartRecording = {},
                    onPauseRecording = {},
                    onResumeRecording = {},
                    onStopRecording = {},
                    onMinimizeRecording = {},
                    onRenameRecordingDraft = {},
                    onEditRecordingStep = { _, _, _ -> },
                    onDeleteRecordingStep = {},
                    onSaveRecording = {},
                    onDiscardRecording = {},
                    onInstructionTextChange = {},
                    onAddInstructions = {},
                    onClearInstructions = {},
                    onReviewInstructionSteps = {},
                    onEditInstructionStep = { _, _, _, _ -> },
                    onDeleteInstructionStep = {},
                    onMoveInstructionStepUp = {},
                    onMoveInstructionStepDown = {},
                    onSaveInstructionsAsWorkflow = {},
                    onExecuteInstructions = {},
                    onRunWorkflow = {},
                    onOpenWorkflowDetails = {},
                    onOpenWorkspace = {},
                    onOpenNanoRetrieverPanel = {},
                    onConfirmDestructiveExecution = {},
                    onCancelDestructiveExecution = {},
                    onClearExecutionBanner = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("automation_hud_expand_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("automation_hud_expand_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("recording_status_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Recording cancelled").assertIsDisplayed()
        composeTestRule.onNodeWithText("Recording cancelled because the recording tab was closed.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("recording_start_button").performScrollTo().assertIsDisplayed()
    }

    // 15. HUD renders active recording controls & other-tab banner when viewing Tab B
    @Test
    fun recordingHud_rendersActiveControlsAndOtherTabIsolationBanner() {
        val activeSession = ActiveRecordingSession(
            sessionId = "rec_sess_live",
            initiatingTabId = "tab_1",
            initialOrigin = "https://shop.example.com:443",
            currentOrigin = "https://shop.example.com:443",
            status = RecordingStatus.RECORDING,
            targetDomain = "shop.example.com",
            targetUrl = "https://shop.example.com/cart",
            steps = listOf(
                AutomationStep(
                    id = "s1",
                    order = 1,
                    actionType = AutomationActionType.CLICK,
                    targetLabel = "Checkout button",
                    selector = "#checkout",
                    value = "",
                    description = "Click Checkout button"
                )
            )
        )
        composeTestRule.setContent {
            ApexBrowserTheme(dynamicColor = false) {
                AutomationHudSheet(
                    hudMode = AutomationHudMode.RECORDING,
                    pageInspection = PageInspectionState(domain = "news.example.org", url = "https://news.example.org"),
                    recordingSession = activeSession,
                    activeTabId = "tab_2",
                    instructionDraftText = "",
                    instructionDraftSteps = emptyList(),
                    executionState = null,
                    workflows = emptyList(),
                    executions = emptyList(),
                    onSelectMode = {},
                    onScanPage = {},
                    onStartRecording = {},
                    onPauseRecording = {},
                    onResumeRecording = {},
                    onStopRecording = {},
                    onMinimizeRecording = {},
                    onRenameRecordingDraft = {},
                    onEditRecordingStep = { _, _, _ -> },
                    onDeleteRecordingStep = {},
                    onSaveRecording = {},
                    onDiscardRecording = {},
                    onInstructionTextChange = {},
                    onAddInstructions = {},
                    onClearInstructions = {},
                    onReviewInstructionSteps = {},
                    onEditInstructionStep = { _, _, _, _ -> },
                    onDeleteInstructionStep = {},
                    onMoveInstructionStepUp = {},
                    onMoveInstructionStepDown = {},
                    onSaveInstructionsAsWorkflow = {},
                    onExecuteInstructions = {},
                    onRunWorkflow = {},
                    onOpenWorkflowDetails = {},
                    onOpenWorkspace = {},
                    onOpenNanoRetrieverPanel = {},
                    onConfirmDestructiveExecution = {},
                    onCancelDestructiveExecution = {},
                    onClearExecutionBanner = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("automation_hud_expand_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("automation_hud_expand_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("recording_status_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Recording continues in shop.example.com tab (1 captured)").assertIsDisplayed()
        composeTestRule.onNodeWithTag("recording_pause_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("recording_stop_button").performScrollTo().assertIsDisplayed()
    }
}
