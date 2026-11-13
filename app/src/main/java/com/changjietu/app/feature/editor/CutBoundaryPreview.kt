package com.changjietu.app.feature.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/**
 * A seam-first overview: each tile contains a source-image excerpt around one cut.
 * The full-length fit used to make 1:12 screenshots unreadable, especially with
 * numbered badges overlaid on the only visible pixels.
 *
 * This is a downsampled preview only. Export and cut coordinates are unchanged.
 */
@Composable
internal fun CutBoundaryPreview(
    bitmap: ImageBitmap,
    plan: PreviewPlan,
    showCrop: Boolean,
    modifier: Modifier = Modifier,
) {
    val cuts = plan.cutEnds.dropLast(1)
    if (cuts.isEmpty()) return
    val density = LocalDensity.current
    val primary = MiuixTheme.colorScheme.primary
    val outline = MiuixTheme.colorScheme.outline
    val cardLabel = MiuixTheme.colorScheme.surface
    BoxWithConstraints(modifier) {
        val cols = cuts.size.coerceAtMost(3)
        val rows = (cuts.size + cols - 1) / cols
        val gutter = with(density) { 8.dp.toPx() }
        val boxW = with(density) { maxWidth.toPx() }
        val boxH = with(density) { maxHeight.toPx() }
        val tileW = ((boxW - gutter * (cols + 1)) / cols).coerceAtLeast(1f)
        val tileH = ((boxH - gutter * (rows + 1)) / rows).coerceAtLeast(1f)
        val fullH = plan.sourceHeight.coerceAtLeast(1)
        val fullW = plan.sourceWidth.coerceAtLeast(1)
        val hasCrop = plan.contentLeft > 0 || plan.contentTop > 0 ||
            plan.contentLeft + plan.contentWidth < fullW ||
            plan.contentTop + plan.contentHeight < fullH

        Canvas(Modifier.fillMaxSize()) {
            cuts.forEachIndexed { index, cut ->
                val col = index % cols
                val row = index / cols
                val x = gutter + col * (tileW + gutter)
                val y = gutter + row * (tileH + gutter)
                val w = tileW.roundToInt().coerceAtLeast(1)
                val h = tileH.roundToInt().coerceAtLeast(1)
                val center = ((plan.contentTop + cut).toFloat() / fullH * bitmap.height)
                    .roundToInt().coerceIn(0, bitmap.height)
                // Match the excerpt aspect ratio to the tile, not to the whole long screenshot.
                val srcH = (bitmap.width.toFloat() * h / w)
                    .roundToInt().coerceIn(1, bitmap.height)
                val srcTop = (center - srcH / 2).coerceIn(0, bitmap.height - srcH)
                drawImage(
                    image = bitmap,
                    srcOffset = IntOffset(0, srcTop),
                    srcSize = IntSize(bitmap.width, srcH),
                    dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
                    dstSize = IntSize(w, h),
                )
                val lineY = y + ((center - srcTop).toFloat() / srcH).coerceIn(0f, 1f) * h
                drawLine(primary, Offset(x, lineY), Offset(x + w, lineY), strokeWidth = 2.dp.toPx())
                if (showCrop && hasCrop) {
                    val leftX = x + plan.contentLeft.toFloat() / fullW * w
                    val rightX = x + (plan.contentLeft + plan.contentWidth).toFloat() / fullW * w
                    val crop = Color(0xFFE6A23C)
                    if (plan.contentLeft > 0) {
                        drawLine(crop, Offset(leftX, y), Offset(leftX, y + h), strokeWidth = 2.dp.toPx())
                    }
                    if (plan.contentLeft + plan.contentWidth < fullW) {
                        drawLine(crop, Offset(rightX, y), Offset(rightX, y + h), strokeWidth = 2.dp.toPx())
                    }
                    for (edge in listOf(plan.contentTop, plan.contentTop + plan.contentHeight)) {
                        val edgeY = edge.toFloat() / fullH * bitmap.height
                        if (edgeY in srcTop.toFloat()..(srcTop + srcH).toFloat()) {
                            val py = y + (edgeY - srcTop) / srcH * h
                            drawLine(crop, Offset(x, py), Offset(x + w, py), strokeWidth = 2.dp.toPx())
                        }
                    }
                }
                drawRect(
                    color = outline,
                    topLeft = Offset(x, y),
                    size = Size(w.toFloat(), h.toFloat()),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
        }
        cuts.indices.forEach { index ->
            val col = index % cols
            val row = index / cols
            val x = gutter + col * (tileW + gutter)
            val y = gutter + row * (tileH + gutter)
            Box(
                modifier = Modifier
                    .offset { IntOffset(x.roundToInt() + 3.dp.roundToPx(), y.roundToInt() + 3.dp.roundToPx()) }
                    .background(cardLabel.copy(alpha = 0.88f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("切线 ${index + 1}", fontSize = 10.sp, color = MiuixTheme.colorScheme.onBackground)
            }
        }
    }
}
