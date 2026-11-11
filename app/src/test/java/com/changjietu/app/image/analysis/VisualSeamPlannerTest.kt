package com.changjietu.app.image.analysis

import com.changjietu.app.image.processing.SliceGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Deterministic regression reproductions of the reported chart/table/photo cuts.
 * The real user uploads show an editor overlay, NOT the unmodified source image;
 * these annotated Y intervals are controlled planner fixtures, not model accuracy.
 */
class VisualSeamPlannerTest {
    private fun energy(height: Int, vararg gaps: IntRange): FloatArray =
        FloatArray(height) { y -> if (gaps.any { y in it }) 0.02f else 8f }

    private fun assertSafeCuts(plan: SplitPlan, blocks: List<IntRange>) {
        val cutEnds = plan.ranges.dropLast(1).map { it.last + 1 }
        assertTrue("At least one seam expected", cutEnds.isNotEmpty())
        for (cut in cutEnds) {
            assertTrue("Cut $cut crossed visual block $blocks", blocks.none { cut in it })
        }
        SliceGeometry.requireNoOverlapCoverage(plan.ranges, plan.contentHeight)
    }

    @Test
    fun chart_in_original_report_is_not_cut_through_mid_plot() {
        val blocks = listOf(1700..3000)
        val plan = VisualSeamPlanner.plan(
            contentHeight = 6600, targetSliceHeight = 2200, overlap = 0,
            rowEnergy = energy(6600, 1470..1550, 3010..3080, 5050..5100),
            visualRows = blocks, protectedRows = blocks,
        )
        assertSafeCuts(plan, blocks)
        assertTrue(plan.ranges.all { it.last - it.first + 1 <= 3960 })
    }

    @Test
    fun tall_table_can_be_one_extended_slice_instead_of_a_broken_table() {
        val blocks = listOf(820..3490)
        val plan = VisualSeamPlanner.plan(
            contentHeight = 6600, targetSliceHeight = 2200, overlap = 0,
            rowEnergy = energy(6600, 700..760, 3530..3590, 5560..5610),
            visualRows = blocks, protectedRows = blocks,
        )
        assertSafeCuts(plan, blocks)
        assertTrue(
            "A picture-preserving slice should be allowed beyond a screen height",
            plan.ranges.any { it.last - it.first + 1 > 2200 },
        )
        assertTrue(plan.ranges.all { it.last - it.first + 1 <= 3960 })
    }

    @Test
    fun image_just_before_target_can_force_an_early_safe_seam() {
        val blocks = listOf(1920..3260)
        val plan = VisualSeamPlanner.plan(
            contentHeight = 7400, targetSliceHeight = 2200, overlap = 0,
            rowEnergy = energy(7400, 1660..1700, 3300..3360, 5320..5370),
            visualRows = blocks, protectedRows = blocks,
        )
        assertSafeCuts(plan, blocks)
        assertTrue("First safe seam must be before the image or after it",
            plan.ranges[0].last + 1 < 1920 || plan.ranges[0].last + 1 > 3260)
    }

    @Test
    fun impossible_visual_region_is_marked_as_unsafe_instead_of_silent_success() {
        val blocks = listOf(100..6200) // longer than bounded bitmap slice
        val plan = VisualSeamPlanner.plan(
            contentHeight = 9000, targetSliceHeight = 2200, overlap = 0,
            rowEnergy = energy(9000, 4200..4260, 8250..8290),
            visualRows = blocks, protectedRows = blocks,
        )
        SliceGeometry.requireNoOverlapCoverage(plan.ranges, 9000)
        val cuts = plan.ranges.dropLast(1).map { it.last + 1 }
        assertTrue("Unavoidable cut not detected", cuts.any { it in blocks.first() })
        assertTrue(
            "UI needs the exposed unsafe seam",
            SplitPointDetector.uncertainCuts(cuts, energy(9000, 4200..4260, 8250..8290),
                protectedRows = blocks).any { it in blocks.first() },
        )
        assertTrue("No unbounded bitmap output",
            plan.ranges.all { it.last - it.first + 1 <= 3960 })
    }

    @Test
    fun configured_overlap_is_preserved_on_all_content_aware_slices() {
        val blocks = listOf(1700..3000, 3900..5400)
        val ov = 120
        val plan = VisualSeamPlanner.plan(
            contentHeight = 8300, targetSliceHeight = 2200, overlap = ov,
            rowEnergy = energy(8300, 1510..1580, 3020..3070, 5460..5520, 7380..7450),
            visualRows = blocks, protectedRows = blocks,
        )
        assertEquals(0, plan.ranges.first().first)
        assertEquals(8299, plan.ranges.last().last)
        for (i in 1 until plan.ranges.size) {
            assertEquals("Configured seam overlap must be exact",
                ov, plan.ranges[i - 1].last + 1 - plan.ranges[i].first)
            assertTrue(plan.ranges[i].first > plan.ranges[i-1].first)
        }
        assertTrue(plan.ranges.all { it.last - it.first + 1 <= 3960 })
    }

    @Test
    fun old_text_only_strategy_and_height_limit_are_unchanged_without_visual_evidence() {
        val map = energy(7200, 2060..2100, 4350..4420, 6500..6560)
        val expected = SplitPointDetector.plan(
            contentHeight = 7200, targetSliceHeight = 2200, overlap = 0,
            rowEnergy = map, protectedRows = listOf(1200..1240),
        )
        val actual = VisualSeamPlanner.plan(
            contentHeight = 7200, targetSliceHeight = 2200, overlap = 0,
            rowEnergy = map, visualRows = emptyList(),
            protectedRows = listOf(1200..1240),
        )
        assertEquals(expected.ranges, actual.ranges)
    }

    @Test
    fun full_height_50000_stays_finite_and_covers_every_source_row() {
        val h = 50000
        val blocks = (0 until 20).map { i -> (i * 2400 + 950)..(i * 2400 + 2400) }
        val map = FloatArray(h) { y -> if (y % 2400 in 880..940) 0.01f else 7f }
        val plan = VisualSeamPlanner.plan(
            contentHeight = h, targetSliceHeight = 2200, overlap = 0,
            rowEnergy = map, visualRows = blocks, protectedRows = blocks,
        )
        SliceGeometry.requireNoOverlapCoverage(plan.ranges, h)
        assertTrue(plan.ranges.size in 13..70)
        assertTrue(plan.ranges.all { it.last - it.first + 1 <= 3960 })
    }
}
