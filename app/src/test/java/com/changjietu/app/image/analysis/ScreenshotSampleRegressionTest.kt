package com.changjietu.app.image.analysis

import com.changjietu.app.core.SafeCropMargins
import com.changjietu.app.image.processing.SliceGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * Deterministic annotated SAMPLE corpus, not screenshots or OCR accuracy tests.
 *
 * Every fixture models screenshot-like row density and an independently labelled
 * unsafe content region. The oracle is the annotation, never the algorithm's own
 * confidence score. A generated test cannot substitute for a real device image.
 */
class ScreenshotSampleRegressionTest {
    private data class Case(
        val name: String,
        val safeGap: IntRange,
        val deceptiveGap: IntRange,
        val protectedBlock: IntRange,
    )

    private val cases = listOf(
        Case("paragraph_lines", 1510..1580, 2040..2120, 1990..2180),
        Case("chat_avatar_and_message", 1630..1700, 2060..2110, 1940..2400),
        Case("dense_table", 1450..1540, 2050..2140, 1840..2720),
        Case("chart_with_legend", 1760..1830, 2030..2090, 1910..2670),
        Case("large_inline_picture", 1570..1670, 2070..2170, 1890..3100),
        Case("post_card_and_comments", 1350..1440, 2030..2160, 1850..2510),
        Case("dark_background_text", 1690..1770, 2030..2140, 1910..2410),
    )

    private fun annotatedEnergy(case: Case, seed: Int, height: Int = 5800): FloatArray {
        val random = Random(seed.toLong())
        val values = FloatArray(height) { 7.5f + random.nextFloat() * 1.5f }
        for (y in case.safeGap) values[y] = 0.05f
        // False 'low energy' in a block must NOT become a valid seam simply
        // because most of the row looks empty (e.g. inside a table or chart).
        for (y in case.deceptiveGap) values[y] = 0.05f
        return values
    }

    @Test
    fun seven_visual_categories_repeated_with_fixed_noise_seeds_choose_annotated_safe_gap() {
        val seeds = intArrayOf(0, 1, 7, 19, 2026)
        for (case in cases) for (seed in seeds) {
            val energy = annotatedEnergy(case, seed)
            val plan = SplitPointDetector.plan(
                contentHeight = energy.size,
                targetSliceHeight = 2200,
                overlap = 0,
                rowEnergy = energy,
                protectedRows = listOf(case.protectedBlock),
            )
            val seam = plan.ranges.first().last + 1
            assertTrue(
                "${case.name} seed=$seed: selected seam $seam outside verified ${case.safeGap}",
                seam in case.safeGap,
            )
            assertFalse(
                "${case.name} seed=$seed: selected seam $seam in annotated block",
                seam in case.protectedBlock,
            )
            SliceGeometry.requireNoOverlapCoverage(plan.ranges, energy.size)
            assertTrue(plan.ranges.all { it.last - it.first + 1 <= 2200 })
        }
    }

    @Test
    fun uninterrupted_table_is_classified_risky_and_does_not_claim_a_safe_seam() {
        val energy = FloatArray(5100) { 8.0f }
        val wholeTable = 1100..3900
        val plan = SplitPointDetector.plan(
            contentHeight = energy.size,
            targetSliceHeight = 2200,
            overlap = 0,
            rowEnergy = energy,
            protectedRows = listOf(wholeTable),
        )
        val seams = plan.ranges.dropLast(1).map { it.last + 1 }
        assertTrue(seams.isNotEmpty())
        assertEquals(
            "No real blank band exists; ALL forced seams must be reported risky",
            seams,
            SplitPointDetector.uncertainCuts(seams, energy, protectedRows = listOf(wholeTable)),
        )
        SliceGeometry.requireNoOverlapCoverage(plan.ranges, energy.size)
    }

    @Test
    fun extra_tall_image_keeps_all_source_rows_in_order_with_zero_overlap() {
        val height = 50000
        val energy = FloatArray(height) { if (it % 1800 in 1750..1780) 0.04f else 8f }
        val plan = SplitPointDetector.plan(
            contentHeight = height,
            targetSliceHeight = 2400,
            overlap = 0,
            rowEnergy = energy,
        )
        SliceGeometry.requireNoOverlapCoverage(plan.ranges, height)
        assertTrue(plan.ranges.size in 21..40)
        for (i in plan.ranges.indices) {
            assertTrue("slice $i exceeded 2400px", plan.ranges[i].last - plan.ranges[i].first + 1 <= 2400)
        }
    }

    @Test
    fun exact_pixel_rectangles_keep_avatar_and_chart_at_left_and_right_edges() {
        val width = 360
        val height = 5280
        val detection = Margins(42, 20, 33, 15)
        val applied = SafeCropMargins.effective(true, detection, manual = null)
        assertEquals(0, applied.left)
        assertEquals(0, applied.right)
        val contentHeight = height - applied.top - applied.bottom
        val energy = FloatArray(contentHeight) { if (it % 1600 in 1480..1515) 0.05f else 8f }
        val plan = SplitPointDetector.plan(
            contentHeight = contentHeight, targetSliceHeight = 2200,
            rowEnergy = energy, overlap = 0,
        )
        SliceGeometry.requireNoOverlapCoverage(plan.ranges, contentHeight)
        val rectangles = plan.ranges.map { SliceGeometry.regionFor(width, height, applied, it) }
        assertEquals(applied.top, rectangles.first().top)
        assertEquals(height - applied.bottom, rectangles.last().bottom)
        rectangles.forEachIndexed { index, rect ->
            assertEquals("left avatar lost in slice $index", 0, rect.left)
            assertEquals("right chart/legend lost in slice $index", width, rect.right)
            assertEquals(width, rect.width)
        }
        for (i in 1 until rectangles.size) assertEquals(rectangles[i - 1].bottom, rectangles[i].top)
    }

    @Test
    fun overlap_mode_repeats_only_explicit_overlap_and_never_misses_a_row() {
        val height = 9400
        val ov = 120
        val plan = SplitPointDetector.plan(
            contentHeight = height, targetSliceHeight = 2200,
            overlap = ov, rowEnergy = FloatArray(height) { 7f },
        )
        assertEquals(0, plan.ranges.first().first)
        assertEquals(height, plan.ranges.last().last + 1)
        plan.ranges.forEach { assertTrue(it.last - it.first + 1 <= 2200) }
        for (i in 1 until plan.ranges.size) {
            val actualOverlap = plan.ranges[i - 1].last - plan.ranges[i].first + 1
            assertEquals("overlap at seam $i is not exactly configured", ov, actualOverlap)
        }
    }
}
