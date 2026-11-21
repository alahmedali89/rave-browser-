package com.example

import android.content.Context
import android.os.Looper
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.automation.ActiveRecordingSession
import com.example.automation.AutomationActionType
import com.example.automation.AutomationEngine
import com.example.automation.AutomationHudMode
import com.example.automation.AutomationStep
import com.example.automation.PageInspectionState
import com.example.automation.RecordedDomEvent
import com.example.automation.RecordingStatus
import com.example.automation.UniversalActionScripts
import com.example.data.local.ApexDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.BrowserRepository
import com.example.engine.FastDownloadEngine
import com.example.ui.browser.AutomationHudSheet
import com.example.ui.theme.ApexBrowserTheme
import com.example.ui.viewmodel.BrowserViewModel
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EnhancedRecorderTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class RecorderEvalWebView(
        context: Context,
        var fakeUrl: String = "https://shop.example.com/cart"
    ) : WebView(context) {
        val evaluatedScripts = mutableListOf<String>()

        override fun getUrl(): String = fakeUrl

        override fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {
            evaluatedScripts.add(script)
            val sidMatch = Regex("""sessionId:\s*("(?:[^"\\]|\\.)*")""").find(script)
            val sid = sidMatch?.groupValues?.getOrNull(1)?.let { JSONObject("{\"v\":$it}").optString("v") } ?: ""
            val payload = if (script.contains("__apexRecorderInstance = inst")) {
                JSONObject().apply {
                    put("ok", true)
                    put("sessionId", sid)
                }.toString()
            } else {
                JSONObject().apply {
                    put("ok", true)
                    put("removed", true)
                }.toString()
            }
            resultCallback?.onReceiveValue(JSONObject.quote(payload))
        }
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

    @Test
    fun buildRecorderInstallScript_registersAndCleansUpAllMinuteInteractionListenersSafely() {
        val engine = AutomationEngine()
        val script = engine.buildRecorderInstallScript(
            sessionId = "rec_test_1",
            tabId = "tab_1",
            expectedOrigin = "https://shop.example.com:443",
            initialSequenceNumber = 0L
        )

        val expectedListeners = listOf(
            "focusin",
            "focusout",
            "input",
            "change",
            "click",
            "dblclick",
            "contextmenu",
            "keydown",
            "scroll",
            "submit",
            "pointerdown",
            "pointermove",
            "pointerup",
            "pointercancel",
            "touchstart",
            "touchmove",
            "touchend",
            "touchcancel",
            "mousedown",
            "mouseup"
        )
        expectedListeners.forEach { eventName ->
            assertTrue(
                "Missing addEventListener for $eventName",
                script.contains("document.addEventListener('$eventName'")
            )
            assertTrue(
                "Missing removeEventListener for $eventName",
                script.contains("document.removeEventListener('$eventName'")
            )
        }

        // Minute action emissions and security invariants
        assertTrue(script.contains("'LONG_PRESS'"))
        assertTrue(script.contains("'DOUBLE_CLICK'"))
        assertTrue(script.contains("'SCROLL'"))
        assertTrue(script.contains("'KEY_PRESS'"))
        assertTrue(script.contains("'CLEAR'"))
        assertTrue(script.contains("'TOGGLE'"))
        assertTrue(script.contains("isPwd ? ''"))
        assertFalse(script.contains("••••••"))
    }

    @Test
    fun processRecordedDomEvent_capturesMinuteInteractionsWithoutDroppingRepeatedClicks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        val prefs = UserPreferencesRepository(context)
        val sp = context.getSharedPreferences("enhanced_rec_${UUID.randomUUID()}", Context.MODE_PRIVATE)
        val repo = BrowserRepository(db.apexDao(), prefs, sp)
        val dl = FastDownloadEngine(context, repo)
        val store = ViewModelStore()
        val vm = ViewModelProvider(
            store,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return BrowserViewModel(
                        repository = repo,
                        fastDownloadEngine = dl,
                        automationEngine = AutomationEngine()
                    ) as T
                }
            }
        )[BrowserViewModel::class.java]
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        try {
            val tabId = vm.uiState.value.activeTabId
            val targetUrl = "https://shop.example.com/cart"
            vm.submitNavigation(targetUrl, tabId)
            val webView = RecorderEvalWebView(context, targetUrl)
            vm.registerWebView(tabId, webView)
            vm.startRecording()
            idleMainUntil { vm.uiState.value.recordingSession.status == RecordingStatus.RECORDING }

            val session = vm.uiState.value.recordingSession
            val origin = session.currentOrigin
            val sid = session.sessionId

            // 1. Page-level Scroll (must keep empty selector so replay scrolls window)
            val r1 = vm.processRecordedDomEvent(
                RecordedDomEvent(
                    sessionId = sid,
                    tabId = tabId,
                    origin = origin,
                    sequenceNumber = 1L,
                    eventType = "SCROLL",
                    targetLabel = "Page down (340px)",
                    selector = "",
                    value = "340",
                    isDestructive = false
                ),
                tabId,
                webView,
                targetUrl
            )
            assertTrue(r1.success)

            // 2. Repeated Click #1 on + button
            val r2 = vm.processRecordedDomEvent(
                RecordedDomEvent(
                    sessionId = sid,
                    tabId = tabId,
                    origin = origin,
                    sequenceNumber = 2L,
                    eventType = "CLICK",
                    targetLabel = "Increase quantity button",
                    selector = "#qty-plus",
                    value = "",
                    isDestructive = false
                ),
                tabId,
                webView,
                targetUrl
            )
            assertTrue(r2.success)

            // 3. Repeated Click #2 on + button (must NOT be deduplicated!)
            val r3 = vm.processRecordedDomEvent(
                RecordedDomEvent(
                    sessionId = sid,
                    tabId = tabId,
                    origin = origin,
                    sequenceNumber = 3L,
                    eventType = "CLICK",
                    targetLabel = "Increase quantity button",
                    selector = "#qty-plus",
                    value = "",
                    isDestructive = false
                ),
                tabId,
                webView,
                targetUrl
            )
            assertTrue(r3.success)

            // 4. Long Press
            val r4 = vm.processRecordedDomEvent(
                RecordedDomEvent(
                    sessionId = sid,
                    tabId = tabId,
                    origin = origin,
                    sequenceNumber = 4L,
                    eventType = "LONG_PRESS",
                    targetLabel = "Item options",
                    selector = "#item-card",
                    value = "hold:450ms",
                    isDestructive = false
                ),
                tabId,
                webView,
                targetUrl
            )
            assertTrue(r4.success)

            // 5. Double Click
            val r5 = vm.processRecordedDomEvent(
                RecordedDomEvent(
                    sessionId = sid,
                    tabId = tabId,
                    origin = origin,
                    sequenceNumber = 5L,
                    eventType = "DOUBLE_CLICK",
                    targetLabel = "Product photo",
                    selector = "#hero-img",
                    value = "",
                    isDestructive = false
                ),
                tabId,
                webView,
                targetUrl
            )
            assertTrue(r5.success)

            // 6. Toggle checked
            val r6 = vm.processRecordedDomEvent(
                RecordedDomEvent(
                    sessionId = sid,
                    tabId = tabId,
                    origin = origin,
                    sequenceNumber = 6L,
                    eventType = "TOGGLE",
                    targetLabel = "Gift wrap checkbox",
                    selector = "#gift-wrap",
                    value = "checked",
                    isDestructive = false
                ),
                tabId,
                webView,
                targetUrl
            )
            assertTrue(r6.success)

            // 7. Key Press
            val r7 = vm.processRecordedDomEvent(
                RecordedDomEvent(
                    sessionId = sid,
                    tabId = tabId,
                    origin = origin,
                    sequenceNumber = 7L,
                    eventType = "KEY_PRESS",
                    targetLabel = "Promo code",
                    selector = "#promo-code",
                    value = "Enter",
                    isDestructive = false
                ),
                tabId,
                webView,
                targetUrl
            )
            assertTrue(r7.success)

            // 8. Clear field
            val r8 = vm.processRecordedDomEvent(
                RecordedDomEvent(
                    sessionId = sid,
                    tabId = tabId,
                    origin = origin,
                    sequenceNumber = 8L,
                    eventType = "CLEAR",
                    targetLabel = "Promo code",
                    selector = "#promo-code",
                    value = "",
                    isDestructive = false
                ),
                tabId,
                webView,
                targetUrl
            )
            assertTrue(r8.success)

            val steps = vm.uiState.value.recordingSession.steps
            assertEquals(8, steps.size)

            assertEquals(AutomationActionType.SCROLL, steps[0].actionType)
            assertEquals("", steps[0].selector)
            assertEquals("340", steps[0].value)

            assertEquals(AutomationActionType.CLICK, steps[1].actionType)
            assertEquals(AutomationActionType.CLICK, steps[2].actionType)
            assertEquals("#qty-plus", steps[2].selector)

            assertEquals(AutomationActionType.LONG_PRESS, steps[3].actionType)
            assertEquals("hold:450ms", steps[3].value)

            assertEquals(AutomationActionType.DOUBLE_CLICK, steps[4].actionType)

            assertEquals(AutomationActionType.TOGGLE, steps[5].actionType)
            assertEquals(true, steps[5].desiredCheckedState)

            assertEquals(AutomationActionType.KEY_PRESS, steps[6].actionType)
            assertEquals("Enter", steps[6].keyboardKey)

            assertEquals(AutomationActionType.CLEAR, steps[7].actionType)
        } finally {
            store.clear()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            db.close()
        }
    }

    @Test
    fun universalActionScripts_buildsReplayScriptsForDoubleClickLongPressAndDirectionalScroll() {
        val dblStep = AutomationStep(
            id = "s_dbl",
            order = 1,
            actionType = AutomationActionType.DOUBLE_CLICK,
            targetLabel = "Zoom photo",
            selector = "#photo",
            description = "Double-clicked Zoom photo"
        )
        val dblScript = UniversalActionScripts.buildStepExecutionScript(dblStep)
        assertTrue(dblScript.contains("dblclick"))

        val holdStep = AutomationStep(
            id = "s_hold",
            order = 2,
            actionType = AutomationActionType.LONG_PRESS,
            targetLabel = "Item card",
            selector = "#item-card",
            value = "hold:450ms",
            description = "Long-pressed Item card"
        )
        val holdScript = UniversalActionScripts.buildStepExecutionScript(holdStep)
        assertTrue(holdScript.contains("pointerdown"))
        assertTrue(holdScript.contains("contextmenu"))

        val pageScrollStep = AutomationStep(
            id = "s_scroll_page",
            order = 3,
            actionType = AutomationActionType.SCROLL,
            targetLabel = "Page down & right (120px)",
            selector = "",
            value = "x:40,y:120",
            description = "Scrolled Page down & right"
        )
        val pageScrollScript = UniversalActionScripts.buildStepExecutionScript(pageScrollStep)
        assertTrue(pageScrollScript.contains("window.scrollBy"))
        assertTrue(pageScrollScript.contains("x:40,y:120"))
    }

    @Test
    fun automationHudSheet_showsActionBadgesInRecordingAndHidesRemovedOverviewSections() {
        val steps = listOf(
            AutomationStep("1", 1, AutomationActionType.SCROLL, "Page down (200px)", "", "200", "Scrolled Page down (200px)"),
            AutomationStep("2", 2, AutomationActionType.LONG_PRESS, "Item", "#item", "hold:450ms", "Long-pressed Item"),
            AutomationStep("3", 3, AutomationActionType.DOUBLE_CLICK, "Photo", "#photo", "", "Double-clicked Photo")
        )
        composeTestRule.setContent {
            ApexBrowserTheme {
                AutomationHudSheet(
                    hudMode = AutomationHudMode.RECORDING,
                    pageInspection = PageInspectionState(domain = "shop.example.com"),
                    recordingSession = ActiveRecordingSession(
                        sessionId = "rec_ui",
                        targetDomain = "shop.example.com",
                        status = RecordingStatus.RECORDING,
                        steps = steps
                    ),
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
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("recorded_step_row_1").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Scroll").assertIsDisplayed()
        composeTestRule.onNodeWithTag("recorded_step_row_2").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Long Press").assertIsDisplayed()
        composeTestRule.onNodeWithTag("recorded_step_row_3").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Double Click").assertIsDisplayed()
    }

    @Test
    fun buildRecorderInstallScript_supportsBottomSheetsCalendarsReadonlyTriggersAndSubRoots() {
        val engine = AutomationEngine()
        val script = engine.buildRecorderInstallScript(
            sessionId = "rec_cal_1",
            tabId = "tab_cal_1",
            expectedOrigin = "https://shop.example.com:443",
            initialSequenceNumber = 0L
        )

        assertTrue(script.contains("isSheetOrOverlayNode"))
        assertTrue(script.contains("isPickerTriggerEl"))
        assertTrue(script.contains("lastTapEmitted"))
        assertTrue(script.contains("attachSubRootListeners"))
        assertTrue(script.contains("scanNestedRoots"))
        assertTrue(script.contains("subRootCleanups"))
        assertTrue(script.contains("[data-time]"))
        assertTrue(script.contains("[data-slot]"))
        assertTrue(script.contains(".flatpickr-day"))
        assertTrue(script.contains(".sheet-item"))
        assertTrue(script.contains(".sheet-title"))
        assertTrue(script.contains("radix-.*-\\d+"))
        assertTrue(script.contains("curSt.cursor === 'pointer'"))
        // Ensure readonly trigger CLICK suppression exemption is in place
        assertTrue(script.contains("!Boolean(el.readOnly) && !isPickerTriggerEl(el)"))
    }
}
