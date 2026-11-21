package com.example.automation

import android.webkit.WebView
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

enum class ExecutionLifecycleSignal {
    RUNNING,
    PAUSED,
    CANCELLED
}

data class ExecutionContextAbortReason(
    val code: AutomationErrorCode,
    val message: String
)

/**
 * Cooperative lifecycle, pause/resume gate, duplicate-run prevention, and context binding
 * controller for Phase 5 workflow execution.
 */
class ExecutionLifecycleController {
    private val signalFlow = MutableStateFlow(ExecutionLifecycleSignal.RUNNING)
    private val _abortReason = MutableStateFlow<ExecutionContextAbortReason?>(null)
    val abortReason: StateFlow<ExecutionContextAbortReason?> = _abortReason.asStateFlow()

    private val activeRunFlag = AtomicBoolean(false)
    @Volatile
    var activeSessionId: String = ""
        private set

    @Volatile
    var boundTabId: String = ""
        private set

    @Volatile
    var boundWebViewIdentity: Int = 0
        private set

    @Volatile
    var initialOrigin: String = ""
        private set

    @Volatile
    var isInterruptionAbort: Boolean = false
        private set

    val isPaused: Boolean
        get() = signalFlow.value == ExecutionLifecycleSignal.PAUSED

    val isCancelled: Boolean
        get() = signalFlow.value == ExecutionLifecycleSignal.CANCELLED || _abortReason.value != null

    fun tryBeginSession(
        tabId: String,
        webView: WebView?,
        canonicalOrigin: String
    ): String? {
        if (!activeRunFlag.compareAndSet(false, true)) {
            return null
        }
        val sessionId = "exec_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        activeSessionId = sessionId
        boundTabId = tabId
        boundWebViewIdentity = webView?.let { System.identityHashCode(it) } ?: 0
        initialOrigin = canonicalOrigin
        isInterruptionAbort = false
        _abortReason.value = null
        signalFlow.value = ExecutionLifecycleSignal.RUNNING
        return sessionId
    }

    fun endSession(sessionId: String) {
        if (sessionId.isNotBlank() && sessionId != activeSessionId) return
        activeSessionId = ""
        boundTabId = ""
        boundWebViewIdentity = 0
        initialOrigin = ""
        isInterruptionAbort = false
        _abortReason.value = null
        signalFlow.value = ExecutionLifecycleSignal.RUNNING
        activeRunFlag.set(false)
    }

    fun requestPause(): Boolean {
        if (!activeRunFlag.get() || _abortReason.value != null) return false
        if (signalFlow.value == ExecutionLifecycleSignal.RUNNING) {
            signalFlow.value = ExecutionLifecycleSignal.PAUSED
            return true
        }
        return false
    }

    fun requestResume(): Boolean {
        if (!activeRunFlag.get() || _abortReason.value != null) return false
        if (signalFlow.value == ExecutionLifecycleSignal.PAUSED) {
            signalFlow.value = ExecutionLifecycleSignal.RUNNING
            return true
        }
        return false
    }

    fun requestCancel(
        code: AutomationErrorCode = AutomationErrorCode.CANCELLED,
        message: String = "Execution cancelled."
    ) {
        if (_abortReason.value == null) {
            _abortReason.value = ExecutionContextAbortReason(code, message)
        }
        signalFlow.value = ExecutionLifecycleSignal.CANCELLED
    }

    fun requestInterrupt(
        code: AutomationErrorCode = AutomationErrorCode.CONTEXT_INVALIDATED,
        message: String = "Execution interrupted before completion."
    ) {
        isInterruptionAbort = true
        if (_abortReason.value == null) {
            _abortReason.value = ExecutionContextAbortReason(code, message)
        }
        signalFlow.value = ExecutionLifecycleSignal.CANCELLED
    }

    fun updateBoundWebViewIfUnset(webView: WebView?) {
        if (boundWebViewIdentity == 0 && webView != null) {
            boundWebViewIdentity = System.identityHashCode(webView)
        }
    }

    fun updateAllowedOrigin(newOrigin: String) {
        if (newOrigin.isNotBlank()) {
            initialOrigin = newOrigin
        }
    }

    /**
     * Suspends cleanly while PAUSED without spinning timers or polling WebView.
     * Throws CancellationException if cancelled, or returns abort reason if aborted by context event.
     */
    suspend fun awaitRunnableOrAbort(): ExecutionContextAbortReason? {
        val existingAbort = _abortReason.value
        if (existingAbort != null) return existingAbort
        if (signalFlow.value == ExecutionLifecycleSignal.CANCELLED) {
            throw CancellationException("Execution cancelled")
        }
        if (signalFlow.value == ExecutionLifecycleSignal.PAUSED) {
            val next = signalFlow.first { it != ExecutionLifecycleSignal.PAUSED }
            val postAbort = _abortReason.value
            if (postAbort != null) return postAbort
            if (next == ExecutionLifecycleSignal.CANCELLED) {
                throw CancellationException("Execution cancelled while paused")
            }
        }
        return _abortReason.value
    }

    /**
     * Bounded interruptible delay that immediately wakes up on Pause/Cancel/Abort.
     */
    suspend fun interruptibleDelay(totalMs: Long) {
        var remaining = totalMs.coerceAtLeast(0L)
        while (remaining > 0L) {
            if (_abortReason.value != null) return
            if (signalFlow.value == ExecutionLifecycleSignal.CANCELLED) {
                throw CancellationException("Execution cancelled")
            }
            awaitRunnableOrAbort()?.let { return }
            val slice = minOf(remaining, 25L)
            delay(slice)
            remaining -= slice
        }
    }

    fun validateBoundContext(
        currentWebView: WebView?,
        isTabStillOpen: Boolean,
        liveUrl: String,
        expectedOrigin: String,
        allowCrossOrigin: Boolean,
        originParser: (String) -> RecordingOriginResult
    ): ExecutionContextAbortReason? {
        _abortReason.value?.let { return it }
        if (!isTabStillOpen) {
            return ExecutionContextAbortReason(
                AutomationErrorCode.TAB_CLOSED,
                "Execution aborted because the target tab was closed."
            )
        }
        if (currentWebView == null) {
            return ExecutionContextAbortReason(
                AutomationErrorCode.WEBVIEW_UNAVAILABLE,
                "Execution aborted because the WebView became unavailable."
            )
        }
        val currentId = System.identityHashCode(currentWebView)
        if (boundWebViewIdentity != 0 && currentId != boundWebViewIdentity) {
            return ExecutionContextAbortReason(
                AutomationErrorCode.WEBVIEW_REPLACED,
                "Execution aborted because the WebView instance was replaced."
            )
        }
        if (!allowCrossOrigin && expectedOrigin.isNotBlank() && liveUrl.isNotBlank() &&
            !liveUrl.equals("apex://home", ignoreCase = true)
        ) {
            val parsedLive = originParser(liveUrl)
            if (!parsedLive.isValid || parsedLive.canonicalOrigin != expectedOrigin) {
                return ExecutionContextAbortReason(
                    AutomationErrorCode.UNEXPECTED_ORIGIN_CHANGE,
                    "Execution aborted because origin changed unexpectedly from '$expectedOrigin' to '${parsedLive.canonicalOrigin.ifBlank { liveUrl }}'."
                )
            }
        }
        return null
    }
}
