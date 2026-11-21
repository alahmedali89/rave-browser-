package com.example.ui.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.automation.AmbiguousCandidateInfo
import com.example.automation.DisambiguationSource
import com.example.automation.LocatorResolutionDiagnostics
import com.example.automation.PendingDisambiguationState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocatorDisambiguationDialog(
    state: PendingDisambiguationState,
    onSelectAndSave: (AmbiguousCandidateInfo) -> Unit,
    onSelectAndRerun: (AmbiguousCandidateInfo) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedIndex by remember(state) {
        mutableIntStateOf(state.candidates.firstOrNull()?.candidateIndex ?: 0)
    }
    var showDiagnostics by remember(state) { mutableStateOf(false) }

    val selectedCandidate = state.candidates.firstOrNull { it.candidateIndex == selectedIndex }
        ?: state.candidates.firstOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("locator_disambiguation_dialog"),
        icon = {
            Icon(
                imageVector = Icons.Filled.AdsClick,
                contentDescription = "Choose target element",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Choose Target Element",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${state.candidates.size} elements matched \"${state.originalTarget.ifBlank { state.stepDescription }}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Step: ${state.stepDescription}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                        .testTag("disambiguation_candidate_list")
                ) {
                    itemsIndexed(state.candidates, key = { idx, c -> "${c.candidateIndex}_$idx" }) { idx, candidate ->
                        val isSelected = candidate.candidateIndex == selectedCandidate?.candidateIndex
                        CandidateOptionCard(
                            candidate = candidate,
                            ordinal = idx + 1,
                            isSelected = isSelected,
                            onClick = { selectedIndex = candidate.candidateIndex }
                        )
                    }

                    state.diagnostics?.let { diag ->
                        item(key = "diagnostics_accordion") {
                            DiagnosticsAccordion(
                                diagnostics = diag,
                                expanded = showDiagnostics,
                                onToggle = { showDiagnostics = !showDiagnostics }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (selectedCandidate != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onSelectAndSave(selectedCandidate) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("disambiguation_update_step_button")
                    ) {
                        Text("Update Step")
                    }
                    if (state.source == DisambiguationSource.EXECUTION) {
                        Button(
                            onClick = { onSelectAndRerun(selectedCandidate) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("disambiguation_rerun_button")
                        ) {
                            Text("Select & Re-run")
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("disambiguation_cancel_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CandidateOptionCard(
    candidate: AmbiguousCandidateInfo,
    ordinal: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val displayTitle = candidate.accessibleName.ifBlank {
        candidate.visibleText.ifBlank { "<${candidate.tagName}> #$ordinal" }
    }
    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("disambiguation_candidate_${candidate.candidateIndex}")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "#$ordinal <${candidate.tagName}>",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (candidate.parentContext.isNotBlank() || candidate.nearbyText.isNotBlank()) {
                val contextText = buildString {
                    if (candidate.parentContext.isNotBlank()) {
                        append("Inside ${candidate.parentContext}")
                    }
                    if (candidate.nearbyText.isNotBlank()) {
                        if (isNotEmpty()) append(" • ")
                        append("Near \"${candidate.nearbyText}\"")
                    }
                }
                Text(
                    text = contextText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (candidate.role.isNotBlank()) {
                    CandidateBadge(label = "role=${candidate.role}")
                }
                candidate.attributes["href"]?.takeIf { it.isNotBlank() }?.let { href ->
                    CandidateBadge(label = "href=$href")
                }
                candidate.attributes["type"]?.takeIf { it.isNotBlank() }?.let { t ->
                    CandidateBadge(label = "type=$t")
                }
                candidate.attributes["name"]?.takeIf { it.isNotBlank() }?.let { nm ->
                    CandidateBadge(label = "name=$nm")
                }
                candidate.attributes["id"]?.takeIf { it.isNotBlank() }?.let { id ->
                    CandidateBadge(label = "id=$id")
                }
                if (!candidate.isEnabled) {
                    CandidateBadge(label = "disabled")
                }
                if (!candidate.isVisible) {
                    CandidateBadge(label = "hidden")
                }
            }

            if (candidate.recommendedSelector.isNotBlank()) {
                Text(
                    text = candidate.recommendedSelector,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CandidateBadge(label: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun DiagnosticsAccordion(
    diagnostics: LocatorResolutionDiagnostics,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            TextButton(
                onClick = onToggle,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("disambiguation_diagnostics_toggle")
            ) {
                Icon(
                    imageVector = Icons.Outlined.BugReport,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Resolution Diagnostics (${diagnostics.totalMatches} matches)",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse diagnostics" else "Expand diagnostics"
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = "Recorded locator: ${diagnostics.recordedLocator.ifBlank { "(none)" }}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Outcome: ${diagnostics.finalOutcome} • Visible ${diagnostics.visibleMatches}/${diagnostics.totalMatches}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    diagnostics.attemptedCandidates.take(6).forEach { attempt ->
                        val status = if (attempt.selected) "SELECTED" else attempt.rejectedReason.ifBlank { "SKIPPED" }
                        Text(
                            text = "• [${attempt.strategy}] ${attempt.selector} → $status (${attempt.matchCount} DOM / ${attempt.visibleCount} visible)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
