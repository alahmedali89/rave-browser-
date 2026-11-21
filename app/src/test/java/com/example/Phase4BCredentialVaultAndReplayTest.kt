package com.example

import android.content.Context
import android.os.Looper
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.automation.AutomationActionType
import com.example.automation.AutomationEngine
import com.example.automation.AutomationErrorCode
import com.example.automation.AutomationStep
import com.example.automation.AutomationStepCodec
import com.example.automation.AutomationType
import com.example.automation.CredentialAuthenticationManager
import com.example.automation.CredentialFieldRole
import com.example.automation.CredentialFillCoordinator
import com.example.automation.CredentialVaultCrypto
import com.example.automation.CredentialVaultManager
import com.example.automation.CredentialVaultOperationResult
import com.example.automation.DecryptedCredentialSecret
import com.example.automation.InMemoryVaultKeyMetadataStore
import com.example.automation.InMemoryVaultKeyProvider
import com.example.automation.RecordedDomEvent
import com.example.automation.RecordingStatus
import com.example.automation.VaultAuthOperation
import com.example.automation.VaultCryptoResult
import com.example.automation.VaultKeyMetadataStore
import com.example.automation.VaultStatus
import com.example.data.local.ApexDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.BrowserRepository
import com.example.engine.FastDownloadEngine
import com.example.engine.PasswordVaultCrypto
import com.example.ui.viewmodel.BrowserViewModel
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Phase 4B Verification Suite:
 * Covers Part 9.1 (Encryption), 9.2 (Database & Migrations), 9.3 (Origin Security),
 * 9.4 (Workflow & Export/Import Security), 9.5 (Replay, Authorization & JS Bridge Security),
 * and 9.6 (Lifecycle, Backgrounding & Device Lock Handling).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase4BCredentialVaultAndReplayTest {

    private lateinit var context: Context
    private lateinit var database: ApexDatabase
    private lateinit var repository: BrowserRepository
    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var keyProvider: InMemoryVaultKeyProvider
    private lateinit var metadataStore: VaultKeyMetadataStore
    private lateinit var crypto: CredentialVaultCrypto
    private lateinit var authManager: CredentialAuthenticationManager
    private lateinit var vaultManager: CredentialVaultManager
    private lateinit var automationEngine: AutomationEngine
    private var activeStore: ViewModelStore? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferencesRepository = UserPreferencesRepository(context)
        database = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        metadataStore = InMemoryVaultKeyMetadataStore()
        keyProvider = InMemoryVaultKeyProvider(metadataStore = metadataStore)
        crypto = CredentialVaultCrypto(keyProvider = keyProvider, metadataStore = metadataStore)
        authManager = CredentialAuthenticationManager(context = context)
        automationEngine = AutomationEngine()
        vaultManager = CredentialVaultManager(
            dao = database.apexDao(),
            crypto = crypto,
            authManager = authManager,
            originParser = { url -> automationEngine.parseOrigin(url) }
        )
        val sharedPrefs = context.getSharedPreferences(
            "phase4b_test_${UUID.randomUUID()}",
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
                        automationEngine = automationEngine
                    ) as T
                }
            }
        )[BrowserViewModel::class.java]
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        return vm
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

    // =========================================================================
    // 9.1 ENCRYPTION TESTS
    // =========================================================================

    @Test
    fun encryption_roundTrip_andUniqueIvPerEncryption() {
        val secret = "SuP3r$3cr3t!Pass#2026".toCharArray()
        val enc1 = crypto.encryptChars(secret.copyOf())
        val enc2 = crypto.encryptChars(secret.copyOf())

        assertTrue(enc1 is VaultCryptoResult.Success)
        assertTrue(enc2 is VaultCryptoResult.Success)

        val payload1 = (enc1 as VaultCryptoResult.Success).value
        val payload2 = (enc2 as VaultCryptoResult.Success).value

        // Must never equal each other (fresh random 12-byte IV per encryption)
        assertNotEquals(payload1, payload2)
        // Must never contain plaintext or raw Base64 of plaintext
        val rawBase64 = Base64.getEncoder().encodeToString(String(secret).toByteArray(Charsets.UTF_8))
        assertFalse(payload1.contains(String(secret)))
        assertFalse(payload1.contains(rawBase64))

        val dec1 = crypto.decryptToChars(payload1)
        val dec2 = crypto.decryptToChars(payload2)
        assertTrue(dec1 is VaultCryptoResult.Success)
        assertTrue(dec2 is VaultCryptoResult.Success)
        assertArrayEquals(secret, (dec1 as VaultCryptoResult.Success).value)
        assertArrayEquals(secret, (dec2 as VaultCryptoResult.Success).value)
    }

    @Test
    fun encryption_tamperedCiphertext_orTamperedIv_orTruncatedPayload_failsClosedWithZeroFallback() {
        val secret = "VaultTestPassword!99".toCharArray()
        val encrypted = (crypto.encryptChars(secret) as VaultCryptoResult.Success).value
        val parts = encrypted.removePrefix(CredentialVaultCrypto.PAYLOAD_PREFIX).split(':')
        assertEquals(2, parts.size)

        val ivBytes = Base64.getDecoder().decode(parts[0])
        val cipherBytes = Base64.getDecoder().decode(parts[1])

        // 1. Tamper with ciphertext/GCM tag byte
        val tamperedCipherBytes = cipherBytes.copyOf()
        tamperedCipherBytes[tamperedCipherBytes.lastIndex] =
            (tamperedCipherBytes.last().toInt() xor 0x55).toByte()
        val tamperedCipherPayload = CredentialVaultCrypto.PAYLOAD_PREFIX +
            parts[0] + ":" + Base64.getEncoder().encodeToString(tamperedCipherBytes)
        val decTamperedCipher = crypto.decryptToChars(tamperedCipherPayload)
        assertTrue(decTamperedCipher is VaultCryptoResult.CorruptedPayload)

        // 2. Tamper with IV byte (index 0)
        val tamperedIvBytes = ivBytes.copyOf()
        tamperedIvBytes[0] = (tamperedIvBytes[0].toInt() xor 0x33).toByte()
        val tamperedIvPayload = CredentialVaultCrypto.PAYLOAD_PREFIX +
            Base64.getEncoder().encodeToString(tamperedIvBytes) + ":" + parts[1]
        val decTamperedIv = crypto.decryptToChars(tamperedIvPayload)
        assertTrue(decTamperedIv is VaultCryptoResult.CorruptedPayload)

        // 3. Truncated payload (shorter than 16-byte GCM tag)
        val truncatedPayload = CredentialVaultCrypto.PAYLOAD_PREFIX +
            parts[0] + ":" + Base64.getEncoder().encodeToString(ByteArray(8) { 1 })
        assertTrue(crypto.decryptToChars(truncatedPayload) is VaultCryptoResult.CorruptedPayload)

        // 4. Plain Base64 string without v1_gcm: prefix must NEVER fall back to Base64 decoding
        val plainBase64 = Base64.getEncoder().encodeToString("FallbackAttack".toByteArray())
        assertTrue(crypto.decryptToChars(plainBase64) is VaultCryptoResult.CorruptedPayload)
        assertEquals("", PasswordVaultCrypto().decrypt(plainBase64))
    }

    @Test
    fun encryption_keyInvalidated_isDetectedAndNeverSilentlyReplaced() = runBlocking {
        vaultManager.unlockVault()
        val saved = vaultManager.addCredential(
            websiteUrl = "https://portal.example.com/login",
            username = "alice@example.com",
            passwordChars = "CorrectHorseBatteryStaple".toCharArray(),
            label = "Portal Login"
        )
        assertTrue(saved is CredentialVaultOperationResult.Success)
        val credId = (saved as CredentialVaultOperationResult.Success).value.credentialId

        // Simulate Android Keystore key invalidation / missing key while records exist
        keyProvider.dropUnderlyingKeyWithoutReset()
        val missingKeyCheck = keyProvider.resolveKeyForEncryption()
        assertTrue(missingKeyCheck is VaultCryptoResult.KeyInvalidated)
        assertEquals(VaultStatus.KEY_INVALIDATED, vaultManager.refreshVaultStatus())

        // Attempting to decrypt while key is invalidated must fail with KeyInvalidated
        // and must NOT silently create a replacement key while encrypted records exist.
        val decResult = vaultManager.decryptFieldForAuthorizedReplay(
            credentialId = credId,
            fieldRole = CredentialFieldRole.PASSWORD,
            livePageUrl = "https://portal.example.com/login",
            expectedStepOrigin = "https://portal.example.com:443"
        )
        assertTrue(decResult is CredentialVaultOperationResult.KeyInvalidated)

        // Explicit user-confirmed reset clears unreadable records and provisions a fresh key
        val resetRes = vaultManager.resetVaultAfterUserConfirmation()
        assertTrue(resetRes is CredentialVaultOperationResult.Success)
        assertEquals(0, vaultManager.getAllCredentialsOnce().size)
    }

    @Test
    fun decryptedCredentialSecret_zeroesCharBufferImmediately() {
        val secret = DecryptedCredentialSecret(
            credentialId = "cred_1",
            websiteOrigin = "https://example.com:443",
            fieldRole = CredentialFieldRole.PASSWORD,
            secretChars = "MySecret123!".toCharArray()
        )
        var capturedLength = 0
        secret.useChars { chars ->
            capturedLength = chars.size
        }
        assertEquals(12, capturedLength)
        assertTrue(secret.isCleared)
    }

    // =========================================================================
    // 9.2 DATABASE & MIGRATION TESTS
    // =========================================================================

    @Test
    fun database_credentialVaultCrud_andStableCredentialIdAcrossUpdates() = runBlocking {
        vaultManager.unlockVault()
        val addRes = vaultManager.addCredential(
            websiteUrl = "https://accounts.example.com:8443/auth",
            username = "bob",
            passwordChars = "InitialPassword!1".toCharArray(),
            label = "Accounts"
        )
        assertTrue(addRes is CredentialVaultOperationResult.Success)
        val created = (addRes as CredentialVaultOperationResult.Success).value
        assertEquals("https://accounts.example.com:8443", created.websiteOrigin)
        assertEquals("https://accounts.example.com:8443", created.displayOrigin)

        // Update credential and verify credentialId remains unchanged
        val updateRes = vaultManager.updateCredential(
            credentialId = created.credentialId,
            websiteUrl = "https://accounts.example.com:8443/new-path",
            username = "bob_updated",
            passwordChars = "RotatedPassword!2".toCharArray(),
            label = "Updated Accounts"
        )
        assertTrue(updateRes is CredentialVaultOperationResult.Success)
        val updated = (updateRes as CredentialVaultOperationResult.Success).value
        assertEquals(created.credentialId, updated.credentialId)
        assertEquals("Updated Accounts", updated.label)
        assertEquals("bob_updated", updated.usernameIdentifier)

        // Reveal requires per-credential authentication
        val revealed = vaultManager.revealCredential(created.credentialId)
        assertTrue(revealed is CredentialVaultOperationResult.Success)
        val details = (revealed as CredentialVaultOperationResult.Success).value
        assertEquals("bob_updated", details.username)
        assertEquals("RotatedPassword!2", details.password)
        details.clear()
        assertEquals("", details.password)

        // Delete credential
        val delRes = vaultManager.deleteCredential(created.credentialId)
        assertTrue(delRes is CredentialVaultOperationResult.Success)
        assertNull(vaultManager.getCredentialMetadataById(created.credentialId))
    }

    @Test
    fun database_migration13To14_preservesExistingDataAndCreatesCredentialVaultTable() {
        val dbName = "migration_test_${System.nanoTime()}.db"
        val callback = object : SupportSQLiteOpenHelper.Callback(13) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `automation_workflows` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `domain` TEXT NOT NULL,
                        `sourceUrl` TEXT NOT NULL,
                        `stepsJson` TEXT NOT NULL,
                        `instructionsText` TEXT NOT NULL,
                        `isEnabled` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `lastExecutedAt` INTEGER,
                        `lastExecutionStatus` TEXT NOT NULL,
                        `lastExecutionSummary` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `automation_workflows`
                    (`id`, `name`, `type`, `domain`, `sourceUrl`, `stepsJson`, `instructionsText`, `isEnabled`, `createdAt`, `updatedAt`, `lastExecutedAt`, `lastExecutionStatus`, `lastExecutionSummary`)
                    VALUES (1, 'Existing Workflow', 'RECORDED', 'example.com', 'https://example.com', '[]', '', 1, 1000, 1000, NULL, 'NEVER_RUN', '')
                    """.trimIndent()
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
        }

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build()
        )
        val sqlDb = helper.writableDatabase
        ApexDatabase.MIGRATION_13_14.migrate(sqlDb)

        // Verify existing workflow row is intact
        sqlDb.query("SELECT name, domain FROM automation_workflows WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Existing Workflow", cursor.getString(0))
            assertEquals("example.com", cursor.getString(1))
        }

        // Verify credential_vault table exists and accepts inserts
        sqlDb.execSQL(
            """
            INSERT INTO `credential_vault`
            (`credentialId`, `websiteOrigin`, `displayOrigin`, `label`, `usernameIdentifier`, `encryptedUsername`, `encryptedPassword`, `createdAt`, `updatedAt`, `lastUsedAt`)
            VALUES ('cred_mig_1', 'https://example.com:443', 'https://example.com', 'Migrated', 'user1', 'v1_gcm:abc', 'v1_gcm:def', 1000, 1000, NULL)
            """.trimIndent()
        )
        sqlDb.query("SELECT credentialId, websiteOrigin FROM credential_vault WHERE credentialId = 'cred_mig_1'")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("cred_mig_1", cursor.getString(0))
                assertEquals("https://example.com:443", cursor.getString(1))
            }

        sqlDb.close()
        context.deleteDatabase(dbName)
    }

    // =========================================================================
    // 9.3 ORIGIN SECURITY TESTS
    // =========================================================================

    @Test
    fun originPolicy_enforcesStrictCanonicalSchemeHostAndPortMatching() {
        // Exact match with default port normalization
        assertTrue(
            vaultManager.originsMatchStrictly(
                "https://example.com:443",
                "https://example.com/login?next=/dashboard"
            )
        )
        assertTrue(
            vaultManager.originsMatchStrictly(
                "http://example.com/path",
                "http://example.com:80/other"
            )
        )

        // Subdomain mismatch MUST fail
        assertFalse(
            vaultManager.originsMatchStrictly(
                "https://example.com",
                "https://sub.example.com/login"
            )
        )
        assertFalse(
            vaultManager.originsMatchStrictly(
                "https://login.example.com",
                "https://example.com/login"
            )
        )

        // Scheme downgrade or mismatch MUST fail
        assertFalse(
            vaultManager.originsMatchStrictly(
                "https://example.com",
                "http://example.com"
            )
        )

        // Non-default port mismatch MUST fail
        assertFalse(
            vaultManager.originsMatchStrictly(
                "https://example.com",
                "https://example.com:8443/login"
            )
        )

        // Lookalike domain MUST fail
        assertFalse(
            vaultManager.originsMatchStrictly(
                "https://example.com",
                "https://example.com.attacker.org/login"
            )
        )

        // Non-HTTP(S) schemes MUST be rejected
        val forbiddenUrls = listOf(
            "javascript:alert(1)",
            "data:text/html,<input type='password'>",
            "file:///sdcard/login.html",
            "about:blank",
            "blob:https://example.com/123",
            "content://com.example.provider/doc",
            "intent://example.com#Intent;scheme=https;end",
            "apex://home"
        )
        forbiddenUrls.forEach { badUrl ->
            val parsed = vaultManager.validateWebsiteOrigin(badUrl)
            assertFalse("Expected $badUrl to be invalid", parsed.isValid)
            assertFalse(vaultManager.originsMatchStrictly("https://example.com", badUrl))
        }
    }

    // =========================================================================
    // 9.4 WORKFLOW & EXPORT/IMPORT SECURITY TESTS
    // =========================================================================

    @Test
    fun workflowRecording_passwordFieldCreatesUnassignedCredentialStepWithZeroPlaintext() {
        val viewModel = createViewModel()
        val loginUrl = "https://login.example.com/signin"
        viewModel.submitNavigation(loginUrl)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val activeTabId = viewModel.uiState.value.activeTabId
        val fakeWebView = object : WebView(context) {
            override fun getUrl(): String = loginUrl
            override fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {
                val sidMatch = Regex("""sessionId:\s*("(?:[^"\\]|\\.)*")""").find(script)
                val sid = sidMatch?.groupValues?.getOrNull(1)
                    ?.let { JSONObject("{\"v\":$it}").optString("v") } ?: ""
                val payload = JSONObject().apply {
                    put("ok", true)
                    put("sessionId", sid)
                }.toString()
                resultCallback?.onReceiveValue(JSONObject.quote(payload))
            }
        }
        viewModel.registerWebView(activeTabId, fakeWebView)

        viewModel.startRecording()
        idleMainUntil {
            viewModel.uiState.value.recordingSession.status == RecordingStatus.RECORDING
        }
        val session = viewModel.uiState.value.recordingSession
        assertEquals(activeTabId, session.initiatingTabId)

        // Simulate recorded password field input event (even if a caller passed a non-empty value)
        val result = viewModel.processRecordedDomEvent(
            event = RecordedDomEvent(
                sessionId = session.sessionId,
                tabId = activeTabId,
                origin = session.currentOrigin,
                sequenceNumber = 1L,
                eventType = "INPUT",
                targetLabel = "Account Password",
                selector = "input#password",
                value = "LeakedPlaintextPasswordShouldBeStripped!",
                isDestructive = false,
                timestamp = System.currentTimeMillis(),
                isPasswordField = true,
                fieldRoleHint = "PASSWORD"
            ),
            sourceTabId = activeTabId,
            sourceWebView = fakeWebView,
            sourceWebViewUrl = loginUrl
        )
        assertTrue(result.success)

        val steps = viewModel.uiState.value.recordingSession.steps
        assertEquals(1, steps.size)
        val recordedStep = steps.first()
        assertTrue(recordedStep.isCredentialStep)
        assertTrue(recordedStep.requiresCredential)
        assertEquals(CredentialFieldRole.PASSWORD, recordedStep.credentialFieldRole)
        // Must NEVER store plaintext password or "••••••"
        assertEquals("", recordedStep.value)
        // Must NEVER automatically bind a credential without explicit user selection
        assertNull(recordedStep.credentialReferenceId)
        assertEquals("https://login.example.com:443", recordedStep.expectedOrigin)
    }

    @Test
    fun workflowExportAndImport_neverContainsPlaintextSecrets_andHandlesMissingCredentialReferences() = runBlocking {
        vaultManager.unlockVault()
        val cred = (vaultManager.addCredential(
            websiteUrl = "https://app.example.com",
            username = "alice",
            passwordChars = "TopSecretPassword!42".toCharArray(),
            label = "App Account"
        ) as CredentialVaultOperationResult.Success).value

        val stepWithCred = AutomationStep(
            id = "step_cred_1",
            order = 1,
            actionType = AutomationActionType.FILL_CREDENTIAL,
            targetLabel = "Password",
            selector = "input#password",
            value = "AttemptedSecretLeakInStepValue",
            description = "Fill password",
            credentialReferenceId = cred.credentialId,
            credentialFieldRole = CredentialFieldRole.PASSWORD,
            expectedOrigin = "https://app.example.com:443",
            requiresCredential = true
        )

        val wfId = repository.saveAutomationWorkflow(
            name = "App Sign-In",
            type = AutomationType.RECORDED,
            domain = "app.example.com",
            sourceUrl = "https://app.example.com/login",
            steps = listOf(stepWithCred),
            instructionsText = ""
        )
        assertTrue(wfId > 0L)

        val exportedJson = repository.exportAutomationsAsJson()
        // Exported JSON must NEVER contain the password or the attempted step value leak
        assertFalse(exportedJson.contains("TopSecretPassword!42"))
        assertFalse(exportedJson.contains("AttemptedSecretLeakInStepValue"))
        assertTrue(exportedJson.contains(cred.credentialId))

        // Delete the credential from the vault, then import the exported JSON
        vaultManager.deleteCredential(cred.credentialId)
        repository.deleteAutomationWorkflow(wfId)

        val importedCount = repository.importAutomationsFromJson(exportedJson)
        assertEquals(1, importedCount)

        val importedWorkflow = repository.automationWorkflowsFlow.first().first()
        val importedSteps = AutomationStepCodec.decode(importedWorkflow.stepsJson)
        assertEquals(1, importedSteps.size)
        val importedStep = importedSteps.first()
        assertTrue(importedStep.isCredentialStep)
        assertTrue(importedStep.requiresCredential)
        assertEquals("", importedStep.value)
        // Because the credential does not exist locally, import marks it unassigned
        assertNull(importedStep.credentialReferenceId)
    }

    // =========================================================================
    // 9.5 REPLAY, AUTHORIZATION & JS BRIDGE SECURITY TESTS
    // =========================================================================

    @Test
    fun replay_blocksUnassigned_deleted_orOriginMismatchedCredentialsBeforeExecution() = runBlocking {
        val viewModel = createViewModel()
        val loginUrl = "https://portal.example.com/login"
        viewModel.submitNavigation(loginUrl)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val activeTabId = viewModel.uiState.value.activeTabId
        val fakeWebView = object : WebView(context) {
            override fun getUrl(): String = loginUrl
        }
        viewModel.registerWebView(activeTabId, fakeWebView)

        // 1. Unassigned credential step -> fails with CREDENTIAL_UNASSIGNED
        val unassignedStep = AutomationStep(
            id = "step_unassigned",
            order = 1,
            actionType = AutomationActionType.FILL_CREDENTIAL,
            targetLabel = "Password",
            selector = "input#password",
            value = "",
            description = "Fill password",
            credentialReferenceId = null,
            credentialFieldRole = CredentialFieldRole.PASSWORD,
            expectedOrigin = "https://portal.example.com:443",
            requiresCredential = true
        )
        val unassignedWfId = repository.saveAutomationWorkflow(
            name = "Unassigned Login",
            type = AutomationType.RECORDED,
            domain = "portal.example.com",
            sourceUrl = loginUrl,
            steps = listOf(unassignedStep),
            instructionsText = ""
        )
        viewModel.runWorkflow(unassignedWfId)
        idleMainUntil { viewModel.uiState.value.executionState?.status == "FAILED" }
        assertTrue(
            viewModel.uiState.value.executionState?.verificationMessage.orEmpty()
                .contains("requires a saved credential", ignoreCase = true)
        )

        // 2. Deleted/missing credential reference -> fails with CREDENTIAL_NOT_FOUND
        val missingCredStep = unassignedStep.copy(
            id = "step_missing",
            credentialReferenceId = "cred_nonexistent_999"
        )
        val missingWfId = repository.saveAutomationWorkflow(
            name = "Missing Credential Login",
            type = AutomationType.RECORDED,
            domain = "portal.example.com",
            sourceUrl = loginUrl,
            steps = listOf(missingCredStep),
            instructionsText = ""
        )
        viewModel.runWorkflow(missingWfId)
        idleMainUntil {
            viewModel.uiState.value.executionState?.workflowId == missingWfId &&
                viewModel.uiState.value.executionState?.status == "FAILED"
        }
        assertTrue(
            viewModel.uiState.value.executionState?.verificationMessage.orEmpty()
                .contains("does not exist", ignoreCase = true)
        )

        // 3. Origin mismatch -> fails with CREDENTIAL_ORIGIN_MISMATCH
        vaultManager.unlockVault()
        val otherOriginCred = (vaultManager.addCredential(
            websiteUrl = "https://other.example.com",
            username = "eve",
            passwordChars = "OtherSecret!1".toCharArray(),
            label = "Other Site"
        ) as CredentialVaultOperationResult.Success).value

        val mismatchStep = unassignedStep.copy(
            id = "step_mismatch",
            credentialReferenceId = otherOriginCred.credentialId
        )
        val mismatchWfId = repository.saveAutomationWorkflow(
            name = "Mismatched Credential Login",
            type = AutomationType.RECORDED,
            domain = "portal.example.com",
            sourceUrl = loginUrl,
            steps = listOf(mismatchStep),
            instructionsText = ""
        )
        viewModel.runWorkflow(mismatchWfId)
        idleMainUntil {
            viewModel.uiState.value.executionState?.workflowId == mismatchWfId &&
                viewModel.uiState.value.executionState?.status == "FAILED"
        }
        assertTrue(
            viewModel.uiState.value.executionState?.verificationMessage.orEmpty()
                .contains("Origin mismatch", ignoreCase = true)
        )
    }

    @Test
    fun replay_requiresExplicitUserAuthorization_andExecutesScopedOneTimeJsFill() = runBlocking {
        val viewModel = createViewModel()
        val loginUrl = "https://secure.example.com/login"
        viewModel.submitNavigation(loginUrl)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val activeTabId = viewModel.uiState.value.activeTabId
        val evaluatedScripts = mutableListOf<String>()
        val webView = object : WebView(context) {
            override fun getUrl(): String = loginUrl
            override fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {
                evaluatedScripts.add(script)
                when {
                    script.contains("readyState") -> {
                        val payload = JSONObject().apply {
                            put("ready", true)
                            put("state", "complete")
                            put("hasBody", true)
                            put("origin", "https://secure.example.com:443")
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
                    else -> {
                        val payload = JSONObject().apply {
                            put("ok", true)
                            put("code", "NONE")
                            put("msg", "Done")
                        }.toString()
                        resultCallback?.onReceiveValue(JSONObject.quote(payload))
                    }
                }
            }
        }
        viewModel.registerWebView(activeTabId, webView)

        vaultManager.unlockVault()
        val cred = (vaultManager.addCredential(
            websiteUrl = loginUrl,
            username = "alice@secure.example.com",
            passwordChars = "MySecretPass!9".toCharArray(),
            label = "Secure Portal"
        ) as CredentialVaultOperationResult.Success).value

        val credStep = AutomationStep(
            id = "step_fill_pw",
            order = 1,
            actionType = AutomationActionType.FILL_CREDENTIAL,
            targetLabel = "Password",
            selector = "input#password",
            value = "",
            description = "Fill password",
            credentialReferenceId = cred.credentialId,
            credentialFieldRole = CredentialFieldRole.PASSWORD,
            expectedOrigin = "https://secure.example.com:443",
            requiresCredential = true
        )
        val wfId = repository.saveAutomationWorkflow(
            name = "Authorized Portal Login",
            type = AutomationType.RECORDED,
            domain = "secure.example.com",
            sourceUrl = loginUrl,
            steps = listOf(credStep),
            instructionsText = ""
        )

        // Starting replay MUST pause for explicit user authorization before any credential decryption
        viewModel.runWorkflow(wfId)
        idleMainUntil {
            viewModel.uiState.value.executionState?.status == "AWAITING_CREDENTIAL_AUTH" &&
                viewModel.uiState.value.pendingCredentialReplayAuth != null
        }

        val pendingAuth = viewModel.uiState.value.pendingCredentialReplayAuth
        assertNotNull(pendingAuth)
        assertEquals(wfId, pendingAuth?.workflowId)
        assertEquals("https://secure.example.com", pendingAuth?.displayOrigin)
        assertEquals(listOf("Secure Portal"), pendingAuth?.items?.map { it.credentialLabel })
        assertTrue("No JS should execute before user authorization", evaluatedScripts.isEmpty())

        // User explicitly confirms credential replay
        viewModel.confirmPendingCredentialReplay()
        idleMainUntil {
            viewModel.uiState.value.executionState?.status == "COMPLETED"
        }

        assertNull(viewModel.uiState.value.pendingCredentialReplayAuth)

        // Verify the scoped fill script checked live window.location origin and input[type="password"]
        // and did NOT store credentials in window.* globals
        val fillScript = evaluatedScripts.firstOrNull { it.contains("CREDENTIAL_FIELD_INVALID") }
        assertNotNull(fillScript)
        assertTrue(fillScript!!.contains("inputType !== 'password'"))
        assertFalse(fillScript.contains("window.apexCredential"))

        // Verify execution log summary in Room never contains the plaintext password
        val executions = repository.automationExecutionsFlow.first()
        assertTrue(executions.none { it.verificationSummary.contains("MySecretPass!9") })
    }

    @Test
    fun replay_midExecutionOriginRedirect_orInvalidFieldType_abortsCleanly() = runBlocking {
        vaultManager.unlockVault()
        val cred = (vaultManager.addCredential(
            websiteUrl = "https://bank.example.com/login",
            username = "alice",
            passwordChars = "BankPassword!2026".toCharArray(),
            label = "Bank Login"
        ) as CredentialVaultOperationResult.Success).value

        authManager.authenticateForCredentialOperation(
            operation = VaultAuthOperation.REPLAY_CREDENTIAL,
            credentialId = cred.credentialId,
            websiteOrigin = cred.websiteOrigin
        )

        val coordinator = CredentialFillCoordinator(
            vaultManager = vaultManager,
            automationEngine = automationEngine
        )
        val step = AutomationStep(
            id = "step_pw",
            order = 1,
            actionType = AutomationActionType.FILL_CREDENTIAL,
            targetLabel = "Password",
            selector = "input#password",
            value = "",
            description = "Fill password",
            credentialReferenceId = cred.credentialId,
            credentialFieldRole = CredentialFieldRole.PASSWORD,
            expectedOrigin = "https://bank.example.com:443",
            requiresCredential = true
        )

        // 1. Mid-replay redirect to evil.example.com before step executes
        val redirectedWebView = object : WebView(context) {
            override fun getUrl(): String = "https://evil.example.com/phish"
        }
        val redirectOutcome = coordinator.executeAuthorizedCredentialStep(
            step = step,
            authorizedTabId = "tab_1",
            currentActiveTabId = "tab_1",
            authorizedWebView = redirectedWebView,
            currentWebViewProvider = { redirectedWebView },
            liveTabUrlProvider = { "https://evil.example.com/phish" },
            navigationSynchronizer = null,
            userAuthorizedCredentialIds = setOf(cred.credentialId)
        )
        assertFalse(redirectOutcome.success)
        assertEquals(AutomationErrorCode.CREDENTIAL_ORIGIN_MISMATCH, redirectOutcome.errorCode)

        // 2. Target field is a text input instead of input[type="password"] for PASSWORD role
        val nonPasswordWebView = object : WebView(context) {
            override fun getUrl(): String = "https://bank.example.com/login"
            override fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {
                val payload = JSONObject().apply {
                    put("ok", false)
                    put("code", "CREDENTIAL_FIELD_INVALID")
                    put("msg", "Refusing to fill password into non-password input of type \"text\".")
                    put("verifiedLength", false)
                }.toString()
                resultCallback?.onReceiveValue(JSONObject.quote(payload))
            }
        }
        val invalidFieldOutcome = coordinator.executeAuthorizedCredentialStep(
            step = step,
            authorizedTabId = "tab_1",
            currentActiveTabId = "tab_1",
            authorizedWebView = nonPasswordWebView,
            currentWebViewProvider = { nonPasswordWebView },
            liveTabUrlProvider = { "https://bank.example.com/login" },
            navigationSynchronizer = null,
            userAuthorizedCredentialIds = setOf(cred.credentialId)
        )
        assertFalse(invalidFieldOutcome.success)
        assertEquals(AutomationErrorCode.CREDENTIAL_FIELD_INVALID, invalidFieldOutcome.errorCode)
    }

    // =========================================================================
    // 9.6 LIFECYCLE, BACKGROUNDING & DEVICE LOCK TESTS
    // =========================================================================

    @Test
    fun lifecycle_onAppBackgrounded_andOnDeviceLocked_immediatelyLockVaultAndClearSecrets() = runBlocking {
        val viewModel = createViewModel()

        viewModel.unlockCredentialVault()
        idleMainUntil { viewModel.uiState.value.vaultStatus == VaultStatus.UNLOCKED }

        var savedId: String? = null
        viewModel.addVaultCredential(
            websiteUrl = "https://vault.example.com",
            username = "user1",
            password = "SecretToClearOnBackground!1",
            label = "Vault Test",
            onSuccess = { savedId = it.credentialId }
        )
        idleMainUntil { savedId != null }

        val entry = vaultManager.getAllCredentialsOnce().first()
        viewModel.revealVaultCredential(entry.credentialId)
        idleMainUntil { viewModel.uiState.value.revealedVaultCredential != null }

        assertEquals(
            "SecretToClearOnBackground!1",
            viewModel.uiState.value.revealedVaultCredential?.password
        )

        // App goes to background -> must lock vault and zero/clear revealed credential
        viewModel.onAppBackgrounded()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals(VaultStatus.LOCKED, viewModel.uiState.value.vaultStatus)
        assertNull(viewModel.uiState.value.revealedVaultCredential)
        assertFalse(vaultManager.authManager.isVaultUnlocked())
        assertFalse(
            vaultManager.authManager.hasRecentCredentialGrant(
                operation = VaultAuthOperation.REVEAL_CREDENTIAL,
                credentialId = entry.credentialId,
                websiteOrigin = entry.websiteOrigin
            )
        )

        // Unlock again and test device screen lock
        viewModel.unlockCredentialVault()
        idleMainUntil { viewModel.uiState.value.vaultStatus == VaultStatus.UNLOCKED }

        viewModel.onDeviceLocked()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals(VaultStatus.LOCKED, viewModel.uiState.value.vaultStatus)
        assertFalse(vaultManager.authManager.isVaultUnlocked())
    }
}
