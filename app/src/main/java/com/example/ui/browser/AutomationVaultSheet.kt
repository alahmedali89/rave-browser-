package com.example.ui.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wc
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DriveFolderUpload
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.R
import com.example.automation.AutomationEngine
import com.example.automation.AutomationVaultCategory
import com.example.automation.AutomationVaultManager
import com.example.automation.RecordingVaultCandidate
import com.example.automation.VaultExhaustionPromptState
import com.example.automation.VaultFileImportCandidate
import com.example.automation.VaultFolderAnalysis
import com.example.automation.VaultFolderFilePayload
import com.example.automation.VaultGeneratorType
import com.example.automation.VaultValueGenerator
import com.example.data.local.AutomationVaultEntryEntity
import com.example.data.local.AutomationVaultFileEntity
import com.example.data.local.AutomationVaultFolderEntity
import com.example.engine.DownloadStorageHelper
import com.example.ui.theme.EmphasizedTypography
import java.io.File
import kotlin.math.absoluteValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun vaultCategoryIcon(category: AutomationVaultCategory): ImageVector = when (category) {
    AutomationVaultCategory.PASSWORD -> Icons.Filled.Key
    AutomationVaultCategory.EMAIL -> Icons.Filled.Email
    AutomationVaultCategory.PHONE -> Icons.Filled.Phone
    AutomationVaultCategory.COUNTRY_CODE -> Icons.Filled.Public
    AutomationVaultCategory.DATE_OF_BIRTH -> Icons.Filled.Cake
    AutomationVaultCategory.GENDER -> Icons.Filled.Wc
    AutomationVaultCategory.USERNAME -> Icons.Filled.Person
    AutomationVaultCategory.NAME -> Icons.Filled.Badge
    AutomationVaultCategory.ADDRESS -> Icons.Filled.Place
    AutomationVaultCategory.CUSTOM -> Icons.Filled.Tune
}

internal fun vaultFolderIcon(folder: AutomationVaultFolderEntity): ImageVector {
    if (!folder.isDefault) return Icons.Filled.Folder
    return when (folder.folderId) {
        AutomationVaultManager.FOLDER_ID_PASSWORDS -> Icons.Filled.Key
        AutomationVaultManager.FOLDER_ID_EMAILS -> Icons.Filled.Email
        AutomationVaultManager.FOLDER_ID_PHONE -> Icons.Filled.Phone
        AutomationVaultManager.FOLDER_ID_COUNTRY_CODE -> Icons.Filled.Public
        AutomationVaultManager.FOLDER_ID_DATE_OF_BIRTH -> Icons.Filled.Cake
        AutomationVaultManager.FOLDER_ID_GENDER -> Icons.Filled.Wc
        AutomationVaultManager.FOLDER_ID_USERNAMES -> Icons.Filled.Person
        AutomationVaultManager.FOLDER_ID_NAMES -> Icons.Filled.Badge
        AutomationVaultManager.FOLDER_ID_ADDRESSES -> Icons.Filled.Place
        AutomationVaultManager.DEFAULT_CUSTOM_FOLDER_ID -> Icons.Filled.Tune
        else -> Icons.Filled.Folder
    }
}

private fun vaultFileIcon(file: AutomationVaultFileEntity): ImageVector {
    val mime = file.mimeType.lowercase()
    val name = file.fileName.lowercase()
    return when {
        mime.startsWith("image/") || name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp") ->
            Icons.Outlined.Image
        mime == "application/pdf" || name.endsWith(".pdf") ->
            Icons.Outlined.PictureAsPdf
        mime.contains("csv") || name.endsWith(".csv") || name.endsWith(".tsv") ->
            Icons.Outlined.TableChart
        AutomationVaultManager.isTextLikeFile(file.fileName, file.mimeType) ->
            Icons.Outlined.Description
        else -> Icons.AutoMirrored.Outlined.InsertDriveFile
    }
}

private fun formatFileSizeBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(java.util.Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(java.util.Locale.US, "%.1f MB", mb)
}

private data class FolderThemeColors(
    val tabColor: Color,
    val bodyColor: Color,
    val iconTint: Color,
    val borderColor: Color
)

@Composable
private fun rememberFolderThemeColors(folder: AutomationVaultFolderEntity): FolderThemeColors {
    val scheme = MaterialTheme.colorScheme
    val toneIndex = when (folder.folderId) {
        AutomationVaultManager.FOLDER_ID_PASSWORDS -> 0
        AutomationVaultManager.FOLDER_ID_EMAILS -> 1
        AutomationVaultManager.FOLDER_ID_PHONE -> 2
        AutomationVaultManager.FOLDER_ID_COUNTRY_CODE -> 0
        AutomationVaultManager.FOLDER_ID_DATE_OF_BIRTH -> 1
        AutomationVaultManager.FOLDER_ID_GENDER -> 2
        AutomationVaultManager.FOLDER_ID_USERNAMES -> 0
        AutomationVaultManager.FOLDER_ID_NAMES -> 1
        AutomationVaultManager.FOLDER_ID_ADDRESSES -> 2
        AutomationVaultManager.DEFAULT_CUSTOM_FOLDER_ID -> 3
        else -> (folder.folderId.hashCode().absoluteValue % 3)
    }
    return when (toneIndex) {
        0 -> FolderThemeColors(
            tabColor = scheme.primary,
            bodyColor = scheme.primaryContainer,
            iconTint = scheme.onPrimaryContainer,
            borderColor = scheme.primary.copy(alpha = 0.28f)
        )
        1 -> FolderThemeColors(
            tabColor = scheme.secondary,
            bodyColor = scheme.secondaryContainer,
            iconTint = scheme.onSecondaryContainer,
            borderColor = scheme.secondary.copy(alpha = 0.28f)
        )
        2 -> FolderThemeColors(
            tabColor = scheme.tertiary,
            bodyColor = scheme.tertiaryContainer,
            iconTint = scheme.onTertiaryContainer,
            borderColor = scheme.tertiary.copy(alpha = 0.28f)
        )
        else -> FolderThemeColors(
            tabColor = scheme.primary.copy(alpha = 0.85f),
            bodyColor = scheme.surfaceContainerHighest,
            iconTint = scheme.primary,
            borderColor = scheme.outlineVariant
        )
    }
}

@Composable
private fun VaultThemeFolderGraphic(
    folder: AutomationVaultFolderEntity,
    modifier: Modifier = Modifier
) {
    val colors = rememberFolderThemeColors(folder)
    val icon = vaultFolderIcon(folder)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(width = 76.dp, height = 62.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val tabHeight = h * 0.22f
            val tabWidth = w * 0.44f
            val bodyTop = h * 0.14f
            val radius = 10.dp.toPx()

            // Back folder plate with top-left tab
            val tabPath = Path().apply {
                moveTo(0f, bodyTop + radius)
                lineTo(0f, radius * 0.65f)
                quadraticTo(0f, 0f, radius * 0.65f, 0f)
                lineTo(tabWidth - radius * 0.45f, 0f)
                quadraticTo(tabWidth, 0f, tabWidth + radius * 0.4f, bodyTop * 0.55f)
                lineTo(tabWidth + radius * 0.85f, bodyTop)
                lineTo(w - radius, bodyTop)
                quadraticTo(w, bodyTop, w, bodyTop + radius)
                lineTo(w, h * 0.5f)
                lineTo(0f, h * 0.5f)
                close()
            }
            drawPath(path = tabPath, color = colors.tabColor)

            // Front rounded folder body
            drawRoundRect(
                color = colors.bodyColor,
                topLeft = Offset(0f, bodyTop + tabHeight * 0.22f),
                size = Size(w, h - (bodyTop + tabHeight * 0.22f)),
                cornerRadius = CornerRadius(radius, radius)
            )
            // Subtle top crease line on front pocket
            drawLine(
                color = colors.tabColor.copy(alpha = 0.28f),
                start = Offset(radius * 0.5f, bodyTop + tabHeight * 0.22f + 1.dp.toPx()),
                end = Offset(w - radius * 0.5f, bodyTop + tabHeight * 0.22f + 1.dp.toPx()),
                strokeWidth = 1.dp.toPx()
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.iconTint,
            modifier = Modifier
                .padding(top = 10.dp)
                .size(26.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationVaultScreenOverlay(
    entries: List<AutomationVaultEntryEntity>,
    revealedValues: Map<String, String>,
    activeTabUrl: String,
    onSaveEntry: (
        entryId: String?,
        category: AutomationVaultCategory,
        label: String,
        value: String,
        websiteOrigin: String,
        isSensitive: Boolean
    ) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onRevealEntry: (String) -> Unit,
    onHideEntry: (String) -> Unit,
    onDismiss: () -> Unit,
    backHandlerEnabled: Boolean = true,
    onImportFileCandidates: (List<VaultFileImportCandidate>) -> Unit = {},
    folders: List<AutomationVaultFolderEntity> = AutomationVaultManager.DEFAULT_FOLDERS,
    files: List<AutomationVaultFileEntity> = emptyList(),
    onSaveEntryInFolder: ((
        entryId: String?,
        category: AutomationVaultCategory,
        label: String,
        value: String,
        websiteOrigin: String,
        isSensitive: Boolean,
        folderId: String
    ) -> Unit)? = null,
    onImportFileCandidatesToFolder: ((List<VaultFileImportCandidate>, String) -> Unit)? = null,
    onCreateFolder: (String) -> Unit = {},
    onRenameFolder: (String, String) -> Unit = { _, _ -> },
    onDeleteFolder: (String) -> Unit = {},
    onUploadFileToFolder: (
        folderId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ) -> Unit = { _, _, _, _ -> },
    onUploadDirectoryFolder: (
        folderName: String,
        files: List<VaultFolderFilePayload>
    ) -> Unit = { _, _ -> },
    onDeleteFile: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    initialFolderId: String? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var openedFolderId by rememberSaveable { mutableStateOf<String?>(initialFolderId?.takeIf { it.isNotBlank() }) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isFabMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<AutomationVaultEntryEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showUploadDialog by remember { mutableStateOf(false) }
    var uploadPrefillContent by remember { mutableStateOf("") }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var renamingFolder by remember { mutableStateOf<AutomationVaultFolderEntity?>(null) }
    var deletingFolder by remember { mutableStateOf<AutomationVaultFolderEntity?>(null) }
    var previewingFile by remember { mutableStateOf<AutomationVaultFileEntity?>(null) }
    var generatorOverrideByFolder by remember { mutableStateOf<Map<String, VaultGeneratorType>>(emptyMap()) }
    var lastGeneratedByFolder by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(initialFolderId) {
        if (!initialFolderId.isNullOrBlank()) {
            openedFolderId = initialFolderId
        }
    }

    val availableFolders = remember(folders) {
        if (folders.isEmpty()) AutomationVaultManager.DEFAULT_FOLDERS else folders
    }
    val openedFolder = remember(openedFolderId, availableFolders) {
        openedFolderId?.let { id -> availableFolders.firstOrNull { it.folderId == id } }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(openedFolderId) {
        isFabMenuExpanded = false
        listState.scrollToItem(0)
    }
    LaunchedEffect(entries) {
        entries.forEach { entry ->
            if (entry.isSensitive && !revealedValues.containsKey(entry.entryId)) {
                onRevealEntry(entry.entryId)
            }
        }
    }

    BackHandler(enabled = backHandlerEnabled) {
        when {
            isFabMenuExpanded -> isFabMenuExpanded = false
            openedFolder != null -> {
                openedFolderId = null
                searchQuery = ""
            }
            else -> onDismiss()
        }
    }

    val activeOrigin = remember(activeTabUrl) {
        if (activeTabUrl.startsWith("http", ignoreCase = true)) {
            AutomationEngine().parseOrigin(activeTabUrl).canonicalOrigin
        } else {
            ""
        }
    }

    val folderFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val targetFolder = openedFolder
        if (uris.isNotEmpty() && targetFolder != null) {
            coroutineScope.launch {
                val payloads = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri ->
                        runCatching {
                            val resolvedName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                if (nameIdx >= 0 && cursor.moveToFirst()) cursor.getString(nameIdx) else ""
                            }.orEmpty().ifBlank { uri.lastPathSegment ?: "uploaded_file" }
                            val resolvedMime = context.contentResolver.getType(uri).orEmpty()
                                .ifBlank { "application/octet-stream" }
                            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                ?: error("Unable to read selected file.")
                            VaultFolderFilePayload(
                                fileName = resolvedName,
                                mimeType = resolvedMime,
                                bytes = bytes
                            )
                        }.getOrNull()
                    }
                }
                if (payloads.isEmpty()) {
                    Toast.makeText(
                        context,
                        "Could not read the selected file(s).",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    payloads.forEach { file ->
                        onUploadFileToFolder(
                            targetFolder.folderId,
                            file.fileName,
                            file.mimeType,
                            file.bytes
                        )
                    }
                }
            }
        }
    }

    val uploadFolderTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            coroutineScope.launch {
                runCatching {
                    readSafDirectoryFolder(context, treeUri)
                }.onSuccess { (folderName, collectedFiles) ->
                    onUploadDirectoryFolder(folderName, collectedFiles)
                }.onFailure { err ->
                    Toast.makeText(
                        context,
                        err.message ?: "Could not upload selected folder.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    if (showCreateFolderDialog) {
        CreateOrRenameVaultFolderDialog(
            title = stringResource(R.string.automation_vault_create_folder_title),
            initialName = "",
            confirmLabel = "Create",
            onConfirm = { newName ->
                onCreateFolder(newName)
                showCreateFolderDialog = false
            },
            onDismiss = { showCreateFolderDialog = false }
        )
    }

    renamingFolder?.let { folder ->
        CreateOrRenameVaultFolderDialog(
            title = stringResource(R.string.automation_vault_rename_folder_title),
            initialName = folder.name,
            confirmLabel = "Save",
            onConfirm = { newName ->
                onRenameFolder(folder.folderId, newName)
                renamingFolder = null
            },
            onDismiss = { renamingFolder = null }
        )
    }

    deletingFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { deletingFolder = null },
            title = { Text("Delete '${folder.name}'?") },
            text = {
                Text("This removes the folder along with any uploaded files and saved values inside it.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteFolder(folder.folderId)
                        if (openedFolderId == folder.folderId) {
                            openedFolderId = null
                        }
                        deletingFolder = null
                    },
                    modifier = Modifier.testTag("vault_confirm_delete_folder_button")
                ) {
                    Text(stringResource(R.string.automation_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingFolder = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    previewingFile?.let { file ->
        VaultFilePreviewDialog(
            file = file,
            onOpenExternal = { openVaultFileWithSystemViewer(context, file) },
            onExtractValues = if (file.textPreview.isNotBlank()) {
                {
                    uploadPrefillContent = file.textPreview
                    previewingFile = null
                    showUploadDialog = true
                }
            } else {
                null
            },
            onDismiss = { previewingFile = null }
        )
    }

    if (showAddDialog) {
        val activeFolder = openedFolder
        val defaultCat = activeFolder?.let {
            val mapped = AutomationVaultCategory.fromId(it.categoryId)
            if (it.isDefault || mapped != AutomationVaultCategory.CUSTOM) mapped
            else AutomationVaultManager.detectCategory(it.name, it.name)
        } ?: AutomationVaultCategory.PASSWORD
        AddEditAutomationVaultDialog(
            initialEntry = null,
            initialRevealedValue = "",
            defaultOriginUrl = activeOrigin,
            defaultCategory = defaultCat,
            folderName = activeFolder?.name,
            onSave = { cat, label, value, origin, sensitive ->
                val targetId = activeFolder?.folderId
                if (targetId != null && onSaveEntryInFolder != null) {
                    onSaveEntryInFolder(null, cat, label, value, origin, sensitive, targetId)
                } else {
                    onSaveEntry(null, cat, label, value, origin, sensitive)
                }
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    if (showUploadDialog) {
        UploadVaultFileDialog(
            existingEntries = entries,
            defaultOriginUrl = activeOrigin,
            initialRawContent = uploadPrefillContent,
            targetFolderName = openedFolder?.name,
            onSaveRawFile = openedFolder?.let { folder ->
                { fileName, mimeType, bytes ->
                    onUploadFileToFolder(folder.folderId, fileName, mimeType, bytes)
                }
            },
            onImportCandidates = { selectedCandidates ->
                val targetId = openedFolder?.folderId
                if (targetId != null && onImportFileCandidatesToFolder != null) {
                    onImportFileCandidatesToFolder(selectedCandidates, targetId)
                } else {
                    onImportFileCandidates(selectedCandidates)
                }
                showUploadDialog = false
                uploadPrefillContent = ""
            },
            onDismiss = {
                showUploadDialog = false
                uploadPrefillContent = ""
            }
        )
    }

    editingEntry?.let { entry ->
        val existingPlain = revealedValues[entry.entryId] ?: if (!entry.isSensitive) entry.maskedPreview else ""
        val entryFolder = availableFolders.firstOrNull {
            it.folderId == AutomationVaultManager.resolveEffectiveFolderId(entry)
        } ?: openedFolder
        AddEditAutomationVaultDialog(
            initialEntry = entry,
            initialRevealedValue = existingPlain,
            defaultOriginUrl = entry.websiteOrigin.ifBlank { activeOrigin },
            folderName = entryFolder?.name,
            onSave = { cat, label, value, origin, sensitive ->
                val targetId = entry.folderId.ifBlank { entryFolder?.folderId.orEmpty() }
                if (targetId.isNotBlank() && onSaveEntryInFolder != null) {
                    onSaveEntryInFolder(entry.entryId, cat, label, value, origin, sensitive, targetId)
                } else {
                    onSaveEntry(entry.entryId, cat, label, value, origin, sensitive)
                }
                editingEntry = null
            },
            onDismiss = { editingEntry = null }
        )
    }

    val normalizedQuery = searchQuery.trim().lowercase()
    val entriesByFolder = remember(entries) {
        entries.groupBy { AutomationVaultManager.resolveEffectiveFolderId(it) }
    }
    val filesByFolder = remember(files) {
        files.groupBy { it.folderId }
    }

    val visibleFolders = remember(availableFolders, entriesByFolder, filesByFolder, normalizedQuery) {
        if (normalizedQuery.isEmpty()) {
            availableFolders
        } else {
            availableFolders.filter { folder ->
                folder.name.lowercase().contains(normalizedQuery) ||
                    entriesByFolder[folder.folderId].orEmpty().any { entryMatchesQuery(it, normalizedQuery) } ||
                    filesByFolder[folder.folderId].orEmpty().any { it.fileName.lowercase().contains(normalizedQuery) }
            }
        }
    }

    val currentFolderFiles = remember(openedFolder, filesByFolder, normalizedQuery) {
        val list = openedFolder?.let { filesByFolder[it.folderId].orEmpty() }.orEmpty()
        if (normalizedQuery.isEmpty()) list else list.filter { it.fileName.lowercase().contains(normalizedQuery) }
    }

    val currentFolderEntries = remember(openedFolder, entriesByFolder, normalizedQuery) {
        val list = openedFolder?.let { entriesByFolder[it.folderId].orEmpty() }.orEmpty()
        if (normalizedQuery.isEmpty()) list else list.filter { entryMatchesQuery(it, normalizedQuery) }
    }

    val globalSearchEntries = remember(entries, normalizedQuery, openedFolder) {
        if (openedFolder != null || normalizedQuery.isEmpty()) emptyList()
        else entries.filter { entryMatchesQuery(it, normalizedQuery) }
    }
    val globalSearchFiles = remember(files, normalizedQuery, openedFolder) {
        if (openedFolder != null || normalizedQuery.isEmpty()) emptyList()
        else files.filter { it.fileName.lowercase().contains(normalizedQuery) }
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
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (openedFolder != null) {
                                openedFolderId = null
                                searchQuery = ""
                            } else {
                                onDismiss()
                            }
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("automation_vault_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                },
                title = {
                    val activeFolder = openedFolder
                    if (activeFolder == null) {
                        Text(
                            text = stringResource(R.string.automation_vault_title),
                            style = EmphasizedTypography.headlineHero,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("automation_vault_topbar_title")
                        )
                    } else {
                        val folderAnalysis = remember(activeFolder.folderId, activeFolder.name, activeFolder.categoryId) {
                            VaultValueGenerator.analyzeFolder(activeFolder)
                        }
                        val selectedOverride = generatorOverrideByFolder[activeFolder.folderId]
                        val effectiveAnalysis = remember(folderAnalysis, selectedOverride) {
                            if (selectedOverride != null && selectedOverride != folderAnalysis.type) {
                                folderAnalysis.copy(
                                    type = selectedOverride,
                                    category = selectedOverride.category,
                                    badgeLabel = selectedOverride.displayName
                                )
                            } else {
                                folderAnalysis
                            }
                        }
                        var showTypeMenu by remember { mutableStateOf(false) }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = activeFolder.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("automation_vault_topbar_title")
                            )
                            Box {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { showTypeMenu = true }
                                        .testTag("vault_folder_generator_type_label")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        modifier = Modifier.padding(start = 6.dp, end = 2.dp, top = 1.dp, bottom = 1.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text = effectiveAnalysis.badgeLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Icon(
                                            imageVector = Icons.Filled.ArrowDropDown,
                                            contentDescription = stringResource(R.string.automation_vault_change_generator_type),
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = showTypeMenu,
                                    onDismissRequest = { showTypeMenu = false }
                                ) {
                                    VaultGeneratorType.entries.forEach { preset ->
                                        DropdownMenuItem(
                                            text = { Text(preset.displayName) },
                                            onClick = {
                                                showTypeMenu = false
                                                generatorOverrideByFolder = generatorOverrideByFolder + (activeFolder.folderId to preset)
                                            },
                                            modifier = Modifier.testTag("vault_generator_preset_${preset.id.lowercase()}")
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                actions = {}
            )
        },
        floatingActionButton = {
            val fabRotation by animateFloatAsState(
                targetValue = if (isFabMenuExpanded) 45f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "vault_fab_rotation"
            )
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AnimatedVisibility(
                    visible = isFabMenuExpanded,
                    enter = fadeIn() + scaleIn(initialScale = 0.88f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f)),
                    exit = fadeOut() + scaleOut(targetScale = 0.88f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f))
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (openedFolder == null) {
                            VaultFabMenuPill(
                                icon = Icons.Outlined.DriveFolderUpload,
                                label = stringResource(R.string.automation_vault_fab_upload),
                                subtitle = stringResource(R.string.automation_vault_fab_upload_subtitle),
                                onClick = {
                                    isFabMenuExpanded = false
                                    uploadFolderTreeLauncher.launch(null)
                                },
                                modifier = Modifier.testTag("vault_fab_upload_folder")
                            )
                            VaultFabMenuPill(
                                icon = Icons.Filled.CreateNewFolder,
                                label = stringResource(R.string.automation_vault_fab_add),
                                subtitle = stringResource(R.string.automation_vault_fab_add_subtitle),
                                onClick = {
                                    isFabMenuExpanded = false
                                    showCreateFolderDialog = true
                                },
                                modifier = Modifier.testTag("vault_fab_add_folder")
                            )
                        } else {
                            VaultFabMenuPill(
                                icon = Icons.Outlined.UploadFile,
                                label = stringResource(R.string.automation_vault_upload),
                                onClick = {
                                    isFabMenuExpanded = false
                                    folderFilePickerLauncher.launch(arrayOf("*/*"))
                                },
                                modifier = Modifier.testTag("vault_fab_upload_files")
                            )
                            VaultFabMenuPill(
                                icon = Icons.Filled.Add,
                                label = stringResource(R.string.automation_vault_add),
                                onClick = {
                                    isFabMenuExpanded = false
                                    showAddDialog = true
                                },
                                modifier = Modifier.testTag("vault_fab_add_value")
                            )
                        }
                    }
                }
                FloatingActionButton(
                    onClick = { isFabMenuExpanded = !isFabMenuExpanded },
                    containerColor = if (isFabMenuExpanded) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    contentColor = if (isFabMenuExpanded) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    modifier = Modifier.testTag("automation_vault_fab")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = if (openedFolder == null) {
                            "Folder actions"
                        } else {
                            "Folder item actions"
                        },
                        modifier = Modifier.rotate(fabRotation)
                    )
                }
            }
        },
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .testTag("automation_vault_screen")
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
            ) {
                val folderColumns = if (maxWidth >= 560.dp) 4 else 3
                val folderRows = remember(visibleFolders, folderColumns) {
                    visibleFolders.chunked(folderColumns)
                }

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(if (openedFolder == null) 12.dp else 0.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("automation_vault_list")
                ) {
                    item(key = "vault_header_search_bar") {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(bottom = if (openedFolder == null) 0.dp else 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = {
                                        Text(
                                            text = stringResource(R.string.automation_vault_search_hint),
                                            maxLines = 1,
                                            softWrap = false,
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
                                    maxLines = 1,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("automation_vault_search_input")
                                )

                                val folderNow = openedFolder
                                if (folderNow != null) {
                                    val folderAnalysisNow = remember(folderNow.folderId, folderNow.name, folderNow.categoryId) {
                                        VaultValueGenerator.analyzeFolder(folderNow)
                                    }
                                    val selectedOverrideNow = generatorOverrideByFolder[folderNow.folderId]
                                    val folderEntriesNow = entriesByFolder[folderNow.folderId].orEmpty()
                                    val existingPlainNow = remember(folderEntriesNow, revealedValues) {
                                        folderEntriesNow.mapNotNull { entry ->
                                            revealedValues[entry.entryId] ?: if (!entry.isSensitive) entry.maskedPreview else null
                                        }.toSet()
                                    }
                                    Button(
                                        onClick = {
                                            val (usedAnalysis, generatedValue) = VaultValueGenerator.generateValue(
                                                folder = folderNow,
                                                overrideType = selectedOverrideNow,
                                                existingValues = existingPlainNow
                                            )
                                            lastGeneratedByFolder = lastGeneratedByFolder + (folderNow.folderId to generatedValue)
                                            if (onSaveEntryInFolder != null) {
                                                onSaveEntryInFolder(
                                                    null,
                                                    usedAnalysis.category,
                                                    folderNow.name,
                                                    generatedValue,
                                                    "",
                                                    usedAnalysis.category.isSensitiveDefault,
                                                    folderNow.folderId
                                                )
                                            } else {
                                                onSaveEntry(
                                                    null,
                                                    usedAnalysis.category,
                                                    folderNow.name,
                                                    generatedValue,
                                                    "",
                                                    usedAnalysis.category.isSensitiveDefault
                                                )
                                            }
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                                        modifier = Modifier
                                            .heightIn(min = 52.dp)
                                            .testTag("vault_folder_generate_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.AutoAwesome,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.automation_vault_generate_button),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            if (openedFolder == null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = stringResource(R.string.automation_vault_folders_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Text(
                                            text = "${visibleFolders.size}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (openedFolder == null) {
                        if (visibleFolders.isEmpty() && globalSearchEntries.isEmpty() && globalSearchFiles.isEmpty()) {
                            item(key = "vault_search_empty") {
                                Surface(
                                    shape = MaterialTheme.shapes.extraLarge,
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("automation_vault_empty_state")
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Text(
                                            text = "No matching folders or files",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = "Try a different search or create a new folder.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            itemsIndexed(
                                items = folderRows,
                                key = { rowIndex, rowItems ->
                                    "folder_row_${rowIndex}_${rowItems.firstOrNull()?.folderId.orEmpty()}"
                                }
                            ) { _, rowItems ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    rowItems.forEach { folder ->
                                        val entryCount = entriesByFolder[folder.folderId]?.size ?: 0
                                        val fileCount = filesByFolder[folder.folderId]?.size ?: 0
                                        VaultFolderTile(
                                            folder = folder,
                                            fileCount = fileCount,
                                            entryCount = entryCount,
                                            onClick = {
                                                openedFolderId = folder.folderId
                                                searchQuery = ""
                                            },
                                            onRename = if (!folder.isDefault) {
                                                { renamingFolder = folder }
                                            } else {
                                                null
                                            },
                                            onDelete = if (!folder.isDefault) {
                                                { deletingFolder = folder }
                                            } else {
                                                null
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    repeat(folderColumns - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }

                            if (globalSearchFiles.isNotEmpty()) {
                                item(key = "global_search_files_header") {
                                    Text(
                                        text = "Matching Files",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                itemsIndexed(
                                    items = globalSearchFiles,
                                    key = { _, file -> "global_file_${file.fileId}" }
                                ) { index, file ->
                                    VaultUploadedFileCard(
                                        file = file,
                                        shape = groupedListItemShape(index, globalSearchFiles.size),
                                        onPreviewOrOpen = { previewingFile = file },
                                        onExtractValues = if (file.textPreview.isNotBlank()) {
                                            {
                                                uploadPrefillContent = file.textPreview
                                                showUploadDialog = true
                                            }
                                        } else {
                                            null
                                        },
                                        onDelete = { onDeleteFile(file.fileId) }
                                    )
                                }
                            }

                            if (globalSearchEntries.isNotEmpty()) {
                                item(key = "global_search_entries_header") {
                                    Text(
                                        text = "Matching Values",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                itemsIndexed(
                                    items = globalSearchEntries,
                                    key = { _, entry -> "global_entry_${entry.entryId}" }
                                ) { index, entry ->
                                    VaultEntryCard(
                                        entry = entry,
                                        revealedPlain = revealedValues[entry.entryId],
                                        shape = groupedListItemShape(index, globalSearchEntries.size),
                                        onEdit = { editingEntry = entry },
                                        onDelete = { onDeleteEntry(entry.entryId) }
                                    )
                                }
                            }
                        }
                    } else {
                        val generatedPreview = lastGeneratedByFolder[openedFolder.folderId]
                        if (!generatedPreview.isNullOrBlank()) {
                            item(key = "folder_auto_generator_preview_${openedFolder.folderId}") {
                                VaultGeneratedValueBanner(
                                    folder = openedFolder,
                                    generatedValue = generatedPreview,
                                    onCopyGenerated = { valueToCopy ->
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        clipboard?.setPrimaryClip(ClipData.newPlainText(openedFolder.name, valueToCopy))
                                        Toast.makeText(
                                            context,
                                            "Copied generated ${openedFolder.name.lowercase()} value",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                        }

                        if (currentFolderFiles.isEmpty() && currentFolderEntries.isEmpty()) {
                            item(key = "folder_empty_state") {
                                Surface(
                                    shape = MaterialTheme.shapes.extraLarge,
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("automation_vault_folder_empty_state")
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.padding(24.dp)
                                    ) {
                                        VaultThemeFolderGraphic(folder = openedFolder)
                                        Text(
                                            text = stringResource(R.string.automation_vault_folder_empty_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = stringResource(R.string.automation_vault_folder_empty_subtitle),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            if (currentFolderFiles.isNotEmpty()) {
                                item(key = "folder_files_section_header") {
                                    MaterialGroupSectionHeader(
                                        title = "Uploaded Files (${currentFolderFiles.size})",
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }
                                itemsIndexed(
                                    items = currentFolderFiles,
                                    key = { _, file -> "file_${file.fileId}" }
                                ) { index, file ->
                                    val fileShape = groupedListItemShape(
                                        index = index,
                                        totalCount = currentFolderFiles.size
                                    )
                                    VaultUploadedFileCard(
                                        file = file,
                                        shape = fileShape,
                                        onPreviewOrOpen = { previewingFile = file },
                                        onExtractValues = if (file.textPreview.isNotBlank()) {
                                            {
                                                uploadPrefillContent = file.textPreview
                                                showUploadDialog = true
                                            }
                                        } else {
                                            null
                                        },
                                        onDelete = { onDeleteFile(file.fileId) },
                                        modifier = Modifier.padding(
                                            bottom = if (index < currentFolderFiles.lastIndex) GroupedListSpacing else 8.dp
                                        )
                                    )
                                }
                            }

                            if (currentFolderEntries.isNotEmpty()) {
                                item(key = "folder_entries_section_header") {
                                    MaterialGroupSectionHeader(
                                        title = "Saved Values (${currentFolderEntries.size})",
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }
                                itemsIndexed(
                                    items = currentFolderEntries,
                                    key = { _, entry -> entry.entryId }
                                ) { index, entry ->
                                    val entryShape = groupedListItemShape(
                                        index = index,
                                        totalCount = currentFolderEntries.size
                                    )
                                    VaultEntryCard(
                                        entry = entry,
                                        revealedPlain = revealedValues[entry.entryId],
                                        folderNameContext = openedFolder.name,
                                        shape = entryShape,
                                        onEdit = { editingEntry = entry },
                                        onDelete = { onDeleteEntry(entry.entryId) },
                                        modifier = Modifier.padding(
                                            bottom = if (index < currentFolderEntries.lastIndex) GroupedListSpacing else 0.dp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isFabMenuExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.18f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { isFabMenuExpanded = false }
                        )
                        .testTag("vault_fab_scrim")
                )
            }
        }
    }
}

@Composable
private fun VaultGeneratedValueBanner(
    folder: AutomationVaultFolderEntity,
    generatedValue: String,
    onCopyGenerated: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val folderColors = rememberFolderThemeColors(folder)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, folderColors.borderColor),
        modifier = modifier
            .fillMaxWidth()
            .testTag("vault_folder_generate_card")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = folderColors.iconTint,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = generatedValue,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .testTag("vault_folder_last_generated_value")
            )
            IconButton(
                onClick = { onCopyGenerated(generatedValue) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("vault_folder_copy_generated_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = stringResource(R.string.automation_vault_copy_generated),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun VaultFabMenuPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shadowElevation = 4.dp,
        tonalElevation = 4.dp,
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private suspend fun readSafDirectoryFolder(
    context: Context,
    treeUri: Uri
): Pair<String, List<VaultFolderFilePayload>> = withContext(Dispatchers.IO) {
    val resolver = context.applicationContext.contentResolver
    val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
    val rootDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)

    var rootName = resolver.query(
        rootDocUri,
        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
        null,
        null,
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }.orEmpty()

    if (rootName.isBlank()) {
        rootName = DownloadStorageHelper.extractFolderLabelFromUri(treeUri.toString())
            .ifBlank { "Uploaded Folder" }
    }

    val collected = mutableListOf<VaultFolderFilePayload>()
    val queue = ArrayDeque<String>()
    val visited = HashSet<String>()
    queue.add(treeDocId)
    visited.add(treeDocId)

    val maxFiles = 100
    val maxBytesPerFile = 25 * 1024 * 1024

    while (queue.isNotEmpty() && collected.size < maxFiles) {
        val currentDocId = queue.removeFirst()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, currentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            while (cursor.moveToNext() && collected.size < maxFiles) {
                val docId = if (idIdx >= 0) cursor.getString(idIdx).orEmpty() else ""
                if (docId.isBlank()) continue
                val displayName = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else ""
                val mimeType = if (mimeIdx >= 0) cursor.getString(mimeIdx).orEmpty() else ""
                val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L

                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (visited.add(docId)) {
                        queue.add(docId)
                    }
                } else {
                    if (size > maxBytesPerFile) continue
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    val bytes = runCatching {
                        resolver.openInputStream(fileUri)?.use { it.readBytes() }
                    }.getOrNull() ?: continue
                    if (bytes.size <= maxBytesPerFile) {
                        collected.add(
                            VaultFolderFilePayload(
                                fileName = displayName.ifBlank { docId.substringAfterLast('/') },
                                mimeType = mimeType.ifBlank { "application/octet-stream" },
                                bytes = bytes
                            )
                        )
                    }
                }
            }
        }
    }
    rootName to collected
}

private fun entryMatchesQuery(entry: AutomationVaultEntryEntity, normalizedQuery: String): Boolean {
    if (normalizedQuery.isEmpty()) return true
    return entry.label.lowercase().contains(normalizedQuery) ||
        entry.domain.lowercase().contains(normalizedQuery) ||
        entry.category.lowercase().contains(normalizedQuery) ||
        (!entry.isSensitive && entry.maskedPreview.lowercase().contains(normalizedQuery))
}

@Composable
private fun VaultFolderTile(
    folder: AutomationVaultFolderEntity,
    fileCount: Int,
    entryCount: Int,
    onClick: () -> Unit,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = rememberFolderThemeColors(folder)
    var showMenu by remember { mutableStateOf(false) }
    val countSummary = remember(fileCount, entryCount) {
        when {
            fileCount > 0 && entryCount > 0 -> "$fileCount files • $entryCount"
            fileCount > 0 -> "$fileCount file${if (fileCount == 1) "" else "s"}"
            entryCount > 0 -> "$entryCount value${if (entryCount == 1) "" else "s"}"
            else -> "Empty"
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.borderColor),
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .testTag("vault_folder_${folder.folderId}")
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 14.dp)
            ) {
                VaultThemeFolderGraphic(folder = folder)
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("vault_folder_name_${folder.folderId}")
                )
                Text(
                    text = countSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onRename != null || onDelete != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                ) {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("vault_folder_more_${folder.folderId}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Folder options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (onRename != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.automation_rename)) },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onRename()
                                },
                                modifier = Modifier.testTag("vault_folder_rename_${folder.folderId}")
                            )
                        }
                        if (onDelete != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.automation_delete),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                },
                                modifier = Modifier.testTag("vault_folder_delete_${folder.folderId}")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultUploadedFileCard(
    file: AutomationVaultFileEntity,
    shape: Shape = groupedListItemShape(0, 1),
    onPreviewOrOpen: () -> Unit,
    onExtractValues: (() -> Unit)?,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onPreviewOrOpen)
            .testTag("vault_file_${file.fileId}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = vaultFileIcon(file),
                        contentDescription = file.fileName,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = file.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val extLabel = file.fileName.substringAfterLast('.', "").uppercase().ifBlank { "FILE" }
                Text(
                    text = "$extLabel • ${formatFileSizeBytes(file.sizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onExtractValues != null) {
                IconButton(
                    onClick = onExtractValues,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("vault_file_extract_${file.fileId}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = "Extract values from file",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("vault_file_delete_${file.fileId}")
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = stringResource(R.string.automation_delete),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun VaultEntryCard(
    entry: AutomationVaultEntryEntity,
    revealedPlain: String?,
    folderNameContext: String? = null,
    shape: Shape = groupedListItemShape(0, 1),
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cat = AutomationVaultCategory.fromId(entry.category)
    val shownValue = revealedPlain ?: entry.maskedPreview
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onEdit)
            .testTag("automation_vault_entry_${entry.entryId}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = vaultCategoryIcon(cat),
                        contentDescription = folderNameContext ?: cat.label,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (folderNameContext == null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = cat.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = entry.domain.ifBlank { "Global" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = shownValue,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("automation_vault_value_${entry.entryId}")
                    )
                } else {
                    Text(
                        text = shownValue,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("automation_vault_value_${entry.entryId}")
                    )
                    val subtitleParts = buildList {
                        val cleanLabel = entry.label.trim()
                        if (cleanLabel.isNotEmpty() &&
                            !cleanLabel.equals(folderNameContext, ignoreCase = true) &&
                            !cleanLabel.equals(cat.label, ignoreCase = true)
                        ) {
                            add(cleanLabel)
                        }
                        val cleanDomain = entry.domain.trim()
                        if (cleanDomain.isNotEmpty() && !cleanDomain.equals("Global", ignoreCase = true)) {
                            add(cleanDomain)
                        }
                    }
                    if (subtitleParts.isNotEmpty()) {
                        Text(
                            text = subtitleParts.joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("automation_vault_delete_${entry.entryId}")
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = stringResource(R.string.automation_delete),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun CreateOrRenameVaultFolderDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var folderName by remember(initialName) { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = folderName,
                onValueChange = { folderName = it },
                label = { Text(stringResource(R.string.automation_vault_folder_name_label)) },
                placeholder = { Text("e.g. Work Credentials, Receipts") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vault_folder_name_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(folderName.trim()) },
                enabled = folderName.trim().isNotEmpty(),
                modifier = Modifier.testTag("vault_folder_save_button")
            ) {
                Text(confirmLabel)
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
private fun VaultFilePreviewDialog(
    file: AutomationVaultFileEntity,
    onOpenExternal: () -> Unit,
    onExtractValues: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val isImage = remember(file.fileId) {
        file.mimeType.startsWith("image/", ignoreCase = true) ||
            file.fileName.lowercase().let {
                it.endsWith(".png") || it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".webp")
            }
    }
    val previewBitmap = remember(file.localFilePath, isImage) {
        if (!isImage) null
        else runCatching {
            val f = File(file.localFilePath)
            if (f.exists()) {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                BitmapFactory.decodeFile(f.absolutePath, opts)?.asImageBitmap()
            } else {
                null
            }
        }.getOrNull()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = file.fileName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "${file.mimeType} • ${formatFileSizeBytes(file.sizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap,
                        contentDescription = file.fileName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else if (file.textPreview.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = file.textPreview,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    Text(
                        text = "Binary file stored securely inside your Automation Vault folder. Tap Open With to view it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onExtractValues != null) {
                    FilledTonalButton(
                        onClick = onExtractValues,
                        modifier = Modifier.testTag("vault_preview_extract_button")
                    ) {
                        Text("Extract Values")
                    }
                }
                Button(
                    onClick = onOpenExternal,
                    modifier = Modifier.testTag("vault_preview_open_external_button")
                ) {
                    Text("Open With")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

private fun openVaultFileWithSystemViewer(context: Context, file: AutomationVaultFileEntity) {
    runCatching {
        val localFile = File(file.localFilePath)
        if (!localFile.exists()) {
            Toast.makeText(context, "File is no longer present on disk.", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            localFile
        )
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, file.mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(viewIntent, file.fileName))
    }.onFailure {
        Toast.makeText(context, "No app installed to open this file type.", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun UploadVaultFileDialog(
    existingEntries: List<AutomationVaultEntryEntity>,
    defaultOriginUrl: String,
    onImportCandidates: (List<VaultFileImportCandidate>) -> Unit,
    onDismiss: () -> Unit,
    initialRawContent: String = "",
    targetFolderName: String? = null,
    onSaveRawFile: ((fileName: String, mimeType: String, bytes: ByteArray) -> Unit)? = null
) {
    val context = LocalContext.current
    var rawFileText by rememberSaveable { mutableStateOf(initialRawContent) }
    var selectedFileName by rememberSaveable { mutableStateOf("") }
    var selectedFileMime by rememberSaveable { mutableStateOf("application/octet-stream") }
    var pickedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var fileReadError by remember { mutableStateOf<String?>(null) }
    var candidates by remember(initialRawContent) {
        mutableStateOf(
            AutomationVaultManager.parseImportContent(
                rawContent = initialRawContent,
                existingEntries = existingEntries,
                defaultOriginUrl = defaultOriginUrl
            )
        )
    }
    var revealedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun applyRawContent(content: String) {
        rawFileText = content
        fileReadError = null
        candidates = AutomationVaultManager.parseImportContent(
            rawContent = content,
            existingEntries = existingEntries,
            defaultOriginUrl = defaultOriginUrl
        )
        revealedIds = emptySet()
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val resolvedName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0 && cursor.moveToFirst()) cursor.getString(nameIdx) else ""
                }.orEmpty().ifBlank { uri.lastPathSegment.orEmpty() }
                val resolvedMime = context.contentResolver.getType(uri).orEmpty()
                    .ifBlank { "application/octet-stream" }
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Unable to read selected file.")
                Triple(resolvedName, resolvedMime, bytes)
            }.onSuccess { (name, mime, bytes) ->
                selectedFileName = name
                selectedFileMime = mime
                pickedBytes = bytes
                if (AutomationVaultManager.isTextLikeFile(name, mime)) {
                    val text = bytes.toString(Charsets.UTF_8)
                    applyRawContent(text)
                } else {
                    fileReadError = null
                }
            }.onFailure { err ->
                fileReadError = err.message ?: "Could not open the selected file."
            }
        }
    }

    val selectedCount = candidates.count { it.isSelected }
    val allSelected = candidates.isNotEmpty() && selectedCount == candidates.size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (targetFolderName.isNullOrBlank()) {
                    "Upload File to Automation Vault"
                } else {
                    "Upload to $targetFolderName"
                }
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (onSaveRawFile != null) {
                        "Choose any file to upload into this folder, or paste .csv / .json / key-value text to extract reusable values."
                    } else {
                        "Upload a .csv, .json, or .txt file with passwords, emails, phones, or form values, then pick which entries to save & use."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledTonalButton(
                    onClick = {
                        filePickerLauncher.launch(
                            arrayOf(
                                "*/*",
                                "text/*",
                                "text/csv",
                                "application/json"
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_upload_choose_file_button")
                ) {
                    Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedFileName.isNotBlank()) "File: $selectedFileName" else "Choose File",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedTextField(
                    value = rawFileText,
                    onValueChange = { applyRawContent(it) },
                    label = { Text("Or paste file content") },
                    placeholder = { Text("email: user@mail.com\npassword: Secret!123") },
                    minLines = 1,
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_upload_raw_input")
                )
                if (fileReadError != null) {
                    Text(
                        text = fileReadError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (candidates.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "$selectedCount of ${candidates.size} selected",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.testTag("vault_upload_selection_summary")
                        )
                        TextButton(
                            onClick = {
                                val nextSelected = !allSelected
                                candidates = candidates.map { it.copy(isSelected = nextSelected) }
                            },
                            modifier = Modifier.testTag("vault_upload_toggle_all_button")
                        ) {
                            Text(if (allSelected) "Deselect All" else "Select All")
                        }
                    }
                    candidates.forEachIndexed { index, item ->
                        val isRevealed = item.id in revealedIds
                        val displayValue = if (item.isSensitive && !isRevealed) "••••••••" else item.value
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (item.isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            border = BorderStroke(
                                1.dp,
                                if (item.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    candidates = candidates.map {
                                        if (it.id == item.id) it.copy(isSelected = !it.isSelected) else it
                                    }
                                }
                                .testTag("vault_upload_item_$index")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Checkbox(
                                    checked = item.isSelected,
                                    onCheckedChange = { checked ->
                                        candidates = candidates.map {
                                            if (it.id == item.id) it.copy(isSelected = checked) else it
                                        }
                                    },
                                    modifier = Modifier.testTag("vault_upload_item_checkbox_$index")
                                )
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable {
                                                    val allCats = AutomationVaultCategory.entries
                                                    val nextCat = allCats[(allCats.indexOf(item.category) + 1) % allCats.size]
                                                    candidates = candidates.map {
                                                        if (it.id == item.id) {
                                                            it.copy(
                                                                category = nextCat,
                                                                isSensitive = nextCat.isSensitiveDefault
                                                            )
                                                        } else {
                                                            it
                                                        }
                                                    }
                                                }
                                                .testTag("vault_upload_item_cat_$index")
                                        ) {
                                            Text(
                                                text = item.category.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        if (item.websiteOrigin.isNotBlank()) {
                                            Text(
                                                text = item.websiteOrigin,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = displayValue,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (item.isSensitive) {
                                    IconButton(
                                        onClick = {
                                            revealedIds = if (isRevealed) revealedIds - item.id else revealedIds + item.id
                                        },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("vault_upload_item_reveal_$index")
                                    ) {
                                        Icon(
                                            imageVector = if (isRevealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                            contentDescription = if (isRevealed) "Hide value" else "Reveal value",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (rawFileText.isNotBlank()) {
                    Text(
                        text = "No recognizable values detected in file. Try CSV, JSON, or key: value lines.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            val canConfirm = selectedCount > 0 || (onSaveRawFile != null && pickedBytes != null)
            Button(
                onClick = {
                    val bytesToSave = pickedBytes
                    if (onSaveRawFile != null && bytesToSave != null && selectedFileName.isNotBlank()) {
                        onSaveRawFile(selectedFileName, selectedFileMime, bytesToSave)
                    }
                    if (selectedCount > 0) {
                        onImportCandidates(candidates)
                    } else {
                        onDismiss()
                    }
                },
                enabled = canConfirm,
                modifier = Modifier.testTag("vault_upload_confirm_button")
            ) {
                Text(
                    when {
                        selectedCount > 0 && onSaveRawFile != null && pickedBytes != null -> "Save File & $selectedCount Values"
                        selectedCount > 0 -> "Use Selected ($selectedCount)"
                        else -> "Save File"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditAutomationVaultDialog(
    initialEntry: AutomationVaultEntryEntity?,
    initialRevealedValue: String,
    defaultOriginUrl: String,
    onSave: (
        category: AutomationVaultCategory,
        label: String,
        value: String,
        websiteOrigin: String,
        isSensitive: Boolean
    ) -> Unit,
    onDismiss: () -> Unit,
    defaultCategory: AutomationVaultCategory = AutomationVaultCategory.PASSWORD,
    folderName: String? = null
) {
    val isFolderScoped = !folderName.isNullOrBlank()
    val initialCat = remember(initialEntry?.entryId, defaultCategory) {
        if (initialEntry != null) AutomationVaultCategory.fromId(initialEntry.category) else defaultCategory
    }
    var category by remember(initialEntry?.entryId) { mutableStateOf(initialCat) }
    var label by remember(initialEntry?.entryId) { mutableStateOf(initialEntry?.label.orEmpty()) }
    var value by remember(initialEntry?.entryId, initialRevealedValue) { mutableStateOf(initialRevealedValue) }
    var restrictToOrigin by remember(initialEntry?.entryId) {
        mutableStateOf(!initialEntry?.websiteOrigin.isNullOrBlank())
    }
    var showValuePlain by remember(initialEntry?.entryId) {
        mutableStateOf(!initialCat.isSensitiveDefault)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    isFolderScoped && initialEntry == null -> "Add to $folderName"
                    isFolderScoped -> "Edit in $folderName"
                    initialEntry == null -> "Add Vault Value"
                    else -> "Edit Vault Value"
                }
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (!isFolderScoped) {
                    Text(
                        text = "Value Type",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AutomationVaultCategory.entries.forEach { cat ->
                            val selected = category == cat
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    category = cat
                                    if (cat == AutomationVaultCategory.PASSWORD) showValuePlain = false
                                },
                                label = { Text(cat.label) },
                                modifier = Modifier.testTag("vault_dialog_cat_${cat.id.lowercase()}")
                            )
                        }
                    }
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Label (e.g. Work Email, Main Phone)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vault_entry_label_input")
                    )
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(folderName?.takeIf { it.isNotBlank() } ?: "Value") },
                    placeholder = {
                        Text(
                            if (initialEntry?.isSensitive == true && value.isEmpty()) {
                                "Enter new value to update"
                            } else if (!folderName.isNullOrBlank()) {
                                "Enter ${folderName.lowercase()}"
                            } else {
                                "Enter ${category.label.lowercase()} value"
                            }
                        )
                    },
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val targetHint = folderName?.takeIf { it.isNotBlank() } ?: category.label
                                val analyzed = VaultValueGenerator.analyzeFolderName(targetHint, category)
                                val syntheticFolder = AutomationVaultFolderEntity(
                                    folderId = AutomationVaultManager.defaultFolderIdForCategory(category),
                                    name = targetHint,
                                    categoryId = analyzed.category.id,
                                    isDefault = isFolderScoped && AutomationVaultManager.DEFAULT_FOLDERS.any {
                                        it.name.equals(targetHint, ignoreCase = true)
                                    }
                                )
                                val (_, generated) = VaultValueGenerator.generateValue(
                                    folder = syntheticFolder,
                                    overrideType = analyzed.type
                                )
                                value = generated
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("vault_entry_generate_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = stringResource(R.string.automation_vault_generate_button),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_entry_value_input")
                )
                if (!isFolderScoped && defaultOriginUrl.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Scope to website only",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (restrictToOrigin) defaultOriginUrl else "Usable on any website",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Switch(
                            checked = restrictToOrigin,
                            onCheckedChange = { restrictToOrigin = it },
                            modifier = Modifier.testTag("vault_entry_scope_switch")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val effectiveLabel = if (isFolderScoped) {
                        initialEntry?.label?.takeIf { it.isNotBlank() } ?: folderName!!.trim()
                    } else {
                        label.trim().ifBlank { category.label }
                    }
                    val effectiveOrigin = if (!isFolderScoped && restrictToOrigin) {
                        defaultOriginUrl
                    } else {
                        initialEntry?.websiteOrigin.orEmpty()
                    }
                    onSave(category, effectiveLabel, value, effectiveOrigin, category.isSensitiveDefault)
                },
                enabled = value.isNotEmpty(),
                modifier = Modifier.testTag("vault_entry_save_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordingVaultCandidateEditDialog(
    candidate: RecordingVaultCandidate,
    existingVaultEntries: List<AutomationVaultEntryEntity>,
    onSaveCandidate: (
        category: AutomationVaultCategory,
        label: String,
        value: String,
        existingEntryId: String?
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var category by remember(candidate.stepId) { mutableStateOf(candidate.category) }
    var label by remember(candidate.stepId) { mutableStateOf(candidate.vaultLabel) }
    var value by remember(candidate.stepId) { mutableStateOf(candidate.capturedValue) }
    var selectedExistingId by remember(candidate.stepId) { mutableStateOf(candidate.existingEntryId) }
    var showValuePlain by remember(candidate.stepId) { mutableStateOf(!candidate.isSensitive) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Grab Value for Automation Vault") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Step ${candidate.stepOrder}: ${candidate.targetLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AutomationVaultCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = {
                                category = cat
                                if (cat == AutomationVaultCategory.PASSWORD) showValuePlain = false
                            },
                            label = { Text(cat.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Vault Label") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("candidate_edit_label_input")
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        selectedExistingId = null
                    },
                    label = {
                        Text(
                            when {
                                selectedExistingId != null -> "Override value (optional — linked to Vault)"
                                candidate.capturedValue.isEmpty() -> "Value (optional — leave blank for Vault pool)"
                                else -> "Value to save & replay"
                            }
                        )
                    },
                    singleLine = true,
                    visualTransformation = if (category.isSensitiveDefault && !showValuePlain) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    trailingIcon = {
                        if (category.isSensitiveDefault) {
                            IconButton(onClick = { showValuePlain = !showValuePlain }) {
                                Icon(
                                    imageVector = if (showValuePlain) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = "Toggle visibility"
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("candidate_edit_value_input")
                )
                if (existingVaultEntries.isNotEmpty()) {
                    Text(
                        text = "Or link existing Vault entry:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    existingVaultEntries.take(6).forEach { entry ->
                        val isChosen = selectedExistingId == entry.entryId
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isChosen) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                            border = if (isChosen) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedExistingId = if (isChosen) null else entry.entryId
                                    if (!isChosen) {
                                        label = entry.label
                                        category = AutomationVaultCategory.fromId(entry.category)
                                    }
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Icon(
                                    imageVector = vaultCategoryIcon(AutomationVaultCategory.fromId(entry.category)),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "${entry.label} (${if (entry.isSensitive) "••••" else entry.maskedPreview})",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isChosen) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveCandidate(category, label.trim().ifBlank { category.label }, value, selectedExistingId)
                },
                enabled = value.isNotEmpty() || selectedExistingId != null || candidate.capturedValue.isEmpty(),
                modifier = Modifier.testTag("candidate_edit_save_button")
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditStepAutomationVaultSection(
    vaultEntries: List<AutomationVaultEntryEntity>,
    selectedVaultEntryId: String?,
    onSelectVaultEntry: (AutomationVaultEntryEntity?) -> Unit,
    onOpenAutomationVault: (() -> Unit)? = null,
    useVaultPool: Boolean = false,
    selectedVaultCategory: AutomationVaultCategory? = null,
    onToggleVaultPool: ((Boolean, AutomationVaultCategory?) -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("edit_step_automation_vault_section")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Automation Vault Value",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (selectedVaultEntryId != null || useVaultPool) {
                    TextButton(
                        onClick = {
                            onToggleVaultPool?.invoke(false, null)
                            onSelectVaultEntry(null)
                        }
                    ) {
                        Text("Use Custom Text")
                    }
                }
            }
            if (onToggleVaultPool != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pull next unused value from Vault pool",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Uses a fresh value from the selected Vault category on every replay",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = useVaultPool,
                        onCheckedChange = { enabled ->
                            onToggleVaultPool(
                                enabled,
                                selectedVaultCategory ?: AutomationVaultCategory.EMAIL
                            )
                        },
                        modifier = Modifier.testTag("edit_step_vault_pool_toggle")
                    )
                }
                if (useVaultPool) {
                    val activeCat = selectedVaultCategory ?: AutomationVaultCategory.EMAIL
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AutomationVaultCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = activeCat == cat,
                                onClick = { onToggleVaultPool(true, cat) },
                                label = { Text(cat.label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("edit_step_vault_pool_cat_${cat.id.lowercase()}")
                            )
                        }
                    }
                }
            }
            if (vaultEntries.isEmpty()) {
                Text(
                    text = "No Automation Vault values saved yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (!useVaultPool) {
                vaultEntries.forEach { entry ->
                    val cat = AutomationVaultCategory.fromId(entry.category)
                    val isSelected = selectedVaultEntryId == entry.entryId
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                onSelectVaultEntry(if (isSelected) null else entry)
                            }
                            .testTag("edit_step_vault_entry_${entry.entryId}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Icon(
                                imageVector = vaultCategoryIcon(cat),
                                contentDescription = cat.label,
                                modifier = Modifier.size(16.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${entry.label} (${cat.label})",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (entry.isSensitive) "••••" else entry.maskedPreview,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
            if (onOpenAutomationVault != null) {
                OutlinedButton(
                    onClick = onOpenAutomationVault,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_step_open_automation_vault_button")
                ) {
                    Text("Manage Automation Vault")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VaultExhaustionPromptDialog(
    prompt: VaultExhaustionPromptState,
    onGenerateAndResume: (count: Int) -> Unit,
    onOpenVault: () -> Unit,
    onDismiss: () -> Unit
) {
    val defaultCount = (prompt.totalRuns - prompt.failedRunIndex + 1).coerceIn(1, 50)
    var generateCount by remember(prompt.workflowId, prompt.folderId, prompt.failedRunIndex) {
        mutableStateOf(defaultCount.coerceAtLeast(5))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = stringResource(R.string.automation_vault_exhausted_title),
                modifier = Modifier.testTag("vault_exhaustion_title")
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = prompt.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Text(
                            text = "${prompt.workflowName} • Run ${prompt.failedRunIndex}/${prompt.totalRuns}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Folder: ${prompt.folderName} • Category: ${prompt.category.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = "Auto-generate values into ${prompt.folderName}:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(5, 10, 20).forEach { countOption ->
                        FilterChip(
                            selected = generateCount == countOption,
                            onClick = { generateCount = countOption },
                            label = { Text("+$countOption values") },
                            modifier = Modifier.testTag("vault_exhaustion_count_$countOption")
                        )
                    }
                }
                OutlinedButton(
                    onClick = onOpenVault,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("vault_exhaustion_open_vault_button")
                ) {
                    Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.automation_vault_exhausted_open_vault))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onGenerateAndResume(generateCount) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("vault_exhaustion_generate_button")
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.automation_vault_exhausted_generate_action, generateCount))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("vault_exhaustion_close_button")
            ) {
                Text(stringResource(R.string.action_close))
            }
        },
        modifier = Modifier.testTag("vault_exhaustion_dialog")
    )
}
