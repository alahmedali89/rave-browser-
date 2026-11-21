package com.example.ui.browser

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.automation.RevealedCredentialDetails
import com.example.automation.VaultStatus
import com.example.data.local.CredentialVaultEntity
import com.example.data.local.SavedPasswordEntity
import com.example.engine.PasswordAuditEngine
import com.example.engine.PasswordAuditReport
import com.example.engine.PasswordStrength
import com.example.engine.PasswordStrengthEvaluator
import com.example.engine.PasswordVaultUtil
import com.example.engine.SecurePasswordGenerator
import com.example.ui.theme.ApexMotion
import com.example.ui.theme.EmphasizedTypography
import com.example.ui.theme.JetBrainsMonoFamily
import java.util.Locale

enum class PasswordManagerTab(val label: String) {
    VAULT("Vault"),
    GENERATOR("Generator"),
    AUDIT("Audit")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PasswordManagerScreenOverlay(
    passwords: List<SavedPasswordEntity>,
    autofillEnabled: Boolean,
    decryptPassword: (SavedPasswordEntity) -> String,
    onSavePassword: (
        id: Long,
        siteName: String,
        siteUrl: String,
        username: String,
        plainPassword: String,
        notes: String,
        category: String,
        isFavorite: Boolean
    ) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onDeletePassword: (Long) -> Unit,
    onClearVault: () -> Unit,
    onImportCsv: (String) -> Unit,
    onExportCsv: ((String) -> Unit) -> Unit,
    onToggleAutofill: (Boolean) -> Unit,
    onOpenWebsite: (String) -> Unit,
    onDismiss: () -> Unit,
    backHandlerEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    credentialVaultEntries: List<CredentialVaultEntity> = emptyList(),
    vaultStatus: VaultStatus = VaultStatus.LOCKED,
    vaultStatusMessage: String? = null,
    revealedVaultCredential: RevealedCredentialDetails? = null,
    onUnlockVault: () -> Unit = {},
    onLockVault: () -> Unit = {},
    onAddVaultCredential: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onUpdateVaultCredential: (String, String, String, String, String) -> Unit = { _, _, _, _, _ -> },
    onRevealVaultCredential: (String) -> Unit = {},
    onHideRevealedVaultCredential: () -> Unit = {},
    onDeleteVaultCredential: (String) -> Unit = {},
    onResetInvalidatedVault: () -> Unit = {}
) {
    BackHandler(enabled = backHandlerEnabled, onBack = onDismiss)

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var activeSection by rememberSaveable { mutableStateOf(PasswordManagerTab.VAULT) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf("All") }
    var splitMenuExpanded by remember { mutableStateOf(false) }

    var showEditorSheet by remember { mutableStateOf(false) }
    var editingEntity by remember { mutableStateOf<SavedPasswordEntity?>(null) }
    var editorInitialPassword by remember { mutableStateOf("") }
    var inspectedEntity by remember { mutableStateOf<SavedPasswordEntity?>(null) }
    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    var exportCsvContent by rememberSaveable { mutableStateOf<String?>(null) }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }

    val revealedIds = remember { mutableStateMapOf<Long, Boolean>() }

    val auditReport = remember(passwords) {
        PasswordAuditEngine.analyze(passwords, decryptPassword)
    }

    val filteredPasswords = remember(passwords, searchQuery, selectedFilter) {
        val q = searchQuery.trim().lowercase(Locale.US)
        passwords.filter { item ->
            val matchesQuery = q.isEmpty() ||
                item.siteName.lowercase(Locale.US).contains(q) ||
                item.host.lowercase(Locale.US).contains(q) ||
                item.username.lowercase(Locale.US).contains(q) ||
                item.category.lowercase(Locale.US).contains(q)

            val matchesFilter = when (selectedFilter) {
                "All" -> true
                "Pinned" -> item.isFavorite
                else -> item.category.equals(selectedFilter, ignoreCase = true)
            }
            matchesQuery && matchesFilter
        }
    }

    val pinnedItems = remember(filteredPasswords) {
        filteredPasswords.filter { it.isFavorite }
    }
    val regularItems = remember(filteredPasswords) {
        filteredPasswords.filterNot { it.isFavorite }
    }

    val splitRotation by animateFloatAsState(
        targetValue = if (splitMenuExpanded) 180f else 0f,
        animationSpec = ApexMotion.spatialSpring(),
        label = "SplitButtonChevronRotation"
    )

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
                            .testTag("passwords_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.passwords_screen_title),
                            style = EmphasizedTypography.headlineHero
                        )
                        if (passwords.isNotEmpty()) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = passwords.size.toString(),
                                    style = EmphasizedTypography.badgeCounter,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                },
                actions = {
                    Box(modifier = Modifier.padding(end = 10.dp)) {
                        SplitButtonLayout(
                            leadingButton = {
                                SplitButtonDefaults.LeadingButton(
                                    onClick = {
                                        editingEntity = null
                                        editorInitialPassword = ""
                                        showEditorSheet = true
                                    },
                                    modifier = Modifier.testTag("passwords_split_add_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize)
                                    )
                                    Spacer(Modifier.size(6.dp))
                                    Text(stringResource(R.string.passwords_add_button))
                                }
                            },
                            trailingButton = {
                                SplitButtonDefaults.TrailingButton(
                                    checked = splitMenuExpanded,
                                    onCheckedChange = { splitMenuExpanded = it },
                                    modifier = Modifier.testTag("passwords_split_menu_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ExpandMore,
                                        contentDescription = stringResource(R.string.cd_tab_switcher_more_options),
                                        modifier = Modifier
                                            .size(SplitButtonDefaults.TrailingIconSize)
                                            .graphicsLayer { rotationZ = splitRotation }
                                    )
                                }
                            }
                        )

                        DropdownMenu(
                            expanded = splitMenuExpanded,
                            onDismissRequest = { splitMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                                text = { Text("Generate Strong Password") },
                                onClick = {
                                    splitMenuExpanded = false
                                    activeSection = PasswordManagerTab.GENERATOR
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Outlined.UploadFile, contentDescription = null) },
                                text = { Text(stringResource(R.string.passwords_import_csv)) },
                                onClick = {
                                    splitMenuExpanded = false
                                    showImportDialog = true
                                }
                            )
                            if (passwords.isNotEmpty()) {
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Outlined.Download, contentDescription = null) },
                                    text = { Text(stringResource(R.string.passwords_export_csv)) },
                                    onClick = {
                                        splitMenuExpanded = false
                                        onExportCsv { csv -> exportCsvContent = csv }
                                    }
                                )
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.CleaningServices,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    text = {
                                        Text(
                                            text = stringResource(R.string.passwords_clear_vault),
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        splitMenuExpanded = false
                                        showClearDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .testTag("password_manager_screen")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
            ) {
                // Material 3 Expressive Connected ToggleButton Switcher
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    val tabs = PasswordManagerTab.entries
                    tabs.forEachIndexed { idx, tab ->
                        val shapes = when (idx) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            tabs.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        }
                        val selected = activeSection == tab
                        ToggleButton(
                            checked = selected,
                            onCheckedChange = { if (it) activeSection = tab },
                            shapes = shapes,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("passwords_mode_${tab.name.lowercase(Locale.US)}")
                        ) {
                            Icon(
                                imageVector = when (tab) {
                                    PasswordManagerTab.VAULT -> if (selected) Icons.Filled.Key else Icons.Outlined.Key
                                    PasswordManagerTab.GENERATOR -> Icons.Filled.AutoAwesome
                                    PasswordManagerTab.AUDIT -> if (selected) Icons.Filled.Shield else Icons.Outlined.Shield
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = when (tab) {
                                    PasswordManagerTab.VAULT -> stringResource(R.string.passwords_tab_vault)
                                    PasswordManagerTab.GENERATOR -> stringResource(R.string.passwords_tab_generator)
                                    PasswordManagerTab.AUDIT -> stringResource(R.string.passwords_tab_audit)
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                AnimatedContent(
                    targetState = activeSection,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "PasswordManagerSectionContent",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { section ->
                    when (section) {
                        PasswordManagerTab.VAULT -> VaultSectionContent(
                            passwords = passwords,
                            pinnedItems = pinnedItems,
                            regularItems = regularItems,
                            auditReport = auditReport,
                            autofillEnabled = autofillEnabled,
                            searchQuery = searchQuery,
                            selectedFilter = selectedFilter,
                            revealedIds = revealedIds,
                            decryptPassword = decryptPassword,
                            onSearchQueryChange = { searchQuery = it },
                            onSelectFilter = { selectedFilter = it },
                            onToggleAutofill = onToggleAutofill,
                            onOpenAudit = { activeSection = PasswordManagerTab.AUDIT },
                            onAddClick = {
                                editingEntity = null
                                editorInitialPassword = ""
                                showEditorSheet = true
                            },
                            onInspectItem = { inspectedEntity = it },
                            onCopyUsername = { item ->
                                clipboard.setText(AnnotatedString(item.username))
                                Toast.makeText(context, "Username copied", Toast.LENGTH_SHORT).show()
                            },
                            onCopyPassword = { item ->
                                clipboard.setText(AnnotatedString(decryptPassword(item)))
                                Toast.makeText(context, "Password copied", Toast.LENGTH_SHORT).show()
                            },
                            onToggleReveal = { id ->
                                revealedIds[id] = !(revealedIds[id] ?: false)
                            },
                            credentialVaultEntries = credentialVaultEntries,
                            vaultStatus = vaultStatus,
                            vaultStatusMessage = vaultStatusMessage,
                            revealedVaultCredential = revealedVaultCredential,
                            onUnlockVault = onUnlockVault,
                            onLockVault = onLockVault,
                            onAddVaultCredential = onAddVaultCredential,
                            onUpdateVaultCredential = onUpdateVaultCredential,
                            onRevealVaultCredential = onRevealVaultCredential,
                            onHideRevealedVaultCredential = onHideRevealedVaultCredential,
                            onDeleteVaultCredential = onDeleteVaultCredential,
                            onResetInvalidatedVault = onResetInvalidatedVault
                        )

                        PasswordManagerTab.GENERATOR -> GeneratorSectionContent(
                            onSaveGeneratedPassword = { generated ->
                                editingEntity = null
                                editorInitialPassword = generated
                                showEditorSheet = true
                            }
                        )

                        PasswordManagerTab.AUDIT -> AuditSectionContent(
                            auditReport = auditReport,
                            onFixWithGeneratedPassword = { item ->
                                editingEntity = item
                                editorInitialPassword = SecurePasswordGenerator.generateRandom(length = 16)
                                showEditorSheet = true
                            },
                            onInspectItem = { inspectedEntity = it }
                        )
                    }
                }
            }
        }
    }

    if (showEditorSheet) {
        PasswordEditorModalSheet(
            editingEntity = editingEntity,
            initialPlainPassword = editorInitialPassword,
            onSave = { id, siteName, siteUrl, username, plainPassword, notes, category, isFavorite ->
                onSavePassword(id, siteName, siteUrl, username, plainPassword, notes, category, isFavorite)
                showEditorSheet = false
                editingEntity = null
                editorInitialPassword = ""
            },
            onDismiss = {
                showEditorSheet = false
                editingEntity = null
                editorInitialPassword = ""
            }
        )
    }

    inspectedEntity?.let { entity ->
        val plain = remember(entity.id, entity.encryptedPassword) { decryptPassword(entity) }
        val isReused = remember(auditReport, entity.id) {
            auditReport.reusedGroups.any { grp -> grp.any { it.id == entity.id } }
        }
        PasswordDetailModalSheet(
            entity = entity,
            plainPassword = plain,
            isReused = isReused,
            onEdit = {
                inspectedEntity = null
                editingEntity = entity
                editorInitialPassword = plain
                showEditorSheet = true
            },
            onToggleFavorite = {
                onToggleFavorite(entity.id)
                inspectedEntity = entity.copy(isFavorite = !entity.isFavorite)
            },
            onOpenWebsite = { url ->
                inspectedEntity = null
                onOpenWebsite(url)
            },
            onDelete = {
                inspectedEntity = null
                onDeletePassword(entity.id)
            },
            onDismiss = { inspectedEntity = null }
        )
    }

    if (showImportDialog) {
        PasswordImportCsvDialog(
            onImport = onImportCsv,
            onDismiss = { showImportDialog = false }
        )
    }

    exportCsvContent?.let { csv ->
        PasswordExportCsvDialog(
            csvContent = csv,
            count = passwords.size,
            onDismiss = { exportCsvContent = null }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Encrypted Vault?") },
            text = {
                Text("This permanently deletes all ${passwords.size} saved login${if (passwords.size == 1) "" else "s"} from your device. This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDialog = false
                        onClearVault()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VaultSectionContent(
    passwords: List<SavedPasswordEntity>,
    pinnedItems: List<SavedPasswordEntity>,
    regularItems: List<SavedPasswordEntity>,
    auditReport: PasswordAuditReport,
    autofillEnabled: Boolean,
    searchQuery: String,
    selectedFilter: String,
    revealedIds: Map<Long, Boolean>,
    decryptPassword: (SavedPasswordEntity) -> String,
    onSearchQueryChange: (String) -> Unit,
    onSelectFilter: (String) -> Unit,
    onToggleAutofill: (Boolean) -> Unit,
    onOpenAudit: () -> Unit,
    onAddClick: () -> Unit,
    onInspectItem: (SavedPasswordEntity) -> Unit,
    onCopyUsername: (SavedPasswordEntity) -> Unit,
    onCopyPassword: (SavedPasswordEntity) -> Unit,
    onToggleReveal: (Long) -> Unit,
    credentialVaultEntries: List<CredentialVaultEntity> = emptyList(),
    vaultStatus: VaultStatus = VaultStatus.LOCKED,
    vaultStatusMessage: String? = null,
    revealedVaultCredential: RevealedCredentialDetails? = null,
    onUnlockVault: () -> Unit = {},
    onLockVault: () -> Unit = {},
    onAddVaultCredential: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onUpdateVaultCredential: (String, String, String, String, String) -> Unit = { _, _, _, _, _ -> },
    onRevealVaultCredential: (String) -> Unit = {},
    onHideRevealedVaultCredential: () -> Unit = {},
    onDeleteVaultCredential: (String) -> Unit = {},
    onResetInvalidatedVault: () -> Unit = {}
) {
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .testTag("passwords_vault_list")
        ) {
            // Expressive Vault Hero Security Card
            item(key = "vault_hero_card") {
                ExpressiveVaultHeroCard(
                    totalCount = passwords.size,
                    healthScore = auditReport.overallScore,
                    weakCount = auditReport.weakItems.size,
                    reusedCount = auditReport.reusedCount,
                    autofillEnabled = autofillEnabled,
                    onToggleAutofill = onToggleAutofill,
                    onOpenAudit = onOpenAudit
                )
            }

            // Phase 4B Secure Automation Credential Vault Card
            item(key = "phase4b_automation_credential_vault_card") {
                Phase4BCredentialVaultCard(
                    entries = credentialVaultEntries,
                    vaultStatus = vaultStatus,
                    vaultStatusMessage = vaultStatusMessage,
                    revealedCredential = revealedVaultCredential,
                    onUnlockVault = onUnlockVault,
                    onLockVault = onLockVault,
                    onAddCredential = onAddVaultCredential,
                    onUpdateCredential = onUpdateVaultCredential,
                    onRevealCredential = onRevealVaultCredential,
                    onHideRevealedCredential = onHideRevealedVaultCredential,
                    onDeleteCredential = onDeleteVaultCredential,
                    onResetInvalidatedVault = onResetInvalidatedVault
                )
            }

            // Squared Search Bar + Category Chips
            item(key = "vault_search_and_filters") {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            border = BorderStroke(
                                width = if (searchQuery.isNotEmpty()) 1.5.dp else 1.dp,
                                color = if (searchQuery.isNotEmpty()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Box(
                                    contentAlignment = Alignment.CenterStart,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.passwords_search_hint),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = onSearchQueryChange,
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("passwords_search_input")
                                    )
                                }
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { onSearchQueryChange("") },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = stringResource(R.string.cd_clear_input),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        val filterOptions = listOf("All", "Pinned") + PasswordVaultUtil.categories
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            filterOptions.forEach { option ->
                                val selected = selectedFilter == option
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSelectFilter(option) },
                                    label = { Text(option) },
                                    leadingIcon = if (selected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.testTag("passwords_filter_${option.lowercase(Locale.US)}")
                                )
                            }
                        }
                    }
                }
            }

            if (passwords.isEmpty()) {
                item(key = "vault_empty_state") {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 32.dp)
                        ) {
                            Surface(
                                shape = MaterialShapes.Cookie9Sided.toShape(),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.Password,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.passwords_empty_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = stringResource(R.string.passwords_empty_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = onAddClick,
                                modifier = Modifier.testTag("passwords_empty_add_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add First Login")
                            }
                        }
                    }
                }
            } else if (pinnedItems.isEmpty() && regularItems.isEmpty()) {
                item(key = "vault_no_matches") {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(28.dp)
                        ) {
                            Text(
                                text = "No matching credentials",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Try clearing your search or selecting All.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                if (pinnedItems.isNotEmpty()) {
                    item(key = "pinned_passwords_group") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            MaterialGroupSectionHeader(
                                icon = Icons.Filled.Star,
                                title = stringResource(R.string.passwords_pinned_section)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(GroupedListSpacing)) {
                                pinnedItems.forEachIndexed { idx, item ->
                                    PasswordCredentialGroupRow(
                                        entity = item,
                                        index = idx,
                                        totalCount = pinnedItems.size,
                                        isRevealed = revealedIds[item.id] == true,
                                        plainPassword = decryptPassword(item),
                                        onClick = { onInspectItem(item) },
                                        onToggleReveal = { onToggleReveal(item.id) },
                                        onCopyUsername = { onCopyUsername(item) },
                                        onCopyPassword = { onCopyPassword(item) }
                                    )
                                }
                            }
                        }
                    }
                }

                if (regularItems.isNotEmpty()) {
                    item(key = "all_passwords_group") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            MaterialGroupSectionHeader(
                                icon = Icons.Outlined.Lock,
                                title = stringResource(R.string.passwords_all_section),
                                trailingAction = {
                                    Text(
                                        text = "${regularItems.size} item${if (regularItems.size == 1) "" else "s"}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(GroupedListSpacing)) {
                                regularItems.forEachIndexed { idx, item ->
                                    PasswordCredentialGroupRow(
                                        entity = item,
                                        index = idx,
                                        totalCount = regularItems.size,
                                        isRevealed = revealedIds[item.id] == true,
                                        plainPassword = decryptPassword(item),
                                        onClick = { onInspectItem(item) },
                                        onToggleReveal = { onToggleReveal(item.id) },
                                        onCopyUsername = { onCopyUsername(item) },
                                        onCopyPassword = { onCopyPassword(item) }
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveVaultHeroCard(
    totalCount: Int,
    healthScore: Int,
    weakCount: Int,
    reusedCount: Int,
    autofillEnabled: Boolean,
    onToggleAutofill: (Boolean) -> Unit,
    onOpenAudit: () -> Unit
) {
    val healthTint by animateColorAsState(
        targetValue = when {
            totalCount == 0 || healthScore >= 80 -> MaterialTheme.colorScheme.primary
            healthScore >= 55 -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.error
        },
        label = "VaultHealthColor"
    )

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onOpenAudit)
                    .padding(4.dp)
            ) {
                Surface(
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (totalCount == 0) {
                            "Keystore Vault Ready"
                        } else {
                            "Vault Health: $healthScore%"
                        },
                        style = EmphasizedTypography.activeTabTitle.copy(
                            fontSize = MaterialTheme.typography.titleMedium.fontSize
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when {
                            totalCount == 0 -> "AES-256-GCM hardware-backed local encryption"
                            weakCount == 0 && reusedCount == 0 -> "All $totalCount saved logins use strong unique passwords"
                            else -> "$weakCount weak • $reusedCount reused • Tap to review audit"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (weakCount > 0 || reusedCount > 0) healthTint else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = healthTint.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = "$healthScore%",
                        style = EmphasizedTypography.badgeCounter,
                        color = healthTint,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            // Autofill quick toggle row
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(role = Role.Switch) { onToggleAutofill(!autofillEnabled) }
                    .testTag("passwords_autofill_switch")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoFixHigh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.passwords_autofill_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.passwords_autofill_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = autofillEnabled, onCheckedChange = null)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PasswordCredentialGroupRow(
    entity: SavedPasswordEntity,
    index: Int,
    totalCount: Int,
    isRevealed: Boolean,
    plainPassword: String,
    onClick: () -> Unit,
    onToggleReveal: () -> Unit,
    onCopyUsername: () -> Unit,
    onCopyPassword: () -> Unit
) {
    val itemShape = groupedListItemShape(index = index, totalCount = totalCount)
    val strength = remember(plainPassword) { PasswordStrengthEvaluator.evaluate(plainPassword) }
    val strengthColor = when (strength.strength) {
        PasswordStrength.WEAK -> MaterialTheme.colorScheme.error
        PasswordStrength.FAIR -> MaterialTheme.colorScheme.tertiary
        PasswordStrength.STRONG -> MaterialTheme.colorScheme.primary
        PasswordStrength.EXCELLENT -> MaterialTheme.colorScheme.secondary
    }

    Surface(
        shape = itemShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(itemShape)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("password_item_${entity.id}")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = if (entity.isFavorite) MaterialShapes.SoftBurst.toShape() else RoundedCornerShape(14.dp),
                    color = if (entity.isFavorite) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = entity.siteName.take(1).uppercase().ifBlank { "A" },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = if (entity.isFavorite) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = entity.siteName,
                            style = EmphasizedTypography.activeTabTitle,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Surface(
                            shape = CircleShape,
                            color = strengthColor.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = strength.strength.label,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = strengthColor,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = entity.username,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isRevealed) plainPassword else "•".repeat(plainPassword.length.coerceIn(8, 12)),
                        style = EmphasizedTypography.monoCode,
                        color = if (isRevealed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleReveal,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = if (isRevealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = stringResource(R.string.cd_toggle_password_visibility),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    IconButton(
                        onClick = onCopyPassword,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = stringResource(R.string.cd_copy_password),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GeneratorSectionContent(
    onSaveGeneratedPassword: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var usePassphrase by rememberSaveable { mutableStateOf(false) }
    var lengthFloat by rememberSaveable { mutableFloatStateOf(16f) }
    var wordCountFloat by rememberSaveable { mutableFloatStateOf(4f) }
    var includeUpper by rememberSaveable { mutableStateOf(true) }
    var includeLower by rememberSaveable { mutableStateOf(true) }
    var includeDigits by rememberSaveable { mutableStateOf(true) }
    var includeSymbols by rememberSaveable { mutableStateOf(true) }
    var passphraseWithExtras by rememberSaveable { mutableStateOf(true) }
    var spinCount by remember { mutableIntStateOf(0) }

    var generatedPassword by rememberSaveable {
        mutableStateOf(SecurePasswordGenerator.generateRandom(length = 16))
    }

    fun regenerate() {
        spinCount++
        generatedPassword = if (usePassphrase) {
            SecurePasswordGenerator.generatePassphrase(
                wordCount = wordCountFloat.toInt(),
                includeNumberAndSymbol = passphraseWithExtras
            )
        } else {
            SecurePasswordGenerator.generateRandom(
                length = lengthFloat.toInt(),
                includeUppercase = includeUpper,
                includeLowercase = includeLower,
                includeDigits = includeDigits,
                includeSymbols = includeSymbols
            )
        }
    }

    val spinAngle by animateFloatAsState(
        targetValue = spinCount * 180f,
        animationSpec = ApexMotion.spatialSpring(),
        label = "RegenerateSpin"
    )
    val strengthReport = remember(generatedPassword) {
        PasswordStrengthEvaluator.evaluate(generatedPassword)
    }
    val strengthColor by animateColorAsState(
        targetValue = when (strengthReport.strength) {
            PasswordStrength.WEAK -> MaterialTheme.colorScheme.error
            PasswordStrength.FAIR -> MaterialTheme.colorScheme.tertiary
            PasswordStrength.STRONG -> MaterialTheme.colorScheme.primary
            PasswordStrength.EXCELLENT -> MaterialTheme.colorScheme.secondary
        },
        label = "GenStrengthColor"
    )

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .testTag("password_generator_section")
        ) {
            item(key = "generator_preview_card") {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = MaterialShapes.SoftBurst.toShape(),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.passwords_generator_title),
                                    style = EmphasizedTypography.activeTabTitle.copy(
                                        fontSize = MaterialTheme.typography.titleMedium.fontSize
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${strengthReport.strength.label} • Score ${strengthReport.score}/100",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = strengthColor
                                )
                            }
                            FilledTonalIconButton(
                                onClick = { regenerate() },
                                modifier = Modifier.testTag("generator_regenerate_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = stringResource(R.string.cd_regenerate_password),
                                    modifier = Modifier.rotate(spinAngle)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = generatedPassword,
                                style = EmphasizedTypography.monoCode.copy(
                                    fontSize = MaterialTheme.typography.titleMedium.fontSize,
                                    lineHeight = MaterialTheme.typography.titleMedium.lineHeight
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .padding(16.dp)
                                    .testTag("generator_output_text")
                            )
                        }

                        LinearProgressIndicator(
                            progress = { (strengthReport.score / 100f).coerceIn(0f, 1f) },
                            color = strengthColor,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(generatedPassword))
                                    Toast.makeText(context, "Generated password copied", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("generator_copy_button")
                            ) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.action_copy))
                            }
                            Button(
                                onClick = { onSaveGeneratedPassword(generatedPassword) },
                                modifier = Modifier
                                    .weight(1.3f)
                                    .testTag("generator_save_to_vault_button")
                            ) {
                                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Save to Vault")
                            }
                        }
                    }
                }
            }

            // Expressive Generator Options
            item(key = "generator_controls_card") {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ToggleButton(
                                checked = !usePassphrase,
                                onCheckedChange = {
                                    if (it) {
                                        usePassphrase = false
                                        regenerate()
                                    }
                                },
                                shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Random Characters")
                            }
                            ToggleButton(
                                checked = usePassphrase,
                                onCheckedChange = {
                                    if (it) {
                                        usePassphrase = true
                                        regenerate()
                                    }
                                },
                                shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Passphrase")
                            }
                        }

                        if (!usePassphrase) {
                            Column {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Length", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "${lengthFloat.toInt()} chars",
                                        style = EmphasizedTypography.badgeCounter,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Slider(
                                    value = lengthFloat,
                                    onValueChange = {
                                        lengthFloat = it
                                        regenerate()
                                    },
                                    valueRange = 8f..32f,
                                    steps = 23
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Character Sets", style = MaterialTheme.typography.titleSmall)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val sets = listOf(
                                        Triple("A-Z", includeUpper) { v: Boolean ->
                                            if (v || includeLower || includeDigits || includeSymbols) {
                                                includeUpper = v
                                                regenerate()
                                            }
                                        },
                                        Triple("a-z", includeLower) { v: Boolean ->
                                            if (v || includeUpper || includeDigits || includeSymbols) {
                                                includeLower = v
                                                regenerate()
                                            }
                                        },
                                        Triple("0-9", includeDigits) { v: Boolean ->
                                            if (v || includeUpper || includeLower || includeSymbols) {
                                                includeDigits = v
                                                regenerate()
                                            }
                                        },
                                        Triple("!@#", includeSymbols) { v: Boolean ->
                                            if (v || includeUpper || includeLower || includeDigits) {
                                                includeSymbols = v
                                                regenerate()
                                            }
                                        }
                                    )
                                    sets.forEachIndexed { idx, (label, checked, onChange) ->
                                        val shapes = when (idx) {
                                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                            sets.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                        }
                                        ToggleButton(
                                            checked = checked,
                                            onCheckedChange = onChange,
                                            shapes = shapes,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(label, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        } else {
                            Column {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Word Count", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "${wordCountFloat.toInt()} words",
                                        style = EmphasizedTypography.badgeCounter,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Slider(
                                    value = wordCountFloat,
                                    onValueChange = {
                                        wordCountFloat = it
                                        regenerate()
                                    },
                                    valueRange = 3f..6f,
                                    steps = 2
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        passphraseWithExtras = !passphraseWithExtras
                                        regenerate()
                                    }
                                    .padding(vertical = 6.dp)
                            ) {
                                Text("Append Number & Symbol", style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = passphraseWithExtras, onCheckedChange = null)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AuditSectionContent(
    auditReport: PasswordAuditReport,
    onFixWithGeneratedPassword: (SavedPasswordEntity) -> Unit,
    onInspectItem: (SavedPasswordEntity) -> Unit
) {
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .testTag("password_audit_section")
        ) {
            item(key = "audit_overview_card") {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                shape = MaterialShapes.Sunny.toShape(),
                                color = if (auditReport.overallScore >= 75) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.errorContainer
                                },
                                modifier = Modifier.size(62.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${auditReport.overallScore}%",
                                        style = EmphasizedTypography.activeTabTitle,
                                        color = if (auditReport.overallScore >= 75) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onErrorContainer
                                        }
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.passwords_audit_title),
                                    style = EmphasizedTypography.headlineHero.copy(
                                        fontSize = MaterialTheme.typography.titleLarge.fontSize
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = when {
                                        auditReport.totalCount == 0 -> "Add logins to run a real-time vault audit"
                                        auditReport.weakItems.isEmpty() && auditReport.reusedGroups.isEmpty() ->
                                            "Zero weak or reused passwords detected"
                                        else -> "Review flagged logins below and rotate with generated keys"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AuditStatTile(
                                label = "Strong",
                                value = auditReport.strongCount.toString(),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            AuditStatTile(
                                label = "Weak",
                                value = auditReport.weakItems.size.toString(),
                                color = if (auditReport.weakItems.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            AuditStatTile(
                                label = "Reused",
                                value = auditReport.reusedCount.toString(),
                                color = if (auditReport.reusedCount > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            if (auditReport.weakItems.isNotEmpty()) {
                item(key = "audit_weak_items") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        MaterialGroupSectionHeader(
                            icon = Icons.Outlined.WarningAmber,
                            title = "Weak or Short Passwords"
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(GroupedListSpacing)) {
                            auditReport.weakItems.forEachIndexed { idx, item ->
                                MaterialGroupListItem(
                                    index = idx,
                                    totalCount = auditReport.weakItems.size,
                                    title = item.siteName,
                                    subtitle = "${item.username} • Strength ${item.strengthScore}/100",
                                    onClick = { onInspectItem(item) },
                                    trailingContent = {
                                        FilledTonalButton(onClick = { onFixWithGeneratedPassword(item) }) {
                                            Text("Fix")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (auditReport.reusedGroups.isNotEmpty()) {
                item(key = "audit_reused_items") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        MaterialGroupSectionHeader(
                            icon = Icons.Outlined.WarningAmber,
                            title = "Reused Passwords"
                        )
                        val flattened = auditReport.reusedGroups.flatten()
                        Column(verticalArrangement = Arrangement.spacedBy(GroupedListSpacing)) {
                            flattened.forEachIndexed { idx, item ->
                                MaterialGroupListItem(
                                    index = idx,
                                    totalCount = flattened.size,
                                    title = item.siteName,
                                    subtitle = "${item.username} • Shared across accounts",
                                    onClick = { onInspectItem(item) },
                                    trailingContent = {
                                        FilledTonalButton(onClick = { onFixWithGeneratedPassword(item) }) {
                                            Text("Rotate")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (auditReport.totalCount > 0 && auditReport.weakItems.isEmpty() && auditReport.reusedGroups.isEmpty()) {
                item(key = "audit_all_clean") {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(18.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    text = "All Credentials Passing",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Every stored password is unique and meets high-entropy security criteria.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun AuditStatTile(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp)
        ) {
            Text(
                text = value,
                style = EmphasizedTypography.headlineHero.copy(
                    fontSize = MaterialTheme.typography.titleLarge.fontSize
                ),
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun Phase4BCredentialVaultCard(
    entries: List<CredentialVaultEntity>,
    vaultStatus: VaultStatus,
    vaultStatusMessage: String?,
    revealedCredential: RevealedCredentialDetails?,
    onUnlockVault: () -> Unit,
    onLockVault: () -> Unit,
    onAddCredential: (String, String, String, String) -> Unit,
    onUpdateCredential: (String, String, String, String, String) -> Unit,
    onRevealCredential: (String) -> Unit,
    onHideRevealedCredential: () -> Unit,
    onDeleteCredential: (String) -> Unit,
    onResetInvalidatedVault: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<CredentialVaultEntity?>(null) }
    var deletingEntry by remember { mutableStateOf<CredentialVaultEntity?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }

    if (showAddDialog) {
        Phase4BCredentialEditorDialog(
            editingEntry = null,
            onSave = { originUrl, label, username, password ->
                onAddCredential(originUrl, label, username, password)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    editingEntry?.let { entry ->
        Phase4BCredentialEditorDialog(
            editingEntry = entry,
            onSave = { originUrl, label, username, password ->
                onUpdateCredential(entry.credentialId, originUrl, label, username, password)
                editingEntry = null
            },
            onDismiss = { editingEntry = null }
        )
    }

    deletingEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { deletingEntry = null },
            title = { Text("Delete Automation Credential?") },
            text = {
                Text(
                    "Remove \"${entry.label}\" (${entry.usernameIdentifier}) for ${entry.displayOrigin}? " +
                        "Any automation workflows referencing this credential will be blocked until reassigned."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteCredential(entry.credentialId)
                        deletingEntry = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("vault_confirm_delete_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingEntry = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset Invalidated Credential Vault?") },
            text = {
                Text(
                    "The Android Keystore encryption key was invalidated or became unavailable. " +
                        "Resetting the vault deletes all unreadable encrypted records and generates a fresh key."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirm = false
                        onResetInvalidatedVault()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("vault_confirm_reset_button")
                ) {
                    Text("Reset Vault")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("phase4b_credential_vault_card")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Automation Credential Vault",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Strict origin-bound AES-256-GCM credentials for authorized workflow replay",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = when (vaultStatus) {
                        VaultStatus.UNLOCKED -> MaterialTheme.colorScheme.primaryContainer
                        VaultStatus.LOCKED, VaultStatus.AUTH_REQUIRED -> MaterialTheme.colorScheme.secondaryContainer
                        VaultStatus.KEY_INVALIDATED, VaultStatus.UNAVAILABLE -> MaterialTheme.colorScheme.errorContainer
                    },
                    modifier = Modifier.testTag("vault_status_badge")
                ) {
                    Text(
                        text = when (vaultStatus) {
                            VaultStatus.UNLOCKED -> "Unlocked"
                            VaultStatus.LOCKED -> "Locked"
                            VaultStatus.AUTH_REQUIRED -> "Auth Required"
                            VaultStatus.KEY_INVALIDATED -> "Key Invalidated"
                            VaultStatus.UNAVAILABLE -> "Unavailable"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when (vaultStatus) {
                            VaultStatus.UNLOCKED -> MaterialTheme.colorScheme.onPrimaryContainer
                            VaultStatus.LOCKED, VaultStatus.AUTH_REQUIRED -> MaterialTheme.colorScheme.onSecondaryContainer
                            VaultStatus.KEY_INVALIDATED, VaultStatus.UNAVAILABLE -> MaterialTheme.colorScheme.onErrorContainer
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            if (!vaultStatusMessage.isNullOrBlank()) {
                Text(
                    text = vaultStatusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (vaultStatus == VaultStatus.KEY_INVALIDATED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilledTonalButton(
                    onClick = {
                        if (vaultStatus == VaultStatus.UNLOCKED) onLockVault() else onUnlockVault()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("vault_lock_toggle_button")
                ) {
                    Icon(
                        imageVector = if (vaultStatus == VaultStatus.UNLOCKED) Icons.Outlined.Lock else Icons.Filled.Key,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (vaultStatus == VaultStatus.UNLOCKED) "Lock Vault" else "Authenticate & Unlock")
                }

                Button(
                    onClick = { showAddDialog = true },
                    enabled = vaultStatus != VaultStatus.KEY_INVALIDATED,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("vault_add_credential_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save Credential")
                }
            }

            if (vaultStatus == VaultStatus.KEY_INVALIDATED) {
                Button(
                    onClick = { showResetConfirm = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_reset_invalidated_button")
                ) {
                    Text("Reset Invalidated Vault")
                }
            }

            if (entries.isEmpty()) {
                Text(
                    text = "No automation credentials saved yet. Save a credential here to authorize login steps in recorded workflows.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    entries.forEach { entry ->
                        val isRevealed = revealedCredential?.credentialId == entry.credentialId
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("vault_entry_row_${entry.credentialId}")
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${entry.displayOrigin} • ${entry.usernameIdentifier}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = if (isRevealed) {
                                                "Password: ${revealedCredential?.password ?: ""}"
                                            } else {
                                                "Password: ••••••••••••"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = JetBrainsMonoFamily,
                                            color = if (isRevealed) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.testTag("vault_entry_password_${entry.credentialId}")
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = {
                                                if (isRevealed) {
                                                    onHideRevealedCredential()
                                                } else {
                                                    onRevealCredential(entry.credentialId)
                                                }
                                            },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("vault_reveal_button_${entry.credentialId}")
                                        ) {
                                            Icon(
                                                imageVector = if (isRevealed) {
                                                    Icons.Outlined.VisibilityOff
                                                } else {
                                                    Icons.Outlined.Visibility
                                                },
                                                contentDescription = if (isRevealed) "Hide password" else "Reveal password",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { editingEntry = entry },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("vault_edit_button_${entry.credentialId}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.AutoFixHigh,
                                                contentDescription = "Edit credential",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { deletingEntry = entry },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("vault_delete_button_${entry.credentialId}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = "Delete credential",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
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
    }
}

@Composable
private fun Phase4BCredentialEditorDialog(
    editingEntry: CredentialVaultEntity?,
    onSave: (String, String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var originUrl by rememberSaveable(editingEntry?.credentialId) {
        mutableStateOf(editingEntry?.displayOrigin ?: "https://")
    }
    var label by rememberSaveable(editingEntry?.credentialId) {
        mutableStateOf(editingEntry?.label ?: "")
    }
    var username by rememberSaveable(editingEntry?.credentialId) {
        mutableStateOf(editingEntry?.usernameIdentifier ?: "")
    }
    var password by rememberSaveable(editingEntry?.credentialId) {
        mutableStateOf("")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (editingEntry == null) "Save Automation Credential" else "Update Automation Credential")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Credential Label (e.g. Work Portal)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_dialog_label_input")
                )
                androidx.compose.material3.OutlinedTextField(
                    value = originUrl,
                    onValueChange = { originUrl = it },
                    label = { Text("Website Origin (http:// or https://)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_dialog_origin_input")
                )
                androidx.compose.material3.OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username or Email") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_dialog_username_input")
                )
                androidx.compose.material3.OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_dialog_password_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(originUrl, label, username, password) },
                enabled = originUrl.isNotBlank() && username.isNotBlank() && password.isNotEmpty(),
                modifier = Modifier.testTag("vault_dialog_save_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

