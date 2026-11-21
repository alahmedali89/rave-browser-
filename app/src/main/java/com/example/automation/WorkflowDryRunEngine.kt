package com.example.automation

import com.example.data.local.AutomationWorkflowEntity
import com.example.data.local.CredentialVaultEntity

/**
 * Deterministic, read-only Workflow Dry-Run Simulator.
 * Executes ZERO JavaScript, performs ZERO WebView navigation or DOM clicks/inputs,
 * and performs ZERO Keystore/credential decryption.
 * Evaluates validation, sub-workflow dependencies, non-DOM conditions, variable substitutions,
 * origin transitions, credential requirements, and destructive actions safely.
 */
object WorkflowDryRunEngine {

    suspend fun simulate(
        workflow: AutomationWorkflowEntity,
        document: WorkflowDocument,
        workflowLoader: suspend (Long) -> Pair<AutomationWorkflowEntity, WorkflowDocument>?,
        credentialLookup: (suspend (String) -> CredentialVaultEntity?)? = null,
        runtimeInputs: Map<String, String> = emptyMap(),
        currentPageUrl: String = workflow.sourceUrl,
        passiveDomSnapshot: List<DetectedPageElement> = emptyList(),
        engine: AutomationEngine = AutomationEngine()
    ): WorkflowDryRunReport {
        val validationReport = WorkflowValidationEngine.validateWorkflow(
            workflow = workflow,
            document = document,
            workflowLookup = { id -> workflowLoader(id)?.let { (ent, doc) -> ent.name to doc } },
            credentialLookup = credentialLookup,
            preSeededVariableNames = runtimeInputs.keys,
            engine = engine
        )

        val graph = WorkflowDependencyResolver.resolveDependencyGraph(
            rootWorkflowId = workflow.id,
            rootSteps = document.steps,
            workflowLoader = workflowLoader
        )
        val resolvedSubWorkflowOrder = graph.topologicalOrderIds
            .filter { it != workflow.id }
            .map { id -> graph.resolvedNamesById[id] ?: "Workflow #$id" }
        val missingDeps = graph.missingWorkflowIds.map { id -> "Workflow #$id" }

        val variableStore = WorkflowVariableStore(initialDefinitions = document.metadata.variables)
        val unresolvedVars = linkedSetOf<String>()
        val authNeeded = mutableListOf<String>()
        val rebindNeeded = mutableListOf<String>()
        val originTransitions = mutableListOf<String>()
        val destructiveActions = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val plannedSteps = mutableListOf<DryRunPlannedStep>()

        validationReport.issues.filter { it.severity == ValidationSeverity.WARNING }.forEach {
            warnings.add("${it.title}: ${it.explanation}")
        }

        // Seed user-supplied dry-run runtime inputs
        for ((k, rawVal) in runtimeInputs) {
            val cleanName = k.trim()
            if (cleanName.isNotEmpty()) {
                val res = variableStore.setVariable(cleanName, null, rawVal)
                if (res.isFailure) {
                    warnings.add(res.exceptionOrNull()?.message ?: "Invalid input for variable '$cleanName'.")
                }
            }
        }

        var simulatedUrl = currentPageUrl.trim().ifBlank { workflow.sourceUrl.trim() }
        var simulatedOrigin = simulatedUrl
            .takeIf { it.isNotBlank() && !it.equals("apex://home", ignoreCase = true) }
            ?.let { engine.parseOrigin(it).canonicalOrigin }
            .orEmpty()
        var lastSimulatedSucceeded = true

        suspend fun simulateSteps(
            steps: List<AutomationStep>,
            store: WorkflowVariableStore,
            depth: Int,
            callStack: List<Long>
        ) {
            for (step in steps) {
                if (plannedSteps.size >= Phase6Limits.MAX_TOTAL_STEP_EXECUTIONS_PER_RUN) {
                    warnings.add("Dry-run preview truncated at ${Phase6Limits.MAX_TOTAL_STEP_EXECUTIONS_PER_RUN} planned steps.")
                    return
                }

                // Resolve ${var} templates without mutating external state
                val substitutions = linkedMapOf<String, String>()
                val rawTemplates = listOf(step.selector, step.value, step.targetLabel)
                for (field in rawTemplates) {
                    for (varName in WorkflowVariableStore.extractReferencedVariableNames(field)) {
                        if (!WorkflowVariableStore.isValidVariableName(varName) ||
                            WorkflowVariableStore.isBlockedCredentialVariableName(varName)
                        ) {
                            unresolvedVars.add(varName)
                        } else {
                            val stored = store.getVariable(varName)
                            if (stored == null) {
                                unresolvedVars.add(varName)
                            } else {
                                substitutions[varName] = if (WorkflowVariableStore.shouldMaskInUi(varName)) {
                                    "••••"
                                } else {
                                    stored.rawValue
                                }
                            }
                        }
                    }
                }

                val notes = mutableListOf<String>()
                var domNote = ""
                var plannedStatus = DryRunStepStatus.PLANNED
                var condEvalResult: Boolean? = null

                if (step.isDestructive || step.actionType == AutomationActionType.SUBMIT) {
                    val label = "Step ${step.order}: ${step.description.ifBlank { step.actionType.label }}"
                    destructiveActions.add(label)
                    notes.add("Requires runtime user confirmation before execution.")
                    plannedStatus = DryRunStepStatus.WARNING
                }

                val needsDomCheck = step.actionType in setOf(
                    AutomationActionType.CLICK,
                    AutomationActionType.INPUT,
                    AutomationActionType.CLEAR,
                    AutomationActionType.SELECT,
                    AutomationActionType.TOGGLE,
                    AutomationActionType.FOCUS,
                    AutomationActionType.BLUR,
                    AutomationActionType.SUBMIT,
                    AutomationActionType.FILL_CREDENTIAL,
                    AutomationActionType.EXTRACT
                )

                if (needsDomCheck && step.actionType != AutomationActionType.FILL_CREDENTIAL) {
                    if (passiveDomSnapshot.isEmpty()) {
                        domNote = "DOM target is UNVERIFIED in dry-run (no live WebView inspection executed)"
                        if (plannedStatus == DryRunStepStatus.PLANNED) {
                            plannedStatus = DryRunStepStatus.UNVERIFIED_DOM
                        }
                    } else {
                        val matched = passiveDomSnapshot.any { el ->
                            (step.selector.isNotBlank() && el.selector == step.selector) ||
                                (step.targetLabel.isNotBlank() && el.accessibleName.equals(step.targetLabel, ignoreCase = true))
                        }
                        domNote = if (matched) {
                            "Matched in passive DOM snapshot (read-only; no action executed)"
                        } else {
                            if (plannedStatus == DryRunStepStatus.PLANNED) {
                                plannedStatus = DryRunStepStatus.UNVERIFIED_DOM
                            }
                            "Not present in current passive DOM snapshot; target UNVERIFIED in dry-run"
                        }
                    }
                }

                // Origin tracking for NAVIGATE and expectedOrigin checks
                var originNote = ""
                if (step.actionType == AutomationActionType.NAVIGATE) {
                    val resolvedDest = when (val r = store.resolveTemplate(step.value.ifBlank { step.targetLabel }, strictUndefined = false)) {
                        is VariableResolutionResult.Success -> r.value.trim()
                        else -> step.value.ifBlank { step.targetLabel }.trim()
                    }
                    val parsedDest = engine.parseOrigin(resolvedDest)
                    if (!parsedDest.isValid && !resolvedDest.contains("\${")) {
                        plannedStatus = DryRunStepStatus.BLOCKED
                        notes.add("Invalid or non-HTTP(S) destination '$resolvedDest'.")
                    } else if (parsedDest.isValid) {
                        if (simulatedOrigin.isNotBlank() && parsedDest.canonicalOrigin != simulatedOrigin) {
                            val transition = "${simulatedOrigin} → ${parsedDest.canonicalOrigin}"
                            originTransitions.add(transition)
                            originNote = "Origin transition: $transition"
                        } else {
                            originNote = "Navigates within ${parsedDest.canonicalOrigin}"
                        }
                        simulatedUrl = resolvedDest
                        simulatedOrigin = parsedDest.canonicalOrigin
                    }
                }

                // Credential checks without accessing Vault crypto or decrypting anything
                if (step.isCredentialStep) {
                    val refId = step.credentialReferenceId?.trim().orEmpty()
                    if (refId.isEmpty()) {
                        plannedStatus = DryRunStepStatus.REQUIRES_CREDENTIAL_REBIND
                        rebindNeeded.add("Step ${step.order}: ${step.description}")
                        notes.add("No Vault credential assigned; must rebind before live execution.")
                    } else {
                        val vaultEntry = credentialLookup?.invoke(refId)
                        if (credentialLookup != null && vaultEntry == null) {
                            plannedStatus = DryRunStepStatus.REQUIRES_CREDENTIAL_REBIND
                            rebindNeeded.add("Step ${step.order}: ${step.description} (Vault entry missing)")
                            notes.add("Bound Vault credential was deleted; rebind required.")
                        } else if (vaultEntry != null && simulatedOrigin.isNotBlank() && vaultEntry.websiteOrigin != simulatedOrigin) {
                            plannedStatus = DryRunStepStatus.BLOCKED
                            notes.add("Origin mismatch: Vault entry bound to '${vaultEntry.websiteOrigin}', workflow origin is '$simulatedOrigin'.")
                        } else {
                            plannedStatus = DryRunStepStatus.REQUIRES_CREDENTIAL_AUTH
                            authNeeded.add("Step ${step.order}: ${vaultEntry?.label ?: step.description}")
                            notes.add("Vault credential bound; explicit authorization required at runtime (never decrypted in dry-run).")
                        }
                    }
                }

                when (step.actionType) {
                    AutomationActionType.UNSUPPORTED -> {
                        plannedStatus = DryRunStepStatus.BLOCKED
                        notes.add("Unsupported action '${step.rawActionType.ifBlank { "UNKNOWN" }}' cannot run.")
                        lastSimulatedSucceeded = false
                    }
                    AutomationActionType.SET_VARIABLE -> {
                        val varName = step.variableName.trim().ifBlank { step.targetLabel.trim() }
                        val resolvedVal = when (val res = store.resolveTemplate(step.value, strictUndefined = true)) {
                            is VariableResolutionResult.Success -> res.value
                            is VariableResolutionResult.Undefined -> {
                                unresolvedVars.add(res.variableName)
                                plannedStatus = DryRunStepStatus.BLOCKED
                                notes.add(res.message)
                                step.value
                            }
                            is VariableResolutionResult.Invalid -> {
                                unresolvedVars.add(res.variableName)
                                plannedStatus = DryRunStepStatus.BLOCKED
                                notes.add(res.message)
                                step.value
                            }
                        }
                        if (plannedStatus != DryRunStepStatus.BLOCKED) {
                            val setRes = store.setVariable(varName, step.variableType, resolvedVal)
                            if (setRes.isFailure) {
                                plannedStatus = DryRunStepStatus.BLOCKED
                                notes.add(setRes.exceptionOrNull()?.message ?: "Invalid variable assignment.")
                            } else {
                                notes.add("Simulated \${$varName} = '${if (WorkflowVariableStore.shouldMaskInUi(varName)) "••••" else resolvedVal}'")
                            }
                        }
                    }
                    AutomationActionType.EXTRACT -> {
                        val outVar = step.extractionSpec?.outputVariableName?.trim()
                            ?.ifBlank { step.variableName.trim() }
                            .orEmpty()
                        if (outVar.isNotEmpty() && WorkflowVariableStore.isValidVariableName(outVar) &&
                            !WorkflowVariableStore.isBlockedCredentialVariableName(outVar)
                        ) {
                            val placeholder = when (step.extractionSpec?.mode) {
                                ExtractionMode.PAGE_URL -> simulatedUrl
                                ExtractionMode.PAGE_TITLE -> "(Dry-run page title)"
                                else -> "(Dry-run extracted value)"
                            }
                            store.setVariable(outVar, WorkflowVariableType.TEXT, placeholder)
                            notes.add("Simulated extraction into \${$outVar} (no DOM read executed).")
                        }
                    }
                    AutomationActionType.CONDITION -> {
                        val eval = evaluateDryRunCondition(
                            node = step.condition,
                            store = store,
                            currentUrl = simulatedUrl,
                            currentOrigin = simulatedOrigin,
                            previousSucceeded = lastSimulatedSucceeded,
                            passiveDomSnapshot = passiveDomSnapshot
                        )
                        condEvalResult = eval.result
                        if (eval.isDomUnverified) {
                            domNote = "Condition depends on live DOM and is UNVERIFIED in dry-run"
                            plannedStatus = DryRunStepStatus.UNVERIFIED_DOM
                        } else if (eval.result != null) {
                            plannedStatus = if (eval.result) DryRunStepStatus.EVALUATED_TRUE else DryRunStepStatus.EVALUATED_FALSE
                            notes.add(eval.explanation)
                        }
                    }
                    else -> Unit
                }

                plannedSteps.add(
                    DryRunPlannedStep(
                        stepId = step.id,
                        stepOrder = step.order,
                        depth = depth,
                        actionType = step.actionType,
                        actionLabel = step.rawActionType.ifBlank { step.actionType.label },
                        description = step.description.ifBlank { step.actionType.label },
                        status = plannedStatus,
                        targetSummary = step.selector.ifBlank { step.targetLabel }.ifBlank { step.value.take(40) },
                        originNote = originNote,
                        variableSubstitutions = substitutions,
                        resolvedConditionResult = condEvalResult,
                        domVerificationNote = domNote,
                        notes = notes
                    )
                )

                // Traverse child branches deterministically according to dry-run rules
                when (step.actionType) {
                    AutomationActionType.CONDITION -> {
                        when (condEvalResult) {
                            true -> simulateSteps(step.thenSteps, store, depth + 1, callStack)
                            false -> {
                                var matchedElif = false
                                for (elif in step.elseIfBranches) {
                                    val elifEval = evaluateDryRunCondition(
                                        node = elif.condition,
                                        store = store,
                                        currentUrl = simulatedUrl,
                                        currentOrigin = simulatedOrigin,
                                        previousSucceeded = lastSimulatedSucceeded,
                                        passiveDomSnapshot = passiveDomSnapshot
                                    )
                                    if (elifEval.result == true) {
                                        matchedElif = true
                                        simulateSteps(elif.steps, store, depth + 1, callStack)
                                        break
                                    }
                                }
                                if (!matchedElif) {
                                    simulateSteps(step.elseSteps, store, depth + 1, callStack)
                                }
                            }
                            null -> {
                                // DOM condition is unverified: preview 'then' once so user can inspect planned actions
                                simulateSteps(step.thenSteps, store, depth + 1, callStack)
                            }
                        }
                    }
                    AutomationActionType.LOOP -> {
                        val cfg = step.loopConfig
                        if (cfg != null) {
                            val previewIterations = cfg.maxIterations.coerceIn(1, Phase6Limits.MAX_LOOP_ITERATIONS)
                            for (i in 1..previewIterations) {
                                if (plannedSteps.size >= Phase6Limits.MAX_TOTAL_STEP_EXECUTIONS_PER_RUN) break
                                if (cfg.iterationVariable.isNotBlank() &&
                                    WorkflowVariableStore.isValidVariableName(cfg.iterationVariable)
                                ) {
                                    store.setVariable(cfg.iterationVariable, WorkflowVariableType.NUMBER, i.toString())
                                }
                                if (cfg.continueCondition != null) {
                                    val contEval = evaluateDryRunCondition(
                                        node = cfg.continueCondition,
                                        store = store,
                                        currentUrl = simulatedUrl,
                                        currentOrigin = simulatedOrigin,
                                        previousSucceeded = lastSimulatedSucceeded,
                                        passiveDomSnapshot = passiveDomSnapshot
                                    )
                                    if (contEval.result == false) break
                                }
                                simulateSteps(step.thenSteps, store, depth + 1, callStack)
                            }
                        }
                    }
                    AutomationActionType.CALL_WORKFLOW -> {
                        val ref = step.subWorkflowRef
                        if (ref != null && ref.targetWorkflowId > 0L &&
                            ref.targetWorkflowId !in callStack &&
                            depth < Phase6Limits.MAX_SUBWORKFLOW_DEPTH
                        ) {
                            val loaded = workflowLoader(ref.targetWorkflowId)
                            if (loaded != null) {
                                val (_, childDoc) = loaded
                                val childScopeRes = store.createChildScope(childDoc.metadata.variables, ref.inputMappings)
                                if (childScopeRes.isSuccess) {
                                    val childStore = childScopeRes.getOrThrow()
                                    simulateSteps(childDoc.steps, childStore, depth + 1, callStack + ref.targetWorkflowId)
                                    if (ref.outputMappings.isNotEmpty()) {
                                        store.applyChildOutputs(childStore, ref.outputMappings)
                                    }
                                } else {
                                    warnings.add("Sub-workflow '${ref.targetWorkflowName}' input mapping issue: ${childScopeRes.exceptionOrNull()?.message}")
                                }
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }

        simulateSteps(document.steps, variableStore, 0, listOf(workflow.id))

        val simulatedFinal = variableStore.snapshotValues().mapValues { (k, v) ->
            if (WorkflowVariableStore.shouldMaskInUi(k)) "••••" else v.rawValue
        }

        val isSafe = validationReport.isExecutionPermitted &&
            unresolvedVars.isEmpty() &&
            missingDeps.isEmpty() &&
            !workflow.isArchived

        return WorkflowDryRunReport(
            workflowId = workflow.id,
            workflowName = workflow.name,
            versionNumber = workflow.currentVersion,
            isSafeToRun = isSafe,
            validationReport = validationReport,
            resolvedSubWorkflowOrder = resolvedSubWorkflowOrder,
            plannedSteps = plannedSteps,
            unresolvedVariables = unresolvedVars.toList(),
            missingDependencies = missingDeps,
            credentialStepsRequiringAuth = authNeeded,
            credentialStepsRequiringRebind = rebindNeeded,
            originTransitions = originTransitions,
            destructiveActions = destructiveActions,
            warnings = warnings.distinct(),
            simulatedFinalVariables = simulatedFinal
        )
    }

    private data class DryRunCondEval(
        val result: Boolean?,
        val isDomUnverified: Boolean,
        val explanation: String
    )

    private fun evaluateDryRunCondition(
        node: ConditionNode?,
        store: WorkflowVariableStore,
        currentUrl: String,
        currentOrigin: String,
        previousSucceeded: Boolean,
        passiveDomSnapshot: List<DetectedPageElement>,
        engine: AutomationEngine = AutomationEngine()
    ): DryRunCondEval {
        if (node == null) return DryRunCondEval(null, false, "Missing condition node.")
        return when (node) {
            is ConditionNode.Group -> {
                val childrenEvals = node.children.map {
                    evaluateDryRunCondition(it, store, currentUrl, currentOrigin, previousSucceeded, passiveDomSnapshot, engine)
                }
                val anyDomUnverified = childrenEvals.any { it.isDomUnverified }
                val rawResult = when (node.combinator) {
                    LogicalCombinator.AND -> when {
                        childrenEvals.any { it.result == false } -> false
                        childrenEvals.all { it.result == true } -> true
                        else -> null
                    }
                    LogicalCombinator.OR -> when {
                        childrenEvals.any { it.result == true } -> true
                        childrenEvals.all { it.result == false } -> false
                        else -> null
                    }
                }
                val finalResult = rawResult?.let { if (node.negate) !it else it }
                DryRunCondEval(
                    result = finalResult,
                    isDomUnverified = anyDomUnverified && finalResult == null,
                    explanation = "Group ${node.combinator.id} evaluated deterministically to $finalResult."
                )
            }
            is ConditionNode.Predicate -> {
                val baseEval: DryRunCondEval = when (node.operator) {
                    ConditionOperator.PREVIOUS_STEP_SUCCEEDED ->
                        DryRunCondEval(previousSucceeded, false, "Previous simulated step status=$previousSucceeded.")
                    ConditionOperator.PREVIOUS_STEP_FAILED ->
                        DryRunCondEval(!previousSucceeded, false, "Previous simulated step failed=${!previousSucceeded}.")
                    ConditionOperator.URL_MATCHES -> {
                        val expected = resolveExpr(node.expectedValue, store)
                        val match = currentUrl.contains(expected, ignoreCase = true)
                        DryRunCondEval(match, false, "URL '$currentUrl' contains '$expected' -> $match.")
                    }
                    ConditionOperator.ORIGIN_MATCHES -> {
                        val expectedOrigin = engine.parseOrigin(resolveExpr(node.expectedValue, store)).canonicalOrigin
                        val match = currentOrigin.isNotBlank() && currentOrigin == expectedOrigin
                        DryRunCondEval(match, false, "Origin '$currentOrigin' == '$expectedOrigin' -> $match.")
                    }
                    ConditionOperator.VARIABLE_NOT_EMPTY -> {
                        val actual = store.getVariable(node.variableName)?.rawValue.orEmpty()
                        val match = actual.isNotBlank()
                        DryRunCondEval(match, false, "\${${node.variableName}} not empty -> $match.")
                    }
                    ConditionOperator.VARIABLE_EQUALS -> {
                        val actual = store.getVariable(node.variableName)?.rawValue.orEmpty()
                        val expected = resolveExpr(node.expectedValue, store)
                        val match = actual == expected
                        DryRunCondEval(match, false, "\${${node.variableName}} == '$expected' -> $match.")
                    }
                    ConditionOperator.VARIABLE_CONTAINS -> {
                        val actual = store.getVariable(node.variableName)?.rawValue.orEmpty()
                        val expected = resolveExpr(node.expectedValue, store)
                        val match = actual.contains(expected, ignoreCase = true)
                        DryRunCondEval(match, false, "\${${node.variableName}} contains '$expected' -> $match.")
                    }
                    ConditionOperator.VARIABLE_GREATER_THAN -> {
                        val actualNum = store.getVariable(node.variableName)?.rawValue?.toDoubleOrNull()
                        val expNum = resolveExpr(node.expectedValue, store).toDoubleOrNull()
                        val match = actualNum != null && expNum != null && actualNum > expNum
                        DryRunCondEval(match, false, "\${${node.variableName}} > $expNum -> $match.")
                    }
                    ConditionOperator.VARIABLE_LESS_THAN -> {
                        val actualNum = store.getVariable(node.variableName)?.rawValue?.toDoubleOrNull()
                        val expNum = resolveExpr(node.expectedValue, store).toDoubleOrNull()
                        val match = actualNum != null && expNum != null && actualNum < expNum
                        DryRunCondEval(match, false, "\${${node.variableName}} < $expNum -> $match.")
                    }
                    else -> {
                        // DOM-dependent condition: never execute live JS!
                        if (passiveDomSnapshot.isNotEmpty() &&
                            (node.operator == ConditionOperator.ELEMENT_EXISTS || node.operator == ConditionOperator.ELEMENT_NOT_EXISTS)
                        ) {
                            val found = passiveDomSnapshot.any { el ->
                                (node.selector.isNotBlank() && el.selector == node.selector) ||
                                    (node.targetLabel.isNotBlank() && el.accessibleName.equals(node.targetLabel, ignoreCase = true))
                            }
                            val res = if (node.operator == ConditionOperator.ELEMENT_EXISTS) found else !found
                            DryRunCondEval(res, true, "Checked passive DOM snapshot only (no live WebView query).")
                        } else {
                            DryRunCondEval(null, true, "Live DOM condition UNVERIFIED in dry-run.")
                        }
                    }
                }
                val finalResult = baseEval.result?.let { if (node.negate) !it else it }
                baseEval.copy(result = finalResult)
            }
        }
    }

    private fun resolveExpr(raw: String, store: WorkflowVariableStore): String {
        return when (val r = store.resolveTemplate(raw, strictUndefined = false)) {
            is VariableResolutionResult.Success -> r.value
            else -> raw
        }
    }
}
