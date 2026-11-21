package com.example.automation

import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import com.example.data.local.AutomationWorkflowEntity
import java.lang.ref.WeakReference
import java.net.URI
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

data class StepExecutionResult(
    val success: Boolean,
    val message: String,
    val errorCode: AutomationErrorCode = if (success) AutomationErrorCode.NONE else AutomationErrorCode.JS_EXCEPTION,
    val resolvedSelector: String = "",
    val recoveredViaFallback: Boolean = false,
    val verified: Boolean = success,
    val extractedValue: String = "",
    val extractedItems: List<String> = emptyList(),
    val ambiguousCandidates: List<AmbiguousCandidateInfo> = emptyList(),
    val locatorDiagnostics: LocatorResolutionDiagnostics? = null,
    val targetBoundingBox: ElementBoundingBox? = null
)

data class WorkflowExecutionOutcome(
    val status: String, // COMPLETED, FAILED, CANCELLED, INTERRUPTED
    val completedSteps: Int,
    val totalSteps: Int,
    val verificationSummary: String,
    val errorCode: AutomationErrorCode = if (status == "COMPLETED") AutomationErrorCode.NONE else AutomationErrorCode.JS_EXCEPTION,
    val durationMs: Long = 0L,
    val stepRecords: List<StepExecutionRecord> = emptyList(),
    val recoveredStepsCount: Int = 0,
    val branchTraces: List<BranchTraceRecord> = emptyList(),
    val recoveryAttempts: List<RecoveryAttemptRecord> = emptyList(),
    val variableSnapshots: List<VariableDisplayBadge> = emptyList(),
    val lastCheckpoint: PersistedExecutionCheckpoint? = null,
    val ambiguousCandidates: List<AmbiguousCandidateInfo> = emptyList(),
    val lastLocatorDiagnostics: LocatorResolutionDiagnostics? = null
)

sealed class JsEvalResult {
    data class Success(val rawValue: String) : JsEvalResult()
    data class Timeout(val timeoutMs: Long) : JsEvalResult()
    data class Unavailable(val reason: String) : JsEvalResult()
    data class Failure(val reason: String) : JsEvalResult()
}

data class SelectorValidationResult(
    val isValid: Boolean,
    val reason: String = ""
)

class AutomationEngine(
    private val jsEvalTimeoutMs: Long = DEFAULT_JS_EVAL_TIMEOUT_MS,
    private val navTimeoutMs: Long = DEFAULT_NAV_TIMEOUT_MS,
    private val domReadyTimeoutMs: Long = DEFAULT_DOM_READY_TIMEOUT_MS,
    private val domStabilityPollMs: Long = DEFAULT_DOM_POLL_MS
) {

    companion object {
        const val DEFAULT_JS_EVAL_TIMEOUT_MS = 5_000L
        const val DEFAULT_NAV_TIMEOUT_MS = 25_000L
        const val DEFAULT_DOM_READY_TIMEOUT_MS = 8_000L
        const val DEFAULT_DOM_POLL_MS = 60L

        private const val JS_TARGET_HELPERS = """
            function apexCssEsc(v) {
                if (window.CSS && typeof window.CSS.escape === 'function') return window.CSS.escape(String(v));
                return String(v).replace(/([ !"#$%&'()*+,.\/:;<=>?@\[\\\]^`{|}~])/g, '\\$1');
            }
            function apexEscAttr(v) {
                return String(v).replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\r|\n/g, ' ');
            }
            function apexNormalize(str) {
                return String(str || '').replace(/\s+/g, ' ').trim().toLowerCase();
            }
            function apexStripRoleSuffix(hint) {
                return String(hint || '').replace(/\s+(?:field|checkbox|option|button)$/i, '').trim();
            }
            function apexResolveFieldGroupLabel(el) {
                if (!el || !el.tagName) return '';
                const tag = (el.tagName || '').toLowerCase();
                const role = (el.getAttribute && el.getAttribute('role') || '').toLowerCase();
                if (tag === 'button' || tag === 'a' || tag === 'summary' || tag === 'option' || tag === 'label' ||
                    role === 'button' || role === 'link' || role === 'option' || role === 'menuitem') {
                    return '';
                }
                const type = (el.getAttribute && el.getAttribute('type') || '').toLowerCase();
                if (type === 'submit' || type === 'button' || type === 'reset' || type === 'hidden') return '';
                function extractLabelCandidate(node, requireExplicit) {
                    if (!node || node.nodeType !== 1 || node === el || (node.contains && node.contains(el))) return '';
                    const nTag = (node.tagName || '').toLowerCase();
                    if (nTag === 'h1' || nTag === 'h2' || nTag === 'h3' || nTag === 'h4' || nTag === 'h5' || nTag === 'h6' ||
                        nTag === 'button' || nTag === 'a' || nTag === 'input' || nTag === 'select' || nTag === 'textarea' || nTag === 'form') {
                        return '';
                    }
                    const forAttr = node.getAttribute && node.getAttribute('for');
                    if (forAttr && (!el.id || forAttr !== el.id) && document.getElementById(forAttr)) return '';
                    if (node.querySelector && node.querySelector('input:not([type="hidden"]), select, textarea, button, a[href]')) return '';
                    const nCls = String(node.className || '').toLowerCase();
                    const isExplicit = nTag === 'label' || nTag === 'legend' || /(?:^|[\s_-])(label|field-label|form-label|control-label|input-label)(?:$|[\s_-])/.test(nCls);
                    if (requireExplicit && !isExplicit && ((nTag !== 'span' && nTag !== 'div') || nCls.indexOf('label') < 0)) return '';
                    const raw = apexNormalize(node.innerText || node.textContent);
                    if (!raw || raw.length > 48) return '';
                    if (!isExplicit && /[.!?]$/.test(raw)) return '';
                    return raw;
                }
                try {
                    const prev = el.previousElementSibling;
                    if (prev) {
                        const pCtrls = el.parentElement && el.parentElement.querySelectorAll
                            ? el.parentElement.querySelectorAll('input:not([type="hidden"]):not([type="submit"]):not([type="button"]), select, textarea').length
                            : 2;
                        const pTxt = extractLabelCandidate(prev, pCtrls > 1);
                        if (pTxt) return pTxt;
                    }
                    let cur = el.parentElement;
                    let depth = 0;
                    while (cur && cur.nodeType === 1 && depth < 3) {
                        const cTag = (cur.tagName || '').toLowerCase();
                        if (cTag === 'form' || cTag === 'body' || cTag === 'html' || cTag === 'main' || cTag === 'table' || cTag === 'fieldset') break;
                        const ctrls = cur.querySelectorAll
                            ? cur.querySelectorAll('input:not([type="hidden"]):not([type="submit"]):not([type="button"]), select, textarea, [role="combobox"], [role="listbox"], [role="spinbutton"]')
                            : [];
                        if (ctrls.length > 1) break;
                        const curPrev = cur.previousElementSibling;
                        if (curPrev) {
                            const parentCtrls = cur.parentElement && cur.parentElement.querySelectorAll
                                ? cur.parentElement.querySelectorAll('input:not([type="hidden"]):not([type="submit"]):not([type="button"]), select, textarea, [role="combobox"], [role="listbox"], [role="spinbutton"]')
                                : [];
                            const cpTxt = extractLabelCandidate(curPrev, parentCtrls.length > 1);
                            if (cpTxt) return cpTxt;
                        }
                        if (ctrls.length <= 1 && cur.querySelector) {
                            const innerLbl = cur.querySelector('label, [class*="label"], [class*="Label"]');
                            if (innerLbl) {
                                const ilTxt = extractLabelCandidate(innerLbl, false);
                                if (ilTxt) return ilTxt;
                            }
                        }
                        cur = cur.parentElement;
                        depth++;
                    }
                } catch (_) {}
                return '';
            }
            function apexGetLabel(el) {
                if (!el) return '';
                try {
                    const aria = apexNormalize(el.getAttribute && el.getAttribute('aria-label'));
                    if (aria) return aria;
                    if (el.id) {
                        const lbl = document.querySelector('label[for="' + apexCssEsc(el.id) + '"]');
                        if (lbl && (lbl.innerText || lbl.textContent)) {
                            const txt = apexNormalize(lbl.innerText || lbl.textContent);
                            if (txt) return txt;
                        }
                    }
                    const parentLbl = el.closest ? el.closest('label') : null;
                    if (parentLbl && (parentLbl.innerText || parentLbl.textContent)) {
                        const txt = apexNormalize(parentLbl.innerText || parentLbl.textContent);
                        if (txt) return txt;
                    }
                    const fieldGroupLbl = apexResolveFieldGroupLabel(el);
                    if (fieldGroupLbl) return fieldGroupLbl;
                    const ph = apexNormalize(el.getAttribute && el.getAttribute('placeholder'));
                    if (ph) return ph;
                    const title = apexNormalize(el.getAttribute && el.getAttribute('title'));
                    if (title) return title;
                    const tag = (el.tagName || '').toLowerCase();
                    const type = (el.getAttribute && el.getAttribute('type') || '').toLowerCase();
                    const inner = (tag === 'input' || tag === 'textarea')
                        ? ((type === 'submit' || type === 'button' || type === 'reset') ? apexNormalize(el.value) : '')
                        : apexNormalize(el.innerText || el.textContent);
                    if (inner) return inner;
                    const name = apexNormalize(el.getAttribute && el.getAttribute('name'));
                    if (name) return name;
                } catch (_) {}
                return '';
            }
            function apexIsElementDisabled(el) {
                if (!el) return true;
                if (el.disabled === true) return true;
                const ariaDisabled = (el.getAttribute && el.getAttribute('aria-disabled')) || '';
                return ariaDisabled.toLowerCase() === 'true';
            }
            function apexElementMatchesHint(el, rawHint) {
                const normHint = apexNormalize(rawHint);
                if (!normHint) return { matched: true, exact: false };
                const strippedHint = apexStripRoleSuffix(normHint);
                const elLabel = apexGetLabel(el);
                if (elLabel === normHint || (strippedHint && elLabel === strippedHint)) {
                    return { matched: true, exact: true };
                }
                const tag = (el.tagName || '').toLowerCase();
                const type = (el.getAttribute && el.getAttribute('type') || '').toLowerCase();
                const valueText = (tag === 'input' && (type === 'submit' || type === 'button' || type === 'reset'))
                    ? (el.value || '')
                    : '';
                const bag = apexNormalize([
                    elLabel,
                    el.getAttribute && el.getAttribute('name'),
                    el.getAttribute && el.getAttribute('placeholder'),
                    el.getAttribute && el.getAttribute('aria-label'),
                    el.getAttribute && el.getAttribute('title'),
                    (tag !== 'input' && tag !== 'textarea') ? (el.innerText || el.textContent || '') : '',
                    valueText
                ].filter(Boolean).join(' '));
                if (bag.includes(normHint) || (strippedHint && bag.includes(strippedHint))) {
                    return { matched: true, exact: false };
                }
                return { matched: false, exact: false };
            }
            function apexQueryChainInScope(scopes, segment, isShadowHop, isIframeHop) {
                const nextScopes = [];
                const cleanSeg = String(segment || '').trim();
                if (!cleanSeg) return { ok: false, nodes: [], error: 'Empty selector segment' };
                for (let s = 0; s < scopes.length; s++) {
                    const scope = scopes[s];
                    if (!scope || typeof scope.querySelectorAll !== 'function') continue;
                    let matched = [];
                    try {
                        matched = Array.from(scope.querySelectorAll(cleanSeg));
                    } catch (e) {
                        return { ok: false, nodes: [], error: (e && e.message ? e.message : String(e)) };
                    }
                    for (let m = 0; m < matched.length; m++) {
                        const node = matched[m];
                        if (isShadowHop) {
                            if (node && node.shadowRoot) nextScopes.push(node.shadowRoot);
                        } else if (isIframeHop) {
                            try {
                                const doc = node && (node.contentDocument || (node.contentWindow && node.contentWindow.document));
                                if (doc) nextScopes.push(doc);
                            } catch (_) {}
                        } else {
                            nextScopes.push(node);
                        }
                    }
                }
                return { ok: true, nodes: nextScopes, error: '' };
            }
            function apexQuerySelectorAllUniversal(rawSel) {
                const trimmed = String(rawSel || '').trim();
                if (!trimmed) return { ok: true, nodes: [], error: '' };
                if (trimmed.indexOf('>>') === -1) {
                    try {
                        return { ok: true, nodes: Array.from(document.querySelectorAll(trimmed)), error: '' };
                    } catch (e) {
                        return { ok: false, nodes: [], error: (e && e.message ? e.message : String(e)) };
                    }
                }
                const tokens = [];
                let rem = trimmed;
                while (rem.length > 0) {
                    const idxShadow = rem.indexOf('>>>');
                    const idxFrame = rem.indexOf('>>');
                    if (idxShadow !== -1 && (idxFrame === -1 || idxShadow <= idxFrame)) {
                        tokens.push({ seg: rem.slice(0, idxShadow).trim(), hop: 'shadow' });
                        rem = rem.slice(idxShadow + 3);
                    } else if (idxFrame !== -1) {
                        tokens.push({ seg: rem.slice(0, idxFrame).trim(), hop: 'iframe' });
                        rem = rem.slice(idxFrame + 2);
                    } else {
                        tokens.push({ seg: rem.trim(), hop: 'none' });
                        rem = '';
                    }
                }
                let currentScopes = [document];
                for (let i = 0; i < tokens.length; i++) {
                    const tok = tokens[i];
                    const res = apexQueryChainInScope(
                        currentScopes,
                        tok.seg,
                        tok.hop === 'shadow',
                        tok.hop === 'iframe'
                    );
                    if (!res.ok) return res;
                    currentScopes = res.nodes;
                    if (currentScopes.length === 0) break;
                }
                return { ok: true, nodes: currentScopes, error: '' };
            }
            function apexResolveTarget(sel, labelHint, defaultQuery) {
                const trimmedSel = String(sel || '').trim();
                const trimmedHint = apexNormalize(labelHint);
                let candidates = [];
                if (trimmedSel) {
                    const queryRes = apexQuerySelectorAllUniversal(trimmedSel);
                    if (!queryRes.ok) {
                        return {
                            ok: false,
                            code: 'INVALID_SELECTOR',
                            msg: 'Invalid CSS selector "' + trimmedSel + '": ' + queryRes.error
                        };
                    }
                    candidates = queryRes.nodes;
                    if (candidates.length === 0) {
                        return {
                            ok: false,
                            code: 'ELEMENT_NOT_FOUND',
                            msg: 'No element matched selector "' + trimmedSel + '"'
                        };
                    }
                    if (candidates.length === 1) {
                        const only = candidates[0];
                        if (apexIsElementDisabled(only)) {
                            return {
                                ok: false,
                                code: 'ELEMENT_DISABLED',
                                msg: 'Target element is disabled for "' + (labelHint || trimmedSel) + '"'
                            };
                        }
                        return { ok: true, el: only };
                    }
                    if (!trimmedHint) {
                        return {
                            ok: false,
                            code: 'ELEMENT_AMBIGUOUS',
                            msg: 'Ambiguous selector "' + trimmedSel + '" matched ' + candidates.length + ' elements'
                        };
                    }
                } else {
                    if (!trimmedHint) {
                        return {
                            ok: false,
                            code: 'INVALID_SELECTOR',
                            msg: 'Step has neither CSS selector nor target label'
                        };
                    }
                    try {
                        candidates = Array.from(document.querySelectorAll(defaultQuery));
                    } catch (e) {
                        return {
                            ok: false,
                            code: 'INVALID_SELECTOR',
                            msg: 'Invalid fallback query: ' + (e && e.message ? e.message : String(e))
                        };
                    }
                    if (candidates.length === 0) {
                        return {
                            ok: false,
                            code: 'ELEMENT_NOT_FOUND',
                            msg: 'No matching elements found on page for "' + labelHint + '"'
                        };
                    }
                }
                const exactMatches = [];
                const partialMatches = [];
                for (let i = 0; i < candidates.length; i++) {
                    const el = candidates[i];
                    const match = apexElementMatchesHint(el, trimmedHint);
                    if (match.matched) {
                        if (match.exact) exactMatches.push(el);
                        else partialMatches.push(el);
                    }
                }
                const chosen = exactMatches.length > 0 ? exactMatches : partialMatches;
                if (chosen.length === 0) {
                    return {
                        ok: false,
                        code: 'ELEMENT_NOT_FOUND',
                        msg: 'Target element not found for "' + (labelHint || trimmedSel) + '"'
                    };
                }
                if (chosen.length > 1) {
                    return {
                        ok: false,
                        code: 'ELEMENT_AMBIGUOUS',
                        msg: 'Ambiguous target: ' + chosen.length + ' elements match "' + (labelHint || trimmedSel) + '"'
                    };
                }
                const target = chosen[0];
                if (apexIsElementDisabled(target)) {
                    return {
                        ok: false,
                        code: 'ELEMENT_DISABLED',
                        msg: 'Target element is disabled for "' + (labelHint || trimmedSel) + '"'
                    };
                }
                return { ok: true, el: target };
            }
        """
    }

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    val universalScanner: UniversalDomScanner = UniversalDomScanner(this)

    fun extractDomain(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isBlank() || trimmed.equals("apex://home", ignoreCase = true)) {
            return "Start Page"
        }
        if (trimmed.equals("apex://news", ignoreCase = true)) return "news.apex.browser"
        if (trimmed.equals("apex://store", ignoreCase = true)) return "store.apex.browser"
        return runCatching {
            URI(trimmed).host?.removePrefix("www.")?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: trimmed
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .substringBefore('/')
            .substringBefore('?')
            .ifBlank { "Web Page" }
    }

    fun validateSelector(rawSelector: String): SelectorValidationResult {
        val trimmed = rawSelector.trim()
        if (trimmed.isEmpty()) {
            return SelectorValidationResult(isValid = false, reason = "Selector is empty")
        }
        if (trimmed.contains(">>>") || trimmed.contains(">>")) {
            val segments = trimmed.split(Regex(">>>|>>")).map { it.trim() }
            if (segments.any { it.isEmpty() }) {
                return SelectorValidationResult(isValid = false, reason = "Empty segment in Shadow DOM or iframe selector chain")
            }
            for (seg in segments) {
                val segResult = validateSingleCssSelectorSegment(seg)
                if (!segResult.isValid) return segResult
            }
            return SelectorValidationResult(isValid = true)
        }
        return validateSingleCssSelectorSegment(trimmed)
    }

    private fun validateSingleCssSelectorSegment(trimmed: String): SelectorValidationResult {
        if (trimmed.any { it.code < 32 }) {
            return SelectorValidationResult(isValid = false, reason = "Selector contains invalid control characters")
        }
        if (trimmed.startsWith(">") || trimmed.startsWith("+") || trimmed.startsWith("~") || trimmed.startsWith(",") ||
            trimmed.endsWith(">") || trimmed.endsWith("+") || trimmed.endsWith("~") || trimmed.endsWith(",")
        ) {
            return SelectorValidationResult(isValid = false, reason = "Selector has leading or trailing combinator")
        }
        var quote: Char? = null
        var bracketDepth = 0
        var parenDepth = 0
        var escaped = false
        for (i in trimmed.indices) {
            val ch = trimmed[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (ch == '\\') {
                escaped = true
                continue
            }
            if (quote != null) {
                if (ch == quote) quote = null
                continue
            }
            when (ch) {
                '\'', '"' -> quote = ch
                '[' -> bracketDepth++
                ']' -> {
                    bracketDepth--
                    if (bracketDepth < 0) {
                        return SelectorValidationResult(isValid = false, reason = "Unmatched ']' in selector")
                    }
                }
                '(' -> parenDepth++
                ')' -> {
                    parenDepth--
                    if (parenDepth < 0) {
                        return SelectorValidationResult(isValid = false, reason = "Unmatched ')' in selector")
                    }
                }
            }
        }
        if (escaped) {
            return SelectorValidationResult(isValid = false, reason = "Trailing backslash in selector")
        }
        if (quote != null) {
            return SelectorValidationResult(isValid = false, reason = "Unclosed string quote in selector")
        }
        if (bracketDepth != 0) {
            return SelectorValidationResult(isValid = false, reason = "Unclosed '[' attribute bracket in selector")
        }
        if (parenDepth != 0) {
            return SelectorValidationResult(isValid = false, reason = "Unclosed '(' pseudo-class parenthesis in selector")
        }
        if (trimmed.contains(Regex(",\\s*,"))) {
            return SelectorValidationResult(isValid = false, reason = "Selector contains an empty list segment")
        }
        return SelectorValidationResult(isValid = true)
    }

    suspend fun scanPageElements(
        webView: WebView?,
        currentUrl: String,
        tabId: String = "",
        navigationEpoch: Long = 0L
    ): PageInspectionState {
        return universalScanner.scanPage(
            webView = webView,
            tabId = tabId,
            currentUrl = currentUrl,
            navigationEpoch = navigationEpoch
        )
    }

    fun parseOrigin(rawUrl: String?): RecordingOriginResult {
        val trimmed = rawUrl?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed.equals("null", ignoreCase = true)) {
            return RecordingOriginResult.OpaqueOrUnsupported("", "No active document URL.")
        }

        val schemePrefix = trimmed.substringBefore(':', missingDelimiterValue = "").lowercase()
        if (schemePrefix in setOf("about", "data", "javascript", "blob", "file", "apex", "chrome", "intent", "mailto", "tel")) {
            return RecordingOriginResult.OpaqueOrUnsupported(
                scheme = schemePrefix,
                reason = "Unsupported or opaque URL scheme '$schemePrefix'."
            )
        }

        val authorityCandidate = trimmed.substringBefore('#').substringBefore('?')
        val uri = runCatching { URI(trimmed) }.getOrNull()
            ?: runCatching { URI(authorityCandidate) }.getOrNull()
            ?: return RecordingOriginResult.OpaqueOrUnsupported(
                scheme = schemePrefix,
                reason = "Malformed URL."
            )

        val scheme = uri.scheme?.lowercase()?.trim().orEmpty()
        if (scheme != "http" && scheme != "https") {
            return RecordingOriginResult.OpaqueOrUnsupported(
                scheme = scheme,
                reason = "Only HTTP and HTTPS origins support automation recording."
            )
        }

        val rawHost = uri.host?.trim().orEmpty()
        val host = rawHost.trimEnd('.').lowercase()
        if (host.isEmpty() || host.contains("..") || host.any { it.isWhitespace() }) {
            return RecordingOriginResult.OpaqueOrUnsupported(
                scheme = scheme,
                reason = "Missing or invalid host in URL."
            )
        }

        val effectivePort = when (val port = uri.port) {
            -1 -> if (scheme == "http") 80 else 443
            in 1..65535 -> port
            else -> return RecordingOriginResult.OpaqueOrUnsupported(
                scheme = scheme,
                reason = "Invalid port in URL."
            )
        }

        val canonicalOrigin = "$scheme://$host:$effectivePort"
        val isDefaultPort = (scheme == "http" && effectivePort == 80) ||
            (scheme == "https" && effectivePort == 443)
        val displayOrigin = if (isDefaultPort) "$scheme://$host" else canonicalOrigin

        return RecordingOriginResult.Valid(
            canonicalOrigin = canonicalOrigin,
            displayOrigin = displayOrigin,
            scheme = scheme,
            host = host,
            effectivePort = effectivePort
        )
    }

    fun areSameOrigin(firstUrl: String?, secondUrl: String?): Boolean {
        val first = parseOrigin(firstUrl)
        val second = parseOrigin(secondUrl)
        return first is RecordingOriginResult.Valid &&
            second is RecordingOriginResult.Valid &&
            first.canonicalOrigin == second.canonicalOrigin
    }

    internal fun buildRecorderInstallScript(
        sessionId: String,
        tabId: String,
        expectedOrigin: String,
        initialSequenceNumber: Long = 0L
    ): String {
        val qSessionId = JSONObject.quote(sessionId)
        val qTabId = JSONObject.quote(tabId)
        val qExpectedOrigin = JSONObject.quote(expectedOrigin)
        val safeInitialSeq = initialSequenceNumber.coerceAtLeast(0L)
        return """
            (function() {
                try {
                    if (window.__apexRecorderInstance && typeof window.__apexRecorderInstance.cleanup === 'function') {
                        try { window.__apexRecorderInstance.cleanup(); } catch (_) {}
                    }
                    window.__apexRecorderInstance = null;
                    window.__apexRecordingActive = false;
                    window.__apexRecorderInstalled = false;

                    function getCanonicalOrigin() {
                        try {
                            if (typeof window.origin === 'string' && window.origin === 'null') return '';
                            var loc = window.location;
                            if (!loc) return '';
                            var proto = String(loc.protocol || '').toLowerCase();
                            if (proto !== 'http:' && proto !== 'https:') return '';
                            var scheme = proto.slice(0, -1);
                            var host = String(loc.hostname || '').trim().replace(/\.+$/, '').toLowerCase();
                            if (!host) return '';
                            var rawPort = String(loc.port || '');
                            var port = rawPort ? parseInt(rawPort, 10) : (scheme === 'http' ? 80 : 443);
                            if (!port || isNaN(port) || port < 1 || port > 65535) return '';
                            return scheme + '://' + host + ':' + port;
                        } catch (_) {
                            return '';
                        }
                    }

                    var actualOrigin = getCanonicalOrigin();
                    var expectedOrigin = $qExpectedOrigin;
                    if (!actualOrigin || actualOrigin !== expectedOrigin) {
                        return JSON.stringify({
                            ok: false,
                            reason: 'origin_mismatch',
                            actualOrigin: actualOrigin || ''
                        });
                    }

                    function escCss(v) {
                        if (window.CSS && typeof window.CSS.escape === 'function') return window.CSS.escape(String(v));
                        return String(v).replace(/([ !"#$%&'()*+,.\/:;<=>?@\[\\\]^`{|}~])/g, '\\$1');
                    }
                    function escAttr(v) {
                        return String(v).replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\r|\n/g, ' ');
                    }
                    function isStableId(id) {
                        if (!id) return false;
                        var clean = String(id).trim();
                        if (!clean || clean.length > 80 || /\s/.test(clean)) return false;
                        if (/^(?:react-select-\d+.*|:r[0-9a-z]+:|«r[0-9a-z]+»|_r_[0-9a-z]+_|radix-.*-\d+|downshift-\d+.*|mui-\d+|ember\d+|el-id-\d+.*|headlessui-.*-\d+|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}|_[0-9a-z]{7,}|\d{5,})$/i.test(clean)) return false;
                        if (/^(?:css-[0-9a-z]{5,}|sc-[a-zA-Z0-9]{5,}|jsx-\d+|svelte-[0-9a-z]{5,}|tw-[0-9a-f]{6,}|style_[0-9a-f]{6,})$/.test(clean)) return false;
                        if (/\d{6,}/.test(clean)) return false;
                        return true;
                    }
                    function getContexts(el) {
                        var framePath = [];
                        var shadowHostPath = [];
                        try {
                            var rootNode = el.getRootNode ? el.getRootNode() : document;
                            var guard = 0;
                            while (typeof ShadowRoot !== 'undefined' && rootNode instanceof ShadowRoot && rootNode.host && guard < 8) {
                                var h = rootNode.host;
                                var ht = (h.tagName || '').toLowerCase();
                                var hs = isStableId(h.id) ? (ht + '#' + escCss(h.id)) :
                                    (h.getAttribute('data-testid') ? (ht + '[data-testid="' + escAttr(h.getAttribute('data-testid')) + '"]') : ht);
                                shadowHostPath.unshift(hs);
                                rootNode = h.getRootNode ? h.getRootNode() : document;
                                guard++;
                            }
                            var win = (el.ownerDocument && el.ownerDocument.defaultView) || window;
                            guard = 0;
                            while (win && win !== window && win.frameElement && guard < 6) {
                                var f = win.frameElement;
                                var fid = f.id || '';
                                var fname = f.getAttribute && f.getAttribute('name') || '';
                                var fs = isStableId(fid) ? ('iframe#' + escCss(fid)) :
                                    (fname ? ('iframe[name="' + escAttr(fname) + '"]') : 'iframe');
                                framePath.unshift(fs);
                                win = win.parent;
                                guard++;
                            }
                        } catch (_) {}
                        return { framePath: framePath, shadowHostPath: shadowHostPath };
                    }
                    function isSheetOrOverlayNode(node) {
                        if (!node || node.nodeType !== 1 || !node.tagName) return false;
                        var t = node.tagName.toLowerCase();
                        if (t === 'dialog') return true;
                        var r = (node.getAttribute('role') || '').toLowerCase();
                        if (r === 'dialog' || r === 'alertdialog' || r === 'listbox' || r === 'menu' || r === 'application') return true;
                        if (node.hasAttribute('aria-modal') || node.hasAttribute('data-bottom-sheet') ||
                            node.hasAttribute('data-sheet') || node.hasAttribute('data-drawer') ||
                            node.hasAttribute('data-modal') || node.hasAttribute('data-calendar') ||
                            node.hasAttribute('data-datepicker') || node.hasAttribute('data-timepicker')) return true;
                        var cls = String(node.className || '').toLowerCase();
                        return /(bottom-sheet|bottomsheet|action-sheet|modal-dialog|\bmodal\b|drawer|offcanvas|datepicker|date-picker|calendar|timepicker|time-picker|flatpickr-calendar|rdp)/.test(cls);
                    }
                    function getParentContext(el) {
                        try {
                            var parts = [];
                            var p = el.parentElement;
                            var steps = 0;
                            while (p && p.nodeType === 1 && steps < 6 && parts.length < 2) {
                                var pt = (p.tagName || '').toLowerCase();
                                if (pt === 'form' || pt === 'header' || pt === 'nav' || pt === 'footer' || pt === 'main' || pt === 'aside' || pt === 'section' || pt === 'fieldset' ||
                                    isSheetOrOverlayNode(p) || p.getAttribute('role') || p.getAttribute('aria-label') || isStableId(p.id)) {
                                    var d = pt;
                                    if (isStableId(p.id)) d += '#' + p.id;
                                    else if (p.getAttribute('data-testid')) d += '[data-testid="' + p.getAttribute('data-testid') + '"]';
                                    else if (p.hasAttribute('data-bottom-sheet')) d += '[data-bottom-sheet]';
                                    else if (p.hasAttribute('data-sheet')) d += '[data-sheet]';
                                    else if (p.hasAttribute('data-drawer')) d += '[data-drawer]';
                                    else if (p.hasAttribute('data-calendar')) d += '[data-calendar]';
                                    else if (p.hasAttribute('data-datepicker')) d += '[data-datepicker]';
                                    else if (p.getAttribute('role')) d += '[role="' + p.getAttribute('role') + '"]';
                                    parts.unshift(d);
                                }
                                p = p.parentElement;
                                steps++;
                            }
                            return parts.join(' > ').slice(0, 120);
                        } catch (_) { return ''; }
                    }
                    function isSensitiveNode(el) {
                        if (!el) return false;
                        var t = String((el.getAttribute && el.getAttribute('type')) || '').toLowerCase();
                        if ((el.tagName || '').toLowerCase() === 'input' && t === 'password') return true;
                        var hint = [
                            t,
                            (el.getAttribute && el.getAttribute('name')) || '',
                            el.id || '',
                            (el.getAttribute && el.getAttribute('autocomplete')) || '',
                            (el.getAttribute && el.getAttribute('aria-label')) || '',
                            (el.getAttribute && el.getAttribute('placeholder')) || ''
                        ].join(' ').toLowerCase();
                        return /(password|passcode|pin\b|secret|token|otp|one-time-code|cvv|cvc|csc|cc-number|cc-csc|ssn|routing)/i.test(hint);
                    }
                    function resolveFieldGroupLabel(el, rootNode) {
                        if (!el || !el.tagName) return '';
                        var tag = (el.tagName || '').toLowerCase();
                        var role = (el.getAttribute && el.getAttribute('role') || '').toLowerCase();
                        if (tag === 'button' || tag === 'a' || tag === 'summary' || tag === 'option' || tag === 'label' ||
                            role === 'button' || role === 'link' || role === 'option' || role === 'menuitem') {
                            return '';
                        }
                        var type = (el.getAttribute && el.getAttribute('type') || '').toLowerCase();
                        if (type === 'submit' || type === 'button' || type === 'reset' || type === 'hidden') return '';
                        var doc = el.ownerDocument || document;
                        var rNode = rootNode || (el.getRootNode && el.getRootNode()) || doc;
                        function extractValidLabelText(node, requireExplicit) {
                            if (!node || node.nodeType !== 1 || node === el || (node.contains && node.contains(el))) return '';
                            var nTag = (node.tagName || '').toLowerCase();
                            if (nTag === 'h1' || nTag === 'h2' || nTag === 'h3' || nTag === 'h4' || nTag === 'h5' || nTag === 'h6' ||
                                nTag === 'button' || nTag === 'a' || nTag === 'input' || nTag === 'select' || nTag === 'textarea' || nTag === 'form') {
                                return '';
                            }
                            var forAttr = node.getAttribute && node.getAttribute('for');
                            if (forAttr && (!el.id || forAttr !== el.id)) {
                                try {
                                    var targetById = (rNode.getElementById ? rNode.getElementById(forAttr) : null) || (doc.getElementById ? doc.getElementById(forAttr) : null);
                                    if (targetById) return '';
                                } catch (_) { return ''; }
                            }
                            if (node.querySelector && node.querySelector('input:not([type="hidden"]), select, textarea, button, a[href]')) return '';
                            var nCls = String(node.className || '').toLowerCase();
                            var isExplicit = nTag === 'label' || nTag === 'legend' || /(?:^|[\s_-])(label|field-label|form-label|control-label|input-label)(?:$|[\s_-])/.test(nCls);
                            if (requireExplicit && !isExplicit && ((nTag !== 'span' && nTag !== 'div') || nCls.indexOf('label') < 0)) return '';
                            var raw = String(node.innerText || node.textContent || '').replace(/\s+/g, ' ').trim();
                            if (!raw || raw.length > 48) return '';
                            if (!isExplicit && /[.!?]$/.test(raw)) return '';
                            return raw;
                        }
                        try {
                            var prev = el.previousElementSibling;
                            if (prev) {
                                var pCtrls = el.parentElement && el.parentElement.querySelectorAll
                                    ? el.parentElement.querySelectorAll('input:not([type="hidden"]):not([type="submit"]):not([type="button"]), select, textarea').length
                                    : 2;
                                var pTxt = extractValidLabelText(prev, pCtrls > 1);
                                if (pTxt) return pTxt;
                            }
                            var cur = el.parentElement;
                            var depth = 0;
                            while (cur && cur.nodeType === 1 && depth < 3) {
                                var cTag = (cur.tagName || '').toLowerCase();
                                if (cTag === 'form' || cTag === 'body' || cTag === 'html' || cTag === 'main' || cTag === 'table' || cTag === 'fieldset') break;
                                var ctrls = cur.querySelectorAll
                                    ? cur.querySelectorAll('input:not([type="hidden"]):not([type="submit"]):not([type="button"]), select, textarea, [role="combobox"], [role="listbox"], [role="spinbutton"]')
                                    : [];
                                if (ctrls.length > 1) break;
                                var curPrev = cur.previousElementSibling;
                                if (curPrev) {
                                    var parentCtrls = cur.parentElement && cur.parentElement.querySelectorAll
                                        ? cur.parentElement.querySelectorAll('input:not([type="hidden"]):not([type="submit"]):not([type="button"]), select, textarea, [role="combobox"], [role="listbox"], [role="spinbutton"]')
                                        : [];
                                    var cpTxt = extractValidLabelText(curPrev, parentCtrls.length > 1);
                                    if (cpTxt) return cpTxt;
                                }
                                if (ctrls.length <= 1 && cur.querySelector) {
                                    var innerLbl = cur.querySelector('label, [class*="label"], [class*="Label"]');
                                    if (innerLbl) {
                                        var ilTxt = extractValidLabelText(innerLbl, false);
                                        if (ilTxt) return ilTxt;
                                    }
                                }
                                cur = cur.parentElement;
                                depth++;
                            }
                        } catch (_) {}
                        return '';
                    }
                    function getNearbyText(el) {
                        if (isSensitiveNode(el)) return '';
                        try {
                            var fgLbl = resolveFieldGroupLabel(el, null);
                            if (fgLbl) return fgLbl.slice(0, 80);
                            var fs = el.closest ? el.closest('fieldset') : null;
                            if (fs) {
                                var lg = fs.querySelector('legend');
                                if (lg && (lg.innerText || lg.textContent)) return String(lg.innerText || lg.textContent).replace(/\s+/g, ' ').trim().slice(0, 80);
                            }
                            var prev = el.previousElementSibling;
                            if (prev && !isSensitiveNode(prev) && (prev.innerText || prev.textContent)) {
                                return String(prev.innerText || prev.textContent).replace(/\s+/g, ' ').trim().slice(0, 80);
                            }
                            var overlay = el.closest ? el.closest('dialog, [role="dialog"], [aria-modal="true"], [data-bottom-sheet], [data-sheet], [data-drawer], [data-calendar], [data-datepicker], .bottom-sheet, .modal, .calendar, .datepicker') : null;
                            if (overlay && overlay.querySelector) {
                                var hd = overlay.querySelector('.sheet-title, .modal-title, .calendar-title, [class*="month-year"], [role="heading"], h1, h2, h3');
                                if (hd && hd !== el && !isSensitiveNode(hd) && (hd.innerText || hd.textContent)) {
                                    return String(hd.innerText || hd.textContent).replace(/\s+/g, ' ').trim().slice(0, 80);
                                }
                            }
                        } catch (_) {}
                        return '';
                    }
                    function getSafeAttributes(el) {
                        var out = {};
                        var sensitive = isSensitiveNode(el);
                        var allowed = [
                            'type', 'role', 'name', 'href', 'placeholder', 'autocomplete', 'inputmode', 'title', 'for',
                            'aria-label', 'aria-labelledby', 'aria-haspopup', 'aria-expanded', 'aria-controls',
                            'aria-pressed', 'aria-selected', 'aria-checked', 'aria-current',
                            'data-testid', 'data-test', 'data-qa', 'data-cy', 'data-action', 'data-cta', 'data-signup',
                            'data-href', 'data-url', 'data-link', 'data-navigate',
                            'data-date', 'data-selected-date', 'data-day', 'data-month', 'data-year',
                            'data-time', 'data-slot', 'data-datepicker', 'data-timepicker', 'data-calendar',
                            'data-bottom-sheet', 'data-sheet', 'data-drawer', 'data-year-picker', 'data-birth-year',
                            'data-dial-code', 'data-country-code', 'data-code', 'data-gender', 'data-value', 'data-option',
                            'data-tab', 'data-index', 'data-key', 'data-dismiss', 'data-picker-role'
                        ];
                        for (var i = 0; i < allowed.length; i++) {
                            var k = allowed[i];
                            if (sensitive && k === 'placeholder') continue;
                            var v = el.getAttribute && el.getAttribute(k);
                            if (v && String(v).trim()) out[k] = String(v).trim().slice(0, 120);
                        }
                        if (isStableId(el.id)) out.id = String(el.id).trim();
                        return out;
                    }
                    function getLabel(el) {
                        if (!el) return 'Element';
                        var root = (el.getRootNode && el.getRootNode()) || document;
                        var doc = el.ownerDocument || document;
                        var lb = (el.getAttribute && el.getAttribute('aria-labelledby') || '').trim();
                        if (lb) {
                            var refTxt = lb.split(/\s+/).map(function(id) {
                                var ref = (root.getElementById ? root.getElementById(id) : null) || (doc.getElementById ? doc.getElementById(id) : null);
                                return ref ? (ref.innerText || ref.textContent || '').trim() : '';
                            }).filter(Boolean).join(' ');
                            if (refTxt) return refTxt.slice(0, 64);
                        }
                        var aria = (el.getAttribute && el.getAttribute('aria-label') || '').trim();
                        if (aria) return aria;
                        if (el.id) {
                            try {
                                var lbl = (root.querySelector ? root.querySelector('label[for="' + escCss(el.id) + '"]') : null) ||
                                    doc.querySelector('label[for="' + escCss(el.id) + '"]');
                                if (lbl && (lbl.innerText || lbl.textContent)) return String(lbl.innerText || lbl.textContent).trim();
                            } catch (_) {}
                        }
                        var parentLbl = el.closest ? el.closest('label') : null;
                        if (parentLbl && (parentLbl.innerText || parentLbl.textContent)) return String(parentLbl.innerText || parentLbl.textContent).trim();
                        var fgLabel = resolveFieldGroupLabel(el, root);
                        if (fgLabel) return fgLabel.slice(0, 64);
                        var titleAttr = (el.getAttribute && el.getAttribute('title') || '').trim();
                        if (titleAttr) return titleAttr;
                        var ph = (el.getAttribute && el.getAttribute('placeholder') || '').trim();
                        if (ph) return ph;
                        var name = (el.getAttribute && el.getAttribute('name') || '').trim();
                        var type = (el.getAttribute && el.getAttribute('type') || '').toLowerCase();
                        var rawText = type === 'password' ? '' : (el.innerText || el.textContent || ((type === 'submit' || type === 'button' || type === 'reset') ? (el.value || '') : ''));
                        var txt = String(rawText).replace(/\s+/g, ' ').trim();
                        if (txt) return txt.slice(0, 64);
                        var altAttr = (el.getAttribute && el.getAttribute('alt') || '').trim();
                        if (altAttr) return altAttr.slice(0, 64);
                        if (el.querySelector) {
                            var imgEl = el.querySelector('img[alt]');
                            var imgAlt = (imgEl && imgEl.getAttribute && imgEl.getAttribute('alt') || '').trim();
                            if (imgAlt) return imgAlt.slice(0, 64);
                            var descAria = el.querySelector('[aria-label]');
                            var descAriaTxt = (descAria && descAria.getAttribute && descAria.getAttribute('aria-label') || '').trim();
                            if (descAriaTxt) return descAriaTxt.slice(0, 64);
                            var svgTitle = el.querySelector('svg title');
                            var svgTitleTxt = (svgTitle && (svgTitle.textContent || '') || '').trim();
                            if (svgTitleTxt) return svgTitleTxt.slice(0, 64);
                        }
                        if (name) return name;
                        return (el.tagName || 'Element').toLowerCase();
                    }
                    function isNodeVisible(node) {
                        try {
                            var cur = node;
                            var d = 0;
                            var vw = window.innerWidth || 1080;
                            var vh = window.innerHeight || 1920;
                            while (cur && cur.nodeType === 1 && d < 25) {
                                var cTag = (cur.tagName || '').toLowerCase();
                                var cType = (cur.getAttribute && cur.getAttribute('type') || '').toLowerCase();
                                if (cType === 'hidden' || (cur.hasAttribute && (cur.hasAttribute('hidden') || cur.hasAttribute('inert')))) return false;
                                if (String(cur.getAttribute && cur.getAttribute('aria-hidden') || '').toLowerCase() === 'true') return false;
                                var dState = String(cur.getAttribute && cur.getAttribute('data-state') || '').toLowerCase();
                                var dOpen = String(cur.getAttribute && cur.getAttribute('data-open') || '').toLowerCase();
                                var dVis = String(cur.getAttribute && cur.getAttribute('data-visible') || '').toLowerCase();
                                var cCls = String(cur.className || '').toLowerCase();
                                if (cTag === 'dialog' && !cur.hasAttribute('open') && dState !== 'open' && dOpen !== 'true') return false;
                                if (isSheetOrOverlayNode(cur) && (dState === 'closed' || dOpen === 'false' || dVis === 'false' || /(?:^|\s)(closed|is-closed|sheet-closed|drawer-closed)(?:\s|$)/.test(cCls))) return false;
                                var win = (cur.ownerDocument && cur.ownerDocument.defaultView) || window;
                                var st = win.getComputedStyle ? win.getComputedStyle(cur) : null;
                                if (st) {
                                    if (st.display === 'none' || st.visibility === 'hidden' || (st.opacity !== '' && parseFloat(st.opacity) === 0)) return false;
                                    if (isSheetOrOverlayNode(cur) && (st.position === 'fixed' || st.position === 'absolute') && !cur.hasAttribute('data-offscreen')) {
                                        var cr = cur.getBoundingClientRect ? cur.getBoundingClientRect() : null;
                                        if (cr && (cr.width === 0 || cr.height === 0 || cr.top >= vh - 1 || cr.bottom <= 1 || cr.left >= vw - 1 || cr.right <= 1)) return false;
                                    }
                                }
                                var rootNode = cur.getRootNode ? cur.getRootNode() : null;
                                cur = cur.parentElement || (rootNode && rootNode.host ? rootNode.host : null);
                                d++;
                            }
                            return true;
                        } catch (_) { return true; }
                    }
                    function buildUniqueSelectorForNode(node, root, prefix) {
                        if (!node || !node.tagName) return '';
                        var nTag = node.tagName.toLowerCase();
                        function isUniq(sel) {
                            try { return root.querySelectorAll(sel).length === 1; } catch (_) { return false; }
                        }
                        var direct = [];
                        if (isStableId(node.id)) direct.push('#' + escCss(node.id));
                        [
                            'data-testid', 'data-test', 'data-qa', 'data-cy', 'data-action',
                            'data-date', 'data-time', 'data-slot', 'data-country-code', 'data-dial-code', 'data-code',
                            'data-day', 'data-month', 'data-year', 'data-gender', 'data-value', 'data-option',
                            'data-tab', 'data-index', 'data-key', 'data-dismiss'
                        ].forEach(function(a) {
                            var v = node.getAttribute && node.getAttribute(a);
                            if (v) direct.push(nTag + '[' + a + '="' + escAttr(v) + '"]');
                        });
                        if (nTag === 'label') {
                            var nFor = node.getAttribute && node.getAttribute('for');
                            if (nFor) direct.push('label[for="' + escAttr(nFor) + '"]');
                        }
                        var nRole = (node.getAttribute && node.getAttribute('role') || '').toLowerCase();
                        var nAria = node.getAttribute && node.getAttribute('aria-label');
                        if (nAria) {
                            if (nRole) direct.push(nTag + '[role="' + escAttr(nRole) + '"][aria-label="' + escAttr(nAria) + '"]');
                            direct.push(nTag + '[aria-label="' + escAttr(nAria) + '"]');
                        }
                        var nName = node.getAttribute && node.getAttribute('name');
                        if (nName) direct.push(nTag + '[name="' + escAttr(nName) + '"]');
                        var nHref = (nTag === 'a' && node.getAttribute) ? (node.getAttribute('href') || '').trim() : '';
                        if (nHref && nHref !== '#' && nHref.indexOf('javascript:') !== 0) {
                            direct.push('a[href="' + escAttr(nHref) + '"]');
                        }
                        var nPh = node.getAttribute && node.getAttribute('placeholder');
                        if (nPh) direct.push(nTag + '[placeholder="' + escAttr(nPh) + '"]');
                        for (var di = 0; di < direct.length; di++) {
                            if (isUniq(direct[di])) return prefix + direct[di];
                        }
                        var aNode = node.parentElement;
                        var aStep = 0;
                        while (aNode && aNode.nodeType === 1 && aStep < 6) {
                            var aTag = (aNode.tagName || '').toLowerCase();
                            var aRole = (aNode.getAttribute && aNode.getAttribute('role') || '').toLowerCase();
                            var aSel = '';
                            if (isStableId(aNode.id)) aSel = aTag + '#' + escCss(aNode.id);
                            else if (aNode.getAttribute && aNode.getAttribute('data-testid')) aSel = aTag + '[data-testid="' + escAttr(aNode.getAttribute('data-testid')) + '"]';
                            else if (aNode.hasAttribute && aNode.hasAttribute('data-bottom-sheet')) aSel = aTag + '[data-bottom-sheet]';
                            else if (aNode.hasAttribute && aNode.hasAttribute('data-sheet')) aSel = aTag + '[data-sheet]';
                            else if (aNode.hasAttribute && aNode.hasAttribute('data-drawer')) aSel = aTag + '[data-drawer]';
                            else if (aNode.hasAttribute && aNode.hasAttribute('data-datepicker')) aSel = aTag + '[data-datepicker]';
                            else if (aNode.hasAttribute && aNode.hasAttribute('data-calendar')) aSel = aTag + '[data-calendar]';
                            else if (aNode.hasAttribute && aNode.hasAttribute('aria-modal') && aNode.getAttribute('aria-modal') === 'true') aSel = aTag + '[aria-modal="true"]';
                            else if (aRole === 'dialog' || aRole === 'alertdialog' || aRole === 'listbox' || aRole === 'menu' || aRole === 'radiogroup' || aRole === 'grid' || aRole === 'application') aSel = aTag + '[role="' + escAttr(aRole) + '"]';
                            else if (aTag === 'form' && aNode.getAttribute && aNode.getAttribute('name')) aSel = 'form[name="' + escAttr(aNode.getAttribute('name')) + '"]';
                            else if (aTag === 'dialog') aSel = aNode.hasAttribute('open') ? 'dialog[open]' : 'dialog';
                            else if (aNode.classList && aNode.classList.contains('bottom-sheet')) aSel = aTag + '.bottom-sheet';
                            else if (aNode.classList && aNode.classList.contains('modal')) aSel = aTag + '.modal';
                            else if (aTag === 'form' || aTag === 'header' || aTag === 'nav' || aTag === 'footer' || aTag === 'main' || aTag === 'aside') aSel = aTag;
                            if (aSel) {
                                for (var ci = 0; ci < direct.length; ci++) {
                                    if (isUniq(aSel + ' ' + direct[ci])) return prefix + aSel + ' ' + direct[ci];
                                }
                            }
                            aNode = aNode.parentElement;
                            aStep++;
                        }
                        var segs = [];
                        var curNode = node;
                        for (var lv = 0; curNode && curNode.nodeType === 1 && lv < 8; lv++) {
                            var ct = (curNode.tagName || '').toLowerCase();
                            if (!ct || ct === 'html' || ct === 'body') break;
                            var seg = ct;
                            var anchored = false;
                            if (isStableId(curNode.id)) {
                                seg = ct + '#' + escCss(curNode.id);
                                if (isUniq([seg].concat(segs).join(' > '))) {
                                    segs.unshift(seg);
                                    return prefix + segs.join(' > ');
                                }
                                anchored = true;
                            }
                            if (!anchored) {
                                var pEl = curNode.parentElement;
                                if (pEl) {
                                    var sameT = Array.from(pEl.children).filter(function(ch) { return ch.tagName === curNode.tagName; });
                                    if (sameT.length > 1) seg += ':nth-of-type(' + (sameT.indexOf(curNode) + 1) + ')';
                                }
                            }
                            segs.unshift(seg);
                            if (isUniq(segs.join(' > '))) return prefix + segs.join(' > ');
                            curNode = curNode.parentElement;
                        }
                        return prefix + (segs.join(' > ') || nTag);
                    }
                    function buildLocatorBundle(el) {
                        if (!el || !el.tagName) return { selector: '', candidates: [], matchCount: 0, contexts: { framePath: [], shadowHostPath: [] }, parentContext: '', nearbyText: '', relevantAttributes: {}, ambiguousCandidates: [] };
                        var root = (el.getRootNode && el.getRootNode()) || document;
                        var contexts = getContexts(el);
                        var prefix = contexts.shadowHostPath.length > 0 ? (contexts.shadowHostPath.join(' >>> ') + ' >>> ') :
                            (contexts.framePath.length > 0 ? (contexts.framePath.join(' >> ') + ' >> ') : '');
                        var scopeName = contexts.shadowHostPath.length > 0 ? 'SHADOW' : (contexts.framePath.length > 0 ? 'IFRAME' : 'DOCUMENT');
                        var tag = el.tagName.toLowerCase();
                        var role = (el.getAttribute && el.getAttribute('role') || '').toLowerCase();
                        var rawCands = [];
                        var seen = {};
                        function pushCand(strategy, localSel, baseConf, stability) {
                            if (!localSel) return;
                            var fullSel = prefix + localSel;
                            if (seen[fullSel]) return;
                            seen[fullSel] = true;
                            var cnt = 0;
                            try { cnt = root.querySelectorAll(localSel).length; } catch (_) { cnt = 0; }
                            if (cnt <= 0) return;
                            var uniq = cnt === 1;
                            var effStrategy = contexts.shadowHostPath.length > 0 ? 'shadow_dom_chain' : (contexts.framePath.length > 0 ? 'iframe_chain' : strategy);
                            var conf = uniq ? baseConf : Math.max(0.30, baseConf - 0.35);
                            rawCands.push({
                                strategy: effStrategy,
                                selector: fullSel,
                                scope: scopeName,
                                confidence: conf,
                                isUnique: uniq,
                                matchCount: cnt,
                                isAmbiguous: !uniq,
                                stability: stability
                            });
                        }
                        if (isStableId(el.id)) pushCand('stable_id', '#' + escCss(el.id), 0.99, 'HIGH');
                        ['data-testid', 'data-test', 'data-qa', 'data-cy', 'data-action'].forEach(function(a) {
                            var v = el.getAttribute && el.getAttribute(a);
                            if (v) pushCand('stable_data_attr', tag + '[' + a + '="' + escAttr(v) + '"]', 0.95, 'HIGH');
                        });
                        [
                            'data-date', 'data-time', 'data-slot', 'data-country-code', 'data-dial-code', 'data-code',
                            'data-day', 'data-month', 'data-year', 'data-gender', 'data-value', 'data-option',
                            'data-tab', 'data-index', 'data-key', 'data-dismiss'
                        ].forEach(function(a) {
                            var v = el.getAttribute && el.getAttribute(a);
                            if (v) pushCand('stable_data_attr', tag + '[' + a + '="' + escAttr(v) + '"]', 0.93, 'HIGH');
                        });
                        if (tag === 'label') {
                            var labelForAttr = el.getAttribute && el.getAttribute('for');
                            if (labelForAttr) {
                                pushCand('label_for', 'label[for="' + escAttr(labelForAttr) + '"]', 0.90, 'HIGH');
                            }
                        }
                        var aria = el.getAttribute && el.getAttribute('aria-label');
                        if (aria) {
                            if (role) pushCand('aria_role_and_name', tag + '[role="' + escAttr(role) + '"][aria-label="' + escAttr(aria) + '"]', 0.94, 'HIGH');
                            pushCand('aria_role_and_name', tag + '[aria-label="' + escAttr(aria) + '"]', 0.91, 'HIGH');
                        }
                        if (isStableId(el.id)) {
                            try {
                                if (root.querySelector('label[for="' + escCss(el.id) + '"]')) {
                                    pushCand('label_for', tag + '#' + escCss(el.id), 0.90, 'HIGH');
                                }
                            } catch (_) {}
                        }
                        var name = el.getAttribute && el.getAttribute('name');
                        if (name) pushCand('stable_name', tag + '[name="' + escAttr(name) + '"]', 0.89, 'HIGH');
                        var href = (tag === 'a' && el.getAttribute) ? (el.getAttribute('href') || '').trim() : '';
                        if (href && href !== '#' && href.indexOf('javascript:') !== 0) {
                            pushCand('href_attr', 'a[href="' + escAttr(href) + '"]', 0.88, 'HIGH');
                        }
                        var ph = el.getAttribute && el.getAttribute('placeholder');
                        if (ph) pushCand('placeholder_attr', tag + '[placeholder="' + escAttr(ph) + '"]', 0.82, 'MEDIUM');

                        // Form or semantic / dialog / sheet / calendar / radiogroup / grid container scoped locators
                        var anc = el.parentElement;
                        var aDepth = 0;
                        while (anc && anc.nodeType === 1 && aDepth < 6) {
                            var at = (anc.tagName || '').toLowerCase();
                            var aRoleAttr = (anc.getAttribute && anc.getAttribute('role') || '').toLowerCase();
                            var cSel = '';
                            if (isStableId(anc.id)) cSel = at + '#' + escCss(anc.id);
                            else if (anc.getAttribute && anc.getAttribute('data-testid')) cSel = at + '[data-testid="' + escAttr(anc.getAttribute('data-testid')) + '"]';
                            else if (anc.hasAttribute && anc.hasAttribute('data-bottom-sheet')) cSel = at + '[data-bottom-sheet]';
                            else if (anc.hasAttribute && anc.hasAttribute('data-sheet')) cSel = at + '[data-sheet]';
                            else if (anc.hasAttribute && anc.hasAttribute('data-drawer')) cSel = at + '[data-drawer]';
                            else if (anc.hasAttribute && anc.hasAttribute('data-datepicker')) cSel = at + '[data-datepicker]';
                            else if (anc.hasAttribute && anc.hasAttribute('data-calendar')) cSel = at + '[data-calendar]';
                            else if (anc.hasAttribute && anc.hasAttribute('aria-modal') && anc.getAttribute('aria-modal') === 'true') cSel = at + '[aria-modal="true"]';
                            else if (aRoleAttr === 'dialog' || aRoleAttr === 'alertdialog' || aRoleAttr === 'listbox' || aRoleAttr === 'menu' || aRoleAttr === 'radiogroup' || aRoleAttr === 'grid' || aRoleAttr === 'application') cSel = at + '[role="' + escAttr(aRoleAttr) + '"]';
                            else if (at === 'form' && anc.getAttribute && anc.getAttribute('name')) cSel = 'form[name="' + escAttr(anc.getAttribute('name')) + '"]';
                            else if (at === 'dialog') cSel = anc.hasAttribute('open') ? 'dialog[open]' : 'dialog';
                            else if (anc.classList && anc.classList.contains('bottom-sheet')) cSel = at + '.bottom-sheet';
                            else if (anc.classList && anc.classList.contains('modal')) cSel = at + '.modal';
                            else if (at === 'form' || at === 'header' || at === 'nav' || at === 'footer' || at === 'main' || at === 'aside') cSel = at;
                            if (cSel) {
                                var strat = (at === 'form') ? 'form_relative' : 'container_scoped';
                                var baseConf = (at === 'form') ? 0.85 : 0.78;
                                if (isStableId(el.id)) pushCand(strat, cSel + ' #' + escCss(el.id), baseConf + 0.03, 'HIGH');
                                if (href && href !== '#') pushCand(strat, cSel + ' a[href="' + escAttr(href) + '"]', baseConf, 'HIGH');
                                if (name) pushCand(strat, cSel + ' ' + tag + '[name="' + escAttr(name) + '"]', baseConf, 'HIGH');
                                if (aria) pushCand(strat, cSel + ' ' + tag + '[aria-label="' + escAttr(aria) + '"]', baseConf, 'HIGH');
                                ['data-date', 'data-time', 'data-slot', 'data-country-code', 'data-dial-code', 'data-code', 'data-day', 'data-month', 'data-year', 'data-gender', 'data-value'].forEach(function(wa) {
                                    var wv = el.getAttribute && el.getAttribute(wa);
                                    if (wv) pushCand(strat, cSel + ' ' + tag + '[' + wa + '="' + escAttr(wv) + '"]', baseConf + 0.02, 'HIGH');
                                });
                                var typeAttr = el.getAttribute && el.getAttribute('type');
                                if (typeAttr) pushCand(strat, cSel + ' ' + tag + '[type="' + escAttr(typeAttr) + '"]', baseConf - 0.05, 'MEDIUM');
                                else pushCand(strat, cSel + ' ' + tag, baseConf - 0.08, 'MEDIUM');
                            }
                            anc = anc.parentElement;
                            aDepth++;
                        }

                        // Structural path up to 8 levels (only break when uniquely matching)
                        var parts = [];
                        var cur = el;
                        for (var depth = 0; cur && cur.nodeType === 1 && depth < 8; depth++) {
                            var pt = cur.tagName.toLowerCase();
                            if (pt === 'html' || pt === 'body') break;
                            var anchored = false;
                            if (isStableId(cur.id)) {
                                pt = pt + '#' + escCss(cur.id);
                                if (root.querySelectorAll([pt].concat(parts).join(' > ')).length === 1) {
                                    parts.unshift(pt);
                                    pushCand('scoped_css', parts.join(' > '), 0.68, 'MEDIUM');
                                    break;
                                }
                                anchored = true;
                            }
                            if (!anchored) {
                                var parent = cur.parentElement;
                                if (parent) {
                                    var sameTag = Array.from(parent.children).filter(function(c) { return c.tagName === cur.tagName; });
                                    if (sameTag.length > 1) pt += ':nth-of-type(' + (sameTag.indexOf(cur) + 1) + ')';
                                }
                            }
                            parts.unshift(pt);
                            var cand = parts.join(' > ');
                            try {
                                if (root.querySelectorAll(cand).length === 1) {
                                    pushCand('scoped_css', cand, 0.64, 'MEDIUM');
                                    break;
                                }
                            } catch (_) {}
                            cur = cur.parentElement;
                        }
                        if (rawCands.length === 0) {
                            var fallbackType = el.getAttribute && el.getAttribute('type');
                            pushCand('scoped_css', fallbackType ? (tag + '[type="' + escAttr(fallbackType) + '"]') : tag, 0.55, 'LOW');
                        }
                        rawCands.sort(function(a, b) {
                            if (a.isUnique !== b.isUnique) return a.isUnique ? -1 : 1;
                            return b.confidence - a.confidence;
                        });
                        var top = rawCands[0] || { selector: tag, matchCount: 1, isAmbiguous: false };
                        var ambigList = [];
                        if (top.matchCount > 1) {
                            try {
                                var localTop = prefix && top.selector.indexOf(prefix) === 0 ? top.selector.slice(prefix.length) : top.selector;
                                var found = Array.from(root.querySelectorAll(localTop)).slice(0, 8);
                                for (var m = 0; m < found.length; m++) {
                                    var f = found[m];
                                    var fRole = (f.getAttribute && f.getAttribute('role') || '').toLowerCase() || (f.tagName.toLowerCase() === 'a' ? 'link' : f.tagName.toLowerCase());
                                    var fSensitive = isSensitiveNode(f);
                                    ambigList.push({
                                        index: m,
                                        tagName: f.tagName.toLowerCase(),
                                        role: fRole,
                                        accessibleName: fSensitive ? ((f.getAttribute && (f.getAttribute('aria-label') || f.getAttribute('name'))) || f.tagName.toLowerCase()) : getLabel(f),
                                        visibleText: fSensitive ? '' : String(f.innerText || f.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 80),
                                        attributes: getSafeAttributes(f),
                                        parentContext: getParentContext(f),
                                        nearbyText: fSensitive ? '' : getNearbyText(f),
                                        framePath: contexts.framePath,
                                        shadowHostPath: contexts.shadowHostPath,
                                        isVisible: isNodeVisible(f),
                                        isEnabled: !Boolean(f.disabled),
                                        recommendedSelector: buildUniqueSelectorForNode(f, root, prefix) || top.selector
                                    });
                                }
                            } catch (_) {}
                        }
                        return {
                            selector: top.selector || '',
                            candidates: rawCands.slice(0, 6),
                            matchCount: top.matchCount || 1,
                            contexts: contexts,
                            parentContext: getParentContext(el),
                            nearbyText: getNearbyText(el),
                            relevantAttributes: getSafeAttributes(el),
                            ambiguousCandidates: ambigList
                        };
                    }

                    function getNodeRect(el) {
                        try {
                            if (!el || typeof el.getBoundingClientRect !== 'function') return null;
                            var r = el.getBoundingClientRect();
                            var offsetX = 0;
                            var offsetY = 0;
                            var curWin = (el.ownerDocument && el.ownerDocument.defaultView) || window;
                            var guard = 0;
                            while (curWin && curWin !== window && curWin.frameElement && guard < 6) {
                                var fr = curWin.frameElement.getBoundingClientRect();
                                offsetX += fr.left;
                                offsetY += fr.top;
                                curWin = curWin.parent;
                                guard++;
                            }
                            var vw = Math.max(1, window.innerWidth || (document.documentElement && document.documentElement.clientWidth) || 360);
                            var vh = Math.max(1, window.innerHeight || (document.documentElement && document.documentElement.clientHeight) || 640);
                            return {
                                left: Math.round((r.left + offsetX) * 10) / 10,
                                top: Math.round((r.top + offsetY) * 10) / 10,
                                width: Math.round(r.width * 10) / 10,
                                height: Math.round(r.height * 10) / 10,
                                viewportWidth: vw,
                                viewportHeight: vh
                            };
                        } catch (_) {
                            return null;
                        }
                    }

                    function highlightRecordedNode(el) {
                        try {
                            if (!el || !el.style) return;
                            var prevOutline = el.getAttribute('data-apex-orig-outline');
                            if (prevOutline === null) {
                                el.setAttribute('data-apex-orig-outline', el.style.outline || '');
                                el.setAttribute('data-apex-orig-outline-offset', el.style.outlineOffset || '');
                                el.setAttribute('data-apex-orig-box-shadow', el.style.boxShadow || '');
                            }
                            el.style.outline = '2.5px solid #00E5FF';
                            el.style.outlineOffset = '2px';
                            el.style.boxShadow = '0 0 0 4px rgba(0, 229, 255, 0.24), 0 0 14px rgba(0, 229, 255, 0.42)';
                            if (el.__apexRecHighlightTimer) {
                                clearTimeout(el.__apexRecHighlightTimer);
                            }
                            el.__apexRecHighlightTimer = setTimeout(function() {
                                try {
                                    el.style.outline = el.getAttribute('data-apex-orig-outline') || '';
                                    el.style.outlineOffset = el.getAttribute('data-apex-orig-outline-offset') || '';
                                    el.style.boxShadow = el.getAttribute('data-apex-orig-box-shadow') || '';
                                    el.removeAttribute('data-apex-orig-outline');
                                    el.removeAttribute('data-apex-orig-outline-offset');
                                    el.removeAttribute('data-apex-orig-box-shadow');
                                } catch (_) {}
                            }, 1400);
                        } catch (_) {}
                    }

                    var initPageScrollX = window.scrollX || window.pageXOffset || 0;
                    var initPageScrollY = window.scrollY || window.pageYOffset || 0;

                    var inst = {
                        sessionId: $qSessionId,
                        tabId: $qTabId,
                        expectedOrigin: expectedOrigin,
                        seq: $safeInitialSeq,
                        active: true,
                        destroyed: false,
                        lastDomEvent: null,
                        lastFocusKey: '',
                        lastFocusEl: null,
                        lastFocusAt: 0,
                        lastToggleEl: null,
                        lastToggleAt: 0,
                        lastSubmitAt: 0,
                        suppressClickUntil: 0,
                        pendingInput: null,
                        inputTimer: null,
                        lastEmittedInput: null,
                        pendingScroll: null,
                        scrollTimer: null,
                        lastKnownPageScroll: { x: initPageScrollX, y: initPageScrollY },
                        pressState: null,
                        holdTimer: null,
                        tapTimer: null,
                        pendingTap: null,
                        lastTapEmitted: null,
                        subRootCleanups: [],
                        extraObservers: [],
                        onFocusIn: null,
                        onFocusOut: null,
                        onInput: null,
                        onChange: null,
                        onClick: null,
                        onDblClick: null,
                        onContextMenu: null,
                        onKeyDown: null,
                        onScroll: null,
                        onSubmit: null,
                        onPointerDown: null,
                        onPointerMove: null,
                        onPointerUp: null,
                        onTouchStart: null,
                        onTouchMove: null,
                        onTouchEnd: null,
                        onMouseDown: null,
                        onMouseUp: null,
                        cleanup: function() {
                            inst.destroyed = true;
                            inst.active = false;
                            if (inst.inputTimer) {
                                try { clearTimeout(inst.inputTimer); } catch (_) {}
                                inst.inputTimer = null;
                            }
                            if (inst.scrollTimer) {
                                try { clearTimeout(inst.scrollTimer); } catch (_) {}
                                inst.scrollTimer = null;
                            }
                            if (inst.holdTimer) {
                                try { clearTimeout(inst.holdTimer); } catch (_) {}
                                inst.holdTimer = null;
                            }
                            if (inst.tapTimer) {
                                try { clearTimeout(inst.tapTimer); } catch (_) {}
                                inst.tapTimer = null;
                            }
                            inst.pendingInput = null;
                            inst.pendingScroll = null;
                            inst.pressState = null;
                            inst.pendingTap = null;
                            inst.lastTapEmitted = null;
                            for (var oi = 0; oi < inst.extraObservers.length; oi++) {
                                try { inst.extraObservers[oi].disconnect(); } catch (_) {}
                            }
                            inst.extraObservers = [];
                            for (var si = 0; si < inst.subRootCleanups.length; si++) {
                                try { inst.subRootCleanups[si](); } catch (_) {}
                            }
                            inst.subRootCleanups = [];
                            try {
                                if (inst.onFocusIn) document.removeEventListener('focusin', inst.onFocusIn, true);
                                if (inst.onFocusOut) document.removeEventListener('focusout', inst.onFocusOut, true);
                                if (inst.onInput) document.removeEventListener('input', inst.onInput, true);
                                if (inst.onChange) document.removeEventListener('change', inst.onChange, true);
                                if (inst.onClick) document.removeEventListener('click', inst.onClick, true);
                                if (inst.onDblClick) document.removeEventListener('dblclick', inst.onDblClick, true);
                                if (inst.onContextMenu) document.removeEventListener('contextmenu', inst.onContextMenu, true);
                                if (inst.onKeyDown) document.removeEventListener('keydown', inst.onKeyDown, true);
                                if (inst.onScroll) document.removeEventListener('scroll', inst.onScroll, true);
                                if (inst.onSubmit) document.removeEventListener('submit', inst.onSubmit, true);
                                if (inst.onPointerDown) document.removeEventListener('pointerdown', inst.onPointerDown, true);
                                if (inst.onPointerMove) document.removeEventListener('pointermove', inst.onPointerMove, true);
                                if (inst.onPointerUp) {
                                    document.removeEventListener('pointerup', inst.onPointerUp, true);
                                    document.removeEventListener('pointercancel', inst.onPointerUp, true);
                                }
                                if (inst.onTouchStart) document.removeEventListener('touchstart', inst.onTouchStart, true);
                                if (inst.onTouchMove) document.removeEventListener('touchmove', inst.onTouchMove, true);
                                if (inst.onTouchEnd) {
                                    document.removeEventListener('touchend', inst.onTouchEnd, true);
                                    document.removeEventListener('touchcancel', inst.onTouchEnd, true);
                                }
                                if (inst.onMouseDown) document.removeEventListener('mousedown', inst.onMouseDown, true);
                                if (inst.onMouseUp) document.removeEventListener('mouseup', inst.onMouseUp, true);
                            } catch (_) {}
                            if (window.__apexRecorderInstance === inst) {
                                window.__apexRecorderInstance = null;
                                window.__apexRecordingActive = false;
                                window.__apexRecorderInstalled = false;
                            }
                        }
                    };

                    function emit(domEvent, actionType, label, el, value, isDestructive, isPasswordField, vaultSecret) {
                        if (inst.destroyed || !inst.active || window.__apexRecorderInstance !== inst) return;
                        var nowOrigin = getCanonicalOrigin();
                        if (!nowOrigin || nowOrigin !== inst.expectedOrigin) {
                            inst.cleanup();
                            return;
                        }
                        if (domEvent && inst.lastDomEvent === domEvent) return;
                        inst.lastDomEvent = domEvent || null;
                        inst.seq = inst.seq + 1;
                        var isPwd = Boolean(isPasswordField);
                        if (el) highlightRecordedNode(el);
                        var bundle = el
                            ? buildLocatorBundle(el)
                            : { selector: '', candidates: [], matchCount: 1, contexts: { framePath: [], shadowHostPath: [] }, parentContext: '', nearbyText: '', relevantAttributes: {}, ambiguousCandidates: [] };
                        var rectObj = el ? getNodeRect(el) : null;
                        var payload = JSON.stringify({
                            sessionId: inst.sessionId,
                            tabId: inst.tabId,
                            origin: nowOrigin,
                            sequenceNumber: inst.seq,
                            eventType: actionType,
                            targetLabel: label || 'Element',
                            tagName: (el && el.tagName ? el.tagName.toLowerCase() : ''),
                            selector: bundle.selector || '',
                            locatorCandidates: bundle.candidates || [],
                            framePath: bundle.contexts.framePath || [],
                            shadowHostPath: bundle.contexts.shadowHostPath || [],
                            parentContext: bundle.parentContext || '',
                            nearbyText: bundle.nearbyText || '',
                            relevantAttributes: bundle.relevantAttributes || {},
                            matchCount: bundle.matchCount || 1,
                            ambiguousCandidates: bundle.ambiguousCandidates || [],
                            boundingBox: rectObj,
                            value: isPwd ? '' : (value || ''),
                            vaultSecret: isPwd ? String(vaultSecret || '').slice(0, 256) : '',
                            isDestructive: Boolean(isDestructive),
                            isPasswordField: isPwd,
                            fieldRole: isPwd ? 'PASSWORD' : '',
                            timestamp: Date.now()
                        });
                        if (window.ApexAutomationBridge && window.ApexAutomationBridge.onRecordedEventJson) {
                            window.ApexAutomationBridge.onRecordedEventJson(payload);
                        }
                    }

                    function flushPendingInput(domEvent) {
                        if (inst.inputTimer) {
                            try { clearTimeout(inst.inputTimer); } catch (_) {}
                            inst.inputTimer = null;
                        }
                        var pending = inst.pendingInput;
                        inst.pendingInput = null;
                        if (!pending || !pending.el) return;
                        if (inst.lastEmittedInput &&
                            inst.lastEmittedInput.el === pending.el &&
                            inst.lastEmittedInput.value === pending.value &&
                            (inst.lastEmittedInput.secretValue || '') === (pending.secretValue || '')) {
                            return;
                        }
                        var hadPrevText = inst.lastEmittedInput &&
                            inst.lastEmittedInput.el === pending.el &&
                            Boolean(inst.lastEmittedInput.value);
                        inst.lastEmittedInput = {
                            el: pending.el,
                            value: pending.value,
                            secretValue: pending.secretValue || ''
                        };
                        if (!pending.isPwd && pending.value === '' && hadPrevText) {
                            emit(domEvent, 'CLEAR', getLabel(pending.el), pending.el, '', false, false, '');
                        } else {
                            emit(domEvent, 'INPUT', getLabel(pending.el), pending.el, pending.value, false, pending.isPwd, pending.secretValue || '');
                        }
                    }

                    function flushPendingScroll() {
                        if (inst.scrollTimer) {
                            try { clearTimeout(inst.scrollTimer); } catch (_) {}
                            inst.scrollTimer = null;
                        }
                        var ps = inst.pendingScroll;
                        inst.pendingScroll = null;
                        if (!ps) return;
                        var dx = Math.round(ps.curX - ps.startX);
                        var dy = Math.round(ps.curY - ps.startY);
                        if (ps.isPage) {
                            inst.lastKnownPageScroll = { x: ps.curX, y: ps.curY };
                        } else if (ps.el) {
                            ps.el.__apexLastScroll = { x: ps.curX, y: ps.curY };
                        }
                        if (Math.abs(dx) < 6 && Math.abs(dy) < 6) return;
                        var dirParts = [];
                        if (dy > 0) dirParts.push('down');
                        else if (dy < 0) dirParts.push('up');
                        if (dx > 0) dirParts.push('right');
                        else if (dx < 0) dirParts.push('left');
                        var dirLabel = dirParts.join(' & ') || 'down';
                        var dist = Math.max(Math.abs(dy), Math.abs(dx));
                        var targetName = ps.isPage ? 'Page' : getLabel(ps.el);
                        var scrollLabel = targetName + ' ' + dirLabel + ' (' + dist + 'px)';
                        var scrollValue = (dx === 0 && dy !== 0) ? String(dy) : ('x:' + dx + ',y:' + dy);
                        emit(null, 'SCROLL', scrollLabel, ps.isPage ? null : ps.el, scrollValue, false, false);
                    }

                    function cancelPendingTap() {
                        if (inst.tapTimer) {
                            try { clearTimeout(inst.tapTimer); } catch (_) {}
                            inst.tapTimer = null;
                        }
                        inst.pendingTap = null;
                    }

                    function cancelHoldTimer() {
                        if (inst.holdTimer) {
                            try { clearTimeout(inst.holdTimer); } catch (_) {}
                            inst.holdTimer = null;
                        }
                    }

                    function resolveEventEl(e) {
                        if (e && typeof e.composedPath === 'function') {
                            var path = e.composedPath();
                            if (path && path.length > 0 && path[0] && path[0].tagName) return path[0];
                        }
                        return e ? e.target : null;
                    }

                    var widgetSelector = 'button, a, summary, [role="button"], [role="link"], [role="tab"], [role="menuitem"], [role="menuitemradio"], [role="menuitemcheckbox"], [role="option"], [role="combobox"], [role="listbox"], [role="radio"], [role="checkbox"], [role="switch"], [role="gridcell"], [role="treeitem"], [role="slider"], [role="spinbutton"], [role="row"][aria-selected], [aria-haspopup], [aria-pressed], [aria-checked], [aria-selected], [aria-current], [data-action], [data-cta], [data-signup], [data-href], [data-url], [data-link], [data-navigate], [data-date], [data-day], [data-month], [data-year], [data-time], [data-slot], [data-datepicker], [data-timepicker], [data-time-picker], [data-calendar], [data-year-picker], [data-birth-year], [data-dial-code], [data-country-code], [data-country], [data-code], [data-gender], [data-value], [data-option], [data-tab], [data-dismiss], [data-close], [tabindex]:not([tabindex="-1"]), [onclick], input[type="submit"], input[type="button"], input[type="reset"], input[type="image"], label, .btn, .button, .cta, .signup-btn, .sign-up-btn, .calendar-day, .time-slot, .sheet-item, .flatpickr-day, .rdp-day';

                    function resolveTapTarget(rawTarget) {
                        if (!rawTarget || !rawTarget.tagName) return null;
                        var el = rawTarget.closest ? rawTarget.closest(widgetSelector) : null;
                        if (!el) {
                            var cur = rawTarget;
                            var steps = 0;
                            var clickableClsRe = /(?:^|[\s_-])(btn|button|cta|signup|sign-up|register|join|nav-link|menu-item|clickable|action-btn)(?:$|[\s_-])/i;
                            while (cur && cur.nodeType === 1 && steps < 4) {
                                var ct = (cur.tagName || '').toUpperCase();
                                if (ct === 'BODY' || ct === 'HTML' || ct === 'FORM' || ct === 'MAIN') break;
                                var hasNestedCtrl = cur.querySelector && cur.querySelector('button, a, input:not([type="hidden"]), select, textarea, [role="button"], [role="link"], [role="option"]');
                                if (!hasNestedCtrl) {
                                    var curWin = (cur.ownerDocument && cur.ownerDocument.defaultView) || window;
                                    var curSt = curWin.getComputedStyle ? curWin.getComputedStyle(cur) : null;
                                    var curCls = typeof cur.className === 'string' ? cur.className : String(cur.getAttribute && cur.getAttribute('class') || '');
                                    var curTxt = String(cur.innerText || cur.textContent || '').replace(/\s+/g, ' ').trim();
                                    var hasClickableCls = clickableClsRe.test(curCls) || (cur.hasAttribute && (cur.hasAttribute('data-action') || cur.hasAttribute('data-cta') || cur.hasAttribute('data-signup')));
                                    if (((curSt && curSt.cursor === 'pointer') || hasClickableCls) && curTxt.length > 0 && curTxt.length <= 120) {
                                        el = cur;
                                        break;
                                    }
                                }
                                cur = cur.parentElement;
                                steps++;
                            }
                            if (!el) el = rawTarget;
                        }
                        if (!el || !el.tagName) return null;
                        var t = el.tagName.toUpperCase();
                        if (t === 'PATH' || t === 'CIRCLE' || t === 'RECT' || t === 'LINE' || t === 'POLYGON' || t === 'POLYLINE' || t === 'USE' || t === 'G') {
                            var svgBtn = el.closest ? el.closest('button, a, [role="button"], [role="link"], svg') : null;
                            if (svgBtn) el = svgBtn;
                        }
                        if (el.tagName.toUpperCase() === 'HTML') return null;
                        return el;
                    }

                    function isPickerTriggerEl(el) {
                        if (!el || !el.getAttribute) return false;
                        var role = String(el.getAttribute('role') || '').toLowerCase();
                        if (el.hasAttribute('aria-haspopup') || el.hasAttribute('aria-controls') ||
                            el.hasAttribute('data-datepicker') || el.hasAttribute('data-timepicker') ||
                            el.hasAttribute('data-time-picker') || el.hasAttribute('data-calendar') ||
                            el.hasAttribute('data-year-picker') || el.hasAttribute('data-birth-year') ||
                            el.hasAttribute('data-bottom-sheet') || el.hasAttribute('data-sheet')) return true;
                        if (role === 'combobox' || role === 'listbox') return true;
                        if ((el.tagName || '').toUpperCase() === 'INPUT' && Boolean(el.readOnly)) return true;
                        return false;
                    }

                    function describeElementClickTarget(el) {
                        var lbl = getLabel(el);
                        var tag = (el.tagName || '').toUpperCase();
                        if (tag === 'BUTTON' && lbl.toLowerCase().indexOf('button') < 0) return lbl + ' button';
                        return lbl;
                    }

                    function isToggleWidget(el) {
                        if (!el || !el.tagName) return false;
                        var tag = el.tagName.toUpperCase();
                        var type = (el.getAttribute && el.getAttribute('type') || '').toLowerCase();
                        var role = (el.getAttribute && el.getAttribute('role') || '').toLowerCase();
                        if (tag === 'INPUT' && (type === 'checkbox' || type === 'radio')) return true;
                        if (role === 'checkbox' || role === 'switch' || role === 'radio' ||
                            role === 'menuitemradio' || role === 'menuitemcheckbox') return true;
                        if (el.hasAttribute && (el.hasAttribute('aria-checked') || el.hasAttribute('aria-pressed'))) return true;
                        return false;
                    }

                    function readToggleState(el) {
                        if (!el) return '';
                        var tag = (el.tagName || '').toUpperCase();
                        var type = (el.getAttribute && el.getAttribute('type') || '').toLowerCase();
                        if (tag === 'INPUT' && (type === 'checkbox' || type === 'radio')) {
                            return el.checked ? 'checked' : 'unchecked';
                        }
                        var ac = String((el.getAttribute && el.getAttribute('aria-checked')) || '').toLowerCase();
                        if (ac === 'true') return 'checked';
                        if (ac === 'false') return 'unchecked';
                        var ap = String((el.getAttribute && el.getAttribute('aria-pressed')) || '').toLowerCase();
                        if (ap === 'true') return 'checked';
                        if (ap === 'false') return 'unchecked';
                        return '';
                    }

                    function findLabelAssociatedToggle(labelEl) {
                        if (!labelEl || (labelEl.tagName || '').toUpperCase() !== 'LABEL') return null;
                        var assocCtrl = null;
                        var lblFor = labelEl.getAttribute && labelEl.getAttribute('for');
                        var rootNode = (labelEl.getRootNode && labelEl.getRootNode()) || document;
                        if (lblFor) {
                            try { assocCtrl = rootNode.querySelector('#' + escCss(lblFor)) || document.getElementById(lblFor); } catch (_) {}
                        }
                        if (!assocCtrl && labelEl.querySelector) {
                            assocCtrl = labelEl.querySelector('input[type="radio"], input[type="checkbox"]');
                        }
                        if (assocCtrl) {
                            var assocType = (assocCtrl.getAttribute && assocCtrl.getAttribute('type') || '').toLowerCase();
                            if (assocType === 'radio' || assocType === 'checkbox') return assocCtrl;
                        }
                        return null;
                    }

                    function isHiddenToggleLabel(labelEl) {
                        var assocCtrl = findLabelAssociatedToggle(labelEl);
                        return (assocCtrl && !isNodeVisible(assocCtrl)) ? assocCtrl : null;
                    }

                    function beginPress(e, sourceKind) {
                        if (inst.destroyed || !inst.active) return;
                        if (sourceKind === 'touch' && inst.pressState && inst.pressState.sourceKind === 'pointer' && (Date.now() - inst.pressState.startTime) < 80) {
                            return;
                        }
                        if (sourceKind === 'mouse' && inst.pressState && (inst.pressState.sourceKind === 'pointer' || inst.pressState.sourceKind === 'touch') && (Date.now() - inst.pressState.startTime) < 120) {
                            return;
                        }
                        var rawTarget = resolveEventEl(e);
                        var el = resolveTapTarget(rawTarget);
                        if (!el) return;
                        cancelHoldTimer();
                        cancelPendingTap();
                        var cx = 0;
                        var cy = 0;
                        if (e.touches && e.touches[0]) {
                            cx = e.touches[0].clientX || 0;
                            cy = e.touches[0].clientY || 0;
                        } else {
                            cx = e.clientX || 0;
                            cy = e.clientY || 0;
                        }
                        var ps = {
                            el: el,
                            rawTarget: rawTarget,
                            sourceKind: sourceKind,
                            startX: cx,
                            startY: cy,
                            startTime: Date.now(),
                            moved: false,
                            holdTriggered: false
                        };
                        inst.pressState = ps;
                        inst.holdTimer = setTimeout(function() {
                            if (inst.destroyed || !inst.active || inst.pressState !== ps || ps.moved) return;
                            ps.holdTriggered = true;
                            inst.suppressClickUntil = Date.now() + 750;
                            flushPendingScroll();
                            emit(null, 'LONG_PRESS', describeElementClickTarget(ps.el), ps.el, 'hold:450ms', false, false);
                        }, 450);
                    }

                    function movePress(e) {
                        var ps = inst.pressState;
                        if (!ps || ps.moved) return;
                        var cx = 0;
                        var cy = 0;
                        if (e.touches && e.touches[0]) {
                            cx = e.touches[0].clientX || 0;
                            cy = e.touches[0].clientY || 0;
                        } else {
                            cx = e.clientX || 0;
                            cy = e.clientY || 0;
                        }
                        if (Math.abs(cx - ps.startX) > 14 || Math.abs(cy - ps.startY) > 14) {
                            ps.moved = true;
                            cancelHoldTimer();
                            cancelPendingTap();
                        }
                    }

                    function endPress(e, isCancel) {
                        cancelHoldTimer();
                        var ps = inst.pressState;
                        if (!ps) return;
                        if (isCancel || ps.moved || ps.holdTriggered) {
                            inst.pressState = null;
                            return;
                        }
                        var duration = Date.now() - ps.startTime;
                        var tapEl = ps.el;
                        inst.pressState = null;
                        if (!tapEl || !tapEl.tagName) return;
                        var tag = tapEl.tagName.toUpperCase();
                        var type = (tapEl.getAttribute && tapEl.getAttribute('type') || '').toLowerCase();
                        // Touch-only or pointerup-only widgets that swallow click: wait 80ms for click/change/focusin, else record tap
                        if (duration < 450 && tag !== 'INPUT' && tag !== 'TEXTAREA' && tag !== 'SELECT') {
                            cancelPendingTap();
                            inst.pendingTap = { el: tapEl, at: Date.now() };
                            inst.tapTimer = setTimeout(function() {
                                var pt = inst.pendingTap;
                                inst.pendingTap = null;
                                inst.tapTimer = null;
                                if (!pt || inst.destroyed || !inst.active) return;
                                if (Date.now() < inst.suppressClickUntil) return;
                                flushPendingScroll();
                                inst.lastTapEmitted = { el: pt.el, at: Date.now() };
                                var lbl = describeElementClickTarget(pt.el);
                                var destructive = /delete|remove|pay|purchase|checkout|submit|confirm|reset|clear/.test(lbl.toLowerCase());
                                var act = isToggleWidget(pt.el) ? 'TOGGLE' : 'CLICK';
                                var val = act === 'TOGGLE' ? readToggleState(pt.el) : 'tap';
                                emit(null, act, lbl, pt.el, val, destructive, false);
                            }, 80);
                        } else if (duration < 450 && tag === 'INPUT' && (type === 'button' || type === 'submit' || type === 'reset')) {
                            cancelPendingTap();
                            inst.pendingTap = { el: tapEl, at: Date.now() };
                            inst.tapTimer = setTimeout(function() {
                                var pt = inst.pendingTap;
                                inst.pendingTap = null;
                                inst.tapTimer = null;
                                if (!pt || inst.destroyed || !inst.active) return;
                                if (Date.now() < inst.suppressClickUntil) return;
                                flushPendingScroll();
                                inst.lastTapEmitted = { el: pt.el, at: Date.now() };
                                emit(null, type === 'submit' ? 'SUBMIT' : 'CLICK', describeElementClickTarget(pt.el), pt.el, 'tap', type === 'submit', false);
                            }, 80);
                        }
                    }

                    inst.onPointerDown = function(e) { try { beginPress(e, 'pointer'); } catch (_) {} };
                    inst.onPointerMove = function(e) { try { movePress(e); } catch (_) {} };
                    inst.onPointerUp = function(e) { try { endPress(e, e.type === 'pointercancel'); } catch (_) {} };
                    inst.onTouchStart = function(e) { try { beginPress(e, 'touch'); } catch (_) {} };
                    inst.onTouchMove = function(e) { try { movePress(e); } catch (_) {} };
                    inst.onTouchEnd = function(e) { try { endPress(e, e.type === 'touchcancel'); } catch (_) {} };
                    inst.onMouseDown = function(e) { try { if (e.button === 0) beginPress(e, 'mouse'); } catch (_) {} };
                    inst.onMouseUp = function(e) { try { if (e.button === 0) endPress(e, false); } catch (_) {} };

                    inst.onScroll = function(e) {
                        try {
                            if (inst.destroyed || !inst.active) return;
                            cancelHoldTimer();
                            cancelPendingTap();
                            var rawTarget = e ? e.target : document;
                            var isPage = !rawTarget || rawTarget === document || rawTarget === document.documentElement || rawTarget === document.body;
                            var curX = isPage ? (window.scrollX || window.pageXOffset || 0) : (rawTarget.scrollLeft || 0);
                            var curY = isPage ? (window.scrollY || window.pageYOffset || 0) : (rawTarget.scrollTop || 0);
                            if (inst.pendingScroll && ((inst.pendingScroll.isPage !== isPage) || (!isPage && inst.pendingScroll.el !== rawTarget))) {
                                flushPendingScroll();
                            }
                            if (!inst.pendingScroll) {
                                var baseScroll = isPage
                                    ? inst.lastKnownPageScroll
                                    : (rawTarget.__apexLastScroll || { x: curX, y: curY });
                                inst.pendingScroll = {
                                    isPage: isPage,
                                    el: isPage ? null : rawTarget,
                                    startX: baseScroll.x,
                                    startY: baseScroll.y,
                                    curX: curX,
                                    curY: curY
                                };
                            } else {
                                inst.pendingScroll.curX = curX;
                                inst.pendingScroll.curY = curY;
                            }
                            if (inst.scrollTimer) {
                                try { clearTimeout(inst.scrollTimer); } catch (_) {}
                            }
                            inst.scrollTimer = setTimeout(function() {
                                flushPendingScroll();
                            }, 160);
                        } catch (_) {}
                    };

                    inst.onFocusIn = function(e) {
                        try {
                            if (inst.destroyed || !inst.active) return;
                            var el = resolveEventEl(e);
                            if (!el || !el.tagName) return;
                            cancelPendingTap();
                            flushPendingScroll();
                            if (inst.pendingInput && inst.pendingInput.el && inst.pendingInput.el !== el) {
                                flushPendingInput(null);
                            }
                            var tag = el.tagName.toUpperCase();
                            var role = (el.getAttribute && el.getAttribute('role') || '').toLowerCase();
                            if (tag === 'INPUT' && (Boolean(el.readOnly) || isPickerTriggerEl(el))) {
                                return;
                            }
                            var isEditableOrSelect = tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' ||
                                role === 'combobox' || role === 'listbox' || role === 'spinbutton' ||
                                Boolean(el.isContentEditable);
                            if (isEditableOrSelect) {
                                var type = (el.getAttribute && el.getAttribute('type') || '').toLowerCase();
                                if (type !== 'checkbox' && type !== 'radio' && type !== 'submit' && type !== 'button' && type !== 'reset') {
                                    var lbl = getLabel(el) + ' field';
                                    var sel = buildLocatorBundle(el).selector;
                                    var focusKey = lbl + '|' + sel;
                                    inst.lastFocusEl = el;
                                    inst.lastFocusAt = Date.now();
                                    if (inst.lastFocusKey === focusKey) return;
                                    inst.lastFocusKey = focusKey;
                                    var isTextFieldFocus = (tag === 'INPUT' || tag === 'TEXTAREA' || Boolean(el.isContentEditable)) &&
                                        role !== 'combobox' && role !== 'listbox';
                                    if (isTextFieldFocus) {
                                        var isPwdFocus = type === 'password' || isSensitiveNode(el);
                                        emit(e, 'INPUT', getLabel(el), el, '', false, isPwdFocus, '');
                                    } else {
                                        emit(e, 'SELECT', lbl, el, '', false, false);
                                    }
                                }
                            }
                        } catch (_) {}
                    };

                    inst.onInput = function(e) {
                        try {
                            if (inst.destroyed || !inst.active) return;
                            var el = resolveEventEl(e);
                            if (!el || !el.tagName) return;
                            cancelPendingTap();
                            flushPendingScroll();
                            var tag = el.tagName.toUpperCase();
                            var type = (el.getAttribute && el.getAttribute('type') || '').toLowerCase();
                            if (type === 'checkbox' || type === 'radio' || type === 'submit' || type === 'button' || type === 'reset' || type === 'hidden') return;
                            var isEditable = tag === 'INPUT' || tag === 'TEXTAREA' || Boolean(el.isContentEditable);
                            if (!isEditable) return;
                            inst.lastFocusKey = '';
                            highlightRecordedNode(el);
                            var isPwd = type === 'password' || isSensitiveNode(el);
                            var rawVal = (tag === 'INPUT' || tag === 'TEXTAREA')
                                ? String(el.value || '')
                                : String(el.innerText || el.textContent || '');
                            var safeVal = isPwd ? '' : rawVal.slice(0, 256);
                            var rawPwdVal = isPwd ? rawVal.slice(0, 256) : '';
                            inst.pendingInput = { el: el, value: safeVal, isPwd: isPwd, secretValue: rawPwdVal };
                            if (inst.inputTimer) {
                                try { clearTimeout(inst.inputTimer); } catch (_) {}
                            }
                            inst.inputTimer = setTimeout(function() {
                                flushPendingInput(null);
                            }, 220);
                        } catch (_) {}
                    };

                    inst.onFocusOut = function(e) {
                        try {
                            if (inst.destroyed || !inst.active) return;
                            if (inst.pendingInput) {
                                flushPendingInput(null);
                            }
                        } catch (_) {}
                    };

                    inst.onChange = function(e) {
                        try {
                            if (inst.destroyed || !inst.active) return;
                            var el = resolveEventEl(e);
                            if (!el || !el.tagName) return;
                            cancelPendingTap();
                            flushPendingScroll();
                            if (inst.inputTimer) {
                                try { clearTimeout(inst.inputTimer); } catch (_) {}
                                inst.inputTimer = null;
                            }
                            if (inst.pendingInput && inst.pendingInput.el === el) {
                                inst.pendingInput = null;
                            } else if (inst.pendingInput) {
                                flushPendingInput(null);
                            }
                            inst.lastFocusKey = '';
                            var tag = el.tagName.toUpperCase();
                            var type = (el.getAttribute && el.getAttribute('type') || '').toLowerCase();
                            if (type === 'checkbox' || type === 'radio') {
                                inst.lastToggleEl = el;
                                inst.lastToggleAt = Date.now();
                                emit(
                                    e,
                                    'TOGGLE',
                                    getLabel(el) + ' ' + (type === 'checkbox' ? 'checkbox' : 'option'),
                                    el,
                                    el.checked ? 'checked' : 'unchecked',
                                    false,
                                    false
                                );
                            } else if (tag === 'SELECT') {
                                emit(e, 'SELECT', getLabel(el), el, el.value || '', false, false);
                            } else if (tag === 'INPUT' || tag === 'TEXTAREA') {
                                var isPwd = type === 'password' || isSensitiveNode(el);
                                var fullInputVal = String(el.value || '').slice(0, 256);
                                var safeVal = isPwd ? '' : fullInputVal;
                                var secretVal = isPwd ? fullInputVal : '';
                                if (inst.lastEmittedInput &&
                                    inst.lastEmittedInput.el === el &&
                                    inst.lastEmittedInput.value === safeVal &&
                                    (inst.lastEmittedInput.secretValue || '') === secretVal) {
                                    return;
                                }
                                var hadPrevText = inst.lastEmittedInput &&
                                    inst.lastEmittedInput.el === el &&
                                    Boolean(inst.lastEmittedInput.value);
                                inst.lastEmittedInput = { el: el, value: safeVal, secretValue: secretVal };
                                if (!isPwd && safeVal === '' && hadPrevText) {
                                    emit(e, 'CLEAR', getLabel(el), el, '', false, false, '');
                                } else {
                                    emit(e, 'INPUT', getLabel(el), el, safeVal, false, isPwd, secretVal);
                                }
                            }
                        } catch (_) {}
                    };

                    inst.onClick = function(e) {
                        try {
                            if (inst.destroyed || !inst.active) return;
                            cancelPendingTap();
                            flushPendingScroll();
                            if (Date.now() < inst.suppressClickUntil) return;
                            var rawTarget = resolveEventEl(e);
                            if (inst.pendingInput && (!rawTarget || inst.pendingInput.el !== rawTarget)) {
                                flushPendingInput(null);
                            }
                            var el = resolveTapTarget(rawTarget);
                            if (!el || !el.tagName) return;
                            if (inst.lastTapEmitted && (Date.now() - inst.lastTapEmitted.at) < 420 &&
                                (inst.lastTapEmitted.el === el || (el.contains && el.contains(inst.lastTapEmitted.el)) || (inst.lastTapEmitted.el.contains && inst.lastTapEmitted.el.contains(el)))) {
                                inst.lastTapEmitted = null;
                                return;
                            }
                            var tag = el.tagName.toUpperCase();
                            var type = (el.getAttribute && el.getAttribute('type') || '').toLowerCase();
                            var role = (el.getAttribute && el.getAttribute('role') || '').toLowerCase();

                            // Avoid duplicate CLICK if onChange just emitted TOGGLE for this checkbox/radio
                            if (tag === 'INPUT' && (type === 'checkbox' || type === 'radio')) {
                                if (inst.lastToggleEl === el && (Date.now() - inst.lastToggleAt) < 120) return;
                                inst.lastToggleEl = el;
                                inst.lastToggleAt = Date.now();
                                emit(
                                    e,
                                    'TOGGLE',
                                    getLabel(el) + ' ' + (type === 'checkbox' ? 'checkbox' : 'option'),
                                    el,
                                    el.checked ? 'checked' : 'unchecked',
                                    false,
                                    false
                                );
                                return;
                            }

                            // If clicking a <label> tied to a checkbox/radio, wait briefly for native change; only emit if needed
                            if (tag === 'LABEL') {
                                var hiddenToggleCtrl = isHiddenToggleLabel(el);
                                if (hiddenToggleCtrl) {
                                    inst.lastToggleEl = hiddenToggleCtrl;
                                    inst.lastToggleAt = Date.now();
                                    emit(e, 'TOGGLE', getLabel(el), el, hiddenToggleCtrl.checked ? 'checked' : 'unchecked', false, false);
                                    return;
                                }
                                var assocCtrl = findLabelAssociatedToggle(el);
                                if (assocCtrl) {
                                    setTimeout(function() {
                                        if (inst.destroyed || !inst.active) return;
                                        if (inst.lastToggleEl === assocCtrl && (Date.now() - inst.lastToggleAt) < 150) return;
                                        emit(null, 'TOGGLE', getLabel(el), assocCtrl, assocCtrl.checked ? 'checked' : 'unchecked', false, false);
                                    }, 30);
                                    return;
                                }
                            }

                            // Avoid duplicate CLICK right after initial focusin SELECT on editable text/select fields (but allow readonly triggers)
                            var isTextOrSelectField = (tag === 'INPUT' && type !== 'submit' && type !== 'button' && type !== 'reset' && type !== 'range' && type !== 'color') ||
                                tag === 'TEXTAREA' || tag === 'SELECT' || Boolean(el.isContentEditable);
                            if (isTextOrSelectField && !Boolean(el.readOnly) && !isPickerTriggerEl(el) && inst.lastFocusEl === el && (Date.now() - inst.lastFocusAt) < 150) {
                                return;
                            }

                            inst.lastFocusKey = '';
                            var lbl = describeElementClickTarget(el);
                            var lower = lbl.toLowerCase();
                            var destructive = /delete|remove|pay|purchase|checkout|submit|confirm|reset|clear/.test(lower);
                            if (type === 'submit') {
                                inst.lastSubmitAt = Date.now();
                                emit(e, 'SUBMIT', lbl, el, '', destructive, false);
                            } else if (isToggleWidget(el)) {
                                emit(e, 'TOGGLE', lbl, el, readToggleState(el), destructive, false);
                            } else {
                                emit(e, 'CLICK', lbl, el, '', destructive, false);
                            }
                        } catch (_) {}
                    };

                    inst.onDblClick = function(e) {
                        try {
                            if (inst.destroyed || !inst.active) return;
                            cancelPendingTap();
                            flushPendingScroll();
                            var rawTarget = resolveEventEl(e);
                            var el = resolveTapTarget(rawTarget);
                            if (!el) return;
                            emit(e, 'DOUBLE_CLICK', describeElementClickTarget(el), el, '', false, false);
                        } catch (_) {}
                    };

                    inst.onContextMenu = function(e) {
                        try {
                            if (inst.destroyed || !inst.active) return;
                            if (Date.now() < inst.suppressClickUntil) return;
                            cancelPendingTap();
                            cancelHoldTimer();
                            flushPendingScroll();
                            var rawTarget = resolveEventEl(e);
                            var el = resolveTapTarget(rawTarget);
                            if (!el) return;
                            inst.suppressClickUntil = Date.now() + 450;
                            emit(e, 'LONG_PRESS', describeElementClickTarget(el), el, 'contextmenu', false, false);
                        } catch (_) {}
                    };

                    inst.onKeyDown = function(e) {
                        try {
                            if (inst.destroyed || !inst.active || e.repeat) return;
                            var key = String(e.key || '');
                            if (!key || key === 'Shift' || key === 'Control' || key === 'Alt' || key === 'Meta') return;
                            var rawTarget = resolveEventEl(e);
                            var el = (rawTarget && rawTarget.tagName && rawTarget.tagName.toUpperCase() !== 'HTML')
                                ? rawTarget
                                : (document.activeElement || document.body);
                            var tag = (el && el.tagName ? el.tagName.toUpperCase() : '');
                            var type = (el && el.getAttribute && el.getAttribute('type') || '').toLowerCase();
                            var isEditable = tag === 'TEXTAREA' || Boolean(el && el.isContentEditable) ||
                                (tag === 'INPUT' && type !== 'checkbox' && type !== 'radio' && type !== 'submit' && type !== 'button' && type !== 'reset');
                            var hasModifier = Boolean(e.ctrlKey || e.metaKey || e.altKey);
                            var isSpecialKey = key === 'Enter' || key === 'Escape' || key === 'Tab' ||
                                key === 'ArrowUp' || key === 'ArrowDown' || key === 'ArrowLeft' || key === 'ArrowRight' ||
                                key === 'Home' || key === 'End' || key === 'PageUp' || key === 'PageDown' ||
                                ((key === ' ' || key === 'Spacebar') && !isEditable) ||
                                ((key === 'Backspace' || key === 'Delete') && (!isEditable || !String(el.value || '')));
                            if (!hasModifier && !isSpecialKey) return;
                            flushPendingScroll();
                            if (inst.pendingInput && (key === 'Enter' || key === 'Tab' || hasModifier)) {
                                flushPendingInput(null);
                            }
                            var mods = [];
                            if (e.ctrlKey) mods.push('Ctrl');
                            if (e.metaKey) mods.push('Meta');
                            if (e.altKey) mods.push('Alt');
                            var displayKey = (key === ' ' || key === 'Spacebar') ? 'Space' : key;
                            var formattedKey = mods.length > 0 ? (mods.join('+') + '+' + displayKey) : displayKey;
                            var targetLbl = (el && el !== document.body) ? getLabel(el) : 'Page';
                            emit(e, 'KEY_PRESS', targetLbl, el, formattedKey, false, false);
                        } catch (_) {}
                    };

                    inst.onSubmit = function(e) {
                        try {
                            if (inst.destroyed || !inst.active) return;
                            if (Date.now() - inst.lastSubmitAt < 250) return;
                            flushPendingInput(null);
                            flushPendingScroll();
                            var formEl = resolveEventEl(e);
                            if (!formEl || !formEl.tagName) return;
                            inst.lastSubmitAt = Date.now();
                            emit(e, 'SUBMIT', getLabel(formEl), formEl, '', true, false);
                        } catch (_) {}
                    };

                    window.__apexRecorderInstance = inst;
                    window.__apexRecordingActive = true;
                    window.__apexRecorderInstalled = true;
                    document.addEventListener('focusin', inst.onFocusIn, true);
                    document.addEventListener('focusout', inst.onFocusOut, true);
                    document.addEventListener('input', inst.onInput, true);
                    document.addEventListener('change', inst.onChange, true);
                    document.addEventListener('click', inst.onClick, true);
                    document.addEventListener('dblclick', inst.onDblClick, true);
                    document.addEventListener('contextmenu', inst.onContextMenu, true);
                    document.addEventListener('keydown', inst.onKeyDown, true);
                    document.addEventListener('scroll', inst.onScroll, true);
                    document.addEventListener('submit', inst.onSubmit, true);
                    document.addEventListener('pointerdown', inst.onPointerDown, true);
                    document.addEventListener('pointermove', inst.onPointerMove, true);
                    document.addEventListener('pointerup', inst.onPointerUp, true);
                    document.addEventListener('pointercancel', inst.onPointerUp, true);
                    document.addEventListener('touchstart', inst.onTouchStart, true);
                    document.addEventListener('touchmove', inst.onTouchMove, true);
                    document.addEventListener('touchend', inst.onTouchEnd, true);
                    document.addEventListener('touchcancel', inst.onTouchEnd, true);
                    document.addEventListener('mousedown', inst.onMouseDown, true);
                    document.addEventListener('mouseup', inst.onMouseUp, true);

                    var attachedSet = typeof WeakSet !== 'undefined' ? new WeakSet() : null;
                    function attachSubRootListeners(targetRoot, isFrameDoc, depth) {
                        if (!targetRoot || inst.destroyed || depth > 4) return;
                        if (attachedSet) {
                            if (attachedSet.has(targetRoot)) return;
                            attachedSet.add(targetRoot);
                        }
                        var evNames = isFrameDoc
                            ? ['focusin', 'focusout', 'input', 'change', 'click', 'dblclick', 'contextmenu', 'keydown', 'scroll', 'submit', 'pointerdown', 'pointermove', 'pointerup', 'pointercancel', 'touchstart', 'touchmove', 'touchend', 'touchcancel', 'mousedown', 'mouseup']
                            : ['focusin', 'focusout', 'change', 'scroll', 'submit'];
                        var handlerMap = {
                            focusin: inst.onFocusIn, focusout: inst.onFocusOut, input: inst.onInput, change: inst.onChange,
                            click: inst.onClick, dblclick: inst.onDblClick, contextmenu: inst.onContextMenu,
                            keydown: inst.onKeyDown, scroll: inst.onScroll, submit: inst.onSubmit,
                            pointerdown: inst.onPointerDown, pointermove: inst.onPointerMove, pointerup: inst.onPointerUp,
                            pointercancel: inst.onPointerUp, touchstart: inst.onTouchStart, touchmove: inst.onTouchMove,
                            touchend: inst.onTouchEnd, touchcancel: inst.onTouchEnd, mousedown: inst.onMouseDown, mouseup: inst.onMouseUp
                        };
                        evNames.forEach(function(ev) {
                            try { targetRoot.addEventListener(ev, handlerMap[ev], true); } catch (_) {}
                        });
                        inst.subRootCleanups.push(function() {
                            evNames.forEach(function(ev) {
                                try { targetRoot.removeEventListener(ev, handlerMap[ev], true); } catch (_) {}
                            });
                        });
                        scanNestedRoots(targetRoot, depth);
                    }
                    function scanNestedRoots(rootNode, depth) {
                        if (!rootNode || !rootNode.querySelectorAll || depth > 4 || inst.destroyed) return;
                        try {
                            var allNodes = rootNode.querySelectorAll('*');
                            var cap = Math.min(allNodes.length, 400);
                            for (var i = 0; i < cap; i++) {
                                var n = allNodes[i];
                                if (n.shadowRoot) {
                                    attachSubRootListeners(n.shadowRoot, false, depth + 1);
                                }
                                if ((n.tagName || '').toLowerCase() === 'iframe') {
                                    try {
                                        var fDoc = n.contentDocument;
                                        if (fDoc && fDoc.documentElement) {
                                            attachSubRootListeners(fDoc, true, depth + 1);
                                        }
                                        if (!n.__apexFrameHooked) {
                                            n.__apexFrameHooked = true;
                                            var onFrmLoad = function() {
                                                try {
                                                    if (!inst.destroyed && n.contentDocument) {
                                                        attachSubRootListeners(n.contentDocument, true, depth + 1);
                                                    }
                                                } catch (_) {}
                                            };
                                            n.addEventListener('load', onFrmLoad);
                                            inst.subRootCleanups.push(function() {
                                                try { n.removeEventListener('load', onFrmLoad); } catch (_) {}
                                            });
                                        }
                                    } catch (_) {}
                                }
                            }
                        } catch (_) {}
                    }
                    scanNestedRoots(document, 0);
                    if (typeof MutationObserver !== 'undefined' && document.documentElement) {
                        try {
                            var subObs = new MutationObserver(function(muts) {
                                if (inst.destroyed) return;
                                for (var mi = 0; mi < muts.length; mi++) {
                                    var m = muts[mi];
                                    if (m.type === 'childList' && m.addedNodes) {
                                        for (var ai = 0; ai < m.addedNodes.length; ai++) {
                                            var an = m.addedNodes[ai];
                                            if (an && an.nodeType === 1) {
                                                if (an.shadowRoot) attachSubRootListeners(an.shadowRoot, false, 1);
                                                if ((an.tagName || '').toLowerCase() === 'iframe') {
                                                    try {
                                                        if (an.contentDocument) attachSubRootListeners(an.contentDocument, true, 1);
                                                    } catch (_) {}
                                                }
                                                scanNestedRoots(an, 1);
                                            }
                                        }
                                    }
                                }
                            });
                            subObs.observe(document.documentElement, { childList: true, subtree: true });
                            inst.extraObservers.push(subObs);
                        } catch (_) {}
                    }

                    return JSON.stringify({
                        ok: true,
                        sessionId: inst.sessionId,
                        tabId: inst.tabId,
                        origin: actualOrigin
                    });
                } catch (err) {
                    return JSON.stringify({
                        ok: false,
                        reason: 'exception',
                        message: String(err && err.message || err || 'Install error')
                    });
                }
            })();
        """.trimIndent()
    }

    internal fun buildRecorderUninstallScript(expectedSessionId: String? = null): String {
        val qSessionId = JSONObject.quote(expectedSessionId?.trim().orEmpty())
        return """
            (function() {
                try {
                    var targetSession = $qSessionId;
                    var inst = window.__apexRecorderInstance;
                    var removed = false;
                    if (inst) {
                        if (!targetSession || inst.sessionId === targetSession) {
                            if (typeof inst.cleanup === 'function') {
                                inst.cleanup();
                            }
                            window.__apexRecorderInstance = null;
                            window.__apexRecordingActive = false;
                            window.__apexRecorderInstalled = false;
                            removed = true;
                        }
                    } else {
                        window.__apexRecordingActive = false;
                        window.__apexRecorderInstalled = false;
                    }
                    return JSON.stringify({ ok: true, removed: removed });
                } catch (err) {
                    return JSON.stringify({
                        ok: false,
                        reason: 'exception',
                        message: String(err && err.message || err || 'Uninstall error')
                    });
                }
            })();
        """.trimIndent()
    }

    suspend fun installRecorderScript(
        webView: WebView?,
        sessionId: String,
        tabId: String,
        expectedOrigin: String,
        initialSequenceNumber: Long = 0L
    ): RecorderLifecycleResult {
        if (webView == null) {
            return RecorderLifecycleResult(
                success = false,
                errorCode = AutomationErrorCode.RECORDING_DOCUMENT_UNAVAILABLE,
                message = "WebView is unavailable for recording."
            )
        }
        val cleanSessionId = sessionId.trim()
        val cleanTabId = tabId.trim()
        val parsedOrigin = parseOrigin(expectedOrigin)
        if (cleanSessionId.isEmpty() || cleanTabId.isEmpty() || parsedOrigin !is RecordingOriginResult.Valid) {
            return RecorderLifecycleResult(
                success = false,
                errorCode = AutomationErrorCode.RECORDING_DOCUMENT_UNAVAILABLE,
                message = "Invalid recording session or website origin."
            )
        }
        val script = buildRecorderInstallScript(
            sessionId = cleanSessionId,
            tabId = cleanTabId,
            expectedOrigin = parsedOrigin.canonicalOrigin,
            initialSequenceNumber = initialSequenceNumber
        )
        return when (val eval = evalJsWithResult(webView, script)) {
            is JsEvalResult.Success -> {
                val json = parseEvalJsonObject(eval.rawValue)
                if (json == null) {
                    RecorderLifecycleResult(
                        success = false,
                        errorCode = AutomationErrorCode.RECORDING_INSTALL_FAILED,
                        message = "Recorder confirmation response was invalid."
                    )
                } else {
                    val ok = json.optBoolean("ok", false)
                    val confirmedSession = json.optString("sessionId", "")
                    if (ok && confirmedSession == cleanSessionId) {
                        RecorderLifecycleResult(success = true)
                    } else {
                        val reason = json.optString("reason", "")
                        val errCode = if (reason == "origin_mismatch") {
                            AutomationErrorCode.RECORDING_ORIGIN_CHANGED
                        } else {
                            AutomationErrorCode.RECORDING_INSTALL_FAILED
                        }
                        RecorderLifecycleResult(
                            success = false,
                            errorCode = errCode,
                            message = json.optString("message", "").ifBlank {
                                if (reason == "origin_mismatch") {
                                    "Page origin changed before recorder could attach."
                                } else {
                                    "Recorder failed to attach to webpage."
                                }
                            }
                        )
                    }
                }
            }
            is JsEvalResult.Timeout -> RecorderLifecycleResult(
                success = false,
                errorCode = AutomationErrorCode.RECORDING_INSTALL_FAILED,
                message = "Recorder installation timed out."
            )
            is JsEvalResult.Unavailable, is JsEvalResult.Failure -> RecorderLifecycleResult(
                success = false,
                errorCode = AutomationErrorCode.RECORDING_INSTALL_FAILED,
                message = "Recorder installation failed in WebView."
            )
        }
    }

    suspend fun uninstallRecorderScript(
        webView: WebView?,
        sessionId: String? = null
    ): RecorderLifecycleResult {
        if (webView == null) {
            return RecorderLifecycleResult(
                success = false,
                errorCode = AutomationErrorCode.RECORDING_CLEANUP_FAILED,
                message = "WebView unavailable during recorder cleanup."
            )
        }
        val script = buildRecorderUninstallScript(sessionId)
        return when (val eval = evalJsWithResult(webView, script)) {
            is JsEvalResult.Success -> {
                val json = parseEvalJsonObject(eval.rawValue)
                if (json?.optBoolean("ok", false) == true) {
                    RecorderLifecycleResult(success = true)
                } else {
                    RecorderLifecycleResult(
                        success = false,
                        errorCode = AutomationErrorCode.RECORDING_CLEANUP_FAILED,
                        message = "Recorder cleanup returned unexpected response."
                    )
                }
            }
            is JsEvalResult.Timeout, is JsEvalResult.Unavailable, is JsEvalResult.Failure -> {
                RecorderLifecycleResult(
                    success = false,
                    errorCode = AutomationErrorCode.RECORDING_CLEANUP_FAILED,
                    message = "Failed to uninstall recorder script."
                )
            }
        }
    }

    fun detachRecorderBestEffort(webView: WebView?, sessionId: String? = null) {
        if (webView == null) return
        val script = buildRecorderUninstallScript(sessionId)
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runCatching { webView.evaluateJavascript(script, null) }
        } else {
            mainHandler.post {
                runCatching { webView.evaluateJavascript(script, null) }
            }
        }
    }

    internal fun parseEvalJsonObject(rawValue: String): JSONObject? {
        if (rawValue.isBlank() || rawValue == "null") return null
        return runCatching {
            val decoded = JSONTokener(rawValue).nextValue()
            val text = when (decoded) {
                is String -> decoded
                is JSONObject -> return@runCatching decoded
                else -> rawValue
            }
            if (text.isBlank()) null else JSONObject(text)
        }.getOrNull()
    }

    fun parseInstructionsToSteps(
        rawInstructions: String,
        currentUrl: String,
        inspectionState: PageInspectionState? = null
    ): List<AutomationStep> {
        val lines = rawInstructions
            .split(Regex("[\n;]+|\\bthen\\b", RegexOption.IGNORE_CASE))
            .map { it.trim().removePrefix("-").removePrefix("•").replace(Regex("^\\d+[.)]\\s*"), "").trim() }
            .filter { it.isNotBlank() }

        if (lines.isEmpty()) return emptyList()

        return lines.mapIndexed { idx, clause ->
            val lower = clause.lowercase()
            val isDestructive = isClauseDestructive(lower)
            val actionType = when {
                lower.startsWith("go to ") || lower.startsWith("open ") || lower.startsWith("navigate ") || lower.contains("http://") || lower.contains("https://") ->
                    AutomationActionType.NAVIGATE
                lower.startsWith("wait ") || lower.contains("wait until ") || lower.contains("wait for ") ->
                    AutomationActionType.WAIT
                lower.startsWith("clear ") || lower.startsWith("empty ") || lower.contains("clear field") ->
                    AutomationActionType.CLEAR
                lower.startsWith("focus ") || lower.startsWith("focus on ") ->
                    AutomationActionType.FOCUS
                lower.startsWith("blur ") || lower.startsWith("unfocus ") ->
                    AutomationActionType.BLUR
                lower.startsWith("press key ") || lower.startsWith("key press ") || Regex("^press\\s+(enter|return|escape|esc|tab|space|backspace|arrow\\w+)\\b", RegexOption.IGNORE_CASE).containsMatchIn(lower) ->
                    AutomationActionType.KEY_PRESS
                lower.contains("type ") || lower.contains("enter ") || lower.contains("fill ") || lower.contains("input ") || lower.contains("set date") ->
                    AutomationActionType.INPUT
                lower.contains("check ") || lower.contains("uncheck ") || lower.contains("toggle ") || lower.contains("checkbox") ->
                    AutomationActionType.TOGGLE
                lower.contains("select ") || lower.contains("choose ") || lower.contains("dropdown") ->
                    AutomationActionType.SELECT
                lower.contains("scroll") ->
                    AutomationActionType.SCROLL
                lower.contains("extract") || lower.contains("scrape") || lower.contains("collect") || lower.contains("summarize") ->
                    AutomationActionType.EXTRACT
                lower.contains("submit") || lower.contains("confirm") || lower.contains("delete") ->
                    AutomationActionType.SUBMIT
                else -> AutomationActionType.CLICK
            }

            val extractedValue = extractQuotedOrAfterKeyword(clause)
            val rawTargetLabel = deriveTargetLabel(clause, actionType, extractedValue)
            val candidateMatch = if (inspectionState != null &&
                inspectionState.elements.isNotEmpty() &&
                actionType != AutomationActionType.NAVIGATE &&
                actionType != AutomationActionType.SCROLL
            ) {
                universalScanner.matchCandidatesForInstruction(
                    clause = clause,
                    actionType = actionType,
                    targetLabel = rawTargetLabel,
                    inspectionState = inspectionState
                )
            } else {
                null
            }

            val primaryCandidate = candidateMatch?.primaryCandidate
            val resolvedLabel = if (primaryCandidate != null && !candidateMatch.isAmbiguous) {
                primaryCandidate.accessibleName.ifBlank { primaryCandidate.label.ifBlank { rawTargetLabel } }
            } else {
                rawTargetLabel
            }
            val resolvedSelector = when {
                primaryCandidate != null && !candidateMatch.isAmbiguous && primaryCandidate.selector.isNotBlank() ->
                    primaryCandidate.selector
                actionType == AutomationActionType.WAIT -> ""
                else -> inferSelector(actionType, resolvedLabel, lower)
            }

            val isSensitivePassword = primaryCandidate?.sensitiveCategory == SensitiveFieldCategory.PASSWORD ||
                primaryCandidate?.semanticType in setOf(FieldSemanticType.PASSWORD, FieldSemanticType.CONFIRM_PASSWORD) ||
                (actionType == AutomationActionType.INPUT && lower.contains("password"))
            val safeValue = if (isSensitivePassword) "" else extractedValue
            val expectedOrig = if (isSensitivePassword) parseOrigin(currentUrl).canonicalOrigin else ""
            val desiredChecked = if (actionType == AutomationActionType.TOGGLE) {
                when {
                    lower.contains("uncheck") || lower.contains("deselect") || lower.contains("off") -> false
                    lower.contains("check") || lower.contains("select") || lower.contains("enable") -> true
                    else -> null
                }
            } else {
                null
            }
            val parsedKey = if (actionType == AutomationActionType.KEY_PRESS) {
                parseKeyboardKeyFromClause(clause, extractedValue)
            } else {
                ""
            }
            val waitSpec = if (actionType == AutomationActionType.WAIT) {
                parseWaitConditionFromClause(
                    clause = clause,
                    targetLabel = resolvedLabel,
                    selector = if (candidateMatch?.isAmbiguous == false) primaryCandidate?.selector.orEmpty() else "",
                    extractedValue = extractedValue
                )
            } else {
                null
            }

            AutomationStep(
                id = "inst_${UUID.randomUUID()}",
                order = idx + 1,
                actionType = actionType,
                targetLabel = resolvedLabel,
                selector = resolvedSelector,
                value = if (actionType == AutomationActionType.KEY_PRESS && safeValue.isBlank()) parsedKey else safeValue,
                description = clause,
                isDestructive = isDestructive,
                credentialFieldRole = if (isSensitivePassword) CredentialFieldRole.PASSWORD else null,
                expectedOrigin = expectedOrig,
                requiresCredential = isSensitivePassword,
                locatorCandidates = if (candidateMatch?.isAmbiguous == false) {
                    primaryCandidate?.locatorCandidates.orEmpty()
                } else {
                    emptyList()
                },
                controlKindHint = if (candidateMatch?.isAmbiguous == false) primaryCandidate?.controlKind else null,
                dropdownKindHint = if (candidateMatch?.isAmbiguous == false) primaryCandidate?.dropdownKind else null,
                waitCondition = waitSpec,
                keyboardKey = parsedKey,
                desiredCheckedState = desiredChecked,
                framePath = if (candidateMatch?.isAmbiguous == false) primaryCandidate?.framePath.orEmpty() else emptyList(),
                shadowHostPath = if (candidateMatch?.isAmbiguous == false) primaryCandidate?.shadowHostPath.orEmpty() else emptyList()
            )
        }
    }

    private fun parseKeyboardKeyFromClause(clause: String, extractedValue: String): String {
        if (extractedValue.isNotBlank()) return extractedValue
        val match = Regex("""\b(enter|return|escape|esc|tab|space|backspace|arrowdown|arrowup|arrowleft|arrowright|down|up|left|right)\b""", RegexOption.IGNORE_CASE)
            .find(clause)
        return match?.value?.replaceFirstChar { it.uppercaseChar() } ?: "Enter"
    }

    private fun parseWaitConditionFromClause(
        clause: String,
        targetLabel: String,
        selector: String,
        extractedValue: String
    ): WaitConditionSpec {
        val lower = clause.lowercase()
        val type = when {
            lower.contains("disappear") || lower.contains("gone") || lower.contains("hide") || lower.contains("hidden") ->
                WaitConditionType.ELEMENT_DISAPPEARS
            lower.contains("interactable") || lower.contains("enabled") || lower.contains("clickable") ->
                WaitConditionType.ELEMENT_INTERACTABLE
            lower.contains("navigation") || lower.contains("page load") ->
                WaitConditionType.NAVIGATION_COMPLETED
            lower.contains("origin change") ->
                WaitConditionType.ORIGIN_CHANGE
            lower.contains("url change") || lower.contains("url contains") ->
                WaitConditionType.URL_CHANGE
            lower.contains("stabilize") || lower.contains("stable") || lower.contains("settle") ->
                WaitConditionType.CONTENT_STABILIZED
            lower.contains("text") || lower.contains("state") || lower.contains("attribute") ->
                WaitConditionType.DOM_STATE_CHANGE
            else -> WaitConditionType.ELEMENT_APPEARS
        }
        return WaitConditionSpec(
            type = type,
            selector = selector,
            targetLabel = targetLabel,
            expectedUrlContains = if (type == WaitConditionType.URL_CHANGE) extractedValue else "",
            expectedText = if (type == WaitConditionType.DOM_STATE_CHANGE) extractedValue else ""
        )
    }

    fun isClauseDestructive(lowerClause: String): Boolean {
        val keywords = listOf(
            "delete", "remove", "erase", "wipe", "clear all",
            "purchase", "buy now", "checkout", "pay ", "payment",
            "submit", "transfer", "send money", "confirm order", "unsubscribe"
        )
        return keywords.any { kw -> lowerClause.contains(kw) }
    }

    private fun extractQuotedOrAfterKeyword(clause: String): String {
        val quoted = Regex("""["']([^"']+)["']""").find(clause)?.groupValues?.getOrNull(1)
        if (!quoted.isNullOrBlank()) return quoted
        val urlMatch = Regex("""(https?://\S+|apex://\S+)""", RegexOption.IGNORE_CASE).find(clause)
        if (urlMatch != null) return urlMatch.groupValues[1]
        return ""
    }

    private fun deriveTargetLabel(
        clause: String,
        actionType: AutomationActionType,
        extractedValue: String
    ): String {
        return when (actionType) {
            AutomationActionType.NAVIGATE -> extractedValue.ifBlank {
                clause.replace(Regex("^(go to|open|navigate to|navigate)\\s+", RegexOption.IGNORE_CASE), "").trim()
            }
            AutomationActionType.SCROLL -> if (clause.contains("up", ignoreCase = true)) "up" else "down"
            else -> {
                var cleaned = clause
                    .replace(Regex("""["'][^"']+["']"""), "")
                    .replace(
                        Regex(
                            "^(click|tap|press key|press|enter|type|fill|clear|empty|focus|blur|unfocus|wait until|wait for|wait|select|choose|toggle|check|uncheck|extract|submit)\\s+(on\\s+|the\\s+|for\\s+)?",
                            RegexOption.IGNORE_CASE
                        ),
                        ""
                    )
                    .trim()
                if (extractedValue.isNotBlank()) {
                    cleaned = cleaned.replace(
                        Regex("^(?:in|into|from|on|at)\\s+(?:the\\s+)?", RegexOption.IGNORE_CASE),
                        ""
                    ).trim()
                }
                cleaned.ifBlank { clause.take(40) }
            }
        }
    }

    private fun inferSelector(
        actionType: AutomationActionType,
        targetLabel: String,
        lowerClause: String
    ): String {
        return when (actionType) {
            AutomationActionType.INPUT, AutomationActionType.CLEAR -> {
                val semanticHints = FieldSemanticClassifier
                    .inferDesiredSemanticsFromInstruction(lowerClause, targetLabel)
                    .mapNotNull { FieldSemanticClassifier.inferSelectorHintsForSemanticType(it).takeIf { h -> h.isNotBlank() } }
                when {
                    lowerClause.contains("email") -> "input[type=\"email\"], input[name*=\"mail\" i]"
                    lowerClause.contains("password") -> "input[type=\"password\"], input[autocomplete*=\"password\" i]"
                    lowerClause.contains("country code") || lowerClause.contains("dial code") ->
                        "input[autocomplete=\"tel-country-code\"], [data-dial-code], [data-country-code], input[type=\"search\"], input[placeholder*=\"country\" i]"
                    lowerClause.contains("phone") || lowerClause.contains("mobile") || lowerClause.contains("tel") ->
                        "input[type=\"tel\"], input[inputmode=\"tel\"], input[autocomplete*=\"tel\" i], input[name*=\"phone\" i]"
                    lowerClause.contains("date") || lowerClause.contains("birth") || lowerClause.contains("dob") || lowerClause.contains("year") || lowerClause.contains("age") ->
                        "input[type=\"date\"], input[autocomplete*=\"bday\" i], [data-datepicker], [data-date], [data-year], input:not([type=\"hidden\"]):not([type=\"submit\"]):not([type=\"button\"]), select, [role=\"combobox\"]"
                    lowerClause.contains("search") -> "input[type=\"search\"], [role=\"searchbox\"], input[name*=\"q\" i], input[name*=\"search\" i]"
                    semanticHints.isNotEmpty() -> semanticHints.first()
                    else -> "input:not([type=\"hidden\"]):not([type=\"submit\"]):not([type=\"button\"]), textarea, [contenteditable=\"true\"], [role=\"textbox\"]"
                }
            }
            AutomationActionType.TOGGLE -> when {
                lowerClause.contains("gender") || lowerClause.contains("sex") ->
                    "[role=\"radio\"], input[type=\"radio\"], label[for], [aria-pressed]"
                else -> "input[type=\"checkbox\"], input[type=\"radio\"], [role=\"checkbox\"], [role=\"radio\"], [role=\"switch\"], [aria-pressed]"
            }
            AutomationActionType.SELECT -> when {
                lowerClause.contains("country code") || lowerClause.contains("dial code") ->
                    "[data-country-code], [data-dial-code], [role=\"option\"], [aria-haspopup], select"
                lowerClause.contains("gender") || lowerClause.contains("sex") ->
                    "select[autocomplete=\"sex\"], select, [role=\"radiogroup\"] [role=\"radio\"], [role=\"combobox\"], [role=\"listbox\"], [role=\"option\"], [aria-haspopup]"
                lowerClause.contains("date") || lowerClause.contains("birth") || lowerClause.contains("calendar") || lowerClause.contains("month") || lowerClause.contains("day") || lowerClause.contains("year") || lowerClause.contains("age") ->
                    "input[type=\"date\"], select[autocomplete*=\"bday\" i], input[autocomplete*=\"bday\" i], [role=\"gridcell\"], [data-date], [data-day], [data-year], [aria-haspopup], select, [role=\"combobox\"], input:not([type=\"hidden\"]):not([type=\"submit\"]):not([type=\"button\"])"
                else -> "select, [role=\"combobox\"], [role=\"listbox\"], [role=\"option\"], [aria-haspopup], [role=\"gridcell\"]"
            }
            AutomationActionType.SUBMIT -> "button[type=\"submit\"], input[type=\"submit\"], button"
            AutomationActionType.CLICK -> when {
                lowerClause.contains("country code") || lowerClause.contains("dial code") ->
                    "[data-country-code], [data-dial-code], [role=\"option\"], button[aria-haspopup], [aria-haspopup]"
                lowerClause.contains("calendar") || lowerClause.contains("date") ->
                    "[role=\"gridcell\"], [data-date], [data-day], [data-datepicker], button, [role=\"button\"]"
                else -> "button, a, [role=\"button\"], [role=\"link\"], [data-action], [data-cta], [data-signup], [role=\"option\"], [role=\"radio\"], [role=\"gridcell\"], [aria-haspopup], .btn, .button, .cta"
            }
            AutomationActionType.FOCUS, AutomationActionType.BLUR, AutomationActionType.KEY_PRESS ->
                "input:not([type=\"hidden\"]), textarea, select, button, a, [contenteditable=\"true\"]"
            AutomationActionType.EXTRACT -> "article, h1, h2, h3, p, a, [role=\"alert\"], [role=\"status\"]"
            else -> ""
        }
    }

    suspend fun executeWorkflow(
        workflow: AutomationWorkflowEntity,
        steps: List<AutomationStep>,
        currentTabUrl: String,
        webViewProvider: () -> WebView?,
        onNavigateUrl: (String) -> Unit,
        onStepProgress: (stepIndex: Int, logMessage: String) -> Unit,
        tabId: String = "",
        navigationSynchronizer: TabNavigationSynchronizer? = null,
        onStepCompleted: (Int) -> Unit = {},
        credentialStepExecutor: (suspend (AutomationStep, String) -> StepExecutionResult)? = null,
        lifecycleController: ExecutionLifecycleController? = null,
        isTabOpenProvider: () -> Boolean = { true },
        liveUrlProvider: (() -> String)? = null,
        onStepDetailedProgress: ((stepIndex: Int, step: AutomationStep, recoveryNotice: String, stepRecords: List<StepExecutionRecord>) -> Unit)? = null,
        workflowDocument: WorkflowDocument? = null,
        initialCheckpoint: PersistedExecutionCheckpoint? = null,
        workflowLookup: (suspend (Long) -> Pair<String, WorkflowDocument>?)? = null,
        confirmedUnsafeRetry: Boolean = false,
        executionId: Long = 0L,
        onPhase6Progress: ((Phase6LiveProgress) -> Unit)? = null,
        vaultValueResolver: (suspend (AutomationStep) -> Result<String>)? = null,
        clearBrowserDataExecutor: (suspend () -> StepExecutionResult)? = null
    ): WorkflowExecutionOutcome {
        val doc = workflowDocument ?: WorkflowDocument(steps = steps)
        return Phase6ExecutionCoordinator(this).executeDocumentWorkflow(
            workflow = workflow,
            document = doc,
            currentTabUrl = currentTabUrl,
            webViewProvider = webViewProvider,
            onNavigateUrl = onNavigateUrl,
            onStepProgress = onStepProgress,
            tabId = tabId,
            navigationSynchronizer = navigationSynchronizer,
            onStepCompleted = onStepCompleted,
            credentialStepExecutor = credentialStepExecutor,
            lifecycleController = lifecycleController,
            isTabOpenProvider = isTabOpenProvider,
            liveUrlProvider = liveUrlProvider,
            onStepDetailedProgress = onStepDetailedProgress,
            initialCheckpoint = initialCheckpoint,
            workflowLookup = workflowLookup,
            confirmedUnsafeRetry = confirmedUnsafeRetry,
            executionId = executionId,
            onPhase6Progress = onPhase6Progress,
            vaultValueResolver = vaultValueResolver,
            clearBrowserDataExecutor = clearBrowserDataExecutor
        )
    }

    private fun isSensitiveStepDescriptor(step: AutomationStep): Boolean {
        if (step.isCredentialStep) return true
        if (step.vaultCategory.equals("PASSWORD", ignoreCase = true)) return true
        val descriptor = "${step.targetLabel} ${step.description} ${step.selector}".lowercase()
        return Regex("""\b(password|passcode|pin|cvv|cvc|csc|secret|token|otp|ssn)\b""")
            .containsMatchIn(descriptor)
    }

    internal fun sanitizeExecutionMessage(rawMsg: String, step: AutomationStep): String {
        var clean = rawMsg.ifBlank { step.description }
        if (isSensitiveStepDescriptor(step)) {
            if (step.value.isNotBlank()) {
                clean = clean.replace(step.value, "••••")
            }
            clean = clean.replace(Regex("[\"'`][^\"'`]{2,}[\"'`]"), "••••")
        }
        return clean
    }

    internal suspend fun executeSingleStepWithRecovery(
        step: AutomationStep,
        activeUrl: String,
        tabId: String = "",
        navigationSynchronizer: TabNavigationSynchronizer? = null,
        nextStep: AutomationStep? = null,
        webViewProvider: () -> WebView?,
        onNavigateUrl: (String) -> Unit,
        credentialStepExecutor: (suspend (AutomationStep, String) -> StepExecutionResult)? = null,
        vaultValueResolver: (suspend (AutomationStep) -> Result<String>)? = null,
        clearBrowserDataExecutor: (suspend () -> StepExecutionResult)? = null
    ): StepExecutionResult {
        val initial = executeSingleStep(
            step = step,
            activeUrl = activeUrl,
            tabId = tabId,
            navigationSynchronizer = navigationSynchronizer,
            nextStep = nextStep,
            webViewProvider = webViewProvider,
            onNavigateUrl = onNavigateUrl,
            credentialStepExecutor = credentialStepExecutor,
            vaultValueResolver = vaultValueResolver,
            clearBrowserDataExecutor = clearBrowserDataExecutor
        )
        if (initial.success ||
            step.isCredentialStep ||
            step.actionType == AutomationActionType.NAVIGATE ||
            step.actionType == AutomationActionType.WAIT ||
            step.actionType == AutomationActionType.CLEAR_DATA ||
            initial.errorCode == AutomationErrorCode.VAULT_VALUES_EXHAUSTED
        ) {
            return initial
        }

        val recoverableError = initial.errorCode == AutomationErrorCode.ELEMENT_NOT_FOUND ||
            initial.errorCode == AutomationErrorCode.ELEMENT_STALE
        if (!recoverableError) {
            return initial
        }

        val expectedOrigin = parseOrigin(activeUrl).canonicalOrigin
        val preSnap = if (tabId.isNotEmpty() && navigationSynchronizer != null) {
            navigationSynchronizer.snapshot(tabId)
        } else {
            null
        }
        val wv = webViewProvider() ?: return initial
        if (preSnap != null && preSnap.activeUrl.isNotBlank()) {
            val snapOrigin = parseOrigin(preSnap.activeUrl).canonicalOrigin
            if (expectedOrigin.isNotEmpty() && snapOrigin.isNotEmpty() && snapOrigin != expectedOrigin) {
                return StepExecutionResult(
                    success = false,
                    message = "Aborted stale locator recovery due to origin change ('$expectedOrigin' -> '$snapOrigin').",
                    errorCode = AutomationErrorCode.UNEXPECTED_ORIGIN_CHANGE,
                    locatorDiagnostics = initial.locatorDiagnostics
                )
            }
        }
        val epoch = preSnap?.epoch ?: 0L
        val freshInspection = universalScanner.scanPage(
            webView = wv,
            tabId = tabId,
            currentUrl = activeUrl,
            navigationEpoch = epoch,
            installDynamicObserver = false
        )
        if (freshInspection.elements.isEmpty()) {
            return initial
        }

        if (preSnap != null && navigationSynchronizer != null) {
            val postSnap = navigationSynchronizer.snapshot(tabId)
            val postOrigin = if (postSnap.activeUrl.isNotBlank()) parseOrigin(postSnap.activeUrl).canonicalOrigin else expectedOrigin
            if (expectedOrigin.isNotEmpty() && postOrigin.isNotEmpty() && postOrigin != expectedOrigin) {
                return StepExecutionResult(
                    success = false,
                    message = "Aborted stale locator recovery after origin shifted to '$postOrigin'.",
                    errorCode = AutomationErrorCode.UNEXPECTED_ORIGIN_CHANGE,
                    locatorDiagnostics = initial.locatorDiagnostics
                )
            }
            if (postSnap.epoch != preSnap.epoch) {
                return StepExecutionResult(
                    success = false,
                    message = "Stale locator recovery aborted: page navigation epoch changed (${preSnap.epoch} -> ${postSnap.epoch}).",
                    errorCode = AutomationErrorCode.ELEMENT_STALE,
                    locatorDiagnostics = initial.locatorDiagnostics
                )
            }
        }

        val match = universalScanner.matchCandidatesForInstruction(
            clause = step.description,
            actionType = step.actionType,
            targetLabel = step.targetLabel.ifBlank { step.description },
            inspectionState = freshInspection
        )
        if (match.isAmbiguous) {
            val candInfos = match.matchedElements.take(12).mapIndexed { idx, el ->
                el.toAmbiguousCandidateInfo(idx)
            }
            val diagnostics = LocatorResolutionDiagnostics(
                recordedLocator = step.selector,
                targetLabel = step.targetLabel,
                totalMatches = match.matchedElements.size,
                visibleMatches = candInfos.count { it.isVisible },
                interactableMatches = candInfos.count { it.isVisible && it.isEnabled },
                finalOutcome = "ELEMENT_AMBIGUOUS",
                attemptedCandidates = initial.locatorDiagnostics?.attemptedCandidates.orEmpty()
            )
            return StepExecutionResult(
                success = false,
                message = "Stale locator recovery found ${match.matchedElements.size} ambiguous candidates for '${step.targetLabel}'.",
                errorCode = AutomationErrorCode.ELEMENT_AMBIGUOUS,
                ambiguousCandidates = candInfos,
                locatorDiagnostics = diagnostics
            )
        }
        val recoveredEl = match.primaryCandidate ?: return initial
        if (recoveredEl.isLocatorAmbiguous || recoveredEl.selector.isBlank()) {
            return if (recoveredEl.isLocatorAmbiguous) {
                val candInfos = match.matchedElements.ifEmpty { listOf(recoveredEl) }
                    .take(12)
                    .mapIndexed { idx, el -> el.toAmbiguousCandidateInfo(idx) }
                StepExecutionResult(
                    success = false,
                    message = "Fresh scan locator for '${step.targetLabel}' is ambiguous.",
                    errorCode = AutomationErrorCode.ELEMENT_AMBIGUOUS,
                    ambiguousCandidates = candInfos,
                    locatorDiagnostics = LocatorResolutionDiagnostics(
                        recordedLocator = step.selector,
                        targetLabel = step.targetLabel,
                        totalMatches = candInfos.size.coerceAtLeast(1),
                        visibleMatches = candInfos.count { it.isVisible },
                        interactableMatches = candInfos.count { it.isVisible && it.isEnabled },
                        finalOutcome = "ELEMENT_AMBIGUOUS",
                        attemptedCandidates = initial.locatorDiagnostics?.attemptedCandidates.orEmpty()
                    )
                )
            } else {
                initial
            }
        }
        if (recoveredEl.selector == step.selector && recoveredEl.locatorCandidates == step.locatorCandidates) {
            return initial
        }

        val recoveredStep = step.copy(
            selector = recoveredEl.selector,
            locatorCandidates = recoveredEl.locatorCandidates,
            controlKindHint = step.controlKindHint ?: recoveredEl.controlKind,
            dropdownKindHint = step.dropdownKindHint ?: recoveredEl.dropdownKind,
            framePath = recoveredEl.framePath.ifEmpty { step.framePath },
            shadowHostPath = recoveredEl.shadowHostPath.ifEmpty { step.shadowHostPath }
        )
        val retryResult = executeSingleStep(
            step = recoveredStep,
            activeUrl = activeUrl,
            tabId = tabId,
            navigationSynchronizer = navigationSynchronizer,
            nextStep = nextStep,
            webViewProvider = webViewProvider,
            onNavigateUrl = onNavigateUrl,
            credentialStepExecutor = credentialStepExecutor,
            vaultValueResolver = vaultValueResolver
        )
        return if (retryResult.success) {
            retryResult.copy(
                recoveredViaFallback = true,
                resolvedSelector = retryResult.resolvedSelector.ifBlank { recoveredEl.selector }
            )
        } else {
            retryResult.copy(
                locatorDiagnostics = retryResult.locatorDiagnostics ?: initial.locatorDiagnostics
            )
        }
    }

    private fun DetectedPageElement.toAmbiguousCandidateInfo(idx: Int): AmbiguousCandidateInfo {
        val isSensitive = sensitiveCategory.isSensitive
        val attrs = LinkedHashMap<String, String>()
        relevantAttributes.forEach { (k, v) ->
            val lk = k.lowercase()
            val isBlocked = lk == "value" || lk == "style" ||
                lk.contains("password") || lk.contains("secret") ||
                lk.contains("token") || lk.contains("otp") ||
                lk.contains("cvv") || lk.contains("cvc") ||
                lk.contains("ssn") || lk == "pin" ||
                (isSensitive && lk == "placeholder")
            if (!isBlocked) {
                attrs[k] = v
            }
        }
        if (htmlId.isNotBlank() && LocatorQualityEvaluator.isStableId(htmlId)) attrs.putIfAbsent("id", htmlId)
        if (inputType.isNotBlank()) attrs.putIfAbsent("type", inputType)
        if (role.isNotBlank()) attrs.putIfAbsent("role", role)
        if (name.isNotBlank()) attrs.putIfAbsent("name", name)
        if (href.isNotBlank()) attrs.putIfAbsent("href", href)
        if (!isSensitive && placeholder.isNotBlank()) attrs.putIfAbsent("placeholder", placeholder)
        val bestSelector = locatorCandidates
            .sortedWith(compareByDescending<LocatorCandidate> { it.isUnique }.thenByDescending { it.confidence })
            .firstOrNull { it.selector.isNotBlank() }?.selector ?: selector
        return AmbiguousCandidateInfo(
            candidateIndex = idx,
            tagName = tagName,
            role = role,
            accessibleName = if (isSensitive) {
                accessibleName.ifBlank { name }.ifBlank { tagName }
            } else {
                accessibleName.ifBlank { label }
            },
            visibleText = if (isSensitive) "" else visibleText.ifBlank { label },
            attributes = attrs,
            parentContext = parentContainerSummary.ifBlank { fieldsetLegend },
            nearbyText = if (isSensitive) "" else nearbyText,
            framePath = framePath,
            shadowHostPath = shadowHostPath,
            isVisible = visible,
            isEnabled = !disabled,
            recommendedSelector = bestSelector
        )
    }

    internal suspend fun awaitWaitCondition(
        condition: WaitConditionSpec,
        tabId: String,
        baselineUrl: String,
        baselineOrigin: String,
        navigationSynchronizer: TabNavigationSynchronizer?,
        webViewProvider: () -> WebView?,
        lifecycleController: ExecutionLifecycleController? = null,
        isTabOpenProvider: () -> Boolean = { true },
        liveUrlProvider: (() -> String)? = null
    ): StepExecutionResult {
        val timeoutMs = condition.timeoutMs.coerceIn(100L, 60_000L)
        val deadline = System.currentTimeMillis() + timeoutMs
        val probeScript = UniversalActionScripts.buildWaitProbeScript(condition)
        var lastSignature = ""
        var stableSinceMs = 0L
        val pollMs = minOf(domStabilityPollMs, 80L).coerceAtLeast(25L)

        while (true) {
            coroutineContext.ensureActive()
            if (lifecycleController != null) {
                lifecycleController.awaitRunnableOrAbort()?.let { abort ->
                    return StepExecutionResult(
                        success = false,
                        message = abort.message,
                        errorCode = abort.code
                    )
                }
                val allowCross = condition.type == WaitConditionType.ORIGIN_CHANGE ||
                    condition.type == WaitConditionType.URL_CHANGE
                val liveUrlNow = liveUrlProvider?.invoke()?.takeIf { it.isNotBlank() } ?: baselineUrl
                lifecycleController.validateBoundContext(
                    currentWebView = webViewProvider(),
                    isTabStillOpen = isTabOpenProvider(),
                    liveUrl = liveUrlNow,
                    expectedOrigin = baselineOrigin,
                    allowCrossOrigin = allowCross,
                    originParser = { parseOrigin(it) }
                )?.let { abort ->
                    return StepExecutionResult(
                        success = false,
                        message = abort.message,
                        errorCode = abort.code
                    )
                }
            }

            if (tabId.isNotEmpty() && navigationSynchronizer != null) {
                val snap = navigationSynchronizer.snapshot(tabId)
                if (snap.phase == NavigationLifecyclePhase.FAILED) {
                    return StepExecutionResult(
                        success = false,
                        message = snap.failureReason ?: "Navigation failed while waiting.",
                        errorCode = AutomationErrorCode.NAVIGATION_FAILED
                    )
                }
                if (condition.type == WaitConditionType.NAVIGATION_COMPLETED &&
                    snap.phase == NavigationLifecyclePhase.FINISHED &&
                    snap.activeUrl.isNotBlank()
                ) {
                    return StepExecutionResult(
                        success = true,
                        message = "Wait satisfied: navigation completed (${extractDomain(snap.activeUrl)}).",
                        verified = true
                    )
                }
            }

            val wv = webViewProvider()
            if (wv == null) {
                return StepExecutionResult(
                    success = false,
                    message = "WebView became unavailable while waiting for ${condition.type.label}.",
                    errorCode = AutomationErrorCode.WEBVIEW_UNAVAILABLE
                )
            }

            when (val evalRes = evalJsWithResult(wv, probeScript, timeoutMs = minOf(jsEvalTimeoutMs, 1_500L))) {
                is JsEvalResult.Success -> {
                    val obj = parseEvalJsonObject(evalRes.rawValue)
                    if (obj != null) {
                        val satisfied = obj.optBoolean("satisfied", false)
                        val probeUrl = obj.optString("url", "")
                            .ifBlank { liveUrlProvider?.invoke().orEmpty() }
                            .ifBlank { navigationSynchronizer?.snapshot(tabId)?.activeUrl.orEmpty() }
                        val sig = obj.optString("signature", "")

                        when (condition.type) {
                            WaitConditionType.URL_CHANGE -> {
                                val changed = probeUrl.isNotBlank() && probeUrl != baselineUrl
                                val matchesFilter = condition.expectedUrlContains.isBlank() ||
                                    probeUrl.contains(condition.expectedUrlContains, ignoreCase = true)
                                if (changed && matchesFilter) {
                                    return StepExecutionResult(
                                        success = true,
                                        message = "Wait satisfied: URL changed to $probeUrl.",
                                        verified = true
                                    )
                                }
                            }
                            WaitConditionType.ORIGIN_CHANGE -> {
                                val probeOrig = parseOrigin(probeUrl).canonicalOrigin
                                val changed = probeOrig.isNotBlank() && probeOrig != baselineOrigin
                                val matchesExpected = condition.expectedOrigin.isBlank() ||
                                    parseOrigin(condition.expectedOrigin).canonicalOrigin == probeOrig
                                if (changed && matchesExpected) {
                                    return StepExecutionResult(
                                        success = true,
                                        message = "Wait satisfied: origin changed to $probeOrig.",
                                        verified = true
                                    )
                                }
                            }
                            WaitConditionType.CONTENT_STABILIZED -> {
                                val now = System.currentTimeMillis()
                                if (satisfied && sig.isNotBlank()) {
                                    if (sig == lastSignature) {
                                        if (stableSinceMs == 0L) stableSinceMs = now
                                        if (now - stableSinceMs >= condition.stabilizeQuietMs) {
                                            return StepExecutionResult(
                                                success = true,
                                                message = "Wait satisfied: page content stabilized.",
                                                verified = true
                                            )
                                        }
                                    } else {
                                        lastSignature = sig
                                        stableSinceMs = now
                                    }
                                }
                            }
                            WaitConditionType.NAVIGATION_COMPLETED -> {
                                if (satisfied && obj.optString("readyState") == "complete") {
                                    return StepExecutionResult(
                                        success = true,
                                        message = "Wait satisfied: document readyState complete.",
                                        verified = true
                                    )
                                }
                            }
                            WaitConditionType.ELEMENT_APPEARS,
                            WaitConditionType.ELEMENT_DISAPPEARS,
                            WaitConditionType.ELEMENT_INTERACTABLE,
                            WaitConditionType.DOM_STATE_CHANGE -> {
                                if (satisfied) {
                                    return StepExecutionResult(
                                        success = true,
                                        message = "Wait satisfied: ${condition.type.label} (${condition.targetLabel.ifBlank { condition.selector }}).",
                                        verified = true
                                    )
                                }
                            }
                        }
                    }
                }
                is JsEvalResult.Timeout -> {
                    // Continue within overall wait timeout unless deadline elapsed
                }
                is JsEvalResult.Unavailable -> {
                    return StepExecutionResult(
                        success = false,
                        message = "WebView became unavailable while waiting: ${evalRes.reason}",
                        errorCode = AutomationErrorCode.WEBVIEW_UNAVAILABLE
                    )
                }
                is JsEvalResult.Failure -> {
                    // Transient evaluation failure during navigation transition; retry until deadline
                }
            }

            if (System.currentTimeMillis() >= deadline) {
                return StepExecutionResult(
                    success = false,
                    message = "Timed out after ${timeoutMs}ms waiting for ${condition.type.label}${
                        condition.targetLabel.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                    }.",
                    errorCode = AutomationErrorCode.WAIT_TIMEOUT
                )
            }
            if (lifecycleController != null) {
                lifecycleController.interruptibleDelay(pollMs)
            } else {
                delay(pollMs)
            }
        }
    }

    internal suspend fun executeSingleStep(
        step: AutomationStep,
        activeUrl: String,
        tabId: String = "",
        navigationSynchronizer: TabNavigationSynchronizer? = null,
        nextStep: AutomationStep? = null,
        webViewProvider: () -> WebView?,
        onNavigateUrl: (String) -> Unit,
        credentialStepExecutor: (suspend (AutomationStep, String) -> StepExecutionResult)? = null,
        vaultValueResolver: (suspend (AutomationStep) -> Result<String>)? = null,
        clearBrowserDataExecutor: (suspend () -> StepExecutionResult)? = null
    ): StepExecutionResult {
        coroutineContext.ensureActive()

        var effectiveStep = step
        var resolvedVaultSecret: String? = null
        val hasVaultBinding = !step.vaultEntryId.isNullOrBlank() ||
            step.useVaultPool ||
            ((step.actionType == AutomationActionType.INPUT ||
                step.actionType == AutomationActionType.SELECT ||
                step.actionType == AutomationActionType.TOGGLE) &&
                step.value.isBlank() &&
                (step.vaultCategory.isNotBlank() || step.vaultFolderId.isNotBlank()))
        if (hasVaultBinding && vaultValueResolver != null) {
            val resolvedVaultResult = vaultValueResolver(step)
            if (resolvedVaultResult.isFailure) {
                val ex = resolvedVaultResult.exceptionOrNull()
                val errCode = if (ex is VaultValuesExhaustedException) {
                    AutomationErrorCode.VAULT_VALUES_EXHAUSTED
                } else {
                    AutomationErrorCode.CREDENTIAL_NOT_FOUND
                }
                val reason = ex?.message
                    ?: "Automation Vault value for '${step.description}' could not be loaded."
                return StepExecutionResult(
                    success = false,
                    message = reason,
                    errorCode = errCode
                )
            }
            val vaultPlainValue = resolvedVaultResult.getOrThrow()
            resolvedVaultSecret = vaultPlainValue
            effectiveStep = step.copy(
                value = vaultPlainValue,
                requiresCredential = false
            )
        }

        if (effectiveStep.isCredentialStep) {
            if (credentialStepExecutor == null) {
                val errCode = if (effectiveStep.credentialReferenceId.isNullOrBlank()) {
                    AutomationErrorCode.CREDENTIAL_UNASSIGNED
                } else {
                    AutomationErrorCode.CREDENTIAL_AUTH_REQUIRED
                }
                return StepExecutionResult(
                    success = false,
                    message = if (effectiveStep.credentialReferenceId.isNullOrBlank()) {
                        "Step '${effectiveStep.description}' requires a saved credential, but none is assigned."
                    } else {
                        "Step '${effectiveStep.description}' requires explicit user authorization and an unlocked Credential Vault."
                    },
                    errorCode = errCode
                )
            }
            return credentialStepExecutor(effectiveStep, activeUrl)
        }

        if (effectiveStep.actionType == AutomationActionType.CLEAR_DATA) {
            if (clearBrowserDataExecutor != null) {
                return clearBrowserDataExecutor()
            }
            val wv = webViewProvider()
            if (wv != null && !activeUrl.equals("apex://home", ignoreCase = true)) {
                val clearJs = """
                    (function() {
                      try { if (window.localStorage) window.localStorage.clear(); } catch (_) {}
                      try { if (window.sessionStorage) window.sessionStorage.clear(); } catch (_) {}
                      return JSON.stringify({ ok: true });
                    })();
                """.trimIndent()
                evalJsWithResultInternal(wv, clearJs, timeoutMs = 1_500L)
            }
            return StepExecutionResult(
                success = true,
                message = "Cleared browser data (cookies, cache & storage).",
                verified = true
            )
        }

        if (effectiveStep.actionType == AutomationActionType.NAVIGATE) {
            val dest = effectiveStep.value.ifBlank { effectiveStep.targetLabel }.trim()
            if (dest.isBlank() || dest.startsWith("javascript:", ignoreCase = true)) {
                return StepExecutionResult(
                    success = false,
                    message = "Invalid or missing target URL for navigation step.",
                    errorCode = AutomationErrorCode.INVALID_URL
                )
            }
            return navigateAndAwaitSettlement(
                dest = dest,
                tabId = tabId,
                navigationSynchronizer = navigationSynchronizer,
                webViewProvider = webViewProvider,
                nextStep = nextStep,
                onNavigateUrl = onNavigateUrl
            )
        }

        val webView = webViewProvider()
        if (webView == null || activeUrl.equals("apex://home", ignoreCase = true)) {
            return StepExecutionResult(
                success = false,
                message = "No active webpage loaded in WebView to execute '${effectiveStep.description}'.",
                errorCode = AutomationErrorCode.WEBVIEW_UNAVAILABLE
            )
        }

        val trimmedSelector = effectiveStep.selector.trim()
        if (trimmedSelector.isNotEmpty()) {
            val validation = validateSelector(trimmedSelector)
            if (!validation.isValid) {
                return StepExecutionResult(
                    success = false,
                    message = "Unsafe or malformed CSS selector '$trimmedSelector': ${validation.reason}",
                    errorCode = AutomationErrorCode.INVALID_SELECTOR
                )
            }
        } else if (effectiveStep.targetLabel.isBlank() && effectiveStep.actionType != AutomationActionType.SCROLL) {
            return StepExecutionResult(
                success = false,
                message = "Step '${effectiveStep.description}' is missing both selector and target label.",
                errorCode = AutomationErrorCode.INVALID_SELECTOR
            )
        }

        val selectorEsc = JSONObject.quote(trimmedSelector)
        val labelEsc = JSONObject.quote(effectiveStep.targetLabel.trim())
        val valueEsc = JSONObject.quote(effectiveStep.value)

        val script = buildStepExecutionScript(
            step = effectiveStep,
            selectorEsc = selectorEsc,
            labelEsc = labelEsc,
            valueEsc = valueEsc
        )

        val effectiveTimeout = if (effectiveStep.stepTimeoutMs > 0L) effectiveStep.stepTimeoutMs else jsEvalTimeoutMs
        var dynamicAttempt = 0
        while (true) {
            when (val evalResult = evalJsWithResult(webView, script, timeoutMs = effectiveTimeout)) {
                is JsEvalResult.Timeout -> return StepExecutionResult(
                    success = false,
                    message = "WebView DOM timed out after ${evalResult.timeoutMs}ms for '${effectiveStep.description}'.",
                    errorCode = AutomationErrorCode.JS_TIMEOUT
                )
                is JsEvalResult.Unavailable -> return StepExecutionResult(
                    success = false,
                    message = "WebView became unavailable while executing '${effectiveStep.description}': ${evalResult.reason}",
                    errorCode = AutomationErrorCode.WEBVIEW_UNAVAILABLE
                )
                is JsEvalResult.Failure -> return StepExecutionResult(
                    success = false,
                    message = "WebView script evaluation failed for '${effectiveStep.description}': ${evalResult.reason}",
                    errorCode = AutomationErrorCode.JS_EXCEPTION
                )
                is JsEvalResult.Success -> {
                    val isPendingDynamic = runCatching {
                        val raw = evalResult.rawValue
                        val unquoted = if (raw.startsWith("\"") && raw.endsWith("\"")) {
                            JSONTokener(raw).nextValue().toString()
                        } else {
                            raw
                        }
                        JSONObject(unquoted).optBoolean("pendingDynamic", false)
                    }.getOrDefault(false)
                    if (isPendingDynamic && dynamicAttempt < 12) {
                        dynamicAttempt++
                        delay(50L)
                        continue
                    }
                    val rawOutcome = parseStepJsonResponse(evalResult.rawValue, effectiveStep)
                    return if (!resolvedVaultSecret.isNullOrEmpty() && isSensitiveStepDescriptor(step)) {
                        rawOutcome.copy(
                            message = rawOutcome.message.replace(resolvedVaultSecret, "••••")
                        )
                    } else {
                        rawOutcome
                    }
                }
            }
        }
    }

    internal fun buildStepExecutionScript(
        step: AutomationStep,
        selectorEsc: String = JSONObject.quote(step.selector.trim()),
        labelEsc: String = JSONObject.quote(step.targetLabel.trim()),
        valueEsc: String = JSONObject.quote(step.value)
    ): String {
        return UniversalActionScripts.buildStepExecutionScript(step)
    }

    private fun parseStepJsonResponse(raw: String, step: AutomationStep): StepExecutionResult {
        if (raw.isBlank() || raw == "null") {
            return StepExecutionResult(
                success = false,
                message = "WebView DOM did not respond for '${step.description}'.",
                errorCode = AutomationErrorCode.JS_EXCEPTION
            )
        }
        return runCatching {
            val unquoted = if (raw.startsWith("\"") && raw.endsWith("\"")) {
                JSONTokener(raw).nextValue().toString()
            } else {
                raw
            }
            val obj = JSONObject(unquoted)
            val ok = obj.optBoolean("ok", false)
            val rawCode = obj.optString("code", "")
            val parsedCode = runCatching {
                if (rawCode.isNotBlank()) AutomationErrorCode.valueOf(rawCode) else null
            }.getOrNull() ?: if (ok) AutomationErrorCode.NONE else AutomationErrorCode.JS_EXCEPTION
            val ambigArr = obj.optJSONArray("ambiguousCandidates")
            val ambigList = if (ambigArr != null) {
                buildList {
                    for (i in 0 until ambigArr.length()) {
                        AmbiguousCandidateInfo.fromJson(ambigArr.optJSONObject(i))?.let { add(it) }
                    }
                }
            } else emptyList()
            val diagnostics = LocatorResolutionDiagnostics.fromJson(obj.optJSONObject("diagnostics"))
            val targetBox = ElementBoundingBox.fromJson(
                obj.optJSONObject("targetRect")
                    ?: obj.optJSONObject("diagnostics")?.optJSONObject("targetRect")
            )
            StepExecutionResult(
                success = ok,
                message = obj.optString("msg", "Step evaluated"),
                errorCode = parsedCode,
                resolvedSelector = obj.optString("resolvedSelector", step.selector),
                recoveredViaFallback = obj.optBoolean("recoveredViaFallback", false),
                verified = obj.optBoolean("verified", ok),
                ambiguousCandidates = ambigList,
                locatorDiagnostics = diagnostics,
                targetBoundingBox = targetBox
            )
        }.getOrElse {
            StepExecutionResult(
                success = false,
                message = "Invalid DOM response while running '${step.description}'.",
                errorCode = AutomationErrorCode.JS_EXCEPTION
            )
        }
    }

    suspend fun highlightElementOnPage(
        webView: WebView?,
        selector: String,
        targetLabel: String = "",
        scrollIntoView: Boolean = true
    ): ElementBoundingBox? {
        if (webView == null || selector.isBlank()) return null
        val script = UniversalActionScripts.buildHighlightSelectorScript(
            selector = selector.trim(),
            targetLabel = targetLabel.trim(),
            scrollIntoView = scrollIntoView
        )
        return when (val evalRes = evalJsWithResult(webView, script, timeoutMs = minOf(jsEvalTimeoutMs, 2_000L))) {
            is JsEvalResult.Success -> {
                val obj = parseEvalJsonObject(evalRes.rawValue)
                ElementBoundingBox.fromJson(obj?.optJSONObject("targetRect"))
            }
            else -> null
        }
    }

    fun clearElementHighlightsOnPage(webView: WebView?) {
        if (webView == null) return
        val script = UniversalActionScripts.buildClearHighlightsScript()
        val ref = WeakReference(webView)
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runCatching { webView.evaluateJavascript(script, null) }
        } else {
            mainHandler.post {
                ref.get()?.let { wv ->
                    runCatching { wv.evaluateJavascript(script, null) }
                }
            }
        }
    }

    internal suspend fun navigateAndAwaitSettlement(
        dest: String,
        tabId: String,
        navigationSynchronizer: TabNavigationSynchronizer?,
        webViewProvider: () -> WebView?,
        nextStep: AutomationStep?,
        onNavigateUrl: (String) -> Unit
    ): StepExecutionResult {
        coroutineContext.ensureActive()
        val preEpoch = if (tabId.isNotEmpty() && navigationSynchronizer != null) {
            navigationSynchronizer.snapshot(tabId).epoch
        } else {
            0L
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            onNavigateUrl(dest)
        } else {
            withTimeoutOrNull(jsEvalTimeoutMs) {
                suspendCancellableCoroutine { cont ->
                    val navRunnable = Runnable {
                        if (cont.isActive) {
                            runCatching { onNavigateUrl(dest) }
                                .onSuccess {
                                    if (cont.isActive) cont.resume(Unit)
                                }
                                .onFailure { t ->
                                    if (t is CancellationException) throw t
                                    if (cont.isActive) cont.resume(Unit)
                                }
                        }
                    }
                    cont.invokeOnCancellation {
                        mainHandler.removeCallbacks(navRunnable)
                    }
                    val posted = mainHandler.post(navRunnable)
                    if (!posted && cont.isActive) {
                        cont.resume(Unit)
                    }
                }
            }
        }
        coroutineContext.ensureActive()

        if (tabId.isNotEmpty() && navigationSynchronizer != null) {
            val snapAfterInvoke = navigationSynchronizer.snapshot(tabId)
            val expectedEpoch = maxOf(preEpoch + 1L, snapAfterInvoke.epoch)
            when (val waitRes = navigationSynchronizer.awaitNavigationSettled(tabId, expectedEpoch, navTimeoutMs)) {
                is NavigationWaitResult.Failed -> {
                    return StepExecutionResult(
                        success = false,
                        message = "Navigation to $dest failed: ${waitRes.reason}",
                        errorCode = AutomationErrorCode.NAVIGATION_FAILED
                    )
                }
                is NavigationWaitResult.Timeout -> {
                    return StepExecutionResult(
                        success = false,
                        message = "Navigation to $dest timed out after ${waitRes.timeoutMs}ms.",
                        errorCode = AutomationErrorCode.NAVIGATION_TIMEOUT
                    )
                }
                is NavigationWaitResult.Completed -> {
                    val readyRes = awaitDomReady(
                        webViewProvider = webViewProvider,
                        nextStep = nextStep,
                        tabId = tabId,
                        navigationSynchronizer = navigationSynchronizer
                    )
                    if (!readyRes.success) return readyRes
                    return StepExecutionResult(
                        success = true,
                        message = "Navigated to ${waitRes.finalUrl.ifBlank { dest }}",
                        errorCode = AutomationErrorCode.NONE
                    )
                }
            }
        }

        // Direct WebView polling fallback if no external synchronizer was supplied
        awaitDomReady(
            webViewProvider = webViewProvider,
            nextStep = nextStep,
            tabId = tabId,
            navigationSynchronizer = null
        )
        return StepExecutionResult(
            success = true,
            message = "Navigated to $dest",
            errorCode = AutomationErrorCode.NONE
        )
    }

    private suspend fun synchronizePostStepState(
        step: AutomationStep,
        nextStep: AutomationStep,
        tabId: String,
        preSnapshot: TabNavigationSnapshot?,
        navigationSynchronizer: TabNavigationSynchronizer?,
        webViewProvider: () -> WebView?,
        lifecycleController: ExecutionLifecycleController? = null
    ): StepExecutionResult {
        coroutineContext.ensureActive()
        if (tabId.isNotEmpty() && navigationSynchronizer != null && preSnapshot != null) {
            // Brief non-blocking yield so WebView navigation callbacks queued by click()/submit()/key/select/toggle can land
            if (step.actionType == AutomationActionType.CLICK ||
                step.actionType == AutomationActionType.SUBMIT ||
                step.actionType == AutomationActionType.KEY_PRESS ||
                step.actionType == AutomationActionType.SELECT ||
                step.actionType == AutomationActionType.TOGGLE
            ) {
                val yieldMs = minOf(80L, domStabilityPollMs.coerceAtLeast(40L))
                if (lifecycleController != null) {
                    lifecycleController.interruptibleDelay(yieldMs)
                } else {
                    delay(yieldMs)
                }
                coroutineContext.ensureActive()
            }
            val currentSnap = navigationSynchronizer.snapshot(tabId)
            if (currentSnap.phase == NavigationLifecyclePhase.FAILED &&
                currentSnap.epoch >= preSnapshot.epoch &&
                currentSnap.updatedAtMs > preSnapshot.updatedAtMs
            ) {
                return StepExecutionResult(
                    success = false,
                    message = "Navigation failed after '${step.description}': ${currentSnap.failureReason ?: "Load error"}",
                    errorCode = AutomationErrorCode.NAVIGATION_FAILED
                )
            }
            val navStarted = currentSnap.phase == NavigationLifecyclePhase.REQUESTED ||
                currentSnap.phase == NavigationLifecyclePhase.LOADING ||
                currentSnap.epoch > preSnapshot.epoch ||
                currentSnap.urlCommitSeq > preSnapshot.urlCommitSeq
            if (navStarted) {
                val targetEpoch = if (currentSnap.epoch > preSnapshot.epoch) {
                    currentSnap.epoch
                } else {
                    preSnapshot.epoch
                }
                when (val waitRes = navigationSynchronizer.awaitNavigationSettled(tabId, targetEpoch, navTimeoutMs)) {
                    is NavigationWaitResult.Failed -> return StepExecutionResult(
                        success = false,
                        message = "Navigation failed after '${step.description}': ${waitRes.reason}",
                        errorCode = AutomationErrorCode.NAVIGATION_FAILED
                    )
                    is NavigationWaitResult.Timeout -> return StepExecutionResult(
                        success = false,
                        message = "Navigation timed out after '${step.description}'.",
                        errorCode = AutomationErrorCode.NAVIGATION_TIMEOUT
                    )
                    is NavigationWaitResult.Completed -> Unit
                }
            }
        }
        return awaitDomReady(
            webViewProvider = webViewProvider,
            nextStep = nextStep,
            tabId = tabId,
            navigationSynchronizer = navigationSynchronizer,
            lifecycleController = lifecycleController
        )
    }

    internal suspend fun awaitDomReady(
        webViewProvider: () -> WebView?,
        nextStep: AutomationStep?,
        tabId: String,
        navigationSynchronizer: TabNavigationSynchronizer?,
        lifecycleController: ExecutionLifecycleController? = null
    ): StepExecutionResult {
        val wv = webViewProvider() ?: return StepExecutionResult(true, "DOM check skipped (no WebView)")
        val needsTargetPresence = nextStep != null &&
            nextStep.actionType != AutomationActionType.NAVIGATE &&
            nextStep.actionType != AutomationActionType.SCROLL &&
            nextStep.actionType != AutomationActionType.WAIT &&
            nextStep.actionType != AutomationActionType.LOOP &&
            nextStep.actionType != AutomationActionType.CONDITION &&
            nextStep.actionType != AutomationActionType.CALL_WORKFLOW &&
            nextStep.actionType != AutomationActionType.SET_VARIABLE &&
            nextStep.actionType != AutomationActionType.CLEAR_DATA &&
            nextStep.actionType != AutomationActionType.UNSUPPORTED &&
            (nextStep.selector.isNotBlank() || nextStep.targetLabel.isNotBlank())

        val nextSelEsc = JSONObject.quote(nextStep?.selector?.trim().orEmpty())
        val nextLabelEsc = JSONObject.quote(nextStep?.targetLabel?.trim().orEmpty())
        val nextCandidatesJson = JSONArray(
            buildList {
                nextStep?.selector?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
                nextStep?.locatorCandidates?.forEach { cand ->
                    val s = cand.selector.trim()
                    if (s.isNotEmpty() && !contains(s)) add(s)
                }
            }
        ).toString()
        val nextQueryEsc = JSONObject.quote(
            when (nextStep?.actionType) {
                AutomationActionType.INPUT, AutomationActionType.CLEAR, AutomationActionType.FILL_CREDENTIAL ->
                    "input:not([type=\"hidden\"]), textarea, [contenteditable=\"true\"], [contenteditable=\"plaintext-only\"], [role=\"textbox\"], [role=\"searchbox\"]"
                AutomationActionType.TOGGLE ->
                    "input[type=\"checkbox\"], input[type=\"radio\"], [role=\"checkbox\"], [role=\"switch\"], [role=\"radio\"]"
                AutomationActionType.SELECT ->
                    "select, [role=\"combobox\"], [role=\"listbox\"], [role=\"option\"]"
                else -> "button, a, [role=\"button\"], input, select, [contenteditable=\"true\"]"
            }
        )

        val targetProbeScript = if (needsTargetPresence) {
            """
                    $JS_TARGET_HELPERS
                    const cands = $nextCandidatesJson;
                    targetReady = cands.some(function(s) {
                        return apexResolveTarget(s, $nextLabelEsc, $nextQueryEsc).ok;
                    });
                    if (!targetReady) {
                        targetReady = apexResolveTarget($nextSelEsc, $nextLabelEsc, $nextQueryEsc).ok;
                    }
            """.trimIndent()
        } else {
            "targetReady = true;"
        }

        val probeScript = """
            (function() {
                try {
                    const readyState = String(document.readyState || '');
                    const url = String(window.location && window.location.href || '');
                    const childLen = document.body ? document.body.children.length : 0;
                    const textLen = document.body ? (document.body.innerText || '').length : 0;
                    let targetReady = true;
                    $targetProbeScript
                    return JSON.stringify({
                        readyState: readyState,
                        url: url,
                        shape: childLen + ':' + textLen,
                        targetReady: Boolean(targetReady)
                    });
                } catch (e) {
                    return JSON.stringify({ readyState: 'error', targetReady: false });
                }
            })();
        """.trimIndent()

        val deadline = System.currentTimeMillis() + domReadyTimeoutMs
        var lastShape: String? = null
        var settledChecks = 0

        while (true) {
            coroutineContext.ensureActive()
            if (lifecycleController != null) {
                lifecycleController.awaitRunnableOrAbort()?.let { abort ->
                    return StepExecutionResult(
                        success = false,
                        message = abort.message,
                        errorCode = abort.code
                    )
                }
            }
            if (tabId.isNotEmpty() && navigationSynchronizer != null) {
                val snap = navigationSynchronizer.snapshot(tabId)
                if (snap.phase == NavigationLifecyclePhase.FAILED) {
                    return StepExecutionResult(
                        success = false,
                        message = snap.failureReason ?: "Page load failed",
                        errorCode = AutomationErrorCode.NAVIGATION_FAILED
                    )
                }
                if (snap.phase == NavigationLifecyclePhase.REQUESTED || snap.phase == NavigationLifecyclePhase.LOADING) {
                    when (val waitRes = navigationSynchronizer.awaitNavigationSettled(tabId, snap.epoch, navTimeoutMs)) {
                        is NavigationWaitResult.Failed -> return StepExecutionResult(
                            success = false,
                            message = waitRes.reason,
                            errorCode = AutomationErrorCode.NAVIGATION_FAILED
                        )
                        is NavigationWaitResult.Timeout -> return StepExecutionResult(
                            success = false,
                            message = "Page redirect timed out after ${waitRes.timeoutMs}ms.",
                            errorCode = AutomationErrorCode.NAVIGATION_TIMEOUT
                        )
                        is NavigationWaitResult.Completed -> Unit
                    }
                }
            }

            val evalRes = evalJsWithResult(wv, probeScript, timeoutMs = minOf(jsEvalTimeoutMs, 1_500L))
            when (evalRes) {
                is JsEvalResult.Unavailable, is JsEvalResult.Failure -> {
                    return StepExecutionResult(true, "DOM readiness check skipped")
                }
                is JsEvalResult.Timeout -> {
                    return StepExecutionResult(
                        success = false,
                        message = "DOM readiness probe timed out.",
                        errorCode = AutomationErrorCode.JS_TIMEOUT
                    )
                }
                is JsEvalResult.Success -> {
                    val parsed = runCatching {
                        val raw = evalRes.rawValue
                        val unquoted = if (raw.startsWith("\"") && raw.endsWith("\"")) {
                            JSONTokener(raw).nextValue().toString()
                        } else {
                            raw
                        }
                        JSONObject(unquoted)
                    }.getOrNull() ?: return StepExecutionResult(true, "DOM probe non-JSON")

                    val readyState = parsed.optString("readyState", "").ifBlank {
                        parsed.optString("state", "")
                    }
                    val readyFlag = parsed.optBoolean("ready", false)
                    val shape = parsed.optString("shape", "")
                    val targetReady = if (parsed.has("targetReady")) {
                        parsed.optBoolean("targetReady", true)
                    } else {
                        true
                    }

                    if ((readyState == "complete" || readyFlag) && targetReady) {
                        return StepExecutionResult(true, "DOM ready")
                    }
                    if ((readyState == "complete" || readyFlag) && !needsTargetPresence) {
                        if (lastShape == shape) {
                            settledChecks++
                            if (settledChecks >= 1) return StepExecutionResult(true, "DOM stabilized")
                        } else {
                            settledChecks = 0
                        }
                    }
                    lastShape = shape
                }
            }

            if (System.currentTimeMillis() >= deadline) {
                return StepExecutionResult(true, "DOM readiness window reached")
            }
            if (lifecycleController != null) {
                lifecycleController.interruptibleDelay(domStabilityPollMs)
            } else {
                delay(domStabilityPollMs)
            }
        }
    }

    internal suspend fun evalJsWithResult(
        webView: WebView?,
        script: String,
        timeoutMs: Long = jsEvalTimeoutMs
    ): JsEvalResult {
        if (webView == null) {
            return JsEvalResult.Unavailable("WebView is null")
        }
        coroutineContext.ensureActive()

        val webViewRef = WeakReference(webView)
        val result = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<JsEvalResult> { cont ->
                val invokeEval = Runnable {
                    if (!cont.isActive) return@Runnable
                    val targetWebView = webViewRef.get()
                    if (targetWebView == null) {
                        cont.resume(JsEvalResult.Unavailable("WebView was released"))
                        return@Runnable
                    }
                    try {
                        targetWebView.evaluateJavascript(script) { value ->
                            if (cont.isActive) {
                                cont.resume(JsEvalResult.Success(value ?: ""))
                            }
                        }
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (t: Throwable) {
                        if (cont.isActive) {
                            cont.resume(
                                JsEvalResult.Failure(t.message ?: "WebView evaluateJavascript failed")
                            )
                        }
                    }
                }
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    invokeEval.run()
                } else {
                    cont.invokeOnCancellation {
                        mainHandler.removeCallbacks(invokeEval)
                    }
                    val posted = mainHandler.post(invokeEval)
                    if (!posted && cont.isActive) {
                        cont.resume(JsEvalResult.Unavailable("Failed to schedule JS evaluation on main thread"))
                    }
                }
            }
        }

        coroutineContext.ensureActive()
        return result ?: JsEvalResult.Timeout(timeoutMs)
    }

    internal suspend fun evalJsWithResultInternal(
        webView: WebView?,
        script: String,
        timeoutMs: Long = jsEvalTimeoutMs
    ): JsEvalResult = evalJsWithResult(webView, script, timeoutMs)
}
