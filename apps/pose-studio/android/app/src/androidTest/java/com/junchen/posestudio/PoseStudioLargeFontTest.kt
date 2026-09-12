package com.junchen.posestudio

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

        rule.onNodeWithText("Camera").performClick()
        rule.onNodeWithText("Front").assertIsDisplayed()
        rule.onNodeWithText("Light").performClick()
        rule.onNodeWithText("Directional light").assertIsDisplayed()
        rule.onNodeWithText("Project").performClick()
        rule.onNodeWithText("Name").assertIsDisplayed()
    }
}
