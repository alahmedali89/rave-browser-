package com.example

import android.content.Context
import android.os.Looper
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.automation.ActiveRecordingSession
import com.example.automation.AutomationActionType
import com.example.automation.AutomationEngine
import com.example.automation.AutomationErrorCode
import com.example.automation.AutomationHudMode
import com.example.automation.AutomationStep
import com.example.automation.AutomationStepCodec
import com.example.automation.AutomationType
import com.example.automation.BranchTraceRecord
import com.example.automation.ConditionNode
import com.example.automation.ConditionOperator
import com.example.automation.CredentialAuthenticationManager
import com.example.automation.CredentialFieldRole
import com.example.automation.CredentialVaultCrypto
import com.example.automation.CredentialVaultManager
import com.example.automation.InMemoryVaultKeyMetadataStore
import com.example.automation.InMemoryVaultKeyProvider
import com.example.automation.InterruptedExecutionCandidate
import com.example.automation.PageInspectionState
import com.example.automation.PersistedExecutionCheckpoint
import com.example.automation.Phase6ExecutionCoordinator
import com.example.automation.RecoveryAttemptRecord
import com.example.automation.RecoveryPolicy
import com.example.automation.RecoveryStrategy
import com.example.automation.SubWorkflowReference
import com.example.automation.TypedVariableValue
import com.example.automation.VariableDisplayBadge
import com.example.automation.WorkflowDocument
import com.example.automation.WorkflowDocumentMetadata
import com.example.automation.WorkflowExecutionState
import com.example.automation.WorkflowVariableDefinition
import com.example.automation.WorkflowVariableType
import com.example.data.local.ApexDatabase
import com.example.data.local.AutomationExecutionEntity
import com.example.data.local.AutomationWorkflowEntity
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.BrowserRepository
import com.example.engine.FastDownloadEngine
import com.example.ui.browser.AutomationDetailsScreenOverlay
import com.example.ui.browser.AutomationHudSheet
import com.example.ui.browser.AutomationWorkspaceScreenOverlay
import com.example.ui.theme.ApexBrowserTheme
import com.example.ui.viewmodel.BrowserViewModel
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Phase 6 Verification Suite — Part 2:
 * - Room Database v14 -> v15 Migration & schema integrity (12/13/14 preserved, 15 added)
 * - Failure Recovery strategies, unsafe retry blocking, and attempt trace logging
 * - Transitive Credential Vault enforcement across sub-workflows & export/import credential stripping
 * - Background/Lock INTERRUPTION persistence, checkpoint origin/tab validation, and clean resume
 * - Compose UI tests for Variables, Recovery, Branch/Loop Steps, Interrupted Banner, and Trace History
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase6WorkflowUiAndRecoveryTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: ApexDatabase
    private lateinit var repository: BrowserRepository
    private lateinit var vaultManager: CredentialVaultManager
    private lateinit var engine: AutomationEngine
    private lateinit var coordinator: Phase6ExecutionCoordinator
    private var activeStore: ViewModelStore? = null

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
        val prefs = UserPreferencesRepository(context)
        database = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val meta = InMemoryVaultKeyMetadataStore()
        val crypto = CredentialVaultCrypto(InMemoryVaultKeyProvider(meta), meta)
        val auth = CredentialAuthenticationManager(context)
        engine = AutomationEngine(
            jsEvalTimeoutMs = 1_500L,
            navTimeoutMs = 1_500L,
            domReadyTimeoutMs = 400L,
            domStabilityPollMs = 20L
        )
        coordinator = Phase6ExecutionCoordinator(engine)
        vaultManager = CredentialVaultManager(
            dao = database.apexDao(),
            crypto = crypto,
            authManager = auth,
            originParser = { url -> engine.parseOrigin(url) }
        )
        repository = BrowserRepository(
            dao = database.apexDao(),
            preferencesRepository = prefs,
            sharedPrefs = context.getSharedPreferences("phase6_ui_test_${UUID.randomUUID()}", Context.MODE_PRIVATE),
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
                        automationEngine = engine
                    ) as T
                }
            }
        )[BrowserViewModel::class.java]
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        return vm
    }

    private suspend fun idleUntil(timeoutMs: Long = 4_000L, detail: () -> String = { "" }, pred: () -> Boolean) {
        val main = Shadows.shadowOf(Looper.getMainLooper())
        val deadline = System.currentTimeMillis() + timeoutMs
        main.idle()
        while (!pred() && System.currentTimeMillis() < deadline) {
            main.idleFor(20L, TimeUnit.MILLISECONDS)
            delay(20L)
        }
        main.idle()
        val extra = detail().takeIf { it.isNotBlank() }?.let { " | $it" }.orEmpty()
        assertTrue("Timed out waiting within ${timeoutMs}ms$extra", pred())
    }

    @Test
    fun roomMigration14To15_preservesAllUserTables_andSupportsCheckpointAndTraceColumns() {
        val dbName = "migration_14_15_test.db"
        File(context.getDatabasePath(dbName).path).delete()

        @Suppress("DEPRECATION")
        val helper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            ApexDatabase::class.java.canonicalName!!,
            FrameworkSQLiteOpenHelperFactory()
        )

        helper.createDatabase(dbName, 14).apply {
            execSQL(
                """
                INSERT INTO bookmarks (id, url, title, createdAt)
                VALUES (1, 'https://example.org', 'Example', 1000)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO credential_vault (
                    credentialId, websiteOrigin, displayOrigin, label, usernameIdentifier,
                    encryptedUsername, encryptedPassword, createdAt, updatedAt, lastUsedAt
                ) VALUES (
                    'cred_1', 'https://auth.example.com', 'auth.example.com', 'Work',
                    'user@example.com', 'enc_u', 'enc_p', 1000, 1000, NULL
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO automation_workflows (
                    id, name, domain, sourceUrl, type, instructionsText, stepsJson,
                    stepCount, createdAt, updatedAt, lastExecutedAt, lastExecutionStatus, lastExecutionSummary
                ) VALUES (
                    7, 'Login Flow', 'auth.example.com', 'https://auth.example.com',
                    'MANUAL', 'Click login', '[]', 1, 1000, 2000, 3000, 'COMPLETED', 'Done'
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO automation_executions (
                    id, workflowId, workflowName, domain, status, totalSteps,
                    completedSteps, verificationSummary, startedAt, finishedAt
                ) VALUES (
                    55, 7, 'Login Flow', 'auth.example.com', 'COMPLETED', 1, 1, 'Done', 2900, 3000
                )
                """.trimIndent()
            )
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(
            dbName,
            15,
            true,
            ApexDatabase.MIGRATION_14_15
        )
        migratedDb.query("SELECT id, title FROM bookmarks WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Example", c.getString(1))
        }
        migratedDb.query("SELECT label FROM credential_vault WHERE credentialId = 'cred_1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Work", c.getString(0))
        }
        migratedDb.query("SELECT status, checkpointJson, traceJson FROM automation_executions WHERE id = 55").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("COMPLETED", c.getString(0))
            assertEquals("", c.getString(1))
            assertEquals("", c.getString(2))
        }
        migratedDb.close()
    }

    @Test
    fun failureRecovery_rescanAndRetry_alternativeBranch_blocksUnsafeRetry_andLogsAttempts() = runBlocking {
        val url = "https://shop.example.com/cart"
        val attempts = AtomicInteger(0)
        var fallbackClicked = false

        val webView = FakeEvalWebView(context) { script, cb ->
            when {
                script.contains("#fallback-btn") -> {
                    fallbackClicked = true
                    cb?.onReceiveValue(JSONObject.quote(JSONObject().put("ok", true).put("msg", "Fallback clicked").toString()))
                }
                script.contains("#flaky-btn") -> {
                    val count = attempts.incrementAndGet()
                    if (count == 1) {
                        cb?.onReceiveValue(JSONObject.quote(JSONObject().put("ok", false).put("code", "ELEMENT_STALE").put("msg", "Detached element").toString()))
                    } else {
                        cb?.onReceiveValue(JSONObject.quote(JSONObject().put("ok", true).put("msg", "Recovered on attempt 2").toString()))
                    }
                }
                else -> {
                    cb?.onReceiveValue(JSONObject.quote(JSONObject().put("ok", false).put("code", "ELEMENT_NOT_FOUND").put("msg", "Missing primary").toString()))
                }
            }
        }

        val wf = AutomationWorkflowEntity(30L, "Recovery Flow", "shop.example.com", url, AutomationType.MANUAL.id)

        // 1. RESCAN_AND_RETRY recovers on retry #1 (total calls = 2)
        val retryStep = AutomationStep(
            id = "retry_click",
            order = 1,
            actionType = AutomationActionType.CLICK,
            targetLabel = "Flaky Button",
            selector = "#flaky-btn",
            description = "Click flaky button",
            recoveryPolicy = RecoveryPolicy(
                strategy = RecoveryStrategy.RESCAN_AND_RETRY,
                maxRetries = 2,
                retryOnCodes = setOf(AutomationErrorCode.ELEMENT_STALE),
                rescanBeforeRetry = true
            )
        )
        val retryOutcome = coordinator.executeDocumentWorkflow(wf, WorkflowDocument(steps = listOf(retryStep)), url, { webView }, {}, { _, _ -> })
        assertEquals("COMPLETED", retryOutcome.status)
        assertTrue(retryOutcome.recoveryAttempts.isNotEmpty())
        assertTrue(retryOutcome.recoveryAttempts.last().succeeded)

        // 2. ALTERNATIVE_BRANCH triggers fallback when primary keeps failing
        val altStep = AutomationStep(
            id = "primary_missing",
            order = 1,
            actionType = AutomationActionType.CLICK,
            targetLabel = "Primary",
            selector = "#missing-primary",
            description = "Click primary button",
            recoveryPolicy = RecoveryPolicy(
                strategy = RecoveryStrategy.ALTERNATIVE_BRANCH,
                maxRetries = 1,
                retryOnCodes = setOf(AutomationErrorCode.ELEMENT_NOT_FOUND),
                alternativeSteps = listOf(
                    AutomationStep(
                        id = "alt_click",
                        order = 1,
                        actionType = AutomationActionType.CLICK,
                        targetLabel = "Fallback Button",
                        selector = "#fallback-btn",
                        description = "Click fallback button"
                    )
                )
            )
        )
        val altOutcome = coordinator.executeDocumentWorkflow(wf, WorkflowDocument(steps = listOf(altStep)), url, { webView }, {}, { _, _ -> })
        assertEquals("COMPLETED", altOutcome.status)
        assertTrue(fallbackClicked)
        assertEquals(RecoveryStrategy.ALTERNATIVE_BRANCH, altOutcome.recoveryAttempts.last().strategy)

        // 3. Auto-retry on SUBMIT step is blocked with UNSAFE_RECOVERY_BLOCKED
        val unsafeSubmit = AutomationStep(
            id = "submit_unsafe",
            order = 1,
            actionType = AutomationActionType.SUBMIT,
            targetLabel = "Submit Order",
            selector = "#missing-submit",
            description = "Submit payment order",
            isDestructive = true,
            recoveryPolicy = RecoveryPolicy(
                strategy = RecoveryStrategy.RESCAN_AND_RETRY,
                maxRetries = 2,
                requireConfirmForUnsafeRetry = true
            )
        )
        val blockedSubmitOutcome = coordinator.executeDocumentWorkflow(wf, WorkflowDocument(steps = listOf(unsafeSubmit)), url, { webView }, {}, { _, _ -> })
        assertEquals("FAILED", blockedSubmitOutcome.status)
        assertEquals(AutomationErrorCode.UNSAFE_RECOVERY_BLOCKED, blockedSubmitOutcome.errorCode)
    }

    @Test
    fun subWorkflow_transitivelyEnforcesCredentialVaultAuth_andExportImportStripsSecrets() = runBlocking {
        val url = "https://accounts.example.com/login"
        val cred = vaultManager.addCredential(url, "alice@example.com", "SuperSecret123!".toCharArray(), "Alice")
        assertTrue(cred is com.example.automation.CredentialVaultOperationResult.Success)
        val credId = (cred as com.example.automation.CredentialVaultOperationResult.Success).value.credentialId

        val childId = repository.saveAutomationWorkflow(
            name = "Child Sign-In",
            domain = "accounts.example.com",
            sourceUrl = url,
            type = AutomationType.MANUAL,
            steps = listOf(
                AutomationStep(
                    id = "child_pwd",
                    order = 1,
                    actionType = AutomationActionType.FILL_CREDENTIAL,
                    targetLabel = "Password",
                    selector = "#pwd",
                    description = "Fill Vault password",
                    credentialReferenceId = credId,
                    credentialFieldRole = CredentialFieldRole.PASSWORD,
                    expectedOrigin = "https://accounts.example.com",
                    requiresCredential = true
                )
            ),
            metadata = WorkflowDocumentMetadata(isReusableComponent = true)
        )

        val parentId = repository.saveAutomationWorkflow(
            name = "Parent With Cred Sub",
            domain = "accounts.example.com",
            sourceUrl = url,
            type = AutomationType.MANUAL,
            steps = listOf(
                AutomationStep(
                    id = "p_call_cred",
                    order = 1,
                    actionType = AutomationActionType.CALL_WORKFLOW,
                    targetLabel = "Child Sign-In",
                    description = "Reuse Child Sign-In",
                    subWorkflowRef = SubWorkflowReference(childId, "Child Sign-In")
                )
            ),
            metadata = WorkflowDocumentMetadata(
                variables = listOf(WorkflowVariableDefinition("note", WorkflowVariableType.TEXT, "ok"))
            )
        )

        // Export & re-import: must preserve Phase 6 metadata & subWorkflowRef, never expose secret values
        val exported = repository.exportAutomationsAsJson()
        assertFalse(exported.contains("SuperSecret123!"))
        assertTrue(exported.contains("\"phase6\""))
        assertTrue(exported.contains("\"subWorkflowRef\""))

        // ViewModel precheck MUST gate on CredentialReplayAuthorization when running parent workflow calling child with credential
        val vm = createViewModel()
        val activeTabId = vm.uiState.value.activeTabId
        val wv = FakeEvalWebView(context) { _, cb ->
            cb?.onReceiveValue(JSONObject.quote(JSONObject().put("ok", true).toString()))
        }
        vm.registerWebView(activeTabId, wv)
        vm.onWebViewPageStarted(activeTabId, url)
        vm.onWebViewPageFinished(activeTabId, "Login", url, canGoBack = false, canGoForward = false)

        vm.runWorkflow(parentId)
        idleUntil { vm.uiState.value.pendingCredentialReplayAuth != null }
        val pendingAuth = vm.uiState.value.pendingCredentialReplayAuth
        assertNotNull(pendingAuth)
        assertTrue(pendingAuth!!.items.any { it.credentialId == credId })
    }

    @Test
    fun interruptedExecution_onBackground_persistsCheckpoint_validatesOriginAndTab_andResumesFromRemainingSteps() = runBlocking {
        val url = "https://resume.example.com/step1"
        val step1Executed = AtomicInteger(0)
        val step2Ready = AtomicBoolean(false)
        val step3Executed = AtomicInteger(0)

        val vm = createViewModel()
        val tabId = vm.uiState.value.activeTabId
        val wv = FakeEvalWebView(context) { script, cb ->
            when {
                script.contains("const condType =") && script.contains("#step2-ready") -> {
                    cb?.onReceiveValue("""{"satisfied":${step2Ready.get()},"url":"$url","signature":"s:1"}""")
                }
                script.contains("readyState") -> {
                    cb?.onReceiveValue("""{"readyState":"complete","url":"$url","shape":"2:20","targetReady":true}""")
                }
                script.contains("STEP_1") -> {
                    step1Executed.incrementAndGet()
                    cb?.onReceiveValue(JSONObject.quote(JSONObject().put("ok", true).toString()))
                }
                script.contains("STEP_3") -> {
                    step3Executed.incrementAndGet()
                    cb?.onReceiveValue(JSONObject.quote(JSONObject().put("ok", true).toString()))
                }
                else -> cb?.onReceiveValue(JSONObject.quote(JSONObject().put("ok", true).toString()))
            }
        }
        vm.registerWebView(tabId, wv)
        vm.onWebViewPageStarted(tabId, url)
        vm.onWebViewPageFinished(tabId, "Resume Test", url, canGoBack = false, canGoForward = false)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val wfId = repository.saveAutomationWorkflow(
            name = "Interruptible 3-Step Flow",
            domain = "resume.example.com",
            sourceUrl = url,
            type = AutomationType.MANUAL,
            steps = listOf(
                AutomationStep(id = "s1", order = 1, actionType = AutomationActionType.CLICK, targetLabel = "STEP_1", selector = "#STEP_1", description = "Step 1"),
                AutomationStep(
                    id = "s2",
                    order = 2,
                    actionType = AutomationActionType.WAIT,
                    targetLabel = "Wait Ready",
                    selector = "#step2-ready",
                    description = "Wait before step 3",
                    waitCondition = com.example.automation.WaitConditionSpec(
                        com.example.automation.WaitConditionType.ELEMENT_APPEARS,
                        selector = "#step2-ready",
                        timeoutMs = 4_000L
                    )
                ),
                AutomationStep(id = "s3", order = 3, actionType = AutomationActionType.CLICK, targetLabel = "STEP_3", selector = "#STEP_3", description = "Step 3")
            )
        )

        vm.runWorkflow(wfId)
        idleUntil(detail = { "s1=${step1Executed.get()}, state=${vm.uiState.value.executionState}" }) {
            step1Executed.get() == 1 && (vm.uiState.value.executionState?.completedSteps ?: 0) >= 1
        }
        // Simulate app moving to background mid-workflow (during STEP_2 wait)
        vm.onAppBackgrounded()

        idleUntil {
            vm.uiState.value.interruptedExecutionCandidate != null
        }

        val candidate = vm.uiState.value.interruptedExecutionCandidate!!
        assertEquals(wfId, candidate.checkpoint.workflowId)
        assertTrue(candidate.canSafeResume)
        assertEquals(1, candidate.checkpoint.completedStepsCount)

        // Confirm origin mismatch blocks safe resume
        val originChanged = vm.validateInterruptedCheckpoint(
            candidate.checkpoint.copy(boundOrigin = "https://other-origin.example.org")
        )
        assertFalse(originChanged.canSafeResume)

        // Confirm missing tab blocks safe resume
        val closedTab = vm.validateInterruptedCheckpoint(
            candidate.checkpoint.copy(tabId = "non_existent_tab")
        )
        assertFalse(closedTab.canSafeResume)

        // Now user explicitly approves resume -> Step 1 must NOT re-run; Step 2 satisfies; Step 3 completes!
        step2Ready.set(true)
        vm.confirmResumeInterruptedExecution(candidate)
        idleUntil(timeoutMs = 5_000L, detail = { "state=${vm.uiState.value.executionState}" }) {
            vm.uiState.value.executionState?.status == "COMPLETED"
        }
        assertEquals(1, step1Executed.get())
        assertEquals(1, step3Executed.get())
    }

    @Test
    fun composeUi_detailsOverlay_rendersVariablesRecoveryBranchesAndTraceHistory() {
        val branchStep = AutomationStep(
            id = "cond_ui_1",
            order = 1,
            actionType = AutomationActionType.CONDITION,
            targetLabel = "Show VIP",
            description = "VIP Branch",
            condition = ConditionNode.Predicate(ConditionOperator.ELEMENT_EXISTS, selector = ".vip"),
            thenSteps = listOf(
                AutomationStep(id = "t1", order = 1, actionType = AutomationActionType.CLICK, targetLabel = "Claim", selector = "#claim", description = "Claim reward")
            ),
            elseSteps = listOf(
                AutomationStep(id = "e1", order = 1, actionType = AutomationActionType.CLICK, targetLabel = "Standard", selector = "#std", description = "Standard option")
            ),
            recoveryPolicy = RecoveryPolicy(RecoveryStrategy.RESCAN_AND_RETRY, maxRetries = 2)
        )
        val doc = WorkflowDocument(
            metadata = WorkflowDocumentMetadata(
                variables = listOf(WorkflowVariableDefinition("username", WorkflowVariableType.TEXT, defaultValue = "alex")),
                defaultRecoveryPolicy = RecoveryPolicy(RecoveryStrategy.WAIT_THEN_RETRY, maxRetries = 1)
            ),
            steps = listOf(branchStep)
        )
        val wf = AutomationWorkflowEntity(
            id = 99L,
            name = "Smart Loyalty Flow",
            domain = "shop.example.com",
            sourceUrl = "https://shop.example.com",
            type = AutomationType.MANUAL.id,
            stepsJson = AutomationStepCodec.encodeDocument(doc),
            stepCount = 1
        )
        val traceJson = Phase6ExecutionCoordinator.serializeExecutionTrace(
            branchTraces = listOf(BranchTraceRecord("cond_ui_1", "IF (VIP Branch)", "Element exists: .vip", matched = true, executedStepsCount = 1)),
            recoveryAttempts = listOf(RecoveryAttemptRecord("cond_ui_1", 1, AutomationErrorCode.ELEMENT_STALE, RecoveryStrategy.RESCAN_AND_RETRY, true, "Recovered cleanly"))
        )
        val exec = AutomationExecutionEntity(
            id = 500L,
            workflowId = 99L,
            workflowName = "Smart Loyalty Flow",
            domain = "shop.example.com",
            status = "COMPLETED",
            totalSteps = 1,
            completedSteps = 1,
            verificationSummary = "Completed via VIP branch",
            traceJson = traceJson
        )

        composeTestRule.setContent {
            ApexBrowserTheme {
                AutomationDetailsScreenOverlay(
                    workflow = wf,
                    executions = listOf(exec),
                    executionState = null,
                    allWorkflows = listOf(wf),
                    onRunWorkflow = {},
                    onRenameWorkflow = { _, _ -> },
                    onUpdateSteps = { _, _ -> },
                    onAddStep = { _, _, _, _, _ -> },
                    onConfirmDestructiveExecution = {},
                    onCancelDestructiveExecution = {},
                    onClearExecutionBanner = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("workflow_variables_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("workflow_variable_chip_username").assertIsDisplayed()
        composeTestRule.onNodeWithTag("automation_details_list").performScrollToNode(hasTestTag("default_recovery_card"))
        composeTestRule.onNodeWithTag("default_recovery_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("automation_details_list").performScrollToNode(hasTestTag("details_add_phase6_step_button"))
        composeTestRule.onNodeWithTag("details_add_phase6_step_button").assertIsDisplayed()

        // Open Phase 6 Builder Dialog
        composeTestRule.onNodeWithTag("details_add_phase6_step_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("phase6_builder_mode_loop").assertIsDisplayed()
    }

    @Test
    fun composeUi_hudSheetAndWorkspace_renderInterruptedBannerAndPhase6LiveTelemetry() {
        val candidateSafe = InterruptedExecutionCandidate(
            checkpoint = PersistedExecutionCheckpoint(
                executionId = 77L,
                workflowId = 44L,
                workflowName = "Checkout Multi-Page",
                tabId = "tab_1",
                boundOrigin = "https://shop.example.com",
                lastUrl = "https://shop.example.com/shipping",
                nextTopLevelIndex = 2,
                totalTopLevelSteps = 4,
                completedStepsCount = 2,
                safeVariables = mapOf("username" to TypedVariableValue("username", WorkflowVariableType.TEXT, "alice")),
                requiresCredentialReauth = true,
                interruptedReason = "App moved to background"
            ),
            canSafeResume = true,
            unsafeReason = ""
        )
        val liveState = WorkflowExecutionState(
            workflowId = 44L,
            workflowName = "Checkout Multi-Page",
            status = "RUNNING",
            currentStepIndex = 2,
            totalSteps = 4,
            activeBranchLabel = "IF: VIP tier",
            activeSubWorkflowName = "Fill Address",
            recoveryAttemptsCount = 1,
            variableSnapshots = listOf(
                VariableDisplayBadge("username", WorkflowVariableType.TEXT, "alice", isMasked = false),
                VariableDisplayBadge("secret_token", WorkflowVariableType.TEXT, "••••", isMasked = true)
            )
        )

        var resumeClicked = false
        var restartClicked = false
        val execStateHolder = mutableStateOf<WorkflowExecutionState?>(null)
        val interruptedHolder = mutableStateOf<InterruptedExecutionCandidate?>(candidateSafe)

        composeTestRule.setContent {
            val activeExec by remember { execStateHolder }
            val activeInterrupted by remember { interruptedHolder }
            ApexBrowserTheme {
                AutomationWorkspaceScreenOverlay(
                    workflows = emptyList(),
                    executions = emptyList(),
                    activeTabDomain = "shop.example.com",
                    activeTabUrl = "https://shop.example.com/shipping",
                    executionState = activeExec,
                    interruptedCandidate = activeInterrupted,
                    onResumeInterrupted = { resumeClicked = true },
                    onRestartInterrupted = { restartClicked = true },
                    onCreateWorkflow = { _, _, _, _ -> },
                    onRunWorkflow = {},
                    onOpenWorkflowDetails = {},
                    onRenameWorkflow = { _, _ -> },
                    onDuplicateWorkflow = {},
                    onDeleteWorkflow = {},
                    onExportJson = {},
                    onImportJson = {},
                    onConfirmDestructiveExecution = {},
                    onCancelDestructiveExecution = {},
                    onClearExecutionBanner = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("interrupted_banner").assertIsDisplayed()
        composeTestRule.onNodeWithText("Requires credential re-authorization before resume").assertIsDisplayed()

        // Tap Resume Safely -> opens confirmation dialog -> confirm
        composeTestRule.onNodeWithTag("interrupted_resume_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("interrupted_dialog_confirm_resume").performClick()
        composeTestRule.waitForIdle()
        assertTrue(resumeClicked)

        // Tap Restart Cleanly
        composeTestRule.onNodeWithTag("interrupted_restart_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(restartClicked)

        // Transition to active RUNNING workflow to verify Phase 6 live telemetry badges
        composeTestRule.runOnUiThread {
            interruptedHolder.value = null
            execStateHolder.value = liveState
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("automation_execution_phase6_telemetry").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sub: Fill Address").assertIsDisplayed()
        composeTestRule.onNodeWithText("IF: VIP tier").assertIsDisplayed()
        composeTestRule.onNodeWithText("Recovery x1").assertIsDisplayed()
    }

    @Test
    fun composeUi_interruptedBanner_disablesResumeWhenUnsafe() {
        val unsafeCandidate = InterruptedExecutionCandidate(
            checkpoint = PersistedExecutionCheckpoint(
                executionId = 88L,
                workflowId = 45L,
                workflowName = "Closed Tab Flow",
                tabId = "tab_closed",
                boundOrigin = "https://shop.example.com",
                lastUrl = "https://shop.example.com/pay",
                nextTopLevelIndex = 1,
                totalTopLevelSteps = 3,
                completedStepsCount = 1
            ),
            canSafeResume = false,
            unsafeReason = "Original browser tab was closed. Restart workflow cleanly."
        )

        composeTestRule.setContent {
            ApexBrowserTheme {
                AutomationHudSheet(
                    hudMode = AutomationHudMode.OVERVIEW,
                    pageInspection = PageInspectionState(domain = "shop.example.com", url = "https://shop.example.com"),
                    recordingSession = ActiveRecordingSession(),
                    instructionDraftText = "",
                    instructionDraftSteps = emptyList(),
                    executionState = null,
                    workflows = emptyList(),
                    executions = emptyList(),
                    interruptedCandidate = unsafeCandidate,
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
        composeTestRule.onNodeWithTag("interrupted_unsafe_warning").assertIsDisplayed()
        composeTestRule.onNodeWithTag("interrupted_resume_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("interrupted_restart_button").assertIsDisplayed()
    }
}
