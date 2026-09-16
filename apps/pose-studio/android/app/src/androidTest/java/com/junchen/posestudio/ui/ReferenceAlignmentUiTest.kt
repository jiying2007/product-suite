package com.junchen.posestudio.ui

import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.junchen.posestudio.MainActivity
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReferenceAlignmentUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @After
    fun cleanup() {
        rule.runOnUiThread { ReferenceOverlaySession.clear() }
    }

    @Test
    fun referenceAlignmentIsExplicitAndDirectlyDraggable() {
        rule.runOnUiThread {
            ReferenceOverlaySession.clear()
            ReferenceOverlaySession.use(
                Uri.parse("android.resource://com.junchen.posestudio/mipmap/ic_launcher"),
            )
        }

        rule.onNodeWithText("Done aligning").assertIsDisplayed()
        val description = "Pose canvas. Select and drag joints, drag empty space to orbit, or use two fingers to zoom and pan."
        rule.onNodeWithContentDescription(description).performTouchInput {
            swipe(center, center + Offset(120f, 48f), durationMillis = 350)
        }
        rule.runOnIdle {
            assertTrue(ReferenceOverlaySession.offsetXDp > 0f)
            assertTrue(ReferenceOverlaySession.offsetYDp > 0f)
        }

        rule.onNodeWithText("Done aligning").performClick()
        rule.onNodeWithText("Align reference").assertIsDisplayed()
    }
}
