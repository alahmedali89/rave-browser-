package com.example.ui.browser

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.BookmarkEntity
import com.example.data.repository.BrowserRepository
import com.example.ui.theme.ApexMotion
import com.example.ui.theme.EmphasizedTypography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreenOverlay(
    bookmarks: List<BookmarkEntity>,
    onSelectBookmark: (String) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onImportBookmarks: (String) -> Unit = {},
    onNotifyExported: (Int, String) -> Unit = { _, _ -> },
    onDismiss: () -> Unit,
    backHandlerEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = backHandlerEnabled, onBack = onDismiss)

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var filterQuery by rememberSaveable { mutableStateOf("") }
    var showOverflowMenu by rememberSaveable { mutableStateOf(false) }
    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    var showExportDialog by rememberSaveable { mutableStateOf(false) }

    // Storage Access Framework: Open Bookmark File (.html / .json / .txt)
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader(Charsets.UTF_8).readText()
                }
            }.getOrNull()?.let { rawContent ->
                if (rawContent.isNotBlank()) {
                    onImportBookmarks(rawContent)
                    showImportDialog = false
                }
            }
        }
    }

    // Storage Access Framework: Save Bookmarks as Netscape HTML (.html)
    val exportHtmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/html")
    ) { uri ->
        if (uri != null) {
            val htmlPayload = BrowserRepository.exportBookmarksAsHtml(bookmarks)
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(htmlPayload.toByteArray(Charsets.UTF_8))
                    outputStream.flush()
                }
                onNotifyExported(bookmarks.size, "HTML")
                showExportDialog = false
            }
        }
    }

    // Storage Access Framework: Save Bookmarks as JSON (.json)
    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val jsonPayload = BrowserRepository.exportBookmarksAsJson(bookmarks)
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonPayload.toByteArray(Charsets.UTF_8))
                    outputStream.flush()
                }
                onNotifyExported(bookmarks.size, "JSON")
                showExportDialog = false
            }
        }
    }

    val filteredBookmarks = if (filterQuery.isBlank()) {
        bookmarks
    } else {
        val q = filterQuery.trim().lowercase()
        bookmarks.filter {
            it.title.lowercase().contains(q) || it.url.lowercase().contains(q)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
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
                            .testTag("close_bookmarks_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.bookmarks_screen_title),
                            style = EmphasizedTypography.headlineHero
                        )
                        Text(
                            text = "${bookmarks.size} saved pages",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("bookmarks_more_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.cd_tab_switcher_more_options)
                            )
                        }

                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                            shape = RoundedCornerShape(18.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .widthIn(min = 200.dp)
                                .testTag("bookmarks_more_dropdown_menu")
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.bookmarks_import_dialog_title)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.FileDownload,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    showImportDialog = true
                                },
                                modifier = Modifier.testTag("bookmarks_import_button")
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.bookmarks_export_dialog_title)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.FileUpload,
                                        contentDescription = null
                                    )
                                },
                                enabled = bookmarks.isNotEmpty(),
                                onClick = {
                                    showOverflowMenu = false
                                    showExportDialog = true
                                },
                                modifier = Modifier.testTag("bookmarks_export_button")
                            )
                        }
                    }
                }
            )
        },
        modifier = modifier
            .fillMaxSize()
            .testTag("bookmarks_screen")
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp)
                    .padding(horizontal = 16.dp)
            ) {
                if (bookmarks.isNotEmpty()) {
                    OutlinedTextField(
                        value = filterQuery,
                        onValueChange = { filterQuery = it },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null
                            )
                        },
                        placeholder = { Text("Search saved bookmarks…") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .testTag("bookmarks_search_input")
                    )
                }

                if (filteredBookmarks.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Card(
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Bookmarks,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = stringResource(R.string.bookmarks_empty_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.bookmarks_empty_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = { showImportDialog = true },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.testTag("bookmarks_empty_import_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.FileDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.bookmarks_import_dialog_title))
                                }
                            }
                        }
                    }
                } else {
                    // Material 3 Grouped List for Saved Bookmarks
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(GroupedListSpacing),
                        contentPadding = PaddingValues(top = 10.dp, bottom = 96.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("bookmarks_list")
                    ) {
                        itemsIndexed(
                            items = filteredBookmarks,
                            key = { _, item -> item.id }
                        ) { index, item ->
                            val itemShape = groupedListItemShape(
                                index = index,
                                totalCount = filteredBookmarks.size
                            )
                            Surface(
                                shape = itemShape,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(
                                    0.75.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem(placementSpec = ApexMotion.spatialSpring())
                                    .clip(itemShape)
                                    .clickable { onSelectBookmark(item.url) }
                                    .testTag("bookmark_card_${item.id}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 66.dp)
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Filled.Public,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = item.title,
                                            style = EmphasizedTypography.activeTabTitle,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = item.url,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    IconButton(
                                        onClick = { onDeleteBookmark(item.id) },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("delete_bookmark_${item.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteOutline,
                                            contentDescription = stringResource(R.string.cd_delete_bookmark),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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

    if (showImportDialog) {
        ImportBookmarksDialog(
            onChooseFile = {
                importFileLauncher.launch(
                    arrayOf("text/html", "application/json", "text/plain", "*/*")
                )
            },
            onImportContent = { content ->
                onImportBookmarks(content)
                showImportDialog = false
            },
            onPasteFromClipboard = {
                clipboardManager.getText()?.text.orEmpty()
            },
            onDismiss = { showImportDialog = false }
        )
    }

    if (showExportDialog) {
        ExportBookmarksDialog(
            bookmarksCount = bookmarks.size,
            onExportHtmlFile = {
                exportHtmlLauncher.launch("apex_bookmarks.html")
            },
            onExportJsonFile = {
                exportJsonLauncher.launch("apex_bookmarks.json")
            },
            onCopyHtmlToClipboard = {
                val html = BrowserRepository.exportBookmarksAsHtml(bookmarks)
                clipboardManager.setText(AnnotatedString(html))
                onNotifyExported(bookmarks.size, "HTML copied")
                showExportDialog = false
            },
            onShareBookmarks = {
                val html = BrowserRepository.exportBookmarksAsHtml(bookmarks)
                runCatching {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Apex Browser Bookmarks (${bookmarks.size})")
                        putExtra(Intent.EXTRA_TEXT, html)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Export Bookmarks"))
                }
                showExportDialog = false
            },
            onDismiss = { showExportDialog = false }
        )
    }
}

@Composable
private fun ImportBookmarksDialog(
    onChooseFile: () -> Unit,
    onImportContent: (String) -> Unit,
    onPasteFromClipboard: () -> String,
    onDismiss: () -> Unit
) {
    var rawText by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.FileDownload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = stringResource(R.string.bookmarks_import_dialog_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.bookmarks_import_dialog_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onChooseFile,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bookmarks_import_choose_file_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FileOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.bookmarks_choose_file))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Or paste HTML / JSON / URLs:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            val clip = onPasteFromClipboard()
                            if (clip.isNotBlank()) {
                                rawText = clip
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("bookmarks_import_paste_clipboard_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentPaste,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Paste", style = MaterialTheme.typography.labelSmall)
                    }
                }

                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    placeholder = {
                        Text(
                            text = "<DT><A HREF=\"https://example.com\">Example</A>\nor [{\"title\":\"Example\",\"url\":\"https://example.com\"}]",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    minLines = 3,
                    maxLines = 6,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bookmarks_import_text_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onImportContent(rawText) },
                enabled = rawText.trim().isNotEmpty(),
                modifier = Modifier.testTag("bookmarks_import_confirm_button")
            ) {
                Text(stringResource(R.string.bookmarks_import_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun ExportBookmarksDialog(
    bookmarksCount: Int,
    onExportHtmlFile: () -> Unit,
    onExportJsonFile: () -> Unit,
    onCopyHtmlToClipboard: () -> Unit,
    onShareBookmarks: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.FileUpload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = stringResource(R.string.bookmarks_export_dialog_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${stringResource(R.string.bookmarks_export_dialog_subtitle)} ($bookmarksCount saved)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Material 3 Expressive Grouped Export Options
                Column(
                    verticalArrangement = Arrangement.spacedBy(GroupedListSpacing),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MaterialGroupListItem(
                        index = 0,
                        totalCount = 4,
                        title = stringResource(R.string.bookmarks_save_html_file),
                        subtitle = "Standard Netscape HTML compatible with all browsers",
                        onClick = onExportHtmlFile,
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.testTag("bookmarks_export_html_file_option")
                    )

                    MaterialGroupListItem(
                        index = 1,
                        totalCount = 4,
                        title = stringResource(R.string.bookmarks_save_json_file),
                        subtitle = "Structured JSON backup with timestamps",
                        onClick = onExportJsonFile,
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.Code,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.testTag("bookmarks_export_json_file_option")
                    )

                    MaterialGroupListItem(
                        index = 2,
                        totalCount = 4,
                        title = stringResource(R.string.bookmarks_copy_html),
                        subtitle = "Copy full HTML bookmark file to clipboard",
                        onClick = onCopyHtmlToClipboard,
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.testTag("bookmarks_export_copy_html_option")
                    )

                    MaterialGroupListItem(
                        index = 3,
                        totalCount = 4,
                        title = stringResource(R.string.bookmarks_share_export),
                        subtitle = "Send exported bookmarks to another app",
                        onClick = onShareBookmarks,
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.testTag("bookmarks_export_share_option")
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
