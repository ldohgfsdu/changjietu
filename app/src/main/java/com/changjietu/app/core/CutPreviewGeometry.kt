package com.changjietu.app.core

/**
 * 处理页切线预览的坐标映射（纯 JVM）。
 *
 * 总览位图按源图比例放进预览框，切线和裁边都用同一套分数坐标，
 * 避免数字被裁切框和位图各算一次后对不齐。
 */
object CutPreviewGeometry {

    data class Frame(
        val offsetX: Float,
        val offsetY: Float,
        val drawWidth: Float,
        val drawHeight: Float,
    )

    fun fit(boxWidth: Float, boxHeight: Float, sourceWidth: Int, sourceHeight: Int): Frame {
        require(boxWidth > 0f && boxHeight > 0f) { "box must be positive" }
        require(sourceWidth > 0 && sourceHeight > 0) { "source must be positive" }
        val srcAspect = sourceWidth.toFloat() / sourceHeight.toFloat()
        val boxAspect = boxWidth / boxHeight
        return if (srcAspect > boxAspect) {
            val drawWidth = boxWidth
            val drawHeight = boxWidth / srcAspect
            Frame(
                offsetX = 0f,
                offsetY = (boxHeight - drawHeight) / 2f,
                drawWidth = drawWidth,
                drawHeight = drawHeight,
            )
        } else {
            val drawHeight = boxHeight
            val drawWidth = boxHeight * srcAspect
            Frame(
                offsetX = (boxWidth - drawWidth) / 2f,
                offsetY = 0f,
                drawWidth = drawWidth,
                drawHeight = drawHeight,
            )
        }
    }

    fun xOf(frame: Frame, sourceX: Int, sourceWidth: Int): Float {
        val width = sourceWidth.coerceAtLeast(1)
        return frame.offsetX + sourceX.toFloat() / width * frame.drawWidth
    }

    fun yOf(frame: Frame, sourceY: Int, sourceHeight: Int): Float {
        val height = sourceHeight.coerceAtLeast(1)
        return frame.offsetY + sourceY.toFloat() / height * frame.drawHeight
    }
}
