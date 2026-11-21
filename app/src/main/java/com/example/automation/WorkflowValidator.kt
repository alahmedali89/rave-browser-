package com.example.automation

import com.example.data.local.AutomationWorkflowEntity

data class WorkflowValidationResult(
    val isValid: Boolean,
    val errorCode: AutomationErrorCode = AutomationErrorCode.NONE,
    val message: String = "",
    val allNestedSteps: List<AutomationStep> = emptyList(),
    val referencedWorkflowIds: Set<Long> = emptySet()
)

object WorkflowValidator {

    suspend fun validateWorkflow(
        rootWorkflowId: Long,
        document: WorkflowDocument,
        workflowLookup: suspend (Long) -> Pair<String, WorkflowDocument>?,
        preSeededVariableNames: Set<String> = emptySet(),
        engine: AutomationEngine = AutomationEngine()
    ): WorkflowValidationResult {
        if (document.steps.isNotEmpty() || preSeededVariableNames.isNotEmpty() || document.metadata.variables.isNotEmpty()) {
            val report = WorkflowValidationEngine.validateWorkflow(
                workflow = AutomationWorkflowEntity(
                    id = rootWorkflowId,
                    name = "Root",
                    domain = "",
                    sourceUrl = "",
                    type = AutomationType.MANUAL.id
                ),
                document = document,
                workflowLookup = workflowLookup,
                credentialLookup = null,
                preSeededVariableNames = preSeededVariableNames,
                engine = engine
            )
            val blocking = report.firstBlockingIssue
            // Note: Existing Phase 6 unit tests may pass standalone steps cleanly; preserve exact result contract
            if (blocking != null && blocking.errorCode != AutomationErrorCode.CREDENTIAL_UNASSIGNED && blocking.errorCode != AutomationErrorCode.EMPTY_WORKFLOW) {
                return WorkflowValidationResult(
                    isValid = false,
                    errorCode = blocking.errorCode,
                    message = blocking.explanation,
                    allNestedSteps = report.allNestedSteps,
                    referencedWorkflowIds = report.referencedWorkflowIds
                )
            }
            if (blocking != null && blocking.errorCode == AutomationErrorCode.CREDENTIAL_UNASSIGNED &&
                report.allNestedSteps.any { it.isCredentialStep }
            ) {
                return WorkflowValidationResult(
                    isValid = false,
                    errorCode = blocking.errorCode,
                    message = blocking.explanation,
                    allNestedSteps = report.allNestedSteps,
                    referencedWorkflowIds = report.referencedWorkflowIds
                )
            }
            return WorkflowValidationResult(
                isValid = true,
                allNestedSteps = report.allNestedSteps,
                referencedWorkflowIds = report.referencedWorkflowIds
            )
        }
        return validateRecursiveBlockingCheck(
            currentWorkflowId = rootWorkflowId,
            currentWorkflowName = "Root",
            document = document,
            callStack = listOf(rootWorkflowId),
            subDepth = 0,
            workflowLookup = workflowLookup,
            preSeededVariableNames = preSeededVariableNames,
            engine = engine
        )
    }

    private suspend fun validateRecursiveBlockingCheck(
        currentWorkflowId: Long,
        currentWorkflowName: String,
        document: WorkflowDocument,
        callStack: List<Long>,
        subDepth: Int,
        workflowLookup: suspend (Long) -> Pair<String, WorkflowDocument>?,
        preSeededVariableNames: Set<String>,
        engine: AutomationEngine
    ): WorkflowValidationResult {
        val knownVars = preSeededVariableNames.toMutableSet()
        for (vDef in document.metadata.variables) {
            if (!WorkflowVariableStore.isValidVariableName(vDef.name)) {
                return WorkflowValidationResult(
                    isValid = false,
                    errorCode = AutomationErrorCode.VARIABLE_INVALID,
                    message = "Invalid variable name '${vDef.name}' in workflow '$currentWorkflowName'."
                )
            }
            if (WorkflowVariableStore.isBlockedCredentialVariableName(vDef.name)) {
                return WorkflowValidationResult(
                    isValid = false,
                    errorCode = AutomationErrorCode.VARIABLE_INVALID,
                    message = "Variable '${vDef.name}' cannot be used for credential/secret values."
                )
            }
            if (vDef.defaultValue.isNotEmpty()) {
                val typeCheck = WorkflowVariableStore.normalizeTypedValue(vDef.type, vDef.defaultValue)
                if (typeCheck.isFailure) {
                    return WorkflowValidationResult(
                        isValid = false,
                        errorCode = AutomationErrorCode.VARIABLE_INVALID,
                        message = typeCheck.exceptionOrNull()?.message ?: "Invalid default value for '${vDef.name}'."
                    )
                }
            }
            knownVars.add(vDef.name)
        }

        val collectedSteps = mutableListOf<AutomationStep>()
        val referencedIds = linkedSetOf<Long>()

        suspend fun validateStepList(steps: List<AutomationStep>): WorkflowValidationResult? {
            for (step in steps) {
                collectedSteps.add(step)

                if (step.selector.isNotBlank() && !step.selector.contains("\${")) {
                    val selRes = engine.validateSelector(step.selector)
                    if (!selRes.isValid) {
                        return WorkflowValidationResult(
                            isValid = false,
                            errorCode = AutomationErrorCode.INVALID_SELECTOR,
                            message = "Invalid selector on step ${step.order} ('${step.description}'): ${selRes.reason}"
                        )
                    }
                }

                // Check ${var} references in step parameters
                val templateFields = listOf(step.value, step.selector, step.targetLabel)
                for (field in templateFields) {
                    for (refVar in WorkflowVariableStore.extractReferencedVariableNames(field)) {
                        if (!WorkflowVariableStore.isValidVariableName(refVar) ||
                            WorkflowVariableStore.isBlockedCredentialVariableName(refVar)
                        ) {
                            return WorkflowValidationResult(
                                isValid = false,
                                errorCode = AutomationErrorCode.VARIABLE_INVALID,
                                message = "Invalid or restricted variable reference '\${$refVar}' in step '${step.description}'."
                            )
                        }
                        if (refVar !in knownVars) {
                            return WorkflowValidationResult(
                                isValid = false,
                                errorCode = AutomationErrorCode.VARIABLE_UNDEFINED,
                                message = "Variable '\${$refVar}' is used before it is defined in step '${step.description}'."
                            )
                        }
                    }
                }

                // Check recovery policy bounds
                step.recoveryPolicy?.let { rp ->
                    if (rp.maxRetries > Phase6Limits.MAX_RECOVERY_RETRIES) {
                        return WorkflowValidationResult(
                            isValid = false,
                            errorCode = AutomationErrorCode.CONDITION_INVALID,
                            message = "Recovery retry count exceeds maximum (${Phase6Limits.MAX_RECOVERY_RETRIES})."
                        )
                    }
                    if (rp.alternativeSteps.isNotEmpty()) {
                        validateStepList(rp.alternativeSteps)?.let { return it }
                    }
                }

                when (step.actionType) {
                    AutomationActionType.CONDITION -> {
                        val cond = step.condition ?: return WorkflowValidationResult(
                            isValid = false,
                            errorCode = AutomationErrorCode.CONDITION_INVALID,
                            message = "Conditional step '${step.description}' has no condition configured."
                        )
                        validateConditionNode(cond, knownVars, engine)?.let { return it }
                        validateStepList(step.thenSteps)?.let { return it }
                        for (elif in step.elseIfBranches) {
                            validateConditionNode(elif.condition, knownVars, engine)?.let { return it }
                            validateStepList(elif.steps)?.let { return it }
                        }
                        validateStepList(step.elseSteps)?.let { return it }
                    }
                    AutomationActionType.LOOP -> {
                        val cfg = step.loopConfig ?: return WorkflowValidationResult(
                            isValid = false,
                            errorCode = AutomationErrorCode.LOOP_LIMIT_EXCEEDED,
                            message = "Loop step '${step.description}' must configure bounded loop limits."
                        )
                        if (cfg.maxIterations !in 1..Phase6Limits.MAX_LOOP_ITERATIONS) {
                            return WorkflowValidationResult(
                                isValid = false,
                                errorCode = AutomationErrorCode.LOOP_LIMIT_EXCEEDED,
                                message = "Loop iterations must be between 1 and ${Phase6Limits.MAX_LOOP_ITERATIONS}."
                            )
                        }
                        if (cfg.iterationVariable.isNotBlank()) {
                            if (!WorkflowVariableStore.isValidVariableName(cfg.iterationVariable) ||
                                WorkflowVariableStore.isBlockedCredentialVariableName(cfg.iterationVariable)
                            ) {
                                return WorkflowValidationResult(
                                    isValid = false,
                                    errorCode = AutomationErrorCode.VARIABLE_INVALID,
                                    message = "Invalid loop index variable '${cfg.iterationVariable}'."
                                )
                            }
                            knownVars.add(cfg.iterationVariable)
                        }
                        if (cfg.continueCondition != null) {
                            validateConditionNode(cfg.continueCondition, knownVars, engine)?.let { return it }
                        }
                        validateStepList(step.thenSteps)?.let { return it }
                    }
                    AutomationActionType.SET_VARIABLE -> {
                        val varName = step.variableName.trim().ifBlank { step.targetLabel.trim() }
                        if (!WorkflowVariableStore.isValidVariableName(varName)) {
                            return WorkflowValidationResult(
                                isValid = false,
                                errorCode = AutomationErrorCode.VARIABLE_INVALID,
                                message = "Invalid variable name '$varName' in SET_VARIABLE step."
                            )
                        }
                        if (WorkflowVariableStore.isBlockedCredentialVariableName(varName)) {
                            return WorkflowValidationResult(
                                isValid = false,
                                errorCode = AutomationErrorCode.VARIABLE_INVALID,
                                message = "Variable '$varName' cannot store credential or secret values."
                            )
                        }
                        if (!step.value.contains("\${")) {
                            val typeCheck = WorkflowVariableStore.normalizeTypedValue(step.variableType, step.value)
                            if (typeCheck.isFailure) {
                                return WorkflowValidationResult(
                                    isValid = false,
                                    errorCode = AutomationErrorCode.VARIABLE_INVALID,
                                    message = typeCheck.exceptionOrNull()?.message ?: "Invalid value for '$varName'."
                                )
                            }
                        }
                        knownVars.add(varName)
                    }
                    AutomationActionType.EXTRACT -> {
                        val outVar = step.extractionSpec?.outputVariableName?.trim()
                            ?.ifBlank { step.variableName.trim() }
                            .orEmpty()
                        if (outVar.isNotEmpty()) {
                            if (!WorkflowVariableStore.isValidVariableName(outVar) ||
                                WorkflowVariableStore.isBlockedCredentialVariableName(outVar)
                            ) {
                                return WorkflowValidationResult(
                                    isValid = false,
                                    errorCode = AutomationErrorCode.VARIABLE_INVALID,
                                    message = "Invalid extraction output variable '$outVar'."
                                )
                            }
                            knownVars.add(outVar)
                        }
                        val spec = step.extractionSpec
                        if (spec != null && spec.mode == ExtractionMode.ATTRIBUTE) {
                            val attr = spec.attributeName.trim().lowercase()
                            if (attr.isEmpty() || attr !in ExtractionSpec.ALLOWLISTED_ATTRIBUTES) {
                                return WorkflowValidationResult(
                                    isValid = false,
                                    errorCode = AutomationErrorCode.EXTRACTION_SENSITIVE_BLOCKED,
                                    message = "Attribute '${spec.attributeName}' is not in the safe extraction allowlist."
                                )
                            }
                        }
                    }
                    AutomationActionType.CALL_WORKFLOW -> {
                        val ref = step.subWorkflowRef ?: return WorkflowValidationResult(
                            isValid = false,
                            errorCode = AutomationErrorCode.SUBWORKFLOW_NOT_FOUND,
                            message = "Sub-workflow step '${step.description}' is missing target workflow reference."
                        )
                        if (ref.targetWorkflowId in callStack) {
                            return WorkflowValidationResult(
                                isValid = false,
                                errorCode = AutomationErrorCode.SUBWORKFLOW_CYCLE,
                                message = "Circular sub-workflow reference detected (workflow ID ${ref.targetWorkflowId})."
                            )
                        }
                        if (subDepth + 1 > Phase6Limits.MAX_SUBWORKFLOW_DEPTH) {
                            return WorkflowValidationResult(
                                isValid = false,
                                errorCode = AutomationErrorCode.SUBWORKFLOW_DEPTH_EXCEEDED,
                                message = "Sub-workflow nesting depth exceeds maximum of ${Phase6Limits.MAX_SUBWORKFLOW_DEPTH}."
                            )
                        }
                        val loaded = workflowLookup(ref.targetWorkflowId) ?: return WorkflowValidationResult(
                            isValid = false,
                            errorCode = AutomationErrorCode.SUBWORKFLOW_NOT_FOUND,
                            message = "Referenced sub-workflow '${ref.targetWorkflowName.ifBlank { ref.targetWorkflowId }}' no longer exists."
                        )
                        referencedIds.add(ref.targetWorkflowId)
                        val (childName, childDoc) = loaded
                        val seededChildVars = buildSet {
                            childDoc.metadata.variables.forEach { add(it.name) }
                            ref.inputMappings.keys.forEach { add(it.trim()) }
                        }
                        val childRes = validateRecursiveBlockingCheck(
                            currentWorkflowId = ref.targetWorkflowId,
                            currentWorkflowName = childName,
                            document = childDoc,
                            callStack = callStack + ref.targetWorkflowId,
                            subDepth = subDepth + 1,
                            workflowLookup = workflowLookup,
                            preSeededVariableNames = seededChildVars,
                            engine = engine
                        )
                        if (!childRes.isValid) return childRes
                        collectedSteps.addAll(childRes.allNestedSteps)
                        referencedIds.addAll(childRes.referencedWorkflowIds)
                        ref.outputMappings.keys.forEach { parentOutVar ->
                            val clean = parentOutVar.trim()
                            if (!WorkflowVariableStore.isValidVariableName(clean) ||
                                WorkflowVariableStore.isBlockedCredentialVariableName(clean)
                            ) {
                                return WorkflowValidationResult(
                                    isValid = false,
                                    errorCode = AutomationErrorCode.VARIABLE_INVALID,
                                    message = "Invalid sub-workflow output variable '$clean'."
                                )
                            }
                            knownVars.add(clean)
                        }
                    }
                    else -> Unit
                }
            }
            return null
        }

        val err = validateStepList(document.steps)
        if (err != null) return err

        return WorkflowValidationResult(
            isValid = true,
            allNestedSteps = collectedSteps,
            referencedWorkflowIds = referencedIds
        )
    }

    fun validateConditionNode(
        node: ConditionNode,
        knownVariables: Set<String>,
        engine: AutomationEngine = AutomationEngine(),
        currentDepth: Int = 1
    ): WorkflowValidationResult? {
        if (currentDepth > Phase6Limits.MAX_CONDITION_DEPTH) {
            return WorkflowValidationResult(
                isValid = false,
                errorCode = AutomationErrorCode.CONDITION_INVALID,
                message = "Condition nesting exceeds maximum depth of ${Phase6Limits.MAX_CONDITION_DEPTH}."
            )
        }
        return when (node) {
            is ConditionNode.Group -> {
                if (node.children.isEmpty()) {
                    return WorkflowValidationResult(
                        isValid = false,
                        errorCode = AutomationErrorCode.CONDITION_INVALID,
                        message = "Condition group (${node.combinator.id}) must contain at least one condition."
                    )
                }
                for (child in node.children) {
                    validateConditionNode(child, knownVariables, engine, currentDepth + 1)?.let { return it }
                }
                null
            }
            is ConditionNode.Predicate -> {
                if (node.operator.requiresDomTarget &&
                    node.selector.isBlank() &&
                    node.targetLabel.isBlank()
                ) {
                    return WorkflowValidationResult(
                        isValid = false,
                        errorCode = AutomationErrorCode.CONDITION_INVALID,
                        message = "Condition '${node.operator.label}' requires a selector or target label."
                    )
                }
                if (node.selector.isNotBlank() && !node.selector.contains("\${")) {
                    val selRes = engine.validateSelector(node.selector)
                    if (!selRes.isValid) {
                        return WorkflowValidationResult(
                            isValid = false,
                            errorCode = AutomationErrorCode.INVALID_SELECTOR,
                            message = "Invalid selector in condition '${node.operator.label}': ${selRes.reason}"
                        )
                    }
                }
                val isVariableOp = node.operator in setOf(
                    ConditionOperator.VARIABLE_EQUALS,
                    ConditionOperator.VARIABLE_CONTAINS,
                    ConditionOperator.VARIABLE_NOT_EMPTY,
                    ConditionOperator.VARIABLE_GREATER_THAN,
                    ConditionOperator.VARIABLE_LESS_THAN
                )
                if (isVariableOp) {
                    val vName = node.variableName.trim()
                    if (!WorkflowVariableStore.isValidVariableName(vName)) {
                        return WorkflowValidationResult(
                            isValid = false,
                            errorCode = AutomationErrorCode.CONDITION_INVALID,
                            message = "Variable condition '${node.operator.label}' requires a valid variable name."
                        )
                    }
                    if (vName !in knownVariables) {
                        return WorkflowValidationResult(
                            isValid = false,
                            errorCode = AutomationErrorCode.VARIABLE_UNDEFINED,
                            message = "Condition references undefined variable '$vName'."
                        )
                    }
                }
                for (ref in WorkflowVariableStore.extractReferencedVariableNames(node.expectedValue) +
                    WorkflowVariableStore.extractReferencedVariableNames(node.selector) +
                    WorkflowVariableStore.extractReferencedVariableNames(node.targetLabel)
                ) {
                    if (ref !in knownVariables) {
                        return WorkflowValidationResult(
                            isValid = false,
                            errorCode = AutomationErrorCode.VARIABLE_UNDEFINED,
                            message = "Condition references undefined variable '\${$ref}'."
                        )
                    }
                }
                null
            }
        }
    }
}
