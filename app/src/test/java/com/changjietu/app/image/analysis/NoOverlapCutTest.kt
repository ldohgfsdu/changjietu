package com.changjietu.app.image.analysis

import com.changjietu.app.image.processing.SliceGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoOverlapCutTest {
    @Test fun exactManualCutsHaveNoDuplicateTextRows() {
        val ends = listOf(2400, 4700, 6857)
        val ranges = SplitPointDetector.rangesFromCutEnds(6857, ends, overlap = 0)
        SliceGeometry.requireNoOverlapCoverage(ranges, 6857)
        assertEquals(listOf(0 until 2400, 2400 until 4700, 4700 until 6857), ranges)
    }

    @Test fun autoTextGapsArePreferredWithoutDuplication() {
        val height = 6857
        val energy = FloatArray(height) { y ->
            if (y % 64 in 46..63) 0.05f else 10f
        }
        val plan = SplitPointDetector.plan(
            contentHeight = height,
            targetSliceHeight = 2500,
            overlap = 0,
            rowEnergy = energy,
        )
        SliceGeometry.requireNoOverlapCoverage(plan.ranges, height)
        assertTrue(plan.ranges.size in 3..4)
        for (range in plan.ranges.dropLast(1)) {
            val cut = range.last + 1
            assertTrue("cut $cut must land inside blank row", energy[cut] < 1f && energy[cut - 1] < 1f)
        }
    }

    @Test fun noEnergyFallbackStillCoversEveryPixelExactlyOnce() {
        val plan = SplitPointDetector.plan(
            contentHeight = 9123,
            targetSliceHeight = 2200,
            overlap = 0,
            rowEnergy = null,
        )
        SliceGeometry.requireNoOverlapCoverage(plan.ranges, 9123)
        assertEquals(9123, plan.ranges.sumOf { it.last - it.first + 1 })
    }

    @Test fun explicitOverlapIsStillAvailableWhenRequested() {
        val ranges = SplitPointDetector.rangesFromCutEnds(
            1000, listOf(400, 800, 1000), overlap = 120,
        )
        assertEquals(120, ranges[0].last - ranges[1].first + 1)
        assertEquals(120, ranges[1].last - ranges[2].first + 1)
    }
}
