package com.junchen.jingdu

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
internal fun LibraryScreen(state: AppUiState, actions: JingduActions, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val folderStore = remember { FolderLibraryStore(context) }
    var deleteTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var tagTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var tagInput by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf("ALL") }
    var libraryQuery by rememberSaveable { mutableStateOf("") }
    var sortName by rememberSaveable { mutableStateOf(LibrarySort.RECENT.name) }
    var sortMenu by remember { mutableStateOf(false) }
    var libraryToolsMenu by remember { mutableStateOf(false) }
    var importChooser by rememberSaveable { mutableStateOf(false) }
    var importPreview by remember { mutableStateOf<ImportPreview?>(null) }
    var previewBusy by remember { mutableStateOf(false) }
    var previewBookId by remember { mutableStateOf<String?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }
    var syncBusy by remember { mutableStateOf(false) }
    var syncResult by remember { mutableStateOf<FolderLibraryStore.SyncResult?>(null) }
    var batchBusy by remember { mutableStateOf(false) }
    var batchReport by remember { mutableStateOf<BatchAutomationReport?>(null) }
    var pendingBatchExport by remember { mutableStateOf<BatchAutomationReport?>(null) }
    var manageFolders by rememberSaveable { mutableStateOf(false) }
    var folderRevision by remember { mutableIntStateOf(0) }

    val folderRoots = remember(folderRevision) { folderStore.roots() }

    val progressiveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        previewBusy = true
        previewBookId = null
        previewError = null
        importPreview = null
        scope.launch {
            try {
                importPreview = withContext(Dispatchers.IO) { ProgressiveImport(context).prepare(uri) }
                val imported = withContext(Dispatchers.IO) {
                    val repository = BookRepository(context)
                    val book = repository.importUri(uri, BookRepository.AUTO)
                    ReaderController().use { warm ->
                        warm.open(repository.normalizedFile(book), 0)
                        repository.updateCharCount(book, warm.length())
                    }
                    book
                }
                previewBookId = imported.id
                actions.onBackToLibrary()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                previewError = failure.message
            } finally {
                previewBusy = false
            }
        }
    }

    suspend fun syncFolders(): FolderLibraryStore.SyncResult = withContext(Dispatchers.IO) {
        val repository = BookRepository(context)
        val existingBookIds = repository.list().mapTo(linkedSetOf()) { it.id }
        var discovered = 0
        var imported = 0
        var skipped = 0
        var failed = 0
        val roots = folderStore.roots()
        roots.forEach { root ->
            val entries = folderStore.scanTxt(root)
            discovered += entries.size
            entries.forEach { entry ->
                if (!folderStore.needsImport(entry, existingBookIds)) {
                    skipped++
                    return@forEach
                }
                try {
                    val book = repository.importUri(entry.uri, BookRepository.AUTO)
                    folderStore.markImported(entry, book.id)
                    existingBookIds += book.id
                    imported++
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    failed++
                }
            }
        }
        FolderLibraryStore.SyncResult(roots.size, discovered, imported, skipped, failed)
    }

    suspend fun performFolderSync() {
        try {
            syncResult = syncFolders()
            actions.onBackToLibrary()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            syncResult = FolderLibraryStore.SyncResult(folderStore.roots().size, 0, 0, 0, 1)
        } finally {
            syncBusy = false
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val persisted = runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.isSuccess
        if (!persisted) {
            syncResult = FolderLibraryStore.SyncResult(folderRoots.size, 0, 0, 0, 1)
            return@rememberLauncherForActivityResult
        }
        folderStore.addRoot(uri)
        folderRevision++
        syncBusy = true
        scope.launch { performFolderSync() }
    }

    fun startFolderSync() {
        if (folderRoots.isEmpty()) {
            folderLauncher.launch(null)
            return
        }
        syncBusy = true
        scope.launch { performFolderSync() }
    }

    fun removeFolderRoot(uri: android.net.Uri) {
        runCatching { context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        folderStore.removeRoot(uri)
        folderRevision++
    }

    fun runBatch(applySafe: Boolean) {
        if (!state.proUnlocked) {
            actions.onUpgradePro()
            return
        }
        val activity = context as? Activity ?: return
        batchBusy = true
        scope.launch {
            batchReport = withContext(Dispatchers.IO) {
                val repository = BookRepository(context)
                BatchAutomation(
                    repository = repository,
                    activityPreferences = activity.getPreferences(Context.MODE_PRIVATE),
                    globalRules = RuleLibrary(context).load(),
                    feedback = SmartCleanFeedbackStore(context),
                    cleanHistory = CleanHistory(context),
                ).run(repository.list(), applySafe)
            }
            batchBusy = false
        }
    }

    val batchExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val report = pendingBatchExport
        pendingBatchExport = null
        if (uri != null && report != null) runCatching {
            context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                output.write(BatchAutomation.toJson(report).toByteArray(Charsets.UTF_8))
            }
        }
    }

    val filteredBooks = remember(state.books, filterName, sortName, libraryQuery) {
        val query = libraryQuery.trim().lowercase()
        val filtered = state.books.filter { book ->
            val matchesQuery = query.isEmpty() || stripTxt(book.name).lowercase().contains(query) ||
                book.tags.any { it.lowercase().contains(query) }
            val matchesFilter = when (filterName) {
                "FAVORITES" -> book.favorite
                "READING" -> book.status == LibraryBookStatus.READING
                "FINISHED" -> book.status == LibraryBookStatus.FINISHED
                else -> true
            }
            matchesQuery && matchesFilter
        }
        when (runCatching { LibrarySort.valueOf(sortName) }.getOrDefault(LibrarySort.RECENT)) {
            LibrarySort.RECENT -> filtered.sortedByDescending(BookCardModel::touchedAt)
            LibrarySort.NAME -> filtered.sortedBy { stripTxt(it.name).lowercase() }
            LibrarySort.PROGRESS -> filtered.sortedByDescending(BookCardModel::progressFraction)
        }
    }
    val continueBook = remember(state.books) {
        state.books.filter { it.status == LibraryBookStatus.READING }.maxByOrNull(BookCardModel::touchedAt)
    }
    val showContinue = continueBook != null && libraryQuery.isBlank() && filterName == "ALL" && sortName == LibrarySort.RECENT.name
    val gridBooks = if (showContinue && continueBook != null) filteredBooks.filterNot { it.id == continueBook.id } else filteredBooks

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column(
                Modifier.fillMaxWidth().statusBarsPaddingCompat().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.app_title), style = if (state.books.isEmpty()) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                        if (state.books.isEmpty()) Text(
                            stringResource(R.string.library_tagline_terminal),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.books.isNotEmpty()) Box {
                        IconButton(onClick = { libraryToolsMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.library_more_actions))
                        }
                        DropdownMenu(expanded = libraryToolsMenu, onDismissRequest = { libraryToolsMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.add_folder_library)) }, leadingIcon = { Icon(Icons.Outlined.FolderOpen, null) }, onClick = { libraryToolsMenu = false; folderLauncher.launch(null) })
                            if (folderRoots.isNotEmpty()) DropdownMenuItem(text = { Text(stringResource(R.string.sync_folders, folderRoots.size)) }, enabled = !syncBusy, leadingIcon = { if (syncBusy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Refresh, null) }, onClick = { libraryToolsMenu = false; startFolderSync() })
                            if (folderRoots.isNotEmpty()) DropdownMenuItem(text = { Text(stringResource(R.string.manage_folder_library, folderRoots.size)) }, leadingIcon = { Icon(Icons.Default.Folder, null) }, onClick = { libraryToolsMenu = false; manageFolders = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.batch_optimize)) }, enabled = !batchBusy && state.books.isNotEmpty(), leadingIcon = { Icon(Icons.Default.AutoFixHigh, null) }, onClick = { libraryToolsMenu = false; runBatch(false) })
                        }
                    }
                }
                if (state.books.size > 1) {
                    OutlinedTextField(
                        value = libraryQuery,
                        onValueChange = { libraryQuery = it.take(120) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = if (libraryQuery.isNotBlank()) ({
                            IconButton({ libraryQuery = "" }) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear)) }
                        }) else null,
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LibraryFilterChip(filterName == "ALL", stringResource(R.string.library_filter_all)) { filterName = "ALL" }
                        LibraryFilterChip(filterName == "READING", stringResource(R.string.library_filter_reading)) { filterName = "READING" }
                        LibraryFilterChip(filterName == "FAVORITES", stringResource(R.string.library_filter_favorites)) { filterName = "FAVORITES" }
                        LibraryFilterChip(filterName == "FINISHED", stringResource(R.string.library_filter_finished)) { filterName = "FINISHED" }
                        Box {
                            AssistChip(onClick = { sortMenu = true }, label = { Text("${stringResource(R.string.library_sort_label)} · ${sortLabel(sortName)}") })
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                LibrarySort.entries.forEach { sort -> DropdownMenuItem(text = { Text(sortLabel(sort.name)) }, onClick = { sortName = sort.name; sortMenu = false }) }
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (state.books.isNotEmpty()) ExtendedFloatingActionButton(
                onClick = { importChooser = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.import_txt)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.books.isEmpty() -> EmptyLibrary(Modifier.padding(padding), { importChooser = true }, { folderLauncher.launch(null) })
            filteredBooks.isEmpty() -> Column(Modifier.fillMaxSize().padding(padding).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text(
                    if (libraryQuery.isNotBlank()) stringResource(R.string.search_no_result, libraryQuery) else stringResource(R.string.search_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = { libraryQuery = ""; filterName = "ALL" }) { Text(stringResource(R.string.clear)) }
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(300.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 112.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                continueBook?.takeIf { showContinue }?.let { current ->
                    item(span = { GridItemSpan(maxLineSpan) }) { ContinueReadingCard(current, { actions.onOpenBook(current.id) }) }
                }
                gridItems(gridBooks, key = { it.id }) { book ->
                    BookCard(book, { actions.onOpenBook(book.id) }, { deleteTarget = book.id }, { actions.onToggleFavorite(book.id) }, { tagTarget = book.id; tagInput = book.tags.joinToString(", ") })
                }
            }
        }
    }

    if (importChooser) ModalBottomSheet(onDismissRequest = { importChooser = false }) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.import_txt), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.import_mode_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Surface(
                onClick = {
                    importChooser = false
                    progressiveLauncher.launch(arrayOf("text/plain", "text/*", "application/octet-stream"))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.select_txt), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.single_import_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }
            Surface(
                onClick = {
                    importChooser = false
                    actions.onBatchImport()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LibraryAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.select_multiple_txt), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.batch_import_picker_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }
        }
    }

    importPreview?.let { preview ->
        ModalBottomSheet(onDismissRequest = { if (!previewBusy) importPreview = null }) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.88f).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.first_readable_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.first_readable_meta, preview.name, preview.encoding, preview.sampledBytes / 1024), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (previewBusy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.full_import_background), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                previewError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Surface(Modifier.fillMaxWidth().weight(1f), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                    SelectionContainer { Text(preview.text, Modifier.padding(16.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodyLarge) }
                }
                previewBookId?.let { id ->
                    Button(onClick = { importPreview = null; actions.onOpenBook(id) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.open_full_book)) }
                }
            }
        }
    }

    if (previewError != null && importPreview == null) AlertDialog(
        onDismissRequest = { previewError = null },
        title = { Text(stringResource(R.string.error_import)) },
        text = { Text(previewError.orEmpty()) },
        confirmButton = { TextButton(onClick = { previewError = null }) { Text(stringResource(R.string.competitive_ok)) } },
    )

    syncResult?.let { result ->
        AlertDialog(
            onDismissRequest = { syncResult = null },
            title = { Text(stringResource(R.string.folder_sync_complete)) },
            text = { Text(stringResource(R.string.folder_sync_result, result.roots, result.discovered, result.imported, result.skipped, result.failed)) },
            confirmButton = { TextButton(onClick = { syncResult = null }) { Text(stringResource(R.string.competitive_ok)) } },
        )
    }

    batchReport?.let { report ->
        AlertDialog(
            onDismissRequest = { if (!batchBusy) batchReport = null },
            title = { Text(stringResource(R.string.batch_optimize_report)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.batch_report_summary, report.booksScanned, report.totalCandidates, report.safeCandidates, report.tocAnomalies, report.failedBooks))
                    if (report.appliedRules > 0) Text(stringResource(R.string.batch_report_applied, report.appliedRules), color = MaterialTheme.colorScheme.primary)
                    report.books.take(5).forEach { item ->
                        Text(stringResource(R.string.batch_book_preview, stripTxt(item.name), item.healthScore, item.safeCandidates), style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { Button(onClick = { runBatch(true) }, enabled = !batchBusy && report.safeCandidates > 0) { Text(stringResource(R.string.apply_safe_batch)) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { pendingBatchExport = report; batchExportLauncher.launch("jingdu-batch-report.json") }) { Text(stringResource(R.string.export_action)) }
                    TextButton(onClick = { batchReport = null }) { Text(stringResource(R.string.competitive_close)) }
                }
            },
        )
    }

    if (manageFolders) AlertDialog(
        onDismissRequest = { manageFolders = false },
        title = { Text(stringResource(R.string.folder_library_roots)) },
        text = {
            if (folderRoots.isEmpty()) Text(stringResource(R.string.folder_library_empty))
            else LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(folderRoots, key = { it.toString() }) { root ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(root.toString(), modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { removeFolderRoot(root) }) { Icon(Icons.Default.Delete, stringResource(R.string.remove_folder_root)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { manageFolders = false }) { Text(stringResource(R.string.competitive_close)) } },
    )

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.remove_from_library_title)) },
            text = { Text(stringResource(R.string.remove_from_library_body)) },
            confirmButton = { TextButton(onClick = { actions.onDeleteLibraryBook(target); deleteTarget = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    tagTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { tagTarget = null },
            title = { Text(stringResource(R.string.book_tags_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.book_tags_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = tagInput, onValueChange = { tagInput = it }, singleLine = false, placeholder = { Text(stringResource(R.string.book_tags_hint)) })
                }
            },
            confirmButton = { TextButton(onClick = { actions.onSetBookTags(target, tagInput); tagTarget = null }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { tagTarget = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun LibraryFilterChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun sortLabel(name: String): String = when (runCatching { LibrarySort.valueOf(name) }.getOrDefault(LibrarySort.RECENT)) {
    LibrarySort.RECENT -> stringResource(R.string.library_sort_recent)
    LibrarySort.NAME -> stringResource(R.string.library_sort_name)
    LibrarySort.PROGRESS -> stringResource(R.string.library_sort_progress)
}

@Composable
private fun EmptyLibrary(modifier: Modifier, onImport: () -> Unit, onFolder: () -> Unit) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(modifier = Modifier.size(88.dp), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.empty_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onImport) { Icon(Icons.Default.Add, contentDescription = null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.import_txt)) }
        TextButton(onClick = onFolder) { Text(stringResource(R.string.add_folder_library)) }
    }
}

@Composable
private fun ContinueReadingCard(book: BookCardModel, onOpen: () -> Unit) {
    ElevatedCard(
        onClick = onOpen,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(width = 64.dp, height = 88.dp), shape = MaterialTheme.shapes.medium, color = coverColor(book.id)) {
                Box(contentAlignment = Alignment.Center) { Text(book.name.trim().firstOrNull()?.toString()?.uppercase() ?: "TXT", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White) }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.continue_reading), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(stripTxt(book.name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                LinearProgressIndicator(progress = { book.progressFraction }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(MaterialTheme.shapes.small))
                Text("${(book.progressFraction * 100).roundToInt()}% · ${formatTouched(book.touchedAt, stringResource(R.string.not_read))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun BookCard(book: BookCardModel, onOpen: () -> Unit, onDelete: () -> Unit, onToggleFavorite: () -> Unit, onEditTags: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    ElevatedCard(onClick = onOpen, colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(width = 50.dp, height = 68.dp), shape = MaterialTheme.shapes.medium, color = coverColor(book.id)) {
                Box(contentAlignment = Alignment.Center) { Text(book.name.trim().firstOrNull()?.toString()?.uppercase() ?: "TXT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stripTxt(book.name), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            if (book.favorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                            contentDescription = stringResource(if (book.favorite) R.string.unfavorite_book else R.string.favorite_book),
                            tint = if (book.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Text("${statusLabel(book.status)} · ${formatTouched(book.touchedAt, stringResource(R.string.not_read))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (book.tags.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(book.tags.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (book.charCount > 0 && book.progress > 0) {
                    Spacer(Modifier.height(7.dp))
                    LinearProgressIndicator(progress = { book.progressFraction }, modifier = Modifier.fillMaxWidth().height(3.dp).clip(MaterialTheme.shapes.small))
                    Spacer(Modifier.height(3.dp))
                    Text("${(book.progressFraction * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.book_actions)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.edit_tags)) }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onEditTags() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete_private_copy)) }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun statusLabel(status: LibraryBookStatus): String = when (status) {
    LibraryBookStatus.UNREAD -> stringResource(R.string.status_unread)
    LibraryBookStatus.READING -> stringResource(R.string.status_reading)
    LibraryBookStatus.FINISHED -> stringResource(R.string.status_finished)
}