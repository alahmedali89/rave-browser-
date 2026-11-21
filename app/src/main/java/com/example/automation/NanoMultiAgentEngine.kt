package com.example.automation

import android.webkit.WebView

enum class AgentRole(val badge: String) {
    PLANNER("PlannerAgent"),
    NAVIGATOR("NavigatorAgent"),
    VALIDATOR("ValidatorAgent")
}

enum class StepStatus {
    RUNNING,
    COMPLETED,
    ADJUSTED
}

data class NanoReasoningStep(
    val id: String,
    val role: AgentRole,
    val actionTitle: String,
    val detail: String,
    val jsSnippet: String? = null,
    val status: StepStatus = StepStatus.RUNNING,
    val timestampMs: Long = System.currentTimeMillis()
)

data class NanoChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val steps: List<NanoReasoningStep> = emptyList(),
    val isRunning: Boolean = false
)

enum class BrowserDomActionType {
    NAVIGATE_URL,
    INSPECT_AND_HIGHLIGHT,
    SCROLL_PAGE,
    CLICK_ELEMENT,
    TYPE_INPUT
}

data class PlannedStep(
    val title: String,
    val actionType: BrowserDomActionType,
    val targetParam: String,
    val rationale: String
)

class PlannerAgent(
    private var universalScanner: UniversalDomScanner? = null
) {
    internal fun bindScanner(scanner: UniversalDomScanner) {
        universalScanner = scanner
    }

    private fun resolveScanner(): UniversalDomScanner =
        universalScanner ?: AutomationEngine().universalScanner.also { universalScanner = it }

    fun decomposeTask(
        prompt: String,
        currentUrl: String,
        inspectionState: PageInspectionState? = null
    ): List<PlannedStep> = resolveScanner().planAgentTask(
        prompt = prompt,
        currentUrl = currentUrl,
        inspectionState = inspectionState
    )
}

data class DomObservation(
    val pageTitle: String,
    val pageUrl: String,
    val highlightedCount: Int,
    val items: List<Pair<String, String>>,
    val actionNote: String,
    val executedScriptPreview: String
)

class NavigatorAgent(
    private var universalScanner: UniversalDomScanner? = null
) {
    internal fun bindScanner(scanner: UniversalDomScanner) {
        universalScanner = scanner
    }

    private fun resolveScanner(): UniversalDomScanner =
        universalScanner ?: AutomationEngine().universalScanner.also { universalScanner = it }

    suspend fun executeStep(
        step: PlannedStep,
        webViewProvider: () -> WebView?,
        onNavigateUrl: (String) -> Unit,
        tabId: String = ""
    ): DomObservation = resolveScanner().executeAgentStep(
        step = step,
        tabId = tabId,
        webViewProvider = webViewProvider,
        onNavigateUrl = onNavigateUrl
    )
}

data class ValidationOutcome(
    val passed: Boolean,
    val confidencePercent: Int,
    val verificationNote: String,
    val finalSummary: String
)

class ValidatorAgent(
    private var universalScanner: UniversalDomScanner? = null
) {
    internal fun bindScanner(scanner: UniversalDomScanner) {
        universalScanner = scanner
    }

    private fun resolveScanner(): UniversalDomScanner =
        universalScanner ?: AutomationEngine().universalScanner.also { universalScanner = it }

    fun validateAndSynthesize(
        userPrompt: String,
        observations: List<DomObservation>,
        inspectionState: PageInspectionState? = null
    ): ValidationOutcome = resolveScanner().validateAgentOutcome(
        userPrompt = userPrompt,
        observations = observations,
        inspectionState = inspectionState
    )
}

class NanoMultiAgentEngine(
    private val planner: PlannerAgent = PlannerAgent(),
    private val navigator: NavigatorAgent = NavigatorAgent(),
    private val validator: ValidatorAgent = ValidatorAgent(),
    private var universalScanner: UniversalDomScanner? = null
) {
    internal fun bindScanner(scanner: UniversalDomScanner) {
        universalScanner = scanner
        planner.bindScanner(scanner)
        navigator.bindScanner(scanner)
        validator.bindScanner(scanner)
    }

    private fun resolveScanner(): UniversalDomScanner =
        universalScanner ?: AutomationEngine().universalScanner.also {
            universalScanner = it
            planner.bindScanner(it)
            navigator.bindScanner(it)
            validator.bindScanner(it)
        }

    suspend fun runTask(
        prompt: String,
        currentUrl: String,
        webViewProvider: () -> WebView?,
        onNavigateUrl: (String) -> Unit,
        onStepUpdate: (List<NanoReasoningStep>) -> Unit,
        tabId: String = ""
    ): ValidationOutcome = resolveScanner().runAgentTask(
        prompt = prompt,
        currentUrl = currentUrl,
        tabId = tabId,
        webViewProvider = webViewProvider,
        onNavigateUrl = onNavigateUrl,
        onStepUpdate = onStepUpdate
    )
}
