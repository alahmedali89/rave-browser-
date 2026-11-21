package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ApexDao {

    @Query("SELECT * FROM privacy_stats WHERE id = 1")
    fun observePrivacyStats(): Flow<PrivacyStatSummaryEntity?>

    @Query("SELECT * FROM privacy_stats WHERE id = 1")
    suspend fun getPrivacyStatsOnce(): PrivacyStatSummaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPrivacyStats(stats: PrivacyStatSummaryEntity)

    @Query("SELECT * FROM blocked_domain_logs ORDER BY timestamp DESC LIMIT 40")
    fun observeRecentBlockedLogs(): Flow<List<BlockedDomainLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedLog(log: BlockedDomainLogEntity)

    @Query("DELETE FROM blocked_domain_logs")
    suspend fun clearBlockedLogs()

    @Query("SELECT * FROM extraction_rules ORDER BY createdAt DESC")
    fun observeExtractionRules(): Flow<List<ExtractionRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExtractionRule(rule: ExtractionRuleEntity): Long

    @Query("UPDATE extraction_rules SET lastRunCount = :count WHERE id = :id")
    suspend fun updateRuleRunCount(id: Long, count: Int)

    @Query("DELETE FROM extraction_rules WHERE id = :id")
    suspend fun deleteExtractionRule(id: Long)

    // Bookmarks
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    suspend fun getBookmarksOnce(): List<BookmarkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookmark(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmarkById(id: Long)

    // Search History (starts empty on fresh install)
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 30")
    fun observeSearchHistory(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSearchHistory(entry: SearchHistoryEntity): Long

    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteSearchHistoryById(id: Long)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()

    // Top Visits (Recent top visits shortcuts)
    @Query("SELECT * FROM top_visits ORDER BY id ASC")
    fun observeTopVisits(): Flow<List<TopVisitEntity>>

    @Query("SELECT * FROM top_visits ORDER BY id ASC")
    suspend fun getTopVisitsOnce(): List<TopVisitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTopVisit(topVisit: TopVisitEntity): Long

    @Query("DELETE FROM top_visits WHERE id = :id")
    suspend fun deleteTopVisitById(id: Long)

    // Browsing History (Full chronological web page visit log)
    @Query("SELECT * FROM browsing_history ORDER BY visitedAt DESC LIMIT 500")
    fun observeBrowsingHistory(): Flow<List<BrowsingHistoryEntity>>

    @Query("SELECT COUNT(*) FROM browsing_history")
    fun observeBrowsingHistoryCount(): Flow<Int>

    @Query("SELECT * FROM browsing_history WHERE url = :url ORDER BY visitedAt DESC LIMIT 1")
    suspend fun getLatestHistoryByUrl(url: String): BrowsingHistoryEntity?

    @Query("SELECT * FROM browsing_history ORDER BY visitedAt DESC LIMIT 1")
    suspend fun getMostRecentHistoryEntry(): BrowsingHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBrowsingHistory(entry: BrowsingHistoryEntity): Long

    @Query("DELETE FROM browsing_history WHERE id = :id")
    suspend fun deleteBrowsingHistoryById(id: Long)

    @Query("DELETE FROM browsing_history WHERE visitedAt >= :sinceMillis")
    suspend fun deleteBrowsingHistorySince(sinceMillis: Long)

    @Query("DELETE FROM browsing_history")
    suspend fun clearAllBrowsingHistory()

    // Fast Downloads (Dedicated Download Manager)
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC LIMIT 300")
    fun observeDownloads(): Flow<List<DownloadItemEntity>>

    @Query("SELECT COUNT(*) FROM downloads")
    fun observeDownloadsCount(): Flow<Int>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getDownloadById(id: Long): DownloadItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownload(item: DownloadItemEntity): Long

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownloadById(id: Long)

    @Query("DELETE FROM downloads WHERE status = 'COMPLETED' OR status = 'FAILED' OR status = 'CANCELED'")
    suspend fun clearCompletedDownloads()

    @Query("DELETE FROM downloads")
    suspend fun clearAllDownloads()

    // Dedicated Password Manager Vault
    @Query("SELECT * FROM saved_passwords ORDER BY isFavorite DESC, updatedAt DESC")
    fun observeSavedPasswords(): Flow<List<SavedPasswordEntity>>

    @Query("SELECT COUNT(*) FROM saved_passwords")
    fun observeSavedPasswordsCount(): Flow<Int>

    @Query("SELECT * FROM saved_passwords ORDER BY isFavorite DESC, updatedAt DESC")
    suspend fun getSavedPasswordsOnce(): List<SavedPasswordEntity>

    @Query("SELECT * FROM saved_passwords WHERE id = :id LIMIT 1")
    suspend fun getSavedPasswordById(id: Long): SavedPasswordEntity?

    @Query("SELECT * FROM saved_passwords WHERE host = :host ORDER BY isFavorite DESC, updatedAt DESC")
    suspend fun getSavedPasswordsByHost(host: String): List<SavedPasswordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedPassword(item: SavedPasswordEntity): Long

    @Query("DELETE FROM saved_passwords WHERE id = :id")
    suspend fun deleteSavedPasswordById(id: Long)

    @Query("DELETE FROM saved_passwords")
    suspend fun clearAllSavedPasswords()

    // Unified Automation Workflows & Executions
    @Query("SELECT * FROM automation_workflows ORDER BY updatedAt DESC")
    fun observeAutomationWorkflows(): Flow<List<AutomationWorkflowEntity>>

    @Query("SELECT * FROM automation_workflows ORDER BY updatedAt DESC")
    suspend fun getAutomationWorkflowsOnce(): List<AutomationWorkflowEntity>

    @Query("SELECT * FROM automation_workflows WHERE id = :id LIMIT 1")
    suspend fun getAutomationWorkflowById(id: Long): AutomationWorkflowEntity?

    @Query("SELECT * FROM automation_workflows WHERE workflowUuid = :workflowUuid LIMIT 1")
    suspend fun getAutomationWorkflowByUuid(workflowUuid: String): AutomationWorkflowEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAutomationWorkflow(workflow: AutomationWorkflowEntity): Long

    @Query("DELETE FROM automation_workflows WHERE id = :id")
    suspend fun deleteAutomationWorkflowById(id: Long)

    // Phase 8 Workflow Versioning, Organization & Transactional Import
    @Query("SELECT * FROM workflow_versions WHERE workflowId = :workflowId ORDER BY versionNumber DESC")
    fun observeWorkflowVersions(workflowId: Long): Flow<List<AutomationWorkflowVersionEntity>>

    @Query("SELECT * FROM workflow_versions WHERE workflowId = :workflowId ORDER BY versionNumber DESC")
    suspend fun getWorkflowVersionsOnce(workflowId: Long): List<AutomationWorkflowVersionEntity>

    @Query("SELECT * FROM workflow_versions WHERE workflowId = :workflowId AND versionNumber = :versionNumber LIMIT 1")
    suspend fun getWorkflowVersionByNumber(workflowId: Long, versionNumber: Int): AutomationWorkflowVersionEntity?

    @Query("SELECT * FROM workflow_versions WHERE workflowId = :workflowId ORDER BY versionNumber DESC LIMIT 1")
    suspend fun getLatestWorkflowVersion(workflowId: Long): AutomationWorkflowVersionEntity?

    @Query("SELECT MAX(versionNumber) FROM workflow_versions WHERE workflowId = :workflowId")
    suspend fun getMaxWorkflowVersionNumber(workflowId: Long): Int?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWorkflowVersion(version: AutomationWorkflowVersionEntity): Long

    @Query(
        """
        UPDATE automation_workflows
        SET workflowUuid = :workflowUuid,
            name = :name,
            domain = :domain,
            sourceUrl = :sourceUrl,
            type = :type,
            instructionsText = :instructionsText,
            stepsJson = :stepsJson,
            stepCount = :stepCount,
            currentVersion = :newVersion,
            folder = :folder,
            category = :category,
            tagsJson = :tagsJson,
            isFavorite = :isFavorite,
            isArchived = :isArchived,
            archivedAt = :archivedAt,
            updatedAt = :updatedAt
        WHERE id = :id AND currentVersion = :expectedVersion
        """
    )
    suspend fun updateAutomationWorkflowWithVersionCheck(
        id: Long,
        expectedVersion: Int,
        newVersion: Int,
        workflowUuid: String,
        name: String,
        domain: String,
        sourceUrl: String,
        type: String,
        instructionsText: String,
        stepsJson: String,
        stepCount: Int,
        folder: String,
        category: String,
        tagsJson: String,
        isFavorite: Boolean,
        isArchived: Boolean,
        archivedAt: Long?,
        updatedAt: Long
    ): Int

    @Query(
        """
        UPDATE automation_workflows
        SET folder = :folder,
            category = :category,
            tagsJson = :tagsJson,
            isFavorite = :isFavorite,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updateWorkflowOrganization(
        id: Long,
        folder: String,
        category: String,
        tagsJson: String,
        isFavorite: Boolean,
        updatedAt: Long
    ): Int

    @Query(
        """
        UPDATE automation_workflows
        SET isArchived = :isArchived,
            archivedAt = :archivedAt,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun setWorkflowArchived(
        id: Long,
        isArchived: Boolean,
        archivedAt: Long?,
        updatedAt: Long
    ): Int

    @Transaction
    suspend fun createWorkflowWithInitialVersion(
        workflow: AutomationWorkflowEntity,
        initialVersion: AutomationWorkflowVersionEntity
    ): Long {
        val insertedId = upsertAutomationWorkflow(workflow)
        insertWorkflowVersion(initialVersion.copy(workflowId = insertedId))
        return insertedId
    }

    @Transaction
    suspend fun updateWorkflowWithOptionalVersionTransaction(
        workflowId: Long,
        expectedVersion: Int,
        newVersion: Int,
        workflowUuid: String,
        name: String,
        domain: String,
        sourceUrl: String,
        type: String,
        instructionsText: String,
        stepsJson: String,
        stepCount: Int,
        folder: String,
        category: String,
        tagsJson: String,
        isFavorite: Boolean,
        isArchived: Boolean,
        archivedAt: Long?,
        updatedAt: Long,
        versionToInsert: AutomationWorkflowVersionEntity?
    ): Int {
        val updatedRows = updateAutomationWorkflowWithVersionCheck(
            id = workflowId,
            expectedVersion = expectedVersion,
            newVersion = newVersion,
            workflowUuid = workflowUuid,
            name = name,
            domain = domain,
            sourceUrl = sourceUrl,
            type = type,
            instructionsText = instructionsText,
            stepsJson = stepsJson,
            stepCount = stepCount,
            folder = folder,
            category = category,
            tagsJson = tagsJson,
            isFavorite = isFavorite,
            isArchived = isArchived,
            archivedAt = archivedAt,
            updatedAt = updatedAt
        )
        if (updatedRows <= 0) return 0
        if (versionToInsert != null) {
            insertWorkflowVersion(versionToInsert.copy(workflowId = workflowId))
        }
        return updatedRows
    }

    @Transaction
    suspend fun commitImportedWorkflowsTransaction(
        items: List<PreparedWorkflowImportRecord>
    ): List<Long> {
        val sentinelToRealId = mutableMapOf<Long, Long>()
        val committedIds = mutableListOf<Long>()
        for (item in items) {
            var resolvedStepsJson = item.workflow.stepsJson
            if (sentinelToRealId.isNotEmpty()) {
                sentinelToRealId.entries
                    .sortedBy { it.key }
                    .forEach { (sentinel, realId) ->
                        if (sentinel < 0L) {
                            val boundaryRegex = Regex("\"targetWorkflowId\"\\s*:\\s*$sentinel(?![0-9])")
                            resolvedStepsJson = resolvedStepsJson.replace(boundaryRegex) {
                                "\"targetWorkflowId\":$realId"
                            }
                        }
                    }
            }
            if (item.targetExistingWorkflowId != null && item.expectedExistingVersion != null) {
                val existingId = item.targetExistingWorkflowId
                val updated = updateAutomationWorkflowWithVersionCheck(
                    id = existingId,
                    expectedVersion = item.expectedExistingVersion,
                    newVersion = item.workflow.currentVersion,
                    workflowUuid = item.workflow.workflowUuid,
                    name = item.workflow.name,
                    domain = item.workflow.domain,
                    sourceUrl = item.workflow.sourceUrl,
                    type = item.workflow.type,
                    instructionsText = item.workflow.instructionsText,
                    stepsJson = resolvedStepsJson,
                    stepCount = item.workflow.stepCount,
                    folder = item.workflow.folder,
                    category = item.workflow.category,
                    tagsJson = item.workflow.tagsJson,
                    isFavorite = item.workflow.isFavorite,
                    isArchived = item.workflow.isArchived,
                    archivedAt = item.workflow.archivedAt,
                    updatedAt = item.workflow.updatedAt
                )
                check(updated == 1) {
                    "Concurrent modification detected while updating workflow $existingId during import"
                }
                insertWorkflowVersion(
                    item.versionSnapshot.copy(
                        workflowId = existingId,
                        stepsJson = resolvedStepsJson
                    )
                )
                if (item.sentinelId < 0L) {
                    sentinelToRealId[item.sentinelId] = existingId
                }
                committedIds.add(existingId)
            } else {
                val insertedId = upsertAutomationWorkflow(
                    item.workflow.copy(
                        id = 0L,
                        stepsJson = resolvedStepsJson
                    )
                )
                insertWorkflowVersion(
                    item.versionSnapshot.copy(
                        workflowId = insertedId,
                        stepsJson = resolvedStepsJson
                    )
                )
                if (item.sentinelId < 0L) {
                    sentinelToRealId[item.sentinelId] = insertedId
                }
                committedIds.add(insertedId)
            }
        }
        return committedIds
    }

    @Query("SELECT * FROM automation_executions ORDER BY startedAt DESC LIMIT 100")
    fun observeAutomationExecutions(): Flow<List<AutomationExecutionEntity>>

    @Query("SELECT * FROM automation_executions WHERE workflowId = :workflowId ORDER BY startedAt DESC LIMIT 100")
    fun observeAutomationExecutionsForWorkflow(workflowId: Long): Flow<List<AutomationExecutionEntity>>

    @Query("SELECT * FROM automation_executions WHERE workflowId = :workflowId ORDER BY startedAt DESC")
    suspend fun getAutomationExecutionsForWorkflowOnce(workflowId: Long): List<AutomationExecutionEntity>

    @Query("SELECT * FROM automation_executions WHERE id = :executionId LIMIT 1")
    suspend fun getAutomationExecutionById(executionId: Long): AutomationExecutionEntity?

    @Query("SELECT * FROM automation_executions WHERE status = 'INTERRUPTED' ORDER BY startedAt DESC LIMIT 1")
    fun observeLatestInterruptedExecution(): Flow<AutomationExecutionEntity?>

    @Query("SELECT * FROM automation_executions WHERE status = 'INTERRUPTED' ORDER BY startedAt DESC LIMIT 1")
    suspend fun getLatestInterruptedExecutionOnce(): AutomationExecutionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAutomationExecution(execution: AutomationExecutionEntity): Long

    @Query(
        """
        UPDATE automation_executions
        SET status = :status,
            completedSteps = :completedSteps,
            totalSteps = :totalSteps,
            verificationSummary = :verificationSummary,
            finishedAt = :finishedAt,
            checkpointJson = :checkpointJson,
            traceJson = :traceJson
        WHERE id = :executionId
        """
    )
    suspend fun updateAutomationExecutionCompletion(
        executionId: Long,
        status: String,
        completedSteps: Int,
        totalSteps: Int,
        verificationSummary: String,
        finishedAt: Long,
        checkpointJson: String = "",
        traceJson: String = ""
    ): Int

    @Query(
        """
        UPDATE automation_executions
        SET status = :status,
            completedSteps = :completedSteps,
            totalSteps = :totalSteps,
            verificationSummary = :verificationSummary,
            checkpointJson = :checkpointJson,
            traceJson = :traceJson
        WHERE id = :executionId
        """
    )
    suspend fun updateAutomationExecutionCheckpointSnapshot(
        executionId: Long,
        status: String,
        completedSteps: Int,
        totalSteps: Int,
        verificationSummary: String,
        checkpointJson: String,
        traceJson: String = ""
    ): Int

    @Query(
        """
        UPDATE automation_workflows
        SET lastExecutedAt = :lastExecutedAt,
            lastExecutionStatus = :lastExecutionStatus,
            lastExecutionSummary = :lastExecutionSummary
        WHERE id = :workflowId
        """
    )
    suspend fun updateWorkflowExecutionSummary(
        workflowId: Long,
        lastExecutedAt: Long,
        lastExecutionStatus: String,
        lastExecutionSummary: String
    )

    @Query("DELETE FROM automation_executions WHERE workflowId = :workflowId")
    suspend fun deleteExecutionsForWorkflow(workflowId: Long)

    @Transaction
    suspend fun deleteAutomationWorkflowWithExecutions(workflowId: Long) {
        deleteExecutionsForWorkflow(workflowId)
        deleteAutomationWorkflowById(workflowId)
    }

    @Transaction
    suspend fun startAutomationExecutionRecord(
        workflowId: Long,
        workflowName: String,
        domain: String,
        totalSteps: Int,
        startedAt: Long
    ): Long {
        updateWorkflowExecutionSummary(
            workflowId = workflowId,
            lastExecutedAt = startedAt,
            lastExecutionStatus = "RUNNING",
            lastExecutionSummary = "Running $totalSteps step${if (totalSteps == 1) "" else "s"}…"
        )
        return upsertAutomationExecution(
            AutomationExecutionEntity(
                workflowId = workflowId,
                workflowName = workflowName,
                domain = domain,
                status = "RUNNING",
                totalSteps = totalSteps,
                completedSteps = 0,
                verificationSummary = "Execution started",
                startedAt = startedAt
            )
        )
    }

    @Transaction
    suspend fun finishAutomationExecutionRecord(
        workflowId: Long,
        executionId: Long,
        status: String,
        completedSteps: Int,
        totalSteps: Int,
        verificationSummary: String,
        finishedAt: Long,
        checkpointJson: String = "",
        traceJson: String = ""
    ) {
        updateWorkflowExecutionSummary(
            workflowId = workflowId,
            lastExecutedAt = finishedAt,
            lastExecutionStatus = status,
            lastExecutionSummary = verificationSummary
        )
        updateAutomationExecutionCompletion(
            executionId = executionId,
            status = status,
            completedSteps = completedSteps,
            totalSteps = totalSteps,
            verificationSummary = verificationSummary,
            finishedAt = finishedAt,
            checkpointJson = checkpointJson,
            traceJson = traceJson
        )
    }

    @Transaction
    suspend fun interruptAutomationExecutionRecord(
        workflowId: Long,
        executionId: Long,
        completedSteps: Int,
        totalSteps: Int,
        verificationSummary: String,
        checkpointJson: String,
        traceJson: String,
        interruptedAt: Long
    ) {
        updateWorkflowExecutionSummary(
            workflowId = workflowId,
            lastExecutedAt = interruptedAt,
            lastExecutionStatus = "INTERRUPTED",
            lastExecutionSummary = verificationSummary
        )
        updateAutomationExecutionCompletion(
            executionId = executionId,
            status = "INTERRUPTED",
            completedSteps = completedSteps,
            totalSteps = totalSteps,
            verificationSummary = verificationSummary,
            finishedAt = interruptedAt,
            checkpointJson = checkpointJson,
            traceJson = traceJson
        )
    }

    // Phase 4B Secure Credential Vault
    @Query("SELECT * FROM credential_vault ORDER BY updatedAt DESC")
    fun observeAllCredentialVaultEntries(): Flow<List<CredentialVaultEntity>>

    @Query("SELECT COUNT(*) FROM credential_vault")
    fun observeCredentialVaultCount(): Flow<Int>

    @Query("SELECT * FROM credential_vault ORDER BY updatedAt DESC")
    suspend fun getAllCredentialVaultEntriesOnce(): List<CredentialVaultEntity>

    @Query("SELECT * FROM credential_vault WHERE credentialId = :credentialId LIMIT 1")
    suspend fun getCredentialVaultEntryById(credentialId: String): CredentialVaultEntity?

    @Query("SELECT * FROM credential_vault WHERE websiteOrigin = :websiteOrigin ORDER BY updatedAt DESC")
    suspend fun getCredentialVaultEntriesForOrigin(websiteOrigin: String): List<CredentialVaultEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCredentialVaultEntry(entry: CredentialVaultEntity)

    @Query("DELETE FROM credential_vault WHERE credentialId = :credentialId")
    suspend fun deleteCredentialVaultEntryById(credentialId: String)

    @Query("UPDATE credential_vault SET lastUsedAt = :usedAt WHERE credentialId = :credentialId")
    suspend fun markCredentialVaultEntryUsed(credentialId: String, usedAt: Long)

    @Query("DELETE FROM credential_vault")
    suspend fun clearAllCredentialVaultEntries()

    // Automation Vault (typed user values: passwords, emails, phones, addresses, custom)
    @Query("SELECT * FROM automation_vault ORDER BY updatedAt DESC")
    fun observeAllAutomationVaultEntries(): Flow<List<AutomationVaultEntryEntity>>

    @Query("SELECT COUNT(*) FROM automation_vault")
    fun observeAutomationVaultCount(): Flow<Int>

    @Query("SELECT * FROM automation_vault ORDER BY updatedAt DESC")
    suspend fun getAllAutomationVaultEntriesOnce(): List<AutomationVaultEntryEntity>

    @Query("SELECT * FROM automation_vault WHERE entryId = :entryId LIMIT 1")
    suspend fun getAutomationVaultEntryById(entryId: String): AutomationVaultEntryEntity?

    @Query("SELECT * FROM automation_vault WHERE category = :category ORDER BY updatedAt DESC")
    suspend fun getAutomationVaultEntriesByCategory(category: String): List<AutomationVaultEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAutomationVaultEntry(entry: AutomationVaultEntryEntity)

    @Query("DELETE FROM automation_vault WHERE entryId = :entryId")
    suspend fun deleteAutomationVaultEntryById(entryId: String)

    @Query("UPDATE automation_vault SET lastUsedAt = :usedAt WHERE entryId = :entryId")
    suspend fun markAutomationVaultEntryUsed(entryId: String, usedAt: Long)

    @Query("DELETE FROM automation_vault")
    suspend fun clearAllAutomationVaultEntries()

    @Query("DELETE FROM automation_vault WHERE folderId = :folderId")
    suspend fun deleteAutomationVaultEntriesByFolderId(folderId: String)

    // Automation Vault Folders
    @Query("SELECT * FROM automation_vault_folders ORDER BY createdAt ASC")
    fun observeAllAutomationVaultFolders(): Flow<List<AutomationVaultFolderEntity>>

    @Query("SELECT * FROM automation_vault_folders ORDER BY createdAt ASC")
    suspend fun getAllAutomationVaultFoldersOnce(): List<AutomationVaultFolderEntity>

    @Query("SELECT * FROM automation_vault_folders WHERE folderId = :folderId LIMIT 1")
    suspend fun getAutomationVaultFolderById(folderId: String): AutomationVaultFolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAutomationVaultFolder(folder: AutomationVaultFolderEntity)

    @Query("DELETE FROM automation_vault_folders WHERE folderId = :folderId")
    suspend fun deleteAutomationVaultFolderById(folderId: String)

    // Automation Vault Uploaded Files
    @Query("SELECT * FROM automation_vault_files ORDER BY createdAt DESC")
    fun observeAllAutomationVaultFiles(): Flow<List<AutomationVaultFileEntity>>

    @Query("SELECT * FROM automation_vault_files WHERE folderId = :folderId ORDER BY createdAt DESC")
    suspend fun getAutomationVaultFilesInFolderOnce(folderId: String): List<AutomationVaultFileEntity>

    @Query("SELECT * FROM automation_vault_files WHERE fileId = :fileId LIMIT 1")
    suspend fun getAutomationVaultFileById(fileId: String): AutomationVaultFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAutomationVaultFile(file: AutomationVaultFileEntity)

    @Query("DELETE FROM automation_vault_files WHERE fileId = :fileId")
    suspend fun deleteAutomationVaultFileById(fileId: String)

    @Query("DELETE FROM automation_vault_files WHERE folderId = :folderId")
    suspend fun deleteAutomationVaultFilesByFolderId(folderId: String)

    @Transaction
    suspend fun deleteAutomationVaultFolderAndContents(folderId: String) {
        deleteAutomationVaultFilesByFolderId(folderId)
        deleteAutomationVaultEntriesByFolderId(folderId)
        deleteAutomationVaultFolderById(folderId)
    }
}





