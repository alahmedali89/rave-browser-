package com.example

import android.content.Context
import android.os.Looper
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.automation.ActiveDomElementHighlight
import com.example.automation.ActiveRecordingSession
import com.example.automation.AutomationActionType
import com.example.automation.AutomationEngine
import com.example.automation.AutomationHudMode
import com.example.automation.AutomationStep
import com.example.automation.AutomationStepCodec
import com.example.automation.AutomationToolbarStatus
import com.example.automation.AutomationType
import com.example.automation.DetectedPageElement
import com.example.automation.ElementBoundingBox
import com.example.automation.PageInspectionState
import com.example.automation.RecordedDomEvent
import com.example.automation.RecordingStatus
import com.example.automation.WorkflowExecutionState
import com.example.data.local.ApexDatabase
import com.example.data.local.AutomationExecutionEntity
import com.example.data.local.AutomationWorkflowEntity
import com.example.data.preferences.BrowserPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.BrowserRepository
import com.example.engine.FastDownloadEngine
import com.example.ui.browser.AutomationDetailsScreenOverlay
import com.example.ui.browser.AutomationHudSheet
import com.example.ui.browser.AutomationWorkspaceScreenOverlay
import com.example.ui.browser.BrowserScreen
import com.example.ui.browser.CompactTopToolbar
import com.example.ui.browser.DomScannerVisualIndicatorOverlay
import com.example.ui.browser.SettingsScreenOverlay
import com.example.ui.theme.ApexBrowserTheme
import com.example.ui.viewmodel.BrowserTab
import com.example.ui.viewmodel.BrowserViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AutomationUiRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun compactTopToolbar_showsPermanentFlutterDashAndAllSevenStates() {
        var activeTab by mutableStateOf(BrowserTab(title = "Home", url = "apex://home"))
        var status by mutableStateOf(AutomationToolbarStatus.IDLE)
        var detectedCount by mutableStateOf(0)
        var clickedCount = 0

        composeTestRule.setContent {
            ApexBrowserTheme {
                CompactTopToolbar(
                    visible = true,
                    activeTab = activeTab,
                    onAddressPillClick = {},
                    onReloadClick = {},
                    automationStatus = status,
                    detectedElementsCount = detectedCount,
                    onAutomationClick = { clickedCount++ }
                )
            }
        }

        // 1. Always visible on Home (Start Page)
        composeTestRule.onNodeWithTag("home_top_search_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("toolbar_automation_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("toolbar_automation_icon", useUnmergedTree = true).assertIsDisplayed()

        // 2. Remains visible on webpage even when 0 elements are detected
        activeTab = BrowserTab(title = "Example", url = "https://example.com/login")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("address_bar_container").assertIsDisplayed()
        composeTestRule.onNodeWithTag("toolbar_automation_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("toolbar_automation_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, clickedCount)

        // 3. Elements detected indicator with count badge
        detectedCount = 6
        status = AutomationToolbarStatus.ELEMENTS_DETECTED
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("toolbar_automation_badge_elements", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("6", useUnmergedTree = true).assertIsDisplayed()

        // 4. Recording indicator
        status = AutomationToolbarStatus.RECORDING
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("toolbar_automation_badge_recording", useUnmergedTree = true).assertIsDisplayed()

        // 5. Paused indicator
        status = AutomationToolbarStatus.PAUSED
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("toolbar_automation_badge_paused", useUnmergedTree = true).assertIsDisplayed()

        // 6. Executing indicator
        status = AutomationToolbarStatus.EXECUTING
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("toolbar_automation_badge_executing", useUnmergedTree = true).assertIsDisplayed()

        // 7. Completed indicator
        status = AutomationToolbarStatus.COMPLETED
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("toolbar_automation_badge_completed", useUnmergedTree = true).assertIsDisplayed()

        // 8. Error indicator
        status = AutomationToolbarStatus.ERROR
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("toolbar_automation_badge_error", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun settingsScreen_placesAutomationsAfterAccessibilityBeforeBrowsingAndOpensWorkspace() {
        var openedAutomations = false

        composeTestRule.setContent {
            ApexBrowserTheme {
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
                    onOpenAutomations = { openedAutomations = true },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("settings_scroll_list")
            .performScrollToNode(hasTestTag("settings_browsing_card"))
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_languages_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("settings_accessibility_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_automations_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_browsing_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Automations").assertIsDisplayed()

        val accessibilityTop = composeTestRule.onNodeWithTag("settings_accessibility_card").fetchSemanticsNode().boundsInRoot.top
        val automationsTop = composeTestRule.onNodeWithTag("settings_automations_card").fetchSemanticsNode().boundsInRoot.top
        val browsingTop = composeTestRule.onNodeWithTag("settings_browsing_card").fetchSemanticsNode().boundsInRoot.top

        assertTrue("Automations must appear below Accessibility", accessibilityTop < automationsTop)
        assertTrue("Automations must appear before Browsing", automationsTop < browsingTop)

        composeTestRule.onNodeWithTag("settings_automations_card").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertTrue(openedAutomations)
    }

    @Test
    fun automationHud_rendersOverviewRecordingAndInstructionsFlows() {
        var hudMode by mutableStateOf(AutomationHudMode.OVERVIEW)
        var recordingSession by mutableStateOf(ActiveRecordingSession())
        var instructionText by mutableStateOf("")
        var instructionSteps by mutableStateOf(emptyList<AutomationStep>())
        var executionState by mutableStateOf<WorkflowExecutionState?>(null)
        var minimizeCalled = false
        var savedRecordingCalled = false
        var destructiveConfirmed = false

        val elements = listOf(
            DetectedPageElement("el_0", "input", "email", "Email", "#email", "email"),
            DetectedPageElement("el_1", "button", "button", "Continue", "#continue", "")
        )

        composeTestRule.setContent {
            ApexBrowserTheme {
                AutomationHudSheet(
                    hudMode = hudMode,
                    pageInspection = PageInspectionState(
                        domain = "example.com",
                        url = "https://example.com/signup",
                        elements = elements
                    ),
                    recordingSession = recordingSession,
                    instructionDraftText = instructionText,
                    instructionDraftSteps = instructionSteps,
                    executionState = executionState,
                    workflows = emptyList(),
                    executions = emptyList(),
                    onSelectMode = { hudMode = it },
                    onScanPage = {},
                    onStartRecording = {
                        hudMode = AutomationHudMode.RECORDING
                        recordingSession = ActiveRecordingSession(
                            status = RecordingStatus.RECORDING,
                            targetDomain = "example.com",
                            targetUrl = "https://example.com/signup",
                            suggestedName = "example.com Signup",
                            steps = listOf(
                                AutomationStep(
                                    id = "s1",
                                    order = 1,
                                    actionType = AutomationActionType.SELECT,
                                    targetLabel = "Email field",
                                    description = "Email field selected"
                                ),
                                AutomationStep(
                                    id = "s2",
                                    order = 2,
                                    actionType = AutomationActionType.INPUT,
                                    targetLabel = "Email",
                                    value = "ada@example.com",
                                    description = "Email entered"
                                )
                            )
                        )
                    },
                    onPauseRecording = {
                        recordingSession = recordingSession.copy(status = RecordingStatus.PAUSED)
                    },
                    onResumeRecording = {
                        recordingSession = recordingSession.copy(status = RecordingStatus.RECORDING)
                    },
                    onStopRecording = {
                        recordingSession = recordingSession.copy(status = RecordingStatus.STOPPED)
                    },
                    onMinimizeRecording = { minimizeCalled = true },
                    onRenameRecordingDraft = {
                        recordingSession = recordingSession.copy(suggestedName = it)
                    },
                    onEditRecordingStep = { _, _, _ -> },
                    onDeleteRecordingStep = { id ->
                        recordingSession = recordingSession.copy(
                            steps = recordingSession.steps.filterNot { it.id == id }
                        )
                    },
                    onSaveRecording = { savedRecordingCalled = true },
                    onDiscardRecording = {
                        recordingSession = ActiveRecordingSession()
                        hudMode = AutomationHudMode.OVERVIEW
                    },
                    onInstructionTextChange = { instructionText = it },
                    onAddInstructions = {
                        instructionSteps = AutomationEngine().parseInstructionsToSteps(
                            instructionText,
                            "https://example.com/signup"
                        )
                    },
                    onClearInstructions = {
                        instructionText = ""
                        instructionSteps = emptyList()
                    },
                    onReviewInstructionSteps = {
                        if (instructionSteps.isEmpty() && instructionText.isNotBlank()) {
                            instructionSteps = AutomationEngine().parseInstructionsToSteps(
                                instructionText,
                                "https://example.com/signup"
                            )
                        }
                    },
                    onEditInstructionStep = { _, _, _, _ -> },
                    onDeleteInstructionStep = { id ->
                        instructionSteps = instructionSteps.filterNot { it.id == id }
                    },
                    onMoveInstructionStepUp = {},
                    onMoveInstructionStepDown = {},
                    onSaveInstructionsAsWorkflow = {},
                    onExecuteInstructions = {
                        val destructive = instructionSteps.filter { it.isDestructive }
                        if (destructive.isNotEmpty()) {
                            executionState = WorkflowExecutionState(
                                workflowId = 0L,
                                workflowName = "Manual Instructions",
                                status = "AWAITING_CONFIRMATION",
                                currentStepIndex = 0,
                                totalSteps = instructionSteps.size,
                                isAwaitingConfirmation = true,
                                pendingDestructiveSteps = destructive
                            )
                        }
                    },
                    onRunWorkflow = {},
                    onOpenWorkflowDetails = {},
                    onOpenWorkspace = {},
                    onOpenNanoRetrieverPanel = {},
                    onConfirmDestructiveExecution = {
                        destructiveConfirmed = true
                        executionState = null
                    },
                    onCancelDestructiveExecution = { executionState = null },
                    onClearExecutionBanner = { executionState = null },
                    onDismiss = {}
                )
            }
        }

        // 1. Overview structure
        composeTestRule.onNodeWithText("Apex Automation").assertIsDisplayed()
        composeTestRule.onNodeWithText("example.com").assertIsDisplayed()
        composeTestRule.onNodeWithText("2 detected").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hud_mode_record_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hud_mode_instructions_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("hud_scan_page_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("hud_view_elements_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("hud_extended_scan_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("hud_quick_saved").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("hud_quick_create").assertDoesNotExist()
        composeTestRule.onNodeWithTag("hud_quick_recent").assertDoesNotExist()
        composeTestRule.onNodeWithTag("hud_quick_nano_retriever").assertDoesNotExist()
        composeTestRule.onNodeWithTag("hud_open_workspace_button").performScrollTo().assertIsDisplayed()

        // 2. Recording flow: Start -> Pause -> Resume -> Stop -> Rename & Save
        composeTestRule.onNodeWithTag("hud_mode_record_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Recording actions…").assertIsDisplayed()
        composeTestRule.onNodeWithText("Email field selected").assertIsDisplayed()
        composeTestRule.onNodeWithTag("recording_minimize_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertTrue(minimizeCalled)

        composeTestRule.onNodeWithTag("recording_pause_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Recording paused").assertIsDisplayed()

        composeTestRule.onNodeWithTag("recording_resume_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Recording actions…").assertIsDisplayed()

        composeTestRule.onNodeWithTag("recording_stop_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("recording_name_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("recording_discard_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("recording_save_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertTrue(savedRecordingCalled)

        // 3. Manual Instructions flow + Destructive confirmation guard
        hudMode = AutomationHudMode.INSTRUCTIONS
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("instructions_input_field").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("instructions_input_field")
            .performTextInput("Enter \"ada@example.com\" in email\nSubmit delete account form")
        composeTestRule.onNodeWithTag("instructions_add_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("instruction_step_row_1").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("instruction_step_row_2").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("instructions_execute_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("destructive_confirm_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("destructive_confirm_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(destructiveConfirmed)
    }

    @Test
    fun workspaceAndDetailsScreens_displayUnifiedWorkflowsSearchFilterAndStepEdits() {
        val sampleSteps = listOf(
            AutomationStep("s1", 1, AutomationActionType.INPUT, "Email", "#email", "ada@apex.dev", "Email entered"),
            AutomationStep("s2", 2, AutomationActionType.CLICK, "Continue", "#continue", "", "Continue button clicked")
        )
        val recordedWf = AutomationWorkflowEntity(
            id = 1L,
            name = "Login Recorder",
            domain = "auth.example.com",
            sourceUrl = "https://auth.example.com",
            type = AutomationType.RECORDED.id,
            stepsJson = AutomationStepCodec.encode(sampleSteps),
            stepCount = 2,
            lastExecutionStatus = "COMPLETED",
            lastExecutedAt = 1_700_000_000_000L
        )
        val manualWf = AutomationWorkflowEntity(
            id = 2L,
            name = "Checkout Instructions",
            domain = "shop.example.com",
            sourceUrl = "https://shop.example.com",
            type = AutomationType.MANUAL.id,
            stepsJson = AutomationStepCodec.encode(sampleSteps),
            stepCount = 2
        )

        var workflows by mutableStateOf(listOf(recordedWf, manualWf))
        var selectedDetailsId by mutableStateOf<Long?>(null)

        composeTestRule.setContent {
            ApexBrowserTheme {
                val activeWf = selectedDetailsId?.let { id -> workflows.firstOrNull { it.id == id } }
                if (activeWf == null) {
                    AutomationWorkspaceScreenOverlay(
                        workflows = workflows,
                        executions = listOf(
                            AutomationExecutionEntity(
                                id = 10L,
                                workflowId = 1L,
                                workflowName = "Login Recorder",
                                domain = "auth.example.com",
                                status = "COMPLETED",
                                totalSteps = 2,
                                completedSteps = 2,
                                verificationSummary = "Verified 2/2 steps"
                            )
                        ),
                        activeTabDomain = "example.com",
                        activeTabUrl = "https://example.com",
                        executionState = null,
                        onCreateWorkflow = { name, domain, url, instructions ->
                            val parsed = AutomationEngine().parseInstructionsToSteps(instructions, url)
                            workflows = workflows + AutomationWorkflowEntity(
                                id = 3L,
                                name = name.ifBlank { "Created Flow" },
                                domain = domain.ifBlank { "example.com" },
                                sourceUrl = url,
                                type = AutomationType.MANUAL.id,
                                stepsJson = AutomationStepCodec.encode(parsed),
                                stepCount = parsed.size
                            )
                        },
                        onRunWorkflow = {},
                        onOpenWorkflowDetails = { selectedDetailsId = it },
                        onRenameWorkflow = { id, newName ->
                            workflows = workflows.map { if (it.id == id) it.copy(name = newName) else it }
                        },
                        onDuplicateWorkflow = {},
                        onDeleteWorkflow = { id ->
                            workflows = workflows.filterNot { it.id == id }
                        },
                        onExportJson = {},
                        onImportJson = {},
                        onConfirmDestructiveExecution = {},
                        onCancelDestructiveExecution = {},
                        onClearExecutionBanner = {},
                        onDismiss = {}
                    )
                } else {
                    AutomationDetailsScreenOverlay(
                        workflow = activeWf,
                        executions = emptyList(),
                        executionState = null,
                        onRunWorkflow = {},
                        onRenameWorkflow = { id, newName ->
                            workflows = workflows.map { if (it.id == id) it.copy(name = newName) else it }
                        },
                        onUpdateSteps = { id, updated ->
                            val reindexed = AutomationStepCodec.reindex(updated)
                            workflows = workflows.map {
                                if (it.id == id) {
                                    it.copy(
                                        stepsJson = AutomationStepCodec.encode(reindexed),
                                        stepCount = reindexed.size
                                    )
                                } else {
                                    it
                                }
                            }
                        },
                        onAddStep = { id, action, target, value, desc ->
                            val current = AutomationStepCodec.decode(activeWf.stepsJson)
                            val updated = AutomationStepCodec.reindex(
                                current + AutomationStep(
                                    id = "s_new",
                                    order = current.size + 1,
                                    actionType = action,
                                    targetLabel = target,
                                    value = value,
                                    description = desc
                                )
                            )
                            workflows = workflows.map {
                                if (it.id == id) {
                                    it.copy(
                                        stepsJson = AutomationStepCodec.encode(updated),
                                        stepCount = updated.size
                                    )
                                } else {
                                    it
                                }
                            }
                        },
                        onConfirmDestructiveExecution = {},
                        onCancelDestructiveExecution = {},
                        onClearExecutionBanner = {},
                        onDismiss = { selectedDetailsId = null }
                    )
                }
            }
        }

        // 1. All category shows both Recorded & Manual workflows; app bar only keeps Vault and More (3-dots)
        composeTestRule.onNodeWithTag("automation_workspace_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("automation_workspace_vault_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("automation_workspace_more_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("automation_workspace_create_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("automation_workspace_sort_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("workspace_automation_vault_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("automation_workflow_card_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("automation_workflow_card_2").assertIsDisplayed()

        // 2. Category filter narrows to Recorded only
        composeTestRule.onNodeWithTag("workspace_category_recorded").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("automation_workflow_card_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("automation_workflow_card_2").assertDoesNotExist()

        // 3. Search filter across All
        composeTestRule.onNodeWithTag("workspace_category_all").performClick()
        composeTestRule.onNodeWithTag("automation_workspace_search_input").performTextInput("Checkout")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("automation_workflow_card_2").assertIsDisplayed()
        composeTestRule.onNodeWithTag("automation_workflow_card_1").assertDoesNotExist()

        composeTestRule.onNodeWithTag("automation_workspace_search_input").performTextClearance()
        composeTestRule.waitForIdle()

        // 4. Open Details for workflow 1 -> reorder step & delete step
        composeTestRule.onNodeWithTag("workflow_edit_button_1").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("automation_details_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("automation_details_list")
            .performScrollToNode(hasTestTag("details_step_row_1"))
        composeTestRule.onNodeWithTag("details_step_row_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("automation_details_list")
            .performScrollToNode(hasTestTag("details_step_row_2"))
        composeTestRule.onNodeWithTag("details_step_row_2").assertIsDisplayed()

        // Move step 2 up to position 1
        composeTestRule.onNodeWithTag("details_step_up_2").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        val reordered = AutomationStepCodec.decode(workflows.first { it.id == 1L }.stepsJson)
        assertEquals("Continue button clicked", reordered.first().description)

        // Delete step 2
        composeTestRule.onNodeWithTag("automation_details_list")
            .performScrollToNode(hasTestTag("details_step_row_2"))
        composeTestRule.onNodeWithTag("details_step_delete_2").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, workflows.first { it.id == 1L }.stepCount)
    }

    @Test
    fun repositoryAndViewModel_persistRecordedAndManualAutomationsInUnifiedLibrary() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val prefsRepo = UserPreferencesRepository(context)
        val sharedPrefs = context.getSharedPreferences("apex_automation_test_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().clear().commit()

        val repo = BrowserRepository(db.apexDao(), prefsRepo, sharedPrefs)
        val downloadEngine = FastDownloadEngine(context, repo)
        val viewModel = BrowserViewModel(repository = repo, fastDownloadEngine = downloadEngine)
        val collectorJob = launch { viewModel.automationWorkflowsState.collect {} }

        val tabId = viewModel.uiState.value.activeTabId
        val recordingWebView = object : WebView(context) {
            override fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {
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
        viewModel.submitNavigation("https://apex.dev/login", tabId)
        viewModel.registerWebView(tabId, recordingWebView)

        try {
            // 1. Start recording, capture events, pause, resume, stop, and save to Room
            viewModel.startRecording()
            val session = viewModel.uiState.value.recordingSession
            assertEquals(RecordingStatus.RECORDING, session.status)
            assertEquals(AutomationToolbarStatus.RECORDING, viewModel.uiState.value.automationToolbarStatus)

            viewModel.onRecordedDomEvent(
                RecordedDomEvent(session.sessionId, tabId, "https://apex.dev", 1L, "SELECT", "Email field", "#email", "", false),
                tabId, recordingWebView, "https://apex.dev/login"
            )
            viewModel.onRecordedDomEvent(
                RecordedDomEvent(session.sessionId, tabId, "https://apex.dev", 2L, "INPUT", "Email", "#email", "user@apex.dev", false),
                tabId, recordingWebView, "https://apex.dev/login"
            )
            viewModel.onRecordedDomEvent(
                RecordedDomEvent(session.sessionId, tabId, "https://apex.dev", 3L, "CLICK", "Continue button", "#continue", "", false),
                tabId, recordingWebView, "https://apex.dev/login"
            )
            assertEquals(3, viewModel.uiState.value.recordingSession.steps.size)

            viewModel.pauseRecording()
            assertEquals(RecordingStatus.PAUSED, viewModel.uiState.value.recordingSession.status)
            assertEquals(AutomationToolbarStatus.PAUSED, viewModel.uiState.value.automationToolbarStatus)
            viewModel.onRecordedDomEvent(
                RecordedDomEvent(session.sessionId, tabId, "https://apex.dev", 4L, "CLICK", "Ignored while paused", "#ignored", "", false),
                tabId, recordingWebView, "https://apex.dev/login"
            )
            assertEquals(3, viewModel.uiState.value.recordingSession.steps.size)

            viewModel.resumeRecording()
            assertEquals(RecordingStatus.RECORDING, viewModel.uiState.value.recordingSession.status)
            viewModel.stopRecording()
            assertEquals(RecordingStatus.STOPPED, viewModel.uiState.value.recordingSession.status)

            viewModel.saveRecordingWorkflow("Recorded Sign In")
            waitForCondition(timeoutMs = 3_000L) {
                viewModel.automationWorkflowsState.value.any { it.name == "Recorded Sign In" }
            }

            // 2. Manual instructions draft parsing, destructive guard, and saving
            viewModel.onInstructionDraftChanged("Open https://example.com\nEnter \"hello\" in search\nSubmit order payment")
            viewModel.addInstructionsToDraft()
            assertEquals(3, viewModel.uiState.value.instructionDraftSteps.size)
            assertTrue(viewModel.uiState.value.instructionDraftSteps.last().isDestructive)

            viewModel.executeInstructionDraft(confirmDestructive = false)
            assertTrue(viewModel.uiState.value.executionState?.isAwaitingConfirmation == true)
            viewModel.cancelPendingDestructiveExecution()
            assertNull(viewModel.uiState.value.executionState)

            viewModel.saveInstructionDraftAsWorkflow("Manual Search Flow")
            waitForCondition(timeoutMs = 3_000L) {
                viewModel.automationWorkflowsState.value.any { it.name == "Manual Search Flow" }
            }

            val all = viewModel.automationWorkflowsState.value
            assertEquals(2, all.size)
            val recorded = all.first { it.name == "Recorded Sign In" }
            val manual = all.first { it.name == "Manual Search Flow" }
            assertEquals(AutomationType.RECORDED.id, recorded.type)
            assertEquals(AutomationType.MANUAL.id, manual.type)

            // 3. Rename, duplicate, and JSON export/import round-trip
            viewModel.renameWorkflow(recorded.id, "Sign In V2")
            waitForCondition(timeoutMs = 3_000L) {
                viewModel.automationWorkflowsState.value.any { it.name == "Sign In V2" }
            }
            viewModel.duplicateWorkflow(manual.id)
            waitForCondition(timeoutMs = 3_000L) {
                viewModel.automationWorkflowsState.value.size == 3
            }

            val exportedJson = repo.exportAutomationsAsJson()
            assertTrue(exportedJson.contains("Sign In V2"))
            assertTrue(exportedJson.contains("Manual Search Flow"))
            assertNotNull(repo.getAutomationWorkflowById(recorded.id))
            assertFalse(AutomationStepCodec.decode(recorded.stepsJson).isEmpty())
        } finally {
            collectorJob.cancel()
            db.close()
        }
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    @Test
    fun mainActivityAndFullBrowserScreen_launchWithoutCrashAndOpenAutomationHud() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        assertNotNull(activity)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        try {
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("start_page_hub").assertIsDisplayed()
            composeTestRule.onNodeWithTag("toolbar_automation_button").assertIsDisplayed()
            composeTestRule.onNodeWithTag("toolbar_automation_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("Apex Automation").assertIsDisplayed()
        } finally {
            controller.pause().stop().destroy()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun domScannerVisualIndicatorOverlay_rendersOutlinesPillAndTogglesControls() {
        var dismissed = false
        var outlinesEnabled by mutableStateOf(true)
        val sampleBox = ElementBoundingBox(
            left = 24f,
            top = 120f,
            width = 240f,
            height = 44f,
            viewportWidth = 412f,
            viewportHeight = 820f
        )
        val activeHighlight = ActiveDomElementHighlight(
            selector = "#checkout-submit",
            targetLabel = "Confirm Order Button",
            actionLabel = "CLICK",
            tagName = "button",
            boundingBox = sampleBox,
            isScanning = false,
            stepIndex = 2,
            totalSteps = 5,
            status = "ACTIVE"
        )
        val scannedElements = listOf(
            DetectedPageElement(
                id = "el_0",
                tagName = "button",
                role = "button",
                label = "Confirm Order Button",
                selector = "#checkout-submit",
                boundingBox = sampleBox
            )
        )

        composeTestRule.setContent {
            ApexBrowserTheme {
                DomScannerVisualIndicatorOverlay(
                    activeHighlight = activeHighlight,
                    scannedElements = scannedElements,
                    isOutlineEnabled = outlinesEnabled,
                    isAutomationRunning = true,
                    onDismissHighlight = { dismissed = true },
                    onToggleOutlines = { outlinesEnabled = !outlinesEnabled }
                )
            }
        }

        composeTestRule.onNodeWithTag("dom_scanner_visual_indicator_overlay").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dom_scanner_visual_indicator_canvas").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dom_scanner_visual_indicator_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dom_scanner_active_action_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dom_scanner_active_step_counter").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dom_scanner_active_target_label").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dom_scanner_active_selector_label").assertIsDisplayed()

        composeTestRule.onNodeWithTag("dom_scanner_toggle_outlines_btn").performClick()
        assertFalse(outlinesEnabled)

        composeTestRule.onNodeWithTag("dom_scanner_dismiss_indicator_btn").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun automationHudSheet_homePageKeepsOnlySavedAndWorkspace_andExpandButtonTogglesSize() {
        var hudMode by mutableStateOf(AutomationHudMode.OVERVIEW)
        var workspaceOpened = false

        composeTestRule.setContent {
            ApexBrowserTheme {
                AutomationHudSheet(
                    hudMode = hudMode,
                    pageInspection = PageInspectionState(domain = "Start Page", url = "apex://home"),
                    isHomePage = true,
                    recordingSession = ActiveRecordingSession(),
                    instructionDraftText = "",
                    instructionDraftSteps = emptyList(),
                    executionState = null,
                    workflows = emptyList(),
                    executions = emptyList(),
                    onSelectMode = { hudMode = it },
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
                    onOpenWorkspace = { workspaceOpened = true },
                    onOpenNanoRetrieverPanel = {},
                    onConfirmDestructiveExecution = {},
                    onCancelDestructiveExecution = {},
                    onClearExecutionBanner = {},
                    onDismiss = {}
                )
            }
        }

        // Home Page HUD keeps only Saved Automations & Open Full Workspace
        composeTestRule.onNodeWithTag("hud_quick_saved").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hud_open_workspace_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hud_mode_record_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("hud_mode_instructions_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("hud_scan_page_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("hud_quick_create").assertDoesNotExist()
        composeTestRule.onNodeWithTag("hud_quick_recent").assertDoesNotExist()
        composeTestRule.onNodeWithTag("hud_quick_nano_retriever").assertDoesNotExist()

        // Arrow dropdown beside Close toggles collapse <-> expand cleanly
        val initialBounds = composeTestRule.onNodeWithTag("automation_hud_sheet").getBoundsInRoot()
        composeTestRule.onNodeWithTag("automation_hud_expand_button")
            .assertContentDescriptionEquals("Collapse HUD")
            .performClick()
        composeTestRule.waitForIdle()

        val collapsedBounds = composeTestRule.onNodeWithTag("automation_hud_sheet").getBoundsInRoot()
        assertTrue(
            "Expected collapsed HUD to move down or shrink (initial=$initialBounds, collapsed=$collapsedBounds)",
            collapsedBounds.top > initialBounds.top || collapsedBounds.height < initialBounds.height
        )
        composeTestRule.onNodeWithTag("automation_hud_expand_button")
            .assertContentDescriptionEquals("Expand HUD")
            .performClick()
        composeTestRule.waitForIdle()

        val expandedBounds = composeTestRule.onNodeWithTag("automation_hud_sheet").getBoundsInRoot()
        assertTrue(
            "Expected re-expanded HUD to move up or grow (collapsed=$collapsedBounds, expanded=$expandedBounds)",
            expandedBounds.top < collapsedBounds.top || expandedBounds.height > collapsedBounds.height
        )
        composeTestRule.onNodeWithTag("automation_hud_expand_button")
            .assertContentDescriptionEquals("Collapse HUD")

        // Saved Automations & Workspace actions work cleanly from Home Page HUD
        composeTestRule.onNodeWithTag("hud_open_workspace_button").performClick()
        assertTrue(workspaceOpened)

        composeTestRule.onNodeWithTag("hud_quick_saved").performClick()
        composeTestRule.waitForIdle()
        assertEquals(AutomationHudMode.SAVED, hudMode)
    }

    private suspend fun waitForCondition(timeoutMs: Long, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate() && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            delay(25L)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Timed out waiting for condition", predicate())
    }
}
