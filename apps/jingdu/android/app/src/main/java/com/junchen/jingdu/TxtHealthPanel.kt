@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.junchen.jingdu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun TxtHealthPanel(state: AppUiState, actions: JingduActions) {
    val report = state.txtHealthReport
    ModalBottomSheet(onDismissRequest = actions.onClosePanel) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.HealthAndSafety, null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(stringResource(R.string.txt_health_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.txt_health_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (report == null) {
                Text(stringResource(R.string.txt_health_waiting), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
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

                HealthStep(
                    icon = { Icon(Icons.Outlined.TextFields, null) },
                    title = stringResource(R.string.txt_health_encoding),
                    detail = stringResource(R.string.txt_health_encoding_detail, report.encoding, report.encodingScore),
                    action = stringResource(R.string.txt_health_review),
                ) { actions.onOpenPanel(ReaderPanel.ENCODING) }

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

                HealthStep(
                    icon = { Icon(Icons.Outlined.MenuBook, null) },
                    title = stringResource(R.string.txt_health_toc),
                    detail = stringResource(R.string.txt_health_toc_detail, report.chapterCount, report.tocAnomalies, report.tocScore),
                    action = stringResource(R.string.txt_health_review),
                ) { actions.onOpenPanel(ReaderPanel.CHAPTERS) }

                HealthStep(
                    icon = { Icon(Icons.Outlined.AutoFixHigh, null) },
                    title = stringResource(R.string.txt_health_clean),
                    detail = stringResource(R.string.txt_health_clean_detail, report.noiseCandidates, report.cleanScore),
                    action = if (report.noiseCandidates > 0) stringResource(R.string.txt_health_clean_now) else stringResource(R.string.txt_health_review),
                ) {
                    if (report.noiseCandidates > 0) actions.onAnalyzeSmartClean()
                    else actions.onOpenPanel(ReaderPanel.CLEAN)
                }

                ElevatedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.txt_health_source_safe), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(Modifier.height(2.dp))
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
