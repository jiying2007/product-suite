package com.junchen.jingdu

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.roundToLong

class MainActivity : ComponentActivity() {
    private val readerViewModel: ReaderViewModel by viewModels()
    private val main = Handler(Looper.getMainLooper())
    private val workers: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "jingdu-worker").apply { isDaemon = true }
    }
    private val progressWorkers: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "jingdu-progress").apply { isDaemon = true }
    }
    private val tocWorkers: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread({
            runCatching { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND) }
            runnable.run()
        }, "jingdu-toc").apply { isDaemon = true }
    }
    private val workGeneration = AtomicLong()
    private val session = ReaderSession()
    private val motionController = ReaderMotionController()

    private lateinit var repository: BookRepository
    private lateinit var ttsCatalog: TtsController
    private lateinit var readerPreferences: ReaderPreferences
    private lateinit var ruleLibrary: RuleLibrary
    private lateinit var userBackup: UserBackup
    private lateinit var reviewPrompter: ReviewPrompter
    private lateinit var billing: BillingManager
    private lateinit var cleanHistory: CleanHistory
    private lateinit var libraryMetadata: LibraryMetadataStore
    private lateinit var smartCleanFeedback: SmartCleanFeedbackStore
    private lateinit var annotationStore: ReaderAnnotationStore
    private lateinit var fontStore: ReaderFontStore
    private lateinit var statsStore: ReaderStatsStore
    private lateinit var smartTocCache: SmartTocCacheStore
    @Volatile private var proUnlocked = false
    @Volatile private var chapterWorkKey: String? = null
    private var reader: ReaderController
        get() = session.reader
        set(value) { session.reader = value }
    private var currentBook: BookRepository.Book?
        get() = session.book
        set(value) { session.book = value }
    private var cleanMode: Boolean
        get() = session.cleanMode
        set(value) { session.cleanMode = value }
    private var visiblePageChars: Long
        get() = session.visiblePageChars
        set(value) { session.visiblePageChars = value }
    private val pageHistory get() = session.pageHistory
    private var pendingExport: File? = null
    private var lastProgressPersistAt = 0L
    private var lastProgressPersistPosition = -1L
    private var consumedReaderPageKey = KeyEvent.KEYCODE_UNKNOWN
    private var uiState: AppUiState
        get() = readerViewModel.state.value
        set(value) { readerViewModel.replace(value) }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { importUri(it) } }
    private val batchImportLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) batchImportUris(uris) }
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val source = pendingExport
        pendingExport = null
        if (uri != null && source != null) exportFile(source, uri)
    }
    private val ruleImportLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::importGlobalRulesFromUri) }
    private val ruleExportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(::exportGlobalRulesToUri) }
    private val backupImportLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::importBackupFromUri) }
    private val backupExportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(::exportBackupToUri) }
    private val fontImportLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runWork(
            label = getString(R.string.busy_import),
            task = { fontStore.import(uri, this) },
            success = { id -> updateSettings(uiState.settings.copy(typeface = ReaderTypeface.CUSTOM, customFontId = id, preset = ReaderPreset.CUSTOM)); showMessage(getString(R.string.reader_font_imported)) },
            errorTitle = getString(R.string.reader_import_font),
        )
    }

    private val ttsStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action != TtsPlaybackService.ACTION_STATE) return
            val active = intent.getBooleanExtra(TtsPlaybackService.EXTRA_ACTIVE, false)
            val playing = intent.getBooleanExtra(TtsPlaybackService.EXTRA_PLAYING, false)
            val offset = intent.getLongExtra(TtsPlaybackService.EXTRA_OFFSET, -1L)
            val nextOffset = intent.getLongExtra(TtsPlaybackService.EXTRA_NEXT_OFFSET, -1L)
            val rangeStart = intent.getLongExtra(TtsPlaybackService.EXTRA_RANGE_START, -1L)
            val rangeEnd = intent.getLongExtra(TtsPlaybackService.EXTRA_RANGE_END, -1L)
            val reason = intent.getStringExtra(TtsPlaybackService.EXTRA_REASON)
            ReaderInteractionRuntime.backgroundTtsPlaying = playing
            if (active) motionController.start(ReaderMotionState.TTS)
            else if (motionController.state == ReaderMotionState.TTS) motionController.stop()
            uiState = uiState.copy(
                motion = motionController.state,
                tts = TtsPlaybackModel(active, playing, offset, nextOffset, rangeStart, rangeEnd, reason),
            )
            if (active && offset >= 0 && uiState.screen == AppScreen.READER && !cleanMode) syncTtsPosition(offset)
            if (reason != null && reason !in setOf("paused", "focus-paused", "user", "stopped", "destroyed", "end")) showMessage(ttsReason(reason))
        }
    }

    private val actions by lazy {
        JingduActions(
            onImport = { importLauncher.launch(arrayOf("text/plain", "text/*", "application/octet-stream")) },
            onBatchImport = { batchImportLauncher.launch(arrayOf("text/plain", "text/*", "application/octet-stream")) },
            onOpenBook = ::openBookById,
            onDeleteLibraryBook = ::deleteLibraryBook,
            onToggleFavorite = ::toggleFavorite,
            onSetBookTags = ::setBookTags,
            onBackToLibrary = ::backToLibrary,
            onNavigatePrevious = { navigatePrevious(userInitiated = true) },
            onNavigateNext = { navigateNext(userInitiated = true) },
            onSeekFraction = ::seekFraction,
            onVisibleCharsChanged = { visiblePageChars = it.coerceAtLeast(ReaderController.MIN_PAGE_CHARS) },
            onOpenPanel = ::openPanel,
            onClosePanel = ::closePanel,
            onSearchQueryChanged = { uiState = uiState.copy(searchQuery = it) },
            onSearch = ::search,
            onJump = ::jumpTo,
            onSyncTtsPosition = ::syncTtsPosition,
            onEnsureChapters = ::ensureChapters,
            onAddBookmark = ::addBookmark,
            onDeleteBookmark = ::deleteBookmark,
            onAddAnnotation = ::addAnnotation,
            onDeleteAnnotation = ::deleteAnnotation,
            onImportFont = { fontImportLauncher.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream")) },
            onAddRule = ::addRule,
            onDeleteRule = ::deleteRule,
            onClearRules = ::clearRules,
            onAnalyzeSmartClean = ::analyzeSmartClean,
            onToggleNoiseCandidate = ::toggleNoiseCandidate,
            onApplySmartClean = ::applySmartClean,
            onUndoSmartClean = ::undoSmartClean,
            onAddGlobalRule = ::addGlobalRule,
            onDeleteGlobalRule = ::deleteGlobalRule,
            onClearGlobalRules = ::clearGlobalRules,
            onInstallRecommendedRules = ::installRecommendedRules,
            onExportGlobalRules = ::exportGlobalRules,
            onImportGlobalRules = ::importGlobalRules,
            onUpgradePro = { billing.purchase() },
            onRestorePro = { billing.restore() },
            onExportBackup = ::exportBackup,
            onImportBackup = ::importBackup,
            onToggleCleanPreview = ::toggleCleanPreview,
            onExportClean = ::exportClean,
            onEncodingSelected = ::redecode,
            onSettingsChanged = ::updateSettings,
            onToggleTts = ::toggleTts,
            onToggleAutoPaging = ::toggleAutoPaging,
            onSleepTimer = ::setSleepTimer,
            onRequestDeleteCurrent = { uiState = uiState.copy(deleteConfirmation = true) },
            onDismissDelete = { uiState = uiState.copy(deleteConfirmation = false) },
            onConfirmDeleteCurrent = ::deleteCurrentBook,
            onMessageConsumed = { uiState = uiState.copy(message = null) },
        )
    }

    private val autoStep = object : Runnable {
        override fun run() {
            if (!motionController.isActive(ReaderMotionState.AUTO_PAGE) || uiState.busyLabel != null || currentBook == null) return
            if (reader.position() >= (reader.length() - 1).coerceAtLeast(0)) {
                stopAutoPaging()
                return
            }
            navigateNext(userInitiated = false)
            val delay = motionController.adaptivePageDelayMs(visiblePageChars, statsStore.charsPerMinute(), uiState.settings)
            main.postDelayed(this, delay)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository = BookRepository(this)
        readerPreferences = ReaderPreferences(this)
        ruleLibrary = RuleLibrary(this)
        reviewPrompter = ReviewPrompter(this)
        cleanHistory = CleanHistory(this)
        libraryMetadata = LibraryMetadataStore(this)
        smartCleanFeedback = SmartCleanFeedbackStore(this)
        annotationStore = ReaderAnnotationStore(this)
        fontStore = ReaderFontStore(this)
        statsStore = ReaderStatsStore(this)
        smartTocCache = SmartTocCacheStore(this)
        ttsCatalog = TtsController(this)
        userBackup = UserBackup(readerPreferences, ruleLibrary, annotationStore)
        uiState = uiState.copy(globalRules = ruleLibrary.load())
        refreshLibrary()
        ContextCompat.registerReceiver(this, ttsStateReceiver, IntentFilter(TtsPlaybackService.ACTION_STATE), ContextCompat.RECEIVER_NOT_EXPORTED)

        billing = BillingManager(
            activity = this,
            onState = { state ->
                val wasUnlocked = proUnlocked
                proUnlocked = state.unlocked
                uiState = uiState.copy(
                    proUnlocked = state.unlocked,
                    proAvailable = state.available,
                    proConnected = state.connected,
                    proPrice = state.price,
                    globalRules = ruleLibrary.load(),
                )
                if (!wasUnlocked && state.unlocked) {
                    refreshTtsVoices()
                    if (cleanMode) currentBook?.let { openBook(it, clean = true) }
                }
            },
            onMessage = ::showMessage,
        )
        billing.start()
        setContent {
            val state by readerViewModel.state.collectAsStateWithLifecycle()
            val location by readerViewModel.location.collectAsStateWithLifecycle()
            JingduApp(
                state = state, actions = actions, location = location, hotPanel = readerViewModel.hotPanel,
                onTrackLocation = { current, target, length -> readerViewModel.trackLocation(current, target, length) },
                onLocationBack = { readerViewModel.backTarget(readerViewModel.state.value.position)?.let(::jumpTo) },
                onLocationForward = { readerViewModel.forwardTarget(readerViewModel.state.value.position)?.let(::jumpTo) },
            )
        }
        workers.execute {
            val settings = readerPreferences.load()
            main.post {
                if (isDestroyed) return@post
                ttsCatalog.setRate(settings.ttsRate); ttsCatalog.setPitch(settings.ttsPitch); ttsCatalog.setVoiceName(settings.ttsVoiceName)
                uiState = uiState.copy(settings = settings)
                refreshTtsVoices()
            }
        }
        startService(Intent(this, TtsPlaybackService::class.java).setAction(TtsPlaybackService.ACTION_STATE))
        if (savedInstanceState == null) handleIncomingIntent(intent) else restoreSession(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        if (::billing.isInitialized) billing.start()
        currentBook?.let { statsStore.begin(it.id, reader.position()) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val book = currentBook
        if (uiState.screen == AppScreen.READER && book != null) {
            outState.putString(STATE_BOOK_ID, book.id)
            outState.putString(STATE_NORMALIZED_SHA, book.normalizedSha256)
            outState.putLong(STATE_POSITION, reader.position())
            outState.putBoolean(STATE_CLEAN_MODE, cleanMode)
        }
        pendingExport?.absolutePath?.let { outState.putString(STATE_PENDING_EXPORT, it) }
        super.onSaveInstanceState(outState)
    }

    private fun restoreSession(state: Bundle) {
        state.getString(STATE_PENDING_EXPORT)?.let { path -> File(path).takeIf(File::isFile)?.let { pendingExport = it } }
        val id = state.getString(STATE_BOOK_ID) ?: return
        val book = findBook(id) ?: return
        val sameRevision = state.getString(STATE_NORMALIZED_SHA) == book.normalizedSha256
        val restoredPosition = if (sameRevision) state.getLong(STATE_POSITION, book.progress) else book.progress
        openBook(book = book, clean = state.getBoolean(STATE_CLEAN_MODE, false), restoredOverride = restoredPosition)
    }

    private fun handleIncomingIntent(intent: Intent?) { incomingUri(intent)?.let { importUri(it) } }

    private fun incomingUri(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        else -> null
    }

    private fun refreshLibrary() { uiState = uiState.copy(books = repository.list().map(::toCard)) }

    private fun toCard(book: BookRepository.Book): BookCardModel {
        val metadata = libraryMetadata.load(book.id)
        return BookCardModel(
            id = book.id, name = book.name, encoding = book.encoding, sizeBytes = book.size,
            progress = book.progress, charCount = book.charCount, touchedAt = book.touchedAt,
            normalizedSha256 = book.normalizedSha256, favorite = metadata.favorite, tags = metadata.tags,
        )
    }

    private fun findBook(id: String): BookRepository.Book? = repository.list().firstOrNull { it.id == id }

    private fun toggleFavorite(id: String) {
        if (findBook(id) == null) return
        libraryMetadata.toggleFavorite(id)
        refreshLibrary()
    }

    private fun setBookTags(id: String, tags: String) {
        if (findBook(id) == null) return
        libraryMetadata.setTags(id, tags)
        refreshLibrary()
    }

    private fun importUri(uri: Uri) {
        runWork(
            label = getString(R.string.busy_import),
            task = { repository.importUri(uri, BookRepository.AUTO) },
            success = { openBook(it, clean = false) },
            errorTitle = getString(R.string.error_import),
        )
    }

    private fun batchImportUris(uris: List<Uri>) {
        val selected = uris.take(MAX_BATCH_IMPORT_FILES)
        runWork(
            label = getString(R.string.busy_batch_import, selected.size),
            task = {
                var imported = 0
                var failed = 0
                selected.forEach { uri ->
                    try { repository.importUri(uri, BookRepository.AUTO); imported++ } catch (_: Throwable) { failed++ }
                }
                imported to failed
            },
            success = { (imported, failed) ->
                refreshLibrary()
                showMessage(buildString {
                    append(getString(R.string.batch_import_result, imported))
                    if (failed > 0) append(getString(R.string.batch_import_failed_suffix, failed))
                    if (uris.size > selected.size) append(getString(R.string.batch_import_limit_suffix, MAX_BATCH_IMPORT_FILES))
                })
            },
            errorTitle = getString(R.string.error_batch_import),
        )
    }

    private fun openBookById(id: String) {
        val book = findBook(id) ?: return showMessage(getString(R.string.private_copy_missing))
        openBook(book, clean = false)
    }

    private fun openBook(book: BookRepository.Book, clean: Boolean, restoredOverride: Long? = null) {
        if (uiState.busyLabel != null) return
        stopAllMotion()

        val previousBook = currentBook
        val previousReader = reader
        val previousClean = cleanMode
        val previousPosition = previousBook?.let { previousReader.position() } ?: 0L
        val restored = restoredOverride ?: if (clean) 0L else if (
            previousBook != null && previousBook.id == book.id &&
            previousBook.normalizedSha256 == book.normalizedSha256 && !previousClean
        ) previousPosition else book.progress

        if (previousBook != null && !previousClean) repository.saveProgress(previousBook, previousPosition)
        val token = workGeneration.incrementAndGet()
        chapterWorkKey = null
        uiState = uiState.copy(
            screen = AppScreen.READER,
            busyLabel = getString(if (clean) R.string.busy_clean_preview else R.string.busy_open),
            panel = null,
            searchQuery = "",
            searchResults = emptyList(),
            chapters = emptyList(),
            chaptersLoaded = false,
            bookmarks = emptyList(),
            repairRules = emptyList(),
            globalRules = ruleLibrary.load(),
            noiseCandidates = emptyList(),
            smartCleanAnalyzed = false,
            smartCleanUndoAvailable = cleanHistory.has(book.id),
        )

        workers.execute {
            val candidate = ReaderController()
            try {
                val file = if (clean) buildClean(book) else repository.normalizedFile(book)
                candidate.open(file, restored)
                if (!clean) {
                    runCatching {
                        prewarmReaderSmartChaptersPanel(
                            applicationContext,
                            book.id,
                            book.normalizedSha256,
                            candidate.length(),
                        )
                    }
                }
                if (token != workGeneration.get() || isDestroyed) { candidate.close(); return@execute }
                main.post {
                    if (token != workGeneration.get() || isDestroyed) { candidate.close(); return@post }
                    reader = candidate
                    currentBook = book
                    cleanMode = clean
                    pageHistory.clear()
                    lastProgressPersistAt = 0L
                    lastProgressPersistPosition = candidate.position()
                    repository.updateCharCount(book, candidate.length())
                    repository.pruneDocumentRevisions(book)
                    repository.pruneCleanRevisions(book, if (clean) file else null)
                    NativeIndexCache.pruneOrphans(repository.normalizedFile(book).parentFile)
                    previousReader.close()
                    uiState = uiState.copy(busyLabel = null, currentBook = toCard(book), cleanMode = clean, smartCleanUndoAvailable = cleanHistory.has(book.id))
                    statsStore.begin(book.id, candidate.position())
                    refreshAnnotations()
                    render()
                    refreshLibrary()
                    if (!clean && previousBook?.id != book.id) reviewPrompter.recordBookOpened()
                }
            } catch (error: Throwable) {
                candidate.close()
                main.post {
                    if (token != workGeneration.get() || isDestroyed) return@post
                    currentBook = previousBook
                    cleanMode = previousClean
                    uiState = uiState.copy(
                        busyLabel = null,
                        screen = if (previousBook == null) AppScreen.LIBRARY else AppScreen.READER,
                        currentBook = previousBook?.let(::toCard),
                        cleanMode = previousClean,
                        message = friendlyError(getString(if (clean) R.string.error_clean else R.string.error_open), error),
                    )
                    if (previousBook != null) render()
                }
            }
        }
    }

    private fun render(pageTurnDirection: Int = 0) {
        val book = currentBook ?: return
        if (uiState.busyLabel != null) return
        try {
            val text = reader.page()
            val position = reader.position()
            val length = reader.length()
            ReaderInteractionRuntime.foregroundPosition = position
            if (!cleanMode) persistProgress(book, position = position)
            statsStore.mark(book.id, position)
            val card = uiState.currentBook
                ?.takeIf { it.id == book.id && it.normalizedSha256 == book.normalizedSha256 }
                ?.copy(progress = position, charCount = length)
                ?: toCard(book).copy(progress = position, charCount = length)
            uiState = uiState.copy(
                screen = AppScreen.READER, currentBook = card, pageText = text,
                position = position, pageTurnDirection = pageTurnDirection, length = length, cleanMode = cleanMode,
            )
        } catch (error: Throwable) {
            showMessage(friendlyError(getString(R.string.error_read), error))
        }
    }

    private fun persistProgress(book: BookRepository.Book, force: Boolean = false, position: Long = reader.position()) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastProgressPersistAt < PROGRESS_SAVE_INTERVAL_MS &&
            abs(position - lastProgressPersistPosition) < PROGRESS_SAVE_CHAR_DELTA) return
        lastProgressPersistAt = now
        lastProgressPersistPosition = position
        if (force) {
            runCatching { progressWorkers.submit { repository.saveProgress(book, position) }.get() }
                .getOrElse { repository.saveProgress(book, position) }
        } else {
            progressWorkers.execute { runCatching { repository.saveProgress(book, position) } }
        }
    }

    private fun publishPositionOnly(book: BookRepository.Book, position: Long) {
        val length = reader.length()
        persistProgress(book, position = position)
        statsStore.mark(book.id, position)
        val card = uiState.currentBook
            ?.takeIf { it.id == book.id && it.normalizedSha256 == book.normalizedSha256 }
            ?.copy(progress = position, charCount = length)
            ?: toCard(book).copy(progress = position, charCount = length)
        ReaderInteractionRuntime.foregroundPosition = position
        uiState = uiState.copy(currentBook = card, position = position, pageTurnDirection = 0, length = length)
    }

    private fun navigateNext(userInitiated: Boolean) {
        if (uiState.busyLabel != null || currentBook == null) return
        if (userInitiated) stopAllMotion()
        val current = reader.position()
        if (current >= (reader.length() - 1).coerceAtLeast(0)) return
        pageHistory.addLast(current)
        reader.move(visiblePageChars.coerceAtLeast(ReaderController.MIN_PAGE_CHARS))
        render(pageTurnDirection = 1)
    }

    private fun navigatePrevious(userInitiated: Boolean) {
        if (uiState.busyLabel != null || currentBook == null) return
        if (userInitiated) stopAllMotion()
        if (pageHistory.isNotEmpty()) reader.jump(pageHistory.removeLast()) else reader.move(-visiblePageChars.coerceAtLeast(ReaderController.MIN_PAGE_CHARS))
        render(pageTurnDirection = -1)
    }

    private fun seekFraction(fraction: Float) {
        if (uiState.busyLabel != null || currentBook == null || reader.length() <= 0) return
        stopAllMotion(); pageHistory.clear()
        reader.jump((reader.length().toDouble() * fraction.coerceIn(0f, 1f)).toLong())
        render()
    }

    private fun jumpTo(offset: Long) {
        if (uiState.busyLabel != null || currentBook == null) return
        stopAllMotion(); pageHistory.clear(); reader.jump(offset)
        readerViewModel.closeHotPanel()
        uiState = uiState.copy(panel = null)
        render()
    }

    private fun syncTtsPosition(offset: Long) {
        val book = currentBook ?: return
        if (uiState.busyLabel != null || cleanMode || reader.length() <= 0) return
        val bounded = offset.coerceIn(0L, (reader.length() - 1).coerceAtLeast(0L))
        if (bounded == reader.position()) return
        reader.jump(bounded)
        if (uiState.settings.readingMode == ReaderMode.CONTINUOUS && !uiState.tts.active) publishPositionOnly(book, bounded)
        else render()
    }

    private fun backToLibrary() {
        readerViewModel.hotPanel.value?.let { readerViewModel.closeHotPanel(); return }
        uiState.panel?.let { uiState = uiState.copy(panel = null); return }
        val book = currentBook
        if (book != null && !cleanMode) persistProgress(book, force = true)
        stopAllMotion(); reader.close()
        currentBook = null; cleanMode = false; pageHistory.clear(); chapterWorkKey = null; refreshLibrary()
        uiState = uiState.copy(
            screen = AppScreen.LIBRARY, currentBook = null, pageText = "", position = 0, length = 0,
            cleanMode = false, panel = null, busyLabel = null, searchQuery = "", searchResults = emptyList(),
            chapters = emptyList(), chaptersLoaded = false, bookmarks = emptyList(), repairRules = emptyList(),
            globalRules = ruleLibrary.load(), noiseCandidates = emptyList(), smartCleanAnalyzed = false,
            smartCleanUndoAvailable = false,
        )
    }

    private fun closePanel() {
        if (readerViewModel.hotPanel.value != null) readerViewModel.closeHotPanel()
        else uiState = uiState.copy(panel = null)
    }

    private fun openPanel(panel: ReaderPanel) {
        if (uiState.busyLabel != null || currentBook == null) return
        stopAutoScroll()
        if (panel == ReaderPanel.QUICK_SETTINGS || panel == ReaderPanel.CHAPTERS) {
            readerViewModel.openHotPanel(panel)
            return
        }
        readerViewModel.closeHotPanel()
        uiState = uiState.copy(panel = panel)
        when (panel) {
            ReaderPanel.BOOKMARKS -> refreshBookmarks()
            ReaderPanel.CLEAN -> refreshRules()
            ReaderPanel.SETTINGS -> refreshTtsVoices()
            else -> Unit
        }
    }

    private fun search(query: String) {
        val value = query.trim()
        if (value.isEmpty() || currentBook == null) return
        runWork(
            label = getString(R.string.busy_search),
            task = { reader.search(value) },
            success = { hits ->
                uiState = uiState.copy(
                    panel = ReaderPanel.SEARCH,
                    searchResults = hits.map { SearchResultModel(it.offset, it.context) },
                    message = if (hits.isEmpty()) getString(R.string.search_no_result, value) else null,
                )
            },
            errorTitle = getString(R.string.error_search),
        )
    }

    private fun ensureChapters() {
        val book = currentBook ?: return
        if (uiState.chaptersLoaded || cleanMode) return
        val length = reader.length()
        if (length <= 0) return
        val revision = book.normalizedSha256
        val position = reader.position()
        val key = "${book.id}:$revision:$length"
        if (chapterWorkKey == key) return
        chapterWorkKey = key
        tocWorkers.execute {
            try {
                val base = smartTocCache.load(book.id, revision, length) ?: ReaderController().use { source ->
                    source.open(repository.normalizedFile(book), position)
                    SmartToc.analyze(source).also { report -> smartTocCache.save(book.id, revision, length, report) }
                }
                val overrides = TocOverrideStore(this).load(book.id, length)
                val report = TocOverrideStore(this).apply(base, overrides)
                // Materialize the potentially large chapter projection on the background TOC worker.
                // The main thread only publishes an already-built immutable list.
                val chapterModels = report.chapters.map { ChapterModel(it.offset, it.title, it.source, it.confidence) }
                main.post {
                    if (chapterWorkKey == key) chapterWorkKey = null
                    val active = currentBook
                    if (isDestroyed || active == null || active.id != book.id || active.normalizedSha256 != revision || cleanMode) return@post
                    uiState = uiState.copy(
                        chaptersLoaded = true,
                        chapters = chapterModels,
                    )
                }
            } catch (error: Throwable) {
                main.post {
                    if (chapterWorkKey == key) chapterWorkKey = null
                    if (!isDestroyed && currentBook?.id == book.id && readerViewModel.hotPanel.value == ReaderPanel.CHAPTERS) {
                        showMessage(friendlyError(getString(R.string.chapters), error))
                    }
                }
            }
        }
    }

    private fun refreshAnnotations() {
        val book = currentBook ?: return
        val values = annotationStore.list(book.id)
        val length = reader.length().coerceAtLeast(1)
        uiState = uiState.copy(
            annotations = values,
            bookmarks = values.filter { it.kind == ReaderAnnotationKind.BOOKMARK }
                .map { BookmarkModel(it.sourceStart.coerceIn(0, length - 1), (it.sourceStart.toDouble() / length.toDouble()).toFloat().coerceIn(0f, 1f)) },
        )
    }

    private fun refreshBookmarks() = refreshAnnotations()

    private fun addBookmark() {
        val book = currentBook ?: return
        if (cleanMode) return showMessage(getString(R.string.bookmark_clean_blocked))
        annotationStore.addBookmark(book.id, reader.position())
        refreshAnnotations()
        showMessage(getString(R.string.bookmark_added))
    }

    private fun deleteBookmark(offset: Long) {
        val book = currentBook ?: return
        annotationStore.deleteBookmark(book.id, offset)
        refreshAnnotations()
    }

    private fun addAnnotation(start: Long, end: Long, kind: ReaderAnnotationKind, style: ReaderHighlightStyle, note: String, excerpt: String) {
        val book = currentBook ?: return
        if (cleanMode || kind == ReaderAnnotationKind.BOOKMARK) return
        annotationStore.upsertRange(book.id, start, end, kind, style, note, excerpt)
        refreshAnnotations()
        showMessage(getString(R.string.reader_annotation_added))
    }

    private fun deleteAnnotation(id: String) {
        val book = currentBook ?: return
        annotationStore.delete(book.id, id)
        refreshAnnotations()
    }

    private fun rulesKey(book: BookRepository.Book) = "rules.${book.id}"
    private fun bookRulesPacked(book: BookRepository.Book): String = getPreferences(MODE_PRIVATE).getString(rulesKey(book), "") ?: ""
    private fun bookRules(book: BookRepository.Book): List<RepairRule> = RuleCodec.parse(bookRulesPacked(book))
    private fun effectiveRules(book: BookRepository.Book): List<RepairRule> = RuleCodec.combined(bookRules(book), ruleLibrary.load(), proUnlocked)
    private fun effectivePackedRules(book: BookRepository.Book): String = RuleCodec.pack(effectiveRules(book))

    private fun refreshRules() {
        val book = currentBook ?: return
        uiState = uiState.copy(repairRules = bookRules(book), globalRules = ruleLibrary.load(), smartCleanUndoAvailable = cleanHistory.has(book.id))
    }

    private fun saveRules(rules: List<RepairRule>, rebuildIfClean: Boolean = true, preserveSmartCleanUndo: Boolean = false) {
        val book = currentBook ?: return
        val valid = rules.filter(RuleCodec::isValid).distinctBy { Triple(it.mode, it.find, it.replacement) }.take(500)
        getPreferences(MODE_PRIVATE).edit().putString(rulesKey(book), RuleCodec.pack(valid)).apply()
        if (!preserveSmartCleanUndo) cleanHistory.clear(book.id)
        uiState = uiState.copy(repairRules = valid, smartCleanUndoAvailable = preserveSmartCleanUndo && cleanHistory.has(book.id))
        if (rebuildIfClean && cleanMode) openBook(book, clean = true)
    }

    private fun addRule(mode: RepairRuleMode, find: String, replacement: String) {
        if (mode == RepairRuleMode.LINE_GLOB && !proUnlocked) { billing.purchase(); return }
        val rule = RepairRule(find.trim(), replacement, mode)
        if (!RuleCodec.isValid(rule)) return showMessage(getString(R.string.invalid_rule))
        if (mode == RepairRuleMode.LINE_GLOB && '*' !in rule.find) return showMessage(getString(R.string.glob_requires_star))
        saveRules(uiState.repairRules + rule)
    }

    private fun deleteRule(index: Int) {
        if (index !in uiState.repairRules.indices) return
        saveRules(uiState.repairRules.filterIndexed { i, _ -> i != index })
    }

    private fun clearRules() { saveRules(emptyList()) }

    private fun analyzeSmartClean() {
        val book = currentBook ?: return
        runWork(
            label = getString(R.string.busy_smart_clean),
            task = { ReaderController().use { source -> source.open(repository.normalizedFile(book), 0); source.noiseCandidates() } },
            success = { candidates ->
                uiState = uiState.copy(
                    panel = ReaderPanel.CLEAN,
                    smartCleanAnalyzed = true,
                    smartCleanUndoAvailable = cleanHistory.has(book.id),
                    noiseCandidates = candidates.map { candidate -> smartCleanModel(book.id, candidate) },
                    message = if (candidates.isEmpty()) getString(R.string.smart_clean_empty) else null,
                )
            },
            errorTitle = getString(R.string.error_smart_clean),
        )
    }

    private fun smartCleanModel(bookId: String, candidate: ReaderController.NoiseCandidate): NoiseCandidateModel {
        val semantic = TinyLocalSemanticCandidateClassifier.classifyCandidate(candidate.text)
        val feedback = smartCleanFeedback.decision(bookId, candidate.reason, candidate.text)
        val semanticDelta = when (semantic.label) {
            SemanticCandidateLabel.AD -> if (semantic.confidence >= 0.65f) 8 else 0
            SemanticCandidateLabel.BODY -> if (semantic.confidence >= 0.65f) -16 else 0
            SemanticCandidateLabel.UNCERTAIN -> 0
        }
        val adjustedScore = (candidate.score + smartCleanFeedback.modelDelta(candidate.reason, candidate.text) + semanticDelta).coerceIn(0, 100)
        val model = NoiseCandidateModel(
            score = adjustedScore,
            count = candidate.count,
            reason = candidate.reason,
            text = candidate.text,
            semanticLabel = semantic.label,
            semanticConfidence = semantic.confidence,
            semanticScore = semantic.score,
            feedback = feedback,
        )
        return model.copy(selected = model.defaultSafeSelection)
    }

    private fun toggleNoiseCandidate(index: Int) {
        if (index !in uiState.noiseCandidates.indices) return
        uiState = uiState.copy(noiseCandidates = uiState.noiseCandidates.mapIndexed { i, candidate -> if (i == index) candidate.copy(selected = !candidate.selected) else candidate })
    }

    private fun applySmartClean() {
        val book = currentBook ?: return
        if (!proUnlocked) { billing.purchase(); return }
        val selected = uiState.noiseCandidates.filter { it.selected }
        if (selected.isEmpty()) return showMessage(getString(R.string.select_clean_suggestion))
        cleanHistory.save(book.id, bookRulesPacked(book))
        selected.forEach { candidate -> smartCleanFeedback.record(book.id, candidate.reason, candidate.text, SmartCleanFeedback.DELETE) }
        val additions = selected.map { RepairRule(it.text, "", RepairRuleMode.LITERAL) }
        val updated = (bookRules(book) + additions).distinctBy { Triple(it.mode, it.find, it.replacement) }.take(500)
        saveRules(updated, rebuildIfClean = false, preserveSmartCleanUndo = true)
        reviewPrompter.recordSmartCleanApplied()
        uiState = uiState.copy(panel = null, smartCleanUndoAvailable = true)
        openBook(book, clean = true)
    }

    private fun undoSmartClean() {
        val book = currentBook ?: return
        val snapshot = cleanHistory.peek(book.id) ?: return
        val restored = RuleCodec.parse(snapshot)
        cleanHistory.clear(book.id)
        saveRules(restored, rebuildIfClean = false, preserveSmartCleanUndo = true)
        uiState = uiState.copy(panel = null, smartCleanUndoAvailable = false)
        openBook(book, clean = true)
        showMessage(getString(R.string.smart_clean_undone))
    }

    private fun addGlobalRule(mode: RepairRuleMode, find: String, replacement: String) {
        if (!requirePro()) return
        val rule = RepairRule(find.trim(), replacement, mode)
        if (!RuleCodec.isValid(rule)) return showMessage(getString(R.string.invalid_rule))
        if (mode == RepairRuleMode.LINE_GLOB && '*' !in rule.find) return showMessage(getString(R.string.glob_requires_star))
        uiState = uiState.copy(globalRules = ruleLibrary.add(rule))
        if (cleanMode) currentBook?.let { openBook(it, clean = true) }
    }

    private fun deleteGlobalRule(index: Int) {
        if (!requirePro()) return
        uiState = uiState.copy(globalRules = ruleLibrary.remove(index))
        if (cleanMode) currentBook?.let { openBook(it, clean = true) }
    }

    private fun clearGlobalRules() {
        if (!requirePro()) return
        ruleLibrary.save(emptyList())
        uiState = uiState.copy(globalRules = emptyList())
        if (cleanMode) currentBook?.let { openBook(it, clean = true) }
    }

    private fun installRecommendedRules() {
        if (!requirePro()) return
        uiState = uiState.copy(globalRules = ruleLibrary.installRecommended())
        showMessage(getString(R.string.recommended_rules_added))
    }

    private fun exportGlobalRules() { if (requirePro()) ruleExportLauncher.launch("jingdu-global-clean-rules.json") }
    private fun importGlobalRules() { if (requirePro()) ruleImportLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) }

    private fun exportGlobalRulesToUri(uri: Uri) {
        runWork(
            label = getString(R.string.busy_export_rules),
            task = { writeUtf8(uri, ruleLibrary.exportJson()); true },
            success = { showMessage(getString(R.string.rules_exported)) },
            errorTitle = getString(R.string.error_rule_export),
        )
    }

    private fun importGlobalRulesFromUri(uri: Uri) {
        runWork(
            label = getString(R.string.busy_import_rules),
            task = { ruleLibrary.importJson(readLimitedUtf8(uri, MAX_RULE_IMPORT_BYTES)) },
            success = { rules -> uiState = uiState.copy(globalRules = rules, panel = ReaderPanel.CLEAN); showMessage(getString(R.string.rules_imported, rules.size)) },
            errorTitle = getString(R.string.error_rule_import),
        )
    }

    private fun exportBackup() { if (requirePro()) backupExportLauncher.launch("jingdu-local-settings-rules-backup.json") }
    private fun importBackup() { if (requirePro()) backupImportLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) }

    private fun exportBackupToUri(uri: Uri) {
        runWork(
            label = getString(R.string.busy_export_backup),
            task = { writeUtf8(uri, userBackup.exportJson()); true },
            success = { showMessage(getString(R.string.backup_exported)) },
            errorTitle = getString(R.string.error_backup_export),
        )
    }

    private fun importBackupFromUri(uri: Uri) {
        runWork(
            label = getString(R.string.busy_restore_backup),
            task = { userBackup.importJson(readLimitedUtf8(uri, MAX_BACKUP_BYTES)) },
            success = { result ->
                ttsCatalog.setRate(result.settings.ttsRate); ttsCatalog.setPitch(result.settings.ttsPitch); ttsCatalog.setVoiceName(result.settings.ttsVoiceName)
                refreshAnnotations()
                uiState = uiState.copy(settings = result.settings, globalRules = result.globalRules, panel = ReaderPanel.SETTINGS)
                refreshTtsVoices()
                showMessage(getString(R.string.backup_restored, result.globalRules.size))
            },
            errorTitle = getString(R.string.error_backup_restore),
        )
    }

    private fun readLimitedUtf8(uri: Uri, limit: Int): String {
        val input = contentResolver.openInputStream(uri) ?: throw IOException(getString(R.string.file_unreadable))
        input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                if (total > limit) throw IOException(getString(R.string.file_too_large))
                output.write(buffer, 0, count)
            }
            return output.toString(Charsets.UTF_8.name())
        }
    }

    private fun writeUtf8(uri: Uri, text: String) {
        contentResolver.openOutputStream(uri, "w").use { output ->
            if (output == null) throw IOException(getString(R.string.destination_unwritable))
            output.write(text.toByteArray(Charsets.UTF_8)); output.flush()
        }
    }

    private fun requirePro(): Boolean {
        if (proUnlocked) return true
        billing.purchase()
        return false
    }

    private fun buildClean(book: BookRepository.Book): File {
        val packed = effectivePackedRules(book)
        val revision = repository.repairRevision(book, packed)
        val output = repository.cleanFile(book, revision)
        if (output.isFile) return output
        ReaderController().use { source -> source.open(repository.normalizedFile(book), 0); source.exportRules(packed, output) }
        return output
    }

    private fun toggleCleanPreview() {
        val book = currentBook ?: return
        uiState = uiState.copy(panel = null)
        openBook(book, clean = !cleanMode)
    }

    private fun exportClean() {
        val book = currentBook ?: return
        runWork(
            label = getString(R.string.busy_generate_clean),
            task = { buildClean(book) },
            success = { file -> pendingExport = file; exportLauncher.launch("${stripTxt(book.name)}-${getString(R.string.clean)}.txt") },
            errorTitle = getString(R.string.error_prepare_export),
        )
    }

    private fun exportFile(source: File, uri: Uri) {
        runWork(
            label = getString(R.string.busy_export),
            task = {
                FileInputStream(source).use { input ->
                    contentResolver.openOutputStream(uri, "w").use { output ->
                        if (output == null) throw IOException(getString(R.string.destination_unwritable))
                        copy(input, output)
                    }
                }
                true
            },
            success = { showMessage(getString(R.string.export_complete)) },
            errorTitle = getString(R.string.error_export),
        )
    }

    private fun copy(input: FileInputStream, output: OutputStream) {
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
        }
        output.flush()
    }

    private fun redecode(encoding: String) {
        val book = currentBook ?: return
        val oldPosition = reader.position()
        val oldLength = reader.length()
        uiState = uiState.copy(panel = null)
        runWork(
            label = if (encoding == BookRepository.AUTO) getString(R.string.busy_redecode_auto) else getString(R.string.busy_redecode, encoding),
            task = {
                val updated = repository.redecode(book, encoding)
                val newLength = ReaderController().use { candidate ->
                    candidate.open(repository.normalizedFile(updated), 0)
                    candidate.length()
                }
                updated to newLength
            },
            success = { (updated, newLength) ->
                val mappedProgress = mapOffset(oldPosition, oldLength, newLength)
                annotationStore.remapBook(book.id, oldLength, newLength)
                smartTocCache.clear(book.id)
                repository.saveProgress(updated, mappedProgress)
                repository.updateCharCount(updated, newLength)
                reviewPrompter.recordEncodingRescue()
                openBook(updated, clean = false, restoredOverride = mappedProgress)
            },
            errorTitle = getString(R.string.error_redecode),
        )
    }

    private fun mapOffset(value: Long, oldLength: Long, newLength: Long): Long {
        if (newLength <= 1) return 0
        if (oldLength <= 1) return value.coerceIn(0, newLength - 1)
        val fraction = value.coerceIn(0, oldLength - 1).toDouble() / (oldLength - 1).toDouble()
        return (fraction * (newLength - 1).toDouble()).roundToLong().coerceIn(0, newLength - 1)
    }

    private fun refreshTtsVoices() { uiState = uiState.copy(ttsVoices = ttsCatalog.offlineVoices().map { TtsVoiceModel(it.name, it.label) }) }

    private fun updateSettings(settings: ReaderSettings) {
        if (settings.ttsVoiceName != uiState.settings.ttsVoiceName && !proUnlocked) { billing.purchase(); return }
        val previousReadingMode = uiState.settings.readingMode
        var normalized = settings.withReachablePagedNavigation().copy(
            fontSizeSp = settings.fontSizeSp.coerceIn(14f, 40f),
            lineHeightMultiplier = settings.lineHeightMultiplier.coerceIn(1.15f, 2.2f),
            letterSpacingEm = settings.letterSpacingEm.coerceIn(-0.02f, 0.12f),
            paragraphSpacingEm = settings.paragraphSpacingEm.coerceIn(0f, 1.5f),
            horizontalPaddingDp = settings.horizontalPaddingDp.coerceIn(8f, 56f),
            verticalPaddingDp = settings.verticalPaddingDp.coerceIn(4f, 56f),
            firstLineIndentEm = settings.firstLineIndentEm.coerceIn(0f, 3f),
            readerBrightness = settings.readerBrightness.coerceIn(0.03f, 1f),
            autoScrollSpeedDpPerSecond = settings.autoScrollSpeedDpPerSecond.coerceIn(12f, 320f),
            autoPagePaceMultiplier = settings.autoPagePaceMultiplier.coerceIn(0.5f, 2f),
            autoPageDelayMs = settings.autoPageDelayMs.coerceIn(2_000L, 120_000L),
            ttsRate = settings.ttsRate.coerceIn(0.5f, 2f),
            ttsPitch = settings.ttsPitch.coerceIn(0.6f, 1.6f),
            ttsVoiceName = settings.ttsVoiceName.take(256),
        )
        if (cleanMode && normalized.autoScrollEnabled) normalized = normalized.copy(autoScrollEnabled = false)
        when {
            normalized.autoScrollEnabled && motionController.state != ReaderMotionState.AUTO_SCROLL -> beginMotion(ReaderMotionState.AUTO_SCROLL)
            !normalized.autoScrollEnabled && motionController.state == ReaderMotionState.AUTO_SCROLL -> motionController.stop(ReaderMotionState.AUTO_SCROLL)
        }
        normalized = normalized.copy(autoScrollEnabled = motionController.state == ReaderMotionState.AUTO_SCROLL)
        readerPreferences.save(normalized)
        ttsCatalog.setRate(normalized.ttsRate); ttsCatalog.setPitch(normalized.ttsPitch); ttsCatalog.setVoiceName(normalized.ttsVoiceName)
        ReaderPageLayoutCache.clear()
        uiState = uiState.copy(settings = normalized, motion = motionController.state)
        if (previousReadingMode != normalized.readingMode && normalized.readingMode == ReaderMode.PAGED && currentBook != null) render()
    }

    private fun beginMotion(target: ReaderMotionState) {
        if (target != ReaderMotionState.AUTO_PAGE) main.removeCallbacks(autoStep)
        if (target != ReaderMotionState.TTS && uiState.tts.active) {
            runCatching { startService(Intent(this, TtsPlaybackService::class.java).setAction(TtsPlaybackService.ACTION_STOP)) }
        }
        motionController.start(target)
        val settings = uiState.settings.copy(autoScrollEnabled = target == ReaderMotionState.AUTO_SCROLL)
        uiState = uiState.copy(motion = target, settings = settings)
    }

    private fun stopAutoScroll() {
        if (motionController.state == ReaderMotionState.AUTO_SCROLL) {
            motionController.stop(ReaderMotionState.AUTO_SCROLL)
            uiState = uiState.copy(motion = motionController.state, settings = uiState.settings.copy(autoScrollEnabled = false))
        }
    }

    private fun stopAllMotion() {
        main.removeCallbacks(autoStep)
        if (uiState.tts.active || motionController.state == ReaderMotionState.TTS) {
            runCatching { startService(Intent(this, TtsPlaybackService::class.java).setAction(TtsPlaybackService.ACTION_STOP)) }
        }
        val needsPublish = motionController.state != ReaderMotionState.IDLE || uiState.motion != ReaderMotionState.IDLE || uiState.settings.autoScrollEnabled
        motionController.stop()
        if (needsPublish) uiState = uiState.copy(motion = ReaderMotionState.IDLE, settings = uiState.settings.copy(autoScrollEnabled = false))
    }

    private fun startTtsPlayback() {
        val book = currentBook ?: return
        if (cleanMode) return
        beginMotion(ReaderMotionState.TTS)
        val intent = Intent(this, TtsPlaybackService::class.java)
            .setAction(TtsPlaybackService.ACTION_START)
            .putExtra(TtsPlaybackService.EXTRA_PATH, repository.normalizedFile(book).absolutePath)
            .putExtra(TtsPlaybackService.EXTRA_BOOK_ID, book.id)
            .putExtra(TtsPlaybackService.EXTRA_TITLE, stripTxt(book.name))
            .putExtra(TtsPlaybackService.EXTRA_OFFSET, reader.position())
            .putExtra(TtsPlaybackService.EXTRA_RATE, uiState.settings.ttsRate)
            .putExtra(TtsPlaybackService.EXTRA_PITCH, uiState.settings.ttsPitch)
            .putExtra(TtsPlaybackService.EXTRA_VOICE, uiState.settings.ttsVoiceName)
            .putExtra(TtsPlaybackService.EXTRA_CHINESE_MODE, uiState.settings.chineseMode.name)
            .putExtra(TtsPlaybackService.EXTRA_CHINESE_OVERRIDES, uiState.settings.chineseOverrides)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
    }

    private fun toggleTts() {
        if (currentBook == null || uiState.busyLabel != null || cleanMode) return
        if (uiState.tts.active) startService(Intent(this, TtsPlaybackService::class.java).setAction(TtsPlaybackService.ACTION_TOGGLE))
        else startTtsPlayback()
    }

    private fun stopTts() {
        if (uiState.tts.active || motionController.state == ReaderMotionState.TTS) {
            runCatching { startService(Intent(this, TtsPlaybackService::class.java).setAction(TtsPlaybackService.ACTION_STOP)) }
        }
        if (motionController.state == ReaderMotionState.TTS) motionController.stop(ReaderMotionState.TTS)
        uiState = uiState.copy(motion = motionController.state)
    }

    private fun stopBackgroundTts() = stopTts()

    private fun toggleAutoPaging() {
        if (currentBook == null || uiState.busyLabel != null) return
        if (motionController.state == ReaderMotionState.AUTO_PAGE) stopAutoPaging() else {
            beginMotion(ReaderMotionState.AUTO_PAGE)
            val delay = motionController.adaptivePageDelayMs(visiblePageChars, statsStore.charsPerMinute(), uiState.settings)
            main.postDelayed(autoStep, delay)
        }
    }

    private fun stopAutoPaging() {
        main.removeCallbacks(autoStep)
        if (motionController.state == ReaderMotionState.AUTO_PAGE) motionController.stop(ReaderMotionState.AUTO_PAGE)
        uiState = uiState.copy(motion = motionController.state)
    }

    private fun setSleepTimer(minutes: Int) {
        main.removeCallbacksAndMessages(SLEEP_TOKEN)
        uiState = uiState.copy(sleepMinutes = minutes)
        if (uiState.tts.active) startService(Intent(this, TtsPlaybackService::class.java).setAction(TtsPlaybackService.ACTION_SLEEP).putExtra(TtsPlaybackService.EXTRA_MINUTES, minutes))
        if (minutes > 0 && motionController.state != ReaderMotionState.TTS) {
            main.postAtTime({
                stopAllMotion()
                uiState = uiState.copy(sleepMinutes = 0, message = getString(R.string.sleep_timer_finished))
            }, SLEEP_TOKEN, SystemClock.uptimeMillis() + minutes * 60_000L)
        }
    }

    private fun deleteCurrentBook() {
        val book = currentBook ?: return
        uiState = uiState.copy(deleteConfirmation = false)
        stopAllMotion(); workGeneration.incrementAndGet(); reader.close()
        repository.delete(book)
        clearBookPreferences(book)
        libraryMetadata.clear(book.id)
        cleanHistory.clearAllForBook(book.id)
        smartCleanFeedback.clearBook(book.id)
        annotationStore.clearBook(book.id)
        smartTocCache.clear(book.id)
        TocOverrideStore(this).reset(book.id)
        currentBook = null; cleanMode = false; pageHistory.clear(); chapterWorkKey = null; refreshLibrary()
        uiState = uiState.copy(
            screen = AppScreen.LIBRARY, currentBook = null, pageText = "", position = 0, length = 0,
            cleanMode = false, panel = null, chaptersLoaded = false, repairRules = emptyList(),
            noiseCandidates = emptyList(), smartCleanAnalyzed = false, smartCleanUndoAvailable = false, message = getString(R.string.removed_from_library),
        )
    }

    private fun deleteLibraryBook(id: String) {
        val book = findBook(id) ?: return
        if (currentBook?.id == id) { deleteCurrentBook(); return }
        repository.delete(book)
        clearBookPreferences(book)
        libraryMetadata.clear(book.id)
        cleanHistory.clearAllForBook(book.id)
        smartCleanFeedback.clearBook(book.id)
        annotationStore.clearBook(book.id)
        smartTocCache.clear(book.id)
        TocOverrideStore(this).reset(book.id)
        refreshLibrary()
        showMessage(getString(R.string.removed_from_library))
    }

    private fun clearBookPreferences(book: BookRepository.Book) {
        getPreferences(MODE_PRIVATE).edit().remove(rulesKey(book)).apply()
        annotationStore.clearBook(book.id)
    }

    private fun <T> runWork(label: String, task: Callable<T>, success: (T) -> Unit, errorTitle: String) {
        if (uiState.busyLabel != null) return
        val token = workGeneration.incrementAndGet()
        uiState = uiState.copy(busyLabel = label)
        workers.execute {
            try {
                val result = task.call()
                main.post {
                    if (isDestroyed || token != workGeneration.get()) return@post
                    uiState = uiState.copy(busyLabel = null); success(result)
                }
            } catch (error: Throwable) {
                main.post {
                    if (isDestroyed || token != workGeneration.get()) return@post
                    uiState = uiState.copy(busyLabel = null, message = friendlyError(errorTitle, error))
                }
            }
        }
    }

    private fun showMessage(message: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) uiState = uiState.copy(message = message)
        else main.post { uiState = uiState.copy(message = message) }
    }

    private fun friendlyError(title: String, error: Throwable): String {
        val raw = error.message?.takeIf { it.isNotBlank() }
        val detail = when (raw) {
            "unsupported_rule_file", "rule_limit_exceeded", "no_valid_rules" -> getString(R.string.invalid_rule)
            else -> raw
        }
        return if (detail == null) title else "$title: $detail"
    }

    private fun ttsReason(reason: String): String = when {
        reason == "audio focus" -> getString(R.string.tts_audio_focus)
        reason == "sleep" -> getString(R.string.sleep_timer_finished)
        reason.startsWith("tts error") -> getString(R.string.tts_engine_error)
        reason == "TTS engine not ready" -> getString(R.string.tts_engine_not_ready)
        reason == "audio focus denied" -> getString(R.string.tts_audio_focus_denied)
        else -> reason
    }

    private fun handleReaderPageKey(keyCode: Int): Boolean {
    if (uiState.screen != AppScreen.READER || uiState.busyLabel != null || currentBook == null ||
        uiState.panel != null || readerViewModel.hotPanel.value != null
    ) return false

    val forward = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP -> {
            if (!ReaderInteractionRuntime.shouldUseVolumeKeysForPaging(uiState.settings, uiState.tts.active)) return false
            val nextKey = if (uiState.settings.reverseVolumeKeys) KeyEvent.KEYCODE_VOLUME_UP else KeyEvent.KEYCODE_VOLUME_DOWN
            keyCode == nextKey
        }
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_SPACE -> true
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_PAGE_UP -> false
        else -> return false
    }
    if (forward) navigateNext(userInitiated = true) else navigatePrevious(userInitiated = true)
    return true
}

@SuppressLint("RestrictedApi") // Intercept Reader paging keys before system/focused-view handling.
override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    val keyCode = event.keyCode
    val isReaderPageKey = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN,
        KeyEvent.KEYCODE_SPACE -> true
        else -> false
    }
    if (isReaderPageKey) {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (consumedReaderPageKey == keyCode) return true
                if (event.repeatCount == 0 && handleReaderPageKey(keyCode)) {
                    consumedReaderPageKey = keyCode
                    return true
                }
            }
            KeyEvent.ACTION_UP -> if (consumedReaderPageKey == keyCode) {
                consumedReaderPageKey = KeyEvent.KEYCODE_UNKNOWN
                return true
            }
        }
    }
    return super.dispatchKeyEvent(event)
}

    override fun onPause() {
        super.onPause()
        if (motionController.state == ReaderMotionState.AUTO_SCROLL || motionController.state == ReaderMotionState.AUTO_PAGE) stopAllMotion()
        statsStore.finish()
        val book = currentBook
        if (book != null && !cleanMode) persistProgress(book, force = true)
    }

    override fun onDestroy() {
        workGeneration.incrementAndGet(); chapterWorkKey = null; main.removeCallbacksAndMessages(null)
        tocWorkers.shutdownNow(); progressWorkers.shutdownNow(); workers.shutdownNow()
        runCatching { unregisterReceiver(ttsStateReceiver) }
        if (::billing.isInitialized) billing.close()
        if (::ttsCatalog.isInitialized) ttsCatalog.close()
        if (::statsStore.isInitialized) statsStore.finish()
        if (::readerPreferences.isInitialized) readerPreferences.flush(uiState.settings)
        reader.close(); super.onDestroy()
    }

    companion object {
        private const val SLEEP_TOKEN = "jingdu-sleep"
        private const val STATE_BOOK_ID = "jingdu.activeBookId"
        private const val STATE_NORMALIZED_SHA = "jingdu.normalizedSha"
        private const val STATE_POSITION = "jingdu.position"
        private const val STATE_CLEAN_MODE = "jingdu.cleanMode"
        private const val STATE_PENDING_EXPORT = "jingdu.pendingExport"
        private const val MAX_RULE_IMPORT_BYTES = 1024 * 1024
        private const val MAX_BACKUP_BYTES = 2 * 1024 * 1024
        private const val MAX_BATCH_IMPORT_FILES = 100
        private const val PROGRESS_SAVE_INTERVAL_MS = 2_000L
        private const val PROGRESS_SAVE_CHAR_DELTA = 4_096L
        private fun stripTxt(name: String): String = if (name.lowercase().endsWith(".txt")) name.dropLast(4) else name
    }
}
