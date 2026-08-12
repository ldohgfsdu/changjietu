package com.changjietu.app.image.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityAnalyzerTest {

    @Test
    fun good_when_width_at_least_1080() {
        val r = QualityAnalyzer.analyze(1080, 20000)
        assertEquals(QualityLevel.GOOD, r.level)
        assertEquals("良好", r.summary)
    }

    @Test
    fun usable_when_720_to_1079() {
        assertEquals(QualityLevel.USABLE, QualityAnalyzer.analyze(720, 3000).level)
        assertEquals(QualityLevel.USABLE, QualityAnalyzer.analyze(1079, 3000).level)
    }

    @Test
    fun low_when_360_to_719() {
        assertEquals(QualityLevel.LOW, QualityAnalyzer.analyze(360, 3000).level)
        assertEquals(QualityLevel.LOW, QualityAnalyzer.analyze(719, 3000).level)
    }

    @Test
    fun unrecoverable_when_width_below_360() {
        val r = QualityAnalyzer.analyze(86, 1536)
        assertEquals(QualityLevel.UNRECOVERABLE, r.level)
        assertEquals("不可恢复", r.summary)
        assertTrue(r.isCritical)
        assertTrue(r.conclusion.contains("无法可靠识别正文"))
        assertTrue(r.suggestion.contains("原始截图"))
        // 不得暗示超分可恢复：结论应否定超分
        assertTrue(r.conclusion.contains("超分") || r.conclusion.contains("猜字"))
    }

    @Test
    fun unrecoverable_when_glyph_too_small_and_width_under_720() {
        val r = QualityAnalyzer.analyze(640, 4000, estimatedGlyphHeightPx = 5f)
        assertEquals(QualityLevel.UNRECOVERABLE, r.level)
    }

    @Test
    fun glyph_small_but_wide_enough_stays_usable_or_good() {
        val r = QualityAnalyzer.analyze(1080, 4000, estimatedGlyphHeightPx = 5f)
        assertEquals(QualityLevel.GOOD, r.level)
    }
}
