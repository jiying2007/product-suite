package com.junchen.jingdu

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs

data class ReaderLocationState(val canBack: Boolean = false, val canForward: Boolean = false)

/**
 * Screen-level state owner. MainActivity remains the Android integration host (SAF, Billing and
 * service intents); all UI-visible state and transient reader location history follow UDF here.
 */
internal class ReaderViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = mutableState.asStateFlow()
    private val mutableHotPanel = MutableStateFlow<ReaderPanel?>(null)
    val hotPanel: StateFlow<ReaderPanel?> = mutableHotPanel.asStateFlow()

    fun openHotPanel(panel: ReaderPanel) {
        require(panel == ReaderPanel.QUICK_SETTINGS || panel == ReaderPanel.CHAPTERS)
        mutableHotPanel.value = panel
    }
    fun closeHotPanel() { mutableHotPanel.value = null }

    private val back = ArrayDeque<Long>()
    private val forward = ArrayDeque<Long>()
    private val mutableLocation = MutableStateFlow(ReaderLocationState())
    val location: StateFlow<ReaderLocationState> = mutableLocation.asStateFlow()
    private var locationBookId: String? = null

    fun replace(value: AppUiState) {
        val nextBookId = value.currentBook?.id
        if (nextBookId != locationBookId) {
            locationBookId = nextBookId
            mutableHotPanel.value = null
            resetLocationHistory()
        }
        // Full-page SLIDE transitions force the whole paged text layout through animation on every
        // navigation step. That path has repeatedly shown insufficient hosted P95/P99 headroom and
        // also conflicts with the Reader's restrained-motion direction. Keep the persisted setting
        // backward-compatible, but publish page changes as immediate content swaps so the heavy
        // AnimatedVisibility branch is never entered. A future lightweight cue can consume a
        // separate signal without putting the text layout back on the animation hot path.
        mutableState.value = if (value.pageTurnDirection == 0) value else value.copy(pageTurnDirection = 0)
    }

    fun reduce(block: (AppUiState) -> AppUiState) { replace(block(mutableState.value)) }

    fun trackLocation(current: Long, target: Long, length: Long) {
        if (length <= 0) return
        val bounded = target.coerceIn(0, (length - 1).coerceAtLeast(0))
        if (abs(bounded - current) < 2L) return
        back.addLast(current)
        while (back.size > MAX_LOCATION_HISTORY) back.removeFirst()
        forward.clear()
        publishLocation()
    }

    fun backTarget(current: Long): Long? {
        if (back.isEmpty()) return null
        val target = back.removeLast()
        forward.addLast(current)
        publishLocation()
        return target
    }

    fun forwardTarget(current: Long): Long? {
        if (forward.isEmpty()) return null
        val target = forward.removeLast()
        back.addLast(current)
        while (back.size > MAX_LOCATION_HISTORY) back.removeFirst()
        publishLocation()
        return target
    }

    fun resetLocationHistory() {
        back.clear(); forward.clear(); publishLocation()
    }

    private fun publishLocation() { mutableLocation.value = ReaderLocationState(back.isNotEmpty(), forward.isNotEmpty()) }

    private companion object { const val MAX_LOCATION_HISTORY = 100 }
}
