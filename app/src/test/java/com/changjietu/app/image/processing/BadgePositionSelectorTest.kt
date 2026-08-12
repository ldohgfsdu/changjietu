package com.changjietu.app.image.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgePositionSelectorTest {

    /** 生成能量数组：默认 [high]，[lowRegions] 内为 [low]。 */
    private fun energy(size: Int, lowRegions: List<IntRange>, low: Float = 0.1f, high: Float = 10f): FloatArray {
        val a = FloatArray(size) { high }
        for (r in lowRegions) {
            for (i in r) if (i in a.indices) a[i] = low
        }
        return a
    }

    private fun band(x: Int, len: Int, size: Int): IntRange {
        val s = x.coerceIn(0, size)
        return s until (s + len).coerceAtMost(size)
    }

    @Test
    fun choose_TR_when_top_right_safe() {
        val w = 800
        val h = 2200
        val badgeW = 120
        val badgeH = 40
        val pad = 16
        // TR：右侧列带 + 顶部行带为空白，其余为高能量
        val col = energy(w, listOf(band(w - badgeW - pad, badgeW + pad, w)))
        val row = energy(h, listOf(band(0, badgeH + pad, h)))
        assertEquals(BadgePosition.TR, BadgePositionSelector.choose(w, h, badgeW, badgeH, pad, col, row))
    }

    @Test
    fun choose_BR_when_top_right_occupied() {
        val w = 800
        val h = 2200
        val badgeW = 120
        val badgeH = 40
        val pad = 16
        // 右侧列带空白，但顶部行带高能量 → TR 被拒；底部行带空白 → BR
        val col = energy(w, listOf(band(w - badgeW - pad, badgeW + pad, w)))
        val row = energy(h, listOf(band(h - badgeH - pad, badgeH + pad, h)))
        assertEquals(BadgePosition.BR, BadgePositionSelector.choose(w, h, badgeW, badgeH, pad, col, row))
    }

    @Test
    fun choose_TL_when_right_column_occupied() {
        val w = 800
        val h = 2200
        val badgeW = 120
        val badgeH = 40
        val pad = 16
        // 右侧列带高能量 → TR/BR 均被拒；左侧列带 + 顶部行带空白 → TL
        val col = energy(w, listOf(band(0, badgeW + pad, w)))
        val row = energy(h, listOf(band(0, badgeH + pad, h)))
        assertEquals(BadgePosition.TL, BadgePositionSelector.choose(w, h, badgeW, badgeH, pad, col, row))
    }

    @Test
    fun choose_BL_when_first_three_corners_occupied() {
        val w = 800
        val h = 2200
        val badgeW = 120
        val badgeH = 40
        val pad = 16
        // 右侧列带高、顶部行带高 → TR/BR/TL 均被拒；左侧列带 + 底部行带空白 → BL
        val col = energy(w, listOf(band(0, badgeW + pad, w)))
        val row = energy(h, listOf(band(h - badgeH - pad, badgeH + pad, h)))
        assertEquals(BadgePosition.BL, BadgePositionSelector.choose(w, h, badgeW, badgeH, pad, col, row))
    }

    @Test
    fun choose_HIDDEN_when_all_corners_unsafe() {
        val w = 800
        val h = 2200
        // 全图高能量：四个候选均无法通过 safe-enough gate
        val col = energy(w, emptyList())
        val row = energy(h, emptyList())
        assertEquals(BadgePosition.HIDDEN, BadgePositionSelector.choose(w, h, 120, 40, 16, col, row))
    }

    @Test
    fun choose_bounds_clamped_and_no_crash_when_badge_exceeds_slice() {
        val w = 50
        val h = 60
        // 角标远大于切片：corner rect 必须 clamp 到切片内，不得越界
        val col = FloatArray(w) { 0.1f }
        val row = FloatArray(h) { 0.1f }
        val pos = BadgePositionSelector.choose(w, h, badgeWidth = 200, badgeHeight = 100, padding = 16, col, row)
        assertTrue(pos in BadgePosition.entries)
        // cornerRect 各角均落在切片内
        for (top in listOf(true, false)) {
            for (right in listOf(true, false)) {
                val r = BadgePositionSelector.cornerRect(w, h, 200, 100, 16, top, right)
                assertTrue("$r left>=0", r.left >= 0)
                assertTrue("$r top>=0", r.top >= 0)
                assertTrue("$r right<=w", r.right <= w)
                assertTrue("$r bottom<=h", r.bottom <= h)
                assertTrue("$r left<right", r.left <= r.right)
                assertTrue("$r top<=bottom", r.top <= r.bottom)
            }
        }
    }

    @Test
    fun choose_HIDDEN_on_dark_high_energy_background() {
        // 深色/高能量背景：所有角能量与整体相当，不能因「相对最低」被误判为 safe
        val col = FloatArray(800) { 100f }
        val row = FloatArray(2200) { 100f }
        assertEquals(BadgePosition.HIDDEN, BadgePositionSelector.choose(800, 2200, 120, 40, 16, col, row))
    }

    @Test
    fun choose_TR_on_blank_slice() {
        // 空白截图：所有角安全，按优先级取 TR
        val col = FloatArray(800)
        val row = FloatArray(2200)
        assertEquals(BadgePosition.TR, BadgePositionSelector.choose(800, 2200, 120, 40, 16, col, row))
    }
}
