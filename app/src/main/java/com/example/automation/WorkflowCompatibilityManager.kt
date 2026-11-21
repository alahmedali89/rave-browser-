package com.example.automation

import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * Validates and non-destructively migrates persisted WorkflowDocument payloads and
 * portable workflow export files across schema/format revisions without mutating unknown steps or metadata.
 */
object WorkflowCompatibilityManager {

    private val RESERVED_WORKFLOW_KEYS = setOf(
        "formatVersion", "schemaVersion", "packageType", "workflowUuid", "legacyId",
        "versionNumber", "name", "domain", "sourceUrl", "type", "instructionsText",
        "steps", "phase6", "organization", "dependencies", "createdAt", "updatedAt"
    )

    fun decodeAndMigrateStoredDocument(
        rawStepsJson: String?,
        allowUpgradeNotification: Boolean = true
    ): Pair<WorkflowDocument, List<String>> {
        val raw = rawStepsJson?.trim().orEmpty()
        if (raw.isEmpty()) {
            return WorkflowDocument() to emptyList()
        }
        return when (val decoded = AutomationStepCodec.decodeSafely(raw)) {
            is AutomationStepDecodeResult.Success -> {
                val notes = mutableListOf<String>()
                if (decoded.isLegacyUnversioned && allowUpgradeNotification) {
                    notes.add("Migrated legacy unversioned steps into schema v${AutomationStepCodec.CURRENT_SCHEMA_VERSION}.")
                }
                val unsupportedCount = decoded.steps.flatMap { it.collectNestedSteps() }
                    .count { it.actionType == AutomationActionType.UNSUPPORTED }
                if (unsupportedCount > 0) {
                    notes.add("Preserved $unsupportedCount unsupported/future action step(s) non-destructively.")
                }
                WorkflowDocument(
                    schemaVersion = decoded.schemaVersion,
                    steps = decoded.steps,
                    metadata = decoded.metadata,
                    isLegacyUnversioned = decoded.isLegacyUnversioned
                ) to notes
            }
            is AutomationStepDecodeResult.UnsupportedVersion -> {
                WorkflowDocument() to listOf(decoded.message)
            }
            is AutomationStepDecodeResult.MalformedJson -> {
                WorkflowDocument() to listOf(decoded.message)
            }
        }
    }

    fun parseAndMigrateExportEnvelope(rawJson: String): CompatibilityCheckResult {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) {
            return CompatibilityCheckResult(
                status = CompatibilityStatus.CORRUPTED,
                errorMessage = "Import payload is empty."
            )
        }
        if (trimmed.toByteArray(Charsets.UTF_8).size > WorkflowPhase8Constants.MAX_IMPORT_FILE_BYTES) {
            return CompatibilityCheckResult(
                status = CompatibilityStatus.CORRUPTED,
                errorMessage = "Import payload exceeds maximum allowed size of ${WorkflowPhase8Constants.MAX_IMPORT_FILE_BYTES / (1024 * 1024)} MB."
            )
        }

        val migrationNotes = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        return runCatching {
            when {
                trimmed.startsWith("[") -> {
                    val arr = JSONArray(trimmed)
                    if (arr.length() > WorkflowPhase8Constants.MAX_IMPORT_WORKFLOWS_PER_BATCH) {
                        return CompatibilityCheckResult(
                            status = CompatibilityStatus.CORRUPTED,
                            errorMessage = "Import bundle exceeds maximum of ${WorkflowPhase8Constants.MAX_IMPORT_WORKFLOWS_PER_BATCH} workflows."
                        )
                    }
                    migrationNotes.add("Migrated legacy Phase 1–7 array export to portable format v${WorkflowPhase8Constants.WORKFLOW_EXPORT_FORMAT_VERSION}.")
                    val parsed = parseWorkflowArray(arr, defaultFormatVersion = 1, migrationNotes = migrationNotes, warnings = warnings)
                        ?: return CompatibilityCheckResult(
                            status = CompatibilityStatus.UNSUPPORTED_FUTURE,
                            sourceFormatVersion = 99,
                            errorMessage = "Bundle contains an unsupported future schema version."
                        )
                    CompatibilityCheckResult(
                        status = CompatibilityStatus.MIGRATED,
                        sourceFormatVersion = 1,
                        packageType = ExportPackageType.COLLECTION,
                        workflows = parsed,
                        migrationNotes = migrationNotes,
                        warnings = warnings
                    )
                }
                trimmed.startsWith("{") -> {
                    val root = JSONObject(trimmed)
                    if (root.has("formatVersion")) {
                        val fmtVer = (root.opt("formatVersion") as? Number)?.toInt() ?: -1
                        if (fmtVer < WorkflowPhase8Constants.MIN_SUPPORTED_EXPORT_FORMAT_VERSION ||
                            fmtVer > WorkflowPhase8Constants.WORKFLOW_EXPORT_FORMAT_VERSION
                        ) {
                            return CompatibilityCheckResult(
                                status = CompatibilityStatus.UNSUPPORTED_FUTURE,
                                sourceFormatVersion = fmtVer,
                                errorMessage = "Unsupported workflow package format v$fmtVer (supported: v${WorkflowPhase8Constants.MIN_SUPPORTED_EXPORT_FORMAT_VERSION}–v${WorkflowPhase8Constants.WORKFLOW_EXPORT_FORMAT_VERSION})."
                            )
                        }
                        val workflowsArr = root.optJSONArray("workflows")
                            ?: return CompatibilityCheckResult(
                                status = CompatibilityStatus.CORRUPTED,
                                sourceFormatVersion = fmtVer,
                                errorMessage = "Versioned workflow package is missing 'workflows' array."
                            )
                        if (workflowsArr.length() > WorkflowPhase8Constants.MAX_IMPORT_WORKFLOWS_PER_BATCH) {
                            return CompatibilityCheckResult(
                                status = CompatibilityStatus.CORRUPTED,
                                errorMessage = "Import bundle exceeds maximum of ${WorkflowPhase8Constants.MAX_IMPORT_WORKFLOWS_PER_BATCH} workflows."
                            )
                        }
                        val pkgType = ExportPackageType.fromId(root.optString("packageType"))
                        if (fmtVer < WorkflowPhase8Constants.WORKFLOW_EXPORT_FORMAT_VERSION) {
                            migrationNotes.add("Migrated package format v$fmtVer to v${WorkflowPhase8Constants.WORKFLOW_EXPORT_FORMAT_VERSION}.")
                        }
                        val parsed = parseWorkflowArray(workflowsArr, defaultFormatVersion = fmtVer, migrationNotes = migrationNotes, warnings = warnings)
                            ?: return CompatibilityCheckResult(
                                status = CompatibilityStatus.UNSUPPORTED_FUTURE,
                                sourceFormatVersion = fmtVer,
                                errorMessage = "Bundle contains a workflow with an unsupported future schema version."
                            )
                        CompatibilityCheckResult(
                            status = if (fmtVer < WorkflowPhase8Constants.WORKFLOW_EXPORT_FORMAT_VERSION || migrationNotes.isNotEmpty()) {
                                CompatibilityStatus.MIGRATED
                            } else {
                                CompatibilityStatus.COMPATIBLE
                            },
                            sourceFormatVersion = fmtVer,
                            packageType = pkgType,
                            workflows = parsed,
                            migrationNotes = migrationNotes,
                            warnings = warnings
                        )
                    } else if (root.has("name") && root.has("steps")) {
                        // Single legacy v1 workflow object
                        val singleArr = JSONArray().put(root)
                        migrationNotes.add("Migrated single workflow export object to portable format v${WorkflowPhase8Constants.WORKFLOW_EXPORT_FORMAT_VERSION}.")
                        val parsed = parseWorkflowArray(singleArr, defaultFormatVersion = 1, migrationNotes = migrationNotes, warnings = warnings)
                            ?: return CompatibilityCheckResult(
                                status = CompatibilityStatus.UNSUPPORTED_FUTURE,
                                sourceFormatVersion = 99,
                                errorMessage = "Workflow uses an unsupported future schema version."
                            )
                        CompatibilityCheckResult(
                            status = CompatibilityStatus.MIGRATED,
                            sourceFormatVersion = 1,
                            packageType = ExportPackageType.SINGLE,
                            workflows = parsed,
                            migrationNotes = migrationNotes,
                            warnings = warnings
                        )
                    } else {
                        CompatibilityCheckResult(
                            status = CompatibilityStatus.CORRUPTED,
                            errorMessage = "Unrecognized workflow JSON envelope structure."
                        )
                    }
                }
                else -> CompatibilityCheckResult(
                    status = CompatibilityStatus.CORRUPTED,
                    errorMessage = "Workflow import payload must be a JSON object or array."
                )
            }
        }.getOrElse { ex ->
            CompatibilityCheckResult(
                status = CompatibilityStatus.CORRUPTED,
                errorMessage = "Malformed workflow JSON: ${ex.message ?: "parse error"}"
            )
        }
    }

    private fun parseWorkflowArray(
        arr: JSONArray,
        defaultFormatVersion: Int,
        migrationNotes: MutableList<String>,
        warnings: MutableList<String>
    ): List<PortableWorkflowPayload>? {
        val result = mutableListOf<PortableWorkflowPayload>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i)
                ?: throw IllegalArgumentException("Workflow entry at index $i is not a JSON object.")
            if (obj.has("schemaVersion")) {
                val sv = (obj.opt("schemaVersion") as? Number)?.toInt() ?: -1
                if (sv < 1 || sv > AutomationStepCodec.CURRENT_SCHEMA_VERSION) {
                    return null
                }
            }
            val name = obj.optString("name", "").trim()
            if (name.isEmpty()) {
                throw IllegalArgumentException("Workflow entry at index $i has an empty name.")
            }

            val rawStepsValue = obj.opt("steps")
            val decodeRes = when (rawStepsValue) {
                is JSONObject -> AutomationStepCodec.decodeSafely(rawStepsValue.toString())
                is JSONArray -> AutomationStepCodec.decodeSafely(rawStepsValue.toString())
                is String -> AutomationStepCodec.decodeSafely(rawStepsValue)
                null -> throw IllegalArgumentException("Workflow '$name' is missing 'steps'.")
                else -> throw IllegalArgumentException("Workflow '$name' has invalid 'steps' format.")
            }
            val (decodedSteps, embeddedMeta, isLegacy) = when (decodeRes) {
                is AutomationStepDecodeResult.Success ->
                    Triple(decodeRes.steps, decodeRes.metadata, decodeRes.isLegacyUnversioned)
                is AutomationStepDecodeResult.UnsupportedVersion -> return null
                is AutomationStepDecodeResult.MalformedJson ->
                    throw IllegalArgumentException("Workflow '$name' steps invalid: ${decodeRes.message}")
            }
            if (isLegacy) {
                migrationNotes.add("Upgraded unversioned steps in '$name' to step schema v${AutomationStepCodec.CURRENT_SCHEMA_VERSION}.")
            }

            val nestedCount = decodedSteps.sumOf { it.collectNestedSteps().size }
            if (nestedCount > WorkflowPhase8Constants.MAX_IMPORT_STEPS_PER_WORKFLOW) {
                throw IllegalArgumentException("Workflow '$name' exceeds maximum step limit (${WorkflowPhase8Constants.MAX_IMPORT_STEPS_PER_WORKFLOW}).")
            }
            val maxDepth = decodedSteps.maxOfOrNull { computeStepAstDepth(it) } ?: 0
            if (maxDepth > WorkflowPhase8Constants.MAX_STEP_NESTING_DEPTH) {
                throw IllegalArgumentException("Workflow '$name' exceeds maximum nesting depth (${WorkflowPhase8Constants.MAX_STEP_NESTING_DEPTH}).")
            }

            val phase6Obj = obj.optJSONObject("phase6")
            val meta = if (phase6Obj != null) WorkflowDocumentMetadata.fromJson(phase6Obj) else embeddedMeta

            val unsupportedCount = decodedSteps.flatMap { it.collectNestedSteps() }
                .count { it.actionType == AutomationActionType.UNSUPPORTED }
            if (unsupportedCount > 0) {
                warnings.add("Workflow '$name' contains $unsupportedCount unsupported/future action step(s) preserved safely.")
            }

            val orgObj = obj.optJSONObject("organization")
            val org = if (orgObj != null) {
                val tagsArr = orgObj.optJSONArray("tags")
                val tags = if (tagsArr != null) {
                    buildList {
                        for (t in 0 until tagsArr.length()) {
                            tagsArr.optString(t, "").trim().takeIf { it.isNotEmpty() }?.let { add(it) }
                        }
                    }
                } else {
                    emptyList()
                }
                WorkflowOrganizationMetadata(
                    folder = orgObj.optString("folder", "").trim(),
                    category = orgObj.optString("category", "").trim(),
                    tags = tags,
                    isFavorite = orgObj.optBoolean("isFavorite", false),
                    isArchived = orgObj.optBoolean("isArchived", false)
                )
            } else {
                WorkflowOrganizationMetadata()
            }

            val depArr = obj.optJSONArray("dependencies")
            val deps = if (depArr != null) {
                buildList {
                    for (d in 0 until depArr.length()) {
                        depArr.optString(d, "").trim().takeIf { it.isNotEmpty() }?.let { add(it) }
                    }
                }
            } else {
                emptyList()
            }

            val extraWorkflowFields = JSONObject().apply {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (k !in RESERVED_WORKFLOW_KEYS && !isSensitiveFieldName(k)) {
                        put(k, obj.opt(k))
                    }
                }
            }

            val wfUuid = obj.optString("workflowUuid", "").trim()
                .ifBlank { UUID.randomUUID().toString() }
            result.add(
                PortableWorkflowPayload(
                    workflowUuid = wfUuid,
                    legacyId = obj.optLong("legacyId", 0L),
                    versionNumber = obj.optInt("versionNumber", defaultFormatVersion.coerceAtLeast(1)).coerceAtLeast(1),
                    name = name,
                    domain = obj.optString("domain", "All sites").trim().ifBlank { "All sites" },
                    sourceUrl = obj.optString("sourceUrl", "").trim(),
                    type = AutomationType.fromId(obj.optString("type")),
                    instructionsText = obj.optString("instructionsText", ""),
                    document = WorkflowDocument(
                        schemaVersion = AutomationStepCodec.CURRENT_SCHEMA_VERSION,
                        steps = decodedSteps,
                        metadata = meta,
                        isLegacyUnversioned = false
                    ),
                    organization = org,
                    dependencyUuids = deps,
                    createdAt = obj.optLong("createdAt", 0L),
                    updatedAt = obj.optLong("updatedAt", 0L),
                    unknownWorkflowFieldsJson = if (extraWorkflowFields.length() > 0) extraWorkflowFields.toString() else "",
                    unsupportedStepsCount = unsupportedCount
                )
            )
        }
        return result
    }

    private fun computeStepAstDepth(step: AutomationStep, current: Int = 1): Int {
        val children = buildList {
            addAll(step.thenSteps)
            step.elseIfBranches.forEach { addAll(it.steps) }
            addAll(step.elseSteps)
            step.recoveryPolicy?.alternativeSteps?.let { addAll(it) }
        }
        if (children.isEmpty()) return current
        return children.maxOf { computeStepAstDepth(it, current + 1) }
    }

    internal fun isSensitiveFieldName(key: String): Boolean {
        val lower = key.lowercase()
        return lower.contains("password") ||
            lower.contains("secret") ||
            lower.contains("token") ||
            lower.contains("apikey") ||
            lower.contains("api_key") ||
            lower.contains("session") ||
            lower.contains("cookie") ||
            lower.contains("keystore") ||
            lower.contains("privatekey") ||
            lower.contains("credential")
    }
}
