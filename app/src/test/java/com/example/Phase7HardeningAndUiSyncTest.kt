package com.example

import android.content.Context
import android.os.Looper
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
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
import com.example.automation.AutomationToolbarStatus
import com.example.automation.AutomationType
import com.example.automation.DetectedPageElement
import com.example.automation.DomScanStatus
import com.example.automation.DomScanSummary
import com.example.automation.ExecutionStatus
import com.example.automation.InterruptedExecutionCandidate
import com.example.automation.JsEvalResult
import com.example.automation.PageInspectionState
import com.example.automation.PersistedExecutionCheckpoint
import com.example.automation.RecordingStatus
import com.example.automation.TypedVariableValue
import com.example.automation.VariableDisplayBadge
import com.example.automation.WorkflowExecutionState
import com.example.automation.WorkflowVariableType
import com.example.data.local.ApexDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.BrowserRepository
import com.example.engine.FastDownloadEngine
import com.example.ui.browser.AutomationHudSheet
import com.example.ui.theme.ApexBrowserTheme
import com.example.ui.viewmodel.BrowserViewModel
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
 * Phase 7 Verification Suite:
 * 1. Resource/leak & reliability regression tests for AutomationEngine, UniversalDomScanner, and BrowserViewModel.
 * 2. AutomationHudSheet UI/UX synchronization & accessibility tests across scanning, recording,
 *    Phase 6 telemetry, error/recovery states, interrupted checkpoint banners, and pause/resume/cancel actions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase7HardeningAndUiSyncTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: ApexDatabase
    private lateinit var repo: BrowserRepository
    private var activeStore: ViewModelStore? = null

    private class NeverRespondingWebView(context: Context) : WebView(context) {
        val removedInterfaces = mutableListOf<String>()
        val evaluatedScripts = mutableListOf<String>()

        override fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {
            evaluatedScripts.add(script)
            // Intentionally never invoke resultCallback to test timeout/cancellation callback cleanup
        }

        override fun removeJavascriptInterface(name: String) {
            removedInterfaces.add(name)
            super.removeJavascriptInterface(name)
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val prefs = UserPreferencesRepository(context)
        val sharedPrefs = context.getSharedPreferences("phase7_test_${UUID.randomUUID()}", Context.MODE_PRIVATE)
        repo = BrowserRepository(db.apexDao(), prefs, sharedPrefs)
    }

    @After
    fun tearDown() {
        activeStore?.clear()
        activeStore = null
        db.close()
    }

    private fun createViewModel(engine: AutomationEngine = AutomationEngine(jsEvalTimeoutMs = 1_000L)): BrowserViewModel {
        val dl = FastDownloadEngine(context, repo)
        val store = ViewModelStore()
        activeStore = store
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
        return vm
    }

    private fun awaitUntil(timeoutMs: Long = 3_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (predicate()) return
            Thread.sleep(20L)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Condition was not satisfied within ${timeoutMs}ms", predicate())
    }

    @Test
    fun automationEngine_evalJsWithResult_cancelsPendingMainRunnableOnCoroutineCancelOrTimeout() {
        val engine = AutomationEngine(jsEvalTimeoutMs = 80L)
        val wv = NeverRespondingWebView(context)

        // Timeout returns JsEvalResult.Timeout rather than hanging forever
        val timeoutRes = runBlocking {
            engine.evalJsWithResultInternal(wv, "1+1", timeoutMs = 60L)
        }
        assertTrue(timeoutRes is JsEvalResult.Timeout)

        // Cancelling coroutine removes queued main-thread runnable cleanly
        runBlocking {
            val job = launch {
                engine.evalJsWithResultInternal(wv, "window.__late()", timeoutMs = 5_000L)
            }
            job.cancel(CancellationException("Immediate cancel"))
            job.join()
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun universalDomScanner_observerScriptIncludesShadowIframeGuardsAndCleansUpStrayPayloads() {
        val engine = AutomationEngine()
        val scanner = engine.universalScanner
        val script = scanner.buildDynamicObserverInstallScript(
            sessionId = "sess_1",
            tabId = "tab_1",
            expectedOrigin = "https://example.com:443",
            debounceMs = 150L,
            maxBatchSize = 50,
            maxMutationQueueSize = 120
        )

        assertTrue(script.contains("state.destroyed"))
        assertTrue(script.contains("state.observers"))
        assertTrue(script.contains("MAX_QUEUE = 120"))
        assertTrue(script.contains("observeRoot"))
        assertTrue(script.contains("shadowRoot"))
        assertTrue(script.contains("contentDocument"))

        // If an async bridge payload arrives when no active session exists, scanner triggers cleanup on sourceWebView
        val wv = NeverRespondingWebView(context)
        val stalePayload = """
            {"sessionId":"old_sess_99","tabId":"tab_missing","origin":"https://example.com:443","removedSelectors":["#x"],"updatedElements":[]}
        """.trimIndent()
        val res = scanner.processDynamicMutationBatch(
            rawJson = stalePayload,
            sourceTabId = "tab_missing",
            sourceWebView = wv,
            liveWebViewUrl = "https://example.com/home"
        )
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertNull(res)
        assertTrue(wv.evaluatedScripts.any { it.contains("old_sess_99") && it.contains("__apexDomScannerObserver") })
    }

    @Test
    fun browserViewModel_registerWebView_detachesBridgesAndCancelsActiveExecutionOnReplacement() {
        val vm = createViewModel()
        val tabId = vm.uiState.value.activeTabId
        val wv1 = NeverRespondingWebView(context)
        val wv2 = NeverRespondingWebView(context)

        vm.submitNavigation("https://example.com/start", tabId)
        vm.registerWebView(tabId, wv1)

        val wfId = runBlocking {
            repo.saveAutomationWorkflow(
                name = "Long Wait",
                domain = "example.com",
                sourceUrl = "https://example.com/start",
                type = AutomationType.MANUAL,
                steps = listOf(
                    AutomationStep(
                        id = "s1",
                        order = 1,
                        actionType = AutomationActionType.WAIT,
                        targetLabel = "Never",
                        selector = "#never",
                        description = "Wait for #never"
                    )
                )
            )
        }
        vm.runWorkflow(wfId, confirmDestructive = true)
        awaitUntil { vm.uiState.value.executionState?.status == ExecutionStatus.RUNNING.id }

        // Replace WebView while execution is suspended
        vm.registerWebView(tabId, wv2)
        awaitUntil { vm.uiState.value.executionState?.errorCode == AutomationErrorCode.WEBVIEW_REPLACED }

        assertTrue(wv1.removedInterfaces.contains("ApexAutomationBridge"))
        assertTrue(wv1.removedInterfaces.contains("ApexPasswordBridge"))
        val execState = vm.uiState.value.executionState
        assertNotNull(execState)
        assertEquals(AutomationErrorCode.WEBVIEW_REPLACED, execState?.errorCode)
    }

    @Test
    fun browserViewModel_closeGroupResetAllTabsAndOnCleared_abortActiveExecutionCleanly() {
        // 1. closeGroup aborts running execution in a closed group tab
        val vmGroup = createViewModel()
        val tabA = vmGroup.uiState.value.activeTabId
        vmGroup.createNewTab(initialUrl = "https://example.com/g1")
        val tabB = vmGroup.uiState.value.activeTabId
        vmGroup.groupSelectedTabs(setOf(tabA, tabB))
        val groupId = vmGroup.uiState.value.tabs.first { it.id == tabB }.groupId!!
        val wvB = NeverRespondingWebView(context)
        vmGroup.registerWebView(tabB, wvB)

        val wfId1 = runBlocking {
            repo.saveAutomationWorkflow(
                name = "Group Wait",
                domain = "example.com",
                sourceUrl = "https://example.com/g1",
                type = AutomationType.MANUAL,
                steps = listOf(
                    AutomationStep("g1", 1, AutomationActionType.WAIT, "Never", "#never", description = "Wait")
                )
            )
        }
        vmGroup.runWorkflow(wfId1, confirmDestructive = true)
        awaitUntil { vmGroup.uiState.value.executionState?.status == ExecutionStatus.RUNNING.id }

        vmGroup.closeGroup(groupId)
        awaitUntil {
            vmGroup.uiState.value.executionState?.errorCode == AutomationErrorCode.TAB_CLOSED
        }
        assertFalse(vmGroup.uiState.value.tabs.any { it.id == tabB })
        assertTrue(wvB.removedInterfaces.contains("ApexAutomationBridge"))

        // 2. resetAllTabs + onCleared cleanly cancel jobs without leaking
        val vmReset = createViewModel()
        val tabR = vmReset.uiState.value.activeTabId
        val wvR = NeverRespondingWebView(context)
        vmReset.submitNavigation("https://example.com/reset", tabR)
        vmReset.registerWebView(tabR, wvR)
        val wfId2 = runBlocking {
            repo.saveAutomationWorkflow(
                name = "Reset Wait",
                domain = "example.com",
                sourceUrl = "https://example.com/reset",
                type = AutomationType.MANUAL,
                steps = listOf(
                    AutomationStep("r1", 1, AutomationActionType.WAIT, "Never", "#never", description = "Wait")
                )
            )
        }
        vmReset.runWorkflow(wfId2, confirmDestructive = true)
        awaitUntil { vmReset.uiState.value.executionState?.status == ExecutionStatus.RUNNING.id }

        vmReset.resetAllTabs()
        awaitUntil {
            vmReset.uiState.value.executionState?.errorCode == AutomationErrorCode.TAB_CLOSED
        }
        assertEquals(1, vmReset.uiState.value.tabs.size)
        assertTrue(wvR.removedInterfaces.contains("ApexAutomationBridge"))

        activeStore?.clear()
        activeStore = null
    }

    @Test
    fun composeUi_automationHudSheet_synchronizesScannerTelemetryRecoveryMaskingInterruptedAndA11y() {
        val pausedClicks = AtomicInteger(0)
        val cancelScanClicks = AtomicInteger(0)

        val scanningState = PageInspectionState(
            domain = "127.0.0.1",
            url = "http://127.0.0.1:8080/form",
            elements = listOf(
                DetectedPageElement(
                    id = "el_0",
                    tagName = "input",
                    role = "textbox",
                    label = "Email",
                    selector = "#email",
                    inputType = "email"
                )
            ),
            isScanning = true,
            scanStatus = DomScanStatus.PARTIAL,
            summary = DomScanSummary(
                totalDiscovered = 1,
                isPartial = true,
                partialReason = "Bounded limit 200 reached"
            ),
            isDynamicObserverActive = true
        )

        val execState = WorkflowExecutionState(
            workflowId = 55L,
            workflowName = "Sync Test Workflow",
            status = ExecutionStatus.RUNNING.id,
            currentStepIndex = 1,
            totalSteps = 3,
            currentStepDescription = "Fill Billing Name",
            verificationMessage = "Running step 2 of 3",
            recoveryNotice = "Recovered stale locator via fallback",
            activeBranchLabel = "IF: Premium User",
            activeSubWorkflowName = "Billing Subflow",
            recoveryAttemptsCount = 1,
            variableSnapshots = listOf(
                VariableDisplayBadge("user_email", WorkflowVariableType.TEXT, "alice@test.local", isMasked = false),
                VariableDisplayBadge("auth_token", WorkflowVariableType.TEXT, "••••", isMasked = true)
            )
        )

        val interrupted = InterruptedExecutionCandidate(
            checkpoint = PersistedExecutionCheckpoint(
                executionId = 90L,
                workflowId = 55L,
                workflowName = "Sync Test Workflow",
                tabId = "tab_1",
                boundOrigin = "http://127.0.0.1:8080",
                lastUrl = "http://127.0.0.1:8080/step2",
                nextTopLevelIndex = 1,
                totalTopLevelSteps = 3,
                completedStepsCount = 1,
                safeVariables = mapOf("user_email" to TypedVariableValue("user_email", WorkflowVariableType.TEXT, "alice@test.local")),
                interruptedReason = "App backgrounded"
            ),
            canSafeResume = true
        )

        var activeExecState by mutableStateOf<WorkflowExecutionState?>(execState)

        composeTestRule.setContent {
            ApexBrowserTheme {
                AutomationHudSheet(
                    hudMode = AutomationHudMode.ELEMENTS,
                    pageInspection = scanningState,
                    recordingSession = ActiveRecordingSession(
                        sessionId = "rec_1",
                        status = RecordingStatus.RECORDING,
                        steps = emptyList()
                    ),
                    instructionDraftText = "",
                    instructionDraftSteps = emptyList(),
                    executionState = activeExecState,
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
                    onDismiss = {},
                    onPauseExecution = { pausedClicks.incrementAndGet() },
                    onCancelScan = { cancelScanClicks.incrementAndGet() },
                    interruptedCandidate = interrupted
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("automation_hud_sheet").assertIsDisplayed()
        // Active execution suppresses interrupted_banner so the live execution banner stays front-and-center
        composeTestRule.onNodeWithTag("interrupted_banner").assertDoesNotExist()
        composeTestRule.onNodeWithTag("elements_partial_banner").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("elements_cancel_scan_button").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, cancelScanClicks.get())

        // Live execution banner, Phase 6 branch/subworkflow telemetry, recovery notice, masked variable & pause control
        composeTestRule.onNodeWithTag("automation_execution_banner").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("automation_execution_recovery_notice").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("automation_execution_phase6_telemetry").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("••••", substring = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("automation_pause_execution_button").performScrollTo().performClick()
        assertEquals(1, pausedClicks.get())

        // Accessibility content descriptions present on close control
        composeTestRule.onNodeWithTag("automation_hud_close_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNode(hasContentDescription("Close Automation HUD")).performScrollTo().assertIsDisplayed()

        // Once active execution is no longer running, interrupted_banner appears for safe resume
        composeTestRule.runOnUiThread {
            activeExecState = null
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("interrupted_banner").performScrollTo().assertIsDisplayed()
    }
}
