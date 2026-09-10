package com.junchen.posestudio

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.junchen.posestudio.model.PoseProject
import com.junchen.posestudio.render.PoseBitmapRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PoseBitmapRendererInstrumentedTest {
    @Test
    fun transparentExportHasClearBackgroundAndOpaqueFigurePixels() {
        val bitmap = PoseBitmapRenderer.render(PoseProject(), 320, 320, transparentBackground = true)
        try {
            assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
            var opaquePixels = 0
            for (y in 0 until bitmap.height step 4) {
                for (x in 0 until bitmap.width step 4) {
                    if (Color.alpha(bitmap.getPixel(x, y)) > 0) opaquePixels++
                }
            }
            assertTrue("transparent export should still contain the mannequin", opaquePixels > 20)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun workspaceExportKeepsOpaqueBackground() {
        val bitmap = PoseBitmapRenderer.render(PoseProject(), 128, 128)
        try {
            assertEquals(255, Color.alpha(bitmap.getPixel(0, 0)))
        } finally {
            bitmap.recycle()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidExportDimensionsAreRejected() {
        PoseBitmapRenderer.render(PoseProject(), 0, 128)
    }
}
