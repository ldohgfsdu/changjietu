package com.changjietu.app.image.analysis

/** Maps OCR source-image Y coordinates to current top/bottom-cropped content. */
object ContentProtectedRows {
    fun inContent(sourceRows: List<IntRange>, contentTop: Int, contentHeight: Int): List<IntRange> {
        if (contentHeight <= 0) return emptyList()
        val end = contentTop.toLong() + contentHeight - 1L
        return sourceRows.mapNotNull { source ->
            val first = maxOf(source.first.toLong(), contentTop.toLong())
            val last = minOf(source.last.toLong(), end)
            if (last < first) null
            else (first - contentTop).toInt()..(last - contentTop).toInt()
        }
    }
}
