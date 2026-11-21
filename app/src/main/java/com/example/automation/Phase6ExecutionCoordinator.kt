package com.example.automation

import android.net.Uri
import android.webkit.WebView
import com.example.data.local.AutomationWorkflowEntity
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject

data class Phase6LiveProgress(
    val activeBranchLabel: String = "",
    val activeSubWorkflowName: String = "",
    val variableSnapshots: List<VariableDisplayBadge> = emptyList(),
    val branchTraces: List<BranchTraceRecord> = emptyList(),
    val recoveryAttempts: List<RecoveryAttemptRecord> = emptyList(),
    val latestCheckpoint: PersistedExecutionCheckpoint? = null
)

class Phase6ExecutionCoordinator(
    private val engine: AutomationEngine
) {
    private data class ExecutionContextState(
        var activeUrl: String,
        var expectedOrigin: String,
        var totalExecutions: Int = 0,
        var previousStepSucceeded: Boolean = true,
        var recoveredCount: Int = 0,
        var activeBranchLabel: String = "",
        var activeSubWorkflowName: String = "",
        val stepRecords: MutableList<StepExecutionRecord> = mutableListOf(),
        val completedStepIds: MutableList<String> = mutableListOf(),
        val branchTraces: MutableList<BranchTraceRecord> = mutableListOf(),
        val recoveryAttempts: MutableList<RecoveryAttemptRecord> = mutableListOf(),
        val notes: MutableList<String> = mutableListOf(),
        var lastCheckpoint: PersistedExecutionCheckpoint? = null
    )

    suspend fun executeDocumentWorkflow(
        workflow: AutomationWorkflowEntity,
        document: WorkflowDocument,
        currentTabUrl: String,
        webViewProvider: () -> WebView?,
        onNavigateUrl: (String) -> Unit,
        onStepProgress: (stepIndex: Int, logMessage: String) -> Unit,
        tabId: String = "",
        navigationSynchronizer: TabNavigationSynchronizer? = null,
        onStepCompleted: (Int) -> Unit = {},
        credentialStepExecutor: (suspend (AutomationStep, String) -> StepExecutionResult)? = null,
        lifecycleController: ExecutionLifecycleController? = null,
        isTabOpenProvider: () -> Boolean = { true },
        liveUrlProvider: (() -> String)? = null,
        onStepDetailedProgress: ((stepIndex: Int, step: AutomationStep, recoveryNotice: String, stepRecords: List<StepExecutionRecord>) -> Unit)? = null,
        initialCheckpoint: PersistedExecutionCheckpoint? = null,
        workflowLookup: (suspend (Long) -> Pair<String, WorkflowDocument>?)? = null,
        confirmedUnsafeRetry: Boolean = false,
        executionId: Long = 0L,
        onPhase6Progress: ((Phase6LiveProgress) -> Unit)? = null,
        vaultValueResolver: (suspend (AutomationStep) -> Result<String>)? = null,
        clearBrowserDataExecutor: (suspend () -> StepExecutionResult)? = null
    ): WorkflowExecutionOutcome {
        val workflowStartMs = System.currentTimeMillis()
        val orderedSteps = AutomationStepCodec.reindex(document.steps.sortedBy { it.order })
        if (orderedSteps.isEmpty()) {
            return WorkflowExecutionOutcome(
                status = "FAILED",
                completedSteps = 0,
                totalSteps = 0,
                verificationSummary = "Workflow has no executable steps.",
                errorCode = AutomationErrorCode.EMPTY_WORKFLOW,
                durationMs = 0L
            )
        }

        val rootVarStore = WorkflowVariableStore(
            initialDefinitions = document.metadata.variables,
            initialValues = initialCheckpoint?.safeVariables.orEmpty()
        )

        // Pre-execution deterministic validation of AST, bounds, variables, sub-workflows, allowlists
        if (workflowLookup != null || usesPhase6Constructs(orderedSteps, document.metadata)) {
            val lookupFn: suspend (Long) -> Pair<String, WorkflowDocument>? = workflowLookup ?: { null }
            val validation = WorkflowValidator.validateWorkflow(
                rootWorkflowId = workflow.id,
                document = document.copy(steps = orderedSteps),
                workflowLookup = lookupFn,
                preSeededVariableNames = initialCheckpoint?.safeVariables?.keys.orEmpty(),
                engine = engine
            )
            if (!validation.isValid) {
                return WorkflowExecutionOutcome(
                    status = "FAILED",
                    completedSteps = initialCheckpoint?.completedStepsCount ?: 0,
                    totalSteps = orderedSteps.size,
                    verificationSummary = validation.message,
                    errorCode = validation.errorCode,
                    durationMs = (System.currentTimeMillis() - workflowStartMs).coerceAtLeast(0L)
                )
            }
        }

        val state = ExecutionContextState(
            activeUrl = currentTabUrl,
            expectedOrigin = engine.parseOrigin(currentTabUrl).canonicalOrigin
        )
        initialCheckpoint?.let { cp ->
            state.completedStepIds.addAll(cp.completedStepIds)
            state.branchTraces.addAll(cp.branchTraces)
            state.recoveryAttempts.addAll(cp.recoveryAttempts)
        }

        val allWorkflowSteps = orderedSteps.flatMap { it.collectNestedSteps() }
        val requiresCredReauth = allWorkflowSteps.any { it.isCredentialStep }

        fun buildCheckpointSnapshot(nextIndex: Int, completed: Int, failedSummary: String = ""): PersistedExecutionCheckpoint {
            val snap = if (tabId.isNotEmpty() && navigationSynchronizer != null) navigationSynchronizer.snapshot(tabId) else null
            return PersistedExecutionCheckpoint(
                executionId = executionId,
                workflowId = workflow.id,
                workflowName = workflow.name,
                tabId = tabId,
                boundOrigin = state.expectedOrigin,
                lastUrl = state.activeUrl,
                navigationEpoch = snap?.epoch ?: 0L,
                urlCommitSeq = snap?.urlCommitSeq ?: 0L,
                nextTopLevelIndex = nextIndex.coerceIn(0, orderedSteps.size),
                totalTopLevelSteps = orderedSteps.size,
                completedStepsCount = completed,
                safeVariables = rootVarStore.snapshotValues(),
                completedStepIds = state.completedStepIds.toList(),
                failedStepSummary = failedSummary,
                branchTraces = state.branchTraces.toList(),
                recoveryAttempts = state.recoveryAttempts.toList(),
                requiresCredentialReauth = requiresCredReauth
            ).also { state.lastCheckpoint = it }
        }

        fun emitPhase6Telemetry(varStore: WorkflowVariableStore, nextIndex: Int, completed: Int) {
            val cp = buildCheckpointSnapshot(nextIndex, completed)
            onPhase6Progress?.invoke(
                Phase6LiveProgress(
                    activeBranchLabel = state.activeBranchLabel,
                    activeSubWorkflowName = state.activeSubWorkflowName,
                    variableSnapshots = varStore.snapshotDisplayBadges(),
                    branchTraces = state.branchTraces.toList(),
                    recoveryAttempts = state.recoveryAttempts.toList(),
                    latestCheckpoint = cp
                )
            )
        }

        fun buildOutcome(
            status: String,
            completed: Int,
            summary: String,
            errorCode: AutomationErrorCode,
            varStore: WorkflowVariableStore,
            nextIndex: Int,
            ambiguousCandidates: List<AmbiguousCandidateInfo> = emptyList(),
            lastDiagnostics: LocatorResolutionDiagnostics? = null
        ): WorkflowExecutionOutcome {
            val cp = buildCheckpointSnapshot(nextIndex, completed, if (status == "FAILED") summary else "")
            val capturedBadges = varStore.snapshotDisplayBadges()
            if (status != "INTERRUPTED") {
                varStore.clear()
            }
            val resolvedDiagnostics = lastDiagnostics ?: state.stepRecords.lastOrNull()?.locatorDiagnostics
            val resolvedAmbiguous = if (ambiguousCandidates.isNotEmpty()) {
                ambiguousCandidates
            } else {
                state.stepRecords.lastOrNull()?.ambiguousCandidates.orEmpty()
            }
            return WorkflowExecutionOutcome(
                status = status,
                completedSteps = completed,
                totalSteps = orderedSteps.size,
                verificationSummary = summary,
                errorCode = errorCode,
                durationMs = (System.currentTimeMillis() - workflowStartMs).coerceAtLeast(0L),
                stepRecords = state.stepRecords.toList(),
                recoveredStepsCount = state.recoveredCount,
                branchTraces = state.branchTraces.toList(),
                recoveryAttempts = state.recoveryAttempts.toList(),
                variableSnapshots = capturedBadges,
                lastCheckpoint = cp,
                ambiguousCandidates = resolvedAmbiguous,
                lastLocatorDiagnostics = resolvedDiagnostics
            )
        }

        suspend fun checkRunnableContext(allowCrossOrigin: Boolean): ExecutionContextAbortReason? {
            coroutineContext.ensureActive()
            if (!isTabOpenProvider()) {
                return ExecutionContextAbortReason(
                    code = AutomationErrorCode.TAB_CLOSED,
                    message = "Target browser tab was closed; workflow halted safely."
                )
            }
            if (lifecycleController == null) return null
            lifecycleController.awaitRunnableOrAbort()?.let { return it }
            val currentLiveUrl = liveUrlProvider?.invoke()?.takeIf { it.isNotBlank() } ?: state.activeUrl
            return lifecycleController.validateBoundContext(
                currentWebView = webViewProvider(),
                isTabStillOpen = isTabOpenProvider(),
                liveUrl = currentLiveUrl,
                expectedOrigin = state.expectedOrigin,
                allowCrossOrigin = allowCrossOrigin,
                originParser = { engine.parseOrigin(it) }
            )
        }

        val startIndex = (initialCheckpoint?.nextTopLevelIndex ?: 0).coerceIn(0, orderedSteps.size)
        var topLevelCompleted = initialCheckpoint?.completedStepsCount ?: 0

        val redirectTargetUrl = resolveWorkflowStartUrl(workflow, orderedSteps)
        val firstStepIsNavigate = orderedSteps.firstOrNull()?.actionType == AutomationActionType.NAVIGATE
        // Redirect active tab to the automation's target website before running steps if not already on it
        if (startIndex == 0 &&
            !firstStepIsNavigate &&
            redirectTargetUrl.isNotBlank() &&
            !urlsMatchForWorkflowStart(state.activeUrl, redirectTargetUrl)
        ) {
            coroutineContext.ensureActive()
            if (!isTabOpenProvider()) {
                return buildOutcome(
                    "FAILED",
                    topLevelCompleted,
                    "Target browser tab was closed; workflow halted safely.",
                    AutomationErrorCode.TAB_CLOSED,
                    rootVarStore,
                    0
                )
            }
            lifecycleController?.awaitRunnableOrAbort()?.let { abort ->
                val status = if (lifecycleController.isInterruptionAbort) "INTERRUPTED"
                else if (abort.code == AutomationErrorCode.CANCELLED) "CANCELLED"
                else "FAILED"
                return buildOutcome(status, topLevelCompleted, abort.message, abort.code, rootVarStore, 0)
            }
            val displayTarget = workflow.domain.ifBlank { redirectTargetUrl }
            onStepProgress(0, "Opening $displayTarget & waiting for website to load…")
            val firstRes = navigateWithContinuation(
                dest = redirectTargetUrl,
                tabId = tabId,
                navigationSynchronizer = navigationSynchronizer,
                webViewProvider = webViewProvider,
                nextStep = orderedSteps.firstOrNull(),
                onNavigateUrl = onNavigateUrl
            )
            if (!firstRes.success) {
                return buildOutcome("FAILED", 0, firstRes.message, firstRes.errorCode, rootVarStore, 0)
            }
            state.activeUrl = navigationSynchronizer?.snapshot(tabId)?.activeUrl?.ifBlank { redirectTargetUrl }
                ?: redirectTargetUrl
            state.expectedOrigin = engine.parseOrigin(state.activeUrl).canonicalOrigin
            lifecycleController?.updateBoundWebViewIfUnset(webViewProvider())
            lifecycleController?.updateAllowedOrigin(state.expectedOrigin)
            rescanAfterPageTransition(webViewProvider(), tabId, navigationSynchronizer, state.activeUrl)
            onStepProgress(0, "Website loaded — starting automation")
        } else if (startIndex == 0 && !firstStepIsNavigate && orderedSteps.isNotEmpty()) {
            coroutineContext.ensureActive()
            if (!isTabOpenProvider()) {
                return buildOutcome(
                    "FAILED",
                    topLevelCompleted,
                    "Target browser tab was closed; workflow halted safely.",
                    AutomationErrorCode.TAB_CLOSED,
                    rootVarStore,
                    0
                )
            }
            lifecycleController?.awaitRunnableOrAbort()?.let { abort ->
                val status = if (lifecycleController.isInterruptionAbort) "INTERRUPTED"
                else if (abort.code == AutomationErrorCode.CANCELLED) "CANCELLED"
                else "FAILED"
                return buildOutcome(status, topLevelCompleted, abort.message, abort.code, rootVarStore, 0)
            }
            val preStartPhase = if (tabId.isNotEmpty() && navigationSynchronizer != null) {
                navigationSynchronizer.snapshot(tabId).phase
            } else {
                NavigationLifecyclePhase.IDLE
            }
            val hadPendingLoad = preStartPhase == NavigationLifecyclePhase.LOADING
            if (hadPendingLoad) {
                onStepProgress(0, "Waiting for website to finish loading…")
            }
            val readyRes = engine.awaitDomReady(
                webViewProvider = webViewProvider,
                nextStep = if (hadPendingLoad) orderedSteps.firstOrNull() else null,
                tabId = if (hadPendingLoad) tabId else "",
                navigationSynchronizer = if (hadPendingLoad) navigationSynchronizer else null,
                lifecycleController = lifecycleController
            )
            if (!readyRes.success) {
                return buildOutcome("FAILED", 0, readyRes.message, readyRes.errorCode, rootVarStore, 0)
            }
            state.activeUrl = navigationSynchronizer?.snapshot(tabId)?.activeUrl?.ifBlank { state.activeUrl }
                ?: state.activeUrl
            state.expectedOrigin = engine.parseOrigin(state.activeUrl).canonicalOrigin
            lifecycleController?.updateBoundWebViewIfUnset(webViewProvider())
            lifecycleController?.updateAllowedOrigin(state.expectedOrigin)
            if (hadPendingLoad) {
                rescanAfterPageTransition(webViewProvider(), tabId, navigationSynchronizer, state.activeUrl)
                onStepProgress(0, "Website loaded — starting automation")
            }
        }

        // Recursive block executor returning null on block success or a failure StepExecutionResult
        suspend fun executeBlock(
            stepsToRun: List<AutomationStep>,
            varStore: WorkflowVariableStore,
            docMetadata: WorkflowDocumentMetadata,
            nestedDepth: Int,
            callStack: List<Long>,
            isRootSequence: Boolean
        ): StepExecutionResult? {
            for ((idx, rawStep) in stepsToRun.withIndex()) {
                if (isRootSequence && idx < startIndex) continue

                state.totalExecutions++
                if (state.totalExecutions > Phase6Limits.MAX_TOTAL_STEP_EXECUTIONS_PER_RUN) {
                    return StepExecutionResult(
                        success = false,
                        message = "Workflow exceeded maximum step execution budget (${Phase6Limits.MAX_TOTAL_STEP_EXECUTIONS_PER_RUN}).",
                        errorCode = AutomationErrorCode.LOOP_LIMIT_EXCEEDED
                    )
                }

                val nextStep = stepsToRun.getOrNull(idx + 1)
                val stepAllowsCrossOrigin = isCrossOriginTransitionAllowed(rawStep, docMetadata)
                checkRunnableContext(allowCrossOrigin = stepAllowsCrossOrigin)?.let { abort ->
                    return StepExecutionResult(success = false, message = abort.message, errorCode = abort.code)
                }

                val progressIndex = if (isRootSequence) idx else topLevelCompleted
                onStepProgress(progressIndex, "Running step ${idx + 1}/${stepsToRun.size}: ${rawStep.description}")
                onStepDetailedProgress?.invoke(progressIndex, rawStep, "", state.stepRecords.toList())
                if (isRootSequence) {
                    emitPhase6Telemetry(varStore, idx, topLevelCompleted)
                }

                val preSnap = if (tabId.isNotEmpty() && navigationSynchronizer != null) {
                    navigationSynchronizer.snapshot(tabId)
                } else {
                    null
                }
                val stepStartMs = System.currentTimeMillis()
                var stepOutcome: StepExecutionResult
                var recoveryNotice = ""

                when (rawStep.actionType) {
                    AutomationActionType.UNSUPPORTED -> {
                        stepOutcome = StepExecutionResult(
                            success = false,
                            message = "Unsupported action type '${rawStep.rawActionType.ifBlank { rawStep.actionType.id }}' cannot be executed.",
                            errorCode = AutomationErrorCode.UNSUPPORTED_ACTION
                        )
                    }
                    AutomationActionType.SET_VARIABLE -> {
                        stepOutcome = executeSetVariableStep(rawStep, varStore)
                    }
                    AutomationActionType.EXTRACT -> {
                        stepOutcome = executeExtractStepWithRecovery(
                            step = rawStep,
                            varStore = varStore,
                            docMetadata = docMetadata,
                            state = state,
                            tabId = tabId,
                            navigationSynchronizer = navigationSynchronizer,
                            webViewProvider = webViewProvider,
                            lifecycleController = lifecycleController,
                            isTabOpenProvider = isTabOpenProvider,
                            liveUrlProvider = liveUrlProvider,
                            confirmedUnsafeRetry = confirmedUnsafeRetry,
                            altExecutor = { alt -> executeBlock(alt, varStore, docMetadata, nestedDepth + 1, callStack, false) }
                        )
                    }
                    AutomationActionType.CONDITION -> {
                        val prevBranch = state.activeBranchLabel
                        val branchRes = executeConditionStep(
                            step = rawStep,
                            varStore = varStore,
                            state = state,
                            webViewProvider = webViewProvider,
                            tabId = tabId,
                            navigationSynchronizer = navigationSynchronizer,
                            nestedDepth = nestedDepth,
                            executeChildSteps = { childSteps ->
                                executeBlock(childSteps, varStore, docMetadata, nestedDepth + 1, callStack, false)
                            }
                        )
                        state.activeBranchLabel = prevBranch
                        stepOutcome = branchRes
                    }
                    AutomationActionType.LOOP -> {
                        val prevBranch = state.activeBranchLabel
                        val loopRes = executeLoopStep(
                            step = rawStep,
                            varStore = varStore,
                            state = state,
                            webViewProvider = webViewProvider,
                            nestedDepth = nestedDepth,
                            lifecycleController = lifecycleController,
                            executeChildSteps = { childSteps ->
                                executeBlock(childSteps, varStore, docMetadata, nestedDepth + 1, callStack, false)
                            }
                        )
                        state.activeBranchLabel = prevBranch
                        stepOutcome = loopRes
                    }
                    AutomationActionType.CALL_WORKFLOW -> {
                        val prevSub = state.activeSubWorkflowName
                        val subRes = executeCallWorkflowStep(
                            step = rawStep,
                            parentVarStore = varStore,
                            state = state,
                            callStack = callStack,
                            nestedDepth = nestedDepth,
                            workflowLookup = workflowLookup,
                            executeChildDoc = { childDoc, childStore, childCallStack ->
                                executeBlock(childDoc.steps, childStore, childDoc.metadata, nestedDepth + 1, childCallStack, false)
                            }
                        )
                        state.activeSubWorkflowName = prevSub
                        stepOutcome = subRes
                    }
                    else -> {
                        val resolvedRes = varStore.resolveStepParameters(rawStep)
                        if (resolvedRes.isFailure) {
                            val ex = resolvedRes.exceptionOrNull()
                            val errCode = if (ex is UndefinedVariableException) {
                                AutomationErrorCode.VARIABLE_UNDEFINED
                            } else {
                                AutomationErrorCode.VARIABLE_INVALID
                            }
                            stepOutcome = StepExecutionResult(
                                success = false,
                                message = ex?.message ?: "Variable resolution failed for '${rawStep.description}'.",
                                errorCode = errCode
                            )
                        } else {
                            val resolvedStep = resolvedRes.getOrThrow()
                            val effectivePolicy = resolvedStep.recoveryPolicy ?: docMetadata.defaultRecoveryPolicy
                            if (effectivePolicy != null) {
                                val recRun = executeStandardStepWithPolicy(
                                    step = resolvedStep,
                                    policy = effectivePolicy,
                                    state = state,
                                    nextStep = nextStep,
                                    tabId = tabId,
                                    navigationSynchronizer = navigationSynchronizer,
                                    webViewProvider = webViewProvider,
                                    onNavigateUrl = { dest ->
                                        state.activeUrl = dest
                                        state.expectedOrigin = engine.parseOrigin(dest).canonicalOrigin.ifBlank { state.expectedOrigin }
                                        lifecycleController?.updateAllowedOrigin(state.expectedOrigin)
                                        onNavigateUrl(dest)
                                    },
                                    credentialStepExecutor = credentialStepExecutor,
                                    lifecycleController = lifecycleController,
                                    isTabOpenProvider = isTabOpenProvider,
                                    liveUrlProvider = liveUrlProvider,
                                    confirmedUnsafeRetry = confirmedUnsafeRetry,
                                    altExecutor = { alt ->
                                        executeBlock(alt, varStore, docMetadata, nestedDepth + 1, callStack, false)
                                    },
                                    vaultValueResolver = vaultValueResolver,
                                    clearBrowserDataExecutor = clearBrowserDataExecutor
                                )
                                stepOutcome = recRun.first
                                recoveryNotice = recRun.second
                            } else {
                                stepOutcome = executeLegacySingleStep(
                                    step = resolvedStep,
                                    activeUrl = state.activeUrl,
                                    expectedOrigin = state.expectedOrigin,
                                    tabId = tabId,
                                    navigationSynchronizer = navigationSynchronizer,
                                    nextStep = nextStep,
                                    webViewProvider = webViewProvider,
                                    onNavigateUrl = { dest ->
                                        state.activeUrl = dest
                                        state.expectedOrigin = engine.parseOrigin(dest).canonicalOrigin.ifBlank { state.expectedOrigin }
                                        lifecycleController?.updateAllowedOrigin(state.expectedOrigin)
                                        onNavigateUrl(dest)
                                    },
                                    credentialStepExecutor = credentialStepExecutor,
                                    lifecycleController = lifecycleController,
                                    isTabOpenProvider = isTabOpenProvider,
                                    liveUrlProvider = liveUrlProvider,
                                    vaultValueResolver = vaultValueResolver,
                                    clearBrowserDataExecutor = clearBrowserDataExecutor
                                )
                            }
                            if (stepOutcome.recoveredViaFallback && recoveryNotice.isBlank()) {
                                recoveryNotice = "Recovered '${rawStep.targetLabel.ifBlank { rawStep.description }}' via updated page locator"
                            }
                        }
                    }
                }

                // Post-step WaitCondition (same as Phase 5)
                if (stepOutcome.success &&
                    rawStep.actionType != AutomationActionType.WAIT &&
                    rawStep.waitCondition != null
                ) {
                    val postCond = engine.awaitWaitCondition(
                        condition = rawStep.waitCondition,
                        tabId = tabId,
                        baselineUrl = state.activeUrl,
                        baselineOrigin = state.expectedOrigin,
                        navigationSynchronizer = navigationSynchronizer,
                        webViewProvider = webViewProvider,
                        lifecycleController = lifecycleController,
                        isTabOpenProvider = isTabOpenProvider,
                        liveUrlProvider = liveUrlProvider
                    )
                    if (!postCond.success) {
                        stepOutcome = postCond.copy(
                            recoveredViaFallback = stepOutcome.recoveredViaFallback,
                            resolvedSelector = stepOutcome.resolvedSelector
                        )
                    }
                }

                val stepDurationMs = (System.currentTimeMillis() - stepStartMs).coerceAtLeast(1L)

                checkRunnableContext(allowCrossOrigin = stepAllowsCrossOrigin)?.let { abort ->
                    return StepExecutionResult(success = false, message = abort.message, errorCode = abort.code)
                }

                if (stepOutcome.recoveredViaFallback) {
                    state.recoveredCount++
                }

                state.previousStepSucceeded = stepOutcome.success

                if (!stepOutcome.success) {
                    val safeFailureMsg = engine.sanitizeExecutionMessage(stepOutcome.message, rawStep)
                    if (rawStep.isOptional &&
                        stepOutcome.errorCode != AutomationErrorCode.ELEMENT_AMBIGUOUS &&
                        stepOutcome.errorCode != AutomationErrorCode.CANCELLED &&
                        stepOutcome.errorCode != AutomationErrorCode.WEBVIEW_REPLACED &&
                        stepOutcome.errorCode != AutomationErrorCode.TAB_CLOSED &&
                        stepOutcome.errorCode != AutomationErrorCode.UNEXPECTED_ORIGIN_CHANGE &&
                        stepOutcome.errorCode != AutomationErrorCode.VAULT_VALUES_EXHAUSTED
                    ) {
                        state.stepRecords.add(
                            StepExecutionRecord(
                                stepId = rawStep.id,
                                stepOrder = rawStep.order,
                                actionType = rawStep.actionType,
                                status = "SKIPPED_OPTIONAL",
                                durationMs = stepDurationMs,
                                errorCode = stepOutcome.errorCode,
                                recoveredViaFallback = stepOutcome.recoveredViaFallback,
                                resolvedSelector = stepOutcome.resolvedSelector,
                                safeMessage = safeFailureMsg,
                                ambiguousCandidates = stepOutcome.ambiguousCandidates,
                                locatorDiagnostics = stepOutcome.locatorDiagnostics,
                                targetBoundingBox = stepOutcome.targetBoundingBox
                            )
                        )
                        state.completedStepIds.add(rawStep.id)
                        if (isRootSequence) {
                            topLevelCompleted++
                            onStepCompleted(topLevelCompleted)
                        }
                        val skipMsg = "Step ${idx + 1} (optional) skipped: $safeFailureMsg"
                        state.notes.add(skipMsg)
                        onStepProgress(progressIndex + 1, skipMsg)
                        onStepDetailedProgress?.invoke(progressIndex + 1, rawStep, "", state.stepRecords.toList())
                        if (isRootSequence) emitPhase6Telemetry(varStore, idx + 1, topLevelCompleted)
                        continue
                    }

                    state.stepRecords.add(
                        StepExecutionRecord(
                            stepId = rawStep.id,
                            stepOrder = rawStep.order,
                            actionType = rawStep.actionType,
                            status = "FAILED",
                            durationMs = stepDurationMs,
                            errorCode = stepOutcome.errorCode,
                            recoveredViaFallback = stepOutcome.recoveredViaFallback,
                            resolvedSelector = stepOutcome.resolvedSelector,
                            safeMessage = safeFailureMsg,
                            ambiguousCandidates = stepOutcome.ambiguousCandidates,
                            locatorDiagnostics = stepOutcome.locatorDiagnostics,
                            targetBoundingBox = stepOutcome.targetBoundingBox
                        )
                    )
                    state.notes.add("Step ${idx + 1} failed: $safeFailureMsg")
                    return stepOutcome.copy(message = safeFailureMsg)
                }

                // Store previous step output in varStore for downstream condition checks / templating
                val safeSuccessMsg = engine.sanitizeExecutionMessage(stepOutcome.message, rawStep)
                if (rawStep.actionType !in setOf(
                        AutomationActionType.CONDITION,
                        AutomationActionType.LOOP,
                        AutomationActionType.CALL_WORKFLOW
                    )
                ) {
                    state.stepRecords.add(
                        StepExecutionRecord(
                            stepId = rawStep.id,
                            stepOrder = rawStep.order,
                            actionType = rawStep.actionType,
                            status = "COMPLETED",
                            durationMs = stepDurationMs,
                            errorCode = AutomationErrorCode.NONE,
                            recoveredViaFallback = stepOutcome.recoveredViaFallback,
                            resolvedSelector = stepOutcome.resolvedSelector,
                            safeMessage = safeSuccessMsg,
                            locatorDiagnostics = stepOutcome.locatorDiagnostics,
                            targetBoundingBox = stepOutcome.targetBoundingBox
                        )
                    )
                }
                state.completedStepIds.add(rawStep.id)

                // Step 2 Multi-Page Continuity: detect nav, redirect, SPA pushState, or reload after step
                if (rawStep.actionType !in setOf(
                        AutomationActionType.CONDITION,
                        AutomationActionType.LOOP,
                        AutomationActionType.CALL_WORKFLOW,
                        AutomationActionType.SET_VARIABLE
                    )
                ) {
                    val syncRes = synchronizeMultiPageContinuity(
                        executedStep = rawStep,
                        nextStep = nextStep,
                        docMetadata = docMetadata,
                        preSnapshot = preSnap,
                        state = state,
                        tabId = tabId,
                        navigationSynchronizer = navigationSynchronizer,
                        webViewProvider = webViewProvider,
                        lifecycleController = lifecycleController,
                        liveUrlProvider = liveUrlProvider
                    )
                    if (!syncRes.success) {
                        val safeSyncMsg = engine.sanitizeExecutionMessage(syncRes.message, rawStep)
                        return syncRes.copy(message = safeSyncMsg)
                    }
                }

                if (isRootSequence) {
                    topLevelCompleted++
                    onStepCompleted(topLevelCompleted)
                    emitPhase6Telemetry(varStore, idx + 1, topLevelCompleted)
                }
                state.notes.add("Step ${idx + 1}: $safeSuccessMsg")
                onStepProgress(progressIndex + 1, "Verified: $safeSuccessMsg")
                onStepDetailedProgress?.invoke(progressIndex + 1, rawStep, recoveryNotice, state.stepRecords.toList())
            }
            return null
        }

        val failure = executeBlock(
            stepsToRun = orderedSteps,
            varStore = rootVarStore,
            docMetadata = document.metadata,
            nestedDepth = 0,
            callStack = listOf(workflow.id),
            isRootSequence = true
        )

        if (failure != null) {
            val status = when {
                lifecycleController?.isInterruptionAbort == true -> "INTERRUPTED"
                failure.errorCode == AutomationErrorCode.CANCELLED -> "CANCELLED"
                else -> "FAILED"
            }
            return buildOutcome(
                status = status,
                completed = topLevelCompleted,
                summary = failure.message,
                errorCode = failure.errorCode,
                varStore = rootVarStore,
                nextIndex = topLevelCompleted,
                ambiguousCandidates = failure.ambiguousCandidates,
                lastDiagnostics = failure.locatorDiagnostics
            )
        }

        val totalDurationMs = (System.currentTimeMillis() - workflowStartMs).coerceAtLeast(0L)
        val durationSec = String.format(java.util.Locale.US, "%.1fs", totalDurationMs / 1000.0)
        val recoveredSuffix = if (state.recoveredCount > 0) " (${state.recoveredCount} locator recovered)" else ""
        val branchSuffix = if (state.branchTraces.isNotEmpty()) " (${state.branchTraces.size} branch checks)" else ""
        return buildOutcome(
            status = "COMPLETED",
            completed = topLevelCompleted,
            summary = "Verified $topLevelCompleted/${orderedSteps.size} steps on ${engine.extractDomain(state.activeUrl)} in $durationSec$recoveredSuffix$branchSuffix. ${state.notes.lastOrNull().orEmpty()}",
            errorCode = AutomationErrorCode.NONE,
            varStore = rootVarStore,
            nextIndex = orderedSteps.size
        )
    }

    private fun usesPhase6Constructs(steps: List<AutomationStep>, metadata: WorkflowDocumentMetadata): Boolean {
        if (!metadata.isEmptyDefault) return true
        return steps.any { s ->
            s.actionType in setOf(
                AutomationActionType.CONDITION,
                AutomationActionType.LOOP,
                AutomationActionType.SET_VARIABLE,
                AutomationActionType.CALL_WORKFLOW
            ) || s.extractionSpec != null || s.recoveryPolicy != null ||
                s.value.contains("\${") || s.selector.contains("\${") || s.targetLabel.contains("\${")
        }
    }

    private fun isCrossOriginTransitionAllowed(
        step: AutomationStep,
        metadata: WorkflowDocumentMetadata
    ): Boolean {
        return step.actionType == AutomationActionType.NAVIGATE ||
            step.allowCrossOriginTransition ||
            step.waitCondition?.type == WaitConditionType.ORIGIN_CHANGE ||
            step.waitCondition?.type == WaitConditionType.URL_CHANGE ||
            metadata.allowedOrigins.isNotEmpty()
    }

    private fun isOriginPermittedForWorkflow(
        candidateOrigin: String,
        previousOrigin: String,
        executedStep: AutomationStep,
        nextStep: AutomationStep?,
        metadata: WorkflowDocumentMetadata
    ): Boolean {
        if (candidateOrigin.isBlank()) return false
        if (previousOrigin.isBlank() || candidateOrigin == previousOrigin) return true
        if (executedStep.actionType == AutomationActionType.NAVIGATE ||
            executedStep.allowCrossOriginTransition ||
            executedStep.waitCondition?.type == WaitConditionType.ORIGIN_CHANGE ||
            executedStep.waitCondition?.type == WaitConditionType.URL_CHANGE
        ) {
            return true
        }
        if (nextStep?.expectedOrigin?.isNotBlank() == true) {
            val nextCanon = engine.parseOrigin(nextStep.expectedOrigin).canonicalOrigin
            if (nextCanon == candidateOrigin) return true
        }
        if (metadata.allowedOrigins.any { allowed ->
                engine.parseOrigin(allowed).canonicalOrigin == candidateOrigin || allowed.equals(candidateOrigin, ignoreCase = true)
            }
        ) {
            return true
        }
        return false
    }

    private suspend fun synchronizeMultiPageContinuity(
        executedStep: AutomationStep,
        nextStep: AutomationStep?,
        docMetadata: WorkflowDocumentMetadata,
        preSnapshot: TabNavigationSnapshot?,
        state: ExecutionContextState,
        tabId: String,
        navigationSynchronizer: TabNavigationSynchronizer?,
        webViewProvider: () -> WebView?,
        lifecycleController: ExecutionLifecycleController?,
        liveUrlProvider: (() -> String)?
    ): StepExecutionResult {
        coroutineContext.ensureActive()
        val preUrl = state.activeUrl
        val preOrigin = state.expectedOrigin
        var urlOrEpochChanged = false

        if (executedStep.actionType == AutomationActionType.NAVIGATE) {
            val snap = if (tabId.isNotEmpty() && navigationSynchronizer != null) navigationSynchronizer.snapshot(tabId) else null
            val resolvedUrl = snap?.activeUrl?.takeIf { it.isNotBlank() }
                ?: liveUrlProvider?.invoke()?.takeIf { it.isNotBlank() }
                ?: state.activeUrl
            state.activeUrl = resolvedUrl
            val newOrig = engine.parseOrigin(resolvedUrl).canonicalOrigin
            if (newOrig.isNotBlank()) {
                state.expectedOrigin = newOrig
                lifecycleController?.updateAllowedOrigin(newOrig)
            }
            urlOrEpochChanged = true
        } else {
            if (tabId.isNotEmpty() && navigationSynchronizer != null && preSnapshot != null) {
                if (executedStep.actionType == AutomationActionType.CLICK ||
                    executedStep.actionType == AutomationActionType.SUBMIT ||
                    executedStep.actionType == AutomationActionType.KEY_PRESS ||
                    executedStep.actionType == AutomationActionType.SELECT ||
                    executedStep.actionType == AutomationActionType.TOGGLE
                ) {
                    val yieldMs = 80L
                    if (lifecycleController != null) {
                        lifecycleController.interruptibleDelay(yieldMs)
                    } else {
                        kotlinx.coroutines.delay(yieldMs)
                    }
                    coroutineContext.ensureActive()
                }
                val currentSnap = navigationSynchronizer.snapshot(tabId)
                if (currentSnap.phase == NavigationLifecyclePhase.FAILED &&
                    currentSnap.epoch >= preSnapshot.epoch &&
                    currentSnap.updatedAtMs > preSnapshot.updatedAtMs
                ) {
                    return StepExecutionResult(
                        success = false,
                        message = "Navigation failed after '${executedStep.description}': ${currentSnap.failureReason ?: "Load error"}",
                        errorCode = AutomationErrorCode.NAVIGATION_FAILED
                    )
                }
                val navStarted = currentSnap.epoch > preSnapshot.epoch ||
                    (currentSnap.phase == NavigationLifecyclePhase.LOADING &&
                        (preSnapshot.phase != NavigationLifecyclePhase.LOADING || currentSnap.updatedAtMs > preSnapshot.updatedAtMs)) ||
                    (currentSnap.phase == NavigationLifecyclePhase.REQUESTED &&
                        preSnapshot.phase != NavigationLifecyclePhase.REQUESTED &&
                        preSnapshot.phase != NavigationLifecyclePhase.LOADING)
                if (navStarted) {
                    val targetEpoch = maxOf(preSnapshot.epoch, currentSnap.epoch)
                    when (val waitRes = navigationSynchronizer.awaitNavigationSettled(tabId, targetEpoch, AutomationEngine.DEFAULT_NAV_TIMEOUT_MS)) {
                        is NavigationWaitResult.Failed -> return StepExecutionResult(
                            success = false,
                            message = "Navigation failed after '${executedStep.description}': ${waitRes.reason}",
                            errorCode = AutomationErrorCode.NAVIGATION_FAILED
                        )
                        is NavigationWaitResult.Timeout -> return StepExecutionResult(
                            success = false,
                            message = "Navigation timed out after '${executedStep.description}'.",
                            errorCode = AutomationErrorCode.NAVIGATION_TIMEOUT
                        )
                        is NavigationWaitResult.Completed -> {
                            urlOrEpochChanged = true
                        }
                    }
                }
                val afterSnap = navigationSynchronizer.snapshot(tabId)
                if (afterSnap.urlCommitSeq > preSnapshot.urlCommitSeq ||
                    afterSnap.epoch > preSnapshot.epoch ||
                    (afterSnap.activeUrl.isNotBlank() && afterSnap.activeUrl != preUrl)
                ) {
                    urlOrEpochChanged = true
                    if (afterSnap.activeUrl.isNotBlank()) {
                        state.activeUrl = afterSnap.activeUrl
                    }
                }
            }

            // Check liveUrlProvider in case SPA route changed directly on WebView
            val liveNow = liveUrlProvider?.invoke()?.takeIf { it.isNotBlank() }
            if (liveNow != null && liveNow != state.activeUrl) {
                state.activeUrl = liveNow
                urlOrEpochChanged = true
            }

            val postOrig = engine.parseOrigin(state.activeUrl).canonicalOrigin
            if (postOrig.isNotBlank() && postOrig != preOrigin) {
                if (!isOriginPermittedForWorkflow(postOrig, preOrigin, executedStep, nextStep, docMetadata)) {
                    return StepExecutionResult(
                        success = false,
                        message = "Unexpected origin transition blocked: '$preOrigin' -> '$postOrig'.",
                        errorCode = AutomationErrorCode.UNEXPECTED_ORIGIN_CHANGE
                    )
                }
                state.expectedOrigin = postOrig
                lifecycleController?.updateAllowedOrigin(postOrig)
                urlOrEpochChanged = true
            }
        }

        if (nextStep != null && docMetadata.allowMultiPageContinuity) {
            val domReady = engine.awaitDomReady(
                webViewProvider = webViewProvider,
                nextStep = nextStep,
                tabId = tabId,
                navigationSynchronizer = navigationSynchronizer,
                lifecycleController = lifecycleController
            )
            if (!domReady.success) return domReady
            if (urlOrEpochChanged) {
                rescanAfterPageTransition(webViewProvider(), tabId, navigationSynchronizer, state.activeUrl)
            }
        }
        return StepExecutionResult(success = true, message = "Page state synchronized")
    }

    private suspend fun rescanAfterPageTransition(
        webView: WebView?,
        tabId: String,
        navigationSynchronizer: TabNavigationSynchronizer?,
        activeUrl: String
    ) {
        if (webView == null) return
        val epoch = if (tabId.isNotEmpty() && navigationSynchronizer != null) {
            navigationSynchronizer.snapshot(tabId).epoch
        } else {
            0L
        }
        engine.universalScanner.scanPage(
            webView = webView,
            tabId = tabId,
            currentUrl = activeUrl,
            navigationEpoch = epoch,
            installDynamicObserver = false
        )
    }

    private suspend fun executeSetVariableStep(
        step: AutomationStep,
        varStore: WorkflowVariableStore
    ): StepExecutionResult {
        val varName = step.variableName.trim().ifBlank { step.targetLabel.trim() }
        val resolvedVal = when (val res = varStore.resolveTemplate(step.value, strictUndefined = true)) {
            is VariableResolutionResult.Success -> res.value
            is VariableResolutionResult.Undefined -> return StepExecutionResult(
                success = false,
                message = res.message,
                errorCode = AutomationErrorCode.VARIABLE_UNDEFINED
            )
            is VariableResolutionResult.Invalid -> return StepExecutionResult(
                success = false,
                message = res.message,
                errorCode = AutomationErrorCode.VARIABLE_INVALID
            )
        }
        val setRes = varStore.setVariable(varName, step.variableType, resolvedVal)
        return if (setRes.isSuccess) {
            val preview = if (WorkflowVariableStore.shouldMaskInUi(varName)) "••••" else resolvedVal.take(36)
            StepExecutionResult(
                success = true,
                message = "Set \${$varName} = '$preview'",
                verified = true
            )
        } else {
            StepExecutionResult(
                success = false,
                message = setRes.exceptionOrNull()?.message ?: "Invalid variable assignment for '$varName'.",
                errorCode = AutomationErrorCode.VARIABLE_INVALID
            )
        }
    }

    private suspend fun executeExtractStepOnce(
        step: AutomationStep,
        varStore: WorkflowVariableStore,
        webViewProvider: () -> WebView?
    ): StepExecutionResult {
        val resolvedRes = varStore.resolveStepParameters(step)
        if (resolvedRes.isFailure) {
            val ex = resolvedRes.exceptionOrNull()
            return StepExecutionResult(
                success = false,
                message = ex?.message ?: "Variable substitution failed",
                errorCode = if (ex is UndefinedVariableException) AutomationErrorCode.VARIABLE_UNDEFINED else AutomationErrorCode.VARIABLE_INVALID
            )
        }
        val resolvedStep = resolvedRes.getOrThrow()
        val wv = webViewProvider() ?: return StepExecutionResult(
            success = false,
            message = "WebView unavailable for extraction step '${step.description}'.",
            errorCode = AutomationErrorCode.WEBVIEW_UNAVAILABLE
        )
        val spec = resolvedStep.extractionSpec ?: ExtractionSpec(
            mode = ExtractionMode.VISIBLE_TEXT,
            outputVariableName = resolvedStep.variableName
        )
        val script = Phase6Scripts.buildStructuredExtractScript(resolvedStep, spec)
        val evalRes = engine.evalJsWithResultInternal(wv, script, timeoutMs = if (resolvedStep.stepTimeoutMs > 0L) resolvedStep.stepTimeoutMs else AutomationEngine.DEFAULT_JS_EVAL_TIMEOUT_MS)
        return when (evalRes) {
            is JsEvalResult.Timeout -> StepExecutionResult(
                success = false,
                message = "Extraction timed out for '${step.description}'.",
                errorCode = AutomationErrorCode.JS_TIMEOUT
            )
            is JsEvalResult.Unavailable -> StepExecutionResult(
                success = false,
                message = "WebView unavailable during extraction: ${evalRes.reason}",
                errorCode = AutomationErrorCode.WEBVIEW_UNAVAILABLE
            )
            is JsEvalResult.Failure -> StepExecutionResult(
                success = false,
                message = "Extraction JS failed: ${evalRes.reason}",
                errorCode = AutomationErrorCode.JS_EXCEPTION
            )
            is JsEvalResult.Success -> {
                val obj = engine.parseEvalJsonObject(evalRes.rawValue)
                    ?: return StepExecutionResult(
                        success = false,
                        message = "Invalid JSON extraction payload for '${step.description}'.",
                        errorCode = AutomationErrorCode.JS_EXCEPTION
                    )
                val ok = obj.optBoolean("ok", false)
                val code = AutomationErrorCode.entries.firstOrNull { it.name == obj.optString("code", "") }
                    ?: if (ok) AutomationErrorCode.NONE else AutomationErrorCode.JS_EXCEPTION
                if (!ok) {
                    return StepExecutionResult(
                        success = false,
                        message = obj.optString("msg", "Extraction failed"),
                        errorCode = code
                    )
                }
                val value = obj.optString("value", "")
                val itemsArr = obj.optJSONArray("items") ?: JSONArray()
                val items = buildList {
                    for (i in 0 until itemsArr.length()) {
                        add(itemsArr.optString(i, ""))
                    }
                }
                val outVar = spec.outputVariableName.trim().ifBlank { resolvedStep.variableName.trim() }
                if (outVar.isNotEmpty()) {
                    val varType = if (spec.mode == ExtractionMode.PAGE_URL || spec.mode == ExtractionMode.LINK_URL) {
                        WorkflowVariableType.URL
                    } else {
                        resolvedStep.variableType
                    }
                    val storeRes = varStore.setVariable(
                        name = outVar,
                        type = varType,
                        rawValue = value,
                        isList = spec.allowMultiple && items.size > 1,
                        listValues = items
                    )
                    if (storeRes.isFailure) {
                        return StepExecutionResult(
                            success = false,
                            message = storeRes.exceptionOrNull()?.message ?: "Failed to store extracted variable '$outVar'.",
                            errorCode = AutomationErrorCode.VARIABLE_INVALID
                        )
                    }
                }
                val preview = if (outVar.isNotEmpty() && WorkflowVariableStore.shouldMaskInUi(outVar)) "••••" else value.take(40)
                StepExecutionResult(
                    success = true,
                    message = if (outVar.isNotEmpty()) "Extracted '\${$outVar}' = \"$preview\"" else obj.optString("msg", "Extracted \"$preview\""),
                    errorCode = AutomationErrorCode.NONE,
                    resolvedSelector = resolvedStep.selector,
                    verified = true,
                    extractedValue = value,
                    extractedItems = items
                )
            }
        }
    }

    private suspend fun executeExtractStepWithRecovery(
        step: AutomationStep,
        varStore: WorkflowVariableStore,
        docMetadata: WorkflowDocumentMetadata,
        state: ExecutionContextState,
        tabId: String,
        navigationSynchronizer: TabNavigationSynchronizer?,
        webViewProvider: () -> WebView?,
        lifecycleController: ExecutionLifecycleController?,
        isTabOpenProvider: () -> Boolean,
        liveUrlProvider: (() -> String)?,
        confirmedUnsafeRetry: Boolean,
        altExecutor: suspend (List<AutomationStep>) -> StepExecutionResult?
    ): StepExecutionResult {
        val policy = step.recoveryPolicy ?: docMetadata.defaultRecoveryPolicy
        val initial = executeExtractStepOnce(step, varStore, webViewProvider)
        if (initial.success || policy == null) return initial
        return applyRecoveryPolicy(
            step = step,
            initialFailure = initial,
            policy = policy,
            state = state,
            tabId = tabId,
            navigationSynchronizer = navigationSynchronizer,
            webViewProvider = webViewProvider,
            lifecycleController = lifecycleController,
            isTabOpenProvider = isTabOpenProvider,
            liveUrlProvider = liveUrlProvider,
            confirmedUnsafeRetry = confirmedUnsafeRetry,
            attemptRunner = { candidateStep -> executeExtractStepOnce(candidateStep, varStore, webViewProvider) },
            altExecutor = altExecutor
        ).first
    }

    private suspend fun executeConditionStep(
        step: AutomationStep,
        varStore: WorkflowVariableStore,
        state: ExecutionContextState,
        webViewProvider: () -> WebView?,
        tabId: String,
        navigationSynchronizer: TabNavigationSynchronizer?,
        nestedDepth: Int,
        executeChildSteps: suspend (List<AutomationStep>) -> StepExecutionResult?
    ): StepExecutionResult {
        val primaryCond = step.condition ?: return StepExecutionResult(
            success = false,
            message = "Condition step '${step.description}' has no IF condition.",
            errorCode = AutomationErrorCode.CONDITION_INVALID
        )
        val primaryEval = evaluateConditionNode(
            node = primaryCond,
            varStore = varStore,
            state = state,
            webView = webViewProvider(),
            depth = 1
        )
        if (primaryEval.errorCode != AutomationErrorCode.NONE) {
            return StepExecutionResult(false, primaryEval.detail, primaryEval.errorCode)
        }
        if (primaryEval.matched) {
            state.activeBranchLabel = "IF: ${step.targetLabel.ifBlank { primaryCond.toHumanSummary() }}"
            state.branchTraces.add(
                BranchTraceRecord(
                    stepId = step.id,
                    branchLabel = "IF (${step.description})",
                    conditionSummary = primaryCond.toHumanSummary(),
                    matched = true,
                    nestedDepth = nestedDepth,
                    executedStepsCount = step.thenSteps.size,
                    subWorkflowName = state.activeSubWorkflowName
                )
            )
            val err = executeChildSteps(step.thenSteps)
            return err ?: StepExecutionResult(
                success = true,
                message = "Executed IF branch (${step.thenSteps.size} steps)",
                verified = true
            )
        }
        state.branchTraces.add(
            BranchTraceRecord(
                stepId = step.id,
                branchLabel = "IF (${step.description})",
                conditionSummary = primaryCond.toHumanSummary(),
                matched = false,
                nestedDepth = nestedDepth,
                executedStepsCount = 0,
                subWorkflowName = state.activeSubWorkflowName
            )
        )

        for ((branchIdx, elif) in step.elseIfBranches.withIndex()) {
            val elifEval = evaluateConditionNode(
                node = elif.condition,
                varStore = varStore,
                state = state,
                webView = webViewProvider(),
                depth = 1
            )
            if (elifEval.errorCode != AutomationErrorCode.NONE) {
                return StepExecutionResult(false, elifEval.detail, elifEval.errorCode)
            }
            state.branchTraces.add(
                BranchTraceRecord(
                    stepId = "${step.id}_elif_$branchIdx",
                    branchLabel = elif.label.ifBlank { "ELSE IF #${branchIdx + 1}" },
                    conditionSummary = elif.condition.toHumanSummary(),
                    matched = elifEval.matched,
                    nestedDepth = nestedDepth,
                    executedStepsCount = if (elifEval.matched) elif.steps.size else 0,
                    subWorkflowName = state.activeSubWorkflowName
                )
            )
            if (elifEval.matched) {
                state.activeBranchLabel = "${elif.label.ifBlank { "ELSE IF" }}: ${elif.condition.toHumanSummary()}"
                val err = executeChildSteps(elif.steps)
                return err ?: StepExecutionResult(
                    success = true,
                    message = "Executed ${elif.label} branch (${elif.steps.size} steps)",
                    verified = true
                )
            }
        }

        if (step.elseSteps.isNotEmpty()) {
            state.activeBranchLabel = "ELSE (${step.description})"
            state.branchTraces.add(
                BranchTraceRecord(
                    stepId = "${step.id}_else",
                    branchLabel = "ELSE (${step.description})",
                    conditionSummary = "Fallback else branch",
                    matched = true,
                    nestedDepth = nestedDepth,
                    executedStepsCount = step.elseSteps.size,
                    subWorkflowName = state.activeSubWorkflowName
                )
            )
            val err = executeChildSteps(step.elseSteps)
            return err ?: StepExecutionResult(
                success = true,
                message = "Executed ELSE branch (${step.elseSteps.size} steps)",
                verified = true
            )
        }

        return StepExecutionResult(
            success = true,
            message = "Condition false with no matching ELSE branch (skipped safely)",
            verified = true
        )
    }

    private suspend fun executeLoopStep(
        step: AutomationStep,
        varStore: WorkflowVariableStore,
        state: ExecutionContextState,
        webViewProvider: () -> WebView?,
        nestedDepth: Int,
        lifecycleController: ExecutionLifecycleController?,
        executeChildSteps: suspend (List<AutomationStep>) -> StepExecutionResult?
    ): StepExecutionResult {
        val cfg = step.loopConfig ?: return StepExecutionResult(
            success = false,
            message = "Loop '${step.description}' is missing configuration.",
            errorCode = AutomationErrorCode.LOOP_LIMIT_EXCEEDED
        )
        val boundedMax = cfg.maxIterations.coerceIn(1, Phase6Limits.MAX_LOOP_ITERATIONS)
        var iterationsRun = 0
        for (loopIndex in 0 until boundedMax) {
            val iteration = loopIndex + 1
            coroutineContext.ensureActive()
            lifecycleController?.awaitRunnableOrAbort()?.let { abort ->
                return StepExecutionResult(false, abort.message, abort.code)
            }
            if (cfg.iterationVariable.isNotBlank()) {
                varStore.setVariable(cfg.iterationVariable, WorkflowVariableType.NUMBER, loopIndex.toString())
            }
            if (cfg.continueCondition != null) {
                val condEval = evaluateConditionNode(
                    node = cfg.continueCondition,
                    varStore = varStore,
                    state = state,
                    webView = webViewProvider(),
                    depth = 1
                )
                if (condEval.errorCode != AutomationErrorCode.NONE) {
                    return StepExecutionResult(false, condEval.detail, condEval.errorCode)
                }
                if (!condEval.matched) {
                    state.branchTraces.add(
                        BranchTraceRecord(
                            stepId = "${step.id}_iter_$iteration",
                            branchLabel = "LOOP stop @#$iteration",
                            conditionSummary = cfg.continueCondition.toHumanSummary(),
                            matched = false,
                            nestedDepth = nestedDepth,
                            executedStepsCount = 0,
                            subWorkflowName = state.activeSubWorkflowName
                        )
                    )
                    break
                }
            }
            state.activeBranchLabel = "LOOP #$iteration/$boundedMax (${step.description})"
            iterationsRun++
            state.branchTraces.add(
                BranchTraceRecord(
                    stepId = "${step.id}_iter_$iteration",
                    branchLabel = "LOOP #$iteration/$boundedMax",
                    conditionSummary = cfg.continueCondition?.toHumanSummary() ?: "Bounded iteration $iteration",
                    matched = true,
                    nestedDepth = nestedDepth,
                    executedStepsCount = step.thenSteps.size,
                    subWorkflowName = state.activeSubWorkflowName
                )
            )
            val err = executeChildSteps(step.thenSteps)
            if (err != null) return err
        }
        return StepExecutionResult(
            success = true,
            message = "Completed $iterationsRun bounded loop iteration(s)",
            verified = true
        )
    }

    private suspend fun executeCallWorkflowStep(
        step: AutomationStep,
        parentVarStore: WorkflowVariableStore,
        state: ExecutionContextState,
        callStack: List<Long>,
        nestedDepth: Int,
        workflowLookup: (suspend (Long) -> Pair<String, WorkflowDocument>?)?,
        executeChildDoc: suspend (WorkflowDocument, WorkflowVariableStore, List<Long>) -> StepExecutionResult?
    ): StepExecutionResult {
        val ref = step.subWorkflowRef ?: return StepExecutionResult(
            success = false,
            message = "Sub-workflow call '${step.description}' has no reference.",
            errorCode = AutomationErrorCode.SUBWORKFLOW_NOT_FOUND
        )
        if (ref.targetWorkflowId in callStack) {
            return StepExecutionResult(
                success = false,
                message = "Circular sub-workflow call blocked (ID ${ref.targetWorkflowId}).",
                errorCode = AutomationErrorCode.SUBWORKFLOW_CYCLE
            )
        }
        if (callStack.size > Phase6Limits.MAX_SUBWORKFLOW_DEPTH) {
            return StepExecutionResult(
                success = false,
                message = "Maximum sub-workflow nesting depth (${Phase6Limits.MAX_SUBWORKFLOW_DEPTH}) exceeded.",
                errorCode = AutomationErrorCode.SUBWORKFLOW_DEPTH_EXCEEDED
            )
        }
        val loaded = workflowLookup?.invoke(ref.targetWorkflowId) ?: return StepExecutionResult(
            success = false,
            message = "Sub-workflow '${ref.targetWorkflowName.ifBlank { ref.targetWorkflowId }}' not found.",
            errorCode = AutomationErrorCode.SUBWORKFLOW_NOT_FOUND
        )
        val (childName, childDoc) = loaded
        val childScopeRes = parentVarStore.createChildScope(childDoc.metadata.variables, ref.inputMappings)
        if (childScopeRes.isFailure) {
            val ex = childScopeRes.exceptionOrNull()
            return StepExecutionResult(
                success = false,
                message = ex?.message ?: "Failed to bind sub-workflow inputs for '$childName'.",
                errorCode = if (ex is UndefinedVariableException) AutomationErrorCode.VARIABLE_UNDEFINED else AutomationErrorCode.VARIABLE_INVALID
            )
        }
        val childStore = childScopeRes.getOrThrow()
        state.activeSubWorkflowName = childName
        state.branchTraces.add(
            BranchTraceRecord(
                stepId = step.id,
                branchLabel = "CALL $childName",
                conditionSummary = "Sub-workflow (${childDoc.steps.size} steps)",
                matched = true,
                nestedDepth = nestedDepth,
                executedStepsCount = childDoc.steps.size,
                subWorkflowName = childName
            )
        )
        val childErr = executeChildDoc(childDoc, childStore, callStack + ref.targetWorkflowId)
        if (childErr != null) {
            childStore.clear()
            return childErr.copy(message = "Sub-workflow '$childName' failed: ${childErr.message}")
        }
        if (ref.outputMappings.isNotEmpty()) {
            val outRes = parentVarStore.applyChildOutputs(childStore, ref.outputMappings)
            if (outRes.isFailure) {
                val ex = outRes.exceptionOrNull()
                childStore.clear()
                return StepExecutionResult(
                    success = false,
                    message = ex?.message ?: "Sub-workflow '$childName' missing declared output.",
                    errorCode = if (ex is UndefinedVariableException) AutomationErrorCode.VARIABLE_UNDEFINED else AutomationErrorCode.VARIABLE_INVALID
                )
            }
        }
        childStore.clear()
        return StepExecutionResult(
            success = true,
            message = "Completed sub-workflow '$childName'",
            verified = true
        )
    }

    private data class ConditionEvalOutcome(
        val matched: Boolean,
        val detail: String,
        val errorCode: AutomationErrorCode = AutomationErrorCode.NONE
    )

    private suspend fun evaluateConditionNode(
        node: ConditionNode,
        varStore: WorkflowVariableStore,
        state: ExecutionContextState,
        webView: WebView?,
        depth: Int
    ): ConditionEvalOutcome {
        if (depth > Phase6Limits.MAX_CONDITION_DEPTH) {
            return ConditionEvalOutcome(
                matched = false,
                detail = "Condition depth exceeded maximum (${Phase6Limits.MAX_CONDITION_DEPTH}).",
                errorCode = AutomationErrorCode.CONDITION_INVALID
            )
        }
        return when (node) {
            is ConditionNode.Group -> {
                if (node.children.isEmpty()) {
                    return ConditionEvalOutcome(
                        matched = false,
                        detail = "Empty condition group.",
                        errorCode = AutomationErrorCode.CONDITION_INVALID
                    )
                }
                val rawMatched = if (node.combinator == LogicalCombinator.AND) {
                    var all = true
                    for (child in node.children) {
                        val r = evaluateConditionNode(child, varStore, state, webView, depth + 1)
                        if (r.errorCode != AutomationErrorCode.NONE) return r
                        if (!r.matched) {
                            all = false
                            break
                        }
                    }
                    all
                } else {
                    var any = false
                    for (child in node.children) {
                        val r = evaluateConditionNode(child, varStore, state, webView, depth + 1)
                        if (r.errorCode != AutomationErrorCode.NONE) return r
                        if (r.matched) {
                            any = true
                            break
                        }
                    }
                    any
                }
                val finalMatch = if (node.negate) !rawMatched else rawMatched
                ConditionEvalOutcome(finalMatch, "${node.combinator.id} evaluated to $finalMatch")
            }
            is ConditionNode.Predicate -> evaluatePredicate(node, varStore, state, webView)
        }
    }

    private suspend fun evaluatePredicate(
        pred: ConditionNode.Predicate,
        varStore: WorkflowVariableStore,
        state: ExecutionContextState,
        webView: WebView?
    ): ConditionEvalOutcome {
        fun resolveOperand(raw: String): Result<String> = when (val r = varStore.resolveTemplate(raw, strictUndefined = true)) {
            is VariableResolutionResult.Success -> Result.success(r.value)
            is VariableResolutionResult.Undefined -> Result.failure(UndefinedVariableException(r.variableName, r.message))
            is VariableResolutionResult.Invalid -> Result.failure(InvalidVariableException(r.variableName, r.message))
        }

        val resolvedExpected = resolveOperand(pred.expectedValue).getOrElse { ex ->
            val err = if (ex is UndefinedVariableException) AutomationErrorCode.VARIABLE_UNDEFINED else AutomationErrorCode.VARIABLE_INVALID
            return ConditionEvalOutcome(false, ex.message ?: "Condition variable error", err)
        }
        val resolvedSelector = resolveOperand(pred.selector).getOrElse { ex ->
            val err = if (ex is UndefinedVariableException) AutomationErrorCode.VARIABLE_UNDEFINED else AutomationErrorCode.VARIABLE_INVALID
            return ConditionEvalOutcome(false, ex.message ?: "Condition selector variable error", err)
        }
        val resolvedLabel = resolveOperand(pred.targetLabel).getOrElse { ex ->
            val err = if (ex is UndefinedVariableException) AutomationErrorCode.VARIABLE_UNDEFINED else AutomationErrorCode.VARIABLE_INVALID
            return ConditionEvalOutcome(false, ex.message ?: "Condition target variable error", err)
        }

        val rawMatched: Boolean
        var detail = ""

        when (pred.operator) {
            ConditionOperator.PREVIOUS_STEP_SUCCEEDED -> {
                rawMatched = state.previousStepSucceeded
                detail = "Previous step succeeded=$rawMatched"
            }
            ConditionOperator.PREVIOUS_STEP_FAILED -> {
                rawMatched = !state.previousStepSucceeded
                detail = "Previous step failed=$rawMatched"
            }
            ConditionOperator.URL_MATCHES -> {
                val url = state.activeUrl
                rawMatched = matchesUrlPattern(url, resolvedExpected)
                detail = "URL '$url' matches '$resolvedExpected': $rawMatched"
            }
            ConditionOperator.ORIGIN_MATCHES -> {
                val expectedOriginCanon = engine.parseOrigin(resolvedExpected).canonicalOrigin.ifBlank { resolvedExpected.trim() }
                val actualCanon = engine.parseOrigin(state.activeUrl).canonicalOrigin.ifBlank { state.expectedOrigin }
                rawMatched = actualCanon.isNotEmpty() && actualCanon.equals(expectedOriginCanon, ignoreCase = true)
                detail = "Origin '$actualCanon' == '$expectedOriginCanon': $rawMatched"
            }
            ConditionOperator.VARIABLE_EQUALS,
            ConditionOperator.VARIABLE_CONTAINS,
            ConditionOperator.VARIABLE_NOT_EMPTY,
            ConditionOperator.VARIABLE_GREATER_THAN,
            ConditionOperator.VARIABLE_LESS_THAN -> {
                val stored = varStore.getVariable(pred.variableName)
                    ?: return ConditionEvalOutcome(
                        matched = false,
                        detail = "Undefined variable '${pred.variableName}' in condition.",
                        errorCode = AutomationErrorCode.VARIABLE_UNDEFINED
                    )
                val v = stored.rawValue
                rawMatched = when (pred.operator) {
                    ConditionOperator.VARIABLE_EQUALS -> v == resolvedExpected
                    ConditionOperator.VARIABLE_CONTAINS -> v.contains(resolvedExpected, ignoreCase = true)
                    ConditionOperator.VARIABLE_NOT_EMPTY -> v.trim().isNotEmpty()
                    ConditionOperator.VARIABLE_GREATER_THAN,
                    ConditionOperator.VARIABLE_LESS_THAN -> {
                        val left = v.trim().toDoubleOrNull()
                        val right = resolvedExpected.trim().toDoubleOrNull()
                        if (left == null || right == null) {
                            return ConditionEvalOutcome(
                                matched = false,
                                detail = "Numeric comparison requires valid numbers (got '$v' vs '$resolvedExpected').",
                                errorCode = AutomationErrorCode.VARIABLE_INVALID
                            )
                        }
                        if (pred.operator == ConditionOperator.VARIABLE_GREATER_THAN) left > right else left < right
                    }
                    else -> false
                }
                detail = "${pred.variableName} evaluated $rawMatched"
            }
            else -> {
                if (webView == null) {
                    return ConditionEvalOutcome(
                        matched = false,
                        detail = "WebView unavailable while evaluating DOM condition '${pred.operator.label}'.",
                        errorCode = AutomationErrorCode.WEBVIEW_UNAVAILABLE
                    )
                }
                val probeJs = Phase6Scripts.buildConditionProbeScript(
                    operator = pred.operator,
                    selector = resolvedSelector,
                    targetLabel = resolvedLabel,
                    expectedValue = resolvedExpected
                )
                val evalRes = engine.evalJsWithResultInternal(webView, probeJs)
                when (evalRes) {
                    is JsEvalResult.Timeout -> return ConditionEvalOutcome(false, "Condition DOM probe timed out.", AutomationErrorCode.JS_TIMEOUT)
                    is JsEvalResult.Unavailable -> return ConditionEvalOutcome(false, evalRes.reason, AutomationErrorCode.WEBVIEW_UNAVAILABLE)
                    is JsEvalResult.Failure -> return ConditionEvalOutcome(false, evalRes.reason, AutomationErrorCode.JS_EXCEPTION)
                    is JsEvalResult.Success -> {
                        val obj = engine.parseEvalJsonObject(evalRes.rawValue)
                            ?: return ConditionEvalOutcome(false, "Malformed condition DOM response.", AutomationErrorCode.JS_EXCEPTION)
                        if (!obj.optBoolean("ok", false)) {
                            val code = AutomationErrorCode.entries.firstOrNull { it.name == obj.optString("code", "") }
                                ?: AutomationErrorCode.CONDITION_INVALID
                            return ConditionEvalOutcome(false, obj.optString("msg", "Condition evaluation failed"), code)
                        }
                        rawMatched = obj.optBoolean("matched", false)
                        detail = obj.optString("detail", "")
                    }
                }
            }
        }
        val finalMatched = if (pred.negate) !rawMatched else rawMatched
        return ConditionEvalOutcome(finalMatched, detail)
    }

    private fun matchesUrlPattern(url: String, pattern: String): Boolean {
        val cleanPattern = pattern.trim()
        if (cleanPattern.isEmpty()) return false
        if (cleanPattern.contains("*")) {
            val regexStr = cleanPattern.split("*").joinToString(".*") { Regex.escape(it) }
            return Regex("^.*$regexStr.*$", RegexOption.IGNORE_CASE).matches(url)
        }
        return url.contains(cleanPattern, ignoreCase = true)
    }

    private suspend fun executeStandardStepWithPolicy(
        step: AutomationStep,
        policy: RecoveryPolicy,
        state: ExecutionContextState,
        nextStep: AutomationStep?,
        tabId: String,
        navigationSynchronizer: TabNavigationSynchronizer?,
        webViewProvider: () -> WebView?,
        onNavigateUrl: (String) -> Unit,
        credentialStepExecutor: (suspend (AutomationStep, String) -> StepExecutionResult)?,
        lifecycleController: ExecutionLifecycleController?,
        isTabOpenProvider: () -> Boolean,
        liveUrlProvider: (() -> String)?,
        confirmedUnsafeRetry: Boolean,
        altExecutor: suspend (List<AutomationStep>) -> StepExecutionResult?,
        vaultValueResolver: (suspend (AutomationStep) -> Result<String>)? = null,
        clearBrowserDataExecutor: (suspend () -> StepExecutionResult)? = null
    ): Pair<StepExecutionResult, String> {
        val initial = executeSingleAttempt(
            step = step,
            activeUrl = state.activeUrl,
            expectedOrigin = state.expectedOrigin,
            tabId = tabId,
            navigationSynchronizer = navigationSynchronizer,
            nextStep = nextStep,
            webViewProvider = webViewProvider,
            onNavigateUrl = onNavigateUrl,
            credentialStepExecutor = credentialStepExecutor,
            lifecycleController = lifecycleController,
            isTabOpenProvider = isTabOpenProvider,
            liveUrlProvider = liveUrlProvider,
            vaultValueResolver = vaultValueResolver,
            clearBrowserDataExecutor = clearBrowserDataExecutor
        )
        if (initial.success) return initial to ""
        return applyRecoveryPolicy(
            step = step,
            initialFailure = initial,
            policy = policy,
            state = state,
            tabId = tabId,
            navigationSynchronizer = navigationSynchronizer,
            webViewProvider = webViewProvider,
            lifecycleController = lifecycleController,
            isTabOpenProvider = isTabOpenProvider,
            liveUrlProvider = liveUrlProvider,
            confirmedUnsafeRetry = confirmedUnsafeRetry,
            attemptRunner = { candidateStep ->
                executeSingleAttempt(
                    step = candidateStep,
                    activeUrl = state.activeUrl,
                    expectedOrigin = state.expectedOrigin,
                    tabId = tabId,
                    navigationSynchronizer = navigationSynchronizer,
                    nextStep = nextStep,
                    webViewProvider = webViewProvider,
                    onNavigateUrl = onNavigateUrl,
                    credentialStepExecutor = credentialStepExecutor,
                    lifecycleController = lifecycleController,
                    isTabOpenProvider = isTabOpenProvider,
                    liveUrlProvider = liveUrlProvider,
                    vaultValueResolver = vaultValueResolver,
                    clearBrowserDataExecutor = clearBrowserDataExecutor
                )
            },
            altExecutor = altExecutor
        )
    }

    private suspend fun applyRecoveryPolicy(
        step: AutomationStep,
        initialFailure: StepExecutionResult,
        policy: RecoveryPolicy,
        state: ExecutionContextState,
        tabId: String,
        navigationSynchronizer: TabNavigationSynchronizer?,
        webViewProvider: () -> WebView?,
        lifecycleController: ExecutionLifecycleController?,
        isTabOpenProvider: () -> Boolean,
        liveUrlProvider: (() -> String)?,
        confirmedUnsafeRetry: Boolean,
        attemptRunner: suspend (AutomationStep) -> StepExecutionResult,
        altExecutor: suspend (List<AutomationStep>) -> StepExecutionResult?
    ): Pair<StepExecutionResult, String> {
        // Hard fail-closed codes never recover automatically
        if (initialFailure.errorCode in setOf(
                AutomationErrorCode.CANCELLED,
                AutomationErrorCode.TAB_CLOSED,
                AutomationErrorCode.WEBVIEW_REPLACED,
                AutomationErrorCode.UNEXPECTED_ORIGIN_CHANGE,
                AutomationErrorCode.SECURITY_CHALLENGE_BLOCKED,
                AutomationErrorCode.EXTRACTION_SENSITIVE_BLOCKED,
                AutomationErrorCode.ELEMENT_AMBIGUOUS,
                AutomationErrorCode.VAULT_VALUES_EXHAUSTED
            )
        ) {
            return initialFailure to ""
        }

        if (policy.strategy == RecoveryStrategy.STOP_SAFELY ||
            policy.maxRetries <= 0 ||
            initialFailure.errorCode !in policy.retryOnCodes
        ) {
            return initialFailure to ""
        }

        val isUnsafeAction = isSensitiveOrDestructiveForRecovery(step)
        if (isUnsafeAction && policy.requireConfirmForUnsafeRetry && !confirmedUnsafeRetry) {
            state.recoveryAttempts.add(
                RecoveryAttemptRecord(
                    stepId = step.id,
                    attemptNumber = 1,
                    triggerErrorCode = initialFailure.errorCode,
                    strategy = policy.strategy,
                    succeeded = false,
                    safeMessage = "Auto-recovery blocked for sensitive/destructive step '${step.description}' without explicit confirmation.",
                    originalFailureMessage = initialFailure.message
                )
            )
            return StepExecutionResult(
                success = false,
                message = "Unsafe auto-recovery blocked for '${step.description}' (original failure: ${initialFailure.message}).",
                errorCode = AutomationErrorCode.UNSAFE_RECOVERY_BLOCKED
            ) to ""
        }

        if (policy.strategy == RecoveryStrategy.ALTERNATIVE_BRANCH && policy.alternativeSteps.isNotEmpty()) {
            val altFailure = altExecutor(policy.alternativeSteps)
            val altOk = altFailure == null
            state.recoveryAttempts.add(
                RecoveryAttemptRecord(
                    stepId = step.id,
                    attemptNumber = 1,
                    triggerErrorCode = initialFailure.errorCode,
                    strategy = RecoveryStrategy.ALTERNATIVE_BRANCH,
                    succeeded = altOk,
                    safeMessage = if (altOk) {
                        "Fallback branch recovered '${step.description}' (${policy.alternativeSteps.size} step(s))"
                    } else {
                        "Fallback branch failed: ${altFailure!!.message}"
                    },
                    originalFailureMessage = initialFailure.message
                )
            )
            return if (altOk) {
                StepExecutionResult(
                    success = true,
                    message = "Recovered '${step.description}' via fallback branch",
                    recoveredViaFallback = true,
                    verified = true
                ) to "Recovered '${step.description}' via fallback branch"
            } else {
                val nonMaskingCodes = setOf(
                    AutomationErrorCode.ELEMENT_AMBIGUOUS,
                    AutomationErrorCode.CANCELLED,
                    AutomationErrorCode.TAB_CLOSED,
                    AutomationErrorCode.WEBVIEW_REPLACED,
                    AutomationErrorCode.UNEXPECTED_ORIGIN_CHANGE,
                    AutomationErrorCode.SECURITY_CHALLENGE_BLOCKED,
                    AutomationErrorCode.EXTRACTION_SENSITIVE_BLOCKED,
                    AutomationErrorCode.VAULT_VALUES_EXHAUSTED
                )
                val effectiveCode = if (altFailure!!.errorCode in nonMaskingCodes) {
                    altFailure.errorCode
                } else {
                    initialFailure.errorCode
                }
                StepExecutionResult(
                    success = false,
                    message = "Original failure: ${initialFailure.message}; Fallback branch failed: ${altFailure.message}",
                    errorCode = effectiveCode,
                    ambiguousCandidates = altFailure.ambiguousCandidates,
                    locatorDiagnostics = altFailure.locatorDiagnostics ?: initialFailure.locatorDiagnostics
                ) to ""
            }
        }

        val maxAttempts = policy.maxRetries.coerceIn(1, Phase6Limits.MAX_RECOVERY_RETRIES)
        var currentStep = step
        for (attempt in 1..maxAttempts) {
            coroutineContext.ensureActive()
            if (policy.strategy == RecoveryStrategy.WAIT_THEN_RETRY) {
                val waitSpec = policy.waitBeforeRetry ?: WaitConditionSpec(
                    type = if (currentStep.selector.isNotBlank() || currentStep.targetLabel.isNotBlank()) {
                        WaitConditionType.ELEMENT_INTERACTABLE
                    } else {
                        WaitConditionType.CONTENT_STABILIZED
                    },
                    selector = currentStep.selector,
                    targetLabel = currentStep.targetLabel,
                    timeoutMs = 2_000L
                )
                engine.awaitWaitCondition(
                    condition = waitSpec,
                    tabId = tabId,
                    baselineUrl = state.activeUrl,
                    baselineOrigin = state.expectedOrigin,
                    navigationSynchronizer = navigationSynchronizer,
                    webViewProvider = webViewProvider,
                    lifecycleController = lifecycleController,
                    isTabOpenProvider = isTabOpenProvider,
                    liveUrlProvider = liveUrlProvider
                )
            }

            if (policy.rescanBeforeRetry || policy.strategy == RecoveryStrategy.RESCAN_AND_RETRY) {
                val wv = webViewProvider()
                if (wv != null) {
                    val epoch = if (tabId.isNotEmpty() && navigationSynchronizer != null) {
                        navigationSynchronizer.snapshot(tabId).epoch
                    } else {
                        0L
                    }
                    val fresh = engine.universalScanner.scanPage(
                        webView = wv,
                        tabId = tabId,
                        currentUrl = state.activeUrl,
                        navigationEpoch = epoch,
                        installDynamicObserver = false
                    )
                    if (fresh.elements.isNotEmpty()) {
                        val match = engine.universalScanner.matchCandidatesForInstruction(
                            clause = currentStep.description,
                            actionType = currentStep.actionType,
                            targetLabel = currentStep.targetLabel.ifBlank { currentStep.description },
                            inspectionState = fresh
                        )
                        val cand = match.primaryCandidate
                        if (!match.isAmbiguous && cand != null && !cand.isLocatorAmbiguous && cand.selector.isNotBlank()) {
                            currentStep = currentStep.copy(
                                selector = cand.selector,
                                locatorCandidates = cand.locatorCandidates,
                                framePath = cand.framePath.ifEmpty { currentStep.framePath },
                                shadowHostPath = cand.shadowHostPath.ifEmpty { currentStep.shadowHostPath }
                            )
                        }
                    }
                }
            }

            val retryRes = attemptRunner(currentStep)
            val msg = if (retryRes.success) {
                "Retry #$attempt succeeded (${retryRes.message})"
            } else {
                "Retry #$attempt failed (${retryRes.message})"
            }
            state.recoveryAttempts.add(
                RecoveryAttemptRecord(
                    stepId = step.id,
                    attemptNumber = attempt,
                    triggerErrorCode = initialFailure.errorCode,
                    strategy = policy.strategy,
                    succeeded = retryRes.success,
                    safeMessage = msg,
                    originalFailureMessage = initialFailure.message
                )
            )
            if (retryRes.success) {
                return retryRes.copy(
                    recoveredViaFallback = true,
                    resolvedSelector = retryRes.resolvedSelector.ifBlank { currentStep.selector }
                ) to "Recovered '${step.targetLabel.ifBlank { step.description }}' after retry #$attempt"
            }
        }

        // Preserve original failure context when bounded retries exhaust
        return initialFailure.copy(
            message = "Recovery exhausted after $maxAttempts attempt(s). Original failure: ${initialFailure.message}"
        ) to ""
    }

    private fun isSensitiveOrDestructiveForRecovery(step: AutomationStep): Boolean {
        if (step.isCredentialStep || step.isDestructive || step.actionType == AutomationActionType.SUBMIT) return true
        val desc = "${step.targetLabel} ${step.description} ${step.selector}".lowercase()
        return Regex("""\b(pay|payment|checkout|purchase|transfer|wire|delete|remove|submit|password|credential)\b""")
            .containsMatchIn(desc)
    }

    private suspend fun executeSingleAttempt(
        step: AutomationStep,
        activeUrl: String,
        expectedOrigin: String,
        tabId: String,
        navigationSynchronizer: TabNavigationSynchronizer?,
        nextStep: AutomationStep?,
        webViewProvider: () -> WebView?,
        onNavigateUrl: (String) -> Unit,
        credentialStepExecutor: (suspend (AutomationStep, String) -> StepExecutionResult)?,
        lifecycleController: ExecutionLifecycleController?,
        isTabOpenProvider: () -> Boolean,
        liveUrlProvider: (() -> String)?,
        vaultValueResolver: (suspend (AutomationStep) -> Result<String>)? = null,
        clearBrowserDataExecutor: (suspend () -> StepExecutionResult)? = null
    ): StepExecutionResult {
        return if (step.actionType == AutomationActionType.WAIT) {
            val waitSpec = step.waitCondition ?: WaitConditionSpec(
                type = if (step.selector.isNotBlank() || step.targetLabel.isNotBlank()) {
                    WaitConditionType.ELEMENT_APPEARS
                } else {
                    WaitConditionType.CONTENT_STABILIZED
                },
                selector = step.selector,
                targetLabel = step.targetLabel,
                expectedText = step.value,
                timeoutMs = if (step.stepTimeoutMs > 0L) step.stepTimeoutMs else AutomationEngine.DEFAULT_JS_EVAL_TIMEOUT_MS
            )
            engine.awaitWaitCondition(
                condition = waitSpec,
                tabId = tabId,
                baselineUrl = activeUrl,
                baselineOrigin = expectedOrigin,
                navigationSynchronizer = navigationSynchronizer,
                webViewProvider = webViewProvider,
                lifecycleController = lifecycleController,
                isTabOpenProvider = isTabOpenProvider,
                liveUrlProvider = liveUrlProvider
            )
        } else {
            engine.executeSingleStep(
                step = step,
                activeUrl = activeUrl,
                tabId = tabId,
                navigationSynchronizer = navigationSynchronizer,
                nextStep = nextStep,
                webViewProvider = webViewProvider,
                onNavigateUrl = onNavigateUrl,
                credentialStepExecutor = credentialStepExecutor,
                vaultValueResolver = vaultValueResolver,
                clearBrowserDataExecutor = clearBrowserDataExecutor
            )
        }
    }

    private suspend fun executeLegacySingleStep(
        step: AutomationStep,
        activeUrl: String,
        expectedOrigin: String,
        tabId: String,
        navigationSynchronizer: TabNavigationSynchronizer?,
        nextStep: AutomationStep?,
        webViewProvider: () -> WebView?,
        onNavigateUrl: (String) -> Unit,
        credentialStepExecutor: (suspend (AutomationStep, String) -> StepExecutionResult)?,
        lifecycleController: ExecutionLifecycleController?,
        isTabOpenProvider: () -> Boolean,
        liveUrlProvider: (() -> String)?,
        vaultValueResolver: (suspend (AutomationStep) -> Result<String>)? = null,
        clearBrowserDataExecutor: (suspend () -> StepExecutionResult)? = null
    ): StepExecutionResult {
        return if (step.actionType == AutomationActionType.WAIT) {
            executeSingleAttempt(
                step = step,
                activeUrl = activeUrl,
                expectedOrigin = expectedOrigin,
                tabId = tabId,
                navigationSynchronizer = navigationSynchronizer,
                nextStep = nextStep,
                webViewProvider = webViewProvider,
                onNavigateUrl = onNavigateUrl,
                credentialStepExecutor = credentialStepExecutor,
                lifecycleController = lifecycleController,
                isTabOpenProvider = isTabOpenProvider,
                liveUrlProvider = liveUrlProvider,
                vaultValueResolver = vaultValueResolver,
                clearBrowserDataExecutor = clearBrowserDataExecutor
            )
        } else {
            engine.executeSingleStepWithRecovery(
                step = step,
                activeUrl = activeUrl,
                tabId = tabId,
                navigationSynchronizer = navigationSynchronizer,
                nextStep = nextStep,
                webViewProvider = webViewProvider,
                onNavigateUrl = onNavigateUrl,
                credentialStepExecutor = credentialStepExecutor,
                vaultValueResolver = vaultValueResolver,
                clearBrowserDataExecutor = clearBrowserDataExecutor
            )
        }
    }

    private suspend fun navigateWithContinuation(
        dest: String,
        tabId: String,
        navigationSynchronizer: TabNavigationSynchronizer?,
        webViewProvider: () -> WebView?,
        nextStep: AutomationStep?,
        onNavigateUrl: (String) -> Unit
    ): StepExecutionResult {
        return engine.navigateAndAwaitSettlement(
            dest = dest,
            tabId = tabId,
            navigationSynchronizer = navigationSynchronizer,
            webViewProvider = webViewProvider,
            nextStep = nextStep,
            onNavigateUrl = onNavigateUrl
        )
    }

    companion object {
        fun resolveWorkflowStartUrl(
            workflow: AutomationWorkflowEntity,
            steps: List<AutomationStep>
        ): String {
            val trimmedSource = workflow.sourceUrl.trim()
            if (trimmedSource.isNotBlank() &&
                !trimmedSource.equals("apex://home", ignoreCase = true) &&
                !trimmedSource.startsWith("apex://", ignoreCase = true)
            ) {
                return trimmedSource
            }
            val ordered = steps.sortedBy { it.order }
            if (ordered.firstOrNull()?.actionType == AutomationActionType.NAVIGATE) {
                return ""
            }
            val domain = workflow.domain.trim()
            if (domain.isNotBlank() &&
                !domain.equals("Start Page", ignoreCase = true) &&
                !domain.equals("All websites", ignoreCase = true) &&
                domain.contains('.')
            ) {
                return if (domain.startsWith("http://", ignoreCase = true) ||
                    domain.startsWith("https://", ignoreCase = true)
                ) {
                    domain
                } else {
                    "https://$domain"
                }
            }
            return ""
        }

        fun urlsMatchForWorkflowStart(activeUrl: String, targetUrl: String): Boolean {
            val a = activeUrl.trim()
            val b = targetUrl.trim()
            if (a.isBlank() || b.isBlank()) return false
            if (a.equals("apex://home", ignoreCase = true)) return false
            if (a.equals(b, ignoreCase = true)) return true
            val uriA = runCatching { Uri.parse(a) }.getOrNull() ?: return false
            val uriB = runCatching { Uri.parse(b) }.getOrNull() ?: return false
            val schemeA = uriA.scheme?.lowercase().orEmpty()
            val schemeB = uriB.scheme?.lowercase().orEmpty()
            val hostA = uriA.host?.lowercase().orEmpty()
            val hostB = uriB.host?.lowercase().orEmpty()
            if (schemeA.isBlank() || schemeB.isBlank() || hostA.isBlank() || hostB.isBlank()) return false
            if (schemeA != schemeB || hostA != hostB) return false
            val portA = normalizedPortForScheme(schemeA, uriA.port)
            val portB = normalizedPortForScheme(schemeB, uriB.port)
            if (portA != portB) return false
            val pathA = (uriA.encodedPath ?: "/").trimEnd('/').ifEmpty { "/" }
            val pathB = (uriB.encodedPath ?: "/").trimEnd('/').ifEmpty { "/" }
            val queryA = uriA.encodedQuery.orEmpty()
            val queryB = uriB.encodedQuery.orEmpty()
            val fragmentA = uriA.encodedFragment.orEmpty()
            val fragmentB = uriB.encodedFragment.orEmpty()
            return pathA == pathB && queryA == queryB && fragmentA == fragmentB
        }

        private fun normalizedPortForScheme(scheme: String, port: Int): Int {
            return when {
                port > 0 -> port
                scheme == "https" -> 443
                scheme == "http" -> 80
                else -> -1
            }
        }

        fun serializeExecutionTrace(
            branchTraces: List<BranchTraceRecord>,
            recoveryAttempts: List<RecoveryAttemptRecord>
        ): String {
            if (branchTraces.isEmpty() && recoveryAttempts.isEmpty()) return ""
            return JSONObject().apply {
                val btArr = JSONArray()
                branchTraces.forEach { btArr.put(it.toJson()) }
                put("branchTraces", btArr)
                val raArr = JSONArray()
                recoveryAttempts.forEach { raArr.put(it.toJson()) }
                put("recoveryAttempts", raArr)
            }.toString()
        }

        fun parseExecutionTrace(raw: String?): Pair<List<BranchTraceRecord>, List<RecoveryAttemptRecord>> {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) return emptyList<BranchTraceRecord>() to emptyList()
            return runCatching {
                val obj = JSONObject(trimmed)
                val btArr = obj.optJSONArray("branchTraces") ?: JSONArray()
                val traces = buildList {
                    for (i in 0 until btArr.length()) {
                        BranchTraceRecord.fromJson(btArr.optJSONObject(i))?.let { add(it) }
                    }
                }
                val raArr = obj.optJSONArray("recoveryAttempts") ?: JSONArray()
                val attempts = buildList {
                    for (i in 0 until raArr.length()) {
                        RecoveryAttemptRecord.fromJson(raArr.optJSONObject(i))?.let { add(it) }
                    }
                }
                traces to attempts
            }.getOrDefault(emptyList<BranchTraceRecord>() to emptyList())
        }
    }
}
