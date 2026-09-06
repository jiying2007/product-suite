@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.junchen.jingdu

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
internal fun ReaderAnnotationsPanel(state: AppUiState, actions: JingduActions) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(ReaderAnnotationFilter.ALL) }
    var pendingMarkdown by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val text = pendingMarkdown
        pendingMarkdown = null
        if (uri != null && text != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
        }
    }
    val visible = remember(state.annotations, query, filter) {
        val needle = query.trim()
        state.annotations.asSequence()
            .filter { it.kind != ReaderAnnotationKind.BOOKMARK }
            .filter {
                filter == ReaderAnnotationFilter.ALL ||
                    (filter == ReaderAnnotationFilter.HIGHLIGHTS && it.kind == ReaderAnnotationKind.HIGHLIGHT) ||
                    (filter == ReaderAnnotationFilter.NOTES && it.kind == ReaderAnnotationKind.NOTE)
            }
            .filter { needle.isEmpty() || it.excerpt.contains(needle, true) || it.note.contains(needle, true) }
            .toList()
    }
    ModalBottomSheet(onDismissRequest = actions.onClosePanel) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.88f).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.reader_annotations), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                query,
                { query = it.take(200) },
                Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                label = { Text(stringResource(R.string.reader_annotations_search)) },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderAnnotationFilter.entries.forEach { value ->
                    FilterChip(filter == value, { filter = value }, label = { Text(annotationFilterLabel(value)) })
                }
            }
            TextButton(
                onClick = {
                    pendingMarkdown = buildAnnotationMarkdown(
                        book = state.currentBook?.name.orEmpty(),
                        values = visible,
                        documentLength = state.length,
                        title = resources.getString(R.string.reader_annotation_markdown_title),
                        noteLabel = resources.getString(R.string.reader_map_note),
                        highlightLabel = resources.getString(R.string.reader_map_highlight),
                        progressLabel = { percent -> resources.getString(R.string.reader_book_progress_value, percent) },
                    )
                    launcher.launch("jingdu-annotations.md")
                },
                enabled = visible.isNotEmpty(),
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(Icons.Outlined.FileDownload, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.reader_export_markdown))
            }
            if (visible.isEmpty()) {
                Text(stringResource(R.string.reader_annotation_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(visible, key = { it.id }) { item ->
                        ListItem(
                            headlineContent = { Text(item.excerpt.ifBlank { item.note }.ifBlank { annotationKindLabel(item.kind) }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { if (item.note.isNotBlank()) Text(item.note, maxLines = 3, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { Icon(if (item.kind == ReaderAnnotationKind.NOTE) Icons.Outlined.EditNote else Icons.Outlined.FormatColorFill, null) },
                            trailingContent = { IconButton({ actions.onDeleteAnnotation(item.id) }) { Icon(Icons.Outlined.Delete, stringResource(R.string.reader_delete_annotation)) } },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton({ actions.onJump(item.sourceStart); actions.onClosePanel() }, Modifier.fillMaxWidth()) {
                            val percent = if (state.length <= 0) 0 else (item.sourceStart.coerceIn(0, state.length) * 100 / state.length).toInt()
                            Text(stringResource(R.string.reader_book_progress_value, percent))
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReaderReadingMapPanel(state: AppUiState, actions: JingduActions) {
    LaunchedEffect(state.currentBook?.id, state.chaptersLoaded) { if (!state.chaptersLoaded) actions.onEnsureChapters() }
    val chapters = state.chapters
    ModalBottomSheet(onDismissRequest = actions.onClosePanel) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.90f).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Map, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(stringResource(R.string.reader_reading_map), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.reader_reading_map_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MapLegend(Color(MaterialTheme.colorScheme.primary.value), stringResource(R.string.reader_map_read))
                MapLegend(Color(MaterialTheme.colorScheme.secondaryContainer.value), stringResource(R.string.reader_map_unread))
            }
            Spacer(Modifier.height(10.dp))
            if (chapters.isEmpty()) {
                Text(stringResource(R.string.reader_no_chapters), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(chapters.indices.toList(), key = { chapters[it].offset }) { index ->
                        val chapter = chapters[index]
                        val end = chapters.getOrNull(index + 1)?.offset ?: state.length
                        val span = (end - chapter.offset).coerceAtLeast(1)
                        val progress = ((state.position - chapter.offset).coerceIn(0, span).toFloat() / span.toFloat()).coerceIn(0f, 1f)
                        val marks = state.annotations.filter { it.sourceStart in chapter.offset until end }
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(ReaderTextPresentation.chapterTitle(chapter.title, state.settings), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${(progress * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp))
                            if (marks.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 5.dp)) {
                                    val bookmarks = marks.count { it.kind == ReaderAnnotationKind.BOOKMARK }
                                    val highlights = marks.count { it.kind == ReaderAnnotationKind.HIGHLIGHT }
                                    val notes = marks.count { it.kind == ReaderAnnotationKind.NOTE }
                                    if (bookmarks > 0) MapCount(Icons.Outlined.Bookmark, bookmarks)
                                    if (highlights > 0) MapCount(Icons.Outlined.FormatColorFill, highlights)
                                    if (notes > 0) MapCount(Icons.Outlined.EditNote, notes)
                                }
                            }
                            TextButton({ actions.onJump(chapter.offset); actions.onClosePanel() }) { Text(stringResource(R.string.reader_skim_preview)) }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReaderReadingHistoryPanel(state: AppUiState, actions: JingduActions) {
    val context = LocalContext.current
    val days by produceState<List<ReaderDayModel>>(initialValue = state.readingDays, state.currentBook?.id) {
        value = withContext(Dispatchers.IO) { ReaderStatsStore(context).days(84).map { ReaderDayModel(it.dayEpoch, it.durationMs, it.charsRead) } }
    }
    val byDay = remember(days) { days.associateBy { it.dayEpoch } }
    val today = remember { LocalDate.now().toEpochDay() }
    val cells = remember(today, days) { (83L downTo 0L).map { today - it } }
    val maxMinutes = remember(days) { days.maxOfOrNull { (it.durationMs / 60_000L).coerceAtLeast(1) }?.toFloat() ?: 1f }
    ModalBottomSheet(onDismissRequest = actions.onClosePanel) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(stringResource(R.string.reader_reading_history), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            if (days.isEmpty()) Text(stringResource(R.string.reader_history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyVerticalGrid(
                columns = GridCells.Fixed(12),
                modifier = Modifier.fillMaxWidth().height(190.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                gridItems(cells) { day ->
                    val item = byDay[day]
                    val intensity = if (item == null) 0.05f else ((item.durationMs / 60_000f) / maxMinutes).coerceIn(0.15f, 1f)
                    Box(Modifier.aspectRatio(1f).background(MaterialTheme.colorScheme.primary.copy(alpha = intensity), RoundedCornerShape(3.dp)))
                }
            }
            days.take(7).forEach { day ->
                val date = LocalDate.ofEpochDay(day.dayEpoch)
                ListItem(
                    headlineContent = { Text(date.toString()) },
                    supportingContent = { Text(stringResource(R.string.reader_history_chars, day.charsRead.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())) },
                    trailingContent = { Text(stringResource(R.string.reader_history_minutes, (day.durationMs / 60_000L).coerceAtLeast(1).toInt())) },
                )
            }
        }
    }
}

@Composable private fun MapLegend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable private fun MapCount(icon: androidx.compose.ui.graphics.vector.ImageVector, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp))
        Text("$count", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable private fun annotationFilterLabel(value: ReaderAnnotationFilter): String = stringResource(
    when (value) {
        ReaderAnnotationFilter.ALL -> R.string.reader_annotations_all
        ReaderAnnotationFilter.HIGHLIGHTS -> R.string.reader_annotations_highlights
        ReaderAnnotationFilter.NOTES -> R.string.reader_annotations_notes
    },
)

@Composable private fun annotationKindLabel(value: ReaderAnnotationKind): String = stringResource(
    when (value) {
        ReaderAnnotationKind.BOOKMARK -> R.string.reader_map_bookmark
        ReaderAnnotationKind.HIGHLIGHT -> R.string.reader_map_highlight
        ReaderAnnotationKind.NOTE -> R.string.reader_map_note
    },
)

internal fun buildAnnotationMarkdown(
    book: String,
    values: List<ReaderAnnotation>,
    documentLength: Long,
    title: String,
    noteLabel: String,
    highlightLabel: String,
    progressLabel: (Int) -> String,
): String = buildString {
    append("# ").append(title).append("\n\n")
    if (book.isNotBlank()) append("## ").append(book).append("\n\n")
    values.forEach { item ->
        val percent = if (documentLength <= 0L) 0 else {
            ((item.sourceStart.coerceIn(0L, documentLength).toDouble() / documentLength.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
        }
        append("- ").append(if (item.kind == ReaderAnnotationKind.NOTE) noteLabel else highlightLabel)
            .append(" · ").append(progressLabel(percent)).append("\n")
        if (item.excerpt.isNotBlank()) append("  > ").append(item.excerpt.replace("\n", " ")).append("\n")
        if (item.note.isNotBlank()) append("  ").append(item.note.replace("\n", "  \n  ")).append("\n")
        append('\n')
    }
}
