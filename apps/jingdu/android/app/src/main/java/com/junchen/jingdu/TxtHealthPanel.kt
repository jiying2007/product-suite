@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.junchen.jingdu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FormatAlignLeft
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun TxtHealthPanel(state: AppUiState, actions: JingduActions) {
    val report = state.txtHealthReport
    val layoutPreview = remember(state.pageText) { SmartLayout.present(state.pageText) }
    ModalBottomSheet(
        onDismissRequest = actions.onClosePanel,
        sheetGesturesEnabled = false,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f).testTag("txt-health-list"),
            contentPadding = PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.HealthAndSafety, null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(stringResource(R.string.txt_health_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.txt_health_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (report == null) {
                item {
                    Text(stringResource(R.string.txt_health_waiting), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.txt_health_score, report.healthScore), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            LinearProgressIndicator(progress = { report.healthScore / 100f }, modifier = Modifier.fillMaxWidth())
                            Text(
                                stringResource(
                                    when (report.severity) {
                                        DoctorSeverity.GOOD -> R.string.txt_health_good
                                        DoctorSeverity.NOTICE -> R.string.txt_health_notice
                                        DoctorSeverity.WARNING -> R.string.txt_health_warning
                                    }
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                item {
                    HealthStep(
                        icon = { Icon(Icons.Outlined.TextFields, null) },
                        title = stringResource(R.string.txt_health_encoding),
                        detail = stringResource(R.string.txt_health_encoding_detail, report.encoding, report.encodingScore),
                        action = stringResource(R.string.txt_health_review),
                    ) { actions.onOpenPanel(ReaderPanel.ENCODING) }
                }

                item {
                    HealthStep(
                        icon = { Icon(Icons.Outlined.FormatAlignLeft, null) },
                        title = stringResource(R.string.txt_health_layout),
                        detail = if (report.hardWrapDetected) {
                            stringResource(R.string.txt_health_layout_detected, report.estimatedJoinedBreaks)
                        } else {
                            stringResource(R.string.txt_health_layout_good)
                        },
                        action = if (state.settings.compressBlankLines) stringResource(R.string.txt_health_layout_on) else stringResource(R.string.txt_health_enable),
                    ) {
                        actions.onSettingsChanged(state.settings.copy(compressBlankLines = true))
                    }
                }

                if (report.hardWrapDetected && layoutPreview.hardWrapDetected && layoutPreview.text != state.pageText) {
                    item {
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.fillMaxWidth().padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(stringResource(R.string.txt_health_layout_preview), fontWeight = FontWeight.SemiBold)
                                Text(stringResource(R.string.txt_health_layout_before), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(state.pageText.take(520), style = MaterialTheme.typography.bodySmall, maxLines = 5)
                                Text(stringResource(R.string.txt_health_layout_after), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(layoutPreview.text.take(520), style = MaterialTheme.typography.bodySmall, maxLines = 5)
                                Text(
                                    stringResource(R.string.txt_health_layout_preview_note, layoutPreview.joinedBreaks),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                item {
                    HealthStep(
                        icon = { Icon(Icons.Outlined.MenuBook, null) },
                        title = stringResource(R.string.txt_health_toc),
                        detail = stringResource(R.string.txt_health_toc_detail, report.chapterCount, report.tocAnomalies, report.tocScore),
                        action = stringResource(R.string.txt_health_review),
                    ) {
                        actions.onClosePanel()
                        actions.onOpenPanel(ReaderPanel.CHAPTERS)
                    }
                }

                item {
                    HealthStep(
                        icon = { Icon(Icons.Outlined.AutoFixHigh, null) },
                        title = stringResource(R.string.txt_health_clean),
                        detail = stringResource(R.string.txt_health_clean_detail, report.noiseCandidates, report.cleanScore),
                        action = if (report.noiseCandidates > 0) stringResource(R.string.txt_health_clean_now) else stringResource(R.string.txt_health_review),
                    ) {
                        if (report.noiseCandidates > 0) actions.onAnalyzeSmartClean()
                        else actions.onOpenPanel(ReaderPanel.CLEAN)
                    }
                }

                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.txt_health_source_safe), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                item {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { actions.onOpenPanel(ReaderPanel.TXT_HEALTH) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.txt_health_recheck))
                        }
                        OutlinedButton(onClick = { actions.onOpenPanel(ReaderPanel.DOCTOR) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.txt_health_details))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthStep(
    icon: @Composable () -> Unit,
    title: String,
    detail: String,
    action: String,
    onClick: () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                icon()
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(action) }
        }
    }
}
