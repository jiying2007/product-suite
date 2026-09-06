package com.junchen.jingdu

enum class AppScreen { LIBRARY, READER }
enum class ReaderPanel { QUICK_SETTINGS, SEARCH, CHAPTERS, BOOKMARKS, ANNOTATIONS, READING_MAP, READING_HISTORY, CLEAN, SETTINGS, ENCODING, DOCTOR, SMART_CLEAN_LAB, PRIVACY }
enum class RepairRuleMode { LITERAL, LINE_GLOB }
enum class LibraryBookStatus { UNREAD, READING, FINISHED }
enum class LibrarySort { RECENT, NAME, PROGRESS }
enum class NoiseRisk { LOW, MEDIUM, HIGH }
enum class ReaderAnnotationFilter { ALL, HIGHLIGHTS, NOTES }

data class BookCardModel(
    val id: String,
    val name: String,
    val encoding: String,
    val sizeBytes: Long,
    val progress: Long,
    val charCount: Long,
    val touchedAt: Long,
    val normalizedSha256: String,
    val favorite: Boolean = false,
    val tags: List<String> = emptyList(),
) {
    val progressFraction: Float get() = if (charCount <= 0) 0f else (progress.toDouble() / charCount.toDouble()).toFloat().coerceIn(0f, 1f)
    val status: LibraryBookStatus get() = when {
        progress <= 0L -> LibraryBookStatus.UNREAD
        charCount > 0L && progressFraction >= 0.985f -> LibraryBookStatus.FINISHED
        else -> LibraryBookStatus.READING
    }
}

data class SearchResultModel(val offset: Long, val context: String)
data class ChapterModel(val offset: Long, val title: String, val source: String = "core", val confidence: Int = 100)
data class BookmarkModel(val offset: Long, val progressFraction: Float)

data class ReaderSkimPreview(
    val fraction: Float,
    val offset: Long,
    val chapter: String?,
    val preview: String,
    val chapterProgressPercent: Int,
    val bookProgressPercent: Int,
    val chapterRemainingMinutes: Int?,
    val bookRemainingMinutes: Int?,
    val originOffset: Long,
)

data class ReaderDayModel(val dayEpoch: Long, val durationMs: Long, val charsRead: Long)

data class RepairRule(val find: String, val replacement: String, val mode: RepairRuleMode = RepairRuleMode.LITERAL)
data class NoiseCandidateModel(
    val score: Int,
    val count: Int,
    val reason: String,
    val text: String,
    val selected: Boolean = false,
    val semanticLabel: SemanticCandidateLabel = SemanticCandidateLabel.UNCERTAIN,
    val semanticConfidence: Float = 0f,
    val semanticScore: Int = 0,
    val feedback: SmartCleanFeedback = SmartCleanFeedback.NONE,
) {
    val risk: NoiseRisk get() = when {
        reason == "inline_fragment" || reason == "garbled_line" -> NoiseRisk.MEDIUM
        reason == "promo_repeated" || score >= 82 -> NoiseRisk.HIGH
        reason == "promo" || reason == "url" || (score >= 68 && count >= 10) -> NoiseRisk.MEDIUM
        else -> NoiseRisk.LOW
    }
    val impactChars: Long get() = text.codePointCount(0, text.length).toLong() * count.coerceAtLeast(0).toLong()
    val defaultSafeSelection: Boolean get() = when {
        feedback == SmartCleanFeedback.KEEP || feedback == SmartCleanFeedback.PROTECT -> false
        feedback == SmartCleanFeedback.DELETE -> true
        semanticLabel == SemanticCandidateLabel.BODY && semanticConfidence >= 0.65f -> false
        reason == "inline_fragment" || reason == "garbled_line" -> false
        semanticLabel == SemanticCandidateLabel.AD && semanticConfidence >= 0.75f && score >= 72 -> true
        else -> risk == NoiseRisk.HIGH || (risk == NoiseRisk.MEDIUM && count >= 20)
    }
}

data class TtsVoiceModel(val name: String, val label: String)
data class TtsPlaybackModel(
    val active: Boolean = false,
    val playing: Boolean = false,
    val offset: Long = -1L,
    val nextOffset: Long = -1L,
    val rangeStart: Long = -1L,
    val rangeEnd: Long = -1L,
    val reason: String? = null,
)

data class AppUiState(
    val screen: AppScreen = AppScreen.LIBRARY,
    val books: List<BookCardModel> = emptyList(),
    val currentBook: BookCardModel? = null,
    val pageText: String = "",
    val position: Long = 0,
    val pageTurnDirection: Int = 0,
    val length: Long = 0,
    val cleanMode: Boolean = false,
    val panel: ReaderPanel? = null,
    val busyLabel: String? = null,
    val message: String? = null,
    val searchQuery: String = "",
    val searchResults: List<SearchResultModel> = emptyList(),
    val chapters: List<ChapterModel> = emptyList(),
    val chaptersLoaded: Boolean = false,
    val bookmarks: List<BookmarkModel> = emptyList(),
    val annotations: List<ReaderAnnotation> = emptyList(),
    val annotationQuery: String = "",
    val annotationFilter: ReaderAnnotationFilter = ReaderAnnotationFilter.ALL,
    val readingDays: List<ReaderDayModel> = emptyList(),
    val skimPreview: ReaderSkimPreview? = null,
    val repairRules: List<RepairRule> = emptyList(),
    val globalRules: List<RepairRule> = emptyList(),
    val noiseCandidates: List<NoiseCandidateModel> = emptyList(),
    val smartCleanAnalyzed: Boolean = false,
    val smartCleanUndoAvailable: Boolean = false,
    val proUnlocked: Boolean = false,
    val proAvailable: Boolean = false,
    val proConnected: Boolean = false,
    val proPrice: String? = null,
    val ttsVoices: List<TtsVoiceModel> = emptyList(),
    val motion: ReaderMotionState = ReaderMotionState.IDLE,
    val tts: TtsPlaybackModel = TtsPlaybackModel(),
    val sleepMinutes: Int = 0,
    val settings: ReaderSettings = ReaderSettings(),
    val deleteConfirmation: Boolean = false,
) {
    val ttsPlaying: Boolean get() = motion == ReaderMotionState.TTS && tts.playing
    val autoPaging: Boolean get() = motion == ReaderMotionState.AUTO_PAGE
    val autoScrolling: Boolean get() = motion == ReaderMotionState.AUTO_SCROLL
}
