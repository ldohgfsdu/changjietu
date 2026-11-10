package com.changjietu.app.image.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitPointDetectorUnsafeSeamTest {
    @Test
    fun find_real_early_gap_instead_of_forcing_cut_near_target_through_table() {
        val height = 5800
        val energy = FloatArray(height) { 8f } // dense table/paragraph rows
        for (y in 1350..1410) energy[y] = 0.08f // real blank paragraph separator
        val plan = SplitPointDetector.plan(
            contentHeight = height,
            targetSliceHeight = 2200,
            overlap = 0,
            rowEnergy = energy,
        )
        val firstCut = plan.ranges.first().last + 1
        assertTrue("Expected safe gap 1350..1410 instead of a forced table cut: $firstCut",
            firstCut in 1354..1406)
        assertEquals(emptyList<Int>(), SplitPointDetector.uncertainCuts(listOf(firstCut), energy))
        assertEquals(0, plan.ranges.first().first)
        assertEquals(height - 1, plan.ranges.last().last)
        plan.ranges.forEach { assertTrue(it.last - it.first + 1 <= 2200) }
        for (i in 1 until plan.ranges.size) {
            assertEquals(plan.ranges[i - 1].last + 1, plan.ranges[i].first)
        }
    }

    @Test
    fun table_with_no_safe_blank_gaps_is_flagged_instead_of_claimed_safe() {
        val height = 4800
        val energy = FloatArray(height) { 7f } // no row has reliably low content energy
        val plan = SplitPointDetector.plan(
            contentHeight = height,
            targetSliceHeight = 2200,
            overlap = 0,
            rowEnergy = energy,
        )
        val seams = plan.ranges.dropLast(1).map { it.last + 1 }
        assertTrue(seams.isNotEmpty())
        assertEquals(seams, SplitPointDetector.uncertainCuts(seams, energy))
    }

    @Test
    fun tiny_false_gap_near_target_does_not_hide_better_earlier_gap() {
        val height = 4200
        val energy = FloatArray(height) { 8f }
        for (y in 1280..1370) energy[y] = 0.08f
        for (y in 2050..2053) energy[y] = 0.08f
        val plan = SplitPointDetector.plan(
            contentHeight = height,
            targetSliceHeight = 2200,
            overlap = 0,
            rowEnergy = energy,
        )
        val firstCut = plan.ranges.first().last + 1
        assertTrue("A wide safe earlier gap should win: $firstCut", firstCut in 1285..1365)
    }
}
