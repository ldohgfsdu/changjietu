package com.changjietu.app.image.analysis

import com.changjietu.app.image.processing.RegionSource
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.max
import kotlin.math.min

/** Only pixel-row geometry is retained; OCR text is never stored or transmitted. */
data class LocalTextScanResult(
    val protectedSourceRows: List<IntRange>,
    val detectedLines: Int,
)

/**
 * Bundled, on-device Chinese OCR. Horizontal line boxes become protected source
 * rows. A full long image is NEVER decoded into one Android Bitmap.
 */
object LocalTextLineScanner {
    private const val TILE_HEIGHT = 1500
    private const val TILE_OVERLAP = 120
    private const val MAX_TILES = 64
    private const val MAX_WIDTH = 1800
    private const val TEXT_PADDING = 12

    suspend fun scan(
        source: RegionSource,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): LocalTextScanResult {
        val height = source.height
        val width = source.width
        require(height > 0 && width > 0)
        val step = TILE_HEIGHT - TILE_OVERLAP
        val total = (height - 1) / step + 1
        require(total <= MAX_TILES) { "Long image exceeds local OCR scan budget" }
        val sample = max(1, (width + MAX_WIDTH - 1) / MAX_WIDTH)
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        val lines = mutableListOf<IntRange>()
        var detectedLines = 0
        try {
            for (tileIndex in 0 until total) {
                currentCoroutineContext().ensureActive()
                val top = tileIndex * step
                val bottom = min(height, top + TILE_HEIGHT)
                val handle = source.decodeRegion(0, top, width, bottom, sample)
                    ?: error("Cannot decode OCR tile at y=$top")
                handle.use { tile ->
                    val bitmap = tile.bitmapOrNull ?: error("OCR tile lacks bitmap")
                    val input = InputImage.fromBitmap(bitmap, 0)
                    // suspendCoroutine intentionally waits for the ML Kit task to finish.
                    // The source bitmap must not be recycled while the task still reads it.
                    val result = suspendCoroutine<Text> { cont ->
                        recognizer.process(input).addOnCompleteListener { task ->
                            if (task.isSuccessful) cont.resume(task.result)
                            else cont.resumeWithException(
                                task.exception ?: IllegalStateException("OCR task cancelled")
                            )
                        }
                    }
                    val scaleY = (bottom - top).toFloat() / bitmap.height.coerceAtLeast(1)
                    for (block in result.textBlocks) for (line in block.lines) {
                        val bounds = line.boundingBox ?: continue
                        val y0 = max(0, top + (bounds.top * scaleY).toInt() - TEXT_PADDING)
                        val y1 = min(height - 1, top + (bounds.bottom * scaleY).toInt() + TEXT_PADDING)
                        if (y1 >= y0 && line.text.isNotBlank()) {
                            lines.add(y0..y1)
                            detectedLines++
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                onProgress(tileIndex + 1, total)
            }
        } finally {
            recognizer.close()
        }
        return LocalTextScanResult(mergeRows(lines), detectedLines)
    }

    /** Consecutive OCR windows may report the same line; merge them cheaply. */
    internal fun mergeRows(rows: List<IntRange>): List<IntRange> {
        val sorted = rows.filter { it.first <= it.last }.sortedBy { it.first }
        if (sorted.isEmpty()) return emptyList()
        val output = mutableListOf<IntRange>()
        var start = sorted.first().first
        var end = sorted.first().last
        for (row in sorted.drop(1)) {
            if (row.first <= end + 1) end = max(end, row.last)
            else {
                output.add(start..end)
                start = row.first
                end = row.last
            }
        }
        output.add(start..end)
        return output
    }
}
