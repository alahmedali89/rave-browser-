package com.example.automation

import android.webkit.WebView
import com.example.data.local.ExtractionRuleEntity

data class ExtractedRecord(
    val id: String,
    val tabId: String,
    val tabTitle: String,
    val primaryField: String,
    val secondaryValue: String,
    val linkUrl: String,
    val matchedSelector: String
)

data class TabSnapshotTarget(
    val tabId: String,
    val title: String,
    val url: String
)

class RetrieverEngine(
    private var universalScanner: UniversalDomScanner? = null
) {
    internal fun bindScanner(scanner: UniversalDomScanner) {
        universalScanner = scanner
    }

    private fun resolveScanner(): UniversalDomScanner =
        universalScanner ?: AutomationEngine().universalScanner.also { universalScanner = it }

    /**
     * Translates either a natural-language extraction prompt or a raw CSS selector
     * into a reusable local-first [ExtractionRuleEntity] via [UniversalDomScanner].
     */
    fun buildRuleFromInput(input: String): ExtractionRuleEntity =
        DomScannerIntelligence.buildRuleFromInput(input)

    suspend fun extractFromSingleTab(
        target: TabSnapshotTarget,
        webView: WebView?,
        rule: ExtractionRuleEntity
    ): List<ExtractedRecord> = resolveScanner().extractStructuredData(
        target = target,
        webView = webView,
        rule = rule
    )

    /**
     * Runs the given extraction workflow across ALL open tabs in parallel via [UniversalDomScanner].
     */
    suspend fun extractAcrossTabsInParallel(
        tabs: List<TabSnapshotTarget>,
        webViewLookup: (String) -> WebView?,
        rule: ExtractionRuleEntity
    ): List<ExtractedRecord> = resolveScanner().extractAcrossTabsInParallel(
        tabs = tabs,
        webViewLookup = webViewLookup,
        rule = rule
    )
}
