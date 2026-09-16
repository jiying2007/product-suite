package com.junchen.posestudio

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test

class PoseStudioLargeFontTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun primaryActionsRemainReachableAtLargeFontScale() {
        rule.onNodeWithText("Pose Studio").assertIsDisplayed()
        rule.onNodeWithText("Save").assertIsDisplayed()
        rule.onNodeWithText("Open").assertIsDisplayed()
        rule.onNodeWithText("Pose").assertIsDisplayed()

        rule.onNodeWithText("Scene").performClick()
        rule.onNodeWithText("Front").assertIsDisplayed()
        rule.onNodeWithText("Directional light").performScrollTo().assertIsDisplayed()

        rule.onNodeWithText("Export").performClick()
        rule.onNodeWithText("Drawing exports").assertIsDisplayed()
        rule.onNodeWithText("Project").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Name").performScrollTo().assertIsDisplayed()
    }
}
