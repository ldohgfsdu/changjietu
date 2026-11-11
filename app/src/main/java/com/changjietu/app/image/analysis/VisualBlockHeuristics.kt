package com.changjietu.app.image.analysis

import kotlin.math.abs

/**
 * Conservative CV detector for connected visual structures: avatars, pictures,
 * chart strokes, and ruled tables. It never claims semantic object recognition.
 * Pure Kotlin pixel processing allows deterministic JVM image-based regression.
 */
internal object VisualBlockHeuristics {
    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int)

    fun detect(pixels: IntArray, width: Int, height: Int): List<Box> {
        require(width > 0 && height > 0 && width.toLong() * height == pixels.size.toLong())
        if (width < 16 || height < 16) return emptyList()
        val bg = background(pixels, width, height)
        val n = pixels.size
        val active = BooleanArray(n)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val i = row + x
                val c = pixels[i]
                val differs = colorDistance(c, bg) >= 70
                val textured = x > 0 && colorDistance(c, pixels[i - 1]) >= 100
                active[i] = differs || textured
            }
        }
        // Two linear 3-pixel morphology passes join nearby anti-aliased strokes.
        // Avoid a full-image mask: each caller passes only one sampled tile.
        val horizontal = BooleanArray(n)
        for (i in active.indices) {
            if (!active[i]) continue
            val x = i % width
            horizontal[i] = true
            if (x > 0) horizontal[i - 1] = true
            if (x + 1 < width) horizontal[i + 1] = true
        }
        val joined = BooleanArray(n)
        for (i in horizontal.indices) {
            if (!horizontal[i]) continue
            joined[i] = true
            if (i >= width) joined[i - width] = true
            if (i + width < n) joined[i + width] = true
        }
        val queue = IntArray(n)
        val boxes = ArrayList<Box>()
        for (seed in 0 until n) {
            if (!joined[seed]) continue
            joined[seed] = false
            queue[0] = seed
            var read = 0
            var write = 1
            var left = seed % width
            var right = left
            var top = seed / width
            var bottom = top
            while (read < write) {
                val i = queue[read++]
                val x = i % width
                val y = i / width
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
                if (x > 0 && joined[i - 1]) { joined[i - 1] = false; queue[write++] = i - 1 }
                if (x + 1 < width && joined[i + 1]) { joined[i + 1] = false; queue[write++] = i + 1 }
                if (y > 0 && joined[i - width]) { joined[i - width] = false; queue[write++] = i - width }
                if (y + 1 < height && joined[i + width]) { joined[i + width] = false; queue[write++] = i + width }
            }
            val w = right - left + 1
            val h = bottom - top + 1
            // Suppress lone strokes/noise; retain small avatars, pictures and grids.
            if (w >= 12 && h >= 12 && write >= 60 &&
                (w >= 20 || h >= 20) && w * h >= 160) {
                boxes += Box(left, top, right, bottom)
            }
        }
        return boxes
    }

    private fun background(p: IntArray, width: Int, height: Int): Int {
        val insetX = (width / 50).coerceIn(0, 8)
        val insetY = (height / 50).coerceIn(0, 8)
        val corners = intArrayOf(
            p[insetY * width + insetX],
            p[insetY * width + width - 1 - insetX],
            p[(height - 1 - insetY) * width + insetX],
            p[(height - 1 - insetY) * width + width - 1 - insetX],
        )
        var best = 0
        var distance = Int.MAX_VALUE
        for (i in corners.indices) for (j in corners.indices) {
            if (i == j) continue
            val d = colorDistance(corners[i], corners[j])
            if (d < distance) { best = i; distance = d }
        }
        return corners[best]
    }

    private fun colorDistance(a: Int, b: Int): Int =
        abs((a shr 16 and 255) - (b shr 16 and 255)) +
            abs((a shr 8 and 255) - (b shr 8 and 255)) +
            abs((a and 255) - (b and 255))
}
