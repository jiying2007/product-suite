package com.junchen.jingdu

import kotlin.math.roundToInt

enum class DoctorSeverity { GOOD, NOTICE, WARNING }

data class TxtDoctorReport(
    val healthScore: Int,
    val encodingScore: Int,
    val tocScore: Int,
    val cleanScore: Int,
    val textScore: Int,
    val chapterCount: Int,
    val tocAnomalies: Int,
    val noiseCandidates: Int,
    val garbledWindows: Int,
    val replacementCharacters: Int,
    val sizeBytes: Long,
    val encoding: String,
) {
    val severity: DoctorSeverity
        get() = when {
            healthScore >= 90 -> DoctorSeverity.GOOD
            healthScore >= 70 -> DoctorSeverity.NOTICE
            else -> DoctorSeverity.WARNING
        }
}

/**
 * Local-only TXT health diagnostic. It samples bounded Core windows and reuses Smart TOC/Clean
 * candidate pipelines. No book text is retained in the report and no network capability exists.
 */
internal object TxtDoctor {
    private const val SAMPLE_WINDOWS = 8
    private const val SAMPLE_CHARS = 4096L

    fun diagnose(reader: ReaderController, book: BookRepository.Book): TxtDoctorReport =
        diagnose(reader, book, SmartToc.analyze(reader), reader.noiseCandidates())

    fun diagnose(
        reader: ReaderController,
        book: BookRepository.Book,
        toc: TocQualityReport,
        noise: List<ReaderController.NoiseCandidate>,
    ): TxtDoctorReport {
        var replacements = 0
        var suspiciousControls = 0
        var totalCodePoints = 0L
        var garbledWindows = 0
        val length = reader.length().coerceAtLeast(1)

        repeat(SAMPLE_WINDOWS) { index ->
            val offset = if (SAMPLE_WINDOWS <= 1) 0 else ((length - 1) * index / (SAMPLE_WINDOWS - 1))
            val text = reader.readAt(offset, SAMPLE_CHARS)
            var localBad = 0
            var localTotal = 0
            var cursor = 0
            while (cursor < text.length) {
                val cp = text.codePointAt(cursor)
                cursor += Character.charCount(cp)
                localTotal++
                totalCodePoints++
                when {
                    cp == 0xFFFD -> { replacements++; localBad++ }
                    Character.getType(cp) == Character.CONTROL.toInt() && cp != '\n'.code && cp != '\r'.code && cp != '\t'.code -> {
                        suspiciousControls++; localBad++
                    }
                }
            }
            if (localTotal > 0 && localBad.toDouble() / localTotal.toDouble() >= 0.01) garbledWindows++
        }

        val badRatio = if (totalCodePoints == 0L) 0.0 else (replacements + suspiciousControls).toDouble() / totalCodePoints.toDouble()
        val textScore = when {
            badRatio <= 0.0001 -> 100
            badRatio <= 0.001 -> 92
            badRatio <= 0.01 -> 72
            else -> 40
        }
        val encodingScore = (textScore - garbledWindows * 3).coerceIn(0, 100)
        val cleanPenalty = noise.take(100).sumOf { candidate ->
            when {
                candidate.score >= 88 -> 4
                candidate.score >= 72 -> 2
                else -> 1
            }
        }.coerceAtMost(55)
        val cleanScore = (100 - cleanPenalty).coerceIn(0, 100)
        val health = (
            encodingScore * 0.35 +
                toc.score * 0.25 +
                cleanScore * 0.25 +
                textScore * 0.15
            ).roundToInt().coerceIn(0, 100)

        return TxtDoctorReport(
            healthScore = health,
            encodingScore = encodingScore,
            tocScore = toc.score,
            cleanScore = cleanScore,
            textScore = textScore,
            chapterCount = toc.chapters.size,
            tocAnomalies = toc.anomalyCount,
            noiseCandidates = noise.size,
            garbledWindows = garbledWindows,
            replacementCharacters = replacements,
            sizeBytes = book.size,
            encoding = book.encoding,
        )
    }
}
