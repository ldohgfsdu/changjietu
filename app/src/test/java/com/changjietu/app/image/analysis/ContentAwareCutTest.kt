package com.changjietu.app.image.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentAwareCutTest {
    @Test
    fun recognized_text_rows_are_never_selected_when_safe_gap_exists() {
        val height = 5400
        val energy = FloatArray(height) { 7f }
        for (i in 1500..1570) energy[i] = 0.05f
        for (i in 2070..2150) energy[i] = 0.05f
        val protected = listOf(2060..2160)
        val plan = SplitPointDetector.plan(
            contentHeight = height, targetSliceHeight = 2200,
            overlap = 0, rowEnergy = energy, protectedRows = protected,
        )
        val first = plan.ranges.first().last + 1
        assertTrue("Unexpected OCR-obstructed cut $first", first in 1505..1565)
        assertEquals(0, plan.ranges.first().first)
        assertEquals(height - 1, plan.ranges.last().last)
        for (i in 1 until plan.ranges.size) {
            assertEquals(plan.ranges[i - 1].last + 1, plan.ranges[i].first)
        }
    }

    @Test
    fun blocked_dense_region_without_safe_seam_is_explicitly_flagged() {
        val energy = FloatArray(4700) { 9f }
        val rows = listOf(1200..3300)
        val plan = SplitPointDetector.plan(
            contentHeight = 4700, targetSliceHeight = 2200, overlap = 0,
            rowEnergy = energy, protectedRows = rows,
        )
        val cuts = plan.ranges.dropLast(1).map { it.last + 1 }
        assertEquals(
            cuts.filter { cut -> cut in 1200..3300 || energy[cut] > 1f },
            SplitPointDetector.uncertainCuts(cuts, energy, protectedRows = rows),
        )
    }

    @Test
    fun detected_text_protection_is_not_inflated_by_invalid_out_of_bounds_data() {
        val energy = FloatArray(5200) { 5f }
        for (i in 1950..2040) energy[i] = 0f
        val plan = SplitPointDetector.plan(
            contentHeight = 5200, targetSliceHeight = 2200,
            overlap = 0, rowEnergy = energy,
            protectedRows = listOf(-300..-1, 6000..6500),
        )
        val cut = plan.ranges.first().last + 1
        assertTrue(cut in 1955..2035)
    }
}
