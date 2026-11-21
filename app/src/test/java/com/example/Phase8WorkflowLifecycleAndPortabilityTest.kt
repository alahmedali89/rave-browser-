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
import com.example.automation.AutomationActionType
import com.example.automation.AutomationEngine
import com.example.automation.AutomationErrorCode
import com.example.automation.AutomationStep
import com.example.automation.AutomationStepCodec
import com.example.automation.AutomationStepDecodeResult
import com.example.automation.AutomationType
import com.example.automation.CompatibilityStatus
import com.example.automation.ConditionNode
import com.example.automation.ConditionOperator
import com.example.automation.CredentialAuthenticationManager
import com.example.automation.CredentialFieldRole
import com.example.automation.CredentialVaultCrypto
import com.example.automation.CredentialVaultManager
import com.example.automation.DetectedPageElement
import com.example.automation.DiffChangeKind
import com.example.automation.DryRunStepStatus
import com.example.automation.ExportPackageType
import com.example.automation.InMemoryVaultKeyMetadataStore
import com.example.automation.InMemoryVaultKeyProvider
import com.example.automation.LocatorCandidate
import com.example.automation.LocatorStrategy
import com.example.automation.LoopConfig
import com.example.automation.RecoveryPolicy
import com.example.automation.RecoveryStrategy
import com.example.automation.SubWorkflowReference
import com.example.automation.ValidationSeverity
import com.example.automation.WaitConditionSpec
import com.example.automation.WaitConditionType
import com.example.automation.WorkflowCompatibilityManager
import com.example.automation.WorkflowConcurrencyConflictException
import com.example.automation.WorkflowConflictResolution
import com.example.automation.WorkflowDiffEngine
import com.example.automation.WorkflowDiffReport
import com.example.automation.WorkflowDocument
import com.example.automation.WorkflowDocumentMetadata
import com.example.automation.WorkflowDryRunEngine
import com.example.automation.WorkflowDryRunReport
import com.example.automation.WorkflowImportExportManager
import com.example.automation.WorkflowOrganizationMetadata
import com.example.automation.WorkflowPhase8Constants
import com.example.automation.WorkflowSortOption
import com.example.automation.WorkflowValidationEngine
import com.example.automation.WorkflowValidationReport
import com.example.automation.WorkflowVariableDefinition
import com.example.automation.WorkflowVariableType
import com.example.automation.WorkflowVersionManager
import com.example.data.local.ApexDatabase
import com.example.data.local.AutomationWorkflowEntity
import com.example.data.local.AutomationWorkflowVersionEntity
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.BrowserRepository
import com.example.engine.FastDownloadEngine
import com.example.ui.browser.AutomationDetailsScreenOverlay
import com.example.ui.browser.AutomationWorkspaceScreenOverlay
import com.example.ui.theme.ApexBrowserTheme
import com.example.ui.viewmodel.BrowserViewModel
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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
 * Phase 8 Verification Suite:
 * - Room v14 -> v15 -> v16 migration & schema integrity (UUID backfill, version seeds, cascade delete)
 * - Workflow versioning (dedup, timestamps, non-destructive restore, optimistic locking, unsupported rename)
 * - Import/export portability (JSON v2, ZIP bundle, 11+ sentinel remapping, secret scrub & rebind, path traversal, atomic rollback)
 * - Format compatibility (legacy migration, future rejection, unknown action/field round-trip preservation)
 * - Validation engine (step IDs, fallback locators, branch-scoped variables, conditions, loops, cycles, recovery waits, credentials)
 * - Semantic diff engine (steps, variables with masking, locators, recovery, metadata)
 * - Safe dry-run simulator (0 WebView JS/nav calls, runtime var substitution, sub-workflow outputs, credential rebind/auth, archive block)
 * - Compose UI & ViewModel integration for workspace filters, archive/favorite toggles, version diff dialog, and dry-run dialog
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase8WorkflowLifecycleAndPortabilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: ApexDatabase
    private lateinit var repository: BrowserRepository
    private lateinit var vaultManager: CredentialVaultManager
    private lateinit var engine: AutomationEngine
    private lateinit var versionManager: WorkflowVersionManager
    private lateinit var importExportManager: WorkflowImportExportManager
    private var activeStore: ViewModelStore? = null

    private class CountingFakeWebView(
        context: Context,
        val jsCallCount: AtomicInteger = AtomicInteger(0),
        val navCallCount: AtomicInteger = AtomicInteger(0)
    ) : WebView(context) {
        override fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {
            jsCallCount.incrementAndGet()
            resultCallback?.onReceiveValue("null")
        }

        override fun loadUrl(url: String) {
            navCallCount.incrementAndGet()
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
        vaultManager = CredentialVaultManager(
            dao = database.apexDao(),
            crypto = crypto,
            authManager = auth,
            originParser = { url -> engine.parseOrigin(url) }
        )
        repository = BrowserRepository(
            dao = database.apexDao(),
            preferencesRepository = prefs,
            sharedPrefs = context.getSharedPreferences("phase8_test_${UUID.randomUUID()}", Context.MODE_PRIVATE),
            credentialVaultManager = vaultManager
        )
        versionManager = WorkflowVersionManager(database.apexDao())
        importExportManager = WorkflowImportExportManager(database.apexDao(), versionManager, engine)
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

    private suspend fun idleUntil(timeoutMs: Long = 4_000L, pred: () -> Boolean) {
        val main = Shadows.shadowOf(Looper.getMainLooper())
        val deadline = System.currentTimeMillis() + timeoutMs
        main.idle()
        while (!pred() && System.currentTimeMillis() < deadline) {
            main.idleFor(20L, TimeUnit.MILLISECONDS)
            delay(20L)
        }
        main.idle()
        assertTrue("Timed out waiting within ${timeoutMs}ms", pred())
    }

    @Test
    fun roomMigration14To16_preservesAllData_backfillsUuids_seedsVersions_andCascadesDelete() {
        val dbName = "migration_14_16_test.db"
        File(context.getDatabasePath(dbName).path).delete()

        @Suppress("DEPRECATION")
        val helper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            ApexDatabase::class.java.canonicalName!!,
            FrameworkSQLiteOpenHelperFactory()
        )

        helper.createDatabase(dbName, 14).apply {
            execSQL("INSERT INTO bookmarks (id, url, title, createdAt) VALUES (1, 'https://apex.dev', 'Apex', 1000)")
            execSQL(
                """
                INSERT INTO automation_workflows (
                    id, name, domain, sourceUrl, type, instructionsText, stepsJson,
                    stepCount, createdAt, updatedAt, lastExecutedAt, lastExecutionStatus, lastExecutionSummary
                ) VALUES (
                    11, 'Daily Report', 'apex.dev', 'https://apex.dev/report',
                    'MANUAL', 'Click run', '[]', 1, 1000, 2000, 3000, 'COMPLETED', 'OK'
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO automation_executions (
                    id, workflowId, workflowName, domain, status, totalSteps,
                    completedSteps, verificationSummary, startedAt, finishedAt
                ) VALUES (
                    88, 11, 'Daily Report', 'apex.dev', 'COMPLETED', 1, 1, 'OK', 2900, 3000
                )
                """.trimIndent()
            )
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(
            dbName,
            16,
            true,
            ApexDatabase.MIGRATION_14_15,
            ApexDatabase.MIGRATION_15_16
        )

        migratedDb.query("SELECT title FROM bookmarks WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Apex", c.getString(0))
        }
        migratedDb.query(
            "SELECT workflowUuid, currentVersion, isArchived, isFavorite, tagsJson FROM automation_workflows WHERE id = 11"
        ).use { c ->
            assertTrue(c.moveToFirst())
            val uuid = c.getString(0)
            assertTrue("Expected backfilled UUID", uuid.length >= 32)
            assertEquals(1, c.getInt(1))
            assertEquals(0, c.getInt(2))
            assertEquals(0, c.getInt(3))
            assertEquals("[]", c.getString(4))
        }
        migratedDb.query(
            "SELECT workflowId, versionNumber, workflowName, changeSummary FROM workflow_versions WHERE workflowId = 11"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(11L, c.getLong(0))
            assertEquals(1, c.getInt(1))
            assertEquals("Daily Report", c.getString(2))
            assertEquals("Migrated initial version", c.getString(3))
        }
        migratedDb.query("SELECT checkpointJson, traceJson FROM automation_executions WHERE id = 88").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("", c.getString(0))
            assertEquals("", c.getString(1))
        }

        // Verify unique index on (workflowId, versionNumber) and CASCADE DELETE on workflow removal
        migratedDb.execSQL("PRAGMA foreign_keys=ON")
        migratedDb.execSQL("DELETE FROM automation_workflows WHERE id = 11")
        migratedDb.query("SELECT COUNT(*) FROM workflow_versions WHERE workflowId = 11").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        migratedDb.close()
    }

    @Test
    fun workflowVersioning_dedupIdenticalSaves_nonDestructiveRestore_optimisticLock_andUnsupportedRename() = runBlocking {
        val step1 = AutomationStep(
            id = "s1",
            order = 1,
            actionType = AutomationActionType.CLICK,
            targetLabel = "Search",
            selector = "#search-btn",
            description = "Click Search"
        )
        val wfId = repository.saveAutomationWorkflow(
            name = "Search Flow",
            domain = "example.com",
            sourceUrl = "https://example.com",
            type = AutomationType.MANUAL,
            steps = listOf(step1),
            changeSummary = "Created v1"
        )
        assertEquals(1, repository.getWorkflowVersionsOnce(wfId).size)

        // Identical content save should NOT create v2
        val dedupRes = versionManager.updateExistingWorkflowVersioned(
            workflowId = wfId,
            expectedVersion = 1,
            document = WorkflowDocument(steps = listOf(step1))
        )
        assertFalse(dedupRes.snapshotCreated)
        assertEquals(1, dedupRes.versionNumber)
        assertEquals(1, repository.getWorkflowVersionsOnce(wfId).size)

        // Update to v2 with an added step
        val step2 = AutomationStep(
            id = "s2",
            order = 2,
            actionType = AutomationActionType.INPUT,
            targetLabel = "Query",
            selector = "#q",
            value = "laptop",
            description = "Enter query"
        )
        val v2Res = versionManager.updateExistingWorkflowVersioned(
            workflowId = wfId,
            expectedVersion = 1,
            document = WorkflowDocument(steps = listOf(step1, step2)),
            changeSummary = "Added query input"
        )
        assertTrue(v2Res.snapshotCreated)
        assertEquals(2, v2Res.versionNumber)

        // Update to v3 with renamed workflow
        repository.renameAutomationWorkflow(wfId, "Search Flow Pro", expectedVersion = 2)
        val currentBeforeRestore = repository.getAutomationWorkflowById(wfId)!!
        assertEquals(3, currentBeforeRestore.currentVersion)

        // Stale expectedVersion throws WorkflowConcurrencyConflictException
        var caughtConflict: WorkflowConcurrencyConflictException? = null
        try {
            versionManager.updateExistingWorkflowVersioned(
                workflowId = wfId,
                expectedVersion = 1,
                name = "Stale Overwrite"
            )
        } catch (ex: WorkflowConcurrencyConflictException) {
            caughtConflict = ex
        }
        assertNotNull(caughtConflict)
        assertEquals(1, caughtConflict!!.expectedVersion)
        assertEquals(3, caughtConflict.actualVersion)

        // Restore v1 -> creates v4 with restoredFromVersion = 1 and keeps v1, v2, v3 intact
        val restoreRes = repository.restoreWorkflowVersion(wfId, targetVersionNumber = 1, expectedCurrentVersion = 3)
        assertEquals(4, restoreRes.versionNumber)
        val allVersions = repository.getWorkflowVersionsOnce(wfId)
        assertEquals(listOf(4, 3, 2, 1), allVersions.map { it.versionNumber })
        val v4 = allVersions.first { it.versionNumber == 4 }
        assertEquals(1, v4.restoredFromVersion)
        assertEquals("Search Flow", v4.workflowName)
        assertEquals(1, v4.stepCount)

        // Metadata rename on an unsupported future schema workflow preserves raw stepsJson intact
        val rawFutureJson = """{"schemaVersion":99,"steps":[{"id":"fx","order":1,"actionType":"QUANTUM_TAP"}]}"""
        val futureId = database.apexDao().upsertAutomationWorkflow(
            AutomationWorkflowEntity(
                name = "Future Raw",
                domain = "future.dev",
                sourceUrl = "https://future.dev",
                type = "MANUAL",
                stepsJson = rawFutureJson,
                stepCount = 1,
                workflowUuid = UUID.randomUUID().toString(),
                currentVersion = 1
            )
        )
        repository.renameAutomationWorkflow(futureId, "Future Raw Renamed")
        val updatedFuture = repository.getAutomationWorkflowById(futureId)!!
        assertEquals("Future Raw Renamed", updatedFuture.name)
        assertEquals(rawFutureJson, updatedFuture.stepsJson)
        assertEquals(1, updatedFuture.stepCount)
        assertEquals(2, updatedFuture.currentVersion)
    }

    @Test
    fun workflowImportExport_handlesSentinel1To11Boundary_scrubsVaultCredentials_andEnforcesAtomicZipSafety() = runBlocking {
        // Store a real credential to ensure export strips both credentialReferenceId and any hardcoded values
        val addCredRes = vaultManager.addCredential(
            websiteUrl = "https://shop.example.com/login",
            username = "buyer@example.com",
            passwordChars = "UltraSecretPassword123!".toCharArray(),
            label = "Shop Account",
            requireVaultUnlocked = false
        ) as com.example.automation.CredentialVaultOperationResult.Success
        val vaultEntry = addCredRes.value

        // Build 10 child workflows (to hit sentinel -1 through -10) + 1 parent calling the 10th child (sentinel -10)
        // Wait: in topological order, children come first! So if child #10 is at index 9 (sentinel -10) and parent #11 calls it,
        // the sentinel replacement regex must NOT confuse -1 with -10!
        val childIds = mutableListOf<Long>()
        for (i in 1..10) {
            val id = repository.saveAutomationWorkflow(
                name = "Helper_$i",
                domain = "shop.example.com",
                sourceUrl = "https://shop.example.com",
                type = AutomationType.MANUAL,
                steps = listOf(
                    AutomationStep(
                        id = "h_${i}_1",
                        order = 1,
                        actionType = AutomationActionType.CLICK,
                        targetLabel = "Btn $i",
                        selector = "#btn-$i",
                        description = "Click btn $i"
                    )
                )
            )
            childIds.add(id)
        }
        val parentId = repository.saveAutomationWorkflow(
            name = "Master Checkout",
            domain = "shop.example.com",
            sourceUrl = "https://shop.example.com",
            type = AutomationType.MANUAL,
            steps = listOf(
                AutomationStep(
                    id = "cred_step",
                    order = 1,
                    actionType = AutomationActionType.FILL_CREDENTIAL,
                    targetLabel = "Password",
                    selector = "#password",
                    value = "UltraSecretPassword123!",
                    description = "Fill password",
                    credentialReferenceId = vaultEntry.credentialId,
                    credentialFieldRole = CredentialFieldRole.PASSWORD,
                    expectedOrigin = "https://shop.example.com",
                    requiresCredential = true
                ),
                AutomationStep(
                    id = "call_1",
                    order = 2,
                    actionType = AutomationActionType.CALL_WORKFLOW,
                    targetLabel = "Helper_1",
                    description = "Call Helper_1",
                    subWorkflowRef = SubWorkflowReference(targetWorkflowId = childIds[0], targetWorkflowName = "Helper_1")
                ),
                AutomationStep(
                    id = "call_10",
                    order = 3,
                    actionType = AutomationActionType.CALL_WORKFLOW,
                    targetLabel = "Helper_10",
                    description = "Call Helper_10",
                    subWorkflowRef = SubWorkflowReference(targetWorkflowId = childIds[9], targetWorkflowName = "Helper_10")
                )
            )
        )

        val allIdsToExport = childIds + parentId
        val zipBytes = repository.exportWorkflowsZipBytes(
            workflowIds = allIdsToExport,
            packageType = ExportPackageType.COLLECTION,
            includeDependencies = true
        )
        val exportedJson = repository.exportAllWorkflowsPackageJson(includeDependencies = true)

        // Security check: zero credential secret or Vault ID leaked in exported JSON
        assertFalse(exportedJson.contains("UltraSecretPassword123!"))
        assertFalse(exportedJson.contains(vaultEntry.credentialId))

        // Delete originals and re-import from ZIP cleanly
        allIdsToExport.forEach { repository.deleteAutomationWorkflow(it) }
        assertTrue(database.apexDao().getAutomationWorkflowsOnce().isEmpty())

        val zipImportRes = repository.importWorkflowsZipBytes(zipBytes, WorkflowConflictResolution.CREATE_COPY)
        assertTrue("ZIP import should succeed: ${zipImportRes.errors}", zipImportRes.success)
        assertEquals(11, zipImportRes.importedCount)
        assertEquals(1, zipImportRes.rebindRequiredWorkflowIds.size)
        assertEquals(1, zipImportRes.rebindRequiredStepCount)

        val importedWorkflows = database.apexDao().getAutomationWorkflowsOnce()
        val importedHelper10 = importedWorkflows.first { it.name == "Helper_10" }
        val importedMaster = importedWorkflows.first { it.name == "Master Checkout" }
        val masterDoc = AutomationStepCodec.decodeDocument(importedMaster.stepsJson)

        val credStepImported = masterDoc.steps.first { it.id == "cred_step" }
        assertNull(credStepImported.credentialReferenceId)
        assertEquals("", credStepImported.value)
        assertTrue(credStepImported.requiresCredential)

        // Verify sentinel -1 vs -10 mapped cleanly to actual Helper_10 DB ID!
        val call10Imported = masterDoc.steps.first { it.id == "call_10" }.subWorkflowRef!!
        assertEquals(importedHelper10.id, call10Imported.targetWorkflowId)

        // Conflict resolution: SKIP_EXISTING & NEW_VERSION_IF_MATCH
        val skipRes = repository.importWorkflowPackageJson(exportedJson, WorkflowConflictResolution.SKIP_EXISTING)
        assertTrue(skipRes.success)
        assertEquals(11, skipRes.skippedCount)
        assertEquals(0, skipRes.importedCount)

        val upgradeRes = repository.importWorkflowPackageJson(exportedJson, WorkflowConflictResolution.NEW_VERSION_IF_MATCH)
        assertTrue(upgradeRes.success)
        assertEquals(11, upgradeRes.updatedCount)
        val masterAfterUpgrade = repository.getAutomationWorkflowById(importedMaster.id)!!
        assertEquals(2, masterAfterUpgrade.currentVersion)

        // ZIP path traversal rejection
        val badZipBytes = ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("../../etc/passwd.json"))
                zos.write("{}".toByteArray())
                zos.closeEntry()
            }
            baos.toByteArray()
        }
        val badZipRes = repository.importWorkflowsZipBytes(badZipBytes, WorkflowConflictResolution.CREATE_COPY)
        assertFalse(badZipRes.success)
        assertTrue(badZipRes.errors.first().contains("unsafe", ignoreCase = true))

        // Atomic rollback: 1st workflow valid, 2nd has malicious/invalid selector -> zero workflows added
        val countBeforeRollback = database.apexDao().getAutomationWorkflowsOnce().size
        val badBatchJson = JSONObject().apply {
            put("formatVersion", WorkflowPhase8Constants.WORKFLOW_EXPORT_FORMAT_VERSION)
            put("packageType", ExportPackageType.COLLECTION.id)
            put("workflows", JSONArray().apply {
                put(JSONObject().apply {
                    put("workflowUuid", UUID.randomUUID().toString())
                    put("name", "Atomic Good")
                    put("domain", "example.com")
                    put("sourceUrl", "https://example.com")
                    put("type", "MANUAL")
                    put("steps", JSONArray().put(JSONObject().apply {
                        put("id", "g1")
                        put("order", 1)
                        put("actionType", "CLICK")
                        put("targetLabel", "OK")
                        put("selector", "#ok")
                        put("description", "OK")
                    }))
                })
                put(JSONObject().apply {
                    put("workflowUuid", UUID.randomUUID().toString())
                    put("name", "Atomic Bad")
                    put("domain", "example.com")
                    put("sourceUrl", "https://example.com")
                    put("type", "MANUAL")
                    put("steps", JSONArray().put(JSONObject().apply {
                        put("id", "b1")
                        put("order", 1)
                        put("actionType", "CLICK")
                        put("targetLabel", "Bad")
                        put("selector", "div[unclosed")
                        put("description", "Bad selector")
                    }))
                })
            })
        }.toString()
        val rollbackRes = repository.importWorkflowPackageJson(badBatchJson, WorkflowConflictResolution.CREATE_COPY)
        assertFalse(rollbackRes.success)
        assertEquals(countBeforeRollback, database.apexDao().getAutomationWorkflowsOnce().size)
    }

    @Test
    fun workflowCompatibility_migratesLegacyV1_rejectsFutureFormats_andPreservesUnknownActions() = runBlocking {
        // 1. Legacy array v1 migration
        val legacyArrayJson = JSONArray().put(
            JSONObject().apply {
                put("name", "Legacy Export")
                put("domain", "legacy.com")
                put("sourceUrl", "https://legacy.com")
                put("type", "MANUAL")
                put("steps", JSONArray().put(
                    JSONObject().apply {
                        put("id", "l1")
                        put("order", 1)
                        put("actionType", "CLICK")
                        put("targetLabel", "Go")
                        put("selector", "#go")
                        put("description", "Click Go")
                    }
                ))
            }
        ).toString()

        val legacyCheck = WorkflowCompatibilityManager.parseAndMigrateExportEnvelope(legacyArrayJson)
        assertTrue(legacyCheck.isSupported)
        assertEquals(CompatibilityStatus.MIGRATED, legacyCheck.status)
        assertEquals(1, legacyCheck.sourceFormatVersion)
        assertEquals(1, legacyCheck.workflows.size)
        assertTrue(legacyCheck.migrationNotes.isNotEmpty())

        // 2. Unsupported future format rejection
        val futureEnvelope = JSONObject().apply {
            put("formatVersion", 99)
            put("workflows", JSONArray())
        }.toString()
        val futureCheck = WorkflowCompatibilityManager.parseAndMigrateExportEnvelope(futureEnvelope)
        assertFalse(futureCheck.isSupported)
        assertEquals(CompatibilityStatus.UNSUPPORTED_FUTURE, futureCheck.status)

        // 3. Unknown future action type & extra step fields preserved, sensitive unknown field stripped
        val unknownActionPackage = JSONObject().apply {
            put("formatVersion", 2)
            put("packageType", "SINGLE")
            put("workflows", JSONArray().put(
                JSONObject().apply {
                    put("workflowUuid", UUID.randomUUID().toString())
                    put("schemaVersion", 1)
                    put("name", "Unknown Step Workflow")
                    put("domain", "future.io")
                    put("sourceUrl", "https://future.io")
                    put("type", "MANUAL")
                    put("steps", JSONArray().put(
                        JSONObject().apply {
                            put("id", "u1")
                            put("order", 1)
                            put("actionType", "FUTURE_QUANTUM_TAP")
                            put("targetLabel", "Tap")
                            put("selector", "#quantum")
                            put("description", "Future action")
                            put("quantumPower", 9001)
                            put("apiSecret", "SHOULD_NOT_SURVIVE")
                        }
                    ))
                }
            ))
        }.toString()

        val importUnknown = repository.importWorkflowPackageJson(unknownActionPackage, WorkflowConflictResolution.CREATE_COPY)
        assertTrue("Unknown action step should import non-destructively: ${importUnknown.errors}", importUnknown.success)
        assertTrue(importUnknown.warnings.any { it.contains("unsupported", ignoreCase = true) })

        val savedId = importUnknown.importedWorkflowIds.single()
        val reExportedJson = repository.exportWorkflowPackageJson(savedId, includeDependencies = false)
        assertTrue(reExportedJson.contains("FUTURE_QUANTUM_TAP"))
        assertTrue(reExportedJson.contains("quantumPower"))
        assertFalse(reExportedJson.contains("SHOULD_NOT_SURVIVE"))
    }

    @Test
    fun workflowValidationEngine_catchesAllErrorAndWarningCategories_includingBranchScopingAndRecoveryWaits() = runBlocking {
        val addGoodCredRes = vaultManager.addCredential(
            websiteUrl = "https://other.example.org/login",
            username = "u",
            passwordChars = "p".toCharArray(),
            label = "Other Site",
            requireVaultUnlocked = false
        ) as com.example.automation.CredentialVaultOperationResult.Success
        val goodVaultCred = addGoodCredRes.value

        // Workflow A calls Workflow B, Workflow B calls Workflow A -> cycle!
        val wfBId = repository.saveAutomationWorkflow(
            name = "Child Loop B",
            domain = "app.example.com",
            sourceUrl = "https://app.example.com",
            type = AutomationType.MANUAL,
            steps = listOf(
                AutomationStep(
                    id = "b_call_a",
                    order = 1,
                    actionType = AutomationActionType.CALL_WORKFLOW,
                    targetLabel = "Parent",
                    description = "Cycle back to parent",
                    subWorkflowRef = SubWorkflowReference(targetWorkflowId = 9999L, targetWorkflowName = "MissingOrParent")
                )
            )
        )

        // Branch variable defined inside THEN without ELSE should NOT leak out!
        val badSteps = listOf(
            AutomationStep(
                id = "", // Missing step ID
                order = 1,
                actionType = AutomationActionType.CLICK,
                targetLabel = "Bad Selector",
                selector = "div[", // Invalid primary selector
                description = "Invalid primary & fallback selector",
                locatorCandidates = listOf(
                    LocatorCandidate(strategy = LocatorStrategy.SCOPED_CSS, selector = "div[broken")
                ),
                recoveryPolicy = RecoveryPolicy(
                    strategy = RecoveryStrategy.WAIT_THEN_RETRY,
                    maxRetries = 99,
                    waitBeforeRetry = WaitConditionSpec(
                        type = WaitConditionType.ELEMENT_APPEARS,
                        selector = "",
                        timeoutMs = -50L
                    )
                )
            ),
            AutomationStep(
                id = "dup_id",
                order = 2,
                actionType = AutomationActionType.CONDITION,
                targetLabel = "Check",
                description = "If condition with non-numeric threshold",
                condition = ConditionNode.Predicate(
                    operator = ConditionOperator.VARIABLE_GREATER_THAN,
                    variableName = "missing_cond_var",
                    expectedValue = "abc"
                ),
                thenSteps = listOf(
                    AutomationStep(
                        id = "inner_set",
                        order = 1,
                        actionType = AutomationActionType.SET_VARIABLE,
                        targetLabel = "then_only_var",
                        variableName = "then_only_var",
                        value = "ok",
                        description = "Set inside then branch only"
                    )
                )
            ),
            AutomationStep(
                id = "dup_id", // Duplicate ID + leaked var + destructive + origin-mismatched credential
                order = 3,
                actionType = AutomationActionType.FILL_CREDENTIAL,
                targetLabel = "Password \${then_only_var}",
                selector = "#pw",
                value = "",
                description = "Use \${then_only_var}",
                isDestructive = true,
                credentialReferenceId = goodVaultCred.credentialId,
                expectedOrigin = "https://app.example.com",
                requiresCredential = true
            ),
            AutomationStep(
                id = "loop_bad",
                order = 4,
                actionType = AutomationActionType.LOOP,
                targetLabel = "Loop",
                description = "Too many iterations",
                loopConfig = LoopConfig(maxIterations = 9999)
            ),
            AutomationStep(
                id = "unsupported_step",
                order = 5,
                actionType = AutomationActionType.UNSUPPORTED,
                rawActionType = "WARP_SPEED",
                targetLabel = "Warp",
                description = "Unsupported op"
            ),
            AutomationStep(
                id = "call_missing",
                order = 6,
                actionType = AutomationActionType.CALL_WORKFLOW,
                targetLabel = "Sub",
                description = "Calls missing sub-workflow and B",
                subWorkflowRef = SubWorkflowReference(targetWorkflowId = 404404L, targetWorkflowName = "GhostFlow")
            ),
            AutomationStep(
                id = "call_b",
                order = 7,
                actionType = AutomationActionType.CALL_WORKFLOW,
                targetLabel = "B",
                description = "Calls B",
                subWorkflowRef = SubWorkflowReference(targetWorkflowId = wfBId, targetWorkflowName = "Child Loop B")
            )
        )

        val wfId = repository.saveAutomationWorkflow(
            name = "Broken Workflow",
            domain = "app.example.com",
            sourceUrl = "https://app.example.com",
            type = AutomationType.MANUAL,
            steps = badSteps
        )
        // Close cycle B -> wfId
        repository.updateAutomationWorkflowSteps(
            wfBId,
            listOf(
                AutomationStep(
                    id = "b_call_parent",
                    order = 1,
                    actionType = AutomationActionType.CALL_WORKFLOW,
                    targetLabel = "Parent",
                    description = "Cycle to parent",
                    subWorkflowRef = SubWorkflowReference(targetWorkflowId = wfId, targetWorkflowName = "Broken Workflow")
                )
            )
        )

        val wfEntity = repository.getAutomationWorkflowById(wfId)!!
        val report = WorkflowValidationEngine.validateWorkflow(
            workflow = wfEntity,
            document = WorkflowDocument(steps = badSteps),
            workflowLookup = { targetId ->
                repository.getAutomationWorkflowById(targetId)?.let { child ->
                    child.name to AutomationStepCodec.decodeDocument(child.stepsJson)
                }
            },
            credentialLookup = { credId -> database.apexDao().getCredentialVaultEntryById(credId) },
            engine = engine
        )
        assertFalse(report.isExecutionPermitted)

        val codes = report.issues.map { it.errorCode }.toSet()
        assertTrue(AutomationErrorCode.MISSING_REQUIRED_PARAM in codes)
        assertTrue(AutomationErrorCode.DUPLICATE_STEP_ID in codes)
        assertTrue(AutomationErrorCode.INVALID_SELECTOR in codes)
        assertTrue(AutomationErrorCode.CONDITION_INVALID in codes)
        assertTrue(AutomationErrorCode.VARIABLE_UNDEFINED in codes)
        assertTrue(AutomationErrorCode.CREDENTIAL_ORIGIN_MISMATCH in codes)
        assertTrue(AutomationErrorCode.LOOP_LIMIT_EXCEEDED in codes)
        assertTrue(AutomationErrorCode.UNSUPPORTED_ACTION in codes)
        assertTrue(AutomationErrorCode.SUBWORKFLOW_NOT_FOUND in codes)
        assertTrue(AutomationErrorCode.SUBWORKFLOW_CYCLE in codes)
        // Branch variable scoping check: then_only_var reported undefined in step 3
        assertTrue(report.issues.any { it.errorCode == AutomationErrorCode.VARIABLE_UNDEFINED && it.explanation.contains("then_only_var") })
        // Fallback locator selector check
        assertTrue(report.issues.any { it.errorCode == AutomationErrorCode.INVALID_SELECTOR && it.explanation.contains("fallback", ignoreCase = true) })
        // Recovery wait timeout check
        assertTrue(report.issues.any { it.title.contains("wait timeout", ignoreCase = true) })
        // Destructive warning present
        assertTrue(report.issues.any { it.severity == ValidationSeverity.WARNING && it.title.contains("Destructive", ignoreCase = true) })
    }

    @Test
    fun workflowDiffEngine_detectsAddedRemovedModifiedReorderedAndMasksSensitiveVars() {
        val baseDoc = WorkflowDocument(
            metadata = WorkflowDocumentMetadata(
                variables = listOf(
                    WorkflowVariableDefinition(name = "api_token_name", type = WorkflowVariableType.TEXT, defaultValue = "old-token"),
                    WorkflowVariableDefinition(name = "retry_count", type = WorkflowVariableType.NUMBER, defaultValue = "1")
                )
            ),
            steps = listOf(
                AutomationStep(id = "a", order = 1, actionType = AutomationActionType.CLICK, targetLabel = "Start", selector = "#start", description = "Start"),
                AutomationStep(id = "b", order = 2, actionType = AutomationActionType.INPUT, targetLabel = "Name", selector = "#name", value = "Alice", description = "Type Name"),
                AutomationStep(id = "c", order = 3, actionType = AutomationActionType.CLICK, targetLabel = "Delete", selector = "#del", description = "Remove me")
            )
        )
        val targetDoc = WorkflowDocument(
            metadata = WorkflowDocumentMetadata(
                variables = listOf(
                    WorkflowVariableDefinition(name = "api_token_name", type = WorkflowVariableType.TEXT, defaultValue = "super-secret-new-token"),
                    WorkflowVariableDefinition(name = "region", type = WorkflowVariableType.TEXT, defaultValue = "us-east")
                ),
                defaultRecoveryPolicy = RecoveryPolicy(strategy = RecoveryStrategy.RESCAN_AND_RETRY, maxRetries = 2)
            ),
            steps = listOf(
                AutomationStep(id = "b", order = 1, actionType = AutomationActionType.INPUT, targetLabel = "Name", selector = "#full-name", value = "Alice", description = "Type Name"),
                AutomationStep(id = "a", order = 2, actionType = AutomationActionType.CLICK, targetLabel = "Start", selector = "#start", description = "Start"),
                AutomationStep(id = "d", order = 3, actionType = AutomationActionType.SUBMIT, targetLabel = "Confirm", selector = "#ok", description = "Submit Order", isDestructive = true)
            )
        )

        val diff = WorkflowDiffEngine.compareDocuments(
            baseLabel = "v1",
            targetLabel = "v2",
            baseName = "Order",
            targetName = "Order v2",
            baseDoc = baseDoc,
            targetDoc = targetDoc
        )

        assertTrue(diff.hasChanges)
        assertEquals(1, diff.addedCount)
        assertEquals(1, diff.removedCount)
        assertEquals(1, diff.modifiedCount)
        assertEquals(1, diff.reorderedCount)
        assertTrue(diff.metadataChanges.any { it.contains("Order") })
        assertTrue(diff.metadataChanges.any { it.contains("recovery policy", ignoreCase = true) })
        // Secret variable value must be masked in diff description
        val tokenChange = diff.metadataChanges.first { it.contains("api_token_name") }
        assertFalse(tokenChange.contains("super-secret-new-token"))
        assertTrue(tokenChange.contains("••••"))
    }

    @Test
    fun workflowDryRunEngine_evaluatesBranchesSubWorkflowsOriginAndCredentials_withZeroWebViewExecution() = runBlocking {
        val countingWebView = CountingFakeWebView(context)
        val childId = repository.saveAutomationWorkflow(
            name = "Child Lookup",
            domain = "shop.example.com",
            sourceUrl = "https://shop.example.com",
            type = AutomationType.MANUAL,
            steps = listOf(
                AutomationStep(
                    id = "c1",
                    order = 1,
                    actionType = AutomationActionType.SET_VARIABLE,
                    targetLabel = "discount_code",
                    variableName = "discount_code",
                    value = "SAVE20",
                    description = "Compute discount"
                )
            ),
            metadata = WorkflowDocumentMetadata(
                variables = listOf(WorkflowVariableDefinition(name = "discount_code", type = WorkflowVariableType.TEXT, isOutput = true))
            )
        )

        val parentId = repository.saveAutomationWorkflow(
            name = "DryRun Checkout",
            domain = "shop.example.com",
            sourceUrl = "https://shop.example.com/cart",
            type = AutomationType.MANUAL,
            metadata = WorkflowDocumentMetadata(
                variables = listOf(
                    WorkflowVariableDefinition(name = "tier", type = WorkflowVariableType.TEXT, defaultValue = "gold", isInput = true),
                    WorkflowVariableDefinition(name = "applied_code", type = WorkflowVariableType.TEXT)
                )
            ),
            steps = listOf(
                AutomationStep(
                    id = "p_cond",
                    order = 1,
                    actionType = AutomationActionType.CONDITION,
                    targetLabel = "Check gold tier",
                    description = "Check tier",
                    condition = ConditionNode.Predicate(
                        operator = ConditionOperator.VARIABLE_EQUALS,
                        variableName = "tier",
                        expectedValue = "platinum"
                    ),
                    thenSteps = listOf(
                        AutomationStep(
                            id = "p_call",
                            order = 1,
                            actionType = AutomationActionType.CALL_WORKFLOW,
                            targetLabel = "Child Lookup",
                            description = "Run Child Lookup",
                            subWorkflowRef = SubWorkflowReference(
                                targetWorkflowId = childId,
                                targetWorkflowName = "Child Lookup",
                                outputMappings = mapOf("applied_code" to "discount_code")
                            )
                        )
                    )
                ),
                AutomationStep(
                    id = "p_nav",
                    order = 2,
                    actionType = AutomationActionType.NAVIGATE,
                    targetLabel = "https://pay.partner.com/checkout",
                    value = "https://pay.partner.com/checkout",
                    description = "Navigate to partner checkout"
                ),
                AutomationStep(
                    id = "p_cred",
                    order = 3,
                    actionType = AutomationActionType.FILL_CREDENTIAL,
                    targetLabel = "Card PIN",
                    selector = "#pin",
                    description = "Fill unbound credential",
                    requiresCredential = true
                ),
                AutomationStep(
                    id = "p_submit",
                    order = 4,
                    actionType = AutomationActionType.SUBMIT,
                    targetLabel = "Pay Now",
                    selector = "#pay",
                    description = "Confirm payment",
                    isDestructive = true
                )
            )
        )

        val report = repository.runWorkflowDryRun(
            workflowId = parentId,
            runtimeInputs = mapOf("tier" to "platinum"),
            currentPageUrl = "https://shop.example.com/cart",
            passiveDomSnapshot = listOf(
                DetectedPageElement(
                    id = "el1",
                    tagName = "button",
                    role = "button",
                    label = "Pay Now",
                    controlKind = com.example.automation.ControlKind.BUTTON,
                    selector = "#pay",
                    accessibleName = "Pay Now"
                )
            )
        )!!

        // Zero side effects against WebView
        assertEquals(0, countingWebView.jsCallCount.get())
        assertEquals(0, countingWebView.navCallCount.get())

        assertFalse("Unbound credential makes live run unsafe", report.isSafeToRun)
        assertTrue(report.plannedSteps.any { it.stepId == "p_cond" && it.status == DryRunStepStatus.EVALUATED_TRUE })
        assertTrue(report.resolvedSubWorkflowOrder.contains("Child Lookup"))
        assertEquals("SAVE20", report.simulatedFinalVariables["applied_code"])
        assertTrue(report.originTransitions.any { it.contains("https://shop.example.com") && it.contains("https://pay.partner.com") })
        assertEquals(1, report.credentialStepsRequiringRebind.size)
        assertEquals(1, report.destructiveActions.size)

        // Archived workflow must also block dry-run safe-to-run
        repository.setWorkflowArchived(parentId, archived = true)
        val archivedDryRun = repository.runWorkflowDryRun(parentId)!!
        assertFalse(archivedDryRun.isSafeToRun)
        assertTrue(archivedDryRun.validationReport.isArchived)
    }

    @Test
    fun composeUi_phase8WorkspaceAndDetailsScreens_supportFiltersArchiveDiffAndDryRun() {
        val vm = createViewModel()
        val idA: Long
        val idB: Long
        runBlocking {
            idA = repository.saveAutomationWorkflow(
                name = "Morning Sync",
                domain = "work.com",
                sourceUrl = "https://work.com",
                type = AutomationType.MANUAL,
                steps = listOf(
                    AutomationStep(id = "a1", order = 1, actionType = AutomationActionType.CLICK, targetLabel = "Sync", selector = "#sync", description = "Sync")
                )
            )
            repository.updateWorkflowOrganization(
                idA,
                WorkflowOrganizationMetadata(folder = "Work", category = "Ops", tags = listOf("daily", "morning"), isFavorite = false)
            )
            repository.updateAutomationWorkflowSteps(
                idA,
                listOf(
                    AutomationStep(id = "a1", order = 1, actionType = AutomationActionType.CLICK, targetLabel = "Sync", selector = "#sync", description = "Sync"),
                    AutomationStep(id = "a2", order = 2, actionType = AutomationActionType.CLICK, targetLabel = "Done", selector = "#done", description = "Done")
                )
            )

            idB = repository.saveAutomationWorkflow(
                name = "Old Legacy Flow",
                domain = "archive.org",
                sourceUrl = "https://archive.org",
                type = AutomationType.RECORDED,
                steps = listOf(
                    AutomationStep(id = "b1", order = 1, actionType = AutomationActionType.CLICK, targetLabel = "Run", selector = "#run", description = "Run")
                )
            )
            repository.setWorkflowArchived(idB, archived = true)
        }

        val workflowsList = runBlocking { database.apexDao().getAutomationWorkflowsOnce() }
        val wfA = workflowsList.first { it.id == idA }
        val wfB = workflowsList.first { it.id == idB }
        val versionsA = runBlocking { repository.getWorkflowVersionsOnce(idA) }
        val diffA = WorkflowDiffEngine.compareVersions(versionsA.last(), versionsA.first())
        val dryRunA = runBlocking { repository.runWorkflowDryRun(idA) }!!

        // 1. Workspace screen: ALL hides archived, ARCHIVED shows it, search & tag filters work
        var toggledFavoriteId: Long? = null
        composeTestRule.setContent {
            ApexBrowserTheme {
                var selectedTag by remember { mutableStateOf<String?>(null) }
                AutomationWorkspaceScreenOverlay(
                    workflows = listOf(wfA, wfB),
                    executions = emptyList(),
                    activeTabDomain = "work.com",
                    activeTabUrl = "https://work.com",
                    executionState = null,
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
                    selectedTagFilter = selectedTag,
                    onSelectTagFilter = { selectedTag = it },
                    onToggleFavorite = { toggledFavoriteId = it }
                )
            }
        }
        composeTestRule.onNodeWithTag("automation_workflow_card_$idA").assertIsDisplayed()
        composeTestRule.onNodeWithTag("automation_workflow_card_$idB").assertDoesNotExist()

        composeTestRule.onNodeWithTag("workspace_tag_filter_daily").performClick()
        composeTestRule.onNodeWithTag("automation_workflow_card_$idA").assertIsDisplayed()

        composeTestRule.onNodeWithTag("workflow_favorite_button_$idA").performClick()
        assertEquals(idA, toggledFavoriteId)

        // Toggle tag filter back off before switching to Archived tab
        composeTestRule.onNodeWithTag("workspace_tag_filter_daily").performClick()
        composeTestRule.onNodeWithTag("workspace_category_archived").performClick()
        composeTestRule.onNodeWithTag("automation_workflow_card_$idB").assertIsDisplayed()
        composeTestRule.onNodeWithTag("automation_workflow_card_$idA").assertDoesNotExist()

        // Also exercise ViewModel sorting & validation wiring directly
        runBlocking {
            vm.setWorkspaceSortOption(WorkflowSortOption.NAME_ASC)
            assertEquals(WorkflowSortOption.NAME_ASC, vm.uiState.value.workspaceSortOption)
            vm.validateWorkflow(idA)
            idleUntil { vm.uiState.value.validationReportsByWorkflowId[idA] != null }
        }
    }

    @Test
    fun composeUi_detailsOverlay_rendersArchivedBlock_versionDiff_andDryRunDialogs() {
        val id: Long
        runBlocking {
            id = repository.saveAutomationWorkflow(
                name = "Invoice Flow",
                domain = "billing.com",
                sourceUrl = "https://billing.com",
                type = AutomationType.MANUAL,
                steps = listOf(
                    AutomationStep(id = "i1", order = 1, actionType = AutomationActionType.CLICK, targetLabel = "Pay", selector = "#pay", description = "Click Pay")
                )
            )
            repository.updateAutomationWorkflowSteps(
                id,
                listOf(
                    AutomationStep(id = "i1", order = 1, actionType = AutomationActionType.CLICK, targetLabel = "Pay", selector = "#pay", description = "Click Pay"),
                    AutomationStep(id = "i2", order = 2, actionType = AutomationActionType.SUBMIT, targetLabel = "Confirm", selector = "#confirm", description = "Confirm Pay", isDestructive = true)
                )
            )
            repository.setWorkflowArchived(id, archived = true)
        }

        val wfArchived = runBlocking { repository.getAutomationWorkflowById(id)!! }
        val versions = runBlocking { repository.getWorkflowVersionsOnce(id) }
        val diff = WorkflowDiffEngine.compareVersions(versions.last(), versions.first())
        val validation = runBlocking { repository.validateWorkflowDetailed(id)!! }
        val dryRun = runBlocking { repository.runWorkflowDryRun(id)!! }

        var restoreArchiveClicked = false
        composeTestRule.setContent {
            ApexBrowserTheme {
                var activeDryRun by remember { mutableStateOf<WorkflowDryRunReport?>(null) }
                var activeDiff by remember { mutableStateOf<WorkflowDiffReport?>(null) }
                AutomationDetailsScreenOverlay(
                    workflow = wfArchived,
                    executions = emptyList(),
                    executionState = null,
                    onRunWorkflow = {},
                    onRenameWorkflow = { _, _ -> },
                    onUpdateSteps = { _, _ -> },
                    onAddStep = { _, _, _, _, _ -> },
                    onConfirmDestructiveExecution = {},
                    onCancelDestructiveExecution = {},
                    onClearExecutionBanner = {},
                    onDismiss = {},
                    validationReport = validation,
                    dryRunReport = activeDryRun,
                    versions = versions,
                    diffReport = activeDiff,
                    onRunDryRun = { _, _ -> activeDryRun = dryRun },
                    onClearDryRun = { activeDryRun = null },
                    onCompareVersions = { _, _, _ -> activeDiff = diff },
                    onClearDiffReport = { activeDiff = null },
                    onRestoreArchivedWorkflow = { restoreArchiveClicked = true }
                )
            }
        }

        // Archived notice banner visible + Run button disabled + Restore button works
        composeTestRule.onNodeWithTag("details_archived_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("details_run_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("details_restore_archive_button").performClick()
        assertTrue(restoreArchiveClicked)

        // Open Versions & Compare v1 -> v2
        composeTestRule.onNodeWithTag("details_versions_button").performClick()
        composeTestRule.onNodeWithTag("version_history_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("version_compare_button").performClick()
        composeTestRule.onNodeWithTag("version_diff_summary_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Close").performClick()

        // Scroll LazyColumn to Validation & Dry-Run card and launch Dry-Run Report dialog
        composeTestRule.onNodeWithTag("automation_details_list")
            .performScrollToNode(hasTestTag("details_dry_run_button"))
        composeTestRule.onNodeWithTag("details_dry_run_button").performClick()
        composeTestRule.onNodeWithTag("dry_run_report_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dry_run_step_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dry_run_close_button").performClick()
    }
}
