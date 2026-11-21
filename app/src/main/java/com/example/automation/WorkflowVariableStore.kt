package com.example.automation

import java.net.URI

sealed class VariableResolutionResult {
    data class Success(val value: String) : VariableResolutionResult()
    data class Undefined(val variableName: String, val message: String) : VariableResolutionResult()
    data class Invalid(val variableName: String, val message: String) : VariableResolutionResult()
}

class WorkflowVariableStore(
    initialDefinitions: List<WorkflowVariableDefinition> = emptyList(),
    initialValues: Map<String, TypedVariableValue> = emptyMap()
) {
    private val definitions = linkedMapOf<String, WorkflowVariableDefinition>()
    private val values = linkedMapOf<String, TypedVariableValue>()

    init {
        initialDefinitions.forEach { def ->
            if (isValidVariableName(def.name) && !isBlockedCredentialVariableName(def.name)) {
                definitions[def.name] = def
                if (def.defaultValue.isNotEmpty()) {
                    setVariable(def.name, def.type, def.defaultValue)
                }
            }
        }
        initialValues.forEach { (name, typed) ->
            if (isValidVariableName(name) && !isBlockedCredentialVariableName(name)) {
                values[name] = typed
            }
        }
    }

    fun registerDefinition(def: WorkflowVariableDefinition): Result<Unit> {
        if (!isValidVariableName(def.name)) {
            return Result.failure(IllegalArgumentException("Invalid variable name '${def.name}'."))
        }
        if (isBlockedCredentialVariableName(def.name)) {
            return Result.failure(IllegalArgumentException("Variable '${def.name}' is reserved and cannot store credentials or secrets."))
        }
        definitions[def.name] = def
        if (def.defaultValue.isNotEmpty() && !values.containsKey(def.name)) {
            return setVariable(def.name, def.type, def.defaultValue)
        }
        return Result.success(Unit)
    }

    fun setVariable(
        name: String,
        type: WorkflowVariableType? = null,
        rawValue: String,
        isList: Boolean = false,
        listValues: List<String> = emptyList()
    ): Result<Unit> {
        val cleanName = name.trim()
        if (!isValidVariableName(cleanName)) {
            return Result.failure(IllegalArgumentException("Invalid variable name '$cleanName'. Use letters, numbers, and underscores."))
        }
        if (isBlockedCredentialVariableName(cleanName)) {
            return Result.failure(IllegalArgumentException("Variable '$cleanName' cannot store credential or secret values."))
        }
        val effectiveType = type ?: definitions[cleanName]?.type ?: WorkflowVariableType.TEXT
        val normalizedResult = normalizeTypedValue(effectiveType, rawValue)
        if (normalizedResult.isFailure) {
            return Result.failure(normalizedResult.exceptionOrNull()!!)
        }
        val normalized = normalizedResult.getOrThrow()
        values[cleanName] = TypedVariableValue(
            name = cleanName,
            type = effectiveType,
            rawValue = normalized,
            isList = isList,
            listValues = listValues.take(Phase6Limits.MAX_EXTRACT_LIST_ITEMS)
        )
        return Result.success(Unit)
    }

    fun getVariable(name: String): TypedVariableValue? = values[name.trim()]

    fun hasVariable(name: String): Boolean = values.containsKey(name.trim())

    fun allNames(): Set<String> = values.keys.toSet()

    fun snapshotValues(): Map<String, TypedVariableValue> = values.toMap()

    fun snapshotDisplayBadges(): List<VariableDisplayBadge> = values.values.map { entry ->
        val shouldMask = shouldMaskInUi(entry.name)
        val preview = when {
            shouldMask -> "••••"
            entry.isList -> "[${entry.listValues.size} items] ${entry.listValues.take(2).joinToString(", ").take(36)}"
            else -> entry.rawValue.take(40)
        }
        VariableDisplayBadge(
            name = entry.name,
            type = entry.type,
            previewValue = preview,
            isMasked = shouldMask
        )
    }

    fun resolveTemplate(template: String, strictUndefined: Boolean = true): VariableResolutionResult {
        if (!template.contains("\${")) return VariableResolutionResult.Success(template)
        val matcher = TEMPLATE_TOKEN_REGEX.findAll(template).toList()
        if (matcher.isEmpty()) return VariableResolutionResult.Success(template)
        val sb = StringBuilder(template.length)
        var lastIndex = 0
        for (m in matcher) {
            sb.append(template, lastIndex, m.range.first)
            val varName = m.groupValues[1].trim()
            if (!isValidVariableName(varName)) {
                return VariableResolutionResult.Invalid(
                    variableName = varName,
                    message = "Invalid variable reference '\${$varName}'."
                )
            }
            if (isBlockedCredentialVariableName(varName)) {
                return VariableResolutionResult.Invalid(
                    variableName = varName,
                    message = "Variable '\${$varName}' is blocked for credential security."
                )
            }
            val stored = values[varName]
            if (stored == null) {
                if (strictUndefined) {
                    return VariableResolutionResult.Undefined(
                        variableName = varName,
                        message = "Undefined workflow variable '\${$varName}'."
                    )
                }
                sb.append(m.value)
            } else {
                sb.append(stored.rawValue)
            }
            lastIndex = m.range.last + 1
        }
        sb.append(template, lastIndex, template.length)
        return VariableResolutionResult.Success(sb.toString())
    }

    fun resolveStepParameters(step: AutomationStep): Result<AutomationStep> {
        if (step.isCredentialStep) {
            // Credential Vault steps strictly reject variable templating inside sensitive credential values
            return Result.success(step)
        }
        val fieldsToResolve = listOf(
            "value" to step.value,
            "selector" to step.selector,
            "targetLabel" to step.targetLabel
        )
        var resolvedValue = step.value
        var resolvedSelector = step.selector
        var resolvedTargetLabel = step.targetLabel
        for ((field, raw) in fieldsToResolve) {
            if (raw.contains("\${")) {
                when (val res = resolveTemplate(raw, strictUndefined = true)) {
                    is VariableResolutionResult.Success -> when (field) {
                        "value" -> resolvedValue = res.value
                        "selector" -> resolvedSelector = res.value
                        "targetLabel" -> resolvedTargetLabel = res.value
                    }
                    is VariableResolutionResult.Undefined -> {
                        return Result.failure(UndefinedVariableException(res.variableName, res.message))
                    }
                    is VariableResolutionResult.Invalid -> {
                        return Result.failure(InvalidVariableException(res.variableName, res.message))
                    }
                }
            }
        }
        return Result.success(
            step.copy(
                value = resolvedValue,
                selector = resolvedSelector,
                targetLabel = resolvedTargetLabel
            )
        )
    }

    fun createChildScope(
        childDefinitions: List<WorkflowVariableDefinition>,
        inputMappings: Map<String, String>
    ): Result<WorkflowVariableStore> {
        val childStore = WorkflowVariableStore(initialDefinitions = childDefinitions)
        for ((childVarName, parentSource) in inputMappings) {
            val cleanTarget = childVarName.trim()
            if (!isValidVariableName(cleanTarget) || isBlockedCredentialVariableName(cleanTarget)) {
                return Result.failure(InvalidVariableException(cleanTarget, "Invalid child input variable '$cleanTarget'."))
            }
            val resolvedVal = when {
                parentSource.contains("\${") -> {
                    when (val r = resolveTemplate(parentSource, strictUndefined = true)) {
                        is VariableResolutionResult.Success -> r.value
                        is VariableResolutionResult.Undefined -> return Result.failure(UndefinedVariableException(r.variableName, r.message))
                        is VariableResolutionResult.Invalid -> return Result.failure(InvalidVariableException(r.variableName, r.message))
                    }
                }
                hasVariable(parentSource) -> getVariable(parentSource)!!.rawValue
                else -> parentSource
            }
            val declaredType = childDefinitions.firstOrNull { it.name == cleanTarget }?.type
                ?: getVariable(parentSource)?.type
                ?: WorkflowVariableType.TEXT
            val setRes = childStore.setVariable(cleanTarget, declaredType, resolvedVal)
            if (setRes.isFailure) return Result.failure(setRes.exceptionOrNull()!!)
        }
        return Result.success(childStore)
    }

    fun applyChildOutputs(
        childStore: WorkflowVariableStore,
        outputMappings: Map<String, String>
    ): Result<Unit> {
        for ((parentVarName, childVarName) in outputMappings) {
            val cleanParent = parentVarName.trim()
            val cleanChild = childVarName.trim()
            if (!isValidVariableName(cleanParent) || isBlockedCredentialVariableName(cleanParent)) {
                return Result.failure(InvalidVariableException(cleanParent, "Invalid parent output variable '$cleanParent'."))
            }
            val childVal = childStore.getVariable(cleanChild)
                ?: return Result.failure(
                    UndefinedVariableException(
                        cleanChild,
                        "Sub-workflow output variable '$cleanChild' was not produced."
                    )
                )
            val targetType = definitions[cleanParent]?.type ?: childVal.type
            val setRes = setVariable(
                name = cleanParent,
                type = targetType,
                rawValue = childVal.rawValue,
                isList = childVal.isList,
                listValues = childVal.listValues
            )
            if (setRes.isFailure) return Result.failure(setRes.exceptionOrNull()!!)
        }
        return Result.success(Unit)
    }

    fun clear() {
        values.clear()
    }

    companion object {
        private val NAME_REGEX = Regex("^[a-zA-Z_][a-zA-Z0-9_]{0,63}$")
        private val TEMPLATE_TOKEN_REGEX = Regex("\\$\\{([^\\}]+)\\}")

        private val BLOCKED_CREDENTIAL_NAMES = setOf(
            "password", "passwd", "pwd", "passcode", "pin", "cvv", "cvc",
            "secret", "secret_key", "private_key", "otp", "totp", "ssn",
            "credit_card", "card_number", "vault_password", "auth_token"
        )

        fun isValidVariableName(name: String): Boolean = NAME_REGEX.matches(name.trim())

        fun isBlockedCredentialVariableName(name: String): Boolean {
            val norm = name.trim().lowercase()
            return norm in BLOCKED_CREDENTIAL_NAMES ||
                norm.contains("password") ||
                norm.contains("passwd") ||
                norm.contains("passcode") ||
                norm.contains("cvv") ||
                norm.contains("secret") ||
                norm.contains("private_key")
        }

        fun shouldMaskInUi(name: String): Boolean {
            val norm = name.trim().lowercase()
            return norm.contains("token") || norm.contains("email") || norm.contains("phone")
        }

        fun extractReferencedVariableNames(text: String): List<String> {
            if (!text.contains("\${")) return emptyList()
            return TEMPLATE_TOKEN_REGEX.findAll(text).map { it.groupValues[1].trim() }.toList()
        }

        fun normalizeTypedValue(type: WorkflowVariableType, raw: String): Result<String> {
            val trimmed = raw.trim()
            return when (type) {
                WorkflowVariableType.TEXT -> Result.success(raw)
                WorkflowVariableType.NUMBER -> {
                    val parsed = trimmed.toDoubleOrNull()
                    if (parsed == null || parsed.isNaN() || parsed.isInfinite()) {
                        Result.failure(IllegalArgumentException("Expected numeric value, got '$raw'."))
                    } else {
                        val formatted = if (parsed % 1.0 == 0.0 && trimmed.indexOf('.') == -1) {
                            parsed.toLong().toString()
                        } else {
                            trimmed
                        }
                        Result.success(formatted)
                    }
                }
                WorkflowVariableType.BOOLEAN -> {
                    when (trimmed.lowercase()) {
                        "true", "1", "yes", "checked" -> Result.success("true")
                        "false", "0", "no", "unchecked" -> Result.success("false")
                        else -> Result.failure(IllegalArgumentException("Expected boolean value, got '$raw'."))
                    }
                }
                WorkflowVariableType.URL -> {
                    val uri = runCatching { URI(trimmed) }.getOrNull()
                    val scheme = uri?.scheme?.lowercase().orEmpty()
                    if (trimmed.isEmpty() || (scheme != "http" && scheme != "https") || uri?.host.isNullOrBlank()) {
                        Result.failure(IllegalArgumentException("Expected HTTP/HTTPS URL, got '$raw'."))
                    } else {
                        Result.success(trimmed)
                    }
                }
            }
        }
    }
}

class UndefinedVariableException(val variableName: String, override val message: String) : Exception(message)
class InvalidVariableException(val variableName: String, override val message: String) : Exception(message)
