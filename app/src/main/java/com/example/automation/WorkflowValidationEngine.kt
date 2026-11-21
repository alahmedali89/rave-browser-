package com.example.automation

import com.example.data.local.AutomationWorkflowEntity
import com.example.data.local.CredentialVaultEntity

/**
 * Structured Phase 8 validation engine producing severity-graded issues (INFO, WARNING, ERROR, CRITICAL),
 * exact step references, clear explanations, suggested fixes, and an execution permission verdict.
 */
object WorkflowValidationEngine {

    suspend fun validateWorkflow(
        workflow: AutomationWorkflowEntity?,
        document: WorkflowDocument,
        workflowLookup: suspend (Long) -> Pair<String, WorkflowDocument>?,
        credentialLookup: (suspend (String) -> CredentialVaultEntity?)? = null,
        preSeededVariableNames: Set<String> = emptySet(),
        engine: AutomationEngine = AutomationEngine()
    ): WorkflowValidationReport {
        val rootId = workflow?.id ?: 0L
        val rootName = workflow?.name?.ifBlank { "Workflow" } ?: "Workflow"
        val issues = mutableListOf<WorkflowValidationIssue>()
        val collectedSteps = mutableListOf<AutomationStep>()
        val referencedIds = linkedSetOf<Long>()
        val rootOriginCanon = workflow?.sourceUrl
            ?.takeIf { it.isNotBlank() && !it.equals("apex://home", ignoreCase = true) }
            ?.let { engine.parseOrigin(it).canonicalOrigin }
            .orEmpty()

        if (workflow?.isArchived == true) {
            issues.add(
                WorkflowValidationIssue(
                    severity = ValidationSeverity.CRITICAL,
                    errorCode = AutomationErrorCode.WORKFLOW_ARCHIVED,
                    title = "Workflow is archived",
                    explanation = "Archived workflows cannot be executed until restored to active workspace.",
                    suggestedResolution = "Restore the workflow from Archive before running or scheduling it.",
                    workflowId = rootId,
                    workflowName = rootName
                )
            )
        }

        if (workflow != null) {
            when (val rawDecode = AutomationStepCodec.decodeSafely(workflow.stepsJson)) {
                is AutomationStepDecodeResult.UnsupportedVersion -> {
                    issues.add(
                        WorkflowValidationIssue(
                            severity = ValidationSeverity.CRITICAL,
                            errorCode = AutomationErrorCode.UNSUPPORTED_SCHEMA,
                            title = "Unsupported step schema v${rawDecode.foundVersion}",
                            explanation = rawDecode.message,
                            suggestedResolution = "Update the app or re-import from a compatible workflow format.",
                            workflowId = rootId,
                            workflowName = rootName
                        )
                    )
                }
                is AutomationStepDecodeResult.MalformedJson -> {
                    issues.add(
                        WorkflowValidationIssue(
                            severity = ValidationSeverity.CRITICAL,
                            errorCode = AutomationErrorCode.UNSUPPORTED_SCHEMA,
                            title = "Malformed workflow JSON",
                            explanation = rawDecode.message,
                            suggestedResolution = "Restore an earlier working version or re-import a valid workflow package.",
                            workflowId = rootId,
                            workflowName = rootName
                        )
                    )
                }
                is AutomationStepDecodeResult.Success -> Unit
            }
        }

        if (document.steps.isEmpty()) {
            issues.add(
                WorkflowValidationIssue(
                    severity = ValidationSeverity.CRITICAL,
                    errorCode = AutomationErrorCode.EMPTY_WORKFLOW,
                    title = "Empty workflow",
                    explanation = "This workflow has no steps to run.",
                    suggestedResolution = "Add at least one action step or record actions before executing.",
                    workflowId = rootId,
                    workflowName = rootName
                )
            )
        }

        suspend fun validateRecursive(
            currentWorkflowId: Long,
            currentWorkflowName: String,
            currentDoc: WorkflowDocument,
            currentOriginCanon: String,
            callStack: List<Long>,
            subDepth: Int,
            seededVars: Set<String>
        ) {
            val knownVars = seededVars.toMutableSet()
            val seenStepIds = mutableSetOf<String>()

            for (allowedOrigin in currentDoc.metadata.allowedOrigins) {
                val parsed = engine.parseOrigin(allowedOrigin)
                if (!parsed.isValid) {
                    issues.add(
                        WorkflowValidationIssue(
                            severity = ValidationSeverity.CRITICAL,
                            errorCode = AutomationErrorCode.INVALID_URL,
                            title = "Invalid allowed origin",
                            explanation = "Allowed origin '$allowedOrigin' in '$currentWorkflowName' is not a valid HTTP(S) origin.",
                            suggestedResolution = "Use a full HTTP(S) origin such as https://example.com.",
                            workflowId = currentWorkflowId,
                            workflowName = currentWorkflowName
                        )
                    )
                }
            }

            currentDoc.metadata.defaultRecoveryPolicy?.let { rp ->
                if (rp.maxRetries !in 0..Phase6Limits.MAX_RECOVERY_RETRIES) {
                    issues.add(
                        WorkflowValidationIssue(
                            severity = ValidationSeverity.CRITICAL,
                            errorCode = AutomationErrorCode.CONDITION_INVALID,
                            title = "Invalid default recovery retry limit",
                            explanation = "Default recovery maxRetries=${rp.maxRetries} exceeds limit ${Phase6Limits.MAX_RECOVERY_RETRIES}.",
                            suggestedResolution = "Set default recovery retries between 0 and ${Phase6Limits.MAX_RECOVERY_RETRIES}.",
                            workflowId = currentWorkflowId,
                            workflowName = currentWorkflowName
                        )
                    )
                }
                rp.waitBeforeRetry?.let { w ->
                    if (w.timeoutMs < 0L) {
                        issues.add(
                            WorkflowValidationIssue(
                                severity = ValidationSeverity.CRITICAL,
                                errorCode = AutomationErrorCode.CONDITION_INVALID,
                                title = "Invalid default recovery wait timeout",
                                explanation = "Default recovery wait timeout (${w.timeoutMs} ms) cannot be negative.",
                                suggestedResolution = "Set timeoutMs to a positive duration.",
                                workflowId = currentWorkflowId,
                                workflowName = currentWorkflowName
                            )
                        )
                    }
                    if (w.selector.isNotBlank() && !w.selector.contains("\${") && !engine.validateSelector(w.selector).isValid) {
                        issues.add(
                            WorkflowValidationIssue(
                                severity = ValidationSeverity.CRITICAL,
                                errorCode = AutomationErrorCode.INVALID_SELECTOR,
                                title = "Invalid default recovery wait selector",
                                explanation = "Default recovery waitBeforeRetry selector '${w.selector}' is invalid.",
                                suggestedResolution = "Provide a valid CSS selector for recovery wait.",
                                workflowId = currentWorkflowId,
                                workflowName = currentWorkflowName
                            )
                        )
                    }
                }
                if (rp.strategy == RecoveryStrategy.WAIT_THEN_RETRY && rp.waitBeforeRetry == null) {
                    issues.add(
                        WorkflowValidationIssue(
                            severity = ValidationSeverity.WARNING,
                            errorCode = AutomationErrorCode.NONE,
                            title = "Missing explicit wait condition for WAIT_THEN_RETRY",
                            explanation = "Default recovery strategy WAIT_THEN_RETRY has no explicit WaitConditionSpec.",
                            suggestedResolution = "Configure a WaitConditionSpec or choose RESCAN_AND_RETRY.",
                            workflowId = currentWorkflowId,
                            workflowName = currentWorkflowName
                        )
                    )
                }
            }

            for (vDef in currentDoc.metadata.variables) {
                if (!WorkflowVariableStore.isValidVariableName(vDef.name)) {
                    issues.add(
                        WorkflowValidationIssue(
                            severity = ValidationSeverity.CRITICAL,
                            errorCode = AutomationErrorCode.VARIABLE_INVALID,
                            title = "Invalid variable name '${vDef.name}'",
                            explanation = "Invalid variable name '${vDef.name}' in workflow '$currentWorkflowName'.",
                            suggestedResolution = "Rename the variable using letters, digits, or underscores starting with a letter.",
                            workflowId = currentWorkflowId,
                            workflowName = currentWorkflowName
                        )
                    )
                    continue
                }
                if (WorkflowVariableStore.isBlockedCredentialVariableName(vDef.name)) {
                    issues.add(
                        WorkflowValidationIssue(
                            severity = ValidationSeverity.CRITICAL,
                            errorCode = AutomationErrorCode.VARIABLE_INVALID,
                            title = "Restricted variable name '${vDef.name}'",
                            explanation = "Variable '${vDef.name}' cannot be used for credential/secret values.",
                            suggestedResolution = "Remove secret/password variables and bind credentials via the encrypted Vault.",
                            workflowId = currentWorkflowId,
                            workflowName = currentWorkflowName
                        )
                    )
                    continue
                }
                if (vDef.defaultValue.isNotEmpty()) {
                    val typeCheck = WorkflowVariableStore.normalizeTypedValue(vDef.type, vDef.defaultValue)
                    if (typeCheck.isFailure) {
                        issues.add(
                            WorkflowValidationIssue(
                                severity = ValidationSeverity.CRITICAL,
                                errorCode = AutomationErrorCode.VARIABLE_INVALID,
                                title = "Invalid default value for '\${${vDef.name}}'",
                                explanation = typeCheck.exceptionOrNull()?.message
                                    ?: "Invalid default value for '${vDef.name}'.",
                                suggestedResolution = "Provide a valid ${vDef.type.label} default value or clear it.",
                                workflowId = currentWorkflowId,
                                workflowName = currentWorkflowName
                            )
                        )
                    }
                }
                knownVars.add(vDef.name)
            }

            suspend fun validateStepList(
                steps: List<AutomationStep>,
                astDepth: Int,
                branchVars: MutableSet<String>
            ) {
                if (astDepth > WorkflowPhase8Constants.MAX_STEP_NESTING_DEPTH) {
                    issues.add(
                        WorkflowValidationIssue(
                            severity = ValidationSeverity.CRITICAL,
                            errorCode = AutomationErrorCode.LOOP_LIMIT_EXCEEDED,
                            title = "Excessive step nesting depth",
                            explanation = "Nested block depth $astDepth exceeds safe limit of ${WorkflowPhase8Constants.MAX_STEP_NESTING_DEPTH}.",
                            suggestedResolution = "Extract deeply nested logic into reusable sub-workflows.",
                            workflowId = currentWorkflowId,
                            workflowName = currentWorkflowName
                        )
                    )
                    return
                }

                var previousOrder = 0
                for ((idx, step) in steps.withIndex()) {
                    collectedSteps.add(step)

                    if (step.id.isBlank()) {
                        issues.add(
                            WorkflowValidationIssue(
                                severity = ValidationSeverity.CRITICAL,
                                errorCode = AutomationErrorCode.MISSING_REQUIRED_PARAM,
                                title = "Missing step ID",
                                explanation = "Step #${idx + 1} ('${step.description}') has no stable identifier.",
                                suggestedResolution = "Assign a unique step ID so diffing, checkpoints, and recovery work reliably.",
                                stepId = step.id,
                                stepOrder = step.order,
                                workflowId = currentWorkflowId,
                                workflowName = currentWorkflowName
                            )
                        )
                    } else if (!seenStepIds.add(step.id)) {
                        issues.add(
                            WorkflowValidationIssue(
                                severity = ValidationSeverity.CRITICAL,
                                errorCode = AutomationErrorCode.DUPLICATE_STEP_ID,
                                title = "Duplicate step ID '${step.id}'",
                                explanation = "Step '${step.description}' reuses ID '${step.id}' within '$currentWorkflowName'.",
                                suggestedResolution = "Regenerate unique step IDs before saving or executing.",
                                stepId = step.id,
                                stepOrder = step.order,
                                workflowId = currentWorkflowId,
                                workflowName = currentWorkflowName
                            )
                        )
                    }

                    if (step.order <= 0 || step.order <= previousOrder) {
                        issues.add(
                            WorkflowValidationIssue(
                                severity = ValidationSeverity.WARNING,
                                errorCode = AutomationErrorCode.NONE,
                                title = "Non-sequential step ordering",
                                explanation = "Step '${step.description}' has order=${step.order} after order=$previousOrder.",
                                suggestedResolution = "Re-save or reorder workflow steps to normalize step indices.",
                                stepId = step.id,
                                stepOrder = step.order,
                                workflowId = currentWorkflowId,
                                workflowName = currentWorkflowName
                            )
                        )
                    }
                    previousOrder = step.order

                    if (step.actionType == AutomationActionType.UNSUPPORTED) {
                        issues.add(
                            WorkflowValidationIssue(
                                severity = ValidationSeverity.CRITICAL,
                                errorCode = AutomationErrorCode.UNSUPPORTED_ACTION,
                                title = "Unsupported action '${step.rawActionType.ifBlank { "UNKNOWN" }}'",
                                explanation = "Step ${step.order} uses unsupported action type '${step.rawActionType.ifBlank { "UNKNOWN" }}' and cannot run in this version.",
                                suggestedResolution = "Replace or remove this step before execution; its data remains preserved.",
                                stepId = step.id,
                                stepOrder = step.order,
                                workflowId = currentWorkflowId,
                                workflowName = currentWorkflowName
                            )
                        )
                    }

                    // Selector check
                    if (step.selector.isNotBlank() && !step.selector.contains("\${")) {
                        val selRes = engine.validateSelector(step.selector)
                        if (!selRes.isValid) {
                            issues.add(
                                WorkflowValidationIssue(
                                    severity = ValidationSeverity.CRITICAL,
                                    errorCode = AutomationErrorCode.INVALID_SELECTOR,
                                    title = "Invalid CSS selector",
                                    explanation = "Invalid selector on step ${step.order} ('${step.description}'): ${selRes.reason}",
                                    suggestedResolution = "Fix or re-pick the CSS selector using the element scanner.",
                                    stepId = step.id,
                                    stepOrder = step.order,
                                    workflowId = currentWorkflowId,
                                    workflowName = currentWorkflowName
                                )
                            )
                        }
                    }
                    step.locatorCandidates.forEach { candidate ->
                        if (candidate.selector.isNotBlank() && !candidate.selector.contains("\${")) {
                            val candRes = engine.validateSelector(candidate.selector)
                            if (!candRes.isValid) {
                                issues.add(
                                    WorkflowValidationIssue(
                                        severity = ValidationSeverity.CRITICAL,
                                        errorCode = AutomationErrorCode.INVALID_SELECTOR,
                                        title = "Invalid fallback CSS selector",
                                        explanation = "Invalid fallback selector '${candidate.selector}' on step ${step.order}: ${candRes.reason}",
                                        suggestedResolution = "Remove or fix the malformed fallback selector candidate.",
                                        stepId = step.id,
                                        stepOrder = step.order,
                                        workflowId = currentWorkflowId,
                                        workflowName = currentWorkflowName
                                    )
                                )
                            }
                        }
                    }

                    // Locator presence checks for interactive DOM actions
                    val needsDomLocator = step.actionType in setOf(
                        AutomationActionType.CLICK,
                        AutomationActionType.INPUT,
                        AutomationActionType.CLEAR,
                        AutomationActionType.SELECT,
                        AutomationActionType.TOGGLE,
                        AutomationActionType.FOCUS,
                        AutomationActionType.BLUR,
                        AutomationActionType.SUBMIT,
                        AutomationActionType.FILL_CREDENTIAL
                    )
                    if (needsDomLocator) {
                        if (step.selector.isBlank() && step.targetLabel.isBlank() && step.locatorCandidates.isEmpty()) {
                            issues.add(
                                WorkflowValidationIssue(
                                    severity = ValidationSeverity.CRITICAL,
                                    errorCode = AutomationErrorCode.MISSING_REQUIRED_PARAM,
                                    title = "Missing target locator",
                                    explanation = "Step ${step.order} (${step.actionType.label}) has neither a selector nor a target label.",
                                    suggestedResolution = "Specify a CSS selector or accessible target label.",
                                    stepId = step.id,
                                    stepOrder = step.order,
                                    workflowId = currentWorkflowId,
                                    workflowName = currentWorkflowName
                                )
                            )
                        } else if (step.selector.isBlank() && step.locatorCandidates.isEmpty()) {
                            issues.add(
                                WorkflowValidationIssue(
                                    severity = ValidationSeverity.INFO,
                                    errorCode = AutomationErrorCode.NONE,
                                    title = "Label-only target heuristic",
                                    explanation = "Step ${step.order} relies on target label '${step.targetLabel}' without a CSS selector.",
                                    suggestedResolution = "Scan the live page to attach a stable CSS selector if available.",
                                    stepId = step.id,
                                    stepOrder = step.order,
                                    workflowId = currentWorkflowId,
                                    workflowName = currentWorkflowName
                                )
                            )
                        }
                    }

                    // Action parameter checks
                    when (step.actionType) {
                        AutomationActionType.NAVIGATE -> {
                            val dest = step.value.ifBlank { step.targetLabel }.trim()
                            if (dest.isEmpty()) {
                                issues.add(
                                    WorkflowValidationIssue(
                                        severity = ValidationSeverity.CRITICAL,
                                        errorCode = AutomationErrorCode.INVALID_URL,
                                        title = "Missing navigation URL",
                                        explanation = "Navigate step ${step.order} has no destination URL.",
                                        suggestedResolution = "Enter an https:// destination URL.",
                                        stepId = step.id,
                                        stepOrder = step.order,
                                        workflowId = currentWorkflowId,
                                        workflowName = currentWorkflowName
                                    )
                                )
                            } else if (!dest.contains("\${")) {
                                val parsedDest = engine.parseOrigin(dest)
                                if (!parsedDest.isValid) {
                                    issues.add(
                                        WorkflowValidationIssue(
                                            severity = ValidationSeverity.CRITICAL,
                                            errorCode = AutomationErrorCode.INVALID_URL,
                                            title = "Disallowed or invalid navigation URL",
                                            explanation = "Navigation URL '$dest' on step ${step.order} is not a valid HTTP(S) destination.",
                                            suggestedResolution = "Use a standard http:// or https:// URL.",
                                            stepId = step.id,
                                            stepOrder = step.order,
                                            workflowId = currentWorkflowId,
                                            workflowName = currentWorkflowName
                                        )
                                    )
                                } else if (currentOriginCanon.isNotBlank() && parsedDest.canonicalOrigin != currentOriginCanon) {
                                    issues.add(
                                        WorkflowValidationIssue(
                                            severity = ValidationSeverity.WARNING,
                                            errorCode = AutomationErrorCode.NONE,
                                            title = "Cross-origin navigation",
                                            explanation = "Step ${step.order} navigates from $currentOriginCanon to ${parsedDest.canonicalOrigin}.",
                                            suggestedResolution = "Verify allowMultiPageContinuity or expected origins match this transition.",
                                            stepId = step.id,
                                            stepOrder = step.order,
                                            workflowId = currentWorkflowId,
                                            workflowName = currentWorkflowName
                                        )
                                    )
                                }
                            }
                        }
                        AutomationActionType.INPUT -> {
                            val isVaultBound = !step.vaultEntryId.isNullOrBlank() ||
                                step.useVaultPool ||
                                step.vaultCategory.isNotBlank() ||
                                step.vaultFolderId.isNotBlank()
                            if (step.value.isBlank() && !step.isCredentialStep && !isVaultBound) {
                                issues.add(
                                    WorkflowValidationIssue(
                                        severity = ValidationSeverity.WARNING,
                                        errorCode = AutomationErrorCode.NONE,
                                        title = "Empty input value",
                                        explanation = "Step ${step.order} (${step.description}) has an empty text value.",
                                        suggestedResolution = "Provide an input string or \${variable} template, or use CLEAR.",
                                        stepId = step.id,
                                        stepOrder = step.order,
                                        workflowId = currentWorkflowId,
                                        workflowName = currentWorkflowName
                                    )
                                )
                            }
                        }
                        AutomationActionType.SELECT -> {
                            if (step.value.isBlank() && step.targetLabel.isBlank()) {
                                issues.add(
                                    WorkflowValidationIssue(
                                        severity = ValidationSeverity.WARNING,
                                        errorCode = AutomationErrorCode.NONE,
                                        title = "Missing option value",
                                        explanation = "Select step ${step.order} does not specify which option to choose.",
                                        suggestedResolution = "Set the option text or value to select.",
                                        stepId = step.id,
                                        stepOrder = step.order,
                                        workflowId = currentWorkflowId,
                                        workflowName = currentWorkflowName
                                    )
                                )
                            }
                        }
                        AutomationActionType.KEY_PRESS -> {
                            if (step.keyboardKey.isBlank() && step.value.isBlank()) {
                                issues.add(
                                    WorkflowValidationIssue(
                                        severity = ValidationSeverity.CRITICAL,
                                        errorCode = AutomationErrorCode.MISSING_REQUIRED_PARAM,
                                        title = "Missing key name",
                                        explanation = "KEY_PRESS step ${step.order} has no keyboard key configured.",
                                        suggestedResolution = "Specify a key such as Enter, Tab, or Escape.",
                                        stepId = step.id,
                                        stepOrder = step.order,
                                        workflowId = currentWorkflowId,
                                        workflowName = currentWorkflowName
                                    )
                                )
                            }
                        }
                        else -> Unit
                    }

                    // Destructive warning
                    if (step.isDestructive || step.actionType == AutomationActionType.SUBMIT) {
                        issues.add(
                            WorkflowValidationIssue(
                                severity = ValidationSeverity.WARNING,
                                errorCode = AutomationErrorCode.NONE,
                                title = "Destructive / submission action",
                                explanation = "Step ${step.order} ('${step.description}') performs a potentially irreversible or submission action.",
                                suggestedResolution = "Runtime confirmation will be required before executing this step.",
                                stepId = step.id,
                                stepOrder = step.order,
                                workflowId = currentWorkflowId,
                                workflowName = currentWorkflowName
                            )
                        )
                    }

                    // Origin & Credential validation
                    if (step.expectedOrigin.isNotBlank()) {
                        val parsedExpected = engine.parseOrigin(step.expectedOrigin)
                        if (!parsedExpected.isValid) {
                            issues.add(
                                WorkflowValidationIssue(
                                    severity = ValidationSeverity.CRITICAL,
                                    errorCode = AutomationErrorCode.CREDENTIAL_ORIGIN_MISMATCH,
                                    title = "Invalid expected origin",
                                    explanation = "Step ${step.order} has invalid expectedOrigin '${step.expectedOrigin}'.",
                                    suggestedResolution = "Update expectedOrigin to a valid HTTP(S) origin.",
                                    stepId = step.id,
                                    stepOrder = step.order,
                                    workflowId = currentWorkflowId,
                                    workflowName = currentWorkflowName
                                )
                            )
                        }
                    }

                    if (step.isCredentialStep) {
                        val refId = step.credentialReferenceId?.trim().orEmpty()
                        if (refId.isEmpty()) {
                            issues.add(
                                WorkflowValidationIssue(
                                    severity = ValidationSeverity.CRITICAL,
                                    errorCode = AutomationErrorCode.CREDENTIAL_UNASSIGNED,
                                    title = "Credential step unassigned",
                                    explanation = "Step ${step.order} ('${step.description}') requires a saved credential, but none is assigned.",
                                    suggestedResolution = "Open step details and bind a matching credential from the encrypted Vault.",
                                    stepId = step.id,
                                    stepOrder = step.order,
                                    workflowId = currentWorkflowId,
                                    workflowName = currentWorkflowName
                                )
                            )
                        } else if (credentialLookup != null) {
                            val vaultEntry = credentialLookup(refId)
                            if (vaultEntry == null) {
                                issues.add(
                                    WorkflowValidationIssue(
                                        severity = ValidationSeverity.CRITICAL,
                                        errorCode = AutomationErrorCode.CREDENTIAL_NOT_FOUND,
                                        title = "Credential missing from Vault",
                                        explanation = "Saved credential for step ${step.order} ('${step.description}') was deleted or does not exist.",
                                        suggestedResolution = "Rebind this step to an existing Vault credential.",
                                        stepId = step.id,
                                        stepOrder = step.order,
                                        workflowId = currentWorkflowId,
                                        workflowName = currentWorkflowName
                                    )
                                )
                            } else {
                                val expectedStepCanon = step.expectedOrigin
                                    .takeIf { it.isNotBlank() }
                                    ?.let { engine.parseOrigin(it).canonicalOrigin }
                                    ?: currentOriginCanon
                                if (expectedStepCanon.isNotBlank() && vaultEntry.websiteOrigin != expectedStepCanon) {
                                    issues.add(
                                        WorkflowValidationIssue(
                                            severity = ValidationSeverity.CRITICAL,
                                            errorCode = AutomationErrorCode.CREDENTIAL_ORIGIN_MISMATCH,
                                            title = "Credential origin mismatch",
                                            explanation = "Origin mismatch: credential '${vaultEntry.label}' is bound to '${vaultEntry.websiteOrigin}', but step expects '$expectedStepCanon'.",
                                            suggestedResolution = "Select a credential matching '$expectedStepCanon'.",
                                            stepId = step.id,
                                            stepOrder = step.order,
                                            workflowId = currentWorkflowId,
                                            workflowName = currentWorkflowName
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Template ${var} validation
                    val templateFields = listOf(step.value, step.selector, step.targetLabel)
                    for (field in templateFields) {
                        for (refVar in WorkflowVariableStore.extractReferencedVariableNames(field)) {
                            if (!WorkflowVariableStore.isValidVariableName(refVar) ||
                                WorkflowVariableStore.isBlockedCredentialVariableName(refVar)
                            ) {
                                issues.add(
                                    WorkflowValidationIssue(
                                        severity = ValidationSeverity.CRITICAL,
                                        errorCode = AutomationErrorCode.VARIABLE_INVALID,
                                        title = "Invalid variable reference '\${$refVar}'",
                                        explanation = "Invalid or restricted variable reference '\${$refVar}' in step '${step.description}'.",
                                        suggestedResolution = "Use a valid non-secret workflow variable name.",
                                        stepId = step.id,
                                        stepOrder = step.order,
                                        workflowId = currentWorkflowId,
                                        workflowName = currentWorkflowName
                                    )
                                )
                            } else if (refVar !in branchVars) {
                                issues.add(
                                    WorkflowValidationIssue(
                                        severity = ValidationSeverity.CRITICAL,
                                        errorCode = AutomationErrorCode.VARIABLE_UNDEFINED,
                                        title = "Unresolved variable '\${$refVar}'",
                                        explanation = "Variable '\${$refVar}' is used before it is defined in step '${step.description}'.",
                                        suggestedResolution = "Define '$refVar' in workflow variables or add a SET_VARIABLE/EXTRACT step earlier.",
                                        stepId = step.id,
                                        stepOrder = step.order,
                                        workflowId = currentWorkflowId,
                                        workflowName = currentWorkflowName
                                    )
                                )
                            }
                        }
                    }

                    // Recovery policy checks
                    step.recoveryPolicy?.let { rp ->
                        if (rp.maxRetries !in 0..Phase6Limits.MAX_RECOVERY_RETRIES) {
                            issues.add(
                                WorkflowValidationIssue(
                                    severity = ValidationSeverity.CRITICAL,
                                    errorCode = AutomationErrorCode.CONDITION_INVALID,
                                    title = "Invalid recovery retry count",
                                    explanation = "Recovery retry count (${rp.maxRetries}) exceeds maximum (${Phase6Limits.MAX_RECOVERY_RETRIES}).",
                                    suggestedResolution = "Reduce recovery maxRetries to ${Phase6Limits.MAX_RECOVERY_RETRIES} or fewer.",
                                    stepId = step.id,
                                    stepOrder = step.order,
                                    workflowId = currentWorkflowId,
                                    workflowName = currentWorkflowName
                                )
                            )
                        }
                        rp.waitBeforeRetry?.let { w ->
                            if (w.timeoutMs < 0L) {
                                issues.add(
                                    WorkflowValidationIssue(
                                        severity = ValidationSeverity.CRITICAL,
                                        errorCode = AutomationErrorCode.CONDITION_INVALID,
                                        title = "Invalid recovery wait timeout",
                                        explanation = "Recovery wait timeout (${w.timeoutMs} ms) on step ${step.order} cannot be negative.",
                                        suggestedResolution = "Set timeoutMs to a positive duration.",
                                        stepId = step.id,
                                        stepOrder = step.order,
                                        workflowId = currentWorkflowId,
                                        workflowName = currentWorkflowName
                                    )
                                )
                            }
                            if (w.selector.isNotBlank() && !w.selector.contains("\${") && !engine.validateSelector(w.selector).isValid) {
                                issues.add(
                                    WorkflowValidationIssue(
                                        severity = ValidationSeverity.CRITICAL,
                                        errorCode = AutomationErrorCode.INVALID_SELECTOR,
                                        title = "Invalid recovery wait selector",
                                        explanation = "Recovery wait selector '${w.selector}' on step ${step.order} is invalid.",
                                        suggestedResolution = "Fix the waitBeforeRetry CSS selector.",
                                        stepId = step.id,
                                        stepOrder = step.order,
                                        workflowId = currentWorkflowId,
                                        workflowName = currentWorkflowName
                                    )
                                )
                            }
                        }
                        if (rp.strategy == RecoveryStrategy.WAIT_THEN_RETRY && rp.waitBeforeRetry == null) {
                            issues.add(
                                WorkflowValidationIssue(
                                    severity = ValidationSeverity.WARNING,
                                    errorCode = AutomationErrorCode.NONE,
                                    title = "Missing explicit wait condition for WAIT_THEN_RETRY",
                                    explanation = "Step ${step.order} uses WAIT_THEN_RETRY without an explicit WaitConditionSpec.",
                                    suggestedResolution = "Configure waitBeforeRetry or choose RESCAN_AND_RETRY.",
                                    stepId = step.id,
                                    stepOrder = step.order,
                                    workflowId = currentWorkflowId,
                                    workflowName = currentWorkflowName
                                )
                            )
                        }
                        if (rp.strategy == RecoveryStrategy.ALTERNATIVE_BRANCH && rp.alternativeSteps.isEmpty()) {
                            issues.add(
                                WorkflowValidationIssue(
                                    severity = ValidationSeverity.WARNING,
                                    errorCode = AutomationErrorCode.NONE,
                                    title = "Empty alternative recovery branch",
                                    explanation = "Step ${step.order} uses ALTERNATIVE_BRANCH recovery with no fallback steps.",
                                    suggestedResolution = "Add fallback steps or switch recovery strategy.",
                                    stepId = step.id,
                                    stepOrder = step.order,
                                    workflowId = currentWorkflowId,
                                    workflowName = currentWorkflowName
                                )
                            )
                        }
                        if (rp.alternativeSteps.isNotEmpty()) {
                            validateStepList(rp.alternativeSteps, astDepth + 1, branchVars.toMutableSet())
                        }
                    }

                    // Phase 6 Control Flow / Variables / Extraction / Sub-workflows
                    when (step.actionType) {
                        AutomationActionType.CONDITION -> {
                            val cond = step.condition
                            if (cond == null) {
                                issues.add(
                                    WorkflowValidationIssue(
                                        severity = ValidationSeverity.CRITICAL,
                                        errorCode = AutomationErrorCode.CONDITION_INVALID,
                                        title = "Missing condition expression",
                                        explanation = "Conditional step '${step.description}' has no condition configured.",
                                        suggestedResolution = "Configure a condition predicate or group.",
                                        stepId = step.id,
                                        stepOrder = step.order,
                                        workflowId = currentWorkflowId,
                                        workflowName = currentWorkflowName
                                    )
                                )
                            } else {
                                validateConditionIssues(cond, branchVars, engine, step, currentWorkflowId, currentWorkflowName, issues)
                            }
                            if (step.thenSteps.isEmpty() && step.elseIfBranches.all { it.steps.isEmpty() } && step.elseSteps.isEmpty()) {
                                issues.add(
                                    WorkflowValidationIssue(
                                        severity = ValidationSeverity.WARNING,
                                        errorCode = AutomationErrorCode.NONE,
                                        title = "Empty conditional branches",
                                        explanation = "Conditional step '${step.description}' has no steps in then/else-if/else branches.",
                                        suggestedResolution = "Add steps to at least one conditional branch.",
                                        stepId = step.id,
                                        stepOrder = step.order,
                                        workflowId = currentWorkflowId,
                                        workflowName = currentWorkflowName
                                    )
                                )
                            }
                            val baseVars = branchVars.toSet()
                            val branchEndVarSets = mutableListOf<Set<String>>()
                            val thenVars = baseVars.toMutableSet()
                            validateStepList(step.thenSteps, astDepth + 1, thenVars)
                            branchEndVarSets.add(thenVars)
                            for (elif in step.elseIfBranches) {
                                validateConditionIssues(elif.condition, baseVars, engine, step, currentWorkflowId, currentWorkflowName, issues)
                                val elifVars = baseVars.toMutableSet()
                                validateStepList(elif.steps, astDepth + 1, elifVars)
                                branchEndVarSets.add(elifVars)
                            }
                            if (step.elseSteps.isNotEmpty()) {
                                val elseVars = baseVars.toMutableSet()
                                validateStepList(step.elseSteps, astDepth + 1, elseVars)
                                branchEndVarSets.add(elseVars)
                                val guaranteedVars = branchEndVarSets.reduce { acc, set -> acc.intersect(set) }
                                branchVars.addAll(guaranteedVars)
                            }
                        }
                        AutomationActionType.LOOP -> {
                            val cfg = step.loopConfig
                            if (cfg == null) {
                                issues.add(
                                    WorkflowValidationIssue(
                                        severity = ValidationSeverity.CRITICAL,
                                        errorCode = AutomationErrorCode.LOOP_LIMIT_EXCEEDED,
                                        title = "Missing loop configuration",
                                        explanation = "Loop step '${step.description}' must configure bounded loop limits.",
                                        suggestedResolution = "Set maxIterations between 1 and ${Phase6Limits.MAX_LOOP_ITERATIONS}.",
                                        stepId = step.id,
                                        stepOrder = step.order,
                                        workflowId = currentWorkflowId,
                                        workflowName = currentWorkflowName
                                    )
                                )
                            } else {
                                if (cfg.maxIterations !in 1..Phase6Limits.MAX_LOOP_ITERATIONS) {
                                    issues.add(
                                        WorkflowValidationIssue(
                                            severity = ValidationSeverity.CRITICAL,
                                            errorCode = AutomationErrorCode.LOOP_LIMIT_EXCEEDED,
                                            title = "Loop limit out of bounds",
                                            explanation = "Loop iterations must be between 1 and ${Phase6Limits.MAX_LOOP_ITERATIONS}.",
                                            suggestedResolution = "Adjust maxIterations into 1..${Phase6Limits.MAX_LOOP_ITERATIONS}.",
                                            stepId = step.id,
                                            stepOrder = step.order,
                                            workflowId = currentWorkflowId,
                                            workflowName = currentWorkflowName
                                        )
                                    )
                                }
                                val loopVars = branchVars.toMutableSet()
                                if (cfg.iterationVariable.isNotBlank()) {
                                    if (!WorkflowVariableStore.isValidVariableName(cfg.iterationVariable) ||
                                        WorkflowVariableStore.isBlockedCredentialVariableName(cfg.iterationVariable)
                                    ) {
                                        issues.add(
                                            WorkflowValidationIssue(
                                                severity = ValidationSeverity.CRITICAL,
                                                errorCode = AutomationErrorCode.VARIABLE_INVALID,
                                                title = "Invalid loop variable '${cfg.iterationVariable}'",
                                                explanation = "Invalid loop index variable '${cfg.iterationVariable}'.",
                                                suggestedResolution = "Use a standard alphanumeric loop index variable name.",
                                                stepId = step.id,
                                                stepOrder = step.order,
                                                workflowId = currentWorkflowId,
                                                workflowName = currentWorkflowName
                                            )
                                        )
                                    } else {
                                        loopVars.add(cfg.iterationVariable)
                                    }
                                }
                                cfg.continueCondition?.let {
                                    validateConditionIssues(it, loopVars, engine, step, currentWorkflowId, currentWorkflowName, issues)
                                }
                                if (step.thenSteps.isEmpty()) {
                                    issues.add(
                                        WorkflowValidationIssue(
                                            severity = ValidationSeverity.WARNING,
                                            errorCode = AutomationErrorCode.NONE,
                                            title = "Empty loop body",
                                            explanation = "Loop step '${step.description}' has no nested steps to execute.",
                                            suggestedResolution = "Add steps inside the loop body.",
                                            stepId = step.id,
                                            stepOrder = step.order,
                                            workflowId = currentWorkflowId,
                                            workflowName = currentWorkflowName
                                        )
                                    )
                                }
                                validateStepList(step.thenSteps, astDepth + 1, loopVars)
                            }
                        }
                        AutomationActionType.SET_VARIABLE -> {
                            val varName = step.variableName.trim().ifBlank { step.targetLabel.trim() }
                            if (!WorkflowVariableStore.isValidVariableName(varName)) {
                                issues.add(
                                    WorkflowValidationIssue(
                                        severity = ValidationSeverity.CRITICAL,
                                        errorCode = AutomationErrorCode.VARIABLE_INVALID,
                                        title = "Invalid SET_VARIABLE name",
                                        explanation = "Invalid variable name '$varName' in SET_VARIABLE step.",
                                        suggestedResolution = "Use a valid variable identifier (letters, digits, underscore).",
                                        stepId = step.id,
                                        stepOrder = step.order,
                                        workflowId = currentWorkflowId,
                                        workflowName = currentWorkflowName
                                    )
                                )
                            } else if (WorkflowVariableStore.isBlockedCredentialVariableName(varName)) {
                                issues.add(
                                    WorkflowValidationIssue(
                                        severity = ValidationSeverity.CRITICAL,
                                        errorCode = AutomationErrorCode.VARIABLE_INVALID,
                                        title = "Restricted variable name '$varName'",
                                        explanation = "Variable '$varName' cannot store credential or secret values.",
                                        suggestedResolution = "Use a non-secret variable name or store secrets in the Vault.",
                                        stepId = step.id,
                                        stepOrder = step.order,
                                        workflowId = currentWorkflowId,
                                        workflowName = currentWorkflowName
                                    )
                                )
                            } else {
                                if (!step.value.contains("\${")) {
                                    val typeCheck = WorkflowVariableStore.normalizeTypedValue(step.variableType, step.value)
                                    if (typeCheck.isFailure) {
                                        issues.add(
                                            WorkflowValidationIssue(
                                                severity = ValidationSeverity.CRITICAL,
                                                errorCode = AutomationErrorCode.VARIABLE_INVALID,
                                                title = "Variable value type mismatch",
                                                explanation = typeCheck.exceptionOrNull()?.message ?: "Invalid value for '$varName'.",
                                                suggestedResolution = "Enter a valid ${step.variableType.label} value.",
                                                stepId = step.id,
                                                stepOrder = step.order,
                                                workflowId = currentWorkflowId,
                                                workflowName = currentWorkflowName
                                            )
                                        )
                                    }
                                }
                                branchVars.add(varName)
                            }
                        }
                        AutomationActionType.EXTRACT -> {
                            val outVar = step.extractionSpec?.outputVariableName?.trim()
                                ?.ifBlank { step.variableName.trim() }
                                .orEmpty()
                            if (outVar.isNotEmpty()) {
                                if (!WorkflowVariableStore.isValidVariableName(outVar) ||
                                    WorkflowVariableStore.isBlockedCredentialVariableName(outVar)
                                ) {
                                    issues.add(
                                        WorkflowValidationIssue(
                                            severity = ValidationSeverity.CRITICAL,
                                            errorCode = AutomationErrorCode.VARIABLE_INVALID,
                                            title = "Invalid extraction variable '$outVar'",
                                            explanation = "Invalid extraction output variable '$outVar'.",
                                            suggestedResolution = "Choose a valid non-secret output variable name.",
                                            stepId = step.id,
                                            stepOrder = step.order,
                                            workflowId = currentWorkflowId,
                                            workflowName = currentWorkflowName
                                        )
                                    )
                                } else {
                                    branchVars.add(outVar)
                                }
                            }
                            val spec = step.extractionSpec
                            if (spec != null && spec.mode == ExtractionMode.ATTRIBUTE) {
                                val attr = spec.attributeName.trim().lowercase()
                                if (attr.isEmpty() || attr !in ExtractionSpec.ALLOWLISTED_ATTRIBUTES) {
                                    issues.add(
                                        WorkflowValidationIssue(
                                            severity = ValidationSeverity.CRITICAL,
                                            errorCode = AutomationErrorCode.EXTRACTION_SENSITIVE_BLOCKED,
                                            title = "Blocked extraction attribute",
                                            explanation = "Attribute '${spec.attributeName}' is not in the safe extraction allowlist.",
                                            suggestedResolution = "Extract visible text or an allowlisted attribute (e.g. href, title, aria-label).",
                                            stepId = step.id,
                                            stepOrder = step.order,
                                            workflowId = currentWorkflowId,
                                            workflowName = currentWorkflowName
                                        )
                                    )
                                }
                            }
                        }
                        AutomationActionType.CALL_WORKFLOW -> {
                            val ref = step.subWorkflowRef
                            if (ref == null) {
                                issues.add(
                                    WorkflowValidationIssue(
                                        severity = ValidationSeverity.CRITICAL,
                                        errorCode = AutomationErrorCode.SUBWORKFLOW_NOT_FOUND,
                                        title = "Missing sub-workflow reference",
                                        explanation = "Sub-workflow step '${step.description}' is missing target workflow reference.",
                                        suggestedResolution = "Select a valid target sub-workflow.",
                                        stepId = step.id,
                                        stepOrder = step.order,
                                        workflowId = currentWorkflowId,
                                        workflowName = currentWorkflowName
                                    )
                                )
                            } else if (ref.targetWorkflowId in callStack) {
                                issues.add(
                                    WorkflowValidationIssue(
                                        severity = ValidationSeverity.CRITICAL,
                                        errorCode = AutomationErrorCode.SUBWORKFLOW_CYCLE,
                                        title = "Circular sub-workflow dependency",
                                        explanation = "Circular sub-workflow reference detected (workflow ID ${ref.targetWorkflowId}).",
                                        suggestedResolution = "Break the dependency cycle between workflows.",
                                        stepId = step.id,
                                        stepOrder = step.order,
                                        workflowId = currentWorkflowId,
                                        workflowName = currentWorkflowName
                                    )
                                )
                            } else if (subDepth + 1 > Phase6Limits.MAX_SUBWORKFLOW_DEPTH) {
                                issues.add(
                                    WorkflowValidationIssue(
                                        severity = ValidationSeverity.CRITICAL,
                                        errorCode = AutomationErrorCode.SUBWORKFLOW_DEPTH_EXCEEDED,
                                        title = "Sub-workflow nesting depth exceeded",
                                        explanation = "Sub-workflow nesting depth exceeds maximum of ${Phase6Limits.MAX_SUBWORKFLOW_DEPTH}.",
                                        suggestedResolution = "Flatten or merge nested sub-workflows.",
                                        stepId = step.id,
                                        stepOrder = step.order,
                                        workflowId = currentWorkflowId,
                                        workflowName = currentWorkflowName
                                    )
                                )
                            } else {
                                val loaded = workflowLookup(ref.targetWorkflowId)
                                if (loaded == null) {
                                    issues.add(
                                        WorkflowValidationIssue(
                                            severity = ValidationSeverity.CRITICAL,
                                            errorCode = AutomationErrorCode.SUBWORKFLOW_NOT_FOUND,
                                            title = "Missing sub-workflow",
                                            explanation = "Referenced sub-workflow '${ref.targetWorkflowName.ifBlank { ref.targetWorkflowId }}' no longer exists.",
                                            suggestedResolution = "Restore the missing sub-workflow or remove this CALL_WORKFLOW step.",
                                            stepId = step.id,
                                            stepOrder = step.order,
                                            workflowId = currentWorkflowId,
                                            workflowName = currentWorkflowName
                                        )
                                    )
                                } else {
                                    referencedIds.add(ref.targetWorkflowId)
                                    val (childName, childDoc) = loaded
                                    val seededChildVars = buildSet {
                                        childDoc.metadata.variables.forEach { add(it.name) }
                                        ref.inputMappings.keys.forEach { add(it.trim()) }
                                    }
                                    validateRecursive(
                                        currentWorkflowId = ref.targetWorkflowId,
                                        currentWorkflowName = childName,
                                        currentDoc = childDoc,
                                        currentOriginCanon = currentOriginCanon,
                                        callStack = callStack + ref.targetWorkflowId,
                                        subDepth = subDepth + 1,
                                        seededVars = seededChildVars
                                    )
                                    ref.outputMappings.keys.forEach { parentOutVar ->
                                        val clean = parentOutVar.trim()
                                        if (!WorkflowVariableStore.isValidVariableName(clean) ||
                                            WorkflowVariableStore.isBlockedCredentialVariableName(clean)
                                        ) {
                                            issues.add(
                                                WorkflowValidationIssue(
                                                    severity = ValidationSeverity.CRITICAL,
                                                    errorCode = AutomationErrorCode.VARIABLE_INVALID,
                                                    title = "Invalid sub-workflow output variable",
                                                    explanation = "Invalid sub-workflow output variable '$clean'.",
                                                    suggestedResolution = "Rename the output variable mapping.",
                                                    stepId = step.id,
                                                    stepOrder = step.order,
                                                    workflowId = currentWorkflowId,
                                                    workflowName = currentWorkflowName
                                                )
                                            )
                                        } else {
                                            branchVars.add(clean)
                                        }
                                    }
                                }
                            }
                        }
                        else -> Unit
                    }
                }
            }

            validateStepList(currentDoc.steps, astDepth = 1, branchVars = knownVars)
        }

        if (document.steps.isNotEmpty()) {
            validateRecursive(
                currentWorkflowId = rootId,
                currentWorkflowName = rootName,
                currentDoc = document,
                currentOriginCanon = rootOriginCanon,
                callStack = listOf(rootId),
                subDepth = 0,
                seededVars = preSeededVariableNames
            )
        }

        val credSteps = collectedSteps.filter { it.isCredentialStep }
        val needsRebind = credSteps.any { it.credentialReferenceId.isNullOrBlank() }
        val isPermitted = issues.none { it.severity.blocksExecution }

        return WorkflowValidationReport(
            workflowId = rootId,
            workflowName = rootName,
            isExecutionPermitted = isPermitted,
            issues = issues,
            allNestedSteps = collectedSteps,
            referencedWorkflowIds = referencedIds,
            hasCredentialSteps = credSteps.isNotEmpty(),
            requiresCredentialRebinding = needsRebind,
            isArchived = workflow?.isArchived == true
        )
    }

    private fun validateConditionIssues(
        node: ConditionNode,
        knownVariables: Set<String>,
        engine: AutomationEngine,
        step: AutomationStep,
        workflowId: Long,
        workflowName: String,
        issues: MutableList<WorkflowValidationIssue>,
        currentDepth: Int = 1
    ) {
        if (currentDepth > Phase6Limits.MAX_CONDITION_DEPTH) {
            issues.add(
                WorkflowValidationIssue(
                    severity = ValidationSeverity.CRITICAL,
                    errorCode = AutomationErrorCode.CONDITION_INVALID,
                    title = "Condition nesting too deep",
                    explanation = "Condition nesting exceeds maximum depth of ${Phase6Limits.MAX_CONDITION_DEPTH}.",
                    suggestedResolution = "Simplify nested AND/OR condition groups.",
                    stepId = step.id,
                    stepOrder = step.order,
                    workflowId = workflowId,
                    workflowName = workflowName
                )
            )
            return
        }
        when (node) {
            is ConditionNode.Group -> {
                if (node.children.isEmpty()) {
                    issues.add(
                        WorkflowValidationIssue(
                            severity = ValidationSeverity.CRITICAL,
                            errorCode = AutomationErrorCode.CONDITION_INVALID,
                            title = "Empty condition group",
                            explanation = "Condition group (${node.combinator.id}) must contain at least one condition.",
                            suggestedResolution = "Add a condition predicate inside the group.",
                            stepId = step.id,
                            stepOrder = step.order,
                            workflowId = workflowId,
                            workflowName = workflowName
                        )
                    )
                    return
                }
                node.children.forEach {
                    validateConditionIssues(it, knownVariables, engine, step, workflowId, workflowName, issues, currentDepth + 1)
                }
            }
            is ConditionNode.Predicate -> {
                if (node.operator.requiresDomTarget && node.selector.isBlank() && node.targetLabel.isBlank()) {
                    issues.add(
                        WorkflowValidationIssue(
                            severity = ValidationSeverity.CRITICAL,
                            errorCode = AutomationErrorCode.CONDITION_INVALID,
                            title = "Condition missing DOM target",
                            explanation = "Condition '${node.operator.label}' requires a selector or target label.",
                            suggestedResolution = "Provide a target selector or label.",
                            stepId = step.id,
                            stepOrder = step.order,
                            workflowId = workflowId,
                            workflowName = workflowName
                        )
                    )
                }
                if (node.selector.isNotBlank() && !node.selector.contains("\${")) {
                    val selRes = engine.validateSelector(node.selector)
                    if (!selRes.isValid) {
                        issues.add(
                            WorkflowValidationIssue(
                                severity = ValidationSeverity.CRITICAL,
                                errorCode = AutomationErrorCode.INVALID_SELECTOR,
                                title = "Invalid condition selector",
                                explanation = "Invalid selector in condition '${node.operator.label}': ${selRes.reason}",
                                suggestedResolution = "Fix the CSS selector inside this condition.",
                                stepId = step.id,
                                stepOrder = step.order,
                                workflowId = workflowId,
                                workflowName = workflowName
                            )
                        )
                    }
                }
                val isVarOp = node.operator in setOf(
                    ConditionOperator.VARIABLE_EQUALS,
                    ConditionOperator.VARIABLE_CONTAINS,
                    ConditionOperator.VARIABLE_NOT_EMPTY,
                    ConditionOperator.VARIABLE_GREATER_THAN,
                    ConditionOperator.VARIABLE_LESS_THAN
                )
                if (isVarOp) {
                    val vName = node.variableName.trim()
                    if (!WorkflowVariableStore.isValidVariableName(vName)) {
                        issues.add(
                            WorkflowValidationIssue(
                                severity = ValidationSeverity.CRITICAL,
                                errorCode = AutomationErrorCode.CONDITION_INVALID,
                                title = "Invalid condition variable",
                                explanation = "Variable condition '${node.operator.label}' requires a valid variable name.",
                                suggestedResolution = "Specify an existing workflow variable name.",
                                stepId = step.id,
                                stepOrder = step.order,
                                workflowId = workflowId,
                                workflowName = workflowName
                            )
                        )
                    } else if (vName !in knownVariables) {
                        issues.add(
                            WorkflowValidationIssue(
                                severity = ValidationSeverity.CRITICAL,
                                errorCode = AutomationErrorCode.VARIABLE_UNDEFINED,
                                title = "Undefined condition variable '$vName'",
                                explanation = "Condition references undefined variable '$vName'.",
                                suggestedResolution = "Define '$vName' before evaluating this condition.",
                                stepId = step.id,
                                stepOrder = step.order,
                                workflowId = workflowId,
                                workflowName = workflowName
                            )
                        )
                    }
                }
                val refs = WorkflowVariableStore.extractReferencedVariableNames(node.expectedValue) +
                    WorkflowVariableStore.extractReferencedVariableNames(node.selector) +
                    WorkflowVariableStore.extractReferencedVariableNames(node.targetLabel)
                for (ref in refs) {
                    if (ref !in knownVariables) {
                        issues.add(
                            WorkflowValidationIssue(
                                severity = ValidationSeverity.CRITICAL,
                                errorCode = AutomationErrorCode.VARIABLE_UNDEFINED,
                                title = "Undefined template variable '\${$ref}'",
                                explanation = "Condition references undefined variable '\${$ref}'.",
                                suggestedResolution = "Define '$ref' before using it in this condition.",
                                stepId = step.id,
                                stepOrder = step.order,
                                workflowId = workflowId,
                                workflowName = workflowName
                            )
                        )
                    }
                }
                when (node.operator) {
                    ConditionOperator.URL_MATCHES,
                    ConditionOperator.ORIGIN_MATCHES -> {
                        if (node.expectedValue.isBlank()) {
                            issues.add(
                                WorkflowValidationIssue(
                                    severity = ValidationSeverity.CRITICAL,
                                    errorCode = AutomationErrorCode.CONDITION_INVALID,
                                    title = "Missing condition match pattern",
                                    explanation = "Condition '${node.operator.label}' requires a non-empty URL or origin pattern.",
                                    suggestedResolution = "Specify an expected URL substring or origin.",
                                    stepId = step.id,
                                    stepOrder = step.order,
                                    workflowId = workflowId,
                                    workflowName = workflowName
                                )
                            )
                        }
                    }
                    ConditionOperator.VARIABLE_GREATER_THAN,
                    ConditionOperator.VARIABLE_LESS_THAN -> {
                        val trimmed = node.expectedValue.trim()
                        if (trimmed.isEmpty()) {
                            issues.add(
                                WorkflowValidationIssue(
                                    severity = ValidationSeverity.CRITICAL,
                                    errorCode = AutomationErrorCode.CONDITION_INVALID,
                                    title = "Missing numeric comparison threshold",
                                    explanation = "Condition '${node.operator.label}' requires a numeric expected value.",
                                    suggestedResolution = "Enter a number or numeric \${variable} reference.",
                                    stepId = step.id,
                                    stepOrder = step.order,
                                    workflowId = workflowId,
                                    workflowName = workflowName
                                )
                            )
                        } else if (!trimmed.contains("\${") && trimmed.toDoubleOrNull() == null) {
                            issues.add(
                                WorkflowValidationIssue(
                                    severity = ValidationSeverity.CRITICAL,
                                    errorCode = AutomationErrorCode.CONDITION_INVALID,
                                    title = "Non-numeric threshold '${node.expectedValue}'",
                                    explanation = "Condition '${node.operator.label}' expects a numeric value, found '${node.expectedValue}'.",
                                    suggestedResolution = "Provide a valid number or numeric \${variable} template.",
                                    stepId = step.id,
                                    stepOrder = step.order,
                                    workflowId = workflowId,
                                    workflowName = workflowName
                                )
                            )
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}
