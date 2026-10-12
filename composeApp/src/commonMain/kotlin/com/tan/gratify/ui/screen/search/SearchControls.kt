package com.tan.gratify.ui.screen.search

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tan.gratify.ui.theme.GratifyColors
import com.tan.gratify.viewModel.*
import gratify.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

internal fun canSubmitQuery(query: String, type: SearchType): Boolean =
    (if (type == SearchType.ALL || type == SearchType.USERS) query.trim().removePrefix("@") else query.trim()).isNotBlank()

@Composable
fun SearchControls(query: String, type: SearchType, onQuery: (String) -> Unit, onSubmit: () -> Unit,
    onType: (SearchType) -> Unit, focusRequester: FocusRequester = remember { FocusRequester() }, onFocus: (Boolean) -> Unit = {},
    landing: Boolean = false) {
    val canSubmit = canSubmitQuery(query, type)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicTextField(value = query, onValueChange = { onQuery(it.take(200)) }, singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.SansSerif,
                    fontWeight = if (landing) FontWeight.Medium else FontWeight.Normal,
                    color = if (landing) Color.Black else GratifyColors.TextPrimary),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(if (landing) Color.Black else GratifyColors.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (canSubmit) onSubmit() }),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    .background(if (landing) Color.White else GratifyColors.Surface, RoundedCornerShape(6.dp))
                    .focusRequester(focusRequester).onFocusChanged { onFocus(it.isFocused) },
                decorationBox = { field ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = if (landing) Color.Black else GratifyColors.TextSecondary)
                        Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                            if (query.isEmpty()) Text(stringResource(if (landing) Res.string.search_compact_hint
                                else if (type == SearchType.USERS) Res.string.search_people_hint else Res.string.search_music_hint),
                                maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.SansSerif),
                                color = if (landing) Color(0xFF505050) else GratifyColors.TextSecondary)
                            field()
                        }
                        if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Close, stringResource(Res.string.search_clear_query), Modifier.size(20.dp), tint = GratifyColors.TextSecondary)
                        } else Spacer(Modifier.width(12.dp))
                    }
                })
            if (!landing) FilledIconButton(onClick = onSubmit, enabled = canSubmit, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Search, stringResource(Res.string.search_submit))
            }
        }
        if (!landing) SearchCategoryFilters(type, onType)
    }
}

/** Consistent capsules that keep the active category visible, including externally selected People. */
@Composable
fun SearchCategoryFilters(type: SearchType, onType: (SearchType) -> Unit, modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()
    val requesters = remember { SearchType.entries.associateWith { BringIntoViewRequester() } }
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val maxCapsuleWidth = (maxWidth - 32.dp).coerceAtLeast(48.dp)
        LaunchedEffect(type, maxWidth, fontScale) {
            withFrameNanos { }
            requesters.getValue(type).bringIntoView()
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).horizontalScroll(scrollState).padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchType.entries.forEach { choice ->
                FilterChip(
                    selected = type == choice, onClick = { onType(choice) },
                    shape = CircleShape, border = null,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    label = { Text(stringResource(choice.toStringRes()), maxLines = 1,
                        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge) },
                    modifier = Modifier.widthIn(min = 48.dp, max = maxCapsuleWidth).heightIn(min = 48.dp)
                        .bringIntoViewRequester(requesters.getValue(choice)),
                )
            }
        }
    }
}

@Composable
fun SearchQueryList(values: List<String>, history: Boolean, busy: Boolean = false, problem: Boolean = false,
    suggestionLoading: Boolean = false, onSubmit: (String) -> Unit, onFill: (String) -> Unit,
    onRemove: (String) -> Unit = {}, onClear: () -> Unit = {}, onRetry: () -> Unit = {}, recommendations: LazyListScope.() -> Unit = {}) {
    var confirming by remember { mutableStateOf(false) }
    if (confirming) AlertDialog(onDismissRequest = { confirming = false }, title = { Text(stringResource(Res.string.search_clear_confirm)) },
        text = { Text(stringResource(Res.string.search_clear_body)) },
        confirmButton = { TextButton(onClick = { confirming = false; onClear() }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(Res.string.clear_search_history)) } },
        dismissButton = { TextButton(onClick = { confirming = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(Res.string.search_cancel)) } })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column {
                Text(stringResource(if (history) Res.string.search_recent_title else Res.string.search_suggestions_title), style = MaterialTheme.typography.titleLarge)
                if (history && values.isNotEmpty()) TextButton(onClick = { confirming = true }, enabled = !busy,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(Res.string.clear_search_history)) }
            }
        }
        if (problem) item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(if (history) Res.string.search_history_failed else Res.string.search_suggestions_failed), color = GratifyColors.TextSecondary)
            if (history) TextButton(onClick = onRetry, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(Res.string.retry)) }
        } }
        if (suggestionLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(Res.string.search_suggestions_loading)) }
        recommendations()
        if (history && values.isEmpty() && !problem) item {
            Column(Modifier.padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.search_recent_empty), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(Res.string.search_recent_body), color = GratifyColors.TextSecondary)
            }
        }
        items(values.distinct(), key = { it }) { value ->
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).background(GratifyColors.Surface, RoundedCornerShape(12.dp)), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).heightIn(min = 64.dp).clickable { onSubmit(value) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(if (history) Icons.Rounded.History else Icons.Rounded.Search, null, Modifier.size(20.dp))
                    Text(value, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { onFill(value) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.Edit, stringResource(Res.string.search_fill)) }
                if (history) IconButton(onClick = { onRemove(value) }, enabled = !busy, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Close, stringResource(Res.string.search_remove_recent))
                }
            }
        }
    }
}

@Composable
fun SearchResultStatus(phase: SearchScreenUIState, query: String, type: SearchType, loading: Boolean = false,
    onRetry: () -> Unit = {}, onAll: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(Icons.Rounded.Search, null, Modifier.size(48.dp), tint = GratifyColors.Accent)
        when {
            phase is SearchScreenUIState.Loading || loading -> {
                Text(stringResource(Res.string.search_loading_title), style = MaterialTheme.typography.headlineSmall)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            phase is SearchScreenUIState.Error -> {
                Text(stringResource(Res.string.search_failed_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(Res.string.search_failed_body), color = GratifyColors.TextSecondary)
                Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(Res.string.retry)) }
            }
            phase is SearchScreenUIState.Empty -> {
                Text(stringResource(Res.string.search_empty_query_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(if (type == SearchType.USERS) Res.string.search_people_body else Res.string.search_music_hint), color = GratifyColors.TextSecondary)
            }
            else -> {
                Text(stringResource(Res.string.search_no_match_title, query), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(Res.string.search_no_match_body), color = GratifyColors.TextSecondary)
                if (type != SearchType.ALL && type != SearchType.USERS) Button(onClick = onAll, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(Res.string.search_all_music)) }
            }
        }
    }
}

@Composable
fun SearchPartialNotice(loading: Boolean, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(GratifyColors.Surface).padding(16.dp)) {
        Text(stringResource(Res.string.search_partial), color = GratifyColors.TextSecondary)
        TextButton(onClick = onRetry, enabled = !loading, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(Res.string.retry)) }
    }
}
