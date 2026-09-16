package com.junchen.posestudio.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReferenceOverlayTest {
    @Test
    fun sampleSizeKeepsNormalImagesAtFullResolution() {
        assertEquals(1, referenceSampleSize(1440, 1920, 2048))
    }

    @Test
    fun sampleSizeBoundsLargeImagesByPowerOfTwo() {
        assertEquals(2, referenceSampleSize(4032, 3024, 2048))
        assertEquals(4, referenceSampleSize(8000, 6000, 2048))
    }

    @Test
    fun invalidBoundsFailSafeToFullResolution() {
        assertEquals(1, referenceSampleSize(0, 1000, 2048))
        assertEquals(1, referenceSampleSize(1000, 1000, 0))
    }

    @Test
    fun directAlignmentScaleIsBounded() {
        assertEquals(2.5f, referenceScaleAfterZoom(2f, 2f), 0.0001f)
        assertEquals(0.5f, referenceScaleAfterZoom(0.8f, 0.2f), 0.0001f)
        assertEquals(1.25f, referenceScaleAfterZoom(1f, 1.25f), 0.0001f)
    }

    @Test
    fun invalidZoomDoesNotCorruptReferenceScale() {
        assertEquals(1.2f, referenceScaleAfterZoom(1.2f, Float.NaN), 0.0001f)
        assertEquals(1f, referenceScaleAfterZoom(Float.NaN, 1f), 0.0001f)
    }

    @Test
    fun directAlignmentPanIsBoundedAndFinite() {
        assertEquals(220f, referenceOffsetAfterPan(210f, 40f), 0.0001f)
        assertEquals(-220f, referenceOffsetAfterPan(-210f, -40f), 0.0001f)
        assertEquals(30f, referenceOffsetAfterPan(10f, 20f), 0.0001f)
        assertEquals(0f, referenceOffsetAfterPan(Float.NaN, 0f), 0.0001f)
    }
}
