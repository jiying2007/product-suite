package com.junchen.jingdu

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class BatchBookResult(
    val bookId: String,
    val name: String,
    val noiseCandidates: Int,
    val safeCandidates: Int,
    val tocAnomalies: Int,
    val healthScore: Int,
    val appliedRules: Int,
)

data class BatchAutomationReport(
    val booksScanned: Int,
    val totalCandidates: Int,
    val safeCandidates: Int,
    val tocAnomalies: Int,
    val appliedRules: Int,
    val failedBooks: Int,
    val books: List<BatchBookResult>,
)

/**
 * Pro batch automation. It only creates/updates local repair-rule metadata and derived clean
 * revisions; source TXT files are never modified. Auto-apply is precision-first and excludes
 * inline/garbled/semantic-BODY/protected candidates.
 */
internal class BatchAutomation(
    private val repository: BookRepository,
    private val activityPreferences: SharedPreferences,
    private val globalRules: List<RepairRule>,
    private val feedback: SmartCleanFeedbackStore,
    private val cleanHistory: CleanHistory,
    private val classifier: SemanticCandidateClassifier = TinyLocalSemanticCandidateClassifier,
) {
    fun run(books: List<BookRepository.Book>, applySafe: Boolean): BatchAutomationReport {
        val results = ArrayList<BatchBookResult>()
        var failed = 0
        books.take(MAX_BOOKS).forEach { book ->
            try {
                ReaderController().use { reader ->
                    reader.open(repository.normalizedFile(book), 0)
                    val candidates = reader.noiseCandidates()
                    val toc = SmartToc.analyze(reader)
                    val doctor = TxtDoctor.diagnose(reader, book, toc, candidates)
                    val safe = candidates.filter { candidate -> safeCandidate(book.id, candidate) }
                    var applied = 0
                    if (applySafe && safe.isNotEmpty()) {
                        val key = "rules.${book.id}"
                        val previousPacked = activityPreferences.getString(key, "") ?: ""
                        val existing = RuleCodec.parse(previousPacked)
                        val additions = safe.map { RepairRule(it.text, "", RepairRuleMode.LITERAL) }
                        val updated = (existing + additions)
                            .distinctBy { Triple(it.mode, it.find, it.replacement) }
                            .take(500)
                        applied = (updated.size - existing.size).coerceAtLeast(0)
                        if (applied > 0) {
                            cleanHistory.save(book.id, previousPacked)
                            val packed = RuleCodec.pack(updated)
                            activityPreferences.edit().putString(key, packed).apply()
                            val effective = RuleCodec.pack(RuleCodec.combined(updated, globalRules, true))
                            val revision = repository.repairRevision(book, effective)
                            val output = repository.cleanFile(book, revision)
                            if (!output.isFile) reader.exportRules(effective, output)
                            repository.pruneCleanRevisions(book, output)
                        }
                    }
                    results += BatchBookResult(
                        bookId = book.id,
                        name = book.name,
                        noiseCandidates = candidates.size,
                        safeCandidates = safe.size,
                        tocAnomalies = toc.anomalyCount,
                        healthScore = doctor.healthScore,
                        appliedRules = applied,
                    )
                }
            } catch (_: Throwable) {
                failed++
            }
        }
        return BatchAutomationReport(
            booksScanned = results.size,
            totalCandidates = results.sumOf(BatchBookResult::noiseCandidates),
            safeCandidates = results.sumOf(BatchBookResult::safeCandidates),
            tocAnomalies = results.sumOf(BatchBookResult::tocAnomalies),
            appliedRules = results.sumOf(BatchBookResult::appliedRules),
            failedBooks = failed,
            books = results,
        )
    }

    private fun safeCandidate(bookId: String, candidate: ReaderController.NoiseCandidate): Boolean {
        val stored = feedback.decision(bookId, candidate.reason, candidate.text)
        if (stored == SmartCleanFeedback.PROTECT || stored == SmartCleanFeedback.KEEP) return false
        if (stored == SmartCleanFeedback.DELETE) return true
        if (candidate.reason == "inline_fragment" || candidate.reason == "garbled_line") return false
        val semantic = classifier.classifyCandidate(candidate.text)
        if (semantic.label == SemanticCandidateLabel.BODY && semantic.confidence >= 0.65f) return false
        val adjusted = candidate.score + feedback.modelDelta(candidate.reason, candidate.text) +
            if (semantic.label == SemanticCandidateLabel.AD && semantic.confidence >= 0.65f) 8 else 0
        return adjusted >= 88 && candidate.count >= 1
    }

    companion object {
        private const val MAX_BOOKS = 100

        fun toJson(report: BatchAutomationReport): String {
            val books = JSONArray()
            report.books.forEach { item ->
                books.put(JSONObject()
                    .put("bookId", item.bookId)
                    .put("name", item.name)
                    .put("healthScore", item.healthScore)
                    .put("noiseCandidates", item.noiseCandidates)
                    .put("safeCandidates", item.safeCandidates)
                    .put("tocAnomalies", item.tocAnomalies)
                    .put("appliedRules", item.appliedRules))
            }
            return JSONObject()
                .put("schema", 1)
                .put("type", "jingdu-batch-automation-report")
                .put("booksScanned", report.booksScanned)
                .put("totalCandidates", report.totalCandidates)
                .put("safeCandidates", report.safeCandidates)
                .put("tocAnomalies", report.tocAnomalies)
                .put("appliedRules", report.appliedRules)
                .put("failedBooks", report.failedBooks)
                .put("containsBookText", false)
                .put("books", books)
                .toString(2)
        }
    }
}
