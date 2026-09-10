package com.junchen.jingdu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun AutoReadSettings(state: AppUiState, actions: JingduActions) = SettingsList {
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
internal fun LanguageSettings(state: AppUiState, actions: JingduActions) = SettingsList {
    val s = state.settings
    var overrides by rememberSaveable(s.chineseOverrides) { mutableStateOf(s.chineseOverrides) }
    Section(stringResource(R.string.chinese_conversion)) { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(ChineseDisplayMode.entries) { value -> FilterChip(s.chineseMode == value, { actions.onSettingsChanged(s.copy(chineseMode = value)) }, label = { Text(chineseModeLabel(value)) }) } } }
    OutlinedTextField(overrides, { overrides = it.take(16 * 1024) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.chinese_overrides)) }, minLines = 4, maxLines = 10)
    Button({ actions.onSettingsChanged(s.copy(chineseOverrides = overrides)) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.save_dictionary)) }
}

@Composable
private fun chineseModeLabel(value: ChineseDisplayMode): String = stringResource(when (value) {
    ChineseDisplayMode.ORIGINAL -> R.string.chinese_original
    ChineseDisplayMode.SIMPLIFIED -> R.string.chinese_simplified
    ChineseDisplayMode.TRADITIONAL -> R.string.chinese_traditional
    ChineseDisplayMode.TAIWAN -> R.string.chinese_taiwan
    ChineseDisplayMode.TAIWAN_PHRASES -> R.string.chinese_taiwan_phrases
    ChineseDisplayMode.HONG_KONG -> R.string.chinese_hong_kong
})
