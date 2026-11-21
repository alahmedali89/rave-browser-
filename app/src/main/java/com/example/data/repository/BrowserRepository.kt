package com.example.data.repository

import com.example.data.local.ApexDao
import com.example.data.local.BlockedDomainLogEntity
import com.example.data.local.BookmarkEntity
import com.example.data.local.ExtractionRuleEntity
import com.example.data.local.PrivacyStatSummaryEntity
import com.example.data.local.SearchHistoryEntity
import com.example.data.preferences.BrowserPreferences
import com.example.data.preferences.DownloadLocationMode
import com.example.data.preferences.SearchEngineOption
import com.example.data.preferences.ThemeModeOption
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.local.TopVisitEntity
import com.example.data.local.BrowsingHistoryEntity
import com.example.data.local.DownloadItemEntity
import com.example.data.local.SavedPasswordEntity
import com.example.data.local.CredentialVaultEntity
import com.example.data.local.AutomationWorkflowEntity
import com.example.data.local.AutomationWorkflowVersionEntity
import com.example.data.local.AutomationExecutionEntity
import com.example.automation.AndroidKeystoreVaultKeyProvider
import com.example.automation.AutomationEngine
import com.example.automation.AutomationStep
import com.example.automation.AutomationStepCodec
import com.example.automation.AutomationStepDecodeResult
import com.example.automation.AutomationType
import com.example.automation.AutomationVaultManager
import com.example.automation.CredentialAuthenticationManager
import com.example.automation.CredentialVaultCrypto
import com.example.automation.CredentialVaultManager
import com.example.automation.DetectedPageElement
import com.example.automation.ExportPackageType
import com.example.automation.InMemoryVaultKeyMetadataStore
import com.example.automation.SharedPreferencesVaultKeyMetadataStore
import com.example.automation.WorkflowConflictResolution
import com.example.automation.WorkflowDiffEngine
import com.example.automation.WorkflowDiffReport
import com.example.automation.WorkflowDocument
import com.example.automation.WorkflowDocumentMetadata
import com.example.automation.WorkflowDryRunEngine
import com.example.automation.WorkflowDryRunReport
import com.example.automation.WorkflowImportExportManager
import com.example.automation.WorkflowImportResult
import com.example.automation.WorkflowOrganizationMetadata
import com.example.automation.WorkflowValidationEngine
import com.example.automation.WorkflowValidationReport
import com.example.automation.WorkflowVersionManager
import com.example.automation.WorkflowVersionResult
import com.example.engine.PasswordStrengthEvaluator
import com.example.engine.PasswordVaultCrypto
import com.example.engine.PasswordVaultUtil
import java.util.UUID
import java.net.URI
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class BrowserRepository(
    private val dao: ApexDao,
    private val preferencesRepository: UserPreferencesRepository,
    private val sharedPrefs: SharedPreferences? = null,
    private val passwordCrypto: PasswordVaultCrypto = PasswordVaultCrypto(),
    val credentialVaultManager: CredentialVaultManager = CredentialVaultManager(
        dao = dao,
        crypto = CredentialVaultCrypto(
            keyProvider = AndroidKeystoreVaultKeyProvider(
                metadataStore = if (sharedPrefs != null) {
                    SharedPreferencesVaultKeyMetadataStore(sharedPrefs)
                } else {
                    InMemoryVaultKeyMetadataStore()
                }
            ),
            metadataStore = if (sharedPrefs != null) {
                SharedPreferencesVaultKeyMetadataStore(sharedPrefs)
            } else {
                InMemoryVaultKeyMetadataStore()
            }
        ),
        authManager = CredentialAuthenticationManager()
    )
) {
    private val historyMutex = Mutex()
    private val validationEngine = AutomationEngine()
    val automationVaultManager = AutomationVaultManager(
        dao = dao,
        crypto = credentialVaultManager.crypto,
        engine = validationEngine
    )
    val workflowVersionManager = WorkflowVersionManager(dao)
    val workflowImportExportManager = WorkflowImportExportManager(dao, workflowVersionManager, validationEngine)

    val preferencesFlow: Flow<BrowserPreferences> = preferencesRepository.preferencesFlow

    val privacyStatsFlow: Flow<PrivacyStatSummaryEntity> = dao.observePrivacyStats()
        .map { it ?: PrivacyStatSummaryEntity() }

    val recentBlockedLogsFlow: Flow<List<BlockedDomainLogEntity>> = dao.observeRecentBlockedLogs()

    val extractionRulesFlow: Flow<List<ExtractionRuleEntity>> = dao.observeExtractionRules()

    val bookmarksFlow: Flow<List<BookmarkEntity>> = dao.observeBookmarks()

    val searchHistoryFlow: Flow<List<SearchHistoryEntity>> = dao.observeSearchHistory()

    val topVisitsFlow: Flow<List<TopVisitEntity>> = dao.observeTopVisits()

    val browsingHistoryFlow: Flow<List<BrowsingHistoryEntity>> = dao.observeBrowsingHistory()

    val browsingHistoryCountFlow: Flow<Int> = dao.observeBrowsingHistoryCount()

    val downloadsFlow: Flow<List<DownloadItemEntity>> = dao.observeDownloads()

    val downloadsCountFlow: Flow<Int> = dao.observeDownloadsCount()

    val savedPasswordsFlow: Flow<List<SavedPasswordEntity>> = dao.observeSavedPasswords()

    val savedPasswordsCountFlow: Flow<Int> = dao.observeSavedPasswordsCount()

    val credentialVaultEntriesFlow: Flow<List<CredentialVaultEntity>> = credentialVaultManager.credentialsFlow

    val credentialVaultCountFlow: Flow<Int> = credentialVaultManager.credentialsCountFlow

    val automationVaultEntriesFlow = automationVaultManager.entriesFlow

    val automationVaultCountFlow = automationVaultManager.countFlow

    val automationVaultFoldersFlow = automationVaultManager.foldersFlow

    val automationVaultFilesFlow = automationVaultManager.filesFlow

    val automationWorkflowsFlow: Flow<List<AutomationWorkflowEntity>> = dao.observeAutomationWorkflows()

    val automationExecutionsFlow: Flow<List<AutomationExecutionEntity>> = dao.observeAutomationExecutions()

    val latestInterruptedExecutionFlow: Flow<AutomationExecutionEntity?> = dao.observeLatestInterruptedExecution()

    fun observeAutomationExecutionsForWorkflow(workflowId: Long): Flow<List<AutomationExecutionEntity>> {
        return dao.observeAutomationExecutionsForWorkflow(workflowId)
    }

    suspend fun getAutomationExecutionById(executionId: Long): AutomationExecutionEntity? {
        return dao.getAutomationExecutionById(executionId)
    }

    suspend fun getLatestInterruptedExecutionOnce(): AutomationExecutionEntity? {
        return dao.getLatestInterruptedExecutionOnce()
    }

    /**
     * Fresh-install initialization: creates only a zeroed-out telemetry row if absent,
     * and seeds initial quick shortcuts for top visits or restores from SharedPreferences.
     */
    suspend fun ensureFreshInitialized() {
        if (dao.getPrivacyStatsOnce() == null) {
            dao.upsertPrivacyStats(PrivacyStatSummaryEntity())
        }

        // Restore bookmarks from SharedPreferences if DB is clean
        sharedPrefs?.getString("saved_bookmarks_json", null)?.let { jsonStr ->
            if (dao.getBookmarksOnce().isEmpty() && jsonStr.isNotBlank()) {
                runCatching {
                    val array = JSONArray(jsonStr)
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        dao.upsertBookmark(
                            BookmarkEntity(
                                url = item.optString("url"),
                                title = item.optString("title"),
                                createdAt = item.optLong("createdAt", System.currentTimeMillis())
                            )
                        )
                    }
                }
            }
        }

        // Purge any legacy pre-seeded default top visits once so only real user-added top visits exist
        val purgedDefaultSeeds = sharedPrefs?.getBoolean("purged_default_seeded_top_visits_v2", false) ?: true
        if (!purgedDefaultSeeds) {
            val existing = dao.getTopVisitsOnce()
            for (item in existing) {
                dao.deleteTopVisitById(item.id)
            }
            sharedPrefs?.edit()
                ?.remove("saved_top_visits_json")
                ?.putBoolean("purged_default_seeded_top_visits_v2", true)
                ?.apply()
        }

        // Ensure Desktop Viewport starts off by default
        val resetDesktopDefault = sharedPrefs?.getBoolean("reset_default_desktop_viewport_v1", false) ?: true
        if (!resetDesktopDefault) {
            preferencesRepository.setDesktopViewportEnabled(false)
            sharedPrefs?.edit()
                ?.putBoolean("reset_default_desktop_viewport_v1", true)
                ?.apply()
        }

        // Ensure Brave Search is set as the default search engine
        val resetBraveDefault = sharedPrefs?.getBoolean("reset_default_search_engine_brave_v1", false) ?: true
        if (!resetBraveDefault) {
            preferencesRepository.setSearchEngine(SearchEngineOption.BRAVE)
            sharedPrefs?.edit()
                ?.putBoolean("reset_default_search_engine_brave_v1", true)
                ?.apply()
        }

        // Restore only real user-added top visits from SharedPreferences if DB is empty
        if (dao.getTopVisitsOnce().isEmpty()) {
            val restoredJson = sharedPrefs?.getString("user_added_top_visits_json", null)
            if (!restoredJson.isNullOrBlank()) {
                runCatching {
                    val array = JSONArray(restoredJson)
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        dao.upsertTopVisit(
                            TopVisitEntity(
                                title = item.optString("title"),
                                url = item.optString("url"),
                                createdAt = item.optLong("createdAt", System.currentTimeMillis())
                            )
                        )
                    }
                }
            }
        }
    }

    private suspend fun syncBookmarksToSharedPrefs() {
        sharedPrefs?.let { sp ->
            runCatching {
                val current = dao.getBookmarksOnce()
                val array = JSONArray()
                for (b in current) {
                    val obj = JSONObject()
                    obj.put("id", b.id)
                    obj.put("url", b.url)
                    obj.put("title", b.title)
                    obj.put("createdAt", b.createdAt)
                    array.put(obj)
                }
                sp.edit().putString("saved_bookmarks_json", array.toString()).apply()
            }
        }
    }

    private suspend fun syncTopVisitsToSharedPrefs() {
        sharedPrefs?.let { sp ->
            runCatching {
                val current = dao.getTopVisitsOnce()
                val array = JSONArray()
                for (v in current) {
                    val obj = JSONObject()
                    obj.put("id", v.id)
                    obj.put("title", v.title)
                    obj.put("url", v.url)
                    obj.put("createdAt", v.createdAt)
                    array.put(obj)
                }
                sp.edit().putString("user_added_top_visits_json", array.toString()).apply()
            }
        }
    }

    suspend fun addTopVisit(title: String, url: String) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) return
        val finalUrl = if (!trimmedUrl.startsWith("http://", ignoreCase = true) &&
            !trimmedUrl.startsWith("https://", ignoreCase = true) &&
            !trimmedUrl.startsWith("apex://", ignoreCase = true)
        ) {
            "https://$trimmedUrl"
        } else {
            trimmedUrl
        }
        val cleanTitle = title.trim().ifBlank {
            finalUrl.removePrefix("https://").removePrefix("http://").removePrefix("www.")
        }
        dao.upsertTopVisit(
            TopVisitEntity(
                title = cleanTitle,
                url = finalUrl,
                createdAt = System.currentTimeMillis()
            )
        )
        syncTopVisitsToSharedPrefs()
    }

    suspend fun deleteTopVisit(id: Long) {
        dao.deleteTopVisitById(id)
        syncTopVisitsToSharedPrefs()
    }

    suspend fun getBookmarkByUrl(url: String): BookmarkEntity? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null
        val normalizedTarget = normalizeHistoryUrl(trimmed)
        return dao.getBookmarksOnce().firstOrNull { bookmark ->
            bookmark.url.trim().equals(trimmed, ignoreCase = true) ||
                (normalizedTarget.isNotEmpty() &&
                    normalizeHistoryUrl(bookmark.url).equals(normalizedTarget, ignoreCase = true))
        }
    }

    suspend fun saveBookmark(url: String, title: String) {
        dao.upsertBookmark(
            BookmarkEntity(
                url = url,
                title = title.ifBlank { url },
                createdAt = System.currentTimeMillis()
            )
        )
        syncBookmarksToSharedPrefs()
    }

    suspend fun deleteBookmark(id: Long) {
        dao.deleteBookmarkById(id)
        syncBookmarksToSharedPrefs()
    }

    suspend fun importBookmarksFromRawContent(rawContent: String): Int {
        val parsed = parseImportedBookmarks(rawContent)
        if (parsed.isEmpty()) return 0

        val existing = dao.getBookmarksOnce()
        val existingByUrl = existing.associateBy { it.url.trim().lowercase() }.toMutableMap()
        var importedCount = 0
        val now = System.currentTimeMillis()

        for ((index, pair) in parsed.withIndex()) {
            val rawUrl = pair.first.trim()
            val rawTitle = pair.second.trim()
            if (rawUrl.isBlank()) continue
            val normalizedUrl = if (!rawUrl.startsWith("http://", ignoreCase = true) &&
                !rawUrl.startsWith("https://", ignoreCase = true) &&
                !rawUrl.startsWith("apex://", ignoreCase = true)
            ) {
                "https://$rawUrl"
            } else {
                rawUrl
            }
            val cleanTitle = rawTitle.ifBlank {
                normalizedUrl.removePrefix("https://").removePrefix("http://").removePrefix("www.")
            }
            val key = normalizedUrl.lowercase()
            val prev = existingByUrl[key]
            val entity = if (prev != null) {
                prev.copy(title = cleanTitle)
            } else {
                BookmarkEntity(
                    url = normalizedUrl,
                    title = cleanTitle,
                    createdAt = now + index
                )
            }
            dao.upsertBookmark(entity)
            existingByUrl[key] = entity
            importedCount++
        }

        if (importedCount > 0) {
            syncBookmarksToSharedPrefs()
        }
        return importedCount
    }

    companion object {
        fun exportBookmarksAsHtml(bookmarks: List<BookmarkEntity>): String {
            val sb = StringBuilder()
            sb.appendLine("<!DOCTYPE NETSCAPE-Bookmark-file-1>")
            sb.appendLine("<!-- This is an automatically generated file.")
            sb.appendLine("     It will be read and overwritten.")
            sb.appendLine("     DO NOT EDIT! -->")
            sb.appendLine("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">")
            sb.appendLine("<TITLE>Bookmarks</TITLE>")
            sb.appendLine("<H1>Bookmarks</H1>")
            sb.appendLine("<DL><p>")
            sb.appendLine("    <DT><H3 ADD_DATE=\"${System.currentTimeMillis() / 1000}\">Apex Browser Bookmarks</H3>")
            sb.appendLine("    <DL><p>")
            for (bookmark in bookmarks) {
                val safeUrl = escapeHtmlAttr(bookmark.url)
                val safeTitle = escapeHtmlText(bookmark.title.ifBlank { bookmark.url })
                val addDateSec = (bookmark.createdAt / 1000L).coerceAtLeast(1L)
                sb.appendLine("        <DT><A HREF=\"$safeUrl\" ADD_DATE=\"$addDateSec\">$safeTitle</A>")
            }
            sb.appendLine("    </DL><p>")
            sb.appendLine("</DL><p>")
            return sb.toString()
        }

        fun exportBookmarksAsJson(bookmarks: List<BookmarkEntity>): String {
            val array = JSONArray()
            for (b in bookmarks) {
                val obj = JSONObject()
                obj.put("title", b.title)
                obj.put("url", b.url)
                obj.put("createdAt", b.createdAt)
                array.put(obj)
            }
            return array.toString(2)
        }

        fun parseImportedBookmarks(rawContent: String): List<Pair<String, String>> {
            val trimmed = rawContent.trim()
            if (trimmed.isEmpty()) return emptyList()

            val results = mutableListOf<Pair<String, String>>()
            val seenUrls = mutableSetOf<String>()

            fun addCandidate(url: String, title: String) {
                val cleanUrl = unescapeHtml(url.trim())
                if (cleanUrl.isBlank() ||
                    cleanUrl.startsWith("javascript:", ignoreCase = true) ||
                    cleanUrl.startsWith("place:", ignoreCase = true) ||
                    cleanUrl.startsWith("about:", ignoreCase = true)
                ) {
                    return
                }
                val key = cleanUrl.lowercase()
                if (seenUrls.add(key)) {
                    val cleanTitle = unescapeHtml(title.replace(Regex("<[^>]*>"), "").trim())
                        .ifBlank { cleanUrl }
                    results.add(cleanUrl to cleanTitle)
                }
            }

            // 1. Try JSON Array or JSON Object (Apex / Chrome / Brave JSON format)
            if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
                runCatching {
                    if (trimmed.startsWith("[")) {
                        val arr = JSONArray(trimmed)
                        extractFromJsonArray(arr, ::addCandidate)
                    } else {
                        val obj = JSONObject(trimmed)
                        extractFromJsonObject(obj, ::addCandidate)
                    }
                }
                if (results.isNotEmpty()) return results
            }

            // 2. Try Netscape HTML <A HREF="...">Title</A>
            val anchorRegex = Regex(
                pattern = """<a\b[^>]*?\bhref\s*=\s*["']([^"']+)["'][^>]*>(.*?)</a>""",
                options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
            anchorRegex.findAll(trimmed).forEach { match ->
                val url = match.groupValues[1]
                val title = match.groupValues[2]
                addCandidate(url, title)
            }
            if (results.isNotEmpty()) return results

            // 3. Try Markdown links [Title](https://...) or plain URL lines
            val mdRegex = Regex("""\[([^\]]+)]\((https?://[^\s)]+)\)""", RegexOption.IGNORE_CASE)
            mdRegex.findAll(trimmed).forEach { match ->
                addCandidate(match.groupValues[2], match.groupValues[1])
            }
            if (results.isNotEmpty()) return results

            val urlLineRegex = Regex("""(https?://[^\s"'<>]+)""", RegexOption.IGNORE_CASE)
            trimmed.lineSequence().forEach { line ->
                val match = urlLineRegex.find(line)
                if (match != null) {
                    val url = match.groupValues[1]
                    val leftoverTitle = line.replace(url, "").trim(' ', '-', '•', ':', '|', '\t')
                    addCandidate(url, leftoverTitle)
                }
            }
            return results
        }

        private fun extractFromJsonArray(
            array: JSONArray,
            onBookmark: (url: String, title: String) -> Unit
        ) {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                extractFromJsonObject(obj, onBookmark)
            }
        }

        private fun extractFromJsonObject(
            obj: JSONObject,
            onBookmark: (url: String, title: String) -> Unit
        ) {
            val url = obj.optString("url").ifBlank { obj.optString("uri") }.ifBlank { obj.optString("href") }
            val title = obj.optString("title").ifBlank { obj.optString("name") }
            if (url.isNotBlank()) {
                onBookmark(url, title)
            }
            obj.optJSONArray("children")?.let { extractFromJsonArray(it, onBookmark) }
            obj.optJSONArray("bookmarks")?.let { extractFromJsonArray(it, onBookmark) }
            obj.optJSONObject("roots")?.let { roots ->
                val keys = roots.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    roots.optJSONObject(key)?.let { extractFromJsonObject(it, onBookmark) }
                }
            }
        }

        private fun escapeHtmlAttr(value: String): String =
            value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")

        private fun escapeHtmlText(value: String): String =
            value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")

        private fun unescapeHtml(value: String): String =
            value.replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
    }

    suspend fun recordSearchQuery(query: String, engineName: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("apex://", ignoreCase = true)) return
        dao.upsertSearchHistory(
            SearchHistoryEntity(
                query = trimmed,
                searchEngineName = engineName,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteSearchHistoryItem(id: Long) {
        dao.deleteSearchHistoryById(id)
    }

    suspend fun clearAllSearchHistory() {
        dao.clearSearchHistory()
    }

    suspend fun recordBlockedItems(
        pageHost: String,
        trackersDelta: Int,
        adsDelta: Int,
        bytesDelta: Long,
        timeMsDelta: Long,
        sampleDomain: String?,
        sampleCategory: String?
    ) {
        val current = dao.getPrivacyStatsOnce() ?: PrivacyStatSummaryEntity()
        dao.upsertPrivacyStats(
            current.copy(
                trackersBlocked = current.trackersBlocked + trackersDelta,
                adsBlocked = current.adsBlocked + adsDelta,
                bytesSaved = current.bytesSaved + bytesDelta,
                timeSavedMs = current.timeSavedMs + timeMsDelta,
                updatedAt = System.currentTimeMillis()
            )
        )
        if (!sampleDomain.isNullOrBlank()) {
            dao.insertBlockedLog(
                BlockedDomainLogEntity(
                    pageHost = pageHost.ifBlank { "web" },
                    blockedDomain = sampleDomain,
                    category = sampleCategory ?: "Tracker",
                    savedBytes = bytesDelta.coerceAtLeast(8_192L)
                )
            )
        }
    }

    suspend fun recordHttpsUpgrade() {
        val current = dao.getPrivacyStatsOnce() ?: PrivacyStatSummaryEntity()
        dao.upsertPrivacyStats(
            current.copy(
                httpsUpgrades = current.httpsUpgrades + 1,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun resetTelemetryStats() {
        dao.upsertPrivacyStats(PrivacyStatSummaryEntity())
        dao.clearBlockedLogs()
        dao.clearSearchHistory()
        dao.clearAllBrowsingHistory()
    }

    fun normalizeHistoryUrl(rawUrl: String): String {
        val noFragment = rawUrl.trim().substringBefore('#').trim()
        if (noFragment.isEmpty()) return ""
        return runCatching {
            val uri = URI(noFragment)
            val scheme = uri.scheme?.lowercase() ?: return@runCatching noFragment.trimEnd('/')
            val host = uri.host?.lowercase() ?: return@runCatching noFragment.trimEnd('/')
            val portPart = if (uri.port != -1 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""
            val rawPath = uri.rawPath.orEmpty()
            val normalizedPath = if (rawPath.isEmpty() || rawPath == "/") "" else rawPath.trimEnd('/')
            val queryPart = uri.rawQuery?.takeIf { it.isNotBlank() }?.let { "?$it" }.orEmpty()
            "$scheme://$host$portPart$normalizedPath$queryPart"
        }.getOrElse {
            noFragment.trimEnd('/')
        }
    }

    suspend fun recordPageVisit(
        url: String,
        title: String,
        isHttps: Boolean = true,
        trackersBlockedOnVisit: Int = 0,
        incrementVisitCount: Boolean = true,
        previousUrlInSameLoad: String? = null
    ) = historyMutex.withLock {
        val normalizedUrl = normalizeHistoryUrl(url)
        if (normalizedUrl.isEmpty() ||
            normalizedUrl.equals("apex://home", ignoreCase = true) ||
            normalizedUrl.startsWith("data:", ignoreCase = true) ||
            normalizedUrl.equals("about:blank", ignoreCase = true)
        ) {
            return@withLock
        }

        val host = runCatching {
            URI(normalizedUrl).host?.removePrefix("www.")?.lowercase().orEmpty()
        }.getOrDefault("")
            .ifBlank {
                normalizedUrl.substringAfter("://", normalizedUrl)
                    .substringBefore('/')
                    .removePrefix("www.")
                    .lowercase()
            }

        val cleanTitle = title.trim().ifBlank { host.ifBlank { normalizedUrl } }
        val now = System.currentTimeMillis()

        val existingDirect = dao.getLatestHistoryByUrl(normalizedUrl)
        val existingFromRedirect = if (existingDirect == null && !previousUrlInSameLoad.isNullOrBlank()) {
            val prevNormalized = normalizeHistoryUrl(previousUrlInSameLoad)
            if (prevNormalized.isNotBlank()) dao.getLatestHistoryByUrl(prevNormalized) else null
        } else {
            null
        }
        val existingRecent = if (existingDirect == null && existingFromRedirect == null && !incrementVisitCount) {
            dao.getMostRecentHistoryEntry()?.takeIf { (now - it.visitedAt) < 12_000L }
        } else {
            null
        }
        val existing = existingDirect ?: existingFromRedirect ?: existingRecent

        if (existing != null) {
            val isRapidDuplicateCallback = (now - existing.visitedAt) < 15_000L
            if (!incrementVisitCount || isRapidDuplicateCallback) {
                // Same page load finishing subresources/title/redirects: update metadata without incrementing visitCount
                val resolvedTitle = if (cleanTitle.equals(host, ignoreCase = true) &&
                    existing.title.isNotBlank() &&
                    !existing.title.equals(host, ignoreCase = true)
                ) {
                    existing.title
                } else {
                    cleanTitle
                }
                dao.upsertBrowsingHistory(
                    existing.copy(
                        url = normalizedUrl,
                        title = resolvedTitle,
                        host = host,
                        isHttps = isHttps,
                        trackersBlockedOnVisit = maxOf(existing.trackersBlockedOnVisit, trackersBlockedOnVisit),
                        visitCount = existing.visitCount.coerceAtLeast(1)
                    )
                )
            } else if ((now - existing.visitedAt) < 6 * 3600_000L) {
                dao.upsertBrowsingHistory(
                    existing.copy(
                        url = normalizedUrl,
                        title = cleanTitle,
                        host = host,
                        isHttps = isHttps,
                        trackersBlockedOnVisit = maxOf(existing.trackersBlockedOnVisit, trackersBlockedOnVisit),
                        visitCount = existing.visitCount + 1,
                        visitedAt = now
                    )
                )
            } else {
                val cumulativeCount = existing.visitCount + 1
                dao.upsertBrowsingHistory(
                    BrowsingHistoryEntity(
                        url = normalizedUrl,
                        title = cleanTitle,
                        host = host,
                        isHttps = isHttps,
                        trackersBlockedOnVisit = trackersBlockedOnVisit,
                        visitCount = cumulativeCount,
                        visitedAt = now
                    )
                )
            }
        } else {
            dao.upsertBrowsingHistory(
                BrowsingHistoryEntity(
                    url = normalizedUrl,
                    title = cleanTitle,
                    host = host,
                    isHttps = isHttps,
                    trackersBlockedOnVisit = trackersBlockedOnVisit,
                    visitCount = 1,
                    visitedAt = now
                )
            )
        }
    }

    suspend fun deleteBrowsingHistoryItem(id: Long) {
        dao.deleteBrowsingHistoryById(id)
    }

    suspend fun clearBrowsingHistorySince(sinceMillis: Long) {
        dao.deleteBrowsingHistorySince(sinceMillis)
    }

    suspend fun clearAllBrowsingHistory() {
        dao.clearAllBrowsingHistory()
    }

    suspend fun saveExtractionRule(rule: ExtractionRuleEntity): Long {
        return dao.upsertExtractionRule(rule)
    }

    suspend fun updateRuleRunCount(id: Long, count: Int) {
        dao.updateRuleRunCount(id, count)
    }

    suspend fun deleteExtractionRule(id: Long) {
        dao.deleteExtractionRule(id)
    }

    suspend fun setShieldsEnabled(enabled: Boolean) =
        preferencesRepository.setShieldsEnabled(enabled)

    suspend fun setHttpsUpgradeEnabled(enabled: Boolean) =
        preferencesRepository.setHttpsUpgradeEnabled(enabled)

    suspend fun setAggressivePreloadEnabled(enabled: Boolean) =
        preferencesRepository.setAggressivePreloadEnabled(enabled)

    suspend fun setScriptGuardEnabled(enabled: Boolean) =
        preferencesRepository.setScriptGuardEnabled(enabled)

    suspend fun setDesktopViewportEnabled(enabled: Boolean) =
        preferencesRepository.setDesktopViewportEnabled(enabled)

    suspend fun setSearchEngine(engine: SearchEngineOption) =
        preferencesRepository.setSearchEngine(engine)

    suspend fun setDynamicColorEnabled(enabled: Boolean) =
        preferencesRepository.setDynamicColorEnabled(enabled)

    suspend fun setSeedPaletteOrdinal(ordinal: Int) =
        preferencesRepository.setSeedPaletteOrdinal(ordinal)

    suspend fun setThemeMode(mode: ThemeModeOption) =
        preferencesRepository.setThemeMode(mode)

    suspend fun cycleSeedPalette() =
        preferencesRepository.cycleSeedPalette()

    suspend fun setShowTopVisitsOnHome(enabled: Boolean) {
        preferencesRepository.setShowTopVisitsOnHome(enabled)
    }

    suspend fun setDefaultZoomPercent(percent: Int) =
        preferencesRepository.setDefaultZoomPercent(percent)

    suspend fun setSiteZoom(hostOrUrl: String, percent: Int) =
        preferencesRepository.setSiteZoom(hostOrUrl, percent)

    suspend fun removeSiteZoom(hostOrUrl: String) =
        preferencesRepository.removeSiteZoom(hostOrUrl)

    suspend fun clearSavedSiteZooms() =
        preferencesRepository.clearSavedSiteZooms()

    suspend fun setShowZoomInMainMenu(enabled: Boolean) =
        preferencesRepository.setShowZoomInMainMenu(enabled)

    suspend fun setForceEnableZoom(enabled: Boolean) =
        preferencesRepository.setForceEnableZoom(enabled)

    suspend fun setSimplifiedViewEnabled(enabled: Boolean) =
        preferencesRepository.setSimplifiedViewEnabled(enabled)

    suspend fun setCaretBrowsingEnabled(enabled: Boolean) =
        preferencesRepository.setCaretBrowsingEnabled(enabled)

    suspend fun setTouchpadSwipeNavigationEnabled(enabled: Boolean) =
        preferencesRepository.setTouchpadSwipeNavigationEnabled(enabled)

    suspend fun setDownloadLocation(
        mode: DownloadLocationMode,
        customTreeUri: String = "",
        customFolderLabel: String = ""
    ) = preferencesRepository.setDownloadLocation(mode, customTreeUri, customFolderLabel)

    suspend fun setAskWhereToSaveFiles(enabled: Boolean) =
        preferencesRepository.setAskWhereToSaveFiles(enabled)

    suspend fun setAutoOpenDownloadedFiles(enabled: Boolean) =
        preferencesRepository.setAutoOpenDownloadedFiles(enabled)

    suspend fun setShowDownloadProgressNotifications(enabled: Boolean) =
        preferencesRepository.setShowDownloadProgressNotifications(enabled)

    suspend fun setParallelDownloadingEnabled(enabled: Boolean) =
        preferencesRepository.setParallelDownloadingEnabled(enabled)

    suspend fun activateProSubscription(
        rawKey: String,
        selectedPackage: com.example.data.preferences.ProSubscriptionPackage = com.example.data.preferences.ProSubscriptionPackage.MONTH,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean =
        preferencesRepository.activateProSubscription(
            rawKey = rawKey,
            selectedPackage = selectedPackage,
            nowMillis = nowMillis
        )

    suspend fun deactivateProSubscription() =
        preferencesRepository.deactivateProSubscription()

    suspend fun setHomeWallpaperMode(mode: com.example.data.preferences.HomeWallpaperMode) =
        preferencesRepository.setHomeWallpaperMode(mode)

    suspend fun setCustomHomeWallpaperPath(path: String) =
        preferencesRepository.setCustomHomeWallpaperPath(path)

    suspend fun setHomeWallpaperBlackAndWhite(enabled: Boolean) =
        preferencesRepository.setHomeWallpaperBlackAndWhite(enabled)

    suspend fun recordAutomationPlayUsage(
        todayKey: String = BrowserPreferences.currentLocalDayKey()
    ): Int = preferencesRepository.recordAutomationPlayUsage(todayKey)

    suspend fun getAllDownloadsOnce(): List<DownloadItemEntity> {
        return downloadsFlow.first()
    }

    suspend fun getDownloadById(id: Long): DownloadItemEntity? {
        return dao.getDownloadById(id)
    }

    suspend fun upsertDownload(item: DownloadItemEntity): Long {
        return dao.upsertDownload(item)
    }

    suspend fun deleteDownloadById(id: Long) {
        dao.deleteDownloadById(id)
    }

    suspend fun clearCompletedDownloads() {
        dao.clearCompletedDownloads()
    }

    suspend fun clearAllDownloads() {
        dao.clearAllDownloads()
    }

    suspend fun setPasswordAutofillEnabled(enabled: Boolean) {
        preferencesRepository.setPasswordAutofillEnabled(enabled)
    }

    fun decryptPassword(entity: SavedPasswordEntity): String {
        return passwordCrypto.decrypt(entity.encryptedPassword)
    }

    suspend fun saveOrUpdatePassword(
        id: Long = 0L,
        siteName: String,
        siteUrl: String,
        username: String,
        plainPassword: String,
        notes: String = "",
        category: String = "Personal",
        isFavorite: Boolean = false
    ): Long {
        val cleanUrl = PasswordVaultUtil.normalizeOriginUrl(siteUrl)
        val host = PasswordVaultUtil.extractHost(cleanUrl)
        val resolvedName = PasswordVaultUtil.deriveSiteName(cleanUrl, siteName)
        val cleanUser = username.trim()
        val now = System.currentTimeMillis()
        val strength = PasswordStrengthEvaluator.evaluate(plainPassword)
        val encrypted = passwordCrypto.encrypt(plainPassword)
        val fp = passwordCrypto.fingerprint(plainPassword)

        val existingById = if (id > 0L) dao.getSavedPasswordById(id) else null
        val existingByHostAndUser = if (existingById == null && host.isNotBlank()) {
            dao.getSavedPasswordsByHost(host).firstOrNull {
                it.username.equals(cleanUser, ignoreCase = true)
            }
        } else {
            null
        }
        val existing = existingById ?: existingByHostAndUser

        val entity = if (existing != null) {
            existing.copy(
                siteName = resolvedName,
                siteUrl = cleanUrl.ifBlank { existing.siteUrl },
                host = host.ifBlank { existing.host },
                username = cleanUser,
                encryptedPassword = encrypted,
                notes = notes.trim(),
                category = category.ifBlank { "Personal" },
                isFavorite = isFavorite,
                strengthScore = strength.score,
                passwordFingerprint = fp,
                updatedAt = now
            )
        } else {
            SavedPasswordEntity(
                siteName = resolvedName,
                siteUrl = cleanUrl.ifBlank { "https://$host" },
                host = host,
                username = cleanUser,
                encryptedPassword = encrypted,
                notes = notes.trim(),
                category = category.ifBlank { "Personal" },
                isFavorite = isFavorite,
                strengthScore = strength.score,
                passwordFingerprint = fp,
                createdAt = now,
                updatedAt = now
            )
        }
        return dao.upsertSavedPassword(entity)
    }

    suspend fun togglePasswordFavorite(id: Long) {
        val existing = dao.getSavedPasswordById(id) ?: return
        dao.upsertSavedPassword(
            existing.copy(
                isFavorite = !existing.isFavorite,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun markPasswordUsed(id: Long) {
        val existing = dao.getSavedPasswordById(id) ?: return
        dao.upsertSavedPassword(existing.copy(lastUsedAt = System.currentTimeMillis()))
    }

    suspend fun deleteSavedPassword(id: Long) {
        dao.deleteSavedPasswordById(id)
    }

    suspend fun clearAllSavedPasswords() {
        dao.clearAllSavedPasswords()
    }

    suspend fun getPasswordsForUrl(url: String): List<SavedPasswordEntity> {
        val pageHost = PasswordVaultUtil.extractHost(url)
        if (pageHost.isBlank()) return emptyList()
        return dao.getSavedPasswordsOnce().filter {
            PasswordVaultUtil.hostMatches(it.host, pageHost)
        }
    }

    suspend fun exportPasswordsToCsv(): String {
        val all = dao.getSavedPasswordsOnce()
        return PasswordVaultUtil.exportCsv(all) { decryptPassword(it) }
    }

    suspend fun importPasswordsFromCsv(rawCsv: String): Int {
        val parsed = PasswordVaultUtil.parseCsv(rawCsv)
        if (parsed.isEmpty()) return 0
        var imported = 0
        for (cred in parsed) {
            saveOrUpdatePassword(
                siteName = cred.siteName,
                siteUrl = cred.siteUrl,
                username = cred.username,
                plainPassword = cred.password,
                notes = cred.notes
            )
            imported++
        }
        return imported
    }

    // --- Unified Automation Workflows & Executions ---

    suspend fun getAutomationWorkflowById(id: Long): AutomationWorkflowEntity? {
        return dao.getAutomationWorkflowById(id)
    }

    suspend fun getAutomationWorkflowDocument(id: Long): WorkflowDocument? {
        val wf = dao.getAutomationWorkflowById(id) ?: return null
        return AutomationStepCodec.decodeDocument(wf.stepsJson)
    }

    suspend fun saveAutomationWorkflow(
        id: Long = 0L,
        name: String,
        domain: String,
        sourceUrl: String,
        type: AutomationType,
        steps: List<AutomationStep>,
        instructionsText: String = "",
        metadata: WorkflowDocumentMetadata? = null,
        expectedVersion: Int? = null,
        changeSummary: String = ""
    ): Long {
        val existing = if (id > 0L) dao.getAutomationWorkflowById(id) else null
        val resolvedMetadata = metadata
            ?: existing?.let { AutomationStepCodec.decodeDocument(it.stepsJson).metadata }
            ?: WorkflowDocumentMetadata()
        val cleanName = name.trim().ifBlank { "Untitled Automation" }
        val cleanDomain = domain.trim().ifBlank { "All sites" }
        return if (existing != null) {
            val res = workflowVersionManager.updateExistingWorkflowVersioned(
                workflowId = existing.id,
                expectedVersion = expectedVersion,
                name = cleanName,
                domain = cleanDomain,
                sourceUrl = sourceUrl.ifBlank { existing.sourceUrl },
                type = type,
                instructionsText = instructionsText.ifBlank { existing.instructionsText },
                document = WorkflowDocument(steps = steps, metadata = resolvedMetadata),
                changeSummary = changeSummary
            )
            res.workflowId
        } else {
            workflowVersionManager.createNewWorkflowWithVersion(
                name = cleanName,
                domain = cleanDomain,
                sourceUrl = sourceUrl,
                type = type,
                steps = steps,
                instructionsText = instructionsText,
                metadata = resolvedMetadata,
                changeSummary = changeSummary.ifBlank { "Initial version" }
            )
        }
    }

    suspend fun saveAutomationWorkflowDocument(
        id: Long,
        document: WorkflowDocument,
        expectedVersion: Int? = null,
        changeSummary: String = ""
    ): Long {
        val existing = dao.getAutomationWorkflowById(id) ?: return 0L
        return workflowVersionManager.updateExistingWorkflowVersioned(
            workflowId = existing.id,
            expectedVersion = expectedVersion,
            document = document,
            changeSummary = changeSummary
        ).workflowId
    }

    suspend fun renameAutomationWorkflow(id: Long, newName: String, expectedVersion: Int? = null) {
        val clean = newName.trim()
        if (clean.isEmpty()) return
        dao.getAutomationWorkflowById(id) ?: return
        workflowVersionManager.updateExistingWorkflowVersioned(
            workflowId = id,
            expectedVersion = expectedVersion,
            name = clean,
            changeSummary = "Renamed to '$clean'"
        )
    }

    suspend fun updateAutomationWorkflowSteps(
        id: Long,
        steps: List<AutomationStep>,
        expectedVersion: Int? = null,
        changeSummary: String = ""
    ) {
        val existing = dao.getAutomationWorkflowById(id) ?: return
        val existingMeta = AutomationStepCodec.decodeDocument(existing.stepsJson).metadata
        workflowVersionManager.updateExistingWorkflowVersioned(
            workflowId = id,
            expectedVersion = expectedVersion,
            document = WorkflowDocument(steps = steps, metadata = existingMeta),
            changeSummary = changeSummary
        )
    }

    suspend fun duplicateAutomationWorkflow(id: Long): Long? {
        val existing = dao.getAutomationWorkflowById(id) ?: return null
        val doc = AutomationStepCodec.decodeDocument(existing.stepsJson)
        val org = WorkflowOrganizationMetadata(
            folder = existing.folder,
            category = existing.category,
            tags = WorkflowOrganizationMetadata.decodeTagsJson(existing.tagsJson),
            isFavorite = existing.isFavorite,
            isArchived = false,
            archivedAt = null
        )
        return workflowVersionManager.createNewWorkflowWithVersion(
            name = "${existing.name} (Copy)",
            domain = existing.domain,
            sourceUrl = existing.sourceUrl,
            type = AutomationType.fromId(existing.type),
            steps = doc.steps,
            instructionsText = existing.instructionsText,
            metadata = doc.metadata,
            organization = org,
            workflowUuid = UUID.randomUUID().toString(),
            changeSummary = "Duplicated from '${existing.name}' (v${existing.currentVersion})"
        )
    }

    suspend fun deleteAutomationWorkflow(id: Long) {
        dao.deleteAutomationWorkflowWithExecutions(id)
    }

    // --- Phase 8: Lifecycle, Versioning, Validation, Dry-Run, Organization & Portability ---

    fun observeWorkflowVersions(workflowId: Long): Flow<List<AutomationWorkflowVersionEntity>> =
        workflowVersionManager.observeVersions(workflowId)

    suspend fun getWorkflowVersionsOnce(workflowId: Long): List<AutomationWorkflowVersionEntity> {
        workflowVersionManager.ensureInitialVersion(workflowId)
        return workflowVersionManager.getVersionsOnce(workflowId)
    }

    suspend fun updateWorkflowWithVersionCheck(
        workflowId: Long,
        expectedVersion: Int,
        name: String? = null,
        domain: String? = null,
        sourceUrl: String? = null,
        type: AutomationType? = null,
        instructionsText: String? = null,
        document: WorkflowDocument? = null,
        changeSummary: String = ""
    ): WorkflowVersionResult = workflowVersionManager.updateExistingWorkflowVersioned(
        workflowId = workflowId,
        expectedVersion = expectedVersion,
        name = name,
        domain = domain,
        sourceUrl = sourceUrl,
        type = type,
        instructionsText = instructionsText,
        document = document,
        changeSummary = changeSummary
    )

    suspend fun restoreWorkflowVersion(
        workflowId: Long,
        targetVersionNumber: Int,
        expectedCurrentVersion: Int? = null
    ): WorkflowVersionResult = workflowVersionManager.restoreVersion(
        workflowId = workflowId,
        targetVersionNumber = targetVersionNumber,
        expectedCurrentVersion = expectedCurrentVersion
    )

    suspend fun compareWorkflowVersions(
        workflowId: Long,
        baseVersionNumber: Int,
        targetVersionNumber: Int
    ): WorkflowDiffReport? {
        workflowVersionManager.ensureInitialVersion(workflowId)
        val base = workflowVersionManager.getVersionSnapshot(workflowId, baseVersionNumber) ?: return null
        val target = workflowVersionManager.getVersionSnapshot(workflowId, targetVersionNumber) ?: return null
        return WorkflowDiffEngine.compareVersions(base, target)
    }

    suspend fun updateWorkflowOrganization(
        workflowId: Long,
        organization: WorkflowOrganizationMetadata
    ) {
        dao.updateWorkflowOrganization(
            id = workflowId,
            folder = organization.folder.trim(),
            category = organization.category.trim(),
            tagsJson = WorkflowOrganizationMetadata.encodeTagsJson(organization.tags),
            isFavorite = organization.isFavorite,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun toggleWorkflowFavorite(workflowId: Long) {
        val existing = dao.getAutomationWorkflowById(workflowId) ?: return
        dao.updateWorkflowOrganization(
            id = workflowId,
            folder = existing.folder,
            category = existing.category,
            tagsJson = existing.tagsJson,
            isFavorite = !existing.isFavorite,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun setWorkflowArchived(workflowId: Long, archived: Boolean) {
        val now = System.currentTimeMillis()
        dao.setWorkflowArchived(
            id = workflowId,
            isArchived = archived,
            archivedAt = if (archived) now else null,
            updatedAt = now
        )
    }

    suspend fun validateWorkflowDetailed(workflowId: Long): WorkflowValidationReport? {
        val wf = dao.getAutomationWorkflowById(workflowId) ?: return null
        val doc = AutomationStepCodec.decodeDocument(wf.stepsJson)
        return WorkflowValidationEngine.validateWorkflow(
            workflow = wf,
            document = doc,
            workflowLookup = { targetId ->
                dao.getAutomationWorkflowById(targetId)?.let { child ->
                    child.name to AutomationStepCodec.decodeDocument(child.stepsJson)
                }
            },
            credentialLookup = { credId -> dao.getCredentialVaultEntryById(credId) },
            engine = validationEngine
        )
    }

    suspend fun validateWorkflowDocumentDetailed(
        workflow: AutomationWorkflowEntity?,
        document: WorkflowDocument
    ): WorkflowValidationReport {
        return WorkflowValidationEngine.validateWorkflow(
            workflow = workflow,
            document = document,
            workflowLookup = { targetId ->
                dao.getAutomationWorkflowById(targetId)?.let { child ->
                    child.name to AutomationStepCodec.decodeDocument(child.stepsJson)
                }
            },
            credentialLookup = { credId -> dao.getCredentialVaultEntryById(credId) },
            engine = validationEngine
        )
    }

    suspend fun runWorkflowDryRun(
        workflowId: Long,
        runtimeInputs: Map<String, String> = emptyMap(),
        currentPageUrl: String = "",
        passiveDomSnapshot: List<DetectedPageElement> = emptyList()
    ): WorkflowDryRunReport? {
        val wf = dao.getAutomationWorkflowById(workflowId) ?: return null
        val doc = AutomationStepCodec.decodeDocument(wf.stepsJson)
        return WorkflowDryRunEngine.simulate(
            workflow = wf,
            document = doc,
            workflowLoader = { targetId ->
                dao.getAutomationWorkflowById(targetId)?.let { child ->
                    child to AutomationStepCodec.decodeDocument(child.stepsJson)
                }
            },
            credentialLookup = { credId -> dao.getCredentialVaultEntryById(credId) },
            runtimeInputs = runtimeInputs,
            currentPageUrl = currentPageUrl.ifBlank { wf.sourceUrl },
            passiveDomSnapshot = passiveDomSnapshot,
            engine = validationEngine
        )
    }

    suspend fun exportWorkflowPackageJson(workflowId: Long, includeDependencies: Boolean): String =
        workflowImportExportManager.exportWorkflowPackageJson(workflowId, includeDependencies)

    suspend fun exportAllWorkflowsPackageJson(includeDependencies: Boolean = true): String =
        workflowImportExportManager.exportCollectionPackageJson(includeDependencies = includeDependencies)

    suspend fun exportWorkflowsZipBytes(
        workflowIds: List<Long>? = null,
        packageType: ExportPackageType = ExportPackageType.COLLECTION,
        includeDependencies: Boolean = true
    ): ByteArray = workflowImportExportManager.exportWorkflowArchiveBytes(
        workflowIds = workflowIds,
        packageType = packageType,
        includeDependencies = includeDependencies
    )

    suspend fun importWorkflowPackageJson(
        rawJson: String,
        conflictResolution: WorkflowConflictResolution = WorkflowConflictResolution.CREATE_COPY
    ): WorkflowImportResult = workflowImportExportManager.importFromJson(rawJson, conflictResolution)

    suspend fun importWorkflowsZipBytes(
        zipBytes: ByteArray,
        conflictResolution: WorkflowConflictResolution = WorkflowConflictResolution.CREATE_COPY
    ): WorkflowImportResult = workflowImportExportManager.importFromZipBytes(zipBytes, conflictResolution)

    suspend fun recordAutomationExecutionStart(
        workflow: AutomationWorkflowEntity,
        totalSteps: Int
    ): Long {
        val now = System.currentTimeMillis()
        return dao.startAutomationExecutionRecord(
            workflowId = workflow.id,
            workflowName = workflow.name,
            domain = workflow.domain,
            totalSteps = totalSteps,
            startedAt = now
        )
    }

    suspend fun finishAutomationExecution(
        workflowId: Long,
        executionId: Long,
        status: String,
        completedSteps: Int,
        totalSteps: Int,
        verificationSummary: String,
        checkpointJson: String = "",
        traceJson: String = ""
    ) {
        val finishedAt = System.currentTimeMillis()
        dao.finishAutomationExecutionRecord(
            workflowId = workflowId,
            executionId = executionId,
            status = status,
            completedSteps = completedSteps,
            totalSteps = totalSteps,
            verificationSummary = verificationSummary,
            finishedAt = finishedAt,
            checkpointJson = checkpointJson,
            traceJson = traceJson
        )
    }

    suspend fun interruptAutomationExecution(
        workflowId: Long,
        executionId: Long,
        completedSteps: Int,
        totalSteps: Int,
        verificationSummary: String,
        checkpointJson: String,
        traceJson: String = ""
    ) {
        dao.interruptAutomationExecutionRecord(
            workflowId = workflowId,
            executionId = executionId,
            completedSteps = completedSteps,
            totalSteps = totalSteps,
            verificationSummary = verificationSummary,
            interruptedAt = System.currentTimeMillis(),
            checkpointJson = checkpointJson,
            traceJson = traceJson
        )
    }

    suspend fun updateExecutionCheckpointSnapshot(
        executionId: Long,
        status: String,
        completedSteps: Int,
        totalSteps: Int,
        verificationSummary: String,
        checkpointJson: String,
        traceJson: String = ""
    ) {
        dao.updateAutomationExecutionCheckpointSnapshot(
            executionId = executionId,
            status = status,
            completedSteps = completedSteps,
            totalSteps = totalSteps,
            verificationSummary = verificationSummary,
            checkpointJson = checkpointJson,
            traceJson = traceJson
        )
    }

    private fun stripCredentialsRecursive(step: AutomationStep): AutomationStep {
        val stripped = if (step.isCredentialStep) step.copy(value = "") else step
        return stripped.copy(
            thenSteps = stripped.thenSteps.map { stripCredentialsRecursive(it) },
            elseIfBranches = stripped.elseIfBranches.map { b -> b.copy(steps = b.steps.map { stripCredentialsRecursive(it) }) },
            elseSteps = stripped.elseSteps.map { stripCredentialsRecursive(it) },
            recoveryPolicy = stripped.recoveryPolicy?.let { pol ->
                pol.copy(alternativeSteps = pol.alternativeSteps.map { stripCredentialsRecursive(it) })
            }
        )
    }

    private suspend fun sanitizeImportedStepsRecursive(
        rawSteps: List<AutomationStep>,
        workflowOriginCanon: String
    ): List<AutomationStep>? {
        val out = mutableListOf<AutomationStep>()
        for (step in rawSteps) {
            if (step.selector.isNotBlank() && !validationEngine.validateSelector(step.selector).isValid) {
                return null
            }
            if (step.expectedOrigin.isNotBlank() && !validationEngine.parseOrigin(step.expectedOrigin).isValid) {
                return null
            }
            val cleanThen = sanitizeImportedStepsRecursive(step.thenSteps, workflowOriginCanon) ?: return null
            val cleanElse = sanitizeImportedStepsRecursive(step.elseSteps, workflowOriginCanon) ?: return null
            val cleanElifs = mutableListOf<com.example.automation.ElseIfBranch>()
            for (b in step.elseIfBranches) {
                val bSteps = sanitizeImportedStepsRecursive(b.steps, workflowOriginCanon) ?: return null
                cleanElifs.add(b.copy(steps = bSteps))
            }
            val cleanPolicy = step.recoveryPolicy?.let { pol ->
                val alt = sanitizeImportedStepsRecursive(pol.alternativeSteps, workflowOriginCanon) ?: return null
                pol.copy(alternativeSteps = alt)
            }
            val updated = step.copy(
                thenSteps = cleanThen,
                elseIfBranches = cleanElifs,
                elseSteps = cleanElse,
                recoveryPolicy = cleanPolicy
            )
            if (updated.isCredentialStep) {
                val refId = updated.credentialReferenceId?.trim()?.takeIf { it.isNotEmpty() }
                val localEntry = if (refId != null) dao.getCredentialVaultEntryById(refId) else null
                val stepExpectedCanonical = if (updated.expectedOrigin.isNotBlank()) {
                    validationEngine.parseOrigin(updated.expectedOrigin).canonicalOrigin
                } else {
                    workflowOriginCanon
                }
                val validLocalBinding = localEntry != null &&
                    (stepExpectedCanonical.isEmpty() || localEntry.websiteOrigin == stepExpectedCanonical)
                out.add(
                    updated.copy(
                        value = "",
                        credentialReferenceId = if (validLocalBinding) localEntry?.credentialId else null,
                        expectedOrigin = stepExpectedCanonical.ifBlank { localEntry?.websiteOrigin.orEmpty() },
                        requiresCredential = true
                    )
                )
            } else {
                out.add(updated)
            }
        }
        return out
    }

    suspend fun exportAutomationsAsJson(): String {
        val workflows = dao.getAutomationWorkflowsOnce()
        val array = JSONArray()
        for (wf in workflows) {
            val doc = AutomationStepCodec.decodeDocument(wf.stepsJson)
            val steps = doc.steps.map { stripCredentialsRecursive(it) }
            val obj = JSONObject()
            obj.put("schemaVersion", AutomationStepCodec.CURRENT_SCHEMA_VERSION)
            obj.put("name", wf.name)
            obj.put("domain", wf.domain)
            obj.put("sourceUrl", wf.sourceUrl)
            obj.put("type", wf.type)
            obj.put("instructionsText", wf.instructionsText)
            obj.put("steps", AutomationStepCodec.encodeStepsArray(steps))
            if (!doc.metadata.isEmptyDefault) {
                obj.put("phase6", doc.metadata.toJson())
            }
            obj.put("createdAt", wf.createdAt)
            obj.put("updatedAt", wf.updatedAt)
            array.put(obj)
        }
        return array.toString(2)
    }

    suspend fun importAutomationsFromJson(rawJson: String): Int {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) return 0
        var imported = 0
        runCatching {
            val array = JSONArray(trimmed)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val name = obj.optString("name").trim()
                if (name.isEmpty()) continue
                if (obj.has("schemaVersion")) {
                    val schemaVersion = (obj.opt("schemaVersion") as? Number)?.toInt() ?: -1
                    if (schemaVersion < 1 || schemaVersion > AutomationStepCodec.CURRENT_SCHEMA_VERSION) {
                        continue
                    }
                }
                val domain = obj.optString("domain", "All sites")
                val sourceUrl = obj.optString("sourceUrl", "")
                val workflowOriginResult = if (sourceUrl.isNotBlank() && !sourceUrl.equals("apex://home", ignoreCase = true)) {
                    validationEngine.parseOrigin(sourceUrl)
                } else {
                    null
                }
                if (workflowOriginResult != null && !workflowOriginResult.isValid) {
                    continue
                }
                val type = AutomationType.fromId(obj.optString("type"))
                val instructionsText = obj.optString("instructionsText", "")
                val rawStepsValue = obj.opt("steps")
                val decodeResult = when (rawStepsValue) {
                    is JSONObject -> AutomationStepCodec.decodeSafely(rawStepsValue.toString())
                    is JSONArray -> AutomationStepCodec.decodeSafely(rawStepsValue.toString())
                    is String -> AutomationStepCodec.decodeSafely(rawStepsValue)
                    else -> AutomationStepDecodeResult.Success(
                        schemaVersion = AutomationStepCodec.CURRENT_SCHEMA_VERSION,
                        steps = emptyList(),
                        isLegacyUnversioned = true
                    )
                }
                val rawSteps = when (decodeResult) {
                    is AutomationStepDecodeResult.Success -> decodeResult.steps
                    is AutomationStepDecodeResult.UnsupportedVersion,
                    is AutomationStepDecodeResult.MalformedJson -> continue
                }
                val parsedMeta = obj.optJSONObject("phase6")?.let {
                    WorkflowDocumentMetadata.fromJson(it)
                } ?: when (decodeResult) {
                    is AutomationStepDecodeResult.Success -> decodeResult.metadata
                    else -> WorkflowDocumentMetadata()
                }
                val sanitizedSteps = sanitizeImportedStepsRecursive(
                    rawSteps = rawSteps,
                    workflowOriginCanon = (workflowOriginResult as? com.example.automation.RecordingOriginResult.Valid)?.canonicalOrigin.orEmpty()
                ) ?: continue

                saveAutomationWorkflow(
                    name = name,
                    domain = domain,
                    sourceUrl = sourceUrl,
                    type = type,
                    steps = sanitizedSteps,
                    instructionsText = instructionsText,
                    metadata = parsedMeta
                )
                imported++
            }
        }
        return imported
    }
}
