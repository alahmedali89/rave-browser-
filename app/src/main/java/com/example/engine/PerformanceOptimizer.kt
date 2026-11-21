package com.example.engine

import android.annotation.SuppressLint
import android.os.Build
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import org.json.JSONArray

data class PreloadCandidate(
    val title: String,
    val url: String,
    val confidencePercent: Int,
    val status: String = "Prefetched DNS + Static Assets"
)

class PerformanceOptimizer {

    private val mobileUserAgent =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    private val desktopUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    fun desktopRequestHeaders(desktopMode: Boolean): Map<String, String> {
        return if (desktopMode) {
            mapOf(
                "Sec-CH-UA" to "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"",
                "Sec-CH-UA-Mobile" to "?0",
                "Sec-CH-UA-Platform" to "\"Windows\"",
                "Upgrade-Insecure-Requests" to "1"
            )
        } else {
            mapOf(
                "Sec-CH-UA-Mobile" to "?1",
                "Sec-CH-UA-Platform" to "\"Android\""
            )
        }
    }
    val hasDrmRenderNode: Boolean by lazy {
        runCatching {
            val node = java.io.File("/dev/dri/renderD128")
            node.exists() && node.canRead() && node.canWrite()
        }.getOrDefault(false)
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun configureFastWebView(
        webView: WebView,
        scriptGuardEnabled: Boolean,
        desktopMode: Boolean,
        aggressiveCaching: Boolean,
        isIncognito: Boolean = false,
        textZoomPercent: Int = 100
    ) {
        // Always use hardware-accelerated Chromium rendering (LAYER_TYPE_NONE) for crisp text
        // rasterization, smooth scrolling, and proper subpixel contrast.
        if (webView.layerType != View.LAYER_TYPE_NONE) {
            webView.setLayerType(View.LAYER_TYPE_NONE, null)
        }
        webView.isVerticalScrollBarEnabled = true
        webView.isHorizontalScrollBarEnabled = desktopMode
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false) // Privacy-by-default: block 3P cookies
        }

        val desiredJsEnabled = !scriptGuardEnabled
        val desiredCacheMode = if (isIncognito) {
            WebSettings.LOAD_NO_CACHE
        } else {
            WebSettings.LOAD_DEFAULT
        }
        val desiredLayoutAlgorithm = if (desktopMode) {
            WebSettings.LayoutAlgorithm.NORMAL
        } else {
            WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
        }
        val desiredTextZoom = textZoomPercent.coerceIn(50, 200)
        val desiredUserAgent = if (desktopMode) {
            desktopUserAgent
        } else {
            mobileUserAgent
        }

        webView.settings.apply {
            if (javaScriptEnabled != desiredJsEnabled) {
                javaScriptEnabled = desiredJsEnabled
            }
            domStorageEnabled = true
            databaseEnabled = !isIncognito
            @Suppress("DEPRECATION")
            saveFormData = false
            @Suppress("DEPRECATION")
            savePassword = false
            loadsImagesAutomatically = true
            if (cacheMode != desiredCacheMode) {
                cacheMode = desiredCacheMode
            }
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            useWideViewPort = true
            loadWithOverviewMode = true
            if (layoutAlgorithm != desiredLayoutAlgorithm) {
                layoutAlgorithm = desiredLayoutAlgorithm
            }
            if (textZoom != desiredTextZoom) {
                textZoom = desiredTextZoom
            }
            minimumFontSize = 8
            defaultFontSize = 16
            defaultFixedFontSize = 13
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            ) {
                @Suppress("DEPRECATION")
                forceDark = WebSettings.FORCE_DARK_OFF
            }
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(true)
            mediaPlaybackRequiresUserGesture = true
            // Only enable offscreen pre-raster when a hardware DRM render node is present;
            // otherwise minigbm logs "E/platform: Failed to open rendernode: No such file or directory".
            offscreenPreRaster = hasDrmRenderNode

            if (userAgentString != desiredUserAgent) {
                userAgentString = desiredUserAgent
            }
        }

        if (isIncognito && webView.url.isNullOrBlank()) {
            runCatching {
                webView.clearCache(true)
                webView.clearFormData()
                webView.clearHistory()
            }
        }
    }

    /**
     * Wipes all in-memory and origin-level storage (localStorage, sessionStorage, IndexedDB, cache, history, form data)
     * from an Incognito WebView before it is closed.
     */
    fun wipeIncognitoWebView(webView: WebView) {
        runCatching {
            webView.stopLoading()
            webView.evaluateJavascript(
                """
                (function() {
                    try {
                        if (window.localStorage) window.localStorage.clear();
                        if (window.sessionStorage) window.sessionStorage.clear();
                        if (window.caches && window.caches.keys) {
                            window.caches.keys().then(function(names) {
                                for (var i = 0; i < names.length; i++) window.caches.delete(names[i]);
                            });
                        }
                        if (window.indexedDB && window.indexedDB.databases) {
                            window.indexedDB.databases().then(function(dbs) {
                                for (var i = 0; i < dbs.length; i++) {
                                    if (dbs[i] && dbs[i].name) window.indexedDB.deleteDatabase(dbs[i].name);
                                }
                            });
                        }
                    } catch (e) {}
                })();
                """.trimIndent(),
                null
            )
            webView.clearCache(true)
            webView.clearFormData()
            webView.clearHistory()
            webView.clearSslPreferences()
            webView.loadUrl("about:blank")
        }
    }

    /**
     * Purges cookies and WebStorage site data when the Incognito session ends.
     */
    fun wipeIncognitoSessionStorage() {
        runCatching {
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeSessionCookies(null)
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
        }
        runCatching {
            WebStorage.getInstance().deleteAllData()
        }
    }

    /**
     * Injects or restores the `<meta name="viewport">` tag and `navigator` Desktop properties
     * so responsive websites and client-side checks render true 1280px desktop layouts.
     */
    fun applyDesktopViewportMode(webView: WebView, desktopMode: Boolean) {
        val dm = webView.resources.displayMetrics
        val viewWidthPx = if (webView.width > 0) webView.width else dm.widthPixels
        val density = if (dm.density > 0f) dm.density else 2.75f
        val cssWidth = (viewWidthPx / density).toInt().coerceIn(320, 1280)
        val scaleStr = String.format(
            java.util.Locale.US,
            "%.3f",
            (cssWidth.toFloat() / 1280f).coerceIn(0.22f, 1.0f)
        )

        val script = if (desktopMode) {
            """
            (function() {
                try {
                    if (!window.__apexDesktopNavigatorPatched) {
                        window.__apexDesktopNavigatorPatched = true;
                        Object.defineProperty(navigator, 'platform', { get: function() { return 'Win32'; }, configurable: true });
                        Object.defineProperty(navigator, 'maxTouchPoints', { get: function() { return 0; }, configurable: true });
                        if (navigator.userAgentData) {
                            Object.defineProperty(navigator, 'userAgentData', {
                                get: function() {
                                    return {
                                        brands: [
                                            { brand: 'Google Chrome', version: '131' },
                                            { brand: 'Chromium', version: '131' },
                                            { brand: 'Not_A Brand', version: '24' }
                                        ],
                                        mobile: false,
                                        platform: 'Windows',
                                        getHighEntropyValues: function() {
                                            return Promise.resolve({
                                                architecture: 'x86',
                                                bitness: '64',
                                                mobile: false,
                                                model: '',
                                                platform: 'Windows',
                                                platformVersion: '15.0.0'
                                            });
                                        }
                                    };
                                },
                                configurable: true
                            });
                        }
                    }
                    var desktopWidth = 1280;
                    var contentAttr = 'width=1280, initial-scale=$scaleStr, minimum-scale=0.1, maximum-scale=5.0, user-scalable=yes';
                    var head = document.head || document.getElementsByTagName('head')[0] || document.documentElement;
                    if (head) {
                        var metas = document.querySelectorAll('meta[name="viewport"]');
                        if (metas.length > 0) {
                            metas.forEach(function(m, idx) {
                                if (idx === 0) {
                                    if (!m.hasAttribute('data-apex-orig-viewport')) {
                                        m.setAttribute('data-apex-orig-viewport', m.getAttribute('content') || '');
                                    }
                                    if (m.getAttribute('content') !== contentAttr) {
                                        m.setAttribute('content', contentAttr);
                                    }
                                } else {
                                    m.parentNode && m.parentNode.removeChild(m);
                                }
                            });
                        } else {
                            var meta = document.createElement('meta');
                            meta.name = 'viewport';
                            meta.setAttribute('data-apex-created', 'true');
                            meta.content = contentAttr;
                            head.appendChild(meta);
                        }
                    }
                    var minWidthPx = desktopWidth + 'px';
                    if (document.documentElement && document.documentElement.style.minWidth !== minWidthPx) {
                        document.documentElement.style.minWidth = minWidthPx;
                    }
                } catch (e) {}
            })();
            """.trimIndent()
        } else {
            """
            (function() {
                try {
                    var meta = document.querySelector('meta[name="viewport"]');
                    if (meta) {
                        if (meta.hasAttribute('data-apex-orig-viewport')) {
                            var orig = meta.getAttribute('data-apex-orig-viewport') || 'width=device-width, initial-scale=1.0';
                            if (meta.getAttribute('content') !== orig) {
                                meta.setAttribute('content', orig);
                            }
                            meta.removeAttribute('data-apex-orig-viewport');
                        } else if (meta.hasAttribute('data-apex-created')) {
                            meta.parentNode && meta.parentNode.removeChild(meta);
                        }
                    }
                    if (document.documentElement && document.documentElement.style.minWidth) {
                        document.documentElement.style.minWidth = '';
                    }
                } catch (e) {}
            })();
            """.trimIndent()
        }
        webView.evaluateJavascript(script, null)
    }

    /**
     * When [forceEnableZoom] is true, strips restrictive `user-scalable=no` or low `maximum-scale`
     * viewport locks from pages so users can always pinch-to-zoom.
     */
    fun applyForceEnableZoom(webView: WebView, forceEnableZoom: Boolean) {
        webView.settings.setSupportZoom(true)
        webView.settings.builtInZoomControls = true
        webView.settings.displayZoomControls = false

        val script = if (forceEnableZoom) {
            """
            (function() {
                try {
                    var meta = document.querySelector('meta[name="viewport"]');
                    if (meta) {
                        var current = meta.getAttribute('content') || '';
                        if (!meta.hasAttribute('data-apex-orig-zoom-vp')) {
                            meta.setAttribute('data-apex-orig-zoom-vp', current);
                        }
                        var parts = current.split(',')
                            .map(function(p) { return p.trim(); })
                            .filter(function(p) {
                                return p.length > 0 &&
                                    !/^user-scalable\s*=/i.test(p) &&
                                    !/^maximum-scale\s*=/i.test(p);
                            });
                        parts.push('user-scalable=yes');
                        parts.push('maximum-scale=5.0');
                        var updated = parts.join(', ');
                        if (current !== updated) {
                            meta.setAttribute('content', updated);
                        }
                    }
                } catch (e) {}
            })();
            """.trimIndent()
        } else {
            """
            (function() {
                try {
                    var meta = document.querySelector('meta[name="viewport"]');
                    if (meta && meta.hasAttribute('data-apex-orig-zoom-vp')) {
                        var orig = meta.getAttribute('data-apex-orig-zoom-vp') || 'width=device-width, initial-scale=1.0';
                        if (meta.getAttribute('content') !== orig) {
                            meta.setAttribute('content', orig);
                        }
                        meta.removeAttribute('data-apex-orig-zoom-vp');
                    }
                } catch (e) {}
            })();
            """.trimIndent()
        }
        webView.evaluateJavascript(script, null)
    }

    /**
     * Enables keyboard caret focus indicators and arrow/selection browsing helpers when
     * caret browsing mode is active.
     */
    fun applyCaretBrowsing(webView: WebView, enabled: Boolean) {
        val script = if (enabled) {
            """
            (function() {
                try {
                    if (!document.getElementById('apex-caret-browsing-style')) {
                        var style = document.createElement('style');
                        style.id = 'apex-caret-browsing-style';
                        style.textContent = 'body *:focus { outline: 2px solid #1a73e8 !important; outline-offset: 2px !important; }';
                        (document.head || document.documentElement).appendChild(style);
                    }
                    document.documentElement.setAttribute('data-apex-caret-browsing', 'true');
                } catch (e) {}
            })();
            """.trimIndent()
        } else {
            """
            (function() {
                try {
                    var el = document.getElementById('apex-caret-browsing-style');
                    if (el && el.parentNode) el.parentNode.removeChild(el);
                    document.documentElement.removeAttribute('data-apex-caret-browsing');
                } catch (e) {}
            })();
            """.trimIndent()
        }
        webView.evaluateJavascript(script, null)
    }

    /**
     * Installs a spec-compliant JS guard on WebGL/WebGL2 rendering contexts so draw calls with
     * `count === 0` or `instanceCount/primcount === 0` short-circuit cleanly without hitting
     * Chromium's GLES2 `RENDER WARNING: Render count or primcount is 0` command decoder error,
     * and blocks hardware WebGL probing when `/dev/dri/renderD128` is absent.
     */
    fun installWebGlZeroCountGuard(webView: WebView, scriptGuardEnabled: Boolean) {
        if (scriptGuardEnabled) return
        val guardScript = """
            (function() {
                if (window.__apexWebGlZeroGuardInstalled) return;
                window.__apexWebGlZeroGuardInstalled = true;
                try {
                    var wrapDraw = function(proto, fnName, countIdx, instIdx) {
                        if (!proto || typeof proto[fnName] !== 'function') return;
                        var orig = proto[fnName];
                        proto[fnName] = function() {
                            var c = arguments[countIdx];
                            if (c === 0) return;
                            if (instIdx >= 0 && arguments[instIdx] === 0) return;
                            return orig.apply(this, arguments);
                        };
                    };

                    if (typeof WebGLRenderingContext !== 'undefined') {
                        var gl1 = WebGLRenderingContext.prototype;
                        wrapDraw(gl1, 'drawArrays', 2, -1);
                        wrapDraw(gl1, 'drawElements', 1, -1);
                    }
                    if (typeof WebGL2RenderingContext !== 'undefined') {
                        var gl2 = WebGL2RenderingContext.prototype;
                        wrapDraw(gl2, 'drawArrays', 2, -1);
                        wrapDraw(gl2, 'drawElements', 1, -1);
                        wrapDraw(gl2, 'drawRangeElements', 3, -1);
                        wrapDraw(gl2, 'drawArraysInstanced', 2, 3);
                        wrapDraw(gl2, 'drawElementsInstanced', 1, 4);
                    }

                    var wrapGetExtension = function(proto) {
                        if (!proto || typeof proto.getExtension !== 'function') return;
                        var origGetExt = proto.getExtension;
                        proto.getExtension = function(name) {
                            var ext = origGetExt.apply(this, arguments);
                            if (ext && typeof name === 'string' && !ext.__apexGuarded) {
                                try {
                                    if (name === 'ANGLE_instanced_arrays') {
                                        ext.__apexGuarded = true;
                                        wrapDraw(ext, 'drawArraysInstancedANGLE', 2, 3);
                                        wrapDraw(ext, 'drawElementsInstancedANGLE', 1, 4);
                                    } else if (name === 'WEBGL_multi_draw') {
                                        ext.__apexGuarded = true;
                                        var wrapMulti = function(fnName) {
                                            if (typeof ext[fnName] !== 'function') return;
                                            var origMulti = ext[fnName];
                                            ext[fnName] = function() {
                                                var drawCount = arguments[arguments.length - 1];
                                                if (drawCount === 0) return;
                                                return origMulti.apply(this, arguments);
                                            };
                                        };
                                        wrapMulti('multiDrawArraysWEBGL');
                                        wrapMulti('multiDrawElementsWEBGL');
                                        wrapMulti('multiDrawArraysInstancedWEBGL');
                                        wrapMulti('multiDrawElementsInstancedWEBGL');
                                    }
                                } catch (e) {}
                            }
                            return ext;
                        };
                    };
                    if (typeof WebGLRenderingContext !== 'undefined') {
                        wrapGetExtension(WebGLRenderingContext.prototype);
                    }
                    if (typeof WebGL2RenderingContext !== 'undefined') {
                        wrapGetExtension(WebGL2RenderingContext.prototype);
                    }
                } catch (e) {}
            })();
        """.trimIndent()
        webView.evaluateJavascript(guardScript, null)
    }

    /**
     * Discovers high-probability next-navigation links in the active WebView DOM
     * and injects lightweight `<link rel="dns-prefetch">` + `<link rel="prerender">` hints.
     */
    fun preloadLikelyNextPages(
        webView: WebView,
        enabled: Boolean,
        onPreloaded: (List<PreloadCandidate>) -> Unit
    ) {
        if (!enabled) {
            onPreloaded(emptyList())
            return
        }

        val preloadScript = """
            (function() {
                try {
                    const anchors = Array.from(document.querySelectorAll('a[href]'))
                        .filter(a => {
                            const h = a.getAttribute('href') || '';
                            const text = (a.innerText || '').trim();
                            return text.length > 3 && (h.startsWith('http') || h.startsWith('/') || h.startsWith('apex://'));
                        })
                        .slice(0, 4);
                    const out = [];
                    anchors.forEach((a, idx) => {
                        const resolved = a.href || a.getAttribute('href');
                        if (resolved && resolved.startsWith('http')) {
                            const link = document.createElement('link');
                            link.rel = 'dns-prefetch';
                            link.href = resolved;
                            document.head.appendChild(link);
                        }
                        out.push({
                            title: (a.innerText || '').trim().slice(0, 56),
                            url: resolved,
                            confidence: Math.max(68, 94 - (idx * 7))
                        });
                    });
                    return JSON.stringify(out);
                } catch (e) {
                    return "[]";
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(preloadScript) { rawJson ->
            val parsed = parsePreloadJson(rawJson)
            onPreloaded(parsed)
        }
    }

    private fun parsePreloadJson(raw: String?): List<PreloadCandidate> {
        if (raw.isNullOrBlank() || raw == "null") return emptyList()
        return runCatching {
            val unquoted = if (raw.startsWith("\"") && raw.endsWith("\"")) {
                org.json.JSONTokener(raw).nextValue().toString()
            } else {
                raw
            }
            val array = JSONArray(unquoted)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        PreloadCandidate(
                            title = obj.optString("title", "Next Page"),
                            url = obj.optString("url", ""),
                            confidencePercent = obj.optInt("confidence", 82)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
