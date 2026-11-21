package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PrivacyStatSummaryEntity::class,
        BlockedDomainLogEntity::class,
        ExtractionRuleEntity::class,
        BookmarkEntity::class,
        SearchHistoryEntity::class,
        TopVisitEntity::class,
        BrowsingHistoryEntity::class,
        DownloadItemEntity::class,
        SavedPasswordEntity::class,
        AutomationWorkflowEntity::class,
        AutomationWorkflowVersionEntity::class,
        AutomationExecutionEntity::class,
        CredentialVaultEntity::class,
        AutomationVaultEntryEntity::class,
        AutomationVaultFolderEntity::class,
        AutomationVaultFileEntity::class
    ],
    version = 18,
    exportSchema = true
)
abstract class ApexDatabase : RoomDatabase() {

    abstract fun apexDao(): ApexDao

    companion object {
        val MIGRATION_12_13: Migration = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `automation_executions_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `workflowId` INTEGER NOT NULL,
                        `workflowName` TEXT NOT NULL,
                        `domain` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `totalSteps` INTEGER NOT NULL,
                        `completedSteps` INTEGER NOT NULL,
                        `verificationSummary` TEXT NOT NULL,
                        `startedAt` INTEGER NOT NULL,
                        `finishedAt` INTEGER,
                        FOREIGN KEY(`workflowId`) REFERENCES `automation_workflows`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `automation_executions_new` (
                        `id`, `workflowId`, `workflowName`, `domain`, `status`,
                        `totalSteps`, `completedSteps`, `verificationSummary`, `startedAt`, `finishedAt`
                    )
                    SELECT
                        e.`id`, e.`workflowId`, e.`workflowName`, e.`domain`, e.`status`,
                        e.`totalSteps`, e.`completedSteps`, e.`verificationSummary`, e.`startedAt`, e.`finishedAt`
                    FROM `automation_executions` AS e
                    WHERE EXISTS (
                        SELECT 1 FROM `automation_workflows` AS w WHERE w.`id` = e.`workflowId`
                    )
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `automation_executions`")
                db.execSQL("ALTER TABLE `automation_executions_new` RENAME TO `automation_executions`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_automation_executions_workflowId` ON `automation_executions` (`workflowId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_automation_executions_startedAt` ON `automation_executions` (`startedAt`)"
                )
            }
        }

        val MIGRATION_13_14: Migration = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `credential_vault` (
                        `credentialId` TEXT NOT NULL,
                        `websiteOrigin` TEXT NOT NULL,
                        `displayOrigin` TEXT NOT NULL,
                        `label` TEXT NOT NULL,
                        `usernameIdentifier` TEXT NOT NULL,
                        `encryptedUsername` TEXT NOT NULL,
                        `encryptedPassword` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `lastUsedAt` INTEGER,
                        PRIMARY KEY(`credentialId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_credential_vault_websiteOrigin` ON `credential_vault` (`websiteOrigin`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_credential_vault_updatedAt` ON `credential_vault` (`updatedAt`)"
                )
            }
        }

        val MIGRATION_14_15: Migration = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `automation_executions` ADD COLUMN `checkpointJson` TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE `automation_executions` ADD COLUMN `traceJson` TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        val MIGRATION_15_16: Migration = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `automation_workflows` ADD COLUMN `workflowUuid` TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE `automation_workflows` ADD COLUMN `currentVersion` INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL(
                    "ALTER TABLE `automation_workflows` ADD COLUMN `folder` TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE `automation_workflows` ADD COLUMN `category` TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE `automation_workflows` ADD COLUMN `tagsJson` TEXT NOT NULL DEFAULT '[]'"
                )
                db.execSQL(
                    "ALTER TABLE `automation_workflows` ADD COLUMN `isFavorite` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE `automation_workflows` ADD COLUMN `isArchived` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE `automation_workflows` ADD COLUMN `archivedAt` INTEGER"
                )
                db.execSQL(
                    """
                    UPDATE `automation_workflows`
                    SET `workflowUuid` = lower(
                        hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' ||
                        substr(hex(randomblob(2)), 2) || '-a' || substr(hex(randomblob(2)), 2) || '-' ||
                        hex(randomblob(6))
                    )
                    WHERE `workflowUuid` = ''
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_automation_workflows_workflowUuid` ON `automation_workflows` (`workflowUuid`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_automation_workflows_isArchived` ON `automation_workflows` (`isArchived`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_automation_workflows_isFavorite` ON `automation_workflows` (`isFavorite`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_automation_workflows_folder` ON `automation_workflows` (`folder`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `workflow_versions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `workflowId` INTEGER NOT NULL,
                        `workflowUuid` TEXT NOT NULL,
                        `versionUuid` TEXT NOT NULL,
                        `versionNumber` INTEGER NOT NULL,
                        `workflowName` TEXT NOT NULL,
                        `domain` TEXT NOT NULL,
                        `sourceUrl` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `instructionsText` TEXT NOT NULL,
                        `stepsJson` TEXT NOT NULL,
                        `stepCount` INTEGER NOT NULL,
                        `changeSummary` TEXT NOT NULL,
                        `contentHash` TEXT NOT NULL,
                        `restoredFromVersion` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`workflowId`) REFERENCES `automation_workflows`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_workflow_versions_workflowId` ON `workflow_versions` (`workflowId`)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_workflow_versions_workflowId_versionNumber` ON `workflow_versions` (`workflowId`, `versionNumber`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_workflow_versions_createdAt` ON `workflow_versions` (`createdAt`)"
                )
                db.execSQL(
                    """
                    INSERT INTO `workflow_versions` (
                        `workflowId`, `workflowUuid`, `versionUuid`, `versionNumber`,
                        `workflowName`, `domain`, `sourceUrl`, `type`, `instructionsText`,
                        `stepsJson`, `stepCount`, `changeSummary`, `contentHash`,
                        `restoredFromVersion`, `createdAt`
                    )
                    SELECT
                        `id`,
                        `workflowUuid`,
                        lower(
                            hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' ||
                            substr(hex(randomblob(2)), 2) || '-a' || substr(hex(randomblob(2)), 2) || '-' ||
                            hex(randomblob(6))
                        ),
                        `currentVersion`,
                        `name`,
                        `domain`,
                        `sourceUrl`,
                        `type`,
                        `instructionsText`,
                        `stepsJson`,
                        `stepCount`,
                        'Migrated initial version',
                        '',
                        NULL,
                        `updatedAt`
                    FROM `automation_workflows`
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_16_17: Migration = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `automation_vault` (
                        `entryId` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `label` TEXT NOT NULL,
                        `websiteOrigin` TEXT NOT NULL,
                        `domain` TEXT NOT NULL,
                        `encryptedValue` TEXT NOT NULL,
                        `maskedPreview` TEXT NOT NULL,
                        `isSensitive` INTEGER NOT NULL,
                        `sourceWorkflowId` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `lastUsedAt` INTEGER,
                        PRIMARY KEY(`entryId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_automation_vault_category` ON `automation_vault` (`category`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_automation_vault_websiteOrigin` ON `automation_vault` (`websiteOrigin`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_automation_vault_updatedAt` ON `automation_vault` (`updatedAt`)"
                )
            }
        }

        val MIGRATION_17_18: Migration = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `automation_vault` ADD COLUMN `folderId` TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_automation_vault_folderId` ON `automation_vault` (`folderId`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `automation_vault_folders` (
                        `folderId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `categoryId` TEXT NOT NULL,
                        `isDefault` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`folderId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_automation_vault_folders_updatedAt` ON `automation_vault_folders` (`updatedAt`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `automation_vault_files` (
                        `fileId` TEXT NOT NULL,
                        `folderId` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `localFilePath` TEXT NOT NULL,
                        `textPreview` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`fileId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_automation_vault_files_folderId` ON `automation_vault_files` (`folderId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_automation_vault_files_createdAt` ON `automation_vault_files` (`createdAt`)"
                )
            }
        }

        @Volatile
        private var INSTANCE: ApexDatabase? = null

        fun getInstance(context: Context): ApexDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ApexDatabase::class.java,
                    "apex_browser_fresh.db"
                )
                    .addMigrations(
                        MIGRATION_12_13,
                        MIGRATION_13_14,
                        MIGRATION_14_15,
                        MIGRATION_15_16,
                        MIGRATION_16_17,
                        MIGRATION_17_18
                    )
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
