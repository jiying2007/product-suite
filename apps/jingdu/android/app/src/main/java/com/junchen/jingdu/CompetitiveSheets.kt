@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.junchen.jingdu

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun DoctorSheet(state: AppUiState, actions: JingduActions) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val book = state.currentBook
    var report by remember(book?.id) { mutableStateOf<TxtDoctorReport?>(null) }
    var loading by remember(book?.id) { mutableStateOf(false) }
    var error by remember(book?.id) { mutableStateOf<String?>(null) }
    var scanRequest by remember(book?.id) { mutableIntStateOf(0) }

    fun request() {
        scanRequest++
        error = null
        report = null
    }
    LaunchedEffect(book?.id, scanRequest) {
        if (book == null) return@LaunchedEffect
        loading = true
        error = null
        try {
            report = withContext(Dispatchers.IO) {
                val repo = BookRepository(context)
                val source = repo.list().firstOrNull { it.id == book.id } ?: error("missing book")
                ReaderController().use { reader ->
                    reader.open(repo.normalizedFile(source), source.progress)
                    TxtDoctor.diagnose(reader, source)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ProductErrorLog(context).record(ProductErrorCode.INTERNAL_OPERATION_FAILED, "txt-doctor")
            error = resources.getString(R.string.txt_doctor_failed)
        } finally {
            loading = false
        }
    }

    ModalBottomSheet(onDismissRequest = actions.onClosePanel, sheetGesturesEnabled = false) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f),
            contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.HealthAndSafety, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.txt_doctor), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.txt_doctor_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { request() }) { Icon(Icons.Default.Refresh, stringResource(R.string.rescan_noise)) }
                }
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            report?.let { value ->
                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.txt_health_score, value.healthScore), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            DoctorMetric(stringResource(R.string.doctor_encoding), value.encodingScore, value.encoding)
                            DoctorMetric(stringResource(R.string.doctor_toc), value.tocScore, stringResource(R.string.doctor_toc_detail, value.chapterCount, value.tocAnomalies))
                            DoctorMetric(stringResource(R.string.doctor_clean), value.cleanScore, stringResource(R.string.doctor_clean_detail, value.noiseCandidates))
                            DoctorMetric(stringResource(R.string.doctor_text), value.textScore, stringResource(R.string.doctor_text_detail, value.garbledWindows, value.replacementCharacters))
                        }
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { actions.onOpenPanel(ReaderPanel.ENCODING) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.text_encoding)) }
                        OutlinedButton(onClick = { actions.onOpenPanel(ReaderPanel.CHAPTERS) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.chapters)) }
                        Button(onClick = { actions.onOpenPanel(ReaderPanel.SMART_CLEAN_LAB) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.smart_clean4)) }
                    }
                }
            }
        }
    }
}

@Composable private fun DoctorMetric(label: String, score: Int, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text("$score", color = MaterialTheme.colorScheme.primary)
        }
        LinearProgressIndicator(progress = { score / 100f }, modifier = Modifier.fillMaxWidth())
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SmartChaptersSheet(state: AppUiState, actions: JingduActions) {
    val context = LocalContext.current
    val book = state.currentBook
    val store = remember { TocOverrideStore(context) }
    var base by remember(book?.id) { mutableStateOf<TocQualityReport?>(null) }
    var report by remember(book?.id) { mutableStateOf<TocQualityReport?>(null) }
    var loading by remember(book?.id) { mutableStateOf(!state.chaptersLoaded) }
    var addDialog by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(book?.id, state.chaptersLoaded, state.chapters, state.length) {
        if (book == null) {
            base = null
            report = null
            loading = false
            return@LaunchedEffect
        }
        if (!state.chaptersLoaded) {
            loading = true
            actions.onEnsureChapters()
            return@LaunchedEffect
        }
        val computed = withContext(Dispatchers.Default) {
            SmartToc.evaluate(state.chapters.map { SmartChapter(it.offset, it.title, it.source, it.confidence) })
        }
        base = computed
        report = store.apply(computed, store.load(book.id, state.length))
        loading = false
    }

    ModalBottomSheet(onDismissRequest = actions.onClosePanel) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.smart_toc), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    report?.let { Text(stringResource(R.string.smart_toc_quality, it.score, it.chapters.size, it.anomalyCount), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                IconButton(onClick = { addDialog = true }) { Icon(Icons.Default.Add, stringResource(R.string.toc_add_here)) }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            report?.let { value ->
                if (value.anomalyCount > 0) AssistChip(onClick = {}, label = { Text(stringResource(R.string.smart_toc_anomalies, value.duplicateTitles, value.numericGaps, value.suspiciousTitles)) })
                LazyColumn(Modifier.heightIn(max = 560.dp)) {
                    items(value.chapters, key = { it.offset }) { chapter ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { actions.onJump(chapter.offset) }, modifier = Modifier.weight(1f)) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(chapter.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (chapter.source != "core") Text(stringResource(R.string.toc_source_user_or_special), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            IconButton(onClick = {
                                if (book != null) {
                                    store.hide(book.id, chapter.offset, state.length)
                                    report = base?.let { store.apply(it, store.load(book.id, state.length)) }
                                }
                            }) { Icon(Icons.Default.Delete, stringResource(R.string.toc_hide_heading)) }
                        }
                        HorizontalDivider()
                    }
                }
                TextButton(onClick = {
                    if (book != null) { store.reset(book.id); report = base }
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.toc_reset_repairs)) }
            }
        }
    }

    if (addDialog && book != null) AlertDialog(
        onDismissRequest = { addDialog = false },
        title = { Text(stringResource(R.string.toc_add_here)) },
        text = { OutlinedTextField(value = title, onValueChange = { title = it.take(80) }, label = { Text(stringResource(R.string.toc_custom_title)) }) },
        confirmButton = { TextButton(onClick = {
            store.add(book.id, state.position, title, state.length)
            report = base?.let { store.apply(it, store.load(book.id, state.length)) }
            title = ""; addDialog = false
        }, enabled = title.isNotBlank()) { Text(stringResource(R.string.toc_add_action)) } },
        dismissButton = { TextButton(onClick = { addDialog = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

private data class LabCandidate(
    val raw: ReaderController.NoiseCandidate,
    val feedback: SmartCleanFeedback,
)

@Composable
internal fun SmartCleanLabSheet(state: AppUiState, actions: JingduActions) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val book = state.currentBook
    val feedbackStore = remember(book?.id) { SmartCleanFeedbackStore(context) }
    var candidates by remember(book?.id) { mutableStateOf<List<LabCandidate>>(emptyList()) }
    var loading by remember(book?.id) { mutableStateOf(true) }
    var error by remember(book?.id) { mutableStateOf<String?>(null) }
    var loadRequest by remember(book?.id) { mutableIntStateOf(0) }

    suspend fun load() {
        if (book == null) {
            candidates = emptyList()
            error = null
            loading = false
            return
        }
        loading = true
        error = null
        try {
            candidates = withContext(Dispatchers.IO) {
                val repo = BookRepository(context)
                val source = repo.list().firstOrNull { it.id == book.id } ?: return@withContext emptyList()
                ReaderController().use { reader ->
                    reader.open(repo.normalizedFile(source), source.progress)
                    reader.noiseCandidates().map { candidate ->
                        LabCandidate(
                            raw = candidate,
                            feedback = feedbackStore.decision(book.id, candidate.reason, candidate.text),
                        )
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ProductErrorLog(context).record(ProductErrorCode.INTERNAL_OPERATION_FAILED, "smart-clean-review")
            candidates = emptyList()
            error = resources.getString(R.string.smart_clean_load_failed)
        } finally {
            loading = false
        }
    }

    fun record(candidate: LabCandidate, decision: SmartCleanFeedback) {
        val currentBook = book ?: return
        feedbackStore.record(currentBook.id, candidate.raw.reason, candidate.raw.text, decision)
        candidates = candidates.map { current ->
            if (current.raw.reason == candidate.raw.reason && current.raw.text == candidate.raw.text) current.copy(feedback = decision) else current
        }
        if (decision == SmartCleanFeedback.DELETE) actions.onAddRule(RepairRuleMode.LITERAL, candidate.raw.text, "")
    }

    LaunchedEffect(book?.id, loadRequest) { load() }

    ModalBottomSheet(onDismissRequest = actions.onClosePanel, sheetGesturesEnabled = false) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f),
            contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 36.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Psychology, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.smart_clean4), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.smart_clean4_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { message ->
                item {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(message, color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = { loadRequest++ }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Refresh, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.rescan_noise))
                        }
                    }
                }
            }
            if (!loading && error == null && candidates.isEmpty()) item { Text(stringResource(R.string.no_noise_found), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(candidates.take(40), key = { it.raw.reason + "\u001f" + it.raw.text }) { candidate ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.smart_clean4_signal, candidate.raw.count), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(candidate.raw.text, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        if (candidate.feedback != SmartCleanFeedback.NONE) Text(stringResource(R.string.smart_clean4_memory), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(onClick = { record(candidate, SmartCleanFeedback.KEEP) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.keep_text)) }
                            OutlinedButton(onClick = { record(candidate, SmartCleanFeedback.PROTECT) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.protect_text)) }
                            Button(onClick = { record(candidate, SmartCleanFeedback.DELETE) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.delete_and_remember)) }
                        }
                    }
                }
            }
            item { OutlinedButton(onClick = { actions.onOpenPanel(ReaderPanel.CLEAN) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.open_standard_clean)) } }
        }
    }
}

@Composable
internal fun PrivacySheet(state: AppUiState, actions: JingduActions) {
    val context = LocalContext.current
    val feedback = remember { SmartCleanFeedbackStore(context) }
    val folders = remember { FolderLibraryStore(context) }
    val audit = remember(state.books.size) { PrivacyAudit.inspect(context, state.books.size, folders.roots().size, feedback.summary()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri, "w")?.use { output -> output.write(PrivacyAudit.toJson(context, audit).toByteArray(Charsets.UTF_8)) }
        }
    }
    ModalBottomSheet(onDismissRequest = actions.onClosePanel) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column { Text(stringResource(R.string.privacy_verification), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold); Text(stringResource(R.string.privacy_verification_body), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            PrivacyFact(stringResource(R.string.privacy_no_internet), audit.networkPermissionAbsent)
            PrivacyFact(stringResource(R.string.privacy_auto_backup_disabled), audit.automaticBackupDisabled)
            PrivacyFact(stringResource(R.string.privacy_no_upload), !audit.bookTextUploadCapability)
            PrivacyFact(stringResource(R.string.privacy_no_analytics), !audit.analyticsSdkPresent)
            PrivacyFact(stringResource(R.string.privacy_no_ads), !audit.adsSdkPresent)
            Text(stringResource(R.string.privacy_local_counts, audit.libraryBooks, audit.folderRoots, audit.feedbackKeep + audit.feedbackDelete + audit.feedbackProtect), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { launcher.launch("jingdu-privacy-audit.json") }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_privacy_audit)) }
        }
    }
}

@Composable private fun PrivacyFact(label: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(stringResource(if (ok) R.string.verified else R.string.not_verified), color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
    }
}