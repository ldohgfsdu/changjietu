package com.changjietu.app.image.analysis

import com.changjietu.app.image.processing.RegionSource
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Approximate on-device visual regions; only source Y intervals leave this scanner. */
data class VisualBlockScanResult(
    val protectedSourceRows: List<IntRange>,
    val detectedRegions: Int,
)

/**
 * Detects image structure without a heavy model or an entire decoded long image.
 * It finds connected visual regions, NOT necessarily whole semantic cards/graphs.
 */
object LocalVisualBlockScanner {
    private const val TILE_HEIGHT = 960
    private const val TILE_OVERLAP = 160
    private const val MAX_TILES = 96
    private const val TARGET_WIDTH = 720
    private const val GUARD_PX = 10

    suspend fun scan(
        source: RegionSource,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): VisualBlockScanResult {
        val height = source.height
        val width = source.width
        require(height > 0 && width > 0)
        val step = TILE_HEIGHT - TILE_OVERLAP
        val count = (height - 1) / step + 1
        require(count <= MAX_TILES) { "Image exceeds visual scan tile budget" }
        val sample = max(1, (width + TARGET_WIDTH - 1) / TARGET_WIDTH)
        val rows = ArrayList<IntRange>()
        var regions = 0
        for (index in 0 until count) {
            currentCoroutineContext().ensureActive()
            val top = index * step
            val bottom = min(height, top + TILE_HEIGHT)
            val handle = source.decodeRegion(0, top, width, bottom, sample)
                ?: error("Cannot decode visual analysis region at y=$top")
            handle.use { tile ->
                val bitmap = tile.bitmapOrNull ?: error("Visual analysis tile lacks pixels")
                val w = bitmap.width
                val h = bitmap.height
                val pixels = IntArray(w * h)
                bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
                val scaleY = (bottom - top).toDouble() / h.coerceAtLeast(1)
                for (box in VisualBlockHeuristics.detect(pixels, w, h)) {
                    val y0 = max(0, top + (box.top * scaleY).toInt() - GUARD_PX)
                    val y1 = min(
                        height - 1,
                        top + ceil((box.bottom + 1) * scaleY).toInt() + GUARD_PX,
                    )
                    if (y1 >= y0) {
                        rows.add(y0..y1)
                        regions++
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            onProgress(index + 1, count)
        }
        return VisualBlockScanResult(LocalTextLineScanner.mergeRows(rows), regions)
    }
}
