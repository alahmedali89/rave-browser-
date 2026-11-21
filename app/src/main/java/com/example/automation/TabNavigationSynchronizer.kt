package com.example.automation

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

enum class NavigationLifecyclePhase {
    IDLE,
    REQUESTED,
    LOADING,
    FINISHED,
    FAILED
}

data class TabNavigationSnapshot(
    val tabId: String,
    val epoch: Long = 0L,
    val urlCommitSeq: Long = 0L,
    val requestedUrl: String = "",
    val activeUrl: String = "",
    val phase: NavigationLifecyclePhase = NavigationLifecyclePhase.IDLE,
    val failureReason: String? = null,
    val updatedAtMs: Long = 0L
)

sealed class NavigationWaitResult {
    data class Completed(
        val finalUrl: String,
        val epoch: Long,
        val urlCommitSeq: Long
    ) : NavigationWaitResult()

    data class Failed(
        val reason: String,
        val url: String,
        val epoch: Long
    ) : NavigationWaitResult()

    data class Timeout(
        val lastUrl: String,
        val epoch: Long,
        val timeoutMs: Long
    ) : NavigationWaitResult()
}

/**
 * Thread-safe per-tab navigation coordinator that tracks navigation request epochs,
 * WebView load phases, redirect transitions, SPA URL commits (doUpdateVisitedHistory),
 * load errors, and tab closure so automation waits deterministically without fixed sleeps.
 */
class TabNavigationSynchronizer(
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    private val states = ConcurrentHashMap<String, MutableStateFlow<TabNavigationSnapshot>>()

    private fun flowFor(tabId: String): MutableStateFlow<TabNavigationSnapshot> {
        return states.computeIfAbsent(tabId) {
            MutableStateFlow(TabNavigationSnapshot(tabId = tabId))
        }
    }

    fun snapshot(tabId: String): TabNavigationSnapshot = flowFor(tabId).value

    /**
     * Marks a new explicit navigation request (address bar submit, automation NAVIGATE, reload).
     * Increments [TabNavigationSnapshot.epoch] so stale events from older requests cannot resolve it.
     */
    fun beginNavigation(tabId: String, targetUrl: String): Long {
        val cleanUrl = targetUrl.trim()
        val isHome = cleanUrl.equals("apex://home", ignoreCase = true)
        val flow = flowFor(tabId)
        var newEpoch = 0L
        while (true) {
            val prev = flow.value
            newEpoch = prev.epoch + 1L
            val next = prev.copy(
                epoch = newEpoch,
                requestedUrl = cleanUrl,
                activeUrl = cleanUrl,
                phase = if (isHome) NavigationLifecyclePhase.FINISHED else NavigationLifecyclePhase.REQUESTED,
                failureReason = null,
                updatedAtMs = timeProvider()
            )
            if (flow.compareAndSet(prev, next)) break
        }
        return newEpoch
    }

    /**
     * Called when WebViewClient.onPageStarted fires for [tabId].
     * Preserves the active request epoch across in-flight server redirects; if the page load
     * was initiated organically (e.g., clicking a link while IDLE/FINISHED/FAILED), advances epoch.
     */
    fun onPageStarted(tabId: String, url: String) {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty() || cleanUrl.startsWith("data:")) return
        val flow = flowFor(tabId)
        while (true) {
            val prev = flow.value
            val isInFlightRequest = prev.phase == NavigationLifecyclePhase.REQUESTED ||
                prev.phase == NavigationLifecyclePhase.LOADING
            val nextEpoch = if (isInFlightRequest) prev.epoch else prev.epoch + 1L
            val next = prev.copy(
                epoch = nextEpoch,
                activeUrl = cleanUrl,
                phase = NavigationLifecyclePhase.LOADING,
                failureReason = null,
                updatedAtMs = timeProvider()
            )
            if (flow.compareAndSet(prev, next)) break
        }
    }

    /**
     * Called when WebViewClient.doUpdateVisitedHistory fires for [tabId].
     * Captures SPA history.pushState / replaceState navigation updates where onPageFinished does not re-fire.
     */
    fun onUrlCommitted(tabId: String, url: String, isReload: Boolean = false) {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty() || cleanUrl.startsWith("data:")) return
        val flow = flowFor(tabId)
        while (true) {
            val prev = flow.value
            val next = if (isReload) {
                prev.copy(
                    epoch = if (prev.phase == NavigationLifecyclePhase.REQUESTED || prev.phase == NavigationLifecyclePhase.LOADING) {
                        prev.epoch
                    } else {
                        prev.epoch + 1L
                    },
                    urlCommitSeq = prev.urlCommitSeq + 1L,
                    activeUrl = cleanUrl,
                    phase = NavigationLifecyclePhase.LOADING,
                    failureReason = null,
                    updatedAtMs = timeProvider()
                )
            } else {
                prev.copy(
                    urlCommitSeq = prev.urlCommitSeq + 1L,
                    activeUrl = cleanUrl,
                    updatedAtMs = timeProvider()
                )
            }
            if (flow.compareAndSet(prev, next)) break
        }
    }

    /**
     * Called when main-frame WebViewClient.onPageFinished fires with progress == 100.
     * Guards against Chromium's post-error onPageFinished overwriting a FAILED epoch.
     */
    fun onPageFinished(tabId: String, url: String) {
        val cleanUrl = url.trim()
        val flow = flowFor(tabId)
        while (true) {
            val prev = flow.value
            if (prev.phase == NavigationLifecyclePhase.FAILED &&
                cleanUrl.equals(prev.activeUrl, ignoreCase = true)
            ) {
                return
            }
            val resolvedUrl = cleanUrl.ifBlank { prev.activeUrl }
            val next = prev.copy(
                activeUrl = resolvedUrl,
                phase = NavigationLifecyclePhase.FINISHED,
                failureReason = null,
                updatedAtMs = timeProvider()
            )
            if (flow.compareAndSet(prev, next)) break
        }
    }

    fun onPageFailed(tabId: String, failingUrl: String, reason: String = "Page failed to load") {
        val cleanUrl = failingUrl.trim()
        val cleanReason = reason.trim().ifBlank { "Page failed to load" }
        val flow = flowFor(tabId)
        while (true) {
            val prev = flow.value
            val next = prev.copy(
                activeUrl = cleanUrl.ifBlank { prev.activeUrl },
                phase = NavigationLifecyclePhase.FAILED,
                failureReason = cleanReason,
                updatedAtMs = timeProvider()
            )
            if (flow.compareAndSet(prev, next)) break
        }
    }

    fun onTabClosed(tabId: String) {
        val flow = states[tabId] ?: return
        while (true) {
            val prev = flow.value
            val next = prev.copy(
                epoch = prev.epoch + 1L,
                phase = NavigationLifecyclePhase.FAILED,
                failureReason = "Tab closed during operation",
                updatedAtMs = timeProvider()
            )
            if (flow.compareAndSet(prev, next)) break
        }
    }

    /**
     * Suspends until [tabId] reaches [NavigationLifecyclePhase.FINISHED] or [NavigationLifecyclePhase.FAILED]
     * at an epoch >= [minEpoch], or until [timeoutMs] expires.
     */
    suspend fun awaitNavigationSettled(
        tabId: String,
        minEpoch: Long,
        timeoutMs: Long
    ): NavigationWaitResult {
        val flow = flowFor(tabId)
        val settled = withTimeoutOrNull(timeoutMs) {
            flow.first { snap ->
                snap.epoch >= minEpoch &&
                    (snap.phase == NavigationLifecyclePhase.FINISHED || snap.phase == NavigationLifecyclePhase.FAILED)
            }
        }
        if (settled == null) {
            val latest = flow.value
            return NavigationWaitResult.Timeout(
                lastUrl = latest.activeUrl.ifBlank { latest.requestedUrl },
                epoch = latest.epoch,
                timeoutMs = timeoutMs
            )
        }
        return if (settled.phase == NavigationLifecyclePhase.FAILED) {
            NavigationWaitResult.Failed(
                reason = settled.failureReason ?: "Navigation failed",
                url = settled.activeUrl.ifBlank { settled.requestedUrl },
                epoch = settled.epoch
            )
        } else {
            NavigationWaitResult.Completed(
                finalUrl = settled.activeUrl.ifBlank { settled.requestedUrl },
                epoch = settled.epoch,
                urlCommitSeq = settled.urlCommitSeq
            )
        }
    }
}
