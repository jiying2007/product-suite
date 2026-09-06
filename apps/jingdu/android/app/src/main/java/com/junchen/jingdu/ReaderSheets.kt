@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.junchen.jingdu

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable internal fun SearchSheet(state: AppUiState, actions: JingduActions) {
    ModalBottomSheet(onDismissRequest = actions.onClosePanel) { Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        SheetTitle(stringResource(R.string.full_text_search), stringResource(R.string.search_subtitle))
        OutlinedTextField(value = state.searchQuery, onValueChange = actions.onSearchQueryChanged, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(stringResource(R.string.search_hint)) }, trailingIcon = { IconButton(onClick = { actions.onSearch(state.searchQuery) }) { Icon(Icons.Default.Search, stringResource(R.string.search)) } })
        Spacer(Modifier.height(12.dp)); if (state.searchResults.isEmpty()) Text(stringResource(R.string.search_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) else LazyColumn(Modifier.heightIn(max = 480.dp)) { items(state.searchResults, key = { it.offset }) { hit -> TextButton(onClick = { actions.onJump(hit.offset) }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) { Text(ReaderTextPresentation.display(hit.context, state.settings), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start) } } }
    } }
}

@Composable internal fun BookmarksSheet(state: AppUiState, actions: JingduActions) {
    ModalBottomSheet(onDismissRequest = actions.onClosePanel) { Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        SheetTitle(stringResource(R.string.bookmarks), stringResource(if (state.cleanMode) R.string.bookmarks_clean_note else R.string.bookmarks_revision_note))
        Button(onClick = actions.onAddBookmark, enabled = !state.cleanMode, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Bookmark, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.add_current_position)) }
        Spacer(Modifier.height(10.dp)); if (state.bookmarks.isEmpty()) Text(stringResource(R.string.no_bookmarks), color = MaterialTheme.colorScheme.onSurfaceVariant) else LazyColumn(Modifier.heightIn(max = 480.dp)) { items(state.bookmarks, key = { it.offset }) { mark -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = { actions.onJump(mark.offset) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.bookmark_row, (mark.progressFraction * 100).roundToInt(), mark.offset), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start) }; IconButton(onClick = { actions.onDeleteBookmark(mark.offset) }) { Icon(Icons.Default.Delete, stringResource(R.string.delete_bookmark)) } } } }
    } }
}

@Composable internal fun CleanSheet(state: AppUiState, actions: JingduActions) {
    var find by rememberSaveable { mutableStateOf("") }
    var replacement by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(RepairRuleMode.LITERAL) }
    var globalFind by rememberSaveable { mutableStateOf("") }
    var globalReplacement by rememberSaveable { mutableStateOf("") }
    var globalMode by rememberSaveable { mutableStateOf(RepairRuleMode.LINE_GLOB) }
    val selectedCount = state.noiseCandidates.count { it.selected }
    val selectedNoiseTotal = state.noiseCandidates.filter { it.selected }.sumOf { it.count }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = actions.onClosePanel, sheetState = sheetState, sheetGesturesEnabled = false) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SheetTitle(stringResource(R.string.clean), stringResource(R.string.clean_subtitle)) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !state.cleanMode, onClick = { if (state.cleanMode) actions.onToggleCleanPreview() }, label = { Text(stringResource(R.string.original_text)) })
                    FilterChip(selected = state.cleanMode, onClick = { if (!state.cleanMode) actions.onToggleCleanPreview() }, label = { Text(stringResource(R.string.clean_preview)) })
                    AssistChip(onClick = actions.onExportClean, label = { Text(stringResource(R.string.export_txt)) })
                }
            }

            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.AutoFixHigh, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                stringResource(R.string.smart_clean),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (state.proUnlocked) AssistChip(onClick = {}, label = { Text("Pro") }, leadingIcon = { Icon(Icons.Outlined.WorkspacePremium, null) })
                        }
                        OutlinedButton(onClick = actions.onAnalyzeSmartClean, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(if (state.smartCleanAnalyzed) R.string.rescan_noise else R.string.scan_noise_free))
                        }
                        if (state.noiseCandidates.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    stringResource(R.string.noise_summary, state.noiseCandidates.size, selectedCount, selectedNoiseTotal),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(stringResource(R.string.smart_clean_apply_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            NoiseCandidateCard(state.noiseCandidates.first(), 0, actions)
                        }
                        OutlinedButton(onClick = { actions.onOpenPanel(ReaderPanel.SMART_CLEAN_LAB) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Psychology, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.smart_clean4))
                        }
                    }
                }
            }

            if (state.noiseCandidates.isNotEmpty()) {
                items((1 until minOf(20, state.noiseCandidates.size)).toList(), key = { it }) { index ->
                    NoiseCandidateCard(state.noiseCandidates[index], index, actions)
                }
                item {
                    val suffix = state.proPrice?.let { stringResource(R.string.price_suffix, it) } ?: ""
                    Button(
                        onClick = if (state.proUnlocked) actions.onApplySmartClean else actions.onUpgradePro,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedCount > 0,
                    ) {
                        if (!state.proUnlocked) {
                            Icon(Icons.Outlined.Lock, null)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (state.proUnlocked) stringResource(R.string.apply_selected_preview) else stringResource(R.string.unlock_pro_apply, suffix))
                    }
                }
                if (state.smartCleanUndoAvailable) {
                    item { OutlinedButton(onClick = actions.onUndoSmartClean, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.smart_clean_undo)) } }
                }
                if (!state.proUnlocked) {
                    item { TextButton(onClick = actions.onRestorePro, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.restore_pro_question)) } }
                }
            } else if (state.smartCleanAnalyzed) {
                item { Text(stringResource(R.string.no_noise_found), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (state.smartCleanUndoAvailable) {
                    item { OutlinedButton(onClick = actions.onUndoSmartClean, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.smart_clean_undo)) } }
                }
            } else if (state.smartCleanUndoAvailable) {
                item { OutlinedButton(onClick = actions.onUndoSmartClean, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.smart_clean_undo)) } }
            }

            item { Text(stringResource(R.string.smart_clean_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { Text(stringResource(R.string.smart_clean_pack, BuiltinCleanRules.PACK_VERSION), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
            item { Text(stringResource(R.string.smart_clean_refiner), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

            item { HorizontalDivider() }
            item { Text(stringResource(R.string.book_manual_rules), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            item { RuleModeChips(mode, state.proUnlocked) { mode = it } }
            item { OutlinedTextField(find, { find = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(if (mode == RepairRuleMode.LITERAL) R.string.find_text else R.string.line_pattern_hint)) }, singleLine = true) }
            item { OutlinedTextField(replacement, { replacement = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.replacement_hint)) }, singleLine = true) }
            item { Button(onClick = { actions.onAddRule(mode, find, replacement); if (mode == RepairRuleMode.LITERAL || state.proUnlocked) { find = ""; replacement = "" } }, enabled = find.isNotBlank()) { Text(stringResource(if (mode == RepairRuleMode.LINE_GLOB && !state.proUnlocked) R.string.unlock_pro_add_wildcard else R.string.add_book_rule)) } }
            if (state.repairRules.isNotEmpty()) item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.book_rules_count, state.repairRules.size)); TextButton(onClick = actions.onClearRules) { Text(stringResource(R.string.clear)) } } }
            items(state.repairRules.indices.toList()) { i -> RuleCard(state.repairRules[i]) { actions.onDeleteRule(i) } }
            item { HorizontalDivider() }
            item { Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.global_rule_library), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Spacer(Modifier.width(8.dp)); AssistChip(onClick = {}, label = { Text("Pro") }) } }
            item { Text(stringResource(R.string.global_rule_body), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (!state.proUnlocked) item { ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(stringResource(R.string.pro_lifetime_title), fontWeight = FontWeight.SemiBold); Text(stringResource(R.string.pro_lifetime_body), color = MaterialTheme.colorScheme.onSurfaceVariant); val suffix = state.proPrice?.let { stringResource(R.string.price_suffix, it) } ?: ""; Button(onClick = actions.onUpgradePro, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.WorkspacePremium, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.unlock_jingdu_pro, suffix)) }; TextButton(onClick = actions.onRestorePro, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.restore_purchase)) } } } }
            else {
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { AssistChip(onClick = actions.onInstallRecommendedRules, label = { Text(stringResource(R.string.install_recommended_rules)) }); AssistChip(onClick = actions.onImportGlobalRules, label = { Text(stringResource(R.string.import_action)) }); AssistChip(onClick = actions.onExportGlobalRules, label = { Text(stringResource(R.string.export_action)) }) } }
                item { RuleModeChips(globalMode, true) { globalMode = it } }
                item { OutlinedTextField(globalFind, { globalFind = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(if (globalMode == RepairRuleMode.LITERAL) R.string.global_find_text else R.string.global_line_pattern)) }, singleLine = true) }
                item { OutlinedTextField(globalReplacement, { globalReplacement = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.replacement_hint)) }, singleLine = true) }
                item { Button(onClick = { actions.onAddGlobalRule(globalMode, globalFind, globalReplacement); globalFind = ""; globalReplacement = "" }, enabled = globalFind.isNotBlank()) { Text(stringResource(R.string.add_global_rule)) } }
                if (state.globalRules.isNotEmpty()) item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(stringResource(R.string.global_rules_count, state.globalRules.size)); TextButton(onClick = actions.onClearGlobalRules) { Text(stringResource(R.string.clear)) } } }
                items(state.globalRules.indices.toList()) { i -> RuleCard(state.globalRules[i]) { actions.onDeleteGlobalRule(i) } }
            }
        }
    }
}

@Composable private fun NoiseCandidateCard(c: NoiseCandidateModel, index: Int, actions: JingduActions) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
            Checkbox(c.selected, { actions.onToggleNoiseCandidate(index) })
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("${riskLabel(c.risk)} · ${localizeNoiseReason(c.reason)} · ${c.score}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.smart_clean_impact, c.count, c.impactChars), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(riskExplanation(c.risk), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(c.text, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable private fun RuleModeChips(mode: RepairRuleMode, pro: Boolean, onMode: (RepairRuleMode) -> Unit) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(mode == RepairRuleMode.LITERAL, { onMode(RepairRuleMode.LITERAL) }, label = { Text(stringResource(R.string.exact_text)) }); FilterChip(mode == RepairRuleMode.LINE_GLOB, { onMode(RepairRuleMode.LINE_GLOB) }, label = { Text(stringResource(if (pro) R.string.whole_line_glob else R.string.whole_line_glob_pro)) }) } }
@Composable private fun RuleCard(rule: RepairRule, onDelete: () -> Unit) { ElevatedCard(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(if (rule.mode == RepairRuleMode.LINE_GLOB) stringResource(R.string.rule_glob, rule.find) else rule.find, fontWeight = FontWeight.Medium); Text(if (rule.replacement.isEmpty()) stringResource(R.string.rule_delete_arrow) else stringResource(R.string.rule_replace_arrow, rule.replacement), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, stringResource(R.string.delete_clean_rule)) } } } }

@Composable internal fun EncodingSheet(state: AppUiState, actions: JingduActions) { ModalBottomSheet(onDismissRequest = actions.onClosePanel) { Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) { SheetTitle(stringResource(R.string.text_encoding), stringResource(R.string.encoding_subtitle)); LazyColumn(Modifier.heightIn(max = 520.dp)) { items(BookRepository.ENCODINGS.toList()) { encoding -> Row(Modifier.fillMaxWidth().selectable(state.currentBook?.encoding == encoding) { actions.onEncodingSelected(encoding) }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) { Text(if (encoding == BookRepository.AUTO) stringResource(R.string.auto_detect) else encoding, modifier = Modifier.weight(1f)); if (state.currentBook?.encoding == encoding) Text(stringResource(R.string.current), color = MaterialTheme.colorScheme.primary) }; HorizontalDivider() } } } } }

@Composable private fun SheetTitle(title: String, subtitle: String) { Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(4.dp)); Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable private fun localizeNoiseReason(code: String): String = when (code) { "url" -> stringResource(R.string.noise_reason_url); "promo" -> stringResource(R.string.noise_reason_promo); "repeated" -> stringResource(R.string.noise_reason_repeated); "promo_repeated" -> stringResource(R.string.noise_reason_promo_repeated); "inline_fragment" -> stringResource(R.string.noise_reason_inline_fragment); "garbled_line" -> stringResource(R.string.noise_reason_garbled_line); else -> code }
@Composable private fun riskLabel(risk: NoiseRisk): String = when (risk) { NoiseRisk.HIGH -> stringResource(R.string.smart_clean_risk_high); NoiseRisk.MEDIUM -> stringResource(R.string.smart_clean_risk_medium); NoiseRisk.LOW -> stringResource(R.string.smart_clean_risk_low) }
@Composable private fun riskExplanation(risk: NoiseRisk): String = when (risk) { NoiseRisk.HIGH -> stringResource(R.string.smart_clean_explain_high); NoiseRisk.MEDIUM -> stringResource(R.string.smart_clean_explain_medium); NoiseRisk.LOW -> stringResource(R.string.smart_clean_explain_low) }
