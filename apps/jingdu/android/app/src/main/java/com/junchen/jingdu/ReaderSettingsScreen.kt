@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.junchen.jingdu

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID
import kotlin.math.roundToInt

private enum class ReaderSettingsPage {
    HOME, APPEARANCE, NAVIGATION, ASSISTANCE, ADVANCED, DISPLAY, AUTO_READ, SPEECH, LANGUAGE, DATA,
}

private fun readerSettingsParent(page: ReaderSettingsPage): ReaderSettingsPage? = when (page) {
    ReaderSettingsPage.HOME -> null
    ReaderSettingsPage.APPEARANCE, ReaderSettingsPage.NAVIGATION, ReaderSettingsPage.ASSISTANCE, ReaderSettingsPage.ADVANCED -> ReaderSettingsPage.HOME
    ReaderSettingsPage.DISPLAY, ReaderSettingsPage.AUTO_READ, ReaderSettingsPage.SPEECH -> ReaderSettingsPage.ASSISTANCE
    ReaderSettingsPage.LANGUAGE, ReaderSettingsPage.DATA -> ReaderSettingsPage.ADVANCED
}

@Composable
internal fun ReaderSettingsScreen(state: AppUiState, actions: JingduActions) {
    var pageName by rememberSaveable { mutableStateOf(ReaderSettingsPage.HOME.name) }
    val page = runCatching { ReaderSettingsPage.valueOf(pageName) }.getOrDefault(ReaderSettingsPage.HOME)
    fun open(next: ReaderSettingsPage) { pageName = next.name }
    fun back() {
        val parent = readerSettingsParent(page)
        if (parent == null) actions.onClosePanel() else pageName = parent.name
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(readerSettingsPageTitle(page)) },
                navigationIcon = { IconButton(::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.reader_settings_back)) } },
                actions = { TextButton(actions.onClosePanel) { Text(stringResource(R.string.reader_settings_done)) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (page) {
                ReaderSettingsPage.HOME -> SettingsHub {
                    SettingsHubCard(stringResource(R.string.reader_settings_group_appearance), stringResource(R.string.reader_settings_group_appearance_hint)) { open(ReaderSettingsPage.APPEARANCE) }
                    SettingsHubCard(stringResource(R.string.reader_settings_group_navigation), stringResource(R.string.reader_settings_group_navigation_hint)) { open(ReaderSettingsPage.NAVIGATION) }
                    SettingsHubCard(stringResource(R.string.reader_settings_group_assistance), stringResource(R.string.reader_settings_group_assistance_hint)) { open(ReaderSettingsPage.ASSISTANCE) }
                    SettingsHubCard(stringResource(R.string.reader_settings_group_advanced), stringResource(R.string.reader_settings_group_advanced_hint)) { open(ReaderSettingsPage.ADVANCED) }
                }
                ReaderSettingsPage.APPEARANCE -> TypographySettings(state, actions)
                ReaderSettingsPage.NAVIGATION -> GestureSettings(state, actions)
                ReaderSettingsPage.ASSISTANCE -> SettingsHub {
                    SettingsHubCard(stringResource(R.string.reader_display), null) { open(ReaderSettingsPage.DISPLAY) }
                    SettingsHubCard(stringResource(R.string.reader_auto_read), null) { open(ReaderSettingsPage.AUTO_READ) }
                    SettingsHubCard(stringResource(R.string.reader_speech), null) { open(ReaderSettingsPage.SPEECH) }
                }
                ReaderSettingsPage.ADVANCED -> SettingsHub {
                    SettingsHubCard(stringResource(R.string.reader_language), null) { open(ReaderSettingsPage.LANGUAGE) }
                    SettingsHubCard(stringResource(R.string.reader_data), null) { open(ReaderSettingsPage.DATA) }
                }
                ReaderSettingsPage.DISPLAY -> DisplaySettings(state, actions)
                ReaderSettingsPage.AUTO_READ -> AutoReadSettings(state, actions)
                ReaderSettingsPage.SPEECH -> SpeechSettings(state, actions)
                ReaderSettingsPage.LANGUAGE -> LanguageSettings(state, actions)
                ReaderSettingsPage.DATA -> DataSettings(state, actions)
            }
        }
    }
}

@Composable
private fun SettingsHub(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun SettingsHubCard(title: String, subtitle: String?, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsList(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
private fun TypographySettings(state: AppUiState, actions: JingduActions) = SettingsList {
    val s = state.settings
    fun visual(value: ReaderSettings) = actions.onSettingsChanged(value.copy(preset = ReaderPreset.CUSTOM, activeThemeId = ""))
    TypographyPreview(s)
    Section(stringResource(R.string.reader_preset)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ReaderPreset.entries.filter { it != ReaderPreset.CUSTOM }) { preset ->
                FilterChip(s.preset == preset, { actions.onSettingsChanged(s.applyProductPreset(preset)) }, label = { Text(presetLabel(preset)) })
            }
        }
    }
    Section(stringResource(R.string.page_tone)) { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(ReaderPalette.entries) { value -> FilterChip(s.palette == value, { visual(s.copy(palette = value)) }, label = { Text(paletteLabel(value)) }) } } }
    Section(stringResource(R.string.font)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(ReaderTypeface.entries) { value -> FilterChip(s.typeface == value, { visual(s.copy(typeface = value)) }, label = { Text(typefaceLabel(value)) }) } }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(ReaderFontWeight.entries) { value -> FilterChip(s.fontWeight == value, { visual(s.copy(fontWeight = value)) }, label = { Text(weightLabel(value)) }) } }
        OutlinedButton(actions.onImportFont, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.FontDownload, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.reader_import_font)) }
    }
    SettingSlider(stringResource(R.string.font_size), s.fontSizeSp, 14f..40f, "${s.fontSizeSp.roundToInt()}sp") { visual(s.copy(fontSizeSp = it)) }
    SettingSlider(stringResource(R.string.line_spacing), s.lineHeightMultiplier, 1.15f..2.2f, "%.2f×".format(s.lineHeightMultiplier)) { visual(s.copy(lineHeightMultiplier = it)) }
    SettingSlider(stringResource(R.string.reader_letter_spacing), s.letterSpacingEm, -0.02f..0.12f, "%.2fem".format(s.letterSpacingEm)) { visual(s.copy(letterSpacingEm = it)) }
    SettingSlider(stringResource(R.string.reader_paragraph_spacing), s.paragraphSpacingEm, 0f..1.5f, "%.2fem".format(s.paragraphSpacingEm)) { visual(s.copy(paragraphSpacingEm = it)) }
    SettingSlider(stringResource(R.string.side_margins), s.horizontalPaddingDp, 8f..56f, "${s.horizontalPaddingDp.roundToInt()}dp") { visual(s.copy(horizontalPaddingDp = it)) }
    SettingSlider(stringResource(R.string.reader_vertical_margins), s.verticalPaddingDp, 4f..56f, "${s.verticalPaddingDp.roundToInt()}dp") { visual(s.copy(verticalPaddingDp = it)) }
    SettingSlider(stringResource(R.string.reader_first_line_indent), s.firstLineIndentEm, 0f..3f, stringResource(R.string.reader_indent_value, s.firstLineIndentEm)) { visual(s.copy(firstLineIndentEm = it)) }
    Section(stringResource(R.string.reader_text_alignment)) { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(ReaderTextAlignment.entries) { value -> FilterChip(s.textAlignment == value, { visual(s.copy(textAlignment = value)) }, label = { Text(stringResource(if (value == ReaderTextAlignment.START) R.string.reader_align_start else R.string.reader_align_justify)) }) } } }
    SettingSwitch(stringResource(R.string.reader_compress_blank_lines), s.compressBlankLines) { visual(s.copy(compressBlankLines = it)) }
    SettingSwitch(stringResource(R.string.reader_emphasize_headings), s.emphasizeHeadings) { visual(s.copy(emphasizeHeadings = it)) }
    NamedThemes(s, actions)
}

@Composable
private fun TypographyPreview(s: ReaderSettings) {
    val context = LocalContext.current
    val family = rememberReaderFontFamily(context, s)
    val baseStyle = ReaderTypographySpec.from(s).composeTextStyle(readerTextColor(s.palette), family)
    val paragraphGap = with(LocalDensity.current) { (s.fontSizeSp * s.paragraphSpacingEm).sp.toDp() }
    val paragraphs = stringResource(R.string.reader_typography_preview_sample).split("\n\n")
    Section(stringResource(R.string.reader_typography_preview)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = readerBackground(s.palette),
            shadowElevation = 1.dp,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(
                    horizontal = s.horizontalPaddingDp.coerceIn(8f, 56f).dp,
                    vertical = s.verticalPaddingDp.coerceIn(4f, 56f).dp,
                ),
                verticalArrangement = Arrangement.spacedBy(paragraphGap),
            ) {
                paragraphs.forEachIndexed { index, paragraph ->
                    Text(
                        paragraph,
                        style = if (index == 0 && s.emphasizeHeadings) baseStyle.copy(fontWeight = FontWeight.SemiBold) else baseStyle,
                        maxLines = if (index == 0) 2 else 4,
                    )
                }
            }
        }
    }
}

@Composable
private fun NamedThemes(s: ReaderSettings, actions: JingduActions) {
    var name by rememberSaveable { mutableStateOf("") }
    var showSave by rememberSaveable { mutableStateOf(false) }
    Section(stringResource(R.string.reader_named_themes)) {
        if (s.namedThemes.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(s.namedThemes, key = { it.id }) { theme ->
                InputChip(
                    selected = s.activeThemeId == theme.id,
                    onClick = { actions.onSettingsChanged(s.applyNamedTheme(theme)) },
                    label = { Text(theme.name) },
                    trailingIcon = { IconButton({ actions.onSettingsChanged(s.copy(namedThemes = s.namedThemes.filterNot { it.id == theme.id }, activeThemeId = if (s.activeThemeId == theme.id) "" else s.activeThemeId)) }) { Icon(Icons.Outlined.Close, stringResource(R.string.reader_delete_theme)) } },
                )
            }
        }
        OutlinedButton({ showSave = true }, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.BookmarkAdd, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.reader_save_theme)) }
    }
    if (showSave) AlertDialog(
        onDismissRequest = { showSave = false },
        title = { Text(stringResource(R.string.reader_save_theme)) },
        text = { OutlinedTextField(name, { name = it.take(80) }, label = { Text(stringResource(R.string.reader_theme_name)) }, singleLine = true) },
        confirmButton = { TextButton({
            val clean = name.trim().ifBlank { return@TextButton }
            val theme = s.toNamedTheme(UUID.randomUUID().toString(), clean)
            actions.onSettingsChanged(s.copy(namedThemes = (s.namedThemes + theme).takeLast(12), activeThemeId = theme.id, preset = ReaderPreset.CUSTOM))
            name = ""; showSave = false
        }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton({ showSave = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun GestureSettings(state: AppUiState, actions: JingduActions) = SettingsList {
    val s = state.settings
    var showMore by rememberSaveable { mutableStateOf(false) }

    SettingSwitch(
        stringResource(R.string.reader_tap_paging),
        s.tapPagingEnabled,
        enabled = canDisablePagingPath(s.tapPagingEnabled, s.swipePagingEnabled),
    ) { actions.onSettingsChanged(s.copy(tapPagingEnabled = it)) }
    SettingSwitch(
        stringResource(R.string.reader_swipe_paging),
        s.swipePagingEnabled,
        enabled = canDisablePagingPath(s.swipePagingEnabled, s.tapPagingEnabled),
    ) { actions.onSettingsChanged(s.copy(swipePagingEnabled = it)) }
    Text(stringResource(R.string.reader_paging_path_required), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SettingSwitch(stringResource(R.string.reader_reverse_gestures), s.reversePagingGestures) { actions.onSettingsChanged(s.copy(reversePagingGestures = it)) }

    Section(stringResource(R.string.reader_volume_keys)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ReaderVolumeKeyMode.entries) { value ->
                FilterChip(s.volumeKeyMode == value, { actions.onSettingsChanged(s.copy(volumeKeyMode = value)) }, label = { Text(volumeLabel(value)) })
            }
        }
        SettingSwitch(stringResource(R.string.reader_reverse_volume), s.reverseVolumeKeys) { actions.onSettingsChanged(s.copy(reverseVolumeKeys = it)) }
    }

    OutlinedButton(onClick = { showMore = !showMore }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.reader_more_gesture_options))
    }

    if (showMore) {
        Section(stringResource(R.string.reader_tap_zone_template)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ReaderTapZonePreset.entries.filter { it != ReaderTapZonePreset.CUSTOM }) { value ->
                    FilterChip(s.tapZonePreset == value, { actions.onSettingsChanged(s.copy(tapZonePreset = value)) }, label = { Text(tapZoneLabel(value)) })
                }
            }
            SettingSlider(stringResource(R.string.reader_tap_zone), s.tapZoneEdgeFraction, 0.18f..0.38f, stringResource(R.string.reader_tap_zone_value, (s.tapZoneEdgeFraction * 100).roundToInt())) {
                actions.onSettingsChanged(s.copy(tapZonePreset = ReaderTapZonePreset.CUSTOM, tapZoneEdgeFraction = it))
            }
        }
        SettingSwitch(stringResource(R.string.reader_brightness_gesture), s.brightnessGestureEnabled) { actions.onSettingsChanged(s.copy(brightnessGestureEnabled = it)) }
        SettingSwitch(stringResource(R.string.reader_pinch_font), s.pinchFontEnabled) { actions.onSettingsChanged(s.copy(pinchFontEnabled = it)) }
        SettingSwitch(stringResource(R.string.reader_double_tap_bookmark), s.doubleTapBookmarkEnabled) { actions.onSettingsChanged(s.copy(doubleTapBookmarkEnabled = it)) }
        SettingSwitch(stringResource(R.string.reader_two_stage_selection), s.twoStageSelectionEnabled) { actions.onSettingsChanged(s.copy(twoStageSelectionEnabled = it)) }
        SettingSwitch(stringResource(R.string.reader_advanced_gestures), s.advancedGestureCustomizationEnabled) { actions.onSettingsChanged(s.copy(advancedGestureCustomizationEnabled = it)) }
        if (s.advancedGestureCustomizationEnabled) {
            Section(stringResource(R.string.reader_gesture_center_action)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ReaderGestureAction.entries) { value -> FilterChip(s.centerTapAction == value, { actions.onSettingsChanged(s.copy(centerTapAction = value)) }, label = { Text(gestureActionLabel(value)) }) }
                }
            }
            Section(stringResource(R.string.reader_gesture_double_action)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ReaderGestureAction.entries) { value -> FilterChip(s.doubleTapAction == value, { actions.onSettingsChanged(s.copy(doubleTapAction = value)) }, label = { Text(gestureActionLabel(value)) }) }
                }
            }
        }
        SettingSwitch(stringResource(R.string.reader_dictionary_actions), s.dictionaryProcessTextEnabled) { actions.onSettingsChanged(s.copy(dictionaryProcessTextEnabled = it)) }
        SettingSwitch(stringResource(R.string.reader_haptics), s.hapticEnabled) { actions.onSettingsChanged(s.copy(hapticEnabled = it)) }
        SettingSlider(stringResource(R.string.reader_controls_auto_hide), s.controlsAutoHideMs.toFloat(), 1_500f..12_000f, stringResource(R.string.seconds_value, s.controlsAutoHideMs / 1000f)) { actions.onSettingsChanged(s.copy(controlsAutoHideMs = it.toLong())) }
    }
}

internal fun canDisablePagingPath(currentEnabled: Boolean, otherEnabled: Boolean): Boolean = !currentEnabled || otherEnabled

@Composable
private fun DisplaySettings(state: AppUiState, actions: JingduActions) = SettingsList {
    val s = state.settings
    SettingSwitch(stringResource(R.string.reader_system_brightness), s.useSystemBrightness) { actions.onSettingsChanged(s.copy(useSystemBrightness = it)) }
    if (!s.useSystemBrightness) SettingSlider(stringResource(R.string.reader_brightness), s.readerBrightness, 0.03f..1f, "${(s.readerBrightness * 100).roundToInt()}%") { actions.onSettingsChanged(s.copy(readerBrightness = it)) }
    SettingSlider(stringResource(R.string.reader_extra_dim), s.extraDim, 0f..0.75f, "${(s.extraDim * 100).roundToInt()}%") { actions.onSettingsChanged(s.copy(extraDim = it)) }
    Text(stringResource(R.string.reader_extra_dim_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SettingSwitch(stringResource(R.string.reader_show_clock), s.showClock) { actions.onSettingsChanged(s.copy(showClock = it)) }
    SettingSwitch(stringResource(R.string.reader_show_battery), s.showBattery) { actions.onSettingsChanged(s.copy(showBattery = it)) }
    SettingSwitch(stringResource(R.string.reader_reading_status), s.showReadingStatus) { actions.onSettingsChanged(s.copy(showReadingStatus = it)) }
    Section(stringResource(R.string.reader_orientation)) { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(ReaderOrientation.entries) { value -> FilterChip(s.orientation == value, { actions.onSettingsChanged(s.copy(orientation = value)) }, label = { Text(orientationLabel(value)) }) } } }
    Section(stringResource(R.string.reader_wide_columns)) { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(ReaderWideColumns.entries) { value -> FilterChip(s.wideColumns == value, { actions.onSettingsChanged(s.copy(wideColumns = value)) }, label = { Text(columnsLabel(value)) }) } } }
    Section(stringResource(R.string.reader_focus_ruler)) { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(listOf(0, 3, 5)) { lines -> FilterChip(s.focusRulerLines == lines, { actions.onSettingsChanged(s.copy(focusRulerLines = lines)) }, label = { Text(stringResource(when (lines) { 3 -> R.string.reader_focus_three; 5 -> R.string.reader_focus_five; else -> R.string.reader_focus_off })) }) } } }
}

@Composable
private fun AutoReadSettings(state: AppUiState, actions: JingduActions) = SettingsList {
    val s = state.settings
    Section(stringResource(R.string.reader_page_animation)) { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(ReaderPageAnimation.entries) { value -> FilterChip(s.pageAnimation == value, { actions.onSettingsChanged(s.copy(pageAnimation = value)) }, label = { Text(stringResource(if (value == ReaderPageAnimation.NONE) R.string.reader_animation_none else R.string.reader_animation_slide)) }) } } }
    Section(stringResource(R.string.auto_page)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(ReaderAutoPageMode.entries) { value -> FilterChip(s.autoPageMode == value, { actions.onSettingsChanged(s.copy(autoPageMode = value)) }, label = { Text(stringResource(if (value == ReaderAutoPageMode.ADAPTIVE) R.string.reader_auto_page_adaptive else R.string.reader_auto_page_fixed)) }) } }
        if (s.autoPageMode == ReaderAutoPageMode.ADAPTIVE) SettingSlider(stringResource(R.string.reader_auto_page_pace), s.autoPagePaceMultiplier, 0.5f..2f, "%.1f×".format(s.autoPagePaceMultiplier)) { actions.onSettingsChanged(s.copy(autoPagePaceMultiplier = it)) }
        else SettingSlider(stringResource(R.string.interval), s.autoPageDelayMs.toFloat(), 2_000f..120_000f, "%.1fs".format(s.autoPageDelayMs / 1000f)) { actions.onSettingsChanged(s.copy(autoPageDelayMs = it.toLong())) }
    }
    SettingSlider(stringResource(R.string.reader_scroll_speed), s.autoScrollSpeedDpPerSecond, 12f..320f, "${s.autoScrollSpeedDpPerSecond.roundToInt()} dp/s") { actions.onSettingsChanged(s.copy(autoScrollSpeedDpPerSecond = it)) }
    Section(stringResource(R.string.sleep_timer)) { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(listOf(0, 15, 30, 60)) { minutes -> FilterChip(state.sleepMinutes == minutes, { actions.onSleepTimer(minutes) }, label = { Text(if (minutes == 0) stringResource(R.string.off) else stringResource(R.string.minutes_value, minutes)) }) } } }
}

@Composable
private fun LanguageSettings(state: AppUiState, actions: JingduActions) = SettingsList {
    val s = state.settings
    var overrides by rememberSaveable(s.chineseOverrides) { mutableStateOf(s.chineseOverrides) }
    Section(stringResource(R.string.chinese_conversion)) { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(ChineseDisplayMode.entries) { value -> FilterChip(s.chineseMode == value, { actions.onSettingsChanged(s.copy(chineseMode = value)) }, label = { Text(chineseModeLabel(value)) }) } } }
    OutlinedTextField(overrides, { overrides = it.take(16 * 1024) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.chinese_overrides)) }, minLines = 4, maxLines = 10)
    Button({ actions.onSettingsChanged(s.copy(chineseOverrides = overrides)) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.save_dictionary)) }
}

@Composable
private fun SpeechSettings(state: AppUiState, actions: JingduActions) = SettingsList {
    val s = state.settings
    val context = LocalContext.current
    val pronunciationStore = remember(context) { TtsPronunciationStore(context) }
    var pronunciationDraft by rememberSaveable { mutableStateOf(pronunciationStore.raw()) }
    SettingSlider(stringResource(R.string.speech_rate), s.ttsRate, 0.5f..2f, "%.1f×".format(s.ttsRate)) { actions.onSettingsChanged(s.copy(ttsRate = it)) }
    SettingSlider(stringResource(R.string.speech_pitch), s.ttsPitch, 0.6f..1.6f, "%.1f×".format(s.ttsPitch)) { actions.onSettingsChanged(s.copy(ttsPitch = it)) }
    Section(stringResource(R.string.offline_voice)) {
        if (state.proUnlocked) {
            FilterChip(s.ttsVoiceName.isEmpty(), { actions.onSettingsChanged(s.copy(ttsVoiceName = "")) }, label = { Text(stringResource(R.string.system_default)) })
            state.ttsVoices.take(20).forEach { voice -> FilterChip(s.ttsVoiceName == voice.name, { actions.onSettingsChanged(s.copy(ttsVoiceName = voice.name)) }, label = { Text(voice.label) }) }
        } else OutlinedButton(actions.onUpgradePro, Modifier.fillMaxWidth()) { Text("Pro") }
    }
    Section(stringResource(R.string.reader_tts_pronunciation)) {
        Text(stringResource(R.string.reader_tts_pronunciation_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = pronunciationDraft,
            onValueChange = { pronunciationDraft = it.take(TtsPronunciationStore.MAX_RAW_CHARS) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 8,
        )
        Button(
            onClick = {
                runCatching { pronunciationStore.save(pronunciationDraft) }
                    .onSuccess { pronunciationDraft = pronunciationStore.raw() }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.reader_tts_pronunciation_save)) }
    }
}

@Composable
private fun DataSettings(state: AppUiState, actions: JingduActions) = SettingsList {
    Section(stringResource(R.string.local_asset_backup)) {
        Text(stringResource(R.string.local_asset_backup_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.proUnlocked) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(actions.onExportBackup, Modifier.weight(1f)) { Icon(Icons.Outlined.FileUpload, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.export_backup)) }
            OutlinedButton(actions.onImportBackup, Modifier.weight(1f)) { Icon(Icons.Outlined.FileDownload, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.restore_backup)) }
        } else OutlinedButton(actions.onUpgradePro, Modifier.fillMaxWidth()) { Text("Pro") }
    }
    TextButton(actions.onRestorePro, Modifier.fillMaxWidth()) { Text(stringResource(if (state.proUnlocked) R.string.pro_unlocked_recheck else R.string.restore_jingdu_pro)) }
}

@Composable private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); content() } }
@Composable private fun SettingSwitch(label: String, checked: Boolean, enabled: Boolean = true, onChecked: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f), color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)); Switch(checked, onChecked, enabled = enabled) } }
@Composable private fun SettingSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, valueText: String, onChange: (Float) -> Unit) { Column { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(valueText, style = MaterialTheme.typography.labelMedium) }; Slider(value, onChange, valueRange = range) } }

@Composable private fun readerSettingsPageTitle(page: ReaderSettingsPage): String = stringResource(when (page) {
    ReaderSettingsPage.HOME -> R.string.reader_settings
    ReaderSettingsPage.APPEARANCE -> R.string.reader_settings_group_appearance
    ReaderSettingsPage.NAVIGATION -> R.string.reader_settings_group_navigation
    ReaderSettingsPage.ASSISTANCE -> R.string.reader_settings_group_assistance
    ReaderSettingsPage.ADVANCED -> R.string.reader_settings_group_advanced
    ReaderSettingsPage.DISPLAY -> R.string.reader_display
    ReaderSettingsPage.AUTO_READ -> R.string.reader_auto_read
    ReaderSettingsPage.SPEECH -> R.string.reader_speech
    ReaderSettingsPage.LANGUAGE -> R.string.reader_language
    ReaderSettingsPage.DATA -> R.string.reader_data
})

@Composable private fun presetLabel(value: ReaderPreset): String = stringResource(when (value) { ReaderPreset.STANDARD -> R.string.reader_preset_standard; ReaderPreset.COMFORT -> R.string.reader_preset_comfort; ReaderPreset.LARGE -> R.string.reader_preset_large; ReaderPreset.NIGHT -> R.string.reader_preset_night; ReaderPreset.LOW_VISION -> R.string.reader_preset_low_vision; ReaderPreset.CUSTOM -> R.string.reader_preset_custom })
@Composable private fun paletteLabel(value: ReaderPalette): String = stringResource(when (value) { ReaderPalette.PAPER -> R.string.paper; ReaderPalette.LIGHT -> R.string.light; ReaderPalette.SEPIA -> R.string.reader_theme_sepia; ReaderPalette.NIGHT -> R.string.night; ReaderPalette.OLED -> R.string.reader_oled })
@Composable private fun typefaceLabel(value: ReaderTypeface): String = stringResource(when (value) { ReaderTypeface.SYSTEM -> R.string.system_font; ReaderTypeface.SERIF -> R.string.serif; ReaderTypeface.MONOSPACE -> R.string.reader_font_monospace; ReaderTypeface.CUSTOM -> R.string.reader_font_custom })
@Composable private fun weightLabel(value: ReaderFontWeight): String = stringResource(when (value) { ReaderFontWeight.NORMAL -> R.string.reader_weight_normal; ReaderFontWeight.MEDIUM -> R.string.reader_weight_medium; ReaderFontWeight.SEMIBOLD -> R.string.reader_weight_semibold })
@Composable private fun tapZoneLabel(value: ReaderTapZonePreset): String = stringResource(when (value) { ReaderTapZonePreset.BALANCED, ReaderTapZonePreset.CUSTOM -> R.string.reader_tap_zone_balanced; ReaderTapZonePreset.RIGHT_HANDED -> R.string.reader_tap_zone_right; ReaderTapZonePreset.LEFT_HANDED -> R.string.reader_tap_zone_left })
@Composable private fun volumeLabel(value: ReaderVolumeKeyMode): String = stringResource(when (value) { ReaderVolumeKeyMode.PAGE_WHEN_NOT_TTS -> R.string.reader_volume_except_tts; ReaderVolumeKeyMode.ALWAYS_PAGE -> R.string.reader_volume_always; ReaderVolumeKeyMode.SYSTEM_VOLUME -> R.string.reader_volume_system })
@Composable private fun orientationLabel(value: ReaderOrientation): String = stringResource(when (value) { ReaderOrientation.SYSTEM -> R.string.reader_orientation_system; ReaderOrientation.PORTRAIT -> R.string.reader_orientation_portrait; ReaderOrientation.LANDSCAPE -> R.string.reader_orientation_landscape })
@Composable private fun columnsLabel(value: ReaderWideColumns): String = stringResource(when (value) { ReaderWideColumns.AUTO -> R.string.reader_columns_auto; ReaderWideColumns.SINGLE -> R.string.reader_columns_single; ReaderWideColumns.DOUBLE -> R.string.reader_columns_double })
@Composable private fun chineseModeLabel(value: ChineseDisplayMode): String = stringResource(when (value) { ChineseDisplayMode.ORIGINAL -> R.string.chinese_original; ChineseDisplayMode.SIMPLIFIED -> R.string.chinese_simplified; ChineseDisplayMode.TRADITIONAL -> R.string.chinese_traditional; ChineseDisplayMode.TAIWAN -> R.string.chinese_taiwan; ChineseDisplayMode.TAIWAN_PHRASES -> R.string.chinese_taiwan_phrases; ChineseDisplayMode.HONG_KONG -> R.string.chinese_hong_kong })

@Composable private fun gestureActionLabel(value: ReaderGestureAction): String = stringResource(when (value) { ReaderGestureAction.CONTROLS -> R.string.reader_gesture_controls; ReaderGestureAction.BOOKMARK -> R.string.reader_gesture_bookmark; ReaderGestureAction.NEXT -> R.string.reader_gesture_next; ReaderGestureAction.PREVIOUS -> R.string.reader_gesture_previous; ReaderGestureAction.NONE -> R.string.reader_gesture_none })
