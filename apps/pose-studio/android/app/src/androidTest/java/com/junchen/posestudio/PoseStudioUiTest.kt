package com.junchen.posestudio

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import org.junit.Rule
import org.junit.Test

class PoseStudioUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun primaryWorkspaceAndSpeedToolsAreUsable() {
        rule.onNodeWithText("Pose Studio").assertIsDisplayed()
        rule.onNodeWithText("Pose").assertIsDisplayed()
        rule.onNodeWithText("Neutral").assertIsDisplayed()
        rule.onNodeWithText("Mirror").performClick()
        rule.onNodeWithText("Undo").performClick()
        rule.onNodeWithText("Camera").performClick()
        rule.onNodeWithText("Front").assertIsDisplayed()
        rule.onNodeWithText("Light").performClick()
        rule.onNodeWithText("Directional light").assertIsDisplayed()
    }

    @Test
    fun sceneAcceptsDirectOrbitGestureWithoutCrashing() {
        val description = "Pose canvas. Select and drag joints, drag empty space to orbit, or use two fingers to zoom and pan."
        rule.onNodeWithContentDescription(description).performTouchInput {
            swipe(center, center + Offset(120f, 24f), durationMillis = 350)
        }
        rule.onNodeWithText("Pose Studio").assertIsDisplayed()
    }
}
