package com.changjietu.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test

class CropSafetyDefaultTest {
    @Test
    fun new_sessions_preserve_full_source_width_until_opt_in() {
        val settings = ProcessSettings()
        assertFalse("Default crop may discard marginal text on long screenshots", settings.cropMargins)
        assertEquals(0, settings.overlap)
    }

    @Test
    fun user_can_explicitly_enable_crop_without_changing_other_settings() {
        val settings = ProcessSettings()
        val optedIn = settings.copy(cropMargins = true)
        assertFalse(settings.cropMargins)
        assertEquals(true, optedIn.cropMargins)
        assertEquals(settings.targetHeight, optedIn.targetHeight)
    }
}
