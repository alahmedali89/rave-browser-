package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "privacy_stats")
data class PrivacyStatSummaryEntity(
    @PrimaryKey val id: Int = 1,
    val trackersBlocked: Int = 0,
    val adsBlocked: Int = 0,
    val httpsUpgrades: Int = 0,
    val bytesSaved: Long = 0L,
    val timeSavedMs: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "blocked_domain_logs")
data class BlockedDomainLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pageHost: String,
    val blockedDomain: String,
    val category: String,
    val savedBytes: Long,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "extraction_rules")
data class ExtractionRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val promptDescription: String,
    val itemSelector: String,
    val titleSubSelector: String,
    val valueSubSelector: String,
    val linkSubSelector: String,
    val lastRunCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "bookmarks",
    indices = [Index(value = ["url"], unique = true)]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "search_history",
    indices = [Index(value = ["query"], unique = true)]
)
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val searchEngineName: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "top_visits",
    indices = [Index(value = ["url"], unique = true)]
)
data class TopVisitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "browsing_history",
    indices = [Index(value = ["visitedAt"]), Index(value = ["url"])]
)
data class BrowsingHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val host: String,
    val isHttps: Boolean = true,
    val trackersBlockedOnVisit: Int = 0,
    val visitCount: Int = 1,
    val visitedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "downloads",
    indices = [Index(value = ["createdAt"]), Index(value = ["status"])]
)
data class DownloadItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val url: String,
    val mimeType: String = "application/octet-stream",
    val category: String = "Other",
    val totalBytes: Long = -1L,
    val downloadedBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val chunksCount: Int = 4,
    val status: String = "DOWNLOADING",
    val localFilePath: String = "",
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

@Entity(
    tableName = "saved_passwords",
    indices = [
        Index(value = ["host"]),
        Index(value = ["updatedAt"]),
        Index(value = ["host", "username"], unique = true)
    ]
)
data class SavedPasswordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val siteName: String,
    val siteUrl: String,
    val host: String,
    val username: String,
    val encryptedPassword: String,
    val notes: String = "",
    val category: String = "Personal",
    val isFavorite: Boolean = false,
    val strengthScore: Int = 0,
    val passwordFingerprint: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null
)

@Entity(
    tableName = "automation_workflows",
    indices = [
        Index(value = ["domain"]),
        Index(value = ["type"]),
        Index(value = ["updatedAt"]),
        Index(value = ["lastExecutedAt"]),
        Index(value = ["workflowUuid"]),
        Index(value = ["isArchived"]),
        Index(value = ["isFavorite"]),
        Index(value = ["folder"])
    ]
)
data class AutomationWorkflowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val domain: String,
    val sourceUrl: String,
    val type: String, // "RECORDED" or "MANUAL"
    val instructionsText: String = "",
    val stepsJson: String = "[]",
    val stepCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastExecutedAt: Long? = null,
    val lastExecutionStatus: String = "NEVER_RUN", // NEVER_RUN, RUNNING, COMPLETED, FAILED, CANCELLED
    val lastExecutionSummary: String = "",
    val workflowUuid: String = "",
    val currentVersion: Int = 1,
    val folder: String = "",
    val category: String = "",
    val tagsJson: String = "[]",
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val archivedAt: Long? = null
)

@Entity(
    tableName = "workflow_versions",
    foreignKeys = [
        ForeignKey(
            entity = AutomationWorkflowEntity::class,
            parentColumns = ["id"],
            childColumns = ["workflowId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["workflowId"]),
        Index(value = ["workflowId", "versionNumber"], unique = true),
        Index(value = ["createdAt"])
    ]
)
data class AutomationWorkflowVersionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workflowId: Long,
    val workflowUuid: String,
    val versionUuid: String,
    val versionNumber: Int,
    val workflowName: String,
    val domain: String,
    val sourceUrl: String,
    val type: String,
    val instructionsText: String = "",
    val stepsJson: String,
    val stepCount: Int = 0,
    val changeSummary: String = "",
    val contentHash: String = "",
    val restoredFromVersion: Int? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "automation_executions",
    foreignKeys = [
        ForeignKey(
            entity = AutomationWorkflowEntity::class,
            parentColumns = ["id"],
            childColumns = ["workflowId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["workflowId"]),
        Index(value = ["startedAt"])
      ]
)
data class AutomationExecutionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workflowId: Long,
    val workflowName: String,
    val domain: String,
    val status: String, // RUNNING, INTERRUPTED, COMPLETED, FAILED, CANCELLED
    val totalSteps: Int = 0,
    val completedSteps: Int = 0,
    val verificationSummary: String = "",
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val checkpointJson: String = "",
    val traceJson: String = ""
)

@Entity(
    tableName = "credential_vault",
    indices = [
        Index(value = ["websiteOrigin"]),
        Index(value = ["updatedAt"])
    ]
)
data class CredentialVaultEntity(
    @PrimaryKey
    val credentialId: String,
    val websiteOrigin: String,
    val displayOrigin: String,
    val label: String,
    val usernameIdentifier: String,
    val encryptedUsername: String,
    val encryptedPassword: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null
)

@Entity(
    tableName = "automation_vault",
    indices = [
        Index(value = ["category"]),
        Index(value = ["websiteOrigin"]),
        Index(value = ["updatedAt"]),
        Index(value = ["folderId"])
    ]
)
data class AutomationVaultEntryEntity(
    @PrimaryKey
    val entryId: String,
    val category: String, // AutomationVaultCategory.name (PASSWORD, EMAIL, PHONE, USERNAME, NAME, ADDRESS, CUSTOM)
    val label: String,
    val websiteOrigin: String = "", // canonical origin or "" for Global / all sites
    val domain: String = "Global",
    val encryptedValue: String, // AES-256-GCM encrypted payload
    val maskedPreview: String,
    val isSensitive: Boolean = false,
    val sourceWorkflowId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null,
    val folderId: String = ""
)

@Entity(
    tableName = "automation_vault_folders",
    indices = [
        Index(value = ["updatedAt"])
    ]
)
data class AutomationVaultFolderEntity(
    @PrimaryKey
    val folderId: String,
    val name: String,
    val categoryId: String = "CUSTOM",
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "automation_vault_files",
    indices = [
        Index(value = ["folderId"]),
        Index(value = ["createdAt"])
    ]
)
data class AutomationVaultFileEntity(
    @PrimaryKey
    val fileId: String,
    val folderId: String,
    val fileName: String,
    val mimeType: String = "application/octet-stream",
    val sizeBytes: Long = 0L,
    val localFilePath: String,
    val textPreview: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class PreparedWorkflowImportRecord(
    val sentinelId: Long,
    val workflow: AutomationWorkflowEntity,
    val versionSnapshot: AutomationWorkflowVersionEntity,
    val targetExistingWorkflowId: Long? = null,
    val expectedExistingVersion: Int? = null
)





