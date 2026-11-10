package com.changjietu.app.image.analysis

import org.junit.Assert.assertEquals
import org.junit.Test

class ContentProtectedRowsTest {
    @Test
    fun maps_absolute_ocr_rows_into_cropped_content_coordinates() {
        assertEquals(
            listOf(0..15, 80..120, 190..199),
            ContentProtectedRows.inContent(
                listOf(40..115, 180..220, 290..350),
                contentTop = 100,
                contentHeight = 200,
            ),
        )
    }

    @Test
    fun invalid_and_outside_rows_do_not_affect_content_plan() {
        assertEquals(
            listOf(0..4),
            ContentProtectedRows.inContent(
                listOf(1..0, -500..-1, 490..504, 610..640),
                contentTop = 500,
                contentHeight = 100,
            ),
        )
    }

    @Test
    fun zero_content_returns_no_protection() {
        assertEquals(emptyList<IntRange>(), ContentProtectedRows.inContent(listOf(0..200), 0, 0))
    }
}
