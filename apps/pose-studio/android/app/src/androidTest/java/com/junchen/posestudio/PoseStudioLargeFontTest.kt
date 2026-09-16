package com.junchen.posestudio

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.junchen.posestudio.ui.ReferenceOverlaySession
import org.junit.After
import org.junit.Rule
import org.junit.Test

class PoseStudioLargeFontTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @After
    fun cleanup() {
        rule.runOnUiThread { ReferenceOverlaySession.clear() }
    }

    @Test
    fun primaryActionsRemainReachableAtLargeFontScale() {
        rule.onNodeWithText("Pose Studio").assertIsDisplayed()
        rule.onNodeWithText("Save").assertIsDisplayed()
        rule.onNodeWithText("Open").assertIsDisplayed()
        rule.onNodeWithText("Pose").assertIsDisplayed()

        rule.runOnUiThread {
            ReferenceOverlaySession.clear()
            ReferenceOverlaySession.use(
                Uri.parse("android.resource://com.junchen.posestudio/mipmap/ic_launcher"),
            )
        }
        rule.onNodeWithText("Choose image").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Hide").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Reset").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Clear").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Drawing proportions").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Balanced").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Long legs").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Long torso").performScrollTo().assertIsDisplayed()

        rule.onNodeWithText("Scene").performClick()
        rule.onNodeWithText("Front").assertIsDisplayed()
        rule.onNodeWithText("Directional light").performScrollTo().assertIsDisplayed()

        rule.onNodeWithText("Export").performClick()
        rule.onNodeWithText("Drawing exports").assertIsDisplayed()
        rule.onNodeWithText("Project").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Name").performScrollTo().assertIsDisplayed()
    }
}
