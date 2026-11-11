package com.changjietu.app.image.analysis

import com.changjietu.app.image.processing.SliceGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unlike older fabricated row-energy tests, these generate actual ARGB pixel
 * patterns and call the production visual detector before planning cuts.
 */
class VisualBlockHeuristicsTest {
    private class SyntheticImage(val width: Int = 240, val height: Int = 640, dark: Boolean = false) {
        val bg: Int = if (dark) 0xff121212.toInt() else 0xfffefefe.toInt()
        val ink: Int = if (dark) 0xffececec.toInt() else 0xff171717.toInt()
        val pixels = IntArray(width * height) { bg }
        fun rect(x: Int, y: Int, w: Int, h: Int, color: Int = ink) {
            for (yy in y until y + h) for (xx in x until x + w) {
                pixels[yy * width + xx] = color
            }
        }
        fun avatar(cx: Int, cy: Int, radius: Int) {
            for (y in cy-radius..cy+radius) for (x in cx-radius..cx+radius) {
                if ((x-cx)*(x-cx) + (y-cy)*(y-cy) <= radius*radius) {
                    pixels[y * width + x] = 0xff168ad0.toInt()
                }
            }
        }
        fun table(top: Int, bottom: Int) {
            for (y in top..bottom step 30) rect(15, y, 210, 2)
            for (x in 15..225 step 70) rect(x, top, 2, bottom-top)
        }
        fun detect(): List<VisualBlockHeuristics.Box> =
            VisualBlockHeuristics.detect(pixels, width, height)
    }

    @Test
    fun actual_pixels_light_and_dark_detect_avatar_photo_and_table_bounds() {
        for (dark in listOf(false, true)) {
            val image = SyntheticImage(dark = dark)
            image.avatar(35, 210, 20)
            image.rect(65, 245, 140, 90, 0xffb1d9e9.toInt())
            image.table(390, 550)
            val regions = image.detect()
            assertTrue("Avatar missing dark=$dark: $regions",
                regions.any { it.top <= 190 && it.bottom >= 230 })
            assertTrue("Photo missing dark=$dark: $regions",
                regions.any { it.top <= 245 && it.bottom >= 334 })
            assertTrue("Ruled table missing dark=$dark: $regions",
                regions.any { it.top <= 390 && it.bottom >= 549 })
        }
    }

    @Test
    fun persistent_timeline_line_does_not_join_separate_post_cards() {
        val image = SyntheticImage()
        image.rect(20, 30, 2, 570) // timeline spine
        image.rect(22, 100, 155, 115, 0xffbbbbbb.toInt())
        image.rect(22, 340, 155, 115, 0xffbbbbbb.toInt())
        val regions = image.detect()
        assertTrue("First card missing: $regions",
            regions.any { it.top <= 100 && it.bottom >= 214 && it.bottom < 280 })
        assertTrue("Second card missing: $regions",
            regions.any { it.top <= 340 && it.bottom >= 454 && it.top > 290 })
        assertFalse("Timeline unexpectedly joined unrelated cards",
            regions.any { it.top <= 100 && it.bottom >= 454 })
    }

    @Test
    fun uniform_background_and_thin_rule_do_not_create_false_blocks() {
        val image = SyntheticImage()
        assertEquals(emptyList<VisualBlockHeuristics.Box>(), image.detect())
        image.rect(12, 320, 216, 1)
        assertEquals(emptyList<VisualBlockHeuristics.Box>(), image.detect())
    }

    @Test
    fun detected_table_region_is_fed_into_real_seam_planner_not_just_mocked_annotation() {
        val image = SyntheticImage(width = 240, height = 640)
        image.table(320, 560)
        val regions = image.detect()
        val protected = regions.map { it.top..it.bottom }
        assertTrue(protected.any { 420 in it })
        val energy = FloatArray(640) { 8f }
        for (y in 410..440) energy[y] = 0f // deceptive gap INSIDE table
        for (y in 260..285) energy[y] = 0f // real gap ABOVE table
        val plan = SplitPointDetector.plan(
            contentHeight = 640, targetSliceHeight = 400, overlap = 0,
            rowEnergy = energy, protectedRows = protected,
        )
        val first = plan.ranges.first().last + 1
        assertTrue("Seam should stay above table, not in its rows: $first", first in 260..285)
        assertFalse(first in 320..560)
        SliceGeometry.requireNoOverlapCoverage(plan.ranges, 640)
    }
}
