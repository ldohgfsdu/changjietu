package com.changjietu.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CutPreviewGeometryTest {

    @Test
    fun tall_source_letterboxes_horizontally_and_maps_y_linearly() {
        val frame = CutPreviewGeometry.fit(
            boxWidth = 200f,
            boxHeight = 200f,
            sourceWidth = 100,
            sourceHeight = 1000,
        )
        assertEquals(0f, frame.offsetY, 0.01f)
        assertEquals(200f, frame.drawHeight, 0.01f)
        assertEquals(20f, frame.drawWidth, 0.01f)
        assertEquals(90f, frame.offsetX, 0.01f)

        assertEquals(0f, CutPreviewGeometry.yOf(frame, 0, 1000), 0.01f)
        assertEquals(20f, CutPreviewGeometry.yOf(frame, 100, 1000), 0.01f)
        assertEquals(200f, CutPreviewGeometry.yOf(frame, 1000, 1000), 0.01f)
    }

    @Test
    fun wide_source_letterboxes_vertically_and_maps_x_linearly() {
        val frame = CutPreviewGeometry.fit(
            boxWidth = 200f,
            boxHeight = 200f,
            sourceWidth = 1000,
            sourceHeight = 100,
        )
        assertEquals(0f, frame.offsetX, 0.01f)
        assertEquals(200f, frame.drawWidth, 0.01f)
        assertEquals(20f, frame.drawHeight, 0.01f)
        assertEquals(90f, frame.offsetY, 0.01f)
        assertEquals(100f, CutPreviewGeometry.xOf(frame, 500, 1000), 0.01f)
    }

    @Test
    fun matching_aspect_fills_the_box() {
        val frame = CutPreviewGeometry.fit(300f, 600f, 100, 200)
        assertEquals(0f, frame.offsetX, 0.01f)
        assertEquals(0f, frame.offsetY, 0.01f)
        assertEquals(300f, frame.drawWidth, 0.01f)
        assertEquals(600f, frame.drawHeight, 0.01f)
    }
}
