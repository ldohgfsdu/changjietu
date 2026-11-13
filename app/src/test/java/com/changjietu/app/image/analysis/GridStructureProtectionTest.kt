package com.changjietu.app.image.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A long bordered table can contain many visually empty rows while its vertical
 * grid lines continue uninterrupted. Row-only energy treats those rows as safe.
 * Source: 2026-11-13 device screenshots, synthetic fixture (not original image).
 */
class GridStructureProtectionTest {
    private fun syntheticTable(
        width: Int = 320,
        height: Int = 4200,
        tableTop: Int = 1450,
        tableBottom: Int = 2550,
    ): Array<IntArray> {
        val rows = Array(height) { IntArray(width) { 0xFFFFFFFF.toInt() } }
        val lines = listOf(24, 67, 112, 157, 202, 247, 292)
        for (y in tableTop until tableBottom) {
            for (x in lines) {
                rows[y][x] = 0xFF7F7F7F.toInt()
                rows[y][x + 1] = 0xFF7F7F7F.toInt()
            }
        }
        return rows
    }

    @Test fun long_vertical_grid_lines_protect_table_interior_not_blank_space() {
        val rows = syntheticTable()
        val detector = GridStructureProtection(height = rows.size, sampledWidth = rows[0].size)
        rows.forEachIndexed { y, row ->
            detector.acceptRow(row, 0, y, y + 1)
        }
        val protected = detector.protectedRows()
        assertFalse(protected[1380])
        assertTrue("A grid cell with no text still belongs to the table", protected[2200])
        assertFalse(protected[2650])
    }

    @Test fun use_structural_protection_in_planner_and_unsafe_cut_warning() {
        val height = 4200
        val rows = syntheticTable(height = height)
        val detector = GridStructureProtection(height, rows[0].size)
        rows.forEachIndexed { y, row -> detector.acceptRow(row, 0, y, y + 1) }
        val protected = detector.protectedRows()

        val energy = FloatArray(height) { 8f }
        // Genuine separator before the table.
        for (y in 1350 until 1410) energy[y] = 0.1f
        // False separator spanning a series of white grid cells near screen height.
        for (y in 1900 until 2300) energy[y] = 0.1f
        val plan = SplitPointDetector.plan(
            contentHeight = height,
            targetSliceHeight = 2200,
            overlap = 0,
            rowEnergy = energy,
            protectedRows = protected,
        )
        val firstCut = plan.ranges.first().last + 1
        assertTrue("Prefer separator before chart, not inside table: $firstCut", firstCut in 1354..1406)
        assertEquals(emptyList<Int>(), SplitPointDetector.uncertainCuts(listOf(firstCut), energy, protectedRows = protected))
        assertEquals(listOf(2100), SplitPointDetector.uncertainCuts(listOf(2100), energy, protectedRows = protected))
        assertEquals(0, plan.ranges.first().first)
        assertEquals(height - 1, plan.ranges.last().last)
        plan.ranges.forEach { assertTrue(it.last - it.first + 1 <= 2200) }
        for (i in 1 until plan.ranges.size) assertEquals(plan.ranges[i - 1].last + 1, plan.ranges[i].first)
    }

    @Test fun unrelated_vertical_borders_do_not_make_entire_page_unsafe() {
        val detector = GridStructureProtection(height = 400, sampledWidth = 320)
        for (y in 0 until 400) {
            val row = IntArray(320) { 0xFFFFFFFF.toInt() }
            for (x in listOf(25, 150, 290)) row[x] = 0xFF222222.toInt()
            detector.acceptRow(row, 0, y, y + 1)
        }
        assertTrue("Three vertical guides are not a multi-column table", detector.protectedRows().none { it })
    }

    @Test fun no_gap_inside_oversize_table_is_reported_as_unresolved() {
        val height = 5000
        val energy = FloatArray(height) { 0.1f }
        val protected = BooleanArray(height) { it in 300 until 4700 }
        val plan = SplitPointDetector.plan(
            contentHeight = height,
            targetSliceHeight = 2200,
            overlap = 0,
            rowEnergy = energy,
            protectedRows = protected,
        )
        val cuts = plan.ranges.dropLast(1).map { it.last + 1 }
        assertTrue(cuts.isNotEmpty())
        assertTrue("Cannot claim a chart spanning more than one slice is automatically safe",
            SplitPointDetector.uncertainCuts(cuts, energy, protectedRows = protected).isNotEmpty())
        assertEquals(height - 1, plan.ranges.last().last)
    }

    @Test fun ordinary_text_without_long_parallel_edges_is_not_misclassified() {
        val detector = GridStructureProtection(height = 400, sampledWidth = 320)
        for (y in 0 until 400) {
            val row = IntArray(320) { 0xFFFFFFFF.toInt() }
            if (y % 45 < 14) for (x in 20 until 160 step 3) row[x] = 0xFF111111.toInt()
            detector.acceptRow(row, 0, y, y + 1)
        }
        assertTrue(detector.protectedRows().none { it })
    }
}
