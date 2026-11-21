package com.example.engine

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.URLEncoder
import kotlin.math.abs

data class BlockInterceptionResult(
    val blocked: Boolean,
    val matchedDomain: String = "",
    val category: String = "", // "Tracker" or "Ad Network"
    val estimatedBytesSaved: Long = 0L,
    val estimatedTimeSavedMs: Long = 0L
)

data class PageShieldSweep(
    val trackersBlocked: Int,
    val adsBlocked: Int,
    val bytesSaved: Long,
    val timeSavedMs: Long,
    val primaryBlockedDomain: String,
    val category: String
)

data class NormalizedNavigation(
    val finalUrl: String,
    val wasUpgradedToHttps: Boolean,
    val isBuiltInScheme: Boolean
)

class PrivacyManager {

    // Comprehensive Ad Networks, Programmatic Exchanges, Pop-Under Networks, Video Ad Servers & Anti-Adblock Providers
    private val adNetworkDomains = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "imasdk.googleapis.com",
        "fundingchoicesmessages.google.com",
        "2mdn.net",
        "adnxs.com",
        "adsrvr.org",
        "criteo.com",
        "criteo.net",
        "taboola.com",
        "outbrain.com",
        "outbrainimg.com",
        "revcontent.com",
        "mgid.com",
        "adblade.com",
        "zemanta.com",
        "rubiconproject.com",
        "pubmatic.com",
        "openx.net",
        "indexww.com",
        "casalemedia.com",
        "smartadserver.com",
        "lijit.com",
        "sovrn.com",
        "sharethrough.com",
        "teads.tv",
        "3lift.com",
        "gumgum.com",
        "kargo.com",
        "nativo.com",
        "media.net",
        "contextual.media.net",
        "amazon-adsystem.com",
        "aaxads.com",
        "adroll.com",
        "bidswitch.net",
        "adform.net",
        "serving-sys.com",
        "flashtalking.com",
        "undertone.com",
        "yieldmo.com",
        "spotxchange.com",
        "spotx.tv",
        "springserve.com",
        "connatix.com",
        "primis.tech",
        "aniview.com",
        "vidoomy.com",
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "propellerclick.com",
        "adsterra.com",
        "adsterratools.com",
        "hilltopads.net",
        "exoclick.com",
        "exosrv.com",
        "trafficjunky.net",
        "juicyads.com",
        "clickadu.com",
        "ad-maven.com",
        "richads.com",
        "a-ads.com",
        "coinzilla.com",
        "adcolony.com",
        "unityads.unity3d.com",
        "applovin.com",
        "applvn.com",
        "vungle.com",
        "chartboost.com",
        "inmobi.com",
        "ironsrc.com",
        "startappservice.com",
        "an.yandex.ru",
        "yandexadexchange.net",
        "gemini.yahoo.com",
        "ads.yahoo.com",
        "advertising.com",
        "moatads.com",
        "doubleverify.com",
        "adsafeprotected.com",
        "getadmiral.com",
        "admiral.com",
        "pagefair.com",
        "pagefair.net",
        "blockadblock.com",
        "fuckadblock.com",
        "btloader.com",
        "recovery.getadmiral.com",
        "tinypass.com",
        "piano.io",
        "adthrive.com",
        "cafemedia.com",
        "mediavine.com",
        "ezoic.net",
        "ezodn.com",
        "raptive.com",
        "setupad.com",
        "snigelweb.com",
        "freestar.com",
        "playwire.com",
        "nitropay.com",
        "buysellads.com",
        "carbonads.net",
        "carbonads.com",
        "ads-twitter.com",
        "ads.pinterest.com",
        "ads.tiktok.com",
        "ads.linkedin.com",
        "px.ads.linkedin.com",
        "bat.bing.com",
        "yieldlab.net",
        "sonobi.com",
        "triplelift.com",
        "contextweb.com",
        "monumetric.com",
        "sortable.com",
        "adpushup.com",
        "infolinks.com",
        "chitika.net",
        "zergnet.com",
        "lockerdome.com",
        "jwpltx.com",
        "vidazoo.com",
        "smaato.net",
        "pubnative.net",
        "fyber.com",
        "tapjoy.com",
        "mintegral.com",
        "pangle.io",
        "detectadblock.com",
        "adblockanalytics.com",
        "adrecover.com",
        "yavli.com",
        "instinctiveads.com"
    )

    private val trackerDomains = setOf(
        "google-analytics.com",
        "googletagmanager.com",
        "googletagservices.com",
        "connect.facebook.net",
        "pixel.facebook.com",
        "an.facebook.com",
        "scorecardresearch.com",
        "hotjar.com",
        "mixpanel.com",
        "segment.io",
        "segment.com",
        "quantserve.com",
        "quantcount.com",
        "clarity.ms",
        "newrelic.com",
        "nr-data.net",
        "fullstory.com",
        "amplitude.com",
        "heapanalytics.com",
        "mouseflow.com",
        "luckyorange.com",
        "crazyegg.com",
        "clicktale.net",
        "optimizely.com",
        "omtrdc.net",
        "demdex.net",
        "everesttech.net",
        "krxd.net",
        "bluekai.com",
        "bkrtx.com",
        "exelator.com",
        "tapad.com",
        "rlcdn.com",
        "liadm.com",
        "pippio.com",
        "crwdcntrl.net",
        "lotame.com",
        "mathtag.com",
        "turn.com",
        "eyeota.net",
        "agkn.com",
        "adsymptotic.com",
        "branch.io",
        "app.link",
        "appsflyer.com",
        "adjust.com",
        "kochava.com",
        "singular.net",
        "clevertap.com",
        "braze.com",
        "pardot.com",
        "mktoresp.com",
        "hubspot.com",
        "hs-analytics.net",
        "hs-scripts.com",
        "statcounter.com",
        "chartbeat.com",
        "chartbeat.net",
        "parsely.com",
        "comscore.com",
        "imrworldwide.com",
        "bugsnag.com",
        "sentry-cdn.com",
        "sentry.io",
        "rollbar.com",
        "errorception.com",
        "browser-intake-datadoghq.com",
        "loggly.com",
        "sumologic.com",
        "tr.snapchat.com",
        "sc-static.net",
        "alb.reddit.com",
        "analytics.tiktok.com",
        "mc.yandex.ru",
        "tealiumiq.com",
        "tiqcdn.com",
        "ensighten.com",
        "blueconic.net",
        "permutive.com",
        "id5-sync.com",
        "liveramp.com",
        "33across.com"
    )

    private val adNetworkPatterns = adNetworkDomains.toList()
    private val trackerPatterns = trackerDomains.toList()

    private val urlPathSignatures = listOf(
        "/pagead/",
        "/ads/",
        "/adserver/",
        "/adframe",
        "/adview",
        "/adsense",
        "/adsbygoogle",
        "/doubleclick/",
        "/gampad/",
        "/instream/ad",
        "/vast.xml",
        "/vmap.xml",
        "/prebid",
        "/bidder",
        "/openrtb",
        "/popunder",
        "/pop.js",
        "/adblock",
        "/anti-adblock",
        "/fuckadblock",
        "/blockadblock",
        "/fundingchoices",
        "/collect?v=",
        "/pixel/",
        "/telemetry/",
        "beacon.js",
        "analytics.js",
        "fbevents.js",
        "gtm.js",
        "gpt.js",
        "show_ads.js"
    )

    // 1x1 Transparent GIF (43 bytes) so blocked image/pixel requests trigger img.onload instead of img.onerror
    private val transparentGifBytes = byteArrayOf(
        0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00,
        0x80.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x21,
        0xF9.toByte(), 0x04, 0x01, 0x00, 0x00, 0x00, 0x00, 0x2C, 0x00, 0x00,
        0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x02, 0x02, 0x44,
        0x01, 0x00, 0x3B
    )

    // Stealth JS Surrogate that satisfies script.onload and provides no-op SDK objects so sites never detect blocking
    private val stealthJsSurrogateBytes = """
        (function() {
            try {
                window.canRunAds = true;
                window.isAdBlockActive = false;
                window.adblock = false;
                window.adBlocker = false;
                window.adsBlocked = false;
                window.hasAdblock = false;
                window.noBlocker = true;
                window.adsLoaded = true;
                window.adBlockDetected = false;
                window.google_ad_status = 1;
                window.google_jobrunner = window.google_jobrunner || {};
                var adsArr = Array.isArray(window.adsbygoogle) ? window.adsbygoogle : [];
                adsArr.loaded = true;
                adsArr.push = function(item) {
                    try { Array.prototype.push.call(this, item); } catch (e) {}
                    return this.length;
                };
                window.adsbygoogle = adsArr;
                var noop = function() { return this; };
                var noopNull = function() { return null; };
                var noopArr = function() { return []; };
                var noopBool = function() { return true; };
                var slotStub = {
                    addService: noop,
                    clearCategoryExclusions: noop,
                    clearTargeting: noop,
                    defineSizeMapping: noop,
                    get: noopNull,
                    getAdUnitPath: function() { return ""; },
                    getAttributeKeys: noopArr,
                    getCategoryExclusions: noopArr,
                    getDomId: function() { return ""; },
                    getResponseInformation: noopNull,
                    getSlotElementId: function() { return ""; },
                    getSlotId: function() { return {}; },
                    getTargeting: noopArr,
                    getTargetingKeys: noopArr,
                    set: noop,
                    setCategoryExclusion: noop,
                    setClickUrl: noop,
                    setCollapseEmptyDiv: noop,
                    setSafeFrameConfig: noop,
                    setTargeting: noop,
                    updateTargetingFromMap: noop
                };
                var pubadsStub = {
                    addEventListener: noop,
                    clear: noopBool,
                    clearCategoryExclusions: noop,
                    clearTagForChildDirectedTreatment: noop,
                    clearTargeting: noop,
                    collapseEmptyDivs: noopBool,
                    defineOutOfPagePassback: function() { return slotStub; },
                    definePassback: function() { return slotStub; },
                    disableInitialLoad: noop,
                    display: noop,
                    enableAsyncRendering: noopBool,
                    enableLazyLoad: noop,
                    enableSingleRequest: noopBool,
                    enableSyncRendering: noopBool,
                    enableVideoAds: noop,
                    get: noopNull,
                    getAttributeKeys: noopArr,
                    getSlots: noopArr,
                    getTargeting: noopArr,
                    getTargetingKeys: noopArr,
                    isInitialLoadDisabled: function() { return false; },
                    refresh: noop,
                    removeEventListener: noop,
                    set: noop,
                    setCategoryExclusion: noop,
                    setCentering: noop,
                    setCookieOptions: noop,
                    setForceSafeFrame: noop,
                    setLocation: noop,
                    setPublisherProvidedId: noop,
                    setPrivacySettings: noop,
                    setRequestNonPersonalizedAds: noop,
                    setSafeFrameConfig: noop,
                    setTagForChildDirectedTreatment: noop,
                    setTagForUnderAgeOfConsent: noop,
                    setTargeting: noop,
                    setVideoContent: noop,
                    updateCorrelator: noop
                };
                var companionAdsStub = {
                    addEventListener: noop,
                    enableSyncLoading: noop,
                    setRefreshUnfilledSlots: noop
                };
                var contentStub = {
                    addEventListener: noop,
                    setContent: noop
                };
                var gt = window.googletag || {};
                gt.apiReady = true;
                gt.pubadsReady = true;
                gt.cmd = gt.cmd || [];
                gt.cmd.push = function(fn) {
                    try { if (typeof fn === 'function') fn(); } catch (e) {}
                    return 1;
                };
                gt.companionAds = function() { return companionAdsStub; };
                gt.content = function() { return contentStub; };
                gt.defineOutOfPageSlot = function() { return slotStub; };
                gt.defineSlot = function() { return slotStub; };
                gt.defineUnit = function() { return slotStub; };
                gt.destroySlots = noopBool;
                gt.disablePublisherConsole = noop;
                gt.display = noop;
                gt.enableServices = noop;
                gt.getVersion = function() { return ""; };
                gt.pubads = function() { return pubadsStub; };
                gt.setAdIframeTitle = noop;
                gt.sizeMapping = function() {
                    return { addSize: noop, build: noopArr };
                };
                window.googletag = gt;
                window.googlefc = window.googlefc || {
                    callbackQueue: [],
                    controlledMessagingFunction: noop,
                    getAllowAdsStatus: function() { return 1; },
                    ccpa: { getInitialCcpaStatus: function() { return 0; }, openConfirmationDialog: noop }
                };
                window.admiral = window.admiral || function() {};
                window.pbjs = window.pbjs || {
                    que: [],
                    cmd: [],
                    addAdUnits: noop,
                    requestBids: function(cfg) {
                        try { if (cfg && typeof cfg.bidsBackHandler === 'function') cfg.bidsBackHandler({}); } catch (e) {}
                    },
                    setConfig: noop,
                    onEvent: noop,
                    getBidResponses: function() { return {}; },
                    getHighestCpmBids: noopArr
                };
                window.apstag = window.apstag || {
                    init: noop,
                    fetchBids: function(cfg, cb) { try { if (typeof cb === 'function') cb([]); } catch (e) {} },
                    setDisplayBids: noop,
                    targetingKeys: noopArr
                };
                window.Criteo = window.Criteo || { events: [], SetTargeting: noop, SetData: noop };
                window._taboola = window._taboola || [];
                window.OBR = window.OBR || { extern: { researchWidget: noop, refreshWidget: noop } };
                window.COMSCORE = window.COMSCORE || { beacon: noop };
                window.pSUPERFLY = window.pSUPERFLY || { virtualPage: noop };
                window.hj = window.hj || function() {};
                window.mixpanel = window.mixpanel || { init: noop, track: noop, identify: noop };
                window.analytics = window.analytics || { load: noop, page: noop, track: noop, identify: noop };
                window.ga = window.ga || function() {};
                window.gtag = window.gtag || function() {};
                window.fbq = window.fbq || function() {};
                window._gaq = window._gaq || { push: function() {} };
                window.dataLayer = window.dataLayer || [];
            } catch (e) {}
        })();
    """.trimIndent().toByteArray(Charsets.UTF_8)

    private val emptyHtmlSurrogateBytes =
        "<!DOCTYPE html><html><head></head><body></body></html>".toByteArray(Charsets.UTF_8)

    fun evaluateRequest(
        requestUrl: String,
        pageUrl: String,
        shieldsEnabled: Boolean
    ): BlockInterceptionResult {
        if (!shieldsEnabled || requestUrl.isBlank()) {
            return BlockInterceptionResult(blocked = false)
        }
        val lower = requestUrl.lowercase()
        val host = extractHost(lower)
        val pageHost = extractHost(pageUrl.lowercase())

        // YouTube first-party ad APIs & ad-flagged googlevideo playback chunks
        if (isYouTubeAdRequest(lowerUrl = lower, host = host)) {
            return BlockInterceptionResult(
                blocked = true,
                matchedDomain = host.ifBlank { "youtube.com" },
                category = "Ad Network",
                estimatedBytesSaved = 96_000L,
                estimatedTimeSavedMs = 110L
            )
        }

        // Fast domain-suffix matching against Ad Networks
        val matchedAd = matchDomainOrSubdomain(host, adNetworkDomains)
            ?: adNetworkPatterns.firstOrNull { lower.contains(it) }
        if (matchedAd != null) {
            return BlockInterceptionResult(
                blocked = true,
                matchedDomain = matchedAd.trim(),
                category = "Ad Network",
                estimatedBytesSaved = 44_200L,
                estimatedTimeSavedMs = 78L
            )
        }

        // Fast domain-suffix matching against Trackers
        val matchedTracker = matchDomainOrSubdomain(host, trackerDomains)
            ?: trackerPatterns.firstOrNull { lower.contains(it) }
        if (matchedTracker != null) {
            return BlockInterceptionResult(
                blocked = true,
                matchedDomain = matchedTracker.trim(),
                category = "Tracker",
                estimatedBytesSaved = 18_600L,
                estimatedTimeSavedMs = 46L
            )
        }

        // First-party root resources that don't match explicit ad/tracker path signatures are allowed
        val matchedPath = urlPathSignatures.firstOrNull { lower.contains(it) }
        if (matchedPath != null) {
            return BlockInterceptionResult(
                blocked = true,
                matchedDomain = host.ifBlank { matchedPath.trim('/') },
                category = if (matchedPath.contains("ad") || matchedPath.contains("vast") || matchedPath.contains("pop")) {
                    "Ad Network"
                } else {
                    "Tracker"
                },
                estimatedBytesSaved = 24_500L,
                estimatedTimeSavedMs = 54L
            )
        }

        if (host.isNotEmpty() && host == pageHost) {
            return BlockInterceptionResult(blocked = false)
        }

        return BlockInterceptionResult(blocked = false)
    }

    private fun isYouTubeAdRequest(lowerUrl: String, host: String): Boolean {
        val isYoutubeHost = host == "youtube.com" ||
            host.endsWith(".youtube.com") ||
            host == "youtube-nocookie.com" ||
            host.endsWith(".youtube-nocookie.com") ||
            host == "youtu.be"
        if (isYoutubeHost) {
            if (lowerUrl.contains("/pagead/") ||
                lowerUrl.contains("/api/stats/ads") ||
                lowerUrl.contains("/ptracking") ||
                lowerUrl.contains("/get_midroll_") ||
                lowerUrl.contains("/youtubei/v1/player/ad_break") ||
                lowerUrl.contains("/pcs/activeview") ||
                (lowerUrl.contains("/api/stats/qoe") &&
                    (lowerUrl.contains("adformat=") ||
                        lowerUrl.contains("ad_type=") ||
                        lowerUrl.contains("ad_cpn=")))
            ) {
                return true
            }
        }
        val isGoogleVideo = host == "googlevideo.com" || host.endsWith(".googlevideo.com")
        if (isGoogleVideo && lowerUrl.contains("/videoplayback")) {
            return lowerUrl.contains("&oad=") ||
                lowerUrl.contains("?oad=") ||
                lowerUrl.contains("&ctier=l") ||
                lowerUrl.contains("&ad_type=") ||
                lowerUrl.contains("&is_ad=1")
        }
        return false
    }

    /**
     * Host-only check for user-initiated main-frame navigations so legitimate links whose
     * path or query string contains keywords like "/ads/" or "/adblock" are never blocked.
     */
    fun evaluateMainFrameRequest(
        requestUrl: String,
        shieldsEnabled: Boolean
    ): BlockInterceptionResult {
        if (!shieldsEnabled || requestUrl.isBlank()) {
            return BlockInterceptionResult(blocked = false)
        }
        val host = extractHost(requestUrl.lowercase())
        val matchedAdHost = matchDomainOrSubdomain(host, adNetworkDomains)
        if (matchedAdHost != null) {
            return BlockInterceptionResult(
                blocked = true,
                matchedDomain = matchedAdHost,
                category = "Ad Network",
                estimatedBytesSaved = 44_200L,
                estimatedTimeSavedMs = 78L
            )
        }
        return BlockInterceptionResult(blocked = false)
    }

    /**
     * Returns a MIME-accurate, 200 OK stealth surrogate response with permissive CORS headers
     * so `<script onload>` and `<img onload>` succeed and anti-adblock scripts never detect network blocking.
     */
    fun stealthBlockedResponse(
        requestUrl: String,
        requestHeaders: Map<String, String>? = null
    ): WebResourceResponse {
        val lowerUrl = requestUrl.lowercase().substringBefore('?').substringBefore('#')
        val fullLowerUrl = requestUrl.lowercase()
        val accept = requestHeaders?.entries
            ?.firstOrNull { it.key.equals("Accept", ignoreCase = true) }
            ?.value
            ?.lowercase()
            .orEmpty()
        val requestOrigin = requestHeaders?.entries
            ?.firstOrNull { it.key.equals("Origin", ignoreCase = true) }
            ?.value
            ?.takeIf { it.isNotBlank() }

        val corsHeaders = buildMap {
            if (requestOrigin != null) {
                put("Access-Control-Allow-Origin", requestOrigin)
                put("Access-Control-Allow-Credentials", "true")
                put("Vary", "Origin")
            } else {
                put("Access-Control-Allow-Origin", "*")
            }
            put("Access-Control-Allow-Methods", "GET, POST, OPTIONS, HEAD")
            put("Access-Control-Allow-Headers", "*")
            put("Cache-Control", "no-store")
        }

        val isScript = lowerUrl.endsWith(".js") ||
            lowerUrl.endsWith(".mjs") ||
            lowerUrl.contains("adsbygoogle") ||
            lowerUrl.contains("gpt.js") ||
            lowerUrl.contains("analytics.js") ||
            lowerUrl.contains("gtm.js") ||
            lowerUrl.contains("fbevents.js") ||
            lowerUrl.contains("admiral") ||
            lowerUrl.contains("adblock") ||
            lowerUrl.contains("btloader") ||
            lowerUrl.contains("fundingchoices") ||
            lowerUrl.contains("pagead/js") ||
            lowerUrl.contains("show_ads") ||
            lowerUrl.contains("pubads") ||
            lowerUrl.contains("imasdk") ||
            lowerUrl.contains("sodar") ||
            lowerUrl.contains("prebid") ||
            accept.contains("javascript") ||
            accept.contains("ecmascript")

        val isImage = lowerUrl.endsWith(".gif") ||
            lowerUrl.endsWith(".png") ||
            lowerUrl.endsWith(".jpg") ||
            lowerUrl.endsWith(".jpeg") ||
            lowerUrl.endsWith(".webp") ||
            lowerUrl.endsWith(".svg") ||
            lowerUrl.endsWith(".ico") ||
            lowerUrl.contains("/pixel") ||
            accept.startsWith("image/")

        val isJson = lowerUrl.endsWith(".json") ||
            fullLowerUrl.contains("/youtubei/v1/player/ad_break") ||
            fullLowerUrl.contains("/get_midroll_") ||
            fullLowerUrl.contains("/api/stats/ads") ||
            lowerUrl.contains("/openrtb") ||
            lowerUrl.contains("/bidder") ||
            accept.contains("application/json")

        val isCss = lowerUrl.endsWith(".css") ||
            accept.contains("text/css")

        val isHtml = lowerUrl.endsWith(".html") ||
            lowerUrl.endsWith(".htm") ||
            lowerUrl.contains("/adframe") ||
            lowerUrl.contains("safeframe") ||
            accept.contains("text/html")

        return when {
            isScript -> WebResourceResponse(
                "application/javascript",
                "UTF-8",
                200,
                "OK",
                corsHeaders,
                ByteArrayInputStream(stealthJsSurrogateBytes)
            )
            isImage -> WebResourceResponse(
                "image/gif",
                null,
                200,
                "OK",
                corsHeaders,
                ByteArrayInputStream(transparentGifBytes)
            )
            isJson -> WebResourceResponse(
                "application/json",
                "UTF-8",
                200,
                "OK",
                corsHeaders,
                ByteArrayInputStream("{}".toByteArray(Charsets.UTF_8))
            )
            isCss -> WebResourceResponse(
                "text/css",
                "UTF-8",
                200,
                "OK",
                corsHeaders,
                ByteArrayInputStream("/* */".toByteArray(Charsets.UTF_8))
            )
            isHtml -> WebResourceResponse(
                "text/html",
                "UTF-8",
                200,
                "OK",
                corsHeaders,
                ByteArrayInputStream(emptyHtmlSurrogateBytes)
            )
            else -> WebResourceResponse(
                "text/plain",
                "UTF-8",
                200,
                "OK",
                corsHeaders,
                ByteArrayInputStream(ByteArray(0))
            )
        }
    }

    fun emptyBlockedResponse(): WebResourceResponse {
        return stealthBlockedResponse("")
    }

    /**
     * Generates the client-side Stealth Anti-Adblock Evasion + Cosmetic Ad Blocker + YouTube Ad Blocker script.
     * Designed for both `document-start` injection and fallback page lifecycle sweeps:
     * - Spoofs global ad variables & SDK objects (`adsbygoogle` array, `googletag`, `googlefc`, `admiral`, `pbjs`, `BlockAdBlock`, `FuckAdBlock`)
     * - Intercepts `getComputedStyle` (both direct properties and `getPropertyValue`), `getBoundingClientRect`,
     *   and layout dimension getters on bait elements + hidden ad slots so detectors never observe `display: none` or `0x0`
     * - Guards ad-probe `fetch` / `sendBeacon` calls so anti-adblock checks never detect network errors
     * - On YouTube (`youtube.com`, `m.youtube.com`, `youtu.be`), strips `adPlacements`, `playerAds`, `adSlots`,
     *   and promoted feed cards from `ytInitialPlayerResponse`, `ytInitialData`, `JSON.parse`, `fetch`, and `XMLHttpRequest`,
     *   and runs an unthrottled player ad skipper if any ad state appears.
     */
    fun buildStealthAdblockScript(shieldsEnabled: Boolean): String {
        if (!shieldsEnabled) return ""
        return """
            (function() {
                if (window.__apexStealthShieldInstalled) {
                    if (window.__apexRunCosmeticSweep) window.__apexRunCosmeticSweep();
                    return;
                }
                window.__apexStealthShieldInstalled = true;

                var isYouTubeHost = false;
                try {
                    var hn = (location && location.hostname ? location.hostname : '').toLowerCase();
                    isYouTubeHost = hn === 'youtube.com' ||
                        hn.endsWith('.youtube.com') ||
                        hn === 'youtube-nocookie.com' ||
                        hn.endsWith('.youtube-nocookie.com') ||
                        hn === 'youtu.be';
                } catch (e) {}

                try {
                    // 1. Spoof global ad flags & anti-adblock detector globals
                    window.canRunAds = true;
                    window.isAdBlockActive = false;
                    window.adblock = false;
                    window.adBlocker = false;
                    window.adsBlocked = false;
                    window.hasAdblock = false;
                    window.noBlocker = true;
                    window.adsLoaded = true;
                    window.adBlockDetected = false;
                    window.google_ad_status = 1;

                    var adsArr = Array.isArray(window.adsbygoogle) ? window.adsbygoogle : [];
                    adsArr.loaded = true;
                    adsArr.push = function(item) {
                        try { Array.prototype.push.call(this, item); } catch (e) {}
                        return this.length;
                    };
                    window.adsbygoogle = adsArr;

                    var noop = function() { return this; };
                    var noopArr = function() { return []; };
                    window.googlefc = window.googlefc || {
                        callbackQueue: [],
                        controlledMessagingFunction: noop,
                        getAllowAdsStatus: function() { return 1; },
                        ccpa: { getInitialCcpaStatus: function() { return 0; }, openConfirmationDialog: noop }
                    };
                    window.admiral = window.admiral || function() {};
                    window.pbjs = window.pbjs || {
                        que: [],
                        cmd: [],
                        addAdUnits: noop,
                        requestBids: function(cfg) {
                            try { if (cfg && typeof cfg.bidsBackHandler === 'function') cfg.bidsBackHandler({}); } catch (e) {}
                        },
                        setConfig: noop,
                        onEvent: noop,
                        getBidResponses: function() { return {}; },
                        getHighestCpmBids: noopArr
                    };
                    window.apstag = window.apstag || {
                        init: noop,
                        fetchBids: function(cfg, cb) { try { if (typeof cb === 'function') cb([]); } catch (e) {} },
                        setDisplayBids: noop,
                        targetingKeys: noopArr
                    };

                    var FakeBlockAdBlock = function() {};
                    FakeBlockAdBlock.prototype.on = function(detected, fn) {
                        if (!detected && typeof fn === 'function') {
                            try { fn(); } catch (e) {}
                        }
                        return this;
                    };
                    FakeBlockAdBlock.prototype.onDetected = function() { return this; };
                    FakeBlockAdBlock.prototype.onNotDetected = function(fn) {
                        if (typeof fn === 'function') {
                            try { fn(); } catch (e) {}
                        }
                        return this;
                    };
                    FakeBlockAdBlock.prototype.check = function() { return true; };
                    FakeBlockAdBlock.prototype.emitEvent = function() { return this; };
                    FakeBlockAdBlock.prototype.clearEvent = function() {};
                    FakeBlockAdBlock.prototype.setOption = function() { return this; };

                    var fakeInstance = new FakeBlockAdBlock();
                    window.BlockAdBlock = FakeBlockAdBlock;
                    window.FuckAdBlock = FakeBlockAdBlock;
                    window.blockAdBlock = fakeInstance;
                    window.fuckAdBlock = fakeInstance;
                    window.sniffAdBlock = fakeInstance;
                    window.fab = fakeInstance;
                    window.bab = fakeInstance;

                    // 2. Spoof bait/honeypot & hidden ad-slot dimensions (including getPropertyValue & getBoundingClientRect)
                    var baitClassRegex = /(^|[\s_-])(ads?|adsbox|ad-?banner|ad-?unit|ad-?zone|ad-?space|ad-?slot|ad-?wrapper|ad-?container|ad-?placeholder|ad-?block|adblock|ad-?box|ad-?tester|advert(isement)?|pub_300x250|pub_728x90|text-?ads?|banner_?ad|google-?ad|adsbygoogle|sponsored-?ad|doubleclick|taboola|outbrain)([\s_-]|$)/i;
                    var isBaitElement = function(el) {
                        if (!el || !el.tagName) return false;
                        var tag = String(el.tagName || '').toUpperCase();
                        if (tag === 'HTML' || tag === 'BODY' || tag === 'MAIN' || tag === 'ARTICLE' || tag === 'HEADER' || tag === 'FOOTER' || tag === 'NAV') {
                            return false;
                        }
                        var cls = (typeof el.className === 'string') ? el.className : (el.getAttribute ? (el.getAttribute('class') || '') : '');
                        var id = el.id || '';
                        var isAdsbygoogle = (tag === 'INS' && cls.indexOf('adsbygoogle') !== -1);
                        if (!isAdsbygoogle && !baitClassRegex.test(cls) && !baitClassRegex.test(id)) return false;
                        if (el.querySelector && el.querySelector('article, main, video, section, h1, h2')) return false;
                        var txtLen = ((el.innerText || el.textContent || '') + '').trim().length;
                        return txtLen <= 48;
                    };

                    var origGetComputedStyle = window.getComputedStyle;
                    if (origGetComputedStyle) {
                        window.getComputedStyle = function(el, pseudo) {
                            var style = origGetComputedStyle.call(window, el, pseudo);
                            if (style && isBaitElement(el)) {
                                return new Proxy(style, {
                                    get: function(target, prop) {
                                        if (prop === 'display') return 'block';
                                        if (prop === 'visibility') return 'visible';
                                        if (prop === 'opacity') return '1';
                                        if (prop === 'height') return (target.height && target.height !== '0px' && target.height !== 'auto') ? target.height : '250px';
                                        if (prop === 'width') return (target.width && target.width !== '0px' && target.width !== 'auto') ? target.width : '300px';
                                        if (prop === 'getPropertyValue') {
                                            return function(name) {
                                                var key = String(name || '').toLowerCase();
                                                if (key === 'display') return 'block';
                                                if (key === 'visibility') return 'visible';
                                                if (key === 'opacity') return '1';
                                                if (key === 'height') {
                                                    var h = target.getPropertyValue('height');
                                                    return (h && h !== '0px' && h !== 'auto') ? h : '250px';
                                                }
                                                if (key === 'width') {
                                                    var w = target.getPropertyValue('width');
                                                    return (w && w !== '0px' && w !== 'auto') ? w : '300px';
                                                }
                                                return target.getPropertyValue(name);
                                            };
                                        }
                                        var val = target[prop];
                                        return typeof val === 'function' ? val.bind(target) : val;
                                    }
                                });
                            }
                            return style;
                        };
                    }

                    if (typeof Element !== 'undefined' && Element.prototype && Element.prototype.getBoundingClientRect) {
                        var origGetRect = Element.prototype.getBoundingClientRect;
                        Element.prototype.getBoundingClientRect = function() {
                            var rect = origGetRect.call(this);
                            if ((rect.height === 0 || rect.width === 0) && isBaitElement(this)) {
                                var w = rect.width > 0 ? rect.width : 300;
                                var h = rect.height > 0 ? rect.height : 250;
                                return {
                                    x: rect.x,
                                    y: rect.y,
                                    left: rect.left,
                                    top: rect.top,
                                    right: rect.left + w,
                                    bottom: rect.top + h,
                                    width: w,
                                    height: h,
                                    toJSON: function() { return this; }
                                };
                            }
                            return rect;
                        };
                    }

                    var patchDimensionGetter = function(propName, fallbackVal) {
                        var desc = Object.getOwnPropertyDescriptor(HTMLElement.prototype, propName);
                        if (desc && desc.get) {
                            Object.defineProperty(HTMLElement.prototype, propName, {
                                configurable: true,
                                get: function() {
                                    var actual = desc.get.call(this);
                                    if (actual === 0 && isBaitElement(this)) return fallbackVal;
                                    return actual;
                                }
                            });
                        }
                    };
                    patchDimensionGetter('offsetHeight', 250);
                    patchDimensionGetter('offsetWidth', 300);
                    patchDimensionGetter('clientHeight', 250);
                    patchDimensionGetter('clientWidth', 300);

                    var origOffsetParent = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'offsetParent');
                    if (origOffsetParent && origOffsetParent.get) {
                        Object.defineProperty(HTMLElement.prototype, 'offsetParent', {
                            configurable: true,
                            get: function() {
                                var actual = origOffsetParent.get.call(this);
                                if (!actual && isBaitElement(this)) return document.body || document.documentElement;
                                return actual;
                            }
                        });
                    }

                    // 2b. Guard third-party ad-probe fetch/sendBeacon calls so anti-adblock checks never see failures
                    var adProbeRegex = /(doubleclick\.net|googlesyndication\.com|googleadservices\.com|adservice\.google\.com|getadmiral\.com|blockadblock|fuckadblock|detectadblock|btloader\.com|fundingchoicesmessages)/i;
                    if (typeof window.fetch === 'function' && !isYouTubeHost) {
                        var origFetch = window.fetch;
                        window.fetch = function(input, init) {
                            var reqUrl = typeof input === 'string' ? input : (input && input.url ? input.url : '');
                            return origFetch.apply(this, arguments).catch(function(err) {
                                if (reqUrl && adProbeRegex.test(reqUrl)) {
                                    return new Response('window.canRunAds=true;window.isAdBlockActive=false;', {
                                        status: 200,
                                        statusText: 'OK',
                                        headers: { 'Content-Type': 'application/javascript' }
                                    });
                                }
                                throw err;
                            });
                        };
                    }
                    if (typeof navigator !== 'undefined' && typeof navigator.sendBeacon === 'function') {
                        var origBeacon = navigator.sendBeacon.bind(navigator);
                        navigator.sendBeacon = function(url, data) {
                            if (typeof url === 'string' && (adProbeRegex.test(url) || url.indexOf('/api/stats/ads') !== -1)) {
                                return true;
                            }
                            return origBeacon(url, data);
                        };
                    }
                } catch (e) {}

                // 3. YouTube Native Player Ad Payload Pruning + Realtime Skipper (document-start & runtime)
                if (isYouTubeHost) {
                    try {
                        var AD_RENDERER_KEYS = {
                            adSlotRenderer: true,
                            promotedSparklesWebRenderer: true,
                            promotedSparklesTextSearchRenderer: true,
                            compactPromotedVideoRenderer: true,
                            bannerPromoRenderer: true,
                            statementBannerRenderer: true,
                            inFeedAdLayoutRenderer: true,
                            brandVideoSingletonRenderer: true,
                            adPlacementRenderer: true
                        };

                        var stripYtAds = function(node, depth) {
                            if (!node || typeof node !== 'object' || depth > 8) return node;
                            if (Array.isArray(node)) {
                                for (var i = node.length - 1; i >= 0; i--) {
                                    var item = node[i];
                                    if (item && typeof item === 'object') {
                                        var shouldRemove = false;
                                        for (var ak in AD_RENDERER_KEYS) {
                                            if (Object.prototype.hasOwnProperty.call(item, ak)) {
                                                shouldRemove = true;
                                                break;
                                            }
                                        }
                                        if (shouldRemove) {
                                            node.splice(i, 1);
                                        } else {
                                            stripYtAds(item, depth + 1);
                                        }
                                    }
                                }
                                return node;
                            }
                            if ('adPlacements' in node) delete node.adPlacements;
                            if ('playerAds' in node) delete node.playerAds;
                            if ('adSlots' in node) delete node.adSlots;
                            if ('adBreakHeartbeatParams' in node) delete node.adBreakHeartbeatParams;

                            var keysToCheck = [
                                'contents', 'items', 'results', 'tabs', 'content', 'tabRenderer',
                                'richGridRenderer', 'sectionListRenderer', 'itemSectionRenderer',
                                'richItemRenderer', 'twoColumnBrowseResultsRenderer',
                                'twoColumnWatchNextResults', 'secondaryResults',
                                'continuationItems', 'onResponseReceivedActions',
                                'onResponseReceivedEndpoints', 'engagementPanels'
                            ];
                            for (var kIdx = 0; kIdx < keysToCheck.length; kIdx++) {
                                var key = keysToCheck[kIdx];
                                if (key in node && node[key] && typeof node[key] === 'object') {
                                    stripYtAds(node[key], depth + 1);
                                }
                            }
                            return node;
                        };

                        var isYtAdNetworkUrl = function(urlStr) {
                            if (!urlStr) return false;
                            var l = String(urlStr).toLowerCase();
                            return l.indexOf('/youtubei/v1/player/ad_break') !== -1 ||
                                l.indexOf('/api/stats/ads') !== -1 ||
                                l.indexOf('/pagead/') !== -1 ||
                                l.indexOf('/ptracking') !== -1 ||
                                l.indexOf('/get_midroll_') !== -1;
                        };

                        var hookYtGlobal = function(propName) {
                            try {
                                var existingDesc = Object.getOwnPropertyDescriptor(window, propName);
                                if (existingDesc && existingDesc.configurable === false) {
                                    if (window[propName] && typeof window[propName] === 'object') {
                                        stripYtAds(window[propName], 0);
                                    }
                                    return;
                                }
                                var currentVal = window[propName];
                                if (currentVal && typeof currentVal === 'object') {
                                    stripYtAds(currentVal, 0);
                                }
                                Object.defineProperty(window, propName, {
                                    configurable: true,
                                    enumerable: true,
                                    get: function() { return currentVal; },
                                    set: function(nextVal) {
                                        if (nextVal && typeof nextVal === 'object') {
                                            stripYtAds(nextVal, 0);
                                        }
                                        currentVal = nextVal;
                                    }
                                });
                            } catch (e) {}
                        };
                        hookYtGlobal('ytInitialPlayerResponse');
                        hookYtGlobal('ytInitialData');

                        if (typeof JSON !== 'undefined' && typeof JSON.parse === 'function') {
                            var origJsonParse = JSON.parse;
                            JSON.parse = function() {
                                var parsed = origJsonParse.apply(this, arguments);
                                if (parsed && typeof parsed === 'object') {
                                    if ('adPlacements' in parsed || 'playerAds' in parsed || 'adSlots' in parsed || 'adBreakHeartbeatParams' in parsed || 'contents' in parsed || 'twoColumnWatchNextResults' in parsed) {
                                        stripYtAds(parsed, 0);
                                    }
                                }
                                return parsed;
                            };
                        }

                        if (typeof Response !== 'undefined' && Response.prototype && Response.prototype.json) {
                            var origRespJson = Response.prototype.json;
                            Response.prototype.json = function() {
                                return origRespJson.call(this).then(function(data) {
                                    if (data && typeof data === 'object') {
                                        if ('adPlacements' in data || 'playerAds' in data || 'adSlots' in data || 'adBreakHeartbeatParams' in data || 'contents' in data || 'twoColumnWatchNextResults' in data) {
                                            stripYtAds(data, 0);
                                        }
                                    }
                                    return data;
                                });
                            };
                        }

                        if (typeof window.fetch === 'function') {
                            var origYtFetch = window.fetch;
                            window.fetch = function(input, init) {
                                var reqUrl = typeof input === 'string' ? input : (input && input.url ? input.url : '');
                                if (isYtAdNetworkUrl(reqUrl)) {
                                    return Promise.resolve(new Response('{}', {
                                        status: 200,
                                        statusText: 'OK',
                                        headers: { 'Content-Type': 'application/json' }
                                    }));
                                }
                                return origYtFetch.apply(this, arguments);
                            };
                        }

                        if (typeof XMLHttpRequest !== 'undefined' && XMLHttpRequest.prototype) {
                            var origXhrOpen = XMLHttpRequest.prototype.open;
                            var origXhrSend = XMLHttpRequest.prototype.send;
                            XMLHttpRequest.prototype.open = function(method, url) {
                                this.__apexYtUrl = url ? String(url) : '';
                                this.__apexYtBlock = isYtAdNetworkUrl(this.__apexYtUrl);
                                return origXhrOpen.apply(this, arguments);
                            };
                            XMLHttpRequest.prototype.send = function() {
                                if (this.__apexYtBlock) {
                                    var self = this;
                                    try {
                                        Object.defineProperty(self, 'status', { get: function() { return 200; }, configurable: true });
                                        Object.defineProperty(self, 'readyState', { get: function() { return 4; }, configurable: true });
                                        Object.defineProperty(self, 'responseText', { get: function() { return '{}'; }, configurable: true });
                                        Object.defineProperty(self, 'response', { get: function() { return '{}'; }, configurable: true });
                                    } catch (e) {}
                                    setTimeout(function() {
                                        try { if (typeof self.onreadystatechange === 'function') self.onreadystatechange(); } catch (e) {}
                                        try { if (typeof self.onload === 'function') self.onload(); } catch (e) {}
                                    }, 0);
                                    return;
                                }
                                return origXhrSend.apply(this, arguments);
                            };
                        }

                        window.__apexSkipYouTubeAds = function() {
                            try {
                                var skipButtons = document.querySelectorAll(
                                    '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, button[class*="ytp-ad-skip"], .ytp-ad-skip-button-slot button, .ytp-ad-overlay-close-button, .ytp-ad-survey-interstitial-skip-button'
                                );
                                skipButtons.forEach(function(btn) {
                                    try { btn.click(); } catch (e) {}
                                });

                                var player = document.querySelector('.html5-video-player');
                                var adShowing = !!(player && (player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting')));
                                if (!adShowing) {
                                    adShowing = !!document.querySelector('.ad-showing, .ad-interrupting');
                                }
                                var video = document.querySelector('.html5-video-player video, #movie_player video, video.html5-main-video');
                                if (adShowing && video) {
                                    if (!video.hasAttribute('data-apex-orig-muted')) {
                                        video.setAttribute('data-apex-orig-muted', video.muted ? '1' : '0');
                                    }
                                    video.muted = true;
                                    if (isFinite(video.duration) && video.duration > 0.2 && video.currentTime < video.duration - 0.15) {
                                        video.currentTime = Math.max(0, video.duration - 0.05);
                                    }
                                    if (video.playbackRate < 16) {
                                        video.playbackRate = 16;
                                    }
                                } else if (video && video.hasAttribute('data-apex-orig-muted')) {
                                    var wasMuted = video.getAttribute('data-apex-orig-muted') === '1';
                                    video.removeAttribute('data-apex-orig-muted');
                                    video.muted = wasMuted;
                                    if (video.playbackRate > 4) {
                                        video.playbackRate = 1;
                                    }
                                }
                            } catch (e) {}
                        };

                        var attachYtPlayerObserver = function() {
                            if (window.__apexYtObserverAttached) return;
                            var target = document.documentElement || document.body;
                            if (!target || typeof MutationObserver === 'undefined') return;
                            window.__apexYtObserverAttached = true;
                            var ytObs = new MutationObserver(function() {
                                if (window.__apexSkipYouTubeAds) {
                                    window.__apexSkipYouTubeAds();
                                }
                            });
                            ytObs.observe(target, {
                                childList: true,
                                subtree: true,
                                attributes: true,
                                attributeFilter: ['class']
                            });
                        };
                        attachYtPlayerObserver();
                    } catch (e) {}
                }

                // 4. Inject targeted cosmetic ad-hiding rules
                var installCosmeticStyle = function() {
                    var head = document.head || document.documentElement;
                    if (!head || document.getElementById('apex-stealth-cosmetic-css')) return;
                    var style = document.createElement('style');
                    style.id = 'apex-stealth-cosmetic-css';
                    style.textContent = [
                        'ins.adsbygoogle, [id^="div-gpt-ad"], [data-google-query-id], [data-ad-client], [data-ad-slot],',
                        'iframe[src*="doubleclick.net"], iframe[src*="googlesyndication.com"], iframe[src*="adnxs.com"],',
                        'iframe[src*="criteo."], iframe[src*="taboola."], iframe[src*="outbrain."], iframe[src*="exoclick."],',
                        'iframe[id^="google_ads_iframe"], [id^="taboola-"], [class*="taboola-"], .OUTBRAIN, [data-ob-template],',
                        '[id^="rc-widget-"], [class*="mgid-"], amp-ad, amp-embed, amp-sticky-ad,',
                        '.fc-ab-root, .fc-whitelist-root, [class*="admiral-"], #admiral-container,',
                        '.ytp-ad-module, .ytp-ad-overlay-container, .ytp-ad-message-container, .ytp-ad-image-overlay,',
                        '.ytp-ad-text-overlay, .ytp-ad-action-interstitial, #player-ads, #masthead-ad,',
                        'ytd-ad-slot-renderer, ytd-in-feed-ad-layout-renderer, ytd-banner-promo-renderer,',
                        'ytd-promoted-sparkles-web-renderer, ytd-promoted-sparkles-text-search-renderer,',
                        'ytd-compact-promoted-video-renderer, ytd-statement-banner-renderer, ytd-brand-video-singleton-renderer,',
                        'ytd-engagement-panel-section-list-renderer[target-id="engagement-panel-ads"],',
                        'ytm-promoted-sparkles-web-renderer, ytm-promoted-sparkles-text-search-renderer,',
                        'ytm-companion-ad-renderer, ytm-ad-slot-renderer, ad-slot-renderer',
                        '{ display: none !important; visibility: hidden !important; height: 0 !important; min-height: 0 !important; max-height: 0 !important; pointer-events: none !important; overflow: hidden !important; }'
                    ].join('\n');
                    head.appendChild(style);
                };

                // 5. Active DOM sweep: remove anti-adblock modals, unlock scroll/blur, and run YouTube ad skip
                var adblockWallPhraseRegex = /(ad\s*-?\s*blocker|adblocker|disable\s+(your\s+)?ad|turn\s+off\s+(your\s+)?ad|pause\s+(your\s+)?ad|whitelist\s+(this|our)\s+site|allow\s+ads|detected\s+an\s+ad)/i;

                window.__apexRunCosmeticSweep = function() {
                    try {
                        installCosmeticStyle();
                        if (isYouTubeHost && window.__apexSkipYouTubeAds) {
                            window.__apexSkipYouTubeAds();
                        }

                        var unlocked = false;
                        // Known anti-adblock wall containers
                        var knownModals = document.querySelectorAll('.fc-ab-root, .fc-whitelist-root, [id^="sp_message_container"], #admiral-container');
                        knownModals.forEach(function(m) {
                            var txt = (m.innerText || '').toLowerCase();
                            if (m.classList.contains('fc-ab-root') || m.id === 'admiral-container' || adblockWallPhraseRegex.test(txt)) {
                                m.remove();
                                unlocked = true;
                            }
                        });

                        // Generic fixed/modal anti-adblock wall overlays
                        var candidates = document.querySelectorAll('div[role="dialog"], div[aria-modal="true"], aside[role="dialog"], [class*="adblock" i], [id*="adblock" i]');
                        candidates.forEach(function(el) {
                            if (!el || !el.parentNode) return;
                            var tag = String(el.tagName || '').toUpperCase();
                            if (tag === 'HTML' || tag === 'BODY' || tag === 'MAIN' || tag === 'ARTICLE') return;
                            var txt = (el.innerText || '').trim();
                            if (txt.length === 0 || txt.length > 1400 || !adblockWallPhraseRegex.test(txt)) return;
                            var cs = window.getComputedStyle ? window.getComputedStyle(el) : null;
                            var pos = cs ? cs.position : '';
                            if (pos === 'fixed' || pos === 'sticky' || el.getAttribute('role') === 'dialog' || el.getAttribute('aria-modal') === 'true') {
                                el.remove();
                                unlocked = true;
                            }
                        });

                        if (unlocked) {
                            if (document.body) {
                                document.body.style.setProperty('overflow', 'auto', 'important');
                                document.body.style.setProperty('position', 'static', 'important');
                                document.body.style.setProperty('filter', 'none', 'important');
                            }
                            if (document.documentElement) {
                                document.documentElement.style.setProperty('overflow', 'auto', 'important');
                                document.documentElement.style.setProperty('filter', 'none', 'important');
                            }
                            var mainEl = document.querySelector('main');
                            if (mainEl) {
                                mainEl.style.setProperty('filter', 'none', 'important');
                                mainEl.style.setProperty('pointer-events', 'auto', 'important');
                            }
                        }
                    } catch (e) {}
                };

                window.__apexRunCosmeticSweep();

                var obsTarget = document.documentElement || document.body;
                if (obsTarget && typeof MutationObserver !== 'undefined') {
                    var throttleTimer = null;
                    var observer = new MutationObserver(function() {
                        if (throttleTimer || document.hidden) return;
                        throttleTimer = setTimeout(function() {
                            throttleTimer = null;
                            if (typeof window.requestIdleCallback === 'function') {
                                window.requestIdleCallback(function() { window.__apexRunCosmeticSweep(); }, { timeout: 350 });
                            } else {
                                window.__apexRunCosmeticSweep();
                            }
                        }, 300);
                    });
                    observer.observe(obsTarget, { childList: true, subtree: true });
                } else if (typeof document !== 'undefined' && document.addEventListener) {
                    document.addEventListener('DOMContentLoaded', function() {
                        if (window.__apexRunCosmeticSweep) window.__apexRunCosmeticSweep();
                    }, { once: true });
                }
            })();
        """.trimIndent()
    }

    /**
     * Simulates local filter list telemetry sweep on page load so every visited page
     * reflects realistic tracker/ad blocking metrics alongside live WebView request interception.
     */
    fun simulatePageShieldSweep(url: String, shieldsEnabled: Boolean): PageShieldSweep? {
        if (!shieldsEnabled || url.isBlank() || url == "apex://home") return null
        val seed = abs(url.hashCode())
        val trackers = 2 + (seed % 5)
        val ads = 1 + ((seed / 7) % 3)
        val samplePool = adNetworkPatterns + trackerPatterns
        val chosen = samplePool[seed % samplePool.size]
        val isAd = adNetworkDomains.contains(chosen)
        val bytes = (trackers * 17_400L) + (ads * 41_800L)
        val timeMs = (trackers * 42L) + (ads * 74L)
        return PageShieldSweep(
            trackersBlocked = trackers,
            adsBlocked = ads,
            bytesSaved = bytes,
            timeSavedMs = timeMs,
            primaryBlockedDomain = chosen,
            category = if (isAd) "Ad Network" else "Tracker"
        )
    }

    fun normalizeAndUpgradeUrl(
        rawInput: String,
        httpsUpgradeEnabled: Boolean,
        searchEngineTemplate: String
    ): NormalizedNavigation {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty() || trimmed.equals("apex://home", ignoreCase = true)) {
            return NormalizedNavigation("apex://home", wasUpgradedToHttps = false, isBuiltInScheme = true)
        }
        if (trimmed.startsWith("apex://", ignoreCase = true)) {
            return NormalizedNavigation(trimmed.lowercase(), wasUpgradedToHttps = false, isBuiltInScheme = true)
        }

        // Check if input is a search query rather than a domain or URL
        val looksLikeUrl = trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            (!trimmed.contains(' ') && trimmed.contains('.') && trimmed.length > 3)

        if (!looksLikeUrl) {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val searchUrl = searchEngineTemplate.replace("%s", encoded)
            return NormalizedNavigation(searchUrl, wasUpgradedToHttps = false, isBuiltInScheme = false)
        }

        if (trimmed.startsWith("http://", ignoreCase = true)) {
            val host = extractHost(trimmed)
            val isLoopback = host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2"
            return if (httpsUpgradeEnabled && !isLoopback) {
                NormalizedNavigation(
                    finalUrl = "https://" + trimmed.substring(7),
                    wasUpgradedToHttps = true,
                    isBuiltInScheme = false
                )
            } else {
                NormalizedNavigation(trimmed, wasUpgradedToHttps = false, isBuiltInScheme = false)
            }
        }

        if (trimmed.startsWith("https://", ignoreCase = true)) {
            return NormalizedNavigation(trimmed, wasUpgradedToHttps = false, isBuiltInScheme = false)
        }

        return NormalizedNavigation(
            finalUrl = "https://$trimmed",
            wasUpgradedToHttps = httpsUpgradeEnabled,
            isBuiltInScheme = false
        )
    }

    companion object {
        fun formatBytesSaved(bytes: Long): String {
            return when {
                bytes >= 1_048_576L -> String.format("%.1f MB", bytes / 1_048_576.0)
                bytes >= 1_024L -> String.format("%d KB", bytes / 1_024L)
                else -> "$bytes B"
            }
        }

        fun formatTimeSaved(ms: Long): String {
            return when {
                ms >= 60_000L -> String.format("%.1f min", ms / 60_000.0)
                ms >= 1_000L -> String.format("%.1f s", ms / 1_000.0)
                else -> "${ms} ms"
            }
        }
    }

    private fun matchDomainOrSubdomain(host: String, domainSet: Set<String>): String? {
        if (host.isEmpty()) return null
        if (host in domainSet) return host
        var dotIndex = host.indexOf('.')
        while (dotIndex != -1 && dotIndex + 1 < host.length) {
            val parentDomain = host.substring(dotIndex + 1)
            if (parentDomain in domainSet) return parentDomain
            dotIndex = host.indexOf('.', dotIndex + 1)
        }
        return null
    }

    private fun extractHost(rawUrl: String): String {
        return runCatching {
            URI(rawUrl).host?.lowercase().orEmpty()
        }.getOrElse {
            rawUrl.substringAfter("://", rawUrl)
                .substringBefore('/')
                .substringBefore(':')
                .lowercase()
        }
    }
}
