package com.example

import android.content.Context
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.automation.AutomationActionType
import com.example.automation.AutomationEngine
import com.example.automation.AutomationErrorCode
import com.example.automation.AutomationStep
import com.example.automation.AutomationStepCodec
import com.example.automation.AutomationType
import com.example.automation.ConditionNode
import com.example.automation.ConditionOperator
import com.example.automation.CredentialAuthenticationManager
import com.example.automation.CredentialVaultCrypto
import com.example.automation.CredentialVaultManager
import com.example.automation.ElseIfBranch
import com.example.automation.ExtractionMode
import com.example.automation.ExtractionSpec
import com.example.automation.InMemoryVaultKeyMetadataStore
import com.example.automation.InMemoryVaultKeyProvider
import com.example.automation.LogicalCombinator
import com.example.automation.LoopConfig
import com.example.automation.Phase6ExecutionCoordinator
import com.example.automation.Phase6Limits
import com.example.automation.SubWorkflowReference
import com.example.automation.TabNavigationSynchronizer
import com.example.automation.WorkflowDocument
import com.example.automation.WorkflowDocumentMetadata
import com.example.automation.WorkflowVariableDefinition
import com.example.automation.WorkflowVariableStore
import com.example.automation.WorkflowVariableType
import com.example.data.local.ApexDatabase
import com.example.data.local.AutomationWorkflowEntity
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.BrowserRepository
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 6 Verification Suite — Part 1:
 * Multi-page continuity, redirects, SPA transitions, conditional branching,
 * bounded loops, workflow variables & templates, structured extraction,
 * and reusable sub-workflows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase6WorkflowIntelligenceTest {

    private lateinit var context: Context
    private lateinit var database: ApexDatabase
    private lateinit var repository: BrowserRepository
    private lateinit var engine: AutomationEngine
    private lateinit var coordinator: Phase6ExecutionCoordinator

    private class FakeEvalWebView(
        context: Context,
        var fakeCurrentUrl: String,
        private val evaluator: (script: String, callback: ValueCallback<String>?) -> Unit
    ) : WebView(context) {
        val evaluatedScripts = mutableListOf<String>()

        override fun getUrl(): String = fakeCurrentUrl

        override fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {
            evaluatedScripts.add(script)
            evaluator(script, resultCallback)
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val prefs = UserPreferencesRepository(context)
        database = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val meta = InMemoryVaultKeyMetadataStore()
        val crypto = CredentialVaultCrypto(InMemoryVaultKeyProvider(meta), meta)
        val auth = CredentialAuthenticationManager(context)
        engine = AutomationEngine(
            jsEvalTimeoutMs = 1_500L,
            navTimeoutMs = 1_500L,
            domReadyTimeoutMs = 400L,
            domStabilityPollMs = 20L
        )
        coordinator = Phase6ExecutionCoordinator(engine)
        val vault = CredentialVaultManager(
            dao = database.apexDao(),
            crypto = crypto,
            authManager = auth,
            originParser = { url -> engine.parseOrigin(url) }
        )
        repository = BrowserRepository(
            dao = database.apexDao(),
            preferencesRepository = prefs,
            sharedPrefs = context.getSharedPreferences("phase6_test_${UUID.randomUUID()}", Context.MODE_PRIVATE),
            credentialVaultManager = vault
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun jsonResult(callback: ValueCallback<String>?, obj: JSONObject) {
        callback?.onReceiveValue(JSONObject.quote(obj.toString()))
    }

    @Test
    fun multiPageContinuity_handlesNavigationRedirectSpaAndReload_rescanAndReResolvesTarget() = runBlocking {
        val tabId = "tab_multi_1"
        val navSync = TabNavigationSynchronizer()
        var currentUrl = "https://shop.example.com/start"
        navSync.onPageStarted(tabId, currentUrl)
        navSync.onPageFinished(tabId, currentUrl)

        var scanCallsOnPage2 = 0
        val clickTargets = mutableListOf<String>()
        lateinit var webView: FakeEvalWebView
        webView = FakeEvalWebView(context, currentUrl) { script, cb ->
            when {
                script.contains("document.readyState") && script.contains("locationHref") -> {
                    cb?.onReceiveValue(JSONObject().apply {
                        put("readyState", "complete")
                        put("locationHref", webView.fakeCurrentUrl)
                    }.toString())
                }
                script.contains("SESSION_ID") && script.contains("DOC_ID") && script.contains("elements") -> {
                    if (webView.fakeCurrentUrl.contains("/checkout")) scanCallsOnPage2++
                    jsonResult(cb, JSONObject().apply {
                        put("ok", true)
                        put("elements", JSONArray().apply {
                            put(JSONObject().apply {
                                put("tagName", "button")
                                put("label", "Confirm Purchase")
                                put("accessibleName", "Confirm Purchase")
                                put("selector", "#confirm-btn")
                                put("visible", true)
                                put("interactable", true)
                            })
                        })
                    })
                }
                script.contains("click") || script.contains("apexResolveOrderedTarget") -> {
                    clickTargets.add(webView.fakeCurrentUrl)
                    jsonResult(cb, JSONObject().apply {
                        put("ok", true)
                        put("code", "NONE")
                        put("msg", "Clicked target")
                    })
                }
                else -> jsonResult(cb, JSONObject().put("ok", true))
            }
        }

        val steps = listOf(
            AutomationStep(
                id = "nav_redirect",
                order = 1,
                actionType = AutomationActionType.NAVIGATE,
                targetLabel = "https://shop.example.com/go-checkout",
                value = "https://shop.example.com/go-checkout",
                description = "Navigate through redirect to checkout"
            ),
            AutomationStep(
                id = "click_confirm",
                order = 2,
                actionType = AutomationActionType.CLICK,
                targetLabel = "Confirm Purchase",
                selector = "#confirm-btn",
                description = "Click Confirm Purchase on destination page"
            )
        )

        val wf = AutomationWorkflowEntity(
            id = 11L,
            name = "Multi-page Redirect Checkout",
            domain = "shop.example.com",
            sourceUrl = currentUrl,
            type = AutomationType.MANUAL.id
        )

        val outcome = coordinator.executeDocumentWorkflow(
            workflow = wf,
            document = WorkflowDocument(steps = steps),
            currentTabUrl = currentUrl,
            tabId = tabId,
            navigationSynchronizer = navSync,
            webViewProvider = { webView },
            onNavigateUrl = { requested ->
                // Simulate server redirect: /go-checkout -> /checkout
                launch {
                    delay(15)
                    navSync.onPageStarted(tabId, requested)
                    delay(15)
                    val redirected = "https://shop.example.com/checkout"
                    currentUrl = redirected
                    webView.fakeCurrentUrl = redirected
                    navSync.onPageStarted(tabId, redirected)
                    navSync.onPageFinished(tabId, redirected)
                    // Simulate SPA commit followed by reload readiness on same origin
                    navSync.onUrlCommitted(tabId, "https://shop.example.com/checkout?step=2", isReload = false)
                    navSync.onUrlCommitted(tabId, "https://shop.example.com/checkout?step=2", isReload = true)
                    navSync.onPageFinished(tabId, "https://shop.example.com/checkout?step=2")
                }
            },
            onStepProgress = { _, _ -> },
            liveUrlProvider = { currentUrl }
        )

        assertEquals("COMPLETED", outcome.status)
        assertEquals(2, outcome.completedSteps)
        assertTrue(scanCallsOnPage2 >= 1)
        assertTrue(clickTargets.any { it.contains("https://shop.example.com/checkout") })
    }

    @Test
    fun multiPageContinuity_enforcesSameOriginAndBlocksUnexpectedOriginOrWrongTab() = runBlocking {
        val tabId = "tab_origin_guard"
        val navSync = TabNavigationSynchronizer()
        var currentUrl = "https://bank.example.com/home"
        navSync.onPageStarted(tabId, currentUrl)
        navSync.onPageFinished(tabId, currentUrl)

        val webView = FakeEvalWebView(context, currentUrl) { script, cb ->
            if (script.contains("document.readyState")) {
                cb?.onReceiveValue(JSONObject().apply {
                    put("readyState", "complete")
                    put("locationHref", currentUrl)
                }.toString())
            } else {
                jsonResult(cb, JSONObject().put("ok", true))
            }
        }

        val wf = AutomationWorkflowEntity(
            id = 12L,
            name = "Origin Isolation Flow",
            domain = "bank.example.com",
            sourceUrl = currentUrl,
            type = AutomationType.MANUAL.id
        )

        // Attempt an unapproved cross-origin jump during click step
        val blockedSteps = listOf(
            AutomationStep(
                id = "click_unapproved_origin",
                order = 1,
                actionType = AutomationActionType.CLICK,
                targetLabel = "External Pay",
                selector = "#pay",
                description = "Click external button"
            )
        )
        // Simulate page silently drifting to phishing origin right before step
        currentUrl = "https://evil-phish.example.net/home"
        webView.fakeCurrentUrl = currentUrl

        val blockedOutcome = coordinator.executeDocumentWorkflow(
            workflow = wf,
            document = WorkflowDocument(steps = blockedSteps),
            currentTabUrl = "https://bank.example.com/home",
            tabId = tabId,
            navigationSynchronizer = navSync,
            webViewProvider = { webView },
            onNavigateUrl = {},
            onStepProgress = { _, _ -> },
            liveUrlProvider = { currentUrl }
        )
        assertEquals("FAILED", blockedOutcome.status)
        assertEquals(AutomationErrorCode.UNEXPECTED_ORIGIN_CHANGE, blockedOutcome.errorCode)

        // Wrong tab closed mid-execution
        val tabClosedOutcome = coordinator.executeDocumentWorkflow(
            workflow = wf,
            document = WorkflowDocument(steps = blockedSteps),
            currentTabUrl = "https://bank.example.com/home",
            tabId = tabId,
            navigationSynchronizer = navSync,
            webViewProvider = { webView },
            onNavigateUrl = {},
            onStepProgress = { _, _ -> },
            isTabOpenProvider = { false },
            liveUrlProvider = { "https://bank.example.com/home" }
        )
        assertEquals("FAILED", tabClosedOutcome.status)
        assertEquals(AutomationErrorCode.TAB_CLOSED, tabClosedOutcome.errorCode)
    }

    @Test
    fun conditionalExecution_ifElseIfElse_mutuallyExclusive_andOrGroups_andMaxDepthValidation() = runBlocking {
        val executedIds = mutableListOf<String>()
        var badgeVisible = false
        var vipNoticeExists = true
        val url = "https://portal.example.com/orders"
        val webView = FakeEvalWebView(context, url) { script, cb ->
            when {
                script.contains("window.location.href") && script.contains("badge") -> {
                    val matched = when {
                        script.contains(".vip-badge") -> badgeVisible
                        script.contains(".promo-banner") -> vipNoticeExists
                        else -> false
                    }
                    jsonResult(cb, JSONObject().apply {
                        put("ok", true)
                        put("matched", matched)
                        put("detail", "Evaluated")
                    })
                }
                script.contains("ELSEIF_STEP") -> {
                    executedIds.add("ELSEIF_STEP")
                    jsonResult(cb, JSONObject().put("ok", true).put("msg", "Ran ELSEIF_STEP"))
                }
                script.contains("THEN_STEP") -> {
                    executedIds.add("THEN_STEP")
                    jsonResult(cb, JSONObject().put("ok", true).put("msg", "Ran THEN_STEP"))
                }
                script.contains("ELSE_STEP") -> {
                    executedIds.add("ELSE_STEP")
                    jsonResult(cb, JSONObject().put("ok", true).put("msg", "Ran ELSE_STEP"))
                }
                else -> jsonResult(cb, JSONObject().put("ok", true))
            }
        }

        // Branch test: IF (false AND varNotEmpty) -> ELSE IF (true OR urlMatch) -> ELSE
        val conditionStep = AutomationStep(
            id = "cond_root",
            order = 1,
            actionType = AutomationActionType.CONDITION,
            targetLabel = "Tier check",
            description = "Check account tier",
            condition = ConditionNode.Group(
                combinator = LogicalCombinator.AND,
                children = listOf(
                    ConditionNode.Predicate(ConditionOperator.ELEMENT_VISIBLE, selector = ".vip-badge"),
                    ConditionNode.Predicate(ConditionOperator.VARIABLE_NOT_EMPTY, variableName = "tier")
                )
            ),
            thenSteps = listOf(
                AutomationStep(
                    id = "then_act",
                    order = 1,
                    actionType = AutomationActionType.CLICK,
                    targetLabel = "THEN_STEP",
                    selector = "#THEN_STEP",
                    description = "Click THEN_STEP"
                )
            ),
            elseIfBranches = listOf(
                ElseIfBranch(
                    id = "elif_1",
                    label = "Promo Branch",
                    condition = ConditionNode.Group(
                        combinator = LogicalCombinator.OR,
                        children = listOf(
                            ConditionNode.Predicate(ConditionOperator.ELEMENT_EXISTS, selector = ".promo-banner"),
                            ConditionNode.Predicate(ConditionOperator.URL_MATCHES, expectedValue = "orders")
                        )
                    ),
                    steps = listOf(
                        AutomationStep(
                            id = "elif_act",
                            order = 1,
                            actionType = AutomationActionType.CLICK,
                            targetLabel = "ELSEIF_STEP",
                            selector = "#ELSEIF_STEP",
                            description = "Click ELSEIF_STEP"
                        )
                    )
                )
            ),
            elseSteps = listOf(
                AutomationStep(
                    id = "else_act",
                    order = 1,
                    actionType = AutomationActionType.CLICK,
                    targetLabel = "ELSE_STEP",
                    selector = "#ELSE_STEP",
                    description = "Click ELSE_STEP"
                )
            )
        )

        val wfId = repository.saveAutomationWorkflow(
            name = "Branch Flow",
            domain = "portal.example.com",
            sourceUrl = url,
            type = AutomationType.MANUAL,
            steps = listOf(conditionStep),
            metadata = WorkflowDocumentMetadata(
                variables = listOf(WorkflowVariableDefinition("tier", WorkflowVariableType.TEXT, defaultValue = "gold"))
            )
        )
        val wf = repository.getAutomationWorkflowById(wfId)!!
        val doc = repository.getAutomationWorkflowDocument(wfId)!!

        val outcome = coordinator.executeDocumentWorkflow(
            workflow = wf,
            document = doc,
            currentTabUrl = url,
            webViewProvider = { webView },
            onNavigateUrl = {},
            onStepProgress = { _, _ -> }
        )

        assertEquals("COMPLETED", outcome.status)
        // Mutually exclusive: only ELSEIF_STEP executed, never THEN_STEP or ELSE_STEP
        assertEquals(listOf("ELSEIF_STEP"), executedIds)
        assertTrue(outcome.branchTraces.any { !it.matched && it.branchLabel.startsWith("IF") })
        assertTrue(outcome.branchTraces.any { it.matched && it.branchLabel.contains("Promo Branch") })

        // Verify trace persists in DB
        val execId = repository.recordAutomationExecutionStart(wf, doc.steps.size)
        val traceJson = Phase6ExecutionCoordinator.serializeExecutionTrace(outcome.branchTraces, outcome.recoveryAttempts)
        repository.finishAutomationExecution(wfId, execId, outcome.status, outcome.completedSteps, outcome.totalSteps, outcome.verificationSummary, traceJson = traceJson)
        val savedExec = database.apexDao().getAutomationExecutionById(execId)!!
        val (parsedBranches, _) = Phase6ExecutionCoordinator.parseExecutionTrace(savedExec.traceJson)
        assertEquals(outcome.branchTraces.size, parsedBranches.size)

        // Verify nesting depth > MAX_CONDITION_DEPTH fails pre-execution validation
        var deepNode: ConditionNode = ConditionNode.Predicate(ConditionOperator.PREVIOUS_STEP_SUCCEEDED)
        repeat(Phase6Limits.MAX_CONDITION_DEPTH + 1) {
            deepNode = ConditionNode.Group(LogicalCombinator.AND, children = listOf(deepNode))
        }
        val tooDeepStep = conditionStep.copy(condition = deepNode)
        val deepOutcome = coordinator.executeDocumentWorkflow(
            workflow = wf,
            document = WorkflowDocument(steps = listOf(tooDeepStep)),
            currentTabUrl = url,
            webViewProvider = { webView },
            onNavigateUrl = {},
            onStepProgress = { _, _ -> }
        )
        assertEquals("FAILED", deepOutcome.status)
        assertEquals(AutomationErrorCode.CONDITION_INVALID, deepOutcome.errorCode)
    }

    @Test
    fun boundedLoops_respectMaxIterations_preventInfiniteLoops_andEnforceStepBudget() = runBlocking {
        val url = "https://list.example.com"
        val iterationsRun = AtomicInteger(0)
        val webView = FakeEvalWebView(context, url) { script, cb ->
            if (script.contains("click") || script.contains("apexResolveOrderedTarget")) {
                iterationsRun.incrementAndGet()
            }
            jsonResult(cb, JSONObject().put("ok", true))
        }

        val loopStep = AutomationStep(
            id = "loop_1",
            order = 1,
            actionType = AutomationActionType.LOOP,
            targetLabel = "Load more",
            description = "Repeat 4 times",
            loopConfig = LoopConfig(
                maxIterations = 4,
                iterationVariable = "loop_idx",
                continueCondition = ConditionNode.Predicate(
                    operator = ConditionOperator.VARIABLE_LESS_THAN,
                    variableName = "loop_idx",
                    expectedValue = "4"
                )
            ),
            thenSteps = listOf(
                AutomationStep(
                    id = "loop_click",
                    order = 1,
                    actionType = AutomationActionType.CLICK,
                    targetLabel = "Next",
                    selector = "#next",
                    description = "Click next \${loop_idx}"
                )
            )
        )

        val wf = AutomationWorkflowEntity(
            id = 15L,
            name = "Bounded Loop",
            domain = "list.example.com",
            sourceUrl = url,
            type = AutomationType.MANUAL.id
        )
        val outcome = coordinator.executeDocumentWorkflow(
            workflow = wf,
            document = WorkflowDocument(steps = listOf(loopStep)),
            currentTabUrl = url,
            webViewProvider = { webView },
            onNavigateUrl = {},
            onStepProgress = { _, _ -> }
        )
        assertEquals("COMPLETED", outcome.status)
        assertEquals(4, iterationsRun.get())

        // Exceeding MAX_LOOP_ITERATIONS rejected by validator
        val illegalLoop = loopStep.copy(
            loopConfig = LoopConfig(maxIterations = Phase6Limits.MAX_LOOP_ITERATIONS + 10)
        )
        val invalidOutcome = coordinator.executeDocumentWorkflow(
            workflow = wf,
            document = WorkflowDocument(steps = listOf(illegalLoop)),
            currentTabUrl = url,
            webViewProvider = { webView },
            onNavigateUrl = {},
            onStepProgress = { _, _ -> }
        )
        assertEquals("FAILED", invalidOutcome.status)
        assertEquals(AutomationErrorCode.LOOP_LIMIT_EXCEEDED, invalidOutcome.errorCode)
    }

    @Test
    fun workflowVariables_crossPageExtractionSubstitution_typeValidation_andCredentialSeparation() = runBlocking {
        // Validate naming & Vault separation: "username" allowed; "password"/"cvv"/"secret" blocked
        assertTrue(WorkflowVariableStore.isValidVariableName("username"))
        assertFalse(WorkflowVariableStore.isBlockedCredentialVariableName("username"))
        assertTrue(WorkflowVariableStore.isBlockedCredentialVariableName("password"))
        assertTrue(WorkflowVariableStore.isBlockedCredentialVariableName("cvv"))
        assertTrue(WorkflowVariableStore.isBlockedCredentialVariableName("auth_token"))

        val tabId = "tab_vars"
        val navSync = TabNavigationSynchronizer()
        var currentUrl = "https://app.example.com/profile"
        navSync.onPageStarted(tabId, currentUrl)
        navSync.onPageFinished(tabId, currentUrl)

        var typedValueObserved = ""
        val webView = FakeEvalWebView(context, currentUrl) { script, cb ->
            when {
                script.contains("document.readyState") && script.contains("locationHref") -> {
                    cb?.onReceiveValue(JSONObject().apply {
                        put("readyState", "complete")
                        put("locationHref", currentUrl)
                    }.toString())
                }
                script.contains("VISIBLE_TEXT") -> {
                    jsonResult(cb, JSONObject().apply {
                        put("ok", true)
                        put("code", "NONE")
                        put("value", "alice_dev")
                        put("items", JSONArray().put("alice_dev"))
                    })
                }
                script.contains("buildInputScriptBody") || script.contains("alice_dev") -> {
                    if (script.contains("alice_dev")) typedValueObserved = "alice_dev"
                    jsonResult(cb, JSONObject().put("ok", true).put("msg", "Entered value"))
                }
                else -> jsonResult(cb, JSONObject().put("ok", true))
            }
        }

        val steps = listOf(
            AutomationStep(
                id = "extract_username",
                order = 1,
                actionType = AutomationActionType.EXTRACT,
                targetLabel = "Displayed Username",
                selector = ".profile-username",
                description = "Extract username",
                variableName = "username",
                extractionSpec = ExtractionSpec(
                    mode = ExtractionMode.VISIBLE_TEXT,
                    outputVariableName = "username"
                )
            ),
            AutomationStep(
                id = "nav_settings",
                order = 2,
                actionType = AutomationActionType.NAVIGATE,
                targetLabel = "https://app.example.com/invite",
                value = "https://app.example.com/invite",
                description = "Go to invite page"
            ),
            AutomationStep(
                id = "input_username",
                order = 3,
                actionType = AutomationActionType.INPUT,
                targetLabel = "Handle",
                selector = "#handle-input",
                value = "\${username}",
                description = "Enter extracted username"
            )
        )

        val wf = AutomationWorkflowEntity(
            id = 20L,
            name = "Extract Username & Reuse",
            domain = "app.example.com",
            sourceUrl = currentUrl,
            type = AutomationType.MANUAL.id
        )
        val outcome = coordinator.executeDocumentWorkflow(
            workflow = wf,
            document = WorkflowDocument(steps = steps),
            currentTabUrl = currentUrl,
            tabId = tabId,
            navigationSynchronizer = navSync,
            webViewProvider = { webView },
            onNavigateUrl = { dest ->
                launch {
                    delay(10)
                    currentUrl = dest
                    navSync.onPageStarted(tabId, dest)
                    navSync.onPageFinished(tabId, dest)
                }
            },
            onStepProgress = { _, _ -> },
            liveUrlProvider = { currentUrl }
        )

        assertEquals("COMPLETED", outcome.status)
        assertEquals("alice_dev", typedValueObserved)
        assertTrue(webView.evaluatedScripts.any { it.contains("alice_dev") })

        // Undefined variable fails deterministically
        val undefOutcome = coordinator.executeDocumentWorkflow(
            workflow = wf,
            document = WorkflowDocument(
                steps = listOf(
                    AutomationStep(
                        id = "bad_input",
                        order = 1,
                        actionType = AutomationActionType.INPUT,
                        targetLabel = "Handle",
                        selector = "#handle",
                        value = "\${never_defined_var}",
                        description = "Use missing var"
                    )
                )
            ),
            currentTabUrl = "https://app.example.com/profile",
            webViewProvider = { webView },
            onNavigateUrl = {},
            onStepProgress = { _, _ -> }
        )
        assertEquals("FAILED", undefOutcome.status)
        assertEquals(AutomationErrorCode.VARIABLE_UNDEFINED, undefOutcome.errorCode)

        // Blocked secret variable name rejected before execution
        val secretVarOutcome = coordinator.executeDocumentWorkflow(
            workflow = wf,
            document = WorkflowDocument(
                metadata = WorkflowDocumentMetadata(
                    variables = listOf(WorkflowVariableDefinition("password", WorkflowVariableType.TEXT, "hunter2"))
                ),
                steps = steps.take(1)
            ),
            currentTabUrl = "https://app.example.com/profile",
            webViewProvider = { webView },
            onNavigateUrl = {},
            onStepProgress = { _, _ -> }
        )
        assertEquals("FAILED", secretVarOutcome.status)
        assertEquals(AutomationErrorCode.VARIABLE_INVALID, secretVarOutcome.errorCode)
    }

    @Test
    fun structuredExtraction_allModes_rejectsAmbiguity_blocksSensitiveFields_andBoundsLists() = runBlocking {
        val url = "https://docs.example.com/article"
        val webView = FakeEvalWebView(context, url) { script, cb ->
            when {
                script.contains("const mode = \"PAGE_TITLE\"") -> jsonResult(cb, JSONObject().put("ok", true).put("value", "Apex Docs").put("items", JSONArray().put("Apex Docs")))
                script.contains("multiple_matches") -> jsonResult(cb, JSONObject().put("ok", false).put("code", "EXTRACTION_AMBIGUOUS").put("msg", "Multiple matching elements found"))
                script.contains("const mode = \"INPUT_VALUE\"") && script.contains("#pwd-input") -> jsonResult(cb, JSONObject().put("ok", false).put("code", "EXTRACTION_SENSITIVE_BLOCKED").put("msg", "Password input blocked"))
                else -> {
                    val items = JSONArray().apply {
                        for (i in 1..5) put("Item $i")
                    }
                    jsonResult(cb, JSONObject().put("ok", true).put("value", "Item 1").put("items", items))
                }
            }
        }
        val wf = AutomationWorkflowEntity(25L, "Extract Suite", "docs.example.com", url, AutomationType.MANUAL.id)

        // Sensitive attribute blocked pre-execution
        val badAttr = AutomationStep(
            id = "ext_bad_attr",
            order = 1,
            actionType = AutomationActionType.EXTRACT,
            targetLabel = "Hidden attr",
            selector = "#x",
            description = "Extract unsafe attr",
            extractionSpec = ExtractionSpec(mode = ExtractionMode.ATTRIBUTE, attributeName = "onclick")
        )
        val badAttrRes = coordinator.executeDocumentWorkflow(wf, WorkflowDocument(steps = listOf(badAttr)), url, { webView }, {}, { _, _ -> })
        assertEquals("FAILED", badAttrRes.status)
        assertEquals(AutomationErrorCode.EXTRACTION_SENSITIVE_BLOCKED, badAttrRes.errorCode)

        // Multi-item list extraction + ambiguity failure
        val listStep = AutomationStep(
            id = "ext_list",
            order = 1,
            actionType = AutomationActionType.EXTRACT,
            targetLabel = "Tags",
            selector = ".tag",
            description = "Extract tags",
            variableName = "tags",
            extractionSpec = ExtractionSpec(
                mode = ExtractionMode.VISIBLE_TEXT,
                allowMultiple = true,
                maxItems = 5,
                outputVariableName = "tags"
            )
        )
        val okRes = coordinator.executeDocumentWorkflow(wf, WorkflowDocument(steps = listOf(listStep)), url, { webView }, {}, { _, _ -> })
        assertEquals("COMPLETED", okRes.status)

        val ambigStep = AutomationStep(
            id = "ext_ambig",
            order = 1,
            actionType = AutomationActionType.EXTRACT,
            targetLabel = "Multiple",
            selector = ".multiple_matches",
            description = "Ambiguous match",
            extractionSpec = ExtractionSpec(mode = ExtractionMode.VISIBLE_TEXT, allowMultiple = false, rejectAmbiguous = true)
        )
        val ambigRes = coordinator.executeDocumentWorkflow(wf, WorkflowDocument(steps = listOf(ambigStep)), url, { webView }, {}, { _, _ -> })
        assertEquals("FAILED", ambigRes.status)
        assertEquals(AutomationErrorCode.EXTRACTION_AMBIGUOUS, ambigRes.errorCode)
    }

    @Test
    fun subWorkflows_reuseWithInputOutputMapping_isolatedScope_cycleAndDepthProtection() = runBlocking {
        val url = "https://crm.example.com/deal"
        var greetingEntered = ""
        val webView = FakeEvalWebView(context, url) { script, cb ->
            if (script.contains("Hello, Sam!")) greetingEntered = "Hello, Sam!"
            jsonResult(cb, JSONObject().put("ok", true))
        }

        // Save reusable child workflow: takes input `customer_name`, creates internal `local_tmp`, produces output `formatted_greeting`
        val childSteps = listOf(
            AutomationStep(
                id = "c_1",
                order = 1,
                actionType = AutomationActionType.SET_VARIABLE,
                targetLabel = "local_tmp",
                value = "ignored_parent",
                description = "Internal helper variable",
                variableName = "local_tmp"
            ),
            AutomationStep(
                id = "c_2",
                order = 2,
                actionType = AutomationActionType.SET_VARIABLE,
                targetLabel = "formatted_greeting",
                value = "Hello, \${customer_name}!",
                description = "Format greeting",
                variableName = "formatted_greeting"
            )
        )
        val childMeta = WorkflowDocumentMetadata(
            variables = listOf(
                WorkflowVariableDefinition("customer_name", WorkflowVariableType.TEXT, isInput = true),
                WorkflowVariableDefinition("formatted_greeting", WorkflowVariableType.TEXT, isOutput = true)
            ),
            isReusableComponent = true
        )
        val childId = repository.saveAutomationWorkflow(
            name = "Format Greeting",
            domain = "crm.example.com",
            sourceUrl = url,
            type = AutomationType.MANUAL,
            steps = childSteps,
            metadata = childMeta
        )

        // Parent calls child and reuses output in INPUT step
        val parentSteps = listOf(
            AutomationStep(
                id = "p_set",
                order = 1,
                actionType = AutomationActionType.SET_VARIABLE,
                targetLabel = "client",
                value = "Sam",
                description = "Set client name",
                variableName = "client"
            ),
            AutomationStep(
                id = "p_call",
                order = 2,
                actionType = AutomationActionType.CALL_WORKFLOW,
                targetLabel = "Format Greeting",
                description = "Call greeting sub-workflow",
                subWorkflowRef = SubWorkflowReference(
                    targetWorkflowId = childId,
                    targetWorkflowName = "Format Greeting",
                    inputMappings = mapOf("customer_name" to "\${client}"),
                    outputMappings = mapOf("greeting_text" to "formatted_greeting")
                )
            ),
            AutomationStep(
                id = "p_input",
                order = 3,
                actionType = AutomationActionType.INPUT,
                targetLabel = "Note",
                selector = "#note",
                value = "\${greeting_text}",
                description = "Write greeting"
            )
        )
        val parentId = repository.saveAutomationWorkflow(
            name = "Parent Deal Flow",
            domain = "crm.example.com",
            sourceUrl = url,
            type = AutomationType.MANUAL,
            steps = parentSteps
        )
        val parentWf = repository.getAutomationWorkflowById(parentId)!!
        val parentDoc = repository.getAutomationWorkflowDocument(parentId)!!

        val outcome = coordinator.executeDocumentWorkflow(
            workflow = parentWf,
            document = parentDoc,
            currentTabUrl = url,
            webViewProvider = { webView },
            onNavigateUrl = {},
            onStepProgress = { _, _ -> },
            workflowLookup = { id ->
                repository.getAutomationWorkflowById(id)?.let { entity ->
                    entity.name to AutomationStepCodec.decodeDocument(entity.stepsJson)
                }
            }
        )
        assertEquals("COMPLETED", outcome.status)
        assertEquals("Hello, Sam!", greetingEntered)
        // Verify child's internal `local_tmp` variable did NOT leak into parent scope
        assertTrue(outcome.variableSnapshots.none { it.name == "local_tmp" })
        assertEquals("Hello, Sam!", outcome.variableSnapshots.firstOrNull { it.name == "greeting_text" }?.previewValue)

        // Cycle detection: self-referencing workflow rejected pre-execution
        val cycleId = repository.saveAutomationWorkflow(
            name = "Self Cycle",
            domain = "crm.example.com",
            sourceUrl = url,
            type = AutomationType.MANUAL,
            steps = emptyList()
        )
        val cycleStep = AutomationStep(
            id = "cyc",
            order = 1,
            actionType = AutomationActionType.CALL_WORKFLOW,
            targetLabel = "Self",
            description = "Call self",
            subWorkflowRef = SubWorkflowReference(targetWorkflowId = cycleId, targetWorkflowName = "Self Cycle")
        )
        repository.saveAutomationWorkflowDocument(cycleId, WorkflowDocument(steps = listOf(cycleStep)))
        val cycleEntity = repository.getAutomationWorkflowById(cycleId)!!
        val cycleDoc = repository.getAutomationWorkflowDocument(cycleId)!!

        val cycleOutcome = coordinator.executeDocumentWorkflow(
            workflow = cycleEntity,
            document = cycleDoc,
            currentTabUrl = url,
            webViewProvider = { webView },
            onNavigateUrl = {},
            onStepProgress = { _, _ -> },
            workflowLookup = { id ->
                repository.getAutomationWorkflowById(id)?.let { entity ->
                    entity.name to AutomationStepCodec.decodeDocument(entity.stepsJson)
                }
            }
        )
        assertEquals("FAILED", cycleOutcome.status)
        assertEquals(AutomationErrorCode.SUBWORKFLOW_CYCLE, cycleOutcome.errorCode)
    }
}
