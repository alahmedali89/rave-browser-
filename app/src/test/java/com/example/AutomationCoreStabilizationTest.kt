package com.example

import android.content.Context
import android.os.Looper
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import java.util.concurrent.TimeUnit
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
import com.example.automation.AutomationStepDecodeResult
import com.example.automation.AutomationToolbarStatus
import com.example.automation.AutomationType
import com.example.automation.JsEvalResult
import com.example.automation.NavigationLifecyclePhase
import com.example.automation.NavigationWaitResult
import com.example.automation.PageInspectionState
import com.example.automation.TabNavigationSynchronizer
import com.example.automation.WorkflowExecutionState
import com.example.data.local.ApexDatabase
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
class AutomationCoreStabilizationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

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

    // -------------------------------------------------------------------------
    // PHASE 1: DATABASE INTEGRITY, MIGRATION SAFETY & SCHEMA VERSIONING
    // -------------------------------------------------------------------------

    @Test
    fun phase1_migration12To13_preservesExistingUserData_enforcesForeignKey_andDeletesOrphans() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_12_13_test.db"
        File(context.getDatabasePath(dbName).path).delete()

        @Suppress("DEPRECATION")
        val helper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            ApexDatabase::class.java.canonicalName!!,
            FrameworkSQLiteOpenHelperFactory()
        )

        helper.createDatabase(dbName, 12).apply {
            execSQL(
                """
                INSERT INTO bookmarks (id, url, title, createdAt)
                VALUES (10, 'https://kotlinlang.org', 'Kotlin', 1000)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO saved_passwords (
                    id, siteName, siteUrl, host, username, encryptedPassword,
                    notes, category, isFavorite, strengthScore, passwordFingerprint,
                    createdAt, updatedAt, lastUsedAt
                ) VALUES (
                    20, 'Example', 'https://example.com', 'example.com', 'alice', 'enc_secret',
                    'work', 'LOGIN', 1, 90, 'fp_1', 1000, 1000, NULL
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO browsing_history (
                    id, url, title, host, isHttps, trackersBlockedOnVisit, visitCount, visitedAt
                ) VALUES (
                    30, 'https://news.ycombinator.com', 'HN', 'news.ycombinator.com', 1, 3, 2, 1500
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO automation_workflows (
                    id, name, domain, sourceUrl, type, instructionsText, stepsJson,
                    stepCount, createdAt, updatedAt, lastExecutedAt, lastExecutionStatus, lastExecutionSummary
                ) VALUES (
                    1, 'Checkout Flow', 'shop.example.com', 'https://shop.example.com',
                    'MANUAL', 'Click buy', '[]', 1, 2000, 2100, 2200, 'COMPLETED', 'OK'
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO automation_executions (
                    id, workflowId, workflowName, domain, status, totalSteps,
                    completedSteps, verificationSummary, startedAt, finishedAt
                ) VALUES (
                    100, 1, 'Checkout Flow', 'shop.example.com', 'COMPLETED', 1, 1, 'Done', 2190, 2200
                )
                """.trimIndent()
            )
            // Orphan execution whose parent workflow does not exist
            execSQL(
                """
                INSERT INTO automation_executions (
                    id, workflowId, workflowName, domain, status, totalSteps,
                    completedSteps, verificationSummary, startedAt, finishedAt
                ) VALUES (
                    101, 9999, 'Ghost Flow', 'ghost.test', 'FAILED', 2, 0, 'Orphaned', 1900, 1950
                )
                """.trimIndent()
            )
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(
            dbName,
            13,
            true,
            ApexDatabase.MIGRATION_12_13
        )

        migratedDb.query("SELECT url, title FROM bookmarks WHERE id = 10").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("https://kotlinlang.org", c.getString(0))
            assertEquals("Kotlin", c.getString(1))
        }
        migratedDb.query("SELECT username FROM saved_passwords WHERE id = 20").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("alice", c.getString(0))
        }
        migratedDb.query("SELECT url FROM browsing_history WHERE id = 30").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("https://news.ycombinator.com", c.getString(0))
        }
        migratedDb.query("SELECT name FROM automation_workflows WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Checkout Flow", c.getString(0))
        }
        migratedDb.query("SELECT id FROM automation_executions ORDER BY id ASC").use { c ->
            val ids = mutableListOf<Long>()
            while (c.moveToNext()) ids.add(c.getLong(0))
            assertEquals(listOf(100L), ids)
        }

        // Verify ON DELETE CASCADE at SQLite level
        migratedDb.execSQL("PRAGMA foreign_keys = ON")
        migratedDb.execSQL("DELETE FROM automation_workflows WHERE id = 1")
        migratedDb.query("SELECT COUNT(*) FROM automation_executions WHERE workflowId = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        migratedDb.close()
    }

    @Test
    fun phase1_finishExecutionPreservesStartedAtAndMetadata_andTransactionalDeleteCleansExecutions() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val prefsRepo = UserPreferencesRepository(context)
        val sharedPrefs = context.getSharedPreferences("apex_phase1_repo_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().clear().commit()
        val repo = BrowserRepository(db.apexDao(), prefsRepo, sharedPrefs)

        try {
            val wf1 = repo.saveAutomationWorkflow(
                name = "Flow One",
                domain = "one.test",
                sourceUrl = "https://one.test",
                type = AutomationType.MANUAL,
                steps = listOf(
                    AutomationStep("s1", 1, AutomationActionType.CLICK, "Next", "#next", "", "Click Next"),
                    AutomationStep("s2", 2, AutomationActionType.CLICK, "Done", "#done", "", "Click Done")
                )
            )
            val wf2 = repo.saveAutomationWorkflow(
                name = "Flow Two",
                domain = "two.test",
                sourceUrl = "https://two.test",
                type = AutomationType.RECORDED,
                steps = listOf(
                    AutomationStep("s3", 1, AutomationActionType.CLICK, "Go", "#go", "", "Click Go")
                )
            )

            val wf1Entity = repo.getAutomationWorkflowById(wf1)!!
            val wf2Entity = repo.getAutomationWorkflowById(wf2)!!

            val execId1 = repo.recordAutomationExecutionStart(wf1Entity, 2)
            val initialRecord = db.apexDao().getAutomationExecutionById(execId1)!!
            val originalStartedAt = initialRecord.startedAt

            delay(25L)
            repo.finishAutomationExecution(
                workflowId = wf1,
                executionId = execId1,
                status = "COMPLETED",
                completedSteps = 2,
                totalSteps = 2,
                verificationSummary = "All steps verified"
            )

            val finishedRecord = db.apexDao().getAutomationExecutionById(execId1)!!
            assertEquals(execId1, finishedRecord.id)
            assertEquals(wf1, finishedRecord.workflowId)
            assertEquals("Flow One", finishedRecord.workflowName)
            assertEquals("one.test", finishedRecord.domain)
            assertEquals(originalStartedAt, finishedRecord.startedAt)
            assertNotNull(finishedRecord.finishedAt)
            assertTrue(finishedRecord.finishedAt!! > originalStartedAt)
            assertEquals("COMPLETED", finishedRecord.status)
            assertEquals(2, finishedRecord.completedSteps)
            assertEquals("All steps verified", finishedRecord.verificationSummary)

            // Start second workflow execution and check per-workflow vs global queries
            val execId2 = repo.recordAutomationExecutionStart(wf2Entity, 1)
            val wf1History = repo.observeAutomationExecutionsForWorkflow(wf1).first()
            val allHistory = repo.automationExecutionsFlow.first()
            assertEquals(listOf(execId1), wf1History.map { it.id })
            assertEquals(setOf(execId1, execId2), allHistory.map { it.id }.toSet())

            // Deleting wf1 removes its execution atomically without touching wf2's execution
            repo.deleteAutomationWorkflow(wf1)
            assertEquals(emptyList<Long>(), db.apexDao().getAutomationExecutionsForWorkflowOnce(wf1).map { it.id })
            assertEquals(listOf(execId2), db.apexDao().getAutomationExecutionsForWorkflowOnce(wf2).map { it.id })
        } finally {
            db.close()
        }
    }

    @Test
    fun phase1_stepCodecVersioning_readsLegacyAndV1_rejectsFutureSchema_andRoundTripsExportImport() = runBlocking {
        val legacyArrayJson = """
            [
              {
                "id": "legacy_1",
                "order": 1,
                "actionType": "CLICK",
                "targetLabel": "Sign In",
                "selector": "#login",
                "value": "",
                "description": "Click Sign In",
                "isDestructive": false
              }
            ]
        """.trimIndent()
        val decodedLegacy = AutomationStepCodec.decodeSafely(legacyArrayJson)
        assertTrue(decodedLegacy is AutomationStepDecodeResult.Success)
        decodedLegacy as AutomationStepDecodeResult.Success
        assertTrue(decodedLegacy.isLegacyUnversioned)
        assertEquals(1, decodedLegacy.steps.size)
        assertEquals("Sign In", decodedLegacy.steps.first().targetLabel)

        val versionlessObj = """{"steps": $legacyArrayJson}"""
        val decodedObj = AutomationStepCodec.decodeSafely(versionlessObj)
        assertTrue(decodedObj is AutomationStepDecodeResult.Success)
        assertEquals(1, decodedObj.stepsOrEmpty().size)

        val encodedV1 = AutomationStepCodec.encode(decodedLegacy.steps)
        assertTrue(encodedV1.contains("\"schemaVersion\":1"))
        val decodedV1 = AutomationStepCodec.decodeSafely(encodedV1)
        assertTrue(decodedV1 is AutomationStepDecodeResult.Success)
        assertFalse((decodedV1 as AutomationStepDecodeResult.Success).isLegacyUnversioned)

        val futureJson = """{"schemaVersion": 99, "steps": $legacyArrayJson}"""
        val futureRes = AutomationStepCodec.decodeSafely(futureJson)
        assertTrue(futureRes is AutomationStepDecodeResult.UnsupportedVersion)
        assertEquals(99, (futureRes as AutomationStepDecodeResult.UnsupportedVersion).foundVersion)

        val badVersionJson = """{"schemaVersion": 0, "steps": $legacyArrayJson}"""
        val badVerRes = AutomationStepCodec.decodeSafely(badVersionJson)
        assertTrue(badVerRes is AutomationStepDecodeResult.UnsupportedVersion)

        // Export / import round-trip & unknown future schema filtering
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = BrowserRepository(
            db.apexDao(),
            UserPreferencesRepository(context),
            context.getSharedPreferences("apex_phase1_export_prefs", Context.MODE_PRIVATE)
        )
        try {
            repo.saveAutomationWorkflow(
                name = "Saved V1",
                domain = "example.com",
                sourceUrl = "https://example.com",
                type = AutomationType.MANUAL,
                steps = decodedLegacy.steps
            )
            val exported = repo.exportAutomationsAsJson()
            assertTrue(exported.contains("\"schemaVersion\": 1"))

            val mixedImport = """
                [
                  {
                    "schemaVersion": 99,
                    "name": "Future Unsupported Workflow",
                    "domain": "future.test",
                    "sourceUrl": "https://future.test",
                    "type": "MANUAL",
                    "steps": {"schemaVersion": 99, "steps": []}
                  },
                  {
                    "schemaVersion": 1,
                    "name": "Valid Imported Workflow",
                    "domain": "valid.test",
                    "sourceUrl": "https://valid.test",
                    "type": "RECORDED",
                    "steps": {"schemaVersion": 1, "steps": $legacyArrayJson}
                  }
                ]
            """.trimIndent()
            val importedCount = repo.importAutomationsFromJson(mixedImport)
            assertEquals(1, importedCount)
            val workflows = db.apexDao().getAutomationWorkflowsOnce()
            assertTrue(workflows.any { it.name == "Valid Imported Workflow" })
            assertFalse(workflows.any { it.name == "Future Unsupported Workflow" })
        } finally {
            db.close()
        }
    }

    // -------------------------------------------------------------------------
    // PHASE 2: CRITICAL EXECUTION SAFETY, SELECTORS, TIMEOUTS & CANCELLATION
    // -------------------------------------------------------------------------

    @Test
    fun phase2_validateSelector_rejectsMalformedAndAcceptsValidEscapedSelectors() {
        val engine = AutomationEngine()
        val invalid = listOf(
            "",
            "   ",
            "input[name=\"unclosed]",
            "input[name=\"missing_bracket\"",
            "button:nth-of-type(2",
            "div >",
            "> button",
            "div,,span",
            "div\\"
        )
        for (sel in invalid) {
            val res = engine.validateSelector(sel)
            assertFalse("Expected invalid for '$sel'", res.isValid)
        }

        val valid = listOf(
            "#email",
            "#user\\:email",
            "input[name=\"user\\\"name\"]",
            "form > div:nth-of-type(2) > button[type=\"submit\"]",
            "[data-testid=\"checkout_btn\"]"
        )
        for (sel in valid) {
            val res = engine.validateSelector(sel)
            assertTrue("Expected valid for '$sel': ${res.reason}", res.isValid)
        }
    }

    @Test
    fun phase2_noUnsafeFirstElementFallback_structuredErrors_jsTimeout_andCancellationPropagation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = AutomationEngine(jsEvalTimeoutMs = 150L, navTimeoutMs = 400L, domReadyTimeoutMs = 300L)

        // Verify generated scripts have NO blind [0] fallbacks across any interactive action
        val actionsToVerify = listOf(
            AutomationActionType.INPUT,
            AutomationActionType.TOGGLE,
            AutomationActionType.SELECT,
            AutomationActionType.CLICK,
            AutomationActionType.SUBMIT
        )
        for (action in actionsToVerify) {
            val step = AutomationStep("s_$action", 1, action, "Email", "#email", "user@test.dev", "Step")
            val js = engine.buildStepExecutionScript(step)
            assertFalse("$action script must not use allInputs[0]", js.contains("allInputs[0]"))
            assertFalse("$action script must not use list[0]", js.contains("list[0]"))
            assertFalse("$action script must not fall back with || candidates[0]", js.contains("|| candidates[0]"))
            val candidatesZeroMatches = Regex("""candidates\[0]""").findAll(js).toList()
            for (match in candidatesZeroMatches) {
                val precedingWindow = js.substring(maxOf(0, match.range.first - 80), match.range.first)
                assertTrue(
                    "$action script must only access candidates[0] inside a strict candidates.length === 1 branch",
                    precedingWindow.contains("candidates.length === 1")
                )
            }
            assertTrue("$action script must reject ambiguous matches", js.contains("ELEMENT_AMBIGUOUS"))
            assertTrue("$action script must use apexResolveTarget", js.contains("apexResolveTarget"))
        }

        // Null WebView -> WEBVIEW_UNAVAILABLE
        val dummyStep = AutomationStep("s1", 1, AutomationActionType.CLICK, "Go", "#go", "", "Click Go")
        val nullWvResult = engine.executeSingleStep(
            step = dummyStep,
            activeUrl = "https://example.com",
            webViewProvider = { null },
            onNavigateUrl = {}
        )
        assertFalse(nullWvResult.success)
        assertEquals(AutomationErrorCode.WEBVIEW_UNAVAILABLE, nullWvResult.errorCode)

        // Malformed selector -> rejected pre-execution with INVALID_SELECTOR
        val badSelectorStep = AutomationStep("s2", 1, AutomationActionType.INPUT, "Email", "input[name=\"broken", "a@b.com", "Bad sel")
        var jsInvokedForBadSel = false
        val probeWv = FakeEvalWebView(context) { _, cb ->
            jsInvokedForBadSel = true
            cb?.onReceiveValue("""{"ok":true}""")
        }
        val badSelRes = engine.executeSingleStep(
            step = badSelectorStep,
            activeUrl = "https://example.com",
            webViewProvider = { probeWv },
            onNavigateUrl = {}
        )
        assertFalse(badSelRes.success)
        assertEquals(AutomationErrorCode.INVALID_SELECTOR, badSelRes.errorCode)
        assertFalse("Malformed selector should be rejected before WebView eval", jsInvokedForBadSel)

        // Missing, ambiguous, disabled, option_not_found structured JSON errors
        val structuredCodes = listOf(
            AutomationErrorCode.ELEMENT_NOT_FOUND,
            AutomationErrorCode.ELEMENT_AMBIGUOUS,
            AutomationErrorCode.ELEMENT_DISABLED,
            AutomationErrorCode.OPTION_NOT_FOUND
        )
        for (expectedCode in structuredCodes) {
            val wv = FakeEvalWebView(context) { _, cb ->
                cb?.onReceiveValue("""{"ok":false,"code":"${expectedCode.name}","msg":"Failed: ${expectedCode.name}"}""")
            }
            val res = engine.executeSingleStep(
                step = dummyStep,
                activeUrl = "https://example.com",
                webViewProvider = { wv },
                onNavigateUrl = {}
            )
            assertFalse(res.success)
            assertEquals(expectedCode, res.errorCode)
        }

        // Throwing evaluateJavascript -> JS_EXCEPTION
        val throwingWv = FakeEvalWebView(context) { _, _ ->
            throw IllegalStateException("WebView destroyed")
        }
        val throwRes = engine.executeSingleStep(
            step = dummyStep,
            activeUrl = "https://example.com",
            webViewProvider = { throwingWv },
            onNavigateUrl = {}
        )
        assertFalse(throwRes.success)
        assertEquals(AutomationErrorCode.JS_EXCEPTION, throwRes.errorCode)

        // Unresponsive evaluateJavascript callback -> bounded JS_TIMEOUT
        val hangingWv = FakeEvalWebView(context) { _, _ -> /* never invoke callback */ }
        val timeoutDeferred = async {
            engine.executeSingleStep(
                step = dummyStep,
                activeUrl = "https://example.com",
                webViewProvider = { hangingWv },
                onNavigateUrl = {}
            )
        }
        waitUntil(timeoutMs = 2_000L) { timeoutDeferred.isCompleted }
        val timeoutRes = timeoutDeferred.await()
        assertFalse(timeoutRes.success)
        assertEquals(AutomationErrorCode.JS_TIMEOUT, timeoutRes.errorCode)

        // CancellationException propagation in evalJsWithResult (must not swallow cancellation)
        val slowEngine = AutomationEngine(jsEvalTimeoutMs = 5_000L)
        val startedEval = CompletableDeferred<Unit>()
        val neverRespondingWv = FakeEvalWebView(context) { _, _ ->
            startedEval.complete(Unit)
        }
        var caughtCancellation = false
        var evalReturnedNormally = false
        val evalJob = launch {
            try {
                val res = slowEngine.evalJsWithResult(neverRespondingWv, "1+1")
                if (res is JsEvalResult.Timeout || res is JsEvalResult.Failure) {
                    evalReturnedNormally = true
                }
            } catch (ce: CancellationException) {
                caughtCancellation = true
                throw ce
            }
        }
        waitUntil(timeoutMs = 2_000L) { startedEval.isCompleted }
        evalJob.cancel()
        waitUntil(timeoutMs = 2_000L) { evalJob.isCompleted }
        assertTrue("CancellationException must propagate rather than returning Timeout/Failure", caughtCancellation)
        assertFalse(evalReturnedNormally)
    }

    @Test
    fun phase2_viewModel_cancelExecution_preventsConcurrentRuns_persistsCancelled_andRejectsUnsupportedSchema() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val prefsRepo = UserPreferencesRepository(context)
        val sharedPrefs = context.getSharedPreferences("apex_phase2_vm_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().clear().commit()
        val repo = BrowserRepository(db.apexDao(), prefsRepo, sharedPrefs)
        val engine = AutomationEngine(jsEvalTimeoutMs = 5_000L, navTimeoutMs = 5_000L, domReadyTimeoutMs = 200L)
        val vm = BrowserViewModel(
            repository = repo,
            fastDownloadEngine = FastDownloadEngine(context, repo),
            automationEngine = engine
        )

        try {
            val activeTabId = vm.uiState.value.activeTabId
            vm.submitNavigation("https://shop.example.com", activeTabId)
            vm.onWebViewPageStarted(activeTabId, "https://shop.example.com")
            vm.onWebViewPageFinished(
                tabId = activeTabId,
                title = "Shop",
                url = "https://shop.example.com",
                canGoBack = false,
                canGoForward = false
            )
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            // FakeWebView: Step 1 + DOM readiness probe complete immediately; Step 2 hangs waiting on callback until cancelled
            var stepEvalCount = 0
            val step2Dispatched = CompletableDeferred<Unit>()
            val fakeWv = FakeEvalWebView(context) { script, cb ->
                if (script.contains("readyState")) {
                    cb?.onReceiveValue("""{"readyState":"complete","url":"https://shop.example.com","shape":"5:120","targetReady":true}""")
                } else {
                    stepEvalCount++
                    if (stepEvalCount == 1) {
                        cb?.onReceiveValue("""{"ok":true,"code":"NONE","msg":"Step 1 clicked"}""")
                    } else {
                        step2Dispatched.complete(Unit)
                    }
                }
            }
            vm.registerWebView(activeTabId, fakeWv)

            val twoSteps = listOf(
                AutomationStep("s1", 1, AutomationActionType.CLICK, "Start", "#start", "", "Click start"),
                AutomationStep("s2", 2, AutomationActionType.CLICK, "Finish", "#finish", "", "Click finish")
            )
            val wfId = repo.saveAutomationWorkflow(
                name = "Cancellable Flow",
                domain = "shop.example.com",
                sourceUrl = "https://shop.example.com",
                type = AutomationType.MANUAL,
                steps = twoSteps
            )

            vm.runWorkflow(wfId, confirmDestructive = true)
            waitUntil { vm.uiState.value.executionState?.status == "RUNNING" }

            // Attempting duplicate concurrent run while RUNNING must be ignored
            vm.runWorkflow(wfId, confirmDestructive = true)
            waitUntil { step2Dispatched.isCompleted }
            val executionsBeforeCancel = db.apexDao().getAutomationExecutionsForWorkflowOnce(wfId)
            assertEquals("Concurrent runWorkflow call must not create a second execution", 1, executionsBeforeCancel.size)
            val startedAtBeforeCancel = executionsBeforeCancel.first().startedAt

            // Now cancel execution
            vm.cancelWorkflowExecution()
            waitUntil { vm.uiState.value.executionState?.status == "CANCELLED" }

            val execAfterCancel = db.apexDao().getAutomationExecutionsForWorkflowOnce(wfId)
            assertEquals(1, execAfterCancel.size)
            val record = execAfterCancel.first()
            assertEquals("CANCELLED", record.status)
            assertNotNull(record.finishedAt)
            assertEquals(startedAtBeforeCancel, record.startedAt)
            assertTrue(record.completedSteps in 0..1)
            assertNotEquals(AutomationToolbarStatus.EXECUTING, vm.uiState.value.automationToolbarStatus)
            assertNotEquals(AutomationToolbarStatus.COMPLETED, vm.uiState.value.automationToolbarStatus)

            // Also verify future schema version fails gracefully without crashing
            val futureId = db.apexDao().upsertAutomationWorkflow(
                AutomationWorkflowEntity(
                    name = "Future Schema Flow",
                    domain = "future.test",
                    sourceUrl = "https://future.test",
                    type = AutomationType.MANUAL.id,
                    instructionsText = "",
                    stepsJson = """{"schemaVersion":99,"steps":[]}""",
                    stepCount = 1,
                    createdAt = 100L,
                    updatedAt = 100L
                )
            )
            vm.runWorkflow(futureId, confirmDestructive = true)
            waitUntil { vm.uiState.value.executionState?.workflowId == futureId }
            val futureExecState = vm.uiState.value.executionState!!
            assertEquals("FAILED", futureExecState.status)
            assertEquals(AutomationErrorCode.UNSUPPORTED_SCHEMA, futureExecState.errorCode)
        } finally {
            db.close()
        }
    }

    @Test
    fun phase2_stepEditingPreservesDestructiveTargetSelector_andUiDisablesRunAndShowsCancelWhileRunning() {
        val destructiveStep = AutomationStep(
            id = "s_pay",
            order = 1,
            actionType = AutomationActionType.SUBMIT,
            targetLabel = "Confirm Purchase",
            selector = "#pay-now",
            value = "",
            description = "Submit checkout payment",
            isDestructive = true
        )

        // Direct codec test: changing actionType and description MUST retain isDestructive = true
        val edited = AutomationStepCodec.applyStepEdit(
            existing = destructiveStep,
            description = "Check order status",
            actionType = AutomationActionType.CLICK,
            value = "",
            targetLabel = destructiveStep.targetLabel,
            selector = destructiveStep.selector
        )
        assertTrue("Destructive flag must remain true after editing action/description", edited.isDestructive)
        assertEquals("Confirm Purchase", edited.targetLabel)
        assertEquals("#pay-now", edited.selector)

        // Escalation test: adding destructive keyword sets isDestructive = true even on non-destructive step
        val safeStep = AutomationStep("s_safe", 1, AutomationActionType.CLICK, "Info", "#info", "", "Open info", isDestructive = false)
        val escalated = AutomationStepCodec.applyStepEdit(
            existing = safeStep,
            description = "Delete account permanently",
            actionType = AutomationActionType.CLICK,
            value = ""
        )
        assertTrue(escalated.isDestructive)

        // Compose UI test: Run buttons disabled while RUNNING, Cancel button triggers callback, step editing dialog preserves fields
        var workflow by mutableStateOf(
            AutomationWorkflowEntity(
                id = 7L,
                name = "Order Flow",
                domain = "shop.example.com",
                sourceUrl = "https://shop.example.com",
                type = AutomationType.RECORDED.id,
                stepsJson = AutomationStepCodec.encode(listOf(destructiveStep)),
                stepCount = 1,
                createdAt = 1000L,
                updatedAt = 2000L
            )
        )
        var executionState by mutableStateOf<WorkflowExecutionState?>(
            WorkflowExecutionState(
                workflowId = 7L,
                workflowName = "Order Flow",
                status = "RUNNING",
                currentStepIndex = 0,
                totalSteps = 1
            )
        )
        var showDetails by mutableStateOf(true)
        var cancelClicked = 0

        composeTestRule.setContent {
            ApexBrowserTheme {
                if (showDetails) {
                    AutomationDetailsScreenOverlay(
                        workflow = workflow,
                        executions = emptyList(),
                        executionState = executionState,
                        onRunWorkflow = {},
                        onRenameWorkflow = { _, _ -> },
                        onUpdateSteps = { _, updatedSteps ->
                            workflow = workflow.copy(
                                stepsJson = AutomationStepCodec.encode(updatedSteps),
                                stepCount = updatedSteps.size
                            )
                        },
                        onAddStep = { _, _, _, _, _ -> },
                        onConfirmDestructiveExecution = {},
                        onCancelDestructiveExecution = {},
                        onClearExecutionBanner = {},
                        onDismiss = {},
                        onCancelExecution = { cancelClicked++ }
                    )
                } else {
                    AutomationWorkspaceScreenOverlay(
                        workflows = listOf(workflow),
                        executions = emptyList(),
                        activeTabDomain = "shop.example.com",
                        activeTabUrl = "https://shop.example.com",
                        executionState = executionState,
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
                        onDismiss = {},
                        onCancelExecution = { cancelClicked++ }
                    )
                }
            }
        }

        // While RUNNING in Details: Run button disabled, Cancel button visible & clicks through
        composeTestRule.onNodeWithTag("details_run_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("automation_cancel_execution_button").assertIsDisplayed().performClick()
        assertEquals(1, cancelClicked)

        // Also check Workspace Run button disabled while RUNNING
        showDetails = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("workflow_run_button_7").assertIsNotEnabled()

        // Clear RUNNING state and test editing the destructive step in Details UI
        showDetails = true
        executionState = null
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("details_run_button").assertIsEnabled()

        composeTestRule.onNodeWithTag("automation_details_list")
            .performScrollToNode(hasTestTag("details_step_edit_1"))
        composeTestRule.onNodeWithTag("details_step_edit_1").performClick()
        composeTestRule.waitForIdle()

        // Edit description & targetLabel/selector inputs are visible
        composeTestRule.onNodeWithTag("edit_step_description_input").performScrollTo().performTextReplacement("Proceed cleanly")
        composeTestRule.onNodeWithTag("edit_step_label_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("edit_step_selector_input").performScrollTo().performTextReplacement("#checkout-submit")
        composeTestRule.onNodeWithTag("edit_step_action_click").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("edit_step_save_button").performClick()
        composeTestRule.waitForIdle()

        val savedSteps = AutomationStepCodec.decode(workflow.stepsJson)
        assertEquals(1, savedSteps.size)
        assertEquals("Proceed cleanly", savedSteps.first().description)
        assertEquals(AutomationActionType.CLICK, savedSteps.first().actionType)
        assertEquals("#checkout-submit", savedSteps.first().selector)
        assertEquals("Confirm Purchase", savedSteps.first().targetLabel)
        assertTrue("Edited step must remain destructive", savedSteps.first().isDestructive)
    }

    @Test
    fun phase2_hudInstructionsMode_disablesExecuteWhileRunning() {
        var cancelClicks = 0
        composeTestRule.setContent {
            ApexBrowserTheme {
                AutomationHudSheet(
                    hudMode = AutomationHudMode.INSTRUCTIONS,
                    pageInspection = PageInspectionState(domain = "example.com", url = "https://example.com"),
                    recordingSession = ActiveRecordingSession(),
                    instructionDraftText = "",
                    instructionDraftSteps = listOf(
                        AutomationStep("i1", 1, AutomationActionType.CLICK, "Go", "#go", "", "Click Go")
                    ),
                    executionState = WorkflowExecutionState(
                        workflowId = 1L,
                        workflowName = "Live Task",
                        status = "RUNNING",
                        currentStepIndex = 0,
                        totalSteps = 1
                    ),
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
                    onCancelExecution = { cancelClicks++ }
                )
            }
        }

        composeTestRule.onNodeWithTag("instructions_execute_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("automation_cancel_execution_button").assertIsDisplayed().performClick()
        assertEquals(1, cancelClicks)
    }

    // -------------------------------------------------------------------------
    // PHASE 3: WEBVIEW NAVIGATION & DOM READINESS SYNCHRONIZATION
    // -------------------------------------------------------------------------

    @Test
    fun phase3_navigationSynchronizer_epochsRedirectsErrorsSpaAndTimeouts() = runBlocking {
        val sync = TabNavigationSynchronizer()

        // 1. Stale & cross-tab events do not satisfy active waiter
        val epoch1 = sync.beginNavigation("tab_1", "https://example.com/step1")
        assertEquals(NavigationLifecyclePhase.REQUESTED, sync.snapshot("tab_1").phase)

        val waitJob = async {
            sync.awaitNavigationSettled("tab_1", minEpoch = epoch1, timeoutMs = 1_500L)
        }
        delay(20L)
        // Other tab finishes -> must NOT settle tab_1
        sync.onPageStarted("tab_other", "https://other.test")
        sync.onPageFinished("tab_other", "https://other.test")
        assertFalse(waitJob.isCompleted)

        // Redirect sequence on tab_1 preserves epoch1 until final onPageFinished
        sync.onPageStarted("tab_1", "https://example.com/step1")
        sync.onPageStarted("tab_1", "https://auth.example.com/sso")
        assertEquals(epoch1, sync.snapshot("tab_1").epoch)
        assertEquals(NavigationLifecyclePhase.LOADING, sync.snapshot("tab_1").phase)
        assertFalse(waitJob.isCompleted)

        sync.onPageFinished("tab_1", "https://example.com/dashboard")
        val completed = waitJob.await()
        assertTrue(completed is NavigationWaitResult.Completed)
        assertEquals("https://example.com/dashboard", (completed as NavigationWaitResult.Completed).finalUrl)

        // 2. Newer beginNavigation rejects pre-existing FINISHED state
        val epoch2 = sync.beginNavigation("tab_1", "https://example.com/step2")
        assertTrue(epoch2 > epoch1)
        val timeoutResult = sync.awaitNavigationSettled("tab_1", minEpoch = epoch2, timeoutMs = 60L)
        assertTrue(timeoutResult is NavigationWaitResult.Timeout)

        // 3. Error state survives Chromium's post-error onPageFinished
        val epoch3 = sync.beginNavigation("tab_1", "https://broken.test")
        sync.onPageStarted("tab_1", "https://broken.test")
        sync.onPageFailed("tab_1", "https://broken.test", "net::ERR_NAME_NOT_RESOLVED")
        sync.onPageFinished("tab_1", "https://broken.test")
        val failRes = sync.awaitNavigationSettled("tab_1", minEpoch = epoch3, timeoutMs = 200L)
        assertTrue(failRes is NavigationWaitResult.Failed)
        assertEquals("net::ERR_NAME_NOT_RESOLVED", (failRes as NavigationWaitResult.Failed).reason)

        // 4. SPA URL commit updates urlCommitSeq and activeUrl
        sync.onPageFinished("tab_spa", "https://spa.test/#/home")
        val beforeSeq = sync.snapshot("tab_spa").urlCommitSeq
        sync.onUrlCommitted("tab_spa", "https://spa.test/#/checkout", isReload = false)
        val afterSpa = sync.snapshot("tab_spa")
        assertEquals(beforeSeq + 1, afterSpa.urlCommitSeq)
        assertEquals("https://spa.test/#/checkout", afterSpa.activeUrl)

        // 5. Tab close aborts suspended waiter immediately
        val epochClose = sync.beginNavigation("tab_close", "https://slow.test")
        val closeWait = async {
            sync.awaitNavigationSettled("tab_close", minEpoch = epochClose, timeoutMs = 2_000L)
        }
        delay(20L)
        sync.onTabClosed("tab_close")
        val closeRes = closeWait.await()
        assertTrue(closeRes is NavigationWaitResult.Failed)
    }

    @Test
    fun phase3_viewModelOnPageLoadFailedAlwaysNotifiesSynchronizerEvenWhenOnline_andExecuteWorkflowWaitsForNavigation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val prefsRepo = UserPreferencesRepository(context)
        val sharedPrefs = context.getSharedPreferences("apex_phase3_sync_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().clear().commit()
        val repo = BrowserRepository(db.apexDao(), prefsRepo, sharedPrefs)

        val fastSyncEngine = AutomationEngine(
            jsEvalTimeoutMs = 1_500L,
            navTimeoutMs = 1_500L,
            domReadyTimeoutMs = 800L,
            domStabilityPollMs = 20L
        )
        val vm = BrowserViewModel(
            repository = repo,
            fastDownloadEngine = FastDownloadEngine(context, repo),
            automationEngine = fastSyncEngine
        )

        try {
            // Confirm online error callback still notifies navigationSynchronizer immediately
            val tId = vm.uiState.value.activeTabId
            assertTrue(vm.uiState.value.isDeviceOnline)
            val errEpoch = vm.navigationSynchronizer.beginNavigation(tId, "https://fail.example.test")
            vm.onWebViewPageLoadFailedWithReason(tId, "https://fail.example.test", "ERR_CONNECTION_REFUSED")
            val errWait = vm.navigationSynchronizer.awaitNavigationSettled(tId, errEpoch, 200L)
            assertTrue(errWait is NavigationWaitResult.Failed)
            assertEquals("ERR_CONNECTION_REFUSED", (errWait as NavigationWaitResult.Failed).reason)

            // Execute workflow with NAVIGATE -> DOM readiness -> CLICK without any fixed sleep dependency
            val sync = TabNavigationSynchronizer()
            var navigatedDest = ""
            var domReadyReadyFlag = false
            var clickExecutedBeforeNavFinished = false
            var navFinishedFired = false

            val wv = FakeEvalWebView(context) { script, cb ->
                if (script.contains("readyState")) {
                    val stateStr = if (domReadyReadyFlag) "complete" else "loading"
                    cb?.onReceiveValue(
                        """{"readyState":"$stateStr","url":"https://dest.test","shape":"5:120","targetReady":$domReadyReadyFlag}"""
                    )
                } else {
                    if (!navFinishedFired) {
                        clickExecutedBeforeNavFinished = true
                    }
                    cb?.onReceiveValue("""{"ok":true,"code":"NONE","msg":"Activated button"}""")
                }
            }

            val wf = AutomationWorkflowEntity(
                id = 42L,
                name = "Sync Nav Flow",
                domain = "dest.test",
                sourceUrl = "https://dest.test",
                type = AutomationType.MANUAL.id,
                stepsJson = "[]",
                stepCount = 2,
                createdAt = 1L,
                updatedAt = 1L
            )
            val steps = listOf(
                AutomationStep("n1", 1, AutomationActionType.NAVIGATE, "https://dest.test", "", "https://dest.test", "Open dest"),
                AutomationStep("c1", 2, AutomationActionType.CLICK, "Submit", "#submit", "", "Click Submit")
            )

            val runJob = async {
                fastSyncEngine.executeWorkflow(
                    workflow = wf,
                    steps = steps,
                    currentTabUrl = "https://start.test",
                    webViewProvider = { wv },
                    onNavigateUrl = { dest ->
                        navigatedDest = dest
                        sync.beginNavigation("tab_sync", dest)
                    },
                    onStepProgress = { _, _ -> },
                    tabId = "tab_sync",
                    navigationSynchronizer = sync
                )
            }

            delay(40L)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertEquals("https://dest.test", navigatedDest)
            assertFalse("Workflow must still wait for navigation finished", runJob.isCompleted)

            sync.onPageStarted("tab_sync", "https://dest.test")
            delay(30L)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertFalse("Workflow must wait while LOADING", runJob.isCompleted)

            navFinishedFired = true
            domReadyReadyFlag = true
            sync.onPageFinished("tab_sync", "https://dest.test")

            val deadline = System.currentTimeMillis() + 2_000L
            while (!runJob.isCompleted && System.currentTimeMillis() < deadline) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(20L)
            }
            val outcome = runJob.await()
            assertEquals("COMPLETED", outcome.status)
            assertEquals(2, outcome.completedSteps)
            assertFalse("Step 2 must not execute before navigation finished", clickExecutedBeforeNavFinished)
        } finally {
            db.close()
        }
    }

    private suspend fun waitUntil(timeoutMs: Long = 3_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate() && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(20L, TimeUnit.MILLISECONDS)
            delay(20L)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Timed out waiting for condition", predicate())
    }
}
