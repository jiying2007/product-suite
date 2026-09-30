package com.junchen.jingdu

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource

private enum class ReaderMoreMenuPage { MAIN, TEXT_TOOLS, MORE_TOOLS }

@Composable
internal fun ReaderMoreMenu(actions: JingduActions, onDismiss: () -> Unit) {
    var page by remember { mutableStateOf(ReaderMoreMenuPage.MAIN) }
    DropdownMenu(true, onDismissRequest = onDismiss) {
        fun close(action: () -> Unit) { onDismiss(); action() }
        when (page) {
            ReaderMoreMenuPage.MAIN -> {
                DropdownMenuItem({ Text(stringResource(R.string.txt_health_title)) }, { close { actions.onOpenPanel(ReaderPanel.TXT_HEALTH) } }, leadingIcon = { Icon(Icons.Outlined.HealthAndSafety, null) })
                DropdownMenuItem({ Text(stringResource(R.string.full_text_search)) }, { close { actions.onOpenPanel(ReaderPanel.SEARCH) } }, leadingIcon = { Icon(Icons.Default.Search, null) })
                DropdownMenuItem({ Text(stringResource(R.string.reader_annotations)) }, { close { actions.onOpenPanel(ReaderPanel.ANNOTATIONS) } }, leadingIcon = { Icon(Icons.Outlined.EditNote, null) })
                DropdownMenuItem({ Text(stringResource(R.string.reader_reading_history)) }, { close { actions.onOpenPanel(ReaderPanel.READING_HISTORY) } }, leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null) })
                DropdownMenuItem({ Text(stringResource(R.string.reader_text_tools)) }, { page = ReaderMoreMenuPage.TEXT_TOOLS }, leadingIcon = { Icon(Icons.Outlined.AutoFixHigh, null) })
                DropdownMenuItem({ Text(stringResource(R.string.reader_more_tools)) }, { page = ReaderMoreMenuPage.MORE_TOOLS }, leadingIcon = { Icon(Icons.Default.MoreHoriz, null) })
            }
            ReaderMoreMenuPage.TEXT_TOOLS -> {
                DropdownMenuItem({ Text(stringResource(R.string.reader_more_menu_back)) }, { page = ReaderMoreMenuPage.MAIN }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) })
                DropdownMenuItem({ Text(stringResource(R.string.txt_doctor)) }, { close { actions.onOpenPanel(ReaderPanel.DOCTOR) } }, leadingIcon = { Icon(Icons.Outlined.HealthAndSafety, null) })
                DropdownMenuItem({ Text(stringResource(R.string.smart_clean4)) }, { close { actions.onOpenPanel(ReaderPanel.SMART_CLEAN_LAB) } }, leadingIcon = { Icon(Icons.Outlined.Psychology, null) })
                DropdownMenuItem({ Text(stringResource(R.string.clean)) }, { close { actions.onOpenPanel(ReaderPanel.CLEAN) } }, leadingIcon = { Icon(Icons.Outlined.AutoFixHigh, null) })
            }
            ReaderMoreMenuPage.MORE_TOOLS -> {
                DropdownMenuItem({ Text(stringResource(R.string.reader_more_menu_back)) }, { page = ReaderMoreMenuPage.MAIN }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) })
                DropdownMenuItem({ Text(stringResource(R.string.bookmarks)) }, { close { actions.onOpenPanel(ReaderPanel.BOOKMARKS) } }, leadingIcon = { Icon(Icons.Outlined.Bookmarks, null) })
                DropdownMenuItem({ Text(stringResource(R.string.reader_reading_map)) }, { close { actions.onOpenPanel(ReaderPanel.READING_MAP); actions.onEnsureChapters() } }, leadingIcon = { Icon(Icons.Outlined.Map, null) })
            }
        }
    }
}
