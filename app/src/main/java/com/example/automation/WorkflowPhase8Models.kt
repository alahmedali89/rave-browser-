package com.example.automation

import org.json.JSONArray

object WorkflowPhase8Constants {
    const val WORKFLOW_EXPORT_FORMAT_VERSION = 2
    const val MIN_SUPPORTED_EXPORT_FORMAT_VERSION = 1
    const val MAX_IMPORT_FILE_BYTES = 2 * 1024 * 1024 // 2 MB
    const val MAX_IMPORT_ZIP_UNCOMPRESSED_BYTES = 4 * 1024 * 1024 // 4 MB
    const val MAX_IMPORT_ZIP_ENTRIES = 64
    const val MAX_IMPORT_WORKFLOWS_PER_BATCH = 50
    const val MAX_IMPORT_STEPS_PER_WORKFLOW = 250
    const val MAX_STEP_NESTING_DEPTH = 12
}

class WorkflowConcurrencyConflictException(
    val workflowId: Long,
    val expectedVersion: Int,
    val actualVersion: Int,
    message: String = "Workflow $workflowId was modified concurrently (expected v$expectedVersion, current v$actualVersion)."
) : IllegalStateException(message)

data class WorkflowOrganizationMetadata(
    val folder: String = "",
    val category: String = "",
    val tags: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val archivedAt: Long? = null
) {
    companion object {
        fun encodeTagsJson(tags: List<String>): String {
            val clean = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            return JSONArray(clean).toString()
        }

        fun decodeTagsJson(raw: String?): List<String> {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) return emptyList()
            return runCatching {
                val arr = JSONArray(trimmed)
                buildList {
                    for (i in 0 until arr.length()) {
                        val tag = arr.optString(i, "").trim()
                        if (tag.isNotEmpty() && !contains(tag)) add(tag)
                    }
                }
            }.getOrElse {
                trimmed.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            }
        }
    }
}

enum class ValidationSeverity(val label: String, val blocksExecution: Boolean) {
    INFO("Info", false),
    WARNING("Warning", false),
    ERROR("Error", true),
    CRITICAL("Critical", true)
}

data class WorkflowValidationIssue(
    val severity: ValidationSeverity,
    val errorCode: AutomationErrorCode,
    val title: String,
    val explanation: String,
    val suggestedResolution: String,
    val stepId: String? = null,
    val stepOrder: Int? = null,
    val workflowId: Long? = null,
    val workflowName: String? = null
)

data class WorkflowValidationReport(
    val workflowId: Long = 0L,
    val workflowName: String = "",
    val isExecutionPermitted: Boolean,
    val issues: List<WorkflowValidationIssue> = emptyList(),
    val allNestedSteps: List<AutomationStep> = emptyList(),
    val referencedWorkflowIds: Set<Long> = emptySet(),
    val hasCredentialSteps: Boolean = false,
    val requiresCredentialRebinding: Boolean = false,
    val isArchived: Boolean = false
) {
    val criticalCount: Int get() = issues.count { it.severity == ValidationSeverity.CRITICAL }
    val errorCount: Int get() = issues.count { it.severity == ValidationSeverity.ERROR }
    val warningCount: Int get() = issues.count { it.severity == ValidationSeverity.WARNING }
    val infoCount: Int get() = issues.count { it.severity == ValidationSeverity.INFO }

    val firstBlockingIssue: WorkflowValidationIssue?
        get() = issues.firstOrNull { it.severity.blocksExecution }

    val summary: String
        get() = when {
            issues.isEmpty() -> "Validation passed with no issues."
            !isExecutionPermitted -> {
                val blocking = criticalCount + errorCount
                "$blocking blocking issue${if (blocking == 1) "" else "s"}, $warningCount warning${if (warningCount == 1) "" else "s"}"
            }
            warningCount > 0 -> "Executable with $warningCount warning${if (warningCount == 1) "" else "s"}"
            else -> "Validation passed ($infoCount note${if (infoCount == 1) "" else "s"})"
        }
}

enum class CompatibilityStatus {
    COMPATIBLE,
    MIGRATED,
    UNSUPPORTED_FUTURE,
    CORRUPTED
}

data class PortableWorkflowPayload(
    val workflowUuid: String,
    val legacyId: Long = 0L,
    val versionNumber: Int = 1,
    val name: String,
    val domain: String,
    val sourceUrl: String,
    val type: AutomationType,
    val instructionsText: String = "",
    val document: WorkflowDocument,
    val organization: WorkflowOrganizationMetadata = WorkflowOrganizationMetadata(),
    val dependencyUuids: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val unknownWorkflowFieldsJson: String = "",
    val unsupportedStepsCount: Int = 0
)

data class CompatibilityCheckResult(
    val status: CompatibilityStatus,
    val sourceFormatVersion: Int = WorkflowPhase8Constants.WORKFLOW_EXPORT_FORMAT_VERSION,
    val targetFormatVersion: Int = WorkflowPhase8Constants.WORKFLOW_EXPORT_FORMAT_VERSION,
    val packageType: ExportPackageType = ExportPackageType.SINGLE,
    val workflows: List<PortableWorkflowPayload> = emptyList(),
    val migrationNotes: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val errorMessage: String = ""
) {
    val isSupported: Boolean
        get() = status == CompatibilityStatus.COMPATIBLE || status == CompatibilityStatus.MIGRATED
}

enum class DiffChangeKind(val label: String) {
    ADDED("Added"),
    REMOVED("Removed"),
    MODIFIED("Modified"),
    REORDERED("Reordered"),
    UNCHANGED("Unchanged")
}

data class WorkflowStepDiffItem(
    val stepId: String,
    val kind: DiffChangeKind,
    val title: String,
    val beforeOrder: Int? = null,
    val afterOrder: Int? = null,
    val beforeActionLabel: String = "",
    val afterActionLabel: String = "",
    val fieldChanges: List<String> = emptyList()
)

data class WorkflowDiffReport(
    val baseLabel: String,
    val targetLabel: String,
    val metadataChanges: List<String> = emptyList(),
    val stepDiffs: List<WorkflowStepDiffItem> = emptyList()
) {
    val addedCount: Int get() = stepDiffs.count { it.kind == DiffChangeKind.ADDED }
    val removedCount: Int get() = stepDiffs.count { it.kind == DiffChangeKind.REMOVED }
    val modifiedCount: Int get() = stepDiffs.count { it.kind == DiffChangeKind.MODIFIED }
    val reorderedCount: Int get() = stepDiffs.count { it.kind == DiffChangeKind.REORDERED }
    val unchangedCount: Int get() = stepDiffs.count { it.kind == DiffChangeKind.UNCHANGED }
    val hasChanges: Boolean
        get() = metadataChanges.isNotEmpty() || addedCount > 0 || removedCount > 0 || modifiedCount > 0 || reorderedCount > 0

    val summary: String
        get() = if (!hasChanges) {
            "No semantic differences between $baseLabel and $targetLabel."
        } else {
            buildList {
                if (metadataChanges.isNotEmpty()) add("${metadataChanges.size} metadata")
                if (addedCount > 0) add("+$addedCount added")
                if (removedCount > 0) add("-$removedCount removed")
                if (modifiedCount > 0) add("~$modifiedCount modified")
                if (reorderedCount > 0) add("↕$reorderedCount reordered")
            }.joinToString(" • ")
        }
}

enum class ExportPackageType(val id: String, val label: String) {
    SINGLE("SINGLE", "Single Workflow"),
    SINGLE_WITH_DEPENDENCIES("SINGLE_WITH_DEPENDENCIES", "Workflow + Dependencies"),
    COLLECTION("COLLECTION", "Workflow Collection");

    companion object {
        fun fromId(raw: String?): ExportPackageType =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: COLLECTION
    }
}

enum class WorkflowConflictResolution(val id: String, val label: String) {
    CREATE_COPY("CREATE_COPY", "Import as Copy"),
    SKIP_EXISTING("SKIP_EXISTING", "Skip Matching UUID"),
    NEW_VERSION_IF_MATCH("NEW_VERSION_IF_MATCH", "Append New Version")
}

data class WorkflowImportResult(
    val success: Boolean,
    val importedCount: Int = 0,
    val updatedCount: Int = 0,
    val skippedCount: Int = 0,
    val importedWorkflowIds: List<Long> = emptyList(),
    val rebindRequiredWorkflowIds: List<Long> = emptyList(),
    val rebindRequiredStepCount: Int = 0,
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
    val migrationNotes: List<String> = emptyList()
) {
    val summary: String
        get() = if (!success) {
            errors.firstOrNull() ?: "Import failed and all changes were rolled back."
        } else {
            buildList {
                if (importedCount > 0) add("$importedCount imported")
                if (updatedCount > 0) add("$updatedCount updated (new version)")
                if (skippedCount > 0) add("$skippedCount skipped")
                if (rebindRequiredStepCount > 0) add("$rebindRequiredStepCount credential step(s) require Vault rebinding")
            }.ifEmpty { listOf("No workflows imported") }.joinToString(" • ")
        }
}

enum class DryRunStepStatus(val label: String) {
    PLANNED("Planned"),
    EVALUATED_TRUE("Condition True"),
    EVALUATED_FALSE("Condition False"),
    BLOCKED("Blocked"),
    WARNING("Warning"),
    REQUIRES_CREDENTIAL_AUTH("Requires Auth"),
    REQUIRES_CREDENTIAL_REBIND("Requires Credential Rebind"),
    UNVERIFIED_DOM("Unverified DOM Target")
}

data class DryRunPlannedStep(
    val stepId: String,
    val stepOrder: Int,
    val depth: Int = 0,
    val actionType: AutomationActionType,
    val actionLabel: String,
    val description: String,
    val status: DryRunStepStatus,
    val targetSummary: String,
    val originNote: String = "",
    val variableSubstitutions: Map<String, String> = emptyMap(),
    val resolvedConditionResult: Boolean? = null,
    val domVerificationNote: String = "",
    val notes: List<String> = emptyList()
)

data class WorkflowDryRunReport(
    val workflowId: Long,
    val workflowName: String,
    val versionNumber: Int,
    val isSafeToRun: Boolean,
    val validationReport: WorkflowValidationReport,
    val resolvedSubWorkflowOrder: List<String> = emptyList(),
    val plannedSteps: List<DryRunPlannedStep> = emptyList(),
    val unresolvedVariables: List<String> = emptyList(),
    val missingDependencies: List<String> = emptyList(),
    val credentialStepsRequiringAuth: List<String> = emptyList(),
    val credentialStepsRequiringRebind: List<String> = emptyList(),
    val originTransitions: List<String> = emptyList(),
    val destructiveActions: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val simulatedFinalVariables: Map<String, String> = emptyMap()
) {
    val totalPlannedActions: Int get() = plannedSteps.size
    val summary: String
        get() = buildString {
            append(if (isSafeToRun) "Dry-run ready" else "Dry-run blocked")
            append(" • ${plannedSteps.size} planned step${if (plannedSteps.size == 1) "" else "s"}")
            if (unresolvedVariables.isNotEmpty()) append(" • ${unresolvedVariables.size} unresolved var(s)")
            if (credentialStepsRequiringRebind.isNotEmpty()) append(" • ${credentialStepsRequiringRebind.size} cred rebind(s)")
            if (destructiveActions.isNotEmpty()) append(" • ${destructiveActions.size} destructive")
        }
}
