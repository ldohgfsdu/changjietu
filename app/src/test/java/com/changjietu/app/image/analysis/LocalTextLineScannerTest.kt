package com.changjietu.app.image.analysis

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalTextLineScannerTest {
    @Test
    fun duplicate_tile_results_are_merged_without_zero_length_regions() {
        val rows = listOf(300..330, 5..15, 14..27, 305..348, 29..28, 430..450)
        assertEquals(listOf(5..27, 300..348, 430..450), LocalTextLineScanner.mergeRows(rows))
    }

    @Test
    fun empty_ocr_result_remains_empty() {
        assertEquals(emptyList<IntRange>(), LocalTextLineScanner.mergeRows(emptyList()))
    }
}
