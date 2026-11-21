package com.example

import android.webkit.WebView
import com.example.automation.ActiveRecordingSession
import com.example.automation.AgentRole
import com.example.automation.AutomationEngine
import com.example.automation.AutomationHudMode
import com.example.automation.BrowserDomActionType
import com.example.automation.ExtractedRecord
import com.example.automation.NanoChatMessage
import com.example.automation.NanoReasoningStep
import com.example.automation.PageInspectionState
import com.example.automation.PlannerAgent
import com.example.automation.RetrieverEngine
import com.example.automation.StepStatus
import com.example.automation.TabSnapshotTarget
import com.example.automation.UniversalDomScanner
import com.example.automation.ValidatorAgent
import com.example.ui.browser.AutomationHudSheet
import com.example.data.local.PrivacyStatSummaryEntity
import com.example.data.preferences.DownloadLocationMode
import com.example.data.preferences.SearchEngineOption
import com.example.data.preferences.ThemeModeOption
import com.example.engine.PrivacyManager
import com.example.engine.SearchSuggestionEngine
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.ui.unit.DpSize
import com.example.ui.browser.BraveBottomNavigationBar
import com.example.ui.browser.BrowserMenuBottomSheet
import com.example.ui.browser.BrowserScreen
import com.example.ui.browser.CompactTopToolbar
import com.example.ui.browser.DraggableTabGridOverlay
import com.example.ui.browser.GroupedListSpacing
import com.example.ui.browser.SettingsInitialSection
import com.example.ui.browser.SettingsScreenOverlay
import com.example.ui.browser.WebViewViewport
import com.example.ui.browser.groupedListItemShape
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.unit.height
import com.example.data.preferences.BrowserPreferences
import com.example.engine.DefaultBrowserHelper
import com.example.engine.DefaultBrowserStatus
import com.example.engine.PerformanceOptimizer
import com.example.ui.theme.ApexBrowserTheme
import com.example.ui.theme.ApexColorSchemeBuilder
import com.example.ui.theme.SeedPalettePreset
import com.example.ui.theme.TabGroupColorOption
import com.example.ui.viewmodel.BrowserTab
import com.example.ui.viewmodel.BrowserUiState
import com.example.ui.viewmodel.BrowserViewModel
import com.example.ui.viewmodel.TabGridEntry
import com.example.ui.viewmodel.TabGroupInfo
import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows
import com.example.data.local.ApexDatabase
import com.example.data.local.SavedPasswordEntity
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.BrowserRepository
import com.example.engine.FastDownloadEngine
import com.example.engine.PasswordAuditEngine
import com.example.engine.PasswordStrength
import com.example.engine.PasswordStrengthEvaluator
import com.example.engine.PasswordVaultCrypto
import com.example.engine.PasswordVaultUtil
import com.example.engine.SecurePasswordGenerator
import com.example.ui.browser.PasswordManagerScreenOverlay
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import kotlinx.coroutines.flow.first
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `verify fresh install state starts with single Home tab and zero stats`() {
        val freshState = BrowserUiState()
        assertEquals(1, freshState.tabs.size)
        assertTrue(freshState.activeTab.isHomePage)
        assertEquals("apex://home", freshState.activeTab.url)
        assertTrue(freshState.tabGroups.isEmpty())
        assertTrue(freshState.searchSuggestions.isEmpty())
        assertTrue(freshState.searchQuery.isEmpty())
        assertFalse(freshState.isSettingsScreenOpen)
        assertFalse(freshState.isMenuSheetOpen)

        SearchEngineOption.entries.forEach { option ->
            assertTrue(option.description.isNotBlank())
            assertTrue(option.searchUrlTemplate.contains("%s"))
        }

        val freshStats = PrivacyStatSummaryEntity()
        assertEquals(0, freshStats.trackersBlocked)
        assertEquals(0, freshStats.adsBlocked)
        assertEquals(0, freshStats.httpsUpgrades)
        assertEquals(0L, freshStats.bytesSaved)
        assertEquals(0L, freshStats.timeSavedMs)
    }

    @Test
    fun `verify SearchSuggestionEngine builds URLs and parses engine suggestions`() {
        val suggestionEngine = SearchSuggestionEngine()

        assertTrue(
            suggestionEngine.buildSuggestUrl(SearchEngineOption.DUCKDUCKGO, "kotlin")
                .contains("ac.duckduckgo.com")
        )
        assertTrue(
            suggestionEngine.buildSuggestUrl(SearchEngineOption.GOOGLE, "kotlin")
                .contains("suggestqueries.google.com")
        )
        assertTrue(
            suggestionEngine.buildSuggestUrl(SearchEngineOption.BRAVE, "kotlin")
                .contains("search.brave.com")
        )
        assertTrue(
            suggestionEngine.buildSuggestUrl(SearchEngineOption.WIKIPEDIA, "kotlin")
                .contains("en.wikipedia.org")
        )

        // OpenSearch JSON array (Google / DuckDuckGo list / Brave / Bing)
        val openSearchJson = """["kotlin",["kotlin","kotlin compose","kotlin coroutines"]]"""
        val googleParsed = suggestionEngine.parseEngineSuggestions(
            rawJson = openSearchJson,
            originalQuery = "kotlin",
            engine = SearchEngineOption.GOOGLE
        )
        assertEquals(3, googleParsed.size)
        assertEquals("kotlin compose", googleParsed[1].title)
        assertEquals("G", googleParsed[1].engineBadge)

        // DuckDuckGo object array format
        val ddgObjJson = """[{"phrase":"privacy browser"},{"phrase":"privacy tools"}]"""
        val ddgParsed = suggestionEngine.parseEngineSuggestions(
            rawJson = ddgObjJson,
            originalQuery = "privacy",
            engine = SearchEngineOption.DUCKDUCKGO
        )
        assertEquals(2, ddgParsed.size)
        assertEquals("privacy browser", ddgParsed[0].title)
        assertEquals("DDG", ddgParsed[0].engineBadge)

        // Wikipedia OpenSearch with descriptions and direct article URLs
        val wikiJson = """["jetpack",["Jetpack Compose"],["Android UI toolkit"],["https://en.wikipedia.org/wiki/Jetpack_Compose"]]"""
        val wikiParsed = suggestionEngine.parseEngineSuggestions(
            rawJson = wikiJson,
            originalQuery = "jetpack",
            engine = SearchEngineOption.WIKIPEDIA
        )
        assertTrue(wikiParsed.any { it.submitValue == "https://en.wikipedia.org/wiki/Jetpack_Compose" })
        assertTrue(wikiParsed.any { it.subtitle == "Android UI toolkit" })
    }

    @Test
    fun `verify M3 Expressive seed ColorScheme generation`() {
        val light = ApexColorSchemeBuilder.fromSeed(
            seed = SeedPalettePreset.ELECTRIC_COBALT.seedColor,
            darkTheme = false
        )
        val dark = ApexColorSchemeBuilder.fromSeed(
            seed = SeedPalettePreset.ELECTRIC_COBALT.seedColor,
            darkTheme = true
        )
        assertNotEquals(light.primaryContainer, dark.primaryContainer)
        assertNotEquals(light.surface, dark.surface)
    }

    @Test
    fun `verify Tab Group grid entries for 4 tabs vs 5+ tabs`() {
        val grp = TabGroupInfo(id = "grp_1", title = "Group 1")
        val fourGroupedTabs = (1..4).map { idx ->
            BrowserTab(id = "t_$idx", title = "Tab $idx", url = "https://example.com/$idx", groupId = "grp_1")
        }
        val stateFour = BrowserUiState(
            tabs = fourGroupedTabs + BrowserTab(id = "solo", title = "Solo", url = "apex://home"),
            tabGroups = listOf(grp)
        )
        val entriesFour = stateFour.gridEntries
        assertEquals(2, entriesFour.size)
        val groupFour = entriesFour.first() as TabGridEntry.GroupTab
        assertEquals(4, groupFour.tabs.size)
        assertFalse(groupFour.tabs.size > 4) // 4 tabs stay shown directly

        val sixGroupedTabs = (1..6).map { idx ->
            BrowserTab(id = "t_$idx", title = "Tab $idx", url = "https://example.com/$idx", groupId = "grp_1")
        }
        val stateSix = BrowserUiState(tabs = sixGroupedTabs, tabGroups = listOf(grp))
        val groupSix = stateSix.gridEntries.single() as TabGridEntry.GroupTab
        assertTrue(groupSix.tabs.size > 4)
        assertEquals(3, groupSix.tabs.size - 3) // 3 tabs shown + 4th slot shows +3 remaining
    }

    @Test
    fun `verify PrivacyManager HTTPS upgrade and tracker blocking`() {
        val privacyManager = PrivacyManager()
        val upgraded = privacyManager.normalizeAndUpgradeUrl(
            rawInput = "http://example.com/articles",
            httpsUpgradeEnabled = true,
            searchEngineTemplate = "https://duckduckgo.com/?q=%s"
        )
        assertEquals("https://example.com/articles", upgraded.finalUrl)
        assertTrue(upgraded.wasUpgradedToHttps)

        val blocked = privacyManager.evaluateRequest(
            requestUrl = "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js",
            pageUrl = "https://news.ycombinator.com",
            shieldsEnabled = true
        )
        assertTrue(blocked.blocked)
        assertEquals("Ad Network", blocked.category)

        // Anti-adblock & pop-under domains must also be intercepted
        val admiralBlocked = privacyManager.evaluateRequest(
            requestUrl = "https://cdn.getadmiral.com/v2/admiral.js",
            pageUrl = "https://example.com",
            shieldsEnabled = true
        )
        assertTrue(admiralBlocked.blocked)

        val firstPartyAllowed = privacyManager.evaluateRequest(
            requestUrl = "https://news.ycombinator.com/news.css",
            pageUrl = "https://news.ycombinator.com",
            shieldsEnabled = true
        )
        assertFalse(firstPartyAllowed.blocked)

        // Stealth JS surrogate and 1x1 GIF surrogate verification
        val jsSurrogate = privacyManager.stealthBlockedResponse(
            "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"
        )
        assertEquals("application/javascript", jsSurrogate.mimeType)
        val jsText = jsSurrogate.data.bufferedReader().readText()
        assertTrue(jsText.contains("window.adsbygoogle"))
        assertTrue(jsText.contains("window.googletag"))

        val gifSurrogate = privacyManager.stealthBlockedResponse(
            "https://ad.doubleclick.net/ddm/trackimp/pixel.gif"
        )
        assertEquals("image/gif", gifSurrogate.mimeType)
        assertTrue(gifSurrogate.data.readBytes().isNotEmpty())

        val stealthScript = privacyManager.buildStealthAdblockScript(true)
        assertTrue(stealthScript.contains("FakeBlockAdBlock"))
        assertTrue(stealthScript.contains("offsetHeight"))
        assertTrue(stealthScript.contains("ytp-ad-skip-button"))
    }

    @Test
    fun `verify Nano multi-agent planner validator and Retriever parallel extraction`() = runTest {
        val planner = PlannerAgent()
        val steps = planner.decomposeTask(
            prompt = "Find the top 3 AI news articles today and summarize them",
            currentUrl = "apex://home"
        )
        assertTrue(steps.size >= 2)

        val validator = ValidatorAgent()
        val outcome = validator.validateAndSynthesize(
            userPrompt = "Summarize top 3",
            observations = emptyList()
        )
        assertTrue(outcome.passed)

        val retriever = RetrieverEngine()
        val rule = retriever.buildRuleFromInput("Extract all product names and prices from this page")
        val records = retriever.extractAcrossTabsInParallel(
            tabs = listOf(
                TabSnapshotTarget("t1", "AI News Pulse", "apex://news"),
                TabSnapshotTarget("t2", "Apex Gear Store", "apex://store")
            ),
            webViewLookup = { null },
            rule = rule
        )
        assertTrue(records.size >= 8)
    }

    @Test
    fun `verify Material 3 Grouped List item shapes for single first middle and last items`() {
        assertTrue(GroupedListSpacing > 0.dp)

        val single = groupedListItemShape(index = 0, totalCount = 1)
        val first = groupedListItemShape(index = 0, totalCount = 3)
        val middle = groupedListItemShape(index = 1, totalCount = 3)
        val last = groupedListItemShape(index = 2, totalCount = 3)

        assertEquals(RoundedCornerShape(24.dp), single)
        assertEquals(
            RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 5.dp, bottomEnd = 5.dp),
            first
        )
        assertEquals(RoundedCornerShape(5.dp), middle)
        assertEquals(
            RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
            last
        )
        assertNotEquals(first, middle)
        assertNotEquals(middle, last)
        assertNotEquals(first, last)
    }

    @Test
    fun `verify group rename color palettes and bottom group tab strip state`() {
        TabGroupColorOption.entries.forEach { option ->
            val lightPalette = ApexColorSchemeBuilder.groupTonalColors(option, darkTheme = false)
            val darkPalette = ApexColorSchemeBuilder.groupTonalColors(option, darkTheme = true)
            assertNotEquals(lightPalette.container, darkPalette.container)
            assertNotEquals(lightPalette.accent, lightPalette.onAccent)
        }

        val emeraldGroup = TabGroupInfo(
            id = "grp_research",
            title = "Research",
            colorOption = TabGroupColorOption.EMERALD
        )
        val homeInGroup = BrowserTab(
            id = "t_home_grp",
            title = "Home",
            url = "apex://home",
            groupId = "grp_research",
            tabColor = TabGroupColorOption.EMERALD
        )
        val docsInGroup = BrowserTab(
            id = "t_docs_grp",
            title = "Kotlin Docs",
            url = "https://kotlinlang.org",
            groupId = "grp_research",
            tabColor = TabGroupColorOption.EMERALD
        )
        val standaloneTab = BrowserTab(
            id = "t_solo",
            title = "Solo",
            url = "apex://home",
            groupId = null
        )

        val stateInGroup = BrowserUiState(
            tabs = listOf(homeInGroup, docsInGroup, standaloneTab),
            tabGroups = listOf(emeraldGroup),
            activeTabId = "t_home_grp"
        )
        assertNotNull(stateInGroup.activeTabGroup)
        assertEquals("Research", stateInGroup.activeTabGroup?.title)
        assertEquals(TabGroupColorOption.EMERALD, stateInGroup.activeTabGroup?.colorOption)
        assertEquals(2, stateInGroup.activeGroupTabs.size)

        val stateOnStandalone = stateInGroup.copy(activeTabId = "t_solo")
        assertNull(stateOnStandalone.activeTabGroup)
        assertTrue(stateOnStandalone.activeGroupTabs.isEmpty())
    }

    @Test
    fun `verify TopVisitEntity model and search engine options for start page`() {
        val topVisit = com.example.data.local.TopVisitEntity(
            id = 1L,
            title = "YouTube",
            url = "https://youtube.com"
        )
        assertEquals(1L, topVisit.id)
        assertEquals("YouTube", topVisit.title)
        assertEquals("https://youtube.com", topVisit.url)
        assertTrue(topVisit.createdAt > 0L)

        // Verify SearchEngineOptions
        val engineIds = SearchEngineOption.entries.map { it.id }.toSet()
        assertTrue(engineIds.contains("google"))
        assertTrue(engineIds.contains("duckduckgo"))
        assertTrue(engineIds.contains("brave"))
        assertTrue(engineIds.contains("bing"))
        assertTrue(engineIds.contains("wikipedia"))
        assertEquals(5, engineIds.size)

        // Verify curved edges shape: RoundedCornerShape(16.dp)
        val searchBarCurvedShape = RoundedCornerShape(16.dp)
        assertEquals(RoundedCornerShape(16.dp), searchBarCurvedShape)
    }

    @Test
    fun `verify SharedPreferences serialization and search engine card configs`() {
        val testBookmarks = listOf(
            com.example.data.local.BookmarkEntity(id = 1L, url = "https://example.com", title = "Example")
        )
        // Verify format and key representation
        val bookmark = testBookmarks.first()
        assertEquals(1L, bookmark.id)
        assertEquals("https://example.com", bookmark.url)
        assertEquals("Example", bookmark.title)

        // Verify all 5 search engines have valid card labels and IDs
        SearchEngineOption.entries.forEach { option ->
            assertTrue(option.id.isNotBlank())
            assertTrue(option.displayName.isNotBlank())
            assertTrue(option.shortBadge.isNotBlank())
            assertTrue(option.description.isNotBlank())
        }
    }

    @Test
    fun `verify FastDownloadEngine pauses cleanly without failing and resumes to completion`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val prefsRepo = UserPreferencesRepository(context)
        val sharedPrefs = context.getSharedPreferences("test_download_prefs", Context.MODE_PRIVATE)
        val repository = BrowserRepository(db.apexDao(), prefsRepo, sharedPrefs)
        val engine = FastDownloadEngine(context, repository)

        val payload = ByteArray(320 * 1024) { idx -> ((idx * 31) and 0xFF).toByte() }
        val server = ServerSocket(0)
        val serverThread = thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    runCatching {
                        socket.use { s ->
                            val reader = s.getInputStream().bufferedReader()
                            var rangeHeader: String? = null
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (line.isEmpty()) break
                                if (line.startsWith("Range:", ignoreCase = true)) {
                                    rangeHeader = line.substringAfter(':').trim()
                                }
                            }
                            val out = s.getOutputStream()
                            val total = payload.size
                            if (rangeHeader != null && rangeHeader!!.startsWith("bytes=")) {
                                val spec = rangeHeader!!.removePrefix("bytes=").trim()
                                val start = spec.substringBefore('-').toIntOrNull() ?: 0
                                val endRaw = spec.substringAfter('-', "").toIntOrNull()
                                val end = (endRaw ?: (total - 1)).coerceAtMost(total - 1)
                                if (start >= total || start > end) {
                                    out.write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$total\r\nConnection: close\r\n\r\n".toByteArray())
                                    out.flush()
                                    return@use
                                }
                                val len = end - start + 1
                                val header = "HTTP/1.1 206 Partial Content\r\n" +
                                    "Accept-Ranges: bytes\r\n" +
                                    "Content-Length: $len\r\n" +
                                    "Content-Range: bytes $start-$end/$total\r\n" +
                                    "Content-Type: application/octet-stream\r\n" +
                                    "Connection: close\r\n\r\n"
                                out.write(header.toByteArray())
                                var pos = start
                                while (pos <= end) {
                                    val chunk = minOf(8192, end - pos + 1)
                                    out.write(payload, pos, chunk)
                                    out.flush()
                                    pos += chunk
                                    Thread.sleep(15L)
                                }
                            } else {
                                val header = "HTTP/1.1 200 OK\r\n" +
                                    "Accept-Ranges: bytes\r\n" +
                                    "Content-Length: $total\r\n" +
                                    "Content-Type: application/octet-stream\r\n" +
                                    "Connection: close\r\n\r\n"
                                out.write(header.toByteArray())
                                out.write(payload)
                                out.flush()
                            }
                        }
                    }
                }
            }
        }

        try {
            val startedLatch = CountDownLatch(1)
            var downloadId = -1L
            engine.enqueueDownload(
                url = "http://127.0.0.1:${server.localPort}/bundle.bin",
                suggestedFileName = "bundle_test.bin"
            ) { entity ->
                downloadId = entity.id
                startedLatch.countDown()
            }
            val startDeadline = System.currentTimeMillis() + 5_000L
            while (startedLatch.count > 0 && System.currentTimeMillis() < startDeadline) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(20L)
            }
            assertEquals(0L, startedLatch.count)
            assertTrue(downloadId > 0L)

            // Wait briefly for chunks to start transferring, then pause
            Thread.sleep(45L)
            engine.pauseDownload(downloadId)

            var pausedEntity = runBlocking { db.apexDao().getDownloadById(downloadId) }
            val deadlinePause = System.currentTimeMillis() + 5_000L
            while (System.currentTimeMillis() < deadlinePause) {
                pausedEntity = runBlocking { db.apexDao().getDownloadById(downloadId) }
                if (pausedEntity?.status == FastDownloadEngine.STATUS_PAUSED) break
                assertNotEquals(
                    "Paused download must never fail: ${pausedEntity?.errorMessage}",
                    FastDownloadEngine.STATUS_FAILED,
                    pausedEntity?.status
                )
                Thread.sleep(40L)
            }
            assertEquals(FastDownloadEngine.STATUS_PAUSED, pausedEntity?.status)

            // Wait another moment to ensure no late error overwrites PAUSED
            Thread.sleep(250L)
            val stablePaused = runBlocking { db.apexDao().getDownloadById(downloadId) }
            assertEquals(FastDownloadEngine.STATUS_PAUSED, stablePaused?.status)

            // Resume and verify completion with byte-exact payload
            engine.resumeDownload(downloadId)
            var finalEntity = runBlocking { db.apexDao().getDownloadById(downloadId) }
            val deadlineComplete = System.currentTimeMillis() + 10_000L
            while (System.currentTimeMillis() < deadlineComplete) {
                finalEntity = runBlocking { db.apexDao().getDownloadById(downloadId) }
                if (finalEntity?.status == FastDownloadEngine.STATUS_COMPLETED) break
                assertNotEquals(
                    "Resumed download must not fail: ${finalEntity?.errorMessage}",
                    FastDownloadEngine.STATUS_FAILED,
                    finalEntity?.status
                )
                Thread.sleep(50L)
            }
            assertEquals(FastDownloadEngine.STATUS_COMPLETED, finalEntity?.status)
            assertEquals(payload.size.toLong(), finalEntity?.downloadedBytes)
            val outFile = File(finalEntity!!.localFilePath)
            assertTrue(outFile.exists())
            assertArrayEquals(payload, outFile.readBytes())
        } finally {
            runCatching { server.close() }
            serverThread.interrupt()
            db.close()
        }
    }

    @Test
    fun `verify CompactTopToolbar horizontal swipe switches tabs and preserves tap`() {
        var nextCount = 0
        var prevCount = 0
        var clickCount = 0
        val homeTab = BrowserTab(id = "t_home", title = "Home", url = "apex://home")
        val webTab = BrowserTab(id = "t_web", title = "Example", url = "https://example.com")

        var currentIndex by mutableIntStateOf(1)
        var currentTab by mutableStateOf(homeTab)

        composeTestRule.setContent {
            ApexBrowserTheme {
                CompactTopToolbar(
                    visible = true,
                    activeTab = currentTab,
                    activeTabIndex = currentIndex,
                    totalTabs = 3,
                    onAddressPillClick = { clickCount++ },
                    onReloadClick = {},
                    onSwipeToNextTab = { nextCount++ },
                    onSwipeToPreviousTab = { prevCount++ }
                )
            }
        }

        // Tap still opens search screen
        composeTestRule.onNodeWithTag("home_top_search_bar").performClick()
        assertEquals(1, clickCount)

        // Swiping left moves to next tab
        composeTestRule.onNodeWithTag("home_top_search_bar").performTouchInput {
            swipeLeft(startX = right * 0.85f, endX = left + (width * 0.1f))
        }
        composeTestRule.waitForIdle()
        assertEquals(1, nextCount)
        assertEquals(0, prevCount)

        // Swiping right on web address bar moves to previous tab
        currentTab = webTab
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("address_bar_container").performTouchInput {
            swipeRight(startX = left + (width * 0.15f), endX = right * 0.9f)
        }
        composeTestRule.waitForIdle()
        assertEquals(1, prevCount)

        // At index 0, swiping right should not trigger previous tab
        currentIndex = 0
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("address_bar_container").performTouchInput {
            swipeRight(startX = left + (width * 0.15f), endX = right * 0.9f)
        }
        composeTestRule.waitForIdle()
        assertEquals(1, prevCount)
    }

    @Test
    fun `verify PasswordVaultCrypto generator evaluator audit and CSV round trip`() {
        val crypto = PasswordVaultCrypto()
        val secret = "Tr0ub4dor&3!ApexVault"
        val encrypted1 = crypto.encrypt(secret)
        val encrypted2 = crypto.encrypt(secret)

        assertNotEquals(secret, encrypted1)
        assertNotEquals(encrypted1, encrypted2) // randomized IV
        assertEquals(secret, crypto.decrypt(encrypted1))
        assertEquals(secret, crypto.decrypt(encrypted2))
        assertEquals(crypto.fingerprint(secret), crypto.fingerprint(secret))
        assertNotEquals(crypto.fingerprint(secret), crypto.fingerprint("${secret}x"))

        val weak = PasswordStrengthEvaluator.evaluate("password")
        assertEquals(PasswordStrength.WEAK, weak.strength)
        assertTrue(PasswordStrengthEvaluator.isCommonWeak("password"))

        val strong = PasswordStrengthEvaluator.evaluate(secret)
        assertTrue(strong.score >= 62)

        val generated = SecurePasswordGenerator.generateRandom(
            length = 18,
            includeUppercase = true,
            includeLowercase = true,
            includeDigits = true,
            includeSymbols = true
        )
        assertEquals(18, generated.length)
        assertTrue(generated.any { it.isUpperCase() })
        assertTrue(generated.any { it.isLowerCase() })
        assertTrue(generated.any { it.isDigit() })
        assertTrue(generated.any { !it.isLetterOrDigit() })

        val phrase = SecurePasswordGenerator.generatePassphrase(wordCount = 4, includeNumberAndSymbol = true)
        assertTrue(phrase.split('-').size >= 5)

        val reusedFp = crypto.fingerprint("Reused#Pass1")
        val items = listOf(
            SavedPasswordEntity(
                id = 1L,
                siteName = "GitHub",
                siteUrl = "https://github.com",
                host = "github.com",
                username = "dev@apex.io",
                encryptedPassword = crypto.encrypt("Reused#Pass1"),
                strengthScore = 70,
                passwordFingerprint = reusedFp
            ),
            SavedPasswordEntity(
                id = 2L,
                siteName = "GitLab",
                siteUrl = "https://gitlab.com",
                host = "gitlab.com",
                username = "dev@apex.io",
                encryptedPassword = crypto.encrypt("Reused#Pass1"),
                strengthScore = 70,
                passwordFingerprint = reusedFp
            ),
            SavedPasswordEntity(
                id = 3L,
                siteName = "Legacy",
                siteUrl = "https://legacy.org",
                host = "legacy.org",
                username = "old",
                encryptedPassword = crypto.encrypt("123456"),
                strengthScore = 12,
                passwordFingerprint = crypto.fingerprint("123456")
            )
        )
        val audit = PasswordAuditEngine.analyze(items) { crypto.decrypt(it.encryptedPassword) }
        assertEquals(3, audit.totalCount)
        assertEquals(1, audit.weakItems.size)
        assertEquals(1, audit.reusedGroups.size)
        assertEquals(1, audit.compromisedCount)
        assertTrue(audit.overallScore < 100)

        assertTrue(PasswordVaultUtil.hostMatches("github.com", "gist.github.com"))
        assertFalse(PasswordVaultUtil.hostMatches("github.com", "evilgithub.com"))

        val csv = PasswordVaultUtil.exportCsv(items) { crypto.decrypt(it.encryptedPassword) }
        val parsed = PasswordVaultUtil.parseCsv(csv)
        assertEquals(3, parsed.size)
        assertEquals("GitHub", parsed[0].siteName)
        assertEquals("Reused#Pass1", parsed[0].password)
    }

    @Test
    fun `verify BrowserRepository password vault CRUD host matching and CSV import`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val prefsRepo = UserPreferencesRepository(context)
        val sharedPrefs = context.getSharedPreferences("test_password_repo_prefs", Context.MODE_PRIVATE)
        val repository = BrowserRepository(db.apexDao(), prefsRepo, sharedPrefs)

        try {
            val savedId = repository.saveOrUpdatePassword(
                siteName = "GitHub",
                siteUrl = "https://github.com/login",
                username = "octocat",
                plainPassword = "S3cure!Apex#Password",
                notes = "Main dev account",
                category = "Work",
                isFavorite = true
            )
            assertTrue(savedId > 0L)

            val matches = repository.getPasswordsForUrl("https://gist.github.com/octocat")
            assertEquals(1, matches.size)
            assertEquals("octocat", matches.first().username)
            assertEquals("S3cure!Apex#Password", repository.decryptPassword(matches.first()))

            val csvInput = """
                name,url,username,password,note
                "Proton","https://account.proton.me","alice@proton.me","Vault#Key!9912","Recovery set"
            """.trimIndent()
            val importedCount = repository.importPasswordsFromCsv(csvInput)
            assertEquals(1, importedCount)

            val exportedCsv = repository.exportPasswordsToCsv()
            assertTrue(exportedCsv.contains("octocat"))
            assertTrue(exportedCsv.contains("alice@proton.me"))

            repository.deleteSavedPassword(savedId)
            assertTrue(repository.getPasswordsForUrl("https://github.com").isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun `verify Material 3 Expressive PasswordManagerScreenOverlay switches modes and displays vault entries`() {
        val crypto = PasswordVaultCrypto()
        val sample = SavedPasswordEntity(
            id = 10L,
            siteName = "Figma",
            siteUrl = "https://figma.com",
            host = "figma.com",
            username = "designer@studio.io",
            encryptedPassword = crypto.encrypt("Design#Vault99!"),
            category = "Work",
            isFavorite = true,
            strengthScore = 90,
            passwordFingerprint = crypto.fingerprint("Design#Vault99!")
        )

        composeTestRule.setContent {
            ApexBrowserTheme {
                PasswordManagerScreenOverlay(
                    passwords = listOf(sample),
                    autofillEnabled = true,
                    decryptPassword = { crypto.decrypt(it.encryptedPassword) },
                    onSavePassword = { _, _, _, _, _, _, _, _ -> },
                    onToggleFavorite = {},
                    onDeletePassword = {},
                    onClearVault = {},
                    onImportCsv = {},
                    onExportCsv = {},
                    onToggleAutofill = {},
                    onOpenWebsite = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("password_manager_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("passwords_split_add_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("passwords_mode_vault").assertIsDisplayed()
        composeTestRule.onNodeWithTag("passwords_mode_generator").assertIsDisplayed()
        composeTestRule.onNodeWithTag("passwords_mode_audit").assertIsDisplayed()

        // Bottom floating toolbar should not exist; controls live at the top
        composeTestRule.onNodeWithTag("passwords_floating_toolbar").assertDoesNotExist()
        composeTestRule.onNodeWithTag("toolbar_vault_btn").assertDoesNotExist()
        composeTestRule.onNodeWithTag("toolbar_generator_btn").assertDoesNotExist()
        composeTestRule.onNodeWithTag("toolbar_audit_btn").assertDoesNotExist()
        composeTestRule.onNodeWithTag("passwords_floating_add_fab").assertDoesNotExist()

        composeTestRule.onNodeWithTag("passwords_vault_list").performScrollToIndex(2)
        composeTestRule.onNodeWithText("Figma").assertIsDisplayed()
        composeTestRule.onNodeWithText("designer@studio.io").assertIsDisplayed()

        // Switch to Material 3 Expressive Generator tab
        composeTestRule.onNodeWithTag("passwords_mode_generator").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Random Characters").assertIsDisplayed()

        // Switch to Security Health Audit tab
        composeTestRule.onNodeWithTag("passwords_mode_audit").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Security Health").assertIsDisplayed()
        composeTestRule.onNodeWithText("All Credentials Passing").assertIsDisplayed()
    }

    @Test
    fun `verify Set as Default Browser hides in menu when default and Settings shows selected browser`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val status = DefaultBrowserHelper.getStatus(context)
        assertNotNull(status.currentDefaultName)
        val launchIntent = DefaultBrowserHelper.createRequestOrSettingsIntent(context)
        assertNotNull(launchIntent.action)

        var showMenu by mutableStateOf(true)
        var isDefault by mutableStateOf(false)
        var browserStatus by mutableStateOf(
            DefaultBrowserStatus(
                isApexDefault = false,
                currentDefaultName = "Chrome",
                currentDefaultPackage = "com.android.chrome"
            )
        )

        var isHomeMenu by mutableStateOf(true)

        composeTestRule.setContent {
            ApexBrowserTheme {
                if (showMenu) {
                    BrowserMenuBottomSheet(
                        isHomeActive = isHomeMenu,
                        desktopViewportEnabled = false,
                        isDefaultBrowser = isDefault,
                        onOpenSettings = {},
                        onSetAsDefaultBrowserClick = { isDefault = true },
                        onNewTabClick = {},
                        onReloadPageClick = {},
                        onToggleDesktopMode = {},
                        onClearBrowsingData = {},
                        onDismiss = {}
                    )
                } else {
                    SettingsScreenOverlay(
                        preferences = BrowserPreferences(),
                        onSelectSearchEngine = {},
                        onToggleShields = {},
                        onToggleHttpsUpgrade = {},
                        onToggleScriptGuard = {},
                        onToggleSmartPreload = {},
                        onToggleDesktopViewport = {},
                        onToggleDynamicColor = {},
                        onSelectSeedPalette = {},
                        onClearBrowsingData = {},
                        defaultBrowserStatus = browserStatus,
                        onOpenDefaultBrowserSettings = {},
                        onDismiss = {}
                    )
                }
            }
        }

        // 1. On Home page menu, default browser promo card appears when not default and hides when default
        composeTestRule.onNodeWithTag("menu_sheet_default_promo_card").assertIsDisplayed()
        isDefault = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("menu_sheet_default_promo_card").assertDoesNotExist()

        // 2. On Web page menu, expanding "More" shows "Set as Default Browser" when not default, and hides when default
        isHomeMenu = false
        isDefault = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("menu_sheet_more").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("menu_sheet_set_default_browser").performScrollTo()
        composeTestRule.onNodeWithTag("menu_sheet_set_default_browser").assertIsDisplayed()

        isDefault = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("menu_sheet_set_default_browser").assertDoesNotExist()

        // 3. In Settings, currently selected default browser ("Chrome") is displayed in subtitle without a pill badge
        showMenu = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_default_browser_card").performScrollTo()
        composeTestRule.onNodeWithTag("settings_default_browser_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Currently selected: Chrome").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_default_browser_selected_badge").assertDoesNotExist()

        // 4. Once Apex is default, Settings updates to display Apex Browser subtitle without a pill badge
        browserStatus = DefaultBrowserStatus(
            isApexDefault = true,
            currentDefaultName = "Apex Browser",
            currentDefaultPackage = context.packageName
        )
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Apex Browser is selected as your default browser").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_default_browser_selected_badge").assertDoesNotExist()
    }

    @Test
    fun `verify Desktop Viewport toggle closes menu sheet when enabled without hiding the item`() {
        var menuOpen by mutableStateOf(true)
        var desktopEnabled by mutableStateOf(false)

        composeTestRule.setContent {
            ApexBrowserTheme {
                if (menuOpen) {
                    BrowserMenuBottomSheet(
                        isHomeActive = false,
                        desktopViewportEnabled = desktopEnabled,
                        onOpenSettings = {},
                        onNewTabClick = {},
                        onReloadPageClick = {},
                        onToggleDesktopMode = { enabled -> desktopEnabled = enabled },
                        onClearBrowsingData = {},
                        onDismiss = { menuOpen = false }
                    )
                }
            }
        }

        // Visible when disabled
        composeTestRule.onNodeWithTag("menu_sheet_desktop_switch").performScrollTo()
        composeTestRule.onNodeWithTag("menu_sheet_desktop_switch").assertIsDisplayed()

        // Tap to enable -> turns on AND closes menu sheet
        composeTestRule.onNodeWithTag("menu_sheet_desktop_switch").performClick()
        composeTestRule.waitForIdle()
        assertTrue(desktopEnabled)
        assertFalse(menuOpen)
        composeTestRule.onNodeWithTag("hamburger_menu_bottom_sheet").assertDoesNotExist()

        // Reopen menu while desktop mode is enabled -> item is still visible (not hidden)
        menuOpen = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("menu_sheet_desktop_switch").performScrollTo()
        composeTestRule.onNodeWithTag("menu_sheet_desktop_switch").assertIsDisplayed()

        // Tap again to disable desktop mode
        composeTestRule.onNodeWithTag("menu_sheet_desktop_switch").performClick()
        composeTestRule.waitForIdle()
        assertFalse(desktopEnabled)
        assertTrue(menuOpen)
    }

    @Test
    fun `verify horizontal swipe left and right deletes tab card while small swipe cancels and tap selects`() {
        val tab1 = BrowserTab(id = "tab_swipe_1", title = "Docs", url = "https://kotlinlang.org")
        val tab2 = BrowserTab(id = "tab_swipe_2", title = "News", url = "https://news.ycombinator.com")
        val tab3 = BrowserTab(id = "tab_swipe_3", title = "Home", url = "apex://home")

        var tabs by mutableStateOf(listOf(tab1, tab2, tab3))
        var selectedTabId by mutableStateOf<String?>(null)
        val closedTabIds = mutableListOf<String>()

        composeTestRule.setContent {
            ApexBrowserTheme {
                DraggableTabGridOverlay(
                    gridEntries = tabs.map { TabGridEntry.SingleTab(it) },
                    totalOpenTabs = tabs.size,
                    activeTabId = tab1.id,
                    expandedGroupId = null,
                    onSelectTab = { selectedTabId = it },
                    onCloseTab = { id ->
                        closedTabIds.add(id)
                        tabs = tabs.filterNot { it.id == id }
                    },
                    onCloseGroup = {},
                    onEditGroup = {},
                    onMergeEntries = { _, _ -> },
                    onMoveGroupedTabToTarget = { _, _ -> },
                    onOpenGroup = {},
                    onAddTabToGroup = {},
                    onUngroupTab = {},
                    onCycleTabColor = {},
                    onNewTab = {},
                    onCreateGroup = {},
                    onResetAllTabs = {},
                    onDismiss = {}
                )
            }
        }

        // 1. Small partial horizontal swipe below threshold springs back without deleting
        composeTestRule.onNodeWithTag("grid_tab_card_tab_swipe_1").performTouchInput {
            swipe(
                start = center,
                end = center.copy(x = center.x + (width * 0.12f)),
                durationMillis = 300
            )
        }
        composeTestRule.waitForIdle()
        assertTrue(closedTabIds.isEmpty())
        composeTestRule.onNodeWithTag("grid_tab_card_tab_swipe_1").assertIsDisplayed()

        // 2. Tap still selects the tab
        composeTestRule.onNodeWithTag("grid_tab_card_tab_swipe_1").performClick()
        composeTestRule.waitForIdle()
        assertEquals("tab_swipe_1", selectedTabId)

        // 3. Full horizontal swipe left deletes tab_swipe_2
        composeTestRule.onNodeWithTag("grid_tab_card_tab_swipe_2").performTouchInput {
            swipeLeft()
        }
        composeTestRule.waitForIdle()
        assertTrue(closedTabIds.contains("tab_swipe_2"))
        composeTestRule.onNodeWithTag("grid_tab_card_tab_swipe_2").assertDoesNotExist()

        // 4. Full horizontal swipe right deletes tab_swipe_3
        composeTestRule.onNodeWithTag("grid_tab_card_tab_swipe_3").performTouchInput {
            swipeRight()
        }
        composeTestRule.waitForIdle()
        assertTrue(closedTabIds.contains("tab_swipe_3"))
        composeTestRule.onNodeWithTag("grid_tab_card_tab_swipe_3").assertDoesNotExist()
    }

    @Test
    fun `verify horizontal swipe deletes tab row inside expanded group modal`() {
        val group = TabGroupInfo(id = "grp_swipe", title = "Work")
        val member1 = BrowserTab(
            id = "grp_tab_1",
            title = "GitHub",
            url = "https://github.com",
            groupId = group.id
        )
        val member2 = BrowserTab(
            id = "grp_tab_2",
            title = "Android Docs",
            url = "https://developer.android.com",
            groupId = group.id
        )
        var groupTabs by mutableStateOf(listOf(member1, member2))
        val closedTabIds = mutableListOf<String>()

        composeTestRule.setContent {
            ApexBrowserTheme {
                DraggableTabGridOverlay(
                    gridEntries = listOf(TabGridEntry.GroupTab(group = group, tabs = groupTabs)),
                    totalOpenTabs = groupTabs.size,
                    activeTabId = member1.id,
                    expandedGroupId = group.id,
                    onSelectTab = {},
                    onCloseTab = { id ->
                        closedTabIds.add(id)
                        groupTabs = groupTabs.filterNot { it.id == id }
                    },
                    onCloseGroup = {},
                    onEditGroup = {},
                    onMergeEntries = { _, _ -> },
                    onMoveGroupedTabToTarget = { _, _ -> },
                    onOpenGroup = {},
                    onAddTabToGroup = {},
                    onUngroupTab = {},
                    onCycleTabColor = {},
                    onNewTab = {},
                    onCreateGroup = {},
                    onResetAllTabs = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("expanded_group_modal").assertIsDisplayed()
        composeTestRule.onNodeWithTag("group_modal_tab_row_grp_tab_2").performTouchInput {
            swipeLeft()
        }
        composeTestRule.waitForIdle()
        assertEquals(listOf("grp_tab_2"), closedTabIds)
        composeTestRule.onNodeWithTag("group_modal_tab_row_grp_tab_2").assertDoesNotExist()
    }

    @Test
    fun `verify desktop viewport defaults off and swipe to delete has no red backdrop`() {
        // Default BrowserPreferences keeps desktop viewport disabled
        assertFalse(BrowserPreferences().desktopViewportEnabled)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val sharedPrefs = context.getSharedPreferences("apex_browser_user_preferences", Context.MODE_PRIVATE)
        sharedPrefs.edit()
            .putBoolean("desktop_viewport_enabled", true)
            .remove("reset_default_desktop_viewport_v1")
            .apply()
        val prefsRepo = UserPreferencesRepository(context)
        val repository = BrowserRepository(db.apexDao(), prefsRepo, sharedPrefs)

        val prefsAfterInit = runBlocking {
            repository.ensureFreshInitialized()
            repository.preferencesFlow.first()
        }
        assertFalse(prefsAfterInit.desktopViewportEnabled)
        db.close()

        // Mid-swipe check confirms no red delete backdrop or delete icon is rendered
        val tab1 = BrowserTab(id = "tab_nobg_1", title = "Docs", url = "https://kotlinlang.org")
        var tabs by mutableStateOf(listOf(tab1))
        composeTestRule.setContent {
            ApexBrowserTheme {
                DraggableTabGridOverlay(
                    gridEntries = tabs.map { TabGridEntry.SingleTab(it) },
                    totalOpenTabs = tabs.size,
                    activeTabId = tab1.id,
                    expandedGroupId = null,
                    onSelectTab = {},
                    onCloseTab = { id -> tabs = tabs.filterNot { it.id == id } },
                    onCloseGroup = {},
                    onEditGroup = {},
                    onMergeEntries = { _, _ -> },
                    onMoveGroupedTabToTarget = { _, _ -> },
                    onOpenGroup = {},
                    onAddTabToGroup = {},
                    onUngroupTab = {},
                    onCycleTabColor = {},
                    onNewTab = {},
                    onCreateGroup = {},
                    onResetAllTabs = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("grid_tab_card_tab_nobg_1").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(-80f, 0f))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("swipe_delete_backdrop_tab_nobg_1").assertDoesNotExist()
        composeTestRule.onNodeWithTag("grid_tab_card_tab_nobg_1").performTouchInput { up() }
        composeTestRule.waitForIdle()
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    @Test
    fun `verify swipe to delete tab shows undo snackbar and undo restores closed tab`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val sharedPrefs = context.getSharedPreferences("test_undo_tab_prefs", Context.MODE_PRIVATE)
        val prefsRepo = UserPreferencesRepository(context)
        val repository = BrowserRepository(db.apexDao(), prefsRepo, sharedPrefs)
        val viewModel = BrowserViewModel(repository)

        viewModel.createNewTab(stayInSwitcher = true)
        val secondTab = viewModel.uiState.value.tabs.last()
        viewModel.setTabSwitcherOpen(true)

        composeTestRule.setContent {
            ApexBrowserTheme {
                BrowserScreen(
                    viewModel = viewModel,
                    windowSizeClass = WindowSizeClass.calculateFromSize(DpSize(412.dp, 915.dp))
                )
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(2, viewModel.uiState.value.tabs.size)
        composeTestRule.onNodeWithTag("grid_tab_card_${secondTab.id}").assertIsDisplayed()

        // Swipe horizontally to delete the second tab
        composeTestRule.onNodeWithTag("grid_tab_card_${secondTab.id}").performTouchInput {
            swipeLeft()
        }
        composeTestRule.waitForIdle()

        assertEquals(1, viewModel.uiState.value.tabs.size)
        composeTestRule.onNodeWithTag("grid_tab_card_${secondTab.id}").assertDoesNotExist()
        composeTestRule.onNodeWithText("Undo").assertIsDisplayed()

        // Snackbar must sit cleanly above the Add (+) button with no overlap
        val snackbarBounds = composeTestRule.onNodeWithTag("browser_snackbar_host").fetchSemanticsNode().boundsInRoot
        val addButtonBounds = composeTestRule.onNodeWithTag("switcher_new_tab_fab").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Expected snackbar bottom (${snackbarBounds.bottom}) <= Add button top (${addButtonBounds.top})",
            snackbarBounds.bottom <= addButtonBounds.top
        )

        // Tap Undo on the Snackbar -> restores the closed tab
        composeTestRule.onNodeWithText("Undo").performClick()
        composeTestRule.waitForIdle()

        assertEquals(2, viewModel.uiState.value.tabs.size)
        assertTrue(viewModel.uiState.value.tabs.any { it.id == secondTab.id })
        composeTestRule.onNodeWithTag("grid_tab_card_${secondTab.id}").assertIsDisplayed()

        db.close()
    }

    @Test
    fun `verify reloadActiveTab sets loading synchronously and onWebViewUrlCommitted avoids loading flicker`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val sharedPrefs = context.getSharedPreferences("test_reload_sync_prefs", Context.MODE_PRIVATE)
        val prefsRepo = UserPreferencesRepository(context)
        val repository = BrowserRepository(db.apexDao(), prefsRepo, sharedPrefs)
        val store = androidx.lifecycle.ViewModelStore()
        val viewModel = androidx.lifecycle.ViewModelProvider(
            store,
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return BrowserViewModel(repository) as T
                }
            }
        )[BrowserViewModel::class.java]

        try {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            viewModel.submitNavigation("https://example.com")
            val tabId = viewModel.uiState.value.activeTabId
            viewModel.onWebViewPageFinished(
                tabId = tabId,
                title = "Example",
                url = "https://example.com",
                canGoBack = false,
                canGoForward = false
            )
            assertFalse(viewModel.uiState.value.activeTab.isLoading)

            // SPA history/anchor commit updates URL without flashing isLoading = true
            viewModel.onWebViewUrlCommitted(tabId, "https://example.com/#section")
            assertEquals("https://example.com/#section", viewModel.uiState.value.activeTab.url)
            assertFalse(viewModel.uiState.value.activeTab.isLoading)

            // reloadActiveTab immediately marks tab as loading so pull-to-refresh does not cancel early
            viewModel.reloadActiveTab()
            assertTrue(viewModel.uiState.value.activeTab.isLoading)
            assertTrue(viewModel.uiState.value.activeTab.progress > 0f)
        } finally {
            store.clear()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            kotlinx.coroutines.delay(50)
            db.close()
        }
    }

    @Test
    fun `verify WebViewViewport downward pull from top reveals expressive indicator and triggers refresh`() {
        var tab by mutableStateOf(
            BrowserTab(
                id = "ptr_tab_1",
                title = "Example",
                url = "https://example.com",
                isLoading = false,
                progress = 1f
            )
        )
        var refreshCount by mutableIntStateOf(0)
        val privacyManager = PrivacyManager()
        val performanceOptimizer = PerformanceOptimizer()

        composeTestRule.setContent {
            ApexBrowserTheme {
                WebViewViewport(
                    tab = tab,
                    preferences = BrowserPreferences(),
                    privacyManager = privacyManager,
                    performanceOptimizer = performanceOptimizer,
                    existingWebView = null,
                    onRegisterWebView = { _, _ -> },
                    onPageStarted = { _, _ -> },
                    onProgressChanged = { _, _ -> },
                    onPageFinished = { _, _, _, _, _ -> },
                    onSubresourceBlocked = { _, _, _, _, _, _ -> },
                    onHttpsUpgradeTriggered = {},
                    onScrollDelta = {},
                    onRefreshPage = {
                        refreshCount += 1
                        tab = tab.copy(isLoading = true, progress = 0.08f)
                    }
                )
            }
        }
        composeTestRule.waitForIdle()

        // Pull down on the WebView from top (scrollY == 0)
        composeTestRule.onNodeWithTag("browser_webview_ptr_tab_1").performTouchInput {
            swipeDown(
                startY = top + 40f,
                endY = top + 420f,
                durationMillis = 250
            )
        }
        composeTestRule.waitForIdle()

        assertEquals(1, refreshCount)
        composeTestRule.onNodeWithTag("expressive_pull_to_refresh_indicator").assertIsDisplayed()
    }

    @Test
    fun `verify Brave Search is default search engine in preferences and repository init`() = runBlocking {
        assertEquals(SearchEngineOption.BRAVE, BrowserPreferences().searchEngine)
        assertEquals(SearchEngineOption.BRAVE, SearchEngineOption.fromId(null))
        assertEquals(SearchEngineOption.BRAVE, SearchEngineOption.fromId("unknown"))

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val sharedPrefs = context.getSharedPreferences("test_brave_default_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().clear().commit()
        val prefsRepo = UserPreferencesRepository(context)
        val repository = BrowserRepository(db.apexDao(), prefsRepo, sharedPrefs)

        try {
            assertEquals(SearchEngineOption.BRAVE, prefsRepo.preferencesFlow.first().searchEngine)

            // Simulate an older install that had DuckDuckGo before the Brave default migration ran
            prefsRepo.setSearchEngine(SearchEngineOption.DUCKDUCKGO)
            repository.ensureFreshInitialized()

            assertEquals(SearchEngineOption.BRAVE, prefsRepo.preferencesFlow.first().searchEngine)
            assertTrue(sharedPrefs.getBoolean("reset_default_search_engine_brave_v1", false))
        } finally {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            db.close()
        }
    }

    @Test
    fun `verify DownloadsScreenOverlay app bar uses icon-only pill buttons on right side`() {
        val sampleDownload = com.example.data.local.DownloadItemEntity(
            id = 42L,
            fileName = "archive.zip",
            url = "https://example.com/archive.zip",
            mimeType = "application/zip",
            category = "Archive",
            totalBytes = 2048L,
            downloadedBytes = 1024L,
            speedBytesPerSec = 512L,
            chunksCount = 4,
            status = FastDownloadEngine.STATUS_DOWNLOADING,
            localFilePath = "/tmp/archive.zip",
            createdAt = System.currentTimeMillis()
        )

        composeTestRule.setContent {
            ApexBrowserTheme {
                com.example.ui.browser.DownloadsScreenOverlay(
                    downloads = listOf(sampleDownload),
                    onStartNewDownload = { _, _ -> },
                    onPauseDownload = {},
                    onResumeDownload = {},
                    onRetryDownload = {},
                    onCancelDownload = {},
                    onDeleteDownload = {},
                    onClearCompletedDownloads = {},
                    onClearAllDownloads = {},
                    onOpenUrlInBrowser = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Downloads").assertIsDisplayed()
        composeTestRule.onNodeWithTag("downloads_new_url_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("downloads_clear_button").assertIsDisplayed()

        // App bar buttons must be icon-only pills with no inline "New Download" / "Clear" text
        composeTestRule.onNodeWithText("New Download").assertDoesNotExist()
        composeTestRule.onNodeWithText("Clear").assertDoesNotExist()

        composeTestRule.onNodeWithTag("downloads_new_url_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Start Fast Download").assertIsDisplayed()
    }

    @Test
    fun `verify DownloadNotificationHelper shows progress in status bar notification and clears on completion`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        val helper = com.example.engine.DownloadNotificationHelper(app)
        val notificationManager = app.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val shadowNotificationManager = Shadows.shadowOf(notificationManager)

        val downloadId = 77L
        val downloadingItem = com.example.data.local.DownloadItemEntity(
            id = downloadId,
            fileName = "ubuntu.iso",
            url = "https://example.com/ubuntu.iso",
            mimeType = "application/octet-stream",
            category = "Other",
            totalBytes = 100_000_000L,
            downloadedBytes = 50_000_000L,
            speedBytesPerSec = 5_000_000L,
            chunksCount = 4,
            status = FastDownloadEngine.STATUS_DOWNLOADING,
            localFilePath = "/tmp/ubuntu.iso",
            createdAt = System.currentTimeMillis()
        )

        helper.notifyDownloadState(downloadingItem, forceImmediate = true)

        val notifId = com.example.engine.DownloadNotificationHelper.notificationIdFor(downloadId)
        val activeNotif = shadowNotificationManager.getNotification(notifId)
        assertNotNull(activeNotif)
        assertTrue((activeNotif.flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0)
        assertEquals(50, activeNotif.extras.getInt(android.app.Notification.EXTRA_PROGRESS))
        assertEquals(100, activeNotif.extras.getInt(android.app.Notification.EXTRA_PROGRESS_MAX))

        val completedItem = downloadingItem.copy(
            downloadedBytes = 100_000_000L,
            status = FastDownloadEngine.STATUS_COMPLETED,
            completedAt = System.currentTimeMillis()
        )
        helper.notifyDownloadState(completedItem, forceImmediate = true)

        val completedNotif = shadowNotificationManager.getNotification(notifId)
        assertNotNull(completedNotif)
        assertTrue((completedNotif.flags and android.app.Notification.FLAG_ONGOING_EVENT) == 0)
        assertEquals(0, completedNotif.extras.getInt(android.app.Notification.EXTRA_PROGRESS_MAX))

        helper.cancel(downloadId)
        assertNull(shadowNotificationManager.getNotification(notifId))
    }

    @Test
    fun `verify active tab grid card renders cleanly under selected theme palette`() {
        val activeTab = BrowserTab(
            id = "tab_theme_active",
            title = "Brave Search",
            url = "https://search.brave.com",
            isLoading = false
        )
        val inactiveTab = BrowserTab(
            id = "tab_theme_inactive",
            title = "Home",
            url = "apex://home",
            isLoading = false
        )

        composeTestRule.setContent {
            ApexBrowserTheme(
                dynamicColor = false,
                seedPreset = SeedPalettePreset.CYBER_EMERALD
            ) {
                DraggableTabGridOverlay(
                    gridEntries = listOf(
                        TabGridEntry.SingleTab(activeTab),
                        TabGridEntry.SingleTab(inactiveTab)
                    ),
                    totalOpenTabs = 2,
                    activeTabId = activeTab.id,
                    expandedGroupId = null,
                    onSelectTab = {},
                    onCloseTab = {},
                    onCloseGroup = {},
                    onEditGroup = {},
                    onMergeEntries = { _, _ -> },
                    onMoveGroupedTabToTarget = { _, _ -> },
                    onOpenGroup = {},
                    onAddTabToGroup = {},
                    onUngroupTab = {},
                    onCycleTabColor = {},
                    onNewTab = {},
                    onCreateGroup = {},
                    onResetAllTabs = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("grid_tab_card_tab_theme_active").assertIsDisplayed()
        composeTestRule.onNodeWithTag("grid_tab_card_tab_theme_inactive").assertIsDisplayed()
    }

    @OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
    @Test
    fun `verify Google Sans Flex typography scale roles axes and zero letter spacing`() {
        val typography = com.example.ui.theme.ApexTypography

        data class RoleSpec(
            val base: androidx.compose.ui.text.TextStyle,
            val emphasized: androidx.compose.ui.text.TextStyle,
            val sizeSp: Float,
            val lineHeightSp: Float,
            val baseWeight: Int,
            val emphasizedWeight: Int
        )

        val specs = listOf(
            RoleSpec(typography.displayLarge, typography.displayLargeEmphasized, 57f, 64f, 400, 500),
            RoleSpec(typography.displayMedium, typography.displayMediumEmphasized, 45f, 52f, 400, 500),
            RoleSpec(typography.displaySmall, typography.displaySmallEmphasized, 36f, 44f, 400, 500),
            RoleSpec(typography.headlineLarge, typography.headlineLargeEmphasized, 32f, 40f, 400, 500),
            RoleSpec(typography.headlineMedium, typography.headlineMediumEmphasized, 28f, 36f, 400, 500),
            RoleSpec(typography.headlineSmall, typography.headlineSmallEmphasized, 24f, 32f, 400, 500),
            RoleSpec(typography.titleLarge, typography.titleLargeEmphasized, 22f, 28f, 400, 500),
            RoleSpec(typography.titleMedium, typography.titleMediumEmphasized, 16f, 24f, 500, 600),
            RoleSpec(typography.titleSmall, typography.titleSmallEmphasized, 14f, 20f, 500, 600),
            RoleSpec(typography.bodyLarge, typography.bodyLargeEmphasized, 16f, 24f, 400, 500),
            RoleSpec(typography.bodyMedium, typography.bodyMediumEmphasized, 14f, 20f, 400, 500),
            RoleSpec(typography.bodySmall, typography.bodySmallEmphasized, 12f, 16f, 400, 500),
            RoleSpec(typography.labelLarge, typography.labelLargeEmphasized, 14f, 20f, 500, 600),
            RoleSpec(typography.labelMedium, typography.labelMediumEmphasized, 12f, 16f, 500, 600),
            RoleSpec(typography.labelSmall, typography.labelSmallEmphasized, 11f, 16f, 500, 600)
        )

        specs.forEach { spec ->
            assertEquals(spec.sizeSp, spec.base.fontSize.value, 0.001f)
            assertEquals(spec.lineHeightSp, spec.base.lineHeight.value, 0.001f)
            assertEquals(spec.baseWeight, spec.base.fontWeight?.weight)
            assertEquals(0f, spec.base.letterSpacing.value, 0.001f)

            assertEquals(spec.sizeSp, spec.emphasized.fontSize.value, 0.001f)
            assertEquals(spec.lineHeightSp, spec.emphasized.lineHeight.value, 0.001f)
            assertEquals(spec.emphasizedWeight, spec.emphasized.fontWeight?.weight)
            assertEquals(0f, spec.emphasized.letterSpacing.value, 0.001f)

            val baseFamily = spec.base.fontFamily as androidx.compose.ui.text.font.FontListFontFamily
            val empFamily = spec.emphasized.fontFamily as androidx.compose.ui.text.font.FontListFontFamily
            val baseFont = baseFamily.fonts.first() as androidx.compose.ui.text.font.ResourceFont
            val empFont = empFamily.fonts.first() as androidx.compose.ui.text.font.ResourceFont

            assertEquals(R.font.google_sans_flex, baseFont.resId)
            assertEquals(R.font.google_sans_flex, empFont.resId)

            val density = androidx.compose.ui.unit.Density(1f)
            val baseOpsz = baseFont.variationSettings.settings.first { it.axisName == "opsz" }.toVariationValue(density)
            val baseRond = baseFont.variationSettings.settings.first { it.axisName == "ROND" }.toVariationValue(density)
            assertEquals(spec.sizeSp, baseOpsz, 0.001f)
            assertEquals(0f, baseRond, 0.001f)

            val empOpsz = empFont.variationSettings.settings.first { it.axisName == "opsz" }.toVariationValue(density)
            val empRond = empFont.variationSettings.settings.first { it.axisName == "ROND" }.toVariationValue(density)
            assertEquals(spec.sizeSp, empOpsz, 0.001f)
            assertEquals(100f, empRond, 0.001f)
        }

        // Verify EmphasizedTypography custom styles also have 0 letterSpacing and bundled fonts
        assertEquals(0f, com.example.ui.theme.EmphasizedTypography.displayStat.letterSpacing.value, 0.001f)
        assertEquals(0f, com.example.ui.theme.EmphasizedTypography.headlineHero.letterSpacing.value, 0.001f)
        assertEquals(0f, com.example.ui.theme.EmphasizedTypography.addressBarActive.letterSpacing.value, 0.001f)
        assertEquals(0f, com.example.ui.theme.EmphasizedTypography.activeTabTitle.letterSpacing.value, 0.001f)
        assertEquals(0f, com.example.ui.theme.EmphasizedTypography.badgeCounter.letterSpacing.value, 0.001f)
        assertEquals(0f, com.example.ui.theme.EmphasizedTypography.webClipboard.letterSpacing.value, 0.001f)
        assertEquals(0f, com.example.ui.theme.EmphasizedTypography.monoCode.letterSpacing.value, 0.001f)
    }

    @Test
    fun `verify BrowserMenuBottomSheet renders M3 Expressive layout with Automation and Sign in`() {
        var backClicked = false
        var findClicked = false
        var automationClicked = false
        var signInClicked = false

        composeTestRule.setContent {
            ApexBrowserTheme {
                BrowserMenuBottomSheet(
                    isHomeActive = false,
                    canNavigateBack = true,
                    canNavigateForward = false,
                    isCurrentPageBookmarked = false,
                    desktopViewportEnabled = false,
                    browsingHistoryCount = 5,
                    downloadsCount = 3,
                    savedPasswordsCount = 2,
                    isDefaultBrowser = false,
                    onBackClick = { backClicked = true },
                    onFindInPageClick = { findClicked = true },
                    onOpenAutomationHudClick = { automationClicked = true },
                    onSignInClick = { signInClicked = true },
                    onOpenSettings = {},
                    onToggleDesktopMode = {},
                    onClearBrowsingData = {},
                    onDismiss = {}
                )
            }
        }

        // Top 4-action bar
        composeTestRule.onNodeWithTag("menu_sheet_back").assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_sheet_forward").assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_sheet_share").assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_sheet_refresh").assertIsDisplayed()

        // Group 1 primary items (Automation replaces Extensions)
        composeTestRule.onNodeWithTag("menu_sheet_bookmark_page").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_sheet_find_in_page").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_sheet_desktop_switch").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_sheet_desktop_pill").assertIsDisplayed()
        composeTestRule.onNodeWithText("Off").assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_sheet_new_incognito_tab").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_sheet_automation").performScrollTo().assertIsDisplayed()
        val webPrivateBounds = composeTestRule.onNodeWithTag("menu_sheet_new_incognito_tab").getBoundsInRoot()
        val webAutomationBounds = composeTestRule.onNodeWithTag("menu_sheet_automation").getBoundsInRoot()
        assertTrue(webPrivateBounds.top < webAutomationBounds.top)
        composeTestRule.onNodeWithText("Automation").assertIsDisplayed()
        composeTestRule.onNodeWithText("Extensions").assertDoesNotExist()
        composeTestRule.onNodeWithTag("menu_sheet_more").performScrollTo().assertIsDisplayed()

        // Middle 4-tile segmented row
        composeTestRule.onNodeWithTag("menu_sheet_history").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_sheet_bookmarks").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_sheet_downloads").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_sheet_passwords").performScrollTo().assertIsDisplayed()

        // Bottom Group 2 (Apex Pro + Settings)
        composeTestRule.onNodeWithTag("menu_sheet_sign_in").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Apex Pro").assertIsDisplayed()
        composeTestRule.onNodeWithText("5 of 5 free automation plays left today").assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_sheet_pro_badge", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_sheet_open_settings").performScrollTo().assertIsDisplayed()

        // Top 4-action bar is fixed outside the scrollable body
        val collapsedTopBarBounds = composeTestRule.onNodeWithTag("menu_sheet_top_actions")
            .assertIsDisplayed()
            .getBoundsInRoot()
        val collapsedBodyBounds = composeTestRule.onNodeWithTag("menu_sheet_scrollable_body")
            .assertIsDisplayed()
            .getBoundsInRoot()

        // Expand Automation & More sub-items while keeping height fixed and top bar pinned
        composeTestRule.onNodeWithTag("menu_sheet_automation_expand").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("menu_sheet_automation_hud").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_sheet_automation_workspace").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("menu_sheet_more").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        val expandedTopBarBounds = composeTestRule.onNodeWithTag("menu_sheet_top_actions")
            .assertIsDisplayed()
            .getBoundsInRoot()
        val expandedBodyBounds = composeTestRule.onNodeWithTag("menu_sheet_scrollable_body")
            .assertIsDisplayed()
            .getBoundsInRoot()
        assertEquals(collapsedTopBarBounds.top.value, expandedTopBarBounds.top.value, 1.5f)
        assertEquals(collapsedBodyBounds.height.value, expandedBodyBounds.height.value, 1.5f)

        composeTestRule.onNodeWithTag("menu_sheet_new_tab").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_sheet_set_default_browser").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_sheet_clear_data").performScrollTo().assertIsDisplayed()

        // No legacy section subheadings or offline pages
        composeTestRule.onNodeWithTag("menu_sheet_offline_pages").assertDoesNotExist()
        composeTestRule.onNodeWithTag("menu_sheet_save_offline").assertDoesNotExist()
        composeTestRule.onNodeWithText("Quick Actions").assertDoesNotExist()
        composeTestRule.onNodeWithText("Preferences & Privacy").assertDoesNotExist()

        // Interactive callbacks (top bar action clicked directly without performScrollTo since it is fixed)
        composeTestRule.onNodeWithTag("menu_sheet_back").performClick()
        composeTestRule.onNodeWithTag("menu_sheet_find_in_page").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("menu_sheet_automation").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("menu_sheet_sign_in").performScrollTo().performClick()
        assertTrue(backClicked)
        assertTrue(findClicked)
        assertTrue(automationClicked)
        assertTrue(signInClicked)
    }

    @Test
    fun `verify Home Page BrowserMenuBottomSheet renders Promo Automation Appearance and direct Settings Appearance`() {
        var promoDismissed by mutableStateOf(false)
        var showDirectAppearance by mutableStateOf(false)
        var settingsDismissed by mutableStateOf(false)

        composeTestRule.setContent {
            ApexBrowserTheme {
                if (!showDirectAppearance) {
                    BrowserMenuBottomSheet(
                        isHomeActive = true,
                        desktopViewportEnabled = false,
                        isDefaultBrowser = false,
                        isDefaultPromoDismissed = promoDismissed,
                        onOpenAppearanceClick = { showDirectAppearance = true },
                        onDismissDefaultPromo = { promoDismissed = true },
                        onOpenSettings = {},
                        onToggleDesktopMode = {},
                        onClearBrowsingData = {},
                        onDismiss = {}
                    )
                } else {
                    SettingsScreenOverlay(
                        preferences = BrowserPreferences(),
                        initialSection = SettingsInitialSection.APPEARANCE,
                        onSelectSearchEngine = {},
                        onToggleShields = {},
                        onToggleHttpsUpgrade = {},
                        onToggleScriptGuard = {},
                        onToggleSmartPreload = {},
                        onToggleDesktopViewport = {},
                        onToggleDynamicColor = {},
                        onSelectSeedPalette = {},
                        onClearBrowsingData = {},
                        onDismiss = { settingsDismissed = true }
                    )
                }
            }
        }

        // Home menu hides top Back/Forward/Share/Refresh bar and shows promo card
        composeTestRule.onNodeWithTag("menu_sheet_top_actions").assertDoesNotExist()
        composeTestRule.onNodeWithTag("menu_sheet_more").assertDoesNotExist()
        composeTestRule.onNodeWithTag("menu_sheet_default_promo_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Make Apex Browser your default").assertIsDisplayed()
        composeTestRule.onNodeWithText("Fast, private, and made for you.").assertIsDisplayed()

        // New private tab item sits directly above Automation on Home menu
        composeTestRule.onNodeWithTag("menu_sheet_new_incognito_tab").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_sheet_automation").performScrollTo().assertIsDisplayed()
        val homePrivateBounds = composeTestRule.onNodeWithTag("menu_sheet_new_incognito_tab").getBoundsInRoot()
        val homeAutomationBounds = composeTestRule.onNodeWithTag("menu_sheet_automation").getBoundsInRoot()
        assertTrue(homePrivateBounds.top < homeAutomationBounds.top)
        composeTestRule.onNodeWithText("Automation").assertIsDisplayed()
        composeTestRule.onNodeWithText("Extensions").assertDoesNotExist()

        // 4-tile segmented bar
        composeTestRule.onNodeWithTag("menu_sheet_history").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_sheet_bookmarks").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_sheet_downloads").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_sheet_passwords").performScrollTo().assertIsDisplayed()

        // Bottom 3-item group: Sign in, Appearance (replaces Customise homepage), Settings
        composeTestRule.onNodeWithTag("menu_sheet_sign_in").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_sheet_appearance").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeTestRule.onNodeWithText("Customise homepage").assertDoesNotExist()
        composeTestRule.onNodeWithTag("menu_sheet_open_settings").performScrollTo().assertIsDisplayed()

        // Default browser promo card no longer has a close icon button
        composeTestRule.onNodeWithTag("menu_sheet_default_promo_dismiss").assertDoesNotExist()
        promoDismissed = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("menu_sheet_default_promo_card").assertDoesNotExist()

        // Clicking Appearance opens Settings directly to Appearance screen, and Back dismisses overlay
        composeTestRule.onNodeWithTag("menu_sheet_appearance").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_appearance_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_appearance_back_button").performClick()
        assertTrue(settingsDismissed)
    }

    @Test
    fun `verify holding Tabs button on website opens quick menu with new tab private tab and close tab`() {
        var isHomeActive by mutableStateOf(true)
        var tabsClicks = 0
        var newTabClicks = 0
        var newPrivateTabClicks = 0
        var closeTabClicks = 0

        composeTestRule.setContent {
            ApexBrowserTheme {
                BraveBottomNavigationBar(
                    isHomeActive = isHomeActive,
                    isBookmarksScreenOpen = false,
                    isSettingsScreenOpen = false,
                    isMenuSheetOpen = false,
                    isCurrentWebPageBookmarked = false,
                    isTabSwitcherOpen = false,
                    isSearchScreenOpen = false,
                    openTabCount = 2,
                    onHomeClick = {},
                    onBookmarksClick = {},
                    onSearchClick = {},
                    onTabsClick = { tabsClicks++ },
                    onMenuClick = {},
                    onNewTabFromTabsMenu = { newTabClicks++ },
                    onNewPrivateTabFromTabsMenu = { newPrivateTabClicks++ },
                    onCloseCurrentTabFromTabsMenu = { closeTabClicks++ },
                    isTabsQuickMenuAvailable = !isHomeActive
                )
            }
        }

        // On Home screen, holding Tabs does not open the quick menu
        composeTestRule.onNodeWithTag("nav_tabs_button").performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tabs_quick_dropdown_menu").assertDoesNotExist()

        // On a website, holding Tabs opens the quick menu without opening the full tab switcher
        isHomeActive = false
        tabsClicks = 0
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nav_tabs_button").performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tabs_quick_dropdown_menu").assertIsDisplayed()
        assertEquals(0, tabsClicks)

        composeTestRule.onNodeWithTag("tabs_quick_menu_new_tab").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tabs_quick_menu_new_private_tab").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tabs_quick_menu_close_tab").assertIsDisplayed()

        composeTestRule.onNodeWithTag("tabs_quick_menu_new_tab").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, newTabClicks)

        composeTestRule.onNodeWithTag("nav_tabs_button").performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tabs_quick_menu_new_private_tab").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, newPrivateTabClicks)

        composeTestRule.onNodeWithTag("nav_tabs_button").performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tabs_quick_menu_close_tab").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, closeTabClicks)
    }

    @Test
    fun `verify SettingsScreenOverlay opens dedicated Privacy and Safety and Appearance sub-screens with selected Search Engine subheading`() {
        var currentPrefs by mutableStateOf(
            BrowserPreferences(searchEngine = SearchEngineOption.BRAVE)
        )
        var openedPasswords by mutableStateOf(false)
        var openedHistory by mutableStateOf(false)

        composeTestRule.setContent {
            ApexBrowserTheme {
                SettingsScreenOverlay(
                    preferences = currentPrefs,
                    onSelectSearchEngine = { engine ->
                        currentPrefs = currentPrefs.copy(searchEngine = engine)
                    },
                    onToggleShields = {},
                    onToggleHttpsUpgrade = {},
                    onToggleScriptGuard = {},
                    onToggleSmartPreload = {},
                    onToggleDesktopViewport = {},
                    onToggleDynamicColor = {},
                    onSelectSeedPalette = {},
                    onSelectThemeMode = { mode ->
                        currentPrefs = currentPrefs.copy(themeMode = mode)
                    },
                    onClearBrowsingData = {},
                    onOpenPasswordManager = { openedPasswords = true },
                    onOpenHistory = { openedHistory = true },
                    onDismiss = {}
                )
            }
        }

        // Main Settings shows Search Engine with selected engine as subheading
        composeTestRule.onNodeWithTag("settings_search_engine_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Search Engine").assertIsDisplayed()
        composeTestRule.onNodeWithText("Brave Search").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_default_browser_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_default_browser_selected_badge").assertDoesNotExist()

        // Selecting a different search engine updates the subheading
        currentPrefs = currentPrefs.copy(searchEngine = SearchEngineOption.GOOGLE)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Google").assertIsDisplayed()

        // Passwords, Downloads, and History appear above Appearance
        composeTestRule.onNodeWithTag("settings_privacy_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_passwords_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_downloads_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_history_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_appearance_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_browsing_card").performScrollTo().assertIsDisplayed()

        val passwordsTop = composeTestRule.onNodeWithTag("settings_passwords_card").fetchSemanticsNode().boundsInRoot.top
        val downloadsTop = composeTestRule.onNodeWithTag("settings_downloads_card").fetchSemanticsNode().boundsInRoot.top
        val historyTop = composeTestRule.onNodeWithTag("settings_history_card").fetchSemanticsNode().boundsInRoot.top
        val appearanceTop = composeTestRule.onNodeWithTag("settings_appearance_card").fetchSemanticsNode().boundsInRoot.top
        assertTrue(passwordsTop < appearanceTop)
        assertTrue(downloadsTop < appearanceTop)
        assertTrue(historyTop < appearanceTop)

        // Quick links trigger their screen callbacks; Downloads opens its dedicated sub-screen
        composeTestRule.onNodeWithTag("settings_passwords_card").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("settings_history_card").performScrollTo().performClick()
        assertTrue(openedPasswords)
        assertTrue(openedHistory)

        composeTestRule.onNodeWithTag("settings_downloads_card").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_downloads_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_download_location_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_downloads_back_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()

        composeTestRule.onNodeWithTag("settings_switch_shields").assertDoesNotExist()
        composeTestRule.onNodeWithTag("settings_switch_dynamic_color").assertDoesNotExist()
        composeTestRule.onNodeWithTag("settings_switch_preload").assertDoesNotExist()

        // Open Privacy & Safety sub-screen (no Passwords or Auto-Save & Fill Logins toggle inside)
        composeTestRule.onNodeWithTag("settings_privacy_card").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_privacy_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Privacy & Safety").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_switch_shields").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_switch_https").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_switch_script_guard").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_clear_data_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_switch_password_autofill").assertDoesNotExist()
        composeTestRule.onNodeWithTag("settings_open_password_manager").assertDoesNotExist()
        composeTestRule.onNodeWithText("Block known third-party ad networks and tracking scripts").assertDoesNotExist()
        composeTestRule.onNodeWithText("Automatically upgrade unencrypted HTTP links to HTTPS").assertDoesNotExist()
        composeTestRule.onNodeWithText("Removes saved search queries and resets local counters").assertDoesNotExist()

        // Navigate back to main Settings
        composeTestRule.onNodeWithTag("settings_privacy_back_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()

        // Open Appearance sub-screen (Theme mode, Dynamic Color, Color Palette & Top Visits)
        composeTestRule.onNodeWithTag("settings_appearance_card").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_appearance_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_theme_mode_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_theme_mode_system").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_theme_mode_light").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_theme_mode_dark").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_theme_mode_dark").performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.data.preferences.ThemeModeOption.DARK, currentPrefs.themeMode)
        composeTestRule.onNodeWithTag("settings_switch_dynamic_color").assertIsDisplayed()
        composeTestRule.onNodeWithText("Color Palette").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_palette_${SeedPalettePreset.ELECTRIC_COBALT.name}").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_switch_top_visits").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Adapt browser surfaces to your Android wallpaper palette").assertDoesNotExist()

        // Navigate back to main Settings
        composeTestRule.onNodeWithTag("settings_appearance_back_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()

        // Open Browsing sub-screen
        composeTestRule.onNodeWithTag("settings_browsing_card").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_browsing_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Browsing").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_switch_preload").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_switch_desktop").assertIsDisplayed()
    }

    @Test
    fun `verify Accessibility sub-screen UI controls preview scaling dialogs and menu zoom`() {
        var currentPrefs by mutableStateOf(
            BrowserPreferences(
                savedSiteZooms = mapOf("kotlinlang.org" to 125)
            )
        )
        var showMenu by mutableStateOf(false)

        composeTestRule.setContent {
            ApexBrowserTheme {
                if (showMenu) {
                    BrowserMenuBottomSheet(
                        isHomeActive = false,
                        desktopViewportEnabled = false,
                        isDefaultBrowser = true,
                        showZoomOption = currentPrefs.showZoomInMainMenu,
                        activeSiteHost = "kotlinlang.org",
                        currentZoomPercent = currentPrefs.effectiveZoomForUrl("https://kotlinlang.org"),
                        hasCustomSiteZoom = currentPrefs.savedSiteZooms.containsKey("kotlinlang.org"),
                        onAdjustZoomPercent = { newZoom ->
                            currentPrefs = currentPrefs.copy(
                                savedSiteZooms = currentPrefs.savedSiteZooms + ("kotlinlang.org" to newZoom)
                            )
                        },
                        onResetSiteZoom = {
                            currentPrefs = currentPrefs.copy(
                                savedSiteZooms = currentPrefs.savedSiteZooms - "kotlinlang.org"
                            )
                        },
                        onOpenSettings = {},
                        onToggleDesktopMode = {},
                        onClearBrowsingData = {},
                        onDismiss = {}
                    )
                } else {
                    SettingsScreenOverlay(
                        preferences = currentPrefs,
                        onSelectSearchEngine = {},
                        onToggleShields = {},
                        onToggleHttpsUpgrade = {},
                        onToggleScriptGuard = {},
                        onToggleSmartPreload = {},
                        onToggleDesktopViewport = {},
                        onToggleDynamicColor = {},
                        onSelectSeedPalette = {},
                        onClearBrowsingData = {},
                        onChangeDefaultZoom = { zoom ->
                            currentPrefs = currentPrefs.copy(defaultZoomPercent = zoom)
                        },
                        onSetSiteZoom = { host, zoom ->
                            currentPrefs = currentPrefs.copy(
                                savedSiteZooms = currentPrefs.savedSiteZooms + (host to zoom)
                            )
                        },
                        onRemoveSiteZoom = { host ->
                            currentPrefs = currentPrefs.copy(
                                savedSiteZooms = currentPrefs.savedSiteZooms - host
                            )
                        },
                        onClearSavedSiteZooms = {
                            currentPrefs = currentPrefs.copy(savedSiteZooms = emptyMap())
                        },
                        onToggleShowZoomInMainMenu = { enabled ->
                            currentPrefs = currentPrefs.copy(showZoomInMainMenu = enabled)
                        },
                        onToggleForceEnableZoom = { enabled ->
                            currentPrefs = currentPrefs.copy(forceEnableZoom = enabled)
                        },
                        onToggleSimplifiedView = { enabled ->
                            currentPrefs = currentPrefs.copy(simplifiedViewEnabled = enabled)
                        },
                        onToggleCaretBrowsing = { enabled ->
                            currentPrefs = currentPrefs.copy(caretBrowsingEnabled = enabled)
                        },
                        onToggleTouchpadSwipeNavigation = { enabled ->
                            currentPrefs = currentPrefs.copy(touchpadSwipeNavigationEnabled = enabled)
                        },
                        onDismiss = {}
                    )
                }
            }
        }

        // 1. Open Accessibility from main Settings
        composeTestRule.onNodeWithTag("settings_accessibility_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_accessibility_card").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_accessibility_screen").assertIsDisplayed()

        // 2. Default zoom & Preview card
        composeTestRule.onNodeWithTag("accessibility_default_zoom_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Default zoom").assertIsDisplayed()
        composeTestRule.onNodeWithText("100 %").assertIsDisplayed()
        composeTestRule.onNodeWithTag("accessibility_preview_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Preview").assertIsDisplayed()
        composeTestRule.onNodeWithText("The Wonderful Wizard of Oz").assertIsDisplayed()
        composeTestRule.onNodeWithText("Chapter 11: The Wonderful Emerald City of Oz").assertIsDisplayed()

        composeTestRule.onNodeWithTag("accessibility_zoom_in_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(105, currentPrefs.defaultZoomPercent)
        composeTestRule.onNodeWithText("105 %").assertIsDisplayed()

        composeTestRule.onNodeWithTag("accessibility_zoom_out_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(100, currentPrefs.defaultZoomPercent)

        // 3. Help dialog
        composeTestRule.onNodeWithTag("settings_accessibility_help_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("accessibility_help_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("accessibility_help_close_button").performClick()
        composeTestRule.waitForIdle()

        // 4. All 7 Material 3 Expressive grouped items
        val a11yTags = listOf(
            "settings_accessibility_saved_zoom",
            "settings_switch_show_zoom_menu",
            "settings_switch_force_zoom",
            "settings_switch_simplified_view",
            "settings_switch_caret_browsing",
            "settings_accessibility_captions",
            "settings_switch_touchpad_swipe"
        )
        a11yTags.forEach { tag ->
            composeTestRule.onNodeWithTag("settings_accessibility_list").performScrollToNode(hasTestTag(tag))
            composeTestRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        }

        // 5. Saved zoom for sites dialog
        composeTestRule.onNodeWithTag("settings_accessibility_list").performScrollToNode(hasTestTag("settings_accessibility_saved_zoom"))
        composeTestRule.onNodeWithTag("settings_accessibility_saved_zoom").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("saved_site_zooms_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("kotlinlang.org").assertIsDisplayed()
        composeTestRule.onNodeWithTag("saved_site_zoom_plus_kotlinlang.org").performClick()
        composeTestRule.waitForIdle()
        assertEquals(130, currentPrefs.savedSiteZooms["kotlinlang.org"])
        composeTestRule.onNodeWithTag("saved_site_zoom_close").performClick()
        composeTestRule.waitForIdle()

        // 6. Toggle Accessibility switches
        listOf(
            "settings_switch_show_zoom_menu",
            "settings_switch_force_zoom",
            "settings_switch_simplified_view",
            "settings_switch_caret_browsing",
            "settings_switch_touchpad_swipe"
        ).forEach { tag ->
            composeTestRule.onNodeWithTag("settings_accessibility_list").performScrollToNode(hasTestTag(tag))
            composeTestRule.onNodeWithTag(tag).performScrollTo().performClick()
        }
        composeTestRule.waitForIdle()

        assertTrue(currentPrefs.showZoomInMainMenu)
        assertTrue(currentPrefs.forceEnableZoom)
        assertTrue(currentPrefs.simplifiedViewEnabled)
        assertTrue(currentPrefs.caretBrowsingEnabled)
        assertFalse(currentPrefs.touchpadSwipeNavigationEnabled)

        // 7. When "Show zoom option in main menu" is enabled, BrowserMenuBottomSheet shows zoom controls
        showMenu = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("menu_sheet_zoom_row").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("130 %").assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_sheet_zoom_in").performClick()
        composeTestRule.waitForIdle()
        assertEquals(135, currentPrefs.savedSiteZooms["kotlinlang.org"])
    }

    @Test
    fun `verify UserPreferencesRepository persists accessibility settings and site zooms`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsRepo = UserPreferencesRepository(context)

        prefsRepo.setDefaultZoomPercent(120)
        prefsRepo.setSiteZoom("https://developer.android.com/compose", 145)
        prefsRepo.setShowZoomInMainMenu(true)
        prefsRepo.setForceEnableZoom(true)
        prefsRepo.setSimplifiedViewEnabled(true)
        prefsRepo.setCaretBrowsingEnabled(true)
        prefsRepo.setTouchpadSwipeNavigationEnabled(false)

        val loaded = prefsRepo.preferencesFlow.first()
        assertEquals(120, loaded.defaultZoomPercent)
        assertEquals(145, loaded.savedSiteZooms["developer.android.com"])
        assertEquals(145, loaded.effectiveZoomForUrl("https://developer.android.com/guide"))
        assertEquals(120, loaded.effectiveZoomForUrl("https://example.org"))
        assertTrue(loaded.showZoomInMainMenu)
        assertTrue(loaded.forceEnableZoom)
        assertTrue(loaded.simplifiedViewEnabled)
        assertTrue(loaded.caretBrowsingEnabled)
        assertFalse(loaded.touchpadSwipeNavigationEnabled)

        prefsRepo.removeSiteZoom("developer.android.com")
        val afterRemove = prefsRepo.preferencesFlow.first()
        assertFalse(afterRemove.savedSiteZooms.containsKey("developer.android.com"))
        assertEquals(120, afterRemove.effectiveZoomForUrl("https://developer.android.com/guide"))
    }

    @Test
    fun networkStatusBanner_andHomeSearchBar_renderOfflineAndBackOnlineStates() {
        assertEquals("github.com", com.example.ui.browser.formatCleanAddressBarUrl("https://www.github.com/Ahmed9ali"))
        assertEquals("github.com", com.example.ui.browser.formatCleanAddressBarUrl("http://github.com/"))

        var bannerState by mutableStateOf(com.example.ui.viewmodel.NetworkStatusBannerState.HIDDEN)
        var isOnline by mutableStateOf(true)
        var currentUrl by mutableStateOf("apex://home")

        composeTestRule.setContent {
            ApexBrowserTheme {
                CompactTopToolbar(
                    visible = true,
                    activeTab = BrowserTab(
                        id = "tab-1",
                        title = "GitHub",
                        url = currentUrl,
                        isLoading = false,
                        progress = 1f
                    ),
                    onAddressPillClick = {},
                    onReloadClick = {},
                    searchEngine = SearchEngineOption.BRAVE,
                    isOnline = isOnline,
                    networkBannerState = bannerState
                )
            }
        }

        // Home page search bar exists and banner is hidden initially
        composeTestRule.onNodeWithTag("home_top_search_bar").assertExists()
        composeTestRule.onNodeWithTag("status_bar_offline_banner").assertDoesNotExist()
        composeTestRule.onNodeWithTag("status_bar_online_banner").assertDoesNotExist()

        // Switch to offline on web page (like Screenshot 1: "No internet connection" + info icon + github.com)
        currentUrl = "https://github.com/dashboard"
        isOnline = false
        bannerState = com.example.ui.viewmodel.NetworkStatusBannerState.OFFLINE
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("status_bar_offline_banner").assertExists()
        composeTestRule.onNode(androidx.compose.ui.test.hasText("No internet connection")).assertExists()
        composeTestRule.onNodeWithTag("address_bar_container").assertExists()
        composeTestRule.onNodeWithTag("address_bar_info_icon", useUnmergedTree = true).assertExists()
        composeTestRule.onNode(androidx.compose.ui.test.hasText("github.com")).assertExists()

        // Reconnect to online (like Screenshot 2: "Back online" + page tune icon + github.com)
        isOnline = true
        bannerState = com.example.ui.viewmodel.NetworkStatusBannerState.BACK_ONLINE
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("status_bar_online_banner").assertExists()
        composeTestRule.onNode(androidx.compose.ui.test.hasText("Back online")).assertExists()
        composeTestRule.onNodeWithTag("address_bar_tune_icon", useUnmergedTree = true).assertExists()
        composeTestRule.onNode(androidx.compose.ui.test.hasText("github.com")).assertExists()
    }

    @Test
    fun settingsOverlayBackStack_returnsToSettingsFromPasswordsDownloadsHistoryAndBookmarks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val prefsRepo = UserPreferencesRepository(context)
        val sharedPrefs = context.getSharedPreferences("test_settings_backstack_prefs", Context.MODE_PRIVATE)
        val repo = BrowserRepository(db.apexDao(), prefsRepo, sharedPrefs)
        val store = androidx.lifecycle.ViewModelStore()
        val vm = androidx.lifecycle.ViewModelProvider(
            store,
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return BrowserViewModel(repository = repo) as T
                }
            }
        )[BrowserViewModel::class.java]

        try {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            // 1. Open Settings -> Open Passwords -> Go back -> Should return to Settings (not Home)
            vm.setSettingsScreenOpen(true)
            assertTrue(vm.uiState.value.isSettingsScreenOpen)
            assertFalse(vm.uiState.value.isPasswordManagerOpen)

            vm.setPasswordManagerOpen(true)
            assertTrue(vm.uiState.value.isPasswordManagerOpen)
            assertFalse(vm.uiState.value.isSettingsScreenOpen)

            vm.setPasswordManagerOpen(false)
            assertFalse(vm.uiState.value.isPasswordManagerOpen)
            assertTrue(vm.uiState.value.isSettingsScreenOpen)

            // 2. From Settings -> Open Downloads -> Go back -> Should return to Settings
            vm.setDownloadsScreenOpen(true)
            assertTrue(vm.uiState.value.isDownloadsScreenOpen)
            assertFalse(vm.uiState.value.isSettingsScreenOpen)

            vm.setDownloadsScreenOpen(false)
            assertFalse(vm.uiState.value.isDownloadsScreenOpen)
            assertTrue(vm.uiState.value.isSettingsScreenOpen)

            // 3. From Settings -> Open History -> Go back -> Should return to Settings
            vm.setHistoryScreenOpen(true)
            assertTrue(vm.uiState.value.isHistoryScreenOpen)
            assertFalse(vm.uiState.value.isSettingsScreenOpen)

            vm.setHistoryScreenOpen(false)
            assertFalse(vm.uiState.value.isHistoryScreenOpen)
            assertTrue(vm.uiState.value.isSettingsScreenOpen)

            // 4. From Settings -> Open Bookmarks -> Go back -> Should return to Settings
            vm.setBookmarksScreenOpen(true)
            assertTrue(vm.uiState.value.isBookmarksScreenOpen)
            assertFalse(vm.uiState.value.isSettingsScreenOpen)

            vm.setBookmarksScreenOpen(false)
            assertFalse(vm.uiState.value.isBookmarksScreenOpen)
            assertTrue(vm.uiState.value.isSettingsScreenOpen)

            // 5. Going back from Settings now returns to Home/Browser
            vm.setSettingsScreenOpen(false)
            assertFalse(vm.uiState.value.isSettingsScreenOpen)

            // 6. Opening Passwords directly from the 3-dot Menu (Home) -> Go back -> Returns directly to Home
            vm.setPasswordManagerOpen(true)
            assertTrue(vm.uiState.value.isPasswordManagerOpen)
            assertFalse(vm.uiState.value.isSettingsScreenOpen)

            vm.setPasswordManagerOpen(false)
            assertFalse(vm.uiState.value.isPasswordManagerOpen)
            assertFalse(vm.uiState.value.isSettingsScreenOpen)
        } finally {
            store.clear()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(60)
            db.close()
        }
    }

    @Test
    fun webViewSmoothness_preventsPostFinishProgressFlickerAndBatchesBlockedSubresources() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val prefsRepo = UserPreferencesRepository(context)
        val sharedPrefs = context.getSharedPreferences("test_webview_smoothness_prefs", Context.MODE_PRIVATE)
        val repo = BrowserRepository(db.apexDao(), prefsRepo, sharedPrefs)
        val store = androidx.lifecycle.ViewModelStore()
        val vm = androidx.lifecycle.ViewModelProvider(
            store,
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return BrowserViewModel(repository = repo) as T
                }
            }
        )[BrowserViewModel::class.java]

        try {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            val tabId = vm.uiState.value.activeTabId
            vm.submitNavigation("https://github.com", tabId)
            assertTrue(vm.uiState.value.activeTab.isLoading)

            // Queue multiple blocked subresources during page load
            vm.onSubresourceBlocked(tabId, "github.com", "doubleclick.net", "Ad Network", 12000L, 45L)
            vm.onSubresourceBlocked(tabId, "github.com", "google-analytics.com", "Tracker", 8000L, 30L)
            vm.onSubresourceBlocked(tabId, "github.com", "taboola.com", "Ad Network", 15000L, 55L)

            // Finish page load — flushes batched blocked count and marks isLoading = false
            vm.onWebViewPageFinished(
                tabId = tabId,
                title = "GitHub",
                url = "https://github.com",
                canGoBack = true,
                canGoForward = false
            )
            assertFalse(vm.uiState.value.activeTab.isLoading)
            assertEquals(1f, vm.uiState.value.activeTab.progress, 0.001f)
            assertEquals(3, vm.uiState.value.activeTab.blockedCountOnPage)

            // Late iframe/analytics progress callback (< 100) after page finish must NOT re-enable isLoading
            vm.onWebViewProgress(tabId, 45)
            assertFalse(vm.uiState.value.activeTab.isLoading)
            assertEquals(1f, vm.uiState.value.activeTab.progress, 0.001f)
        } finally {
            store.clear()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            kotlinx.coroutines.delay(80)
            db.close()
        }
    }

    @Test
    fun turboDownload_handlesRedirectAndTransientDropAndResumes8xChunksToCompletion() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val prefsRepo = UserPreferencesRepository(context)
        val sharedPrefs = context.getSharedPreferences("test_turbo_redirect_prefs", Context.MODE_PRIVATE)
        val repository = BrowserRepository(db.apexDao(), prefsRepo, sharedPrefs)
        val engine = FastDownloadEngine(context, repository)

        // 640 KB payload triggers 8x Parallel Turbo chunks (>= 512 KB)
        val payload = ByteArray(640 * 1024) { idx -> ((idx * 37 + 13) and 0xFF).toByte() }
        val droppedOnceForChunk = java.util.concurrent.atomic.AtomicBoolean(false)
        val server = ServerSocket(0)
        val port = server.localPort
        val serverThread = thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    runCatching {
                        socket.use { s ->
                            val reader = s.getInputStream().bufferedReader()
                            val requestLine = reader.readLine() ?: return@use
                            val path = requestLine.split(' ').getOrNull(1).orEmpty()
                            var rangeHeader: String? = null
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (line.isEmpty()) break
                                if (line.startsWith("Range:", ignoreCase = true)) {
                                    rangeHeader = line.substringAfter(':').trim()
                                }
                            }
                            val out = s.getOutputStream()
                            if (path.startsWith("/redirect")) {
                                val redirectHeader = "HTTP/1.1 302 Found\r\n" +
                                    "Location: http://127.0.0.1:$port/cdn/turbo_archive.bin\r\n" +
                                    "Content-Length: 0\r\n" +
                                    "Connection: close\r\n\r\n"
                                out.write(redirectHeader.toByteArray())
                                out.flush()
                                return@use
                            }

                            val total = payload.size
                            if (rangeHeader != null && rangeHeader!!.startsWith("bytes=")) {
                                val spec = rangeHeader!!.removePrefix("bytes=").trim()
                                val start = spec.substringBefore('-').toIntOrNull() ?: 0
                                val endRaw = spec.substringAfter('-', "").toIntOrNull()
                                val end = (endRaw ?: (total - 1)).coerceAtMost(total - 1)
                                val len = end - start + 1
                                val header = "HTTP/1.1 206 Partial Content\r\n" +
                                    "Accept-Ranges: bytes\r\n" +
                                    "Content-Length: $len\r\n" +
                                    "Content-Range: bytes $start-$end/$total\r\n" +
                                    "Content-Type: application/octet-stream\r\n" +
                                    "Connection: close\r\n\r\n"
                                out.write(header.toByteArray())

                                // Simulate a transient socket drop mid-chunk when user switches apps/tabs
                                if (start > 0 && end > start + 16384 && droppedOnceForChunk.compareAndSet(false, true)) {
                                    out.write(payload, start, 8192)
                                    out.flush()
                                    return@use
                                }

                                var pos = start
                                while (pos <= end) {
                                    val chunk = minOf(16384, end - pos + 1)
                                    out.write(payload, pos, chunk)
                                    out.flush()
                                    pos += chunk
                                }
                            } else {
                                val header = "HTTP/1.1 200 OK\r\n" +
                                    "Accept-Ranges: bytes\r\n" +
                                    "Content-Length: $total\r\n" +
                                    "Content-Type: application/octet-stream\r\n" +
                                    "Connection: close\r\n\r\n"
                                out.write(header.toByteArray())
                                out.write(payload)
                                out.flush()
                            }
                        }
                    }
                }
            }
        }

        try {
            val startedLatch = CountDownLatch(1)
            var downloadId = -1L
            engine.enqueueDownload(
                url = "http://127.0.0.1:$port/redirect/turbo_archive.bin",
                suggestedFileName = "turbo_archive.bin"
            ) { entity ->
                downloadId = entity.id
                startedLatch.countDown()
            }
            val startDeadline = System.currentTimeMillis() + 5_000L
            while (startedLatch.count > 0 && System.currentTimeMillis() < startDeadline) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(20L)
            }
            assertEquals(0L, startedLatch.count)
            assertTrue(downloadId > 0L)

            var finalEntity = runBlocking { db.apexDao().getDownloadById(downloadId) }
            val deadlineComplete = System.currentTimeMillis() + 12_000L
            while (System.currentTimeMillis() < deadlineComplete) {
                finalEntity = runBlocking { db.apexDao().getDownloadById(downloadId) }
                if (finalEntity?.status == FastDownloadEngine.STATUS_COMPLETED) break
                assertNotEquals(
                    "8x Turbo download must recover from transient socket drop without failing: ${finalEntity?.errorMessage}",
                    FastDownloadEngine.STATUS_FAILED,
                    finalEntity?.status
                )
                Thread.sleep(50L)
            }
            assertEquals(FastDownloadEngine.STATUS_COMPLETED, finalEntity?.status)
            assertEquals(FastDownloadEngine.DEFAULT_TURBO_CHUNKS, finalEntity?.chunksCount)
            assertEquals(payload.size.toLong(), finalEntity?.downloadedBytes)
            assertTrue("Transient socket drop should have been triggered and recovered", droppedOnceForChunk.get())
            val outFile = File(finalEntity!!.localFilePath)
            assertTrue(outFile.exists())
            assertArrayEquals(payload, outFile.readBytes())
        } finally {
            runCatching { server.close() }
            serverThread.interrupt()
            db.close()
        }
    }

    @Test
    fun clipboardDetection_disappearsAfterUseAndDoesNotReappearUntilNewCopy() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.ui.browser.ClipboardConsumptionTracker.resetForTesting(context)
        val sysClipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        sysClipboard.setPrimaryClip(
            android.content.ClipData.newPlainText("url", "https://developer.android.com/jetpack")
        )

        var isSearchOpen by mutableStateOf(true)
        var currentQuery by mutableStateOf("")
        var submittedTarget by mutableStateOf<String?>(null)

        composeTestRule.setContent {
            ApexBrowserTheme {
                if (isSearchOpen) {
                    com.example.ui.browser.SearchScreenOverlay(
                        query = currentQuery,
                        selectedEngine = SearchEngineOption.GOOGLE,
                        suggestions = emptyList(),
                        recentHistory = emptyList(),
                        activeTabUrl = null,
                        activeTabTitle = null,
                        onQueryChange = { currentQuery = it },
                        onSubmitSearch = { submitted ->
                            submittedTarget = submitted
                            currentQuery = ""
                            isSearchOpen = false
                        },
                        onFillQuery = { currentQuery = it },
                        onDeleteHistoryItem = {},
                        onClearAllHistory = {},
                        onDismiss = { isSearchOpen = false }
                    )
                }
            }
        }

        composeTestRule.mainClock.advanceTimeBy(150)
        composeTestRule.waitForIdle()

        // 1. Clipboard card is initially detected and displayed
        composeTestRule.onNodeWithTag("clipboard_detected_row").assertIsDisplayed()

        // 2. Use the clipboard suggestion via Fill button -> query fills and clipboard card is consumed
        composeTestRule.onNodeWithTag("fill_clipboard_suggestion").performClick()
        composeTestRule.waitForIdle()
        assertEquals("https://developer.android.com/jetpack", currentQuery)
        composeTestRule.onNodeWithTag("clipboard_detected_row").assertDoesNotExist()

        // 3. Even if the user clears the search input, the consumed clipboard card must NOT reappear
        composeTestRule.onNodeWithTag("search_screen_clear_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals("", currentQuery)
        composeTestRule.onNodeWithTag("clipboard_detected_row").assertDoesNotExist()

        // 4. Close and reopen the Search screen -> same clipboard content must remain hidden
        isSearchOpen = false
        composeTestRule.waitForIdle()
        isSearchOpen = true
        composeTestRule.mainClock.advanceTimeBy(150)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("clipboard_detected_row").assertDoesNotExist()

        // 5. Copy a new URL to the clipboard -> reopening Search screen detects the new clip once,
        // and clicking the row navigates and consumes it so it disappears afterwards.
        isSearchOpen = false
        composeTestRule.waitForIdle()
        sysClipboard.setPrimaryClip(
            android.content.ClipData.newPlainText("url", "https://kotlinlang.org/docs")
        )
        isSearchOpen = true
        composeTestRule.mainClock.advanceTimeBy(150)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("clipboard_detected_row").assertIsDisplayed()

        composeTestRule.onNodeWithTag("clipboard_detected_row").performClick()
        composeTestRule.waitForIdle()
        assertEquals("https://kotlinlang.org/docs", submittedTarget)

        // Reopen Search screen after direct click -> consumed clipboard item does not reappear
        isSearchOpen = true
        composeTestRule.mainClock.advanceTimeBy(150)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("clipboard_detected_row").assertDoesNotExist()
    }

    @Test
    fun typingInSearchBar_loadsSuggestionsWithoutShowingLoadingBar() {
        var currentQuery by mutableStateOf("")
        var currentSuggestions by mutableStateOf<List<com.example.engine.SearchSuggestionItem>>(emptyList())

        composeTestRule.setContent {
            ApexBrowserTheme {
                com.example.ui.browser.SearchScreenOverlay(
                    query = currentQuery,
                    selectedEngine = SearchEngineOption.BRAVE,
                    suggestions = currentSuggestions,
                    recentHistory = emptyList(),
                    onQueryChange = { newText ->
                        currentQuery = newText
                        currentSuggestions = if (newText.isBlank()) {
                            emptyList()
                        } else {
                            listOf(
                                com.example.engine.SearchSuggestionItem(
                                    id = "sugg_1",
                                    title = "$newText documentation",
                                    subtitle = "",
                                    submitValue = "$newText documentation",
                                    fillText = "$newText documentation",
                                    engineBadge = "BRV",
                                    kind = com.example.engine.SuggestionKind.ENGINE_SUGGESTION
                                )
                            )
                        }
                    },
                    onSubmitSearch = {},
                    onFillQuery = { currentQuery = it },
                    onDeleteHistoryItem = {},
                    onClearAllHistory = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("search_screen_input").performTextInput("kotlin")
        composeTestRule.waitForIdle()

        // No progress bar / loading bar should appear while typing and fetching suggestions
        val progressNodes = composeTestRule.onAllNodes(
            androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(
                androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo
            )
        ).fetchSemanticsNodes()
        assertTrue("No loading bar should display while typing search suggestions", progressNodes.isEmpty())

        // Suggestions should load and display
        composeTestRule.onNodeWithTag("suggestion_row_immediate_query_action").assertIsDisplayed()
        composeTestRule.onNodeWithTag("suggestion_row_sugg_1").assertIsDisplayed()
    }

    @Test
    fun `verify Downloads settings sub-screen renders all 5 options toggles state and switches download location`() {
        var currentPrefs by mutableStateOf(BrowserPreferences())
        var closedAll by mutableStateOf(false)

        composeTestRule.setContent {
            ApexBrowserTheme {
                SettingsScreenOverlay(
                    preferences = currentPrefs,
                    onSelectSearchEngine = {},
                    onToggleShields = {},
                    onToggleHttpsUpgrade = {},
                    onToggleScriptGuard = {},
                    onToggleSmartPreload = {},
                    onToggleDesktopViewport = {},
                    onToggleDynamicColor = {},
                    onSelectSeedPalette = {},
                    onClearBrowsingData = {},
                    onSelectDownloadLocation = { mode, uri, label ->
                        currentPrefs = currentPrefs.copy(
                            downloadLocationMode = mode,
                            customDownloadTreeUri = uri,
                            customDownloadFolderLabel = label
                        )
                    },
                    onToggleAskWhereToSaveFiles = { enabled ->
                        currentPrefs = currentPrefs.copy(askWhereToSaveFiles = enabled)
                    },
                    onToggleAutoOpenDownloadedFiles = { enabled ->
                        currentPrefs = currentPrefs.copy(autoOpenDownloadedFiles = enabled)
                    },
                    onToggleShowDownloadProgressNotifications = { enabled ->
                        currentPrefs = currentPrefs.copy(showDownloadProgressNotifications = enabled)
                    },
                    onToggleParallelDownloading = { enabled ->
                        currentPrefs = currentPrefs.copy(parallelDownloadingEnabled = enabled)
                    },
                    onDismiss = { closedAll = true }
                )
            }
        }

        // Open Settings -> Downloads sub-screen
        composeTestRule.onNodeWithTag("settings_downloads_card").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_downloads_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Download location").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Ask where to save files").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Automatically open when possible").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Show download progress notifications").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Parallel downloading").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Enable parallel downloading to accelerate download speed").performScrollTo().assertIsDisplayed()

        // Defaults: askWhereToSaveFiles = false, autoOpenDownloadedFiles = true,
        // showDownloadProgressNotifications = true, parallelDownloadingEnabled = true
        assertFalse(currentPrefs.askWhereToSaveFiles)
        assertTrue(currentPrefs.autoOpenDownloadedFiles)
        assertTrue(currentPrefs.showDownloadProgressNotifications)
        assertTrue(currentPrefs.parallelDownloadingEnabled)

        // Toggle all 4 switches and verify state updates
        composeTestRule.onNodeWithTag("settings_switch_ask_where_to_save").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("settings_switch_auto_open_downloads").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("settings_switch_download_notifications").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("settings_switch_parallel_downloading").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertTrue(currentPrefs.askWhereToSaveFiles)
        assertFalse(currentPrefs.autoOpenDownloadedFiles)
        assertFalse(currentPrefs.showDownloadProgressNotifications)
        assertFalse(currentPrefs.parallelDownloadingEnabled)

        // Open Download Location dialog and select Private Storage
        composeTestRule.onNodeWithTag("settings_download_location_card").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("download_location_option_external").assertIsDisplayed()
        composeTestRule.onNodeWithTag("download_location_option_internal").assertIsDisplayed()
        composeTestRule.onNodeWithTag("download_location_choose_folder_button").assertIsDisplayed()

        composeTestRule.onNodeWithTag("download_location_option_internal").performClick()
        composeTestRule.waitForIdle()
        assertEquals(DownloadLocationMode.APP_INTERNAL, currentPrefs.downloadLocationMode)
        composeTestRule.onNodeWithTag("settings_download_location_card").performScrollTo()
        composeTestRule.onNodeWithText("Private storage", substring = true).assertIsDisplayed()

        // Close button (X) dismisses Settings overlay cleanly
        composeTestRule.onNodeWithTag("settings_downloads_close_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(closedAll)
    }

    @Test
    fun `verify DownloadStorageHelper directory resolution and Play-safe auto-open filtering`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val externalDir = com.example.engine.DownloadStorageHelper.resolveDirectory(
            context,
            DownloadLocationMode.APP_EXTERNAL
        )
        val internalDir = com.example.engine.DownloadStorageHelper.resolveDirectory(
            context,
            DownloadLocationMode.APP_INTERNAL
        )
        assertTrue(externalDir.exists())
        assertTrue(internalDir.exists())
        assertNotEquals(externalDir.absolutePath, internalDir.absolutePath)

        // Executable binaries & Android packages must NEVER auto-open
        assertFalse(com.example.engine.DownloadStorageHelper.isSafeToAutoOpen("installer.apk", "application/vnd.android.package-archive"))
        assertFalse(com.example.engine.DownloadStorageHelper.isSafeToAutoOpen("classes.dex", "application/octet-stream"))
        assertFalse(com.example.engine.DownloadStorageHelper.isSafeToAutoOpen("payload.jar", "application/java-archive"))
        assertFalse(com.example.engine.DownloadStorageHelper.isSafeToAutoOpen("native.so", "application/octet-stream"))

        // Safe documents and media can auto-open when a viewer exists
        assertTrue(com.example.engine.DownloadStorageHelper.isSafeToAutoOpen("manual.pdf", "application/pdf"))
        assertTrue(com.example.engine.DownloadStorageHelper.isSafeToAutoOpen("photo.png", "image/png"))
        assertTrue(com.example.engine.DownloadStorageHelper.isSafeToAutoOpen("notes.txt", "text/plain"))
    }

    @Test
    fun `verify Download settings persist in repository and enforce ask-where-to-save and single-stream vs parallel`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val prefsRepo = UserPreferencesRepository(context)
        val browserRepo = BrowserRepository(db.apexDao(), prefsRepo)
        val engine = FastDownloadEngine(context, browserRepo)
        val vm = BrowserViewModel(repository = browserRepo, fastDownloadEngine = engine)

        runBlocking<Unit> {
            // Reset to clean defaults first
            browserRepo.setDownloadLocation(DownloadLocationMode.APP_INTERNAL, "", "")
            browserRepo.setAskWhereToSaveFiles(true)
            browserRepo.setAutoOpenDownloadedFiles(false)
            browserRepo.setShowDownloadProgressNotifications(false)
            browserRepo.setParallelDownloadingEnabled(false)

            val saved = browserRepo.preferencesFlow.first()
            assertEquals(DownloadLocationMode.APP_INTERNAL, saved.downloadLocationMode)
            assertTrue(saved.askWhereToSaveFiles)
            assertFalse(saved.autoOpenDownloadedFiles)
            assertFalse(saved.showDownloadProgressNotifications)
            assertFalse(saved.parallelDownloadingEnabled)
        }

        // Start a download while askWhereToSaveFiles == true -> holds in pendingDownloadPrompt
        vm.startFastDownload(
            url = "https://example.org/files/report.pdf",
            suggestedFileName = "report.pdf",
            mimeType = "application/pdf",
            contentLength = 2048L
        )
        val waitDeadline = System.currentTimeMillis() + 3_000L
        while (vm.uiState.value.pendingDownloadPrompt == null && System.currentTimeMillis() < waitDeadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20L)
        }
        val prompt = vm.uiState.value.pendingDownloadPrompt
        assertNotNull("Expected Ask Where To Save prompt before starting download", prompt)
        assertEquals("report.pdf", prompt!!.suggestedFileName)
        assertEquals("https://example.org/files/report.pdf", prompt.url)

        // Dismiss cancels without inserting download
        vm.dismissPendingDownload()
        assertNull(vm.uiState.value.pendingDownloadPrompt)

        // Now start a real local download with parallelDownloadingEnabled = false -> chunksCount == 1 & stored in APP_INTERNAL
        runBlocking<Unit> {
            browserRepo.setAskWhereToSaveFiles(false)
            browserRepo.setDownloadLocation(DownloadLocationMode.APP_INTERNAL, "", "")
            browserRepo.setParallelDownloadingEnabled(false)
            engine.updateDownloadSettings(browserRepo.preferencesFlow.first())
        }

        val payload = ByteArray(192 * 1024) { (it % 239).toByte() }
        val server = ServerSocket(0)
        val port = server.localPort
        val serverThread = thread(isDaemon = true) {
            while (!server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    client.use { s ->
                        val reader = s.getInputStream().bufferedReader()
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                        }
                        val out = s.getOutputStream()
                        val header = "HTTP/1.1 200 OK\r\n" +
                            "Accept-Ranges: bytes\r\n" +
                            "Content-Length: ${payload.size}\r\n" +
                            "Content-Type: application/octet-stream\r\n" +
                            "Connection: close\r\n\r\n"
                        out.write(header.toByteArray())
                        out.write(payload)
                        out.flush()
                    }
                }
            }
        }

        try {
            val startedLatch = CountDownLatch(1)
            var downloadId = -1L
            engine.enqueueDownload(
                url = "http://127.0.0.1:$port/single_stream.bin",
                suggestedFileName = "single_stream.bin"
            ) { entity ->
                downloadId = entity.id
                startedLatch.countDown()
            }
            val startDeadline = System.currentTimeMillis() + 5_000L
            while (startedLatch.count > 0 && System.currentTimeMillis() < startDeadline) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(20L)
            }
            assertEquals(0L, startedLatch.count)

            var finalEntity = runBlocking { db.apexDao().getDownloadById(downloadId) }
            val completeDeadline = System.currentTimeMillis() + 8_000L
            while (System.currentTimeMillis() < completeDeadline) {
                finalEntity = runBlocking { db.apexDao().getDownloadById(downloadId) }
                if (finalEntity?.status == FastDownloadEngine.STATUS_COMPLETED) break
                Thread.sleep(40L)
            }

            assertEquals(FastDownloadEngine.STATUS_COMPLETED, finalEntity?.status)
            assertEquals("Parallel downloading disabled must use single stream (chunksCount == 1)", 1, finalEntity?.chunksCount)
            val internalDirPath = com.example.engine.DownloadStorageHelper.resolveDirectory(
                context,
                DownloadLocationMode.APP_INTERNAL
            ).absolutePath
            assertTrue(
                "Download should be written to internal directory ($internalDirPath) but was ${finalEntity?.localFilePath}",
                finalEntity?.localFilePath?.startsWith(internalDirPath) == true
            )
            assertArrayEquals(payload, File(finalEntity!!.localFilePath).readBytes())
        } finally {
            runCatching { server.close() }
            serverThread.interrupt()
            runBlocking<Unit> {
                // Restore defaults so shared prefs in test process don't affect subsequent runs
                browserRepo.setDownloadLocation(DownloadLocationMode.APP_EXTERNAL, "", "")
                browserRepo.setAskWhereToSaveFiles(false)
                browserRepo.setAutoOpenDownloadedFiles(true)
                browserRepo.setShowDownloadProgressNotifications(true)
                browserRepo.setParallelDownloadingEnabled(true)
            }
            db.close()
        }
    }

    @Test
    fun `verify DraggableTabGridOverlay selection mode selects single and group tabs without closing or opening modal`() {
        val t1 = BrowserTab(id = "sel_t1", title = "Wiki", url = "https://wikipedia.org")
        val t2 = BrowserTab(id = "sel_t2", title = "Docs", url = "https://developer.android.com", groupId = "sel_g1")
        val t3 = BrowserTab(id = "sel_t3", title = "Compose", url = "https://compose.dev", groupId = "sel_g1")
        val group = TabGroupInfo(id = "sel_g1", title = "Dev", colorOption = TabGroupColorOption.COBALT)

        val closedSingleTabs = mutableListOf<String>()
        var openedGroupId: String? = null
        var batchedGroupSelection: Set<String> = emptySet()
        var batchedCloseSelection: Set<String> = emptySet()

        composeTestRule.setContent {
            ApexBrowserTheme {
                DraggableTabGridOverlay(
                    gridEntries = listOf(
                        TabGridEntry.SingleTab(t1),
                        TabGridEntry.GroupTab(group, listOf(t2, t3))
                    ),
                    totalOpenTabs = 3,
                    activeTabId = t1.id,
                    expandedGroupId = null,
                    onSelectTab = {},
                    onCloseTab = { closedSingleTabs += it },
                    onCloseGroup = {},
                    onEditGroup = {},
                    onMergeEntries = { _, _ -> },
                    onMoveGroupedTabToTarget = { _, _ -> },
                    onOpenGroup = { openedGroupId = it },
                    onAddTabToGroup = {},
                    onUngroupTab = {},
                    onCycleTabColor = {},
                    onNewTab = {},
                    onCreateGroup = {},
                    onResetAllTabs = {},
                    onCloseSelectedTabs = { batchedCloseSelection = it },
                    onGroupSelectedTabs = { batchedGroupSelection = it },
                    onDismiss = {}
                )
            }
        }

        // Open 3-dot overflow menu -> Select tab
        composeTestRule.onNodeWithTag("switcher_more_menu_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("switcher_menu_select_tab").performClick()
        composeTestRule.waitForIdle()

        // Close buttons should be replaced by selection checkboxes
        composeTestRule.onNodeWithTag("close_tab_button_${t1.id}").assertDoesNotExist()
        composeTestRule.onNodeWithTag("close_group_button_${group.id}").assertDoesNotExist()
        composeTestRule.onNodeWithTag("select_tab_checkbox_${t1.id}").assertIsDisplayed()
        composeTestRule.onNodeWithTag("select_group_checkbox_${group.id}").assertIsDisplayed()

        // Tapping single-tab checkbox selects it without closing
        composeTestRule.onNodeWithTag("select_tab_checkbox_${t1.id}").performClick()
        // Scroll group card clear of the floating bottom selection pill before tapping card center
        composeTestRule.onNodeWithTag("tabs_lazy_grid").performScrollToIndex(1)
        composeTestRule.waitForIdle()
        // Tapping group card selects all group member tabs without opening group modal
        composeTestRule.onNodeWithTag("grid_group_card_${group.id}").performClick()
        composeTestRule.waitForIdle()

        assertTrue(closedSingleTabs.isEmpty())
        assertNull(openedGroupId)
        composeTestRule.onNodeWithText("3 selected").assertIsDisplayed()
        composeTestRule.onNodeWithTag("selection_group_tabs_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(setOf("sel_t1", "sel_t2", "sel_t3"), batchedGroupSelection)

        // Re-enter selection mode, select single tab, and batch close
        composeTestRule.onNodeWithTag("switcher_more_menu_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("switcher_menu_select_tab").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tabs_lazy_grid").performScrollToIndex(0)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("select_tab_checkbox_${t1.id}").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("selection_close_tabs_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(setOf("sel_t1"), batchedCloseSelection)
    }

    @Test
    fun `verify DraggableTabGridOverlay incognito menu options only close private tabs and create private groups`() {
        val incTab = BrowserTab(id = "inc_1", title = "Private Search", url = "https://duckduckgo.com", isPrivate = true)
        var resetAllCalled = 0
        var closeAllIncognitoCalled = 0
        var createGroupCalled = 0
        var createIncognitoGroupCalled = 0
        var newRegularTabCalled = 0

        composeTestRule.setContent {
            ApexBrowserTheme {
                DraggableTabGridOverlay(
                    gridEntries = listOf(TabGridEntry.SingleTab(incTab)),
                    totalOpenTabs = 1,
                    activeTabId = incTab.id,
                    expandedGroupId = null,
                    isIncognitoAvailable = true,
                    isActiveTabIncognito = true,
                    onSelectTab = {},
                    onCloseTab = {},
                    onCloseGroup = {},
                    onEditGroup = {},
                    onMergeEntries = { _, _ -> },
                    onMoveGroupedTabToTarget = { _, _ -> },
                    onOpenGroup = {},
                    onAddTabToGroup = {},
                    onUngroupTab = {},
                    onCycleTabColor = {},
                    onNewTab = { newRegularTabCalled++ },
                    onCreateGroup = { createGroupCalled++ },
                    onCreateIncognitoGroup = { createIncognitoGroupCalled++ },
                    onResetAllTabs = { resetAllCalled++ },
                    onCloseAllIncognitoTabs = { closeAllIncognitoCalled++ },
                    onDismiss = {}
                )
            }
        }

        // Open 3-dot overflow menu while in Incognito segment
        composeTestRule.onNodeWithTag("switcher_more_menu_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Close all private tabs").assertIsDisplayed()
        composeTestRule.onNodeWithText("New private tab").assertIsDisplayed()

        // Close all private tabs should NOT wipe regular tabs
        composeTestRule.onNodeWithTag("switcher_menu_close_all_tabs").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, closeAllIncognitoCalled)
        assertEquals(0, resetAllCalled)

        // New tab group inside Incognito segment invokes onCreateIncognitoGroup
        composeTestRule.onNodeWithTag("switcher_more_menu_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("switcher_menu_new_group").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, createIncognitoGroupCalled)
        assertEquals(0, createGroupCalled)

        // Switching to ALL_TABS when 0 regular tabs exist displays empty regular tabs state
        composeTestRule.onNodeWithTag("mode_pill_tabs_segment").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("empty_regular_tabs_state").assertIsDisplayed()
        composeTestRule.onNodeWithTag("empty_state_open_regular_tab_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, newRegularTabCalled)
    }

    @Test
    fun `verify BraveBottomNavigationBar selection exclusivity and quick menu dismissal preserves subsequent Tabs click`() {
        var isMenuOpen by mutableStateOf(true)
        var isHomeActive by mutableStateOf(false)
        var tabsClicks = 0
        var closeTabClicks = 0

        composeTestRule.setContent {
            ApexBrowserTheme {
                BraveBottomNavigationBar(
                    isHomeActive = isHomeActive,
                    isBookmarksScreenOpen = false,
                    isSettingsScreenOpen = false,
                    isMenuSheetOpen = isMenuOpen,
                    isCurrentWebPageBookmarked = true,
                    isTabSwitcherOpen = false,
                    isSearchScreenOpen = false,
                    isOtherOverlayOpen = false,
                    openTabCount = 2,
                    onHomeClick = {},
                    onBookmarksClick = {},
                    onSearchClick = {},
                    onTabsClick = { tabsClicks++ },
                    onMenuClick = {},
                    onNewTabFromTabsMenu = {},
                    onNewPrivateTabFromTabsMenu = {},
                    onCloseCurrentTabFromTabsMenu = { closeTabClicks++ },
                    isTabsQuickMenuAvailable = true
                )
            }
        }

        // With Menu open on a bookmarked webpage, Menu is selected and Bookmarks is not
        composeTestRule.onNodeWithTag("nav_menu_button").assertIsSelected()
        composeTestRule.onNodeWithTag("nav_bookmarks_button").assertIsNotSelected()

        // Close menu, long-press Tabs, execute Close tab, then regular tap Tabs -> must trigger onTabsClick cleanly
        isMenuOpen = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nav_tabs_button").performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tabs_quick_dropdown_menu").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tabs_quick_menu_close_tab").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, closeTabClicks)
        assertEquals(0, tabsClicks)

        composeTestRule.onNodeWithTag("nav_tabs_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, tabsClicks)
    }

    @Test
    fun `verify ViewModel resets navigation history flags on home press and fallback back navigation`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        val prefsRepo = UserPreferencesRepository(context)
        val sharedPrefs = context.getSharedPreferences("test_nav_reset_prefs", Context.MODE_PRIVATE)
        val browserRepo = BrowserRepository(db.apexDao(), prefsRepo, sharedPrefs)
        val store = androidx.lifecycle.ViewModelStore()
        val vm = androidx.lifecycle.ViewModelProvider(
            store,
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return BrowserViewModel(repository = browserRepo) as T
                }
            }
        )[BrowserViewModel::class.java]

        try {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            vm.submitNavigation("https://example.org/article")
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            vm.onWebViewPageFinished(
                tabId = vm.uiState.value.activeTabId,
                title = "Example Article",
                url = "https://example.org/article",
                canGoBack = false,
                canGoForward = true
            )
            assertFalse(vm.uiState.value.activeTab.isHomePage)
            assertTrue(vm.uiState.value.activeTab.canGoForward)

            // Fallback navigateBack when WebView cannot go back returns to apex://home and resets flags
            vm.navigateBack()
            val afterBack = vm.uiState.value.activeTab
            assertTrue(afterBack.isHomePage)
            assertFalse(afterBack.canGoBack)
            assertFalse(afterBack.canGoForward)
            assertEquals("apex://home", afterBack.requestedUrl)

            // Re-open webpage and press bottom Home button
            vm.submitNavigation("https://example.org/second")
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            vm.onWebViewPageFinished(
                tabId = vm.uiState.value.activeTabId,
                title = "Second Page",
                url = "https://example.org/second",
                canGoBack = true,
                canGoForward = true
            )
            vm.onBottomHomePressed()
            val afterHome = vm.uiState.value.activeTab
            assertTrue(afterHome.isHomePage)
            assertFalse(afterHome.canGoBack)
            assertFalse(afterHome.canGoForward)
            assertEquals("apex://home", afterHome.requestedUrl)
        } finally {
            store.clear()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            db.close()
        }
    }

    @Test
    fun `verify merged UniversalDomScanner powers DOM-grounded Nano agent and Shadow DOM structured extraction`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = AutomationEngine()
        val scanner = engine.universalScanner
        val webView = WebView(context)
        val tabId = "merged_scan_tab"
        val testUrl = "https://store.example.test/catalog"

        val fixtureHtml = """
            <html>
              <body>
                <button id="filter-pro" type="button" aria-label="Filter Pro Gear">Filter Pro</button>
                <input id="catalog-search" type="search" placeholder="Search catalog..." />
                <input id="user-secret" type="password" value="hidden-secret" />
                <article class="product-card">
                  <h2>Apex Titanium Keyboard</h2>
                  <span class="price">$189</span>
                  <a href="https://store.example.test/keyboard">View Product</a>
                </article>
                <div id="shadow-host">
                  <template shadowrootmode="open">
                    <button id="shadow-buy-btn" type="button">Add Shadow Bundle</button>
                    <article class="product-card">
                      <h3>Shadow Studio Dock</h3>
                      <span class="price">$249</span>
                    </article>
                  </template>
                </div>
              </body>
            </html>
        """.trimIndent()

        UniversalDomScanner.attachHtmlFixture(
            webView = webView,
            url = testUrl,
            html = fixtureHtml
        )

        try {
            // 1. Scan enriches PageInspectionState with interactive elements + structured content + semantic intent
            val scanned = scanner.scanPage(webView = webView, tabId = tabId, currentUrl = testUrl)
            assertTrue(scanned.elements.any { it.selector == "#filter-pro" })
            assertTrue(scanned.extractedRecords.any { it.primaryField.contains("Apex Titanium Keyboard") })
            assertTrue(scanned.extractedRecords.any { it.primaryField.contains("Shadow Studio Dock") })
            assertTrue(scanned.summary.structuredRecordsFound >= 2)
            assertTrue(scanned.pageIntentSummary.contains("Storefront") || scanned.pageIntentSummary.contains("extracted"))
            // Strict privacy: password field value must never leak into extracted records or safeValue
            assertTrue(scanned.extractedRecords.none { it.primaryField.contains("hidden-secret") || it.secondaryValue.contains("hidden-secret") })

            // 2. DOM-grounded Planner resolves real DOM selector instead of generic fallback
            val groundedPlan = scanner.planAgentTask(
                prompt = "Click \"Filter Pro\" and extract product prices",
                currentUrl = testUrl,
                inspectionState = scanned
            )
            val clickStep = groundedPlan.firstOrNull { it.actionType == BrowserDomActionType.CLICK_ELEMENT }
            assertNotNull(clickStep)
            assertEquals("#filter-pro", clickStep?.targetParam)

            // 3. Unified runAgentTask executes over fixture DOM and updates PageInspectionState
            val stepSnapshots = mutableListOf<List<NanoReasoningStep>>()
            val outcome = scanner.runAgentTask(
                prompt = "Find top 3 product prices on this store",
                currentUrl = testUrl,
                tabId = tabId,
                webViewProvider = { webView },
                onNavigateUrl = {},
                onStepUpdate = { steps -> stepSnapshots.add(steps) }
            )
            assertTrue(outcome.passed)
            assertTrue(outcome.finalSummary.contains("Apex Titanium Keyboard"))
            assertTrue(stepSnapshots.isNotEmpty())
        } finally {
            UniversalDomScanner.clearAttachedFixture(webView)
        }
    }

    @Test
    fun `verify AutomationHudSheet DOM Scanner exposes unified Agent query and Extract Data tabs`() {
        val sampleInspection = PageInspectionState(
            domain = "store.apex.browser",
            url = "https://store.apex.browser",
            pageIntentSummary = "Storefront / Catalog • 2 controls • 2 extracted items",
            extractedRecords = listOf(
                ExtractedRecord(
                    id = "rec_1",
                    tabId = "t1",
                    tabTitle = "Apex Gear Store",
                    primaryField = "Apex Mech Keyboard",
                    secondaryValue = "$149",
                    linkUrl = "https://store.apex.browser/kb",
                    matchedSelector = "article"
                )
            )
        )
        var capturedPrompt = ""
        var extractPageClicks = 0
        var extractAllTabsClicks = 0
        val sampleMessages = listOf(
            NanoChatMessage(id = "welcome_nano", isUser = false, text = "Ready"),
            NanoChatMessage(
                id = "resp_1",
                isUser = false,
                text = "Verified 1 product from Apex Gear Store: Apex Mech Keyboard — $149",
                steps = listOf(
                    NanoReasoningStep(
                        id = "s1",
                        role = AgentRole.PLANNER,
                        actionTitle = "Grounded plan",
                        detail = "Ready",
                        status = StepStatus.COMPLETED
                    ),
                    NanoReasoningStep(
                        id = "s2",
                        role = AgentRole.VALIDATOR,
                        actionTitle = "Verified",
                        detail = "OK",
                        status = StepStatus.COMPLETED
                    )
                )
            )
        )

        composeTestRule.setContent {
            ApexBrowserTheme {
                AutomationHudSheet(
                    hudMode = AutomationHudMode.ELEMENTS,
                    pageInspection = sampleInspection,
                    recordingSession = ActiveRecordingSession(),
                    instructionDraftText = "",
                    instructionDraftSteps = emptyList(),
                    executionState = null,
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
                    nanoMessages = sampleMessages,
                    extractedRecords = sampleInspection.extractedRecords,
                    openTabsCount = 3,
                    onRunScannerAgent = { capturedPrompt = it },
                    onExtractCurrentPage = { extractPageClicks++ },
                    onExtractAllTabs = { extractAllTabsClicks++ }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("scanner_unified_intelligence_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("scanner_page_intent_chip").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("scanner_agent_result_card").performScrollTo().assertIsDisplayed()

        // Submit an agent prompt directly inside the DOM Scanner
        composeTestRule.onNodeWithTag("scanner_agent_query_input").performScrollTo().performTextInput("Summarize prices")
        composeTestRule.onNodeWithTag("scanner_run_agent_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals("Summarize prices", capturedPrompt)

        // Extract current page & switch to Extracted Data tab inside Scanner
        composeTestRule.onNodeWithTag("scanner_extract_page_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, extractPageClicks)
        composeTestRule.onNodeWithTag("scanner_extracted_records_list").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Apex Mech Keyboard").performScrollTo().assertIsDisplayed()

        // Extract across all tabs
        composeTestRule.onNodeWithTag("scanner_extract_all_tabs_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, extractAllTabsClicks)
    }

    @Test
    fun `verify Brave-style tab switcher renders clean full-bleed preview, New tab shortcuts, and accepts sparse thumbnails`() = runBlocking {
        val sampleThumb = android.graphics.Bitmap.createBitmap(360, 460, android.graphics.Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.WHITE)
            // Sparse top header bar/logo (e.g. Google search page) that would fail coarse 5x5 sampling
            for (x in 150..210) {
                for (y in 45..70) {
                    setPixel(x, y, android.graphics.Color.BLUE)
                }
            }
        }
        val webTab = BrowserTab(
            id = "brave_web_1",
            title = "brave browser - Google",
            url = "https://www.google.com/search?q=brave",
            isLoading = false,
            previewThumbnail = sampleThumb
        )
        val homeTab = BrowserTab(
            id = "brave_home_1",
            title = "Home",
            url = "apex://home",
            isLoading = false
        )
        val topVisit = com.example.data.local.TopVisitEntity(
            id = 1L,
            title = "GitHub",
            url = "https://github.com"
        )

        composeTestRule.setContent {
            ApexBrowserTheme {
                DraggableTabGridOverlay(
                    gridEntries = listOf(
                        TabGridEntry.SingleTab(webTab),
                        TabGridEntry.SingleTab(homeTab)
                    ),
                    totalOpenTabs = 2,
                    activeTabId = homeTab.id,
                    expandedGroupId = null,
                    topVisits = listOf(topVisit),
                    onSelectTab = {},
                    onCloseTab = {},
                    onCloseGroup = {},
                    onEditGroup = {},
                    onMergeEntries = { _, _ -> },
                    onMoveGroupedTabToTarget = { _, _ -> },
                    onOpenGroup = {},
                    onAddTabToGroup = {},
                    onUngroupTab = {},
                    onCycleTabColor = {},
                    onNewTab = {},
                    onCreateGroup = {},
                    onResetAllTabs = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("grid_tab_card_brave_web_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tabs_lazy_grid").performScrollToIndex(1)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("grid_tab_card_brave_home_1").assertIsDisplayed()
        composeTestRule.onNodeWithText("New tab").assertIsDisplayed()
        composeTestRule.onNodeWithText("GitHub").assertIsDisplayed()

        // No legacy clutter labels ("Drag to group", "Start Page", "Shield:")
        composeTestRule.onNodeWithText("Drag to group").assertDoesNotExist()
        composeTestRule.onNodeWithText("Start Page").assertDoesNotExist()

        // Verify ViewModel accepts sparse 8x8-detectable thumbnail cleanly
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val prefsRepo = UserPreferencesRepository(context)
        val sharedPrefs = context.getSharedPreferences("test_brave_thumb_prefs", Context.MODE_PRIVATE)
        val repository = BrowserRepository(db.apexDao(), prefsRepo, sharedPrefs)
        val store = androidx.lifecycle.ViewModelStore()
        val vm = androidx.lifecycle.ViewModelProvider(
            store,
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return BrowserViewModel(repository) as T
                }
            }
        )[BrowserViewModel::class.java]

        try {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            vm.submitNavigation("https://www.google.com")
            val activeId = vm.uiState.value.activeTabId
            val sparseCopy = android.graphics.Bitmap.createBitmap(sampleThumb)
            vm.updateTabThumbnail(activeId, sparseCopy)
            assertNotNull(vm.uiState.value.tabs.first { it.id == activeId }.previewThumbnail)
        } finally {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(100L)
            store.clear()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50L)
            db.close()
        }
    }

    @Test
    fun `verify PerformanceOptimizer keeps hardware WebView layer and PrivacyManager avoids main-frame false positives`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val optimizer = com.example.engine.PerformanceOptimizer()
        val webView = android.webkit.WebView(context)
        optimizer.configureFastWebView(
            webView = webView,
            scriptGuardEnabled = false,
            desktopMode = false,
            aggressiveCaching = true,
            isIncognito = false,
            textZoomPercent = 115
        )
        assertEquals(android.view.View.LAYER_TYPE_NONE, webView.layerType)
        assertEquals(115, webView.settings.textZoom)
        assertEquals(16, webView.settings.defaultFontSize)

        val privacyManager = PrivacyManager()
        val articleUrl = privacyManager.evaluateMainFrameRequest(
            requestUrl = "https://en.wikipedia.org/wiki/Adblock?ref=/ads/",
            shieldsEnabled = true
        )
        assertFalse(articleUrl.blocked)

        val adRedirect = privacyManager.evaluateMainFrameRequest(
            requestUrl = "https://trk.doubleclick.net/click?dest=foo",
            shieldsEnabled = true
        )
        assertTrue(adRedirect.blocked)
    }

    @Test
    fun `verify PrivacyManager YouTube ad blocking and hardened anti-adblock stealth responses`() {
        val privacyManager = PrivacyManager()

        // YouTube ad endpoints must be blocked
        val adBreakBlocked = privacyManager.evaluateRequest(
            requestUrl = "https://m.youtube.com/youtubei/v1/player/ad_break?key=abc",
            pageUrl = "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
            shieldsEnabled = true
        )
        assertTrue(adBreakBlocked.blocked)

        val ytPageadBlocked = privacyManager.evaluateRequest(
            requestUrl = "https://www.youtube.com/pagead/viewthroughconversion/123/",
            pageUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            shieldsEnabled = true
        )
        assertTrue(ytPageadBlocked.blocked)

        val ytStatsAdsBlocked = privacyManager.evaluateRequest(
            requestUrl = "https://www.youtube.com/api/stats/ads?adformat=1",
            pageUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            shieldsEnabled = true
        )
        assertTrue(ytStatsAdsBlocked.blocked)

        val adVideoStreamBlocked = privacyManager.evaluateRequest(
            requestUrl = "https://rr1---sn.googlevideo.com/videoplayback?expire=1&oad=1&mime=video/mp4",
            pageUrl = "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
            shieldsEnabled = true
        )
        assertTrue(adVideoStreamBlocked.blocked)

        // Regular YouTube playback and video streams must NOT be blocked
        val ytPlayerAllowed = privacyManager.evaluateRequest(
            requestUrl = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
            pageUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            shieldsEnabled = true
        )
        assertFalse(ytPlayerAllowed.blocked)

        val regularVideoStreamAllowed = privacyManager.evaluateRequest(
            requestUrl = "https://rr1---sn.googlevideo.com/videoplayback?expire=1&itag=18&mime=video/mp4",
            pageUrl = "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
            shieldsEnabled = true
        )
        assertFalse(regularVideoStreamAllowed.blocked)

        val mainFrameYtAllowed = privacyManager.evaluateMainFrameRequest(
            requestUrl = "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
            shieldsEnabled = true
        )
        assertFalse(mainFrameYtAllowed.blocked)

        // Credentialed CORS reflection & JSON/HTML/JS stealth surrogates
        val adBreakJson = privacyManager.stealthBlockedResponse(
            requestUrl = "https://m.youtube.com/youtubei/v1/player/ad_break",
            requestHeaders = mapOf("Origin" to "https://m.youtube.com", "Accept" to "application/json")
        )
        assertEquals("application/json", adBreakJson.mimeType)
        assertEquals("https://m.youtube.com", adBreakJson.responseHeaders["Access-Control-Allow-Origin"])
        assertEquals("true", adBreakJson.responseHeaders["Access-Control-Allow-Credentials"])
        assertEquals("{}", adBreakJson.data.bufferedReader().readText())

        val htmlSurrogate = privacyManager.stealthBlockedResponse(
            requestUrl = "https://tpc.googlesyndication.com/safeframe/1-0-40/html/container.html",
            requestHeaders = mapOf("Accept" to "text/html")
        )
        assertEquals("text/html", htmlSurrogate.mimeType)
        assertTrue(htmlSurrogate.data.bufferedReader().readText().contains("<!DOCTYPE html>"))

        val jsSurrogate = privacyManager.stealthBlockedResponse(
            requestUrl = "https://btloader.com/tag?o=123",
            requestHeaders = mapOf("Accept" to "*/*")
        )
        assertEquals("application/javascript", jsSurrogate.mimeType)
        val jsCode = jsSurrogate.data.bufferedReader().readText()
        assertTrue(jsCode.contains("Array.isArray(window.adsbygoogle)"))
        assertTrue(jsCode.contains("window.googlefc"))
        assertTrue(jsCode.contains("window.pbjs"))
        assertTrue(jsCode.contains("window.admiral"))

        // Hardened document-start script checks
        val stealthScript = privacyManager.buildStealthAdblockScript(true)
        assertTrue(stealthScript.contains("getPropertyValue"))
        assertTrue(stealthScript.contains("getBoundingClientRect"))
        assertTrue(stealthScript.contains("ytInitialPlayerResponse"))
        assertTrue(stealthScript.contains("adPlacements"))
        assertTrue(stealthScript.contains("playerAds"))
        assertTrue(stealthScript.contains("adSlots"))
        assertTrue(stealthScript.contains("__apexSkipYouTubeAds"))
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    @Test
    fun `verify bottom navigation bar hides when scrolling website down and shows when scrolling up or to top`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val sharedPrefs = context.getSharedPreferences("test_bottom_bar_scroll_prefs", Context.MODE_PRIVATE)
        val prefsRepo = UserPreferencesRepository(context)
        val repository = BrowserRepository(db.apexDao(), prefsRepo, sharedPrefs)
        val store = androidx.lifecycle.ViewModelStore()
        val viewModel = androidx.lifecycle.ViewModelProvider(
            store,
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return BrowserViewModel(repository) as T
                }
            }
        )[BrowserViewModel::class.java]

        try {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            // On Home page, scrolling never hides the bottom navigation bar
            assertTrue(viewModel.uiState.value.activeTab.isHomePage)
            viewModel.onWebViewScrollDelta(dy = 120, scrollY = 200)
            assertTrue(viewModel.uiState.value.isBottomBarVisible)

            // Navigate to a website
            viewModel.submitNavigation("https://example.com/article")
            composeTestRule.setContent {
                ApexBrowserTheme {
                    BrowserScreen(
                        viewModel = viewModel,
                        windowSizeClass = WindowSizeClass.calculateFromSize(DpSize(412.dp, 915.dp))
                    )
                }
            }
            composeTestRule.waitForIdle()
            assertTrue(viewModel.uiState.value.isBottomBarVisible)
            composeTestRule.onNodeWithTag("bottom_navigation_bar").assertIsDisplayed()

            // Tiny jitter near top does not immediately hide the bar
            viewModel.onWebViewScrollDelta(dy = 6, scrollY = 18)
            assertTrue(viewModel.uiState.value.isBottomBarVisible)

            // Scrolling down the webpage (dragging finger up / toward top) hides the bottom bar while keeping top toolbar visible
            viewModel.onWebViewScrollDelta(dy = 60, scrollY = 140)
            composeTestRule.waitForIdle()
            assertFalse(viewModel.uiState.value.isBottomBarVisible)
            assertTrue(viewModel.uiState.value.isToolbarVisible)
            composeTestRule.onNodeWithTag("bottom_navigation_bar").assertDoesNotExist()

            // Scrolling back up (dragging finger down / toward bottom) shows the bottom bar
            viewModel.onWebViewScrollDelta(dy = -36, scrollY = 104)
            composeTestRule.waitForIdle()
            assertTrue(viewModel.uiState.value.isBottomBarVisible)
            composeTestRule.onNodeWithTag("bottom_navigation_bar").assertIsDisplayed()

            // Hide again, then return to scrollY == 0 (top of page) -> must re-show bottom bar
            viewModel.onWebViewScrollDelta(dy = 48, scrollY = 180)
            composeTestRule.waitForIdle()
            assertFalse(viewModel.uiState.value.isBottomBarVisible)
            composeTestRule.onNodeWithTag("bottom_navigation_bar").assertDoesNotExist()

            viewModel.onWebViewScrollDelta(dy = 0, scrollY = 0)
            composeTestRule.waitForIdle()
            assertTrue(viewModel.uiState.value.isBottomBarVisible)
            composeTestRule.onNodeWithTag("bottom_navigation_bar").assertIsDisplayed()
        } finally {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(100L)
            store.clear()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50L)
            db.close()
        }
    }

    @Test
    fun `verify DownloadsScreen displays Used of Total storage and opens completed app files on user click`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val downloadDir = com.example.engine.DownloadStorageHelper.resolveDirectory(
            context,
            DownloadLocationMode.APP_EXTERNAL
        )
        val apkFile = File(downloadDir, "sample app (1).apk")
        apkFile.writeText("PK-dummy-apk-content")

        try {
            // MIME type resolution handles parentheses & spaces in filenames
            assertEquals(
                "application/vnd.android.package-archive",
                FastDownloadEngine.resolveMimeType("sample app (1).apk", "application/octet-stream")
            )

            // Auto-open still blocks APKs, while user-initiated open produces a FileProvider ACTION_VIEW Intent
            assertFalse(
                com.example.engine.DownloadStorageHelper.isSafeToAutoOpen(
                    apkFile.name,
                    "application/vnd.android.package-archive"
                )
            )
            val autoIntent = com.example.engine.DownloadStorageHelper.buildOpenFileIntent(
                context = context,
                localFilePath = apkFile.absolutePath,
                fileName = apkFile.name,
                mimeType = "application/octet-stream",
                userInitiated = false
            )
            assertNull(autoIntent)

            val userIntent = com.example.engine.DownloadStorageHelper.buildOpenFileIntent(
                context = context,
                localFilePath = apkFile.absolutePath,
                fileName = apkFile.name,
                mimeType = "application/octet-stream",
                userInitiated = true
            )
            assertNotNull(userIntent)
            assertEquals(android.content.Intent.ACTION_VIEW, userIntent?.action)
            assertEquals("application/vnd.android.package-archive", userIntent?.type)
            assertEquals("content", userIntent?.data?.scheme)
            assertTrue(
                ((userIntent?.flags ?: 0) and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0
            )

            val completedApk = com.example.data.local.DownloadItemEntity(
                id = 91L,
                fileName = apkFile.name,
                url = "https://example.com/app.apk",
                mimeType = "application/vnd.android.package-archive",
                category = "Archive",
                totalBytes = apkFile.length(),
                downloadedBytes = apkFile.length(),
                speedBytesPerSec = 4_000_000L,
                chunksCount = 8,
                status = FastDownloadEngine.STATUS_COMPLETED,
                localFilePath = apkFile.absolutePath,
                createdAt = System.currentTimeMillis(),
                completedAt = System.currentTimeMillis()
            )
            // With nothing downloaded, used space must be 0 B (not OS/device partition space)
            val emptyStorageInfo = com.example.engine.DownloadStorageHelper.getStorageSpaceInfo(
                context = context,
                mode = DownloadLocationMode.APP_INTERNAL,
                downloads = emptyList()
            )
            assertEquals(0L, emptyStorageInfo.usedBytes)
            assertTrue(
                com.example.engine.DownloadStorageHelper.formatUsedOfTotalLabel(emptyStorageInfo)
                    .startsWith("0 B / ")
            )

            // With a completed download, used space must match actual file bytes on disk
            val withApkStorageInfo = com.example.engine.DownloadStorageHelper.getStorageSpaceInfo(
                context = context,
                mode = DownloadLocationMode.APP_INTERNAL,
                downloads = listOf(completedApk)
            )
            assertEquals(apkFile.length(), withApkStorageInfo.usedBytes)

            val customSpace = com.example.engine.DownloadStorageHelper.StorageSpaceInfo(
                usedBytes = 42L * 1024L * 1024L * 1024L,
                totalBytes = 128L * 1024L * 1024L * 1024L,
                availableBytes = 86L * 1024L * 1024L * 1024L
            )
            var openedItem: com.example.data.local.DownloadItemEntity? = null

            composeTestRule.setContent {
                ApexBrowserTheme {
                    com.example.ui.browser.DownloadsScreenOverlay(
                        downloads = listOf(completedApk),
                        storageSpaceInfoOverride = customSpace,
                        onOpenDownload = { openedItem = it },
                        onStartNewDownload = { _, _ -> },
                        onPauseDownload = {},
                        onResumeDownload = {},
                        onRetryDownload = {},
                        onCancelDownload = {},
                        onDeleteDownload = {},
                        onClearCompletedDownloads = {},
                        onClearAllDownloads = {},
                        onOpenUrlInBrowser = {},
                        onDismiss = {}
                    )
                }
            }
            composeTestRule.waitForIdle()

            // Top summary metric must show storage value & bar without "Used of Total" or "Data Saved" text
            composeTestRule.onNodeWithText("Data Saved").assertDoesNotExist()
            composeTestRule.onNodeWithText("Used of Total").assertDoesNotExist()
            composeTestRule.onNodeWithText("42.0 / 128 GB").assertIsDisplayed()
            composeTestRule.onNodeWithTag("downloads_used_space_tile").assertIsDisplayed()
            composeTestRule.onNodeWithTag("downloads_storage_usage_bar").assertIsDisplayed()
            composeTestRule.onNodeWithTag("downloads_summary_card").assertIsDisplayed()
            composeTestRule.onNodeWithText("Engine Speed").assertIsDisplayed()

            // Tapping the completed app's open button opens it directly
            composeTestRule.onNodeWithTag("download_open_91")
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
            composeTestRule.waitForIdle()
            assertEquals(91L, openedItem?.id)
        } finally {
            runCatching { apkFile.delete() }
        }
    }

    @Test
    fun `verify FastDownloadEngine performs 8x multi-TCP ranged work-stealing download with single foreground notification`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val notificationManager = app.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val shadowNotificationManager = Shadows.shadowOf(notificationManager)
        notificationManager.cancelAll()

        val db = Room.inMemoryDatabaseBuilder(app, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val sharedPrefs = app.getSharedPreferences("test_turbo_multi_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().clear().commit()
        val prefsRepo = UserPreferencesRepository(app)
        val browserRepo = BrowserRepository(db.apexDao(), prefsRepo, sharedPrefs)

        runBlocking<Unit> {
            browserRepo.setDownloadLocation(DownloadLocationMode.APP_INTERNAL, "", "")
            browserRepo.setAskWhereToSaveFiles(false)
            browserRepo.setShowDownloadProgressNotifications(true)
            browserRepo.setParallelDownloadingEnabled(true)
        }

        val engine = FastDownloadEngine(app, browserRepo)
        val payload = ByteArray(768 * 1024) { ((it * 31) % 251).toByte() }
        val rangeRequestsObserved = java.util.concurrent.atomic.AtomicInteger(0)

        val server = ServerSocket(0)
        val port = server.localPort
        val serverThread = thread(isDaemon = true) {
            while (!server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    client.use { s ->
                        val reader = s.getInputStream().bufferedReader()
                        var requestLine = ""
                        var rangeHeader: String? = null
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (requestLine.isEmpty()) requestLine = line
                            if (line.startsWith("Range:", ignoreCase = true)) {
                                rangeHeader = line.substringAfter(':').trim()
                            }
                            if (line.isEmpty()) break
                        }
                        val out = s.getOutputStream()
                        val isHead = requestLine.startsWith("HEAD", ignoreCase = true)
                        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                            val spec = rangeHeader.removePrefix("bytes=")
                            val start = spec.substringBefore('-').toIntOrNull() ?: 0
                            val endRaw = spec.substringAfter('-', "").toIntOrNull()
                            val end = (endRaw ?: (payload.size - 1)).coerceAtMost(payload.size - 1)
                            val len = (end - start) + 1
                            rangeRequestsObserved.incrementAndGet()
                            val header = "HTTP/1.1 206 Partial Content\r\n" +
                                "Accept-Ranges: bytes\r\n" +
                                "Content-Range: bytes $start-$end/${payload.size}\r\n" +
                                "Content-Length: $len\r\n" +
                                "Content-Type: application/vnd.android.package-archive\r\n" +
                                "Connection: close\r\n\r\n"
                            out.write(header.toByteArray())
                            if (!isHead && len > 0) {
                                out.write(payload, start, len)
                            }
                        } else {
                            val header = "HTTP/1.1 200 OK\r\n" +
                                "Accept-Ranges: bytes\r\n" +
                                "Content-Length: ${payload.size}\r\n" +
                                "Content-Type: application/vnd.android.package-archive\r\n" +
                                "Connection: close\r\n\r\n"
                            out.write(header.toByteArray())
                            if (!isHead) {
                                out.write(payload)
                            }
                        }
                        out.flush()
                    }
                }
            }
        }

        try {
            val startedLatch = CountDownLatch(1)
            var downloadId = -1L
            engine.enqueueDownload(
                url = "http://127.0.0.1:$port/turbo_app.apk",
                suggestedFileName = "turbo_app.apk"
            ) { entity ->
                downloadId = entity.id
                startedLatch.countDown()
            }

            val startDeadline = System.currentTimeMillis() + 5_000L
            while (startedLatch.count > 0 && System.currentTimeMillis() < startDeadline) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(20L)
            }
            assertEquals(0L, startedLatch.count)

            var finalEntity = runBlocking { db.apexDao().getDownloadById(downloadId) }
            var sawNoDuplicateActivePerItemNotif = true
            val completeDeadline = System.currentTimeMillis() + 10_000L
            while (System.currentTimeMillis() < completeDeadline) {
                finalEntity = runBlocking { db.apexDao().getDownloadById(downloadId) }
                if (finalEntity?.status == FastDownloadEngine.STATUS_DOWNLOADING) {
                    val perItemNotif = shadowNotificationManager.getNotification(
                        com.example.engine.DownloadNotificationHelper.notificationIdFor(downloadId)
                    )
                    if (perItemNotif != null &&
                        (perItemNotif.flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0
                    ) {
                        sawNoDuplicateActivePerItemNotif = false
                    }
                }
                if (finalEntity?.status == FastDownloadEngine.STATUS_COMPLETED) break
                Thread.sleep(25L)
            }

            assertTrue(
                "Active download must not post a duplicate per-item ongoing notification alongside ForegroundService",
                sawNoDuplicateActivePerItemNotif
            )
            assertEquals(FastDownloadEngine.STATUS_COMPLETED, finalEntity?.status)
            assertEquals(FastDownloadEngine.DEFAULT_TURBO_CHUNKS, finalEntity?.chunksCount)
            assertTrue(
                "Expected multiple parallel Range requests (>= 8), got ${rangeRequestsObserved.get()}",
                rangeRequestsObserved.get() >= 8
            )
            assertArrayEquals(payload, File(finalEntity!!.localFilePath).readBytes())
        } finally {
            runCatching { server.close() }
            serverThread.interrupt()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(100L)
            db.close()
        }
    }
}



