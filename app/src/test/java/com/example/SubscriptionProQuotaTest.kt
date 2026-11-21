package com.example

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ApexDatabase
import com.example.data.preferences.BrowserPreferences
import com.example.data.preferences.ProSubscriptionPackage
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.BrowserRepository
import com.example.engine.FastDownloadEngine
import com.example.ui.browser.BrowserMenuBottomSheet
import com.example.ui.browser.SubscriptionScreen
import com.example.ui.theme.ApexBrowserTheme
import com.example.ui.viewmodel.BrowserViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class SubscriptionProQuotaTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: ApexDatabase
    private lateinit var prefsRepo: UserPreferencesRepository
    private lateinit var repository: BrowserRepository

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
            prefsRepo.deactivateProSubscription()
        }
        repository = BrowserRepository(
            dao = database.apexDao(),
            preferencesRepository = prefsRepo
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun proSubscriptionPackages_divideFiveDollarsMonthlyAcrossMonthWeekDay() {
        assertEquals(5.0, ProSubscriptionPackage.MONTH.priceUsd, 0.001)
        assertEquals("$5.00", ProSubscriptionPackage.MONTH.formattedPrice)
        assertEquals(30, ProSubscriptionPackage.MONTH.durationDays)

        assertEquals(5.0 * 7.0 / 30.0, ProSubscriptionPackage.WEEK.priceUsd, 0.001)
        assertEquals("$1.17", ProSubscriptionPackage.WEEK.formattedPrice)
        assertEquals(7, ProSubscriptionPackage.WEEK.durationDays)

        assertEquals(5.0 / 30.0, ProSubscriptionPackage.DAY.priceUsd, 0.001)
        assertEquals("$0.17", ProSubscriptionPackage.DAY.formattedPrice)
        assertEquals(1, ProSubscriptionPackage.DAY.durationDays)
    }

    @Test
    fun browserPreferences_enforcesDailyFiveLimit_andResetsOnNewDay() {
        val today = "2026-11-20"
        val tomorrow = "2026-11-21"

        val fresh = BrowserPreferences()
        assertEquals(5, fresh.remainingFreeAutomationPlaysToday(today))
        assertTrue(fresh.canPlayAutomationToday(today))

        val usedFive = fresh.copy(
            automationPlayDayKey = today,
            automationDailyPlaysUsed = 5
        )
        assertEquals(0, usedFive.remainingFreeAutomationPlaysToday(today))
        assertFalse(usedFive.canPlayAutomationToday(today))

        // Day rollover automatically resets remaining count to 5
        assertEquals(5, usedFive.remainingFreeAutomationPlaysToday(tomorrow))
        assertTrue(usedFive.canPlayAutomationToday(tomorrow))

        // Pro activation bypasses daily limit completely while valid
        val pro = usedFive.copy(isProActivated = true, proActivationKey = "sk_12345")
        assertTrue(pro.canPlayAutomationToday(today))
        assertEquals(Int.MAX_VALUE, pro.remainingFreeAutomationPlaysToday(today))
    }

    @Test
    fun preferencesRepository_enforcesExpiryForDayWeekAndMonthPackages() = runBlocking {
        val baseTime = 1_700_000_000_000L
        val oneDayMs = ProSubscriptionPackage.DAY.durationMillis
        val oneWeekMs = ProSubscriptionPackage.WEEK.durationMillis
        val oneMonthMs = ProSubscriptionPackage.MONTH.durationMillis

        // Activate Day package
        assertTrue(
            prefsRepo.activateProSubscription(
                rawKey = "sk_12345",
                selectedPackage = ProSubscriptionPackage.DAY,
                nowMillis = baseTime
            )
        )
        val dayPrefs = prefsRepo.preferencesFlow.first()
        assertEquals(ProSubscriptionPackage.DAY, dayPrefs.proPackage)
        assertEquals(baseTime + oneDayMs, dayPrefs.proExpiresAtMillis)
        assertTrue(dayPrefs.isProCurrentlyActive(baseTime + oneDayMs - 1_000L))
        assertFalse(dayPrefs.isProCurrentlyActive(baseTime + oneDayMs))
        assertTrue(dayPrefs.isProExpired(baseTime + oneDayMs))

        // Activate Week package
        assertTrue(
            prefsRepo.activateProSubscription(
                rawKey = "sk_12345",
                selectedPackage = ProSubscriptionPackage.WEEK,
                nowMillis = baseTime
            )
        )
        val weekPrefs = prefsRepo.preferencesFlow.first()
        assertEquals(ProSubscriptionPackage.WEEK, weekPrefs.proPackage)
        assertEquals(baseTime + oneWeekMs, weekPrefs.proExpiresAtMillis)
        assertTrue(weekPrefs.isProCurrentlyActive(baseTime + oneDayMs + 1_000L))
        assertFalse(weekPrefs.isProCurrentlyActive(baseTime + oneWeekMs))

        // Activate Month package
        assertTrue(
            prefsRepo.activateProSubscription(
                rawKey = "sk_12345",
                selectedPackage = ProSubscriptionPackage.MONTH,
                nowMillis = baseTime
            )
        )
        val monthPrefs = prefsRepo.preferencesFlow.first()
        assertEquals(ProSubscriptionPackage.MONTH, monthPrefs.proPackage)
        assertEquals(baseTime + oneMonthMs, monthPrefs.proExpiresAtMillis)
        assertTrue(monthPrefs.isProCurrentlyActive(baseTime + oneWeekMs + 1_000L))
        assertFalse(monthPrefs.isProCurrentlyActive(baseTime + oneMonthMs))
    }

    @Test
    fun preferencesRepository_recordsPlaysAndActivatesOnlyWithValidKey() = runBlocking {
        val day1 = "2026-11-20"
        val day2 = "2026-11-21"

        repeat(5) { idx ->
            val used = prefsRepo.recordAutomationPlayUsage(day1)
            assertEquals(idx + 1, used)
        }
        val afterFive = prefsRepo.preferencesFlow.first()
        assertEquals(0, afterFive.remainingFreeAutomationPlaysToday(day1))
        assertFalse(afterFive.canPlayAutomationToday(day1))

        // Recording on a new day resets counter to 1
        val day2Used = prefsRepo.recordAutomationPlayUsage(day2)
        assertEquals(1, day2Used)
        assertEquals(4, prefsRepo.preferencesFlow.first().remainingFreeAutomationPlaysToday(day2))

        // Reject invalid keys
        assertFalse(prefsRepo.activateProSubscription("wrong_key"))
        assertFalse(prefsRepo.activateProSubscription("   "))
        assertFalse(prefsRepo.preferencesFlow.first().isProActivated)

        // Accept valid key with surrounding whitespace
        assertTrue(prefsRepo.activateProSubscription("  sk_12345 \n"))
        val activated = prefsRepo.preferencesFlow.first()
        assertTrue(activated.isProActivated)
        assertEquals("sk_12345", activated.proActivationKey)
        assertTrue(activated.proActivatedAtMillis > 0L)
        assertTrue(activated.canPlayAutomationToday(day1))
    }

    private fun awaitMainCondition(timeoutMs: Long = 5_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate()) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            if (predicate()) return
            if (System.currentTimeMillis() >= deadline) {
                throw AssertionError("Timed out after ${timeoutMs}ms waiting for ViewModel state condition")
            }
            Thread.sleep(15L)
        }
    }

    @Test
    fun viewModel_blocksSixthDailyAutomationPlayAndUnlocksAfterSk12345Activation() {
        val store = androidx.lifecycle.ViewModelStore()
        val viewModel = androidx.lifecycle.ViewModelProvider(
            store,
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return BrowserViewModel(
                        repository = repository,
                        fastDownloadEngine = FastDownloadEngine(context, repository)
                    ) as T
                }
            }
        )[BrowserViewModel::class.java]

        try {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            val today = BrowserPreferences.currentLocalDayKey()

            // Exhaust 5 free plays directly in repo for today
            runBlocking {
                repeat(5) {
                    repository.recordAutomationPlayUsage(today)
                }
                assertEquals(0, prefsRepo.preferencesFlow.first().remainingFreeAutomationPlaysToday(today))
            }

            // Attempting Nano automation should trigger Subscription screen launch instead of executing
            viewModel.runNanoTask("Click Submit")
            awaitMainCondition {
                viewModel.uiState.value.isSubscriptionDialogOpen
            }
            assertTrue(viewModel.uiState.value.isSubscriptionDialogOpen)
            assertNotNull(viewModel.uiState.value.subscriptionPromptReason)
            assertTrue(
                viewModel.uiState.value.subscriptionPromptReason.orEmpty().contains("5/5 plays used today")
            )

            // Invalid key sets inline error and leaves Pro inactive
            viewModel.activateProSubscription("sk_invalid", ProSubscriptionPackage.WEEK)
            awaitMainCondition {
                viewModel.uiState.value.proActivationError != null
            }
            assertFalse(runBlocking { prefsRepo.preferencesFlow.first().isProActivated })

            // Valid key sk_12345 activates Pro for selected Week package, clears error & limit reason
            viewModel.activateProSubscription("sk_12345", ProSubscriptionPackage.WEEK)
            awaitMainCondition {
                viewModel.preferencesState.value.isProActivated
            }
            assertNull(viewModel.uiState.value.proActivationError)
            assertNull(viewModel.uiState.value.subscriptionPromptReason)
            val savedPrefs = runBlocking { prefsRepo.preferencesFlow.first() }
            assertTrue(savedPrefs.isProCurrentlyActive())
            assertEquals(ProSubscriptionPackage.WEEK, savedPrefs.proPackage)

            viewModel.setSubscriptionDialogOpen(false)
            assertFalse(viewModel.uiState.value.isSubscriptionDialogOpen)
        } finally {
            store.clear()
        }
    }

    @Test
    fun subscriptionScreen_supportsPackageSelectionPasteActivateAndOmitsBackupActions() {
        var isPro by mutableStateOf(false)
        var remaining by mutableStateOf(0)
        var errorMsg by mutableStateOf<String?>(null)
        var activatedWithKey by mutableStateOf("")
        var selectedPkg by mutableStateOf(ProSubscriptionPackage.MONTH)
        var activePkg by mutableStateOf(ProSubscriptionPackage.MONTH)
        var expiresAt by mutableStateOf(0L)

        composeTestRule.setContent {
            ApexBrowserTheme {
                val clipboard = LocalClipboardManager.current
                androidx.compose.foundation.layout.Column {
                    BrowserMenuBottomSheet(
                        isHomeActive = true,
                        desktopViewportEnabled = false,
                        isProActivated = isPro,
                        remainingFreePlaysToday = remaining,
                        dailyPlayLimit = 5,
                        onOpenSettings = {},
                        onToggleDesktopMode = {},
                        onClearBrowsingData = {},
                        onDismiss = {}
                    )
                    SubscriptionScreen(
                        isProActivated = isPro,
                        isProExpired = false,
                        activeKey = activatedWithKey,
                        activePackage = activePkg,
                        expiresAtMillis = expiresAt,
                        remainingProMillis = activePkg.durationMillis,
                        selectedPackage = selectedPkg,
                        onSelectPackage = { selectedPkg = it },
                        remainingFreePlaysToday = remaining,
                        dailyPlayLimit = 5,
                        promptReason = "Daily 5 free automation plays used.",
                        activationError = errorMsg,
                        onActivateKey = { raw, pkg ->
                            if (raw.trim() == BrowserPreferences.VALID_PRO_ACTIVATION_KEY) {
                                activatedWithKey = raw.trim()
                                activePkg = pkg
                                expiresAt = System.currentTimeMillis() + pkg.durationMillis
                                isPro = true
                                errorMsg = null
                            } else {
                                errorMsg = "Invalid activation key."
                            }
                        },
                        onClearError = { errorMsg = null },
                        onDeactivatePro = {
                            isPro = false
                            activatedWithKey = ""
                            expiresAt = 0L
                        },
                        onBack = {}
                    )
                }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    clipboard.setText(AnnotatedString("sk_12345"))
                }
            }
        }

        composeTestRule.onNodeWithTag("subscription_pro_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pro_limit_reason_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pro_package_month").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("pro_package_week").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("pro_package_day").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("pro_contact_author_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Contact Author ($5.00)").assertIsDisplayed()
        composeTestRule.onNodeWithText("$5.00/mo unlimited", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("$5/mo ÷ 30", substring = true).assertDoesNotExist()

        // Bookmark backup, passwords backup, and automation packages must NOT exist on SubscriptionScreen
        composeTestRule.onNodeWithTag("sync_dialog_bookmarks_backup").assertDoesNotExist()
        composeTestRule.onNodeWithTag("sync_dialog_passwords_backup").assertDoesNotExist()
        composeTestRule.onNodeWithTag("sync_dialog_automations_backup").assertDoesNotExist()

        // Select Weekly package ($1.17 / 7 days)
        composeTestRule.onNodeWithTag("pro_package_week").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(ProSubscriptionPackage.WEEK, selectedPkg)
        composeTestRule.onNodeWithText("Contact Author ($1.17)").performScrollTo().assertIsDisplayed()

        // Test invalid key feedback first
        composeTestRule.onNodeWithTag("pro_activation_key_input").performScrollTo().performTextInput("bad_key")
        composeTestRule.onNodeWithTag("pro_activate_button").performScrollTo().assertIsEnabled().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pro_activation_error").performScrollTo().assertIsDisplayed()

        // Tap 1-tap Paste icon button to paste sk_12345 from clipboard, then activate
        composeTestRule.onNodeWithTag("pro_paste_key_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pro_activate_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertTrue(isPro)
        assertEquals("sk_12345", activatedWithKey)
        assertEquals(ProSubscriptionPackage.WEEK, activePkg)
        composeTestRule.onNodeWithText("Apex Pro Active").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pro_validity_summary").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("pro_deactivate_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun subscriptionActivity_launchesWithPromptReasonExtra() {
        val intent = SubscriptionActivity.createIntent(
            context = context,
            promptReason = "Daily limit reached"
        )
        val controller = Robolectric.buildActivity(SubscriptionActivity::class.java, intent).setup()
        val activity = controller.get()
        assertNotNull(activity)
        assertEquals("Daily limit reached", activity.intent.getStringExtra(SubscriptionActivity.EXTRA_PROMPT_REASON))
        controller.pause().stop().destroy()
    }
}
