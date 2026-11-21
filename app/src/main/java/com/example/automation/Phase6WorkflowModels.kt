package com.example.automation

import org.json.JSONArray
import org.json.JSONObject

object Phase6Limits {
    const val MAX_CONDITION_DEPTH = 5
    const val MAX_LOOP_ITERATIONS = 25
    const val MAX_TOTAL_STEP_EXECUTIONS_PER_RUN = 200
    const val MAX_SUBWORKFLOW_DEPTH = 4
    const val MAX_RECOVERY_RETRIES = 3
    const val MAX_EXTRACT_LIST_ITEMS = 20
}

enum class WorkflowVariableType(val id: String, val label: String) {
    TEXT("TEXT", "Text"),
    NUMBER("NUMBER", "Number"),
    BOOLEAN("BOOLEAN", "Boolean"),
    URL("URL", "URL");

    companion object {
        fun fromId(raw: String?): WorkflowVariableType =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: TEXT
    }
}

data class WorkflowVariableDefinition(
    val name: String,
    val type: WorkflowVariableType = WorkflowVariableType.TEXT,
    val defaultValue: String = "",
    val isInput: Boolean = false,
    val isOutput: Boolean = false,
    val description: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("type", type.id)
        if (defaultValue.isNotEmpty()) put("defaultValue", defaultValue)
        if (isInput) put("isInput", true)
        if (isOutput) put("isOutput", true)
        if (description.isNotBlank()) put("description", description)
    }

    companion object {
        fun fromJson(obj: JSONObject?): WorkflowVariableDefinition? {
            if (obj == null) return null
            val name = obj.optString("name", "").trim()
            if (name.isEmpty()) return null
            return WorkflowVariableDefinition(
                name = name,
                type = WorkflowVariableType.fromId(obj.optString("type")),
                defaultValue = obj.optString("defaultValue", ""),
                isInput = obj.optBoolean("isInput", false),
                isOutput = obj.optBoolean("isOutput", false),
                description = obj.optString("description", "")
            )
        }
    }
}

data class TypedVariableValue(
    val name: String,
    val type: WorkflowVariableType,
    val rawValue: String,
    val isList: Boolean = false,
    val listValues: List<String> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("type", type.id)
        put("rawValue", rawValue)
        if (isList) {
            put("isList", true)
            put("listValues", JSONArray(listValues))
        }
    }

    companion object {
        fun fromJson(obj: JSONObject?): TypedVariableValue? {
            if (obj == null) return null
            val name = obj.optString("name", "").trim()
            if (name.isEmpty()) return null
            val listArr = obj.optJSONArray("listValues")
            val listVals = if (listArr != null) {
                buildList {
                    for (i in 0 until listArr.length()) {
                        add(listArr.optString(i, ""))
                    }
                }
            } else {
                emptyList()
            }
            return TypedVariableValue(
                name = name,
                type = WorkflowVariableType.fromId(obj.optString("type")),
                rawValue = obj.optString("rawValue", ""),
                isList = obj.optBoolean("isList", false),
                listValues = listVals
            )
        }
    }
}

data class VariableDisplayBadge(
    val name: String,
    val type: WorkflowVariableType,
    val previewValue: String,
    val isMasked: Boolean = false
)

enum class ConditionOperator(val id: String, val label: String, val requiresDomTarget: Boolean) {
    ELEMENT_EXISTS("ELEMENT_EXISTS", "Element exists", true),
    ELEMENT_NOT_EXISTS("ELEMENT_NOT_EXISTS", "Element does not exist", true),
    ELEMENT_VISIBLE("ELEMENT_VISIBLE", "Element is visible", true),
    ELEMENT_ENABLED("ELEMENT_ENABLED", "Element is enabled", true),
    ELEMENT_CONTAINS_TEXT("ELEMENT_CONTAINS_TEXT", "Element contains text", true),
    INPUT_VALUE_EQUALS("INPUT_VALUE_EQUALS", "Input value equals", true),
    CHECKBOX_CHECKED("CHECKBOX_CHECKED", "Checkbox is checked", true),
    URL_MATCHES("URL_MATCHES", "URL matches pattern", false),
    ORIGIN_MATCHES("ORIGIN_MATCHES", "Page origin matches", false),
    PREVIOUS_STEP_SUCCEEDED("PREVIOUS_STEP_SUCCEEDED", "Previous step succeeded", false),
    PREVIOUS_STEP_FAILED("PREVIOUS_STEP_FAILED", "Previous step failed", false),
    VARIABLE_EQUALS("VARIABLE_EQUALS", "Variable equals", false),
    VARIABLE_CONTAINS("VARIABLE_CONTAINS", "Variable contains", false),
    VARIABLE_NOT_EMPTY("VARIABLE_NOT_EMPTY", "Variable is not empty", false),
    VARIABLE_GREATER_THAN("VARIABLE_GREATER_THAN", "Variable greater than", false),
    VARIABLE_LESS_THAN("VARIABLE_LESS_THAN", "Variable less than", false);

    companion object {
        fun fromId(raw: String?): ConditionOperator? =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) }
    }
}

enum class LogicalCombinator(val id: String, val label: String) {
    AND("AND", "ALL match (AND)"),
    OR("OR", "ANY matches (OR)");

    companion object {
        fun fromId(raw: String?): LogicalCombinator =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: AND
    }
}

sealed class ConditionNode {
    data class Predicate(
        val operator: ConditionOperator,
        val selector: String = "",
        val targetLabel: String = "",
        val expectedValue: String = "",
        val variableName: String = "",
        val negate: Boolean = false
    ) : ConditionNode()

    data class Group(
        val combinator: LogicalCombinator = LogicalCombinator.AND,
        val children: List<ConditionNode> = emptyList(),
        val negate: Boolean = false
    ) : ConditionNode()

    fun depth(): Int = when (this) {
        is Predicate -> 1
        is Group -> 1 + (children.maxOfOrNull { it.depth() } ?: 0)
    }

    fun toJson(): JSONObject = when (this) {
        is Predicate -> JSONObject().apply {
            put("nodeType", "PREDICATE")
            put("operator", operator.id)
            if (selector.isNotBlank()) put("selector", selector)
            if (targetLabel.isNotBlank()) put("targetLabel", targetLabel)
            if (expectedValue.isNotBlank()) put("expectedValue", expectedValue)
            if (variableName.isNotBlank()) put("variableName", variableName)
            if (negate) put("negate", true)
        }
        is Group -> JSONObject().apply {
            put("nodeType", "GROUP")
            put("combinator", combinator.id)
            if (negate) put("negate", true)
            val arr = JSONArray()
            children.forEach { arr.put(it.toJson()) }
            put("children", arr)
        }
    }

    fun toHumanSummary(): String = when (this) {
        is Predicate -> {
            val prefix = if (negate) "NOT " else ""
            val target = targetLabel.ifBlank { selector }.ifBlank { variableName }.ifBlank { expectedValue }
            when (operator) {
                ConditionOperator.VARIABLE_EQUALS,
                ConditionOperator.VARIABLE_CONTAINS,
                ConditionOperator.VARIABLE_NOT_EMPTY,
                ConditionOperator.VARIABLE_GREATER_THAN,
                ConditionOperator.VARIABLE_LESS_THAN ->
                    "${prefix}\${$variableName} ${operator.label.lowercase()} ${expectedValue}".trim()
                ConditionOperator.PREVIOUS_STEP_SUCCEEDED,
                ConditionOperator.PREVIOUS_STEP_FAILED -> "${prefix}${operator.label}"
                else -> "${prefix}${operator.label}: $target ${expectedValue}".trim()
            }
        }
        is Group -> {
            val prefix = if (negate) "NOT (" else "("
            prefix + children.joinToString(" ${combinator.id} ") { it.toHumanSummary() } + ")"
        }
    }

    companion object {
        fun fromJson(obj: JSONObject?): ConditionNode? {
            if (obj == null) return null
            val nodeType = obj.optString("nodeType", "").trim().uppercase()
            return if (nodeType == "GROUP" || obj.has("children")) {
                val arr = obj.optJSONArray("children") ?: JSONArray()
                val kids = buildList {
                    for (i in 0 until arr.length()) {
                        fromJson(arr.optJSONObject(i))?.let { add(it) }
                    }
                }
                Group(
                    combinator = LogicalCombinator.fromId(obj.optString("combinator")),
                    children = kids,
                    negate = obj.optBoolean("negate", false)
                )
            } else {
                val op = ConditionOperator.fromId(obj.optString("operator")) ?: return null
                Predicate(
                    operator = op,
                    selector = obj.optString("selector", ""),
                    targetLabel = obj.optString("targetLabel", ""),
                    expectedValue = obj.optString("expectedValue", ""),
                    variableName = obj.optString("variableName", ""),
                    negate = obj.optBoolean("negate", false)
                )
            }
        }
    }
}

data class ElseIfBranch(
    val id: String,
    val label: String = "Else If",
    val condition: ConditionNode,
    val steps: List<AutomationStep> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("label", label)
        put("condition", condition.toJson())
        put("steps", AutomationStepCodec.encodeStepsArray(steps))
    }

    companion object {
        fun fromJson(obj: JSONObject?): ElseIfBranch? {
            if (obj == null) return null
            val cond = ConditionNode.fromJson(obj.optJSONObject("condition")) ?: return null
            val stepsArr = obj.optJSONArray("steps") ?: JSONArray()
            return ElseIfBranch(
                id = obj.optString("id", "elseif_branch"),
                label = obj.optString("label", "Else If"),
                condition = cond,
                steps = AutomationStepCodec.parseStepsArray(stepsArr)
            )
        }
    }
}

data class LoopConfig(
    val maxIterations: Int = 5,
    val continueCondition: ConditionNode? = null,
    val iterationVariable: String = "loop_index"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("maxIterations", maxIterations.coerceIn(1, Phase6Limits.MAX_LOOP_ITERATIONS))
        if (continueCondition != null) put("continueCondition", continueCondition.toJson())
        if (iterationVariable.isNotBlank()) put("iterationVariable", iterationVariable)
    }

    companion object {
        fun fromJson(obj: JSONObject?): LoopConfig? {
            if (obj == null) return null
            return LoopConfig(
                maxIterations = obj.optInt("maxIterations", 5).coerceIn(1, Phase6Limits.MAX_LOOP_ITERATIONS),
                continueCondition = ConditionNode.fromJson(obj.optJSONObject("continueCondition")),
                iterationVariable = obj.optString("iterationVariable", "loop_index")
            )
        }
    }
}

enum class ExtractionMode(val id: String, val label: String) {
    VISIBLE_TEXT("VISIBLE_TEXT", "Visible Text"),
    INPUT_VALUE("INPUT_VALUE", "Input Value"),
    SELECTED_OPTION("SELECTED_OPTION", "Selected Option"),
    LINK_URL("LINK_URL", "Link URL"),
    ATTRIBUTE("ATTRIBUTE", "Element Attribute"),
    PAGE_TITLE("PAGE_TITLE", "Page Title"),
    PAGE_URL("PAGE_URL", "Current URL"),
    PAGE_META("PAGE_META", "Page Metadata");

    companion object {
        fun fromId(raw: String?): ExtractionMode =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: VISIBLE_TEXT
    }
}

data class ExtractionSpec(
    val mode: ExtractionMode = ExtractionMode.VISIBLE_TEXT,
    val attributeName: String = "",
    val allowMultiple: Boolean = false,
    val maxItems: Int = 10,
    val rejectAmbiguous: Boolean = true,
    val outputVariableName: String = "",
    val metaKey: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("mode", mode.id)
        if (attributeName.isNotBlank()) put("attributeName", attributeName)
        if (allowMultiple) put("allowMultiple", true)
        put("maxItems", maxItems.coerceIn(1, Phase6Limits.MAX_EXTRACT_LIST_ITEMS))
        put("rejectAmbiguous", rejectAmbiguous)
        if (outputVariableName.isNotBlank()) put("outputVariableName", outputVariableName)
        if (metaKey.isNotBlank()) put("metaKey", metaKey)
    }

    companion object {
        val ALLOWLISTED_ATTRIBUTES = setOf(
            "href", "src", "alt", "title", "aria-label", "data-testid",
            "data-test", "role", "type", "name", "placeholder", "lang"
        )

        fun fromJson(obj: JSONObject?): ExtractionSpec? {
            if (obj == null) return null
            return ExtractionSpec(
                mode = ExtractionMode.fromId(obj.optString("mode")),
                attributeName = obj.optString("attributeName", ""),
                allowMultiple = obj.optBoolean("allowMultiple", false),
                maxItems = obj.optInt("maxItems", 10).coerceIn(1, Phase6Limits.MAX_EXTRACT_LIST_ITEMS),
                rejectAmbiguous = obj.optBoolean("rejectAmbiguous", true),
                outputVariableName = obj.optString("outputVariableName", ""),
                metaKey = obj.optString("metaKey", "")
            )
        }
    }
}

enum class RecoveryStrategy(val id: String, val label: String) {
    RESCAN_AND_RETRY("RESCAN_AND_RETRY", "Re-scan DOM & Retry"),
    WAIT_THEN_RETRY("WAIT_THEN_RETRY", "Wait & Retry"),
    ALTERNATIVE_BRANCH("ALTERNATIVE_BRANCH", "Run Fallback Branch"),
    STOP_SAFELY("STOP_SAFELY", "Stop Safely");

    companion object {
        fun fromId(raw: String?): RecoveryStrategy =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: RESCAN_AND_RETRY
    }
}

data class RecoveryPolicy(
    val strategy: RecoveryStrategy = RecoveryStrategy.RESCAN_AND_RETRY,
    val maxRetries: Int = 1,
    val retryOnCodes: Set<AutomationErrorCode> = setOf(
        AutomationErrorCode.ELEMENT_NOT_FOUND,
        AutomationErrorCode.ELEMENT_STALE,
        AutomationErrorCode.ELEMENT_NOT_INTERACTABLE,
        AutomationErrorCode.JS_TIMEOUT
    ),
    val rescanBeforeRetry: Boolean = true,
    val waitBeforeRetry: WaitConditionSpec? = null,
    val alternativeSteps: List<AutomationStep> = emptyList(),
    val requireConfirmForUnsafeRetry: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("strategy", strategy.id)
        put("maxRetries", maxRetries.coerceIn(0, Phase6Limits.MAX_RECOVERY_RETRIES))
        put("retryOnCodes", JSONArray(retryOnCodes.map { it.name }))
        put("rescanBeforeRetry", rescanBeforeRetry)
        if (waitBeforeRetry != null) put("waitBeforeRetry", waitBeforeRetry.toJson())
        if (alternativeSteps.isNotEmpty()) {
            put("alternativeSteps", AutomationStepCodec.encodeStepsArray(alternativeSteps))
        }
        put("requireConfirmForUnsafeRetry", requireConfirmForUnsafeRetry)
    }

    companion object {
        fun fromJson(obj: JSONObject?): RecoveryPolicy? {
            if (obj == null) return null
            val codesArr = obj.optJSONArray("retryOnCodes")
            val codes = if (codesArr != null) {
                buildSet {
                    for (i in 0 until codesArr.length()) {
                        val name = codesArr.optString(i, "")
                        AutomationErrorCode.entries.firstOrNull { it.name == name }?.let { add(it) }
                    }
                }
            } else {
                setOf(AutomationErrorCode.ELEMENT_NOT_FOUND, AutomationErrorCode.ELEMENT_STALE)
            }
            val altArr = obj.optJSONArray("alternativeSteps") ?: JSONArray()
            return RecoveryPolicy(
                strategy = RecoveryStrategy.fromId(obj.optString("strategy")),
                maxRetries = obj.optInt("maxRetries", 1).coerceIn(0, Phase6Limits.MAX_RECOVERY_RETRIES),
                retryOnCodes = codes,
                rescanBeforeRetry = obj.optBoolean("rescanBeforeRetry", true),
                waitBeforeRetry = WaitConditionSpec.fromJson(obj.optJSONObject("waitBeforeRetry")),
                alternativeSteps = AutomationStepCodec.parseStepsArray(altArr),
                requireConfirmForUnsafeRetry = obj.optBoolean("requireConfirmForUnsafeRetry", true)
            )
        }
    }
}

data class SubWorkflowReference(
    val targetWorkflowId: Long,
    val targetWorkflowName: String = "",
    val inputMappings: Map<String, String> = emptyMap(),
    val outputMappings: Map<String, String> = emptyMap(),
    val targetWorkflowUuid: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("targetWorkflowId", targetWorkflowId)
        if (targetWorkflowName.isNotBlank()) put("targetWorkflowName", targetWorkflowName)
        if (targetWorkflowUuid.isNotBlank()) put("targetWorkflowUuid", targetWorkflowUuid)
        if (inputMappings.isNotEmpty()) {
            val inObj = JSONObject()
            inputMappings.forEach { (k, v) -> inObj.put(k, v) }
            put("inputMappings", inObj)
        }
        if (outputMappings.isNotEmpty()) {
            val outObj = JSONObject()
            outputMappings.forEach { (k, v) -> outObj.put(k, v) }
            put("outputMappings", outObj)
        }
    }

    companion object {
        fun fromJson(obj: JSONObject?): SubWorkflowReference? {
            if (obj == null) return null
            val wfId = obj.optLong("targetWorkflowId", 0L)
            val wfUuid = obj.optString("targetWorkflowUuid", "").trim()
            if (wfId == 0L && wfUuid.isEmpty()) return null
            val inMap = obj.optJSONObject("inputMappings")?.let { inObj ->
                buildMap {
                    val keys = inObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        put(k, inObj.optString(k, ""))
                    }
                }
            } ?: emptyMap()
            val outMap = obj.optJSONObject("outputMappings")?.let { outObj ->
                buildMap {
                    val keys = outObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        put(k, outObj.optString(k, ""))
                    }
                }
            } ?: emptyMap()
            return SubWorkflowReference(
                targetWorkflowId = wfId,
                targetWorkflowName = obj.optString("targetWorkflowName", ""),
                inputMappings = inMap,
                outputMappings = outMap,
                targetWorkflowUuid = wfUuid
            )
        }
    }
}

data class WorkflowDocumentMetadata(
    val variables: List<WorkflowVariableDefinition> = emptyList(),
    val allowedOrigins: List<String> = emptyList(),
    val defaultRecoveryPolicy: RecoveryPolicy? = null,
    val allowMultiPageContinuity: Boolean = true,
    val isReusableComponent: Boolean = false,
    val repeatCount: Int = 1,
    val clearDataAfterRun: Boolean = false
) {
    val isEmptyDefault: Boolean
        get() = variables.isEmpty() &&
            allowedOrigins.isEmpty() &&
            defaultRecoveryPolicy == null &&
            allowMultiPageContinuity &&
            !isReusableComponent &&
            repeatCount == 1 &&
            !clearDataAfterRun

    fun toJson(): JSONObject = JSONObject().apply {
        if (variables.isNotEmpty()) {
            val arr = JSONArray()
            variables.forEach { arr.put(it.toJson()) }
            put("variables", arr)
        }
        if (allowedOrigins.isNotEmpty()) {
            put("allowedOrigins", JSONArray(allowedOrigins))
        }
        if (defaultRecoveryPolicy != null) {
            put("defaultRecoveryPolicy", defaultRecoveryPolicy.toJson())
        }
        put("allowMultiPageContinuity", allowMultiPageContinuity)
        if (isReusableComponent) put("isReusableComponent", true)
        if (repeatCount != 1) put("repeatCount", repeatCount.coerceIn(1, 500))
        if (clearDataAfterRun) put("clearDataAfterRun", true)
    }

    companion object {
        fun fromJson(obj: JSONObject?): WorkflowDocumentMetadata {
            if (obj == null) return WorkflowDocumentMetadata()
            val varsArr = obj.optJSONArray("variables")
            val vars = if (varsArr != null) {
                buildList {
                    for (i in 0 until varsArr.length()) {
                        WorkflowVariableDefinition.fromJson(varsArr.optJSONObject(i))?.let { add(it) }
                    }
                }
            } else {
                emptyList()
            }
            val originsArr = obj.optJSONArray("allowedOrigins")
            val origins = if (originsArr != null) {
                buildList {
                    for (i in 0 until originsArr.length()) {
                        originsArr.optString(i, "").trim().takeIf { it.isNotEmpty() }?.let { add(it) }
                    }
                }
            } else {
                emptyList()
            }
            return WorkflowDocumentMetadata(
                variables = vars,
                allowedOrigins = origins,
                defaultRecoveryPolicy = RecoveryPolicy.fromJson(obj.optJSONObject("defaultRecoveryPolicy")),
                allowMultiPageContinuity = obj.optBoolean("allowMultiPageContinuity", true),
                isReusableComponent = obj.optBoolean("isReusableComponent", false),
                repeatCount = obj.optInt("repeatCount", 1).coerceIn(1, 500),
                clearDataAfterRun = obj.optBoolean("clearDataAfterRun", false)
            )
        }
    }
}

data class WorkflowDocument(
    val schemaVersion: Int = AutomationStepCodec.CURRENT_SCHEMA_VERSION,
    val steps: List<AutomationStep> = emptyList(),
    val metadata: WorkflowDocumentMetadata = WorkflowDocumentMetadata(),
    val isLegacyUnversioned: Boolean = false
)

data class BranchTraceRecord(
    val stepId: String,
    val branchLabel: String,
    val conditionSummary: String,
    val matched: Boolean,
    val nestedDepth: Int = 0,
    val executedStepsCount: Int = 0,
    val subWorkflowName: String = "",
    val timestampMs: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("stepId", stepId)
        put("branchLabel", branchLabel)
        put("conditionSummary", conditionSummary)
        put("matched", matched)
        put("nestedDepth", nestedDepth)
        put("executedStepsCount", executedStepsCount)
        if (subWorkflowName.isNotBlank()) put("subWorkflowName", subWorkflowName)
        put("timestampMs", timestampMs)
    }

    companion object {
        fun fromJson(obj: JSONObject?): BranchTraceRecord? {
            if (obj == null) return null
            return BranchTraceRecord(
                stepId = obj.optString("stepId", ""),
                branchLabel = obj.optString("branchLabel", ""),
                conditionSummary = obj.optString("conditionSummary", ""),
                matched = obj.optBoolean("matched", false),
                nestedDepth = obj.optInt("nestedDepth", 0),
                executedStepsCount = obj.optInt("executedStepsCount", 0),
                subWorkflowName = obj.optString("subWorkflowName", ""),
                timestampMs = obj.optLong("timestampMs", 0L)
            )
        }
    }
}

data class RecoveryAttemptRecord(
    val stepId: String,
    val attemptNumber: Int,
    val triggerErrorCode: AutomationErrorCode,
    val strategy: RecoveryStrategy,
    val succeeded: Boolean,
    val safeMessage: String,
    val originalFailureMessage: String = "",
    val timestampMs: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("stepId", stepId)
        put("attemptNumber", attemptNumber)
        put("triggerErrorCode", triggerErrorCode.name)
        put("strategy", strategy.id)
        put("succeeded", succeeded)
        put("safeMessage", safeMessage)
        if (originalFailureMessage.isNotBlank()) put("originalFailureMessage", originalFailureMessage)
        put("timestampMs", timestampMs)
    }

    companion object {
        fun fromJson(obj: JSONObject?): RecoveryAttemptRecord? {
            if (obj == null) return null
            val errName = obj.optString("triggerErrorCode", "NONE")
            val code = AutomationErrorCode.entries.firstOrNull { it.name == errName } ?: AutomationErrorCode.NONE
            return RecoveryAttemptRecord(
                stepId = obj.optString("stepId", ""),
                attemptNumber = obj.optInt("attemptNumber", 1),
                triggerErrorCode = code,
                strategy = RecoveryStrategy.fromId(obj.optString("strategy")),
                succeeded = obj.optBoolean("succeeded", false),
                safeMessage = obj.optString("safeMessage", ""),
                originalFailureMessage = obj.optString("originalFailureMessage", ""),
                timestampMs = obj.optLong("timestampMs", 0L)
            )
        }
    }
}

data class PersistedExecutionCheckpoint(
    val executionId: Long,
    val workflowId: Long,
    val workflowName: String,
    val tabId: String,
    val boundOrigin: String,
    val lastUrl: String,
    val navigationEpoch: Long = 0L,
    val urlCommitSeq: Long = 0L,
    val nextTopLevelIndex: Int = 0,
    val totalTopLevelSteps: Int = 0,
    val completedStepsCount: Int = 0,
    val safeVariables: Map<String, TypedVariableValue> = emptyMap(),
    val completedStepIds: List<String> = emptyList(),
    val failedStepSummary: String = "",
    val branchTraces: List<BranchTraceRecord> = emptyList(),
    val recoveryAttempts: List<RecoveryAttemptRecord> = emptyList(),
    val requiresCredentialReauth: Boolean = false,
    val interruptedReason: String = "",
    val savedAtMs: Long = System.currentTimeMillis()
) {
    fun toJsonString(): String = JSONObject().apply {
        put("executionId", executionId)
        put("workflowId", workflowId)
        put("workflowName", workflowName)
        put("tabId", tabId)
        put("boundOrigin", boundOrigin)
        put("lastUrl", lastUrl)
        put("navigationEpoch", navigationEpoch)
        put("urlCommitSeq", urlCommitSeq)
        put("nextTopLevelIndex", nextTopLevelIndex)
        put("totalTopLevelSteps", totalTopLevelSteps)
        put("completedStepsCount", completedStepsCount)
        val varsObj = JSONObject()
        safeVariables.forEach { (k, v) -> varsObj.put(k, v.toJson()) }
        put("safeVariables", varsObj)
        put("completedStepIds", JSONArray(completedStepIds))
        if (failedStepSummary.isNotBlank()) put("failedStepSummary", failedStepSummary)
        val btArr = JSONArray()
        branchTraces.forEach { btArr.put(it.toJson()) }
        put("branchTraces", btArr)
        val raArr = JSONArray()
        recoveryAttempts.forEach { raArr.put(it.toJson()) }
        put("recoveryAttempts", raArr)
        put("requiresCredentialReauth", requiresCredentialReauth)
        put("interruptedReason", interruptedReason)
        put("savedAtMs", savedAtMs)
    }.toString()

    companion object {
        fun fromJsonString(raw: String?): PersistedExecutionCheckpoint? {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            return runCatching {
                val obj = JSONObject(trimmed)
                val vars = buildMap {
                    val vObj = obj.optJSONObject("safeVariables")
                    if (vObj != null) {
                        val keys = vObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            TypedVariableValue.fromJson(vObj.optJSONObject(k))?.let { put(k, it) }
                        }
                    }
                }
                val completedArr = obj.optJSONArray("completedStepIds")
                val completedIds = if (completedArr != null) {
                    buildList {
                        for (i in 0 until completedArr.length()) {
                            add(completedArr.optString(i, ""))
                        }
                    }
                } else {
                    emptyList()
                }
                val btArr = obj.optJSONArray("branchTraces")
                val traces = if (btArr != null) {
                    buildList {
                        for (i in 0 until btArr.length()) {
                            BranchTraceRecord.fromJson(btArr.optJSONObject(i))?.let { add(it) }
                        }
                    }
                } else {
                    emptyList()
                }
                val raArr = obj.optJSONArray("recoveryAttempts")
                val attempts = if (raArr != null) {
                    buildList {
                        for (i in 0 until raArr.length()) {
                            RecoveryAttemptRecord.fromJson(raArr.optJSONObject(i))?.let { add(it) }
                        }
                    }
                } else {
                    emptyList()
                }
                PersistedExecutionCheckpoint(
                    executionId = obj.optLong("executionId", 0L),
                    workflowId = obj.optLong("workflowId", 0L),
                    workflowName = obj.optString("workflowName", ""),
                    tabId = obj.optString("tabId", ""),
                    boundOrigin = obj.optString("boundOrigin", ""),
                    lastUrl = obj.optString("lastUrl", ""),
                    navigationEpoch = obj.optLong("navigationEpoch", 0L),
                    urlCommitSeq = obj.optLong("urlCommitSeq", 0L),
                    nextTopLevelIndex = obj.optInt("nextTopLevelIndex", 0),
                    totalTopLevelSteps = obj.optInt("totalTopLevelSteps", 0),
                    completedStepsCount = obj.optInt("completedStepsCount", 0),
                    safeVariables = vars,
                    completedStepIds = completedIds,
                    failedStepSummary = obj.optString("failedStepSummary", ""),
                    branchTraces = traces,
                    recoveryAttempts = attempts,
                    requiresCredentialReauth = obj.optBoolean("requiresCredentialReauth", false),
                    interruptedReason = obj.optString("interruptedReason", ""),
                    savedAtMs = obj.optLong("savedAtMs", 0L)
                )
            }.getOrNull()
        }

        fun fromJson(raw: String?): PersistedExecutionCheckpoint? = fromJsonString(raw)
    }
}

data class InterruptedExecutionCandidate(
    val checkpoint: PersistedExecutionCheckpoint,
    val canSafeResume: Boolean,
    val unsafeReason: String = "",
    val validationReason: String = unsafeReason,
    val requiresCredentialReauth: Boolean = checkpoint.requiresCredentialReauth
)
