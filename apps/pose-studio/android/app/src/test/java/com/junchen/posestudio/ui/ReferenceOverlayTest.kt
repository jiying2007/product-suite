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
}
