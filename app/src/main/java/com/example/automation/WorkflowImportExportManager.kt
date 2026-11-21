package com.example.automation

import com.example.data.local.ApexDao
import com.example.data.local.AutomationWorkflowEntity
import com.example.data.local.AutomationWorkflowVersionEntity
import com.example.data.local.PreparedWorkflowImportRecord
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages structured, versioned, credential-scrubbed workflow export and validated atomic import
 * in both JSON and ZIP archive formats, including transitive sub-workflow dependency bundling.
 */
class WorkflowImportExportManager(
    private val dao: ApexDao,
    private val versionManager: WorkflowVersionManager,
    private val engine: AutomationEngine = AutomationEngine()
) {
    suspend fun exportWorkflowPackageJson(
        rootWorkflowId: Long,
        includeDependencies: Boolean
    ): String {
        val packageType = if (includeDependencies) {
            ExportPackageType.SINGLE_WITH_DEPENDENCIES
        } else {
            ExportPackageType.SINGLE
        }
        val selected = collectWorkflowsForExport(
            workflowIds = listOf(rootWorkflowId),
            includeDependencies = includeDependencies
        )
        return buildExportEnvelope(selected, packageType).toString(2)
    }

    suspend fun exportCollectionPackageJson(
        workflowIds: List<Long>? = null,
        includeDependencies: Boolean = true
    ): String {
        val all = dao.getAutomationWorkflowsOnce()
        val targetIds = if (workflowIds == null) {
            all.filter { !it.isArchived }.map { it.id }.ifEmpty { all.map { it.id } }
        } else {
            workflowIds
        }
        val selected = collectWorkflowsForExport(
            workflowIds = targetIds,
            includeDependencies = includeDependencies
        )
        return buildExportEnvelope(selected, ExportPackageType.COLLECTION).toString(2)
    }

    suspend fun exportWorkflowArchiveBytes(
        workflowIds: List<Long>? = null,
        packageType: ExportPackageType = ExportPackageType.COLLECTION,
        includeDependencies: Boolean = true
    ): ByteArray {
        val all = dao.getAutomationWorkflowsOnce()
        val targetIds = if (workflowIds == null) {
            all.filter { !it.isArchived }.map { it.id }.ifEmpty { all.map { it.id } }
        } else {
            workflowIds
        }
        val selected = collectWorkflowsForExport(
            workflowIds = targetIds,
            includeDependencies = includeDependencies
        )
        val bundleJson = buildExportEnvelope(selected, packageType).toString(2)
        val manifest = JSONObject().apply {
            put("formatVersion", WorkflowPhase8Constants.WORKFLOW_EXPORT_FORMAT_VERSION)
            put("packageType", packageType.id)
            put("exportedAt", System.currentTimeMillis())
            put("generator", "ApexBrowser-Phase8")
            put("workflowCount", selected.size)
            put("workflowUuids", JSONArray(selected.map { it.workflowUuid }))
        }.toString(2)

        return ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("manifest.json"))
                zos.write(manifest.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("workflow_bundle.json"))
                zos.write(bundleJson.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            baos.toByteArray()
        }
    }

    suspend fun importFromZipBytes(
        zipBytes: ByteArray,
        conflictResolution: WorkflowConflictResolution = WorkflowConflictResolution.CREATE_COPY
    ): WorkflowImportResult {
        if (zipBytes.isEmpty()) {
            return WorkflowImportResult(
                success = false,
                errors = listOf("Selected ZIP archive is empty.")
            )
        }
        if (zipBytes.size > WorkflowPhase8Constants.MAX_IMPORT_FILE_BYTES) {
            return WorkflowImportResult(
                success = false,
                errors = listOf("ZIP archive exceeds maximum size of ${WorkflowPhase8Constants.MAX_IMPORT_FILE_BYTES / (1024 * 1024)} MB.")
            )
        }

        val extractedJsonFiles = linkedMapOf<String, String>()
        var totalEntries = 0
        var totalUncompressed = 0L

        val readResult = runCatching {
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
                val buffer = ByteArray(4096)
                var entry = zis.nextEntry
                while (entry != null) {
                    totalEntries++
                    if (totalEntries > WorkflowPhase8Constants.MAX_IMPORT_ZIP_ENTRIES) {
                        throw IllegalArgumentException("ZIP archive exceeds maximum entry limit (${WorkflowPhase8Constants.MAX_IMPORT_ZIP_ENTRIES}).")
                    }
                    val entryName = entry.name
                    if (!isSafeZipEntryName(entryName)) {
                        throw SecurityException("Rejected unsafe ZIP entry path '$entryName'.")
                    }
                    if (!entry.isDirectory) {
                        val out = ByteArrayOutputStream()
                        while (true) {
                            val read = zis.read(buffer)
                            if (read <= 0) break
                            totalUncompressed += read
                            if (totalUncompressed > WorkflowPhase8Constants.MAX_IMPORT_ZIP_UNCOMPRESSED_BYTES) {
                                throw IllegalArgumentException("ZIP archive exceeds maximum uncompressed size limit.")
                            }
                            out.write(buffer, 0, read)
                        }
                        if (entryName.endsWith(".json", ignoreCase = true)) {
                            extractedJsonFiles[entryName] = out.toString(Charsets.UTF_8.name())
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        if (readResult.isFailure) {
            return WorkflowImportResult(
                success = false,
                errors = listOf(readResult.exceptionOrNull()?.message ?: "Failed to read ZIP archive safely.")
            )
        }

        val bundleJson = extractedJsonFiles["workflow_bundle.json"]
            ?: extractedJsonFiles.entries
                .firstOrNull { !it.key.equals("manifest.json", ignoreCase = true) }
                ?.value
            ?: return WorkflowImportResult(
                success = false,
                errors = listOf("ZIP archive does not contain a workflow JSON payload (expected 'workflow_bundle.json').")
            )

        return importFromJson(bundleJson, conflictResolution)
    }

    suspend fun importFromJson(
        rawJson: String,
        conflictResolution: WorkflowConflictResolution = WorkflowConflictResolution.CREATE_COPY
    ): WorkflowImportResult {
        val compat = WorkflowCompatibilityManager.parseAndMigrateExportEnvelope(rawJson)
        if (!compat.isSupported) {
            return WorkflowImportResult(
                success = false,
                errors = listOf(compat.errorMessage.ifBlank { "Unsupported or corrupted workflow package." }),
                warnings = compat.warnings,
                migrationNotes = compat.migrationNotes
            )
        }
        if (compat.workflows.isEmpty()) {
            return WorkflowImportResult(
                success = false,
                errors = listOf("Workflow package contains zero workflows."),
                warnings = compat.warnings,
                migrationNotes = compat.migrationNotes
            )
        }

        // 1. Order payloads topologically so dependencies precede parents cleanly
        val (orderedPayloads, cycleErrors) = WorkflowDependencyResolver.sortPortablePayloadsTopologically(compat.workflows)
        if (cycleErrors.isNotEmpty()) {
            return WorkflowImportResult(
                success = false,
                errors = cycleErrors,
                warnings = compat.warnings,
                migrationNotes = compat.migrationNotes
            )
        }

        val existingDbWorkflows = dao.getAutomationWorkflowsOnce()
        val existingByUuid = existingDbWorkflows
            .filter { it.workflowUuid.isNotBlank() }
            .associateBy { it.workflowUuid }
        val existingById = existingDbWorkflows.associateBy { it.id }

        val bundleByUuid = orderedPayloads.associateBy { it.workflowUuid }
        val bundleByLegacyId = orderedPayloads
            .filter { it.legacyId > 0L }
            .associateBy { it.legacyId }

        val errors = mutableListOf<String>()
        val warnings = compat.warnings.toMutableList()
        val migrationNotes = compat.migrationNotes.toMutableList()

        // 2. Sanitize & validate every workflow payload BEFORE mutating the database
        val sanitizedPayloads = mutableMapOf<String, PortableWorkflowPayload>()
        var rebindStepCount = 0
        val rebindWorkflowUuids = linkedSetOf<String>()

        for (payload in orderedPayloads) {
            val cleanSourceUrl = payload.sourceUrl.trim()
            if (cleanSourceUrl.isNotBlank() && !cleanSourceUrl.equals("apex://home", ignoreCase = true)) {
                val originRes = engine.parseOrigin(cleanSourceUrl)
                if (!originRes.isValid) {
                    errors.add("Workflow '${payload.name}' has invalid sourceUrl '$cleanSourceUrl'.")
                    continue
                }
            }

            for (origin in payload.document.metadata.allowedOrigins) {
                if (!engine.parseOrigin(origin).isValid) {
                    errors.add("Workflow '${payload.name}' has invalid allowedOrigin '$origin'.")
                }
            }

            val stripCounter = intArrayOf(0)
            val sanitizedSteps = sanitizeImportedStepsRecursive(
                steps = payload.document.steps,
                workflowName = payload.name,
                errors = errors,
                rebindCounter = stripCounter
            )
            if (sanitizedSteps == null) continue

            if (stripCounter[0] > 0) {
                rebindStepCount += stripCounter[0]
                rebindWorkflowUuids.add(payload.workflowUuid)
                warnings.add("Workflow '${payload.name}' has ${stripCounter[0]} credential step(s) requiring Vault rebinding.")
            }

            // Strip any sensitive default values from variables just in case
            val cleanVars = payload.document.metadata.variables.filterNot {
                WorkflowVariableStore.isBlockedCredentialVariableName(it.name)
            }
            sanitizedPayloads[payload.workflowUuid] = payload.copy(
                document = payload.document.copy(
                    steps = AutomationStepCodec.reindex(sanitizedSteps),
                    metadata = payload.document.metadata.copy(variables = cleanVars)
                )
            )
        }

        // 3. Verify all sub-workflow dependencies resolve either within this import bundle or local DB
        for (payload in orderedPayloads) {
            val sanitized = sanitizedPayloads[payload.workflowUuid] ?: continue
            val refs = WorkflowDependencyResolver.collectDirectSubWorkflowRefs(sanitized.document.steps)
            for (ref in refs) {
                val inBundle = (ref.targetWorkflowUuid.isNotBlank() && bundleByUuid.containsKey(ref.targetWorkflowUuid)) ||
                    (ref.targetWorkflowId > 0L && bundleByLegacyId.containsKey(ref.targetWorkflowId))
                val inLocalDb = (ref.targetWorkflowUuid.isNotBlank() && existingByUuid.containsKey(ref.targetWorkflowUuid)) ||
                    (ref.targetWorkflowId > 0L && existingById.containsKey(ref.targetWorkflowId))
                if (!inBundle && !inLocalDb) {
                    val label = ref.targetWorkflowName.ifBlank {
                        ref.targetWorkflowUuid.ifBlank { "#${ref.targetWorkflowId}" }
                    }
                    errors.add("Missing required sub-workflow dependency '$label' referenced by '${sanitized.name}'.")
                }
            }
        }

        if (errors.isNotEmpty()) {
            return WorkflowImportResult(
                success = false,
                errors = errors,
                warnings = warnings,
                migrationNotes = migrationNotes
            )
        }

        // 4. Plan DB records, sentinel IDs, and UUID -> resolved ID mapping for topological import
        var nextSentinel = -1L
        val plannedRecords = mutableListOf<PreparedWorkflowImportRecord>()
        val resolvedTargetByBundleUuid = mutableMapOf<String, Long>()
        val resolvedTargetByBundleLegacyId = mutableMapOf<Long, Long>()
        var importedCount = 0
        var updatedCount = 0
        var skippedCount = 0
        val now = System.currentTimeMillis()
        val rebindBundleUuidsWithCreatedId = mutableSetOf<String>()

        for (payload in orderedPayloads) {
            val sanitized = sanitizedPayloads[payload.workflowUuid] ?: continue
            val matchedExisting = existingByUuid[sanitized.workflowUuid]

            if (matchedExisting != null && conflictResolution == WorkflowConflictResolution.SKIP_EXISTING) {
                skippedCount++
                resolvedTargetByBundleUuid[sanitized.workflowUuid] = matchedExisting.id
                if (sanitized.legacyId > 0L) resolvedTargetByBundleLegacyId[sanitized.legacyId] = matchedExisting.id
                continue
            }

            // Remap SubWorkflowReference IDs in steps before encoding JSON
            val remappedSteps = remapSubWorkflowRefsRecursive(
                steps = sanitized.document.steps,
                bundleByUuid = bundleByUuid,
                bundleByLegacyId = bundleByLegacyId,
                resolvedTargetByBundleUuid = resolvedTargetByBundleUuid,
                resolvedTargetByBundleLegacyId = resolvedTargetByBundleLegacyId,
                existingByUuid = existingByUuid,
                existingById = existingById
            )
            val reindexed = AutomationStepCodec.reindex(remappedSteps)
            val encodedSteps = AutomationStepCodec.encode(reindexed, sanitized.document.metadata)
            val tagsJson = WorkflowOrganizationMetadata.encodeTagsJson(sanitized.organization.tags)

            if (matchedExisting != null && conflictResolution == WorkflowConflictResolution.NEW_VERSION_IF_MATCH) {
                val maxVer = dao.getMaxWorkflowVersionNumber(matchedExisting.id) ?: matchedExisting.currentVersion
                val nextVersion = maxOf(matchedExisting.currentVersion, maxVer) + 1
                val hash = WorkflowVersionManager.computeContentHash(
                    name = sanitized.name,
                    domain = sanitized.domain,
                    sourceUrl = sanitized.sourceUrl,
                    type = sanitized.type.id,
                    instructionsText = sanitized.instructionsText,
                    stepsJson = encodedSteps
                )
                val updatedEntity = matchedExisting.copy(
                    name = sanitized.name,
                    domain = sanitized.domain,
                    sourceUrl = sanitized.sourceUrl,
                    type = sanitized.type.id,
                    instructionsText = sanitized.instructionsText,
                    stepsJson = encodedSteps,
                    stepCount = reindexed.size,
                    currentVersion = nextVersion,
                    folder = sanitized.organization.folder.ifBlank { matchedExisting.folder },
                    category = sanitized.organization.category.ifBlank { matchedExisting.category },
                    tagsJson = if (sanitized.organization.tags.isNotEmpty()) tagsJson else matchedExisting.tagsJson,
                    isFavorite = sanitized.organization.isFavorite || matchedExisting.isFavorite,
                    updatedAt = now
                )
                val versionEntity = AutomationWorkflowVersionEntity(
                    workflowId = matchedExisting.id,
                    workflowUuid = matchedExisting.workflowUuid,
                    versionUuid = UUID.randomUUID().toString(),
                    versionNumber = nextVersion,
                    workflowName = sanitized.name,
                    domain = sanitized.domain,
                    sourceUrl = sanitized.sourceUrl,
                    type = sanitized.type.id,
                    instructionsText = sanitized.instructionsText,
                    stepsJson = encodedSteps,
                    stepCount = reindexed.size,
                    changeSummary = "Imported update as v$nextVersion",
                    contentHash = hash,
                    createdAt = now
                )
                plannedRecords.add(
                    PreparedWorkflowImportRecord(
                        sentinelId = matchedExisting.id,
                        workflow = updatedEntity,
                        versionSnapshot = versionEntity,
                        targetExistingWorkflowId = matchedExisting.id,
                        expectedExistingVersion = matchedExisting.currentVersion
                    )
                )
                resolvedTargetByBundleUuid[sanitized.workflowUuid] = matchedExisting.id
                if (sanitized.legacyId > 0L) resolvedTargetByBundleLegacyId[sanitized.legacyId] = matchedExisting.id
                if (sanitized.workflowUuid in rebindWorkflowUuids) {
                    rebindBundleUuidsWithCreatedId.add(sanitized.workflowUuid)
                }
                updatedCount++
            } else {
                val sentinel = nextSentinel--
                val isDuplicateCopy = matchedExisting != null
                val finalUuid = if (isDuplicateCopy) UUID.randomUUID().toString() else sanitized.workflowUuid
                val finalName = if (isDuplicateCopy) "${sanitized.name} (Imported)" else sanitized.name
                val hash = WorkflowVersionManager.computeContentHash(
                    name = finalName,
                    domain = sanitized.domain,
                    sourceUrl = sanitized.sourceUrl,
                    type = sanitized.type.id,
                    instructionsText = sanitized.instructionsText,
                    stepsJson = encodedSteps
                )
                val newEntity = AutomationWorkflowEntity(
                    name = finalName,
                    domain = sanitized.domain,
                    sourceUrl = sanitized.sourceUrl,
                    type = sanitized.type.id,
                    instructionsText = sanitized.instructionsText,
                    stepsJson = encodedSteps,
                    stepCount = reindexed.size,
                    createdAt = if (sanitized.createdAt > 0L) sanitized.createdAt else now,
                    updatedAt = now,
                    workflowUuid = finalUuid,
                    currentVersion = 1,
                    folder = sanitized.organization.folder,
                    category = sanitized.organization.category,
                    tagsJson = tagsJson,
                    isFavorite = sanitized.organization.isFavorite,
                    isArchived = false,
                    archivedAt = null
                )
                val versionEntity = AutomationWorkflowVersionEntity(
                    workflowId = 0L,
                    workflowUuid = finalUuid,
                    versionUuid = UUID.randomUUID().toString(),
                    versionNumber = 1,
                    workflowName = finalName,
                    domain = sanitized.domain,
                    sourceUrl = sanitized.sourceUrl,
                    type = sanitized.type.id,
                    instructionsText = sanitized.instructionsText,
                    stepsJson = encodedSteps,
                    stepCount = reindexed.size,
                    changeSummary = if (isDuplicateCopy) "Imported copy (from v${sanitized.versionNumber})" else "Imported (v${sanitized.versionNumber})",
                    contentHash = hash,
                    createdAt = now
                )
                plannedRecords.add(
                    PreparedWorkflowImportRecord(
                        sentinelId = sentinel,
                        workflow = newEntity,
                        versionSnapshot = versionEntity
                    )
                )
                resolvedTargetByBundleUuid[sanitized.workflowUuid] = sentinel
                if (sanitized.legacyId > 0L) resolvedTargetByBundleLegacyId[sanitized.legacyId] = sentinel
                if (sanitized.workflowUuid in rebindWorkflowUuids) {
                    rebindBundleUuidsWithCreatedId.add(sanitized.workflowUuid)
                }
                importedCount++
            }
        }

        // 5. Full structural validation over the prepared workflows before committing
        val stagedById = plannedRecords.associate { rec ->
            rec.sentinelId to (rec.workflow.name to AutomationStepCodec.decodeDocument(rec.workflow.stepsJson))
        }
        val structuralErrors = mutableListOf<String>()
        for (rec in plannedRecords) {
            val doc = AutomationStepCodec.decodeDocument(rec.workflow.stepsJson)
            val report = WorkflowValidationEngine.validateWorkflow(
                workflow = rec.workflow.copy(id = rec.sentinelId),
                document = doc,
                workflowLookup = { targetId ->
                    stagedById[targetId] ?: dao.getAutomationWorkflowById(targetId)?.let {
                        it.name to AutomationStepCodec.decodeDocument(it.stepsJson)
                    }
                },
                credentialLookup = null,
                engine = engine
            )
            // Credential unbinding is expected & required on import; unsupported action types are preserved safely on import;
            // any structural corruption (cycles, missing dependencies, invalid selectors, invalid URLs, invalid variables) blocks import!
            val fatalImportIssues = report.issues.filter { issue ->
                issue.severity.blocksExecution &&
                    issue.errorCode != AutomationErrorCode.CREDENTIAL_UNASSIGNED &&
                    issue.errorCode != AutomationErrorCode.UNSUPPORTED_ACTION
            }
            fatalImportIssues.forEach { structuralErrors.add("${rec.workflow.name}: ${it.explanation}") }
        }
        if (structuralErrors.isNotEmpty()) {
            return WorkflowImportResult(
                success = false,
                errors = structuralErrors,
                warnings = warnings,
                migrationNotes = migrationNotes
            )
        }

        // 6. Commit atomically inside a single Room transaction
        if (plannedRecords.isEmpty()) {
            return WorkflowImportResult(
                success = true,
                importedCount = 0,
                updatedCount = 0,
                skippedCount = skippedCount,
                warnings = warnings,
                migrationNotes = migrationNotes
            )
        }

        val commitResult = runCatching {
            dao.commitImportedWorkflowsTransaction(plannedRecords)
        }
        if (commitResult.isFailure) {
            return WorkflowImportResult(
                success = false,
                errors = listOf("Atomic import failed and was rolled back: ${commitResult.exceptionOrNull()?.message ?: "database error"}"),
                warnings = warnings,
                migrationNotes = migrationNotes
            )
        }

        val committedIds = commitResult.getOrThrow()
        val rebindIds = mutableListOf<Long>()
        plannedRecords.forEachIndexed { idx, rec ->
            val committedId = committedIds.getOrNull(idx) ?: return@forEachIndexed
            val doc = AutomationStepCodec.decodeDocument(rec.workflow.stepsJson)
            if (doc.steps.flatMap { it.collectNestedSteps() }.any { it.isCredentialStep }) {
                rebindIds.add(committedId)
            }
        }

        return WorkflowImportResult(
            success = true,
            importedCount = importedCount,
            updatedCount = updatedCount,
            skippedCount = skippedCount,
            importedWorkflowIds = committedIds,
            rebindRequiredWorkflowIds = rebindIds,
            rebindRequiredStepCount = rebindStepCount,
            warnings = warnings,
            migrationNotes = migrationNotes
        )
    }

    private suspend fun collectWorkflowsForExport(
        workflowIds: List<Long>,
        includeDependencies: Boolean
    ): List<AutomationWorkflowEntity> {
        val collected = linkedMapOf<Long, AutomationWorkflowEntity>()
        val queue = ArrayDeque(workflowIds.distinct())
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (id <= 0L || collected.containsKey(id)) continue
            var wf = dao.getAutomationWorkflowById(id) ?: continue
            if (wf.workflowUuid.isBlank()) {
                val uuid = UUID.randomUUID().toString()
                wf = wf.copy(workflowUuid = uuid)
                dao.upsertAutomationWorkflow(wf)
            }
            collected[id] = wf
            if (includeDependencies) {
                val doc = AutomationStepCodec.decodeDocument(wf.stepsJson)
                WorkflowDependencyResolver.collectDirectSubWorkflowRefs(doc.steps).forEach { ref ->
                    if (ref.targetWorkflowId > 0L && !collected.containsKey(ref.targetWorkflowId)) {
                        queue.addLast(ref.targetWorkflowId)
                    }
                }
            }
        }
        return collected.values.toList()
    }

    private suspend fun buildExportEnvelope(
        workflows: List<AutomationWorkflowEntity>,
        packageType: ExportPackageType
    ): JSONObject {
        val allById = workflows.associateBy { it.id }.toMutableMap()
        val workflowsArray = JSONArray()
        for (wf in workflows) {
            val doc = AutomationStepCodec.decodeDocument(wf.stepsJson)
            val scrubbedSteps = scrubStepsForExport(doc.steps, allById)
            val scrubbedMeta = doc.metadata.copy(
                variables = doc.metadata.variables.filterNot {
                    WorkflowVariableStore.isBlockedCredentialVariableName(it.name)
                }.map { v ->
                    if (WorkflowVariableStore.shouldMaskInUi(v.name)) v.copy(defaultValue = "") else v
                }
            )
            val depUuids = WorkflowDependencyResolver.collectDirectSubWorkflowRefs(scrubbedSteps)
                .mapNotNull { it.targetWorkflowUuid.takeIf { u -> u.isNotBlank() } }
                .distinct()

            val wfUuid = wf.workflowUuid.ifBlank { UUID.randomUUID().toString() }
            val obj = JSONObject().apply {
                put("schemaVersion", AutomationStepCodec.CURRENT_SCHEMA_VERSION)
                put("workflowUuid", wfUuid)
                put("legacyId", wf.id)
                put("versionNumber", wf.currentVersion.coerceAtLeast(1))
                put("name", wf.name)
                put("domain", wf.domain)
                put("sourceUrl", wf.sourceUrl)
                put("type", wf.type)
                put("instructionsText", wf.instructionsText)
                put("steps", AutomationStepCodec.encodeStepsArray(scrubbedSteps))
                if (!scrubbedMeta.isEmptyDefault) {
                    put("phase6", scrubbedMeta.toJson())
                }
                val tags = WorkflowOrganizationMetadata.decodeTagsJson(wf.tagsJson)
                val orgObj = JSONObject().apply {
                    put("folder", wf.folder)
                    put("category", wf.category)
                    put("tags", JSONArray(tags))
                    put("isFavorite", wf.isFavorite)
                    put("isArchived", wf.isArchived)
                }
                put("organization", orgObj)
                if (depUuids.isNotEmpty()) {
                    put("dependencies", JSONArray(depUuids))
                }
                put("createdAt", wf.createdAt)
                put("updatedAt", wf.updatedAt)
            }
            workflowsArray.put(obj)
        }
        return JSONObject().apply {
            put("formatVersion", WorkflowPhase8Constants.WORKFLOW_EXPORT_FORMAT_VERSION)
            put("packageType", packageType.id)
            put("exportedAt", System.currentTimeMillis())
            put("generator", "ApexBrowser-Phase8")
            put("workflows", workflowsArray)
        }
    }

    private suspend fun scrubStepsForExport(
        steps: List<AutomationStep>,
        knownById: MutableMap<Long, AutomationWorkflowEntity>
    ): List<AutomationStep> {
        return steps.map { step ->
            val scrubbedSubRef = step.subWorkflowRef?.let { ref ->
                var target = knownById[ref.targetWorkflowId]
                if (target == null && ref.targetWorkflowId > 0L) {
                    target = dao.getAutomationWorkflowById(ref.targetWorkflowId)
                    if (target != null) knownById[target.id] = target
                }
                val resolvedUuid = target?.workflowUuid?.takeIf { it.isNotBlank() } ?: ref.targetWorkflowUuid
                ref.copy(
                    targetWorkflowUuid = resolvedUuid,
                    targetWorkflowName = target?.name ?: ref.targetWorkflowName
                )
            }
            val scrubbedBase = if (step.isCredentialStep) {
                step.copy(
                    value = "",
                    credentialReferenceId = null,
                    requiresCredential = true
                )
            } else {
                step
            }
            scrubbedBase.copy(
                subWorkflowRef = scrubbedSubRef,
                thenSteps = scrubStepsForExport(step.thenSteps, knownById),
                elseIfBranches = step.elseIfBranches.map { b ->
                    b.copy(steps = scrubStepsForExport(b.steps, knownById))
                },
                elseSteps = scrubStepsForExport(step.elseSteps, knownById),
                recoveryPolicy = step.recoveryPolicy?.let { pol ->
                    pol.copy(alternativeSteps = scrubStepsForExport(pol.alternativeSteps, knownById))
                }
            )
        }
    }

    private fun sanitizeImportedStepsRecursive(
        steps: List<AutomationStep>,
        workflowName: String,
        errors: MutableList<String>,
        rebindCounter: IntArray
    ): List<AutomationStep>? {
        val out = mutableListOf<AutomationStep>()
        for (step in steps) {
            if (step.selector.isNotBlank() && !step.selector.contains("\${")) {
                val selCheck = engine.validateSelector(step.selector)
                if (!selCheck.isValid) {
                    errors.add("Workflow '$workflowName' step '${step.description}' has unsafe or invalid selector: ${selCheck.reason}")
                    return null
                }
            }
            if (step.expectedOrigin.isNotBlank()) {
                val origCheck = engine.parseOrigin(step.expectedOrigin)
                if (!origCheck.isValid) {
                    errors.add("Workflow '$workflowName' step '${step.description}' has invalid expectedOrigin '${step.expectedOrigin}'.")
                    return null
                }
            }
            val cleanThen = sanitizeImportedStepsRecursive(step.thenSteps, workflowName, errors, rebindCounter) ?: return null
            val cleanElse = sanitizeImportedStepsRecursive(step.elseSteps, workflowName, errors, rebindCounter) ?: return null
            val cleanElifs = mutableListOf<ElseIfBranch>()
            for (b in step.elseIfBranches) {
                val bSteps = sanitizeImportedStepsRecursive(b.steps, workflowName, errors, rebindCounter) ?: return null
                cleanElifs.add(b.copy(steps = bSteps))
            }
            val cleanRecovery = step.recoveryPolicy?.let { pol ->
                val alt = sanitizeImportedStepsRecursive(pol.alternativeSteps, workflowName, errors, rebindCounter) ?: return null
                pol.copy(alternativeSteps = alt)
            }

            // Always scrub credentials and clear reference IDs on import so user explicitly rebinds local Vault entries
            val sanitizedStep = if (step.isCredentialStep) {
                rebindCounter[0]++
                step.copy(
                    value = "",
                    credentialReferenceId = null,
                    requiresCredential = true,
                    thenSteps = cleanThen,
                    elseIfBranches = cleanElifs,
                    elseSteps = cleanElse,
                    recoveryPolicy = cleanRecovery
                )
            } else {
                step.copy(
                    thenSteps = cleanThen,
                    elseIfBranches = cleanElifs,
                    elseSteps = cleanElse,
                    recoveryPolicy = cleanRecovery
                )
            }
            out.add(sanitizedStep)
        }
        return out
    }

    private fun remapSubWorkflowRefsRecursive(
        steps: List<AutomationStep>,
        bundleByUuid: Map<String, PortableWorkflowPayload>,
        bundleByLegacyId: Map<Long, PortableWorkflowPayload>,
        resolvedTargetByBundleUuid: Map<String, Long>,
        resolvedTargetByBundleLegacyId: Map<Long, Long>,
        existingByUuid: Map<String, AutomationWorkflowEntity>,
        existingById: Map<Long, AutomationWorkflowEntity>
    ): List<AutomationStep> {
        return steps.map { step ->
            val updatedRef = step.subWorkflowRef?.let { ref ->
                val resolvedId = ref.targetWorkflowUuid.takeIf { it.isNotBlank() }?.let { uuid ->
                    resolvedTargetByBundleUuid[uuid] ?: existingByUuid[uuid]?.id
                } ?: ref.targetWorkflowId.takeIf { it > 0L }?.let { legacyId ->
                    resolvedTargetByBundleLegacyId[legacyId] ?: existingById[legacyId]?.id
                } ?: ref.targetWorkflowId

                val resolvedUuid = ref.targetWorkflowUuid.ifBlank {
                    bundleByLegacyId[ref.targetWorkflowId]?.workflowUuid
                        ?: existingById[resolvedId]?.workflowUuid
                        ?: ""
                }
                val resolvedName = ref.targetWorkflowName.ifBlank {
                    bundleByUuid[resolvedUuid]?.name ?: existingById[resolvedId]?.name.orEmpty()
                }
                ref.copy(
                    targetWorkflowId = resolvedId,
                    targetWorkflowUuid = resolvedUuid,
                    targetWorkflowName = resolvedName
                )
            }
            step.copy(
                subWorkflowRef = updatedRef,
                thenSteps = remapSubWorkflowRefsRecursive(
                    step.thenSteps, bundleByUuid, bundleByLegacyId,
                    resolvedTargetByBundleUuid, resolvedTargetByBundleLegacyId, existingByUuid, existingById
                ),
                elseIfBranches = step.elseIfBranches.map { b ->
                    b.copy(
                        steps = remapSubWorkflowRefsRecursive(
                            b.steps, bundleByUuid, bundleByLegacyId,
                            resolvedTargetByBundleUuid, resolvedTargetByBundleLegacyId, existingByUuid, existingById
                        )
                    )
                },
                elseSteps = remapSubWorkflowRefsRecursive(
                    step.elseSteps, bundleByUuid, bundleByLegacyId,
                    resolvedTargetByBundleUuid, resolvedTargetByBundleLegacyId, existingByUuid, existingById
                ),
                recoveryPolicy = step.recoveryPolicy?.let { pol ->
                    pol.copy(
                        alternativeSteps = remapSubWorkflowRefsRecursive(
                            pol.alternativeSteps, bundleByUuid, bundleByLegacyId,
                            resolvedTargetByBundleUuid, resolvedTargetByBundleLegacyId, existingByUuid, existingById
                        )
                    )
                }
            )
        }
    }

    companion object {
        fun isSafeZipEntryName(entryName: String): Boolean {
            if (entryName.isBlank()) return false
            if (entryName.contains('\u0000')) return false
            if (entryName.startsWith("/") || entryName.startsWith("\\")) return false
            if (entryName.contains('\\')) return false
            if (entryName.contains(':')) return false
            val segments = entryName.split('/')
            return segments.none { it == ".." || it == "." }
        }
    }
}
