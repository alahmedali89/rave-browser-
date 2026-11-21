package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.automation.AutomationActionType
import com.example.automation.AutomationErrorCode
import com.example.automation.AutomationStep
import com.example.automation.ConditionNode
import com.example.automation.ConditionOperator
import com.example.automation.CredentialFieldRole
import com.example.automation.ElseIfBranch
import com.example.automation.ExecutionLifecycleController
import com.example.automation.ExtractionMode
import com.example.automation.ExtractionSpec
import com.example.automation.LogicalCombinator
import com.example.automation.LoopConfig
import com.example.automation.PersistedExecutionCheckpoint
import com.example.automation.Phase6ExecutionCoordinator
import com.example.automation.RecoveryPolicy
import com.example.automation.RecoveryStrategy
import com.example.automation.StepExecutionResult
import com.example.automation.WaitConditionSpec
import com.example.automation.WaitConditionType
import com.example.automation.WorkflowDocument
import com.example.automation.WorkflowDocumentMetadata
import com.example.automation.WorkflowVariableDefinition
import com.example.automation.WorkflowVariableType
import com.example.data.local.AutomationWorkflowEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 7 real Chromium WebView automation execution tests:
 * Verifies all 14 core actions plus Phase 6 branching, bounded loops, variables,
 * sub-workflow calls, structured extraction, recovery, multi-page continuity, and checkpoint resume
 * against real DOM state inside Chromium.
 */
@RunWith(AndroidJUnit4::class)
class RealWebViewAutomationExecutionInstrumentedTest {

    private fun workflowEntity(id: Long = 1L, name: String, url: String): AutomationWorkflowEntity =
        AutomationWorkflowEntity(
            id = id,
            name = name,
            domain = "127.0.0.1",
            sourceUrl = url,
            type = "MANUAL"
        )

    @Test
    fun realWebView_executesAll14CoreActionsAndVerifiesRealDomState() {
        LocalWebFixtureServer().use { server ->
            server.registerHtml(
                "/form-page",
                """
                <!DOCTYPE html>
                <html>
                <head><meta charset="utf-8"><title>Real Form</title></head>
                <body>
                  <h1 id="heading">Checkout Form</h1>
                  <form id="main-form" action="/submitted" method="GET" onsubmit="window.__submitted = true; return true;">
                    <label for="name-input">Full Name</label>
                    <input id="name-input" type="text" value="Old Name" />

                    <label for="email-input">Login Email</label>
                    <input id="email-input" type="email" />

                    <label for="pass-input">Login Password</label>
                    <input id="pass-input" type="password" />

                    <label for="tier-select">Plan Tier</label>
                    <select id="tier-select">
                      <option value="free">Free</option>
                      <option value="pro">Pro Plan</option>
                      <option value="team">Team Plan</option>
                    </select>

                    <label for="agree-check">Agree Policy</label>
                    <input id="agree-check" type="checkbox" />

                    <label for="search-key-box">Shortcut Box</label>
                    <input id="search-key-box" type="text" />

                    <button id="reveal-btn" type="button" onclick="setTimeout(function(){ var d=document.createElement('div'); d.id='delayed-banner'; d.textContent='Loaded Async Banner'; document.body.appendChild(d); }, 120);">Reveal Async</button>
                    <div id="events-log"></div>
                    <div style="height:1400px">Tall Spacer</div>
                    <button id="submit-btn" type="submit">Submit Order</button>
                  </form>
                  <script>
                    window.__focusCount = 0;
                    window.__blurCount = 0;
                    window.__enterPressed = false;
                    document.getElementById('name-input').addEventListener('focus', () => { window.__focusCount++; });
                    document.getElementById('name-input').addEventListener('blur', () => { window.__blurCount++; });
                    document.getElementById('search-key-box').addEventListener('keydown', (e) => {
                      if (e.key === 'Enter') window.__enterPressed = true;
                    });
                  </script>
                </body>
                </html>
                """.trimIndent()
            )
            server.registerHtml(
                "/submitted",
                "<!DOCTYPE html><html><head><title>Submitted</title></head><body><h2 id='done-msg'>Order Confirmed</h2></body></html>"
            )
            server.registerHtml(
                "/next-dest",
                "<!DOCTYPE html><html><head><title>Next Destination</title></head><body><p id='dest-tag'>Arrived</p></body></html>"
            )

            WebViewTestHost(tabId = "tab_actions").use { host ->
                val startUrl = server.urlFor("/form-page")
                host.loadAndAwait(startUrl)

                var credCalls = 0
                val steps = listOf(
                    AutomationStep(id = "s1", order = 1, actionType = AutomationActionType.CLEAR, targetLabel = "Full Name", selector = "#name-input", description = "Clear Full Name"),
                    AutomationStep(id = "s2", order = 2, actionType = AutomationActionType.INPUT, targetLabel = "Full Name", selector = "#name-input", value = "Ada Lovelace", description = "Type Full Name"),
                    AutomationStep(id = "s3", order = 3, actionType = AutomationActionType.FOCUS, targetLabel = "Full Name", selector = "#name-input", description = "Focus Full Name"),
                    AutomationStep(id = "s4", order = 4, actionType = AutomationActionType.BLUR, targetLabel = "Full Name", selector = "#name-input", description = "Blur Full Name"),
                    AutomationStep(id = "s5", order = 5, actionType = AutomationActionType.SELECT, targetLabel = "Plan Tier", selector = "#tier-select", value = "Pro Plan", description = "Select Pro Plan"),
                    AutomationStep(id = "s6", order = 6, actionType = AutomationActionType.TOGGLE, targetLabel = "Agree Policy", selector = "#agree-check", desiredCheckedState = true, description = "Check Agree Policy"),
                    AutomationStep(id = "s7", order = 7, actionType = AutomationActionType.KEY_PRESS, targetLabel = "Shortcut Box", selector = "#search-key-box", keyboardKey = "Enter", description = "Press Enter"),
                    AutomationStep(id = "s8", order = 8, actionType = AutomationActionType.CLICK, targetLabel = "Reveal Async", selector = "#reveal-btn", description = "Click Reveal Async"),
                    AutomationStep(
                        id = "s9",
                        order = 9,
                        actionType = AutomationActionType.WAIT,
                        targetLabel = "Delayed Banner",
                        selector = "#delayed-banner",
                        waitCondition = WaitConditionSpec(
                            type = WaitConditionType.ELEMENT_APPEARS,
                            selector = "#delayed-banner",
                            timeoutMs = 3_000L
                        ),
                        description = "Wait for Delayed Banner"
                    ),
                    AutomationStep(
                        id = "s10",
                        order = 10,
                        actionType = AutomationActionType.EXTRACT,
                        targetLabel = "Delayed Banner",
                        selector = "#delayed-banner",
                        extractionSpec = ExtractionSpec(mode = ExtractionMode.VISIBLE_TEXT, outputVariableName = "banner_text"),
                        description = "Extract Delayed Banner"
                    ),
                    AutomationStep(id = "s11", order = 11, actionType = AutomationActionType.SCROLL, targetLabel = "down", value = "500", description = "Scroll down"),
                    AutomationStep(
                        id = "s12",
                        order = 12,
                        actionType = AutomationActionType.FILL_CREDENTIAL,
                        targetLabel = "Login Password",
                        selector = "#pass-input",
                        credentialReferenceId = "cred_test_1",
                        credentialFieldRole = CredentialFieldRole.PASSWORD,
                        expectedOrigin = server.origin,
                        description = "Fill Vault Password"
                    ),
                    AutomationStep(id = "s13", order = 13, actionType = AutomationActionType.SUBMIT, targetLabel = "Submit Order", selector = "#submit-btn", description = "Submit Order"),
                    AutomationStep(
                        id = "s14",
                        order = 14,
                        actionType = AutomationActionType.NAVIGATE,
                        targetLabel = "Next Dest",
                        value = server.urlFor("/next-dest"),
                        allowCrossOriginTransition = false,
                        description = "Navigate to next page"
                    )
                )

                val outcome = runBlocking {
                    host.automationEngine.executeWorkflow(
                        workflow = workflowEntity(name = "All 14 Actions", url = startUrl),
                        steps = steps,
                        currentTabUrl = startUrl,
                        tabId = host.tabId,
                        navigationSynchronizer = host.navigationSynchronizer,
                        webViewProvider = { host.webView },
                        onNavigateUrl = { dest ->
                            host.runOnMainSync {
                                host.navigationSynchronizer.beginNavigation(host.tabId, dest)
                                host.webView.loadUrl(dest)
                            }
                        },
                        onStepProgress = { _, _ -> },
                        credentialStepExecutor = { step, _ ->
                            credCalls++
                            host.evalJs("document.querySelector('#pass-input').value = 'V@ultSecret99';")
                            StepExecutionResult(success = true, message = "Filled password step ${step.id}")
                        },
                        liveUrlProvider = { host.currentUrl() }
                    )
                }

                assertEquals("COMPLETED", outcome.status)
                assertEquals(14, outcome.completedSteps)
                assertEquals(1, credCalls)
                assertTrue(outcome.variableSnapshots.any { it.name == "banner_text" && it.previewValue.contains("Loaded Async Banner") })
                assertEquals("Arrived", host.evalJsUnquoted("document.getElementById('dest-tag').textContent"))
            }
        }
    }

    @Test
    fun realWebView_executesPhase6BranchesLoopsVariablesSubWorkflowsExtractionAndRecovery() {
        LocalWebFixtureServer().use { server ->
            server.registerHtml(
                "/phase6",
                """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="utf-8">
                  <meta name="description" content="Apex Phase 6 Fixture">
                  <title>Phase 6 Workflow Lab</title>
                </head>
                <body>
                  <span id="status-flag" data-testid="tier-badge">VIP</span>
                  <input id="target-field" type="text" />
                  <ul id="items-list">
                    <li class="item-row">Alpha</li>
                    <li class="item-row">Beta</li>
                    <li class="item-row">Gamma</li>
                  </ul>
                  <button id="inc-btn" type="button" onclick="var c=parseInt(document.getElementById('counter').textContent,10)+1; document.getElementById('counter').textContent=String(c);">Increment</button>
                  <span id="counter">0</span>
                  <div id="sub-host"></div>
                  <button id="spawn-late" type="button" onclick="setTimeout(function(){ var b=document.createElement('button'); b.id='recovered-target'; b.textContent='Recovered'; document.body.appendChild(b); }, 180);">Spawn Late</button>
                </body>
                </html>
                """.trimIndent()
            )

            WebViewTestHost(tabId = "tab_phase6").use { host ->
                val url = server.urlFor("/phase6")
                host.loadAndAwait(url)

                val subWorkflowId = 99L
                val subDoc = WorkflowDocument(
                    metadata = WorkflowDocumentMetadata(
                        variables = listOf(WorkflowVariableDefinition(name = "sub_input", type = WorkflowVariableType.TEXT, isInput = true)),
                        isReusableComponent = true
                    ),
                    steps = listOf(
                        AutomationStep(
                            id = "sub_set",
                            order = 1,
                            actionType = AutomationActionType.INPUT,
                            targetLabel = "Target Field",
                            selector = "#target-field",
                            value = "\${sub_input}",
                            description = "Fill field from sub-workflow input"
                        )
                    )
                )

                val rootSteps = listOf(
                    AutomationStep(
                        id = "v1",
                        order = 1,
                        actionType = AutomationActionType.SET_VARIABLE,
                        targetLabel = "greeting",
                        variableName = "greeting",
                        value = "Hello-VIP",
                        description = "Set greeting variable"
                    ),
                    AutomationStep(
                        id = "cond_outer",
                        order = 2,
                        actionType = AutomationActionType.CONDITION,
                        targetLabel = "Check VIP flag",
                        condition = ConditionNode.Group(
                            combinator = LogicalCombinator.AND,
                            children = listOf(
                                ConditionNode.Predicate(
                                    operator = ConditionOperator.ELEMENT_CONTAINS_TEXT,
                                    selector = "#status-flag",
                                    expectedValue = "VIP"
                                ),
                                ConditionNode.Predicate(
                                    operator = ConditionOperator.VARIABLE_EQUALS,
                                    variableName = "greeting",
                                    expectedValue = "Hello-VIP"
                                )
                            )
                        ),
                        thenSteps = listOf(
                            AutomationStep(
                                id = "cond_inner",
                                order = 1,
                                actionType = AutomationActionType.CONDITION,
                                targetLabel = "Nested branch",
                                condition = ConditionNode.Predicate(
                                    operator = ConditionOperator.ELEMENT_EXISTS,
                                    selector = "#inc-btn"
                                ),
                                thenSteps = listOf(
                                    AutomationStep(
                                        id = "loop_clicks",
                                        order = 1,
                                        actionType = AutomationActionType.LOOP,
                                        targetLabel = "Loop 3 clicks",
                                        loopConfig = LoopConfig(maxIterations = 3, iterationVariable = "idx"),
                                        thenSteps = listOf(
                                            AutomationStep(
                                                id = "click_inc",
                                                order = 1,
                                                actionType = AutomationActionType.CLICK,
                                                targetLabel = "Increment",
                                                selector = "#inc-btn",
                                                description = "Click Increment"
                                            )
                                        ),
                                        description = "Bounded 3x loop"
                                    )
                                ),
                                description = "Nested branch"
                            )
                        ),
                        elseIfBranches = listOf(
                            ElseIfBranch(
                                id = "ei_1",
                                label = "Fallback ElseIf",
                                condition = ConditionNode.Predicate(operator = ConditionOperator.ELEMENT_EXISTS, selector = "#nonexistent"),
                                steps = emptyList()
                            )
                        ),
                        description = "Outer VIP condition"
                    ),
                    AutomationStep(
                        id = "call_sub",
                        order = 3,
                        actionType = AutomationActionType.CALL_WORKFLOW,
                        targetLabel = "Sub Fill",
                        subWorkflowRef = com.example.automation.SubWorkflowReference(
                            targetWorkflowId = subWorkflowId,
                            targetWorkflowName = "Fill Input Subflow",
                            inputMappings = mapOf("sub_input" to "\${greeting}")
                        ),
                        description = "Call sub-workflow"
                    ),
                    AutomationStep(
                        id = "extract_multi",
                        order = 4,
                        actionType = AutomationActionType.EXTRACT,
                        targetLabel = "Items",
                        selector = ".item-row",
                        extractionSpec = ExtractionSpec(
                            mode = ExtractionMode.VISIBLE_TEXT,
                            allowMultiple = true,
                            maxItems = 10,
                            rejectAmbiguous = false,
                            outputVariableName = "extracted_items"
                        ),
                        description = "Structured extract list"
                    ),
                    AutomationStep(
                        id = "spawn",
                        order = 5,
                        actionType = AutomationActionType.CLICK,
                        targetLabel = "Spawn Late",
                        selector = "#spawn-late",
                        description = "Trigger late element"
                    ),
                    AutomationStep(
                        id = "recovered_click",
                        order = 6,
                        actionType = AutomationActionType.CLICK,
                        targetLabel = "Recovered",
                        selector = "#recovered-target",
                        recoveryPolicy = RecoveryPolicy(
                            strategy = RecoveryStrategy.WAIT_THEN_RETRY,
                            maxRetries = 2,
                            rescanBeforeRetry = true,
                            waitBeforeRetry = WaitConditionSpec(
                                type = WaitConditionType.ELEMENT_APPEARS,
                                selector = "#recovered-target",
                                timeoutMs = 2_500L
                            )
                        ),
                        description = "Click late element with Wait+Rescan recovery"
                    )
                )

                val coordinator = Phase6ExecutionCoordinator(host.automationEngine)
                val outcome = runBlocking {
                    coordinator.executeDocumentWorkflow(
                        workflow = workflowEntity(id = 10L, name = "Phase 6 Full", url = url),
                        document = WorkflowDocument(
                            metadata = WorkflowDocumentMetadata(
                                variables = listOf(WorkflowVariableDefinition("greeting", WorkflowVariableType.TEXT))
                            ),
                            steps = rootSteps
                        ),
                        currentTabUrl = url,
                        tabId = host.tabId,
                        navigationSynchronizer = host.navigationSynchronizer,
                        webViewProvider = { host.webView },
                        onNavigateUrl = { dest -> host.runOnMainSync { host.webView.loadUrl(dest) } },
                        onStepProgress = { _, _ -> },
                        workflowLookup = { requestedId ->
                            if (requestedId == subWorkflowId) "Fill Input Subflow" to subDoc else null
                        },
                        liveUrlProvider = { host.currentUrl() }
                    )
                }

                assertEquals("COMPLETED", outcome.status)
                assertEquals("3", host.evalJsUnquoted("document.getElementById('counter').textContent"))
                assertEquals("Hello-VIP", host.evalJsUnquoted("document.getElementById('target-field').value"))
                assertTrue(outcome.branchTraces.isNotEmpty())
                assertTrue(outcome.recoveredStepsCount >= 1)
                assertTrue(outcome.variableSnapshots.any { it.name == "extracted_items" && it.previewValue.contains("Alpha") })
            }
        }
    }

    @Test
    fun realWebView_unsafeDestructiveRetryBlockedAndCheckpointRecoveryResumesSafely() {
        LocalWebFixtureServer().use { server ->
            server.registerHtml(
                "/multipage-1",
                """
                <!DOCTYPE html>
                <html><body>
                  <input id="step1-box" type="text" />
                  <a id="next-link" href="/multipage-2">Go Page 2</a>
                </body></html>
                """.trimIndent()
            )
            server.registerHtml(
                "/multipage-2",
                """
                <!DOCTYPE html>
                <html><body>
                  <input id="step2-box" type="text" />
                  <button id="missing-delete" type="button">Delete Account</button>
                </body></html>
                """.trimIndent()
            )

            WebViewTestHost(tabId = "tab_checkpoint").use { host ->
                val url1 = server.urlFor("/multipage-1")
                val url2 = server.urlFor("/multipage-2")
                host.loadAndAwait(url1)

                val step1 = AutomationStep(
                    id = "mp_1",
                    order = 1,
                    actionType = AutomationActionType.INPUT,
                    targetLabel = "Step 1 Box",
                    selector = "#step1-box",
                    value = "alpha",
                    description = "Fill Page 1"
                )
                val step2 = AutomationStep(
                    id = "mp_2",
                    order = 2,
                    actionType = AutomationActionType.NAVIGATE,
                    targetLabel = "Go Page 2",
                    value = url2,
                    description = "Go to Page 2"
                )
                val step3 = AutomationStep(
                    id = "mp_3",
                    order = 3,
                    actionType = AutomationActionType.INPUT,
                    targetLabel = "Step 2 Box",
                    selector = "#step2-box",
                    value = "resumed-beta",
                    description = "Fill Page 2"
                )

                val controller = ExecutionLifecycleController()
                val sessionId = controller.tryBeginSession(host.tabId, host.webView, server.origin)!!
                // Simulate interruption after step 2 navigated to page 2
                host.loadAndAwait(url2)

                val checkpoint = PersistedExecutionCheckpoint(
                    executionId = 42L,
                    workflowId = 7L,
                    workflowName = "Multi-page Resume",
                    tabId = host.tabId,
                    boundOrigin = server.origin,
                    lastUrl = url2,
                    nextTopLevelIndex = 2,
                    totalTopLevelSteps = 3,
                    completedStepsCount = 2,
                    completedStepIds = listOf("mp_1", "mp_2"),
                    navigationEpoch = host.navigationSynchronizer.snapshot(host.tabId).epoch,
                    interruptedReason = "App backgrounded mid-run",
                    savedAtMs = System.currentTimeMillis()
                )

                val resumedOutcome = runBlocking {
                    try {
                        Phase6ExecutionCoordinator(host.automationEngine).executeDocumentWorkflow(
                            workflow = workflowEntity(id = 7L, name = "Multi-page Resume", url = url1),
                            document = WorkflowDocument(
                                metadata = WorkflowDocumentMetadata(allowMultiPageContinuity = true),
                                steps = listOf(step1, step2, step3)
                            ),
                            currentTabUrl = url2,
                            tabId = host.tabId,
                            navigationSynchronizer = host.navigationSynchronizer,
                            webViewProvider = { host.webView },
                            onNavigateUrl = { dest -> host.runOnMainSync { host.webView.loadUrl(dest) } },
                            onStepProgress = { _, _ -> },
                            lifecycleController = controller,
                            initialCheckpoint = checkpoint,
                            liveUrlProvider = { host.currentUrl() }
                        )
                    } finally {
                        controller.endSession(sessionId)
                    }
                }

                assertEquals("COMPLETED", resumedOutcome.status)
                assertEquals(3, resumedOutcome.completedSteps)
                assertEquals("resumed-beta", host.evalJsUnquoted("document.getElementById('step2-box').value"))

                // Also verify destructive step retry is NOT blindly retried when confirm is not granted
                val unsafeStep = AutomationStep(
                    id = "unsafe_submit",
                    order = 1,
                    actionType = AutomationActionType.SUBMIT,
                    targetLabel = "Nonexistent Delete",
                    selector = "#does-not-exist",
                    isDestructive = true,
                    recoveryPolicy = RecoveryPolicy(
                        strategy = RecoveryStrategy.RESCAN_AND_RETRY,
                        maxRetries = 2,
                        requireConfirmForUnsafeRetry = true
                    ),
                    description = "Unsafe delete button"
                )
                val unsafeOutcome = runBlocking {
                    Phase6ExecutionCoordinator(host.automationEngine).executeDocumentWorkflow(
                        workflow = workflowEntity(id = 8L, name = "Unsafe Guard", url = url2),
                        document = WorkflowDocument(steps = listOf(unsafeStep)),
                        currentTabUrl = url2,
                        tabId = host.tabId,
                        navigationSynchronizer = host.navigationSynchronizer,
                        webViewProvider = { host.webView },
                        onNavigateUrl = {},
                        onStepProgress = { _, _ -> },
                        confirmedUnsafeRetry = false,
                        liveUrlProvider = { host.currentUrl() }
                    )
                }
                assertEquals("FAILED", unsafeOutcome.status)
                assertTrue(unsafeOutcome.recoveryAttempts.none { it.succeeded })
                assertFalse(unsafeOutcome.errorCode == AutomationErrorCode.NONE)
            }
        }
    }
}
