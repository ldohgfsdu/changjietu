package com.changjietu.app.image.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarginDetectorTest {

    @Test
    fun detects_uniform_side_margins() {
        val w = 100
        val h = 200
        val col = FloatArray(w) { 10f }
        val row = FloatArray(h) { 10f }
        for (i in 0 until 10) col[i] = 0.5f
        for (i in w - 8 until w) col[i] = 0.5f
        for (i in 0 until 5) row[i] = 0.5f
        for (i in h - 6 until h) row[i] = 0.5f

        val m = MarginDetector.detect(w, h, col, row, threshold = 2.5f)
        assertEquals(10, m.left)
        assertEquals(8, m.right)
        assertEquals(5, m.top)
        assertEquals(6, m.bottom)
    }

    @Test
    fun refuses_to_crop_half_image() {
        val w = 100
        val h = 100
        val col = FloatArray(w) { 0.1f }
        val row = FloatArray(h) { 0.1f }
        val m = MarginDetector.detect(w, h, col, row)
        assertTrue(m.isEmpty())
    }
}
