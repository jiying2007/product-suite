package com.junchen.jingdu

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.LinkedHashMap
import kotlin.math.roundToInt

private data class TocPanelKey(val bookId: String, val revision: String, val length: Long, val chaptersHash: Int)
private data class TocPanelEntry(val base: TocQualityReport, val report: TocQualityReport)

internal fun readerTocCurrentIndex(chapters: List<SmartChapter>, activePosition: Long): Int {
    if (chapters.isEmpty()) return 0
    var low = 0
    var high = chapters.lastIndex
    var result = 0
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (chapters[mid].offset <= activePosition) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return result
}

private object TocPanelCache {
    private val entries = object : LinkedHashMap<TocPanelKey, TocPanelEntry>(5, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<TocPanelKey, TocPanelEntry>?): Boolean = size > 4
    }

    @Synchronized fun get(key: TocPanelKey): TocPanelEntry? = entries[key]
    @Synchronized fun get(bookId: String, revision: String, length: Long): TocPanelEntry? =
        entries.entries.firstOrNull { (key, _) ->
            key.bookId == bookId && key.revision == revision && key.length == length
        }?.value

    @Synchronized fun put(key: TocPanelKey, entry: TocPanelEntry) { entries[key] = entry }
}

/** Promote the import/re-decode TOC cache before the first visible Chapters interaction. */
internal fun prewarmReaderSmartChaptersPanel(
    context: Context,
    bookId: String,
    revision: String,
    length: Long,
) {
    if (bookId.isBlank() || revision.isBlank() || length <= 0L) return
    if (TocPanelCache.get(bookId, revision, length) != null) return
    val appContext = context.applicationContext
    val base = SmartTocCacheStore(appContext).load(bookId, revision, length) ?: return
    val key = TocPanelKey(bookId, revision, length, base.chapters.hashCode())
    if (TocPanelCache.get(key) != null) return
    val store = TocOverrideStore(appContext)
    TocPanelCache.put(key, TocPanelEntry(base, store.apply(base, store.load(bookId, length))))
}

/**
 * Smart TOC is a real scrolling list. Manual eight-row pagination was both slower to navigate and
 * a source of stale-display-list bugs. LazyColumn keeps chapter counts bounded to the viewport,
 * gives Android native scrolling/fling semantics, and makes every visible row a real hit target.
 */
@Composable
internal fun ReaderSmartChaptersPanel(
    state: AppUiState,
    actions: JingduActions,
    currentPosition: () -> Long,
) {
    val context = LocalContext.current
    val book = state.currentBook
    val store = remember { TocOverrideStore(context) }
    val derivedCache = remember { SmartTocCacheStore(context) }
    val initial = remember(book?.id, book?.normalizedSha256, state.length) {
        book?.let { TocPanelCache.get(it.id, it.normalizedSha256, state.length) }
    }
    var base by remember(book?.id, book?.normalizedSha256, state.length) { mutableStateOf(initial?.base) }
    var report by remember(book?.id, book?.normalizedSha256, state.length) { mutableStateOf(initial?.report) }
    var loading by remember(book?.id, book?.normalizedSha256, state.length) {
        mutableStateOf(initial == null && !state.chaptersLoaded)
    }
    var failed by remember(book?.id, book?.normalizedSha256, state.length) { mutableStateOf(false) }
    var loadRequest by remember(book?.id, book?.normalizedSha256, state.length) { mutableIntStateOf(0) }
    var addDialog by rememberSaveable(book?.id) { mutableStateOf(false) }
    var title by rememberSaveable(book?.id) { mutableStateOf("") }
    var editing by rememberSaveable(book?.id) { mutableStateOf(false) }
    val activePosition = currentPosition()
    val initialChapters = initial?.report?.chapters.orEmpty()
    val initialIndex = remember(initialChapters, activePosition) {
        readerTocCurrentIndex(initialChapters, activePosition)
    }
    var positioned by remember(book?.id) { mutableStateOf(initialChapters.isNotEmpty()) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

    LaunchedEffect(book?.id, book?.normalizedSha256, state.chaptersLoaded, state.chapters, state.length, loadRequest) {
        if (book == null) {
            base = null
            report = null
            loading = false
            failed = false
            positioned = false
            return@LaunchedEffect
        }
        if (initial != null) {
            base = initial.base
            report = initial.report
            loading = false
            failed = false
            return@LaunchedEffect
        }

        failed = false
        loading = true
        try {
            // Import/re-decode prewarms this revision cache.
            // A revision-cache hit is authoritative for this panel; never hydrate duplicate global chapter state.
            val cachedBase = withContext(Dispatchers.IO) {
                derivedCache.load(book.id, book.normalizedSha256, state.length)
            }
            if (cachedBase != null) {
                val key = TocPanelKey(book.id, book.normalizedSha256, state.length, cachedBase.chapters.hashCode())
                TocPanelCache.get(key)?.let { cached ->
                    base = cached.base
                    report = cached.report
                    loading = false
                    return@LaunchedEffect
                }
                val overrides = withContext(Dispatchers.IO) { store.load(book.id, state.length) }
                val computed = withContext(Dispatchers.Default) { store.apply(cachedBase, overrides) }
                base = cachedBase
                report = computed
                TocPanelCache.put(key, TocPanelEntry(cachedBase, computed))
                loading = false
                return@LaunchedEffect
            }

            if (!state.chaptersLoaded) {
                actions.onEnsureChapters()
                return@LaunchedEffect
            }
            val key = TocPanelKey(book.id, book.normalizedSha256, state.length, state.chapters.hashCode())
            TocPanelCache.get(key)?.let { cached ->
                base = cached.base
                report = cached.report
                loading = false
                return@LaunchedEffect
            }
            val computedBase = withContext(Dispatchers.Default) {
                SmartToc.evaluate(state.chapters.map { SmartChapter(it.offset, it.title, it.source, it.confidence) })
            }
            val overrides = withContext(Dispatchers.IO) { store.load(book.id, state.length) }
            val computed = withContext(Dispatchers.Default) { store.apply(computedBase, overrides) }
            base = computedBase
            report = computed
            TocPanelCache.put(key, TocPanelEntry(computedBase, computed))
            loading = false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ProductErrorLog(context).record(ProductErrorCode.INTERNAL_OPERATION_FAILED, "smart-chapters")
            base = null
            report = null
            positioned = false
            failed = true
            loading = false
        }
    }

    val chapters = report?.chapters.orEmpty()
    val currentIndex = remember(chapters, activePosition) {
        readerTocCurrentIndex(chapters, activePosition)
    }

    LaunchedEffect(book?.id, chapters.size, currentIndex) {
        if (!positioned && chapters.isNotEmpty()) {
            listState.scrollToItem(currentIndex.coerceIn(chapters.indices))
            positioned = true
        }
    }

    fun panelKey(): TocPanelKey? = book?.let {
        TocPanelKey(it.id, it.normalizedSha256, state.length, base?.chapters?.hashCode() ?: state.chapters.hashCode())
    }

    fun cache(updated: TocQualityReport?) {
        report = updated
        val original = base
        val key = panelKey()
        if (original != null && updated != null && key != null) {
            TocPanelCache.put(key, TocPanelEntry(original, updated))
        }
    }

    fun hide(index: Int) {
        val currentBook = book ?: return
        val chapter = chapters.getOrNull(index) ?: return
        store.hide(currentBook.id, chapter.offset, state.length)
        cache(base?.let { store.apply(it, store.load(currentBook.id, state.length)) })
    }

    fun reset() {
        val currentBook = book ?: return
        store.reset(currentBook.id)
        report = base
        positioned = false
        val key = panelKey()
        val original = base
        if (original != null && key != null) TocPanelCache.put(key, TocPanelEntry(original, original))
    }

    val quality = report?.let {
        stringResource(R.string.smart_toc_quality, it.score, it.chapters.size, it.anomalyCount)
    }.orEmpty()

    ReaderPanelSurface(onDismiss = actions.onClosePanel) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.88f)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.smart_toc), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    if (editing && quality.isNotBlank()) Text(
                        quality,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (editing) {
                    IconButton({ addDialog = true }, enabled = book != null) {
                        Icon(Icons.Default.Add, stringResource(R.string.toc_add_here))
                    }
                    IconButton(::reset, enabled = base != null) {
                        Icon(Icons.Outlined.Restore, stringResource(R.string.toc_reset_repairs))
                    }
                    IconButton({ editing = false }) {
                        Icon(Icons.Default.Done, stringResource(R.string.reader_toc_done))
                    }
                } else {
                    IconButton({ editing = true }) {
                        Icon(Icons.Outlined.Edit, stringResource(R.string.reader_toc_edit))
                    }
                }
                IconButton(actions.onClosePanel) {
                    Icon(Icons.Default.Close, stringResource(R.string.cancel))
                }
            }
            HorizontalDivider()

            when {
                failed -> Column(
                    Modifier.fillMaxWidth().height(180.dp).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        stringResource(R.string.reader_chapters_failed),
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { loadRequest++ }) {
                        Text(stringResource(R.string.reader_retry))
                    }
                }
                loading -> Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                chapters.isEmpty() -> Box(
                    Modifier.fillMaxWidth().height(180.dp).padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.reader_no_chapters), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(chapters, key = { _, item -> item.offset }) { index, chapter ->
                        val displayTitle = ReaderTextPresentation.chapterTitle(chapter.title, state.settings)
                        val selected = index == currentIndex
                        val rowModifier = if (selected) {
                            Modifier.fillMaxWidth().background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                RoundedCornerShape(14.dp),
                            )
                        } else {
                            Modifier.fillMaxWidth()
                        }
                        val editSemantics = if (editing) {
                            val hideActionLabel = "${stringResource(R.string.toc_hide_heading)}: $displayTitle"
                            Modifier.semantics(mergeDescendants = true) {
                                customActions = listOf(
                                    CustomAccessibilityAction(hideActionLabel) {
                                        hide(index)
                                        true
                                    },
                                )
                            }
                        } else Modifier
                        Row(
                            rowModifier
                                .clickable { actions.onJump(chapter.offset) }
                                .then(editSemantics)
                                .heightIn(min = 64.dp)
                                .padding(start = 14.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (editing) {
                                val percent = if (state.length <= 0L) 0 else {
                                    ((chapter.offset.toDouble() / state.length.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
                                }
                                val sourceLabel = if (chapter.source != "core") {
                                    " · ${stringResource(R.string.toc_source_user_or_special)}"
                                } else {
                                    ""
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        displayTitle,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                    Text(
                                        "$percent%$sourceLabel",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Box(Modifier.clearAndSetSemantics {}) {
                                    IconButton({ hide(index) }) {
                                        Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                                    }
                                }
                            } else {
                                ReaderPanelText(
                                    text = displayTitle,
                                    modifier = Modifier.weight(1f).height(52.dp).padding(end = 12.dp),
                                    fontSizeSp = 16f,
                                    bold = selected,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (addDialog && book != null) AlertDialog(
        onDismissRequest = { addDialog = false },
        title = { Text(stringResource(R.string.toc_add_here)) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(80) },
                label = { Text(stringResource(R.string.toc_custom_title)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    store.add(book.id, currentPosition(), title, state.length)
                    cache(base?.let { store.apply(it, store.load(book.id, state.length)) })
                    title = ""
                    addDialog = false
                    positioned = false
                },
                enabled = title.isNotBlank(),
            ) { Text(stringResource(R.string.toc_add_action)) }
        },
        dismissButton = {
            TextButton({ addDialog = false }) { Text(stringResource(R.string.cancel)) }
        },
    )
}
