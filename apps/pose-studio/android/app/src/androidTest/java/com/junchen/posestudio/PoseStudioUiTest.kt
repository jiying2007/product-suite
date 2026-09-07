package com.junchen.posestudio

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class PoseStudioUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appStartsWithPoseWorkspace() {
        rule.onNodeWithText("Pose Studio").assertIsDisplayed()
        rule.onNodeWithText("Pose").assertIsDisplayed()
        rule.onNodeWithText("Neutral").assertIsDisplayed()
    }
}
