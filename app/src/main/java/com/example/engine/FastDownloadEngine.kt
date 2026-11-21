package com.example.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import com.example.data.local.DownloadItemEntity
import com.example.data.preferences.BrowserPreferences
import com.example.data.preferences.DownloadLocationMode
import com.example.data.repository.BrowserRepository
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.Socket
import java.net.URL
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray
import javax.net.SocketFactory
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response

/**
 * High-speed 8× Parallel Multi-TCP Work-Stealing Turbo & Positional NIO FileChannel Download Engine.
 *
 * Features:
 * - True multi-TCP acceleration using `OkHttpClient` with `Protocol.HTTP_1_1` (preventing HTTP/2
 *   single-connection multiplexing bottlenecks), `512 KB` socket receive buffers, and `TCP_NODELAY`.
 * - Dynamic work-stealing micro-segment queue so faster sockets pick up remaining byte ranges
 *   instead of stalling on a slow stream.
 * - Lock-free positional `FileChannel.write(ByteBuffer, offset)` into a single `.downloading` file
 *   with zero post-download merge copy, plus lightweight `.apexmeta` resume checkpointing.
 * - Process-wide foreground service (`DownloadForegroundService` `dataSync`) with a single unified
 *   progress notification, WakeLock + WifiLock, and engine-owned connectivity recovery.
 * - Live in-memory `liveProgress` `StateFlow` so progress sliders stay fluid across screen switches.
 */
class FastDownloadEngine(
    private val context: Context,
    private val repository: BrowserRepository
) {
    data class LiveDownloadTelemetry(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long,
        val chunksCount: Int,
        val status: String
    )

    private val appContext: Context = context.applicationContext
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private val pausedFlags = ConcurrentHashMap<Long, AtomicBoolean>()
    private val canceledFlags = ConcurrentHashMap<Long, AtomicBoolean>()
    private val activeCalls = ConcurrentHashMap<Long, MutableSet<Call>>()
    private val downloadMutexes = ConcurrentHashMap<Long, Mutex>()
    private val downloadUserAgents = ConcurrentHashMap<Long, String>()
    private val downloadSafTargets = ConcurrentHashMap<Long, String>()
    private val activeDownloadSummaries = ConcurrentHashMap<Long, DownloadItemEntity>()
    private val notificationHelper = DownloadNotificationHelper(appContext)
    private val lastForegroundSyncMs = AtomicLong(0L)
    private val isNetworkAvailable = AtomicBoolean(true)
    private val networkRestoredSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    @Volatile
    private var currentPreferences: BrowserPreferences = BrowserPreferences()

    private val _autoOpenEvents = MutableSharedFlow<DownloadItemEntity>(extraBufferCapacity = 8)
    val autoOpenEvents: SharedFlow<DownloadItemEntity> = _autoOpenEvents.asSharedFlow()

    private val _liveProgress = MutableStateFlow<Map<Long, LiveDownloadTelemetry>>(emptyMap())
    val liveProgress: StateFlow<Map<Long, LiveDownloadTelemetry>> = _liveProgress.asStateFlow()

    private val highThroughputSocketFactory = object : SocketFactory() {
        private val delegate = getDefault()

        private fun configure(socket: Socket): Socket {
            runCatching {
                socket.receiveBufferSize = SOCKET_RECEIVE_BUFFER_BYTES
                socket.tcpNoDelay = true
            }
            return socket
        }

        override fun createSocket(): Socket = configure(delegate.createSocket())
        override fun createSocket(host: String?, port: Int): Socket =
            configure(delegate.createSocket(host, port))
        override fun createSocket(
            host: String?,
            port: Int,
            localHost: InetAddress?,
            localPort: Int
        ): Socket = configure(delegate.createSocket(host, port, localHost, localPort))

        override fun createSocket(host: InetAddress?, port: Int): Socket =
            configure(delegate.createSocket(host, port))

        override fun createSocket(
            address: InetAddress?,
            port: Int,
            localAddress: InetAddress?,
            localPort: Int
        ): Socket = configure(delegate.createSocket(address, port, localAddress, localPort))
    }

    // Parallel multi-TCP client forcing HTTP/1.1 connections so each worker gets an independent
    // TCP socket and congestion window instead of coalescing onto one HTTP/2 connection.
    private val parallelHttpClient: OkHttpClient by lazy {
        val dispatcher = Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 16
        }
        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(ConnectionPool(24, 2, TimeUnit.MINUTES))
            .protocols(listOf(Protocol.HTTP_1_1))
            .socketFactory(highThroughputSocketFactory)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    // Standard ALPN client for single-stream downloads and HTTP/2-only fallback.
    private val standardHttpClient: OkHttpClient by lazy {
        val dispatcher = Dispatcher().apply {
            maxRequests = 32
            maxRequestsPerHost = 8
        }
        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(ConnectionPool(12, 2, TimeUnit.MINUTES))
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .socketFactory(highThroughputSocketFactory)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val defaultUserAgent =
        "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    init {
        isNetworkAvailable.set(queryCurrentConnectivity())
        registerEngineNetworkCallback()
        engineScope.launch {
            repository.preferencesFlow.collect { prefs ->
                updateDownloadSettings(prefs)
            }
        }
        resumeStrandedDownloadsOnStartup()
    }

    @Suppress("DEPRECATION")
    private fun queryCurrentConnectivity(): Boolean {
        return runCatching {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val active = cm.activeNetwork
            if (active != null) {
                val caps = cm.getNetworkCapabilities(active)
                if (caps != null) {
                    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                }
                return true
            }
            val legacyInfo = cm.activeNetworkInfo
            if (legacyInfo != null) {
                return legacyInfo.isConnectedOrConnecting
            }
            false
        }.getOrDefault(true)
    }

    private fun isLoopbackUrl(url: String): Boolean {
        val host = runCatching { URL(url).host.lowercase(Locale.US) }.getOrNull().orEmpty()
        return host == "127.0.0.1" || host == "localhost" || host == "::1" || host == "[::1]"
    }

    private fun registerEngineNetworkCallback() {
        runCatching {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(
                request,
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        val wasOffline = !isNetworkAvailable.getAndSet(true)
                        networkRestoredSignal.tryEmit(Unit)
                        if (wasOffline) {
                            onNetworkAvailable()
                        }
                    }

                    override fun onLost(network: Network) {
                        isNetworkAvailable.set(queryCurrentConnectivity())
                    }
                }
            )
        }
    }

    private fun updateLiveTelemetry(
        downloadId: Long,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSec: Long,
        chunksCount: Int,
        status: String
    ) {
        _liveProgress.update { current ->
            if (status != STATUS_DOWNLOADING) {
                current - downloadId
            } else {
                current + (
                    downloadId to LiveDownloadTelemetry(
                        downloadedBytes = downloadedBytes,
                        totalBytes = totalBytes,
                        speedBytesPerSec = speedBytesPerSec,
                        chunksCount = chunksCount,
                        status = status
                    )
                    )
            }
        }
    }

    fun updateDownloadSettings(prefs: BrowserPreferences) {
        val prevShowNotifications = currentPreferences.showDownloadProgressNotifications
        currentPreferences = prefs
        if (prevShowNotifications && !prefs.showDownloadProgressNotifications) {
            notificationHelper.cancelAll()
            DownloadForegroundService.stop(appContext)
        }
    }

    fun notifyExternalState(item: DownloadItemEntity) {
        if (!currentPreferences.showDownloadProgressNotifications &&
            (item.status == STATUS_DOWNLOADING || item.status == STATUS_PAUSED)
        ) {
            notificationHelper.cancel(item.id)
            return
        }
        if (item.status == STATUS_DOWNLOADING) {
            activeDownloadSummaries[item.id] = item
            syncForegroundService(item, force = true)
            if (DownloadForegroundService.isActiveOrRequested()) {
                notificationHelper.cancel(item.id)
                return
            }
        }
        notificationHelper.notifyDownloadState(item, forceImmediate = true)
    }

    private fun notifyStateRespectingPreference(item: DownloadItemEntity, forceImmediate: Boolean) {
        if (!currentPreferences.showDownloadProgressNotifications) {
            notificationHelper.cancel(item.id)
            return
        }
        if (item.status == STATUS_DOWNLOADING) {
            activeDownloadSummaries[item.id] = item
            // Active downloads use DownloadForegroundService's unified progress notification
            // to avoid showing two duplicate progress bar notifications.
            if (DownloadForegroundService.isActiveOrRequested()) {
                notificationHelper.cancel(item.id)
                return
            }
        } else {
            activeDownloadSummaries.remove(item.id)
        }
        notificationHelper.notifyDownloadState(item, forceImmediate = forceImmediate)
    }

    private fun mutexFor(downloadId: Long): Mutex {
        return downloadMutexes.computeIfAbsent(downloadId) { Mutex() }
    }

    private fun pauseFlagFor(downloadId: Long, defaultValue: Boolean = false): AtomicBoolean {
        return pausedFlags.computeIfAbsent(downloadId) { AtomicBoolean(defaultValue) }
    }

    private fun cancelFlagFor(downloadId: Long, defaultValue: Boolean = false): AtomicBoolean {
        return canceledFlags.computeIfAbsent(downloadId) { AtomicBoolean(defaultValue) }
    }

    private fun registerCall(downloadId: Long, call: Call) {
        activeCalls.computeIfAbsent(downloadId) { ConcurrentHashMap.newKeySet() }.add(call)
    }

    private fun unregisterCall(downloadId: Long, call: Call) {
        activeCalls[downloadId]?.remove(call)
    }

    private fun disconnectAllConnections(downloadId: Long) {
        val calls = activeCalls.remove(downloadId) ?: return
        calls.forEach { call ->
            runCatching { call.cancel() }
        }
    }

    private fun syncForegroundService(item: DownloadItemEntity? = null, force: Boolean = false) {
        if (item != null) {
            if (item.status == STATUS_DOWNLOADING) {
                activeDownloadSummaries[item.id] = item
            } else {
                activeDownloadSummaries.remove(item.id)
            }
        }

        val activeItems = activeDownloadSummaries.values.toList()
        val runningCount = max(activeJobs.count { (_, job) -> job.isActive }, activeItems.size)
        if (runningCount <= 0 && item?.status != STATUS_DOWNLOADING) {
            activeDownloadSummaries.clear()
            DownloadForegroundService.stop(appContext)
            return
        }

        if (!currentPreferences.showDownloadProgressNotifications) {
            DownloadForegroundService.stop(appContext)
            return
        }

        val now = System.currentTimeMillis()
        if (!force && now - lastForegroundSyncMs.get() < 450L) {
            return
        }
        lastForegroundSyncMs.set(now)

        val displayItems = if (activeItems.isNotEmpty()) activeItems else listOfNotNull(item)
        val activeCount = max(displayItems.size, 1)

        val title: String
        val subtitle: String
        val percent: Int

        if (displayItems.size == 1) {
            val single = displayItems.first()
            title = single.fileName.ifBlank { "Downloading file…" }
            val dl = formatBytes(single.downloadedBytes)
            val total = if (single.totalBytes > 0L) " / ${formatBytes(single.totalBytes)}" else ""
            val speed = if (single.speedBytesPerSec > 0L) " • ${formatSpeed(single.speedBytesPerSec)}" else ""
            val modeTag = if (single.chunksCount > 1) " (${single.chunksCount}× Turbo)" else ""
            subtitle = "$dl$total$speed$modeTag"
            percent = if (single.totalBytes > 0L) {
                ((single.downloadedBytes * 100L) / single.totalBytes).toInt().coerceIn(0, 100)
            } else {
                -1
            }
        } else if (displayItems.size > 1) {
            val totalDl = displayItems.sumOf { it.downloadedBytes.coerceAtLeast(0L) }
            val allKnownTotal = displayItems.all { it.totalBytes > 0L }
            val sumTotal = if (allKnownTotal) displayItems.sumOf { it.totalBytes } else -1L
            val sumSpeed = displayItems.sumOf { it.speedBytesPerSec.coerceAtLeast(0L) }
            title = "Downloading $activeCount files…"
            val dlLabel = formatBytes(totalDl)
            val totalLabel = if (sumTotal > 0L) " / ${formatBytes(sumTotal)}" else ""
            val speedLabel = if (sumSpeed > 0L) " • ${formatSpeed(sumSpeed)}" else ""
            subtitle = "$dlLabel$totalLabel$speedLabel"
            percent = if (sumTotal > 0L) {
                ((totalDl * 100L) / sumTotal).toInt().coerceIn(0, 100)
            } else {
                -1
            }
        } else {
            title = "Downloading file…"
            subtitle = "${DEFAULT_TURBO_CHUNKS}× Parallel Turbo Engine active"
            percent = -1
        }

        val started = DownloadForegroundService.startOrUpdate(
            context = appContext,
            activeCount = activeCount,
            title = title,
            subtitle = subtitle,
            progressPercent = percent
        )
        if (started) {
            displayItems.forEach { notificationHelper.cancel(it.id) }
        }
    }

    private fun startForegroundServiceImmediatelyHint(fileName: String, chunksCount: Int) {
        if (!currentPreferences.showDownloadProgressNotifications) return
        val turboLabel = if (chunksCount > 1) " ($chunksCount× Turbo)" else ""
        DownloadForegroundService.startOrUpdate(
            context = appContext,
            activeCount = max(activeJobs.size, 1),
            title = fileName.ifBlank { "Starting download…" },
            subtitle = "Preparing connection$turboLabel",
            progressPercent = -1
        )
    }

    private fun resumeStrandedDownloadsOnStartup() {
        engineScope.launch {
            runCatching {
                val all = repository.getAllDownloadsOnce()
                all.filter { it.status == STATUS_DOWNLOADING }.forEach { stranded ->
                    if (activeJobs[stranded.id]?.isActive != true) {
                        val diskBytes = computeCurrentDiskBytes(stranded)
                        val resumed = stranded.copy(
                            downloadedBytes = max(stranded.downloadedBytes, diskBytes),
                            speedBytesPerSec = 0L,
                            errorMessage = null
                        )
                        repository.upsertDownload(resumed)
                        startOrResumeJob(resumed, downloadUserAgents[resumed.id] ?: defaultUserAgent)
                    }
                }
            }
        }
    }

    /**
     * Automatically resumes any interrupted or stranded downloads when network connectivity returns.
     */
    fun onNetworkAvailable() {
        isNetworkAvailable.set(true)
        networkRestoredSignal.tryEmit(Unit)
        engineScope.launch {
            runCatching {
                val all = repository.getAllDownloadsOnce()
                all.forEach { item ->
                    val isRunning = activeJobs[item.id]?.isActive == true
                    val isStrandedActive = item.status == STATUS_DOWNLOADING && !isRunning
                    val isTransientNetworkFailure = item.status == STATUS_FAILED &&
                        !isRunning &&
                        (computeCurrentDiskBytes(item) > 0L || item.downloadedBytes > 0L) &&
                        pausedFlags[item.id]?.get() != true &&
                        canceledFlags[item.id]?.get() != true
                    if (isStrandedActive || isTransientNetworkFailure) {
                        resumeDownload(item.id)
                    }
                }
            }
        }
    }

    fun getDownloadsDirectory(
        mode: DownloadLocationMode = currentPreferences.downloadLocationMode
    ): File {
        return DownloadStorageHelper.resolveDirectory(appContext, mode)
    }

    fun enqueueDownload(
        url: String,
        suggestedFileName: String? = null,
        contentDisposition: String? = null,
        mimeType: String? = null,
        contentLength: Long = -1L,
        userAgent: String? = null,
        locationModeOverride: DownloadLocationMode? = null,
        customTreeUriOverride: String? = null,
        onStarted: (DownloadItemEntity) -> Unit = {}
    ) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isEmpty()) return

        val prefsSnapshot = currentPreferences
        val initialHintName = suggestedFileName?.takeIf { it.isNotBlank() }
            ?: trimmedUrl.substringAfterLast('/').substringBefore('?').ifBlank { "download" }
        val hintChunks = if (prefsSnapshot.parallelDownloadingEnabled && !trimmedUrl.startsWith("data:", true)) {
            DEFAULT_TURBO_CHUNKS
        } else {
            1
        }
        // Start foreground service synchronously while caller is still in foreground
        startForegroundServiceImmediatelyHint(initialHintName, hintChunks)

        engineScope.launch {
            val prefs = currentPreferences
            val effectiveMode = locationModeOverride ?: prefs.downloadLocationMode
            val effectiveCustomUri = customTreeUriOverride?.takeIf { it.isNotBlank() }
                ?: prefs.customDownloadTreeUri
            val resolvedMime = resolveMimeType(
                suggestedFileName?.takeIf { it.contains('.') } ?: trimmedUrl,
                mimeType
            )
            val rawName = when {
                !suggestedFileName.isNullOrBlank() && suggestedFileName.contains('.') ->
                    sanitizeFileName(suggestedFileName)
                !suggestedFileName.isNullOrBlank() ->
                    sanitizeFileName(suggestedFileName)
                else -> sanitizeFileName(
                    URLUtil.guessFileName(trimmedUrl, contentDisposition, resolvedMime)
                )
            }
            val uniqueFile = allocateUniqueFile(getDownloadsDirectory(effectiveMode), rawName)
            val finalMime = resolveMimeType(uniqueFile.name, resolvedMime)
            val category = inferCategory(uniqueFile.name, finalMime)
            val initialChunks = when {
                !prefs.parallelDownloadingEnabled -> 1
                trimmedUrl.startsWith("data:", ignoreCase = true) -> 1
                contentLength in 1 until MIN_PARALLEL_BYTES -> 1
                contentLength in MIN_PARALLEL_BYTES until HIGH_TURBO_THRESHOLD_BYTES -> MEDIUM_TURBO_CHUNKS
                else -> DEFAULT_TURBO_CHUNKS
            }

            val initialEntity = DownloadItemEntity(
                fileName = uniqueFile.name,
                url = trimmedUrl,
                mimeType = finalMime,
                category = category,
                totalBytes = if (contentLength > 0) contentLength else -1L,
                downloadedBytes = 0L,
                speedBytesPerSec = 0L,
                chunksCount = initialChunks,
                status = STATUS_DOWNLOADING,
                localFilePath = uniqueFile.absolutePath,
                createdAt = System.currentTimeMillis()
            )

            val id = repository.upsertDownload(initialEntity)
            val savedEntity = initialEntity.copy(id = id)
            if (effectiveMode == DownloadLocationMode.CUSTOM_SAF && effectiveCustomUri.isNotBlank()) {
                downloadSafTargets[id] = effectiveCustomUri
            }
            activeDownloadSummaries[id] = savedEntity
            updateLiveTelemetry(
                downloadId = id,
                downloadedBytes = 0L,
                totalBytes = savedEntity.totalBytes,
                speedBytesPerSec = 0L,
                chunksCount = initialChunks,
                status = STATUS_DOWNLOADING
            )
            notifyStateRespectingPreference(savedEntity, forceImmediate = true)
            val effectiveUa = userAgent ?: defaultUserAgent
            downloadUserAgents[id] = effectiveUa
            val pFlag = pauseFlagFor(id, defaultValue = false)
            val cFlag = cancelFlagFor(id, defaultValue = false)

            engineScope.launch(Dispatchers.Main) {
                onStarted(savedEntity)
            }

            if (pFlag.get() || cFlag.get()) {
                return@launch
            }
            startOrResumeJob(savedEntity, effectiveUa)
        }
    }

    fun pauseDownload(downloadId: Long) {
        val pFlag = pauseFlagFor(downloadId, defaultValue = true)
        pFlag.set(true)

        engineScope.launch {
            disconnectAllConnections(downloadId)
            val runningJob = activeJobs.remove(downloadId)
            runningJob?.cancel()
            runCatching { runningJob?.join() }

            withContext(NonCancellable) {
                mutexFor(downloadId).withLock {
                    val existing = repository.getDownloadById(downloadId) ?: return@withLock
                    if (existing.status == STATUS_DOWNLOADING ||
                        existing.status == STATUS_PAUSED ||
                        existing.status == STATUS_FAILED
                    ) {
                        val bestBytes = computeCurrentDiskBytes(existing).let { diskBytes ->
                            val maxKnown = maxOf(existing.downloadedBytes, diskBytes)
                            if (existing.totalBytes > 0L) {
                                maxKnown.coerceAtMost(existing.totalBytes)
                            } else {
                                maxKnown
                            }
                        }
                        val pausedItem = existing.copy(
                            status = STATUS_PAUSED,
                            downloadedBytes = bestBytes,
                            speedBytesPerSec = 0L,
                            errorMessage = null
                        )
                        activeDownloadSummaries.remove(downloadId)
                        updateLiveTelemetry(
                            downloadId = downloadId,
                            downloadedBytes = bestBytes,
                            totalBytes = pausedItem.totalBytes,
                            speedBytesPerSec = 0L,
                            chunksCount = pausedItem.chunksCount,
                            status = STATUS_PAUSED
                        )
                        repository.upsertDownload(pausedItem)
                        notifyStateRespectingPreference(pausedItem, forceImmediate = true)
                    }
                }
                syncForegroundService(force = true)
            }
        }
    }

    fun resumeDownload(downloadId: Long) {
        val pFlag = pauseFlagFor(downloadId, defaultValue = false)
        val cFlag = cancelFlagFor(downloadId, defaultValue = false)
        pFlag.set(false)
        cFlag.set(false)
        startForegroundServiceImmediatelyHint("Resuming download…", DEFAULT_TURBO_CHUNKS)

        engineScope.launch {
            val itemToStart = mutexFor(downloadId).withLock {
                val existing = repository.getDownloadById(downloadId) ?: return@withLock null
                if (existing.status == STATUS_COMPLETED || existing.status == STATUS_CANCELED) {
                    return@withLock null
                }
                if (existing.status == STATUS_DOWNLOADING && activeJobs[downloadId]?.isActive == true) {
                    return@withLock null
                }
                if (pFlag.get() || cFlag.get()) {
                    return@withLock null
                }
                val diskBytes = computeCurrentDiskBytes(existing)
                val resumedBytes = max(existing.downloadedBytes, diskBytes).let { raw ->
                    if (existing.totalBytes > 0L) raw.coerceAtMost(existing.totalBytes) else raw
                }
                val updated = existing.copy(
                    status = STATUS_DOWNLOADING,
                    downloadedBytes = resumedBytes,
                    errorMessage = null,
                    speedBytesPerSec = 0L
                )
                activeDownloadSummaries[downloadId] = updated
                updateLiveTelemetry(
                    downloadId = downloadId,
                    downloadedBytes = resumedBytes,
                    totalBytes = updated.totalBytes,
                    speedBytesPerSec = 0L,
                    chunksCount = updated.chunksCount,
                    status = STATUS_DOWNLOADING
                )
                repository.upsertDownload(updated)
                notifyStateRespectingPreference(updated, forceImmediate = true)
                updated
            } ?: return@launch

            startOrResumeJob(itemToStart, downloadUserAgents[downloadId] ?: defaultUserAgent)
        }
    }

    fun retryDownload(downloadId: Long, forceCleanRestart: Boolean = false) {
        val pFlag = pauseFlagFor(downloadId, defaultValue = false)
        val cFlag = cancelFlagFor(downloadId, defaultValue = false)
        pFlag.set(false)
        cFlag.set(false)
        startForegroundServiceImmediatelyHint("Retrying download…", DEFAULT_TURBO_CHUNKS)

        engineScope.launch {
            disconnectAllConnections(downloadId)
            val prevJob = activeJobs.remove(downloadId)
            prevJob?.cancel()
            runCatching { prevJob?.join() }

            val itemToStart = mutexFor(downloadId).withLock {
                val existing = repository.getDownloadById(downloadId) ?: return@withLock null
                val diskBytes = computeCurrentDiskBytes(existing)
                val canResumePartial = !forceCleanRestart &&
                    existing.status != STATUS_COMPLETED &&
                    existing.localFilePath.isNotBlank() &&
                    diskBytes > 0L

                if (canResumePartial) {
                    val resumedBytes = max(existing.downloadedBytes, diskBytes).let { raw ->
                        if (existing.totalBytes > 0L) raw.coerceAtMost(existing.totalBytes) else raw
                    }
                    val resumedItem = existing.copy(
                        downloadedBytes = resumedBytes,
                        speedBytesPerSec = 0L,
                        status = STATUS_DOWNLOADING,
                        errorMessage = null,
                        completedAt = null
                    )
                    activeDownloadSummaries[downloadId] = resumedItem
                    updateLiveTelemetry(
                        downloadId = downloadId,
                        downloadedBytes = resumedBytes,
                        totalBytes = resumedItem.totalBytes,
                        speedBytesPerSec = 0L,
                        chunksCount = resumedItem.chunksCount,
                        status = STATUS_DOWNLOADING
                    )
                    repository.upsertDownload(resumedItem)
                    notifyStateRespectingPreference(resumedItem, forceImmediate = true)
                    return@withLock resumedItem
                }

                if (existing.localFilePath.isNotBlank()) {
                    deleteFileAndParts(File(existing.localFilePath))
                }
                val targetFile = if (existing.localFilePath.isNotBlank() &&
                    !existing.localFilePath.startsWith("content://", ignoreCase = true)
                ) {
                    File(existing.localFilePath)
                } else {
                    allocateUniqueFile(getDownloadsDirectory(), existing.fileName)
                }
                val resetChunks = if (currentPreferences.parallelDownloadingEnabled) DEFAULT_TURBO_CHUNKS else 1
                val resetItem = existing.copy(
                    downloadedBytes = 0L,
                    speedBytesPerSec = 0L,
                    chunksCount = resetChunks,
                    status = STATUS_DOWNLOADING,
                    localFilePath = targetFile.absolutePath,
                    errorMessage = null,
                    completedAt = null
                )
                activeDownloadSummaries[downloadId] = resetItem
                updateLiveTelemetry(
                    downloadId = downloadId,
                    downloadedBytes = 0L,
                    totalBytes = resetItem.totalBytes,
                    speedBytesPerSec = 0L,
                    chunksCount = resetChunks,
                    status = STATUS_DOWNLOADING
                )
                repository.upsertDownload(resetItem)
                notifyStateRespectingPreference(resetItem, forceImmediate = true)
                resetItem
            } ?: return@launch

            startOrResumeJob(itemToStart, downloadUserAgents[downloadId] ?: defaultUserAgent)
        }
    }

    fun cancelDownload(downloadId: Long) {
        val cFlag = cancelFlagFor(downloadId, defaultValue = true)
        cFlag.set(true)
        pauseFlagFor(downloadId, defaultValue = false).set(false)

        engineScope.launch {
            disconnectAllConnections(downloadId)
            val prevJob = activeJobs.remove(downloadId)
            prevJob?.cancel()
            runCatching { prevJob?.join() }

            withContext(NonCancellable) {
                mutexFor(downloadId).withLock {
                    val existing = repository.getDownloadById(downloadId) ?: return@withLock
                    if (existing.localFilePath.isNotBlank()) {
                        deleteFileAndParts(File(existing.localFilePath))
                    }
                    activeDownloadSummaries.remove(downloadId)
                    updateLiveTelemetry(
                        downloadId = downloadId,
                        downloadedBytes = existing.downloadedBytes,
                        totalBytes = existing.totalBytes,
                        speedBytesPerSec = 0L,
                        chunksCount = existing.chunksCount,
                        status = STATUS_CANCELED
                    )
                    repository.upsertDownload(
                        existing.copy(
                            status = STATUS_CANCELED,
                            speedBytesPerSec = 0L
                        )
                    )
                    notificationHelper.cancel(downloadId)
                }
                syncForegroundService(force = true)
            }
        }
    }

    fun deleteDownload(downloadId: Long, deleteFileFromDisk: Boolean = true) {
        cancelFlagFor(downloadId, defaultValue = true).set(true)
        pauseFlagFor(downloadId, defaultValue = false).set(false)

        engineScope.launch {
            disconnectAllConnections(downloadId)
            val prevJob = activeJobs.remove(downloadId)
            prevJob?.cancel()
            runCatching { prevJob?.join() }

            withContext(NonCancellable) {
                mutexFor(downloadId).withLock {
                    val existing = repository.getDownloadById(downloadId)
                    if (deleteFileFromDisk && existing != null && existing.localFilePath.isNotBlank()) {
                        deleteFileAndParts(File(existing.localFilePath))
                    }
                    repository.deleteDownloadById(downloadId)
                }
                activeDownloadSummaries.remove(downloadId)
                _liveProgress.update { it - downloadId }
                notificationHelper.cancel(downloadId)
                pausedFlags.remove(downloadId)
                canceledFlags.remove(downloadId)
                downloadUserAgents.remove(downloadId)
                downloadSafTargets.remove(downloadId)
                downloadMutexes.remove(downloadId)
                syncForegroundService(force = true)
            }
        }
    }

    fun clearAllDownloads() {
        val ids = activeJobs.keys.toList()
        ids.forEach { id ->
            cancelFlagFor(id, defaultValue = true).set(true)
            pauseFlagFor(id, defaultValue = false).set(false)
        }
        engineScope.launch {
            ids.forEach { id ->
                disconnectAllConnections(id)
                activeJobs.remove(id)?.cancel()
            }
            withContext(NonCancellable) {
                DownloadLocationMode.entries.forEach { mode ->
                    if (mode != DownloadLocationMode.CUSTOM_SAF) {
                        getDownloadsDirectory(mode).listFiles()?.forEach { file ->
                            runCatching { file.delete() }
                        }
                    }
                }
                activeDownloadSummaries.clear()
                _liveProgress.value = emptyMap()
                repository.clearAllDownloads()
                notificationHelper.cancelAll()
                pausedFlags.clear()
                canceledFlags.clear()
                downloadUserAgents.clear()
                downloadSafTargets.clear()
                downloadMutexes.clear()
                syncForegroundService(force = true)
            }
        }
    }

    private fun startOrResumeJob(item: DownloadItemEntity, userAgent: String) {
        activeJobs.remove(item.id)?.cancel()
        val pauseFlag = pauseFlagFor(item.id, defaultValue = false)
        val cancelFlag = cancelFlagFor(item.id, defaultValue = false)

        val job = engineScope.launch {
            val latestDownloaded = AtomicLong(item.downloadedBytes.coerceAtLeast(0L))
            val latestTotal = AtomicLong(item.totalBytes)
            syncForegroundService(item, force = true)
            try {
                if (pauseFlag.get()) {
                    persistPausedState(item.id, latestDownloaded.get(), latestTotal.get())
                    return@launch
                }
                if (cancelFlag.get()) return@launch

                when {
                    item.url.startsWith("data:", ignoreCase = true) -> {
                        executeDataUrlDownload(item, pauseFlag, cancelFlag)
                    }
                    else -> {
                        executeHttpFastDownload(
                            item = item,
                            userAgent = userAgent,
                            pauseFlag = pauseFlag,
                            cancelFlag = cancelFlag,
                            latestDownloaded = latestDownloaded,
                            latestTotal = latestTotal
                        )
                    }
                }
            } catch (ce: CancellationException) {
                if (pauseFlag.get() && !cancelFlag.get()) {
                    withContext(NonCancellable) {
                        persistPausedState(item.id, latestDownloaded.get(), latestTotal.get())
                    }
                }
                throw ce
            } catch (e: Exception) {
                if (pauseFlag.get() && !cancelFlag.get()) {
                    withContext(NonCancellable) {
                        persistPausedState(item.id, latestDownloaded.get(), latestTotal.get())
                    }
                } else if (!cancelFlag.get()) {
                    withContext(NonCancellable) {
                        mutexFor(item.id).withLock {
                            val latest = repository.getDownloadById(item.id) ?: item
                            if (latest.status != STATUS_PAUSED &&
                                latest.status != STATUS_CANCELED &&
                                latest.status != STATUS_COMPLETED
                            ) {
                                val diskBytes = computeCurrentDiskBytes(latest)
                                val savedBytes = maxOf(
                                    latest.downloadedBytes,
                                    latestDownloaded.get(),
                                    diskBytes
                                ).let { raw ->
                                    val resolvedTotal = if (latest.totalBytes > 0L) {
                                        latest.totalBytes
                                    } else {
                                        latestTotal.get()
                                    }
                                    if (resolvedTotal > 0L) raw.coerceAtMost(resolvedTotal) else raw
                                }
                                val failedItem = latest.copy(
                                    status = STATUS_FAILED,
                                    downloadedBytes = savedBytes,
                                    speedBytesPerSec = 0L,
                                    errorMessage = e.message?.takeIf { it.isNotBlank() }
                                        ?: "Connection interrupted — tap Resume to continue"
                                )
                                activeDownloadSummaries.remove(item.id)
                                updateLiveTelemetry(
                                    downloadId = item.id,
                                    downloadedBytes = savedBytes,
                                    totalBytes = failedItem.totalBytes,
                                    speedBytesPerSec = 0L,
                                    chunksCount = failedItem.chunksCount,
                                    status = STATUS_FAILED
                                )
                                repository.upsertDownload(failedItem)
                                notifyStateRespectingPreference(failedItem, forceImmediate = true)
                            }
                        }
                    }
                }
            } finally {
                disconnectAllConnections(item.id)
                activeJobs.remove(item.id, coroutineContext[Job])
                activeDownloadSummaries.remove(item.id)
                syncForegroundService(force = true)
            }
        }
        activeJobs[item.id] = job
        syncForegroundService(item, force = true)
    }

    private suspend fun persistPausedState(
        downloadId: Long,
        fallbackBytes: Long,
        fallbackTotal: Long
    ) {
        mutexFor(downloadId).withLock {
            val latest = repository.getDownloadById(downloadId) ?: return@withLock
            if (latest.status == STATUS_CANCELED || latest.status == STATUS_COMPLETED) {
                return@withLock
            }
            val diskBytes = computeCurrentDiskBytes(latest)
            val resolvedTotal = if (latest.totalBytes > 0L) latest.totalBytes else fallbackTotal
            val bestBytes = maxOf(latest.downloadedBytes, fallbackBytes, diskBytes).let { raw ->
                if (resolvedTotal > 0L) raw.coerceAtMost(resolvedTotal) else raw
            }
            val pausedItem = latest.copy(
                status = STATUS_PAUSED,
                downloadedBytes = bestBytes,
                totalBytes = resolvedTotal,
                speedBytesPerSec = 0L,
                errorMessage = null
            )
            activeDownloadSummaries.remove(downloadId)
            updateLiveTelemetry(
                downloadId = downloadId,
                downloadedBytes = bestBytes,
                totalBytes = resolvedTotal,
                speedBytesPerSec = 0L,
                chunksCount = pausedItem.chunksCount,
                status = STATUS_PAUSED
            )
            repository.upsertDownload(pausedItem)
            notifyStateRespectingPreference(pausedItem, forceImmediate = true)
        }
    }

    private suspend fun persistDownloadingProgress(
        downloadId: Long,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSec: Long,
        chunksCount: Int,
        pauseFlag: AtomicBoolean,
        cancelFlag: AtomicBoolean,
        persistToDb: Boolean
    ) {
        if (pauseFlag.get() || cancelFlag.get()) return
        val clampedBytes = if (totalBytes > 0L) {
            downloadedBytes.coerceAtMost(totalBytes)
        } else {
            downloadedBytes
        }
        updateLiveTelemetry(
            downloadId = downloadId,
            downloadedBytes = clampedBytes,
            totalBytes = totalBytes,
            speedBytesPerSec = speedBytesPerSec.coerceAtLeast(0L),
            chunksCount = chunksCount,
            status = STATUS_DOWNLOADING
        )

        val currentSummary = activeDownloadSummaries[downloadId]
        if (currentSummary != null) {
            val syncedItem = currentSummary.copy(
                downloadedBytes = max(currentSummary.downloadedBytes, clampedBytes),
                totalBytes = if (totalBytes > 0L) totalBytes else currentSummary.totalBytes,
                speedBytesPerSec = speedBytesPerSec.coerceAtLeast(0L),
                chunksCount = chunksCount,
                status = STATUS_DOWNLOADING
            )
            activeDownloadSummaries[downloadId] = syncedItem
            notifyStateRespectingPreference(syncedItem, forceImmediate = false)
            syncForegroundService(syncedItem, force = false)
        }

        if (!persistToDb) return

        mutexFor(downloadId).withLock {
            if (pauseFlag.get() || cancelFlag.get()) return@withLock
            val latest = repository.getDownloadById(downloadId) ?: return@withLock
            if (latest.status != STATUS_DOWNLOADING) return@withLock
            val updatedItem = latest.copy(
                downloadedBytes = max(latest.downloadedBytes, clampedBytes),
                totalBytes = if (totalBytes > 0L) totalBytes else latest.totalBytes,
                speedBytesPerSec = speedBytesPerSec.coerceAtLeast(0L),
                chunksCount = chunksCount,
                status = STATUS_DOWNLOADING
            )
            activeDownloadSummaries[downloadId] = updatedItem
            repository.upsertDownload(updatedItem)
            notifyStateRespectingPreference(updatedItem, forceImmediate = false)
            syncForegroundService(updatedItem, force = false)
        }
    }

    private suspend fun finalizeCompletedDownload(
        downloadId: Long,
        completedBase: DownloadItemEntity,
        targetFile: File
    ) {
        val explicitSafUri = downloadSafTargets.remove(downloadId)
        val effectiveSafUri = explicitSafUri ?: if (currentPreferences.downloadLocationMode == DownloadLocationMode.CUSTOM_SAF) {
            currentPreferences.customDownloadTreeUri.takeIf { it.isNotBlank() }
        } else {
            null
        }
        val exportedSafUri = if (!effectiveSafUri.isNullOrBlank()) {
            DownloadStorageHelper.copyFileToSafTree(
                context = appContext,
                sourceFile = targetFile,
                treeUriString = effectiveSafUri,
                mimeType = completedBase.mimeType,
                fileName = completedBase.fileName
            )
        } else {
            null
        }
        val finalItem = if (exportedSafUri != null) {
            completedBase.copy(localFilePath = exportedSafUri.toString())
        } else {
            completedBase
        }
        activeDownloadSummaries.remove(downloadId)
        updateLiveTelemetry(
            downloadId = downloadId,
            downloadedBytes = finalItem.downloadedBytes,
            totalBytes = finalItem.totalBytes,
            speedBytesPerSec = 0L,
            chunksCount = finalItem.chunksCount,
            status = STATUS_COMPLETED
        )
        repository.upsertDownload(finalItem)
        syncForegroundService(finalItem, force = true)
        notifyStateRespectingPreference(finalItem, forceImmediate = true)
        if (currentPreferences.autoOpenDownloadedFiles) {
            _autoOpenEvents.tryEmit(finalItem)
        }
    }

    private suspend fun executeDataUrlDownload(
        item: DownloadItemEntity,
        pauseFlag: AtomicBoolean,
        cancelFlag: AtomicBoolean
    ) {
        if (pauseFlag.get() || cancelFlag.get()) return
        val targetFile = File(
            item.localFilePath.ifBlank {
                allocateUniqueFile(getDownloadsDirectory(), item.fileName).absolutePath
            }
        )
        targetFile.parentFile?.mkdirs()
        val payload = item.url.substringAfter(',', "")
        val meta = item.url.substringBefore(',', "")
        val bytes = if (meta.contains(";base64", ignoreCase = true)) {
            Base64.decode(payload, Base64.DEFAULT)
        } else {
            URLDecoder.decode(payload, "UTF-8").toByteArray(Charsets.UTF_8)
        }
        if (pauseFlag.get() || cancelFlag.get()) return
        FileOutputStream(targetFile).use { it.write(bytes) }
        val total = bytes.size.toLong()
        mutexFor(item.id).withLock {
            if (pauseFlag.get()) {
                val latest = repository.getDownloadById(item.id) ?: item
                val pausedItem = latest.copy(
                    totalBytes = total,
                    downloadedBytes = total,
                    speedBytesPerSec = 0L,
                    chunksCount = 1,
                    status = STATUS_PAUSED,
                    localFilePath = targetFile.absolutePath
                )
                activeDownloadSummaries.remove(item.id)
                updateLiveTelemetry(item.id, total, total, 0L, 1, STATUS_PAUSED)
                repository.upsertDownload(pausedItem)
                syncForegroundService(pausedItem, force = true)
                notifyStateRespectingPreference(pausedItem, forceImmediate = true)
                return@withLock
            }
            if (cancelFlag.get()) return@withLock
            val latest = repository.getDownloadById(item.id) ?: item
            val completedItem = latest.copy(
                totalBytes = total,
                downloadedBytes = total,
                speedBytesPerSec = max(total * 8L, 1_048_576L),
                chunksCount = 1,
                status = STATUS_COMPLETED,
                localFilePath = targetFile.absolutePath,
                completedAt = System.currentTimeMillis()
            )
            finalizeCompletedDownload(item.id, completedItem, targetFile)
        }
    }

    private suspend fun executeHttpFastDownload(
        item: DownloadItemEntity,
        userAgent: String,
        pauseFlag: AtomicBoolean,
        cancelFlag: AtomicBoolean,
        latestDownloaded: AtomicLong,
        latestTotal: AtomicLong
    ) {
        val targetFile = File(
            item.localFilePath.ifBlank {
                allocateUniqueFile(getDownloadsDirectory(), item.fileName).absolutePath
            }
        )
        targetFile.parentFile?.mkdirs()

        val cookieHeader = runCatching {
            CookieManager.getInstance().getCookie(item.url)
        }.getOrNull()

        val existingMeta = readSegmentMeta(metaFileFor(targetFile))
        val hasExistingMeta = existingMeta != null && tempDownloadingFileFor(targetFile).exists()
        val hasSinglePartialFile = targetFile.exists() && targetFile.length() > 0L && !hasExistingMeta

        val probe = probeRemoteResource(
            downloadId = item.id,
            url = item.url,
            userAgent = userAgent,
            cookieHeader = cookieHeader,
            pauseFlag = pauseFlag,
            cancelFlag = cancelFlag
        )
        if (pauseFlag.get()) {
            withContext(NonCancellable) {
                persistPausedState(item.id, latestDownloaded.get(), latestTotal.get())
            }
            return
        }
        if (cancelFlag.get()) return

        val resolvedTotalBytes = when {
            hasExistingMeta && existingMeta!!.totalBytes > 0L -> existingMeta.totalBytes
            probe.contentLength > 0L -> probe.contentLength
            else -> item.totalBytes
        }
        latestTotal.set(resolvedTotalBytes)

        val resolvedFileName = if (probe.suggestedName.isNotBlank() &&
            (item.fileName.startsWith("downloadfile", true) || item.fileName.endsWith(".bin", true))
        ) {
            sanitizeFileName(probe.suggestedName)
        } else {
            item.fileName
        }
        val resolvedMime = resolveMimeType(resolvedFileName, probe.mimeType.ifBlank { item.mimeType })
        val resolvedCategory = inferCategory(resolvedFileName, resolvedMime)
        val transferUrl = probe.finalUrl.ifBlank { item.url }

        val allowParallel = currentPreferences.parallelDownloadingEnabled
        val useParallelSegments = when {
            hasExistingMeta && resolvedTotalBytes > 0L -> true
            !allowParallel -> false
            hasSinglePartialFile -> false
            probe.supportsRanges && resolvedTotalBytes >= MIN_PARALLEL_BYTES -> true
            else -> false
        }

        val workerCount = when {
            !useParallelSegments -> 1
            resolvedTotalBytes >= HIGH_TURBO_THRESHOLD_BYTES -> DEFAULT_TURBO_CHUNKS
            else -> MEDIUM_TURBO_CHUNKS
        }

        val currentDiskBytes = computeCurrentDiskBytesForTarget(targetFile)
        if (currentDiskBytes > 0L) {
            latestDownloaded.set(max(latestDownloaded.get(), currentDiskBytes))
        }

        var workingItem = item
        val shouldAbort = mutexFor(item.id).withLock {
            if (pauseFlag.get() || cancelFlag.get()) return@withLock true
            val latest = repository.getDownloadById(item.id) ?: item
            if (latest.status != STATUS_DOWNLOADING) return@withLock true
            val bestDownloaded = maxOf(latest.downloadedBytes, latestDownloaded.get(), currentDiskBytes).let { raw ->
                if (resolvedTotalBytes > 0L) raw.coerceAtMost(resolvedTotalBytes) else raw
            }
            workingItem = latest.copy(
                fileName = resolvedFileName,
                mimeType = resolvedMime,
                category = resolvedCategory,
                totalBytes = resolvedTotalBytes,
                downloadedBytes = bestDownloaded,
                chunksCount = workerCount,
                status = STATUS_DOWNLOADING,
                localFilePath = targetFile.absolutePath,
                errorMessage = null
            )
            activeDownloadSummaries[item.id] = workingItem
            updateLiveTelemetry(
                downloadId = item.id,
                downloadedBytes = bestDownloaded,
                totalBytes = resolvedTotalBytes,
                speedBytesPerSec = 0L,
                chunksCount = workerCount,
                status = STATUS_DOWNLOADING
            )
            repository.upsertDownload(workingItem)
            notifyStateRespectingPreference(workingItem, forceImmediate = true)
            syncForegroundService(workingItem, force = true)
            false
        }
        if (shouldAbort) {
            if (pauseFlag.get() && !cancelFlag.get()) {
                withContext(NonCancellable) {
                    persistPausedState(item.id, latestDownloaded.get(), resolvedTotalBytes)
                }
            }
            return
        }

        if (useParallelSegments) {
            try {
                downloadInParallelWorkStealing(
                    item = workingItem,
                    transferUrl = transferUrl,
                    targetFile = targetFile,
                    totalBytes = resolvedTotalBytes,
                    workers = workerCount,
                    existingMeta = existingMeta,
                    userAgent = userAgent,
                    cookieHeader = cookieHeader,
                    pauseFlag = pauseFlag,
                    cancelFlag = cancelFlag,
                    latestDownloaded = latestDownloaded
                )
            } catch (e: RangeNotSupportedException) {
                if (pauseFlag.get() || cancelFlag.get()) return
                deleteWorkFilesOnly(targetFile)
                latestDownloaded.set(0L)
                downloadSingleFastStream(
                    item = workingItem.copy(chunksCount = 1, downloadedBytes = 0L),
                    transferUrl = transferUrl,
                    targetFile = targetFile,
                    totalBytes = resolvedTotalBytes,
                    supportsRanges = false,
                    userAgent = userAgent,
                    cookieHeader = cookieHeader,
                    pauseFlag = pauseFlag,
                    cancelFlag = cancelFlag,
                    latestDownloaded = latestDownloaded,
                    latestTotal = latestTotal
                )
            }
        } else {
            downloadSingleFastStream(
                item = workingItem,
                transferUrl = transferUrl,
                targetFile = targetFile,
                totalBytes = resolvedTotalBytes,
                supportsRanges = probe.supportsRanges || hasSinglePartialFile,
                userAgent = userAgent,
                cookieHeader = cookieHeader,
                pauseFlag = pauseFlag,
                cancelFlag = cancelFlag,
                latestDownloaded = latestDownloaded,
                latestTotal = latestTotal
            )
        }
    }

    private data class SegmentSpec(
        val startByte: Long,
        val endByte: Long,
        val initialWritten: Long
    ) {
        val length: Long get() = (endByte - startByte) + 1L
    }

    private data class SegmentMeta(
        val totalBytes: Long,
        val segments: List<SegmentSpec>
    )

    private suspend fun downloadInParallelWorkStealing(
        item: DownloadItemEntity,
        transferUrl: String,
        targetFile: File,
        totalBytes: Long,
        workers: Int,
        existingMeta: SegmentMeta?,
        userAgent: String,
        cookieHeader: String?,
        pauseFlag: AtomicBoolean,
        cancelFlag: AtomicBoolean,
        latestDownloaded: AtomicLong
    ) = coroutineScope {
        val tempFile = tempDownloadingFileFor(targetFile)
        val metaFile = metaFileFor(targetFile)

        val segments: List<SegmentSpec> = if (existingMeta != null &&
            existingMeta.totalBytes == totalBytes &&
            existingMeta.segments.isNotEmpty() &&
            tempFile.exists()
        ) {
            existingMeta.segments
        } else {
            val targetSegmentCount = when {
                totalBytes <= 1_048_576L -> workers
                else -> min(
                    MAX_WORK_SEGMENTS,
                    max(workers * 3, (totalBytes / TARGET_SEGMENT_SIZE_BYTES).toInt())
                )
            }.coerceAtMost(totalBytes.coerceAtLeast(1L).toInt()).coerceAtLeast(1)

            val segSize = totalBytes / targetSegmentCount
            (0 until targetSegmentCount).map { idx ->
                val start = idx * segSize
                val end = if (idx == targetSegmentCount - 1) totalBytes - 1 else (start + segSize - 1)
                SegmentSpec(startByte = start, endByte = end, initialWritten = 0L)
            }
        }

        val segmentWritten = AtomicLongArray(segments.size)
        var initialBytes = 0L
        val pendingQueue = ConcurrentLinkedQueue<Int>()
        for (i in segments.indices) {
            val spec = segments[i]
            val clampedWritten = spec.initialWritten.coerceIn(0L, spec.length)
            segmentWritten.set(i, clampedWritten)
            initialBytes += clampedWritten
            if (clampedWritten < spec.length) {
                pendingQueue.add(i)
            }
        }

        val totalDownloaded = AtomicLong(initialBytes)
        latestDownloaded.set(initialBytes)
        val startTimeMs = System.currentTimeMillis()
        writeSegmentMeta(metaFile, totalBytes, segments, segmentWritten)

        val raf = RandomAccessFile(tempFile, "rw")
        val fileChannel = raf.channel
        runCatching {
            if (raf.length() < totalBytes) {
                raf.setLength(totalBytes)
            }
        }

        val progressReporter = launch {
            var lastBytes = initialBytes
            var lastTickMs = startTimeMs
            var lastDbPersistMs = startTimeMs
            var lastMetaFlushMs = startTimeMs
            var smoothedSpeed = 0L
            while (isActive && !pauseFlag.get() && !cancelFlag.get()) {
                delay(150L)
                if (pauseFlag.get() || cancelFlag.get()) break
                val now = System.currentTimeMillis()
                val currentBytes = totalDownloaded.get()
                latestDownloaded.set(currentBytes)
                val dtMs = (now - lastTickMs).coerceAtLeast(1L)
                val instantSpeed = ((currentBytes - lastBytes).coerceAtLeast(0L) * 1000L) / dtMs
                smoothedSpeed = if (smoothedSpeed <= 0L) {
                    instantSpeed
                } else {
                    ((instantSpeed * 65L) + (smoothedSpeed * 35L)) / 100L
                }
                lastBytes = currentBytes
                lastTickMs = now

                val shouldPersistDb = (now - lastDbPersistMs) >= DB_PROGRESS_FLUSH_MS
                if (shouldPersistDb) {
                    lastDbPersistMs = now
                }
                if (now - lastMetaFlushMs >= 900L) {
                    lastMetaFlushMs = now
                    writeSegmentMeta(metaFile, totalBytes, segments, segmentWritten)
                }

                persistDownloadingProgress(
                    downloadId = item.id,
                    downloadedBytes = currentBytes,
                    totalBytes = totalBytes,
                    speedBytesPerSec = smoothedSpeed,
                    chunksCount = workers,
                    pauseFlag = pauseFlag,
                    cancelFlag = cancelFlag,
                    persistToDb = shouldPersistDb
                )
            }
        }

        try {
            val workerTasks = (0 until min(workers, max(pendingQueue.size, 1))).map {
                async(Dispatchers.IO) {
                    while (!pauseFlag.get() && !cancelFlag.get()) {
                        val segIdx = pendingQueue.poll() ?: break
                        val spec = segments[segIdx]
                        downloadSegmentToChannel(
                            downloadId = item.id,
                            url = transferUrl,
                            fileChannel = fileChannel,
                            segmentIndex = segIdx,
                            spec = spec,
                            segmentWritten = segmentWritten,
                            userAgent = userAgent,
                            cookieHeader = cookieHeader,
                            pauseFlag = pauseFlag,
                            cancelFlag = cancelFlag,
                            totalDownloaded = totalDownloaded
                        )
                    }
                }
            }
            workerTasks.awaitAll()
        } finally {
            progressReporter.cancel()
            latestDownloaded.set(totalDownloaded.get())
            withContext(NonCancellable) {
                runCatching { fileChannel.force(false) }
                runCatching { fileChannel.close() }
                runCatching { raf.close() }
                if (!cancelFlag.get()) {
                    writeSegmentMeta(metaFile, totalBytes, segments, segmentWritten)
                }
            }
        }

        if (pauseFlag.get() && !cancelFlag.get()) {
            withContext(NonCancellable) {
                persistPausedState(item.id, totalDownloaded.get(), totalBytes)
            }
            return@coroutineScope
        }
        if (cancelFlag.get()) return@coroutineScope

        val verifiedSum = (segments.indices).sumOf { idx ->
            segmentWritten.get(idx).coerceAtMost(segments[idx].length)
        }
        if (verifiedSum < totalBytes) {
            if (pauseFlag.get() && !cancelFlag.get()) {
                withContext(NonCancellable) {
                    persistPausedState(item.id, verifiedSum, totalBytes)
                }
                return@coroutineScope
            }
            throw IOException("Incomplete segment transfer ($verifiedSum / $totalBytes bytes)")
        }

        if (targetFile.exists()) {
            targetFile.delete()
        }
        if (!tempFile.renameTo(targetFile)) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }
        deleteWorkFilesOnly(targetFile)

        val elapsedMs = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(1L)
        val transferredThisRun = (totalBytes - initialBytes).coerceAtLeast(1L)
        val avgSpeed = (transferredThisRun * 1000L) / elapsedMs

        mutexFor(item.id).withLock {
            if (pauseFlag.get() || cancelFlag.get()) return@withLock
            val latest = repository.getDownloadById(item.id) ?: item
            val completedItem = latest.copy(
                downloadedBytes = totalBytes,
                totalBytes = totalBytes,
                speedBytesPerSec = avgSpeed,
                chunksCount = workers,
                status = STATUS_COMPLETED,
                completedAt = System.currentTimeMillis(),
                errorMessage = null
            )
            finalizeCompletedDownload(item.id, completedItem, targetFile)
        }
    }

    private suspend fun downloadSegmentToChannel(
        downloadId: Long,
        url: String,
        fileChannel: FileChannel,
        segmentIndex: Int,
        spec: SegmentSpec,
        segmentWritten: AtomicLongArray,
        userAgent: String,
        cookieHeader: String?,
        pauseFlag: AtomicBoolean,
        cancelFlag: AtomicBoolean,
        totalDownloaded: AtomicLong
    ) {
        var attempt = 0
        var preferStandardClient = false
        var lastException: IOException? = null

        while (attempt <= MAX_STREAM_RETRIES) {
            if (pauseFlag.get() || cancelFlag.get()) return

            val alreadyWritten = segmentWritten.get(segmentIndex).coerceIn(0L, spec.length)
            var remaining = spec.length - alreadyWritten
            if (remaining <= 0L) return

            val requestStart = spec.startByte + alreadyWritten
            var activeCall: Call? = null
            var activeResponse: Response? = null

            try {
                val client = if (preferStandardClient) standardHttpClient else parallelHttpClient
                val connected = executeHttpWithRedirects(
                    downloadId = downloadId,
                    client = client,
                    initialUrl = url,
                    method = "GET",
                    userAgent = userAgent,
                    cookieHeader = cookieHeader,
                    rangeHeader = "bytes=$requestStart-${spec.endByte}",
                    pauseFlag = pauseFlag,
                    cancelFlag = cancelFlag
                )
                activeCall = connected.call
                activeResponse = connected.response

                if (pauseFlag.get() || cancelFlag.get()) return
                val code = connected.response.code

                if (code == 200 && (segmentIndex > 0 || requestStart > 0L)) {
                    throw RangeNotSupportedException("Server ignored Range header on segment $segmentIndex")
                }
                if (code == 416 && alreadyWritten >= spec.length) {
                    return
                }
                if (code != 206 && !(requestStart == 0L && code == 200)) {
                    throw IOException("HTTP $code while downloading segment $segmentIndex")
                }

                val bodyStream = connected.response.body?.byteStream()
                    ?: throw IOException("Empty response body on segment $segmentIndex")

                var writePos = requestStart
                val buffer = ByteArray(STREAM_BUFFER_SIZE)
                bodyStream.use { input ->
                    while (remaining > 0L) {
                        if (pauseFlag.get() || cancelFlag.get()) return
                        val toRead = min(buffer.size.toLong(), remaining).toInt()
                        val read = input.read(buffer, 0, toRead)
                        if (read == -1) break
                        val byteBuf = ByteBuffer.wrap(buffer, 0, read)
                        while (byteBuf.hasRemaining()) {
                            writePos += fileChannel.write(byteBuf, writePos)
                        }
                        remaining -= read
                        segmentWritten.addAndGet(segmentIndex, read.toLong())
                        totalDownloaded.addAndGet(read.toLong())
                    }
                }

                if (remaining <= 0L || pauseFlag.get() || cancelFlag.get()) {
                    return
                }
                throw IOException("Premature EOF on segment $segmentIndex ($remaining bytes remaining)")
            } catch (rne: RangeNotSupportedException) {
                throw rne
            } catch (e: IOException) {
                if (pauseFlag.get() || cancelFlag.get()) return
                if (!preferStandardClient && e.message?.contains("protocol", ignoreCase = true) == true) {
                    preferStandardClient = true
                }
                lastException = e
                if (!isLoopbackUrl(url) && !queryCurrentConnectivity()) {
                    isNetworkAvailable.set(false)
                    waitForNetworkRecovery(pauseFlag, cancelFlag)
                    continue
                }
                attempt++
                if (attempt > MAX_STREAM_RETRIES) {
                    throw e
                }
                val backoffMs = (RETRY_BACKOFF_BASE_MS * (1L shl min(attempt - 1, 4))).coerceAtMost(8_000L)
                delay(backoffMs)
            } finally {
                runCatching { activeResponse?.close() }
                if (activeCall != null) {
                    unregisterCall(downloadId, activeCall)
                }
            }
        }

        if (lastException != null) {
            throw lastException
        }
    }

    private suspend fun downloadSingleFastStream(
        item: DownloadItemEntity,
        transferUrl: String,
        targetFile: File,
        totalBytes: Long,
        supportsRanges: Boolean,
        userAgent: String,
        cookieHeader: String?,
        pauseFlag: AtomicBoolean,
        cancelFlag: AtomicBoolean,
        latestDownloaded: AtomicLong,
        latestTotal: AtomicLong
    ) {
        var canResumeRange = supportsRanges
        var allow416Reset = true
        var attempt = 0
        val startTimeMs = System.currentTimeMillis()
        val initialBytesOnDisk = if (targetFile.exists()) targetFile.length() else 0L

        while (attempt <= MAX_STREAM_RETRIES) {
            if (pauseFlag.get() || cancelFlag.get()) {
                if (pauseFlag.get() && !cancelFlag.get()) {
                    withContext(NonCancellable) {
                        persistPausedState(item.id, latestDownloaded.get(), latestTotal.get())
                    }
                }
                return
            }

            val existingBytes = if (canResumeRange && targetFile.exists() && targetFile.length() > 0L) {
                targetFile.length()
            } else {
                0L
            }

            val knownTotal = latestTotal.get().let { if (it > 0L) it else totalBytes }
            if (knownTotal > 0L && existingBytes >= knownTotal) {
                mutexFor(item.id).withLock {
                    if (cancelFlag.get()) return@withLock
                    val latest = repository.getDownloadById(item.id) ?: item
                    val completedItem = latest.copy(
                        downloadedBytes = knownTotal,
                        totalBytes = knownTotal,
                        speedBytesPerSec = 0L,
                        chunksCount = 1,
                        status = STATUS_COMPLETED,
                        completedAt = System.currentTimeMillis(),
                        errorMessage = null
                    )
                    finalizeCompletedDownload(item.id, completedItem, targetFile)
                }
                return
            }

            var activeCall: Call? = null
            var activeResponse: Response? = null
            try {
                val rangeHeader = if (existingBytes > 0L && canResumeRange) {
                    "bytes=$existingBytes-"
                } else {
                    null
                }
                val connected = executeHttpWithRedirects(
                    downloadId = item.id,
                    client = standardHttpClient,
                    initialUrl = transferUrl,
                    method = "GET",
                    userAgent = userAgent,
                    cookieHeader = cookieHeader,
                    rangeHeader = rangeHeader,
                    pauseFlag = pauseFlag,
                    cancelFlag = cancelFlag
                )
                activeCall = connected.call
                activeResponse = connected.response

                if (pauseFlag.get() || cancelFlag.get()) {
                    if (pauseFlag.get() && !cancelFlag.get()) {
                        withContext(NonCancellable) {
                            persistPausedState(item.id, existingBytes, knownTotal)
                        }
                    }
                    return
                }

                val responseCode = connected.response.code

                if (responseCode == 416 && existingBytes > 0L && allow416Reset) {
                    allow416Reset = false
                    canResumeRange = false
                    runCatching { activeResponse.close() }
                    unregisterCall(item.id, activeCall)
                    activeCall = null
                    activeResponse = null
                    runCatching { targetFile.delete() }
                    latestDownloaded.set(0L)
                    continue
                }

                if (responseCode !in 200..299) {
                    throw IOException("Server returned HTTP $responseCode")
                }

                val isResuming = responseCode == 206 && existingBytes > 0L
                if (isResuming || connected.response.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true) {
                    canResumeRange = true
                }
                val contentLenHeader = connected.response.header("Content-Length")?.trim()?.toLongOrNull()
                    ?: connected.response.body?.contentLength()
                    ?: -1L
                val effectiveTotal = when {
                    knownTotal > 0L -> knownTotal
                    isResuming && contentLenHeader > 0L -> existingBytes + contentLenHeader
                    contentLenHeader > 0L -> contentLenHeader
                    else -> -1L
                }
                latestTotal.set(effectiveTotal)

                val bodyStream = connected.response.body?.byteStream()
                    ?: throw IOException("Empty response body")

                var downloaded = if (isResuming) existingBytes else 0L
                latestDownloaded.set(downloaded)
                var lastReportedBytes = downloaded
                var lastTickMs = System.currentTimeMillis()
                var lastDbPersistMs = lastTickMs
                var smoothedSpeed = 0L

                bodyStream.use { input ->
                    FileOutputStream(targetFile, isResuming).use { output ->
                        val buffer = ByteArray(STREAM_BUFFER_SIZE)
                        while (true) {
                            if (pauseFlag.get() || cancelFlag.get()) {
                                output.flush()
                                latestDownloaded.set(downloaded)
                                if (pauseFlag.get() && !cancelFlag.get()) {
                                    withContext(NonCancellable) {
                                        persistPausedState(item.id, downloaded, effectiveTotal)
                                    }
                                }
                                return
                            }
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            latestDownloaded.set(downloaded)

                            val now = System.currentTimeMillis()
                            if (now - lastTickMs >= 150L) {
                                val dt = (now - lastTickMs).coerceAtLeast(1L)
                                val instantSpeed = ((downloaded - lastReportedBytes).coerceAtLeast(0L) * 1000L) / dt
                                smoothedSpeed = if (smoothedSpeed <= 0L) {
                                    instantSpeed
                                } else {
                                    ((instantSpeed * 65L) + (smoothedSpeed * 35L)) / 100L
                                }
                                lastReportedBytes = downloaded
                                lastTickMs = now
                                val shouldPersistDb = (now - lastDbPersistMs) >= DB_PROGRESS_FLUSH_MS
                                if (shouldPersistDb) {
                                    lastDbPersistMs = now
                                }
                                persistDownloadingProgress(
                                    downloadId = item.id,
                                    downloadedBytes = downloaded,
                                    totalBytes = effectiveTotal,
                                    speedBytesPerSec = smoothedSpeed,
                                    chunksCount = 1,
                                    pauseFlag = pauseFlag,
                                    cancelFlag = cancelFlag,
                                    persistToDb = shouldPersistDb
                                )
                            }
                        }
                        output.flush()
                    }
                }

                if (pauseFlag.get() && !cancelFlag.get()) {
                    withContext(NonCancellable) {
                        persistPausedState(item.id, downloaded, effectiveTotal)
                    }
                    return
                }
                if (cancelFlag.get()) return

                if (effectiveTotal > 0L && downloaded < effectiveTotal) {
                    throw IOException("Stream ended early ($downloaded / $effectiveTotal bytes)")
                }

                val finalTotal = if (effectiveTotal > 0L) effectiveTotal else downloaded
                val totalElapsedMs = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(1L)
                val avgSpeed = ((downloaded - initialBytesOnDisk).coerceAtLeast(1L) * 1000L) / totalElapsedMs

                mutexFor(item.id).withLock {
                    if (pauseFlag.get() || cancelFlag.get()) return@withLock
                    val latest = repository.getDownloadById(item.id) ?: item
                    val completedItem = latest.copy(
                        downloadedBytes = downloaded,
                        totalBytes = finalTotal,
                        speedBytesPerSec = avgSpeed,
                        chunksCount = 1,
                        status = STATUS_COMPLETED,
                        completedAt = System.currentTimeMillis(),
                        errorMessage = null
                    )
                    finalizeCompletedDownload(item.id, completedItem, targetFile)
                }
                return
            } catch (e: IOException) {
                if (pauseFlag.get() && !cancelFlag.get()) {
                    withContext(NonCancellable) {
                        persistPausedState(item.id, latestDownloaded.get(), latestTotal.get())
                    }
                    return
                }
                if (cancelFlag.get()) return
                if (!isLoopbackUrl(transferUrl) && !queryCurrentConnectivity()) {
                    isNetworkAvailable.set(false)
                    waitForNetworkRecovery(pauseFlag, cancelFlag)
                    continue
                }
                attempt++
                if (attempt > MAX_STREAM_RETRIES || (!canResumeRange && latestDownloaded.get() > 0L && attempt > 3)) {
                    throw e
                }
                val backoffMs = (RETRY_BACKOFF_BASE_MS * (1L shl min(attempt - 1, 4))).coerceAtMost(8_000L)
                delay(backoffMs)
            } finally {
                runCatching { activeResponse?.close() }
                if (activeCall != null) {
                    unregisterCall(item.id, activeCall)
                }
            }
        }
    }

    private suspend fun waitForNetworkRecovery(
        pauseFlag: AtomicBoolean,
        cancelFlag: AtomicBoolean
    ) {
        withTimeoutOrNull(NETWORK_WAIT_TIMEOUT_MS) {
            var waited = 0L
            while (!isNetworkAvailable.get() && !pauseFlag.get() && !cancelFlag.get() && waited < NETWORK_WAIT_TIMEOUT_MS) {
                withTimeoutOrNull(1_000L) {
                    networkRestoredSignal.collect { return@collect }
                }
                if (isNetworkAvailable.get() || queryCurrentConnectivity()) {
                    isNetworkAvailable.set(true)
                    break
                }
                waited += 1_000L
            }
        }
    }

    private fun tempDownloadingFileFor(targetFile: File): File {
        return File("${targetFile.absolutePath}.downloading")
    }

    private fun metaFileFor(targetFile: File): File {
        return File("${targetFile.absolutePath}.apexmeta")
    }

    private fun writeSegmentMeta(
        metaFile: File,
        totalBytes: Long,
        segments: List<SegmentSpec>,
        segmentWritten: AtomicLongArray
    ) {
        runCatching {
            val sb = StringBuilder(64 + segments.size * 32)
            sb.append(totalBytes).append('\n')
            sb.append(segments.size).append('\n')
            for (i in segments.indices) {
                val spec = segments[i]
                sb.append(spec.startByte).append(',')
                    .append(spec.endByte).append(',')
                    .append(segmentWritten.get(i).coerceIn(0L, spec.length))
                    .append('\n')
            }
            metaFile.writeText(sb.toString())
        }
    }

    private fun readSegmentMeta(metaFile: File): SegmentMeta? {
        if (!metaFile.exists()) return null
        return runCatching {
            val lines = metaFile.readLines().filter { it.isNotBlank() }
            if (lines.size < 3) return null
            val totalBytes = lines[0].trim().toLongOrNull() ?: return null
            val count = lines[1].trim().toIntOrNull() ?: return null
            if (count <= 0 || lines.size < 2 + count) return null
            val segments = (0 until count).map { idx ->
                val parts = lines[2 + idx].split(',')
                if (parts.size != 3) return null
                SegmentSpec(
                    startByte = parts[0].toLong(),
                    endByte = parts[1].toLong(),
                    initialWritten = parts[2].toLong()
                )
            }
            SegmentMeta(totalBytes = totalBytes, segments = segments)
        }.getOrNull()
    }

    private fun deleteWorkFilesOnly(targetFile: File) {
        runCatching { tempDownloadingFileFor(targetFile).delete() }
        runCatching { metaFileFor(targetFile).delete() }
        for (i in 0 until MAX_WORK_SEGMENTS) {
            runCatching { File("${targetFile.absolutePath}.part$i").delete() }
        }
        runCatching { File("${targetFile.absolutePath}.merging").delete() }
    }

    private fun deleteFileAndParts(targetFile: File) {
        runCatching { targetFile.delete() }
        deleteWorkFilesOnly(targetFile)
    }

    private fun computeCurrentDiskBytesForTarget(targetFile: File): Long {
        val meta = readSegmentMeta(metaFileFor(targetFile))
        if (meta != null && tempDownloadingFileFor(targetFile).exists()) {
            return meta.segments.sumOf { it.initialWritten.coerceIn(0L, it.length) }
        }
        var partSum = 0L
        var foundParts = false
        for (i in 0 until MAX_WORK_SEGMENTS) {
            val part = File("${targetFile.absolutePath}.part$i")
            if (part.exists()) {
                foundParts = true
                partSum += part.length()
            }
        }
        if (foundParts) return partSum
        return if (targetFile.exists()) targetFile.length() else 0L
    }

    fun computeCurrentDiskBytes(item: DownloadItemEntity): Long {
        if (item.localFilePath.isBlank()) return 0L
        return computeCurrentDiskBytesForTarget(File(item.localFilePath))
    }

    private class RangeNotSupportedException(message: String) : IOException(message)

    private data class ConnectedOkHttpResponse(
        val call: Call,
        val response: Response,
        val finalUrl: String
    )

    private fun executeHttpWithRedirects(
        downloadId: Long,
        client: OkHttpClient,
        initialUrl: String,
        method: String,
        userAgent: String,
        cookieHeader: String?,
        rangeHeader: String?,
        pauseFlag: AtomicBoolean,
        cancelFlag: AtomicBoolean
    ): ConnectedOkHttpResponse {
        var currentUrl = initialUrl
        var redirects = 0
        var currentMethod = method
        val initialOrigin = runCatching {
            val u = URL(initialUrl)
            "${u.protocol}://${u.authority}/"
        }.getOrNull()

        while (redirects <= MAX_REDIRECTS) {
            if (pauseFlag.get() || cancelFlag.get()) {
                throw IOException("Download paused or canceled")
            }

            val reqBuilder = Request.Builder()
                .url(currentUrl)
                .header("User-Agent", userAgent)
                .header("Accept", "*/*")
                .header("Accept-Encoding", "identity")
                .header("Connection", "keep-alive")

            if (!initialOrigin.isNullOrBlank()) {
                reqBuilder.header("Referer", initialOrigin)
            }
            if (!rangeHeader.isNullOrBlank() && currentMethod != "HEAD") {
                reqBuilder.header("Range", rangeHeader)
            }
            val hopCookie = runCatching {
                CookieManager.getInstance().getCookie(currentUrl)
            }.getOrNull() ?: cookieHeader
            if (!hopCookie.isNullOrBlank()) {
                reqBuilder.header("Cookie", hopCookie)
            }

            if (currentMethod == "HEAD") {
                reqBuilder.head()
            } else {
                reqBuilder.get()
            }

            val call = client.newCall(reqBuilder.build())
            registerCall(downloadId, call)
            try {
                val response = call.execute()
                val code = response.code
                if (code in REDIRECT_CODES) {
                    val location = response.header("Location")
                    response.close()
                    unregisterCall(downloadId, call)
                    if (location.isNullOrBlank()) {
                        throw IOException("Redirect (HTTP $code) missing Location header")
                    }
                    currentUrl = URL(URL(currentUrl), location.trim()).toString()
                    if (code == 303 && currentMethod != "HEAD") {
                        currentMethod = "GET"
                    }
                    redirects++
                    continue
                }
                isNetworkAvailable.set(true)
                return ConnectedOkHttpResponse(
                    call = call,
                    response = response,
                    finalUrl = response.request.url.toString()
                )
            } catch (e: Exception) {
                unregisterCall(downloadId, call)
                throw e
            }
        }
        throw IOException("Too many redirects (> $MAX_REDIRECTS)")
    }

    private data class ProbeResult(
        val finalUrl: String,
        val contentLength: Long,
        val supportsRanges: Boolean,
        val mimeType: String,
        val suggestedName: String
    )

    private fun probeRemoteResource(
        downloadId: Long,
        url: String,
        userAgent: String,
        cookieHeader: String?,
        pauseFlag: AtomicBoolean,
        cancelFlag: AtomicBoolean
    ): ProbeResult {
        var bestResult: ProbeResult? = null

        // First try GET bytes=0-0 (works with S3/CDN presigned URLs that reject HEAD)
        for (attempt in 0..1) {
            if (pauseFlag.get() || cancelFlag.get()) {
                return ProbeResult(url, -1L, false, "", "")
            }
            val attemptResult = runCatching {
                val connected = executeHttpWithRedirects(
                    downloadId = downloadId,
                    client = standardHttpClient,
                    initialUrl = url,
                    method = "GET",
                    userAgent = userAgent,
                    cookieHeader = cookieHeader,
                    rangeHeader = "bytes=0-0",
                    pauseFlag = pauseFlag,
                    cancelFlag = cancelFlag
                )
                try {
                    extractProbeFromResponse(connected.response, connected.finalUrl)
                } finally {
                    runCatching { connected.response.close() }
                    unregisterCall(downloadId, connected.call)
                }
            }.getOrNull()

            if (attemptResult != null) {
                bestResult = attemptResult
                if (attemptResult.contentLength > 0L && attemptResult.supportsRanges) {
                    return attemptResult
                }
                break
            }
        }

        // Fallback HEAD probe if GET 0-0 didn't confirm length or range support
        if (bestResult == null || bestResult.contentLength <= 0L || !bestResult.supportsRanges) {
            val headResult = runCatching {
                val connected = executeHttpWithRedirects(
                    downloadId = downloadId,
                    client = standardHttpClient,
                    initialUrl = bestResult?.finalUrl ?: url,
                    method = "HEAD",
                    userAgent = userAgent,
                    cookieHeader = cookieHeader,
                    rangeHeader = null,
                    pauseFlag = pauseFlag,
                    cancelFlag = cancelFlag
                )
                try {
                    extractProbeFromResponse(connected.response, connected.finalUrl)
                } finally {
                    runCatching { connected.response.close() }
                    unregisterCall(downloadId, connected.call)
                }
            }.getOrNull()

            if (headResult != null) {
                val mergedLength = max(bestResult?.contentLength ?: -1L, headResult.contentLength)
                val mergedRanges = (bestResult?.supportsRanges == true) || headResult.supportsRanges
                return ProbeResult(
                    finalUrl = headResult.finalUrl.ifBlank { bestResult?.finalUrl ?: url },
                    contentLength = mergedLength,
                    supportsRanges = mergedRanges,
                    mimeType = headResult.mimeType.ifBlank { bestResult?.mimeType.orEmpty() },
                    suggestedName = headResult.suggestedName.ifBlank { bestResult?.suggestedName.orEmpty() }
                )
            }
        }

        return bestResult ?: ProbeResult(
            finalUrl = url,
            contentLength = -1L,
            supportsRanges = false,
            mimeType = "",
            suggestedName = ""
        )
    }

    private fun extractProbeFromResponse(response: Response, finalUrl: String): ProbeResult {
        val code = response.code
        val contentRange = response.header("Content-Range").orEmpty()
        val acceptRanges = response.header("Accept-Ranges").orEmpty()
        val supportsRanges = (code == 206 && contentRange.startsWith("bytes", ignoreCase = true)) ||
            acceptRanges.contains("bytes", ignoreCase = true)

        val totalFromRange = contentRange.substringAfter('/', "")
            .trim()
            .takeIf { it.isNotEmpty() && it != "*" }
            ?.toLongOrNull()
        val headerContentLen = response.header("Content-Length")
            ?.trim()
            ?.toLongOrNull()
            ?: response.body?.contentLength()
            ?: -1L

        val contentLength = when {
            totalFromRange != null && totalFromRange > 0L -> totalFromRange
            code == 200 && headerContentLen > 0L -> headerContentLen
            headerContentLen > 1L -> headerContentLen
            else -> -1L
        }
        val mimeType = response.header("Content-Type")?.substringBefore(';')?.trim().orEmpty()
        val disposition = response.header("Content-Disposition")
        val suggestedName = if (!disposition.isNullOrBlank()) {
            URLUtil.guessFileName(finalUrl, disposition, mimeType)
        } else {
            ""
        }
        return ProbeResult(
            finalUrl = finalUrl,
            contentLength = contentLength,
            supportsRanges = supportsRanges,
            mimeType = mimeType,
            suggestedName = suggestedName
        )
    }

    companion object {
        private const val STREAM_BUFFER_SIZE = 256 * 1024 // 256 KB buffered transfer
        private const val SOCKET_RECEIVE_BUFFER_BYTES = 512 * 1024 // 512 KB SO_RCVBUF per TCP stream
        private const val DB_PROGRESS_FLUSH_MS = 350L
        private const val NETWORK_WAIT_TIMEOUT_MS = 60_000L
        const val DEFAULT_TURBO_CHUNKS = 8
        const val MEDIUM_TURBO_CHUNKS = 4
        private const val MAX_WORK_SEGMENTS = 32
        private const val TARGET_SEGMENT_SIZE_BYTES = 1_536 * 1024L // ~1.5 MB per segment
        private const val MIN_PARALLEL_BYTES = 64 * 1024L // 64 KB
        private const val HIGH_TURBO_THRESHOLD_BYTES = 256 * 1024L // 256 KB -> 8× Turbo
        private const val MAX_STREAM_RETRIES = 8
        private const val RETRY_BACKOFF_BASE_MS = 500L
        private const val MAX_REDIRECTS = 10
        private val REDIRECT_CODES = intArrayOf(301, 302, 303, 307, 308)

        const val STATUS_DOWNLOADING = "DOWNLOADING"
        const val STATUS_PAUSED = "PAUSED"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_CANCELED = "CANCELED"

        private val EXPLICIT_MIME_MAP = mapOf(
            "apk" to "application/vnd.android.package-archive",
            "pdf" to "application/pdf",
            "zip" to "application/zip",
            "rar" to "application/vnd.rar",
            "7z" to "application/x-7z-compressed",
            "tar" to "application/x-tar",
            "gz" to "application/gzip",
            "mp4" to "video/mp4",
            "mkv" to "video/x-matroska",
            "webm" to "video/webm",
            "mp3" to "audio/mpeg",
            "m4a" to "audio/mp4",
            "wav" to "audio/wav",
            "png" to "image/png",
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "webp" to "image/webp",
            "gif" to "image/gif",
            "svg" to "image/svg+xml",
            "txt" to "text/plain",
            "csv" to "text/csv",
            "json" to "application/json",
            "html" to "text/html",
            "htm" to "text/html",
            "doc" to "application/msword",
            "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        )

        @Volatile
        private var INSTANCE: FastDownloadEngine? = null

        fun getInstance(context: Context, repository: BrowserRepository): FastDownloadEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FastDownloadEngine(context.applicationContext, repository).also {
                    INSTANCE = it
                }
            }
        }

        fun sanitizeFileName(raw: String): String {
            val cleaned = raw.trim()
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .take(120)
            return cleaned.ifBlank { "download_${System.currentTimeMillis()}.bin" }
        }

        fun allocateUniqueFile(directory: File, desiredName: String): File {
            var candidate = File(directory, desiredName)
            if (!candidate.exists() &&
                !File("${candidate.absolutePath}.downloading").exists() &&
                !File("${candidate.absolutePath}.part0").exists()
            ) {
                return candidate
            }
            val baseName = desiredName.substringBeforeLast('.', desiredName)
            val ext = desiredName.substringAfterLast('.', "")
            var counter = 1
            while (candidate.exists() ||
                File("${candidate.absolutePath}.downloading").exists() ||
                File("${candidate.absolutePath}.part0").exists()
            ) {
                val numbered = if (ext.isNotEmpty() && baseName != desiredName) {
                    "$baseName ($counter).$ext"
                } else {
                    "$desiredName ($counter)"
                }
                candidate = File(directory, numbered)
                counter++
            }
            return candidate
        }

        fun resolveMimeType(urlOrFileName: String, providedMime: String?): String {
            val cleanedProvided = providedMime?.substringBefore(';')?.trim().orEmpty()
            if (cleanedProvided.isNotEmpty() &&
                !cleanedProvided.equals("application/octet-stream", ignoreCase = true) &&
                !cleanedProvided.equals("binary/octet-stream", ignoreCase = true)
            ) {
                return cleanedProvided
            }
            val cleanTarget = urlOrFileName.substringBefore('?').substringBefore('#')
            val fileNamePart = cleanTarget.substringAfterLast('/')
            val ext = fileNamePart.substringAfterLast('.', "").lowercase(Locale.US)
            if (ext.isNotEmpty() && fileNamePart.contains('.')) {
                EXPLICIT_MIME_MAP[ext]?.let { return it }
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
            }
            return cleanedProvided.ifBlank { "application/octet-stream" }
        }

        fun inferCategory(fileName: String, mimeType: String): String {
            val lowerName = fileName.lowercase(Locale.US)
            val lowerMime = mimeType.lowercase(Locale.US)
            return when {
                lowerMime.startsWith("image/") ||
                    lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
                    lowerName.endsWith(".png") || lowerName.endsWith(".webp") ||
                    lowerName.endsWith(".gif") || lowerName.endsWith(".svg") -> "Image"
                lowerMime.startsWith("video/") ||
                    lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") ||
                    lowerName.endsWith(".webm") || lowerName.endsWith(".mov") -> "Video"
                lowerMime.startsWith("audio/") ||
                    lowerName.endsWith(".mp3") || lowerName.endsWith(".wav") ||
                    lowerName.endsWith(".flac") || lowerName.endsWith(".m4a") ||
                    lowerName.endsWith(".ogg") -> "Audio"
                lowerMime.contains("pdf") || lowerMime.contains("word") ||
                    lowerMime.contains("presentation") || lowerMime.contains("spreadsheet") ||
                    lowerMime.startsWith("text/") ||
                    lowerName.endsWith(".pdf") || lowerName.endsWith(".doc") ||
                    lowerName.endsWith(".docx") || lowerName.endsWith(".txt") ||
                    lowerName.endsWith(".md") || lowerName.endsWith(".html") ||
                    lowerName.endsWith(".csv") || lowerName.endsWith(".json") -> "Document"
                lowerMime.contains("zip") || lowerMime.contains("tar") ||
                    lowerMime.contains("compressed") || lowerMime.contains("archive") ||
                    lowerMime == "application/vnd.android.package-archive" ||
                    lowerName.endsWith(".zip") || lowerName.endsWith(".rar") ||
                    lowerName.endsWith(".7z") || lowerName.endsWith(".tar") ||
                    lowerName.endsWith(".gz") || lowerName.endsWith(".apk") -> "Archive"
                else -> "Other"
            }
        }

        fun formatBytes(bytes: Long): String {
            if (bytes <= 0L) return "0 B"
            val kb = 1024.0
            val mb = kb * 1024.0
            val gb = mb * 1024.0
            return when {
                bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
                bytes >= mb -> String.format(Locale.US, "%.2f MB", bytes / mb)
                bytes >= kb -> String.format(Locale.US, "%.1f KB", bytes / kb)
                else -> "$bytes B"
            }
        }

        fun formatSpeed(bytesPerSec: Long): String {
            if (bytesPerSec <= 0L) return "0 KB/s"
            return "${formatBytes(bytesPerSec)}/s"
        }
    }
}
