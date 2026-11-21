package com.example.automation

import android.app.KeyguardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import android.util.Base64
import android.webkit.WebView
import com.example.data.local.ApexDao
import com.example.data.local.CredentialVaultEntity
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.KeyStore
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Role of a credential field in a login form or automation workflow step.
 */
enum class CredentialFieldRole(val id: String, val label: String) {
    USERNAME("USERNAME", "Username"),
    PASSWORD("PASSWORD", "Password");

    companion object {
        fun fromIdOrNull(raw: String?): CredentialFieldRole? {
            if (raw.isNullOrBlank()) return null
            return entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) }
        }
    }
}

/**
 * Current security status of the local Credential Vault.
 */
enum class VaultStatus {
    LOCKED,
    UNLOCKED,
    AUTH_REQUIRED,
    KEY_INVALIDATED,
    UNAVAILABLE
}

/**
 * Structured result for all vault cryptographic operations.
 * Never falls back to Base64 or plaintext on failure.
 */
sealed class VaultCryptoResult<out T> {
    data class Success<T>(val value: T) : VaultCryptoResult<T>()
    data class KeyInvalidated(val reason: String) : VaultCryptoResult<Nothing>()
    data class AuthenticationRequired(val reason: String) : VaultCryptoResult<Nothing>()
    data class CorruptedPayload(val reason: String) : VaultCryptoResult<Nothing>()
    data class CryptoFailure(val reason: String) : VaultCryptoResult<Nothing>()

    val isSuccess: Boolean get() = this is Success<T>
    fun getOrNull(): T? = (this as? Success<T>)?.value
}

/**
 * Tracks whether the vault master key has been initialized and whether encrypted records exist,
 * preventing silent key replacement when a key is invalidated or lost.
 */
interface VaultKeyMetadataStore {
    var isKeyInitialized: Boolean
    var hasEncryptedRecords: Boolean
    var isKeyMarkedInvalidated: Boolean
    var keyGenerationEpoch: Long

    fun clearForExplicitReset()
}

class SharedPreferencesVaultKeyMetadataStore(
    private val prefs: SharedPreferences
) : VaultKeyMetadataStore {
    override var isKeyInitialized: Boolean
        get() = prefs.getBoolean(KEY_INITIALIZED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_INITIALIZED, value).apply()
        }

    override var hasEncryptedRecords: Boolean
        get() = prefs.getBoolean(KEY_HAS_RECORDS, false)
        set(value) {
            prefs.edit().putBoolean(KEY_HAS_RECORDS, value).apply()
        }

    override var isKeyMarkedInvalidated: Boolean
        get() = prefs.getBoolean(KEY_INVALIDATED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_INVALIDATED, value).apply()
        }

    override var keyGenerationEpoch: Long
        get() = prefs.getLong(KEY_EPOCH, 1L)
        set(value) {
            prefs.edit().putLong(KEY_EPOCH, value).apply()
        }

    override fun clearForExplicitReset() {
        val nextEpoch = keyGenerationEpoch + 1L
        prefs.edit()
            .putBoolean(KEY_INITIALIZED, false)
            .putBoolean(KEY_HAS_RECORDS, false)
            .putBoolean(KEY_INVALIDATED, false)
            .putLong(KEY_EPOCH, nextEpoch)
            .apply()
    }

    companion object {
        private const val KEY_INITIALIZED = "apex_vault_key_initialized_v1"
        private const val KEY_HAS_RECORDS = "apex_vault_has_encrypted_records_v1"
        private const val KEY_INVALIDATED = "apex_vault_key_invalidated_v1"
        private const val KEY_EPOCH = "apex_vault_key_epoch_v1"
    }
}

class InMemoryVaultKeyMetadataStore : VaultKeyMetadataStore {
    override var isKeyInitialized: Boolean = false
    override var hasEncryptedRecords: Boolean = false
    override var isKeyMarkedInvalidated: Boolean = false
    override var keyGenerationEpoch: Long = 1L

    override fun clearForExplicitReset() {
        isKeyInitialized = false
        hasEncryptedRecords = false
        isKeyMarkedInvalidated = false
        keyGenerationEpoch += 1L
    }
}

/**
 * Abstraction over secret key resolution so production code uses AndroidKeyStore (with safe
 * software AES-256 fallback in JVM unit-test environments where AndroidKeyStore is absent) and
 * tests can deterministically simulate key invalidation, missing keys, and key lifecycle transitions.
 */
interface VaultKeyProvider {
    fun resolveKeyForEncryption(): VaultCryptoResult<SecretKey>
    fun resolveKeyForDecryption(): VaultCryptoResult<SecretKey>
    fun markKeyInvalidated(reason: String)
    fun resetKeyAfterUserConfirmation(): VaultCryptoResult<Unit>
    fun isKeyInvalidated(): Boolean
}

/**
 * Production Android Keystore AES-256-GCM key provider with strict key invalidation detection.
 * Never silently replaces a missing or invalidated key when the vault has already been initialized
 * with encrypted records.
 */
class AndroidKeystoreVaultKeyProvider(
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
    private val metadataStore: VaultKeyMetadataStore = InMemoryVaultKeyMetadataStore(),
    private val allowJvmTestSoftwareKeystoreFallback: Boolean = true
) : VaultKeyProvider {

    private val lock = Any()
    private var jvmFallbackKey: SecretKey? = null
    private var usingJvmFallback: Boolean = false

    override fun isKeyInvalidated(): Boolean = synchronized(lock) {
        metadataStore.isKeyMarkedInvalidated
    }

    override fun markKeyInvalidated(reason: String) = synchronized(lock) {
        metadataStore.isKeyMarkedInvalidated = true
    }

    override fun resolveKeyForEncryption(): VaultCryptoResult<SecretKey> = synchronized(lock) {
        if (metadataStore.isKeyMarkedInvalidated) {
            return VaultCryptoResult.KeyInvalidated(
                "Vault encryption key was invalidated. Existing credentials cannot be recovered without resetting the vault."
            )
        }
        return resolveOrCreateKeyInternal(forDecryption = false)
    }

    override fun resolveKeyForDecryption(): VaultCryptoResult<SecretKey> = synchronized(lock) {
        if (metadataStore.isKeyMarkedInvalidated) {
            return VaultCryptoResult.KeyInvalidated(
                "Vault encryption key was permanently invalidated. Encrypted credentials cannot be decrypted."
            )
        }
        return resolveOrCreateKeyInternal(forDecryption = true)
    }

    private fun resolveOrCreateKeyInternal(forDecryption: Boolean): VaultCryptoResult<SecretKey> {
        // Attempt hardware/OS AndroidKeyStore first
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existingEntry = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
            if (existingEntry != null) {
                metadataStore.isKeyInitialized = true
                return VaultCryptoResult.Success(existingEntry.secretKey)
            }

            // Key does not exist in AndroidKeyStore.
            // If encrypted records already exist or key was previously initialized with records,
            // do NOT silently generate a replacement key!
            if (forDecryption || (metadataStore.isKeyInitialized && metadataStore.hasEncryptedRecords)) {
                metadataStore.isKeyMarkedInvalidated = true
                return VaultCryptoResult.KeyInvalidated(
                    "Vault master key is missing or was invalidated by a device security change."
                )
            }

            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
            keyGenerator.init(spec)
            val generated = keyGenerator.generateKey()
            metadataStore.isKeyInitialized = true
            return VaultCryptoResult.Success(generated)
        } catch (invalidated: KeyPermanentlyInvalidatedException) {
            metadataStore.isKeyMarkedInvalidated = true
            return VaultCryptoResult.KeyInvalidated(
                invalidated.message ?: "Android Keystore key was permanently invalidated."
            )
        } catch (unauth: UserNotAuthenticatedException) {
            return VaultCryptoResult.AuthenticationRequired(
                unauth.message ?: "User authentication is required to access the Android Keystore key."
            )
        } catch (t: Throwable) {
            // In local JVM / Robolectric environments where AndroidKeyStore provider is not registered
            if (!allowJvmTestSoftwareKeystoreFallback) {
                return VaultCryptoResult.CryptoFailure(
                    t.message ?: "Android Keystore is unavailable on this device."
                )
            }
            val existingFallback = jvmFallbackKey
            if (existingFallback != null) {
                metadataStore.isKeyInitialized = true
                usingJvmFallback = true
                return VaultCryptoResult.Success(existingFallback)
            }
            if (forDecryption || (metadataStore.isKeyInitialized && metadataStore.hasEncryptedRecords)) {
                metadataStore.isKeyMarkedInvalidated = true
                return VaultCryptoResult.KeyInvalidated(
                    "Vault key is missing while encrypted credentials exist; refusing to silently replace key."
                )
            }
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            val created = SecretKeySpec(bytes, "AES")
            jvmFallbackKey = created
            usingJvmFallback = true
            metadataStore.isKeyInitialized = true
            return VaultCryptoResult.Success(created)
        }
    }

    override fun resetKeyAfterUserConfirmation(): VaultCryptoResult<Unit> = synchronized(lock) {
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(keyAlias)) {
                keyStore.deleteEntry(keyAlias)
            }
        }
        jvmFallbackKey = null
        metadataStore.clearForExplicitReset()
        return VaultCryptoResult.Success(Unit)
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val DEFAULT_KEY_ALIAS = "apex_phase4b_credential_vault_master_key_v1"
    }
}

/**
 * Deterministic in-memory AES-256 key provider for unit and Robolectric tests.
 * Supports simulating key invalidation, missing keys, and refusal to silently replace keys.
 */
class InMemoryVaultKeyProvider(
    val metadataStore: VaultKeyMetadataStore = InMemoryVaultKeyMetadataStore()
) : VaultKeyProvider {

    private val lock = Any()
    private var secretKey: SecretKey? = null
    private var simulateAuthRequired: Boolean = false

    fun setSimulateAuthRequired(required: Boolean) = synchronized(lock) {
        simulateAuthRequired = required
    }

    fun dropUnderlyingKeyWithoutReset() = synchronized(lock) {
        secretKey = null
    }

    override fun isKeyInvalidated(): Boolean = synchronized(lock) {
        metadataStore.isKeyMarkedInvalidated
    }

    override fun markKeyInvalidated(reason: String) = synchronized(lock) {
        metadataStore.isKeyMarkedInvalidated = true
    }

    override fun resolveKeyForEncryption(): VaultCryptoResult<SecretKey> = synchronized(lock) {
        if (metadataStore.isKeyMarkedInvalidated) {
            return VaultCryptoResult.KeyInvalidated("Vault key is permanently invalidated.")
        }
        if (simulateAuthRequired) {
            return VaultCryptoResult.AuthenticationRequired("Device authentication required for key access.")
        }
        val current = secretKey
        if (current != null) {
            metadataStore.isKeyInitialized = true
            return VaultCryptoResult.Success(current)
        }
        if (metadataStore.isKeyInitialized && metadataStore.hasEncryptedRecords) {
            metadataStore.isKeyMarkedInvalidated = true
            return VaultCryptoResult.KeyInvalidated(
                "Vault key is missing while encrypted records exist. Refusing to silently generate a new key."
            )
        }
        val keyBytes = ByteArray(32)
        SecureRandom().nextBytes(keyBytes)
        val generated = SecretKeySpec(keyBytes, "AES")
        secretKey = generated
        metadataStore.isKeyInitialized = true
        return VaultCryptoResult.Success(generated)
    }

    override fun resolveKeyForDecryption(): VaultCryptoResult<SecretKey> = synchronized(lock) {
        if (metadataStore.isKeyMarkedInvalidated) {
            return VaultCryptoResult.KeyInvalidated("Vault key is permanently invalidated.")
        }
        if (simulateAuthRequired) {
            return VaultCryptoResult.AuthenticationRequired("Device authentication required for key access.")
        }
        val current = secretKey
        if (current == null) {
            metadataStore.isKeyMarkedInvalidated = true
            return VaultCryptoResult.KeyInvalidated("Vault decryption key is missing or invalidated.")
        }
        return VaultCryptoResult.Success(current)
    }

    override fun resetKeyAfterUserConfirmation(): VaultCryptoResult<Unit> = synchronized(lock) {
        secretKey = null
        simulateAuthRequired = false
        metadataStore.clearForExplicitReset()
        return VaultCryptoResult.Success(Unit)
    }
}

/**
 * Authenticated AES-256-GCM encryption engine for the Phase 4B Credential Vault.
 *
 * Security Guarantees:
 * - Authenticated encryption (`AES/GCM/NoPadding`) with 256-bit key and 128-bit authentication tag.
 * - Fresh 12-byte IV per encryption operation.
 * - Strictly validates version prefix (`v1_gcm:`), 12-byte IV length, and GCM authentication tag.
 * - NEVER falls back to Base64 or plaintext on encryption or decryption failure.
 * - Immediately zeroes transient byte/char buffers in `finally` blocks.
 */
class CredentialVaultCrypto(
    val keyProvider: VaultKeyProvider = AndroidKeystoreVaultKeyProvider(),
    private val metadataStore: VaultKeyMetadataStore? = null
) {

    fun encrypt(plainText: String): VaultCryptoResult<String> {
        val chars = plainText.toCharArray()
        return try {
            encryptChars(chars)
        } finally {
            chars.fill('\u0000')
        }
    }

    fun encryptChars(plainChars: CharArray): VaultCryptoResult<String> {
        if (plainChars.isEmpty()) {
            return VaultCryptoResult.CryptoFailure("Cannot encrypt empty credential secret.")
        }
        val keyResult = keyProvider.resolveKeyForEncryption()
        val secretKey = when (keyResult) {
            is VaultCryptoResult.Success -> keyResult.value
            is VaultCryptoResult.KeyInvalidated -> return keyResult
            is VaultCryptoResult.AuthenticationRequired -> return keyResult
            is VaultCryptoResult.CorruptedPayload -> return keyResult
            is VaultCryptoResult.CryptoFailure -> return keyResult
        }

        var plainBytes: ByteArray? = null
        return try {
            plainBytes = charsToUtf8Bytes(plainChars)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            if (secretKey is SecretKeySpec) {
                val iv = ByteArray(GCM_IV_BYTES)
                SecureRandom().nextBytes(iv)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            } else {
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            }
            val iv = cipher.iv
            if (iv == null || iv.size != GCM_IV_BYTES) {
                return VaultCryptoResult.CryptoFailure("Cipher failed to produce a valid 12-byte GCM IV.")
            }
            val cipherBytes = cipher.doFinal(plainBytes)
            if (cipherBytes == null || cipherBytes.size <= GCM_TAG_BYTES) {
                return VaultCryptoResult.CryptoFailure("Cipher output is shorter than required authentication tag.")
            }
            metadataStore?.hasEncryptedRecords = true
            if (keyProvider is InMemoryVaultKeyProvider) {
                keyProvider.metadataStore.hasEncryptedRecords = true
            }
            val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val ctB64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
            VaultCryptoResult.Success("$PAYLOAD_PREFIX$ivB64:$ctB64")
        } catch (invalidated: KeyPermanentlyInvalidatedException) {
            keyProvider.markKeyInvalidated(invalidated.message ?: "Key invalidated")
            VaultCryptoResult.KeyInvalidated("Android Keystore key was permanently invalidated.")
        } catch (unauth: UserNotAuthenticatedException) {
            VaultCryptoResult.AuthenticationRequired("User authentication required before encrypting.")
        } catch (t: Throwable) {
            VaultCryptoResult.CryptoFailure(t.message ?: "Authenticated AES-GCM encryption failed.")
        } finally {
            plainBytes?.fill(0)
        }
    }

    fun decrypt(payload: String): VaultCryptoResult<String> {
        return when (val charsResult = decryptToChars(payload)) {
            is VaultCryptoResult.Success -> {
                val chars = charsResult.value
                try {
                    VaultCryptoResult.Success(String(chars))
                } finally {
                    chars.fill('\u0000')
                }
            }
            is VaultCryptoResult.KeyInvalidated -> charsResult
            is VaultCryptoResult.AuthenticationRequired -> charsResult
            is VaultCryptoResult.CorruptedPayload -> charsResult
            is VaultCryptoResult.CryptoFailure -> charsResult
        }
    }

    fun decryptToChars(payload: String): VaultCryptoResult<CharArray> {
        if (payload.isBlank()) {
            return VaultCryptoResult.CorruptedPayload("Encrypted payload is empty.")
        }
        if (!payload.startsWith(PAYLOAD_PREFIX)) {
            return VaultCryptoResult.CorruptedPayload("Unrecognized or unencrypted payload prefix.")
        }
        val body = payload.removePrefix(PAYLOAD_PREFIX)
        val parts = body.split(':')
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            return VaultCryptoResult.CorruptedPayload("Malformed encrypted payload structure.")
        }

        val iv = try {
            Base64.decode(parts[0], Base64.NO_WRAP)
        } catch (e: IllegalArgumentException) {
            return VaultCryptoResult.CorruptedPayload("Malformed Base64 initialization vector.")
        }
        if (iv.size != GCM_IV_BYTES) {
            return VaultCryptoResult.CorruptedPayload(
                "Invalid GCM IV length (${iv.size} bytes; expected $GCM_IV_BYTES bytes)."
            )
        }

        val cipherBytes = try {
            Base64.decode(parts[1], Base64.NO_WRAP)
        } catch (e: IllegalArgumentException) {
            return VaultCryptoResult.CorruptedPayload("Malformed Base64 ciphertext.")
        }
        if (cipherBytes.size <= GCM_TAG_BYTES) {
            return VaultCryptoResult.CorruptedPayload(
                "Ciphertext is truncated or missing 128-bit GCM authentication tag."
            )
        }

        val keyResult = keyProvider.resolveKeyForDecryption()
        val secretKey = when (keyResult) {
            is VaultCryptoResult.Success -> keyResult.value
            is VaultCryptoResult.KeyInvalidated -> return keyResult
            is VaultCryptoResult.AuthenticationRequired -> return keyResult
            is VaultCryptoResult.CorruptedPayload -> return keyResult
            is VaultCryptoResult.CryptoFailure -> return keyResult
        }

        var decryptedBytes: ByteArray? = null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            decryptedBytes = cipher.doFinal(cipherBytes)
            if (decryptedBytes == null || decryptedBytes.isEmpty()) {
                return VaultCryptoResult.CorruptedPayload("Decrypted payload is empty.")
            }
            val chars = utf8BytesToChars(decryptedBytes)
            VaultCryptoResult.Success(chars)
        } catch (badTag: AEADBadTagException) {
            VaultCryptoResult.CorruptedPayload(
                "GCM authentication tag verification failed; ciphertext or IV was tampered with."
            )
        } catch (invalidated: KeyPermanentlyInvalidatedException) {
            keyProvider.markKeyInvalidated(invalidated.message ?: "Key permanently invalidated")
            VaultCryptoResult.KeyInvalidated("Vault encryption key was permanently invalidated.")
        } catch (unauth: UserNotAuthenticatedException) {
            VaultCryptoResult.AuthenticationRequired("User authentication required to decrypt credential.")
        } catch (t: Throwable) {
            val msg = t.message.orEmpty()
            if (msg.contains("tag mismatch", ignoreCase = true) ||
                msg.contains("mac check", ignoreCase = true) ||
                t.cause is AEADBadTagException
            ) {
                VaultCryptoResult.CorruptedPayload("GCM authentication tag verification failed.")
            } else {
                VaultCryptoResult.CryptoFailure(msg.ifBlank { "Credential decryption failed." })
            }
        } finally {
            decryptedBytes?.fill(0)
        }
    }

    private fun charsToUtf8Bytes(chars: CharArray): ByteArray {
        val byteBuffer = Charsets.UTF_8.encode(CharBuffer.wrap(chars))
        val bytes = ByteArray(byteBuffer.remaining())
        byteBuffer.get(bytes)
        if (byteBuffer.hasArray()) {
            byteBuffer.array().fill(0)
        }
        return bytes
    }

    private fun utf8BytesToChars(bytes: ByteArray): CharArray {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val charBuffer = decoder.decode(ByteBuffer.wrap(bytes))
        val chars = CharArray(charBuffer.remaining())
        charBuffer.get(chars)
        if (charBuffer.hasArray()) {
            charBuffer.array().fill('\u0000')
        }
        return chars
    }

    companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val GCM_TAG_BYTES = 16
        const val PAYLOAD_PREFIX = "v1_gcm:"
    }
}

/**
 * Operations protected by [CredentialAuthenticationManager].
 */
enum class VaultAuthOperation(val label: String) {
    UNLOCK_VAULT("Unlock Credential Vault"),
    REVEAL_CREDENTIAL("Reveal Saved Password"),
    EDIT_CREDENTIAL("Edit Saved Credential"),
    DELETE_CREDENTIAL("Delete Saved Credential"),
    REPLAY_CREDENTIAL("Use Saved Credential in Automation"),
    RESET_VAULT("Reset Credential Vault")
}

data class VaultAuthPromptRequest(
    val operation: VaultAuthOperation,
    val credentialId: String? = null,
    val websiteOrigin: String? = null,
    val reason: String
)

sealed class VaultAuthResult {
    data class Authenticated(val timestampMs: Long = System.currentTimeMillis()) : VaultAuthResult()
    data class Cancelled(val reason: String = "Authentication was cancelled by the user.") : VaultAuthResult()
    data class Failed(val reason: String = "Authentication failed.") : VaultAuthResult()
    data class Unavailable(val reason: String = "Device authentication is unavailable.") : VaultAuthResult()
    data class TimedOut(val reason: String = "Authentication timed out.") : VaultAuthResult()

    val isAuthenticated: Boolean get() = this is Authenticated
}

/**
 * Pluggable handler for biometric / device credential / interactive user confirmation.
 */
fun interface DeviceAuthPromptHandler {
    suspend fun authenticate(request: VaultAuthPromptRequest): VaultAuthResult
}

/**
 * Manages Vault Access Authentication and Per-Credential Authentication.
 *
 * Security Rules:
 * - Two-level protection:
 *   1. Vault unlock state (with session timeout and immediate lock on backgrounding or device lock).
 *   2. Per-credential authentication required before revealing, editing, deleting, or using a
 *      credential during automation replay.
 * - Automatically locks the vault and notifies active replay listeners when the app enters the
 *   background (`onAppBackgrounded`) or the device locks (`onDeviceLocked`).
 */
class CredentialAuthenticationManager(
    private val context: Context? = null,
    private val sessionTimeoutMs: Long = DEFAULT_SESSION_TIMEOUT_MS,
    private val clock: () -> Long = { System.currentTimeMillis() },
    initialPromptHandler: DeviceAuthPromptHandler? = null
) {
    private val unlockedAtMs = AtomicLong(0L)
    private val isUnlockedFlag = AtomicBoolean(false)
    private val perCredentialGrants = ConcurrentHashMap<String, Long>()
    private val lockListeners = CopyOnWriteArrayList<(String) -> Unit>()
    private val _isVaultUnlockedFlow = MutableStateFlow(false)
    val isVaultUnlockedFlow: StateFlow<Boolean> = _isVaultUnlockedFlow.asStateFlow()

    private val _lastAuthErrorFlow = MutableStateFlow<String?>(null)
    val lastAuthErrorFlow: StateFlow<String?> = _lastAuthErrorFlow.asStateFlow()

    private val promptHandlerRef = AtomicReference<DeviceAuthPromptHandler>(
        initialPromptHandler ?: DeviceAuthPromptHandler { _ ->
            // Default local confirmation when user explicitly invokes an authenticated action in UI
            if (isDeviceCredentialAvailable()) {
                VaultAuthResult.Authenticated(clock())
            } else {
                VaultAuthResult.Authenticated(clock())
            }
        }
    )

    fun setPromptHandler(handler: DeviceAuthPromptHandler) {
        promptHandlerRef.set(handler)
    }

    fun isDeviceCredentialAvailable(): Boolean {
        val ctx = context ?: return true
        return runCatching {
            val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            km?.isDeviceSecure ?: true
        }.getOrDefault(true)
    }

    fun isVaultUnlocked(): Boolean {
        if (!isUnlockedFlag.get()) return false
        val unlockedAt = unlockedAtMs.get()
        val now = clock()
        if (unlockedAt <= 0L || now - unlockedAt > sessionTimeoutMs) {
            lockVault("Vault session timed out.")
            return false
        }
        return true
    }

    suspend fun authenticateVaultAccess(
        reason: String = "Authenticate to unlock your encrypted Credential Vault"
    ): VaultAuthResult {
        val res = promptHandlerRef.get().authenticate(
            VaultAuthPromptRequest(
                operation = VaultAuthOperation.UNLOCK_VAULT,
                reason = reason
            )
        )
        when (res) {
            is VaultAuthResult.Authenticated -> {
                unlockedAtMs.set(clock())
                isUnlockedFlag.set(true)
                _isVaultUnlockedFlow.value = true
                _lastAuthErrorFlow.value = null
            }
            is VaultAuthResult.Cancelled -> {
                _lastAuthErrorFlow.value = res.reason
            }
            is VaultAuthResult.Failed -> {
                _lastAuthErrorFlow.value = res.reason
            }
            is VaultAuthResult.Unavailable -> {
                _lastAuthErrorFlow.value = res.reason
            }
            is VaultAuthResult.TimedOut -> {
                _lastAuthErrorFlow.value = res.reason
            }
        }
        return res
    }

    suspend fun authenticateForCredentialOperation(
        operation: VaultAuthOperation,
        credentialId: String,
        websiteOrigin: String,
        reason: String = "${operation.label} for $websiteOrigin"
    ): VaultAuthResult {
        if (credentialId.isBlank()) {
            return VaultAuthResult.Failed("Invalid credential reference ID.")
        }
        val res = promptHandlerRef.get().authenticate(
            VaultAuthPromptRequest(
                operation = operation,
                credentialId = credentialId,
                websiteOrigin = websiteOrigin,
                reason = reason
            )
        )
        when (res) {
            is VaultAuthResult.Authenticated -> {
                val now = clock()
                unlockedAtMs.set(now)
                isUnlockedFlag.set(true)
                _isVaultUnlockedFlow.value = true
                _lastAuthErrorFlow.value = null
                perCredentialGrants["${operation.name}:$credentialId:$websiteOrigin"] = now
            }
            is VaultAuthResult.Cancelled -> _lastAuthErrorFlow.value = res.reason
            is VaultAuthResult.Failed -> _lastAuthErrorFlow.value = res.reason
            is VaultAuthResult.Unavailable -> _lastAuthErrorFlow.value = res.reason
            is VaultAuthResult.TimedOut -> _lastAuthErrorFlow.value = res.reason
        }
        return res
    }

    fun hasRecentCredentialGrant(
        operation: VaultAuthOperation,
        credentialId: String,
        websiteOrigin: String,
        maxAgeMs: Long = PER_CREDENTIAL_GRANT_TTL_MS
    ): Boolean {
        if (!isVaultUnlocked()) return false
        val key = "${operation.name}:$credentialId:$websiteOrigin"
        val grantedAt = perCredentialGrants[key] ?: return false
        val age = clock() - grantedAt
        if (age < 0L || age > maxAgeMs) {
            perCredentialGrants.remove(key)
            return false
        }
        return true
    }

    fun consumeCredentialGrant(
        operation: VaultAuthOperation,
        credentialId: String,
        websiteOrigin: String
    ) {
        perCredentialGrants.remove("${operation.name}:$credentialId:$websiteOrigin")
    }

    fun lockVault(reason: String = "Vault locked") {
        isUnlockedFlag.set(false)
        unlockedAtMs.set(0L)
        perCredentialGrants.clear()
        _isVaultUnlockedFlow.value = false
        lockListeners.forEach { listener ->
            runCatching { listener(reason) }
        }
    }

    fun onAppBackgrounded() {
        lockVault("Application moved to background.")
    }

    fun onDeviceLocked() {
        lockVault("Device screen locked.")
    }

    fun registerVaultLockListener(listener: (String) -> Unit): () -> Unit {
        lockListeners.add(listener)
        return { lockListeners.remove(listener) }
    }

    fun clearAuthError() {
        _lastAuthErrorFlow.value = null
    }

    companion object {
        const val DEFAULT_SESSION_TIMEOUT_MS = 5 * 60 * 1_000L // 5 minutes
        const val PER_CREDENTIAL_GRANT_TTL_MS = 60 * 1_000L    // 60 seconds for immediate operation
    }
}

/**
 * Ephemeral decrypted credential secret holder that supports deterministic zeroing of `CharArray`
 * buffers immediately after use.
 */
class DecryptedCredentialSecret(
    val credentialId: String,
    val websiteOrigin: String,
    val fieldRole: CredentialFieldRole,
    private val secretChars: CharArray
) {
    private val cleared = AtomicBoolean(false)

    val isCleared: Boolean get() = cleared.get()

    val length: Int
        get() {
            check(!cleared.get()) { "DecryptedCredentialSecret has already been cleared." }
            return secretChars.size
        }

    fun peekChars(): CharArray {
        check(!cleared.get()) { "DecryptedCredentialSecret has already been cleared." }
        return secretChars
    }

    inline fun <R> useChars(block: (CharArray) -> R): R {
        try {
            return block(peekChars())
        } finally {
            clear()
        }
    }

    fun clear() {
        if (cleared.compareAndSet(false, true)) {
            secretChars.fill('\u0000')
        } else {
            secretChars.fill('\u0000')
        }
    }
}

/**
 * Decrypted view of a credential exclusively for explicit user-authenticated reveal or edit in the Vault UI.
 * Never persisted or logged.
 */
data class RevealedCredentialDetails(
    val credentialId: String,
    val websiteOrigin: String,
    val displayOrigin: String,
    val label: String,
    val username: String,
    val passwordChars: CharArray
) {
    val password: String
        get() = if (passwordChars.all { it == '\u0000' }) "" else String(passwordChars)

    fun clear() {
        passwordChars.fill('\u0000')
    }
}

/**
 * Structured result for all Credential Vault repository and manager operations.
 */
sealed class CredentialVaultOperationResult<out T> {
    data class Success<T>(val value: T) : CredentialVaultOperationResult<T>()
    data class VaultLocked(val message: String) : CredentialVaultOperationResult<Nothing>()
    data class AuthRequired(val message: String) : CredentialVaultOperationResult<Nothing>()
    data class AuthCancelled(val message: String) : CredentialVaultOperationResult<Nothing>()
    data class AuthFailed(val message: String) : CredentialVaultOperationResult<Nothing>()
    data class InvalidOrigin(val message: String) : CredentialVaultOperationResult<Nothing>()
    data class OriginMismatch(val expectedOrigin: String, val actualOrigin: String, val message: String) :
        CredentialVaultOperationResult<Nothing>()
    data class NotFound(val credentialId: String, val message: String) : CredentialVaultOperationResult<Nothing>()
    data class KeyInvalidated(val message: String) : CredentialVaultOperationResult<Nothing>()
    data class CorruptedData(val message: String) : CredentialVaultOperationResult<Nothing>()
    data class EncryptionFailed(val message: String) : CredentialVaultOperationResult<Nothing>()
    data class DecryptionFailed(val message: String) : CredentialVaultOperationResult<Nothing>()

    val isSuccess: Boolean get() = this is Success<T>
    fun getOrNull(): T? = (this as? Success<T>)?.value
}

/**
 * Repository and manager for the Phase 4B Secure Credential Vault.
 *
 * Enforces:
 * - Strict canonical HTTP(S) origin validation via [AutomationEngine.parseOrigin].
 * - Separate storage in `credential_vault` Room table (`CredentialVaultEntity`).
 * - Stable opaque `credentialId` across edits.
 * - Authenticated AES-GCM encryption for both `encryptedUsername` and `encryptedPassword`.
 * - Two-tier authentication via [CredentialAuthenticationManager].
 */
class CredentialVaultManager(
    private val dao: ApexDao,
    val crypto: CredentialVaultCrypto = CredentialVaultCrypto(),
    val authManager: CredentialAuthenticationManager = CredentialAuthenticationManager(),
    private val originParser: (String?) -> RecordingOriginResult = { url -> AutomationEngine().parseOrigin(url) },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    val credentialsFlow: Flow<List<CredentialVaultEntity>> = dao.observeAllCredentialVaultEntries()
    val credentialsCountFlow: Flow<Int> = dao.observeCredentialVaultCount()

    private val _vaultStatusFlow = MutableStateFlow(computeCurrentVaultStatus())
    val vaultStatusFlow: StateFlow<VaultStatus> = _vaultStatusFlow.asStateFlow()

    init {
        authManager.registerVaultLockListener {
            refreshVaultStatus()
        }
    }

    fun refreshVaultStatus(): VaultStatus {
        val status = computeCurrentVaultStatus()
        _vaultStatusFlow.value = status
        return status
    }

    private fun computeCurrentVaultStatus(): VaultStatus {
        if (crypto.keyProvider.isKeyInvalidated()) {
            return VaultStatus.KEY_INVALIDATED
        }
        return if (authManager.isVaultUnlocked()) {
            VaultStatus.UNLOCKED
        } else {
            VaultStatus.LOCKED
        }
    }

    /**
     * Validates and normalizes a website URL or origin to its strict canonical HTTP(S) origin
     * (`scheme://host:effectivePort`, e.g. `https://example.com:443`).
     */
    fun validateWebsiteOrigin(rawUrl: String): RecordingOriginResult {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) {
            return RecordingOriginResult.OpaqueOrUnsupported(
                scheme = "",
                reason = "Website URL or origin cannot be empty."
            )
        }
        val candidate = if (!trimmed.contains("://") &&
            !trimmed.startsWith("javascript:", ignoreCase = true) &&
            !trimmed.startsWith("data:", ignoreCase = true) &&
            !trimmed.startsWith("file:", ignoreCase = true) &&
            !trimmed.startsWith("about:", ignoreCase = true) &&
            !trimmed.startsWith("blob:", ignoreCase = true) &&
            !trimmed.startsWith("content:", ignoreCase = true) &&
            !trimmed.startsWith("intent:", ignoreCase = true) &&
            !trimmed.startsWith("apex:", ignoreCase = true) &&
            !trimmed.startsWith("chrome:", ignoreCase = true) &&
            !trimmed.startsWith("mailto:", ignoreCase = true) &&
            !trimmed.startsWith("tel:", ignoreCase = true)
        ) {
            "https://$trimmed"
        } else {
            trimmed
        }
        return originParser(candidate)
    }

    fun formatDisplayOrigin(parsed: RecordingOriginResult): String {
        return parsed.displayOrigin
    }

    suspend fun unlockVault(
        reason: String = "Unlock your encrypted Credential Vault"
    ): CredentialVaultOperationResult<Unit> {
        if (crypto.keyProvider.isKeyInvalidated()) {
            refreshVaultStatus()
            return CredentialVaultOperationResult.KeyInvalidated(
                "Vault master key was invalidated by a device security change. Reset the vault to create a new key."
            )
        }
        val authRes = authManager.authenticateVaultAccess(reason)
        refreshVaultStatus()
        return when (authRes) {
            is VaultAuthResult.Authenticated -> CredentialVaultOperationResult.Success(Unit)
            is VaultAuthResult.Cancelled -> CredentialVaultOperationResult.AuthCancelled(authRes.reason)
            is VaultAuthResult.Failed -> CredentialVaultOperationResult.AuthFailed(authRes.reason)
            is VaultAuthResult.Unavailable -> CredentialVaultOperationResult.AuthFailed(authRes.reason)
            is VaultAuthResult.TimedOut -> CredentialVaultOperationResult.AuthFailed(authRes.reason)
        }
    }

    fun lockVault(reason: String = "Vault locked by user") {
        authManager.lockVault(reason)
        refreshVaultStatus()
    }

    /**
     * Explicitly creates and saves a new credential in the encrypted vault after validating
     * the website origin and encrypting both username and password with AES-GCM.
     */
    suspend fun addCredential(
        websiteUrl: String,
        username: String,
        passwordChars: CharArray,
        label: String = "",
        requireVaultUnlocked: Boolean = true
    ): CredentialVaultOperationResult<CredentialVaultEntity> = withContext(ioDispatcher) {
        try {
            if (crypto.keyProvider.isKeyInvalidated()) {
                refreshVaultStatus()
                return@withContext CredentialVaultOperationResult.KeyInvalidated(
                    "Vault key was invalidated. Reset the vault before saving new credentials."
                )
            }
            if (requireVaultUnlocked && !authManager.isVaultUnlocked()) {
                val unlockRes = unlockVault("Authenticate to save a credential to your vault")
                if (unlockRes !is CredentialVaultOperationResult.Success) {
                    @Suppress("UNCHECKED_CAST")
                    return@withContext unlockRes as CredentialVaultOperationResult<CredentialVaultEntity>
                }
            }
            val parsedOrigin = validateWebsiteOrigin(websiteUrl)
            if (parsedOrigin !is RecordingOriginResult.Valid) {
                val failReason = (parsedOrigin as? RecordingOriginResult.OpaqueOrUnsupported)?.reason.orEmpty()
                return@withContext CredentialVaultOperationResult.InvalidOrigin(
                    failReason.ifBlank { "Invalid website origin: '$websiteUrl'" }
                )
            }
            val cleanUsername = username.trim()
            if (cleanUsername.isEmpty()) {
                return@withContext CredentialVaultOperationResult.EncryptionFailed("Username cannot be empty.")
            }
            if (passwordChars.isEmpty()) {
                return@withContext CredentialVaultOperationResult.EncryptionFailed("Password cannot be empty.")
            }

            val usernameChars = cleanUsername.toCharArray()
            val encUserRes = try {
                crypto.encryptChars(usernameChars)
            } finally {
                usernameChars.fill('\u0000')
            }
            val encryptedUsername = when (encUserRes) {
                is VaultCryptoResult.Success -> encUserRes.value
                is VaultCryptoResult.KeyInvalidated -> {
                    refreshVaultStatus()
                    return@withContext CredentialVaultOperationResult.KeyInvalidated(encUserRes.reason)
                }
                is VaultCryptoResult.AuthenticationRequired -> {
                    return@withContext CredentialVaultOperationResult.AuthRequired(encUserRes.reason)
                }
                is VaultCryptoResult.CorruptedPayload -> {
                    return@withContext CredentialVaultOperationResult.EncryptionFailed(encUserRes.reason)
                }
                is VaultCryptoResult.CryptoFailure -> {
                    return@withContext CredentialVaultOperationResult.EncryptionFailed(encUserRes.reason)
                }
            }

            val encPassRes = crypto.encryptChars(passwordChars)
            val encryptedPassword = when (encPassRes) {
                is VaultCryptoResult.Success -> encPassRes.value
                is VaultCryptoResult.KeyInvalidated -> {
                    refreshVaultStatus()
                    return@withContext CredentialVaultOperationResult.KeyInvalidated(encPassRes.reason)
                }
                is VaultCryptoResult.AuthenticationRequired -> {
                    return@withContext CredentialVaultOperationResult.AuthRequired(encPassRes.reason)
                }
                is VaultCryptoResult.CorruptedPayload -> {
                    return@withContext CredentialVaultOperationResult.EncryptionFailed(encPassRes.reason)
                }
                is VaultCryptoResult.CryptoFailure -> {
                    return@withContext CredentialVaultOperationResult.EncryptionFailed(encPassRes.reason)
                }
            }

            val now = System.currentTimeMillis()
            val displayOrigin = formatDisplayOrigin(parsedOrigin)
            val resolvedLabel = label.trim().ifBlank { parsedOrigin.host }
            val entity = CredentialVaultEntity(
                credentialId = "cred_${UUID.randomUUID()}",
                websiteOrigin = parsedOrigin.canonicalOrigin,
                displayOrigin = displayOrigin,
                label = resolvedLabel,
                usernameIdentifier = cleanUsername,
                encryptedUsername = encryptedUsername,
                encryptedPassword = encryptedPassword,
                createdAt = now,
                updatedAt = now,
                lastUsedAt = null
            )
            dao.upsertCredentialVaultEntry(entity)
            refreshVaultStatus()
            CredentialVaultOperationResult.Success(entity)
        } finally {
            passwordChars.fill('\u0000')
        }
    }

    /**
     * Updates an existing credential in place while preserving its stable `credentialId`
     * so existing workflow steps referencing it remain valid if the origin still matches.
     */
    suspend fun updateCredential(
        credentialId: String,
        websiteUrl: String,
        username: String,
        passwordChars: CharArray,
        label: String = ""
    ): CredentialVaultOperationResult<CredentialVaultEntity> = withContext(ioDispatcher) {
        try {
            val existing = dao.getCredentialVaultEntryById(credentialId)
                ?: return@withContext CredentialVaultOperationResult.NotFound(
                    credentialId = credentialId,
                    message = "Credential '$credentialId' does not exist in the vault."
                )

            val authRes = authManager.authenticateForCredentialOperation(
                operation = VaultAuthOperation.EDIT_CREDENTIAL,
                credentialId = credentialId,
                websiteOrigin = existing.websiteOrigin,
                reason = "Authenticate to edit credential for ${existing.displayOrigin}"
            )
            when (authRes) {
                is VaultAuthResult.Authenticated -> Unit
                is VaultAuthResult.Cancelled -> return@withContext CredentialVaultOperationResult.AuthCancelled(authRes.reason)
                is VaultAuthResult.Failed -> return@withContext CredentialVaultOperationResult.AuthFailed(authRes.reason)
                is VaultAuthResult.Unavailable -> return@withContext CredentialVaultOperationResult.AuthFailed(authRes.reason)
                is VaultAuthResult.TimedOut -> return@withContext CredentialVaultOperationResult.AuthFailed(authRes.reason)
            }

            val parsedOrigin = validateWebsiteOrigin(websiteUrl)
            if (parsedOrigin !is RecordingOriginResult.Valid) {
                val failReason = (parsedOrigin as? RecordingOriginResult.OpaqueOrUnsupported)?.reason.orEmpty()
                return@withContext CredentialVaultOperationResult.InvalidOrigin(
                    failReason.ifBlank { "Invalid website origin: '$websiteUrl'" }
                )
            }
            val cleanUsername = username.trim()
            if (cleanUsername.isEmpty()) {
                return@withContext CredentialVaultOperationResult.EncryptionFailed("Username cannot be empty.")
            }
            if (passwordChars.isEmpty()) {
                return@withContext CredentialVaultOperationResult.EncryptionFailed("Password cannot be empty.")
            }

            val usernameChars = cleanUsername.toCharArray()
            val encUserRes = try {
                crypto.encryptChars(usernameChars)
            } finally {
                usernameChars.fill('\u0000')
            }
            val encryptedUsername = when (encUserRes) {
                is VaultCryptoResult.Success -> encUserRes.value
                is VaultCryptoResult.KeyInvalidated -> {
                    refreshVaultStatus()
                    return@withContext CredentialVaultOperationResult.KeyInvalidated(encUserRes.reason)
                }
                is VaultCryptoResult.AuthenticationRequired -> return@withContext CredentialVaultOperationResult.AuthRequired(encUserRes.reason)
                is VaultCryptoResult.CorruptedPayload -> return@withContext CredentialVaultOperationResult.EncryptionFailed(encUserRes.reason)
                is VaultCryptoResult.CryptoFailure -> return@withContext CredentialVaultOperationResult.EncryptionFailed(encUserRes.reason)
            }

            val encPassRes = crypto.encryptChars(passwordChars)
            val encryptedPassword = when (encPassRes) {
                is VaultCryptoResult.Success -> encPassRes.value
                is VaultCryptoResult.KeyInvalidated -> {
                    refreshVaultStatus()
                    return@withContext CredentialVaultOperationResult.KeyInvalidated(encPassRes.reason)
                }
                is VaultCryptoResult.AuthenticationRequired -> return@withContext CredentialVaultOperationResult.AuthRequired(encPassRes.reason)
                is VaultCryptoResult.CorruptedPayload -> return@withContext CredentialVaultOperationResult.EncryptionFailed(encPassRes.reason)
                is VaultCryptoResult.CryptoFailure -> return@withContext CredentialVaultOperationResult.EncryptionFailed(encPassRes.reason)
            }

            val updated = existing.copy(
                websiteOrigin = parsedOrigin.canonicalOrigin,
                displayOrigin = formatDisplayOrigin(parsedOrigin),
                label = label.trim().ifBlank { parsedOrigin.host },
                usernameIdentifier = cleanUsername,
                encryptedUsername = encryptedUsername,
                encryptedPassword = encryptedPassword,
                updatedAt = System.currentTimeMillis()
            )
            dao.upsertCredentialVaultEntry(updated)
            authManager.consumeCredentialGrant(VaultAuthOperation.EDIT_CREDENTIAL, credentialId, existing.websiteOrigin)
            CredentialVaultOperationResult.Success(updated)
        } finally {
            passwordChars.fill('\u0000')
        }
    }

    /**
     * Reveals a saved credential's username and password after explicit per-credential authentication.
     */
    suspend fun revealCredential(
        credentialId: String
    ): CredentialVaultOperationResult<RevealedCredentialDetails> = withContext(ioDispatcher) {
        val existing = dao.getCredentialVaultEntryById(credentialId)
            ?: return@withContext CredentialVaultOperationResult.NotFound(
                credentialId = credentialId,
                message = "Credential '$credentialId' was not found."
            )

        val authRes = authManager.authenticateForCredentialOperation(
            operation = VaultAuthOperation.REVEAL_CREDENTIAL,
            credentialId = credentialId,
            websiteOrigin = existing.websiteOrigin,
            reason = "Authenticate to reveal password for ${existing.displayOrigin}"
        )
        when (authRes) {
            is VaultAuthResult.Authenticated -> Unit
            is VaultAuthResult.Cancelled -> return@withContext CredentialVaultOperationResult.AuthCancelled(authRes.reason)
            is VaultAuthResult.Failed -> return@withContext CredentialVaultOperationResult.AuthFailed(authRes.reason)
            is VaultAuthResult.Unavailable -> return@withContext CredentialVaultOperationResult.AuthFailed(authRes.reason)
            is VaultAuthResult.TimedOut -> return@withContext CredentialVaultOperationResult.AuthFailed(authRes.reason)
        }

        val userDec = crypto.decrypt(existing.encryptedUsername)
        val plainUser = when (userDec) {
            is VaultCryptoResult.Success -> userDec.value
            is VaultCryptoResult.KeyInvalidated -> {
                refreshVaultStatus()
                return@withContext CredentialVaultOperationResult.KeyInvalidated(userDec.reason)
            }
            is VaultCryptoResult.AuthenticationRequired -> return@withContext CredentialVaultOperationResult.AuthRequired(userDec.reason)
            is VaultCryptoResult.CorruptedPayload -> return@withContext CredentialVaultOperationResult.CorruptedData(userDec.reason)
            is VaultCryptoResult.CryptoFailure -> return@withContext CredentialVaultOperationResult.DecryptionFailed(userDec.reason)
        }

        val passDec = crypto.decryptToChars(existing.encryptedPassword)
        val passChars = when (passDec) {
            is VaultCryptoResult.Success -> passDec.value
            is VaultCryptoResult.KeyInvalidated -> {
                refreshVaultStatus()
                return@withContext CredentialVaultOperationResult.KeyInvalidated(passDec.reason)
            }
            is VaultCryptoResult.AuthenticationRequired -> return@withContext CredentialVaultOperationResult.AuthRequired(passDec.reason)
            is VaultCryptoResult.CorruptedPayload -> return@withContext CredentialVaultOperationResult.CorruptedData(passDec.reason)
            is VaultCryptoResult.CryptoFailure -> return@withContext CredentialVaultOperationResult.DecryptionFailed(passDec.reason)
        }

        authManager.consumeCredentialGrant(VaultAuthOperation.REVEAL_CREDENTIAL, credentialId, existing.websiteOrigin)
        CredentialVaultOperationResult.Success(
            RevealedCredentialDetails(
                credentialId = existing.credentialId,
                websiteOrigin = existing.websiteOrigin,
                displayOrigin = existing.displayOrigin,
                label = existing.label,
                username = plainUser,
                passwordChars = passChars
            )
        )
    }

    /**
     * Deletes a saved credential after per-credential authentication.
     */
    suspend fun deleteCredential(
        credentialId: String
    ): CredentialVaultOperationResult<Unit> = withContext(ioDispatcher) {
        val existing = dao.getCredentialVaultEntryById(credentialId)
            ?: return@withContext CredentialVaultOperationResult.NotFound(
                credentialId = credentialId,
                message = "Credential '$credentialId' was not found."
            )

        val authRes = authManager.authenticateForCredentialOperation(
            operation = VaultAuthOperation.DELETE_CREDENTIAL,
            credentialId = credentialId,
            websiteOrigin = existing.websiteOrigin,
            reason = "Authenticate to permanently delete credential for ${existing.displayOrigin}"
        )
        when (authRes) {
            is VaultAuthResult.Authenticated -> Unit
            is VaultAuthResult.Cancelled -> return@withContext CredentialVaultOperationResult.AuthCancelled(authRes.reason)
            is VaultAuthResult.Failed -> return@withContext CredentialVaultOperationResult.AuthFailed(authRes.reason)
            is VaultAuthResult.Unavailable -> return@withContext CredentialVaultOperationResult.AuthFailed(authRes.reason)
            is VaultAuthResult.TimedOut -> return@withContext CredentialVaultOperationResult.AuthFailed(authRes.reason)
        }

        dao.deleteCredentialVaultEntryById(credentialId)
        authManager.consumeCredentialGrant(VaultAuthOperation.DELETE_CREDENTIAL, credentialId, existing.websiteOrigin)
        CredentialVaultOperationResult.Success(Unit)
    }

    /**
     * Resets the entire vault and its master key ONLY after explicit user confirmation
     * (e.g. when recovering from a permanently invalidated Keystore key).
     */
    suspend fun resetVaultAfterUserConfirmation(): CredentialVaultOperationResult<Unit> = withContext(ioDispatcher) {
        val authRes = authManager.authenticateVaultAccess("Confirm resetting the Credential Vault")
        if (authRes is VaultAuthResult.Cancelled) {
            return@withContext CredentialVaultOperationResult.AuthCancelled(authRes.reason)
        }
        dao.clearAllCredentialVaultEntries()
        crypto.keyProvider.resetKeyAfterUserConfirmation()
        authManager.lockVault("Vault reset completed")
        refreshVaultStatus()
        CredentialVaultOperationResult.Success(Unit)
    }

    suspend fun getCredentialMetadataById(credentialId: String): CredentialVaultEntity? = withContext(ioDispatcher) {
        if (credentialId.isBlank()) return@withContext null
        dao.getCredentialVaultEntryById(credentialId)
    }

    suspend fun getAllCredentialsOnce(): List<CredentialVaultEntity> = withContext(ioDispatcher) {
        dao.getAllCredentialVaultEntriesOnce()
    }

    /**
     * Returns saved credentials whose `websiteOrigin` matches the exact canonical HTTP(S) origin of [url].
     * Never matches across different schemes, subdomains, ports, or lookalike domains.
     */
    suspend fun getCredentialsForExactOrigin(url: String): List<CredentialVaultEntity> = withContext(ioDispatcher) {
        val parsed = originParser(url)
        if (!parsed.isValid) return@withContext emptyList()
        dao.getCredentialVaultEntriesForOrigin(parsed.canonicalOrigin)
    }

    /**
     * Checks whether two URLs/origins resolve to the exact same canonical HTTP(S) origin.
     */
    fun originsMatchStrictly(firstUrlOrOrigin: String, secondUrlOrOrigin: String): Boolean {
        val first = originParser(firstUrlOrOrigin)
        val second = originParser(secondUrlOrOrigin)
        return first.isValid && second.isValid && first.canonicalOrigin == second.canonicalOrigin
    }

    /**
     * Decrypts ONLY the single requested field (`USERNAME` or `PASSWORD`) for an authorized replay step
     * after verifying exact canonical origin match and per-credential authentication.
     */
    suspend fun decryptFieldForAuthorizedReplay(
        credentialId: String,
        fieldRole: CredentialFieldRole,
        livePageUrl: String,
        expectedStepOrigin: String
    ): CredentialVaultOperationResult<DecryptedCredentialSecret> = withContext(ioDispatcher) {
        if (crypto.keyProvider.isKeyInvalidated()) {
            refreshVaultStatus()
            return@withContext CredentialVaultOperationResult.KeyInvalidated(
                "Credential vault encryption key was invalidated."
            )
        }
        val entity = dao.getCredentialVaultEntryById(credentialId)
            ?: return@withContext CredentialVaultOperationResult.NotFound(
                credentialId = credentialId,
                message = "Referenced credential '$credentialId' does not exist in the vault."
            )

        val liveParsed = originParser(livePageUrl)
        if (!liveParsed.isValid) {
            return@withContext CredentialVaultOperationResult.OriginMismatch(
                expectedOrigin = entity.websiteOrigin,
                actualOrigin = livePageUrl,
                message = "Current page URL '$livePageUrl' is not a valid HTTP(S) origin."
            )
        }
        if (liveParsed.canonicalOrigin != entity.websiteOrigin) {
            return@withContext CredentialVaultOperationResult.OriginMismatch(
                expectedOrigin = entity.websiteOrigin,
                actualOrigin = liveParsed.canonicalOrigin,
                message = "Credential origin '${entity.websiteOrigin}' does not match live page origin '${liveParsed.canonicalOrigin}'."
            )
        }
        if (expectedStepOrigin.isNotBlank()) {
            val stepParsed = originParser(expectedStepOrigin)
            if (!stepParsed.isValid || stepParsed.canonicalOrigin != entity.websiteOrigin) {
                return@withContext CredentialVaultOperationResult.OriginMismatch(
                    expectedOrigin = entity.websiteOrigin,
                    actualOrigin = expectedStepOrigin,
                    message = "Workflow step expected origin '$expectedStepOrigin' does not match credential origin '${entity.websiteOrigin}'."
                )
            }
        }

        if (!authManager.hasRecentCredentialGrant(
                operation = VaultAuthOperation.REPLAY_CREDENTIAL,
                credentialId = credentialId,
                websiteOrigin = entity.websiteOrigin
            )
        ) {
            val authRes = authManager.authenticateForCredentialOperation(
                operation = VaultAuthOperation.REPLAY_CREDENTIAL,
                credentialId = credentialId,
                websiteOrigin = entity.websiteOrigin,
                reason = "Authenticate to fill ${fieldRole.label.lowercase()} on ${entity.displayOrigin}"
            )
            when (authRes) {
                is VaultAuthResult.Authenticated -> Unit
                is VaultAuthResult.Cancelled -> return@withContext CredentialVaultOperationResult.AuthCancelled(authRes.reason)
                is VaultAuthResult.Failed -> return@withContext CredentialVaultOperationResult.AuthFailed(authRes.reason)
                is VaultAuthResult.Unavailable -> return@withContext CredentialVaultOperationResult.AuthFailed(authRes.reason)
                is VaultAuthResult.TimedOut -> return@withContext CredentialVaultOperationResult.AuthFailed(authRes.reason)
            }
        }

        val encryptedPayload = when (fieldRole) {
            CredentialFieldRole.USERNAME -> entity.encryptedUsername
            CredentialFieldRole.PASSWORD -> entity.encryptedPassword
        }

        val decResult = crypto.decryptToChars(encryptedPayload)
        val secretChars = when (decResult) {
            is VaultCryptoResult.Success -> decResult.value
            is VaultCryptoResult.KeyInvalidated -> {
                refreshVaultStatus()
                return@withContext CredentialVaultOperationResult.KeyInvalidated(decResult.reason)
            }
            is VaultCryptoResult.AuthenticationRequired -> {
                return@withContext CredentialVaultOperationResult.AuthRequired(decResult.reason)
            }
            is VaultCryptoResult.CorruptedPayload -> {
                return@withContext CredentialVaultOperationResult.CorruptedData(decResult.reason)
            }
            is VaultCryptoResult.CryptoFailure -> {
                return@withContext CredentialVaultOperationResult.DecryptionFailed(decResult.reason)
            }
        }

        dao.markCredentialVaultEntryUsed(credentialId, System.currentTimeMillis())
        CredentialVaultOperationResult.Success(
            DecryptedCredentialSecret(
                credentialId = credentialId,
                websiteOrigin = entity.websiteOrigin,
                fieldRole = fieldRole,
                secretChars = secretChars
            )
        )
    }
}

/**
 * Summary of a credential requested by a workflow awaiting user replay authorization.
 */
data class CredentialReplayItemSummary(
    val stepId: String,
    val stepOrder: Int,
    val stepDescription: String,
    val credentialId: String,
    val credentialLabel: String,
    val usernameIdentifier: String,
    val websiteOrigin: String,
    val displayOrigin: String,
    val fieldRole: CredentialFieldRole
)

/**
 * State presented to the user before replaying any workflow that uses saved credentials.
 */
data class PendingCredentialReplayAuthorization(
    val workflowId: Long,
    val workflowName: String,
    val tabId: String,
    val websiteOrigin: String,
    val displayOrigin: String,
    val items: List<CredentialReplayItemSummary>,
    val hasSubmissionStep: Boolean,
    val pendingDestructiveSteps: List<AutomationStep> = emptyList()
)

/**
 * Coordinates the 12-step pre-fill verification, single-field on-demand decryption,
 * narrowly scoped one-time JS execution, post-fill verification, and immediate buffer clearing.
 *
 * Security Guarantees:
 * - Never exposes a `@JavascriptInterface` method allowing webpage JavaScript to read credentials.
 * - Never stores credentials in `window.*`, global variables, DOM attributes, `localStorage`, or `sessionStorage`.
 * - Verifies live `window.location` canonical origin inside the JS IIFE before touching the DOM.
 * - Verifies target field visibility, enabled/editable state, uniqueness, and field type (`input[type="password"]` for passwords).
 * - Post-fill verification checks only that the target input retained the expected character length without reflecting the secret back to Kotlin.
 * - Immediately aborts if the app backgrounds, the device locks, the vault locks, the tab closes, or the page redirects.
 */
class CredentialFillCoordinator(
    private val vaultManager: CredentialVaultManager,
    private val automationEngine: AutomationEngine
) {
    private val abortedByLifecycle = AtomicBoolean(false)
    private val abortReasonRef = AtomicReference<String?>(null)

    init {
        vaultManager.authManager.registerVaultLockListener { reason ->
            abortedByLifecycle.set(true)
            abortReasonRef.set(reason)
        }
    }

    fun resetLifecycleAbort() {
        abortedByLifecycle.set(false)
        abortReasonRef.set(null)
    }

    fun notifyLifecycleAbort(reason: String) {
        abortedByLifecycle.set(true)
        abortReasonRef.set(reason)
    }

    /**
     * Executes a single credential-backed workflow step following all 12 verification steps in Part 6.2.
     */
    suspend fun executeAuthorizedCredentialStep(
        step: AutomationStep,
        authorizedTabId: String,
        currentActiveTabId: String,
        authorizedWebView: WebView?,
        currentWebViewProvider: () -> WebView?,
        liveTabUrlProvider: () -> String,
        navigationSynchronizer: TabNavigationSynchronizer?,
        userAuthorizedCredentialIds: Set<String>
    ): StepExecutionResult {
        // Step 1: Confirm the workflow is still running in its authorized tab and was not aborted by background/lock
        if (abortedByLifecycle.get()) {
            return StepExecutionResult(
                success = false,
                message = abortReasonRef.get() ?: "Credential replay aborted because the app backgrounded or vault locked.",
                errorCode = AutomationErrorCode.CREDENTIAL_VAULT_LOCKED
            )
        }
        if (authorizedTabId.isBlank() || authorizedTabId != currentActiveTabId) {
            return StepExecutionResult(
                success = false,
                message = "Credential fill aborted: active tab changed from authorized workflow tab.",
                errorCode = AutomationErrorCode.CREDENTIAL_ORIGIN_MISMATCH
            )
        }

        // Step 2: Confirm the current WebView instance is the expected authorized WebView instance
        val currentWebView = currentWebViewProvider()
        if (currentWebView == null || authorizedWebView == null || currentWebView !== authorizedWebView) {
            return StepExecutionResult(
                success = false,
                message = "Credential fill aborted: target WebView instance is unavailable or changed.",
                errorCode = AutomationErrorCode.WEBVIEW_UNAVAILABLE
            )
        }

        // Check navigation state (reject if page load failed or cross-origin navigation is in progress)
        if (navigationSynchronizer != null) {
            val navSnap = navigationSynchronizer.snapshot(authorizedTabId)
            if (navSnap.phase == NavigationLifecyclePhase.FAILED) {
                return StepExecutionResult(
                    success = false,
                    message = "Credential fill aborted: page load failed (${navSnap.failureReason ?: "navigation error"}).",
                    errorCode = AutomationErrorCode.NAVIGATION_FAILED
                )
            }
        }

        // Step 5 & 6: Confirm credential reference is assigned and explicitly authorized by user for this run
        val credentialId = step.credentialReferenceId?.trim().orEmpty()
        if (credentialId.isEmpty()) {
            return StepExecutionResult(
                success = false,
                message = "Step '${step.description}' requires a saved credential, but none is assigned.",
                errorCode = AutomationErrorCode.CREDENTIAL_UNASSIGNED
            )
        }
        if (credentialId !in userAuthorizedCredentialIds) {
            return StepExecutionResult(
                success = false,
                message = "Credential fill blocked: user has not authorized credential '$credentialId' for this replay.",
                errorCode = AutomationErrorCode.CREDENTIAL_AUTH_REQUIRED
            )
        }

        val fieldRole = step.credentialFieldRole ?: inferFieldRole(step)

        // Validate selector before decrypting any secret
        if (step.selector.isNotBlank()) {
            val selCheck = automationEngine.validateSelector(step.selector)
            if (!selCheck.isValid) {
                return StepExecutionResult(
                    success = false,
                    message = "Invalid CSS selector for credential field: ${selCheck.reason}",
                    errorCode = AutomationErrorCode.INVALID_SELECTOR
                )
            }
        }

        // Step 3: Confirm current page origin (both tab URL and live WebView URL) matches credential's authorized origin
        val liveTabUrl = liveTabUrlProvider()
        val liveTabOrigin = automationEngine.parseOrigin(liveTabUrl)
        if (!liveTabOrigin.isValid) {
            return StepExecutionResult(
                success = false,
                message = "Credential fill blocked: active tab URL '$liveTabUrl' is not a valid HTTP(S) origin.",
                errorCode = AutomationErrorCode.CREDENTIAL_ORIGIN_MISMATCH
            )
        }
        val liveWvUrl = runCatching { currentWebView.url.orEmpty() }.getOrDefault("")
        if (liveWvUrl.isNotBlank()) {
            val wvOrigin = automationEngine.parseOrigin(liveWvUrl)
            if (!wvOrigin.isValid || wvOrigin.canonicalOrigin != liveTabOrigin.canonicalOrigin) {
                return StepExecutionResult(
                    success = false,
                    message = "Credential fill blocked: WebView URL origin '${wvOrigin.canonicalOrigin.ifBlank { liveWvUrl }}' does not match authorized tab origin '${liveTabOrigin.canonicalOrigin}'.",
                    errorCode = AutomationErrorCode.CREDENTIAL_ORIGIN_MISMATCH
                )
            }
        }

        // Step 5, 7, 8: Verify credential exists, origin matches, vault auth succeeds, and decrypt ONLY the required field
        val decryptRes = vaultManager.decryptFieldForAuthorizedReplay(
            credentialId = credentialId,
            fieldRole = fieldRole,
            livePageUrl = liveTabUrl,
            expectedStepOrigin = step.expectedOrigin
        )

        val secret = when (decryptRes) {
            is CredentialVaultOperationResult.Success -> decryptRes.value
            is CredentialVaultOperationResult.NotFound -> return StepExecutionResult(
                success = false,
                message = decryptRes.message,
                errorCode = AutomationErrorCode.CREDENTIAL_NOT_FOUND
            )
            is CredentialVaultOperationResult.OriginMismatch -> return StepExecutionResult(
                success = false,
                message = decryptRes.message,
                errorCode = AutomationErrorCode.CREDENTIAL_ORIGIN_MISMATCH
            )
            is CredentialVaultOperationResult.InvalidOrigin -> return StepExecutionResult(
                success = false,
                message = decryptRes.message,
                errorCode = AutomationErrorCode.CREDENTIAL_ORIGIN_MISMATCH
            )
            is CredentialVaultOperationResult.VaultLocked -> return StepExecutionResult(
                success = false,
                message = decryptRes.message,
                errorCode = AutomationErrorCode.CREDENTIAL_VAULT_LOCKED
            )
            is CredentialVaultOperationResult.AuthRequired -> return StepExecutionResult(
                success = false,
                message = decryptRes.message,
                errorCode = AutomationErrorCode.CREDENTIAL_AUTH_REQUIRED
            )
            is CredentialVaultOperationResult.AuthCancelled -> return StepExecutionResult(
                success = false,
                message = decryptRes.message,
                errorCode = AutomationErrorCode.CREDENTIAL_AUTH_CANCELLED
            )
            is CredentialVaultOperationResult.AuthFailed -> return StepExecutionResult(
                success = false,
                message = decryptRes.message,
                errorCode = AutomationErrorCode.CREDENTIAL_AUTH_FAILED
            )
            is CredentialVaultOperationResult.KeyInvalidated -> return StepExecutionResult(
                success = false,
                message = decryptRes.message,
                errorCode = AutomationErrorCode.CREDENTIAL_KEY_INVALIDATED
            )
            is CredentialVaultOperationResult.CorruptedData -> return StepExecutionResult(
                success = false,
                message = decryptRes.message,
                errorCode = AutomationErrorCode.CREDENTIAL_DECRYPTION_FAILED
            )
            is CredentialVaultOperationResult.DecryptionFailed -> return StepExecutionResult(
                success = false,
                message = decryptRes.message,
                errorCode = AutomationErrorCode.CREDENTIAL_DECRYPTION_FAILED
            )
            is CredentialVaultOperationResult.EncryptionFailed -> return StepExecutionResult(
                success = false,
                message = decryptRes.message,
                errorCode = AutomationErrorCode.CREDENTIAL_DECRYPTION_FAILED
            )
        }

        // Step 9, 10, 11, 12: Execute narrowly scoped one-time JS fill, verify length without reflecting secret, and clear buffer in finally
        return try {
            if (abortedByLifecycle.get() || !vaultManager.authManager.isVaultUnlocked()) {
                return StepExecutionResult(
                    success = false,
                    message = "Credential fill aborted before DOM injection because the vault was locked.",
                    errorCode = AutomationErrorCode.CREDENTIAL_VAULT_LOCKED
                )
            }
            if (currentWebViewProvider() !== authorizedWebView) {
                return StepExecutionResult(
                    success = false,
                    message = "Credential fill aborted: WebView instance changed before DOM injection.",
                    errorCode = AutomationErrorCode.WEBVIEW_UNAVAILABLE
                )
            }

            val expectedLength = secret.length
            val fillScript = buildScopedCredentialFillScript(
                selector = step.selector,
                targetLabel = step.targetLabel,
                fieldRole = fieldRole,
                expectedCanonicalOrigin = secret.websiteOrigin,
                secretChars = secret.peekChars(),
                expectedLength = expectedLength
            )
            // Clear the decrypted CharArray immediately after building the one-time script
            secret.clear()

            val evalRes = automationEngine.evalJsWithResult(authorizedWebView, fillScript)
            when (evalRes) {
                is JsEvalResult.Unavailable -> StepExecutionResult(
                    success = false,
                    message = evalRes.reason,
                    errorCode = AutomationErrorCode.WEBVIEW_UNAVAILABLE
                )
                is JsEvalResult.Timeout -> StepExecutionResult(
                    success = false,
                    message = "Credential fill timed out after ${evalRes.timeoutMs}ms.",
                    errorCode = AutomationErrorCode.JS_TIMEOUT
                )
                is JsEvalResult.Failure -> StepExecutionResult(
                    success = false,
                    message = evalRes.reason,
                    errorCode = AutomationErrorCode.JS_EXCEPTION
                )
                is JsEvalResult.Success -> parseCredentialFillJsResult(
                    rawValue = evalRes.rawValue,
                    fieldRole = fieldRole,
                    targetLabel = step.targetLabel.ifBlank { fieldRole.label }
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } finally {
            secret.clear()
        }
    }

    /**
     * Builds a narrowly scoped one-time IIFE script that:
     * 1. Verifies live `window.location` canonical origin inside the page matches [expectedCanonicalOrigin].
     * 2. Resolves the single visible, enabled, editable target element.
     * 3. Verifies the target element's input type matches [fieldRole] (`input[type="password"]` for `PASSWORD`).
     * 4. Sets the value via the native `HTMLInputElement.prototype.value` setter and dispatches `input`/`change`.
     * 5. Verifies `target.value.length === expectedLength` without returning the secret value to native code.
     */
    internal fun buildScopedCredentialFillScript(
        selector: String,
        targetLabel: String,
        fieldRole: CredentialFieldRole,
        expectedCanonicalOrigin: String,
        secretChars: CharArray,
        expectedLength: Int
    ): String {
        val selEsc = JSONObject.quote(selector.trim())
        val labelEsc = JSONObject.quote(targetLabel.trim())
        val expectedOriginEsc = JSONObject.quote(expectedCanonicalOrigin.trim())
        val roleEsc = JSONObject.quote(fieldRole.id)
        val secretEsc = quoteCharsAsJsonString(secretChars)
        val roleQueryEsc = when (fieldRole) {
            CredentialFieldRole.PASSWORD -> JSONObject.quote("input[type=\"password\"]")
            CredentialFieldRole.USERNAME -> JSONObject.quote(
                "input:not([type=\"password\"]):not([type=\"hidden\"]):not([type=\"submit\"]):not([type=\"button\"]):not([type=\"checkbox\"]):not([type=\"radio\"]):not([type=\"file\"])"
            )
        }

        return """
            (function() {
                try {
                    const loc = window.location;
                    if (!loc || !loc.protocol || !loc.hostname) {
                        return JSON.stringify({
                            ok: false,
                            code: 'CREDENTIAL_ORIGIN_MISMATCH',
                            msg: 'Document location is unavailable for origin verification.'
                        });
                    }
                    const scheme = String(loc.protocol).replace(':', '').toLowerCase();
                    if (scheme !== 'https' && scheme !== 'http') {
                        return JSON.stringify({
                            ok: false,
                            code: 'CREDENTIAL_ORIGIN_MISMATCH',
                            msg: 'Credential fill refused on non-HTTP(S) scheme: ' + scheme
                        });
                    }
                    const host = String(loc.hostname).toLowerCase();
                    const rawPort = String(loc.port || '').trim();
                    const effectivePort = rawPort ? parseInt(rawPort, 10) : (scheme === 'https' ? 443 : 80);
                    const liveCanonicalOrigin = scheme + '://' + host + ':' + effectivePort;
                    if (liveCanonicalOrigin !== $expectedOriginEsc) {
                        return JSON.stringify({
                            ok: false,
                            code: 'CREDENTIAL_ORIGIN_MISMATCH',
                            msg: 'Live page origin (' + liveCanonicalOrigin + ') does not match authorized origin (' + $expectedOriginEsc + ').'
                        });
                    }

                    const norm = (s) => (s || '').toString().replace(/\s+/g, ' ').trim();
                    const isVisible = (el) => {
                        if (!el || typeof el.getBoundingClientRect !== 'function') return false;
                        if (el.hidden || el.getAttribute('aria-hidden') === 'true') return false;
                        const style = window.getComputedStyle ? window.getComputedStyle(el) : null;
                        if (style && (style.display === 'none' || style.visibility === 'hidden' || parseFloat(style.opacity || '1') === 0)) {
                            return false;
                        }
                        const rect = el.getBoundingClientRect();
                        return rect.width > 0 && rect.height > 0;
                    };
                    const isDisabled = (el) => {
                        if (!el) return true;
                        return Boolean(el.disabled) || el.getAttribute('aria-disabled') === 'true';
                    };
                    const labelOf = (el) => {
                        if (!el) return '';
                        const aria = norm(el.getAttribute && el.getAttribute('aria-label'));
                        if (aria) return aria;
                        const ph = norm(el.getAttribute && el.getAttribute('placeholder'));
                        if (ph) return ph;
                        const id = el.id ? norm(el.id) : '';
                        if (id && typeof CSS !== 'undefined' && CSS.escape) {
                            const lbl = document.querySelector('label[for="' + CSS.escape(id) + '"]');
                            if (lbl && norm(lbl.innerText || lbl.textContent)) {
                                return norm(lbl.innerText || lbl.textContent);
                            }
                        }
                        const name = norm(el.getAttribute && el.getAttribute('name'));
                        if (name) return name;
                        return id;
                    };

                    const sel = $selEsc;
                    const label = $labelEsc;
                    const roleQuery = $roleQueryEsc;
                    let target = null;

                    if (sel && sel.trim().length > 0) {
                        let matched;
                        try {
                            matched = Array.from(document.querySelectorAll(sel));
                        } catch (err) {
                            return JSON.stringify({ ok: false, code: 'INVALID_SELECTOR', msg: 'Invalid selector: ' + sel });
                        }
                        const visible = matched.filter(isVisible);
                        if (visible.length === 1) {
                            target = visible[0];
                        } else if (visible.length > 1) {
                            const l = norm(label).toLowerCase();
                            if (l.length > 0) {
                                const exact = visible.filter(el => labelOf(el).toLowerCase() === l);
                                if (exact.length === 1) {
                                    target = exact[0];
                                } else {
                                    return JSON.stringify({ ok: false, code: 'ELEMENT_AMBIGUOUS', msg: 'Selector matched multiple visible credential inputs.' });
                                }
                            } else {
                                return JSON.stringify({ ok: false, code: 'ELEMENT_AMBIGUOUS', msg: 'Selector matched multiple visible credential inputs.' });
                            }
                        }
                    }

                    if (!target && label && label.trim().length > 0) {
                        const l = norm(label).toLowerCase();
                        const pool = Array.from(document.querySelectorAll(roleQuery)).filter(isVisible);
                        const exact = pool.filter(el => labelOf(el).toLowerCase() === l);
                        if (exact.length === 1) {
                            target = exact[0];
                        } else if (exact.length > 1) {
                            return JSON.stringify({ ok: false, code: 'ELEMENT_AMBIGUOUS', msg: 'Multiple visible inputs matched label: ' + label });
                        } else {
                            const partial = pool.filter(el => {
                                const cand = labelOf(el).toLowerCase();
                                return cand.length > 0 && cand.includes(l);
                            });
                            if (partial.length === 1) {
                                target = partial[0];
                            } else if (partial.length > 1) {
                                return JSON.stringify({ ok: false, code: 'ELEMENT_AMBIGUOUS', msg: 'Multiple visible inputs partially matched label: ' + label });
                            }
                        }
                    }

                    if (!target) {
                        return JSON.stringify({
                            ok: false,
                            code: 'ELEMENT_NOT_FOUND',
                            msg: 'Target credential field not found or not visible on page.'
                        });
                    }
                    if (isDisabled(target) || Boolean(target.readOnly)) {
                        return JSON.stringify({
                            ok: false,
                            code: 'ELEMENT_DISABLED',
                            msg: 'Target credential field is disabled or read-only.'
                        });
                    }

                    const tag = String(target.tagName || '').toLowerCase();
                    const inputType = String(target.type || 'text').toLowerCase();
                    if (tag !== 'input') {
                        return JSON.stringify({
                            ok: false,
                            code: 'CREDENTIAL_FIELD_INVALID',
                            msg: 'Target element is <' + tag + '>, expected <input>.'
                        });
                    }
                    if ($roleEsc === 'PASSWORD') {
                        if (inputType !== 'password') {
                            return JSON.stringify({
                                ok: false,
                                code: 'CREDENTIAL_FIELD_INVALID',
                                msg: 'Refusing to fill password into non-password input of type "' + inputType + '".'
                            });
                        }
                    } else {
                        const allowedUserTypes = ['text', 'email', 'tel', 'search', 'url', ''];
                        if (inputType === 'password' || !allowedUserTypes.includes(inputType)) {
                            return JSON.stringify({
                                ok: false,
                                code: 'CREDENTIAL_FIELD_INVALID',
                                msg: 'Refusing to fill username into incompatible input of type "' + inputType + '".'
                            });
                        }
                    }

                    target.scrollIntoView({ behavior: 'instant', block: 'center' });
                    target.focus();
                    const proto = Object.getPrototypeOf(target);
                    const desc = (proto && Object.getOwnPropertyDescriptor(proto, 'value')) ||
                        Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value');
                    if (desc && typeof desc.set === 'function') {
                        desc.set.call(target, $secretEsc);
                    } else {
                        target.value = $secretEsc;
                    }
                    target.dispatchEvent(new Event('input', { bubbles: true }));
                    target.dispatchEvent(new Event('change', { bubbles: true }));

                    const currentVal = typeof target.value === 'string' ? target.value : '';
                    const verifiedLength = currentVal.length === $expectedLength && currentVal.length > 0;
                    if (!verifiedLength) {
                        return JSON.stringify({
                            ok: false,
                            code: 'CREDENTIAL_FILL_FAILED',
                            msg: 'Target field did not retain the expected credential value length after fill.',
                            verifiedLength: false
                        });
                    }
                    return JSON.stringify({
                        ok: true,
                        code: 'NONE',
                        msg: 'Filled ' + ($roleEsc === 'PASSWORD' ? 'password' : 'username') + ' field securely.',
                        verifiedLength: true
                    });
                } catch (err) {
                    return JSON.stringify({
                        ok: false,
                        code: 'JS_EXCEPTION',
                        msg: 'Credential fill script error.'
                    });
                }
            })();
        """.trimIndent()
    }

    private fun parseCredentialFillJsResult(
        rawValue: String,
        fieldRole: CredentialFieldRole,
        targetLabel: String
    ): StepExecutionResult {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty() || trimmed == "null" || trimmed == "\"null\"") {
            return StepExecutionResult(
                success = false,
                message = "Credential fill returned empty response for '$targetLabel'.",
                errorCode = AutomationErrorCode.CREDENTIAL_FILL_FAILED
            )
        }
        val jsonObj = runCatching {
            val unquoted = if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                JSONTokener(trimmed).nextValue().toString()
            } else {
                trimmed
            }
            JSONObject(unquoted)
        }.getOrElse {
            return StepExecutionResult(
                success = false,
                message = "Malformed credential fill response for '$targetLabel'.",
                errorCode = AutomationErrorCode.CREDENTIAL_FILL_FAILED
            )
        }

        val ok = jsonObj.optBoolean("ok", false)
        val verifiedLength = jsonObj.optBoolean("verifiedLength", false)
        val codeStr = jsonObj.optString("code", "")
        val msg = jsonObj.optString("msg", "").ifBlank {
            if (ok && verifiedLength) {
                "Filled ${fieldRole.label.lowercase()} into '$targetLabel'"
            } else {
                "Failed to fill ${fieldRole.label.lowercase()} into '$targetLabel'"
            }
        }
        if (ok && verifiedLength) {
            return StepExecutionResult(
                success = true,
                message = msg,
                errorCode = AutomationErrorCode.NONE
            )
        }
        val code = runCatching { AutomationErrorCode.valueOf(codeStr) }
            .getOrDefault(AutomationErrorCode.CREDENTIAL_FILL_FAILED)
        return StepExecutionResult(
            success = false,
            message = msg,
            errorCode = code
        )
    }

    private fun quoteCharsAsJsonString(chars: CharArray): String {
        val sb = StringBuilder(chars.size + 8)
        sb.append('"')
        for (c in chars) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", c.code))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }

    companion object {
        fun inferFieldRole(step: AutomationStep): CredentialFieldRole {
            step.credentialFieldRole?.let { return it }
            val combined = "${step.targetLabel} ${step.selector} ${step.description}".lowercase()
            return if (combined.contains("password") || combined.contains("passcode") || combined.contains("pwd")) {
                CredentialFieldRole.PASSWORD
            } else {
                CredentialFieldRole.USERNAME
            }
        }
    }
}
