package com.changjietu.app.core

import com.changjietu.app.image.analysis.Margins
import org.junit.Assert.assertEquals
import org.junit.Test

class SafeCropMarginsTest {
    private val detected = Margins(left = 44, top = 26, right = 20, bottom = 12)

    @Test
    fun automatic_never_removes_left_or_right_pixels() {
        assertEquals(Margins(0, 26, 0, 12), SafeCropMargins.automatic(detected))
        assertEquals(
            Margins(0, 26, 0, 12),
            SafeCropMargins.effective(true, detected, manual = null),
        )
    }

    @Test
    fun disabled_crop_preserves_all_four_sides_even_with_manual_values() {
        val manual = Margins(10, 15, 11, 16)
        assertEquals(Margins(0, 0, 0, 0), SafeCropMargins.effective(false, detected, manual))
    }

    @Test
    fun horizontal_crop_only_works_when_user_explicitly_sets_manual_margins() {
        val manual = Margins(10, 15, 11, 16)
        assertEquals(manual, SafeCropMargins.effective(true, detected, manual))
    }
}
