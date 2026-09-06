package com.junchen.jingdu

/** One authority for every document-derived string shown or spoken to the user. */
internal object ReaderTextPresentation {
    data class Presented(
        val sourceText: String,
        val displayText: String,
        val projection: TextProjection,
    )

    fun present(source: String, settings: ReaderSettings): Presented =
        present(source, settings.chineseMode, settings.chineseOverrides)

    fun present(source: String, mode: ChineseDisplayMode, overrides: String): Presented {
        val display = display(source, mode, overrides)
        return Presented(source, display, TextProjection.between(source, display))
    }

    /** Display-only text does not need source/display offset projection. */
    fun display(source: String, settings: ReaderSettings): String =
        display(source, settings.chineseMode, settings.chineseOverrides)

    fun display(source: String, mode: ChineseDisplayMode, overrides: String): String =
        ChineseTextConverter.convert(source, mode, overrides)

    fun chapterTitle(sourceTitle: String, settings: ReaderSettings): String = display(sourceTitle, settings)

    fun chapters(source: List<ChapterModel>, settings: ReaderSettings): List<ChapterModel> =
        source.map { chapter -> chapter.copy(title = chapterTitle(chapter.title, settings)) }

    /** Maps Android TTS UTF-16 callback ranges in spoken text back to source-code-point offsets. */
    fun sourceRangeForDisplayUtf16(
        displayText: String,
        projection: TextProjection,
        utf16Start: Int,
        utf16End: Int,
    ): LongRange {
        val startUtf = utf16Start.coerceIn(0, displayText.length)
        val endUtf = utf16End.coerceIn(startUtf, displayText.length)
        val startCp = displayText.codePointCount(0, startUtf).toLong()
        val endCp = displayText.codePointCount(0, endUtf).toLong()
        val sourceStart = projection.sourceForDisplay(startCp)
        val sourceEndExclusive = projection.sourceForDisplay(endCp)
            .coerceAtLeast((sourceStart + if (endCp > startCp) 1 else 0).coerceAtMost(projection.sourceCodePoints))
            .coerceAtMost(projection.sourceCodePoints)
        return sourceStart until sourceEndExclusive
    }
}
