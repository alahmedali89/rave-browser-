package com.example.automation

import com.example.data.local.ApexDao
import com.example.data.local.AutomationVaultEntryEntity
import com.example.data.local.AutomationVaultFileEntity
import com.example.data.local.AutomationVaultFolderEntity
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

data class VaultFolderFilePayload(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray
)

data class VaultDirectoryUploadResult(
    val folder: AutomationVaultFolderEntity,
    val uploadedFiles: List<AutomationVaultFileEntity>
)

class VaultValuesExhaustedException(
    val category: AutomationVaultCategory,
    val folderId: String,
    val folderName: String,
    message: String
) : IllegalStateException(message)

/**
 * Manages user-owned automation values (passwords, emails, phone numbers, usernames,
 * names, addresses, and custom values) encrypted at rest via [CredentialVaultCrypto].
 *
 * Values can be grabbed during a recording based on user selection, edited inside the
 * Automation Vault inside Automation, and resolved dynamically on every replay.
 */
class AutomationVaultManager(
    private val dao: ApexDao,
    val crypto: CredentialVaultCrypto,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val engine: AutomationEngine = AutomationEngine()
) {
    val entriesFlow: Flow<List<AutomationVaultEntryEntity>> = dao.observeAllAutomationVaultEntries()
    val countFlow: Flow<Int> = dao.observeAutomationVaultCount()
    val foldersFlow: Flow<List<AutomationVaultFolderEntity>> =
        dao.observeAllAutomationVaultFolders().map { custom -> mergeDefaultAndCustomFolders(custom) }
    val filesFlow: Flow<List<AutomationVaultFileEntity>> = dao.observeAllAutomationVaultFiles()

    suspend fun getAllEntriesOnce(): List<AutomationVaultEntryEntity> =
        dao.getAllAutomationVaultEntriesOnce()

    suspend fun getEntryById(entryId: String): AutomationVaultEntryEntity? =
        dao.getAutomationVaultEntryById(entryId.trim())

    suspend fun getAllFoldersOnce(): List<AutomationVaultFolderEntity> =
        mergeDefaultAndCustomFolders(dao.getAllAutomationVaultFoldersOnce())

    suspend fun createFolder(name: String): Result<AutomationVaultFolderEntity> {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Folder name cannot be empty."))
        }
        val existing = getAllFoldersOnce()
        if (existing.any { it.name.equals(cleanName, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("A folder named '$cleanName' already exists."))
        }
        val now = clock()
        val analyzed = VaultValueGenerator.analyzeFolderName(cleanName)
        val entity = AutomationVaultFolderEntity(
            folderId = "folder_custom_${UUID.randomUUID().toString().take(12)}",
            name = cleanName,
            categoryId = analyzed.category.id,
            isDefault = false,
            createdAt = now,
            updatedAt = now
        )
        dao.upsertAutomationVaultFolder(entity)
        return Result.success(entity)
    }

    suspend fun renameFolder(folderId: String, newName: String): Result<AutomationVaultFolderEntity> {
        val cleanId = folderId.trim()
        if (isDefaultFolderId(cleanId)) {
            return Result.failure(IllegalArgumentException("Default folders cannot be renamed."))
        }
        val cleanName = newName.trim()
        if (cleanName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Folder name cannot be empty."))
        }
        val current = dao.getAutomationVaultFolderById(cleanId)
            ?: return Result.failure(NoSuchElementException("Folder not found."))
        val existing = getAllFoldersOnce()
        if (existing.any { it.folderId != cleanId && it.name.equals(cleanName, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("A folder named '$cleanName' already exists."))
        }
        val analyzed = VaultValueGenerator.analyzeFolderName(cleanName)
        val updated = current.copy(
            name = cleanName,
            categoryId = analyzed.category.id,
            updatedAt = clock()
        )
        dao.upsertAutomationVaultFolder(updated)
        return Result.success(updated)
    }

    suspend fun deleteFolder(folderId: String): Result<Unit> {
        val cleanId = folderId.trim()
        if (isDefaultFolderId(cleanId)) {
            return Result.failure(IllegalArgumentException("Default folders cannot be deleted."))
        }
        val existing = dao.getAutomationVaultFolderById(cleanId)
            ?: return Result.failure(NoSuchElementException("Folder not found."))
        val files = dao.getAutomationVaultFilesInFolderOnce(existing.folderId)
        for (file in files) {
            runCatching { File(file.localFilePath).delete() }
        }
        dao.deleteAutomationVaultFolderAndContents(existing.folderId)
        return Result.success(Unit)
    }

    suspend fun saveUploadedFileToFolder(
        folderId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        filesRootDir: File
    ): Result<AutomationVaultFileEntity> {
        val targetFolderId = folderId.trim().ifBlank { DEFAULT_CUSTOM_FOLDER_ID }
        val cleanName = sanitizeFileName(fileName)
        val resolvedMime = mimeType.trim().ifBlank { "application/octet-stream" }
        val vaultDir = File(filesRootDir, VAULT_FILES_DIR_NAME)
        if (!vaultDir.exists()) {
            vaultDir.mkdirs()
        }
        val fileId = "vfile_${UUID.randomUUID().toString().take(12)}"
        val diskFile = File(vaultDir, "${fileId}_$cleanName")
        runCatching {
            diskFile.writeBytes(bytes)
        }.onFailure { err ->
            return Result.failure(IllegalStateException("Could not save file: ${err.message}", err))
        }
        val textPreview = if (bytes.size <= MAX_PREVIEW_BYTES && isTextLikeFile(cleanName, resolvedMime)) {
            runCatching {
                bytes.toString(Charsets.UTF_8)
                    .removePrefix("\uFEFF")
                    .take(MAX_PREVIEW_CHARS)
            }.getOrDefault("")
        } else {
            ""
        }
        val entity = AutomationVaultFileEntity(
            fileId = fileId,
            folderId = targetFolderId,
            fileName = cleanName,
            mimeType = resolvedMime,
            sizeBytes = bytes.size.toLong(),
            localFilePath = diskFile.absolutePath,
            textPreview = textPreview,
            createdAt = clock()
        )
        dao.upsertAutomationVaultFile(entity)
        return Result.success(entity)
    }

    suspend fun deleteUploadedFile(fileId: String): Result<Unit> {
        val cleanId = fileId.trim()
        if (cleanId.isEmpty()) {
            return Result.failure(IllegalArgumentException("File ID is missing."))
        }
        val existing = dao.getAutomationVaultFileById(cleanId)
        if (existing != null) {
            runCatching { File(existing.localFilePath).delete() }
            dao.deleteAutomationVaultFileById(cleanId)
        }
        return Result.success(Unit)
    }

    suspend fun uploadDirectoryFolder(
        folderName: String,
        files: List<VaultFolderFilePayload>,
        filesRootDir: File
    ): Result<VaultDirectoryUploadResult> {
        val baseName = folderName.trim().ifBlank { "Uploaded Folder" }
        val existingNames = getAllFoldersOnce().map { it.name.lowercase() }.toSet()
        var candidateName = baseName
        var suffix = 2
        while (candidateName.lowercase() in existingNames && suffix <= 100) {
            candidateName = "$baseName ($suffix)"
            suffix++
        }
        val folder = createFolder(candidateName).getOrElse { err ->
            return Result.failure(err)
        }
        val savedFiles = mutableListOf<AutomationVaultFileEntity>()
        for (payload in files) {
            val saved = saveUploadedFileToFolder(
                folderId = folder.folderId,
                fileName = payload.fileName,
                mimeType = payload.mimeType,
                bytes = payload.bytes,
                filesRootDir = filesRootDir
            ).getOrElse { err ->
                return Result.failure(err)
            }
            savedFiles.add(saved)
        }
        return Result.success(
            VaultDirectoryUploadResult(
                folder = folder,
                uploadedFiles = savedFiles
            )
        )
    }

    suspend fun saveOrUpdateEntry(
        entryId: String? = null,
        category: AutomationVaultCategory,
        label: String,
        plainValue: String,
        websiteOrigin: String = "",
        isSensitive: Boolean = category.isSensitiveDefault,
        sourceWorkflowId: Long? = null,
        folderId: String = ""
    ): Result<AutomationVaultEntryEntity> {
        val cleanLabel = label.trim().ifBlank { category.label }
        val cleanValue = plainValue
        if (cleanValue.isEmpty()) {
            return Result.failure(IllegalArgumentException("Vault value cannot be empty."))
        }
        val cleanOrigin = if (websiteOrigin.isBlank()) {
            ""
        } else {
            when (val parsed = engine.parseOrigin(websiteOrigin)) {
                is RecordingOriginResult.Valid -> parsed.canonicalOrigin
                else -> websiteOrigin.trim()
            }
        }
        val displayDomain = if (cleanOrigin.isBlank()) {
            "Global"
        } else {
            when (val parsed = engine.parseOrigin(cleanOrigin)) {
                is RecordingOriginResult.Valid -> parsed.displayOrigin.ifBlank { cleanOrigin }
                else -> cleanOrigin
            }
        }
        val existing = entryId?.trim()?.takeIf { it.isNotEmpty() }?.let { dao.getAutomationVaultEntryById(it) }
        val finalId = existing?.entryId ?: UUID.randomUUID().toString()
        val now = clock()
        val sensitive = isSensitive || category == AutomationVaultCategory.PASSWORD

        val chars = cleanValue.toCharArray()
        val encryptedPayload = try {
            when (val enc = crypto.encryptChars(chars)) {
                is VaultCryptoResult.Success -> enc.value
                is VaultCryptoResult.KeyInvalidated -> return Result.failure(IllegalStateException(enc.reason))
                is VaultCryptoResult.AuthenticationRequired -> return Result.failure(IllegalStateException(enc.reason))
                is VaultCryptoResult.CorruptedPayload -> return Result.failure(IllegalStateException(enc.reason))
                is VaultCryptoResult.CryptoFailure -> return Result.failure(IllegalStateException(enc.reason))
            }
        } finally {
            chars.fill('\u0000')
        }

        val resolvedFolderId = folderId.trim()
            .ifBlank { existing?.folderId?.trim().orEmpty() }
            .ifBlank { defaultFolderIdForCategory(category) }

        val entity = AutomationVaultEntryEntity(
            entryId = finalId,
            category = category.id,
            label = cleanLabel,
            websiteOrigin = cleanOrigin,
            domain = displayDomain,
            encryptedValue = encryptedPayload,
            maskedPreview = buildMaskedPreview(category, cleanValue, sensitive),
            isSensitive = sensitive,
            sourceWorkflowId = sourceWorkflowId ?: existing?.sourceWorkflowId,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            lastUsedAt = existing?.lastUsedAt,
            folderId = resolvedFolderId
        )
        dao.upsertAutomationVaultEntry(entity)
        return Result.success(entity)
    }

    suspend fun deleteEntry(entryId: String) {
        val cleanId = entryId.trim()
        if (cleanId.isNotEmpty()) {
            dao.deleteAutomationVaultEntryById(cleanId)
        }
    }

    suspend fun decryptEntryValue(entryId: String, markUsed: Boolean = false): Result<String> {
        val cleanId = entryId.trim()
        if (cleanId.isEmpty()) {
            return Result.failure(IllegalArgumentException("Automation Vault entry ID is missing."))
        }
        val entity = dao.getAutomationVaultEntryById(cleanId)
            ?: return Result.failure(NoSuchElementException("Automation Vault entry not found."))
        val decrypted = when (val res = crypto.decrypt(entity.encryptedValue)) {
            is VaultCryptoResult.Success -> res.value
            is VaultCryptoResult.KeyInvalidated -> return Result.failure(IllegalStateException(res.reason))
            is VaultCryptoResult.AuthenticationRequired -> return Result.failure(IllegalStateException(res.reason))
            is VaultCryptoResult.CorruptedPayload -> return Result.failure(IllegalStateException(res.reason))
            is VaultCryptoResult.CryptoFailure -> return Result.failure(IllegalStateException(res.reason))
        }
        if (markUsed) {
            dao.markAutomationVaultEntryUsed(cleanId, clock())
        }
        return Result.success(decrypted)
    }

    suspend fun pickEntriesForCategoryOrFolder(
        category: AutomationVaultCategory,
        folderId: String = "",
        pinnedEntryId: String? = null
    ): Pair<AutomationVaultFolderEntity, List<AutomationVaultEntryEntity>> {
        val allFolders = getAllFoldersOnce()
        val allEntries = getAllEntriesOnce()
        val pinnedEntry = pinnedEntryId?.trim()?.takeIf { it.isNotEmpty() }?.let { id ->
            allEntries.firstOrNull { it.entryId == id }
        }
        val cleanFolderId = folderId.trim()
            .ifBlank { pinnedEntry?.let { resolveEffectiveFolderId(it) }.orEmpty() }
            .ifBlank { defaultFolderIdForCategory(category) }
        val resolvedFolder = allFolders.firstOrNull { it.folderId == cleanFolderId }
            ?: allFolders.firstOrNull { it.folderId == defaultFolderIdForCategory(category) }
            ?: AutomationVaultFolderEntity(
                folderId = cleanFolderId,
                name = defaultFolderNameForCategory(category),
                categoryId = category.id,
                isDefault = isDefaultFolderId(cleanFolderId),
                createdAt = 0L,
                updatedAt = 0L
            )
        val inFolder = allEntries.filter { resolveEffectiveFolderId(it) == resolvedFolder.folderId }
        val pool = if (inFolder.isNotEmpty() || cleanFolderId != defaultFolderIdForCategory(category)) {
            inFolder
        } else {
            allEntries.filter { it.category.equals(category.id, ignoreCase = true) }
        }
        val sorted = pool.sortedWith(compareBy<AutomationVaultEntryEntity> { it.createdAt }.thenBy { it.entryId })
        val ordered = if (pinnedEntry != null && sorted.any { it.entryId == pinnedEntry.entryId }) {
            listOf(pinnedEntry) + sorted.filterNot { it.entryId == pinnedEntry.entryId }
        } else {
            sorted
        }
        return resolvedFolder to ordered
    }

    suspend fun resolveNextPoolValue(
        category: AutomationVaultCategory,
        folderId: String = "",
        pinnedEntryId: String? = null,
        consumedEntryIds: MutableSet<String> = mutableSetOf(),
        markUsed: Boolean = true
    ): Result<String> {
        val (resolvedFolder, poolEntries) = pickEntriesForCategoryOrFolder(
            category = category,
            folderId = folderId,
            pinnedEntryId = pinnedEntryId
        )
        val nextEntry = poolEntries.firstOrNull { it.entryId !in consumedEntryIds }
            ?: return Result.failure(
                VaultValuesExhaustedException(
                    category = category,
                    folderId = resolvedFolder.folderId,
                    folderName = resolvedFolder.name,
                    message = if (poolEntries.isEmpty()) {
                        "No ${category.label} values in '${resolvedFolder.name}'. Add data in Automation Vault or generate values to replay."
                    } else {
                        "All ${poolEntries.size} ${category.label} value(s) in '${resolvedFolder.name}' have been used. Add more data or generate values to continue."
                    }
                )
            )
        consumedEntryIds.add(nextEntry.entryId)
        return decryptEntryValue(nextEntry.entryId, markUsed = markUsed)
    }

    suspend fun importSelectedCandidates(
        candidates: List<VaultFileImportCandidate>,
        targetFolderId: String = ""
    ): Result<List<AutomationVaultEntryEntity>> {
        val selected = candidates.filter { it.isSelected && it.value.isNotEmpty() }
        if (selected.isEmpty()) {
            return Result.failure(IllegalArgumentException("Select at least one value from the file to save."))
        }
        val cleanTargetFolderId = targetFolderId.trim()
        val saved = mutableListOf<AutomationVaultEntryEntity>()
        for (candidate in selected) {
            val res = saveOrUpdateEntry(
                entryId = candidate.existingEntryId,
                category = candidate.category,
                label = candidate.label,
                plainValue = candidate.value,
                websiteOrigin = candidate.websiteOrigin,
                isSensitive = candidate.isSensitive || candidate.category == AutomationVaultCategory.PASSWORD,
                folderId = cleanTargetFolderId
            )
            if (res.isFailure) {
                return Result.failure(
                    res.exceptionOrNull() ?: IllegalStateException("Failed saving '${candidate.label}' to Automation Vault.")
                )
            }
            res.getOrNull()?.let { saved.add(it) }
        }
        return Result.success(saved)
    }

    companion object {
        private val EMAIL_REGEX = Regex("""^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$""")
        private val PHONE_VALUE_REGEX = Regex("""^[+\d][\d\s().-]{5,18}\d$""")
        private const val VAULT_FILES_DIR_NAME = "automation_vault_files"
        private const val MAX_PREVIEW_BYTES = 256 * 1024
        private const val MAX_PREVIEW_CHARS = 64 * 1024

        const val FOLDER_ID_PASSWORDS = "folder_password"
        const val FOLDER_ID_EMAILS = "folder_email"
        const val FOLDER_ID_PHONE = "folder_phone"
        const val FOLDER_ID_COUNTRY_CODE = "folder_country_code"
        const val FOLDER_ID_DATE_OF_BIRTH = "folder_dob"
        const val FOLDER_ID_GENDER = "folder_gender"
        const val FOLDER_ID_USERNAMES = "folder_username"
        const val FOLDER_ID_NAMES = "folder_name"
        const val FOLDER_ID_ADDRESSES = "folder_address"
        const val DEFAULT_CUSTOM_FOLDER_ID = "folder_custom"

        val DEFAULT_FOLDERS: List<AutomationVaultFolderEntity> = listOf(
            AutomationVaultFolderEntity(
                folderId = FOLDER_ID_PASSWORDS,
                name = "Passwords",
                categoryId = AutomationVaultCategory.PASSWORD.id,
                isDefault = true,
                createdAt = 1L,
                updatedAt = 1L
            ),
            AutomationVaultFolderEntity(
                folderId = FOLDER_ID_EMAILS,
                name = "Emails",
                categoryId = AutomationVaultCategory.EMAIL.id,
                isDefault = true,
                createdAt = 2L,
                updatedAt = 2L
            ),
            AutomationVaultFolderEntity(
                folderId = FOLDER_ID_PHONE,
                name = "Phone",
                categoryId = AutomationVaultCategory.PHONE.id,
                isDefault = true,
                createdAt = 3L,
                updatedAt = 3L
            ),
            AutomationVaultFolderEntity(
                folderId = FOLDER_ID_COUNTRY_CODE,
                name = "Country Code",
                categoryId = AutomationVaultCategory.COUNTRY_CODE.id,
                isDefault = true,
                createdAt = 4L,
                updatedAt = 4L
            ),
            AutomationVaultFolderEntity(
                folderId = FOLDER_ID_DATE_OF_BIRTH,
                name = "Date of Birth",
                categoryId = AutomationVaultCategory.DATE_OF_BIRTH.id,
                isDefault = true,
                createdAt = 5L,
                updatedAt = 5L
            ),
            AutomationVaultFolderEntity(
                folderId = FOLDER_ID_GENDER,
                name = "Gender",
                categoryId = AutomationVaultCategory.GENDER.id,
                isDefault = true,
                createdAt = 6L,
                updatedAt = 6L
            ),
            AutomationVaultFolderEntity(
                folderId = FOLDER_ID_USERNAMES,
                name = "Usernames",
                categoryId = AutomationVaultCategory.USERNAME.id,
                isDefault = true,
                createdAt = 7L,
                updatedAt = 7L
            ),
            AutomationVaultFolderEntity(
                folderId = FOLDER_ID_NAMES,
                name = "Full Names",
                categoryId = AutomationVaultCategory.NAME.id,
                isDefault = true,
                createdAt = 8L,
                updatedAt = 8L
            ),
            AutomationVaultFolderEntity(
                folderId = FOLDER_ID_ADDRESSES,
                name = "Addresses",
                categoryId = AutomationVaultCategory.ADDRESS.id,
                isDefault = true,
                createdAt = 9L,
                updatedAt = 9L
            ),
            AutomationVaultFolderEntity(
                folderId = DEFAULT_CUSTOM_FOLDER_ID,
                name = "Custom Values",
                categoryId = AutomationVaultCategory.CUSTOM.id,
                isDefault = true,
                createdAt = 10L,
                updatedAt = 10L
            )
        )

        private val DEFAULT_FOLDER_IDS: Set<String> = DEFAULT_FOLDERS.map { it.folderId }.toSet()

        fun defaultFolderIdForCategory(category: AutomationVaultCategory): String = when (category) {
            AutomationVaultCategory.PASSWORD -> FOLDER_ID_PASSWORDS
            AutomationVaultCategory.EMAIL -> FOLDER_ID_EMAILS
            AutomationVaultCategory.PHONE -> FOLDER_ID_PHONE
            AutomationVaultCategory.COUNTRY_CODE -> FOLDER_ID_COUNTRY_CODE
            AutomationVaultCategory.DATE_OF_BIRTH -> FOLDER_ID_DATE_OF_BIRTH
            AutomationVaultCategory.GENDER -> FOLDER_ID_GENDER
            AutomationVaultCategory.USERNAME -> FOLDER_ID_USERNAMES
            AutomationVaultCategory.NAME -> FOLDER_ID_NAMES
            AutomationVaultCategory.ADDRESS -> FOLDER_ID_ADDRESSES
            AutomationVaultCategory.CUSTOM -> DEFAULT_CUSTOM_FOLDER_ID
        }

        fun defaultFolderNameForCategory(category: AutomationVaultCategory): String = when (category) {
            AutomationVaultCategory.PASSWORD -> "Passwords"
            AutomationVaultCategory.EMAIL -> "Emails"
            AutomationVaultCategory.PHONE -> "Phone"
            AutomationVaultCategory.COUNTRY_CODE -> "Country Code"
            AutomationVaultCategory.DATE_OF_BIRTH -> "Date of Birth"
            AutomationVaultCategory.GENDER -> "Gender"
            AutomationVaultCategory.USERNAME -> "Usernames"
            AutomationVaultCategory.NAME -> "Full Names"
            AutomationVaultCategory.ADDRESS -> "Addresses"
            AutomationVaultCategory.CUSTOM -> "Custom Values"
        }

        fun isDefaultFolderId(folderId: String): Boolean = folderId.trim() in DEFAULT_FOLDER_IDS

        fun resolveEffectiveFolderId(entry: AutomationVaultEntryEntity): String {
            val raw = entry.folderId.trim()
            if (raw.isNotEmpty()) return raw
            return defaultFolderIdForCategory(AutomationVaultCategory.fromId(entry.category))
        }

        fun mergeDefaultAndCustomFolders(
            customFolders: List<AutomationVaultFolderEntity>
        ): List<AutomationVaultFolderEntity> {
            val customOnly = customFolders.filter { it.folderId !in DEFAULT_FOLDER_IDS && !it.isDefault }
            return DEFAULT_FOLDERS + customOnly
        }

        fun sanitizeFileName(rawName: String): String {
            val trimmed = rawName.trim().substringAfterLast('/').substringAfterLast('\\')
            val cleaned = trimmed.replace(Regex("""[^A-Za-z0-9._ -]"""), "_").trim()
            return cleaned.ifBlank { "vault_file_${System.currentTimeMillis()}" }
        }

        fun isTextLikeFile(fileName: String, mimeType: String): Boolean {
            val lowerMime = mimeType.lowercase()
            if (lowerMime.startsWith("text/") ||
                lowerMime.contains("json") ||
                lowerMime.contains("csv") ||
                lowerMime.contains("xml") ||
                lowerMime.contains("yaml")
            ) {
                return true
            }
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return ext in setOf("txt", "csv", "tsv", "json", "env", "ini", "cfg", "conf", "md", "log", "xml", "yaml", "yml")
        }

        fun buildMaskedPreview(
            category: AutomationVaultCategory,
            rawValue: String,
            isSensitive: Boolean
        ): String {
            if (isSensitive || category == AutomationVaultCategory.PASSWORD) {
                return "••••••••"
            }
            val clean = rawValue.trim()
            if (clean.length <= 2) return clean
            return clean.take(64)
        }

        private val DIAL_CODE_VALUE_REGEX = Regex("""^\+\d{1,4}$""")

        fun detectCategory(
            targetLabel: String,
            selector: String,
            value: String = "",
            isCredentialStep: Boolean = false,
            credentialFieldRole: CredentialFieldRole? = null,
            inputTypeHint: String = ""
        ): AutomationVaultCategory {
            val rawCombined = "$targetLabel $selector $inputTypeHint"
            val normalizedTokens = rawCombined
                .replace(Regex("""([a-z])([A-Z])"""), "$1 $2")
                .replace('_', ' ')
                .replace('-', ' ')
                .lowercase()
            val combined = "$rawCombined $normalizedTokens".lowercase()
            if (isCredentialStep ||
                credentialFieldRole == CredentialFieldRole.PASSWORD ||
                inputTypeHint.equals("password", ignoreCase = true) ||
                selector.contains("""type="password"""", ignoreCase = true) ||
                selector.contains("type='password'", ignoreCase = true) ||
                Regex("""\b(password|passcode|passphrase|pin|pwd|otp|verification code|one time)\b""").containsMatchIn(combined)
            ) {
                return AutomationVaultCategory.PASSWORD
            }
            if (inputTypeHint.equals("email", ignoreCase = true) ||
                selector.contains("""type="email"""", ignoreCase = true) ||
                selector.contains("type='email'", ignoreCase = true) ||
                combined.contains("email") ||
                combined.contains("e-mail") ||
                EMAIL_REGEX.matches(value.trim())
            ) {
                return AutomationVaultCategory.EMAIL
            }
            // Country code MUST be checked before phone so "Phone Country Code" / "tel-country-code" routes properly
            if (Regex("""\b(country code|countrycode|dial code|dialcode|calling code|phone code|tel country code|mobile code|isd code|idd code|isd|idd|country prefix)\b""").containsMatchIn(combined) ||
                selector.contains("tel-country-code", ignoreCase = true) ||
                DIAL_CODE_VALUE_REGEX.matches(value.trim())
            ) {
                return AutomationVaultCategory.COUNTRY_CODE
            }
            if (inputTypeHint.equals("tel", ignoreCase = true) ||
                selector.contains("""type="tel"""", ignoreCase = true) ||
                selector.contains("type='tel'", ignoreCase = true) ||
                Regex("""\b(phone|mobile|tel|telephone|cell|whatsapp|sms|contact number|mobile no|phone no|phoneno|telno)\b""").containsMatchIn(combined) ||
                PHONE_VALUE_REGEX.matches(value.trim())
            ) {
                return AutomationVaultCategory.PHONE
            }
            if (Regex("""\b(date of birth|dateofbirth|birth date|birthdate|birthday|birth day|birth month|birthmonth|birth year|birthyear|year of birth|month of birth|day of birth|dob|bday|bday day|bday month|bday year|born)\b""").containsMatchIn(combined)) {
                return AutomationVaultCategory.DATE_OF_BIRTH
            }
            val valLower = value.trim().lowercase()
            if (Regex("""\b(gender|sex|pronoun|pronouns)\b""").containsMatchIn(combined) ||
                valLower in setOf("male", "female", "non-binary", "nonbinary", "prefer not to say")
            ) {
                return AutomationVaultCategory.GENDER
            }
            if (credentialFieldRole == CredentialFieldRole.USERNAME ||
                Regex("""\b(username|user name|userid|user id|login|handle|account)\b""").containsMatchIn(combined)
            ) {
                return AutomationVaultCategory.USERNAME
            }
            if (Regex("""\b(address|street|city|postal|zip|zipcode|postcode|apartment)\b""").containsMatchIn(combined)) {
                return AutomationVaultCategory.ADDRESS
            }
            if (Regex("""\b(name|first name|firstname|last name|lastname|full name|fullname|given name|family name)\b""").containsMatchIn(combined)) {
                return AutomationVaultCategory.NAME
            }
            return AutomationVaultCategory.CUSTOM
        }

        fun extractRecordingCandidates(
            steps: List<AutomationStep>,
            capturedSecretsByStepId: Map<String, String> = emptyMap(),
            existingEntries: List<AutomationVaultEntryEntity> = emptyList(),
            sourceUrl: String = ""
        ): List<RecordingVaultCandidate> {
            val engine = AutomationEngine()
            val canonicalOrigin = if (sourceUrl.isBlank()) {
                ""
            } else {
                engine.parseOrigin(sourceUrl).canonicalOrigin
            }
            val domain = if (sourceUrl.isBlank()) "" else engine.extractDomain(sourceUrl)
            val eligibleSteps = steps.filter { step ->
                step.actionType == AutomationActionType.INPUT ||
                    step.actionType == AutomationActionType.SELECT ||
                    (step.actionType == AutomationActionType.TOGGLE && (step.useVaultPool || step.vaultCategory.isNotBlank())) ||
                    step.actionType == AutomationActionType.FILL_CREDENTIAL ||
                    step.isCredentialStep
            }
            return eligibleSteps.mapNotNull { step ->
                val secretOverride = capturedSecretsByStepId[step.id].orEmpty()
                val rawVal = if (step.isCredentialStep || step.value.isBlank()) {
                    secretOverride
                } else {
                    step.value.trim()
                }
                val category = if (step.vaultCategory.isNotBlank()) {
                    AutomationVaultCategory.fromId(step.vaultCategory)
                } else {
                    detectCategory(
                        targetLabel = step.targetLabel,
                        selector = step.selector,
                        value = rawVal,
                        isCredentialStep = step.isCredentialStep,
                        credentialFieldRole = step.credentialFieldRole
                    )
                }
                val isPwd = category == AutomationVaultCategory.PASSWORD || step.isCredentialStep
                val hasExplicitPoolBinding = step.useVaultPool || step.vaultCategory.isNotBlank() || !step.vaultEntryId.isNullOrBlank()
                val isFieldOnlyCapable = step.actionType == AutomationActionType.INPUT || step.actionType == AutomationActionType.SELECT
                if (!isPwd && rawVal.isBlank() && !hasExplicitPoolBinding && !isFieldOnlyCapable) {
                    return@mapNotNull null
                }
                val cleanTarget = step.targetLabel
                    .replace(Regex("""\s+field$""", RegexOption.IGNORE_CASE), "")
                    .trim()
                    .ifBlank { category.label }
                val defaultLabel = if (domain.isNotBlank() && domain != "Start Page") {
                    "$cleanTarget ($domain)"
                } else {
                    cleanTarget
                }
                val matchedExisting = step.vaultEntryId?.let { boundId ->
                    existingEntries.firstOrNull { it.entryId == boundId }
                } ?: existingEntries.firstOrNull { existing ->
                    existing.category.equals(category.id, ignoreCase = true) &&
                        (existing.websiteOrigin.isBlank() ||
                            canonicalOrigin.isBlank() ||
                            existing.websiteOrigin == canonicalOrigin) &&
                        existing.label.equals(defaultLabel, ignoreCase = true)
                }
                val isFieldOnlyEmpty = rawVal.isEmpty() && isFieldOnlyCapable
                RecordingVaultCandidate(
                    stepId = step.id,
                    stepOrder = step.order,
                    targetLabel = cleanTarget,
                    selector = step.selector,
                    category = category,
                    vaultLabel = matchedExisting?.label ?: defaultLabel,
                    capturedValue = rawVal,
                    hasSecretCapture = isPwd && secretOverride.isNotEmpty(),
                    isSensitive = category.isSensitiveDefault,
                    isSelected = !isPwd || secretOverride.isNotEmpty() || matchedExisting != null || (isFieldOnlyEmpty && step.credentialReferenceId.isNullOrBlank()),
                    existingEntryId = matchedExisting?.entryId
                )
            }
        }

        fun parseImportContent(
            rawContent: String,
            existingEntries: List<AutomationVaultEntryEntity> = emptyList(),
            defaultOriginUrl: String = ""
        ): List<VaultFileImportCandidate> {
            val cleaned = rawContent
                .removePrefix("\uFEFF")
                .trim()
            if (cleaned.isEmpty()) return emptyList()

            val engine = AutomationEngine()
            val normalizedDefaultOrigin = normalizeImportOrigin(engine, defaultOriginUrl)
            val draft = mutableListOf<VaultFileImportCandidate>()

            fun pushCandidate(
                rawCategory: AutomationVaultCategory? = null,
                rawLabel: String,
                rawValue: String,
                rawOrigin: String = "",
                isSensitiveHint: Boolean? = null
            ) {
                val valTrimmed = rawValue.trim()
                if (valTrimmed.isEmpty()) return
                val cleanLabel = rawLabel.trim().ifBlank { "Imported Value" }
                val origin = normalizeImportOrigin(engine, rawOrigin).ifBlank { normalizedDefaultOrigin }
                val category = rawCategory ?: detectCategory(
                    targetLabel = cleanLabel,
                    selector = cleanLabel,
                    value = valTrimmed
                )
                val sensitive = isSensitiveHint ?: category.isSensitiveDefault
                val matchedExisting = existingEntries.firstOrNull { existing ->
                    existing.category.equals(category.id, ignoreCase = true) &&
                        existing.label.equals(cleanLabel, ignoreCase = true) &&
                        (existing.websiteOrigin.isBlank() || origin.isBlank() || existing.websiteOrigin == origin)
                }
                draft.add(
                    VaultFileImportCandidate(
                        id = "file_cand_${draft.size}_${UUID.randomUUID().toString().take(8)}",
                        category = category,
                        label = cleanLabel,
                        value = valTrimmed,
                        websiteOrigin = origin,
                        isSensitive = sensitive || category == AutomationVaultCategory.PASSWORD,
                        isSelected = true,
                        existingEntryId = matchedExisting?.entryId
                    )
                )
            }

            // 1. JSON format (array, container object, or flat key-value object)
            if (cleaned.startsWith("{") || cleaned.startsWith("[")) {
                val parsedJson = parseJsonImport(cleaned, ::pushCandidate)
                if (parsedJson && draft.isNotEmpty()) {
                    return deduplicateCandidates(draft)
                }
            }

            // Split lines for CSV/TSV and key-value/plain text parsing
            val nonBlankLines = cleaned.lines()
                .map { it.trimEnd('\r') }
                .filter { it.isNotBlank() }

            // 2. CSV / TSV format
            if (nonBlankLines.isNotEmpty()) {
                val parsedCsv = parseDelimitedImport(nonBlankLines, ::pushCandidate)
                if (parsedCsv && draft.isNotEmpty()) {
                    return deduplicateCandidates(draft)
                }
            }

            // 3. Key-value (.env / txt / ini / colon-separated) & plain text lines
            for (rawLine in nonBlankLines) {
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("//") || line.startsWith(";")) continue

                if (line.contains('|')) {
                    val parts = line.split('|').map { it.trim() }.filter { it.isNotEmpty() }
                    when {
                        parts.size >= 3 -> {
                            val site = parts[0]
                            val user = parts[1]
                            val pass = parts[2]
                            val userCat = if (EMAIL_REGEX.matches(user)) AutomationVaultCategory.EMAIL else AutomationVaultCategory.USERNAME
                            pushCandidate(userCat, "$site ${userCat.label}", user, site, false)
                            pushCandidate(AutomationVaultCategory.PASSWORD, "$site Password", pass, site, true)
                        }
                        parts.size == 2 -> {
                            pushCandidate(null, parts[0], parts[1])
                        }
                    }
                    continue
                }

                val eqIdx = line.indexOf('=')
                val colonIdx = findKeyValueColonIndex(line)
                val sepIdx = when {
                    eqIdx in 1..48 && (colonIdx < 0 || eqIdx < colonIdx) -> eqIdx
                    colonIdx in 1..48 -> colonIdx
                    else -> -1
                }
                if (sepIdx > 0) {
                    val left = line.substring(0, sepIdx).trim().trim('"', '\'')
                    val right = line.substring(sepIdx + 1).trim().trim('"', '\'')
                    if (left.isNotEmpty() && right.isNotEmpty()) {
                        // Support credential pair like `alice@example.com:SecretPass123`
                        if (EMAIL_REGEX.matches(left)) {
                            pushCandidate(AutomationVaultCategory.EMAIL, "Account Email", left, "", false)
                            pushCandidate(AutomationVaultCategory.PASSWORD, "Account ($left) Password", right, "", true)
                        } else {
                            pushCandidate(null, prettifyKey(left), right)
                        }
                    }
                } else {
                    val detectedCat = detectCategory("", "", line)
                    val label = when (detectedCat) {
                        AutomationVaultCategory.EMAIL -> "Imported Email"
                        AutomationVaultCategory.PHONE -> "Imported Phone"
                        AutomationVaultCategory.COUNTRY_CODE -> "Imported Country Code"
                        AutomationVaultCategory.DATE_OF_BIRTH -> "Imported Date of Birth"
                        AutomationVaultCategory.GENDER -> "Imported Gender"
                        AutomationVaultCategory.PASSWORD -> "Imported Password"
                        else -> "Imported Value"
                    }
                    pushCandidate(detectedCat, label, line)
                }
            }

            return deduplicateCandidates(draft)
        }

        private fun normalizeImportOrigin(engine: AutomationEngine, raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isBlank()) return ""
            val withScheme = if (trimmed.contains("://")) trimmed else if (trimmed.contains('.')) "https://$trimmed" else return ""
            return when (val parsed = engine.parseOrigin(withScheme)) {
                is RecordingOriginResult.Valid -> parsed.canonicalOrigin
                else -> ""
            }
        }

        private fun findKeyValueColonIndex(line: String): Int {
            val idx = line.indexOf(':')
            if (idx <= 0) return -1
            val before = line.substring(0, idx).trim()
            if (before.equals("http", ignoreCase = true) || before.equals("https", ignoreCase = true)) {
                return -1
            }
            if (before.contains('/') || before.contains(' ') && before.length > 36) {
                return -1
            }
            return idx
        }

        private fun prettifyKey(raw: String): String {
            return raw.trim()
                .replace('_', ' ')
                .replace('-', ' ')
                .split(Regex("""\s+"""))
                .filter { it.isNotEmpty() }
                .joinToString(" ") { part ->
                    part.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase() else ch.toString() }
                }
                .ifBlank { "Imported Value" }
        }

        private fun deduplicateCandidates(items: List<VaultFileImportCandidate>): List<VaultFileImportCandidate> {
            val seen = HashSet<String>()
            val out = mutableListOf<VaultFileImportCandidate>()
            for (item in items) {
                val sig = "${item.category.id}|${item.label.lowercase()}|${item.value}|${item.websiteOrigin.lowercase()}"
                if (seen.add(sig)) {
                    out.add(item)
                }
            }
            return out
        }

        private fun parseJsonImport(
            cleaned: String,
            pushCandidate: (
                rawCategory: AutomationVaultCategory?,
                rawLabel: String,
                rawValue: String,
                rawOrigin: String,
                isSensitiveHint: Boolean?
            ) -> Unit
        ): Boolean {
            fun handleObject(obj: JSONObject, fallbackTitle: String = "") {
                val explicitValue = obj.optString("value", "").ifBlank { obj.optString("secret", "") }
                val siteUrl = obj.optString("websiteOrigin", "").ifBlank {
                    obj.optString("origin", "").ifBlank {
                        obj.optString("url", "").ifBlank { obj.optString("website", "") }
                    }
                }
                val title = obj.optString("label", "").ifBlank {
                    obj.optString("name", "").ifBlank {
                        obj.optString("title", "").ifBlank {
                            obj.optString("site", "").ifBlank { fallbackTitle }
                        }
                    }
                }
                if (obj.has("category") && explicitValue.isNotBlank()) {
                    val cat = AutomationVaultCategory.fromId(obj.optString("category"))
                    val sensitive = if (obj.has("isSensitive")) obj.optBoolean("isSensitive") else cat.isSensitiveDefault
                    pushCandidate(cat, title.ifBlank { cat.label }, explicitValue, siteUrl, sensitive)
                    return
                }
                val skipKeys = setOf(
                    "id", "entryid", "note", "notes", "url", "website", "websiteorigin", "origin",
                    "title", "label", "name", "site", "createdat", "updatedat", "lastusedat", "issensitive", "category"
                )
                val keys = obj.keys()
                var emittedStructured = false
                val prefix = title.takeIf { it.isNotBlank() }
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = obj.opt(k)
                    if (v is JSONObject) {
                        handleObject(v, k)
                        emittedStructured = true
                        continue
                    }
                    if (v is JSONArray) {
                        for (i in 0 until v.length()) {
                            v.optJSONObject(i)?.let {
                                handleObject(it, title)
                                emittedStructured = true
                            }
                        }
                        continue
                    }
                    val strVal = v?.toString()?.trim().orEmpty()
                    if (strVal.isEmpty() || strVal == "null") continue
                    val kLow = k.lowercase()
                    if (kLow in skipKeys && kLow != "name") continue
                    if (kLow == "name" && (obj.has("password") || obj.has("email") || obj.has("username") || obj.has("phone"))) {
                        continue
                    }
                    val pretty = prettifyKey(k)
                    val label = if (!prefix.isNullOrBlank() && !prefix.equals(pretty, ignoreCase = true)) {
                        "$prefix $pretty"
                    } else {
                        pretty
                    }
                    val detected = detectCategory(k, k, strVal)
                    pushCandidate(detected, label, strVal, siteUrl, detected.isSensitiveDefault)
                    emittedStructured = true
                }
                if (!emittedStructured && title.isNotBlank() && explicitValue.isNotBlank()) {
                    pushCandidate(null, title, explicitValue, siteUrl, null)
                }
            }

            return runCatching {
                if (cleaned.startsWith("[")) {
                    val arr = JSONArray(cleaned)
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.let { handleObject(it) }
                    }
                    true
                } else {
                    val obj = JSONObject(cleaned)
                    val containerKey = listOf("items", "entries", "credentials", "logins", "values", "vault")
                        .firstOrNull { obj.optJSONArray(it) != null }
                    if (containerKey != null) {
                        val arr = obj.getJSONArray(containerKey)
                        for (i in 0 until arr.length()) {
                            arr.optJSONObject(i)?.let { handleObject(it) }
                        }
                    } else {
                        handleObject(obj)
                    }
                    true
                }
            }.getOrDefault(false)
        }

        private fun parseDelimitedImport(
            lines: List<String>,
            pushCandidate: (
                rawCategory: AutomationVaultCategory?,
                rawLabel: String,
                rawValue: String,
                rawOrigin: String,
                isSensitiveHint: Boolean?
            ) -> Unit
        ): Boolean {
            val first = lines.firstOrNull() ?: return false
            val delimiter = when {
                first.contains('\t') -> '\t'
                first.contains(',') -> ','
                else -> return false
            }
            val rows = lines.map { parseCsvRow(it, delimiter) }.filter { row -> row.any { it.isNotBlank() } }
            if (rows.isEmpty()) return false

            val header = rows.first().map { it.trim().lowercase() }
            val hasRecognizedHeader = header.any {
                it in setOf(
                    "password", "pass", "pwd", "username", "user", "login",
                    "email", "e-mail", "phone", "mobile", "tel",
                    "country code", "country_code", "countrycode", "dial code", "dial_code",
                    "dob", "birthday", "birthdate", "date of birth", "gender", "sex",
                    "value", "secret", "category", "type", "label", "name", "title", "url", "website", "address", "fullname"
                )
            }

            if (hasRecognizedHeader && rows.size >= 2) {
                val idxCategory = header.indexOfFirst { it == "category" || it == "type" }
                val idxValue = header.indexOfFirst { it == "value" || it == "secret" }
                val idxLabel = header.indexOfFirst { it == "label" || it == "name" || it == "title" || it == "site" }
                val idxUrl = header.indexOfFirst { it == "url" || it == "website" || it == "origin" || it == "domain" }

                for (r in 1 until rows.size) {
                    val row = rows[r]
                    val urlVal = idxUrl.takeIf { it in row.indices }?.let { row[it].trim() }.orEmpty()
                    val titleVal = idxLabel.takeIf { it in row.indices }?.let { row[it].trim() }.orEmpty()

                    if (idxCategory >= 0 && idxValue >= 0 && idxValue in row.indices) {
                        val rawVal = row[idxValue].trim()
                        if (rawVal.isNotEmpty()) {
                            val cat = AutomationVaultCategory.fromId(row.getOrNull(idxCategory))
                            val lbl = titleVal.ifBlank { cat.label }
                            pushCandidate(cat, lbl, rawVal, urlVal, cat.isSensitiveDefault)
                        }
                        continue
                    }

                    // Otherwise iterate each value column (supports Chrome/Brave/Bitwarden CSV + custom multi-column CSVs)
                    val metaIndices = setOfNotNull(
                        idxUrl.takeIf { it >= 0 },
                        header.indexOfFirst { it == "note" || it == "notes" }.takeIf { it >= 0 }
                    )
                    val nonMetaPopulated = header.indices.filter { it !in metaIndices && !row.getOrNull(it).isNullOrBlank() }
                    for (colIdx in nonMetaPopulated) {
                        val colName = header[colIdx]
                        if ((colIdx == idxLabel) && nonMetaPopulated.size > 1 && colName in setOf("name", "title", "label", "site")) {
                            continue
                        }
                        val cellVal = row[colIdx].trim()
                        if (cellVal.isEmpty()) continue
                        val detected = detectCategory(colName, colName, cellVal)
                        val colNice = prettifyKey(colName)
                        val finalLabel = if (titleVal.isNotBlank() && !titleVal.equals(colNice, ignoreCase = true)) {
                            "$titleVal $colNice"
                        } else {
                            colNice
                        }
                        pushCandidate(detected, finalLabel, cellVal, urlVal, detected.isSensitiveDefault)
                    }
                }
                return true
            }

            // Headerless CSV: support `label,value` or `category,label,value[,url]`
            if (rows.all { it.size >= 2 }) {
                for (row in rows) {
                    when {
                        row.size >= 3 && AutomationVaultCategory.entries.any {
                            it.id.equals(row[0].trim(), true) || it.label.equals(row[0].trim(), true)
                        } -> {
                            val cat = AutomationVaultCategory.fromId(row[0])
                            val lbl = row[1].trim().ifBlank { cat.label }
                            val valStr = row[2].trim()
                            val originStr = row.getOrNull(3)?.trim().orEmpty()
                            pushCandidate(cat, lbl, valStr, originStr, cat.isSensitiveDefault)
                        }
                        row.size >= 2 -> {
                            val lbl = row[0].trim()
                            val valStr = row[1].trim()
                            val originStr = row.getOrNull(2)?.trim().orEmpty()
                            pushCandidate(null, lbl, valStr, originStr, null)
                        }
                    }
                }
                return true
            }
            return false
        }

        private fun parseCsvRow(line: String, delimiter: Char): List<String> {
            val out = mutableListOf<String>()
            val cur = StringBuilder()
            var inQuotes = false
            var i = 0
            while (i < line.length) {
                val ch = line[i]
                if (ch == '"') {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        cur.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                } else if (ch == delimiter && !inQuotes) {
                    out.add(cur.toString())
                    cur.clear()
                } else {
                    cur.append(ch)
                }
                i++
            }
            out.add(cur.toString())
            return out
        }
    }
}
