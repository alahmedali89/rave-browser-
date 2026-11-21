package com.example.ui.browser

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.automation.AutomationActionType
import com.example.automation.AutomationErrorCode
import com.example.automation.AutomationStep
import com.example.automation.AutomationStepCodec
import com.example.automation.ConditionNode
import com.example.automation.ConditionOperator
import com.example.automation.ElseIfBranch
import com.example.automation.ExtractionMode
import com.example.automation.ExtractionSpec
import com.example.automation.LogicalCombinator
import com.example.automation.LoopConfig
import com.example.automation.Phase6Limits
import com.example.automation.RecoveryPolicy
import com.example.automation.RecoveryStrategy
import com.example.automation.SubWorkflowReference
import com.example.automation.WorkflowVariableDefinition
import com.example.automation.WorkflowVariableStore
import com.example.automation.WorkflowVariableType
import com.example.data.local.AutomationWorkflowEntity
import java.util.UUID

@Composable
fun AddWorkflowVariableDialog(
    onSave: (WorkflowVariableDefinition) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(WorkflowVariableType.TEXT) }
    var defaultValue by remember { mutableStateOf("") }
    var isInput by remember { mutableStateOf(false) }
    var isOutput by remember { mutableStateOf(false) }

    val cleanName = name.trim()
    val isBlockedSecret = cleanName.isNotEmpty() && WorkflowVariableStore.isBlockedCredentialVariableName(cleanName)
    val isValidName = cleanName.isNotEmpty() && WorkflowVariableStore.isValidVariableName(cleanName)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Workflow Variable") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Variable name (e.g. username, orderTotal)") },
                    isError = isBlockedSecret || (cleanName.isNotEmpty() && !isValidName),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_var_name_input")
                )
                if (isBlockedSecret) {
                    Text(
                        text = "Passwords and secrets must stay in Credential Vault, not workflow variables.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (cleanName.isNotEmpty() && !isValidName) {
                    Text(
                        text = "Use letters, digits, or underscores starting with a letter.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Text("Type", style = MaterialTheme.typography.labelMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    WorkflowVariableType.entries.forEach { t ->
                        FilterChip(
                            selected = selectedType == t,
                            onClick = { selectedType = t },
                            label = { Text(t.label) },
                            modifier = Modifier.testTag("add_var_type_${t.id.lowercase()}")
                        )
                    }
                }

                OutlinedTextField(
                    value = defaultValue,
                    onValueChange = { defaultValue = it },
                    label = { Text("Default value (optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_var_default_input")
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Accept as sub-workflow input", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = isInput, onCheckedChange = { isInput = it })
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Expose as sub-workflow output", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = isOutput, onCheckedChange = { isOutput = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        WorkflowVariableDefinition(
                            name = cleanName,
                            type = selectedType,
                            defaultValue = defaultValue,
                            isInput = isInput,
                            isOutput = isOutput
                        )
                    )
                },
                enabled = isValidName && !isBlockedSecret,
                modifier = Modifier.testTag("add_var_save_button")
            ) {
                Text("Save Variable")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun RecoveryPolicyEditorDialog(
    initialPolicy: RecoveryPolicy?,
    onSave: (RecoveryPolicy) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedStrategy by remember { mutableStateOf(initialPolicy?.strategy ?: RecoveryStrategy.RESCAN_AND_RETRY) }
    var maxRetries by remember { mutableIntStateOf(initialPolicy?.maxRetries ?: 1) }
    var rescanBeforeRetry by remember { mutableStateOf(initialPolicy?.rescanBeforeRetry ?: true) }
    var altStepDesc by remember {
        mutableStateOf(initialPolicy?.alternativeSteps?.firstOrNull()?.description.orEmpty())
    }
    var altStepSelector by remember {
        mutableStateOf(initialPolicy?.alternativeSteps?.firstOrNull()?.selector.orEmpty())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure Failure Recovery") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text("Recovery strategy", style = MaterialTheme.typography.labelMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    RecoveryStrategy.entries.forEach { st ->
                        FilterChip(
                            selected = selectedStrategy == st,
                            onClick = { selectedStrategy = st },
                            label = { Text(st.label) },
                            modifier = Modifier.testTag("recovery_strategy_${st.id.lowercase()}")
                        )
                    }
                }

                Text("Max retries (0–${Phase6Limits.MAX_RECOVERY_RETRIES})", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0..Phase6Limits.MAX_RECOVERY_RETRIES).forEach { count ->
                        FilterChip(
                            selected = maxRetries == count,
                            onClick = { maxRetries = count },
                            label = { Text("${count}x") },
                            modifier = Modifier.testTag("recovery_retries_$count")
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Re-scan page DOM before retry", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = rescanBeforeRetry, onCheckedChange = { rescanBeforeRetry = it })
                }

                if (selectedStrategy == RecoveryStrategy.ALTERNATIVE_BRANCH) {
                    OutlinedTextField(
                        value = altStepDesc,
                        onValueChange = { altStepDesc = it },
                        label = { Text("Fallback click target / step description") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = altStepSelector,
                        onValueChange = { altStepSelector = it },
                        label = { Text("Fallback CSS selector (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val altSteps = if (selectedStrategy == RecoveryStrategy.ALTERNATIVE_BRANCH && altStepDesc.isNotBlank()) {
                        listOf(
                            AutomationStep(
                                id = "alt_${UUID.randomUUID()}",
                                order = 1,
                                actionType = AutomationActionType.CLICK,
                                targetLabel = altStepDesc.trim(),
                                selector = altStepSelector.trim(),
                                description = altStepDesc.trim(),
                                isDestructive = AutomationStepCodec.isClauseDestructive(altStepDesc.lowercase())
                            )
                        )
                    } else {
                        initialPolicy?.alternativeSteps.orEmpty()
                    }
                    onSave(
                        RecoveryPolicy(
                            strategy = selectedStrategy,
                            maxRetries = maxRetries.coerceIn(0, Phase6Limits.MAX_RECOVERY_RETRIES),
                            retryOnCodes = initialPolicy?.retryOnCodes ?: setOf(
                                AutomationErrorCode.ELEMENT_NOT_FOUND,
                                AutomationErrorCode.ELEMENT_STALE,
                                AutomationErrorCode.ELEMENT_NOT_INTERACTABLE,
                                AutomationErrorCode.JS_TIMEOUT
                            ),
                            rescanBeforeRetry = rescanBeforeRetry,
                            alternativeSteps = altSteps
                        )
                    )
                },
                modifier = Modifier.testTag("recovery_policy_save_button")
            ) {
                Text("Save Policy")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (initialPolicy != null) {
                    OutlinedButton(onClick = onClear) { Text("Clear") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
fun Phase6StepBuilderDialog(
    currentWorkflowId: Long,
    allWorkflows: List<AutomationWorkflowEntity>,
    onAddStep: (AutomationStep) -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf(AutomationActionType.CONDITION) }
    var description by remember { mutableStateOf("") }

    // CONDITION fields
    var condOperator by remember { mutableStateOf(ConditionOperator.ELEMENT_EXISTS) }
    var condSelector by remember { mutableStateOf("") }
    var condExpectedValue by remember { mutableStateOf("") }
    var condVariableName by remember { mutableStateOf("") }
    var useSecondPredicate by remember { mutableStateOf(false) }
    var combinator by remember { mutableStateOf(LogicalCombinator.AND) }
    var cond2Operator by remember { mutableStateOf(ConditionOperator.VARIABLE_NOT_EMPTY) }
    var cond2Selector by remember { mutableStateOf("") }
    var cond2Var by remember { mutableStateOf("") }
    var thenActionType by remember { mutableStateOf(AutomationActionType.CLICK) }
    var thenTarget by remember { mutableStateOf("") }
    var thenValue by remember { mutableStateOf("") }
    var enableElseIf by remember { mutableStateOf(false) }
    var elseIfVarOrSelector by remember { mutableStateOf("") }
    var elseIfStepTarget by remember { mutableStateOf("") }
    var elseStepTarget by remember { mutableStateOf("") }

    // LOOP fields
    var loopMaxIterations by remember { mutableStateOf("5") }
    var loopIterVar by remember { mutableStateOf("loop_index") }
    var loopBodyTarget by remember { mutableStateOf("") }
    var loopBodyValue by remember { mutableStateOf("") }

    // SET_VARIABLE fields
    var setVarName by remember { mutableStateOf("") }
    var setVarType by remember { mutableStateOf(WorkflowVariableType.TEXT) }
    var setVarValue by remember { mutableStateOf("") }

    // EXTRACT fields
    var extractMode by remember { mutableStateOf(ExtractionMode.VISIBLE_TEXT) }
    var extractSelector by remember { mutableStateOf("") }
    var extractAttrOrMeta by remember { mutableStateOf("") }
    var extractOutVar by remember { mutableStateOf("") }
    var extractAllowMultiple by remember { mutableStateOf(false) }

    // CALL_WORKFLOW fields
    val eligibleSubWorkflows = remember(allWorkflows, currentWorkflowId) {
        allWorkflows.filter { it.id != currentWorkflowId }
    }
    var selectedSubWorkflow by remember(eligibleSubWorkflows) {
        mutableStateOf(eligibleSubWorkflows.firstOrNull())
    }
    var subInputMappingRaw by remember { mutableStateOf("") }
    var subOutputMappingRaw by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Phase 6 Step") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    listOf(
                        AutomationActionType.CONDITION to "If / Branch",
                        AutomationActionType.LOOP to "Loop",
                        AutomationActionType.SET_VARIABLE to "Set Var",
                        AutomationActionType.EXTRACT to "Extract",
                        AutomationActionType.CALL_WORKFLOW to "Sub-Workflow"
                    ).forEach { (action, label) ->
                        FilterChip(
                            selected = mode == action,
                            onClick = { mode = action },
                            label = { Text(label) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("phase6_builder_mode_${action.id.lowercase()}")
                        )
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Step title / description (optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("phase6_builder_description")
                )

                when (mode) {
                    AutomationActionType.CONDITION -> {
                        Text("IF Condition", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            listOf(
                                ConditionOperator.ELEMENT_EXISTS,
                                ConditionOperator.ELEMENT_VISIBLE,
                                ConditionOperator.ELEMENT_CONTAINS_TEXT,
                                ConditionOperator.URL_MATCHES,
                                ConditionOperator.VARIABLE_EQUALS,
                                ConditionOperator.VARIABLE_NOT_EMPTY
                            ).forEach { op ->
                                FilterChip(
                                    selected = condOperator == op,
                                    onClick = { condOperator = op },
                                    label = { Text(op.label) }
                                )
                            }
                        }
                        if (condOperator.requiresDomTarget) {
                            OutlinedTextField(
                                value = condSelector,
                                onValueChange = { condSelector = it },
                                label = { Text("CSS selector or target label") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("phase6_cond_selector")
                            )
                        } else if (condOperator.name.startsWith("VARIABLE_")) {
                            OutlinedTextField(
                                value = condVariableName,
                                onValueChange = { condVariableName = it },
                                label = { Text("Variable name") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("phase6_cond_varname")
                            )
                        }
                        OutlinedTextField(
                            value = condExpectedValue,
                            onValueChange = { condExpectedValue = it },
                            label = { Text("Expected text / URL / value (optional)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("phase6_cond_expected")
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Combine with 2nd condition (AND/OR)", style = MaterialTheme.typography.bodySmall)
                            Switch(checked = useSecondPredicate, onCheckedChange = { useSecondPredicate = it })
                        }
                        if (useSecondPredicate) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                LogicalCombinator.entries.forEach { comb ->
                                    FilterChip(
                                        selected = combinator == comb,
                                        onClick = { combinator = comb },
                                        label = { Text(comb.id) }
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = cond2Var,
                                onValueChange = { cond2Var = it },
                                label = { Text("2nd variable name (for VARIABLE_NOT_EMPTY)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = cond2Selector,
                                onValueChange = { cond2Selector = it },
                                label = { Text("Or 2nd CSS selector (optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        OutlinedTextField(
                            value = thenTarget,
                            onValueChange = { thenTarget = it },
                            label = { Text("THEN: target element / selector to click or type") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("phase6_then_target")
                        )
                        OutlinedTextField(
                            value = thenValue,
                            onValueChange = { thenValue = it },
                            label = { Text("THEN input value (if typing \${var}, optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Add ELSE IF branch", style = MaterialTheme.typography.bodySmall)
                            Switch(checked = enableElseIf, onCheckedChange = { enableElseIf = it })
                        }
                        if (enableElseIf) {
                            OutlinedTextField(
                                value = elseIfVarOrSelector,
                                onValueChange = { elseIfVarOrSelector = it },
                                label = { Text("ELSE IF selector exists") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = elseIfStepTarget,
                                onValueChange = { elseIfStepTarget = it },
                                label = { Text("ELSE IF click target") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        OutlinedTextField(
                            value = elseStepTarget,
                            onValueChange = { elseStepTarget = it },
                            label = { Text("ELSE fallback click target (optional)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("phase6_else_target")
                        )
                    }

                    AutomationActionType.LOOP -> {
                        OutlinedTextField(
                            value = loopMaxIterations,
                            onValueChange = { loopMaxIterations = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Max iterations (1–${Phase6Limits.MAX_LOOP_ITERATIONS})") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("phase6_loop_max")
                        )
                        OutlinedTextField(
                            value = loopIterVar,
                            onValueChange = { loopIterVar = it },
                            label = { Text("Iteration index variable") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = loopBodyTarget,
                            onValueChange = { loopBodyTarget = it },
                            label = { Text("Loop body target selector / label") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("phase6_loop_body_target")
                        )
                        OutlinedTextField(
                            value = loopBodyValue,
                            onValueChange = { loopBodyValue = it },
                            label = { Text("Loop body input value (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    AutomationActionType.SET_VARIABLE -> {
                        OutlinedTextField(
                            value = setVarName,
                            onValueChange = { setVarName = it },
                            label = { Text("Variable name (e.g. greeting)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("phase6_setvar_name")
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            WorkflowVariableType.entries.forEach { t ->
                                FilterChip(
                                    selected = setVarType == t,
                                    onClick = { setVarType = t },
                                    label = { Text(t.label) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = setVarValue,
                            onValueChange = { setVarValue = it },
                            label = { Text("Value or \${template}") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("phase6_setvar_value")
                        )
                    }

                    AutomationActionType.EXTRACT -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            ExtractionMode.entries.forEach { m ->
                                FilterChip(
                                    selected = extractMode == m,
                                    onClick = { extractMode = m },
                                    label = { Text(m.label) }
                                )
                            }
                        }
                        if (extractMode != ExtractionMode.PAGE_TITLE && extractMode != ExtractionMode.PAGE_URL) {
                            OutlinedTextField(
                                value = extractSelector,
                                onValueChange = { extractSelector = it },
                                label = { Text("Element selector or label") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("phase6_extract_selector")
                            )
                        }
                        if (extractMode == ExtractionMode.ATTRIBUTE || extractMode == ExtractionMode.PAGE_META) {
                            OutlinedTextField(
                                value = extractAttrOrMeta,
                                onValueChange = { extractAttrOrMeta = it },
                                label = { Text(if (extractMode == ExtractionMode.ATTRIBUTE) "Attribute name (e.g. href)" else "Meta key (e.g. description)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        OutlinedTextField(
                            value = extractOutVar,
                            onValueChange = { extractOutVar = it },
                            label = { Text("Store in variable name (optional)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("phase6_extract_outvar")
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Extract list (up to ${Phase6Limits.MAX_EXTRACT_LIST_ITEMS})", style = MaterialTheme.typography.bodySmall)
                            Switch(checked = extractAllowMultiple, onCheckedChange = { extractAllowMultiple = it })
                        }
                    }

                    AutomationActionType.CALL_WORKFLOW -> {
                        if (eligibleSubWorkflows.isEmpty()) {
                            Text(
                                text = "Create another saved workflow first to reuse it as a sub-workflow.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text("Target Sub-Workflow", style = MaterialTheme.typography.labelMedium)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                eligibleSubWorkflows.forEach { wf ->
                                    FilterChip(
                                        selected = selectedSubWorkflow?.id == wf.id,
                                        onClick = { selectedSubWorkflow = wf },
                                        label = { Text(wf.name) },
                                        modifier = Modifier.testTag("phase6_subwf_chip_${wf.id}")
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = subInputMappingRaw,
                                onValueChange = { subInputMappingRaw = it },
                                label = { Text("Input map (childVar=parentExpr, comma-sep)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = subOutputMappingRaw,
                                onValueChange = { subOutputMappingRaw = it },
                                label = { Text("Output map (parentVar=childVar, comma-sep)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    else -> Unit
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val step = when (mode) {
                        AutomationActionType.CONDITION -> {
                            val p1 = ConditionNode.Predicate(
                                operator = condOperator,
                                selector = condSelector.trim(),
                                targetLabel = condSelector.trim(),
                                expectedValue = condExpectedValue.trim(),
                                variableName = condVariableName.trim()
                            )
                            val rootCond = if (useSecondPredicate) {
                                val p2Op = if (cond2Selector.isNotBlank()) ConditionOperator.ELEMENT_EXISTS else cond2Operator
                                val p2 = ConditionNode.Predicate(
                                    operator = p2Op,
                                    selector = cond2Selector.trim(),
                                    targetLabel = cond2Selector.trim(),
                                    variableName = cond2Var.trim()
                                )
                                ConditionNode.Group(combinator = combinator, children = listOf(p1, p2))
                            } else {
                                p1
                            }
                            val thenStep = AutomationStep(
                                id = "then_${UUID.randomUUID()}",
                                order = 1,
                                actionType = if (thenValue.isNotBlank()) AutomationActionType.INPUT else thenActionType,
                                targetLabel = thenTarget.trim().ifBlank { "Continue" },
                                selector = thenTarget.trim(),
                                value = thenValue,
                                description = if (thenValue.isNotBlank()) "Enter '$thenValue' in $thenTarget" else "Click ${thenTarget.ifBlank { "target" }}"
                            )
                            val elseIfList = if (enableElseIf && elseIfVarOrSelector.isNotBlank()) {
                                listOf(
                                    ElseIfBranch(
                                        id = "elseif_${UUID.randomUUID()}",
                                        label = "Else If",
                                        condition = ConditionNode.Predicate(
                                            operator = ConditionOperator.ELEMENT_EXISTS,
                                            selector = elseIfVarOrSelector.trim(),
                                            targetLabel = elseIfVarOrSelector.trim()
                                        ),
                                        steps = listOf(
                                            AutomationStep(
                                                id = "elseif_step_${UUID.randomUUID()}",
                                                order = 1,
                                                actionType = AutomationActionType.CLICK,
                                                targetLabel = elseIfStepTarget.trim().ifBlank { elseIfVarOrSelector.trim() },
                                                selector = elseIfStepTarget.trim().ifBlank { elseIfVarOrSelector.trim() },
                                                description = "Click ${elseIfStepTarget.trim().ifBlank { elseIfVarOrSelector.trim() }}"
                                            )
                                        )
                                    )
                                )
                            } else {
                                emptyList()
                            }
                            val elseList = if (elseStepTarget.isNotBlank()) {
                                listOf(
                                    AutomationStep(
                                        id = "else_${UUID.randomUUID()}",
                                        order = 1,
                                        actionType = AutomationActionType.CLICK,
                                        targetLabel = elseStepTarget.trim(),
                                        selector = elseStepTarget.trim(),
                                        description = "Else click ${elseStepTarget.trim()}"
                                    )
                                )
                            } else {
                                emptyList()
                            }
                            AutomationStep(
                                id = "cond_${UUID.randomUUID()}",
                                order = 1,
                                actionType = AutomationActionType.CONDITION,
                                targetLabel = rootCond.toHumanSummary(),
                                description = description.trim().ifBlank { "Branch: ${rootCond.toHumanSummary()}" },
                                condition = rootCond,
                                thenSteps = listOf(thenStep),
                                elseIfBranches = elseIfList,
                                elseSteps = elseList
                            )
                        }

                        AutomationActionType.LOOP -> {
                            val maxIter = (loopMaxIterations.toIntOrNull() ?: 5).coerceIn(1, Phase6Limits.MAX_LOOP_ITERATIONS)
                            val bodyAction = if (loopBodyValue.isNotBlank()) AutomationActionType.INPUT else AutomationActionType.CLICK
                            AutomationStep(
                                id = "loop_${UUID.randomUUID()}",
                                order = 1,
                                actionType = AutomationActionType.LOOP,
                                targetLabel = loopBodyTarget.trim().ifBlank { "Loop $maxIter times" },
                                description = description.trim().ifBlank { "Loop up to ${maxIter}x" },
                                loopConfig = LoopConfig(
                                    maxIterations = maxIter,
                                    iterationVariable = loopIterVar.trim().ifBlank { "loop_index" }
                                ),
                                thenSteps = listOf(
                                    AutomationStep(
                                        id = "loop_body_${UUID.randomUUID()}",
                                        order = 1,
                                        actionType = bodyAction,
                                        targetLabel = loopBodyTarget.trim().ifBlank { "Item" },
                                        selector = loopBodyTarget.trim(),
                                        value = loopBodyValue,
                                        description = "Repeat on ${loopBodyTarget.trim().ifBlank { "item" }}"
                                    )
                                )
                            )
                        }

                        AutomationActionType.SET_VARIABLE -> {
                            val cleanName = setVarName.trim()
                            AutomationStep(
                                id = "var_${UUID.randomUUID()}",
                                order = 1,
                                actionType = AutomationActionType.SET_VARIABLE,
                                targetLabel = cleanName,
                                value = setVarValue,
                                description = description.trim().ifBlank { "Set \${$cleanName} = ${setVarValue.take(24)}" },
                                variableName = cleanName,
                                variableType = setVarType
                            )
                        }

                        AutomationActionType.EXTRACT -> {
                            val outVar = extractOutVar.trim()
                            AutomationStep(
                                id = "ext_${UUID.randomUUID()}",
                                order = 1,
                                actionType = AutomationActionType.EXTRACT,
                                targetLabel = extractSelector.trim().ifBlank { extractMode.label },
                                selector = extractSelector.trim(),
                                description = description.trim().ifBlank { "Extract ${extractMode.label}" },
                                variableName = outVar,
                                extractionSpec = ExtractionSpec(
                                    mode = extractMode,
                                    attributeName = if (extractMode == ExtractionMode.ATTRIBUTE) extractAttrOrMeta.trim() else "",
                                    allowMultiple = extractAllowMultiple,
                                    maxItems = Phase6Limits.MAX_EXTRACT_LIST_ITEMS,
                                    outputVariableName = outVar,
                                    metaKey = if (extractMode == ExtractionMode.PAGE_META) extractAttrOrMeta.trim() else ""
                                )
                            )
                        }

                        AutomationActionType.CALL_WORKFLOW -> {
                            val target = selectedSubWorkflow!!
                            val inMap = parseKeyValueCsv(subInputMappingRaw)
                            val outMap = parseKeyValueCsv(subOutputMappingRaw)
                            AutomationStep(
                                id = "call_${UUID.randomUUID()}",
                                order = 1,
                                actionType = AutomationActionType.CALL_WORKFLOW,
                                targetLabel = target.name,
                                description = description.trim().ifBlank { "Run sub-workflow '${target.name}'" },
                                subWorkflowRef = SubWorkflowReference(
                                    targetWorkflowId = target.id,
                                    targetWorkflowName = target.name,
                                    inputMappings = inMap,
                                    outputMappings = outMap
                                )
                            )
                        }

                        else -> return@Button
                    }
                    onAddStep(step)
                },
                enabled = when (mode) {
                    AutomationActionType.CALL_WORKFLOW -> selectedSubWorkflow != null
                    AutomationActionType.SET_VARIABLE -> WorkflowVariableStore.isValidVariableName(setVarName.trim()) &&
                        !WorkflowVariableStore.isBlockedCredentialVariableName(setVarName.trim())
                    else -> true
                },
                modifier = Modifier.testTag("phase6_builder_confirm_button")
            ) {
                Text("Add Step")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun parseKeyValueCsv(raw: String): Map<String, String> {
    if (raw.isBlank()) return emptyMap()
    return buildMap {
        raw.split(",").forEach { pair ->
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2) {
                val k = parts[0].trim()
                val v = parts[1].trim()
                if (k.isNotEmpty()) put(k, v)
            }
        }
    }
}
