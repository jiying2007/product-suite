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
        val alignmentDescription =
            "Choose a local image, then use Align reference on the canvas to drag and pinch it into place. " +
                "Sliders remain available for precise adjustment."
        rule.onNodeWithContentDescription(alignmentDescription).performTouchInput {
            swipe(
                centerRight + Offset(-240f, -100f),
                centerRight + Offset(-100f, -52f),
                durationMillis = 350,
            )
        }
        rule.runOnIdle {
            assertTrue(ReferenceOverlaySession.offsetXDp > 0f)
            assertTrue(ReferenceOverlaySession.offsetYDp > 0f)
        }

        rule.onNodeWithText("Done aligning").performClick()
        rule.onNodeWithText("Align reference").assertIsDisplayed()
    }
}
