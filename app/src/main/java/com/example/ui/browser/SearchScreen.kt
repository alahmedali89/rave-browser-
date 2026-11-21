package com.example.ui.browser

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.SearchHistoryEntity
import com.example.data.preferences.SearchEngineOption
import com.example.engine.SearchSuggestionItem
import com.example.engine.SuggestionKind
import com.example.ui.theme.EmphasizedTypography
import com.example.ui.viewmodel.NetworkStatusBannerState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreenOverlay(
    query: String,
    selectedEngine: SearchEngineOption,
    suggestions: List<SearchSuggestionItem>,
    recentHistory: List<SearchHistoryEntity>,
    activeTabUrl: String? = null,
    activeTabTitle: String? = null,
    networkBannerState: NetworkStatusBannerState = NetworkStatusBannerState.HIDDEN,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: (String) -> Unit,
    onFillQuery: (String) -> Unit,
    onDeleteHistoryItem: (Long) -> Unit,
    onClearAllHistory: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onDismiss)

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var clipboardSnippet by remember { mutableStateOf<String?>(null) }
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = query, selection = TextRange(query.length)))
    }

    // Keep textFieldValue synchronized when query is updated externally (e.g. Edit URL, Fill suggestion, Clear)
    LaunchedEffect(query) {
        if (textFieldValue.text != query) {
            textFieldValue = TextFieldValue(
                text = query,
                selection = TextRange(query.length)
            )
        }
    }

    val consumeCurrentClipboardSnippet: (String?) -> Unit = { snippetToConsume ->
        val targetSnippet = (snippetToConsume ?: clipboardSnippet)?.trim()
        if (!targetSnippet.isNullOrEmpty()) {
            ClipboardConsumptionTracker.markConsumed(context, targetSnippet)
        }
        clipboardSnippet = null
    }

    val fillAndFocusQuery: (String) -> Unit = { filledText ->
        textFieldValue = TextFieldValue(
            text = filledText,
            selection = TextRange(filledText.length)
        )
        onFillQuery(filledText)
        runCatching {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(Unit) {
        ClipboardConsumptionTracker.ensureRegistered(context)
        runCatching {
            val text = clipboardManager.getText()?.text?.trim()
            if (!text.isNullOrBlank() &&
                text != activeTabUrl &&
                text != query.trim() &&
                !ClipboardConsumptionTracker.isConsumed(context, text)
            ) {
                clipboardSnippet = text
            } else {
                clipboardSnippet = null
            }
        }
        delay(60)
        runCatching {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxSize()
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    StatusBarConnectivityHeader(
                        networkBannerState = networkBannerState,
                        defaultContainerColor = MaterialTheme.colorScheme.surface
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(start = 4.dp, end = 12.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("search_screen_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.cd_close_search),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(
                                    0.85.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 14.dp)
                                ) {
                                    SearchEngineLogo(
                                        engine = selectedEngine,
                                        size = 22.dp
                                    )

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Box(
                                        contentAlignment = Alignment.CenterStart,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (textFieldValue.text.isEmpty()) {
                                            Text(
                                                text = stringResource(
                                                    R.string.search_screen_hint,
                                                    selectedEngine.displayName
                                                ),
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        val submitCurrentInput: () -> Unit = {
                                            val candidate = textFieldValue.text.trim()
                                            if (candidate.isNotBlank()) {
                                                val activeClip = clipboardSnippet
                                                if (!activeClip.isNullOrBlank() &&
                                                    (candidate.equals(activeClip, ignoreCase = true) ||
                                                        candidate.equals("https://$activeClip", ignoreCase = true) ||
                                                        candidate.equals("http://$activeClip", ignoreCase = true))
                                                ) {
                                                    consumeCurrentClipboardSnippet(activeClip)
                                                }
                                                focusManager.clearFocus()
                                                keyboardController?.hide()
                                                onSubmitSearch(candidate)
                                            }
                                        }
                                        BasicTextField(
                                            value = textFieldValue,
                                            onValueChange = { newVal ->
                                                textFieldValue = newVal
                                                val activeClip = clipboardSnippet
                                                if (!activeClip.isNullOrBlank() && newVal.text.trim() == activeClip) {
                                                    consumeCurrentClipboardSnippet(activeClip)
                                                }
                                                if (newVal.text != query) {
                                                    onQueryChange(newVal.text)
                                                }
                                            },
                                            singleLine = true,
                                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Uri,
                                                imeAction = ImeAction.Search
                                            ),
                                            keyboardActions = KeyboardActions(
                                                onSearch = { submitCurrentInput() },
                                                onGo = { submitCurrentInput() },
                                                onDone = { submitCurrentInput() }
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .focusRequester(focusRequester)
                                                .testTag("search_screen_input")
                                        )
                                    }

                                    if (textFieldValue.text.isNotEmpty()) {
                                        IconButton(
                                            onClick = {
                                                textFieldValue = TextFieldValue("", TextRange.Zero)
                                                onQueryChange("")
                                                runCatching {
                                                    focusRequester.requestFocus()
                                                    keyboardController?.show()
                                                }
                                            },
                                            modifier = Modifier
                                                .size(34.dp)
                                                .testTag("search_screen_clear_button")
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
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .testTag("dedicated_search_screen")
        ) { innerPadding ->
            Box(
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 780.dp)
                ) {
                    val hasActiveUrl = !activeTabUrl.isNullOrBlank() && !activeTabUrl.startsWith("apex://", ignoreCase = true)
                    val trimmedQuery = query.trim()

                    val effectiveSuggestions = remember(trimmedQuery, suggestions, selectedEngine) {
                        if (trimmedQuery.isEmpty()) {
                            emptyList()
                        } else {
                            val looksLikeDomainOrUrl = trimmedQuery.startsWith("http://", ignoreCase = true) ||
                                trimmedQuery.startsWith("https://", ignoreCase = true) ||
                                (!trimmedQuery.contains(' ') && trimmedQuery.contains('.') && trimmedQuery.length >= 4)
                            val hasDirectMatch = suggestions.any {
                                it.title.equals(trimmedQuery, ignoreCase = true) ||
                                    it.submitValue.equals(trimmedQuery, ignoreCase = true)
                            }
                            if (hasDirectMatch) {
                                suggestions
                            } else {
                                val normalizedTarget = if (looksLikeDomainOrUrl &&
                                    !trimmedQuery.startsWith("http://", true) &&
                                    !trimmedQuery.startsWith("https://", true)
                                ) {
                                    "https://$trimmedQuery"
                                } else {
                                    trimmedQuery
                                }
                                val immediateItem = SearchSuggestionItem(
                                    id = "immediate_query_action",
                                    title = trimmedQuery,
                                    subtitle = if (looksLikeDomainOrUrl) {
                                        "Open web address directly"
                                    } else {
                                        "Search with ${selectedEngine.displayName}"
                                    },
                                    submitValue = normalizedTarget,
                                    fillText = trimmedQuery,
                                    engineBadge = if (looksLikeDomainOrUrl) "URL" else selectedEngine.shortBadge,
                                    kind = if (looksLikeDomainOrUrl) SuggestionKind.DIRECT_URL else SuggestionKind.ENGINE_SUGGESTION
                                )
                                listOf(immediateItem) + suggestions
                            }
                        }
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 14.dp,
                            end = 14.dp,
                            top = 10.dp,
                            bottom = 120.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(GroupedListSpacing),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("search_screen_scroll_list")
                    ) {
                        if (trimmedQuery.isNotEmpty()) {
                            val totalGroupCount = effectiveSuggestions.size
                            itemsIndexed(
                                items = effectiveSuggestions,
                                key = { _, item -> item.id }
                            ) { idx, suggestion ->
                                val shape = groupedListItemShape(
                                    index = idx,
                                    totalCount = totalGroupCount,
                                    outerRadius = 16.dp,
                                    innerRadius = 4.dp
                                )
                                GroupedSuggestionListItem(
                                    item = suggestion,
                                    typedPrefix = trimmedQuery,
                                    shape = shape,
                                    onSelect = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        onSubmitSearch(suggestion.submitValue)
                                    },
                                    onFill = { fillAndFocusQuery(suggestion.fillText) }
                                )
                            }
                        } else {
                            val currentSnippet = clipboardSnippet?.trim()?.takeIf { it.isNotEmpty() }
                            val hasClipboard = currentSnippet != null
                            val totalGroupCount = (if (hasActiveUrl) 1 else 0) + (if (hasClipboard) 1 else 0) + recentHistory.size
                            var currentIndex = 0

                            if (hasActiveUrl) {
                                val activeShape = groupedListItemShape(
                                    index = currentIndex++,
                                    totalCount = totalGroupCount,
                                    outerRadius = 16.dp,
                                    innerRadius = 4.dp
                                )
                                item(key = "active_page_url_group_item") {
                                    ActiveUrlGroupItem(
                                        title = activeTabTitle?.takeIf { it.isNotBlank() } ?: activeTabUrl,
                                        url = activeTabUrl,
                                        shape = activeShape,
                                        onOpen = {
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            onSubmitSearch(activeTabUrl)
                                        },
                                        onShare = {
                                            runCatching {
                                                val intent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_SUBJECT, activeTabTitle ?: "Link")
                                                    putExtra(Intent.EXTRA_TEXT, activeTabUrl)
                                                }
                                                context.startActivity(Intent.createChooser(intent, "Share link"))
                                            }
                                        },
                                        onCopy = {
                                            clipboardManager.setText(AnnotatedString(activeTabUrl))
                                            Toast.makeText(context, context.getString(R.string.notice_link_copied), Toast.LENGTH_SHORT).show()
                                        },
                                        onEdit = {
                                            fillAndFocusQuery(activeTabUrl)
                                        }
                                    )
                                }
                            }

                            if (currentSnippet != null) {
                                val isCopiedUrl = currentSnippet.startsWith("http://", ignoreCase = true) ||
                                    currentSnippet.startsWith("https://", ignoreCase = true) ||
                                    (!currentSnippet.contains(' ') && currentSnippet.contains('.') && currentSnippet.length >= 4)
                                val clipShape = groupedListItemShape(
                                    index = currentIndex++,
                                    totalCount = totalGroupCount,
                                    outerRadius = 16.dp,
                                    innerRadius = 4.dp
                                )
                                item(key = "clipboard_detected_group_card") {
                                    ClipboardDetectedCard(
                                        snippet = currentSnippet,
                                        isUrl = isCopiedUrl,
                                        shape = clipShape,
                                        onClick = {
                                            consumeCurrentClipboardSnippet(currentSnippet)
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            val target = if (isCopiedUrl && !currentSnippet.startsWith("http://", true) && !currentSnippet.startsWith("https://", true)) {
                                                "https://$currentSnippet"
                                            } else {
                                                currentSnippet
                                            }
                                            onSubmitSearch(target)
                                        },
                                        onFill = {
                                            consumeCurrentClipboardSnippet(currentSnippet)
                                            fillAndFocusQuery(currentSnippet)
                                        }
                                    )
                                }
                            }

                            val historyStartIndex = currentIndex
                            itemsIndexed(
                                items = recentHistory,
                                key = { _, item -> item.id }
                            ) { idx, historyItem ->
                                val shape = groupedListItemShape(
                                    index = historyStartIndex + idx,
                                    totalCount = totalGroupCount,
                                    outerRadius = 16.dp,
                                    innerRadius = 4.dp
                                )
                                GroupedHistoryListItem(
                                    historyItem = historyItem,
                                    shape = shape,
                                    onSelect = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        onSubmitSearch(historyItem.query)
                                    },
                                    onFill = { fillAndFocusQuery(historyItem.query) },
                                    onDelete = { onDeleteHistoryItem(historyItem.id) }
                                )
                            }

                            if (recentHistory.isNotEmpty()) {
                                item(key = "clear_history_footer") {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 12.dp, bottom = 8.dp)
                                    ) {
                                        TextButton(
                                            onClick = onClearAllHistory,
                                            modifier = Modifier.testTag("clear_search_history_button")
                                        ) {
                                            Text(
                                                text = stringResource(R.string.search_clear_history),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            } else if (!hasActiveUrl && !hasClipboard) {
                                item(key = "recent_history_empty_state") {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 32.dp, horizontal = 8.dp)
                                    ) {
                                        Card(
                                            shape = MaterialTheme.shapes.extraLarge,
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                            ),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(28.dp)
                                            ) {
                                                Surface(
                                                    shape = MaterialTheme.shapes.large,
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    modifier = Modifier.size(56.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Outlined.TravelExplore,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            modifier = Modifier.size(28.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = stringResource(R.string.search_fresh_empty_title),
                                                    style = EmphasizedTypography.activeTabTitle,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = stringResource(
                                                        R.string.search_fresh_empty_subtitle,
                                                        selectedEngine.displayName
                                                    ),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    textAlign = TextAlign.Center
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
}

/**
 * Active URL item formatted as a standard Material 3 Grouped List item with action icons at the right side.
 */
@Composable
private fun ActiveUrlGroupItem(
    title: String,
    url: String,
    shape: androidx.compose.ui.graphics.Shape,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            0.75.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onOpen)
            .testTag("search_active_url_card")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(1.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                IconButton(
                    onClick = onShare,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("action_share_url")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = stringResource(R.string.action_share),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onCopy,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("action_copy_url")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(R.string.action_copy),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("action_edit_url")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.action_edit),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Clipboard detection card in Grouped List item style.
 */
@Composable
private fun ClipboardDetectedCard(
    snippet: String,
    isUrl: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    onFill: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            0.75.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
            .testTag("clipboard_detected_row")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.ContentPaste,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(1.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(
                        if (isUrl) R.string.clipboard_copied_link_title else R.string.clipboard_copied_text_title
                    ),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = snippet,
                    style = EmphasizedTypography.webClipboard,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onFill,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("fill_clipboard_suggestion")
            ) {
                Icon(
                    imageVector = Icons.Filled.NorthWest,
                    contentDescription = stringResource(R.string.cd_fill_suggestion),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

/**
 * Suggestion item using the Grouped List item design style (position-aware shape, compact M3 height).
 */
@Composable
private fun GroupedSuggestionListItem(
    item: SearchSuggestionItem,
    typedPrefix: String,
    shape: androidx.compose.ui.graphics.Shape,
    onSelect: () -> Unit,
    onFill: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isUrl = item.kind == SuggestionKind.DIRECT_URL

    val annotatedTitle = remember(item.title, typedPrefix) {
        buildAnnotatedString {
            val lowerTitle = item.title.lowercase()
            val lowerPrefix = typedPrefix.lowercase()
            if (lowerPrefix.isNotEmpty() && lowerTitle.startsWith(lowerPrefix)) {
                append(item.title.substring(0, typedPrefix.length))
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(item.title.substring(typedPrefix.length))
                }
            } else {
                append(item.title)
            }
        }
    }

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            0.75.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onSelect)
            .testTag("suggestion_row_${item.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 50.dp)
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isUrl) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isUrl) Icons.Filled.Public else Icons.Filled.Search,
                        contentDescription = null,
                        tint = if (isUrl) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(1.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = annotatedTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.subtitle.isNotBlank()) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onFill,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("fill_suggestion_${item.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.NorthWest,
                    contentDescription = stringResource(R.string.cd_fill_suggestion),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

/**
 * Recent search history item using the Grouped List item design style.
 */
@Composable
private fun GroupedHistoryListItem(
    historyItem: SearchHistoryEntity,
    shape: androidx.compose.ui.graphics.Shape,
    onSelect: () -> Unit,
    onFill: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            0.75.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onSelect)
            .testTag("history_item_${historyItem.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 50.dp)
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(1.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = historyItem.query,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onFill,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.NorthWest,
                        contentDescription = stringResource(R.string.cd_fill_suggestion),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cd_remove_history_item),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

/**
 * Tracks consumed clipboard snippets so that once a detected clipboard link or text is used
 * (via direct navigation/search, fill-to-search-bar, or paste), it disappears and does not
 * reappear on subsequent Search screen visits until a new clip is copied.
 */
object ClipboardConsumptionTracker {
    private const val PREFS_NAME = "apex_clipboard_detection_prefs"
    private const val KEY_CONSUMED_SNIPPET = "consumed_snippet"
    private const val KEY_CONSUMED_TIMESTAMP = "consumed_timestamp"

    @Volatile
    private var listenerRegistered = false

    @Volatile
    private var consumedSnippetInMemory: String? = null

    @Volatile
    private var consumedTimestampInMemory: Long = 0L

    fun ensureRegistered(context: Context) {
        if (listenerRegistered) return
        synchronized(this) {
            if (listenerRegistered) return
            val appContext = context.applicationContext
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            consumedSnippetInMemory = prefs.getString(KEY_CONSUMED_SNIPPET, null)
            consumedTimestampInMemory = prefs.getLong(KEY_CONSUMED_TIMESTAMP, 0L)
            val sysClipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            sysClipboard?.addPrimaryClipChangedListener {
                consumedSnippetInMemory = null
                consumedTimestampInMemory = 0L
                prefs.edit()
                    .remove(KEY_CONSUMED_SNIPPET)
                    .remove(KEY_CONSUMED_TIMESTAMP)
                    .apply()
            }
            listenerRegistered = true
        }
    }

    fun isConsumed(context: Context, rawText: String): Boolean {
        val normalized = rawText.trim()
        if (normalized.isEmpty()) return true
        ensureRegistered(context)
        val appContext = context.applicationContext
        val sysClipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val currentClipTs = sysClipboard?.primaryClipDescription?.timestamp ?: 0L
        val consumedText = consumedSnippetInMemory
            ?: appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_CONSUMED_SNIPPET, null)
        if (consumedText == null) return false
        if (consumedText != normalized) return false
        val consumedTs = consumedTimestampInMemory.takeIf { it > 0L }
            ?: appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_CONSUMED_TIMESTAMP, 0L)
        if (currentClipTs > 0L && consumedTs > 0L && currentClipTs > consumedTs) {
            return false
        }
        return true
    }

    fun markConsumed(context: Context, rawText: String) {
        val normalized = rawText.trim()
        if (normalized.isEmpty()) return
        ensureRegistered(context)
        val appContext = context.applicationContext
        val sysClipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val currentClipTs = sysClipboard?.primaryClipDescription?.timestamp ?: System.currentTimeMillis()
        consumedSnippetInMemory = normalized
        consumedTimestampInMemory = currentClipTs
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CONSUMED_SNIPPET, normalized)
            .putLong(KEY_CONSUMED_TIMESTAMP, currentClipTs)
            .apply()
    }

    fun resetForTesting(context: Context) {
        consumedSnippetInMemory = null
        consumedTimestampInMemory = 0L
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}

