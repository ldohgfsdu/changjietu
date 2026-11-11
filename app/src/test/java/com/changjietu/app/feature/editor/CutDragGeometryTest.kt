package com.changjietu.app.feature.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class CutDragGeometryTest {
    @Test fun noMovementOrSubpixelTapNeverCommits() {
        assertFalse(CutDragGeometry.hasChanged(1200, 0f, 800f, 4000))
        assertFalse(CutDragGeometry.hasChanged(1200, 0.05f, 800f, 4000))
    }

    @Test fun dragBackToOriginDoesNotCommit() {
        val moves = listOf(8f, 32f, 6f, -8f, 0f)
        assertTrue(moves.dropLast(1).any { CutDragGeometry.hasChanged(1200, it, 800f, 4000) })
        assertFalse(CutDragGeometry.hasChanged(1200, moves.last(), 800f, 4000))
    }

    @Test fun meaningfulDragCommitsExactlyPixelMappedPosition() {
        assertEquals(1400, CutDragGeometry.finalPosition(1200, 40f, 800f, 4000))
        assertTrue(CutDragGeometry.hasChanged(1200, 40f, 800f, 4000))
        assertEquals(1000, CutDragGeometry.finalPosition(1200, -40f, 800f, 4000))
    }

    @Test fun invalidViewportIsNoOp() {
        assertFalse(CutDragGeometry.hasChanged(1200, 10f, 0f, 4000))
        assertFalse(CutDragGeometry.hasChanged(1200, Float.NaN, 900f, 4000))
    }
}
