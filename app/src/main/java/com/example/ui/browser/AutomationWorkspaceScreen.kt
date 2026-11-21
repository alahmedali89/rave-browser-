package com.example.ui.browser

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.ui.semantics.Role
import com.example.data.preferences.BrowserPreferences
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.automation.AutomationStepCodec
import com.example.automation.AutomationType
import com.example.automation.AutomationWorkspaceCategory
import com.example.automation.InterruptedExecutionCandidate
import com.example.automation.LocatorResolutionDiagnostics
import com.example.automation.WorkflowConflictResolution
import com.example.automation.WorkflowExecutionState
import com.example.automation.WorkflowImportResult
import com.example.automation.WorkflowOrganizationMetadata
import com.example.automation.WorkflowSortOption
import com.example.data.local.AutomationExecutionEntity
import com.example.data.local.AutomationWorkflowEntity
import com.example.ui.theme.EmphasizedTypography
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationWorkspaceScreenOverlay(
    workflows: List<AutomationWorkflowEntity>,
    executions: List<AutomationExecutionEntity>,
    activeTabDomain: String,
    activeTabUrl: String,
    executionState: WorkflowExecutionState?,
    onCreateWorkflow: (name: String, domain: String, sourceUrl: String, instructionsText: String) -> Unit,
    onRunWorkflow: (Long) -> Unit,
    onOpenWorkflowDetails: (Long) -> Unit,
    onRenameWorkflow: (Long, String) -> Unit,
    onDuplicateWorkflow: (Long) -> Unit,
    onDeleteWorkflow: (Long) -> Unit,
    onExportJson: ((String) -> Unit) -> Unit,
    onImportJson: (String) -> Unit,
    onConfirmDestructiveExecution: () -> Unit,
    onCancelDestructiveExecution: () -> Unit,
    onClearExecutionBanner: () -> Unit,
    backHandlerEnabled: Boolean = true,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onCancelExecution: () -> Unit = {},
    onPauseExecution: () -> Unit = {},
    onResumeExecution: () -> Unit = {},
    interruptedCandidate: InterruptedExecutionCandidate? = null,
    onResumeInterrupted: () -> Unit = {},
    onRestartInterrupted: () -> Unit = {},
    onDismissInterrupted: () -> Unit = {},
    sortOption: WorkflowSortOption = WorkflowSortOption.UPDATED_DESC,
    selectedFolderFilter: String? = null,
    selectedTagFilter: String? = null,
    lastImportResult: WorkflowImportResult? = null,
    onChangeSortOption: (WorkflowSortOption) -> Unit = {},
    onSelectFolderFilter: (String?) -> Unit = {},
    onSelectTagFilter: (String?) -> Unit = {},
    onToggleFavorite: (Long) -> Unit = {},
    onUpdateOrganization: (Long, WorkflowOrganizationMetadata) -> Unit = { _, _ -> },
    onArchiveWorkflow: (Long) -> Unit = {},
    onRestoreArchivedWorkflow: (Long) -> Unit = {},
    onExportSinglePackageJson: (Long, Boolean, (String) -> Unit) -> Unit = { _, _, _ -> },
    onExportZipBytes: (List<Long>?, (ByteArray) -> Unit) -> Unit = { _, _ -> },
    onImportPackageJson: (String, WorkflowConflictResolution) -> Unit = { _, _ -> },
    onImportZipBytes: (ByteArray, WorkflowConflictResolution) -> Unit = { _, _ -> },
    onDismissImportResult: () -> Unit = {},
    onOpenDisambiguation: (() -> Unit)? = null,
    lastLocatorDiagnostics: LocatorResolutionDiagnostics? = null,
    automationVaultCount: Int = 0,
    onOpenAutomationVault: () -> Unit = {},
    isProActivated: Boolean = false,
    dailyPlaysRemaining: Int = BrowserPreferences.FREE_DAILY_AUTOMATION_PLAY_LIMIT,
    dailyPlayLimit: Int = BrowserPreferences.FREE_DAILY_AUTOMATION_PLAY_LIMIT,
    onOpenProSubscription: () -> Unit = {}
) {
    BackHandler(enabled = backHandlerEnabled, onBack = onDismiss)

    val context = LocalContext.current
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf(AutomationWorkspaceCategory.ALL) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var renamingWorkflow by remember { mutableStateOf<AutomationWorkflowEntity?>(null) }
    var deletingWorkflow by remember { mutableStateOf<AutomationWorkflowEntity?>(null) }
    var organizingWorkflow by remember { mutableStateOf<AutomationWorkflowEntity?>(null) }
    var showTopOverflowMenu by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var pendingImportConflictMode by remember { mutableStateOf(WorkflowConflictResolution.CREATE_COPY) }
    var pendingExportJsonPayload by remember { mutableStateOf<Pair<String?, String>?>(null) }
    var pendingExportZipIds by remember { mutableStateOf<List<Long>?>(null) }

    val openImportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()?.let { bytes ->
                if (bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
                    onImportZipBytes(bytes, pendingImportConflictMode)
                } else {
                    onImportPackageJson(bytes.toString(Charsets.UTF_8), pendingImportConflictMode)
                }
            }
        }
    }

    val createJsonFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val jsonContent = pendingExportJsonPayload?.second
        if (uri != null && jsonContent != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(jsonContent.toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    val createZipFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            onExportZipBytes(pendingExportZipIds) { bytes ->
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(bytes)
                    }
                }
            }
        }
    }

    if (executionState?.isAwaitingConfirmation == true) {
        DestructiveAutomationConfirmDialog(
            workflowName = executionState.workflowName,
            destructiveSteps = executionState.pendingDestructiveSteps,
            onConfirm = onConfirmDestructiveExecution,
            onDismiss = onCancelDestructiveExecution
        )
    }

    if (lastImportResult != null) {
        WorkflowImportResultDialog(
            result = lastImportResult,
            onDismiss = onDismissImportResult
        )
    }

    if (showCreateDialog) {
        CreateAutomationDialog(
            defaultDomain = activeTabDomain,
            defaultUrl = activeTabUrl,
            onCreate = { name, domain, url, instructions ->
                onCreateWorkflow(name, domain, url, instructions)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    renamingWorkflow?.let { wf ->
        RenameAutomationDialog(
            currentName = wf.name,
            onConfirm = { newName ->
                onRenameWorkflow(wf.id, newName)
                renamingWorkflow = null
            },
            onDismiss = { renamingWorkflow = null }
        )
    }

    organizingWorkflow?.let { wf ->
        WorkflowOrganizationDialog(
            workflow = wf,
            onSaveOrganization = { org -> onUpdateOrganization(wf.id, org) },
            onArchiveToggle = { archive ->
                if (archive) onArchiveWorkflow(wf.id) else onRestoreArchivedWorkflow(wf.id)
            },
            onDismiss = { organizingWorkflow = null }
        )
    }

    deletingWorkflow?.let { wf ->
        AlertDialog(
            onDismissRequest = { deletingWorkflow = null },
            title = { Text("Delete Automation?") },
            text = {
                Text("\"${wf.name}\" and its recorded execution history will be permanently deleted.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteWorkflow(wf.id)
                        deletingWorkflow = null
                    },
                    modifier = Modifier.testTag("confirm_delete_workflow_button")
                ) {
                    Text(stringResource(R.string.automation_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingWorkflow = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showImportDialog) {
        WorkflowPackageImportDialog(
            onImportJson = { raw, mode ->
                onImportPackageJson(raw, mode)
            },
            onPickDocumentFile = { mode ->
                pendingImportConflictMode = mode
                openImportFileLauncher.launch(arrayOf("application/json", "application/zip", "*/*"))
            },
            onDismiss = { showImportDialog = false }
        )
    }

    pendingExportJsonPayload?.let { (wfName, json) ->
        WorkflowPackageExportDialog(
            workflowName = wfName,
            jsonPreview = json,
            onSaveJsonFile = {
                val baseName = (wfName ?: "apex-workflows").replace(Regex("[^a-zA-Z0-9_-]"), "_")
                createJsonFileLauncher.launch("$baseName.apexworkflow.json")
            },
            onSaveZipFile = {
                val baseName = (wfName ?: "apex-workflows").replace(Regex("[^a-zA-Z0-9_-]"), "_")
                createZipFileLauncher.launch("$baseName.apexworkflow.zip")
            },
            onDismiss = { pendingExportJsonPayload = null }
        )
    }

    val availableFolders = remember(workflows) {
        workflows.mapNotNull { it.folder.trim().takeIf { f -> f.isNotEmpty() } }.distinct().sorted()
    }
    val availableTags = remember(workflows) {
        workflows.flatMap { WorkflowOrganizationMetadata.decodeTagsJson(it.tagsJson) }.distinct().sorted()
    }

    val normalizedQuery = searchQuery.trim().lowercase()
    val filteredWorkflows = remember(
        workflows,
        selectedCategory,
        normalizedQuery,
        sortOption,
        selectedFolderFilter,
        selectedTagFilter
    ) {
        val categoryFiltered = when (selectedCategory) {
            AutomationWorkspaceCategory.ALL -> workflows.filter { !it.isArchived }
            AutomationWorkspaceCategory.FAVORITES -> workflows.filter { !it.isArchived && it.isFavorite }
            AutomationWorkspaceCategory.RECORDED -> workflows.filter {
                !it.isArchived && AutomationType.fromId(it.type) == AutomationType.RECORDED
            }
            AutomationWorkspaceCategory.MANUAL -> workflows.filter {
                !it.isArchived && AutomationType.fromId(it.type) == AutomationType.MANUAL
            }
            AutomationWorkspaceCategory.RECENT -> workflows
                .filter { !it.isArchived && it.lastExecutedAt != null }
            AutomationWorkspaceCategory.ARCHIVED -> workflows.filter { it.isArchived }
        }
        val orgFiltered = categoryFiltered.filter { wf ->
            val folderOk = selectedFolderFilter == null || wf.folder.equals(selectedFolderFilter, ignoreCase = true)
            val tags = WorkflowOrganizationMetadata.decodeTagsJson(wf.tagsJson)
            val tagOk = selectedTagFilter == null || tags.any { it.equals(selectedTagFilter, ignoreCase = true) }
            folderOk && tagOk
        }
        val searched = if (normalizedQuery.isEmpty()) {
            orgFiltered
        } else {
            orgFiltered.filter { wf ->
                val tags = WorkflowOrganizationMetadata.decodeTagsJson(wf.tagsJson)
                wf.name.lowercase().contains(normalizedQuery) ||
                    wf.domain.lowercase().contains(normalizedQuery) ||
                    wf.type.lowercase().contains(normalizedQuery) ||
                    wf.folder.lowercase().contains(normalizedQuery) ||
                    tags.any { it.lowercase().contains(normalizedQuery) }
            }
        }
        when {
            selectedCategory == AutomationWorkspaceCategory.RECENT ->
                searched.sortedByDescending { it.lastExecutedAt ?: 0L }
            sortOption == WorkflowSortOption.UPDATED_DESC ->
                searched.sortedWith(compareByDescending<AutomationWorkflowEntity> { it.isFavorite }.thenByDescending { it.updatedAt })
            sortOption == WorkflowSortOption.NAME_ASC ->
                searched.sortedBy { it.name.lowercase() }
            sortOption == WorkflowSortOption.RECENTLY_EXECUTED ->
                searched.sortedByDescending { it.lastExecutedAt ?: 0L }
            sortOption == WorkflowSortOption.MOST_STEPS ->
                searched.sortedByDescending { AutomationStepCodec.decode(it.stepsJson).size }
            sortOption == WorkflowSortOption.VERSION_DESC ->
                searched.sortedByDescending { it.currentVersion }
            else -> searched
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("automation_workspace_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.automation_workspace_title),
                        style = EmphasizedTypography.headlineHero
                    )
                },
                actions = {
                    IconButton(
                        onClick = onOpenAutomationVault,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("automation_workspace_vault_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = stringResource(R.string.automation_vault_title)
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { showTopOverflowMenu = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("automation_workspace_more_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "More options"
                            )
                        }
                        DropdownMenu(
                            expanded = showTopOverflowMenu,
                            onDismissRequest = { showTopOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export Collection (JSON / ZIP)") },
                                leadingIcon = { Icon(Icons.Filled.FileUpload, contentDescription = null) },
                                onClick = {
                                    showTopOverflowMenu = false
                                    pendingExportZipIds = null
                                    onExportJson { pendingExportJsonPayload = null to it }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Import Package (JSON / ZIP)") },
                                leadingIcon = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
                                onClick = {
                                    showTopOverflowMenu = false
                                    showImportDialog = true
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            WorkflowSortOption.entries.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text("Sort: ${opt.label}") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (opt == sortOption) Icons.Filled.Check else Icons.AutoMirrored.Filled.Sort,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showTopOverflowMenu = false
                                        onChangeSortOption(opt)
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.testTag("automation_workspace_fab")
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.automation_quick_create)
                )
            }
        },
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .testTag("automation_workspace_screen")
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .testTag("automation_workspace_list")
            ) {
                item(key = "workspace_search_and_filters") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val clampedRemaining = dailyPlaysRemaining.coerceIn(0, dailyPlayLimit)
                        val isExhausted = !isProActivated && clampedRemaining <= 0
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = when {
                                isProActivated -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                isExhausted -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            border = BorderStroke(
                                0.75.dp,
                                when {
                                    isProActivated -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                    isExhausted -> MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(role = Role.Button, onClick = onOpenProSubscription)
                                .testTag("workspace_pro_quota_chip")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 9.dp)
                            ) {
                                Icon(
                                    imageVector = if (isProActivated) {
                                        Icons.Filled.Verified
                                    } else {
                                        Icons.Outlined.WorkspacePremium
                                    },
                                    contentDescription = null,
                                    tint = if (isExhausted) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (isProActivated) {
                                        stringResource(R.string.pro_plan_active_label)
                                    } else {
                                        stringResource(R.string.pro_plan_free_format, clampedRemaining, dailyPlayLimit)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isExhausted) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (!isProActivated) {
                                    Text(
                                        text = if (isExhausted) "Activate Pro" else "Upgrade",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isExhausted) {
                                            MaterialTheme.colorScheme.onErrorContainer
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.automation_search_hint),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.automation_clear))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("automation_workspace_search_input")
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            AutomationWorkspaceCategory.entries.forEach { category ->
                                val selected = selectedCategory == category
                                val countForCategory = when (category) {
                                    AutomationWorkspaceCategory.ALL -> workflows.count { !it.isArchived }
                                    AutomationWorkspaceCategory.FAVORITES -> workflows.count { !it.isArchived && it.isFavorite }
                                    AutomationWorkspaceCategory.RECORDED -> workflows.count {
                                        !it.isArchived && AutomationType.fromId(it.type) == AutomationType.RECORDED
                                    }
                                    AutomationWorkspaceCategory.MANUAL -> workflows.count {
                                        !it.isArchived && AutomationType.fromId(it.type) == AutomationType.MANUAL
                                    }
                                    AutomationWorkspaceCategory.RECENT -> workflows.count {
                                        !it.isArchived && it.lastExecutedAt != null
                                    }
                                    AutomationWorkspaceCategory.ARCHIVED -> workflows.count { it.isArchived }
                                }
                                FilterChip(
                                    selected = selected,
                                    onClick = { selectedCategory = category },
                                    label = { Text("${category.label} ($countForCategory)") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.testTag("workspace_category_${category.name.lowercase()}")
                                )
                            }
                        }

                        if (availableFolders.isNotEmpty() || availableTags.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                availableFolders.forEach { folder ->
                                    val selected = selectedFolderFilter.equals(folder, ignoreCase = true)
                                    FilterChip(
                                        selected = selected,
                                        onClick = { onSelectFolderFilter(if (selected) null else folder) },
                                        leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        label = { Text(folder) },
                                        modifier = Modifier.testTag("workspace_folder_filter_${folder.lowercase()}")
                                    )
                                }
                                availableTags.forEach { tag ->
                                    val selected = selectedTagFilter.equals(tag, ignoreCase = true)
                                    FilterChip(
                                        selected = selected,
                                        onClick = { onSelectTagFilter(if (selected) null else tag) },
                                        label = { Text("#$tag") },
                                        modifier = Modifier.testTag("workspace_tag_filter_${tag.lowercase()}")
                                    )
                                }
                            }
                        }
                    }
                }

                val isExecutionRunning = executionState?.executionStatus?.isActive == true
                if (interruptedCandidate != null && !isExecutionRunning) {
                    item(key = "workspace_interrupted_banner") {
                        InterruptedExecutionResumeBanner(
                            candidate = interruptedCandidate,
                            onResume = onResumeInterrupted,
                            onRestart = onRestartInterrupted,
                            onDismiss = onDismissInterrupted
                        )
                    }
                }
                if (executionState != null && !executionState.isAwaitingConfirmation && !executionState.isInterrupted) {
                    item(key = "workspace_execution_banner") {
                        AutomationExecutionStatusBanner(
                            executionState = executionState,
                            onCancel = onCancelExecution,
                            onPause = onPauseExecution,
                            onResume = onResumeExecution,
                            onReplay = if (executionState.workflowId > 0L) {
                                { onRunWorkflow(executionState.workflowId) }
                            } else {
                                null
                            },
                            onDismiss = onClearExecutionBanner,
                            onOpenDisambiguation = onOpenDisambiguation,
                            lastLocatorDiagnostics = lastLocatorDiagnostics
                        )
                    }
                }

                if (filteredWorkflows.isEmpty()) {
                    item(key = "workspace_empty_state") {
                        AutomationWorkspaceEmptyState(
                            hasAnyWorkflows = workflows.isNotEmpty(),
                            hasSearchQuery = normalizedQuery.isNotEmpty() || selectedFolderFilter != null || selectedTagFilter != null,
                            category = selectedCategory,
                            onCreateClick = { showCreateDialog = true },
                            onClearFilter = {
                                searchQuery = ""
                                selectedCategory = AutomationWorkspaceCategory.ALL
                                onSelectFolderFilter(null)
                                onSelectTagFilter(null)
                            }
                        )
                    }
                } else {
                    items(
                        items = filteredWorkflows,
                        key = { it.id }
                    ) { workflow ->
                        AutomationWorkflowCard(
                            workflow = workflow,
                            isRunEnabled = !isExecutionRunning && !workflow.isArchived,
                            onOpenDetails = { onOpenWorkflowDetails(workflow.id) },
                            onRun = { onRunWorkflow(workflow.id) },
                            onEdit = { onOpenWorkflowDetails(workflow.id) },
                            onRename = { renamingWorkflow = workflow },
                            onDuplicate = { onDuplicateWorkflow(workflow.id) },
                            onToggleFavorite = { onToggleFavorite(workflow.id) },
                            onOrganize = { organizingWorkflow = workflow },
                            onArchiveToggle = {
                                if (workflow.isArchived) onRestoreArchivedWorkflow(workflow.id) else onArchiveWorkflow(workflow.id)
                            },
                            onExportBundle = {
                                pendingExportZipIds = listOf(workflow.id)
                                onExportSinglePackageJson(workflow.id, true) { json ->
                                    pendingExportJsonPayload = workflow.name to json
                                }
                            },
                            onDelete = { deletingWorkflow = workflow }
                        )
                    }
                }

                if (selectedCategory == AutomationWorkspaceCategory.RECENT && executions.isNotEmpty()) {
                    item(key = "recent_executions_header") {
                        Spacer(modifier = Modifier.height(6.dp))
                        MaterialGroupSectionHeader(
                            title = "Execution Log (${executions.size})",
                            icon = Icons.Outlined.History
                        )
                    }
                    items(
                        items = executions.take(15),
                        key = { "exec_${it.id}" }
                    ) { exec ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable { onOpenWorkflowDetails(exec.workflowId) }
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Text(
                                    text = "${exec.workflowName} • ${exec.status}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${exec.domain} • ${exec.completedSteps}/${exec.totalSteps} steps • ${exec.verificationSummary}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutomationWorkflowCard(
    workflow: AutomationWorkflowEntity,
    isRunEnabled: Boolean = true,
    onOpenDetails: () -> Unit,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onToggleFavorite: () -> Unit = {},
    onOrganize: () -> Unit = {},
    onArchiveToggle: () -> Unit = {},
    onExportBundle: () -> Unit = {},
    onDelete: () -> Unit
) {
    val dateFormatter = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val type = AutomationType.fromId(workflow.type)
    var menuExpanded by remember { mutableStateOf(false) }
    val tags = remember(workflow.tagsJson) { WorkflowOrganizationMetadata.decodeTagsJson(workflow.tagsJson) }

    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        onClick = onOpenDetails,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("automation_workflow_card_${workflow.id}")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SettingsIconBadge(
                    icon = if (type == AutomationType.RECORDED) {
                        Icons.Outlined.RadioButtonChecked
                    } else {
                        Icons.Outlined.EditNote
                    },
                    highlighted = !workflow.isArchived
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = workflow.name,
                            style = EmphasizedTypography.activeTabTitle,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "v${workflow.currentVersion}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                        if (workflow.isArchived) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = stringResource(R.string.automation_archived_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    val docSummary = remember(workflow.stepsJson) {
                        val doc = AutomationStepCodec.decodeDocument(workflow.stepsJson)
                        val varCount = doc.metadata.variables.size
                        val nestedCount = doc.steps.count {
                            it.condition != null || it.loopConfig != null || it.subWorkflowRef != null
                        }
                        buildList {
                            if (varCount > 0) add("$varCount var${if (varCount == 1) "" else "s"}")
                            if (nestedCount > 0) add("$nestedCount logic/sub")
                            if (doc.metadata.defaultRecoveryPolicy != null) add("recovery")
                        }.joinToString(" • ")
                    }
                    Text(
                        text = buildString {
                            append("${workflow.domain} • ${type.label} • ${workflow.stepCount} step${if (workflow.stepCount == 1) "" else "s"}")
                            if (docSummary.isNotBlank()) append(" • $docSummary")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (workflow.folder.isNotBlank() || tags.isNotEmpty()) {
                        Text(
                            text = buildList {
                                if (workflow.folder.isNotBlank()) add("Folder: ${workflow.folder}")
                                tags.forEach { add("#$it") }
                            }.joinToString(" • "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("workflow_favorite_button_${workflow.id}")
                ) {
                    Icon(
                        imageVector = if (workflow.isFavorite) Icons.Filled.Star else Icons.Filled.StarOutline,
                        contentDescription = stringResource(R.string.automation_favorite_toggle),
                        tint = if (workflow.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("workflow_more_button_${workflow.id}")
                    ) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Workflow actions")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.automation_edit)) },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.automation_rename)) },
                            leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.automation_organize)) },
                            leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onOrganize()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export Package (+ Deps)") },
                            leadingIcon = { Icon(Icons.Filled.FileUpload, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onExportBundle()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.automation_duplicate)) },
                            leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onDuplicate()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (workflow.isArchived) {
                                        stringResource(R.string.automation_restore_archive)
                                    } else {
                                        stringResource(R.string.automation_archive)
                                    }
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (workflow.isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onArchiveToggle()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.automation_delete)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Modified ${dateFormatter.format(Date(workflow.updatedAt))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Last status: ${formatExecutionStatusLabel(workflow.lastExecutionStatus)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = when (workflow.lastExecutionStatus) {
                            "COMPLETED" -> MaterialTheme.colorScheme.primary
                            "FAILED" -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("workflow_edit_button_${workflow.id}")
                    ) {
                        Text(stringResource(R.string.automation_edit))
                    }
                    if (workflow.isArchived) {
                        FilledTonalButton(
                            onClick = onArchiveToggle,
                            modifier = Modifier.testTag("workflow_restore_button_${workflow.id}")
                        ) {
                            Icon(Icons.Filled.Unarchive, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Restore")
                        }
                    } else {
                        FilledTonalButton(
                            onClick = onRun,
                            enabled = isRunEnabled,
                            modifier = Modifier.testTag("workflow_run_button_${workflow.id}")
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.automation_run))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutomationWorkspaceEmptyState(
    hasAnyWorkflows: Boolean,
    hasSearchQuery: Boolean,
    category: AutomationWorkspaceCategory,
    onCreateClick: () -> Unit,
    onClearFilter: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(if (hasSearchQuery) "automation_no_results_state" else "automation_empty_state")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = ApexIcons.MotionSensorActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Text(
                text = when {
                    hasSearchQuery -> "No matching automations"
                    hasAnyWorkflows -> "No ${category.label.lowercase()} automations yet"
                    else -> stringResource(R.string.automation_empty_title)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = when {
                    hasSearchQuery -> "Try searching by another workflow name, domain, or type."
                    hasAnyWorkflows -> "Switch category tabs or create a new workflow."
                    else -> stringResource(R.string.automation_empty_subtitle)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (hasSearchQuery || hasAnyWorkflows) {
                OutlinedButton(onClick = onClearFilter) {
                    Text("Show All Automations")
                }
            } else {
                Button(onClick = onCreateClick) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.automation_quick_create))
                }
            }
        }
    }
}

@Composable
fun CreateAutomationDialog(
    defaultDomain: String,
    defaultUrl: String,
    onCreate: (name: String, domain: String, sourceUrl: String, instructionsText: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf(if (defaultDomain == "Start Page") "" else defaultDomain) }
    var sourceUrl by remember { mutableStateOf(if (defaultUrl == "apex://home") "" else defaultUrl) }
    var instructions by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.automation_quick_create)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Automation name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_automation_name_input")
                )
                OutlinedTextField(
                    value = sourceUrl,
                    onValueChange = {
                        sourceUrl = it
                        if (domain.isBlank()) domain = it.substringAfter("://").substringBefore('/')
                    },
                    label = { Text("Target URL or domain (optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_automation_url_input")
                )
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text("Instructions (one step per line)") },
                    placeholder = { Text(stringResource(R.string.automation_instructions_placeholder)) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_automation_instructions_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, domain, sourceUrl, instructions) },
                enabled = instructions.isNotBlank(),
                modifier = Modifier.testTag("create_automation_confirm_button")
            ) {
                Text(stringResource(R.string.automation_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun RenameAutomationDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember(currentName) { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.automation_rename)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Workflow name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rename_automation_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(value) },
                enabled = value.isNotBlank(),
                modifier = Modifier.testTag("rename_automation_confirm_button")
            ) {
                Text(stringResource(R.string.automation_rename))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

internal fun formatExecutionStatusLabel(rawStatus: String): String = when (rawStatus.uppercase()) {
    "COMPLETED" -> "Completed"
    "FAILED" -> "Failed"
    "RUNNING" -> "Running"
    "PAUSED" -> "Paused"
    "INTERRUPTED" -> "Interrupted"
    "CANCELLED" -> "Cancelled"
    else -> "Never run"
}
