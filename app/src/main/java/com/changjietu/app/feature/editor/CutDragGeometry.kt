package com.changjietu.app.feature.editor

import kotlin.math.roundToInt

/** Same pixel conversion used by drag gestures, with no file I/O. */
internal object CutDragGeometry {
    fun finalPosition(base: Int, displacementPx: Float, viewportHeightPx: Float, contentHeight: Int): Int {
        if (viewportHeightPx <= 0f || contentHeight <= 0 || !displacementPx.isFinite()) return base
        return (base + displacementPx / viewportHeightPx * contentHeight).roundToInt()
    }

    fun hasChanged(base: Int, displacementPx: Float, viewportHeightPx: Float, contentHeight: Int): Boolean =
        finalPosition(base, displacementPx, viewportHeightPx, contentHeight) != base
}
