package com.example.engine

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.DownloadItemEntity
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Posts and updates system status bar & notification drawer progress for active,
 * paused, completed, and failed downloads.
 */
class DownloadNotificationHelper(private val context: Context) {

    private data class ProgressSnapshot(
        val timestampMs: Long,
        val percent: Int,
        val hasKnownTotal: Boolean
    )

    private val lastProgressSnapshots = ConcurrentHashMap<Long, ProgressSnapshot>()
    private val trackedIds = ConcurrentHashMap.newKeySet<Long>()
    @Volatile
    private var channelCreated = false

    private val notificationManager: NotificationManager?
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    fun notifyDownloadState(
        item: DownloadItemEntity,
        forceImmediate: Boolean = false
    ) {
        if (item.id <= 0L) return

        if (item.status == FastDownloadEngine.STATUS_CANCELED) {
            cancel(item.id)
            return
        }

        if (!hasNotificationPermission()) {
            return
        }

        val manager = notificationManager ?: return
        ensureChannel(manager)

        val hasKnownTotal = item.totalBytes > 0L
        val percent = if (hasKnownTotal) {
            ((item.downloadedBytes * 100L) / item.totalBytes).toInt().coerceIn(0, 100)
        } else {
            0
        }

        val now = System.currentTimeMillis()
        if (item.status == FastDownloadEngine.STATUS_DOWNLOADING && !forceImmediate) {
            val prev = lastProgressSnapshots[item.id]
            if (prev != null &&
                now - prev.timestampMs < PROGRESS_THROTTLE_MS &&
                abs(percent - prev.percent) < 2 &&
                prev.hasKnownTotal == hasKnownTotal
            ) {
                return
            }
            lastProgressSnapshots[item.id] = ProgressSnapshot(now, percent, hasKnownTotal)
        } else if (item.status == FastDownloadEngine.STATUS_DOWNLOADING) {
            lastProgressSnapshots[item.id] = ProgressSnapshot(now, percent, hasKnownTotal)
        } else {
            lastProgressSnapshots.remove(item.id)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_DOWNLOADS)
            .setContentIntent(createOpenDownloadsPendingIntent(item.id))
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        when (item.status) {
            FastDownloadEngine.STATUS_DOWNLOADING -> {
                val downloadedLabel = FastDownloadEngine.formatBytes(item.downloadedBytes)
                val sizeLabel = if (hasKnownTotal) {
                    "$downloadedLabel / ${FastDownloadEngine.formatBytes(item.totalBytes)}"
                } else {
                    downloadedLabel
                }
                val speedSuffix = if (item.speedBytesPerSec > 0L) {
                    " • ${FastDownloadEngine.formatSpeed(item.speedBytesPerSec)}"
                } else {
                    ""
                }
                builder
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle(item.fileName)
                    .setContentText("$sizeLabel$speedSuffix")
                    .setSubText(if (hasKnownTotal) "$percent%" else "Downloading")
                    .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setProgress(
                        if (hasKnownTotal) 100 else 0,
                        if (hasKnownTotal) percent else 0,
                        !hasKnownTotal
                    )
            }

            FastDownloadEngine.STATUS_PAUSED -> {
                val downloadedLabel = FastDownloadEngine.formatBytes(item.downloadedBytes)
                val sizeLabel = if (hasKnownTotal) {
                    "$downloadedLabel / ${FastDownloadEngine.formatBytes(item.totalBytes)}"
                } else {
                    downloadedLabel
                }
                builder
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle(item.fileName)
                    .setContentText("Paused • $sizeLabel")
                    .setSubText(if (hasKnownTotal) "$percent% paused" else "Paused")
                    .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                    .setOngoing(false)
                    .setAutoCancel(false)
                    .setProgress(
                        if (hasKnownTotal) 100 else 0,
                        if (hasKnownTotal) percent else 0,
                        false
                    )
            }

            FastDownloadEngine.STATUS_COMPLETED -> {
                val finalBytes = item.totalBytes.coerceAtLeast(item.downloadedBytes)
                builder
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle(item.fileName)
                    .setContentText("Download complete • ${FastDownloadEngine.formatBytes(finalBytes)}")
                    .setSubText("Complete")
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .setProgress(0, 0, false)
            }

            FastDownloadEngine.STATUS_FAILED -> {
                builder
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                    .setContentTitle("Download failed: ${item.fileName}")
                    .setContentText(
                        item.errorMessage?.takeIf { it.isNotBlank() }
                            ?: "Tap to retry in Downloads"
                    )
                    .setSubText("Failed")
                    .setCategory(NotificationCompat.CATEGORY_ERROR)
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .setProgress(0, 0, false)
            }

            else -> return
        }

        trackedIds.add(item.id)
        runCatching {
            manager.notify(notificationIdFor(item.id), builder.build())
        }
    }

    fun cancel(downloadId: Long) {
        lastProgressSnapshots.remove(downloadId)
        trackedIds.remove(downloadId)
        runCatching {
            notificationManager?.cancel(notificationIdFor(downloadId))
        }
    }

    fun cancelAll() {
        lastProgressSnapshots.clear()
        val ids = trackedIds.toList()
        trackedIds.clear()
        val manager = notificationManager ?: return
        ids.forEach { id ->
            runCatching { manager.cancel(notificationIdFor(id)) }
        }
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (channelCreated || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val channel = NotificationChannel(
            CHANNEL_ID_DOWNLOADS,
            context.getString(R.string.notification_channel_downloads_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_channel_downloads_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        runCatching {
            manager.createNotificationChannel(channel)
            channelCreated = true
        }
    }

    private fun createOpenDownloadsPendingIntent(downloadId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_DOWNLOADS
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(
            context,
            notificationIdFor(downloadId),
            intent,
            flags
        )
    }

    companion object {
        const val CHANNEL_ID_DOWNLOADS = "apex_downloads_progress_v1"
        const val ACTION_OPEN_DOWNLOADS = "com.example.action.OPEN_DOWNLOADS"
        private const val NOTIFICATION_ID_BASE = 40_000
        private const val PROGRESS_THROTTLE_MS = 250L

        fun notificationIdFor(downloadId: Long): Int {
            return NOTIFICATION_ID_BASE + (downloadId and 0x0FFF_FFFFL).toInt()
        }
    }
}
