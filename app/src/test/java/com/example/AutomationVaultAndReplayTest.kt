package com.example

import android.content.Context
import android.os.Looper
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.width
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.automation.ActiveRecordingSession
import com.example.automation.AutomationActionType
import com.example.automation.AutomationEngine
import com.example.automation.AutomationHudMode
import com.example.automation.AutomationStep
import com.example.automation.AutomationStepCodec
import com.example.automation.AutomationType
import com.example.automation.AutomationVaultCategory
import com.example.automation.AutomationVaultManager
import com.example.automation.PageInspectionState
import com.example.automation.Phase6ExecutionCoordinator
import com.example.automation.RecordedDomEvent
import com.example.automation.RecordingStatus
import com.example.automation.RecordingVaultCandidate
import com.example.automation.TabNavigationSynchronizer
import com.example.automation.VaultFileImportCandidate
import com.example.automation.VaultFolderFilePayload
import com.example.automation.WorkflowDocument
import com.example.automation.WorkflowExecutionState
import com.example.data.local.ApexDatabase
import com.example.data.local.AutomationVaultEntryEntity
import com.example.data.local.AutomationVaultFileEntity
import com.example.data.local.AutomationVaultFolderEntity
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.BrowserRepository
import com.example.engine.FastDownloadEngine
import com.example.ui.browser.AutomationHudSheet
import com.example.ui.browser.AutomationVaultScreenOverlay
import com.example.ui.browser.UploadVaultFileDialog
import com.example.ui.theme.ApexBrowserTheme
import com.example.ui.viewmodel.BrowserViewModel
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AutomationVaultAndReplayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class FakeWebView(
        context: Context,
        var fakeUrl: String = "https://shop.example.com/signup",
        private val evaluator: (script: String, callback: ValueCallback<String>?) -> Unit
    ) : WebView(context) {
        val evaluatedScripts = mutableListOf<String>()

        override fun getUrl(): String = fakeUrl

        override fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {
            evaluatedScripts.add(script)
            evaluator(script, resultCallback)
        }
    }

    private class Harness(
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

    private fun createHarness(
        engine: AutomationEngine = AutomationEngine(
            jsEvalTimeoutMs = 1_500L,
            navTimeoutMs = 1_500L,
            domReadyTimeoutMs = 450L,
            domStabilityPollMs = 20L
        )
    ): Harness {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        val prefs = UserPreferencesRepository(context)
        val sharedPrefs = context.getSharedPreferences(
            "automation_vault_replay_${UUID.randomUUID()}",
            Context.MODE_PRIVATE
        )
        val repo = BrowserRepository(db.apexDao(), prefs, sharedPrefs)
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
        return Harness(db = db, repo = repo, vm = vm, context = context, store = store)
    }

    private fun idleMainUntil(timeoutMs: Long = 4_000L, predicate: () -> Boolean) {
        val mainLooper = Shadows.shadowOf(Looper.getMainLooper())
        val deadline = System.currentTimeMillis() + timeoutMs
        mainLooper.idle()
        while (!predicate() && System.currentTimeMillis() < deadline) {
            mainLooper.idleFor(20L, TimeUnit.MILLISECONDS)
        }
        mainLooper.idle()
        assertTrue("Timed out waiting for condition", predicate())
    }

    @Test
    fun firstRecording_grabsSelectedValuesIntoAutomationVault_whileKeepingStepPasswordEmpty() {
        val h = createHarness()
        try {
            val tabId = h.vm.uiState.value.activeTabId
            val targetUrl = "https://accounts.example.com/register"
            h.vm.onWebViewPageFinished(tabId, "Sign Up", targetUrl, canGoBack = false, canGoForward = false)

            val fakeWv = FakeWebView(h.context, fakeUrl = targetUrl) { script, cb ->
                val sidMatch = Regex("""sessionId:\s*("(?:[^"\\]|\\.)*")""").find(script)
                val sid = sidMatch?.groupValues?.getOrNull(1)?.let { JSONObject("{\"v\":$it}").optString("v") } ?: ""
                val payload = if (script.contains("__apexRecorderInstance = inst")) {
                    JSONObject().put("ok", true).put("sessionId", sid).toString()
                } else {
                    JSONObject().put("ok", true).toString()
                }
                cb?.onReceiveValue(JSONObject.quote(payload))
            }
            h.vm.registerWebView(tabId, fakeWv)

            h.vm.startRecording()
            idleMainUntil { h.vm.uiState.value.recordingSession.status == RecordingStatus.RECORDING }
            val session = h.vm.uiState.value.recordingSession

            // 1) Email input
            h.vm.onRecordedDomEvent(
                RecordedDomEvent(
                    sessionId = session.sessionId,
                    tabId = tabId,
                    origin = "https://accounts.example.com",
                    sequenceNumber = 1L,
                    eventType = "INPUT",
                    targetLabel = "Email Address",
                    selector = "input[type=\"email\"][name=\"email\"]",
                    value = "alex@example.com",
                    isDestructive = false
                ),
                tabId,
                fakeWv,
                targetUrl
            )
            // 2) Phone input (user will later uncheck this one)
            h.vm.onRecordedDomEvent(
                RecordedDomEvent(
                    sessionId = session.sessionId,
                    tabId = tabId,
                    origin = "https://accounts.example.com",
                    sequenceNumber = 2L,
                    eventType = "INPUT",
                    targetLabel = "Phone Number",
                    selector = "input[type=\"tel\"][name=\"phone\"]",
                    value = "+15551234567",
                    isDestructive = false
                ),
                tabId,
                fakeWv,
                targetUrl
            )
            // 3) Password input: step.value must stay empty while sensitiveCapturedValue feeds Automation Vault
            h.vm.onRecordedDomEvent(
                RecordedDomEvent(
                    sessionId = session.sessionId,
                    tabId = tabId,
                    origin = "https://accounts.example.com",
                    sequenceNumber = 3L,
                    eventType = "INPUT",
                    targetLabel = "Password",
                    selector = "input[type=\"password\"]",
                    value = "",
                    isDestructive = false,
                    isPasswordField = true,
                    fieldRoleHint = "PASSWORD",
                    sensitiveCapturedValue = "SuperSecret!99"
                ),
                tabId,
                fakeWv,
                targetUrl
            )
            idleMainUntil { h.vm.uiState.value.recordingSession.steps.size == 3 }

            val passwordStepBeforeStop = h.vm.uiState.value.recordingSession.steps[2]
            assertEquals("", passwordStepBeforeStop.value)

            // Stop recording -> grabbed candidates appear for user selection
            h.vm.stopRecording()
            idleMainUntil { h.vm.uiState.value.recordingVaultCandidates.size == 3 }

            val candidates = h.vm.uiState.value.recordingVaultCandidates
            assertEquals(AutomationVaultCategory.EMAIL, candidates[0].category)
            assertEquals("alex@example.com", candidates[0].capturedValue)
            assertTrue(candidates[0].isSelected)

            assertEquals(AutomationVaultCategory.PHONE, candidates[1].category)
            assertEquals("+15551234567", candidates[1].capturedValue)
            assertTrue(candidates[1].isSelected)

            assertEquals(AutomationVaultCategory.PASSWORD, candidates[2].category)
            assertEquals("SuperSecret!99", candidates[2].capturedValue)
            assertTrue(candidates[2].hasSecretCapture)
            assertTrue(candidates[2].isSelected)

            // User deselects Phone (kept literal) and updates Email label
            h.vm.toggleRecordingVaultCandidate(candidates[1].stepId)
            h.vm.updateRecordingVaultCandidate(
                stepId = candidates[0].stepId,
                category = AutomationVaultCategory.EMAIL,
                label = "Primary Account Email",
                value = "alex@example.com"
            )

            h.vm.saveRecordingWorkflow("Account Signup")
            idleMainUntil { h.vm.uiState.value.recordingSession.status == RecordingStatus.IDLE }

            runBlocking {
                val savedWorkflow = h.db.apexDao().observeAutomationWorkflows().first().first()
                assertFalse("Password must never be stored plaintext in workflow JSON", savedWorkflow.stepsJson.contains("SuperSecret!99"))
                val savedSteps = AutomationStepCodec.decode(savedWorkflow.stepsJson)
                assertEquals(3, savedSteps.size)

                // Selected Email & Password steps are bound to Automation Vault
                assertNotNull(savedSteps[0].vaultEntryId)
                assertEquals("EMAIL", savedSteps[0].vaultCategory)
                assertEquals("", savedSteps[0].value)

                // Unselected Phone step stays literal
                assertEquals(null, savedSteps[1].vaultEntryId)
                assertEquals("+15551234567", savedSteps[1].value)

                // Password step is bound to Automation Vault (no Credential Vault block needed)
                assertNotNull(savedSteps[2].vaultEntryId)
                assertEquals("PASSWORD", savedSteps[2].vaultCategory)
                assertEquals("", savedSteps[2].value)
                assertFalse(savedSteps[2].requiresCredential)

                val vaultEntries = h.db.apexDao().getAllAutomationVaultEntriesOnce()
                assertEquals(2, vaultEntries.size)
                assertEquals("alex@example.com", h.repo.automationVaultManager.decryptEntryValue(savedSteps[0].vaultEntryId!!).getOrThrow())
                assertEquals("SuperSecret!99", h.repo.automationVaultManager.decryptEntryValue(savedSteps[2].vaultEntryId!!).getOrThrow())
            }
        } finally {
            h.close()
        }
    }

    @Test
    fun unlimitedReplay_readsUpdatedValuesFromAutomationVault_onEveryReplay() {
        val h = createHarness()
        try {
            val targetUrl = "https://portal.example.com/login"
            val tabId = h.vm.uiState.value.activeTabId
            h.vm.onWebViewPageFinished(tabId, "Portal", targetUrl, canGoBack = false, canGoForward = false)

            val injectedScripts = mutableListOf<String>()
            val fakeWv = FakeWebView(h.context, fakeUrl = targetUrl) { script, cb ->
                injectedScripts.add(script)
                val reply = if (script.contains("readyState")) {
                    JSONObject()
                        .put("readyState", "complete")
                        .put("url", targetUrl)
                        .put("shape", "2:24")
                        .put("targetReady", true)
                        .toString()
                } else {
                    JSONObject()
                        .put("ok", true)
                        .put("msg", "Filled input")
                        .put("resolvedSelector", "input[name=\"email\"]")
                        .put("verified", true)
                        .toString()
                }
                cb?.onReceiveValue(JSONObject.quote(reply))
            }
            h.vm.registerWebView(tabId, fakeWv)

            val (emailEntryId, passEntryId, workflowId) = runBlocking {
                val emailEntry = h.repo.automationVaultManager.saveOrUpdateEntry(
                    category = AutomationVaultCategory.EMAIL,
                    label = "Work Email",
                    plainValue = "user1@example.com",
                    websiteOrigin = "https://portal.example.com",
                    isSensitive = false
                ).getOrThrow()
                val passEntry = h.repo.automationVaultManager.saveOrUpdateEntry(
                    category = AutomationVaultCategory.PASSWORD,
                    label = "Work Password",
                    plainValue = "FirstPass111",
                    websiteOrigin = "https://portal.example.com",
                    isSensitive = true
                ).getOrThrow()

                val wfId = h.repo.saveAutomationWorkflow(
                    name = "Portal Sign In",
                    domain = "portal.example.com",
                    sourceUrl = targetUrl,
                    type = AutomationType.RECORDED,
                    steps = listOf(
                        AutomationStep(
                            id = "s1",
                            order = 1,
                            actionType = AutomationActionType.INPUT,
                            targetLabel = "Email",
                            selector = "input[name=\"email\"]",
                            value = "",
                            description = "Enter Email",
                            vaultEntryId = emailEntry.entryId,
                            vaultCategory = AutomationVaultCategory.EMAIL.id
                        ),
                        AutomationStep(
                            id = "s2",
                            order = 2,
                            actionType = AutomationActionType.INPUT,
                            targetLabel = "Password",
                            selector = "input[type=\"password\"]",
                            value = "",
                            description = "Enter Password",
                            isDestructive = false,
                            vaultEntryId = passEntry.entryId,
                            vaultCategory = AutomationVaultCategory.PASSWORD.id
                        )
                    )
                )
                Triple(emailEntry.entryId, passEntry.entryId, wfId)
            }

            // Replay #1
            h.vm.replayWorkflow(workflowId)
            idleMainUntil { h.vm.uiState.value.executionState?.status == "COMPLETED" }
            assertTrue(injectedScripts.any { it.contains("user1@example.com") })
            assertTrue(injectedScripts.any { it.contains("FirstPass111") })

            // Update values in Automation Vault WITHOUT re-recording workflow
            injectedScripts.clear()
            runBlocking {
                h.repo.automationVaultManager.saveOrUpdateEntry(
                    entryId = emailEntryId,
                    category = AutomationVaultCategory.EMAIL,
                    label = "Work Email",
                    plainValue = "rotated@example.com",
                    websiteOrigin = "https://portal.example.com",
                    isSensitive = false
                ).getOrThrow()
                h.repo.automationVaultManager.saveOrUpdateEntry(
                    entryId = passEntryId,
                    category = AutomationVaultCategory.PASSWORD,
                    label = "Work Password",
                    plainValue = "SecondPass222!",
                    websiteOrigin = "https://portal.example.com",
                    isSensitive = true
                ).getOrThrow()
            }

            // Replay #2 picks up updated Automation Vault values automatically
            h.vm.replayWorkflow(workflowId)
            idleMainUntil { h.vm.uiState.value.executionState?.status == "COMPLETED" }
            assertTrue(injectedScripts.any { it.contains("rotated@example.com") })
            assertTrue(injectedScripts.any { it.contains("SecondPass222!") })
            assertFalse(injectedScripts.any { it.contains("user1@example.com") })
            assertFalse(injectedScripts.any { it.contains("FirstPass111") })

            // Replay #3 works cleanly right after Replay #2 (unlimited replays)
            injectedScripts.clear()
            h.vm.replayWorkflow(workflowId)
            idleMainUntil { h.vm.uiState.value.executionState?.status == "COMPLETED" }
            assertTrue(injectedScripts.any { it.contains("rotated@example.com") })
            runBlocking {
                val refreshed = h.repo.automationVaultManager.getEntryById(emailEntryId)!!
                assertNotNull("Expected lastUsedAt to be recorded on replay", refreshed.lastUsedAt)
            }
        } finally {
            h.close()
        }
    }

    @Test
    fun workflowStart_waitsForWebsiteLoadCompletionBeforeRunningStep1() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = AutomationEngine(
            jsEvalTimeoutMs = 1_500L,
            navTimeoutMs = 2_000L,
            domReadyTimeoutMs = 1_500L,
            domStabilityPollMs = 20L
        )
        val coordinator = Phase6ExecutionCoordinator(engine)
        val navSync = TabNavigationSynchronizer()
        val tabId = "tab_wait_load"
        val url = "https://slow.example.com/checkout"

        // Tab URL matches workflow URL, but website is still actively LOADING
        navSync.onPageStarted(tabId, url)

        var stepExecutedBeforeLoadFinished = false
        val wv = FakeWebView(context, fakeUrl = url) { script, cb ->
            if (script.contains("readyState")) {
                val ready = navSync.snapshot(tabId).phase.name == "FINISHED"
                val reply = JSONObject()
                    .put("readyState", if (ready) "complete" else "loading")
                    .put("url", url)
                    .put("shape", if (ready) "4:120" else "0:0")
                    .put("targetReady", ready)
                    .toString()
                cb?.onReceiveValue(JSONObject.quote(reply))
            } else {
                if (navSync.snapshot(tabId).phase.name != "FINISHED") {
                    stepExecutedBeforeLoadFinished = true
                }
                val reply = JSONObject()
                    .put("ok", true)
                    .put("msg", "Clicked Continue")
                    .put("resolvedSelector", "#continue-btn")
                    .put("verified", true)
                    .toString()
                cb?.onReceiveValue(JSONObject.quote(reply))
            }
        }

        val steps = listOf(
            AutomationStep(
                id = "step_1",
                order = 1,
                actionType = AutomationActionType.CLICK,
                targetLabel = "Continue",
                selector = "#continue-btn",
                value = "",
                description = "Click Continue"
            )
        )
        val workflow = com.example.data.local.AutomationWorkflowEntity(
            id = 42L,
            name = "Checkout Flow",
            domain = "slow.example.com",
            sourceUrl = url,
            type = AutomationType.RECORDED.id,
            instructionsText = "",
            stepsJson = AutomationStepCodec.encode(steps),
            stepCount = 1,
            createdAt = 1L,
            updatedAt = 1L
        )

        val progressLogs = mutableListOf<String>()
        val deferred = async {
            coordinator.executeDocumentWorkflow(
                workflow = workflow,
                document = WorkflowDocument(steps = steps),
                currentTabUrl = url,
                webViewProvider = { wv },
                onNavigateUrl = {},
                onStepProgress = { _, msg -> progressLogs.add(msg) },
                tabId = tabId,
                navigationSynchronizer = navSync
            )
        }

        delay(120L)
        assertFalse("Step 1 must wait while website is still loading", stepExecutedBeforeLoadFinished)
        navSync.onPageFinished(tabId, url)

        val outcome = deferred.await()
        assertEquals("COMPLETED", outcome.status)
        assertFalse("Step 1 must never execute before page load finished", stepExecutedBeforeLoadFinished)
        assertTrue(progressLogs.any { it.contains("Waiting for website", ignoreCase = true) })
    }

    @Test
    fun automationHudAndVaultOverlay_exposeVaultQuickActionAndRecordingCandidates() {
        var hudMode by mutableStateOf(AutomationHudMode.RECORDING)
        var openedVault by mutableStateOf(false)
        var toggledStepId: String? = null
        var candidates by mutableStateOf(
            listOf(
                RecordingVaultCandidate(
                    stepId = "rec_1",
                    stepOrder = 1,
                    targetLabel = "Phone",
                    selector = "input[type=\"tel\"]",
                    category = AutomationVaultCategory.PHONE,
                    vaultLabel = "Phone (shop.example.com)",
                    capturedValue = "555-0199",
                    isSensitive = false,
                    isSelected = true
                )
            )
        )

        composeTestRule.setContent {
            ApexBrowserTheme {
                if (openedVault) {
                    AutomationVaultScreenOverlay(
                        entries = emptyList(),
                        revealedValues = emptyMap(),
                        activeTabUrl = "https://shop.example.com",
                        onSaveEntry = { _, _, _, _, _, _ -> },
                        onDeleteEntry = {},
                        onRevealEntry = {},
                        onHideEntry = {},
                        onDismiss = { openedVault = false }
                    )
                } else {
                    AutomationHudSheet(
                        hudMode = hudMode,
                        pageInspection = PageInspectionState(domain = "shop.example.com", url = "https://shop.example.com"),
                        isHomePage = false,
                        recordingSession = ActiveRecordingSession(
                            status = RecordingStatus.STOPPED,
                            targetDomain = "shop.example.com",
                            targetUrl = "https://shop.example.com",
                            steps = listOf(
                                AutomationStep(
                                    id = "rec_1",
                                    order = 1,
                                    actionType = AutomationActionType.INPUT,
                                    targetLabel = "Phone",
                                    selector = "input[type=\"tel\"]",
                                    value = "555-0199",
                                    description = "Enter Phone"
                                )
                            )
                        ),
                        instructionDraftText = "",
                        instructionDraftSteps = emptyList(),
                        executionState = WorkflowExecutionState(
                            workflowId = 7L,
                            workflowName = "Checkout",
                            status = "COMPLETED",
                            currentStepIndex = 1,
                            totalSteps = 1
                        ),
                        workflows = emptyList(),
                        executions = emptyList(),
                        recordingVaultCandidates = candidates,
                        automationVaultCount = 2,
                        onSelectMode = { hudMode = it },
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
                        onOpenAutomationVault = { openedVault = true },
                        onToggleRecordingVaultCandidate = { id ->
                            toggledStepId = id
                            candidates = candidates.map {
                                if (it.stepId == id) it.copy(isSelected = !it.isSelected) else it
                            }
                        },
                        onDismiss = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("recording_vault_candidate_1").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("recording_vault_check_1").performScrollTo().performClick()
        assertEquals("rec_1", toggledStepId)

        hudMode = AutomationHudMode.OVERVIEW
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("hud_quick_vault").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("automation_vault_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("automation_vault_back_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("hud_quick_vault").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun vaultFileUpload_parsesCsvJsonAndKeyValueFiles_andImportsOnlyUserSelectedEntries() = runBlocking {
        val h = createHarness()
        try {
            val csvText = """
                name,url,username,password
                GitHub,https://github.com/login,dev@example.com,GitSecret!99
                Shop,https://shop.example.com,shopper1,ShopPass!22
            """.trimIndent()
            val csvCandidates = AutomationVaultManager.parseImportContent(csvText)
            assertEquals(4, csvCandidates.size)
            assertTrue(csvCandidates.any { it.category == AutomationVaultCategory.EMAIL && it.value == "dev@example.com" })
            assertTrue(csvCandidates.any { it.category == AutomationVaultCategory.PASSWORD && it.value == "GitSecret!99" && it.isSensitive })
            assertTrue(csvCandidates.any { it.category == AutomationVaultCategory.USERNAME && it.value == "shopper1" })

            val kvText = """
                # Personal profile values
                phone: +1 555-010-2020
                address = 742 Evergreen Terrace
                portal_password: Portal!Secret7
            """.trimIndent()
            val kvCandidates = AutomationVaultManager.parseImportContent(kvText)
            assertEquals(3, kvCandidates.size)
            assertTrue(kvCandidates.any { it.category == AutomationVaultCategory.PHONE && it.value == "+1 555-010-2020" })
            assertTrue(kvCandidates.any { it.category == AutomationVaultCategory.ADDRESS && it.value == "742 Evergreen Terrace" })
            assertTrue(kvCandidates.any { it.category == AutomationVaultCategory.PASSWORD && it.value == "Portal!Secret7" })

            // User deselects the address line and imports only phone + password
            val userSelection = kvCandidates.map { item ->
                if (item.category == AutomationVaultCategory.ADDRESS) item.copy(isSelected = false) else item
            }
            h.vm.importAutomationVaultCandidates(userSelection)
            idleMainUntil { runBlocking { h.repo.automationVaultManager.getAllEntriesOnce().size == 2 } }

            val stored = h.repo.automationVaultManager.getAllEntriesOnce()
            assertEquals(2, stored.size)
            assertFalse(stored.any { it.category == AutomationVaultCategory.ADDRESS.id })
            val passEntry = stored.first { it.category == AutomationVaultCategory.PASSWORD.id }
            assertEquals("Portal!Secret7", h.repo.automationVaultManager.decryptEntryValue(passEntry.entryId).getOrThrow())
        } finally {
            h.close()
        }
    }

    @Test
    fun automationVaultScreen_singleLineSearchBar_andUploadFileDialogSelectionFlow() {
        var importedSelection by mutableStateOf<List<VaultFileImportCandidate>>(emptyList())
        var showUploadDialog by mutableStateOf(false)

        composeTestRule.setContent {
            ApexBrowserTheme {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.width(360.dp)
                ) {
                    AutomationVaultScreenOverlay(
                        entries = emptyList(),
                        revealedValues = emptyMap(),
                        activeTabUrl = "https://portal.example.com",
                        onSaveEntry = { _, _, _, _, _, _ -> },
                        onImportFileCandidates = { candidates ->
                            importedSelection = candidates.filter { it.isSelected }
                        },
                        onDeleteEntry = {},
                        onRevealEntry = {},
                        onHideEntry = {},
                        onDismiss = {}
                    )
                    if (showUploadDialog) {
                        UploadVaultFileDialog(
                            existingEntries = emptyList(),
                            defaultOriginUrl = "https://portal.example.com",
                            onImportCandidates = { candidates ->
                                importedSelection = candidates.filter { it.isSelected }
                                showUploadDialog = false
                            },
                            onDismiss = { showUploadDialog = false }
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        // Top app bar must NOT have upload/add/new-folder icons
        composeTestRule.onNodeWithTag("automation_vault_upload_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("automation_vault_add_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("automation_vault_top_new_folder_button").assertDoesNotExist()

        // Search bar must stay single-line height even at compact 360.dp phone width
        val searchBounds = composeTestRule.onNodeWithTag("automation_vault_search_input")
            .assertIsDisplayed()
            .getBoundsInRoot()
        val searchHeight = searchBounds.bottom - searchBounds.top
        assertTrue(
            "Expected single-line search bar height <= 68.dp, was $searchHeight",
            searchHeight <= 68.dp
        )

        // Open Upload File extraction dialog and paste multi-entry content
        showUploadDialog = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("vault_upload_choose_file_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("vault_upload_raw_input").performTextInput(
            "email: alice@example.com\nphone: +15550199\npassword: MySecret!99"
        )
        composeTestRule.waitForIdle()

        // All 3 parsed entries appear; deselect the 2nd item (phone) before confirming
        composeTestRule.onNodeWithTag("vault_upload_item_0").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("vault_upload_item_1").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("vault_upload_item_2").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("vault_upload_item_checkbox_1").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("vault_upload_selection_summary").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("2 of 3 selected", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("vault_upload_confirm_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(2, importedSelection.size)
        assertTrue(importedSelection.any { it.category == AutomationVaultCategory.EMAIL && it.value == "alice@example.com" })
        assertTrue(importedSelection.any { it.category == AutomationVaultCategory.PASSWORD && it.value == "MySecret!99" })
        assertFalse(importedSelection.any { it.category == AutomationVaultCategory.PHONE })
    }

    @Test
    fun vaultFolders_createCustomFolder_uploadArbitraryFilesAndScopeEntries() = runBlocking {
        val h = createHarness()
        val tempRoot = File(h.context.cacheDir, "vault_test_${UUID.randomUUID()}").apply { mkdirs() }
        try {
            // Default folders always exist
            val initialFolders = h.repo.automationVaultManager.getAllFoldersOnce()
            assertTrue(initialFolders.any { it.folderId == AutomationVaultManager.FOLDER_ID_PASSWORDS && it.name == "Passwords" })
            assertTrue(initialFolders.any { it.folderId == AutomationVaultManager.FOLDER_ID_EMAILS && it.name == "Emails" })

            // Create custom folder via ViewModel
            h.vm.createAutomationVaultFolder("Work Docs")
            idleMainUntil {
                h.vm.automationVaultFoldersState.value.any { it.name == "Work Docs" } ||
                    runBlocking { h.repo.automationVaultManager.getAllFoldersOnce().any { it.name == "Work Docs" } }
            }
            val createdFolder = h.repo.automationVaultManager.getAllFoldersOnce().first { it.name == "Work Docs" }
            assertFalse(createdFolder.isDefault)

            // Upload both a PDF binary file and a CSV text file into the folder
            val pdfBytes = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n".toByteArray()
            val savedPdf = h.repo.automationVaultManager.saveUploadedFileToFolder(
                folderId = createdFolder.folderId,
                fileName = "onboarding.pdf",
                mimeType = "application/pdf",
                bytes = pdfBytes,
                filesRootDir = tempRoot
            ).getOrThrow()
            assertTrue(File(savedPdf.localFilePath).exists())
            assertEquals("", savedPdf.textPreview)

            val csvBytes = "email,password\nwork@example.com,WorkPass!1\n".toByteArray()
            val savedCsv = h.repo.automationVaultManager.saveUploadedFileToFolder(
                folderId = createdFolder.folderId,
                fileName = "accounts.csv",
                mimeType = "text/csv",
                bytes = csvBytes,
                filesRootDir = tempRoot
            ).getOrThrow()
            assertTrue(savedCsv.textPreview.contains("work@example.com"))

            // Save a vault entry scoped into the custom folder
            val scopedEntry = h.repo.automationVaultManager.saveOrUpdateEntry(
                category = AutomationVaultCategory.EMAIL,
                label = "Work Email",
                plainValue = "work@example.com",
                folderId = createdFolder.folderId
            ).getOrThrow()
            assertEquals(createdFolder.folderId, scopedEntry.folderId)

            // Direct directory upload creates a folder with all uploaded files
            val dirResult = h.repo.automationVaultManager.uploadDirectoryFolder(
                folderName = "Tax Bundle",
                files = listOf(
                    VaultFolderFilePayload("summary.txt", "text/plain", "tax: 2026".toByteArray()),
                    VaultFolderFilePayload("receipt.png", "image/png", byteArrayOf(1, 2, 3, 4))
                ),
                filesRootDir = tempRoot
            ).getOrThrow()
            assertEquals("Tax Bundle", dirResult.folder.name)
            assertEquals(2, dirResult.uploadedFiles.size)
            assertTrue(dirResult.uploadedFiles.all { File(it.localFilePath).exists() })

            // Rename custom folder
            val renamed = h.repo.automationVaultManager.renameFolder(createdFolder.folderId, "Office Files").getOrThrow()
            assertEquals("Office Files", renamed.name)

            // Deleting custom folder cleans up DB records and disk files
            h.repo.automationVaultManager.deleteFolder(createdFolder.folderId).getOrThrow()
            assertFalse(File(savedPdf.localFilePath).exists())
            assertFalse(File(savedCsv.localFilePath).exists())
            assertEquals(null, h.repo.automationVaultManager.getEntryById(scopedEntry.entryId))
        } finally {
            tempRoot.deleteRecursively()
            h.close()
        }
    }

    @Test
    fun automationVaultOverlay_showsFoldersInsteadOfChips_allowsFolderCreation_andShowsFilesInsideFolder() {
        var folders by mutableStateOf(AutomationVaultManager.DEFAULT_FOLDERS)
        var files by mutableStateOf(
            listOf(
                AutomationVaultFileEntity(
                    fileId = "file_1",
                    folderId = AutomationVaultManager.FOLDER_ID_PASSWORDS,
                    fileName = "backup_codes.txt",
                    mimeType = "text/plain",
                    sizeBytes = 128L,
                    localFilePath = "/tmp/backup_codes.txt",
                    textPreview = "recovery: 1234-5678",
                    createdAt = 100L
                )
            )
        )
        var entries by mutableStateOf(
            listOf(
                AutomationVaultEntryEntity(
                    entryId = "entry_pwd_1",
                    category = AutomationVaultCategory.PASSWORD.id,
                    label = "GitHub Password",
                    websiteOrigin = "https://github.com",
                    domain = "github.com",
                    encryptedValue = "enc",
                    maskedPreview = "••••••••",
                    isSensitive = true,
                    folderId = AutomationVaultManager.FOLDER_ID_PASSWORDS
                )
            )
        )

        var savedCategory by mutableStateOf<AutomationVaultCategory?>(null)
        var savedLabel by mutableStateOf<String?>(null)
        var savedValue by mutableStateOf<String?>(null)
        var savedFolderId by mutableStateOf<String?>(null)

        var editedEntryId by mutableStateOf<String?>(null)
        var deletedEntryId by mutableStateOf<String?>(null)

        composeTestRule.setContent {
            ApexBrowserTheme {
                AutomationVaultScreenOverlay(
                    entries = entries,
                    revealedValues = mapOf("entry_pwd_1" to "VisiblePass!99"),
                    activeTabUrl = "https://github.com",
                    folders = folders,
                    files = files,
                    onSaveEntry = { _, _, _, _, _, _ -> },
                    onSaveEntryInFolder = { existingId, cat, lbl, value, _, _, folderId ->
                        editedEntryId = existingId
                        savedCategory = cat
                        savedLabel = lbl
                        savedValue = value
                        savedFolderId = folderId
                    },
                    onDeleteEntry = { deletedEntryId = it },
                    onRevealEntry = {},
                    onHideEntry = {},
                    onCreateFolder = { name ->
                        folders = folders + AutomationVaultFolderEntity(
                            folderId = "folder_custom_ui_1",
                            name = name,
                            isDefault = false
                        )
                    },
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Filter chips, top encryption banner, New Folder button, and root app bar add/upload icons must be removed
        composeTestRule.onNodeWithTag("vault_filter_all").assertDoesNotExist()
        composeTestRule.onNodeWithTag("vault_filter_password").assertDoesNotExist()
        composeTestRule.onNodeWithTag("vault_filter_email").assertDoesNotExist()
        composeTestRule.onNodeWithTag("automation_vault_upload_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("automation_vault_add_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("automation_vault_top_new_folder_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("automation_vault_new_folder_button").assertDoesNotExist()
        composeTestRule.onNodeWithText(
            "Encrypted with AES-256. Organize passwords, emails, values, and uploaded files in folders."
        ).assertDoesNotExist()

        // Default folders are visible with names below folder icons
        composeTestRule.onNodeWithTag("vault_folder_${AutomationVaultManager.FOLDER_ID_PASSWORDS}").assertIsDisplayed()
        composeTestRule.onNodeWithTag("vault_folder_name_${AutomationVaultManager.FOLDER_ID_PASSWORDS}", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Passwords").assertIsDisplayed()
        composeTestRule.onNodeWithTag("vault_folder_${AutomationVaultManager.FOLDER_ID_EMAILS}").assertIsDisplayed()
        composeTestRule.onNodeWithText("Emails").assertIsDisplayed()

        // Clicking FAB at root expands Add (create folder) & Upload (upload folder directly)
        composeTestRule.onNodeWithTag("automation_vault_fab").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("vault_fab_add_folder").assertIsDisplayed()
        composeTestRule.onNodeWithTag("vault_fab_upload_folder").assertIsDisplayed()

        // Tap Add from FAB to create a new custom folder
        composeTestRule.onNodeWithTag("vault_fab_add_folder").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("vault_folder_name_input").performTextInput("Travel Docs")
        composeTestRule.onNodeWithTag("vault_folder_save_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("automation_vault_list")
            .performScrollToNode(hasTestTag("vault_folder_folder_custom_ui_1"))
        composeTestRule.onNodeWithTag("vault_folder_folder_custom_ui_1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Travel Docs").assertIsDisplayed()

        // Open Passwords folder -> TopBar icons, banner, and inline Upload/Add buttons are removed; FAB shows Upload Files & Add Value
        composeTestRule.onNodeWithTag("automation_vault_list")
            .performScrollToNode(hasTestTag("vault_folder_${AutomationVaultManager.FOLDER_ID_PASSWORDS}"))
        composeTestRule.onNodeWithTag("vault_folder_${AutomationVaultManager.FOLDER_ID_PASSWORDS}").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(
            "Upload any document or add encrypted values inside 'Passwords'."
        ).assertDoesNotExist()
        composeTestRule.onNodeWithTag("vault_folder_rename_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("vault_folder_delete_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("automation_vault_upload_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("automation_vault_add_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("vault_folder_upload_file_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("vault_folder_add_value_button").assertDoesNotExist()

        composeTestRule.onNodeWithTag("automation_vault_list")
            .performScrollToNode(hasTestTag("vault_file_file_1"))
        composeTestRule.onNodeWithTag("vault_file_file_1").assertIsDisplayed()
        composeTestRule.onNodeWithText("backup_codes.txt").assertIsDisplayed()
        composeTestRule.onNodeWithTag("automation_vault_list")
            .performScrollToNode(hasTestTag("automation_vault_entry_entry_pwd_1"))
        composeTestRule.onNodeWithTag("automation_vault_entry_entry_pwd_1").assertIsDisplayed()

        // Password value must be displayed unmasked, with ONLY Delete icon on the right (no reveal/copy/edit icons)
        composeTestRule.onNodeWithText("VisiblePass!99", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("automation_vault_reveal_entry_pwd_1").assertDoesNotExist()
        composeTestRule.onNodeWithTag("automation_vault_copy_entry_pwd_1").assertDoesNotExist()
        composeTestRule.onNodeWithTag("automation_vault_edit_entry_pwd_1").assertDoesNotExist()
        composeTestRule.onNodeWithTag("automation_vault_delete_entry_pwd_1", useUnmergedTree = true).assertIsDisplayed()

        // Clicking the card edits the value directly
        composeTestRule.onNodeWithTag("automation_vault_entry_entry_pwd_1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("vault_entry_value_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("vault_entry_label_input").assertDoesNotExist()
        composeTestRule.onNodeWithTag("vault_entry_save_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals("entry_pwd_1", editedEntryId)
        assertEquals("VisiblePass!99", savedValue)

        // Clicking Delete icon deletes the entry
        composeTestRule.onNodeWithTag("automation_vault_delete_entry_pwd_1", useUnmergedTree = true).performClick()
        assertEquals("entry_pwd_1", deletedEntryId)

        // Clicking FAB inside folder shows Upload Files & Add Value tabs
        composeTestRule.onNodeWithTag("automation_vault_fab").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("vault_fab_upload_files").assertIsDisplayed()
        composeTestRule.onNodeWithTag("vault_fab_add_value").assertIsDisplayed()

        // Clicking Add Value inside Passwords asks ONLY for value (no Value Type chips, no Label input)
        composeTestRule.onNodeWithTag("vault_fab_add_value").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Value Type").assertDoesNotExist()
        composeTestRule.onNodeWithTag("vault_dialog_cat_password").assertDoesNotExist()
        composeTestRule.onNodeWithTag("vault_dialog_cat_email").assertDoesNotExist()
        composeTestRule.onNodeWithTag("vault_entry_label_input").assertDoesNotExist()
        composeTestRule.onNodeWithTag("vault_entry_value_input").assertIsDisplayed().performTextInput("FolderSecret!42")
        composeTestRule.onNodeWithTag("vault_entry_save_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(AutomationVaultCategory.PASSWORD, savedCategory)
        assertEquals("Passwords", savedLabel)
        assertEquals("FolderSecret!42", savedValue)
        assertEquals(AutomationVaultManager.FOLDER_ID_PASSWORDS, savedFolderId)

        // Return to Folders grid via TopAppBar back button
        composeTestRule.onNodeWithTag("automation_vault_back_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("vault_folder_${AutomationVaultManager.FOLDER_ID_EMAILS}").assertIsDisplayed()
    }

    @Test
    fun vaultValueGenerator_analyzesCustomFolderNames_andFolderGenerateButtonSavesMatchingValues() = runBlocking {
        val h = createHarness()
        try {
            // Verify custom folder name analysis on creation & rename in AutomationVaultManager
            val apiFolder = h.repo.automationVaultManager.createFolder("Work API Keys").getOrThrow()
            val emailFolder = h.repo.automationVaultManager.createFolder("Gmail Accounts").getOrThrow()
            assertEquals(AutomationVaultCategory.EMAIL.id, emailFolder.categoryId)

            val apiAnalysis = com.example.automation.VaultValueGenerator.analyzeFolder(apiFolder)
            assertEquals(com.example.automation.VaultGeneratorType.API_KEY, apiAnalysis.type)
            val (_, generatedApiKey) = com.example.automation.VaultValueGenerator.generateValue(apiFolder)
            assertTrue(generatedApiKey.startsWith("apx_") && generatedApiKey.length >= 24)

            val emailAnalysis = com.example.automation.VaultValueGenerator.analyzeFolder(emailFolder)
            assertEquals(com.example.automation.VaultGeneratorType.EMAIL, emailAnalysis.type)
            val (_, generatedGmail) = com.example.automation.VaultValueGenerator.generateValue(emailFolder)
            assertTrue("Expected @gmail.com email, got $generatedGmail", generatedGmail.endsWith("@gmail.com"))

            val pinFolder = AutomationVaultFolderEntity(folderId = "c_pin", name = "ATM 4 Digit PIN", isDefault = false)
            val (pinInfo, pinVal) = com.example.automation.VaultValueGenerator.generateValue(pinFolder)
            assertEquals(com.example.automation.VaultGeneratorType.PIN, pinInfo.type)
            assertTrue("Expected 4-digit PIN, got $pinVal", Regex("""^\d{4}$""").matches(pinVal))

            val empIdFolder = AutomationVaultFolderEntity(folderId = "c_emp", name = "Employee IDs", isDefault = false)
            val (empInfo, empVal) = com.example.automation.VaultValueGenerator.generateValue(empIdFolder)
            assertEquals(com.example.automation.VaultGeneratorType.CONTEXTUAL_CODE, empInfo.type)
            assertTrue("Expected prefixed contextual code, got $empVal", Regex("""^[A-Z]+-\d{6}$""").matches(empVal))

            val dobFolder = AutomationVaultFolderEntity(folderId = "c_dob", name = "Birthdays", isDefault = false)
            val (_, dobVal) = com.example.automation.VaultValueGenerator.generateValue(dobFolder)
            assertTrue("Expected YYYY-MM-DD date, got $dobVal", Regex("""^\d{4}-\d{2}-\d{2}$""").matches(dobVal))
        } finally {
            h.close()
        }
    }

    @Test
    fun automationVaultOverlay_insideFolders_generatesPasswordsAndCustomFolderAnalyzedValuesOnClick() {
        var folders by mutableStateOf(AutomationVaultManager.DEFAULT_FOLDERS)
        var savedEntries by mutableStateOf<List<AutomationVaultEntryEntity>>(emptyList())
        var revealed by mutableStateOf<Map<String, String>>(emptyMap())
        var lastSavedFolderId by mutableStateOf<String?>(null)
        var lastSavedCategory by mutableStateOf<AutomationVaultCategory?>(null)
        var lastSavedValue by mutableStateOf<String?>(null)

        composeTestRule.setContent {
            ApexBrowserTheme {
                AutomationVaultScreenOverlay(
                    entries = savedEntries,
                    revealedValues = revealed,
                    activeTabUrl = "https://example.com",
                    folders = folders,
                    onSaveEntry = { _, _, _, _, _, _ -> },
                    onSaveEntryInFolder = { _, cat, lbl, valStr, _, sensitive, folderId ->
                        val newId = "gen_entry_${savedEntries.size + 1}"
                        val newEntity = AutomationVaultEntryEntity(
                            entryId = newId,
                            category = cat.id,
                            label = lbl,
                            websiteOrigin = "",
                            domain = "Global",
                            encryptedValue = "enc",
                            maskedPreview = if (sensitive) "••••••••" else valStr,
                            isSensitive = sensitive,
                            folderId = folderId
                        )
                        savedEntries = savedEntries + newEntity
                        revealed = revealed + (newId to valStr)
                        lastSavedFolderId = folderId
                        lastSavedCategory = cat
                        lastSavedValue = valStr
                    },
                    onDeleteEntry = {},
                    onRevealEntry = {},
                    onHideEntry = {},
                    onCreateFolder = { name ->
                        val analyzed = com.example.automation.VaultValueGenerator.analyzeFolderName(name)
                        folders = folders + AutomationVaultFolderEntity(
                            folderId = "folder_custom_gen_1",
                            name = name,
                            categoryId = analyzed.category.id,
                            isDefault = false
                        )
                    },
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Open Passwords folder -> Generate button generates and saves a strong password
        composeTestRule.onNodeWithTag("vault_folder_${AutomationVaultManager.FOLDER_ID_PASSWORDS}").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("vault_folder_generator_type_label", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("vault_folder_generate_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        val genPass = lastSavedValue
        assertNotNull(genPass)
        assertTrue("Generated password should be >= 12 chars, was $genPass", genPass!!.length >= 12)
        assertEquals(AutomationVaultManager.FOLDER_ID_PASSWORDS, lastSavedFolderId)
        assertEquals(AutomationVaultCategory.PASSWORD, lastSavedCategory)
        composeTestRule.onNodeWithTag("vault_folder_generate_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("vault_folder_last_generated_value").assertIsDisplayed()

        // Also test inline Generate icon inside Add Value dialog
        composeTestRule.onNodeWithTag("automation_vault_fab").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("vault_fab_add_value").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("vault_entry_generate_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("vault_entry_save_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(2, savedEntries.size)

        // 2. Go back, create custom "Work Emails" folder, open it, verify folder-name analysis & Generate button
        composeTestRule.onNodeWithTag("automation_vault_back_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("automation_vault_fab").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("vault_fab_add_folder").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("vault_folder_name_input").performTextInput("Work Emails")
        composeTestRule.onNodeWithTag("vault_folder_save_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("automation_vault_list")
            .performScrollToNode(hasTestTag("vault_folder_folder_custom_gen_1"))
        composeTestRule.onNodeWithTag("vault_folder_folder_custom_gen_1").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("vault_folder_generate_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        val genEmail = lastSavedValue
        assertNotNull(genEmail)
        assertTrue("Expected analyzed folder to generate email, got $genEmail", genEmail!!.contains("@"))
        assertEquals("folder_custom_gen_1", lastSavedFolderId)
        assertEquals(AutomationVaultCategory.EMAIL, lastSavedCategory)
    }
}
