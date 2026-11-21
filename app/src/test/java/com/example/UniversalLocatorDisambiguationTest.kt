package com.example

import android.content.Context
import android.os.Looper
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.automation.AmbiguousCandidateInfo
import com.example.automation.AutomationActionType
import com.example.automation.AutomationEngine
import com.example.automation.AutomationErrorCode
import com.example.automation.AutomationStep
import com.example.automation.AutomationStepCodec
import com.example.automation.AutomationType
import com.example.automation.CredentialAuthenticationManager
import com.example.automation.CredentialVaultCrypto
import com.example.automation.CredentialVaultManager
import com.example.automation.DisambiguationSource
import com.example.automation.InMemoryVaultKeyMetadataStore
import com.example.automation.InMemoryVaultKeyProvider
import com.example.automation.LocatorAttemptDiagnostic
import com.example.automation.LocatorResolutionDiagnostics
import com.example.automation.LocatorStrategy
import com.example.automation.PendingDisambiguationState
import com.example.automation.RecordedDomEvent
import com.example.automation.RecordingStatus
import com.example.automation.UniversalActionScripts
import com.example.automation.UniversalDomScanner
import com.example.automation.WorkflowExecutionState
import com.example.data.local.ApexDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.BrowserRepository
import com.example.engine.FastDownloadEngine
import com.example.ui.browser.AutomationExecutionStatusBanner
import com.example.ui.browser.LocatorDisambiguationDialog
import com.example.ui.theme.ApexBrowserTheme
import com.example.ui.viewmodel.BrowserViewModel
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
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
 * Verification suite for Universal Locator Resolution and Element Disambiguation:
 * - Reproduces the account.fydeos.io "signup" multi-match scenario.
 * - Verifies semantic locator generation, unstable ID rejection, uniqueness validation, and priority ordering.
 * - Verifies runtime JS script structure (ancestor visibility, Shadow/iframe chain hints, structured ambiguity payloads).
 * - Verifies AutomationEngine structured ambiguity propagation & BrowserViewModel refinement + re-run flow.
 * - Verifies Compose LocatorDisambiguationDialog and AutomationExecutionStatusBanner UI & diagnostics accordion.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UniversalLocatorDisambiguationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: ApexDatabase
    private lateinit var repository: BrowserRepository
    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var vaultManager: CredentialVaultManager
    private lateinit var engine: AutomationEngine
    private var activeStore: androidx.lifecycle.ViewModelStore? = null

    private class FakeEvalWebView(
        context: Context,
        private val evaluator: (script: String, callback: ValueCallback<String>?) -> Unit
    ) : WebView(context) {
        override fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {
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
            "disambig_test_${UUID.randomUUID()}",
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

    private fun createViewModel(): BrowserViewModel {
        val dl = FastDownloadEngine(context, repository)
        val store = androidx.lifecycle.ViewModelStore()
        activeStore = store
        val vm = androidx.lifecycle.ViewModelProvider(
            store,
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return BrowserViewModel(
                        repository = repository,
                        fastDownloadEngine = dl,
                        automationEngine = engine
                    ) as T
                }
            }
        )[BrowserViewModel::class.java]
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        return vm
    }

    private suspend fun idleMainUntil(
        timeoutMs: Long = 4_000L,
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
        assertTrue("Timed out waiting for condition within ${timeoutMs}ms", predicate())
    }

    // =========================================================================
    // 1. Account FydeOS "signup" Ambiguity Reproduction & Resolution
    // =========================================================================

    @Test
    fun fydeosSignupReproduction_detectsMultipleVisibleMatches_neverAutoPicksFirst_andRefinesUniquely() = runBlocking {
        val fydeosHtml = """
            <html>
            <body>
                <header id="top-nav">
                    <a href="/signup?src=header">Sign Up</a>
                </header>
                <main>
                    <section id="hero">
                        <h1>Create your account</h1>
                        <a href="/signup?src=hero" data-testid="hero-signup-btn">Sign Up</a>
                    </section>
                    <section id="plans">
                        <button type="button" class="plan-cta">Sign Up</button>
                    </section>
                </main>
                <aside id="mobile-drawer" hidden>
                    <a href="/signup?src=drawer">Sign Up</a>
                </aside>
                <div style="display:none">
                    <button type="button">Sign Up</button>
                </div>
                <footer id="site-footer">
                    <nav aria-label="Account links">
                        <a href="/signup?src=footer">Sign Up</a>
                    </nav>
                </footer>
            </body>
            </html>
        """.trimIndent()

        val wv = WebView(context)
        try {
            UniversalDomScanner.attachHtmlFixture(wv, "https://account.fydeos.io/login", fydeosHtml)
            val result = engine.scanPageElements(
                webView = wv,
                currentUrl = "https://account.fydeos.io/login",
                tabId = "tab_fydeos"
            )
            assertTrue(result.elements.isNotEmpty())

            // Hidden elements (aside[hidden] and div[style='display:none']) must be marked invisible
            val visibleSignups = result.elements.filter {
                it.visible && (it.visibleText.equals("Sign Up", ignoreCase = true) ||
                    it.label.contains("Sign Up", ignoreCase = true))
            }
            assertEquals(4, visibleSignups.size)
            val hiddenSignups = result.elements.filter {
                !it.visible && it.visibleText.equals("Sign Up", ignoreCase = true)
            }
            assertEquals(2, hiddenSignups.size)

            // Hero element should have unique semantic candidates such as [data-testid='hero-signup-btn'] and href
            val heroSignup = visibleSignups.first { it.relevantAttributes["data-testid"] == "hero-signup-btn" }
            assertFalse(heroSignup.isLocatorAmbiguous)
            assertEquals(LocatorStrategy.STABLE_DATA_ATTR, heroSignup.locatorStrategy)
            assertTrue(heroSignup.locatorCandidates.any { it.strategy == LocatorStrategy.HREF_ATTR && it.selector.contains("/signup?src=hero") })

            // Instruction match for bare "click signup" on identical-text visible links flags ambiguity without auto-picking index 0
            val genericLinksHtml = """
                <html><body>
                    <header><a href="/signup?loc=nav">signup</a></header>
                    <main><a href="/signup?loc=hero">signup</a></main>
                    <footer><a href="/signup?loc=footer">signup</a></footer>
                </body></html>
            """.trimIndent()
            UniversalDomScanner.attachHtmlFixture(wv, "https://account.fydeos.io/", genericLinksHtml)
            val bareScan = engine.scanPageElements(
                webView = wv,
                currentUrl = "https://account.fydeos.io/",
                tabId = "tab_bare"
            )
            val candidates = bareScan.elements.filter { it.href.isNotBlank() }
            assertEquals(3, candidates.size)
            candidates.forEach { el ->
                // Each element has a unique href locator candidate for disambiguation
                assertTrue(el.locatorCandidates.any { it.strategy == LocatorStrategy.HREF_ATTR && it.isUnique })
            }

            // Bare identical buttons inside bare <body> still surface ambiguity
            UniversalDomScanner.attachHtmlFixture(
                wv,
                "https://account.fydeos.io/dup",
                "<html><body><button>signup</button><button>signup</button></body></html>"
            )
            val bareDupButtons = engine.scanPageElements(
                webView = wv,
                currentUrl = "https://account.fydeos.io/dup",
                tabId = "tab_dup"
            )
            val match = engine.universalScanner.matchCandidatesForInstruction(
                clause = "click signup",
                actionType = AutomationActionType.CLICK,
                targetLabel = "signup",
                inspectionState = bareDupButtons
            )
            assertTrue(match.isAmbiguous)
            assertTrue(match.matchedElements.size >= 2)
        } finally {
            UniversalDomScanner.clearAttachedFixture(wv)
        }
    }

    // =========================================================================
    // 2. Unstable ID Rejection, Semantic Priority & Redaction
    // =========================================================================

    @Test
    fun unstableRandomIds_areRejectedInFavorOfSemanticLocators_andSensitiveValuesRedacted() = runBlocking {
        val html = """
            <html><body>
                <form id="login-form">
                    <label for="mui-12345">Email Address</label>
                    <input id="mui-12345" name="email" type="email" placeholder="you@fydeos.io" data-testid="login-email" />
                    <label for=":r1a:">Secret Password</label>
                    <input id=":r1a:" name="password" type="password" value="SuperSecretPassword123!" data-testid="login-password" />
                    <button id="css-1ab2cd3" type="submit" aria-label="Continue to Account">Continue</button>
                </form>
            </body></html>
        """.trimIndent()

        val wv = WebView(context)
        try {
            UniversalDomScanner.attachHtmlFixture(wv, "https://account.fydeos.io/auth", html)
            val scan = engine.scanPageElements(
                webView = wv,
                currentUrl = "https://account.fydeos.io/auth",
                tabId = "tab_sec"
            )
            val emailEl = scan.elements.first { it.name == "email" }
            // mui-12345 must not be chosen as STABLE_ID; data-testid wins
            assertEquals(LocatorStrategy.STABLE_DATA_ATTR, emailEl.locatorStrategy)
            assertFalse(emailEl.locatorCandidates.any { it.strategy == LocatorStrategy.STABLE_ID && it.selector == "#mui-12345" })
            assertTrue(emailEl.locatorCandidates.any { it.strategy == LocatorStrategy.PLACEHOLDER_ATTR && it.selector.contains("you@fydeos.io") })

            val continueBtn = scan.elements.first { it.accessibleName == "Continue to Account" }
            assertFalse(continueBtn.locatorCandidates.any { it.strategy == LocatorStrategy.STABLE_ID && it.selector == "#css-1ab2cd3" })
            assertTrue(continueBtn.locatorCandidates.any { it.strategy == LocatorStrategy.ARIA_ROLE_AND_NAME })

            // Password value must never leak into relevantAttributes or serialized element fields
            val passEl = scan.elements.first { it.isPasswordInput }
            assertFalse(passEl.toString().contains("SuperSecretPassword123!"))
            assertFalse(passEl.relevantAttributes.values.any { it.contains("SuperSecretPassword123!") })
        } finally {
            UniversalDomScanner.clearAttachedFixture(wv)
        }
    }

    // =========================================================================
    // 3. Runtime Action Script Resolution Priority, Ancestor Visibility & Recorder
    // =========================================================================

    @Test
    fun runtimeScripts_includeAncestorVisibility_semanticFallback_diagnostics_andInvariantSafeIndexing() {
        val clickStep = AutomationStep(
            id = "step_signup",
            order = 1,
            actionType = AutomationActionType.CLICK,
            targetLabel = "signup",
            selector = "signup",
            description = "Click signup",
            framePath = listOf("iframe#auth-frame"),
            shadowHostPath = listOf("fyde-auth-card")
        )
        val js = UniversalActionScripts.buildStepExecutionScript(clickStep)

        assertTrue(js.contains("apexIsVisible"))
        assertTrue(js.contains("visibility === 'hidden'"))
        assertTrue(js.contains("parseFloat(style.opacity) === 0"))
        assertTrue(js.contains("[inert]"))
        assertTrue(js.contains("aria-hidden"))
        assertTrue(js.contains("apexIsStableId"))
        assertTrue(js.contains("apexCollectCandidateInfo"))
        assertTrue(js.contains("ambiguousCandidates"))
        assertTrue(js.contains("diagnostics"))
        assertTrue(js.contains("frameChainHint"))
        assertTrue(js.contains("shadowChainHint"))
        assertTrue(js.contains("ELEMENT_AMBIGUOUS"))
        assertTrue(js.contains("apexNormCompact"))

        // Verify Phase 5 / Stabilization invariant: never select candidates[0] without candidates.length === 1
        var searchFrom = 0
        var checked = 0
        while (true) {
            val idx = js.indexOf("candidates[0]", searchFrom)
            if (idx < 0) break
            val before = js.substring((idx - 80).coerceAtLeast(0), idx)
            assertTrue("candidates[0] at $idx must be guarded by candidates.length === 1", before.contains("candidates.length === 1"))
            checked++
            searchFrom = idx + "candidates[0]".length
        }
        assertTrue(checked >= 1)

        val recorderJs = engine.buildRecorderInstallScript("sess_1", "tab_1", "https://account.fydeos.io:443", 0L)
        assertTrue(recorderJs.contains("composedPath"))
        assertTrue(recorderJs.contains("isStableId"))
        assertTrue(recorderJs.contains("shadowHostPath"))
        assertTrue(recorderJs.contains("framePath"))
        assertTrue(recorderJs.contains("locatorCandidates"))
        assertTrue(recorderJs.contains("ambiguousCandidates"))
        assertTrue(recorderJs.contains("matchCount"))
        assertTrue(recorderJs.contains("parentContext"))
        assertTrue(recorderJs.contains("nearbyText"))
        assertTrue(recorderJs.contains("isPwd ? '' : (value || '')"))
    }

    // =========================================================================
    // 4. Engine & ViewModel Ambiguity Propagation -> Selection -> Re-run Flow
    // =========================================================================

    @Test
    fun executionAmbiguity_surfacesStructuredCandidatesAndDiagnostics_andRefinesStepOnSelection() = runBlocking {
        val targetUrl = "https://account.fydeos.io/"
        val step = AutomationStep(
            id = "s_signup",
            order = 1,
            actionType = AutomationActionType.CLICK,
            targetLabel = "signup",
            selector = "signup",
            description = "Click signup"
        )
        val diagJson = JSONObject()
            .put("recordedLocator", "signup")
            .put("totalMatches", 5)
            .put("visibleMatches", 4)
            .put("interactableMatches", 4)
            .put("finalOutcome", "ELEMENT_AMBIGUOUS")
            .put(
                "attemptedCandidates",
                JSONArray().put(
                    JSONObject()
                        .put("strategy", "SCOPED_TEXT")
                        .put("selector", "signup")
                        .put("matchCount", 5)
                        .put("visibleCount", 4)
                        .put("interactableCount", 4)
                        .put("selected", false)
                        .put("rejectedReason", "Ambiguous: matched 4 visible elements")
                )
            )
        val candArray = JSONArray()
            .put(
                JSONObject()
                    .put("index", 0)
                    .put("tagName", "a")
                    .put("accessibleName", "Sign Up")
                    .put("visibleText", "Sign Up")
                    .put("role", "link")
                    .put("parentContext", "header#top-nav")
                    .put("nearbyText", "Log In")
                    .put("isVisible", true)
                    .put("isEnabled", true)
                    .put("recommendedSelector", "#top-nav a[href='/signup?src=header']")
                    .put("attributes", JSONObject().put("href", "/signup?src=header"))
            )
            .put(
                JSONObject()
                    .put("index", 1)
                    .put("tagName", "a")
                    .put("accessibleName", "Sign Up")
                    .put("visibleText", "Sign Up")
                    .put("role", "link")
                    .put("parentContext", "section#hero")
                    .put("nearbyText", "Create your account")
                    .put("isVisible", true)
                    .put("isEnabled", true)
                    .put("recommendedSelector", "[data-testid='hero-signup-btn']")
                    .put("attributes", JSONObject().put("href", "/signup?src=hero").put("data-testid", "hero-signup-btn"))
            )

        val wv = FakeEvalWebView(context) { script, cb ->
            if (cb == null) return@FakeEvalWebView
            when {
                script.contains("readyState") -> {
                    cb.onReceiveValue("""{"readyState":"complete","url":"$targetUrl","shape":"5:100","targetReady":true}""")
                }
                script.contains("[data-testid='hero-signup-btn']") && !script.contains("const initialSelector = \"signup\"") -> {
                    cb.onReceiveValue("""{"ok":true,"code":"NONE","verified":true,"msg":"Clicked [data-testid='hero-signup-btn']"}""")
                }
                else -> {
                    val resp = JSONObject()
                        .put("ok", false)
                        .put("code", "ELEMENT_AMBIGUOUS")
                        .put("verified", false)
                        .put("msg", "Ambiguous locator matched 4 visible elements (5 total) for 'signup'.")
                        .put("ambiguousCandidates", candArray)
                        .put("diagnostics", diagJson)
                    cb.onReceiveValue(resp.toString())
                }
            }
        }

        // Direct AutomationEngine execution returns structured ambiguous candidates + diagnostics
        val firstOutcome = engine.executeSingleStepWithRecovery(
            step = step,
            activeUrl = targetUrl,
            tabId = "t_fyde",
            webViewProvider = { wv },
            onNavigateUrl = {}
        )
        assertFalse(firstOutcome.success)
        assertEquals(AutomationErrorCode.ELEMENT_AMBIGUOUS, firstOutcome.errorCode)
        assertEquals(2, firstOutcome.ambiguousCandidates.size)
        assertEquals("[data-testid='hero-signup-btn']", firstOutcome.ambiguousCandidates[1].recommendedSelector)
        assertNotNull(firstOutcome.locatorDiagnostics)
        assertEquals(5, firstOutcome.locatorDiagnostics?.totalMatches)
        assertEquals(4, firstOutcome.locatorDiagnostics?.visibleMatches)

        // Full BrowserViewModel flow: run fails with pendingDisambiguation -> resolveAmbiguousLocator updates DB & re-runs -> SUCCEEDS
        val vm = createViewModel()
        val tabId = vm.uiState.value.activeTab.id
        vm.onWebViewPageFinished(
            tabId = tabId,
            title = "FydeOS Account",
            url = targetUrl,
            canGoBack = false,
            canGoForward = false
        )
        vm.registerWebView(tabId, wv)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val wfId = repository.saveAutomationWorkflow(
            name = "account.fydeos.io Task",
            domain = "account.fydeos.io",
            sourceUrl = targetUrl,
            type = AutomationType.MANUAL,
            steps = listOf(step),
            instructionsText = "click signup"
        )
        vm.runWorkflow(wfId, confirmDestructive = true)
        idleMainUntil { vm.uiState.value.pendingDisambiguation != null }

        val pending = vm.uiState.value.pendingDisambiguation!!
        assertEquals(DisambiguationSource.EXECUTION, pending.source)
        assertEquals(2, pending.candidates.size)
        assertNotNull(vm.uiState.value.executionState)
        assertEquals(AutomationErrorCode.ELEMENT_AMBIGUOUS, vm.uiState.value.executionState?.errorCode)

        // Select the hero candidate & re-run
        vm.resolveAmbiguousLocator(pending.candidates[1], rerunAfterSelection = true)
        idleMainUntil { vm.uiState.value.executionState?.status == "COMPLETED" }

        assertNull(vm.uiState.value.pendingDisambiguation)
        val savedWf = repository.getAutomationWorkflowById(wfId)!!
        val updatedSteps = AutomationStepCodec.decode(savedWf.stepsJson)
        assertEquals("[data-testid='hero-signup-btn']", updatedSteps.first().selector)
        assertEquals(LocatorStrategy.STABLE_DATA_ATTR, updatedSteps.first().locatorCandidates.first().strategy)
    }

    @Test
    fun recordingAmbiguity_triggersDisambiguationAndRefinesRecordedStep() = runBlocking {
        val vm = createViewModel()
        val tabId = vm.uiState.value.activeTab.id
        val url = "https://account.fydeos.io/"
        vm.onWebViewPageFinished(tabId, "FydeOS", url, false, false)
        val wv = FakeEvalWebView(context) { script, cb ->
            if (script.contains("__apexRecorderInstance = inst")) {
                val sidMatch = Regex("""sessionId:\s*("(?:[^"\\]|\\.)*")""").find(script)
                val sid = sidMatch?.groupValues?.getOrNull(1)?.let { JSONObject("{\"v\":$it}").optString("v") } ?: ""
                cb?.onReceiveValue(JSONObject.quote(JSONObject().put("ok", true).put("sessionId", sid).toString()))
            } else {
                cb?.onReceiveValue(JSONObject.quote("""{"ok":true,"removed":true}"""))
            }
        }
        vm.registerWebView(tabId, wv)
        vm.startRecording()
        idleMainUntil { vm.uiState.value.recordingSession.status == RecordingStatus.RECORDING }
        val sid = vm.uiState.value.recordingSession.sessionId

        val recordedEvent = RecordedDomEvent(
            sessionId = sid,
            tabId = tabId,
            origin = "https://account.fydeos.io:443",
            sequenceNumber = 1L,
            eventType = "CLICK",
            targetLabel = "Sign Up",
            selector = "a",
            value = "",
            isDestructive = false,
            parentContext = "section#hero",
            nearbyText = "Start Free",
            matchCount = 3,
            ambiguousCandidates = listOf(
                AmbiguousCandidateInfo(
                    candidateIndex = 0,
                    tagName = "a",
                    accessibleName = "Sign Up",
                    parentContext = "header#nav",
                    recommendedSelector = "#nav a[href='/signup']"
                ),
                AmbiguousCandidateInfo(
                    candidateIndex = 1,
                    tagName = "a",
                    accessibleName = "Sign Up",
                    parentContext = "section#hero",
                    recommendedSelector = "#hero a[href='/signup?src=hero']"
                )
            )
        )

        vm.onRecordedDomEvent(recordedEvent, tabId, wv, url)
        idleMainUntil { vm.uiState.value.pendingDisambiguation != null }

        val pending = vm.uiState.value.pendingDisambiguation!!
        assertEquals(DisambiguationSource.RECORDING, pending.source)
        vm.resolveAmbiguousLocator(pending.candidates[1], rerunAfterSelection = false)

        val step = vm.uiState.value.recordingSession.steps.single()
        assertEquals("#hero a[href='/signup?src=hero']", step.selector)
        assertNull(vm.uiState.value.pendingDisambiguation)
    }

    // =========================================================================
    // 5. Compose UI Tests: Disambiguation Dialog & Banner Controls
    // =========================================================================

    @Test
    fun locatorDisambiguationDialog_rendersCandidates_togglesDiagnostics_andInvokesCallbacks() {
        val candidates = listOf(
            AmbiguousCandidateInfo(
                candidateIndex = 0,
                tagName = "a",
                accessibleName = "Sign Up",
                visibleText = "Sign Up",
                role = "link",
                attributes = mapOf("href" to "/signup?src=header"),
                parentContext = "header#top-nav",
                nearbyText = "Log In",
                recommendedSelector = "#top-nav a[href='/signup?src=header']"
            ),
            AmbiguousCandidateInfo(
                candidateIndex = 1,
                tagName = "button",
                accessibleName = "Sign Up Hero",
                visibleText = "Sign Up",
                role = "button",
                attributes = mapOf("type" to "button", "data-testid" to "hero-cta"),
                parentContext = "section#hero",
                nearbyText = "Get Started",
                recommendedSelector = "[data-testid='hero-cta']"
            )
        )
        val diag = LocatorResolutionDiagnostics(
            targetLabel = "signup",
            recordedLocator = "signup",
            totalMatches = 2,
            visibleMatches = 2,
            interactableMatches = 2,
            finalOutcome = "ELEMENT_AMBIGUOUS",
            attemptedCandidates = listOf(
                LocatorAttemptDiagnostic(
                    strategy = "SCOPED_TEXT",
                    selector = "signup",
                    matchCount = 2,
                    visibleCount = 2,
                    interactableCount = 2,
                    selected = false,
                    rejectedReason = "Ambiguous: matched 2 visible elements"
                )
            )
        )
        val rerunChosen = AtomicReference<AmbiguousCandidateInfo?>()

        composeRule.setContent {
            ApexBrowserTheme {
                LocatorDisambiguationDialog(
                    state = PendingDisambiguationState(
                        workflowId = 1L,
                        stepId = "s1",
                        stepDescription = "Click signup",
                        originalTarget = "signup",
                        source = DisambiguationSource.EXECUTION,
                        candidates = candidates,
                        diagnostics = diag
                    ),
                    onSelectAndSave = {},
                    onSelectAndRerun = { rerunChosen.set(it) },
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("locator_disambiguation_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("disambiguation_candidate_list")
            .performScrollToNode(hasTestTag("disambiguation_candidate_1"))
        composeRule.onNodeWithText("Inside section#hero • Near \"Get Started\"").assertIsDisplayed()

        // Expand diagnostics accordion
        composeRule.onNodeWithTag("disambiguation_candidate_list")
            .performScrollToNode(hasTestTag("disambiguation_diagnostics_toggle"))
        composeRule.onNodeWithTag("disambiguation_diagnostics_toggle").performClick()
        composeRule.onNodeWithTag("disambiguation_candidate_list")
            .performScrollToNode(hasText("Recorded locator: signup"))
        composeRule.onNodeWithText("Recorded locator: signup").assertIsDisplayed()

        // Choose second candidate & tap Select & Re-run
        composeRule.onNodeWithTag("disambiguation_candidate_list")
            .performScrollToNode(hasTestTag("disambiguation_candidate_1"))
        composeRule.onNodeWithTag("disambiguation_candidate_1").performClick()
        composeRule.onNodeWithTag("disambiguation_rerun_button").performClick()
        assertEquals("[data-testid='hero-cta']", rerunChosen.get()?.recommendedSelector)
    }

    @Test
    fun executionStatusBanner_showsChooseTargetAndExpandableDiagnosticsOnAmbiguousFailure() {
        var opened = false
        val diag = LocatorResolutionDiagnostics(
            targetLabel = "signup",
            recordedLocator = "signup",
            totalMatches = 5,
            visibleMatches = 4,
            interactableMatches = 4,
            finalOutcome = "ELEMENT_AMBIGUOUS"
        )
        val execState = WorkflowExecutionState(
            workflowId = 9L,
            workflowName = "account.fydeos.io Task",
            status = "FAILED",
            currentStepIndex = 0,
            totalSteps = 1,
            verificationMessage = "Ambiguous locator matched 4 visible elements for 'signup'.",
            errorCode = AutomationErrorCode.ELEMENT_AMBIGUOUS,
            ambiguousCandidates = listOf(
                AmbiguousCandidateInfo(
                    candidateIndex = 0,
                    tagName = "a",
                    accessibleName = "Sign Up",
                    recommendedSelector = "[data-testid='hero-cta']"
                )
            ),
            lastLocatorDiagnostics = diag
        )

        composeRule.setContent {
            ApexBrowserTheme {
                AutomationExecutionStatusBanner(
                    executionState = execState,
                    onDismiss = {},
                    onOpenDisambiguation = { opened = true },
                    lastLocatorDiagnostics = diag
                )
            }
        }

        composeRule.onNodeWithTag("automation_choose_target_button").assertIsDisplayed().performClick()
        assertTrue(opened)

        composeRule.onNodeWithTag("automation_diagnostics_toggle").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("automation_diagnostics_panel").assertIsDisplayed()
        composeRule.onNodeWithText("Total 5 • Visible 4 • Interactable 4 • Outcome: ELEMENT_AMBIGUOUS").assertIsDisplayed()
    }

    // =========================================================================
    // 6. Comprehensive 15-Scenario Verification Suite
    // =========================================================================

    @Test
    fun uniqueSignupButton_duplicateNames_duplicateIds_multipleForms_nestedContainers() = runBlocking {
        val wv = WebView(context)
        try {
            // Case 1: Unique Signup button
            val uniqueHtml = """
                <html><body>
                    <main>
                        <button id="signup-cta" type="button" data-testid="primary-signup">Sign Up</button>
                    </main>
                </body></html>
            """.trimIndent()
            UniversalDomScanner.attachHtmlFixture(wv, "https://account.fydeos.io/unique", uniqueHtml)
            val uniqueScan = engine.scanPageElements(wv, "https://account.fydeos.io/unique", "tab_u")
            val uniqueBtn = uniqueScan.elements.single()
            assertFalse(uniqueBtn.isLocatorAmbiguous)
            val uniqueMatch = engine.universalScanner.matchCandidatesForInstruction(
                clause = "click signup",
                actionType = AutomationActionType.CLICK,
                targetLabel = "Sign Up",
                inspectionState = uniqueScan
            )
            assertFalse(uniqueMatch.isAmbiguous)
            assertEquals(uniqueBtn.selector, uniqueMatch.primaryCandidate?.selector)

            // Case 2 & 3: Five elements with similar Signup text / Duplicate accessible names
            val fiveHtml = """
                <html><body>
                    <header><button type="button" aria-label="Sign Up">Sign Up</button></header>
                    <nav><button type="button" aria-label="Sign Up">Sign Up Now</button></nav>
                    <main><button type="button" aria-label="Sign Up">Sign Up</button></main>
                    <section><a href="/signup?a=1" aria-label="Sign Up">Signup</a></section>
                    <footer><button type="button" aria-label="Sign Up">Sign Up</button></footer>
                </body></html>
            """.trimIndent()
            UniversalDomScanner.attachHtmlFixture(wv, "https://account.fydeos.io/five", fiveHtml)
            val fiveScan = engine.scanPageElements(wv, "https://account.fydeos.io/five", "tab_5")
            val fiveMatch = engine.universalScanner.matchCandidatesForInstruction(
                clause = "click sign up",
                actionType = AutomationActionType.CLICK,
                targetLabel = "Sign Up",
                inspectionState = fiveScan
            )
            assertTrue(fiveMatch.isAmbiguous)
            assertTrue(fiveMatch.matchedElements.size >= 4)
            // ARIA_ROLE_AND_NAME candidate on duplicate aria-label buttons must NOT be marked unique
            val dupAriaBtn = fiveScan.elements.first { it.tagName == "button" }
            assertFalse(
                dupAriaBtn.locatorCandidates
                    .filter { it.strategy == LocatorStrategy.ARIA_ROLE_AND_NAME }
                    .any { it.isUnique }
            )

            // Case 4: Duplicate IDs across sections -> #signup-btn is non-unique, container-scoped IS unique
            val dupIdHtml = """
                <html><body>
                    <section id="hero-panel"><button id="signup-btn" type="button">Sign Up</button></section>
                    <section id="footer-panel"><button id="signup-btn" type="button">Sign Up</button></section>
                </body></html>
            """.trimIndent()
            UniversalDomScanner.attachHtmlFixture(wv, "https://account.fydeos.io/dupid", dupIdHtml)
            val dupIdScan = engine.scanPageElements(wv, "https://account.fydeos.io/dupid", "tab_id")
            assertEquals(2, dupIdScan.elements.size)
            dupIdScan.elements.forEach { el ->
                val rawIdCand = el.locatorCandidates.first { it.strategy == LocatorStrategy.STABLE_ID }
                assertFalse(rawIdCand.isUnique)
                assertEquals(2, rawIdCand.matchCount)
                val scopedIdCand = el.locatorCandidates.first {
                    it.strategy == LocatorStrategy.CONTAINER_SCOPED && it.selector.contains("#signup-btn")
                }
                assertTrue(scopedIdCand.isUnique)
                assertEquals(1, scopedIdCand.matchCount)
            }

            // Case 5: Multiple forms with similar controls -> FORM_RELATIVE is unique per form
            val multiFormHtml = """
                <html><body>
                    <form id="login-form">
                        <input type="email" name="email" placeholder="Email" />
                        <button type="submit">Continue</button>
                    </form>
                    <form id="signup-form">
                        <input type="email" name="email" placeholder="Email" />
                        <button type="submit">Continue</button>
                    </form>
                </body></html>
            """.trimIndent()
            UniversalDomScanner.attachHtmlFixture(wv, "https://account.fydeos.io/forms", multiFormHtml)
            val formScan = engine.scanPageElements(wv, "https://account.fydeos.io/forms", "tab_forms")
            val emails = formScan.elements.filter { it.name == "email" }
            assertEquals(2, emails.size)
            emails.forEach { emailEl ->
                val rawName = emailEl.locatorCandidates.first { it.strategy == LocatorStrategy.STABLE_NAME }
                assertFalse(rawName.isUnique)
                val formScoped = emailEl.locatorCandidates.first {
                    it.strategy == LocatorStrategy.FORM_RELATIVE && it.selector.contains("input[name=\"email\"]")
                }
                assertTrue(formScoped.isUnique)
                assertEquals(1, formScoped.matchCount)
            }
            val emailMatch = engine.universalScanner.matchCandidatesForInstruction(
                clause = "enter email",
                actionType = AutomationActionType.INPUT,
                targetLabel = "email",
                inspectionState = formScan
            )
            assertTrue(emailMatch.isAmbiguous)
            assertEquals(2, emailMatch.matchedElements.size)

            // Case 6: Nested containers where inner data-testid repeats across outer panels
            val nestedHtml = """
                <html><body>
                    <section id="plan-basic">
                        <div data-testid="card-actions"><button type="button">Choose Plan</button></div>
                    </section>
                    <section id="plan-pro">
                        <div data-testid="card-actions"><button type="button">Choose Plan</button></div>
                    </section>
                </body></html>
            """.trimIndent()
            UniversalDomScanner.attachHtmlFixture(wv, "https://account.fydeos.io/nested", nestedHtml)
            val nestedScan = engine.scanPageElements(wv, "https://account.fydeos.io/nested", "tab_nested")
            assertEquals(2, nestedScan.elements.size)
            assertTrue(nestedScan.elements.any { el ->
                el.locatorCandidates.any {
                    it.isUnique && it.selector.contains("section#plan-basic") && it.selector.contains("card-actions")
                }
            })
            assertTrue(nestedScan.elements.any { el ->
                el.locatorCandidates.any {
                    it.isUnique && it.selector.contains("section#plan-pro") && it.selector.contains("card-actions")
                }
            })
        } finally {
            UniversalDomScanner.clearAttachedFixture(wv)
        }
    }

    @Test
    fun hiddenDisabledDynamicSpaShadowAndIframeElements_areHandledDeterministically() = runBlocking {
        val wv = WebView(context)
        try {
            // Case 7 & 8: Hidden & Disabled elements
            val statesHtml = """
                <html><body>
                    <button id="vis-btn" type="button">Sign Up</button>
                    <div style="display:none"><button id="hid-btn" type="button">Sign Up</button></div>
                    <button id="dis-btn" type="button" disabled>Continue</button>
                </body></html>
            """.trimIndent()
            UniversalDomScanner.attachHtmlFixture(wv, "https://account.fydeos.io/states", statesHtml)
            val statesScan = engine.scanPageElements(wv, "https://account.fydeos.io/states", "tab_st")
            val visSignup = statesScan.elements.first { it.htmlId == "vis-btn" }
            val hidSignup = statesScan.elements.first { it.htmlId == "hid-btn" }
            val disBtn = statesScan.elements.first { it.htmlId == "dis-btn" }
            assertTrue(visSignup.visible)
            assertFalse(hidSignup.visible)
            assertTrue(disBtn.disabled)
            // Hidden duplicate does NOT cause false ambiguity against the sole visible Sign Up button
            val visMatch = engine.universalScanner.matchCandidatesForInstruction(
                clause = "click sign up",
                actionType = AutomationActionType.CLICK,
                targetLabel = "Sign Up",
                inspectionState = statesScan
            )
            assertFalse(visMatch.isAmbiguous)
            assertEquals("#vis-btn", visMatch.primaryCandidate?.selector)

            // Runtime disabled & hidden protection checks
            val disabledJs = UniversalActionScripts.buildStepExecutionScript(
                AutomationStep(
                    id = "s_dis",
                    order = 1,
                    actionType = AutomationActionType.CLICK,
                    targetLabel = "Continue",
                    selector = "#dis-btn",
                    description = "Click Continue"
                )
            )
            assertTrue(disabledJs.contains("ELEMENT_DISABLED"))
            assertTrue(disabledJs.contains("ELEMENT_NOT_INTERACTABLE"))

            // Case 9: Dynamic DOM updates via incremental mutation batch
            val activeSession = engine.universalScanner.getActiveSession("tab_st")!!
            val mutPayload = JSONObject()
                .put("sessionId", activeSession.scanSessionId)
                .put("tabId", "tab_st")
                .put("origin", statesScan.canonicalOrigin)
                .put(
                    "updatedElements",
                    JSONArray().put(
                        JSONObject()
                            .put("tag", "button")
                            .put("htmlId", "dyn-signup-cta")
                            .put("role", "button")
                            .put("accessibleName", "Sign Up Dynamic")
                            .put("visibleText", "Sign Up Dynamic")
                            .put("selector", "#dyn-signup-cta")
                            .put("locatorStrategy", "STABLE_ID")
                            .put("controlKind", "BUTTON")
                            .put("visible", true)
                            .put("disabled", false)
                            .put("inViewport", true)
                            .put("interactable", true)
                    )
                )
                .toString()
            val afterMutation = engine.universalScanner.processDynamicMutationBatch(
                rawJson = mutPayload,
                sourceTabId = "tab_st",
                sourceWebView = wv,
                liveWebViewUrl = "https://account.fydeos.io/states"
            )
            assertNotNull(afterMutation)
            assertTrue(afterMutation!!.summary.dynamicUpdatesProcessed >= 1)
            assertTrue(afterMutation.elements.any { it.htmlId == "dyn-signup-cta" })

            // Case 10: SPA navigation marks old scan stale and rescans cleanly on new same-origin route
            engine.universalScanner.onTabNavigated("tab_st", "https://account.fydeos.io/register-step2", wv)
            val staleInspection = engine.universalScanner.getLatestInspection("tab_st")!!
            assertTrue(staleInspection.elements.all { it.stale })

            // Case 11 & 12: Shadow DOM + Same-origin iframe + Cross-origin iframe isolation
            val embeddedHtml = """
                <html><body>
                    <fyde-auth-card id="auth-host">
                        <template shadowrootmode="open">
                            <button id="shadow-signup" type="button">Sign Up</button>
                        </template>
                    </fyde-auth-card>
                    <iframe id="local-frame" src="https://account.fydeos.io/embedded/signup">
                        <button id="iframe-signup" type="button">Sign Up</button>
                    </iframe>
                    <iframe id="third-party" src="https://external-auth.example.net/widget"></iframe>
                </body></html>
            """.trimIndent()
            UniversalDomScanner.attachHtmlFixture(wv, "https://account.fydeos.io/embed", embeddedHtml)
            val embedScan = engine.scanPageElements(wv, "https://account.fydeos.io/embed", "tab_embed")

            val shadowBtn = embedScan.elements.first { it.htmlId == "shadow-signup" }
            assertEquals(listOf("fyde-auth-card#auth-host"), shadowBtn.shadowHostPath)
            assertEquals(LocatorStrategy.SHADOW_DOM_CHAIN, shadowBtn.locatorStrategy)
            assertTrue(shadowBtn.selector.contains(">>>"))

            val iframeBtn = embedScan.elements.first { it.htmlId == "iframe-signup" }
            assertEquals(listOf("iframe#local-frame"), iframeBtn.framePath)
            assertEquals(LocatorStrategy.IFRAME_CHAIN, iframeBtn.locatorStrategy)
            assertTrue(iframeBtn.selector.contains(">>"))

            assertTrue(embedScan.inaccessibleFrames.any { it.reason == "cross-origin" })
        } finally {
            UniversalDomScanner.clearAttachedFixture(wv)
        }
    }

    @Test
    fun staleLocatorRecovery_originGuard_nestedCandidateSelection_andFirstClickPrevention() = runBlocking {
        val wv = WebView(context)
        try {
            // Case 13: Stale locator recovery -> re-evaluates DOM on ELEMENT_STALE and succeeds on unique fresh target
            val freshHtml = """
                <html><body>
                    <main>
                        <button id="new-signup-btn" data-testid="new-signup" type="button">Sign Up</button>
                    </main>
                </body></html>
            """.trimIndent()
            UniversalDomScanner.attachHtmlFixture(wv, "https://account.fydeos.io/home", freshHtml)

            var callCount = 0
            val recoveryWv = FakeEvalWebView(context) { script, cb ->
                if (cb == null) return@FakeEvalWebView
                // Trigger scanPage JS call as no-op (fixture handles scanPageElements)
                if (script.contains("__apexScan")) return@FakeEvalWebView
                callCount++
                if (script.contains("#old-signup-btn") && !script.contains("new-signup")) {
                    cb.onReceiveValue("""{"ok":false,"code":"ELEMENT_STALE","msg":"Element was detached from DOM"}""")
                } else {
                    cb.onReceiveValue("""{"ok":true,"code":"NONE","verified":true,"resolvedSelector":"button[data-testid=\"new-signup\"]","msg":"Activated Sign Up"}""")
                }
            }
            UniversalDomScanner.attachHtmlFixture(recoveryWv, "https://account.fydeos.io/home", freshHtml)

            val staleStep = AutomationStep(
                id = "s_stale",
                order = 1,
                actionType = AutomationActionType.CLICK,
                targetLabel = "Sign Up",
                selector = "#old-signup-btn",
                description = "Click Sign Up"
            )
            val recoveredOutcome = engine.executeSingleStepWithRecovery(
                step = staleStep,
                activeUrl = "https://account.fydeos.io/home",
                tabId = "tab_rec",
                webViewProvider = { recoveryWv },
                onNavigateUrl = {}
            )
            assertTrue(recoveredOutcome.success)
            assertTrue(recoveredOutcome.recoveredViaFallback)
            assertEquals("button[data-testid=\"new-signup\"]", recoveredOutcome.resolvedSelector)

            // Recovery aborts securely if navigation shifted origin away from activeUrl
            val navSync = com.example.automation.TabNavigationSynchronizer()
            val tId = "tab_origin_shift"
            navSync.onPageStarted(tId, "https://phishing.example.org/login")
            val blockedOriginOutcome = engine.executeSingleStepWithRecovery(
                step = staleStep,
                activeUrl = "https://account.fydeos.io/home",
                tabId = tId,
                navigationSynchronizer = navSync,
                webViewProvider = { recoveryWv },
                onNavigateUrl = {}
            )
            assertFalse(blockedOriginOutcome.success)
            assertEquals(AutomationErrorCode.UNEXPECTED_ORIGIN_CHANGE, blockedOriginOutcome.errorCode)

            // Case 14 & 15: Phase 6 RecoveryPolicy NEVER swallows ELEMENT_AMBIGUOUS to click first element,
            // and user-selected candidate refines nested workflow steps cleanly.
            val ambigResp = JSONObject()
                .put("ok", false)
                .put("code", "ELEMENT_AMBIGUOUS")
                .put("msg", "Ambiguous locator matched 5 elements for \"signup\".")
                .put(
                    "ambiguousCandidates",
                    JSONArray().put(
                        JSONObject()
                            .put("candidateIndex", 0)
                            .put("tagName", "a")
                            .put("accessibleName", "Sign Up")
                            .put("parentContext", "nav")
                            .put("recommendedSelector", "nav a[href='/signup?src=nav']")
                    ).put(
                        JSONObject()
                            .put("candidateIndex", 1)
                            .put("tagName", "button")
                            .put("accessibleName", "Sign Up")
                            .put("parentContext", "main")
                            .put("recommendedSelector", "[data-testid='main-signup']")
                    )
                )
                .toString()

            val vm = createViewModel()
            val tabId = vm.uiState.value.activeTab.id
            val pageUrl = "https://account.fydeos.io/"
            val branchWv = FakeEvalWebView(context) { script, cb ->
                if (cb == null) return@FakeEvalWebView
                when {
                    script.contains("readyState") ->
                        cb.onReceiveValue("""{"readyState":"complete","url":"$pageUrl","shape":"5:100","targetReady":true}""")
                    script.contains("main-signup") ->
                        cb.onReceiveValue("""{"ok":true,"code":"NONE","verified":true,"msg":"Clicked main signup"}""")
                    else -> cb.onReceiveValue(ambigResp)
                }
            }
            vm.onWebViewPageFinished(tabId, "FydeOS", pageUrl, false, false)
            vm.registerWebView(tabId, branchWv)

            val wfId = repository.saveAutomationWorkflow(
                name = "Policy Safeguard Workflow",
                domain = "account.fydeos.io",
                sourceUrl = pageUrl,
                type = AutomationType.MANUAL,
                steps = listOf(
                    AutomationStep(
                        id = "s_ambig_policy",
                        order = 1,
                        actionType = AutomationActionType.CLICK,
                        targetLabel = "signup",
                        selector = "signup",
                        description = "Click signup",
                        recoveryPolicy = com.example.automation.RecoveryPolicy(
                            maxRetries = 2,
                            retryOnCodes = setOf(
                                AutomationErrorCode.ELEMENT_NOT_FOUND,
                                AutomationErrorCode.ELEMENT_AMBIGUOUS
                            )
                        )
                    )
                ),
                instructionsText = "click signup"
            )
            vm.runWorkflow(wfId, confirmDestructive = true)
            idleMainUntil { vm.uiState.value.executionState?.status == "FAILED" }
            assertEquals(AutomationErrorCode.ELEMENT_AMBIGUOUS, vm.uiState.value.executionState?.errorCode)
            assertNotNull(vm.uiState.value.pendingDisambiguation)

            // User picks second candidate -> updates step & re-runs -> COMPLETED
            vm.resolveAmbiguousLocator(
                vm.uiState.value.pendingDisambiguation!!.candidates[1],
                rerunAfterSelection = true
            )
            idleMainUntil { vm.uiState.value.executionState?.status == "COMPLETED" }

            // Recorder script includes per-candidate unique selector builder & semantic clickable roles
            val recorderJs = engine.buildRecorderInstallScript("s_rec", tabId, "https://account.fydeos.io:443", 0L)
            assertTrue(recorderJs.contains("buildUniqueSelectorForNode"))
            assertTrue(recorderJs.contains("[role=\"link\"]"))
            assertTrue(recorderJs.contains("[role=\"tab\"]"))
            assertTrue(recorderJs.contains("[role=\"menuitem\"]"))
            assertFalse(recorderJs.contains("recommendedSelector: top.selector\n"))
        } finally {
            UniversalDomScanner.clearAttachedFixture(wv)
        }
    }

    @Test
    fun nestedConditionAndAlternativeBranchAmbiguity_versionPreservation_rerunLocatorSwap_andSensitiveRedaction() = runBlocking {
        val pageUrl = "https://account.fydeos.io/flow"
        val ambigResp = JSONObject()
            .put("ok", false)
            .put("code", "ELEMENT_AMBIGUOUS")
            .put("msg", "Ambiguous locator matched 2 elements for \"signup\".")
            .put(
                "ambiguousCandidates",
                JSONArray().put(
                    JSONObject()
                        .put("candidateIndex", 0)
                        .put("tagName", "a")
                        .put("accessibleName", "Sign Up")
                        .put("parentContext", "nav")
                        .put("recommendedSelector", "nav a[href='/signup']")
                ).put(
                    JSONObject()
                        .put("candidateIndex", 1)
                        .put("tagName", "button")
                        .put("accessibleName", "Sign Up")
                        .put("parentContext", "main#hero")
                        .put("recommendedSelector", "[data-testid='hero-signup-btn']")
                )
            )
            .toString()

        val observedActionScripts = mutableListOf<String>()
        val wv = FakeEvalWebView(context) { script, cb ->
            if (cb == null) return@FakeEvalWebView
            when {
                script.contains("readyState") ->
                    cb.onReceiveValue("""{"readyState":"complete","url":"$pageUrl","shape":"5:100","targetReady":true}""")
                script.contains("hero-signup-btn") -> {
                    observedActionScripts.add(script)
                    cb.onReceiveValue("""{"ok":true,"code":"NONE","verified":true,"resolvedSelector":"[data-testid='hero-signup-btn']","msg":"Clicked hero signup"}""")
                }
                script.contains("missing-primary-btn") -> {
                    observedActionScripts.add(script)
                    cb.onReceiveValue("""{"ok":false,"code":"ELEMENT_NOT_FOUND","msg":"Primary missing"}""")
                }
                else -> {
                    observedActionScripts.add(script)
                    cb.onReceiveValue(ambigResp)
                }
            }
        }

        val vm = createViewModel()
        val tabId = vm.uiState.value.activeTab.id
        vm.onWebViewPageFinished(tabId, "FydeOS", pageUrl, false, false)
        vm.registerWebView(tabId, wv)

        // 1. Nested CONDITION (thenSteps) ambiguity -> refines ONLY nested step ID -> creates v2 -> re-run uses new locator without old ambiguous selector
        val condStep = AutomationStep(
            id = "step_cond",
            order = 1,
            actionType = AutomationActionType.CONDITION,
            targetLabel = "URL matches",
            description = "If on flow page",
            condition = com.example.automation.ConditionNode.Predicate(
                operator = com.example.automation.ConditionOperator.URL_MATCHES,
                expectedValue = "account.fydeos.io/flow"
            ),
            thenSteps = listOf(
                AutomationStep(
                    id = "nested_then_signup",
                    order = 1,
                    actionType = AutomationActionType.CLICK,
                    targetLabel = "signup",
                    selector = ".generic-signup-ambig",
                    description = "Click nested signup",
                    locatorCandidates = listOf(
                        com.example.automation.LocatorCandidate(
                            strategy = LocatorStrategy.SCOPED_CSS,
                            selector = ".generic-signup-ambig",
                            isUnique = false,
                            matchCount = 2,
                            isAmbiguous = true
                        )
                    )
                )
            )
        )
        val condWfId = repository.saveAutomationWorkflow(
            name = "Nested Conditional Workflow",
            domain = "account.fydeos.io",
            sourceUrl = pageUrl,
            type = AutomationType.MANUAL,
            steps = listOf(condStep),
            instructionsText = "if on flow click signup"
        )
        vm.runWorkflow(condWfId, confirmDestructive = true)
        idleMainUntil { vm.uiState.value.pendingDisambiguation != null }

        val condPending = vm.uiState.value.pendingDisambiguation!!
        assertEquals("nested_then_signup", condPending.stepId)
        assertEquals(2, condPending.candidates.size)

        observedActionScripts.clear()
        vm.resolveAmbiguousLocator(condPending.candidates[1], rerunAfterSelection = true)
        idleMainUntil { vm.uiState.value.executionState?.status == "COMPLETED" }

        // Check version history: v1 kept old ambiguous selector, v2 preserved refined locator
        val versions = repository.getWorkflowVersionsOnce(condWfId)
        assertEquals(2, versions.size)
        val v1Nested = AutomationStepCodec.decode(versions.first { it.versionNumber == 1 }.stepsJson).single().thenSteps.single()
        val v2Nested = AutomationStepCodec.decode(versions.first { it.versionNumber == 2 }.stepsJson).single().thenSteps.single()
        assertEquals(".generic-signup-ambig", v1Nested.selector)
        assertEquals("[data-testid='hero-signup-btn']", v2Nested.selector)
        assertFalse(v2Nested.locatorCandidates.any { it.selector == ".generic-signup-ambig" })
        assertTrue(observedActionScripts.any { it.contains("[data-testid='hero-signup-btn']") && !it.contains(".generic-signup-ambig") })

        // 2. RecoveryPolicy ALTERNATIVE_BRANCH hitting ELEMENT_AMBIGUOUS surfaces candidates instead of masking under ELEMENT_NOT_FOUND
        val altStep = AutomationStep(
            id = "step_with_alt",
            order = 1,
            actionType = AutomationActionType.CLICK,
            targetLabel = "Primary",
            selector = "#missing-primary-btn",
            description = "Click Primary",
            recoveryPolicy = com.example.automation.RecoveryPolicy(
                strategy = com.example.automation.RecoveryStrategy.ALTERNATIVE_BRANCH,
                maxRetries = 1,
                retryOnCodes = setOf(AutomationErrorCode.ELEMENT_NOT_FOUND),
                alternativeSteps = listOf(
                    AutomationStep(
                        id = "alt_branch_signup",
                        order = 1,
                        actionType = AutomationActionType.CLICK,
                        targetLabel = "signup",
                        selector = ".alt-ambig-signup",
                        description = "Fallback signup"
                    )
                )
            )
        )
        val altWfId = repository.saveAutomationWorkflow(
            name = "Alt Branch Ambiguity Workflow",
            domain = "account.fydeos.io",
            sourceUrl = pageUrl,
            type = AutomationType.MANUAL,
            steps = listOf(altStep),
            instructionsText = "click primary or fallback"
        )
        vm.runWorkflow(altWfId, confirmDestructive = true)
        idleMainUntil { vm.uiState.value.executionState?.status == "FAILED" }
        assertEquals(AutomationErrorCode.ELEMENT_AMBIGUOUS, vm.uiState.value.executionState?.errorCode)
        val altPending = vm.uiState.value.pendingDisambiguation
        assertNotNull(altPending)
        assertEquals("alt_branch_signup", altPending!!.stepId)

        vm.resolveAmbiguousLocator(altPending.candidates[1], rerunAfterSelection = true)
        idleMainUntil { vm.uiState.value.executionState?.status == "COMPLETED" }
        val savedAltStep = AutomationStepCodec.decode(repository.getAutomationWorkflowById(altWfId)!!.stepsJson)
            .single().recoveryPolicy!!.alternativeSteps.single()
        assertEquals("[data-testid='hero-signup-btn']", savedAltStep.selector)

        // 3. Sensitive field attributes and runtime candidate text remain redacted in diagnostics
        val parsedCand = AmbiguousCandidateInfo.fromJson(
            JSONObject()
                .put("candidateIndex", 0)
                .put("tagName", "input")
                .put("recommendedSelector", "input[name='secret_token']")
                .put(
                    "attributes",
                    JSONObject()
                        .put("type", "password")
                        .put("value", "RawSecretValue999")
                        .put("data-token", "BearerTopSecret")
                        .put("secretKey", "TopSecret")
                        .put("data-testid", "auth-token-input")
                )
        )!!
        assertFalse(parsedCand.attributes.containsKey("value"))
        assertFalse(parsedCand.attributes.containsKey("data-token"))
        assertFalse(parsedCand.attributes.containsKey("secretKey"))
        assertEquals("auth-token-input", parsedCand.attributes["data-testid"])

        val actionJs = UniversalActionScripts.buildStepExecutionScript(altStep)
        assertTrue(actionJs.contains("const isSensitive = apexIsSensitiveEl(el);"))
        assertTrue(actionJs.contains("const visibleText = isSensitive ? ''"))
        assertTrue(actionJs.contains("nearbyText: isSensitive ? ''"))
    }

    @Test
    fun optionalAndCallWorkflowAmbiguityCannotBypassAndRefinesSubWorkflow() = runBlocking {
        val pageUrl = "https://account.fydeos.io/onboarding"
        val ambigResp = JSONObject()
            .put("ok", false)
            .put("code", "ELEMENT_AMBIGUOUS")
            .put("msg", "Ambiguous locator matched 2 elements for \"signup\".")
            .put(
                "ambiguousCandidates",
                JSONArray().put(
                    JSONObject()
                        .put("candidateIndex", 0)
                        .put("tagName", "a")
                        .put("accessibleName", "Sign Up")
                        .put("parentContext", "nav")
                        .put("recommendedSelector", "nav a[href='/signup']")
                ).put(
                    JSONObject()
                        .put("candidateIndex", 1)
                        .put("tagName", "button")
                        .put("accessibleName", "Sign Up")
                        .put("parentContext", "main#hero")
                        .put("recommendedSelector", "[data-testid='subwf-signup-btn']")
                )
            )
            .toString()

        val observedActionScripts = mutableListOf<String>()
        val wv = FakeEvalWebView(context) { script, cb ->
            if (cb == null) return@FakeEvalWebView
            when {
                script.contains("readyState") ->
                    cb.onReceiveValue("""{"readyState":"complete","url":"$pageUrl","shape":"5:100","targetReady":true}""")
                script.contains("subwf-signup-btn") -> {
                    observedActionScripts.add(script)
                    cb.onReceiveValue("""{"ok":true,"code":"NONE","verified":true,"resolvedSelector":"[data-testid='subwf-signup-btn']","msg":"Clicked refined sub-workflow signup"}""")
                }
                else -> {
                    observedActionScripts.add(script)
                    cb.onReceiveValue(ambigResp)
                }
            }
        }

        val vm = createViewModel()
        val tabId = vm.uiState.value.activeTab.id
        vm.onWebViewPageFinished(tabId, "FydeOS", pageUrl, false, false)
        vm.registerWebView(tabId, wv)

        // 1. Even with isOptional = true, ELEMENT_AMBIGUOUS must remain fail-closed and surface candidates
        val optionalAmbigStep = AutomationStep(
            id = "opt_ambig_step",
            order = 1,
            actionType = AutomationActionType.CLICK,
            targetLabel = "signup",
            selector = ".opt-signup-ambig",
            description = "Optional signup click",
            isOptional = true
        )
        val optWfId = repository.saveAutomationWorkflow(
            name = "Optional Ambiguity Flow",
            domain = "account.fydeos.io",
            sourceUrl = pageUrl,
            type = AutomationType.MANUAL,
            steps = listOf(optionalAmbigStep),
            instructionsText = "optional signup"
        )
        vm.runWorkflow(optWfId, confirmDestructive = true)
        idleMainUntil { vm.uiState.value.executionState?.status == "FAILED" }
        assertEquals(AutomationErrorCode.ELEMENT_AMBIGUOUS, vm.uiState.value.executionState?.errorCode)
        assertNotNull(vm.uiState.value.pendingDisambiguation)
        assertEquals("opt_ambig_step", vm.uiState.value.pendingDisambiguation!!.stepId)

        // Dismissing and reopening from execution banner/details preserves exact failing stepId
        vm.dismissPendingDisambiguation()
        assertNull(vm.uiState.value.pendingDisambiguation)
        vm.openDisambiguationForExecution()
        assertNotNull(vm.uiState.value.pendingDisambiguation)
        assertEquals("opt_ambig_step", vm.uiState.value.pendingDisambiguation!!.stepId)
        vm.dismissPendingDisambiguation()

        // 2. CALL_WORKFLOW child hitting ELEMENT_AMBIGUOUS refines child sub-workflow & preserves versions
        val subStep = AutomationStep(
            id = "sub_signup_step",
            order = 1,
            actionType = AutomationActionType.CLICK,
            targetLabel = "signup",
            selector = ".sub-signup-ambig",
            description = "Click signup inside sub-workflow"
        )
        val subWfId = repository.saveAutomationWorkflow(
            name = "Sub Signup Flow",
            domain = "account.fydeos.io",
            sourceUrl = pageUrl,
            type = AutomationType.MANUAL,
            steps = listOf(subStep),
            instructionsText = "click signup"
        )
        val parentCallStep = AutomationStep(
            id = "parent_call_step",
            order = 1,
            actionType = AutomationActionType.CALL_WORKFLOW,
            targetLabel = "Sub Signup Flow",
            description = "Call Sub Signup Flow",
            subWorkflowRef = com.example.automation.SubWorkflowReference(
                targetWorkflowId = subWfId,
                targetWorkflowName = "Sub Signup Flow"
            )
        )
        val parentWfId = repository.saveAutomationWorkflow(
            name = "Parent Orchestrator Flow",
            domain = "account.fydeos.io",
            sourceUrl = pageUrl,
            type = AutomationType.MANUAL,
            steps = listOf(parentCallStep),
            instructionsText = "call sub signup"
        )

        vm.runWorkflow(parentWfId, confirmDestructive = true)
        idleMainUntil { vm.uiState.value.pendingDisambiguation != null }
        val parentPending = vm.uiState.value.pendingDisambiguation!!
        assertEquals("sub_signup_step", parentPending.stepId)

        // Re-opening after dismiss still resolves inner sub-workflow stepId, not parent_call_step
        vm.dismissPendingDisambiguation()
        vm.openDisambiguationForExecution()
        assertEquals("sub_signup_step", vm.uiState.value.pendingDisambiguation!!.stepId)

        observedActionScripts.clear()
        vm.resolveAmbiguousLocator(parentPending.candidates[1], rerunAfterSelection = true)
        idleMainUntil { vm.uiState.value.executionState?.status == "COMPLETED" }

        val subVersions = repository.getWorkflowVersionsOnce(subWfId)
        assertEquals(2, subVersions.size)
        val v1Sub = AutomationStepCodec.decode(subVersions.first { it.versionNumber == 1 }.stepsJson).single()
        val v2Sub = AutomationStepCodec.decode(subVersions.first { it.versionNumber == 2 }.stepsJson).single()
        assertEquals(".sub-signup-ambig", v1Sub.selector)
        assertEquals("[data-testid='subwf-signup-btn']", v2Sub.selector)
        assertTrue(observedActionScripts.any { it.contains("[data-testid='subwf-signup-btn']") })

        // 3. Recorder script includes sensitive-field scrubbing for ambiguous candidate lists
        val recorderJs = engine.buildRecorderInstallScript("sess-1", "tab-1", pageUrl, 0)
        assertTrue(recorderJs.contains("function isSensitiveNode(el)"))
        assertTrue(recorderJs.contains("if (sensitive && k === 'placeholder') continue;"))
        assertTrue(recorderJs.contains("visibleText: fSensitive ? ''"))
        assertTrue(recorderJs.contains("nearbyText: fSensitive ? ''"))
    }
}
