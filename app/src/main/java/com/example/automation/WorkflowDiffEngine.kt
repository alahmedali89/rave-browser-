package com.example.automation

import com.example.data.local.AutomationWorkflowVersionEntity

/**
 * Computes semantic diffs between two WorkflowDocuments or saved WorkflowVersions
 * using stable step identifiers rather than fragile raw JSON text comparisons.
 */
object WorkflowDiffEngine {

    fun compareVersions(
        base: AutomationWorkflowVersionEntity,
        target: AutomationWorkflowVersionEntity
    ): WorkflowDiffReport {
        val baseDoc = AutomationStepCodec.decodeDocument(base.stepsJson)
        val targetDoc = AutomationStepCodec.decodeDocument(target.stepsJson)
        return compareDocuments(
            baseLabel = "v${base.versionNumber} (${base.workflowName})",
            targetLabel = "v${target.versionNumber} (${target.workflowName})",
            baseName = base.workflowName,
            targetName = target.workflowName,
            baseDomain = base.domain,
            targetDomain = target.domain,
            baseSourceUrl = base.sourceUrl,
            targetSourceUrl = target.sourceUrl,
            baseType = base.type,
            targetType = target.type,
            baseDoc = baseDoc,
            targetDoc = targetDoc
        )
    }

    fun compareDocuments(
        baseLabel: String = "Previous",
        targetLabel: String = "Current",
        baseName: String = "",
        targetName: String = "",
        baseDomain: String = "",
        targetDomain: String = "",
        baseSourceUrl: String = "",
        targetSourceUrl: String = "",
        baseType: String = "",
        targetType: String = "",
        baseDoc: WorkflowDocument,
        targetDoc: WorkflowDocument
    ): WorkflowDiffReport {
        val metadataChanges = mutableListOf<String>()
        if (baseName.isNotBlank() && targetName.isNotBlank() && baseName != targetName) {
            metadataChanges.add("Name changed: '$baseName' → '$targetName'")
        }
        if (baseDomain.isNotBlank() && targetDomain.isNotBlank() && baseDomain != targetDomain) {
            metadataChanges.add("Domain changed: '$baseDomain' → '$targetDomain'")
        }
        if (baseSourceUrl != targetSourceUrl && (baseSourceUrl.isNotBlank() || targetSourceUrl.isNotBlank())) {
            metadataChanges.add("Source URL changed: '${baseSourceUrl.ifBlank { "none" }}' → '${targetSourceUrl.ifBlank { "none" }}'")
        }
        if (baseType.isNotBlank() && targetType.isNotBlank() && baseType != targetType) {
            metadataChanges.add("Workflow type changed: $baseType → $targetType")
        }

        val baseVars = baseDoc.metadata.variables.associateBy { it.name }
        val targetVars = targetDoc.metadata.variables.associateBy { it.name }
        for (v in targetVars.keys - baseVars.keys) {
            metadataChanges.add("Variable added: \${$v} (${targetVars[v]?.type?.label})")
        }
        for (v in baseVars.keys - targetVars.keys) {
            metadataChanges.add("Variable removed: \${$v}")
        }
        for (v in baseVars.keys.intersect(targetVars.keys)) {
            val bv = baseVars.getValue(v)
            val tv = targetVars.getValue(v)
            if (bv.type != tv.type || bv.defaultValue != tv.defaultValue || bv.isInput != tv.isInput || bv.isOutput != tv.isOutput) {
                val safeBefore = if (WorkflowVariableStore.shouldMaskInUi(v)) "••••" else bv.defaultValue
                val safeAfter = if (WorkflowVariableStore.shouldMaskInUi(v)) "••••" else tv.defaultValue
                metadataChanges.add("Variable updated: \${$v} (${bv.type.id} '$safeBefore' → ${tv.type.id} '$safeAfter')")
            }
        }

        if (baseDoc.metadata.allowedOrigins != targetDoc.metadata.allowedOrigins) {
            metadataChanges.add("Allowed origins updated: ${baseDoc.metadata.allowedOrigins} → ${targetDoc.metadata.allowedOrigins}")
        }
        if (baseDoc.metadata.allowMultiPageContinuity != targetDoc.metadata.allowMultiPageContinuity) {
            metadataChanges.add("Multi-page continuity: ${baseDoc.metadata.allowMultiPageContinuity} → ${targetDoc.metadata.allowMultiPageContinuity}")
        }
        val baseRecovery = baseDoc.metadata.defaultRecoveryPolicy?.toJson()?.toString().orEmpty()
        val targetRecovery = targetDoc.metadata.defaultRecoveryPolicy?.toJson()?.toString().orEmpty()
        if (baseRecovery != targetRecovery) {
            metadataChanges.add("Default recovery policy changed")
        }

        val stepDiffs = diffStepLists(baseDoc.steps, targetDoc.steps)
        return WorkflowDiffReport(
            baseLabel = baseLabel,
            targetLabel = targetLabel,
            metadataChanges = metadataChanges,
            stepDiffs = stepDiffs
        )
    }

    fun diffStepLists(
        beforeSteps: List<AutomationStep>,
        afterSteps: List<AutomationStep>
    ): List<WorkflowStepDiffItem> {
        val beforeById = beforeSteps.withIndex().associate { it.value.id to (it.index to it.value) }
        val afterById = afterSteps.withIndex().associate { it.value.id to (it.index to it.value) }
        val items = mutableListOf<WorkflowStepDiffItem>()
        val emittedIds = mutableSetOf<String>()

        // Walk afterSteps in target order, comparing matched IDs and marking ADDED / MODIFIED / REORDERED / UNCHANGED
        for ((afterIdx, afterStep) in afterSteps.withIndex()) {
            emittedIds.add(afterStep.id)
            val match = beforeById[afterStep.id]
            if (match == null) {
                items.add(
                    WorkflowStepDiffItem(
                        stepId = afterStep.id,
                        kind = DiffChangeKind.ADDED,
                        title = afterStep.description.ifBlank { afterStep.targetLabel.ifBlank { afterStep.actionType.label } },
                        beforeOrder = null,
                        afterOrder = afterIdx + 1,
                        afterActionLabel = afterStep.actionType.label,
                        fieldChanges = listOf("Added step '${afterStep.actionType.label}' at position #${afterIdx + 1}")
                    )
                )
            } else {
                val (beforeIdx, beforeStep) = match
                val changes = describeSemanticStepChanges(beforeStep, afterStep, beforeIdx, afterIdx)
                val orderChanged = beforeIdx != afterIdx
                val kind = when {
                    changes.any { !it.startsWith("Order shifted:") } -> DiffChangeKind.MODIFIED
                    orderChanged -> DiffChangeKind.REORDERED
                    else -> DiffChangeKind.UNCHANGED
                }
                items.add(
                    WorkflowStepDiffItem(
                        stepId = afterStep.id,
                        kind = kind,
                        title = afterStep.description.ifBlank { beforeStep.description },
                        beforeOrder = beforeIdx + 1,
                        afterOrder = afterIdx + 1,
                        beforeActionLabel = beforeStep.actionType.label,
                        afterActionLabel = afterStep.actionType.label,
                        fieldChanges = changes
                    )
                )
            }
        }

        // Emit REMOVED steps in original order
        for ((beforeIdx, beforeStep) in beforeSteps.withIndex()) {
            if (beforeStep.id !in afterById && emittedIds.add(beforeStep.id)) {
                items.add(
                    WorkflowStepDiffItem(
                        stepId = beforeStep.id,
                        kind = DiffChangeKind.REMOVED,
                        title = beforeStep.description.ifBlank { beforeStep.targetLabel.ifBlank { beforeStep.actionType.label } },
                        beforeOrder = beforeIdx + 1,
                        afterOrder = null,
                        beforeActionLabel = beforeStep.actionType.label,
                        fieldChanges = listOf("Removed step '${beforeStep.actionType.label}' (was #${beforeIdx + 1})")
                    )
                )
            }
        }

        return items
    }

    private fun describeSemanticStepChanges(
        before: AutomationStep,
        after: AutomationStep,
        beforeIdx: Int,
        afterIdx: Int
    ): List<String> {
        val changes = mutableListOf<String>()
        if (before.actionType != after.actionType || before.rawActionType != after.rawActionType) {
            val bAct = before.rawActionType.ifBlank { before.actionType.id }
            val aAct = after.rawActionType.ifBlank { after.actionType.id }
            changes.add("Action type: $bAct → $aAct")
        }
        if (before.description != after.description) {
            changes.add("Description updated")
        }
        if (before.targetLabel != after.targetLabel) {
            changes.add("Target label: '${before.targetLabel}' → '${after.targetLabel}'")
        }
        if (before.selector != after.selector ||
            before.locatorCandidates.map { it.selector } != after.locatorCandidates.map { it.selector }
        ) {
            changes.add("Locator changed: '${before.selector.ifBlank { "label-only" }}' → '${after.selector.ifBlank { "label-only" }}'")
        }
        if (before.isCredentialStep || after.isCredentialStep) {
            if (before.credentialReferenceId != after.credentialReferenceId) {
                changes.add("Credential binding changed")
            }
            if (before.credentialFieldRole != after.credentialFieldRole) {
                changes.add("Credential field role: ${before.credentialFieldRole?.id ?: "unset"} → ${after.credentialFieldRole?.id ?: "unset"}")
            }
            if (before.expectedOrigin != after.expectedOrigin) {
                changes.add("Expected origin: '${before.expectedOrigin}' → '${after.expectedOrigin}'")
            }
        } else if (before.value != after.value) {
            changes.add("Input/parameter value changed")
        }
        if (before.variableName != after.variableName || before.variableType != after.variableType) {
            changes.add("Variable binding: ${before.variableName}(${before.variableType.id}) → ${after.variableName}(${after.variableType.id})")
        }
        val bCond = before.condition?.toJson()?.toString().orEmpty()
        val aCond = after.condition?.toJson()?.toString().orEmpty()
        if (bCond != aCond) {
            changes.add("Condition changed: '${before.condition?.toHumanSummary() ?: "none"}' → '${after.condition?.toHumanSummary() ?: "none"}'")
        }
        val bLoop = before.loopConfig?.toJson()?.toString().orEmpty()
        val aLoop = after.loopConfig?.toJson()?.toString().orEmpty()
        if (bLoop != aLoop) {
            changes.add("Loop configuration changed (max ${before.loopConfig?.maxIterations ?: 0} → ${after.loopConfig?.maxIterations ?: 0})")
        }
        val bSub = before.subWorkflowRef?.toJson()?.toString().orEmpty()
        val aSub = after.subWorkflowRef?.toJson()?.toString().orEmpty()
        if (bSub != aSub) {
            changes.add("Sub-workflow reference changed: '${before.subWorkflowRef?.targetWorkflowName ?: before.subWorkflowRef?.targetWorkflowId}' → '${after.subWorkflowRef?.targetWorkflowName ?: after.subWorkflowRef?.targetWorkflowId}'")
        }
        val bExt = before.extractionSpec?.toJson()?.toString().orEmpty()
        val aExt = after.extractionSpec?.toJson()?.toString().orEmpty()
        if (bExt != aExt) {
            changes.add("Extraction specification changed")
        }
        val bRec = before.recoveryPolicy?.toJson()?.toString().orEmpty()
        val aRec = after.recoveryPolicy?.toJson()?.toString().orEmpty()
        if (bRec != aRec) {
            changes.add("Recovery policy changed: ${before.recoveryPolicy?.strategy?.id ?: "none"} → ${after.recoveryPolicy?.strategy?.id ?: "none"}")
        }
        val bWait = before.waitCondition?.toJson()?.toString().orEmpty()
        val aWait = after.waitCondition?.toJson()?.toString().orEmpty()
        if (bWait != aWait) {
            changes.add("Wait condition changed")
        }
        if (before.isOptional != after.isOptional || before.isDestructive != after.isDestructive) {
            changes.add("Flags changed (optional=${after.isOptional}, destructive=${after.isDestructive})")
        }
        val thenDiff = diffStepLists(before.thenSteps, after.thenSteps).filter { it.kind != DiffChangeKind.UNCHANGED }
        if (thenDiff.isNotEmpty()) {
            changes.add("Nested 'then' branch: ${thenDiff.size} step change(s)")
        }
        val elseDiff = diffStepLists(before.elseSteps, after.elseSteps).filter { it.kind != DiffChangeKind.UNCHANGED }
        if (elseDiff.isNotEmpty()) {
            changes.add("Nested 'else' branch: ${elseDiff.size} step change(s)")
        }
        if (before.elseIfBranches.map { it.toJson().toString() } != after.elseIfBranches.map { it.toJson().toString() }) {
            changes.add("Nested 'else-if' branches modified")
        }
        if (beforeIdx != afterIdx) {
            changes.add("Order shifted: #${beforeIdx + 1} → #${afterIdx + 1}")
        }
        return changes
    }
}
