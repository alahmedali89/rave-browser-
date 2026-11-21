package com.example.ui.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.automation.AutomationActionType
import com.example.automation.AutomationStep
import com.example.automation.BranchTraceRecord
import com.example.automation.InterruptedExecutionCandidate
import com.example.automation.Phase6ExecutionCoordinator
import com.example.automation.RecoveryAttemptRecord
import com.example.automation.RecoveryPolicy
import com.example.automation.WorkflowVariableDefinition
import java.util.Locale

@Composable
fun InterruptedExecutionResumeBanner(
    candidate: InterruptedExecutionCandidate,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    val cp = candidate.checkpoint

    if (showConfirmDialog) {
        InterruptedExecutionResumeDialog(
            candidate = candidate,
            onConfirmResume = {
                showConfirmDialog = false
                onResume()
            },
            onRestartCleanly = {
                showConfirmDialog = false
                onRestart()
            },
            onDismiss = { showConfirmDialog = false }
        )
    }

    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .testTag("interrupted_banner")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.PauseCircleOutline,
                    contentDescription = "Interrupted workflow",
                    tint = if (candidate.canSafeResume) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${cp.workflowName} • Interrupted",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("interrupted_banner_title")
                    )
                    Text(
                        text = "Step ${cp.completedStepsCount}/${cp.totalTopLevelSteps} • ${cp.boundOrigin.ifBlank { cp.lastUrl }.ifBlank { "Saved session" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("interrupted_dismiss_button")
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Dismiss interrupted execution")
                }
            }

            if (cp.interruptedReason.isNotBlank()) {
                Text(
                    text = cp.interruptedReason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (candidate.requiresCredentialReauth) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "Requires credential re-authorization before resume",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (!candidate.canSafeResume && candidate.unsafeReason.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("interrupted_unsafe_warning")
                ) {
                    Text(
                        text = candidate.unsafeReason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { showConfirmDialog = true },
                    enabled = candidate.canSafeResume,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("interrupted_resume_button")
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Resume Safely")
                }
                OutlinedButton(
                    onClick = onRestart,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("interrupted_restart_button")
                ) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Restart Cleanly")
                }
            }
        }
    }
}

@Composable
fun InterruptedExecutionResumeDialog(
    candidate: InterruptedExecutionCandidate,
    onConfirmResume: () -> Unit,
    onRestartCleanly: () -> Unit,
    onDismiss: () -> Unit
) {
    val cp = candidate.checkpoint
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary
            )
        },
        title = { Text("Resume Interrupted Workflow?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "\"${cp.workflowName}\" paused after completing ${cp.completedStepsCount} of ${cp.totalTopLevelSteps} steps.",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (cp.lastUrl.isNotBlank()) {
                    Text(
                        text = "Origin: ${cp.boundOrigin.ifBlank { cp.lastUrl }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (candidate.requiresCredentialReauth) {
                    Text(
                        text = "Fresh credential vault authorization will be requested for remaining credential steps.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (cp.safeVariables.isNotEmpty()) {
                    Text(
                        text = "Restoring ${cp.safeVariables.size} saved session variable(s).",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmResume,
                enabled = candidate.canSafeResume,
                modifier = Modifier.testTag("interrupted_dialog_confirm_resume")
            ) {
                Text("Resume from Step ${(cp.nextTopLevelIndex + 1).coerceAtLeast(1)}")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(
                    onClick = onRestartCleanly,
                    modifier = Modifier.testTag("interrupted_dialog_restart")
                ) {
                    Text("Restart Cleanly")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkflowVariablesEditorCard(
    variables: List<WorkflowVariableDefinition>,
    onAddVariable: (WorkflowVariableDefinition) -> Unit,
    onRemoveVariable: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AddWorkflowVariableDialog(
            onSave = { def ->
                onAddVariable(def)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    ElevatedCard(
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier
            .fillMaxWidth()
            .testTag("workflow_variables_card")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DataObject,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Workflow Variables (${variables.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Reuse values via \${variableName}. Passwords are excluded.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                FilledTonalButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("add_workflow_variable_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Var")
                }
            }

            if (variables.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    variables.forEach { v ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.testTag("workflow_variable_chip_${v.name}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "\${${v.name}} • ${v.type.label}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    val roleTags = buildList {
                                        if (v.defaultValue.isNotBlank()) add("=\"${v.defaultValue}\"")
                                        if (v.isInput) add("IN")
                                        if (v.isOutput) add("OUT")
                                    }.joinToString(" • ")
                                    if (roleTags.isNotBlank()) {
                                        Text(
                                            text = roleTags,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onRemoveVariable(v.name) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("remove_workflow_variable_${v.name}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteOutline,
                                        contentDescription = "Remove ${v.name}",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecoveryPolicyEditorCard(
    policy: RecoveryPolicy?,
    onSavePolicy: (RecoveryPolicy?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        RecoveryPolicyEditorDialog(
            initialPolicy = policy,
            onSave = { updated ->
                onSavePolicy(updated)
                showDialog = false
            },
            onClear = {
                onSavePolicy(null)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }

    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .testTag("default_recovery_card")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.HealthAndSafety,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Failure Recovery Policy",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (policy == null) {
                        "Default: Stop safely (configure bounded retry / fallback)"
                    } else {
                        "${policy.strategy.label} • max ${policy.maxRetries} retries"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = { showDialog = true },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("configure_default_recovery_button")
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Configure")
            }
        }
    }
}

@Composable
fun Phase6StepMetadataPreview(
    step: AutomationStep,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            if (step.allowCrossOriginTransition) {
                Phase6TagChip(label = "Cross-Origin Allowed", isAccent = true)
            }
            step.recoveryPolicy?.let { pol ->
                Phase6TagChip(label = "Recovery: ${pol.strategy.label} (${pol.maxRetries}x)")
            }
            step.extractionSpec?.let { spec ->
                val outSuffix = if (spec.outputVariableName.isNotBlank()) " -> \${${spec.outputVariableName}}" else ""
                val listSuffix = if (spec.allowMultiple) " (list ≤${spec.maxItems})" else ""
                Phase6TagChip(label = "${spec.mode.label}$listSuffix$outSuffix", isAccent = true)
            }
            if (step.actionType == AutomationActionType.SET_VARIABLE && step.variableName.isNotBlank()) {
                Phase6TagChip(label = "\${${step.variableName}} : ${step.variableType.label}", isAccent = true)
            }
            step.subWorkflowRef?.let { ref ->
                Phase6TagChip(
                    label = "Calls #${ref.targetWorkflowId} ${ref.targetWorkflowName}".trim(),
                    isAccent = true
                )
            }
        }

        if (step.actionType == AutomationActionType.CONDITION && step.condition != null) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = "IF ${step.condition.toHumanSummary()} (${step.thenSteps.size} steps)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    step.elseIfBranches.forEach { branch ->
                        Text(
                            text = "ELSE IF ${branch.condition.toHumanSummary()} (${branch.steps.size} steps)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (step.elseSteps.isNotEmpty()) {
                        Text(
                            text = "ELSE (${step.elseSteps.size} steps)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (step.actionType == AutomationActionType.LOOP && step.loopConfig != null) {
            val cfg = step.loopConfig
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Loop,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Loop ≤${cfg.maxIterations}x (${cfg.iterationVariable}) • ${step.thenSteps.size} body step(s)" +
                            (cfg.continueCondition?.let { " • while ${it.toHumanSummary()}" }.orEmpty()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun Phase6TagChip(
    label: String,
    isAccent: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isAccent) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isAccent) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun ExecutionTraceDetailsCard(
    traceJson: String,
    modifier: Modifier = Modifier
) {
    val parsed = remember(traceJson) { Phase6ExecutionCoordinator.parseExecutionTrace(traceJson) }
    val branchTraces: List<BranchTraceRecord> = parsed.first
    val recoveryAttempts: List<RecoveryAttemptRecord> = parsed.second
    if (branchTraces.isEmpty() && recoveryAttempts.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .testTag("execution_trace_details_card")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(10.dp)
        ) {
            if (branchTraces.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AltRoute,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Branches & Sub-Workflows (${branchTraces.size})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                branchTraces.take(6).forEach { trace ->
                    val statusTag = if (trace.matched) "TAKEN" else "SKIPPED"
                    val subTag = if (trace.subWorkflowName.isNotBlank()) " [${trace.subWorkflowName}]" else ""
                    Text(
                        text = "• [$statusTag] ${trace.branchLabel}$subTag (${trace.executedStepsCount} steps)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (recoveryAttempts.isNotEmpty()) {
                if (branchTraces.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccountTree,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Recovery Attempts (${recoveryAttempts.size})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                recoveryAttempts.take(5).forEach { rec ->
                    val outcome = if (rec.succeeded) "Recovered" else "Failed"
                    Text(
                        text = "• #${rec.attemptNumber} ${rec.strategy.label.lowercase(Locale.US)} ($outcome): ${rec.safeMessage}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (rec.succeeded) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
            }
        }
    }
}
