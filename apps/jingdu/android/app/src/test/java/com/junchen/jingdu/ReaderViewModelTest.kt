package com.junchen.jingdu

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderViewModelTest {
    @Test
    fun pageTurnStateNeverPublishesFullPageAnimationDirection() {
        val viewModel = ReaderViewModel()

        viewModel.replace(AppUiState(pageTurnDirection = 1))
        assertEquals(0, viewModel.state.value.pageTurnDirection)

        viewModel.replace(AppUiState(pageTurnDirection = -1))
        assertEquals(0, viewModel.state.value.pageTurnDirection)
    }
}
