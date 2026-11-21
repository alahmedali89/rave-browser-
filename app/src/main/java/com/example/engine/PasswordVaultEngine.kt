package com.example.engine

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.data.local.SavedPasswordEntity
import java.net.URI
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

enum class PasswordStrength(val label: String, val rank: Int) {
    WEAK("Weak", 0),
    FAIR("Fair", 1),
    STRONG("Strong", 2),
    EXCELLENT("Excellent", 3)
}

data class PasswordStrengthReport(
    val score: Int,
    val strength: PasswordStrength,
    val tip: String
)

data class PasswordAuditReport(
    val totalCount: Int,
    val overallScore: Int,
    val strongCount: Int,
    val weakItems: List<SavedPasswordEntity>,
    val reusedGroups: List<List<SavedPasswordEntity>>,
    val compromisedCount: Int
) {
    val reusedCount: Int
        get() = reusedGroups.sumOf { it.size }
}

/**
 * Encrypts and decrypts stored passwords with an Android Keystore AES-256-GCM key,
 * with a deterministic fallback key only when AndroidKeyStore is unavailable (e.g., local JVM tests).
 */
class PasswordVaultCrypto {

    private val keyAlias = "apex_password_vault_master_key_v1"
    private val prefix = "v1:"
    private val gcmTagBits = 128

    private val secretKey: SecretKey by lazy {
        loadOrCreateKeystoreKey() ?: fallbackTestKey()
    }

    private fun loadOrCreateKeystoreKey(): SecretKey? {
        return runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val existing = (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey
            if (existing != null) return@runCatching existing

            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
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
            keyGenerator.generateKey()
        }.getOrNull()
    }

    private fun fallbackTestKey(): SecretKey {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("apex_browser_local_vault_fallback_v1".toByteArray(Charsets.UTF_8))
        return SecretKeySpec(digest, "AES")
    }

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val plainBytes = plainText.toByteArray(Charsets.UTF_8)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            if (secretKey is SecretKeySpec) {
                val iv = ByteArray(12)
                SecureRandom().nextBytes(iv)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(gcmTagBits, iv))
            } else {
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            }
            val iv = cipher.iv
            require(iv != null && iv.size == 12) { "Invalid GCM IV length" }
            val encrypted = cipher.doFinal(plainBytes)
            val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val cipherB64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
            return "$prefix$ivB64:$cipherB64"
        } finally {
            plainBytes.fill(0)
        }
    }

    fun decrypt(payload: String): String {
        if (payload.isEmpty() || !payload.startsWith(prefix)) return ""
        return runCatching {
            val rest = payload.removePrefix(prefix)
            val parts = rest.split(':', limit = 2)
            if (parts.size != 2) return@runCatching ""
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            if (iv.size != 12) return@runCatching ""
            val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)
            if (cipherBytes.size <= 16) return@runCatching ""
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(gcmTagBits, iv))
            val plainBytes = cipher.doFinal(cipherBytes)
            try {
                String(plainBytes, Charsets.UTF_8)
            } finally {
                plainBytes.fill(0)
            }
        }.getOrDefault("")
    }

    fun fingerprint(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("apex_fp_v1:$plainText".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

object PasswordStrengthEvaluator {

    private val commonWeakPasswords = setOf(
        "password", "123456", "12345678", "123456789", "qwerty", "abc123",
        "letmein", "admin", "welcome", "iloveyou", "monkey", "dragon",
        "passw0rd", "password1", "qwerty123", "000000", "111111"
    )

    fun evaluate(password: String): PasswordStrengthReport {
        if (password.isEmpty()) {
            return PasswordStrengthReport(0, PasswordStrength.WEAK, "Enter a password to check strength")
        }
        val lower = password.lowercase(Locale.US)
        if (lower in commonWeakPasswords) {
            return PasswordStrengthReport(
                score = 12,
                strength = PasswordStrength.WEAK,
                tip = "Commonly leaked password — replace immediately"
            )
        }

        val hasLower = password.any { it.isLowerCase() }
        val hasUpper = password.any { it.isUpperCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSymbol = password.any { !it.isLetterOrDigit() }
        val uniqueRatio = password.toSet().size.toFloat() / password.length.coerceAtLeast(1)

        var score = 0
        score += when {
            password.length >= 16 -> 42
            password.length >= 12 -> 34
            password.length >= 10 -> 26
            password.length >= 8 -> 18
            else -> password.length * 2
        }
        val classes = listOf(hasLower, hasUpper, hasDigit, hasSymbol).count { it }
        score += classes * 11
        if (uniqueRatio >= 0.75f && password.length >= 8) score += 10
        if (password.length >= 14 && classes >= 3) score += 6

        // Penalize repeated triplets (e.g. "aaa", "111")
        if (Regex("(.)\\1\\1").containsMatchIn(password)) {
            score -= 14
        }

        val clamped = score.coerceIn(5, 100)
        val strength = when {
            clamped >= 82 -> PasswordStrength.EXCELLENT
            clamped >= 62 -> PasswordStrength.STRONG
            clamped >= 42 -> PasswordStrength.FAIR
            else -> PasswordStrength.WEAK
        }
        val tip = when {
            password.length < 8 -> "Too short — use at least 12 characters"
            classes <= 1 -> "Mix uppercase, numbers, and symbols"
            strength == PasswordStrength.FAIR -> "Add length or special symbols for stronger entropy"
            strength == PasswordStrength.STRONG -> "Solid protection against brute-force attacks"
            else -> "High-entropy credential"
        }
        return PasswordStrengthReport(clamped, strength, tip)
    }

    fun isCommonWeak(password: String): Boolean {
        val lower = password.trim().lowercase(Locale.US)
        return lower.length < 8 || lower in commonWeakPasswords
    }
}

object SecurePasswordGenerator {
    private val random = SecureRandom()
    private const val LOWER = "abcdefghijkmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val DIGITS = "23456789"
    private const val SYMBOLS = "!@#$%^&*_-+=?"

    private val wordBank = listOf(
        "Apex", "Cobalt", "Summit", "Cipher", "Beacon", "Vortex", "Aurora", "Harbor",
        "Falcon", "Quartz", "Timber", "Horizon", "Lantern", "Glacier", "Atlas", "Spark",
        "Velvet", "Matrix", "Vector", "Prism", "Canyon", "Solar", "Anchor", "Orbit",
        "Pulse", "Nimbus", "Cedar", "Breeze", "Mosaic", "Echo", "Shield", "Kestrel"
    )

    fun generateRandom(
        length: Int = 16,
        includeUppercase: Boolean = true,
        includeLowercase: Boolean = true,
        includeDigits: Boolean = true,
        includeSymbols: Boolean = true
    ): String {
        val pools = mutableListOf<String>()
        if (includeUppercase) pools += UPPER
        if (includeLowercase) pools += LOWER
        if (includeDigits) pools += DIGITS
        if (includeSymbols) pools += SYMBOLS
        if (pools.isEmpty()) pools += LOWER

        val targetLen = length.coerceIn(8, 32)
        val chars = ArrayList<Char>(targetLen)

        // Guarantee at least one character from each enabled set
        pools.take(targetLen).forEach { pool ->
            chars += pool[random.nextInt(pool.length)]
        }

        val combined = pools.joinToString("")
        while (chars.size < targetLen) {
            chars += combined[random.nextInt(combined.length)]
        }

        // Fisher-Yates shuffle with SecureRandom
        for (i in chars.lastIndex downTo 1) {
            val j = random.nextInt(i + 1)
            val tmp = chars[i]
            chars[i] = chars[j]
            chars[j] = tmp
        }
        return chars.joinToString("")
    }

    fun generatePassphrase(
        wordCount: Int = 4,
        includeNumberAndSymbol: Boolean = true
    ): String {
        val count = wordCount.coerceIn(3, 6)
        val words = (0 until count).map { wordBank[random.nextInt(wordBank.size)] }
        val base = words.joinToString("-")
        return if (includeNumberAndSymbol) {
            val num = 10 + random.nextInt(90)
            val sym = "!@#\$%&*"[random.nextInt(7)]
            "$base-$num$sym"
        } else {
            base
        }
    }
}

object PasswordAuditEngine {

    fun analyze(
        items: List<SavedPasswordEntity>,
        decryptor: (SavedPasswordEntity) -> String
    ): PasswordAuditReport {
        if (items.isEmpty()) {
            return PasswordAuditReport(
                totalCount = 0,
                overallScore = 100,
                strongCount = 0,
                weakItems = emptyList(),
                reusedGroups = emptyList(),
                compromisedCount = 0
            )
        }

        val weakItems = mutableListOf<SavedPasswordEntity>()
        var compromised = 0
        var strongCount = 0
        var scoreSum = 0

        items.forEach { item ->
            val plain = decryptor(item)
            val report = PasswordStrengthEvaluator.evaluate(plain)
            scoreSum += report.score
            if (report.score >= 62) {
                strongCount++
            } else {
                weakItems += item
            }
            if (PasswordStrengthEvaluator.isCommonWeak(plain)) {
                compromised++
            }
        }

        val reusedGroups = items
            .filter { it.passwordFingerprint.isNotBlank() }
            .groupBy { it.passwordFingerprint }
            .values
            .filter { it.size > 1 }
            .toList()

        val reusedPenalty = (reusedGroups.sumOf { it.size - 1 } * 8).coerceAtMost(35)
        val averageScore = (scoreSum / items.size).coerceIn(0, 100)
        val overallScore = (averageScore - reusedPenalty).coerceIn(5, 100)

        return PasswordAuditReport(
            totalCount = items.size,
            overallScore = overallScore,
            strongCount = strongCount,
            weakItems = weakItems,
            reusedGroups = reusedGroups,
            compromisedCount = compromised
        )
    }
}

object PasswordVaultUtil {

    val categories = listOf("Personal", "Work", "Finance", "Social", "Shopping")

    fun normalizeOriginUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return ""
        return if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            "https://$trimmed"
        } else {
            trimmed
        }
    }

    fun extractHost(rawUrl: String): String {
        val normalized = normalizeOriginUrl(rawUrl)
        if (normalized.isBlank()) return ""
        return runCatching {
            URI(normalized).host?.removePrefix("www.")?.lowercase(Locale.US).orEmpty()
        }.getOrDefault("")
            .ifBlank {
                normalized.substringAfter("://", normalized)
                    .substringBefore('/')
                    .substringBefore(':')
                    .removePrefix("www.")
                    .lowercase(Locale.US)
            }
    }

    fun deriveSiteName(rawUrl: String, explicitName: String = ""): String {
        val clean = explicitName.trim()
        if (clean.isNotBlank()) return clean
        val host = extractHost(rawUrl)
        if (host.isBlank()) return "Saved Account"
        val primary = host.substringBefore('.')
        return primary.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
        }
    }

    fun hostMatches(savedHost: String, pageHost: String): Boolean {
        val a = savedHost.trim().lowercase(Locale.US).removePrefix("www.")
        val b = pageHost.trim().lowercase(Locale.US).removePrefix("www.")
        if (a.isBlank() || b.isBlank()) return false
        return a == b || b.endsWith(".$a") || a.endsWith(".$b")
    }

    fun formatShortDate(millis: Long): String {
        val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return fmt.format(Date(millis))
    }

    fun exportCsv(
        items: List<SavedPasswordEntity>,
        decryptor: (SavedPasswordEntity) -> String
    ): String {
        val sb = StringBuilder()
        sb.appendLine("name,url,username,password,note")
        items.forEach { item ->
            val plain = decryptor(item)
            sb.append(csvEscape(item.siteName)).append(',')
            sb.append(csvEscape(item.siteUrl)).append(',')
            sb.append(csvEscape(item.username)).append(',')
            sb.append(csvEscape(plain)).append(',')
            sb.appendLine(csvEscape(item.notes))
        }
        return sb.toString()
    }

    data class ParsedCsvCredential(
        val siteName: String,
        val siteUrl: String,
        val username: String,
        val password: String,
        val notes: String
    )

    fun parseCsv(rawCsv: String): List<ParsedCsvCredential> {
        val rows = parseCsvRows(rawCsv)
        if (rows.isEmpty()) return emptyList()

        val firstRowLower = rows.first().map { it.trim().lowercase(Locale.US) }
        val hasHeader = firstRowLower.any {
            it == "url" || it == "username" || it == "password" || it == "login_uri"
        }

        val nameIdx: Int
        val urlIdx: Int
        val userIdx: Int
        val passIdx: Int
        val noteIdx: Int
        val dataRows: List<List<String>>

        if (hasHeader) {
            nameIdx = firstRowLower.indexOfFirst { it in setOf("name", "title", "site") }
            urlIdx = firstRowLower.indexOfFirst { it in setOf("url", "login_uri", "origin", "website") }
            userIdx = firstRowLower.indexOfFirst { it in setOf("username", "login_username", "user", "email") }
            passIdx = firstRowLower.indexOfFirst { it in setOf("password", "login_password", "pass") }
            noteIdx = firstRowLower.indexOfFirst { it in setOf("note", "notes", "extra") }
            dataRows = rows.drop(1)
        } else {
            nameIdx = 0
            urlIdx = 1
            userIdx = 2
            passIdx = 3
            noteIdx = 4
            dataRows = rows
        }

        if (passIdx < 0 || userIdx < 0) return emptyList()

        return dataRows.mapNotNull { cols ->
            val username = cols.getOrNull(userIdx)?.trim().orEmpty()
            val password = cols.getOrNull(passIdx).orEmpty()
            val rawUrl = if (urlIdx >= 0) cols.getOrNull(urlIdx)?.trim().orEmpty() else ""
            val rawName = if (nameIdx >= 0) cols.getOrNull(nameIdx)?.trim().orEmpty() else ""
            val notes = if (noteIdx >= 0) cols.getOrNull(noteIdx)?.trim().orEmpty() else ""
            if (password.isBlank() || (username.isBlank() && rawUrl.isBlank())) {
                null
            } else {
                val url = normalizeOriginUrl(rawUrl.ifBlank { rawName })
                val name = deriveSiteName(url, rawName)
                ParsedCsvCredential(
                    siteName = name,
                    siteUrl = url,
                    username = username,
                    password = password,
                    notes = notes
                )
            }
        }
    }

    private fun csvEscape(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') ||
            value.contains('\n') || value.contains('\r')
        return if (needsQuotes) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun parseCsvRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> inQuotes = true
                    ',' -> {
                        currentRow += field.toString()
                        field.setLength(0)
                    }
                    '\n' -> {
                        currentRow += field.toString()
                        field.setLength(0)
                        if (currentRow.any { it.isNotBlank() }) rows += currentRow.toList()
                        currentRow.clear()
                    }
                    '\r' -> Unit
                    else -> field.append(ch)
                }
            }
            i++
        }
        if (field.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow += field.toString()
            if (currentRow.any { it.isNotBlank() }) rows += currentRow.toList()
        }
        return rows
    }
}
