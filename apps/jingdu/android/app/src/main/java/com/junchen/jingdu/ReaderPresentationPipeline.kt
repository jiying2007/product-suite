package com.junchen.jingdu

internal data class ReaderPresentedText(
    val sourceText: String,
    val displayText: String,
    val map: SourceDisplayMap,
)

/**
 * The single bounded presentation pipeline used by paged, continuous, skim and selection paths.
 * Core/source text remains immutable and authoritative; display-only edits always carry an exact
 * monotonic projection back to source coordinates.
 */
internal object ReaderPresentationPipeline {
    private val excessiveBlankLines = Regex("\\n[ \\t]*\\n(?:[ \\t]*\\n)+")

    fun present(
        source: String,
        settings: ReaderSettings,
        prewarmSelection: Boolean = settings.readingMode == ReaderMode.CONTINUOUS,
    ): ReaderPresentedText {
        var intermediate = source
        if (settings.compressBlankLines) {
            // The long-standing presentation toggle now represents conservative Smart Layout:
            // repair fixed-width hard wraps only with strong local evidence, then normalize truly
            // excessive blank lines. Both operations remain display-only and projection-backed.
            intermediate = SmartLayout.present(intermediate).text
            if (excessiveBlankLines.containsMatchIn(intermediate)) {
                intermediate = intermediate.replace(excessiveBlankLines, "\n\n")
            }
        }
        if (settings.paragraphSpacingEm > 0f && "\n\n" in intermediate) {
            intermediate = intermediate.replace("\n\n", "\n${ReaderTypographySpec.PARAGRAPH_SPACER}\n")
        }
        val sourceToIntermediate = TextProjection.between(source, intermediate)
        val presented = ReaderTextPresentation.present(intermediate, settings.chineseMode, settings.chineseOverrides)
        val display = presented.displayText
        val intermediateToDisplay = presented.projection
        val map = SourceDisplayMap.compose(sourceToIntermediate, intermediateToDisplay)
        // Continuous text is consumed later by Compose, so prewarm its exact selection map on this
        // bounded worker. Paged rendering first measures the visible prefix and then calls
        // annotatedForSelection() on the same worker; skipping full-window prewarm there avoids
        // allocating source-range annotations for text that cannot appear on the current page.
        if (prewarmSelection) ReaderSelectionController.prewarmSelectionMap(display, map)
        return ReaderPresentedText(
            sourceText = source,
            displayText = display,
            map = map,
        )
    }
}
