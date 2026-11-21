package com.example.automation

import com.example.data.local.AutomationWorkflowEntity
import java.security.MessageDigest
import java.util.UUID

data class WorkflowVersionResult(
    val workflowId: Long,
    val versionNumber: Int,
    val snapshotCreated: Boolean,
    val message: String
)

/**
 * Manages persistent workflow version history, deduplicated content hashing,
 * optimistic concurrency checks, and non-destructive version restoration.
 */
class WorkflowVersionManager(
    private val dao: com.example.data.local.ApexDao
) {
    fun observeVersions(workflowId: Long) = dao.observeWorkflowVersions(workflowId)

    suspend fun getVersionsOnce(workflowId: Long) = dao.getWorkflowVersionsOnce(workflowId)

    suspend fun getVersionSnapshot(workflowId: Long, versionNumber: Int) =
        dao.getWorkflowVersionByNumber(workflowId, versionNumber)

    suspend fun ensureInitialVersion(workflowId: Long) {
        val wf = dao.getAutomationWorkflowById(workflowId) ?: return
        val existing = dao.getLatestWorkflowVersion(workflowId)
        if (existing != null) return
        val ensuredUuid = wf.workflowUuid.ifBlank { UUID.randomUUID().toString() }
        if (wf.workflowUuid.isBlank()) {
            dao.upsertAutomationWorkflow(wf.copy(workflowUuid = ensuredUuid))
        }
        val hash = computeContentHash(
            name = wf.name,
            domain = wf.domain,
            sourceUrl = wf.sourceUrl,
            type = wf.type,
            instructionsText = wf.instructionsText,
            stepsJson = wf.stepsJson
        )
        dao.insertWorkflowVersion(
            com.example.data.local.AutomationWorkflowVersionEntity(
                workflowId = wf.id,
                workflowUuid = ensuredUuid,
                versionUuid = UUID.randomUUID().toString(),
                versionNumber = wf.currentVersion.coerceAtLeast(1),
                workflowName = wf.name,
                domain = wf.domain,
                sourceUrl = wf.sourceUrl,
                type = wf.type,
                instructionsText = wf.instructionsText,
                stepsJson = wf.stepsJson,
                stepCount = wf.stepCount,
                changeSummary = "Initial version",
                contentHash = hash,
                createdAt = wf.updatedAt
            )
        )
    }

    suspend fun createNewWorkflowWithVersion(
        name: String,
        domain: String,
        sourceUrl: String,
        type: AutomationType,
        steps: List<AutomationStep>,
        instructionsText: String = "",
        metadata: WorkflowDocumentMetadata = WorkflowDocumentMetadata(),
        organization: WorkflowOrganizationMetadata = WorkflowOrganizationMetadata(),
        workflowUuid: String = UUID.randomUUID().toString(),
        changeSummary: String = "Initial version"
    ): Long {
        val now = System.currentTimeMillis()
        val reindexed = AutomationStepCodec.reindex(steps)
        val encoded = AutomationStepCodec.encode(reindexed, metadata)
        val cleanName = name.trim().ifBlank { "Untitled Automation" }
        val cleanDomain = domain.trim().ifBlank { "All sites" }
        val cleanUuid = workflowUuid.trim().ifBlank { UUID.randomUUID().toString() }
        val hash = computeContentHash(cleanName, cleanDomain, sourceUrl, type.id, instructionsText, encoded)

        val entity = AutomationWorkflowEntity(
            name = cleanName,
            domain = cleanDomain,
            sourceUrl = sourceUrl,
            type = type.id,
            instructionsText = instructionsText,
            stepsJson = encoded,
            stepCount = reindexed.size,
            createdAt = now,
            updatedAt = now,
            workflowUuid = cleanUuid,
            currentVersion = 1,
            folder = organization.folder.trim(),
            category = organization.category.trim(),
            tagsJson = WorkflowOrganizationMetadata.encodeTagsJson(organization.tags),
            isFavorite = organization.isFavorite,
            isArchived = organization.isArchived,
            archivedAt = organization.archivedAt
        )
        val versionEntity = com.example.data.local.AutomationWorkflowVersionEntity(
            workflowId = 0L,
            workflowUuid = cleanUuid,
            versionUuid = UUID.randomUUID().toString(),
            versionNumber = 1,
            workflowName = cleanName,
            domain = cleanDomain,
            sourceUrl = sourceUrl,
            type = type.id,
            instructionsText = instructionsText,
            stepsJson = encoded,
            stepCount = reindexed.size,
            changeSummary = changeSummary,
            contentHash = hash,
            createdAt = now
        )
        return dao.createWorkflowWithInitialVersion(entity, versionEntity)
    }

    /**
     * Updates an existing workflow with optimistic concurrency control and records a new version
     * snapshot ONLY when semantic workflow content changed (deduplicating identical saves).
     */
    suspend fun updateExistingWorkflowVersioned(
        workflowId: Long,
        expectedVersion: Int? = null,
        name: String? = null,
        domain: String? = null,
        sourceUrl: String? = null,
        type: AutomationType? = null,
        instructionsText: String? = null,
        document: WorkflowDocument? = null,
        changeSummary: String = "",
        restoredFromVersion: Int? = null
    ): WorkflowVersionResult {
        ensureInitialVersion(workflowId)
        val existing = dao.getAutomationWorkflowById(workflowId)
            ?: throw IllegalArgumentException("Workflow $workflowId does not exist.")
        val lockExpectedVersion = expectedVersion ?: existing.currentVersion
        if (existing.currentVersion != lockExpectedVersion) {
            throw WorkflowConcurrencyConflictException(
                workflowId = workflowId,
                expectedVersion = lockExpectedVersion,
                actualVersion = existing.currentVersion
            )
        }

        val resolvedName = name?.trim()?.ifBlank { existing.name } ?: existing.name
        val resolvedDomain = domain?.trim()?.ifBlank { existing.domain } ?: existing.domain
        val resolvedUrl = sourceUrl ?: existing.sourceUrl
        val resolvedType = type?.id ?: existing.type
        val resolvedInstructions = instructionsText ?: existing.instructionsText
        val rawSafeDecode = if (document == null) {
            AutomationStepCodec.decodeSafely(existing.stepsJson)
        } else {
            null
        }
        val resolvedDoc = document ?: when (rawSafeDecode) {
            is AutomationStepDecodeResult.Success -> WorkflowDocument(
                schemaVersion = rawSafeDecode.schemaVersion,
                steps = rawSafeDecode.steps,
                metadata = rawSafeDecode.metadata,
                isLegacyUnversioned = rawSafeDecode.isLegacyUnversioned
            )
            else -> null
        }
        val reindexedSteps = resolvedDoc?.let { AutomationStepCodec.reindex(it.steps) }
        val encodedSteps = if (resolvedDoc != null && reindexedSteps != null) {
            AutomationStepCodec.encode(reindexedSteps, resolvedDoc.metadata)
        } else {
            existing.stepsJson
        }
        val resolvedStepCount = reindexedSteps?.size ?: existing.stepCount
        val ensuredUuid = existing.workflowUuid.ifBlank { UUID.randomUUID().toString() }
        val newHash = computeContentHash(
            name = resolvedName,
            domain = resolvedDomain,
            sourceUrl = resolvedUrl,
            type = resolvedType,
            instructionsText = resolvedInstructions,
            stepsJson = encodedSteps
        )
        val latestSnapshot = dao.getLatestWorkflowVersion(workflowId)
        val latestHash = latestSnapshot?.contentHash?.ifBlank {
            computeContentHash(
                name = latestSnapshot.workflowName,
                domain = latestSnapshot.domain,
                sourceUrl = latestSnapshot.sourceUrl,
                type = latestSnapshot.type,
                instructionsText = latestSnapshot.instructionsText,
                stepsJson = latestSnapshot.stepsJson
            )
        }

        val isDuplicateSnapshot = restoredFromVersion == null &&
            latestHash != null &&
            latestHash == newHash

        val now = System.currentTimeMillis()
        if (isDuplicateSnapshot) {
            // Identical content: still touch updatedAt without duplicating version row or bumping version
            val rows = dao.updateWorkflowWithOptionalVersionTransaction(
                workflowId = workflowId,
                expectedVersion = lockExpectedVersion,
                newVersion = existing.currentVersion,
                workflowUuid = ensuredUuid,
                name = resolvedName,
                domain = resolvedDomain,
                sourceUrl = resolvedUrl,
                type = resolvedType,
                instructionsText = resolvedInstructions,
                stepsJson = encodedSteps,
                stepCount = resolvedStepCount,
                folder = existing.folder,
                category = existing.category,
                tagsJson = existing.tagsJson,
                isFavorite = existing.isFavorite,
                isArchived = existing.isArchived,
                archivedAt = existing.archivedAt,
                updatedAt = now,
                versionToInsert = null
            )
            if (rows == 0) {
                val fresh = dao.getAutomationWorkflowById(workflowId)
                throw WorkflowConcurrencyConflictException(
                    workflowId = workflowId,
                    expectedVersion = lockExpectedVersion,
                    actualVersion = fresh?.currentVersion ?: -1
                )
            }
            return WorkflowVersionResult(
                workflowId = workflowId,
                versionNumber = existing.currentVersion,
                snapshotCreated = false,
                message = "No changes detected; retained v${existing.currentVersion}"
            )
        }

        val maxVer = dao.getMaxWorkflowVersionNumber(workflowId) ?: existing.currentVersion
        val nextVersion = maxOf(existing.currentVersion, maxVer) + 1
        val autoSummary = changeSummary.trim().ifBlank {
            val prevDecode = AutomationStepCodec.decodeSafely(existing.stepsJson)
            if (prevDecode is AutomationStepDecodeResult.Success && resolvedDoc != null && reindexedSteps != null) {
                val diff = WorkflowDiffEngine.compareDocuments(
                    baseLabel = "v${existing.currentVersion}",
                    targetLabel = "v$nextVersion",
                    baseName = existing.name,
                    targetName = resolvedName,
                    baseDomain = existing.domain,
                    targetDomain = resolvedDomain,
                    baseSourceUrl = existing.sourceUrl,
                    targetSourceUrl = resolvedUrl,
                    baseType = existing.type,
                    targetType = resolvedType,
                    baseDoc = WorkflowDocument(
                        schemaVersion = prevDecode.schemaVersion,
                        steps = prevDecode.steps,
                        metadata = prevDecode.metadata,
                        isLegacyUnversioned = prevDecode.isLegacyUnversioned
                    ),
                    targetDoc = resolvedDoc.copy(steps = reindexedSteps)
                )
                diff.summary
            } else {
                "Updated workflow metadata"
            }
        }

        val versionRow = com.example.data.local.AutomationWorkflowVersionEntity(
            workflowId = workflowId,
            workflowUuid = ensuredUuid,
            versionUuid = UUID.randomUUID().toString(),
            versionNumber = nextVersion,
            workflowName = resolvedName,
            domain = resolvedDomain,
            sourceUrl = resolvedUrl,
            type = resolvedType,
            instructionsText = resolvedInstructions,
            stepsJson = encodedSteps,
            stepCount = resolvedStepCount,
            changeSummary = autoSummary,
            contentHash = newHash,
            restoredFromVersion = restoredFromVersion,
            createdAt = now
        )
        val updatedRows = dao.updateWorkflowWithOptionalVersionTransaction(
            workflowId = workflowId,
            expectedVersion = lockExpectedVersion,
            newVersion = nextVersion,
            workflowUuid = ensuredUuid,
            name = resolvedName,
            domain = resolvedDomain,
            sourceUrl = resolvedUrl,
            type = resolvedType,
            instructionsText = resolvedInstructions,
            stepsJson = encodedSteps,
            stepCount = resolvedStepCount,
            folder = existing.folder,
            category = existing.category,
            tagsJson = existing.tagsJson,
            isFavorite = existing.isFavorite,
            isArchived = existing.isArchived,
            archivedAt = existing.archivedAt,
            updatedAt = now,
            versionToInsert = versionRow
        )
        if (updatedRows == 0) {
            val fresh = dao.getAutomationWorkflowById(workflowId)
            throw WorkflowConcurrencyConflictException(
                workflowId = workflowId,
                expectedVersion = lockExpectedVersion,
                actualVersion = fresh?.currentVersion ?: -1
            )
        }
        return WorkflowVersionResult(
            workflowId = workflowId,
            versionNumber = nextVersion,
            snapshotCreated = true,
            message = "Saved v$nextVersion"
        )
    }

    /**
     * Restores an older version snapshot by creating a brand new version number rather than
     * mutating or deleting any historical versions.
     */
    suspend fun restoreVersion(
        workflowId: Long,
        targetVersionNumber: Int,
        expectedCurrentVersion: Int? = null
    ): WorkflowVersionResult {
        ensureInitialVersion(workflowId)
        val snapshot = dao.getWorkflowVersionByNumber(workflowId, targetVersionNumber)
            ?: throw IllegalArgumentException("Version v$targetVersionNumber not found for workflow $workflowId.")
        val doc = AutomationStepCodec.decodeDocument(snapshot.stepsJson)
        return updateExistingWorkflowVersioned(
            workflowId = workflowId,
            expectedVersion = expectedCurrentVersion,
            name = snapshot.workflowName,
            domain = snapshot.domain,
            sourceUrl = snapshot.sourceUrl,
            type = AutomationType.fromId(snapshot.type),
            instructionsText = snapshot.instructionsText,
            document = doc,
            changeSummary = "Restored from v$targetVersionNumber",
            restoredFromVersion = targetVersionNumber
        )
    }

    companion object {
        fun computeContentHash(
            name: String,
            domain: String,
            sourceUrl: String,
            type: String,
            instructionsText: String,
            stepsJson: String
        ): String {
            val md = MessageDigest.getInstance("SHA-256")
            val input = "$name\u0000$domain\u0000$sourceUrl\u0000$type\u0000$instructionsText\u0000$stepsJson"
            return md.digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }
}
