package com.example.automation

import org.json.JSONArray
import org.json.JSONObject

enum class AutomationType(val id: String, val label: String) {
    RECORDED("RECORDED", "Recorded"),
    MANUAL("MANUAL", "Manual");

    companion object {
        fun fromId(raw: String?): AutomationType =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: MANUAL
    }
}

enum class AutomationHudMode {
    OVERVIEW,
    RECORDING,
    INSTRUCTIONS,
    ELEMENTS,
    SAVED,
    RECENT
}

enum class AutomationToolbarStatus {
    IDLE,
    ELEMENTS_DETECTED,
    RECORDING,
    PAUSED,
    EXECUTING,
    COMPLETED,
    ERROR
}

enum class AutomationWorkspaceCategory(val label: String) {
    ALL("All"),
    FAVORITES("Favorites"),
    RECORDED("Recorded"),
    MANUAL("Manual"),
    RECENT("Recent"),
    ARCHIVED("Archived")
}

enum class WorkflowSortOption(val label: String) {
    UPDATED_DESC("Recently Updated"),
    NAME_ASC("Name (A–Z)"),
    RECENTLY_EXECUTED("Recently Executed"),
    MOST_STEPS("Most Steps"),
    VERSION_DESC("Highest Version")
}

enum class AutomationActionType(val id: String, val label: String) {
    CLICK("CLICK", "Click"),
    DOUBLE_CLICK("DOUBLE_CLICK", "Double Click"),
    LONG_PRESS("LONG_PRESS", "Long Press"),
    INPUT("INPUT", "Input"),
    FILL_CREDENTIAL("FILL_CREDENTIAL", "Fill Credential"),
    CLEAR("CLEAR", "Clear"),
    SELECT("SELECT", "Select"),
    TOGGLE("TOGGLE", "Toggle"),
    SCROLL("SCROLL", "Scroll"),
    FOCUS("FOCUS", "Focus"),
    BLUR("BLUR", "Blur"),
    KEY_PRESS("KEY_PRESS", "Key Press"),
    WAIT("WAIT", "Wait"),
    NAVIGATE("NAVIGATE", "Navigate"),
    EXTRACT("EXTRACT", "Extract"),
    SUBMIT("SUBMIT", "Submit"),
    CONDITION("CONDITION", "If / Branch"),
    LOOP("LOOP", "Loop"),
    SET_VARIABLE("SET_VARIABLE", "Set Variable"),
    CALL_WORKFLOW("CALL_WORKFLOW", "Sub-Workflow"),
    CLEAR_DATA("CLEAR_DATA", "Clear Data"),
    UNSUPPORTED("UNSUPPORTED", "Unsupported Action");

    companion object {
        fun fromId(raw: String?): AutomationActionType {
            return entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: CLICK
        }

        fun fromIdOrNull(raw: String?): AutomationActionType? {
            val clean = raw?.trim().orEmpty()
            if (clean.isEmpty()) return null
            return entries.firstOrNull { it.id.equals(clean, ignoreCase = true) }
        }
    }
}

enum class ExecutionStatus(val id: String, val label: String) {
    IDLE("IDLE", "Idle"),
    RUNNING("RUNNING", "Running"),
    PAUSED("PAUSED", "Paused"),
    INTERRUPTED("INTERRUPTED", "Interrupted"),
    COMPLETED("COMPLETED", "Completed"),
    FAILED("FAILED", "Failed"),
    CANCELLED("CANCELLED", "Cancelled"),
    AWAITING_CONFIRMATION("AWAITING_CONFIRMATION", "Awaiting Confirmation"),
    AWAITING_CREDENTIAL_AUTH("AWAITING_CREDENTIAL_AUTH", "Awaiting Auth");

    val isTerminal: Boolean
        get() = this == COMPLETED || this == FAILED || this == CANCELLED || this == INTERRUPTED

    val isActive: Boolean
        get() = this == RUNNING || this == PAUSED

    companion object {
        fun fromId(raw: String?): ExecutionStatus {
            return entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: IDLE
        }
    }
}

enum class WaitConditionType(val id: String, val label: String) {
    ELEMENT_APPEARS("ELEMENT_APPEARS", "Element appears"),
    ELEMENT_DISAPPEARS("ELEMENT_DISAPPEARS", "Element disappears"),
    ELEMENT_INTERACTABLE("ELEMENT_INTERACTABLE", "Element interactable"),
    NAVIGATION_COMPLETED("NAVIGATION_COMPLETED", "Navigation completes"),
    URL_CHANGE("URL_CHANGE", "URL changes"),
    ORIGIN_CHANGE("ORIGIN_CHANGE", "Origin changes"),
    DOM_STATE_CHANGE("DOM_STATE_CHANGE", "DOM state changes"),
    CONTENT_STABILIZED("CONTENT_STABILIZED", "Content stabilizes");

    companion object {
        fun fromId(raw: String?): WaitConditionType? {
            return entries.firstOrNull { it.id.equals(raw, ignoreCase = true) }
        }
    }
}

data class WaitConditionSpec(
    val type: WaitConditionType,
    val selector: String = "",
    val targetLabel: String = "",
    val expectedUrlContains: String = "",
    val expectedOrigin: String = "",
    val expectedText: String = "",
    val attributeName: String = "",
    val expectedAttributeValue: String = "",
    val timeoutMs: Long = 5_000L,
    val stabilizeQuietMs: Long = 250L
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("type", type.id)
        if (selector.isNotBlank()) put("selector", selector)
        if (targetLabel.isNotBlank()) put("targetLabel", targetLabel)
        if (expectedUrlContains.isNotBlank()) put("expectedUrlContains", expectedUrlContains)
        if (expectedOrigin.isNotBlank()) put("expectedOrigin", expectedOrigin)
        if (expectedText.isNotBlank()) put("expectedText", expectedText)
        if (attributeName.isNotBlank()) put("attributeName", attributeName)
        if (expectedAttributeValue.isNotBlank()) put("expectedAttributeValue", expectedAttributeValue)
        put("timeoutMs", timeoutMs)
        put("stabilizeQuietMs", stabilizeQuietMs)
    }

    companion object {
        fun fromJson(obj: JSONObject?): WaitConditionSpec? {
            if (obj == null) return null
            val type = WaitConditionType.fromId(obj.optString("type")) ?: return null
            return WaitConditionSpec(
                type = type,
                selector = obj.optString("selector", ""),
                targetLabel = obj.optString("targetLabel", ""),
                expectedUrlContains = obj.optString("expectedUrlContains", ""),
                expectedOrigin = obj.optString("expectedOrigin", ""),
                expectedText = obj.optString("expectedText", ""),
                attributeName = obj.optString("attributeName", ""),
                expectedAttributeValue = obj.optString("expectedAttributeValue", ""),
                timeoutMs = obj.optLong("timeoutMs", 5_000L).coerceIn(100L, 60_000L),
                stabilizeQuietMs = obj.optLong("stabilizeQuietMs", 250L).coerceIn(50L, 5_000L)
            )
        }
    }
}

data class LocatorAttemptDiagnostic(
    val strategy: String,
    val selector: String,
    val matchCount: Int = 0,
    val visibleCount: Int = 0,
    val interactableCount: Int = 0,
    val selected: Boolean = false,
    val rejectedReason: String = ""
) {
    companion object {
        fun fromJson(obj: JSONObject?): LocatorAttemptDiagnostic? {
            if (obj == null) return null
            return LocatorAttemptDiagnostic(
                strategy = obj.optString("strategy", ""),
                selector = obj.optString("selector", ""),
                matchCount = obj.optInt("matchCount", 0),
                visibleCount = obj.optInt("visibleCount", 0),
                interactableCount = obj.optInt("interactableCount", 0),
                selected = obj.optBoolean("selected", false),
                rejectedReason = obj.optString("rejectedReason", "")
            )
        }
    }
}

data class AmbiguousCandidateInfo(
    val candidateIndex: Int = 0,
    val tagName: String,
    val accessibleName: String = "",
    val visibleText: String = "",
    val role: String = "",
    val attributes: Map<String, String> = emptyMap(),
    val parentContext: String = "",
    val nearbyText: String = "",
    val isVisible: Boolean = true,
    val isEnabled: Boolean = true,
    val recommendedSelector: String = "",
    val strategy: LocatorStrategy = LocatorStrategy.SCOPED_CSS,
    val framePath: List<String> = emptyList(),
    val shadowHostPath: List<String> = emptyList()
) {
    companion object {
        fun fromJson(obj: JSONObject?): AmbiguousCandidateInfo? {
            if (obj == null) return null
            val index = if (obj.has("candidateIndex")) {
                obj.optInt("candidateIndex", 0)
            } else {
                obj.optInt("index", 0)
            }
            val attrObj = obj.optJSONObject("attributes")
            val attrs = if (attrObj != null) {
                buildMap {
                    val keys = attrObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val lk = k.lowercase()
                        val isBlocked = lk == "value" || lk == "style" ||
                            lk.contains("password") || lk.contains("secret") ||
                            lk.contains("token") || lk.contains("otp") ||
                            lk.contains("cvv") || lk.contains("cvc") ||
                            lk.contains("ssn") || lk == "pin"
                        if (!isBlocked) {
                            put(k, attrObj.optString(k, ""))
                        }
                    }
                }
            } else {
                emptyMap()
            }
            val frameArr = obj.optJSONArray("framePath")
            val framePath = if (frameArr != null) {
                buildList {
                    for (i in 0 until frameArr.length()) {
                        val item = frameArr.optString(i, "").trim()
                        if (item.isNotEmpty()) add(item)
                    }
                }
            } else {
                emptyList()
            }
            val shadowArr = obj.optJSONArray("shadowHostPath")
            val shadowHostPath = if (shadowArr != null) {
                buildList {
                    for (i in 0 until shadowArr.length()) {
                        val item = shadowArr.optString(i, "").trim()
                        if (item.isNotEmpty()) add(item)
                    }
                }
            } else {
                emptyList()
            }
            return AmbiguousCandidateInfo(
                candidateIndex = index,
                tagName = obj.optString("tagName", ""),
                accessibleName = obj.optString("accessibleName", ""),
                visibleText = obj.optString("visibleText", ""),
                role = obj.optString("role", ""),
                attributes = attrs,
                parentContext = obj.optString("parentContext", ""),
                nearbyText = obj.optString("nearbyText", ""),
                isVisible = obj.optBoolean("isVisible", true),
                isEnabled = obj.optBoolean("isEnabled", true),
                recommendedSelector = obj.optString("recommendedSelector", ""),
                strategy = LocatorStrategy.fromId(obj.optString("strategy", "")),
                framePath = framePath,
                shadowHostPath = shadowHostPath
            )
        }
    }
}

data class LocatorResolutionDiagnostics(
    val stepId: String = "",
    val targetLabel: String = "",
    val recordedLocator: String = "",
    val totalMatches: Int = 0,
    val visibleMatches: Int = 0,
    val interactableMatches: Int = 0,
    val resolvedSelector: String = "",
    val finalOutcome: String = "",
    val attemptedCandidates: List<LocatorAttemptDiagnostic> = emptyList(),
    val candidatesSummary: List<AmbiguousCandidateInfo> = emptyList()
) {
    companion object {
        fun fromJson(obj: JSONObject?): LocatorResolutionDiagnostics? {
            if (obj == null) return null
            val attemptsArr = obj.optJSONArray("attemptedCandidates")
            val attempts = if (attemptsArr != null) {
                buildList {
                    for (i in 0 until attemptsArr.length()) {
                        LocatorAttemptDiagnostic.fromJson(attemptsArr.optJSONObject(i))?.let { add(it) }
                    }
                }
            } else {
                emptyList()
            }
            val summaryArr = obj.optJSONArray("candidatesSummary")
            val summary = if (summaryArr != null) {
                buildList {
                    for (i in 0 until summaryArr.length()) {
                        AmbiguousCandidateInfo.fromJson(summaryArr.optJSONObject(i))?.let { add(it) }
                    }
                }
            } else {
                emptyList()
            }
            return LocatorResolutionDiagnostics(
                stepId = obj.optString("stepId", ""),
                targetLabel = obj.optString("targetLabel", ""),
                recordedLocator = obj.optString("recordedLocator", ""),
                totalMatches = obj.optInt("totalMatches", 0),
                visibleMatches = obj.optInt("visibleMatches", 0),
                interactableMatches = obj.optInt("interactableMatches", 0),
                resolvedSelector = obj.optString("resolvedSelector", ""),
                finalOutcome = obj.optString("finalOutcome", ""),
                attemptedCandidates = attempts,
                candidatesSummary = summary
            )
        }
    }
}

enum class DisambiguationSource {
    EXECUTION,
    RECORDING,
    INSTRUCTION_DRAFT
}

data class PendingDisambiguationState(
    val workflowId: Long? = null,
    val stepId: String,
    val stepOrder: Int = 1,
    val stepDescription: String,
    val actionType: AutomationActionType = AutomationActionType.CLICK,
    val originalTarget: String,
    val originalSelector: String = "",
    val source: DisambiguationSource = DisambiguationSource.EXECUTION,
    val candidates: List<AmbiguousCandidateInfo>,
    val diagnostics: LocatorResolutionDiagnostics? = null
)

data class ElementBoundingBox(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val viewportWidth: Float = 0f,
    val viewportHeight: Float = 0f
) {
    val isValid: Boolean
        get() = width > 0f && height > 0f && viewportWidth > 0f && viewportHeight > 0f

    companion object {
        fun fromJson(obj: JSONObject?): ElementBoundingBox? {
            if (obj == null) return null
            val left = obj.optDouble("left", Double.NaN)
            val top = obj.optDouble("top", Double.NaN)
            val width = obj.optDouble("width", Double.NaN)
            val height = obj.optDouble("height", Double.NaN)
            if (left.isNaN() || top.isNaN() || width.isNaN() || height.isNaN()) return null
            if (width <= 0.0 || height <= 0.0) return null
            return ElementBoundingBox(
                left = left.toFloat(),
                top = top.toFloat(),
                width = width.toFloat(),
                height = height.toFloat(),
                viewportWidth = obj.optDouble("viewportWidth", 0.0).toFloat().coerceAtLeast(0f),
                viewportHeight = obj.optDouble("viewportHeight", 0.0).toFloat().coerceAtLeast(0f)
            )
        }
    }
}

data class ActiveDomElementHighlight(
    val selector: String,
    val targetLabel: String,
    val actionLabel: String,
    val tagName: String = "",
    val boundingBox: ElementBoundingBox? = null,
    val isScanning: Boolean = false,
    val stepIndex: Int = 0,
    val totalSteps: Int = 0,
    val status: String = "INTERACTING",
    val timestampMs: Long = System.currentTimeMillis()
)

data class StepExecutionRecord(
    val stepId: String,
    val stepOrder: Int,
    val actionType: AutomationActionType,
    val status: String,
    val durationMs: Long,
    val errorCode: AutomationErrorCode = AutomationErrorCode.NONE,
    val recoveredViaFallback: Boolean = false,
    val resolvedSelector: String = "",
    val safeMessage: String = "",
    val ambiguousCandidates: List<AmbiguousCandidateInfo> = emptyList(),
    val locatorDiagnostics: LocatorResolutionDiagnostics? = null,
    val targetBoundingBox: ElementBoundingBox? = null
)

data class AutomationStep(
    val id: String,
    val order: Int,
    val actionType: AutomationActionType,
    val targetLabel: String,
    val selector: String = "",
    val value: String = "",
    val description: String,
    val isDestructive: Boolean = false,
    val credentialReferenceId: String? = null,
    val credentialFieldRole: CredentialFieldRole? = null,
    val expectedOrigin: String = "",
    val requiresCredential: Boolean = false,
    val locatorCandidates: List<LocatorCandidate> = emptyList(),
    val controlKindHint: ControlKind? = null,
    val dropdownKindHint: DropdownKind? = null,
    val waitCondition: WaitConditionSpec? = null,
    val keyboardKey: String = "",
    val desiredCheckedState: Boolean? = null,
    val clearBeforeInput: Boolean = false,
    val framePath: List<String> = emptyList(),
    val shadowHostPath: List<String> = emptyList(),
    val isOptional: Boolean = false,
    val stepTimeoutMs: Long = 0L,
    val condition: ConditionNode? = null,
    val thenSteps: List<AutomationStep> = emptyList(),
    val elseIfBranches: List<ElseIfBranch> = emptyList(),
    val elseSteps: List<AutomationStep> = emptyList(),
    val loopConfig: LoopConfig? = null,
    val extractionSpec: ExtractionSpec? = null,
    val recoveryPolicy: RecoveryPolicy? = null,
    val subWorkflowRef: SubWorkflowReference? = null,
    val variableName: String = "",
    val variableType: WorkflowVariableType = WorkflowVariableType.TEXT,
    val allowCrossOriginTransition: Boolean = false,
    val vaultEntryId: String? = null,
    val vaultCategory: String = "",
    val useVaultPool: Boolean = false,
    val vaultFolderId: String = "",
    val rawActionType: String = "",
    val unknownFieldsJson: String = ""
) {
    val isCredentialStep: Boolean
        get() = requiresCredential ||
            !credentialReferenceId.isNullOrBlank() ||
            actionType == AutomationActionType.FILL_CREDENTIAL

    fun collectNestedSteps(): List<AutomationStep> = buildList {
        add(this@AutomationStep)
        thenSteps.forEach { addAll(it.collectNestedSteps()) }
        elseIfBranches.forEach { branch ->
            branch.steps.forEach { addAll(it.collectNestedSteps()) }
        }
        elseSteps.forEach { addAll(it.collectNestedSteps()) }
        recoveryPolicy?.alternativeSteps?.forEach { addAll(it.collectNestedSteps()) }
    }
}

enum class RecordingStatus {
    IDLE,
    RECORDING,
    PAUSED,
    STOPPED,
    CANCELLED,
    ERROR
}

enum class AutomationErrorCode {
    NONE,
    WEBVIEW_UNAVAILABLE,
    EMPTY_WORKFLOW,
    UNSUPPORTED_SCHEMA,
    INVALID_URL,
    INVALID_SELECTOR,
    ELEMENT_NOT_FOUND,
    ELEMENT_AMBIGUOUS,
    ELEMENT_STALE,
    ELEMENT_DISABLED,
    ELEMENT_NOT_INTERACTABLE,
    OPTION_NOT_FOUND,
    WAIT_TIMEOUT,
    VERIFICATION_FAILED,
    JS_TIMEOUT,
    JS_EXCEPTION,
    NAVIGATION_TIMEOUT,
    NAVIGATION_FAILED,
    CONTEXT_INVALIDATED,
    TAB_CLOSED,
    WEBVIEW_REPLACED,
    UNEXPECTED_ORIGIN_CHANGE,
    DUPLICATE_EXECUTION,
    CROSS_ORIGIN_BLOCKED,
    SECURITY_CHALLENGE_BLOCKED,
    UNSUPPORTED_ACTION,
    CANCELLED,
    RECORDING_SESSION_INVALID,
    RECORDING_SESSION_MISMATCH,
    RECORDING_TAB_MISMATCH,
    RECORDING_TAB_CLOSED,
    RECORDING_ORIGIN_CHANGED,
    RECORDING_DOCUMENT_UNAVAILABLE,
    RECORDING_INSTALL_FAILED,
    RECORDING_CLEANUP_FAILED,
    CREDENTIAL_VAULT_LOCKED,
    CREDENTIAL_AUTH_REQUIRED,
    CREDENTIAL_AUTH_FAILED,
    CREDENTIAL_AUTH_CANCELLED,
    CREDENTIAL_KEY_INVALIDATED,
    CREDENTIAL_UNASSIGNED,
    CREDENTIAL_NOT_FOUND,
    CREDENTIAL_ORIGIN_MISMATCH,
    CREDENTIAL_FIELD_INVALID,
    CREDENTIAL_DECRYPTION_FAILED,
    CREDENTIAL_FILL_FAILED,
    SCAN_CONTEXT_INVALIDATED,
    SCAN_ORIGIN_MISMATCH,
    SCAN_TIMEOUT,
    SCAN_CANCELLED,
    CONDITION_INVALID,
    VARIABLE_UNDEFINED,
    VARIABLE_INVALID,
    EXTRACTION_AMBIGUOUS,
    EXTRACTION_SENSITIVE_BLOCKED,
    LOOP_LIMIT_EXCEEDED,
    SUBWORKFLOW_CYCLE,
    SUBWORKFLOW_DEPTH_EXCEEDED,
    SUBWORKFLOW_NOT_FOUND,
    UNSAFE_RECOVERY_BLOCKED,
    CHECKPOINT_INVALID,
    WORKFLOW_ARCHIVED,
    DUPLICATE_STEP_ID,
    MISSING_REQUIRED_PARAM,
    VAULT_VALUES_EXHAUSTED
}

data class RecordingErrorInfo(
    val code: AutomationErrorCode,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

sealed class RecordingOriginResult {
    abstract val isValid: Boolean
    abstract val canonicalOrigin: String
    abstract val displayOrigin: String

    data class Valid(
        override val canonicalOrigin: String,
        override val displayOrigin: String = canonicalOrigin,
        val scheme: String = "",
        val host: String = "",
        val effectivePort: Int = 0
    ) : RecordingOriginResult() {
        override val isValid: Boolean = true
    }

    data class OpaqueOrUnsupported(
        val scheme: String = "",
        val reason: String = ""
    ) : RecordingOriginResult() {
        override val isValid: Boolean = false
        override val canonicalOrigin: String = ""
        override val displayOrigin: String = ""
    }
}

data class RecordedDomEvent(
    val sessionId: String = "",
    val tabId: String = "",
    val origin: String = "",
    val sequenceNumber: Long = 0L,
    val eventType: String,
    val targetLabel: String,
    val selector: String,
    val value: String,
    val isDestructive: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val isPasswordField: Boolean = false,
    val fieldRoleHint: String = "",
    val locatorCandidates: List<LocatorCandidate> = emptyList(),
    val framePath: List<String> = emptyList(),
    val shadowHostPath: List<String> = emptyList(),
    val parentContext: String = "",
    val nearbyText: String = "",
    val relevantAttributes: Map<String, String> = emptyMap(),
    val matchCount: Int = 1,
    val ambiguousCandidates: List<AmbiguousCandidateInfo> = emptyList(),
    val tagName: String = "",
    val boundingBox: ElementBoundingBox? = null,
    val sensitiveCapturedValue: String = ""
)

data class RecorderLifecycleResult(
    val success: Boolean,
    val errorCode: AutomationErrorCode = AutomationErrorCode.NONE,
    val message: String = ""
)

data class DetectedPageElement(
    val id: String,
    val tagName: String,
    val role: String,
    val label: String,
    val selector: String,
    val inputType: String = "",
    val isInteractive: Boolean = true,
    val placeholder: String = "",
    val scanSessionId: String = "",
    val tabId: String = "",
    val documentId: String = "",
    val framePath: List<String> = emptyList(),
    val frameUrl: String = "",
    val frameOrigin: String = "",
    val shadowHostPath: List<String> = emptyList(),
    val controlKind: ControlKind = ControlKind.OTHER_INTERACTIVE,
    val dropdownKind: DropdownKind = DropdownKind.NONE,
    val accessibleName: String = label,
    val name: String = "",
    val htmlId: String = "",
    val safeValue: String = "",
    val checked: Boolean? = null,
    val selected: Boolean? = null,
    val required: Boolean = false,
    val disabled: Boolean = false,
    val readonly: Boolean = false,
    val editable: Boolean = false,
    val visible: Boolean = true,
    val inViewport: Boolean = true,
    val interactable: Boolean = isInteractive,
    val contentEditable: String = "",
    val autocomplete: String = "",
    val ariaAttributes: Map<String, String> = emptyMap(),
    val controlledElementId: String = "",
    val formGroupId: String = "",
    val formName: String = "",
    val fieldsetLegend: String = "",
    val radioGroupName: String = "",
    val dateTimeMetadata: DateTimeMetadata? = null,
    val sensitiveCategory: SensitiveFieldCategory = SensitiveFieldCategory.NONE,
    val isCredentialCapable: Boolean = false,
    val locatorCandidates: List<LocatorCandidate> = emptyList(),
    val locatorStrategy: LocatorStrategy = LocatorStrategy.SCOPED_CSS,
    val locatorConfidence: Float = 0.7f,
    val isLocatorAmbiguous: Boolean = false,
    val discoverySource: String = "PASSIVE_SCAN",
    val discoveredAt: Long = 0L,
    val lastUpdatedAt: Long = 0L,
    val stale: Boolean = false,
    val href: String = "",
    val visibleText: String = "",
    val parentContainerSummary: String = "",
    val nearbyText: String = "",
    val relevantAttributes: Map<String, String> = emptyMap(),
    val boundingBox: ElementBoundingBox? = null,
    val semanticType: FieldSemanticType = FieldSemanticType.NONE,
    val inputMode: String = "",
    val datalistOptions: List<String> = emptyList()
) {
    val isSensitiveField: Boolean
        get() = sensitiveCategory.isSensitive || inputType.equals("password", ignoreCase = true)

    val isInShadowDom: Boolean
        get() = shadowHostPath.isNotEmpty()

    val isInSameOriginIframe: Boolean
        get() = framePath.isNotEmpty()

    val isPasswordInput: Boolean
        get() = inputType.equals("password", ignoreCase = true) ||
            sensitiveCategory == SensitiveFieldCategory.PASSWORD
}

data class PageInspectionState(
    val domain: String = "Start Page",
    val url: String = "apex://home",
    val elements: List<DetectedPageElement> = emptyList(),
    val isScanning: Boolean = false,
    val lastScannedAt: Long = 0L,
    val errorMessage: String? = null,
    val scanSessionId: String = "",
    val tabId: String = "",
    val canonicalOrigin: String = "",
    val scanStatus: DomScanStatus = DomScanStatus.IDLE,
    val summary: DomScanSummary = DomScanSummary(),
    val inaccessibleFrames: List<InaccessibleFrameInfo> = emptyList(),
    val isExtendedScanRunning: Boolean = false,
    val isDynamicObserverActive: Boolean = false,
    val highlightedElementSelector: String = "",
    val extractedRecords: List<ExtractedRecord> = emptyList(),
    val pageIntentSummary: String = ""
)

data class ActiveRecordingSession(
    val sessionId: String = "",
    val initiatingTabId: String = "",
    val initialOrigin: String = "",
    val currentOrigin: String = "",
    val status: RecordingStatus = RecordingStatus.IDLE,
    val steps: List<AutomationStep> = emptyList(),
    val targetDomain: String = "",
    val targetUrl: String = "",
    val suggestedName: String = "",
    val startedAt: Long = 0L,
    val lastSequenceNumber: Long = 0L,
    val recordingError: RecordingErrorInfo? = null,
    val pendingOriginChangeUrl: String? = null
)

enum class AutomationVaultCategory(
    val id: String,
    val label: String,
    val isSensitiveDefault: Boolean
) {
    PASSWORD("PASSWORD", "Password", true),
    EMAIL("EMAIL", "Email", false),
    PHONE("PHONE", "Phone", false),
    COUNTRY_CODE("COUNTRY_CODE", "Country Code", false),
    DATE_OF_BIRTH("DATE_OF_BIRTH", "Date of Birth", false),
    GENDER("GENDER", "Gender", false),
    USERNAME("USERNAME", "Username", false),
    NAME("NAME", "Full Name", false),
    ADDRESS("ADDRESS", "Address", false),
    CUSTOM("CUSTOM", "Custom Value", false);

    companion object {
        fun fromId(raw: String?): AutomationVaultCategory {
            val clean = raw?.trim().orEmpty()
            return entries.firstOrNull {
                it.id.equals(clean, ignoreCase = true) ||
                    it.name.equals(clean, ignoreCase = true) ||
                    it.label.equals(clean, ignoreCase = true)
            } ?: when (clean.lowercase()) {
                "dob", "birthday", "birthdate", "birth date", "dateofbirth" -> DATE_OF_BIRTH
                "countrycode", "dial code", "dial_code", "dialcode", "calling code" -> COUNTRY_CODE
                "sex", "pronoun", "pronouns" -> GENDER
                else -> CUSTOM
            }
        }
    }
}

data class RecordingVaultCandidate(
    val stepId: String,
    val stepOrder: Int,
    val targetLabel: String,
    val selector: String = "",
    val category: AutomationVaultCategory,
    val vaultLabel: String,
    val capturedValue: String,
    val hasSecretCapture: Boolean = false,
    val isSensitive: Boolean = category.isSensitiveDefault,
    val isSelected: Boolean = true,
    val existingEntryId: String? = null
)

data class VaultFileImportCandidate(
    val id: String,
    val category: AutomationVaultCategory,
    val label: String,
    val value: String,
    val websiteOrigin: String = "",
    val isSensitive: Boolean = category.isSensitiveDefault,
    val isSelected: Boolean = true,
    val existingEntryId: String? = null
)

data class WorkflowExecutionState(
    val workflowId: Long,
    val workflowName: String,
    val status: String,
    val currentStepIndex: Int,
    val totalSteps: Int,
    val stepLogs: List<String> = emptyList(),
    val verificationMessage: String = "",
    val isAwaitingConfirmation: Boolean = false,
    val pendingDestructiveSteps: List<AutomationStep> = emptyList(),
    val errorCode: AutomationErrorCode = AutomationErrorCode.NONE,
    val currentStepDescription: String = "",
    val currentActionLabel: String = "",
    val completedSteps: Int = 0,
    val startedAtMs: Long = 0L,
    val elapsedMs: Long = 0L,
    val recoveryNotice: String = "",
    val stepRecords: List<StepExecutionRecord> = emptyList(),
    val activeBranchLabel: String = "",
    val activeSubWorkflowName: String = "",
    val recoveryAttemptsCount: Int = 0,
    val variableSnapshots: List<VariableDisplayBadge> = emptyList(),
    val branchTraces: List<BranchTraceRecord> = emptyList(),
    val recoveryAttempts: List<RecoveryAttemptRecord> = emptyList(),
    val isInterrupted: Boolean = false,
    val ambiguousCandidates: List<AmbiguousCandidateInfo> = emptyList(),
    val lastLocatorDiagnostics: LocatorResolutionDiagnostics? = null,
    val activeElementHighlight: ActiveDomElementHighlight? = null,
    val currentRunIndex: Int = 1,
    val totalRuns: Int = 1,
    val clearDataAfterRun: Boolean = false
) {
    val executionStatus: ExecutionStatus
        get() = ExecutionStatus.fromId(status)
}

data class WorkflowPlayOptions(
    val repeatCount: Int = 1,
    val clearDataAfterRun: Boolean = false
)

data class VaultExhaustionPromptState(
    val workflowId: Long,
    val workflowName: String,
    val folderId: String,
    val folderName: String,
    val category: AutomationVaultCategory,
    val targetLabel: String,
    val failedRunIndex: Int,
    val totalRuns: Int,
    val message: String
)

sealed class AutomationStepDecodeResult {
    data class Success(
        val schemaVersion: Int,
        val steps: List<AutomationStep>,
        val isLegacyUnversioned: Boolean = false,
        val metadata: WorkflowDocumentMetadata = WorkflowDocumentMetadata()
    ) : AutomationStepDecodeResult()

    data class UnsupportedVersion(
        val foundVersion: Int,
        val message: String
    ) : AutomationStepDecodeResult()

    data class MalformedJson(
        val message: String
    ) : AutomationStepDecodeResult()

    fun stepsOrEmpty(): List<AutomationStep> = when (this) {
        is Success -> steps
        else -> emptyList()
    }
}

object AutomationStepCodec {
    const val CURRENT_SCHEMA_VERSION = 1

    private val destructiveKeywords = listOf(
        "delete", "remove", "pay", "purchase", "checkout", "confirm order",
        "place order", "transfer", "wire", "send money", "submit form",
        "submit", "sign out", "logout", "deactivate", "cancel subscription",
        "unsubscribe"
    )

    fun encode(
        steps: List<AutomationStep>,
        metadata: WorkflowDocumentMetadata = WorkflowDocumentMetadata()
    ): String {
        val arr = encodeStepsArray(steps)
        return JSONObject().apply {
            put("schemaVersion", CURRENT_SCHEMA_VERSION)
            put("steps", arr)
            if (!metadata.isEmptyDefault) {
                put("phase6", metadata.toJson())
            }
        }.toString()
    }

    fun encodeDocument(document: WorkflowDocument): String =
        encode(document.steps, document.metadata)

    fun encodeStepsArray(steps: List<AutomationStep>): JSONArray {
        val arr = JSONArray()
        steps.forEachIndexed { idx, step ->
            val sanitizedIsCredentialStep = step.isCredentialStep
            val sanitizedValue = if (sanitizedIsCredentialStep) "" else step.value
            val obj = JSONObject()
            if (step.unknownFieldsJson.isNotBlank()) {
                runCatching {
                    val extra = JSONObject(step.unknownFieldsJson)
                    val keys = extra.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        if (k !in RESERVED_STEP_KEYS && !isSensitiveUnknownKey(k)) {
                            obj.put(k, extra.opt(k))
                        }
                    }
                }
            }
            obj.put("id", step.id)
            obj.put("order", idx + 1)
            val encodedActionType = if (step.actionType == AutomationActionType.UNSUPPORTED && step.rawActionType.isNotBlank()) {
                step.rawActionType
            } else {
                step.actionType.id
            }
            obj.put("actionType", encodedActionType)
            obj.put("targetLabel", step.targetLabel)
            obj.put("selector", step.selector)
            obj.put("value", sanitizedValue)
            obj.put("description", step.description)
            obj.put("isDestructive", step.isDestructive)
            if (!step.credentialReferenceId.isNullOrBlank()) {
                obj.put("credentialReferenceId", step.credentialReferenceId)
            }
            if (step.credentialFieldRole != null) {
                obj.put("credentialFieldRole", step.credentialFieldRole.id)
            }
            if (step.expectedOrigin.isNotBlank()) {
                obj.put("expectedOrigin", step.expectedOrigin)
            }
            if (step.requiresCredential || sanitizedIsCredentialStep) {
                obj.put("requiresCredential", true)
            }
            if (step.locatorCandidates.isNotEmpty()) {
                val candArr = JSONArray()
                step.locatorCandidates.take(6).forEach { candArr.put(it.toJson()) }
                obj.put("locatorCandidates", candArr)
            }
            if (step.controlKindHint != null) {
                obj.put("controlKindHint", step.controlKindHint.id)
            }
            if (step.dropdownKindHint != null && step.dropdownKindHint != DropdownKind.NONE) {
                obj.put("dropdownKindHint", step.dropdownKindHint.id)
            }
            if (step.waitCondition != null) {
                obj.put("waitCondition", step.waitCondition.toJson())
            }
            if (step.keyboardKey.isNotBlank()) {
                obj.put("keyboardKey", step.keyboardKey)
            }
            if (step.desiredCheckedState != null) {
                obj.put("desiredCheckedState", step.desiredCheckedState)
            }
            if (step.clearBeforeInput) {
                obj.put("clearBeforeInput", true)
            }
            if (step.framePath.isNotEmpty()) {
                obj.put("framePath", JSONArray(step.framePath))
            }
            if (step.shadowHostPath.isNotEmpty()) {
                obj.put("shadowHostPath", JSONArray(step.shadowHostPath))
            }
            if (step.isOptional) {
                obj.put("isOptional", true)
            }
            if (step.stepTimeoutMs > 0L) {
                obj.put("stepTimeoutMs", step.stepTimeoutMs)
            }
            if (step.condition != null) {
                obj.put("condition", step.condition.toJson())
            }
            if (step.thenSteps.isNotEmpty()) {
                obj.put("thenSteps", encodeStepsArray(step.thenSteps))
            }
            if (step.elseIfBranches.isNotEmpty()) {
                val eArr = JSONArray()
                step.elseIfBranches.forEach { eArr.put(it.toJson()) }
                obj.put("elseIfBranches", eArr)
            }
            if (step.elseSteps.isNotEmpty()) {
                obj.put("elseSteps", encodeStepsArray(step.elseSteps))
            }
            if (step.loopConfig != null) {
                obj.put("loopConfig", step.loopConfig.toJson())
            }
            if (step.extractionSpec != null) {
                obj.put("extractionSpec", step.extractionSpec.toJson())
            }
            if (step.recoveryPolicy != null) {
                obj.put("recoveryPolicy", step.recoveryPolicy.toJson())
            }
            if (step.subWorkflowRef != null) {
                obj.put("subWorkflowRef", step.subWorkflowRef.toJson())
            }
            if (step.variableName.isNotBlank()) {
                obj.put("variableName", step.variableName)
            }
            if (step.variableType != WorkflowVariableType.TEXT) {
                obj.put("variableType", step.variableType.id)
            }
            if (step.allowCrossOriginTransition) {
                obj.put("allowCrossOriginTransition", true)
            }
            if (!step.vaultEntryId.isNullOrBlank()) {
                obj.put("vaultEntryId", step.vaultEntryId)
            }
            if (step.vaultCategory.isNotBlank()) {
                obj.put("vaultCategory", step.vaultCategory)
            }
            if (step.useVaultPool) {
                obj.put("useVaultPool", true)
            }
            if (step.vaultFolderId.isNotBlank()) {
                obj.put("vaultFolderId", step.vaultFolderId)
            }
            arr.put(obj)
        }
        return arr
    }

    private val RESERVED_STEP_KEYS = setOf(
        "id", "order", "actionType", "targetLabel", "selector", "value", "description",
        "isDestructive", "credentialReferenceId", "credentialFieldRole", "expectedOrigin",
        "requiresCredential", "locatorCandidates", "controlKindHint", "dropdownKindHint",
        "waitCondition", "keyboardKey", "desiredCheckedState", "clearBeforeInput",
        "framePath", "shadowHostPath", "isOptional", "stepTimeoutMs", "condition",
        "thenSteps", "elseIfBranches", "elseSteps", "loopConfig", "extractionSpec",
        "recoveryPolicy", "subWorkflowRef", "variableName", "variableType",
        "allowCrossOriginTransition", "vaultEntryId", "vaultCategory", "useVaultPool",
        "vaultFolderId"
    )

    private fun isSensitiveUnknownKey(key: String): Boolean {
        val lower = key.lowercase()
        return lower.contains("password") ||
            lower.contains("secret") ||
            lower.contains("token") ||
            lower.contains("apikey") ||
            lower.contains("api_key") ||
            lower.contains("session") ||
            lower.contains("cookie") ||
            lower.contains("keystore") ||
            lower.contains("privatekey") ||
            lower.contains("credential")
    }

    fun decodeSafely(json: String?): AutomationStepDecodeResult {
        val raw = json?.trim().orEmpty()
        if (raw.isEmpty()) {
            return AutomationStepDecodeResult.Success(
                schemaVersion = CURRENT_SCHEMA_VERSION,
                steps = emptyList(),
                isLegacyUnversioned = false
            )
        }
        return runCatching {
            if (raw.startsWith("[")) {
                val arr = JSONArray(raw)
                AutomationStepDecodeResult.Success(
                    schemaVersion = CURRENT_SCHEMA_VERSION,
                    steps = parseStepsArray(arr),
                    isLegacyUnversioned = true
                )
            } else if (raw.startsWith("{")) {
                val envelope = JSONObject(raw)
                val meta = WorkflowDocumentMetadata.fromJson(envelope.optJSONObject("phase6"))
                if (envelope.has("schemaVersion")) {
                    val version = envelope.optInt("schemaVersion", -1)
                    if (version != CURRENT_SCHEMA_VERSION) {
                        return@runCatching AutomationStepDecodeResult.UnsupportedVersion(
                            foundVersion = version,
                            message = "Unsupported workflow step schema v$version (supported: v$CURRENT_SCHEMA_VERSION)."
                        )
                    }
                    val arr = envelope.optJSONArray("steps") ?: JSONArray()
                    AutomationStepDecodeResult.Success(
                        schemaVersion = version,
                        steps = parseStepsArray(arr),
                        isLegacyUnversioned = false,
                        metadata = meta
                    )
                } else {
                    val arr = envelope.optJSONArray("steps") ?: JSONArray()
                    AutomationStepDecodeResult.Success(
                        schemaVersion = CURRENT_SCHEMA_VERSION,
                        steps = parseStepsArray(arr),
                        isLegacyUnversioned = true,
                        metadata = meta
                    )
                }
            } else {
                AutomationStepDecodeResult.MalformedJson("Workflow steps must be a JSON array or versioned object.")
            }
        }.getOrElse { ex ->
            AutomationStepDecodeResult.MalformedJson("Unable to parse workflow steps: ${ex.message ?: "invalid JSON"}")
        }
    }

    fun decodeDocument(json: String?): WorkflowDocument {
        return when (val res = decodeSafely(json)) {
            is AutomationStepDecodeResult.Success -> WorkflowDocument(
                schemaVersion = res.schemaVersion,
                steps = res.steps,
                metadata = res.metadata,
                isLegacyUnversioned = res.isLegacyUnversioned
            )
            else -> WorkflowDocument()
        }
    }

    internal fun parseStepsArray(arr: JSONArray): List<AutomationStep> {
        val result = ArrayList<AutomationStep>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val hasActionType = obj.has("actionType")
            val rawActionStr = obj.optString("actionType", "").trim()
            val actionType = when {
                !hasActionType -> AutomationActionType.CLICK
                rawActionStr.isEmpty() -> AutomationActionType.UNSUPPORTED
                else -> AutomationActionType.fromIdOrNull(rawActionStr) ?: AutomationActionType.UNSUPPORTED
            }
            val preservedRawActionType = if (actionType == AutomationActionType.UNSUPPORTED) {
                rawActionStr
            } else {
                ""
            }
            val extraFields = JSONObject().apply {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (k !in RESERVED_STEP_KEYS && !isSensitiveUnknownKey(k)) {
                        put(k, obj.opt(k))
                    }
                }
            }
            val unknownFieldsJson = if (extraFields.length() > 0) extraFields.toString() else ""
            val rawRef = obj.optString("credentialReferenceId", "").trim()
            val credRef = rawRef.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
            val credRole = CredentialFieldRole.fromIdOrNull(obj.optString("credentialFieldRole", ""))
            val expectedOrigin = obj.optString("expectedOrigin", "").trim()
            val requiresCred = obj.optBoolean("requiresCredential", false) ||
                credRef != null ||
                actionType == AutomationActionType.FILL_CREDENTIAL
            val rawValue = obj.optString("value", "")

            val locCandArr = obj.optJSONArray("locatorCandidates")
            val locatorCandidates = if (locCandArr != null) {
                buildList {
                    for (c in 0 until locCandArr.length()) {
                        val cObj = locCandArr.optJSONObject(c) ?: continue
                        val sel = cObj.optString("selector", "").trim()
                        if (sel.isNotEmpty()) {
                            add(LocatorCandidate.fromJson(cObj))
                        }
                    }
                }
            } else {
                emptyList()
            }

            val frameArr = obj.optJSONArray("framePath")
            val framePath = if (frameArr != null) {
                buildList {
                    for (f in 0 until frameArr.length()) {
                        frameArr.optString(f, "").trim().takeIf { it.isNotEmpty() }?.let { add(it) }
                    }
                }
            } else {
                emptyList()
            }

            val shadowArr = obj.optJSONArray("shadowHostPath")
            val shadowPath = if (shadowArr != null) {
                buildList {
                    for (s in 0 until shadowArr.length()) {
                        shadowArr.optString(s, "").trim().takeIf { it.isNotEmpty() }?.let { add(it) }
                    }
                }
            } else {
                emptyList()
            }

            val desiredChecked = if (obj.has("desiredCheckedState") && !obj.isNull("desiredCheckedState")) {
                obj.optBoolean("desiredCheckedState")
            } else {
                null
            }

            val rawControlKind = obj.optString("controlKindHint", "").trim()
            val controlKindHint = if (rawControlKind.isNotEmpty()) {
                ControlKind.entries.firstOrNull { it.id.equals(rawControlKind, ignoreCase = true) }
            } else {
                null
            }
            val rawDropdownKind = obj.optString("dropdownKindHint", "").trim()
            val dropdownKindHint = if (rawDropdownKind.isNotEmpty()) {
                DropdownKind.entries.firstOrNull { it.id.equals(rawDropdownKind, ignoreCase = true) }
            } else {
                null
            }

            val thenArr = obj.optJSONArray("thenSteps")
            val thenSteps = if (thenArr != null) parseStepsArray(thenArr) else emptyList()

            val elseIfArr = obj.optJSONArray("elseIfBranches")
            val elseIfBranches = if (elseIfArr != null) {
                buildList {
                    for (b in 0 until elseIfArr.length()) {
                        ElseIfBranch.fromJson(elseIfArr.optJSONObject(b))?.let { add(it) }
                    }
                }
            } else {
                emptyList()
            }

            val elseArr = obj.optJSONArray("elseSteps")
            val elseSteps = if (elseArr != null) parseStepsArray(elseArr) else emptyList()

            result.add(
                AutomationStep(
                    id = obj.optString("id", "step_$i"),
                    order = obj.optInt("order", i + 1),
                    actionType = actionType,
                    targetLabel = obj.optString("targetLabel", ""),
                    selector = obj.optString("selector", ""),
                    value = if (requiresCred) "" else rawValue,
                    description = obj.optString("description", ""),
                    isDestructive = obj.optBoolean("isDestructive", false),
                    credentialReferenceId = credRef,
                    credentialFieldRole = credRole,
                    expectedOrigin = expectedOrigin,
                    requiresCredential = requiresCred,
                    locatorCandidates = locatorCandidates,
                    controlKindHint = controlKindHint,
                    dropdownKindHint = dropdownKindHint,
                    waitCondition = WaitConditionSpec.fromJson(obj.optJSONObject("waitCondition")),
                    keyboardKey = obj.optString("keyboardKey", ""),
                    desiredCheckedState = desiredChecked,
                    clearBeforeInput = obj.optBoolean("clearBeforeInput", false),
                    framePath = framePath,
                    shadowHostPath = shadowPath,
                    isOptional = obj.optBoolean("isOptional", false),
                    stepTimeoutMs = obj.optLong("stepTimeoutMs", 0L).coerceAtLeast(0L),
                    condition = ConditionNode.fromJson(obj.optJSONObject("condition")),
                    thenSteps = thenSteps,
                    elseIfBranches = elseIfBranches,
                    elseSteps = elseSteps,
                    loopConfig = LoopConfig.fromJson(obj.optJSONObject("loopConfig")),
                    extractionSpec = ExtractionSpec.fromJson(obj.optJSONObject("extractionSpec")),
                    recoveryPolicy = RecoveryPolicy.fromJson(obj.optJSONObject("recoveryPolicy")),
                    subWorkflowRef = SubWorkflowReference.fromJson(obj.optJSONObject("subWorkflowRef")),
                    variableName = obj.optString("variableName", ""),
                    variableType = WorkflowVariableType.fromId(obj.optString("variableType")),
                    allowCrossOriginTransition = obj.optBoolean("allowCrossOriginTransition", false),
                    vaultEntryId = obj.optString("vaultEntryId", "").trim().takeIf {
                        it.isNotEmpty() && !it.equals("null", ignoreCase = true)
                    },
                    vaultCategory = obj.optString("vaultCategory", "").trim(),
                    useVaultPool = obj.optBoolean("useVaultPool", false),
                    vaultFolderId = obj.optString("vaultFolderId", "").trim(),
                    rawActionType = preservedRawActionType,
                    unknownFieldsJson = unknownFieldsJson
                )
            )
        }
        return reindex(result)
    }

    fun decode(json: String?): List<AutomationStep> = decodeSafely(json).stepsOrEmpty()

    fun reindex(steps: List<AutomationStep>): List<AutomationStep> =
        steps.mapIndexed { idx, step -> step.copy(order = idx + 1) }

    fun isClauseDestructive(lowerClause: String): Boolean {
        return destructiveKeywords.any { kw ->
            if (kw == "delete" || kw == "pay" || kw == "submit") {
                Regex("""\b${Regex.escape(kw)}\b""").containsMatchIn(lowerClause)
            } else {
                lowerClause.contains(kw)
            }
        }
    }

    fun applyStepEdit(
        existing: AutomationStep,
        description: String,
        actionType: AutomationActionType,
        value: String,
        targetLabel: String = existing.targetLabel,
        selector: String = existing.selector,
        destructiveClassifier: (String) -> Boolean = { clause -> isClauseDestructive(clause) },
        credentialReferenceId: String? = existing.credentialReferenceId,
        credentialFieldRole: CredentialFieldRole? = existing.credentialFieldRole,
        expectedOrigin: String = existing.expectedOrigin,
        requiresCredential: Boolean = existing.requiresCredential,
        vaultEntryId: String? = existing.vaultEntryId,
        vaultCategory: String = existing.vaultCategory,
        useVaultPool: Boolean = existing.useVaultPool,
        vaultFolderId: String = existing.vaultFolderId
    ): AutomationStep {
        val cleanDescription = description.trim().ifBlank { existing.description }
        val cleanTarget = targetLabel.trim().ifBlank { existing.targetLabel }
        val cleanSelector = selector.trim()
        val effectiveRequiresCred = requiresCredential ||
            !credentialReferenceId.isNullOrBlank() ||
            actionType == AutomationActionType.FILL_CREDENTIAL
        val cleanVaultId = vaultEntryId?.trim()?.takeIf { it.isNotEmpty() }
        val cleanValue = if (effectiveRequiresCred) "" else value
        val preservedOrEscalatedDestructive = existing.isDestructive ||
            actionType == AutomationActionType.SUBMIT ||
            destructiveClassifier(cleanDescription.lowercase()) ||
            destructiveClassifier(cleanTarget.lowercase()) ||
            destructiveClassifier(cleanValue.lowercase())
        val updatedCandidates = if (cleanSelector.isNotEmpty() && cleanSelector != existing.selector) {
            listOf(
                LocatorCandidate(
                    strategy = LocatorStrategy.SCOPED_CSS,
                    selector = cleanSelector,
                    confidence = 0.90f,
                    isUnique = true
                )
            ) + existing.locatorCandidates.filterNot { it.selector == cleanSelector }
        } else {
            existing.locatorCandidates
        }
        val effectiveUsePool = useVaultPool && cleanVaultId == null
        return existing.copy(
            actionType = actionType,
            targetLabel = cleanTarget,
            selector = cleanSelector,
            value = cleanValue,
            description = cleanDescription,
            isDestructive = preservedOrEscalatedDestructive,
            credentialReferenceId = credentialReferenceId?.trim()?.takeIf { it.isNotEmpty() },
            credentialFieldRole = credentialFieldRole,
            expectedOrigin = expectedOrigin.trim(),
            requiresCredential = effectiveRequiresCred,
            vaultEntryId = cleanVaultId,
            vaultCategory = if (cleanVaultId != null || effectiveUsePool) vaultCategory.trim() else "",
            useVaultPool = effectiveUsePool,
            vaultFolderId = if (cleanVaultId != null || effectiveUsePool) vaultFolderId.trim() else "",
            locatorCandidates = updatedCandidates
        )
    }

    fun buildStepSummary(
        actionType: AutomationActionType,
        targetLabel: String,
        value: String,
        isCredentialStep: Boolean = false,
        credentialFieldRole: CredentialFieldRole? = null,
        useVaultPool: Boolean = false,
        vaultCategoryLabel: String = ""
    ): String {
        val cleanTarget = targetLabel.trim().ifBlank { "Element" }
        if (isCredentialStep || actionType == AutomationActionType.FILL_CREDENTIAL) {
            val roleLabel = credentialFieldRole?.label?.lowercase() ?: "saved credential"
            return "Fill $roleLabel into $cleanTarget"
        }
        if (useVaultPool && (actionType == AutomationActionType.INPUT || actionType == AutomationActionType.SELECT || actionType == AutomationActionType.TOGGLE) && value.isBlank()) {
            val poolLabel = vaultCategoryLabel.trim().ifBlank { "Automation Vault" }
            return "Fill $poolLabel (Vault Pool) into $cleanTarget"
        }
        return when (actionType) {
            AutomationActionType.CLICK -> "$cleanTarget clicked"
            AutomationActionType.DOUBLE_CLICK -> "Double-clicked $cleanTarget"
            AutomationActionType.LONG_PRESS -> "Long-pressed $cleanTarget"
            AutomationActionType.INPUT -> if (value.isNotBlank()) {
                val safeShown = if (cleanTarget.contains("password", ignoreCase = true)) "••••" else value.take(28)
                "Entered \"$safeShown\" in $cleanTarget"
            } else {
                "$cleanTarget updated"
            }
            AutomationActionType.FILL_CREDENTIAL -> "Fill saved credential into $cleanTarget"
            AutomationActionType.CLEAR -> "Cleared $cleanTarget"
            AutomationActionType.SELECT -> if (value.isNotBlank()) {
                "Selected \"${value.take(28)}\" in $cleanTarget"
            } else {
                "$cleanTarget selected"
            }
            AutomationActionType.TOGGLE -> when {
                value.equals("checked", ignoreCase = true) -> "Checked $cleanTarget"
                value.equals("unchecked", ignoreCase = true) -> "Unchecked $cleanTarget"
                else -> "$cleanTarget toggled"
            }
            AutomationActionType.SCROLL -> "Scroll $cleanTarget"
            AutomationActionType.FOCUS -> "Focused $cleanTarget"
            AutomationActionType.BLUR -> "Blurred $cleanTarget"
            AutomationActionType.KEY_PRESS -> if (value.isNotBlank()) {
                "Pressed ${value.take(20)} on $cleanTarget"
            } else {
                "Pressed key on $cleanTarget"
            }
            AutomationActionType.WAIT -> if (value.isNotBlank()) {
                "Wait: ${value.take(40)}"
            } else {
                "Wait for $cleanTarget"
            }
            AutomationActionType.NAVIGATE -> "Navigate to $cleanTarget"
            AutomationActionType.EXTRACT -> "Extract $cleanTarget"
            AutomationActionType.SUBMIT -> "Submit $cleanTarget"
            AutomationActionType.CONDITION -> "If condition: $cleanTarget"
            AutomationActionType.LOOP -> "Loop: $cleanTarget"
            AutomationActionType.SET_VARIABLE -> "Set variable $cleanTarget"
            AutomationActionType.CALL_WORKFLOW -> "Run sub-workflow $cleanTarget"
            AutomationActionType.CLEAR_DATA -> "Clear browser data (cookies, cache & storage)"
            AutomationActionType.UNSUPPORTED -> "Unsupported action ($cleanTarget)"
        }
    }
}
