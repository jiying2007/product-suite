package com.junchen.jingdu

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.google.protobuf.InvalidProtocolBufferException
import com.junchen.jingdu.proto.ChineseDisplayModeProto
import com.junchen.jingdu.proto.ReaderAutoPageModeProto
import com.junchen.jingdu.proto.ReaderFontWeightProto
import com.junchen.jingdu.proto.ReaderGestureActionProto
import com.junchen.jingdu.proto.ReaderModeProto
import com.junchen.jingdu.proto.ReaderOrientationProto
import com.junchen.jingdu.proto.ReaderPageAnimationProto
import com.junchen.jingdu.proto.ReaderPaletteProto
import com.junchen.jingdu.proto.ReaderPresetProto
import com.junchen.jingdu.proto.ReaderSettingsProto
import com.junchen.jingdu.proto.ReaderTapZonePresetProto
import com.junchen.jingdu.proto.ReaderTextAlignmentProto
import com.junchen.jingdu.proto.ReaderThemeProto
import com.junchen.jingdu.proto.ReaderTypefaceProto
import com.junchen.jingdu.proto.ReaderVolumeKeyModeProto
import com.junchen.jingdu.proto.ReaderWideColumnsProto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

enum class ReaderPalette { PAPER, LIGHT, SEPIA, NIGHT, OLED }
enum class ReaderTypeface { SYSTEM, SERIF, MONOSPACE, CUSTOM }
enum class ChineseDisplayMode { ORIGINAL, SIMPLIFIED, TRADITIONAL, TAIWAN, TAIWAN_PHRASES, HONG_KONG }
enum class ReaderMode { PAGED, CONTINUOUS }
enum class ReaderPageAnimation { NONE, SLIDE }
enum class ReaderOrientation { SYSTEM, PORTRAIT, LANDSCAPE }
enum class ReaderTextAlignment { START, JUSTIFY }
enum class ReaderFontWeight { NORMAL, MEDIUM, SEMIBOLD }
enum class ReaderPreset { STANDARD, COMFORT, LARGE, NIGHT, LOW_VISION, CUSTOM }
enum class ReaderVolumeKeyMode { PAGE_WHEN_NOT_TTS, ALWAYS_PAGE, SYSTEM_VOLUME }
enum class ReaderWideColumns { AUTO, SINGLE, DOUBLE }
enum class ReaderTapZonePreset { BALANCED, RIGHT_HANDED, LEFT_HANDED, CUSTOM }
enum class ReaderAutoPageMode { ADAPTIVE, FIXED }
enum class ReaderGestureAction { CONTROLS, BOOKMARK, NEXT, PREVIOUS, NONE }

data class ReaderNamedTheme(
    val id: String,
    val name: String,
    val palette: ReaderPalette,
    val typeface: ReaderTypeface,
    val customFontId: String,
    val fontSizeSp: Float,
    val lineHeightMultiplier: Float,
    val letterSpacingEm: Float,
    val paragraphSpacingEm: Float,
    val horizontalPaddingDp: Float,
    val verticalPaddingDp: Float,
    val firstLineIndentEm: Float,
    val textAlignment: ReaderTextAlignment,
    val fontWeight: ReaderFontWeight,
    val compressBlankLines: Boolean,
    val emphasizeHeadings: Boolean,
    val extraDim: Float,
)

data class ReaderSettings(
    val palette: ReaderPalette = ReaderPalette.PAPER,
    val typeface: ReaderTypeface = ReaderTypeface.SYSTEM,
    val customFontId: String = "",
    val fontSizeSp: Float = 20f,
    val lineHeightMultiplier: Float = 1.55f,
    val letterSpacingEm: Float = 0f,
    val paragraphSpacingEm: Float = 0.45f,
    val horizontalPaddingDp: Float = 24f,
    val verticalPaddingDp: Float = 18f,
    val firstLineIndentEm: Float = 0f,
    val textAlignment: ReaderTextAlignment = ReaderTextAlignment.JUSTIFY,
    val fontWeight: ReaderFontWeight = ReaderFontWeight.NORMAL,
    val compressBlankLines: Boolean = true,
    val emphasizeHeadings: Boolean = true,
    val preset: ReaderPreset = ReaderPreset.STANDARD,
    val readingMode: ReaderMode = ReaderMode.PAGED,
    val pageAnimation: ReaderPageAnimation = ReaderPageAnimation.SLIDE,
    val tapPagingEnabled: Boolean = true,
    val swipePagingEnabled: Boolean = true,
    val reversePagingGestures: Boolean = false,
    val tapZonePreset: ReaderTapZonePreset = ReaderTapZonePreset.BALANCED,
    val tapZoneEdgeFraction: Float = 0.25f,
    val brightnessGestureEnabled: Boolean = true,
    val pinchFontEnabled: Boolean = true,
    val doubleTapBookmarkEnabled: Boolean = false,
    val controlsAutoHideMs: Long = 3500L,
    val useSystemBrightness: Boolean = true,
    val readerBrightness: Float = 0.45f,
    val orientation: ReaderOrientation = ReaderOrientation.SYSTEM,
    val volumeKeyMode: ReaderVolumeKeyMode = ReaderVolumeKeyMode.PAGE_WHEN_NOT_TTS,
    val reverseVolumeKeys: Boolean = false,
    val wideColumns: ReaderWideColumns = ReaderWideColumns.AUTO,
    val showReadingStatus: Boolean = true,
    val showClock: Boolean = false,
    val showBattery: Boolean = false,
    val focusRulerLines: Int = 0,
    val hapticEnabled: Boolean = true,
    val gestureCoachDismissed: Boolean = false,
    val autoScrollEnabled: Boolean = false,
    val autoScrollSpeedDpPerSecond: Float = 55f,
    val autoPageMode: ReaderAutoPageMode = ReaderAutoPageMode.ADAPTIVE,
    val autoPagePaceMultiplier: Float = 1.0f,
    val autoPageDelayMs: Long = 6500L,
    val chineseMode: ChineseDisplayMode = ChineseDisplayMode.ORIGINAL,
    val chineseOverrides: String = "",
    val ttsRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val ttsVoiceName: String = "",
    val extraDim: Float = 0f,
    val twoStageSelectionEnabled: Boolean = true,
    val advancedGestureCustomizationEnabled: Boolean = false,
    val centerTapAction: ReaderGestureAction = ReaderGestureAction.CONTROLS,
    val doubleTapAction: ReaderGestureAction = ReaderGestureAction.NONE,
    val dictionaryProcessTextEnabled: Boolean = true,
    val namedThemes: List<ReaderNamedTheme> = emptyList(),
    val activeThemeId: String = "",
)

internal fun ReaderSettings.applyPreset(value: ReaderPreset): ReaderSettings = when (value) {
    ReaderPreset.STANDARD -> copy(preset = value, palette = ReaderPalette.PAPER, typeface = ReaderTypeface.SYSTEM, fontSizeSp = 20f, lineHeightMultiplier = 1.55f, letterSpacingEm = 0f, paragraphSpacingEm = 0.45f, horizontalPaddingDp = 24f, verticalPaddingDp = 18f, firstLineIndentEm = 0f, textAlignment = ReaderTextAlignment.JUSTIFY, fontWeight = ReaderFontWeight.NORMAL, extraDim = 0f, activeThemeId = "")
    ReaderPreset.COMFORT -> copy(preset = value, palette = ReaderPalette.PAPER, typeface = ReaderTypeface.SERIF, fontSizeSp = 21f, lineHeightMultiplier = 1.65f, letterSpacingEm = 0.01f, paragraphSpacingEm = 0.55f, horizontalPaddingDp = 28f, verticalPaddingDp = 22f, firstLineIndentEm = 2f, textAlignment = ReaderTextAlignment.JUSTIFY, fontWeight = ReaderFontWeight.NORMAL, extraDim = 0f, activeThemeId = "")
    ReaderPreset.LARGE -> copy(preset = value, palette = ReaderPalette.LIGHT, typeface = ReaderTypeface.SYSTEM, fontSizeSp = 28f, lineHeightMultiplier = 1.72f, letterSpacingEm = 0.01f, paragraphSpacingEm = 0.65f, horizontalPaddingDp = 30f, verticalPaddingDp = 24f, firstLineIndentEm = 1f, textAlignment = ReaderTextAlignment.START, fontWeight = ReaderFontWeight.MEDIUM, extraDim = 0f, activeThemeId = "")
    ReaderPreset.NIGHT -> copy(preset = value, palette = ReaderPalette.NIGHT, typeface = ReaderTypeface.SERIF, fontSizeSp = 21f, lineHeightMultiplier = 1.65f, letterSpacingEm = 0.01f, paragraphSpacingEm = 0.55f, horizontalPaddingDp = 26f, verticalPaddingDp = 20f, firstLineIndentEm = 2f, textAlignment = ReaderTextAlignment.JUSTIFY, fontWeight = ReaderFontWeight.NORMAL, extraDim = 0.08f, activeThemeId = "")
    ReaderPreset.LOW_VISION -> copy(preset = value, palette = ReaderPalette.LIGHT, typeface = ReaderTypeface.SYSTEM, fontSizeSp = 34f, lineHeightMultiplier = 1.85f, letterSpacingEm = 0.035f, paragraphSpacingEm = 0.8f, horizontalPaddingDp = 32f, verticalPaddingDp = 28f, firstLineIndentEm = 0f, textAlignment = ReaderTextAlignment.START, fontWeight = ReaderFontWeight.SEMIBOLD, focusRulerLines = 5, extraDim = 0f, activeThemeId = "")
    ReaderPreset.CUSTOM -> copy(preset = value)
}

internal fun ReaderSettings.toNamedTheme(id: String, name: String): ReaderNamedTheme = ReaderNamedTheme(id.take(80), name.take(80), palette, typeface, customFontId, fontSizeSp, lineHeightMultiplier, letterSpacingEm, paragraphSpacingEm, horizontalPaddingDp, verticalPaddingDp, firstLineIndentEm, textAlignment, fontWeight, compressBlankLines, emphasizeHeadings, extraDim)
internal fun ReaderSettings.applyNamedTheme(theme: ReaderNamedTheme): ReaderSettings = copy(preset = ReaderPreset.CUSTOM, activeThemeId = theme.id, palette = theme.palette, typeface = theme.typeface, customFontId = theme.customFontId, fontSizeSp = theme.fontSizeSp, lineHeightMultiplier = theme.lineHeightMultiplier, letterSpacingEm = theme.letterSpacingEm, paragraphSpacingEm = theme.paragraphSpacingEm, horizontalPaddingDp = theme.horizontalPaddingDp, verticalPaddingDp = theme.verticalPaddingDp, firstLineIndentEm = theme.firstLineIndentEm, textAlignment = theme.textAlignment, fontWeight = theme.fontWeight, compressBlankLines = theme.compressBlankLines, emphasizeHeadings = theme.emphasizeHeadings, extraDim = theme.extraDim)

internal fun ReaderSettings.withReachablePagedNavigation(): ReaderSettings =
    if (readingMode == ReaderMode.PAGED && !tapPagingEnabled && !swipePagingEnabled) copy(tapPagingEnabled = true) else this

private object ReaderSettingsSerializer : Serializer<ReaderSettingsProto> {
    override val defaultValue: ReaderSettingsProto = ReaderSettingsProto.getDefaultInstance()
    override suspend fun readFrom(input: InputStream): ReaderSettingsProto = try { ReaderSettingsProto.parseFrom(input) } catch (error: InvalidProtocolBufferException) { throw CorruptionException("Cannot read Reader settings", error) }
    override suspend fun writeTo(t: ReaderSettingsProto, output: OutputStream) = t.writeTo(output)
}

private val Context.readerDataStore: DataStore<ReaderSettingsProto> by dataStore(fileName = "reader-settings.pb", serializer = ReaderSettingsSerializer)

@OptIn(FlowPreview::class)
class ReaderPreferences(context: Context) {
    private val dataStore = context.applicationContext.readerDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = MutableSharedFlow<ReaderSettings>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init { scope.launch { pending.debounce(350L).collect { persist(it) } } }

    fun observe(): Flow<ReaderSettings> = dataStore.data.map(::fromProto)
    fun load(): ReaderSettings = runBlocking(Dispatchers.IO) { fromProto(dataStore.data.first()) }
    fun save(value: ReaderSettings) { pending.tryEmit(sanitize(value)) }
    fun flush(value: ReaderSettings) = runBlocking(Dispatchers.IO) { persist(sanitize(value)) }
    fun exportMap(): Map<String, Any> = toMap(load())

    fun importMap(values: Map<String, Any?>): ReaderSettings {
        val fallback = load()
        val imported = sanitize(fallback.copy(
            palette = enumValue(values["palette"], fallback.palette), typeface = enumValue(values["typeface"], fallback.typeface), customFontId = string(values["customFontId"], fallback.customFontId, 128),
            fontSizeSp = number(values["fontSizeSp"], fallback.fontSizeSp), lineHeightMultiplier = number(values["lineHeightMultiplier"], fallback.lineHeightMultiplier), letterSpacingEm = number(values["letterSpacingEm"], fallback.letterSpacingEm), paragraphSpacingEm = number(values["paragraphSpacingEm"], fallback.paragraphSpacingEm),
            horizontalPaddingDp = number(values["horizontalPaddingDp"], fallback.horizontalPaddingDp), verticalPaddingDp = number(values["verticalPaddingDp"], fallback.verticalPaddingDp), firstLineIndentEm = number(values["firstLineIndentEm"], fallback.firstLineIndentEm), textAlignment = enumValue(values["textAlignment"], fallback.textAlignment), fontWeight = enumValue(values["fontWeight"], fallback.fontWeight),
            compressBlankLines = boolean(values["compressBlankLines"], fallback.compressBlankLines), emphasizeHeadings = boolean(values["emphasizeHeadings"], fallback.emphasizeHeadings), preset = enumValue(values["preset"], fallback.preset), readingMode = enumValue(values["readingMode"], fallback.readingMode), pageAnimation = enumValue(values["pageAnimation"], fallback.pageAnimation),
            tapPagingEnabled = boolean(values["tapPagingEnabled"], fallback.tapPagingEnabled), swipePagingEnabled = boolean(values["swipePagingEnabled"], fallback.swipePagingEnabled), reversePagingGestures = boolean(values["reversePagingGestures"], fallback.reversePagingGestures), tapZonePreset = enumValue(values["tapZonePreset"], fallback.tapZonePreset), tapZoneEdgeFraction = number(values["tapZoneEdgeFraction"], fallback.tapZoneEdgeFraction),
            brightnessGestureEnabled = boolean(values["brightnessGestureEnabled"], fallback.brightnessGestureEnabled), pinchFontEnabled = boolean(values["pinchFontEnabled"], fallback.pinchFontEnabled), doubleTapBookmarkEnabled = boolean(values["doubleTapBookmarkEnabled"], fallback.doubleTapBookmarkEnabled), controlsAutoHideMs = long(values["controlsAutoHideMs"], fallback.controlsAutoHideMs), useSystemBrightness = boolean(values["useSystemBrightness"], fallback.useSystemBrightness), readerBrightness = number(values["readerBrightness"], fallback.readerBrightness),
            orientation = enumValue(values["orientation"], fallback.orientation), volumeKeyMode = enumValue(values["volumeKeyMode"], fallback.volumeKeyMode), reverseVolumeKeys = boolean(values["reverseVolumeKeys"], fallback.reverseVolumeKeys), wideColumns = enumValue(values["wideColumns"], fallback.wideColumns), showReadingStatus = boolean(values["showReadingStatus"], fallback.showReadingStatus), showClock = boolean(values["showClock"], fallback.showClock), showBattery = boolean(values["showBattery"], fallback.showBattery), focusRulerLines = int(values["focusRulerLines"], fallback.focusRulerLines),
            hapticEnabled = boolean(values["hapticEnabled"], fallback.hapticEnabled), gestureCoachDismissed = boolean(values["gestureCoachDismissed"], fallback.gestureCoachDismissed), autoScrollEnabled = false, autoScrollSpeedDpPerSecond = number(values["autoScrollSpeedDpPerSecond"], fallback.autoScrollSpeedDpPerSecond), autoPageMode = enumValue(values["autoPageMode"], fallback.autoPageMode), autoPagePaceMultiplier = number(values["autoPagePaceMultiplier"], fallback.autoPagePaceMultiplier), autoPageDelayMs = long(values["autoPageDelayMs"], fallback.autoPageDelayMs),
            chineseMode = enumValue(values["chineseMode"], fallback.chineseMode), chineseOverrides = string(values["chineseOverrides"], fallback.chineseOverrides, MAX_OVERRIDE_TEXT_CHARS), ttsRate = number(values["ttsRate"], fallback.ttsRate), ttsPitch = number(values["ttsPitch"], fallback.ttsPitch), ttsVoiceName = string(values["ttsVoiceName"], fallback.ttsVoiceName, 256), extraDim = number(values["extraDim"], fallback.extraDim), twoStageSelectionEnabled = boolean(values["twoStageSelectionEnabled"], fallback.twoStageSelectionEnabled), advancedGestureCustomizationEnabled = boolean(values["advancedGestureCustomizationEnabled"], fallback.advancedGestureCustomizationEnabled), centerTapAction = enumValue(values["centerTapAction"], fallback.centerTapAction), doubleTapAction = enumValue(values["doubleTapAction"], fallback.doubleTapAction), dictionaryProcessTextEnabled = boolean(values["dictionaryProcessTextEnabled"], fallback.dictionaryProcessTextEnabled), namedThemes = parseThemes(values["namedThemes"]), activeThemeId = string(values["activeThemeId"], fallback.activeThemeId, 80),
        ))
        flush(imported)
        return imported
    }

    private suspend fun persist(value: ReaderSettings) { dataStore.updateData { toProto(value) } }

    private fun fromProto(value: ReaderSettingsProto): ReaderSettings {
        if (value.schema != SCHEMA) return ReaderSettings()
        return sanitize(ReaderSettings(
            palette = value.palette.toModel(), typeface = value.typeface.toModel(), customFontId = value.customFontId, fontSizeSp = value.fontSizeSp, lineHeightMultiplier = value.lineHeightMultiplier, letterSpacingEm = value.letterSpacingEm, paragraphSpacingEm = value.paragraphSpacingEm, horizontalPaddingDp = value.horizontalPaddingDp, verticalPaddingDp = value.verticalPaddingDp, firstLineIndentEm = value.firstLineIndentEm, textAlignment = value.textAlignment.toModel(), fontWeight = value.fontWeight.toModel(), compressBlankLines = value.compressBlankLines, emphasizeHeadings = value.emphasizeHeadings, preset = value.preset.toModel(), readingMode = value.readingMode.toModel(), pageAnimation = value.pageAnimation.toModel(), tapPagingEnabled = value.tapPagingEnabled, swipePagingEnabled = value.swipePagingEnabled, reversePagingGestures = value.reversePagingGestures, tapZonePreset = value.tapZonePreset.toModel(), tapZoneEdgeFraction = value.tapZoneEdgeFraction, brightnessGestureEnabled = value.brightnessGestureEnabled, pinchFontEnabled = value.pinchFontEnabled, doubleTapBookmarkEnabled = value.doubleTapBookmarkEnabled, controlsAutoHideMs = value.controlsAutoHideMs, useSystemBrightness = value.useSystemBrightness, readerBrightness = value.readerBrightness, orientation = value.orientation.toModel(), volumeKeyMode = value.volumeKeyMode.toModel(), reverseVolumeKeys = value.reverseVolumeKeys, wideColumns = value.wideColumns.toModel(), showReadingStatus = value.showReadingStatus, showClock = value.showClock, showBattery = value.showBattery, focusRulerLines = value.focusRulerLines, hapticEnabled = value.hapticEnabled, gestureCoachDismissed = value.gestureCoachDismissed, autoScrollEnabled = false, autoScrollSpeedDpPerSecond = value.autoScrollSpeedDpPerSecond, autoPageMode = value.autoPageMode.toModel(), autoPagePaceMultiplier = value.autoPagePaceMultiplier, autoPageDelayMs = value.autoPageDelayMs, chineseMode = value.chineseMode.toModel(), chineseOverrides = value.chineseOverrides, ttsRate = value.ttsRate, ttsPitch = value.ttsPitch, ttsVoiceName = value.ttsVoiceName, extraDim = value.extraDim, twoStageSelectionEnabled = value.twoStageSelectionEnabled, advancedGestureCustomizationEnabled = value.advancedGestureCustomizationEnabled, centerTapAction = value.centerTapAction.toModel(), doubleTapAction = value.doubleTapAction.toModel(), dictionaryProcessTextEnabled = value.dictionaryProcessTextEnabled, namedThemes = value.namedThemesList.map { it.toModel() }.take(MAX_THEMES), activeThemeId = value.activeThemeId,
        ))
    }

    private fun toProto(value: ReaderSettings): ReaderSettingsProto = ReaderSettingsProto.newBuilder()
        .setSchema(SCHEMA).setPalette(value.palette.toProto()).setTypeface(value.typeface.toProto()).setCustomFontId(value.customFontId).setFontSizeSp(value.fontSizeSp).setLineHeightMultiplier(value.lineHeightMultiplier).setLetterSpacingEm(value.letterSpacingEm).setParagraphSpacingEm(value.paragraphSpacingEm).setHorizontalPaddingDp(value.horizontalPaddingDp).setVerticalPaddingDp(value.verticalPaddingDp).setFirstLineIndentEm(value.firstLineIndentEm).setTextAlignment(value.textAlignment.toProto()).setFontWeight(value.fontWeight.toProto()).setCompressBlankLines(value.compressBlankLines).setEmphasizeHeadings(value.emphasizeHeadings).setPreset(value.preset.toProto()).setReadingMode(value.readingMode.toProto()).setPageAnimation(value.pageAnimation.toProto()).setTapPagingEnabled(value.tapPagingEnabled).setSwipePagingEnabled(value.swipePagingEnabled).setReversePagingGestures(value.reversePagingGestures).setTapZonePreset(value.tapZonePreset.toProto()).setTapZoneEdgeFraction(value.tapZoneEdgeFraction).setBrightnessGestureEnabled(value.brightnessGestureEnabled).setPinchFontEnabled(value.pinchFontEnabled).setDoubleTapBookmarkEnabled(value.doubleTapBookmarkEnabled).setControlsAutoHideMs(value.controlsAutoHideMs).setUseSystemBrightness(value.useSystemBrightness).setReaderBrightness(value.readerBrightness).setOrientation(value.orientation.toProto()).setVolumeKeyMode(value.volumeKeyMode.toProto()).setReverseVolumeKeys(value.reverseVolumeKeys).setWideColumns(value.wideColumns.toProto()).setShowReadingStatus(value.showReadingStatus).setShowClock(value.showClock).setShowBattery(value.showBattery).setFocusRulerLines(value.focusRulerLines).setHapticEnabled(value.hapticEnabled).setGestureCoachDismissed(value.gestureCoachDismissed).setAutoScrollSpeedDpPerSecond(value.autoScrollSpeedDpPerSecond).setAutoPageMode(value.autoPageMode.toProto()).setAutoPagePaceMultiplier(value.autoPagePaceMultiplier).setAutoPageDelayMs(value.autoPageDelayMs).setChineseMode(value.chineseMode.toProto()).setChineseOverrides(value.chineseOverrides).setTtsRate(value.ttsRate).setTtsPitch(value.ttsPitch).setTtsVoiceName(value.ttsVoiceName).setExtraDim(value.extraDim).setTwoStageSelectionEnabled(value.twoStageSelectionEnabled).setAdvancedGestureCustomizationEnabled(value.advancedGestureCustomizationEnabled).setCenterTapAction(value.centerTapAction.toProto()).setDoubleTapAction(value.doubleTapAction.toProto()).setDictionaryProcessTextEnabled(value.dictionaryProcessTextEnabled).addAllNamedThemes(value.namedThemes.take(MAX_THEMES).map { it.toProto() }).setActiveThemeId(value.activeThemeId).build()

    private fun sanitize(value: ReaderSettings): ReaderSettings = value.withReachablePagedNavigation().copy(customFontId = value.customFontId.take(128), fontSizeSp = value.fontSizeSp.coerceIn(14f, 40f), lineHeightMultiplier = value.lineHeightMultiplier.coerceIn(1.15f, 2.2f), letterSpacingEm = value.letterSpacingEm.coerceIn(-0.02f, 0.12f), paragraphSpacingEm = value.paragraphSpacingEm.coerceIn(0f, 1.5f), horizontalPaddingDp = value.horizontalPaddingDp.coerceIn(8f, 56f), verticalPaddingDp = value.verticalPaddingDp.coerceIn(4f, 56f), firstLineIndentEm = value.firstLineIndentEm.coerceIn(0f, 3f), tapZoneEdgeFraction = value.tapZoneEdgeFraction.coerceIn(0.18f, 0.38f), controlsAutoHideMs = value.controlsAutoHideMs.coerceIn(1500L, 12_000L), readerBrightness = value.readerBrightness.coerceIn(0.03f, 1f), focusRulerLines = value.focusRulerLines.takeIf { it in setOf(0, 3, 5) } ?: 0, autoScrollEnabled = false, autoScrollSpeedDpPerSecond = value.autoScrollSpeedDpPerSecond.coerceIn(12f, 320f), autoPagePaceMultiplier = value.autoPagePaceMultiplier.coerceIn(0.5f, 2f), autoPageDelayMs = value.autoPageDelayMs.coerceIn(2000L, 120_000L), chineseOverrides = value.chineseOverrides.take(MAX_OVERRIDE_TEXT_CHARS), ttsRate = value.ttsRate.coerceIn(0.5f, 2f), ttsPitch = value.ttsPitch.coerceIn(0.6f, 1.6f), ttsVoiceName = value.ttsVoiceName.take(256), extraDim = value.extraDim.coerceIn(0f, 0.75f), namedThemes = value.namedThemes.distinctBy { it.id }.take(MAX_THEMES), activeThemeId = value.activeThemeId.take(80))

    private fun toMap(value: ReaderSettings): Map<String, Any> = linkedMapOf("palette" to value.palette.name, "typeface" to value.typeface.name, "customFontId" to value.customFontId, "fontSizeSp" to value.fontSizeSp, "lineHeightMultiplier" to value.lineHeightMultiplier, "letterSpacingEm" to value.letterSpacingEm, "paragraphSpacingEm" to value.paragraphSpacingEm, "horizontalPaddingDp" to value.horizontalPaddingDp, "verticalPaddingDp" to value.verticalPaddingDp, "firstLineIndentEm" to value.firstLineIndentEm, "textAlignment" to value.textAlignment.name, "fontWeight" to value.fontWeight.name, "compressBlankLines" to value.compressBlankLines, "emphasizeHeadings" to value.emphasizeHeadings, "preset" to value.preset.name, "readingMode" to value.readingMode.name, "pageAnimation" to value.pageAnimation.name, "tapPagingEnabled" to value.tapPagingEnabled, "swipePagingEnabled" to value.swipePagingEnabled, "reversePagingGestures" to value.reversePagingGestures, "tapZonePreset" to value.tapZonePreset.name, "tapZoneEdgeFraction" to value.tapZoneEdgeFraction, "brightnessGestureEnabled" to value.brightnessGestureEnabled, "pinchFontEnabled" to value.pinchFontEnabled, "doubleTapBookmarkEnabled" to value.doubleTapBookmarkEnabled, "controlsAutoHideMs" to value.controlsAutoHideMs, "useSystemBrightness" to value.useSystemBrightness, "readerBrightness" to value.readerBrightness, "orientation" to value.orientation.name, "volumeKeyMode" to value.volumeKeyMode.name, "reverseVolumeKeys" to value.reverseVolumeKeys, "wideColumns" to value.wideColumns.name, "showReadingStatus" to value.showReadingStatus, "showClock" to value.showClock, "showBattery" to value.showBattery, "focusRulerLines" to value.focusRulerLines, "hapticEnabled" to value.hapticEnabled, "gestureCoachDismissed" to value.gestureCoachDismissed, "autoScrollSpeedDpPerSecond" to value.autoScrollSpeedDpPerSecond, "autoPageMode" to value.autoPageMode.name, "autoPagePaceMultiplier" to value.autoPagePaceMultiplier, "autoPageDelayMs" to value.autoPageDelayMs, "chineseMode" to value.chineseMode.name, "chineseOverrides" to value.chineseOverrides, "ttsRate" to value.ttsRate, "ttsPitch" to value.ttsPitch, "ttsVoiceName" to value.ttsVoiceName, "extraDim" to value.extraDim, "twoStageSelectionEnabled" to value.twoStageSelectionEnabled, "advancedGestureCustomizationEnabled" to value.advancedGestureCustomizationEnabled, "centerTapAction" to value.centerTapAction.name, "doubleTapAction" to value.doubleTapAction.name, "dictionaryProcessTextEnabled" to value.dictionaryProcessTextEnabled, "activeThemeId" to value.activeThemeId, "namedThemes" to JSONArray().also { array -> value.namedThemes.forEach { array.put(it.toJson()) } })

    private fun parseThemes(raw: Any?): List<ReaderNamedTheme> { val array = raw as? JSONArray ?: return emptyList(); return buildList { for (index in 0 until minOf(array.length(), MAX_THEMES)) runCatching { add(array.getJSONObject(index).toNamedTheme()) } } }
    private inline fun <reified T : Enum<T>> enumValue(raw: Any?, fallback: T): T = enumValues<T>().firstOrNull { it.name == raw?.toString() } ?: fallback
    private fun number(raw: Any?, fallback: Float): Float = (raw as? Number)?.toFloat() ?: raw?.toString()?.toFloatOrNull() ?: fallback
    private fun long(raw: Any?, fallback: Long): Long = (raw as? Number)?.toLong() ?: raw?.toString()?.toLongOrNull() ?: fallback
    private fun int(raw: Any?, fallback: Int): Int = (raw as? Number)?.toInt() ?: raw?.toString()?.toIntOrNull() ?: fallback
    private fun boolean(raw: Any?, fallback: Boolean): Boolean = raw as? Boolean ?: raw?.toString()?.toBooleanStrictOrNull() ?: fallback
    private fun string(raw: Any?, fallback: String, max: Int): String = (raw as? String ?: fallback).take(max)
    private companion object { const val SCHEMA = 3; const val MAX_OVERRIDE_TEXT_CHARS = 16 * 1024; const val MAX_THEMES = 12 }
}

private fun ReaderNamedTheme.toProto(): ReaderThemeProto = ReaderThemeProto.newBuilder().setId(id).setName(name).setPalette(palette.toProto()).setTypeface(typeface.toProto()).setCustomFontId(customFontId).setFontSizeSp(fontSizeSp).setLineHeightMultiplier(lineHeightMultiplier).setLetterSpacingEm(letterSpacingEm).setParagraphSpacingEm(paragraphSpacingEm).setHorizontalPaddingDp(horizontalPaddingDp).setVerticalPaddingDp(verticalPaddingDp).setFirstLineIndentEm(firstLineIndentEm).setTextAlignment(textAlignment.toProto()).setFontWeight(fontWeight.toProto()).setCompressBlankLines(compressBlankLines).setEmphasizeHeadings(emphasizeHeadings).setExtraDim(extraDim).build()
private fun ReaderThemeProto.toModel() = ReaderNamedTheme(id.take(80), name.take(80), palette.toModel(), typeface.toModel(), customFontId.take(128), fontSizeSp, lineHeightMultiplier, letterSpacingEm, paragraphSpacingEm, horizontalPaddingDp, verticalPaddingDp, firstLineIndentEm, textAlignment.toModel(), fontWeight.toModel(), compressBlankLines, emphasizeHeadings, extraDim)
private fun ReaderNamedTheme.toJson() = JSONObject().put("id", id).put("name", name).put("palette", palette.name).put("typeface", typeface.name).put("customFontId", customFontId).put("fontSizeSp", fontSizeSp).put("lineHeightMultiplier", lineHeightMultiplier).put("letterSpacingEm", letterSpacingEm).put("paragraphSpacingEm", paragraphSpacingEm).put("horizontalPaddingDp", horizontalPaddingDp).put("verticalPaddingDp", verticalPaddingDp).put("firstLineIndentEm", firstLineIndentEm).put("textAlignment", textAlignment.name).put("fontWeight", fontWeight.name).put("compressBlankLines", compressBlankLines).put("emphasizeHeadings", emphasizeHeadings).put("extraDim", extraDim)
private fun JSONObject.toNamedTheme() = ReaderNamedTheme(optString("id").take(80), optString("name").take(80), enumValues<ReaderPalette>().firstOrNull { it.name == optString("palette") } ?: ReaderPalette.PAPER, enumValues<ReaderTypeface>().firstOrNull { it.name == optString("typeface") } ?: ReaderTypeface.SYSTEM, optString("customFontId").take(128), optDouble("fontSizeSp", 20.0).toFloat(), optDouble("lineHeightMultiplier", 1.55).toFloat(), optDouble("letterSpacingEm", 0.0).toFloat(), optDouble("paragraphSpacingEm", 0.45).toFloat(), optDouble("horizontalPaddingDp", 24.0).toFloat(), optDouble("verticalPaddingDp", 18.0).toFloat(), optDouble("firstLineIndentEm", 0.0).toFloat(), enumValues<ReaderTextAlignment>().firstOrNull { it.name == optString("textAlignment") } ?: ReaderTextAlignment.JUSTIFY, enumValues<ReaderFontWeight>().firstOrNull { it.name == optString("fontWeight") } ?: ReaderFontWeight.NORMAL, optBoolean("compressBlankLines", true), optBoolean("emphasizeHeadings", true), optDouble("extraDim", 0.0).toFloat())

private fun ReaderPalette.toProto() = ReaderPaletteProto.values()[ordinal]
private fun ReaderPaletteProto.toModel() = ReaderPalette.entries.getOrElse(ordinal) { ReaderPalette.PAPER }
private fun ReaderTypeface.toProto() = ReaderTypefaceProto.values()[ordinal]
private fun ReaderTypefaceProto.toModel() = ReaderTypeface.entries.getOrElse(ordinal) { ReaderTypeface.SYSTEM }
private fun ChineseDisplayMode.toProto() = ChineseDisplayModeProto.values()[ordinal]
private fun ChineseDisplayModeProto.toModel() = ChineseDisplayMode.entries.getOrElse(ordinal) { ChineseDisplayMode.ORIGINAL }
private fun ReaderMode.toProto() = ReaderModeProto.values()[ordinal]
private fun ReaderModeProto.toModel() = ReaderMode.entries.getOrElse(ordinal) { ReaderMode.PAGED }
private fun ReaderPageAnimation.toProto() = ReaderPageAnimationProto.values()[ordinal]
private fun ReaderPageAnimationProto.toModel() = ReaderPageAnimation.entries.getOrElse(ordinal) { ReaderPageAnimation.SLIDE }
private fun ReaderOrientation.toProto() = ReaderOrientationProto.values()[ordinal]
private fun ReaderOrientationProto.toModel() = ReaderOrientation.entries.getOrElse(ordinal) { ReaderOrientation.SYSTEM }
private fun ReaderTextAlignment.toProto() = ReaderTextAlignmentProto.values()[ordinal]
private fun ReaderTextAlignmentProto.toModel() = ReaderTextAlignment.entries.getOrElse(ordinal) { ReaderTextAlignment.JUSTIFY }
private fun ReaderFontWeight.toProto() = ReaderFontWeightProto.values()[ordinal]
private fun ReaderFontWeightProto.toModel() = ReaderFontWeight.entries.getOrElse(ordinal) { ReaderFontWeight.NORMAL }
private fun ReaderPreset.toProto() = ReaderPresetProto.values()[ordinal]
private fun ReaderPresetProto.toModel() = ReaderPreset.entries.getOrElse(ordinal) { ReaderPreset.STANDARD }
private fun ReaderVolumeKeyMode.toProto() = ReaderVolumeKeyModeProto.values()[ordinal]
private fun ReaderVolumeKeyModeProto.toModel() = ReaderVolumeKeyMode.entries.getOrElse(ordinal) { ReaderVolumeKeyMode.PAGE_WHEN_NOT_TTS }
private fun ReaderWideColumns.toProto() = ReaderWideColumnsProto.values()[ordinal]
private fun ReaderWideColumnsProto.toModel() = ReaderWideColumns.entries.getOrElse(ordinal) { ReaderWideColumns.AUTO }
private fun ReaderTapZonePreset.toProto() = ReaderTapZonePresetProto.values()[ordinal]
private fun ReaderTapZonePresetProto.toModel() = ReaderTapZonePreset.entries.getOrElse(ordinal) { ReaderTapZonePreset.BALANCED }
private fun ReaderAutoPageMode.toProto() = ReaderAutoPageModeProto.values()[ordinal]
private fun ReaderAutoPageModeProto.toModel() = ReaderAutoPageMode.entries.getOrElse(ordinal) { ReaderAutoPageMode.ADAPTIVE }
private fun ReaderGestureAction.toProto() = ReaderGestureActionProto.values()[ordinal]
private fun ReaderGestureActionProto.toModel() = ReaderGestureAction.entries.getOrElse(ordinal) { ReaderGestureAction.NONE }
