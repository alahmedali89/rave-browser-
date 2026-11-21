package com.example.ui.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.automation.DiffChangeKind
import com.example.automation.DryRunStepStatus
import com.example.automation.ValidationSeverity
import com.example.automation.WorkflowConflictResolution
import com.example.automation.WorkflowDiffReport
import com.example.automation.WorkflowDocumentMetadata
import com.example.automation.WorkflowDryRunReport
import com.example.automation.WorkflowImportResult
import com.example.automation.WorkflowOrganizationMetadata
import com.example.automation.WorkflowValidationReport
import com.example.data.local.AutomationWorkflowEntity
import com.example.data.local.AutomationWorkflowVersionEntity
import java.text.DateFormat
import java.util.Date

@Composable
fun WorkflowArchivedNoticeBanner(
    onRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
        modifier = modifier
            .fillMaxWidth()
            .testTag("details_archived_banner")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Archive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.automation_archived_badge),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = stringResource(R.string.automation_archived_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            FilledTonalButton(
                onClick = onRestore,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("details_restore_archive_button")
            ) {
                Icon(Icons.Filled.Unarchive, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Restore")
            }
        }
    }
}

@Composable
fun WorkflowValidationStatusCard(
    report: WorkflowValidationReport?,
    onValidate: () -> Unit,
    onRunDryRun: () -> Unit,
    onInspectReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (containerColor, contentColor, icon) = when {
        report == null -> Triple(
            MaterialTheme.colorScheme.surfaceContainerLow,
            MaterialTheme.colorScheme.onSurface,
            Icons.Filled.Shield
        )
        !report.isExecutionPermitted -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Filled.ErrorOutline
        )
        report.warningCount > 0 -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            Icons.Filled.WarningAmber
        )
        else -> Triple(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            MaterialTheme.colorScheme.onPrimaryContainer,
            Icons.Filled.CheckCircle
        )
    }

    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .testTag("details_validation_card")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = containerColor,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(12.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = contentColor)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                report == null -> "Validation not run yet"
                                !report.isExecutionPermitted -> "Execution Blocked by Validation"
                                report.warningCount > 0 -> "Valid with Warnings"
                                else -> "Ready to Execute"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor
                        )
                        Text(
                            text = report?.summary ?: "Run validation or a safe dry-run before executing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor,
                            modifier = Modifier.testTag("details_validation_summary")
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onValidate,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("details_validate_button")
                ) {
                    Icon(Icons.Filled.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.automation_validate), maxLines = 1)
                }
                FilledTonalButton(
                    onClick = onRunDryRun,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("details_dry_run_button")
                ) {
                    Icon(Icons.Filled.PlayCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.automation_dry_run), maxLines = 1)
                }
            }

            if (report != null && report.issues.isNotEmpty()) {
                TextButton(
                    onClick = onInspectReport,
                    modifier = Modifier
                        .align(Alignment.End)
                        .testTag("details_inspect_issues_button")
                ) {
                    Text("Inspect ${report.issues.size} Validation Finding${if (report.issues.size == 1) "" else "s"}")
                }
            }
        }
    }
}

@Composable
fun WorkflowValidationReportDialog(
    report: WorkflowValidationReport,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Validation Report • ${report.workflowName}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("validation_report_dialog")
            ) {
                Text(
                    text = report.summary,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (report.isExecutionPermitted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                if (report.issues.isEmpty()) {
                    Text("No structural, selector, variable, dependency, or credential issues detected.")
                } else {
                    report.issues.forEachIndexed { idx, issue ->
                        val badgeColor = when (issue.severity) {
                            ValidationSeverity.CRITICAL, ValidationSeverity.ERROR -> MaterialTheme.colorScheme.errorContainer
                            ValidationSeverity.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
                            ValidationSeverity.INFO -> MaterialTheme.colorScheme.secondaryContainer
                        }
                        val badgeTextColor = when (issue.severity) {
                            ValidationSeverity.CRITICAL, ValidationSeverity.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                            ValidationSeverity.WARNING -> MaterialTheme.colorScheme.onTertiaryContainer
                            ValidationSeverity.INFO -> MaterialTheme.colorScheme.onSecondaryContainer
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("validation_issue_${idx + 1}")
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(shape = RoundedCornerShape(6.dp), color = badgeColor) {
                                        Text(
                                            text = issue.severity.label.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = badgeTextColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = issue.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = issue.explanation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Fix: ${issue.suggestedResolution}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.testTag("validation_report_close_button")) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

@Composable
fun WorkflowDryRunReportDialog(
    report: WorkflowDryRunReport,
    metadata: WorkflowDocumentMetadata,
    onRerunWithInputs: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit
) {
    val inputValues = remember(report.workflowId) {
        mutableStateMapOf<String, String>().apply {
            metadata.variables.forEach { v -> put(v.name, v.defaultValue) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Safe Dry-Run • v${report.versionNumber}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("dry_run_report_dialog")
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (report.isSafeToRun) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Text(
                            text = report.summary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (report.isSafeToRun) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            }
                        )
                        Text(
                            text = "Simulation only: zero clicks, zero page navigation, and zero credential decryption executed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (report.isSafeToRun) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            }
                        )
                    }
                }

                if (metadata.variables.isNotEmpty()) {
                    Text("Test Variable Inputs", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    metadata.variables.forEach { vDef ->
                        OutlinedTextField(
                            value = inputValues[vDef.name].orEmpty(),
                            onValueChange = { inputValues[vDef.name] = it },
                            label = { Text("\${${vDef.name}} (${vDef.type.label})") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    OutlinedButton(
                        onClick = { onRerunWithInputs(inputValues.toMap()) },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Re-Simulate with Inputs")
                    }
                }

                if (report.resolvedSubWorkflowOrder.isNotEmpty()) {
                    Text(
                        text = "Resolved sub-workflow order: ${report.resolvedSubWorkflowOrder.joinToString(" → ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (report.originTransitions.isNotEmpty()) {
                    Text(
                        text = "Origin transitions: ${report.originTransitions.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                if (report.credentialStepsRequiringRebind.isNotEmpty()) {
                    Text(
                        text = "Credential rebinding required: ${report.credentialStepsRequiringRebind.joinToString("; ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (report.credentialStepsRequiringAuth.isNotEmpty()) {
                    Text(
                        text = "Runtime Vault auth required: ${report.credentialStepsRequiringAuth.joinToString("; ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (report.destructiveActions.isNotEmpty()) {
                    Text(
                        text = "Destructive confirmation steps: ${report.destructiveActions.joinToString("; ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                HorizontalDivider()
                Text("Planned Step Simulation (${report.plannedSteps.size})", style = MaterialTheme.typography.labelLarge)
                report.plannedSteps.forEachIndexed { idx, step ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dry_run_step_${idx + 1}")
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Text(
                                text = "${"  ".repeat(step.depth)}#${step.stepOrder} ${step.actionLabel} • ${step.status.label}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = when (step.status) {
                                    DryRunStepStatus.BLOCKED -> MaterialTheme.colorScheme.error
                                    DryRunStepStatus.REQUIRES_CREDENTIAL_REBIND -> MaterialTheme.colorScheme.error
                                    DryRunStepStatus.WARNING -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                            Text(step.description, style = MaterialTheme.typography.bodySmall)
                            if (step.variableSubstitutions.isNotEmpty()) {
                                Text(
                                    text = "Resolved vars: ${step.variableSubstitutions.entries.joinToString { "\${${it.key}}='${it.value}'" }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (step.domVerificationNote.isNotBlank()) {
                                Text(
                                    text = step.domVerificationNote,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            step.notes.forEach { n ->
                                Text(
                                    text = "• $n",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.testTag("dry_run_close_button")) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

@Composable
fun WorkflowVersionHistoryAndDiffDialog(
    workflow: AutomationWorkflowEntity,
    versions: List<AutomationWorkflowVersionEntity>,
    diffReport: WorkflowDiffReport?,
    onCompareVersions: (baseVersion: Int, targetVersion: Int) -> Unit,
    onRestoreVersion: (targetVersion: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormatter = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    var selectedBaseVersion by remember(versions) {
        mutableIntStateOf(versions.getOrNull(1)?.versionNumber ?: versions.firstOrNull()?.versionNumber ?: 1)
    }
    var selectedTargetVersion by remember(versions) {
        mutableIntStateOf(versions.firstOrNull()?.versionNumber ?: 1)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Version History • v${workflow.currentVersion}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("version_history_dialog")
            ) {
                if (versions.size >= 2) {
                    Text("Compare Versions (Semantic Diff)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        versions.forEach { v ->
                            FilterChip(
                                selected = selectedBaseVersion == v.versionNumber,
                                onClick = { selectedBaseVersion = v.versionNumber },
                                label = { Text("Base v${v.versionNumber}") }
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        versions.forEach { v ->
                            FilterChip(
                                selected = selectedTargetVersion == v.versionNumber,
                                onClick = { selectedTargetVersion = v.versionNumber },
                                label = { Text("Target v${v.versionNumber}") }
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = { onCompareVersions(selectedBaseVersion, selectedTargetVersion) },
                        modifier = Modifier.testTag("version_compare_button")
                    ) {
                        Icon(Icons.Filled.CompareArrows, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Compare v$selectedBaseVersion → v$selectedTargetVersion")
                    }
                }

                if (diffReport != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("version_diff_summary_card")
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Text(
                                text = "${diffReport.baseLabel} vs ${diffReport.targetLabel}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(diffReport.summary, style = MaterialTheme.typography.bodySmall)
                            diffReport.metadataChanges.forEach { ch ->
                                Text("• $ch", style = MaterialTheme.typography.labelSmall)
                            }
                            diffReport.stepDiffs.filter { it.kind != DiffChangeKind.UNCHANGED }.forEach { stepDiff ->
                                Text(
                                    text = "[${stepDiff.kind.label.uppercase()}] ${stepDiff.title}: ${stepDiff.fieldChanges.joinToString("; ")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (stepDiff.kind) {
                                        DiffChangeKind.ADDED -> MaterialTheme.colorScheme.primary
                                        DiffChangeKind.REMOVED -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()
                Text("Saved Revisions (${versions.size})", style = MaterialTheme.typography.labelLarge)
                versions.forEach { ver ->
                    val isCurrent = ver.versionNumber == workflow.currentVersion
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("version_row_${ver.versionNumber}")
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "v${ver.versionNumber}${if (isCurrent) " (Current)" else ""} • ${ver.stepCount} step(s)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (!isCurrent) {
                                    OutlinedButton(
                                        onClick = { onRestoreVersion(ver.versionNumber) },
                                        modifier = Modifier.testTag("version_restore_button_${ver.versionNumber}")
                                    ) {
                                        Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Restore")
                                    }
                                }
                            }
                            Text(
                                text = ver.changeSummary.ifBlank { "Updated workflow" },
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = dateFormatter.format(Date(ver.createdAt)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}

@Composable
fun WorkflowOrganizationDialog(
    workflow: AutomationWorkflowEntity,
    onSaveOrganization: (WorkflowOrganizationMetadata) -> Unit,
    onArchiveToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var folder by remember(workflow.id) { mutableStateOf(workflow.folder) }
    var category by remember(workflow.id) { mutableStateOf(workflow.category) }
    var tagsRaw by remember(workflow.id) {
        mutableStateOf(WorkflowOrganizationMetadata.decodeTagsJson(workflow.tagsJson).joinToString(", "))
    }
    var isFavorite by remember(workflow.id) { mutableStateOf(workflow.isFavorite) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Organize Workflow") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .testTag("workflow_organization_dialog")
            ) {
                OutlinedTextField(
                    value = folder,
                    onValueChange = { folder = it },
                    label = { Text("Folder (e.g. Shopping, Work)") },
                    leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("organize_folder_input")
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category (optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("organize_category_input")
                )
                OutlinedTextField(
                    value = tagsRaw,
                    onValueChange = { tagsRaw = it },
                    label = { Text("Tags (comma-separated)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("organize_tags_input")
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Favorite workflow", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = isFavorite,
                        onCheckedChange = { isFavorite = it },
                        modifier = Modifier.testTag("organize_favorite_switch")
                    )
                }
                HorizontalDivider()
                OutlinedButton(
                    onClick = {
                        onArchiveToggle(!workflow.isArchived)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("organize_archive_toggle_button")
                ) {
                    Icon(
                        imageVector = if (workflow.isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (workflow.isArchived) "Restore from Archive" else "Archive Workflow (Disable Execution)")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val tags = tagsRaw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                    onSaveOrganization(
                        WorkflowOrganizationMetadata(
                            folder = folder.trim(),
                            category = category.trim(),
                            tags = tags,
                            isFavorite = isFavorite,
                            isArchived = workflow.isArchived,
                            archivedAt = workflow.archivedAt
                        )
                    )
                    onDismiss()
                },
                modifier = Modifier.testTag("organize_save_button")
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun WorkflowImportResultDialog(
    result: WorkflowImportResult,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (result.success) "Import Completed" else "Import Rejected") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("import_result_dialog")
            ) {
                Text(
                    text = result.summary,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (result.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                if (result.rebindRequiredStepCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Security notice: All credential values and vault bindings were stripped on export/import. Rebind ${result.rebindRequiredStepCount} credential step(s) in Workflow Details before running.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                result.migrationNotes.forEach { note ->
                    Text("• Migrated: $note", style = MaterialTheme.typography.bodySmall)
                }
                result.warnings.forEach { w ->
                    Text("• Warning: $w", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
                result.errors.forEach { e ->
                    Text("• Error: $e", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.testTag("import_result_close_button")) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

@Composable
fun WorkflowPackageImportDialog(
    onImportJson: (String, WorkflowConflictResolution) -> Unit,
    onPickDocumentFile: (WorkflowConflictResolution) -> Unit,
    onDismiss: () -> Unit
) {
    var rawInput by remember { mutableStateOf("") }
    var conflictPolicy by remember { mutableStateOf(WorkflowConflictResolution.CREATE_COPY) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.automation_import_package)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .testTag("workflow_import_dialog")
            ) {
                Text("Duplicate UUID conflict policy", style = MaterialTheme.typography.labelMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    WorkflowConflictResolution.entries.forEach { mode ->
                        FilterChip(
                            selected = conflictPolicy == mode,
                            onClick = { conflictPolicy = mode },
                            label = { Text(mode.label) },
                            modifier = Modifier.testTag("import_conflict_${mode.id.lowercase()}")
                        )
                    }
                }
                OutlinedButton(
                    onClick = {
                        onPickDocumentFile(conflictPolicy)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("import_pick_file_button")
                ) {
                    Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Choose JSON or ZIP File…")
                }
                OutlinedTextField(
                    value = rawInput,
                    onValueChange = { rawInput = it },
                    label = { Text("Or paste workflow package JSON") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("import_json_text_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onImportJson(rawInput, conflictPolicy)
                    onDismiss()
                },
                enabled = rawInput.isNotBlank(),
                modifier = Modifier.testTag("import_json_confirm_button")
            ) {
                Text("Import JSON")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun WorkflowPackageExportDialog(
    workflowName: String?,
    jsonPreview: String,
    onSaveJsonFile: (() -> Unit)? = null,
    onSaveZipFile: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (workflowName != null) "Export • $workflowName" else "Export Workflow Collection"
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .testTag("workflow_export_dialog")
            ) {
                Text(
                    text = "Credentials, passwords, tokens, and Vault bindings are stripped automatically for safe sharing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (onSaveJsonFile != null) {
                        OutlinedButton(
                            onClick = onSaveJsonFile,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_save_json_file_button")
                        ) {
                            Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save .json")
                        }
                    }
                    if (onSaveZipFile != null) {
                        FilledTonalButton(
                            onClick = onSaveZipFile,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_save_zip_file_button")
                        ) {
                            Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save .zip")
                        }
                    }
                }
                OutlinedTextField(
                    value = jsonPreview,
                    onValueChange = {},
                    readOnly = true,
                    minLines = 5,
                    maxLines = 9,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    clipboardManager.setText(AnnotatedString(jsonPreview))
                    onDismiss()
                },
                modifier = Modifier.testTag("export_copy_json_button")
            ) {
                Text("Copy JSON")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}
