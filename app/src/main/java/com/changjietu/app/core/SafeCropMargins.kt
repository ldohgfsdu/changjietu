package com.changjietu.app.core

import com.changjietu.app.image.analysis.Margins

/**
 * Fail-closed auto cropping policy for long screenshots.
 *
 * A single sparse glyph or avatar can have almost no effect on average
 * column energy over many thousands of rows. Therefore automatic cropping
 * must never remove horizontal source pixels. Users can explicitly request
 * horizontal cropping via manual margins.
 */
object SafeCropMargins {
    fun automatic(detected: Margins): Margins = detected.copy(left = 0, right = 0)

    fun effective(cropEnabled: Boolean, detected: Margins, manual: Margins?): Margins =
        if (!cropEnabled) Margins(0, 0, 0, 0)
        else manual ?: automatic(detected)
}
