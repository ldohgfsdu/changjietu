package com.changjietu.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceCutGeometryTest {

    @Test
    fun toggle_crop_top_only_changes_mapping() {
        // source 5000，top 38 → content 4962
        assertEquals(5000, SourceCutGeometry.sourceFromContent(4962, 38))
        assertEquals(
            listOf(4962),
            SourceCutGeometry.contentCutsFromSource(listOf(5000), 38, 10000, 50),
        )
        assertEquals(
            listOf(5000),
            SourceCutGeometry.contentCutsFromSource(listOf(5000), 0, 10000, 50),
        )
    }

    @Test
    fun left_right_only_change_does_not_move_cuts() {
        val cuts = listOf(2480, 5010, 7600, 10050, 11980)
        assertEquals(cuts, SourceCutGeometry.contentCutsFromSource(cuts, 0, 13507, 50))
    }

    @Test
    fun cuts_inside_cropped_top_are_filtered_and_flagged() {
        assertTrue(SourceCutGeometry.conflicts(listOf(20, 5000), 38, 10000, 50))
        assertFalse(SourceCutGeometry.conflicts(listOf(5000), 38, 10000, 50))
    }

    @Test
    fun bottom_crop_beyond_cut_is_conflict() {
        assertTrue(SourceCutGeometry.conflicts(listOf(11980), 38, 10000, 50))
    }

    @Test
    fun visible_index_skips_hidden_cut() {
        val manual = listOf(20, 5000)
        assertEquals(listOf(5000), SourceCutGeometry.visibleSource(manual, 38, 10000, 50))
        assertEquals(1, SourceCutGeometry.fullIndexOfVisible(manual, 0, 38, 10000, 50))
        assertEquals(-1, SourceCutGeometry.fullIndexOfVisible(manual, 1, 38, 10000, 50))
    }

    @Test
    fun sanitize_rejects_zero_width_height() {
        val m = com.changjietu.app.image.analysis.Margins(610, 0, 610, 0)
        val c = SourceCutGeometry.sanitizeMargins(m, 1220, 1000)
        assertTrue(c.left + c.right <= 1220 - 64)
        assertTrue(c.left >= 0 && c.right >= 0)
        val tall = com.changjietu.app.image.analysis.Margins(0, 500, 0, 500)
        val c2 = SourceCutGeometry.sanitizeMargins(tall, 1220, 1000)
        assertTrue(c2.top + c2.bottom <= 1000 - 100)
    }
}
