package com.example

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ApexDatabase
import com.example.data.preferences.BrowserPreferences
import com.example.data.preferences.HomeWallpaperMode
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.BrowserRepository
import com.example.engine.HomeWallpaperData
import com.example.engine.HomeWallpaperEngine
import com.example.ui.browser.DraggableTabGridOverlay
import com.example.ui.browser.SettingsInitialSection
import com.example.ui.browser.SettingsScreenOverlay
import com.example.ui.browser.StartPageHome
import com.example.ui.theme.ApexBrowserTheme
import com.example.ui.theme.TabGroupColorOption
import com.example.ui.viewmodel.BrowserTab
import com.example.ui.viewmodel.BrowserViewModel
import com.example.ui.viewmodel.TabGridEntry
import com.example.ui.viewmodel.TabGroupInfo
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class HomeWallpaperAppearanceTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: ApexDatabase
    private lateinit var prefsRepo: UserPreferencesRepository
    private lateinit var repository: BrowserRepository
    private lateinit var tempCacheDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("apex_pro_subscription_state", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        database = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefsRepo = UserPreferencesRepository(context)
        runBlocking {
            prefsRepo.setHomeWallpaperMode(HomeWallpaperMode.ONLINE_API)
            prefsRepo.setCustomHomeWallpaperPath("")
            prefsRepo.setHomeWallpaperBlackAndWhite(false)
        }
        repository = BrowserRepository(
            dao = database.apexDao(),
            preferencesRepository = prefsRepo
        )
        tempCacheDir = File(context.cacheDir, "wallpaper_test_${System.nanoTime()}").apply {
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        database.close()
        tempCacheDir.deleteRecursively()
    }

    private fun createSolidPngBytes(colorArgb: Int): ByteArray {
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(colorArgb)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        return out.toByteArray()
    }

    @Test
    fun parseCatalogJson_parsesAuthorsAndBuildsPortraitUrls() {
        val rawJson = """
            [
              {"id":"101","author":"Alejandro Escamilla"},
              {"id":"202","author":""},
              {"id":"","author":"Missing Id"}
            ]
        """.trimIndent()

        val items = HomeWallpaperEngine.parseCatalogJson(rawJson)
        assertEquals(2, items.size)
        assertEquals("101", items[0].id)
        assertEquals("Alejandro Escamilla", items[0].author)
        assertEquals("https://picsum.photos/id/101/1080/1920", items[0].imageUrl)
        assertEquals("202", items[1].id)
        assertEquals("Picsum Photos", items[1].author)
        assertEquals("https://picsum.photos/id/202/1080/1920", items[1].imageUrl)
    }

    @Test
    fun loadWallpaperForSeed_assignsDistinctApiWallpapersAcrossNewTabsAndCachesSameSeed() {
        val redPng = createSolidPngBytes(android.graphics.Color.RED)
        val bluePng = createSolidPngBytes(android.graphics.Color.BLUE)
        val requestedUrls = mutableListOf<String>()

        val interceptor = Interceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()
            requestedUrls.add(url)
            when {
                url.contains("/v2/list") -> {
                    val json = """
                        [
                          {"id":"10","author":"Paul Jarvis"},
                          {"id":"20","author":"Tina Rataj"}
                        ]
                    """.trimIndent()
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(json.toResponseBody("application/json".toMediaType()))
                        .build()
                }
                url.contains("/id/10/") -> {
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(redPng.toResponseBody("image/png".toMediaType()))
                        .build()
                }
                else -> {
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(bluePng.toResponseBody("image/png".toMediaType()))
                        .build()
                }
            }
        }

        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val engine = HomeWallpaperEngine(cacheRootDir = tempCacheDir, httpClient = client)

        val first = runBlocking { engine.loadWallpaperForSeed("home_tab_1") }
        val second = runBlocking { engine.loadWallpaperForSeed("home_tab_2") }
        val firstRepeated = runBlocking { engine.loadWallpaperForSeed("home_tab_1") }

        assertNotNull(first)
        assertNotNull(second)
        assertNotNull(firstRepeated)
        assertNotEquals(first!!.imageUrl, second!!.imageUrl)
        assertNotEquals(first.author, second.author)
        assertEquals(first.imageUrl, firstRepeated!!.imageUrl)
        assertEquals(first.author, firstRepeated.author)
        assertTrue(requestedUrls.any { it.contains("https://picsum.photos/v2/list") })
    }

    @Test
    fun browserViewModel_generatesUniqueHomeWallpaperSeedForEveryNewHomeTab() {
        val viewModel = BrowserViewModel(
            repository = repository,
            wallpaperEngine = HomeWallpaperEngine(cacheRootDir = tempCacheDir)
        )
        val initialTab = viewModel.uiState.value.activeTab
        assertTrue(initialTab.isHomePage)
        assertTrue(initialTab.homeWallpaperSeed.isNotBlank())

        viewModel.createNewTab(initialUrl = "apex://home")
        val secondTab = viewModel.uiState.value.activeTab
        assertTrue(secondTab.isHomePage)
        assertNotEquals(initialTab.id, secondTab.id)
        assertNotEquals(initialTab.homeWallpaperSeed, secondTab.homeWallpaperSeed)

        viewModel.createNewIncognitoTab(initialUrl = "apex://home")
        val thirdTab = viewModel.uiState.value.activeTab
        assertTrue(thirdTab.isHomePage)
        assertNotEquals(secondTab.homeWallpaperSeed, thirdTab.homeWallpaperSeed)
        assertNotEquals(initialTab.homeWallpaperSeed, thirdTab.homeWallpaperSeed)
    }

    @Test
    fun userPreferencesRepository_persistsWallpaperModeCustomPathAndBlackAndWhite() = runBlocking {
        prefsRepo.setHomeWallpaperMode(HomeWallpaperMode.CUSTOM_PHONE)
        prefsRepo.setCustomHomeWallpaperPath("/data/local/tmp/custom_wp.jpg")
        prefsRepo.setHomeWallpaperBlackAndWhite(true)

        val updated = prefsRepo.preferencesFlow.first()
        assertEquals(HomeWallpaperMode.CUSTOM_PHONE, updated.homeWallpaperMode)
        assertEquals("/data/local/tmp/custom_wp.jpg", updated.customHomeWallpaperPath)
        assertTrue(updated.homeWallpaperBlackAndWhite)

        prefsRepo.setHomeWallpaperMode(HomeWallpaperMode.BLACK_AND_WHITE)
        prefsRepo.setHomeWallpaperBlackAndWhite(false)
        val bwUpdated = prefsRepo.preferencesFlow.first()
        assertEquals(HomeWallpaperMode.BLACK_AND_WHITE, bwUpdated.homeWallpaperMode)
        assertFalse(bwUpdated.homeWallpaperBlackAndWhite)
    }

    @Test
    fun appearanceSubScreen_displaysWallpaperModesPickFromPhoneAndBlackAndWhiteToggle() {
        var currentPrefs by mutableStateOf(BrowserPreferences())

        composeTestRule.setContent {
            ApexBrowserTheme(darkTheme = false, dynamicColor = false) {
                SettingsScreenOverlay(
                    preferences = currentPrefs,
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
                    onSelectHomeWallpaperMode = { mode ->
                        currentPrefs = currentPrefs.copy(homeWallpaperMode = mode)
                    },
                    onToggleHomeWallpaperBlackAndWhite = { enabled ->
                        currentPrefs = currentPrefs.copy(homeWallpaperBlackAndWhite = enabled)
                    },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("settings_home_wallpaper_card")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_wallpaper_preview")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_wallpaper_mode_online_api")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_wallpaper_mode_custom_phone")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_wallpaper_mode_black_and_white")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_wallpaper_pick_phone_button")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_wallpaper_bw_switch")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()
        assertTrue(currentPrefs.homeWallpaperBlackAndWhite)

        composeTestRule.onNodeWithTag("settings_wallpaper_mode_black_and_white")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        assertEquals(HomeWallpaperMode.BLACK_AND_WHITE, currentPrefs.homeWallpaperMode)
        composeTestRule.onNodeWithTag("settings_wallpaper_bw_switch").assertDoesNotExist()
    }

    @Test
    fun startPageHome_switchesBetweenOnlineApiCustomPhoneAndBlackAndWhiteModes() {
        val onlineBitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.CYAN)
        }
        val customBitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.MAGENTA)
        }
        var currentPrefs by mutableStateOf(
            BrowserPreferences(homeWallpaperMode = HomeWallpaperMode.ONLINE_API)
        )
        var refreshClicks = 0

        composeTestRule.setContent {
            ApexBrowserTheme(darkTheme = true, dynamicColor = false) {
                StartPageHome(
                    preferences = currentPrefs,
                    topVisits = emptyList(),
                    onOpenSearchScreen = {},
                    onSelectDestination = {},
                    onAddTopVisit = { _, _ -> },
                    onDeleteTopVisit = {},
                    wallpaperData = HomeWallpaperData(
                        seed = "seed_a",
                        bitmap = onlineBitmap,
                        author = "Alejandro Escamilla",
                        imageUrl = "https://picsum.photos/id/10/1080/1920"
                    ),
                    customWallpaperBitmap = customBitmap,
                    onRefreshWallpaper = { refreshClicks += 1 }
                )
            }
        }

        composeTestRule.onNodeWithTag("home_wallpaper_image").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_wallpaper_credit_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_wallpaper_refresh_button").performClick()
        assertEquals(1, refreshClicks)

        composeTestRule.runOnIdle {
            currentPrefs = currentPrefs.copy(
                homeWallpaperMode = HomeWallpaperMode.CUSTOM_PHONE,
                homeWallpaperBlackAndWhite = true
            )
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_wallpaper_image").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_wallpaper_credit_pill").assertDoesNotExist()

        composeTestRule.runOnIdle {
            currentPrefs = currentPrefs.copy(homeWallpaperMode = HomeWallpaperMode.BLACK_AND_WHITE)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_wallpaper_bw_canvas").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_wallpaper_image").assertDoesNotExist()
    }

    @Test
    fun tabSwitcherPreviews_renderOnlineApiCustomPhoneAndBlackAndWhiteWallpapers() {
        val redBitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.RED)
        }
        val greenBitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.GREEN)
        }
        val customBitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.MAGENTA)
        }

        val singleHomeTab = BrowserTab(
            id = "tab_home_single",
            title = "Home",
            url = "apex://home",
            homeWallpaperSeed = "seed_single"
        )
        val groupHomeTab = BrowserTab(
            id = "tab_home_group",
            title = "Home",
            url = "apex://home",
            groupId = "grp_1",
            homeWallpaperSeed = "seed_group"
        )
        val groupWebTab = BrowserTab(
            id = "tab_web_group",
            title = "Example",
            url = "https://example.com",
            groupId = "grp_1"
        )
        val gridEntries = listOf(
            TabGridEntry.SingleTab(singleHomeTab),
            TabGridEntry.GroupTab(
                group = TabGroupInfo(
                    id = "grp_1",
                    title = "Group 1",
                    colorOption = TabGroupColorOption.COBALT
                ),
                tabs = listOf(groupHomeTab, groupWebTab)
            )
        )
        val homeWallpapers = mapOf(
            "seed_single" to HomeWallpaperData(
                seed = "seed_single",
                bitmap = redBitmap,
                author = "Author One",
                imageUrl = "https://picsum.photos/id/1/1080/1920"
            ),
            "seed_group" to HomeWallpaperData(
                seed = "seed_group",
                bitmap = greenBitmap,
                author = "Author Two",
                imageUrl = "https://picsum.photos/id/2/1080/1920"
            )
        )

        var currentPrefs by mutableStateOf(
            BrowserPreferences(homeWallpaperMode = HomeWallpaperMode.ONLINE_API)
        )

        composeTestRule.setContent {
            ApexBrowserTheme(darkTheme = false, dynamicColor = false) {
                DraggableTabGridOverlay(
                    gridEntries = gridEntries,
                    totalOpenTabs = 3,
                    activeTabId = singleHomeTab.id,
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
                    preferences = currentPrefs,
                    homeWallpapers = homeWallpapers,
                    customWallpaperBitmap = customBitmap,
                    onDismiss = {}
                )
            }
        }

        // Single home tab and group mini-slot home tab both render their wallpaper previews
        composeTestRule.onNodeWithTag("tab_preview_wallpaper_image_${singleHomeTab.id}", useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_preview_wallpaper_image_${groupHomeTab.id}", useUnmergedTree = true)
            .assertIsDisplayed()

        // Switch to Custom Phone + monochrome filter -> still renders wallpaper images in both previews
        composeTestRule.runOnIdle {
            currentPrefs = currentPrefs.copy(
                homeWallpaperMode = HomeWallpaperMode.CUSTOM_PHONE,
                homeWallpaperBlackAndWhite = true
            )
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tab_preview_wallpaper_image_${singleHomeTab.id}", useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_preview_wallpaper_image_${groupHomeTab.id}", useUnmergedTree = true)
            .assertIsDisplayed()

        // Switch to solid Black & White wallpaper mode -> renders B&W canvases instead of photo bitmaps
        composeTestRule.runOnIdle {
            currentPrefs = currentPrefs.copy(homeWallpaperMode = HomeWallpaperMode.BLACK_AND_WHITE)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tab_preview_wallpaper_bw_${singleHomeTab.id}", useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_preview_wallpaper_bw_${groupHomeTab.id}", useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_preview_wallpaper_image_${singleHomeTab.id}", useUnmergedTree = true)
            .assertDoesNotExist()
    }
}
