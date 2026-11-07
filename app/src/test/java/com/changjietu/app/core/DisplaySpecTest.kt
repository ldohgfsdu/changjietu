package com.changjietu.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplaySpecTest {

    @Test
    fun portrait_convention_uses_short_and_long_sides() {
        val spec = DisplaySpec.of(widthPx = 2400, heightPx = 1080)
        assertEquals(1080, spec.shortSidePx)
        assertEquals(2400, spec.longSidePx)
        assertEquals(2400, spec.maxSliceHeightPx)
    }

    @Test
    fun percent_of_screen_height() {
        val spec = DisplaySpec.of(1080, 2400)
        assertEquals(100, spec.percentOfScreenHeight(2400))
        assertEquals(50, spec.percentOfScreenHeight(1200))
        assertEquals(1, spec.percentOfScreenHeight(1))
        assertEquals(100, spec.percentOfScreenHeight(3000))
    }

    @Test
    fun percent_of_screen_width() {
        val spec = DisplaySpec.of(1080, 2400)
        assertEquals(100, spec.percentOfScreenWidth(1080))
        assertEquals(50, spec.percentOfScreenWidth(540))
        assertEquals(100, spec.percentOfScreenWidth(1440))
    }

    @Test
    fun max_slice_height_is_clamped_to_decode_safe_range() {
        assertEquals(8000, DisplaySpec.of(1440, 50000).maxSliceHeightPx)
        assertEquals(400, DisplaySpec.of(200, 300).maxSliceHeightPx)
    }
}
