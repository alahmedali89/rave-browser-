package com.example

import android.content.Context
import android.os.Looper
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.automation.AutomationActionType
import com.example.automation.AutomationEngine
import com.example.automation.AutomationStep
import com.example.automation.AutomationType
import com.example.automation.CredentialAuthenticationManager
import com.example.automation.CredentialFieldRole
import com.example.automation.CredentialVaultCrypto
import com.example.automation.CredentialVaultManager
import com.example.automation.CredentialVaultOperationResult
import com.example.automation.InMemoryVaultKeyMetadataStore
import com.example.automation.InMemoryVaultKeyProvider
import com.example.automation.Phase6ExecutionCoordinator
import com.example.data.local.ApexDatabase
import com.example.data.local.AutomationWorkflowEntity
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.BrowserRepository
import com.example.engine.FastDownloadEngine
import com.example.ui.browser.BrowserScreen
import com.example.ui.theme.ApexBrowserTheme
import com.example.ui.viewmodel.BrowserViewModel
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlayAutomationRedirectTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class RedirectCapableWebView(
        context: Context,
        var fakeUrl: String = "https://news.example.com/feed"
    ) : WebView(context) {
        val evaluatedScripts = mutableListOf<String>()

        override fun getUrl(): String = fakeUrl

        override fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {
            evaluatedScripts.add(script)
            val origin = AutomationEngine().parseOrigin(fakeUrl).canonicalOrigin
            when {
                script.contains("readyState") -> {
                    val payload = JSONObject().apply {
                        put("ready", true)
                        put("state", "complete")
                        put("hasBody", true)
                        put("origin", origin)
                    }.toString()
                    resultCallback?.onReceiveValue(JSONObject.quote(payload))
                }
                script.contains("CREDENTIAL_FIELD_INVALID") -> {
                    val payload = JSONObject().apply {
                        put("ok", true)
                        put("code", "NONE")
                        put("verifiedLength", true)
                        put("msg", "Filled password field securely.")
                    }.toString()
                    resultCallback?.onReceiveValue(JSONObject.quote(payload))
                }
                script.contains("apexScannerResults") || script.contains("elements") && script.contains("viewportWidth") -> {
                    val payload = JSONObject().apply {
                        put("ok", true)
                        put("elements", org.json.JSONArray())
                    }.toString()
                    resultCallback?.onReceiveValue(JSONObject.quote(payload))
                }
                else -> {
                    val payload = JSONObject().apply {
                        put("ok", true)
                        put("code", "NONE")
                        put("msg", "Executed action cleanly")
                        put("verified", true)
                    }.toString()
                    resultCallback?.onReceiveValue(JSONObject.quote(payload))
                }
            }
        }
    }

    private fun idleMainUntil(timeoutMs: Long = 5_000L, predicate: () -> Boolean) {
        val mainLooper = Shadows.shadowOf(Looper.getMainLooper())
        val deadline = System.currentTimeMillis() + timeoutMs
        mainLooper.idle()
        while (!predicate() && System.currentTimeMillis() < deadline) {
            mainLooper.idleFor(25L, TimeUnit.MILLISECONDS)
        }
        mainLooper.idle()
        assertTrue("Timed out waiting for condition", predicate())
    }

    private fun createEnv(): Triple<ApexDatabase, BrowserRepository, BrowserViewModel> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        val prefs = UserPreferencesRepository(context)
        val metadataStore = InMemoryVaultKeyMetadataStore()
        val keyProvider = InMemoryVaultKeyProvider(metadataStore = metadataStore)
        val crypto = CredentialVaultCrypto(keyProvider = keyProvider, metadataStore = metadataStore)
        val authManager = CredentialAuthenticationManager(context = context)
        val engine = AutomationEngine()
        val vaultManager = CredentialVaultManager(
            dao = db.apexDao(),
            crypto = crypto,
            authManager = authManager,
            originParser = { url -> engine.parseOrigin(url) }
        )
        val sp = context.getSharedPreferences("play_redirect_${UUID.randomUUID()}", Context.MODE_PRIVATE)
        val repo = BrowserRepository(
            dao = db.apexDao(),
            preferencesRepository = prefs,
            sharedPrefs = sp,
            credentialVaultManager = vaultManager
        )
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
        return Triple(db, repo, vm)
    }

    @Test
    fun resolveAndMatchHelpers_coverSourceUrlFallbackAndTrailingSlashNormalization() {
        val withSource = AutomationWorkflowEntity(
            id = 1L,
            name = "Checkout",
            type = AutomationType.RECORDED.name,
            domain = "shop.example.com",
            sourceUrl = "https://shop.example.com/checkout",
            instructionsText = "",
            stepsJson = "[]",
            stepCount = 1
        )
        assertEquals(
            "https://shop.example.com/checkout",
            Phase6ExecutionCoordinator.resolveWorkflowStartUrl(withSource, emptyList())
        )

        val domainOnly = withSource.copy(sourceUrl = "")
        assertEquals(
            "https://shop.example.com",
            Phase6ExecutionCoordinator.resolveWorkflowStartUrl(domainOnly, emptyList())
        )

        val navStepFirst = listOf(
            AutomationStep(
                id = "n1",
                order = 1,
                actionType = AutomationActionType.NAVIGATE,
                targetLabel = "Other Site",
                value = "https://other.example.com",
                description = "Open other site"
            )
        )
        assertEquals("", Phase6ExecutionCoordinator.resolveWorkflowStartUrl(domainOnly, navStepFirst))
        assertEquals(
            "https://shop.example.com/checkout",
            Phase6ExecutionCoordinator.resolveWorkflowStartUrl(withSource, navStepFirst)
        )

        assertTrue(
            Phase6ExecutionCoordinator.urlsMatchForWorkflowStart(
                "https://shop.example.com",
                "https://shop.example.com/"
            )
        )
        assertFalse(
            Phase6ExecutionCoordinator.urlsMatchForWorkflowStart(
                "https://news.example.com",
                "https://shop.example.com/checkout"
            )
        )
        assertFalse(
            Phase6ExecutionCoordinator.urlsMatchForWorkflowStart(
                "apex://home",
                "https://shop.example.com/checkout"
            )
        )
    }

    @Test
    fun playAutomationFromAnotherSite_dismissesOverlays_redirectsToTargetUrl_andExecutesSteps() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val (db, repo, vm) = createEnv()
        try {
            val tabId = vm.uiState.value.activeTabId
            val currentUrl = "https://news.example.com/feed"
            val targetUrl = "https://shop.example.com/checkout"

            vm.submitNavigation(currentUrl, tabId)
            val webView = RedirectCapableWebView(context, currentUrl)
            vm.registerWebView(tabId, webView)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            vm.onWebViewPageFinished(tabId, "News", currentUrl, canGoBack = false, canGoForward = false)

            // Open Workspace + Details overlays to simulate user clicking Play inside Details/Workspace
            vm.openAutomationHud()
            idleMainUntil { vm.uiState.value.isAutomationHudOpen }
            vm.openAutomationDetails(999L)
            idleMainUntil {
                vm.uiState.value.isAutomationWorkspaceOpen &&
                    vm.uiState.value.selectedAutomationWorkflowId == 999L
            }

            val wfId = repo.saveAutomationWorkflow(
                name = "Checkout Flow",
                type = AutomationType.RECORDED,
                domain = "shop.example.com",
                sourceUrl = targetUrl,
                steps = listOf(
                    AutomationStep(
                        id = "s_click",
                        order = 1,
                        actionType = AutomationActionType.CLICK,
                        targetLabel = "Place Order",
                        selector = "#place-order",
                        description = "Click Place Order"
                    )
                ),
                instructionsText = ""
            )

            vm.runWorkflow(wfId)

            // Overlays must dismiss and active tab must redirect to targetUrl before step 1 runs
            idleMainUntil {
                !vm.uiState.value.isAutomationHudOpen &&
                    !vm.uiState.value.isAutomationWorkspaceOpen &&
                    vm.uiState.value.selectedAutomationWorkflowId == null &&
                    vm.uiState.value.activeTab.url == targetUrl
            }
            assertTrue(
                "No step interaction script should execute before redirected page finishes loading",
                webView.evaluatedScripts.none { it.contains("#place-order") }
            )

            // Simulate redirected WebView finishing navigation
            webView.fakeUrl = targetUrl
            vm.onWebViewPageFinished(tabId, "Checkout", targetUrl, canGoBack = true, canGoForward = false)

            idleMainUntil {
                vm.uiState.value.executionState?.status == "COMPLETED"
            }
            assertEquals(targetUrl, vm.uiState.value.activeTab.url)
            assertTrue(
                "Step selector must run after redirect settles",
                webView.evaluatedScripts.any { it.contains("#place-order") }
            )
            assertTrue(
                vm.uiState.value.executionState?.stepLogs.orEmpty().any { it.contains("Opening") }
            )
        } finally {
            db.close()
        }
    }

    @Test
    fun playAutomationFromStartPage_withoutInitialWebView_mountsWebViewOnRedirect_andCompletes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val (db, repo, vm) = createEnv()
        try {
            val tabId = vm.uiState.value.activeTabId
            assertEquals("apex://home", vm.uiState.value.activeTab.url)

            // Start Page has NO registered WebView yet
            val wfId = repo.saveAutomationWorkflow(
                name = "Domain Only Flow",
                type = AutomationType.RECORDED,
                domain = "shop.example.com",
                sourceUrl = "",
                steps = listOf(
                    AutomationStep(
                        id = "s_tap",
                        order = 1,
                        actionType = AutomationActionType.CLICK,
                        targetLabel = "Shop Now",
                        selector = "button.shop-now",
                        description = "Click Shop Now"
                    )
                ),
                instructionsText = ""
            )

            vm.openAutomationHud()
            vm.runWorkflow(wfId)

            // Should navigate from apex://home to https://shop.example.com instead of aborting on null WebView
            idleMainUntil {
                !vm.uiState.value.isAutomationHudOpen &&
                    vm.uiState.value.activeTab.url == "https://shop.example.com"
            }

            // Simulate WebViewViewport composing once tab leaves Start Page
            val mountedWebView = RedirectCapableWebView(context, "https://shop.example.com")
            vm.registerWebView(tabId, mountedWebView)
            vm.onWebViewPageFinished(tabId, "Shop", "https://shop.example.com", canGoBack = true, canGoForward = false)

            idleMainUntil {
                vm.uiState.value.executionState?.status == "COMPLETED"
            }
            assertTrue(mountedWebView.evaluatedScripts.any { it.contains("button.shop-now") })
        } finally {
            db.close()
        }
    }

    @Test
    fun playCredentialWorkflowFromAnotherSite_authorizesAndRedirectsToWorkflowOrigin() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        val prefs = UserPreferencesRepository(context)
        val metadataStore = InMemoryVaultKeyMetadataStore()
        val keyProvider = InMemoryVaultKeyProvider(metadataStore = metadataStore)
        val crypto = CredentialVaultCrypto(keyProvider = keyProvider, metadataStore = metadataStore)
        val authManager = CredentialAuthenticationManager(context = context)
        val engine = AutomationEngine()
        val vaultManager = CredentialVaultManager(
            dao = db.apexDao(),
            crypto = crypto,
            authManager = authManager,
            originParser = { url -> engine.parseOrigin(url) }
        )
        val sp = context.getSharedPreferences("play_cred_redirect_${UUID.randomUUID()}", Context.MODE_PRIVATE)
        val repo = BrowserRepository(
            dao = db.apexDao(),
            preferencesRepository = prefs,
            sharedPrefs = sp,
            credentialVaultManager = vaultManager
        )
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

        try {
            val tabId = vm.uiState.value.activeTabId
            val otherUrl = "https://news.example.com"
            val loginUrl = "https://portal.example.com/login"

            vm.submitNavigation(otherUrl, tabId)
            val webView = RedirectCapableWebView(context, otherUrl)
            vm.registerWebView(tabId, webView)
            vm.onWebViewPageFinished(tabId, "News", otherUrl, canGoBack = false, canGoForward = false)

            vaultManager.unlockVault()
            val cred = (vaultManager.addCredential(
                websiteUrl = loginUrl,
                username = "alice@portal.example.com",
                passwordChars = "SecretPass!99".toCharArray(),
                label = "Portal Account"
            ) as CredentialVaultOperationResult.Success).value

            val wfId = repo.saveAutomationWorkflow(
                name = "Portal Login",
                type = AutomationType.RECORDED,
                domain = "portal.example.com",
                sourceUrl = loginUrl,
                steps = listOf(
                    AutomationStep(
                        id = "cred_pw",
                        order = 1,
                        actionType = AutomationActionType.FILL_CREDENTIAL,
                        targetLabel = "Password",
                        selector = "input#password",
                        value = "",
                        description = "Fill password",
                        credentialReferenceId = cred.credentialId,
                        credentialFieldRole = CredentialFieldRole.PASSWORD,
                        expectedOrigin = "https://portal.example.com:443",
                        requiresCredential = true
                    )
                ),
                instructionsText = ""
            )

            vm.setAutomationWorkspaceOpen(true)
            vm.runWorkflow(wfId)

            // Should prompt credential authorization for portal.example.com even though tab is currently on news.example.com
            idleMainUntil {
                vm.uiState.value.executionState?.status == "AWAITING_CREDENTIAL_AUTH" &&
                    vm.uiState.value.pendingCredentialReplayAuth != null
            }
            assertEquals("https://portal.example.com", vm.uiState.value.pendingCredentialReplayAuth?.displayOrigin)

            vm.confirmPendingCredentialReplay()
            idleMainUntil {
                !vm.uiState.value.isAutomationWorkspaceOpen &&
                    vm.uiState.value.activeTab.url == loginUrl
            }

            webView.fakeUrl = loginUrl
            vm.onWebViewPageFinished(tabId, "Portal Login", loginUrl, canGoBack = true, canGoForward = false)

            idleMainUntil {
                vm.uiState.value.executionState?.status == "COMPLETED"
            }
            assertTrue(webView.evaluatedScripts.any { it.contains("CREDENTIAL_FIELD_INVALID") })
        } finally {
            store.clear()
            db.close()
        }
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    @Test
    fun browserScreen_showsFloatingLiveExecutionBar_andControlsDismissOnCompletion() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val (db, repo, vm) = createEnv()
        try {
            val tabId = vm.uiState.value.activeTabId
            val targetUrl = "https://shop.example.com/checkout"
            vm.submitNavigation(targetUrl, tabId)
            val webView = RedirectCapableWebView(context, targetUrl)
            vm.registerWebView(tabId, webView)
            vm.onWebViewPageFinished(tabId, "Checkout", targetUrl, canGoBack = false, canGoForward = false)

            val wfId = repo.saveAutomationWorkflow(
                name = "Quick Checkout",
                type = AutomationType.RECORDED,
                domain = "shop.example.com",
                sourceUrl = targetUrl,
                steps = listOf(
                    AutomationStep(
                        id = "s1",
                        order = 1,
                        actionType = AutomationActionType.CLICK,
                        targetLabel = "Buy",
                        selector = "#buy",
                        description = "Click Buy"
                    )
                ),
                instructionsText = ""
            )

            val windowSizeClass = WindowSizeClass.calculateFromSize(DpSize(412.dp, 915.dp))
            composeTestRule.setContent {
                ApexBrowserTheme {
                    BrowserScreen(
                        viewModel = vm,
                        windowSizeClass = windowSizeClass
                    )
                }
            }
            composeTestRule.waitForIdle()

            // Register RedirectCapableWebView after WebViewViewport's factory mounts
            vm.registerWebView(tabId, webView)
            vm.onWebViewPageFinished(tabId, "Checkout", targetUrl, canGoBack = false, canGoForward = false)

            vm.runWorkflow(wfId)
            idleMainUntil {
                vm.uiState.value.executionState?.status == "COMPLETED"
            }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("browser_live_execution_bar").assertIsDisplayed()
            composeTestRule.onNodeWithText("Quick Checkout", useUnmergedTree = true).assertIsDisplayed()
            composeTestRule.onNodeWithTag("browser_exec_dismiss_button").assertIsDisplayed().performClick()
            composeTestRule.waitForIdle()
            assertNull(vm.uiState.value.executionState)
        } finally {
            db.close()
        }
    }
}
