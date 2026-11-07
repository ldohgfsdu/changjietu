package com.changjietu.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverviewGeometryTest {

    @Test
    fun small_image_is_not_downsampled() {
        val s = OverviewGeometry.compute(300, 500)
        assertEquals(1, s.sampleSize)
        assertEquals(300, s.outWidth)
        assertEquals(500, s.outHeight)
    }

    @Test
    fun output_always_within_bounds_for_very_long_image() {
        val s = OverviewGeometry.compute(1440, 50_000)
        assertTrue("outWidth capped", s.outWidth <= OverviewGeometry.MAX_WIDTH)
        assertTrue("outHeight capped", s.outHeight <= OverviewGeometry.MAX_HEIGHT)
        assertTrue(s.outWidth >= 1)
        assertTrue(s.outHeight >= 1)
        // sampleSize 必须是 2 的幂，保证 BitmapRegionDecoder 行为可预期
        assertTrue("power of two", s.sampleSize and (s.sampleSize - 1) == 0)
    }

    @Test
    fun typical_phone_screenshot_uses_single_digit_sample() {
        val s = OverviewGeometry.compute(1080, 18_420)
        assertTrue(s.outWidth <= OverviewGeometry.MAX_WIDTH)
        assertTrue(s.outHeight <= OverviewGeometry.MAX_HEIGHT)
        assertTrue(s.sampleSize >= 1)
        assertEquals(0, s.sampleSize and (s.sampleSize - 1)) // 2 的幂
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejects_non_positive_dimensions() {
        OverviewGeometry.compute(0, 100)
    }
}
