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
        removeTimelineSpines(active, width, height)
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

    /** Disconnect persistent timeline strokes that would join unrelated cards. */
    private fun removeTimelineSpines(active: BooleanArray, width: Int, height: Int) {
        val streak = IntArray(width)
        val longest = IntArray(width)
        for (y in 0 until height) for (x in 0 until width) {
            streak[x] = if (active[y * width + x]) streak[x] + 1 else 0
            if (streak[x] > longest[x]) longest[x] = streak[x]
        }
        val candidate = BooleanArray(width) {
            longest[it] >= (height * 0.68f).toInt()
        }
        val n = candidate.count { it }
        // Do not erase full-bleed photos (many candidate columns) or tables
        // (many separated continuous vertical grid lines).
        if (n == 0 || n > width / 8) return
        var groups = 0
        for (x in candidate.indices) {
            if (candidate[x] && (x == 0 || !candidate[x - 1])) groups++
        }
        if (groups > 3) return
        for (x in candidate.indices) {
            if (!candidate[x]) continue
            for (xx in (x - 2).coerceAtLeast(0)..(x + 2).coerceAtMost(width - 1)) {
                for (y in 0 until height) active[y * width + xx] = false
            }
        }
    }

    private fun background(p: IntArray, width: Int, height: Int): Int {
        // Sample the entire canvas; choosing only the border fails for a white
        // feed with black side gutters or a dark toolbar.
        val bins = HashMap<Int, Pair<Int, Int>>()
        for (yi in 0..24) for (xi in 0..18) {
            val x = (width - 1) * xi / 18
            val y = (height - 1) * yi / 24
            val c = p[y * width + x]
            val key = ((c shr 12) and 0xf00) or
                ((c shr 8) and 0xf0) or ((c shr 4) and 0xf)
            val old = bins[key]
            bins[key] = ((old?.first ?: 0) + 1) to (old?.second ?: c)
        }
        return bins.values.maxByOrNull { it.first }?.second ?: p[0]
    }

    private fun colorDistance(a: Int, b: Int): Int =
        abs((a shr 16 and 255) - (b shr 16 and 255)) +
            abs((a shr 8 and 255) - (b shr 8 and 255)) +
            abs((a and 255) - (b and 255))
}
