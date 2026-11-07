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
        // 保守内边距：宽100留2、高200留1，检测10→8、8→6、5→4、6→5
        assertEquals(8, m.left)
        assertEquals(6, m.right)
        assertEquals(4, m.top)
        assertEquals(5, m.bottom)
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

    @Test
    fun tolerates_isolated_noise_spike_in_margin() {
        val w = 100
        val h = 200
        val col = FloatArray(w) { 10f }
        val row = FloatArray(h) { 10f }
        for (i in 0 until 10) col[i] = 0.5f
        for (i in w - 8 until w) col[i] = 0.5f
        for (i in 0 until 5) row[i] = 0.5f
        for (i in h - 6 until h) row[i] = 0.5f
        // 空白边里的孤立噪点不应把空白段截断（旧实现会在第一个高能量样本处停止）。
        col[3] = 10f

        val m = MarginDetector.detect(w, h, col, row, threshold = 2.5f)
        assertEquals(8, m.left)
        assertEquals(6, m.right)
        assertEquals(4, m.top)
        assertEquals(5, m.bottom)
    }

    @Test
    fun adaptive_threshold_detects_margins_on_low_contrast_image() {
        val w = 100
        val h = 200
        // 低对比：正文能量仅 1.5，低于旧的固定阈值 2.5，旧实现会把正文当空白过裁。
        val col = FloatArray(w) { 1.5f }
        val row = FloatArray(h) { 1.5f }
        for (i in 0 until 12) col[i] = 0.1f
        for (i in w - 12 until w) col[i] = 0.1f
        for (i in 0 until 7) row[i] = 0.1f
        for (i in h - 7 until h) row[i] = 0.1f

        val m = MarginDetector.detect(w, h, col, row)
        assertEquals(10, m.left)
        assertEquals(10, m.right)
        assertEquals(6, m.top)
        assertEquals(6, m.bottom)
    }

    @Test
    fun clamps_only_overcropped_axis_instead_of_dropping_all() {
        val w = 100
        val h = 200
        val col = FloatArray(w) { 10f }
        val row = FloatArray(h) { 10f }
        for (i in 0 until 30) col[i] = 0.1f
        for (i in w - 30 until w) col[i] = 0.1f
        for (i in 0 until 5) row[i] = 0.5f
        for (i in h - 6 until h) row[i] = 0.5f

        // 宽度两侧合计达到 50%，旧实现会「四边全归零」，把有效的上下裁切也丢掉。
        val m = MarginDetector.detect(w, h, col, row)
        assertTrue("宽度仍应被裁切", m.left + m.right > 0)
        assertTrue("宽度剩余必须严格超过一半", w - m.left - m.right > w / 2)
        assertEquals(4, m.top)
        assertEquals(5, m.bottom)
    }

    @Test
    fun refuses_near_uniform_blank_axis_independently() {
        val w = 100
        val h = 200
        val col = FloatArray(w) { 0.1f } // 整列近纯空白
        val row = FloatArray(h) { 10f }
        for (i in 0 until 5) row[i] = 0.5f
        for (i in h - 6 until h) row[i] = 0.5f

        val m = MarginDetector.detect(w, h, col, row)
        assertEquals(0, m.left)
        assertEquals(0, m.right)
        assertEquals(4, m.top)
        assertEquals(5, m.bottom)
    }

    @Test
    fun crops_wide_single_side_margin_beyond_old_quarter_cap() {
        val w = 100
        val h = 80
        val col = FloatArray(w) { 10f }
        val row = FloatArray(h) { 10f }
        // 单侧 32% 空白。旧上限 25% 会留下一截白边。
        for (i in 0 until 32) col[i] = 0.1f

        val m = MarginDetector.detect(w, h, col, row)
        assertEquals(30, m.left)
        assertEquals(0, m.right)
        assertEquals(0, m.top)
        assertEquals(0, m.bottom)
    }

    @Test
    fun conservative_safety_is_scaling_not_fixed() {
        // 大图：1220宽应留24、高13507留16（评审误裁图尺寸）
        val w = 1220
        val h = 13507
        val col = FloatArray(w) { 10f }
        val row = FloatArray(h) { 10f }
        for (i in 0 until 57) col[i] = 0.5f
        for (i in w - 191 until w) col[i] = 0.5f
        for (i in 0 until 38) row[i] = 0.5f
        for (i in h - 20 until h) row[i] = 0.5f

        val m = MarginDetector.detect(w, h, col, row)
        assertEquals(57 - 24, m.left)
        assertEquals(191 - 24, m.right)
        assertEquals(38 - 16, m.top)
        assertEquals(20 - 16, m.bottom)
    }
}
