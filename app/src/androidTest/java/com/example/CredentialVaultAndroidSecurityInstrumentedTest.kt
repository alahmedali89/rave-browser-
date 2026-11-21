package com.example

import android.app.KeyguardManager
import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.automation.AndroidKeystoreVaultKeyProvider
import com.example.automation.AutomationActionType
import com.example.automation.AutomationEngine
import com.example.automation.AutomationErrorCode
import com.example.automation.AutomationStep
import com.example.automation.CredentialAuthenticationManager
import com.example.automation.CredentialFieldRole
import com.example.automation.CredentialFillCoordinator
import com.example.automation.CredentialVaultCrypto
import com.example.automation.CredentialVaultManager
import com.example.automation.CredentialVaultOperationResult
import com.example.automation.DeviceAuthPromptHandler
import com.example.automation.SharedPreferencesVaultKeyMetadataStore
import com.example.automation.VaultAuthOperation
import com.example.automation.VaultAuthResult
import com.example.automation.VaultCryptoResult
import com.example.automation.VaultStatus
import com.example.data.local.ApexDatabase
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 7 Android runtime Credential Vault & Phase 4B security validation.
 * Tests actual AndroidKeyStore AES-256-GCM key provisioning (with software fallback DISABLED),
 * KeyguardManager integration, session expiration, backgrounding/device lock abort,
 * non-silent key invalidation handling, deletion, origin mismatch denial, and secret redaction.
 *
 * Hardware-backed Keystore / physical biometric UI cannot be simulated cleanly on headless
 * emulators without enrolled biometrics; those hardware prompt boundaries are checked via
 * DeviceAuthPromptHandler and KeyguardManager state.
 */
@RunWith(AndroidJUnit4::class)
class CredentialVaultAndroidSecurityInstrumentedTest {

    private lateinit var context: Context
    private lateinit var db: ApexDatabase
    private lateinit var keyAlias: String
    private lateinit var metadataStore: SharedPreferencesVaultKeyMetadataStore

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        keyAlias = "apex_test_vault_key_${UUID.randomUUID().toString().replace("-", "").take(10)}"
        val prefs = context.getSharedPreferences("test_vault_meta_$keyAlias", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        metadataStore = SharedPreferencesVaultKeyMetadataStore(prefs)
    }

    @After
    fun tearDown() {
        runCatching {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (ks.containsAlias(keyAlias)) {
                ks.deleteEntry(keyAlias)
            }
        }
        runCatching { db.close() }
    }

    @Test
    fun androidKeyStore_createsRealAesGcmKey_encryptsAndDecryptsWithoutSoftwareFallback() {
        val keyProvider = AndroidKeystoreVaultKeyProvider(
            keyAlias = keyAlias,
            metadataStore = metadataStore,
            allowJvmTestSoftwareKeystoreFallback = false
        )
        val crypto = CredentialVaultCrypto(keyProvider)

        val secretText = "SuperSecPass!99"
        val secret = secretText.toCharArray()
        val encRes = crypto.encryptChars(secret)
        secret.fill('\u0000')
        assertTrue("Expected AndroidKeyStore AES-GCM encryption to succeed, got $encRes", encRes is VaultCryptoResult.Success)
        val payload = (encRes as VaultCryptoResult.Success).value
        assertTrue(payload.startsWith(CredentialVaultCrypto.PAYLOAD_PREFIX))
        assertFalse(payload.contains(secretText))

        // Verify key actually exists in AndroidKeyStore
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertTrue(ks.containsAlias(keyAlias))

        // Tamper ciphertext => AEADBadTagException => CorruptedPayload
        val tampered = payload.dropLast(4) + "AAAA"
        val tamperedRes = crypto.decryptToChars(tampered)
        assertTrue(tamperedRes is VaultCryptoResult.CorruptedPayload)

        // Valid payload decrypts back cleanly
        val decRes = crypto.decryptToChars(payload)
        assertTrue(decRes is VaultCryptoResult.Success)
        val plain = (decRes as VaultCryptoResult.Success).value
        try {
            assertEquals(secretText, String(plain))
        } finally {
            plain.fill('\u0000')
        }
    }

    @Test
    fun androidKeyStore_deletedOrInvalidatedKeyWithExistingRecordsIsNeverSilentlyReplaced() {
        val keyProvider = AndroidKeystoreVaultKeyProvider(
            keyAlias = keyAlias,
            metadataStore = metadataStore,
            allowJvmTestSoftwareKeystoreFallback = false
        )
        val crypto = CredentialVaultCrypto(keyProvider)
        val manager = CredentialVaultManager(
            dao = db.apexDao(),
            crypto = crypto,
            authManager = CredentialAuthenticationManager(
                context = context,
                initialPromptHandler = DeviceAuthPromptHandler { VaultAuthResult.Authenticated() }
            )
        )

        runBlocking {
            val added = manager.addCredential(
                websiteUrl = "https://portal.example.org/login",
                username = "alice@example.org",
                passwordChars = "SecretPass123!".toCharArray(),
                label = "Portal",
                requireVaultUnlocked = false
            )
            assertTrue(added is CredentialVaultOperationResult.Success)
        }
        metadataStore.hasEncryptedRecords = true

        // Simulate OS / security-change deletion or permanent invalidation of the Keystore entry
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        ks.deleteEntry(keyAlias)

        // Attempting decrypt or re-encrypt MUST return KeyInvalidated, never silently create a new key
        val decAttempt = keyProvider.resolveKeyForDecryption()
        assertTrue(decAttempt is VaultCryptoResult.KeyInvalidated)
        assertTrue(keyProvider.isKeyInvalidated())
        assertEquals(VaultStatus.KEY_INVALIDATED, manager.refreshVaultStatus())

        val encAttempt = keyProvider.resolveKeyForEncryption()
        assertTrue(encAttempt is VaultCryptoResult.KeyInvalidated)
        assertFalse(ks.containsAlias(keyAlias))
    }

    @Test
    fun vaultLifecycle_checksExpirationBackgroundingDeviceLockOriginMismatchDeletionAndRedaction() {
        var virtualTimeMs = 1_000_000L
        val keyProvider = AndroidKeystoreVaultKeyProvider(
            keyAlias = keyAlias,
            metadataStore = metadataStore,
            allowJvmTestSoftwareKeystoreFallback = false
        )
        val authManager = CredentialAuthenticationManager(
            context = context,
            sessionTimeoutMs = 5_000L,
            clock = { virtualTimeMs },
            initialPromptHandler = DeviceAuthPromptHandler { VaultAuthResult.Authenticated(virtualTimeMs) }
        )
        val manager = CredentialVaultManager(
            dao = db.apexDao(),
            crypto = CredentialVaultCrypto(keyProvider),
            authManager = authManager
        )

        // Check Android KeyguardManager query executes without crash
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val secureReported = authManager.isDeviceCredentialAvailable()
        if (km != null) {
            assertEquals(km.isDeviceSecure, secureReported)
        }

        val secretValue = "MyTopSecret9876"
        var savedId = ""
        runBlocking {
            assertTrue(manager.unlockVault() is CredentialVaultOperationResult.Success)
            assertTrue(authManager.isVaultUnlocked())

            val addRes = manager.addCredential(
                websiteUrl = "http://127.0.0.1:9001/signin",
                username = "bob.tester",
                passwordChars = secretValue.toCharArray(),
                label = "Local Auth",
                requireVaultUnlocked = true
            )
            assertTrue(addRes is CredentialVaultOperationResult.Success)
            savedId = (addRes as CredentialVaultOperationResult.Success).value.credentialId

            // Session timeout expires automatically after 5s
            virtualTimeMs += 6_000L
            assertFalse(authManager.isVaultUnlocked())
            assertEquals(VaultStatus.LOCKED, manager.refreshVaultStatus())

            // Re-unlock, then verify backgrounding locks immediately
            assertTrue(manager.unlockVault() is CredentialVaultOperationResult.Success)
            authManager.onAppBackgrounded()
            assertFalse(authManager.isVaultUnlocked())

            // Re-unlock, then verify device lock locks immediately
            assertTrue(manager.unlockVault() is CredentialVaultOperationResult.Success)
            authManager.onDeviceLocked()
            assertFalse(authManager.isVaultUnlocked())

            // Unlock and test origin mismatch refusal + replay authorization denial when cancelled
            assertTrue(manager.unlockVault() is CredentialVaultOperationResult.Success)
            val wrongOriginRes = manager.decryptFieldForAuthorizedReplay(
                credentialId = savedId,
                fieldRole = CredentialFieldRole.PASSWORD,
                livePageUrl = "http://127.0.0.1:9002/login",
                expectedStepOrigin = "http://127.0.0.1:9002"
            )
            assertTrue(wrongOriginRes is CredentialVaultOperationResult.OriginMismatch)

            // Cancelled biometric/device prompt blocks replay cleanly
            authManager.setPromptHandler(DeviceAuthPromptHandler {
                VaultAuthResult.Cancelled("Biometric dismissed")
            })
            val deniedReplay = manager.decryptFieldForAuthorizedReplay(
                credentialId = savedId,
                fieldRole = CredentialFieldRole.PASSWORD,
                livePageUrl = "http://127.0.0.1:9001/signin",
                expectedStepOrigin = "http://127.0.0.1:9001"
            )
            assertTrue(deniedReplay is CredentialVaultOperationResult.AuthCancelled)
        }

        // Verify sensitive-value redaction in AutomationEngine messages and real WebView credential fill
        LocalWebFixtureServer().use { server ->
            val targetOrigin = server.origin
            authManager.setPromptHandler(DeviceAuthPromptHandler {
                VaultAuthResult.Authenticated(virtualTimeMs)
            })
            var webCredId = ""
            runBlocking {
                assertTrue(manager.unlockVault() is CredentialVaultOperationResult.Success)
                val addLocal = manager.addCredential(
                    websiteUrl = server.urlFor("/login"),
                    username = "real_user",
                    passwordChars = secretValue.toCharArray(),
                    label = "Server Cred",
                    requireVaultUnlocked = true
                )
                assertTrue(addLocal is CredentialVaultOperationResult.Success)
                webCredId = (addLocal as CredentialVaultOperationResult.Success).value.credentialId
            }

            server.registerHtml(
                "/login",
                """
                <!DOCTYPE html>
                <html><body>
                  <form>
                    <input id="login-user" type="text" aria-label="Username" />
                    <input id="login-pass" type="password" aria-label="Password" />
                  </form>
                </body></html>
                """.trimIndent()
            )

            WebViewTestHost(tabId = "tab_vault_fill").use { host ->
                val loginUrl = server.urlFor("/login")
                host.loadAndAwait(loginUrl)

                val coordinator = CredentialFillCoordinator(
                    vaultManager = manager,
                    automationEngine = host.automationEngine
                )
                val fillStep = AutomationStep(
                    id = "fill_pw",
                    order = 1,
                    actionType = AutomationActionType.FILL_CREDENTIAL,
                    targetLabel = "Password",
                    selector = "#login-pass",
                    credentialReferenceId = webCredId,
                    credentialFieldRole = CredentialFieldRole.PASSWORD,
                    expectedOrigin = targetOrigin,
                    requiresCredential = true,
                    description = "Fill Password"
                )
                val fillResult = runBlocking {
                    coordinator.executeAuthorizedCredentialStep(
                        step = fillStep,
                        authorizedTabId = host.tabId,
                        currentActiveTabId = host.tabId,
                        authorizedWebView = host.webView,
                        currentWebViewProvider = { host.webView },
                        liveTabUrlProvider = { host.currentUrl() },
                        navigationSynchronizer = host.navigationSynchronizer,
                        userAuthorizedCredentialIds = setOf(webCredId)
                    )
                }

                assertTrue("Expected credential fill to succeed: ${fillResult.message}", fillResult.success)
                assertFalse("Plaintext secret MUST NEVER appear in step message", fillResult.message.contains(secretValue))
                assertEquals(secretValue.length.toString(), host.evalJsUnquoted("String(document.getElementById('login-pass').value.length)"))

                // Verify credential deletion removes encrypted row cleanly
                runBlocking {
                    val delRes = manager.deleteCredential(webCredId)
                    assertTrue(delRes is CredentialVaultOperationResult.Success)
                    assertNotEquals(null, db.apexDao().getCredentialVaultEntryById(savedId))
                    assertEquals(null, db.apexDao().getCredentialVaultEntryById(webCredId))
                }
            }
        }
    }
}
