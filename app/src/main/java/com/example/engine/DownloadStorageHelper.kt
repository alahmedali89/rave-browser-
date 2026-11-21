package com.example.engine

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.local.DownloadItemEntity
import com.example.data.preferences.BrowserPreferences
import com.example.data.preferences.DownloadLocationMode
import java.io.File
import java.io.FileInputStream
import java.util.Locale

/**
 * Resolves Play-compliant scoped download directories, Storage Access Framework (SAF) folder
 * exports, display summaries, device storage usage, and FileProvider-backed open intents
 * without broad storage permissions.
 */
object DownloadStorageHelper {

    data class StorageSpaceInfo(
        val usedBytes: Long,
        val totalBytes: Long,
        val availableBytes: Long
    ) {
        val usedFraction: Float
            get() = if (totalBytes > 0L) {
                (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
    }

    sealed class OpenDownloadedFileResult {
        data object Opened : OpenDownloadedFileResult()
        data class Failed(val message: String) : OpenDownloadedFileResult()
    }

    private val BLOCKED_AUTO_OPEN_EXTENSIONS = setOf(
        "apk", "dex", "jar", "so", "exe", "bat", "cmd", "sh", "bin", "msi"
    )

    fun resolveDirectory(context: Context, mode: DownloadLocationMode): File {
        val appContext = context.applicationContext
        val dir = when (mode) {
            DownloadLocationMode.APP_INTERNAL -> File(appContext.filesDir, "downloads")
            DownloadLocationMode.APP_EXTERNAL,
            DownloadLocationMode.CUSTOM_SAF -> {
                appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: File(appContext.filesDir, "downloads")
            }
        }
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getStorageSpaceInfo(
        context: Context,
        mode: DownloadLocationMode = DownloadLocationMode.APP_EXTERNAL,
        downloads: List<DownloadItemEntity> = emptyList()
    ): StorageSpaceInfo {
        val rawDownloadsUsed = computeDownloadsUsedBytes(context, downloads)
        val candidates = listOfNotNull(
            runCatching { resolveDirectory(context, mode) }.getOrNull(),
            runCatching { context.applicationContext.filesDir }.getOrNull(),
            runCatching { Environment.getDataDirectory() }.getOrNull()
        )
        for (dir in candidates) {
            val stats = runCatching {
                val statFs = StatFs(dir.absolutePath)
                val total = statFs.totalBytes.coerceAtLeast(0L)
                val available = statFs.availableBytes.coerceAtLeast(0L)
                if (total > 0L) {
                    val clampedUsed = rawDownloadsUsed.coerceIn(0L, total)
                    StorageSpaceInfo(
                        usedBytes = clampedUsed,
                        totalBytes = total,
                        availableBytes = available
                    )
                } else {
                    null
                }
            }.getOrNull()
            if (stats != null) return stats
        }
        return StorageSpaceInfo(
            usedBytes = rawDownloadsUsed.coerceAtLeast(0L),
            totalBytes = 0L,
            availableBytes = 0L
        )
    }

    fun computeDownloadsUsedBytes(
        context: Context,
        downloads: List<DownloadItemEntity>
    ): Long {
        if (downloads.isEmpty()) return 0L
        val appContext = context.applicationContext
        val seenPaths = HashSet<String>(downloads.size)
        var totalUsed = 0L

        for (item in downloads) {
            if (item.status == FastDownloadEngine.STATUS_CANCELED) continue

            val path = item.localFilePath.trim()
            if (path.isNotEmpty() && !seenPaths.add(path)) {
                continue
            }

            val itemBytes = when {
                item.status == FastDownloadEngine.STATUS_COMPLETED -> {
                    when {
                        path.startsWith("content://", ignoreCase = true) -> {
                            queryContentUriSize(appContext, Uri.parse(path))
                                ?.takeIf { it > 0L }
                                ?: maxOf(item.totalBytes, item.downloadedBytes, 0L)
                        }
                        path.isNotEmpty() -> {
                            val file = File(path)
                            if (file.exists() && file.isFile) {
                                file.length().coerceAtLeast(0L)
                            } else {
                                maxOf(item.totalBytes, item.downloadedBytes, 0L)
                            }
                        }
                        else -> maxOf(item.totalBytes, item.downloadedBytes, 0L)
                    }
                }
                item.status == FastDownloadEngine.STATUS_DOWNLOADING ||
                    item.status == FastDownloadEngine.STATUS_PAUSED ||
                    item.status == FastDownloadEngine.STATUS_FAILED -> {
                    item.downloadedBytes.coerceAtLeast(0L)
                }
                else -> 0L
            }
            totalUsed += itemBytes
        }
        return totalUsed.coerceAtLeast(0L)
    }

    private fun queryContentUriSize(context: Context, uri: Uri): Long? {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !cursor.isNull(idx)) cursor.getLong(idx) else null
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    fun formatUsedOfTotalLabel(info: StorageSpaceInfo): String {
        if (info.totalBytes <= 0L) return "0 B / 0 B"
        val gb = 1024.0 * 1024.0 * 1024.0
        val mb = 1024.0 * 1024.0
        return when {
            info.usedBytes >= gb && info.totalBytes >= gb -> {
                val usedStr = formatCompactUnitValue(info.usedBytes / gb)
                val totalStr = formatCompactUnitValue(info.totalBytes / gb)
                "$usedStr / $totalStr GB"
            }
            info.usedBytes >= mb && info.totalBytes >= mb && info.totalBytes < gb -> {
                val usedStr = formatCompactUnitValue(info.usedBytes / mb)
                val totalStr = formatCompactUnitValue(info.totalBytes / mb)
                "$usedStr / $totalStr MB"
            }
            else -> "${formatCompactStorage(info.usedBytes)} / ${formatCompactStorage(info.totalBytes)}"
        }
    }

    fun formatCompactStorage(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        val tb = gb * 1024.0
        return when {
            bytes >= tb -> "${formatCompactUnitValue(bytes / tb)} TB"
            bytes >= gb -> "${formatCompactUnitValue(bytes / gb)} GB"
            bytes >= mb -> "${formatCompactUnitValue(bytes / mb)} MB"
            bytes >= kb -> "${formatCompactUnitValue(bytes / kb)} KB"
            else -> "$bytes B"
        }
    }

    private fun formatCompactUnitValue(value: Double): String {
        return when {
            value >= 100.0 -> String.format(Locale.US, "%.0f", value)
            value >= 10.0 -> String.format(Locale.US, "%.1f", value)
            else -> String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
        }
    }

    fun extractFolderLabelFromUri(uriString: String): String {
        if (uriString.isBlank()) return ""
        return runCatching {
            val uri = Uri.parse(uriString)
            val segment = uri.lastPathSegment.orEmpty()
            val cleaned = segment.substringAfter(':', segment).trim('/')
            cleaned.ifBlank { uri.host.orEmpty() }
        }.getOrDefault("")
    }

    fun formatLocationSummary(
        context: Context,
        preferences: BrowserPreferences
    ): Pair<String, String> {
        return formatLocationSummary(
            context = context,
            mode = preferences.downloadLocationMode,
            customTreeUri = preferences.customDownloadTreeUri,
            customLabel = preferences.customDownloadFolderLabel
        )
    }

    fun formatLocationSummary(
        context: Context,
        mode: DownloadLocationMode,
        customTreeUri: String,
        customLabel: String
    ): Pair<String, String> {
        return when (mode) {
            DownloadLocationMode.APP_EXTERNAL -> {
                val path = resolveDirectory(context, DownloadLocationMode.APP_EXTERNAL).absolutePath
                "Downloads" to path
            }
            DownloadLocationMode.APP_INTERNAL -> {
                val path = resolveDirectory(context, DownloadLocationMode.APP_INTERNAL).absolutePath
                "Private storage" to path
            }
            DownloadLocationMode.CUSTOM_SAF -> {
                val label = customLabel.ifBlank { extractFolderLabelFromUri(customTreeUri) }
                    .ifBlank { "Selected folder" }
                val pathOrUri = customTreeUri.ifBlank {
                    resolveDirectory(context, DownloadLocationMode.APP_EXTERNAL).absolutePath
                }
                label to pathOrUri
            }
        }
    }

    fun copyFileToSafTree(
        context: Context,
        sourceFile: File,
        treeUriString: String,
        mimeType: String,
        fileName: String
    ): Uri? {
        if (!sourceFile.exists() || treeUriString.isBlank()) return null
        return runCatching {
            val resolver = context.applicationContext.contentResolver
            val treeUri = Uri.parse(treeUriString)
            val parentDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
            val safeMime = mimeType.ifBlank { "application/octet-stream" }
            val destUri = DocumentsContract.createDocument(
                resolver,
                parentDocUri,
                safeMime,
                fileName
            ) ?: return null

            resolver.openOutputStream(destUri)?.use { output ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
                output.flush()
            } ?: return null

            destUri
        }.getOrNull()
    }

    fun isSafeToAutoOpen(fileName: String, mimeType: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
        if (ext in BLOCKED_AUTO_OPEN_EXTENSIONS) {
            return false
        }
        val lowerMime = mimeType.lowercase(Locale.US)
        if (lowerMime == "application/vnd.android.package-archive") {
            return false
        }
        return true
    }

    fun resolveContentUriForDownload(
        context: Context,
        localFilePath: String,
        fileName: String
    ): Uri? {
        if (localFilePath.isBlank()) return null
        val appContext = context.applicationContext
        if (localFilePath.startsWith("content://", ignoreCase = true)) {
            return Uri.parse(localFilePath)
        }
        val directFile = File(localFilePath)
        val resolvedFile = when {
            directFile.exists() && directFile.isFile -> directFile
            fileName.isNotBlank() -> {
                listOf(
                    File(resolveDirectory(appContext, DownloadLocationMode.APP_EXTERNAL), fileName),
                    File(resolveDirectory(appContext, DownloadLocationMode.APP_INTERNAL), fileName)
                ).firstOrNull { it.exists() && it.isFile }
            }
            else -> null
        } ?: return null

        return runCatching {
            FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                resolvedFile
            )
        }.getOrNull()
    }

    fun buildOpenFileIntent(
        context: Context,
        localFilePath: String,
        fileName: String,
        mimeType: String,
        userInitiated: Boolean = false
    ): Intent? {
        if (localFilePath.isBlank()) return null
        if (!userInitiated && !isSafeToAutoOpen(fileName, mimeType)) {
            return null
        }
        val appContext = context.applicationContext
        val uri = resolveContentUriForDownload(appContext, localFilePath, fileName) ?: return null
        val resolvedMime = FastDownloadEngine.resolveMimeType(fileName, mimeType)
            .ifBlank { "*/*" }

        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, resolvedMime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (userInitiated) {
            return viewIntent
        }
        return if (viewIntent.resolveActivity(appContext.packageManager) != null) {
            viewIntent
        } else {
            null
        }
    }

    fun openDownloadedFile(
        context: Context,
        localFilePath: String,
        fileName: String,
        mimeType: String
    ): OpenDownloadedFileResult {
        val uri = resolveContentUriForDownload(context, localFilePath, fileName)
            ?: return OpenDownloadedFileResult.Failed(
                context.getString(R.string.downloads_open_failed_missing)
            )
        val resolvedMime = FastDownloadEngine.resolveMimeType(fileName, mimeType)
            .ifBlank { "*/*" }

        val primaryIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, resolvedMime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // First try direct viewer / system handler for the specific MIME type.
        if (tryStartActivity(context, primaryIntent)) {
            return OpenDownloadedFileResult.Opened
        }

        // Second try chooser with the specific MIME type (ensuring permission grant flags propagate).
        val specificChooser = Intent.createChooser(primaryIntent, fileName).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (tryStartActivity(context, specificChooser)) {
            return OpenDownloadedFileResult.Opened
        }

        // Third try wildcard chooser so Files / archive / document viewers can handle the URI.
        val wildcardIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val wildcardChooser = Intent.createChooser(wildcardIntent, fileName).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (tryStartActivity(context, wildcardChooser)) {
            return OpenDownloadedFileResult.Opened
        }

        // Final fallback for packages where direct APK install isn't permitted from third-party apps.
        val systemDownloadsIntent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (tryStartActivity(context, systemDownloadsIntent)) {
            return OpenDownloadedFileResult.Opened
        }

        return OpenDownloadedFileResult.Failed(
            context.getString(R.string.downloads_open_failed_no_viewer)
        )
    }

    private fun tryStartActivity(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }
}
