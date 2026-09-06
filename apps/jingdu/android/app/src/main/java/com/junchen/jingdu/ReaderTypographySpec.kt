package com.junchen.jingdu

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import android.text.style.LineHeightSpan
import android.text.style.StyleSpan
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

internal data class ReaderTypographySpec(
    val typeface: ReaderTypeface,
    val customFontId: String,
    val fontSizeSp: Float,
    val lineHeightMultiplier: Float,
    val letterSpacingEm: Float,
    val paragraphSpacingEm: Float,
    val firstLineIndentEm: Float,
    val alignment: ReaderTextAlignment,
    val weight: ReaderFontWeight,
) {
    val fingerprint: Int = listOf(
        typeface.name, customFontId, fontSizeSp, lineHeightMultiplier, letterSpacingEm,
        paragraphSpacingEm, firstLineIndentEm, alignment.name, weight.name,
    ).hashCode()

    fun composeTextStyle(color: Color, family: FontFamily): TextStyle = TextStyle(
        color = color,
        fontFamily = family,
        fontWeight = composeWeight(),
        fontSize = fontSizeSp.sp,
        lineHeight = (fontSizeSp * lineHeightMultiplier).sp,
        letterSpacing = (fontSizeSp * letterSpacingEm).sp,
        textAlign = if (alignment == ReaderTextAlignment.START) TextAlign.Start else TextAlign.Justify,
        textIndent = TextIndent(firstLine = (fontSizeSp * firstLineIndentEm).sp),
        // Phrase-aware high-quality line breaking materially improves CJK punctuation/phrase
        // boundaries in long prose. It is presentation-only and never changes source offsets.
        lineBreak = LineBreak(
            strategy = LineBreak.Strategy.HighQuality,
            strictness = LineBreak.Strictness.Normal,
            wordBreak = LineBreak.WordBreak.Phrase,
        ),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both,
        ),
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )

    fun androidTypeface(context: Context): Typeface {
        val base = when (typeface) {
            ReaderTypeface.SERIF -> Typeface.SERIF
            ReaderTypeface.MONOSPACE -> Typeface.MONOSPACE
            ReaderTypeface.CUSTOM -> ReaderFontStore(context).file(customFontId)?.let {
                runCatching { Typeface.Builder(it).build() }.getOrNull()
            } ?: Typeface.SANS_SERIF
            ReaderTypeface.SYSTEM -> Typeface.SANS_SERIF
        }
        return Typeface.create(base, when (weight) {
            ReaderFontWeight.NORMAL -> Typeface.NORMAL
            ReaderFontWeight.MEDIUM, ReaderFontWeight.SEMIBOLD -> Typeface.BOLD
        })
    }

    /**
     * StaticLayout receives the same paragraph spacing/indent/weight semantics as Compose.
     * Paragraph gaps are represented by a zero-width sentinel line inserted by the presentation
     * pipeline; that sentinel gets an explicit line height instead of abusing global line spacing.
     */
    fun androidLayoutText(displayText: String, density: Density, emphasizeHeadings: Boolean = false): CharSequence {
        if (displayText.isEmpty()) return displayText
        val value = SpannableString(displayText)
        val gapPx = with(density) { (fontSizeSp * paragraphSpacingEm).sp.toPx() }.roundToInt().coerceAtLeast(1)
        var index = displayText.indexOf(PARAGRAPH_SPACER)
        while (index >= 0) {
            value.setSpan(ExactLineHeightSpan(gapPx), index, index + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            index = displayText.indexOf(PARAGRAPH_SPACER, index + 1)
        }
        val firstMarginPx = with(density) { (fontSizeSp * firstLineIndentEm).sp.toPx() }.roundToInt().coerceAtLeast(0)
        if (firstMarginPx > 0) {
            var start = 0
            while (start < value.length) {
                val end = displayText.indexOf('\n', start).let { if (it < 0) value.length else it + 1 }
                if (start < end && displayText[start] != PARAGRAPH_SPACER) {
                    value.setSpan(LeadingMarginSpan.Standard(firstMarginPx, 0), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                start = end
            }
        }
        if (weight != ReaderFontWeight.NORMAL) {
            value.setSpan(StyleSpan(Typeface.BOLD), 0, value.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else if (emphasizeHeadings) {
            var cursor = 0
            displayText.lineSequence().forEach { line ->
                val end = (cursor + line.length).coerceAtMost(value.length)
                if (end > cursor && ReaderHeadingClassifier.isHeading(line.trim())) {
                    value.setSpan(StyleSpan(Typeface.BOLD), cursor, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                cursor = (end + 1).coerceAtMost(value.length)
            }
        }
        return value
    }

    private fun composeWeight(): FontWeight = when (weight) {
        ReaderFontWeight.NORMAL -> FontWeight.Normal
        ReaderFontWeight.MEDIUM -> FontWeight.Medium
        ReaderFontWeight.SEMIBOLD -> FontWeight.SemiBold
    }

    /** API-26-compatible equivalent of LineHeightSpan.Standard (which is API 29+). */
    private class ExactLineHeightSpan(private val heightPx: Int) : LineHeightSpan {
        override fun chooseHeight(
            text: CharSequence,
            start: Int,
            end: Int,
            spanstartv: Int,
            lineHeight: Int,
            fm: Paint.FontMetricsInt,
        ) {
            val originalHeight = fm.descent - fm.ascent
            if (originalHeight <= 0) return
            val ratio = heightPx.toFloat() / originalHeight.toFloat()
            fm.descent = (fm.descent * ratio).roundToInt()
            fm.ascent = fm.descent - heightPx
        }
    }

    companion object {
        const val PARAGRAPH_SPACER: Char = '\u200B'
        fun from(settings: ReaderSettings) = ReaderTypographySpec(
            settings.typeface, settings.customFontId, settings.fontSizeSp,
            settings.lineHeightMultiplier, settings.letterSpacingEm, settings.paragraphSpacingEm,
            settings.firstLineIndentEm, settings.textAlignment, settings.fontWeight,
        )
    }
}
