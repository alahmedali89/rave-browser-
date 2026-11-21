package com.example.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground service (`dataSync`) that keeps active multi-chunk Turbo downloads alive
 * when the user switches to other apps, locks the screen, or switches browser tabs.
 *
 * Also serves as the single unified progress notification for active downloads so
 * Android never displays two duplicate progress bar notifications.
 */
class DownloadForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        acquireLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_FOREGROUND) {
            isForegroundRunning.set(false)
            isStartRequested.set(false)
            releaseLocks()
            runCatching {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            }
            stopSelf()
            return START_NOT_STICKY
        }

        val activeCount = intent?.getIntExtra(EXTRA_ACTIVE_COUNT, 1)?.coerceAtLeast(1) ?: 1
        val title = intent?.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() }
            ?: if (activeCount == 1) "Downloading file…" else "Downloading $activeCount files…"
        val subtitle = intent?.getStringExtra(EXTRA_SUBTITLE)?.takeIf { it.isNotBlank() }
            ?: "Turbo Engine active"
        val progressPercent = intent?.getIntExtra(EXTRA_PROGRESS_PERCENT, -1) ?: -1

        val notification = buildForegroundNotification(
            context = this,
            title = title,
            subtitle = subtitle,
            progressPercent = progressPercent
        )

        val promoted = runCatching {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
            ServiceCompat.startForeground(
                this,
                FOREGROUND_NOTIFICATION_ID,
                notification,
                fgsType
            )
            true
        }.getOrDefault(false)

        if (promoted) {
            isForegroundRunning.set(true)
            isStartRequested.set(true)
        }
        acquireLocks()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        isForegroundRunning.set(false)
        isStartRequested.set(false)
        releaseLocks()
        super.onDestroy()
    }

    private fun acquireLocks() {
        runCatching {
            if (wakeLock?.isHeld != true) {
                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = pm?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "ApexBrowser:TurboDownloadWakeLock"
                )?.apply {
                    setReferenceCounted(false)
                    acquire(60 * 60 * 1000L)
                }
            }
        }
        runCatching {
            if (wifiLock?.isHeld != true) {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val lockMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    @Suppress("DEPRECATION")
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiLock = wm?.createWifiLock(lockMode, "ApexBrowser:TurboDownloadWifiLock")?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        }
    }

    private fun releaseLocks() {
        runCatching {
            val lock = wakeLock
            wakeLock = null
            if (lock != null && lock.isHeld) {
                lock.release()
            }
        }
        runCatching {
            val lock = wifiLock
            wifiLock = null
            if (lock != null && lock.isHeld) {
                lock.release()
            }
        }
    }

    companion object {
        const val FOREGROUND_NOTIFICATION_ID = 39_999
        const val ACTION_START_OR_UPDATE = "com.example.action.DOWNLOAD_FGS_START_OR_UPDATE"
        const val ACTION_STOP_FOREGROUND = "com.example.action.DOWNLOAD_FGS_STOP"
        const val EXTRA_ACTIVE_COUNT = "extra_active_count"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_SUBTITLE = "extra_subtitle"
        const val EXTRA_PROGRESS_PERCENT = "extra_progress_percent"

        private val isForegroundRunning = AtomicBoolean(false)
        private val isStartRequested = AtomicBoolean(false)

        fun isActiveOrRequested(): Boolean = isForegroundRunning.get() || isStartRequested.get()

        fun buildForegroundNotification(
            context: Context,
            title: String,
            subtitle: String,
            progressPercent: Int
        ): Notification {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (manager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runCatching {
                    val channel = NotificationChannel(
                        DownloadNotificationHelper.CHANNEL_ID_DOWNLOADS,
                        context.getString(R.string.notification_channel_downloads_name),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = context.getString(R.string.notification_channel_downloads_desc)
                        setShowBadge(false)
                        enableVibration(false)
                        setSound(null, null)
                    }
                    manager.createNotificationChannel(channel)
                }
            }

            val openIntent = Intent(context, MainActivity::class.java).apply {
                action = DownloadNotificationHelper.ACTION_OPEN_DOWNLOADS
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                FOREGROUND_NOTIFICATION_ID,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val hasDeterminateProgress = progressPercent in 0..100
            return NotificationCompat.Builder(context, DownloadNotificationHelper.CHANNEL_ID_DOWNLOADS)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(subtitle)
                .setSubText(if (hasDeterminateProgress) "$progressPercent%" else "Downloading")
                .setContentIntent(pendingIntent)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setOngoing(true)
                .setAutoCancel(false)
                .setProgress(
                    if (hasDeterminateProgress) 100 else 0,
                    if (hasDeterminateProgress) progressPercent else 0,
                    !hasDeterminateProgress
                )
                .build()
        }

        fun startOrUpdate(
            context: Context,
            activeCount: Int,
            title: String,
            subtitle: String,
            progressPercent: Int,
            postNotification: Boolean = true
        ): Boolean {
            val appContext = context.applicationContext
            // If the service is already promoted in foreground, update its notification directly
            // without calling ContextCompat.startForegroundService repeatedly from background.
            if (isForegroundRunning.get()) {
                if (postNotification) {
                    runCatching {
                        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                        val notification = buildForegroundNotification(
                            context = appContext,
                            title = title,
                            subtitle = subtitle,
                            progressPercent = progressPercent
                        )
                        manager?.notify(FOREGROUND_NOTIFICATION_ID, notification)
                    }
                }
                return true
            }

            return runCatching {
                isStartRequested.set(true)
                val intent = Intent(appContext, DownloadForegroundService::class.java).apply {
                    action = ACTION_START_OR_UPDATE
                    putExtra(EXTRA_ACTIVE_COUNT, activeCount)
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_SUBTITLE, subtitle)
                    putExtra(EXTRA_PROGRESS_PERCENT, progressPercent)
                }
                ContextCompat.startForegroundService(appContext, intent)
                true
            }.getOrElse {
                isStartRequested.set(false)
                false
            }
        }

        fun stop(context: Context) {
            isForegroundRunning.set(false)
            isStartRequested.set(false)
            val appContext = context.applicationContext
            runCatching {
                val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                manager?.cancel(FOREGROUND_NOTIFICATION_ID)
            }
            runCatching {
                val stopIntent = Intent(appContext, DownloadForegroundService::class.java)
                appContext.stopService(stopIntent)
            }
        }
    }
}
