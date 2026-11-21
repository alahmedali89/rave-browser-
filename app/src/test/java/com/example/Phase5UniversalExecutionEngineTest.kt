package com.example

import android.content.Context
import android.os.Looper
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import com.example.automation.AutomationStepCodec
import com.example.automation.AutomationToolbarStatus
import com.example.automation.AutomationType
import com.example.automation.ControlKind
import com.example.automation.CredentialAuthenticationManager
import com.example.automation.CredentialFieldRole
import com.example.automation.CredentialVaultCrypto
import com.example.automation.CredentialVaultManager
import com.example.automation.DropdownKind
import com.example.automation.ExecutionLifecycleController
import com.example.automation.InMemoryVaultKeyMetadataStore
import com.example.automation.InMemoryVaultKeyProvider
import com.example.automation.LocatorCandidate
import com.example.automation.LocatorStrategy
import com.example.automation.PageInspectionState
import com.example.automation.RecordedDomEvent
import com.example.automation.RecordingOriginResult
import com.example.automation.RecordingStatus
import com.example.automation.TabNavigationSynchronizer
import com.example.automation.UniversalActionScripts
import com.example.automation.UniversalDomScanner
import com.example.automation.WaitConditionSpec
import com.example.automation.WaitConditionType
import com.example.automation.WorkflowExecutionState
import com.example.data.local.ApexDatabase
import com.example.data.local.AutomationWorkflowEntity
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.BrowserRepository
import com.example.engine.FastDownloadEngine
import com.example.ui.browser.AutomationHudSheet
import com.example.ui.theme.ApexBrowserTheme
import com.example.ui.viewmodel.BrowserViewModel
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Phase 5 Verification Suite — Universal Automation Execution Engine.
 * Covers:
 * - Universal action script builders & verification logic (click, input, clear, toggle, select,
 *   custom dropdown, date/time controls, contenteditable, scroll, focus/blur, key press).
 * - UniversalDomScanner locator resolution, candidate ranking, ambiguity rejection,
 *   and bounded stale-element recovery.
 * - Action verification and zero secret leakage in logs/UI.
 * - Deterministic WaitConditions (element appears/disappears/interactable, navigation completed,
 *   URL/origin change, DOM state change, content stabilized, attached waitCondition, and timeouts).
 * - ExecutionLifecycleController & BrowserViewModel pause/resume/cancel/duplicate-run prevention,
 *   tab-close / WebView replacement / background / lock abort handling, and Room execution history.
 * - Recorded event locator enrichment without leaking credential values.
 * - AutomationHudSheet execution status banner controls for RUNNING, PAUSED, COMPLETED, and FAILED.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase5UniversalExecutionEngineTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: ApexDatabase
    private lateinit var repository: BrowserRepository
    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var vaultManager: CredentialVaultManager
    private lateinit var engine: AutomationEngine
    private var activeStore: ViewModelStore? = null

    private class FakeEvalWebView(
        context: Context,
        private val evaluator: (script: String, callback: ValueCallback<String>?) -> Unit
    ) : WebView(context) {
        val evaluatedScripts = mutableListOf<String>()

        override fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {
            evaluatedScripts.add(script)
            evaluator(script, resultCallback)
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferencesRepository = UserPreferencesRepository(context)
        database = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val metadataStore = InMemoryVaultKeyMetadataStore()
        val keyProvider = InMemoryVaultKeyProvider(metadataStore = metadataStore)
        val crypto = CredentialVaultCrypto(keyProvider = keyProvider, metadataStore = metadataStore)
        val authManager = CredentialAuthenticationManager(context = context)
        engine = AutomationEngine(
            jsEvalTimeoutMs = 1_500L,
            navTimeoutMs = 1_500L,
            domReadyTimeoutMs = 400L,
            domStabilityPollMs = 25L
        )
        vaultManager = CredentialVaultManager(
            dao = database.apexDao(),
            crypto = crypto,
            authManager = authManager,
            originParser = { url -> engine.parseOrigin(url) }
        )
        val sharedPrefs = context.getSharedPreferences(
            "phase5_test_${UUID.randomUUID()}",
            Context.MODE_PRIVATE
        )
        repository = BrowserRepository(
            dao = database.apexDao(),
            preferencesRepository = preferencesRepository,
            sharedPrefs = sharedPrefs,
            credentialVaultManager = vaultManager
        )
    }

    @After
    fun tearDown() {
        activeStore?.clear()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        database.close()
    }

    private fun createViewModel(customEngine: AutomationEngine = engine): BrowserViewModel {
        val dl = FastDownloadEngine(context, repository)
        val store = ViewModelStore()
        activeStore = store
        val vm = ViewModelProvider(
            store,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return BrowserViewModel(
                        repository = repository,
                        fastDownloadEngine = dl,
                        automationEngine = customEngine
                    ) as T
                }
            }
        )[BrowserViewModel::class.java]
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        return vm
    }

    private suspend fun idleMainUntil(
        timeoutMs: Long = 4_000L,
        detail: () -> String = { "" },
        predicate: () -> Boolean
    ) {
        val mainLooper = Shadows.shadowOf(Looper.getMainLooper())
        val deadline = System.currentTimeMillis() + timeoutMs
        mainLooper.idle()
        while (!predicate() && System.currentTimeMillis() < deadline) {
            mainLooper.idleFor(20L, TimeUnit.MILLISECONDS)
            delay(20L)
        }
        mainLooper.idle()
        val extra = detail().takeIf { it.isNotBlank() }?.let { " | detail: $it" }.orEmpty()
        assertTrue("Timed out waiting for condition within ${timeoutMs}ms$extra", predicate())
    }

    // =========================================================================
    // 1. UNIVERSAL ACTION SCRIPT BUILDERS & CANDIDATE ORDERING
    // =========================================================================

    @Test
    fun universalActionScripts_ordersCandidatesByUniquenessAndConfidence_andCoversAllActionTypes() {
        val stepInput = AutomationStep(
            id = "s_input",
            order = 1,
            actionType = AutomationActionType.INPUT,
            targetLabel = "Notes",
            selector = "#notes",
            value = "Meeting notes",
            description = "Enter notes",
            clearBeforeInput = true,
            locatorCandidates = listOf(
                LocatorCandidate(LocatorStrategy.SCOPED_CSS, ".note-box", confidence = 0.50f, isUnique = false),
                LocatorCandidate(LocatorStrategy.STABLE_DATA_ATTR, "[data-testid='notes']", confidence = 0.92f, isUnique = true),
                LocatorCandidate(LocatorStrategy.STABLE_NAME, "textarea[name='notes']", confidence = 0.85f, isUnique = true)
            )
        )
        val inputJs = UniversalActionScripts.buildStepExecutionScript(stepInput)
        // Primary selector first, then unique higher-confidence candidate before non-unique candidate
        val idxPrimary = inputJs.indexOf("#notes")
        val idxTestId = inputJs.indexOf("[data-testid='notes']")
        val idxName = inputJs.indexOf("textarea[name='notes']")
        val idxClass = inputJs.indexOf(".note-box")
        assertTrue(idxPrimary in 0 until idxTestId)
        assertTrue(idxTestId in 0 until idxName)
        assertTrue(idxName in 0 until idxClass)
        assertTrue(inputJs.contains("clearBeforeInput") || inputJs.contains("if (true)"))
        assertTrue(inputJs.contains("contenteditable", ignoreCase = true))
        assertTrue(inputJs.contains("datetime-local"))
        assertTrue(inputJs.contains("month"))
        assertTrue(inputJs.contains("week"))
        assertTrue(inputJs.contains("VERIFICATION_FAILED"))

        // Verify CLEAR, TOGGLE, SELECT, FOCUS, BLUR, KEY_PRESS, SCROLL scripts all build cleanly with verification hooks
        val clearJs = UniversalActionScripts.buildStepExecutionScript(
            AutomationStep("s_clr", 2, AutomationActionType.CLEAR, "Bio", "#bio", "", "Clear Bio")
        )
        assertTrue(clearJs.contains("still contains text after clear"))

        val toggleJs = UniversalActionScripts.buildStepExecutionScript(
            AutomationStep("s_tog", 3, AutomationActionType.TOGGLE, "Accept", "#terms", "", "Check terms", desiredCheckedState = true)
        )
        assertTrue(toggleJs.contains("const desiredState = true;"))
        assertTrue(toggleJs.contains("aria-checked"))

        val selectJs = UniversalActionScripts.buildStepExecutionScript(
            AutomationStep("s_sel", 4, AutomationActionType.SELECT, "Country", "#country", "Canada", "Select Canada", dropdownKindHint = DropdownKind.CUSTOM_COMBOBOX)
        )
        assertTrue(selectJs.contains("role === 'combobox'"))
        assertTrue(selectJs.contains("OPTION_NOT_FOUND"))
        assertTrue(selectJs.contains("datetime-local"))

        val focusJs = UniversalActionScripts.buildStepExecutionScript(
            AutomationStep("s_foc", 5, AutomationActionType.FOCUS, "Email", "#email", "", "Focus Email")
        )
        assertTrue(focusJs.contains("doc.activeElement === target"))

        val blurJs = UniversalActionScripts.buildStepExecutionScript(
            AutomationStep("s_blr", 6, AutomationActionType.BLUR, "Email", "#email", "", "Blur Email")
        )
        assertTrue(blurJs.contains("remained focused after blur"))

        val keyJs = UniversalActionScripts.buildStepExecutionScript(
            AutomationStep("s_key", 7, AutomationActionType.KEY_PRESS, "Search", "#search", "", "Press Enter", keyboardKey = "Enter")
        )
        assertTrue(keyJs.contains("KeyboardEvent('keydown'"))
        assertTrue(keyJs.contains("Enter"))

        val scrollJs = UniversalActionScripts.buildStepExecutionScript(
            AutomationStep("s_scr", 8, AutomationActionType.SCROLL, "", "", "bottom", "Scroll bottom")
        )
        assertTrue(scrollJs.contains("window.scrollTo"))
        // Must contain zero external AI endpoint calls
        assertFalse(inputJs.contains("googleapis.com", ignoreCase = true))
        assertFalse(selectJs.contains("generativelanguage", ignoreCase = true))
    }

    // =========================================================================
    // 2. BOUNDED STALE LOCATOR RECOVERY, AMBIGUITY & CHALLENGE GUARDS
    // =========================================================================

    @Test
    fun staleElement_triggersBoundedSingleScannerRecovery_andRetriesUpdatedLocator() = runBlocking {
        val url = "https://shop.example.com/checkout"
        val html = """
            <html><body>
                <button id="updated-checkout-btn" data-testid="complete-purchase">Complete Purchase</button>
            </body></html>
        """.trimIndent()

        var attempts = 0
        val webView = FakeEvalWebView(context) { script, cb ->
            // Ignore wait probes / DOM readiness / UniversalDomScanner spy calls (cb == null)
            if (script.contains("readyState") || cb == null) {
                cb?.onReceiveValue("""{"readyState":"complete","url":"$url","shape":"1:20","targetReady":true}""")
                return@FakeEvalWebView
            }
            attempts++
            if (script.contains("#old-checkout-btn")) {
                cb.onReceiveValue("""{"ok":false,"code":"ELEMENT_STALE","msg":"Element detached from DOM"}""")
            } else if (script.contains("#updated-checkout-btn") || script.contains("complete-purchase")) {
                cb.onReceiveValue("""{"ok":true,"code":"NONE","verified":true,"resolvedSelector":"#updated-checkout-btn","recoveredViaFallback":true,"msg":"Activated Complete Purchase"}""")
            } else {
                cb.onReceiveValue("""{"ok":false,"code":"ELEMENT_NOT_FOUND","msg":"Not found"}""")
            }
        }
        UniversalDomScanner.attachHtmlFixture(webView, url, html)

        try {
            val wf = AutomationWorkflowEntity(
                id = 11L,
                name = "Checkout",
                domain = "shop.example.com",
                sourceUrl = url,
                type = AutomationType.RECORDED.id,
                stepsJson = "[]",
                stepCount = 1,
                createdAt = 1L,
                updatedAt = 1L
            )
            val staleStep = AutomationStep(
                id = "st1",
                order = 1,
                actionType = AutomationActionType.CLICK,
                targetLabel = "Complete Purchase",
                selector = "#old-checkout-btn",
                description = "Click Complete Purchase"
            )

            val outcome = engine.executeWorkflow(
                workflow = wf,
                steps = listOf(staleStep),
                currentTabUrl = url,
                webViewProvider = { webView },
                onNavigateUrl = {},
                onStepProgress = { _, _ -> },
                tabId = "tab_rec"
            )

            assertEquals("COMPLETED", outcome.status)
            assertEquals(1, outcome.completedSteps)
            assertEquals(1, outcome.recoveredStepsCount)
            assertEquals(2, attempts) // Initial stale attempt + 1 recovered retry
            assertTrue(outcome.stepRecords.single().recoveredViaFallback)
            assertEquals("#updated-checkout-btn", outcome.stepRecords.single().resolvedSelector)
            assertTrue(outcome.verificationSummary.contains("1 locator recovered"))
        } finally {
            UniversalDomScanner.clearAttachedFixture(webView)
        }
    }

    @Test
    fun ambiguousLocator_andSecurityChallenge_failClosedWithoutBypass() = runBlocking {
        val url = "https://auth.example.com/login"
        val ambiguousHtml = """
            <html><body>
                <button class="dup-btn">Continue</button>
                <button class="dup-btn">Continue</button>
            </body></html>
        """.trimIndent()
        val wv = FakeEvalWebView(context) { script, cb ->
            if (script.contains("#missing-continue")) {
                cb?.onReceiveValue("""{"ok":false,"code":"ELEMENT_NOT_FOUND","msg":"Missing #missing-continue"}""")
            } else {
                cb?.onReceiveValue("""{"ok":false,"code":"ELEMENT_AMBIGUOUS","msg":"Multiple elements matched"}""")
            }
        }
        UniversalDomScanner.attachHtmlFixture(wv, url, ambiguousHtml)

        val wf = AutomationWorkflowEntity(
            id = 12L, name = "Ambiguous Test", domain = "auth.example.com",
            sourceUrl = url, type = AutomationType.MANUAL.id, stepsJson = "[]",
            stepCount = 2, createdAt = 1L, updatedAt = 1L
        )
        var secondStepExecuted = false
        val wvTracking = FakeEvalWebView(context) { script, cb ->
            if (script.contains("readyState")) {
                cb?.onReceiveValue("""{"readyState":"complete","url":"$url","shape":"2:16","targetReady":true}""")
            } else if (script.contains("#step2-btn")) {
                secondStepExecuted = true
                cb?.onReceiveValue("""{"ok":true,"code":"NONE","msg":"Step 2 clicked"}""")
            } else {
                cb?.onReceiveValue("""{"ok":false,"code":"ELEMENT_NOT_FOUND","msg":"Missing #missing-continue"}""")
            }
        }
        UniversalDomScanner.attachHtmlFixture(wvTracking, url, ambiguousHtml)

        try {
            val outcome = engine.executeWorkflow(
                workflow = wf,
                steps = listOf(
                    AutomationStep("s1", 1, AutomationActionType.CLICK, "Continue", "#missing-continue", "", "Click Continue"),
                    AutomationStep("s2", 2, AutomationActionType.CLICK, "Submit", "#step2-btn", "", "Click Submit")
                ),
                currentTabUrl = url,
                webViewProvider = { wvTracking },
                onNavigateUrl = {},
                onStepProgress = { _, _ -> },
                tabId = "tab_amb"
            )
            assertEquals("FAILED", outcome.status)
            assertEquals(AutomationErrorCode.ELEMENT_AMBIGUOUS, outcome.errorCode)
            assertEquals(0, outcome.completedSteps)
            assertFalse("Required failure must stop workflow before step 2", secondStepExecuted)

            // Security challenge guard
            val captchaWv = FakeEvalWebView(context) { _, cb ->
                cb?.onReceiveValue("""{"ok":false,"code":"SECURITY_CHALLENGE_BLOCKED","msg":"Active CAPTCHA requires manual completion."}""")
            }
            val captchaOutcome = engine.executeWorkflow(
                workflow = wf.copy(stepCount = 1),
                steps = listOf(AutomationStep("c1", 1, AutomationActionType.CLICK, "Verify", "#verify", "", "Click Verify")),
                currentTabUrl = url,
                webViewProvider = { captchaWv },
                onNavigateUrl = {},
                onStepProgress = { _, _ -> }
            )
            assertEquals("FAILED", captchaOutcome.status)
            assertEquals(AutomationErrorCode.SECURITY_CHALLENGE_BLOCKED, captchaOutcome.errorCode)
        } finally {
            UniversalDomScanner.clearAttachedFixture(wv)
            UniversalDomScanner.clearAttachedFixture(wvTracking)
        }
    }

    // =========================================================================
    // 3. ACTION VERIFICATION, STEP ORDERING, OPTIONAL VS REQUIRED, & LOG REDACTION
    // =========================================================================

    @Test
    fun workflowExecution_enforcesStepOrder_verificationChecks_optionalSkipping_andSensitiveRedaction() = runBlocking {
        val executedOrder = mutableListOf<String>()
        val secretPin = "987654-TOPSECRET"

        val wv = FakeEvalWebView(context) { script, cb ->
            if (script.contains("readyState")) {
                cb?.onReceiveValue("""{"readyState":"complete","url":"https://bank.example.com","shape":"4:40","targetReady":true}""")
                return@FakeEvalWebView
            }
            when {
                script.contains("#first-field") -> {
                    executedOrder.add("first")
                    cb?.onReceiveValue("""{"ok":true,"code":"NONE","verified":true,"msg":"Entered 'hello'"}""")
                }
                script.contains("#optional-banner") -> {
                    executedOrder.add("optional")
                    cb?.onReceiveValue("""{"ok":false,"code":"ELEMENT_NOT_FOUND","msg":"Optional banner absent"}""")
                }
                script.contains("#pin-code") -> {
                    executedOrder.add("pin")
                    // Intentionally echo plaintext secret from JS to confirm Kotlin sanitizer strips it completely
                    cb?.onReceiveValue("""{"ok":true,"code":"NONE","verified":true,"msg":"Entered \"$secretPin\" into PIN"}""")
                }
                script.contains("#bad-date") -> {
                    executedOrder.add("bad_date")
                    cb?.onReceiveValue("""{"ok":false,"code":"VERIFICATION_FAILED","msg":"Invalid date value format"}""")
                }
                else -> cb?.onReceiveValue("""{"ok":true,"code":"NONE","msg":"OK"}""")
            }
        }

        val wf = AutomationWorkflowEntity(
            id = 20L, name = "Bank Verify", domain = "bank.example.com",
            sourceUrl = "https://bank.example.com", type = AutomationType.MANUAL.id,
            stepsJson = "[]", stepCount = 3, createdAt = 1L, updatedAt = 1L
        )
        // Intentionally supply steps out of order: order=3, order=1, order=2
        val stepsOutOfOrder = listOf(
            AutomationStep("p3", 3, AutomationActionType.INPUT, "PIN Code", "#pin-code", secretPin, "Enter PIN"),
            AutomationStep("p1", 1, AutomationActionType.INPUT, "Note", "#first-field", "hello", "Enter Note"),
            AutomationStep("p2", 2, AutomationActionType.CLICK, "Dismiss", "#optional-banner", "", "Dismiss promo", isOptional = true)
        )

        val logs = mutableListOf<String>()
        val outcome = engine.executeWorkflow(
            workflow = wf,
            steps = stepsOutOfOrder,
            currentTabUrl = "https://bank.example.com",
            webViewProvider = { wv },
            onNavigateUrl = {},
            onStepProgress = { _, msg -> logs.add(msg) }
        )

        assertEquals("COMPLETED", outcome.status)
        assertEquals(listOf("first", "optional", "pin"), executedOrder)
        assertEquals(3, outcome.completedSteps)
        assertEquals("SKIPPED_OPTIONAL", outcome.stepRecords[1].status)
        // Zero plaintext secret in logs, records, or verification summary
        assertFalse(outcome.verificationSummary.contains(secretPin))
        assertTrue(logs.none { it.contains(secretPin) })
        assertTrue(outcome.stepRecords.none { it.safeMessage.contains(secretPin) })
        assertTrue(outcome.stepRecords[2].safeMessage.contains("••••"))

        // Verification failure on required step must fail workflow (no silent skip)
        val failOutcome = engine.executeWorkflow(
            workflow = wf,
            steps = listOf(
                AutomationStep("bd", 1, AutomationActionType.INPUT, "Birth Date", "#bad-date", "not-a-date", "Set Birth Date", stepTimeoutMs = 900L),
                AutomationStep("after", 2, AutomationActionType.CLICK, "Save", "#save", "", "Save")
            ),
            currentTabUrl = "https://bank.example.com",
            webViewProvider = { wv },
            onNavigateUrl = {},
            onStepProgress = { _, _ -> }
        )
        assertEquals("FAILED", failOutcome.status)
        assertEquals(AutomationErrorCode.VERIFICATION_FAILED, failOutcome.errorCode)
        assertEquals(0, failOutcome.completedSteps)
    }

    // =========================================================================
    // 4. DETERMINISTIC WAIT CONDITIONS & TIMEOUTS
    // =========================================================================

    @Test
    fun waitConditions_handleAppearanceDisappearanceUrlOriginDomStateStabilizationAndTimeout() = runBlocking {
        val sync = TabNavigationSynchronizer()
        val appeared = AtomicBoolean(false)
        val disappeared = AtomicBoolean(false)
        val interactable = AtomicBoolean(false)
        val currentProbeUrl = arrayOf("https://app.example.com/start")
        val domChanged = AtomicBoolean(false)

        val wv = FakeEvalWebView(context) { script, cb ->
            when {
                script.contains("#modal-ok") -> {
                    cb?.onReceiveValue("""{"satisfied":${appeared.get()},"url":"${currentProbeUrl[0]}","signature":"c:1:10"}""")
                }
                script.contains(".spinner") -> {
                    cb?.onReceiveValue("""{"satisfied":${disappeared.get()},"url":"${currentProbeUrl[0]}","signature":"c:1:10"}""")
                }
                script.contains("#ready-btn") -> {
                    cb?.onReceiveValue("""{"satisfied":${interactable.get()},"url":"${currentProbeUrl[0]}","signature":"c:1:10"}""")
                }
                script.contains("#status-badge") -> {
                    cb?.onReceiveValue("""{"satisfied":${domChanged.get()},"url":"${currentProbeUrl[0]}","signature":"c:2:20"}""")
                }
                script.contains("CONTENT_STABILIZED") -> {
                    cb?.onReceiveValue("""{"satisfied":true,"readyState":"complete","url":"${currentProbeUrl[0]}","signature":"complete:5:250"}""")
                }
                script.contains("NAVIGATION_COMPLETED") || script.contains("URL_CHANGE") || script.contains("ORIGIN_CHANGE") -> {
                    cb?.onReceiveValue("""{"satisfied":true,"readyState":"complete","url":"${currentProbeUrl[0]}","signature":"complete:5:250"}""")
                }
                else -> {
                    cb?.onReceiveValue("""{"ok":true,"code":"NONE","readyState":"complete","targetReady":true,"msg":"Clicked"}""")
                }
            }
        }

        // 4a. ELEMENT_APPEARS
        val appearJob = async {
            engine.awaitWaitCondition(
                condition = WaitConditionSpec(WaitConditionType.ELEMENT_APPEARS, selector = "#modal-ok", timeoutMs = 800L),
                tabId = "t1", baselineUrl = "https://app.example.com/start",
                baselineOrigin = "https://app.example.com:443", navigationSynchronizer = sync, webViewProvider = { wv }
            )
        }
        delay(60L)
        assertFalse(appearJob.isCompleted)
        appeared.set(true)
        assertTrue(appearJob.await().success)

        // 4b. ELEMENT_DISAPPEARS
        val disJob = async {
            engine.awaitWaitCondition(
                condition = WaitConditionSpec(WaitConditionType.ELEMENT_DISAPPEARS, selector = ".spinner", timeoutMs = 800L),
                tabId = "t1", baselineUrl = "https://app.example.com/start",
                baselineOrigin = "https://app.example.com:443", navigationSynchronizer = sync, webViewProvider = { wv }
            )
        }
        delay(60L)
        disappeared.set(true)
        assertTrue(disJob.await().success)

        // 4c. ELEMENT_INTERACTABLE
        interactable.set(true)
        val intRes = engine.awaitWaitCondition(
            condition = WaitConditionSpec(WaitConditionType.ELEMENT_INTERACTABLE, selector = "#ready-btn", timeoutMs = 500L),
            tabId = "t1", baselineUrl = "https://app.example.com/start",
            baselineOrigin = "https://app.example.com:443", navigationSynchronizer = sync, webViewProvider = { wv }
        )
        assertTrue(intRes.success)

        // 4d. URL_CHANGE & ORIGIN_CHANGE & NAVIGATION_COMPLETED
        currentProbeUrl[0] = "https://app.example.com/dashboard?ok=1"
        val urlRes = engine.awaitWaitCondition(
            condition = WaitConditionSpec(WaitConditionType.URL_CHANGE, expectedUrlContains = "dashboard", timeoutMs = 500L),
            tabId = "t1", baselineUrl = "https://app.example.com/start",
            baselineOrigin = "https://app.example.com:443", navigationSynchronizer = sync, webViewProvider = { wv }
        )
        assertTrue(urlRes.success)

        currentProbeUrl[0] = "https://pay.partner.test/confirm"
        val origRes = engine.awaitWaitCondition(
            condition = WaitConditionSpec(WaitConditionType.ORIGIN_CHANGE, expectedOrigin = "https://pay.partner.test", timeoutMs = 500L),
            tabId = "t1", baselineUrl = "https://app.example.com/dashboard",
            baselineOrigin = "https://app.example.com:443", navigationSynchronizer = sync, webViewProvider = { wv }
        )
        assertTrue(origRes.success)

        sync.onPageFinished("t1", "https://pay.partner.test/confirm")
        val navRes = engine.awaitWaitCondition(
            condition = WaitConditionSpec(WaitConditionType.NAVIGATION_COMPLETED, timeoutMs = 500L),
            tabId = "t1", baselineUrl = "https://pay.partner.test/confirm",
            baselineOrigin = "https://pay.partner.test:443", navigationSynchronizer = sync, webViewProvider = { wv }
        )
        assertTrue(navRes.success)

        // 4e. DOM_STATE_CHANGE + CONTENT_STABILIZED
        domChanged.set(true)
        assertTrue(
            engine.awaitWaitCondition(
                condition = WaitConditionSpec(WaitConditionType.DOM_STATE_CHANGE, selector = "#status-badge", expectedText = "Saved", timeoutMs = 500L),
                tabId = "t1", baselineUrl = "https://pay.partner.test/confirm",
                baselineOrigin = "https://pay.partner.test:443", navigationSynchronizer = sync, webViewProvider = { wv }
            ).success
        )
        assertTrue(
            engine.awaitWaitCondition(
                condition = WaitConditionSpec(WaitConditionType.CONTENT_STABILIZED, timeoutMs = 600L, stabilizeQuietMs = 60L),
                tabId = "t1", baselineUrl = "https://pay.partner.test/confirm",
                baselineOrigin = "https://pay.partner.test:443", navigationSynchronizer = sync, webViewProvider = { wv }
            ).success
        )

        // 4f. Attached WaitCondition on a CLICK step + WAIT_TIMEOUT verification
        domChanged.set(false)
        val timeoutRes = engine.awaitWaitCondition(
            condition = WaitConditionSpec(WaitConditionType.DOM_STATE_CHANGE, selector = "#status-badge", expectedText = "Never", timeoutMs = 120L),
            tabId = "t1", baselineUrl = "https://pay.partner.test/confirm",
            baselineOrigin = "https://pay.partner.test:443", navigationSynchronizer = sync, webViewProvider = { wv }
        )
        assertFalse(timeoutRes.success)
        assertEquals(AutomationErrorCode.WAIT_TIMEOUT, timeoutRes.errorCode)
    }

    // =========================================================================
    // 5. EXECUTION LIFECYCLE, PAUSE/RESUME, DUPLICATE GUARD & VIEWMODEL HISTORY
    // =========================================================================

    @Test
    fun executionLifecycleController_enforcesSingleSessionPauseResumeCancelAndContextAborts() = runBlocking {
        val controller = ExecutionLifecycleController()
        val wv1 = WebView(context)
        val wv2 = WebView(context)

        val s1 = controller.tryBeginSession("tab_1", wv1, "https://example.com:443")
        assertNotNull(s1)
        // Concurrent second session rejected
        assertNull(controller.tryBeginSession("tab_1", wv1, "https://example.com:443"))

        assertTrue(controller.requestPause())
        assertTrue(controller.isPaused)

        val waiter = async { controller.awaitRunnableOrAbort() }
        delay(40L)
        assertFalse("Waiter must remain suspended while paused", waiter.isCompleted)

        assertTrue(controller.requestResume())
        assertFalse(controller.isPaused)
        assertNull(waiter.await())

        // Context aborts: WebView replaced, tab closed, unexpected origin change
        assertEquals(
            AutomationErrorCode.WEBVIEW_REPLACED,
            controller.validateBoundContext(wv2, true, "https://example.com/a", "https://example.com:443", false) {
                engine.parseOrigin(it)
            }?.code
        )
        assertEquals(
            AutomationErrorCode.TAB_CLOSED,
            controller.validateBoundContext(wv1, false, "https://example.com/a", "https://example.com:443", false) {
                engine.parseOrigin(it)
            }?.code
        )
        assertEquals(
            AutomationErrorCode.UNEXPECTED_ORIGIN_CHANGE,
            controller.validateBoundContext(wv1, true, "https://evil.test/phish", "https://example.com:443", false) {
                engine.parseOrigin(it)
            }?.code
        )

        // Cancel wakes paused waiter cleanly
        assertTrue(controller.requestPause())
        val cancelledWaiter = async {
            try {
                controller.awaitRunnableOrAbort()
                null
            } catch (ce: CancellationException) {
                ce
            }
        }
        delay(30L)
        controller.requestCancel()
        // Note: requestCancel sets abortReason AND signal CANCELLED; awaitRunnableOrAbort returns abortReason or throws CancellationException
        val cancelOutcome = cancelledWaiter.await()
        assertTrue(cancelOutcome is CancellationException || controller.isCancelled)

        controller.endSession(s1!!)
        // New session allowed cleanly after endSession
        val s2 = controller.tryBeginSession("tab_1", wv1, "https://example.com:443")
        assertNotNull(s2)
        controller.endSession(s2!!)
    }

    @Test
    fun browserViewModel_pauseResumeDuplicatePreventTabCloseAndRecorderEnrichmentWorkEndToEnd() = runBlocking {
        val vm = createViewModel()
        val tabId = vm.uiState.value.activeTabId
        val startUrl = "https://portal.example.com/form"
        vm.onWebViewPageFinished(
            tabId = tabId,
            title = "Portal Form",
            url = startUrl,
            canGoBack = false,
            canGoForward = false
        )
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val step2Gate = AtomicBoolean(false)
        val step1Ran = AtomicBoolean(false)
        val evalCalls = AtomicInteger(0)

        val wv = FakeEvalWebView(context) { script, cb ->
            if (script.contains("__apexRecorderInstance = inst")) {
                val sidMatch = Regex("""sessionId:\s*("(?:[^"\\]|\\.)*")""").find(script)
                val sid = sidMatch?.groupValues?.getOrNull(1)?.let { JSONObject("{\"v\":$it}").optString("v") } ?: ""
                val payload = JSONObject().put("ok", true).put("sessionId", sid).toString()
                cb?.onReceiveValue(JSONObject.quote(payload))
                return@FakeEvalWebView
            }
            if (script.contains("var inst = window.__apexRecorderInstance")) {
                val payload = JSONObject().put("ok", true).put("removed", true).toString()
                cb?.onReceiveValue(JSONObject.quote(payload))
                return@FakeEvalWebView
            }
            if (cb == null) {
                return@FakeEvalWebView
            }
            evalCalls.incrementAndGet()
            when {
                script.contains("const condType =") && script.contains("#step2-wait") -> {
                    cb.onReceiveValue("""{"satisfied":${step2Gate.get()},"url":"$startUrl","signature":"s:1"}""")
                }
                script.contains("#step1-input") -> {
                    step1Ran.set(true)
                    cb.onReceiveValue("""{"ok":true,"code":"NONE","verified":true,"msg":"Entered Alice"}""")
                }
                script.contains("readyState") -> {
                    cb.onReceiveValue("""{"readyState":"complete","url":"$startUrl","shape":"3:30","targetReady":true}""")
                }
                else -> cb.onReceiveValue("""{"ok":true,"code":"NONE","verified":true,"msg":"Clicked"}""")
            }
        }
        vm.registerWebView(tabId, wv)

        // Record a DOM event with a matching PageInspection element -> verify Phase 5 locator enrichment
        UniversalDomScanner.attachHtmlFixture(
            wv,
            startUrl,
            """
                <html><body>
                    <label for="rec-name">Full Name</label>
                    <input id="rec-name" data-testid="full-name-input" type="text" />
                    <label for="rec-pass">Password</label>
                    <input id="rec-pass" type="password" />
                </body></html>
            """.trimIndent()
        )
        vm.scanActivePageElements()
        idleMainUntil { vm.uiState.value.pageInspection.elements.isNotEmpty() }

        vm.startRecording()
        idleMainUntil { vm.uiState.value.recordingSession.status == RecordingStatus.RECORDING }
        val recSessionId = vm.uiState.value.recordingSession.sessionId
        val fullNameEl = vm.uiState.value.pageInspection.elements.first { it.htmlId == "rec-name" }
        assertTrue(
            vm.processRecordedDomEvent(
                event = RecordedDomEvent(
                    sessionId = recSessionId,
                    tabId = tabId,
                    origin = "https://portal.example.com:443",
                    sequenceNumber = 1L,
                    eventType = "INPUT",
                    targetLabel = "Full Name",
                    selector = fullNameEl.selector,
                    value = "Alice",
                    isDestructive = false
                ),
                sourceTabId = tabId,
                sourceWebView = wv,
                sourceWebViewUrl = startUrl
            ).success
        )
        val recPassEl = vm.uiState.value.pageInspection.elements.first { it.htmlId == "rec-pass" }
        assertTrue(
            vm.processRecordedDomEvent(
                event = RecordedDomEvent(
                    sessionId = recSessionId,
                    tabId = tabId,
                    origin = "https://portal.example.com:443",
                    sequenceNumber = 2L,
                    eventType = "INPUT",
                    targetLabel = "Password",
                    selector = recPassEl.selector,
                    value = "SuperSecret123",
                    isDestructive = false,
                    isPasswordField = true
                ),
                sourceTabId = tabId,
                sourceWebView = wv,
                sourceWebViewUrl = startUrl
            ).success
        )
        val recordedSteps = vm.uiState.value.recordingSession.steps
        assertEquals(2, recordedSteps.size)
        assertTrue("Recorded step must copy locatorCandidates from scanner", recordedSteps[0].locatorCandidates.isNotEmpty())
        assertEquals(ControlKind.STANDARD_INPUT, recordedSteps[0].controlKindHint)
        // Phase 4A/4B credential isolation preserved
        assertEquals("", recordedSteps[1].value)
        assertTrue(recordedSteps[1].isCredentialStep)
        assertEquals(CredentialFieldRole.PASSWORD, recordedSteps[1].credentialFieldRole)
        vm.discardRecording()
        idleMainUntil { vm.uiState.value.recordingSession.status == RecordingStatus.IDLE }

        // Create workflow and test Pause -> Duplicate-run guard -> Resume -> Completed -> Room history
        val wfId = repository.saveAutomationWorkflow(
            name = "Pause Resume Flow",
            domain = "portal.example.com",
            sourceUrl = startUrl,
            type = AutomationType.MANUAL,
            steps = listOf(
                AutomationStep("s1", 1, AutomationActionType.INPUT, "Name", "#step1-input", "Alice", "Enter Name"),
                AutomationStep(
                    "s2", 2, AutomationActionType.WAIT, "Badge", "#step2-wait", "", "Wait for badge",
                    waitCondition = WaitConditionSpec(WaitConditionType.ELEMENT_APPEARS, selector = "#step2-wait", timeoutMs = 4_000L)
                )
            )
        )

        vm.runWorkflow(wfId)
        idleMainUntil { step1Ran.get() && vm.uiState.value.executionState?.status == "RUNNING" }

        vm.pauseWorkflowExecution()
        idleMainUntil { vm.uiState.value.executionState?.status == "PAUSED" }
        assertEquals(AutomationToolbarStatus.PAUSED, vm.uiState.value.automationToolbarStatus)

        // Attempt duplicate run while PAUSED -> must be ignored
        val execsBeforeDup = database.apexDao().observeAutomationExecutions().first().size
        vm.runWorkflow(wfId)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals("PAUSED", vm.uiState.value.executionState?.status)
        assertEquals(execsBeforeDup, database.apexDao().observeAutomationExecutions().first().size)

        // Confirm zero polling churn while paused
        val evalsWhilePaused = evalCalls.get()
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(100L, TimeUnit.MILLISECONDS)
        assertEquals(evalsWhilePaused, evalCalls.get())

        // Resume & let wait condition finish
        step2Gate.set(true)
        vm.resumeWorkflowExecution()
        idleMainUntil(
            detail = {
                val ex = vm.uiState.value.executionState
                "status=${ex?.status} err=${ex?.errorCode} msg=${ex?.verificationMessage} step=${ex?.currentStepIndex}/${ex?.totalSteps} done=${ex?.completedSteps} evals=${evalCalls.get()}"
            }
        ) { vm.uiState.value.executionState?.status == "COMPLETED" }
        assertEquals(2, vm.uiState.value.executionState?.completedSteps)

        val history = database.apexDao().getAutomationExecutionsForWorkflowOnce(wfId)
        assertEquals(1, history.size)
        assertEquals("COMPLETED", history.first().status)
        assertEquals(2, history.first().completedSteps)

        // Now test WebView replacement mid-run aborts safely as FAILED(WEBVIEW_REPLACED)
        step2Gate.set(false)
        step1Ran.set(false)
        vm.runWorkflow(wfId)
        idleMainUntil { step1Ran.get() && vm.uiState.value.executionState?.status == "RUNNING" }

        val replacementWv = FakeEvalWebView(context) { _, cb ->
            cb?.onReceiveValue("""{"ok":false}""")
        }
        vm.registerWebView(tabId, replacementWv)
        idleMainUntil { vm.uiState.value.executionState?.status == "FAILED" }
        assertEquals(AutomationErrorCode.WEBVIEW_REPLACED, vm.uiState.value.executionState?.errorCode)

        UniversalDomScanner.clearAttachedFixture(wv)
    }

    // =========================================================================
    // 6. HUD EXECUTION BANNER CONTROLS (RUNNING, PAUSED, COMPLETED, FAILED)
    // =========================================================================

    @Test
    fun automationHud_rendersPauseResumeCancelAndRecoveryStates() {
        var execState by mutableStateOf<WorkflowExecutionState?>(
            WorkflowExecutionState(
                workflowId = 1L,
                workflowName = "Checkout",
                status = "RUNNING",
                currentStepIndex = 0,
                totalSteps = 3,
                currentStepDescription = "Fill Shipping Address",
                currentActionLabel = "Input",
                completedSteps = 0,
                verificationMessage = "Executing on shop.example.com…",
                recoveryNotice = "Recovered 'Shipping' via updated page locator"
            )
        )
        var pauseClicks = 0
        var resumeClicks = 0
        var cancelClicks = 0
        var dismissClicks = 0

        composeTestRule.setContent {
            ApexBrowserTheme {
                AutomationHudSheet(
                    hudMode = AutomationHudMode.OVERVIEW,
                    pageInspection = PageInspectionState(domain = "shop.example.com", url = "https://shop.example.com"),
                    recordingSession = ActiveRecordingSession(),
                    instructionDraftText = "",
                    instructionDraftSteps = emptyList(),
                    executionState = execState,
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
                    onClearExecutionBanner = { dismissClicks++ },
                    onDismiss = {},
                    onCancelExecution = { cancelClicks++ },
                    onPauseExecution = { pauseClicks++ },
                    onResumeExecution = { resumeClicks++ }
                )
            }
        }

        // RUNNING shows progress, step info, recovery notice, Pause and Cancel
        composeTestRule.onNodeWithTag("automation_execution_progress").assertIsDisplayed()
        composeTestRule.onNodeWithText("Step 1/3: [Input] Fill Shipping Address", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Recovered 'Shipping' via updated page locator", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("automation_pause_execution_button").assertIsDisplayed().performClick()
        assertEquals(1, pauseClicks)

        // PAUSED shows Resume and Cancel
        execState = execState!!.copy(
            status = "PAUSED",
            verificationMessage = "Paused at step 1/3"
        )
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("automation_resume_execution_button").assertIsDisplayed().performClick()
        assertEquals(1, resumeClicks)
        composeTestRule.onNodeWithTag("automation_cancel_execution_button").assertIsDisplayed().performClick()
        assertEquals(1, cancelClicks)

        // FAILED shows safe message & Dismiss
        execState = execState!!.copy(
            status = "FAILED",
            verificationMessage = "Target 'Pay Now' is disabled.",
            recoveryNotice = ""
        )
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Target 'Pay Now' is disabled.", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("automation_dismiss_execution_button").assertIsDisplayed().performClick()
        assertEquals(1, dismissClicks)
    }

    // =========================================================================
    // 7. BIRTH-YEAR SELECTION ACROSS NATIVE SELECT, CUSTOM DROPDOWN, CALENDAR
    //    DIALOG, SEPARATE FIELDS & DYNAMIC / SCROLLABLE YEAR OPTIONS
    // =========================================================================

    @Test
    fun birthYearExecutionScripts_supportNativeSelectCustomDropdownCalendarSeparateFieldAndScrollableDynamicOptions() = runBlocking {
        // 1. Verify generated JS for SELECT and INPUT includes native select range diagnostics,
        //    custom picker readonly trigger handling, scrollable year discovery, dynamic retry,
        //    and strict post-selection verification.
        val selectStep = AutomationStep(
            id = "by-select",
            order = 1,
            actionType = AutomationActionType.SELECT,
            targetLabel = "Birth year",
            selector = "#birth-year-trigger",
            value = "1998",
            description = "Select 1998 in Birth year"
        )
        val selectJs = UniversalActionScripts.buildStepExecutionScript(selectStep)
        assertTrue(selectJs.contains("apexSelectNativeOption"))
        assertTrue(selectJs.contains("apexSelectCustomPickerOption"))
        assertTrue(selectJs.contains("apexIsCustomPickerOrDropdown"))
        assertTrue(selectJs.contains("apexIsBirthYearCandidateEl"))
        assertTrue(selectJs.contains("apexIsUnrelatedNumericEl"))
        assertTrue(selectJs.contains("data-apex-dyn-retries"))
        assertTrue(selectJs.contains("pendingDynamic: true"))
        assertTrue(selectJs.contains("new Event('scroll', { bubbles: true })"))
        assertTrue(selectJs.contains("but the selection was not applied to the control"))

        // 2. Verify AutomationEngine polls when pendingDynamic=true is returned for dynamically rendered year options
        //    and succeeds once the async year option renders and is verified.
        val evalAttempts = AtomicInteger(0)
        val dynamicWv = FakeEvalWebView(context) { script, cb ->
            if (cb == null) return@FakeEvalWebView
            when {
                script.contains("readyState") -> {
                    cb.onReceiveValue("""{"readyState":"complete","url":"https://signup.example.com/dob","shape":"4:40","targetReady":true}""")
                }
                script.contains("#dynamic-year-dropdown") -> {
                    val attempt = evalAttempts.incrementAndGet()
                    if (attempt < 3) {
                        // First 2 evaluations: dropdown just opened, options still rendering asynchronously
                        cb.onReceiveValue(
                            """{"ok":false,"code":"OPTION_NOT_FOUND","pendingDynamic":true,"msg":"Waiting for dynamic options to render for \"1998\" in \"Birth year\"."}"""
                        )
                    } else {
                        // 3rd evaluation: async option 1998 rendered, clicked, and verified on control
                        cb.onReceiveValue(
                            """{"ok":true,"code":"NONE","selectedValue":"1998","selectedText":"1998","verified":true,"resolvedSelector":"#dynamic-year-dropdown","msg":"Selected \"1998\" in \"Birth year\"."}"""
                        )
                    }
                }
                else -> cb.onReceiveValue("""{"ok":true,"code":"NONE","verified":true}""")
            }
        }

        val wf = AutomationWorkflowEntity(
            id = 77L,
            name = "Dynamic Birth Year Flow",
            domain = "signup.example.com",
            sourceUrl = "https://signup.example.com/dob",
            type = AutomationType.RECORDED.id,
            stepsJson = "[]",
            stepCount = 1,
            createdAt = 1L,
            updatedAt = 1L
        )
        val dynamicStep = AutomationStep(
            id = "dyn-year-step",
            order = 1,
            actionType = AutomationActionType.SELECT,
            targetLabel = "Birth year",
            selector = "#dynamic-year-dropdown",
            value = "1998",
            description = "Select 1998 in dynamically loaded Birth year dropdown"
        )
        val dynResult = engine.executeWorkflow(
            workflow = wf,
            steps = listOf(dynamicStep),
            currentTabUrl = "https://signup.example.com/dob",
            webViewProvider = { dynamicWv },
            onNavigateUrl = {},
            onStepProgress = { _, _ -> },
            tabId = "tab_dyn_year"
        )
        assertEquals("COMPLETED", dynResult.status)
        assertEquals(3, evalAttempts.get())

        // 3. Verify that if a year option click does not actually apply the selection, execution fails with VERIFICATION_FAILED
        val unverifiedWv = FakeEvalWebView(context) { script, cb ->
            if (cb == null) return@FakeEvalWebView
            when {
                script.contains("readyState") -> {
                    cb.onReceiveValue("""{"readyState":"complete","url":"https://signup.example.com/dob","shape":"4:40","targetReady":true}""")
                }
                else -> {
                    cb.onReceiveValue(
                        """{"ok":false,"code":"VERIFICATION_FAILED","verified":false,"msg":"Clicked option \"1998\" in \"Birth year\", but the selection was not applied to the control."}"""
                    )
                }
            }
        }
        val unverifiedResult = engine.executeWorkflow(
            workflow = wf.copy(id = 78L, name = "Unverified Birth Year Click"),
            steps = listOf(selectStep),
            currentTabUrl = "https://signup.example.com/dob",
            webViewProvider = { unverifiedWv },
            onNavigateUrl = {},
            onStepProgress = { _, _ -> },
            tabId = "tab_unverified_year"
        )
        assertEquals("FAILED", unverifiedResult.status)
        assertEquals(AutomationErrorCode.VERIFICATION_FAILED, unverifiedResult.errorCode)
        assertTrue(unverifiedResult.verificationSummary.contains("not applied"))
    }

    @Test
    fun clickAndSelectReplayScripts_calibrateBottomSheetCalendarMonthNavAndTimeSlots() {
        val clickStep = AutomationStep(
            id = "cal-day-click",
            order = 1,
            actionType = AutomationActionType.CLICK,
            targetLabel = "June 15, 2026",
            selector = "[data-date=\"2026-06-15\"]",
            value = "",
            description = "Click June 15, 2026"
        )
        val clickJs = UniversalActionScripts.buildStepExecutionScript(clickStep)
        assertTrue(clickJs.contains("apexIsSheetOrDialogNode"))
        assertTrue(clickJs.contains("[role=\\\"gridcell\\\"]"))
        assertTrue(clickJs.contains("[data-date]"))
        assertTrue(clickJs.contains("[data-time]"))
        assertTrue(clickJs.contains("[data-slot]"))
        assertTrue(clickJs.contains(".sheet-item"))
        assertTrue(clickJs.contains(".flatpickr-day"))

        val selectCalStep = AutomationStep(
            id = "cal-select",
            order = 2,
            actionType = AutomationActionType.SELECT,
            targetLabel = "Appointment Date",
            selector = "#appointment-date",
            value = "2026-06-15",
            description = "Select 2026-06-15 in Appointment Date"
        )
        val selectCalJs = UniversalActionScripts.buildStepExecutionScript(selectCalStep)
        assertTrue(selectCalJs.contains("isOverflowOrDisabledDay"))
        assertTrue(selectCalJs.contains(".bottom-sheet"))
        assertTrue(selectCalJs.contains(".flatpickr-calendar"))
        assertTrue(selectCalJs.contains("prev-month"))
        assertTrue(selectCalJs.contains("next-month"))
        assertTrue(selectCalJs.contains("wantedIsoDate"))
    }
}

