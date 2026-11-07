package com.changjietu.app.image.processing

import com.changjietu.app.image.analysis.Margins
import com.changjietu.app.image.analysis.SplitPointDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SliceGeometryTest {
    @Test fun exactPixelsAtSeamWithMargins() {
        val margins = Margins(left = 33, top = 22, right = 167, bottom = 4)
        val cuts = listOf(1800, 4300, 6800)
        val ranges = SplitPointDetector.rangesFromCutEnds(6800, cuts, overlap = 0)
        SliceGeometry.requireNoOverlapCoverage(ranges, 6800)
        val rectangles = ranges.map { SliceGeometry.regionFor(1220, 6826, margins, it) }
        assertEquals(33, rectangles.first().left)
        assertEquals(1053, rectangles.first().right)
        assertEquals(22, rectangles.first().top)
        assertEquals(1822, rectangles.first().bottom)
        assertEquals(1822, rectangles[1].top)
        assertEquals(4322, rectangles[1].bottom)
        assertEquals(4322, rectangles[2].top)
        assertEquals(6822, rectangles.last().bottom)
        assertEquals(6800, rectangles.sumOf { it.height })
    }

    @Test fun onePixelBoundaryHasNoHiddenPadding() {
        val rect = SliceGeometry.regionFor(
            1220, 6800, Margins(0, 0, 0, 0), 2400 until 4000,
        )
        assertEquals(2400, rect.top)
        assertEquals(4000, rect.bottom)
        assertEquals(1600, rect.height)
    }

    @Test fun gapOrOverlapIsRejectedWhenExactModeExpected() {
        assertThrows(IllegalArgumentException::class.java) {
            SliceGeometry.requireNoOverlapCoverage(
                listOf(0 until 1200, 1190 until 2500), 2500,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SliceGeometry.requireNoOverlapCoverage(
                listOf(0 until 1200, 1210 until 2500), 2500,
            )
        }
    }

    @Test fun outOfBoundsSourceRangeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SliceGeometry.regionFor(100, 1000, Margins(0, 10, 0, 10), 0 until 990)
        }
    }
}
